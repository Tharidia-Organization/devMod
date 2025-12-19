package com.frenkvs.devmod.ui.editor.sections;

import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.EditorStartTab;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Clickable card section for navigating to another editor module.
 * Used by GeneralModule (Navigation Hub) to provide module switching.
 */

public final class ModuleCardSection implements EditorSection.CustomSection {

    private static final int CARD_HEIGHT = 56;
    private static final int CARD_PADDING = 8;
    private static final int ICON_SIZE = 24;
    private static final int TITLE_Y_OFFSET = 8;
    private static final int DESC_Y_OFFSET = 24;
    private static final int HOVER_BORDER_WIDTH = 2;

    private final String id;
    private final EditorStartTab targetModule;
    private final String title;
    private final String description;
    private final String icon;
    private final int accentColor;
    private final Consumer<EditorStartTab> onClickCallback;

    // Render state for hit testing
    private int lastX, lastY, lastWidth, lastHeight;
    private boolean hovered = false;

    public ModuleCardSection(
            String id,
            EditorStartTab targetModule,
            String title,
            String description,
            String icon,
            int accentColor,
            Consumer<EditorStartTab> onClickCallback
    ) {
        this.id = id;
        this.targetModule = targetModule;
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.accentColor = accentColor;
        this.onClickCallback = onClickCallback;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return title;
    }

    @Override
    public int getHeight() {
        return CARD_HEIGHT + CARD_PADDING;
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        // Store bounds for hit testing
        lastX = bounds.x();
        lastY = bounds.y();
        lastWidth = bounds.width();
        lastHeight = CARD_HEIGHT;

        // Check hover
        hovered = mouseX >= lastX && mouseX < lastX + lastWidth
               && mouseY >= lastY && mouseY < lastY + lastHeight;

        // Background
        int bgColor = hovered ? UIConstants.Background.HOVER() : UIConstants.Background.PANEL();
        graphics.fill(lastX, lastY, lastX + lastWidth, lastY + CARD_HEIGHT, bgColor);

        // Border (accent on hover)
        int borderColor = hovered ? accentColor : UIConstants.Border.DEFAULT();
        drawBorder(graphics, lastX, lastY, lastWidth, CARD_HEIGHT, borderColor, hovered ? HOVER_BORDER_WIDTH : 1);

        // Icon area
        int iconX = lastX + CARD_PADDING;
        int iconY = lastY + (CARD_HEIGHT - ICON_SIZE) / 2;
        graphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, accentColor & 0x44FFFFFF);

        // Icon text (emoji/symbol)
        int iconTextX = iconX + (ICON_SIZE - font.width(icon)) / 2;
        int iconTextY = iconY + (ICON_SIZE - 8) / 2;
        graphics.drawString(font, icon, iconTextX, iconTextY, accentColor, false);

        // Title
        int textX = iconX + ICON_SIZE + CARD_PADDING;
        graphics.drawString(font, title, textX, lastY + TITLE_Y_OFFSET, UIConstants.Text.TITLE(), false);

        // Description
        graphics.drawString(font, description, textX, lastY + DESC_Y_OFFSET, UIConstants.Text.SECONDARY(), false);

        // Arrow indicator on hover
        if (hovered) {
            String arrow = "→";
            int arrowX = lastX + lastWidth - CARD_PADDING - font.width(arrow);
            int arrowY = lastY + (CARD_HEIGHT - 8) / 2;
            graphics.drawString(font, arrow, arrowX, arrowY, accentColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered && onClickCallback != null) {
            onClickCallback.accept(targetModule);
            return true;
        }
        return false;
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color, int thickness) {
        // Top
        graphics.fill(x, y, x + w, y + thickness, color);
        // Bottom
        graphics.fill(x, y + h - thickness, x + w, y + h, color);
        // Left
        graphics.fill(x, y, x + thickness, y + h, color);
        // Right
        graphics.fill(x + w - thickness, y, x + w, y + h, color);
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════

    public static ModuleCardSection weapon(Consumer<EditorStartTab> callback) {
        return new ModuleCardSection(
            "card-weapon",
            EditorStartTab.WEAPON,
            "Weapon Editor",
            "Damage, speed, knockback, sweep",
            "⚔",
            UIConstants.Accent.RED(),
            callback
        );
    }

    public static ModuleCardSection armor(Consumer<EditorStartTab> callback) {
        return new ModuleCardSection(
            "card-armor",
            EditorStartTab.ARMOR,
            "Armor Editor",
            "Defense, toughness, knockback resistance",
            "🛡",
            UIConstants.Accent.BLUE(),
            callback
        );
    }

    public static ModuleCardSection recipe(Consumer<EditorStartTab> callback) {
        return new ModuleCardSection(
            "card-recipe",
            EditorStartTab.RECIPE,
            "Recipe Editor",
            "Crafting and smithing recipes",
            "📖",
            UIConstants.Accent.GREEN(),
            callback
        );
    }

    public static ModuleCardSection usable(Consumer<EditorStartTab> callback) {
        return new ModuleCardSection(
            "card-usable",
            EditorStartTab.USABLE,
            "Usable Item Editor",
            "Cooldowns, use duration, throwables",
            "⏱",
            UIConstants.Accent.ORANGE(),
            callback
        );
    }

    public static ModuleCardSection food(Consumer<EditorStartTab> callback) {
        return new ModuleCardSection(
            "card-food",
            EditorStartTab.FOOD,
            "Food Editor",
            "Nutrition, saturation, effects",
            "🍖",
            UIConstants.Accent.GREEN(),
            callback
        );
    }

    public static ModuleCardSection fuel(Consumer<EditorStartTab> callback) {
        return new ModuleCardSection(
            "card-fuel",
            EditorStartTab.FUEL,
            "Fuel Editor",
            "Burn time, cook time settings",
            "🔥",
            UIConstants.Accent.ORANGE(),
            callback
        );
    }
}
