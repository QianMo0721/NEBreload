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

import java.util.concurrent.TimeUnit;

/**
 * Forge 1.20.1 backport of NeoForge-style chunk tracking view + cache state.
 * 终于啊终于, neoforge的API真好用, 我要用一辈子neoforge!!!
 */
public class CachedChunkTrackingView implements ChunkTrackingViewCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChunkTrackingViewCompat.Positioned major;
    private final Long2ObjectLinkedOpenHashMap<CacheEntry> cache = new Long2ObjectLinkedOpenHashMap<>();

    public CachedChunkTrackingView(ChunkTrackingViewCompat.Positioned major) {
        this.major = major;
        this.cache.defaultReturnValue(null);
    }

    @Override
    public boolean contains(int x, int z, boolean includeNeighbors) {
        return major.contains(x, z, includeNeighbors) || cache.containsKey(ChunkPos.asLong(x, z));
    }

    @Override
    public void forEach(java.util.function.Consumer<ChunkPos> consumer) {
        major.forEach(consumer);
        var iterator = cache.keySet().iterator();
        while (iterator.hasNext()) {
            consumer.accept(new ChunkPos(iterator.nextLong()));
        }
    }

    public interface Context {
        void startChunkTracking(ChunkPos pos);

        void stopChunkTracking(ChunkPos pos);

        void putTicket(ChunkPos pos, int ticks);

        void removeTicket(ChunkPos pos);

        void onCacheHit(ChunkPos pos, int estimatedBodySize);

        int estimateChunkBodySize(ChunkPos pos);
    }

    public static void onUpdateChunkTracking(ServerPlayer player, int playerViewDistance, Context context) {
        ChunkTrackingViewHolder holder = (ChunkTrackingViewHolder) player;
        CachedChunkTrackingView currentTrackingView = holder.neb$getChunkTrackingView();
        ChunkTrackingViewCompat.Positioned next = new ChunkTrackingViewCompat.Positioned(player.chunkPosition(), playerViewDistance);

        if (currentTrackingView != null) {
            currentTrackingView.tick(player, next, context);
            return;
        }

        CachedChunkTrackingView cachedView = new CachedChunkTrackingView(next);
        ChunkTrackingViewCompat.difference(ChunkTrackingViewCompat.EMPTY, cachedView, context::startChunkTracking, pos -> {
        });
        holder.neb$setChunkTrackingView(cachedView);
    }

    public static void clear(ServerPlayer player, Context context) {
        ChunkTrackingViewHolder holder = (ChunkTrackingViewHolder) player;
        CachedChunkTrackingView cachedView = holder.neb$getChunkTrackingView();
        holder.neb$setChunkTrackingView(null);
        if (cachedView == null) {
            return;
        }

        java.util.HashSet<Long> cleared = new java.util.HashSet<>();
        cachedView.forEach(chunkPos -> {
            long packed = chunkPos.toLong();
            if (cleared.add(packed)) {
                context.removeTicket(chunkPos);
                context.stopChunkTracking(chunkPos);
            }
        });
        cachedView.cache.clear();
    }

    private void tick(ServerPlayer player, ChunkTrackingViewCompat.Positioned next, Context context) {
        var cfg = NotEnoughBandwidthLegacyConfig.get();
        long now = System.currentTimeMillis();
        int chunkCacheBufferSize = cfg.getDccSizeLimitSafe();
        int chunkCacheDistance = cfg.getDccDistanceSafe();
        int chunkCacheTimeout = cfg.getDccTimeoutSafeSeconds();
        long chunkCacheTimeoutMilli = TimeUnit.SECONDS.toMillis(chunkCacheTimeout);
        boolean debug = cfg.debugLog;

        if (!major.equals(next)) {
            ChunkTrackingViewCompat.difference(major, next, chunkPos -> {
                CacheEntry hit = cache.remove(chunkPos.toLong());
                if (hit == null) {
                    context.startChunkTracking(chunkPos);
                    if (debug) {
                        LOGGER.debug("Cache miss at {} in {}'s chunk cache.", chunkPos, player.getGameProfile().getName());
                    }
                } else {
                    context.removeTicket(chunkPos);
                    // DCC 实际启用时，缓存命中的设计目标就是让客户端继续沿用已持有的 chunk，
                    // 因此遵循“不重发 chunk 主体”的原则，只补统计。
                    // 若 DCC 已关闭或当前配置已使其不可用，则不能再假定客户端一定还保留该 chunk；
                    // 此时即使命中缓存记录，也补走一次原版 startChunkTracking，保证客户端可自愈。
                    if (cfg.isDelayedChunkCachingUsable()) {
                        context.onCacheHit(chunkPos, hit.estimatedBodySize());
                    } else {
                        context.startChunkTracking(chunkPos);
                        context.onCacheHit(chunkPos, hit.estimatedBodySize());
                    }
                    if (debug) {
                        LOGGER.debug("Cache hit at {} in {}'s chunk cache.", chunkPos, player.getGameProfile().getName());
                    }
                }
            }, chunkPos -> {
                if (next.center().getChessboardDistance(chunkPos) <= chunkCacheDistance) {
                    context.putTicket(chunkPos, chunkCacheTimeout * 20);
                    cache.putAndMoveToLast(chunkPos.toLong(), new CacheEntry(now, Math.max(0, context.estimateChunkBodySize(chunkPos))));
                    if (debug) {
                        LOGGER.debug("Caching {} in {}'s chunk cache.", chunkPos, player.getGameProfile().getName());
                    }
                } else {
                    context.stopChunkTracking(chunkPos);
                }
            });

            enumerate((pos, entry) -> {
                ChunkPos chunkPos = new ChunkPos(pos);
                if (next.center().getChessboardDistance(chunkPos) > chunkCacheDistance) {
                    context.removeTicket(chunkPos);
                    context.stopChunkTracking(chunkPos);
                    if (debug) {
                        LOGGER.debug("Remove {} from {}'s chunk cache: too far away.", chunkPos, player.getGameProfile().getName());
                    }
                    return CacheConsumer.REMOVE;
                }
                return CacheConsumer.CONTINUE;
            });
        }

        enumerate((pos, entry) -> {
            boolean legacy = entry.cachedAtMs() <= now - chunkCacheTimeoutMilli;
            if (legacy || cache.size() > chunkCacheBufferSize) {
                ChunkPos chunkPos = new ChunkPos(pos);
                context.removeTicket(chunkPos);
                context.stopChunkTracking(chunkPos);
                if (debug) {
                    LOGGER.debug("Remove {} from {}'s chunk cache: {}", chunkPos, player.getGameProfile().getName(), legacy ? "timeout" : "buffer is full");
                }
                return CacheConsumer.REMOVE;
            }
            return CacheConsumer.STOP;
        });

        major = next;
    }

    @FunctionalInterface
    private interface CacheConsumer {
        byte CONTINUE = 0, REMOVE = 1, STOP = 2;

        byte accept(long pos, CacheEntry entry);
    }

    private void enumerate(CacheConsumer consumer) {
        ObjectIterator<Long2ObjectMap.Entry<CacheEntry>> iterator = Long2ObjectMaps.fastIterator(cache);
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<CacheEntry> entry = iterator.next();
            byte result = consumer.accept(entry.getLongKey(), entry.getValue());
            if ((result & CacheConsumer.REMOVE) != 0) {
                iterator.remove();
            }
            if ((result & CacheConsumer.STOP) != 0) {
                return;
            }
        }
    }

    private record CacheEntry(long cachedAtMs, int estimatedBodySize) {
    }
}
