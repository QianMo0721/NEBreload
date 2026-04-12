package cn.ussshenzhou.notenoughbandwidth.util;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyCustomPayloadAccessor {
    private static final ConcurrentHashMap<Class<?>, Field> CHANNEL_FIELDS = new ConcurrentHashMap<Class<?>, Field>();
    private static final ConcurrentHashMap<Class<?>, Field> DATA_FIELDS = new ConcurrentHashMap<Class<?>, Field>();

    private LegacyCustomPayloadAccessor() {
    }

    public static String getChannelName(Packet<?> packet) {
        if (!(packet instanceof CPacketCustomPayload) && !(packet instanceof SPacketCustomPayload)) {
            return null;
        }
        Object value = getFieldValue(packet, CHANNEL_FIELDS, getChannelFieldCandidates(packet));
        return value instanceof String ? (String) value : null;
    }

    public static PacketBuffer getBufferData(Packet<?> packet) {
        if (!(packet instanceof CPacketCustomPayload) && !(packet instanceof SPacketCustomPayload)) {
            return null;
        }
        Object value = getFieldValue(packet, DATA_FIELDS, getDataFieldCandidates(packet));
        return value instanceof PacketBuffer ? (PacketBuffer) value : null;
    }

    public static CPacketCustomPayload createCPacket(String channelName, PacketBuffer payload) {
        CPacketCustomPayload packet = new CPacketCustomPayload();
        setFieldValue(packet, CHANNEL_FIELDS, getChannelFieldCandidates(packet), channelName);
        setFieldValue(packet, DATA_FIELDS, getDataFieldCandidates(packet), payload);
        return packet;
    }

    public static SPacketCustomPayload createSPacket(String channelName, PacketBuffer payload) {
        SPacketCustomPayload packet = new SPacketCustomPayload();
        setFieldValue(packet, CHANNEL_FIELDS, getChannelFieldCandidates(packet), channelName);
        setFieldValue(packet, DATA_FIELDS, getDataFieldCandidates(packet), payload);
        return packet;
    }

    private static Object getFieldValue(Object instance, ConcurrentHashMap<Class<?>, Field> cache, String[] candidates) {
        try {
            Field field = cache.computeIfAbsent(instance.getClass(), cls -> resolveField(cls, candidates));
            return field == null ? null : field.get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access custom payload field", e);
        }
    }

    private static void setFieldValue(Object instance, ConcurrentHashMap<Class<?>, Field> cache, String[] candidates, Object value) {
        try {
            Field field = cache.computeIfAbsent(instance.getClass(), cls -> resolveField(cls, candidates));
            if (field == null) {
                throw new IllegalStateException("Failed to resolve custom payload field for " + instance.getClass().getName());
            }
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to write custom payload field", e);
        }
    }

    private static Field resolveField(Class<?> cls, String[] candidates) {
        for (String candidate : candidates) {
            try {
                Field field = cls.getDeclaredField(candidate);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static String[] getChannelFieldCandidates(Packet<?> packet) {
        if (packet instanceof CPacketCustomPayload) {
            return new String[]{"channel", "field_149562_a"};
        }
        return new String[]{"channel", "field_149172_a"};
    }

    private static String[] getDataFieldCandidates(Packet<?> packet) {
        if (packet instanceof CPacketCustomPayload) {
            return new String[]{"data", "field_149561_c"};
        }
        return new String[]{"data", "field_149171_b"};
    }
}
