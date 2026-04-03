package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.network.NebPayloads;
import cn.ussshenzhou.notenoughbandwidth.util.DefaultChannelPipelineHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.channel.DefaultChannelPipeline;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DecoderHandler;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AggregationManager {
    private static final WeakHashMap<ClientConnection, ArrayList<AggregatedEncodePacket>> PACKET_BUFFER = new WeakHashMap<>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "NEBL-Flush-thread");
        thread.setDaemon(true);
        return thread;
    });
    private static final ArrayList<ScheduledFuture<?>> TASKS = new ArrayList<>();
    private static volatile boolean initialized = false;

    public synchronized static void init() {
        if (initialized) {
            return;
        }
        initialized = false;
        PACKET_BUFFER.clear();
        TASKS.forEach(task -> task.cancel(false));
        TASKS.clear();
        TASKS.add(TIMER.scheduleAtFixedRate(AggregationManager::flush, 0, AggregationFlushHelper.getFlushPeriodInMilliseconds(), TimeUnit.MILLISECONDS));
        initialized = true;
    }

    public synchronized static void takeOver(Packet<?> packet, ClientConnection connection) {
        Identifier type;
        try {
            type = PacketUtil.getTrueType(packet);
        } catch (Exception ignored) {
            return;
        }
        if (type == null || !connection.isOpen()) {
            return;
        }
        PACKET_BUFFER.computeIfAbsent(connection, ignored -> new ArrayList<>()).add(new AggregatedEncodePacket(packet, type));
    }

    private synchronized static void flush() {
        PACKET_BUFFER.entrySet().removeIf(e -> !e.getKey().isOpen());
        PACKET_BUFFER.forEach(AggregationManager::flushInternal);
    }

    public synchronized static void flushConnection(ClientConnection connection) {
        TIMER.execute(() -> {
            synchronized (AggregationManager.class) {
                PACKET_BUFFER.entrySet().removeIf(e -> !e.getKey().isOpen());
                flushInternal(connection, PACKET_BUFFER.get(connection));
            }
        });
    }

    private synchronized static void flushInternal(ClientConnection connection, ArrayList<AggregatedEncodePacket> packets) {
        if (packets == null || packets.isEmpty() || !connection.isOpen()) {
            return;
        }
        try {
            DefaultChannelPipeline pipeline = (DefaultChannelPipeline) ((cn.ussshenzhou.notenoughbandwidth.mixin.ClientConnectionAccessor) (Object) connection).nebl$getChannel().pipeline();
            PacketEncoder encoder = DefaultChannelPipelineHelper.getPacketEncoder(pipeline);
            DecoderHandler decoder = DefaultChannelPipelineHelper.getPacketDecoder(pipeline);
            if (encoder == null || decoder == null) {
                return;
            }
            ArrayList<AggregatedEncodePacket> sendPackets = new ArrayList<>(packets);
            PacketAggregationPacket aggregationPacket = new PacketAggregationPacket(sendPackets, connection);
            packets.clear();
            if (connection.getSide() == net.minecraft.network.NetworkSide.CLIENTBOUND && connection.getPacketListener() instanceof net.minecraft.server.network.ServerPlayNetworkHandler serverHandler) {
                ServerPlayNetworking.send(serverHandler.player, aggregationPacket.toPayload());
                ((cn.ussshenzhou.notenoughbandwidth.mixin.ClientConnectionAccessor) (Object) connection).nebl$getChannel().flush();
            }
        } catch (Exception ignored) {
            packets.clear();
        }
    }

    public static void handleAggregatedPacket(Packet<?> packet, ClientConnection connection) {
        if (!PacketAggregationPacket.isAggregationPacket(packet)) {
            return;
        }
        net.minecraft.network.PacketByteBuf payloadData = PacketUtil.getPayloadData(packet);
        if (payloadData == null) {
            return;
        }
        PacketAggregationPacket aggregationPacket = new PacketAggregationPacket(payloadData);
        aggregationPacket.setBakedSize(payloadData.readableBytes());
        aggregationPacket.decode(connection).forEach(p -> {
            try {
                p.handle(connection.getPacketListener());
            } finally {
                p.getData().release();
            }
        });
    }
}
