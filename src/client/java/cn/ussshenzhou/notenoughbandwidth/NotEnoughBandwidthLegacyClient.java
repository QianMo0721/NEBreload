package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.network.StatRespond;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.network.FabricChannelCollector;
import cn.ussshenzhou.notenoughbandwidth.stat.ModKey;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.TreeSet;

public final class NotEnoughBandwidthLegacyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AggregationManager.init();
        ModKey.init();
        StatRespond.initClientReceiver();
        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            Set<Identifier> channels = new TreeSet<>(Identifier::compareTo);
            channels.addAll(ClientPlayNetworking.getReceived());
            channels.addAll(ClientPlayNetworking.getSendable());
            FabricChannelCollector.initFromIdentifiers(channels);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            resetServerStats();
            ModKey.ScreenStateHolder.reset();
        });
    }

    private static void resetServerStats() {
        SimpleStatManager.inboundBytesBakedServer = 0L;
        SimpleStatManager.inboundBytesRawServer = 0L;
        SimpleStatManager.outboundBytesBakedServer = 0L;
        SimpleStatManager.outboundBytesRawServer = 0L;
        SimpleStatManager.inboundSpeedBakedServer = 0.0D;
        SimpleStatManager.inboundSpeedRawServer = 0.0D;
        SimpleStatManager.outboundSpeedBakedServer = 0.0D;
        SimpleStatManager.outboundSpeedRawServer = 0.0D;
    }
}
