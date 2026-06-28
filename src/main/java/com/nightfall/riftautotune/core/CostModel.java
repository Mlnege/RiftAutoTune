package com.nightfall.riftautotune.core;

/**
 * Predicts frame time / FPS for a candidate {@link GraphicsSettings}, grounded in the
 * <em>measured</em> benchmark rather than the hardware spec.
 *
 * <p><b>Model.</b> The measured reference frame time is split between a fixed base cost (vanilla
 * overhead that no knob can remove) and the tunable knobs, weighted by {@link Knob#baseCostMs}.
 * The predicted frame time for any settings scales proportionally:</p>
 *
 * <pre>predict(s) = refFrameTimeMs &times; (BASE + costAbove(s)) / (BASE + costAbove(reference))</pre>
 *
 * <p>Because it is anchored on the real measurement, a heavy shaderpack that drags a strong GPU
 * down to 59 FPS is reflected directly: shedding shader knobs predicts a real, proportional FPS
 * gain, so the optimizer actually lowers them instead of assuming "this GPU is fast, leave it
 * maxed". There is deliberately <b>no per-tier cost scaling</b> &mdash; the old tier factor made
 * powerful hardware look like everything was free and is what pinned options too high.</p>
 *
 * <p>When the benchmark reports the machine is CPU-bound, high-{@link Knob#cpuWeight} knobs
 * (simulation distance, DH CPU load, render distance) are made to look more expensive so they are
 * shed before GPU-only effects.</p>
 */
public final class CostModel {

    /** Irreducible base cost (in the same weight units as {@link Knob#baseCostMs}). */
    private static final double BASE_COST = 8.0;
    /** How strongly a CPU-bound result inflates CPU-heavy knobs. */
    private static final double CPU_BIAS = 0.85;

    private final TuningContext ctx;
    private final boolean cpuBound;
    private final double refFrameTimeMs;
    private final double refCostAbove;

    public CostModel(TuningContext ctx) {
        this.ctx = ctx;
        this.cpuBound = ctx.benchmark.cpuBound();
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

    /**
     * Per-level-step cost weight for a knob: measured if the sweep calibrated it, else the knob's
     * {@link Knob#baseCostMs}. Inflated for CPU-heavy knobs when the run was CPU-bound.
     */
    public double knobCostMs(Knob knob) {
        Double measured = ctx.benchmark.measuredKnobCostMs.get(knob);
        double base = (measured != null && measured > 0) ? measured : knob.baseCostMs;
        if (cpuBound) base *= (1.0 + CPU_BIAS * knob.cpuWeight);
        return base;
    }

    /** Sum of cost contributed above level 0 by every active knob. */
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
        double ratio = (BASE_COST + costAbove(s)) / (BASE_COST + refCostAbove);
        return Math.max(0.5, refFrameTimeMs * ratio); // cap at ~2000 fps to avoid nonsense
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

    public boolean isCpuBound() {
        return cpuBound;
    }

    public TuningContext context() {
        return ctx;
    }
}
