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
}
