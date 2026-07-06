package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.util.RiftLog;

import java.util.List;

/** Holds the adapters and applies settings to all available targets. */
public final class AdapterRegistry {

    private final List<ConfigAdapter> adapters = List.of(
            new VanillaConfigAdapter(),
            new EmbeddiumAdapter(),
            new OculusAdapter(),
            new DistantHorizonsAdapter()
    );

    /**
     * True hands-off for Distant Horizons: skip the adapter entirely (not even a renderingEnabled
     * write) so whatever the player sets in DH's own in-game menu is untouched. Set by
     * {@code /riftautotune dh ignore} to isolate DH-the-mod as a variable when a DH/compat bug is
     * suspected, separate from FORCE_OFF (which actively disables rendering) or AUTO (which still
     * manages DH via the guard/adaptive loop).
     */
    private volatile boolean dhHandsOff = false;

    public void setDhHandsOff(boolean handsOff) {
        this.dhHandsOff = handsOff;
    }

    public void applyAll(GraphicsSettings settings) {
        applyAll(settings, false);
    }

    public void applyAll(GraphicsSettings settings, boolean allowShaderReload) {
        for (ConfigAdapter a : adapters) {
            if (dhHandsOff && a instanceof DistantHorizonsAdapter) {
                RiftLog.debug("skip adapter {} (dh hands-off)", a.name());
                continue;
            }
            if (!a.isAvailable()) {
                RiftLog.debug("skip adapter {} (unavailable)", a.name());
                continue;
            }
            try {
                if (a instanceof OculusAdapter oculus) {
                    oculus.apply(settings, allowShaderReload);
                } else {
                    a.apply(settings);
                }
                RiftLog.debug("applied via {}", a.name());
            } catch (Throwable t) {
                RiftLog.error("adapter " + a.name() + " failed (continuing)", t);
            }
        }
    }

    /**
     * Apply ONLY the Distant Horizons adapter. Used by {@code /riftautotune dh on|off} so toggling
     * DH never runs the Oculus adapter - which would re-write the shader on/off state and yank the
     * user's manually-enabled shaders every time DH is touched.
     */
    public void applyDhOnly(GraphicsSettings settings) {
        if (dhHandsOff) {
            RiftLog.debug("dh-only apply skipped (dh hands-off)");
            return;
        }
        for (ConfigAdapter a : adapters) {
            if (a instanceof DistantHorizonsAdapter && a.isAvailable()) {
                try {
                    a.apply(settings);
                } catch (Throwable t) {
                    RiftLog.error("DH-only apply failed", t);
                }
                return;
            }
        }
    }

    /** When false, the Oculus adapter stops rewriting the pack's per-option .txt (user manual mode). */
    public void setManageShaderOptions(boolean manage) {
        for (ConfigAdapter a : adapters) {
            if (a instanceof OculusAdapter oculus) {
                oculus.setManageOptions(manage);
                return;
            }
        }
    }

    /** Reload the active shaderpack if shaders are on (so DH LOD compiles in) - no enable change. */
    public void reloadShaderForDh() {
        for (ConfigAdapter a : adapters) {
            if (a instanceof OculusAdapter oculus && a.isAvailable()) {
                try {
                    oculus.reloadIfShadersEnabled();
                } catch (Throwable t) {
                    RiftLog.error("shader reload for DH failed", t);
                }
                return;
            }
        }
    }
}
