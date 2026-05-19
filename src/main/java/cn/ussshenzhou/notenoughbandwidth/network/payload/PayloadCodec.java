package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.PacketBuffer;

public interface PayloadCodec<T extends NebPayload> {
    void encode(PacketBuffer buf, T payload);

    T decode(PacketBuffer buf);
}
