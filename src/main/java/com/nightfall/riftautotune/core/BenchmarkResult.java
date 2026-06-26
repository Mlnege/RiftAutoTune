package com.nightfall.riftautotune.core;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Outcome of a benchmark run: headline FPS figures, percentiles, a stability score, a
 * GPU/CPU-bound hint, the settings that were active while measuring, and any per-knob costs
 * the sweep was able to calibrate on this machine.
 *
 * <p>All math is dependency-free so it can be unit-tested.</p>
 */
public final class BenchmarkResult {

    public final double avgFps;
    public final double onePctLowFps;
    public final double pointOnePctLowFps;
    /** 0..1, higher is steadier (1 - coefficient of variation of frame time). */
    public final double stabilityScore;
    public final boolean gpuBound;
    /** Settings that were active while the sample was taken (the cost-model reference point). */
    public final GraphicsSettings referenceSettings;
    /** Optional per-knob measured cost in ms per level step (from the sweep). May be empty. */
    public final Map<Knob, Double> measuredKnobCostMs;

    public BenchmarkResult(double avgFps, double onePctLowFps, double pointOnePctLowFps,
                           double stabilityScore, boolean gpuBound,
                           GraphicsSettings referenceSettings,
                           Map<Knob, Double> measuredKnobCostMs) {
        this.avgFps = avgFps;
        this.onePctLowFps = onePctLowFps;
        this.pointOnePctLowFps = pointOnePctLowFps;
        this.stabilityScore = stabilityScore;
        this.gpuBound = gpuBound;
        this.referenceSettings = referenceSettings == null ? new GraphicsSettings() : referenceSettings;
        this.measuredKnobCostMs = measuredKnobCostMs == null
                ? new EnumMap<>(Knob.class) : new EnumMap<>(measuredKnobCostMs);
    }

    /** Ratio of 1%-low to average; used to predict 1%-low at other settings. */
    public double onePctRatio() {
        return avgFps <= 0 ? 0.85 : Math.min(1.0, onePctLowFps / avgFps);
    }

    public double referenceFrameTimeMs() {
        return avgFps <= 0 ? 1000.0 : 1000.0 / avgFps;
    }

    /**
     * Build a result from raw per-frame times (ms). Computes avg, 1% / 0.1% lows, and stability.
     *
     * @param frameTimesMs frame times in milliseconds (warm-up frames already excluded)
     * @param reference    the settings active while sampling
     * @param gpuBound     whether the run was judged GPU-bound
     * @param sweepCosts   optional calibrated per-knob costs, may be {@code null}
     */
    public static BenchmarkResult fromFrameTimes(double[] frameTimesMs, GraphicsSettings reference,
                                                 boolean gpuBound, Map<Knob, Double> sweepCosts) {
        if (frameTimesMs == null || frameTimesMs.length == 0) {
            return new BenchmarkResult(0, 0, 0, 0, gpuBound, reference, sweepCosts);
        }
        double[] sorted = frameTimesMs.clone();
        Arrays.sort(sorted); // ascending frame time => ascending "fastness" of frames

        double sum = 0;
        for (double v : sorted) sum += v;
        double meanMs = sum / sorted.length;
        double avgFps = meanMs <= 0 ? 0 : 1000.0 / meanMs;

        double onePctLowFps = lowFps(sorted, 0.01);
        double pointOnePctLowFps = lowFps(sorted, 0.001);

        // stability = 1 - (stddev / mean), clamped.
        double varSum = 0;
        for (double v : sorted) {
            double d = v - meanMs;
            varSum += d * d;
        }
        double stdDev = Math.sqrt(varSum / sorted.length);
        double stability = meanMs <= 0 ? 0 : Math.max(0.0, Math.min(1.0, 1.0 - (stdDev / meanMs)));

        return new BenchmarkResult(avgFps, onePctLowFps, pointOnePctLowFps, stability,
                gpuBound, reference, sweepCosts);
    }

    /** Mean frame time of the slowest {@code fraction} of frames, expressed as FPS. */
    private static double lowFps(double[] ascendingFrameTimes, double fraction) {
        int n = ascendingFrameTimes.length;
        int count = Math.max(1, (int) Math.ceil(n * fraction));
        // slowest frames are the largest frame times -> tail of the ascending array
        double sum = 0;
        for (int i = n - count; i < n; i++) {
            sum += ascendingFrameTimes[i];
        }
        double meanWorst = sum / count;
        return meanWorst <= 0 ? 0 : 1000.0 / meanWorst;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "BenchmarkResult{avg=%.1f, 1%%=%.1f, 0.1%%=%.1f, stability=%.2f, gpuBound=%s}",
                avgFps, onePctLowFps, pointOnePctLowFps, stabilityScore, gpuBound);
    }
}
