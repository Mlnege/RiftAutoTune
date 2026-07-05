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

    public static final ForgeConfigSpec.BooleanValue ENABLE_C2ME_TUNING;
    public static final ForgeConfigSpec.IntValue C2ME_MAX_PARALLELISM;

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
