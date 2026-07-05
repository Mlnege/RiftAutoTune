package com.nightfall.riftautotune.client;

import com.nightfall.riftautotune.RiftConfig;
import com.nightfall.riftautotune.adapter.AdapterRegistry;
import com.nightfall.riftautotune.adapter.SuperResolutionAdapter;
import com.nightfall.riftautotune.client.gui.ResultsHud;
import com.nightfall.riftautotune.client.gui.ShaderConsentScreen;
import com.nightfall.riftautotune.command.RiftCommands;
import com.nightfall.riftautotune.core.AutoTuneOptimizer;
import com.nightfall.riftautotune.core.BenchmarkResult;
import com.nightfall.riftautotune.core.CostModel;
import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.HardwareProfile;
import com.nightfall.riftautotune.core.HardwareTier;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.core.QualityLadder;
import com.nightfall.riftautotune.core.TuningContext;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Central coordinator. Owns the benchmark, optimizer hand-off, adapters and the adaptive loop,
 * and wires them to the Forge game-event bus. Registered on the Forge bus by the main mod class.
 */
public final class RiftClientManager {

    public static final RiftClientManager INSTANCE = new RiftClientManager();

    private final AdapterRegistry adapters = new AdapterRegistry();
    private final BenchmarkHarness benchmark = new BenchmarkHarness();
    private final AdaptiveController adaptive = new AdaptiveController();
    private final SuperResolutionAdapter superRes = new SuperResolutionAdapter();
    private final DhSessionGuard dhGuard = new DhSessionGuard();

    private HardwareProfile hardware;
    private boolean shadersAvailable;
    private boolean dhAvailable;
    private volatile boolean firstRunHandled = false;
    private volatile boolean tuningInProgress = false;

    private GraphicsSettings current;
    private BenchmarkResult lastResult;
    private HardwareTier forcedTier; // set by /riftautotune profile <tier>

    private RiftClientManager() {}

    // ----------------------------------------------------------------- setup
    public void onClientSetup() {
        RiftLog.DEBUG = RiftConfig.DEBUG_LOGGING.get();
        RiftLog.info("Client setup complete. Target band {}-{} FPS.",
                RiftConfig.TARGET_FPS_MIN.get(), RiftConfig.TARGET_FPS_MAX.get());
    }

    // -------------------------------------------------------------- events
    @SubscribeEvent
    public void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (firstRunHandled) {
            adaptive.reset();
            // Re-entering the game (same client session): re-ask shader consent using the
            // cached tuning result instead of re-benchmarking.
            RiftExecutor.onRenderThread(this::reconfirmShaderConsentForCurrent);
            return;
        }
        firstRunHandled = true;
        RiftExecutor.onRenderThread(this::beginFirstRunFlow);
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        adaptive.reset();
        benchmark.cancel();
        dhGuard.onSessionEnd();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        // Drive the benchmark state machine.
        if (benchmark.isRunning()) {
            benchmark.onRenderFrame();
            return; // don't run the adaptive loop mid-benchmark
        }

        // Pause adaptation while any screen (incl. video settings, the shader consent dialog) is open.
        adaptive.setPaused(mc.screen != null);

        if (current != null && !tuningInProgress) {
            adaptive.onRenderFrame(current, this::applyResolved);
            // Session guard AFTER the adaptive step: it clamps whatever the optimizer chose, so
            // multiplayer CPU protection and the auto-off can never be out-voted.
            dhGuard.onRenderFrame(mc, current, this::applyResolved);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        RiftCommands.register(event.getDispatcher(), this);
    }

    // ----------------------------------------------------------- first run
    private void beginFirstRunFlow() {
        hardware = HardwareDetector.detect();
        shadersAvailable = ModCompat.shadersAvailable();
        dhAvailable = ModCompat.distantHorizonsAvailable();
        adaptive.setEnvironment(hardware, shadersAvailable, dhAvailable);
        dhGuard.setDhAvailable(dhAvailable);

        RiftExecutor.async(() -> {
            ProfileStore.SavedProfile saved = ProfileStore.load();
            if (saved != null && hardware.fingerprint().equals(saved.fingerprint)) {
                GraphicsSettings restored = ProfileStore.toSettings(saved);
                BenchmarkResult approx = new BenchmarkResult(saved.avgFps, saved.onePctLowFps,
                        saved.onePctLowFps * 0.8, 0.9, true,
                        QualityLadder.potatoBaseline(shadersAvailable), null);
                RiftExecutor.onRenderThread(() -> presentAndApply(restored, approx, true));
            } else {
                RiftExecutor.onRenderThread(this::startBenchmark);
            }
        });
    }

    private void startBenchmark() {
        // Bottom-up tuning: always measure at the potato baseline (lowest settings, shaderpack on
        // at its floor profile). The optimizer then only ADDS detail the measurement can afford.
        // A forced tier (/riftautotune profile <tier>) still benchmarks at that preset explicitly.
        GraphicsSettings reference = forcedTier != null
                ? QualityLadder.presetFor(forcedTier)
                : QualityLadder.potatoBaseline(shadersAvailable);
        forceAvailability(reference);
        current = reference;
        tuningInProgress = true;

        // Apply the reference so the measurement reflects a known starting point.
        adapters.applyAll(reference, true);
        toast(Component.translatable("riftautotune.toast.benchmarking"));

        benchmark.begin(reference, RiftConfig.BENCHMARK_SECONDS.get())
                .whenComplete((result, err) -> {
                    if (err != null || result == null) {
                        tuningInProgress = false;
                        RiftLog.warn("Benchmark aborted: {}", err == null ? "no result" : err.toString());
                        return;
                    }
                    lastResult = result;
                    optimizeAndApply(result);
                });
    }

    private void optimizeAndApply(BenchmarkResult result) {
        RiftExecutor.asyncSupply(() -> {
            boolean vsync = readVsync();
            TuningContext ctx = new TuningContext(hardware, result,
                    RiftConfig.TARGET_FPS_MIN.get(), RiftConfig.TARGET_FPS_MAX.get(),
                    RiftConfig.ONE_PCT_FLOOR.get(), RiftConfig.QUALITY_BIAS.get(),
                    shadersAvailable, dhAvailable, vsync);
            return AutoTuneOptimizer.optimize(ctx);
        }).thenAccept(settings -> RiftExecutor.onRenderThread(() ->
                presentAndApply(settings, result, false)));
    }

    // ------------------------------------------------------ shader consent
    /**
     * Gate before shaders are actually enabled: shows hardware specs + predicted performance
     * (with vs without shaders) and only applies shaders once the player accepts. Skipped
     * entirely when shaders aren't available, aren't part of the tuned settings, or the player
     * disabled the prompt in config.
     */
    private void presentAndApply(GraphicsSettings settings, BenchmarkResult referenceResult, boolean isRestore) {
        boolean wantsShaders = shadersAvailable && settings.get(Knob.SHADERS) > 0;
        if (!wantsShaders || !RiftConfig.ASK_SHADER_CONSENT.get()) {
            finishApply(settings, referenceResult, isRestore);
            return;
        }

        GraphicsSettings withoutShaders = settings.copy().set(Knob.SHADERS, 0);
        TuningContext ctx = new TuningContext(hardware, referenceResult,
                RiftConfig.TARGET_FPS_MIN.get(), RiftConfig.TARGET_FPS_MAX.get(),
                RiftConfig.ONE_PCT_FLOOR.get(), RiftConfig.QUALITY_BIAS.get(),
                shadersAvailable, dhAvailable, readVsync());
        CostModel cm = new CostModel(ctx);
        double fpsWith = cm.predictFps(settings);
        double fpsWithout = cm.predictFps(withoutShaders);

        List<Component> lines = buildConsentLines(referenceResult, fpsWith, fpsWithout);
        Minecraft.getInstance().setScreen(new ShaderConsentScreen(lines,
                () -> finishApply(settings, referenceResult, isRestore),
                () -> finishApply(withoutShaders, referenceResult, isRestore)));
    }

    /** Re-shown on every subsequent world join in the same client session (no re-benchmark). */
    private void reconfirmShaderConsentForCurrent() {
        if (current == null || lastResult == null || hardware == null) return;
        if (!shadersAvailable || current.get(Knob.SHADERS) <= 0 || !RiftConfig.ASK_SHADER_CONSENT.get()) {
            return;
        }

        GraphicsSettings withShaders = current;
        GraphicsSettings withoutShaders = current.copy().set(Knob.SHADERS, 0);
        TuningContext ctx = new TuningContext(hardware, lastResult,
                RiftConfig.TARGET_FPS_MIN.get(), RiftConfig.TARGET_FPS_MAX.get(),
                RiftConfig.ONE_PCT_FLOOR.get(), RiftConfig.QUALITY_BIAS.get(),
                shadersAvailable, dhAvailable, readVsync());
        CostModel cm = new CostModel(ctx);
        double fpsWith = cm.predictFps(withShaders);
        double fpsWithout = cm.predictFps(withoutShaders);

        List<Component> lines = buildConsentLines(lastResult, fpsWith, fpsWithout);
        Minecraft.getInstance().setScreen(new ShaderConsentScreen(lines,
                () -> applyRuntimeChoice(withShaders),
                () -> applyRuntimeChoice(withoutShaders)));
    }

    private void applyRuntimeChoice(GraphicsSettings settings) {
        GraphicsSettings clamped = dhGuard.clamp(settings);
        current = clamped;
        RiftExecutor.onRenderThread(() -> adapters.applyAll(clamped, true));
    }

    private List<Component> buildConsentLines(BenchmarkResult baseline, double fpsWith, double fpsWithout) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("riftautotune.consent.spec_gpu", hardware.gpuRenderer));
        lines.add(Component.translatable("riftautotune.consent.spec_hw",
                hardware.vramMb > 0 ? (hardware.vramMb + "MB") : "?",
                hardware.cpuThreads, Math.round(hardware.systemRamMb / 1024.0)));
        lines.add(Component.translatable("riftautotune.consent.spec_display",
                hardware.screenWidth, hardware.screenHeight, hardware.refreshRate));
        lines.add(Component.translatable("riftautotune.consent.spec_tier", tierName()));
        lines.add(Component.empty());
        lines.add(Component.translatable("riftautotune.consent.baseline",
                (int) baseline.avgFps, (int) baseline.onePctLowFps));
        lines.add(Component.translatable("riftautotune.consent.predicted_with", (int) fpsWith));
        lines.add(Component.translatable("riftautotune.consent.predicted_without", (int) fpsWithout));
        return lines;
    }

    private void finishApply(GraphicsSettings settings, BenchmarkResult result, boolean isRestore) {
        settings = dhGuard.clamp(settings);
        current = settings;
        adapters.applyAll(settings, true);
        if (isRestore) {
            toast(Component.translatable("riftautotune.toast.nochange"));
            ResultsHud.showFor(8000);
            RiftLog.info("Restored saved profile (fingerprint match): {}", settings);
        } else {
            // Resolution-aware FSR upscaling: only engages when GPU-bound below the floor.
            superRes.tune(result.avgFps, RiftConfig.TARGET_FPS_MIN.get(), result.gpuBound, result.cpuBound());
            ResultsHud.showFor(10000);
            toast(Component.translatable("riftautotune.toast.applied", tierName(), (int) result.avgFps));
            persist(settings, result);
            RiftLog.info("Applied tuned settings: {}", settings);
        }
        tuningInProgress = false;
        adaptive.reset();
    }

    private void persist(GraphicsSettings settings, BenchmarkResult result) {
        RiftExecutor.async(() -> ProfileStore.save(ProfileStore.build(
                hardware.fingerprint(), tierName(), result.avgFps, result.onePctLowFps, settings)));
    }

    private void applyResolved(GraphicsSettings settings) {
        GraphicsSettings clamped = dhGuard.clamp(settings);
        current = clamped;
        adapters.applyAll(clamped);
    }

    private void forceAvailability(GraphicsSettings s) {
        if (!shadersAvailable) s.set(Knob.SHADERS, 0);
        if (!dhAvailable) s.set(Knob.DH_LOD_DISTANCE, 0);
    }

    private boolean readVsync() {
        try {
            return Minecraft.getInstance().options.enableVsync().get();
        } catch (Throwable t) {
            return false;
        }
    }

    private void toast(Component message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().addToast(new SystemToast(
                    SystemToast.SystemToastIds.TUTORIAL_HINT,
                    Component.translatable("riftautotune.toast.title"), message));
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------ command surface
    public void commandBenchmark() {
        RiftExecutor.onRenderThread(() -> {
            if (hardware == null) beginFirstRunFlow();
            else startBenchmark();
        });
    }

    public void commandApply() {
        if (current != null) RiftExecutor.onRenderThread(() -> adapters.applyAll(current, true));
    }

    public void commandReset() {
        RiftExecutor.async(ProfileStore::delete);
        firstRunHandled = false;
        forcedTier = null;
    }

    public void commandForceProfile(HardwareTier tier) {
        // An explicit manual command is already the user's consent - no dialog.
        forcedTier = tier;
        RiftExecutor.onRenderThread(() -> {
            if (hardware == null) hardware = HardwareDetector.detect();
            GraphicsSettings preset = QualityLadder.presetFor(tier);
            forceAvailability(preset);
            current = preset;
            adapters.applyAll(preset, true);
            ResultsHud.showFor(8000);
            RiftLog.info("Forced profile {}.", tier);
        });
    }

    public List<String> statusLines() {
        List<String> out = new ArrayList<>();
        out.add("Tier: " + (forcedTier != null ? forcedTier + " (forced)" : tierName()));
        out.add("Band: " + RiftConfig.TARGET_FPS_MIN.get() + "-" + RiftConfig.TARGET_FPS_MAX.get()
                + " FPS" + (hardware != null ? " (display " + hardware.refreshRate + "Hz)" : ""));
        if (lastResult != null) {
            out.add(String.format("Benchmark: %.0f avg / %.0f 1%% / %.0f 0.1%%",
                    lastResult.avgFps, lastResult.onePctLowFps, lastResult.pointOnePctLowFps));
        }
        out.add("Shaders: " + (shadersAvailable ? "available" : "absent")
                + ", DH: " + (dhAvailable ? "available" : "absent"));
        out.add("Adaptive: " + (RiftConfig.ENABLE_ADAPTIVE.get() ? "on" : "off")
                + (adaptive.isPaused() ? " (paused)" : ""));
        out.add(dhGuard.statusLine());
        return out;
    }

    /** /riftautotune dh &lt;on|off|auto&gt; - manual override for the DH session guard. */
    public void commandDhOverride(DhSessionGuard.UserOverride mode) {
        dhGuard.setOverride(mode);
        RiftExecutor.onRenderThread(() -> {
            if (current == null) return;
            GraphicsSettings next = current;
            if (mode == DhSessionGuard.UserOverride.FORCE_ON
                    && dhAvailable && next.get(Knob.DH_LOD_DISTANCE) == 0) {
                // Re-enable at a modest distance; the adaptive loop tunes it from there.
                next = next.copy().set(Knob.DH_LOD_DISTANCE, 2);
            }
            applyResolved(next);
        });
    }

    // -------------------------------------------------------------- getters for HUD
    public HardwareProfile hardware() { return hardware; }
    public BenchmarkResult lastResult() { return lastResult; }
    public GraphicsSettings current() { return current; }
    public boolean adaptivePaused() { return adaptive.isPaused(); }

    private String tierName() {
        if (forcedTier != null) return forcedTier.name();
        return hardware != null ? hardware.tier.name() : "UNKNOWN";
    }
}
