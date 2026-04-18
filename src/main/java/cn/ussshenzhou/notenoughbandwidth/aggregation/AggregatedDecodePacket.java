package cn.ussshenzhou.notenoughbandwidth.aggregation;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraftforge.network.ICustomPacket;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * @author USS_Shenzhou
 * Holds a decoded sub-packet entry extracted from an aggregated packet bundle.
 * On handle, dispatches the sub-packet back to its target listener.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AggregatedDecodePacket {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private final ResourceLocation type;
    private final int vanillaPacketId;
    private final ByteBuf data;

    public AggregatedDecodePacket(ResourceLocation type, ByteBuf data) {
        this.type = type;
        this.vanillaPacketId = -1;
        this.data = data;
    }

    public AggregatedDecodePacket(int vanillaPacketId, ByteBuf data) {
        this.type = null;
        this.vanillaPacketId = vanillaPacketId;
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

            PacketFlow receivingFlow = connection.getReceiving();
            Packet<?> packet = createVanillaPacket(receivingFlow);
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

            if (dispatchCustomPayload(connection, receivingFlow, listener)) {
                return;
            }

            LOGGER.error("[NEB] Skipped: unable to decode aggregated sub-packet {}", type);
        } catch (Exception e) {
            LOGGER.error("[NEB] Skipped: Failed to handle sub-packet {}", type, e);
        }
    }

    public void replay(Connection connection, PacketFlow flow) {
        try {
            if (connection == null) {
                LOGGER.error("[NEB] Skipped: no connection for replayed sub-packet {}", type);
                return;
            }
            PacketListener listener = connection.getPacketListener();
            if (listener == null) {
                LOGGER.error("[NEB] Skipped: no packet listener for replayed sub-packet {}", type);
                return;
            }
            Packet<?> packet = createVanillaPacket(flow);
            if (packet != null) {
                try {
                    ((Packet<PacketListener>) packet).handle(listener);
                } catch (RunningOnDifferentThreadException ignored) {
                    return;
                }
                return;
            }
            if (dispatchCustomPayload(connection, flow, listener)) {
                return;
            }
            LOGGER.error("[NEB] Skipped: unable to replay aggregated sub-packet {}", type);
        } catch (Exception e) {
            LOGGER.error("[NEB] Skipped: Failed to replay sub-packet {}", type, e);
        }
    }

    public Packet<?> decode(PacketFlow flow) {
        Packet<?> packet = createVanillaPacket(flow);
        if (packet != null) {
            return packet;
        }
        return createCustomPayloadPacket(flow);
    }

    private Packet<?> createVanillaPacket(PacketFlow flow) {
        if (vanillaPacketId < 0) {
            return null;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(data.duplicate());
        try {
            return ConnectionProtocol.PLAY.createPacket(flow, vanillaPacketId, buf);
        } catch (Exception e) {
            LOGGER.debug("[NEB] createVanillaPacket failed for {} / {}: {}", vanillaPacketId, type, e.getMessage());
            return null;
        }
    }

    private Packet<?> createCustomPayloadPacket(PacketFlow flow) {
        if (type == null) {
            return null;
        }
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

    private boolean dispatchCustomPayload(Connection connection, PacketFlow flow, @Nullable PacketListener listener) {
        if (type == null) {
            return false;
        }
        Packet<?> packet = createCustomPayloadPacket(flow);
        if (!(packet instanceof ICustomPacket<?> customPacket)) {
            return false;
        }
        try {
            if (NetworkHooks.onCustomPayload(customPacket, connection)) {
                return true;
            }
            if (!"minecraft".equals(type.getNamespace())) {
                LOGGER.debug("[NEB] Forge custom payload dispatch returned false for {}, but namespace is modded; skip vanilla fallback", type);
                return true;
            }
            LOGGER.debug("[NEB] Forge custom payload dispatch returned false for {}, falling back to vanilla custom payload path", type);
            if (listener != null) {
                try {
                    ((Packet<PacketListener>) packet).handle(listener);
                } catch (RunningOnDifferentThreadException ignored) {
                    return true;
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("[NEB] Failed to dispatch Forge custom payload {}", type, e);
            return false;
        }
    }

    public ByteBuf getData() {
        return data;
    }
}
