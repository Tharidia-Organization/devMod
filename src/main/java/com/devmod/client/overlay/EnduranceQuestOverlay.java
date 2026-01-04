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

    // === Toggle ===
    private static boolean enabled = true;
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
        int panelX = MARGIN_LEFT;
        int panelY = MARGIN_TOP;

        // Calculate panel height
        int panelHeight = calculatePanelHeight(data);

        // Render panel
        renderPanel(graphics, font, panelX, panelY, PANEL_WIDTH, panelHeight, data);

        // Check for wave completion animation
        if (data.currentWave() != lastWave) {
            if (lastWave > 0 && data.currentWave() > lastWave) {
                waveCompleteAnimTime = System.currentTimeMillis();
            }
            lastWave = data.currentWave();
        }

        // Render wave complete animation
        renderWaveCompleteAnimation(graphics, font, panelX, panelY + panelHeight + 5);

        // Render prominent wave banner at top center
        renderWaveBanner(graphics, font, mc.getWindow().getGuiScaledWidth(), data);

        // Render boss alert if active
        renderBossAlert(graphics, font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /**
     * Renders the main panel.
     */
    private static void renderPanel(GuiGraphics g, Font font, int x, int y, int width, int height,
                                     QuestSyncPayload data) {
        // Background with border
        renderPanelBackground(g, x, y, width, height);

        int textX = x + PANEL_PADDING;
        int textY = y + PANEL_PADDING;

        // === Header: Quest Name (truncated if needed) ===
        String questName = data.questName();
        if (questName == null || questName.isEmpty()) {
            questName = "Endurance Quest";
        }
        // Remove problematic characters and truncate
        questName = questName.replaceAll("[^\\w\\s-]", "").trim();
        int maxNameWidth = width - PANEL_PADDING * 2 - 60; // Leave space for points
        var safeFont = Objects.requireNonNull(font);
        if (safeFont.width(Objects.requireNonNull(questName)) > maxNameWidth) {
            String ellipsis = "...";
            int minChars = Math.min(6, questName.length()); // Keep at least 6 chars for readability
            while (safeFont.width(questName + ellipsis) > maxNameWidth && questName.length() > minChars) {
                questName = questName.substring(0, questName.length() - 1);
            }
            questName = questName + ellipsis;
        }
        g.drawString(font, "\u2694 " + questName, textX, textY, TEXT_TITLE, true); // ⚔ icon

        // Points in header on the right
        String pointsText = data.pointsEarned() + " pts";
        int pointsWidth = font.width(pointsText);
        g.drawString(font, pointsText, x + width - PANEL_PADDING - pointsWidth, textY, TEXT_ACCENT, false);
        textY += LINE_HEIGHT + 4;

        // === Template/Policy/Difficulty ===
        String templatePolicy = buildTemplatePolicyLine(data);
        if (!templatePolicy.isBlank()) {
            g.drawString(font, templatePolicy, textX, textY, TEXT_DIM, false);
            textY += LINE_HEIGHT;
        }

        String difficultyLine = buildDifficultyLine(data);
        if (!difficultyLine.isBlank()) {
            g.drawString(font, difficultyLine, textX, textY, TEXT_DIM, false);
            textY += LINE_HEIGHT;
        }

        // === Objective & Status ===
        String objectiveTitle = data.objectiveTitle();
        if (objectiveTitle == null || objectiveTitle.isBlank()) {
            objectiveTitle = "Survive waves";
        }
        g.drawString(font, "Objective: " + objectiveTitle, textX, textY, TEXT_DIM, false);
        textY += LINE_HEIGHT;
        String objectiveDescription = data.objectiveDescription();
        if (objectiveDescription != null && !objectiveDescription.isBlank()) {
            g.drawString(font, objectiveDescription, textX, textY, TEXT_DIM, false);
            textY += LINE_HEIGHT;
        }
        String statusLabel = "Status: " + getStatusLabel(data);
        g.drawString(font, statusLabel, textX, textY, getStatusColor(data), false);
        textY += LINE_HEIGHT + 2;

        // === Wave Progress (on separate row) ===
        String waveText;
        if (data.endlessMode()) {
            waveText = "Wave " + data.currentWave() + " (Endless)";
        } else {
            waveText = "Wave " + data.currentWave() + " / " + data.totalWaves();
        }
        g.drawString(font, waveText, textX, textY, TEXT_NORMAL, false);

        // Kills on the right of the same row
        String killsText = data.mobsKilled() + " kills";
        int killsWidth = font.width(killsText);
        g.drawString(font, killsText, x + width - PANEL_PADDING - killsWidth, textY, TEXT_SUCCESS, false);
        textY += LINE_HEIGHT + 4;

        // === Progress Bar (Objective progress) ===
        WaveObjectiveState.Type objectiveType = data.getObjectiveType();
        int progressValue = objectiveType == WaveObjectiveState.Type.KILL_ALL
            ? data.mobsKilledInWave()
            : data.objectiveProgress();
        int targetValue = objectiveType == WaveObjectiveState.Type.KILL_ALL
            ? data.totalMobsInWave()
            : data.objectiveTarget();
        float progress = targetValue > 0 ? (float) progressValue / targetValue : 0;

        renderProgressBar(g, textX, textY, width - PANEL_PADDING * 2, PROGRESS_BAR_HEIGHT, progress);

        // Progress text above the bar
        String progressLabel = buildObjectiveProgressLabel(objectiveType, progressValue, targetValue);
        int mobTextWidth = font.width(Objects.requireNonNull(progressLabel, "progressLabel"));
        g.drawString(font, progressLabel, textX + (width - PANEL_PADDING * 2 - mobTextWidth) / 2,
                     textY - 1, TEXT_DIM, false);
        textY += PROGRESS_BAR_HEIGHT + 6;

        // === Combo & Style Rank ===
        if (data.currentCombo() > 0 || data.styleScore() > 0) {
            ComboSystem.StyleRank rank = data.getStyleRank();
            String comboText = "Combo: " + data.currentCombo() + " | " + rank.displayName;
            g.drawString(font, comboText, textX, textY, rank.color, false);
            textY += LINE_HEIGHT;

            // === Flow State Indicator ===
            FlowStateTracker.FlowState flowState = data.getFlowState();
            if (flowState != FlowStateTracker.FlowState.NEUTRAL) {
                // Show flow state with color
                String flowText = flowState.displayName;
                if (!flowText.isEmpty()) {
                    g.drawString(font, flowText, textX, textY, flowState.color, false);
                    textY += LINE_HEIGHT;
                }
            }

            // Show virtuoso progress bar if making progress
            if (data.virtuosoProgress() > 0 && data.virtuosoProgress() < 1.0f) {
                String virtuosoLabel = "Virtuoso: ";
                g.drawString(font, virtuosoLabel, textX, textY, TEXT_DIM, false);
                int barX = textX + font.width(virtuosoLabel);
                int barWidth = 60;
                int barHeight = 4;
                // Background
                g.fill(barX, textY + 2, barX + barWidth, textY + 2 + barHeight, PROGRESS_BG);
                // Fill (golden for virtuoso progress)
                int fillWidth = (int) (barWidth * data.virtuosoProgress());
                g.fill(barX, textY + 2, barX + fillWidth, textY + 2 + barHeight, COLOR_MOMENTUM_HEATED);
                textY += LINE_HEIGHT;
            }

            // Show stale risk warning
            if (data.staleRisk() >= 0.66f && flowState != FlowStateTracker.FlowState.STALE) {
                String warning = "⚠ Vary attacks!";
                g.drawString(font, warning, textX, textY, COLOR_MOMENTUM_HEATED, false);
                textY += LINE_HEIGHT;
            }

            textY += 2;
        }

        // === Momentum Bar ===
        MomentumTracker.MomentumState momentumState = data.getMomentumState();
        int momentumPercent = data.momentumPercent();

        // Draw momentum label
        String momentumLabel = "Momentum: ";
        g.drawString(font, momentumLabel, textX, textY, TEXT_DIM, false);
        int barX = textX + font.width(momentumLabel);
        int barWidth = 80;
        int barHeight = 6;

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
        String percentText = momentumPercent + "%";
        g.drawString(font, percentText, barX + barWidth + 4, textY, TEXT_NORMAL, false);

        // State indicator
        if (momentumState != MomentumTracker.MomentumState.BUILDING) {
            String stateText = momentumState.displayName;
            if (!stateText.isEmpty()) {
                int stateX = barX + barWidth + 4 + font.width(percentText) + 4;
                g.drawString(font, stateText, stateX, textY, momentumState.color, false);
            }
        }

        // Overdrive timer
        if (data.isOverdrive() && data.overdriveRemaining() > 0) {
            textY += LINE_HEIGHT;
            long seconds = data.overdriveRemaining() / 1000;
            String timerText = "⚡ OVERDRIVE: " + seconds + "s";
            g.drawString(font, timerText, textX, textY, COLOR_MOMENTUM_OVERDRIVE, false);
        }

        textY += LINE_HEIGHT + 2;

        // === Wave Modifiers ===
        List<String> modifiers = data.waveModifiers();
        if (!modifiers.isEmpty()) {
            String modLabel = "Modifiers: ";
            g.drawString(font, modLabel, textX, textY, TEXT_DIM, false);

            int modX = textX + font.width(modLabel);
            for (String modName : modifiers) {
                int modColor = getModifierColor(modName);
                String icon = getModifierIcon(modName);
                String displayText = icon + " " + modName;
                g.drawString(font, displayText, modX, textY, modColor, false);
                modX += font.width(displayText) + 6;

                // Wrap to next line if needed
                if (modX > x + width - PANEL_PADDING - 40) {
                    textY += LINE_HEIGHT;
                    modX = textX;
                }
            }
            textY += LINE_HEIGHT + 2;
        }

        // === Detailed Stats ===
        if (showDetails) {
            // Separator line
            g.fill(x + 4, textY, x + width - 4, textY + 1,
                OverlayTheme.withAlpha(PANEL_BORDER, OverlayTheme.Alpha.GHOST));
            textY += 4;

            g.drawString(font, "Run Stats", textX, textY, TEXT_ACCENT, false);
            textY += LINE_HEIGHT;

            // Contract multiplier (if active)
            if (ContractHudOverlay.INSTANCE.hasActiveContracts()) {
                float contractMult = ContractHudOverlay.INSTANCE.getTotalMultiplier();
                String contractText = String.format("\u2694 Contracts: %.1fx", contractMult);
                g.drawString(font, contractText, textX, textY, OverlayTheme.Contract.MULTIPLIER_HIGH, false);
                textY += LINE_HEIGHT;
            }

            // Session timer + Kills on same line
            long duration = data.sessionDurationMs();
            String timeText = formatDuration(duration);
            g.drawString(font, "Time: " + timeText, textX, textY, TEXT_DIM, false);
            String killText = data.mobsKilled() + " kills";
            int killWidth = font.width(killText);
            g.drawString(font, killText, x + width - PANEL_PADDING - killWidth, textY, TEXT_SUCCESS, false);
            textY += LINE_HEIGHT;

            // Damage dealt/taken + Deaths (if any)
            String dmgText = "DMG: " + data.damageDealt() + "/" + data.damageTaken();
            g.drawString(font, dmgText, textX, textY, TEXT_DIM, false);
            if (data.deaths() > 0) {
                String deathText = "\u2620 " + data.deaths(); // Skull icon
                int deathWidth = font.width(deathText);
                g.drawString(font, deathText, x + width - PANEL_PADDING - deathWidth, textY, TEXT_DANGER, false);
            }
            textY += LINE_HEIGHT;

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
            g.drawString(font, dpsMetrics, textX, textY, TEXT_DIM, false);
            textY += LINE_HEIGHT;

            // LVC-specific stats (accuracy, crits, abilities, defense)
            if (hasLvc && lvcPayload != null) {
                LVCSyncPayload payload = lvcPayload;
                // Accuracy & Crits
                double accuracy = lvcCache.getAccuracy() * 100;
                int critCount = payload.criticalHitCount();
                String accCritText = String.format("Accuracy %.0f%% | \u2726 %d crits", accuracy, critCount); // Sparkle icon
                g.drawString(font, accCritText, textX, textY, TEXT_DIM, false);
                textY += LINE_HEIGHT;

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
                    g.drawString(font, abilityText.toString(), textX, textY, COLOR_AFFIX_SWIFT, false);
                    textY += LINE_HEIGHT;
                }

                // Defensive stats (damage negated, stamina spent) - only if meaningful
                double damageNegated = payload.totalDamageNegated();
                double staminaSpent = payload.totalStaminaSpent();
                if (damageNegated > 0 || staminaSpent > 10) {
                    String defenseText = String.format("\u2764 %.0f blocked | \u269B %.0f stamina",
                        damageNegated, staminaSpent); // Blocked and stamina icons
                    g.drawString(font, defenseText, textX, textY, COLOR_SURVIVE_GREEN, false);
                    textY += LINE_HEIGHT;
                }

                // Top weapons - compact display
                String topWeapons = payload.topWeapons();
                if (topWeapons != null && !topWeapons.isEmpty()) {
                    String weaponsDisplay = formatTopWeapons(topWeapons);
                    if (!weaponsDisplay.isEmpty()) {
                        g.drawString(font, "\u2694 " + weaponsDisplay, textX, textY, TEXT_TITLE, false);
                        textY += LINE_HEIGHT;
                    }
                }
            }
        }

        // === Keybind Hint ===
        textY += 2;
        String hint = "F11: Continue | F12: Exit";
        g.drawString(font, hint, textX, textY, COLOR_BORDER_DIM, false);
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
    private static void renderWaveBanner(GuiGraphics g, Font font, int screenWidth, QuestSyncPayload data) {
        WaveObjectiveState.Type objectiveType = data.getObjectiveType();
        boolean isTimedObjective = objectiveType == WaveObjectiveState.Type.SURVIVE_TIME ||
                                   objectiveType == WaveObjectiveState.Type.HOLD_ZONE;

        // Banner positioned at top-center - wider for timed objectives
        int bannerWidth = isTimedObjective ? 220 : 160;
        int bannerHeight = isTimedObjective ? 48 : WAVE_BANNER_HEIGHT;
        int bannerX = (screenWidth - bannerWidth) / 2;
        int bannerY = 5;

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
            int labelWidth = safeFont.width(objectiveLabel);
            g.drawString(font, objectiveLabel, bannerX + bannerWidth / 2 - labelWidth / 2,
                        bannerY + 4, COLOR_SURVIVE_GREEN, true);

            // Calculate remaining time
            int progress = data.objectiveProgress();
            int target = data.objectiveTarget();
            int remaining = Math.max(0, target - progress);

            // Large countdown timer in center
            String timerText = remaining + "s";
            g.pose().pushPose();
            float scale = 2.0f;
            int timerWidth = safeFont.width(timerText);

            g.pose().translate(bannerX + bannerWidth / 2.0f, bannerY + 14, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.pose().translate(-timerWidth / 2.0f, 0, 0);

            // Pulse effect when time is low
            int timerColor = remaining <= 5 ? COLOR_AFFIX_EMPOWERED : TEXT_NORMAL; // Red when <= 5s
            g.drawString(font, timerText, 0, 0, timerColor, true);
            g.pose().popPose();

            // Wave number smaller at bottom
            String waveLabel = "Wave " + data.currentWave();
            int waveLabelWidth = safeFont.width(waveLabel);
            g.drawString(font, waveLabel, bannerX + bannerWidth / 2 - waveLabelWidth / 2,
                        bannerY + bannerHeight - 12, TEXT_DIM, false);
        } else {
            // === KILL OBJECTIVE MODE (original behavior) ===
            // Large wave number at center
            String waveNum = String.valueOf(data.currentWave());

            g.pose().pushPose();
            float scale = 2.0f;
            int numWidth = safeFont.width(Objects.requireNonNull(waveNum));

            g.pose().translate(bannerX + bannerWidth / 2.0f, bannerY + 4, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.pose().translate(-numWidth / 2.0f, 0, 0);
            g.drawString(font, waveNum, 0, 0, WAVE_NUMBER_COLOR, true);
            g.pose().popPose();

            // Label below the number
            if (!data.endlessMode()) {
                String totalLabel = "/ " + data.totalWaves();
                int labelWidth = safeFont.width(totalLabel);
                g.drawString(font, totalLabel, bannerX + bannerWidth / 2 - labelWidth / 2,
                            bannerY + bannerHeight - 11, TEXT_DIM, false);
            } else {
                String endlessLabel = "ENDLESS";
                int labelWidth = safeFont.width(endlessLabel);
                g.drawString(font, endlessLabel, bannerX + bannerWidth / 2 - labelWidth / 2,
                            bannerY + bannerHeight - 11, TEXT_ACCENT, false);
            }
        }
    }

    /**
     * Renders wave completion animation.
     */
    private static void renderWaveCompleteAnimation(GuiGraphics g, Font font, int x, int y) {
        long elapsed = System.currentTimeMillis() - waveCompleteAnimTime;
        if (elapsed > WAVE_ANIM_DURATION) return;

        float progress = (float) elapsed / WAVE_ANIM_DURATION;
        float alpha = progress < 0.7f ? 1.0f : 1.0f - (progress - 0.7f) / 0.3f;
        float scale = 1.0f + (1.0f - progress) * 0.3f;

        String message = "WAVE COMPLETE!";
        int color = applyAlpha(TEXT_SUCCESS, alpha);

        g.pose().pushPose();
        g.pose().translate(x + PANEL_WIDTH / 2.0f, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-font.width(message) / 2.0f, 0, 0);

        g.drawString(font, message, 0, 0, color, true);

        g.pose().popPose();
    }

    /**
     * Calculates dynamic panel height.
     */
    private static int calculatePanelHeight(QuestSyncPayload data) {
        int height = PANEL_PADDING * 2;
        height += LINE_HEIGHT + 2;  // Header (quest name)
        if (!buildTemplatePolicyLine(data).isBlank()) {
            height += LINE_HEIGHT; // Template/Policy
        }
        if (!buildDifficultyLine(data).isBlank()) {
            height += LINE_HEIGHT; // Difficulty/Mode
        }
        height += LINE_HEIGHT;     // Objective
        if (data.objectiveDescription() != null && !data.objectiveDescription().isBlank()) {
            height += LINE_HEIGHT; // Objective description
        }
        height += LINE_HEIGHT + 2; // Status
        height += LINE_HEIGHT + 4;  // Wave progress text
        height += PROGRESS_BAR_HEIGHT + 6; // Progress bar

        // Combo/style (if active)
        if (data.currentCombo() > 0 || data.styleScore() > 0) {
            height += LINE_HEIGHT; // Combo line

            // Flow state indicator
            FlowStateTracker.FlowState flowState = data.getFlowState();
            if (flowState != FlowStateTracker.FlowState.NEUTRAL) {
                height += LINE_HEIGHT;
            }

            // Virtuoso progress bar
            if (data.virtuosoProgress() > 0 && data.virtuosoProgress() < 1.0f) {
                height += LINE_HEIGHT;
            }

            // Stale risk warning
            if (data.staleRisk() >= 0.66f && flowState != FlowStateTracker.FlowState.STALE) {
                height += LINE_HEIGHT;
            }

            height += 2;
        }

        // Momentum bar (always shown during quest)
        height += LINE_HEIGHT + 2; // Momentum bar line

        // Overdrive timer
        if (data.isOverdrive() && data.overdriveRemaining() > 0) {
            height += LINE_HEIGHT;
        }

        // Modifiers - calculate actual line count based on text width
        List<String> modifiers = data.waveModifiers();
        if (!modifiers.isEmpty()) {
            // Estimate modifier lines based on total character width
            // "Modifiers: " label + icons + names + spacing
            int estimatedWidth = 60; // "Modifiers: " label
            int lines = 1;
            for (String modName : modifiers) {
                int modWidth = modName.length() * 7 + 20; // icon + name + spacing
                if (estimatedWidth + modWidth > PANEL_WIDTH - PANEL_PADDING * 2 - 40) {
                    lines++;
                    estimatedWidth = 0;
                }
                estimatedWidth += modWidth;
            }
            height += LINE_HEIGHT * lines + 2;
        }

            if (showDetails) {
                height += 4;  // Separator
                height += LINE_HEIGHT * 4; // Run Stats + Time + Kills/DMG + metrics

                // LVC-specific lines if data available
                ClientLVCCache lvcCache = ClientLVCCache.INSTANCE;
                LVCSyncPayload lvcPayload = lvcCache.getCachedPayload();
                boolean hasLvc = lvcCache.hasData() && !lvcCache.isStale() && lvcPayload != null;

                if (hasLvc && lvcPayload != null) {
                    LVCSyncPayload payload = lvcPayload;
                    height += LINE_HEIGHT; // Accuracy & crits line

                    // Ability usage line (if any abilities used)
                    if (payload.dashCount() > 0 || payload.dodgeCount() > 0 || payload.perfectDodgeCount() > 0) {
                        height += LINE_HEIGHT;
                    }

                    // Defensive stats line (if meaningful)
                    if (payload.totalDamageNegated() > 0 || payload.totalStaminaSpent() > 10) {
                        height += LINE_HEIGHT;
                    }

                    // Top weapons line (if any)
                    String topWeapons = payload.topWeapons();
                    if (topWeapons != null && !topWeapons.isEmpty()) {
                        height += LINE_HEIGHT;
                    }
                }

            if (data.deaths() > 0) {
                height += LINE_HEIGHT; // Deaths
            }
        }

        height += LINE_HEIGHT + 2; // Keybind hint

        return height;
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
                            mc.setScreen(new WaveDirectiveScreen());
                        }
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
        int edgeHeight = 15;
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
        int textWidth = font.width(warningText);

        // Background box
        int boxPadding = 10;
        int boxX = centerX - textWidth / 2 - boxPadding;
        int boxY = centerY - 60;
        g.fill(boxX, boxY, boxX + textWidth + boxPadding * 2, boxY + 32,
            OverlayTheme.withAlpha(OverlayTheme.Utility.BLACK, OverlayTheme.Alpha.STANDARD));

        // Border
        int borderColorAlert = applyAlpha(COLOR_BOSS_ALERT, pulse);
        g.fill(boxX, boxY, boxX + textWidth + boxPadding * 2, boxY + 1, borderColorAlert);
        g.fill(boxX, boxY + 31, boxX + textWidth + boxPadding * 2, boxY + 32, borderColorAlert);
        g.fill(boxX, boxY, boxX + 1, boxY + 32, borderColorAlert);
        g.fill(boxX + textWidth + boxPadding * 2 - 1, boxY, boxX + textWidth + boxPadding * 2, boxY + 32, borderColorAlert);

        // Text pulsating
        int textAlpha = (int) (pulse * 255);
        int textColor = OverlayTheme.withAlpha(COLOR_BOSS_ALERT, textAlpha);
        g.drawCenteredString(font, warningText, centerX, boxY + 6, textColor);

        // Boss type below
        g.drawCenteredString(font, Objects.requireNonNull(bossAlertType.toUpperCase(Locale.ROOT)), centerX, boxY + 18, TEXT_DIM);

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
