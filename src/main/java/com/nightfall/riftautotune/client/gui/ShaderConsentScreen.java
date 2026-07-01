package com.nightfall.riftautotune.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Modal confirmation shown on world entry before shaders are enabled: displays the detected
 * hardware and the expected performance, and applies shaders only when the player accepts.
 * Declining tunes/applies the profile with shaders off instead. The choice callback fires exactly
 * once; closing with ESC counts as declining so the pack never enables shaders without consent.
 */
public final class ShaderConsentScreen extends Screen {

    private final List<Component> infoLines;
    private final Runnable onAccept;
    private final Runnable onDecline;
    private boolean decided;

    public ShaderConsentScreen(List<Component> infoLines, Runnable onAccept, Runnable onDecline) {
        super(Component.translatable("riftautotune.consent.title"));
        this.infoLines = infoLines;
        this.onAccept = onAccept;
        this.onDecline = onDecline;
    }

    @Override
    protected void init() {
        int contentBottom = 60 + infoLines.size() * 12;
        int buttonY = Math.min(height - 30, contentBottom + 16);
        int mid = width / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("riftautotune.consent.accept"),
                        b -> decide(onAccept))
                .bounds(mid - 154, buttonY, 150, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("riftautotune.consent.decline"),
                        b -> decide(onDecline))
                .bounds(mid + 4, buttonY, 150, 20).build());
    }

    private void decide(Runnable choice) {
        if (decided) return;
        decided = true;
        onClose();
        choice.run();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 30, 0xFFFFFF);
        int y = 60;
        for (Component line : infoLines) {
            g.drawCenteredString(font, line, width / 2, y, 0xD0D0D0);
            y += 12;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        // ESC (or any programmatic close) without an explicit choice counts as "no shaders".
        if (!decided) {
            decided = true;
            super.onClose();
            onDecline.run();
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
