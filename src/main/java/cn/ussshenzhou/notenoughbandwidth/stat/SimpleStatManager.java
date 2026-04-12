package cn.ussshenzhou.notenoughbandwidth.stat;

/**
 * @author USS_Shenzhou
 */
public class SimpleStatManager {
    public static final SimpleStatData LOCAL = new SimpleStatData();

    public static String getReadableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public static String getReadableSpeed(int bytesPerSecond) {
        return getReadableSize(bytesPerSecond) + "/s";
    }

    public static void inBaked(int size) {
        LOCAL.inboundBytesBaked().addAndGet(size);
        LOCAL.inboundSpeedBaked().put(size);
    }

    public static void inRaw(int size) {
        LOCAL.inboundBytesRaw().addAndGet(size);
        LOCAL.inboundSpeedRaw().put(size);
    }

    public static void outBaked(int size) {
        LOCAL.outboundBytesBaked().addAndGet(size);
        LOCAL.outboundSpeedBaked().put(size);
    }

    public static void outRaw(int size) {
        LOCAL.outboundBytesRaw().addAndGet(size);
        LOCAL.outboundSpeedRaw().put(size);
    }

    public static long inboundBytesBakedServer;
    public static long inboundBytesRawServer;
    public static long outboundBytesBakedServer;
    public static long outboundBytesRawServer;
    public static double inboundSpeedBakedServer;
    public static double inboundSpeedRawServer;
    public static double outboundSpeedBakedServer;
    public static double outboundSpeedRawServer;
}
