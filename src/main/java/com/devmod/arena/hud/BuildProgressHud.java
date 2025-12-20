package com.devmod.arena.hud;

import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.arena.ui.BuildProgressOverlay.BuildPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DD39: Client-side HUD for displaying build progress.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Progress bar with percentage</li>
 *   <li>Phase indicator</li>
 *   <li>Block count display</li>
 *   <li>Smooth animation between updates</li>
 *   <li>Automatic fade-out on completion</li>
 * </ul>
 *
 * <p>Receives updates from {@link BuildProgressPayload} packets at 4Hz max.</p>
 */
public class BuildProgressHud {

    private static final BuildProgressHud INSTANCE = new BuildProgressHud();

    // ========== Layout Constants ==========

    /** HUD position from bottom-center */
    private static final int HUD_OFFSET_Y = 60;

    /** Progress bar dimensions */
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 12;
    private static final int BAR_BORDER = 2;

    /** Colors */
    private static final int COLOR_BACKGROUND = 0x80000000;
    private static final int COLOR_BORDER = 0xFF404040;
    private static final int COLOR_BAR_EMPTY = 0xFF202020;
    private static final int COLOR_BAR_FILL = 0xFF00AA00;
    private static final int COLOR_BAR_FILL_WARNING = 0xFFAAAA00;
    private static final int COLOR_BAR_FILL_COMPLETE = 0xFF00FF00;
    private static final int COLOR_BAR_FILL_FAILED = 0xFFFF0000;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SHADOW = 0xFF000000;

    /** Animation settings */
    private static final float SMOOTH_SPEED = 0.1f;
    private static final long FADE_OUT_DELAY_MS = 2000;
    private static final long FADE_OUT_DURATION_MS = 500;

    // ========== State ==========

    /** Active build progress by arena */
    private final Map<UUID, BuildProgressState> activeBuilds = new ConcurrentHashMap<>();

    /** Current displayed arena (null if none) */
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

        // Calculate position (bottom-center)
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = screenHeight - HUD_OFFSET_Y;

        // Render with alpha
        renderProgressBar(graphics, x, y, state, alpha);
    }

    private void renderProgressBar(GuiGraphics graphics, int x, int y, BuildProgressState state, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        var font = Objects.requireNonNull(mc.font);

        // Background panel
        int panelY = y - 20;
        int panelHeight = BAR_HEIGHT + 30;
        graphics.fill(x - 5, panelY, x + BAR_WIDTH + 5, panelY + panelHeight,
            applyAlpha(COLOR_BACKGROUND, alpha));

        // Phase text
        String phaseText = Objects.requireNonNull(getPhaseDisplayName(state.phase));
        int phaseWidth = font.width(phaseText);
        graphics.drawString(font, phaseText, x + (BAR_WIDTH - phaseWidth) / 2, panelY + 3,
            applyAlpha(COLOR_TEXT, alpha), false);

        // Progress bar border
        graphics.fill(x - BAR_BORDER, y - BAR_BORDER,
            x + BAR_WIDTH + BAR_BORDER, y + BAR_HEIGHT + BAR_BORDER,
            applyAlpha(COLOR_BORDER, alpha));

        // Progress bar background
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT,
            applyAlpha(COLOR_BAR_EMPTY, alpha));

        // Progress bar fill
        int fillWidth = (int) (BAR_WIDTH * state.displayedProgress);
        int fillColor = getProgressColor(state);
        graphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT,
            applyAlpha(fillColor, alpha));

        // Percentage text
        String percentText = Objects.requireNonNull(String.format("%.1f%%", state.displayedProgress * 100));
        int percentWidth = font.width(percentText);
        int textX = x + (BAR_WIDTH - percentWidth) / 2;
        int textY = y + (BAR_HEIGHT - 8) / 2;

        // Shadow
        graphics.drawString(font, percentText, textX + 1, textY + 1,
            applyAlpha(COLOR_TEXT_SHADOW, alpha), false);
        // Text
        graphics.drawString(font, percentText, textX, textY,
            applyAlpha(COLOR_TEXT, alpha), false);

        // Block count text
        String blockText = Objects.requireNonNull(String.format("%,d / %,d blocks", state.blocksPlaced, state.totalBlocks));
        int blockWidth = font.width(blockText);
        graphics.drawString(font, blockText, x + (BAR_WIDTH - blockWidth) / 2, y + BAR_HEIGHT + 4,
            applyAlpha(COLOR_TEXT, alpha), false);
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
            return COLOR_BAR_FILL_FAILED;
        }
        if (state.phase == BuildPhase.COMPLETE) {
            return COLOR_BAR_FILL_COMPLETE;
        }
        // Warning color if stuck (progress hasn't changed in a while)
        if (state.displayedProgress > 0.9 && state.displayedProgress < 1.0) {
            return COLOR_BAR_FILL_WARNING;
        }
        return COLOR_BAR_FILL;
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

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
