package com.nightfall.riftautotune.adapter;

import com.nightfall.riftautotune.core.GraphicsSettings;
import com.nightfall.riftautotune.core.Knob;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;

/**
 * Writes vanilla {@link Options}. These are public, stable APIs for 1.20.1, so no reflection is
 * needed. Each setter is guarded individually so one failure never aborts the rest.
 */
public final class VanillaConfigAdapter implements ConfigAdapter {

    @Override
    public String name() {
        return "Vanilla";
    }

    @Override
    public boolean isAvailable() {
        return Minecraft.getInstance() != null && Minecraft.getInstance().options != null;
    }

    @Override
    public void apply(GraphicsSettings s) {
        Options o = Minecraft.getInstance().options;

        set(() -> o.renderDistance().set(s.value(Knob.RENDER_DISTANCE)));
        set(() -> o.simulationDistance().set(s.value(Knob.SIMULATION_DISTANCE)));
        set(() -> o.entityDistanceScaling().set(s.value(Knob.ENTITY_DISTANCE) / 100.0)); // % -> factor
        set(() -> o.biomeBlendRadius().set(s.value(Knob.BIOME_BLEND)));
        set(() -> o.graphicsMode().set(graphics(s.get(Knob.GRAPHICS_MODE))));
        set(() -> o.cloudStatus().set(clouds(s.get(Knob.CLOUDS))));
        set(() -> o.particles().set(particles(s.get(Knob.PARTICLES))));

        set(o::save);
        RiftLog.debug("vanilla options applied (rd={}, sd={})",
                s.value(Knob.RENDER_DISTANCE), s.value(Knob.SIMULATION_DISTANCE));
    }

    private static GraphicsStatus graphics(int level) {
        return switch (level) {
            case 0 -> GraphicsStatus.FAST;
            case 2 -> GraphicsStatus.FABULOUS;
            default -> GraphicsStatus.FANCY;
        };
    }

    private static CloudStatus clouds(int level) {
        return switch (level) {
            case 0 -> CloudStatus.OFF;
            case 1 -> CloudStatus.FAST;
            default -> CloudStatus.FANCY;
        };
    }

    private static ParticleStatus particles(int level) {
        return switch (level) {
            case 0 -> ParticleStatus.MINIMAL;
            case 1 -> ParticleStatus.DECREASED;
            default -> ParticleStatus.ALL;
        };
    }

    private static void set(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            RiftLog.debug("vanilla setter failed: {}", t.toString());
        }
    }
}
