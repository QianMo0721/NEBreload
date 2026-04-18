package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.nio.charset.StandardCharsets;

/**
 * @author USS_Shenzhou
 */
public final class RawTrafficHelper {

    private RawTrafficHelper() {
    }

    public static int getWriteUtfCost(ResourceLocation resourceLocation) {
        return getWriteUtfCost(resourceLocation.toString());
    }

    public static int getWriteUtfCost(String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        return FriendlyByteBuf.getVarIntSize(utf8.length) + utf8.length;
    }

    public static int estimateChunkBodySize(LevelChunk chunk) {
        if (chunk == null) {
            return 0;
        }
        int total = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            total += section.getSerializedSize();
        }
        return Math.max(0, total);
    }

    public static int estimateCachedChunkRawSize(int chunkBodySize) {
        if (chunkBodySize <= 0) {
            return 0;
        }
        double multiplier = NotEnoughBandwidthLegacyConfig.get().getChunkCacheRawSizeMultiplierSafe();
        if (multiplier <= 0.0D) {
            return 0;
        }
        double estimated = chunkBodySize * multiplier;
        if (estimated >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.round(estimated);
    }
}
