package cn.ussshenzhou.notenoughbandwidth.util;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * @author USS_Shenzhou
 */
public class TimeCounter {
    private final Long2IntOpenHashMap container = new Long2IntOpenHashMap();
    private final int windowsSizeMs;

    public TimeCounter(int windowsSizeMs) {
        this.windowsSizeMs = windowsSizeMs;
    }

    public TimeCounter() {
        this(2000);
    }

    private synchronized void update() {
        final long now = System.currentTimeMillis();
        container.keySet().removeIf(then -> now - then > windowsSizeMs);
    }

    public synchronized void put(int value) {
        update();
        container.put(System.currentTimeMillis(), value);
    }

    public synchronized double averageIn1s() {
        int sum = 0;
        for (int value : container.values()) {
            sum += value;
        }
        return sum / (double) windowsSizeMs * 1000.0D;
    }
}
