package com.devmod.client.ui.screens;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.network.EquipMobPayload;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class MobEquipmentScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 300;
    private static final int ROW_HEIGHT = 28;
    private static final int INPUT_WIDTH = 140;
    private static final int DIALOG_WIDTH = 240;
    private static final int DIALOG_HEIGHT = 90;

    private final Mob mob;
    private final Screen parentScreen;

    @Nullable
    private EditBox mainHand;
    @Nullable
    private EditBox offHand;
    @Nullable
    private EditBox head;
    @Nullable
    private EditBox chest;
    @Nullable
    private EditBox legs;
    @Nullable
    private EditBox feet;

    private String originalMainHand = "";
    private String originalOffHand = "";
    private String originalHead = "";
    private String originalChest = "";
    private String originalLegs = "";
    private String originalFeet = "";

    // Blur control
    private int originalBlurValue = 0;

    // Error state
    @Nullable
    private String errorMessage = null;
    private int errorDisplayTicks = 0;
    @Nullable
    private EditBox errorField = null;
    private final EditorButton applyButton = new EditorButton("mob-equip-apply", "Apply").style(EditorButton.Style.PRIMARY);
    private final EditorButton backButton = new EditorButton("mob-equip-back", "Back").style(EditorButton.Style.NORMAL);
    private final EditorButton discardButton = new EditorButton("mob-equip-discard", "Discard").style(EditorButton.Style.DANGER);
    private final EditorButton cancelDiscardButton = new EditorButton("mob-equip-cancel", "Cancel").style(EditorButton.Style.NORMAL);
    private boolean showingConfirmDialog = false;

    public MobEquipmentScreen(Mob mob, Screen parentScreen) {
        super(I18n.translate("devmod.screen.mob_equipment", mob.getName().getString()));
        this.mob = mob;
        this.parentScreen = parentScreen;

        // Disable menu blur when opening this screen
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }
    }

    @Override
    protected void init() {
        if (font == null) return;
        UIScaleManager.update();

        applyButton.onClick(this::save);
        backButton.onClick(this::requestClose);
        discardButton.onClick(this::discardAndClose);
        cancelDiscardButton.onClick(() -> showingConfirmDialog = false);

        int scaledPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int scaledPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int scaledInputWidth = UIScaleManager.scale(INPUT_WIDTH);
        int panelX = (this.width - scaledPanelWidth) / 2;
        int panelY = (this.height - scaledPanelHeight) / 2;
        int inputX = panelX + scaledPanelWidth - UIScaleManager.scale(DesignTokens.Spacing.PANEL_PADDING) - scaledInputWidth;

        // Start after header
        int fieldY = panelY + DesignTokens.Spacing.HEADER_HEIGHT + DesignTokens.Spacing.PANEL_PADDING + 20;

        // Weapons section fields
        String mainHandValue = getItemName(EquipmentSlot.MAINHAND);
        originalMainHand = normalize(mainHandValue);
        mainHand = createInputField(inputX, fieldY, mainHandValue);
        fieldY += ROW_HEIGHT;

        String offHandValue = getItemName(EquipmentSlot.OFFHAND);
        originalOffHand = normalize(offHandValue);
        offHand = createInputField(inputX, fieldY, offHandValue);
        fieldY += ROW_HEIGHT + DesignTokens.Spacing.GAP_LARGE + 16; // separator + section header

        // Armor section fields
        String headValue = getItemName(EquipmentSlot.HEAD);
        originalHead = normalize(headValue);
        head = createInputField(inputX, fieldY, headValue);
        fieldY += ROW_HEIGHT;

        String chestValue = getItemName(EquipmentSlot.CHEST);
        originalChest = normalize(chestValue);
        chest = createInputField(inputX, fieldY, chestValue);
        fieldY += ROW_HEIGHT;

        String legsValue = getItemName(EquipmentSlot.LEGS);
        originalLegs = normalize(legsValue);
        legs = createInputField(inputX, fieldY, legsValue);
        fieldY += ROW_HEIGHT;

        String feetValue = getItemName(EquipmentSlot.FEET);
        originalFeet = normalize(feetValue);
        feet = createInputField(inputX, fieldY, feetValue);
        updateButtonStates();
    }

    private EditBox createInputField(int x, int y, String value) {
        EditBox field = new EditBox(Objects.requireNonNull(font), x, y, INPUT_WIDTH, 18,
            Objects.requireNonNull(Component.empty()));
        if (value == null) value = "";
        field.setValue(value);
        field.setMaxLength(64);
        field.setBordered(false);
        field.setTextColor(DesignTokens.Text.PRIMARY());
        field.setTextColorUneditable(DesignTokens.Text.MUTED());
        field.setResponder(text -> {
            clearError();
            updateButtonStates();
        });
        this.addRenderableWidget(field);
        return field;
    }

    private String getItemName(@Nonnull EquipmentSlot slot) {
        ItemStack stack = mob.getItemBySlot(slot);
        if (stack.isEmpty()) return "";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()));
        return Objects.requireNonNull(key).toString();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        if (!mob.isAlive()) {
            var mc = minecraft;
            var player = mc != null ? mc.player : null;
            if (player != null) {
                player.displayClientMessage(
                    I18n.translate("devmod.mobconfig.target_died"),
                    false
                );
            }
            closeNow();
            return;
        }
        // Dark background
        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // Main panel
        var safeFont = Objects.requireNonNull(font);
        AxiomRenderer.drawPanel(graphics, safeFont, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
            "Equipment: " + mob.getName().getString());
        updateButtonStates();

        int contentX = panelX + DesignTokens.Spacing.PANEL_PADDING;
        int contentY = panelY + DesignTokens.Spacing.HEADER_HEIGHT + DesignTokens.Spacing.PANEL_PADDING;
        int contentWidth = PANEL_WIDTH - DesignTokens.Spacing.PANEL_PADDING * 2;

        // Weapons section header
        AxiomRenderer.drawSectionHeader(graphics, safeFont, contentX, contentY, "Weapons");
        contentY += 16;

        // Weapon slots
        drawEquipmentLabel(graphics, contentX, contentY, "Main Hand:", DesignTokens.Accent.RED(),
            mob.getItemBySlot(EquipmentSlot.MAINHAND));
        contentY += ROW_HEIGHT;

        drawEquipmentLabel(graphics, contentX, contentY, "Off Hand:", DesignTokens.Accent.BLUE(),
            mob.getItemBySlot(EquipmentSlot.OFFHAND));
        contentY += ROW_HEIGHT;

        // Separator
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += DesignTokens.Spacing.GAP_LARGE;

        // Armor section header
        AxiomRenderer.drawSectionHeader(graphics, safeFont, contentX, contentY, "Armor");
        contentY += 16;

        // Armor slots
        drawEquipmentLabel(graphics, contentX, contentY, "Head:", DesignTokens.Accent.CYAN,
            mob.getItemBySlot(EquipmentSlot.HEAD));
        contentY += ROW_HEIGHT;

        drawEquipmentLabel(graphics, contentX, contentY, "Chest:", DesignTokens.Accent.GREEN,
            mob.getItemBySlot(EquipmentSlot.CHEST));
        contentY += ROW_HEIGHT;

        drawEquipmentLabel(graphics, contentX, contentY, "Legs:", DesignTokens.Accent.RED,
            mob.getItemBySlot(EquipmentSlot.LEGS));
        contentY += ROW_HEIGHT;

        drawEquipmentLabel(graphics, contentX, contentY, "Feet:", DesignTokens.Accent.PURPLE(),
            mob.getItemBySlot(EquipmentSlot.FEET));
        contentY += ROW_HEIGHT + DesignTokens.Spacing.GAP_LARGE;

        // Separator
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += DesignTokens.Spacing.GAP_LARGE;

        // Action buttons
        int buttonWidth = 100;
        int buttonGap = 10;
        int buttonsX = panelX + (PANEL_WIDTH - buttonWidth * 2 - buttonGap) / 2;
        int backX = buttonsX + buttonWidth + buttonGap;
        applyButton.render(graphics, buttonsX, contentY, buttonWidth, DesignTokens.Size.BUTTON_HEIGHT, mouseX, mouseY);
        backButton.render(graphics, backX, contentY, buttonWidth, DesignTokens.Size.BUTTON_HEIGHT, mouseX, mouseY);

        renderInputBackgrounds(graphics);

        // Render widgets (EditBoxes)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw red border around error field (after widgets render)
        EditBox localErrorField = errorField;
        if (localErrorField != null && errorDisplayTicks > 0) {
            int fx = localErrorField.getX() - 1;
            int fy = localErrorField.getY() - 1;
            int fw = localErrorField.getWidth() + 2;
            int fh = localErrorField.getHeight() + 2;
            // Draw red border
            graphics.fill(fx, fy, fx + fw, fy + 1, DesignTokens.Accent.RED()); // top
            graphics.fill(fx, fy + fh - 1, fx + fw, fy + fh, DesignTokens.Accent.RED()); // bottom
            graphics.fill(fx, fy, fx + 1, fy + fh, DesignTokens.Accent.RED()); // left
            graphics.fill(fx + fw - 1, fy, fx + fw, fy + fh, DesignTokens.Accent.RED()); // right

            // Show error message
            String msg = errorMessage != null ? errorMessage : "";
            int textWidth = safeFont.width(msg);
            int errorX = (this.width - textWidth) / 2;
            graphics.drawString(safeFont, msg, errorX, panelY + PANEL_HEIGHT + 8, DesignTokens.Accent.RED(), false);

            errorDisplayTicks--;
            if (errorDisplayTicks <= 0) {
                errorMessage = null;
                errorField = null;
            }
        }

        // Footer hint
        int footerY = panelY + PANEL_HEIGHT + (errorMessage != null ? 24 : 8);
        AxiomRenderer.drawHint(graphics, safeFont, panelX, footerY, "Enter item IDs (e.g., minecraft:diamond_sword). Leave blank to clear.");

        if (showingConfirmDialog) {
            renderConfirmDialog(graphics, mouseX, mouseY);
        }
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        renderInputBackground(graphics, mainHand);
        renderInputBackground(graphics, offHand);
        renderInputBackground(graphics, head);
        renderInputBackground(graphics, chest);
        renderInputBackground(graphics, legs);
        renderInputBackground(graphics, feet);
    }

    private void renderInputBackground(GuiGraphics graphics, @Nullable EditBox field) {
        if (field != null) {
            AxiomRenderer.drawInputBackground(graphics, field.getX(), field.getY(), field.getWidth(), field.getHeight(),
                field.isFocused());
        }
    }

    private void drawEquipmentLabel(GuiGraphics graphics, int x, int y, String label, int accentColor, ItemStack currentItem) {
        // Accent bar
        graphics.fill(x, y + 4, x + 3, y + 14, accentColor);

        // Label
        graphics.drawString(Objects.requireNonNull(font), label, x + 8, y + 5, DesignTokens.Text.PRIMARY(), false);

        // Current item preview (if any)
        if (!currentItem.isEmpty()) {
            graphics.renderItem(currentItem, x + 70, y);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showingConfirmDialog) {
            if (button != 0) return true;
            boolean handled = discardButton.mouseClicked(mouseX, mouseY, button) ||
                cancelDiscardButton.mouseClicked(mouseX, mouseY, button);
            if (!handled) {
                int dx = (this.width - DIALOG_WIDTH) / 2;
                int dy = (this.height - DIALOG_HEIGHT) / 2;
                if (!AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, dx, dy, DIALOG_WIDTH, DIALOG_HEIGHT)) {
                    showingConfirmDialog = false;
                }
            }
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (applyButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (backButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (showingConfirmDialog) {
            discardButton.mouseReleased(mouseX, mouseY, button);
            cancelDiscardButton.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        boolean handled = applyButton.mouseReleased(mouseX, mouseY, button) ||
                          backButton.mouseReleased(mouseX, mouseY, button);
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    private void save() {
        // Capture nullable fields in local variables for null safety
        EditBox localMainHand = mainHand;
        EditBox localOffHand = offHand;
        EditBox localHead = head;
        EditBox localChest = chest;
        EditBox localLegs = legs;
        EditBox localFeet = feet;

        if (localMainHand == null || localOffHand == null || localHead == null ||
            localChest == null || localLegs == null || localFeet == null) {
            return;
        }

        // Clear previous error state
        errorField = null;
        errorMessage = null;

        // Validate each field - empty is allowed, but non-empty must be valid item ID
        EditBox[] fields = {localMainHand, localOffHand, localHead, localChest, localLegs, localFeet};
        String[] names = {"Main Hand", "Off Hand", "Head", "Chest", "Legs", "Feet"};

        for (int i = 0; i < fields.length; i++) {
            String value = fields[i].getValue().trim();
            if (!value.isEmpty() && !isValidItemId(value)) {
                errorField = fields[i];
                errorMessage = "Invalid item: " + names[i];
                errorDisplayTicks = 100;
                return;
            }
        }

        // All valid - send to server
        PacketDistributor.sendToServer(new EquipMobPayload(
            mob.getId(),
            localMainHand.getValue().trim(),
            localOffHand.getValue().trim(),
            localHead.getValue().trim(),
            localChest.getValue().trim(),
            localLegs.getValue().trim(),
            localFeet.getValue().trim()
        ));

        // Show success feedback to user
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                I18n.translate("devmod.equip.updated_for", mob.getName().getString()),
                true
            );
        }
        syncOriginalValues();
        updateButtonStates();
    }

    /**
     * Validates if the given string is a valid item ID in the registry.
     */
    private boolean isValidItemId(@Nonnull String itemId) {
        try {
            ResourceLocation loc = Objects.requireNonNull(ResourceLocation.parse(itemId));
            return BuiltInRegistries.ITEM.containsKey(loc);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onClose() {
        requestClose();
    }

    private void requestClose() {
        if (showingConfirmDialog) {
            showingConfirmDialog = false;
            return;
        }
        if (hasUnsavedChanges()) {
            showingConfirmDialog = true;
            return;
        }
        closeNow();
    }

    private void discardAndClose() {
        showingConfirmDialog = false;
        closeNow();
    }

    private void closeNow() {
        // Restore original blur setting
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }

        mc.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Disable blur - just solid dimmed background
        graphics.fill(0, 0, this.width, this.height,
            DesignTokens.withAlpha(DesignTokens.Neutral.N950, DesignTokens.Alpha.A75));
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur
    }

    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height,
            DesignTokens.withAlpha(DesignTokens.Neutral.N950, DesignTokens.Alpha.A75));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showingConfirmDialog) {
            if (keyCode == 256) {
                showingConfirmDialog = false;
                return true;
            }
            return true;
        }
        if (keyCode == 256) {
            requestClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateButtonStates() {
        applyButton.enabled(hasUnsavedChanges());
    }

    private boolean hasUnsavedChanges() {
        // Capture nullable fields in local variables for null safety
        EditBox localMainHand = mainHand;
        EditBox localOffHand = offHand;
        EditBox localHead = head;
        EditBox localChest = chest;
        EditBox localLegs = legs;
        EditBox localFeet = feet;

        if (localMainHand == null || localOffHand == null || localHead == null ||
            localChest == null || localLegs == null || localFeet == null) {
            return false;
        }
        return !normalize(localMainHand.getValue()).equals(originalMainHand) ||
            !normalize(localOffHand.getValue()).equals(originalOffHand) ||
            !normalize(localHead.getValue()).equals(originalHead) ||
            !normalize(localChest.getValue()).equals(originalChest) ||
            !normalize(localLegs.getValue()).equals(originalLegs) ||
            !normalize(localFeet.getValue()).equals(originalFeet);
    }

    private void syncOriginalValues() {
        // Capture nullable fields in local variables for null safety
        EditBox localMainHand = mainHand;
        EditBox localOffHand = offHand;
        EditBox localHead = head;
        EditBox localChest = chest;
        EditBox localLegs = legs;
        EditBox localFeet = feet;

        if (localMainHand == null || localOffHand == null || localHead == null ||
            localChest == null || localLegs == null || localFeet == null) {
            return;
        }
        originalMainHand = normalize(localMainHand.getValue());
        originalOffHand = normalize(localOffHand.getValue());
        originalHead = normalize(localHead.getValue());
        originalChest = normalize(localChest.getValue());
        originalLegs = normalize(localLegs.getValue());
        originalFeet = normalize(localFeet.getValue());
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private void clearError() {
        errorMessage = null;
        errorField = null;
        errorDisplayTicks = 0;
    }

    private void renderConfirmDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height,
            DesignTokens.withAlpha(DesignTokens.Mask.NONE, DesignTokens.Alpha.A63));

        int dx = (this.width - DIALOG_WIDTH) / 2;
        int dy = (this.height - DIALOG_HEIGHT) / 2;

        graphics.fill(dx, dy, dx + DIALOG_WIDTH, dy + DIALOG_HEIGHT, DesignTokens.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, dx, dy, DIALOG_WIDTH, DIALOG_HEIGHT, DesignTokens.Accent.ORANGE());

        var safeFont = Objects.requireNonNull(font);
        String title = "Unsaved Changes";
        int titleX = dx + (DIALOG_WIDTH - safeFont.width(title)) / 2;
        graphics.drawString(safeFont, title, titleX, dy + 10, DesignTokens.Text.PRIMARY(), false);

        String msg1 = "Discard equipment edits?";
        int msgX = dx + (DIALOG_WIDTH - safeFont.width(msg1)) / 2;
        graphics.drawString(safeFont, msg1, msgX, dy + 28, DesignTokens.Text.SECONDARY(), false);

        int btnW = 90;
        int btnH = DesignTokens.Size.BUTTON_HEIGHT;
        int btnGap = 10;
        int btnX = dx + (DIALOG_WIDTH - (btnW * 2 + btnGap)) / 2;
        int btnY = dy + DIALOG_HEIGHT - 26;

        discardButton.render(graphics, btnX, btnY, btnW, btnH, mouseX, mouseY);
        cancelDiscardButton.render(graphics, btnX + btnW + btnGap, btnY, btnW, btnH, mouseX, mouseY);
    }
}
