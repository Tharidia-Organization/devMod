package com.devmod.client.ui.radial.render;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.radial.RadialCategory;
import com.devmod.client.ui.radial.RadialMenuConfig;
import com.devmod.client.ui.radial.RadialMenuItem;
import com.devmod.client.ui.radial.config.RadialMenuConstants;

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
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font cannot be null");
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

            renderCategorySegment(graphics, safeFont, cat, i, numCategories,
                config, selected, alphaInt, segmentAngle, startOffset);
        }
    }

    /**
     * Renders a single category segment.
     */
    private static void renderCategorySegment(GuiGraphics graphics, @Nonnull Font font,
                                               RadialCategory cat, int index, int numCategories,
                                               RingConfig config, boolean selected,
                                               int alphaInt, double segmentAngle, double startOffset) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
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
        renderCategoryIcon(graphics, safeFont, cat, iconX,
            iconY + RadialMenuConstants.CATEGORY_ICON_OFFSET_Y, selected, alphaInt, config);

        // Category name
        int textColor = selected ? RadialMenuConstants.COLOR_TEXT_PRIMARY : RadialMenuConstants.COLOR_TEXT_SECONDARY;
        textColor = RadialGeometry.applyAlpha(textColor, alphaInt);
        @Nonnull String categoryName = Objects.requireNonNull(Objects.requireNonNullElse(cat.getName(), ""), "categoryName");
        graphics.drawCenteredString(safeFont, categoryName, iconX,
            iconY + RadialMenuConstants.CATEGORY_LABEL_OFFSET_Y, textColor);

        // Active items badge
        int activeCount = cat.countActiveItems();
        int badgeThreshold = (int) (RadialMenuConstants.BADGE_ALPHA_THRESHOLD * 255);
        if (activeCount > 0 && alphaInt > badgeThreshold) { // Only show badges when mostly visible
            int badgeColor = RadialGeometry.applyAlpha(config.theme.active, alphaInt);
            renderBadge(graphics, safeFont,
                iconX + RadialMenuConstants.CATEGORY_BADGE_OFFSET_X,
                iconY + RadialMenuConstants.CATEGORY_BADGE_OFFSET_Y,
                activeCount, badgeColor, config.pulsePhase);
        }
    }

    /**
     * Renders a category icon with alpha support for transitions.
     */
    private static void renderCategoryIcon(GuiGraphics graphics, @Nonnull Font font,
                                            RadialCategory cat, int x, int y,
                                            boolean selected, int alpha, RingConfig config) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        int iconColor = selected ? cat.getColor() : config.theme.textSecondary;
        iconColor = RadialGeometry.applyAlpha(iconColor, alpha);

        ItemStack iconStack = cat.getIconStack();
        boolean useItemStack = config.iconMode == RadialMenuConfig.IconMode.ITEMSTACK ||
            (config.iconMode == RadialMenuConfig.IconMode.AUTO && iconStack != null);

        if (useItemStack && iconStack != null && alpha > RadialMenuConstants.ITEMSTACK_ALPHA_THRESHOLD) {
            // Only render item stacks when mostly opaque (they don't support alpha well)
            @Nonnull ItemStack safeIconStack = Objects.requireNonNull(iconStack, "iconStack");
            graphics.renderItem(safeIconStack,
                x + RadialMenuConstants.CATEGORY_ITEMSTACK_OFFSET_X,
                y + RadialMenuConstants.CATEGORY_ITEMSTACK_OFFSET_Y);
        } else {
            @Nonnull String iconText = Objects.requireNonNull(Objects.requireNonNullElse(cat.getIcon(), ""), "iconText");
            graphics.drawCenteredString(safeFont, iconText, x, y, iconColor);
        }
    }

    /**
     * Renders an active count badge.
     */
    private static void renderBadge(GuiGraphics graphics, @Nonnull Font font,
                                     int x, int y, int count, int color, float pulsePhase) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
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
        @Nonnull String countText = Objects.requireNonNull(String.valueOf(count), "countText");
        graphics.drawCenteredString(safeFont, countText, x,
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
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(categories, "categories cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        // Use getVisibleItems() so indices match selectedItemIndex from RadialMenuScreen
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

            renderItem(graphics, safeFont, item, i, config, category,
                catStartAngle, itemAngleStep, baseRadius, itemSize);
        }
    }

    /**
     * Renders a single menu item.
     */
    private static void renderItem(GuiGraphics graphics, @Nonnull Font font,
                                    RadialMenuItem item, int index,
                                    ItemsConfig config, RadialCategory category,
                                    double catStartAngle, double itemAngleStep,
                                    int baseRadius, int itemSize) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        boolean itemSelected = (index == config.selectedItemIndex);
        boolean isActive = item.isToggle() && item.isActive();
        boolean canExecute = item.canExecute();
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
        if (!canExecute) {
            bgColor = RadialGeometry.blendColors(bgColor, RadialMenuConstants.COLOR_INACTIVE,
                RadialMenuConstants.ITEM_DISABLED_BLEND);
        }
        RadialGeometry.renderCircle(graphics, itemX, itemY, itemRadiusSize, bgColor);

        // Border
        int borderColor = isActive
            ? config.theme.active
            : (itemSelected ? category.getColor() : RadialMenuConstants.COLOR_DIVIDER);
        if (!canExecute) {
            borderColor = RadialGeometry.blendColors(borderColor, RadialMenuConstants.COLOR_INACTIVE,
                RadialMenuConstants.ITEM_DISABLED_BLEND);
        }
        int borderWidth = itemSelected
            ? RadialMenuConstants.BORDER_WIDTH_SELECTED
            : RadialMenuConstants.BORDER_WIDTH_DEFAULT;
        RadialGeometry.renderRing(graphics, itemX, itemY,
            itemRadiusSize - borderWidth, itemRadiusSize, borderColor);

        // Inner highlight for depth when selected
        if (itemSelected) {
            int highlightColor = RadialGeometry.blendColors(category.getColor(), 0xFFFFFFFF,
                RadialMenuConstants.ITEM_HIGHLIGHT_BLEND);
            if (!canExecute) {
                highlightColor = RadialGeometry.blendColors(highlightColor, RadialMenuConstants.COLOR_INACTIVE,
                    RadialMenuConstants.ITEM_DISABLED_BLEND);
            }
            RadialGeometry.renderRing(graphics, itemX, itemY,
                itemRadiusSize - borderWidth - 1, itemRadiusSize - borderWidth, highlightColor);
        }

        // Icon
        renderItemIcon(graphics, safeFont, item, itemX, itemY, itemSelected, canExecute, config.theme);

        // Name (truncated if needed)
        renderItemName(graphics, safeFont, item, itemX, itemY, itemSelected, isActive, canExecute, config.theme);

        // Toggle status indicator
        renderItemStatus(graphics, safeFont, item, itemX, itemY, isActive, canExecute, config.theme);
    }

    /**
     * Renders an item's icon.
     */
    private static void renderItemIcon(GuiGraphics graphics, @Nonnull Font font,
                                         RadialMenuItem item, int x, int y,
                                         boolean selected, boolean canExecute, RadialMenuConfig.ColorTheme theme) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        ItemStack iconStack = Objects.requireNonNullElse(item.getIconStack(), ItemStack.EMPTY);
        if (!iconStack.isEmpty()) {
            graphics.renderItem(iconStack,
                x + RadialMenuConstants.ITEM_ICON_STACK_OFFSET_X,
                y + RadialMenuConstants.ITEM_ICON_STACK_OFFSET_Y);
        } else {
            int iconColor = selected ? theme.textPrimary : theme.textSecondary;
            if (!canExecute) {
                iconColor = RadialMenuConstants.COLOR_INACTIVE;
            }
            @Nonnull String iconText = Objects.requireNonNull(Objects.requireNonNullElse(item.getIconEmoji(), ""), "iconText");
            graphics.drawCenteredString(safeFont, iconText, x,
                y + RadialMenuConstants.ITEM_ICON_TEXT_OFFSET_Y, iconColor);
        }
    }

    /**
     * Renders an item's name, truncating if too long.
     */
    private static void renderItemName(GuiGraphics graphics, @Nonnull Font font,
                                         RadialMenuItem item, int x, int y,
                                         boolean selected, boolean isActive, boolean canExecute,
                                         RadialMenuConfig.ColorTheme theme) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        @Nonnull String name = Objects.requireNonNull(Objects.requireNonNullElse(item.getName(), ""), "name");
        int maxWidth = RadialMenuConstants.ITEM_NAME_MAX_WIDTH;

        java.util.List<String> lines = splitNameLines(safeFont, name, maxWidth);
        int nameColor = selected
            ? theme.textPrimary
            : (isActive ? theme.active : theme.textSecondary);
        if (!canExecute) {
            nameColor = RadialMenuConstants.COLOR_INACTIVE;
        }
        @Nonnull String line0 = Objects.requireNonNull(lines.get(0), "line0");
        if (lines.size() == 1) {
            graphics.drawCenteredString(safeFont, line0, x,
                y + RadialMenuConstants.ITEM_NAME_OFFSET_Y, nameColor);
            return;
        }

        int lineHeight = safeFont.lineHeight;
        int line1Y = y + RadialMenuConstants.ITEM_NAME_OFFSET_Y - (lineHeight / 2);
        int line2Y = line1Y + lineHeight;
        @Nonnull String line1 = line0;
        @Nonnull String line2 = Objects.requireNonNull(lines.get(1), "line2");
        graphics.drawCenteredString(safeFont, line1, x, line1Y, nameColor);
        graphics.drawCenteredString(safeFont, line2, x, line2Y, nameColor);
    }

    private static java.util.List<String> splitNameLines(@Nonnull Font font, @Nonnull String name, int maxWidth) {
        if (font.width(name) <= maxWidth) {
            return java.util.List.of(name);
        }

        String[] words = name.split(" ");
        if (words.length < 2) {
            return java.util.List.of(truncateToWidth(font, name, maxWidth));
        }

        int bestSplit = -1;
        int bestMaxWidth = Integer.MAX_VALUE;
        for (int i = 0; i < words.length - 1; i++) {
            @Nonnull String line1 = Objects.requireNonNull(joinWords(words, 0, i), "line1");
            @Nonnull String line2 = Objects.requireNonNull(joinWords(words, i + 1, words.length - 1), "line2");
            int line1Width = font.width(line1);
            int line2Width = font.width(line2);
            int maxLineWidth = Math.max(line1Width, line2Width);
            if (line1Width <= maxWidth && line2Width <= maxWidth && maxLineWidth < bestMaxWidth) {
                bestSplit = i;
                bestMaxWidth = maxLineWidth;
            }
        }

        if (bestSplit != -1) {
            return java.util.List.of(
                joinWords(words, 0, bestSplit),
                joinWords(words, bestSplit + 1, words.length - 1)
            );
        }

        return java.util.List.of(truncateToWidth(font, name, maxWidth));
    }

    private static String truncateToWidth(@Nonnull Font font, @Nonnull String name, int maxWidth) {
        String trimmed = name;
        String ellipsis = "...";
        int minChars = Math.min(RadialMenuConstants.ITEM_NAME_MIN_CHARS, trimmed.length());
        while (font.width(trimmed + ellipsis) > maxWidth && trimmed.length() > minChars) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private static String joinWords(String[] words, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(words[i]);
        }
        return builder.toString();
    }

    /**
     * Renders an item's status indicator (ON/OFF or subcategory arrow).
     */
    private static void renderItemStatus(GuiGraphics graphics, @Nonnull Font font,
                                          RadialMenuItem item, int x, int y,
                                          boolean isActive, boolean canExecute, RadialMenuConfig.ColorTheme theme) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        if (item.isToggle()) {
            @Nonnull String status = Objects.requireNonNull(isActive ? "ON" : "OFF", "status");
            int statusColor = isActive ? theme.active : RadialMenuConstants.ITEM_STATUS_INACTIVE_COLOR;
            if (!canExecute) {
                statusColor = RadialMenuConstants.COLOR_INACTIVE;
            }
            graphics.drawCenteredString(safeFont, status, x,
                y + RadialMenuConstants.ITEM_STATUS_OFFSET_Y, statusColor);
        } else if (item.isSubcategoryLink()) {
            @Nonnull String indicator = Objects.requireNonNull(">", "indicator");
            int indicatorColor = canExecute ? theme.textSecondary : RadialMenuConstants.COLOR_INACTIVE;
            graphics.drawCenteredString(safeFont, indicator, x,
                y + RadialMenuConstants.ITEM_STATUS_OFFSET_Y, indicatorColor);
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
