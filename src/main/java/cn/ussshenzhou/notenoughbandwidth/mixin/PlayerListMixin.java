package cn.ussshenzhou.notenoughbandwidth.mixin;

import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Fabric 侧不再扩大原版同步给客户端的视距。
 *
 * 原先直接修改 {@link PlayerManager#setViewDistance(int)} 的入参会导致服务端
 * 发送超出客户端 chunk cache radius 的区块，从而触发大量 “Ignoring chunk since it's not in the view range”
 * 警告。当前的保留/恢复逻辑已经由 [`ChunkMapMixin`](src/main/java/cn/ussshenzhou/notenoughbandwidth/mixin/ChunkMapMixin.java:26)
 * 与 [`PlayerChunkCache`](src/main/java/cn/ussshenzhou/notenoughbandwidth/chunk/PlayerChunkCache.java:14)
 * 独立实现，因此这里保留空 Mixin 以维持配置兼容，但不再改写视距参数。
 */
@Mixin(PlayerManager.class)
public class PlayerListMixin {
}
