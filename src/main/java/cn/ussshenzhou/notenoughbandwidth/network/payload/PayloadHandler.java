package cn.ussshenzhou.notenoughbandwidth.network.payload;

@FunctionalInterface
public interface PayloadHandler<T extends NebPayload> {
    void handle(T payload, PayloadContext context);
}
