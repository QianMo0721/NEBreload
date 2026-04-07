package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
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
    private void nebwInitOnAcceptedServerChannelList(
            HandshakeMessages.S2CModList message,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier,
            CallbackInfo ci) {
        if (!message.getChannels().containsKey(PacketAggregationPacket.TYPE)) {
            return;
        }
        NamespaceIndexManager.initFromNegotiatedChannels(message.getChannels());
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
    }

    @Inject(method = "handleClientModListOnServer", at = @At("TAIL"), remap = false)
    private void nebwInitOnAcceptedClientChannelList(
            HandshakeMessages.C2SModListReply message,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier,
            CallbackInfo ci) {
        if (!message.getChannels().containsKey(PacketAggregationPacket.TYPE)) {
            return;
        }
        NamespaceIndexManager.initFromNegotiatedChannels(message.getChannels());
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
    }
}
