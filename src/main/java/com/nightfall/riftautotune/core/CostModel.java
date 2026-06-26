package com.nightfall.riftautotune.core;

/**
 * Predicts frame time / FPS for a candidate {@link GraphicsSettings}, anchored on the measured
 * benchmark.
 *
 * <p>Model: frame time is the measured reference frame time plus the difference in "cost above
 * level 0" between the candidate and the reference settings. Each knob's cost per level step is
 * either a value the sweep calibrated on this exact machine, or its {@link Knob#baseCostMs}
 * scaled by a per-tier factor. Inactive knobs (feature absent, or its master toggle off)
 * contribute nothing &mdash; this is what makes toggling shaders or Distant Horizons on/off
 * move the prediction by the right amount.</p>
 */
public final class CostModel {

    private final TuningContext ctx;
    private final double refFrameTimeMs;
    private final double refCostAbove;

    public CostModel(TuningContext ctx) {
        this.ctx = ctx;
        this.refFrameTimeMs = ctx.benchmark.referenceFrameTimeMs();
        this.refCostAbove = costAbove(ctx.benchmark.referenceSettings);
    }

    /** Whether a knob actually does anything given availability and the current master toggles. */
    public boolean isActive(Knob knob, GraphicsSettings s) {
        if (knob.isShaderMaster()) return ctx.shadersAvailable;
        if (knob.isDhMaster()) return ctx.dhAvailable;
        if (knob.requiresShaders) return ctx.shadersAvailable && s.get(Knob.SHADERS) > 0;
        if (knob.requiresDistantHorizons) return ctx.dhAvailable && s.get(Knob.DH_LOD_DISTANCE) > 0;
        return true;
    }

    /** Per-level-step cost in ms: measured if the sweep calibrated it, else tier-scaled default. */
    public double knobCostMs(Knob knob) {
        Double measured = ctx.benchmark.measuredKnobCostMs.get(knob);
        if (measured != null && measured > 0) return measured;
        return knob.baseCostMs * tierFactor(ctx.hardware.tier);
    }

    private static double tierFactor(HardwareTier tier) {
        return switch (tier) {
            case LOW -> 1.70;
            case MEDIUM -> 1.00;
            case HIGH -> 0.65;
            case ULTRA -> 0.45;
        };
    }

    /** Sum of cost contributed above level 0 by every active knob, in ms. */
    public double costAbove(GraphicsSettings s) {
        double total = 0;
        for (Knob k : Knob.values()) {
            if (isActive(k, s)) {
                total += knobCostMs(k) * s.get(k);
            }
        }
        return total;
    }

    public double predictFrameTimeMs(GraphicsSettings s) {
        double ft = refFrameTimeMs + (costAbove(s) - refCostAbove);
        return Math.max(0.5, ft); // cap at ~2000 fps to avoid divide-by-zero nonsense
    }

    public double predictFps(GraphicsSettings s) {
        return 1000.0 / predictFrameTimeMs(s);
    }

    public double predictOnePctLowFps(GraphicsSettings s) {
        return predictFps(s) * ctx.benchmark.onePctRatio();
    }

    /** Perceived visual quality of a settings set (active knobs only). */
    public double visualScore(GraphicsSettings s) {
        double total = 0;
        for (Knob k : Knob.values()) {
            if (isActive(k, s)) {
                total += k.visualWeight * s.get(k);
            }
        }
        return total;
    }

    public TuningContext context() {
        return ctx;
    }
}
