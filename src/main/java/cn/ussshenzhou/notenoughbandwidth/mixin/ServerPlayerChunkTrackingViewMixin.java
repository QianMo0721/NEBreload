package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.chunk.CachedChunkTrackingView;
import cn.ussshenzhou.notenoughbandwidth.chunk.ChunkTrackingViewHolder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

@Mixin(ServerPlayer.class)
public class ServerPlayerChunkTrackingViewMixin implements ChunkTrackingViewHolder {
    @Unique
    @Nullable
    private CachedChunkTrackingView neb$chunkTrackingView;

    @Override
    @Nullable
    public CachedChunkTrackingView neb$getChunkTrackingView() {
        return neb$chunkTrackingView;
    }

    @Override
    public void neb$setChunkTrackingView(@Nullable CachedChunkTrackingView view) {
        this.neb$chunkTrackingView = view;
    }
}
