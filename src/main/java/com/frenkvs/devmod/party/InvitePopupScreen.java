package com.frenkvs.devmod.party;

import com.frenkvs.devmod.endurance.QuestType;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Modal popup screen shown when receiving a party invite.
 *
 * Features:
 * - Shows sender name and quest type
 * - 30-second countdown timer
 * - Accept/Decline buttons
 * - Auto-closes on timeout
 */
public class InvitePopupScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(InvitePopupScreen.class);

    // Layout constants
    private static final int POPUP_WIDTH = 280;
    private static final int POPUP_HEIGHT = 140;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 20;

    // Colors
    private static final int COLOR_BG = UIConstants.Background.PANEL_SOLID();
    private static final int COLOR_HEADER = UIConstants.Background.HEADER();
    private static final int COLOR_TEXT = UIConstants.Text.PRIMARY();
    private static final int COLOR_TEXT_DIM = UIConstants.Text.SECONDARY();
    private static final int COLOR_ACCENT = UIConstants.Accent.CYAN();
    private static final int COLOR_TIMER_OK = UIConstants.Accent.GREEN();
    private static final int COLOR_TIMER_WARN = UIConstants.Accent.GOLD();
    private static final int COLOR_TIMER_URGENT = UIConstants.Accent.RED();
    private static final int COLOR_BORDER = UIConstants.Border.DEFAULT();

    // Invite data
    private final UUID inviteId;
    private final String senderName;
    private final QuestType questType;
    private final long expiresAt;

    // UI state
    private int popupX;
    private int popupY;
    private boolean responded = false;
    private EditorButton acceptButton;
    private EditorButton declineButton;

    /**
     * Create an invite popup from notification payload.
     */
    public InvitePopupScreen(UUID inviteId, String senderName, QuestType questType, long expiresAt) {
        super(Component.translatable("devmod.party.invite_title"));
        this.inviteId = inviteId;
        this.senderName = senderName;
        this.questType = questType;
        this.expiresAt = expiresAt;
    }

    /**
     * Create from a PartyNotificationPayload.
     */
    public static InvitePopupScreen fromNotification(PartyNotificationPayload payload) {
        return new InvitePopupScreen(
            payload.relatedId(),
            payload.playerName(),
            payload.getQuestType(),
            payload.expiresAt()
        );
    }

    @Override
    protected void init() {
        super.init();

        // Center the popup
        popupX = (width - POPUP_WIDTH) / 2;
        popupY = (height - POPUP_HEIGHT) / 2;

        // Accept button
        acceptButton = EditorButton.builder("invite-accept", Component.translatable("devmod.party.accept").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onAccept)
            .build();

        // Decline button
        declineButton = EditorButton.builder("invite-decline", Component.translatable("devmod.party.decline").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onDecline)
            .build();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Darken background
        renderBackground(Objects.requireNonNull(graphics, "graphics"), mouseX, mouseY, partialTick);

        // Popup background
        graphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, COLOR_BG);

        // Border with glow effect
        graphics.renderOutline(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, COLOR_BORDER);
        graphics.renderOutline(popupX - 1, popupY - 1, POPUP_WIDTH + 2, POPUP_HEIGHT + 2,
                UIConstants.setAlpha(COLOR_BORDER, 0x44));

        // Header
        graphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + 25, COLOR_HEADER);
        graphics.drawCenteredString(Objects.requireNonNull(font, "font"), Objects.requireNonNull(title, "title"), popupX + POPUP_WIDTH / 2, popupY + 8, COLOR_ACCENT);

        // Invite message
        String inviteMsg = Objects.requireNonNull(String.format("%s invites you to:", senderName), "inviteMsg");
        graphics.drawCenteredString(Objects.requireNonNull(font, "font"), inviteMsg, popupX + POPUP_WIDTH / 2, popupY + 35, COLOR_TEXT);

        // Quest type with color coding
        String questTypeMsg = Objects.requireNonNull(questType.displayName, "questTypeMsg");
        int questColor = getQuestTypeColor(questType);
        graphics.drawCenteredString(Objects.requireNonNull(font, "font"), questTypeMsg, popupX + POPUP_WIDTH / 2, popupY + 50, questColor);

        // Quest type description
        String description = Objects.requireNonNull(getQuestTypeDescription(questType), "description");
        graphics.drawCenteredString(Objects.requireNonNull(font, "font"), description, popupX + POPUP_WIDTH / 2, popupY + 65, COLOR_TEXT_DIM);

        // Timer
        long remainingMs = expiresAt - System.currentTimeMillis();
        int remainingSeconds = (int) Math.max(0, remainingMs / 1000);

        String timerText = Objects.requireNonNull(String.format("Expires in: %ds", remainingSeconds), "timerText");
        int timerColor = getTimerColor(remainingSeconds);
        graphics.drawCenteredString(Objects.requireNonNull(font, "font"), timerText, popupX + POPUP_WIDTH / 2, popupY + POPUP_HEIGHT - 55, timerColor);

        // Timer bar
        renderTimerBar(graphics, remainingMs);

        renderButtons(graphics, mouseX, mouseY);
    }

    private void renderTimerBar(GuiGraphics graphics, long remainingMs) {
        int barX = popupX + 20;
        int barY = popupY + POPUP_HEIGHT - 45;
        int barWidth = POPUP_WIDTH - 40;
        int barHeight = 4;

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, UIConstants.Background.INPUT());

        // Progress
        float progress = Math.max(0, Math.min(1, remainingMs / (float) PartyInvite.TIMEOUT_MS));
        int progressWidth = (int) (barWidth * progress);

        int progressColor = getTimerColor((int) (remainingMs / 1000));
        graphics.fill(barX, barY, barX + progressWidth, barY + barHeight, progressColor);
    }

    private int getTimerColor(int seconds) {
        if (seconds > 15) return COLOR_TIMER_OK;
        if (seconds > 5) return COLOR_TIMER_WARN;
        return COLOR_TIMER_URGENT;
    }

    private int getQuestTypeColor(QuestType type) {
        return switch (type) {
            case PVE_COOP -> UIConstants.Accent.GREEN();
            case RAID_BOSS -> UIConstants.Accent.ORANGE();
            case EVENT -> UIConstants.Accent.PURPLE();
        };
    }

    private String getQuestTypeDescription(QuestType type) {
        return switch (type) {
            case PVE_COOP -> String.format("%d-%d players, standard difficulty", type.minPlayers, type.maxPlayers);
            case RAID_BOSS -> String.format("%d-%d players, enhanced bosses", type.minPlayers, type.maxPlayers);
            case EVENT -> String.format("%d-%d players, massive boss", type.minPlayers, type.maxPlayers);
        };
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonY = popupY + POPUP_HEIGHT - 35;
        int acceptX = popupX + POPUP_WIDTH / 2 - BUTTON_WIDTH - BUTTON_GAP / 2;
        int declineX = popupX + POPUP_WIDTH / 2 + BUTTON_GAP / 2;

        boolean enabled = !responded;
        acceptButton.enabled(enabled);
        declineButton.enabled(enabled);

        acceptButton.render(graphics, acceptX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
        declineButton.render(graphics, declineX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();

        // Check for timeout
        if (System.currentTimeMillis() >= expiresAt && !responded) {
            LOGGER.info("[InvitePopup] Invite expired");
            onClose();
        }
    }

    private void onAccept() {
        if (responded) return;
        responded = true;

        LOGGER.info("[InvitePopup] Accepting invite: {}", inviteId);

        // Send accept response to server
        PacketDistributor.sendToServer(new InviteResponsePayload(inviteId, true));

        UIConstants.Sound.success();
        onClose();
    }

    private void onDecline() {
        if (responded) return;
        responded = true;

        LOGGER.info("[InvitePopup] Declining invite: {}", inviteId);

        // Send decline response to server
        PacketDistributor.sendToServer(new InviteResponsePayload(inviteId, false));

        UIConstants.Sound.click();
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (acceptButton != null && acceptButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (declineButton != null && declineButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        if (acceptButton != null) handled |= acceptButton.mouseReleased(mouseX, mouseY, button);
        if (declineButton != null) handled |= declineButton.mouseReleased(mouseX, mouseY, button);
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // ESC declines the invite
        return true;
    }

    @Override
    public void onClose() {
        // If closing without responding (ESC or timeout), auto-decline
        if (!responded) {
            LOGGER.info("[InvitePopup] Auto-declining invite on close");
            PacketDistributor.sendToServer(new InviteResponsePayload(inviteId, false));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Show the invite popup to the player.
     * Call this from the client network handler when receiving INVITE_RECEIVED notification.
     */
    public static void showInvite(PartyNotificationPayload notification) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            InvitePopupScreen popup = fromNotification(notification);
            mc.setScreen(popup);
        }
    }
}
