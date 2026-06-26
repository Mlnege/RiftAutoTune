package com.nightfall.riftautotune.client.gui;

import com.nightfall.riftautotune.client.RiftClientManager;
import com.nightfall.riftautotune.core.BenchmarkResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny top-left overlay shown briefly after a benchmark/apply: detected tier, measured FPS, the
 * applied preset and current mode. Registered on the mod event bus.
 */
public final class ResultsHud {

    private static volatile long visibleUntil = 0L;

    private static final IGuiOverlay OVERLAY = ResultsHud::render;

    private ResultsHud() {}

    public static void showFor(long millis) {
        visibleUntil = System.currentTimeMillis() + millis;
    }

    public static void onRegister(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("rift_hud", OVERLAY);
    }

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics g,
                               float partialTick, int screenWidth, int screenHeight) {
        if (System.currentTimeMillis() > visibleUntil) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug || mc.screen != null) return; // hide behind F3 and menus

        Font font = mc.font;
        RiftClientManager m = RiftClientManager.INSTANCE;

        List<String> lines = new ArrayList<>();
        lines.add("§bRiftAutoTune");
        if (m.hardware() != null) {
            lines.add("Tier: " + m.hardware().tier);
        }
        BenchmarkResult r = m.lastResult();
        if (r != null) {
            lines.add(String.format("FPS: %.0f avg / %.0f 1%%", r.avgFps, r.onePctLowFps));
        }
        lines.add("Mode: " + (m.adaptivePaused() ? "paused" : "adaptive"));

        int x = 6;
        int y = 6;
        for (String line : lines) {
            g.drawString(font, line, x, y, 0xFFFFFF, true);
            y += 10;
        }
    }
}
