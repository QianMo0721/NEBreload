package cn.ussshenzhou.notenoughbandwidth.network.payload;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;

public final class ModNetworkRegistry {
    private ModNetworkRegistry() {
    }

    public static void networkPacketRegistry(PayloadRegistrar registrar) {
        PayloadRegistrar networkThreadRegistrar = registrar.executesOn(HandlerThread.NETWORK);
        networkThreadRegistrar.playBidirectional(NebTransportSetupPayload.REQUEST, NebTransportSetupPayload.CODEC, NebTransportSetupPayload::handle);
        networkThreadRegistrar.playBidirectional(PacketAggregationPayloadRegistration.SAMPLE, PacketAggregationPayloadRegistration.CODEC, PacketAggregationPayloadRegistration::handle);
    }

    public static final class PacketAggregationPayloadRegistration implements NebPayload {
        static final PacketAggregationPayloadRegistration SAMPLE = new PacketAggregationPayloadRegistration();
        static final PayloadCodec<PacketAggregationPayloadRegistration> CODEC = new PayloadCodec<PacketAggregationPayloadRegistration>() {
            @Override
            public void encode(net.minecraft.network.PacketBuffer buf, PacketAggregationPayloadRegistration payload) {
            }

            @Override
            public PacketAggregationPayloadRegistration decode(net.minecraft.network.PacketBuffer buf) {
                return SAMPLE;
            }
        };

        @Override
        public String type() {
            return PacketAggregationPacket.CHANNEL_NAME;
        }

        public static void handle(PacketAggregationPayloadRegistration payload, PayloadContext context) {
        }
    }
}
