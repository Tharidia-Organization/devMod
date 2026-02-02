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
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class EditorHubScreen extends Screen {

    private static final int CONTENT_WIDTH = 340;
    private static final int HEADER_GAP = 18;
    private static final int BUTTON_GAP = 4;
    private static final int SECTION_GAP = 12;

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
        addSection("devmod.editor_hub.section.items",
            actionButton("editor-item-auto", ActionIds.UI_ITEM_EDITOR_OPEN_AUTO, EditorButton.Style.PRIMARY),
            actionButton("editor-item-weapon", ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON, EditorButton.Style.NORMAL),
            actionButton("editor-item-armor", ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR, EditorButton.Style.NORMAL),
            actionButton("editor-item-shield", ActionIds.UI_ITEM_EDITOR_OPEN_SHIELD, EditorButton.Style.NORMAL),
            actionButton("editor-item-general", ActionIds.UI_ITEM_EDITOR_OPEN_GENERAL, EditorButton.Style.NORMAL),
            actionButton("editor-item-recipe", ActionIds.UI_ITEM_EDITOR_OPEN_RECIPE, EditorButton.Style.NORMAL),
            actionButton("editor-item-food", ActionIds.UI_ITEM_EDITOR_OPEN_FOOD, EditorButton.Style.NORMAL),
            actionButton("editor-item-fuel", ActionIds.UI_ITEM_EDITOR_OPEN_FUEL, EditorButton.Style.NORMAL),
            actionButton("editor-item-usable", ActionIds.UI_ITEM_EDITOR_OPEN_USABLE, EditorButton.Style.NORMAL)
        );

        addSection("devmod.editor_hub.section.mobs",
            actionButton("editor-mob-config", ActionIds.UI_MOB_CONFIG_OPEN, EditorButton.Style.NORMAL),
            actionButton("editor-mob-equipment", ActionIds.UI_MOB_EQUIPMENT_OPEN, EditorButton.Style.NORMAL)
        );

        addSection("devmod.editor_hub.section.quests",
            actionButton("editor-quest", ActionIds.UI_QUEST_EDITOR_OPEN, EditorButton.Style.NORMAL),
            actionButton("editor-endurance", ActionIds.UI_ENDURANCE_EDITOR_OPEN, EditorButton.Style.NORMAL),
            actionButton("editor-stamina", ActionIds.UI_STAMINA_EDITOR_OPEN, EditorButton.Style.NORMAL)
        );

        addSection("devmod.editor_hub.section.world",
            actionButton("editor-room-bounds", ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN, EditorButton.Style.NORMAL)
        );
    }

    private void addSection(String titleKey, EditorButton... buttons) {
        String title = Objects.requireNonNull(I18n.translate(titleKey).getString(), "section title");
        List<EditorButton> list = List.of(buttons);
        sections.add(new Section(title, list));
        allButtons.addAll(list);
    }

    private EditorButton actionButton(String id, String actionId, EditorButton.Style style) {
        String label = resolveLabel(actionId);
        String description = resolveDescription(actionId);
        EditorButton button = new EditorButton(id, label).style(style);
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
        int contentX = (this.width - scaledContentWidth) / 2;
        int y = UIScaleManager.scale(28);

        for (Section section : sections) {
            AxiomRenderer.drawSectionHeader(graphics, safeFont, contentX, y, section.title());
            y += UIScaleManager.scale(HEADER_GAP);

            for (EditorButton button : section.buttons()) {
                button.render(graphics, contentX, y, scaledContentWidth, scaledButtonHeight, mouseX, mouseY);
                y += scaledButtonHeight + UIScaleManager.scale(BUTTON_GAP);
            }

            y += UIScaleManager.scale(SECTION_GAP);
        }

        int buttonWidth = UIScaleManager.scale(110);
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - UIScaleManager.scale(32);
        backButton.render(graphics, buttonX, buttonY, buttonWidth, scaledButtonHeight, mouseX, mouseY);
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
