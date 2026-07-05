package com.nightfall.riftautotune.core;

/**
 * Pure sizing policy for C2ME's {@code globalExecutorParallelism} (no Minecraft imports).
 *
 * <p>C2ME reads its config once at bootstrap, so this is a <b>next-launch</b> tuning: the value is
 * derived from the measured hardware tier rather than live FPS. The goal is the same as the DH
 * guard's: chunk generation must never starve the render thread (or an integrated server) of CPU.
 * C2ME's own default is aggressive (roughly {@code cpus / 1.6 - 2} on Windows), which on weak or
 * heavily-modded machines is exactly the "world loads fast but the game stutters" trade we don't
 * want.</p>
 */
public final class C2meTuningPolicy {

    private C2meTuningPolicy() {}

    /**
     * @param tier      measured hardware tier
     * @param cores     available processors (>= 1)
     * @param cpuBound  true when the last benchmark classified the run as CPU-bound
     * @param maxCap    user cap from config; {@code 0} = auto (cores - 2)
     * @return worker thread count for C2ME's global executor, always >= 1
     */
    public static int parallelismFor(HardwareTier tier, int cores, boolean cpuBound, int maxCap) {
        // Deliberately BELOW C2ME's own default (~cpus/1.6-2): the user's hard requirement is that
        // chunk gen must never make the machine struggle, even at the cost of slower world loading.
        int c = Math.max(1, cores);
        int base = switch (tier) {
            case MINIMUM -> 1;
            case LOW -> Math.max(1, c / 6);
            case MEDIUM -> Math.max(1, c / 4);
            case HIGH -> Math.max(2, c / 3);
            case VERY_HIGH, ULTRA, EXTREME -> Math.max(2, c / 2);
        };
        if (cpuBound) base = Math.max(1, base - 1);
        // Always leave headroom for the render thread + integrated server tick thread.
        int autoCap = Math.max(1, c - 2);
        int cap = maxCap > 0 ? Math.min(maxCap, autoCap) : autoCap;
        return Math.max(1, Math.min(base, cap));
    }
}
