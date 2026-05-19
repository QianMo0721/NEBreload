package cn.ussshenzhou.notenoughbandwidth.util;

import java.util.concurrent.atomic.AtomicLong;

public class TimeCounter {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int SLOT_COUNT = 20;
    private static final long SLOT_NANOS = WINDOW_NANOS / SLOT_COUNT;

    private final AtomicLong[] values = new AtomicLong[SLOT_COUNT];
    private final AtomicLong[] ticks = new AtomicLong[SLOT_COUNT];

    public TimeCounter() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            values[i] = new AtomicLong();
            ticks[i] = new AtomicLong(Long.MIN_VALUE);
        }
    }

    public void put(int size) {
        long now = System.nanoTime();
        long tick = now / SLOT_NANOS;
        int slot = (int) (tick % SLOT_COUNT);
        AtomicLong tickRef = ticks[slot];
        long previous = tickRef.get();
        if (previous != tick) {
            if (tickRef.compareAndSet(previous, tick)) {
                values[slot].set(size);
                return;
            }
            previous = tickRef.get();
            if (previous != tick) {
                values[slot].set(size);
                tickRef.set(tick);
                return;
            }
        }
        values[slot].addAndGet(size);
    }

    public double averageIn1s() {
        long nowTick = System.nanoTime() / SLOT_NANOS;
        long sum = 0L;
        for (int i = 0; i < SLOT_COUNT; i++) {
            long slotTick = ticks[i].get();
            if (nowTick - slotTick < SLOT_COUNT) {
                sum += values[i].get();
            }
        }
        return sum;
    }
}
