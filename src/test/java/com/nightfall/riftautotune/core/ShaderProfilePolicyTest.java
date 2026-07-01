package com.nightfall.riftautotune.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderProfilePolicyTest {

    @Test
    @DisplayName("Maps quality ladder shader presets to Spooklementary profile names")
    void mapsQualityLadderProfiles() {
        assertEquals("POTATO", ShaderProfilePolicy.profileFor(QualityLadder.presetFor(HardwareTier.LOW)));
        assertEquals("MEDIUM", ShaderProfilePolicy.profileFor(QualityLadder.presetFor(HardwareTier.MEDIUM)));
        assertEquals("HIGH", ShaderProfilePolicy.profileFor(QualityLadder.presetFor(HardwareTier.HIGH)));
        assertEquals("VERYHIGH", ShaderProfilePolicy.profileFor(QualityLadder.presetFor(HardwareTier.VERY_HIGH)));
        assertEquals("ULTRA", ShaderProfilePolicy.profileFor(QualityLadder.presetFor(HardwareTier.ULTRA)));
    }

    @Test
    @DisplayName("PBR is gated to VERYHIGH and above")
    void gatesPbrToVeryHighAndAbove() {
        assertFalse(ShaderProfilePolicy.enablesPbr("HIGH"));
        assertEquals("0", ShaderProfilePolicy.pbrModeFor("HIGH"));
        assertTrue(ShaderProfilePolicy.enablesPbr("VERYHIGH"));
        assertTrue(ShaderProfilePolicy.enablesPbr("ULTRA"));
        assertEquals("1", ShaderProfilePolicy.pbrModeFor("VERYHIGH"));
    }

    @Test
    @DisplayName("Falls back to the nearest lower profile exposed by the shaderpack")
    void nearestAvailableFallsBackDown() {
        Set<String> available = Set.of("LOW", "MEDIUM", "HIGH");
        assertEquals("HIGH", ShaderProfilePolicy.nearestAvailable("VERYHIGH", available));
        assertEquals("MEDIUM", ShaderProfilePolicy.nearestAvailable("MEDIUM", available));
    }
}
