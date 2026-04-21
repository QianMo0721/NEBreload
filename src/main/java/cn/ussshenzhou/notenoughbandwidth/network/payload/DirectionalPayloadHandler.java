package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.protocol.PacketFlow;

public record DirectionalPayloadHandler<T extends NebPayload>(
        PayloadHandler<T> clientboundHandler,
        PayloadHandler<T> serverboundHandler
) implements PayloadHandler<T> {
    @Override
    public void handle(T payload, PayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            clientboundHandler.handle(payload, context);
        } else {
            serverboundHandler.handle(payload, context);
        }
    }
}
