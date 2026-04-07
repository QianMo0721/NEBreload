package cn.ussshenzhou.notenoughbandwidth.util;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author USS_Shenzhou
 */
public class PacketUtil {

    // Cache: packet class -> ResourceLocation id
    private static final ConcurrentHashMap<Class<?>, ResourceLocation> TYPE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, ResourceLocation> VANILLA_PACKET_IDS = new ConcurrentHashMap<>();
    private static volatile boolean vanillaPacketIdsInitialized = false;
    private static final Map<String, String> VANILLA_NAME_OVERRIDES = Map.ofEntries(
            Map.entry("net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket", "mount_screen_open"),
            Map.entry("net.minecraft.network.protocol.game.ClientboundRecipePacket", "update_recipes"),
            Map.entry("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Pos", "move_entity_pos"),
            Map.entry("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$PosRot", "move_entity_pos_rot"),
            Map.entry("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot", "move_entity_rot"),
            Map.entry("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Pos", "move_player_pos"),
            Map.entry("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$PosRot", "move_player_pos_rot"),
            Map.entry("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Rot", "move_player_rot"),
            Map.entry("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$StatusOnly", "move_player_status_only")
    );

    /**
     * Get the true ResourceLocation type of a Packet.
     * Order:
     * 1) For vanilla CustomPayload packets, read getIdentifier() directly.
     * 2) Use explicit vanilla packet mappings derived from ConnectionProtocol.
     * 3) Reflectively call type().id() if available.
     * 4) For NEB-registered packets use the registration map.
     * 5) Fallback to class-name-derived minecraft:snake_case.
     */
    public static ResourceLocation getTrueType(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
            return clientbound.getIdentifier();
        }
        if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
            return serverbound.getIdentifier();
        }
        return TYPE_CACHE.computeIfAbsent(packet.getClass(), cls -> {
            ResourceLocation vanilla = getVanillaPacketId(cls);
            if (vanilla != null) {
                return vanilla;
            }

            // Try Packet#type().id() via reflection
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

            ResourceLocation fromRegistry = ModNetworkRegistry.getPacketId(cls);
            if (fromRegistry != null) {
                return fromRegistry;
            }

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
        ResourceLocation vanilla = getVanillaPacketId(cls);
        if (vanilla != null) {
            return vanilla;
        }
        ResourceLocation fromRegistry = ModNetworkRegistry.getPacketId(cls);
        if (fromRegistry != null) {
            return fromRegistry;
        }
        return fallbackPacketId(cls);
    }

    private static ResourceLocation getVanillaPacketId(Class<?> cls) {
        ensureVanillaPacketIds();
        return VANILLA_PACKET_IDS.get(cls);
    }

    private static synchronized void ensureVanillaPacketIds() {
        if (vanillaPacketIdsInitialized) {
            return;
        }
        registerVanillaPackets(PacketFlow.CLIENTBOUND);
        registerVanillaPackets(PacketFlow.SERVERBOUND);
        vanillaPacketIdsInitialized = true;
    }

    private static void registerVanillaPackets(PacketFlow flow) {
        var packetsByIds = ConnectionProtocol.PLAY.getPacketsByIds(flow);
        for (Class<? extends Packet<?>> packetClass : packetsByIds.values()) {
            VANILLA_PACKET_IDS.putIfAbsent(packetClass, buildVanillaPacketId(packetClass));
        }
    }

    private static ResourceLocation buildVanillaPacketId(Class<?> cls) {
        String override = VANILLA_NAME_OVERRIDES.get(cls.getName());
        if (override != null) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", override);
        }

        String simpleName = cls.getSimpleName();
        simpleName = stripDirectionalPrefix(simpleName);
        if (simpleName.endsWith("Packet")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Packet".length());
        }
        if (simpleName.isEmpty()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath("minecraft", toSnakeCase(simpleName));
    }

    private static String stripDirectionalPrefix(String simpleName) {
        if (simpleName.startsWith("Clientbound")) {
            return simpleName.substring("Clientbound".length());
        }
        if (simpleName.startsWith("Serverbound")) {
            return simpleName.substring("Serverbound".length());
        }
        return simpleName;
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
