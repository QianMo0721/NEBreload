package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacy;
import cn.ussshenzhou.notenoughbandwidth.util.DefaultChannelPipelineHelper;
import cn.ussshenzhou.notenoughbandwidth.util.LegacyCustomPayloadAccessor;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author USS_Shenzhou
 */
public class AggregationManager {
    private static final int SERVERBOUND_CUSTOM_PAYLOAD_LIMIT = 32767;
    private static final WeakHashMap<NetworkManager, ArrayList<AggregatedEncodePacket>> PACKET_BUFFER = new WeakHashMap<NetworkManager, ArrayList<AggregatedEncodePacket>>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("NEB-Flush-thread").setDaemon(true).build());
    private static final ArrayList<ScheduledFuture<?>> TASKS = new ArrayList<ScheduledFuture<?>>();
    private static volatile boolean initialized;

    public static boolean isInitialized() {
        return initialized;
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = false;
        PACKET_BUFFER.clear();
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

    public static synchronized void takeOver(Packet<?> packet, NetworkManager connection) {
        ResourceLocation type = PacketUtil.getTrueType(packet);
        ArrayList<AggregatedEncodePacket> packets = PACKET_BUFFER.get(connection);
        if (packets == null) {
            packets = new ArrayList<AggregatedEncodePacket>();
            PACKET_BUFFER.put(connection, packets);
        }
        packets.add(new AggregatedEncodePacket(packet, type, getOutboundDirection(connection)));
    }

    public static synchronized void flushConnection(NetworkManager connection) {
        cleanupDisconnected();
        flushInternal(connection, PACKET_BUFFER.get(connection));
    }

    private static synchronized void flush() {
        cleanupDisconnected();
        for (Map.Entry<NetworkManager, ArrayList<AggregatedEncodePacket>> entry : PACKET_BUFFER.entrySet()) {
            flushInternal(entry.getKey(), entry.getValue());
        }
    }

    private static void cleanupDisconnected() {
        PACKET_BUFFER.entrySet().removeIf(entry -> entry.getKey() == null || !entry.getKey().isChannelOpen());
    }

    private static synchronized void flushInternal(NetworkManager connection, @Nullable ArrayList<AggregatedEncodePacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        if (connection == null || connection.channel() == null || !connection.isChannelOpen()) {
            return;
        }
        try {
            ArrayList<AggregatedEncodePacket> sendPackets = new ArrayList<AggregatedEncodePacket>(packets);
            packets.clear();
            if (getOutboundDirection(connection) == EnumPacketDirection.SERVERBOUND) {
                sendServerboundBatches(connection, sendPackets);
                return;
            }
            PacketAggregationPacket aggregationPacket = new PacketAggregationPacket(sendPackets, connection);
            connection.sendPacket(DefaultChannelPipelineHelper.toVanillaAggregatedPacket(connection, aggregationPacket));
            if (connection.channel() != null) {
                connection.channel().flush();
            }
        } catch (Exception e) {
            NotEnoughBandwidthLegacy.LOGGER.error("[NEB] Failed to flush aggregated packets", e);
        }
    }

    private static EnumPacketDirection getOutboundDirection(NetworkManager connection) {
        return connection.getDirection() == EnumPacketDirection.CLIENTBOUND
                ? EnumPacketDirection.SERVERBOUND
                : EnumPacketDirection.CLIENTBOUND;
    }

    private static void sendServerboundBatches(NetworkManager connection, ArrayList<AggregatedEncodePacket> sendPackets) {
        ArrayList<AggregatedEncodePacket> batch = new ArrayList<AggregatedEncodePacket>();
        for (AggregatedEncodePacket packet : sendPackets) {
            batch.add(packet);
            if (estimateServerboundBatchSize(batch, connection) > SERVERBOUND_CUSTOM_PAYLOAD_LIMIT) {
                batch.remove(batch.size() - 1);
                if (!batch.isEmpty()) {
                    sendBatch(connection, batch);
                    batch = new ArrayList<AggregatedEncodePacket>();
                }
                batch.add(packet);
            }
        }
        if (!batch.isEmpty()) {
            sendBatch(connection, batch);
        }
    }

    private static int estimateServerboundBatchSize(ArrayList<AggregatedEncodePacket> batch, NetworkManager connection) {
        PacketBuffer test = new PacketBuffer(Unpooled.buffer());
        try {
            new PacketAggregationPacket(new ArrayList<AggregatedEncodePacket>(batch), connection).encode(test);
            return test.readableBytes();
        } finally {
            test.release();
        }
    }

    private static void sendBatch(NetworkManager connection, ArrayList<AggregatedEncodePacket> batch) {
        PacketAggregationPacket aggregationPacket = new PacketAggregationPacket(new ArrayList<AggregatedEncodePacket>(batch), connection);
        Packet<?> wrapper = DefaultChannelPipelineHelper.toVanillaAggregatedPacket(connection, aggregationPacket);
        PacketBuffer payload = LegacyCustomPayloadAccessor.getBufferData(wrapper);
        if (payload != null && payload.readableBytes() > SERVERBOUND_CUSTOM_PAYLOAD_LIMIT) {
            NotEnoughBandwidthLegacy.LOGGER.warn("[NEB] Skip oversized serverbound aggregation batch: {} bytes", payload.readableBytes());
            for (AggregatedEncodePacket packet : batch) {
                connection.sendPacket(packet.getPacket());
            }
            return;
        }
        connection.sendPacket(wrapper);
        if (connection.channel() != null) {
            connection.channel().flush();
        }
    }
}
