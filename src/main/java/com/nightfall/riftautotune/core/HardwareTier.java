package com.nightfall.riftautotune.core;

/**
 * Hardware classification used to pick a <em>starting</em> graphics preset. Finer-grained than a
 * simple low/medium/high so a borderline machine gets a closer seed and the first benchmark has
 * less work to do.
 *
 * <p>The optimizer never trusts the tier alone &mdash; it always refines against the measured
 * {@link BenchmarkResult} (actual FPS + 1%-low + CPU load). The tier only decides where the
 * search starts.</p>
 *
 * <p>Order matters: {@link #ordinal()} goes from weakest to strongest and {@link #up()}/{@link #down()}
 * walk the ladder. Free of Minecraft/Forge imports so {@code core} stays unit-testable.</p>
 */
public enum HardwareTier {
    MINIMUM,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH,
    ULTRA,
    EXTREME;

    /** @return the next tier up, or {@code this} if already at the top. */
    public HardwareTier up() {
        return ordinal() >= values().length - 1 ? this : values()[ordinal() + 1];
    }

    /** @return the next tier down, or {@code this} if already at the bottom. */
    public HardwareTier down() {
        return ordinal() <= 0 ? this : values()[ordinal() - 1];
    }
}
