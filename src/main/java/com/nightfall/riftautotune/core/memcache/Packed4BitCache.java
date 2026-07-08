package com.nightfall.riftautotune.core.memcache;

/**
 * Common API for a fixed-capacity packed array of 4-bit values (0..15), two values per byte.
 *
 * <p>Implementations: {@link Packed4BitArray} (heap {@code byte[]}) and
 * {@link OffHeapPacked4BitArray} (direct {@link java.nio.ByteBuffer}). Obtain instances via
 * {@link PackedArrays#allocate(int, boolean)} which handles off-heap fallback and stats.</p>
 *
 * <p>Contract, uniform across implementations:</p>
 * <ul>
 *   <li>Indices outside {@code [0, capacity)} throw {@link IndexOutOfBoundsException}.</li>
 *   <li>Values outside {@code [0, 15]} throw {@link IllegalArgumentException} (we throw
 *       rather than clamp: a silently clamped cache value is a correctness bug waiting to
 *       be rendered).</li>
 *   <li>{@link #close()} is idempotent and never throws a checked exception. After close,
 *       {@code get}/{@code set}/{@code fill} throw {@link IllegalStateException}.</li>
 *   <li>Instances are NOT thread-safe; confine each array to one writer at a time.
 *       (The shared {@link MemCacheStats} accounting IS thread-safe.)</li>
 * </ul>
 */
public interface Packed4BitCache extends AutoCloseable {

    /** Smallest storable value. */
    int MIN_VALUE = 0;
    /** Largest storable value (4 bits). */
    int MAX_VALUE = 15;

    /** Returns the value (0..15) at {@code index}. */
    int get(int index);

    /** Stores {@code value} (0..15) at {@code index}. */
    void set(int index, int value);

    /** Sets every slot to {@code value} (0..15). */
    void fill(int value);

    /** Number of 4-bit slots. Always even. */
    int capacity();

    /** Bytes actually used for storage: exactly {@code capacity() / 2}. */
    int byteSize();

    /** True when backed by a direct (off-heap) buffer. */
    boolean isOffHeap();

    /** True once {@link #close()} has been called. */
    boolean isClosed();

    /**
     * Bytes saved versus the {@code int[]} this array replaces:
     * {@code capacity * 4 - capacity / 2} (8x smaller). This counts ONLY this array's own
     * storage - it is a per-array bookkeeping figure, not a JVM-wide savings claim.
     */
    default long savedBytesVsInt() {
        return (long) capacity() * Integer.BYTES - byteSize();
    }

    /** Idempotent; releases storage accounting (and native memory, best effort). */
    @Override
    void close();
}
