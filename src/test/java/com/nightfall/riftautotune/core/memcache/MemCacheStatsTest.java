package com.nightfall.riftautotune.core.memcache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemCacheStatsTest {

    @Test
    void startsAtZero() {
        MemCacheStats stats = new MemCacheStats();
        assertEquals(0, stats.heapBytes());
        assertEquals(0, stats.directBytes());
        assertEquals(0, stats.totalBytes());
        assertEquals(0, stats.arrayCount());
        assertEquals(0, stats.savedBytesEstimate());
    }

    @Test
    void tracksHeapAllocationUpAndDown() {
        MemCacheStats stats = new MemCacheStats();
        Packed4BitArray a = new Packed4BitArray(4096, stats);
        assertEquals(2048, stats.heapBytes());
        assertEquals(0, stats.directBytes());
        assertEquals(1, stats.arrayCount());
        // Honest math: savings vs the int[4096] baseline = 16384 - 2048 = 14336,
        // counted ONLY for arrays this package manages.
        assertEquals(14336, stats.savedBytesEstimate());

        a.close();
        assertEquals(0, stats.heapBytes());
        assertEquals(0, stats.arrayCount());
        assertEquals(0, stats.savedBytesEstimate());
    }

    @Test
    void tracksDirectAllocationUpAndDown() {
        MemCacheStats stats = new MemCacheStats();
        OffHeapPacked4BitArray a = new OffHeapPacked4BitArray(
                128, OffHeapPacked4BitArray.SYSTEM_ALLOCATOR, stats);
        assertEquals(64, stats.directBytes());
        assertEquals(0, stats.heapBytes());
        assertEquals(64, stats.totalBytes());
        assertEquals(1, stats.arrayCount());
        assertEquals(128L * 4 - 64, stats.savedBytesEstimate());

        a.close();
        assertEquals(0, stats.directBytes());
        assertEquals(0, stats.arrayCount());
        assertEquals(0, stats.savedBytesEstimate());
    }

    @Test
    void mixedPoolsAccumulateIndependently() {
        MemCacheStats stats = new MemCacheStats();
        Packed4BitArray heap = new Packed4BitArray(64, stats);
        OffHeapPacked4BitArray direct = new OffHeapPacked4BitArray(
                32, OffHeapPacked4BitArray.SYSTEM_ALLOCATOR, stats);
        assertEquals(32, stats.heapBytes());
        assertEquals(16, stats.directBytes());
        assertEquals(48, stats.totalBytes());
        assertEquals(2, stats.arrayCount());

        heap.close();
        assertEquals(0, stats.heapBytes());
        assertEquals(16, stats.directBytes());
        assertEquals(1, stats.arrayCount());

        direct.close();
        assertEquals(0, stats.totalBytes());
        assertEquals(0, stats.arrayCount());
    }

    @Test
    void doubleCloseNeverDoubleDecrements() {
        MemCacheStats stats = new MemCacheStats();
        Packed4BitArray heap = new Packed4BitArray(64, stats);
        OffHeapPacked4BitArray direct = new OffHeapPacked4BitArray(
                64, OffHeapPacked4BitArray.SYSTEM_ALLOCATOR, stats);
        heap.close();
        heap.close();
        direct.close();
        direct.close();
        assertEquals(0, stats.heapBytes(), "stats must never go negative");
        assertEquals(0, stats.directBytes());
        assertEquals(0, stats.arrayCount());
        assertEquals(0, stats.savedBytesEstimate());
    }

    @Test
    void freeActionRunsAtMostOnceViaCleanable() {
        // The same Runnable is used by close() and the leak-safety Cleaner; the
        // Cleanable contract (at most once) is what makes the accounting safe. Verify
        // the action itself is symmetric with onAllocate.
        MemCacheStats stats = new MemCacheStats();
        stats.onAllocate(true, 4096);
        Runnable free = stats.freeAction(true, 4096);
        free.run();
        assertEquals(0, stats.directBytes());
        assertEquals(0, stats.arrayCount());
        assertEquals(0, stats.savedBytesEstimate());
    }

    @Test
    void toStringCarriesTheHonestLabel() {
        MemCacheStats stats = new MemCacheStats();
        stats.onAllocate(false, 64);
        String s = stats.toString();
        assertTrue(s.contains("savedVsIntBaseline"), "label must say what the number is: " + s);
        assertTrue(s.contains("arrays=1"), s);
    }
}
