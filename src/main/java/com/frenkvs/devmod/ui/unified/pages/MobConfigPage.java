package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.MobConfigManager;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.SettingsCategory;
import com.frenkvs.devmod.ui.unified.SettingsPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Mob configuration page - shows nearby mobs and their custom stats.
 * Click on a mob to open the detailed MobConfigScreen.
 */
public class MobConfigPage implements SettingsPage {

    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_SPACING = 16;
    private static final int MOB_SCAN_RADIUS = 16;
    private static final long STATUS_MESSAGE_DURATION_MS = 2000;

    private List<LivingEntity> nearbyMobs = List.of();
    private int scrollOffset = 0;
    private long lastScanTime = 0;

    // Status message for user feedback
    private String statusMessage = null;
    private long statusMessageTime = 0;
    private int statusMessageColor = UIConstants.Text.MUTED;

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.MOBS;
    }

    @Override
    public String getTitle() {
        return "Mob Configuration";
    }

    @Override
    public void init() {
        scanNearbyMobs();
    }

    @Override
    public void tick() {
        // Rescan every 2 seconds
        if (System.currentTimeMillis() - lastScanTime > 2000) {
            scanNearbyMobs();
        }
    }

    private void scanNearbyMobs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            nearbyMobs = List.of();
            return;
        }

        AABB scanBox = mc.player.getBoundingBox().inflate(MOB_SCAN_RADIUS);
        nearbyMobs = mc.level.getEntitiesOfClass(LivingEntity.class, scanBox,
            e -> e != mc.player && e.isAlive());

        lastScanTime = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        int currentY = y;

        // === Nearby Mobs Section ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY,
            "Nearby Mobs (" + nearbyMobs.size() + " in " + MOB_SCAN_RADIUS + " blocks)");
        currentY += ROW_HEIGHT;

        if (nearbyMobs.isEmpty()) {
            graphics.drawString(font, "No mobs nearby", x, currentY, UIConstants.Text.MUTED, false);
            currentY += ROW_HEIGHT;
        } else {
            // Show up to 8 mobs
            int displayCount = Math.min(nearbyMobs.size(), 8);
            for (int i = scrollOffset; i < Math.min(scrollOffset + displayCount, nearbyMobs.size()); i++) {
                LivingEntity mob = nearbyMobs.get(i);
                currentY = renderMobRow(graphics, font, x, currentY, width, mouseX, mouseY, mob, i);
            }

            // Scroll hint if more mobs
            if (nearbyMobs.size() > 8) {
                currentY += 4;
                String scrollHint = "Scroll for more (" + (scrollOffset + 1) + "-" +
                    Math.min(scrollOffset + displayCount, nearbyMobs.size()) + " of " + nearbyMobs.size() + ")";
                graphics.drawString(font, scrollHint, x, currentY, UIConstants.Text.MUTED, false);
                currentY += ROW_HEIGHT;
            }
        }

        currentY += SECTION_SPACING;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += SECTION_SPACING;

        // === Global Configurations Section ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Global Configurations");
        currentY += ROW_HEIGHT;

        // Count configured mob types
        int configuredCount = countConfiguredMobTypes();
        graphics.drawString(font, configuredCount + " mob types have custom stats", x, currentY,
            configuredCount > 0 ? UIConstants.Text.PRIMARY : UIConstants.Text.MUTED, false);
        currentY += ROW_HEIGHT;

        // Clear all button
        int buttonWidth = 140;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        boolean clearHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, buttonWidth, buttonHeight);

        if (configuredCount > 0) {
            AxiomRenderer.drawButton(graphics, font, x, currentY, buttonWidth, buttonHeight,
                "Clear All Configs", clearHovered, false);
        } else {
            graphics.fill(x, currentY, x + buttonWidth, currentY + buttonHeight, UIConstants.Background.INPUT);
            AxiomRenderer.drawBorder(graphics, x, currentY, buttonWidth, buttonHeight, UIConstants.Border.MUTED);
            int textWidth = font.width("Clear All Configs");
            graphics.drawString(font, "Clear All Configs", x + (buttonWidth - textWidth) / 2,
                currentY + (buttonHeight - 9) / 2, UIConstants.Text.DISABLED, false);
        }
        currentY += buttonHeight + 12;

        // Hint
        AxiomRenderer.drawHint(graphics, font, x, currentY, "Shift+Right-click a mob in-game to configure it");

        // Status message (fades out after duration)
        if (statusMessage != null && System.currentTimeMillis() - statusMessageTime < STATUS_MESSAGE_DURATION_MS) {
            currentY += ROW_HEIGHT;
            // Calculate fade (last 500ms)
            long elapsed = System.currentTimeMillis() - statusMessageTime;
            float alpha = elapsed > STATUS_MESSAGE_DURATION_MS - 500
                    ? (STATUS_MESSAGE_DURATION_MS - elapsed) / 500f
                    : 1f;
            int color = (statusMessageColor & 0x00FFFFFF) | ((int)(alpha * 255) << 24);
            graphics.drawString(font, statusMessage, x, currentY, color, false);
        }
    }

    private void showStatusMessage(String message, int color) {
        this.statusMessage = message;
        this.statusMessageColor = color;
        this.statusMessageTime = System.currentTimeMillis();
    }

    private int renderMobRow(GuiGraphics graphics, Font font, int x, int y, int width,
                              int mouseX, int mouseY, LivingEntity mob, int index) {
        int rowWidth = width - 20;
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        // Background on hover
        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + ROW_HEIGHT - 2, UIConstants.Background.HOVER);
        }

        // Mob name
        String mobName = mob.getName().getString();
        EntityType<?> mobType = mob.getType();
        boolean hasConfig = MobConfigManager.hasConfig(mobType);

        graphics.drawString(font, mobName, x, y + 2, UIConstants.Text.PRIMARY, false);

        // Config indicator
        if (hasConfig) {
            int tagX = x + font.width(mobName) + 8;
            graphics.fill(tagX - 2, y, tagX + font.width("configured") + 2, y + 12, UIConstants.Background.ACTIVE);
            graphics.drawString(font, "configured", tagX, y + 2, UIConstants.Status.SUCCESS, false);
        }

        // Health bar
        float healthPercent = mob.getHealth() / mob.getMaxHealth();
        int barX = x + rowWidth - 60;
        int barY = y + 4;
        int barWidth = 50;
        int barHeight = 8;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, UIConstants.Background.INPUT);
        int healthColor = UIConstants.getHealthColor(healthPercent * 100);
        graphics.fill(barX, barY, barX + (int)(barWidth * healthPercent), barY + barHeight, healthColor);
        AxiomRenderer.drawBorder(graphics, barX, barY, barWidth, barHeight, UIConstants.Border.MUTED);

        // Distance
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double distance = mob.distanceTo(mc.player);
            String distText = String.format("%.1fm", distance);
            graphics.drawString(font, distText, x, y + 12, UIConstants.Text.MUTED, false);
        }

        return y + ROW_HEIGHT;
    }

    private int countConfiguredMobTypes() {
        // This is a simplified count - in reality you'd iterate through MobConfigManager's data
        int count = 0;
        for (LivingEntity mob : nearbyMobs) {
            if (MobConfigManager.hasConfig(mob.getType())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        int rowWidth = contentWidth - 20;
        int y = contentY + ROW_HEIGHT; // Skip section header

        // Check mob rows
        int displayCount = Math.min(nearbyMobs.size(), 8);
        for (int i = scrollOffset; i < Math.min(scrollOffset + displayCount, nearbyMobs.size()); i++) {
            if (mouseX >= contentX && mouseX < contentX + rowWidth &&
                mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2) {

                LivingEntity entity = nearbyMobs.get(i);
                if (entity instanceof Mob mob) {
                    Minecraft.getInstance().setScreen(new com.frenkvs.devmod.MobConfigScreen(mob));
                    return true;
                } else {
                    // Show feedback for non-configurable entities (players, armor stands, etc.)
                    String entityName = entity.getName().getString();
                    showStatusMessage(entityName + " cannot be configured (not a Mob)", UIConstants.Status.WARNING);
                    return true; // Consume the click even though we can't configure
                }
            }
            y += ROW_HEIGHT;
        }

        // Skip to clear button area
        if (nearbyMobs.size() > 8) {
            y += 4 + ROW_HEIGHT; // Scroll hint
        }
        y += SECTION_SPACING + SECTION_SPACING + ROW_HEIGHT + ROW_HEIGHT; // Separators and headers

        // Clear All button
        int buttonWidth = 140;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        if (countConfiguredMobTypes() > 0 &&
            AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, buttonWidth, buttonHeight)) {
            MobConfigManager.clearAllGlobalStats();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (nearbyMobs.size() > 8) {
            scrollOffset -= (int) scrollY;
            scrollOffset = Math.max(0, Math.min(scrollOffset, nearbyMobs.size() - 8));
            return true;
        }
        return false;
    }

    @Override
    public int getContentHeight() {
        return 400;
    }
}
