package cn.ussshenzhou.notenoughbandwidth.network.payload;

public record MainThreadPayloadHandler<T extends NebPayload>(PayloadHandler<T> handler) implements PayloadHandler<T> {
    @Override
    public void handle(T payload, PayloadContext context) {
        context.enqueueWork(() -> handler.handle(payload, context));
    }
}
