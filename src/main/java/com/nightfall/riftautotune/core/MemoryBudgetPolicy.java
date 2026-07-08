package com.nightfall.riftautotune.core;

/**
 * Pure policy that turns the JVM heap budget ({@code -Xmx}) into hard ceilings for the
 * memory-heavy tuning knobs - no Minecraft imports, unit tested.
 *
 * <p>Why: the optimizer and the adaptive loop chase FPS, but FPS headroom says nothing about
 * heap headroom. A strong GPU on an 8&nbsp;GB heap can hold 100+ FPS at render distance 24 right up
 * until chunk storage + shader targets trigger an OOM. So the knob search space itself is capped
 * by the heap budget: an 8&nbsp;GB client can never be tuned beyond what its heap can carry,
 * no matter what the frame-time measurements suggest.</p>
 *
 * <p>The caps are ceilings, not targets - the optimizer still tunes freely <em>below</em> them.
 * Tier boundaries (in max-heap bytes, from the Nightfall 8&nbsp;GB budget analysis):</p>
 * <ul>
 *   <li>&le; 4.5 GiB &mdash; potato-ish caps (RD 6, 1024 shadow, minimal particles)</li>
 *   <li>&le; 8.5 GiB &mdash; the 8 GB profile (RD 10, 2048 shadow, mipmap 2, Voxy LOD 768 chunks
 *       = sectionRenderDistance 24.0)</li>
 *   <li>&le; 12.5 GiB &mdash; roomy (RD 16, 4096 shadow)</li>
 *   <li>else &mdash; uncapped (the knob tables' own maxima are the only limit)</li>
 * </ul>
 */
public final class MemoryBudgetPolicy {

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final long MIB = 1024L * 1024L;

    /** Upper heap bound (inclusive) of the potato tier: 4.5 GiB. */
    static final long POTATO_MAX_HEAP_BYTES = (long) (4.5 * GIB);
    /** Upper heap bound (inclusive) of the 8 GB-profile tier: 8.5 GiB. */
    static final long PROFILE_8G_MAX_HEAP_BYTES = (long) (8.5 * GIB);
    /** Upper heap bound (inclusive) of the 12 GB tier: 12.5 GiB. */
    static final long PROFILE_12G_MAX_HEAP_BYTES = (long) (12.5 * GIB);

    /**
     * Heap-budget ceilings. All values are inclusive maxima.
     *
     * @param maxRenderDistance        vanilla render distance ceiling, in chunks
     * @param maxShadowRes             shader shadow-map resolution ceiling, in pixels
     * @param maxMipmap                vanilla mipmap-level ceiling (advisory: no adapter writes
     *                                 mipmaps yet; kept here so future adapters share one policy)
     * @param particleLevel            highest {@link Knob#PARTICLES} <em>level</em> allowed
     *                                 (0 = Minimal, 1 = Decreased, 2 = All)
     * @param maxVoxyLodDistance       Voxy LOD render distance ceiling, in chunks
     * @param offHeapCacheBudgetBytes  direct-memory budget for the packed cache backend
     *                                 ({@code core.memcache}) - OUR caches only, not a claim
     *                                 about total direct memory
     */
    public record BudgetCaps(int maxRenderDistance, int maxShadowRes, int maxMipmap,
                             int particleLevel, int maxVoxyLodDistance,
                             long offHeapCacheBudgetBytes) {

        /**
         * The highest level the budget allows for a knob (always &le; {@link Knob#maxLevel()}).
         * Knobs the heap budget does not govern keep their full range.
         */
        public int maxLevelFor(Knob knob) {
            return switch (knob) {
                case RENDER_DISTANCE -> highestLevelAtOrBelow(knob, maxRenderDistance);
                case SHADER_SHADOW_RES -> highestLevelAtOrBelow(knob, maxShadowRes);
                case PARTICLES -> Math.min(Math.max(particleLevel, 0), knob.maxLevel());
                default -> knob.maxLevel();
            };
        }

        /**
         * Lowers every over-budget knob to its budget ceiling. Returns {@code settings}
         * unchanged (same instance) when everything is already within budget, otherwise a
         * clamped copy - the input is never mutated.
         */
        public GraphicsSettings clamp(GraphicsSettings settings) {
            GraphicsSettings out = settings;
            for (Knob k : Knob.values()) {
                int ceiling = maxLevelFor(k);
                if (out.get(k) > ceiling) {
                    if (out == settings) out = settings.copy();
                    out.set(k, ceiling);
                }
            }
            return out;
        }

        /** True when this settings set is already within every budget ceiling. */
        public boolean withinBudget(GraphicsSettings settings) {
            for (Knob k : Knob.values()) {
                if (settings.get(k) > maxLevelFor(k)) return false;
            }
            return true;
        }

        /** Short human-readable summary for logs / the status command. */
        public String describe() {
            return "RD<=" + capStr(maxRenderDistance) + "ch, shadow<=" + capStr(maxShadowRes)
                    + ", mipmap<=" + maxMipmap
                    + ", particlesLvl<=" + Math.min(Math.max(particleLevel, 0), Knob.PARTICLES.maxLevel())
                    + ", voxyLod<=" + maxVoxyLodDistance + "ch"
                    + ", offHeapCache<=" + (offHeapCacheBudgetBytes / MIB) + "MB";
        }

        private static String capStr(int v) {
            return v == Integer.MAX_VALUE ? "uncapped" : String.valueOf(v);
        }

        /** Highest knob level whose real value is at or below the cap (never below level 0). */
        private static int highestLevelAtOrBelow(Knob knob, int capValue) {
            int level = 0;
            for (int lvl = 0; lvl <= knob.maxLevel(); lvl++) {
                if (knob.valueAt(lvl) <= capValue) level = lvl;
            }
            return level;
        }
    }

    // Tier tables. Voxy distances follow the 8 GB budget analysis: 768 chunks is Voxy
    // sectionRenderDistance 24.0 (1 section == 32 chunks), the validated 8 GB horizon.
    private static final BudgetCaps POTATO =
            new BudgetCaps(6, 1024, 1, 0, 128, 128L * MIB);
    private static final BudgetCaps PROFILE_8G =
            new BudgetCaps(10, 2048, 2, 1, 768, 512L * MIB);
    private static final BudgetCaps PROFILE_12G =
            new BudgetCaps(16, 4096, 4, 2, 1024, GIB);
    private static final BudgetCaps UNCAPPED =
            new BudgetCaps(Integer.MAX_VALUE, Integer.MAX_VALUE, 4, Integer.MAX_VALUE,
                    2048, 2L * GIB);

    private static volatile BudgetCaps runtimeCaps;

    private MemoryBudgetPolicy() {}

    /**
     * Caps for a given max heap size. Non-positive (unknown) heap sizes do not cap -
     * same convention as {@link VoxyTuningPolicy#ramDistanceCapChunks(int)}.
     */
    public static BudgetCaps capsFor(long maxHeapBytes) {
        if (maxHeapBytes <= 0) return UNCAPPED;
        if (maxHeapBytes <= POTATO_MAX_HEAP_BYTES) return POTATO;
        if (maxHeapBytes <= PROFILE_8G_MAX_HEAP_BYTES) return PROFILE_8G;
        if (maxHeapBytes <= PROFILE_12G_MAX_HEAP_BYTES) return PROFILE_12G;
        return UNCAPPED;
    }

    /** No ceilings (every knob keeps its full range). Default for legacy call sites/tests. */
    public static BudgetCaps uncapped() {
        return UNCAPPED;
    }

    /**
     * Caps for THIS JVM, derived from {@code Runtime.getRuntime().maxMemory()} once and cached
     * ({@code -Xmx} never changes at runtime). Pure {@code java.lang} - safe in {@code core} -
     * but unit tests should use {@link #capsFor(long)} so they don't depend on the test JVM's heap.
     */
    public static BudgetCaps forCurrentRuntime() {
        BudgetCaps caps = runtimeCaps;
        if (caps == null) {
            caps = capsFor(Runtime.getRuntime().maxMemory());
            runtimeCaps = caps;
        }
        return caps;
    }
}
