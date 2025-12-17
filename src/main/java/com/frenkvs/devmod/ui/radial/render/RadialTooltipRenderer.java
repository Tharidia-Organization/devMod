package com.frenkvs.devmod.ui.radial.render;

import com.frenkvs.devmod.ui.radial.RadialCategory;
import com.frenkvs.devmod.ui.radial.RadialMenuConfig;
import com.frenkvs.devmod.ui.radial.RadialMenuItem;
import com.frenkvs.devmod.ui.radial.model.MacroCategory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Objects;

/**
 * Renders tooltips, help text, and UI indicators for the Radial Menu.
 *
 * <p>This renderer handles:</p>
 * <ul>
 *   <li>Item and category tooltips</li>
 *   <li>Macro-category tooltips</li>
 *   <li>Help text at screen bottom</li>
 *   <li>Breadcrumb navigation display</li>
 *   <li>Edit mode indicator</li>
 *   <li>Theme indicator</li>
 * </ul>
 */
public final class RadialTooltipRenderer {

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

        int tooltipWidth = font.width(tooltip);
        int tooltipX = context.centerX - tooltipWidth / 2;
        int tooltipY = context.centerY + context.outerRadius + 70;

        int padding = 6;

        // Border
        graphics.fill(tooltipX - padding - 1, tooltipY - padding - 1,
            tooltipX + tooltipWidth + padding + 1, tooltipY + 10 + padding + 1,
            context.theme.border);

        // Background
        graphics.fill(tooltipX - padding, tooltipY - padding,
            tooltipX + tooltipWidth + padding, tooltipY + 10 + padding,
            0xF0101020);

        // Text
        graphics.drawString(font, tooltip, tooltipX, tooltipY, context.theme.textPrimary, false);
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
     * @param centerHovered        whether center button is hovered
     * @param macroHubHovered      whether hovering over macro hub area
     * @param editMode             whether edit mode is active
     * @return tooltip text or null if no tooltip should be shown
     */
    public static String generateTooltip(MacroCategory hoveredMacro,
                                          MacroCategory selectedMacro,
                                          int selectedFavoriteIndex,
                                          List<FavoriteRef> favorites,
                                          int selectedCategoryIndex,
                                          int selectedItemIndex,
                                          List<RadialCategory> categories,
                                          boolean centerHovered,
                                          boolean macroHubHovered,
                                          boolean editMode) {
        // Center close button tooltip
        if (centerHovered) {
            return "§7Click to close §8| §7Scroll to search";
        }

        // Macro-category segment tooltip
        if (hoveredMacro != null && macroHubHovered) {
            return RadialHubRenderer.getMacroTooltip(hoveredMacro, selectedMacro);
        }

        // Favorite item tooltip
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            return "★ " + favorites.get(selectedFavoriteIndex).itemName();
        }

        // Category item tooltip (use visible items to match selectedItemIndex)
        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < categories.size()) {
            RadialCategory cat = categories.get(selectedCategoryIndex);
            java.util.List<RadialMenuItem> visibleItems = cat.getVisibleItems();
            if (selectedItemIndex >= 0 && selectedItemIndex < visibleItems.size()) {
                RadialMenuItem item = visibleItems.get(selectedItemIndex);
                String tooltip = item.getDescription();
                if (editMode) {
                    tooltip += " §8| §cShift+Click to favorite";
                }
                return tooltip;
            }
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
     * Renders the help text at the bottom of the screen.
     *
     * @param graphics      GUI graphics context
     * @param font          font for rendering text
     * @param screenWidth   screen width
     * @param screenHeight  screen height
     * @param selectedMacro currently selected macro
     * @param searchMode    whether search mode is active
     * @param openTime      time when menu was opened
     * @param theme         color theme
     */
    public static void renderHelpText(GuiGraphics graphics, Font font,
                                       int screenWidth, int screenHeight,
                                       MacroCategory selectedMacro,
                                       boolean searchMode, long openTime,
                                       RadialMenuConfig.ColorTheme theme) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(selectedMacro, "selectedMacro cannot be null");
        Objects.requireNonNull(theme, "theme cannot be null");

        float helpAlpha = Math.min(1f, (System.currentTimeMillis() - openTime) / 200f);
        int textAlpha = (int) (0xAA * helpAlpha);
        int helpColor = (textAlpha << 24) | (theme.textSecondary & 0x00FFFFFF);

        String helpLine;
        if (searchMode) {
            helpLine = "§eSearch §7- Type to filter, Enter to select, Esc to cancel";
        } else {
            helpLine = "§f[" + selectedMacro.getIcon() + " " + selectedMacro.getName() +
                "§f] §7Click center to switch §8| §7[/] Search §8| §7[1-4] Macro";
        }

        graphics.drawCenteredString(font, helpLine, screenWidth / 2, screenHeight - 25, helpColor);
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
                                         RadialCategory currentCategory) {
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

        graphics.drawString(font, breadcrumb.toString(), 10, 10, 0xFFFFFFFF, true);
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

        String editText = "§c§l[EDIT MODE] §7Shift+Click to ★ favorite";
        int textWidth = font.width(editText);

        graphics.fill(screenWidth / 2 - textWidth / 2 - 5, 5,
            screenWidth / 2 + textWidth / 2 + 5, 20, 0xCC000000);
        graphics.drawCenteredString(font, editText, screenWidth / 2, 8, 0xFFFF4444);
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
        if (elapsed >= 2000) return;

        float alpha = 1f - Math.max(0, (elapsed - 1000) / 1000f);
        if (alpha <= 0) return;

        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        String themeText = "Theme: " + themeName;
        graphics.drawCenteredString(font, themeText, screenWidth / 2, 30, color);
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
     * @param icon         item icon emoji
     * @param name         item name
     * @param categoryName category name
     * @param isToggle     whether item is a toggle
     * @param isActive     whether toggle is active
     */
    public record SearchResultDisplay(
        String icon,
        String name,
        String categoryName,
        boolean isToggle,
        boolean isActive
    ) {
        public SearchResultDisplay {
            Objects.requireNonNull(icon, "icon cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(categoryName, "categoryName cannot be null");
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

        if (searchBoxAnimation < 0.01f) return;

        // Darken background
        int overlayAlpha = (int) (0x80 * searchBoxAnimation);
        graphics.fill(0, 0, config.screenWidth, config.screenHeight, (overlayAlpha << 24));

        // Search box
        int boxWidth = (int) (300 * searchBoxAnimation);
        int boxHeight = 30;
        int boxX = config.centerX - boxWidth / 2;
        int boxY = 50;

        // Box border and background
        graphics.fill(boxX - 2, boxY - 2, boxX + boxWidth + 2, boxY + boxHeight + 2, config.theme.border);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xEE101020);

        // Search icon and text
        String displayText = !searchQuery.isEmpty() ? searchQuery : "§7Type to search...";
        graphics.drawString(font, "🔍 " + displayText, boxX + 10, boxY + 10, config.theme.textPrimary);

        // Blinking cursor
        if (!searchQuery.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = boxX + 10 + font.width("🔍 " + searchQuery);
            graphics.fill(cursorX, boxY + 8, cursorX + 2, boxY + 22, config.theme.textPrimary);
        }

        // Results
        int resultY = boxY + boxHeight + 10;
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResultDisplay result = searchResults.get(i);
            boolean selected = (i == selectedResultIndex);

            int resultBg = selected ? config.theme.selected : 0xCC101020;
            graphics.fill(boxX, resultY, boxX + boxWidth, resultY + 25, resultBg);

            String catName = "§7[" + result.categoryName + "]";
            graphics.drawString(font, result.icon + " " + result.name + " " + catName,
                boxX + 10, resultY + 8, config.theme.textPrimary);

            if (result.isToggle && result.isActive) {
                graphics.drawString(font, "§a● ON", boxX + boxWidth - 40, resultY + 8, config.theme.active);
            }

            resultY += 28;
        }
    }
}
