package com.frenkvs.devmod.ui.radial.render;

import com.frenkvs.devmod.ui.radial.RadialCategory;
import com.frenkvs.devmod.ui.radial.RadialMenuConfig;
import com.frenkvs.devmod.ui.radial.RadialMenuItem;
import com.frenkvs.devmod.ui.radial.config.RadialMenuConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Renders the outer category ring and category items for the Radial Menu.
 *
 * <p>This renderer handles:</p>
 * <ul>
 *   <li>Category segments in the outer ring</li>
 *   <li>Category icons and names</li>
 *   <li>Active item badges</li>
 *   <li>Item rendering when a category is selected</li>
 *   <li>Cross-fade transitions between macro categories</li>
 * </ul>
 */
@SuppressWarnings("null") // Minecraft API null annotations
public final class RadialCategoryRenderer {

    private RadialCategoryRenderer() {
        // Utility class - no instantiation
    }

    // ================================================================
    // CATEGORY RING RENDERING
    // ================================================================

    /**
     * Configuration for rendering the category ring.
     *
     * @param centerX          center X coordinate
     * @param centerY          center Y coordinate
     * @param innerRadius      inner radius of the ring
     * @param outerRadius      outer radius of the ring
     * @param itemRadius       radius for icon placement
     * @param selectedIndex    currently selected category index (-1 if none)
     * @param categoryAnimations animation progress for each category
     * @param theme            color theme to use
     * @param iconMode         icon rendering mode (EMOJI, ITEMSTACK, AUTO)
     * @param pulsePhase       current pulse animation phase
     */
    public record RingConfig(
        int centerX,
        int centerY,
        int innerRadius,
        int outerRadius,
        int itemRadius,
        int selectedIndex,
        float[] categoryAnimations,
        RadialMenuConfig.ColorTheme theme,
        RadialMenuConfig.IconMode iconMode,
        float pulsePhase
    ) {
        public RingConfig {
            Objects.requireNonNull(categoryAnimations, "categoryAnimations cannot be null");
            Objects.requireNonNull(theme, "theme cannot be null");
            Objects.requireNonNull(iconMode, "iconMode cannot be null");
        }
    }

    /**
     * Renders the category ring with optional cross-fade transition support.
     *
     * @param graphics      GUI graphics context
     * @param font          font for rendering text
     * @param categories    list of categories to render
     * @param config        ring configuration
     * @param alpha         opacity multiplier (0-1) for cross-fade effect
     * @param allowSelection whether to highlight selected category
     */
    public static void renderCategoryRing(GuiGraphics graphics, Font font,
                                           List<RadialCategory> categories,
                                           RingConfig config,
                                           float alpha, boolean allowSelection) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(categories, "categories cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        int numCategories = categories.size();
        if (numCategories == 0) return;

        double segmentAngle = RadialMenuConstants.TWO_PI / numCategories;
        double startOffset = -Math.PI / 2;
        int alphaInt = (int) (alpha * 255);

        // Render each category segment
        for (int i = 0; i < numCategories; i++) {
            RadialCategory cat = categories.get(i);
            boolean selected = allowSelection && (i == config.selectedIndex);

            renderCategorySegment(graphics, font, cat, i, numCategories,
                config, selected, alphaInt, segmentAngle, startOffset);
        }
    }

    /**
     * Renders a single category segment.
     */
    private static void renderCategorySegment(GuiGraphics graphics, Font font,
                                               RadialCategory cat, int index, int numCategories,
                                               RingConfig config, boolean selected,
                                               int alphaInt, double segmentAngle, double startOffset) {
        double startAngle = startOffset + (index - 0.5) * segmentAngle;
        double endAngle = startAngle + segmentAngle;

        // Segment fill
        int baseColor = selected ? 0xEE252540 : 0xDD1a1a30;
        int segColor = selected
            ? RadialGeometry.blendColors(baseColor, cat.getColor(), 0.25f)
            : baseColor;
        segColor = RadialGeometry.applyAlpha(segColor, alphaInt);

        RadialGeometry.renderArcSegment(graphics, config.centerX, config.centerY,
            config.innerRadius, config.outerRadius, startAngle, endAngle, segColor);

        // Outer border
        int borderCol = selected ? cat.getColor() : 0xFF404060;
        borderCol = RadialGeometry.applyAlpha(borderCol, alphaInt);
        RadialGeometry.renderArcOutline(graphics, config.centerX, config.centerY,
            config.outerRadius, startAngle, endAngle, borderCol, 2);

        // Divider line
        int dividerColor = RadialGeometry.applyAlpha(0xFF404060, alphaInt);
        int divX1 = (int) (config.centerX + Math.cos(startAngle) * config.innerRadius);
        int divY1 = (int) (config.centerY + Math.sin(startAngle) * config.innerRadius);
        int divX2 = (int) (config.centerX + Math.cos(startAngle) * config.outerRadius);
        int divY2 = (int) (config.centerY + Math.sin(startAngle) * config.outerRadius);
        RadialGeometry.drawLine(graphics, divX1, divY1, divX2, divY2, dividerColor);

        // Icon position
        double midAngle = startOffset + index * segmentAngle;
        int iconX = (int) (config.centerX + Math.cos(midAngle) * config.itemRadius);
        int iconY = (int) (config.centerY + Math.sin(midAngle) * config.itemRadius);

        // Render icon
        renderCategoryIcon(graphics, font, cat, iconX, iconY - 8, selected, alphaInt, config);

        // Category name
        int textColor = selected ? 0xFFFFFFFF : 0xFFBBBBCC;
        textColor = RadialGeometry.applyAlpha(textColor, alphaInt);
        graphics.drawCenteredString(font, cat.getName(), iconX, iconY + 6, textColor);

        // Active items badge
        int activeCount = cat.countActiveItems();
        if (activeCount > 0 && alphaInt > 127) { // Only show badges when mostly visible
            int badgeColor = RadialGeometry.applyAlpha(config.theme.active, alphaInt);
            renderBadge(graphics, font, iconX + 20, iconY - 14, activeCount, badgeColor, config.pulsePhase);
        }
    }

    /**
     * Renders a category icon with alpha support for transitions.
     */
    private static void renderCategoryIcon(GuiGraphics graphics, Font font,
                                            RadialCategory cat, int x, int y,
                                            boolean selected, int alpha, RingConfig config) {
        int iconColor = selected ? cat.getColor() : config.theme.textSecondary;
        iconColor = RadialGeometry.applyAlpha(iconColor, alpha);

        boolean useItemStack = config.iconMode == RadialMenuConfig.IconMode.ITEMSTACK ||
            (config.iconMode == RadialMenuConfig.IconMode.AUTO && cat.getIconStack() != null);

        if (useItemStack && cat.getIconStack() != null && alpha > RadialMenuConstants.ITEMSTACK_ALPHA_THRESHOLD) {
            // Only render item stacks when mostly opaque (they don't support alpha well)
            graphics.renderItem(cat.getIconStack(), x - 8, y - 4);
        } else {
            graphics.drawCenteredString(font, cat.getIcon(), x, y, iconColor);
        }
    }

    /**
     * Renders an active count badge.
     */
    private static void renderBadge(GuiGraphics graphics, Font font,
                                     int x, int y, int count, int color, float pulsePhase) {
        float pulse = 0.8f + 0.2f * (float) Math.sin(pulsePhase * 2);
        int badgeColor = RadialGeometry.blendColors(color, 0xFFFFFFFF, pulse * 0.3f);

        graphics.fill(x - 6, y - 4, x + 6, y + 6, 0xDD000000);
        graphics.drawCenteredString(font, String.valueOf(count), x, y - 2, badgeColor);
    }

    /**
     * Renders the ring borders (inner and outer).
     *
     * @param graphics    GUI graphics context
     * @param centerX     center X coordinate
     * @param centerY     center Y coordinate
     * @param innerRadius inner ring radius
     * @param outerRadius outer ring radius
     */
    public static void renderRingBorders(GuiGraphics graphics,
                                          int centerX, int centerY,
                                          int innerRadius, int outerRadius) {
        // Inner ring border
        RadialGeometry.renderRing(graphics, centerX, centerY,
            innerRadius - 2, innerRadius, 0xFF303050);

        // Outer ring border
        RadialGeometry.renderRing(graphics, centerX, centerY,
            outerRadius, outerRadius + 2, 0xFF505070);
    }

    // ================================================================
    // CATEGORY ITEMS RENDERING
    // ================================================================

    /**
     * Configuration for rendering category items.
     *
     * @param centerX         center X coordinate
     * @param centerY         center Y coordinate
     * @param outerRadius     outer radius of the category ring
     * @param selectedCategoryIndex selected category index
     * @param selectedItemIndex selected item index (-1 if none)
     * @param itemAnimations  animation progress for each item
     * @param theme           color theme to use
     */
    public record ItemsConfig(
        int centerX,
        int centerY,
        int outerRadius,
        int selectedCategoryIndex,
        int selectedItemIndex,
        float[] itemAnimations,
        RadialMenuConfig.ColorTheme theme
    ) {
        public ItemsConfig {
            Objects.requireNonNull(itemAnimations, "itemAnimations cannot be null");
            Objects.requireNonNull(theme, "theme cannot be null");
        }
    }

    /**
     * Renders the items for a selected category.
     *
     * @param graphics   GUI graphics context
     * @param font       font for rendering text
     * @param category   the category whose items to render
     * @param categories all categories (for angle calculation)
     * @param config     items configuration
     */
    public static void renderCategoryItems(GuiGraphics graphics, Font font,
                                            RadialCategory category,
                                            List<RadialCategory> categories,
                                            ItemsConfig config) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(categories, "categories cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        List<RadialMenuItem> items = category.getItems();
        int numItems = items.size();
        if (numItems == 0) return;

        double segmentAngle = RadialMenuConstants.TWO_PI / categories.size();
        double startOffset = -Math.PI / 2;
        double catStartAngle = startOffset + (config.selectedCategoryIndex - 0.5) * segmentAngle;
        double itemAngleStep = segmentAngle / numItems;

        int baseRadius = config.outerRadius + 55;
        int itemSize = 34;

        for (int i = 0; i < numItems; i++) {
            RadialMenuItem item = items.get(i);
            if (!item.isVisible()) continue;

            renderItem(graphics, font, item, i, config, category,
                catStartAngle, itemAngleStep, baseRadius, itemSize);
        }
    }

    /**
     * Renders a single menu item.
     */
    private static void renderItem(GuiGraphics graphics, Font font,
                                    RadialMenuItem item, int index,
                                    ItemsConfig config, RadialCategory category,
                                    double catStartAngle, double itemAngleStep,
                                    int baseRadius, int itemSize) {
        boolean itemSelected = (index == config.selectedItemIndex);
        boolean isActive = item.isToggle() && item.isActive();
        float itemAnim = index < config.itemAnimations.length ? config.itemAnimations[index] : 0;

        double itemAngle = catStartAngle + (index + 0.5) * itemAngleStep;

        // Position with slight expansion on hover
        int itemX = (int) (config.centerX + Math.cos(itemAngle) * (baseRadius + 6 * itemAnim));
        int itemY = (int) (config.centerY + Math.sin(itemAngle) * (baseRadius + 6 * itemAnim));

        int itemRadiusSize = itemSize + (int) (4 * itemAnim);

        // Background
        int bgColor = itemSelected ? 0xFF303050 : 0xF0202035;
        if (isActive) {
            bgColor = RadialGeometry.blendColors(bgColor, config.theme.active, 0.25f);
        }
        RadialGeometry.renderCircle(graphics, itemX, itemY, itemRadiusSize, bgColor);

        // Border
        int borderColor = isActive
            ? config.theme.active
            : (itemSelected ? category.getColor() : 0xFF505070);
        int borderWidth = itemSelected ? 3 : 2;
        RadialGeometry.renderRing(graphics, itemX, itemY,
            itemRadiusSize - borderWidth, itemRadiusSize, borderColor);

        // Inner highlight for depth when selected
        if (itemSelected) {
            int highlightColor = RadialGeometry.blendColors(category.getColor(), 0xFFFFFFFF, 0.3f);
            RadialGeometry.renderRing(graphics, itemX, itemY,
                itemRadiusSize - borderWidth - 1, itemRadiusSize - borderWidth, highlightColor);
        }

        // Icon
        renderItemIcon(graphics, font, item, itemX, itemY, itemSelected, config.theme);

        // Name (truncated if needed)
        renderItemName(graphics, font, item, itemX, itemY, itemSelected, isActive, config.theme);

        // Toggle status indicator
        renderItemStatus(graphics, font, item, itemX, itemY, isActive, config.theme);
    }

    /**
     * Renders an item's icon.
     */
    private static void renderItemIcon(GuiGraphics graphics, Font font,
                                         RadialMenuItem item, int x, int y,
                                         boolean selected, RadialMenuConfig.ColorTheme theme) {
        ItemStack iconStack = item.getIconStack();
        if (iconStack != null) {
            graphics.renderItem(iconStack, x - 8, y - 16);
        } else {
            int iconColor = selected ? theme.textPrimary : theme.textSecondary;
            graphics.drawCenteredString(font, item.getIconEmoji(), x, y - 12, iconColor);
        }
    }

    /**
     * Renders an item's name, truncating if too long.
     */
    private static void renderItemName(GuiGraphics graphics, Font font,
                                         RadialMenuItem item, int x, int y,
                                         boolean selected, boolean isActive,
                                         RadialMenuConfig.ColorTheme theme) {
        String name = item.getName();
        int maxWidth = 56;

        if (font.width(name) > maxWidth) {
            String ellipsis = "...";
            int minChars = Math.min(6, name.length());
            while (font.width(name + ellipsis) > maxWidth && name.length() > minChars) {
                name = name.substring(0, name.length() - 1);
            }
            name += ellipsis;
        }

        int nameColor = selected
            ? theme.textPrimary
            : (isActive ? theme.active : theme.textSecondary);
        graphics.drawCenteredString(font, name, x, y + 4, nameColor);
    }

    /**
     * Renders an item's status indicator (ON/OFF or subcategory arrow).
     */
    private static void renderItemStatus(GuiGraphics graphics, Font font,
                                          RadialMenuItem item, int x, int y,
                                          boolean isActive, RadialMenuConfig.ColorTheme theme) {
        if (item.isToggle()) {
            String status = isActive ? "ON" : "OFF";
            int statusColor = isActive ? theme.active : 0xFF666666;
            graphics.drawCenteredString(font, status, x, y + 16, statusColor);
        } else if (item.isSubcategoryLink()) {
            graphics.drawCenteredString(font, "▸", x, y + 16, theme.textSecondary);
        }
    }

    // ================================================================
    // CATEGORY GLOW EFFECT
    // ================================================================

    /**
     * Renders a glow effect behind the selected category.
     *
     * @param graphics      GUI graphics context
     * @param categories    list of categories
     * @param categoryIndex selected category index
     * @param centerX       center X coordinate
     * @param centerY       center Y coordinate
     * @param itemRadius    radius for glow placement
     * @param categoryAnimations animation progress for each category
     */
    public static void renderCategoryGlow(GuiGraphics graphics,
                                           List<RadialCategory> categories,
                                           int categoryIndex,
                                           int centerX, int centerY, int itemRadius,
                                           float[] categoryAnimations) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(categories, "categories cannot be null");
        Objects.requireNonNull(categoryAnimations, "categoryAnimations cannot be null");

        if (categoryIndex >= categories.size()) return;

        RadialCategory cat = categories.get(categoryIndex);
        float anim = categoryIndex < categoryAnimations.length ? categoryAnimations[categoryIndex] : 0;

        double segmentAngle = RadialMenuConstants.TWO_PI / categories.size();
        double midAngle = -Math.PI / 2 + categoryIndex * segmentAngle;

        int glowX = (int) (centerX + Math.cos(midAngle) * itemRadius);
        int glowY = (int) (centerY + Math.sin(midAngle) * itemRadius);

        int glowRadius = (int) (60 * anim);
        int glowColorVal = (cat.getColor() & 0x00FFFFFF) | ((int) (0x40 * anim) << 24);
        RadialGeometry.renderRadialGradient(graphics, glowX, glowY, glowRadius, glowColorVal, 0x00000000);
    }
}
