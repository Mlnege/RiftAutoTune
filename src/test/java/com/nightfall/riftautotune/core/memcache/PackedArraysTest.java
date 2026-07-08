package com.nightfall.riftautotune.core.memcache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackedArraysTest {

    /** Deterministic stand-in for direct-memory exhaustion (see DirectAllocator seam). */
    private static final OffHeapPacked4BitArray.DirectAllocator ALWAYS_OOM = bytes -> {
        throw new OutOfMemoryError("Direct buffer memory (simulated)");
    };

    @Test
    void prefersOffHeapWhenDirectAllocationSucceeds() {
        MemCacheStats stats = new MemCacheStats();
        try (Packed4BitCache c = PackedArrays.allocate(
                64, true, OffHeapPacked4BitArray.SYSTEM_ALLOCATOR, stats)) {
            assertTrue(c.isOffHeap());
            assertEquals(32, stats.directBytes());
            assertEquals(0, stats.heapBytes());
        }
    }

    @Test
    void heapWhenOffHeapNotPreferred() {
        MemCacheStats stats = new MemCacheStats();
        try (Packed4BitCache c = PackedArrays.allocate(
                64, false, OffHeapPacked4BitArray.SYSTEM_ALLOCATOR, stats)) {
            assertFalse(c.isOffHeap());
            assertEquals(32, stats.heapBytes());
            assertEquals(0, stats.directBytes());
        }
    }

    @Test
    void directOomFallsBackToHeapInsteadOfCrashing() {
        MemCacheStats stats = new MemCacheStats();
        try (Packed4BitCache c = PackedArrays.allocate(64, true, ALWAYS_OOM, stats)) {
            assertFalse(c.isOffHeap(), "OOM'd direct allocation must degrade to heap");
            assertInstanceOf(Packed4BitArray.class, c);
            // The failed off-heap attempt must leave no trace in the stats.
            assertEquals(0, stats.directBytes());
            assertEquals(32, stats.heapBytes());
            assertEquals(1, stats.arrayCount());
            // And the fallback array must actually work.
            c.set(0, 15);
            assertEquals(15, c.get(0));
        }
    }

    @Test
    void badCapacityThrowsInsteadOfFallingBack() {
        MemCacheStats stats = new MemCacheStats();
        // An argument bug must surface as IllegalArgumentException from validation,
        // never be swallowed by the OOM fallback.
        assertThrows(IllegalArgumentException.class,
                () -> PackedArrays.allocate(7, true, ALWAYS_OOM, stats));
        assertThrows(IllegalArgumentException.class,
                () -> PackedArrays.allocate(-2, false, ALWAYS_OOM, stats));
        assertEquals(0, stats.arrayCount());
    }

    @Test
    void publicApiAllocatesAgainstGlobalStats() {
        MemCacheStats global = MemCacheStats.global();
        long baseCount = global.arrayCount();
        long baseHeap = global.heapBytes();
        try (Packed4BitCache c = PackedArrays.allocate(16, false)) {
            assertEquals(baseCount + 1, global.arrayCount());
            assertEquals(baseHeap + 8, global.heapBytes());
        }
        assertEquals(baseCount, global.arrayCount());
        assertEquals(baseHeap, global.heapBytes());
    }

    @Test
    void sectionHelperAllocates4096Slots() {
        try (Packed4BitCache c = PackedArrays.allocateSection(false)) {
            assertEquals(Packed4BitArray.SECTION_VALUES, c.capacity());
            assertEquals(2048, c.byteSize());
        }
    }
}
