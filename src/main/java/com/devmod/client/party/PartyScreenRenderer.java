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

    // Colors (aligned with project tokens)
    private static final int COLOR_PANEL_BG = DesignTokens.Background.PANEL;
    private static final int COLOR_PANEL_HEADER = DesignTokens.Background.HEADER;
    private static final int COLOR_PANEL_DIVIDER = DesignTokens.Border.MUTED;
    private static final int COLOR_PANEL_BORDER = DesignTokens.Border.DEFAULT;
    private static final int COLOR_ACCENT = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_ACCENT_SOFT = DesignTokens.Background.GLOW;

    private static final int COLOR_TEXT_PRIMARY = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_SECONDARY = DesignTokens.Text.SECONDARY;
    private static final int COLOR_TEXT_MUTED = DesignTokens.Text.MUTED;

    private static final int COLOR_READY = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_NOT_READY = DesignTokens.Semantic.ERROR;
    private static final int COLOR_LEADER_GOLD = DesignTokens.Semantic.WARNING;

    private static final int COLOR_TAB_ACTIVE = DesignTokens.Background.TAB_ACTIVE;
    private static final int COLOR_TAB_HOVER = DesignTokens.Background.HOVER;
    private static final int COLOR_TAB_DEFAULT = DesignTokens.Background.TAB_INACTIVE;
    private static final int COLOR_ROW_HOVER = DesignTokens.withAlpha(DesignTokens.Background.HOVER, 0xE0);
    private static final int COLOR_ROW_DEFAULT = DesignTokens.withAlpha(DesignTokens.Background.CONTENT, 0xCC);
    private static final int COLOR_HINT_TEXT = DesignTokens.Text.MUTED;
    private static final int COLOR_BAR_BG = DesignTokens.Background.INPUT;

    // Stat colors
    private static final int COLOR_STAT_HP = DesignTokens.Party.STAT_HP;
    private static final int COLOR_STAT_DMG = DesignTokens.Party.STAT_DMG;
    private static final int COLOR_STAT_POINTS = DesignTokens.Party.STAT_POINTS;
    private static final int COLOR_STAT_DIFFICULTY = DesignTokens.Party.STAT_DIFFICULTY;

    private static final int MAX_PREVIEW_WAVE = 20;

    private final PartyScreen screen;

    public PartyScreenRenderer(PartyScreen screen) {
        this.screen = screen;
    }

    @Nonnull
    private net.minecraft.client.gui.Font getFont() {
        return screen.getScreenFont();
    }

    private int s(int value) {
        float scale = Math.max(1f, UIScaleManager.getEffectiveScale());
        return UIScaleManager.snap((int) (value * scale));
    }

    private int line() {
        return getFont().lineHeight;
    }

    private int lineGap() {
        return s(2);
    }

    private int panelHeaderHeight() {
        return Math.max(s(22), line() + s(6));
    }

    private int memberRowHeight() {
        return Math.max(s(32), line() * 2 + s(8));
    }

    private int mobRowHeight() {
        return Math.max(s(24), line() * 2 + s(6));
    }

    private int inputHeight() {
        return Math.max(s(20), line() + s(6));
    }

    private int filterButtonHeight() {
        return Math.max(s(12), line() + s(2));
    }

    private int tierButtonHeight() {
        return Math.max(s(14), line() + s(2));
    }

    private int tabBarHeight() {
        return Math.max(s(28), line() * 2 + s(8));
    }

    public void renderMainPanel(GuiGraphics graphics) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        int panelWidth = screen.getPanelWidth();
        int panelHeight = screen.getPanelHeight();
        PartyScreen.PartyLayout layout = screen.getLayout();

        // Panel background + header strip
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL_BG);
        int headerHeight = Math.max(s(36), layout.contentTop() - panelY - tabBarHeight() - s(6));
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + headerHeight, COLOR_PANEL_HEADER);
        graphics.fill(panelX, panelY + headerHeight, panelX + panelWidth, panelY + headerHeight + 1, COLOR_PANEL_DIVIDER);
        graphics.fill(panelX, panelY, panelX + s(3), panelY + headerHeight, COLOR_ACCENT);
        AxiomRenderer.drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, COLOR_PANEL_BORDER);

        // Title with glow
        String title = "PARTY MANAGEMENT";
        net.minecraft.client.gui.Font f = getFont();
        int titleX = panelX + panelWidth / 2 - f.width(title) / 2;
        int textY = panelY + s(10);
        int titleY = textY;

        // Main title
        UIScaleManager.drawScaledString(graphics, f, title, titleX, titleY, COLOR_TEXT_PRIMARY, false);

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
            textY += line() + lineGap();
            int subX = panelX + panelWidth / 2 - f.width(subtitle) / 2;
            UIScaleManager.drawScaledString(graphics, f, subtitle, subX, textY, COLOR_TEXT_SECONDARY, false);
            if (partyState != PartyData.PartyState.IN_QUEST) {
                String startReason = screen.getStartBlockReason();
                if (startReason != null && !startReason.isBlank()) {
                    String hint = "START LOCKED: " + startReason;
                    int hintX = panelX + panelWidth / 2 - f.width(hint) / 2;
                    textY += line() + lineGap();
                    UIScaleManager.drawScaledString(graphics, f, hint, hintX, textY, COLOR_NOT_READY, false);
                }
                String nextHint = buildLobbyNextAction(questType, members, screen.isLeader());
                if (nextHint != null && !nextHint.isBlank()) {
                    @Nonnull String nextText = java.util.Objects.requireNonNull(fitToWidth(f, nextHint, panelWidth - s(40)));
                    int nextX = panelX + panelWidth / 2 - f.width(nextText) / 2;
                    textY += line() + lineGap();
                    UIScaleManager.drawScaledString(graphics, f, nextText, nextX, textY, COLOR_TEXT_MUTED, false);
                }
            }
        }
    }

    public void drawAnimatedBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        int borderThick = s(2);
        int borderThin = s(3);
        // Top
        graphics.fill(x, y, x + w, y + borderThick, color);
        // Bottom
        graphics.fill(x, y + h - borderThick, x + w, y + h, color);
        // Left
        graphics.fill(x, y, x + borderThick, y + h, color);
        // Right
        graphics.fill(x + w - borderThick, y, x + w, y + h, color);

        // Corner accents
        int cornerSize = s(8);
        // Top-left
        graphics.fill(x, y, x + cornerSize, y + borderThin, COLOR_ACCENT);
        graphics.fill(x, y, x + borderThin, y + cornerSize, COLOR_ACCENT);
        // Top-right
        graphics.fill(x + w - cornerSize, y, x + w, y + borderThin, COLOR_ACCENT);
        graphics.fill(x + w - borderThin, y, x + w, y + cornerSize, COLOR_ACCENT);
        // Bottom-left
        graphics.fill(x, y + h - borderThin, x + cornerSize, y + h, COLOR_ACCENT);
        graphics.fill(x, y + h - cornerSize, x + borderThin, y + h, COLOR_ACCENT);
        // Bottom-right
        graphics.fill(x + w - cornerSize, y + h - borderThin, x + w, y + h, COLOR_ACCENT);
        graphics.fill(x + w - borderThin, y + h - cornerSize, x + w, y + h, COLOR_ACCENT);
    }

    public int renderQuestTypeTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = screen.getPanelX();
        int panelWidth = screen.getPanelWidth();
        PartyScreen.PartyLayout layout = screen.getLayout();
        QuestType questType = screen.getQuestType();

        int tabHeight = tabBarHeight();
        int tabGap = s(4);
        int tabY = layout.contentTop() - tabHeight - s(6);
        int tabStartX = panelX + s(20);
        int usableW = panelWidth - s(40);
        int tabs = QuestType.values().length;
        int tabWidth = tabs > 0 ? (usableW - tabGap * (tabs - 1)) / tabs : s(80);
        net.minecraft.client.gui.Font f = getFont();
        int contentHeight = line() * 2 + lineGap();
        int textTop = tabY + Math.max(s(3), (tabHeight - contentHeight) / 2);

        int hoveredQuestTab = -1;

        for (int i = 0; i < QuestType.values().length; i++) {
            QuestType type = QuestType.values()[i];
            int tabX = tabStartX + i * (tabWidth + tabGap);

            boolean isActive = type == questType;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth - tabGap &&
                    mouseY >= tabY && mouseY < tabY + tabHeight;

            if (isHovered) hoveredQuestTab = i;

            // Tab background
            int bgColor = isActive ? COLOR_TAB_ACTIVE : (isHovered ? COLOR_TAB_HOVER : COLOR_TAB_DEFAULT);
            graphics.fill(tabX, tabY, tabX + tabWidth - tabGap, tabY + tabHeight, bgColor);

            // Active indicator
            if (isActive) {
                graphics.fill(tabX, tabY + tabHeight - s(3), tabX + tabWidth - tabGap, tabY + tabHeight, COLOR_ACCENT_SOFT);
                graphics.fill(tabX, tabY + tabHeight - s(2), tabX + tabWidth - tabGap, tabY + tabHeight, COLOR_ACCENT);
            }

            // Border
            int borderColor = isActive ? COLOR_ACCENT : (isHovered ? COLOR_PANEL_BORDER : COLOR_PANEL_DIVIDER);
            AxiomRenderer.drawBorder(graphics, tabX, tabY, tabWidth - tabGap, tabHeight, borderColor);

            // Icon + Name
            String icon = getQuestTypeIcon(type);
            String name = type.getDisplayName();
            int textColor = isActive ? COLOR_ACCENT : (isHovered ? COLOR_TEXT_PRIMARY : COLOR_TEXT_SECONDARY);

            String label = fitToWidth(f, icon + " " + name, Math.max(0, tabWidth - s(6)));
            UIScaleManager.drawScaledCenteredString(graphics, f, label, tabX + (tabWidth - tabGap) / 2, textTop, textColor);

            // Player range
            String range = type.getMinPlayers() + "-" + type.getMaxPlayers() + " players";
            String rangeLabel = fitToWidth(f, range, Math.max(0, tabWidth - s(6)));
            UIScaleManager.drawScaledCenteredString(graphics, f, rangeLabel, tabX + (tabWidth - tabGap) / 2, textTop + line() + lineGap(), COLOR_TEXT_MUTED);
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
        PartyScreen.PartyLayout layout = screen.getLayout();
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

        int panelLeft = layout.membersX();
        int panelTop = layout.membersY();
        int panelW = layout.membersW();
        int panelH = layout.membersH();
        int headerH = panelHeaderHeight();
        net.minecraft.client.gui.Font f = getFont();
        int headerTextY = panelTop + Math.max(s(2), (headerH - line()) / 2);

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COLOR_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, COLOR_PANEL_BORDER);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + headerH, COLOR_PANEL_HEADER);
        graphics.fill(panelLeft, panelTop, panelLeft + s(3), panelTop + headerH, COLOR_ACCENT);
        UIScaleManager.drawScaledString(graphics, f, "PARTY MEMBERS", panelLeft + s(8), headerTextY, COLOR_TEXT_PRIMARY, false);
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
        String countsLong = "Act" + activeCount + " Ready" + readyCount + " Spec" + spectatorCount + " Off" + offlineCount;
        int labelWidth = f.width("PARTY MEMBERS");
        int maxCountsWidth = panelW - s(16) - labelWidth - s(8);
        String counts = countsLong;
        if (maxCountsWidth > 0 && f.width(countsLong) > maxCountsWidth) {
            counts = "A" + activeCount + " R" + readyCount + " S" + spectatorCount + " O" + offlineCount;
        }
        counts = java.util.Objects.requireNonNull(fitToWidth(f, counts, Math.max(0, panelW - s(16))));
        int countsWidth = f.width(counts);
        UIScaleManager.drawScaledString(graphics, f, counts, panelLeft + panelW - countsWidth - s(6), headerTextY, COLOR_TEXT_MUTED, false);

        int hoveredMemberIndex = -1;
        int memberY = panelTop + headerH + s(6);
        int rowHeight = memberRowHeight();

        int visibleCount = Math.min(maxVisible, Math.max(0, members.size() - scrollOffset));
        for (int i = 0; i < visibleCount; i++) {
            PartySyncPayload.PartyMemberInfo member = members.get(scrollOffset + i);
            float anim = i < memberAnimations.length ? memberAnimations[i] : 1f;

            int rowY = memberY + i * rowHeight;
            int rowH = rowHeight;

            // Slide-in animation
            int offsetX = (int) ((1f - anim) * -s(50));
            int rowX = panelLeft + s(5) + offsetX;
            int rowW = panelW - s(10);

            boolean isHovered = mouseX >= panelLeft + s(5) && mouseX < panelLeft + panelW - s(5) &&
                    mouseY >= rowY && mouseY < rowY + rowH;
            if (isHovered) hoveredMemberIndex = scrollOffset + i;

            // Row background
            int rowBg = isHovered ? COLOR_ROW_HOVER : COLOR_ROW_DEFAULT;
            graphics.fill(rowX, rowY, rowX + rowW, rowY + rowH, rowBg);

            boolean isMemberLeader = member.playerId().equals(leaderId);

            String status;
            int statusTextColor;
            int statusColor;
            int statusGlow;
            String tag;
            int tagColor;
            if (!member.isOnline()) {
                status = "Offline";
                statusTextColor = COLOR_TEXT_MUTED;
                statusColor = COLOR_TEXT_MUTED;
                tag = "OFF";
                tagColor = COLOR_TEXT_MUTED;
            } else if (inQuest) {
                if (member.isSpectator()) {
                    status = "Spectating";
                    statusTextColor = COLOR_TEXT_MUTED;
                    statusColor = COLOR_NOT_READY;
                    tag = "SPEC";
                    tagColor = COLOR_TEXT_MUTED;
                } else if (member.isReady()) {
                    status = "Ready to continue";
                    statusTextColor = COLOR_READY;
                    statusColor = COLOR_READY;
                    tag = "READY";
                    tagColor = COLOR_READY;
                } else {
                    status = "Fighting";
                    statusTextColor = COLOR_ACCENT;
                    statusColor = COLOR_ACCENT;
                    tag = "ACTIVE";
                    tagColor = COLOR_ACCENT;
                }
            } else {
                status = member.isReady() ? "Ready" : "Not ready";
                statusTextColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
                statusColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
                tag = member.isReady() ? "READY" : "WAIT";
                tagColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            }
            statusGlow = DesignTokens.withAlpha(statusColor, 0x40);

            int statusX = rowX + s(5);
            int barOuterTop = rowY + s(4);
            int barOuterBottom = rowY + rowH - s(4);
            int barInnerTop = rowY + s(6);
            int barInnerBottom = rowY + rowH - s(6);

            // Status glow
            graphics.fill(statusX - s(2), barOuterTop, statusX + s(10), barOuterBottom, statusGlow);
            graphics.fill(statusX, barInnerTop, statusX + s(8), barInnerBottom, statusColor);

            // Leader crown or player icon
            String prefix = isMemberLeader ? "[L] " : "    ";
            int nameColor = isMemberLeader ? COLOR_LEADER_GOLD : COLOR_TEXT_PRIMARY;

            int tagPadding = s(4);
            int tagHeight = Math.max(s(12), line() + s(2));
            int tagWidth = f.width(tag) + tagPadding * 2;
            int tagX = rowX + rowW - tagWidth - s(6);

            // Player name
            String displayName = member.playerName();
            Minecraft mc = Minecraft.getInstance();
            var localPlayer = mc.player;
            if (localPlayer != null && member.playerId().equals(localPlayer.getUUID())) {
                displayName += " (You)";
            }
            int maxTextWidth = Math.max(0, tagX - (rowX + s(18)) - s(6));
            String displayLine = fitToWidth(f, prefix + displayName, maxTextWidth);
            int nameY = rowY + s(4);
            UIScaleManager.drawScaledString(graphics, f, displayLine, rowX + s(18), nameY, nameColor, false);

            // Status text
            String kitLabel = resolveMemberKitLabel(member);
            String statusDetail = kitLabel != null && !kitLabel.isBlank()
                ? status + " | " + kitLabel
                : status;
            String statusLine = fitToWidth(f, statusDetail, maxTextWidth);
            int statusY = nameY + line() + lineGap();
            UIScaleManager.drawScaledString(graphics, f, statusLine, rowX + s(18), statusY, statusTextColor, false);

            // Status tag
            int tagY = rowY + (rowH - tagHeight) / 2;
            graphics.fill(tagX, tagY, tagX + tagWidth, tagY + tagHeight, tagColor);
            int tagTextY = tagY + Math.max(0, (tagHeight - line()) / 2);
            UIScaleManager.drawScaledString(graphics, f, tag, tagX + tagPadding, tagTextY, COLOR_TEXT_PRIMARY, false);

            // Kick hint on hover (for leader)
            if (isHovered && isLeader && !member.playerId().equals(leaderId) && !inQuest) {
                int hintX = Math.max(rowX + s(18), rowX + rowW - s(95));
                int hintY = rowY + Math.max(0, (rowH - line()) / 2);
                UIScaleManager.drawScaledString(graphics, f, "[Right-click: Kick]", hintX, hintY, COLOR_TEXT_MUTED, false);
            }
        }

        int footerY = panelTop + panelH - line() - s(4);
        if (members.size() > maxVisible && visibleCount > 0) {
            String range = String.format("%d-%d/%d",
                scrollOffset + 1,
                scrollOffset + visibleCount,
                members.size());
            UIScaleManager.drawScaledString(graphics, f, range, panelLeft + panelW - s(70), footerY, COLOR_TEXT_MUTED, false);
        }

        String composition = buildCompositionLine(members);
        if (composition != null && !composition.isBlank()) {
            int compMaxWidth = members.size() > maxVisible ? panelW - s(80) : panelW - s(16);
            String compLine = fitToWidth(f, composition, compMaxWidth);
            UIScaleManager.drawScaledString(graphics, f, compLine, panelLeft + s(8), footerY, COLOR_TEXT_MUTED, false);
        }

        // Empty state
        if (members.isEmpty()) {
            int emptyY = panelTop + headerH + s(30);
            UIScaleManager.drawScaledCenteredString(graphics, f, "No warriors yet...", panelLeft + panelW / 2, emptyY, COLOR_TEXT_MUTED);
            UIScaleManager.drawScaledCenteredString(graphics, f, "Invite players below", panelLeft + panelW / 2, emptyY + line() + lineGap(), COLOR_TEXT_MUTED);
        }

        // Invite section header
        int inviteSectionY = panelTop + panelH + s(8);
        UIScaleManager.drawScaledString(graphics, f, "> INVITE PLAYER", panelLeft + s(5), inviteSectionY, COLOR_TEXT_MUTED, false);

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
        PartyScreen.PartyLayout layout = screen.getLayout();
        List<EnduranceQuestRegistry.MobQuestConfig> filteredMobs = screen.getFilteredMobs();
        int selectedMobIndex = screen.getSelectedMobIndex();
        int mobListScrollOffset = screen.getMobListScrollOffset();
        String selectedNamespace = screen.getSelectedNamespace();
        MobTier selectedTierFilter = screen.getSelectedTierFilter();

        int panelLeft = layout.mobX();
        int panelTop = layout.mobY();
        int panelW = layout.mobW();
        int panelH = layout.mobH();
        int headerH = panelHeaderHeight();
        net.minecraft.client.gui.Font f = getFont();
        int headerTextY = panelTop + Math.max(s(2), (headerH - line()) / 2);

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COLOR_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, COLOR_PANEL_BORDER);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + headerH, COLOR_PANEL_HEADER);
        graphics.fill(panelLeft, panelTop, panelLeft + s(3), panelTop + headerH, COLOR_ACCENT);
        UIScaleManager.drawScaledString(graphics, f, "SELECT ENEMY", panelLeft + s(8), headerTextY, COLOR_TEXT_PRIMARY, false);

        // Search background
        EditBox searchBox = screen.getMobSearchBox();
        if (searchBox != null && searchBox.visible) {
            AxiomRenderer.drawInputBackground(graphics, searchBox.getX(), searchBox.getY(), searchBox.getWidth(),
                searchBox.getHeight(), searchBox.isFocused());
        }

        // Namespace filter buttons (All / MC / Mods)
        int searchY = panelTop + headerH + s(6);
        int searchH = inputHeight();
        if (searchBox != null) {
            searchY = searchBox.getY();
            searchH = searchBox.getHeight();
        }

        // COMPACT LAYOUT: Namespace filters inline to the right of search area
        // Position them at the right side of the panel, same Y as search
        int nsBtnH = Math.min(searchH, filterButtonHeight());
        int nsBtnY = searchY + (searchH - nsBtnH) / 2; // Center vertically with search
        int nsTextY = nsBtnY + Math.max(0, (nsBtnH - line()) / 2);
        int nsBtnX = panelLeft + panelW - s(60); // Position at right side

        // "All" button (compact)
        boolean allActive = selectedNamespace == null;
        int allW = s(22);
        int allColor = allActive ? COLOR_TAB_ACTIVE : COLOR_TAB_DEFAULT;
        boolean allHovered = mouseX >= nsBtnX && mouseX < nsBtnX + allW &&
                            mouseY >= nsBtnY && mouseY < nsBtnY + nsBtnH;
        if (allHovered) allColor = DesignTokens.lighten(allColor, 0.2f);
        graphics.fill(nsBtnX, nsBtnY, nsBtnX + allW, nsBtnY + nsBtnH, allColor);
        AxiomRenderer.drawBorder(graphics, nsBtnX, nsBtnY, allW, nsBtnH, allActive ? COLOR_ACCENT : COLOR_PANEL_DIVIDER);
        UIScaleManager.drawScaledCenteredString(graphics, f, "All", nsBtnX + allW / 2, nsTextY, COLOR_TEXT_PRIMARY);
        nsBtnX += allW + s(2);

        // "MC" button (compact)
        boolean mcActive = "minecraft".equals(selectedNamespace);
        int mcW = s(22);
        int mcColor = mcActive ? COLOR_TAB_ACTIVE : COLOR_TAB_DEFAULT;
        boolean mcHovered = mouseX >= nsBtnX && mouseX < nsBtnX + mcW &&
                           mouseY >= nsBtnY && mouseY < nsBtnY + nsBtnH;
        if (mcHovered) mcColor = DesignTokens.lighten(mcColor, 0.2f);
        graphics.fill(nsBtnX, nsBtnY, nsBtnX + mcW, nsBtnY + nsBtnH, mcColor);
        AxiomRenderer.drawBorder(graphics, nsBtnX, nsBtnY, mcW, nsBtnH, mcActive ? COLOR_ACCENT : COLOR_PANEL_DIVIDER);
        UIScaleManager.drawScaledCenteredString(graphics, f, "MC", nsBtnX + mcW / 2, nsTextY, COLOR_TEXT_PRIMARY);

        // Tier filter buttons - directly below search row with minimal gap
        int filterY = searchY + searchH + s(2);
        int btnH = tierButtonHeight();
        int btnX = panelLeft + s(5);
        int tierTextY = filterY + Math.max(0, (btnH - line()) / 2);

        // Short unique labels: 1-2 chars + color makes them identifiable
        String[] tierLabels = {"1", "2", "3", "4", "5", "B"};
        MobTier[] tiers = MobTier.values();
        float[] hoverAnims = screen.getTierHoverAnimations();
        int hoveredTier = -1;
        int btnW = s(20);

        for (int i = 0; i < tiers.length && i < tierLabels.length; i++) {
            MobTier tier = tiers[i];
            String label = tierLabels[i];
            int tierColor = getTierColor(tier);

            boolean active = tier == selectedTierFilter;
            float hoverAnim = i < hoverAnims.length ? hoverAnims[i] : 0f;

            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW &&
                             mouseY >= filterY && mouseY < filterY + btnH;
            if (hovered) hoveredTier = i;

            // Background - tier color when active, default otherwise
            int bgColor = active ? tierColor : COLOR_TAB_DEFAULT;
            if (hoverAnim > 0.1f && !active) {
                bgColor = blendColors(COLOR_TAB_DEFAULT, DesignTokens.withAlpha(tierColor, 0x80), hoverAnim);
            }
            graphics.fill(btnX, filterY, btnX + btnW, filterY + btnH, bgColor);

            // Left color bar (always shows tier color)
            graphics.fill(btnX, filterY, btnX + s(3), filterY + btnH, tierColor);

            // Border
            AxiomRenderer.drawBorder(graphics, btnX, filterY, btnW, btnH,
                active ? tierColor : COLOR_PANEL_DIVIDER);

            // Label
            int textColor = active ? COLOR_TEXT_PRIMARY : (hoverAnim > 0.5f ? tierColor : COLOR_TEXT_SECONDARY);
            UIScaleManager.drawScaledCenteredString(graphics, f, label, btnX + btnW / 2, tierTextY, textColor);

            btnX += btnW + s(2);
        }

        screen.setHoveredTierIndex(hoveredTier);

        // Mob list with smooth hover animations
        int listY = screen.getMobListTop();
        int hoveredMobIndex = -1;
        int rowHeightCompact = mobRowHeight();
        int listPadding = s(6);
        int maxVisibleMobs = screen.getMaxVisibleMobs();
        float[] mobHoverAnims = screen.getMobHoverAnimations();

        for (int i = 0; i < maxVisibleMobs; i++) {
            int mobIndex = mobListScrollOffset + i;
            if (mobIndex >= filteredMobs.size()) break;

            EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(mobIndex);
            int rowY = listY + i * rowHeightCompact;
            int rowH = rowHeightCompact - s(2);
            int rowX = panelLeft + listPadding;
            int rowW = panelW - listPadding * 2 - s(10); // Space for scrollbar

            boolean isSelected = mobIndex == selectedMobIndex;
            boolean isHovered = mouseX >= rowX && mouseX < rowX + rowW &&
                    mouseY >= rowY && mouseY < rowY + rowH;

            if (isHovered) hoveredMobIndex = mobIndex;

            // Get smooth hover animation value
            float hoverAnim = i < mobHoverAnims.length ? mobHoverAnims[i] : 0f;
            int tierColor = getTierColor(config.getTier());

            // Animated row background
            if (isSelected) {
                // Selected: accent glow with pulse effect
                float pulse = screen.getGlowPulse();
                int glowAlpha = (int) (0x30 + 0x20 * pulse);
                graphics.fill(rowX - s(2), rowY - s(1), rowX + rowW + s(2), rowY + rowH + s(1),
                    DesignTokens.withAlpha(COLOR_ACCENT, glowAlpha));
                graphics.fill(rowX, rowY, rowX + rowW, rowY + rowH, COLOR_TAB_ACTIVE);

                // Bevel effect on selected
                int highlight = DesignTokens.withAlpha(DesignTokens.lighten(COLOR_TAB_ACTIVE, 0.2f), 0x60);
                graphics.fill(rowX + 1, rowY + 1, rowX + rowW - 1, rowY + s(2), highlight);

                // Left accent bar with glow
                graphics.fill(rowX, rowY, rowX + s(4), rowY + rowH, COLOR_ACCENT);
            } else {
                // Non-selected: animated background based on hover
                int baseBg = COLOR_ROW_DEFAULT;
                int hoverBg = COLOR_ROW_HOVER;
                int animatedBg = blendColors(baseBg, hoverBg, hoverAnim);
                graphics.fill(rowX, rowY, rowX + rowW, rowY + rowH, animatedBg);

                // Animated tier bar (expands on hover)
                int barWidth = s(3) + (int) (s(2) * hoverAnim);
                int barColor = blendColors(DesignTokens.withAlpha(tierColor, 0xA0), tierColor, hoverAnim);
                graphics.fill(rowX, rowY + s(1), rowX + barWidth, rowY + rowH - s(1), barColor);

                // Subtle highlight on hover
                if (hoverAnim > 0.1f) {
                    int highlightAlpha = (int) (0x20 * hoverAnim);
                    graphics.fill(rowX + 1, rowY + 1, rowX + rowW - 1, rowY + s(2),
                        DesignTokens.withAlpha(DesignTokens.Notification.RGB_WHITE, highlightAlpha));
                }
            }

            // Mob name with animated color
            String name = Objects.requireNonNull(config.getDisplayName());
            int textX = rowX + s(10);
            int maxNameWidth = rowW - s(20);
            if (f.width(name) > maxNameWidth) {
                name = Objects.requireNonNull(f.plainSubstrByWidth(name, maxNameWidth - s(10))) + "..";
            }

            int baseNameColor = COLOR_TEXT_SECONDARY;
            int hoverNameColor = COLOR_TEXT_PRIMARY;
            int nameColor = isSelected ? COLOR_ACCENT : blendColors(baseNameColor, hoverNameColor, hoverAnim);
            int nameY = rowY + s(2);
            UIScaleManager.drawScaledString(graphics, f, name, textX, nameY, nameColor, false);

            // Difficulty preset with tier color hint on hover
            String preset = getPresetSymbol(config.getDifficultyPreset());
            int presetColor = isSelected ? COLOR_TEXT_SECONDARY : blendColors(COLOR_TEXT_MUTED, tierColor, hoverAnim * 0.5f);
            UIScaleManager.drawScaledString(graphics, f, preset, textX, nameY + line() + s(1), presetColor, false);

            // Enemy count on right (fades in on hover)
            int enemyCount = config.getMobCountForWave(1, 1, screen.getQuestType());
            String countLabel = enemyCount + " mobs";
            int countWidth = f.width(countLabel);
            int countAlpha = (int) (0x80 + 0x7F * hoverAnim);
            int countColor = DesignTokens.withAlpha(COLOR_TEXT_MUTED, countAlpha);
            UIScaleManager.drawScaledString(graphics, f, countLabel, rowX + rowW - countWidth - s(4), nameY + line() + s(1), countColor, false);
        }

        // Enhanced scroll indicators with fade gradients and scrollbar
        int listHeight = maxVisibleMobs * rowHeightCompact;
        int maxScroll = Math.max(0, filteredMobs.size() - maxVisibleMobs);
        boolean canScrollUp = mobListScrollOffset > 0;
        boolean canScrollDown = mobListScrollOffset < maxScroll;

        // Top fade gradient when scrollable
        if (canScrollUp) {
            int fadeH = s(12);
            for (int i = 0; i < fadeH; i++) {
                float ratio = 1f - (float) i / fadeH;
                int alpha = (int) (0xC0 * ratio);
                int fadeColor = (alpha << 24) | (COLOR_PANEL_BG & DesignTokens.Mask.RGB);
                graphics.fill(panelLeft + listPadding, listY + i, panelLeft + panelW - listPadding, listY + i + 1, fadeColor);
            }
            // Arrow indicator
            UIScaleManager.drawScaledCenteredString(graphics, f, "\u25B2", panelLeft + panelW - s(12), listY + s(2), COLOR_ACCENT);
        }

        // Bottom fade gradient when scrollable
        if (canScrollDown) {
            int fadeH = s(12);
            int bottomY = listY + listHeight;
            for (int i = 0; i < fadeH; i++) {
                float ratio = (float) i / fadeH;
                int alpha = (int) (0xC0 * ratio);
                int fadeColor = (alpha << 24) | (COLOR_PANEL_BG & DesignTokens.Mask.RGB);
                graphics.fill(panelLeft + listPadding, bottomY - fadeH + i, panelLeft + panelW - listPadding, bottomY - fadeH + i + 1, fadeColor);
            }
            // Arrow indicator
            UIScaleManager.drawScaledCenteredString(graphics, f, "\u25BC", panelLeft + panelW - s(12), bottomY - line() - s(2), COLOR_ACCENT);
        }

        // Interactive scrollbar with state-based colors (right side)
        if (filteredMobs.size() > maxVisibleMobs) {
            int scrollbarX = panelLeft + panelW - s(8);
            int scrollbarW = s(4);
            int scrollbarH = listHeight;

            // Check if mouse is over scrollbar area
            boolean scrollbarHovered = mouseX >= scrollbarX - s(2) && mouseX < scrollbarX + scrollbarW + s(2) &&
                                       mouseY >= listY && mouseY < listY + scrollbarH;
            boolean isDragging = screen.isDraggingMobScrollbar();

            // Track with subtle background
            int trackColor = scrollbarHovered ? DesignTokens.lighten(COLOR_TAB_DEFAULT, 0.1f) : COLOR_TAB_DEFAULT;
            graphics.fill(scrollbarX, listY, scrollbarX + scrollbarW, listY + scrollbarH, trackColor);

            // Thumb with state-based styling
            float thumbRatio = (float) maxVisibleMobs / filteredMobs.size();
            int thumbH = Math.max(s(20), (int) (scrollbarH * thumbRatio));
            float scrollProgress = maxScroll > 0 ? (float) mobListScrollOffset / maxScroll : 0f;
            int thumbY = listY + (int) ((scrollbarH - thumbH) * scrollProgress);

            // State-based thumb color
            int thumbColor;
            if (isDragging) {
                thumbColor = DesignTokens.lighten(COLOR_ACCENT, 0.2f); // Active/dragging
            } else if (scrollbarHovered) {
                thumbColor = COLOR_ACCENT; // Hovered
            } else {
                thumbColor = DesignTokens.withAlpha(COLOR_ACCENT, 0xC0); // Normal (slightly transparent)
            }

            // Thumb with rounded appearance (top/bottom highlights)
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, thumbColor);

            // Top highlight on thumb for 3D effect
            int thumbHighlight = DesignTokens.withAlpha(DesignTokens.lighten(thumbColor, 0.3f), 0x80);
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + s(2), thumbHighlight);

            // Drag hint when hovered
            if (scrollbarHovered && !isDragging) {
                int hintAlpha = 0x40;
                graphics.fill(scrollbarX - s(1), thumbY - s(1), scrollbarX + scrollbarW + s(1), thumbY + thumbH + s(1),
                    DesignTokens.withAlpha(COLOR_ACCENT, hintAlpha));
            }
        }

        // Count with scroll position
        String countText;
        if (filteredMobs.size() > maxVisibleMobs) {
            int firstVisible = mobListScrollOffset + 1;
            int lastVisible = Math.min(mobListScrollOffset + maxVisibleMobs, filteredMobs.size());
            countText = firstVisible + "-" + lastVisible + " / " + filteredMobs.size() + " enemies";
        } else {
            countText = filteredMobs.size() + " enemies";
        }
        UIScaleManager.drawScaledString(graphics, f, countText, panelLeft + s(8), panelTop + panelH - line() - s(4), COLOR_TEXT_MUTED, false);

        return hoveredMobIndex;
    }

    public void renderRunStatusPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        PartyScreen.PartyLayout layout = screen.getLayout();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();

        int panelLeft = layout.mobX();
        int panelTop = layout.mobY();
        int panelW = layout.mobW();
        int panelH = layout.mobH();
        int headerH = panelHeaderHeight();
        net.minecraft.client.gui.Font f = getFont();
        int headerTextY = panelTop + Math.max(s(2), (headerH - line()) / 2);

        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COLOR_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, COLOR_PANEL_BORDER);

        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + headerH, COLOR_PANEL_HEADER);
        graphics.fill(panelLeft, panelTop, panelLeft + s(3), panelTop + headerH, COLOR_ACCENT);
        UIScaleManager.drawScaledString(graphics, f, "RUN STATUS", panelLeft + s(8), headerTextY, COLOR_TEXT_PRIMARY, false);

        QuestSyncPayload questData = ClientQuestCache.getData();
        boolean hasQuestData = questData != null && questData.hasActiveQuest();
        if (!hasQuestData) {
            UIScaleManager.drawScaledCenteredString(graphics, f, "Awaiting run data", panelLeft + panelW / 2, panelTop + panelH / 2 - line() / 2, COLOR_TEXT_MUTED);
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

        int textX = panelLeft + s(8);
        int textY = panelTop + headerH + s(8);
        int maxWidth = panelW - s(16);
        int lineGap = lineGap();

        String waveLine = fitToWidth(f, "Wave " + waveLabel, maxWidth);
        UIScaleManager.drawScaledString(graphics, f, waveLine, textX, textY, COLOR_TEXT_PRIMARY, false);
        textY += f.lineHeight + lineGap;

        String typeLine = fitToWidth(f, "Type " + questType.getDisplayName(), maxWidth);
        UIScaleManager.drawScaledString(graphics, f, typeLine, textX, textY, COLOR_TEXT_MUTED, false);
        textY += f.lineHeight + lineGap;

        String runLine = fitToWidth(f, "Run " + formatDuration(questDataSafe.sessionDurationMs()), maxWidth);
        UIScaleManager.drawScaledString(graphics, f, runLine, textX, textY, COLOR_TEXT_MUTED, false);
        textY += f.lineHeight + lineGap;

        if (Screen.hasShiftDown()) {
            String questIdShort = shortId(questDataSafe.questId());
            String questLine = fitToWidth(f, "Quest " + questIdShort, maxWidth);
            UIScaleManager.drawScaledString(graphics, f, questLine, textX, textY, COLOR_TEXT_PRIMARY, false);
            textY += f.lineHeight + lineGap;
        } else {
            String questHint = fitToWidth(f, "Hold SHIFT for questId", maxWidth);
            UIScaleManager.drawScaledString(graphics, f, questHint, textX, textY, COLOR_TEXT_MUTED, false);
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
        int tensionColor = tensionActive ? ClientTensionCache.getDisplayColor() : COLOR_TEXT_MUTED;
        UIScaleManager.drawScaledString(graphics, f, tensionLine, textX, textY, tensionColor, false);
        textY += f.lineHeight + lineGap;

        if (syncStale) {
            String syncLine = fitToWidth(f, "Sync stale " + syncSeconds + "s", maxWidth);
            UIScaleManager.drawScaledString(graphics, f, syncLine, textX, textY, COLOR_NOT_READY, false);
            textY += f.lineHeight + lineGap;
        }

        String detailLeft;
        String detailRight = "";
        int detailColor = COLOR_TEXT_PRIMARY;
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
        int leftMax = maxWidth - (rightWidth > 0 ? rightWidth + s(6) : 0);
        detailLeft = fitToWidth(f, detailLeft, leftMax);
        UIScaleManager.drawScaledString(graphics, f, detailLeft, textX, textY, detailColor, false);
        if (!detailRight.isEmpty()) {
            int rightX = panelLeft + panelW - s(8) - rightWidth;
            UIScaleManager.drawScaledString(graphics, f, detailRight, rightX, textY, COLOR_TEXT_MUTED, false);
        }

        if (atCheckpoint) {
            String waitingLabel = buildWaitingLabel(members);
            if (!waitingLabel.isEmpty()) {
                textY += f.lineHeight + lineGap;
                waitingLabel = fitToWidth(f, waitingLabel, maxWidth);
                UIScaleManager.drawScaledString(graphics, f, waitingLabel, textX, textY, COLOR_TEXT_MUTED, false);
            }
        }

        String actionLabel;
        int actionColor = COLOR_TEXT_MUTED;
        if (screen.isLocalSpectator()) {
            actionLabel = "NEXT: READY to REJOIN";
            actionColor = COLOR_ACCENT;
        } else if (atCheckpoint) {
            actionLabel = screen.isLocalReady() ? "NEXT: Waiting party" : "NEXT: READY to CONTINUE";
            actionColor = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
        } else {
            actionLabel = "NEXT: Fighting";
        }
        actionLabel = fitToWidth(f, actionLabel, maxWidth);
        int barCount = (tensionActive ? 1 : 0) + (atCheckpoint ? 1 : 0);
        int barHeight = s(4);
        int barGap = s(4);
        int barStackHeight = barCount > 0 ? barCount * barHeight + (barCount - 1) * barGap : 0;
        int actionY = panelTop + panelH - barStackHeight - line() - s(14);
        UIScaleManager.drawScaledString(graphics, f, actionLabel, textX, actionY, actionColor, false);

        if (barCount > 0) {
            int barX = panelLeft + s(8);
            int barW = panelW - s(16);
            int barY = panelTop + panelH - s(12) - barStackHeight;
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
        PartyScreen.PartyLayout layout = screen.getLayout();
        float glowPulse = screen.getGlowPulse();
        EnduranceQuestRegistry.MobQuestConfig selectedConfig = screen.getSelectedMobConfig();
        boolean selectionFilteredOut = screen.isSelectedMobFilteredOut();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
        QuestType questType = screen.getQuestType();
        LivingEntity previewEntity = screen.getPreviewEntity();
        boolean isDraggingPreview = screen.isDraggingPreview();

        int panelLeft = layout.previewX();
        int panelTop = layout.previewY();
        int panelW = layout.previewW();
        int panelH = layout.previewH();
        int headerH = panelHeaderHeight();
        int innerPadding = s(5);
        net.minecraft.client.gui.Font f = getFont();
        int headerTextY = panelTop + Math.max(s(2), (headerH - line()) / 2);

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COLOR_PANEL_BG);

        // Inner preview area - adapt to available height
        int previewArea = panelTop + headerH;
        int statsSpace = line() * 4 + lineGap() * 3 + s(16);  // Space needed for stats
        int availableForPreview = Math.max(s(60), panelH - headerH - statsSpace);
        int previewH = Math.min(s(120), availableForPreview);
        graphics.fill(panelLeft + innerPadding, previewArea, panelLeft + panelW - innerPadding, previewArea + previewH, DesignTokens.Background.INPUT);

        // Platform - adapt to preview height
        int centerX = panelLeft + panelW / 2;
        int platformY = previewArea + previewH - s(10);
        int platformRadius = Math.min(s(40), panelW / 2 - innerPadding - s(5));
        int glowBand = s(5);

        // Platform glow
        for (int r = platformRadius; r > platformRadius - glowBand; r--) {
            int alpha = (int) ((1 - (platformRadius - r) / (float) glowBand) * (50 + glowPulse * 30));
            int glowC = (alpha << 24) | (COLOR_ACCENT & DesignTokens.Mask.RGB);
            graphics.fill(centerX - r, platformY - s(3), centerX + r, platformY, glowC);
        }

        graphics.fill(centerX - platformRadius + innerPadding, platformY - 1, centerX + platformRadius - innerPadding, platformY, COLOR_ACCENT);

        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, COLOR_PANEL_BORDER);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + headerH, COLOR_PANEL_HEADER);
        graphics.fill(panelLeft, panelTop, panelLeft + s(3), panelTop + headerH, COLOR_ACCENT);
        UIScaleManager.drawScaledString(graphics, f, "PREVIEW", panelLeft + s(8), headerTextY, COLOR_TEXT_PRIMARY, false);
        if (selectionFilteredOut) {
            String filteredLabel = "Filtered out";
            UIScaleManager.drawScaledString(graphics, f, filteredLabel, panelLeft + panelW - f.width(filteredLabel) - s(8), headerTextY, COLOR_TEXT_MUTED, false);
        }

        // Smooth rotation
        float mobRotationY = screen.getMobRotationY();
        float targetMobRotationY = screen.getTargetMobRotationY();
        float mobRotationX = screen.getMobRotationX();
        screen.setMobRotationY(Mth.lerp(0.15f, mobRotationY, targetMobRotationY));

        if (previewEntity != null && selectedConfig != null) {
            int entityCenterY = previewArea + (int)(previewH * 0.7f);

            float mobHeight = previewEntity.getBbHeight();
            float mobWidth = previewEntity.getBbWidth();
            float maxDim = Math.max(mobHeight, mobWidth);
            // Scale entity to fit in preview area
            int maxScale = (int)(previewH * 0.5f);
            int scale = (int) Math.min(maxScale, s(80) / maxDim);

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
                UIScaleManager.drawScaledCenteredString(graphics, f, "[Preview Error]", centerX, entityCenterY - s(20), COLOR_TEXT_MUTED);
            }

            EnduranceQuestRegistry.MobQuestConfig config = selectedConfig;

            // Stats section - mob name and tier
            int statsY = previewArea + previewH + s(6);

            String nameLine = fitToWidth(f, Objects.requireNonNull(config.getDisplayName()), panelW - s(12));
            UIScaleManager.drawScaledCenteredString(graphics, f, nameLine, centerX, statsY, getTierColor(config.getTier()));

            String tierBadge = Objects.requireNonNull(getTierBadge(config.getTier()));
            UIScaleManager.drawScaledCenteredString(graphics, f, tierBadge, centerX, statsY + line() + lineGap(), getTierColor(config.getTier()));

            // Compact stats display - 2 rows only, stays within panel bounds
            int playerCount = Math.max(1, members.size());
            float scaledHP = config.getScaledHealth(playerCount, questType);

            int statY = statsY + (line() + lineGap()) * 2 + s(4);
            int statPadding = s(6);

            // Row 1: Base HP | Base DMG
            String hpLabel = "HP " + String.format("%.0f", config.getBaseHealth());
            String dmgLabel = "DMG " + String.format("%.0f", config.getBaseDamage());
            int halfW = (panelW - statPadding * 3) / 2;

            UIScaleManager.drawScaledString(graphics, f, fitToWidth(f, hpLabel, halfW),
                panelLeft + statPadding, statY, COLOR_STAT_HP, false);
            UIScaleManager.drawScaledString(graphics, f, fitToWidth(f, dmgLabel, halfW),
                panelLeft + statPadding + halfW + statPadding, statY, COLOR_STAT_DMG, false);

            // Row 2: Scaled HP | Points/Kill
            statY += line() + lineGap();
            String scaledLabel = "Scaled " + String.format("%.0f", scaledHP);
            String pointsLabel = config.getPointsPerKill() + " pts/kill";

            UIScaleManager.drawScaledString(graphics, f, fitToWidth(f, scaledLabel, halfW),
                panelLeft + statPadding, statY, COLOR_READY, false);
            UIScaleManager.drawScaledString(graphics, f, fitToWidth(f, pointsLabel, halfW),
                panelLeft + statPadding + halfW + statPadding, statY, COLOR_STAT_POINTS, false);

        } else {
            String emptyText = selectedConfig == null ? "Select an enemy" : "Preview unavailable";
            UIScaleManager.drawScaledCenteredString(graphics, f, emptyText, centerX, previewArea + previewH / 2 - line() / 2, COLOR_TEXT_MUTED);
        }

        // Drag hint
        boolean hovering = mouseX >= panelLeft + innerPadding && mouseX < panelLeft + panelW - innerPadding &&
                mouseY >= previewArea && mouseY < previewArea + previewH;
        if (hovering && !isDraggingPreview) {
            UIScaleManager.drawScaledString(graphics, f, "[Drag to rotate]", panelLeft + s(8), previewArea + s(4), COLOR_HINT_TEXT, false);
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
        int panelWidth = screen.getPanelWidth();
        int previewWaveNumber = screen.getPreviewWaveNumber();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
        QuestType questType = screen.getQuestType();

        int barY = screen.getWaveBarY();
        int barX = panelX + s(15);
        int barW = panelWidth - s(30);
        net.minecraft.client.gui.Font f = getFont();

        // Separator
        graphics.fill(barX, barY - s(5), barX + barW, barY - s(3), COLOR_PANEL_DIVIDER);

        // Wave slider section
        UIScaleManager.drawScaledString(graphics, f, "> WAVE PREVIEW", barX, barY, COLOR_TEXT_MUTED, false);

        // Slider
        int sliderX = barX + s(100);
        int sliderW = s(150);
        int sliderY = barY;
        int sliderH = s(8);

        graphics.fill(sliderX, sliderY + s(2), sliderX + sliderW, sliderY + s(10), COLOR_TAB_HOVER);
        AxiomRenderer.drawBorder(graphics, sliderX, sliderY + s(2), sliderW, sliderH, COLOR_PANEL_DIVIDER);

        float progress = (previewWaveNumber - 1) / (float) (MAX_PREVIEW_WAVE - 1);
        int fillW = (int) (sliderW * progress);
        graphics.fill(sliderX + 1, sliderY + s(3), sliderX + fillW, sliderY + s(9), COLOR_ACCENT);

        UIScaleManager.drawScaledString(graphics, f, "Wave " + previewWaveNumber, sliderX + sliderW + s(10), sliderY, COLOR_TEXT_PRIMARY, false);

        // Stats row with segmented display
        int statsY = barY + line() + s(8);
        int playerCount = Math.max(1, members.size());

        int mobCount = config.getMobCountForWave(previewWaveNumber, playerCount, questType);
        float scaledHP = config.getScaledHealth(playerCount, questType);
        float scaledDMG = config.getScaledDamage(playerCount);
        float waveMultiplier = 1.0f + (previewWaveNumber - 1) * 0.05f;

        // Stat segment data: label, value, color
        String[] labels = {"Mobs", "HP", "DMG", "Points", "Diff"};
        String[] values = {
            String.valueOf(mobCount),
            String.format("%.0f", scaledHP * waveMultiplier),
            String.format("%.0f", scaledDMG * waveMultiplier),
            String.valueOf(mobCount * config.getPointsPerKill()),
            String.format("%.1fx", questType.getDifficultyMultiplier() * waveMultiplier)
        };
        int[] colors = {COLOR_TEXT_PRIMARY, COLOR_STAT_HP, COLOR_STAT_DMG, COLOR_STAT_POINTS, COLOR_STAT_DIFFICULTY};

        // Calculate segment layout - use 5 columns if wide, 3x2 if narrow
        boolean useWideLayout = barW >= s(350);
        int segmentCount = useWideLayout ? 5 : 3;
        int segmentGap = s(4);
        int segmentW = (barW - segmentGap * (segmentCount - 1)) / segmentCount;
        int segmentH = line() * 2 + s(6);

        // Render segments
        int rows = useWideLayout ? 1 : 2;
        for (int row = 0; row < rows; row++) {
            int startIdx = row * segmentCount;
            int endIdx = Math.min(startIdx + segmentCount, labels.length);
            int segY = statsY + row * (segmentH + segmentGap);

            for (int i = startIdx; i < endIdx; i++) {
                int col = i - startIdx;
                int segX = barX + col * (segmentW + segmentGap);

                // Segment background
                graphics.fill(segX, segY, segX + segmentW, segY + segmentH, COLOR_ROW_DEFAULT);

                // Top accent line with stat color
                graphics.fill(segX, segY, segX + segmentW, segY + s(2), colors[i]);

                // Label (centered, top)
                UIScaleManager.drawScaledCenteredString(graphics, f, labels[i], segX + segmentW / 2, segY + s(3), COLOR_TEXT_MUTED);

                // Value (centered, bottom, colored)
                UIScaleManager.drawScaledCenteredString(graphics, f, values[i], segX + segmentW / 2, segY + line() + s(4), colors[i]);
            }
        }
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
        int maxWidth = panelWidth - s(150);
        UIScaleManager.drawScaledString(graphics, f, fitToWidth(f, line, maxWidth), panelX + s(15), kitY, COLOR_TEXT_MUTED, false);
    }

    private void renderRunFooter(GuiGraphics graphics) {
        int panelX = screen.getPanelX();
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

        int barY = screen.getWaveBarY();
        int barX = panelX + s(15);
        int barW = panelWidth - s(30);
        net.minecraft.client.gui.Font f = getFont();

        graphics.fill(barX, barY - s(5), barX + barW, barY - s(3), COLOR_PANEL_DIVIDER);

        String statusLabel = atCheckpoint ? "CHECKPOINT READY" : "RUN ACTIVE";
        UIScaleManager.drawScaledString(graphics, f, statusLabel, barX, barY, COLOR_TEXT_MUTED, false);

        String waveLabel = questData.endlessMode()
            ? "Wave " + questData.currentWave() + "/INF"
            : "Wave " + questData.currentWave() + "/" + questData.totalWaves();
        int waveWidth = f.width(waveLabel);
        int waveX = barX + barW - waveWidth;
        UIScaleManager.drawScaledString(graphics, f, waveLabel, waveX, barY, COLOR_TEXT_MUTED, false);
        if (syncStale) {
            String syncLabel = "SYNC " + syncSeconds + "s";
            int syncWidth = f.width(syncLabel);
            int syncX = Math.max(barX, waveX - syncWidth - s(8));
            UIScaleManager.drawScaledString(graphics, f, syncLabel, syncX, barY, COLOR_NOT_READY, false);
        }

        int infoY = barY + line() + lineGap();
        String readyLabel;
        int readyColor;
        if (atCheckpoint) {
            readyLabel = "Checkpoint " + readyCount + "/" + gateTotal;
            readyColor = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
        } else {
            readyLabel = "In wave";
            readyColor = COLOR_TEXT_PRIMARY;
        }
        UIScaleManager.drawScaledString(graphics, f, readyLabel, barX, infoY, readyColor, false);

        String actionLabel;
        int actionColor = COLOR_TEXT_MUTED;
        if (screen.isLocalSpectator()) {
            actionLabel = "NEXT: READY = REJOIN";
            actionColor = COLOR_ACCENT;
        } else if (atCheckpoint) {
            actionLabel = screen.isLocalReady() ? "NEXT: Waiting party" : "NEXT: READY = CONTINUE";
            actionColor = readyCount >= gateTotal ? COLOR_READY : COLOR_NOT_READY;
        } else {
            actionLabel = "NEXT: Fighting";
        }
        UIScaleManager.drawScaledString(graphics, f, actionLabel, barX + s(140), infoY, actionColor, false);

        if (atCheckpoint) {
            int progressY = infoY + line() + lineGap();
            int progressW = s(140);
            int progressH = s(4);
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
        int centerX = panelX + panelWidth / 2;
        int centerY = panelY + panelHeight / 2;
        net.minecraft.client.gui.Font f = getFont();

        String noParty = "NO ACTIVE PARTY";
        int yOffset70 = s(70);
        UIScaleManager.drawScaledCenteredString(graphics, f, noParty, centerX, centerY - yOffset70, COLOR_TEXT_PRIMARY);

        UIScaleManager.drawScaledCenteredString(graphics, f, "Click CREATE PARTY to start a new group,", centerX, centerY - s(45), COLOR_TEXT_PRIMARY);
        UIScaleManager.drawScaledCenteredString(graphics, f, "then invite other players to join you.", centerX, centerY - s(30), COLOR_TEXT_SECONDARY);

        int lineW = s(140);
        int lineCenter = s(40);
        int lineY = centerY - s(15);
        graphics.fill(centerX - lineW, lineY, centerX + lineW, lineY + 1, COLOR_PANEL_DIVIDER);
        graphics.fill(centerX - lineCenter, lineY, centerX + lineCenter, lineY + 1, COLOR_ACCENT);

        UIScaleManager.drawScaledCenteredString(graphics, f, "- OR -", centerX, centerY, COLOR_TEXT_MUTED);
        UIScaleManager.drawScaledCenteredString(graphics, f, "Wait for another player to invite you.", centerX, centerY + s(15), COLOR_TEXT_SECONDARY);
        UIScaleManager.drawScaledCenteredString(graphics, f, "Invites will appear as a popup notification.", centerX, centerY + s(30), COLOR_TEXT_MUTED);
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

    /**
     * Blend two colors by a ratio (0.0 = color1, 1.0 = color2).
     * Used for smooth hover transitions.
     */
    private int blendColors(int color1, int color2, float ratio) {
        ratio = Mth.clamp(ratio, 0f, 1f);
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
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
