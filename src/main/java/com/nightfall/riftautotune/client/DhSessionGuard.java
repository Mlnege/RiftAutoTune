package com.nightfall.riftautotune.client;

import com.nightfall.riftautotune.RiftConfig;
import com.nightfall.riftautotune.core.DhGuardPolicy;
import com.nightfall.riftautotune.core.DhGuardPolicy.SessionMode;
import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Session-aware Distant Horizons guard (render-thread only).
 *
 * <p>Detects whether this client is in singleplayer, on a remote server, or hosting an open
 * integrated server, and enforces {@link DhGuardPolicy}: minimum DH CPU load in any multiplayer
 * session, a LOD-distance cap while hosting, and a sticky per-world auto-off when FPS stays under
 * the configured floor with DH rendering. The adaptive optimizer can never override the guard
 * because every settings apply is routed through {@link #clamp(GraphicsSettings)}.</p>
 */
public final class DhSessionGuard {

    /** Manual override from /riftautotune dh <on|off|auto>. */
    public enum UserOverride { AUTO, FORCE_ON, FORCE_OFF }

    private static final int MODE_CHECK_INTERVAL_FRAMES = 40;
    private static final int WINDOW_FRAMES = 240;

    private final FrameTimeMonitor monitor = new FrameTimeMonitor(WINDOW_FRAMES);
    private final DhGuardPolicy.AutoOff autoOff = new DhGuardPolicy.AutoOff();

    private SessionMode mode = SessionMode.SINGLEPLAYER;
    private UserOverride override = UserOverride.AUTO;
    private boolean autoDisabled = false;   // sticky until logout or /riftautotune dh on
    private boolean announcedMode = false;
    private int frameCounter = 0;
    private boolean dhAvailable = false;

    public void setDhAvailable(boolean available) {
        this.dhAvailable = available;
    }

    public SessionMode mode() {
        return mode;
    }

    public boolean autoDisabled() {
        return autoDisabled;
    }

    public UserOverride override() {
        return override;
    }

    /** Called on logout: session-scoped state resets so the next world re-evaluates cleanly. */
    public void onSessionEnd() {
        autoDisabled = false;
        announcedMode = false;
        mode = SessionMode.SINGLEPLAYER;
        autoOff.reset();
        monitor.reset();
    }

    public void setOverride(UserOverride value) {
        this.override = value;
        if (value == UserOverride.FORCE_ON) {
            autoDisabled = false;
            autoOff.reset();
        }
    }

    /** Route EVERY settings apply through this so no code path can out-vote the guard. */
    public GraphicsSettings clamp(GraphicsSettings settings) {
        if (!dhAvailable) return settings;
        boolean forcedOff = override == UserOverride.FORCE_OFF
                || (autoDisabled && override != UserOverride.FORCE_ON);
        GraphicsSettings out = DhGuardPolicy.clamp(settings, mode,
                RiftConfig.DH_GUARD.get(),
                RiftConfig.DH_HOST_MAX_LOD_LEVEL.get(),
                forcedOff);
        // FORCE_ON also PINS the LOD distance at >= 1: the adaptive optimizer treats DH as the
        // cheapest visual shed and would otherwise zero it within seconds on a stuttery world
        // (observed: enabled at 64 chunks, adaptively disabled 23s later). With the pin, the
        // optimizer sheds other knobs instead and DH stays visible as the user demanded.
        if (override == UserOverride.FORCE_ON && out.get(Knob.DH_LOD_DISTANCE) == 0) {
            out = (out == settings ? out.copy() : out).set(Knob.DH_LOD_DISTANCE, 1);
        }
        return out;
    }

    /**
     * Per render frame. Detects session-mode changes and runs the auto-off watchdog; when either
     * demands a change, the clamped settings are re-applied through {@code apply}.
     */
    public void onRenderFrame(Minecraft mc, GraphicsSettings current, Consumer<GraphicsSettings> apply) {
        if (!dhAvailable || current == null) return;

        double frameMs = monitor.tick();

        if (++frameCounter >= MODE_CHECK_INTERVAL_FRAMES) {
            frameCounter = 0;
            SessionMode detected = detectMode(mc);
            if (detected != mode) {
                mode = detected;
                announcedMode = false;
                RiftLog.info("DH guard: session mode -> {}", mode);
            }
            // clamp() copies only when a change is required, so instance identity is the signal.
            GraphicsSettings clamped = clamp(current);
            if (clamped != current) {
                if (!announcedMode && DhGuardPolicy.isMultiplayer(mode)) {
                    announcedMode = true;
                    chat(mc, Component.translatable(mode == SessionMode.HOSTING
                            ? "riftautotune.dhguard.hosting"
                            : "riftautotune.dhguard.multiplayer"));
                }
                RiftLog.info("DH guard: enforcing session policy ({}).", mode);
                apply.accept(clamped);
                return;
            }
        }

        // Auto-off watchdog: sustained sub-floor FPS while DH renders -> switch DH off for good.
        if (!RiftConfig.DH_AUTO_OFF.get() || autoDisabled || override != UserOverride.AUTO) return;
        if (frameMs < 0 || monitor.sampleCount() < WINDOW_FRAMES) return;

        double meanMs = monitor.meanMs();
        double avgFps = meanMs > 0 ? 1000.0 / meanMs : 0;
        double onePctMs = monitor.onePctLowMs();
        double onePctFps = onePctMs > 0 ? 1000.0 / onePctMs : avgFps;
        boolean dhRendering = current.get(Knob.DH_LOD_DISTANCE) > 0;

        long holdNanos = RiftConfig.DH_AUTO_OFF_HOLD_SECONDS.get() * 1_000_000_000L;
        if (autoOff.shouldDisable(System.nanoTime(), avgFps, onePctFps, dhRendering,
                RiftConfig.DH_AUTO_OFF_FPS.get(), holdNanos)) {
            autoDisabled = true;
            RiftLog.info("DH guard: auto-off (avg {} fps, 1% {} fps below floor {}).",
                    (int) avgFps, (int) onePctFps, RiftConfig.DH_AUTO_OFF_FPS.get());
            chat(mc, Component.translatable("riftautotune.dhguard.autooff",
                    (int) avgFps, RiftConfig.DH_AUTO_OFF_FPS.get()));
            apply.accept(clamp(current));
        }
    }

    /**
     * Session mode from the client's own state. Hosting = we run the integrated server AND it is
     * open to others (vanilla LAN publish, or any second player - covers Essential invites, whose
     * session wrapper does not always flip the vanilla published flag).
     */
    private SessionMode detectMode(Minecraft mc) {
        if (mc == null || mc.level == null) return SessionMode.SINGLEPLAYER;
        IntegratedServer integrated = mc.getSingleplayerServer();
        if (integrated == null) return SessionMode.REMOTE_MULTIPLAYER;
        try {
            if (integrated.isPublished() || integrated.getPlayerCount() > 1) {
                return SessionMode.HOSTING;
            }
        } catch (Throwable t) {
            // Defensive: any server-state read hiccup falls back to singleplayer handling.
        }
        return SessionMode.SINGLEPLAYER;
    }

    private static void chat(Minecraft mc, Component message) {
        try {
            if (mc != null && mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("[RiftAutoTune] ").append(message), false);
            }
        } catch (Throwable ignored) {
        }
    }

    public String statusLine() {
        return "DH guard: " + mode
                + (autoDisabled ? " (auto-off)" : "")
                + (override != UserOverride.AUTO ? " [" + override + "]" : "");
    }
}
