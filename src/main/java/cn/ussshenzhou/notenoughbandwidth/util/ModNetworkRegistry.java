package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistrar;

/**
 * Aggregation transport registry only.
 */
public class ModNetworkRegistry {

    public static void networkPacketRegistry(PayloadRegistrar registrar) {
        registrar.playBidirectional(
                PacketAggregationPacket.SAMPLE,
                PacketAggregationPacket.CODEC,
                PacketAggregationPacket::handle
        );
    }

}
