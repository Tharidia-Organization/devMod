package com.frenkvs.devmod.ui.radial.render;

import com.frenkvs.devmod.ui.radial.config.RadialMenuConstants;
import com.frenkvs.devmod.ui.radial.model.MacroCategory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Objects;

/**
 * Renders the center hub of the Radial Menu.
 *
 * <p>The hub consists of:</p>
 * <ul>
 *   <li>4 macro-category segments (ANALYZE, COMBAT, TOOLS, PLAY)</li>
 *   <li>Close/back button in the very center</li>
 *   <li>Divider lines between segments</li>
 *   <li>Outer ring around the entire hub</li>
 * </ul>
 *
 * <p>Each segment is clickable and switches the active macro-category.
 * The selected macro is highlighted with its color.</p>
 */
@SuppressWarnings("null") // Minecraft API null annotations
public final class RadialHubRenderer {

    private RadialHubRenderer() {
        // Utility class - no instantiation
    }

    // ================================================================
    // HUB STATE
    // ================================================================

    /**
     * Immutable state container for hub rendering.
     *
     * @param centerX           center X coordinate
     * @param centerY           center Y coordinate
     * @param centerButtonRadius radius of the close button area
     * @param macroHubRadius    outer radius of the macro hub
     * @param selectedMacro     currently selected macro-category
     * @param hoveredMacro      macro-category being hovered (or null)
     * @param segmentAnimations animation progress for each segment (0-1)
     * @param categoryHoverAnim animation progress for center hover (0-1)
     * @param searchMode        whether search mode is active
     * @param inSubcategory     whether currently in a subcategory
     */
    public record HubState(
        int centerX,
        int centerY,
        int centerButtonRadius,
        int macroHubRadius,
        MacroCategory selectedMacro,
        MacroCategory hoveredMacro,
        float[] segmentAnimations,
        float categoryHoverAnim,
        boolean searchMode,
        boolean inSubcategory
    ) {
        public HubState {
            Objects.requireNonNull(selectedMacro, "selectedMacro cannot be null");
            Objects.requireNonNull(segmentAnimations, "segmentAnimations cannot be null");
        }
    }

    /**
     * Result of hover detection on the hub.
     *
     * @param hoveredMacro the macro being hovered (null if none)
     * @param centerHovered whether the center close button is hovered
     */
    public record HoverResult(MacroCategory hoveredMacro, boolean centerHovered) {
        public static final HoverResult NONE = new HoverResult(null, false);
        public static final HoverResult CENTER = new HoverResult(null, true);
    }

    // ================================================================
    // HOVER DETECTION
    // ================================================================

    /**
     * Determines which part of the hub the mouse is hovering over.
     *
     * @param mouseX          mouse X coordinate
     * @param mouseY          mouse Y coordinate
     * @param centerX         hub center X
     * @param centerY         hub center Y
     * @param centerButtonRadius close button radius
     * @param macroHubRadius  outer hub radius
     * @return hover result indicating what's being hovered
     */
    public static HoverResult detectHover(int mouseX, int mouseY,
                                           int centerX, int centerY,
                                           int centerButtonRadius, int macroHubRadius) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        // Very center for close button
        if (distance < centerButtonRadius * RadialMenuConstants.CLOSE_BUTTON_RATIO) {
            return HoverResult.CENTER;
        }

        // Outside hub entirely
        if (distance >= macroHubRadius) {
            return HoverResult.NONE;
        }

        // Calculate which macro segment the mouse is over
        double angle = Math.atan2(dy, dx);
        if (angle < 0) angle += RadialMenuConstants.TWO_PI;

        // 4 segments, starting from top (-PI/2), going clockwise
        double startOffset = -Math.PI / 2 - Math.PI / 4; // Offset so ANALYZE is at top
        double adjustedAngle = angle - startOffset;
        if (adjustedAngle < 0) adjustedAngle += RadialMenuConstants.TWO_PI;

        int macroIndex = (int) (adjustedAngle / (Math.PI / 2)) % 4;
        return new HoverResult(MacroCategory.values()[macroIndex], false);
    }

    /**
     * Updates segment animations based on selection and hover state.
     *
     * @param animations      array of animation values to update (modified in place)
     * @param selectedMacro   currently selected macro
     * @param hoveredMacro    currently hovered macro (or null)
     * @param lerpFactor      interpolation factor for smooth animation
     */
    public static void updateSegmentAnimations(float[] animations,
                                                MacroCategory selectedMacro,
                                                MacroCategory hoveredMacro,
                                                float lerpFactor) {
        Objects.requireNonNull(animations, "animations cannot be null");
        Objects.requireNonNull(selectedMacro, "selectedMacro cannot be null");

        MacroCategory[] macros = MacroCategory.values();
        for (int i = 0; i < macros.length && i < animations.length; i++) {
            MacroCategory macro = macros[i];
            float targetAnim = (macro == selectedMacro || macro == hoveredMacro) ? 1f : 0f;
            animations[i] = Mth.lerp(lerpFactor, animations[i], targetAnim);
        }
    }

    // ================================================================
    // RENDERING
    // ================================================================

    /**
     * Renders the complete center hub.
     *
     * @param graphics GUI graphics context
     * @param font     font for rendering text
     * @param state    hub state containing all rendering parameters
     */
    public static void render(GuiGraphics graphics, Font font, HubState state) {
        Objects.requireNonNull(graphics, "graphics cannot be null");
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(state, "state cannot be null");

        renderMacroSegments(graphics, font, state);
        renderCenterButton(graphics, font, state);
        renderOuterRing(graphics, state);
    }

    /**
     * Renders the 4 macro-category segments.
     */
    private static void renderMacroSegments(GuiGraphics graphics, Font font, HubState state) {
        MacroCategory[] macros = MacroCategory.values();
        double segmentAngle = Math.PI / 2; // 90 degrees per segment
        double startOffset = -Math.PI / 2 - Math.PI / 4; // Start from top-left

        int innerR = (int) (state.centerButtonRadius * RadialMenuConstants.CLOSE_BUTTON_RATIO);
        int outerR = state.macroHubRadius;

        for (int i = 0; i < macros.length; i++) {
            MacroCategory macro = macros[i];
            boolean isSelected = macro == state.selectedMacro;
            boolean isHovered = macro == state.hoveredMacro;

            double segStart = startOffset + i * segmentAngle;
            double segEnd = segStart + segmentAngle;

            // Segment colors
            int baseColor = isSelected
                ? RadialGeometry.blendColors(0xFF252540, macro.getColor(), 0.4f)
                : 0xF0202035;
            if (isHovered && !isSelected) {
                baseColor = RadialGeometry.blendColors(baseColor, macro.getColor(), 0.25f);
            }

            // Render segment arc
            RadialGeometry.renderArcSegment(graphics, state.centerX, state.centerY,
                innerR, outerR, segStart, segEnd, baseColor);

            // Segment border
            int borderColor = isSelected
                ? macro.getColor()
                : (isHovered
                    ? RadialGeometry.blendColors(0xFF606080, macro.getColor(), 0.5f)
                    : 0xFF404060);
            int borderWidth = isSelected ? 3 : 2;
            RadialGeometry.renderArcOutline(graphics, state.centerX, state.centerY,
                outerR, segStart, segEnd, borderColor, borderWidth);

            // Divider line at segment start
            renderSegmentDivider(graphics, state.centerX, state.centerY,
                innerR, outerR, segStart);

            // Render macro icon
            renderMacroIcon(graphics, font, state.centerX, state.centerY,
                innerR, outerR, segStart, segmentAngle, macro, isSelected, isHovered);
        }
    }

    /**
     * Renders a divider line between segments.
     */
    private static void renderSegmentDivider(GuiGraphics graphics, int cx, int cy,
                                              int innerR, int outerR, double angle) {
        int x1 = (int) (cx + Math.cos(angle) * innerR);
        int y1 = (int) (cy + Math.sin(angle) * innerR);
        int x2 = (int) (cx + Math.cos(angle) * outerR);
        int y2 = (int) (cy + Math.sin(angle) * outerR);
        RadialGeometry.drawLine(graphics, x1, y1, x2, y2, 0xFF505070);
    }

    /**
     * Renders a macro category icon within its segment.
     */
    private static void renderMacroIcon(GuiGraphics graphics, Font font,
                                         int cx, int cy, int innerR, int outerR,
                                         double segStart, double segmentAngle,
                                         MacroCategory macro, boolean isSelected, boolean isHovered) {
        double midAngle = segStart + segmentAngle / 2;
        int iconRadius = (innerR + outerR) / 2;
        int iconX = (int) (cx + Math.cos(midAngle) * iconRadius);
        int iconY = (int) (cy + Math.sin(midAngle) * iconRadius);

        int iconColor = isSelected ? 0xFFFFFFFF : (isHovered ? macro.getColor() : 0xFFAAAAAA);
        graphics.drawCenteredString(font, macro.getIcon(), iconX, iconY - 4, iconColor);
    }

    /**
     * Renders the center close/back button.
     */
    private static void renderCenterButton(GuiGraphics graphics, Font font, HubState state) {
        boolean centerHovered = state.hoveredMacro == null &&
            state.categoryHoverAnim > 0.5f;

        int closeBtnRadius = (int) (state.centerButtonRadius * RadialMenuConstants.CLOSE_BUTTON_RATIO);

        // Background
        int closeBgColor = centerHovered ? 0xFF453545 : 0xF0252530;
        RadialGeometry.renderCircle(graphics, state.centerX, state.centerY,
            closeBtnRadius, closeBgColor);

        // Border
        int closeBorderColor = centerHovered ? 0xFFFF6666 : 0xFF505070;
        RadialGeometry.renderRing(graphics, state.centerX, state.centerY,
            closeBtnRadius - 2, closeBtnRadius, closeBorderColor);

        // Icon
        String centerIcon;
        int centerIconColor;

        if (centerHovered) {
            centerIcon = state.inSubcategory ? "←" : "✕";
            centerIconColor = state.inSubcategory ? 0xFF80AAFF : 0xFFFF6666;
        } else if (state.searchMode) {
            centerIcon = "🔍";
            centerIconColor = 0xFF8080FF;
        } else {
            // Show selected macro icon in center when not hovered
            centerIcon = state.selectedMacro.getIcon();
            centerIconColor = state.selectedMacro.getColor();
        }

        graphics.drawCenteredString(font, centerIcon, state.centerX, state.centerY - 3, centerIconColor);
    }

    /**
     * Renders the outer ring around the entire hub.
     */
    private static void renderOuterRing(GuiGraphics graphics, HubState state) {
        int ringColor = RadialGeometry.blendColors(0xFF505070, state.selectedMacro.getColor(), 0.3f);
        RadialGeometry.renderRing(graphics, state.centerX, state.centerY,
            state.macroHubRadius, state.macroHubRadius + 2, ringColor);
    }

    // ================================================================
    // TOOLTIP HELPERS
    // ================================================================

    /**
     * Generates tooltip text for a hovered macro segment.
     *
     * @param hoveredMacro  the macro being hovered
     * @param selectedMacro the currently selected macro
     * @return tooltip text with formatting codes
     */
    public static String getMacroTooltip(MacroCategory hoveredMacro, MacroCategory selectedMacro) {
        Objects.requireNonNull(hoveredMacro, "hoveredMacro cannot be null");
        Objects.requireNonNull(selectedMacro, "selectedMacro cannot be null");

        if (hoveredMacro == selectedMacro) {
            return "§a● " + hoveredMacro.getName() + " §7(active) - " + hoveredMacro.getDescription();
        } else {
            return "§7Click: §f" + hoveredMacro.getName() + " §7- " + hoveredMacro.getDescription();
        }
    }

    /**
     * Generates tooltip text for the center close button.
     *
     * @return tooltip text with formatting codes
     */
    public static String getCenterButtonTooltip() {
        return "§7Click to close §8| §7Scroll to search";
    }
}
