package com.nightfall.riftautotune.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareProfileTest {

    @Test
    @DisplayName("GTX 1060 6GB @1080p classifies as MEDIUM (the MEDIUM-HIGH boundary)")
    void gtx1060IsMedium() {
        assertEquals(HardwareTier.MEDIUM, TestData.hw(6144, 6, 16384, 1920, 1080, 60).tier);
    }

    @Test
    @DisplayName("Weak hardware classifies as LOW")
    void weakIsLow() {
        assertEquals(HardwareTier.LOW, TestData.hw(2048, 4, 8192, 1920, 1080, 60).tier);
    }

    @Test
    @DisplayName("High-end hardware @1080p classifies as ULTRA")
    void strongIsUltra() {
        assertEquals(HardwareTier.ULTRA, TestData.hw(12288, 16, 32768, 1920, 1080, 60).tier);
    }

    @Test
    @DisplayName("4K resolution penalises the tier")
    void resolutionPenalty() {
        HardwareTier at1080 = TestData.hw(8192, 12, 16384, 1920, 1080, 60).tier;
        HardwareTier at4k = TestData.hw(8192, 12, 16384, 3840, 2160, 60).tier;
        assertTrue(at4k.ordinal() < at1080.ordinal(), "4K should be a lower tier than 1080p");
    }

    @Test
    @DisplayName("Fingerprint is stable for identical hardware and changes with resolution")
    void fingerprintBehaviour() {
        String a = TestData.hw(8192, 12, 16384, 1920, 1080, 144).fingerprint();
        String b = TestData.hw(8192, 12, 16384, 1920, 1080, 144).fingerprint();
        String c = TestData.hw(8192, 12, 16384, 2560, 1440, 144).fingerprint();
        assertEquals(a, b, "same hardware => same fingerprint");
        assertNotEquals(a, c, "different resolution => different fingerprint");
    }

    @Test
    @DisplayName("16GB+ GPU with many cores classifies as EXTREME (finer top tier)")
    void beastIsExtreme() {
        assertEquals(HardwareTier.EXTREME, TestData.hw(16384, 16, 32768, 1920, 1080, 60).tier);
    }

    @Test
    @DisplayName("Integrated GPU at 4K classifies as MINIMUM (finer bottom tier)")
    void integratedAt4kIsMinimum() {
        // VRAM unknown -> modest heuristic, few cores, then the 4K penalty drags it below LOW.
        assertEquals(HardwareTier.MINIMUM, TestData.hw(-1, 4, 8192, 3840, 2160, 60).tier);
    }
}
