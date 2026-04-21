package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.network.payload.PacketDistributor;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistrar;

/**
 * Debug/statistics payloads are kept off the aggregation transport channel.
 */
public class ModNetworkRegistry {

    public static void networkPacketRegistry(PayloadRegistrar registrar) {
        registrar.playToServer(StatQuery.SAMPLE, StatQuery.CODEC, StatQuery::handle);
        registrar.playToClient(StatRespond.SAMPLE, StatRespond.CODEC, StatRespond::handle);
    }

    public static void sendToServer(StatQuery query) {
        PacketDistributor.sendToServer(query);
    }
}
