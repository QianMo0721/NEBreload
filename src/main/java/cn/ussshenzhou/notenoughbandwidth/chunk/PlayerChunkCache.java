package cn.ussshenzhou.notenoughbandwidth.chunk;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthConfig;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.util.math.ChunkPos;

import java.util.concurrent.TimeUnit;

public final class PlayerChunkCache {
    private static final long NO_CACHE = Long.MIN_VALUE;

    private final Long2LongLinkedOpenHashMap retainedChunks = new Long2LongLinkedOpenHashMap();
    private final LongArrayList pendingUnloads = new LongArrayList();
    private ChunkPos center = new ChunkPos(0, 0);
    private int viewDistance;
    private boolean centerChangedSinceLastTrim;

    public PlayerChunkCache() {
        retainedChunks.defaultReturnValue(NO_CACHE);
    }

    public void updateCenter(ChunkPos center, int viewDistance) {
        boolean changed = !this.center.equals(center) || this.viewDistance != viewDistance;
        this.center = center;
        this.viewDistance = viewDistance;
        this.centerChangedSinceLastTrim |= changed;
    }

    public boolean retain(ChunkPos pos, long nowMillis) {
        if (!withinRetentionDistance(pos)) {
            return false;
        }
        removePendingUnload(pos.toLong());
        retainedChunks.putAndMoveToLast(pos.toLong(), nowMillis);
        trim(nowMillis);
        return retainedChunks.containsKey(pos.toLong());
    }

    public boolean revive(long chunkPos) {
        removePendingUnload(chunkPos);
        return retainedChunks.remove(chunkPos) != NO_CACHE;
    }

    public void remove(long chunkPos) {
        retainedChunks.remove(chunkPos);
        removePendingUnload(chunkPos);
    }

    public void trim(long nowMillis) {
        var cfg = ConfigHelper.getConfigRead(NotEnoughBandwidthConfig.class);
        int chunkCacheBufferSize = Math.max(1, cfg.dccSizeLimit);
        long chunkCacheTimeoutMilli = TimeUnit.SECONDS.toMillis(Math.max(0, cfg.dccTimeout));

        if (centerChangedSinceLastTrim) {
            enumerate((pos, time) -> {
                ChunkPos chunkPos = new ChunkPos(pos);
                if (!withinRetentionDistance(chunkPos)) {
                    markPendingUnload(pos);
                    return CacheConsumer.REMOVE;
                }
                return CacheConsumer.CONTINUE;
            });
            centerChangedSinceLastTrim = false;
        }

        enumerate((pos, time) -> {
            boolean legacy = time <= nowMillis - chunkCacheTimeoutMilli;
            if (legacy || retainedChunks.size() > chunkCacheBufferSize) {
                markPendingUnload(pos);
                return CacheConsumer.REMOVE;
            }
            return CacheConsumer.STOP;
        });
    }

    public LongArrayList drainPendingUnloads() {
        LongArrayList result = new LongArrayList(pendingUnloads);
        pendingUnloads.clear();
        return result;
    }

    public boolean contains(long chunkPos) {
        return retainedChunks.containsKey(chunkPos);
    }

    public boolean hasPendingUnload(long chunkPos) {
        for (int i = 0; i < pendingUnloads.size(); i++) {
            if (pendingUnloads.getLong(i) == chunkPos) {
                return true;
            }
        }
        return false;
    }

    private boolean withinRetentionDistance(ChunkPos pos) {
        int retentionDistance = Math.max(0, ConfigHelper.getConfigRead(NotEnoughBandwidthConfig.class).dccDistance);
        return chessboardDistance(center, pos) <= retentionDistance;
    }

    private void markPendingUnload(long chunkPos) {
        if (retainedChunks.containsKey(chunkPos)) {
            removePendingUnload(chunkPos);
            pendingUnloads.add(chunkPos);
        }
    }

    private void removePendingUnload(long chunkPos) {
        for (int i = 0; i < pendingUnloads.size(); i++) {
            if (pendingUnloads.getLong(i) == chunkPos) {
                pendingUnloads.removeLong(i);
                return;
            }
        }
    }

    @FunctionalInterface
    private interface CacheConsumer {
        byte CONTINUE = 0;
        byte REMOVE = 1;
        byte STOP = 2;

        byte accept(long pos, long time);
    }

    private void enumerate(CacheConsumer consumer) {
        ObjectIterator<Long2LongMap.Entry> iterator = Long2LongMaps.fastIterator(retainedChunks);
        while (iterator.hasNext()) {
            Long2LongMap.Entry entry = iterator.next();
            byte action = consumer.accept(entry.getLongKey(), entry.getLongValue());
            if ((action & CacheConsumer.REMOVE) != 0) {
                iterator.remove();
            }
            if ((action & CacheConsumer.STOP) != 0) {
                return;
            }
        }
    }

    private static int chessboardDistance(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }
}
