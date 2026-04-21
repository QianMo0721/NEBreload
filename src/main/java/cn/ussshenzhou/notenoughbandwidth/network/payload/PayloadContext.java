package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public record PayloadContext(
        @Nullable Connection connection,
        @Nullable PacketListener listener,
        PacketFlow flow,
        @Nullable Supplier<NetworkEvent.Context> forgeContextSupplier
) {
    public static PayloadContext of(Connection connection, PacketListener listener, PacketFlow flow) {
        return new PayloadContext(connection, listener, flow, null);
    }

    public static PayloadContext of(Connection connection, PacketListener listener, PacketFlow flow, Supplier<NetworkEvent.Context> forgeContextSupplier) {
        return new PayloadContext(connection, listener, flow, forgeContextSupplier);
    }

    @Nullable
    public NetworkEvent.Context forgeContext() {
        return forgeContextSupplier == null ? null : forgeContextSupplier.get();
    }

    public CompletableFuture<Void> enqueueWork(Runnable runnable) {
        NetworkEvent.Context context = forgeContext();
        if (context != null) {
            return context.enqueueWork(runnable);
        }
        runnable.run();
        return CompletableFuture.completedFuture(null);
    }

    public <T> CompletableFuture<T> enqueueWork(Supplier<T> supplier) {
        NetworkEvent.Context context = forgeContext();
        if (context != null) {
            CompletableFuture<T> future = new CompletableFuture<>();
            context.enqueueWork(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                    if (throwable instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new RuntimeException(throwable);
                }
            });
            return future;
        }
        return CompletableFuture.completedFuture(supplier.get());
    }

    @Nullable
    public Player player() {
        if (listener instanceof ServerGamePacketListenerImpl serverListener) {
            return serverListener.player;
        }
        if (flow == PacketFlow.CLIENTBOUND) {
            return clientPlayer();
        }
        return null;
    }

    @Nullable
    private static Player clientPlayer() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Object player = minecraftClass.getField("player").get(minecraft);
            return player instanceof Player p ? p : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    public void disconnect(Component reason) {
        if (connection != null) {
            connection.disconnect(reason);
        }
    }

    public void reply(NebPayload payload) {
        if (connection == null || payload == null) {
            return;
        }
        PacketFlow replyFlow = flow == PacketFlow.CLIENTBOUND ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND;
        PayloadRegistry.send(connection, replyFlow, payload);
    }

    public void handle(NebPayload payload) {
        PayloadRegistry.dispatchPayload(payload, this);
    }
}
