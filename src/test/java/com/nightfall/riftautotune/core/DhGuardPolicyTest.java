package com.nightfall.riftautotune.core;

import com.nightfall.riftautotune.core.DhGuardPolicy.SessionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DhGuardPolicyTest {

    private static GraphicsSettings settingsWith(int dhDistanceLevel, int dhCpuLevel) {
        GraphicsSettings s = QualityLadder.potatoBaseline(false);
        s.set(Knob.DH_LOD_DISTANCE, dhDistanceLevel);
        s.set(Knob.DH_CPU_LOAD, dhCpuLevel);
        return s;
    }

    @Test
    void remoteMultiplayerForcesCpuLoadToMinimum() {
        GraphicsSettings s = settingsWith(3, 2);
        GraphicsSettings out = DhGuardPolicy.clamp(s, SessionMode.REMOTE_MULTIPLAYER, true, 2, false);
        assertEquals(0, out.get(Knob.DH_CPU_LOAD));
        assertEquals(3, out.get(Knob.DH_LOD_DISTANCE), "remote play only clamps CPU, not distance");
        assertEquals(2, s.get(Knob.DH_CPU_LOAD), "input instance must not be mutated");
    }

    @Test
    void hostingCapsLodDistanceAndCpu() {
        GraphicsSettings s = settingsWith(4, 2);
        GraphicsSettings out = DhGuardPolicy.clamp(s, SessionMode.HOSTING, true, 2, false);
        assertEquals(0, out.get(Knob.DH_CPU_LOAD));
        assertEquals(2, out.get(Knob.DH_LOD_DISTANCE));
    }

    @Test
    void singleplayerIsUntouchedAndSameInstance() {
        GraphicsSettings s = settingsWith(4, 2);
        assertSame(s, DhGuardPolicy.clamp(s, SessionMode.SINGLEPLAYER, true, 2, false));
    }

    @Test
    void guardDisabledLeavesMultiplayerAlone() {
        GraphicsSettings s = settingsWith(4, 2);
        assertSame(s, DhGuardPolicy.clamp(s, SessionMode.HOSTING, false, 2, false));
    }

    @Test
    void forcedOffZeroesDistanceInAnyMode() {
        GraphicsSettings s = settingsWith(3, 1);
        GraphicsSettings out = DhGuardPolicy.clamp(s, SessionMode.SINGLEPLAYER, true, 2, true);
        assertEquals(0, out.get(Knob.DH_LOD_DISTANCE));
    }

    @Test
    void clampReturnsSameInstanceWhenAlreadyCompliant() {
        GraphicsSettings s = settingsWith(2, 0);
        assertSame(s, DhGuardPolicy.clamp(s, SessionMode.HOSTING, true, 2, false));
    }

    @Test
    void autoOffTriggersOnlyAfterSustainedHold() {
        DhGuardPolicy.AutoOff off = new DhGuardPolicy.AutoOff();
        long sec = 1_000_000_000L;
        long hold = 45 * sec;
        assertFalse(off.shouldDisable(0, 20, 15, true, 30, hold), "first low sample arms the timer");
        assertFalse(off.shouldDisable(44 * sec, 20, 15, true, 30, hold), "still inside hold");
        assertTrue(off.shouldDisable(46 * sec, 20, 15, true, 30, hold), "sustained low fires");
    }

    @Test
    void autoOffHoverAtFloorDoesNotResetTimer() {
        DhGuardPolicy.AutoOff off = new DhGuardPolicy.AutoOff();
        long sec = 1_000_000_000L;
        long hold = 45 * sec;
        assertFalse(off.shouldDisable(0, 20, 15, true, 30, hold));
        // 32 fps is above the floor(30) but inside the exit margin (35): timer must stay armed.
        assertFalse(off.shouldDisable(20 * sec, 32, 25, true, 30, hold));
        assertTrue(off.shouldDisable(46 * sec, 20, 15, true, 30, hold));
    }

    @Test
    void autoOffClearRecoveryResetsTimer() {
        DhGuardPolicy.AutoOff off = new DhGuardPolicy.AutoOff();
        long sec = 1_000_000_000L;
        long hold = 45 * sec;
        assertFalse(off.shouldDisable(0, 20, 15, true, 30, hold));
        assertFalse(off.shouldDisable(20 * sec, 60, 45, true, 30, hold), "clear recovery resets");
        assertFalse(off.shouldDisable(46 * sec, 20, 15, true, 30, hold), "timer restarted at 46s");
        assertTrue(off.shouldDisable(92 * sec, 20, 15, true, 30, hold));
    }

    @Test
    void autoOffIgnoredWhileDhNotRendering() {
        DhGuardPolicy.AutoOff off = new DhGuardPolicy.AutoOff();
        long hold = 1L;
        assertFalse(off.shouldDisable(0, 5, 5, false, 30, hold));
        assertFalse(off.shouldDisable(100, 5, 5, false, 30, hold));
    }
}
