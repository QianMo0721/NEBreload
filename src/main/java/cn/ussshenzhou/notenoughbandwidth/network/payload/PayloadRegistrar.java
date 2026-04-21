package cn.ussshenzhou.notenoughbandwidth.network.payload;

public class PayloadRegistrar {
    private String version;
    private boolean optional;
    private HandlerThread thread = HandlerThread.MAIN;

    public PayloadRegistrar(String version) {
        this.version = version;
    }

    private PayloadRegistrar(PayloadRegistrar source) {
        this.version = source.version;
        this.optional = source.optional;
        this.thread = source.thread;
    }

    public PayloadRegistrar executesOn(HandlerThread thread) {
        PayloadRegistrar clone = new PayloadRegistrar(this);
        clone.thread = thread;
        return clone;
    }

    public PayloadRegistrar versioned(String version) {
        PayloadRegistrar clone = new PayloadRegistrar(this);
        clone.version = version;
        return clone;
    }

    public PayloadRegistrar optional() {
        PayloadRegistrar clone = new PayloadRegistrar(this);
        clone.optional = true;
        return clone;
    }

    public <T extends NebPayload> PayloadRegistrar playToClient(T sample, PayloadCodec<T> codec, PayloadHandler<T> handler) {
        PayloadRegistry.register(new PayloadRegistration<>(sample.type(), codec, wrap(handler), true, false, version, optional, thread));
        return this;
    }

    public <T extends NebPayload> PayloadRegistrar playToServer(T sample, PayloadCodec<T> codec, PayloadHandler<T> handler) {
        PayloadRegistry.register(new PayloadRegistration<>(sample.type(), codec, wrap(handler), false, true, version, optional, thread));
        return this;
    }

    public <T extends NebPayload> PayloadRegistrar playBidirectional(T sample, PayloadCodec<T> codec, PayloadHandler<T> handler) {
        PayloadRegistry.register(new PayloadRegistration<>(sample.type(), codec, wrap(handler), true, true, version, optional, thread));
        return this;
    }

    private <T extends NebPayload> PayloadHandler<T> wrap(PayloadHandler<T> handler) {
        if (thread == HandlerThread.MAIN) {
            return new MainThreadPayloadHandler<>(handler);
        }
        return handler;
    }
}
