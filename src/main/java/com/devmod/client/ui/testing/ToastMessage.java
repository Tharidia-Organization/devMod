package com.devmod.client.ui.testing;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Small transient toast message renderer with fade in/out.
 */
public final class ToastMessage {

    private String message = "";
    private long messageTime = 0;
    private final long durationMs;
    private final long fadeMs;

    public enum Position {
        TOP_RIGHT,
        CENTER_LEFT
    }

    public static final int DEFAULT_PADDING_X = 8;
    public static final int DEFAULT_PADDING_Y = 8;

    public ToastMessage(long durationMs, long fadeMs) {
        this.durationMs = durationMs;
        this.fadeMs = fadeMs;
    }

    public void show(String message) {
        this.message = message == null ? "" : message;
        this.messageTime = System.currentTimeMillis();
    }

    public void renderTopRight(GuiGraphics graphics, Font font, int rightX, int topY) {
        renderTopRight(graphics, font, rightX, topY, DEFAULT_PADDING_X, DEFAULT_PADDING_Y);
    }

    public void renderTopRight(GuiGraphics graphics, Font font, int rightX, int topY,
                               int paddingX, int paddingY) {
        if (message == null || message.isEmpty() || font == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - messageTime;
        if (elapsed > durationMs) {
            message = "";
            return;
        }

        float alpha = 1.0f;
        if (elapsed < fadeMs) {
            float t = elapsed / (float) fadeMs;
            alpha = easeOutCubic(t);
        } else if (elapsed > durationMs - fadeMs) {
            float t = (elapsed - (durationMs - fadeMs)) / (float) fadeMs;
            alpha = Math.max(0f, 1.0f - easeInCubic(t));
        }

        int textWidth = UIScaleManager.getScaledStringWidth(font, message);
        int widthPx = textWidth + paddingX * 2;
        int heightPx = 12 + paddingY * 2;
        int toastX = rightX - widthPx;
        int toastY = topY;

        int bg = DesignTokens.withAlpha(DesignTokens.Background.TOOLTIP(), (int) (0xF0 * alpha));
        int border = DesignTokens.withAlpha(DesignTokens.Border.LIGHT(), (int) (0xFF * alpha));
        int textColor = DesignTokens.withAlpha(DesignTokens.Text.PRIMARY(), (int) (0xFF * alpha));

        graphics.fill(toastX, toastY, toastX + widthPx, toastY + heightPx, bg);
        AxiomRenderer.drawBorder(graphics, toastX, toastY, widthPx, heightPx, border);
        UIScaleManager.drawScaledString(graphics, font, message, toastX + paddingX, toastY + paddingY + 2, textColor, false);
    }

    public void renderInBounds(GuiGraphics graphics, Font font, int x, int y, int width, int height, Position position) {
        if (message == null || message.isEmpty() || font == null) {
            return;
        }

        int textWidth = UIScaleManager.getScaledStringWidth(font, message);
        int widthPx = textWidth + DEFAULT_PADDING_X * 2;
        int heightPx = 12 + DEFAULT_PADDING_Y * 2;

        int toastX;
        int toastY;
        if (position == Position.CENTER_LEFT) {
            toastX = x + DEFAULT_PADDING_X;
            toastY = y + Math.max(0, (height - heightPx) / 2);
        } else {
            toastX = x + width - widthPx - DEFAULT_PADDING_X;
            toastY = y + DEFAULT_PADDING_Y;
        }

        renderTopRight(graphics, font, toastX + widthPx, toastY, DEFAULT_PADDING_X, DEFAULT_PADDING_Y);
    }

    private static float easeOutCubic(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        float inv = 1.0f - clamped;
        return 1.0f - inv * inv * inv;
    }

    private static float easeInCubic(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return clamped * clamped * clamped;
    }
}
