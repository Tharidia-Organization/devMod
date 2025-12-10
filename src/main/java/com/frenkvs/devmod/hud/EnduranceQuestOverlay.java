package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.endurance.ClientQuestCache;
import com.frenkvs.devmod.endurance.ComboSystem;
import com.frenkvs.devmod.endurance.EnduranceQuestState;
import com.frenkvs.devmod.endurance.QuestSyncPayload;
import com.frenkvs.devmod.endurance.WaveCheckpointScreen;
import com.frenkvs.devmod.endurance.PerkSelectionScreen;
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

import java.util.List;

/**
 * HUD Overlay per Endurance Quest.
 *
 * Mostra durante una quest attiva:
 * - Wave corrente / totale (o "Endless" se in modalità infinita)
 * - Mob rimasti nella wave (con progress bar)
 * - Modificatori attivi della wave (icone/nomi)
 * - Timer sessione e punti accumulati
 * - Kill count e damage dealt/taken
 * - Combo e Style rank (DMC-style)
 *
 * Posizione: alto a sinistra (sotto boss bars)
 * Stile: Compatto, semi-trasparente, coerente con altri overlay DevMod
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
@SuppressWarnings("null")
public class EnduranceQuestOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "endurance_quest_hud");

    // === Colori UI ===
    private static final int PANEL_BG = 0xDD1A1A2E;           // Blu scuro 87% opacity
    private static final int PANEL_BORDER = 0xFFFF5722;       // Arancione (endurance)
    private static final int PANEL_BORDER_GLOW = 0x44FF5722;  // Glow border

    private static final int TEXT_TITLE = 0xFFFFAB91;         // Arancione chiaro (wave)
    private static final int TEXT_NORMAL = 0xFFFFFFFF;        // Bianco
    private static final int TEXT_ACCENT = 0xFFFF5722;        // Arancione (highlight)
    private static final int TEXT_DIM = 0xFFAAAAAA;           // Grigio (secondary)
    private static final int TEXT_DANGER = 0xFFFF5252;        // Rosso (damage taken)
    private static final int TEXT_SUCCESS = 0xFF4CAF50;       // Verde (kills)

    private static final int PROGRESS_BG = 0xFF333333;        // Sfondo barra
    private static final int PROGRESS_FILL = 0xFFFF5722;      // Arancione (riempimento)

    // === Dimensioni ===
    private static final int PANEL_PADDING = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int PANEL_WIDTH = 200;
    private static final int MARGIN_LEFT = 10;
    private static final int MARGIN_TOP = 100; // Sotto FpsTracker (5,5)-(145,90)

    private static final int PROGRESS_BAR_HEIGHT = 6;

    // === Toggle ===
    private static boolean enabled = true;
    private static boolean showDetails = true; // Mostra stats dettagliate

    // === Animation ===
    private static long waveCompleteAnimTime = 0;
    private static int lastWave = 0;
    private static final long WAVE_ANIM_DURATION = 2000;

    // === Wave Counter Banner ===
    private static final int WAVE_BANNER_HEIGHT = 32;
    private static final int WAVE_BANNER_BG = 0xBB1A1A2E;     // Semi-transparent
    private static final int WAVE_NUMBER_COLOR = 0xFFFF5722;   // Arancione brillante

    // === State Watcher for Checkpoint Screen ===
    private static boolean checkpointScreenShown = false;
    private static long waveCompleteDetectedTime = 0;
    private static final long CHECKPOINT_SCREEN_DELAY = 500; // ms before auto-opening

    // === Boss Alert State ===
    private static long bossAlertStartTime = 0;
    private static long bossAlertDuration = 0;
    private static String bossAlertType = "";
    private static long lastSoundTick = 0;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.BOSS_OVERLAY,
            LAYER_ID,
            EnduranceQuestOverlay::render
        );
    }

    /**
     * Metodo di rendering principale.
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
     * Renderizza il pannello principale.
     */
    private static void renderPanel(GuiGraphics g, Font font, int x, int y, int width, int height,
                                     QuestSyncPayload data) {
        // Sfondo con bordo
        renderPanelBackground(g, x, y, width, height);

        int textX = x + PANEL_PADDING;
        int textY = y + PANEL_PADDING;

        // === Header: Nome Quest (troncato se necessario) ===
        String questName = data.questName();
        if (questName == null || questName.isEmpty()) {
            questName = "Endurance Quest";
        }
        // Rimuovi caratteri problematici e tronca
        questName = questName.replaceAll("[^\\w\\s-]", "").trim();
        int maxNameWidth = width - PANEL_PADDING * 2 - 60; // Lascia spazio per punti
        if (font.width(questName) > maxNameWidth) {
            while (font.width(questName + "...") > maxNameWidth && questName.length() > 3) {
                questName = questName.substring(0, questName.length() - 1);
            }
            questName = questName + "...";
        }
        g.drawString(font, "\u2694 " + questName, textX, textY, TEXT_TITLE, true); // ⚔ icon

        // Punti nell'header a destra
        String pointsText = data.pointsEarned() + " pts";
        int pointsWidth = font.width(pointsText);
        g.drawString(font, pointsText, x + width - PANEL_PADDING - pointsWidth, textY, TEXT_ACCENT, false);
        textY += LINE_HEIGHT + 4;

        // === Wave Progress (su riga separata) ===
        String waveText;
        if (data.endlessMode()) {
            waveText = "Wave " + data.currentWave() + " (Endless)";
        } else {
            waveText = "Wave " + data.currentWave() + " / " + data.totalWaves();
        }
        g.drawString(font, waveText, textX, textY, TEXT_NORMAL, false);

        // Kills a destra sulla stessa riga
        String killsText = data.mobsKilled() + " kills";
        int killsWidth = font.width(killsText);
        g.drawString(font, killsText, x + width - PANEL_PADDING - killsWidth, textY, TEXT_SUCCESS, false);
        textY += LINE_HEIGHT + 4;

        // === Progress Bar (Mob nella wave) ===
        int killed = data.mobsKilledInWave();
        int total = data.totalMobsInWave();
        float progress = total > 0 ? (float) killed / total : 0;

        renderProgressBar(g, textX, textY, width - PANEL_PADDING * 2, PROGRESS_BAR_HEIGHT, progress);

        // Progress text sopra la barra
        String mobText = killed + " / " + total + " mobs";
        int mobTextWidth = font.width(mobText);
        g.drawString(font, mobText, textX + (width - PANEL_PADDING * 2 - mobTextWidth) / 2,
                     textY - 1, TEXT_DIM, false);
        textY += PROGRESS_BAR_HEIGHT + 6;

        // === Combo & Style Rank ===
        if (data.currentCombo() > 0 || data.styleScore() > 0) {
            ComboSystem.StyleRank rank = data.getStyleRank();
            String comboText = "Combo: " + data.currentCombo() + " | " + rank.displayName;
            g.drawString(font, comboText, textX, textY, rank.color, false);
            textY += LINE_HEIGHT + 2;
        }

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

        // === Stats Dettagliate ===
        if (showDetails) {
            // Linea separatore
            g.fill(x + 4, textY, x + width - 4, textY + 1, PANEL_BORDER & 0x55FFFFFF);
            textY += 4;

            // Timer sessione
            long duration = data.sessionDurationMs();
            String timeText = formatDuration(duration);
            g.drawString(font, "Time: " + timeText, textX, textY, TEXT_DIM, false);
            textY += LINE_HEIGHT;

            // Kill count
            String killText = "Kills: " + data.mobsKilled();
            g.drawString(font, killText, textX, textY, TEXT_SUCCESS, false);

            // Damage dealt/taken a destra
            String dmgText = "DMG: " + data.damageDealt() + "/" + data.damageTaken();
            int dmgWidth = font.width(dmgText);
            g.drawString(font, dmgText, x + width - PANEL_PADDING - dmgWidth, textY, TEXT_DIM, false);
            textY += LINE_HEIGHT;

            // Deaths questa sessione (se > 0)
            if (data.deaths() > 0) {
                g.drawString(font, "Deaths: " + data.deaths(), textX, textY, TEXT_DANGER, false);
                textY += LINE_HEIGHT;
            }
        }

        // === Keybind Hint ===
        textY += 2;
        String hint = "F11: Continue | F12: Exit";
        g.drawString(font, hint, textX, textY, 0xFF555555, false);
    }

    /**
     * Renderizza sfondo pannello con bordo.
     */
    private static void renderPanelBackground(GuiGraphics g, int x, int y, int width, int height) {
        // Glow esterno
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_BORDER_GLOW);

        // Sfondo principale
        g.fill(x, y, x + width, y + height, PANEL_BG);

        // Bordo
        g.fill(x, y, x + width, y + 1, PANEL_BORDER);                    // Top
        g.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);  // Bottom
        g.fill(x, y, x + 1, y + height, PANEL_BORDER);                   // Left
        g.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);   // Right
    }

    /**
     * Renderizza la progress bar.
     */
    private static void renderProgressBar(GuiGraphics g, int x, int y, int width, int height, float progress) {
        // Background
        g.fill(x, y, x + width, y + height, PROGRESS_BG);

        // Fill
        int fillWidth = (int) (width * Math.min(1.0f, progress));
        g.fill(x, y, x + fillWidth, y + height, PROGRESS_FILL);

        // Border
        g.fill(x, y, x + width, y + 1, 0xFF555555);
        g.fill(x, y + height - 1, x + width, y + height, 0xFF555555);
    }

    /**
     * Renderizza il wave banner prominente al centro-alto dello schermo.
     * Mostra numero wave grande e ben visibile.
     */
    private static void renderWaveBanner(GuiGraphics g, Font font, int screenWidth, QuestSyncPayload data) {
        // Banner posizionato al centro-alto
        int bannerWidth = 160;
        int bannerX = (screenWidth - bannerWidth) / 2;
        int bannerY = 5;

        // Sfondo semi-trasparente
        g.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + WAVE_BANNER_HEIGHT, WAVE_BANNER_BG);

        // Bordi arancioni
        g.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + 2, PANEL_BORDER);
        g.fill(bannerX, bannerY + WAVE_BANNER_HEIGHT - 2, bannerX + bannerWidth, bannerY + WAVE_BANNER_HEIGHT, PANEL_BORDER);

        // Wave number grande al centro
        String waveNum = String.valueOf(data.currentWave());

        // Scala il numero della wave per renderlo prominente
        g.pose().pushPose();
        float scale = 2.0f;
        int numWidth = font.width(waveNum);

        // Numero grande centrato
        g.pose().translate(bannerX + bannerWidth / 2.0f, bannerY + 4, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-numWidth / 2.0f, 0, 0);
        g.drawString(font, waveNum, 0, 0, WAVE_NUMBER_COLOR, true);
        g.pose().popPose();

        // Label sotto il numero (solo se endless o per mostrare totale)
        if (!data.endlessMode()) {
            String totalLabel = "/ " + data.totalWaves();
            int labelWidth = font.width(totalLabel);
            g.drawString(font, totalLabel, bannerX + bannerWidth / 2 - labelWidth / 2,
                        bannerY + WAVE_BANNER_HEIGHT - 11, TEXT_DIM, false);
        } else {
            String endlessLabel = "ENDLESS";
            int labelWidth = font.width(endlessLabel);
            g.drawString(font, endlessLabel, bannerX + bannerWidth / 2 - labelWidth / 2,
                        bannerY + WAVE_BANNER_HEIGHT - 11, TEXT_ACCENT, false);
        }
    }

    /**
     * Renderizza animazione completamento wave.
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
     * Calcola altezza dinamica del pannello.
     */
    private static int calculatePanelHeight(QuestSyncPayload data) {
        int height = PANEL_PADDING * 2;
        height += LINE_HEIGHT + 2;  // Header (quest name)
        height += LINE_HEIGHT + 4;  // Wave progress text
        height += PROGRESS_BAR_HEIGHT + 6; // Progress bar

        // Combo/style (if active)
        if (data.currentCombo() > 0 || data.styleScore() > 0) {
            height += LINE_HEIGHT + 2;
        }

        // Modifiers (estimate 1-2 lines)
        List<String> modifiers = data.waveModifiers();
        if (!modifiers.isEmpty()) {
            int modCount = modifiers.size();
            height += LINE_HEIGHT * (modCount > 3 ? 2 : 1) + 2;
        }

        if (showDetails) {
            height += 4;  // Separator
            height += LINE_HEIGHT * 2; // Time + Kills/DMG

            if (data.deaths() > 0) {
                height += LINE_HEIGHT; // Deaths
            }
        }

        height += LINE_HEIGHT + 2; // Keybind hint

        return height;
    }

    /**
     * Formatta durata in mm:ss.
     */
    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Applica alpha a un colore ARGB.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Restituisce colore per nome di modificatore.
     */
    private static int getModifierColor(String modName) {
        return switch (modName.toLowerCase()) {
            case "swift" -> 0xFF64B5F6;       // Blu chiaro
            case "empowered" -> 0xFFFF5252;   // Rosso
            case "fortified" -> 0xFF4CAF50;   // Verde
            case "armored" -> 0xFF9E9E9E;     // Grigio
            case "blazing" -> 0xFFFF9800;     // Arancione
            case "phantom" -> 0xFF7C4DFF;     // Viola
            case "regenerating" -> 0xFFE91E63;// Rosa
            case "horde" -> 0xFFFFEB3B;       // Giallo
            default -> 0xFFFFFFFF;            // Bianco
        };
    }

    /**
     * Restituisce icona per nome di modificatore.
     */
    private static String getModifierIcon(String modName) {
        return switch (modName.toLowerCase()) {
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
            }

            // Wait for delay before opening screen (allows animation to play)
            long elapsed = System.currentTimeMillis() - waveCompleteDetectedTime;
            if (elapsed >= CHECKPOINT_SCREEN_DELAY) {
                // Open checkpoint screen (on main thread)
                mc.execute(() -> {
                    // Don't interrupt PerkSelectionScreen - player needs to choose a perk first
                    if (mc.screen instanceof PerkSelectionScreen) {
                        return; // Wait for player to finish perk selection
                    }

                    // Mark as shown to prevent reopening (only after perk selection is done)
                    checkpointScreenShown = true;

                    // Close current screen if it's not the checkpoint screen or chat
                    if (mc.screen != null && !(mc.screen instanceof WaveCheckpointScreen)) {
                        // Close inventory, pause menu, etc. to show checkpoint
                        if (!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
                            mc.setScreen(null);
                        }
                    }

                    // Open checkpoint screen if no screen is active
                    if (mc.screen == null) {
                        mc.setScreen(new WaveCheckpointScreen());
                    }
                });
            }
        }

        // Reset flags when state changes away from WAVE_COMPLETE
        if (currentState != EnduranceQuestState.WAVE_COMPLETE) {
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
        int glowColor = (glowAlpha << 24) | 0xFF0000;

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
        g.fill(boxX, boxY, boxX + textWidth + boxPadding * 2, boxY + 32, 0xCC000000);

        // Border
        int borderColor = applyAlpha(0xFFFF4444, pulse);
        g.fill(boxX, boxY, boxX + textWidth + boxPadding * 2, boxY + 1, borderColor);
        g.fill(boxX, boxY + 31, boxX + textWidth + boxPadding * 2, boxY + 32, borderColor);
        g.fill(boxX, boxY, boxX + 1, boxY + 32, borderColor);
        g.fill(boxX + textWidth + boxPadding * 2 - 1, boxY, boxX + textWidth + boxPadding * 2, boxY + 32, borderColor);

        // Text pulsating
        int textAlpha = (int) (pulse * 255);
        int textColor = (textAlpha << 24) | 0xFF4444;
        g.drawCenteredString(font, warningText, centerX, boxY + 6, textColor);

        // Boss type below
        g.drawCenteredString(font, bossAlertType.toUpperCase(), centerX, boxY + 18, 0xFFAAAAAA);

        // Sound every second (first 100ms of each second)
        Minecraft mc = Minecraft.getInstance();
        long currentSecond = remaining / 1000;
        if (currentSecond != lastSoundTick && mc.player != null) {
            lastSoundTick = currentSecond;
            // Pitch increases as countdown approaches: 0.5 -> 1.5
            float pitch = 0.5f + (1.0f - remaining / (float) bossAlertDuration);
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, pitch);
        }
    }
}
