package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.chunk.CachedChunkTrackingView;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author USS_Shenzhou
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    public abstract DistanceManager getDistanceManager();

    private static final Map<Integer, TicketType<Integer>> NEB_CACHE_TICKETS = new ConcurrentHashMap<>();

    private static TicketType<Integer> getCacheTicketType(int ticks) {
        return NEB_CACHE_TICKETS.computeIfAbsent(ticks,
                t -> TicketType.create("neb_cache_" + t, Integer::compare, t));
    }

    /**
     * @author Burning_TNT
     * @reason NEB overwrites original chunk map update strategy.
     */
    @Overwrite
    private void updateChunkTracking(ServerPlayer player) {
        if (player.level() != this.level) {
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
                TicketType<Integer> type = getCacheTicketType(ticks);
                getDistanceManager().addRegionTicket(type, pos, 1, ticks);
            }
        });
    }

    @Shadow
    protected abstract int getPlayerViewDistance(ServerPlayer player);

    @Shadow
    protected abstract void markChunkPendingToSend(ServerPlayer player, ChunkPos pos);

    @Shadow
    protected abstract void dropChunk(ServerPlayer player, ChunkPos pos);
}
