package cn.ussshenzhou.notenoughbandwidth.network.payload;

public interface PayloadHandler<T extends NebPayload> {
    void handle(T payload, PayloadContext context);
}
