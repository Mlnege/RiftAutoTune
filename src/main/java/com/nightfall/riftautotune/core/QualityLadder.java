package com.nightfall.riftautotune.core;

/**
 * Starting presets per {@link HardwareTier}. These are only the optimizer's seed; the search
 * then moves up or down each knob to land inside the FPS band.
 */
public final class QualityLadder {

    private QualityLadder() {}

    public static GraphicsSettings presetFor(HardwareTier tier) {
        GraphicsSettings s = new GraphicsSettings();
        switch (tier) {
            case LOW -> {
                s.set(Knob.RENDER_DISTANCE, 2);      // 8 chunks
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
        }
        return s;
    }
}
