package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @ModifyVariable(method = "setViewDistance", at = @At("HEAD"), argsOnly = true)
    private int neb$modifyViewDistance(int viewDistance) {
        try {
            var cfg = NotEnoughBandwidthLegacyConfig.get();
            if (!cfg.isDelayedChunkCachingUsable()) {
                return viewDistance;
            }
            return viewDistance + cfg.getDccDistanceSafe();
        } catch (IllegalStateException e) {
            return viewDistance;
        }
    }
}
