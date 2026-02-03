package com.devmod.client.ui.editor.sections;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;

public final class TextNoteSection implements EditorSection.CustomSection {
    private static final int DEFAULT_HEIGHT = 16;
    private static final int DEFAULT_TEXT_INSET_X = 8;
    private static final int DEFAULT_TEXT_OFFSET_Y = 4;
    private final String id;
    private final String text;
    private final int height;
    private final int textInsetX;
    private final int textOffsetY;
    private final int textColor;

    public TextNoteSection(String id, String text) {
        this(id, text, DEFAULT_HEIGHT, DEFAULT_TEXT_INSET_X, DEFAULT_TEXT_OFFSET_Y, DesignTokens.Text.MUTED());
    }

    public TextNoteSection(String id, String text, int height, int textInsetX, int textOffsetY, int textColor) {
        this.id = id;
        this.text = text;
        this.height = height;
        this.textInsetX = textInsetX;
        this.textOffsetY = textOffsetY;
        this.textColor = textColor;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getLabel() { return ""; }

    @Override
    public int getHeight() { return ScaledCoord.scaleDim(height); }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
        int insetX = ScaledCoord.scaleDim(textInsetX);
        int offsetY = Math.max(0, ScaledCoord.scaleDim(textOffsetY));
        UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull(text, "text"),
            bounds.x() + insetX, bounds.y() + offsetY, textColor, false);
    }
}
