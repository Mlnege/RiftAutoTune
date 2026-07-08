package com.nightfall.riftautotune.core.memcache;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;

/**
 * Off-heap packed nibble array: same contract as {@link Packed4BitArray}, backed by a
 * direct {@link ByteBuffer} so the payload lives outside the Java heap (it counts
 * against {@code -XX:MaxDirectMemorySize}, not {@code -Xmx}).
 *
 * <p><b>Lifecycle.</b> Java offers no supported way to free a direct buffer eagerly
 * (invoking the internal cleaner via {@code sun.misc.Unsafe} is explicitly NOT used
 * here). The documented approach this class takes:</p>
 * <ul>
 *   <li>{@link #close()} marks the array closed, drops the only strong reference to the
 *       buffer (best-effort free: the JVM releases the native memory as soon as GC
 *       collects the unreachable buffer), and settles the {@link MemCacheStats} entry
 *       exactly once. After close, {@code get}/{@code set}/{@code fill} throw
 *       {@link IllegalStateException}.</li>
 *   <li>A {@link Cleaner} registration is the leak-safety net: if the caller forgets
 *       {@code close()}, the stats entry still settles when the array is collected, and
 *       the buffer becomes unreachable with it.</li>
 * </ul>
 *
 * <p>Not thread-safe. Allocation can throw {@link OutOfMemoryError} when direct memory
 * is exhausted - use {@link PackedArrays#allocate(int, boolean)} which falls back to a
 * heap array instead of crashing.</p>
 */
public final class OffHeapPacked4BitArray implements Packed4BitCache, AutoCloseable {

    /**
     * Allocation seam so tests can force the {@link OutOfMemoryError} fallback path
     * deterministically (really exhausting direct memory in a unit test is unreliable
     * and slow). Production uses {@link #SYSTEM_ALLOCATOR}.
     */
    @FunctionalInterface
    interface DirectAllocator {
        /** Returns a direct buffer of {@code byteSize} bytes; may throw {@link OutOfMemoryError}. */
        ByteBuffer allocateDirect(int byteSize);
    }

    /** The real thing: {@link ByteBuffer#allocateDirect(int)}. */
    static final DirectAllocator SYSTEM_ALLOCATOR = ByteBuffer::allocateDirect;

    private final int capacity;
    private final Cleaner.Cleanable release;
    /** Null once closed - the closed marker AND the reference drop for best-effort free. */
    private ByteBuffer buffer;

    /** Allocates a direct buffer; records in global stats. May throw {@link OutOfMemoryError}. */
    public OffHeapPacked4BitArray(int capacity) {
        this(capacity, SYSTEM_ALLOCATOR, MemCacheStats.global());
    }

    /** Package-private allocator/stats seam for tests and {@link PackedArrays}. */
    OffHeapPacked4BitArray(int capacity, DirectAllocator allocator, MemCacheStats stats) {
        Packed4BitArray.checkCapacity(capacity);
        this.capacity = capacity;
        // May throw OutOfMemoryError - deliberately BEFORE any stats/cleaner bookkeeping
        // so a failed allocation is never counted.
        this.buffer = allocator.allocateDirect(capacity / 2);
        stats.onAllocate(true, capacity);
        // freeAction captures no reference to this array or the buffer, otherwise the
        // cleaner could never fire. Cleanable.clean() runs it at most once whether
        // triggered by close() or by GC.
        this.release = MemCacheStats.CLEANER.register(this, stats.freeAction(true, capacity));
    }

    @Override
    public int get(int index) {
        ByteBuffer buf = open();
        checkIndex(index);
        return (buf.get(index >> 1) >> ((index & 1) << 2)) & 0xF;
    }

    @Override
    public void set(int index, int value) {
        ByteBuffer buf = open();
        checkIndex(index);
        Packed4BitArray.checkValue(value);
        int byteIndex = index >> 1;
        int shift = (index & 1) << 2;
        buf.put(byteIndex, (byte) ((buf.get(byteIndex) & ~(0xF << shift)) | (value << shift)));
    }

    @Override
    public void fill(int value) {
        ByteBuffer buf = open();
        Packed4BitArray.checkValue(value);
        byte pattern = (byte) ((value << 4) | value);
        for (int i = 0, n = capacity / 2; i < n; i++) {
            buf.put(i, pattern);
        }
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
        return true;
    }

    @Override
    public boolean isClosed() {
        return buffer == null;
    }

    /**
     * Idempotent explicit release: drops the buffer reference (best-effort native free
     * via GC - see class javadoc) and settles stats exactly once via the cleanable.
     */
    @Override
    public void close() {
        buffer = null;
        release.clean();
    }

    private ByteBuffer open() {
        ByteBuffer buf = buffer;
        if (buf == null) {
            throw new IllegalStateException("OffHeapPacked4BitArray is closed");
        }
        return buf;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= capacity) {
            throw new IndexOutOfBoundsException("index " + index + " out of [0, " + capacity + ")");
        }
    }
}
