package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.PacketBuffer;

public class MainThreadPayloadHandler<T extends NebPayload> implements PayloadHandler<T> {
    private final PayloadHandler<T> delegate;

    public MainThreadPayloadHandler(PayloadHandler<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handle(final T payload, final PayloadContext context) {
        context.enqueueWork(new Runnable() {
            @Override
            public void run() {
                delegate.handle(payload, context);
            }
        });
    }
}
