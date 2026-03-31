package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author USS_Shenzhou
 * In Forge 1.20.1, initializes NamespaceIndexManager and AggregationManager
 * when the first player joins (at which point the protocol registry is stable).
 */
@Mixin(PlayerList.class)
public class NetworkRegistryMixin {

    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void nebwInitOnFirstPlayer(
            Connection connection,
            ServerPlayer player,
            CallbackInfo ci) {
        // Initialize index and aggregation manager once when first player connects.
        // NamespaceIndexManager.init() is idempotent (checks initialized flag internally).
        if (!NamespaceIndexManager.isInitialized()) {
            NamespaceIndexManager.initFromRegistry();
        }
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
    }
}
