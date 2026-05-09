package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author USS_Shenzhou
 */
public class AggregationManager {
    private static final int CLIENTBOUND_CUSTOM_PAYLOAD_LIMIT = 1024 * 1024;
    private static final int SERVERBOUND_CUSTOM_PAYLOAD_LIMIT = 32767;
    private static final int VELOCITY_DEFAULT_PLUGIN_MESSAGE_PAYLOAD_LIMIT = 32767;
    /**
     * zstd-jni writes a small magicless frame/block header even when content-size and magic are disabled.
     * The NeoForge implementation relies on GenericPacketSplitter for this safety margin; Forge 1.20.1 does
     * not provide an equivalent generic serverbound splitter, so reserve it in our batch estimator.
     */
    private static final int ZSTD_MAGICLESS_FLUSH_OVERHEAD = 8;
    private static final ThreadLocal<Boolean> INTERNAL_SEND = ThreadLocal.withInitial(() -> false);
    private static final WeakHashMap<Connection, ArrayList<AggregatedEncodePacket>> PACKET_BUFFER = new WeakHashMap<>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("NEB-Flush-thread").setDaemon(true).build());
    private static final ArrayList<ScheduledFuture<?>> TASKS = new ArrayList<>();
    private static volatile boolean initialized = false;

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isInternalSend() {
        return INTERNAL_SEND.get();
    }

    public synchronized static void init() {
        if (initialized) {
            return;
        }
        initialized = false;
        releaseAllBufferedPackets();
        PACKET_BUFFER.clear();
        TASKS.forEach(task -> task.cancel(false));
        TASKS.clear();
        TASKS.add(TIMER.scheduleAtFixedRate(
                AggregationManager::flush,
                0,
                AggregationFlushHelper.getFlushPeriodInMilliseconds(),
                TimeUnit.MILLISECONDS));
        initialized = true;
    }

    public synchronized static void takeOver(Packet<?> packet, Connection connection) {
        var type = PacketUtil.getTrueType(packet);
        PACKET_BUFFER.computeIfAbsent(connection, k -> new ArrayList<>())
                .add(new AggregatedEncodePacket(packet, type, connection.getSending()));
    }

    public synchronized static void flushConnection(Connection connection) {
        removeDisconnectedConnections();
        flushInternal(connection, PACKET_BUFFER.get(connection));
    }

    public synchronized static void clearConnection(@Nullable Connection connection) {
        if (connection == null) {
            return;
        }
        ArrayList<AggregatedEncodePacket> packets = PACKET_BUFFER.remove(connection);
        releaseBufferedPackets(packets);
    }

    private synchronized static void flush() {
        removeDisconnectedConnections();
        PACKET_BUFFER.forEach(AggregationManager::flushInternal);
    }

    private synchronized static void flushInternal(
            Connection connection,
            @Nullable ArrayList<AggregatedEncodePacket> packets) {
        ArrayList<AggregatedEncodePacket> sendPackets = null;
        try {
            if (packets == null || packets.isEmpty()) {
                return;
            }

            if (connection.channel() == null || !connection.isConnected()) {
                releaseBufferedPackets(packets);
                return;
            }

            sendPackets = new ArrayList<>(packets);
            ArrayList<AggregatedEncodePacket> packetsToSend = sendPackets;
            runInternalSend(() -> {
                flushBatch(connection, packetsToSend);
                if (connection.channel() != null) {
                    connection.channel().flush();
                }
            });
            packets.clear();
        } catch (Exception e) {
            if (packets != null) {
                releaseBufferedPackets(packets);
            }
            LogUtils.getLogger().error("[NEB] Skipped: Failed to flush packets.", e);
        } finally {
            if (sendPackets != null) {
                sendPackets.forEach(AggregatedEncodePacket::release);
            }
        }
    }

    private static void removeDisconnectedConnections() {
        PACKET_BUFFER.entrySet().removeIf(e -> {
            if (!e.getKey().isConnected()) {
                releaseBufferedPackets(e.getValue());
                return true;
            }
            return false;
        });
    }

    private static void releaseAllBufferedPackets() {
        PACKET_BUFFER.values().forEach(AggregationManager::releaseBufferedPackets);
    }

    private static void releaseBufferedPackets(@Nullable ArrayList<AggregatedEncodePacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        packets.forEach(AggregatedEncodePacket::release);
        packets.clear();
    }

    private static void flushBatch(Connection connection, ArrayList<AggregatedEncodePacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        int maxPacketSize = getEffectiveTransportPayloadLimit(connection.getSending());
        int estimatedSize = estimateAggregatePayloadSize(packets);
        if (estimatedSize > maxPacketSize) {
            splitOrPassthrough(connection, packets, maxPacketSize);
            return;
        }
        try {
            PayloadRegistry.send(connection, connection.getSending(), new PacketAggregationPacket(packets, connection));
        } catch (IllegalArgumentException e) {
            if (!isPayloadTooLarge(e)) {
                throw e;
            }
            splitOrPassthrough(connection, packets, maxPacketSize);
        }
    }

    private static int getEffectiveTransportPayloadLimit(PacketFlow flow) {
        int configuredLimit = NotEnoughBandwidthLegacyConfig.get().getMaxPacketSize();
        int vanillaLimit = flow == PacketFlow.CLIENTBOUND ? getClientboundTransportPayloadLimit() : SERVERBOUND_CUSTOM_PAYLOAD_LIMIT;
        return Math.min(configuredLimit, vanillaLimit);
    }

    private static int getClientboundTransportPayloadLimit() {
        if (NotEnoughBandwidthLegacyConfig.get().isVelocityProxyCompatibleMode()) {
            return VELOCITY_DEFAULT_PLUGIN_MESSAGE_PAYLOAD_LIMIT;
        }
        return CLIENTBOUND_CUSTOM_PAYLOAD_LIMIT;
    }

    private static int estimateAggregatePayloadSize(ArrayList<AggregatedEncodePacket> packets) {
        int rawSize = estimateAggregateRawSize(packets);
        if (rawSize >= 32 && ZstdHelper.isAvailable()) {
            return 1 + FriendlyByteBuf.getVarIntSize(rawSize) + rawSize + ZSTD_MAGICLESS_FLUSH_OVERHEAD;
        }
        return 1 + rawSize;
    }

    private static int estimateAggregateRawSize(ArrayList<AggregatedEncodePacket> packets) {
        int total = 0;
        for (AggregatedEncodePacket packet : packets) {
            total += Math.max(0, packet.getEncodedSizeEstimate());
        }
        return total;
    }

    private static void splitOrPassthrough(Connection connection, ArrayList<AggregatedEncodePacket> packets, int maxPacketSize) {
        if (packets.size() <= 1) {
            passthroughSingle(connection, packets.isEmpty() ? null : packets.get(0));
            return;
        }

        ArrayList<AggregatedEncodePacket> current = new ArrayList<>();
        int currentRawSize = 0;
        for (AggregatedEncodePacket packet : packets) {
            int packetSize = Math.max(1, packet.getEncodedSizeEstimate());
            int nextRawSize = currentRawSize + packetSize;
            if (!current.isEmpty() && estimateAggregatePayloadSizeForRawSize(nextRawSize) > maxPacketSize) {
                flushBatch(connection, new ArrayList<>(current));
                current.clear();
                currentRawSize = 0;
                nextRawSize = packetSize;
            }
            current.add(packet);
            currentRawSize = nextRawSize;
        }

        if (!current.isEmpty()) {
            if (current.size() == packets.size()) {
                int mid = packets.size() / 2;
                flushBatch(connection, new ArrayList<>(packets.subList(0, mid)));
                flushBatch(connection, new ArrayList<>(packets.subList(mid, packets.size())));
                return;
            }
            flushBatch(connection, current);
        }
    }

    private static int estimateAggregatePayloadSizeForRawSize(int rawSize) {
        if (rawSize >= 32 && ZstdHelper.isAvailable()) {
            return 1 + FriendlyByteBuf.getVarIntSize(rawSize) + rawSize + ZSTD_MAGICLESS_FLUSH_OVERHEAD;
        }
        return 1 + rawSize;
    }

    private static boolean isPayloadTooLarge(IllegalArgumentException e) {
        String message = e.getMessage();
        return message != null && (message.contains("Payload may not be larger than") || message.contains("NEB: Packet too large"));
    }

    private static void passthroughSingle(Connection connection, @Nullable AggregatedEncodePacket packet) {
        if (packet == null) {
            return;
        }
        packet.sendPassthrough(connection, connection.getSending());
    }

    private static void runInternalSend(Runnable action) {
        INTERNAL_SEND.set(true);
        try {
            action.run();
        } finally {
            INTERNAL_SEND.remove();
        }
    }
}
