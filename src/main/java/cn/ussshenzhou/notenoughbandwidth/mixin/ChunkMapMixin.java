package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.chunk.CachedChunkTrackingView;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.RawTrafficHelper;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author USS_Shenzhou
 * 移植者吐槽:怎么老被重指向炸啊!!!
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Unique
    private static volatile Field neb$levelField;

    @Unique
    private static volatile Field neb$viewDistanceField;

    @Unique
    private static volatile Field neb$updatingChunkMapField;

    @Unique
    private static volatile Field neb$distanceManagerField;

    @Unique
    private static volatile Method neb$updateChunkTrackingMethod;


    @Unique
    private static int nebCacheTicketTicks() {
        return Math.max(1, cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig.get().getDccTimeoutSafeSeconds() * 20);
    }

    @Unique
    private static boolean nebCacheEnabled() {
        return cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig.get().isDelayedChunkCachingUsable();
    }

    @Unique
    private ServerLevel nebLevel() {
        try {
            Field field = neb$levelField;
            if (field == null) {
                try {
                    field = ChunkMap.class.getDeclaredField("level");
                } catch (NoSuchFieldException ignored) {
                    field = ChunkMap.class.getDeclaredField("f_140133_");
                }
                field.setAccessible(true);
                neb$levelField = field;
            }
            return (ServerLevel) field.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve ChunkMap level field", e);
        }
    }

    @Unique
    private int nebViewDistance() {
        try {
            Field field = neb$viewDistanceField;
            if (field == null) {
                try {
                    field = ChunkMap.class.getDeclaredField("viewDistance");
                } catch (NoSuchFieldException ignored) {
                    field = ChunkMap.class.getDeclaredField("f_140126_");
                }
                field.setAccessible(true);
                neb$viewDistanceField = field;
            }
            return field.getInt(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve ChunkMap viewDistance field", e);
        }
    }

    @Unique
    private void nebSetViewDistance(int viewDistance) {
        try {
            Field field = neb$viewDistanceField;
            if (field == null) {
                try {
                    field = ChunkMap.class.getDeclaredField("viewDistance");
                } catch (NoSuchFieldException ignored) {
                    field = ChunkMap.class.getDeclaredField("f_140126_");
                }
                field.setAccessible(true);
                neb$viewDistanceField = field;
            }
            field.setInt(this, viewDistance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to update ChunkMap viewDistance field", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder> nebUpdatingChunkMap() {
        try {
            Field field = neb$updatingChunkMapField;
            if (field == null) {
                try {
                    field = ChunkMap.class.getDeclaredField("updatingChunkMap");
                } catch (NoSuchFieldException ignored) {
                    field = ChunkMap.class.getDeclaredField("f_140129_");
                }
                field.setAccessible(true);
                neb$updatingChunkMapField = field;
            }
            return (it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder>) field.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve ChunkMap updatingChunkMap field", e);
        }
    }

    @Unique
    private DistanceManager nebDistanceManager() {
        try {
            Field field = neb$distanceManagerField;
            if (field == null) {
                try {
                    field = ChunkMap.class.getDeclaredField("distanceManager");
                } catch (NoSuchFieldException ignored) {
                    field = ChunkMap.class.getDeclaredField("f_140145_");
                }
                field.setAccessible(true);
                neb$distanceManagerField = field;
            }
            return (DistanceManager) field.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve ChunkMap distanceManager field", e);
        }
    }

    @Unique
    private void nebInvokeUpdateChunkTracking(
            ServerPlayer player,
            ChunkPos pos,
            MutableObject<ClientboundLevelChunkWithLightPacket> packetHolder,
            boolean wasInRange,
            boolean isInRange
    ) {
        try {
            Method method = neb$updateChunkTrackingMethod;
            if (method == null) {
                try {
                    method = ChunkMap.class.getDeclaredMethod(
                            "updateChunkTracking",
                            ServerPlayer.class,
                            ChunkPos.class,
                            MutableObject.class,
                            boolean.class,
                            boolean.class
                    );
                } catch (NoSuchMethodException ignored) {
                    method = ChunkMap.class.getDeclaredMethod(
                            "m_183754_",
                            ServerPlayer.class,
                            ChunkPos.class,
                            MutableObject.class,
                            boolean.class,
                            boolean.class
                    );
                }
                method.setAccessible(true);
                neb$updateChunkTrackingMethod = method;
            }
            method.invoke(this, player, pos, packetHolder, wasInRange, isInRange);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve ChunkMap updateChunkTracking method", e);
        }
    }

    @Unique
    private TicketType<Integer> nebCacheTicketType() {
        int ticks = nebCacheTicketTicks();
        return TicketType.create("neb_cache_" + ticks, Integer::compare, ticks);
    }

    @Unique
    private void nebRemoveCacheTicket(ChunkPos pos) {
        int ticks = nebCacheTicketTicks();
        nebDistanceManager().removeRegionTicket(nebCacheTicketType(), pos, 1, ticks);
    }

    @Unique
    private void nebUpdatePlayerTickets(int viewDistance) {
        DistanceManager distanceManager = nebDistanceManager();
        try {
            Method method;
            try {
                method = DistanceManager.class.getDeclaredMethod("updatePlayerTickets", int.class);
            } catch (NoSuchMethodException ignored) {
                method = DistanceManager.class.getDeclaredMethod("m_140777_", int.class);
            }
            method.setAccessible(true);
            method.invoke(distanceManager, viewDistance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke DistanceManager.updatePlayerTickets", e);
        }
    }

    @Unique
    private CachedChunkTrackingView.Context nebChunkTrackingContext(ServerPlayer player) {
        return new CachedChunkTrackingView.Context() {
            @Override
            public void startChunkTracking(ChunkPos pos) {
                nebCallVanillaUpdateChunkTracking(player, pos, false, true);
            }

            @Override
            public void stopChunkTracking(ChunkPos pos) {
                nebCallVanillaUpdateChunkTracking(player, pos, true, false);
            }

            @Override
            public void putTicket(ChunkPos pos, int ticks) {
                int clampedTicks = Math.max(1, ticks);
                nebDistanceManager().addRegionTicket(nebCacheTicketType(), pos, 1, clampedTicks);
            }

            @Override
            public void removeTicket(ChunkPos pos) {
                nebRemoveCacheTicket(pos);
            }

            @Override
            public void onCacheHit(ChunkPos pos, int estimatedBodySize) {
                /*
                延迟区块缓存命中时，这个区块本次并没有真正重新经过网络发送。
                之前这里把“理论上若重发该区块会产生的 raw”补记进统计，
                会把 client inbound / server outbound 的 raw 人为抬高，
                让面板看起来像聚合压缩效果很差，实际上这是 DCC 命中带来的
                本地/服务端缓存收益，不应混入真实网络传输统计。
                */
            }

            @Override
            public int estimateChunkBodySize(ChunkPos pos) {
                return nebEstimateChunkBodySize(pos);
            }
        };
    }

    @Unique
    private void nebUpdateChunkTrackingView(ServerPlayer player) {
        if (!nebCacheEnabled() || player.level() != nebLevel()) {
            return;
        }
        CachedChunkTrackingView.onUpdateChunkTracking(player, nebViewDistance(), nebChunkTrackingContext(player));
    }

    @Redirect(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;Lorg/apache/commons/lang3/mutable/MutableObject;ZZ)V"
            )
    )
    private void nebRedirectUpdateChunkTrackingInUpdatePlayerStatus(
            ChunkMap instance,
            ServerPlayer player,
            ChunkPos pos,
            MutableObject<ClientboundLevelChunkWithLightPacket> packetHolder,
            boolean wasInRange,
            boolean isInRange
    ) {
        if (!nebCacheEnabled()) {
            nebInvokeUpdateChunkTracking(player, pos, packetHolder, wasInRange, isInRange);
        }
    }

    @Inject(method = "updatePlayerStatus", at = @At("TAIL"))
    private void nebOnUpdatePlayerStatus(ServerPlayer player, boolean added, CallbackInfo ci) {
        if (!nebCacheEnabled()) {
            CachedChunkTrackingView.clear(player, nebChunkTrackingContext(player));
            return;
        }
        if (!added) {
            CachedChunkTrackingView.clear(player, nebChunkTrackingContext(player));
            return;
        }
        nebUpdateChunkTrackingView(player);
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;Lorg/apache/commons/lang3/mutable/MutableObject;ZZ)V"
            )
    )
    private void nebRedirectUpdateChunkTrackingInMove(
            ChunkMap instance,
            ServerPlayer player,
            ChunkPos pos,
            MutableObject<ClientboundLevelChunkWithLightPacket> packetHolder,
            boolean wasInRange,
            boolean isInRange
    ) {
        if (!nebCacheEnabled()) {
            nebInvokeUpdateChunkTracking(player, pos, packetHolder, wasInRange, isInRange);
        }
    }

    @Inject(method = "move", at = @At("TAIL"))
    private void nebOnMove(ServerPlayer player, CallbackInfo ci) {
        if (nebCacheEnabled()) {
            nebUpdateChunkTrackingView(player);
        }
    }

    @Inject(method = "setViewDistance", at = @At("HEAD"), cancellable = true)
    private void nebSetViewDistance(int viewDistance, CallbackInfo ci) {
        int clamped = Mth.clamp(viewDistance, 2, 32);
        if (clamped == nebViewDistance()) {
            ci.cancel();
            return;
        }

        int oldViewDistance = nebViewDistance();
        nebSetViewDistance(clamped);
        nebUpdatePlayerTickets(nebViewDistance());

        if (nebCacheEnabled()) {
            for (ServerPlayer player : nebLevel().players()) {
                nebUpdateChunkTrackingView(player);
            }
            ci.cancel();
            return;
        }

        for (ChunkHolder chunkHolder : nebUpdatingChunkMap().values()) {
            ChunkPos chunkPos = chunkHolder.getPos();
            MutableObject<ClientboundLevelChunkWithLightPacket> packetHolder = new MutableObject<>();

            for (ServerPlayer player : nebLevel().players()) {
                SectionPos lastSectionPos = player.getLastSectionPos();
                boolean wasInRange = ChunkMap.isChunkInRange(
                        chunkPos.x,
                        chunkPos.z,
                        lastSectionPos.x(),
                        lastSectionPos.z(),
                        oldViewDistance
                );
                boolean isInRange = ChunkMap.isChunkInRange(
                        chunkPos.x,
                        chunkPos.z,
                        lastSectionPos.x(),
                        lastSectionPos.z(),
                        nebViewDistance()
                );
                if (wasInRange || isInRange) {
                    nebInvokeUpdateChunkTracking(player, chunkPos, packetHolder, wasInRange, isInRange);
                }
            }
        }
        ci.cancel();
    }

    @Unique
    private void nebCallVanillaUpdateChunkTracking(
            ServerPlayer player,
            ChunkPos pos,
            boolean wasInRange,
            boolean isInRange
    ) {
        nebInvokeUpdateChunkTracking(player, pos, new MutableObject<>(), wasInRange, isInRange);
    }

    @Unique
    private int nebEstimateChunkBodySize(ChunkPos pos) {
        ChunkHolder chunkHolder = nebUpdatingChunkMap().get(pos.toLong());
        if (chunkHolder == null) {
            return 0;
        }
        var chunk = chunkHolder.getTickingChunk();
        if (chunk == null) {
            chunk = chunkHolder.getFullChunk();
        }
        return RawTrafficHelper.estimateChunkBodySize(chunk);
    }
}
