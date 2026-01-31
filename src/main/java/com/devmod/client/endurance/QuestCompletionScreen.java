package com.devmod.client.endurance;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.QuestCompletionPayload;

@OnlyIn(Dist.CLIENT)
public class QuestCompletionScreen extends Screen {

    // === Colors - Thematic victory screen (gold theme) ===
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_GOLD = DesignTokens.Semantic.WARNING;
    private static final int COLOR_GEM = EnduranceUiTheme.Accent.PURPLE;  // Blood gems (purple)
    private static final int COLOR_SUCCESS = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_BONUS = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_ORANGE = EnduranceUiTheme.Accent.ORANGE;  // Mutators/achievements

    // === Dimensions (base values, may be scaled for small screens) ===
    private static final int BASE_PANEL_WIDTH = DesignTokens.Component.MODAL_MAX_WIDTH;
    private static final int BASE_PANEL_HEIGHT = 380;
    private static final int MIN_PANEL_WIDTH = 280;

    // Calculated panel dimensions (set in init)
    private int PANEL_WIDTH = BASE_PANEL_WIDTH;
    private int PANEL_HEIGHT = BASE_PANEL_HEIGHT;

    // === Animation (optimized for snappier UX) ===
    private static final long FADE_IN_DURATION = 300;
    private static final long COUNTER_DURATION = 800;
    private static final long TROPHY_PULSE_PERIOD = 1500;

    // === Data ===
    private final QuestCompletionPayload data;

    // === State ===
    private long openTime;
    private boolean victoryFanfarePlayed = false;
    private int animatedTokens = 0;
    private int animatedGems = 0;
    @Nullable
    private EditorButton continueButton;

    public QuestCompletionScreen(QuestCompletionPayload data) {
        super(java.util.Objects.requireNonNull(Component.literal("Quest Complete!"), "title"));
        this.data = data;
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();

        // Calculate responsive panel dimensions
        int maxWidth = width - 40; // 20px margin on each side
        int maxHeight = height - 60; // 30px margin top/bottom
        PANEL_WIDTH = Math.max(MIN_PANEL_WIDTH, Math.min(BASE_PANEL_WIDTH, maxWidth));
        PANEL_HEIGHT = Math.max(MIN_PANEL_WIDTH, Math.min(BASE_PANEL_HEIGHT, maxHeight));

        continueButton = EditorButton.builder("completion-continue", "Continue")
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.LARGE)
            .onClick(this::closeScreen)
            .build();
    }

    @Override
    public void tick() {
        super.tick();

        long elapsed = System.currentTimeMillis() - openTime;

        // Animate token counter
        if (elapsed > FADE_IN_DURATION && elapsed < FADE_IN_DURATION + COUNTER_DURATION) {
            float progress = (elapsed - FADE_IN_DURATION) / (float) COUNTER_DURATION;
            animatedTokens = (int) (data.tokensEarned() * easeOutCubic(progress));
            animatedGems = (int) (data.bloodGemsEarned() * easeOutCubic(progress));
        } else if (elapsed >= FADE_IN_DURATION + COUNTER_DURATION) {
            animatedTokens = data.tokensEarned();
            animatedGems = data.bloodGemsEarned();
        }
    }

    private float easeOutCubic(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        long elapsed = System.currentTimeMillis() - openTime;
        float fadeProgress = Math.min(1.0f, elapsed / (float) FADE_IN_DURATION);

        // Play victory sound
        var mc = minecraft;
        if (!victoryFanfarePlayed && elapsed > 200 && mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), 1.0f)));
            victoryFanfarePlayed = true;
        }

        // Darken background
        int bgAlpha = (int) (0xEE * fadeProgress);
        graphics.fill(0, 0, width, height, (bgAlpha << 24) | EnduranceUiTheme.CompletionScreen.BACKDROP_RGB);

        int centerX = width / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // Panel with golden glow
        renderPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, fadeProgress);

        // Content
        if (fadeProgress > 0.3f) {
            float contentAlpha = (fadeProgress - 0.3f) / 0.7f;
            renderContent(graphics, panelX, panelY, contentAlpha, elapsed);
        }

        // Keybind hint
        if (fadeProgress > 0.8f) {
            float hintAlpha = (fadeProgress - 0.8f) / 0.2f;
            int hintColor = applyAlpha(DesignTokens.Text.MUTED, hintAlpha);
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font),
                "ESC / Enter: Continue", centerX, panelY + PANEL_HEIGHT + 10, hintColor);
        }

        renderButtons(graphics, mouseX, mouseY);
    }

    private void renderPanel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        int bgAlpha = (int) (0xDD * alpha);
        int borderAlpha = (int) (0xFF * alpha);
        int glowAlpha = (int) (0x44 * alpha);

        // Golden glow
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, (glowAlpha << 24) | EnduranceUiTheme.CompletionScreen.GOLD_RGB);

        // Background
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | EnduranceUiTheme.CompletionScreen.PANEL_RGB);

        // Border
        int borderColor = (borderAlpha << 24) | EnduranceUiTheme.CompletionScreen.GOLD_RGB;
        g.fill(x, y, x + w, y + 3, borderColor);
        g.fill(x, y + h - 3, x + w, y + h, borderColor);
        g.fill(x, y, x + 3, y + h, borderColor);
        g.fill(x + w - 3, y, x + w, y + h, borderColor);
    }

    private void renderContent(GuiGraphics g, int panelX, int panelY, float alpha, long elapsed) {
        var safeFont = Objects.requireNonNull(font);
        int centerX = panelX + PANEL_WIDTH / 2;

        // Trophy with pulse
        float pulse = (float) (Math.sin(elapsed / (double) TROPHY_PULSE_PERIOD * Math.PI * 2) * 0.2 + 0.8);
        int trophyColor = applyAlpha(COLOR_GOLD, alpha * pulse);

        String trophy = "\u2605"; // Star
        g.pose().pushPose();
        g.pose().translate(centerX - safeFont.width(trophy) * 2, panelY + 15, 0);
        g.pose().scale(4.0f, 4.0f, 1.0f);
        g.drawString(safeFont, trophy, 0, 0, trophyColor, true);
        g.pose().popPose();

        int y = panelY + 70;

        // Title
        String title = data.endlessMode() ? "ENDLESS MODE COMPLETE!" : "QUEST COMPLETE!";
        UIScaleManager.drawScaledCenteredString(g, safeFont, title, centerX, y, applyAlpha(COLOR_SUCCESS, alpha));
        y += 18;

        // Quest name and wave info (truncated to fit panel width)
        String questName = truncateText(data.questName(), PANEL_WIDTH - 40);
        UIScaleManager.drawScaledCenteredString(g, safeFont, Objects.requireNonNull(questName), centerX, y, applyAlpha(COLOR_TEXT, alpha));
        y += 14;

        String waveInfo = data.endlessMode()
            ? "Reached Wave " + data.finalWave()
            : "Completed " + data.finalWave() + "/" + data.totalWaves() + " waves";
        UIScaleManager.drawScaledCenteredString(g, safeFont, waveInfo, centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
        y += 14;

        // Duration
        UIScaleManager.drawScaledCenteredString(g, safeFont, "Time: " + data.getFormattedDuration(), centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
        y += 16;

        boolean hasRunInfo = (data.templateId() != null && !data.templateId().isBlank())
            || (data.policyId() != null && !data.policyId().isBlank())
            || (data.instanceId() != null && !data.instanceId().isBlank())
            || (data.arenaId() != null && !data.arenaId().isBlank());

        if (hasRunInfo) {
            UIScaleManager.drawScaledCenteredString(g, safeFont, "-- RUN INFO --", centerX, y, applyAlpha(COLOR_BONUS, alpha));
            y += 14;

            if (data.templateId() != null && !data.templateId().isBlank()) {
                String templateLine = "Template: " + data.templateId() + " v" + data.templateVersion();
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    Objects.requireNonNull(truncateText(templateLine, PANEL_WIDTH - 60), "templateLine"),
                    centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
                y += 12;
            }
            if (data.policyId() != null && !data.policyId().isBlank()) {
                String policyLine = "Policy: " + data.policyId() + " v" + data.policyVersion();
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    Objects.requireNonNull(truncateText(policyLine, PANEL_WIDTH - 60), "policyLine"),
                    centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
                y += 12;
            }

            String difficulty = data.difficultyLabel() != null && !data.difficultyLabel().isBlank()
                ? data.difficultyLabel() : "standard";
            String mode = data.questTypeLabel() != null && !data.questTypeLabel().isBlank()
                ? data.questTypeLabel() : "endurance";
            UIScaleManager.drawScaledCenteredString(g, safeFont, "Difficulty: " + difficulty + " | Mode: " + mode,
                centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
            y += 12;

            if (data.instanceId() != null && !data.instanceId().isBlank()) {
                String runLine = "Run ID: " + data.instanceId();
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    Objects.requireNonNull(truncateText(runLine, PANEL_WIDTH - 60), "runLine"),
                    centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
                y += 12;
            } else if (data.arenaId() != null && !data.arenaId().isBlank()) {
                String arenaLine = "Arena ID: " + data.arenaId();
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    Objects.requireNonNull(truncateText(arenaLine, PANEL_WIDTH - 60), "arenaLine"),
                    centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
                y += 12;
            }
            y += 6;
        } else {
            y += 8;
        }

        // Separator
        int sepColor = applyAlpha(DesignTokens.Stroke.MUTED, alpha);
        g.fill(panelX + 30, y, panelX + PANEL_WIDTH - 30, y + 1, sepColor);
        y += 15;

        // === REWARDS SECTION ===
        UIScaleManager.drawScaledCenteredString(g, safeFont, "-- REWARDS --", centerX, y, applyAlpha(COLOR_GOLD, alpha));
        y += 18;

        // Tokens (animated)
        String tokenText = "\u2B50 " + animatedTokens + " Tokens";
        UIScaleManager.drawScaledCenteredString(g, safeFont, tokenText, centerX, y, applyAlpha(COLOR_GOLD, alpha));
        y += 14;

        // Show multipliers if significant
        if (data.styleMultiplier() > 1.0f || data.mutatorMultiplier() > 1.0f) {
            String multText = Objects.requireNonNull(String.format("(Base: %d | Style: x%.1f | Mutator: x%.1f)",
                data.baseTokens(), data.styleMultiplier(), data.mutatorMultiplier()));
            UIScaleManager.drawScaledCenteredString(g, safeFont, multText, centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
            y += 14;
        }

        // Blood Gems
        if (data.bloodGemsEarned() > 0) {
            String gemText = "\u2666 " + animatedGems + " Blood Gems";
            UIScaleManager.drawScaledCenteredString(g, safeFont, gemText, centerX, y, applyAlpha(COLOR_GEM, alpha));
            y += 14;
        }

        // Prestige points
        if (data.prestigeEarned() > 0) {
            UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2726 " + data.prestigeEarned() + " Prestige", centerX, y, applyAlpha(COLOR_GEM, alpha));
            y += 14;
        }
        y += 8;

        // === BONUSES ===
        if (data.noHitBonus() || data.speedBonus() || data.activeMutators() > 0) {
            UIScaleManager.drawScaledCenteredString(g, safeFont, "-- BONUSES --", centerX, y, applyAlpha(COLOR_BONUS, alpha));
            y += 16;

            if (data.noHitBonus()) {
                UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2714 No Hit Bonus!", centerX, y, applyAlpha(COLOR_SUCCESS, alpha));
                y += 12;
            }
            if (data.speedBonus()) {
                UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2714 Speed Bonus!", centerX, y, applyAlpha(COLOR_SUCCESS, alpha));
                y += 12;
            }
            if (data.activeMutators() > 0) {
                UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2714 " + data.activeMutators() + " Mutators Active", centerX, y, applyAlpha(COLOR_ORANGE, alpha));
                y += 12;
            }
            y += 6;
        }

        // === STATS ===
        UIScaleManager.drawScaledCenteredString(g, safeFont, "-- STATS --", centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
        y += 16;

        int leftX = panelX + 50;
        int rightX = centerX + 20;

        // Two column layout
        UIScaleManager.drawScaledString(g, safeFont, "Kills: " + data.totalKills(), leftX, y, applyAlpha(COLOR_TEXT, alpha), false);
        UIScaleManager.drawScaledString(g, safeFont, "Max Combo: " + data.maxCombo(), rightX, y, applyAlpha(COLOR_TEXT, alpha), false);
        y += 12;

        UIScaleManager.drawScaledString(g, safeFont, "Damage Dealt: " + String.format("%.0f", data.totalDamageDealt()), leftX, y, applyAlpha(COLOR_TEXT, alpha), false);
        UIScaleManager.drawScaledString(g, safeFont, "Style Rank: " + data.getStyleRank().getDisplayName(), rightX, y, applyAlpha(data.getStyleRank().getColor(), alpha), false);
        y += 12;

        if (data.totalDamageTaken() > 0) {
            UIScaleManager.drawScaledString(g, safeFont, "Damage Taken: " + String.format("%.0f", data.totalDamageTaken()), leftX, y, applyAlpha(DesignTokens.Semantic.ERROR, alpha), false);
        }
        if (data.deaths() > 0) {
            UIScaleManager.drawScaledString(g, safeFont, "Deaths: " + data.deaths(), rightX, y, applyAlpha(DesignTokens.Semantic.ERROR, alpha), false);
        }
        y += 18;

        // === ACHIEVEMENTS ===
        List<String> achievements = data.achievementNames();
        if (!achievements.isEmpty()) {
            UIScaleManager.drawScaledCenteredString(g, safeFont, "-- ACHIEVEMENTS UNLOCKED --", centerX, y, applyAlpha(COLOR_GOLD, alpha));
            y += 14;

            int maxY = panelY + PANEL_HEIGHT - 60;
            int achievementsShown = 0;
            for (String achievement : achievements) {
                if (y > maxY) {
                    // Show indicator for remaining achievements
                    int remaining = achievements.size() - achievementsShown;
                    UIScaleManager.drawScaledCenteredString(g, safeFont, "+" + remaining + " more...", centerX, y, applyAlpha(DesignTokens.Text.MUTED, alpha));
                    break;
                }
                UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2605 " + achievement, centerX, y, applyAlpha(COLOR_ORANGE, alpha));
                y += 11;
                achievementsShown++;
            }
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a <= 0) a = (int) (255 * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int centerX = width / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int buttonWidth = 100;  // Standard medium button width
        int buttonHeight = DesignTokens.Component.BUTTON_HEIGHT_LG;
        int buttonX = centerX - buttonWidth / 2;
        int buttonY = panelY + PANEL_HEIGHT - 40;

        if (continueButton != null) {
            continueButton.render(graphics, buttonX, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }
    }

    /**
     * Truncate text to fit within maxWidth pixels, adding ellipsis if needed.
     */
    private String truncateText(String text, int maxWidth) {
        var safeFont = Objects.requireNonNull(font);
        if (safeFont.width(Objects.requireNonNull(text)) <= maxWidth) return text;
        String ellipsis = "...";
        int minChars = Math.min(6, text.length());
        String truncated = text;
        while (safeFont.width(truncated + ellipsis) > maxWidth && truncated.length() > minChars) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + ellipsis;
    }

    private void closeScreen() {
        var mc = minecraft;
        if (mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 1.0f)));
            mc.setScreen(null);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC or Enter to close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && continueButton != null && continueButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        if (continueButton != null) {
            handled = continueButton.mouseReleased(mouseX, mouseY, button);
        }
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
