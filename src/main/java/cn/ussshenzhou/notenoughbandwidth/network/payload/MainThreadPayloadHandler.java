package cn.ussshenzhou.notenoughbandwidth.network.payload;

public class MainThreadPayloadHandler<T extends NebPayload> implements PayloadHandler<T> {
    private final PayloadHandler<T> delegate;

    public MainThreadPayloadHandler(PayloadHandler<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handle(final T payload, final PayloadContext context) {
        if (payload != null && context != null && context.shouldKeepOnNetworkThread(payload.type())) {
            delegate.handle(payload, context);
            return;
        }
        context.enqueueWork(new Runnable() {
            @Override
            public void run() {
                delegate.handle(payload, context);
            }
        });
    }
}
