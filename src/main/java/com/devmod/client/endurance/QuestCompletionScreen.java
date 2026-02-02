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

    // === Dimensions (base values, scaled at runtime) ===
    private static final int BASE_PANEL_WIDTH = DesignTokens.Component.MODAL_MAX_WIDTH;
    private static final int BASE_PANEL_HEIGHT = 380;
    private static final int MIN_PANEL_WIDTH = 280;

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
        int maxWidth = width - UIScaleManager.scale(40);
        int maxHeight = height - UIScaleManager.scale(60);
        int contentHeight = calculateContentHeight();
        int sPanelWidth = Math.max(UIScaleManager.scale(MIN_PANEL_WIDTH),
            Math.min(UIScaleManager.scale(BASE_PANEL_WIDTH), maxWidth));
        int minPanelHeight = UIScaleManager.scale(MIN_PANEL_WIDTH);
        int basePanelHeight = UIScaleManager.scale(BASE_PANEL_HEIGHT);
        int sPanelHeight = Math.max(minPanelHeight, Math.min(basePanelHeight, maxHeight));
        sPanelHeight = Math.max(sPanelHeight, Math.min(maxHeight, contentHeight));
        int panelX = centerX - sPanelWidth / 2;
        int panelY = (height - sPanelHeight) / 2;

        // Panel with golden glow
        renderPanel(graphics, panelX, panelY, sPanelWidth, sPanelHeight, fadeProgress);

        // Content
        if (fadeProgress > 0.3f) {
            float contentAlpha = (fadeProgress - 0.3f) / 0.7f;
            renderContent(graphics, panelX, panelY, sPanelWidth, sPanelHeight, contentAlpha, elapsed);
        }

        // Keybind hint
        if (fadeProgress > 0.8f) {
            float hintAlpha = (fadeProgress - 0.8f) / 0.2f;
            int hintColor = applyAlpha(DesignTokens.Text.MUTED, hintAlpha);
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font),
                "ESC / Enter: Continue", centerX, panelY + sPanelHeight + UIScaleManager.scale(10), hintColor);
        }

        renderButtons(graphics, mouseX, mouseY, sPanelHeight);
    }

    private int calculateContentHeight() {
        var safeFont = Objects.requireNonNull(font, "font");
        int lineHeight = UIScaleManager.getScaledLineHeight(safeFont, 10);
        int titleGap = lineHeight + UIScaleManager.scale(6);
        int lineGap = lineHeight + UIScaleManager.scale(2);
        int smallGap = lineHeight;
        int sectionGap = lineHeight + UIScaleManager.scale(6);
        int blockGap = lineHeight + UIScaleManager.scale(8);

        int height = 0;
        height += UIScaleManager.scale(70); // top offset for trophy and title
        height += titleGap; // title
        height += lineGap;  // quest name
        height += lineGap;  // wave info
        height += sectionGap; // duration + spacing

        boolean hasRunInfo = (data.templateId() != null && !data.templateId().isBlank())
            || (data.policyId() != null && !data.policyId().isBlank())
            || (data.instanceId() != null && !data.instanceId().isBlank())
            || (data.arenaId() != null && !data.arenaId().isBlank());
        if (hasRunInfo) {
            height += lineGap; // run header
            if (data.templateId() != null && !data.templateId().isBlank()) height += smallGap;
            if (data.policyId() != null && !data.policyId().isBlank()) height += smallGap;
            height += smallGap; // difficulty/mode
            if ((data.instanceId() != null && !data.instanceId().isBlank())
                || (data.arenaId() != null && !data.arenaId().isBlank())) {
                height += smallGap;
            }
            height += UIScaleManager.scale(6);
        } else {
            height += UIScaleManager.scale(8);
        }

        height += UIScaleManager.scale(15); // separator

        // Rewards
        height += blockGap; // header + spacing
        height += lineGap; // tokens
        if (data.styleMultiplier() > 1.0f || data.mutatorMultiplier() > 1.0f) height += lineGap;
        if (data.bloodGemsEarned() > 0) height += lineGap;
        if (data.prestigeEarned() > 0) height += lineGap;
        height += UIScaleManager.scale(8);

        // Bonuses
        if (data.noHitBonus() || data.speedBonus() || data.activeMutators() > 0) {
            height += sectionGap; // header
            if (data.noHitBonus()) height += smallGap;
            if (data.speedBonus()) height += smallGap;
            if (data.activeMutators() > 0) height += smallGap;
            height += UIScaleManager.scale(6);
        }

        // Stats
        height += sectionGap; // header
        boolean twoColumn = UIScaleManager.scale(BASE_PANEL_WIDTH) >= UIScaleManager.scale(360);
        int statsLines = 2;
        if (data.totalDamageTaken() > 0 || data.deaths() > 0) {
            statsLines = 3;
        }
        height += statsLines * smallGap + (twoColumn ? blockGap : UIScaleManager.scale(4));

        // Achievements
        if (!data.achievementNames().isEmpty()) {
            height += lineGap; // header
            height += Math.min(data.achievementNames().size(), 3) * UIScaleManager.scale(11);
        }

        // Footer space for buttons
        height += UIScaleManager.scale(64);
        return height;
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

    private void renderContent(GuiGraphics g, int panelX, int panelY, int panelWidth, int panelHeight, float alpha, long elapsed) {
        var safeFont = Objects.requireNonNull(font);
        int centerX = panelX + panelWidth / 2;
        int lineHeight = UIScaleManager.getScaledLineHeight(safeFont, 10);
        int titleGap = lineHeight + UIScaleManager.scale(6);
        int lineGap = lineHeight + UIScaleManager.scale(2);
        int smallGap = lineHeight;
        int sectionGap = lineHeight + UIScaleManager.scale(6);
        int blockGap = lineHeight + UIScaleManager.scale(8);
        int inset = UIScaleManager.scale(30);
        int contentBottom = panelY + panelHeight - UIScaleManager.scale(64);

        // Trophy with pulse
        float pulse = (float) (Math.sin(elapsed / (double) TROPHY_PULSE_PERIOD * Math.PI * 2) * 0.2 + 0.8);
        int trophyColor = applyAlpha(COLOR_GOLD, alpha * pulse);

        String trophy = "\u2605"; // Star
        g.pose().pushPose();
        g.pose().translate(centerX - safeFont.width(trophy) * 2, panelY + UIScaleManager.scale(15), 0);
        g.pose().scale(4.0f, 4.0f, 1.0f);
        UIScaleManager.drawScaledString(g, safeFont, trophy, 0, 0, trophyColor, true);
        g.pose().popPose();

        int y = panelY + UIScaleManager.scale(70);

        // Title
        String title = data.endlessMode() ? "ENDLESS MODE COMPLETE!" : "QUEST COMPLETE!";
        UIScaleManager.drawScaledCenteredString(g, safeFont, title, centerX, y, applyAlpha(COLOR_SUCCESS, alpha));
        y += titleGap;

        if (y + lineHeight > contentBottom) {
            return;
        }

        // Quest name and wave info (truncated to fit panel width)
        String questName = truncateText(data.questName(), panelWidth - UIScaleManager.scale(40));
        UIScaleManager.drawScaledCenteredString(g, safeFont, Objects.requireNonNull(questName), centerX, y, applyAlpha(COLOR_TEXT, alpha));
        y += lineGap;

        String waveInfo = data.endlessMode()
            ? "Reached Wave " + data.finalWave()
            : "Completed " + data.finalWave() + "/" + data.totalWaves() + " waves";
        UIScaleManager.drawScaledCenteredString(g, safeFont, waveInfo, centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
        y += lineGap;

        // Duration
        UIScaleManager.drawScaledCenteredString(g, safeFont, "Time: " + data.getFormattedDuration(), centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
        y += sectionGap;

        if (y + lineHeight > contentBottom) {
            return;
        }

        boolean hasRunInfo = (data.templateId() != null && !data.templateId().isBlank())
            || (data.policyId() != null && !data.policyId().isBlank())
            || (data.instanceId() != null && !data.instanceId().isBlank())
            || (data.arenaId() != null && !data.arenaId().isBlank());

        if (hasRunInfo) {
            UIScaleManager.drawScaledCenteredString(g, safeFont, "-- RUN INFO --", centerX, y, applyAlpha(COLOR_BONUS, alpha));
            y += lineGap;

            if (data.templateId() != null && !data.templateId().isBlank()) {
                String templateLine = "Template: " + data.templateId() + " v" + data.templateVersion();
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    Objects.requireNonNull(truncateText(templateLine, panelWidth - UIScaleManager.scale(60)), "templateLine"),
                    centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
                y += smallGap;
                if (y + lineHeight > contentBottom) {
                    return;
                }
            }
            if (data.policyId() != null && !data.policyId().isBlank()) {
                String policyLine = "Policy: " + data.policyId() + " v" + data.policyVersion();
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    Objects.requireNonNull(truncateText(policyLine, panelWidth - UIScaleManager.scale(60)), "policyLine"),
                    centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
                y += smallGap;
                if (y + lineHeight > contentBottom) {
                    return;
                }
            }

            String difficulty = data.difficultyLabel() != null && !data.difficultyLabel().isBlank()
                ? data.difficultyLabel() : "standard";
            String mode = data.questTypeLabel() != null && !data.questTypeLabel().isBlank()
                ? data.questTypeLabel() : "endurance";
            UIScaleManager.drawScaledCenteredString(g, safeFont, "Difficulty: " + difficulty + " | Mode: " + mode,
                centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
            y += smallGap;
            if (y + lineHeight > contentBottom) {
                return;
            }

            if (data.instanceId() != null && !data.instanceId().isBlank()) {
                String runLine = "Run ID: " + data.instanceId();
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    Objects.requireNonNull(truncateText(runLine, panelWidth - UIScaleManager.scale(60)), "runLine"),
                    centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
                y += smallGap;
            } else if (data.arenaId() != null && !data.arenaId().isBlank()) {
                String arenaLine = "Arena ID: " + data.arenaId();
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    Objects.requireNonNull(truncateText(arenaLine, panelWidth - UIScaleManager.scale(60)), "arenaLine"),
                    centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
                y += smallGap;
            }
            y += UIScaleManager.scale(6);
        } else {
            y += UIScaleManager.scale(8);
        }

        if (y + lineHeight > contentBottom) {
            return;
        }

        // Separator
        int sepColor = applyAlpha(DesignTokens.Stroke.MUTED, alpha);
        g.fill(panelX + inset, y, panelX + panelWidth - inset, y + 1, sepColor);
        y += UIScaleManager.scale(15);

        // === REWARDS SECTION ===
        if (y + lineHeight > contentBottom) {
            return;
        }
        UIScaleManager.drawScaledCenteredString(g, safeFont, "-- REWARDS --", centerX, y, applyAlpha(COLOR_GOLD, alpha));
        y += blockGap;

        // Tokens (animated)
        if (y + lineHeight > contentBottom) {
            return;
        }
        String tokenText = "\u2B50 " + animatedTokens + " Tokens";
        UIScaleManager.drawScaledCenteredString(g, safeFont, tokenText, centerX, y, applyAlpha(COLOR_GOLD, alpha));
        y += lineGap;

        // Show multipliers if significant
        if (data.styleMultiplier() > 1.0f || data.mutatorMultiplier() > 1.0f) {
            if (y + lineHeight > contentBottom) {
                return;
            }
            String multText = Objects.requireNonNull(String.format("(Base: %d | Style: x%.1f | Mutator: x%.1f)",
                data.baseTokens(), data.styleMultiplier(), data.mutatorMultiplier()));
            UIScaleManager.drawScaledCenteredString(g, safeFont, multText, centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
            y += lineGap;
        }

        // Blood Gems
        if (data.bloodGemsEarned() > 0) {
            if (y + lineHeight > contentBottom) {
                return;
            }
            String gemText = "\u2666 " + animatedGems + " Blood Gems";
            UIScaleManager.drawScaledCenteredString(g, safeFont, gemText, centerX, y, applyAlpha(COLOR_GEM, alpha));
            y += lineGap;
        }

        // Prestige points
        if (data.prestigeEarned() > 0) {
            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2726 " + data.prestigeEarned() + " Prestige", centerX, y, applyAlpha(COLOR_GEM, alpha));
            y += lineGap;
        }
        y += UIScaleManager.scale(8);

        // === BONUSES ===
        if (data.noHitBonus() || data.speedBonus() || data.activeMutators() > 0) {
            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledCenteredString(g, safeFont, "-- BONUSES --", centerX, y, applyAlpha(COLOR_BONUS, alpha));
            y += sectionGap;

            if (data.noHitBonus()) {
                if (y + lineHeight > contentBottom) {
                    return;
                }
                UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2714 No Hit Bonus!", centerX, y, applyAlpha(COLOR_SUCCESS, alpha));
                y += smallGap;
            }
            if (data.speedBonus()) {
                if (y + lineHeight > contentBottom) {
                    return;
                }
                UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2714 Speed Bonus!", centerX, y, applyAlpha(COLOR_SUCCESS, alpha));
                y += smallGap;
            }
            if (data.activeMutators() > 0) {
                if (y + lineHeight > contentBottom) {
                    return;
                }
                UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2714 " + data.activeMutators() + " Mutators Active", centerX, y, applyAlpha(COLOR_ORANGE, alpha));
                y += smallGap;
            }
            y += UIScaleManager.scale(6);
        }

        // === STATS ===
        if (y + lineHeight > contentBottom) {
            return;
        }
        UIScaleManager.drawScaledCenteredString(g, safeFont, "-- STATS --", centerX, y, applyAlpha(COLOR_TEXT_DIM, alpha));
        y += sectionGap;

        boolean twoColumn = panelWidth >= UIScaleManager.scale(360);
        int leftX = panelX + UIScaleManager.scale(40);
        int rightX = panelX + panelWidth / 2 + UIScaleManager.scale(10);

        if (twoColumn) {
            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledString(g, safeFont, "Kills: " + data.totalKills(), leftX, y, applyAlpha(COLOR_TEXT, alpha), false);
            UIScaleManager.drawScaledString(g, safeFont, "Max Combo: " + data.maxCombo(), rightX, y, applyAlpha(COLOR_TEXT, alpha), false);
            y += smallGap;

            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledString(g, safeFont, "Damage Dealt: " + String.format("%.0f", data.totalDamageDealt()), leftX, y, applyAlpha(COLOR_TEXT, alpha), false);
            UIScaleManager.drawScaledString(g, safeFont, "Style Rank: " + data.getStyleRank().getDisplayName(), rightX, y, applyAlpha(data.getStyleRank().getColor(), alpha), false);
            y += smallGap;

            if (data.totalDamageTaken() > 0) {
                if (y + lineHeight > contentBottom) {
                    return;
                }
                UIScaleManager.drawScaledString(g, safeFont, "Damage Taken: " + String.format("%.0f", data.totalDamageTaken()), leftX, y, applyAlpha(DesignTokens.Semantic.ERROR, alpha), false);
            }
            if (data.deaths() > 0) {
                if (y + lineHeight > contentBottom) {
                    return;
                }
                UIScaleManager.drawScaledString(g, safeFont, "Deaths: " + data.deaths(), rightX, y, applyAlpha(DesignTokens.Semantic.ERROR, alpha), false);
            }
            y += blockGap;
        } else {
            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledCenteredString(g, safeFont, "Kills: " + data.totalKills(), centerX, y, applyAlpha(COLOR_TEXT, alpha));
            y += smallGap;
            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledCenteredString(g, safeFont, "Max Combo: " + data.maxCombo(), centerX, y, applyAlpha(COLOR_TEXT, alpha));
            y += smallGap;
            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledCenteredString(g, safeFont, "Damage Dealt: " + String.format("%.0f", data.totalDamageDealt()), centerX, y, applyAlpha(COLOR_TEXT, alpha));
            y += smallGap;
            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledCenteredString(g, safeFont, "Style Rank: " + data.getStyleRank().getDisplayName(), centerX, y, applyAlpha(data.getStyleRank().getColor(), alpha));
            y += smallGap;
            if (data.totalDamageTaken() > 0) {
                if (y + lineHeight > contentBottom) {
                    return;
                }
                UIScaleManager.drawScaledCenteredString(g, safeFont, "Damage Taken: " + String.format("%.0f", data.totalDamageTaken()), centerX, y, applyAlpha(DesignTokens.Semantic.ERROR, alpha));
                y += smallGap;
            }
            if (data.deaths() > 0) {
                if (y + lineHeight > contentBottom) {
                    return;
                }
                UIScaleManager.drawScaledCenteredString(g, safeFont, "Deaths: " + data.deaths(), centerX, y, applyAlpha(DesignTokens.Semantic.ERROR, alpha));
                y += smallGap;
            }
            y += UIScaleManager.scale(4);
        }

        // === ACHIEVEMENTS ===
        List<String> achievements = data.achievementNames();
        if (!achievements.isEmpty()) {
            if (y + lineHeight > contentBottom) {
                return;
            }
            UIScaleManager.drawScaledCenteredString(g, safeFont, "-- ACHIEVEMENTS UNLOCKED --", centerX, y, applyAlpha(COLOR_GOLD, alpha));
            y += lineGap;

            int maxY = panelY + panelHeight - UIScaleManager.scale(60);
            int achievementsShown = 0;
            for (String achievement : achievements) {
                if (y > maxY) {
                    // Show indicator for remaining achievements
                    int remaining = achievements.size() - achievementsShown;
                    UIScaleManager.drawScaledCenteredString(g, safeFont, "+" + remaining + " more...", centerX, y, applyAlpha(DesignTokens.Text.MUTED, alpha));
                    break;
                }
                UIScaleManager.drawScaledCenteredString(g, safeFont, "\u2605 " + achievement, centerX, y, applyAlpha(COLOR_ORANGE, alpha));
                y += UIScaleManager.scale(11);
                achievementsShown++;
            }
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a <= 0) a = (int) (255 * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY, int panelHeight) {
        int centerX = width / 2;
        int panelY = (height - panelHeight) / 2;
        int buttonWidth = UIScaleManager.scale(120);
        int buttonHeight = UIScaleManager.scale(DesignTokens.Component.BUTTON_HEIGHT_LG);
        int buttonX = centerX - buttonWidth / 2;
        int buttonY = panelY + panelHeight - UIScaleManager.scale(40);

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
