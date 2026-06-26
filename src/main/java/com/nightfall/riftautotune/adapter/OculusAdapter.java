package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.RiftConfig;
import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.Reflect;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Tunes Oculus shaders. There is no guaranteed public API for setting individual shader OPTIONS,
 * so we:
 * <ol>
 *   <li>select + enable/disable the pack via {@code config/oculus.properties};</li>
 *   <li>write per-option overrides to {@code shaderpacks/<pack>.txt} (Iris/Oculus override format);</li>
 *   <li>ask Oculus/Iris to reload via a reflective entry point.</li>
 * </ol>
 *
 * <p>Every external touch-point is best-effort and marked {@code // TODO: confirm} because the
 * exact property keys depend on the installed Oculus version and the shaderpack.</p>
 */
public final class OculusAdapter implements ConfigAdapter {

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get();
    private static final Path GAME_DIR = FMLPaths.GAMEDIR.get();

    @Override
    public String name() {
        return "Oculus";
    }

    @Override
    public boolean isAvailable() {
        return ModCompat.shadersAvailable();
    }

    @Override
    public void apply(GraphicsSettings settings) {
        boolean shadersOn = settings.get(Knob.SHADERS) > 0;
        String pack = resolvePackName();

        writeOculusProperties(shadersOn, pack);
        if (shadersOn && pack != null) {
            writeShaderOverrides(pack, settings);
        }
        triggerReload();
        RiftLog.debug("Oculus applied (shaders={}, pack={})", shadersOn, pack);
    }

    /** Find the preferred pack file in /shaderpacks, else keep whatever is currently selected. */
    private String resolvePackName() {
        String preferred = RiftConfig.PREFERRED_SHADERPACK.get();
        Path dir = GAME_DIR.resolve("shaderpacks");
        if (preferred != null && !preferred.isBlank() && Files.isDirectory(dir)) {
            String needle = preferred.toLowerCase(Locale.ROOT).replace(" ", "");
            try (Stream<Path> files = Files.list(dir)) {
                return files.map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).replace(" ", "").contains(needle))
                        .findFirst()
                        .orElse(currentlySelectedPack());
            } catch (Throwable t) {
                RiftLog.debug("shaderpack scan failed: {}", t.toString());
            }
        }
        return currentlySelectedPack();
    }

    private String currentlySelectedPack() {
        Properties p = readProps(CONFIG_DIR.resolve("oculus.properties"));
        return p.getProperty("shaderPack", null); // TODO: confirm key name in installed Oculus
    }

    private void writeOculusProperties(boolean enable, String pack) {
        Path file = CONFIG_DIR.resolve("oculus.properties");
        try {
            Properties props = readProps(file);
            props.setProperty("enableShaders", Boolean.toString(enable)); // TODO: confirm key
            if (pack != null) {
                props.setProperty("shaderPack", pack);                    // TODO: confirm key
            }
            backup(file);
            try (BufferedWriter w = Files.newBufferedWriter(file)) {
                props.store(w, "Written by RiftAutoTune");
            }
        } catch (Throwable t) {
            RiftLog.error("writing oculus.properties failed", t);
        }
    }

    /**
     * Write the user-override file the shader loader reads back: shaderpacks/&lt;pack&gt;.txt.
     * We only write keys we are confident about (shadowMapResolution is a near-universal
     * Complementary/BSL key); pack-specific keys are left for confirmation to avoid clobbering.
     */
    private void writeShaderOverrides(String pack, GraphicsSettings s) {
        // Iris/Oculus stores overrides next to the pack as "<pack>.txt".
        Path overrides = GAME_DIR.resolve("shaderpacks").resolve(pack + ".txt");
        try {
            StringBuilder sb = new StringBuilder();
            // High-confidence, widely-supported key:
            sb.append("shadowMapResolution=").append(s.value(Knob.SHADER_SHADOW_RES)).append('\n');
            // TODO: confirm per-pack keys before enabling these (names differ across packs):
            //   sb.append("SHADOW_QUALITY=").append(s.get(Knob.SHADER_SHADOW_DIST)).append('\n');
            //   sb.append("VOLUMETRIC_FOG=").append(s.get(Knob.SHADER_VOLUMETRIC) > 0).append('\n');
            //   sb.append("SSAO=").append(s.get(Knob.SHADER_SSAO) > 0).append('\n');
            //   sb.append("BLOOM=").append(s.get(Knob.SHADER_BLOOM) > 0).append('\n');
            backup(overrides);
            Files.writeString(overrides, sb.toString());
            RiftLog.debug("wrote shader overrides to {}", overrides.getFileName());
        } catch (Throwable t) {
            RiftLog.error("writing shader overrides failed", t);
        }
    }

    /** Try known Iris/Oculus reload entry points reflectively. */
    private void triggerReload() {
        // Newer Iris fork API
        Object api = Reflect.invokeStatic(Reflect.method(
                Reflect.clazz("net.irisshaders.iris.api.v0.IrisApi"), "getInstance"));
        if (api != null) {
            Method reload = Reflect.method(api.getClass(), "reload"); // TODO: confirm signature
            if (reload != null) {
                Reflect.invoke(reload, api);
                RiftLog.debug("Oculus/Iris reload via IrisApi");
                return;
            }
        }
        // Older coderbot-based Oculus
        Method legacy = Reflect.method(Reflect.clazz("net.coderbot.iris.Iris"), "reload");
        if (legacy != null) {
            Reflect.invokeStatic(legacy);
            RiftLog.debug("Oculus reload via net.coderbot.iris.Iris");
        }
    }

    private static Properties readProps(Path file) {
        Properties p = new Properties();
        try {
            if (Files.exists(file)) {
                try (var in = Files.newBufferedReader(file)) {
                    p.load(in);
                }
            }
        } catch (Throwable ignored) {
        }
        return p;
    }

    private static void backup(Path file) {
        try {
            if (Files.exists(file)) {
                Path bak = file.resolveSibling(file.getFileName() + ".riftbak");
                if (!Files.exists(bak)) Files.copy(file, bak);
            }
        } catch (Throwable ignored) {
        }
    }
}
