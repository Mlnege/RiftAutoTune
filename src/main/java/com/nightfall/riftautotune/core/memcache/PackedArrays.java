package com.nightfall.riftautotune.core.memcache;

/**
 * Factory for packed 4-bit cache arrays. This is the entry point cache owners should
 * use: it prefers off-heap storage when asked, but a direct-memory
 * {@link OutOfMemoryError} degrades to a heap array instead of crashing the game -
 * a cache must never take the client down.
 *
 * <p>All allocations report to {@link MemCacheStats#global()} (allocation in the
 * constructors, release via {@code close()}/cleaner), so the HUD/log can show exactly
 * how many bytes of OUR caches are live and what they saved versus {@code int[]}
 * baselines - and nothing more than that.</p>
 */
public final class PackedArrays {

    private PackedArrays() {}

    /**
     * Allocates {@code capacity} 4-bit slots.
     *
     * @param capacity      number of values; must be positive and even
     * @param preferOffHeap try a direct buffer first; on direct-memory OOM this falls
     *                      back to the Java heap silently (never crashes). Note a heap
     *                      fallback that ALSO cannot allocate propagates its
     *                      {@link OutOfMemoryError} - at that point the JVM is dying
     *                      anyway and masking it would only hide the real problem.
     * @throws IllegalArgumentException on odd or non-positive capacity (validated
     *                                  BEFORE any allocation attempt - a bad argument
     *                                  is a bug, not a fallback case)
     */
    public static Packed4BitCache allocate(int capacity, boolean preferOffHeap) {
        return allocate(capacity, preferOffHeap,
                OffHeapPacked4BitArray.SYSTEM_ALLOCATOR, MemCacheStats.global());
    }

    /** Convenience: one 16x16x16 section ({@link Packed4BitArray#SECTION_VALUES} slots). */
    public static Packed4BitCache allocateSection(boolean preferOffHeap) {
        return allocate(Packed4BitArray.SECTION_VALUES, preferOffHeap);
    }

    /** Package-private seam: injectable allocator + stats for deterministic tests. */
    static Packed4BitCache allocate(int capacity, boolean preferOffHeap,
                                    OffHeapPacked4BitArray.DirectAllocator allocator,
                                    MemCacheStats stats) {
        // Validate up front so argument bugs surface as IllegalArgumentException and are
        // never swallowed by the OOM fallback below.
        Packed4BitArray.checkCapacity(capacity);
        if (preferOffHeap) {
            try {
                return new OffHeapPacked4BitArray(capacity, allocator, stats);
            } catch (OutOfMemoryError directExhausted) {
                // Direct memory (-XX:MaxDirectMemorySize) is exhausted. The constructor
                // throws before touching stats, so nothing to unwind - degrade to heap.
            }
        }
        return new Packed4BitArray(capacity, stats);
    }
}
