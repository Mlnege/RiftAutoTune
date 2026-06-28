package com.nightfall.riftautotune.client;

import com.nightfall.riftautotune.core.BenchmarkResult;
import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.util.CpuLoad;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.concurrent.CompletableFuture;

/**
 * Render-frame-driven benchmark. Waits for chunk loading + Distant Horizons LOD generation to
 * settle (steady state) before sampling, runs a fixed yaw sweep so runs are reproducible, then
 * computes a {@link BenchmarkResult}.
 *
 * <p>Drive it by calling {@link #onRenderFrame()} once per rendered frame from the render thread.
 * The percentile/stability math lives in the pure-logic core and is unit-tested.</p>
 */
public final class BenchmarkHarness {

    private enum Phase { IDLE, WARMUP, SAMPLING, DONE }

    private static final double STEADY_CV_THRESHOLD = 0.25; // frame-time CV considered "settled"
    private static final long MIN_WARMUP_NANOS = 3_000_000_000L; // never sample in the first 3s
    private static final long MAX_WARMUP_NANOS = 20_000_000_000L; // give up waiting after 20s

    private final FrameTimeMonitor warmupMonitor = new FrameTimeMonitor(120);
    private final FrameTimeMonitor sampleMonitor = new FrameTimeMonitor(4096);

    private Phase phase = Phase.IDLE;
    private long phaseStartNanos;
    private long sampleDurationNanos;
    private GraphicsSettings reference = new GraphicsSettings();
    private float sweepYaw;
    private CompletableFuture<BenchmarkResult> future;
    private double cpuSum;
    private int cpuSamples;

    public boolean isRunning() {
        return phase == Phase.WARMUP || phase == Phase.SAMPLING;
    }

    /** Begin a benchmark. Completes the returned future on the render thread when finished. */
    public CompletableFuture<BenchmarkResult> begin(GraphicsSettings referenceSettings, int seconds) {
        this.reference = referenceSettings == null ? new GraphicsSettings() : referenceSettings;
        this.sampleDurationNanos = Math.max(3, seconds) * 1_000_000_000L;
        this.warmupMonitor.reset();
        this.sampleMonitor.reset();
        this.cpuSum = 0;
        this.cpuSamples = 0;
        this.phase = Phase.WARMUP;
        this.phaseStartNanos = System.nanoTime();
        this.future = new CompletableFuture<>();
        LocalPlayer p = Minecraft.getInstance().player;
        this.sweepYaw = p != null ? p.getYRot() : 0f;
        RiftLog.info("Benchmark started: waiting for steady state...");
        return future;
    }

    /** Called once per rendered frame on the render thread. */
    public void onRenderFrame() {
        if (phase == Phase.IDLE || phase == Phase.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return; // not in world yet

        long now = System.nanoTime();
        switch (phase) {
            case WARMUP -> {
                warmupMonitor.tick();
                long elapsed = now - phaseStartNanos;
                boolean settled = elapsed >= MIN_WARMUP_NANOS
                        && warmupMonitor.sampleCount() >= 30
                        && warmupMonitor.coefficientOfVariation() < STEADY_CV_THRESHOLD;
                if (settled || elapsed >= MAX_WARMUP_NANOS) {
                    RiftLog.info("Steady state reached ({} ms warmup) - sampling.", elapsed / 1_000_000);
                    phase = Phase.SAMPLING;
                    phaseStartNanos = now;
                    sampleMonitor.reset();
                }
            }
            case SAMPLING -> {
                sampleMonitor.tick();
                double cpu = CpuLoad.system();
                if (cpu >= 0) { cpuSum += cpu; cpuSamples++; }
                applyCameraSweep(mc);
                if (now - phaseStartNanos >= sampleDurationNanos) {
                    finish();
                }
            }
            default -> { }
        }
    }

    /** Slow, steady yaw rotation so every run scans the same kind of scene. */
    private void applyCameraSweep(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        sweepYaw += 0.6f; // ~deg/frame; full rotation in a few seconds
        if (sweepYaw > 360f) sweepYaw -= 360f;
        p.setYRot(sweepYaw);
        p.setXRot(0f); // level pitch keeps the horizon (and DH LODs) in view
    }

    private void finish() {
        double[] frames = sampleMonitor.snapshot();
        boolean gpuBound = estimateGpuBound(frames);
        double cpuLoad = cpuSamples > 0 ? cpuSum / cpuSamples : -1.0;
        // sweepCosts left null: the cost model grounds knob costs in the measured reference frame
        // time instead. TODO: optionally run a multi-tuple sweep here to calibrate per-knob costs.
        BenchmarkResult result = BenchmarkResult.fromFrameTimes(frames, reference, gpuBound, null, cpuLoad);
        phase = Phase.DONE;
        RiftLog.info("Benchmark done: {}", result);
        if (future != null && !future.isDone()) {
            future.complete(result);
        }
    }

    /**
     * Heuristic GPU/CPU-bound guess. A true classification needs a CPU-only pass; here we use the
     * spread between typical and worst frames as a proxy (CPU stalls produce spikier frame times).
     * TODO: replace with a render-distance-reduced comparison pass.
     */
    private boolean estimateGpuBound(double[] frames) {
        if (frames.length < 10) return true;
        double sum = 0;
        for (double f : frames) sum += f;
        double mean = sum / frames.length;
        if (mean <= 0) return true;
        double var = 0;
        for (double f : frames) {
            double d = f - mean;
            var += d * d;
        }
        double cv = Math.sqrt(var / frames.length) / mean;
        // A steadier frame time (low CV) usually means GPU-bound; spiky means CPU/IO-bound.
        return cv < 0.35;
    }

    public void cancel() {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        phase = Phase.IDLE;
    }
}
