package com.devmod.client.ui.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorCard;
import com.devmod.client.ui.editor.components.EditorIcons;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.util.I18n;

import net.minecraft.world.item.ItemStack;

@OnlyIn(Dist.CLIENT)
public class EditorHubScreen extends Screen {

    // Layout constants - Modern Cards style
    private static final int CONTENT_WIDTH = 380;
    private static final int HEADER_GAP = 12;
    private static final int BUTTON_GAP = 6;
    private static final int CARD_PADDING = 12;
    private static final int CARD_GAP = 16;

    @Nullable
    private final Screen parent;
    private final List<Section> sections = new ArrayList<>();
    private final List<EditorButton> allButtons = new ArrayList<>();
    private final EditorButton backButton = new EditorButton("editor-hub-back", "Back");

    private int originalBlurValue = 0;

    private record Section(String title, List<EditorButton> buttons) {}

    public EditorHubScreen(@Nullable Screen parent) {
        super(I18n.screenTitle("editor_hub"));
        this.parent = parent;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }

        buildSections();
        backButton.style(EditorButton.Style.NORMAL);
    }

    @Override
    protected void init() {
        backButton.onClick(this::onClose);
    }

    private void buildSections() {
        // Item Editors section
        addSection("devmod.editor_hub.section.items",
            actionButton("editor-item-auto", ActionIds.UI_ITEM_EDITOR_OPEN_AUTO, EditorButton.Style.PRIMARY, EditorIcons.ITEM_AUTO),
            actionButton("editor-item-weapon", ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON, EditorButton.Style.NORMAL, EditorIcons.WEAPON),
            actionButton("editor-item-armor", ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR, EditorButton.Style.NORMAL, EditorIcons.ARMOR),
            actionButton("editor-item-shield", ActionIds.UI_ITEM_EDITOR_OPEN_SHIELD, EditorButton.Style.NORMAL, EditorIcons.SHIELD),
            actionButton("editor-item-general", ActionIds.UI_ITEM_EDITOR_OPEN_GENERAL, EditorButton.Style.NORMAL, EditorIcons.ITEM_GENERAL),
            actionButton("editor-item-recipe", ActionIds.UI_ITEM_EDITOR_OPEN_RECIPE, EditorButton.Style.NORMAL, EditorIcons.RECIPE),
            actionButton("editor-item-food", ActionIds.UI_ITEM_EDITOR_OPEN_FOOD, EditorButton.Style.NORMAL, EditorIcons.FOOD),
            actionButton("editor-item-fuel", ActionIds.UI_ITEM_EDITOR_OPEN_FUEL, EditorButton.Style.NORMAL, EditorIcons.FUEL),
            actionButton("editor-item-usable", ActionIds.UI_ITEM_EDITOR_OPEN_USABLE, EditorButton.Style.NORMAL, EditorIcons.USABLE)
        );

        // Mob Editors section
        addSection("devmod.editor_hub.section.mobs",
            actionButton("editor-mob-config", ActionIds.UI_MOB_CONFIG_OPEN, EditorButton.Style.NORMAL, EditorIcons.MOB),
            actionButton("editor-mob-equipment", ActionIds.UI_MOB_EQUIPMENT_OPEN, EditorButton.Style.NORMAL, EditorIcons.MOB_EQUIPMENT)
        );

        // Quest & Endurance section
        addSection("devmod.editor_hub.section.quests",
            actionButton("editor-quest", ActionIds.UI_QUEST_EDITOR_OPEN, EditorButton.Style.NORMAL, EditorIcons.QUEST),
            actionButton("editor-endurance", ActionIds.UI_ENDURANCE_EDITOR_OPEN, EditorButton.Style.NORMAL, EditorIcons.ENDURANCE),
            actionButton("editor-stamina", ActionIds.UI_STAMINA_EDITOR_OPEN, EditorButton.Style.NORMAL, EditorIcons.STAMINA)
        );

        // World Tools section
        addSection("devmod.editor_hub.section.world",
            actionButton("editor-room-bounds", ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN, EditorButton.Style.NORMAL, EditorIcons.ROOM_BOUNDS)
        );
    }

    private void addSection(String titleKey, EditorButton... buttons) {
        String title = Objects.requireNonNull(I18n.translate(titleKey).getString(), "section title");
        List<EditorButton> list = List.of(buttons);
        sections.add(new Section(title, list));
        allButtons.addAll(list);
    }

    private EditorButton actionButton(String id, String actionId, EditorButton.Style style, ItemStack icon) {
        String label = resolveLabel(actionId);
        String description = resolveDescription(actionId);
        EditorButton button = new EditorButton(id, label).style(style).itemIcon(icon);
        if (!description.isBlank()) {
            button.tooltip(description);
        }
        button.onClick(() -> invokeAction(actionId));
        return button;
    }

    private String resolveLabel(String actionId) {
        RadialAction action = ActionRegistry.getAction(actionId);
        if (action != null) {
            return Objects.requireNonNull(action.getLabel().getString(), "action label");
        }
        return actionId;
    }

    private String resolveDescription(String actionId) {
        RadialAction action = ActionRegistry.getAction(actionId);
        if (action != null) {
            return Objects.requireNonNull(action.getDescription().getString(), "action description");
        }
        return "";
    }

    @Nonnull
    private Font getSafeFont() {
        return Objects.requireNonNull(font, "font");
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        Font safeFont = getSafeFont();

        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);
        AxiomRenderer.drawCenteredTitle(graphics, safeFont, this.width, UIScaleManager.scale(8),
            I18n.translate("devmod.screen.editor_hub").getString());

        int scaledContentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        int scaledButtonHeight = UIScaleManager.scale(DesignTokens.Size.BUTTON_HEIGHT);
        int scaledCardPadding = UIScaleManager.scale(CARD_PADDING);
        int scaledHeaderGap = UIScaleManager.scale(HEADER_GAP);
        int scaledButtonGap = UIScaleManager.scale(BUTTON_GAP);
        int scaledCardGap = UIScaleManager.scale(CARD_GAP);
        int contentX = (this.width - scaledContentWidth) / 2;
        int y = UIScaleManager.scale(28);

        for (Section section : sections) {
            // Calculate card height
            int buttonCount = section.buttons().size();
            int headerHeight = UIScaleManager.getScaledLineHeight() + 4; // Header text + underline
            int buttonsHeight = buttonCount * scaledButtonHeight + (buttonCount - 1) * scaledButtonGap;
            int cardHeight = scaledCardPadding * 2 + headerHeight + scaledHeaderGap + buttonsHeight;

            // Draw card container
            EditorCard.render(graphics, contentX, y, scaledContentWidth, cardHeight);

            // Draw section header with accent underline inside card
            int headerX = contentX + scaledCardPadding;
            int headerY = y + scaledCardPadding;
            AxiomRenderer.drawEnhancedSectionHeader(graphics, safeFont, headerX, headerY,
                scaledContentWidth - scaledCardPadding * 2, section.title());

            // Draw buttons inside card
            int buttonY = headerY + headerHeight + scaledHeaderGap;
            int buttonWidth = scaledContentWidth - scaledCardPadding * 2;

            for (EditorButton button : section.buttons()) {
                button.render(graphics, headerX, buttonY, buttonWidth, scaledButtonHeight, mouseX, mouseY);
                buttonY += scaledButtonHeight + scaledButtonGap;
            }

            y += cardHeight + scaledCardGap;
        }

        // Back button with icon at bottom
        backButton.itemIcon(EditorIcons.BACK);
        int backButtonWidth = UIScaleManager.scale(110);
        int backButtonX = (this.width - backButtonWidth) / 2;
        int backButtonY = this.height - UIScaleManager.scale(32);
        backButton.render(graphics, backButtonX, backButtonY, backButtonWidth, scaledButtonHeight, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (EditorButton btn : allButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        handled |= backButton.mouseReleased(mouseX, mouseY, button);
        for (EditorButton btn : allButtons) {
            handled |= btn.mouseReleased(mouseX, mouseY, button);
        }
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void invokeAction(String actionId) {
        ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI);
        ActionRegistry.invoke(actionId, context);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }

        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, DesignTokens.Background.SCREEN);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur.
    }

    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, DesignTokens.Background.SCREEN);
    }
}
