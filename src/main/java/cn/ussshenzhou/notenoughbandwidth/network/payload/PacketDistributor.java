package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.PacketFlow;

import java.util.Objects;

public final class PacketDistributor {
    private PacketDistributor() {
    }

    public static void sendToServer(NebPayload payload, NebPayload... payloads) {
        var listener = Objects.requireNonNull(Minecraft.getInstance().getConnection(), "Cannot send serverbound payload before client connection exists");
        PayloadRegistry.send(listener.getConnection(), PacketFlow.SERVERBOUND, payload);
        for (NebPayload otherPayload : payloads) {
            PayloadRegistry.send(listener.getConnection(), PacketFlow.SERVERBOUND, otherPayload);
        }
    }

    public static void sendToPlayer(ServerPlayer player, NebPayload payload, NebPayload... payloads) {
        PayloadRegistry.send(player.connection.connection, PacketFlow.CLIENTBOUND, payload);
        for (NebPayload otherPayload : payloads) {
            PayloadRegistry.send(player.connection.connection, PacketFlow.CLIENTBOUND, otherPayload);
        }
    }
}
