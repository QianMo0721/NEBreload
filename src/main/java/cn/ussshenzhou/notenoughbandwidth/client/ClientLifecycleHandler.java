package cn.ussshenzhou.notenoughbandwidth.client;

import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebTransportSetupPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 直连时，NEB 会在 FML 握手完成后由 {@code HandshakeHandler} mixin 初始化。
 *
 * Velocity -> Forge 子服链路下，现代 Forge hostname token 可能不会被完整保留，
 * 于是 PLAY 阶段看不到协商后的 payload setup，导致聚合优化永远不启用。
 * 这里在客户端登录完成后补发一次轻量协商请求，仅在尚未拿到 NEB transport 时触发。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientLifecycleHandler {

    private static final int MAX_SETUP_RETRIES = 12;
    private static final int RETRY_INTERVAL_TICKS = 10;
    private static Connection pendingSetupConnection;
    private static int remainingSetupRetries;
    private static int retryCooldown;

    private ClientLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Connection connection = event.getConnection();
        if (connection == null) {
            return;
        }
        pendingSetupConnection = connection;
        remainingSetupRetries = MAX_SETUP_RETRIES;
        retryCooldown = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Connection connection = pendingSetupConnection;
        if (connection == null) {
            return;
        }
        if (ChannelAttributes.hasNebTransport(connection) || !isConnectionUsable(connection)) {
            clearPendingState(connection);
            return;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        if (remainingSetupRetries <= 0) {
            clearPendingState(connection);
            return;
        }
        trySendFallbackSetup(connection);
    }

    private static void trySendFallbackSetup(Connection connection) {
        if (!isConnectionUsable(connection)) {
            clearPendingState(connection);
            return;
        }
        if (ChannelAttributes.hasNebTransport(connection)) {
            clearPendingState(connection);
            return;
        }
        if (connection.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get() != ConnectionProtocol.PLAY) {
            return;
        }
        ChannelAttributes.clearTransportSetupRequested(connection);
        ChannelAttributes.markTransportSetupRequested(connection);
        PayloadRegistry.send(connection, PacketFlow.SERVERBOUND, NebTransportSetupPayload.REQUEST);
        remainingSetupRetries--;
        retryCooldown = RETRY_INTERVAL_TICKS;
    }

    private static boolean isConnectionUsable(Connection connection) {
        return connection != null
                && connection.channel() != null
                && connection.channel().isActive()
                && Minecraft.getInstance().getConnection() != null
                && Minecraft.getInstance().player != null;
    }

    private static void clearPendingState(Connection connection) {
        if (connection != null) {
            ChannelAttributes.clearTransportSetupRequested(connection);
        }
        pendingSetupConnection = null;
        remainingSetupRetries = 0;
        retryCooldown = 0;
    }
}
