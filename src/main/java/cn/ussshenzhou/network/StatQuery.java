package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PacketDistributor;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadCodec;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.LOCAL;

/**
 * @author USS_Shenzhou
 */
public class StatQuery implements NebPayload {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "stat_query");
    public static final StatQuery SAMPLE = new StatQuery();
    public static final PayloadCodec<StatQuery> CODEC = new PayloadCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, StatQuery payload) {
        }

        @Override
        public StatQuery decode(FriendlyByteBuf buf) {
            return new StatQuery(buf);
        }
    };

    public StatQuery() {
    }

    public StatQuery(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
        // empty
    }

    public void handle(PayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
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
        });
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }
}
