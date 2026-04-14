package cn.ussshenzhou.notenoughbandwidth.chunk;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CachedChunkTrackingView {
    private static final long NO_CACHE = -1L;
    private static final Logger LOGGER = LogManager.getLogger();

    private static final WeakHashMap<EntityPlayerMP, CachedChunkTrackingView> PLAYER_CACHE_VIEWS = new WeakHashMap<EntityPlayerMP, CachedChunkTrackingView>();

    private final Long2LongLinkedOpenHashMap cache = new Long2LongLinkedOpenHashMap();
    private final Long2IntOpenHashMap cachedRawSizes = new Long2IntOpenHashMap();

    private CachedChunkTrackingView() {
        cache.defaultReturnValue(NO_CACHE);
    }

    private static long pack(ChunkPos pos) {
        return ChunkPos.asLong(pos.x, pos.z);
    }

    private static ChunkPos unpack(long packed) {
        return new ChunkPos((int) (packed >> 32), (int) packed);
    }

    private static CachedChunkTrackingView get(EntityPlayerMP player) {
        return PLAYER_CACHE_VIEWS.computeIfAbsent(player, ignored -> new CachedChunkTrackingView());
    }

    public static boolean onChunkEnter(EntityPlayerMP player, ChunkPos pos) {
        CachedChunkTrackingView cachedView = PLAYER_CACHE_VIEWS.get(player);
        if (cachedView == null) {
            return false;
        }

        long packed = pack(pos);
        boolean hit = cachedView.cache.remove(packed) != NO_CACHE;
        if (!hit) {
            return false;
        }

        int cachedRawSize = cachedView.cachedRawSizes.remove(packed);
        if (cachedRawSize > 0) {
            SimpleStatManager.outRaw(cachedRawSize);
        }

        LOGGER.trace("Cache hit at {} in {}'s chunk cache, restored {} raw bytes.", pos, player.getName(), Integer.valueOf(cachedRawSize));
        return true;
    }

    public static boolean onChunkLeave(EntityPlayerMP player, ChunkPos pos, ChunkPos currentCenter, int rawSize) {
        NotEnoughBandwidthLegacyConfig cfg = NotEnoughBandwidthLegacyConfig.get();
        int chunkCacheDistance = cfg.getDccDistanceSafe();
        if (!cfg.isDelayedChunkCachingUsable()) {
            return false;
        }
        if (chessboardDist(currentCenter, pos) > chunkCacheDistance) {
            return false;
        }

        CachedChunkTrackingView cachedView = get(player);
        long packed = pack(pos);
        cachedView.cache.putAndMoveToLast(packed, System.currentTimeMillis());
        if (rawSize > 0) {
            cachedView.cachedRawSizes.put(packed, rawSize);
        } else {
            cachedView.cachedRawSizes.remove(packed);
        }
        LOGGER.trace("Caching {} in {}'s chunk cache with {} raw bytes.", pos, player.getName(), Integer.valueOf(rawSize));
        return true;
    }

    public static void tick(EntityPlayerMP player, ChunkPos currentCenter, Consumer<ChunkPos> stopChunkTracking) {
        CachedChunkTrackingView cachedView = PLAYER_CACHE_VIEWS.get(player);
        if (cachedView == null) {
            return;
        }

        NotEnoughBandwidthLegacyConfig cfg = NotEnoughBandwidthLegacyConfig.get();
        long now = System.currentTimeMillis();
        int chunkCacheDistance = cfg.getDccDistanceSafe();
        int chunkCacheBufferSize = cfg.getDccSizeLimitSafe();
        long timeoutMs = TimeUnit.SECONDS.toMillis(cfg.getDccTimeoutSafeSeconds());

        ObjectIterator<Long2LongMap.Entry> it = cachedView.cache.long2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2LongMap.Entry entry = it.next();
            ChunkPos chunkPos = unpack(entry.getLongKey());

            if (chessboardDist(currentCenter, chunkPos) > chunkCacheDistance) {
                it.remove();
                cachedView.cachedRawSizes.remove(entry.getLongKey());
                stopChunkTracking.accept(chunkPos);
                LOGGER.trace("Remove {} from {}'s chunk cache: too far away.", chunkPos, player.getName());
                continue;
            }

            if (now - entry.getLongValue() > timeoutMs) {
                it.remove();
                cachedView.cachedRawSizes.remove(entry.getLongKey());
                stopChunkTracking.accept(chunkPos);
                LOGGER.trace("Remove {} from {}'s chunk cache: timeout.", chunkPos, player.getName());
            }
        }

        while (cachedView.cache.size() > chunkCacheBufferSize) {
            long pos = cachedView.cache.firstLongKey();
            cachedView.cache.remove(pos);
            cachedView.cachedRawSizes.remove(pos);
            ChunkPos chunkPos = unpack(pos);
            stopChunkTracking.accept(chunkPos);
            LOGGER.trace("Remove {} from {}'s chunk cache: buffer is full.", chunkPos, player.getName());
        }
    }

    public static void clear(EntityPlayerMP player, Consumer<ChunkPos> stopChunkTracking) {
        CachedChunkTrackingView cachedView = PLAYER_CACHE_VIEWS.remove(player);
        if (cachedView == null) {
            return;
        }

        ObjectIterator<Long2LongMap.Entry> it = cachedView.cache.long2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2LongMap.Entry entry = it.next();
            ChunkPos chunkPos = unpack(entry.getLongKey());
            stopChunkTracking.accept(chunkPos);
        }
        cachedView.cache.clear();
        cachedView.cachedRawSizes.clear();
    }

    private static int chessboardDist(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }
}
