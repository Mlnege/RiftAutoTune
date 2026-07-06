package com.nightfall.riftautotune.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PackProfileResolutionTest {

    private static final Set<String> KAPPA = Set.of("Low", "Medium", "High", "Ultra", "Extreme");
    private static final Set<String> COMPLEMENTARY =
            Set.of("POTATO", "VERYLOW", "LOW", "MEDIUM", "HIGH", "VERYHIGH", "ULTRA");
    private static final Set<String> BLISS_PSEUDO = Set.of("SHADER_VERSION_LABEL", "OLD_BLISS_TONEMAP");

    @Test
    void kappaMixedCaseLadderResolvesByRank() {
        assertEquals("Low", ShaderProfilePolicy.resolvePackProfile("POTATO", KAPPA));
        assertEquals("Low", ShaderProfilePolicy.resolvePackProfile("LOW", KAPPA));
        assertEquals("Medium", ShaderProfilePolicy.resolvePackProfile("MEDIUM", KAPPA));
        assertEquals("High", ShaderProfilePolicy.resolvePackProfile("HIGH", KAPPA));
        assertEquals("Ultra", ShaderProfilePolicy.resolvePackProfile("ULTRA", KAPPA));
    }

    @Test
    void veryHighTieBreaksTowardLowerLoad() {
        // VERYHIGH(5) sits between Kappa High(4) and Ultra(6): the machine-protective rule wins.
        assertEquals("High", ShaderProfilePolicy.resolvePackProfile("VERYHIGH", KAPPA));
    }

    @Test
    void complementaryLadderResolvesToItself() {
        for (String p : COMPLEMENTARY) {
            assertEquals(p, ShaderProfilePolicy.resolvePackProfile(p, COMPLEMENTARY));
        }
    }

    @Test
    void blissPseudoProfilesAreNeverSelected() {
        assertNull(ShaderProfilePolicy.resolvePackProfile("MEDIUM", BLISS_PSEUDO),
                "tonemap/version pseudo-profiles must force the option-table fallback");
    }

    @Test
    void blissFamilyDetectionAndTableShape() {
        assertEquals(ShaderOptionTables.PackFamily.BLISS,
                ShaderOptionTables.PackFamily.detect("Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"));
        assertEquals(ShaderOptionTables.PackFamily.KAPPA,
                ShaderOptionTables.PackFamily.detect("Kappa_v5.3.zip"));
        assertEquals(ShaderOptionTables.PackFamily.COMPLEMENTARY_LIKE,
                ShaderOptionTables.PackFamily.detect("Spooklementary_v2.0.4.zip"));

        var low = ShaderOptionTables.blissOptions("VERYLOW");
        var ultra = ShaderOptionTables.blissOptions("ULTRA");
        assertEquals("false", low.get("VOLUMETRIC_CLOUDS"));
        assertEquals("true", ultra.get("VOLUMETRIC_CLOUDS"));
        assertEquals("false", low.get("SSGI"));
        assertEquals("true", ultra.get("SSGI"));
        assertTrue(Integer.parseInt(low.get("VL_SAMPLES")) < Integer.parseInt(ultra.get("VL_SAMPLES")));
        assertEquals(low.keySet(), ultra.keySet(), "every tier writes the full key set for clean downgrades");
    }
}
