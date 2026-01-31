package com.devmod.client.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.transport.TransportColor;
import com.devmod.transport.network.TransportPartyStatusPayload;

/*
 * Party teleport status screen.
 * Displays party teleport progress including:
 * - Current phase (countdown, teleporting, waiting, complete)
 * - Party member list with arrival status
 * - Progress visualization
 * - Cancel option during countdown
 *
 * From Bibbia Estetica Regola 4:
 * - Progress bar centered, horizontal
 * - Member list scrollable
 * - Phase color coding
 * - Background transparency: 85% opaque (0xD9)
 */
@OnlyIn(Dist.CLIENT)
public class PartyTeleportScreen extends BaseDevModScreen {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyTeleportScreen.class);

    private static final int BACKGROUND_ALPHA = 0xD9;
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 260;
    private static final int MEMBER_HEIGHT = 24;
    private static final int VISIBLE_MEMBERS = 5;
    private static final int PROGRESS_BAR_HEIGHT = 16;
    private static final int BORDER_THICKNESS = 2;

    private final UUID partyId;
    private final List<PartyMemberEntry> members;
    private final String destinationName;
    private final TransportColor color;

    private int currentPhase = TransportPartyStatusPayload.PHASE_COUNTDOWN;
    private int arrivedCount = 0;
    private int expectedCount;
    private int countdownSeconds = 5;
    private int scrollOffset = 0;
    private boolean autoCloseOnComplete = true;

    // Panel bounds (recalculated each frame for responsiveness)
    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listHeight;

    // Scaled dimensions (updated each frame for responsiveness)
    private int scaledPanelWidth;
    private int scaledPanelHeight;
    private int scaledMemberHeight;
    private int scaledProgressBarHeight;
    private int scaledBorderThickness;

    // Widgets
    @Nullable
    private EditorButtonWidget cancelButton;
    @Nullable
    private EditorButtonWidget closeButton;

    /*
     * Party member entry.
     */
    public record PartyMemberEntry(
        UUID playerId,
        String playerName,
        boolean arrived,
        boolean isLeader
    ) {}

    public PartyTeleportScreen(
            @Nonnull UUID partyId,
            @Nonnull List<PartyMemberEntry> members,
            @Nonnull String destinationName,
            @Nonnull TransportColor color) {
        super(Component.translatable("screen.devmod.party_teleport"), "party_teleport", "transport");

        this.partyId = partyId;
        this.members = new ArrayList<>(members);
        this.destinationName = destinationName;
        this.color = color;
        this.expectedCount = members.size();
    }

    @Override
    protected void initContent() {
        // Calculate scaled layout for init
        UIScaleManager.update();
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int sMemberHeight = UIScaleManager.scale(MEMBER_HEIGHT);

        panelLeft = (width - sPanelWidth) / 2;
        panelTop = (height - sPanelHeight) / 2;
        listTop = panelTop + UIScaleManager.scale(100);
        listHeight = sMemberHeight * VISIBLE_MEMBERS;

        int buttonWidth = UIScaleManager.scale(120);
        int buttonHeight = UIScaleManager.scale(20);
        int buttonY = panelTop + sPanelHeight - UIScaleManager.scale(35);

        // Cancel button (only visible during countdown)
        EditorButton cancel = EditorButton.builder("party-teleport-cancel",
                Component.translatable("screen.devmod.party_teleport.cancel").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::cancelTeleport)
            .build();
        cancelButton = new EditorButtonWidget(cancel, panelLeft + (sPanelWidth - buttonWidth) / 2, buttonY, buttonWidth, buttonHeight);
        addRenderableWidget(cancelButton);

        // Close button (visible after complete/cancelled)
        EditorButton close = EditorButton.builder("party-teleport-close",
                Component.translatable("gui.close").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        var closeBtn = new EditorButtonWidget(close, panelLeft + (sPanelWidth - buttonWidth) / 2, buttonY, buttonWidth, buttonHeight);
        closeBtn.visible = false;
        closeButton = closeBtn;
        addRenderableWidget(closeBtn);

        updateButtonVisibility();
    }

    /*
     * Updates the party teleport status from a payload.
     */
    public void updateStatus(@Nonnull TransportPartyStatusPayload payload) {
        this.currentPhase = payload.phase();
        this.arrivedCount = payload.arrivedCount();
        this.expectedCount = payload.expectedCount();

        // Update member arrival status
        List<UUID> arrivedIds = payload.arrivedMembers();
        for (int i = 0; i < members.size(); i++) {
            PartyMemberEntry member = members.get(i);
            if (arrivedIds.contains(member.playerId()) && !member.arrived()) {
                members.set(i, new PartyMemberEntry(
                    member.playerId(),
                    member.playerName(),
                    true,
                    member.isLeader()
                ));
            }
        }

        updateButtonVisibility();

        // Auto-close on complete
        if (autoCloseOnComplete && (payload.isComplete() || payload.isCancelled())) {
            // Small delay before closing
            Minecraft.getInstance().tell(() -> {
                var mc = this.minecraft;
                if (mc != null && mc.screen == this) {
                    if (currentPhase == TransportPartyStatusPayload.PHASE_COMPLETE) {
                        onClose();
                    }
                }
            });
        }
    }

    /*
     * Updates the countdown display.
     */
    public void updateCountdown(int secondsRemaining) {
        this.countdownSeconds = secondsRemaining;
    }

    /*
     * Updates button visibility based on phase.
     */
    private void updateButtonVisibility() {
        boolean canCancel = currentPhase == TransportPartyStatusPayload.PHASE_COUNTDOWN
            || currentPhase == TransportPartyStatusPayload.PHASE_WAITING;
        boolean showClose = currentPhase == TransportPartyStatusPayload.PHASE_COMPLETE
            || currentPhase == TransportPartyStatusPayload.PHASE_CANCELLED;

        var cancel = this.cancelButton;
        if (cancel != null) {
            cancel.visible = canCancel;
            cancel.getButton().setEnabled(canCancel);
        }
        var close = this.closeButton;
        if (close != null) {
            close.visible = showClose;
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();

        // Update scaled dimensions for responsiveness
        scaledPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        scaledPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        scaledMemberHeight = UIScaleManager.scale(MEMBER_HEIGHT);
        scaledProgressBarHeight = UIScaleManager.scale(PROGRESS_BAR_HEIGHT);
        scaledBorderThickness = UIScaleManager.scale(BORDER_THICKNESS);

        // Recalculate panel bounds
        panelLeft = (width - scaledPanelWidth) / 2;
        panelTop = (height - scaledPanelHeight) / 2;
        listTop = panelTop + UIScaleManager.scale(100);
        listHeight = scaledMemberHeight * VISIBLE_MEMBERS;

        var renderFont = Objects.requireNonNull(this.font);

        // Panel background
        int bgColor = (BACKGROUND_ALPHA << 24) | (DesignTokens.Bg.LEVEL_2 & 0xFFFFFF);
        graphics.fill(panelLeft, panelTop, panelLeft + scaledPanelWidth, panelTop + scaledPanelHeight, bgColor);

        // Panel border (phase-colored)
        int borderColor = (0xFF << 24) | (getPhaseColor() & 0xFFFFFF);
        renderBorder(graphics, panelLeft, panelTop, scaledPanelWidth, scaledPanelHeight, borderColor);

        // Title
        Component title = Objects.requireNonNull(Component.translatable("screen.devmod.party_teleport.title"));
        String titleString = title.getString();
        int titleWidth = UIScaleManager.getScaledStringWidth(renderFont, titleString);
        UIScaleManager.drawScaledString(graphics, renderFont, titleString, panelLeft + (scaledPanelWidth - titleWidth) / 2, panelTop + UIScaleManager.scale(10), DesignTokens.Text.PRIMARY, true);

        // Phase status
        Component phaseText = getPhaseText();
        String phaseString = phaseText.getString();
        int phaseWidth = UIScaleManager.getScaledStringWidth(renderFont, phaseString);
        int phaseColor = getPhaseColor();
        UIScaleManager.drawScaledString(graphics, renderFont, phaseString, panelLeft + (scaledPanelWidth - phaseWidth) / 2, panelTop + UIScaleManager.scale(28), phaseColor, true);

        // Destination
        if (!destinationName.isEmpty()) {
            Component destText = Objects.requireNonNull(
                Component.translatable("screen.devmod.party_teleport.destination", destinationName));
            String destString = destText.getString();
            int destWidth = UIScaleManager.getScaledStringWidth(renderFont, destString);
            UIScaleManager.drawScaledString(graphics, renderFont, destString, panelLeft + (scaledPanelWidth - destWidth) / 2, panelTop + UIScaleManager.scale(44), DesignTokens.Text.SECONDARY);
        }

        // Progress bar
        int sPadding = UIScaleManager.scale(20);
        int progressY = panelTop + UIScaleManager.scale(60);
        renderProgressBar(graphics, panelLeft + sPadding, progressY, scaledPanelWidth - sPadding * 2, scaledProgressBarHeight);

        // Progress text
        String progressText = Objects.requireNonNull(String.format("%d / %d", arrivedCount, expectedCount));
        int progressTextWidth = UIScaleManager.getScaledStringWidth(renderFont, progressText);
        UIScaleManager.drawScaledString(graphics, renderFont, progressText, panelLeft + (scaledPanelWidth - progressTextWidth) / 2, progressY + UIScaleManager.scale(20), DesignTokens.Text.SECONDARY);

        // Member list header
        int sListPadding = UIScaleManager.scale(15);
        String membersHeader = Objects.requireNonNull(
            Component.translatable("screen.devmod.party_teleport.members")).getString();
        UIScaleManager.drawScaledString(graphics, renderFont, membersHeader, panelLeft + sListPadding, listTop - UIScaleManager.scale(12), DesignTokens.Text.SECONDARY);

        // Member list background
        int sListInset = UIScaleManager.scale(10);
        int listBgColor = (BACKGROUND_ALPHA << 24) | (DesignTokens.Bg.LEVEL_1 & 0xFFFFFF);
        graphics.fill(panelLeft + sListInset, listTop, panelLeft + scaledPanelWidth - sListInset, listTop + listHeight, listBgColor);

        // Render visible members
        int sItemInset = UIScaleManager.scale(12);
        int sItemPadding = UIScaleManager.scale(2);
        int visibleCount = Math.min(VISIBLE_MEMBERS, members.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int memberIndex = scrollOffset + i;
            PartyMemberEntry member = members.get(memberIndex);
            int itemY = listTop + i * scaledMemberHeight;
            renderMemberItem(graphics, member, panelLeft + sItemInset, itemY + sItemPadding, scaledPanelWidth - sItemInset * 2, scaledMemberHeight - sItemPadding * 2);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(renderFont, Objects.requireNonNull(Component.literal("^")), panelLeft + scaledPanelWidth / 2, listTop - UIScaleManager.scale(8), DesignTokens.Text.MUTED);
        }
        if (scrollOffset + VISIBLE_MEMBERS < members.size()) {
            graphics.drawCenteredString(renderFont, Objects.requireNonNull(Component.literal("v")), panelLeft + scaledPanelWidth / 2, listTop + listHeight + UIScaleManager.scale(2), DesignTokens.Text.MUTED);
        }
    }

    /*
     * Renders the progress bar.
     */
    private void renderProgressBar(GuiGraphics graphics, int x, int y, int width, int height) {
        var renderFont = Objects.requireNonNull(this.font);
        int sInset = UIScaleManager.scale(2);

        // Background
        int bgColor = (0xFF << 24) | (DesignTokens.Bg.LEVEL_1 & 0xFFFFFF);
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        int borderColor = (0xFF << 24) | (DesignTokens.Stroke.DEFAULT & 0xFFFFFF);
        renderBorder(graphics, x, y, width, height, borderColor, UIScaleManager.scale(1));

        // Fill
        float progress = expectedCount > 0 ? (float) arrivedCount / expectedCount : 0f;
        progress = Math.min(1f, Math.max(0f, progress));
        int fillWidth = (int) ((width - sInset * 2) * progress);

        if (fillWidth > 0) {
            int fillColor = (0xFF << 24) | (getPhaseColor() & 0xFFFFFF);
            graphics.fill(x + sInset, y + sInset, x + sInset + fillWidth, y + height - sInset, fillColor);
        }

        // Countdown text (during countdown phase)
        if (currentPhase == TransportPartyStatusPayload.PHASE_COUNTDOWN && countdownSeconds > 0) {
            String countText = Objects.requireNonNull(String.valueOf(countdownSeconds));
            int countWidth = UIScaleManager.getScaledStringWidth(renderFont, countText);
            int textHeight = UIScaleManager.scale(8);
            UIScaleManager.drawScaledString(graphics, renderFont, countText, x + (width - countWidth) / 2, y + (height - textHeight) / 2, DesignTokens.Text.PRIMARY, true);
        }
    }

    /*
     * Renders a party member item.
     */
    private void renderMemberItem(GuiGraphics graphics, PartyMemberEntry member, int x, int y, int width, int height) {
        var renderFont = Objects.requireNonNull(this.font);
        int sIndicatorWidth = UIScaleManager.scale(4);
        int sTextPadding = UIScaleManager.scale(8);
        int sTextHeight = UIScaleManager.scale(8);

        // Background based on status
        int bgColor;
        if (member.arrived()) {
            bgColor = (0xFF << 24) | 0x1a3d2b; // Green tint
        } else {
            bgColor = (0xFF << 24) | (DesignTokens.Surface.LEVEL_1 & 0xFFFFFF);
        }
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Status indicator
        int indicatorColor = member.arrived() ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED;
        graphics.fill(x, y, x + sIndicatorWidth, y + height, (0xFF << 24) | (indicatorColor & 0xFFFFFF));

        // Player name
        String displayName = member.playerName();
        if (member.isLeader()) {
            displayName = "[L] " + displayName;
        }
        int textColor = member.arrived() ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.PRIMARY;
        UIScaleManager.drawScaledString(graphics, renderFont, displayName, x + sTextPadding, y + (height - sTextHeight) / 2, textColor);

        // Status text
        String statusText = member.arrived() ? "Arrived" : "Waiting...";
        int statusWidth = UIScaleManager.getScaledStringWidth(renderFont, statusText);
        int statusColor = member.arrived() ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED;
        UIScaleManager.drawScaledString(graphics, renderFont, statusText, x + width - statusWidth - sIndicatorWidth, y + (height - sTextHeight) / 2, statusColor);
    }

    /*
     * Gets the current phase display text.
     */
    @Nonnull
    private Component getPhaseText() {
        return Objects.requireNonNull(switch (currentPhase) {
            case TransportPartyStatusPayload.PHASE_COUNTDOWN ->
                Component.translatable("screen.devmod.party_teleport.phase.countdown", countdownSeconds);
            case TransportPartyStatusPayload.PHASE_TELEPORTING ->
                Component.translatable("screen.devmod.party_teleport.phase.teleporting");
            case TransportPartyStatusPayload.PHASE_WAITING ->
                Component.translatable("screen.devmod.party_teleport.phase.waiting");
            case TransportPartyStatusPayload.PHASE_COMPLETE ->
                Component.translatable("screen.devmod.party_teleport.phase.complete");
            case TransportPartyStatusPayload.PHASE_CANCELLED ->
                Component.translatable("screen.devmod.party_teleport.phase.cancelled");
            default -> Component.literal("Unknown");
        });
    }

    /*
     * Gets the color for the current phase.
     */
    private int getPhaseColor() {
        return switch (currentPhase) {
            case TransportPartyStatusPayload.PHASE_COUNTDOWN -> DesignTokens.Palette.ACCENT_AMBER;
            case TransportPartyStatusPayload.PHASE_TELEPORTING -> DesignTokens.Palette.ACCENT_BLUE;
            case TransportPartyStatusPayload.PHASE_WAITING -> DesignTokens.Palette.ACCENT_TEAL;
            case TransportPartyStatusPayload.PHASE_COMPLETE -> DesignTokens.Palette.SUCCESS;
            case TransportPartyStatusPayload.PHASE_CANCELLED -> DesignTokens.Palette.ERROR;
            default -> color.getColorValue();
        };
    }

    /*
     * Renders a border.
     */
    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        renderBorder(graphics, x, y, width, height, color, scaledBorderThickness);
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color, int thickness) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y, x + thickness, y + height, color);
        graphics.fill(x + width - thickness, y, x + width, y + height, color);
    }

    /*
     * Cancels the party teleport.
     */
    private void cancelTeleport() {
        LOGGER.info("[PartyTeleport] Cancel requested for party {}", partyId);

        // Send cancel request to server
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.devmod.transport.network.TransportCancelPartyPayload(partyId)
        );

        currentPhase = TransportPartyStatusPayload.PHASE_CANCELLED;
        updateButtonVisibility();
    }

    @Override
    protected boolean handleMouseScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        int sListInset = UIScaleManager.scale(10);
        if (mouseX >= panelLeft + sListInset && mouseX < panelLeft + scaledPanelWidth - sListInset
            && mouseY >= listTop && mouseY < listTop + listHeight) {

            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (scrollY < 0 && scrollOffset + VISIBLE_MEMBERS < members.size()) {
                scrollOffset++;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /*
     * Opens this screen.
     */
    public static void open(
            @Nonnull UUID partyId,
            @Nonnull List<PartyMemberEntry> members,
            @Nonnull String destination,
            @Nonnull TransportColor color) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "party_teleport",
            () -> new PartyTeleportScreen(partyId, members, destination, color));
    }

    /*
     * Gets the current screen if it's a PartyTeleportScreen.
     */
    @Nullable
    public static PartyTeleportScreen getCurrent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PartyTeleportScreen pts) {
            return pts;
        }
        return null;
    }
}
