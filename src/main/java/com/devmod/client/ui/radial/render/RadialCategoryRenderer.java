package com.devmod.client.ui.radial.render;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.radial.RadialActionSafety;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.radial.RadialCategory;
import com.devmod.client.ui.radial.RadialMenuConfig;
import com.devmod.client.ui.radial.RadialMenuItem;
import com.devmod.client.ui.radial.config.RadialMenuConstants;
import com.devmod.client.ui.radial.config.RadialMenuScaler;

public final class RadialCategoryRenderer {
    private static final Splitter SPACE_SPLITTER = Splitter.on(' ');
    private static final double MIN_LABEL_ANGLE = 0.25; // ~14deg; below this show only selected

    private RadialCategoryRenderer() {
        // Utility class - no instantiation
    }

    // ================================================================
    // CATEGORY RING RENDERING
    // ================================================================

    /**
     * Configuration for rendering the category ring.
     */
    public static final class RingConfig {
        public final int centerX;
        public final int centerY;
        public final int innerRadius;
        public final int outerRadius;
        public final int itemRadius;
        public final int selectedIndex;
        public final float[] categoryAnimations;
        public final RadialMenuConfig.ColorTheme theme;
        public final RadialMenuConfig.IconMode iconMode;
        public final float pulsePhase;

        /**
         * Creates a ring config instance.
         */
        public RingConfig(int centerX,
                          int centerY,
                          int innerRadius,
                          int outerRadius,
                          int itemRadius,
                          int selectedIndex,
                          float[] categoryAnimations,
                          RadialMenuConfig.ColorTheme theme,
                          RadialMenuConfig.IconMode iconMode,
                          float pulsePhase) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.innerRadius = innerRadius;
            this.outerRadius = outerRadius;
            this.itemRadius = itemRadius;
            this.selectedIndex = selectedIndex;
            this.categoryAnimations = Objects.requireNonNull(categoryAnimations, "categoryAnimations cannot be null");
            this.theme = Objects.requireNonNull(theme, "theme cannot be null");
            this.iconMode = Objects.requireNonNull(iconMode, "iconMode cannot be null");
            this.pulsePhase = pulsePhase;
        }

        public int centerX() { return centerX; }
        public int centerY() { return centerY; }
        public int innerRadius() { return innerRadius; }
        public int outerRadius() { return outerRadius; }
        public int itemRadius() { return itemRadius; }
        public int selectedIndex() { return selectedIndex; }
        public float[] categoryAnimations() { return categoryAnimations; }
        public RadialMenuConfig.ColorTheme theme() { return theme; }
        public RadialMenuConfig.IconMode iconMode() { return iconMode; }
        public float pulsePhase() { return pulsePhase; }
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

            renderCategorySegment(graphics, safeFont, cat, i,
                config, selected, alphaInt, segmentAngle, startOffset);
        }
    }

    /**
     * Renders a single category segment.
     */
    private static void renderCategorySegment(GuiGraphics graphics, @Nonnull Font font,
                                               RadialCategory cat, int index,
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
            config.outerRadius, startAngle, endAngle, borderCol,
            RadialMenuScaler.scaleConstant(RadialMenuConstants.BORDER_WIDTH_DEFAULT));

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
            iconY + RadialMenuScaler.scaleConstant(RadialMenuConstants.CATEGORY_ICON_OFFSET_Y), selected, alphaInt, config);

        // Category name (truncate to avoid overlap on small screens/locales)
        int textColor = selected ? RadialMenuConstants.COLOR_TEXT_PRIMARY : RadialMenuConstants.COLOR_TEXT_SECONDARY;
        textColor = RadialGeometry.applyAlpha(textColor, alphaInt);
        @Nonnull String categoryName = Objects.requireNonNull(Objects.requireNonNullElse(cat.getName(), ""), "categoryName");
        int maxLabelWidth = computeCategoryLabelWidth(config.itemRadius, segmentAngle);
        float fontScale = resolveFontScale();
        int maxLabelWidthFont = toFontWidth(maxLabelWidth, fontScale);
        @Nonnull String label = Objects.requireNonNull(truncateLabel(safeFont, categoryName, maxLabelWidthFont), "label");
        drawCenteredStringScaled(graphics, safeFont, label, iconX,
            iconY + RadialMenuScaler.scaleConstant(RadialMenuConstants.CATEGORY_LABEL_OFFSET_Y), textColor, fontScale);

        // Active items badge
        int activeCount = cat.countActiveItems();
        int badgeThreshold = (int) (RadialMenuConstants.BADGE_ALPHA_THRESHOLD * 255);
        if (activeCount > 0 && alphaInt > badgeThreshold) { // Only show badges when mostly visible
            int badgeColor = RadialGeometry.applyAlpha(config.theme.active, alphaInt);
            renderBadge(graphics, safeFont,
                iconX + RadialMenuScaler.scaleConstant(RadialMenuConstants.CATEGORY_BADGE_OFFSET_X),
                iconY + RadialMenuScaler.scaleConstant(RadialMenuConstants.CATEGORY_BADGE_OFFSET_Y),
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
                x + RadialMenuScaler.scaleConstant(RadialMenuConstants.CATEGORY_ITEMSTACK_OFFSET_X),
                y + RadialMenuScaler.scaleConstant(RadialMenuConstants.CATEGORY_ITEMSTACK_OFFSET_Y));
        } else {
            @Nonnull String iconText = Objects.requireNonNull(Objects.requireNonNullElse(cat.getIcon(), ""), "iconText");
            float fontScale = resolveFontScale();
            drawCenteredStringScaled(graphics, safeFont, iconText, x, y, iconColor, fontScale);
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
        int badgeColor = RadialGeometry.blendColors(color, DesignTokens.Text.WHITE,
            pulse * RadialMenuConstants.BADGE_BLEND_FACTOR);

        int badgeHalfWidth = RadialMenuScaler.scaleConstant(RadialMenuConstants.BADGE_HALF_WIDTH);
        int badgeTopOffset = RadialMenuScaler.scaleConstant(RadialMenuConstants.BADGE_TOP_OFFSET);
        int badgeBottomOffset = RadialMenuScaler.scaleConstant(RadialMenuConstants.BADGE_BOTTOM_OFFSET);
        graphics.fill(
            x - badgeHalfWidth,
            y + badgeTopOffset,
            x + badgeHalfWidth,
            y + badgeBottomOffset,
            RadialMenuConstants.BADGE_BG_COLOR);
        @Nonnull String countText = Objects.requireNonNull(String.valueOf(count), "countText");
        float fontScale = resolveFontScale();
        drawCenteredStringScaled(graphics, safeFont, countText, x,
            y + RadialMenuScaler.scaleConstant(RadialMenuConstants.BADGE_TEXT_OFFSET_Y), badgeColor, fontScale);
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
        int borderThickness = RadialMenuScaler.scaleConstant(RadialMenuConstants.RING_BORDER_THICKNESS);
        // Inner ring border
        RadialGeometry.renderRing(graphics, centerX, centerY,
            innerRadius - borderThickness,
            innerRadius, RadialMenuConstants.COLOR_INNER_RING);

        // Outer ring border
        RadialGeometry.renderRing(graphics, centerX, centerY,
            outerRadius,
            outerRadius + borderThickness,
            RadialMenuConstants.COLOR_DIVIDER);
    }

    /**
     * Renders a keyboard focus ring for the selected category.
     */
    public static void renderCategoryFocusRing(GuiGraphics graphics,
                                                List<RadialCategory> categories,
                                                RingConfig config,
                                                int focusIndex,
                                                float alpha) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(categories, "categories cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        int numCategories = categories.size();
        if (numCategories == 0 || focusIndex < 0 || focusIndex >= numCategories) {
            return;
        }

        double segmentAngle = RadialMenuConstants.TWO_PI / numCategories;
        double startOffset = RadialMenuConstants.CATEGORY_START_OFFSET;
        double startAngle = startOffset + (focusIndex - 0.5) * segmentAngle;
        double endAngle = startAngle + segmentAngle;

        int alphaInt = (int) (alpha * 255);
        RadialCategory cat = categories.get(focusIndex);

        int borderWidth = RadialMenuScaler.scaleConstant(RadialMenuConstants.FOCUS_RING_BORDER_WIDTH);
        int glowWidth = RadialMenuScaler.scaleConstant(RadialMenuConstants.FOCUS_RING_GLOW_WIDTH);
        int glowRadius = config.outerRadius + Math.max(1, glowWidth / 2);

        int borderColor = RadialGeometry.applyAlpha(cat.getColor(), alphaInt);
        int glowAlpha = (alphaInt * RadialMenuConstants.FOCUS_RING_GLOW_ALPHA) / 255;
        int glowColor = RadialGeometry.applyAlpha(cat.getColor(), glowAlpha);

        // Glow (soft outer outline)
        RadialGeometry.renderArcOutline(graphics, config.centerX, config.centerY,
            glowRadius, startAngle, endAngle, glowColor, glowWidth);

        // Thicker focus border
        RadialGeometry.renderArcOutline(graphics, config.centerX, config.centerY,
            config.outerRadius, startAngle, endAngle, borderColor, borderWidth);
    }

    // ================================================================
    // CATEGORY ITEMS RENDERING
    // ================================================================

    /**
     * Configuration for rendering category items.
     */
    public static final class ItemsConfig {
        public final int centerX;
        public final int centerY;
        public final int outerRadius;
        public final int selectedCategoryIndex;
        public final int selectedItemIndex;
        public final float[] itemAnimations;
        public final RadialMenuConfig.ColorTheme theme;
        /** Shake offset for each item (horizontal pixel offset, null if no shake) */
        @Nullable public final float[] shakeOffsets;
        /** Flash alpha for each item (0-1, null if no flash) */
        @Nullable public final float[] flashAlphas;
        /** Flash color for each item (ARGB, null if default) */
        @Nullable public final int[] flashColors;
        /** Default flash color (ARGB) */
        public final int flashBlockedColor;

        /**
         * Creates an items config instance.
         */
        public ItemsConfig(int centerX,
                           int centerY,
                           int outerRadius,
                           int selectedCategoryIndex,
                           int selectedItemIndex,
                           float[] itemAnimations,
                           RadialMenuConfig.ColorTheme theme) {
            this(centerX, centerY, outerRadius, selectedCategoryIndex, selectedItemIndex,
                 itemAnimations, theme, null, null, null, RadialMenuConstants.COLOR_BLOCKED_TINT);
        }

        /**
         * Creates an items config instance with shake and flash support.
         */
        public ItemsConfig(int centerX,
                           int centerY,
                           int outerRadius,
                           int selectedCategoryIndex,
                           int selectedItemIndex,
                           float[] itemAnimations,
                           RadialMenuConfig.ColorTheme theme,
                           @Nullable float[] shakeOffsets,
                           @Nullable float[] flashAlphas,
                           @Nullable int[] flashColors,
                           int flashBlockedColor) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.outerRadius = outerRadius;
            this.selectedCategoryIndex = selectedCategoryIndex;
            this.selectedItemIndex = selectedItemIndex;
            this.itemAnimations = Objects.requireNonNull(itemAnimations, "itemAnimations cannot be null");
            this.theme = Objects.requireNonNull(theme, "theme cannot be null");
            this.shakeOffsets = shakeOffsets;
            this.flashAlphas = flashAlphas;
            this.flashColors = flashColors;
            this.flashBlockedColor = flashBlockedColor;
        }

        public int centerX() { return centerX; }
        public int centerY() { return centerY; }
        public int outerRadius() { return outerRadius; }
        public int selectedCategoryIndex() { return selectedCategoryIndex; }
        public int selectedItemIndex() { return selectedItemIndex; }
        public float[] itemAnimations() { return itemAnimations; }
        public RadialMenuConfig.ColorTheme theme() { return theme; }

        /** Gets shake offset for an item, or 0 if no shake. */
        public float getShakeOffset(int index) {
            float[] offsets = shakeOffsets;
            return offsets != null && index >= 0 && index < offsets.length
                ? offsets[index] : 0f;
        }

        /** Gets flash alpha for an item, or 0 if no flash. */
        public float getFlashAlpha(int index) {
            float[] alphas = flashAlphas;
            return alphas != null && index >= 0 && index < alphas.length
                ? alphas[index] : 0f;
        }

        /** Gets flash color for an item, falling back to default if unset. */
        public int getFlashColor(int index) {
            int[] colors = flashColors;
            if (colors != null && index >= 0 && index < colors.length) {
                int color = colors[index];
                if (color != 0) {
                    return color;
                }
            }
            return flashBlockedColor;
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
                                            List<RadialMenuItem> visibleItems,
                                            List<RadialCategory> categories,
                                            ItemsConfig config) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(visibleItems, "visibleItems cannot be null");
        Objects.requireNonNull(categories, "categories cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        int numItems = visibleItems.size();
        if (numItems == 0) return;

        double segmentAngle = RadialMenuConstants.TWO_PI / categories.size();
        double startOffset = RadialMenuConstants.CATEGORY_START_OFFSET;
        double catStartAngle = startOffset + (config.selectedCategoryIndex - 0.5) * segmentAngle;
        double itemAngleStep = segmentAngle / numItems;

        int baseRadius = config.outerRadius + RadialMenuScaler.getItemRingOffset();
        int itemSize = RadialMenuScaler.getItemSize();

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
        int hoverOffset = RadialMenuScaler.getItemHoverOffset();
        int itemX = (int) (config.centerX + Math.cos(itemAngle) *
            (baseRadius + hoverOffset * itemAnim));
        int itemY = (int) (config.centerY + Math.sin(itemAngle) *
            (baseRadius + hoverOffset * itemAnim));

        // Apply micro-shake offset (horizontal only for subtle effect)
        float shakeOffset = config.getShakeOffset(index);
        if (shakeOffset != 0f) {
            itemX += (int) shakeOffset;
        }

        int hoverSizeBonus = RadialMenuScaler.scaleConstant(RadialMenuConstants.ITEM_HOVER_SIZE_BONUS);
        int itemRadiusSize = itemSize + (int) (hoverSizeBonus * itemAnim);

        // Background
        int bgColor = itemSelected
            ? RadialMenuConstants.COLOR_INNER_RING
            : RadialMenuConstants.COLOR_BG_DARK;
        if (isActive) {
            bgColor = RadialGeometry.blendColors(bgColor, config.theme.active,
                RadialMenuConstants.ITEM_ACTIVE_BLEND);
        }
        if (!canExecute) {
            // BLOCKED state: use warning tint instead of grey
            bgColor = RadialGeometry.blendColors(bgColor, RadialMenuConstants.COLOR_BLOCKED_TINT,
                RadialMenuConstants.BLOCKED_BG_BLEND);
        }
        RadialGeometry.renderCircle(graphics, itemX, itemY, itemRadiusSize, bgColor);

        // Border
        int borderColor = isActive
            ? config.theme.active
            : (itemSelected ? category.getColor() : RadialMenuConstants.COLOR_DIVIDER);
        if (!canExecute) {
            // BLOCKED state: use warning border
            borderColor = RadialGeometry.blendColors(borderColor, RadialMenuConstants.COLOR_BLOCKED_BORDER,
                RadialMenuConstants.BLOCKED_BORDER_BLEND);
        }
        int borderWidth = itemSelected
            ? RadialMenuScaler.scaleConstant(RadialMenuConstants.BORDER_WIDTH_SELECTED)
            : RadialMenuScaler.scaleConstant(RadialMenuConstants.BORDER_WIDTH_DEFAULT);
        RadialGeometry.renderRing(graphics, itemX, itemY,
            itemRadiusSize - borderWidth, itemRadiusSize, borderColor);

        // Inner highlight for depth when selected
        if (itemSelected) {
            int highlightColor = RadialGeometry.blendColors(category.getColor(), DesignTokens.Text.WHITE,
                RadialMenuConstants.ITEM_HIGHLIGHT_BLEND);
            if (!canExecute) {
                // BLOCKED state: use warning highlight
                highlightColor = RadialGeometry.blendColors(highlightColor, RadialMenuConstants.COLOR_BLOCKED_BORDER,
                    RadialMenuConstants.BLOCKED_BORDER_BLEND);
            }
            RadialGeometry.renderRing(graphics, itemX, itemY,
                itemRadiusSize - borderWidth - 1, itemRadiusSize - borderWidth, highlightColor);
        }

        // Flash overlay for blocked/success/failed feedback
        float flashAlpha = config.getFlashAlpha(index);
        if (flashAlpha > 0f) {
            int flashColor = RadialGeometry.applyAlpha(config.getFlashColor(index), (int) (flashAlpha * 128));
            RadialGeometry.renderCircle(graphics, itemX, itemY, itemRadiusSize - borderWidth, flashColor);
        }

        // Icon
        renderItemIcon(graphics, safeFont, item, itemX, itemY, itemSelected, canExecute, config.theme);

        // Name (truncate to avoid overlap with adjacent items)
        int maxNameWidth = computeItemNameWidth(baseRadius, itemAngleStep);
        renderItemName(graphics, safeFont, item, itemX, itemY, config.centerX, config.centerY,
            itemRadiusSize, itemAngleStep, itemSelected, isActive, canExecute, config.theme, maxNameWidth);

        // Toggle status indicator
        renderItemStatus(graphics, safeFont, item, itemX, itemY, isActive, canExecute, config.theme);

        // Risk badge
        RadialActionSafety.RiskLevel riskLevel = RadialActionSafety.evaluate(item);
        if (riskLevel != RadialActionSafety.RiskLevel.SAFE) {
            renderRiskBadge(graphics, safeFont, itemX, itemY, riskLevel);
        }
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
                x + RadialMenuScaler.scaleConstant(RadialMenuConstants.ITEM_ICON_STACK_OFFSET_X),
                y + RadialMenuScaler.scaleConstant(RadialMenuConstants.ITEM_ICON_STACK_OFFSET_Y));
        } else {
            int iconColor = selected ? theme.textPrimary : theme.textSecondary;
            if (!canExecute) {
                // BLOCKED state: use muted warning color for icon
                iconColor = RadialMenuConstants.COLOR_BLOCKED_ICON;
            }
            @Nonnull String iconText = Objects.requireNonNull(Objects.requireNonNullElse(item.getIconEmoji(), ""), "iconText");
            float fontScale = resolveFontScale();
            drawCenteredStringScaled(graphics, safeFont, iconText, x,
                y + RadialMenuScaler.scaleConstant(RadialMenuConstants.ITEM_ICON_TEXT_OFFSET_Y), iconColor, fontScale);
        }
    }

    /**
     * Renders an item's name, truncating if too long.
     */
    private static void renderItemName(GuiGraphics graphics, @Nonnull Font font,
                                         RadialMenuItem item, int x, int y,
                                         int centerX, int centerY, int itemRadiusSize,
                                         double itemAngleStep, boolean selected, boolean isActive, boolean canExecute,
                                         RadialMenuConfig.ColorTheme theme, int maxWidth) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        @Nonnull String name = Objects.requireNonNull(Objects.requireNonNullElse(item.getName(), ""), "name");
        int resolvedMaxWidth = Math.max(RadialMenuScaler.scaleConstant(24), maxWidth);
        if (selected) {
            resolvedMaxWidth = Math.max(resolvedMaxWidth,
                RadialMenuScaler.scaleConstant(RadialMenuConstants.ITEM_NAME_MAX_WIDTH * 2));
        }
        float fontScale = resolveFontScale();

        if (!selected) {
            double minAngle = MIN_LABEL_ANGLE / Math.max(0.85, RadialMenuScaler.getScaleFactor());
            boolean denseLayout = itemAngleStep < minAngle;
            if (!RadialMenuScaler.showDetailedLabels() || denseLayout) {
                return;
            }
        }

        int dx = x - centerX;
        int dy = y - centerY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0.5f) {
            return;
        }
        float nx = dx / len;
        float ny = dy / len;
        int lineHeight = scaledLineHeight(safeFont, fontScale);
        int radialPadding = Math.max(RadialMenuScaler.scaleConstant(4), Math.round(lineHeight * 0.2f));
        float radialOffset = itemRadiusSize + radialPadding + (lineHeight / 2f);
        int anchorX = Math.round(x + nx * radialOffset);
        int anchorY = Math.round(y + ny * radialOffset);

        int alignMode = Math.abs(nx) < 0.25f ? 0 : (nx > 0f ? 1 : -1);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        int availableWidth;
        if (alignMode > 0) {
            availableWidth = Math.max(0, safeRight - anchorX);
        } else if (alignMode < 0) {
            availableWidth = Math.max(0, anchorX - safeLeft);
        } else {
            availableWidth = Math.max(0, Math.min(anchorX - safeLeft, safeRight - anchorX) * 2);
        }
        int effectiveMaxWidth = Math.min(resolvedMaxWidth, availableWidth);
        if (effectiveMaxWidth <= 0) {
            return;
        }
        int maxWidthFont = toFontWidth(effectiveMaxWidth, fontScale);
        java.util.List<String> lines = splitNameLines(safeFont, name, maxWidthFont);
        if (lines.isEmpty()) {
            return;
        }

        int nameColor = selected
            ? theme.textPrimary
            : (isActive ? theme.active : theme.textSecondary);
        if (!canExecute) {
            // BLOCKED state: use muted warning color for name
            nameColor = RadialMenuConstants.COLOR_BLOCKED_ICON;
        }
        @Nonnull String line0 = Objects.requireNonNull(lines.get(0), "line0");
        int totalHeight = lineHeight * lines.size();

        int maxLineWidth = 0;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            maxLineWidth = Math.max(maxLineWidth, scaledWidth(safeFont, line, fontScale));
        }

        int labelX;
        if (alignMode > 0) {
            int minX = safeLeft;
            int maxX = safeRight - maxLineWidth;
            labelX = maxX < minX ? clamp(anchorX, safeLeft, safeRight) : clamp(anchorX, minX, maxX);
        } else if (alignMode < 0) {
            int minX = safeLeft + maxLineWidth;
            int maxX = safeRight;
            labelX = maxX < minX ? clamp(anchorX, safeLeft, safeRight) : clamp(anchorX, minX, maxX);
        } else {
            int halfWidth = Math.max(1, maxLineWidth / 2);
            int minX = safeLeft + halfWidth;
            int maxX = safeRight - halfWidth;
            labelX = maxX < minX ? clamp(anchorX, safeLeft, safeRight) : clamp(anchorX, minX, maxX);
        }

        int labelY = clamp(anchorY - (totalHeight / 2), safeTop, safeBottom - totalHeight);
        if (lines.size() == 1) {
            if (alignMode == 0) {
                drawCenteredStringScaled(graphics, safeFont, line0, labelX, labelY, nameColor, fontScale);
            } else if (alignMode > 0) {
                drawStringScaled(graphics, safeFont, line0, labelX, labelY, nameColor, fontScale);
            } else {
                int lineWidth = scaledWidth(safeFont, line0, fontScale);
                drawStringScaled(graphics, safeFont, line0, labelX - lineWidth, labelY, nameColor, fontScale);
            }
            return;
        }

        int line1Y = labelY;
        int line2Y = line1Y + lineHeight;
        @Nonnull String line1 = line0;
        @Nonnull String line2 = Objects.requireNonNull(lines.get(1), "line2");
        if (alignMode == 0) {
            drawCenteredStringScaled(graphics, safeFont, line1, labelX, line1Y, nameColor, fontScale);
            drawCenteredStringScaled(graphics, safeFont, line2, labelX, line2Y, nameColor, fontScale);
            return;
        }

        int line1Width = scaledWidth(safeFont, line1, fontScale);
        int line2Width = scaledWidth(safeFont, line2, fontScale);
        if (alignMode > 0) {
            drawStringScaled(graphics, safeFont, line1, labelX, line1Y, nameColor, fontScale);
            drawStringScaled(graphics, safeFont, line2, labelX, line2Y, nameColor, fontScale);
        } else {
            drawStringScaled(graphics, safeFont, line1, labelX - line1Width, line1Y, nameColor, fontScale);
            drawStringScaled(graphics, safeFont, line2, labelX - line2Width, line2Y, nameColor, fontScale);
        }
    }

    private static java.util.List<String> splitNameLines(@Nonnull Font font, @Nonnull String name, int maxWidth) {
        if (font.width(name) <= maxWidth) {
            return java.util.List.of(name);
        }

        String[] words = SPACE_SPLITTER.splitToList(name).toArray(String[]::new);
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

    private static int computeCategoryLabelWidth(int itemRadius, double segmentAngle) {
        int arcWidth = (int) Math.floor(itemRadius * segmentAngle);
        int minWidth = RadialMenuScaler.scaleConstant(36);
        int maxWidth = RadialMenuScaler.scaleConstant(90);
        int preferred = (int) (arcWidth * 0.75f);
        return Math.max(minWidth, Math.min(preferred, maxWidth));
    }

    private static int computeItemNameWidth(int baseRadius, double itemAngleStep) {
        int arcWidth = (int) Math.floor(baseRadius * itemAngleStep);
        int preferred = (int) (arcWidth * 0.8f);
        int baseMax = RadialMenuScaler.scaleConstant(RadialMenuConstants.ITEM_NAME_MAX_WIDTH);
        int minWidth = RadialMenuScaler.scaleConstant(24);
        return Math.max(minWidth, Math.min(preferred, baseMax));
    }

    private static String truncateLabel(@Nonnull Font font, @Nonnull String label, int maxWidth) {
        if (font.width(label) <= maxWidth) {
            return label;
        }
        String trimmed = label;
        String ellipsis = "...";
        int minChars = Math.min(4, trimmed.length());
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

    private static float resolveFontScale() {
        float scale = RadialMenuScaler.getFontScale();
        return scale > 0f ? scale : 1f;
    }

    private static int toFontWidth(int width, float scale) {
        if (scale <= 0f) {
            return width;
        }
        return Math.max(0, Math.round(width / scale));
    }

    private static void drawCenteredStringScaled(GuiGraphics graphics, @Nonnull Font font, @Nonnull String text,
                                                  int x, int y, int color, float scale) {
        if (scale <= 0f || Math.abs(scale - 1f) < 0.001f) {
            UIScaleManager.drawScaledCenteredString(graphics, font, text, x, y, color);
            return;
        }
        float inv = 1f / scale;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawCenteredString(font, text, Math.round(x * inv), Math.round(y * inv), color);
        graphics.pose().popPose();
    }

    private static int scaledLineHeight(Font font, float scale) {
        return Math.max(1, Math.round(font.lineHeight * scale));
    }

    private static int scaledWidth(@Nonnull Font font, @Nonnull String text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    private static void drawStringScaled(GuiGraphics graphics, @Nonnull Font font, @Nonnull String text,
                                          int x, int y, int color, float scale) {
        if (scale <= 0f || Math.abs(scale - 1f) < 0.001f) {
            UIScaleManager.drawScaledString(graphics, font, text, x, y, color, false);
            return;
        }
        float inv = 1f / scale;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, Math.round(x * inv), Math.round(y * inv), color, false);
        graphics.pose().popPose();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * Renders an item's status indicator (ON/OFF or subcategory arrow).
     */
    private static void renderItemStatus(GuiGraphics graphics, @Nonnull Font font,
                                          RadialMenuItem item, int x, int y,
                                          boolean isActive, boolean canExecute, RadialMenuConfig.ColorTheme theme) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        int statusOffsetY = RadialMenuScaler.scaleConstant(RadialMenuConstants.ITEM_STATUS_OFFSET_Y);
        if (item.isToggle()) {
            @Nonnull String status = isActive ? "ON" : "OFF";
            int statusColor = isActive ? theme.active : RadialMenuConstants.ITEM_STATUS_INACTIVE_COLOR;
            if (!canExecute) {
                // BLOCKED state: use muted warning color
                statusColor = RadialMenuConstants.COLOR_BLOCKED_ICON;
            }
            float fontScale = resolveFontScale();
            drawCenteredStringScaled(graphics, safeFont, status, x,
                y + statusOffsetY, statusColor, fontScale);
        } else if (item.isSubcategoryLink()) {
            @Nonnull String indicator = ">";
            int indicatorColor = canExecute ? theme.textSecondary : RadialMenuConstants.COLOR_BLOCKED_ICON;
            float fontScale = resolveFontScale();
            drawCenteredStringScaled(graphics, safeFont, indicator, x,
                y + statusOffsetY, indicatorColor, fontScale);
        }
    }

    private static void renderRiskBadge(GuiGraphics graphics, @Nonnull Font font, int x, int y,
                                         RadialActionSafety.RiskLevel riskLevel) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        int badgeX = x + RadialMenuScaler.scaleConstant(RadialMenuConstants.RISK_BADGE_OFFSET_X);
        int badgeY = y + RadialMenuScaler.scaleConstant(RadialMenuConstants.RISK_BADGE_OFFSET_Y);
        int badgeColor = riskLevel == RadialActionSafety.RiskLevel.DANGER
            ? RadialMenuConstants.RISK_BADGE_DANGER_COLOR
            : RadialMenuConstants.RISK_BADGE_CAUTION_COLOR;
        String badgeText = riskLevel == RadialActionSafety.RiskLevel.DANGER ? "!" : "?";

        RadialGeometry.renderCircle(graphics, badgeX, badgeY,
            RadialMenuScaler.scaleConstant(RadialMenuConstants.RISK_BADGE_RADIUS),
            RadialMenuConstants.RISK_BADGE_BG_COLOR);
        float fontScale = resolveFontScale();
        drawCenteredStringScaled(graphics, safeFont, badgeText, badgeX,
            badgeY + RadialMenuScaler.scaleConstant(RadialMenuConstants.RISK_BADGE_TEXT_OFFSET_Y), badgeColor, fontScale);
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

        int glowRadius = (int) (RadialMenuScaler.getCategoryGlowRadius() * anim);
        int glowAlpha = (int) (RadialMenuConstants.CATEGORY_GLOW_ALPHA * anim);
        int glowColorVal = (cat.getColor() & DesignTokens.Mask.RGB) | (glowAlpha << 24);
        RadialGeometry.renderRadialGradient(graphics, glowX, glowY, glowRadius, glowColorVal, DesignTokens.Mask.NONE);
    }
}
