package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author USS_Shenzhou
 */
public class AggregationManager {
    private static final WeakHashMap<Connection, ArrayList<AggregatedEncodePacket>> PACKET_BUFFER = new WeakHashMap<>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("NEB-Flush-thread").setDaemon(true).build());
    private static final ArrayList<ScheduledFuture<?>> TASKS = new ArrayList<>();
    private static volatile boolean initialized = false;

    public static boolean isInitialized() {
        return initialized;
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
                .add(new AggregatedEncodePacket(packet, type));
    }

    /**
     * Force flush all buffered packets for a specific connection immediately.
     * Called when a packet that cannot be aggregated is encountered to maintain packet order.
     */
    public synchronized static void flushConnection(Connection connection) {
        TIMER.execute(() -> {
            synchronized (AggregationManager.class) {
                PACKET_BUFFER.entrySet().removeIf(e -> !e.getKey().isConnected());
                flushInternal(connection, PACKET_BUFFER.get(connection));
            }
        });
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
            var sendPackets = new ArrayList<>(packets);
            packets.clear();

            // Build the aggregation packet and send it via the connection.
            // In Forge 1.20.1 we use the Forge SimpleChannel which wraps the payload.
            var aggregationPacket = new PacketAggregationPacket(sendPackets, connection);
            // Use ModNetworkRegistry channel to send the aggregation packet
            // (registered as bidirectional in ModNetworkRegistry.register())
            cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.CHANNEL.sendTo(
                    aggregationPacket,
                    connection,
                    connection.getSending() == PacketFlow.SERVERBOUND
                            ? net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER
                            : net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
            );
            // Flush the underlying Netty channel
            if (connection.channel() != null) {
                connection.channel().flush();
            }
        } catch (Exception e) {
            LogUtils.getLogger().error("[NEB] Skipped: Failed to flush packets.", e);
        }
    }
}
