package cn.ussshenzhou.notenoughbandwidth.network.payload;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregatedDecodePacket;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;

import java.util.ArrayList;

public final class PacketAggregationPayload implements NebPayload {
    public static final PacketAggregationPayload SAMPLE = new PacketAggregationPayload(new PacketBuffer(Unpooled.buffer(0)));
    public static final PayloadCodec<PacketAggregationPayload> CODEC = new PayloadCodec<PacketAggregationPayload>() {
        @Override
        public void encode(PacketBuffer buf, PacketAggregationPayload payload) {
            buf.writeBytes(payload.payload, payload.payload.readerIndex(), payload.payload.readableBytes());
        }

        @Override
        public PacketAggregationPayload decode(PacketBuffer buf) {
            return new PacketAggregationPayload(new PacketBuffer(buf.readBytes(buf.readableBytes())));
        }
    };

    private final PacketBuffer payload;

    public PacketAggregationPayload(PacketBuffer payload) {
        this.payload = payload;
    }

    @Override
    public String type() {
        return PacketAggregationPacket.CHANNEL_NAME;
    }

    public PacketBuffer payload() {
        return payload;
    }

    public static PacketAggregationPayload fromTransportPacket(net.minecraft.network.Packet<?> packet) {
        PacketBuffer payload = PacketAggregationPacket.copyPayload(packet);
        return payload == null ? null : new PacketAggregationPayload(payload);
    }

    public static void handle(PacketAggregationPayload payload, PayloadContext context) {
        if (payload == null || payload.payload == null || context.connection() == null) {
            return;
        }
        PacketBuffer copy = new PacketBuffer(payload.payload.retainedDuplicate());
        try {
            ArrayList<AggregatedDecodePacket> packets = PacketAggregationPacket.decode(context.connection(), copy);
            try {
                for (AggregatedDecodePacket subPacket : packets) {
                    try {
                        subPacket.replay(context);
                    } finally {
                        subPacket.release();
                    }
                }
            } finally {
                packets.clear();
            }
        } finally {
            copy.release();
        }
    }

    public static void send(NetworkManager connection, boolean clientbound, PacketBuffer payload) {
        if (connection == null || payload == null) {
            return;
        }
        PacketBuffer duplicate = new PacketBuffer(payload.retainedDuplicate());
        try {
            PayloadRegistry.send(connection, clientbound, new PacketAggregationPayload(duplicate));
        } finally {
            duplicate.release();
        }
    }
}
