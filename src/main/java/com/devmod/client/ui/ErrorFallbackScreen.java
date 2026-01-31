package com.devmod.client.ui;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Simple fallback screen shown when a client screen fails to open.
 */
@OnlyIn(Dist.CLIENT)
public final class ErrorFallbackScreen extends Screen {

    @Nullable
    private final Screen parent;
    @Nullable
    private final String message;

    public ErrorFallbackScreen(@Nullable Screen parent, @Nullable String message) {
        super(Component.translatable("devmod.screen.error.title"));
        this.parent = parent;
        this.message = message;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();

        graphics.fill(0, 0, width, height, DesignTokens.ErrorScreen.BG);

        int centerX = UIScaleManager.getCenterX();
        int centerY = UIScaleManager.getCenterY();

        if (font == null) {
            return;
        }

        graphics.drawCenteredString(font,
            Component.translatable("devmod.screen.error.title"),
            centerX, centerY - 30, DesignTokens.ErrorScreen.TITLE);

        String safeMessage = message;
        if (safeMessage != null && !safeMessage.isBlank()) {
            if (safeMessage.length() > 140) {
                safeMessage = safeMessage.substring(0, 137) + "...";
            }
            graphics.drawCenteredString(font,
                Component.literal(safeMessage),
                centerX, centerY - 10, DesignTokens.ErrorScreen.TEXT);
        }

        graphics.drawCenteredString(font,
            Component.translatable("devmod.screen.error.hint"),
            centerX, centerY + 20, DesignTokens.ErrorScreen.HINT);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (parent != null) {
            mc.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
