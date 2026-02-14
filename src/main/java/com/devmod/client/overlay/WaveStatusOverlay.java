package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.util.I18n;

/**
 * Compact arena Wave Status HUD overlay.
 *
 * Displays at top-center of the screen:
 * - Wave X/Y indicator
 * - Enemy count remaining
 * - Active wave modifier names
 *
 * Only visible during active arena (endurance quest) sessions.
 */
@OnlyIn(Dist.CLIENT)
public class WaveStatusOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "wave_status_hud");

    public static final WaveStatusOverlay INSTANCE = new WaveStatusOverlay();

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.BOSS_OVERLAY),
            Objects.requireNonNull(LAYER_ID),
            (graphics, deltaTracker) -> INSTANCE.render(graphics, deltaTracker)
        );
    }

    // Dimensions
    private static final int BAR_HEIGHT = 22;
    private static final int PADDING = OverlayTheme.Dimension.PADDING;
    private static final int MODIFIER_GAP = 6;

    // Colors
    private static final int BG_COLOR = OverlayTheme.Panel.BG_STANDARD;
    private static final int BORDER_COLOR = OverlayTheme.Border.ENDURANCE;
    private static final int TEXT_WAVE = OverlayTheme.Endurance.LIGHT;
    private static final int TEXT_ENEMIES = OverlayTheme.Text.PRIMARY;
    private static final int TEXT_MODIFIER = OverlayTheme.Text.MUTED;
    private static final int SEPARATOR_COLOR = OverlayTheme.Border.MUTED;

    private WaveStatusOverlay() {}

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        UIScaleManager.update();

        // Only show during active quest
        if (!ClientQuestCache.hasActiveQuest()) {
            return;
        }

        QuestSyncPayload data = ClientQuestCache.getData();
        if (data == null) {
            return;
        }

        EnduranceQuestState state = data.getState();
        if (state != EnduranceQuestState.IN_PROGRESS && state != EnduranceQuestState.WAVE_COMPLETE) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        Font font = Objects.requireNonNull(mc.font, "Font cannot be null");
        int screenWidth = graphics.guiWidth();

        // Build display strings
        String waveText;
        if (data.endlessMode()) {
            waveText = I18n.translate("devmod.wave_status.wave_endless", data.currentWave()).getString();
        } else {
            waveText = I18n.translate("devmod.wave_status.wave", data.currentWave(), data.totalWaves()).getString();
        }

        int enemiesRemaining = data.totalMobsInWave() - data.mobsKilledInWave();
        if (enemiesRemaining < 0) enemiesRemaining = 0;
        String enemyText = I18n.translate("devmod.wave_status.enemies", enemiesRemaining).getString();

        List<String> modifiers = data.waveModifiers();

        // Calculate total bar width
        int sPadding = UIScaleManager.scale(PADDING);
        int sModifierGap = UIScaleManager.scale(MODIFIER_GAP);
        int sBarHeight = UIScaleManager.scale(BAR_HEIGHT);

        int waveWidth = UIScaleManager.getScaledStringWidth(font, waveText);
        int enemyWidth = UIScaleManager.getScaledStringWidth(font, enemyText);
        int separatorWidth = UIScaleManager.scale(1);
        int separatorPadding = UIScaleManager.scale(8);

        int totalWidth = sPadding + waveWidth + separatorPadding + separatorWidth + separatorPadding + enemyWidth;

        // Add modifier widths
        if (modifiers != null && !modifiers.isEmpty()) {
            totalWidth += separatorPadding + separatorWidth + separatorPadding;
            for (int i = 0; i < modifiers.size(); i++) {
                if (i > 0) totalWidth += sModifierGap;
                totalWidth += UIScaleManager.getScaledStringWidth(font, modifiers.get(i));
            }
        }
        totalWidth += sPadding;

        // Center horizontally
        int x = (screenWidth - totalWidth) / 2;
        int y = UIScaleManager.scale(2);

        // === BACKGROUND ===
        graphics.fill(x, y, x + totalWidth, y + sBarHeight, BG_COLOR);

        // Border (bottom line only for compact look)
        graphics.fill(x, y + sBarHeight - 1, x + totalWidth, y + sBarHeight, BORDER_COLOR);

        // === WAVE TEXT ===
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int textY = y + (sBarHeight - lineHeight) / 2;
        int cursorX = x + sPadding;

        UIScaleManager.drawScaledString(graphics, font, waveText, cursorX, textY, TEXT_WAVE, true);
        cursorX += waveWidth;

        // Separator
        cursorX += separatorPadding;
        graphics.fill(cursorX, y + UIScaleManager.scale(4), cursorX + separatorWidth,
            y + sBarHeight - UIScaleManager.scale(4), SEPARATOR_COLOR);
        cursorX += separatorWidth + separatorPadding;

        // === ENEMY COUNT ===
        UIScaleManager.drawScaledString(graphics, font, enemyText, cursorX, textY, TEXT_ENEMIES, false);
        cursorX += enemyWidth;

        // === MODIFIERS ===
        if (modifiers != null && !modifiers.isEmpty()) {
            cursorX += separatorPadding;
            graphics.fill(cursorX, y + UIScaleManager.scale(4), cursorX + separatorWidth,
                y + sBarHeight - UIScaleManager.scale(4), SEPARATOR_COLOR);
            cursorX += separatorWidth + separatorPadding;

            for (int i = 0; i < modifiers.size(); i++) {
                String mod = modifiers.get(i);
                if (i > 0) cursorX += sModifierGap;

                int modColor = getModifierColor(mod);
                UIScaleManager.drawScaledString(graphics, font, mod, cursorX, textY, modColor, false);
                cursorX += UIScaleManager.getScaledStringWidth(font, mod);
            }
        }
    }

    /**
     * Map modifier names to affix theme colors.
     */
    private int getModifierColor(String modifier) {
        String lower = modifier.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("swift") || lower.contains("speed")) return OverlayTheme.Affix.SWIFT;
        if (lower.contains("empowered") || lower.contains("power")) return OverlayTheme.Affix.EMPOWERED;
        if (lower.contains("fortified") || lower.contains("tough")) return OverlayTheme.Affix.FORTIFIED;
        if (lower.contains("armored") || lower.contains("armor")) return OverlayTheme.Affix.ARMORED;
        if (lower.contains("blazing") || lower.contains("fire")) return OverlayTheme.Affix.BLAZING;
        if (lower.contains("phantom") || lower.contains("invis")) return OverlayTheme.Affix.PHANTOM;
        if (lower.contains("regen")) return OverlayTheme.Affix.REGENERATING;
        if (lower.contains("horde") || lower.contains("swarm")) return OverlayTheme.Affix.HORDE;
        return TEXT_MODIFIER;
    }
}
