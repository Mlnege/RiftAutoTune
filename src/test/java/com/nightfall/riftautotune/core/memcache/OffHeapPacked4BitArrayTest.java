package com.nightfall.riftautotune.core.memcache;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OffHeapPacked4BitArrayTest {

    private static OffHeapPacked4BitArray offHeap(int capacity, MemCacheStats stats) {
        return new OffHeapPacked4BitArray(capacity, OffHeapPacked4BitArray.SYSTEM_ALLOCATOR, stats);
    }

    @Test
    void roundtripsAllSixteenValuesAtBoundaries() {
        try (OffHeapPacked4BitArray a = offHeap(64, new MemCacheStats())) {
            for (int v = 0; v <= 15; v++) {
                a.set(0, v);
                assertEquals(v, a.get(0), "value " + v + " at index 0");
                a.set(a.capacity() - 1, v);
                assertEquals(v, a.get(a.capacity() - 1), "value " + v + " at last index");
            }
        }
    }

    @Test
    void fuzzMatchesIntArrayReferenceModel() {
        Random rng = new Random(0xD1_5EED);
        int capacity = Packed4BitArray.SECTION_VALUES;
        try (OffHeapPacked4BitArray a = offHeap(capacity, new MemCacheStats())) {
            int[] reference = new int[capacity];
            for (int op = 0; op < 20_000; op++) {
                int index = rng.nextInt(capacity);
                int value = rng.nextInt(16);
                a.set(index, value);
                reference[index] = value;
                int probe = rng.nextInt(capacity);
                assertEquals(reference[probe], a.get(probe), "probe at index " + probe);
            }
            for (int i = 0; i < capacity; i++) {
                assertEquals(reference[i], a.get(i), "final sweep at index " + i);
            }
        }
    }

    @Test
    void fillSetsEverySlot() {
        try (OffHeapPacked4BitArray a = offHeap(32, new MemCacheStats())) {
            a.fill(13);
            for (int i = 0; i < a.capacity(); i++) {
                assertEquals(13, a.get(i));
            }
        }
    }

    @Test
    void rejectsOddCapacityAndBadValuesAndBadIndices() {
        MemCacheStats stats = new MemCacheStats();
        assertThrows(IllegalArgumentException.class,
                () -> offHeap(5, stats));
        assertEquals(0, stats.arrayCount(), "failed constructions must not be counted");
        try (OffHeapPacked4BitArray a = offHeap(8, stats)) {
            assertThrows(IllegalArgumentException.class, () -> a.set(0, 16));
            assertThrows(IllegalArgumentException.class, () -> a.fill(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> a.get(8));
            assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1, 0));
        }
    }

    @Test
    void reportsOffHeapAndExactSizes() {
        try (OffHeapPacked4BitArray a = offHeap(Packed4BitArray.SECTION_VALUES, new MemCacheStats())) {
            assertTrue(a.isOffHeap());
            assertEquals(2048, a.byteSize());
            assertEquals(14336, a.savedBytesVsInt());
        }
    }

    @Test
    void closeIsIdempotentAndPostCloseAccessThrows() {
        MemCacheStats stats = new MemCacheStats();
        OffHeapPacked4BitArray a = offHeap(16, stats);
        a.set(3, 9);
        assertFalse(a.isClosed());
        a.close();
        assertTrue(a.isClosed());
        a.close(); // idempotent
        assertEquals(0, stats.arrayCount(), "close must settle stats exactly once");
        assertEquals(0, stats.directBytes());
        assertThrows(IllegalStateException.class, () -> a.get(3));
        assertThrows(IllegalStateException.class, () -> a.set(3, 1));
        assertThrows(IllegalStateException.class, () -> a.fill(0));
    }

    @Test
    void tryWithResourcesClosesAutomatically() {
        MemCacheStats stats = new MemCacheStats();
        OffHeapPacked4BitArray leaked;
        try (OffHeapPacked4BitArray a = offHeap(16, stats)) {
            leaked = a;
            assertEquals(1, stats.arrayCount());
        }
        assertTrue(leaked.isClosed());
        assertEquals(0, stats.arrayCount());
    }
}
