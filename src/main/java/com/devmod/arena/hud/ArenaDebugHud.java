package com.devmod.arena.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DD30: HUD Visibility - permission + toggle esplicito
 *
 * Debug HUD overlay for arena instances.
 * Only renders if player has permission AND has explicitly enabled the toggle.
 */
public class ArenaDebugHud {

    private static final ArenaDebugHud INSTANCE = new ArenaDebugHud();

    /** Permission required to see the debug HUD */
    public static final String PERMISSION_VIEW_HUD = "devmod.arena.debug.hud";

    /** HUD position constants */
    private static final int HUD_X = 5;
    private static final int HUD_Y = 5;
    private static final int LINE_HEIGHT = 10;
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int HEADER_COLOR = 0x00FF00;

    /** Current arena debug info */
    private ArenaDebugInfo currentInfo;

    private ArenaDebugHud() {}

    public static ArenaDebugHud getInstance() {
        return INSTANCE;
    }

    /**
     * Renders the debug HUD if conditions are met
     *
     * @param guiGraphics The graphics context
     * @param playerId The player's UUID
     * @param hasPermission Whether the player has the required permission
     */
    public void render(GuiGraphics guiGraphics, UUID playerId, boolean hasPermission) {
        // Permission check first
        if (!hasPermission) {
            return;
        }

        // Toggle check (default OFF)
        if (!ArenaDebugState.getInstance().isHudEnabled(playerId)) {
            return;
        }

        // No info to display
        if (currentInfo == null) {
            return;
        }

        renderHud(guiGraphics);
    }

    /**
     * Internal HUD rendering
     */
    private void renderHud(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = buildDebugLines();

        if (lines.isEmpty()) {
            return;
        }

        // Calculate dimensions
        int maxWidth = 0;
        for (String line : lines) {
            int width = mc.font.width(line);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }

        int height = lines.size() * LINE_HEIGHT + 4;
        int width = maxWidth + 10;

        // Draw background
        guiGraphics.fill(HUD_X - 2, HUD_Y - 2, HUD_X + width, HUD_Y + height, BACKGROUND_COLOR);

        // Draw lines
        int y = HUD_Y;
        for (int i = 0; i < lines.size(); i++) {
            int color = (i == 0) ? HEADER_COLOR : TEXT_COLOR;
            guiGraphics.drawString(mc.font, lines.get(i), HUD_X, y, color, false);
            y += LINE_HEIGHT;
        }
    }

    /**
     * Builds the debug info lines
     */
    private List<String> buildDebugLines() {
        List<String> lines = new ArrayList<>();

        if (currentInfo == null) {
            return lines;
        }

        lines.add("[Arena Debug HUD]");
        lines.add("Template: " + (currentInfo.templateId != null ? currentInfo.templateId.toString() : "none"));
        lines.add("State: " + currentInfo.arenaState);
        lines.add("Players: " + currentInfo.playerCount);
        lines.add("Duration: " + formatDuration(currentInfo.durationSeconds));

        if (currentInfo.isOverridden) {
            lines.add("* TEMPLATE OVERRIDE ACTIVE *");
        }

        if (currentInfo.extraInfo != null && !currentInfo.extraInfo.isEmpty()) {
            lines.add("");
            for (String extra : currentInfo.extraInfo) {
                lines.add("  " + extra);
            }
        }

        return lines;
    }

    /**
     * Formats duration in seconds to MM:SS or HH:MM:SS
     */
    private String formatDuration(long seconds) {
        if (seconds < 3600) {
            return String.format("%02d:%02d", seconds / 60, seconds % 60);
        }
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    /**
     * Updates the current arena debug info
     *
     * @param info The new debug info
     */
    public void updateInfo(ArenaDebugInfo info) {
        this.currentInfo = info;
    }

    /**
     * Clears the current debug info
     */
    public void clearInfo() {
        this.currentInfo = null;
    }

    /**
     * Debug information record
     */
    public record ArenaDebugInfo(
        ResourceLocation templateId,
        String arenaState,
        int playerCount,
        long durationSeconds,
        boolean isOverridden,
        List<String> extraInfo
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private ResourceLocation templateId;
            private String arenaState = "UNKNOWN";
            private int playerCount = 0;
            private long durationSeconds = 0;
            private boolean isOverridden = false;
            private List<String> extraInfo = new ArrayList<>();

            public Builder templateId(ResourceLocation templateId) {
                this.templateId = templateId;
                return this;
            }

            public Builder arenaState(String arenaState) {
                this.arenaState = arenaState;
                return this;
            }

            public Builder playerCount(int playerCount) {
                this.playerCount = playerCount;
                return this;
            }

            public Builder durationSeconds(long durationSeconds) {
                this.durationSeconds = durationSeconds;
                return this;
            }

            public Builder isOverridden(boolean isOverridden) {
                this.isOverridden = isOverridden;
                return this;
            }

            public Builder addExtraInfo(String info) {
                this.extraInfo.add(info);
                return this;
            }

            public ArenaDebugInfo build() {
                return new ArenaDebugInfo(templateId, arenaState, playerCount, durationSeconds, isOverridden, extraInfo);
            }
        }
    }
}
