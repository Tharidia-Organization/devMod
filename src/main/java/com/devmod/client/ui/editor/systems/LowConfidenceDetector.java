package com.devmod.client.ui.editor.systems;

import com.devmod.DevMod;
import com.devmod.config.EditorClientConfig;
import com.devmod.client.ui.editor.EditorModule;
import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.WeaponTypeDetector;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.EditorSpacing;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.modules.RangedModule;
import com.devmod.client.ui.editor.modules.WeaponModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Handles low-confidence weapon type detection warnings.
 * Displays a dialog when the weapon type detection confidence is below threshold,
 * allowing user to continue, whitelist, or cancel.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#37-confirmation-dialogs
 */
public final class LowConfidenceDetector extends BaseOverlay {

    // Dialog dimensions
    private static final int WIDTH = 320;
    private static final int HEIGHT = 176;
    private static final int PADDING = 12;
    private static final int TEXT_START_Y = 30;
    private static final int TEXT_LINE_STEP = 12;
    private static final int HINT_START_Y = 72;
    private static final int STATUS_OFFSET_Y = 44;
    private static final int BTN_WIDTH = 88;
    private static final int BTN_HEIGHT = 16;
    private static final int BTN_TEXT_Y = 4;
    private static final int BTN_TEXT_CONTINUE_X = 12;
    private static final int BTN_TEXT_WHITELIST_X = 10;
    private static final int BTN_TEXT_CANCEL_X = 18;

    private static final float MIN_CONFIDENCE_FOR_AUTO_EDIT = 0.8f;

    // State
    private WeaponTypeDetector.DetectionResult pendingDetection = null;
    private String statusMessage = null;
    private ItemStack currentItem = null;

    // Button bounds (computed during render)
    private ResponsiveLayout.Rect continueBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect whitelistBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect cancelBounds = ResponsiveLayout.Rect.EMPTY;

    // Callbacks
    private BiConsumer<String, Integer> statusConsumer;
    private Runnable onCancelCallback;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Set the status message consumer for showing feedback.
     */
    public void setStatusConsumer(BiConsumer<String, Integer> consumer) {
        this.statusConsumer = consumer;
    }

    /**
     * Set the callback for when user cancels (closes editor).
     */
    public void setOnCancelCallback(Runnable callback) {
        this.onCancelCallback = callback;
    }

    /**
     * Check if the given item should trigger a low confidence warning.
     * If so, shows the dialog.
     *
     * @param item         The item being edited
     * @param requestedTab The requested editor tab
     * @param activeModule The active editor module
     */
    public void checkAndWarn(ItemStack item, EditorStartTab requestedTab, EditorModule activeModule) {
        if (item == null || item.isEmpty()) return;

        var detection = WeaponTypeDetector.detectDetailed(item);
        if (detection == null) return;

        boolean isWeaponPath = requestedTab == EditorStartTab.WEAPON
            || activeModule instanceof WeaponModule
            || activeModule instanceof RangedModule;
        if (!isWeaponPath) return;

        float threshold = (float) Math.max(
            EditorClientConfig.EDITOR_WEAPON_MIN_CONFIDENCE.get(),
            (double) MIN_CONFIDENCE_FOR_AUTO_EDIT
        );

        if (detection.confidence() < threshold
            && EditorClientConfig.EDITOR_WEAPON_HEURISTIC_ENABLED.get()
            && detection.method() == WeaponTypeDetector.DetectionMethod.ATTRIBUTE_HEURISTIC) {

            this.currentItem = item;
            this.pendingDetection = detection;
            this.statusMessage = null;
            show();

            DevMod.LOGGER.warn("[LowConfidenceDetection] Low confidence: {} ({})",
                detection.type(), detection.method());
        }
    }

    /**
     * Get the pending detection result.
     */
    public WeaponTypeDetector.DetectionResult getPendingDetection() {
        return pendingDetection;
    }

    /**
     * Check if a detection is pending (dialog visible).
     */
    public boolean hasPendingDetection() {
        return pendingDetection != null && isVisible();
    }

    /**
     * Reset the system state and hide the dialog.
     */
    public void reset() {
        pendingDetection = null;
        statusMessage = null;
        currentItem = null;
        hideImmediate();
    }

    // =========================================================================
    // BaseOverlay IMPLEMENTATION
    // =========================================================================

    @Override
    protected int getPanelWidth() {
        return WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return HEIGHT;
    }

    @Override
    public OverlayType getType() {
        return OverlayType.LOW_CONFIDENCE;
    }

    @Override
    protected boolean shouldCloseOnClickOutside() {
        return false; // Dialog requires explicit button click
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
        if (pendingDetection == null || currentItem == null) return;

        Font safeFont = Objects.requireNonNull(font, "font");

        // Title
        graphics.drawString(safeFont, "Low Confidence Detection", x + PADDING, y + PADDING,
            UIConstants.Text.TITLE(), false);

        // Item info
        var id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(currentItem.getItem()));
        String itemId = id == null ? "<unknown>" : id.toString();

        int textX = x + PADDING;
        int textY = y + TEXT_START_Y;

        graphics.drawString(safeFont, "Item: " + itemId, textX, textY,
            UIConstants.Text.SECONDARY(), false);
        textY += TEXT_LINE_STEP;

        graphics.drawString(safeFont,
            "Detected: " + pendingDetection.type() + " via " + pendingDetection.method(),
            textX, textY, UIConstants.Text.SECONDARY(), false);
        textY += TEXT_LINE_STEP;

        graphics.drawString(safeFont,
            "Confidence: " + String.format("%.0f%%", pendingDetection.confidence() * 100),
            textX, textY, UIConstants.Text.SECONDARY(), false);

        // Hints
        textY = y + HINT_START_Y;
        graphics.drawString(safeFont, "Add to whitelist/tag to skip this warning next time.",
            textX, textY, UIConstants.Text.MUTED(), false);
        textY += TEXT_LINE_STEP;
        graphics.drawString(safeFont, "Config: config/devmod/weapon_whitelist.json",
            textX, textY, UIConstants.Text.MUTED(), false);

        // Status message
        if (statusMessage != null) {
            graphics.drawString(safeFont, statusMessage, textX, y + height - STATUS_OFFSET_Y,
                UIConstants.Accent.ORANGE(), false);
        }

        // Buttons
        int btnW = EditorSpacing.snapToGrid(BTN_WIDTH);
        int btnH = EditorSpacing.snapToGrid(BTN_HEIGHT);
        int btnSpacing = EditorSpacing.S;
        int totalBtnWidth = btnW * 3 + btnSpacing * 2;
        int btnStartX = x + (width - totalBtnWidth) / 2;
        int btnY = y + height - EditorSpacing.L;

        int btnContinueX = btnStartX;
        int btnWhitelistX = btnStartX + btnW + btnSpacing;
        int btnCancelX = btnStartX + (btnW + btnSpacing) * 2;

        // Draw button backgrounds
        graphics.fill(btnContinueX, btnY, btnContinueX + btnW, btnY + btnH,
            UIConstants.Background.INPUT());
        graphics.fill(btnWhitelistX, btnY, btnWhitelistX + btnW, btnY + btnH,
            UIConstants.Background.INPUT());
        graphics.fill(btnCancelX, btnY, btnCancelX + btnW, btnY + btnH,
            UIConstants.Background.INPUT());

        // Draw button labels
        graphics.drawString(safeFont, "Continue", btnContinueX + BTN_TEXT_CONTINUE_X,
            btnY + BTN_TEXT_Y, UIConstants.Text.PRIMARY(), false);
        graphics.drawString(safeFont, "Whitelist", btnWhitelistX + BTN_TEXT_WHITELIST_X,
            btnY + BTN_TEXT_Y, UIConstants.Text.PRIMARY(), false);
        graphics.drawString(safeFont, "Cancel", btnCancelX + BTN_TEXT_CANCEL_X,
            btnY + BTN_TEXT_Y, UIConstants.Text.PRIMARY(), false);

        // Cache button bounds for click handling
        continueBounds = new ResponsiveLayout.Rect(btnContinueX, btnY, btnW, btnH);
        whitelistBounds = new ResponsiveLayout.Rect(btnWhitelistX, btnY, btnW, btnH);
        cancelBounds = new ResponsiveLayout.Rect(btnCancelX, btnY, btnW, btnH);
    }

    @Override
    protected void onEscapePressed() {
        if (onCancelCallback != null) {
            onCancelCallback.run();
        }
        reset();
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        // Enter = Continue
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            reset();
            return true;
        }
        return true; // Consume all keys when visible
    }

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
                                          int panelX, int panelY, int panelW, int panelH) {
        if (pendingDetection == null) return true;

        if (continueBounds.contains(mouseX, mouseY)) {
            reset();
            return true;
        }

        if (whitelistBounds.contains(mouseX, mouseY)) {
            boolean added = addItemToWhitelist();
            if (!added) {
                statusMessage = "Whitelist update failed. Check logs.";
            } else {
                if (statusConsumer != null) {
                    var id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(currentItem.getItem()));
                    String label = id == null ? "<unknown>" : id.toString();
                    statusConsumer.accept("Whitelisted " + label, UIConstants.Accent.GREEN());
                }
                reset();
            }
            return true;
        }

        if (cancelBounds.contains(mouseX, mouseY)) {
            if (onCancelCallback != null) {
                onCancelCallback.run();
            }
            reset();
            return true;
        }

        return true; // Consume click
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private boolean addItemToWhitelist() {
        if (currentItem == null || currentItem.isEmpty()) {
            return false;
        }
        try {
            WeaponTypeDetector.WeaponType type = pendingDetection == null
                ? WeaponTypeDetector.WeaponType.GENERIC_MELEE
                : pendingDetection.type();

            if (type == WeaponTypeDetector.WeaponType.UNKNOWN
                || type == WeaponTypeDetector.WeaponType.NOT_A_WEAPON
                || type == WeaponTypeDetector.WeaponType.SHIELD) {
                type = WeaponTypeDetector.WeaponType.GENERIC_MELEE;
            }

            return WeaponTypeDetector.addToWhitelist(
                Objects.requireNonNull(currentItem.getItem()), type);
        } catch (Exception e) {
            DevMod.LOGGER.warn("[LowConfidenceDetection] Failed to add to whitelist", e);
            return false;
        }
    }
}
