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
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

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
        DEBUG_LOGGING = b.comment("Verbose tuning logs.")
                .define("debugLogging", false);

        b.pop();
        SPEC = b.build();
    }

    private RiftConfig() {}
}
