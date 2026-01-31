package com.devmod.client.party;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.network.ClientTensionCache;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.EnduranceQuestRegistry.MobDifficultyPreset;
import com.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.KitPreset;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.QuestType;
import com.devmod.endurance.WaveObjectiveState;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.party.PartyData;
import com.devmod.party.PartySyncPayload;

public class PartyScreenRenderer {

    // Colors (using DesignTokens for consistency)
    private static final int COLOR_BG_DARK = DesignTokens.Bg.LEVEL_0;
    private static final int COLOR_HEADER_GRADIENT_TOP = DesignTokens.Surface.LEVEL_2;
    private static final int COLOR_HEADER_GRADIENT_BOT = DesignTokens.Surface.LEVEL_0;
    private static final int COLOR_GLOW_BLUE = DesignTokens.Accent.SECONDARY;
    private static final int COLOR_GLOW_CYAN = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_TEXT_WHITE = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_GRAY = DesignTokens.Text.SECONDARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.MUTED;
    private static final int COLOR_READY = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_NOT_READY = DesignTokens.Semantic.ERROR;
    private static final int COLOR_LEADER_GOLD = DesignTokens.Semantic.WARNING;

    // Additional UI colors
    private static final int COLOR_PANEL_BG = DesignTokens.Bg.LEVEL_1;
    private static final int COLOR_PANEL_HEADER = DesignTokens.Surface.LEVEL_1;
    private static final int COLOR_BORDER_SUBTLE = DesignTokens.Stroke.MUTED;
    private static final int COLOR_TAB_ACTIVE = DesignTokens.Party.TAB_ACTIVE;
    private static final int COLOR_TAB_HOVER = DesignTokens.Surface.LEVEL_1;
    private static final int COLOR_TAB_DEFAULT = DesignTokens.Surface.LEVEL_0;
    private static final int COLOR_ROW_HOVER = DesignTokens.Party.ROW_HOVER;
    private static final int COLOR_ROW_DEFAULT = DesignTokens.Party.ROW_DEFAULT;
    private static final int COLOR_HINT_TEXT = DesignTokens.Party.HINT_TEXT;
    private static final int COLOR_BAR_BG = DesignTokens.Surface.LEVEL_0;

    // Stat colors
    private static final int COLOR_STAT_HP = DesignTokens.Party.STAT_HP;
    private static final int COLOR_STAT_DMG = DesignTokens.Party.STAT_DMG;
    private static final int COLOR_STAT_POINTS = DesignTokens.Party.STAT_POINTS;
    private static final int COLOR_STAT_DIFFICULTY = DesignTokens.Party.STAT_DIFFICULTY;

    // Status glow colors
    private static final int COLOR_READY_GLOW = DesignTokens.Party.READY_GLOW;
    private static final int COLOR_NOT_READY_GLOW = DesignTokens.Party.NOT_READY_GLOW;

    private static final int MAX_VISIBLE_MOBS = 6;
    private static final int MAX_PREVIEW_WAVE = 20;

    private final PartyScreen screen;

    public PartyScreenRenderer(PartyScreen screen) {
        this.screen = screen;
    }

    @Nonnull
    private net.minecraft.client.gui.Font getFont() {
        return screen.getScreenFont();
    }

    public void renderMainPanel(GuiGraphics graphics) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        int panelWidth = screen.getPanelWidth();
        int panelHeight = screen.getPanelHeight();
        float glowPulse = screen.getGlowPulse();
        float titleGlow = screen.getTitleGlow();

        // Outer glow effect
        int glowAlpha = (int) (30 + glowPulse * 20);
        int glowColor = (glowAlpha << 24) | (COLOR_GLOW_BLUE & DesignTokens.Mask.RGB);
        int glowOffset4 = UIScaleManager.scale(4);
        int glowOffset2 = UIScaleManager.scale(2);
        graphics.fill(panelX - glowOffset4, panelY - glowOffset4, panelX + panelWidth + glowOffset4, panelY + panelHeight + glowOffset4, glowColor);
        graphics.fill(panelX - glowOffset2, panelY - glowOffset2, panelX + panelWidth + glowOffset2, panelY + panelHeight + glowOffset2, glowColor);

        // Main background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_BG_DARK);

        // Header gradient
        int gradientHeight = UIScaleManager.scale(40);
        for (int i = 0; i < gradientHeight; i++) {
            float t = i / (float) gradientHeight;
            int color = DesignTokens.lerp(COLOR_HEADER_GRADIENT_TOP, COLOR_HEADER_GRADIENT_BOT, t);
            graphics.fill(panelX, panelY + i, panelX + panelWidth, panelY + i + 1, color);
        }

        // Animated border
        int borderAlpha = (int) (180 + glowPulse * 75);
        int borderColor = (borderAlpha << 24) | (COLOR_GLOW_BLUE & DesignTokens.Mask.RGB);
        drawAnimatedBorder(graphics, panelX, panelY, panelWidth, panelHeight, borderColor);

        // Title with glow
        String title = "PARTY MANAGEMENT";
        int titleX = panelX + panelWidth / 2 - getFont().width(title) / 2;
        int titleY = panelY + UIScaleManager.scale(14);

        // Title glow
        int titleGlowAlpha = (int) (titleGlow * 100);
        net.minecraft.client.gui.Font f = getFont();
        UIScaleManager.drawScaledString(graphics, f, title, titleX - 1, titleY, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & DesignTokens.Mask.RGB), false);
        UIScaleManager.drawScaledString(graphics, f, title, titleX + 1, titleY, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & DesignTokens.Mask.RGB), false);
        UIScaleManager.drawScaledString(graphics, f, title, titleX, titleY - 1, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & DesignTokens.Mask.RGB), false);
        UIScaleManager.drawScaledString(graphics, f, title, titleX, titleY + 1, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & DesignTokens.Mask.RGB), false);

        // Main title
        UIScaleManager.drawScaledString(graphics, f, title, titleX, titleY, COLOR_GLOW_CYAN, false);

        // Subtitle
        if (screen.isInParty()) {
            PartyData.PartyState partyState = screen.getPartyState();
            QuestType questType = screen.getQuestType();
            List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
            String subtitle = partyState.name() + " - " + members.size() + "/" + questType.getMaxPlayers() + " Warriors";
            if (partyState == PartyData.PartyState.IN_QUEST) {
                QuestSyncPayload questData = ClientQuestCache.getData();
                if (questData != null && questData.hasActiveQuest()) {
                    String waveLabel = questData.endlessMode()
                        ? "Wave " + questData.currentWave()
                        : "Wave " + questData.currentWave() + "/" + questData.totalWaves();
                    subtitle = subtitle + " - " + waveLabel;
                }
            }
            int subX = panelX + panelWidth / 2 - f.width(subtitle) / 2;
            UIScaleManager.drawScaledString(graphics, f, subtitle, subX, panelY + UIScaleManager.scale(28), COLOR_TEXT_DIM, false);
            if (partyState != PartyData.PartyState.IN_QUEST) {
                String startReason = screen.getStartBlockReason();
                if (startReason != null && !startReason.isBlank()) {
                    String hint = "START LOCKED: " + startReason;
                    int hintX = panelX + panelWidth / 2 - f.width(hint) / 2;
                    UIScaleManager.drawScaledString(graphics, f, hint, hintX, panelY + UIScaleManager.scale(38), COLOR_NOT_READY, false);
                }
                String nextHint = buildLobbyNextAction(questType, members, screen.isLeader());
                if (nextHint != null && !nextHint.isBlank()) {
                    int nextY = panelY + UIScaleManager.scale(startReason != null && !startReason.isBlank() ? 48 : 38);
                    @Nonnull String nextText = java.util.Objects.requireNonNull(fitToWidth(f, nextHint, panelWidth - UIScaleManager.scale(40)));
                    int nextX = panelX + panelWidth / 2 - f.width(nextText) / 2;
                    UIScaleManager.drawScaledString(graphics, f, nextText, nextX, nextY, COLOR_TEXT_DIM, false);
                }
            }
        }
    }

    public void drawAnimatedBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        int borderThick = UIScaleManager.scale(2);
        int borderThin = UIScaleManager.scale(3);
        // Top
        graphics.fill(x, y, x + w, y + borderThick, color);
        // Bottom
        graphics.fill(x, y + h - borderThick, x + w, y + h, color);
        // Left
        graphics.fill(x, y, x + borderThick, y + h, color);
        // Right
        graphics.fill(x + w - borderThick, y, x + w, y + h, color);

        // Corner accents
        int cornerSize = UIScaleManager.scale(8);
        // Top-left
        graphics.fill(x, y, x + cornerSize, y + borderThin, COLOR_GLOW_CYAN);
        graphics.fill(x, y, x + borderThin, y + cornerSize, COLOR_GLOW_CYAN);
        // Top-right
        graphics.fill(x + w - cornerSize, y, x + w, y + borderThin, COLOR_GLOW_CYAN);
        graphics.fill(x + w - borderThin, y, x + w, y + cornerSize, COLOR_GLOW_CYAN);
        // Bottom-left
        graphics.fill(x, y + h - borderThin, x + cornerSize, y + h, COLOR_GLOW_CYAN);
        graphics.fill(x, y + h - cornerSize, x + borderThin, y + h, COLOR_GLOW_CYAN);
        // Bottom-right
        graphics.fill(x + w - cornerSize, y + h - borderThin, x + w, y + h, COLOR_GLOW_CYAN);
        graphics.fill(x + w - borderThin, y + h - cornerSize, x + w, y + h, COLOR_GLOW_CYAN);
    }

    public int renderQuestTypeTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        int panelWidth = screen.getPanelWidth();
        float glowPulse = screen.getGlowPulse();
        QuestType questType = screen.getQuestType();

        int tabY = panelY + UIScaleManager.scale(45);
        int tabMargin = UIScaleManager.scale(40);
        int tabGap = UIScaleManager.scale(4);
        int tabWidth = (panelWidth - tabMargin) / 3;
        int tabHeight = UIScaleManager.scale(28);
        net.minecraft.client.gui.Font f = getFont();

        int hoveredQuestTab = -1;

        for (int i = 0; i < QuestType.values().length; i++) {
            QuestType type = QuestType.values()[i];
            int tabX = panelX + UIScaleManager.scale(20) + i * tabWidth;

            boolean isActive = type == questType;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth - tabGap &&
                    mouseY >= tabY && mouseY < tabY + tabHeight;

            if (isHovered) hoveredQuestTab = i;

            // Tab background
            int bgColor = isActive ? COLOR_TAB_ACTIVE : (isHovered ? COLOR_TAB_HOVER : COLOR_TAB_DEFAULT);
            graphics.fill(tabX, tabY, tabX + tabWidth - tabGap, tabY + tabHeight, bgColor);

            // Active indicator
            if (isActive) {
                int glowIntensity = (int) (150 + glowPulse * 50);
                graphics.fill(tabX, tabY + tabHeight - UIScaleManager.scale(3), tabX + tabWidth - tabGap, tabY + tabHeight,
                    (glowIntensity << 24) | (COLOR_GLOW_BLUE & DesignTokens.Mask.RGB));
                graphics.fill(tabX, tabY + tabHeight - UIScaleManager.scale(2), tabX + tabWidth - tabGap, tabY + tabHeight, COLOR_GLOW_BLUE);
            }

            // Border
            int borderColor = isActive ? COLOR_GLOW_BLUE : (isHovered ? COLOR_GLOW_BLUE : COLOR_BORDER_SUBTLE);
            AxiomRenderer.drawBorder(graphics, tabX, tabY, tabWidth - tabGap, tabHeight, borderColor);

            // Icon + Name
            String icon = getQuestTypeIcon(type);
            String name = type.getDisplayName();
            int textColor = isActive ? COLOR_GLOW_CYAN : (isHovered ? COLOR_TEXT_WHITE : COLOR_TEXT_GRAY);

            UIScaleManager.drawScaledCenteredString(graphics, f, icon + " " + name, tabX + (tabWidth - tabGap) / 2, tabY + UIScaleManager.scale(6), textColor);

            // Player range
            String range = type.getMinPlayers() + "-" + type.getMaxPlayers() + " players";
            UIScaleManager.drawScaledCenteredString(graphics, f, range, tabX + (tabWidth - tabGap) / 2, tabY + UIScaleManager.scale(17), COLOR_TEXT_DIM);
        }

        return hoveredQuestTab;
    }

    private String getQuestTypeIcon(QuestType type) {
        return switch (type) {
            case PVE_COOP -> "[CO-OP]";
            case RAID_BOSS -> "[RAID]";
            case EVENT -> "[EVENT]";
        };
    }

    public int renderMembersPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        UUID leaderId = screen.getLeaderId();
        boolean isLeader = screen.isLeader();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
        float[] memberAnimations = screen.getMemberAnimations();
        PartyData.PartyState partyState = screen.getPartyState();
        boolean inQuest = partyState == PartyData.PartyState.IN_QUEST;
        int maxVisible = screen.getMaxVisibleMembers();
        int rawOffset = screen.getMemberListScrollOffset();
        int maxScroll = Math.max(0, members.size() - maxVisible);
        int scrollOffset = Math.min(Math.max(0, rawOffset), maxScroll);

        int panelLeft = panelX + UIScaleManager.scale(15);
        int panelTop = panelY + UIScaleManager.scale(80);
        int panelW = UIScaleManager.scale(200);
        int panelH = UIScaleManager.scale(200);
        int headerH = UIScaleManager.scale(22);
        net.minecraft.client.gui.Font f = getFont();

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COLOR_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, COLOR_BORDER_SUBTLE);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + headerH, COLOR_PANEL_HEADER);
        UIScaleManager.drawScaledString(graphics, f, "PARTY MEMBERS", panelLeft + UIScaleManager.scale(8), panelTop + UIScaleManager.scale(7), COLOR_GLOW_CYAN, false);
        int activeCount = 0;
        int readyCount = 0;
        int spectatorCount = 0;
        int offlineCount = 0;
        for (PartySyncPayload.PartyMemberInfo member : members) {
            if (!member.isOnline()) {
                offlineCount++;
            } else if (member.isSpectator()) {
                spectatorCount++;
            } else {
                activeCount++;
                if (member.isReady()) {
                    readyCount++;
                }
            }
        }
        @Nonnull String counts = java.util.Objects.requireNonNull(fitToWidth(f,
            "Act" + activeCount + " Ready" + readyCount + " Spec" + spectatorCount + " Off" + offlineCount,
            panelW - UIScaleManager.scale(16)));
        int countsWidth = f.width(counts);
        UIScaleManager.drawScaledString(graphics, f, counts, panelLeft + panelW - countsWidth - UIScaleManager.scale(6), panelTop + UIScaleManager.scale(7), COLOR_TEXT_DIM, false);

        int hoveredMemberIndex = -1;
        int memberY = panelTop + UIScaleManager.scale(28);
        int rowHeight = UIScaleManager.scale(32);

        int visibleCount = Math.min(maxVisible, Math.max(0, members.size() - scrollOffset));
        for (int i = 0; i < visibleCount; i++) {
            PartySyncPayload.PartyMemberInfo member = members.get(scrollOffset + i);
            float anim = i < memberAnimations.length ? memberAnimations[i] : 1f;

            int rowY = memberY + i * rowHeight;
            int rowH = rowHeight;

            // Slide-in animation
            int offsetX = (int) ((1f - anim) * UIScaleManager.scale(-50));
            int rowX = panelLeft + UIScaleManager.scale(5) + offsetX;
            int rowW = panelW - UIScaleManager.scale(10);

            boolean isHovered = mouseX >= panelLeft + UIScaleManager.scale(5) && mouseX < panelLeft + panelW - UIScaleManager.scale(5) &&
                    mouseY >= rowY && mouseY < rowY + rowH;
            if (isHovered) hoveredMemberIndex = scrollOffset + i;

            // Row background
            int rowBg = isHovered ? COLOR_ROW_HOVER : COLOR_ROW_DEFAULT;
            graphics.fill(rowX, rowY, rowX + rowW, rowY + rowH, (int)(rowBg * anim));

            boolean isMemberLeader = member.playerId().equals(leaderId);

            String status;
            int statusTextColor;
            int statusColor;
            int statusGlow;
            String tag;
            int tagColor;
            if (!member.isOnline()) {
                status = "Offline";
                statusTextColor = COLOR_TEXT_DIM;
                statusColor = COLOR_TEXT_DIM;
                statusGlow = COLOR_NOT_READY_GLOW;
                tag = "OFF";
                tagColor = COLOR_TEXT_DIM;
            } else if (inQuest) {
                if (member.isSpectator()) {
                    status = "Spectating";
                    statusTextColor = COLOR_TEXT_DIM;
                    statusColor = COLOR_NOT_READY;
                    statusGlow = COLOR_NOT_READY_GLOW;
                    tag = "SPEC";
                    tagColor = COLOR_TEXT_DIM;
                } else if (member.isReady()) {
                    status = "Ready to continue";
                    statusTextColor = COLOR_READY;
                    statusColor = COLOR_READY;
                    statusGlow = COLOR_READY_GLOW;
                    tag = "READY";
                    tagColor = COLOR_READY;
                } else {
                    status = "Fighting";
                    statusTextColor = COLOR_GLOW_CYAN;
                    statusColor = COLOR_GLOW_CYAN;
                    statusGlow = COLOR_GLOW_BLUE;
                    tag = "ACTIVE";
                    tagColor = COLOR_GLOW_CYAN;
                }
            } else {
                status = member.isReady() ? "Ready" : "Not ready";
                statusTextColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
                statusColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
                statusGlow = member.isReady() ? COLOR_READY_GLOW : COLOR_NOT_READY_GLOW;
                tag = member.isReady() ? "READY" : "WAIT";
                tagColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            }

            int statusX = rowX + UIScaleManager.scale(5);

            // Status glow
            graphics.fill(statusX - UIScaleManager.scale(2), rowY + UIScaleManager.scale(5), statusX + UIScaleManager.scale(10), rowY + rowH - UIScaleManager.scale(5), statusGlow);
            graphics.fill(statusX, rowY + UIScaleManager.scale(7), statusX + UIScaleManager.scale(8), rowY + rowH - UIScaleManager.scale(7), statusColor);

            // Leader crown or player icon
            String prefix = isMemberLeader ? "[L] " : "    ";
            int nameColor = isMemberLeader ? COLOR_LEADER_GOLD : COLOR_TEXT_WHITE;

            int tagPadding = UIScaleManager.scale(4);
            int tagWidth = f.width(tag) + tagPadding * 2;
            int tagX = rowX + rowW - tagWidth - UIScaleManager.scale(6);

            // Player name
            String displayName = member.playerName();
            Minecraft mc = Minecraft.getInstance();
            var localPlayer = mc.player;
            if (localPlayer != null && member.playerId().equals(localPlayer.getUUID())) {
                displayName += " (You)";
            }
            int maxTextWidth = Math.max(0, tagX - (rowX + UIScaleManager.scale(18)) - UIScaleManager.scale(6));
            String displayLine = fitToWidth(f, prefix + displayName, maxTextWidth);
            UIScaleManager.drawScaledString(graphics, f, displayLine, rowX + UIScaleManager.scale(18), rowY + UIScaleManager.scale(5), nameColor, false);

            // Status text
            String kitLabel = resolveMemberKitLabel(member);
            String statusDetail = kitLabel != null && !kitLabel.isBlank()
                ? status + " | " + kitLabel
                : status;
            String statusLine = fitToWidth(f, statusDetail, maxTextWidth);
            UIScaleManager.drawScaledString(graphics, f, statusLine, rowX + UIScaleManager.scale(18), rowY + UIScaleManager.scale(15), statusTextColor, false);

            // Status tag
            int tagY = rowY + UIScaleManager.scale(8);
            graphics.fill(tagX, tagY, tagX + tagWidth, tagY + UIScaleManager.scale(12), tagColor);
            UIScaleManager.drawScaledString(graphics, f, tag, tagX + tagPadding, tagY + UIScaleManager.scale(2), COLOR_TEXT_WHITE, false);

            // Kick hint on hover (for leader)
            if (isHovered && isLeader && !member.playerId().equals(leaderId) && !inQuest) {
                UIScaleManager.drawScaledString(graphics, f, "[Right-click: Kick]", rowX + rowW - UIScaleManager.scale(85), rowY + UIScaleManager.scale(10), COLOR_TEXT_DIM, false);
            }
        }

        if (members.size() > maxVisible && visibleCount > 0) {
            String range = String.format("%d-%d/%d",
                scrollOffset + 1,
                scrollOffset + visibleCount,
                members.size());
            UIScaleManager.drawScaledString(graphics, f, range, panelLeft + panelW - UIScaleManager.scale(70), panelTop + panelH - UIScaleManager.scale(12), COLOR_TEXT_DIM, false);
        }

        String composition = buildCompositionLine(members);
        if (composition != null && !composition.isBlank()) {
            int compMaxWidth = members.size() > maxVisible ? panelW - UIScaleManager.scale(80) : panelW - UIScaleManager.scale(16);
            String compLine = fitToWidth(f, composition, compMaxWidth);
            UIScaleManager.drawScaledString(graphics, f, compLine, panelLeft + UIScaleManager.scale(8), panelTop + panelH - UIScaleManager.scale(12), COLOR_TEXT_DIM, false);
        }

        // Empty state
        if (members.isEmpty()) {
            UIScaleManager.drawScaledCenteredString(graphics, f, "No warriors yet...", panelLeft + panelW / 2, panelTop + UIScaleManager.scale(80), COLOR_TEXT_DIM);
            UIScaleManager.drawScaledCenteredString(graphics, f, "Invite players below", panelLeft + panelW / 2, panelTop + UIScaleManager.scale(95), COLOR_TEXT_DIM);
        }

        // Invite section header
        int inviteSectionY = panelTop + panelH + UIScaleManager.scale(8);
        UIScaleManager.drawScaledString(graphics, f, "> INVITE PLAYER", panelLeft + UIScaleManager.scale(5), inviteSectionY, COLOR_TEXT_DIM, false);

        // Input background
        EditBox inviteBox = screen.getInviteBox();
        if (inviteBox != null && inviteBox.visible) {
            AxiomRenderer.drawInputBackground(graphics, inviteBox.getX(), inviteBox.getY(), inviteBox.getWidth(),
                inviteBox.getHeight(), inviteBox.isFocused());
        }

        return hoveredMemberIndex;
    }

    @Nullable
    private String resolveMemberKitLabel(PartySyncPayload.PartyMemberInfo member) {
        if (member == null) {
            return null;
        }
        String label = member.kitLabel();
        if (label != null && !label.isBlank()) {
            return label;
        }
        String kitId = member.kitId();
        if (kitId == null || kitId.isBlank()) {
            return null;
        }
        if ("TEMPORARY".equals(kitId)) {
            return "Temporary Kit";
        }
        if (kitId.length() == 8) {
            return "Custom Kit";
        }
        try {
            return KitPreset.valueOf(kitId.toUpperCase(java.util.Locale.ROOT)).getDisplayName();
        } catch (IllegalArgumentException e) {
            return kitId;
        }
    }

    @Nullable
    private String buildCompositionLine(List<PartySyncPayload.PartyMemberInfo> members) {
        if (members == null || members.isEmpty()) {
            return null;
        }
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (PartySyncPayload.PartyMemberInfo member : members) {
            if (member == null || !member.isOnline()) {
                continue;
            }
            String label = resolveMemberKitLabel(member);
            if (label == null || label.isBlank()) {
                continue;
            }
            counts.merge(label, 1, (a, b) -> a + b);
        }
        if (counts.isEmpty()) {
            return null;
        }
        java.util.List<String> dupes = new java.util.ArrayList<>();
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                dupes.add(entry.getValue() + "x " + entry.getKey());
            }
        }
        if (dupes.isEmpty()) {
            return "Comp: Mixed";
        }
        return "Comp: " + String.join(", ", dupes);
    }

    public int renderMobSelectionPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        float glowPulse = screen.getGlowPulse();
        List<EnduranceQuestRegistry.MobQuestConfig> filteredMobs = screen.getFilteredMobs();
        int selectedMobIndex = screen.getSelectedMobIndex();
        int mobListScrollOffset = screen.getMobListScrollOffset();
        String selectedNamespace = screen.getSelectedNamespace();
        MobTier selectedTierFilter = screen.getSelectedTierFilter();

        int panelLeft = panelX + UIScaleManager.scale(225);
        int panelTop = panelY + UIScaleManager.scale(80);
        int panelW = UIScaleManager.scale(160);
        int panelH = UIScaleManager.scale(230);
        int headerH = UIScaleManager.scale(22);
        net.minecraft.client.gui.Font f = getFont();

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COLOR_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, COLOR_BORDER_SUBTLE);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + headerH, COLOR_PANEL_HEADER);
        UIScaleManager.drawScaledString(graphics, f, "SELECT ENEMY", panelLeft + UIScaleManager.scale(8), panelTop + UIScaleManager.scale(7), COLOR_GLOW_CYAN, false);

        // Search background
        EditBox searchBox = screen.getMobSearchBox();
        if (searchBox != null && searchBox.visible) {
            AxiomRenderer.drawInputBackground(graphics, searchBox.getX(), searchBox.getY(), searchBox.getWidth(),
                searchBox.getHeight(), searchBox.isFocused());
        }

        // Namespace filter buttons (All / MC / Mods)
        int searchY = panelTop + UIScaleManager.scale(28);
        int nsFilterY = searchY + UIScaleManager.scale(25);
        int nsBtnX = panelLeft + UIScaleManager.scale(5);
        int nsBtnH = UIScaleManager.scale(12);

        // "All" button
        boolean allActive = selectedNamespace == null;
        int allW = UIScaleManager.scale(28);
        int allColor = allActive ? COLOR_GLOW_BLUE : COLOR_TAB_DEFAULT;
        boolean allHovered = mouseX >= nsBtnX && mouseX < nsBtnX + allW &&
                            mouseY >= nsFilterY && mouseY < nsFilterY + nsBtnH;
        if (allHovered) allColor = DesignTokens.lighten(allColor, 0.2f);
        graphics.fill(nsBtnX, nsFilterY, nsBtnX + allW, nsFilterY + nsBtnH, allColor);
        AxiomRenderer.drawBorder(graphics, nsBtnX, nsFilterY, allW, nsBtnH, allActive ? COLOR_GLOW_BLUE : COLOR_BORDER_SUBTLE);
        UIScaleManager.drawScaledCenteredString(graphics, f, "All", nsBtnX + allW / 2, nsFilterY + UIScaleManager.scale(2), COLOR_TEXT_WHITE);
        nsBtnX += allW + UIScaleManager.scale(2);

        // "MC" button
        boolean mcActive = "minecraft".equals(selectedNamespace);
        int mcW = UIScaleManager.scale(24);
        int mcColor = mcActive ? COLOR_READY : COLOR_TAB_DEFAULT;
        boolean mcHovered = mouseX >= nsBtnX && mouseX < nsBtnX + mcW &&
                           mouseY >= nsFilterY && mouseY < nsFilterY + nsBtnH;
        if (mcHovered) mcColor = DesignTokens.lighten(mcColor, 0.2f);
        graphics.fill(nsBtnX, nsFilterY, nsBtnX + mcW, nsFilterY + nsBtnH, mcColor);
        AxiomRenderer.drawBorder(graphics, nsBtnX, nsFilterY, mcW, nsBtnH, mcActive ? COLOR_READY : COLOR_BORDER_SUBTLE);
        UIScaleManager.drawScaledCenteredString(graphics, f, "MC", nsBtnX + mcW / 2, nsFilterY + UIScaleManager.scale(2), COLOR_TEXT_WHITE);
        nsBtnX += mcW + UIScaleManager.scale(2);

        // Mod count
        long modCount = screen.getAvailableNamespaces().stream().filter(ns -> !"minecraft".equals(ns)).count();
        if (modCount > 0) {
            UIScaleManager.drawScaledString(graphics, f, "+" + modCount, nsBtnX + UIScaleManager.scale(2), nsFilterY + UIScaleManager.scale(2), COLOR_TEXT_DIM, false);
        }

        // Tier filter buttons
        int filterY = nsFilterY + UIScaleManager.scale(16);
        int btnW = UIScaleManager.scale(22);
        int btnH = UIScaleManager.scale(14);
        int btnX = panelLeft + UIScaleManager.scale(5);

        for (MobTier tier : MobTier.values()) {
            boolean active = tier == selectedTierFilter;
            int color = active ? getTierColor(tier) : COLOR_TAB_DEFAULT;
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW &&
                             mouseY >= filterY && mouseY < filterY + btnH;

            if (hovered) color = DesignTokens.lighten(color, 0.2f);

            graphics.fill(btnX, filterY, btnX + btnW, filterY + btnH, color);
            AxiomRenderer.drawBorder(graphics, btnX, filterY, btnW, btnH,
                active ? getTierColor(tier) : COLOR_BORDER_SUBTLE);

            String initial = Objects.requireNonNull(tier.name().substring(0, 1));
            UIScaleManager.drawScaledCenteredString(graphics, f, initial, btnX + btnW / 2, filterY + UIScaleManager.scale(3), COLOR_TEXT_WHITE);

            btnX += btnW + UIScaleManager.scale(2);
        }

        // Mob list
        int listY = filterY + UIScaleManager.scale(18);
        int hoveredMobIndex = -1;
        int rowHeightCompact = UIScaleManager.scale(24);
        int listPadding = UIScaleManager.scale(5);

        for (int i = 0; i < MAX_VISIBLE_MOBS; i++) {
            int mobIndex = mobListScrollOffset + i;
            if (mobIndex >= filteredMobs.size()) break;

            EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(mobIndex);
            int rowY = listY + i * rowHeightCompact;
            int rowH = rowHeightCompact;

            boolean isSelected = mobIndex == selectedMobIndex;
            boolean isHovered = mouseX >= panelLeft + listPadding && mouseX < panelLeft + panelW - listPadding &&
                    mouseY >= rowY && mouseY < rowY + rowH;

            if (isHovered) hoveredMobIndex = mobIndex;

            // Row background
            if (isSelected) {
                int selectGlow = (int) (100 + glowPulse * 50);
                graphics.fill(panelLeft + listPadding, rowY, panelLeft + panelW - listPadding, rowY + rowH,
                    (selectGlow << 24) | (COLOR_GLOW_BLUE & DesignTokens.Mask.RGB));
                graphics.fill(panelLeft + listPadding + 1, rowY + 1, panelLeft + panelW - listPadding - 1, rowY + rowH - 1, COLOR_TAB_ACTIVE);
            } else if (isHovered) {
                graphics.fill(panelLeft + listPadding, rowY, panelLeft + panelW - listPadding, rowY + rowH, COLOR_ROW_DEFAULT);
            }

            // Tier color bar
            int tierColor = getTierColor(config.getTier());
            graphics.fill(panelLeft + listPadding, rowY + UIScaleManager.scale(2), panelLeft + listPadding + UIScaleManager.scale(4), rowY + rowH - UIScaleManager.scale(2), tierColor);

            // Mob name
            String name = Objects.requireNonNull(config.getDisplayName());
            int maxNameWidth = panelW - UIScaleManager.scale(40);
            if (f.width(name) > maxNameWidth) {
                name = Objects.requireNonNull(f.plainSubstrByWidth(name, maxNameWidth - UIScaleManager.scale(5))) + "..";
            }
            int nameColor = isSelected ? COLOR_GLOW_CYAN : COLOR_TEXT_WHITE;
            UIScaleManager.drawScaledString(graphics, f, name, panelLeft + UIScaleManager.scale(14), rowY + UIScaleManager.scale(4), nameColor, false);

            // Difficulty preset
            String preset = getPresetSymbol(config.getDifficultyPreset());
            UIScaleManager.drawScaledString(graphics, f, preset, panelLeft + UIScaleManager.scale(14), rowY + UIScaleManager.scale(14), COLOR_TEXT_DIM, false);
        }

        // Scroll indicators
        if (mobListScrollOffset > 0) {
            UIScaleManager.drawScaledCenteredString(graphics, f, "^", panelLeft + panelW / 2, listY - UIScaleManager.scale(8), COLOR_GLOW_BLUE);
        }
        if (mobListScrollOffset + MAX_VISIBLE_MOBS < filteredMobs.size()) {
            UIScaleManager.drawScaledCenteredString(graphics, f, "v", panelLeft + panelW / 2, listY + MAX_VISIBLE_MOBS * rowHeightCompact + UIScaleManager.scale(2), COLOR_GLOW_BLUE);
        }

        // Count
        UIScaleManager.drawScaledString(graphics, f, filteredMobs.size() + " enemies", panelLeft + UIScaleManager.scale(8), panelTop + panelH - UIScaleManager.scale(14), COLOR_TEXT_DIM, false);

        return hoveredMobIndex;
    }

    public void renderRunStatusPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();

        int panelLeft = panelX + UIScaleManager.scale(225);
        int panelTop = panelY + UIScaleManager.scale(80);
        int panelW = UIScaleManager.scale(160);
        int panelH = UIScaleManager.scale(230);
        int headerH = UIScaleManager.scale(22);
        net.minecraft.client.gui.Font f = getFont();

        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COLOR_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, COLOR_BORDER_SUBTLE);

        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + headerH, COLOR_PANEL_HEADER);
        UIScaleManager.drawScaledString(graphics, f, "RUN STATUS", panelLeft + UIScaleManager.scale(8), panelTop + UIScaleManager.scale(7), COLOR_GLOW_CYAN, false);

        QuestSyncPayload questData = ClientQuestCache.getData();
        boolean hasQuestData = questData != null && questData.hasActiveQuest();
        if (!hasQuestData) {
            UIScaleManager.drawScaledCenteredString(graphics, f, "Awaiting run data", panelLeft + panelW / 2, panelTop + UIScaleManager.scale(110), COLOR_TEXT_DIM);
            return;
        }
        QuestSyncPayload questDataSafe = Objects.requireNonNull(questData);

        boolean atCheckpoint = questDataSafe.getState() == EnduranceQuestState.WAVE_COMPLETE;
        boolean tensionActive = ClientTensionCache.isActive();
        boolean syncStale = ClientQuestCache.isStale(3000);
        int syncSeconds = syncStale ? Math.max(1, (int) Math.ceil(ClientQuestCache.getTimeSinceUpdate() / 1000.0)) : 0;
        QuestType questType = screen.getQuestType();

        int activeCount = 0;
        int readyCount = 0;
        for (PartySyncPayload.PartyMemberInfo member : members) {
            if (!member.isOnline() || member.isSpectator()) {
                continue;
            }
            activeCount++;
            if (member.isReady()) {
                readyCount++;
            }
        }
        int gateTotal = Math.max(1, activeCount);

        String waveLabel = questDataSafe.endlessMode()
            ? questDataSafe.currentWave() + "/INF"
            : questDataSafe.currentWave() + "/" + questDataSafe.totalWaves();

        int textX = panelLeft + UIScaleManager.scale(8);
        int textY = panelTop + UIScaleManager.scale(30);
        int maxWidth = panelW - UIScaleManager.scale(16);
        int lineGap = UIScaleManager.scale(2);

        String waveLine = fitToWidth(f, "Wave " + waveLabel, maxWidth);
        UIScaleManager.drawScaledString(graphics, f, waveLine, textX, textY, COLOR_TEXT_WHITE, false);
        textY += f.lineHeight + lineGap;

        String typeLine = fitToWidth(f, "Type " + questType.getDisplayName(), maxWidth);
        UIScaleManager.drawScaledString(graphics, f, typeLine, textX, textY, COLOR_TEXT_DIM, false);
        textY += f.lineHeight + lineGap;

        String runLine = fitToWidth(f, "Run " + formatDuration(questDataSafe.sessionDurationMs()), maxWidth);
        UIScaleManager.drawScaledString(graphics, f, runLine, textX, textY, COLOR_TEXT_DIM, false);
        textY += f.lineHeight + lineGap;

        if (Screen.hasShiftDown()) {
            String questIdShort = shortId(questDataSafe.questId());
            String questLine = fitToWidth(f, "Quest " + questIdShort, maxWidth);
            UIScaleManager.drawScaledString(graphics, f, questLine, textX, textY, COLOR_TEXT_WHITE, false);
            textY += f.lineHeight + lineGap;
        } else {
            String questHint = fitToWidth(f, "Hold SHIFT for questId", maxWidth);
            UIScaleManager.drawScaledString(graphics, f, questHint, textX, textY, COLOR_TEXT_DIM, false);
            textY += f.lineHeight + lineGap;
        }

        int tensionPercent = Math.round(ClientTensionCache.getTensionPercent() * 100f);
        String tensionLabel = ClientTensionCache.getTensionLabel();
        if (tensionLabel == null) {
            tensionLabel = "";
        }
        String tensionLine = tensionActive
            ? "Tension " + tensionPercent + "% " + tensionLabel
            : "Tension --";
        tensionLine = fitToWidth(f, tensionLine, maxWidth);
        int tensionColor = tensionActive ? ClientTensionCache.getDisplayColor() : COLOR_TEXT_DIM;
        UIScaleManager.drawScaledString(graphics, f, tensionLine, textX, textY, tensionColor, false);
        textY += f.lineHeight + lineGap;

        if (syncStale) {
            String syncLine = fitToWidth(f, "Sync stale " + syncSeconds + "s", maxWidth);
            UIScaleManager.drawScaledString(graphics, f, syncLine, textX, textY, COLOR_NOT_READY, false);
            textY += f.lineHeight + lineGap;
        }

        String detailLeft;
        String detailRight = "";
        int detailColor = COLOR_TEXT_WHITE;
        if (atCheckpoint) {
            detailLeft = "Checkpoint " + readyCount + "/" + gateTotal;
            detailColor = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
            if (activeCount == 0) {
                detailRight = "No actives";
            } else {
                int waiting = Math.max(0, gateTotal - readyCount);
                detailRight = waiting > 0 ? "Waiting " + waiting : "All Ready";
            }
        } else {
            String objective = questDataSafe.objectiveTitle();
            if (objective == null || objective.isEmpty()) {
                objective = questDataSafe.questName();
            }
            int progress = questDataSafe.objectiveProgress();
            int target = questDataSafe.objectiveTarget();
            WaveObjectiveState.Type objectiveType = questDataSafe.getObjectiveType();
            if (objectiveType == WaveObjectiveState.Type.KILL_ALL) {
                progress = questDataSafe.mobsKilledInWave();
                target = questDataSafe.totalMobsInWave();
                detailLeft = target > 0 ? "Kills " + progress + "/" + target : "Kills";
            } else {
                detailLeft = "Objective " + objective;
            }
            if (objectiveType != WaveObjectiveState.Type.KILL_ALL) {
                if (target > 0) {
                    detailRight = progress + "/" + target;
                } else if (questDataSafe.objectiveComplete()) {
                    detailRight = "DONE";
                } else if (questDataSafe.objectiveFailed()) {
                    detailRight = "FAIL";
                }
            }
        }
        int rightWidth = detailRight.isEmpty() ? 0 : f.width(detailRight);
        int leftMax = maxWidth - (rightWidth > 0 ? rightWidth + UIScaleManager.scale(6) : 0);
        detailLeft = fitToWidth(f, detailLeft, leftMax);
        UIScaleManager.drawScaledString(graphics, f, detailLeft, textX, textY, detailColor, false);
        if (!detailRight.isEmpty()) {
            int rightX = panelLeft + panelW - UIScaleManager.scale(8) - rightWidth;
            UIScaleManager.drawScaledString(graphics, f, detailRight, rightX, textY, COLOR_TEXT_DIM, false);
        }

        if (atCheckpoint) {
            String waitingLabel = buildWaitingLabel(members);
            if (!waitingLabel.isEmpty()) {
                textY += f.lineHeight + lineGap;
                waitingLabel = fitToWidth(f, waitingLabel, maxWidth);
                UIScaleManager.drawScaledString(graphics, f, waitingLabel, textX, textY, COLOR_TEXT_DIM, false);
            }
        }

        String actionLabel;
        int actionColor = COLOR_TEXT_DIM;
        if (screen.isLocalSpectator()) {
            actionLabel = "NEXT: READY to REJOIN";
            actionColor = COLOR_GLOW_CYAN;
        } else if (atCheckpoint) {
            actionLabel = screen.isLocalReady() ? "NEXT: Waiting party" : "NEXT: READY to CONTINUE";
            actionColor = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
        } else {
            actionLabel = "NEXT: Fighting";
        }
        actionLabel = fitToWidth(f, actionLabel, maxWidth);
        int actionY = panelTop + panelH - UIScaleManager.scale(38);
        UIScaleManager.drawScaledString(graphics, f, actionLabel, textX, actionY, actionColor, false);

        int barCount = (tensionActive ? 1 : 0) + (atCheckpoint ? 1 : 0);
        if (barCount > 0) {
            int barHeight = UIScaleManager.scale(4);
            int barGap = UIScaleManager.scale(4);
            int barX = panelLeft + UIScaleManager.scale(8);
            int barW = panelW - UIScaleManager.scale(16);
            int barY = panelTop + panelH - UIScaleManager.scale(12) - (barHeight + barGap) * barCount;
            if (tensionActive) {
                graphics.fill(barX, barY, barX + barW, barY + barHeight, COLOR_BAR_BG);
                int fill = Math.round(barW * ClientTensionCache.getTensionPercent());
                if (fill > 0) {
                    graphics.fill(barX, barY, barX + fill, barY + barHeight, ClientTensionCache.getDisplayColor());
                }
                barY += barHeight + barGap;
            }
            if (atCheckpoint) {
                graphics.fill(barX, barY, barX + barW, barY + barHeight, COLOR_BAR_BG);
                float progress = activeCount > 0 ? (float) readyCount / activeCount : 0f;
                int fill = Math.round(barW * progress);
                int color = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
                if (fill > 0) {
                    graphics.fill(barX, barY, barX + fill, barY + barHeight, color);
                }
            }
        }
    }

    public void renderMobPreviewPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        float glowPulse = screen.getGlowPulse();
        EnduranceQuestRegistry.MobQuestConfig selectedConfig = screen.getSelectedMobConfig();
        boolean selectionFilteredOut = screen.isSelectedMobFilteredOut();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
        QuestType questType = screen.getQuestType();
        LivingEntity previewEntity = screen.getPreviewEntity();
        boolean isDraggingPreview = screen.isDraggingPreview();

        int panelLeft = panelX + UIScaleManager.scale(395);
        int panelTop = panelY + UIScaleManager.scale(80);
        int panelW = UIScaleManager.scale(190);
        int panelH = UIScaleManager.scale(230);
        int headerH = UIScaleManager.scale(22);
        int innerPadding = UIScaleManager.scale(5);
        net.minecraft.client.gui.Font f = getFont();

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COLOR_PANEL_BG);

        // Inner preview area
        int previewArea = panelTop + headerH;
        int previewH = UIScaleManager.scale(120);
        graphics.fill(panelLeft + innerPadding, previewArea, panelLeft + panelW - innerPadding, previewArea + previewH, COLOR_BG_DARK);

        // Platform
        int centerX = panelLeft + panelW / 2;
        int platformY = previewArea + UIScaleManager.scale(110);
        int platformRadius = UIScaleManager.scale(40);
        int glowBand = UIScaleManager.scale(5);

        // Platform glow
        for (int r = platformRadius; r > platformRadius - glowBand; r--) {
            int alpha = (int) ((1 - (platformRadius - r) / (float) glowBand) * (50 + glowPulse * 30));
            int glowC = (alpha << 24) | (COLOR_GLOW_BLUE & DesignTokens.Mask.RGB);
            graphics.fill(centerX - r, platformY - UIScaleManager.scale(3), centerX + r, platformY, glowC);
        }

        graphics.fill(centerX - platformRadius + innerPadding, platformY - 1, centerX + platformRadius - innerPadding, platformY, COLOR_GLOW_BLUE);

        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, COLOR_BORDER_SUBTLE);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + headerH, COLOR_PANEL_HEADER);
        UIScaleManager.drawScaledString(graphics, f, "PREVIEW", panelLeft + UIScaleManager.scale(8), panelTop + UIScaleManager.scale(7), COLOR_GLOW_CYAN, false);
        if (selectionFilteredOut) {
            String filteredLabel = "Filtered out";
            UIScaleManager.drawScaledString(graphics, f, filteredLabel, panelLeft + panelW - f.width(filteredLabel) - UIScaleManager.scale(8), panelTop + UIScaleManager.scale(7), COLOR_TEXT_DIM, false);
        }

        // Smooth rotation
        float mobRotationY = screen.getMobRotationY();
        float targetMobRotationY = screen.getTargetMobRotationY();
        float mobRotationX = screen.getMobRotationX();
        screen.setMobRotationY(Mth.lerp(0.15f, mobRotationY, targetMobRotationY));

        if (previewEntity != null && selectedConfig != null) {
            int entityCenterY = previewArea + UIScaleManager.scale(85);

            float mobHeight = previewEntity.getBbHeight();
            float mobWidth = previewEntity.getBbWidth();
            float maxDim = Math.max(mobHeight, mobWidth);
            int scale = (int) Math.min(UIScaleManager.scale(40), UIScaleManager.scale(80) / maxDim);

            Quaternionf rotation = Objects.requireNonNull(new Quaternionf()
                    .rotateY((float) Math.toRadians(screen.getMobRotationY()))
                    .rotateX((float) Math.toRadians(mobRotationX))
                    .rotateZ((float) Math.PI));

            try {
                InventoryScreen.renderEntityInInventory(
                        graphics, centerX, entityCenterY, scale,
                        new Vector3f(0, 0, 0), rotation, null, previewEntity
                );
            } catch (Exception e) {
                UIScaleManager.drawScaledCenteredString(graphics, f, "[Preview Error]", centerX, entityCenterY - UIScaleManager.scale(20), COLOR_TEXT_DIM);
            }

            EnduranceQuestRegistry.MobQuestConfig config = selectedConfig;

            // Stats section
            int statsY = previewArea + UIScaleManager.scale(125);

            UIScaleManager.drawScaledCenteredString(graphics, f, Objects.requireNonNull(config.getDisplayName()), centerX, statsY, getTierColor(config.getTier()));

            String tierBadge = Objects.requireNonNull(getTierBadge(config.getTier()));
            UIScaleManager.drawScaledCenteredString(graphics, f, tierBadge, centerX, statsY + UIScaleManager.scale(12), getTierColor(config.getTier()));

            // Stats grid
            int statY = statsY + UIScaleManager.scale(28);
            int col1 = panelLeft + UIScaleManager.scale(15);
            int col2 = panelLeft + panelW / 2 + UIScaleManager.scale(5);

            UIScaleManager.drawScaledString(graphics, f, "HP", col1, statY, COLOR_STAT_HP, false);
            UIScaleManager.drawScaledString(graphics, f, String.format("%.0f", config.getBaseHealth()), col1 + UIScaleManager.scale(25), statY, COLOR_TEXT_WHITE, false);

            UIScaleManager.drawScaledString(graphics, f, "DMG", col2, statY, COLOR_STAT_DMG, false);
            UIScaleManager.drawScaledString(graphics, f, String.format("%.0f", config.getBaseDamage()), col2 + UIScaleManager.scale(30), statY, COLOR_TEXT_WHITE, false);

            statY += UIScaleManager.scale(14);
            int playerCount = Math.max(1, members.size());
            float scaledHP = config.getScaledHealth(playerCount, questType);
            float scaledDMG = config.getScaledDamage(playerCount);

            UIScaleManager.drawScaledString(graphics, f, "Scaled", col1, statY, COLOR_TEXT_DIM, false);
            UIScaleManager.drawScaledString(graphics, f, String.format("%.0f", scaledHP), col1 + UIScaleManager.scale(40), statY, COLOR_READY, false);
            UIScaleManager.drawScaledString(graphics, f, String.format("%.0f", scaledDMG), col2 + UIScaleManager.scale(30), statY, COLOR_STAT_POINTS, false);

            statY += UIScaleManager.scale(14);
            UIScaleManager.drawScaledString(graphics, f, "Points/Kill: " + config.getPointsPerKill(), col1, statY, COLOR_TEXT_GRAY, false);

        } else {
            String emptyText = selectedConfig == null ? "Select an enemy" : "Preview unavailable";
            UIScaleManager.drawScaledCenteredString(graphics, f, emptyText, centerX, previewArea + UIScaleManager.scale(60), COLOR_TEXT_DIM);
        }

        // Drag hint
        boolean hovering = mouseX >= panelLeft + innerPadding && mouseX < panelLeft + panelW - innerPadding &&
                mouseY >= previewArea && mouseY < previewArea + previewH;
        if (hovering && !isDraggingPreview) {
            UIScaleManager.drawScaledString(graphics, f, "[Drag to rotate]", panelLeft + UIScaleManager.scale(8), previewArea + UIScaleManager.scale(4), COLOR_HINT_TEXT, false);
        }
    }

    public void renderWaveStatsBar(GuiGraphics graphics, int mouseX, int mouseY) {
        renderKitSelectionRow(graphics);
        if (screen.getPartyState() == PartyData.PartyState.IN_QUEST) {
            renderRunFooter(graphics);
            return;
        }
        EnduranceQuestRegistry.MobQuestConfig config = screen.getSelectedMobConfig();
        if (config == null) return;

        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        int panelHeight = screen.getPanelHeight();
        int panelWidth = screen.getPanelWidth();
        int previewWaveNumber = screen.getPreviewWaveNumber();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
        QuestType questType = screen.getQuestType();

        int barY = panelY + panelHeight - UIScaleManager.scale(85);
        int barX = panelX + UIScaleManager.scale(15);
        int barW = panelWidth - UIScaleManager.scale(30);
        net.minecraft.client.gui.Font f = getFont();

        // Separator
        graphics.fill(barX, barY - UIScaleManager.scale(5), barX + barW, barY - UIScaleManager.scale(3), COLOR_BORDER_SUBTLE);

        // Wave slider section
        UIScaleManager.drawScaledString(graphics, f, "> WAVE PREVIEW", barX, barY, COLOR_TEXT_DIM, false);

        // Slider
        int sliderX = barX + UIScaleManager.scale(100);
        int sliderW = UIScaleManager.scale(150);
        int sliderY = barY;
        int sliderH = UIScaleManager.scale(8);

        graphics.fill(sliderX, sliderY + UIScaleManager.scale(2), sliderX + sliderW, sliderY + UIScaleManager.scale(10), COLOR_TAB_HOVER);
        AxiomRenderer.drawBorder(graphics, sliderX, sliderY + UIScaleManager.scale(2), sliderW, sliderH, COLOR_BORDER_SUBTLE);

        float progress = (previewWaveNumber - 1) / (float) (MAX_PREVIEW_WAVE - 1);
        int fillW = (int) (sliderW * progress);
        graphics.fill(sliderX + 1, sliderY + UIScaleManager.scale(3), sliderX + fillW, sliderY + UIScaleManager.scale(9), COLOR_GLOW_BLUE);

        UIScaleManager.drawScaledString(graphics, f, "Wave " + previewWaveNumber, sliderX + sliderW + UIScaleManager.scale(10), sliderY, COLOR_TEXT_WHITE, false);

        // Stats row
        int statsY = barY + UIScaleManager.scale(16);
        int playerCount = Math.max(1, members.size());

        int mobCount = config.getMobCountForWave(previewWaveNumber, playerCount, questType);
        float scaledHP = config.getScaledHealth(playerCount, questType);
        float scaledDMG = config.getScaledDamage(playerCount);
        float waveMultiplier = 1.0f + (previewWaveNumber - 1) * 0.05f;

        int col = barX;
        UIScaleManager.drawScaledString(graphics, f, "Mobs: " + mobCount, col, statsY, COLOR_TEXT_WHITE, false);
        col += UIScaleManager.scale(80);
        UIScaleManager.drawScaledString(graphics, f, String.format("HP: %.0f", scaledHP * waveMultiplier), col, statsY, COLOR_STAT_HP, false);
        col += UIScaleManager.scale(90);
        UIScaleManager.drawScaledString(graphics, f, String.format("DMG: %.0f", scaledDMG * waveMultiplier), col, statsY, COLOR_STAT_DMG, false);
        col += UIScaleManager.scale(90);
        UIScaleManager.drawScaledString(graphics, f, String.format("Points: %d", mobCount * config.getPointsPerKill()), col, statsY, COLOR_STAT_POINTS, false);
        col += UIScaleManager.scale(100);
        UIScaleManager.drawScaledString(graphics, f, String.format("Difficulty: %.1fx", questType.getDifficultyMultiplier() * waveMultiplier), col, statsY, COLOR_STAT_DIFFICULTY, false);
    }

    private void renderKitSelectionRow(GuiGraphics graphics) {
        if (!screen.isInParty()) {
            return;
        }
        int panelX = screen.getPanelX();
        int panelWidth = screen.getPanelWidth();
        int kitY = screen.getKitRowY();
        net.minecraft.client.gui.Font f = getFont();

        String label = screen.getLocalKitLabel();
        String kitLabel = label != null && !label.isBlank() ? label : "-";
        boolean locked = screen.getPartyState() == PartyData.PartyState.IN_QUEST;
        boolean syncing = screen.isKitSyncInFlight();
        String suffix = syncing ? " (SYNC)" : (locked ? " (LOCKED)" : "");
        String line = "KIT: " + kitLabel + suffix;
        int maxWidth = panelWidth - UIScaleManager.scale(150);
        UIScaleManager.drawScaledString(graphics, f, fitToWidth(f, line, maxWidth), panelX + UIScaleManager.scale(15), kitY, COLOR_TEXT_DIM, false);
    }

    private void renderRunFooter(GuiGraphics graphics) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        int panelHeight = screen.getPanelHeight();
        int panelWidth = screen.getPanelWidth();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();

        QuestSyncPayload questData = ClientQuestCache.getData();
        if (questData == null || !questData.hasActiveQuest()) {
            return;
        }

        boolean atCheckpoint = questData.getState() == EnduranceQuestState.WAVE_COMPLETE;
        boolean syncStale = ClientQuestCache.isStale(3000);
        int syncSeconds = syncStale ? Math.max(1, (int) Math.ceil(ClientQuestCache.getTimeSinceUpdate() / 1000.0)) : 0;

        int activeCount = 0;
        int readyCount = 0;
        for (PartySyncPayload.PartyMemberInfo member : members) {
            if (!member.isOnline() || member.isSpectator()) {
                continue;
            }
            activeCount++;
            if (member.isReady()) {
                readyCount++;
            }
        }
        int gateTotal = Math.max(1, activeCount);

        int barY = panelY + panelHeight - UIScaleManager.scale(85);
        int barX = panelX + UIScaleManager.scale(15);
        int barW = panelWidth - UIScaleManager.scale(30);
        net.minecraft.client.gui.Font f = getFont();

        graphics.fill(barX, barY - UIScaleManager.scale(5), barX + barW, barY - UIScaleManager.scale(3), COLOR_BORDER_SUBTLE);

        String statusLabel = atCheckpoint ? "CHECKPOINT READY" : "RUN ACTIVE";
        UIScaleManager.drawScaledString(graphics, f, statusLabel, barX, barY, COLOR_TEXT_DIM, false);

        String waveLabel = questData.endlessMode()
            ? "Wave " + questData.currentWave() + "/INF"
            : "Wave " + questData.currentWave() + "/" + questData.totalWaves();
        int waveWidth = f.width(waveLabel);
        int waveX = barX + barW - waveWidth;
        UIScaleManager.drawScaledString(graphics, f, waveLabel, waveX, barY, COLOR_TEXT_DIM, false);
        if (syncStale) {
            String syncLabel = "SYNC " + syncSeconds + "s";
            int syncWidth = f.width(syncLabel);
            int syncX = Math.max(barX, waveX - syncWidth - UIScaleManager.scale(8));
            UIScaleManager.drawScaledString(graphics, f, syncLabel, syncX, barY, COLOR_NOT_READY, false);
        }

        int infoY = barY + UIScaleManager.scale(16);
        String readyLabel;
        int readyColor;
        if (atCheckpoint) {
            readyLabel = "Checkpoint " + readyCount + "/" + gateTotal;
            readyColor = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
        } else {
            readyLabel = "In wave";
            readyColor = COLOR_TEXT_WHITE;
        }
        UIScaleManager.drawScaledString(graphics, f, readyLabel, barX, infoY, readyColor, false);

        String actionLabel;
        int actionColor = COLOR_TEXT_DIM;
        if (screen.isLocalSpectator()) {
            actionLabel = "NEXT: READY = REJOIN";
            actionColor = COLOR_GLOW_CYAN;
        } else if (atCheckpoint) {
            actionLabel = screen.isLocalReady() ? "NEXT: Waiting party" : "NEXT: READY = CONTINUE";
            actionColor = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
        } else {
            actionLabel = "NEXT: Fighting";
        }
        UIScaleManager.drawScaledString(graphics, f, actionLabel, barX + UIScaleManager.scale(140), infoY, actionColor, false);

        if (atCheckpoint) {
            int progressY = infoY + UIScaleManager.scale(10);
            int progressW = UIScaleManager.scale(140);
            int progressH = UIScaleManager.scale(4);
            graphics.fill(barX, progressY, barX + progressW, progressY + progressH, COLOR_BAR_BG);
            float progress = activeCount > 0 ? (float) readyCount / activeCount : 0f;
            int fill = Math.round(progressW * progress);
            if (fill > 0) {
                int color = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
                graphics.fill(barX, progressY, barX + fill, progressY + progressH, color);
            }
        }
    }

    public void renderNoPartyState(GuiGraphics graphics) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        int panelWidth = screen.getPanelWidth();
        int panelHeight = screen.getPanelHeight();
        float glowPulse = screen.getGlowPulse();

        int centerX = panelX + panelWidth / 2;
        int centerY = panelY + panelHeight / 2;
        net.minecraft.client.gui.Font f = getFont();

        String noParty = "NO ACTIVE PARTY";

        int glowAlpha = (int) (glowPulse * 80);
        int yOffset70 = UIScaleManager.scale(70);
        UIScaleManager.drawScaledString(graphics, f, noParty, centerX - f.width(noParty) / 2 - 1, centerY - yOffset70, (glowAlpha << 24) | (COLOR_GLOW_CYAN & DesignTokens.Mask.RGB), false);
        UIScaleManager.drawScaledString(graphics, f, noParty, centerX - f.width(noParty) / 2 + 1, centerY - yOffset70, (glowAlpha << 24) | (COLOR_GLOW_CYAN & DesignTokens.Mask.RGB), false);

        UIScaleManager.drawScaledCenteredString(graphics, f, noParty, centerX, centerY - yOffset70, COLOR_GLOW_CYAN);

        UIScaleManager.drawScaledCenteredString(graphics, f, "Click CREATE PARTY to start a new group,", centerX, centerY - UIScaleManager.scale(45), COLOR_TEXT_WHITE);
        UIScaleManager.drawScaledCenteredString(graphics, f, "then invite other players to join you.", centerX, centerY - UIScaleManager.scale(30), COLOR_TEXT_GRAY);

        int lineW = UIScaleManager.scale(140);
        int lineCenter = UIScaleManager.scale(40);
        int lineY = centerY - UIScaleManager.scale(15);
        graphics.fill(centerX - lineW, lineY, centerX + lineW, lineY + 1, COLOR_BORDER_SUBTLE);
        graphics.fill(centerX - lineCenter, lineY, centerX + lineCenter, lineY + 1, COLOR_GLOW_BLUE);

        UIScaleManager.drawScaledCenteredString(graphics, f, "- OR -", centerX, centerY, COLOR_TEXT_DIM);
        UIScaleManager.drawScaledCenteredString(graphics, f, "Wait for another player to invite you.", centerX, centerY + UIScaleManager.scale(15), COLOR_TEXT_GRAY);
        UIScaleManager.drawScaledCenteredString(graphics, f, "Invites will appear as a popup notification.", centerX, centerY + UIScaleManager.scale(30), COLOR_TEXT_DIM);
    }

    // Utility methods

    private String getPresetSymbol(MobDifficultyPreset preset) {
        return switch (preset) {
            case SWARM -> "[Swarm]";
            case STANDARD -> "[Standard]";
            case TANK -> "[Tank]";
            case GLASS_CANNON -> "[Glass]";
            case BOSS_STYLE -> "[Boss]";
        };
    }

    private String buildLobbyNextAction(QuestType questType, List<PartySyncPayload.PartyMemberInfo> members, boolean isLeader) {
        if (!isLeader) {
            return "NEXT: Wait for leader";
        }
        int memberCount = members != null ? members.size() : 0;
        boolean soloAllowed = questType.allowsSoloPlay();
        boolean enoughPlayers = (soloAllowed && memberCount == 1) || memberCount >= questType.getMinPlayers();
        if (!enoughPlayers) {
            return "NEXT: Invite players";
        }
        if (members != null) {
            long notReady = members.stream().filter(m -> !m.isReady()).count();
            if (notReady > 0) {
                return "NEXT: Everyone READY";
            }
        }
        return "NEXT: Start quest";
    }

    public int getTierColor(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> DesignTokens.Party.DIFFICULTY_TRIVIAL;
            case EASY -> DesignTokens.Party.DIFFICULTY_EASY;
            case MEDIUM -> DesignTokens.Party.DIFFICULTY_MEDIUM;
            case HARD -> DesignTokens.Party.DIFFICULTY_HARD;
            case ELITE -> DesignTokens.Party.DIFFICULTY_ELITE;
            case BOSS -> DesignTokens.Party.DIFFICULTY_BOSS;
        };
    }

    public String getTierBadge(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> "* Trivial";
            case EASY -> "** Easy";
            case MEDIUM -> "*** Medium";
            case HARD -> "**** Hard";
            case ELITE -> "***** Elite";
            case BOSS -> "BOSS";
        };
    }

    @Nonnull
    private String fitToWidth(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && font.width(trimmed + "..") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "" : trimmed + "..";
    }

    private String shortId(String fullId) {
        if (fullId == null || fullId.isEmpty()) {
            return "-";
        }
        return fullId.length() <= 8 ? fullId : fullId.substring(0, 8);
    }

    private String buildWaitingLabel(List<PartySyncPayload.PartyMemberInfo> members) {
        if (members == null || members.isEmpty()) {
            return "";
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (PartySyncPayload.PartyMemberInfo member : members) {
            if (!member.isOnline() || member.isSpectator() || member.isReady()) {
                continue;
            }
            names.add(truncateName(member.playerName(), 6));
        }
        if (names.isEmpty()) {
            return "";
        }
        int cap = Math.min(2, names.size());
        String text = String.join(", ", names.subList(0, cap));
        if (names.size() > cap) {
            text = text + " +" + (names.size() - cap);
        }
        return "Waiting: " + text;
    }

    private String truncateName(String name, int maxLen) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.length() <= maxLen) {
            return name;
        }
        return name.substring(0, Math.max(1, maxLen - 2)) + "..";
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) {
            return "00:00";
        }
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + ":" + String.format("%02d:%02d", minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
