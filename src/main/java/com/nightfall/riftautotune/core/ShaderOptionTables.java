package com.nightfall.riftautotune.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Direct option tables for shaderpacks that expose NO quality profiles.
 *
 * <p>Bliss (Chocapic13 edit) is the motivating case: its only {@code profile.*} entries are
 * tonemap variants, so quality must be driven through individual options. Key names below were
 * extracted from Bliss v2.1.2's shaders.properties (sliders/screens), not guessed. An option
 * whose value string does not match the pack's declared type is simply ignored by Iris/Oculus -
 * worst case is a no-op, never a broken pack.</p>
 */
public final class ShaderOptionTables {

    /** Rough family of the active shaderpack, detected from its file name. */
    public enum PackFamily {
        COMPLEMENTARY_LIKE, KAPPA, BLISS, UNKNOWN;

        public static PackFamily detect(String packFileName) {
            if (packFileName == null) return UNKNOWN;
            String n = packFileName.toLowerCase(Locale.ROOT);
            if (n.contains("bliss")) return BLISS;
            if (n.contains("kappa")) return KAPPA;
            if (n.contains("complementary") || n.contains("spooklementary") || n.contains("reimagined")) {
                return COMPLEMENTARY_LIKE;
            }
            return UNKNOWN;
        }
    }

    private ShaderOptionTables() {}

    /**
     * Bliss option set for a canonical profile (POTATO..ULTRA). Every tier writes the FULL key
     * set so tier downgrades cleanly overwrite the previous tier's values.
     */
    public static Map<String, String> blissOptions(String canonicalProfile) {
        int rank = Math.max(0, ShaderProfilePolicy.qualityRank(canonicalProfile));
        Map<String, String> m = new LinkedHashMap<>();
        // rank: 0 POTATO, 1 VERYLOW, 2 LOW, 3 MEDIUM, 4 HIGH, 5 VERYHIGH, 6 ULTRA
        m.put("shadowMapResolution", rank <= 2 ? "1024" : rank <= 4 ? "2048" : "4096");
        m.put("shadowDistance", switch (Math.min(rank, 6)) {
            case 0, 1 -> "80.0";
            case 2 -> "96.0";
            case 3 -> "128.0";
            case 4 -> "160.0";
            case 5 -> "192.0";
            default -> "224.0";
        });
        m.put("VL_SAMPLES", switch (Math.min(rank, 6)) {
            case 0, 1 -> "4";
            case 2 -> "6";
            case 3 -> "8";
            case 4 -> "10";
            case 5 -> "12";
            default -> "16";
        });
        m.put("VOLUMETRIC_CLOUDS", rank <= 1 ? "false" : "true");
        m.put("SSGI", rank >= 5 ? "true" : "false");
        m.put("SCREENSPACE_CONTACT_SHADOWS", rank >= 4 ? "true" : "false");
        m.put("ambientOcclusionLevel", rank <= 1 ? "0.0" : rank == 2 ? "0.5" : "1.0");
        // Bliss's DH integration master toggle. Its shaders.properties gates DH shadows behind it,
        // and enabling it keeps DH LOD terrain properly lit/shadowed instead of flat. Harmless when
        // DH is absent (the whole DH block is gated behind the DISTANT_HORIZONS compile define).
        m.put("DISTANT_HORIZONS_SHADOWMAP", "true");
        return m;
    }
}
