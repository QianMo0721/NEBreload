package cn.ussshenzhou.notenoughbandwidth.chunk;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Forge 1.20.1 adaptation of delayed chunk caching.
 *
 * <p>Unlike the original project, 1.20.1 does not expose
 * `ChunkTrackingView`, so this class only keeps the cache state and lets
 * `ChunkMapMixin` continue using vanilla's original range-diff loops.</p>
 */
public class CachedChunkTrackingView {
    private static final long NO_CACHE = -1L;
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final WeakHashMap<ServerPlayer, CachedChunkTrackingView> PLAYER_CACHE_VIEWS = new WeakHashMap<>();

    private final Long2ObjectLinkedOpenHashMap<CacheEntry> cache = new Long2ObjectLinkedOpenHashMap<>();

    private CachedChunkTrackingView() {
        cache.defaultReturnValue(null);
    }

    private static CachedChunkTrackingView get(ServerPlayer player) {
        return PLAYER_CACHE_VIEWS.computeIfAbsent(player, ignored -> new CachedChunkTrackingView());
    }

    public static int onChunkEnter(ServerPlayer player, ChunkPos pos) {
        CachedChunkTrackingView cachedView = PLAYER_CACHE_VIEWS.get(player);
        if (cachedView == null) {
            return -1;
        }

        long packed = pos.toLong();
        CacheEntry hit = cachedView.cache.remove(packed);
        if (hit == null) {
            return -1;
        }

        LOGGER.trace("Cache hit at {} in {}'s chunk cache.", pos, player.getGameProfile().getName());
        return hit.estimatedBodySize();
    }

    public static boolean onChunkLeave(ServerPlayer player, ChunkPos pos, ChunkPos currentCenter, int estimatedBodySize) {
        var cfg = NotEnoughBandwidthLegacyConfig.get();
        int chunkCacheDistance = cfg.getDccDistanceSafe();
        if (!cfg.isDelayedChunkCachingUsable()) {
            return false;
        }
        if (chessboardDist(currentCenter, pos) > chunkCacheDistance) {
            return false;
        }

        CachedChunkTrackingView cachedView = get(player);
        cachedView.cache.putAndMoveToLast(pos.toLong(), new CacheEntry(System.currentTimeMillis(), Math.max(0, estimatedBodySize)));
        LOGGER.trace("Caching {} in {}'s chunk cache.", pos, player.getGameProfile().getName());
        return true;
    }

    public static void tick(ServerPlayer player, ChunkPos currentCenter, Consumer<ChunkPos> stopChunkTracking) {
        CachedChunkTrackingView cachedView = PLAYER_CACHE_VIEWS.get(player);
        if (cachedView == null) {
            return;
        }

        var cfg = NotEnoughBandwidthLegacyConfig.get();
        long now = System.currentTimeMillis();
        int chunkCacheDistance = cfg.getDccDistanceSafe();
        int chunkCacheBufferSize = cfg.getDccSizeLimitSafe();
        long timeoutMs = TimeUnit.SECONDS.toMillis(cfg.getDccTimeoutSafeSeconds());

        ObjectIterator<Long2ObjectMap.Entry<CacheEntry>> it = Long2ObjectMaps.fastIterator(cachedView.cache);
        while (it.hasNext()) {
            Long2ObjectMap.Entry<CacheEntry> entry = it.next();
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()));

            if (chessboardDist(currentCenter, chunkPos) > chunkCacheDistance) {
                it.remove();
                stopChunkTracking.accept(chunkPos);
                LOGGER.trace("Remove {} from {}'s chunk cache: too far away.", chunkPos, player.getGameProfile().getName());
                continue;
            }

            if (now - entry.getValue().cachedAtMs() > timeoutMs) {
                it.remove();
                stopChunkTracking.accept(chunkPos);
                LOGGER.trace("Remove {} from {}'s chunk cache: timeout.", chunkPos, player.getGameProfile().getName());
            }
        }

        while (cachedView.cache.size() > chunkCacheBufferSize) {
            long pos = cachedView.cache.firstLongKey();
            cachedView.cache.remove(pos);
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(pos), ChunkPos.getZ(pos));
            stopChunkTracking.accept(chunkPos);
            LOGGER.trace("Remove {} from {}'s chunk cache: buffer is full.", chunkPos, player.getGameProfile().getName());
        }
    }

    public static void clear(ServerPlayer player, Consumer<ChunkPos> stopChunkTracking) {
        CachedChunkTrackingView cachedView = PLAYER_CACHE_VIEWS.remove(player);
        if (cachedView == null) {
            return;
        }

        ObjectIterator<Long2ObjectMap.Entry<CacheEntry>> it = Long2ObjectMaps.fastIterator(cachedView.cache);
        while (it.hasNext()) {
            Long2ObjectMap.Entry<CacheEntry> entry = it.next();
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()));
            stopChunkTracking.accept(chunkPos);
        }
        cachedView.cache.clear();
    }

    private static int chessboardDist(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    private record CacheEntry(long cachedAtMs, int estimatedBodySize) {
    }
}
