package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.resources.ResourceLocation;

public record NetworkChannel(ResourceLocation id, String version) {
}
