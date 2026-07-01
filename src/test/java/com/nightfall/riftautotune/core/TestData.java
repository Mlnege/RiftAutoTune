package com.nightfall.riftautotune.core;

/** Shared builders for the core unit tests. */
final class TestData {

    private TestData() {}

    static HardwareProfile hw(int vramMb, int threads, int ramMb, int w, int h, int refresh) {
        return new HardwareProfile("Vendor", "Renderer", "3.2", vramMb, threads, ramMb,
                4096, "test-os", w, h, refresh, 2);
    }

    /** Benchmark whose reference is the tier preset, with a given measured average FPS. */
    static BenchmarkResult bench(HardwareTier tier, double avgFps, double onePctLow) {
        return new BenchmarkResult(avgFps, onePctLow, onePctLow * 0.8, 0.9, true,
                QualityLadder.presetFor(tier), null);
    }

    /** Benchmark measured at the bottom-up potato baseline (how first-run tuning now measures). */
    static BenchmarkResult potatoBench(boolean shaders, double avgFps, double onePctLow) {
        return new BenchmarkResult(avgFps, onePctLow, onePctLow * 0.8, 0.9, true,
                QualityLadder.potatoBaseline(shaders), null);
    }

    static TuningContext ctx(HardwareProfile hw, BenchmarkResult b, int low, int high,
                             boolean shaders, boolean dh) {
        return new TuningContext(hw, b, low, high, 50, 0.5, shaders, dh, false);
    }
}
