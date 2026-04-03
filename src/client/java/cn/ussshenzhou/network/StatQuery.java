package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.network.NebPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class StatQuery {
    private StatQuery() {
    }

    public static void send() {
        ClientPlayNetworking.send(new NebPayloads.StatQueryPayload());
    }
}
