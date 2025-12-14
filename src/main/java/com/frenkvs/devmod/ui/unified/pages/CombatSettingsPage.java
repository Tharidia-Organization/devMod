package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.WeaponConfigManager;
import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.editor.ItemEditorScreen;
import com.frenkvs.devmod.ui.editor.EditorStartTab;
import com.frenkvs.devmod.ui.unified.SettingsCategory;
import com.frenkvs.devmod.ui.unified.SettingsPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Combat settings page - weapon stats and damage configuration.
 * Shows current weapon stats and allows quick access to weapon editor.
 */
public class CombatSettingsPage implements SettingsPage {

    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_SPACING = 16;
    private static final int SCROLLBAR_WIDTH = 6;

    // Scrolling state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;
    private boolean isDraggingScrollbar = false;
    private int lastContentY, lastContentHeight;

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
        lastContentY = y;
        lastContentHeight = height;
        visibleHeight = height;

        // Calculate total content height
        totalContentHeight = calculateContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        // Enable scissoring
        graphics.enableScissor(x, y, x + width, y + height);

        int currentY = y - scrollOffset;
        Minecraft mc = Minecraft.getInstance();

        // === Current Weapon Section ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Current Weapon");
        currentY += ROW_HEIGHT + 4;

        ItemStack heldItem = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;

        if (heldItem.isEmpty()) {
            graphics.drawString(font, "No weapon in hand", x, currentY, UIConstants.Text.MUTED, false);
            currentY += ROW_HEIGHT;

            AxiomRenderer.drawHint(graphics, font, x, currentY, "Hold a weapon to see its stats");
            currentY += ROW_HEIGHT + SECTION_SPACING;
        } else {
            // Weapon name and icon
            String weaponName = heldItem.getHoverName().getString();
            graphics.drawString(font, weaponName, x, currentY, UIConstants.Text.PRIMARY, false);
            currentY += ROW_HEIGHT;

            // Get weapon stats
            WeaponStats stats = WeaponConfigManager.getStats(heldItem);

            // Stats display
            currentY = renderStatRow(graphics, font, x, currentY, width, "Head Multiplier",
                String.format("%.1fx", stats.headMult), UIConstants.BodyPart.HEAD);
            currentY = renderStatRow(graphics, font, x, currentY, width, "Body Multiplier",
                String.format("%.1fx", stats.bodyMult), UIConstants.BodyPart.BODY);
            currentY = renderStatRow(graphics, font, x, currentY, width, "Legs Multiplier",
                String.format("%.1fx", stats.legsMult), UIConstants.BodyPart.LEGS);
            currentY = renderStatRow(graphics, font, x, currentY, width, "Armor Penetration",
                String.format("%.0f%%", stats.armorPenetration * 100), UIConstants.Accent.ORANGE);
            currentY = renderStatRow(graphics, font, x, currentY, width, "Base Damage Bonus",
                String.format("+%.1f", stats.baseDamageBonus), UIConstants.Accent.RED);

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
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        boolean editorHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, buttonWidth, buttonHeight);
        boolean hasWeapon = !heldItem.isEmpty();

        if (hasWeapon) {
            AxiomRenderer.drawButton(graphics, font, x, currentY, buttonWidth, buttonHeight,
                "Open Weapon Editor [M]", editorHovered, false);
        } else {
            // Disabled button
            graphics.fill(x, currentY, x + buttonWidth, currentY + buttonHeight, UIConstants.Background.INPUT);
            AxiomRenderer.drawBorder(graphics, x, currentY, buttonWidth, buttonHeight, UIConstants.Border.MUTED);
            int textWidth = font.width("Open Weapon Editor [M]");
            graphics.drawString(font, "Open Weapon Editor [M]", x + (buttonWidth - textWidth) / 2,
                currentY + (buttonHeight - 9) / 2, UIConstants.Text.DISABLED, false);
        }
        currentY += buttonHeight + 8;

        // Hint
        AxiomRenderer.drawHint(graphics, font, x, currentY, "Press M in-game to edit weapon stats");

        // Disable scissoring
        graphics.disableScissor();

        // Render scrollbar if needed
        if (totalContentHeight > visibleHeight) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH - 2, y, SCROLLBAR_WIDTH, height);
        }
    }

    private int calculateContentHeight() {
        int h = 0;
        h += ROW_HEIGHT + 4; // Current Weapon header
        h += ROW_HEIGHT; // Weapon name or "No weapon"
        h += ROW_HEIGHT * 5; // 5 stat rows (or hint)
        h += SECTION_SPACING * 2; // Separator + spacing
        h += ROW_HEIGHT + 4; // Quick Actions header
        h += UIConstants.Size.BUTTON_HEIGHT + 8; // Button
        h += 20; // Hint
        return h;
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height) {
        graphics.fill(x, y, x + barWidth, y + height, UIConstants.Background.INPUT);
        float visibleRatio = (float) visibleHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));
        float scrollRatio = maxScrollOffset > 0 ? (float) scrollOffset / maxScrollOffset : 0;
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);
        int thumbColor = isDraggingScrollbar ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        graphics.fill(x, thumbY, x + barWidth, thumbY + thumbHeight, thumbColor);
    }

    private int renderStatRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                               String label, String value, int valueColor) {
        graphics.drawString(font, label, x, y + 2, UIConstants.Text.SECONDARY, false);

        int valueWidth = font.width(Objects.requireNonNull(value));
        graphics.drawString(font, value, x + width - valueWidth - 20, y + 2, valueColor, false);

        return y + ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        // Check scrollbar click
        if (totalContentHeight > visibleHeight) {
            int scrollbarX = contentX + contentWidth - SCROLLBAR_WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 2) {
                isDraggingScrollbar = true;
                // Calculate thumb dimensions for accurate click positioning
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
                int trackHeight = lastContentHeight - thumbHeight;
                if (trackHeight > 0) {
                    // Center thumb on click position
                    float clickRatio = (float)(mouseY - contentY - thumbHeight / 2) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        ItemStack heldItem = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;

        // Calculate button position with scroll offset
        int buttonY = calculateButtonY(contentY - scrollOffset, heldItem.isEmpty());
        int buttonWidth = 160;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;

        if (!heldItem.isEmpty() && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, buttonY, buttonWidth, buttonHeight)) {
            mc.setScreen(new ItemEditorScreen(heldItem, EditorStartTab.WEAPON));
            return true;
        }

        return false;
    }

    private int calculateButtonY(int startY, boolean noWeapon) {
        int currentY = startY;
        currentY += ROW_HEIGHT + 4; // Section header
        if (noWeapon) {
            currentY += ROW_HEIGHT + ROW_HEIGHT + SECTION_SPACING;
        } else {
            currentY += ROW_HEIGHT + ROW_HEIGHT * 5 + SECTION_SPACING;
        }
        currentY += SECTION_SPACING; // Separator
        currentY += ROW_HEIGHT + 4; // Quick Actions header
        return currentY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
                float dragRatio = (float)(mouseY - lastContentY - thumbHeight / 2) / trackHeight;
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
}
