package com.nightfall.riftautotune.core;

import java.util.Arrays;
import java.util.Set;

/**
 * Maps RiftAutoTune's generic shader knobs to shaderpack profile names.
 *
 * <p>Spooklementary exposes Complementary-style profiles in {@code shaders.properties}
 * ({@code POTATO..ULTRA}). Keeping this mapping in the pure core lets tests pin the important
 * policy: PBR is only enabled from {@code VERYHIGH} upward.</p>
 */
public final class ShaderProfilePolicy {

    public static final String POTATO = "POTATO";
    public static final String VERYLOW = "VERYLOW";
    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";
    public static final String VERYHIGH = "VERYHIGH";
    public static final String ULTRA = "ULTRA";

    private static final String[] ORDER = {
            POTATO, VERYLOW, LOW, MEDIUM, HIGH, VERYHIGH, ULTRA
    };

    private ShaderProfilePolicy() {}

    public static String profileFor(GraphicsSettings settings) {
        if (settings.get(Knob.SHADERS) <= 0) {
            return POTATO;
        }

        int shadowRes = settings.get(Knob.SHADER_SHADOW_RES);
        int shadowDist = settings.get(Knob.SHADER_SHADOW_DIST);
        int volumetric = settings.get(Knob.SHADER_VOLUMETRIC);
        int ssao = settings.get(Knob.SHADER_SSAO);

        if (shadowRes >= 3 && shadowDist >= 3 && volumetric >= 3) {
            return ULTRA;
        }
        if (shadowRes >= 2 && shadowDist >= 2 && volumetric >= 2 && ssao >= 2) {
            return VERYHIGH;
        }
        if (shadowRes >= 2 && shadowDist >= 2 && volumetric >= 2) {
            return HIGH;
        }
        if (shadowRes >= 1 && shadowDist >= 1 && volumetric >= 1) {
            return MEDIUM;
        }
        if (shadowRes >= 1 || shadowDist >= 1 || volumetric >= 1) {
            return LOW;
        }
        return VERYLOW;
    }

    public static boolean enablesPbr(String profile) {
        return rank(profile) >= rank(VERYHIGH);
    }

    public static String pbrModeFor(String profile) {
        return enablesPbr(profile) ? "1" : "0";
    }

    public static String nearestAvailable(String requested, Set<String> available) {
        if (available == null || available.isEmpty() || available.contains(requested)) {
            return requested;
        }

        int requestedRank = rank(requested);
        for (int down = requestedRank; down >= 0; down--) {
            if (available.contains(ORDER[down])) return ORDER[down];
        }
        for (int up = requestedRank + 1; up < ORDER.length; up++) {
            if (available.contains(ORDER[up])) return ORDER[up];
        }
        return requested;
    }

    private static int rank(String profile) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(profile)) return i;
        }
        return Arrays.asList(ORDER).indexOf(MEDIUM);
    }
}
