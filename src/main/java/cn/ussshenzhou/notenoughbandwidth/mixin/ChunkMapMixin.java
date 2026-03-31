package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.chunk.CachedChunkTrackingView;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Shadow
    int viewDistance;

    private static final Map<Integer, TicketType<Integer>> NEB_CACHE_TICKETS = new ConcurrentHashMap<>();

    private static TicketType<Integer> getCacheTicketType(int ticks) {
        return NEB_CACHE_TICKETS.computeIfAbsent(ticks,
                t -> TicketType.create("neb_cache_" + t, Integer::compare, t));
    }

    private void nebUpdateChunkTracking(ServerPlayer player) {
        if (player.level() != this.level) {
            return;
        }

        MutableObject<ClientboundLevelChunkWithLightPacket> packetHolder = new MutableObject<>();
        CachedChunkTrackingView.onUpdateChunkTracking(player, this.viewDistance, new CachedChunkTrackingView.Context() {
            @Override
            public void startChunkTracking(ChunkPos pos) {
                updateChunkTracking(player, pos, packetHolder, false, true);
            }

            @Override
            public void stopChunkTracking(ChunkPos pos) {
                updateChunkTracking(player, pos, packetHolder, true, false);
            }

            @Override
            public void putTicket(ChunkPos pos, int ticks) {
                TicketType<Integer> type = getCacheTicketType(ticks);
                getDistanceManager().addRegionTicket(type, pos, 1, ticks);
            }
        });
    }

    @Inject(method = "updatePlayerStatus", at = @At("HEAD"))
    private void nebOnUpdatePlayerStatus(ServerPlayer player, boolean added, CallbackInfo ci) {
        nebUpdateChunkTracking(player);
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void nebOnMove(ServerPlayer player, CallbackInfo ci) {
        nebUpdateChunkTracking(player);
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
    }

    @Shadow
    protected abstract void updateChunkTracking(ServerPlayer player, ChunkPos pos, MutableObject<ClientboundLevelChunkWithLightPacket> packetHolder, boolean wasInRange, boolean isInRange);
}
