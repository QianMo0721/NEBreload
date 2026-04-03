package cn.ussshenzhou.notenoughbandwidth.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class NebPayloads {
    public static final Identifier PACKET_AGGREGATION_ID = Identifier.of(ModConstants.MOD_ID, "packet_aggregation_packet");
    public static final PacketType<PacketAggregationPayload> PACKET_AGGREGATION = PacketType.create(PACKET_AGGREGATION_ID, PacketAggregationPayload::new);
    public static final PacketType<StatQueryPayload> STAT_QUERY = PacketType.create(Identifier.of(ModConstants.MOD_ID, "stat_query"), StatQueryPayload::new);
    public static final PacketType<StatRespondPayload> STAT_RESPOND = PacketType.create(Identifier.of(ModConstants.MOD_ID, "stat_resp"), StatRespondPayload::new);

    private NebPayloads() {
    }

    public static void init() {
    }

    public record PacketAggregationPayload(PacketByteBuf data) implements FabricPacket {
        public PacketAggregationPayload {
            data = new PacketByteBuf(data.copy());
        }

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeBytes(data, data.readerIndex(), data.readableBytes());
        }

        @Override
        public PacketType<?> getType() {
            return PACKET_AGGREGATION;
        }
    }

    public record StatQueryPayload() implements FabricPacket {
        public StatQueryPayload(PacketByteBuf buf) {
            this();
        }

        @Override
        public void write(PacketByteBuf buf) {
        }

        @Override
        public PacketType<?> getType() {
            return STAT_QUERY;
        }
    }

    public record StatRespondPayload(
            long inboundBytesBakedServer,
            long inboundBytesRawServer,
            long outboundBytesBakedServer,
            long outboundBytesRawServer,
            double inboundSpeedBakedServer,
            double inboundSpeedRawServer,
            double outboundSpeedBakedServer,
            double outboundSpeedRawServer
    ) implements FabricPacket {
        public StatRespondPayload(PacketByteBuf buf) {
            this(
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            );
        }

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeVarLong(inboundBytesBakedServer);
            buf.writeVarLong(inboundBytesRawServer);
            buf.writeVarLong(outboundBytesBakedServer);
            buf.writeVarLong(outboundBytesRawServer);
            buf.writeDouble(inboundSpeedBakedServer);
            buf.writeDouble(inboundSpeedRawServer);
            buf.writeDouble(outboundSpeedBakedServer);
            buf.writeDouble(outboundSpeedRawServer);
        }

        @Override
        public PacketType<?> getType() {
            return STAT_RESPOND;
        }
    }
}
