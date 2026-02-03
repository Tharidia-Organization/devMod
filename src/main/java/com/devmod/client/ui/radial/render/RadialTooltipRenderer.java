package com.devmod.client.ui.radial.render;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.radial.RadialActionSafety;
import com.devmod.client.ui.radial.RadialCategory;
import com.devmod.client.ui.radial.RadialMenuConfig;
import com.devmod.client.ui.radial.RadialMenuItem;
import com.devmod.client.ui.radial.RadialBlockedHelpResolver;
import com.devmod.client.ui.radial.config.RadialMenuConstants;
import com.devmod.client.ui.radial.config.RadialMenuScaler;
import com.devmod.client.ui.radial.model.MacroCategory;
import com.devmod.util.I18n;

public final class RadialTooltipRenderer {
    private static final Splitter WORD_SPLITTER = Splitter.on(' ').omitEmptyStrings();

    private RadialTooltipRenderer() {
        // Utility class - no instantiation
    }

    // ================================================================
    // TOOLTIP RENDERING
    // ================================================================

    /**
     * Context for tooltip rendering.
     *
     * @param centerX        center X coordinate
     * @param centerY        center Y coordinate
     * @param outerRadius    outer radius of the menu
     * @param theme          color theme to use
     */
    public record TooltipContext(
        int screenWidth,
        int screenHeight,
        int centerX,
        int centerY,
        int outerRadius,
        RadialMenuConfig.ColorTheme theme
    ) {
        public TooltipContext {
            Objects.requireNonNull(theme, "theme cannot be null");
        }
    }

    /**
     * Renders a tooltip at the standard position below the menu.
     *
     * @param graphics GUI graphics context
     * @param font     font for rendering text
     * @param tooltip  tooltip text (may include formatting codes)
     * @param context  tooltip context
     */
    public static void renderTooltip(GuiGraphics graphics, Font font,
                                      String tooltip, TooltipContext context) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        if (tooltip == null || tooltip.isEmpty()) return;

        float fontScale = resolveFontScale();
        int padding = RadialMenuScaler.scaleConstant(RadialMenuConstants.TOOLTIP_PADDING);
        int border = RadialMenuScaler.scaleConstant(RadialMenuConstants.TOOLTIP_BORDER_THICKNESS);
        int maxWidth = Math.max(80, context.screenWidth - (padding + border) * 2 - 8);
        int maxWidthFont = toFontWidth(maxWidth, fontScale);
        List<String> lines = wrapTooltipLines(font, tooltip, maxWidthFont);
        if (lines.isEmpty()) return;
        int tooltipWidthFont = 0;
        for (String line : lines) {
            tooltipWidthFont = Math.max(tooltipWidthFont, font.width(Objects.requireNonNull(line)));
        }
        int tooltipWidth = Math.round(tooltipWidthFont * fontScale);

        int tooltipX = context.centerX - tooltipWidth / 2;
        int tooltipY = context.centerY + context.outerRadius + RadialMenuScaler.getTooltipOffsetY();

        int lineHeight = scaledLineHeight(font, fontScale);
        int textHeight = lineHeight * lines.size();
        int maxX = context.screenWidth - tooltipWidth - padding - border;
        tooltipX = Math.max(padding + border, Math.min(tooltipX, maxX));

        int maxY = context.screenHeight - textHeight - padding - border;
        if (tooltipY > maxY) {
            tooltipY = context.centerY - context.outerRadius - RadialMenuScaler.getTooltipOffsetY() - textHeight;
        }
        tooltipY = Math.max(padding + border, Math.min(tooltipY, maxY));

        // Border
        graphics.fill(tooltipX - padding - border, tooltipY - padding - border,
            tooltipX + tooltipWidth + padding + border, tooltipY + textHeight + padding + border,
            context.theme.border);

        // Background
        graphics.fill(tooltipX - padding, tooltipY - padding,
            tooltipX + tooltipWidth + padding, tooltipY + textHeight + padding,
            RadialMenuConstants.TOOLTIP_BG_COLOR);

        // Text with shadow for better contrast
        int lineY = tooltipY;
        for (String line : lines) {
            drawStringScaled(graphics, font, nonNull(line), tooltipX, lineY, context.theme.textPrimary, true, fontScale);
            lineY += lineHeight;
        }
    }

    private static List<String> wrapTooltipLines(Font font, String tooltip, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        String[] rawLines = tooltip.split("\n", -1);
        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                lines.add("");
                continue;
            }
            lines.addAll(wrapLine(font, rawLine, maxWidth));
        }
        return lines;
    }

    private static List<String> wrapLine(Font font, String line, int maxWidth) {
        if (font.width(Objects.requireNonNull(line)) <= maxWidth) {
            return java.util.List.of(line);
        }

        Iterable<String> words = WORD_SPLITTER.split(line);
        List<String> wrapped = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        String carryFormat = "";

        for (String word : words) {
            String prefix = carryFormat;
            if (word.startsWith("\u00A7")) {
                prefix = "";
            }
            String candidate = current.length() == 0
                ? prefix + word
                : current + " " + word;

            if (font.width(candidate) <= maxWidth || current.length() == 0) {
                if (current.length() == 0) {
                    current.append(prefix).append(word);
                } else {
                    current.append(' ').append(word);
                }
            } else {
                wrapped.add(current.toString());
                carryFormat = lastFormatCode(current.toString());
                current.setLength(0);
                current.append(carryFormat).append(word);
            }
        }

        if (current.length() > 0) {
            wrapped.add(current.toString());
        }
        return wrapped;
    }

    private static String lastFormatCode(String line) {
        String last = "";
        for (int i = 0; i + 1 < line.length(); i++) {
            if (line.charAt(i) == '\u00A7') {
                last = line.substring(i, i + 2);
                i++;
            }
        }
        return last;
    }

    /**
     * Generates tooltip text for the current selection.
     *
     * @param hoveredMacro         macro being hovered (or null)
     * @param selectedMacro        currently selected macro
     * @param selectedFavoriteIndex selected favorite index (-1 if none)
     * @param favorites            list of favorite items
     * @param selectedCategoryIndex selected category index (-1 if none)
     * @param selectedItemIndex    selected item index (-1 if none)
     * @param categories           list of categories
     * @param activeCategory       active category for item rendering (subcategory override)
     * @param selectedItem         selected item (already filtered) or null
     * @param centerHovered        whether center button is hovered
     * @param macroHubHovered      whether hovering over macro hub area
     * @param editMode             whether edit mode is active
     * @return tooltip text or null if no tooltip should be shown
     */
    @Nullable
    public static String generateTooltip(@Nullable MacroCategory hoveredMacro,
                                          MacroCategory selectedMacro,
                                          int selectedFavoriteIndex,
                                          List<FavoriteRef> favorites,
                                          int selectedCategoryIndex,
                                          int selectedItemIndex,
                                          List<RadialCategory> categories,
                                          @Nullable RadialCategory activeCategory,
                                          @Nullable RadialMenuItem selectedItem,
                                          boolean centerHovered,
                                          boolean macroHubHovered,
                                          boolean editMode) {
        // Center close button tooltip
        if (centerHovered) {
            if (activeCategory != null && activeCategory.hasParent()) {
                return I18n.translate("devmod.radial.tooltip.back").getString();
            }
            return I18n.translate("devmod.radial.tooltip.close").getString();
        }

        // Macro-category segment tooltip
        if (hoveredMacro != null && macroHubHovered) {
            return RadialHubRenderer.getMacroTooltip(hoveredMacro, selectedMacro);
        }

        // Favorite item tooltip
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            return "* " + favorites.get(selectedFavoriteIndex).itemName();
        }

        // Category item tooltip (use visible items to match selectedItemIndex)
        if (selectedItem != null) {
            String tooltip = Objects.requireNonNullElse(selectedItem.getDescription(), "");
            String meta = buildActionMeta(selectedItem);
            if (meta != null && !meta.isBlank()) {
                tooltip += "\n" + meta;
            }
            if (!selectedItem.canExecute()) {
                Component reasonComponent = resolveUnavailableReasonComponent(selectedItem);
                String reason = reasonComponent == null ? null : reasonComponent.getString();
                if (reason == null || reason.isBlank()) {
                    tooltip += "\n" + I18n.translate("devmod.radial.tooltip.unavailable").getString();
                } else {
                    tooltip += "\n\u00A7c" + reason;
                }
                RadialBlockedHelpResolver.BlockedHelp help =
                    RadialBlockedHelpResolver.resolve(reasonComponent, reason);
                if (help != null) {
                    tooltip += "\n\u00A78" + I18n.translate(help.key(), help.args()).getString();
                }
            }
            if (editMode) {
                tooltip += I18n.translate("devmod.radial.tooltip.favorite_hint").getString();
            }
            return tooltip;
        }

        return null;
    }

    /**
     * Reference to a favorite item for tooltip generation.
     *
     * @param itemName name of the favorited item
     */
    public record FavoriteRef(String itemName) {
        public FavoriteRef {
            Objects.requireNonNull(itemName, "itemName cannot be null");
        }
    }

    // ================================================================
    // HELP TEXT RENDERING
    // ================================================================

    /**
     * Renders help text lines at the bottom of the screen.
     *
     * @param graphics     GUI graphics context
     * @param font         font for rendering text
     * @param screenWidth  screen width
     * @param screenHeight screen height
     * @param lines        lines to render
     * @param openTime     time when menu was opened
     * @param theme        color theme
     */
    public static void renderHelpText(GuiGraphics graphics, Font font,
                                       int screenWidth, int screenHeight,
                                       List<String> lines, long openTime,
                                       RadialMenuConfig.ColorTheme theme,
                                       int minY) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(lines, "lines cannot be null");
        Objects.requireNonNull(theme, "theme cannot be null");

        if (lines.isEmpty()) return;

        float fontScale = resolveFontScale();
        int margin = RadialMenuScaler.scaleConstant(8);
        int maxWidth = Math.max(80, UIScaleManager.getSafeWidth() - margin * 2);
        int maxWidthFont = toFontWidth(maxWidth, fontScale);
        List<String> wrappedLines = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                wrappedLines.add("");
                continue;
            }
            wrappedLines.addAll(wrapLine(font, line, maxWidthFont));
        }
        if (wrappedLines.isEmpty()) return;

        float helpAlpha = Math.min(1f,
            (System.currentTimeMillis() - openTime) / (float) RadialMenuConstants.HELP_FADE_DURATION_MS);
        int textAlpha = (int) (RadialMenuConstants.HELP_TEXT_ALPHA * helpAlpha);
        int helpColor = (textAlpha << 24) | (theme.textSecondary & DesignTokens.Mask.RGB);

        int lineHeight = scaledLineHeight(font, fontScale) + RadialMenuScaler.scaleConstant(2);
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        int topBound = Math.max(margin, Math.max(safeTop, minY));
        int bottomLimit = Math.min(safeBottom, screenHeight - margin)
            - RadialMenuScaler.scaleConstant(RadialMenuConstants.HELP_TEXT_MARGIN_BOTTOM);
        int availableHeight = bottomLimit - topBound;
        if (availableHeight < 0) return;
        int maxLines = Math.max(1, availableHeight / Math.max(1, lineHeight) + 1);
        int lineCount = Math.min(wrappedLines.size(), maxLines);
        boolean truncated = lineCount < wrappedLines.size();
        List<String> renderLines = new java.util.ArrayList<>(wrappedLines.subList(0, lineCount));
        if (truncated && !renderLines.isEmpty()) {
            int lastIndex = renderLines.size() - 1;
            String last = renderLines.get(lastIndex);
            String withEllipsis = truncateToWidth(font, last + "…", maxWidthFont);
            renderLines.set(lastIndex, withEllipsis);
        }
        int startY = bottomLimit - (lineCount - 1) * lineHeight;
        startY = Math.max(topBound, startY);
        int x = screenWidth / 2;
        int y = startY;
        for (String line : renderLines) {
            drawCenteredStringScaled(graphics, font, Objects.requireNonNull(line), x, y, helpColor, fontScale);
            y += lineHeight;
        }
    }

    // ================================================================
    // BREADCRUMB RENDERING
    // ================================================================

    /**
     * Renders the navigation breadcrumb in the top-left corner.
     *
     * @param graphics         GUI graphics context
     * @param font             font for rendering text
     * @param navigationStack  stack of parent categories
     * @param currentCategory  current category (or null)
     */
    public static void renderBreadcrumb(GuiGraphics graphics, Font font, int screenWidth,
                                         List<RadialCategory> navigationStack,
                                         @Nullable RadialCategory currentCategory) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(navigationStack, "navigationStack cannot be null");

        if (navigationStack.isEmpty() && currentCategory == null) return;

        float fontScale = resolveFontScale();
        int x = RadialMenuScaler.scaleConstant(RadialMenuConstants.BREADCRUMB_X);
        int y = RadialMenuScaler.scaleConstant(RadialMenuConstants.BREADCRUMB_Y);
        int margin = RadialMenuScaler.scaleConstant(6);
        int maxWidth = Math.max(40, screenWidth - x - margin);
        if (maxWidth <= 0) return;

        int maxWidthFont = toFontWidth(maxWidth, fontScale);
        String breadcrumb = buildBreadcrumb(font, navigationStack, currentCategory, maxWidthFont);
        if (breadcrumb.isEmpty()) return;

        drawStringScaled(graphics, font, breadcrumb, x, y, RadialMenuConstants.BREADCRUMB_COLOR, true, fontScale);
    }

    // ================================================================
    // INDICATOR RENDERING
    // ================================================================

    /**
     * Renders the edit mode indicator at the top of the screen.
     *
     * @param graphics    GUI graphics context
     * @param font        font for rendering text
     * @param screenWidth screen width
     */
    public static void renderEditModeIndicator(GuiGraphics graphics, Font font, int screenWidth) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");

        String editText = Objects.requireNonNull(I18n.translate("devmod.radial.edit_mode").getString());

        float fontScale = resolveFontScale();
        int centerX = screenWidth / 2;
        int paddingX = RadialMenuScaler.scaleConstant(RadialMenuConstants.EDIT_MODE_PADDING_X);
        int bgTopY = RadialMenuScaler.scaleConstant(RadialMenuConstants.EDIT_MODE_BG_TOP_Y);
        int bgBottomY = RadialMenuScaler.scaleConstant(RadialMenuConstants.EDIT_MODE_BG_BOTTOM_Y);
        int textY = RadialMenuScaler.scaleConstant(RadialMenuConstants.EDIT_MODE_TEXT_Y);
        int margin = RadialMenuScaler.scaleConstant(8);
        int maxTextWidth = Math.max(40, screenWidth - margin * 2 - paddingX * 2);
        if (maxTextWidth <= 0) return;
        int maxTextWidthFont = toFontWidth(maxTextWidth, fontScale);
        String displayText = truncateToWidth(font, editText, maxTextWidthFont);
        int textWidth = scaledWidth(font, displayText, fontScale);

        graphics.fill(centerX - textWidth / 2 - paddingX,
            bgTopY,
            centerX + textWidth / 2 + paddingX,
            bgBottomY,
            RadialMenuConstants.EDIT_MODE_BG_COLOR);
        drawCenteredStringScaled(graphics, font, displayText, centerX,
            textY, RadialMenuConstants.EDIT_MODE_TEXT_COLOR, fontScale);
    }

    /**
     * Renders the theme indicator that fades out after opening.
     *
     * @param graphics    GUI graphics context
     * @param font        font for rendering text
     * @param screenWidth screen width
     * @param themeName   name of the current theme
     * @param openTime    time when menu was opened
     */
    public static void renderThemeIndicator(GuiGraphics graphics, Font font,
                                             int screenWidth, String themeName, long openTime) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(themeName, "themeName cannot be null");

        long elapsed = System.currentTimeMillis() - openTime;
        if (elapsed >= RadialMenuConstants.THEME_INDICATOR_DURATION_MS) return;

        long fadeStart = RadialMenuConstants.THEME_INDICATOR_FADE_START_MS;
        long fadeDuration = RadialMenuConstants.THEME_INDICATOR_DURATION_MS - fadeStart;
        float alpha = 1f - Math.max(0f, (elapsed - fadeStart) / (float) fadeDuration);
        if (alpha <= 0) return;

        float fontScale = resolveFontScale();
        int color = ((int) (alpha * 255) << 24) | RadialMenuConstants.THEME_INDICATOR_COLOR;
        int margin = RadialMenuScaler.scaleConstant(8);
        int maxTextWidth = Math.max(40, screenWidth - margin * 2);
        if (maxTextWidth <= 0) return;
        String themeText = Objects.requireNonNull(I18n.translate("devmod.radial.theme_indicator", themeName).getString());
        int maxTextWidthFont = toFontWidth(maxTextWidth, fontScale);
        String displayText = truncateToWidth(font, themeText, maxTextWidthFont);
        drawCenteredStringScaled(graphics, font, displayText, screenWidth / 2,
            RadialMenuScaler.scaleConstant(RadialMenuConstants.THEME_INDICATOR_Y), color, fontScale);
    }

    // ================================================================
    // SEARCH OVERLAY RENDERING
    // ================================================================

    /**
     * Configuration for search overlay rendering.
     *
     * @param screenWidth   screen width
     * @param screenHeight  screen height
     * @param centerX       center X coordinate
     * @param theme         color theme
     */
    public record SearchConfig(
        int screenWidth,
        int screenHeight,
        int centerX,
        RadialMenuConfig.ColorTheme theme
    ) {
        public SearchConfig {
            Objects.requireNonNull(theme, "theme cannot be null");
        }
    }

    /**
     * A search result for rendering in the overlay.
     *
     * @param iconStack    item icon
     * @param name         item name
     * @param categoryName category name
     * @param isToggle     whether item is a toggle
     * @param isActive     whether toggle is active
     * @param canExecute   whether item can be executed
     */
    public record SearchResultDisplay(
        @Nonnull net.minecraft.world.item.ItemStack iconStack,
        String name,
        String categoryName,
        String macroName,
        boolean isToggle,
        boolean isActive,
        boolean canExecute
    ) {
        public SearchResultDisplay {
            Objects.requireNonNull(iconStack, "iconStack cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(categoryName, "categoryName cannot be null");
            Objects.requireNonNull(macroName, "macroName cannot be null");
        }
    }

    /**
     * Renders the search overlay.
     *
     * @param graphics            GUI graphics context
     * @param font                font for rendering text
     * @param config              search configuration
     * @param searchQuery         current search query
     * @param searchResults       list of search results to display
     * @param selectedResultIndex currently selected result index
     * @param searchBoxAnimation  animation progress (0-1)
     */
    public static void renderSearchOverlay(GuiGraphics graphics, Font font,
                                            SearchConfig config,
                                            String searchQuery,
                                            List<SearchResultDisplay> searchResults,
                                            int selectedResultIndex,
                                            float searchBoxAnimation) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(searchQuery, "searchQuery cannot be null");
        Objects.requireNonNull(searchResults, "searchResults cannot be null");

        if (searchBoxAnimation < RadialMenuConstants.SEARCH_ANIMATION_EPSILON) return;
        float fontScale = resolveFontScale();

        // Darken background
        int overlayAlpha = (int) (RadialMenuConstants.SEARCH_OVERLAY_ALPHA * searchBoxAnimation);
        graphics.fill(0, 0, config.screenWidth, config.screenHeight, (overlayAlpha << 24));

        // Search box - scale all dimensions and clamp to screen
        int margin = RadialMenuScaler.scaleConstant(8);
        int scaledBoxWidth = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_BOX_WIDTH);
        int maxBoxWidth = Math.max(60, config.screenWidth - margin * 2);
        int baseBoxWidth = Math.min(scaledBoxWidth, maxBoxWidth);
        int boxWidth = Math.max(1, (int) (baseBoxWidth * searchBoxAnimation));
        int boxHeight = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_BOX_HEIGHT);
        int boxX = config.centerX - boxWidth / 2;
        int boxY = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_BOX_Y);
        int border = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_BOX_BORDER);
        int minX = margin;
        int maxX = config.screenWidth - margin - boxWidth;
        boxX = clamp(boxX, minX, Math.max(minX, maxX));
        int minY = margin;
        int maxY = config.screenHeight - margin - boxHeight;
        boxY = clamp(boxY, minY, Math.max(minY, maxY));

        // Box border and background
        graphics.fill(boxX - border, boxY - border, boxX + boxWidth + border, boxY + boxHeight + border,
            config.theme.border);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, RadialMenuConstants.SEARCH_BOX_BG);

        // Search label and text
        int textOffsetXBase = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_BOX_TEXT_OFFSET_X);
        int textOffsetX = Math.min(textOffsetXBase, Math.max(2, boxWidth / 4));
        int textOffsetY = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_BOX_TEXT_OFFSET_Y);
        int textMaxWidth = Math.max(0, boxWidth - textOffsetX * 2);
        int textMaxWidthFont = toFontWidth(textMaxWidth, fontScale);
        String searchPrefix = nonNull(I18n.translate("devmod.radial.search.prefix").getString());
        String displayPrefix = truncateToWidth(font, searchPrefix, textMaxWidthFont);
        int prefixWidth = font.width(displayPrefix);
        int maxQueryWidth = Math.max(0, textMaxWidthFont - prefixWidth);
        String placeholder = nonNull(I18n.translate("devmod.radial.search.placeholder").getString());
        String displayText = searchQuery.isEmpty()
            ? truncateToWidth(font, placeholder, maxQueryWidth)
            : truncateToWidth(font, nonNull(searchQuery), maxQueryWidth);
        String displayQuery = searchQuery.isEmpty() ? "" : displayText;
        drawStringScaled(graphics, font, displayPrefix + displayText,
            boxX + textOffsetX,
            boxY + textOffsetY,
            config.theme.textPrimary,
            false,
            fontScale);

        // Blinking cursor
        if (!searchQuery.isEmpty() &&
            (System.currentTimeMillis() / RadialMenuConstants.SEARCH_CURSOR_BLINK_MS) % 2 == 0) {
            int cursorX = boxX + textOffsetX + scaledWidth(font, displayPrefix + displayQuery, fontScale);
            int cursorYStart = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_CURSOR_Y_START);
            int cursorYEnd = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_CURSOR_Y_END);
            int cursorWidth = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_CURSOR_WIDTH);
            int maxCursorX = boxX + boxWidth - border - cursorWidth;
            cursorX = Math.min(cursorX, maxCursorX);
            graphics.fill(cursorX, boxY + cursorYStart,
                cursorX + cursorWidth,
                boxY + cursorYEnd, config.theme.textPrimary);
        }

        // Results - scale all dimensions
        int resultsTopGap = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_RESULTS_TOP_GAP);
        int resultY = boxY + boxHeight + resultsTopGap;
        int resultHeight = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_RESULT_HEIGHT);
        int resultTextOffsetX = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_RESULT_TEXT_OFFSET_X);
        int resultTextOffsetY = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_RESULT_TEXT_OFFSET_Y);
        int resultStatusOffsetX = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_RESULT_STATUS_OFFSET_X);
        int resultGap = RadialMenuScaler.scaleConstant(RadialMenuConstants.SEARCH_RESULT_GAP);
        int iconOffset = RadialMenuScaler.scaleConstant(4);
        int iconTextGap = RadialMenuScaler.scaleConstant(18);

        // Show "No results" message when query is not empty but no results found
        if (!searchQuery.isEmpty() && searchResults.isEmpty()) {
            graphics.fill(boxX, resultY, boxX + boxWidth, resultY + resultHeight, RadialMenuConstants.SEARCH_RESULT_BG);
            String noResultsMsg = nonNull(I18n.translate("devmod.radial.search.no_results").getString());
            int noResultsTextWidth = Math.max(0, boxWidth - resultTextOffsetX * 2);
            int noResultsMaxFont = toFontWidth(noResultsTextWidth, fontScale);
            String displayNoResults = truncateToWidth(font, noResultsMsg, noResultsMaxFont);
            int noResultsColor = config.theme.textSecondary;
            drawStringScaled(graphics, font, displayNoResults, boxX + resultTextOffsetX, resultY + resultTextOffsetY,
                noResultsColor, false, fontScale);
            // Show suggestion hint
            int hintY = resultY + resultGap;
            String hintMsg = nonNull(I18n.translate("devmod.radial.search.hint").getString());
            String displayHint = truncateToWidth(font, hintMsg, noResultsMaxFont);
            int hintColor = (config.theme.textSecondary & DesignTokens.Mask.RGB) | (0x80 << 24);
            drawStringScaled(graphics, font, displayHint, boxX + resultTextOffsetX, hintY + resultTextOffsetY,
                hintColor, false, fontScale);
            return;
        }

        int availableHeight = config.screenHeight - margin - resultY;
        int maxResults = Math.max(0, (availableHeight + (resultGap - resultHeight)) / Math.max(1, resultGap));
        int resultsToRender = Math.min(searchResults.size(), maxResults);

        for (int i = 0; i < resultsToRender; i++) {
            SearchResultDisplay result = searchResults.get(i);
            boolean selected = (i == selectedResultIndex);

            int resultBg = selected ? config.theme.selected : RadialMenuConstants.SEARCH_RESULT_BG;
            graphics.fill(boxX, resultY, boxX + boxWidth, resultY + resultHeight, resultBg);

            int iconX = boxX + resultTextOffsetX;
            int iconY = resultY + iconOffset;
            if (!result.iconStack().isEmpty()) {
                graphics.renderItem(result.iconStack(), iconX, iconY);
            }

            int textColor = result.canExecute() ? config.theme.textPrimary : RadialMenuConstants.COLOR_INACTIVE;
            String context = result.categoryName();
            if (!result.macroName().isBlank()) {
                context = result.macroName() + " / " + result.categoryName();
            }
            String catName = "\u00A77[" + context + "]";
            int textX = boxX + resultTextOffsetX + iconTextGap;
            int rightPadding = result.isToggle && result.isActive ? resultStatusOffsetX : resultTextOffsetX;
            int maxTextWidth = Math.max(0, boxWidth - (resultTextOffsetX + iconTextGap + rightPadding));
            int maxTextWidthFont = toFontWidth(maxTextWidth, fontScale);
            String line = result.name + " " + catName;
            String displayLine = truncateToWidth(font, line, maxTextWidthFont);
            drawStringScaled(graphics, font, displayLine, textX, resultY + resultTextOffsetY, textColor, false, fontScale);

            if (result.isToggle && result.isActive) {
                String statusText = nonNull(I18n.translate("devmod.radial.status.on").getString());
                int statusWidth = scaledWidth(font, statusText, fontScale);
                int statusX = boxX + boxWidth - resultStatusOffsetX;
                int minGap = RadialMenuScaler.scaleConstant(6);
                if (statusX - textX - minGap >= statusWidth) {
                    int statusColor = result.canExecute() ? config.theme.active : RadialMenuConstants.COLOR_INACTIVE;
                    drawStringScaled(graphics, font, statusText, statusX, resultY + resultTextOffsetY, statusColor, false, fontScale);
                }
            }

            resultY += resultGap;
        }
    }

    @Nullable
    private static Component resolveUnavailableReasonComponent(RadialMenuItem item) {
        String actionId = item.getAction().getRegistryId();
        if (actionId == null) {
            return null;
        }
        com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
        if (action == null) {
            return null;
        }
        return action.getPrecondition().failureMessage(ClientActionContexts.forRadial());
    }

    @Nullable
    private static String buildActionMeta(RadialMenuItem item) {
        String actionId = item.getAction().getRegistryId();
        if (actionId == null) {
            return null;
        }
        com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
        if (action == null) {
            return null;
        }
        List<String> lines = new java.util.ArrayList<>();
        com.devmod.actions.ActionType actionType = action.getActionType();
        if (actionType != null) {
            lines.add(I18n.translate("devmod.radial.meta.type", formatActionType(actionType)).getString());
        }
        RadialActionSafety.RiskLevel riskLevel = RadialActionSafety.evaluate(item);
        if (action.requiresConfirm() || riskLevel == RadialActionSafety.RiskLevel.DANGER) {
            lines.add(I18n.translate("devmod.radial.meta.confirm_required").getString());
        }
        int permissionLevel = action.getPermissionLevel();
        if (permissionLevel >= 0) {
            lines.add(I18n.translate("devmod.radial.meta.permission", permissionLevel).getString());
        }
        if (riskLevel != RadialActionSafety.RiskLevel.SAFE) {
            lines.add(I18n.translate("devmod.radial.meta.risk", formatRisk(riskLevel)).getString());
        }
        if (lines.isEmpty()) {
            return null;
        }
        return String.join("\n", lines);
    }

    private static String formatActionType(com.devmod.actions.ActionType actionType) {
        return I18n.translate("devmod.radial.action_type." + actionType.name().toLowerCase(java.util.Locale.ROOT))
            .getString();
    }

    private static String formatRisk(RadialActionSafety.RiskLevel riskLevel) {
        return I18n.translate("devmod.radial.risk." + riskLevel.getId()).getString();
    }

    private static String buildBreadcrumb(Font font, List<RadialCategory> navigationStack,
                                           @Nullable RadialCategory currentCategory, int maxWidth) {
        List<String> segments = new java.util.ArrayList<>();
        for (RadialCategory cat : navigationStack) {
            String name = Objects.requireNonNullElse(cat.getName(), "");
            segments.add("\u00A77" + name);
        }
        if (currentCategory != null) {
            String name = Objects.requireNonNullElse(currentCategory.getName(), "");
            segments.add("\u00A7f" + name);
        }
        if (segments.isEmpty()) {
            return "";
        }

        String full = joinWithSeparator(segments);
        if (font.width(full) <= maxWidth) {
            return full;
        }

        int sepWidth = font.width(" > ");
        List<String> tail = new java.util.ArrayList<>();
        int usedWidth = 0;
        for (int i = segments.size() - 1; i >= 0; i--) {
            String seg = nonNull(segments.get(i));
            int segWidth = font.width(seg);
            int addWidth = segWidth + (tail.isEmpty() ? 0 : sepWidth);
            if (usedWidth + addWidth <= maxWidth) {
                tail.add(0, seg);
                usedWidth += addWidth;
            } else {
                break;
            }
        }

        if (tail.isEmpty()) {
            return truncateFormattedSegment(font, nonNull(segments.get(segments.size() - 1)), maxWidth);
        }
        if (tail.size() == segments.size()) {
            return joinWithSeparator(tail);
        }

        String ellipsis = "\u00A77...";
        int ellipsisWidth = font.width(ellipsis);
        int available = maxWidth - ellipsisWidth - sepWidth;
        if (available <= 0) {
            return truncateFormattedSegment(font, nonNull(segments.get(segments.size() - 1)), maxWidth);
        }

        while (!tail.isEmpty() && font.width(joinWithSeparator(tail)) > available) {
            tail.remove(0);
        }
        if (tail.isEmpty()) {
            return truncateFormattedSegment(font, nonNull(segments.get(segments.size() - 1)), maxWidth);
        }

        String tailString = joinWithSeparator(tail);
        if (font.width(tailString) > available) {
            tailString = truncateFormattedSegment(font, nonNull(tail.get(tail.size() - 1)), available);
        }

        return ellipsis + " > " + tailString;
    }

    @Nonnull
    private static String joinWithSeparator(List<String> segments) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                builder.append(" > ");
            }
            builder.append(segments.get(i));
        }
        String result = builder.toString();
        return result != null ? result : "";
    }

    @Nonnull
    private static String truncateFormattedSegment(Font font, @Nonnull String segment, int maxWidth) {
        if (font.width(segment) <= maxWidth) {
            return segment;
        }
        int idx = 0;
        StringBuilder prefix = new StringBuilder();
        while (idx + 1 < segment.length() && segment.charAt(idx) == '\u00A7') {
            prefix.append(segment, idx, idx + 2);
            idx += 2;
        }
        String plain = nonNull(segment.substring(idx));
        String prefixStr = nonNull(prefix.toString());
        String truncated = truncateToWidth(font, plain, Math.max(0, maxWidth - font.width(prefixStr)));
        return prefixStr + truncated;
    }

    @Nonnull
    private static String truncateToWidth(Font font, @Nonnull String text, int maxWidth) {
        if (maxWidth <= 0 || text.isEmpty()) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth > maxWidth) {
            return "";
        }

        int targetWidth = Math.max(0, maxWidth - ellipsisWidth);
        StringBuilder builder = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                builder.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            int charWidth = font.width(nonNull(String.valueOf(c)));
            if (width + charWidth > targetWidth) {
                break;
            }
            builder.append(c);
            width += charWidth;
        }
        return builder + ellipsis;
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

    private static int scaledWidth(@Nonnull Font font, @Nonnull String text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    private static int scaledLineHeight(Font font, float scale) {
        return Math.max(1, Math.round(font.lineHeight * scale));
    }

    private static void drawStringScaled(GuiGraphics graphics, @Nonnull Font font, @Nonnull String text,
                                          int x, int y, int color, boolean shadow, float scale) {
        if (scale <= 0f || Math.abs(scale - 1f) < 0.001f) {
            UIScaleManager.drawScaledString(graphics, font, text, x, y, color, shadow);
            return;
        }
        float inv = 1f / scale;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, Math.round(x * inv), Math.round(y * inv), color, shadow);
        graphics.pose().popPose();
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

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Ensures a String is non-null, returning empty string if null. */
    @Nonnull
    private static String nonNull(String value) {
        return value != null ? value : "";
    }
}
