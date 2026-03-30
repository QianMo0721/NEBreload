package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.notenoughbandwidth.util.TimeCounter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author USS_Shenzhou
 */
public class SimpleStatData {
    private final AtomicLong inboundBytesBaked = new AtomicLong(0);
    private final AtomicLong inboundBytesRaw = new AtomicLong(0);
    private final AtomicLong outboundBytesBaked = new AtomicLong(0);
    private final AtomicLong outboundBytesRaw = new AtomicLong(0);
    private final TimeCounter inboundSpeedBaked = new TimeCounter();
    private final TimeCounter inboundSpeedRaw = new TimeCounter();
    private final TimeCounter outboundSpeedBaked = new TimeCounter();
    private final TimeCounter outboundSpeedRaw = new TimeCounter();

    public AtomicLong inboundBytesBaked() {
        return inboundBytesBaked;
    }

    public AtomicLong inboundBytesRaw() {
        return inboundBytesRaw;
    }

    public AtomicLong outboundBytesBaked() {
        return outboundBytesBaked;
    }

    public AtomicLong outboundBytesRaw() {
        return outboundBytesRaw;
    }

    public TimeCounter inboundSpeedBaked() {
        return inboundSpeedBaked;
    }

    public TimeCounter inboundSpeedRaw() {
        return inboundSpeedRaw;
    }

    public TimeCounter outboundSpeedBaked() {
        return outboundSpeedBaked;
    }

    public TimeCounter outboundSpeedRaw() {
        return outboundSpeedRaw;
    }
}
