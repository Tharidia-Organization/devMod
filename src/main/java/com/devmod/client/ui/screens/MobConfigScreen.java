package com.devmod.client.ui.screens;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.config.MobPresetManager;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public class MobConfigScreen extends Screen {

    private final Mob mob;

    // Delegate classes
    @Nullable
    private MobConfigScreenState state;
    @Nullable
    private MobConfigScreenRenderer renderer;

    public MobConfigScreen(Mob mob) {
        super(I18n.translate("devmod.screen.mob_config", mob.getName().getString()));
        this.mob = mob;
    }

    @Override
    protected void init() {
        // Initialize delegates
        MobConfigScreenState newState = new MobConfigScreenState(mob);
        this.state = newState;
        this.renderer = new MobConfigScreenRenderer(newState, font);

        // Disable blur
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            newState.originalBlurValue = blurOption.get();
            blurOption.set(0);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        MobConfigScreenState state = requireState();
        MobConfigScreenRenderer renderer = requireRenderer();
        // Check if mob died while screen is open
        if (!mob.isAlive()) {
            var mc = minecraft;
            var player = mc != null ? mc.player : null;
            if (player != null) {
                player.displayClientMessage(
                    I18n.translate("devmod.mobconfig.target_died"),
                    false
                );
                player.playSound(Objects.requireNonNull(net.minecraft.sounds.SoundEvents.VILLAGER_NO), 0.8f, 1.0f);
            }
            this.onClose();
            return;
        }

        // Update animations
        state.updateAnimations();

        int panelW = MobConfigScreenRenderer.scaledPanelWidth();
        int panelH = MobConfigScreenRenderer.scaledPanelHeight();
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        // Main panel with gradient header
        renderer.drawMainPanel(graphics, panelX, panelY);

        // Global mode warning banner
        if (state.isGlobalMode) {
            renderer.drawGlobalModeWarning(graphics, panelX, panelY - s(28));
        }

        // Tab bar
        renderer.drawTabs(graphics, panelX, panelY, mouseX, mouseY);

        // Content area
        int contentY = panelY + s(DesignTokens.Spacing.HEADER_HEIGHT) + MobConfigScreenRenderer.scaledTabHeight() + s(8);

        // Left side: Mob preview
        int previewX = panelX + s(15);
        int previewY = contentY + s(10);
        renderer.drawMobPreview(graphics, previewX, previewY, mouseX, mouseY);

        // Right side: Sliders
        int slidersX = panelX + MobConfigScreenRenderer.scaledPreviewSize() + s(40);
        int slidersY = contentY + s(5);
        renderer.drawSliders(graphics, slidersX, slidersY, mouseX, mouseY);

        // Bottom section
        int bottomY = panelY + panelH - s(80);
        renderer.drawBottomSection(graphics, panelX, bottomY, mouseX, mouseY);

        // Tooltips
        renderer.drawTooltips(graphics, panelX, panelY, mouseX, mouseY);

        // Dialogs
        if (state.showingConfirmDialog) {
            renderer.drawConfirmDialog(graphics, this.width, this.height, mouseX, mouseY);
        }
        if (state.showSavePresetDialog) {
            renderer.drawSavePresetDialog(graphics, this.width, this.height, mouseX, mouseY);
        }
        if (state.deleteConfirmPreset != null) {
            renderer.drawDeletePresetDialog(graphics, this.width, this.height, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MobConfigScreenState state = requireState();
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Handle save preset dialog
        if (state.showSavePresetDialog) {
            return handleSavePresetDialogClick(mx, my, button);
        }

        // Handle delete preset dialog
        if (state.deleteConfirmPreset != null) {
            return handleDeletePresetDialogClick(mx, my, button);
        }

        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Handle confirmation dialog
        if (state.showingConfirmDialog) {
            return handleConfirmDialogClick(mx, my);
        }

        int panelW = MobConfigScreenRenderer.scaledPanelWidth();
        int panelH = MobConfigScreenRenderer.scaledPanelHeight();
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        // Tab clicks
        if (handleTabClick(mx, my, panelX, panelY)) return true;

        // Mob preview drag
        if (handlePreviewClick(mx, my, panelX, panelY)) return true;

        // Slider interactions
        if (handleSliderClick(mx, my, panelX, panelY)) return true;

        // Cancel editing if clicked elsewhere
        if (state.editingSlider >= 0) {
            state.commitNumericInput();
        }

        // Mode toggle
        int bottomY = panelY + panelH - s(80);
        if (AxiomRenderer.isMouseOver(mx, my, panelX + s(15), bottomY, s(120), s(22))) {
            state.toggleMode();
            return true;
        }

        // Action buttons
        if (handleActionButtons(mx, my, panelX, bottomY)) return true;

        // Preset buttons
        if (handlePresetClick(mx, my, panelX, bottomY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleSavePresetDialogClick(int mx, int my, int button) {
        MobConfigScreenState state = requireState();
        int dw = s(200);
        int dh = s(90);
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        int btnW = s(60);
        int btnH = s(18);
        int btnGap = s(10);
        int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dh - s(28);

        if (button == 0 && AxiomRenderer.isMouseOver(mx, my, btnsX, btnsY, btnW, btnH)) {
            if (state.presetNameInput.length() > 0) {
                state.saveCurrentAsPreset(state.presetNameInput.toString());
                state.showSavePresetDialog = false;
                state.presetNameInput.setLength(0);
            }
            return true;
        }

        if (button == 0 && AxiomRenderer.isMouseOver(mx, my, btnsX + btnW + btnGap, btnsY, btnW, btnH)) {
            state.showSavePresetDialog = false;
            state.presetNameInput.setLength(0);
            return true;
        }

        return true;
    }

    private boolean handleDeletePresetDialogClick(int mx, int my, int button) {
        MobConfigScreenState state = requireState();
        int dw = s(220);
        int dh = s(80);
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        int btnW = s(60);
        int btnH = s(18);
        int btnGap = s(10);
        int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dh - s(28);

        if (button == 0 && AxiomRenderer.isMouseOver(mx, my, btnsX, btnsY, btnW, btnH)) {
            String presetToDelete = state.deleteConfirmPreset;
            if (presetToDelete != null) {
                state.deleteUserPreset(presetToDelete);
            }
            state.deleteConfirmPreset = null;
            return true;
        }

        if (button == 0 && AxiomRenderer.isMouseOver(mx, my, btnsX + btnW + btnGap, btnsY, btnW, btnH)) {
            state.deleteConfirmPreset = null;
            return true;
        }

        return true;
    }

    private boolean handleConfirmDialogClick(int mx, int my) {
        MobConfigScreenState state = requireState();
        int dialogW = MobConfigScreenRenderer.scaledDialogWidth();
        int dialogH = MobConfigScreenRenderer.scaledDialogHeight();
        int dx = (this.width - dialogW) / 2;
        int dy = (this.height - dialogH) / 2;
        int btnW = s(80);
        int btnH = s(22);
        int btnGap = s(20);
        int btnsX = dx + (dialogW - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dialogH - s(35);

        if (AxiomRenderer.isMouseOver(mx, my, btnsX, btnsY, btnW, btnH)) {
            state.confirmedDiscard = true;
            state.showingConfirmDialog = false;
            this.onClose();
            return true;
        }

        int cancelX = btnsX + btnW + btnGap;
        if (AxiomRenderer.isMouseOver(mx, my, cancelX, btnsY, btnW, btnH)) {
            state.showingConfirmDialog = false;
            return true;
        }

        if (!AxiomRenderer.isMouseOver(mx, my, dx, dy, dialogW, dialogH)) {
            state.showingConfirmDialog = false;
            return true;
        }

        return true;
    }

    private boolean handleTabClick(int mx, int my, int panelX, int panelY) {
        MobConfigScreenState state = requireState();
        int tabsX = panelX + s(10);
        int tabsY = panelY + s(DesignTokens.Spacing.HEADER_HEIGHT);
        int tabW = MobConfigScreenRenderer.scaledTabWidth();
        int tabH = MobConfigScreenRenderer.scaledTabHeight();
        for (int i = 0; i < 3; i++) {
            int tx = tabsX + i * (tabW + s(4));
            if (AxiomRenderer.isMouseOver(mx, my, tx, tabsY, tabW, tabH)) {
                if (state.editingSlider >= 0) {
                    state.commitNumericInput();
                }
                state.selectedTab = i;
                return true;
            }
        }
        return false;
    }

    private boolean handlePreviewClick(int mx, int my, int panelX, int panelY) {
        MobConfigScreenState state = requireState();
        int previewX = panelX + s(15);
        int previewY = panelY + s(DesignTokens.Spacing.HEADER_HEIGHT) + MobConfigScreenRenderer.scaledTabHeight() + s(8) + s(10);
        int previewSize = MobConfigScreenRenderer.scaledPreviewSize();
        if (AxiomRenderer.isMouseOver(mx, my, previewX, previewY, previewSize, previewSize + s(20))) {
            state.isDraggingPreview = true;
            state.dragStartX = mx;
            state.dragStartY = my;
            state.dragStartRotX = state.rotationX;
            state.dragStartRotY = state.targetRotationY;
            return true;
        }
        return false;
    }

    private boolean handleSliderClick(int mx, int my, int panelX, int panelY) {
        MobConfigScreenState state = requireState();
        int slidersX = panelX + MobConfigScreenRenderer.scaledPreviewSize() + s(40);
        int contentY = panelY + s(DesignTokens.Spacing.HEADER_HEIGHT) + MobConfigScreenRenderer.scaledTabHeight() + s(8) + s(5);
        int numSliders = state.selectedTab == 2 ? 1 : 3;

        for (int i = 0; i < numSliders; i++) {
            int rowY = contentY + i * (MobConfigScreenRenderer.scaledRowHeight() + s(16));
            int sliderY = rowY + s(12);
            int sliderId = state.selectedTab * 10 + i;

            // Value text click
            int valueX = slidersX + MobConfigScreenRenderer.scaledSliderWidth() + s(10);
            int valueW = s(35);
            int valueH = s(12);
            if (AxiomRenderer.isMouseOver(mx, my, valueX - s(2), rowY - s(2), valueW + s(4), valueH + s(2))) {
                if (state.editingSlider != sliderId) {
                    state.commitNumericInput();
                    state.editingSlider = sliderId;
                    state.inputBuffer.setLength(0);
                    state.inputBuffer.append(String.format("%.1f", state.getSliderValue(sliderId)));
                    state.inputCursorBlink = System.currentTimeMillis();
                }
                return true;
            }

            // Slider track click
            if (AxiomRenderer.isMouseOver(mx, my, slidersX - s(5), sliderY - s(5), MobConfigScreenRenderer.scaledSliderWidth() + s(10), MobConfigScreenRenderer.scaledSliderHeight() + s(10))) {
                if (state.editingSlider >= 0) {
                    state.commitNumericInput();
                }
                state.activeSlider = sliderId;
                state.updateSliderValue(mx, slidersX, MobConfigScreenRenderer.scaledSliderWidth());
                return true;
            }
        }
        return false;
    }

    private boolean handleActionButtons(int mx, int my, int panelX, int bottomY) {
        MobConfigScreenState state = requireState();
        int btnY = bottomY + s(56);
        int btnW = s(70);
        int btnH = s(22);
        int btnGap = s(8);
        boolean hasStatChanges = state.hasStatChanges();
        boolean hasUnsavedChanges = state.hasUnsavedChanges();

        int resetX = panelX + s(15);
        if (AxiomRenderer.isMouseOver(mx, my, resetX, btnY, s(55), btnH)) {
            if (hasStatChanges) {
                state.resetToOriginal();
            }
            return true;
        }

        int applyX = panelX + MobConfigScreenRenderer.scaledPanelWidth() - btnW - btnGap - btnW - s(15);
        if (AxiomRenderer.isMouseOver(mx, my, applyX, btnY, btnW, btnH)) {
            if (hasUnsavedChanges) {
                state.save();
                this.onClose();
            }
            return true;
        }

        int equipX = applyX + btnW + btnGap;
        if (AxiomRenderer.isMouseOver(mx, my, equipX, btnY, btnW, btnH)) {
            ActionRegistry.invoke(ActionIds.UI_MOB_EQUIPMENT_OPEN,
                ClientActionContexts.forClient(ActionOrigin.UI));
            return true;
        }

        return false;
    }

    private boolean handlePresetClick(int mx, int my, int panelX, int bottomY, int button) {
        MobConfigScreenState state = requireState();
        int presetX = panelX + s(150);
        int presetY = bottomY - s(3) + s(12);
        int presetW = s(55);
        int presetH = s(16);
        int gap = s(4);

        // Built-in presets
        for (int i = 0; i < MobConfigScreenState.PRESET_NAMES.size(); i++) {
            int row = i / 3;
            int col = i % 3;
            int px = presetX + col * (presetW + gap);
            int py = presetY + row * (presetH + gap);

            if (AxiomRenderer.isMouseOver(mx, my, px, py, presetW, presetH)) {
                state.applyPreset(i);
                return true;
            }
        }

        // User presets section
        String[] userPresetNames = MobPresetManager.getPresetNames();
        int userY = presetY + 2 * (presetH + gap) + s(6);

        // Save button
        int saveBtnX = presetX + s(20);
        if (MobPresetManager.canAddPreset() && AxiomRenderer.isMouseOver(mx, my, saveBtnX, userY - s(1), s(12), s(10))) {
            state.showSavePresetDialog = true;
            state.presetNameInput.setLength(0);
            return true;
        }

        // User preset buttons
        int userPresetW = s(45);
        int userPresetH = s(14);
        int userStartX = presetX + s(40);
        int maxVisible = 3;

        for (int i = 0; i < Math.min(userPresetNames.length, maxVisible); i++) {
            int px = userStartX + i * (userPresetW + s(2));
            int py = userY - s(2);

            if (AxiomRenderer.isMouseOver(mx, my, px, py, userPresetW, userPresetH)) {
                if (button == 1) {
                    state.deleteConfirmPreset = userPresetNames[i];
                } else {
                    state.applyUserPreset(userPresetNames[i]);
                }
                return true;
            }
        }

        return false;
    }

    public Mob getMob() {
        return mob;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        MobConfigScreenState state = requireState();
        if (state.isDraggingPreview && button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            state.targetRotationY = state.dragStartRotY - (mx - state.dragStartX) * 0.8f;
            state.rotationX = Mth.clamp(state.dragStartRotX - (my - state.dragStartY) * 0.5f, -45, 45);
            return true;
        }

        if (state.activeSlider >= 0 && button == 0) {
        int panelX = (this.width - MobConfigScreenRenderer.scaledPanelWidth()) / 2;
        int slidersX = panelX + MobConfigScreenRenderer.scaledPreviewSize() + s(40);
        state.updateSliderValue((int) mouseX, slidersX, MobConfigScreenRenderer.scaledSliderWidth());
        return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        MobConfigScreenState state = requireState();
        if (button == 0) {
            state.isDraggingPreview = false;
            state.activeSlider = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MobConfigScreenState state = requireState();
        int panelX = (this.width - MobConfigScreenRenderer.scaledPanelWidth()) / 2;
        int panelY = (this.height - MobConfigScreenRenderer.scaledPanelHeight()) / 2;
        int previewX = panelX + s(15);
        int previewY = panelY + s(DesignTokens.Spacing.HEADER_HEIGHT) + MobConfigScreenRenderer.scaledTabHeight() + s(8) + s(10);
        int previewSize = MobConfigScreenRenderer.scaledPreviewSize();

        if (mouseX >= previewX && mouseX < previewX + previewSize &&
            mouseY >= previewY && mouseY < previewY + previewSize) {
            state.previewZoom = Mth.clamp(state.previewZoom + (float) scrollY * 0.1f, 0.5f, 2.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        MobConfigScreenState state = requireState();
        // Save preset dialog
        if (state.showSavePresetDialog) {
            if (keyCode == 256) { // ESC
                state.showSavePresetDialog = false;
                state.presetNameInput.setLength(0);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                if (state.presetNameInput.length() > 0) {
                    state.saveCurrentAsPreset(state.presetNameInput.toString());
                    state.showSavePresetDialog = false;
                    state.presetNameInput.setLength(0);
                }
                return true;
            }
            if (keyCode == 259 && state.presetNameInput.length() > 0) { // Backspace
                state.presetNameInput.deleteCharAt(state.presetNameInput.length() - 1);
                return true;
            }
            return true;
        }

        // Delete preset dialog
        if (state.deleteConfirmPreset != null) {
            if (keyCode == 256) {
                state.deleteConfirmPreset = null;
                return true;
            }
            return true;
        }

        // Numeric input mode
        if (state.editingSlider >= 0) {
            if (keyCode == 257 || keyCode == 335) {
                state.commitNumericInput();
                return true;
            }
            if (keyCode == 256) {
                state.editingSlider = -1;
                state.inputBuffer.setLength(0);
                return true;
            }
            if (keyCode == 259 && state.inputBuffer.length() > 0) {
                state.inputBuffer.deleteCharAt(state.inputBuffer.length() - 1);
                return true;
            }
            if (keyCode == 258) { // Tab
                state.commitNumericInput();
                int numSliders = state.selectedTab == 2 ? 1 : 3;
                int localSlider = state.editingSlider % 10;
                int nextLocal = (localSlider + 1) % numSliders;
                state.editingSlider = state.selectedTab * 10 + nextLocal;
                state.inputBuffer.setLength(0);
                state.inputBuffer.append(String.format("%.1f", state.getSliderValue(state.editingSlider)));
                state.inputCursorBlink = System.currentTimeMillis();
                return true;
            }
            return true;
        }

        // ESC key
        if (keyCode == 256) {
            if (state.showingConfirmDialog) {
                state.showingConfirmDialog = false;
                return true;
            }
            if (state.hasUnsavedChanges() && !state.confirmedDiscard) {
                state.showingConfirmDialog = true;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        MobConfigScreenState state = requireState();
        // Preset name input
        if (state.showSavePresetDialog) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '_' || codePoint == '-') {
                if (state.presetNameInput.length() < 16) {
                    state.presetNameInput.append(codePoint);
                }
                return true;
            }
            return true;
        }

        // Numeric input for sliders
        if (state.editingSlider >= 0) {
            if (Character.isDigit(codePoint) || codePoint == '.' || codePoint == '-') {
                if (state.inputBuffer.length() < 8) {
                    if (codePoint == '.' && state.inputBuffer.toString().contains(".")) {
                        return true;
                    }
                    if (codePoint == '-' && state.inputBuffer.length() > 0) {
                        return true;
                    }
                    state.inputBuffer.append(codePoint);
                }
                return true;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Nonnull
    private MobConfigScreenState requireState() {
        return Objects.requireNonNull(state, "state");
    }

    @Nonnull
    private MobConfigScreenRenderer requireRenderer() {
        return Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        MobConfigScreenState localState = this.state;
        if (mc.options != null && localState != null) {
            mc.options.menuBackgroundBlurriness().set(localState.originalBlurValue);
        }
        super.onClose();
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Don't render default background
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Disabled
    }

    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        // Don't render default background
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
