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

    public void applyAll(GraphicsSettings settings) {
        applyAll(settings, false);
    }

    public void applyAll(GraphicsSettings settings, boolean allowShaderReload) {
        for (ConfigAdapter a : adapters) {
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
