package com.nightfall.riftautotune.core;

/**
 * Pure policy for the Distant Horizons session guard (no Minecraft imports - unit tested).
 *
 * <p>Three rules, in priority order:</p>
 * <ol>
 *   <li><b>Remote-client CPU protection</b>: connected to a REMOTE server, the DH CPU-load knob is
 *       forced to its minimum. A remote client only builds LODs from chunks the server streams to
 *       it, so extra worker threads buy little and just fight the network/render work.</li>
 *   <li><b>Host steady generation</b>: when this client is also the server (LAN / Essential
 *       invite), DH keeps generating CONTINUOUSLY at the moderate level (CPU load pinned to 1 =
 *       half runtime ratio, cores/4 threads) - never throttled to the starvation minimum, never
 *       running full-bore over the integrated server. The LOD distance is additionally capped so
 *       the integrated server keeps its headroom. (The host is the only machine that can generate
 *       LODs for unexplored terrain; choking it to 1 thread froze distant generation for the whole
 *       session.)</li>
 *   <li><b>Auto-off</b>: if FPS stays under a floor for a sustained hold while DH is rendering,
 *       DH is switched off entirely for the session (weak machines should not pay for LODs).</li>
 * </ol>
 */
public final class DhGuardPolicy {

    /** Where this client session is running. */
    public enum SessionMode {
        SINGLEPLAYER,
        /** Connected to a remote (dedicated or friend-hosted) server. */
        REMOTE_MULTIPLAYER,
        /** This client runs the integrated server and others can join (LAN/Essential). */
        HOSTING
    }

    private DhGuardPolicy() {}

    /** True when the mode is any kind of multiplayer (remote or hosting). */
    public static boolean isMultiplayer(SessionMode mode) {
        return mode == SessionMode.REMOTE_MULTIPLAYER || mode == SessionMode.HOSTING;
    }

    /**
     * Clamp a settings set for the given session. Returns a defensive copy only when a change is
     * required; otherwise returns the input instance unchanged (cheap for the per-apply hot path).
     *
     * @param hostMaxLodLevel highest allowed {@link Knob#DH_LOD_DISTANCE} level while hosting
     * @param dhForcedOff     sticky auto-off (or user off) - forces DH fully disabled
     */
    public static GraphicsSettings clamp(GraphicsSettings s, SessionMode mode,
                                         boolean guardEnabled, int hostMaxLodLevel,
                                         boolean dhForcedOff) {
        if (s == null) return null;
        GraphicsSettings out = s;

        if (dhForcedOff && out.get(Knob.DH_LOD_DISTANCE) > 0) {
            out = out.copy().set(Knob.DH_LOD_DISTANCE, 0);
        }
        if (!guardEnabled || !isMultiplayer(mode)) return out;

        if (mode == SessionMode.REMOTE_MULTIPLAYER) {
            // Remote client: minimum CPU. LODs come from server-streamed chunks anyway.
            if (out.get(Knob.DH_CPU_LOAD) > 0) {
                out = (out == s ? out.copy() : out).set(Knob.DH_CPU_LOAD, 0);
            }
        } else { // HOSTING
            // Essential/LAN host: pin CPU load to the moderate level so distant generation always
            // keeps running (floor: never starved to 0) without going full-bore (cap: never 2)
            // while the integrated server shares the machine.
            if (out.get(Knob.DH_CPU_LOAD) != 1) {
                out = (out == s ? out.copy() : out).set(Knob.DH_CPU_LOAD, 1);
            }
            int cap = Math.max(0, Math.min(hostMaxLodLevel, Knob.DH_LOD_DISTANCE.maxLevel()));
            if (out.get(Knob.DH_LOD_DISTANCE) > cap) {
                out = (out == s ? out.copy() : out).set(Knob.DH_LOD_DISTANCE, cap);
            }
        }
        return out;
    }

    /**
     * Sustained-low-FPS detector for the auto-off rule. Time is injected so tests don't sleep.
     * Uses an exit margin (floor + {@value #EXIT_MARGIN_FPS} fps) so a reading that hovers right
     * at the floor doesn't reset the hold timer and postpone protection forever.
     */
    public static final class AutoOff {
        private static final double EXIT_MARGIN_FPS = 5.0;

        // Explicit armed flag: a raw "0 = not armed" sentinel would mis-arm when the clock
        // legitimately reads 0 (unit tests inject time; nanoTime origin is arbitrary anyway).
        private boolean armed = false;
        private long belowSinceNanos;

        /**
         * @return true when DH should be switched off now (sustained low FPS while DH renders).
         */
        public boolean shouldDisable(long nowNanos, double avgFps, double onePctFps,
                                     boolean dhRendering, int fpsFloor, long holdNanos) {
            if (!dhRendering) {
                armed = false;
                return false;
            }
            boolean below = avgFps < fpsFloor || onePctFps < fpsFloor * 0.55;
            boolean clearlyAbove = avgFps > fpsFloor + EXIT_MARGIN_FPS;
            if (below) {
                if (!armed) {
                    armed = true;
                    belowSinceNanos = nowNanos;
                }
                return nowNanos - belowSinceNanos >= holdNanos;
            }
            if (clearlyAbove) armed = false; // hovering at the floor keeps the timer armed
            return false;
        }

        public void reset() {
            armed = false;
        }
    }
}
