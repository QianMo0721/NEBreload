package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.util.DefaultChannelPipelineHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
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
                .add(new AggregatedEncodePacket(packet, type, connection.getSending()));
    }

    public synchronized static void flushConnection(Connection connection) {
        // 这里必须同步立刻冲刷，而不是丢到定时线程异步执行。
        // 否则当 ConnectionMixin 遇到“需要旁路/跳过聚合”的包时，
        // 原始包会继续立即发送，而之前缓冲的聚合包反而稍后才发出，
        // 从而破坏顺序。像 FTB Quests 这类依赖严格收发时序的 payload
        // 就会表现成任务书数据未收到、进服后功能异常甚至超时。
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
            packets.clear();

            var aggregationPacket = new PacketAggregationPacket(sendPackets, connection);
            connection.send(DefaultChannelPipelineHelper.toVanillaAggregatedPacket(connection, aggregationPacket));
            if (connection.channel() != null) {
                connection.channel().flush();
            }
        } catch (Exception e) {
            LogUtils.getLogger().error("[NEB] Skipped: Failed to flush packets.", e);
        }
    }
}
