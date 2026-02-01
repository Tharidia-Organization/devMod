package com.devmod.client.ui.radial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionType;
import com.devmod.actions.client.ActionKeybindRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class RadialActionDetailScreen extends Screen {
    private static final int PANEL_MIN_WIDTH = 320;
    private static final int PANEL_MAX_WIDTH = 440;
    private static final int PANEL_MIN_HEIGHT = 160;
    private static final int PANEL_MAX_HEIGHT = 360;
    private static final int HEADER_HEIGHT = 22;
    private static final int PADDING = 12;
    private static final int BUTTON_HEIGHT = DesignTokens.Size.BUTTON_HEIGHT;
    private static final int BUTTON_GAP = 8;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 6;
    private static final int SCROLL_STEP = 12;

    private final Screen parent;
    private final RadialMenuItem item;
    @Nullable
    private final String actionId;
    @Nullable
    private final com.devmod.actions.RadialAction registryAction;
    @Nullable
    private final ActionKeybindRegistry.KeybindHint keybindHint;
    private final RadialActionSafety.RiskLevel riskLevel;
    private final boolean forceConfirm;
    private List<DetailLine> descriptionLines = List.of();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int scrollOffset;
    private int maxScrollOffset;
    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;
    private boolean lastAdvancedDetails = false;

    @Nullable
    private EditorButton executeButton;
    @Nullable
    private EditorButton keybindsButton;
    @Nullable
    private EditorButton backButton;
    private final List<EditorButton> buttons = new ArrayList<>();

    public RadialActionDetailScreen(Screen parent, RadialMenuItem item) {
        this(parent, item, RadialActionSafety.evaluate(item));
    }

    public RadialActionDetailScreen(Screen parent, RadialMenuItem item, RadialActionSafety.RiskLevel riskLevel) {
        super(java.util.Objects.requireNonNull(Component.translatable("devmod.radial.details.title"), "title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.item = Objects.requireNonNull(item, "item");
        this.actionId = item.getAction().getRegistryId();
        this.registryAction = actionId != null ? ActionRegistry.getAction(actionId) : null;
        this.keybindHint = actionId != null ? ActionKeybindRegistry.getKeybindHint(actionId) : null;
        this.riskLevel = riskLevel != null ? riskLevel : RadialActionSafety.RiskLevel.SAFE;
        this.forceConfirm = registryAction != null
            && (registryAction.requiresConfirm() || this.riskLevel == RadialActionSafety.RiskLevel.DANGER);
    }

    @Override
    protected void init() {
        super.init();
        configureButtons();
        updateLayout(true);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        UIScaleManager.update();
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        AxiomRenderer.drawScreenBackground(safeGraphics, width, height);

        Font font = requireFont();
        updateLayout(false);

        renderPanel(safeGraphics, font);
        renderButtons(safeGraphics, mouseX, mouseY);

        super.render(safeGraphics, mouseX, mouseY, partialTicks);
    }

    private void configureButtons() {
        buttons.clear();

        String executeLabelKey = forceConfirm
            ? "devmod.radial.details.confirm_execute"
            : "devmod.radial.details.execute";
        String executeLabel = I18n.translate(executeLabelKey).getString();
        EditorButton execute = new EditorButton("radial-execute", executeLabel)
            .style(forceConfirm || riskLevel == RadialActionSafety.RiskLevel.DANGER
                ? EditorButton.Style.DANGER
                : EditorButton.Style.PRIMARY)
            .onClick(() -> executeAction(forceConfirm));
        executeButton = execute;
        buttons.add(execute);

        if (keybindHint != null) {
            String keybindLabel = I18n.translate("devmod.radial.details.keybinds").getString();
            EditorButton keybinds = new EditorButton("radial-keybinds", keybindLabel)
                .style(EditorButton.Style.GHOST)
                .onClick(this::openKeybinds);
            keybindsButton = keybinds;
            buttons.add(keybinds);
        } else {
            keybindsButton = null;
        }

        EditorButton back = new EditorButton("radial-back", CommonComponents.GUI_BACK.getString())
            .style(EditorButton.Style.NORMAL)
            .onClick(this::returnToParent);
        backButton = back;
        buttons.add(back);
    }

    private void updateLayout(boolean force) {
        boolean showAdvanced = SettingsManager.INSTANCE.getSettings().debug.developerMode;
        if (!force && width == lastLayoutWidth && height == lastLayoutHeight && showAdvanced == lastAdvancedDetails) {
            return;
        }
        lastLayoutWidth = width;
        lastLayoutHeight = height;
        lastAdvancedDetails = showAdvanced;

        int maxWidth = Math.max(120, width - 20);
        panelWidth = Math.min(PANEL_MAX_WIDTH, maxWidth);
        panelWidth = Math.max(Math.min(PANEL_MIN_WIDTH, maxWidth), panelWidth);
        int textWidth = Math.max(80, panelWidth - (PADDING * 2));

        Font font = requireFont();
        descriptionLines = buildDescriptionLines(font, textWidth, showAdvanced);

        int lineHeight = font.lineHeight + 2;
        int textHeight = descriptionLines.size() * lineHeight;
        int buttonAreaHeight = BUTTON_HEIGHT + PADDING;
        int calculatedHeight = HEADER_HEIGHT + PADDING + textHeight + buttonAreaHeight + PADDING;

        int maxHeight = Math.min(PANEL_MAX_HEIGHT, Math.max(120, height - 40));
        int minHeight = Math.min(PANEL_MIN_HEIGHT, maxHeight);
        panelHeight = Math.max(minHeight, Math.min(calculatedHeight, maxHeight));

        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        contentX = panelX + PADDING;
        contentY = panelY + HEADER_HEIGHT + PADDING;
        contentWidth = panelWidth - (PADDING * 2);
        contentHeight = panelHeight - HEADER_HEIGHT - (PADDING * 2) - BUTTON_HEIGHT - PADDING;
        if (contentHeight < lineHeight) {
            contentHeight = lineHeight;
        }

        maxScrollOffset = Math.max(0, textHeight - contentHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
    }

    private void renderPanel(GuiGraphics graphics, Font font) {
        int panelBg = DesignTokens.Background.PANEL();
        int headerBg = DesignTokens.Background.HEADER();
        int border = DesignTokens.Border.DEFAULT();
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, panelBg);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT, headerBg);
        AxiomRenderer.drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, border);

        int accentColor = riskColor();
        graphics.fill(panelX + 1, panelY + 1, panelX + 3, panelY + panelHeight - 1, accentColor);

        String itemName = Objects.requireNonNullElse(item.getName(), "");
        Component fallbackTitle = Component.literal(Objects.requireNonNull(itemName, "itemName"));
        Component title = registryAction != null
            ? Objects.requireNonNullElse(registryAction.getLabel(), fallbackTitle)
            : fallbackTitle;
        Component safeTitle = Objects.requireNonNull(title, "title");

        int titleX = panelX + PADDING;
        ItemStack icon = item.getIconStack();
        if (icon != null && !icon.isEmpty()) {
            int iconY = panelY + (HEADER_HEIGHT - ICON_SIZE) / 2;
            graphics.renderItem(icon, titleX, iconY);
            titleX += ICON_SIZE + ICON_GAP;
        }
        int titleY = panelY + (HEADER_HEIGHT - font.lineHeight) / 2;
        UIScaleManager.drawScaledString(graphics, font, safeTitle, titleX, titleY, DesignTokens.Text.PRIMARY(), false);

        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        int lineHeight = font.lineHeight + 2;
        int y = contentY - scrollOffset;
        for (DetailLine line : descriptionLines) {
            DetailLine safeLine = Objects.requireNonNull(line, "detail line");
            UIScaleManager.drawScaledString(graphics, font, safeLine.text, contentX, y, safeLine.color, false);
            y += lineHeight;
        }
        graphics.disableScissor();
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        if (buttons.isEmpty()) {
            return;
        }
        int buttonCount = buttons.size();
        int availableWidth = panelWidth - (PADDING * 2);
        int buttonWidth = Math.min(120, (availableWidth - (buttonCount - 1) * BUTTON_GAP) / buttonCount);
        buttonWidth = Math.max(80, buttonWidth);
        int totalWidth = buttonWidth * buttonCount + (buttonCount - 1) * BUTTON_GAP;
        int startX = panelX + (panelWidth - totalWidth) / 2;
        int y = panelY + panelHeight - PADDING - BUTTON_HEIGHT;

        int x = startX;
        for (EditorButton button : buttons) {
            EditorButton safeButton = Objects.requireNonNull(button, "button");
            safeButton.render(graphics, x, y, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
            x += buttonWidth + BUTTON_GAP;
        }
    }

    private List<DetailLine> buildDescriptionLines(Font font, int maxWidth, boolean showAdvanced) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        List<DetailLine> lines = new ArrayList<>();
        String description = item.getDescription();
        if (description != null && !description.isBlank()) {
            description.lines().forEach(rawLine -> {
                String safeLine = Objects.requireNonNull(rawLine, "description line");
                Component lineComponent = Component.literal(safeLine);
                for (FormattedCharSequence seq : safeFont.split(lineComponent, maxWidth)) {
                    lines.add(new DetailLine(seq, DesignTokens.Text.PRIMARY()));
                }
            });
        }

        List<Component> meta = new ArrayList<>();
        if (registryAction != null) {
            ActionCategory category = registryAction.getCategory();
            if (category != null) {
                meta.add(I18n.translate("devmod.radial.details.category", category.getLabel().getString()));
            }
            String commandHint = registryAction.getCommandHint();
            if (commandHint != null && !commandHint.isBlank()) {
                meta.add(I18n.translate("devmod.radial.details.command", "/" + commandHint));
            }
            int permissionLevel = registryAction.getPermissionLevel();
            if (permissionLevel >= 0) {
                meta.add(I18n.translate("devmod.radial.details.permission", permissionLevel));
            }
            if (registryAction.requiresConfirm()) {
                meta.add(I18n.translate("devmod.radial.details.confirm"));
            }
            if (showAdvanced) {
                ActionType actionType = registryAction.getActionType();
                if (actionType != null) {
                    meta.add(I18n.translate("devmod.radial.details.type", formatActionType(actionType)));
                }
                String menuPath = registryAction.getMenuPath();
                if (menuPath != null && !menuPath.isBlank()) {
                    meta.add(I18n.translate("devmod.radial.details.path", menuPath));
                }
            }
        }

        Component riskLine = null;
        if (riskLevel != RadialActionSafety.RiskLevel.SAFE) {
            String riskLabelKey = "devmod.radial.risk." + riskLevel.getId();
            riskLine = I18n.translate("devmod.radial.details.risk", I18n.translate(riskLabelKey).getString());
        }
        if (showAdvanced && actionId != null) {
            meta.add(I18n.translate("devmod.radial.details.id", actionId));
        }

        if (!meta.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(new DetailLine(FormattedCharSequence.EMPTY, DesignTokens.Text.MUTED()));
            }
            for (Component metaLine : meta) {
                for (FormattedCharSequence seq : safeFont.split(metaLine, maxWidth)) {
                    lines.add(new DetailLine(seq, DesignTokens.Text.MUTED()));
                }
            }
        }
        if (riskLine != null) {
            if (!lines.isEmpty() && meta.isEmpty()) {
                lines.add(new DetailLine(FormattedCharSequence.EMPTY, DesignTokens.Text.MUTED()));
            }
            for (FormattedCharSequence seq : safeFont.split(riskLine, maxWidth)) {
                lines.add(new DetailLine(seq, riskColor()));
            }
        }
        return lines;
    }

    private int riskColor() {
        return switch (riskLevel) {
            case SAFE -> DesignTokens.Semantic.SUCCESS;
            case CAUTION -> DesignTokens.Semantic.WARNING;
            case DANGER -> DesignTokens.Semantic.ERROR;
        };
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return mouseX >= contentX && mouseX <= contentX + contentWidth
            && mouseY >= contentY && mouseY <= contentY + contentHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (executeButton != null && executeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (keybindsButton != null && keybindsButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        if (executeButton != null) {
            handled |= executeButton.mouseReleased(mouseX, mouseY, button);
        }
        if (keybindsButton != null) {
            handled |= keybindsButton.mouseReleased(mouseX, mouseY, button);
        }
        if (backButton != null) {
            handled |= backButton.mouseReleased(mouseX, mouseY, button);
        }
        if (handled) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScrollOffset <= 0 || !isMouseOverContent(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset -= (int) (scrollY * SCROLL_STEP);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToParent();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            executeAction(forceConfirm);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void executeAction(boolean confirmed) {
        if (actionId != null && registryAction != null) {
            ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI);
            if (confirmed) {
                context = context.withConfirmed(true);
            }
            ActionRegistry.invoke(actionId, context);
            if (Minecraft.getInstance().screen == this) {
                returnToParent();
            }
            return;
        }

        item.execute();
        if (Minecraft.getInstance().screen == this) {
            returnToParent();
        }
    }

    private void openKeybinds() {
        ActionRegistry.invoke(ActionIds.UI_KEYBINDS_OPEN, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(parent);
    }

    private @Nonnull Font requireFont() {
        return Objects.requireNonNull(font, "font");
    }

    private static String formatActionType(ActionType actionType) {
        return I18n.translate("devmod.radial.action_type." + actionType.name().toLowerCase(Locale.ROOT))
            .getString();
    }

    private static final class DetailLine {
        private final @Nonnull FormattedCharSequence text;
        private final int color;

        private DetailLine(FormattedCharSequence text, int color) {
            this.text = Objects.requireNonNull(text, "text");
            this.color = color;
        }
    }
}
