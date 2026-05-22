package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PacketDistributor;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistrar;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;

public final class ModNetworkRegistry {
    public static final String PERMISSION_NODE = ModConstants.MOD_ID;
    public static final String STAT_QUERY_CHANNEL = ModConstants.MOD_ID + ":stat_query";
    public static final String STAT_RESP_CHANNEL = ModConstants.MOD_ID + ":stat_resp";

    private ModNetworkRegistry() {
    }

    public static void networkPacketRegistry(PayloadRegistrar registrar) {
        registrar.playToServer(StatQuery.SAMPLE, StatQuery.CODEC, StatQuery::handle);
        registrar.playToClient(StatRespond.SAMPLE, StatRespond.CODEC, StatRespond::handle);
    }

    public static void sendToServer(StatQuery query) {
        if (query == null) {
            query = StatQuery.SAMPLE;
        }
        PacketDistributor.sendToServer(query);
    }

    public static StatRespond createLocalStatSnapshot() {
        return new StatRespond(
                SimpleStatManager.LOCAL.inboundBytesBaked().get(),
                SimpleStatManager.LOCAL.inboundBytesRaw().get(),
                SimpleStatManager.LOCAL.outboundBytesBaked().get(),
                SimpleStatManager.LOCAL.outboundBytesRaw().get(),
                SimpleStatManager.LOCAL.inboundSpeedBaked().averageIn1s(),
                SimpleStatManager.LOCAL.inboundSpeedRaw().averageIn1s(),
                SimpleStatManager.LOCAL.outboundSpeedBaked().averageIn1s(),
                SimpleStatManager.LOCAL.outboundSpeedRaw().averageIn1s()
        );
    }
}
