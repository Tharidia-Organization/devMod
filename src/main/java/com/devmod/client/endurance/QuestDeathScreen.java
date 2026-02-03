package com.devmod.client.endurance;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
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
import com.devmod.endurance.QuestActionPayload;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public class QuestDeathScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestDeathScreen.class);

    // === Colors - Thematic death screen (red theme, using DesignTokens) ===
    private static final int COLOR_BG = EnduranceUiTheme.DeathScreen.BG;           // Dark red-tinted background (death-specific)
    private static final int COLOR_PANEL_BG = EnduranceUiTheme.DeathScreen.PANEL_BG;     // Dark panel (death-specific)
    private static final int COLOR_BORDER = DesignTokens.Semantic.ERROR;
    private static final int COLOR_BORDER_GLOW = DesignTokens.withAlpha(DesignTokens.Semantic.ERROR, DesignTokens.Alpha.A27);
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_DEATH = DesignTokens.Semantic.ERROR;
    private static final int COLOR_SUCCESS = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_WARNING = DesignTokens.Semantic.WARNING;
    private static final int COLOR_GOLD = DesignTokens.Semantic.WARNING;  // Gold/orange for points

    // === Dimensions ===
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 335; // UX Q8: Increased from 320 to fit respawn_location line

    // === Animation ===
    private static final long FADE_IN_DURATION = 500;
    private static final long SKULL_PULSE_PERIOD = 1500;

    // === Data ===
    private final int currentWave;
    private final int totalWaves;
    private final boolean endlessMode;
    private final int pointsEarned;
    private final int deathsThisRun;

    // === State ===
    private long openTime;
    private boolean soundPlayed = false;
    private boolean giveUpRequested = false;  // Track if give up already requested
    @Nullable
    private EditorButton respawnButton;
    @Nullable
    private EditorButton giveUpButton;

    public QuestDeathScreen() {
        super(I18n.translate("devmod.endurance.you_died"));

        // Capture data from ClientQuestCache
        this.currentWave = ClientQuestCache.getCurrentWave();
        this.totalWaves = ClientQuestCache.getTotalWaves();
        this.endlessMode = ClientQuestCache.isEndlessMode();
        this.pointsEarned = ClientQuestCache.getPointsEarned();
        this.deathsThisRun = ClientQuestCache.getDeaths();
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        LOGGER.info("[EnduranceQuest][Client] QuestDeathScreen opened (wave={}/{}, deathsThisRun={})",
            currentWave, totalWaves, deathsThisRun);

        respawnButton = EditorButton.builder("respawn", I18n.translate("devmod.endurance.respawn_cost").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.LARGE)
            .onClick(this::respawnAndContinue)
            .build();

        giveUpButton = EditorButton.builder("give-up", I18n.translate("devmod.endurance.give_up").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.LARGE)
            .onClick(this::giveUpAndCollect)
            .build();
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int maxHeight = height - UIScaleManager.scale(60);
        int contentHeight = calculateContentHeight();
        int sPanelHeight = Math.max(UIScaleManager.scale(PANEL_HEIGHT), contentHeight);
        sPanelHeight = Math.min(sPanelHeight, maxHeight);

        long elapsed = System.currentTimeMillis() - openTime;
        float fadeProgress = Math.min(1.0f, elapsed / (float) FADE_IN_DURATION);

        // Play death sound once
        var mc = minecraft;
        if (!soundPlayed && elapsed > 100 && mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.TOTEM_USE), 0.5f, 0.8f)));
            soundPlayed = true;
        }

        // Darken background
        int bgAlpha = (int) (((COLOR_BG >> 24) & 0xFF) * fadeProgress);
        graphics.fill(0, 0, width, height, (bgAlpha << 24) | (COLOR_BG & DesignTokens.Mask.RGB));

        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - sPanelWidth / 2;
        int panelY = centerY - sPanelHeight / 2;

        // Panel with glow
        renderPanel(graphics, panelX, panelY, sPanelWidth, sPanelHeight, fadeProgress);

        // Content
        if (fadeProgress > 0.3f) {
            float contentAlpha = (fadeProgress - 0.3f) / 0.7f;
            renderContent(graphics, panelX, panelY, sPanelWidth, sPanelHeight, contentAlpha, elapsed);
        }

        // Keybind hints
        if (fadeProgress > 0.8f) {
            float hintAlpha = (fadeProgress - 0.8f) / 0.2f;
            int hintColor = applyAlpha(DesignTokens.Text.MUTED, hintAlpha);
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font),
                Objects.requireNonNull(I18n.translate("devmod.death.keybind_hint").getString()),
                centerX, panelY + sPanelHeight + UIScaleManager.scale(5), hintColor);
        }

        // ESC blocked message (overlays the hint when ESC is pressed)
        if (shouldShowEscMessage()) {
            long escElapsed = System.currentTimeMillis() - lastEscPressTime;
            float escAlpha = 1.0f - (escElapsed / (float) ESC_MESSAGE_DURATION);
            int escColor = applyAlpha(COLOR_WARNING, escAlpha);
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font),
                Objects.requireNonNull(I18n.translate("devmod.endurance.must_choose").getString()),
                centerX, panelY + sPanelHeight + UIScaleManager.scale(18), escColor);
        }

        if (elapsed > FADE_IN_DURATION + 300) {
            renderButtons(graphics, mouseX, mouseY, sPanelHeight);
        }
    }

    private int calculateContentHeight() {
        var safeFont = Objects.requireNonNull(font, "font");
        int lineHeight = UIScaleManager.getScaledLineHeight(safeFont, 10);
        int lineGap = UIScaleManager.scale(4);
        int sectionGap = UIScaleManager.scale(10);
        int topOffset = UIScaleManager.scale(75);
        int bottomButtons = UIScaleManager.scale(80);

        int height = topOffset;
        height += lineHeight + sectionGap; // YOU DIED
        height += lineHeight + lineGap;    // Wave
        height += lineHeight + lineGap;    // Points
        height += lineHeight + sectionGap; // Deaths
        height += sectionGap;              // separator gap
        height += lineHeight + lineGap;    // Choose your fate
        height += lineHeight * 4;          // respawn lines (header + 3 details)
        height += lineHeight + lineGap;    // give up line
        height += bottomButtons;
        return height;
    }

    private void renderPanel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        int bgAlpha = (int) (((COLOR_PANEL_BG >> 24) & 0xFF) * alpha);
        int borderAlpha = (int) (((COLOR_BORDER >> 24) & 0xFF) * alpha);
        int glowAlpha = (int) (((COLOR_BORDER_GLOW >> 24) & 0xFF) * alpha);

        // Glow
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, (glowAlpha << 24) | (COLOR_BORDER_GLOW & DesignTokens.Mask.RGB));

        // Background
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (COLOR_PANEL_BG & DesignTokens.Mask.RGB));

        // Border
        int borderColor = (borderAlpha << 24) | (COLOR_BORDER & DesignTokens.Mask.RGB);
        g.fill(x, y, x + w, y + 2, borderColor);           // Top
        g.fill(x, y + h - 2, x + w, y + h, borderColor);   // Bottom
        g.fill(x, y, x + 2, y + h, borderColor);           // Left
        g.fill(x + w - 2, y, x + w, y + h, borderColor);   // Right
    }

    private void renderContent(GuiGraphics g, int panelX, int panelY, int sPanelWidth, int sPanelHeight, float alpha, long elapsed) {
        var safeFont = Objects.requireNonNull(font);
        int lineHeight = UIScaleManager.getScaledLineHeight(safeFont, 10);
        int lineGap = UIScaleManager.scale(4);
        int sectionGap = UIScaleManager.scale(10);
        int sInset = UIScaleManager.scale(30);
        int buttonBlock = UIScaleManager.scale(70);
        int contentBottom = panelY + sPanelHeight - buttonBlock - UIScaleManager.scale(10);

        // Pulsing skull effect
        float pulse = (float) (Math.sin(elapsed / (double) SKULL_PULSE_PERIOD * Math.PI * 2) * 0.3 + 0.7);
        int skullColor = applyAlpha(COLOR_DEATH, alpha * pulse);

        // Skull icon (Unicode)
        String skull = "☠";
        int skullX = panelX + sPanelWidth / 2 - safeFont.width(skull) * 2;
        g.pose().pushPose();
        g.pose().translate(skullX, panelY + UIScaleManager.scale(20), 0);
        g.pose().scale(4.0f, 4.0f, 1.0f);
        UIScaleManager.drawScaledString(g, safeFont, skull, 0, 0, skullColor, true);
        g.pose().popPose();

        int y = panelY + UIScaleManager.scale(75);

        // "YOU DIED" text
        UIScaleManager.drawScaledCenteredString(g, safeFont,
            Objects.requireNonNull(I18n.translate("devmod.endurance.you_died").getString()),
            panelX + sPanelWidth / 2, y, applyAlpha(COLOR_DEATH, alpha));
        y += lineHeight + sectionGap;

        // Wave info
        String waveText = endlessMode
            ? I18n.translate("devmod.endurance.wave").getString() + " " + currentWave + " (" + I18n.translate("devmod.endurance.endless_mode").getString() + ")"
            : I18n.translate("devmod.endurance.wave").getString() + " " + currentWave + " / " + totalWaves;
        UIScaleManager.drawScaledCenteredString(g, safeFont, waveText,
            panelX + sPanelWidth / 2, y, applyAlpha(COLOR_TEXT_DIM, alpha));
        y += lineHeight + lineGap;

        // Points earned
        String pointsText = I18n.translate("devmod.endurance.points").getString() + ": " + pointsEarned;
        UIScaleManager.drawScaledCenteredString(g, safeFont, pointsText,
            panelX + sPanelWidth / 2, y, applyAlpha(COLOR_GOLD, alpha));
        y += lineHeight + lineGap;

        // Deaths this run
        String deathsText = I18n.translate("devmod.endurance.deaths").getString() + ": " + deathsThisRun;
        UIScaleManager.drawScaledCenteredString(g, safeFont, deathsText,
            panelX + sPanelWidth / 2, y, applyAlpha(COLOR_WARNING, alpha));
        y += lineHeight + sectionGap;

        if (y + lineHeight > contentBottom) {
            return;
        }

        // Separator
        int sepColor = applyAlpha(DesignTokens.Stroke.MUTED, alpha);
        g.fill(panelX + sInset, y, panelX + sPanelWidth - sInset, y + 1, sepColor);
        y += sectionGap;

        // Options explanation
        UIScaleManager.drawScaledCenteredString(g, safeFont,
            Objects.requireNonNull(I18n.ui("choose_fate").getString()),
            panelX + sPanelWidth / 2, y, applyAlpha(COLOR_TEXT, alpha));
        y += lineHeight + lineGap;

        if (y + lineHeight > contentBottom) {
            return;
        }

        // Respawn info
        UIScaleManager.drawScaledString(g, safeFont, "• " + I18n.ui("respawn_info").getString(),
            panelX + sInset, y, applyAlpha(COLOR_SUCCESS, alpha), false);
        y += lineHeight;
        UIScaleManager.drawScaledString(g, safeFont, "  " + I18n.translate("devmod.endurance.respawn_cost").getString(),
            panelX + sInset, y, applyAlpha(COLOR_WARNING, alpha), false);
        y += lineHeight;
        UIScaleManager.drawScaledString(g, safeFont, "  " + I18n.ui("respawn_countdown").getString(),
            panelX + sInset, y, applyAlpha(COLOR_TEXT_DIM, alpha), false);
        y += lineHeight;
        // UX Q2: Show respawn location to reduce uncertainty
        UIScaleManager.drawScaledString(g, safeFont, "  " + I18n.ui("respawn_location").getString(),
            panelX + sInset, y, applyAlpha(COLOR_TEXT_DIM, alpha), false);
        y += lineHeight + lineGap;

        if (y + lineHeight > contentBottom) {
            return;
        }

        // Give up info
        UIScaleManager.drawScaledString(g, safeFont, "• " + I18n.ui("giveup_info").getString(),
            panelX + sInset, y, applyAlpha(COLOR_DEATH, alpha), false);
        y += lineHeight;
        UIScaleManager.drawScaledString(g, safeFont, "  " + I18n.translate("devmod.endurance.points").getString() + ": " + Math.max(0, pointsEarned),
            panelX + sInset, y, applyAlpha(COLOR_GOLD, alpha), false);
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a < 0) a = (int)(255 * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY, int sPanelHeight) {
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - sPanelWidth / 2;
        int panelY = centerY - sPanelHeight / 2;
        int buttonWidth = sPanelWidth - UIScaleManager.scale(DesignTokens.Space.PANEL_PADDING * 5);
        int sButtonHeight = UIScaleManager.scale(DesignTokens.Component.BUTTON_HEIGHT_LG);
        int buttonGap = UIScaleManager.scale(8);
        int giveUpY = panelY + sPanelHeight - sButtonHeight - UIScaleManager.scale(18);
        int respawnY = giveUpY - sButtonHeight - buttonGap;

        if (respawnButton != null) {
            respawnButton.render(graphics, panelX + UIScaleManager.scale(20), respawnY, buttonWidth, sButtonHeight, mouseX, mouseY);
        }
        if (giveUpButton != null) {
            giveUpButton.render(graphics, panelX + UIScaleManager.scale(20), giveUpY, buttonWidth, sButtonHeight, mouseX, mouseY);
        }
    }

    private void respawnAndContinue() {
        LOGGER.info("[EnduranceQuest][Client] QuestDeathScreen respawn clicked");
        ClientQuestCache.startDeathScreenSuppression();
        ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_CONTINUE,
            ClientActionContexts.forClient(ActionOrigin.UI, QuestActionPayload.Action.CONTINUE_AFTER_DEATH));
        var mc = minecraft;
        if (mc != null) {
            var connection = mc.getConnection();
            if (connection != null) {
                connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            }
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP), 1.0f)));
            mc.setScreen(null);

            // Stop suppression after a delay to ensure vanilla DeathScreen doesn't appear
            // during the brief moment between closing this screen and respawn completing
            mc.execute(() -> {
                // Schedule stop after 2 seconds (enough for respawn to complete)
                java.util.concurrent.CompletableFuture.delayedExecutor(
                    2, java.util.concurrent.TimeUnit.SECONDS
                ).execute(ClientQuestCache::stopDeathScreenSuppression);
            });
        }
    }

    private void giveUpAndCollect() {
        LOGGER.info("[EnduranceQuest][Client] QuestDeathScreen give up clicked (count={})", giveUpRequested ? 2 : 1);
        giveUpRequested = true;  // Track that give up was clicked at least once
        // Mark quest end as pending - this prevents RenderEvents from reopening QuestDeathScreen
        // while we wait for the server to send the quest-ended sync
        ClientQuestCache.setPendingQuestEnd(true);
        // NOTE: Don't call forceStopDeathScreenSuppression() here - the player is still dead
        // client-side and we need to keep suppressing until respawn sync arrives.
        // Suppression will naturally stop once player is alive and quest is inactive.

        ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_EXIT,
            ClientActionContexts.forClient(ActionOrigin.UI, QuestActionPayload.Action.GIVE_UP_AFTER_DEATH)
                .withConfirmed(true));
        var mc = minecraft;
        if (mc != null) {
            // DO NOT close the screen here - keep QuestDeathScreen open until server confirms
            // quest end and respawns the player. Closing prematurely causes vanilla DeathScreen
            // to appear because Minecraft sees dead player without a death screen.
            // The server will handle respawn and teleport, which will close this screen naturally.
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 1.0f)));
            LOGGER.info("[EnduranceQuest][Client] Waiting for server to confirm quest end...");
        }
    }

    // Track ESC press for feedback
    private long lastEscPressTime = 0;
    private static final long ESC_MESSAGE_DURATION = 2000;

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // F11 = Respawn
        if (keyCode == GLFW.GLFW_KEY_F11) {
            respawnAndContinue();
            return true;
        }
        // F12 = Give up
        if (keyCode == GLFW.GLFW_KEY_F12) {
            giveUpAndCollect();
            return true;
        }
        // Block ESC from closing - show feedback instead
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            lastEscPressTime = System.currentTimeMillis();
            // Play a subtle "denied" sound
            var mc = minecraft;
            if (mc != null) {
                mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                    Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BASS.value()), 0.5f, 0.5f)));
            }
            return true; // Consume but don't close
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        if (button == 0 && System.currentTimeMillis() - openTime > FADE_IN_DURATION + 300) {
            if (respawnButton != null && respawnButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (giveUpButton != null && giveUpButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        boolean handled = false;
        if (System.currentTimeMillis() - openTime > FADE_IN_DURATION + 300) {
            if (respawnButton != null) handled |= respawnButton.mouseReleased(mouseX, mouseY, button);
            if (giveUpButton != null) handled |= giveUpButton.mouseReleased(mouseX, mouseY, button);
        }
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Check if ESC message should be displayed.
     */
    private boolean shouldShowEscMessage() {
        return System.currentTimeMillis() - lastEscPressTime < ESC_MESSAGE_DURATION;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Force player to make a choice
    }
}
