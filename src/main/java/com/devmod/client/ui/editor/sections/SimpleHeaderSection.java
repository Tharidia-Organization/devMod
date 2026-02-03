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

public final class SimpleHeaderSection implements EditorSection.HeaderSection {
    private static final int DEFAULT_HEIGHT = 20;
    private static final int TEXT_HEIGHT = 8;
    private static final int DEFAULT_TEXT_INSET_X = 4;
    private final String id;
    private final String label;
    private final int height;
    private final int textInsetX;
    private final int textOffsetY;

    public SimpleHeaderSection(String id, String label) {
        this(id, label, DEFAULT_HEIGHT);
    }

    public SimpleHeaderSection(String id, String label, int height) {
        this(id, label, height, DEFAULT_TEXT_INSET_X, (height - TEXT_HEIGHT) / 2);
    }

    public SimpleHeaderSection(String id, String label, int height, int textInsetX, int textOffsetY) {
        this.id = id;
        this.label = label;
        this.height = height;
        this.textInsetX = textInsetX;
        this.textOffsetY = textOffsetY;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getLabel() { return label; }

    @Override
    public int getHeight() { return ScaledCoord.scaleDim(height); }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
        int textY = bounds.y() + Math.max(0, ScaledCoord.scaleDim(textOffsetY));
        UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull(label, "label"),
            bounds.x() + ScaledCoord.scaleDim(textInsetX), textY, DesignTokens.Text.TITLE(), false);
    }

    @Override
    public boolean isCollapsible() { return false; }

    @Override
    public boolean isCollapsed() { return false; }

    @Override
    public void setCollapsed(boolean collapsed) {}
}
