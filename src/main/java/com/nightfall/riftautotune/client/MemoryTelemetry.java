package com.nightfall.riftautotune.client;

import com.nightfall.riftautotune.RiftConfig;
import com.nightfall.riftautotune.core.MemoryBudgetPolicy;
import com.nightfall.riftautotune.core.memcache.MemCacheStats;
import com.nightfall.riftautotune.util.ModCompat;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Periodic memory snapshot: one compact INFO line every {@code memoryLogIntervalSeconds}
 * (default 60 s, off-able via {@code memoryTelemetry=false}), plus the same snapshot as chat
 * lines for {@code /riftautotune memory}.
 *
 * <p>What it reports - and, honestly, what each number IS:</p>
 * <ul>
 *   <li><b>heap</b> - JVM heap used/max ({@link Runtime}). The whole heap, not something we manage.</li>
 *   <li><b>direct</b> - the JVM-wide "direct" {@link BufferPoolMXBean} (all DirectByteBuffers:
 *       Voxy geometry, Netty, our off-heap caches, ...). Observed, not managed by us.</li>
 *   <li><b>packedCache</b> - {@link MemCacheStats#global()}: bytes of OUR 4-bit packed cache
 *       arrays only, and the bytes they save versus the {@code int[]} each replaces. This is the
 *       only "savings" figure we claim, because it is the only memory we directly manage.</li>
 *   <li><b>clientChunks</b> - loaded client chunk count, or -1 outside a world.</li>
 *   <li><b>voxy</b> - mod presence via {@link ModCompat} only; no reflection into Voxy internals.</li>
 * </ul>
 *
 * <p>Threading: {@link #onRenderFrame()} and {@link #snapshotLines()} run on the render thread
 * (the chunk count is read from the client level; everything else is thread-safe MXBean/adder
 * reads). Client commands also execute on the render thread, so both callers are safe.</p>
 */
public final class MemoryTelemetry {

    private final BufferPoolMXBean directPool = findDirectPool();
    private long lastLogNanos;

    /** Call once per rendered frame on the render thread; logs at the configured interval. */
    public void onRenderFrame() {
        if (!RiftConfig.MEMORY_TELEMETRY.get()) return;
        long intervalNanos = RiftConfig.MEMORY_LOG_INTERVAL_SECONDS.get() * 1_000_000_000L;
        long now = System.nanoTime();
        if (lastLogNanos != 0L && now - lastLogNanos < intervalNanos) return;
        lastLogNanos = now;
        RiftLog.info("Memory: {}", String.join("; ", snapshotLines()));
    }

    /** The snapshot as short lines - shared by the log line and /riftautotune memory. */
    public List<String> snapshotLines() {
        List<String> out = new ArrayList<>();

        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        out.add("heap " + mb(heapUsed) + "/" + mb(rt.maxMemory()) + " MB");

        if (directPool != null) {
            out.add("direct pool " + mb(directPool.getMemoryUsed()) + " MB in "
                    + directPool.getCount() + " buffers (JVM-wide)");
        } else {
            out.add("direct pool unavailable");
        }

        MemCacheStats stats = MemCacheStats.global();
        out.add("packedCache heap=" + mb(stats.heapBytes()) + " MB, direct=" + mb(stats.directBytes())
                + " MB, arrays=" + stats.arrayCount()
                + ", saved=" + mb(stats.savedBytesEstimate()) + " MB vs int[] (our caches only)");

        int chunks = loadedClientChunks();
        out.add("clientChunks=" + chunks + (chunks < 0 ? " (no world)" : ""));

        out.add("voxy=" + (ModCompat.voxyAvailable() ? "present" : "absent")
                + "; budget: " + MemoryBudgetPolicy.forCurrentRuntime().describe());
        return out;
    }

    /** Loaded client chunk count; -1 when no world is open. Render thread only. */
    private static int loadedClientChunks() {
        try {
            ClientLevel level = Minecraft.getInstance().level;
            return level != null ? level.getChunkSource().getLoadedChunksCount() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static BufferPoolMXBean findDirectPool() {
        try {
            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                if ("direct".equals(pool.getName())) return pool;
            }
        } catch (Throwable ignored) {
            // Restricted JVM without the platform MXBean - telemetry degrades, never crashes.
        }
        return null;
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
    }
}
