package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.HandshakeMessages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author USS_Shenzhou
 * Initialize NEB only after Forge has accepted the negotiated mod channel list,
 * so both sides derive packet indices from the same PLAY channel set.
 */
@Mixin(HandshakeHandler.class)
public class NetworkRegistryMixin {

    @Inject(method = "handleServerModListOnClient", at = @At("TAIL"), remap = false)
    private void nebInitOnAcceptedServerChannelList(
            HandshakeMessages.S2CModList message,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier,
            CallbackInfo ci) {
        if (!message.getChannels().containsKey(PacketAggregationPacket.TYPE)) {
            return;
        }
        var payloadSetup = PayloadRegistry.buildSetup(message.getChannels());
        NamespaceIndexManager.initFromPayloadSetup(payloadSetup);
        var context = contextSupplier.get();
        if (context != null && context.getNetworkManager() != null) {
            ChannelAttributes.setPayloadSetup(context.getNetworkManager(), payloadSetup);
        }
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
    }

    @Inject(method = "handleClientModListOnServer", at = @At("TAIL"), remap = false)
    private void nebInitOnAcceptedClientChannelList(
            HandshakeMessages.C2SModListReply message,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier,
            CallbackInfo ci) {
        if (!message.getChannels().containsKey(PacketAggregationPacket.TYPE)) {
            return;
        }
        var payloadSetup = PayloadRegistry.buildSetup(message.getChannels());
        NamespaceIndexManager.initFromPayloadSetup(payloadSetup);
        var context = contextSupplier.get();
        if (context != null && context.getNetworkManager() != null) {
            ChannelAttributes.setPayloadSetup(context.getNetworkManager(), payloadSetup);
        }
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
    }
}
