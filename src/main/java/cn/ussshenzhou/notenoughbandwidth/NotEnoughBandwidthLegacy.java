package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.network.FabricChannelCollector;
import cn.ussshenzhou.notenoughbandwidth.network.NebPayloads;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class NotEnoughBandwidthLegacy implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigHelper.loadConfig(new NotEnoughBandwidthConfig());
        NebPayloads.init();
        AggregationManager.init();
        ServerPlayConnectionEvents.INIT.register((handler, server) -> FabricChannelCollector.initServerPlayChannels(handler));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> FabricChannelCollector.initServerPlayChannels(handler));
        ServerPlayNetworking.registerGlobalReceiver(NebPayloads.STAT_QUERY, (packet, player, responseSender) -> {
            if (hasStatPermission(player)) {
                responseSender.sendPacket(new NebPayloads.StatRespondPayload(
                        SimpleStatManager.LOCAL.inboundBytesBaked().get(),
                        SimpleStatManager.LOCAL.inboundBytesRaw().get(),
                        SimpleStatManager.LOCAL.outboundBytesBaked().get(),
                        SimpleStatManager.LOCAL.outboundBytesRaw().get(),
                        SimpleStatManager.LOCAL.inboundSpeedBaked().averageIn1s(),
                        SimpleStatManager.LOCAL.inboundSpeedRaw().averageIn1s(),
                        SimpleStatManager.LOCAL.outboundSpeedBaked().averageIn1s(),
                        SimpleStatManager.LOCAL.outboundSpeedRaw().averageIn1s()
                ));
            }
        });
    }

    private static boolean hasStatPermission(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2);
    }
}
