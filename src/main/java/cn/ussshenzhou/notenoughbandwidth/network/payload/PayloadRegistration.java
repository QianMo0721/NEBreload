package cn.ussshenzhou.notenoughbandwidth.network.payload;

public class PayloadRegistration<T extends NebPayload> {
    private final String id;
    private final PayloadCodec<T> codec;
    private final PayloadHandler<T> handler;
    private final boolean clientbound;
    private final boolean serverbound;
    private final String version;
    private final boolean optional;
    private final HandlerThread thread;

    public PayloadRegistration(String id, PayloadCodec<T> codec, PayloadHandler<T> handler,
                               boolean clientbound, boolean serverbound,
                               String version, boolean optional, HandlerThread thread) {
        this.id = id;
        this.codec = codec;
        this.handler = handler;
        this.clientbound = clientbound;
        this.serverbound = serverbound;
        this.version = version;
        this.optional = optional;
        this.thread = thread;
    }

    public String id() {
        return id;
    }

    public PayloadCodec<T> codec() {
        return codec;
    }

    public PayloadHandler<T> handler() {
        return handler;
    }

    public boolean clientbound() {
        return clientbound;
    }

    public boolean serverbound() {
        return serverbound;
    }

    public String version() {
        return version;
    }

    public boolean optional() {
        return optional;
    }

    public HandlerThread thread() {
        return thread;
    }

    public boolean matches(boolean inboundClientbound) {
        return inboundClientbound ? clientbound : serverbound;
    }
}
