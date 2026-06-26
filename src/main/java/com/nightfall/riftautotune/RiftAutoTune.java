package com.nightfall.riftautotune;

import com.nightfall.riftautotune.client.RiftClientManager;
import com.nightfall.riftautotune.client.gui.ResultsHud;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * RiftAutoTune entry point. Client-only: all behaviour is gated to {@link Dist#CLIENT} so the
 * mod is inert (and harmless) on a dedicated server.
 */
@Mod(RiftAutoTune.MODID)
public final class RiftAutoTune {

    public static final String MODID = "riftautotune";

    public RiftAutoTune() {
        // Register config on every side; reading it is cheap and side-agnostic.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RiftConfig.SPEC);

        if (FMLEnvironment.dist != Dist.CLIENT) {
            RiftLog.info("Dedicated server detected - RiftAutoTune stays inert.");
            return;
        }

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onClientSetup);
        modBus.addListener(ResultsHud::onRegister);

        // Game (Forge) bus: ticks, world-join, command + overlay registration live in the manager.
        MinecraftForge.EVENT_BUS.register(RiftClientManager.INSTANCE);
        RiftLog.info("Constructed (client).");
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(RiftClientManager.INSTANCE::onClientSetup);
    }
}
