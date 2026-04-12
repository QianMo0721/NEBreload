package cn.ussshenzhou.notenoughbandwidth.util;

import com.google.common.collect.BiMap;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketUtil {
    private static final ConcurrentHashMap<Class<?>, ResourceLocation> TYPE_CACHE = new ConcurrentHashMap<Class<?>, ResourceLocation>();
    private static final ConcurrentHashMap<Class<?>, ResourceLocation> VANILLA_PACKET_IDS = new ConcurrentHashMap<Class<?>, ResourceLocation>();
    private static final Map<String, String> VANILLA_NAME_OVERRIDES = new HashMap<String, String>();
    private static volatile boolean vanillaPacketIdsInitialized;

    static {
        VANILLA_NAME_OVERRIDES.put("net.minecraft.network.play.client.CPacketPlayer", "player");
        VANILLA_NAME_OVERRIDES.put("net.minecraft.network.play.client.CPacketPlayer$Position", "player_position");
        VANILLA_NAME_OVERRIDES.put("net.minecraft.network.play.client.CPacketPlayer$Rotation", "player_rotation");
        VANILLA_NAME_OVERRIDES.put("net.minecraft.network.play.client.CPacketPlayer$PositionRotation", "player_position_rotation");
        VANILLA_NAME_OVERRIDES.put("net.minecraft.network.play.server.SPacketEntity$S15PacketEntityRelMove", "entity_rel_move");
        VANILLA_NAME_OVERRIDES.put("net.minecraft.network.play.server.SPacketEntity$S16PacketEntityLook", "entity_look");
        VANILLA_NAME_OVERRIDES.put("net.minecraft.network.play.server.SPacketEntity$S17PacketEntityLookMove", "entity_look_move");
    }

    private PacketUtil() {
    }

    public static ResourceLocation getTrueType(Packet<?> packet) {
        if (packet instanceof CPacketCustomPayload) {
            return fromChannel(LegacyCustomPayloadAccessor.getChannelName(packet));
        }
        if (packet instanceof SPacketCustomPayload) {
            return fromChannel(LegacyCustomPayloadAccessor.getChannelName(packet));
        }
        ResourceLocation fromRegistry = ModNetworkRegistry.getPacketId(packet.getClass());
        if (fromRegistry != null) {
            return fromRegistry;
        }
        return TYPE_CACHE.computeIfAbsent(packet.getClass(), PacketUtil::resolveVanillaOrFallback);
    }

    public static ResourceLocation getPacketId(Class<?> cls) {
        if (cls == null) {
            return null;
        }
        ResourceLocation fromRegistry = ModNetworkRegistry.getPacketId(cls);
        if (fromRegistry != null) {
            return fromRegistry;
        }
        return TYPE_CACHE.computeIfAbsent(cls, PacketUtil::resolveVanillaOrFallback);
    }

    public static Object getTruePacket(Packet<?> packet) {
        return packet;
    }

    private static ResourceLocation resolveVanillaOrFallback(Class<?> cls) {
        ResourceLocation vanilla = getVanillaPacketId(cls);
        if (vanilla != null) {
            return vanilla;
        }
        return fallbackPacketId(cls);
    }

    @Nullable
    private static ResourceLocation getVanillaPacketId(Class<?> cls) {
        ensureVanillaPacketIds();
        return VANILLA_PACKET_IDS.get(cls);
    }

    @SuppressWarnings("unchecked")
    private static synchronized void ensureVanillaPacketIds() {
        if (vanillaPacketIdsInitialized) {
            return;
        }
        try {
            Field directionMapsField = EnumConnectionState.class.getDeclaredField("directionMaps");
            directionMapsField.setAccessible(true);
            Map<EnumPacketDirection, BiMap<Integer, Class<? extends Packet<?>>>> directionMaps =
                    (Map<EnumPacketDirection, BiMap<Integer, Class<? extends Packet<?>>>>) directionMapsField.get(EnumConnectionState.PLAY);
            if (directionMaps != null) {
                for (BiMap<Integer, Class<? extends Packet<?>>> packetsById : directionMaps.values()) {
                    for (Class<? extends Packet<?>> packetClass : packetsById.values()) {
                        ResourceLocation id = buildVanillaPacketId(packetClass);
                        if (id != null) {
                            VANILLA_PACKET_IDS.putIfAbsent(packetClass, id);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        vanillaPacketIdsInitialized = true;
    }

    private static ResourceLocation fromChannel(String channel) {
        if (channel == null) {
            return new ResourceLocation("minecraft", "unknown");
        }
        if (channel.indexOf(':') >= 0) {
            return new ResourceLocation(channel.toLowerCase(Locale.ROOT));
        }
        if ("REGISTER".equalsIgnoreCase(channel)) {
            return new ResourceLocation("minecraft", "register");
        }
        if ("UNREGISTER".equalsIgnoreCase(channel)) {
            return new ResourceLocation("minecraft", "unregister");
        }
        if (channel.startsWith("MC|")) {
            return new ResourceLocation("minecraft", toSnakeCase(channel.substring(3)));
        }
        if (channel.startsWith("FML|")) {
            return new ResourceLocation("fml", toSnakeCase(channel.substring(4)));
        }
        return new ResourceLocation("legacy", toSnakeCase(channel));
    }

    @Nullable
    private static ResourceLocation buildVanillaPacketId(Class<?> cls) {
        String override = VANILLA_NAME_OVERRIDES.get(cls.getName());
        if (override != null) {
            return new ResourceLocation("minecraft", override);
        }

        String simpleName = buildPacketSimpleName(cls);
        simpleName = stripDirectionalPrefix(simpleName);
        if (simpleName.endsWith("Packet")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Packet".length());
        }
        if (simpleName.isEmpty()) {
            return null;
        }
        return new ResourceLocation("minecraft", toSnakeCase(simpleName));
    }

    private static ResourceLocation fallbackPacketId(Class<?> cls) {
        ResourceLocation vanilla = buildVanillaPacketId(cls);
        if (vanilla != null) {
            return vanilla;
        }
        return new ResourceLocation("minecraft", toSnakeCase(cls.getSimpleName()));
    }

    private static String buildPacketSimpleName(Class<?> cls) {
        Class<?> enclosingClass = cls.getEnclosingClass();
        if (enclosingClass == null) {
            return cls.getSimpleName();
        }
        return enclosingClass.getSimpleName() + cls.getSimpleName();
    }

    private static String stripDirectionalPrefix(String simpleName) {
        if (simpleName.startsWith("Clientbound")) {
            return simpleName.substring("Clientbound".length());
        }
        if (simpleName.startsWith("Serverbound")) {
            return simpleName.substring("Serverbound".length());
        }
        if (simpleName.startsWith("CPacket")) {
            return simpleName.substring("CPacket".length());
        }
        if (simpleName.startsWith("SPacket")) {
            return simpleName.substring("SPacket".length());
        }
        return simpleName;
    }

    private static String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder();
        char previous = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '|' || c == ' ' || c == '$') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
                previous = c;
                continue;
            }
            boolean upper = Character.isUpperCase(c);
            boolean previousLowerOrDigit = Character.isLowerCase(previous) || Character.isDigit(previous);
            if (upper && i > 0 && previousLowerOrDigit && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
            previous = c;
        }
        return sb.toString();
    }
}
