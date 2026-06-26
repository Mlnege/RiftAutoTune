package com.nightfall.riftautotune.client;

/**
 * Lock-light rolling frame-time recorder. {@link #tick()} is called once per rendered frame on
 * the render thread; everything is plain arithmetic so it never stalls the frame.
 */
public final class FrameTimeMonitor {

    private long lastNanos = 0L;
    private final double[] ring;
    private int idx = 0;
    private int filled = 0;

    public FrameTimeMonitor(int capacity) {
        this.ring = new double[Math.max(8, capacity)];
    }

    /** Record one frame. Returns the frame time in ms, or -1 for the very first (no delta yet). */
    public double tick() {
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
            return -1;
        }
        double ms = (now - lastNanos) / 1_000_000.0;
        lastNanos = now;
        // ignore absurd spikes (alt-tab, GC stop-the-world, loading) so they don't poison stats
        if (ms > 0 && ms < 2000.0) {
            ring[idx] = ms;
            idx = (idx + 1) % ring.length;
            if (filled < ring.length) filled++;
        }
        return ms;
    }

    public void reset() {
        lastNanos = 0L;
        idx = 0;
        filled = 0;
    }

    public int sampleCount() {
        return filled;
    }

    /** Snapshot of the recorded frame times (oldest..newest ordering not guaranteed). */
    public double[] snapshot() {
        double[] out = new double[filled];
        System.arraycopy(ring, 0, out, 0, filled);
        return out;
    }

    /** Mean frame time (ms) over the current window, or 0 if empty. */
    public double meanMs() {
        if (filled == 0) return 0;
        double sum = 0;
        for (int i = 0; i < filled; i++) sum += ring[i];
        return sum / filled;
    }

    /** Coefficient of variation of frame time; low == steady (used for steady-state detection). */
    public double coefficientOfVariation() {
        if (filled < 2) return 1.0;
        double mean = meanMs();
        if (mean <= 0) return 1.0;
        double var = 0;
        for (int i = 0; i < filled; i++) {
            double d = ring[i] - mean;
            var += d * d;
        }
        return Math.sqrt(var / filled) / mean;
    }
}
