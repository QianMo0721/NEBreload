package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadCodec;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PacketDistributor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class StatQuery implements NebPayload {
    public static final String TYPE = ModConstants.MOD_ID + ":stat_query";
    public static final StatQuery SAMPLE = new StatQuery();
    public static final PayloadCodec<StatQuery> CODEC = new PayloadCodec<StatQuery>() {
        @Override
        public void encode(PacketBuffer buf, StatQuery payload) {
            payload.encode(buf);
        }

        @Override
        public StatQuery decode(PacketBuffer buf) {
            return StatQuery.decode(buf);
        }
    };

    @Override
    public String type() {
        return TYPE;
    }

    public void encode(PacketBuffer buf) {
    }

    public static StatQuery decode(PacketBuffer buf) {
        return new StatQuery();
    }

    public void handle(EntityPlayerMP player) {
        if (player == null || !player.canUseCommand(2, ModNetworkRegistry.PERMISSION_NODE)) {
            return;
        }
        ModNetworkRegistry.deliverStatSnapshot(player);
    }

    public static void handle(StatQuery payload, PayloadContext context) {
        if (context.player() instanceof EntityPlayerMP) {
            payload.handle((EntityPlayerMP) context.player());
        }
    }

    public static void sendToServer() {
        PacketDistributor.sendToServer(SAMPLE);
    }
}
