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

    @Test
    @DisplayName("Bottom-up: strong hardware measured at potato buys substantial detail")
    void potatoBaselineUpgradesOnStrongHardware() {
        // A 300-FPS potato measurement means huge headroom: the optimizer must ADD detail well
        // beyond the baseline, not leave the player staring at potato graphics.
        TuningContext ctx = TestData.ctx(TestData.hw(16384, 16, 65536, 2560, 1440, 144),
                TestData.potatoBench(true, 300, 220), 60, 100, true, true);
        GraphicsSettings baseline = QualityLadder.potatoBaseline(true);
        GraphicsSettings tuned = AutoTuneOptimizer.optimize(ctx);
        CostModel cm = new CostModel(ctx);
        assertTrue(cm.visualScore(tuned) > cm.visualScore(baseline),
                "strong hardware must gain visual detail over the potato baseline; got " + tuned);
        assertTrue(cm.predictFps(tuned) >= ctx.effectiveLow - 0.6,
                "upgraded settings must still hold the floor");
        assertEquals(1, tuned.get(Knob.SHADERS), "shaderpack must stay enabled");
    }

    @Test
    @DisplayName("Bottom-up: weak hardware at the floor keeps the band and buys no expensive detail")
    void potatoBaselineHoldsOnWeakHardware() {
        // Potato measurement sits exactly at both floors: only negligible cosmetic upgrades can
        // fit; anything expensive (shader sub-knobs, DH) would break the floor and must stay off.
        TuningContext ctx = TestData.ctx(TestData.hw(2048, 4, 8192, 1920, 1080, 60),
                TestData.potatoBench(true, 60.5, 50.5), 60, 100, true, true);
        GraphicsSettings tuned = AutoTuneOptimizer.optimize(ctx);
        CostModel cm = new CostModel(ctx);
        assertTrue(cm.predictFps(tuned) >= ctx.effectiveLow - 0.6,
                "tuned fps " + cm.predictFps(tuned) + " must hold the floor " + ctx.effectiveLow);
        assertEquals(0, tuned.get(Knob.SHADER_SHADOW_RES), "no headroom -> shadow res stays at floor");
        assertEquals(0, tuned.get(Knob.SHADER_VOLUMETRIC), "no headroom -> volumetrics stay at floor");
        assertEquals(0, tuned.get(Knob.DH_LOD_DISTANCE), "no headroom -> DH stays off");
    }

    @Test
    @DisplayName("Bottom-up: the potato baseline keeps the shaderpack on at its floor profile")
    void potatoBaselineKeepsShadersOn() {
        GraphicsSettings baseline = QualityLadder.potatoBaseline(true);
        assertEquals(1, baseline.get(Knob.SHADERS), "baseline keeps the pack enabled");
        assertEquals(0, baseline.get(Knob.SHADER_SHADOW_RES), "shader sub-knobs start at the floor");
        assertEquals(0, QualityLadder.potatoBaseline(false).get(Knob.SHADERS),
                "no Oculus -> baseline has shaders off");
    }

    @Test
    @DisplayName("Heavy shader at sub-band FPS sheds shader quality even on strong hardware")
    void heavyShaderShedsOnStrongHardware() {
        // Strong GPU (EXTREME seed) but the heavy pack measured only 55 avg / 40 1%-low with shaders
        // maxed. The cost model is anchored on that real measurement, so the optimizer must pull
        // shader quality DOWN to recover the band - not keep it maxed because "the GPU is fast".
        GraphicsSettings maxed = QualityLadder.presetFor(HardwareTier.EXTREME);
        BenchmarkResult heavy = new BenchmarkResult(55, 40, 33, 0.85, true, maxed, null);
        TuningContext ctx = new TuningContext(TestData.hw(16384, 16, 32768, 2560, 1440, 144),
                heavy, 60, 100, 50, 0.5, true, false, false); // DH off, like the reported profile
        GraphicsSettings tuned = AutoTuneOptimizer.optimize(ctx);
        CostModel cm = new CostModel(ctx);
        assertTrue(cm.predictFps(tuned) >= ctx.effectiveLow - 0.6,
                "tuned fps " + cm.predictFps(tuned) + " must reach the band floor " + ctx.effectiveLow);
        assertTrue(tuned.get(Knob.SHADER_SHADOW_RES) < maxed.get(Knob.SHADER_SHADOW_RES)
                        || tuned.get(Knob.SHADERS) == 0,
                "expected shadow-map res lowered (or shaders off); got " + tuned);
    }
}
