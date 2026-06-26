package com.nightfall.riftautotune.client;

import com.nightfall.riftautotune.RiftConfig;
import com.nightfall.riftautotune.core.BenchmarkResult;
import com.nightfall.riftautotune.core.CostModel;
import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.HardwareProfile;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.core.TuningContext;
import com.nightfall.riftautotune.util.RiftLog;

import java.util.function.Consumer;

/**
 * Closed-loop controller that nudges quality one rung at a time to keep FPS in band during play.
 *
 * <p>Stability guarantees:</p>
 * <ul>
 *   <li><b>Hysteresis</b>: only acts after FPS stays out of band for a sustained hold time, so
 *       brief combat/particle dips are ignored.</li>
 *   <li><b>Cooldown</b> between changes and a per-minute change cap to prevent oscillation.</li>
 *   <li><b>Pause</b> when the user edits settings manually.</li>
 * </ul>
 */
public final class AdaptiveController {

    private static final int WINDOW_FRAMES = 240;
    private static final long DOWN_HOLD_NANOS = 4_000_000_000L;  // sustained < low
    private static final long UP_HOLD_NANOS = 8_000_000_000L;    // sustained > high (be lazy going up)
    private static final long COOLDOWN_NANOS = 5_000_000_000L;
    private static final int MAX_CHANGES_PER_MIN = 6;

    private final FrameTimeMonitor monitor = new FrameTimeMonitor(WINDOW_FRAMES);

    private HardwareProfile hardware;
    private boolean shadersAvailable;
    private boolean dhAvailable;

    private boolean paused = false;
    private long belowSince = 0L;
    private long aboveSince = 0L;
    private long lastChange = 0L;
    private long minuteStart = 0L;
    private int changesThisMinute = 0;

    public void setEnvironment(HardwareProfile hardware, boolean shadersAvailable, boolean dhAvailable) {
        this.hardware = hardware;
        this.shadersAvailable = shadersAvailable;
        this.dhAvailable = dhAvailable;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            belowSince = aboveSince = 0L;
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void reset() {
        monitor.reset();
        belowSince = aboveSince = 0L;
    }

    /**
     * Call once per rendered frame on the render thread.
     *
     * @param current current applied settings
     * @param apply   callback that applies a new settings set (adapters, on render thread)
     */
    public void onRenderFrame(GraphicsSettings current, Consumer<GraphicsSettings> apply) {
        if (!RiftConfig.ENABLE_ADAPTIVE.get() || paused || hardware == null) return;

        double frame = monitor.tick();
        if (frame < 0 || monitor.sampleCount() < WINDOW_FRAMES) return; // need a full window

        long now = System.nanoTime();
        if (minuteStart == 0L) minuteStart = now;
        if (now - minuteStart >= 60_000_000_000L) {
            minuteStart = now;
            changesThisMinute = 0;
        }

        double avgFps = 1000.0 / monitor.meanMs();
        int low = RiftConfig.TARGET_FPS_MIN.get();
        int high = Math.min(RiftConfig.TARGET_FPS_MAX.get(), hardware.refreshRate);

        if (avgFps < low) {
            aboveSince = 0L;
            if (belowSince == 0L) belowSince = now;
            if (now - belowSince >= DOWN_HOLD_NANOS && canChange(now)) {
                step(current, apply, true, avgFps);
            }
        } else if (avgFps > high) {
            belowSince = 0L;
            if (aboveSince == 0L) aboveSince = now;
            if (now - aboveSince >= UP_HOLD_NANOS && canChange(now)) {
                step(current, apply, false, avgFps);
            }
        } else {
            belowSince = aboveSince = 0L; // in band, all good
        }
    }

    private boolean canChange(long now) {
        return now - lastChange >= COOLDOWN_NANOS && changesThisMinute < MAX_CHANGES_PER_MIN;
    }

    /** Apply a single best rung change (down = shed quality, up = add quality). */
    private void step(GraphicsSettings current, Consumer<GraphicsSettings> apply, boolean down, double measuredFps) {
        CostModel cm = costModelFor(current, measuredFps);
        Knob best = null;
        double bestRatio = -1;
        double beforeFt = cm.predictFrameTimeMs(current);
        double beforeVis = cm.visualScore(current);

        for (Knob k : Knob.values()) {
            int lvl = current.get(k);
            if (down) {
                if (lvl <= 0) continue;
                GraphicsSettings cand = current.copy().set(k, lvl - 1);
                double saved = beforeFt - cm.predictFrameTimeMs(cand);
                if (saved <= 1e-6) continue;
                double visLost = beforeVis - cm.visualScore(cand);
                double ratio = saved / Math.max(visLost, 1e-6);
                if (ratio > bestRatio) { bestRatio = ratio; best = k; }
            } else {
                if (lvl >= k.maxLevel()) continue;
                if (k.isShaderMaster() && !shadersAvailable) continue;
                if (k.isDhMaster() && !dhAvailable) continue;
                GraphicsSettings cand = current.copy().set(k, lvl + 1);
                double cost = cm.predictFrameTimeMs(cand) - beforeFt;
                double visGain = cm.visualScore(cand) - beforeVis;
                if (visGain <= 1e-6) continue;
                double ratio = visGain / Math.max(cost, 1e-6);
                if (ratio > bestRatio) { bestRatio = ratio; best = k; }
            }
        }

        if (best == null) return;
        GraphicsSettings next = current.copy().set(best, current.get(best) + (down ? -1 : 1));
        lastChange = System.nanoTime();
        changesThisMinute++;
        belowSince = aboveSince = 0L;
        RiftLog.info("Adaptive {} {} (was fps {}).", down ? "DOWN" : "UP", best.displayName, (int) measuredFps);
        apply.accept(next);
    }

    private CostModel costModelFor(GraphicsSettings current, double measuredFps) {
        BenchmarkResult synthetic = new BenchmarkResult(
                measuredFps, measuredFps * 0.9, measuredFps * 0.8, 0.9, true, current, null);
        TuningContext ctx = new TuningContext(hardware, synthetic,
                RiftConfig.TARGET_FPS_MIN.get(), RiftConfig.TARGET_FPS_MAX.get(),
                RiftConfig.ONE_PCT_FLOOR.get(), RiftConfig.QUALITY_BIAS.get(),
                shadersAvailable, dhAvailable, false);
        return new CostModel(ctx);
    }
}
