package com.frenkvs.devmod.attributes;

import com.frenkvs.devmod.DevMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

/**
 * Overlay HUD 2D per il sistema di monitoraggio attributi.
 * Mostra un pannello laterale con:
 * - Attributi del target primario
 * - Lista entità tracciate
 * - Log history degli eventi
 *
 * Ispirato all'immagine di riferimento "Attribute Monitoring System"
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
@SuppressWarnings("null")
public class AttributeHudOverlay {
    public static final AttributeHudOverlay INSTANCE = new AttributeHudOverlay();

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "attribute_monitor");

    // === Colori UI (Impact UI Style) ===
    private static final int PANEL_BG = 0xCC1A1A2E;           // Blu scuro 80% opacity
    private static final int PANEL_BORDER = 0xFF3D5AFE;       // Blu elettrico Impact
    private static final int BORDER_GLOW = 0x553D5AFE;        // Glow effect
    private static final int TITLE_COLOR = 0xFF00FFFF;        // Cyan Impact
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GREEN = 0xFF00FF00;         // Verde Impact
    private static final int TEXT_YELLOW = 0xFFFFD700;        // Oro Impact
    private static final int TEXT_RED = 0xFFFF4444;           // Rosso Impact
    private static final int TEXT_GRAY = 0xFFAAAAAA;          // Grigio muted
    private static final int TEXT_ORANGE = 0xFFFF9800;        // Arancione

    // === Layout ===
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_MARGIN = 10;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_GAP = 8;

    private AttributeHudOverlay() {}

    /**
     * Registra il layer GUI.
     */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CROSSHAIR,
            LAYER_ID,
            AttributeHudOverlay::render
        );
    }

    /**
     * Renderizza l'overlay HUD.
     * Chiamato ogni frame quando il layer è attivo.
     */
    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!AttributeMonitoringSystem.INSTANCE.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Posizione pannello (lato destro)
        int panelX = screenWidth - PANEL_WIDTH - PANEL_MARGIN;
        int panelY = PANEL_MARGIN;

        // Calcola altezza dinamica
        int panelHeight = calculatePanelHeight();

        // === SFONDO ===
        renderBackground(graphics, panelX, panelY, PANEL_WIDTH, panelHeight);

        // === CONTENUTO ===
        int y = panelY + PADDING;
        int textX = panelX + PADDING;
        int contentWidth = PANEL_WIDTH - PADDING * 2;

        // TITOLO
        graphics.drawString(font, "§b§lAttribute Monitoring", textX, y, TITLE_COLOR, false);
        y += LINE_HEIGHT + 2;

        // Linea separatore
        graphics.fill(textX, y, textX + contentWidth, y + 1, PANEL_BORDER);
        y += SECTION_GAP;

        // === TARGET PRIMARIO ===
        TrackedEntity target = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();
        if (target != null && target.isValid()) {
            y = renderTargetSection(graphics, font, textX, y, contentWidth, target);
        } else {
            graphics.drawString(font, "§7No target selected", textX, y, TEXT_GRAY, false);
            y += LINE_HEIGHT + SECTION_GAP;
        }

        // Separatore
        graphics.fill(textX, y, textX + contentWidth, y + 1, PANEL_BORDER & 0x77FFFFFF);
        y += SECTION_GAP;

        // === ENTITÀ TRACCIATE ===
        y = renderTrackedListSection(graphics, font, textX, y, contentWidth);

        // Separatore
        graphics.fill(textX, y, textX + contentWidth, y + 1, PANEL_BORDER & 0x77FFFFFF);
        y += SECTION_GAP;

        // === LOG HISTORY ===
        renderLogSection(graphics, font, textX, y, contentWidth);
    }

    private static int calculatePanelHeight() {
        int height = PADDING * 2 + LINE_HEIGHT + 2 + SECTION_GAP; // Titolo

        TrackedEntity target = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();
        if (target != null && target.isValid()) {
            height += LINE_HEIGHT * 9 + SECTION_GAP; // Sezione target
        } else {
            height += LINE_HEIGHT + SECTION_GAP;
        }

        height += SECTION_GAP; // Separatore

        // Lista tracciati (max 5)
        int trackedCount = Math.min(AttributeMonitoringSystem.INSTANCE.getTrackedCount(), 5);
        height += LINE_HEIGHT + (LINE_HEIGHT * trackedCount) + SECTION_GAP;

        height += SECTION_GAP; // Separatore

        // Log (max 8 entries)
        int logCount = Math.min(AttributeMonitoringSystem.INSTANCE.getLogHistory().size(), 8);
        height += LINE_HEIGHT + (LINE_HEIGHT * Math.max(logCount, 1)) + SECTION_GAP;

        return height;
    }

    private static int renderTargetSection(GuiGraphics graphics, Font font, int x, int y, int width, TrackedEntity target) {
        // Nome target
        String nameStr = "§f" + target.getEntityName();
        if (target.hasLineOfSight()) {
            nameStr += " §a[LoS]";
        } else {
            nameStr += " §c[BLOCKED]";
        }
        graphics.drawString(font, nameStr, x, y, TEXT_WHITE, false);
        y += LINE_HEIGHT;

        // Health bar
        float healthPercent = target.getHealthPercent();
        int healthColor = healthPercent > 50 ? TEXT_GREEN : (healthPercent > 25 ? TEXT_YELLOW : TEXT_RED);
        String healthStr = String.format("HP: %.1f/%.1f (%.0f%%)",
            target.getCurrentHealth(), target.getMaxHealth(), healthPercent);
        graphics.drawString(font, healthStr, x, y, healthColor, false);
        y += LINE_HEIGHT;

        // Health bar grafica
        int barWidth = width - 4;
        int barHeight = 4;
        int barX = x + 2;
        graphics.fill(barX, y, barX + barWidth, y + barHeight, 0xFF333333); // BG
        int filledWidth = (int) (barWidth * (healthPercent / 100f));
        graphics.fill(barX, y, barX + filledWidth, y + barHeight, healthColor); // Fill
        y += barHeight + 4;

        // Armor
        String armorStr = String.format("Armor: %.1f (Tough: %.1f)",
            target.getArmorValue(), target.getArmorToughness());
        graphics.drawString(font, armorStr, x, y, TEXT_GRAY, false);
        y += LINE_HEIGHT;

        // Attack stats
        String attackStr = String.format("ATK: %.1f | SPD: %.2f",
            target.getAttackDamage(), target.getAttackSpeed());
        graphics.drawString(font, attackStr, x, y, TEXT_ORANGE, false);
        y += LINE_HEIGHT;

        // Movement
        String moveStr = String.format("Move: %.3f | KB Res: %.0f%%",
            target.getMovementSpeed(), target.getKnockbackResistance() * 100);
        graphics.drawString(font, moveStr, x, y, TEXT_GRAY, false);
        y += LINE_HEIGHT;

        // Distance
        double dist = target.getDistanceToPlayer();
        String distStr = String.format("Distance: %.1f blocks", dist);
        graphics.drawString(font, distStr, x, y, TEXT_GRAY, false);
        y += LINE_HEIGHT;

        // Pehkui (se presente)
        if (target.hasPehkuiModification()) {
            Float scale = target.getPehkuiScale();
            String scaleStr = String.format("§dPehkui Scale: %.2fx", scale != null ? scale : 1f);
            graphics.drawString(font, scaleStr, x, y, 0xFFFF55FF, false);
            y += LINE_HEIGHT;
        }

        // Health delta
        float delta = target.getHealthDelta();
        if (Math.abs(delta) > 0.1f) {
            String deltaStr = delta > 0 ?
                String.format("§a+%.1f HP", delta) :
                String.format("§c%.1f HP", delta);
            graphics.drawString(font, deltaStr, x, y, delta > 0 ? TEXT_GREEN : TEXT_RED, false);
            y += LINE_HEIGHT;
        }

        return y + SECTION_GAP;
    }

    private static int renderTrackedListSection(GuiGraphics graphics, Font font, int x, int y, int width) {
        List<TrackedEntity> tracked = AttributeMonitoringSystem.INSTANCE.getTrackedEntities();

        String header = String.format("§eTracked Entities (%d)", tracked.size());
        graphics.drawString(font, header, x, y, TEXT_YELLOW, false);
        y += LINE_HEIGHT;

        if (tracked.isEmpty()) {
            graphics.drawString(font, "§7None", x + 4, y, TEXT_GRAY, false);
            y += LINE_HEIGHT;
        } else {
            TrackedEntity primary = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();
            int shown = 0;
            for (TrackedEntity entity : tracked) {
                if (shown >= 5) break;

                boolean isPrimary = entity == primary;
                String prefix = isPrimary ? "§b> " : "§7  ";
                String losIndicator = entity.hasLineOfSight() ? "§a●" : "§c●";

                String entryStr = String.format("%s%s %s §7(%.0f%%)",
                    prefix, losIndicator, entity.getEntityName(), entity.getHealthPercent());
                graphics.drawString(font, entryStr, x, y, TEXT_WHITE, false);
                y += LINE_HEIGHT;
                shown++;
            }

            if (tracked.size() > 5) {
                graphics.drawString(font, "§7  ... +" + (tracked.size() - 5) + " more", x, y, TEXT_GRAY, false);
                y += LINE_HEIGHT;
            }
        }

        return y + SECTION_GAP;
    }

    private static void renderLogSection(GuiGraphics graphics, Font font, int x, int y, int width) {
        List<AttributeLogEntry> logs = AttributeMonitoringSystem.INSTANCE.getLogHistory();

        graphics.drawString(font, "§7Log History", x, y, TEXT_GRAY, false);
        y += LINE_HEIGHT;

        if (logs.isEmpty()) {
            graphics.drawString(font, "§8No events", x + 4, y, 0xFF555555, false);
        } else {
            int shown = 0;
            for (AttributeLogEntry log : logs) {
                if (shown >= 8) break;

                // Calcola alpha per fade
                float alpha = log.getAlpha(30000); // 30 secondi max
                if (alpha < 0.1f) continue;

                int color = applyAlpha(log.type().getColor(), alpha);
                String timeStr = "§8[" + log.getFormattedAge() + "] ";
                String fullStr = timeStr + log.getFormattedMessage();

                // Tronca se troppo lungo
                if (font.width(fullStr) > width) {
                    fullStr = fullStr.substring(0, Math.min(fullStr.length(), 35)) + "...";
                }

                graphics.drawString(font, fullStr, x, y, color, false);
                y += LINE_HEIGHT;
                shown++;
            }
        }
    }

    private static void renderBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        // Glow esterno (Impact UI style)
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER_GLOW);

        // Sfondo
        graphics.fill(x, y, x + width, y + height, PANEL_BG);

        // Bordo
        graphics.fill(x, y, x + width, y + 1, PANEL_BORDER); // Top
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER); // Bottom
        graphics.fill(x, y, x + 1, y + height, PANEL_BORDER); // Left
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER); // Right
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
