package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Unique
    private static volatile Field neb$viewDistanceField;

    @Unique
    private static int neb$effectiveViewDistance(int viewDistance) {
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

    @Unique
    private static Field neb$resolveViewDistanceField() {
        Field field = neb$viewDistanceField;
        if (field != null) {
            return field;
        }
        try {
            try {
                field = PlayerList.class.getDeclaredField("viewDistance");
            } catch (NoSuchFieldException ignored) {
                field = PlayerList.class.getDeclaredField("f_11207_");
            }
            field.setAccessible(true);
            neb$viewDistanceField = field;
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve PlayerList viewDistance field", e);
        }
    }

    @Unique
    private static void neb$setViewDistanceField(PlayerList playerList, int viewDistance) {
        try {
            neb$resolveViewDistanceField().setInt(playerList, viewDistance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to update PlayerList viewDistance field", e);
        }
    }

    @Inject(method = "setViewDistance", at = @At("HEAD"), cancellable = true)
    private void neb$setViewDistance(int viewDistance, CallbackInfo ci) {
        PlayerList playerList = (PlayerList) (Object) this;
        int effectiveViewDistance = neb$effectiveViewDistance(viewDistance);
        neb$setViewDistanceField(playerList, effectiveViewDistance);
        playerList.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(effectiveViewDistance));
        for (ServerLevel level : playerList.getServer().getAllLevels()) {
            if (level != null) {
                level.getChunkSource().setViewDistance(effectiveViewDistance);
            }
        }
        ci.cancel();
    }
}
