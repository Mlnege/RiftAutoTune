package com.nightfall.riftautotune.core;

/**
 * Everything the {@link CostModel} and {@link AutoTuneOptimizer} need, bundled and decoupled
 * from Minecraft. Also computes the <em>display-aware</em> FPS band: the requested band is
 * clamped so we never target above the monitor's refresh rate (pointless), and when VSync is on
 * the refresh rate becomes the effective ceiling.
 */
public final class TuningContext {

    public final HardwareProfile hardware;
    public final BenchmarkResult benchmark;
    public final double qualityBias;        // 0 = favour performance, 1 = favour quality
    public final boolean shadersAvailable;
    public final boolean dhAvailable;
    public final boolean vsync;
    /** Heap-budget knob ceilings; never null (defaults to uncapped). */
    public final MemoryBudgetPolicy.BudgetCaps budgetCaps;

    public final int effectiveLow;
    public final int effectiveHigh;
    public final int onePctFloor;

    /** Legacy form without budget caps: no heap-based knob ceilings (uncapped). */
    public TuningContext(HardwareProfile hardware, BenchmarkResult benchmark,
                         int requestedLow, int requestedHigh, int onePctFloor,
                         double qualityBias, boolean shadersAvailable, boolean dhAvailable,
                         boolean vsync) {
        this(hardware, benchmark, requestedLow, requestedHigh, onePctFloor, qualityBias,
                shadersAvailable, dhAvailable, vsync, MemoryBudgetPolicy.uncapped());
    }

    public TuningContext(HardwareProfile hardware, BenchmarkResult benchmark,
                         int requestedLow, int requestedHigh, int onePctFloor,
                         double qualityBias, boolean shadersAvailable, boolean dhAvailable,
                         boolean vsync, MemoryBudgetPolicy.BudgetCaps budgetCaps) {
        this.hardware = hardware;
        this.benchmark = benchmark;
        this.qualityBias = clamp01(qualityBias);
        this.shadersAvailable = shadersAvailable;
        this.dhAvailable = dhAvailable;
        this.vsync = vsync;
        this.budgetCaps = budgetCaps != null ? budgetCaps : MemoryBudgetPolicy.uncapped();

        int refresh = hardware != null ? hardware.refreshRate : 60;

        // Never aim above the panel refresh rate: targeting beyond it is pointless, and with
        // VSync on it is a hard cap anyway. The 1%-low floor is what keeps a VSync-capped panel
        // smooth, and that is enforced separately via onePctFloor.
        int high = Math.min(requestedHigh, refresh);
        int low = Math.min(requestedLow, high - 5);
        if (low < 20) low = 20;
        if (high <= low) high = low + 10;

        this.effectiveLow = low;
        this.effectiveHigh = high;
        // Don't demand a 1%-low floor above the band's own low.
        this.onePctFloor = Math.min(onePctFloor, low);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
