package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.chunk.CachedChunkTrackingView;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Burning_TNT
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract int getPlayerViewDistance(ServerPlayer player);

    @Shadow
    protected abstract void markChunkPendingToSend(ServerPlayer player, ChunkPos pos);

    @Shadow
    private static void dropChunk(ServerPlayer player, ChunkPos pos) {
    }

    @Shadow
    public abstract net.minecraft.server.level.DistanceManager getDistanceManager();

    @Unique
    private static TicketType TICKET_TYPE;

    /**
     * @author Burning_TNT
     * @reason NEBL overwrites original chunk map update strategy only when DCC is enabled.
     */
    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void neblUpdateChunkTracking(ServerPlayer player, CallbackInfo ci) {
        if (player.level() != this.level || !NotEnoughBandwidthLegacyConfig.get().dccEnabled) {
            return;
        }

        CachedChunkTrackingView.onUpdateChunkTracking(player, getPlayerViewDistance(player), new CachedChunkTrackingView.Context() {
            @Override
            public void startChunkTracking(ChunkPos pos) {
                markChunkPendingToSend(player, pos);
            }

            @Override
            public void stopChunkTracking(ChunkPos pos) {
                dropChunk(player, pos);
            }

            @Override
            public void putTicket(ChunkPos pos, int ticks) {
                TicketType ticketType = TICKET_TYPE;
                if (ticketType == null || ticketType.timeout() != ticks) {
                    ticketType = TICKET_TYPE = TicketType.create("nebl_cache", (posA, posB) -> 0, ticks);
                }
                getDistanceManager().addRegionTicket(ticketType, pos, 1, pos);
            }
        });
        ci.cancel();
    }
}
