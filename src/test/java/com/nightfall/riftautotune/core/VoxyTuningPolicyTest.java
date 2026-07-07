package com.nightfall.riftautotune.core;

import com.nightfall.riftautotune.core.DhGuardPolicy.SessionMode;
import com.nightfall.riftautotune.core.VoxyTuningPolicy.VoxySettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoxyTuningPolicyTest {

    @Test
    void renderDistance256ChunksIsPinnedInEveryMode() {
        for (SessionMode mode : SessionMode.values()) {
            VoxySettings s = VoxyTuningPolicy.compute(mode, 32, false, 256, true, 0);
            assertEquals(8.0f, s.sectionRenderDistance(), 1e-6,
                    "256 chunks == sectionRenderDistance 8.0 (Voxy stores 32-chunk units), mode " + mode);
        }
    }

    @Test
    void remoteClientSpendsNoCpuWhenCpuOffEnabled() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.REMOTE_MULTIPLAYER, 32, false, 256, true, 0);
        assertEquals(1, s.serviceThreads());
        assertFalse(s.ingestEnabled(), "remote non-host must not build LODs");
        // The pinned distance survives even in the no-CPU mode: existing LODs still render.
        assertEquals(8.0f, s.sectionRenderDistance(), 1e-6);
    }

    @Test
    void hostKeepsIngestingWithFewerThreadsThanSingleplayer() {
        VoxySettings sp = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 32, false, 256, true, 0);
        VoxySettings host = VoxyTuningPolicy.compute(SessionMode.HOSTING, 32, false, 256, true, 0);
        assertTrue(host.ingestEnabled(), "the host is the machine that builds LODs");
        assertTrue(host.serviceThreads() < sp.serviceThreads(),
                "hosting shares the box with the integrated server");
        assertTrue(sp.serviceThreads() <= 16, "must stay far below Voxy's own cores*2/1.5 default");
    }

    @Test
    void cpuBoundBenchmarkHalvesThreads() {
        VoxySettings idle = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 32, false, 256, true, 0);
        VoxySettings bound = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 32, true, 256, true, 0);
        assertEquals(Math.max(1, idle.serviceThreads() / 2), bound.serviceThreads());
    }

    @Test
    void explicitCapWins() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 64, false, 256, true, 3);
        assertEquals(3, s.serviceThreads());
    }

    @Test
    void tinyCpuNeverGetsZeroThreads() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.REMOTE_MULTIPLAYER, 2, true, 256, false, 0);
        assertTrue(s.serviceThreads() >= 1);
        assertTrue(s.ingestEnabled(), "remoteCpuOff=false keeps ingest on");
    }
}
