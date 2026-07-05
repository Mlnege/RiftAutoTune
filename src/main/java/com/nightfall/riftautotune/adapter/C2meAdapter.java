package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.RiftConfig;
import com.nightfall.riftautotune.core.C2meTuningPolicy;
import com.nightfall.riftautotune.core.HardwareProfile;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.RiftLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static (next-launch) tuner for C2ME / C3ME, the parallel chunk engine running through Sinytra
 * Connector in this pack.
 *
 * <p>C2ME evaluates {@code config/c2me.toml} once in a bootstrap class-init, long before any mod
 * API could reach it, so there is no runtime knob to turn. Instead this adapter rewrites the
 * {@code globalExecutorParallelism} key (the master worker-thread count) after each benchmark, and
 * the new value takes effect on the next game launch. The original file is backed up once as
 * {@code c2me.toml.riftbak}.</p>
 */
public final class C2meAdapter {

    private static final Pattern KEY_LINE =
            Pattern.compile("(?m)^\\s*globalExecutorParallelism\\s*=\\s*-?\\d+\\s*$");

    /** Connector keeps the fabric mod id; stock C2ME uses c2me, the Connector fork uses c3me. */
    public boolean isAvailable() {
        return ModCompat.loaded("c3me") || ModCompat.loaded("c2me");
    }

    /**
     * Compute and persist the recommended parallelism. Safe to call from any thread (file I/O
     * only). Returns the value written, or -1 when unavailable/failed/unchanged-not-needed.
     */
    public int tune(HardwareProfile hardware, boolean cpuBound) {
        if (!RiftConfig.ENABLE_C2ME_TUNING.get() || !isAvailable() || hardware == null) return -1;
        int target = C2meTuningPolicy.parallelismFor(
                hardware.tier, hardware.cpuThreads, cpuBound, RiftConfig.C2ME_MAX_PARALLELISM.get());
        try {
            Path cfg = Paths.get("config", "c2me.toml");
            if (Files.exists(cfg)) {
                String text = Files.readString(cfg);
                Matcher m = KEY_LINE.matcher(text);
                String replacement = "globalExecutorParallelism = " + target;
                String updated;
                if (m.find()) {
                    if (m.group().trim().equals(replacement)) {
                        RiftLog.debug("C2ME parallelism already {} - no write needed.", target);
                        return target;
                    }
                    updated = m.replaceFirst(replacement);
                } else {
                    updated = text + (text.endsWith("\n") ? "" : "\n") + replacement + "\n";
                }
                Path bak = cfg.resolveSibling("c2me.toml.riftbak");
                if (!Files.exists(bak)) Files.copy(cfg, bak, StandardCopyOption.COPY_ATTRIBUTES);
                Files.writeString(cfg, updated);
            } else {
                // First launch alongside C2ME: seed a minimal config; C2ME fills in the rest.
                List<String> lines = new ArrayList<>();
                lines.add("# Seeded by RiftAutoTune - C2ME merges its remaining defaults on next launch.");
                lines.add("globalExecutorParallelism = " + target);
                Files.createDirectories(cfg.getParent());
                Files.write(cfg, lines);
            }
            RiftLog.info("C2ME globalExecutorParallelism -> {} (tier {}, {} cores{}). Applies on NEXT launch.",
                    target, hardware.tier, hardware.cpuThreads, cpuBound ? ", cpu-bound" : "");
            return target;
        } catch (Throwable t) {
            RiftLog.warn("C2ME config write failed: {}", t.toString());
            return -1;
        }
    }
}
