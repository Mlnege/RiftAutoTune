package com.nightfall.riftautotune.core;

import com.nightfall.riftautotune.core.DhGuardPolicy.SessionMode;
import com.nightfall.riftautotune.core.VoxyTuningPolicy.VoxySettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoxyTuningPolicyTest {

    private static final int RAM_64G = 65536;
    private static final int RAM_16G = 16384;
    private static final int RAM_8G = 8192;

    @Test
    void renderDistance256ChunksIsPinnedInEveryModeOnBigRam() {
        for (SessionMode mode : SessionMode.values()) {
            VoxySettings s = VoxyTuningPolicy.compute(mode, 32, false, 256, true, 0, RAM_64G);
            assertEquals(8.0f, s.sectionRenderDistance(), 1e-6,
                    "256 chunks == sectionRenderDistance 8.0 (Voxy stores 32-chunk units), mode " + mode);
        }
    }

    @Test
    void sixteenGigPresetCapsDistanceAndThreads() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 16, false, 256, true, 0, RAM_16G);
        assertEquals(4.0f, s.sectionRenderDistance(), 1e-6, "16GB preset caps 256 -> 128 chunks");
        assertTrue(s.serviceThreads() <= 3, "16GB preset caps ingest workers at 3");
        assertTrue(s.ingestEnabled());
    }

    @Test
    void eightGigPresetIsEvenSmaller() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 8, false, 256, true, 0, RAM_8G);
        assertEquals(2.0f, s.sectionRenderDistance(), 1e-6, "8GB preset caps at 64 chunks");
        assertTrue(s.serviceThreads() <= 2);
        assertTrue(s.serviceThreads() >= 1);
    }

    @Test
    void userConfiguredSmallerDistanceWinsOverRamCap() {
        // Config pin 96 on a 64GB machine: RAM cap (2048) must not RAISE the user's choice.
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 32, false, 96, true, 0, RAM_64G);
        assertEquals(3.0f, s.sectionRenderDistance(), 1e-6);
    }

    @Test
    void unknownRamDoesNotCap() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 32, false, 256, true, 0, 0);
        assertEquals(8.0f, s.sectionRenderDistance(), 1e-6);
    }

    @Test
    void remoteClientSpendsNoCpuWhenCpuOffEnabled() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.REMOTE_MULTIPLAYER, 32, false, 256, true, 0, RAM_64G);
        assertEquals(1, s.serviceThreads());
        assertFalse(s.ingestEnabled(), "remote non-host must not build LODs");
        assertEquals(8.0f, s.sectionRenderDistance(), 1e-6);
    }

    @Test
    void remoteOn16GigStillRendersButSmaller() {
        // The friend's machine: remote client + 16GB -> capped distance AND no CPU spend.
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.REMOTE_MULTIPLAYER, 12, false, 256, true, 0, RAM_16G);
        assertEquals(4.0f, s.sectionRenderDistance(), 1e-6);
        assertEquals(1, s.serviceThreads());
        assertFalse(s.ingestEnabled());
    }

    @Test
    void hostKeepsIngestingWithFewerThreadsThanSingleplayer() {
        VoxySettings sp = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 32, false, 256, true, 0, RAM_64G);
        VoxySettings host = VoxyTuningPolicy.compute(SessionMode.HOSTING, 32, false, 256, true, 0, RAM_64G);
        assertTrue(host.ingestEnabled(), "the host is the machine that builds LODs");
        assertTrue(host.serviceThreads() < sp.serviceThreads(),
                "hosting shares the box with the integrated server");
        assertTrue(sp.serviceThreads() <= 16, "must stay far below Voxy's own cores*2/1.5 default");
    }

    @Test
    void cpuBoundBenchmarkHalvesThreads() {
        VoxySettings idle = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 32, false, 256, true, 0, RAM_64G);
        VoxySettings bound = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 32, true, 256, true, 0, RAM_64G);
        assertEquals(Math.max(1, idle.serviceThreads() / 2), bound.serviceThreads());
    }

    @Test
    void explicitCapWins() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.SINGLEPLAYER, 64, false, 256, true, 3, RAM_64G);
        assertEquals(3, s.serviceThreads());
    }

    @Test
    void tinyCpuNeverGetsZeroThreads() {
        VoxySettings s = VoxyTuningPolicy.compute(SessionMode.REMOTE_MULTIPLAYER, 2, true, 256, false, 0, RAM_8G);
        assertTrue(s.serviceThreads() >= 1);
        assertTrue(s.ingestEnabled(), "remoteCpuOff=false keeps ingest on");
    }
}
