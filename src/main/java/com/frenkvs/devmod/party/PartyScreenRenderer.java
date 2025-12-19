package com.frenkvs.devmod.party;

import com.frenkvs.devmod.endurance.EnduranceQuestRegistry;
import com.frenkvs.devmod.endurance.EnduranceQuestRegistry.MobDifficultyPreset;
import com.frenkvs.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.frenkvs.devmod.endurance.QuestType;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles all rendering operations for PartyScreen.
 * Extracted for single responsibility.
 */
public class PartyScreenRenderer {

    // Colors
    private static final int COLOR_BG_DARK = 0xF0080810;
    private static final int COLOR_HEADER_GRADIENT_TOP = 0xFF1A1A35;
    private static final int COLOR_HEADER_GRADIENT_BOT = 0xFF0D0D1A;
    private static final int COLOR_GLOW_BLUE = 0xFF3D5AFE;
    private static final int COLOR_GLOW_CYAN = 0xFF00FFFF;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAABBCC;
    private static final int COLOR_TEXT_DIM = 0xFF667788;
    private static final int COLOR_READY = 0xFF00FF88;
    private static final int COLOR_NOT_READY = 0xFFFF6677;
    private static final int COLOR_LEADER_GOLD = 0xFFFFD700;

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
        int glowColor = (glowAlpha << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF);
        graphics.fill(panelX - 4, panelY - 4, panelX + panelWidth + 4, panelY + panelHeight + 4, glowColor);
        graphics.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, glowColor);

        // Main background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_BG_DARK);

        // Header gradient
        for (int i = 0; i < 40; i++) {
            float t = i / 40f;
            int color = UIConstants.lerp(COLOR_HEADER_GRADIENT_TOP, COLOR_HEADER_GRADIENT_BOT, t);
            graphics.fill(panelX, panelY + i, panelX + panelWidth, panelY + i + 1, color);
        }

        // Animated border
        int borderAlpha = (int) (180 + glowPulse * 75);
        int borderColor = (borderAlpha << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF);
        drawAnimatedBorder(graphics, panelX, panelY, panelWidth, panelHeight, borderColor);

        // Title with glow
        String title = "PARTY MANAGEMENT";
        int titleX = panelX + panelWidth / 2 - getFont().width(title) / 2;

        // Title glow
        int titleGlowAlpha = (int) (titleGlow * 100);
        net.minecraft.client.gui.Font f = getFont();
        graphics.drawString(f, title, titleX - 1, panelY + 14, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);
        graphics.drawString(f, title, titleX + 1, panelY + 14, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);
        graphics.drawString(f, title, titleX, panelY + 13, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);
        graphics.drawString(f, title, titleX, panelY + 15, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);

        // Main title
        graphics.drawString(f, title, titleX, panelY + 14, COLOR_GLOW_CYAN, false);

        // Subtitle
        if (screen.isInParty()) {
            PartyData.PartyState partyState = screen.getPartyState();
            QuestType questType = screen.getQuestType();
            List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
            String subtitle = partyState.name() + " - " + members.size() + "/" + questType.maxPlayers + " Warriors";
            int subX = panelX + panelWidth / 2 - f.width(subtitle) / 2;
            graphics.drawString(f, subtitle, subX, panelY + 28, COLOR_TEXT_DIM, false);
        }
    }

    public void drawAnimatedBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        // Top
        graphics.fill(x, y, x + w, y + 2, color);
        // Bottom
        graphics.fill(x, y + h - 2, x + w, y + h, color);
        // Left
        graphics.fill(x, y, x + 2, y + h, color);
        // Right
        graphics.fill(x + w - 2, y, x + w, y + h, color);

        // Corner accents
        int cornerSize = 8;
        // Top-left
        graphics.fill(x, y, x + cornerSize, y + 3, COLOR_GLOW_CYAN);
        graphics.fill(x, y, x + 3, y + cornerSize, COLOR_GLOW_CYAN);
        // Top-right
        graphics.fill(x + w - cornerSize, y, x + w, y + 3, COLOR_GLOW_CYAN);
        graphics.fill(x + w - 3, y, x + w, y + cornerSize, COLOR_GLOW_CYAN);
        // Bottom-left
        graphics.fill(x, y + h - 3, x + cornerSize, y + h, COLOR_GLOW_CYAN);
        graphics.fill(x, y + h - cornerSize, x + 3, y + h, COLOR_GLOW_CYAN);
        // Bottom-right
        graphics.fill(x + w - cornerSize, y + h - 3, x + w, y + h, COLOR_GLOW_CYAN);
        graphics.fill(x + w - 3, y + h - cornerSize, x + w, y + h, COLOR_GLOW_CYAN);
    }

    public int renderQuestTypeTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        int panelWidth = screen.getPanelWidth();
        float glowPulse = screen.getGlowPulse();
        QuestType questType = screen.getQuestType();

        int tabY = panelY + 45;
        int tabWidth = (panelWidth - 40) / 3;
        int tabHeight = 28;
        net.minecraft.client.gui.Font f = getFont();

        int hoveredQuestTab = -1;

        for (int i = 0; i < QuestType.values().length; i++) {
            QuestType type = QuestType.values()[i];
            int tabX = panelX + 20 + i * tabWidth;

            boolean isActive = type == questType;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth - 4 &&
                    mouseY >= tabY && mouseY < tabY + tabHeight;

            if (isHovered) hoveredQuestTab = i;

            // Tab background
            int bgColor = isActive ? 0xFF1A2A4A : (isHovered ? 0xFF151525 : 0xFF0A0A15);
            graphics.fill(tabX, tabY, tabX + tabWidth - 4, tabY + tabHeight, bgColor);

            // Active indicator
            if (isActive) {
                int glowIntensity = (int) (150 + glowPulse * 50);
                graphics.fill(tabX, tabY + tabHeight - 3, tabX + tabWidth - 4, tabY + tabHeight,
                    (glowIntensity << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF));
                graphics.fill(tabX, tabY + tabHeight - 2, tabX + tabWidth - 4, tabY + tabHeight, COLOR_GLOW_BLUE);
            }

            // Border
            int borderColor = isActive ? COLOR_GLOW_BLUE : (isHovered ? 0xFF3D5AFE : 0xFF2A2A4A);
            AxiomRenderer.drawBorder(graphics, tabX, tabY, tabWidth - 4, tabHeight, borderColor);

            // Icon + Name
            String icon = getQuestTypeIcon(type);
            String name = type.displayName;
            int textColor = isActive ? COLOR_GLOW_CYAN : (isHovered ? COLOR_TEXT_WHITE : COLOR_TEXT_GRAY);

            graphics.drawCenteredString(f, icon + " " + name, tabX + (tabWidth - 4) / 2, tabY + 6, textColor);

            // Player range
            String range = type.minPlayers + "-" + type.maxPlayers + " players";
            graphics.drawCenteredString(f, range, tabX + (tabWidth - 4) / 2, tabY + 17, COLOR_TEXT_DIM);
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

        int panelLeft = panelX + 15;
        int panelTop = panelY + 80;
        int panelW = 200;
        int panelH = 200;
        net.minecraft.client.gui.Font f = getFont();

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, 0xCC0A0A18);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, 0xFF2A3A5A);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + 22, 0xFF151528);
        graphics.drawString(f, "PARTY MEMBERS", panelLeft + 8, panelTop + 7, COLOR_GLOW_CYAN, false);

        int hoveredMemberIndex = -1;
        int memberY = panelTop + 28;

        for (int i = 0; i < Math.min(members.size(), 6); i++) {
            PartySyncPayload.PartyMemberInfo member = members.get(i);
            float anim = memberAnimations[i];

            int rowY = memberY + i * 28;
            int rowH = 26;

            // Slide-in animation
            int offsetX = (int) ((1f - anim) * -50);
            int rowX = panelLeft + 5 + offsetX;
            int rowW = panelW - 10;

            boolean isHovered = mouseX >= panelLeft + 5 && mouseX < panelLeft + panelW - 5 &&
                    mouseY >= rowY && mouseY < rowY + rowH;
            if (isHovered) hoveredMemberIndex = i;

            // Row background
            int rowBg = isHovered ? 0x40FFFFFF : 0x20FFFFFF;
            graphics.fill(rowX, rowY, rowX + rowW, rowY + rowH, (int)(rowBg * anim));

            boolean isMemberLeader = member.playerId().equals(leaderId);

            // Ready/status indicator with glow
            int statusX = rowX + 5;
            int statusColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            int statusGlow = member.isReady() ? 0x4000FF88 : 0x40FF4466;

            // Status glow
            graphics.fill(statusX - 2, rowY + 5, statusX + 10, rowY + rowH - 5, statusGlow);
            graphics.fill(statusX, rowY + 7, statusX + 8, rowY + rowH - 7, statusColor);

            // Leader crown or player icon
            String prefix = isMemberLeader ? "[L] " : "    ";
            int nameColor = isMemberLeader ? COLOR_LEADER_GOLD : COLOR_TEXT_WHITE;

            // Player name
            String displayName = member.playerName();
            Minecraft mc = Minecraft.getInstance();
            var localPlayer = mc.player;
            if (localPlayer != null && member.playerId().equals(localPlayer.getUUID())) {
                displayName += " (You)";
            }
            graphics.drawString(f, prefix + displayName, rowX + 18, rowY + 5, nameColor, false);

            // Status text
            String status = member.isReady() ? "Ready" : "Waiting";
            int statusTextColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            graphics.drawString(f, status, rowX + 18, rowY + 15, statusTextColor, false);

            // Kick hint on hover (for leader)
            if (isHovered && isLeader && !member.playerId().equals(leaderId)) {
                graphics.drawString(f, "[Right-click: Kick]", rowX + rowW - 85, rowY + 10, COLOR_TEXT_DIM, false);
            }
        }

        // Empty state
        if (members.isEmpty()) {
            graphics.drawCenteredString(f, "No warriors yet...", panelLeft + panelW / 2, panelTop + 80, COLOR_TEXT_DIM);
            graphics.drawCenteredString(f, "Invite players below", panelLeft + panelW / 2, panelTop + 95, COLOR_TEXT_DIM);
        }

        // Invite section header
        int inviteSectionY = panelTop + panelH + 8;
        graphics.drawString(f, "> INVITE PLAYER", panelLeft + 5, inviteSectionY, COLOR_TEXT_DIM, false);

        // Input background
        graphics.fill(panelLeft + 5, inviteSectionY + 12, panelLeft + 145, inviteSectionY + 32, 0xFF0A0A15);
        AxiomRenderer.drawBorder(graphics, panelLeft + 5, inviteSectionY + 12, 140, 20, 0xFF2A3A5A);

        return hoveredMemberIndex;
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

        int panelLeft = panelX + 225;
        int panelTop = panelY + 80;
        int panelW = 160;
        int panelH = 230;
        net.minecraft.client.gui.Font f = getFont();

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, 0xCC0A0A18);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, 0xFF2A3A5A);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + 22, 0xFF151528);
        graphics.drawString(f, "SELECT ENEMY", panelLeft + 8, panelTop + 7, COLOR_GLOW_CYAN, false);

        // Search background
        int searchY = panelTop + 28;
        graphics.fill(panelLeft + 5, searchY, panelLeft + panelW - 5, searchY + 20, 0xFF0A0A15);
        AxiomRenderer.drawBorder(graphics, panelLeft + 5, searchY, panelW - 10, 20, 0xFF2A3A5A);

        // Namespace filter buttons (All / MC / Mods)
        int nsFilterY = searchY + 25;
        int nsBtnX = panelLeft + 5;

        // "All" button
        boolean allActive = selectedNamespace == null;
        int allW = 28;
        int allColor = allActive ? 0xFF3D5AFE : 0xFF1A1A2A;
        boolean allHovered = mouseX >= nsBtnX && mouseX < nsBtnX + allW &&
                            mouseY >= nsFilterY && mouseY < nsFilterY + 12;
        if (allHovered) allColor = UIConstants.lighten(allColor, 0.2f);
        graphics.fill(nsBtnX, nsFilterY, nsBtnX + allW, nsFilterY + 12, allColor);
        AxiomRenderer.drawBorder(graphics, nsBtnX, nsFilterY, allW, 12, allActive ? 0xFF3D5AFE : 0xFF3A3A5A);
        graphics.drawCenteredString(f, "All", nsBtnX + allW / 2, nsFilterY + 2, COLOR_TEXT_WHITE);
        nsBtnX += allW + 2;

        // "MC" button
        boolean mcActive = "minecraft".equals(selectedNamespace);
        int mcW = 24;
        int mcColor = mcActive ? 0xFF44AA44 : 0xFF1A1A2A;
        boolean mcHovered = mouseX >= nsBtnX && mouseX < nsBtnX + mcW &&
                           mouseY >= nsFilterY && mouseY < nsFilterY + 12;
        if (mcHovered) mcColor = UIConstants.lighten(mcColor, 0.2f);
        graphics.fill(nsBtnX, nsFilterY, nsBtnX + mcW, nsFilterY + 12, mcColor);
        AxiomRenderer.drawBorder(graphics, nsBtnX, nsFilterY, mcW, 12, mcActive ? 0xFF44AA44 : 0xFF3A3A5A);
        graphics.drawCenteredString(f, "MC", nsBtnX + mcW / 2, nsFilterY + 2, COLOR_TEXT_WHITE);
        nsBtnX += mcW + 2;

        // Mod count
        long modCount = screen.getAvailableNamespaces().stream().filter(ns -> !"minecraft".equals(ns)).count();
        if (modCount > 0) {
            graphics.drawString(f, "+" + modCount, nsBtnX + 2, nsFilterY + 2, COLOR_TEXT_DIM, false);
        }

        // Tier filter buttons
        int filterY = nsFilterY + 16;
        int btnW = 22;
        int btnX = panelLeft + 5;

        for (MobTier tier : MobTier.values()) {
            boolean active = tier == selectedTierFilter;
            int color = active ? getTierColor(tier) : 0xFF1A1A2A;
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW &&
                             mouseY >= filterY && mouseY < filterY + 14;

            if (hovered) color = UIConstants.lighten(color, 0.2f);

            graphics.fill(btnX, filterY, btnX + btnW, filterY + 14, color);
            AxiomRenderer.drawBorder(graphics, btnX, filterY, btnW, 14,
                active ? getTierColor(tier) : 0xFF3A3A5A);

            String initial = Objects.requireNonNull(tier.name().substring(0, 1));
            graphics.drawCenteredString(f, initial, btnX + btnW / 2, filterY + 3, COLOR_TEXT_WHITE);

            btnX += btnW + 2;
        }

        // Mob list
        int listY = filterY + 18;
        int hoveredMobIndex = -1;

        for (int i = 0; i < MAX_VISIBLE_MOBS; i++) {
            int mobIndex = mobListScrollOffset + i;
            if (mobIndex >= filteredMobs.size()) break;

            EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(mobIndex);
            int rowY = listY + i * 26;
            int rowH = 24;

            boolean isSelected = mobIndex == selectedMobIndex;
            boolean isHovered = mouseX >= panelLeft + 5 && mouseX < panelLeft + panelW - 5 &&
                    mouseY >= rowY && mouseY < rowY + rowH;

            if (isHovered) hoveredMobIndex = mobIndex;

            // Row background
            if (isSelected) {
                int selectGlow = (int) (100 + glowPulse * 50);
                graphics.fill(panelLeft + 5, rowY, panelLeft + panelW - 5, rowY + rowH,
                    (selectGlow << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF));
                graphics.fill(panelLeft + 6, rowY + 1, panelLeft + panelW - 6, rowY + rowH - 1, 0xFF1A2A4A);
            } else if (isHovered) {
                graphics.fill(panelLeft + 5, rowY, panelLeft + panelW - 5, rowY + rowH, 0x30FFFFFF);
            }

            // Tier color bar
            int tierColor = getTierColor(config.tier);
            graphics.fill(panelLeft + 5, rowY + 2, panelLeft + 9, rowY + rowH - 2, tierColor);

            // Mob name
            String name = Objects.requireNonNull(config.displayName);
            if (f.width(name) > panelW - 40) {
                name = Objects.requireNonNull(f.plainSubstrByWidth(name, panelW - 45)) + "..";
            }
            int nameColor = isSelected ? COLOR_GLOW_CYAN : COLOR_TEXT_WHITE;
            graphics.drawString(f, name, panelLeft + 14, rowY + 4, nameColor, false);

            // Difficulty preset
            String preset = getPresetSymbol(config.difficultyPreset);
            graphics.drawString(f, preset, panelLeft + 14, rowY + 14, COLOR_TEXT_DIM, false);
        }

        // Scroll indicators
        if (mobListScrollOffset > 0) {
            graphics.drawCenteredString(f, "^", panelLeft + panelW / 2, listY - 8, COLOR_GLOW_BLUE);
        }
        if (mobListScrollOffset + MAX_VISIBLE_MOBS < filteredMobs.size()) {
            graphics.drawCenteredString(f, "v", panelLeft + panelW / 2, listY + MAX_VISIBLE_MOBS * 26 + 2, COLOR_GLOW_BLUE);
        }

        // Count
        graphics.drawString(f, filteredMobs.size() + " enemies", panelLeft + 8, panelTop + panelH - 14, COLOR_TEXT_DIM, false);

        return hoveredMobIndex;
    }

    public void renderMobPreviewPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        float glowPulse = screen.getGlowPulse();
        List<EnduranceQuestRegistry.MobQuestConfig> filteredMobs = screen.getFilteredMobs();
        int selectedMobIndex = screen.getSelectedMobIndex();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
        QuestType questType = screen.getQuestType();
        LivingEntity previewEntity = screen.getPreviewEntity();
        boolean isDraggingPreview = screen.isDraggingPreview();

        int panelLeft = panelX + 395;
        int panelTop = panelY + 80;
        int panelW = 190;
        int panelH = 230;
        net.minecraft.client.gui.Font f = getFont();

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, 0xCC0A0A18);

        // Inner preview area
        int previewArea = panelTop + 22;
        graphics.fill(panelLeft + 5, previewArea, panelLeft + panelW - 5, previewArea + 120, 0xFF050510);

        // Platform
        int centerX = panelLeft + panelW / 2;
        int platformY = previewArea + 110;
        int platformRadius = 40;

        // Platform glow
        for (int r = platformRadius; r > platformRadius - 5; r--) {
            int alpha = (int) ((1 - (platformRadius - r) / 5f) * (50 + glowPulse * 30));
            int glowC = (alpha << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF);
            graphics.fill(centerX - r, platformY - 3, centerX + r, platformY, glowC);
        }

        graphics.fill(centerX - platformRadius + 5, platformY - 1, centerX + platformRadius - 5, platformY, COLOR_GLOW_BLUE);

        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, 0xFF2A3A5A);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + 22, 0xFF151528);
        graphics.drawString(f, "PREVIEW", panelLeft + 8, panelTop + 7, COLOR_GLOW_CYAN, false);

        // Smooth rotation
        float mobRotationY = screen.getMobRotationY();
        float targetMobRotationY = screen.getTargetMobRotationY();
        float mobRotationX = screen.getMobRotationX();
        screen.setMobRotationY(Mth.lerp(0.15f, mobRotationY, targetMobRotationY));

        if (previewEntity != null && !filteredMobs.isEmpty() && selectedMobIndex < filteredMobs.size()) {
            int entityCenterY = previewArea + 85;

            float mobHeight = previewEntity.getBbHeight();
            float mobWidth = previewEntity.getBbWidth();
            float maxDim = Math.max(mobHeight, mobWidth);
            int scale = (int) Math.min(40, 80 / maxDim);

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
                graphics.drawCenteredString(f, "[Preview Error]", centerX, entityCenterY - 20, COLOR_TEXT_DIM);
            }

            EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(selectedMobIndex);

            // Stats section
            int statsY = previewArea + 125;

            graphics.drawCenteredString(f, Objects.requireNonNull(config.displayName), centerX, statsY, getTierColor(config.tier));

            String tierBadge = Objects.requireNonNull(getTierBadge(config.tier));
            graphics.drawCenteredString(f, tierBadge, centerX, statsY + 12, getTierColor(config.tier));

            // Stats grid
            int statY = statsY + 28;
            int col1 = panelLeft + 15;
            int col2 = panelLeft + panelW / 2 + 5;

            graphics.drawString(f, "HP", col1, statY, 0xFFFF6666, false);
            graphics.drawString(f, String.format("%.0f", config.baseHealth), col1 + 25, statY, COLOR_TEXT_WHITE, false);

            graphics.drawString(f, "DMG", col2, statY, 0xFFFFAA00, false);
            graphics.drawString(f, String.format("%.0f", config.baseDamage), col2 + 30, statY, COLOR_TEXT_WHITE, false);

            statY += 14;
            int playerCount = Math.max(1, members.size());
            float scaledHP = config.getScaledHealth(playerCount, questType);
            float scaledDMG = config.getScaledDamage(playerCount);

            graphics.drawString(f, "Scaled", col1, statY, COLOR_TEXT_DIM, false);
            graphics.drawString(f, String.format("%.0f", scaledHP), col1 + 40, statY, COLOR_READY, false);
            graphics.drawString(f, String.format("%.0f", scaledDMG), col2 + 30, statY, 0xFFFFFF00, false);

            statY += 14;
            graphics.drawString(f, "Points/Kill: " + config.pointsPerKill, col1, statY, COLOR_TEXT_GRAY, false);

        } else {
            graphics.drawCenteredString(f, "Select an enemy", centerX, previewArea + 60, COLOR_TEXT_DIM);
        }

        // Drag hint
        boolean hovering = mouseX >= panelLeft + 5 && mouseX < panelLeft + panelW - 5 &&
                mouseY >= previewArea && mouseY < previewArea + 120;
        if (hovering && !isDraggingPreview) {
            graphics.drawString(f, "[Drag to rotate]", panelLeft + 8, previewArea + 4, 0x60FFFFFF, false);
        }
    }

    public void renderWaveStatsBar(GuiGraphics graphics, int mouseX, int mouseY) {
        List<EnduranceQuestRegistry.MobQuestConfig> filteredMobs = screen.getFilteredMobs();
        int selectedMobIndex = screen.getSelectedMobIndex();
        if (filteredMobs.isEmpty() || selectedMobIndex >= filteredMobs.size()) return;

        int panelX = screen.getPanelX();
        int panelY = screen.getPanelY();
        int panelHeight = screen.getPanelHeight();
        int panelWidth = screen.getPanelWidth();
        int previewWaveNumber = screen.getPreviewWaveNumber();
        List<PartySyncPayload.PartyMemberInfo> members = screen.getMembers();
        QuestType questType = screen.getQuestType();

        int barY = panelY + panelHeight - 85;
        int barX = panelX + 15;
        int barW = panelWidth - 30;
        net.minecraft.client.gui.Font f = getFont();

        // Separator
        graphics.fill(barX, barY - 5, barX + barW, barY - 3, 0xFF2A3A5A);

        // Wave slider section
        graphics.drawString(f, "> WAVE PREVIEW", barX, barY, COLOR_TEXT_DIM, false);

        // Slider
        int sliderX = barX + 100;
        int sliderW = 150;
        int sliderY = barY;

        graphics.fill(sliderX, sliderY + 2, sliderX + sliderW, sliderY + 10, 0xFF151525);
        AxiomRenderer.drawBorder(graphics, sliderX, sliderY + 2, sliderW, 8, 0xFF2A3A5A);

        float progress = (previewWaveNumber - 1) / (float) (MAX_PREVIEW_WAVE - 1);
        int fillW = (int) (sliderW * progress);
        graphics.fill(sliderX + 1, sliderY + 3, sliderX + fillW, sliderY + 9, COLOR_GLOW_BLUE);

        graphics.drawString(f, "Wave " + previewWaveNumber, sliderX + sliderW + 10, sliderY, COLOR_TEXT_WHITE, false);

        // Stats row
        int statsY = barY + 16;
        EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(selectedMobIndex);
        int playerCount = Math.max(1, members.size());

        int mobCount = config.getMobCountForWave(previewWaveNumber, playerCount, questType);
        float scaledHP = config.getScaledHealth(playerCount, questType);
        float scaledDMG = config.getScaledDamage(playerCount);
        float waveMultiplier = 1.0f + (previewWaveNumber - 1) * 0.05f;

        int col = barX;
        graphics.drawString(f, "Mobs: " + mobCount, col, statsY, COLOR_TEXT_WHITE, false);
        col += 80;
        graphics.drawString(f, String.format("HP: %.0f", scaledHP * waveMultiplier), col, statsY, 0xFFFF6666, false);
        col += 90;
        graphics.drawString(f, String.format("DMG: %.0f", scaledDMG * waveMultiplier), col, statsY, 0xFFFFAA00, false);
        col += 90;
        graphics.drawString(f, String.format("Points: %d", mobCount * config.pointsPerKill), col, statsY, 0xFFFFFF00, false);
        col += 100;
        graphics.drawString(f, String.format("Difficulty: %.1fx", questType.difficultyMultiplier * waveMultiplier), col, statsY, 0xFFAA66FF, false);
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
        graphics.drawString(f, noParty, centerX - f.width(noParty) / 2 - 1, centerY - 70, (glowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);
        graphics.drawString(f, noParty, centerX - f.width(noParty) / 2 + 1, centerY - 70, (glowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);

        graphics.drawCenteredString(f, noParty, centerX, centerY - 70, COLOR_GLOW_CYAN);

        graphics.drawCenteredString(f, "Click CREATE PARTY to start a new group,", centerX, centerY - 45, COLOR_TEXT_WHITE);
        graphics.drawCenteredString(f, "then invite other players to join you.", centerX, centerY - 30, COLOR_TEXT_GRAY);

        int lineW = 140;
        graphics.fill(centerX - lineW, centerY - 15, centerX + lineW, centerY - 14, 0xFF2A3A5A);
        graphics.fill(centerX - 40, centerY - 15, centerX + 40, centerY - 14, COLOR_GLOW_BLUE);

        graphics.drawCenteredString(f, "- OR -", centerX, centerY, COLOR_TEXT_DIM);
        graphics.drawCenteredString(f, "Wait for another player to invite you.", centerX, centerY + 15, COLOR_TEXT_GRAY);
        graphics.drawCenteredString(f, "Invites will appear as a popup notification.", centerX, centerY + 30, COLOR_TEXT_DIM);
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

    public int getTierColor(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> 0xFF666666;
            case EASY -> 0xFF55FF55;
            case MEDIUM -> 0xFFFFFF55;
            case HARD -> 0xFFFF8800;
            case ELITE -> 0xFFFF5555;
            case BOSS -> 0xFFAA00FF;
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
}
