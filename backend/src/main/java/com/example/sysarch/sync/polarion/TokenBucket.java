package com.example.sysarch.sync.polarion;

/**
 * Rate limit for calls to Polarion (spec §6.3): at most {@code ratePerSecond} calls per second, with a burst of the
 * same size. {@link #acquire()} blocks until a permit is free. Thread-safe.
 */
public final class TokenBucket {

    /** Time source and sleep, replaceable in tests. */
    interface Clock {
        long nanoTime();

        void sleepNanos(long nanos) throws InterruptedException;
    }

    private static final Clock SYSTEM = new Clock() {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public void sleepNanos(long nanos) throws InterruptedException {
            Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000));
        }
    };

    private final double ratePerNano;

    private final double capacity;

    private final Clock clock;

    private double tokens;

    private long lastRefill;

    public TokenBucket(double ratePerSecond) {
        this(ratePerSecond, SYSTEM);
    }

    TokenBucket(double ratePerSecond, Clock clock) {
        if (ratePerSecond <= 0) {
            throw new IllegalArgumentException("rate must be positive: " + ratePerSecond);
        }
        this.ratePerNano = ratePerSecond / 1_000_000_000.0;
        this.capacity = Math.max(1, ratePerSecond);
        this.clock = clock;
        this.tokens = this.capacity;
        this.lastRefill = clock.nanoTime();
    }

    /** Takes one permit, waiting as long as needed. */
    public void acquire() throws InterruptedException {
        while (true) {
            long waitNanos;
            synchronized (this) {
                this.refill();
                if (this.tokens >= 1) {
                    this.tokens -= 1;
                    return;
                }
                waitNanos = (long) Math.ceil((1 - this.tokens) / this.ratePerNano);
            }
            this.clock.sleepNanos(waitNanos);
        }
    }

    private void refill() {
        long now = this.clock.nanoTime();
        this.tokens = Math.min(this.capacity, this.tokens + (now - this.lastRefill) * this.ratePerNano);
        this.lastRefill = now;
    }
}
