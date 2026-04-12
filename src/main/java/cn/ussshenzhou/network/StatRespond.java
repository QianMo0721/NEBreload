package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * @author USS_Shenzhou
 */
public class StatRespond implements IMessage {

    private long inboundBytesBaked;
    private long inboundBytesRaw;
    private long outboundBytesBaked;
    private long outboundBytesRaw;
    private double inboundSpeedBaked;
    private double inboundSpeedRaw;
    private double outboundSpeedBaked;
    private double outboundSpeedRaw;

    public StatRespond(long inboundBytesBaked, long inboundBytesRaw, long outboundBytesBaked, long outboundBytesRaw,
                       double inboundSpeedBaked, double inboundSpeedRaw, double outboundSpeedBaked, double outboundSpeedRaw) {
        this.inboundBytesBaked = inboundBytesBaked;
        this.inboundBytesRaw = inboundBytesRaw;
        this.outboundBytesBaked = outboundBytesBaked;
        this.outboundBytesRaw = outboundBytesRaw;
        this.inboundSpeedBaked = inboundSpeedBaked;
        this.inboundSpeedRaw = inboundSpeedRaw;
        this.outboundSpeedBaked = outboundSpeedBaked;
        this.outboundSpeedRaw = outboundSpeedRaw;
    }

    public StatRespond() {
        this(0L, 0L, 0L, 0L, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        inboundBytesBaked = buf.readLong();
        inboundBytesRaw = buf.readLong();
        outboundBytesBaked = buf.readLong();
        outboundBytesRaw = buf.readLong();
        inboundSpeedBaked = buf.readDouble();
        inboundSpeedRaw = buf.readDouble();
        outboundSpeedBaked = buf.readDouble();
        outboundSpeedRaw = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(inboundBytesBaked);
        buf.writeLong(inboundBytesRaw);
        buf.writeLong(outboundBytesBaked);
        buf.writeLong(outboundBytesRaw);
        buf.writeDouble(inboundSpeedBaked);
        buf.writeDouble(inboundSpeedRaw);
        buf.writeDouble(outboundSpeedBaked);
        buf.writeDouble(outboundSpeedRaw);
    }

    public static class Handler implements IMessageHandler<StatRespond, IMessage> {
        @Override
        public IMessage onMessage(final StatRespond message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    SimpleStatManager.inboundBytesBakedServer = message.inboundBytesBaked;
                    SimpleStatManager.inboundBytesRawServer = message.inboundBytesRaw;
                    SimpleStatManager.outboundBytesBakedServer = message.outboundBytesBaked;
                    SimpleStatManager.outboundBytesRawServer = message.outboundBytesRaw;
                    SimpleStatManager.inboundSpeedBakedServer = message.inboundSpeedBaked;
                    SimpleStatManager.inboundSpeedRawServer = message.inboundSpeedRaw;
                    SimpleStatManager.outboundSpeedBakedServer = message.outboundSpeedBaked;
                    SimpleStatManager.outboundSpeedRawServer = message.outboundSpeedRaw;
                }
            });
            return null;
        }
    }
}
