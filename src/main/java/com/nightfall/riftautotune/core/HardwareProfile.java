package com.nightfall.riftautotune.core;

import java.util.Locale;

/**
 * Immutable snapshot of the user's hardware and display, plus a derived {@link HardwareTier}
 * and a stable fingerprint used to decide when a re-benchmark is required.
 *
 * <p>Pure data + classification logic, no Minecraft imports. The client-side
 * {@code HardwareDetector} fills this in from GL / GLFW / JMX queries.</p>
 */
public final class HardwareProfile {

    public final String gpuVendor;
    public final String gpuRenderer;
    public final String glVersion;
    /** Dedicated VRAM in MiB, or {@code -1} if it could not be detected. */
    public final int vramMb;
    public final int cpuThreads;
    public final int systemRamMb;
    /** JVM max heap (-Xmx) in MiB. */
    public final int maxHeapMb;
    public final String os;
    public final int screenWidth;
    public final int screenHeight;
    public final int refreshRate;
    public final int guiScale;
    public final HardwareTier tier;

    public HardwareProfile(String gpuVendor, String gpuRenderer, String glVersion, int vramMb,
                           int cpuThreads, int systemRamMb, int maxHeapMb, String os,
                           int screenWidth, int screenHeight, int refreshRate, int guiScale) {
        this.gpuVendor = safe(gpuVendor);
        this.gpuRenderer = safe(gpuRenderer);
        this.glVersion = safe(glVersion);
        this.vramMb = vramMb;
        this.cpuThreads = Math.max(1, cpuThreads);
        this.systemRamMb = Math.max(0, systemRamMb);
        this.maxHeapMb = Math.max(0, maxHeapMb);
        this.os = safe(os);
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        this.refreshRate = refreshRate <= 0 ? 60 : refreshRate;
        this.guiScale = Math.max(0, guiScale);
        this.tier = classify(this.vramMb, this.cpuThreads, this.systemRamMb,
                this.screenWidth, this.screenHeight, this.gpuRenderer);
    }

    private static String safe(String s) {
        return s == null ? "unknown" : s;
    }

    /** Total pixels the GPU must shade per frame, the main resolution-cost driver. */
    public long pixelCount() {
        return (long) screenWidth * screenHeight;
    }

    /**
     * Classify hardware into a tier. Thresholds are documented and deliberately conservative
     * so that a freshly-classified machine starts a notch <em>below</em> what it can probably
     * sustain; the benchmark then ratchets quality back up.
     *
     * <p>Worked example (matches the design note): a GTX 1060 6&nbsp;GB + 6&nbsp;threads +
     * 16&nbsp;GB RAM at 1080p scores VRAM=2, CPU=1, RAM=1 =&gt; 4 =&gt; {@link HardwareTier#MEDIUM}
     * (the MEDIUM&ndash;HIGH boundary; at 1440p the resolution penalty keeps it at MEDIUM, and an
     * 8&nbsp;GB card would tip it to HIGH).</p>
     */
    public static HardwareTier classify(int vramMb, int cpuThreads, int systemRamMb,
                                        int screenWidth, int screenHeight, String renderer) {
        int score = 0;

        // VRAM is the strongest single signal for shader + Distant Horizons headroom.
        if (vramMb < 0) {
            // VRAM unknown: fall back to a renderer-string heuristic, otherwise assume modest.
            score += heuristicVramScore(renderer);
        } else if (vramMb >= 12288) score += 4;   // >=12 GB
        else if (vramMb >= 8192)    score += 3;   //   8 GB
        else if (vramMb >= 6144)    score += 2;   //   6 GB  (GTX 1060 6GB lands here)
        else if (vramMb >= 4096)    score += 1;   //   4 GB
        else                        score += 0;   //  <4 GB

        // CPU threads matter for chunk/LOD building and the simulation distance knob.
        if (cpuThreads >= 16)      score += 3;
        else if (cpuThreads >= 12) score += 2;
        else if (cpuThreads >= 8)  score += 1;
        else if (cpuThreads >= 6)  score += 1;
        else                       score += 0;    // dual/quad core

        // System RAM gates how large -Xmx can safely be. Check the larger threshold first so
        // both branches are reachable (>=32 GB must score +2, not fall through to the +1 branch).
        if (systemRamMb >= 32768)      score += 2;   // 32 GB
        else if (systemRamMb >= 16384) score += 1;   // 16 GB

        // Resolution penalty: more pixels => effectively a weaker GPU for our purposes.
        long pixels = (long) screenWidth * screenHeight;
        if (pixels >= 7_000_000L)      score -= 2;  // ~4K
        else if (pixels >= 3_500_000L) score -= 1;  // ~1440p
        // <=1080p: no penalty

        if (score <= 1) return HardwareTier.LOW;
        if (score <= 4) return HardwareTier.MEDIUM;
        if (score <= 6) return HardwareTier.HIGH;
        return HardwareTier.ULTRA;
    }

    /** Very rough fallback when VRAM cannot be read from the driver. */
    private static int heuristicVramScore(String renderer) {
        String r = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT);
        if (r.contains("rtx 40") || r.contains("rtx 30") || r.contains("rx 7") || r.contains("rx 6")) return 3;
        if (r.contains("rtx 20") || r.contains("gtx 16") || r.contains("rx 5")) return 2;
        if (r.contains("gtx 10")) return 2;
        if (r.contains("intel") || r.contains("uhd") || r.contains("iris")) return 0; // integrated
        return 1;
    }

    /**
     * Stable fingerprint of the components that affect tuning. If this changes between launches
     * (new GPU, driver, resolution, or -Xmx), the saved profile is invalidated and we re-benchmark.
     */
    public String fingerprint() {
        String raw = gpuVendor + '|' + gpuRenderer + '|' + glVersion + '|' + vramMb + '|'
                + cpuThreads + '|' + systemRamMb + '|' + maxHeapMb + '|'
                + screenWidth + 'x' + screenHeight + '@' + refreshRate;
        // Compact, deterministic, dependency-free hash (FNV-1a 64-bit) rendered as hex.
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < raw.length(); i++) {
            hash ^= raw.charAt(i);
            hash *= 0x100000001b3L;
        }
        return Long.toHexString(hash);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "HardwareProfile{tier=%s, gpu='%s', vram=%dMB, threads=%d, ram=%dMB, xmx=%dMB, %dx%d@%dHz}",
                tier, gpuRenderer, vramMb, cpuThreads, systemRamMb, maxHeapMb,
                screenWidth, screenHeight, refreshRate);
    }
}
