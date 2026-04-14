package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.chunk.CachedChunkTrackingView;
import cn.ussshenzhou.notenoughbandwidth.util.EncodedTrafficStatHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mixin(value = PlayerChunkMap.class)
public abstract class PlayerChunkMapMixin {
    @Shadow
    @Final
    private List<EntityPlayerMP> players;

    @Shadow
    private int playerViewRadius;

    @Shadow
    public abstract PlayerChunkMapEntry getEntry(int x, int z);

    @Shadow
    private PlayerChunkMapEntry getOrCreateEntry(int x, int z) {
        throw new AssertionError();
    }

    @Shadow
    public abstract boolean isPlayerWatchingChunk(EntityPlayerMP player, int chunkX, int chunkZ);

    @Inject(method = "tick", at = @At("TAIL"))
    private void nebTickCachedChunks(CallbackInfo ci) {
        NotEnoughBandwidthLegacyConfig cfg = NotEnoughBandwidthLegacyConfig.get();
        if (!cfg.isDelayedChunkCachingUsable()) {
            return;
        }
        ArrayList<EntityPlayerMP> snapshot = new ArrayList<EntityPlayerMP>(this.players);
        for (EntityPlayerMP player : snapshot) {
            nebRefreshChunkTracking(player, cfg);
            CachedChunkTrackingView.tick(player, nebCurrentCenter(player), new Consumer<ChunkPos>() {
                @Override
                public void accept(ChunkPos pos) {
                    PlayerChunkMapEntry entry = getEntry(pos.x, pos.z);
                    if (entry != null && entry.containsPlayer(player)) {
                        entry.removePlayer(player);
                    }
                }
            });
        }
    }

    @Inject(method = "addPlayer", at = @At("TAIL"))
    private void nebOnAddPlayer(EntityPlayerMP player, CallbackInfo ci) {
        NotEnoughBandwidthLegacyConfig cfg = NotEnoughBandwidthLegacyConfig.get();
        if (!cfg.isDelayedChunkCachingUsable()) {
            return;
        }
        nebRefreshChunkTracking(player, cfg);
    }

    @Inject(method = "updateMovingPlayer", at = @At("TAIL"))
    private void nebOnUpdateMovingPlayer(EntityPlayerMP player, CallbackInfo ci) {
        NotEnoughBandwidthLegacyConfig cfg = NotEnoughBandwidthLegacyConfig.get();
        if (!cfg.isDelayedChunkCachingUsable()) {
            return;
        }
        nebRefreshChunkTracking(player, cfg);
    }

    @Inject(method = "setPlayerViewRadius", at = @At("TAIL"))
    private void nebOnSetPlayerViewRadius(int viewDistance, CallbackInfo ci) {
        NotEnoughBandwidthLegacyConfig cfg = NotEnoughBandwidthLegacyConfig.get();
        if (!cfg.isDelayedChunkCachingUsable()) {
            return;
        }
        ArrayList<EntityPlayerMP> snapshot = new ArrayList<EntityPlayerMP>(this.players);
        for (EntityPlayerMP player : snapshot) {
            nebRefreshChunkTracking(player, cfg);
        }
    }

    @Inject(method = "removePlayer", at = @At("HEAD"))
    private void nebClearCachedChunks(EntityPlayerMP player, CallbackInfo ci) {
        CachedChunkTrackingView.clear(player, new Consumer<ChunkPos>() {
            @Override
            public void accept(ChunkPos pos) {
                PlayerChunkMapEntry entry = getEntry(pos.x, pos.z);
                if (entry != null && entry.containsPlayer(player)) {
                    entry.removePlayer(player);
                }
            }
        });
    }

    private void nebRefreshChunkTracking(EntityPlayerMP player, NotEnoughBandwidthLegacyConfig cfg) {
        ChunkPos center = nebCurrentCenter(player);
        int expandedRadius = this.playerViewRadius;
        int baseRadius = Math.max(0, expandedRadius - cfg.getDccDistanceSafe());

        for (int x = center.x - expandedRadius; x <= center.x + expandedRadius; x++) {
            for (int z = center.z - expandedRadius; z <= center.z + expandedRadius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                int distance = nebChessboardDistance(center, pos);
                boolean watching = isPlayerWatchingChunk(player, x, z);
                if (distance <= baseRadius) {
                    CachedChunkTrackingView.onChunkEnter(player, pos);
                    if (!watching) {
                        getOrCreateEntry(x, z).addPlayer(player);
                    }
                } else if (watching) {
                    PlayerChunkMapEntry entry = getEntry(x, z);
                    int cachedChunkRawSize = nebEstimateCachedChunkRawSize(entry);
                    if (!CachedChunkTrackingView.onChunkLeave(player, pos, center, cachedChunkRawSize)) {
                        if (entry != null && entry.containsPlayer(player)) {
                            entry.removePlayer(player);
                        }
                    }
                }
            }
        }
    }

    private static ChunkPos nebCurrentCenter(EntityPlayerMP player) {
        return new ChunkPos(((int) player.posX) >> 4, ((int) player.posZ) >> 4);
    }

    private static int nebChessboardDistance(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    private static int nebEstimateCachedChunkRawSize(PlayerChunkMapEntry entry) {
        if (entry == null || entry.getChunk() == null) {
            return 0;
        }
        return EncodedTrafficStatHelper.estimateChunkPacketRawSize(entry.getChunk());
    }
}
