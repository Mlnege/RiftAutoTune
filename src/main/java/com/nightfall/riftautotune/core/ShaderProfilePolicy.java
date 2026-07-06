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

    /**
     * Case-insensitive quality rank for a profile NAME as found in a shaderpack's
     * shaders.properties. Returns -1 for names that are not quality tiers at all -
     * e.g. Bliss ships pseudo-profiles like {@code SHADER_VERSION_LABEL} / {@code
     * OLD_BLISS_TONEMAP} (tonemap variants) which must never be selected as a quality level.
     */
    public static int qualityRank(String name) {
        if (name == null) return -1;
        return switch (name.toLowerCase().replace("_", "")) {
            case "potato" -> 0;
            case "verylow" -> 1;
            case "low", "lite" -> 2;
            case "medium", "med", "normal", "default", "balanced" -> 3;
            case "high" -> 4;
            case "veryhigh", "ultrahigh" -> 5;
            case "ultra" -> 6;
            case "extreme", "max", "maximum" -> 7;
            default -> -1;
        };
    }

    /**
     * Resolve the canonical profile onto whatever quality ladder the ACTIVE pack exposes,
     * matching by rank instead of exact name (Kappa uses {@code Low..Extreme} mixed-case;
     * Complementary-family uses {@code POTATO..ULTRA}). Ties break toward the LOWER rank -
     * the pack's standing rule is that tuning must never overload the machine.
     *
     * @return the pack's own profile name, or {@code null} when the pack exposes no usable
     *         quality profiles (then the caller should fall back to direct option writes)
     */
    public static String resolvePackProfile(String canonical, Set<String> packProfiles) {
        if (packProfiles == null || packProfiles.isEmpty()) return null;
        int target = qualityRank(canonical);
        if (target < 0) target = qualityRank(MEDIUM);
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        int bestRank = Integer.MAX_VALUE;
        for (String candidate : packProfiles) {
            int rank = qualityRank(candidate);
            if (rank < 0) continue; // pseudo-profile (tonemap/version label) - never select
            int dist = Math.abs(rank - target);
            if (dist < bestDist || (dist == bestDist && rank < bestRank)) {
                best = candidate;
                bestDist = dist;
                bestRank = rank;
            }
        }
        return best;
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
