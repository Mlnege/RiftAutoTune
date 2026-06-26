package com.nightfall.riftautotune.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkResultTest {

    @Test
    @DisplayName("Average and 1% low are computed from frame times")
    void percentiles() {
        double[] frames = new double[100];
        for (int i = 0; i < 100; i++) frames[i] = 10.0; // 100 FPS
        frames[99] = 50.0;                               // one ugly 20 FPS frame
        BenchmarkResult r = BenchmarkResult.fromFrameTimes(frames, new GraphicsSettings(), true, null);

        assertEquals(1000.0 / 10.4, r.avgFps, 2.0, "avg ~ mean frame time");
        assertEquals(20.0, r.onePctLowFps, 0.6, "1% low reflects the single worst frame");
        assertTrue(r.pointOnePctLowFps <= r.onePctLowFps + 0.01, "0.1% low <= 1% low");
    }

    @Test
    @DisplayName("onePctRatio is bounded and sensible")
    void ratio() {
        BenchmarkResult r = new BenchmarkResult(100, 80, 70, 0.9, true, new GraphicsSettings(), null);
        assertEquals(0.8, r.onePctRatio(), 1e-9);
    }

    @Test
    @DisplayName("Empty frame data degrades gracefully")
    void emptyFrames() {
        BenchmarkResult r = BenchmarkResult.fromFrameTimes(new double[0], new GraphicsSettings(), true, null);
        assertEquals(0.0, r.avgFps, 1e-9);
    }
}
