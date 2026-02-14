package com.devmod.client.ui.screens;

import java.util.ArrayList;
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

/**
 * Post-arena summary screen shown after a run ends (victory or defeat).
 * Displays combat performance stats, style rank, and best moments.
 */
@OnlyIn(Dist.CLIENT)
public class ArenaResultsScreen extends Screen {

    // === Data ===
    private final ArenaRunData data;

    // === Animation ===
    private long openTime;
    private boolean soundPlayed = false;
    private static final long FADE_IN_MS = 350;
    private static final long RANK_REVEAL_MS = 600;

    // === Buttons ===
    @Nullable private EditorButton playAgainButton;
    @Nullable private EditorButton returnButton;

    /**
     * Immutable data record for arena run results.
     */
    public record ArenaRunData(
        boolean victory,
        int wavesCleared,
        int totalWaves,
        long durationMs,
        int totalKills,
        int styleScore,
        String styleRank,
        int bestCombo,
        int perfectDodges,
        float damageTaken,
        float damageDealt,
        int deaths,
        int criticalHits,
        float critRate,
        List<String> bestMoments
    ) {
        public String formattedDuration() {
            long seconds = durationMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        public int rankColor() {
            return switch (styleRank) {
                case "SSS" -> 0xFFD700; // Gold
                case "SS"  -> 0xFF8C00; // Dark orange
                case "S"   -> 0xFF4500; // Red-orange
                case "A"   -> 0xE06B5B; // Red
                case "B"   -> 0x4E9CFF; // Blue
                case "C"   -> 0x45D483; // Green
                default    -> 0xB9C3D4; // Gray
            };
        }
    }

    public ArenaResultsScreen(ArenaRunData data) {
        super(Objects.requireNonNull(Component.literal(data.victory() ? "VICTORY!" : "DEFEATED"), "title"));
        this.data = data;
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();

        playAgainButton = EditorButton.builder("arena-play-again", "Play Again")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::closeScreen)
            .build();

        returnButton = EditorButton.builder("arena-return", "Return to Hub")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::closeScreen)
            .build();
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        long elapsed = System.currentTimeMillis() - openTime;
        float fade = Math.min(1.0f, elapsed / (float) FADE_IN_MS);

        // Play sound
        var mc = minecraft;
        if (!soundPlayed && elapsed > 150 && mc != null) {
            var sound = data.victory()
                ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                : SoundEvents.RESPAWN_ANCHOR_DEPLETE.value();
            mc.getSoundManager().play(Objects.requireNonNull(
                SimpleSoundInstance.forUI(Objects.requireNonNull(sound), data.victory() ? 1.0f : 0.8f)));
            soundPlayed = true;
        }

        // Dark overlay
        int bgAlpha = (int) (0xE0 * fade);
        g.fill(0, 0, width, height, (bgAlpha << 24) | 0x080B0F);

        // Panel dimensions
        int panelW = Math.min(UIScaleManager.scale(400), width - UIScaleManager.scale(40));
        int panelH = Math.min(UIScaleManager.scale(420), height - UIScaleManager.scale(40));
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        // Panel background
        int panelAlpha = (int) (0xDD * fade);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH,
            (panelAlpha << 24) | (DesignTokens.Palette.SURFACE & 0xFFFFFF));

        // Border
        int borderColor = data.victory()
            ? applyAlpha(DesignTokens.Semantic.WARNING, fade)  // Gold for victory
            : applyAlpha(DesignTokens.Semantic.ERROR, fade);   // Red for defeat
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, borderColor);
        g.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, borderColor);
        g.fill(panelX, panelY, panelX + 2, panelY + panelH, borderColor);
        g.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, borderColor);

        if (fade < 0.2f) return;

        var safeFont = Objects.requireNonNull(font);
        float contentFade = (fade - 0.2f) / 0.8f;
        int centerX = width / 2;
        int lineH = UIScaleManager.getScaledLineHeight(safeFont, 10);
        int y = panelY + UIScaleManager.scale(16);

        // Title
        String title = data.victory() ? "VICTORY!" : "DEFEATED";
        int titleColor = data.victory()
            ? applyAlpha(DesignTokens.Semantic.WARNING, contentFade)
            : applyAlpha(DesignTokens.Semantic.ERROR, contentFade);
        UIScaleManager.drawScaledCenteredString(g, safeFont, title, centerX, y, titleColor);
        y += lineH + UIScaleManager.scale(4);

        // Wave info
        String waveText = "Wave " + data.wavesCleared() + "/" + data.totalWaves();
        UIScaleManager.drawScaledCenteredString(g, safeFont, waveText, centerX, y,
            applyAlpha(DesignTokens.Text.SECONDARY, contentFade));
        y += lineH + UIScaleManager.scale(8);

        // Style rank (large, centered)
        boolean showRank = elapsed > RANK_REVEAL_MS;
        if (showRank) {
            float rankFade = Math.min(1.0f, (elapsed - RANK_REVEAL_MS) / 300f);
            int rankColor = applyAlpha(data.rankColor(), rankFade * contentFade);
            g.pose().pushPose();
            float scale = 3.0f;
            int rankTextW = (int) (safeFont.width(data.styleRank()) * scale);
            g.pose().translate(centerX - rankTextW / 2f, y, 0);
            g.pose().scale(scale, scale, 1.0f);
            UIScaleManager.drawScaledString(g, safeFont, data.styleRank(), 0, 0, rankColor, true);
            g.pose().popPose();
            y += (int) (lineH * scale) + UIScaleManager.scale(2);

            UIScaleManager.drawScaledCenteredString(g, safeFont,
                "Style Score: " + String.format("%,d", data.styleScore()), centerX, y,
                applyAlpha(DesignTokens.Text.SECONDARY, contentFade));
            y += lineH + UIScaleManager.scale(8);
        } else {
            y += UIScaleManager.scale(40);
        }

        // Separator
        int sepColor = applyAlpha(DesignTokens.Stroke.MUTED, contentFade);
        int inset = UIScaleManager.scale(24);
        g.fill(panelX + inset, y, panelX + panelW - inset, y + 1, sepColor);
        y += UIScaleManager.scale(10);

        // Stats table
        int leftX = panelX + UIScaleManager.scale(28);
        int rightX = panelX + panelW / 2 + UIScaleManager.scale(8);
        int statGap = lineH + UIScaleManager.scale(1);
        int bottomLimit = panelY + panelH - UIScaleManager.scale(50);

        // Left column
        if (y < bottomLimit) {
            drawStat(g, safeFont, "Time", data.formattedDuration(), leftX, y, contentFade);
            drawStat(g, safeFont, "Total Kills", String.valueOf(data.totalKills()), rightX, y, contentFade);
            y += statGap;
        }
        if (y < bottomLimit) {
            drawStat(g, safeFont, "Best Combo", data.bestCombo() + " hits", leftX, y, contentFade);
            drawStat(g, safeFont, "Crit Rate", String.format("%.0f%%", data.critRate() * 100), rightX, y, contentFade);
            y += statGap;
        }
        if (y < bottomLimit) {
            drawStat(g, safeFont, "Dmg Dealt", String.format("%.0f", data.damageDealt()), leftX, y, contentFade);
            drawStat(g, safeFont, "Dmg Taken", String.format("%.0f", data.damageTaken()), rightX, y,
                contentFade, data.damageTaken() > 0 ? DesignTokens.Semantic.ERROR : DesignTokens.Text.PRIMARY);
            y += statGap;
        }
        if (y < bottomLimit && data.perfectDodges() > 0) {
            drawStat(g, safeFont, "Perfect Dodges", String.valueOf(data.perfectDodges()), leftX, y, contentFade);
            if (data.deaths() > 0) {
                drawStat(g, safeFont, "Deaths", String.valueOf(data.deaths()), rightX, y,
                    contentFade, DesignTokens.Semantic.ERROR);
            }
            y += statGap;
        }

        y += UIScaleManager.scale(6);

        // Best moments
        List<String> moments = data.bestMoments();
        if (!moments.isEmpty() && y < bottomLimit) {
            g.fill(panelX + inset, y, panelX + panelW - inset, y + 1, sepColor);
            y += UIScaleManager.scale(8);

            UIScaleManager.drawScaledCenteredString(g, safeFont, "Best Moments", centerX, y,
                applyAlpha(DesignTokens.Accent.PRIMARY, contentFade));
            y += lineH + UIScaleManager.scale(2);

            for (int i = 0; i < moments.size() && y < bottomLimit; i++) {
                UIScaleManager.drawScaledCenteredString(g, safeFont,
                    "\u2605 " + moments.get(i), centerX, y,
                    applyAlpha(DesignTokens.Semantic.WARNING, contentFade));
                y += lineH;
            }
        }

        // Buttons
        renderButtons(g, mouseX, mouseY, panelX, panelY, panelW, panelH);
    }

    private void drawStat(GuiGraphics g, net.minecraft.client.gui.Font f, String label, String value,
                           int x, int y, float alpha) {
        drawStat(g, f, label, value, x, y, alpha, DesignTokens.Text.PRIMARY);
    }

    private void drawStat(GuiGraphics g, net.minecraft.client.gui.Font f, String label, String value,
                           int x, int y, float alpha, int valueColor) {
        UIScaleManager.drawScaledString(g, f, label + ": ", x, y,
            applyAlpha(DesignTokens.Text.SECONDARY, alpha), false);
        int labelW = UIScaleManager.getScaledStringWidth(f, label + ": ");
        UIScaleManager.drawScaledString(g, f, value, x + labelW, y,
            applyAlpha(valueColor, alpha), false);
    }

    private void renderButtons(GuiGraphics g, int mouseX, int mouseY,
                                int panelX, int panelY, int panelW, int panelH) {
        int btnW = UIScaleManager.scale(100);
        int btnH = UIScaleManager.scale(DesignTokens.Component.BUTTON_HEIGHT_MD);
        int btnY = panelY + panelH - UIScaleManager.scale(36);
        int gap = UIScaleManager.scale(8);
        int totalW = btnW * 2 + gap;
        int startX = panelX + (panelW - totalW) / 2;

        if (playAgainButton != null) {
            playAgainButton.render(g, startX, btnY, btnW, btnH, mouseX, mouseY);
        }
        if (returnButton != null) {
            returnButton.render(g, startX + btnW + gap, btnY, btnW, btnH, mouseX, mouseY);
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a <= 0) a = (int) (255 * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    private void closeScreen() {
        var mc = minecraft;
        if (mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(
                SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 1.0f)));
            mc.setScreen(null);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        if (button == 0) {
            if (playAgainButton != null && playAgainButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (returnButton != null && returnButton.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        if (playAgainButton != null && playAgainButton.mouseReleased(mouseX, mouseY, button)) return true;
        if (returnButton != null && returnButton.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // === Factory helpers ===

    /**
     * Create from a QuestCompletionPayload (maps endurance data to arena results view).
     */
    public static ArenaResultsScreen fromCompletion(
            boolean victory, int wavesCleared, int totalWaves, long durationMs,
            int kills, int styleScore, String styleRank, int maxCombo,
            float damageDealt, float damageTaken, int deaths,
            int critHits, float critRate, int perfectDodges, List<String> moments) {
        ArenaRunData runData = new ArenaRunData(
            victory, wavesCleared, totalWaves, durationMs,
            kills, styleScore, styleRank, maxCombo,
            perfectDodges, damageTaken, damageDealt, deaths, critHits, critRate,
            moments != null ? moments : List.of()
        );
        return new ArenaResultsScreen(runData);
    }
}
