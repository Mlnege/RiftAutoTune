package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.Reflect;
import com.nightfall.riftautotune.util.RiftLog;

import java.lang.reflect.Method;

/**
 * Tunes Distant Horizons through its public {@code DhApi}, reached reflectively so RiftAutoTune
 * compiles and runs without the DH jar.
 *
 * <p>Targets the <b>DH 3.x</b> API actually shipped on 1.20.1
 * ({@code com.seibel.distanthorizons.api.DhApi$Delayed.configs} -&gt; {@code IDhApiConfig}). The
 * previous version targeted the DH 2.x chain ({@code graphics().quality().lodChunkRenderDistanceRadius()}),
 * which no longer exists in 3.x &mdash; so nothing applied. This rewrite uses the real 3.x getters
 * and reports, per value, whether {@code setValue} was accepted and what the config now reads back,
 * so it is provable in the log that the option actually changed.</p>
 *
 * <p>CPU control: {@code multiThreading().threadRuntimeRatio()} throttles how hard DH's worker
 * threads run. DH LOD generation is heavily CPU-bound (it can saturate even a 7950X3D), so when the
 * tuner marks the run CPU-bound it lowers the DH CPU-load knob, and this adapter turns that into a
 * real reduction of the thread runtime ratio + render distance.</p>
 */
public final class DistantHorizonsAdapter implements ConfigAdapter {

    private static final String DELAYED = "com.seibel.distanthorizons.api.DhApi$Delayed";
    private static final String VERTICAL_QUALITY_ENUM =
            "com.seibel.distanthorizons.api.enums.config.EDhApiVerticalQuality";
    private static final String TRANSPARENCY_ENUM =
            "com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency";

    @Override
    public String name() {
        return "DistantHorizons";
    }

    @Override
    public boolean isAvailable() {
        return ModCompat.distantHorizonsAvailable() && Reflect.clazz(DELAYED) != null;
    }

    @Override
    public void apply(GraphicsSettings s) {
        boolean dhOn = s.get(Knob.DH_LOD_DISTANCE) > 0;
        int lodChunks = Math.max(0, s.value(Knob.DH_LOD_DISTANCE)); // 0/64/128/256/512 chunks
        int cpuLoad = s.value(Knob.DH_CPU_LOAD);                    // 0..2
        int verticalQuality = s.value(Knob.DH_VERTICAL_QUALITY);   // 0..2

        Object configs = Reflect.getStaticField(Reflect.clazz(DELAYED), "configs");
        if (configs == null) {
            RiftLog.warn("DH: DhApi.Delayed.configs not available yet; skipping DH apply.");
            return;
        }

        Object graphics = call0(configs, "graphics");
        Object multi = call0(configs, "multiThreading");
        if (graphics == null) {
            RiftLog.warn("DH: graphics() config group missing (DH API changed?). Skipping.");
            return;
        }

        // Rendering on/off + render distance (chunks).
        setCfg(call0(graphics, "renderingEnabled"), Boolean.valueOf(dhOn), "renderingEnabled");
        if (dhOn) {
            setCfg(call0(graphics, "chunkRenderDistance"), Integer.valueOf(lodChunks), "chunkRenderDistance");
            Object vq = Reflect.enumConst(VERTICAL_QUALITY_ENUM, verticalQualityName(verticalQuality));
            if (vq != null) setCfg(call0(graphics, "verticalQuality"), vq, "verticalQuality");

            // Force transparency = COMPLETE. When it is off (which DH silently does if the quality
            // preset drops below MEDIUM), Iris+DH renders the world onto the sky with Bliss and
            // other shaders (the well-known "world drawn on the sky" bug). Re-asserting COMPLETE on
            // every apply keeps it fixed even if a preset change tried to disable it.
            Object complete = Reflect.enumConst(TRANSPARENCY_ENUM, "COMPLETE");
            if (complete != null) setCfg(call0(graphics, "transparency"), complete, "transparency");
        }

        // CPU throttle - the key lever for "DH crushes the CPU". 0..2 -> 25% / 50% / 100% runtime.
        // At the low levels the worker THREAD COUNT is also capped: the runtime ratio alone still
        // wakes every worker (bad on a busy host/multiplayer client); one throttled thread is the
        // true low-impact mode.
        if (multi != null) {
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            int level = Math.max(0, Math.min(2, cpuLoad));
            double ratio = switch (level) {
                case 0 -> 0.25;
                case 1 -> 0.50;
                default -> 1.00;
            };
            setCfg(call0(multi, "threadRuntimeRatio"), Double.valueOf(ratio), "threadRuntimeRatio");
            switch (level) {
                case 0 -> setCfg(call0(multi, "threadCount"), Integer.valueOf(1), "threadCount");
                case 1 -> setCfg(call0(multi, "threadCount"),
                        Integer.valueOf(Math.max(1, cores / 4)), "threadCount");
                default -> { /* leave DH's own thread count */ }
            }
        }
    }

    private static String verticalQualityName(int level) {
        return switch (Math.max(0, Math.min(2, level))) {
            case 0 -> "LOW";
            case 1 -> "MEDIUM";
            default -> "HIGH";
        };
    }

    /** Invoke a no-arg getter on the config tree, null-safe. */
    private static Object call0(Object target, String getter) {
        if (target == null) return null;
        return Reflect.invoke(Reflect.methodByName(target.getClass(), getter, 0), target);
    }

    /**
     * Set an {@code IDhApiConfigValue} and log the outcome. {@code setValue} returns whether the API
     * override was accepted; we also read the value back so the log proves the change took effect.
     */
    private static void setCfg(Object cfgValue, Object value, String label) {
        if (cfgValue == null) {
            RiftLog.warn("DH {}: config handle missing - not applied.", label);
            return;
        }
        // Read-before-write: DH disposes ALL render data on any config write (its
        // AbstractDelayedConfigTimer fires "Render data cleared..."), so writing an unchanged
        // value on every apply made LOD generation restart endlessly and never display. Skip the
        // write entirely when the value already matches.
        Object current = Reflect.invoke(Reflect.methodByName(cfgValue.getClass(), "getValue", 0), cfgValue);
        if (current != null && current.equals(value)) {
            RiftLog.debug("DH {} already {} - no write (avoids render-data reset).", label, current);
            return;
        }
        Method setValue = Reflect.methodByName(cfgValue.getClass(), "setValue", 1);
        Object accepted = Reflect.invoke(setValue, cfgValue, value);
        Object readback = Reflect.invoke(Reflect.methodByName(cfgValue.getClass(), "getValue", 0), cfgValue);
        boolean ok = Boolean.TRUE.equals(accepted) && (readback == null || readback.equals(value));
        if (ok) {
            RiftLog.info("DH {} = {} (applied OK)", label, readback);
        } else {
            RiftLog.warn("DH {} -> requested {}, accepted={}, readback={} (NOT confirmed)",
                    label, value, accepted, readback);
        }
    }
}
