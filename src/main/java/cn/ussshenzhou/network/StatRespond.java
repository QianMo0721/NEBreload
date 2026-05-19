package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadCodec;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import net.minecraft.network.PacketBuffer;

public class StatRespond implements NebPayload {
    public static final String TYPE = ModConstants.MOD_ID + ":stat_resp";
    public static final StatRespond SAMPLE = new StatRespond(0, 0, 0, 0, 0, 0, 0, 0);
    public static final PayloadCodec<StatRespond> CODEC = new PayloadCodec<StatRespond>() {
        @Override
        public void encode(PacketBuffer buf, StatRespond payload) {
            payload.encode(buf);
        }

        @Override
        public StatRespond decode(PacketBuffer buf) {
            return StatRespond.decode(buf);
        }
    };

    public final long inboundBytesBaked;
    public final long inboundBytesRaw;
    public final long outboundBytesBaked;
    public final long outboundBytesRaw;
    public final double inboundSpeedBaked;
    public final double inboundSpeedRaw;
    public final double outboundSpeedBaked;
    public final double outboundSpeedRaw;

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

    @Override
    public String type() {
        return TYPE;
    }

    public void encode(PacketBuffer buf) {
        buf.writeLong(inboundBytesBaked);
        buf.writeLong(inboundBytesRaw);
        buf.writeLong(outboundBytesBaked);
        buf.writeLong(outboundBytesRaw);
        buf.writeDouble(inboundSpeedBaked);
        buf.writeDouble(inboundSpeedRaw);
        buf.writeDouble(outboundSpeedBaked);
        buf.writeDouble(outboundSpeedRaw);
    }

    public static StatRespond decode(PacketBuffer buf) {
        return new StatRespond(
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
        );
    }

    public void handle() {
        SimpleStatManager.inboundBytesBakedServer = inboundBytesBaked;
        SimpleStatManager.inboundBytesRawServer = inboundBytesRaw;
        SimpleStatManager.outboundBytesBakedServer = outboundBytesBaked;
        SimpleStatManager.outboundBytesRawServer = outboundBytesRaw;
        SimpleStatManager.inboundSpeedBakedServer = inboundSpeedBaked;
        SimpleStatManager.inboundSpeedRawServer = inboundSpeedRaw;
        SimpleStatManager.outboundSpeedBakedServer = outboundSpeedBaked;
        SimpleStatManager.outboundSpeedRawServer = outboundSpeedRaw;
    }

    public static void handle(StatRespond payload, PayloadContext context) {
        payload.handle();
    }
}
