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
        double startOffset = RadialMenuConstants.CATEGORY_START_OFFSET;
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
        int baseColor = selected ? RadialMenuConstants.COLOR_SELECTED_BG : RadialMenuConstants.COLOR_UNSELECTED_BG;
        int segColor = selected
            ? RadialGeometry.blendColors(baseColor, cat.getColor(), RadialMenuConstants.CATEGORY_SELECTED_BLEND)
            : baseColor;
        segColor = RadialGeometry.applyAlpha(segColor, alphaInt);

        RadialGeometry.renderArcSegment(graphics, config.centerX, config.centerY,
            config.innerRadius, config.outerRadius, startAngle, endAngle, segColor);

        // Outer border
        int borderCol = selected ? cat.getColor() : RadialMenuConstants.COLOR_BORDER;
        borderCol = RadialGeometry.applyAlpha(borderCol, alphaInt);
        RadialGeometry.renderArcOutline(graphics, config.centerX, config.centerY,
            config.outerRadius, startAngle, endAngle, borderCol, RadialMenuConstants.BORDER_WIDTH_DEFAULT);

        // Divider line
        int dividerColor = RadialGeometry.applyAlpha(RadialMenuConstants.COLOR_BORDER, alphaInt);
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
        renderCategoryIcon(graphics, font, cat, iconX,
            iconY + RadialMenuConstants.CATEGORY_ICON_OFFSET_Y, selected, alphaInt, config);

        // Category name
        int textColor = selected ? RadialMenuConstants.COLOR_TEXT_PRIMARY : RadialMenuConstants.COLOR_TEXT_SECONDARY;
        textColor = RadialGeometry.applyAlpha(textColor, alphaInt);
        graphics.drawCenteredString(font, cat.getName(), iconX,
            iconY + RadialMenuConstants.CATEGORY_LABEL_OFFSET_Y, textColor);

        // Active items badge
        int activeCount = cat.countActiveItems();
        int badgeThreshold = (int) (RadialMenuConstants.BADGE_ALPHA_THRESHOLD * 255);
        if (activeCount > 0 && alphaInt > badgeThreshold) { // Only show badges when mostly visible
            int badgeColor = RadialGeometry.applyAlpha(config.theme.active, alphaInt);
            renderBadge(graphics, font,
                iconX + RadialMenuConstants.CATEGORY_BADGE_OFFSET_X,
                iconY + RadialMenuConstants.CATEGORY_BADGE_OFFSET_Y,
                activeCount, badgeColor, config.pulsePhase);
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
            graphics.renderItem(cat.getIconStack(),
                x + RadialMenuConstants.CATEGORY_ITEMSTACK_OFFSET_X,
                y + RadialMenuConstants.CATEGORY_ITEMSTACK_OFFSET_Y);
        } else {
            graphics.drawCenteredString(font, cat.getIcon(), x, y, iconColor);
        }
    }

    /**
     * Renders an active count badge.
     */
    private static void renderBadge(GuiGraphics graphics, Font font,
                                     int x, int y, int count, int color, float pulsePhase) {
        float pulse = RadialMenuConstants.BADGE_PULSE_BASE +
            RadialMenuConstants.BADGE_PULSE_VARIATION *
                (float) Math.sin(pulsePhase * RadialMenuConstants.BADGE_PULSE_SPEED);
        int badgeColor = RadialGeometry.blendColors(color, 0xFFFFFFFF,
            pulse * RadialMenuConstants.BADGE_BLEND_FACTOR);

        graphics.fill(
            x - RadialMenuConstants.BADGE_HALF_WIDTH,
            y + RadialMenuConstants.BADGE_TOP_OFFSET,
            x + RadialMenuConstants.BADGE_HALF_WIDTH,
            y + RadialMenuConstants.BADGE_BOTTOM_OFFSET,
            RadialMenuConstants.BADGE_BG_COLOR);
        graphics.drawCenteredString(font, String.valueOf(count), x,
            y + RadialMenuConstants.BADGE_TEXT_OFFSET_Y, badgeColor);
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
            innerRadius - RadialMenuConstants.RING_BORDER_THICKNESS,
            innerRadius, RadialMenuConstants.COLOR_INNER_RING);

        // Outer ring border
        RadialGeometry.renderRing(graphics, centerX, centerY,
            outerRadius,
            outerRadius + RadialMenuConstants.RING_BORDER_THICKNESS,
            RadialMenuConstants.COLOR_DIVIDER);
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

        // Use getVisibleItems() so indices match selectedItemIndex from RadialMenuScreenV3
        List<RadialMenuItem> visibleItems = category.getVisibleItems();
        int numItems = visibleItems.size();
        if (numItems == 0) return;

        double segmentAngle = RadialMenuConstants.TWO_PI / categories.size();
        double startOffset = RadialMenuConstants.CATEGORY_START_OFFSET;
        double catStartAngle = startOffset + (config.selectedCategoryIndex - 0.5) * segmentAngle;
        double itemAngleStep = segmentAngle / numItems;

        int baseRadius = config.outerRadius + RadialMenuConstants.ITEM_RING_OFFSET;
        int itemSize = RadialMenuConstants.ITEM_BASE_SIZE;

        for (int i = 0; i < numItems; i++) {
            RadialMenuItem item = visibleItems.get(i);

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
        int itemX = (int) (config.centerX + Math.cos(itemAngle) *
            (baseRadius + RadialMenuConstants.ITEM_HOVER_OFFSET * itemAnim));
        int itemY = (int) (config.centerY + Math.sin(itemAngle) *
            (baseRadius + RadialMenuConstants.ITEM_HOVER_OFFSET * itemAnim));

        int itemRadiusSize = itemSize + (int) (RadialMenuConstants.ITEM_HOVER_SIZE_BONUS * itemAnim);

        // Background
        int bgColor = itemSelected
            ? RadialMenuConstants.COLOR_INNER_RING
            : RadialMenuConstants.COLOR_BG_DARK;
        if (isActive) {
            bgColor = RadialGeometry.blendColors(bgColor, config.theme.active,
                RadialMenuConstants.ITEM_ACTIVE_BLEND);
        }
        RadialGeometry.renderCircle(graphics, itemX, itemY, itemRadiusSize, bgColor);

        // Border
        int borderColor = isActive
            ? config.theme.active
            : (itemSelected ? category.getColor() : RadialMenuConstants.COLOR_DIVIDER);
        int borderWidth = itemSelected
            ? RadialMenuConstants.BORDER_WIDTH_SELECTED
            : RadialMenuConstants.BORDER_WIDTH_DEFAULT;
        RadialGeometry.renderRing(graphics, itemX, itemY,
            itemRadiusSize - borderWidth, itemRadiusSize, borderColor);

        // Inner highlight for depth when selected
        if (itemSelected) {
            int highlightColor = RadialGeometry.blendColors(category.getColor(), 0xFFFFFFFF,
                RadialMenuConstants.ITEM_HIGHLIGHT_BLEND);
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
            graphics.renderItem(iconStack,
                x + RadialMenuConstants.ITEM_ICON_STACK_OFFSET_X,
                y + RadialMenuConstants.ITEM_ICON_STACK_OFFSET_Y);
        } else {
            int iconColor = selected ? theme.textPrimary : theme.textSecondary;
            graphics.drawCenteredString(font, item.getIconEmoji(), x,
                y + RadialMenuConstants.ITEM_ICON_TEXT_OFFSET_Y, iconColor);
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
        int maxWidth = RadialMenuConstants.ITEM_NAME_MAX_WIDTH;

        if (font.width(name) > maxWidth) {
            String ellipsis = "...";
            int minChars = Math.min(RadialMenuConstants.ITEM_NAME_MIN_CHARS, name.length());
            while (font.width(name + ellipsis) > maxWidth && name.length() > minChars) {
                name = name.substring(0, name.length() - 1);
            }
            name += ellipsis;
        }

        int nameColor = selected
            ? theme.textPrimary
            : (isActive ? theme.active : theme.textSecondary);
        graphics.drawCenteredString(font, name, x, y + RadialMenuConstants.ITEM_NAME_OFFSET_Y, nameColor);
    }

    /**
     * Renders an item's status indicator (ON/OFF or subcategory arrow).
     */
    private static void renderItemStatus(GuiGraphics graphics, Font font,
                                          RadialMenuItem item, int x, int y,
                                          boolean isActive, RadialMenuConfig.ColorTheme theme) {
        if (item.isToggle()) {
            String status = isActive ? "ON" : "OFF";
            int statusColor = isActive ? theme.active : RadialMenuConstants.ITEM_STATUS_INACTIVE_COLOR;
            graphics.drawCenteredString(font, status, x,
                y + RadialMenuConstants.ITEM_STATUS_OFFSET_Y, statusColor);
        } else if (item.isSubcategoryLink()) {
            graphics.drawCenteredString(font, "▸", x,
                y + RadialMenuConstants.ITEM_STATUS_OFFSET_Y, theme.textSecondary);
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
        double midAngle = RadialMenuConstants.CATEGORY_START_OFFSET + categoryIndex * segmentAngle;

        int glowX = (int) (centerX + Math.cos(midAngle) * itemRadius);
        int glowY = (int) (centerY + Math.sin(midAngle) * itemRadius);

        int glowRadius = (int) (RadialMenuConstants.CATEGORY_GLOW_RADIUS * anim);
        int glowAlpha = (int) (RadialMenuConstants.CATEGORY_GLOW_ALPHA * anim);
        int glowColorVal = (cat.getColor() & 0x00FFFFFF) | (glowAlpha << 24);
        RadialGeometry.renderRadialGradient(graphics, glowX, glowY, glowRadius, glowColorVal, 0x00000000);
    }
}
