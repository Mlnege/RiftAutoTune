package com.nightfall.riftautotune.core;

/**
 * Greedy / hill-climb optimizer, <b>bottom-up</b>: seeds from the potato baseline
 * ({@link QualityLadder#potatoBaseline}) — the lowest settings with the shaderpack kept on at its
 * floor profile — then:
 * <ol>
 *   <li><b>Downgrade</b> (safety net) if even the baseline misses the band floor or the 1%-low
 *       floor, shedding the knob that saves the most frame time per unit of visual quality lost
 *       (this is what can turn the shaderpack fully off on genuinely weak machines).</li>
 *   <li><b>Upgrade</b> to spend the measured headroom, always buying the most visual quality per
 *       ms, stopping at a quality "aim" inside the band that {@code qualityBias} controls.</li>
 * </ol>
 *
 * <p>Detail is only ever <em>added</em> on top of the potato look according to what the measured
 * hardware can actually afford — the per-tier preset is no longer the seed (it pinned options high
 * on strong hardware before the measurement could say otherwise; tiers remain only as the HUD
 * label and the {@code /riftautotune profile} manual override).</p>
 *
 * <p>Deterministic (ties broken by {@link Knob} ordinal), terminating (iteration-capped), and
 * fully unit-testable.</p>
 */
public final class AutoTuneOptimizer {

    private static final int MAX_ITERATIONS = 400;
    private static final double EPS = 1.0e-6;

    private final CostModel model;
    private final TuningContext ctx;

    public AutoTuneOptimizer(CostModel model) {
        this.model = model;
        this.ctx = model.context();
    }

    public GraphicsSettings optimize() {
        GraphicsSettings s = QualityLadder.potatoBaseline(ctx.shadersAvailable);
        forceFeatureAvailability(s);
        // Heap-budget ceilings apply to the seed too (defensive; the baseline is already low).
        s = ctx.budgetCaps.clamp(s);

        // Quality aim inside the band. Higher bias => aim nearer the low edge => more quality kept.
        double aim = ctx.effectiveLow + (1.0 - ctx.qualityBias) * (ctx.effectiveHigh - ctx.effectiveLow);

        downgradePhase(s);
        upgradePhase(s, aim);
        return s;
    }

    private void forceFeatureAvailability(GraphicsSettings s) {
        if (!ctx.shadersAvailable) {
            s.set(Knob.SHADERS, 0);
        }
        if (!ctx.dhAvailable) {
            s.set(Knob.DH_LOD_DISTANCE, 0);
        }
    }

    /** Lower quality until inside the band floor and above the 1%-low floor. */
    private void downgradePhase(GraphicsSettings s) {
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (satisfiesFloors(s)) return;

            Knob best = null;
            double bestRatio = -1;
            double beforeFt = model.predictFrameTimeMs(s);
            double beforeVis = model.visualScore(s);

            for (Knob k : Knob.values()) {
                if (s.get(k) <= 0) continue;
                GraphicsSettings cand = s.copy().set(k, s.get(k) - 1);
                double saved = beforeFt - model.predictFrameTimeMs(cand);   // >0 expected
                double visLost = beforeVis - model.visualScore(cand);       // >=0
                if (saved <= EPS) continue; // no effect (inactive knob) -> skip
                double ratio = saved / Math.max(visLost, EPS);
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    best = k;
                }
            }

            if (best == null) return; // nothing left to lower -> best effort
            s.set(best, s.get(best) - 1);
        }
    }

    /** Raise quality while staying within the band, up to the quality aim. */
    private void upgradePhase(GraphicsSettings s, double aim) {
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (model.predictFps(s) <= aim) return; // reached desired quality point

            Knob best = null;
            double bestRatio = -1;
            double beforeFt = model.predictFrameTimeMs(s);
            double beforeVis = model.visualScore(s);

            for (Knob k : Knob.values()) {
                // Ceiling = knob's own max, lowered by the heap-budget caps (memory headroom
                // is invisible to the FPS signal, so it bounds the search space instead).
                if (s.get(k) >= ctx.budgetCaps.maxLevelFor(k)) continue;
                if (k.isShaderMaster() && !ctx.shadersAvailable) continue;
                if (k.isDhMaster() && !ctx.dhAvailable) continue;

                GraphicsSettings cand = s.copy().set(k, s.get(k) + 1);
                if (!satisfiesFloors(cand)) continue; // would break the band/floor

                double cost = model.predictFrameTimeMs(cand) - beforeFt; // >0 expected
                double visGain = model.visualScore(cand) - beforeVis;    // >=0
                if (visGain <= EPS) continue; // no visual benefit (inactive) -> skip
                double ratio = visGain / Math.max(cost, EPS);
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    best = k;
                }
            }

            if (best == null) return; // no upgrade keeps us in band
            s.set(best, s.get(best) + 1);
        }
    }

    private boolean satisfiesFloors(GraphicsSettings s) {
        return model.predictFps(s) >= ctx.effectiveLow
                && model.predictOnePctLowFps(s) >= ctx.onePctFloor;
    }

    /** Convenience: build the model and run. */
    public static GraphicsSettings optimize(TuningContext ctx) {
        return new AutoTuneOptimizer(new CostModel(ctx)).optimize();
    }
}
