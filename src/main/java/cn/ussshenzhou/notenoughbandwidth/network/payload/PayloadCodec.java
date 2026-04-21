package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.FriendlyByteBuf;

public interface PayloadCodec<T extends NebPayload> {
    void encode(FriendlyByteBuf buf, T payload);

    T decode(FriendlyByteBuf buf);
}
