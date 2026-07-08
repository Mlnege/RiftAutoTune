package com.nightfall.riftautotune.core.memcache;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Packed4BitArrayTest {

    @Test
    void roundtripsAllSixteenValuesAtBoundaries() {
        MemCacheStats stats = new MemCacheStats();
        Packed4BitArray a = new Packed4BitArray(64, stats);
        for (int v = 0; v <= 15; v++) {
            a.set(0, v);
            assertEquals(v, a.get(0), "value " + v + " at index 0");
            a.set(a.capacity() - 1, v);
            assertEquals(v, a.get(a.capacity() - 1), "value " + v + " at last index");
        }
    }

    @Test
    void adjacentNibblesDoNotBleedIntoEachOther() {
        Packed4BitArray a = new Packed4BitArray(8, new MemCacheStats());
        a.set(2, 15); // low nibble of byte 1
        a.set(3, 0);  // high nibble of byte 1
        assertEquals(15, a.get(2));
        assertEquals(0, a.get(3));
        a.set(3, 9);
        assertEquals(15, a.get(2), "writing the odd slot must not clobber the even slot");
        assertEquals(9, a.get(3));
    }

    @Test
    void fuzzMatchesIntArrayReferenceModel() {
        // Seeded so a failure is reproducible.
        Random rng = new Random(0x5EED_CAFE);
        int capacity = Packed4BitArray.SECTION_VALUES;
        Packed4BitArray a = new Packed4BitArray(capacity, new MemCacheStats());
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

    @Test
    void fillSetsEverySlotAndValidatesValue() {
        Packed4BitArray a = new Packed4BitArray(32, new MemCacheStats());
        a.fill(7);
        for (int i = 0; i < a.capacity(); i++) {
            assertEquals(7, a.get(i));
        }
        a.fill(0);
        for (int i = 0; i < a.capacity(); i++) {
            assertEquals(0, a.get(i));
        }
        assertThrows(IllegalArgumentException.class, () -> a.fill(16));
        assertThrows(IllegalArgumentException.class, () -> a.fill(-1));
    }

    @Test
    void rejectsOddZeroAndNegativeCapacity() {
        MemCacheStats stats = new MemCacheStats();
        assertThrows(IllegalArgumentException.class, () -> new Packed4BitArray(7, stats));
        assertThrows(IllegalArgumentException.class, () -> new Packed4BitArray(0, stats));
        assertThrows(IllegalArgumentException.class, () -> new Packed4BitArray(-4, stats));
        assertEquals(0, stats.arrayCount(), "failed constructions must not be counted");
    }

    @Test
    void outOfRangeValueThrowsNotClamps() {
        Packed4BitArray a = new Packed4BitArray(8, new MemCacheStats());
        a.set(1, 5);
        assertThrows(IllegalArgumentException.class, () -> a.set(1, 16));
        assertThrows(IllegalArgumentException.class, () -> a.set(1, -1));
        assertThrows(IllegalArgumentException.class, () -> a.set(1, Integer.MIN_VALUE));
        assertEquals(5, a.get(1), "rejected writes must leave the slot untouched");
    }

    @Test
    void outOfRangeIndexThrows() {
        Packed4BitArray a = new Packed4BitArray(8, new MemCacheStats());
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(8));
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(8, 0));
    }

    @Test
    void sizeAndSavingsMathIsExact() {
        Packed4BitArray a = new Packed4BitArray(Packed4BitArray.SECTION_VALUES, new MemCacheStats());
        assertEquals(4096, a.capacity());
        assertEquals(2048, a.byteSize(), "capacity/2 bytes");
        // int[4096] = 16384 bytes; packed = 2048 bytes; saved = 14336 (8x smaller).
        assertEquals(16384 - 2048, a.savedBytesVsInt());
        assertFalse(a.isOffHeap());
    }

    @Test
    void closeIsIdempotentAndPostCloseAccessThrows() {
        MemCacheStats stats = new MemCacheStats();
        Packed4BitArray a = new Packed4BitArray(16, stats);
        assertFalse(a.isClosed());
        a.close();
        assertTrue(a.isClosed());
        a.close(); // second close is a no-op
        assertEquals(0, stats.arrayCount(), "close must settle stats exactly once");
        assertThrows(IllegalStateException.class, () -> a.get(0));
        assertThrows(IllegalStateException.class, () -> a.set(0, 1));
        assertThrows(IllegalStateException.class, () -> a.fill(1));
    }
}
