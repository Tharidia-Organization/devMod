package com.devmod.client.overlay;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.common.base.Splitter;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.endurance.EnduranceUiCache;
import com.devmod.client.endurance.PerkSelectionScreen;
import com.devmod.client.endurance.WaveCheckpointScreen;
import com.devmod.client.endurance.WaveDirectiveScreen;
import com.devmod.client.telemetry.ClientLVCCache;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.FlowStateTracker;
import com.devmod.endurance.MomentumTracker;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.WaveObjectiveState;
import com.devmod.telemetry.network.LVCSyncPayload;
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class EnduranceQuestOverlay {
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
    private static final Splitter COLON_SPLITTER = Splitter.on(':').trimResults();

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "endurance_quest_hud");

    // === UI Colors (delegating to OverlayTheme) ===
    private static final int PANEL_BG = DesignTokens.Bg.LEVEL_1;
    private static final int PANEL_BORDER = OverlayTheme.Border.ENDURANCE;
    private static final int PANEL_BORDER_GLOW = OverlayTheme.Border.glow(OverlayTheme.Border.ENDURANCE);

    private static final int TEXT_TITLE = OverlayTheme.Endurance.LIGHT;
    private static final int TEXT_NORMAL = OverlayTheme.Text.PRIMARY;
    private static final int TEXT_ACCENT = OverlayTheme.Endurance.PRIMARY;
    private static final int TEXT_DIM = OverlayTheme.Text.MUTED;
    private static final int TEXT_DANGER = OverlayTheme.Text.DANGER;
    private static final int TEXT_SUCCESS = OverlayTheme.Border.SUCCESS;

    private static final int PROGRESS_BG = OverlayTheme.Progress.BG;
    private static final int PROGRESS_FILL = OverlayTheme.Progress.FILL_ORANGE;

    // === Momentum State Colors (from OverlayTheme.Momentum) ===
    private static final int COLOR_MOMENTUM_STAGNANT = OverlayTheme.Momentum.STAGNANT;
    private static final int COLOR_MOMENTUM_HEATED = OverlayTheme.Momentum.HEATED;
    private static final int COLOR_MOMENTUM_OVERDRIVE = OverlayTheme.Momentum.OVERDRIVE;
    private static final int COLOR_MOMENTUM_NORMAL = OverlayTheme.Momentum.NORMAL;

    // === Affix Colors (from OverlayTheme.Affix) ===
    private static final int COLOR_AFFIX_SWIFT = OverlayTheme.Affix.SWIFT;
    private static final int COLOR_AFFIX_EMPOWERED = OverlayTheme.Affix.EMPOWERED;
    private static final int COLOR_AFFIX_FORTIFIED = OverlayTheme.Affix.FORTIFIED;
    private static final int COLOR_AFFIX_ARMORED = OverlayTheme.Affix.ARMORED;
    private static final int COLOR_AFFIX_BLAZING = OverlayTheme.Affix.BLAZING;
    private static final int COLOR_AFFIX_PHANTOM = OverlayTheme.Affix.PHANTOM;
    private static final int COLOR_AFFIX_REGENERATING = OverlayTheme.Affix.REGENERATING;
    private static final int COLOR_AFFIX_HORDE = OverlayTheme.Affix.HORDE;

    // === Misc Colors (from OverlayTheme) ===
    private static final int COLOR_BORDER_DIM = OverlayTheme.Border.MUTED;
    private static final int COLOR_BOSS_ALERT = OverlayTheme.Endurance.BOSS_ALERT;
    private static final int COLOR_SURVIVE_GREEN = OverlayTheme.Border.SUCCESS;

    // === Dimensions ===
    private static final int PANEL_PADDING = OverlayTheme.Dimension.PADDING;
    private static final int LINE_HEIGHT = OverlayTheme.Dimension.LINE_HEIGHT;
    private static final int PANEL_WIDTH = 200;
    private static final int MARGIN_LEFT = 10;
    private static final int MARGIN_TOP = 100; // Sotto FpsTracker (5,5)-(145,90)

    private static final int PROGRESS_BAR_HEIGHT = 6;

    // === Toggle (disabled by default - enable via radial menu) ===
    private static boolean enabled = false;
    private static boolean showDetails = true; // Show detailed stats

    // === Animation ===
    private static long waveCompleteAnimTime = 0;
    private static int lastWave = 0;
    private static final long WAVE_ANIM_DURATION = 2000;

    // === Wave Counter Banner ===
    private static final int WAVE_BANNER_HEIGHT = 32;
    private static final int WAVE_BANNER_BG = OverlayTheme.Endurance.BG;
    private static final int WAVE_NUMBER_COLOR = PANEL_BORDER;

    // === State Watcher for Checkpoint Screen ===
    private static boolean checkpointScreenShown = false;
    private static long waveCompleteDetectedTime = 0;
    private static final long CHECKPOINT_SCREEN_DELAY = 500; // ms before auto-opening

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(EnduranceQuestOverlay.class);

    // === Boss Alert State ===
    private static long bossAlertStartTime = 0;
    private static long bossAlertDuration = 0;
    private static String bossAlertType = "";
    private static long lastSoundTick = 0;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.BOSS_OVERLAY),
            Objects.requireNonNull(LAYER_ID),
            EnduranceQuestOverlay::render
        );
    }

    /**
     * Main rendering method.
     */
    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Use client-side cache instead of server-side manager
        if (!ClientQuestCache.hasActiveQuest()) return;

        QuestSyncPayload data = ClientQuestCache.getData();
        if (data == null) return;

        // Don't show if quest is not active
        EnduranceQuestState state = data.getState();
        if (state != EnduranceQuestState.IN_PROGRESS && state != EnduranceQuestState.WAVE_COMPLETE) return;

        // === State Watcher: Open checkpoint screen on wave complete ===
        checkForStateTransition(mc, state);

        Font font = mc.font;

        // Scale dimensions for positioning
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sMarginLeft = UIScaleManager.scale(MARGIN_LEFT);
        int sMarginTop = UIScaleManager.scale(MARGIN_TOP);

        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();

        boolean allowDetails = showDetails;
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sProgressBarHeight = UIScaleManager.scale(PROGRESS_BAR_HEIGHT);
        int panelHeight = calculatePanelHeight(data, panelWidth, allowDetails, sLineHeight, sPanelPadding, sProgressBarHeight);
        if (panelHeight > UIScaleManager.getSafeHeight() && allowDetails) {
            allowDetails = false;
            panelHeight = calculatePanelHeight(data, panelWidth, false, sLineHeight, sPanelPadding, sProgressBarHeight);
        }

        int panelX = Math.max(safeLeft, Math.min(sMarginLeft, safeRight - panelWidth));
        int panelY = Math.max(safeTop, Math.min(sMarginTop, safeBottom - panelHeight));

        // Render panel
        renderPanel(graphics, font, panelX, panelY, panelWidth, panelHeight, data, allowDetails);

        // Check for wave completion animation
        if (data.currentWave() != lastWave) {
            if (lastWave > 0 && data.currentWave() > lastWave) {
                waveCompleteAnimTime = System.currentTimeMillis();
            }
            lastWave = data.currentWave();
        }

        // Render wave complete animation
        renderWaveCompleteAnimation(graphics, font, panelX, panelY + panelHeight + UIScaleManager.scale(5), panelWidth);

        // Render prominent wave banner at top center
        renderWaveBanner(graphics, font, data);

        // Render boss alert if active
        renderBossAlert(graphics, font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /**
     * Renders the main panel.
     */
    private static void renderPanel(GuiGraphics g, Font font, int x, int y, int width, int height,
                                     QuestSyncPayload data, boolean allowDetails) {
        // Background with border
        renderPanelBackground(g, x, y, width, height);

        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);
        int sProgressBarHeight = UIScaleManager.scale(PROGRESS_BAR_HEIGHT);
        int sGap2 = UIScaleManager.scale(2);
        int sGap4 = UIScaleManager.scale(4);
        int sGap6 = UIScaleManager.scale(6);
        int sGap8 = UIScaleManager.scale(8);
        int textX = x + sPanelPadding;
        int textY = y + sPanelPadding;
        int contentWidth = Math.max(0, width - sPanelPadding * 2);

        // === Header: Quest Name (truncated if needed) ===
        String questName = data.questName();
        if (questName == null || questName.isEmpty()) {
            questName = "Endurance Quest";
        }
        // Remove problematic characters and truncate
        questName = questName.replaceAll("[^\\w\\s-]", "").trim();
        var safeFont = Objects.requireNonNull(font);
        String pointsText = data.pointsEarned() + " pts";
        int pointsWidth = UIScaleManager.getScaledStringWidth(safeFont, pointsText);
        int maxNameWidth = Math.max(0, contentWidth - pointsWidth - sGap6);
        questName = truncateToWidth(safeFont, questName, maxNameWidth);
        UIScaleManager.drawScaledString(g, font, "\u2694 " + questName, textX, textY, TEXT_TITLE, true); // ⚔ icon

        // Points in header on the right
        UIScaleManager.drawScaledString(g, font, pointsText, x + width - sPanelPadding - pointsWidth, textY, TEXT_ACCENT);
        textY += sLineHeight + sGap4;

        // === Template/Policy/Difficulty ===
        String templatePolicy = buildTemplatePolicyLine(data);
        if (!templatePolicy.isBlank()) {
            templatePolicy = truncateToWidth(safeFont, templatePolicy, contentWidth);
            UIScaleManager.drawScaledString(g, font, templatePolicy, textX, textY, TEXT_DIM);
            textY += sLineHeight;
        }

        String difficultyLine = buildDifficultyLine(data);
        if (!difficultyLine.isBlank()) {
            difficultyLine = truncateToWidth(safeFont, difficultyLine, contentWidth);
            UIScaleManager.drawScaledString(g, font, difficultyLine, textX, textY, TEXT_DIM);
            textY += sLineHeight;
        }

        // === Objective & Status ===
        String objectiveTitle = data.objectiveTitle();
        if (objectiveTitle == null || objectiveTitle.isBlank()) {
            objectiveTitle = "Survive waves";
        }
        String objectiveLine = truncateToWidth(safeFont, "Objective: " + objectiveTitle, contentWidth);
        UIScaleManager.drawScaledString(g, font, objectiveLine, textX, textY, TEXT_DIM);
        textY += sLineHeight;
        String objectiveDescription = data.objectiveDescription();
        if (objectiveDescription != null && !objectiveDescription.isBlank()) {
            String descLine = truncateToWidth(safeFont, objectiveDescription, contentWidth);
            UIScaleManager.drawScaledString(g, font, descLine, textX, textY, TEXT_DIM);
            textY += sLineHeight;
        }
        String statusLabel = truncateToWidth(safeFont, "Status: " + getStatusLabel(data), contentWidth);
        UIScaleManager.drawScaledString(g, font, statusLabel, textX, textY, getStatusColor(data));
        textY += sLineHeight + sGap2;

        // === Wave Progress (on separate row) ===
        String waveText;
        if (data.endlessMode()) {
            waveText = "Wave " + data.currentWave() + " (Endless)";
        } else {
            waveText = "Wave " + data.currentWave() + " / " + data.totalWaves();
        }
        String killsText = data.mobsKilled() + " kills";
        killsText = truncateToWidth(safeFont, killsText, contentWidth);
        int killsWidth = UIScaleManager.getScaledStringWidth(safeFont, killsText);
        int maxWaveWidth = Math.max(0, contentWidth - killsWidth - sGap6);
        waveText = truncateToWidth(safeFont, waveText, maxWaveWidth);
        UIScaleManager.drawScaledString(g, font, waveText, textX, textY, TEXT_NORMAL);

        // Kills on the right of the same row
        UIScaleManager.drawScaledString(g, font, killsText, x + width - sPanelPadding - killsWidth, textY, TEXT_SUCCESS);
        textY += sLineHeight + sGap4;

        // === Progress Bar (Objective progress) ===
        WaveObjectiveState.Type objectiveType = data.getObjectiveType();
        int progressValue = objectiveType == WaveObjectiveState.Type.KILL_ALL
            ? data.mobsKilledInWave()
            : data.objectiveProgress();
        int targetValue = objectiveType == WaveObjectiveState.Type.KILL_ALL
            ? data.totalMobsInWave()
            : data.objectiveTarget();
        float progress = targetValue > 0 ? (float) progressValue / targetValue : 0;

        renderProgressBar(g, textX, textY, contentWidth, sProgressBarHeight, progress);

        // Progress text above the bar
        String progressLabel = buildObjectiveProgressLabel(objectiveType, progressValue, targetValue);
        progressLabel = truncateToWidth(safeFont, Objects.requireNonNull(progressLabel, "progressLabel"), contentWidth);
        int mobTextWidth = UIScaleManager.getScaledStringWidth(safeFont, progressLabel);
        UIScaleManager.drawScaledString(g, font, progressLabel, textX + (contentWidth - mobTextWidth) / 2,
                     textY - UIScaleManager.scale(1), TEXT_DIM);
        textY += sProgressBarHeight + sGap6;

        // === Combo & Style Rank ===
        if (data.currentCombo() > 0 || data.styleScore() > 0) {
            ComboSystem.StyleRank rank = data.getStyleRank();
            String comboText = "Combo: " + data.currentCombo() + " | " + rank.getDisplayName();
            comboText = truncateToWidth(safeFont, comboText, contentWidth);
            UIScaleManager.drawScaledString(g, font, comboText, textX, textY, rank.getColor());
            textY += sLineHeight;

            // === Flow State Indicator ===
            FlowStateTracker.FlowState flowState = data.getFlowState();
            if (flowState != FlowStateTracker.FlowState.NEUTRAL) {
                // Show flow state with color
                String flowText = flowState.getDisplayName();
                if (!flowText.isEmpty()) {
                    flowText = truncateToWidth(safeFont, flowText, contentWidth);
                    UIScaleManager.drawScaledString(g, font, flowText, textX, textY, flowState.getColor());
                    textY += sLineHeight;
                }
            }

            // Show virtuoso progress bar if making progress
            if (data.virtuosoProgress() > 0 && data.virtuosoProgress() < 1.0f) {
                String virtuosoLabel = "Virtuoso: ";
                UIScaleManager.drawScaledString(g, font, virtuosoLabel, textX, textY, TEXT_DIM);
                int barX = textX + UIScaleManager.getScaledStringWidth(safeFont, virtuosoLabel);
                int maxBarWidth = Math.max(0, contentWidth - UIScaleManager.getScaledStringWidth(safeFont, virtuosoLabel) - sGap4);
                int barWidth = Math.min(UIScaleManager.scale(60), maxBarWidth);
                int barHeight = UIScaleManager.scale(4);
                // Background
                g.fill(barX, textY + 2, barX + barWidth, textY + 2 + barHeight, PROGRESS_BG);
                // Fill (golden for virtuoso progress)
                int fillWidth = (int) (barWidth * data.virtuosoProgress());
                g.fill(barX, textY + 2, barX + fillWidth, textY + 2 + barHeight, COLOR_MOMENTUM_HEATED);
                textY += sLineHeight;
            }

            // Show stale risk warning
            if (data.staleRisk() >= 0.66f && flowState != FlowStateTracker.FlowState.STALE) {
                String warning = "⚠ Vary attacks!";
                warning = truncateToWidth(safeFont, warning, contentWidth);
                UIScaleManager.drawScaledString(g, font, warning, textX, textY, COLOR_MOMENTUM_HEATED);
                textY += sLineHeight;
            }

            textY += sGap2;
        }

        // === Momentum Bar ===
        MomentumTracker.MomentumState momentumState = data.getMomentumState();
        int momentumPercent = data.momentumPercent();

        // Draw momentum label
        String momentumLabel = "Momentum: ";
        UIScaleManager.drawScaledString(g, font, momentumLabel, textX, textY, TEXT_DIM);
        int barX = textX + UIScaleManager.getScaledStringWidth(safeFont, momentumLabel);
        int barHeight = UIScaleManager.scale(6);
        String percentText = momentumPercent + "%";
        int percentWidth = UIScaleManager.getScaledStringWidth(safeFont, percentText);
        int maxBarWidth = Math.max(0, contentWidth - UIScaleManager.getScaledStringWidth(safeFont, momentumLabel) - percentWidth - sGap8);
        int barWidth = Math.min(UIScaleManager.scale(80), maxBarWidth);

        // Background
        g.fill(barX, textY + 1, barX + barWidth, textY + 1 + barHeight, PROGRESS_BG);

        // Fill color based on state
        int fillColor = switch (momentumState) {
            case STAGNANT -> COLOR_MOMENTUM_STAGNANT;
            case HEATED -> COLOR_MOMENTUM_HEATED;
            case OVERDRIVE -> COLOR_MOMENTUM_OVERDRIVE;
            default -> COLOR_MOMENTUM_NORMAL;
        };

        int fillWidth = (int) (barWidth * (momentumPercent / 100f));
        g.fill(barX, textY + 1, barX + fillWidth, textY + 1 + barHeight, fillColor);

        // Percentage text
        int percentX = barX + barWidth + sGap4;
        UIScaleManager.drawScaledString(g, font, percentText, percentX, textY, TEXT_NORMAL);

        // State indicator
        if (momentumState != MomentumTracker.MomentumState.BUILDING) {
            String stateText = momentumState.getDisplayName();
            if (!stateText.isEmpty()) {
                int stateX = percentX + percentWidth + sGap4;
                int stateMaxWidth = Math.max(0, textX + contentWidth - stateX);
                stateText = truncateToWidth(safeFont, stateText, stateMaxWidth);
                UIScaleManager.drawScaledString(g, font, stateText, stateX, textY, momentumState.getColor());
            }
        }

        // Overdrive timer
        if (data.isOverdrive() && data.overdriveRemaining() > 0) {
            textY += sLineHeight;
            long seconds = data.overdriveRemaining() / 1000;
            String timerText = "⚡ OVERDRIVE: " + seconds + "s";
            timerText = truncateToWidth(safeFont, timerText, contentWidth);
            UIScaleManager.drawScaledString(g, font, timerText, textX, textY, COLOR_MOMENTUM_OVERDRIVE);
        }

        textY += sLineHeight + sGap2;

        // === Wave Modifiers ===
        List<String> modifiers = data.waveModifiers();
        if (!modifiers.isEmpty()) {
            String modLabel = "Modifiers: ";
            UIScaleManager.drawScaledString(g, font, modLabel, textX, textY, TEXT_DIM);

            int modX = textX + UIScaleManager.getScaledStringWidth(safeFont, modLabel);
            int maxLineX = textX + contentWidth;
            for (String modName : modifiers) {
                int modColor = getModifierColor(modName);
                String icon = getModifierIcon(modName);
                String displayText = truncateToWidth(safeFont, icon + " " + modName, contentWidth);
                int displayWidth = UIScaleManager.getScaledStringWidth(safeFont, displayText);
                if (modX + displayWidth > maxLineX && modX > textX) {
                    textY += sLineHeight;
                    modX = textX;
                }
                UIScaleManager.drawScaledString(g, font, displayText, modX, textY, modColor);
                modX += displayWidth + sGap6;
            }
            textY += sLineHeight + sGap2;
        }

        // === Detailed Stats ===
        if (allowDetails) {
            // Separator line
            g.fill(x + UIScaleManager.scale(4), textY, x + width - UIScaleManager.scale(4), textY + 1,
                OverlayTheme.withAlpha(PANEL_BORDER, OverlayTheme.Alpha.GHOST));
            textY += sGap4;

            UIScaleManager.drawScaledString(g, font, "Run Stats", textX, textY, TEXT_ACCENT);
            textY += sLineHeight;

            // Contract multiplier (if active)
            if (ContractHudOverlay.INSTANCE.hasActiveContracts()) {
                float contractMult = ContractHudOverlay.INSTANCE.getTotalMultiplier();
                String contractText = String.format("\u2694 Contracts: %.1fx", contractMult);
                contractText = truncateToWidth(safeFont, contractText, contentWidth);
                UIScaleManager.drawScaledString(g, font, contractText, textX, textY, OverlayTheme.Contract.MULTIPLIER_HIGH);
                textY += sLineHeight;
            }

            // Session timer + Kills on same line
            long duration = data.sessionDurationMs();
            String timeText = formatDuration(duration);
            String timeLine = truncateToWidth(safeFont, "Time: " + timeText, contentWidth);
            UIScaleManager.drawScaledString(g, font, timeLine, textX, textY, TEXT_DIM);
            String killText = truncateToWidth(safeFont, data.mobsKilled() + " kills", contentWidth);
            int killWidth = UIScaleManager.getScaledStringWidth(safeFont, killText);
            UIScaleManager.drawScaledString(g, font, killText, x + width - sPanelPadding - killWidth, textY, TEXT_SUCCESS);
            textY += sLineHeight;

            // Damage dealt/taken + Deaths (if any)
            String dmgText = truncateToWidth(safeFont, "DMG: " + data.damageDealt() + "/" + data.damageTaken(), contentWidth);
            UIScaleManager.drawScaledString(g, font, dmgText, textX, textY, TEXT_DIM);
            if (data.deaths() > 0) {
                String deathText = truncateToWidth(safeFont, "\u2620 " + data.deaths(), contentWidth);
                int deathWidth = UIScaleManager.getScaledStringWidth(safeFont, deathText);
                UIScaleManager.drawScaledString(g, font, deathText, x + width - sPanelPadding - deathWidth, textY, TEXT_DANGER);
            }
            textY += sLineHeight;

            // Get LVC data for enhanced metrics
            ClientLVCCache lvcCache = ClientLVCCache.INSTANCE;
            LVCSyncPayload lvcPayload = lvcCache.getCachedPayload();
            boolean hasLvc = lvcCache.hasData() && !lvcCache.isStale() && lvcPayload != null;

            // DPS metrics with Peak DPS
            double seconds = Math.max(1, data.sessionDurationMs() / 1000.0);
            double kps = data.mobsKilled() / seconds;
            double dtps = data.damageTaken() / seconds;
            double dps = data.damageDealt() / seconds;
            double peakDps = 0;
            if (hasLvc && lvcPayload != null) {
                dps = lvcCache.getCurrentDPS();
                peakDps = lvcPayload.peakDPS();
            }

            String dpsMetrics;
            if (hasLvc && peakDps > 0) {
                dpsMetrics = String.format("DPS %.1f (peak %.1f) | DTPS %.1f", dps, peakDps, dtps);
            } else {
                dpsMetrics = String.format("KPS %.2f | DPS %.1f | DTPS %.1f", kps, dps, dtps);
            }
            dpsMetrics = truncateToWidth(safeFont, dpsMetrics, contentWidth);
            UIScaleManager.drawScaledString(g, font, dpsMetrics, textX, textY, TEXT_DIM);
            textY += sLineHeight;

            // LVC-specific stats (accuracy, crits, abilities, defense)
            if (hasLvc && lvcPayload != null) {
                LVCSyncPayload payload = lvcPayload;
                // Accuracy & Crits
                double accuracy = lvcCache.getAccuracy() * 100;
                int critCount = payload.criticalHitCount();
                String accCritText = String.format("Accuracy %.0f%% | \u2726 %d crits", accuracy, critCount); // Sparkle icon
                accCritText = truncateToWidth(safeFont, accCritText, contentWidth);
                UIScaleManager.drawScaledString(g, font, accCritText, textX, textY, TEXT_DIM);
                textY += sLineHeight;

                // Ability usage (dash, dodge, perfect dodge) - only if any used
                int dashCount = payload.dashCount();
                int dodgeCount = payload.dodgeCount();
                int perfectCount = payload.perfectDodgeCount();
                if (dashCount > 0 || dodgeCount > 0 || perfectCount > 0) {
                    StringBuilder abilityText = new StringBuilder("Abilities: ");
                    if (dashCount > 0) {
                        abilityText.append("\u26A1").append(dashCount); // Dash icon
                    }
                    if (dodgeCount > 0) {
                        if (dashCount > 0) abilityText.append(" ");
                        abilityText.append("\u21BA").append(dodgeCount); // Dodge icon
                    }
                    if (perfectCount > 0) {
                        if (dashCount > 0 || dodgeCount > 0) abilityText.append(" ");
                        abilityText.append("\u2605").append(perfectCount); // Perfect icon
                    }
                    String abilities = truncateToWidth(safeFont, abilityText.toString(), contentWidth);
                    UIScaleManager.drawScaledString(g, font, abilities, textX, textY, COLOR_AFFIX_SWIFT);
                    textY += sLineHeight;
                }

                // Defensive stats (damage negated, stamina spent) - only if meaningful
                double damageNegated = payload.totalDamageNegated();
                double staminaSpent = payload.totalStaminaSpent();
                if (damageNegated > 0 || staminaSpent > 10) {
                    String defenseText = String.format("\u2764 %.0f blocked | \u269B %.0f stamina",
                        damageNegated, staminaSpent); // Blocked and stamina icons
                    defenseText = truncateToWidth(safeFont, defenseText, contentWidth);
                    UIScaleManager.drawScaledString(g, font, defenseText, textX, textY, COLOR_SURVIVE_GREEN);
                    textY += sLineHeight;
                }

                // Top weapons - compact display
                String topWeapons = payload.topWeapons();
                if (topWeapons != null && !topWeapons.isEmpty()) {
                    String weaponsDisplay = formatTopWeapons(topWeapons);
                    if (!weaponsDisplay.isEmpty()) {
                        String weaponsLine = truncateToWidth(safeFont, "\u2694 " + weaponsDisplay, contentWidth);
                        UIScaleManager.drawScaledString(g, font, weaponsLine, textX, textY, TEXT_TITLE);
                        textY += sLineHeight;
                    }
                }
            }
        }

        // === Keybind Hint ===
        textY += sGap2;
        String hint = truncateToWidth(safeFont, "F11: Continue | F12: Exit", contentWidth);
        UIScaleManager.drawScaledString(g, font, hint, textX, textY, COLOR_BORDER_DIM);
    }

    /**
     * Renders panel background with border.
     */
    private static void renderPanelBackground(GuiGraphics g, int x, int y, int width, int height) {
        // Outer glow
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_BORDER_GLOW);

        // Main background
        g.fill(x, y, x + width, y + height, PANEL_BG);

        // Border
        g.fill(x, y, x + width, y + 1, PANEL_BORDER);                    // Top
        g.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);  // Bottom
        g.fill(x, y, x + 1, y + height, PANEL_BORDER);                   // Left
        g.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);   // Right
    }

    /**
     * Renders the progress bar.
     */
    private static void renderProgressBar(GuiGraphics g, int x, int y, int width, int height, float progress) {
        // Background
        g.fill(x, y, x + width, y + height, PROGRESS_BG);

        // Fill
        int fillWidth = (int) (width * Math.min(1.0f, progress));
        g.fill(x, y, x + fillWidth, y + height, PROGRESS_FILL);

        // Border
        g.fill(x, y, x + width, y + 1, COLOR_BORDER_DIM);
        g.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER_DIM);
    }

    /**
     * Renders the prominent wave banner at the top-center of the screen.
     * Shows a large and visible wave number, or a countdown timer for time-based objectives.
     */
    private static void renderWaveBanner(GuiGraphics g, Font font, QuestSyncPayload data) {
        WaveObjectiveState.Type objectiveType = data.getObjectiveType();
        boolean isTimedObjective = objectiveType == WaveObjectiveState.Type.SURVIVE_TIME ||
                                   objectiveType == WaveObjectiveState.Type.HOLD_ZONE;

        // Banner positioned at top-center - wider for timed objectives
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeTop = UIScaleManager.getSafeTop();
        int safeWidth = UIScaleManager.getSafeWidth();
        int safeHeight = UIScaleManager.getSafeHeight();
        int bannerWidth = Math.min(UIScaleManager.scale(isTimedObjective ? 220 : 160), safeWidth);
        int bannerHeight = Math.min(UIScaleManager.scale(isTimedObjective ? 48 : WAVE_BANNER_HEIGHT), safeHeight);
        int bannerX = safeLeft + (safeWidth - bannerWidth) / 2;
        int bannerY = Math.max(safeTop, UIScaleManager.scale(5));
        int padding = UIScaleManager.scale(6);

        // Choose colors based on objective type
        int borderColor = isTimedObjective ? COLOR_SURVIVE_GREEN : PANEL_BORDER; // Green for survive, orange for kill
        int bgColor = isTimedObjective ? OverlayTheme.Endurance.BG_SURVIVE : WAVE_BANNER_BG;

        // Semi-transparent background
        g.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + bannerHeight, bgColor);

        // Borders
        g.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + 2, borderColor);
        g.fill(bannerX, bannerY + bannerHeight - 2, bannerX + bannerWidth, bannerY + bannerHeight, borderColor);

        var safeFont = Objects.requireNonNull(font);

        if (isTimedObjective) {
            // === TIMED OBJECTIVE MODE ===
            // Show objective type label at top
            String objectiveLabel = objectiveType == WaveObjectiveState.Type.SURVIVE_TIME
                ? "\u23F1 SURVIVE" : "\u2B55 HOLD ZONE";  // ⏱ SURVIVE or ⭕ HOLD ZONE
            objectiveLabel = truncateToWidth(safeFont, objectiveLabel, bannerWidth - padding * 2);
            UIScaleManager.drawScaledCenteredString(g, font, objectiveLabel, bannerX + bannerWidth / 2,
                        bannerY + UIScaleManager.scale(4), COLOR_SURVIVE_GREEN);

            // Calculate remaining time
            int progress = data.objectiveProgress();
            int target = data.objectiveTarget();
            int remaining = Math.max(0, target - progress);

            // Large countdown timer in center
            String timerText = remaining + "s";
            int timerWidth = UIScaleManager.getScaledStringWidth(safeFont, timerText);
            g.pose().pushPose();
            float maxScale = timerWidth > 0
                ? Math.min(2.0f, (bannerWidth - padding * 2) / (float) timerWidth)
                : 1.0f;
            float scale = Math.max(0.8f, maxScale);

            g.pose().translate(bannerX + bannerWidth / 2.0f, bannerY + UIScaleManager.scale(14), 0);
            g.pose().scale(scale, scale, 1.0f);
            g.pose().translate(-timerWidth / 2.0f, 0, 0);

            // Pulse effect when time is low
            int timerColor = remaining <= 5 ? COLOR_AFFIX_EMPOWERED : TEXT_NORMAL; // Red when <= 5s
            UIScaleManager.drawScaledString(g, font, timerText, 0, 0, timerColor, true);
            g.pose().popPose();

            // Wave number smaller at bottom
            String waveLabel = "Wave " + data.currentWave();
            waveLabel = truncateToWidth(safeFont, waveLabel, bannerWidth - padding * 2);
            UIScaleManager.drawScaledCenteredString(g, font, waveLabel, bannerX + bannerWidth / 2,
                        bannerY + bannerHeight - UIScaleManager.scale(12), TEXT_DIM);
        } else {
            // === KILL OBJECTIVE MODE (original behavior) ===
            // Large wave number at center
            String waveNum = String.valueOf(data.currentWave());
            int numWidth = UIScaleManager.getScaledStringWidth(safeFont, Objects.requireNonNull(waveNum));

            g.pose().pushPose();
            float maxScale = numWidth > 0
                ? Math.min(2.0f, (bannerWidth - padding * 2) / (float) numWidth)
                : 1.0f;
            float scale = Math.max(0.8f, maxScale);

            g.pose().translate(bannerX + bannerWidth / 2.0f, bannerY + UIScaleManager.scale(4), 0);
            g.pose().scale(scale, scale, 1.0f);
            g.pose().translate(-numWidth / 2.0f, 0, 0);
            UIScaleManager.drawScaledString(g, font, waveNum, 0, 0, WAVE_NUMBER_COLOR, true);
            g.pose().popPose();

            // Label below the number
            if (!data.endlessMode()) {
                String totalLabel = "/ " + data.totalWaves();
                totalLabel = truncateToWidth(safeFont, totalLabel, bannerWidth - padding * 2);
                UIScaleManager.drawScaledCenteredString(g, font, totalLabel, bannerX + bannerWidth / 2,
                            bannerY + bannerHeight - UIScaleManager.scale(11), TEXT_DIM);
            } else {
                String endlessLabel = "ENDLESS";
                endlessLabel = truncateToWidth(safeFont, endlessLabel, bannerWidth - padding * 2);
                UIScaleManager.drawScaledCenteredString(g, font, endlessLabel, bannerX + bannerWidth / 2,
                            bannerY + bannerHeight - UIScaleManager.scale(11), TEXT_ACCENT);
            }
        }
    }

    /**
     * Renders wave completion animation.
     */
    private static void renderWaveCompleteAnimation(GuiGraphics g, Font font, int x, int y, int panelWidth) {
        long elapsed = System.currentTimeMillis() - waveCompleteAnimTime;
        if (elapsed > WAVE_ANIM_DURATION) return;

        float progress = (float) elapsed / WAVE_ANIM_DURATION;
        float alpha = progress < 0.7f ? 1.0f : 1.0f - (progress - 0.7f) / 0.3f;
        float scale = 1.0f + (1.0f - progress) * 0.3f;

        String message = truncateToWidth(Objects.requireNonNull(font), "WAVE COMPLETE!",
            Math.max(0, panelWidth - UIScaleManager.scale(8)));
        int color = applyAlpha(TEXT_SUCCESS, alpha);

        g.pose().pushPose();
        g.pose().translate(x + panelWidth / 2.0f, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-UIScaleManager.getScaledStringWidth(font, message) / 2.0f, 0, 0);

        UIScaleManager.drawScaledString(g, font, message, 0, 0, color, true);

        g.pose().popPose();
    }

    /**
     * Calculates dynamic panel height.
     */
    private static int calculatePanelHeight(QuestSyncPayload data, int panelWidth, boolean showDetails,
                                            int sLineHeight, int sPanelPadding, int sProgressBarHeight) {
        int height = sPanelPadding * 2;
        height += sLineHeight + UIScaleManager.scale(2);  // Header (quest name)
        if (!buildTemplatePolicyLine(data).isBlank()) {
            height += sLineHeight; // Template/Policy
        }
        if (!buildDifficultyLine(data).isBlank()) {
            height += sLineHeight; // Difficulty/Mode
        }
        height += sLineHeight;     // Objective
        if (data.objectiveDescription() != null && !data.objectiveDescription().isBlank()) {
            height += sLineHeight; // Objective description
        }
        height += sLineHeight + UIScaleManager.scale(2); // Status
        height += sLineHeight + UIScaleManager.scale(4);  // Wave progress text
        height += sProgressBarHeight + UIScaleManager.scale(6); // Progress bar

        // Combo/style (if active)
        if (data.currentCombo() > 0 || data.styleScore() > 0) {
            height += sLineHeight; // Combo line

            // Flow state indicator
            FlowStateTracker.FlowState flowState = data.getFlowState();
            if (flowState != FlowStateTracker.FlowState.NEUTRAL) {
                height += sLineHeight;
            }

            // Virtuoso progress bar
            if (data.virtuosoProgress() > 0 && data.virtuosoProgress() < 1.0f) {
                height += sLineHeight;
            }

            // Stale risk warning
            if (data.staleRisk() >= 0.66f && flowState != FlowStateTracker.FlowState.STALE) {
                height += sLineHeight;
            }

            height += UIScaleManager.scale(2);
        }

        // Momentum bar (always shown during quest)
        height += sLineHeight + UIScaleManager.scale(2); // Momentum bar line

        // Overdrive timer
        if (data.isOverdrive() && data.overdriveRemaining() > 0) {
            height += sLineHeight;
        }

        // Modifiers - calculate actual line count based on text width
        List<String> modifiers = data.waveModifiers();
        if (!modifiers.isEmpty()) {
            // Estimate modifier lines based on total character width
            // "Modifiers: " label + icons + names + spacing
            int estimatedWidth = UIScaleManager.scale(60); // "Modifiers: " label
            int lines = 1;
            for (String modName : modifiers) {
                int modWidth = modName.length() * UIScaleManager.scale(7) + UIScaleManager.scale(20); // icon + name + spacing
                if (estimatedWidth + modWidth > panelWidth - sPanelPadding * 2 - UIScaleManager.scale(40)) {
                    lines++;
                    estimatedWidth = 0;
                }
                estimatedWidth += modWidth;
            }
            height += sLineHeight * lines + UIScaleManager.scale(2);
        }

        if (showDetails) {
            height += UIScaleManager.scale(4);  // Separator
            height += sLineHeight * 4; // Run Stats + Time + Kills/DMG + metrics

            // LVC-specific lines if data available
            ClientLVCCache lvcCache = ClientLVCCache.INSTANCE;
            LVCSyncPayload lvcPayload = lvcCache.getCachedPayload();
            boolean hasLvc = lvcCache.hasData() && !lvcCache.isStale() && lvcPayload != null;

            if (hasLvc && lvcPayload != null) {
                LVCSyncPayload payload = lvcPayload;
                height += sLineHeight; // Accuracy & crits line

                // Ability usage line (if any abilities used)
                if (payload.dashCount() > 0 || payload.dodgeCount() > 0 || payload.perfectDodgeCount() > 0) {
                    height += sLineHeight;
                }

                // Defensive stats line (if meaningful)
                if (payload.totalDamageNegated() > 0 || payload.totalStaminaSpent() > 10) {
                    height += sLineHeight;
                }

                // Top weapons line (if any)
                String topWeapons = payload.topWeapons();
                if (topWeapons != null && !topWeapons.isEmpty()) {
                    height += sLineHeight;
                }
            }

            if (data.deaths() > 0) {
                height += sLineHeight; // Deaths
            }
        }

        height += sLineHeight + UIScaleManager.scale(2); // Keybind hint

        return height;
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        return font.plainSubstrByWidth(text, allowed) + "...";
    }

    /**
     * Formats duration as mm:ss.
     */
    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static String buildTemplatePolicyLine(QuestSyncPayload data) {
        String templateId = shortId(data.templateId());
        String policyId = shortId(data.policyId());
        if ((templateId == null || templateId.isBlank()) && (policyId == null || policyId.isBlank())) {
            return "";
        }
        String templateLabel = templateId != null && !templateId.isBlank()
            ? "Template: " + templateId + " v" + data.templateVersion()
            : "Template: -";
        String policyLabel = policyId != null && !policyId.isBlank()
            ? "Policy: " + policyId + " v" + data.policyVersion()
            : "Policy: -";
        return templateLabel + " | " + policyLabel;
    }

    private static String buildDifficultyLine(QuestSyncPayload data) {
        String difficulty = data.difficultyLabel();
        String questType = data.questTypeLabel();
        if ((difficulty == null || difficulty.isBlank()) && (questType == null || questType.isBlank())) {
            return "";
        }
        String difficultyLabel = difficulty != null && !difficulty.isBlank() ? difficulty : "standard";
        String questTypeLabel = questType != null && !questType.isBlank() ? questType : "endurance";
        return "Difficulty: " + difficultyLabel + " | Mode: " + questTypeLabel;
    }

    private static String shortId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value;
        int idx = trimmed.indexOf(':');
        if (idx >= 0 && idx + 1 < trimmed.length()) {
            trimmed = trimmed.substring(idx + 1);
        }
        if (trimmed.length() > 18) {
            trimmed = trimmed.substring(0, 18) + "...";
        }
        return trimmed;
    }

    private static String getStatusLabel(QuestSyncPayload data) {
        EnduranceQuestState state = data.getState();
        return switch (state) {
            case IN_PROGRESS -> "Combat";
            case WAVE_COMPLETE -> "Intermission";
            case FAILED -> "Downed";
            case COMPLETED -> "Complete";
            case COOLDOWN -> "Cooldown";
            default -> "Starting";
        };
    }

    private static int getStatusColor(QuestSyncPayload data) {
        return switch (data.getState()) {
            case WAVE_COMPLETE, COMPLETED -> TEXT_SUCCESS;
            case FAILED -> TEXT_DANGER;
            default -> TEXT_NORMAL;
        };
    }

    /**
     * Applies alpha to an ARGB color.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    /**
     * Returns color for modifier name.
     */
    private static int getModifierColor(String modName) {
        return switch (modName.toLowerCase(Locale.ROOT)) {
            case "swift" -> COLOR_AFFIX_SWIFT;
            case "empowered" -> COLOR_AFFIX_EMPOWERED;
            case "fortified" -> COLOR_AFFIX_FORTIFIED;
            case "armored" -> COLOR_AFFIX_ARMORED;
            case "blazing" -> COLOR_AFFIX_BLAZING;
            case "phantom" -> COLOR_AFFIX_PHANTOM;
            case "regenerating" -> COLOR_AFFIX_REGENERATING;
            case "horde" -> COLOR_AFFIX_HORDE;
            default -> TEXT_NORMAL;
        };
    }

    /**
     * Returns icon for modifier name.
     */
    private static String getModifierIcon(String modName) {
        return switch (modName.toLowerCase(Locale.ROOT)) {
            case "swift" -> "\u26A1";        // ⚡ Lightning bolt (speed)
            case "empowered" -> "\u2694";    // ⚔ Crossed swords (damage)
            case "fortified" -> "\u2665";    // ♥ Heart (more health)
            case "armored" -> "\u2748";      // ❈ Shield-like (armor)
            case "blazing" -> "\u2739";      // ✹ Fire/star (fire damage)
            case "phantom" -> "\u2734";      // ✴ Sparkle (invisibility)
            case "regenerating" -> "\u2764"; // ❤ Heart (regen)
            case "horde" -> "\u2694\u2694";  // ⚔⚔ Double swords (many mobs)
            default -> "\u2605";             // ★ Star (generic)
        };
    }

    private static String buildObjectiveProgressLabel(WaveObjectiveState.Type type, int progress, int target) {
        if (target <= 0) {
            return "Progress: " + progress;
        }
        return switch (type) {
            case SURVIVE_TIME, HOLD_ZONE -> progress + " / " + target + "s";
            case ELITE_HUNT -> progress + " / " + target + " elite";
            case KILL_ALL -> progress + " / " + target + " mobs";
        };
    }

    /**
     * Formats top weapons string for display.
     * Input format: "weapon1:kills,weapon2:kills,..." (from LVCSyncPayload)
     * Output format: "weapon1 (kills), weapon2 (kills)" - truncated to fit panel
     */
    private static String formatTopWeapons(String topWeapons) {
        if (topWeapons == null || topWeapons.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int maxWeapons = 2; // Show at most 2 weapons to fit panel width
        int count = 0;

        for (String entry : COMMA_SPLITTER.split(topWeapons)) {
            if (count >= maxWeapons) break;

            List<String> parts = COLON_SPLITTER.splitToList(Objects.requireNonNull(entry));
            if (parts.size() != 2) continue;

            String weapon = parts.get(0);
            String kills = parts.get(1);

            // Shorten weapon name (remove namespace, truncate if long)
            int colonIdx = weapon.indexOf(':');
            if (colonIdx >= 0 && colonIdx + 1 < weapon.length()) {
                weapon = weapon.substring(colonIdx + 1);
            }
            // Convert snake_case to readable
            weapon = weapon.replace("_", " ");
            // Truncate long names
            if (weapon.length() > 12) {
                weapon = weapon.substring(0, 10) + "..";
            }

            if (!result.isEmpty()) {
                result.append(", ");
            }
            result.append(weapon).append(":").append(kills);
            count++;
        }

        return result.toString();
    }

    // === State Transition Handler ===

    /**
     * Checks for state transitions and opens checkpoint screen when wave completes.
     * Uses a delay to allow player to see the "WAVE COMPLETE" animation first,
     * and will close any open screen (except chat) to show checkpoint.
     */
    private static void checkForStateTransition(Minecraft mc, EnduranceQuestState currentState) {
        // Detect transition to WAVE_COMPLETE
        if (currentState == EnduranceQuestState.WAVE_COMPLETE && !checkpointScreenShown) {
            // First time detecting wave complete - record timestamp
            if (waveCompleteDetectedTime == 0) {
                waveCompleteDetectedTime = System.currentTimeMillis();
                LOGGER.info("[CheckpointDebug] WAVE_COMPLETE detected, starting delay timer");
            }

            // Wait for delay before opening screen (allows animation to play)
            long elapsed = System.currentTimeMillis() - waveCompleteDetectedTime;
            if (elapsed >= CHECKPOINT_SCREEN_DELAY) {
                // Open checkpoint screen (on main thread)
                mc.execute(() -> {
                    // Don't interrupt PerkSelectionScreen - player needs to choose a perk first
                    if (mc.screen instanceof PerkSelectionScreen) {
                        LOGGER.debug("[CheckpointDebug] Waiting for PerkSelectionScreen");
                        return; // Wait for player to finish perk selection
                    }

                    if (EnduranceUiCache.getLastDirectiveChoices() != null) {
                        if (mc.screen instanceof WaveDirectiveScreen) {
                            LOGGER.debug("[CheckpointDebug] WaveDirectiveScreen already open");
                            return;
                        }
                        LOGGER.info("[CheckpointDebug] Opening WaveDirectiveScreen");
                        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
                            mc.setScreen(null);
                        }
                        if (mc.screen == null) {
                            com.devmod.client.ui.ScreenSafety.openSafe(
                                "wave_directive",
                                () -> new WaveDirectiveScreen());
                        }
                        return;
                    }

                    // Check if WIS debrief should show first
                    var wis = com.devmod.client.endurance.wis.WaveIntelligenceManager.INSTANCE;
                    if (wis.isEnabled() && wis.getCurrentPhase() == com.devmod.client.endurance.wis.WavePhase.DEBRIEF) {
                        // WIS is handling debrief - checkpoint will open after debrief closes
                        com.devmod.client.endurance.wis.WISOverlayHandler.requestCheckpointAfterDebrief();
                        checkpointScreenShown = true; // Prevent reopening
                        LOGGER.info("[CheckpointDebug] WIS debrief active, checkpoint will open after debrief");
                        return;
                    }

                    // Mark as shown to prevent reopening (only after perk selection is done)
                    checkpointScreenShown = true;
                    LOGGER.info("[CheckpointDebug] Opening WaveCheckpointScreen, currentScreen={}",
                        mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");

                    // Close current screen if it's not the checkpoint screen or chat
                    if (mc.screen != null && !(mc.screen instanceof WaveCheckpointScreen)) {
                        // Close inventory, pause menu, etc. to show checkpoint
                        if (!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
                            mc.setScreen(null);
                        }
                    }

                    // Open checkpoint screen if no screen is active
                    var currentScreen = mc.screen;
                    if (currentScreen == null) {
                        LOGGER.info("[CheckpointDebug] Invoking UI_WAVE_CHECKPOINT_OPEN action");
                        ActionRegistry.invoke(ActionIds.UI_WAVE_CHECKPOINT_OPEN,
                            ClientActionContexts.forClient(ActionOrigin.EVENT));
                    } else {
                        LOGGER.warn("[CheckpointDebug] Screen not null after close attempt: {}",
                            currentScreen.getClass().getSimpleName());
                    }
                });
            }
        }

        // Reset flags when state changes away from WAVE_COMPLETE
        if (currentState != EnduranceQuestState.WAVE_COMPLETE) {
            if (checkpointScreenShown || waveCompleteDetectedTime > 0) {
                LOGGER.info("[CheckpointDebug] State changed from WAVE_COMPLETE to {}, resetting flags",
                    currentState);
            }
            checkpointScreenShown = false;
            waveCompleteDetectedTime = 0;
        }
    }

    /**
     * Reset state watcher when quest ends or player disconnects.
     */
    public static void resetStateWatcher() {
        checkpointScreenShown = false;
        waveCompleteDetectedTime = 0;
    }

    // === Public API ===

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void setShowDetails(boolean value) {
        showDetails = value;
    }

    public static boolean isShowingDetails() {
        return showDetails;
    }

    public static void toggleDetails() {
        showDetails = !showDetails;
    }

    // === Boss Alert System ===

    /**
     * Called from network handler when server sends boss alert.
     */
    public static void onBossAlert(long duration, String type) {
        bossAlertStartTime = System.currentTimeMillis();
        bossAlertDuration = duration;
        bossAlertType = type;
        lastSoundTick = 0;
    }

    /**
     * Renders boss alert with pulsing red edges and countdown.
     */
    private static void renderBossAlert(GuiGraphics g, Font font, int screenWidth, int screenHeight) {
        if (bossAlertStartTime == 0) return;

        long elapsed = System.currentTimeMillis() - bossAlertStartTime;
        if (elapsed >= bossAlertDuration) {
            // Alert finished
            bossAlertStartTime = 0;
            return;
        }

        long remaining = bossAlertDuration - elapsed;
        int seconds = (int) (remaining / 1000) + 1;

        // Pulse effect (0.7 to 1.0)
        float pulse = (float) Math.sin(elapsed / 100.0) * 0.15f + 0.85f;

        // Screen edge glow red
        int edgeHeight = Math.max(2, UIScaleManager.scale(15));
        int glowAlpha = (int) (pulse * 180);
        int glowColor = OverlayTheme.withAlpha(COLOR_BOSS_ALERT, glowAlpha);

        // Top edge
        g.fill(0, 0, screenWidth, edgeHeight, glowColor);
        // Bottom edge
        g.fill(0, screenHeight - edgeHeight, screenWidth, screenHeight, glowColor);
        // Left edge
        g.fill(0, 0, edgeHeight, screenHeight, glowColor);
        // Right edge
        g.fill(screenWidth - edgeHeight, 0, screenWidth, screenHeight, glowColor);

        // Central warning text
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        String warningText = "!! BOSS INCOMING IN " + seconds + "s !!";
        int boxPadding = UIScaleManager.scale(10);
        int maxTextWidth = Math.max(0, UIScaleManager.getSafeWidth() - boxPadding * 2 - UIScaleManager.scale(8));
        warningText = truncateToWidth(font, warningText, maxTextWidth);
        int textWidth = font.width(warningText);

        // Background box
        int boxWidth = textWidth + boxPadding * 2;
        int boxHeight = UIScaleManager.scale(32);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        int boxX = Math.max(safeLeft, Math.min(centerX - boxWidth / 2, safeRight - boxWidth));
        int boxY = Math.max(safeTop, Math.min(centerY - UIScaleManager.scale(60), safeBottom - boxHeight));
        g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight,
            OverlayTheme.withAlpha(OverlayTheme.Utility.BLACK, OverlayTheme.Alpha.STANDARD));

        // Border
        int borderColorAlert = applyAlpha(COLOR_BOSS_ALERT, pulse);
        g.fill(boxX, boxY, boxX + boxWidth, boxY + 1, borderColorAlert);
        g.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, borderColorAlert);
        g.fill(boxX, boxY, boxX + 1, boxY + boxHeight, borderColorAlert);
        g.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, borderColorAlert);

        // Text pulsating
        int textAlpha = (int) (pulse * 255);
        int textColor = OverlayTheme.withAlpha(COLOR_BOSS_ALERT, textAlpha);
        UIScaleManager.drawScaledCenteredString(g, font, warningText, boxX + boxWidth / 2, boxY + UIScaleManager.scale(6), textColor);

        // Boss type below
        String bossType = Objects.requireNonNull(bossAlertType.toUpperCase(Locale.ROOT));
        bossType = truncateToWidth(font, bossType, maxTextWidth);
        UIScaleManager.drawScaledCenteredString(g, font, bossType, boxX + boxWidth / 2, boxY + UIScaleManager.scale(18), TEXT_DIM);

        // Sound every second (first 100ms of each second)
        Minecraft mc = Minecraft.getInstance();
        long currentSecond = remaining / 1000;
        var player = mc.player;
        if (currentSecond != lastSoundTick && player != null) {
            lastSoundTick = currentSecond;
            // Pitch increases as countdown approaches: 0.5 -> 1.5
            float pitch = 0.5f + (1.0f - remaining / (float) bossAlertDuration);
            player.playSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BELL.value()), 0.8f, pitch);
        }
    }
}
