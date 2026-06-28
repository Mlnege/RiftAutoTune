package com.nightfall.riftautotune.util;

import java.lang.management.ManagementFactory;

/**
 * Lightweight CPU-load sampler. {@code getCpuLoad()/getProcessCpuLoad()} are cheap but not free,
 * so the reading is cached and refreshed at most a couple of times a second. Returns 0..1, or
 * {@code -1} when the platform does not expose it (the tuning logic then falls back to the
 * frame-time spikiness heuristic for CPU/GPU-bound classification).
 */
public final class CpuLoad {

    @SuppressWarnings("removal")
    private static final com.sun.management.OperatingSystemMXBean OS = osBean();
    private static final long REFRESH_NANOS = 500_000_000L;

    private static volatile double cached = -1.0;
    private static volatile long lastNanos = 0L;

    private CpuLoad() {}

    @SuppressWarnings("removal")
    private static com.sun.management.OperatingSystemMXBean osBean() {
        try {
            java.lang.management.OperatingSystemMXBean b = ManagementFactory.getOperatingSystemMXBean();
            return (b instanceof com.sun.management.OperatingSystemMXBean sun) ? sun : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** System-wide CPU load 0..1 (falls back to this JVM's process load), or -1 if unavailable. */
    @SuppressWarnings("removal")
    public static double system() {
        if (OS == null) return -1.0;
        long now = System.nanoTime();
        if (now - lastNanos >= REFRESH_NANOS) {
            lastNanos = now;
            try {
                double v = OS.getCpuLoad();             // whole-system load (Java 14+)
                if (v < 0) v = OS.getProcessCpuLoad();  // fall back to this process
                cached = v;
            } catch (Throwable t) {
                cached = -1.0;
            }
        }
        return cached;
    }
}
