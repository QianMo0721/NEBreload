package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PacketDistributor;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadCodec;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.LOCAL;

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

    public static void handle(StatQuery payload, PayloadContext context) {
        context.enqueueWork(new Runnable() {
            @Override
            public void run() {
                if (context.player() instanceof EntityPlayerMP) {
                    EntityPlayerMP serverPlayer = (EntityPlayerMP) context.player();
                    if (serverPlayer.canUseCommand(2, ModNetworkRegistry.PERMISSION_NODE)) {
                        PacketDistributor.sendToPlayer(serverPlayer, new StatRespond(
                                LOCAL.inboundBytesBaked().get(),
                                LOCAL.inboundBytesRaw().get(),
                                LOCAL.outboundBytesBaked().get(),
                                LOCAL.outboundBytesRaw().get(),
                                LOCAL.inboundSpeedBaked().averageIn1s(),
                                LOCAL.inboundSpeedRaw().averageIn1s(),
                                LOCAL.outboundSpeedBaked().averageIn1s(),
                                LOCAL.outboundSpeedRaw().averageIn1s()
                        ));
                    }
                }
            }
        });
    }

    public static void sendToServer() {
        PacketDistributor.sendToServer(SAMPLE);
    }
}
