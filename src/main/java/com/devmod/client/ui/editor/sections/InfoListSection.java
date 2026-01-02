package com.devmod.client.ui.editor.sections;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.ResponsiveLayout;

public final class InfoListSection implements EditorSection.CustomSection {
    private static final int LINE_HEIGHT = 12;
    private static final int TEXT_INSET_X = 8;
    private static final int HEADER_TEXT_HEIGHT = 8;
    private static final int HEADER_TEXT_OFFSET_Y =
        (EditorDimensions.SECTION_HEADER_HEIGHT - HEADER_TEXT_HEIGHT) / 2;
    private static final int SECTION_BOTTOM_PADDING = 8;
    private final String id;
    private final String title;
    private final List<String> lines;

    public InfoListSection(String id, String title, List<String> lines) {
        this.id = id;
        this.title = title;
        this.lines = lines;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getLabel() { return title; }

    @Override
    public int getHeight() {
        return EditorDimensions.SECTION_HEADER_HEIGHT + lines.size() * LINE_HEIGHT + SECTION_BOTTOM_PADDING;
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
        int y = bounds.y();
        graphics.fill(bounds.x(), y, bounds.x() + bounds.width(), y + EditorDimensions.SECTION_HEADER_HEIGHT,
            DesignTokens.Background.HEADER());
        graphics.drawString(font, Objects.requireNonNull(title, "title"), bounds.x() + TEXT_INSET_X,
            y + HEADER_TEXT_OFFSET_Y, DesignTokens.Text.TITLE(), false);
        y += EditorDimensions.SECTION_HEADER_HEIGHT;
        for (String line : lines) {
            graphics.drawString(font, Objects.requireNonNull(line, "line"), bounds.x() + TEXT_INSET_X,
                y, DesignTokens.Text.SECONDARY(), false);
            y += LINE_HEIGHT;
        }
    }
}
