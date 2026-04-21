package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;

public record PayloadRegistration<T extends NebPayload>(
        ResourceLocation id,
        PayloadCodec<T> codec,
        PayloadHandler<T> handler,
        boolean clientbound,
        boolean serverbound,
        String version,
        boolean optional,
        HandlerThread thread
) {
    public boolean matches(boolean toClient, boolean toServer) {
        return (!toClient || clientbound) && (!toServer || serverbound);
    }

    public boolean matches(PacketFlow flow) {
        return switch (flow) {
            case CLIENTBOUND -> clientbound;
            case SERVERBOUND -> serverbound;
        };
    }
}
