package com.nightfall.riftautotune.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists the chosen profile + hardware fingerprint so we only re-benchmark when the hardware
 * actually changes. Stored as JSON in the config directory. All I/O runs on the async worker.
 */
public final class ProfileStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("riftautotune-profile.json");

    private ProfileStore() {}

    /** Serializable snapshot. */
    public static final class SavedProfile {
        public String fingerprint;
        public String tier;
        public double avgFps;
        public double onePctLowFps;
        public Map<String, Integer> levels = new LinkedHashMap<>();
    }

    public static SavedProfile build(String fingerprint, String tier, double avgFps,
                                     double onePctLowFps, GraphicsSettings settings) {
        SavedProfile p = new SavedProfile();
        p.fingerprint = fingerprint;
        p.tier = tier;
        p.avgFps = avgFps;
        p.onePctLowFps = onePctLowFps;
        for (Knob k : Knob.values()) {
            p.levels.put(k.name(), settings.get(k));
        }
        return p;
    }

    public static GraphicsSettings toSettings(SavedProfile p) {
        GraphicsSettings s = new GraphicsSettings();
        if (p != null && p.levels != null) {
            for (Map.Entry<String, Integer> e : p.levels.entrySet()) {
                try {
                    s.set(Knob.valueOf(e.getKey()), e.getValue());
                } catch (IllegalArgumentException ignored) {
                    // unknown knob name from an older/newer version - skip it
                }
            }
        }
        return s;
    }

    public static synchronized void save(SavedProfile profile) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(profile));
            RiftLog.info("Saved profile to {}", FILE);
        } catch (IOException e) {
            RiftLog.error("Could not save profile", e);
        }
    }

    public static synchronized SavedProfile load() {
        try {
            if (!Files.exists(FILE)) return null;
            return GSON.fromJson(Files.readString(FILE), SavedProfile.class);
        } catch (Exception e) {
            RiftLog.error("Could not read profile (ignoring)", e);
            return null;
        }
    }

    public static synchronized void delete() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException e) {
            RiftLog.error("Could not delete profile", e);
        }
    }
}
