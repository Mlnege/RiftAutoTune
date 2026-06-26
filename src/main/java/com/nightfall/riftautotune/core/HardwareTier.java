package com.nightfall.riftautotune.core;

/**
 * Coarse hardware classification used to pick a starting graphics preset.
 *
 * <p>The optimizer never trusts the tier alone &mdash; it always refines against the
 * measured {@link BenchmarkResult}. The tier only decides the <em>starting point</em>
 * of the search, which keeps the first benchmark short.</p>
 *
 * <p>This class is intentionally free of any Minecraft/Forge imports so the whole
 * {@code core} package can be unit-tested with plain {@code javac}.</p>
 */
public enum HardwareTier {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA;

    /** @return the next tier up, or {@code this} if already at the top. */
    public HardwareTier up() {
        return this == ULTRA ? ULTRA : values()[ordinal() + 1];
    }

    /** @return the next tier down, or {@code this} if already at the bottom. */
    public HardwareTier down() {
        return this == LOW ? LOW : values()[ordinal() - 1];
    }
}
