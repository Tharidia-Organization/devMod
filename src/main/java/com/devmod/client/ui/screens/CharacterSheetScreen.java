package com.devmod.client.ui.screens;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.abilities.DashAbilitySystem;
import com.devmod.abilities.DodgeAbilitySystem;
import com.devmod.abilities.SharedAbilityCooldown;
import com.devmod.client.endurance.ClientCombatFlowCache;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.endurance.ComboSystem;
import com.devmod.util.I18n;

/**
 * Character Sheet screen showing player stats and progression.
 * Tabs: Overview, Combat, Progression, Abilities.
 */
@OnlyIn(Dist.CLIENT)
public class CharacterSheetScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_GAP = 2;
    private static final int PADDING = 8;
    private static final int LINE_H = 12;
    private static final int SECTION_GAP = 10;

    private enum Tab { OVERVIEW, COMBAT, PROGRESSION, ABILITIES }

    private Tab activeTab = Tab.OVERVIEW;
    private int panelX, panelY, panelW, panelH;
    private int contentY;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Tab button regions
    private final int[] tabX = new int[4];
    private final int[] tabW = new int[4];
    private int tabY;

    public CharacterSheetScreen() {
        super(I18n.screenTitle("character_sheet"));
    }

    @Override
    protected void init() {
        panelW = UIScaleManager.scale(PANEL_WIDTH);
        panelH = this.height - UIScaleManager.scale(40);
        panelX = (this.width - panelW) / 2;
        panelY = UIScaleManager.scale(20);

        int scaledTabH = UIScaleManager.scale(TAB_HEIGHT);
        tabY = panelY;
        contentY = panelY + scaledTabH + 1;

        int tabCount = Tab.values().length;
        int gap = UIScaleManager.scale(TAB_GAP);
        int totalGaps = gap * (tabCount - 1);
        int singleTabW = (panelW - totalGaps) / tabCount;
        for (int i = 0; i < tabCount; i++) {
            tabX[i] = panelX + i * (singleTabW + gap);
            tabW[i] = singleTabW;
        }

        scrollOffset = 0;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        Font f = Objects.requireNonNull(font, "font");

        // Background
        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, DesignTokens.Background.PANEL);
        AxiomRenderer.drawSimplePanel(graphics, panelX, panelY, panelW, panelH);

        // Tabs
        renderTabs(graphics, f, mouseX, mouseY);

        // Content area (clipped)
        int contentH = panelY + panelH - contentY - UIScaleManager.scale(PADDING);
        graphics.enableScissor(panelX, contentY, panelX + panelW, contentY + contentH);

        int drawn = switch (activeTab) {
            case OVERVIEW -> renderOverview(graphics, f, panelX, contentY - scrollOffset, panelW);
            case COMBAT -> renderCombat(graphics, f, panelX, contentY - scrollOffset, panelW);
            case PROGRESSION -> renderProgression(graphics, f, panelX, contentY - scrollOffset, panelW);
            case ABILITIES -> renderAbilities(graphics, f, panelX, contentY - scrollOffset, panelW);
        };

        maxScroll = Math.max(0, drawn - contentH);
        graphics.disableScissor();

        // Scrollbar indicator
        if (maxScroll > 0) {
            renderScrollbar(graphics, panelX + panelW - UIScaleManager.scale(3), contentY, UIScaleManager.scale(3), contentH, contentH, drawn);
        }

        // Footer hint
        int hintY = panelY + panelH + UIScaleManager.scale(4);
        UIScaleManager.drawScaledString(graphics, f, "ESC to close", (this.width - UIScaleManager.getScaledStringWidth(f, "ESC to close")) / 2, hintY, OverlayTheme.Text.HINT, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderTabs(GuiGraphics graphics, Font f, int mx, int my) {
        String[] labels = {
            I18n.translate("devmod.character_sheet.tab.overview").getString(),
            I18n.translate("devmod.character_sheet.tab.combat").getString(),
            I18n.translate("devmod.character_sheet.tab.progression").getString(),
            I18n.translate("devmod.character_sheet.tab.abilities").getString()
        };
        Tab[] tabs = Tab.values();
        int scaledTabH = UIScaleManager.scale(TAB_HEIGHT);

        for (int i = 0; i < tabs.length; i++) {
            boolean selected = activeTab == tabs[i];
            boolean hovered = mx >= tabX[i] && mx < tabX[i] + tabW[i] && my >= tabY && my < tabY + scaledTabH;
            AxiomRenderer.drawTab(graphics, f, tabX[i], tabY, tabW[i], scaledTabH, labels[i], selected, hovered);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OVERVIEW TAB
    // ═══════════════════════════════════════════════════════════════════════════

    private int renderOverview(GuiGraphics graphics, Font f, int x, int startY, int w) {
        int pad = UIScaleManager.scale(PADDING);
        int lineH = UIScaleManager.scale(LINE_H);
        int secGap = UIScaleManager.scale(SECTION_GAP);
        int cx = x + pad;
        int cw = w - pad * 2;
        int y = startY + pad;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return y - startY;

        // Player model preview
        int modelSize = UIScaleManager.scale(50);
        int modelX = cx + cw / 2;
        int modelY = y + modelSize + UIScaleManager.scale(10);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            graphics, cx, y, cx + cw, modelY + UIScaleManager.scale(5),
            modelSize, 0.0625f, (float) modelX, (float) (y + modelSize / 2), player
        );
        y = modelY + UIScaleManager.scale(10);

        // Player name and level
        String name = player.getName().getString();
        drawCenteredLabel(graphics, f, cx, y, cw, name, OverlayTheme.Text.TITLE);
        y += lineH + 2;

        int level = player.experienceLevel;
        drawCenteredLabel(graphics, f, cx, y, cw, I18n.translate("devmod.character_sheet.level", level).getString(), OverlayTheme.Text.VALUE);
        y += lineH + secGap;

        // Quick summary section
        y = drawSectionHeader(graphics, f, cx, y, I18n.translate("devmod.character_sheet.section.quick_summary").getString());
        y += 4;

        float maxHealth = player.getMaxHealth();
        float health = player.getHealth();
        int armor = player.getArmorValue();
        int food = player.getFoodData().getFoodLevel();

        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.health"), String.format("%.0f / %.0f", health, maxHealth), OverlayTheme.Text.VALUE);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.armor"), String.valueOf(armor), OverlayTheme.Text.VALUE);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.food_level"), food + " / 20", OverlayTheme.Text.VALUE);

        // Style rank from combat flow cache
        ComboSystem.StyleRank rank = ClientCombatFlowCache.INSTANCE.getStyleRank();
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.style_rank"), rank.name() + " - " + rank.getDisplayName(), rank.getColor());
        y += secGap;

        // Equipment section
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.equipment"));
        y += 4;

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (!mainHand.isEmpty()) {
            y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.main_hand"), mainHand.getHoverName().getString(), OverlayTheme.Text.PRIMARY);
        }
        if (!offHand.isEmpty()) {
            y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.off_hand"), offHand.getHoverName().getString(), OverlayTheme.Text.PRIMARY);
        }

        // Armor slots
        for (ItemStack armorPiece : player.getArmorSlots()) {
            if (!armorPiece.isEmpty()) {
                y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.armor"), armorPiece.getHoverName().getString(), OverlayTheme.Text.PRIMARY);
            }
        }

        y += pad;
        return y - startY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT TAB
    // ═══════════════════════════════════════════════════════════════════════════

    private int renderCombat(GuiGraphics graphics, Font f, int x, int startY, int w) {
        int pad = UIScaleManager.scale(PADDING);
        int secGap = UIScaleManager.scale(SECTION_GAP);
        int cx = x + pad;
        int cw = w - pad * 2;
        int y = startY + pad;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return y - startY;
        UUID playerId = player.getUUID();

        // Combat Stats from client cache
        ClientCombatFlowCache cache = ClientCombatFlowCache.INSTANCE;

        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.combat_performance"));
        y += 4;

        int combo = cache.getCombo();
        int maxCombo = cache.getMaxCombo();
        ComboSystem.StyleRank rank = cache.getStyleRank();

        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.current_combo"), String.valueOf(combo), OverlayTheme.Text.VALUE);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.best_combo"), String.valueOf(maxCombo), OverlayTheme.Text.VALUE_BRIGHT);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.style_score"), String.format("%,.0f", cache.getStyleScore()), OverlayTheme.Text.VALUE);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.current_style_rank"), rank.name() + " - " + rank.getDisplayName(), rank.getColor());
        y += secGap;

        // Defensive stats
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.defensive_stats"));
        y += 4;

        DodgeAbilitySystem.DodgeData dodgeData = DodgeAbilitySystem.INSTANCE.getDodgeData(playerId);
        int perfectDodges = dodgeData.perfectDodgeCount;
        int totalDodges = dodgeData.dodgeCount;
        float dodgeRate = totalDodges > 0 ? (float) perfectDodges / totalDodges * 100f : 0f;

        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.perfect_dodges"), String.valueOf(perfectDodges), OverlayTheme.Text.CYAN);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.total_dodges"), String.valueOf(totalDodges), OverlayTheme.Text.VALUE);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.perfect_dodge_rate"), String.format("%.1f%%", dodgeRate), OverlayTheme.Text.GOLD);
        y += secGap;

        // Combat flow
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.flow_state"));
        y += 4;

        String flowState = cache.getFlowState().name();
        float momentum = cache.getMomentumPercent();
        String momentumState = cache.getMomentumState().name();
        float virtuoso = cache.getVirtuosoProgress() * 100f;

        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.flow_state"), flowState, OverlayTheme.Text.CYAN);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.momentum"), String.format("%.0f%% (%s)", momentum, momentumState), OverlayTheme.Text.VALUE);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.virtuoso_progress"), String.format("%.1f%%", virtuoso), OverlayTheme.Text.PURPLE);

        if (cache.isInOverdrive()) {
            long remaining = cache.getOverdriveRemainingMs() / 1000;
            y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.overdrive"), remaining + "s remaining", OverlayTheme.Text.DANGER_BRIGHT);
        }

        y += pad;
        return y - startY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROGRESSION TAB
    // ═══════════════════════════════════════════════════════════════════════════

    private int renderProgression(GuiGraphics graphics, Font f, int x, int startY, int w) {
        int pad = UIScaleManager.scale(PADDING);
        int secGap = UIScaleManager.scale(SECTION_GAP);
        int cx = x + pad;
        int cw = w - pad * 2;
        int y = startY + pad;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return y - startY;

        // Season Pass
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.season_pass"));
        y += 4;

        int xpLevel = player.experienceLevel;
        float xpProg = player.experienceProgress;
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.xp_level"), String.valueOf(xpLevel), OverlayTheme.Text.VALUE);

        // XP progress bar
        y = drawProgressBar(graphics, cx, y, cw, xpProg, OverlayTheme.Progress.FILL_CYAN);
        y += 4;

        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.xp_progress"), String.format("%.0f%%", xpProg * 100), OverlayTheme.Text.VALUE);
        y += secGap;

        // Player stats
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.player_statistics"));
        y += 4;

        // Use player's score as a proxy for some stats
        int score = player.getScore();
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.score"), String.format("%,d", score), OverlayTheme.Text.GOLD);

        // Playtime (approximation from level * 20 minutes)
        int playMinutes = xpLevel * 20;
        int hours = playMinutes / 60;
        int minutes = playMinutes % 60;
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.est_playtime"), hours + "h " + minutes + "m", OverlayTheme.Text.MUTED);
        y += secGap;

        // Achievements section
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.milestones"));
        y += 4;

        ComboSystem.StyleRank bestRank = ClientCombatFlowCache.INSTANCE.getStyleRank();
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.best_style_rank"), bestRank.name(), bestRank.getColor());

        int maxCombo = ClientCombatFlowCache.INSTANCE.getMaxCombo();
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.best_combo"), String.valueOf(maxCombo), OverlayTheme.Text.VALUE_BRIGHT);

        y += pad;
        return y - startY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ABILITIES TAB
    // ═══════════════════════════════════════════════════════════════════════════

    private int renderAbilities(GuiGraphics graphics, Font f, int x, int startY, int w) {
        int pad = UIScaleManager.scale(PADDING);
        int secGap = UIScaleManager.scale(SECTION_GAP);
        int cx = x + pad;
        int cw = w - pad * 2;
        int y = startY + pad;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return y - startY;
        UUID playerId = player.getUUID();

        // Dash ability
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.dash"));
        y += 4;

        DashAbilitySystem.DashData dashData = DashAbilitySystem.INSTANCE.getDashData(playerId);
        int dashCharges = SharedAbilityCooldown.INSTANCE.getAvailableCharges(playerId);
        int dashMaxCharges = SharedAbilityCooldown.INSTANCE.getMaxCharges(playerId);
        float dashCd = DashAbilitySystem.INSTANCE.getCooldownPercent(playerId);

        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.charges"), dashCharges + " / " + dashMaxCharges, OverlayTheme.Text.VALUE);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.cooldown"), dashCd > 0 ? String.format("%.0f%%", dashCd * 100) : t("devmod.character_sheet.stat.ready"), dashCd > 0 ? OverlayTheme.Text.WARNING : OverlayTheme.Text.VALUE);

        // Cooldown bar
        if (dashCd > 0) {
            y = drawProgressBar(graphics, cx, y, cw, 1.0f - dashCd, OverlayTheme.Progress.FILL_CYAN);
            y += 2;
        }

        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.total_dashes"), String.valueOf(dashData.dashCount), OverlayTheme.Text.PRIMARY);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.currently_dashing"), dashData.isDashing ? t("devmod.character_sheet.stat.yes") : t("devmod.character_sheet.stat.no"), dashData.isDashing ? OverlayTheme.Text.VALUE_BRIGHT : OverlayTheme.Text.MUTED);
        y += secGap;

        // Dodge ability
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.dodge"));
        y += 4;

        DodgeAbilitySystem.DodgeData dodgeData = DodgeAbilitySystem.INSTANCE.getDodgeData(playerId);
        float dodgeCd = DodgeAbilitySystem.INSTANCE.getCooldownPercent(playerId);

        int dodgeCharges = SharedAbilityCooldown.INSTANCE.getAvailableCharges(playerId);
        int dodgeMaxCharges = SharedAbilityCooldown.INSTANCE.getMaxCharges(playerId);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.charges"), dodgeCharges + " / " + dodgeMaxCharges, OverlayTheme.Text.VALUE);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.cooldown"), dodgeCd > 0 ? String.format("%.0f%%", dodgeCd * 100) : t("devmod.character_sheet.stat.ready"), dodgeCd > 0 ? OverlayTheme.Text.WARNING : OverlayTheme.Text.VALUE);

        if (dodgeCd > 0) {
            y = drawProgressBar(graphics, cx, y, cw, 1.0f - dodgeCd, OverlayTheme.Progress.FILL_CYAN);
            y += 2;
        }

        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.total_dodges"), String.valueOf(dodgeData.dodgeCount), OverlayTheme.Text.PRIMARY);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.perfect_dodges"), String.valueOf(dodgeData.perfectDodgeCount), OverlayTheme.Text.CYAN);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.iframes_active"), dodgeData.iframesTicks > 0 ? t("devmod.character_sheet.stat.yes") + " (" + dodgeData.iframesTicks + " ticks)" : t("devmod.character_sheet.stat.no"), dodgeData.iframesTicks > 0 ? OverlayTheme.Text.VALUE_BRIGHT : OverlayTheme.Text.MUTED);

        if (dodgeData.counterAttackWindowTicks > 0) {
            y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.counter_window"), dodgeData.counterAttackWindowTicks + " ticks", OverlayTheme.Text.GOLD);
        }
        y += secGap;

        // Food / Hunger status
        y = drawSectionHeader(graphics, f, cx, y, t("devmod.character_sheet.section.status"));
        y += 4;

        int foodLevel = player.getFoodData().getFoodLevel();
        float saturation = player.getFoodData().getSaturationLevel();
        int foodColor = foodLevel >= 14 ? OverlayTheme.Text.VALUE : foodLevel >= 6 ? OverlayTheme.Text.WARNING : OverlayTheme.Text.DANGER;
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.food_level"), foodLevel + " / 20", foodColor);
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.saturation"), String.format("%.1f", saturation), OverlayTheme.Text.VALUE);

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float healthPct = health / maxHealth;
        int healthColor = healthPct > 0.6f ? OverlayTheme.Text.VALUE : healthPct > 0.3f ? OverlayTheme.Text.WARNING : OverlayTheme.Text.DANGER;
        y = drawStatRow(graphics, f, cx, y, cw, t("devmod.character_sheet.stat.health"), String.format("%.0f / %.0f", health, maxHealth), healthColor);
        y = drawProgressBar(graphics, cx, y, cw, healthPct, OverlayTheme.Progress.byRatio(healthPct));

        y += pad;
        return y - startY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RENDERING HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private int drawSectionHeader(GuiGraphics graphics, Font f, int x, int y, String title) {
        int lineH = UIScaleManager.scale(LINE_H);
        UIScaleManager.drawScaledString(graphics, f, title, x, y, OverlayTheme.Text.TITLE, false);
        y += lineH;
        // Divider line
        int divW = UIScaleManager.getScaledStringWidth(f, title) + UIScaleManager.scale(20);
        graphics.fill(x, y, x + divW, y + 1, OverlayTheme.Border.ACCENT);
        return y + 2;
    }

    private int drawStatRow(GuiGraphics graphics, Font f, int x, int y, int w, String label, String value, int valueColor) {
        int lineH = UIScaleManager.scale(LINE_H);
        UIScaleManager.drawScaledString(graphics, f, label, x, y, OverlayTheme.Text.MUTED, false);
        int valW = UIScaleManager.getScaledStringWidth(f, value);
        UIScaleManager.drawScaledString(graphics, f, value, x + w - valW, y, valueColor, false);
        return y + lineH;
    }

    private int drawProgressBar(GuiGraphics graphics, int x, int y, int w, float progress, int fillColor) {
        int barH = UIScaleManager.scale(4);
        // Background
        graphics.fill(x, y, x + w, y + barH, OverlayTheme.Progress.BG);
        // Fill
        int fillW = (int) (w * Math.max(0, Math.min(1, progress)));
        if (fillW > 0) {
            graphics.fill(x, y, x + fillW, y + barH, fillColor);
        }
        return y + barH + 2;
    }

    private void drawCenteredLabel(GuiGraphics graphics, Font f, int x, int y, int w, String text, int color) {
        int textW = UIScaleManager.getScaledStringWidth(f, text);
        UIScaleManager.drawScaledString(graphics, f, text, x + (w - textW) / 2, y, color, false);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int w, int viewH, int contentH, int totalH) {
        if (totalH <= viewH) return;
        float thumbRatio = (float) viewH / totalH;
        int thumbH = Math.max(UIScaleManager.scale(10), (int) (viewH * thumbRatio));
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = y + (int) ((viewH - thumbH) * scrollRatio);

        graphics.fill(x, y, x + w, y + viewH, OverlayTheme.withAlpha(OverlayTheme.Utility.BLACK, 60));
        graphics.fill(x, thumbY, x + w, thumbY + thumbH, OverlayTheme.withAlpha(OverlayTheme.Text.MUTED, 120));
    }

    /** Shorthand for translating a key to a string. */
    private static String t(String key) {
        return I18n.translate(key).getString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int scaledTabH = UIScaleManager.scale(TAB_HEIGHT);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i] && mouseY >= tabY && mouseY < tabY + scaledTabH) {
                activeTab = tabs[i];
                scrollOffset = 0;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * UIScaleManager.scale(12))));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, DesignTokens.Background.SCREEN);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Disable blur
    }

    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, DesignTokens.Background.SCREEN);
    }
}
