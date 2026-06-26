package com.nightfall.riftautotune.adapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Tunes Embeddium via its JSON config (config/embeddium-options.json - NOT toml).
 *
 * <p>Embeddium's render distance follows the vanilla option, so here we only ensure the safe,
 * "free" performance toggles are on (face culling, fog occlusion, compact vertex format). Exact
 * JSON keys vary by Embeddium version, so we patch defensively: only keys that already exist are
 * overwritten.</p>
 */
public final class EmbeddiumAdapter implements ConfigAdapter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("embeddium-options.json");

    // TODO: confirm these key names against the installed Embeddium version.
    private static final String SECTION_ADVANCED = "advanced";
    private static final String[] PERF_FLAGS_TRUE = {
            "use_block_face_culling", "use_fog_occlusion", "use_compact_vertex_format",
            "use_entity_culling", "animate_only_visible_textures"
    };

    @Override
    public String name() {
        return "Embeddium";
    }

    @Override
    public boolean isAvailable() {
        return ModCompat.embeddiumAvailable();
    }

    @Override
    public void apply(GraphicsSettings settings) {
        try {
            if (!Files.exists(FILE)) {
                RiftLog.debug("embeddium-options.json not found yet; Embeddium will write it on first run.");
                return;
            }
            backup(FILE);
            JsonObject root = GSON.fromJson(Files.readString(FILE), JsonObject.class);
            if (root == null) return;

            JsonObject advanced = root.has(SECTION_ADVANCED) && root.get(SECTION_ADVANCED).isJsonObject()
                    ? root.getAsJsonObject(SECTION_ADVANCED) : null;
            if (advanced != null) {
                for (String key : PERF_FLAGS_TRUE) {
                    if (advanced.has(key)) {
                        advanced.addProperty(key, true);
                    }
                }
            }
            Files.writeString(FILE, GSON.toJson(root));
            RiftLog.debug("Embeddium perf flags ensured.");
            // Embeddium reloads its options from disk on next load; a live reload API is not relied on.
        } catch (Throwable t) {
            RiftLog.error("Embeddium adapter failed", t);
        }
    }

    private static void backup(Path file) {
        try {
            Path bak = file.resolveSibling(file.getFileName() + ".riftbak");
            if (!Files.exists(bak)) {
                Files.copy(file, bak, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (Throwable ignored) {
            // backup is best-effort
        }
    }
}
