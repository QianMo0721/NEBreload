package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthConfig;
import net.minecraft.server.PlayerManager;
import net.minecraft.util.math.MathHelper;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerManager.class)
public class PlayerListMixin {
    @ModifyVariable(method = "setViewDistance", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/server/PlayerManager;viewDistance:I",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
    ), argsOnly = true)
    private int nebl$modifyViewDistance(int viewDistance) {
        try {
            int retention = Math.max(0, NotEnoughBandwidthConfig.get().dccDistance);
            return MathHelper.clamp(viewDistance + retention, 2, 32);
        } catch (IllegalStateException ignored) {
            return viewDistance;
        }
    }
}
