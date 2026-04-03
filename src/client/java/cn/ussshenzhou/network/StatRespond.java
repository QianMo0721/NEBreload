package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.network.NebPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class StatRespond {
    private StatRespond() {
    }

    public static void initClientReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(NebPayloads.STAT_RESPOND, (packet, player, responseSender) -> {
            if (player == null) {
                return;
            }
            cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.inboundBytesBakedServer = packet.inboundBytesBakedServer();
            cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.inboundBytesRawServer = packet.inboundBytesRawServer();
            cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.outboundBytesBakedServer = packet.outboundBytesBakedServer();
            cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.outboundBytesRawServer = packet.outboundBytesRawServer();
            cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.inboundSpeedBakedServer = packet.inboundSpeedBakedServer();
            cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.inboundSpeedRawServer = packet.inboundSpeedRawServer();
            cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.outboundSpeedBakedServer = packet.outboundSpeedBakedServer();
            cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.outboundSpeedRawServer = packet.outboundSpeedRawServer();
        });
    }
}
