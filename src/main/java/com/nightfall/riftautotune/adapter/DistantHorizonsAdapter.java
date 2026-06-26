package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.Reflect;
import com.nightfall.riftautotune.util.RiftLog;

/**
 * Tunes Distant Horizons through its public API ({@code com.seibel.distanthorizons.api.DhApi}),
 * reached reflectively so the mod compiles and runs without the DH jar present.
 *
 * <p>Realism note: DH + Oculus shader integration on 1.20.1 is version-sensitive. When the active
 * shaderpack does not advertise DH ("Iris-DH v2") support, this adapter requests DH's "Old
 * Lighting" mode so LODs still receive shading instead of rendering black/broken. Detecting
 * shader-side DH support is itself uncertain, so the relevant call sites are marked
 * {@code // TODO: confirm}.</p>
 */
public final class DistantHorizonsAdapter implements ConfigAdapter {

    private static final String DH_API = "com.seibel.distanthorizons.api.DhApi";

    @Override
    public String name() {
        return "DistantHorizons";
    }

    @Override
    public boolean isAvailable() {
        return ModCompat.distantHorizonsAvailable() && Reflect.clazz(DH_API) != null;
    }

    @Override
    public void apply(GraphicsSettings s) {
        boolean dhOn = s.get(Knob.DH_LOD_DISTANCE) > 0;
        int lodChunks = Math.max(0, s.value(Knob.DH_LOD_DISTANCE)); // 0/64/128/256/512 (chunks)
        int cpuLoad = s.value(Knob.DH_CPU_LOAD);                    // 0..2
        int verticalQuality = s.value(Knob.DH_VERTICAL_QUALITY);   // 0..2
        int lodDetail = s.value(Knob.DH_LOD_DETAIL);               // 0..2

        RiftLog.info("DH target: on={}, lodDistance={}ch, cpuLoad={}, vertQuality={}, lodDetail={}",
                dhOn, lodChunks, cpuLoad, verticalQuality, lodDetail);

        // ------------------------------------------------------------------
        // Reflective config writes. The DhApi config tree differs across DH
        // versions; isolate the exact chain here behind try/catch + TODO.
        // Typical (2.x) shape:
        //   DhApi.Delayed.configs.graphics().quality().lodChunkRenderDistanceRadius().setValue(int)
        //   DhApi.Delayed.configs.graphics().cpuLoad()...setValue(...)
        //   DhApi.Delayed.configs.graphics().quality().verticalQuality().setValue(enum)
        // ------------------------------------------------------------------
        try {
            Object configs = resolveConfigsRoot();
            if (configs == null) {
                RiftLog.debug("DhApi configs root not resolved; values logged only (confirm DhApi tree).");
                return;
            }
            // TODO: confirm the exact getter/setter chain on the installed DH version and wire
            //       lodChunkRenderDistanceRadius / cpuLoad / verticalQuality / lodDetail here.
            applyLightingFallbackIfNeeded(configs);
        } catch (Throwable t) {
            RiftLog.error("DH adapter reflective apply failed (non-fatal)", t);
        }
    }

    /** Resolve {@code DhApi.Delayed.configs} (or equivalent) reflectively, or null. */
    private Object resolveConfigsRoot() {
        Class<?> dhApi = Reflect.clazz(DH_API);
        if (dhApi == null) return null;
        // DhApi.Delayed is a static holder in 2.x; "configs" is a public field there.
        Object delayed = Reflect.getStaticField(dhApi, "Delayed"); // TODO: confirm holder name
        if (delayed != null) {
            Object configs = Reflect.invoke(Reflect.method(delayed.getClass(), "configs"), delayed);
            if (configs != null) return configs;
        }
        // Fallback: a direct static "configs" accessor, if present.
        return Reflect.invokeStatic(Reflect.method(dhApi, "configs"));
    }

    /**
     * If shaders are active but DH-aware shader support can't be confirmed, prefer DH "Old
     * Lighting" so LODs are shaded rather than broken.
     */
    private void applyLightingFallbackIfNeeded(Object configs) {
        boolean shadersOn = ModCompat.shadersAvailable();
        boolean dhAwareShader = detectDhAwareShader(); // best-effort
        if (shadersOn && !dhAwareShader) {
            RiftLog.info("Shaders active without confirmed DH support -> requesting DH 'Old Lighting' fallback.");
            // TODO: confirm DhApi lighting/old-lighting config path and set it here.
        }
    }

    /** Heuristic: we cannot reliably introspect a pack's DH support, so assume false (safe). */
    private boolean detectDhAwareShader() {
        return false; // TODO: detect via pack metadata or Oculus/Iris DH-support flag when available
    }
}
