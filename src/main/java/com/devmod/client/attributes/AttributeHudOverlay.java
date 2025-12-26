package com.devmod.client.attributes;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

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

import com.devmod.DevMod;
import com.devmod.attributes.AttributeLogEntry;
import com.devmod.util.I18n;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class AttributeHudOverlay {
    public static final AttributeHudOverlay INSTANCE = new AttributeHudOverlay();

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "attribute_monitor");

    // === UI Colors (Impact UI Style) ===
    private static final int PANEL_BG = 0xCC1A1A2E;           // Dark blue 80% opacity
    private static final int PANEL_BORDER = 0xFF3D5AFE;       // Electric blue Impact
    private static final int BORDER_GLOW = 0x553D5AFE;        // Glow effect
    private static final int TITLE_COLOR = 0xFF00FFFF;        // Cyan Impact
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GREEN = 0xFF00FF00;         // Green Impact
    private static final int TEXT_YELLOW = 0xFFFFD700;        // Gold Impact
    private static final int TEXT_RED = 0xFFFF4444;           // Red Impact
    private static final int TEXT_GRAY = 0xFFAAAAAA;          // Muted gray
    private static final int TEXT_ORANGE = 0xFFFF9800;        // Orange

    // === Layout ===
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_MARGIN = 10;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_GAP = 8;

    private AttributeHudOverlay() {}

    /**
     * Registers the GUI layer.
     */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(LAYER_ID),
            AttributeHudOverlay::render
        );
    }

    /**
     * Renders the HUD overlay.
     * Called every frame when the layer is active.
     */
    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!AttributeMonitoringSystem.INSTANCE.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Font font = Objects.requireNonNull(mc.font);
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Panel position (right side)
        int panelX = screenWidth - PANEL_WIDTH - PANEL_MARGIN;
        int panelY = PANEL_MARGIN;

        // Calculate dynamic height
        int panelHeight = calculatePanelHeight();

        // === BACKGROUND ===
        renderBackground(graphics, panelX, panelY, PANEL_WIDTH, panelHeight);

        // === CONTENT ===
        int y = panelY + PADDING;
        int textX = panelX + PADDING;
        int contentWidth = PANEL_WIDTH - PADDING * 2;

        // TITLE
        graphics.drawString(font, I18n.translate("devmod.attribute_monitor.title").getString(), textX, y, TITLE_COLOR, false);
        y += LINE_HEIGHT + 2;

        // Separator line
        graphics.fill(textX, y, textX + contentWidth, y + 1, PANEL_BORDER);
        y += SECTION_GAP;

        // === TARGET PRIMARIO ===
        TrackedEntity target = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();
        if (target != null && target.isValid()) {
            y = renderTargetSection(graphics, font, textX, y, contentWidth, target);
        } else {
            graphics.drawString(font, I18n.translate("devmod.attribute_monitor.no_target").getString(), textX, y, TEXT_GRAY, false);
            y += LINE_HEIGHT + SECTION_GAP;
        }

        // Separator
        graphics.fill(textX, y, textX + contentWidth, y + 1, PANEL_BORDER & 0x77FFFFFF);
        y += SECTION_GAP;

        // === TRACKED ENTITIES ===
        y = renderTrackedListSection(graphics, font, textX, y, contentWidth);

        // Separator
        graphics.fill(textX, y, textX + contentWidth, y + 1, PANEL_BORDER & 0x77FFFFFF);
        y += SECTION_GAP;

        // === LOG HISTORY ===
        renderLogSection(graphics, font, textX, y, contentWidth);
    }

    private static int calculatePanelHeight() {
        int height = PADDING * 2 + LINE_HEIGHT + 2 + SECTION_GAP; // Title

        TrackedEntity target = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();
        if (target != null && target.isValid()) {
            height += LINE_HEIGHT * 9 + SECTION_GAP; // Target section
        } else {
            height += LINE_HEIGHT + SECTION_GAP;
        }

        height += SECTION_GAP; // Separator

        // Tracked list (max 5)
        int trackedCount = Math.min(AttributeMonitoringSystem.INSTANCE.getTrackedCount(), 5);
        height += LINE_HEIGHT + (LINE_HEIGHT * trackedCount) + SECTION_GAP;

        height += SECTION_GAP; // Separator

        // Log (max 8 entries)
        int logCount = Math.min(AttributeMonitoringSystem.INSTANCE.getLogHistory().size(), 8);
        height += LINE_HEIGHT + (LINE_HEIGHT * Math.max(logCount, 1)) + SECTION_GAP;

        return height;
    }

    private static int renderTargetSection(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, TrackedEntity target) {
        // Target name
        String nameStr = "§f" + target.getEntityName();
        String losTag = target.hasLineOfSight()
            ? I18n.translate("devmod.attribute_monitor.los").getString()
            : I18n.translate("devmod.attribute_monitor.blocked").getString();
        nameStr += " " + losTag;
        graphics.drawString(font, nameStr, x, y, TEXT_WHITE, false);
        y += LINE_HEIGHT;

        // Health bar
        float healthPercent = target.getHealthPercent();
        int healthColor = healthPercent > 50 ? TEXT_GREEN : (healthPercent > 25 ? TEXT_YELLOW : TEXT_RED);
        String healthStr = I18n.translate("devmod.attribute_monitor.health",
            target.getCurrentHealth(), target.getMaxHealth(), healthPercent).getString();
        graphics.drawString(font, healthStr, x, y, healthColor, false);
        y += LINE_HEIGHT;

        // Graphical health bar
        int barWidth = width - 4;
        int barHeight = 4;
        int barX = x + 2;
        graphics.fill(barX, y, barX + barWidth, y + barHeight, 0xFF333333); // Background
        int filledWidth = (int) (barWidth * (healthPercent / 100f));
        graphics.fill(barX, y, barX + filledWidth, y + barHeight, healthColor); // Fill
        y += barHeight + 4;

        // Armor
        String armorStr = I18n.translate("devmod.attribute_monitor.armor",
            target.getArmorValue(), target.getArmorToughness()).getString();
        graphics.drawString(font, armorStr, x, y, TEXT_GRAY, false);
        y += LINE_HEIGHT;

        // Attack stats
        String attackStr = I18n.translate("devmod.attribute_monitor.attack",
            target.getAttackDamage(), target.getAttackSpeed()).getString();
        graphics.drawString(font, attackStr, x, y, TEXT_ORANGE, false);
        y += LINE_HEIGHT;

        // Movement
        String moveStr = I18n.translate("devmod.attribute_monitor.movement",
            target.getMovementSpeed(), target.getKnockbackResistance() * 100).getString();
        graphics.drawString(font, moveStr, x, y, TEXT_GRAY, false);
        y += LINE_HEIGHT;

        // Distance
        double dist = target.getDistanceToPlayer();
        String distStr = I18n.translate("devmod.attribute_monitor.distance", dist).getString();
        graphics.drawString(font, distStr, x, y, TEXT_GRAY, false);
        y += LINE_HEIGHT;

        // Pehkui (if present)
        if (target.hasPehkuiModification()) {
            Float scale = target.getPehkuiScale();
            String scaleStr = I18n.translate("devmod.attribute_monitor.pehkui_scale", scale != null ? scale : 1f).getString();
            graphics.drawString(font, scaleStr, x, y, 0xFFFF55FF, false);
            y += LINE_HEIGHT;
        }

        // Health delta
        float delta = target.getHealthDelta();
        if (Math.abs(delta) > 0.1f) {
            String deltaKey = delta > 0
                ? "devmod.attribute_monitor.health_delta.positive"
                : "devmod.attribute_monitor.health_delta.negative";
            String deltaStr = I18n.translate(deltaKey, delta).getString();
            graphics.drawString(font, deltaStr, x, y, delta > 0 ? TEXT_GREEN : TEXT_RED, false);
            y += LINE_HEIGHT;
        }

        return y + SECTION_GAP;
    }

    private static int renderTrackedListSection(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width) {
        List<TrackedEntity> tracked = AttributeMonitoringSystem.INSTANCE.getTrackedEntities();

        String header = I18n.translate("devmod.attribute_monitor.tracked_entities", tracked.size()).getString();
        graphics.drawString(font, header, x, y, TEXT_YELLOW, false);
        y += LINE_HEIGHT;

        if (tracked.isEmpty()) {
            graphics.drawString(font, I18n.translate("devmod.attribute_monitor.none").getString(), x + 4, y, TEXT_GRAY, false);
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
                graphics.drawString(font,
                    I18n.translate("devmod.attribute_monitor.more", tracked.size() - 5).getString(),
                    x, y, TEXT_GRAY, false);
                y += LINE_HEIGHT;
            }
        }

        return y + SECTION_GAP;
    }

    private static void renderLogSection(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width) {
        List<AttributeLogEntry> logs = AttributeMonitoringSystem.INSTANCE.getLogHistory();

        graphics.drawString(font, I18n.translate("devmod.attribute_monitor.log_history").getString(), x, y, TEXT_GRAY, false);
        y += LINE_HEIGHT;

        if (logs.isEmpty()) {
            graphics.drawString(font, I18n.translate("devmod.attribute_monitor.log.none").getString(), x + 4, y, 0xFF555555, false);
        } else {
            int shown = 0;
            for (AttributeLogEntry log : logs) {
                if (shown >= 8) break;

                // Calculate alpha for fade
                float alpha = log.getAlpha(30000); // 30 secondi max
                if (alpha < 0.1f) continue;

                int color = applyAlpha(log.type().getColor(), alpha);
                String timeStr = I18n.translate("devmod.attribute_monitor.log.age", log.getFormattedAge()).getString();
                String fullStr = timeStr + log.getFormattedMessage();

                // Truncate if too long
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
        // Outer glow (Impact UI style)
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER_GLOW);

        // Background
        graphics.fill(x, y, x + width, y + height, PANEL_BG);

        // Border
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
