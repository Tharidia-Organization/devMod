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
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.components.ScrollableContentArea;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public abstract class ActionHubScreen extends Screen {

    protected static final int CONTENT_WIDTH = 360;
    protected static final int HEADER_GAP = 18;
    protected static final int BUTTON_GAP = 4;
    protected static final int SECTION_GAP = 12;

    @Nullable
    private final Screen parent;
    private final String titleKey;
    private final List<Section> sections = new ArrayList<>();
    private final EditorButton backButton = new EditorButton("hub-back", I18n.ui("back").getString());
    private final EditorButton quickButton = new EditorButton("hub-mode-quick", I18n.ui("hub_mode.quick").getString());
    private final EditorButton advancedButton = new EditorButton("hub-mode-advanced", I18n.ui("hub_mode.advanced").getString());
    private final ScrollableContentArea scrollArea = new ScrollableContentArea();

    private int originalBlurValue = 0;
    private boolean showAdvanced = false;

    protected record HubItem(EditorButton button, boolean advancedOnly) {}
    protected record Section(String title, List<HubItem> items) {}

    protected ActionHubScreen(@Nullable Screen parent, String titleKey) {
        super(I18n.screenTitle(titleKey));
        this.parent = parent;
        this.titleKey = Objects.requireNonNull(titleKey, "titleKey");

        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }

        backButton.style(EditorButton.Style.NORMAL);
        quickButton.style(EditorButton.Style.GHOST).toggleable(true);
        advancedButton.style(EditorButton.Style.GHOST).toggleable(true);
    }

    protected abstract void buildSections();

    protected final void resetSections() {
        sections.clear();
    }

    protected void addSection(String titleKey, HubItem... items) {
        String title = Objects.requireNonNull(I18n.translate(titleKey).getString(), "section title");
        List<HubItem> list = List.of(items);
        sections.add(new Section(title, list));
    }

    protected EditorButton actionButton(String id, String actionId, EditorButton.Style style) {
        String label = resolveLabel(actionId);
        String description = resolveDescription(actionId);
        EditorButton button = new EditorButton(id, label).style(style);
        if (!description.isBlank()) {
            button.tooltip(description);
        }
        button.onClick(() -> invokeAction(actionId));
        return button;
    }

    protected HubItem actionItem(String id, String actionId, EditorButton.Style style) {
        return new HubItem(actionButton(id, actionId, style), false);
    }

    protected HubItem advancedActionItem(String id, String actionId, EditorButton.Style style) {
        return new HubItem(actionButton(id, actionId, style), true);
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
    protected void init() {
        backButton.onClick(this::onClose);
        quickButton.onClick(() -> setAdvanced(false));
        advancedButton.onClick(() -> setAdvanced(true));
        loadModeFromSettings();
        updateModeButtons();
        scrollArea.reset();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        Font safeFont = getSafeFont();

        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);
        AxiomRenderer.drawCenteredTitle(graphics, safeFont, this.width, UIScaleManager.scale(8),
            I18n.translate("devmod.screen." + titleKey).getString());

        int scaledContentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        int contentX = (this.width - scaledContentWidth) / 2;
        int contentY = UIScaleManager.scale(28);
        int bottomPadding = UIScaleManager.scale(52);
        int scrollHeight = Math.max(UIScaleManager.scale(60), this.height - contentY - bottomPadding);

        scrollArea.render(graphics, contentX, contentY, scaledContentWidth, scrollHeight,
            mouseX, mouseY, partialTick, (g, x, y, width, mx, my) ->
                renderSections(g, safeFont, x, y, width, mx, my));

        int scaledButtonHeight = UIScaleManager.scale(DesignTokens.Size.BUTTON_HEIGHT);
        int buttonY = this.height - UIScaleManager.scale(32);
        int backWidth = UIScaleManager.scale(110);
        int modeWidth = UIScaleManager.scale(84);
        int gap = UIScaleManager.scale(8);
        int backX = (this.width - backWidth) / 2;
        backButton.render(graphics, backX, buttonY, backWidth, scaledButtonHeight, mouseX, mouseY);

        int quickX = backX - gap - modeWidth;
        int advancedX = backX + backWidth + gap;
        quickButton.render(graphics, quickX, buttonY, modeWidth, scaledButtonHeight, mouseX, mouseY);
        advancedButton.render(graphics, advancedX, buttonY, modeWidth, scaledButtonHeight, mouseX, mouseY);
    }

    private int renderSections(GuiGraphics graphics, Font font, int contentX, int startY,
                               int contentWidth, int mouseX, int mouseY) {
        int y = startY;
        int scaledButtonHeight = UIScaleManager.scale(DesignTokens.Size.BUTTON_HEIGHT);
        int headerGap = UIScaleManager.scale(HEADER_GAP);
        int buttonGap = UIScaleManager.scale(BUTTON_GAP);
        int sectionGap = UIScaleManager.scale(SECTION_GAP);

        for (Section section : sections) {
            List<HubItem> visibleItems = section.items().stream()
                .filter(this::isItemVisible)
                .toList();
            if (visibleItems.isEmpty()) {
                continue;
            }
            AxiomRenderer.drawSectionHeader(graphics, font, contentX, y, section.title());
            y += headerGap;

            for (HubItem item : visibleItems) {
                EditorButton button = item.button();
                button.render(graphics, contentX, y, contentWidth, scaledButtonHeight, mouseX, mouseY);
                y += scaledButtonHeight + buttonGap;
            }

            y += sectionGap;
        }

        return Math.max(0, y - startY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollArea.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (quickButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (advancedButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (Section section : sections) {
            for (HubItem item : section.items()) {
                if (!isItemVisible(item)) {
                    continue;
                }
                if (item.button().mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = scrollArea.mouseReleased(mouseX, mouseY, button);
        handled |= backButton.mouseReleased(mouseX, mouseY, button);
        handled |= quickButton.mouseReleased(mouseX, mouseY, button);
        handled |= advancedButton.mouseReleased(mouseX, mouseY, button);
        for (Section section : sections) {
            for (HubItem item : section.items()) {
                if (!isItemVisible(item)) {
                    continue;
                }
                handled |= item.button().mouseReleased(mouseX, mouseY, button);
            }
        }
        if (handled) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollArea.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollArea.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (scrollArea.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void invokeAction(String actionId) {
        ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI);
        ActionRegistry.invoke(actionId, context);
    }

    private boolean isItemVisible(HubItem item) {
        if (showAdvanced) {
            return true;
        }
        return !item.advancedOnly();
    }

    private void setAdvanced(boolean advanced) {
        if (showAdvanced == advanced) {
            return;
        }
        showAdvanced = advanced;
        updateModeButtons();
        scrollArea.scrollToTop();
        persistModeToSettings();
    }

    private void updateModeButtons() {
        quickButton.toggled(!showAdvanced);
        advancedButton.toggled(showAdvanced);
    }

    private void loadModeFromSettings() {
        showAdvanced = SettingsManager.INSTANCE.getSettings().general.hubAdvancedMode;
    }

    private void persistModeToSettings() {
        SettingsManager.INSTANCE.getSettings().general.hubAdvancedMode = showAdvanced;
        SettingsManager.INSTANCE.markDirty();
        SettingsManager.INSTANCE.save();
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
