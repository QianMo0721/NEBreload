package cn.ussshenzhou.notenoughbandwidth.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
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
     * 1) For vanilla CustomPayload packets, read getIdentifier() directly.
     * 2) Reflectively call type().id() if available.
     * 3) For NEB-registered packets use the registration map.
     * 4) Fallback to class-name-derived minecraft:snake_case.
     */
    public static ResourceLocation getTrueType(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
            return clientbound.getIdentifier();
        }
        if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
            return serverbound.getIdentifier();
        }
        return TYPE_CACHE.computeIfAbsent(packet.getClass(), cls -> {
            // 1) Try Packet#type().id() via reflection
            try {
                Method typeMethod = cls.getMethod("type");
                Object packetType = typeMethod.invoke(packet);
                if (packetType != null) {
                    Method idMethod = packetType.getClass().getMethod("id");
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

            // 3) Fallback: use simple class name as path under minecraft namespace
            return fallbackPacketId(cls);
        });
    }

    /**
     * Resolve the canonical id for a packet class without having a packet instance.
     */
    public static ResourceLocation getPacketId(Class<?> cls) {
        if (cls == null) {
            return null;
        }
        ResourceLocation fromRegistry = ModNetworkRegistry.getPacketId(cls);
        if (fromRegistry != null) {
            return fromRegistry;
        }
        return fallbackPacketId(cls);
    }

    private static ResourceLocation fallbackPacketId(Class<?> cls) {
        String simpleName = cls.getSimpleName();
        if (simpleName.endsWith("Packet")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Packet".length());
        }
        String path = toSnakeCase(simpleName);
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    /**
     * Get the "true" packet object.
     * For Forge 1.20.1 game custom payload packets, return the packet itself for now;
     * the caller should use getTrueType(packet) to read the embedded channel id.
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
