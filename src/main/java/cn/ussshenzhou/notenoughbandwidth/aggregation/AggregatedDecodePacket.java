package cn.ussshenzhou.notenoughbandwidth.aggregation;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author USS_Shenzhou
 * Holds a decoded sub-packet entry extracted from an aggregated packet bundle.
 * On handle, dispatches the sub-packet back to its target listener.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AggregatedDecodePacket {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private final ResourceLocation type;
    private final ByteBuf data;

    public AggregatedDecodePacket(ResourceLocation type, ByteBuf data) {
        this.type = type;
        this.data = data;
    }

    /**
     * Handle this sub-packet.
     * Strategy:
     *  1. Look up the packet numeric id in ConnectionProtocol by matching class name.
     *  2. Decode the packet bytes into a typed Packet<?> instance.
     *  3. Dispatch to the current packet listener.
     */
    public void handle(NetworkEvent.Context context) {
        try {
            Connection connection = context.getNetworkManager();
            if (connection == null) {
                LOGGER.error("[NEB] Skipped: no connection for sub-packet {}", type);
                return;
            }
            PacketListener listener = connection.getPacketListener();
            if (listener == null) {
                LOGGER.error("[NEB] Skipped: no packet listener for sub-packet {}", type);
                return;
            }

            // Determine receiving direction by checking the listener interface
            // ServerGamePacketListener = receiving serverbound (client sent to server)
            // ClientGamePacketListener = receiving clientbound (server sent to client)
            PacketFlow receivingFlow = getReceivingFlow(listener);
            ConnectionProtocol protocol = ConnectionProtocol.PLAY;

            Packet<?> packet = createPacketFromProtocol(protocol, receivingFlow, type,
                    new FriendlyByteBuf(data.duplicate()));

            if (packet == null) {
                // Try Forge channel (ModNetworkRegistry handles StatQuery, StatRespond, PacketAggregationPacket)
                handleAsForgeChannelPacket(context);
                return;
            }

            context.enqueueWork(() -> {
                try {
                    ((Packet<PacketListener>) packet).handle(listener);
                } catch (Exception ex) {
                    LOGGER.error("[NEB] Exception handling sub-packet {}", type, ex);
                }
            });
        } catch (Exception e) {
            LOGGER.error("[NEB] Skipped: Failed to handle sub-packet {}", type, e);
        }
    }

    /**
     * Determine which PacketFlow we are receiving based on the listener type.
     * ServerGamePacketListener receives SERVERBOUND packets (client -> server).
     * ClientGamePacketListener receives CLIENTBOUND packets (server -> client).
     */
    private static PacketFlow getReceivingFlow(PacketListener listener) {
        // Check superinterfaces: if listener is a server-side listener, packets are serverbound
        for (Class<?> iface : getAllInterfaces(listener.getClass())) {
            String name = iface.getSimpleName();
            if (name.contains("Server") || name.contains("Serverbound")) {
                return PacketFlow.SERVERBOUND;
            }
        }
        return PacketFlow.CLIENTBOUND;
    }

    private static java.util.Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        var result = new java.util.LinkedHashSet<Class<?>>();
        while (clazz != null) {
            for (Class<?> iface : clazz.getInterfaces()) {
                result.add(iface);
                result.addAll(getAllInterfaces(iface));
            }
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    /**
     * Create a Packet<?> using ConnectionProtocol reflection.
     * In Forge 1.20.1, ConnectionProtocol maintains an internal flows map.
     */
    private Packet<?> createPacketFromProtocol(
            ConnectionProtocol protocol,
            PacketFlow flow,
            ResourceLocation typeId,
            FriendlyByteBuf buf) {
        try {
            Integer id = findPacketIdByTypeName(protocol, flow, typeId);
            if (id == null) return null;
            return protocol.createPacket(flow, id, buf);
        } catch (Exception e) {
            LOGGER.debug("[NEB] createPacketFromProtocol failed for {}: {}", typeId, e.getMessage());
            return null;
        }
    }

    /**
     * Find packet integer id by matching ResourceLocation path against class simple name (snake_case).
     * ConnectionProtocol.flows is Map<PacketFlow, PacketSet>, PacketSet has classToId Map.
     */
    private static Integer findPacketIdByTypeName(ConnectionProtocol protocol, PacketFlow flow, ResourceLocation typeId) {
        try {
            Field flowsField = findField(ConnectionProtocol.class, "flows");
            if (flowsField == null) return null;
            flowsField.setAccessible(true);
            Map<PacketFlow, ?> flows = (Map<PacketFlow, ?>) flowsField.get(protocol);
            Object packetSet = flows.get(flow);
            if (packetSet == null) return null;

            Field classToIdField = findField(packetSet.getClass(), "classToId");
            if (classToIdField == null) return null;
            classToIdField.setAccessible(true);
            Map<Class<?>, Integer> classToId = (Map<Class<?>, Integer>) classToIdField.get(packetSet);

            String targetPath = typeId.getPath();
            for (Map.Entry<Class<?>, Integer> entry : classToId.entrySet()) {
                String snakeName = toSnakeCase(entry.getKey().getSimpleName());
                if (snakeName.equals(targetPath)) {
                    return entry.getValue();
                }
            }
            return null;
        } catch (Exception e) {
            LOGGER.debug("[NEB] findPacketIdByTypeName failed for {}: {}", typeId, e.getMessage());
            return null;
        }
    }

    /**
     * Handle as a Forge-registered packet via the SimpleChannel.
     * This handles NEB-specific packets (StatQuery, StatRespond).
     */
    private void handleAsForgeChannelPacket(NetworkEvent.Context context) {
        try {
            // Use Forge channel's message dispatch by decoding with the registered decoder
            var channel = cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.CHANNEL;
            FriendlyByteBuf buf = new FriendlyByteBuf(data.duplicate());
            // The channel stores messages by integer id; look up by ResourceLocation
            ResourceLocation rl = cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.getPacketId(
                    cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.ID_TO_CLASS.get(type));
            if (rl == null) {
                LOGGER.error("[NEB] No Forge handler for sub-packet {}", type);
                return;
            }
            // Dispatch via reflection on SimpleChannel internals
            dispatchForgePacket(channel, type, buf, context);
        } catch (Exception e) {
            LOGGER.error("[NEB] Skipped: handleAsForgeChannelPacket failed for {}", type, e);
        }
    }

    /**
     * Dispatch a packet through Forge's SimpleChannel by looking up the registered message handler.
     */
    private void dispatchForgePacket(
            net.minecraftforge.network.simple.SimpleChannel channel,
            ResourceLocation typeId,
            FriendlyByteBuf buf,
            NetworkEvent.Context context) {
        try {
            // SimpleChannel has a field "messageIndex" (List<Triple<...>>)
            // Triple: (IntRange discriminator, Pair<decoder, consumer>, Optional<direction>)
            // We match on the stored ResourceLocation for each registered message.
            Field indexField = findField(channel.getClass(), "messageIndex");
            if (indexField == null) {
                LOGGER.error("[NEB] Cannot find messageIndex in SimpleChannel");
                return;
            }
            indexField.setAccessible(true);
            java.util.List<?> messageIndex = (java.util.List<?>) indexField.get(channel);
            // Each entry is Triple<IntRange, Pair<BiFunction<FriendlyByteBuf,Integer,?>, BiConsumer<?,Supplier<NetworkEvent.Context>>>, Optional<NetworkDirection>>
            // We need to match typeId against the channel name registered in the index
            // Simplest: try to decode with all registered decoders, match by type after decode
            // Instead: look up the id map we already built in ModNetworkRegistry
            var cls = cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.ID_TO_CLASS.get(typeId);
            if (cls == null) {
                LOGGER.error("[NEB] No class registered for {}", typeId);
                return;
            }
            // Find the integer message id for this class
            Field indexByClassField = findField(channel.getClass(), "indexedCodecs");
            if (indexByClassField != null) {
                indexByClassField.setAccessible(true);
            }
            // Fallback: Just use ModNetworkRegistry helper
            cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.dispatchPacket(typeId, buf, context);
        } catch (Exception e) {
            LOGGER.error("[NEB] dispatchForgePacket failed for {}", typeId, e);
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> cur = clazz;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

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

    public ByteBuf getData() {
        return data;
    }
}
