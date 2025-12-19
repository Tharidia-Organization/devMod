package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Premium Checkpoint screen with cinematic animations.
 * Uses standard UIConstants for consistent theming.
 *
 * Features:
 * - Smooth fade-in with scale animation
 * - Animated stat counters that tick up
 * - Particle celebration effects
 * - Pulsing glow on style rank reveal
 * - Sound effects on stat reveals
 * - Gradient backgrounds with depth
 */
@OnlyIn(Dist.CLIENT)

public class WaveCheckpointScreen extends Screen {

    // === Colors - Standardized to UIConstants ===
    private static final int COLOR_BG_TOP = UIConstants.Background.PANEL();
    private static final int COLOR_BG_BOTTOM = UIConstants.Background.INPUT();
    private static final int COLOR_BORDER = UIConstants.Border.DEFAULT();  // Blue instead of orange
    private static final int COLOR_SUCCESS = UIConstants.Accent.GREEN();
    private static final int COLOR_GOLD = UIConstants.Accent.GOLD();
    private static final int COLOR_TEXT = UIConstants.Text.PRIMARY();
    private static final int COLOR_TEXT_DIM = UIConstants.Text.SECONDARY();
    private static final int COLOR_ACCENT = UIConstants.Accent.BLUE();  // Blue instead of orange

    // === Dimensions - using UIConstants for consistency ===
    private static final int PANEL_WIDTH = UIConstants.Size.DIALOG_WIDTH_MEDIUM;
    private static final int PANEL_HEIGHT = 260;

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

    // === Buttons (custom for fade) ===
    private EditorButton continueButton;
    private EditorButton exitButton;

    public WaveCheckpointScreen() {
        super(I18n.translate("devmod.endurance.wave"));

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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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

        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // === Panel Background with Gradient ===
        renderPanelWithGradient(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, fadeProgress);

        // === Header (animated) ===
        if (elapsed > HEADER_REVEAL_DELAY) {
            float headerAlpha = Math.min(1.0f, (elapsed - HEADER_REVEAL_DELAY) / 300.0f);
            renderHeader(graphics, centerX, panelY, headerAlpha, elapsed);

            // Play completion sound once
            if (!soundPlayed && minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f));
                soundPlayed = true;
            }
        }

        // === Statistics (staggered reveal with animated counters) ===
        if (elapsed > STATS_REVEAL_DELAY) {
            renderAnimatedStats(graphics, panelX, panelY, centerX, elapsed);
        }

        // === Style Rank Reveal (dramatic) ===
        if (elapsed > RANK_REVEAL_DELAY) {
            float rankAlpha = Math.min(1.0f, (elapsed - RANK_REVEAL_DELAY) / 400.0f);
            renderStyleRankReveal(graphics, centerX, panelY + 170, rankAlpha, elapsed);

            // Play rank reveal sound once
            if (!rankSoundPlayed && minecraft != null) {
                float pitch = 0.8f + (styleRank.ordinal() * 0.1f);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, pitch, 0.6f));
                rankSoundPlayed = true;
            }
        }

        // === Progress Bar (for non-endless) ===
        if (!endlessMode && elapsed > RANK_REVEAL_DELAY + 200) {
            renderProgressBar(graphics, panelX, panelY + 195, PANEL_WIDTH, elapsed);
        }

        // === Hint ===
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            float hintAlpha = Math.min(1.0f, (elapsed - BUTTONS_REVEAL_DELAY) / 300.0f);
            int hintColor = applyAlpha(UIConstants.Text.MUTED(), hintAlpha);
            graphics.drawCenteredString(font, "ESC/F11: Continue  |  F12: Exit", centerX, panelY + PANEL_HEIGHT - 22, hintColor);
        }

        graphics.pose().popPose();

        // === Render Particles (outside scale transform) ===
        renderParticles(graphics, centerX, centerY);

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
        int glowAlpha = (int) (0x44 * pulse * alpha);
        int glowColor = (glowAlpha << 24) | (COLOR_BORDER & 0x00FFFFFF);

        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, glowColor);
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, glowColor);

        // Border
        int borderColor = applyAlpha(COLOR_BORDER, alpha);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, borderColor);

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
        int highlightColor = applyAlpha(UIConstants.Border.SEPARATOR(), alpha);
        g.fill(x, y, x + w, y + 1, highlightColor);
    }

    private void renderHeader(GuiGraphics g, int centerX, int panelY, float alpha, long elapsed) {
        // Checkmark icon (animated scale)
        float checkScale = 1.0f + 0.1f * (float) Math.sin(elapsed / 200.0);
        String checkMark = "\u2713";
        int checkColor = applyAlpha(COLOR_SUCCESS, alpha);

        g.pose().pushPose();
        g.pose().translate(centerX - 70, panelY + 18, 0);
        g.pose().scale(checkScale * 1.5f, checkScale * 1.5f, 1.0f);
        g.drawString(font, checkMark, 0, 0, checkColor, true);
        g.pose().popPose();

        // "WAVE X COMPLETE!" text
        String headerText = "WAVE " + waveNumber + " COMPLETE!";
        int headerColor = applyAlpha(COLOR_GOLD, alpha);

        // Slight bounce animation
        float bounce = elapsed < HEADER_REVEAL_DELAY + 300
            ? (float) Math.sin((elapsed - HEADER_REVEAL_DELAY) / 50.0) * 2
            : 0;

        g.drawCenteredString(font, headerText, centerX + 10, (int)(panelY + 18 + bounce), headerColor);

        // Separator with glow
        int sepColor = applyAlpha(COLOR_BORDER & 0x88FFFFFF, alpha);
        g.fill(centerX - 150, panelY + 38, centerX + 150, panelY + 39, sepColor);
    }

    private void renderAnimatedStats(GuiGraphics g, int panelX, int panelY, int centerX, long elapsed) {
        long statsElapsed = elapsed - STATS_REVEAL_DELAY;
        int statsY = panelY + 55;
        int leftCol = panelX + 35;
        int rightCol = centerX + 25;
        int lineHeight = 26;

        // Define stats with their reveal order
        StatEntry[] stats = {
            new StatEntry("Time", formatDuration(sessionDurationMs), COLOR_TEXT, 0, false),
            new StatEntry("Points", "+" + pointsEarned, COLOR_ACCENT, 0, true),
            new StatEntry("Kills", String.valueOf(mobsKilled), COLOR_SUCCESS, 1, true),
            new StatEntry("Damage", String.valueOf(damageDealt), UIConstants.Accent.RED(), 1, true),
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
                renderStatBox(g, stat.label, displayValue, leftCol, statsY + stat.row * lineHeight, stat.valueColor, statAlpha);
            }
        }

        for (int idx : rightStats) {
            StatEntry stat = stats[idx];
            long statDelay = stat.row * STATS_STAGGER;
            if (statsElapsed > statDelay) {
                float statAlpha = Math.min(1.0f, (statsElapsed - statDelay) / 200.0f);
                String displayValue = stat.animate ? animateNumber(stat.value, statsElapsed - statDelay) : stat.value;
                renderStatBox(g, stat.label, displayValue, rightCol, statsY + stat.row * lineHeight, stat.valueColor, statAlpha);
            }
        }
    }

    private void renderStatBox(GuiGraphics g, String label, String value, int x, int y, int valueColor, float alpha) {
        // Background box
        int boxWidth = 140;
        int boxHeight = 22;
        int bgColor = applyAlpha(UIConstants.Background.INPUT(), alpha);
        g.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        // Border
        int borderColor = applyAlpha(UIConstants.Border.SEPARATOR(), alpha);
        g.fill(x, y, x + boxWidth, y + 1, borderColor);

        // Label
        int labelColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        g.drawString(font, label, x + 5, y + 3, labelColor, false);

        // Value (larger, bold with shadow)
        int valColor = applyAlpha(valueColor, alpha);
        g.drawString(font, value, x + 5, y + 12, valColor, true);
    }

    private void renderStyleRankReveal(GuiGraphics g, int centerX, int y, float alpha, long elapsed) {
        // Dramatic style rank reveal with glow
        long rankElapsed = elapsed - RANK_REVEAL_DELAY;

        // Pulsing glow behind rank
        float pulse = 0.6f + 0.4f * (float) Math.sin(rankElapsed / 150.0);
        int glowRadius = (int) (30 * pulse);
        int glowColor = applyAlpha(styleRank.color & 0x44FFFFFF, alpha * pulse);

        g.fill(centerX - glowRadius, y - 10, centerX + glowRadius, y + 20, glowColor);

        // Scale animation for rank
        float rankScale = alpha < 1.0f ? 0.5f + alpha * 0.5f : 1.0f + 0.05f * (float) Math.sin(rankElapsed / 100.0);

        g.pose().pushPose();
        g.pose().translate(centerX, y + 5, 0);
        g.pose().scale(rankScale * 1.8f, rankScale * 1.8f, 1.0f);

        String rankText = styleRank.displayName;
        int rankWidth = font.width(rankText);
        int rankColor = applyAlpha(styleRank.color, alpha);
        g.drawString(font, rankText, -rankWidth / 2, -5, rankColor, true);

        g.pose().popPose();

        // "Style Rank" label above
        int labelColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        g.drawCenteredString(font, "STYLE RANK", centerX, y - 15, labelColor);
    }

    private void renderProgressBar(GuiGraphics g, int panelX, int y, int panelWidth, long elapsed) {
        float alpha = Math.min(1.0f, (elapsed - RANK_REVEAL_DELAY - 200) / 300.0f);

        int barWidth = panelWidth - 60;
        int barX = panelX + 30;
        int barHeight = 8;

        // Label
        String progressText = "Quest Progress: " + waveNumber + "/" + totalWaves;
        int labelColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        g.drawCenteredString(font, progressText, panelX + panelWidth / 2, y - 2, labelColor);

        // Bar background
        int bgColor = applyAlpha(UIConstants.Background.INPUT(), alpha);
        g.fill(barX, y + 10, barX + barWidth, y + 10 + barHeight, bgColor);

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
            g.fill(barX + i, y + 10, barX + i + 1, y + 10 + barHeight, lineColor);
        }

        // Shine effect
        if (fillWidth > 10) {
            int shineX = barX + (int)((elapsed / 20) % fillWidth);
            int shineColor = applyAlpha(UIConstants.Border.SEPARATOR(), alpha);
            g.fill(shineX, y + 10, shineX + 3, y + 10 + barHeight, shineColor);
        }

        // Border
        int borderColor = applyAlpha(UIConstants.Border.MUTED(), alpha);
        g.fill(barX, y + 10, barX + barWidth, y + 11, borderColor);
        g.fill(barX, y + 10 + barHeight - 1, barX + barWidth, y + 10 + barHeight, borderColor);
    }

    // === Particle System ===

    private void spawnParticle() {
        int x = width / 2 + random.nextInt(200) - 100;
        int y = height / 2 - PANEL_HEIGHT / 2 - 20;
        int color = random.nextBoolean() ? COLOR_GOLD : COLOR_ACCENT;
        particles.add(new Particle(x, y, color));
    }

    private void renderParticles(GuiGraphics g, int centerX, int centerY) {
        for (Particle p : particles) {
            p.render(g);
        }
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        int buttonWidth = (PANEL_WIDTH - 60) / 2;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;
        int buttonY = panelY + PANEL_HEIGHT - 55;

        if (continueButton != null) {
            continueButton.render(graphics, panelX + 25, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }
        if (exitButton != null) {
            exitButton.render(graphics, panelX + PANEL_WIDTH - buttonWidth - 25, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
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
        return (a << 24) | (color & 0x00FFFFFF);
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

    // === Actions ===

    private void continueToNextWave() {
        PacketDistributor.sendToServer(
            new QuestActionPayload(QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE)
        );
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void exitAndCollect() {
        PacketDistributor.sendToServer(
            new QuestActionPayload(QuestActionPayload.Action.EXIT_AT_CHECKPOINT)
        );
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
            int c = (alpha << 24) | (color & 0x00FFFFFF);
            int s = (int) (size * life);
            g.fill((int)x - s/2, (int)y - s/2, (int)x + s/2, (int)y + s/2, c);
        }
    }
}
