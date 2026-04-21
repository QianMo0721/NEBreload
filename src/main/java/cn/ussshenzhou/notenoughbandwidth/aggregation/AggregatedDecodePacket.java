package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
import net.minecraft.server.network.ServerGamePacketListenerImpl;

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
    @Nullable
    private final NebPayload payload;

    public AggregatedDecodePacket(ResourceLocation type, ByteBuf data) {
        this.type = type;
        this.vanillaPacketId = -1;
        this.data = data;
        this.payload = decodeRegisteredPayload(type, data);
    }

    public AggregatedDecodePacket(int vanillaPacketId, ByteBuf data) {
        this.type = null;
        this.vanillaPacketId = vanillaPacketId;
        this.data = data;
        this.payload = null;
    }

    @Nullable
    private static NebPayload decodeRegisteredPayload(@Nullable ResourceLocation type, ByteBuf data) {
        if (type == null || !PayloadRegistry.contains(type)) {
            return null;
        }
        FriendlyByteBuf payloadBuf = new FriendlyByteBuf(data.retainedDuplicate());
        try {
            return PayloadRegistry.decode(type, payloadBuf);
        } finally {
            payloadBuf.release();
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
            PayloadContext payloadContext = PayloadContext.of(connection, listener, flow);
            Packet<?> packet = createVanillaPacket(flow);
            if (packet != null) {
                try {
                    ((Packet<PacketListener>) packet).handle(listener);
                } catch (RunningOnDifferentThreadException ignored) {
                    return;
                }
                return;
            }
            if (payload != null
                    && hasNegotiatedPayloadChannel(connection, type)
                    && handleRegisteredPayload(payloadContext)) {
                return;
            }
            if (dispatchCustomPayload(flow, listener)) {
                return;
            }
            LOGGER.error("[NEB] Skipped: unable to replay aggregated sub-packet {}", type);
        } catch (Exception e) {
            LOGGER.error("[NEB] Skipped: Failed to replay sub-packet {}", type, e);
        }
    }

    private Packet<?> createVanillaPacket(PacketFlow flow) {
        int packetId = vanillaPacketId;
        if (packetId < 0 && type != null) {
            packetId = PacketUtil.getVanillaPacketId(flow, type);
        }
        if (packetId < 0) {
            return null;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(data.duplicate());
        try {
            return ConnectionProtocol.PLAY.createPacket(flow, packetId, buf);
        } catch (Exception e) {
            LOGGER.debug("[NEB] createVanillaPacket failed for {} / {}: {}", packetId, type, e.getMessage());
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
            if (payload != null) {
                PayloadRegistry.encode(buf, payload);
            } else {
                buf.writeBytes(data.duplicate());
            }
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

    private boolean handleRegisteredPayload(PayloadContext context) {
        if (payload == null) {
            return false;
        }
        context.handle(payload);
        return true;
    }

    private boolean dispatchCustomPayload(PacketFlow flow, @Nullable PacketListener listener) {
        if (type == null || listener == null) {
            return false;
        }
        try {
            if (flow == PacketFlow.CLIENTBOUND && listener instanceof ClientPacketListener) {
                Packet<?> packet = createCustomPayloadPacket(flow);
                if (packet instanceof ClientboundCustomPayloadPacket clientboundPacket) {
                    ((Packet) clientboundPacket).handle(listener);
                    return true;
                }
                return false;
            }
            if (flow == PacketFlow.SERVERBOUND && listener instanceof ServerGamePacketListenerImpl) {
                Packet<?> packet = createCustomPayloadPacket(flow);
                if (packet instanceof ServerboundCustomPayloadPacket serverboundPacket) {
                    ((Packet) serverboundPacket).handle(listener);
                    return true;
                }
                return false;
            }
            Packet<?> packet = createCustomPayloadPacket(flow);
            if (packet == null) {
                return false;
            }
            ((Packet<PacketListener>) packet).handle(listener);
            return true;
        } catch (RunningOnDifferentThreadException ignored) {
            return true;
        } catch (Exception e) {
            LOGGER.error("[NEB] Failed to dispatch custom payload {}", type, e);
            return false;
        }
    }

    private boolean hasNegotiatedPayloadChannel(Connection connection, ResourceLocation id) {
        if (id == null) {
            return false;
        }
        var setup = ChannelAttributes.getPayloadSetup(connection);
        return setup == null || setup.hasChannel(id);
    }

    public ByteBuf getData() {
        return data;
    }
}
