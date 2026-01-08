package com.devmod.client.ui.radial.render;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.radial.RadialActionSafety;
import com.devmod.client.ui.radial.RadialCategory;
import com.devmod.client.ui.radial.RadialMenuConfig;
import com.devmod.client.ui.radial.RadialMenuItem;
import com.devmod.client.ui.radial.config.RadialMenuConstants;
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

        int padding = RadialMenuConstants.TOOLTIP_PADDING;
        int border = RadialMenuConstants.TOOLTIP_BORDER_THICKNESS;
        int maxWidth = Math.max(80, context.screenWidth - (padding + border) * 2 - 8);
        List<String> lines = wrapTooltipLines(font, tooltip, maxWidth);
        if (lines.isEmpty()) return;
        int tooltipWidth = 0;
        for (String line : lines) {
            tooltipWidth = Math.max(tooltipWidth, font.width(Objects.requireNonNull(line)));
        }

        int tooltipX = context.centerX - tooltipWidth / 2;
        int tooltipY = context.centerY + context.outerRadius + RadialMenuConstants.TOOLTIP_OFFSET_Y;

        int lineHeight = font.lineHeight;
        int textHeight = lineHeight * lines.size();
        int maxX = context.screenWidth - tooltipWidth - padding - border;
        tooltipX = Math.max(padding + border, Math.min(tooltipX, maxX));

        int maxY = context.screenHeight - textHeight - padding - border;
        if (tooltipY > maxY) {
            tooltipY = context.centerY - context.outerRadius - RadialMenuConstants.TOOLTIP_OFFSET_Y - textHeight;
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

        // Text
        int lineY = tooltipY;
        for (String line : lines) {
            graphics.drawString(font, line, tooltipX, lineY, context.theme.textPrimary, false);
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
            if (word.startsWith("§")) {
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
            if (line.charAt(i) == '§') {
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
                String reason = resolveUnavailableReason(selectedItem);
                if (reason == null || reason.isBlank()) {
                    tooltip += "\n" + I18n.translate("devmod.radial.tooltip.unavailable").getString();
                } else {
                    tooltip += "\n§c" + reason;
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
                                       RadialMenuConfig.ColorTheme theme) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(lines, "lines cannot be null");
        Objects.requireNonNull(theme, "theme cannot be null");

        if (lines.isEmpty()) return;

        float helpAlpha = Math.min(1f,
            (System.currentTimeMillis() - openTime) / (float) RadialMenuConstants.HELP_FADE_DURATION_MS);
        int textAlpha = (int) (RadialMenuConstants.HELP_TEXT_ALPHA * helpAlpha);
        int helpColor = (textAlpha << 24) | (theme.textSecondary & DesignTokens.Mask.RGB);

        int lineHeight = font.lineHeight + 2;
        int lineCount = lines.size();
        int startY = screenHeight - RadialMenuConstants.HELP_TEXT_MARGIN_BOTTOM
            - (lineCount - 1) * lineHeight;
        int x = screenWidth / 2;
        int y = startY;
        for (String line : lines) {
            graphics.drawCenteredString(font, Objects.requireNonNull(line), x, y, helpColor);
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
    public static void renderBreadcrumb(GuiGraphics graphics, Font font,
                                         List<RadialCategory> navigationStack,
                                         @Nullable RadialCategory currentCategory) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(navigationStack, "navigationStack cannot be null");

        if (navigationStack.isEmpty() && currentCategory == null) return;

        StringBuilder breadcrumb = new StringBuilder("§7");
        for (RadialCategory cat : navigationStack) {
            breadcrumb.append(cat.getName()).append(" > ");
        }
        if (currentCategory != null) {
            breadcrumb.append("§f").append(currentCategory.getName());
        }

        graphics.drawString(font, breadcrumb.toString(),
            RadialMenuConstants.BREADCRUMB_X, RadialMenuConstants.BREADCRUMB_Y,
            RadialMenuConstants.BREADCRUMB_COLOR, true);
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
        int textWidth = font.width(editText);

        int centerX = screenWidth / 2;
        graphics.fill(centerX - textWidth / 2 - RadialMenuConstants.EDIT_MODE_PADDING_X,
            RadialMenuConstants.EDIT_MODE_BG_TOP_Y,
            centerX + textWidth / 2 + RadialMenuConstants.EDIT_MODE_PADDING_X,
            RadialMenuConstants.EDIT_MODE_BG_BOTTOM_Y,
            RadialMenuConstants.EDIT_MODE_BG_COLOR);
        graphics.drawCenteredString(font, editText, centerX,
            RadialMenuConstants.EDIT_MODE_TEXT_Y, RadialMenuConstants.EDIT_MODE_TEXT_COLOR);
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

        int color = ((int) (alpha * 255) << 24) | RadialMenuConstants.THEME_INDICATOR_COLOR;
        String themeText = Objects.requireNonNull(I18n.translate("devmod.radial.theme_indicator", themeName).getString());
        graphics.drawCenteredString(font, themeText, screenWidth / 2,
            RadialMenuConstants.THEME_INDICATOR_Y, color);
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

        // Darken background
        int overlayAlpha = (int) (RadialMenuConstants.SEARCH_OVERLAY_ALPHA * searchBoxAnimation);
        graphics.fill(0, 0, config.screenWidth, config.screenHeight, (overlayAlpha << 24));

        // Search box
        int boxWidth = (int) (RadialMenuConstants.SEARCH_BOX_WIDTH * searchBoxAnimation);
        int boxHeight = RadialMenuConstants.SEARCH_BOX_HEIGHT;
        int boxX = config.centerX - boxWidth / 2;
        int boxY = RadialMenuConstants.SEARCH_BOX_Y;
        int border = RadialMenuConstants.SEARCH_BOX_BORDER;

        // Box border and background
        graphics.fill(boxX - border, boxY - border, boxX + boxWidth + border, boxY + boxHeight + border,
            config.theme.border);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, RadialMenuConstants.SEARCH_BOX_BG);

        // Search label and text
        String displayText = !searchQuery.isEmpty()
            ? searchQuery
            : I18n.translate("devmod.radial.search.placeholder").getString();
        String searchPrefix = I18n.translate("devmod.radial.search.prefix").getString();
        graphics.drawString(font, searchPrefix + displayText,
            boxX + RadialMenuConstants.SEARCH_BOX_TEXT_OFFSET_X,
            boxY + RadialMenuConstants.SEARCH_BOX_TEXT_OFFSET_Y,
            config.theme.textPrimary);

        // Blinking cursor
        if (!searchQuery.isEmpty() &&
            (System.currentTimeMillis() / RadialMenuConstants.SEARCH_CURSOR_BLINK_MS) % 2 == 0) {
            int cursorX = boxX + RadialMenuConstants.SEARCH_BOX_TEXT_OFFSET_X + font.width(searchPrefix + searchQuery);
            graphics.fill(cursorX, boxY + RadialMenuConstants.SEARCH_CURSOR_Y_START,
                cursorX + RadialMenuConstants.SEARCH_CURSOR_WIDTH,
                boxY + RadialMenuConstants.SEARCH_CURSOR_Y_END, config.theme.textPrimary);
        }

        // Results
        int resultY = boxY + boxHeight + RadialMenuConstants.SEARCH_RESULTS_TOP_GAP;
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResultDisplay result = searchResults.get(i);
            boolean selected = (i == selectedResultIndex);

            int resultBg = selected ? config.theme.selected : RadialMenuConstants.SEARCH_RESULT_BG;
            graphics.fill(boxX, resultY, boxX + boxWidth, resultY + RadialMenuConstants.SEARCH_RESULT_HEIGHT, resultBg);

            int iconX = boxX + RadialMenuConstants.SEARCH_RESULT_TEXT_OFFSET_X;
            int iconY = resultY + 4;
            if (!result.iconStack().isEmpty()) {
                graphics.renderItem(result.iconStack(), iconX, iconY);
            }

            int textColor = result.canExecute() ? config.theme.textPrimary : RadialMenuConstants.COLOR_INACTIVE;
            String context = result.categoryName();
            if (!result.macroName().isBlank()) {
                context = result.macroName() + " / " + result.categoryName();
            }
            String catName = "§7[" + context + "]";
            graphics.drawString(font, result.name + " " + catName,
                boxX + RadialMenuConstants.SEARCH_RESULT_TEXT_OFFSET_X + 18,
                resultY + RadialMenuConstants.SEARCH_RESULT_TEXT_OFFSET_Y,
                textColor);

            if (result.isToggle && result.isActive) {
                int statusColor = result.canExecute() ? config.theme.active : RadialMenuConstants.COLOR_INACTIVE;
                graphics.drawString(font, I18n.translate("devmod.radial.status.on").getString(),
                    boxX + boxWidth - RadialMenuConstants.SEARCH_RESULT_STATUS_OFFSET_X,
                    resultY + RadialMenuConstants.SEARCH_RESULT_TEXT_OFFSET_Y, statusColor);
            }

            resultY += RadialMenuConstants.SEARCH_RESULT_GAP;
        }
    }

    @Nullable
    private static String resolveUnavailableReason(RadialMenuItem item) {
        String actionId = item.getAction().getRegistryId();
        if (actionId == null) {
            return null;
        }
        com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
        if (action == null) {
            return null;
        }
        return action.getPrecondition().failureMessage(ClientActionContexts.forRadial()).getString();
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
}
