package com.nightfall.riftautotune.core.memcache;

import java.lang.ref.Cleaner;
import java.util.Arrays;

/**
 * Heap-backed packed nibble array: {@code capacity} 4-bit values (0..15) in a
 * {@code byte[capacity / 2]}, two values per byte (low nibble = even index).
 *
 * <p>A 16x16x16 section is {@link #SECTION_VALUES} = 4096 values in 2048 bytes, versus
 * 16384 bytes for {@code int[4096]} - 8x smaller. Use it ONLY for recomputable side
 * caches (see {@link com.nightfall.riftautotune.core.memcache package docs}); never for
 * authoritative game state.</p>
 *
 * <p>Reads/writes are plain shift+mask with an explicit bounds check - no branching on
 * the value path beyond validation. Not thread-safe.</p>
 */
public final class Packed4BitArray implements Packed4BitCache {

    /** Values in one 16x16x16 section (16^3), the natural unit for section-shaped caches. */
    public static final int SECTION_VALUES = 4096;

    private final byte[] data;
    private final int capacity;
    private final Cleaner.Cleanable release;
    private boolean closed;

    /** Allocates on the Java heap and records the allocation in the global stats. */
    public Packed4BitArray(int capacity) {
        this(capacity, MemCacheStats.global());
    }

    /** Package-private stats seam for tests and {@link PackedArrays}. */
    Packed4BitArray(int capacity, MemCacheStats stats) {
        checkCapacity(capacity);
        this.capacity = capacity;
        this.data = new byte[capacity / 2];
        stats.onAllocate(false, capacity);
        // The cleaner guarantees the stats entry settles even if the caller forgets
        // close(); heap memory itself is reclaimed by GC as usual.
        this.release = MemCacheStats.CLEANER.register(this, stats.freeAction(false, capacity));
    }

    @Override
    public int get(int index) {
        checkOpen();
        checkIndex(index);
        // Arithmetic shift then mask: sign extension is stripped by the final & 0xF.
        return (data[index >> 1] >> ((index & 1) << 2)) & 0xF;
    }

    @Override
    public void set(int index, int value) {
        checkOpen();
        checkIndex(index);
        checkValue(value);
        int byteIndex = index >> 1;
        int shift = (index & 1) << 2;
        data[byteIndex] = (byte) ((data[byteIndex] & ~(0xF << shift)) | (value << shift));
    }

    @Override
    public void fill(int value) {
        checkOpen();
        checkValue(value);
        Arrays.fill(data, (byte) ((value << 4) | value));
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int byteSize() {
        return capacity / 2;
    }

    @Override
    public boolean isOffHeap() {
        return false;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * Idempotent. Marks the array unusable and settles the stats entry exactly once
     * (further access throws {@link IllegalStateException}, matching the off-heap
     * implementation so callers can treat both uniformly). The bytes themselves are
     * reclaimed by GC.
     */
    @Override
    public void close() {
        closed = true;
        release.clean();
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Packed4BitArray is closed");
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= capacity) {
            throw new IndexOutOfBoundsException("index " + index + " out of [0, " + capacity + ")");
        }
    }

    /** Capacity must be positive and even (two values per byte - no half bytes). */
    static void checkCapacity(int capacity) {
        if (capacity <= 0 || (capacity & 1) != 0) {
            throw new IllegalArgumentException(
                    "capacity must be positive and even, got " + capacity);
        }
    }

    /** Values are 4-bit: throw (never silently clamp) outside 0..15. */
    static void checkValue(int value) {
        if ((value & ~0xF) != 0) {
            throw new IllegalArgumentException("4-bit value out of range 0..15: " + value);
        }
    }
}
