package cn.ussshenzhou.notenoughbandwidth.aggregation;

/**
 * @author USS_Shenzhou
 */
public class AggregationFlushHelper {
    public static int getFlushPeriodInMilliseconds() {
        return 20;
    }

    public static long getMaxBatchWaitNanos() {
        return java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(getFlushPeriodInMilliseconds());
    }

    @Deprecated(forRemoval = true)
    public static int getFlushCountInSeconds() {
        return Math.max(1000 / AggregationFlushHelper.getFlushPeriodInMilliseconds(), 1);
    }

    @Deprecated(forRemoval = true)
    public static int getThresholdCount1s() {
        return 20 * 2;
    }
}
