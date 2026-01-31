package com.devmod.client.ui.unified.pages;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.overlay.ImpactDisplayMode;
import com.devmod.client.overlay.ImpactHudController;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.scroll.Scrollbar;
import com.devmod.client.ui.unified.SettingsPage;
import com.devmod.config.Config;
import com.devmod.config.handler.impl.WeaponConfigHandler;
import com.devmod.stats.WeaponStats;

public class CombatSettingsPage implements SettingsPage {

    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_SPACING = 16;

    // Scrolling state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;
    private boolean isDraggingScrollbar = false;
    private boolean showScrollbar = false;
    private int lastContentX, lastContentWidth;
    private int lastContentY, lastContentHeight;
    private final EditorButton openEditorButton = new EditorButton("combat-open-editor", "Open Weapon Editor [M]").style(EditorButton.Style.PRIMARY);

    // Impact HUD mode buttons
    private final EditorButton impactMinimalBtn = new EditorButton("impact-minimal", "Minimal");
    private final EditorButton impactDetailedBtn = new EditorButton("impact-detailed", "Detailed");
    private final EditorButton impactAnalysisBtn = new EditorButton("impact-analysis", "Analysis");
    private final EditorButton impactCombatLanguageBtn = new EditorButton("impact-combat-language", "Combat Language");
    @Nullable
    private ImpactDisplayMode cachedImpactMode = null;

    public CombatSettingsPage() {
        // Register mode change listener for UI updates
        ImpactHudController.INSTANCE.setModeChangeListener(this::onImpactModeChanged);
    }

    private void onImpactModeChanged(ImpactDisplayMode newMode) {
        cachedImpactMode = newMode;
    }

    @Override
    public String getTitle() {
        return "Combat Settings";
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        // Store dimensions for scroll calculations
        lastContentX = x;
        lastContentY = y;
        lastContentWidth = width;
        lastContentHeight = height;
        visibleHeight = height;

        // Calculate total content height
        totalContentHeight = calculateContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        showScrollbar = totalContentHeight > visibleHeight;

        // Enable scissoring with try/finally for safety
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            int currentY = y - scrollOffset;
            Minecraft mc = Minecraft.getInstance();

            // === Current Weapon Section ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Current Weapon");
            currentY += ROW_HEIGHT + 4;

            ItemStack heldItem = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;

            if (heldItem.isEmpty()) {
                UIScaleManager.drawScaledString(graphics, font, "No weapon in hand", x, currentY, DesignTokens.Text.MUTED, false);
                currentY += ROW_HEIGHT;

                AxiomRenderer.drawHint(graphics, font, x, currentY, "Hold a weapon to see its stats");
                currentY += ROW_HEIGHT + SECTION_SPACING;
            } else {
                // Weapon name and icon
                String weaponName = heldItem.getHoverName().getString();
                UIScaleManager.drawScaledString(graphics, font, weaponName, x, currentY, DesignTokens.Text.PRIMARY, false);
                currentY += ROW_HEIGHT;

                // Get weapon stats
                WeaponStats stats = WeaponConfigHandler.INSTANCE.getStats(heldItem);

                // Stats display - body part colors
                int headColor = DesignTokens.BodyDiagram.HEAD;   // Red for head
                int bodyColor = DesignTokens.BodyDiagram.BODY;   // Teal for body
                int armsColor = DesignTokens.BodyDiagram.ARMS;   // Yellow for arms
                int legsColor = DesignTokens.BodyDiagram.LEGS;   // Light green for legs
                currentY = renderStatRow(graphics, font, x, currentY, width, "Head Multiplier",
                    String.format("%.1fx", stats.getHeadMult()), headColor);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Body Multiplier",
                    String.format("%.1fx", stats.getBodyMult()), bodyColor);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Arms Multiplier",
                    String.format("%.1fx", stats.getArmsMult()), armsColor);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Legs Multiplier",
                    String.format("%.1fx", stats.getLegsMult()), legsColor);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Armor Penetration",
                    String.format("%.0f%%", stats.getArmorPenetration() * 100), DesignTokens.BodyDiagram.ARMOR_LABEL);
                currentY = renderStatRow(graphics, font, x, currentY, width, "Base Damage Bonus",
                    String.format("+%.1f", stats.getBaseDamageBonus()), DesignTokens.Semantic.ERROR);

                currentY += SECTION_SPACING;
            }

            // Separator
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += SECTION_SPACING;

            // === Quick Actions Section ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Quick Actions");
            currentY += ROW_HEIGHT + 4;

            // Open Weapon Editor button
            int buttonWidth = 160;
            int buttonHeight = DesignTokens.Component.BUTTON_HEIGHT_LG;
            boolean hasWeapon = !heldItem.isEmpty();

            openEditorButton
                .enabled(hasWeapon)
                .onClick(() -> {
                    ActionRegistry.invoke(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON,
                        ClientActionContexts.forClient(ActionOrigin.UI));
                });
            openEditorButton.render(graphics, x, currentY, buttonWidth, buttonHeight, mouseX, mouseY);
            currentY += buttonHeight + 8;

            // Hint
            AxiomRenderer.drawHint(graphics, font, x, currentY, "Press M in-game to edit weapon stats");
            currentY += ROW_HEIGHT + SECTION_SPACING;

            // Separator
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += SECTION_SPACING;

            // === Impact HUD Settings Section ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Impact HUD Mode");
            currentY += ROW_HEIGHT + 4;

            // Get current preferred mode
            ImpactDisplayMode currentMode = ImpactHudController.INSTANCE.getUserPreferredCombatMode();
            if (cachedImpactMode != null) {
                currentMode = cachedImpactMode;
            }

            // Mode buttons
            int modeBtnWidth = 80;
            int modeBtnHeight = DesignTokens.Component.BUTTON_HEIGHT_MD;
            int btnGap = 8;

            // Minimal button
            boolean isMinimal = currentMode == ImpactDisplayMode.MINIMAL;
            impactMinimalBtn
                .style(isMinimal ? EditorButton.Style.PRIMARY : EditorButton.Style.NORMAL)
                .onClick(() -> ImpactHudController.INSTANCE.setUserPreferredCombatMode(ImpactDisplayMode.MINIMAL));
            impactMinimalBtn.render(graphics, x, currentY, modeBtnWidth, modeBtnHeight, mouseX, mouseY);

            // Detailed button
            boolean isDetailed = currentMode == ImpactDisplayMode.DETAILED;
            impactDetailedBtn
                .style(isDetailed ? EditorButton.Style.PRIMARY : EditorButton.Style.NORMAL)
                .onClick(() -> ImpactHudController.INSTANCE.setUserPreferredCombatMode(ImpactDisplayMode.DETAILED));
            impactDetailedBtn.render(graphics, x + modeBtnWidth + btnGap, currentY, modeBtnWidth, modeBtnHeight, mouseX, mouseY);

            // Analysis button
            boolean isAnalysis = currentMode == ImpactDisplayMode.ANALYSIS;
            impactAnalysisBtn
                .style(isAnalysis ? EditorButton.Style.PRIMARY : EditorButton.Style.NORMAL)
                .onClick(() -> ImpactHudController.INSTANCE.setUserPreferredCombatMode(ImpactDisplayMode.ANALYSIS));
            impactAnalysisBtn.render(graphics, x + (modeBtnWidth + btnGap) * 2, currentY, modeBtnWidth, modeBtnHeight, mouseX, mouseY);

            currentY += modeBtnHeight + 8;

            // Mode description
            String modeDesc = switch (currentMode) {
                case OFF -> "Impact feedback disabled";
                case MINIMAL -> "Flash and shake effects only";
                case DETAILED -> "Full damage breakdown panels";
                case ANALYSIS -> "Complete analysis with DPS tracking";
            };
            AxiomRenderer.drawHint(graphics, font, x, currentY, modeDesc);
            currentY += ROW_HEIGHT + 4;

            boolean combatLanguageActive = Config.IMPACT_VFX_GLYPHS_ENABLED.get()
                && Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.get();
            impactCombatLanguageBtn
                .style(combatLanguageActive ? EditorButton.Style.PRIMARY : EditorButton.Style.NORMAL)
                .onClick(() -> ActionRegistry.invoke(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE,
                    ClientActionContexts.forClient(ActionOrigin.UI)));
            impactCombatLanguageBtn.render(graphics, x, currentY, 160, modeBtnHeight, mouseX, mouseY);
            if (combatLanguageActive) {
                UIScaleManager.drawScaledString(graphics, font, "Active", x + 170, currentY + 6, DesignTokens.Semantic.SUCCESS, false);
            }
            currentY += modeBtnHeight + 4;
            AxiomRenderer.drawHint(graphics, font, x, currentY, "3D Combat Language preset (glyphs only)");
        } finally {
            // Disable scissoring
            graphics.disableScissor();
        }

        // Render scrollbar if needed
        if (showScrollbar) {
            renderScrollbar(graphics, x + width - Scrollbar.WIDTH - 2, y, Scrollbar.WIDTH, height);
        }
    }

    private int calculateContentHeight() {
        Minecraft mc = Minecraft.getInstance();
        boolean hasWeapon = mc.player != null && !mc.player.getMainHandItem().isEmpty();
        int h = 0;
        h += ROW_HEIGHT + 4; // Current Weapon header
        if (hasWeapon) {
            h += ROW_HEIGHT; // Weapon name
            h += ROW_HEIGHT * 6; // Stat rows
            h += SECTION_SPACING; // Section spacing
        } else {
            h += ROW_HEIGHT; // No weapon line
            h += ROW_HEIGHT; // Hint
            h += SECTION_SPACING;
        }
        h += SECTION_SPACING * 2; // Separator + spacing
        h += ROW_HEIGHT + 4; // Quick Actions header
        h += DesignTokens.Component.BUTTON_HEIGHT_LG + 8; // Button
        h += 20; // Hint
        h += SECTION_SPACING * 2; // Separator + spacing
        h += ROW_HEIGHT + 4; // Impact HUD Mode header
        h += DesignTokens.Component.BUTTON_HEIGHT_MD + 8; // Mode buttons
        h += ROW_HEIGHT; // Mode description
        h += DesignTokens.Component.BUTTON_HEIGHT_MD + 4; // Combat Language button
        h += ROW_HEIGHT; // Combat Language hint
        return h;
    }

    private void renderScrollbar(@Nonnull GuiGraphics graphics, int x, int y, int barWidth, int height) {
        Scrollbar.render(graphics, x, y, barWidth, height,
            scrollOffset, totalContentHeight, visibleHeight,
            false, isDraggingScrollbar);
    }

    private int renderStatRow(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                               String label, String value, int valueColor) {
        UIScaleManager.drawScaledString(graphics, font, label, x, y + 2, DesignTokens.Text.SECONDARY, false);

        int valueWidth = UIScaleManager.getScaledStringWidth(font, Objects.requireNonNull(value));
        int rightPadding = showScrollbar ? Scrollbar.WIDTH + 8 : 8;
        UIScaleManager.drawScaledString(graphics, font, value, x + width - valueWidth - rightPadding, y + 2, valueColor, false);

        return y + ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        // Check scrollbar click
        if (showScrollbar) {
            int scrollbarX = contentX + contentWidth - Scrollbar.WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + Scrollbar.WIDTH + 2) {
                isDraggingScrollbar = true;
                // Calculate thumb dimensions for accurate click positioning
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
                int trackHeight = lastContentHeight - thumbHeight;
                if (trackHeight > 0) {
                    // Center thumb on click position
                    float clickRatio = (float) (mouseY - contentY - thumbHeight / 2.0f) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        if (isMouseOverContent(mouseX, mouseY)) {
            if (openEditorButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (impactMinimalBtn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (impactDetailedBtn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (impactAnalysisBtn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOverContent(mouseX, mouseY) || maxScrollOffset <= 0) {
            return false;
        }
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        if (openEditorButton.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (impactMinimalBtn.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (impactDetailedBtn.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (impactAnalysisBtn.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScrollOffset > 0) {
            // Calculate thumb dimensions for accurate drag positioning
            float visibleRatio = (float) visibleHeight / totalContentHeight;
            int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
            int trackHeight = lastContentHeight - thumbHeight;
            if (trackHeight > 0) {
                // Center thumb on mouse position during drag
                float dragRatio = (float) (mouseY - lastContentY - thumbHeight / 2.0f) / trackHeight;
                dragRatio = Math.max(0, Math.min(1, dragRatio));
                scrollOffset = (int)(maxScrollOffset * dragRatio);
            }
            return true;
        }
        return false;
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return mouseX >= lastContentX && mouseX <= lastContentX + lastContentWidth
            && mouseY >= lastContentY && mouseY <= lastContentY + lastContentHeight;
    }
}
