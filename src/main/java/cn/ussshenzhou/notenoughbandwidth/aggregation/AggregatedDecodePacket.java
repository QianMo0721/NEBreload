package cn.ussshenzhou.notenoughbandwidth.aggregation;

import com.mojang.logging.LogUtils;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Identifier;

public class AggregatedDecodePacket {
    private final Identifier type;
    private final PacketByteBuf data;

    public AggregatedDecodePacket(Identifier type, PacketByteBuf data) {
        this.type = type;
        this.data = data;
    }

    public void handle(PacketListener listener) {
        try {
            Packet<?> vanilla = PacketUtil.recreateVanillaPacket(type, data);
            if (vanilla != null) {
                dispatch(listener, vanilla);
                return;
            }
            PacketByteBuf copied = new PacketByteBuf(data.copy());
            Packet<?> packet = createCustomPacket(listener, copied);
            if (packet == null) {
                copied.release();
                return;
            }
            dispatch(listener, packet);
        } catch (Exception e) {
            LogUtils.getLogger().error("Skipped: Failed to handle packet {}", type, e);
        }
    }

    private Packet<?> createCustomPacket(PacketListener listener, PacketByteBuf copied) {
        NetworkSide side = PacketUtil.getPacketSideByListener(listener);
        return PacketUtil.createCustomPacket(side, type, copied);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dispatch(PacketListener listener, Packet<?> packet) {
        if (listener instanceof ServerPlayNetworkHandler serverPlayNetworkHandler) {
            MinecraftServer server = serverPlayNetworkHandler.player.server;
            server.execute(() -> ((Packet) packet).apply(serverPlayNetworkHandler));
            return;
        }
        ((Packet) packet).apply(listener);
    }

    public PacketByteBuf getData() {
        return data;
    }
}
