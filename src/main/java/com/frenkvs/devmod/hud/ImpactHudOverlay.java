package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * HUD Overlay per mostrare l'analisi dell'impatto in tempo reale.
 *
 * Registrato sopra il crosshair, mostra:
 * - Impact Analysis Panel (breakdown danno)
 * - Mod Specifics Panel (Pehkui, Better Combat)
 * - Entity Info (opzionale)
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
// Minecraft API methods are not annotated but never return null in practice
@SuppressWarnings("null")
public class ImpactHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "impact_analysis");

    // === Colori UI (stile immagine di riferimento) ===
    private static final int PANEL_BG = 0xCC1A1A2E;           // Blu scuro 80% opacity
    private static final int PANEL_BORDER = 0xFF3D5AFE;       // Blu elettrico
    private static final int PANEL_BORDER_GLOW = 0x553D5AFE;  // Glow border

    private static final int TEXT_TITLE = 0xFF00FFFF;         // Cyan
    private static final int TEXT_NORMAL = 0xFFFFFFFF;        // Bianco
    private static final int TEXT_VALUE = 0xFF00FF00;         // Verde
    private static final int TEXT_FORMULA = 0xFFFFD700;       // Oro
    private static final int TEXT_MUTED = 0xFFAAAAAA;         // Grigio

    // === Dimensioni ===
    private static final int PANEL_PADDING = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int SECTION_SPACING = 6;

    // === Toggle (initialized from config) ===
    private static boolean enabled = getConfigEnabled();

    // === Posizione ultimo pannello renderizzato (per hit-test crosshair) ===
    private static int lastPanelX = 0;
    private static int lastPanelY = 0;
    private static int lastPanelWidth = 0;
    private static int lastPanelHeight = 0;

    private static boolean getConfigEnabled() {
        try { return Config.IMPACT_HUD_ENABLED.get(); }
        catch (Exception e) { return true; }
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CROSSHAIR,
            LAYER_ID,
            ImpactHudOverlay::render
        );
    }

    /**
     * Metodo di rendering principale chiamato ogni frame.
     * NeoForge 1.21: usa DeltaTracker invece di float partialTick
     */
    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled) return;

        ImpactData data = ImpactData.get();
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Font font = mc.font;

        // Calcola dimensioni pannello
        int panelWidth = 260;
        int panelHeight = calculatePanelHeight(data, font);

        // Posizione: angolo destro con margine
        int panelX = screenWidth - panelWidth - 10;
        int panelY = 10;

        // Calcola altezza totale (include mod specifics se presente)
        int totalHeight = panelHeight;
        if (data.hasPehkuiModification() || data.isBetterCombatAttack()) {
            int modHeight = (data.hasPehkuiModification() && data.isBetterCombatAttack()) ? 52 : 40;
            totalHeight += 8 + modHeight;
        }

        // Salva posizione per hit-test
        lastPanelX = panelX;
        lastPanelY = panelY;
        lastPanelWidth = panelWidth;
        lastPanelHeight = totalHeight;

        // Controlla se il crosshair (centro schermo) è sopra il pannello
        int crosshairX = screenWidth / 2;
        int crosshairY = screenHeight / 2;
        boolean isObserving = isCrosshairOverPanel(crosshairX, crosshairY);

        // Aggiorna stato osservazione
        data.setObserved(isObserving);

        float alpha = data.getRemainingAlpha();
        if (alpha <= 0.01f) return;

        // Render Impact Analysis Panel
        renderImpactPanel(graphics, font, data, panelX, panelY, panelWidth, panelHeight, alpha);

        // Render Mod Specifics Panel (sotto il primo)
        if (data.hasPehkuiModification() || data.isBetterCombatAttack()) {
            int modPanelY = panelY + panelHeight + 8;
            renderModSpecificsPanel(graphics, font, data, panelX, modPanelY, panelWidth, alpha);
        }
    }

    /**
     * Controlla se il crosshair è sopra il pannello Impact HUD.
     */
    private static boolean isCrosshairOverPanel(int crosshairX, int crosshairY) {
        return crosshairX >= lastPanelX && crosshairX <= lastPanelX + lastPanelWidth &&
               crosshairY >= lastPanelY && crosshairY <= lastPanelY + lastPanelHeight;
    }

    /**
     * Renderizza il pannello principale "Impact Analysis".
     */
    private static void renderImpactPanel(GuiGraphics g, Font font, ImpactData data,
                                          int x, int y, int width, int height, float alpha) {
        // Sfondo con bordo
        renderPanelBackground(g, x, y, width, height, alpha);

        int textX = x + PANEL_PADDING;
        int textY = y + PANEL_PADDING;

        // === Titolo ===
        String title = "Impact Analysis (Multi-Part & Mod Integrated)";
        g.drawString(font, title, textX, textY, applyAlpha(TEXT_TITLE, alpha), false);
        textY += LINE_HEIGHT + 2;

        // Linea separatore
        g.fill(x + 4, textY, x + width - 4, textY + 1, applyAlpha(PANEL_BORDER, alpha * 0.5f));
        textY += SECTION_SPACING;

        // === Part Hit ===
        Component partHit = I18n.translate("devmod.hud.part_hit").append(Component.literal(": "))
            .append(Component.literal(data.bodyPart.name()).withStyle(s -> s.withColor(data.getBodyPartColor())))
            .append(Component.literal(" (")).append(I18n.translate("devmod.hud.modifier")).append(Component.literal(": "))
            .append(Component.literal(String.format("x%.2f", data.bodyPartMultiplier)).withStyle(s -> s.withColor(0x00FF00)))
            .append(Component.literal(")"));
        g.drawString(font, partHit, textX, textY, applyAlpha(TEXT_NORMAL, alpha), false);
        textY += LINE_HEIGHT + 2;

        // === Source ===
        g.drawString(font, "Source: " + data.getFormattedAttackSource(),
            textX, textY, applyAlpha(TEXT_MUTED, alpha), false);
        textY += LINE_HEIGHT + SECTION_SPACING;

        // === Breakdown ===
        DamageBreakdown bd = data.breakdown;

        // Base Weapon Dmg
        g.drawString(font,
            String.format("Base Weapon Dmg: %.2f", bd.baseWeaponDamage),
            textX, textY, applyAlpha(TEXT_NORMAL, alpha), false);
        textY += LINE_HEIGHT;

        // Enchants
        for (DamageBreakdown.EnchantBonus eb : bd.enchantBonuses) {
            if (eb.bonus() > 0) {
                g.drawString(font,
                    String.format("Enchant (%s): +%.2f", eb.name(), eb.bonus()),
                    textX, textY, applyAlpha(TEXT_VALUE, alpha), false);
                textY += LINE_HEIGHT;
            }
        }

        // Pehkui Size Bonus
        if (bd.pehkuiSizeBonus > 0) {
            g.drawString(font,
                String.format("Pehkui Size Bonus (+25%% of Base): +%.2f", bd.pehkuiSizeBonus),
                textX, textY, applyAlpha(TEXT_VALUE, alpha), false);
            textY += LINE_HEIGHT;
        }

        textY += SECTION_SPACING;

        // === Formula ===
        g.drawString(font, "Local Part Calc: " + bd.getFormulaString(),
            textX, textY, applyAlpha(TEXT_FORMULA, alpha), false);
        textY += LINE_HEIGHT + 2;

        // === Danno Reale (NUOVO) ===
        if (data.hasActualDamage()) {
            // Mostra danno reale con evidenziazione
            float actualDmg = data.getActualDamageDealt();
            float reduction = data.getDamageReduction();

            // Danno reale in grande
            String actualText = String.format("ACTUAL DAMAGE: %.2f", actualDmg);
            // Simula bold con shadow
            g.drawString(font, actualText, textX + 1, textY, applyAlpha(0x550000, alpha), false);
            g.drawString(font, actualText, textX, textY, applyAlpha(0xFF4444, alpha), false);
            textY += LINE_HEIGHT + 2;

            // Health bar info
            String healthText = String.format("HP: %.2f -> %.2f",
                data.getHealthBefore(), data.getHealthAfter());
            g.drawString(font, healthText, textX, textY, applyAlpha(TEXT_MUTED, alpha), false);
            textY += LINE_HEIGHT;

            // Mostra riduzione/amplificazione
            if (Math.abs(reduction) > 0.1f) {
                String reductionText;
                int reductionColor;
                if (reduction > 0) {
                    reductionText = String.format("Armor/Effects reduced: -%.2f", reduction);
                    reductionColor = 0xFF8888; // Rosso chiaro
                } else {
                    reductionText = String.format("Damage amplified: +%.2f", -reduction);
                    reductionColor = 0x88FF88; // Verde chiaro
                }
                g.drawString(font, reductionText, textX, textY, applyAlpha(reductionColor, alpha), false);
                textY += LINE_HEIGHT;
            }
        } else {
            // Fallback al danno calcolato (vecchio comportamento)
            String finalText = String.format("*Calculated Dmg: %.2f*", bd.finalDamage);
            g.drawString(font, finalText, textX + 1, textY, applyAlpha(0x005500, alpha), false);
            g.drawString(font, finalText, textX, textY, applyAlpha(TEXT_VALUE, alpha), false);
        }
    }

    /**
     * Renderizza il pannello "Mod Specifics".
     */
    private static void renderModSpecificsPanel(GuiGraphics g, Font font, ImpactData data,
                                                 int x, int y, int width, float alpha) {
        int height = 40;
        if (data.hasPehkuiModification() && data.isBetterCombatAttack()) {
            height = 52;
        }

        renderPanelBackground(g, x, y, width, height, alpha);

        int textX = x + PANEL_PADDING;
        int textY = y + PANEL_PADDING;

        // Titolo
        g.drawString(font, "Mod Specifics", textX, textY, applyAlpha(TEXT_TITLE, alpha), false);
        textY += LINE_HEIGHT + 4;

        // Better Combat
        if (data.isBetterCombatAttack()) {
            g.drawString(font, "Better Combat: Arc Collision Detected",
                textX, textY, applyAlpha(TEXT_MUTED, alpha), false);
            textY += LINE_HEIGHT;
        }

        // Pehkui
        if (data.hasPehkuiModification()) {
            String scaleText = String.format("Pehkui: Entity Size Modified (Scale %.2f)",
                data.pehkuiVisualScale != null ? data.pehkuiVisualScale : 1.0f);
            g.drawString(font, scaleText, textX, textY, applyAlpha(TEXT_MUTED, alpha), false);
        }
    }

    /**
     * Renderizza sfondo pannello con bordo glow.
     */
    private static void renderPanelBackground(GuiGraphics g, int x, int y, int width, int height, float alpha) {
        // Glow esterno (opzionale, effetto sottile)
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, applyAlpha(PANEL_BORDER_GLOW, alpha * 0.3f));

        // Sfondo principale
        g.fill(x, y, x + width, y + height, applyAlpha(PANEL_BG, alpha));

        // Bordo
        int borderColor = applyAlpha(PANEL_BORDER, alpha * 0.8f);
        g.fill(x, y, x + width, y + 1, borderColor);                    // Top
        g.fill(x, y + height - 1, x + width, y + height, borderColor);  // Bottom
        g.fill(x, y, x + 1, y + height, borderColor);                   // Left
        g.fill(x + width - 1, y, x + width, y + height, borderColor);   // Right
    }

    /**
     * Calcola altezza dinamica del pannello in base ai dati.
     */
    private static int calculatePanelHeight(ImpactData data, Font font) {
        int height = PANEL_PADDING * 2;  // Top + bottom padding

        height += LINE_HEIGHT + 2;  // Title
        height += SECTION_SPACING;  // Separator
        height += LINE_HEIGHT + 2;  // Part Hit
        height += LINE_HEIGHT + SECTION_SPACING;  // Source

        height += LINE_HEIGHT;  // Base Weapon Dmg

        // Enchants
        DamageBreakdown bd = data.breakdown;
        for (DamageBreakdown.EnchantBonus eb : bd.enchantBonuses) {
            if (eb.bonus() > 0) {
                height += LINE_HEIGHT;
            }
        }

        // Pehkui bonus
        if (bd.pehkuiSizeBonus > 0) {
            height += LINE_HEIGHT;
        }

        height += SECTION_SPACING;
        height += LINE_HEIGHT;  // Formula

        // Sezione danno reale (più righe)
        if (data.hasActualDamage()) {
            height += LINE_HEIGHT + 2;  // ACTUAL DAMAGE
            height += LINE_HEIGHT;      // HP: before -> after
            if (Math.abs(data.getDamageReduction()) > 0.1f) {
                height += LINE_HEIGHT;  // Armor/Effects reduced
            }
        } else {
            height += LINE_HEIGHT + 2;  // Calculated Dmg (fallback)
        }

        return height;
    }

    /**
     * Applica alpha a un colore ARGB.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
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

    /**
     * Returns the Y coordinate of the bottom of the last rendered panel.
     * Used by other overlays to position themselves below this one.
     */
    public static int getLastPanelBottom() {
        return lastPanelY + lastPanelHeight;
    }
}
