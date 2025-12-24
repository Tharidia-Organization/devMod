package com.devmod.ui.screens;

import com.devmod.network.EquipMobPayload;
import com.devmod.ui.AxiomRenderer;
import com.devmod.ui.editor.core.UIConstants;
import com.devmod.ui.editor.components.EditorButton;
import com.devmod.util.I18n;
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

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Screen per modificare l'equipaggiamento di un mob.
 * Refactored con Axiom-style UI.
 */
@OnlyIn(Dist.CLIENT)
public class MobEquipmentScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 300;
    private static final int ROW_HEIGHT = 28;
    private static final int INPUT_WIDTH = 140;

    private final Mob mob;
    private final Screen parentScreen;

    private EditBox mainHand, offHand, head, chest, legs, feet;

    // Blur control
    private int originalBlurValue = 0;

    // Error state
    private String errorMessage = null;
    private int errorDisplayTicks = 0;
    private EditBox errorField = null;
    private final EditorButton applyButton = new EditorButton("mob-equip-apply", "Apply").style(EditorButton.Style.PRIMARY);
    private final EditorButton backButton = new EditorButton("mob-equip-back", "Back").style(EditorButton.Style.NORMAL);

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

        applyButton.onClick(this::save);
        backButton.onClick(this::onClose);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int inputX = panelX + PANEL_WIDTH - UIConstants.Spacing.PANEL_PADDING - INPUT_WIDTH;

        // Start after header
        int fieldY = panelY + UIConstants.Spacing.HEADER_HEIGHT + UIConstants.Spacing.PANEL_PADDING + 20;

        // Weapons section fields
        mainHand = createInputField(inputX, fieldY, getItemName(EquipmentSlot.MAINHAND));
        fieldY += ROW_HEIGHT;

        offHand = createInputField(inputX, fieldY, getItemName(EquipmentSlot.OFFHAND));
        fieldY += ROW_HEIGHT + UIConstants.Spacing.GAP_LARGE + 16; // separator + section header

        // Armor section fields
        head = createInputField(inputX, fieldY, getItemName(EquipmentSlot.HEAD));
        fieldY += ROW_HEIGHT;

        chest = createInputField(inputX, fieldY, getItemName(EquipmentSlot.CHEST));
        fieldY += ROW_HEIGHT;

        legs = createInputField(inputX, fieldY, getItemName(EquipmentSlot.LEGS));
        fieldY += ROW_HEIGHT;

        feet = createInputField(inputX, fieldY, getItemName(EquipmentSlot.FEET));
    }

    private EditBox createInputField(int x, int y, String value) {
        EditBox field = new EditBox(Objects.requireNonNull(font), x, y, INPUT_WIDTH, 18,
            Objects.requireNonNull(Component.empty()));
        if (value == null) value = "";
        field.setValue(value);
        field.setMaxLength(64);
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
        // Dark background
        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // Main panel
        var safeFont = Objects.requireNonNull(font);
        AxiomRenderer.drawPanel(graphics, safeFont, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
            "Equipment: " + mob.getName().getString());

        int contentX = panelX + UIConstants.Spacing.PANEL_PADDING;
        int contentY = panelY + UIConstants.Spacing.HEADER_HEIGHT + UIConstants.Spacing.PANEL_PADDING;
        int contentWidth = PANEL_WIDTH - UIConstants.Spacing.PANEL_PADDING * 2;

        // Weapons section header
        AxiomRenderer.drawSectionHeader(graphics, safeFont, contentX, contentY, "Weapons");
        contentY += 16;

        // Weapon slots
        drawEquipmentLabel(graphics, contentX, contentY, "Main Hand:", UIConstants.Accent.RED(),
            mob.getItemBySlot(EquipmentSlot.MAINHAND));
        contentY += ROW_HEIGHT;

        drawEquipmentLabel(graphics, contentX, contentY, "Off Hand:", UIConstants.Accent.BLUE(),
            mob.getItemBySlot(EquipmentSlot.OFFHAND));
        contentY += ROW_HEIGHT;

        // Separator
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += UIConstants.Spacing.GAP_LARGE;

        // Armor section header
        AxiomRenderer.drawSectionHeader(graphics, safeFont, contentX, contentY, "Armor");
        contentY += 16;

        // Armor slots
        drawEquipmentLabel(graphics, contentX, contentY, "Head:", UIConstants.BodyPart.HEAD,
            mob.getItemBySlot(EquipmentSlot.HEAD));
        contentY += ROW_HEIGHT;

        drawEquipmentLabel(graphics, contentX, contentY, "Chest:", UIConstants.BodyPart.BODY,
            mob.getItemBySlot(EquipmentSlot.CHEST));
        contentY += ROW_HEIGHT;

        drawEquipmentLabel(graphics, contentX, contentY, "Legs:", UIConstants.BodyPart.LEGS,
            mob.getItemBySlot(EquipmentSlot.LEGS));
        contentY += ROW_HEIGHT;

        drawEquipmentLabel(graphics, contentX, contentY, "Feet:", UIConstants.Accent.PURPLE(),
            mob.getItemBySlot(EquipmentSlot.FEET));
        contentY += ROW_HEIGHT + UIConstants.Spacing.GAP_LARGE;

        // Separator
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += UIConstants.Spacing.GAP_LARGE;

        // Action buttons
        int buttonWidth = 100;
        int buttonGap = 10;
        int buttonsX = panelX + (PANEL_WIDTH - buttonWidth * 2 - buttonGap) / 2;
        int backX = buttonsX + buttonWidth + buttonGap;
        applyButton.render(graphics, buttonsX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);
        backButton.render(graphics, backX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Render widgets (EditBoxes)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw red border around error field (after widgets render)
        if (errorField != null && errorDisplayTicks > 0) {
            int fx = errorField.getX() - 1;
            int fy = errorField.getY() - 1;
            int fw = errorField.getWidth() + 2;
            int fh = errorField.getHeight() + 2;
            // Draw red border
            graphics.fill(fx, fy, fx + fw, fy + 1, UIConstants.Accent.RED()); // top
            graphics.fill(fx, fy + fh - 1, fx + fw, fy + fh, UIConstants.Accent.RED()); // bottom
            graphics.fill(fx, fy, fx + 1, fy + fh, UIConstants.Accent.RED()); // left
            graphics.fill(fx + fw - 1, fy, fx + fw, fy + fh, UIConstants.Accent.RED()); // right

            // Show error message
            String msg = errorMessage != null ? errorMessage : "";
            int textWidth = safeFont.width(msg);
            int errorX = (this.width - textWidth) / 2;
            graphics.drawString(safeFont, msg, errorX, panelY + PANEL_HEIGHT + 8, UIConstants.Accent.RED(), false);

            errorDisplayTicks--;
            if (errorDisplayTicks <= 0) {
                errorMessage = null;
                errorField = null;
            }
        }

        // Footer hint
        int footerY = panelY + PANEL_HEIGHT + (errorMessage != null ? 24 : 8);
        AxiomRenderer.drawHint(graphics, safeFont, panelX, footerY, "Enter item IDs (e.g., minecraft:diamond_sword)");
    }

    private void drawEquipmentLabel(GuiGraphics graphics, int x, int y, String label, int accentColor, ItemStack currentItem) {
        // Accent bar
        graphics.fill(x, y + 4, x + 3, y + 14, accentColor);

        // Label
        graphics.drawString(Objects.requireNonNull(font), label, x + 8, y + 5, UIConstants.Text.PRIMARY(), false);

        // Current item preview (if any)
        if (!currentItem.isEmpty()) {
            graphics.renderItem(currentItem, x + 70, y);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (applyButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (backButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = applyButton.mouseReleased(mouseX, mouseY, button) ||
                          backButton.mouseReleased(mouseX, mouseY, button);
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    private void save() {
        if (mainHand == null || offHand == null || head == null ||
            chest == null || legs == null || feet == null) {
            return;
        }

        // Clear previous error state
        errorField = null;
        errorMessage = null;

        // Validate each field - empty is allowed, but non-empty must be valid item ID
        EditBox[] fields = {mainHand, offHand, head, chest, legs, feet};
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
            mainHand.getValue().trim(),
            offHand.getValue().trim(),
            head.getValue().trim(),
            chest.getValue().trim(),
            legs.getValue().trim(),
            feet.getValue().trim()
        ));

        // Show success feedback to user
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                I18n.translate("devmod.equip.updated_for", mob.getName().getString()),
                true
            );
        }
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
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur
    }

    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}
