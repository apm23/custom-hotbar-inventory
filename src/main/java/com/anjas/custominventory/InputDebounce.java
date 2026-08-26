package com.anjas.custominventory;

/** Monotonic-time debounce guard used to collapse switch bounce into one action. */
public final class InputDebounce {
    private final long intervalNanos;
    private long lastAccepted = Long.MIN_VALUE;

    public InputDebounce(long intervalNanos) {
        if (intervalNanos < 0) throw new IllegalArgumentException("intervalNanos=" + intervalNanos);
        this.intervalNanos = intervalNanos;
    }

    public boolean tryAcquire(long nowNanos) {
        if (lastAccepted == Long.MIN_VALUE || nowNanos - lastAccepted >= intervalNanos) {
            lastAccepted = nowNanos;
            return true;
        }
        return false;
    }

    public void reset() {
        lastAccepted = Long.MIN_VALUE;
    }
}
