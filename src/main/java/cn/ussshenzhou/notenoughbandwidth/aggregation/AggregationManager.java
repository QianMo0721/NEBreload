package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
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
        PACKET_BUFFER.entrySet().removeIf(e -> !e.getKey().isConnected());
        flushInternal(connection, PACKET_BUFFER.get(connection));
    }

    private synchronized static void flush() {
        PACKET_BUFFER.entrySet().removeIf(e -> !e.getKey().isConnected());
        PACKET_BUFFER.forEach(AggregationManager::flushInternal);
    }

    private synchronized static void flushInternal(
            Connection connection,
            @Nullable ArrayList<AggregatedEncodePacket> packets) {
        try {
            if (packets == null || packets.isEmpty()) {
                return;
            }

            if (connection.channel() == null || !connection.isConnected()) {
                return;
            }

            var sendPackets = new ArrayList<>(packets);
            runInternalSend(() -> {
                flushBatch(connection, sendPackets);
                if (connection.channel() != null) {
                    connection.channel().flush();
                }
            });
            packets.clear();
            sendPackets.forEach(AggregatedEncodePacket::release);
        } catch (Exception e) {
            packets.clear();
            LogUtils.getLogger().error("[NEB] Skipped: Failed to flush packets.", e);
        }
    }

    private static void flushBatch(Connection connection, ArrayList<AggregatedEncodePacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        int maxPacketSize = NotEnoughBandwidthLegacyConfig.get().getMaxPacketSize();
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

    private static int estimateAggregatePayloadSize(ArrayList<AggregatedEncodePacket> packets) {
        int total = 1;
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
        int currentSize = 1;
        for (AggregatedEncodePacket packet : packets) {
            int packetSize = Math.max(1, packet.getEncodedSizeEstimate());
            if (!current.isEmpty() && currentSize + packetSize > maxPacketSize) {
                flushBatch(connection, new ArrayList<>(current));
                current.clear();
                currentSize = 1;
            }
            current.add(packet);
            currentSize += packetSize;
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
