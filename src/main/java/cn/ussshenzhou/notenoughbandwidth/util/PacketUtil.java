package cn.ussshenzhou.notenoughbandwidth.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;

public class PacketUtil {
    public static Identifier getTrueType(Packet<?> packet) {
        if (packet instanceof CustomPayloadC2SPacket customPayloadC2SPacket) {
            return customPayloadC2SPacket.getChannel();
        }
        if (packet instanceof CustomPayloadS2CPacket customPayloadS2CPacket) {
            return customPayloadS2CPacket.getChannel();
        }
        return getVanillaType(packet);
    }

    public static Object getTruePacket(Packet<?> packet) {
        if (packet instanceof CustomPayloadC2SPacket customPayloadC2SPacket) {
            return customPayloadC2SPacket.getData();
        }
        if (packet instanceof CustomPayloadS2CPacket customPayloadS2CPacket) {
            return customPayloadS2CPacket.getData();
        }
        return packet;
    }

    public static PacketByteBuf getPayloadData(Packet<?> packet) {
        if (packet instanceof CustomPayloadC2SPacket customPayloadC2SPacket) {
            return customPayloadC2SPacket.getData();
        }
        if (packet instanceof CustomPayloadS2CPacket customPayloadS2CPacket) {
            return customPayloadS2CPacket.getData();
        }
        return null;
    }

    public static Identifier getVanillaType(Packet<?> packet) {
        int packetId = getPacketId(packet);
        if (packetId < 0) {
            return Identifier.of("minecraft", packet.getClass().getSimpleName().toLowerCase());
        }
        NetworkSide side = getPacketSide(packet);
        return Identifier.of("minecraft", (side == NetworkSide.CLIENTBOUND ? "clientbound" : "serverbound") + "/" + packetId);
    }

    public static int getPacketId(Packet<?> packet) {
        NetworkState state = NetworkState.getPacketHandlerState(packet);
        if (state == null) {
            return -1;
        }
        NetworkSide side = getPacketSide(packet);
        return state.getPacketId(side, packet);
    }

    public static Packet<?> recreateVanillaPacket(Identifier type, PacketByteBuf data) {
        if (!"minecraft".equals(type.getNamespace())) {
            return null;
        }
        String path = type.getPath();
        int split = path.indexOf('/');
        if (split < 0) {
            return null;
        }
        NetworkSide side = switch (path.substring(0, split)) {
            case "clientbound" -> NetworkSide.CLIENTBOUND;
            case "serverbound" -> NetworkSide.SERVERBOUND;
            default -> null;
        };
        if (side == null) {
            return null;
        }
        int id;
        try {
            id = Integer.parseInt(path.substring(split + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
        return NetworkState.PLAY.getPacketHandler(side, id, new PacketByteBuf(data.copy()));
    }

    public static Packet<?> createCustomPacket(NetworkSide side, Identifier type, PacketByteBuf copied) {
        return side == NetworkSide.CLIENTBOUND
                ? new CustomPayloadS2CPacket(type, copied)
                : new CustomPayloadC2SPacket(type, copied);
    }

    public static void writeVanillaPacket(Packet<?> packet, ByteBuf out) {
        ByteBuf scratch = ByteBufAllocator.DEFAULT.buffer();
        try {
            packet.write(new PacketByteBuf(scratch));
            out.writeBytes(scratch, scratch.readerIndex(), scratch.readableBytes());
        } finally {
            scratch.release();
        }
    }

    public static NetworkSide getPacketSide(Packet<?> packet) {
        return packet instanceof CustomPayloadS2CPacket || packet.getClass().getName().contains(".s2c.")
                ? NetworkSide.CLIENTBOUND
                : NetworkSide.SERVERBOUND;
    }

    public static NetworkSide getPacketSideByListener(net.minecraft.network.listener.PacketListener listener) {
        return listener instanceof net.minecraft.network.listener.ClientPlayPacketListener
                ? NetworkSide.CLIENTBOUND
                : NetworkSide.SERVERBOUND;
    }
}
