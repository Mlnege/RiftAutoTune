package com.nightfall.riftautotune.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTuneOptimizerTest {

    @Test
    @DisplayName("Holds the FPS floor on a 144Hz display")
    void holdsBandOnHighRefresh() {
        TuningContext ctx = TestData.ctx(TestData.hw(8192, 12, 32768, 2560, 1440, 144),
                TestData.bench(HardwareTier.HIGH, 85, 68), 60, 100, true, true);
        GraphicsSettings s = AutoTuneOptimizer.optimize(ctx);
        double fps = new CostModel(ctx).predictFps(s);
        assertTrue(fps >= ctx.effectiveLow - 0.6,
                "predicted fps " + fps + " should be at/above floor " + ctx.effectiveLow);
    }

    @Test
    @DisplayName("Clamps the band to the monitor refresh rate")
    void clampsBandToRefresh() {
        TuningContext ctx = TestData.ctx(TestData.hw(6144, 6, 16384, 1920, 1080, 60),
                TestData.bench(HardwareTier.MEDIUM, 75, 60), 60, 100, true, true);
        assertEquals(60, ctx.effectiveHigh, "high must clamp to 60Hz panel");
        GraphicsSettings s = AutoTuneOptimizer.optimize(ctx);
        double fps = new CostModel(ctx).predictFps(s);
        assertTrue(fps >= ctx.effectiveLow - 0.6, "fps " + fps + " below floor");
    }

    @Test
    @DisplayName("Downgrades to recover the band when the machine is too slow")
    void downgradesWhenTooSlow() {
        TuningContext ctx = TestData.ctx(TestData.hw(6144, 6, 16384, 1920, 1080, 144),
                TestData.bench(HardwareTier.MEDIUM, 40, 30), 60, 100, true, true);
        GraphicsSettings s = AutoTuneOptimizer.optimize(ctx);
        CostModel cm = new CostModel(ctx);
        double fpsTuned = cm.predictFps(s);
        double fpsPreset = cm.predictFps(QualityLadder.presetFor(HardwareTier.MEDIUM));
        assertTrue(fpsTuned > fpsPreset, "tuned fps should exceed the slow preset fps");
        assertTrue(fpsTuned >= ctx.effectiveLow - 0.6, "tuned fps " + fpsTuned + " below floor");
    }

    @Test
    @DisplayName("Never enables shaders when Oculus is absent")
    void respectsShaderAvailability() {
        TuningContext ctx = TestData.ctx(TestData.hw(8192, 12, 16384, 1920, 1080, 144),
                TestData.bench(HardwareTier.HIGH, 85, 68), 60, 100, false, true);
        GraphicsSettings s = AutoTuneOptimizer.optimize(ctx);
        assertEquals(0, s.get(Knob.SHADERS), "shaders must stay off when unavailable");
    }

    @Test
    @DisplayName("Never enables Distant Horizons when it is absent")
    void respectsDhAvailability() {
        TuningContext ctx = TestData.ctx(TestData.hw(8192, 12, 16384, 1920, 1080, 144),
                TestData.bench(HardwareTier.HIGH, 85, 68), 60, 100, true, false);
        GraphicsSettings s = AutoTuneOptimizer.optimize(ctx);
        assertEquals(0, s.get(Knob.DH_LOD_DISTANCE), "DH must stay off when unavailable");
    }

    @Test
    @DisplayName("Terminates and returns settings even in an impossible band")
    void terminatesOnImpossibleBand() {
        // Ask for 200+ FPS on a machine averaging 30 at preset: infeasible, must still return.
        TuningContext ctx = TestData.ctx(TestData.hw(2048, 4, 8192, 1920, 1080, 240),
                TestData.bench(HardwareTier.LOW, 30, 22), 200, 240, false, false);
        GraphicsSettings s = AutoTuneOptimizer.optimize(ctx);
        assertTrue(s != null, "optimizer must always return a settings object");
    }
}
