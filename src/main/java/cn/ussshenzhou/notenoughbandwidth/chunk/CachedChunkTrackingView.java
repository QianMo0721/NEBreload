package cn.ussshenzhou.notenoughbandwidth.chunk;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A chunk tracking cache for Forge 1.20.1.
 * In 1.20.1, ChunkTrackingView does not exist; this class provides
 * equivalent DCC (Delayed Chunk Caching) functionality by maintaining
 * a per-player cache of chunk positions that have recently left the
 * active view distance.
 *
 * @author USS_Shenzhou (adapted for Forge 1.20.1)
 */
public class CachedChunkTrackingView {
    private static final long NO_CACHE = -1;
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The center chunk position of the player's current view.
     */
    private ChunkPos center;
    private int viewDistance;

    /**
     * Cache of chunks that have recently left the major view.
     * Key: packed ChunkPos, Value: timestamp (ms) when last in view.
     */
    private final Long2LongLinkedOpenHashMap cache = new Long2LongLinkedOpenHashMap();

    private static final WeakHashMap<ServerPlayer, CachedChunkTrackingView> PLAYER_CACHE_VIEWS = new WeakHashMap<>();

    private CachedChunkTrackingView(ChunkPos center, int viewDistance) {
        this.center = center;
        this.viewDistance = viewDistance;
    }

    public interface Context {
        void startChunkTracking(ChunkPos pos);
        void stopChunkTracking(ChunkPos pos);
        void putTicket(ChunkPos pos, int ticks);
    }

    /**
     * Called from the mixin on each chunk tracking update.
     */
    public static void onUpdateChunkTracking(ServerPlayer player, int playerViewDistance, Context context) {
        ChunkPos playerChunkPos = player.chunkPosition();

        CachedChunkTrackingView cachedView = PLAYER_CACHE_VIEWS.get(player);
        if (cachedView == null) {
            cachedView = new CachedChunkTrackingView(playerChunkPos, playerViewDistance);
            PLAYER_CACHE_VIEWS.put(player, cachedView);
            // First time: start tracking all chunks in view
            forEachInView(playerChunkPos, playerViewDistance, context::startChunkTracking);
            return;
        }

        ChunkPos oldCenter = cachedView.center;
        int oldViewDistance = cachedView.viewDistance;

        // Update center/distance
        cachedView.center = playerChunkPos;
        cachedView.viewDistance = playerViewDistance;

        cachedView.tick(player, oldCenter, oldViewDistance, playerChunkPos, playerViewDistance, context);
    }

    private void tick(ServerPlayer player, ChunkPos oldCenter, int oldVD, ChunkPos newCenter, int newVD, Context context) {
        long now = System.currentTimeMillis();
        var cfg = NotEnoughBandwidthLegacyConfig.get();
        int chunkCacheTimeout = cfg.dccTimeout;
        int chunkCacheDistance = newVD + cfg.dccDistance;
        int chunkCacheSizeLimit = cfg.dccSizeLimit;
        long timeoutMs = TimeUnit.SECONDS.toMillis(chunkCacheTimeout);

        // Find newly added chunks (in new view but not in old view)
        forEachInView(newCenter, newVD, chunkPos -> {
            if (!isInView(oldCenter, oldVD, chunkPos)) {
                long packed = chunkPos.toLong();
                if (cache.containsKey(packed)) {
                    // Was in cache, refresh
                    context.putTicket(chunkPos, chunkCacheTimeout * 20);
                    cache.put(packed, now);
                    LOGGER.trace("Cache hit at {} in {}'s chunk cache.", chunkPos, player.getGameProfile().getName());
                } else {
                    // Truly new chunk
                    context.startChunkTracking(chunkPos);
                }
            }
        });

        // Find chunks that left the view - add them to cache
        forEachInView(oldCenter, oldVD, chunkPos -> {
            if (!isInView(newCenter, newVD, chunkPos)) {
                long packed = chunkPos.toLong();
                if (!cache.containsKey(packed)) {
                    cache.put(packed, now);
                    context.putTicket(chunkPos, chunkCacheTimeout * 20);
                    LOGGER.trace("Caching {} in {}'s chunk cache.", chunkPos, player.getGameProfile().getName());
                }
            }
        });

        // Evict entries that are too far or too old
        ObjectIterator<Long2LongMap.Entry> it = Long2LongMaps.fastIterator(cache);
        while (it.hasNext()) {
            Long2LongMap.Entry entry = it.next();
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()));

            if (isInView(newCenter, newVD, chunkPos)) {
                // Back in main view; remove from cache (don't stop tracking)
                it.remove();
                continue;
            }

            if (chessboardDist(newCenter, chunkPos) > chunkCacheDistance) {
                it.remove();
                context.stopChunkTracking(chunkPos);
                LOGGER.trace("Remove {} from {}'s chunk cache: too far away.", chunkPos, player.getGameProfile().getName());
                continue;
            }

            if (now - entry.getLongValue() > timeoutMs) {
                it.remove();
                context.stopChunkTracking(chunkPos);
                LOGGER.trace("Remove {} from {}'s chunk cache: timeout", chunkPos, player.getGameProfile().getName());
            }
        }

        // Evict oldest if over size limit
        while (cache.size() > chunkCacheSizeLimit) {
            long pos = cache.firstLongKey();
            cache.remove(pos);
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(pos), ChunkPos.getZ(pos));
            context.stopChunkTracking(chunkPos);
            LOGGER.trace("Remove {} from {}'s chunk cache: buffer is full", chunkPos, player.getGameProfile().getName());
        }
    }

    private static void forEachInView(ChunkPos center, int viewDistance, Consumer<ChunkPos> action) {
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                action.accept(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
    }

    private static boolean isInView(ChunkPos center, int viewDistance, ChunkPos pos) {
        return Math.abs(pos.x - center.x) <= viewDistance && Math.abs(pos.z - center.z) <= viewDistance;
    }

    private static int chessboardDist(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }
}
