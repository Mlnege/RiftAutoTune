package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.core.VoxyTuningPolicy.VoxySettings;
import com.nightfall.riftautotune.util.Reflect;
import com.nightfall.riftautotune.util.RiftLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Applies {@link com.nightfall.riftautotune.core.VoxyTuningPolicy} decisions to Voxy, the
 * Connector-loaded fabric LOD renderer the pack owner ported (voxy-0.2.14-alpha).
 *
 * <p>Everything is reached reflectively so RiftAutoTune keeps loading when Voxy is absent. The
 * touch points were verified against the ported source (Downloads/voxy-mc_1201):</p>
 * <ul>
 *   <li>{@code VoxyConfig.CONFIG} static instance with public fields
 *       {@code sectionRenderDistance} (float, 1.0 == 32 chunks), {@code serviceThreads} (int),
 *       {@code ingestEnabled} (boolean) and a no-arg {@code save()}.</li>
 *   <li>{@code VoxyCommon.getInstance().updateDedicatedThreads()} - live thread-pool rebalance,
 *       the same call Voxy's own config screen makes after editing serviceThreads.</li>
 *   <li>{@code IGetVoxyRenderSystem.getNullable().setRenderDistance(float)} - live render
 *       distance apply, again exactly what the config screen does.</li>
 * </ul>
 *
 * <p>Read-before-write on every field: values are only written (and {@code save()}/rebalance only
 * invoked) when something actually changed, so repeated applies never thrash Voxy's thread pools
 * (the DH render-data-reset lesson, applied here from day one). Call {@link #apply} on the render
 * thread.</p>
 */
public final class VoxyAdapter {

    private static final String CONFIG_CLASS = "me.cortex.voxy.client.config.VoxyConfig";
    private static final String COMMON_CLASS = "me.cortex.voxy.commonImpl.VoxyCommon";
    private static final String GET_VRS_CLASS = "me.cortex.voxy.client.core.IGetVoxyRenderSystem";

    public boolean isAvailable() {
        Class<?> c = Reflect.clazz(CONFIG_CLASS);
        return c != null && Reflect.getStaticField(c, "CONFIG") != null;
    }

    /** Apply the computed settings; returns true when anything changed. Render thread only. */
    public boolean apply(VoxySettings settings) {
        try {
            Class<?> cfgClass = Reflect.clazz(CONFIG_CLASS);
            if (cfgClass == null) return false;
            Object cfg = Reflect.getStaticField(cfgClass, "CONFIG");
            if (cfg == null) {
                RiftLog.debug("Voxy CONFIG not initialized yet - skipping apply.");
                return false;
            }

            boolean distChanged = setFloat(cfg, "sectionRenderDistance", settings.sectionRenderDistance());
            boolean thrChanged = setInt(cfg, "serviceThreads", settings.serviceThreads());
            boolean ingChanged = setBool(cfg, "ingestEnabled", settings.ingestEnabled());
            if (!(distChanged || thrChanged || ingChanged)) {
                return false; // nothing to do - no save, no pool rebalance, no renderer poke
            }

            Method save = Reflect.methodByName(cfgClass, "save", 0);
            if (save != null) Reflect.invoke(save, cfg);

            if (thrChanged || ingChanged) {
                Class<?> common = Reflect.clazz(COMMON_CLASS);
                Method getInstance = common != null ? Reflect.methodByName(common, "getInstance", 0) : null;
                Object instance = getInstance != null ? Reflect.invokeStatic(getInstance) : null;
                if (instance != null) {
                    Reflect.invoke(Reflect.methodByName(instance.getClass(), "updateDedicatedThreads", 0), instance);
                }
            }

            if (distChanged) {
                Class<?> iget = Reflect.clazz(GET_VRS_CLASS);
                Method getNullable = iget != null ? Reflect.methodByName(iget, "getNullable", 0) : null;
                Object vrs = getNullable != null ? Reflect.invokeStatic(getNullable) : null;
                if (vrs != null) {
                    Reflect.invoke(Reflect.methodByName(vrs.getClass(), "setRenderDistance", 1),
                            vrs, settings.sectionRenderDistance());
                }
            }

            RiftLog.info("Voxy tuned: renderDistance={} chunks (pinned), serviceThreads={}, ingest={}",
                    Math.round(settings.sectionRenderDistance() * 32),
                    settings.serviceThreads(), settings.ingestEnabled());
            return true;
        } catch (Throwable t) {
            RiftLog.error("Voxy apply failed (non-fatal)", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ field helpers

    private static boolean setFloat(Object target, String name, float value) throws Exception {
        Field f = target.getClass().getField(name);
        float current = f.getFloat(target);
        if (Math.abs(current - value) < 1e-4f) return false;
        f.setFloat(target, value);
        return true;
    }

    private static boolean setInt(Object target, String name, int value) throws Exception {
        Field f = target.getClass().getField(name);
        if (f.getInt(target) == value) return false;
        f.setInt(target, value);
        return true;
    }

    private static boolean setBool(Object target, String name, boolean value) throws Exception {
        Field f = target.getClass().getField(name);
        if (f.getBoolean(target) == value) return false;
        f.setBoolean(target, value);
        return true;
    }
}
