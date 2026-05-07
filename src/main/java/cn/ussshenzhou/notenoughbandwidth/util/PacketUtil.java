package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
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

    private static final ConcurrentHashMap<Class<?>, ResourceLocation> TYPE_CACHE = new ConcurrentHashMap<>();
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
     * 1) For vanilla CustomPayload packets, read the payload identifier directly.
     * 2) Reflectively call Packet#type().id() when available.
     * 3) For NEB transport packet, use the registered payload id explicitly.
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

            if (PacketAggregationPacket.class == cls) {
                return PacketAggregationPacket.TYPE;
            }

            return fallbackPacketId(cls);
        });
    }

    public static ResourceLocation getTrueType(Class<?> packetClass) {
        return TYPE_CACHE.computeIfAbsent(packetClass, cls -> {
            if (PacketAggregationPacket.class == cls) {
                return PacketAggregationPacket.TYPE;
            }
            return fallbackPacketId(cls);
        });
    }

    private static ResourceLocation fallbackPacketId(Class<?> cls) {
        String override = VANILLA_NAME_OVERRIDES.get(cls.getName());
        if (override != null) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", override);
        }
        String simpleName = cls.getSimpleName();
        simpleName = stripDirectionalPrefix(simpleName);
        if (simpleName.endsWith("Packet")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Packet".length());
        }
        String path = toSnakeCase(simpleName);
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
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

    /**
     * Get the "true" packet object.
     *
     * After removing the legacy vanilla bridge transport, this helper is only
     * used by traffic-stat mixins to detect the internal aggregation payload.
     * Keep business custom payloads as the original Minecraft packet object so
     * upper layers stay closer to the NeoForge branch semantics.
     */
    public static Object getTruePacket(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
            if (PacketAggregationPacket.TYPE.equals(clientbound.getIdentifier())) {
                FriendlyByteBuf payloadCopy = clientbound.getInternalData();
                if (payloadCopy != null) {
                    try {
                        NebPayload decoded = decodeRegisteredPayload(clientbound.getIdentifier(), payloadCopy);
                        if (decoded != null) {
                            return decoded;
                        }
                    } finally {
                        if (payloadCopy.refCnt() > 0) {
                            payloadCopy.release();
                        }
                    }
                }
            }
            return packet;
        }
        if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
            if (PacketAggregationPacket.TYPE.equals(serverbound.getIdentifier())) {
                FriendlyByteBuf payload = new FriendlyByteBuf(serverbound.getData().retainedDuplicate());
                try {
                    NebPayload decoded = decodeRegisteredPayload(serverbound.getIdentifier(), payload);
                    if (decoded != null) {
                        return decoded;
                    }
                } finally {
                    if (payload.refCnt() > 0) {
                        payload.release();
                    }
                }
            }
            return packet;
        }
        return packet;
    }

    private static NebPayload decodeRegisteredPayload(ResourceLocation id, FriendlyByteBuf payload) {
        return PayloadRegistry.decode(id, payload);
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
