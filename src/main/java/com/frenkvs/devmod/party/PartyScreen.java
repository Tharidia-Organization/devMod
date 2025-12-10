package com.frenkvs.devmod.party;

import com.frenkvs.devmod.endurance.QuestType;
import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Party management screen for creating/joining parties and managing multiplayer quests.
 */
public class PartyScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyScreen.class);

    // Layout constants
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 280;
    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_HEIGHT = 25;
    private static final int MEMBER_ROW_HEIGHT = 24;

    // Colors
    private static final int COLOR_BG = 0xE0101010;
    private static final int COLOR_HEADER = 0xE0202020;
    private static final int COLOR_BORDER = 0xFF404040;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_LEADER = 0xFFFFD700;
    private static final int COLOR_READY = 0xFF00FF00;
    private static final int COLOR_NOT_READY = 0xFFFF6666;
    private static final int COLOR_TAB_ACTIVE = 0xFF3366FF;
    private static final int COLOR_TAB_INACTIVE = 0xFF333333;

    // State from ClientPartyCache
    private List<PartySyncPayload.PartyMemberInfo> members = new ArrayList<>();
    private UUID leaderId = null;
    private QuestType questType = QuestType.PVE_COOP;
    private PartyData.PartyState partyState = PartyData.PartyState.FORMING;
    private boolean isInParty = false;
    private boolean isLeader = false;
    private boolean isReady = false;

    // UI Components
    private EditBox inviteBox;
    private Button createPartyButton;
    private Button readyButton;
    private Button startButton;
    private Button leaveButton;
    private Button disbandButton;

    // Selected member for kick
    @Nullable
    private UUID selectedMemberId = null;
    private int hoveredMemberIndex = -1;

    // Panel position
    private int panelX;
    private int panelY;

    public PartyScreen() {
        super(Component.translatable("devmod.party.title"));
    }

    @Override
    protected void init() {
        super.init();

        // Center the panel
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        // Refresh party data from cache
        refreshFromCache();

        // Invite box (only if in party and leader)
        int inviteY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 10;
        inviteBox = new EditBox(font, panelX + 10, inviteY, PANEL_WIDTH - 100, 20,
                Component.translatable("devmod.party.invite_placeholder"));
        inviteBox.setHint(Component.translatable("devmod.party.invite_hint"));
        inviteBox.setMaxLength(16); // Minecraft username max length
        addRenderableWidget(inviteBox);

        // Invite button
        addRenderableWidget(Button.builder(Component.translatable("devmod.party.send_invite"), this::onInviteClicked)
                .bounds(panelX + PANEL_WIDTH - 80, inviteY, 70, 20)
                .build());

        // Bottom buttons
        int buttonY = panelY + PANEL_HEIGHT - 35;
        int buttonWidth = 70;
        int buttonGap = 5;

        // Ready button
        readyButton = Button.builder(getReadyButtonText(), this::onReadyClicked)
                .bounds(panelX + 10, buttonY, buttonWidth, 20)
                .build();
        addRenderableWidget(readyButton);

        // Leave button
        leaveButton = Button.builder(Component.translatable("devmod.party.leave_party"), this::onLeaveClicked)
                .bounds(panelX + 10 + buttonWidth + buttonGap, buttonY, buttonWidth, 20)
                .build();
        addRenderableWidget(leaveButton);

        // Start Quest button (leader only)
        startButton = Button.builder(Component.translatable("devmod.party.start_quest"), this::onStartClicked)
                .bounds(panelX + PANEL_WIDTH - buttonWidth * 2 - buttonGap - 10, buttonY, buttonWidth, 20)
                .build();
        addRenderableWidget(startButton);

        // Disband button (leader only)
        disbandButton = Button.builder(Component.translatable("devmod.party.disband_party"), this::onDisbandClicked)
                .bounds(panelX + PANEL_WIDTH - buttonWidth - 10, buttonY, buttonWidth, 20)
                .build();
        addRenderableWidget(disbandButton);

        // Create Party button (shown when not in a party)
        createPartyButton = Button.builder(Component.translatable("devmod.party.create_party"), this::onCreatePartyClicked)
                .bounds(panelX + PANEL_WIDTH / 2 - 60, panelY + PANEL_HEIGHT / 2 - 10, 120, 20)
                .build();
        addRenderableWidget(createPartyButton);

        updateButtonStates();
    }

    private void refreshFromCache() {
        isInParty = ClientPartyCache.isInParty();

        if (isInParty) {
            members = new ArrayList<>(ClientPartyCache.getMembers());
            leaderId = ClientPartyCache.getLeaderId();
            QuestType cachedType = ClientPartyCache.getQuestType();
            if (cachedType != null) {
                questType = cachedType;
            }
            PartyData.PartyState cachedState = ClientPartyCache.getPartyState();
            if (cachedState != null) {
                partyState = cachedState;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                isLeader = mc.player.getUUID().equals(leaderId);
                isReady = ClientPartyCache.isLocalPlayerReady();
            }
        } else {
            members.clear();
            leaderId = null;
            isLeader = false;
            isReady = false;
        }
    }

    private void updateButtonStates() {
        // Create party button only visible when not in a party
        createPartyButton.visible = !isInParty;
        createPartyButton.active = !isInParty;

        // Other buttons only visible when in a party
        readyButton.visible = isInParty;
        leaveButton.visible = isInParty;
        startButton.visible = isInParty;
        inviteBox.visible = isInParty;

        // Ready button message
        readyButton.setMessage(getReadyButtonText());

        // Start button only for leader when enough players are ready
        startButton.active = isLeader && canStartQuest();

        // Disband button only for leader
        disbandButton.active = isLeader;
        disbandButton.visible = isInParty && isLeader;

        // Invite box only for leader
        inviteBox.setEditable(isLeader);
    }

    private boolean canStartQuest() {
        if (!isInParty || members.size() < questType.minPlayers) {
            return false;
        }
        // Check if all members are ready
        return members.stream().allMatch(PartySyncPayload.PartyMemberInfo::isReady);
    }

    private Component getReadyButtonText() {
        return isReady
            ? Component.translatable("devmod.party.not_ready")
            : Component.translatable("devmod.party.ready");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Darken background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_BG);

        // Border
        graphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, COLOR_BORDER);

        // Header
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT, COLOR_HEADER);
        graphics.drawCenteredString(font, title, panelX + PANEL_WIDTH / 2, panelY + 10, COLOR_TEXT);

        if (isInParty) {
            // Quest type tabs
            renderQuestTypeTabs(graphics, mouseX, mouseY);

            // Members list
            renderMembersList(graphics, mouseX, mouseY);

            // Party info
            renderPartyInfo(graphics);
        } else {
            // Show "No Party" message
            graphics.drawCenteredString(font, Component.translatable("devmod.party.no_party"),
                    panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT / 2 - 30, COLOR_TEXT_DIM);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderQuestTypeTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabY = panelY + HEADER_HEIGHT;
        int tabWidth = PANEL_WIDTH / 3;

        for (int i = 0; i < QuestType.values().length; i++) {
            QuestType type = QuestType.values()[i];
            int tabX = panelX + i * tabWidth;

            boolean isActive = type == questType;
            int tabColor = isActive ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE;

            graphics.fill(tabX, tabY, tabX + tabWidth, tabY + TAB_HEIGHT, tabColor);
            graphics.drawCenteredString(font, type.displayName, tabX + tabWidth / 2, tabY + 8, COLOR_TEXT);
        }
    }

    private void renderMembersList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 40;
        int listHeight = PANEL_HEIGHT - HEADER_HEIGHT - TAB_HEIGHT - 100;

        hoveredMemberIndex = -1;

        for (int i = 0; i < members.size() && i < 8; i++) {
            PartySyncPayload.PartyMemberInfo member = members.get(i);
            int rowY = listY + i * MEMBER_ROW_HEIGHT;

            // Check hover
            if (mouseX >= panelX + 10 && mouseX < panelX + PANEL_WIDTH - 10 &&
                mouseY >= rowY && mouseY < rowY + MEMBER_ROW_HEIGHT) {
                hoveredMemberIndex = i;
                graphics.fill(panelX + 10, rowY, panelX + PANEL_WIDTH - 10, rowY + MEMBER_ROW_HEIGHT, 0x40FFFFFF);
            }

            // Ready indicator
            int indicatorColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            graphics.fill(panelX + 12, rowY + 4, panelX + 18, rowY + MEMBER_ROW_HEIGHT - 6, indicatorColor);

            // Leader crown
            boolean isMemberLeader = member.playerId().equals(leaderId);
            String prefix = isMemberLeader ? "\u2605 " : ""; // Star for leader
            int nameColor = isMemberLeader ? COLOR_LEADER : COLOR_TEXT;

            // Player name
            graphics.drawString(font, prefix + member.playerName(), panelX + 24, rowY + 7, nameColor);

            // Ready text
            String readyText = member.isReady() ? "Ready" : "Not Ready";
            int readyTextColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            graphics.drawString(font, readyText, panelX + PANEL_WIDTH - 70, rowY + 7, readyTextColor);
        }

        // Empty state
        if (members.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No members"),
                    panelX + PANEL_WIDTH / 2, listY + listHeight / 2 - 5, COLOR_TEXT_DIM);
        }
    }

    private void renderPartyInfo(GuiGraphics graphics) {
        int infoY = panelY + PANEL_HEIGHT - 55;

        // Player count
        String playerInfo = String.format("Players: %d/%d (min: %d)",
                members.size(), questType.maxPlayers, questType.minPlayers);
        graphics.drawString(font, playerInfo, panelX + 10, infoY, COLOR_TEXT_DIM);

        // Quest type info
        String typeInfo = String.format("Difficulty: %.1fx", questType.difficultyMultiplier);
        graphics.drawString(font, typeInfo, panelX + PANEL_WIDTH - 100, infoY, COLOR_TEXT_DIM);
    }

    // === Button Handlers ===

    private void onInviteClicked(Button button) {
        String playerName = inviteBox.getValue().trim();
        if (playerName.isEmpty()) {
            return;
        }

        LOGGER.info("[PartyScreen] Sending invite to: {}", playerName);
        PacketDistributor.sendToServer(NamedInvitePayload.create(playerName, questType));
        inviteBox.setValue("");
        UIConstants.Sound.success();
    }

    private void onReadyClicked(Button button) {
        isReady = !isReady;
        ClientPartyCache.setLocalPlayerReady(isReady);

        // Send ready status to server
        PacketDistributor.sendToServer(PartyActionPayload.toggleReady());

        updateButtonStates();
        UIConstants.Sound.toggleOn();
    }

    private void onLeaveClicked(Button button) {
        LOGGER.info("[PartyScreen] Leaving party");
        PacketDistributor.sendToServer(PartyActionPayload.leaveParty());

        onClose();
        UIConstants.Sound.click();
    }

    private void onStartClicked(Button button) {
        if (!canStartQuest()) {
            UIConstants.Sound.error();
            return;
        }

        LOGGER.info("[PartyScreen] Starting quest with {} players", members.size());
        PacketDistributor.sendToServer(PartyActionPayload.startQuest());

        onClose();
        UIConstants.Sound.success();
    }

    private void onDisbandClicked(Button button) {
        LOGGER.info("[PartyScreen] Disbanding party");
        PacketDistributor.sendToServer(PartyActionPayload.disbandParty());

        onClose();
        UIConstants.Sound.warning();
    }

    private void onCreatePartyClicked(Button button) {
        LOGGER.info("[PartyScreen] Creating party with type: {}", questType);
        PacketDistributor.sendToServer(PartyActionPayload.createParty(questType));

        // Optimistic UI update
        isInParty = true;
        isLeader = true;
        updateButtonStates();
        UIConstants.Sound.success();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check quest type tab clicks
        int tabY = panelY + HEADER_HEIGHT;
        int tabWidth = PANEL_WIDTH / 3;

        if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT && isLeader) {
            for (int i = 0; i < QuestType.values().length; i++) {
                int tabX = panelX + i * tabWidth;
                if (mouseX >= tabX && mouseX < tabX + tabWidth) {
                    questType = QuestType.values()[i];
                    ClientPartyCache.setQuestType(questType);

                    // Send quest type change packet
                    PacketDistributor.sendToServer(PartyActionPayload.setQuestType(questType));

                    UIConstants.Sound.click();
                    updateButtonStates();
                    return true;
                }
            }
        }

        // Check member selection for kick
        if (hoveredMemberIndex >= 0 && hoveredMemberIndex < members.size()) {
            PartySyncPayload.PartyMemberInfo member = members.get(hoveredMemberIndex);
            if (!member.playerId().equals(leaderId)) { // Can't select leader
                selectedMemberId = member.playerId();
                UIConstants.Sound.click();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
