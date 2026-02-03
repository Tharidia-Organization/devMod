package com.devmod.client.ui.radial.render;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.client.panels.context.ContextDetector;
import com.devmod.client.panels.context.ContextMode;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.radial.config.RadialMenuConstants;
import com.devmod.client.ui.radial.config.RadialMenuScaler;
import com.devmod.client.ui.radial.model.MacroCategory;
import com.devmod.util.I18n;

public final class RadialHubRenderer {

    private static final java.util.Map<MacroCategory, ItemStack> MACRO_ICON_ITEMS = java.util.Map.of(
        MacroCategory.ANALYZE, new ItemStack(Items.SPYGLASS),
        MacroCategory.TELEMETRY, new ItemStack(Items.REDSTONE),
        MacroCategory.COMBAT, new ItemStack(Items.DIAMOND_SWORD),
        MacroCategory.ARENA, new ItemStack(Items.SHIELD),
        MacroCategory.PLAY, new ItemStack(Items.MAP),
        MacroCategory.TOOLS, new ItemStack(Items.ANVIL)
    );

    private RadialHubRenderer() {
        // Utility class - no instantiation
    }

    // ================================================================
    // HUB STATE
    // ================================================================

    /**
     * Immutable state container for hub rendering.
     */
    public static final class HubState {
        public final int centerX;
        public final int centerY;
        public final int centerButtonRadius;
        public final int macroHubRadius;
        public final MacroCategory selectedMacro;
        public final @Nullable MacroCategory hoveredMacro;
        public final float[] segmentAnimations;
        public final @Nullable boolean[] macroAvailability;
        public final float categoryHoverAnim;
        public final boolean searchMode;
        public final boolean inSubcategory;
        public final ContextMode contextMode;
        public final long contextPulseStartMs;
        public final boolean reducedMotion;

        /**
         * Creates a hub state instance.
         */
        public HubState(int centerX,
                        int centerY,
                        int centerButtonRadius,
                        int macroHubRadius,
                        MacroCategory selectedMacro,
                        @Nullable MacroCategory hoveredMacro,
                        float[] segmentAnimations,
                        @Nullable boolean[] macroAvailability,
                        float categoryHoverAnim,
                        boolean searchMode,
                        boolean inSubcategory,
                        ContextMode contextMode,
                        long contextPulseStartMs,
                        boolean reducedMotion) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerButtonRadius = centerButtonRadius;
            this.macroHubRadius = macroHubRadius;
            this.selectedMacro = Objects.requireNonNull(selectedMacro, "selectedMacro cannot be null");
            this.hoveredMacro = hoveredMacro;
            this.segmentAnimations = Objects.requireNonNull(segmentAnimations, "segmentAnimations cannot be null");
            this.macroAvailability = macroAvailability;
            this.categoryHoverAnim = categoryHoverAnim;
            this.searchMode = searchMode;
            this.inSubcategory = inSubcategory;
            this.contextMode = Objects.requireNonNull(contextMode, "contextMode cannot be null");
            this.contextPulseStartMs = contextPulseStartMs;
            this.reducedMotion = reducedMotion;
        }

        public int centerX() { return centerX; }
        public int centerY() { return centerY; }
        public int centerButtonRadius() { return centerButtonRadius; }
        public int macroHubRadius() { return macroHubRadius; }
        public MacroCategory selectedMacro() { return selectedMacro; }
        public @Nullable MacroCategory hoveredMacro() { return hoveredMacro; }
        public float[] segmentAnimations() { return segmentAnimations; }
        public @Nullable boolean[] macroAvailability() { return macroAvailability; }
        public float categoryHoverAnim() { return categoryHoverAnim; }
        public boolean searchMode() { return searchMode; }
        public boolean inSubcategory() { return inSubcategory; }
        public ContextMode contextMode() { return contextMode; }
        public long contextPulseStartMs() { return contextPulseStartMs; }
        public boolean reducedMotion() { return reducedMotion; }
    }

    /**
     * Result of hover detection on the hub.
     *
     * @param hoveredMacro the macro being hovered (null if none)
     * @param centerHovered whether the center close button is hovered
     */
    public record HoverResult(@Nullable MacroCategory hoveredMacro, boolean centerHovered) {
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

        // Macro segments, starting from top (-PI/2), going clockwise
        double startOffset = RadialMenuConstants.MACRO_START_OFFSET;
        double adjustedAngle = angle - startOffset;
        if (adjustedAngle < 0) adjustedAngle += RadialMenuConstants.TWO_PI;

        int macroIndex = (int) (adjustedAngle / RadialMenuConstants.MACRO_SEGMENT_ANGLE) % RadialMenuConstants.MACRO_COUNT;
        return new HoverResult(MacroCategory.fromIndex(macroIndex), false);
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
        renderContextBadge(graphics, font, state);
        renderOuterRing(graphics, state);
    }

    /**
     * Renders the macro-category segments.
     */
    private static void renderMacroSegments(GuiGraphics graphics, Font font, HubState state) {
        MacroCategory[] macros = MacroCategory.values();
        double segmentAngle = RadialMenuConstants.MACRO_SEGMENT_ANGLE;
        double startOffset = RadialMenuConstants.MACRO_START_OFFSET;

        int innerR = (int) (state.centerButtonRadius * RadialMenuConstants.CLOSE_BUTTON_RATIO);
        int outerR = state.macroHubRadius;

        for (int i = 0; i < macros.length; i++) {
            MacroCategory macro = macros[i];
            boolean isAvailable = isMacroAvailable(state, i);
            boolean isSelected = macro == state.selectedMacro;
            boolean isHovered = macro == state.hoveredMacro && isAvailable;

            double segStart = startOffset + i * segmentAngle;
            double segEnd = segStart + segmentAngle;

            // Segment colors
            int baseColor = isSelected
                ? RadialGeometry.blendColors(RadialMenuConstants.COLOR_MACRO_SELECTED_BASE, macro.getColor(),
                    RadialMenuConstants.MACRO_SELECTED_BLEND)
                : RadialMenuConstants.COLOR_BG_DARK;
            if (isHovered && !isSelected) {
                baseColor = RadialGeometry.blendColors(baseColor, macro.getColor(), RadialMenuConstants.MACRO_HOVER_BLEND);
            }
            if (!isAvailable) {
                baseColor = RadialGeometry.blendColors(baseColor, RadialMenuConstants.COLOR_BG_DARK,
                    RadialMenuConstants.MACRO_DISABLED_BLEND);
            }

            // Render segment arc
            RadialGeometry.renderArcSegment(graphics, state.centerX, state.centerY,
                innerR, outerR, segStart, segEnd, baseColor);

            // Segment border
            int borderColor = isSelected
                ? macro.getColor()
                : (isHovered
                    ? RadialGeometry.blendColors(RadialMenuConstants.COLOR_MACRO_HOVER_BORDER, macro.getColor(),
                        RadialMenuConstants.BORDER_HOVER_BLEND)
                    : RadialMenuConstants.COLOR_BORDER);
            int borderWidth = isSelected
                ? RadialMenuScaler.scaleConstant(RadialMenuConstants.BORDER_WIDTH_SELECTED)
                : RadialMenuScaler.scaleConstant(RadialMenuConstants.BORDER_WIDTH_DEFAULT);
            if (!isAvailable) {
                borderColor = RadialGeometry.blendColors(borderColor, RadialMenuConstants.COLOR_BORDER,
                    RadialMenuConstants.MACRO_DISABLED_BLEND);
            }
            RadialGeometry.renderArcOutline(graphics, state.centerX, state.centerY,
                outerR, segStart, segEnd, borderColor, borderWidth);

            // Divider line at segment start
            renderSegmentDivider(graphics, state.centerX, state.centerY,
                innerR, outerR, segStart);

            // Render macro icon
            renderMacroIcon(graphics, font, state.centerX, state.centerY,
                innerR, outerR, segStart, segmentAngle, macro, isSelected, isHovered, isAvailable);
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
        RadialGeometry.drawLine(graphics, x1, y1, x2, y2, RadialMenuConstants.COLOR_DIVIDER);
    }

    /**
     * Renders a macro category icon within its segment.
     */
    private static void renderMacroIcon(GuiGraphics graphics, Font font,
                                         int cx, int cy, int innerR, int outerR,
                                         double segStart, double segmentAngle,
                                         MacroCategory macro, boolean isSelected, boolean isHovered,
                                         boolean isAvailable) {
        Font safeFont = Objects.requireNonNull(font, "font");
        double midAngle = segStart + segmentAngle / 2;
        int iconRadius = (innerR + outerR) / 2;
        int iconX = (int) (cx + Math.cos(midAngle) * iconRadius);
        int iconY = (int) (cy + Math.sin(midAngle) * iconRadius);

        ItemStack iconStack = resolveMacroIconStack(macro);
        if (iconStack != null && isAvailable) {
            float iconScale = RadialMenuConstants.MACRO_ICON_ITEM_SCALE;
            if (isSelected) {
                iconScale = RadialMenuConstants.MACRO_ICON_ITEM_SCALE_SELECTED;
            } else if (isHovered) {
                iconScale = RadialMenuConstants.MACRO_ICON_ITEM_SCALE_HOVER;
            }
            renderItemIcon(graphics, iconStack, iconX,
                iconY + RadialMenuScaler.scaleConstant(RadialMenuConstants.MACRO_ICON_ITEM_OFFSET_Y),
                iconScale);
            return;
        }

        int iconColor = isSelected
            ? RadialMenuConstants.COLOR_TEXT_PRIMARY
            : (isHovered ? macro.getColor() : RadialMenuConstants.COLOR_INACTIVE);
        if (!isAvailable) {
            iconColor = RadialGeometry.blendColors(iconColor, RadialMenuConstants.COLOR_INACTIVE,
                RadialMenuConstants.MACRO_DISABLED_BLEND);
        }
        String iconText = Objects.requireNonNullElse(macro.getIcon(), "");
        if (iconText.isBlank()) {
            String name = Objects.requireNonNull(macro.getName(), "macro name");
            iconText = name.isEmpty() ? "?" : name.substring(0, 1);
        }
        float fontScale = resolveFontScale();
        drawCenteredStringScaled(graphics, safeFont, Objects.requireNonNull(iconText, "iconText"), iconX,
            iconY + RadialMenuScaler.scaleConstant(RadialMenuConstants.MACRO_ICON_TEXT_OFFSET_Y), iconColor, fontScale);
    }

    @Nullable
    private static ItemStack resolveMacroIconStack(@Nullable MacroCategory macro) {
        if (macro == null) {
            return null;
        }
        return MACRO_ICON_ITEMS.get(macro);
    }

    private static void renderItemIcon(GuiGraphics graphics, ItemStack iconStack, int centerX, int centerY, float scale) {
        if (iconStack == null || iconStack.isEmpty()) {
            return;
        }
        float half = 8f * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - half, centerY - half, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(iconStack, 0, 0);
        graphics.pose().popPose();
    }

    private static boolean isMacroAvailable(HubState state, int index) {
        boolean[] availability = state.macroAvailability;
        if (availability == null || index < 0 || index >= availability.length) {
            return true;
        }
        return availability[index];
    }

    /**
     * Renders the center close/back button.
     */
    private static void renderCenterButton(GuiGraphics graphics, Font font, HubState state) {
        Font safeFont = Objects.requireNonNull(font, "font");
        boolean centerHovered = state.hoveredMacro == null &&
            state.categoryHoverAnim > RadialMenuConstants.CENTER_HOVER_THRESHOLD;

        int closeBtnRadius = (int) (state.centerButtonRadius * RadialMenuConstants.CLOSE_BUTTON_RATIO);

        // Background
        int closeBgColor = centerHovered ? RadialMenuConstants.COLOR_CLOSE_HOVER : RadialMenuConstants.COLOR_CLOSE_NORMAL;
        RadialGeometry.renderCircle(graphics, state.centerX, state.centerY,
            closeBtnRadius, closeBgColor);

        // Border
        int closeBorderColor = centerHovered ? RadialMenuConstants.COLOR_CLOSE_BORDER_HOVER : RadialMenuConstants.COLOR_DIVIDER;
        int borderThickness = RadialMenuScaler.scaleConstant(RadialMenuConstants.RING_BORDER_THICKNESS);
        RadialGeometry.renderRing(graphics, state.centerX, state.centerY,
            closeBtnRadius - borderThickness,
            closeBtnRadius, closeBorderColor);

        // Icon
        String centerIcon = null;
        ItemStack centerIconStack = null;
        int centerIconColor = RadialMenuConstants.COLOR_TEXT_PRIMARY;

        if (state.searchMode) {
            centerIcon = "\uD83D\uDD0D";
        } else if (state.inSubcategory) {
            centerIcon = "<";
            centerIconColor = centerHovered
                ? RadialMenuConstants.COLOR_CLOSE_BORDER_HOVER
                : RadialMenuConstants.COLOR_CENTER_ICON_BACK;
        } else if (centerHovered) {
            centerIcon = "X";
            centerIconColor = RadialMenuConstants.COLOR_CLOSE_BORDER_HOVER;
        } else {
            centerIconStack = resolveMacroIconStack(state.selectedMacro);
            if (centerIconStack == null || centerIconStack.isEmpty()) {
                centerIcon = Objects.requireNonNullElse(state.selectedMacro.getIcon(), "");
                centerIconColor = state.selectedMacro.getColor();
            }
        }

        if (centerIconStack != null && !centerIconStack.isEmpty()) {
            renderItemIcon(graphics, centerIconStack, state.centerX,
                state.centerY + RadialMenuScaler.scaleConstant(RadialMenuConstants.CENTER_ICON_ITEM_OFFSET_Y),
                RadialMenuConstants.CENTER_ICON_ITEM_SCALE);
        } else if (centerIcon != null) {
            float fontScale = resolveFontScale();
            drawCenteredStringScaled(graphics, safeFont, centerIcon, state.centerX,
                state.centerY + RadialMenuScaler.scaleConstant(RadialMenuConstants.CENTER_ICON_TEXT_OFFSET_Y), centerIconColor, fontScale);
        }
    }

    /**
     * Renders the outer ring around the entire hub.
     */
    private static void renderOuterRing(GuiGraphics graphics, HubState state) {
        int ringColor = RadialGeometry.blendColors(RadialMenuConstants.COLOR_DIVIDER,
            state.selectedMacro.getColor(), RadialMenuConstants.OUTER_RING_BLEND);
        int borderThickness = RadialMenuScaler.scaleConstant(RadialMenuConstants.RING_BORDER_THICKNESS);
        RadialGeometry.renderRing(graphics, state.centerX, state.centerY,
            state.macroHubRadius,
            state.macroHubRadius + borderThickness,
            ringColor);
    }

    // ================================================================
    // CONTEXT BADGE
    // ================================================================

    private static void renderContextBadge(GuiGraphics graphics, @Nonnull Font font, HubState state) {
        ContextMode mode = state.contextMode;
        String label = resolveContextLabel(mode);

        if (mode == ContextMode.COMBAT && ContextDetector.INSTANCE.isCurrentlyCombat()) {
            long remainingMs = ContextDetector.INSTANCE.getRemainingCombatTime();
            if (remainingMs > 0) {
                int remainingSec = (int) Math.ceil(remainingMs / 1000.0);
                label = label + " " + remainingSec + "s";
            }
        }

        float fontScale = resolveFontScale();
        int textWidth = scaledWidth(font, label, fontScale);
        int textHeight = Math.round(font.lineHeight * fontScale);

        int paddingX = RadialMenuScaler.scaleConstant(RadialMenuConstants.CONTEXT_BADGE_PADDING_X);
        int paddingY = RadialMenuScaler.scaleConstant(RadialMenuConstants.CONTEXT_BADGE_PADDING_Y);

        int badgeWidth = textWidth + paddingX * 2;
        int badgeHeight = textHeight + paddingY * 2;

        int closeBtnRadius = (int) (state.centerButtonRadius * RadialMenuConstants.CLOSE_BUTTON_RATIO);
        int offsetY = RadialMenuScaler.scaleConstant(RadialMenuConstants.CONTEXT_BADGE_OFFSET_Y);
        int badgeCenterY = state.centerY - closeBtnRadius - offsetY - (badgeHeight / 2);

        float pulseScale = 1f;
        if (!state.reducedMotion && state.contextPulseStartMs > 0) {
            long elapsed = System.currentTimeMillis() - state.contextPulseStartMs;
            long duration = RadialMenuConstants.CONTEXT_BADGE_PULSE_DURATION_MS;
            if (elapsed < duration) {
                float t = Math.min(1f, elapsed / (float) duration);
                pulseScale = 1f + (1f - t) * RadialMenuConstants.CONTEXT_BADGE_PULSE_SCALE;
            }
        }

        int badgeWidthPulse = Math.round(badgeWidth * pulseScale);
        int badgeHeightPulse = Math.round(badgeHeight * pulseScale);
        int badgeX = state.centerX - badgeWidthPulse / 2;
        int badgeY = badgeCenterY - badgeHeightPulse / 2;

        int badgeColor = resolveContextColor(mode);
        int radius = Math.max(1, badgeHeightPulse / 2);

        // Draw pill background
        RadialGeometry.renderCircle(graphics, badgeX + radius, badgeCenterY, radius, badgeColor);
        RadialGeometry.renderCircle(graphics, badgeX + badgeWidthPulse - radius, badgeCenterY, radius, badgeColor);
        graphics.fill(badgeX + radius, badgeY, badgeX + badgeWidthPulse - radius, badgeY + badgeHeightPulse, badgeColor);

        // Text
        drawCenteredStringScaled(graphics, font, label, state.centerX, badgeCenterY,
            RadialMenuConstants.CONTEXT_BADGE_TEXT, fontScale);
    }

    private static int resolveContextColor(ContextMode mode) {
        return switch (mode) {
            case COMBAT -> RadialMenuConstants.CONTEXT_BADGE_COMBAT;
            case EXPLORE -> RadialMenuConstants.CONTEXT_BADGE_EXPLORE;
            case TEST -> RadialMenuConstants.CONTEXT_BADGE_TEST;
            default -> RadialMenuConstants.CONTEXT_BADGE_DEFAULT;
        };
    }

    @Nonnull
    private static String resolveContextLabel(ContextMode mode) {
        String key = "devmod.context.mode." + mode.name().toLowerCase(Locale.ROOT);
        return Objects.requireNonNull(I18n.translate(key).getString());
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
            return I18n.translate("devmod.radial.macro.tooltip.active",
                hoveredMacro.getName(), hoveredMacro.getDescription()).getString();
        }
        return I18n.translate("devmod.radial.macro.tooltip.inactive",
            hoveredMacro.getName(), hoveredMacro.getDescription()).getString();
    }

    /**
     * Generates tooltip text for the center close button.
     *
     * @return tooltip text with formatting codes
     */
    public static String getCenterButtonTooltip() {
        return I18n.translate("devmod.radial.tooltip.close").getString();
    }

    private static float resolveFontScale() {
        float scale = RadialMenuScaler.getFontScale();
        return scale > 0f ? scale : 1f;
    }

    private static int scaledWidth(@Nonnull Font font, @Nonnull String text, float scale) {
        if (scale <= 0f || Math.abs(scale - 1f) < 0.001f) {
            return font.width(text);
        }
        return Math.round(font.width(text) * scale);
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
        UIScaleManager.drawScaledCenteredString(graphics, font, text, Math.round(x * inv), Math.round(y * inv), color);
        graphics.pose().popPose();
    }
}
