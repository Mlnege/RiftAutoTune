package com.nightfall.riftautotune.core.memcache;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe accounting for every packed cache array this package manages.
 *
 * <p><b>Honest math.</b> {@link #savedBytesEstimate()} is the sum, over LIVE arrays
 * allocated through this package, of {@code capacity * 4 - capacity / 2} - i.e. bytes
 * saved versus the plain {@code int[]} each array replaces. It counts ONLY our own
 * arrays. It says nothing about total JVM, Minecraft, or native memory, and must never
 * be reported as such.</p>
 *
 * <p>Backed by {@link LongAdder}s: allocation/free events may come from any thread
 * (including the {@link Cleaner} thread) with negligible contention.</p>
 *
 * <p>Production code uses the {@link #global()} instance; tests construct isolated
 * instances via the package-private constructor.</p>
 */
public final class MemCacheStats {

    /**
     * One shared cleaner (one daemon thread) for the whole package. It guarantees that a
     * forgotten array still settles its stats entry (and, for off-heap arrays, that the
     * direct buffer becomes unreachable) when the array is garbage collected.
     */
    static final Cleaner CLEANER = Cleaner.create();

    private static final MemCacheStats GLOBAL = new MemCacheStats();

    /** The process-wide instance production allocations report to. */
    public static MemCacheStats global() {
        return GLOBAL;
    }

    private final LongAdder heapBytes = new LongAdder();
    private final LongAdder directBytes = new LongAdder();
    private final LongAdder arrayCount = new LongAdder();
    private final LongAdder savedBytes = new LongAdder();

    /** Package-private: use {@link #global()} in production; tests may isolate. */
    MemCacheStats() {}

    /** Records one successful allocation of {@code capacity} 4-bit slots. */
    void onAllocate(boolean offHeap, int capacity) {
        long bytes = capacity / 2;
        (offHeap ? directBytes : heapBytes).add(bytes);
        arrayCount.increment();
        savedBytes.add((long) capacity * Integer.BYTES - bytes);
    }

    /**
     * Returns the release action for one allocation. Registered with the {@link #CLEANER}
     * AND invoked by explicit {@code close()}; {@link Cleaner.Cleanable#clean()} runs it
     * at most once, so double-close / close-then-GC can never double-decrement.
     * The returned runnable captures no array reference (a self-reference would make the
     * cleaner registration useless).
     */
    Runnable freeAction(boolean offHeap, int capacity) {
        return () -> {
            long bytes = capacity / 2;
            (offHeap ? directBytes : heapBytes).add(-bytes);
            arrayCount.decrement();
            savedBytes.add(-((long) capacity * Integer.BYTES - bytes));
        };
    }

    /** Live heap bytes held by our packed arrays. */
    public long heapBytes() {
        return heapBytes.sum();
    }

    /** Live direct (off-heap) bytes held by our packed arrays. */
    public long directBytes() {
        return directBytes.sum();
    }

    /** Live heap + direct bytes held by our packed arrays. */
    public long totalBytes() {
        return heapBytes() + directBytes();
    }

    /** Number of live (not yet closed/collected) arrays. */
    public long arrayCount() {
        return arrayCount.sum();
    }

    /**
     * Bytes saved versus {@code int[]} baselines, summed over live arrays allocated
     * through this package only. NOT a JVM-wide figure - see class javadoc.
     */
    public long savedBytesEstimate() {
        return savedBytes.sum();
    }

    @Override
    public String toString() {
        return "MemCacheStats{arrays=" + arrayCount()
                + ", heapBytes=" + heapBytes()
                + ", directBytes=" + directBytes()
                + ", savedVsIntBaseline=" + savedBytesEstimate() + "}";
    }
}
