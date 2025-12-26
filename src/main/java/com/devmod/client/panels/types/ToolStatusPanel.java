package com.devmod.client.panels.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.panels.core.FloatingPanel;
import com.devmod.client.panels.core.PanelType;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.hub.ToolType;

public class ToolStatusPanel extends FloatingPanel {

    // Tool states cache
    private List<ToolState> toolStates = new ArrayList<>();
    private int activeCount = 0;
    private long lastUpdateTick = 0;

    private static final int UPDATE_INTERVAL_TICKS = 10; // Update every 10 ticks

    /**
     * State of a single tool.
     */
    private record ToolState(String name, String hotkey, boolean enabled, int color) {}

    /**
     * Creates a tool status panel at a fixed position.
     */
    public ToolStatusPanel(Vec3 position) {
        super(PanelType.TOOL_STATUS, position);
        updateToolStates();
    }

    @Override
    public void tick() {
        super.tick();

        long currentTick = System.currentTimeMillis() / 50;
        if (currentTick - lastUpdateTick >= UPDATE_INTERVAL_TICKS) {
            updateToolStates();
            lastUpdateTick = currentTick;
        }
    }

    /**
     * Updates the states of all tools.
     */
    private void updateToolStates() {
        toolStates.clear();
        activeCount = 0;

        for (ToolType tool : ToolType.values()) {
            boolean enabled = tool.isEnabled();
            if (enabled) activeCount++;

            toolStates.add(new ToolState(
                tool.getLabel(),
                tool.getHotkey(),
                enabled,
                enabled ? UIConstants.Status.SUCCESS() : UIConstants.Text.MUTED()
            ));
        }
    }

    @Override
    public void renderContent(GuiGraphics graphics, int contentWidth, int contentHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int y = 0;
        int lineHeight = 10;

        // Header with count
        String header = "Active: " + activeCount + "/" + toolStates.size();
        Font font = Objects.requireNonNull(mc.font);
        graphics.drawString(font, header, 0, y, UIConstants.Text.SECONDARY(), false);
        y += lineHeight + 4;

        // Tool list (show only the first ones that fit)
        int maxTools = (contentHeight - y) / lineHeight;
        int shown = 0;

        for (ToolState tool : toolStates) {
            if (shown >= maxTools) break;

            // Colored indicator
            int dotSize = 4;
            int dotY = y + (lineHeight - dotSize) / 2;
            graphics.fill(0, dotY, dotSize, dotY + dotSize, tool.color);

            // Name and hotkey
            String text = "[" + tool.hotkey + "] " + tool.name;
            int textColor = tool.enabled ? UIConstants.Text.PRIMARY() : UIConstants.Text.MUTED();
            graphics.drawString(font, text, dotSize + 4, y, textColor, false);

            y += lineHeight;
            shown++;
        }
    }

    @Override
    public void renderContent3D(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource bufferSource, @Nonnull Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        int y = 0;
        int lineHeight = 10;

        // Header with count
        String header = "Active: " + activeCount + "/" + toolStates.size();
        renderText3D(poseStack, bufferSource, font, header, 0, y, applyAlpha(UIConstants.Text.SECONDARY(), alpha));
        y += lineHeight + 4;

        // Tool list (show only the first ones that fit)
        int maxTools = (contentHeight - y) / lineHeight;
        int shown = 0;

        for (ToolState tool : toolStates) {
            if (shown >= maxTools) break;

            // Name and hotkey
            String text = "[" + tool.hotkey + "] " + tool.name;
            int textColor = tool.enabled ? UIConstants.Text.PRIMARY() : UIConstants.Text.MUTED();
            renderText3D(poseStack, bufferSource, font, text, 0, y, applyAlpha(textColor, alpha));

            y += lineHeight;
            shown++;
        }
    }

    @Override
    @Nonnull
    public String getTitle() {
        return Objects.requireNonNull("Tools (" + activeCount + ")", "title");
    }

    // === Getters ===

    public int getActiveCount() {
        return activeCount;
    }

    public int getTotalCount() {
        return toolStates.size();
    }

    /**
     * Checks if a specific tool is active.
     */
    public boolean isToolActive(ToolType tool) {
        return tool.isEnabled();
    }

    @Override
    public String toString() {
        return String.format("ToolStatusPanel[active=%d/%d]", activeCount, toolStates.size());
    }
}
