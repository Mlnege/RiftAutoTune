/**
 * 4-bit packed cache primitives for RECOMPUTABLE auxiliary caches only.
 *
 * <p><b>Scope and honesty.</b> This package stores side-cache values that fit in 4 bits
 * (0..15) at 2 bits-per-nibble-pair density: 4096 values (one 16x16x16 section) take
 * 2048 bytes instead of the 16384 bytes an {@code int[4096]} would take (8x smaller).
 * It is a container for <i>derived, recomputable</i> data (e.g. cached light/occlusion
 * levels, LOD quality bands, tint indices) that the game can rebuild at any time.</p>
 *
 * <p>It must <b>never</b> hold authoritative live game state - no {@code LevelChunk},
 * {@code BlockState}, {@code PalettedContainer}, NBT, entity or registry data. Lossy
 * packing of authoritative data corrupts worlds; caches merely get recomputed.</p>
 *
 * <p><b>Savings accounting is local, not global.</b> {@link
 * com.nightfall.riftautotune.core.memcache.MemCacheStats} counts only bytes of arrays
 * allocated through this package, compared against the {@code int[]} each array
 * replaces. It is NOT a claim about total JVM/Minecraft memory usage, and this package
 * does not (and cannot) "quantize the whole heap".</p>
 *
 * <p>Pure Java (java.nio + java.lang.ref). No Minecraft imports - keep it that way so
 * the unit tests in {@code src/test} run without launching the game.</p>
 */
package com.nightfall.riftautotune.core.memcache;
