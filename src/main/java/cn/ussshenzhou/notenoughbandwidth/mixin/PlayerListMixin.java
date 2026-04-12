package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import net.minecraft.server.management.PlayerList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = PlayerList.class)
public class PlayerListMixin {
    @ModifyVariable(method = "setViewDistance", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/server/management/PlayerList;viewDistance:I",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
    ), argsOnly = true)
    private int nebExpandViewDistance(int viewDistance) {
        try {
            NotEnoughBandwidthLegacyConfig cfg = NotEnoughBandwidthLegacyConfig.get();
            if (!cfg.isDelayedChunkCachingUsable()) {
                return viewDistance;
            }
            return viewDistance + cfg.getDccDistanceSafe();
        } catch (IllegalStateException e) {
            return viewDistance;
        }
    }
}
