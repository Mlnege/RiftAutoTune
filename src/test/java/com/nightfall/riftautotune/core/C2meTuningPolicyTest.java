package com.nightfall.riftautotune.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C2meTuningPolicyTest {

    @Test
    void minimumTierAlwaysSingleThread() {
        assertEquals(1, C2meTuningPolicy.parallelismFor(HardwareTier.MINIMUM, 32, false, 0));
    }

    @Test
    void staysBelowC2meDefaultOnMidTiers() {
        // 16-thread CPU: C2ME's own Windows default would be ~8 (16/1.6-2).
        assertEquals(4, C2meTuningPolicy.parallelismFor(HardwareTier.MEDIUM, 16, false, 0));
        assertEquals(5, C2meTuningPolicy.parallelismFor(HardwareTier.HIGH, 16, false, 0));
        assertEquals(8, C2meTuningPolicy.parallelismFor(HardwareTier.ULTRA, 16, false, 0));
    }

    @Test
    void cpuBoundShedsOneWorker() {
        assertEquals(4, C2meTuningPolicy.parallelismFor(HardwareTier.HIGH, 16, true, 0));
        assertEquals(1, C2meTuningPolicy.parallelismFor(HardwareTier.MINIMUM, 16, true, 0));
    }

    @Test
    void capsLeaveHeadroomForRenderAndServer() {
        // 4-core machine: auto cap = 2, so even ULTRA can't take more than 2.
        assertEquals(2, C2meTuningPolicy.parallelismFor(HardwareTier.ULTRA, 4, false, 0));
        // user cap wins when tighter
        assertEquals(3, C2meTuningPolicy.parallelismFor(HardwareTier.ULTRA, 32, false, 3));
        // degenerate cores never below 1
        assertEquals(1, C2meTuningPolicy.parallelismFor(HardwareTier.LOW, 1, true, 0));
    }
}
