package com.devmod.mailbox.client.screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.mailbox.attachment.MailAttachment;
import com.devmod.mailbox.client.ClientMailboxCache;
import com.devmod.mailbox.client.MailboxUiSkin;
import com.devmod.mailbox.network.payload.MailboxActionPayload;
import com.devmod.mailbox.network.payload.MailboxStatusPayload;
import com.devmod.mailbox.network.payload.MailboxSyncPayload.MailboxMessageData;

/**
 * Main mailbox screen for viewing and managing messages.
 */
@OnlyIn(Dist.CLIENT)
public class MailboxScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxScreen.class);

    // Layout constants
    private static final int PANEL_WIDTH = 600;
    private static final int PANEL_HEIGHT = 400;
    private static final int LIST_WIDTH = 200;
    private static final int DETAIL_WIDTH = PANEL_WIDTH - LIST_WIDTH - 30;

    // Date formatter
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    // UI state
    private int panelX, panelY;
    private int scrollOffset = 0;
    private static final int VISIBLE_MESSAGES = 9;
    private static final int MESSAGE_HEIGHT = 32;
    private static final long STATUS_TTL_MS = 5000;

    @Nullable
    private UUID selectedMessageId = null;
    private int hoveredMessageIndex = -1;
    @Nullable
    private String statusMessage = null;
    private int statusColor = MailboxUiSkin.textMuted();
    private long statusMessageAt = 0;

    // Buttons
    @Nullable private EditorButton closeButton;
    @Nullable private EditorButton deleteButton;
    @Nullable private EditorButton claimButton;
    @Nullable private EditorButton composeButton;
    @Nullable private EditorButton refreshButton;
    @Nullable private EditorButton claimAllButton;
    @Nullable private EditorButton deleteReadButton;

    public MailboxScreen() {
        super(Objects.requireNonNull(Component.translatable("devmod.mailbox.title")));
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

        initButtons();

        // Select first unread message by default
        List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
        for (MailboxMessageData msg : messages) {
            if (!msg.isRead()) {
                selectedMessageId = msg.id();
                break;
            }
        }
        if (selectedMessageId == null && !messages.isEmpty()) {
            selectedMessageId = messages.get(0).id();
        }
    }

    private void initButtons() {
        // Close button
        closeButton = EditorButton.builder("close", "Close")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();

        // Delete button
        deleteButton = EditorButton.builder("delete", "Delete")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onDeleteClicked)
            .build();

        // Claim button
        claimButton = EditorButton.builder("claim", "Claim")
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClaimClicked)
            .build();

        // Compose button
        composeButton = EditorButton.builder("compose", "Compose")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onComposeClicked)
            .build();

        // Refresh button
        refreshButton = EditorButton.builder("refresh", "⟳")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onClick(this::onRefreshClicked)
            .build();

        claimAllButton = EditorButton.builder("claim_all", "Claim All")
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClaimAllClicked)
            .build();

        deleteReadButton = EditorButton.builder("delete_read", "Delete Read")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onDeleteReadClicked)
            .build();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Main panel
        renderMainPanel(graphics);

        // Header
        renderHeader(graphics, mouseX, mouseY);

        // Message list
        hoveredMessageIndex = renderMessageList(graphics, mouseX, mouseY);

        // Message detail
        renderMessageDetail(graphics);

        // Buttons
        renderButtons(graphics, mouseX, mouseY);
        renderStatus(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMainPanel(GuiGraphics graphics) {
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, MailboxUiSkin.panelBg());
        MailboxUiSkin.renderScrollPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        int contentTop = panelY + 45;
        int contentBottom = panelY + PANEL_HEIGHT - 45;
        int listX = panelX + 10;
        int detailX = panelX + LIST_WIDTH + 20;
        graphics.fill(
            listX,
            contentTop,
            listX + LIST_WIDTH,
            contentBottom,
            MailboxUiSkin.listBg());
        graphics.fill(
            detailX,
            contentTop,
            detailX + DETAIL_WIDTH,
            contentBottom,
            MailboxUiSkin.detailBg());

        // Border
        int borderColor = MailboxUiSkin.border();
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, borderColor);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);

        // Divider between list and detail
        int dividerX = panelX + LIST_WIDTH + 10;
        graphics.fill(dividerX, panelY + 50, dividerX + 1, panelY + PANEL_HEIGHT - 45, MailboxUiSkin.divider());
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int headerY = panelY + 10;

        // Title
        String title = "Mailbox";
        MailboxUiSkin.drawText(graphics, getFont(), title, panelX + 15, headerY, MailboxUiSkin.textPrimary(), false);
        int titleWidth = MailboxUiSkin.textWidth(getFont(), title);

        // Unread count badge
        int unread = ClientMailboxCache.getUnreadCount();
        if (unread > 0) {
            String badge = "(" + unread + " unread)";
            MailboxUiSkin.drawText(
                graphics,
                getFont(),
                badge,
                panelX + 15 + titleWidth + 8,
                headerY,
                MailboxUiSkin.unreadAccent(),
                false);
        }

        // Message count
        int total = ClientMailboxCache.getMessageCount();
        int max = ClientMailboxCache.getMaxMessages();
        String countText = total + "/" + max + " messages";
        int countX = panelX + PANEL_WIDTH - MailboxUiSkin.textWidth(getFont(), countText) - 50;
        MailboxUiSkin.drawText(graphics, getFont(), countText, countX, headerY, MailboxUiSkin.textMuted(), false);

        // Refresh button
        if (refreshButton != null) {
            refreshButton.render(graphics, panelX + PANEL_WIDTH - 40, headerY - 2, 24, 18, mouseX, mouseY);
        }

        // Separator line
        graphics.fill(panelX + 10, panelY + 35, panelX + PANEL_WIDTH - 10, panelY + 36, MailboxUiSkin.border());
    }

    private int renderMessageList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
        int listX = panelX + 10;
        int listY = panelY + 45;
        int listHeight = PANEL_HEIGHT - 90;
        boolean feathered = MailboxUiSkin.isFeatheredTheme();
        int textStartX = listX + (feathered ? 26 : 22);

        // Empty state
        if (messages.isEmpty()) {
            String emptyText = "No messages";
            int textX = listX + (LIST_WIDTH - MailboxUiSkin.textWidth(getFont(), emptyText)) / 2;
            int textY = listY + listHeight / 2;
            MailboxUiSkin.drawText(graphics, getFont(), emptyText, textX, textY, MailboxUiSkin.textMuted(), false);
            return -1;
        }

        int hovered = -1;
        int visibleCount = Math.min(VISIBLE_MESSAGES, messages.size() - scrollOffset);

        for (int i = 0; i < visibleCount; i++) {
            int actualIndex = i + scrollOffset;
            if (actualIndex >= messages.size()) break;

            MailboxMessageData msg = messages.get(actualIndex);
            int itemY = listY + i * MESSAGE_HEIGHT;

            // Check hover
            boolean isHovered = mouseX >= listX && mouseX < listX + LIST_WIDTH &&
                               mouseY >= itemY && mouseY < itemY + MESSAGE_HEIGHT - 2;
            if (isHovered) {
                hovered = actualIndex;
            }

            // Selection/hover background
            boolean isSelected = msg.id().equals(selectedMessageId);
            if (isSelected) {
                graphics.fill(listX, itemY, listX + LIST_WIDTH, itemY + MESSAGE_HEIGHT - 2, MailboxUiSkin.listSelected());
            } else if (isHovered) {
                graphics.fill(listX, itemY, listX + LIST_WIDTH, itemY + MESSAGE_HEIGHT - 2, MailboxUiSkin.listHover());
            }

            boolean drewScrollIcon = false;
            if (feathered) {
                ItemStack scrollStack = MailboxUiSkin.scrollIcon(!msg.isRead());
                if (scrollStack != null) {
                    int iconX = listX + 4;
                    int iconY = itemY + (MESSAGE_HEIGHT - 16) / 2;
                    graphics.renderItem(scrollStack, iconX, iconY);
                    graphics.renderItemDecorations(getFont(), scrollStack, iconX, iconY);
                    drewScrollIcon = true;
                }
            }

            // Unread indicator (fallback)
            if (!msg.isRead() && !drewScrollIcon) {
                int dotX = listX + 5;
                int dotY = itemY + MESSAGE_HEIGHT / 2 - 3;
                graphics.fill(dotX, dotY, dotX + 6, dotY + 6, MailboxUiSkin.unreadAccent());
            }

            // Message type icon color
            int typeColor = getMessageTypeColor(msg.messageTypeOrdinal());
            int typeBarX = listX + (feathered ? 22 : 14);
            graphics.fill(typeBarX, itemY + 4, typeBarX + 2, itemY + MESSAGE_HEIGHT - 6, typeColor);

            // Sender or subject
            String senderText = msg.senderName() != null ? msg.senderName() : "System";
            int rightPadding = msg.hasAttachment() ? 20 : 8;
            int textMaxWidth = LIST_WIDTH - (textStartX - listX) - rightPadding;
            senderText = truncateToWidth(senderText, textMaxWidth);
            int textColor = msg.isRead() ? MailboxUiSkin.textMuted() : MailboxUiSkin.textPrimary();
            MailboxUiSkin.drawText(graphics, getFont(), senderText, textStartX, itemY + 4, textColor, false);

            // Subject preview
            String subjectPreview = msg.subject();
            subjectPreview = truncateToWidth(subjectPreview, textMaxWidth);
            MailboxUiSkin.drawText(
                graphics,
                getFont(),
                subjectPreview,
                textStartX,
                itemY + 16,
                MailboxUiSkin.textSecondary(),
                false);

            // Attachment icon
            if (msg.hasAttachment()) {
                int iconX = listX + LIST_WIDTH - 18;
                if (feathered) {
                    MailboxUiSkin.renderPearlIcon(graphics, iconX, itemY + 8, 10);
                } else {
                    int iconColor = msg.attachmentClaimed() ? DesignTokens.Text.MUTED() : DesignTokens.Accent.GOLD();
                    graphics.drawString(getFont(), "📎", iconX, itemY + 10, iconColor, false);
                }
            }
        }

        // Scrollbar
        if (messages.size() > VISIBLE_MESSAGES) {
            int scrollbarX = listX + LIST_WIDTH - 4;
            int scrollbarH = listHeight - 4;
            int thumbHeight = Math.max(20, scrollbarH * VISIBLE_MESSAGES / messages.size());
            int thumbY = listY + 2 + (scrollbarH - thumbHeight) * scrollOffset / Math.max(1, messages.size() - VISIBLE_MESSAGES);

            graphics.fill(scrollbarX, listY + 2, scrollbarX + 3, listY + scrollbarH, MailboxUiSkin.scrollbarTrack());
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, MailboxUiSkin.scrollbarThumb());
        }

        return hovered;
    }

    private void renderMessageDetail(GuiGraphics graphics) {
        int detailX = panelX + LIST_WIDTH + 20;
        int detailY = panelY + 45;
        int detailHeight = PANEL_HEIGHT - 90;
        int detailPadding = 8;
        int detailTextX = detailX + detailPadding;
        int detailTextWidth = DETAIL_WIDTH - detailPadding * 2;

        @Nullable MailboxMessageData selected = selectedMessageId != null
            ? ClientMailboxCache.getMessage(selectedMessageId)
            : null;

        if (selected == null) {
            String noSelection = "Select a message";
            int textX = detailX + (DETAIL_WIDTH - MailboxUiSkin.textWidth(getFont(), noSelection)) / 2;
            int textY = detailY + detailHeight / 2;
            MailboxUiSkin.drawText(graphics, getFont(), noSelection, textX, textY, MailboxUiSkin.textMuted(), false);
            return;
        }

        // Header info
        int y = detailY;

        // Sender
        String senderLabel = "From: ";
        String senderValue = selected.senderName() != null ? selected.senderName() : "System";
        int senderLabelWidth = MailboxUiSkin.textWidth(getFont(), senderLabel);
        String senderText = truncateToWidth(senderValue, detailTextWidth - senderLabelWidth);
        MailboxUiSkin.drawText(graphics, getFont(), senderLabel, detailTextX, y, MailboxUiSkin.textMuted(), false);
        MailboxUiSkin.drawText(
            graphics,
            getFont(),
            senderText,
            detailTextX + senderLabelWidth,
            y,
            getMessageTypeColor(selected.messageTypeOrdinal()),
            false);
        y += 14;

        // Subject
        String subjectLabel = "Subject: ";
        int subjectLabelWidth = MailboxUiSkin.textWidth(getFont(), subjectLabel);
        String subjectText = selected.subject();
        subjectText = truncateToWidth(subjectText, detailTextWidth - subjectLabelWidth);
        MailboxUiSkin.drawText(graphics, getFont(), subjectLabel, detailTextX, y, MailboxUiSkin.textMuted(), false);
        MailboxUiSkin.drawText(graphics, getFont(), subjectText, detailTextX + subjectLabelWidth, y, MailboxUiSkin.textPrimary(), false);
        y += 14;

        // Date
        String dateStr = DATE_FORMAT.format(Instant.ofEpochMilli(selected.createdAtMillis()));
        String dateLabel = "Date: ";
        int dateLabelWidth = MailboxUiSkin.textWidth(getFont(), dateLabel);
        MailboxUiSkin.drawText(graphics, getFont(), dateLabel, detailTextX, y, MailboxUiSkin.textMuted(), false);
        MailboxUiSkin.drawText(graphics, getFont(), dateStr, detailTextX + dateLabelWidth, y, MailboxUiSkin.textSecondary(), false);
        y += 20;

        // Separator
        graphics.fill(detailTextX, y, detailTextX + detailTextWidth, y + 1, MailboxUiSkin.divider());
        y += 10;

        // Body
        String body = selected.body() != null ? selected.body() : "(No message body)";
        List<String> lines = wrapText(body, detailTextWidth);
        int footerReserve = selected.hasAttachment() ? 60 : 20;
        int bodyMaxY = detailY + detailHeight - footerReserve;
        for (String line : lines) {
            if (y > bodyMaxY) {
                MailboxUiSkin.drawText(graphics, getFont(), "...", detailTextX, y, MailboxUiSkin.textMuted(), false);
                break;
            }
            MailboxUiSkin.drawText(graphics, getFont(), line, detailTextX, y, MailboxUiSkin.textPrimary(), false);
            y += 12;
        }

        // Attachment section
        if (selected.hasAttachment()) {
            y = detailY + detailHeight - 50;
            graphics.fill(detailTextX, y, detailTextX + detailTextWidth, y + 1, MailboxUiSkin.divider());
            y += 8;

            String attachLabel = "Attachment:";
            int attachLabelWidth = MailboxUiSkin.textWidth(getFont(), attachLabel);
            String claimedLabel = selected.attachmentClaimed() ? "(Claimed)" : null;
            int claimedWidth = claimedLabel != null ? MailboxUiSkin.textWidth(getFont(), claimedLabel) : 0;
            int descMaxWidth = detailTextWidth - attachLabelWidth - 8 - (claimedWidth > 0 ? claimedWidth + 6 : 0);
            MailboxUiSkin.drawText(graphics, getFont(), attachLabel, detailTextX, y, MailboxUiSkin.textAccent(), false);

            String desc = "Unknown";
            if (selected.attachmentData() != null) {
                MailAttachment attachment = MailAttachment.fromJson(selected.attachmentData());
                if (attachment != null) {
                    desc = attachment.getDescription();
                }
            }
            if (!desc.isEmpty()) {
                desc = truncateToWidth(desc, descMaxWidth);
                MailboxUiSkin.drawText(
                    graphics,
                    getFont(),
                    desc,
                    detailTextX + attachLabelWidth + 8,
                    y,
                    DesignTokens.Accent.GOLD(),
                    false);
            }

            if (claimedLabel != null) {
                MailboxUiSkin.drawText(
                    graphics,
                    getFont(),
                    claimedLabel,
                    detailTextX + detailTextWidth - claimedWidth,
                    y,
                    MailboxUiSkin.textMuted(),
                    false);
            }
        }
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonY = panelY + PANEL_HEIGHT - 35;

        // Left side - Close
        if (closeButton != null) {
            closeButton.render(graphics, panelX + 15, buttonY, 70, 22, mouseX, mouseY);
        }

        EditorButton claimAll = claimAllButton;
        if (claimAll != null) {
            claimAll.enabled(ClientMailboxCache.hasUnclaimedAttachments());
            claimAll.render(graphics, panelX + 95, buttonY, 70, 22, mouseX, mouseY);
        }

        EditorButton deleteRead = deleteReadButton;
        if (deleteRead != null) {
            deleteRead.enabled(hasDeletableReadMessages());
            deleteRead.render(graphics, panelX + 175, buttonY, 70, 22, mouseX, mouseY);
        }

        // Center - Compose
        if (composeButton != null) {
            composeButton.render(graphics, panelX + PANEL_WIDTH / 2 - 40, buttonY, 80, 22, mouseX, mouseY);
        }

        // Right side - message actions (only if selected)
        if (selectedMessageId != null) {
            MailboxMessageData selected = ClientMailboxCache.getMessage(selectedMessageId);
            if (selected != null) {
                // Claim button (if has unclaimed attachment)
                if (selected.canClaimAttachment() && claimButton != null) {
                    claimButton.render(graphics, panelX + PANEL_WIDTH - 170, buttonY, 70, 22, mouseX, mouseY);
                }

                // Delete button
                if (deleteButton != null) {
                    deleteButton.render(graphics, panelX + PANEL_WIDTH - 90, buttonY, 70, 22, mouseX, mouseY);
                }
            }
        }
    }

    private void renderStatus(GuiGraphics graphics) {
        if (statusMessage == null || isStatusExpired()) {
            statusMessage = null;
            statusMessageAt = 0;
            MailboxStatusPayload statusPayload = ClientMailboxCache.pollStatus();
            if (statusPayload != null) {
                setStatusMessage(statusPayload.message(), mapStatusColor(statusPayload.status()));
            }
        }
        if (statusMessage == null) return;
        long age = System.currentTimeMillis() - statusMessageAt;
        if (age > STATUS_TTL_MS) {
            statusMessage = null;
            return;
        }
        int y = panelY + PANEL_HEIGHT - 55;
        MailboxUiSkin.drawText(graphics, getFont(), statusMessage, panelX + 15, y, statusColor, false);
    }

    // ========== EVENT HANDLERS ==========

    private void onDeleteClicked() {
        if (selectedMessageId == null) return;

        MailboxMessageData selected = ClientMailboxCache.getMessage(selectedMessageId);
        if (selected == null) {
            setStatusMessage("Select a message", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }
        if (selected.canClaimAttachment()) {
            setStatusMessage("Claim attachment before deleting", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }

        LOGGER.info("[MailboxScreen] Deleting message: {}", selectedMessageId);
        PacketDistributor.sendToServer(MailboxActionPayload.delete(selectedMessageId));
        ClientMailboxCache.removeMessage(selectedMessageId);

        // Select next message
        List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
        selectedMessageId = messages.isEmpty() ? null : messages.get(0).id();

        UiSounds.click();
    }

    private void onClaimClicked() {
        if (selectedMessageId == null) return;

        MailboxMessageData selected = ClientMailboxCache.getMessage(selectedMessageId);
        if (selected == null) {
            setStatusMessage("Select a message", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }
        if (!selected.canClaimAttachment()) {
            setStatusMessage("No claimable attachment", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }

        LOGGER.info("[MailboxScreen] Claiming attachment for message: {}", selectedMessageId);
        PacketDistributor.sendToServer(MailboxActionPayload.claim(selectedMessageId));
        ClientMailboxCache.markAttachmentClaimed(selectedMessageId);

        UiSounds.success();
    }

    private void onClaimAllClicked() {
        List<MailboxMessageData> claimable = ClientMailboxCache.getUnclaimedAttachmentMessages();
        if (claimable.isEmpty()) {
            setStatusMessage("No attachments to claim", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }

        LOGGER.info("[MailboxScreen] Claiming all attachments: {}", claimable.size());
        for (MailboxMessageData msg : claimable) {
            PacketDistributor.sendToServer(MailboxActionPayload.claim(msg.id()));
            ClientMailboxCache.markAttachmentClaimed(msg.id());
        }

        setStatusMessage("Claimed " + claimable.size() + " attachment(s)", DesignTokens.Status.SUCCESS());
        ClientMailboxCache.suppressClaimStatus(STATUS_TTL_MS);
        UiSounds.success();
    }

    private void onDeleteReadClicked() {
        List<MailboxMessageData> deletable = getDeletableReadMessages();
        if (deletable.isEmpty()) {
            setStatusMessage("No read messages to delete", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }

        boolean removedSelection = false;
        LOGGER.info("[MailboxScreen] Deleting read messages: {}", deletable.size());
        for (MailboxMessageData msg : deletable) {
            PacketDistributor.sendToServer(MailboxActionPayload.delete(msg.id()));
            ClientMailboxCache.removeMessage(msg.id());
            if (msg.id().equals(selectedMessageId)) {
                removedSelection = true;
            }
        }

        List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
        if (removedSelection) {
            selectedMessageId = messages.isEmpty() ? null : messages.get(0).id();
        }
        int maxScroll = Math.max(0, messages.size() - VISIBLE_MESSAGES);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        setStatusMessage("Deleted " + deletable.size() + " message(s)", DesignTokens.Status.SUCCESS());
        ClientMailboxCache.suppressDeleteStatus(STATUS_TTL_MS);
        UiSounds.delete();
    }

    private void onComposeClicked() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new MailboxComposeScreen(this));
        UiSounds.click();
    }

    private void onRefreshClicked() {
        LOGGER.info("[MailboxScreen] Refresh clicked");
        PacketDistributor.sendToServer(MailboxActionPayload.refresh());
        setStatusMessage("Refreshing mailbox...", DesignTokens.Status.PENDING());
        UiSounds.click();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Message list click
        if (hoveredMessageIndex >= 0) {
            List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
            if (hoveredMessageIndex < messages.size()) {
                MailboxMessageData clicked = messages.get(hoveredMessageIndex);
                selectedMessageId = clicked.id();

                if (clicked.body() == null ||
                    (clicked.hasAttachment() && clicked.attachmentData() == null)) {
                    PacketDistributor.sendToServer(MailboxActionPayload.refresh());
                }

                // Mark as read
                if (!clicked.isRead()) {
                    PacketDistributor.sendToServer(MailboxActionPayload.read(clicked.id()));
                    ClientMailboxCache.markAsRead(clicked.id());
                }

                UiSounds.click();
                return true;
            }
        }

        // Button clicks
        if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (claimAllButton != null && claimAllButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (deleteReadButton != null && deleteReadButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (deleteButton != null && deleteButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (claimButton != null && claimButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (composeButton != null && composeButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (refreshButton != null && refreshButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (closeButton != null) closeButton.mouseReleased(mouseX, mouseY, button);
        if (claimAllButton != null) claimAllButton.mouseReleased(mouseX, mouseY, button);
        if (deleteReadButton != null) deleteReadButton.mouseReleased(mouseX, mouseY, button);
        if (deleteButton != null) deleteButton.mouseReleased(mouseX, mouseY, button);
        if (claimButton != null) claimButton.mouseReleased(mouseX, mouseY, button);
        if (composeButton != null) composeButton.mouseReleased(mouseX, mouseY, button);
        if (refreshButton != null) refreshButton.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listX = panelX + 10;
        int listY = panelY + 45;
        int listWidth = LIST_WIDTH;
        int listHeight = PANEL_HEIGHT - 90;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {
            List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
            int maxScroll = Math.max(0, messages.size() - VISIBLE_MESSAGES);
            scrollOffset = Mth.clamp(scrollOffset - (int) verticalAmount, 0, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape or M to close
        if (keyCode == 256 /* ESCAPE */ || keyCode == 77 /* M */) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== UTILITIES ==========

    private int getMessageTypeColor(int typeOrdinal) {
        return MailboxUiSkin.messageTypeColor(typeOrdinal);
    }

    private String truncateToWidth(String text, int maxWidth) {
        return MailboxUiSkin.trimToWidth(getFont(), text, maxWidth);
    }

    private List<String> wrapText(String text, int maxWidth) {
        return MailboxUiSkin.wrapText(getFont(), text, maxWidth);
    }

    private void setStatusMessage(String message, int color) {
        statusMessage = message;
        statusColor = color;
        statusMessageAt = System.currentTimeMillis();
    }

    private boolean isStatusExpired() {
        if (statusMessageAt <= 0) return true;
        return System.currentTimeMillis() - statusMessageAt > STATUS_TTL_MS;
    }

    private int mapStatusColor(MailboxStatusPayload.Status status) {
        return switch (status) {
            case SUCCESS -> DesignTokens.Status.SUCCESS();
            case ERROR -> DesignTokens.Status.ERROR();
            case WARNING -> DesignTokens.Status.WARNING();
            case INFO -> DesignTokens.Status.INFO();
        };
    }

    private boolean hasDeletableReadMessages() {
        return ClientMailboxCache.getMessages().stream()
            .anyMatch(msg -> msg.isRead() && !msg.canClaimAttachment());
    }

    private List<MailboxMessageData> getDeletableReadMessages() {
        return ClientMailboxCache.getMessages().stream()
            .filter(msg -> msg.isRead() && !msg.canClaimAttachment())
            .toList();
    }

    /**
     * Open the mailbox screen.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new MailboxScreen());
    }
}
