package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PacketAggregationPayload;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class AggregationManager {
    private static final int CLIENTBOUND_CUSTOM_PAYLOAD_LIMIT = 1024 * 1024;
    private static final int SERVERBOUND_CUSTOM_PAYLOAD_LIMIT = 32767;
    private static final ThreadLocal<Boolean> INTERNAL_SEND = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private static final WeakHashMap<NetworkManager, ArrayList<AggregatedEncodePacket>> PACKET_BUFFER = new WeakHashMap<NetworkManager, ArrayList<AggregatedEncodePacket>>();
    private static final WeakHashMap<NetworkManager, Long> BATCH_START_NANOS = new WeakHashMap<NetworkManager, Long>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("NEB-Flush-thread").setDaemon(true).build());
    private static final ArrayList<ScheduledFuture<?>> TASKS = new ArrayList<ScheduledFuture<?>>();
    private static volatile boolean initialized = false;

    private AggregationManager() {
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        removeDisconnectedConnections();
        int connectionCount = PACKET_BUFFER.size();
        int totalBufferedPackets = 0;
        long totalEstimatedBytes = 0L;
        int maxBufferedPacketsPerConnection = 0;
        for (ArrayList<AggregatedEncodePacket> packets : PACKET_BUFFER.values()) {
            if (packets == null) {
                continue;
            }
            int size = packets.size();
            totalBufferedPackets += size;
            if (size > maxBufferedPacketsPerConnection) {
                maxBufferedPacketsPerConnection = size;
            }
            for (AggregatedEncodePacket packet : packets) {
                if (packet != null) {
                    totalEstimatedBytes += Math.max(0, packet.getEncodedSizeEstimate());
                }
            }
        }
        return new DebugSnapshot(connectionCount, totalBufferedPackets, totalEstimatedBytes, maxBufferedPacketsPerConnection);
    }

    public static boolean isInternalSend() {
        return INTERNAL_SEND.get();
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        releaseAllBufferedPackets();
        PACKET_BUFFER.clear();
        BATCH_START_NANOS.clear();
        for (ScheduledFuture<?> task : TASKS) {
            task.cancel(false);
        }
        TASKS.clear();
        TASKS.add(TIMER.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                flush();
            }
        }, 0L, AggregationFlushHelper.getFlushPeriodInMilliseconds(), TimeUnit.MILLISECONDS));
        initialized = true;
    }

    public static synchronized boolean takeOver(Packet<?> packet, NetworkManager connection) {
        String type = PacketAggregationPacket.resolvePacketType(packet);
        if (type == null || NotEnoughBandwidthLegacyConfig.skipType(type)) {
            return false;
        }
        ArrayList<AggregatedEncodePacket> packets = PACKET_BUFFER.get(connection);
        if (packets == null) {
            packets = new ArrayList<AggregatedEncodePacket>();
            PACKET_BUFFER.put(connection, packets);
        }
        if (packets.isEmpty()) {
            BATCH_START_NANOS.put(connection, System.nanoTime());
        } else if (isBatchExpired(connection)) {
            flushInternal(connection, packets);
            packets = PACKET_BUFFER.get(connection);
            if (packets == null) {
                packets = new ArrayList<AggregatedEncodePacket>();
                PACKET_BUFFER.put(connection, packets);
            }
            if (packets.isEmpty()) {
                BATCH_START_NANOS.put(connection, System.nanoTime());
            }
        }
        packets.add(new AggregatedEncodePacket(packet, type, connection.getDirection()));
        return true;
    }

    public static synchronized void flushConnection(NetworkManager connection) {
        removeDisconnectedConnections();
        flushInternal(connection, PACKET_BUFFER.get(connection));
    }

    public static synchronized void clearConnection(@Nullable NetworkManager connection) {
        if (connection == null) {
            return;
        }
        ArrayList<AggregatedEncodePacket> packets = PACKET_BUFFER.remove(connection);
        BATCH_START_NANOS.remove(connection);
        releaseBufferedPackets(packets);
    }

    private static synchronized void flush() {
        removeDisconnectedConnections();
        for (Map.Entry<NetworkManager, ArrayList<AggregatedEncodePacket>> entry : PACKET_BUFFER.entrySet()) {
            flushInternal(entry.getKey(), entry.getValue());
        }
    }

    private static synchronized void flushInternal(NetworkManager connection, @Nullable ArrayList<AggregatedEncodePacket> packets) {
        ArrayList<AggregatedEncodePacket> sendPackets = null;
        try {
            if (packets == null || packets.isEmpty()) {
                return;
            }
            Channel channel = connection.channel();
            if (channel == null || !connection.isChannelOpen()) {
                releaseBufferedPackets(packets);
                return;
            }
            sendPackets = new ArrayList<AggregatedEncodePacket>(packets);
            final ArrayList<AggregatedEncodePacket> packetsToSend = sendPackets;
            runInternalSend(new Runnable() {
                @Override
                public void run() {
                    flushBatch(connection, packetsToSend);
                    Channel current = connection.channel();
                    if (current != null) {
                        current.flush();
                    }
                }
            });
            packets.clear();
            BATCH_START_NANOS.remove(connection);
        } catch (Exception e) {
            if (packets != null) {
                releaseBufferedPackets(packets);
            }
            throw new RuntimeException("[NEB] Failed to flush packets", e);
        } finally {
            if (sendPackets != null) {
                for (AggregatedEncodePacket packet : sendPackets) {
                    packet.release();
                }
            }
        }
    }

    private static void removeDisconnectedConnections() {
        PACKET_BUFFER.entrySet().removeIf(entry -> {
            NetworkManager manager = entry.getKey();
            if (manager == null || !manager.isChannelOpen()) {
                releaseBufferedPackets(entry.getValue());
                BATCH_START_NANOS.remove(manager);
                return true;
            }
            return false;
        });
    }

    private static boolean isBatchExpired(NetworkManager connection) {
        ArrayList<AggregatedEncodePacket> packets = PACKET_BUFFER.get(connection);
        if (packets == null || packets.isEmpty()) {
            BATCH_START_NANOS.remove(connection);
            return false;
        }
        Long batchStart = BATCH_START_NANOS.get(connection);
        if (batchStart == null) {
            BATCH_START_NANOS.put(connection, System.nanoTime());
            return false;
        }
        return System.nanoTime() - batchStart.longValue() >= AggregationFlushHelper.getMaxBatchWaitNanos();
    }

    private static void releaseAllBufferedPackets() {
        for (ArrayList<AggregatedEncodePacket> packets : PACKET_BUFFER.values()) {
            releaseBufferedPackets(packets);
        }
    }

    private static void releaseBufferedPackets(@Nullable ArrayList<AggregatedEncodePacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        for (AggregatedEncodePacket packet : packets) {
            packet.release();
        }
        packets.clear();
    }

    private static void flushBatch(NetworkManager connection, ArrayList<AggregatedEncodePacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        int maxPacketSize = getEffectiveTransportPayloadLimit(packets);
        int estimatedSize = PacketAggregationPacket.estimatePayloadSize(packets);
        if (estimatedSize > maxPacketSize) {
            splitOrPassthrough(connection, packets, maxPacketSize);
            return;
        }
        PacketBuffer payload = PacketAggregationPacket.createTransportPayload(connection, packets);
        try {
            boolean clientbound = isClientboundBatch(packets);
            PacketAggregationPayload.send(connection, clientbound, payload);
        } catch (IllegalArgumentException e) {
            if (!isPayloadTooLarge(e)) {
                throw e;
            }
            splitOrPassthrough(connection, packets, maxPacketSize);
        } finally {
            payload.release();
        }
    }

    private static boolean isClientboundBatch(ArrayList<AggregatedEncodePacket> packets) {
        if (packets.isEmpty()) {
            return false;
        }
        Packet<?> firstPacket = packets.get(0).getPacket();
        return firstPacket instanceof SPacketCustomPayload || firstPacket.getClass().getName().contains("server.");
    }

    private static int getEffectiveTransportPayloadLimit(ArrayList<AggregatedEncodePacket> packets) {
        int configuredLimit = NotEnoughBandwidthLegacyConfig.get().getMaxPacketSize();
        if (packets.isEmpty()) {
            return configuredLimit;
        }
        Packet<?> firstPacket = packets.get(0).getPacket();
        int vanillaLimit = firstPacket instanceof SPacketCustomPayload ? CLIENTBOUND_CUSTOM_PAYLOAD_LIMIT : SERVERBOUND_CUSTOM_PAYLOAD_LIMIT;
        return Math.min(configuredLimit, vanillaLimit);
    }

    private static void splitOrPassthrough(NetworkManager connection, ArrayList<AggregatedEncodePacket> packets, int maxPacketSize) {
        if (packets.size() <= 1) {
            AggregatedEncodePacket single = packets.isEmpty() ? null : packets.get(0);
            if (single != null) {
                single.sendPassthrough(connection);
            }
            return;
        }
        ArrayList<AggregatedEncodePacket> current = new ArrayList<AggregatedEncodePacket>();
        int currentSize = 0;
        for (AggregatedEncodePacket packet : packets) {
            int packetSize = Math.max(1, packet.getEncodedSizeEstimate());
            if (!current.isEmpty() && currentSize + packetSize > maxPacketSize) {
                flushBatch(connection, new ArrayList<AggregatedEncodePacket>(current));
                current.clear();
                currentSize = 0;
            }
            current.add(packet);
            currentSize += packetSize;
        }
        if (!current.isEmpty()) {
            if (current.size() == packets.size()) {
                int mid = packets.size() / 2;
                flushBatch(connection, new ArrayList<AggregatedEncodePacket>(packets.subList(0, mid)));
                flushBatch(connection, new ArrayList<AggregatedEncodePacket>(packets.subList(mid, packets.size())));
                return;
            }
            flushBatch(connection, current);
        }
    }

    private static boolean isPayloadTooLarge(IllegalArgumentException e) {
        String message = e.getMessage();
        return message != null && message.contains("Payload may not be larger than");
    }

    private static void runInternalSend(Runnable action) {
        INTERNAL_SEND.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            INTERNAL_SEND.remove();
        }
    }
    public static final class DebugSnapshot {
        private final int connectionCount;
        private final int totalBufferedPackets;
        private final long totalEstimatedBytes;
        private final int maxBufferedPacketsPerConnection;

        public DebugSnapshot(int connectionCount, int totalBufferedPackets, long totalEstimatedBytes, int maxBufferedPacketsPerConnection) {
            this.connectionCount = connectionCount;
            this.totalBufferedPackets = totalBufferedPackets;
            this.totalEstimatedBytes = totalEstimatedBytes;
            this.maxBufferedPacketsPerConnection = maxBufferedPacketsPerConnection;
        }

        public int connectionCount() {
            return connectionCount;
        }

        public int totalBufferedPackets() {
            return totalBufferedPackets;
        }

        public long totalEstimatedBytes() {
            return totalEstimatedBytes;
        }

        public int maxBufferedPacketsPerConnection() {
            return maxBufferedPacketsPerConnection;
        }
    }
}
