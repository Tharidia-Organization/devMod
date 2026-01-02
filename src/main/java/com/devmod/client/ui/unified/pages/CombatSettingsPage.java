package com.devmod.client.ui.unified.pages;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.scroll.Scrollbar;
import com.devmod.client.ui.unified.SettingsCategory;
import com.devmod.client.ui.unified.SettingsPage;
import com.devmod.config.WeaponConfigManager;
import com.devmod.stats.WeaponStats;

public class CombatSettingsPage implements SettingsPage {

    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_SPACING = 16;

    // Scrolling state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;
    private boolean isDraggingScrollbar = false;
    private boolean showScrollbar = false;
    private int lastContentX, lastContentWidth;
    private int lastContentY, lastContentHeight;
    private final EditorButton openEditorButton = new EditorButton("combat-open-editor", "Open Weapon Editor [M]").style(EditorButton.Style.PRIMARY);

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.COMBAT;
    }

    @Override
    public String getTitle() {
        return "Combat Settings";
    }

    @Override
    public void render(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        // Store dimensions for scroll calculations
        lastContentX = x;
        lastContentY = y;
        lastContentWidth = width;
        lastContentHeight = height;
        visibleHeight = height;

        // Calculate total content height
        totalContentHeight = calculateContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        showScrollbar = totalContentHeight > visibleHeight;

        // Enable scissoring with try/finally for safety
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            int currentY = y - scrollOffset;
            Minecraft mc = Minecraft.getInstance();

            // === Current Weapon Section ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Current Weapon");
            currentY += ROW_HEIGHT + 4;

            ItemStack heldItem = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;

            if (heldItem.isEmpty()) {
                graphics.drawString(font, "No weapon in hand", x, currentY, DesignTokens.Text.MUTED, false);
                currentY += ROW_HEIGHT;

                AxiomRenderer.drawHint(graphics, font, x, currentY, "Hold a weapon to see its stats");
                currentY += ROW_HEIGHT + SECTION_SPACING;
            } else {
                // Weapon name and icon
                String weaponName = heldItem.getHoverName().getString();
                graphics.drawString(font, weaponName, x, currentY, DesignTokens.Text.PRIMARY, false);
                currentY += ROW_HEIGHT;

                // Get weapon stats
                WeaponStats stats = WeaponConfigManager.getStats(heldItem);

                // Stats display - body part colors
                int headColor = 0xFFFF6B6B;   // Red for head
                int bodyColor = 0xFF4ECDC4;   // Teal for body
                int armsColor = 0xFFFFE66D;   // Yellow for arms
                int legsColor = 0xFF95E1D3;   // Light green for legs
                currentY = renderStatRow(graphics, font, x, currentY, width, "Head Multiplier",
                    String.format("%.1fx", stats.headMult), headColor);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Body Multiplier",
                    String.format("%.1fx", stats.bodyMult), bodyColor);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Arms Multiplier",
                    String.format("%.1fx", stats.armsMult), armsColor);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Legs Multiplier",
                    String.format("%.1fx", stats.legsMult), legsColor);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Armor Penetration",
                    String.format("%.0f%%", stats.armorPenetration * 100), 0xFFFF8C00);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Base Damage Bonus",
                    String.format("+%.1f", stats.baseDamageBonus), DesignTokens.Semantic.ERROR);

                currentY += SECTION_SPACING;
            }

            // Separator
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += SECTION_SPACING;

            // === Quick Actions Section ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Quick Actions");
            currentY += ROW_HEIGHT + 4;

            // Open Weapon Editor button
            int buttonWidth = 160;
            int buttonHeight = DesignTokens.Component.BUTTON_HEIGHT_LG;
            boolean hasWeapon = !heldItem.isEmpty();

            openEditorButton
                .enabled(hasWeapon)
                .onClick(() -> {
                    ActionRegistry.invoke(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON,
                        ClientActionContexts.forClient(ActionOrigin.UI));
                });
            openEditorButton.render(graphics, x, currentY, buttonWidth, buttonHeight, mouseX, mouseY);
            currentY += buttonHeight + 8;

            // Hint
            AxiomRenderer.drawHint(graphics, font, x, currentY, "Press M in-game to edit weapon stats");
        } finally {
            // Disable scissoring
            graphics.disableScissor();
        }

        // Render scrollbar if needed
        if (showScrollbar) {
            renderScrollbar(graphics, x + width - Scrollbar.WIDTH - 2, y, Scrollbar.WIDTH, height);
        }
    }

    private int calculateContentHeight() {
        Minecraft mc = Minecraft.getInstance();
        boolean hasWeapon = mc.player != null && !mc.player.getMainHandItem().isEmpty();
        int h = 0;
        h += ROW_HEIGHT + 4; // Current Weapon header
        if (hasWeapon) {
            h += ROW_HEIGHT; // Weapon name
            h += ROW_HEIGHT * 6; // Stat rows
            h += SECTION_SPACING; // Section spacing
        } else {
            h += ROW_HEIGHT; // No weapon line
            h += ROW_HEIGHT; // Hint
            h += SECTION_SPACING;
        }
        h += SECTION_SPACING * 2; // Separator + spacing
        h += ROW_HEIGHT + 4; // Quick Actions header
        h += DesignTokens.Component.BUTTON_HEIGHT_LG + 8; // Button
        h += 20; // Hint
        return h;
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height) {
        Scrollbar.render(graphics, x, y, barWidth, height,
            scrollOffset, totalContentHeight, visibleHeight,
            false, isDraggingScrollbar);
    }

    private int renderStatRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                               String label, String value, int valueColor) {
        graphics.drawString(font, label, x, y + 2, DesignTokens.Text.SECONDARY, false);

        int valueWidth = font.width(Objects.requireNonNull(value));
        int rightPadding = showScrollbar ? Scrollbar.WIDTH + 8 : 8;
        graphics.drawString(font, value, x + width - valueWidth - rightPadding, y + 2, valueColor, false);

        return y + ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        // Check scrollbar click
        if (showScrollbar) {
            int scrollbarX = contentX + contentWidth - Scrollbar.WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + Scrollbar.WIDTH + 2) {
                isDraggingScrollbar = true;
                // Calculate thumb dimensions for accurate click positioning
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
                int trackHeight = lastContentHeight - thumbHeight;
                if (trackHeight > 0) {
                    // Center thumb on click position
                    float clickRatio = (float) (mouseY - contentY - thumbHeight / 2.0f) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        if (isMouseOverContent(mouseX, mouseY) && openEditorButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOverContent(mouseX, mouseY) || maxScrollOffset <= 0) {
            return false;
        }
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        if (openEditorButton.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScrollOffset > 0) {
            // Calculate thumb dimensions for accurate drag positioning
            float visibleRatio = (float) visibleHeight / totalContentHeight;
            int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
            int trackHeight = lastContentHeight - thumbHeight;
            if (trackHeight > 0) {
                // Center thumb on mouse position during drag
                float dragRatio = (float) (mouseY - lastContentY - thumbHeight / 2.0f) / trackHeight;
                dragRatio = Math.max(0, Math.min(1, dragRatio));
                scrollOffset = (int)(maxScrollOffset * dragRatio);
            }
            return true;
        }
        return false;
    }

    @Override
    public int getContentHeight() {
        return calculateContentHeight();
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return mouseX >= lastContentX && mouseX <= lastContentX + lastContentWidth
            && mouseY >= lastContentY && mouseY <= lastContentY + lastContentHeight;
    }
}
