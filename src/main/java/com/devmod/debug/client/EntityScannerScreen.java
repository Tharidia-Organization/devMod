package com.devmod.debug.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.debug.network.EntityScanDataPayload;

/**
 * Screen for displaying entity scan results from the EntityScanner block.
 * Shows a list of entities with details panel on selection.
 */
@OnlyIn(Dist.CLIENT)
public class EntityScannerScreen extends Screen {
    private static final int PANEL_PADDING = 8;
    private static final int LIST_WIDTH = 180;
    private static final int ITEM_HEIGHT = 20;
    private static final int MAX_VISIBLE_ITEMS = 15;

    private final BlockPos scannerPos;
    private List<EntityScanDataPayload.ScannedEntity> entities = new ArrayList<>();
    private int scanRadius = 16;

    @Nullable
    private EntityScanDataPayload.ScannedEntity selectedEntity;
    private int scrollOffset = 0;

    public EntityScannerScreen(BlockPos scannerPos) {
        super(Component.literal("Entity Scanner"));
        this.scannerPos = scannerPos;
    }

    /**
     * Update with new scan data.
     */
    public void updateScanData(EntityScanDataPayload data) {
        if (data.scannerPos().equals(scannerPos)) {
            this.entities = new ArrayList<>(data.entities());
            this.scanRadius = data.scanRadius();

            // Keep selection if entity still exists
            if (selectedEntity != null) {
                int selectedId = selectedEntity.entityId();
                selectedEntity = entities.stream()
                    .filter(e -> e.entityId() == selectedId)
                    .findFirst()
                    .orElse(null);
            }
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int screenWidth = width;
        int screenHeight = height;
        int panelWidth = Math.min(600, screenWidth - 40);
        int panelHeight = Math.min(400, screenHeight - 40);
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = (screenHeight - panelHeight) / 2;

        // Main panel background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DesignTokens.Surface.LEVEL_0);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, DesignTokens.Border.DEFAULT);

        // Title
        String title = "Entity Scanner - Radius: " + scanRadius + " - Found: " + entities.size();
        graphics.drawString(font, title, panelX + PANEL_PADDING, panelY + PANEL_PADDING, DesignTokens.Text.PRIMARY, false);

        int contentY = panelY + PANEL_PADDING + 14;
        int contentHeight = panelHeight - PANEL_PADDING * 2 - 14;

        // Entity list (left panel)
        renderEntityList(graphics, panelX + PANEL_PADDING, contentY, LIST_WIDTH, contentHeight, mouseX, mouseY);

        // Details panel (right side)
        int detailsX = panelX + PANEL_PADDING + LIST_WIDTH + PANEL_PADDING;
        int detailsWidth = panelWidth - LIST_WIDTH - PANEL_PADDING * 3;
        renderDetailsPanel(graphics, detailsX, contentY, detailsWidth, contentHeight);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntityList(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        // List background
        graphics.fill(x, y, x + width, y + height, DesignTokens.Surface.LEVEL_1);
        graphics.renderOutline(x, y, width, height, DesignTokens.Border.DEFAULT);

        int visibleItems = Math.min(MAX_VISIBLE_ITEMS, entities.size());
        for (int i = 0; i < visibleItems && (i + scrollOffset) < entities.size(); i++) {
            int index = i + scrollOffset;
            EntityScanDataPayload.ScannedEntity entity = entities.get(index);
            int itemY = y + 2 + i * ITEM_HEIGHT;

            // Selection/hover highlight
            boolean isSelected = entity.equals(selectedEntity);
            boolean isHovered = mouseX >= x && mouseX < x + width
                && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            if (isSelected) {
                graphics.fill(x + 2, itemY, x + width - 2, itemY + ITEM_HEIGHT - 1, DesignTokens.Surface.LEVEL_2);
            } else if (isHovered) {
                graphics.fill(x + 2, itemY, x + width - 2, itemY + ITEM_HEIGHT - 1,
                    DesignTokens.withAlpha(DesignTokens.Surface.LEVEL_1, DesignTokens.Alpha.A50));
            }

            // Entity name
            String displayName = truncate(entity.name(), 16);
            graphics.drawString(font, displayName, x + 4, itemY + 2, DesignTokens.Text.PRIMARY, false);

            // Health bar
            float healthPercent = entity.maxHealth() > 0 ? entity.health() / entity.maxHealth() : 0;
            int healthColor = healthPercent > 0.5f ? DesignTokens.Semantic.SUCCESS
                : healthPercent > 0.25f ? DesignTokens.Semantic.WARNING
                : DesignTokens.Semantic.ERROR;
            int barWidth = width - 8;
            int barHeight = 3;
            int barY = itemY + 12;
            graphics.fill(x + 4, barY, x + 4 + barWidth, barY + barHeight, DesignTokens.Surface.LEVEL_0);
            graphics.fill(x + 4, barY, x + 4 + (int)(barWidth * healthPercent), barY + barHeight, healthColor);
        }
    }

    private void renderDetailsPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Details background
        graphics.fill(x, y, x + width, y + height, DesignTokens.Surface.LEVEL_1);
        graphics.renderOutline(x, y, width, height, DesignTokens.Border.DEFAULT);

        if (selectedEntity == null) {
            graphics.drawString(font, "Select an entity", x + PANEL_PADDING, y + PANEL_PADDING, DesignTokens.Text.MUTED, false);
            return;
        }

        int lineY = y + PANEL_PADDING;
        int lineHeight = 11;

        // Entity header
        graphics.drawString(font, selectedEntity.name(), x + PANEL_PADDING, lineY, DesignTokens.Accent.PRIMARY, false);
        lineY += lineHeight;
        graphics.drawString(font, selectedEntity.type(), x + PANEL_PADDING, lineY, DesignTokens.Text.SECONDARY, false);
        lineY += lineHeight;

        // Position
        String pos = String.format("Pos: %.1f, %.1f, %.1f", selectedEntity.x(), selectedEntity.y(), selectedEntity.z());
        graphics.drawString(font, pos, x + PANEL_PADDING, lineY, DesignTokens.Text.SECONDARY, false);
        lineY += lineHeight;

        // Health
        String health = String.format("Health: %.1f / %.1f", selectedEntity.health(), selectedEntity.maxHealth());
        graphics.drawString(font, health, x + PANEL_PADDING, lineY, DesignTokens.Text.PRIMARY, false);
        lineY += lineHeight + 4;

        // Attributes section
        if (!selectedEntity.attributes().isEmpty()) {
            graphics.drawString(font, "-- Attributes --", x + PANEL_PADDING, lineY, DesignTokens.Accent.SECONDARY, false);
            lineY += lineHeight;
            for (EntityScanDataPayload.AttributeData attr : selectedEntity.attributes()) {
                if (lineY > y + height - lineHeight) break;
                String attrName = attr.name().replace("attribute.minecraft.", "").replace("attribute.devmod.", "");
                String attrText = String.format("%s: %.1f (%.1f)", truncate(attrName, 20), attr.currentValue(), attr.baseValue());
                graphics.drawString(font, attrText, x + PANEL_PADDING, lineY, DesignTokens.Text.SECONDARY, false);
                lineY += lineHeight;
            }
            lineY += 4;
        }

        // Equipment section
        if (!selectedEntity.equipment().isEmpty()) {
            graphics.drawString(font, "-- Equipment --", x + PANEL_PADDING, lineY, DesignTokens.Accent.SECONDARY, false);
            lineY += lineHeight;
            for (EntityScanDataPayload.EquipmentData equip : selectedEntity.equipment()) {
                if (lineY > y + height - lineHeight) break;
                String equipText = String.format("[%s] %s", equip.slot(), truncate(equip.itemName(), 24));
                graphics.drawString(font, equipText, x + PANEL_PADDING, lineY, DesignTokens.Text.SECONDARY, false);
                lineY += lineHeight;
            }
            lineY += 4;
        }

        // AI Goals section
        if (!selectedEntity.goals().isEmpty()) {
            graphics.drawString(font, "-- AI Goals --", x + PANEL_PADDING, lineY, DesignTokens.Accent.SECONDARY, false);
            lineY += lineHeight;
            for (EntityScanDataPayload.GoalData goal : selectedEntity.goals()) {
                if (lineY > y + height - lineHeight) break;
                int goalColor = goal.isRunning() ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED;
                String marker = goal.isRunning() ? ">" : " ";
                String goalText = String.format("%s[%d] %s", marker, goal.priority(), goal.name());
                graphics.drawString(font, goalText, x + PANEL_PADDING, lineY, goalColor, false);
                lineY += lineHeight;
            }
            lineY += 4;
        }

        // Target Goals section
        if (!selectedEntity.targetGoals().isEmpty()) {
            graphics.drawString(font, "-- Target Goals --", x + PANEL_PADDING, lineY, DesignTokens.Accent.SECONDARY, false);
            lineY += lineHeight;
            for (EntityScanDataPayload.GoalData goal : selectedEntity.targetGoals()) {
                if (lineY > y + height - lineHeight) break;
                int goalColor = goal.isRunning() ? DesignTokens.Semantic.ERROR : DesignTokens.Text.MUTED;
                String marker = goal.isRunning() ? ">" : " ";
                String goalText = String.format("%s[%d] %s", marker, goal.priority(), goal.name());
                graphics.drawString(font, goalText, x + PANEL_PADDING, lineY, goalColor, false);
                lineY += lineHeight;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int screenWidth = width;
            int screenHeight = height;
            int panelWidth = Math.min(600, screenWidth - 40);
            int panelHeight = Math.min(400, screenHeight - 40);
            int panelX = (screenWidth - panelWidth) / 2;
            int panelY = (screenHeight - panelHeight) / 2;
            int contentY = panelY + PANEL_PADDING + 14;
            int contentHeight = panelHeight - PANEL_PADDING * 2 - 14;
            int listX = panelX + PANEL_PADDING;

            // Check if click is in entity list
            if (mouseX >= listX && mouseX < listX + LIST_WIDTH
                && mouseY >= contentY && mouseY < contentY + contentHeight) {

                int relY = (int) mouseY - contentY - 2;
                int clickedIndex = relY / ITEM_HEIGHT + scrollOffset;

                if (clickedIndex >= 0 && clickedIndex < entities.size()) {
                    selectedEntity = entities.get(clickedIndex);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int screenWidth = width;
        int screenHeight = height;
        int panelWidth = Math.min(600, screenWidth - 40);
        int panelHeight = Math.min(400, screenHeight - 40);
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = (screenHeight - panelHeight) / 2;
        int contentY = panelY + PANEL_PADDING + 14;
        int contentHeight = panelHeight - PANEL_PADDING * 2 - 14;
        int listX = panelX + PANEL_PADDING;

        // Scroll entity list
        if (mouseX >= listX && mouseX < listX + LIST_WIDTH
            && mouseY >= contentY && mouseY < contentY + contentHeight) {
            int maxScroll = Math.max(0, entities.size() - MAX_VISIBLE_ITEMS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) deltaY));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }

    /**
     * Get the scanner position this screen is connected to.
     */
    public BlockPos getScannerPos() {
        return scannerPos;
    }
}
