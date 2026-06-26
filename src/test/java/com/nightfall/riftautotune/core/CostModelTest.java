package com.nightfall.riftautotune.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostModelTest {

    private CostModel model(boolean shaders, boolean dh) {
        TuningContext ctx = TestData.ctx(TestData.hw(8192, 12, 32768, 2560, 1440, 144),
                TestData.bench(HardwareTier.HIGH, 85, 68), 60, 100, shaders, dh);
        return new CostModel(ctx);
    }

    @Test
    @DisplayName("Predicts the measured average at the reference settings")
    void predictsReferenceFps() {
        CostModel cm = model(true, true);
        double fps = cm.predictFps(QualityLadder.presetFor(HardwareTier.HIGH));
        assertEquals(85.0, fps, 0.5);
    }

    @Test
    @DisplayName("Higher settings predict lower FPS")
    void higherSettingsLowerFps() {
        CostModel cm = model(true, true);
        double low = cm.predictFps(QualityLadder.presetFor(HardwareTier.LOW));
        double ultra = cm.predictFps(QualityLadder.presetFor(HardwareTier.ULTRA));
        assertTrue(low > ultra, "LOW preset (" + low + ") should beat ULTRA (" + ultra + ")");
    }

    @Test
    @DisplayName("Shader sub-knobs are inactive when the shader master is off")
    void inactiveKnobsHaveNoEffect() {
        CostModel cm = model(true, true);
        GraphicsSettings off = QualityLadder.presetFor(HardwareTier.HIGH);
        off.set(Knob.SHADERS, 0);
        double before = cm.predictFrameTimeMs(off);
        off.set(Knob.SHADER_SHADOW_RES, off.get(Knob.SHADER_SHADOW_RES) == 0 ? 1 : 0);
        double after = cm.predictFrameTimeMs(off);
        assertEquals(before, after, 1e-9, "changing a shadow knob with shaders off must not change cost");
        assertFalse(cm.isActive(Knob.SHADER_SHADOW_RES, off));
    }

    @Test
    @DisplayName("Master toggles gate feature activity correctly")
    void mastersGateActivity() {
        CostModel withDh = model(true, true);
        GraphicsSettings s = QualityLadder.presetFor(HardwareTier.HIGH);
        assertTrue(withDh.isActive(Knob.DH_CPU_LOAD, s), "DH sub-knob active when DH on & available");

        CostModel noDh = model(true, false);
        assertFalse(noDh.isActive(Knob.DH_LOD_DISTANCE, s), "DH master inactive when DH unavailable");
        assertFalse(noDh.isActive(Knob.DH_CPU_LOAD, s), "DH sub-knob inactive when DH unavailable");
    }
}
