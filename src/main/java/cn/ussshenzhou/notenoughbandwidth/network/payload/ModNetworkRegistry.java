package cn.ussshenzhou.notenoughbandwidth.network.payload;

public final class ModNetworkRegistry {
    private ModNetworkRegistry() {
    }

    public static void networkPacketRegistry(PayloadRegistrar registrar) {
        PayloadRegistrar networkThreadRegistrar = registrar.executesOn(HandlerThread.NETWORK);
        networkThreadRegistrar.playBidirectional(NebTransportSetupPayload.REQUEST, NebTransportSetupPayload.CODEC, NebTransportSetupPayload::handle);
        networkThreadRegistrar.playBidirectional(PacketAggregationPayload.SAMPLE, PacketAggregationPayload.CODEC, PacketAggregationPayload::handle);
    }
}
