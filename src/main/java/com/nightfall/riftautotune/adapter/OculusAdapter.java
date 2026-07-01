package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.RiftConfig;
import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.core.ShaderProfilePolicy;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.Reflect;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Tunes Oculus shaders. There is no guaranteed public API for setting individual shader OPTIONS,
 * so we:
 * <ol>
 *   <li>select + enable/disable the pack via {@code config/oculus.properties};</li>
 *   <li>write per-option overrides to {@code shaderpacks/<pack>.txt} (Iris/Oculus override format);</li>
 * </ol>
 *
 * <p>The adaptive loop calls this adapter with shader reload disabled. Benchmark/profile entry
 * points may allow one reload only when files actually changed, so the measured run sees the
 * chosen profile without the old intermittent mid-play refreshes.</p>
 */
public final class OculusAdapter implements ConfigAdapter {

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get();
    private static final Path GAME_DIR = FMLPaths.GAMEDIR.get();
    private static final String SHADER_PROPERTIES = "shaders/shaders.properties";
    private static final Pattern PROFILE_LINE =
            Pattern.compile("^\\s*profile\\.([A-Za-z0-9_]+)\\s*=\\s*(.+?)\\s*$");
    private static final Pattern OPTION_PAIR =
            Pattern.compile("([A-Za-z0-9_\\.]+)=([^\\s]+)");

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
        apply(settings, false);
    }

    public void apply(GraphicsSettings settings, boolean allowShaderReload) {
        boolean shadersOn = settings.get(Knob.SHADERS) > 0;
        String pack = resolvePackName();

        boolean oculusChanged = writeOculusProperties(shadersOn, pack);
        boolean shaderChanged = false;
        if (shadersOn && pack != null) {
            shaderChanged = writeShaderOverrides(pack, settings);
        }
        boolean reload = allowShaderReload && (oculusChanged || shaderChanged);
        if (reload) {
            triggerReload();
        }
        RiftLog.debug("Oculus applied (shaders={}, pack={}, oculusChanged={}, shaderOverridesChanged={}, reload={})",
                shadersOn, pack, oculusChanged, shaderChanged, reload ? "once" : "suppressed");
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
        return p.getProperty("shaderPack", null);
    }

    private boolean writeOculusProperties(boolean enable, String pack) {
        Path file = CONFIG_DIR.resolve("oculus.properties");
        try {
            Properties props = readProps(file);
            String enableValue = Boolean.toString(enable);
            boolean changed = !enableValue.equals(props.getProperty("enableShaders"));
            props.setProperty("enableShaders", enableValue);
            if (pack != null) {
                changed |= !pack.equals(props.getProperty("shaderPack"));
                props.setProperty("shaderPack", pack);
            }
            if (!changed) return false;
            backup(file);
            writeStableProperties(file, props, "Written by RiftAutoTune");
            return true;
        } catch (Throwable t) {
            RiftLog.error("writing oculus.properties failed", t);
            return false;
        }
    }

    /**
     * Write the user-override file the shader loader reads back: shaderpacks/&lt;pack&gt;.txt.
     * Spooklementary/Complementary profiles are read from the active pack at runtime, so updates
     * track the installed shaderpack instead of hard-coding every option value in the mod.
     */
    private boolean writeShaderOverrides(String pack, GraphicsSettings s) {
        // Iris/Oculus stores overrides next to the pack as "<pack>.txt".
        Path overrides = GAME_DIR.resolve("shaderpacks").resolve(pack + ".txt");
        try {
            ShaderPackMetadata metadata = readShaderPackMetadata(pack);
            String profile = ShaderProfilePolicy.profileFor(s);
            profile = ShaderProfilePolicy.nearestAvailable(profile, metadata.profiles().keySet());

            Map<String, String> desired = new LinkedHashMap<>();
            if (!metadata.profiles().isEmpty()) {
                desired.put("profile", profile);
                Map<String, String> profileOptions = metadata.profiles().get(profile);
                if (profileOptions != null) {
                    desired.putAll(profileOptions);
                }
            }

            desired.put("shadowMapResolution", Integer.toString(s.value(Knob.SHADER_SHADOW_RES)));
            if (metadata.managedKeys().contains("RP_MODE") || isSpooklementary(pack)) {
                desired.put("RP_MODE", ShaderProfilePolicy.pbrModeFor(profile));
            }

            Set<String> managed = new HashSet<>(metadata.managedKeys());
            managed.addAll(desired.keySet());
            Map<String, String> merged = readSimpleProperties(overrides);
            merged.keySet().removeIf(managed::contains);
            merged.putAll(desired);

            String text = formatSimpleProperties(merged);
            String old = Files.exists(overrides) ? Files.readString(overrides) : "";
            if (old.equals(text)) return false;
            backup(overrides);
            Files.writeString(overrides, text, StandardCharsets.UTF_8);
            RiftLog.info("wrote {} profile {} overrides to {} (PBR={})",
                    pack, profile, overrides.getFileName(), desired.get("RP_MODE"));
            return true;
        } catch (Throwable t) {
            RiftLog.error("writing shader overrides failed", t);
            return false;
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

    private static void writeStableProperties(Path file, Properties props, String header) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("#");
            w.write(header);
            w.newLine();
            for (String key : new java.util.TreeSet<>(props.stringPropertyNames())) {
                w.write(key);
                w.write('=');
                w.write(props.getProperty(key, ""));
                w.newLine();
            }
        }
    }

    private static Map<String, String> readSimpleProperties(Path file) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            if (!Files.exists(file)) return map;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int idx = trimmed.indexOf('=');
                if (idx <= 0) continue;
                map.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    private static String formatSimpleProperties(Map<String, String> props) {
        StringBuilder sb = new StringBuilder("# Written by RiftAutoTune - shader reload suppressed\n");
        for (Map.Entry<String, String> e : props.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private ShaderPackMetadata readShaderPackMetadata(String pack) {
        String text = readShaderProperties(pack);
        Map<String, Map<String, String>> profiles = parseProfiles(text);
        Set<String> managed = new LinkedHashSet<>();
        managed.add("profile");
        managed.add("shadowMapResolution");
        for (Map<String, String> profile : profiles.values()) {
            managed.addAll(profile.keySet());
        }
        if (isSpooklementary(pack)) {
            managed.add("RP_MODE");
        }
        return new ShaderPackMetadata(profiles, managed);
    }

    private String readShaderProperties(String pack) {
        Path packPath = GAME_DIR.resolve("shaderpacks").resolve(pack);
        try {
            if (Files.isDirectory(packPath)) {
                Path file = packPath.resolve(SHADER_PROPERTIES);
                return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            }
            if (Files.isRegularFile(packPath)) {
                try (ZipFile zip = new ZipFile(packPath.toFile())) {
                    ZipEntry entry = zip.getEntry(SHADER_PROPERTIES);
                    if (entry == null) return "";
                    try (InputStream in = zip.getInputStream(entry)) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Throwable t) {
            RiftLog.debug("could not read {} from {}: {}", SHADER_PROPERTIES, pack, t.toString());
        }
        return "";
    }

    private static Map<String, Map<String, String>> parseProfiles(String text) {
        Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return profiles;
        for (String rawLine : text.replace("\r", "").split("\n")) {
            Matcher line = PROFILE_LINE.matcher(rawLine);
            if (!line.matches()) continue;
            String name = line.group(1).toUpperCase(Locale.ROOT);
            Map<String, String> options = new LinkedHashMap<>();
            Matcher option = OPTION_PAIR.matcher(line.group(2));
            while (option.find()) {
                options.put(option.group(1), option.group(2));
            }
            profiles.put(name, options);
        }
        return profiles;
    }

    /** Try known Iris/Oculus reload entry points reflectively. Only called from explicit flows. */
    private void triggerReload() {
        Object api = Reflect.invokeStatic(Reflect.method(
                Reflect.clazz("net.irisshaders.iris.api.v0.IrisApi"), "getInstance"));
        if (api != null) {
            Method reload = Reflect.method(api.getClass(), "reload");
            if (reload != null) {
                Reflect.invoke(reload, api);
                RiftLog.debug("Oculus/Iris reload via IrisApi");
                return;
            }
        }
        Method legacy = Reflect.method(Reflect.clazz("net.coderbot.iris.Iris"), "reload");
        if (legacy != null) {
            Reflect.invokeStatic(legacy);
            RiftLog.debug("Oculus reload via net.coderbot.iris.Iris");
        }
    }

    private static boolean isSpooklementary(String pack) {
        return pack != null && pack.toLowerCase(Locale.ROOT).contains("spooklementary");
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

    private record ShaderPackMetadata(Map<String, Map<String, String>> profiles, Set<String> managedKeys) {}
}
