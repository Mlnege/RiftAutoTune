package com.nightfall.riftautotune.core;

/**
 * One tunable graphics setting ("knob") with discrete quality levels and weights that drive
 * the optimizer.
 *
 * <ul>
 *   <li>{@code values} &mdash; the real value at each level (e.g. render distance in chunks).
 *       {@code level 0} is always the cheapest/lowest quality.</li>
 *   <li>{@code visualWeight} &mdash; perceived visual gain per level step. Higher = more
 *       important to keep.</li>
 *   <li>{@code baseCostMs} &mdash; relative frame-time cost weight per level step. The
 *       {@link CostModel} no longer scales this by a hardware tier; instead it grounds the
 *       absolute scale in the <em>measured</em> benchmark frame time, so a heavy shaderpack
 *       (e.g. Spooklementary / Complementary) is reflected by the real measurement rather than
 *       assumed cheap on strong GPUs.</li>
 *   <li>{@code cpuWeight} &mdash; 0..1, how much this knob loads the CPU / main thread. When a
 *       benchmark reports the machine is CPU-bound the cost model inflates high-{@code cpuWeight}
 *       knobs so the optimizer sheds simulation distance / DH CPU load before GPU-only effects.</li>
 *   <li>{@code requiresShaders}/{@code requiresDistantHorizons} &mdash; the knob only does
 *       anything when that feature is present and its master toggle is on.</li>
 * </ul>
 *
 * <p>Knobs deliberately ordered so {@code ordinal()} gives deterministic tie-breaking.
 * "Cheap visual, high cost" knobs (shadow-map resolution, DH CPU load) carry a high
 * {@code baseCostMs} relative to {@code visualWeight} so the optimizer sheds them first.</p>
 */
public enum Knob {

    // ---- Vanilla ---------------------------------------------------------------------------
    //                    displayName              values                                  vis   cost  cpu   shd    dh
    RENDER_DISTANCE      ("Render Distance",      new int[]{4, 6, 8, 10, 12, 16, 24, 32}, 1.40, 0.45, 0.85, false, false),
    SIMULATION_DISTANCE  ("Simulation Distance",  new int[]{4, 6, 8, 10, 12},             0.45, 0.50, 1.00, false, false),
    ENTITY_DISTANCE      ("Entity Distance %",    new int[]{50, 75, 100, 150, 200},       0.45, 0.30, 0.60, false, false),
    GRAPHICS_MODE        ("Graphics",             new int[]{0, 1, 2},                     1.00, 0.80, 0.20, false, false),
    BIOME_BLEND          ("Biome Blend",          new int[]{0, 1, 2, 3},                  0.20, 0.25, 0.50, false, false),
    CLOUDS               ("Clouds",               new int[]{0, 1, 2},                     0.30, 0.25, 0.15, false, false),
    PARTICLES            ("Particles",            new int[]{0, 1, 2},                     0.30, 0.20, 0.50, false, false),

    // ---- Shaders (Oculus) ------------------------------------------------------------------
    // Master toggle. Gated by HardwareProfile/shader availability in CostModel, not here.
    SHADERS              ("Shaders",              new int[]{0, 1},                        8.00, 6.00, 0.05, false, false),
    SHADER_SHADOW_RES    ("Shadow Map Res",       new int[]{512, 1024, 2048, 4096},       1.20, 1.60, 0.10, true,  false),
    SHADER_SHADOW_DIST   ("Shadow Distance",      new int[]{0, 1, 2, 3},                  0.60, 0.80, 0.10, true,  false),
    SHADER_VOLUMETRIC    ("Volumetric Fog",       new int[]{0, 1, 2, 3},                  0.90, 1.10, 0.05, true,  false),
    SHADER_SSAO          ("SSAO",                 new int[]{0, 1, 2},                     0.50, 0.60, 0.05, true,  false),
    SHADER_BLOOM         ("Bloom",                new int[]{0, 1},                        0.40, 0.30, 0.05, true,  false),

    // ---- Distant Horizons ------------------------------------------------------------------
    // Master toggle (level 0 == DH off). Gated by DH availability in CostModel.
    DH_LOD_DISTANCE      ("DH LOD Distance",      new int[]{0, 64, 128, 256, 512},        2.00, 1.00, 0.70, false, true),
    DH_CPU_LOAD          ("DH CPU Load",          new int[]{0, 1, 2},                     0.20, 1.30, 1.00, false, true),
    DH_VERTICAL_QUALITY  ("DH Vertical Quality",  new int[]{0, 1, 2},                     0.50, 0.60, 0.40, false, true),
    DH_LOD_DETAIL        ("DH LOD Detail",        new int[]{0, 1, 2},                     0.60, 0.70, 0.50, false, true);

    public final String displayName;
    private final int[] values;
    public final double visualWeight;
    public final double baseCostMs;
    /** 0..1 share of this knob's cost that lands on the CPU / main thread. */
    public final double cpuWeight;
    public final boolean requiresShaders;
    public final boolean requiresDistantHorizons;

    Knob(String displayName, int[] values, double visualWeight, double baseCostMs,
         double cpuWeight, boolean requiresShaders, boolean requiresDistantHorizons) {
        this.displayName = displayName;
        this.values = values;
        this.visualWeight = visualWeight;
        this.baseCostMs = baseCostMs;
        this.cpuWeight = cpuWeight;
        this.requiresShaders = requiresShaders;
        this.requiresDistantHorizons = requiresDistantHorizons;
    }

    public int levelCount() {
        return values.length;
    }

    public int maxLevel() {
        return values.length - 1;
    }

    /** The real value (chunks, percent, enum ordinal, ...) at the given quality level. */
    public int valueAt(int level) {
        return values[clampLevel(level)];
    }

    public int clampLevel(int level) {
        if (level < 0) return 0;
        if (level > maxLevel()) return maxLevel();
        return level;
    }

    /** True if this knob is the master toggle for shaders. */
    public boolean isShaderMaster() {
        return this == SHADERS;
    }

    /** True if this knob is the master toggle for Distant Horizons. */
    public boolean isDhMaster() {
        return this == DH_LOD_DISTANCE;
    }
}
