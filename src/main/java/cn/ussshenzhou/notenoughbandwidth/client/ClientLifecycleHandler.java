package cn.ussshenzhou.notenoughbandwidth.client;

import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.network.payload.ClientPayloadBridge;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebTransportSetupPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Forge 1.12.2 没有现代 Forge 的登录后协商补发钩子，
 * 这里在客户端连入世界后，若仍未拿到 NEB transport，
 * 则周期性补发一次轻量 setup 请求，兼容代理链路。
 */
@SideOnly(Side.CLIENT)
public final class ClientLifecycleHandler {
    private static final int MAX_SETUP_RETRIES = 12;
    private static final int RETRY_INTERVAL_TICKS = 10;

    private static NetworkManager pendingSetupConnection;
    private static int remainingSetupRetries;
    private static int retryCooldown;

    public ClientLifecycleHandler() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        NetworkManager connection = ClientPayloadBridge.getClientNetworkManager();
        if (connection == null) {
            clearPendingState(null);
            return;
        }
        if (pendingSetupConnection != connection) {
            pendingSetupConnection = connection;
            remainingSetupRetries = MAX_SETUP_RETRIES;
            retryCooldown = 0;
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

    private static void trySendFallbackSetup(NetworkManager connection) {
        if (!isConnectionUsable(connection)) {
            clearPendingState(connection);
            return;
        }
        if (ChannelAttributes.hasNebTransport(connection)) {
            clearPendingState(connection);
            return;
        }
        if (connection.channel() == null || connection.channel().attr(NetworkManager.PROTOCOL_ATTRIBUTE_KEY).get() != EnumConnectionState.PLAY) {
            return;
        }
        ChannelAttributes.clearTransportSetupRequested(connection);
        ChannelAttributes.markTransportSetupRequested(connection);
        PayloadRegistry.send(connection, false, NebTransportSetupPayload.REQUEST);
        remainingSetupRetries--;
        retryCooldown = RETRY_INTERVAL_TICKS;
    }

    private static boolean isConnectionUsable(NetworkManager connection) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return connection != null
                && connection.channel() != null
                && connection.channel().isActive()
                && minecraft.getConnection() != null
                && minecraft.player != null;
    }

    private static void clearPendingState(NetworkManager connection) {
        if (connection != null) {
            ChannelAttributes.clearTransportSetupRequested(connection);
        }
        pendingSetupConnection = null;
        remainingSetupRetries = 0;
        retryCooldown = 0;
    }
}
