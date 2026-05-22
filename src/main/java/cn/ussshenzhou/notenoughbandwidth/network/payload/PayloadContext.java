package cn.ussshenzhou.notenoughbandwidth.network.payload;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;

public class PayloadContext {
    @Nullable
    private final NetworkManager connection;
    @Nullable
    private final INetHandler listener;
    private final boolean clientbound;

    public PayloadContext(@Nullable NetworkManager connection, @Nullable INetHandler listener, boolean clientbound) {
        this.connection = connection;
        this.listener = listener;
        this.clientbound = clientbound;
    }

    public static PayloadContext of(@Nullable NetworkManager connection, @Nullable INetHandler listener, boolean clientbound) {
        return new PayloadContext(connection, listener, clientbound);
    }

    @Nullable
    public NetworkManager connection() {
        return connection;
    }

    @Nullable
    public INetHandler listener() {
        return listener;
    }

    public boolean clientbound() {
        return clientbound;
    }

    public void enqueueWork(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (listener instanceof INetHandlerPlayServer) {
            EntityPlayer currentPlayer = resolveServerPlayer();
            if (currentPlayer instanceof EntityPlayerMP && ((EntityPlayerMP) currentPlayer).getServerWorld() != null) {
                ((EntityPlayerMP) currentPlayer).getServerWorld().addScheduledTask(runnable);
                return;
            }
        }
        if (clientbound) {
            ClientPayloadWork.enqueue(runnable);
            return;
        }
        runnable.run();
    }

    @Nullable
    public EntityPlayer player() {
        EntityPlayer serverPlayer = resolveServerPlayer();
        if (serverPlayer != null) {
            return serverPlayer;
        }
        return clientbound ? ClientPayloadWork.getClientPlayer() : null;
    }

    @Nullable
    private EntityPlayer resolveServerPlayer() {
        if (listener instanceof NetHandlerPlayServer) {
            return ((NetHandlerPlayServer) listener).player;
        }
        return null;
    }

    public boolean shouldKeepOnNetworkThread(String payloadType) {
        return clientbound && NotEnoughBandwidthLegacyConfig.shouldKeepCustomPayloadOnNetworkThread(payloadType);
    }

    public void disconnect(ITextComponent reason) {
        if (connection != null) {
            connection.closeChannel(reason);
        }
    }

    public void reply(NebPayload payload) {
        if (connection == null || payload == null) {
            return;
        }
        PayloadRegistry.send(connection, !clientbound, payload);
    }

    public void handle(NebPayload payload) {
        PayloadRegistry.dispatchPayload(payload, this);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void handlePacket(Packet<?> packet) {
        if (packet == null || listener == null) {
            return;
        }
        ((Packet) packet).processPacket(listener);
    }

    @Nullable
    public PacketBuffer createPayloadBuffer() {
        if (connection == null) {
            return null;
        }
        return new PacketBuffer(io.netty.buffer.Unpooled.buffer());
    }
}
