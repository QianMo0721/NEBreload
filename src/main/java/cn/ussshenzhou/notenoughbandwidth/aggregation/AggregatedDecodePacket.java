package cn.ussshenzhou.notenoughbandwidth.aggregation;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

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

            PacketFlow receivingFlow = getReceivingFlow(listener);
            Packet<?> packet = tryCreateVanillaPacket(receivingFlow);
            if (packet != null) {
                Packet<?> finalPacket = packet;
                context.enqueueWork(() -> {
                    try {
                        ((Packet<PacketListener>) finalPacket).handle(listener);
                    } catch (Exception ex) {
                        LOGGER.error("[NEB] Exception handling vanilla sub-packet {}", type, ex);
                    }
                });
                return;
            }

            packet = createCustomPayloadPacket(receivingFlow);
            if (packet != null) {
                Packet<?> finalPacket = packet;
                context.enqueueWork(() -> {
                    try {
                        ((Packet<PacketListener>) finalPacket).handle(listener);
                    } catch (Exception ex) {
                        LOGGER.error("[NEB] Exception handling custom sub-packet {}", type, ex);
                    }
                });
                return;
            }

            LOGGER.error("[NEB] Skipped: unable to decode aggregated sub-packet {}", type);
        } catch (Exception e) {
            LOGGER.error("[NEB] Skipped: Failed to handle sub-packet {}", type, e);
        }
    }

    public Packet<?> decode(PacketFlow flow) {
        Packet<?> packet = tryCreateVanillaPacket(flow);
        if (packet != null) {
            return packet;
        }
        return createCustomPayloadPacket(flow);
    }

    private Packet<?> tryCreateVanillaPacket(PacketFlow flow) {
        FriendlyByteBuf buf = new FriendlyByteBuf(data.duplicate());
        try {
            Integer id = findPacketId(ConnectionProtocol.PLAY, flow, type);
            if (id == null) {
                return null;
            }
            return ConnectionProtocol.PLAY.createPacket(flow, id, buf);
        } catch (Exception e) {
            LOGGER.debug("[NEB] tryCreateVanillaPacket failed for {}: {}", type, e.getMessage());
            return null;
        }
    }

    private Packet<?> createCustomPayloadPacket(PacketFlow flow) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.ByteBufAllocator.DEFAULT.buffer());
        try {
            buf.writeResourceLocation(type);
            buf.writeBytes(data.duplicate());
            if (flow == PacketFlow.CLIENTBOUND) {
                return new ClientboundCustomPayloadPacket(buf);
            }
            return new ServerboundCustomPayloadPacket(buf);
        } catch (Exception e) {
            LOGGER.debug("[NEB] createCustomPayloadPacket failed for {}: {}", type, e.getMessage());
            return null;
        } finally {
            buf.release();
        }
    }

    /**
     * ServerGamePacketListener receives SERVERBOUND packets.
     * ClientGamePacketListener receives CLIENTBOUND packets.
     */
    private static PacketFlow getReceivingFlow(PacketListener listener) {
        String className = listener.getClass().getName();
        if (className.contains("Server") || className.contains("server")) {
            return PacketFlow.SERVERBOUND;
        }
        return PacketFlow.CLIENTBOUND;
    }

    private static Integer findPacketId(ConnectionProtocol protocol, PacketFlow flow, ResourceLocation typeId) {
        try {
            Int2ObjectMap<Class<? extends Packet<?>>> packetsByIds = protocol.getPacketsByIds(flow);
            for (Int2ObjectMap.Entry<Class<? extends Packet<?>>> entry : packetsByIds.int2ObjectEntrySet()) {
                Class<? extends Packet<?>> packetClass = entry.getValue();
                ResourceLocation packetId = cn.ussshenzhou.notenoughbandwidth.util.PacketUtil.getPacketId(packetClass);
                if (typeId.equals(packetId)) {
                    return entry.getIntKey();
                }
            }
            return null;
        } catch (Exception e) {
            LOGGER.debug("[NEB] findPacketId failed for {}: {}", typeId, e.getMessage());
            return null;
        }
    }

    private static boolean isCustomPayloadType(ResourceLocation typeId) {
        return !"minecraft".equals(typeId.getNamespace()) || "custom_payload".equals(typeId.getPath());
    }

    public ByteBuf getData() {
        return data;
    }
}
