package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkDispatcher.class, remap = false)
public class NetworkDispatcherMixin {
    @Inject(method = "completeClientSideConnection", at = @At("TAIL"), remap = false)
    private void nebInitIndexOnClientConnectionEstablished(NetworkDispatcher.ConnectionType connectionType, CallbackInfo ci) {
        nebEnsureInitialized(connectionType);
    }

    @Inject(method = "completeServerSideConnection", at = @At("TAIL"), remap = false)
    private void nebInitIndexOnServerConnectionEstablished(NetworkDispatcher.ConnectionType connectionType, CallbackInfo ci) {
        nebEnsureInitialized(connectionType);
    }

    private static void nebEnsureInitialized(NetworkDispatcher.ConnectionType connectionType) {
        if (connectionType != NetworkDispatcher.ConnectionType.MODDED) {
            return;
        }
        if (NamespaceIndexManager.isInitialized()) {
            NamespaceIndexManager.refreshFromRegisteredChannels();
        } else {
            NamespaceIndexManager.initFromRegisteredChannels();
        }
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
    }
}
