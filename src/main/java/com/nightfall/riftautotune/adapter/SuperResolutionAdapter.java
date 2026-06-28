package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.RiftConfig;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.Reflect;
import com.nightfall.riftautotune.util.RiftLog;

/**
 * Drives the <a href="https://modrinth.com/mod/superresolution">Super Resolution</a> mod's FSR
 * upscaler as a GPU-relief lever, reached reflectively (soft dependency).
 *
 * <p>Industry rationale: temporal upscaling (FSR2/FSR3) renders the scene at a lower internal
 * resolution and reconstructs to native, so GPU shading cost scales roughly with the pixel count
 * ~ 1/ratio&sup2;. Therefore the upscale ratio needed to lift the average FPS to the target is
 * about {@code sqrt(target / measured)}, clamped. A small sharpening pass compensates for the
 * softness upscaling introduces, scaled with the ratio.</p>
 *
 * <p>It only engages when the run is <b>GPU-bound</b> and below the FPS floor &mdash; upscaling does
 * nothing for a CPU-bound bottleneck (the GPU already has headroom), so in that case it renders at
 * native resolution. Every write is read back and logged so it is provable the option changed.</p>
 *
 * <p>Targets {@code io.homo.superresolution.common.config.SuperResolutionConfig}
 * ({@code ENABLE_UPSCALE}/{@code UPSCALE_RATIO}/{@code SHARPNESS}, each a {@code ConfigValue} with
 * {@code set(Object)}/{@code get()}). The mod's FSR backend supports FSRVersion V2/V3.</p>
 */
public final class SuperResolutionAdapter {

    private static final String CONFIG = "io.homo.superresolution.common.config.SuperResolutionConfig";
    private static final String API = "io.homo.superresolution.api.SuperResolutionAPI";

    public boolean isAvailable() {
        return ModCompat.superResolutionAvailable() && Reflect.clazz(CONFIG) != null;
    }

    /**
     * Decide and apply the upscale settings.
     *
     * @param avgFps       measured average FPS at native resolution
     * @param targetLowFps the FPS band floor we want to reach
     * @param gpuBound     whether the run is GPU-limited (upscaling only helps here)
     * @param cpuBound     whether the run is CPU-limited (upscaling will not help)
     */
    public void tune(double avgFps, int targetLowFps, boolean gpuBound, boolean cpuBound) {
        if (!isAvailable() || !RiftConfig.ENABLE_SUPER_RES.get()) return;

        boolean enable;
        float ratio;
        float sharpness;
        if (gpuBound && !cpuBound && avgFps > 0 && avgFps < targetLowFps) {
            double needed = Math.sqrt((double) targetLowFps / avgFps);
            double maxR = RiftConfig.SR_MAX_UPSCALE.get();
            ratio = (float) Math.max(1.0, Math.min(maxR, needed));
            enable = ratio > 1.02f; // skip a no-op upscale
            sharpness = (float) Math.max(0.2, Math.min(0.9, 0.2 + (ratio - 1.0) * 0.7));
        } else {
            enable = false;        // native res: not GPU-bound, or CPU is the wall (FSR won't help)
            ratio = 1.0f;
            sharpness = 0.3f;
        }

        Class<?> cfg = Reflect.clazz(CONFIG);
        RiftLog.info("SuperRes decision: enable={}, ratio={}x, sharpness={} (avg {} / target {}, {})",
                enable, String.format(java.util.Locale.ROOT, "%.2f", ratio),
                String.format(java.util.Locale.ROOT, "%.2f", sharpness),
                (int) avgFps, targetLowFps, cpuBound ? "CPU-bound" : (gpuBound ? "GPU-bound" : "in band"));

        setVal(cfg, "ENABLE_UPSCALE", Boolean.valueOf(enable), "enableUpscale");
        if (enable) {
            setVal(cfg, "UPSCALE_RATIO", Float.valueOf(ratio), "upscaleRatio");
            setVal(cfg, "SHARPNESS", Float.valueOf(sharpness), "sharpness");
        }
        logRenderResolution();
    }

    /** Set a SuperResolutionConfig {@code ConfigValue} static field and confirm via read-back. */
    private static void setVal(Class<?> cfg, String field, Object value, String label) {
        Object cv = Reflect.getStaticField(cfg, field);
        if (cv == null) {
            RiftLog.warn("SuperRes {}: config field '{}' missing - not applied.", label, field);
            return;
        }
        Reflect.invoke(Reflect.methodByName(cv.getClass(), "set", 1), cv, value);
        Object readback = Reflect.invoke(Reflect.methodByName(cv.getClass(), "get", 0), cv);
        if (readback != null && readback.equals(value)) {
            RiftLog.info("SuperRes {} = {} (applied OK)", label, readback);
        } else {
            RiftLog.warn("SuperRes {} -> requested {}, readback {} (NOT confirmed)", label, value, readback);
        }
    }

    /** Log the actual internal render resolution vs output, proving the upscale took effect. */
    private static void logRenderResolution() {
        try {
            Class<?> api = Reflect.clazz(API);
            Object rw = Reflect.invokeStatic(Reflect.method(api, "getRenderWidth"));
            Object rh = Reflect.invokeStatic(Reflect.method(api, "getRenderHeight"));
            Object sw = Reflect.invokeStatic(Reflect.method(api, "getScreenWidth"));
            Object sh = Reflect.invokeStatic(Reflect.method(api, "getScreenHeight"));
            if (rw != null && sw != null) {
                RiftLog.info("SuperRes internal render {}x{} -> output {}x{}", rw, rh, sw, sh);
            }
        } catch (Throwable ignored) {
        }
    }
}
