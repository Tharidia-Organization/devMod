package com.devmod.client.ui.unified.pages;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.unified.SettingsPage;
import com.devmod.config.MobConfigManager;

public class MobConfigPage implements SettingsPage {

    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_SPACING = 16;
    private static final int MOB_SCAN_RADIUS = 16;
    private static final int MAX_VISIBLE_MOBS = 8;
    private static final long STATUS_MESSAGE_DURATION_MS = 2000;
    private static final int CLEAR_DIALOG_WIDTH = 260;
    private static final int CLEAR_DIALOG_HEIGHT = 100;

    private List<Mob> nearbyMobs = List.of();
    private int scrollOffset = 0;
    private long lastScanTime = 0;

    private int lastContentX = 0;
    private int lastContentY = 0;
    private int lastContentWidth = 0;
    private int lastContentHeight = 0;
    private int lastListX = 0;
    private int lastListY = 0;
    private int lastListWidth = 0;
    private int lastListHeight = 0;

    // Status message for user feedback
    @Nullable private String statusMessage;
    private long statusMessageTime = 0;
    private int statusMessageColor = DesignTokens.Text.MUTED;
    private final EditorButton clearAllBtn = new EditorButton("mob-clear-configs", "Clear All Configs").style(EditorButton.Style.DANGER);
    private final EditorButton confirmClearBtn = new EditorButton("mob-clear-confirm", "Clear All").style(EditorButton.Style.DANGER);
    private final EditorButton cancelClearBtn = new EditorButton("mob-clear-cancel", "Cancel").style(EditorButton.Style.NORMAL);
    private boolean showingClearConfirm = false;

    @Override
    public String getTitle() {
        return "Mob Configuration";
    }

    @Override
    public void init() {
        clearAllBtn.onClick(this::openClearConfirm);
        confirmClearBtn.onClick(this::confirmClearAll);
        cancelClearBtn.onClick(() -> showingClearConfirm = false);
        showingClearConfirm = false;
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
        var localPlayer = mc.player;
        var localLevel = mc.level;
        if (localLevel == null || localPlayer == null) {
            nearbyMobs = List.of();
            scrollOffset = 0;
            return;
        }

        AABB scanBox = Objects.requireNonNull(localPlayer.getBoundingBox().inflate(MOB_SCAN_RADIUS));
        nearbyMobs = localLevel.getEntitiesOfClass(Mob.class, scanBox, Mob::isAlive);

        clampScrollOffset();
        lastScanTime = System.currentTimeMillis();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        lastContentX = x;
        lastContentY = y;
        lastContentWidth = width;
        lastContentHeight = height;
        int currentY = y;

        // === Nearby Mobs Section ===
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, currentY,
            "Nearby Mobs (" + nearbyMobs.size() + " in " + MOB_SCAN_RADIUS + " blocks)");
        currentY += ROW_HEIGHT;
        lastListX = x;
        lastListY = currentY;
        lastListWidth = width - 20;
        lastListHeight = Math.max(ROW_HEIGHT, Math.min(nearbyMobs.size(), MAX_VISIBLE_MOBS) * ROW_HEIGHT);

        if (nearbyMobs.isEmpty()) {
            graphics.drawString(safeFont, "No mobs nearby", x, currentY, DesignTokens.Text.MUTED, false);
            currentY += ROW_HEIGHT;
        } else {
            // Show up to 8 mobs
            int displayCount = Math.min(nearbyMobs.size(), MAX_VISIBLE_MOBS);
            for (int i = scrollOffset; i < Math.min(scrollOffset + displayCount, nearbyMobs.size()); i++) {
                Mob mob = nearbyMobs.get(i);
                currentY = renderMobRow(graphics, safeFont, x, currentY, width, mouseX, mouseY, mob);
            }

            // Scroll hint if more mobs
            if (nearbyMobs.size() > MAX_VISIBLE_MOBS) {
                currentY += 4;
                String scrollHint = "Scroll for more (" + (scrollOffset + 1) + "-" +
                    Math.min(scrollOffset + displayCount, nearbyMobs.size()) + " of " + nearbyMobs.size() + ")";
                graphics.drawString(safeFont, scrollHint, x, currentY, DesignTokens.Text.MUTED, false);
                currentY += ROW_HEIGHT;
            }
        }

        currentY += SECTION_SPACING;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += SECTION_SPACING;

        // === Global Configurations Section ===
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, currentY, "Global Configurations");
        currentY += ROW_HEIGHT;

        // Count configured mob types
        int configuredCount = countConfiguredMobTypes();
        String countLabel = configuredCount == 0
            ? "No global mob configs yet"
            : configuredCount + " mob types have custom stats";
        graphics.drawString(safeFont, countLabel, x, currentY,
            configuredCount > 0 ? DesignTokens.Text.PRIMARY : DesignTokens.Text.MUTED, false);
        currentY += ROW_HEIGHT;

        // Clear all button
        int buttonWidth = 160;
        int buttonHeight = DesignTokens.Component.BUTTON_HEIGHT_LG;
        clearAllBtn.enabled(configuredCount > 0);
        clearAllBtn.render(graphics, x, currentY, buttonWidth, buttonHeight, mouseX, mouseY);
        currentY += buttonHeight + 12;

        // Hint
        AxiomRenderer.drawHint(graphics, font, x, currentY, "Shift+Right-click a mob in-game, or click a row to configure it");

        // Status message (fades out after duration)
        if (statusMessage != null && System.currentTimeMillis() - statusMessageTime < STATUS_MESSAGE_DURATION_MS) {
            currentY += ROW_HEIGHT;
            // Calculate fade (last 500ms)
            long elapsed = System.currentTimeMillis() - statusMessageTime;
            float alpha = elapsed > STATUS_MESSAGE_DURATION_MS - 500
                    ? (STATUS_MESSAGE_DURATION_MS - elapsed) / 500f
                    : 1f;
            int color = (statusMessageColor & DesignTokens.Mask.RGB) | ((int)(alpha * 255) << 24);
            graphics.drawString(font, statusMessage, x, currentY, color, false);
        }

        if (showingClearConfirm) {
            renderClearConfirm(graphics, safeFont, mouseX, mouseY);
        }
    }

    private void showStatusMessage(String message, int color) {
        this.statusMessage = message;
        this.statusMessageColor = color;
        this.statusMessageTime = System.currentTimeMillis();
    }

    private int renderMobRow(GuiGraphics graphics, Font font, int x, int y, int width,
                              int mouseX, int mouseY, Mob mob) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        int rowWidth = width - 20;
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        // Background on hover
        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + ROW_HEIGHT - 2, DesignTokens.Surface.LEVEL_1);
        }

        // Mob name
        String mobName = Objects.requireNonNull(mob.getName().getString());
        boolean hasConfig = MobConfigManager.hasConfig(mob.getType());

        graphics.drawString(safeFont, mobName, x, y + 2, DesignTokens.Text.PRIMARY, false);

        // Config indicator
        if (hasConfig) {
            int tagX = x + safeFont.width(mobName) + 8;
            graphics.fill(tagX - 2, y, tagX + safeFont.width("configured") + 2, y + 12, DesignTokens.Surface.LEVEL_2);
            graphics.drawString(safeFont, "configured", tagX, y + 2, DesignTokens.Semantic.SUCCESS, false);
        }

        // Health bar
        float healthPercent = mob.getHealth() / mob.getMaxHealth();
        int barX = x + rowWidth - 60;
        int barY = y + 4;
        int barWidth = 50;
        int barHeight = 8;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, DesignTokens.Bg.LEVEL_0);
        // Health color: green > 50%, yellow > 25%, red otherwise
        int healthColor = healthPercent > 0.5f ? DesignTokens.Semantic.SUCCESS
            : healthPercent > 0.25f ? DesignTokens.Semantic.WARNING : DesignTokens.Semantic.ERROR;
        graphics.fill(barX, barY, barX + (int)(barWidth * healthPercent), barY + barHeight, healthColor);
        AxiomRenderer.drawBorder(graphics, barX, barY, barWidth, barHeight, DesignTokens.Stroke.MUTED);

        // Distance
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double distance = mob.distanceTo(mc.player);
            String distText = String.format("%.1fm", distance);
            graphics.drawString(safeFont, distText, x, y + 12, DesignTokens.Text.MUTED, false);
        }

        return y + ROW_HEIGHT;
    }

    private int countConfiguredMobTypes() {
        return MobConfigManager.getGlobalConfigCount();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (showingClearConfirm) {
            if (button == 0) {
                confirmClearBtn.mouseClicked(mouseX, mouseY, button);
                cancelClearBtn.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        if (button != 0) return false;

        int rowWidth = contentWidth - 20;
        int y = contentY + ROW_HEIGHT; // Skip section header

        // Check mob rows
        int displayCount = Math.min(nearbyMobs.size(), MAX_VISIBLE_MOBS);
        for (int i = scrollOffset; i < Math.min(scrollOffset + displayCount, nearbyMobs.size()); i++) {
            if (mouseX >= contentX && mouseX < contentX + rowWidth &&
                mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2) {

                Mob mob = nearbyMobs.get(i);
                ActionRegistry.invoke(ActionIds.UI_MOB_CONFIG_OPEN,
                    ClientActionContexts.forClient(ActionOrigin.UI, mob));
                return true;
            }
            y += ROW_HEIGHT;
        }

        // Skip to clear button area
        if (nearbyMobs.size() > 8) {
            y += 4 + ROW_HEIGHT; // Scroll hint
        }
        y += SECTION_SPACING + SECTION_SPACING + ROW_HEIGHT + ROW_HEIGHT; // Separators and headers

        // Clear All button
        if (clearAllBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (showingClearConfirm) {
            confirmClearBtn.mouseReleased(mouseX, mouseY, button);
            cancelClearBtn.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (clearAllBtn.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showingClearConfirm) {
            return true;
        }
        if (nearbyMobs.size() > MAX_VISIBLE_MOBS && isMouseOverList(mouseX, mouseY)) {
            scrollOffset -= (int) scrollY;
            clampScrollOffset();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!showingClearConfirm) {
            return false;
        }
        if (keyCode == 256) { // ESC
            showingClearConfirm = false;
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter
            confirmClearAll();
            return true;
        }
        return true;
    }

    @Override
    public void onClose() {
        showingClearConfirm = false;
    }

    private void openClearConfirm() {
        if (MobConfigManager.getGlobalConfigCount() <= 0) {
            return;
        }
        showingClearConfirm = true;
    }

    private void confirmClearAll() {
        MobConfigManager.clearAllGlobalStatsAndSave();
        showingClearConfirm = false;
        showStatusMessage("Cleared all configs", DesignTokens.Semantic.SUCCESS);
        // Sound handled by EditorButton or caller
    }

    private void clampScrollOffset() {
        int maxScroll = Math.max(0, nearbyMobs.size() - MAX_VISIBLE_MOBS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private boolean isMouseOverList(double mouseX, double mouseY) {
        if (lastListWidth <= 0 || lastListHeight <= 0) {
            return false;
        }
        return mouseX >= lastListX && mouseX < lastListX + lastListWidth &&
            mouseY >= lastListY && mouseY < lastListY + lastListHeight;
    }

    private void renderClearConfirm(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(lastContentX, lastContentY, lastContentX + lastContentWidth,
            lastContentY + lastContentHeight, DesignTokens.withAlpha(DesignTokens.Bg.LEVEL_0, 200));

        int panelWidth = Math.min(CLEAR_DIALOG_WIDTH, lastContentWidth - 20);
        int panelHeight = CLEAR_DIALOG_HEIGHT;
        int panelX = lastContentX + (lastContentWidth - panelWidth) / 2;
        int panelY = lastContentY + (lastContentHeight - panelHeight) / 2;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DesignTokens.Bg.LEVEL_1);
        AxiomRenderer.drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, DesignTokens.Semantic.WARNING);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, DesignTokens.Semantic.WARNING);

        String title = "Clear all configs?";
        int titleX = panelX + (panelWidth - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, panelY + 10, DesignTokens.Text.PRIMARY, false);

        String line1 = "This removes all global mob stats.";
        int line1X = panelX + (panelWidth - font.width(line1)) / 2;
        graphics.drawString(font, line1, line1X, panelY + 28, DesignTokens.Text.SECONDARY, false);

        String line2 = "This cannot be undone.";
        int line2X = panelX + (panelWidth - font.width(line2)) / 2;
        graphics.drawString(font, line2, line2X, panelY + 42, DesignTokens.Text.SECONDARY, false);

        int btnW = 90;
        int btnH = DesignTokens.Component.BUTTON_HEIGHT_LG;
        int btnGap = 10;
        int btnX = panelX + (panelWidth - (btnW * 2 + btnGap)) / 2;
        int btnY = panelY + panelHeight - btnH - 10;
        confirmClearBtn.render(graphics, btnX, btnY, btnW, btnH, mouseX, mouseY);
        cancelClearBtn.render(graphics, btnX + btnW + btnGap, btnY, btnW, btnH, mouseX, mouseY);
    }
}
