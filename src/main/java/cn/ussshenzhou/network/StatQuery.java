package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.LOCAL;

/**
 * @author USS_Shenzhou
 */
public class StatQuery implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<StatQuery, IMessage> {
        @Override
        public IMessage onMessage(StatQuery message, MessageContext ctx) {
            final EntityPlayerMP serverPlayer = ctx.getServerHandler().player;
            serverPlayer.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (serverPlayer.canUseCommand(2, ModConstants.MOD_ID)) {
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
                                serverPlayer
                        );
                    }
                }
            });
            return null;
        }
    }
}
