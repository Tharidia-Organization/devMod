package com.devmod.mailbox.client.screen;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.ItemPickerOverlay;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.attachment.CurrencyAttachment;
import com.devmod.mailbox.attachment.ItemAttachment;
import com.devmod.mailbox.attachment.MailAttachment;
import com.devmod.mailbox.client.MailboxUiSkin;
import com.devmod.mailbox.network.payload.MailboxSendPayload;

/**
 * Compose screen for sending a mailbox message.
 */
@OnlyIn(Dist.CLIENT)
public class MailboxComposeScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxComposeScreen.class);

    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 380;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_GAP = 36;
    private static final int INPUT_PADDING = 3;
    private static final int HELPER_BUTTON_WIDTH = 80;
    private static final int HELPER_BUTTON_HEIGHT = 18;
    private static final int HELPER_BUTTON_GAP = 10;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final Screen parent;

    @Nullable private FieldLayout recipientField;
    @Nullable private FieldLayout subjectField;
    @Nullable private FieldLayout bodyField;
    @Nullable private FieldLayout attachmentField;
    @Nullable private FieldLayout itemIdField;
    @Nullable private FieldLayout itemCountField;
    @Nullable private FieldLayout currencyTypeField;
    @Nullable private FieldLayout currencyAmountField;

    @Nullable private EditorButton sendButton;
    @Nullable private EditorButton cancelButton;
    @Nullable private EditorButton useItemButton;
    @Nullable private EditorButton useCurrencyButton;
    @Nullable private EditorButton clearAttachmentButton;
    @Nullable private ItemPickerOverlay itemPickerOverlay;

    @Nullable private String errorMessage = null;

    private int panelX;
    private int panelY;

    public MailboxComposeScreen(Screen parent) {
        super(Objects.requireNonNull(Component.literal("Compose Message")));
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    @Nonnull
    private net.minecraft.client.gui.Font getFont() {
        return Objects.requireNonNull(this.font, "Font not initialized");
    }

    @Override
    protected void init() {
        super.init();

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        initFields();
        initButtons();
        initOverlays();
    }

    private void initFields() {
        int fieldWidth = PANEL_WIDTH - 40;
        int baseX = panelX + 20;
        int baseY = panelY + 40;

        int maxSubject = MailboxConfig.INSTANCE.getMaxSubjectLength();
        int maxBody = MailboxConfig.INSTANCE.getMaxBodyLength();

        recipientField = createField(
            "To (name or UUID)",
            baseX,
            baseY,
            fieldWidth,
            FIELD_HEIGHT,
            64,
            "Player name or UUID"
        );

        subjectField = createField(
            "Subject",
            baseX,
            baseY + FIELD_GAP,
            fieldWidth,
            FIELD_HEIGHT,
            maxSubject,
            "Subject"
        );

        bodyField = createField(
            "Body (use \\n for new line)",
            baseX,
            baseY + FIELD_GAP * 2,
            fieldWidth,
            FIELD_HEIGHT,
            maxBody,
            "Message body"
        );

        int attachmentWidth = fieldWidth - 90;
        attachmentField = createField(
            "Attachment JSON (optional)",
            baseX,
            baseY + FIELD_GAP * 3,
            attachmentWidth,
            FIELD_HEIGHT,
            4096,
            "{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":1}"
        );

        int helperButtonWidth = HELPER_BUTTON_WIDTH;
        int helperGap = HELPER_BUTTON_GAP;
        int countWidth = 60;
        int helperFieldWidth = fieldWidth - helperButtonWidth - helperGap;
        int itemIdWidth = helperFieldWidth - countWidth - helperGap;

        itemIdField = createField(
            "Item ID",
            baseX,
            baseY + FIELD_GAP * 4,
            itemIdWidth,
            FIELD_HEIGHT,
            128,
            "minecraft:diamond"
        );

        itemCountField = createField(
            "Count",
            baseX + itemIdWidth + helperGap,
            baseY + FIELD_GAP * 4,
            countWidth,
            FIELD_HEIGHT,
            5,
            "1"
        );

        currencyTypeField = createField(
            "Currency",
            baseX,
            baseY + FIELD_GAP * 5,
            itemIdWidth,
            FIELD_HEIGHT,
            16,
            "tokens"
        );

        currencyAmountField = createField(
            "Amount",
            baseX + itemIdWidth + helperGap,
            baseY + FIELD_GAP * 5,
            countWidth,
            FIELD_HEIGHT,
            6,
            "10"
        );

        FieldLayout rf = recipientField;
        if (rf != null) {
            rf.box.setFocused(true);
        }
    }

    private void initButtons() {
        sendButton = EditorButton.builder("send", "Send")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onSendClicked)
            .build();

        cancelButton = EditorButton.builder("cancel", "Cancel")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();

        useItemButton = EditorButton.builder("use_item", "Use Item")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::onUseItemClicked)
            .build();

        useCurrencyButton = EditorButton.builder("use_currency", "Use Currency")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::onUseCurrencyClicked)
            .build();

        clearAttachmentButton = EditorButton.builder("clear_attach", "Clear")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onClick(this::onClearAttachment)
            .build();
    }

    private void initOverlays() {
        itemPickerOverlay = new ItemPickerOverlay()
            .onItemSelected(this::onItemPicked)
            .onTagSelected(tag -> {
                errorMessage = "Tags are not supported for attachments";
                UiSounds.warning();
            });
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        renderPanel(graphics);
        renderFields(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        renderAttachmentHelperButtons(graphics, mouseX, mouseY);
        renderButtons(graphics, mouseX, mouseY);
        renderError(graphics);

        renderItemPickerOverlay(graphics, mouseX, mouseY);
    }

    private void renderItemPickerOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemPickerOverlay overlay = itemPickerOverlay;
        if (overlay == null || !overlay.isVisible()) {
            return;
        }
        overlay.render(graphics, getFont(), width, height, mouseX, mouseY);
    }

    private void renderPanel(GuiGraphics graphics) {
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, MailboxUiSkin.panelBg());
        MailboxUiSkin.renderScrollPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        int borderColor = MailboxUiSkin.border();
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, borderColor);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);

        MailboxUiSkin.drawText(graphics, getFont(), "Compose Message", panelX + 15, panelY + 12,
            MailboxUiSkin.textPrimary(), false);
        graphics.fill(panelX + 10, panelY + 30, panelX + PANEL_WIDTH - 10, panelY + 31,
            MailboxUiSkin.border());
    }

    private void renderFields(GuiGraphics graphics) {
        renderField(graphics, recipientField);
        renderField(graphics, subjectField);
        renderField(graphics, bodyField);
        renderField(graphics, attachmentField);
        renderField(graphics, itemIdField);
        renderField(graphics, itemCountField);
        renderField(graphics, currencyTypeField);
        renderField(graphics, currencyAmountField);
    }

    private void renderField(GuiGraphics graphics, @Nullable FieldLayout field) {
        if (field == null) return;
        MailboxUiSkin.drawText(graphics, getFont(), field.label, field.labelX, field.labelY,
            MailboxUiSkin.textMuted(), false);
        MailboxUiSkin.drawInputBackground(graphics, field.x, field.y, field.width, field.height,
            field.box.isFocused());
    }

    private void renderAttachmentHelperButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        FieldLayout attachField = attachmentField;
        EditorButton clearBtn = clearAttachmentButton;
        if (attachField != null && clearBtn != null) {
            int x = attachField.x + attachField.width + HELPER_BUTTON_GAP;
            int y = attachField.y + (attachField.height - HELPER_BUTTON_HEIGHT) / 2;
            clearBtn.enabled(true);
            clearBtn.render(graphics, x, y, HELPER_BUTTON_WIDTH, HELPER_BUTTON_HEIGHT, mouseX, mouseY);
        }
        FieldLayout countField = itemCountField;
        EditorButton itemBtn = useItemButton;
        if (countField != null && itemBtn != null) {
            int x = countField.x + countField.width + HELPER_BUTTON_GAP;
            int y = countField.y + (countField.height - HELPER_BUTTON_HEIGHT) / 2;
            itemBtn.enabled(MailboxConfig.INSTANCE.isItemAttachmentsEnabled());
            itemBtn.render(graphics, x, y, HELPER_BUTTON_WIDTH, HELPER_BUTTON_HEIGHT, mouseX, mouseY);
        }
        FieldLayout currencyField = currencyAmountField;
        EditorButton currencyBtn = useCurrencyButton;
        if (currencyField != null && currencyBtn != null) {
            int x = currencyField.x + currencyField.width + HELPER_BUTTON_GAP;
            int y = currencyField.y + (currencyField.height - HELPER_BUTTON_HEIGHT) / 2;
            currencyBtn.enabled(MailboxConfig.INSTANCE.isCurrencyAttachmentsEnabled());
            currencyBtn.render(graphics, x, y, HELPER_BUTTON_WIDTH, HELPER_BUTTON_HEIGHT, mouseX, mouseY);
        }
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonY = panelY + PANEL_HEIGHT - 35;
        if (cancelButton != null) {
            cancelButton.render(graphics, panelX + 20, buttonY, 80, 22, mouseX, mouseY);
        }
        if (sendButton != null) {
            sendButton.render(graphics, panelX + PANEL_WIDTH - 100, buttonY, 80, 22, mouseX, mouseY);
        }
    }

    private void renderError(GuiGraphics graphics) {
        if (errorMessage == null) return;
        int y = panelY + PANEL_HEIGHT - 55;
        MailboxUiSkin.drawText(graphics, getFont(), errorMessage, panelX + 20, y, DesignTokens.Border.ERROR(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        ItemPickerOverlay overlay = itemPickerOverlay;
        if (overlay != null && overlay.isVisible()) {
            return overlay.mouseClicked(mouseX, mouseY, width, height);
        }

        if (clearAttachmentButton != null && clearAttachmentButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (useItemButton != null && useItemButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (useCurrencyButton != null && useCurrencyButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (sendButton != null && sendButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (cancelButton != null && cancelButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (clearAttachmentButton != null) clearAttachmentButton.mouseReleased(mouseX, mouseY, button);
        if (useItemButton != null) useItemButton.mouseReleased(mouseX, mouseY, button);
        if (useCurrencyButton != null) useCurrencyButton.mouseReleased(mouseX, mouseY, button);
        if (sendButton != null) sendButton.mouseReleased(mouseX, mouseY, button);
        if (cancelButton != null) cancelButton.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ItemPickerOverlay overlay = itemPickerOverlay;
        if (overlay != null && overlay.isVisible()) {
            return overlay.mouseScrolled(mouseX, mouseY, verticalAmount, width, height);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ItemPickerOverlay overlay = itemPickerOverlay;
        if (overlay != null && overlay.isVisible()) {
            return overlay.keyPressed(keyCode);
        }
        if (keyCode == 256 /* ESCAPE */) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        ItemPickerOverlay overlay = itemPickerOverlay;
        if (overlay != null && overlay.isVisible()) {
            return overlay.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ItemPickerOverlay overlay = itemPickerOverlay;
        if (overlay != null) {
            overlay.hideImmediate();
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void onUseItemClicked() {
        errorMessage = null;
        if (!MailboxConfig.INSTANCE.isItemAttachmentsEnabled()) {
            errorMessage = "Item attachments are disabled";
            UiSounds.error();
            return;
        }

        FieldLayout attachment = requireField(attachmentField, "Attachment field missing");
        FieldLayout itemId = requireField(itemIdField, "Item ID field missing");
        FieldLayout itemCount = requireField(itemCountField, "Item count field missing");
        if (attachment == null || itemId == null || itemCount == null) {
            return;
        }

        String itemIdInput = itemId.box.getValue().trim();
        if (itemIdInput.isEmpty()) {
            openItemPicker();
            return;
        }

        int count = parsePositiveInt(itemCount.box.getValue(), 1, "Item count");
        if (count <= 0) {
            UiSounds.error();
            return;
        }

        ItemAttachment attachmentData = ItemAttachment.of(itemIdInput, count);
        if (attachmentData == null) {
            errorMessage = "Invalid item ID";
            UiSounds.error();
            return;
        }

        attachment.box.setValue(Objects.requireNonNull(attachmentData.toJson().toString()));
        attachment.box.setFocused(true);
        UiSounds.click();
    }

    private void onUseCurrencyClicked() {
        errorMessage = null;
        if (!MailboxConfig.INSTANCE.isCurrencyAttachmentsEnabled()) {
            errorMessage = "Currency attachments are disabled";
            UiSounds.error();
            return;
        }

        FieldLayout attachment = requireField(attachmentField, "Attachment field missing");
        FieldLayout currencyType = requireField(currencyTypeField, "Currency field missing");
        FieldLayout currencyAmount = requireField(currencyAmountField, "Amount field missing");
        if (attachment == null || currencyType == null || currencyAmount == null) {
            return;
        }

        String typeInput = currencyType.box.getValue().trim();
        String normalized = typeInput.isEmpty()
            ? CurrencyAttachment.TOKENS
            : typeInput.toLowerCase(Locale.ROOT);
        String type = switch (normalized) {
            case "token", "tokens" -> CurrencyAttachment.TOKENS;
            case "coin", "coins" -> CurrencyAttachment.COINS;
            case "gem", "gems" -> CurrencyAttachment.GEMS;
            case "blood_gems", "bloodgems" -> CurrencyAttachment.BLOOD_GEMS;
            case "prestige" -> CurrencyAttachment.PRESTIGE;
            default -> normalized;
        };

        int amount = parsePositiveInt(currencyAmount.box.getValue(), 10, "Amount");
        if (amount <= 0) {
            UiSounds.error();
            return;
        }

        CurrencyAttachment attachmentData = new CurrencyAttachment(type, amount);
        attachment.box.setValue(Objects.requireNonNull(attachmentData.toJson().toString()));
        attachment.box.setFocused(true);
        UiSounds.click();
    }

    private void onClearAttachment() {
        errorMessage = null;
        FieldLayout attachment = requireField(attachmentField, "Attachment field missing");
        if (attachment == null) {
            return;
        }
        attachment.box.setValue("");
        attachment.box.setFocused(true);
        UiSounds.click();
    }

    private void onItemPicked(ItemStack stack) {
        FieldLayout itemId = requireField(itemIdField, "Item ID field missing");
        if (itemId == null) {
            return;
        }
        ResourceLocation itemKey = Objects.requireNonNull(
            BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem())));
        itemId.box.setValue(Objects.requireNonNull(itemKey.toString()));
        onUseItemClicked();
    }

    private void openItemPicker() {
        ItemPickerOverlay overlay = itemPickerOverlay;
        if (overlay == null) {
            return;
        }
        overlay.show();
        UiSounds.click();
    }

    private void onSendClicked() {
        errorMessage = null;

        FieldLayout recipient = requireField(recipientField, "Recipient field missing");
        FieldLayout subject = requireField(subjectField, "Subject field missing");
        FieldLayout body = requireField(bodyField, "Body field missing");
        FieldLayout attachment = requireField(attachmentField, "Attachment field missing");
        if (recipient == null || subject == null || body == null || attachment == null) {
            return;
        }

        String recipientInput = recipient.box.getValue().trim();
        String subjectInput = subject.box.getValue().trim();
        String bodyInput = body.box.getValue().trim();
        String attachmentInput = attachment.box.getValue().trim();

        if (recipientInput.isEmpty()) {
            errorMessage = "Recipient is required";
            return;
        }
        if (subjectInput.isEmpty()) {
            errorMessage = "Subject is required";
            return;
        }

        ResolvedRecipient resolved = resolveRecipient(recipientInput);
        if (resolved == null) {
            errorMessage = "Recipient is invalid";
            return;
        }

        if (!attachmentInput.isEmpty()) {
            List<MailAttachment> parsed = MailAttachment.parseAttachments(attachmentInput);
            if (parsed.isEmpty()) {
                errorMessage = "Invalid attachment JSON";
                return;
            }
        } else {
            attachmentInput = null;
        }

        int maxSubject = MailboxConfig.INSTANCE.getMaxSubjectLength();
        int maxBody = MailboxConfig.INSTANCE.getMaxBodyLength();

        bodyInput = bodyInput.replace("\\n", "\n");

        if (subjectInput.length() > maxSubject) {
            subjectInput = subjectInput.substring(0, maxSubject);
        }
        if (bodyInput.length() > maxBody) {
            bodyInput = bodyInput.substring(0, maxBody);
        }

        LOGGER.info("[MailboxCompose] Sending message to {}", resolved.uuid);
        PacketDistributor.sendToServer(new MailboxSendPayload(
            resolved.uuid,
            resolved.name,
            subjectInput,
            bodyInput.isBlank() ? null : bodyInput,
            attachmentInput
        ));

        UiSounds.success();
        onClose();
    }

    private int parsePositiveInt(String input, int defaultValue, String label) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                errorMessage = label + " must be greater than 0";
                return -1;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errorMessage = label + " must be a number";
            return -1;
        }
    }

    @Nullable
    private FieldLayout requireField(@Nullable FieldLayout field, String error) {
        if (field == null) {
            errorMessage = error;
            return null;
        }
        return field;
    }

    @Nullable
    private ResolvedRecipient resolveRecipient(String input) {
        try {
            return new ResolvedRecipient(UUID.fromString(input), null);
        } catch (IllegalArgumentException ignored) {
            // Fall through to name lookup.
        }

        if (input.length() > 64) {
            return null;
        }

        return new ResolvedRecipient(ZERO_UUID, input);
    }

    private FieldLayout createField(String label, int x, int y, int width, int height,
            int maxLength, String hint) {
        int labelY = y;
        int inputY = y + 12;

        Component emptyTitle = Objects.requireNonNull(Component.literal(""), "EditBox title");
        EditBox box = new EditBox(getFont(), x + INPUT_PADDING, inputY + INPUT_PADDING,
            width - INPUT_PADDING * 2, height - INPUT_PADDING * 2, emptyTitle);
        Component hintComponent = Objects.requireNonNull(
            MailboxUiSkin.styledComponent(Objects.requireNonNull(hint, "hint")), "hint component");
        box.setHint(hintComponent);
        box.setMaxLength(maxLength);
        box.setBordered(false);
        if (MailboxUiSkin.isFeatheredTheme()) {
            box.setTextColor(MailboxUiSkin.textPrimary());
            box.setTextColorUneditable(MailboxUiSkin.textMuted());
        }
        addRenderableWidget(box);

        return new FieldLayout(label, x, labelY, x, inputY, width, height, box);
    }

    private static final class FieldLayout {
        private final String label;
        private final int labelX;
        private final int labelY;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final EditBox box;

        private FieldLayout(String label, int labelX, int labelY, int x, int y,
                int width, int height, EditBox box) {
            this.label = label;
            this.labelX = labelX;
            this.labelY = labelY;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.box = box;
        }
    }

    private record ResolvedRecipient(UUID uuid, @Nullable String name) {}
}
