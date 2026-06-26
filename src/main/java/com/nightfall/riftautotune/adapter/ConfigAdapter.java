package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.core.GraphicsSettings;

/**
 * A target we can push resolved {@link GraphicsSettings} into. Implementations must be safe to
 * call when their backing mod is absent ({@link #isAvailable()} == false =&gt; {@link #apply} is
 * a no-op).
 */
public interface ConfigAdapter {

    String name();

    boolean isAvailable();

    /** Apply the relevant slice of the settings. Called on the render thread. */
    void apply(GraphicsSettings settings);
}
