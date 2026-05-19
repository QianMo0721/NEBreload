package cn.ussshenzhou.notenoughbandwidth.aggregation;

public final class AggregationFlushHelper {
    private AggregationFlushHelper() {
    }

    public static int getFlushPeriodInMilliseconds() {
        return 20;
    }

    public static long getMaxBatchWaitNanos() {
        return java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(getFlushPeriodInMilliseconds());
    }
}
