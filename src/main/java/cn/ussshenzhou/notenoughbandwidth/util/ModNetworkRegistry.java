package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import net.minecraft.util.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;

public final class ModNetworkRegistry {
    private static final ConcurrentHashMap<ResourceLocation, Class<?>> ID_TO_CLASS = new ConcurrentHashMap<ResourceLocation, Class<?>>();
    private static final ConcurrentHashMap<Class<?>, ResourceLocation> CLASS_TO_ID = new ConcurrentHashMap<Class<?>, ResourceLocation>();
    private static boolean registered;

    private ModNetworkRegistry() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ID_TO_CLASS.put(PacketAggregationPacket.TYPE, PacketAggregationPacket.class);
        CLASS_TO_ID.put(PacketAggregationPacket.class, PacketAggregationPacket.TYPE);
    }

    public static ResourceLocation getPacketId(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return CLASS_TO_ID.get(clazz);
    }
}
