package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.QuestActionPayload;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)

public class WaveCheckpointScreen extends Screen {

    // === Colors - Using DesignTokens for consistency ===
    private static final int COLOR_BG_TOP = DesignTokens.Bg.LEVEL_2;
    private static final int COLOR_BG_BOTTOM = DesignTokens.Surface.LEVEL_0;
    private static final int COLOR_BORDER = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_SUCCESS = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_GOLD = DesignTokens.Semantic.WARNING;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_ACCENT = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_DAMAGE = DesignTokens.Semantic.ERROR;

    // === Dimensions - using DesignTokens for consistency ===
    private static final int PANEL_WIDTH = DesignTokens.Component.MODAL_MAX_WIDTH;
    private static final int PANEL_HEIGHT = 380;  // Increased from 260 for better spacing

    // === Animation Timing (ms) ===
    private static final long FADE_IN_DURATION = 400;
    private static final long HEADER_REVEAL_DELAY = 200;
    private static final long STATS_REVEAL_DELAY = 600;
    private static final long STATS_STAGGER = 150;       // Delay between each stat
    private static final long COUNTER_DURATION = 800;    // How long counters take to reach final value
    private static final long RANK_REVEAL_DELAY = 1400;
    private static final long BUTTONS_REVEAL_DELAY = 1800;
    private static final long PARTICLE_START_DELAY = 500;

    // === Wave data ===
    private final int waveNumber;
    private final int totalWaves;
    private final boolean endlessMode;
    private final int pointsEarned;
    private final int mobsKilled;
    private final int maxCombo;
    private final ComboSystem.StyleRank styleRank;
    private final int damageDealt;
    private final long sessionDurationMs;

    // === Animation State ===
    private long openTime;
    private boolean soundPlayed = false;
    private boolean rankSoundPlayed = false;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    private record StatsLayout(int statsY, int boxHeight, int rowStep, int statsBottom) {}

    // === Buttons (custom for fade) ===
    @Nullable
    private EditorButton continueButton;
    @Nullable
    private EditorButton exitButton;

    public WaveCheckpointScreen() {
        super(I18n.translate("devmod.endurance.wave"));
        LOGGER.info("[CheckpointScreen] Constructor called");

        // Capture data from ClientQuestCache
        this.waveNumber = ClientQuestCache.getCurrentWave();
        this.totalWaves = ClientQuestCache.getTotalWaves();
        this.endlessMode = ClientQuestCache.isEndlessMode();
        this.pointsEarned = ClientQuestCache.getPointsEarned();
        this.mobsKilled = ClientQuestCache.getMobsKilledInWave();
        this.maxCombo = ClientQuestCache.getMaxCombo();
        this.styleRank = ClientQuestCache.getStyleRank();
        this.damageDealt = ClientQuestCache.getDamageDealt();
        this.sessionDurationMs = ClientQuestCache.getSessionDuration();
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        LOGGER.info("[CheckpointScreen] init() called, screen dimensions: {}x{}", width, height);

        // Continue button (primary CTA)
        String continueText = endlessMode
            ? I18n.translate("devmod.endurance.starting_wave", waveNumber + 1).getString()
            : I18n.translate("devmod.endurance.wave").getString() + " " + (waveNumber + 1) + "/" + totalWaves;

        continueButton = EditorButton.builder("checkpoint-continue", continueText)
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.LARGE)
            .onClick(this::continueToNextWave)
            .build();

        // Exit button (secondary action)
        exitButton = EditorButton.builder("checkpoint-exit", I18n.translate("devmod.ui.exit_collect").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.LARGE)
            .onClick(this::exitAndCollect)
            .build();
    }

    @Override
    public void tick() {
        super.tick();

        long elapsed = System.currentTimeMillis() - openTime;

        // Spawn particles after delay
        if (elapsed > PARTICLE_START_DELAY && elapsed < PARTICLE_START_DELAY + 2000) {
            if (random.nextFloat() < 0.3f) {
                spawnParticle();
            }
        }

        // Update particles
        particles.removeIf(Particle::isDead);
        for (Particle p : particles) {
            p.update();
        }
    }

    private boolean renderLogged = false;

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);

        if (!renderLogged) {
            LOGGER.info("[CheckpointScreen] First render call!");
            renderLogged = true;
        }
        long elapsed = System.currentTimeMillis() - openTime;

        // Calculate fade-in progress
        float fadeProgress = Math.min(1.0f, (float) elapsed / FADE_IN_DURATION);
        float scaleProgress = easeOutBack(fadeProgress);

        // Dim background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;

        // Apply scale animation
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scaleProgress, scaleProgress, 1.0f);
        graphics.pose().translate(-centerX, -centerY, 0);

        int panelX = centerX - sPanelWidth / 2;
        int panelY = centerY - sPanelHeight / 2;
        StatsLayout statsLayout = computeStatsLayout(panelY);

        // === Panel Background with Gradient ===
        renderPanelWithGradient(graphics, panelX, panelY, sPanelWidth, sPanelHeight, fadeProgress);

        // === Header (animated) ===
        if (elapsed > HEADER_REVEAL_DELAY) {
            float headerAlpha = Math.min(1.0f, (elapsed - HEADER_REVEAL_DELAY) / 300.0f);
            renderHeader(graphics, centerX, panelY, headerAlpha, elapsed);

            // Play completion sound once
            var mc = minecraft;
            if (!soundPlayed && mc != null) {
                mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                    Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP), 1.0f, 0.8f)));
                soundPlayed = true;
            }
        }

        // === Statistics (staggered reveal with animated counters) ===
        if (elapsed > STATS_REVEAL_DELAY) {
            renderAnimatedStats(graphics, panelX, panelY, centerX, elapsed, statsLayout);
        }

        int minRankY = panelY + s(210);
        int rankY = Math.max(minRankY, statsLayout.statsBottom() + s(16));
        int progressY = rankY + s(42);
        int maxProgressY = panelY + sPanelHeight - s(110);
        if (progressY > maxProgressY) {
            progressY = maxProgressY;
            rankY = progressY - s(42);
        }

        // === Style Rank Reveal (dramatic) ===
        if (elapsed > RANK_REVEAL_DELAY) {
            float rankAlpha = Math.min(1.0f, (elapsed - RANK_REVEAL_DELAY) / 400.0f);
            renderStyleRankReveal(graphics, centerX, rankY, rankAlpha, elapsed);

            // Play rank reveal sound once
            var mc2 = minecraft;
            if (!rankSoundPlayed && mc2 != null) {
                float pitch = getRankPitch(styleRank);
                mc2.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                    Objects.requireNonNull(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), pitch, 0.6f)));
                rankSoundPlayed = true;
            }
        }

        // === Progress Bar (for non-endless) ===
        if (!endlessMode && elapsed > RANK_REVEAL_DELAY + 200) {
            renderProgressBar(graphics, panelX, progressY, sPanelWidth, elapsed);
        }

        // === Hint ===
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            float hintAlpha = Math.min(1.0f, (elapsed - BUTTONS_REVEAL_DELAY) / 300.0f);
            int hintColor = applyAlpha(DesignTokens.Text.MUTED, hintAlpha);
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font),
                "ESC/F11: Continue  |  F12: Exit",
                centerX, panelY + sPanelHeight - s(18), hintColor);
        }

        graphics.pose().popPose();

        // === Render Particles (outside scale transform) ===
        renderParticles(graphics);

        // Render buttons when visible
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            renderButtons(graphics, mouseX, mouseY);
        }
    }

    // === Rendering Methods ===

    private void renderPanelWithGradient(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        // Outer glow (pulsing)
        long elapsed = System.currentTimeMillis() - openTime;
        float pulse = 0.5f + 0.5f * (float) Math.sin(elapsed / 500.0);
        int glowAlpha = (int) (DesignTokens.Alpha.A27 * pulse * alpha);
        int glowColor = (glowAlpha << 24) | (COLOR_BORDER & DesignTokens.Mask.RGB);

        g.fill(x - s(4), y - s(4), x + w + s(4), y + h + s(4), glowColor);
        g.fill(x - s(3), y - s(3), x + w + s(3), y + h + s(3), glowColor);

        // Border
        int borderColor = applyAlpha(COLOR_BORDER, alpha);
        g.fill(x - s(2), y - s(2), x + w + s(2), y + h + s(2), borderColor);

        // Gradient background (top to bottom)
        int topColor = applyAlpha(COLOR_BG_TOP, alpha);
        int bottomColor = applyAlpha(COLOR_BG_BOTTOM, alpha);

        // Simple vertical gradient using horizontal slices
        for (int i = 0; i < h; i++) {
            float gradientProgress = (float) i / h;
            int lineColor = lerpColor(topColor, bottomColor, gradientProgress);
            g.fill(x, y + i, x + w, y + i + 1, lineColor);
        }

        // Inner border highlight (top)
        int highlightColor = applyAlpha(DesignTokens.Stroke.MUTED, alpha);
        g.fill(x, y, x + w, y + s(1), highlightColor);
    }

    private void renderHeader(GuiGraphics g, int centerX, int panelY, float alpha, long elapsed) {
        // Checkmark icon (animated scale) - larger and positioned better
        float checkScale = 1.0f + 0.1f * (float) Math.sin(elapsed / 200.0);
        String checkMark = "\u2713";
        int checkColor = applyAlpha(COLOR_SUCCESS, alpha);

        g.pose().pushPose();
        g.pose().translate(centerX - s(85), panelY + s(22), 0);
        g.pose().scale(checkScale * 1.8f, checkScale * 1.8f, 1.0f);  // Larger checkmark
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font), checkMark, 0, 0, checkColor, true);
        g.pose().popPose();

        // "WAVE X COMPLETE!" text - larger and better positioned
        String headerText = "WAVE " + waveNumber + " COMPLETE!";
        int headerColor = applyAlpha(COLOR_GOLD, alpha);

        // Slight bounce animation
        float bounce = elapsed < HEADER_REVEAL_DELAY + 300
            ? (float) Math.sin((elapsed - HEADER_REVEAL_DELAY) / 50.0) * 2
            : 0;

        UIScaleManager.drawScaledCenteredString(g, Objects.requireNonNull(font), headerText,
            centerX + s(12), (int)(panelY + s(22) + bounce), headerColor);

        // Separator with glow - wider and more visible
        int sepColor = applyAlpha(DesignTokens.withAlpha(COLOR_BORDER, DesignTokens.Alpha.A53), alpha);
        int sepHalfWidth = s(180);  // Wider separator
        g.fill(centerX - sepHalfWidth, panelY + s(46), centerX + sepHalfWidth, panelY + s(48), sepColor);  // Thicker line
    }

    private void renderAnimatedStats(GuiGraphics g, int panelX, int panelY, int centerX, long elapsed, StatsLayout layout) {
        long statsElapsed = elapsed - STATS_REVEAL_DELAY;
        var safeFont = Objects.requireNonNull(font);
        int lineHeight = UIScaleManager.getScaledLineHeight(safeFont, 10);
        int valueGap = s(4);  // Increased from 2
        int boxHeight = layout.boxHeight();
        int rowStep = layout.rowStep();
        int statsY = layout.statsY();
        int leftCol = panelX + s(24);  // Adjusted for wider boxes
        int rightCol = centerX + s(14);

        // Define stats with their reveal order
        StatEntry[] stats = {
            new StatEntry("Time", formatDuration(sessionDurationMs), COLOR_TEXT, 0, false),
            new StatEntry("Points", "+" + pointsEarned, COLOR_ACCENT, 0, true),
            new StatEntry("Kills", String.valueOf(mobsKilled), COLOR_SUCCESS, 1, true),
            new StatEntry("Damage", String.valueOf(damageDealt), COLOR_DAMAGE, 1, true),
            new StatEntry("Combo", String.valueOf(maxCombo), COLOR_GOLD, 2, true)
        };

        // Left column: Time, Kills, Combo
        int[] leftStats = {0, 2, 4};
        int[] rightStats = {1, 3};

        for (int idx : leftStats) {
            StatEntry stat = stats[idx];
            long statDelay = stat.row * STATS_STAGGER;
            if (statsElapsed > statDelay) {
                float statAlpha = Math.min(1.0f, (statsElapsed - statDelay) / 200.0f);
                String displayValue = stat.animate ? animateNumber(stat.value, statsElapsed - statDelay) : stat.value;
                renderStatBox(g, stat.label, displayValue, leftCol, statsY + stat.row * rowStep,
                    stat.valueColor, statAlpha, lineHeight, boxHeight, valueGap);
            }
        }

        for (int idx : rightStats) {
            StatEntry stat = stats[idx];
            long statDelay = stat.row * STATS_STAGGER;
            if (statsElapsed > statDelay) {
                float statAlpha = Math.min(1.0f, (statsElapsed - statDelay) / 200.0f);
                String displayValue = stat.animate ? animateNumber(stat.value, statsElapsed - statDelay) : stat.value;
                renderStatBox(g, stat.label, displayValue, rightCol, statsY + stat.row * rowStep,
                    stat.valueColor, statAlpha, lineHeight, boxHeight, valueGap);
            }
        }
    }

    private void renderStatBox(GuiGraphics g, String label, String value, int x, int y,
                               int valueColor, float alpha, int lineHeight, int boxHeight, int valueGap) {
        // Background box - wider for better readability
        int boxWidth = s(175);  // Increased from 140
        int bgColor = applyAlpha(DesignTokens.Surface.LEVEL_0, alpha);
        g.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        // Border on all sides for clearer visual boundary
        int borderColor = applyAlpha(DesignTokens.Stroke.MUTED, alpha);
        g.fill(x, y, x + boxWidth, y + 1, borderColor);  // Top
        g.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, borderColor);  // Bottom
        g.fill(x, y, x + 1, y + boxHeight, borderColor);  // Left
        g.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, borderColor);  // Right

        // Label with more padding
        var safeFont = Objects.requireNonNull(font);
        int labelColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        UIScaleManager.drawScaledString(g, safeFont, label,
            x + s(8), y + s(5), labelColor, false);

        // Value (larger, bold with shadow) - more padding
        int valColor = applyAlpha(valueColor, alpha);
        UIScaleManager.drawScaledString(g, safeFont, value,
            x + s(8), y + s(6) + lineHeight + valueGap, valColor, true);
    }

    private void renderStyleRankReveal(GuiGraphics g, int centerX, int y, float alpha, long elapsed) {
        // Dramatic style rank reveal with glow
        long rankElapsed = elapsed - RANK_REVEAL_DELAY;

        // Pulsing glow behind rank
        float pulse = 0.6f + 0.4f * (float) Math.sin(rankElapsed / 150.0);
        int glowRadius = (int) (s(30) * pulse);
        int glowColor = applyAlpha(DesignTokens.withAlpha(styleRank.getColor(), DesignTokens.Alpha.A27), alpha * pulse);

        g.fill(centerX - glowRadius, y - s(10), centerX + glowRadius, y + s(20), glowColor);

        // Scale animation for rank
        float rankScale = alpha < 1.0f ? 0.5f + alpha * 0.5f : 1.0f + 0.05f * (float) Math.sin(rankElapsed / 100.0);

        g.pose().pushPose();
        g.pose().translate(centerX, y + s(5), 0);
        g.pose().scale(rankScale * 1.8f, rankScale * 1.8f, 1.0f);

        var safeFont = Objects.requireNonNull(font);
        String rankText = Objects.requireNonNull(styleRank.getDisplayName());
        int rankWidth = UIScaleManager.getScaledStringWidth(safeFont, rankText);
        int rankColor = applyAlpha(styleRank.getColor(), alpha);
        UIScaleManager.drawScaledString(g, safeFont, rankText, -rankWidth / 2, -s(5), rankColor, true);

        g.pose().popPose();

        // "Style Rank" label above
        int labelColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        int lineHeight = UIScaleManager.getScaledLineHeight(safeFont, 10);
        UIScaleManager.drawScaledCenteredString(g, safeFont, "STYLE RANK", centerX, y - lineHeight, labelColor);
    }

    private StatsLayout computeStatsLayout(int panelY) {
        var safeFont = Objects.requireNonNull(font);
        int lineHeight = UIScaleManager.getScaledLineHeight(safeFont, 10);
        int valueGap = s(4);
        int boxHeight = lineHeight * 2 + valueGap + s(4);
        int rowGap = s(12);
        int rowStep = boxHeight + rowGap;
        int statsY = panelY + s(58);
        int statsBottom = statsY + rowStep * 2 + boxHeight;
        return new StatsLayout(statsY, boxHeight, rowStep, statsBottom);
    }

    private void renderProgressBar(GuiGraphics g, int panelX, int y, int panelWidth, long elapsed) {
        float alpha = Math.min(1.0f, (elapsed - RANK_REVEAL_DELAY - 200) / 300.0f);

        int barWidth = panelWidth - s(60);
        int barX = panelX + s(30);
        int barHeight = s(8);

        // Label
        var safeFont = Objects.requireNonNull(font);
        String progressText = "Quest Progress: " + waveNumber + "/" + totalWaves;
        int labelColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        UIScaleManager.drawScaledCenteredString(g, safeFont, progressText, panelX + panelWidth / 2, y - s(2), labelColor);

        // Bar background
        int bgColor = applyAlpha(DesignTokens.Surface.LEVEL_0, alpha);
        g.fill(barX, y + s(10), barX + barWidth, y + s(10) + barHeight, bgColor);

        // Animated fill (guard against division by zero)
        float targetProgress = totalWaves > 0 ? (float) waveNumber / totalWaves : 0f;
        long animElapsed = elapsed - RANK_REVEAL_DELAY - 200;
        float fillProgress = Math.min(targetProgress, targetProgress * (animElapsed / 500.0f));

        int fillWidth = (int) (barWidth * fillProgress);
        int fillColor = applyAlpha(COLOR_BORDER, alpha);

        // Gradient fill effect (guard against division by zero)
        for (int i = 0; i < fillWidth; i++) {
            float gradProgress = barWidth > 0 ? (float) i / barWidth : 0f;
            int lineColor = lerpColor(applyAlpha(COLOR_ACCENT, alpha), fillColor, gradProgress);
            g.fill(barX + i, y + s(10), barX + i + 1, y + s(10) + barHeight, lineColor);
        }

        // Shine effect
        if (fillWidth > 10) {
            int shineX = barX + (int)((elapsed / 20) % fillWidth);
            int shineColor = applyAlpha(DesignTokens.Stroke.MUTED, alpha);
            g.fill(shineX, y + s(10), shineX + s(3), y + s(10) + barHeight, shineColor);
        }

        // Border
        int borderColor = applyAlpha(DesignTokens.Stroke.MUTED, alpha);
        g.fill(barX, y + s(10), barX + barWidth, y + s(11), borderColor);
        g.fill(barX, y + s(10) + barHeight - s(1), barX + barWidth, y + s(10) + barHeight, borderColor);
    }

    // === Particle System ===

    private void spawnParticle() {
        int x = width / 2 + random.nextInt(s(200)) - s(100);
        int y = height / 2 - s(PANEL_HEIGHT) / 2 - s(20);
        int color = random.nextBoolean() ? COLOR_GOLD : COLOR_ACCENT;
        particles.add(new Particle(x, y, color));
    }

    private void renderParticles(GuiGraphics g) {
        for (Particle p : particles) {
            p.render(g);
        }
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - sPanelWidth / 2;
        int panelY = centerY - sPanelHeight / 2;

        // Wider buttons with more spacing
        int buttonWidth = (sPanelWidth - s(80)) / 2;  // Increased gap between buttons
        int buttonHeight = s(DesignTokens.Component.BUTTON_HEIGHT_LG + 4);  // Slightly taller
        int buttonY = panelY + sPanelHeight - s(65);  // Moved up for better spacing

        if (continueButton != null) {
            continueButton.render(graphics, panelX + s(30), buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }
        if (exitButton != null) {
            exitButton.render(graphics, panelX + sPanelWidth - buttonWidth - s(30), buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }
    }

    // === Helper Methods ===

    private String animateNumber(String finalValue, long elapsed) {
        // Extract numeric part
        boolean hasPlus = finalValue.startsWith("+");
        String numStr = hasPlus ? finalValue.substring(1) : finalValue;

        try {
            int target = Integer.parseInt(numStr);
            float progress = Math.min(1.0f, (float) elapsed / COUNTER_DURATION);
            progress = easeOutQuad(progress);
            int current = (int) (target * progress);
            return (hasPlus ? "+" : "") + current;
        } catch (NumberFormatException e) {
            return finalValue;
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    private static int lerpColor(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2));
    }

    private static float easeOutQuad(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }

    private static float getRankPitch(ComboSystem.StyleRank rank) {
        return switch (rank) {
            case D -> 0.8f;
            case C -> 0.9f;
            case B -> 1.0f;
            case A -> 1.1f;
            case S -> 1.2f;
            case SS -> 1.3f;
            case SSS -> 1.4f;
        };
    }

    // === Actions ===

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(WaveCheckpointScreen.class);

    private void continueToNextWave() {
        LOGGER.info("[CheckpointDebug] continueToNextWave called, sending CONTINUE_TO_NEXT_WAVE action");
        ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_CONTINUE,
            ClientActionContexts.forClient(ActionOrigin.UI, QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void exitAndCollect() {
        ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_EXIT,
            ClientActionContexts.forClient(ActionOrigin.UI, QuestActionPayload.Action.EXIT_AT_CHECKPOINT)
                .withConfirmed(true));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        if (button == 0 && System.currentTimeMillis() - openTime > BUTTONS_REVEAL_DELAY) {
            if (continueButton != null && continueButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (exitButton != null && exitButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        boolean handled = false;
        if (System.currentTimeMillis() - openTime > BUTTONS_REVEAL_DELAY) {
            if (continueButton != null) handled |= continueButton.mouseReleased(mouseX, mouseY, button);
            if (exitButton != null) handled |= exitButton.mouseReleased(mouseX, mouseY, button);
        }
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_F11) {
            continueToNextWave();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F12) {
            exitAndCollect();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // === Inner Classes ===

    private record StatEntry(String label, String value, int valueColor, int row, boolean animate) {}

    private class Particle {
        float x, y;
        float vx, vy;
        float life;
        int color;
        float size;

        Particle(float x, float y, int color) {
            this.x = x;
            this.y = y;
            this.vx = (random.nextFloat() - 0.5f) * 3;
            this.vy = random.nextFloat() * -2 - 1;
            this.life = 1.0f;
            this.color = color;
            this.size = 2 + random.nextFloat() * 3;
        }

        void update() {
            x += vx;
            y += vy;
            vy += 0.05f; // Gravity
            life -= 0.02f;
        }

        boolean isDead() {
            return life <= 0;
        }

        void render(GuiGraphics g) {
            int alpha = (int) (255 * life);
            int c = (alpha << 24) | (color & DesignTokens.Mask.RGB);
            int s = (int) (size * life);
            g.fill((int)x - s/2, (int)y - s/2, (int)x + s/2, (int)y + s/2, c);
        }
    }
}
