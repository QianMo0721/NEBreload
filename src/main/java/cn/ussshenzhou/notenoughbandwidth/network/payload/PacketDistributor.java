package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

public final class PacketDistributor {
    private PacketDistributor() {
    }

    public static void sendToServer(NebPayload payload, NebPayload... payloads) {
        PayloadRegistry.sendToServer(payload, payloads);
    }

    public static void sendToPlayer(EntityPlayerMP player, NebPayload payload, NebPayload... payloads) {
        PayloadRegistry.sendToPlayer(player, payload, payloads);
    }
}
