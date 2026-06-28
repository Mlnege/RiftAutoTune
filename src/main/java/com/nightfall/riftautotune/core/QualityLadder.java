package com.nightfall.riftautotune.core;

/**
 * Starting presets per {@link HardwareTier}. These are only the optimizer's <em>seed</em>; the
 * search then moves every knob up or down to land inside the FPS band based on the measured
 * benchmark. Seven tiers give a finer starting point than a coarse low/medium/high.
 *
 * <p>Note: {@code GRAPHICS_MODE} stays at Fancy (1) on any tier that enables shaders &mdash;
 * Fabulous (2) breaks Oculus/Iris shader rendering, so it is never seeded together with shaders.</p>
 */
public final class QualityLadder {

    private QualityLadder() {}

    public static GraphicsSettings presetFor(HardwareTier tier) {
        GraphicsSettings s = new GraphicsSettings();
        switch (tier) {
            case MINIMUM -> {
                s.set(Knob.RENDER_DISTANCE, 1);      // 6 chunks
                s.set(Knob.SIMULATION_DISTANCE, 0);  // 4
                s.set(Knob.ENTITY_DISTANCE, 0);      // 50%
                s.set(Knob.GRAPHICS_MODE, 0);        // Fast
                s.set(Knob.BIOME_BLEND, 0);
                s.set(Knob.CLOUDS, 0);
                s.set(Knob.PARTICLES, 0);            // Minimal
                s.set(Knob.SHADERS, 0);              // no shaders
                s.set(Knob.DH_LOD_DISTANCE, 0);      // DH off
                s.set(Knob.DH_CPU_LOAD, 0);
                s.set(Knob.DH_VERTICAL_QUALITY, 0);
                s.set(Knob.DH_LOD_DETAIL, 0);
            }
            case LOW -> {
                s.set(Knob.RENDER_DISTANCE, 2);      // 8
                s.set(Knob.SIMULATION_DISTANCE, 1);  // 6
                s.set(Knob.ENTITY_DISTANCE, 1);      // 75%
                s.set(Knob.GRAPHICS_MODE, 0);        // Fast
                s.set(Knob.BIOME_BLEND, 1);
                s.set(Knob.CLOUDS, 0);
                s.set(Knob.PARTICLES, 1);
                s.set(Knob.SHADERS, 0);              // shaders off on LOW
                s.set(Knob.DH_LOD_DISTANCE, 1);      // 64
                s.set(Knob.DH_CPU_LOAD, 0);
                s.set(Knob.DH_VERTICAL_QUALITY, 0);
                s.set(Knob.DH_LOD_DETAIL, 0);
            }
            case MEDIUM -> {
                s.set(Knob.RENDER_DISTANCE, 3);      // 10
                s.set(Knob.SIMULATION_DISTANCE, 2);  // 8
                s.set(Knob.ENTITY_DISTANCE, 2);      // 100%
                s.set(Knob.GRAPHICS_MODE, 1);        // Fancy
                s.set(Knob.BIOME_BLEND, 1);
                s.set(Knob.CLOUDS, 1);
                s.set(Knob.PARTICLES, 2);
                s.set(Knob.SHADERS, 1);
                s.set(Knob.SHADER_SHADOW_RES, 1);    // 1024
                s.set(Knob.SHADER_SHADOW_DIST, 1);
                s.set(Knob.SHADER_VOLUMETRIC, 1);
                s.set(Knob.SHADER_SSAO, 1);
                s.set(Knob.SHADER_BLOOM, 1);
                s.set(Knob.DH_LOD_DISTANCE, 2);      // 128
                s.set(Knob.DH_CPU_LOAD, 1);
                s.set(Knob.DH_VERTICAL_QUALITY, 1);
                s.set(Knob.DH_LOD_DETAIL, 1);
            }
            case HIGH -> {
                s.set(Knob.RENDER_DISTANCE, 5);      // 16
                s.set(Knob.SIMULATION_DISTANCE, 2);  // 8
                s.set(Knob.ENTITY_DISTANCE, 2);      // 100%
                s.set(Knob.GRAPHICS_MODE, 1);        // Fancy (Fabulous breaks shaders)
                s.set(Knob.BIOME_BLEND, 2);
                s.set(Knob.CLOUDS, 2);
                s.set(Knob.PARTICLES, 2);
                s.set(Knob.SHADERS, 1);
                s.set(Knob.SHADER_SHADOW_RES, 2);    // 2048
                s.set(Knob.SHADER_SHADOW_DIST, 2);
                s.set(Knob.SHADER_VOLUMETRIC, 2);
                s.set(Knob.SHADER_SSAO, 1);
                s.set(Knob.SHADER_BLOOM, 1);
                s.set(Knob.DH_LOD_DISTANCE, 3);      // 256
                s.set(Knob.DH_CPU_LOAD, 1);
                s.set(Knob.DH_VERTICAL_QUALITY, 1);
                s.set(Knob.DH_LOD_DETAIL, 1);
            }
            case VERY_HIGH -> {
                s.set(Knob.RENDER_DISTANCE, 5);      // 16
                s.set(Knob.SIMULATION_DISTANCE, 3);  // 10
                s.set(Knob.ENTITY_DISTANCE, 3);      // 150%
                s.set(Knob.GRAPHICS_MODE, 1);        // Fancy
                s.set(Knob.BIOME_BLEND, 2);
                s.set(Knob.CLOUDS, 2);
                s.set(Knob.PARTICLES, 2);
                s.set(Knob.SHADERS, 1);
                s.set(Knob.SHADER_SHADOW_RES, 2);    // 2048
                s.set(Knob.SHADER_SHADOW_DIST, 2);
                s.set(Knob.SHADER_VOLUMETRIC, 2);
                s.set(Knob.SHADER_SSAO, 2);
                s.set(Knob.SHADER_BLOOM, 1);
                s.set(Knob.DH_LOD_DISTANCE, 3);      // 256
                s.set(Knob.DH_CPU_LOAD, 1);
                s.set(Knob.DH_VERTICAL_QUALITY, 2);
                s.set(Knob.DH_LOD_DETAIL, 1);
            }
            case ULTRA -> {
                s.set(Knob.RENDER_DISTANCE, 6);      // 24
                s.set(Knob.SIMULATION_DISTANCE, 3);  // 10
                s.set(Knob.ENTITY_DISTANCE, 3);      // 150%
                s.set(Knob.GRAPHICS_MODE, 1);        // Fancy
                s.set(Knob.BIOME_BLEND, 2);
                s.set(Knob.CLOUDS, 2);
                s.set(Knob.PARTICLES, 2);
                s.set(Knob.SHADERS, 1);
                s.set(Knob.SHADER_SHADOW_RES, 3);    // 4096
                s.set(Knob.SHADER_SHADOW_DIST, 3);
                s.set(Knob.SHADER_VOLUMETRIC, 3);
                s.set(Knob.SHADER_SSAO, 2);
                s.set(Knob.SHADER_BLOOM, 1);
                s.set(Knob.DH_LOD_DISTANCE, 4);      // 512
                s.set(Knob.DH_CPU_LOAD, 1);
                s.set(Knob.DH_VERTICAL_QUALITY, 2);
                s.set(Knob.DH_LOD_DETAIL, 2);
            }
            case EXTREME -> {
                s.set(Knob.RENDER_DISTANCE, 7);      // 32
                s.set(Knob.SIMULATION_DISTANCE, 4);  // 12
                s.set(Knob.ENTITY_DISTANCE, 4);      // 200%
                s.set(Knob.GRAPHICS_MODE, 1);        // Fancy (still shader-compatible)
                s.set(Knob.BIOME_BLEND, 3);
                s.set(Knob.CLOUDS, 2);
                s.set(Knob.PARTICLES, 2);
                s.set(Knob.SHADERS, 1);
                s.set(Knob.SHADER_SHADOW_RES, 3);    // 4096
                s.set(Knob.SHADER_SHADOW_DIST, 3);
                s.set(Knob.SHADER_VOLUMETRIC, 3);
                s.set(Knob.SHADER_SSAO, 2);
                s.set(Knob.SHADER_BLOOM, 1);
                s.set(Knob.DH_LOD_DISTANCE, 4);      // 512
                s.set(Knob.DH_CPU_LOAD, 2);
                s.set(Knob.DH_VERTICAL_QUALITY, 2);
                s.set(Knob.DH_LOD_DETAIL, 2);
            }
        }
        return s;
    }
}
