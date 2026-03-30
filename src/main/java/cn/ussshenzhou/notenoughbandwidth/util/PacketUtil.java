package cn.ussshenzhou.notenoughbandwidth.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author USS_Shenzhou
 */
public class PacketUtil {

    // Cache: packet class -> ResourceLocation id
    private static final ConcurrentHashMap<Class<?>, ResourceLocation> TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Get the true ResourceLocation type of a Packet.
     * Order:
     * 1) Use Packet#type().id() if available (most reliable on 1.20.1).
     * 2) For NEB-registered packets use the registration map.
     * 3) Fallback to class-name-derived minecraft:snake_case.
     */
    public static ResourceLocation getTrueType(Packet<?> packet) {
        return TYPE_CACHE.computeIfAbsent(packet.getClass(), cls -> {
            // 1) Try Packet#type().id() (vanilla PacketType path is authoritative)
            try {
                var packetType = packet.type();
                if (packetType != null) {
                    var idMethod = packetType.getClass().getMethod("id");
                    Object id = idMethod.invoke(packetType);
                    if (id instanceof ResourceLocation rl) {
                        return rl;
                    }
                }
            } catch (Throwable ignored) {
            }

            // 2) Try to get from Forge network registry
            ResourceLocation fromRegistry = ModNetworkRegistry.getPacketId(cls);
            if (fromRegistry != null) {
                return fromRegistry;
            }

            // 3) Fallback: use simple class name as path under "minecraft" namespace
            String simpleName = cls.getSimpleName();
            String path = toSnakeCase(simpleName);
            return new ResourceLocation("minecraft", path);
        });
    }

    /**
     * Get the "true" packet object.
     * For NEB-registered packets wrapped in vanilla CustomPayload packets,
     * this returns the inner payload; for vanilla packets it returns the packet itself.
     * In Forge 1.20.1 all our custom packets are directly registered as message classes,
     * so this simply returns the packet.
     */
    public static Object getTruePacket(Packet<?> packet) {
        return packet;
    }

    /**
     * Convert a CamelCase class name to snake_case.
     */
    private static String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
