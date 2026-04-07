package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.LOCAL;

/**
 * @author USS_Shenzhou
 */
public class StatQuery {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "stat_query");

    public StatQuery() {
    }

    public StatQuery(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
        // empty
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.getSender();
            if (serverPlayer != null && serverPlayer.hasPermissions(2)) {
                cn.ussshenzhou.network.ModNetworkRegistry.RESPOND_CHANNEL.sendTo(
                        new StatRespond(
                                LOCAL.inboundBytesBaked().get(),
                                LOCAL.inboundBytesRaw().get(),
                                LOCAL.outboundBytesBaked().get(),
                                LOCAL.outboundBytesRaw().get(),
                                LOCAL.inboundSpeedBaked().averageIn1s(),
                                LOCAL.inboundSpeedRaw().averageIn1s(),
                                LOCAL.outboundSpeedBaked().averageIn1s(),
                                LOCAL.outboundSpeedRaw().averageIn1s()
                        ),
                        serverPlayer.connection.connection,
                        NetworkDirection.PLAY_TO_CLIENT
                );
            }
        });
        ctx.setPacketHandled(true);
    }
}
