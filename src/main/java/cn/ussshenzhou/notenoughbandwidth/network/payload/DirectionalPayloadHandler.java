package cn.ussshenzhou.notenoughbandwidth.network.payload;

public class DirectionalPayloadHandler<T extends NebPayload> implements PayloadHandler<T> {
    private final PayloadHandler<T> clientboundHandler;
    private final PayloadHandler<T> serverboundHandler;

    public DirectionalPayloadHandler(PayloadHandler<T> clientboundHandler, PayloadHandler<T> serverboundHandler) {
        this.clientboundHandler = clientboundHandler;
        this.serverboundHandler = serverboundHandler;
    }

    @Override
    public void handle(T payload, PayloadContext context) {
        if (context.clientbound()) {
            clientboundHandler.handle(payload, context);
        } else {
            serverboundHandler.handle(payload, context);
        }
    }
}
