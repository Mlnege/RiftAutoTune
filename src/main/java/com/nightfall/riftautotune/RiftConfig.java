package com.nightfall.riftautotune;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side configuration (config/riftautotune-client.toml).
 */
public final class RiftConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue TARGET_FPS_MIN;
    public static final ForgeConfigSpec.IntValue TARGET_FPS_MAX;
    public static final ForgeConfigSpec.IntValue ONE_PCT_FLOOR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ADAPTIVE;
    public static final ForgeConfigSpec.IntValue BENCHMARK_SECONDS;
    public static final ForgeConfigSpec.DoubleValue QUALITY_BIAS;
    public static final ForgeConfigSpec.ConfigValue<String> PREFERRED_SHADERPACK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SUPER_RES;
    public static final ForgeConfigSpec.DoubleValue SR_MAX_UPSCALE;
    public static final ForgeConfigSpec.BooleanValue ASK_SHADER_CONSENT;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    public static final ForgeConfigSpec.BooleanValue MEMORY_TELEMETRY;
    public static final ForgeConfigSpec.IntValue MEMORY_LOG_INTERVAL_SECONDS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_C2ME_TUNING;
    public static final ForgeConfigSpec.IntValue C2ME_MAX_PARALLELISM;

    public static final ForgeConfigSpec.BooleanValue ENABLE_VOXY_TUNING;
    public static final ForgeConfigSpec.IntValue VOXY_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.IntValue VOXY_HOST_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.IntValue VOXY_GUEST_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.BooleanValue VOXY_REMOTE_CPU_OFF;
    public static final ForgeConfigSpec.BooleanValue VOXY_HOST_INGEST_OFF;
    public static final ForgeConfigSpec.IntValue VOXY_MAX_THREADS;

    public static final ForgeConfigSpec.BooleanValue DH_GUARD;
    public static final ForgeConfigSpec.IntValue DH_HOST_MAX_LOD_LEVEL;
    public static final ForgeConfigSpec.BooleanValue DH_AUTO_OFF;
    public static final ForgeConfigSpec.IntValue DH_AUTO_OFF_FPS;
    public static final ForgeConfigSpec.IntValue DH_AUTO_OFF_HOLD_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("RiftAutoTune - automatic benchmark & adaptive graphics tuner").push("general");

        TARGET_FPS_MIN = b.comment("Lower edge of the target FPS band.")
                .defineInRange("targetFpsMin", 60, 20, 480);
        TARGET_FPS_MAX = b.comment("Upper edge of the target FPS band. Auto-clamped to your monitor's refresh rate.")
                .defineInRange("targetFpsMax", 100, 30, 480);
        ONE_PCT_FLOOR = b.comment("Minimum acceptable 1% low FPS. The optimizer keeps the 1% low above this.")
                .defineInRange("onePctLowFloor", 50, 10, 240);
        ENABLE_ADAPTIVE = b.comment("Keep adjusting settings during play to stay in the band.")
                .define("enableAdaptiveMode", true);
        BENCHMARK_SECONDS = b.comment("Length of the steady-state measurement window, in seconds.")
                .defineInRange("benchmarkSeconds", 8, 3, 60);
        QUALITY_BIAS = b.comment("0.0 = favour performance (more FPS headroom), 1.0 = favour quality (closer to the floor).")
                .defineInRange("qualityBias", 0.5D, 0.0D, 1.0D);
        PREFERRED_SHADERPACK = b.comment(
                        "Preferred Oculus shaderpack to tune. License-clean, DH-compatible default.",
                        "If it is not installed, RiftAutoTune tunes whatever pack is active instead.")
                .define("preferredShaderpack", "ComplementaryReimagined");
        ENABLE_SUPER_RES = b.comment(
                        "Use the Super Resolution (FSR) mod to upscale when the run is GPU-bound below the",
                        "FPS floor. Renders at a lower internal resolution and reconstructs to native.")
                .define("enableSuperResolution", true);
        SR_MAX_UPSCALE = b.comment(
                        "Maximum FSR upscale ratio. 1.0 = native; 1.5 = render at ~67% linear resolution;",
                        "2.0 = render at 50% linear resolution (about 4x fewer shaded pixels).")
                .defineInRange("superResolutionMaxUpscale", 2.0D, 1.0D, 3.0D);
        ASK_SHADER_CONSENT = b.comment(
                        "Ask for confirmation before enabling shaders on world entry, showing the",
                        "detected hardware and the expected performance. Shaders are only applied",
                        "after the player accepts; declining tunes the profile without shaders.")
                .define("askShaderConsent", true);
        DEBUG_LOGGING = b.comment("Verbose tuning logs.")
                .define("debugLogging", false);

        b.pop();

        b.comment("Memory telemetry - periodic log snapshot of heap / direct buffers / packed caches.")
                .push("memory");

        MEMORY_TELEMETRY = b.comment(
                        "Log a memory snapshot (heap used/max, JVM direct buffer pool, packed cache",
                        "stats, loaded client chunks) to the game log at a fixed interval.",
                        "Same data as /riftautotune memory.")
                .define("memoryTelemetry", true);
        MEMORY_LOG_INTERVAL_SECONDS = b.comment("Seconds between memory telemetry log lines.")
                .defineInRange("memoryLogIntervalSeconds", 60, 10, 3600);

        b.pop();

        b.comment("C2ME (parallel chunk engine) static tuning - written to config/c2me.toml, applies next launch.")
                .push("chunkEngine");

        ENABLE_C2ME_TUNING = b.comment(
                        "Size C2ME's worker thread pool from the measured hardware tier (conservative:",
                        "always below C2ME's own default so chunk gen never overloads the machine).")
                .define("enableC2meTuning", true);
        C2ME_MAX_PARALLELISM = b.comment(
                        "Hard cap for C2ME worker threads. 0 = automatic (cores - 2).")
                .defineInRange("c2meMaxParallelism", 0, 0, 64);

        b.pop();

        b.comment("Voxy (LOD renderer) tuning - pinned render distance, benchmark-driven threads.")
                .push("voxy");

        ENABLE_VOXY_TUNING = b.comment(
                        "Manage Voxy when it is installed: pin its render distance and size its",
                        "worker threads from the benchmark instead of Voxy's cores*2/1.5 default.")
                .define("enableVoxyTuning", true);
        VOXY_RENDER_DISTANCE_CHUNKS = b.comment(
                        "Voxy render distance in SINGLEPLAYER, in chunks.",
                        "Never lowered by the adaptive optimizer.")
                .defineInRange("voxyRenderDistanceChunks", 256, 32, 2048);
        VOXY_HOST_RENDER_DISTANCE_CHUNKS = b.comment(
                        "Voxy render distance when HOSTING - the owner's local/LAN client of a",
                        "dedicated or open-to-LAN server, in chunks. The 'I have the beefy machine,",
                        "render everything' horizon; needs matching Chunky/Chunksmith pregen and a",
                        "large geometry buffer. WARNING: RAM/VRAM scale with this - 2048 is huge",
                        "(~32k blocks) and largely aspirational without massive pregen.")
                .defineInRange("voxyHostRenderDistanceChunks", 2048, 32, 2048);
        VOXY_GUEST_RENDER_DISTANCE_CHUNKS = b.comment(
                        "Voxy render distance for a REMOTE multiplayer guest (not the host), in",
                        "chunks. Kept small so guests stay light; with voxyRemoteCpuOff they also",
                        "build no LODs (they still render whatever the server streams).")
                .defineInRange("voxyGuestRenderDistanceChunks", 32, 32, 256);
        VOXY_REMOTE_CPU_OFF = b.comment(
                        "On a remote multiplayer client (not the host), stop Voxy from building",
                        "LODs entirely (ingest off, 1 worker). Existing LODs still render.")
                .define("voxyRemoteCpuOff", true);
        VOXY_HOST_INGEST_OFF = b.comment(
                        "When HOSTING, make the client render-only: it renders/meshes the LOD",
                        "horizon (threads kept for the big host distance) but does NOT ingest world",
                        "chunks into new LODs itself - the dedicated server's Chunky pregen + VSS",
                        "generate and stream the LODs to it. Lifts LOD generation off the playing",
                        "client (paced on the server) while the far view stays. Set false to have",
                        "the host also build LODs locally as it explores un-pregenerated areas.")
                .define("voxyHostIngestOff", true);
        VOXY_MAX_THREADS = b.comment(
                        "Hard cap for Voxy worker threads. 0 = automatic (cores / 2).")
                .defineInRange("voxyMaxThreads", 0, 0, 64);

        b.pop();

        b.comment("Distant Horizons session guard - multiplayer/host protection and auto-off.")
                .push("distantHorizons");

        DH_GUARD = b.comment(
                        "In ANY multiplayer session (remote server or hosting), force the DH CPU load",
                        "to its minimum so LOD generation never fights the network/server work.")
                .define("dhMultiplayerGuard", true);
        DH_HOST_MAX_LOD_LEVEL = b.comment(
                        "Highest DH LOD distance level allowed while HOSTING an open world",
                        "(0=off, 1=64, 2=128, 3=256, 4=512 chunks). Keeps the integrated server responsive.")
                .defineInRange("dhHostMaxLodLevel", 2, 0, 4);
        DH_AUTO_OFF = b.comment(
                        "If FPS stays under dhAutoOffFps for dhAutoOffHoldSeconds while DH is rendering,",
                        "switch DH off for this session (sticky; /riftautotune dh on re-enables).")
                .define("dhAutoOff", true);
        DH_AUTO_OFF_FPS = b.comment("Average-FPS floor that triggers the DH auto-off.")
                .defineInRange("dhAutoOffFps", 30, 10, 120);
        DH_AUTO_OFF_HOLD_SECONDS = b.comment("How long FPS must stay under the floor before auto-off.")
                .defineInRange("dhAutoOffHoldSeconds", 45, 5, 600);

        b.pop();
        SPEC = b.build();
    }

    private RiftConfig() {}
}
