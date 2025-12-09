package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.WeaponConfigManager;
import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.SettingsCategory;
import com.frenkvs.devmod.ui.unified.SettingsPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Combat settings page - weapon stats and damage configuration.
 * Shows current weapon stats and allows quick access to weapon editor.
 */
public class CombatSettingsPage implements SettingsPage {

    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_SPACING = 16;

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.COMBAT;
    }

    @Override
    public String getTitle() {
        return "Combat Settings";
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        int currentY = y;
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
    }

    private int renderStatRow(GuiGraphics graphics, Font font, int x, int y, int width,
                               String label, String value, int valueColor) {
        graphics.drawString(font, label, x, y + 2, UIConstants.Text.SECONDARY, false);

        int valueWidth = font.width(value);
        graphics.drawString(font, value, x + width - valueWidth - 20, y + 2, valueColor, false);

        return y + ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        Minecraft mc = Minecraft.getInstance();
        ItemStack heldItem = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;

        // Calculate button position - must match render() exactly
        int buttonY = calculateButtonY(contentY, heldItem.isEmpty());
        int buttonWidth = 160;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;

        if (!heldItem.isEmpty() && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, buttonY, buttonWidth, buttonHeight)) {
            mc.setScreen(new com.frenkvs.devmod.WeaponEditorScreen());
            return true;
        }

        return false;
    }

    /**
     * Calculate button Y position consistently for both render and click detection.
     * This ensures the clickable area matches the rendered button position.
     */
    private int calculateButtonY(int startY, boolean noWeapon) {
        int currentY = startY;

        // Section header "Current Weapon"
        currentY += ROW_HEIGHT + 4;

        // Weapon info section
        if (noWeapon) {
            currentY += ROW_HEIGHT;  // "No weapon in hand" text
            currentY += ROW_HEIGHT + SECTION_SPACING;  // Hint + spacing
        } else {
            currentY += ROW_HEIGHT;  // Weapon name
            currentY += ROW_HEIGHT * 5;  // 5 stat rows
            currentY += SECTION_SPACING;  // Spacing after stats
        }

        // Separator
        currentY += SECTION_SPACING;

        // Section header "Quick Actions"
        currentY += ROW_HEIGHT + 4;

        return currentY;
    }

    @Override
    public int getContentHeight() {
        return 300;
    }
}
