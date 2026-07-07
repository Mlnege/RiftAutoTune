package com.nightfall.riftautotune.core;

import com.nightfall.riftautotune.core.DhGuardPolicy.SessionMode;

/**
 * Pure policy for tuning Voxy (the Connector-loaded fabric LOD renderer) - no Minecraft imports,
 * unit tested.
 *
 * <p>Design goals, per the pack owner's request:</p>
 * <ol>
 *   <li><b>Render distance is PINNED</b> (default 256 chunks) in every mode - the whole point of
 *       Voxy is the horizon, so the optimizer never trades it away. Voxy stores distance as
 *       {@code sectionRenderDistance} where 1.0 == 32 chunks (verified against the ported source:
 *       the config screen displays {@code sectionRenderDistance * 32} as chunks).</li>
 *   <li><b>Threads are the tuned knob.</b> Voxy's own default is {@code cores * 2 / 1.5}
 *       (= 42 threads on a 7950X3D!) which violates the pack's "never overload the machine" rule.
 *       We size conservatively from the benchmark: singleplayer cores/4, hosting cores/6 (the
 *       integrated server shares the box), halved again when the benchmark says the run is
 *       CPU-bound.</li>
 *   <li><b>Multiplayer: only the host spends CPU.</b> A REMOTE client gets serviceThreads=1 and
 *       ingest disabled - previously built LODs still render (that is GPU work), but no CPU is
 *       spent building new ones. The HOSTING machine keeps ingesting so the world's LODs keep
 *       growing for everyone's future sessions.</li>
 * </ol>
 */
public final class VoxyTuningPolicy {

    /** Mirror of the Voxy config fields RiftAutoTune manages. */
    public record VoxySettings(float sectionRenderDistance, int serviceThreads, boolean ingestEnabled) {}

    private VoxyTuningPolicy() {}

    /** Voxy stores render distance in 32-chunk units: 256 chunks -> 8.0f. */
    public static float chunksToSections(int chunks) {
        return Math.max(32, chunks) / 32.0f;
    }

    /**
     * RAM preset: caps the pinned render distance on smaller machines. Voxy's geometry buffers,
     * ingest queues and storage cache all scale with the visible area, and its geometry capacity
     * is hardcoded - render distance is the one real memory lever. A 16 GB machine also carries
     * Windows + the 10 GB-heap client, so off-heap headroom is thin.
     */
    public static int ramDistanceCapChunks(int systemRamMb) {
        if (systemRamMb <= 0) return 2048;      // unknown -> don't cap (benchmark machines report real values)
        if (systemRamMb >= 28_000) return 2048; // 32 GB+: effectively uncapped
        if (systemRamMb >= 20_000) return 256;  // 24 GB
        if (systemRamMb >= 12_000) return 128;  // 16 GB - the smooth-on-16GB preset
        return 64;                              // 8 GB
    }

    /** RAM preset: small machines also get fewer ingest workers (less native/queue churn). */
    public static int ramThreadCap(int systemRamMb) {
        if (systemRamMb <= 0 || systemRamMb >= 28_000) return Integer.MAX_VALUE;
        if (systemRamMb >= 20_000) return 6;
        if (systemRamMb >= 12_000) return 3;    // 16 GB
        return 2;                               // 8 GB
    }

    /**
     * @param pinnedRenderDistanceChunks render distance to hold in ALL modes (chunks, e.g. 256);
     *                                   the RAM preset may cap it on smaller machines
     * @param remoteCpuOff               when true, a remote-multiplayer client builds no LODs
     * @param maxThreadsCap              hard cap from config; 0 = automatic (cores / 2)
     * @param systemRamMb                total system RAM; drives the small-machine preset
     */
    public static VoxySettings compute(SessionMode mode, int cpuThreads, boolean cpuBound,
                                       int pinnedRenderDistanceChunks, boolean remoteCpuOff,
                                       int maxThreadsCap, int systemRamMb) {
        int distance = Math.min(pinnedRenderDistanceChunks, ramDistanceCapChunks(systemRamMb));
        float srd = chunksToSections(distance);
        int cores = Math.max(1, cpuThreads);
        int cap = maxThreadsCap > 0 ? maxThreadsCap : Math.max(1, cores / 2);
        cap = Math.min(cap, ramThreadCap(systemRamMb));

        if (mode == SessionMode.REMOTE_MULTIPLAYER && remoteCpuOff) {
            // Not the host: render existing LODs but never spend CPU building new ones.
            return new VoxySettings(srd, 1, false);
        }

        int base = switch (mode) {
            case SINGLEPLAYER -> Math.max(2, cores / 4);
            case HOSTING -> Math.max(2, cores / 6);           // integrated server shares the machine
            case REMOTE_MULTIPLAYER -> Math.max(1, cores / 8); // remoteCpuOff=false fallback
        };
        if (cpuBound) {
            base = Math.max(1, base / 2);
        }
        return new VoxySettings(srd, Math.max(1, Math.min(base, cap)), true);
    }
}
