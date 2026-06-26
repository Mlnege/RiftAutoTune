package com.nightfall.riftautotune.util;

import net.minecraftforge.fml.ModList;

/** Centralised "is mod X present?" checks. Cheap and null-safe. */
public final class ModCompat {

    public static final String EMBEDDIUM = "embeddium";
    public static final String OCULUS = "oculus";
    public static final String DISTANT_HORIZONS = "distanthorizons";
    public static final String CTM = "ctm";
    public static final String FUSION = "fusion";

    private ModCompat() {}

    public static boolean loaded(String modId) {
        try {
            return ModList.get() != null && ModList.get().isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean shadersAvailable() {
        return loaded(OCULUS);
    }

    public static boolean distantHorizonsAvailable() {
        return loaded(DISTANT_HORIZONS);
    }

    public static boolean embeddiumAvailable() {
        return loaded(EMBEDDIUM);
    }
}
