package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.chunk.PlayerChunkCache;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ChunkMapMixin {
    @Shadow
    int watchDistance;

    @Unique
    private static final int NEBL$RETENTION_TICKET_LEVEL = 33;
    @Unique
    private static final Map<ServerPlayerEntity, PlayerChunkCache> NEBL$PLAYER_CHUNK_CACHES = new WeakHashMap<>();
    @Unique
    private static final Map<Long, Set<UUID>> NEBL$RETAINED_CHUNK_REFS = new HashMap<>();
    @Unique
    private static final Map<UUID, Set<Long>> NEBL$PLAYER_RETAINED_INDEX = new HashMap<>();

    @Inject(method = "handlePlayerAddedOrRemoved", at = @At("HEAD"))
    private void nebl$initPlayerCache(ServerPlayerEntity player, boolean added, CallbackInfo ci) {
        if (added) {
            PlayerChunkCache cache = nebl$getOrCreateCache(player);
            cache.updateCenter(player.getChunkPos(), this.watchDistance);
            cache.trim(System.currentTimeMillis());
            nebl$flushPendingUnloads(player, cache, false);
        } else {
            PlayerChunkCache cache = NEBL$PLAYER_CHUNK_CACHES.remove(player);
            if (cache != null) {
                nebl$flushPendingUnloads(player, cache, true);
            }
            nebl$clearPlayerRetentions(player);
        }
    }

    @Inject(method = "setViewDistance", at = @At("TAIL"))
    private void nebl$syncAllCacheViewDistances(int watchDistance, CallbackInfo ci) {
        long now = System.currentTimeMillis();
        for (Map.Entry<ServerPlayerEntity, PlayerChunkCache> entry : NEBL$PLAYER_CHUNK_CACHES.entrySet()) {
            ServerPlayerEntity player = entry.getKey();
            if (player == null) {
                continue;
            }
            PlayerChunkCache cache = entry.getValue();
            cache.updateCenter(player.getChunkPos(), this.watchDistance);
            cache.trim(now);
            nebl$flushPendingUnloads(player, cache, false);
        }
    }

    @Inject(method = "updatePosition", at = @At("HEAD"))
    private void nebl$updatePlayerCacheCenter(ServerPlayerEntity player, CallbackInfo ci) {
        PlayerChunkCache cache = nebl$getOrCreateCache(player);
        cache.updateCenter(player.getChunkPos(), this.watchDistance);
        cache.trim(System.currentTimeMillis());
        nebl$flushPendingUnloads(player, cache, false);
    }

    @Inject(method = "sendWatchPackets", at = @At("HEAD"), cancellable = true)
    private void nebl$retainChunks(ServerPlayerEntity player, ChunkPos pos, MutableObject<ChunkDataS2CPacket> mutablePacket, boolean oldWithinViewDistance, boolean newWithinViewDistance, CallbackInfo ci) {
        PlayerChunkCache cache = nebl$getOrCreateCache(player);
        cache.updateCenter(player.getChunkPos(), this.watchDistance);
        long now = System.currentTimeMillis();
        cache.trim(now);
        nebl$flushPendingUnloads(player, cache, false);

        long chunkPos = pos.toLong();
        if (oldWithinViewDistance && !newWithinViewDistance) {
            if (cache.retain(pos, now)) {
                nebl$acquireRetention(player, pos);
                ci.cancel();
                return;
            }
            nebl$releaseRetention(player, chunkPos);
            cache.remove(chunkPos);
            return;
        }

        if (!oldWithinViewDistance && newWithinViewDistance) {
            if (cache.revive(chunkPos)) {
                nebl$releaseRetention(player, chunkPos);
            }
            return;
        }

        if (!oldWithinViewDistance && !newWithinViewDistance && (cache.contains(chunkPos) || cache.hasPendingUnload(chunkPos))) {
            ci.cancel();
        }
    }

    @Unique
    private void nebl$flushPendingUnloads(ServerPlayerEntity player, PlayerChunkCache cache, boolean force) {
        LongArrayList pending = cache.drainPendingUnloads();
        for (long chunkPos : pending) {
            if (!force && cache.contains(chunkPos)) {
                continue;
            }
            player.sendUnloadChunkPacket(new ChunkPos(chunkPos));
            nebl$releaseRetention(player, chunkPos);
            cache.remove(chunkPos);
        }
    }

    @Unique
    private static void nebl$acquireRetention(ServerPlayerEntity player, ChunkPos pos) {
        long chunkPos = pos.toLong();
        UUID playerId = player.getUuid();
        Set<UUID> players = NEBL$RETAINED_CHUNK_REFS.computeIfAbsent(chunkPos, ignored -> new HashSet<>());
        if (players.add(playerId)) {
            NEBL$PLAYER_RETAINED_INDEX.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(chunkPos);
            if (players.size() == 1) {
                nebl$getTicketManager(player).addTicketWithLevel(ChunkTicketType.UNKNOWN, pos, NEBL$RETENTION_TICKET_LEVEL, pos);
            }
        }
    }

    @Unique
    private static void nebl$releaseRetention(ServerPlayerEntity player, long chunkPos) {
        UUID playerId = player.getUuid();
        Set<UUID> players = NEBL$RETAINED_CHUNK_REFS.get(chunkPos);
        if (players != null && players.remove(playerId) && players.isEmpty()) {
            NEBL$RETAINED_CHUNK_REFS.remove(chunkPos);
            ChunkPos pos = new ChunkPos(chunkPos);
            nebl$getTicketManager(player).removeTicketWithLevel(ChunkTicketType.UNKNOWN, pos, NEBL$RETENTION_TICKET_LEVEL, pos);
        }
        Set<Long> retained = NEBL$PLAYER_RETAINED_INDEX.get(playerId);
        if (retained != null) {
            retained.remove(chunkPos);
            if (retained.isEmpty()) {
                NEBL$PLAYER_RETAINED_INDEX.remove(playerId);
            }
        }
    }

    @Unique
    private static void nebl$clearPlayerRetentions(ServerPlayerEntity player) {
        Set<Long> retained = NEBL$PLAYER_RETAINED_INDEX.remove(player.getUuid());
        if (retained == null) {
            return;
        }
        for (long chunkPos : retained) {
            Set<UUID> players = NEBL$RETAINED_CHUNK_REFS.get(chunkPos);
            if (players != null) {
                players.remove(player.getUuid());
                if (players.isEmpty()) {
                    NEBL$RETAINED_CHUNK_REFS.remove(chunkPos);
                    ChunkPos pos = new ChunkPos(chunkPos);
                    nebl$getTicketManager(player).removeTicketWithLevel(ChunkTicketType.UNKNOWN, pos, NEBL$RETENTION_TICKET_LEVEL, pos);
                }
            }
        }
    }

    @Unique
    private static ChunkTicketManager nebl$getTicketManager(ServerPlayerEntity player) {
        return player.getServerWorld().getChunkManager().threadedAnvilChunkStorage.getTicketManager();
    }

    @Unique
    private static PlayerChunkCache nebl$getOrCreateCache(ServerPlayerEntity player) {
        return NEBL$PLAYER_CHUNK_CACHES.computeIfAbsent(player, ignored -> new PlayerChunkCache());
    }
}
