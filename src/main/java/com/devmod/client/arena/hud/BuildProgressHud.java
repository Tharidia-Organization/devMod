package com.devmod.client.arena.hud;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.arena.BuildPhase;
import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;

public class BuildProgressHud {

    private static final BuildProgressHud INSTANCE = new BuildProgressHud();

    // ========== Layout Constants ==========

    /** HUD position from bottom-center */
    private static final int HUD_OFFSET_Y = 60;

    /** Progress bar dimensions */
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 12;
    private static final int BAR_BORDER = 2;

    /** Animation settings */
    private static final float SMOOTH_SPEED = 0.1f;
    private static final long FADE_OUT_DELAY_MS = 2000;
    private static final long FADE_OUT_DURATION_MS = 500;

    // ========== State ==========

    /** Active build progress by arena */
    private final Map<UUID, BuildProgressState> activeBuilds = new ConcurrentHashMap<>();

    /** Current displayed arena (null if none) */
    @Nullable
    private UUID displayedArena;

    private BuildProgressHud() {}

    public static BuildProgressHud getInstance() {
        return INSTANCE;
    }

    // ========== Packet Handler ==========

    /**
     * Handles an incoming progress payload from the server.
     */
    public void handlePayload(BuildProgressPayload payload) {
        UUID arenaId = payload.arenaId();

        if (payload.isComplete() || payload.isFailed()) {
            // Mark for fade-out
            BuildProgressState state = activeBuilds.get(arenaId);
            if (state != null) {
                state.completedAt = System.currentTimeMillis();
                state.targetProgress = payload.progressPercent();
                state.phase = payload.phase();
            }
        } else {
            // Update or create state
            BuildProgressState state = activeBuilds.computeIfAbsent(arenaId, k -> new BuildProgressState());
            state.targetProgress = payload.progressPercent();
            state.blocksPlaced = payload.blocksPlaced();
            state.totalBlocks = payload.totalBlocks();
            state.phase = payload.phase();
            state.completedAt = 0;

            // Set as displayed arena if none active
            if (displayedArena == null) {
                displayedArena = arenaId;
            }
        }
    }

    // ========== Rendering ==========

    /**
     * Renders the build progress HUD.
     * Called from client render event.
     */
    public void render(GuiGraphics graphics, float partialTick) {
        if (displayedArena == null) {
            return;
        }

        BuildProgressState state = activeBuilds.get(displayedArena);
        if (state == null) {
            displayedArena = null;
            return;
        }

        // Calculate fade-out
        float alpha = 1.0f;
        if (state.completedAt > 0) {
            long elapsed = System.currentTimeMillis() - state.completedAt;
            if (elapsed > FADE_OUT_DELAY_MS + FADE_OUT_DURATION_MS) {
                // Remove completed build
                activeBuilds.remove(displayedArena);
                displayedArena = findNextArena();
                return;
            } else if (elapsed > FADE_OUT_DELAY_MS) {
                alpha = 1.0f - ((elapsed - FADE_OUT_DELAY_MS) / (float) FADE_OUT_DURATION_MS);
            }
        }

        // Smooth progress animation
        state.displayedProgress += (state.targetProgress - state.displayedProgress) * SMOOTH_SPEED;

        UIScaleManager.update();

        // Calculate position (bottom-center)
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int sBarWidth = UIScaleManager.scale(BAR_WIDTH);
        int sBarHeight = UIScaleManager.scale(BAR_HEIGHT);
        int sHudOffsetY = UIScaleManager.scale(HUD_OFFSET_Y);

        int x = (screenWidth - sBarWidth) / 2;
        int y = screenHeight - sHudOffsetY;

        // Render with alpha
        renderProgressBar(graphics, x, y, sBarWidth, sBarHeight, state, alpha);
    }

    private void renderProgressBar(GuiGraphics graphics, int x, int y,
                                   int barWidth, int barHeight,
                                   BuildProgressState state, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        var font = Objects.requireNonNull(mc.font);
        int sBarBorder = UIScaleManager.scale(BAR_BORDER);
        int panelOffsetY = UIScaleManager.scale(20);
        int panelPadding = UIScaleManager.scale(5);
        int panelHeight = barHeight + UIScaleManager.scale(30);
        int panelY = y - panelOffsetY;

        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        if (panelY < safeTop) {
            panelY = safeTop;
            y = panelY + panelOffsetY;
        } else if (panelY + panelHeight > safeBottom) {
            panelY = safeBottom - panelHeight;
            y = panelY + panelOffsetY;
        }

        // Background panel
        graphics.fill(x - panelPadding, panelY, x + barWidth + panelPadding, panelY + panelHeight,
            applyAlpha(BuildProgressHudTheme.Panel.BACKGROUND, alpha));

        // Phase text
        String phaseText = Objects.requireNonNull(getPhaseDisplayName(state.phase));
        phaseText = truncateToWidth(font, phaseText, barWidth);
        int phaseWidth = font.width(phaseText);
        graphics.drawString(font, phaseText, x + (barWidth - phaseWidth) / 2, panelY + UIScaleManager.scale(3),
            applyAlpha(BuildProgressHudTheme.Panel.TEXT, alpha), false);

        // Progress bar border
        graphics.fill(x - sBarBorder, y - sBarBorder,
            x + barWidth + sBarBorder, y + barHeight + sBarBorder,
            applyAlpha(BuildProgressHudTheme.Panel.BORDER, alpha));

        // Progress bar background
        graphics.fill(x, y, x + barWidth, y + barHeight,
            applyAlpha(BuildProgressHudTheme.Panel.BAR_EMPTY, alpha));

        // Progress bar fill
        int fillWidth = (int) (barWidth * state.displayedProgress);
        int fillColor = getProgressColor(state);
        graphics.fill(x, y, x + fillWidth, y + barHeight,
            applyAlpha(fillColor, alpha));

        // Percentage text
        String percentText = Objects.requireNonNull(String.format("%.1f%%", state.displayedProgress * 100));
        int percentWidth = font.width(percentText);
        int textX = x + (barWidth - percentWidth) / 2;
        int textY = y + (barHeight - UIScaleManager.scale(8)) / 2;

        // Shadow
        graphics.drawString(font, percentText, textX + 1, textY + 1,
            applyAlpha(BuildProgressHudTheme.Panel.TEXT_SHADOW, alpha), false);
        // Text
        graphics.drawString(font, percentText, textX, textY,
            applyAlpha(BuildProgressHudTheme.Panel.TEXT, alpha), false);

        // Block count text
        String blockText = Objects.requireNonNull(String.format("%,d / %,d blocks", state.blocksPlaced, state.totalBlocks));
        blockText = truncateToWidth(font, blockText, barWidth);
        int blockWidth = font.width(blockText);
        graphics.drawString(font, blockText, x + (barWidth - blockWidth) / 2, y + barHeight + UIScaleManager.scale(4),
            applyAlpha(BuildProgressHudTheme.Panel.TEXT, alpha), false);
    }

    private String getPhaseDisplayName(BuildPhase phase) {
        return switch (phase) {
            case INITIALIZING -> "Initializing...";
            case LOADING_CHUNKS -> "Loading Chunks...";
            case PLACING_BLOCKS -> "Placing Blocks";
            case SPAWNING_ENTITIES -> "Spawning Entities...";
            case FINALIZING -> "Finalizing...";
            case COMPLETE -> "Build Complete!";
            case FAILED -> "Build Failed";
        };
    }

    private int getProgressColor(BuildProgressState state) {
        if (state.phase == BuildPhase.FAILED) {
            return BuildProgressHudTheme.Progress.FAILED;
        }
        if (state.phase == BuildPhase.COMPLETE) {
            return BuildProgressHudTheme.Progress.COMPLETE;
        }
        // Warning color if stuck (progress hasn't changed in a while)
        if (state.displayedProgress > 0.9 && state.displayedProgress < 1.0) {
            return BuildProgressHudTheme.Progress.WARNING;
        }
        return BuildProgressHudTheme.Progress.NORMAL;
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    private String truncateToWidth(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int minChars = Math.min(4, text.length());
        String trimmed = text;
        while (font.width(trimmed + ellipsis) > maxWidth && trimmed.length() > minChars) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    @Nullable
    private UUID findNextArena() {
        return activeBuilds.keySet().stream()
            .filter(id -> {
                BuildProgressState s = activeBuilds.get(id);
                return s != null && s.completedAt == 0;
            })
            .findFirst()
            .orElse(null);
    }

    // ========== Public API ==========

    /**
     * Clears all active build progress.
     */
    public void clear() {
        activeBuilds.clear();
        displayedArena = null;
    }

    /**
     * Returns true if there is an active build being displayed.
     */
    public boolean isActive() {
        return displayedArena != null;
    }

    /**
     * Returns the currently displayed arena ID.
     */
    @Nullable
    public UUID getDisplayedArena() {
        return displayedArena;
    }

    // ========== Internal State ==========

    private static class BuildProgressState {
        double targetProgress = 0.0;
        double displayedProgress = 0.0;
        int blocksPlaced = 0;
        int totalBlocks = 0;
        BuildPhase phase = BuildPhase.INITIALIZING;
        long completedAt = 0; // 0 = not completed
    }
}
