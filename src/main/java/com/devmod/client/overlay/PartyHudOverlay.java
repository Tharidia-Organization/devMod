package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.party.ClientPartyCache;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.party.PartyData;
import com.devmod.party.PartySyncPayload;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class PartyHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "party_hud");

    // === Colors (using DesignTokens for consistency) ===
    private static final int PANEL_BG = DesignTokens.Bg.LEVEL_1;              // Standard 0xE0 alpha
    private static final int PANEL_BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int TEXT_PRIMARY = DesignTokens.Text.PRIMARY;
    private static final int TEXT_SECONDARY = DesignTokens.Text.SECONDARY;
    private static final int COLOR_LEADER = DesignTokens.Semantic.WARNING;     // Gold for leader
    private static final int COLOR_OFFLINE = DesignTokens.Text.MUTED;

    private static final int STATUS_BAR_BG = DesignTokens.Surface.LEVEL_0;
    private static final int STATUS_READY = DesignTokens.Semantic.SUCCESS;
    private static final int STATUS_WAITING = DesignTokens.Semantic.WARNING;
    private static final int STATUS_OFFLINE = DesignTokens.Semantic.ERROR;

    // === Dimensions (using DesignTokens grid) ===
    private static final int PANEL_WIDTH = 120;
    private static final int MEMBER_HEIGHT = DesignTokens.Component.ROW_HEIGHT_COMPACT; // 24px
    private static final int STATUS_BAR_HEIGHT = 3;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 12;
    private static final int MAX_VISIBLE_MEMBERS = 8;
    private static final int MARGIN_LEFT = 10;
    private static final int MARGIN_TOP = 300; // Below EnduranceQuestOverlay

    // === Toggle ===
    private static boolean enabled = true;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.BOSS_OVERLAY),
            Objects.requireNonNull(LAYER_ID),
            PartyHudOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled) return;

        // Only show when in a party
        if (!ClientPartyCache.isInParty()) return;

        // Only show during active quest (check if in IN_QUEST state)
        PartyData.PartyState state = ClientPartyCache.getPartyState();
        if (state != PartyData.PartyState.IN_QUEST) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        List<PartySyncPayload.PartyMemberInfo> members = ClientPartyCache.getMembers();
        if (members.isEmpty()) return;

        Font font = mc.font;

        int totalMembers = members.size();
        int visibleCount = Math.min(totalMembers, MAX_VISIBLE_MEMBERS);
        boolean hasOverflow = totalMembers > MAX_VISIBLE_MEMBERS;

        // Calculate panel size
        int panelHeight = PADDING + HEADER_HEIGHT + visibleCount * MEMBER_HEIGHT + PADDING;
        if (hasOverflow) {
            panelHeight += MEMBER_HEIGHT;
        }

        int x = MARGIN_LEFT;
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int maxY = screenHeight - panelHeight - 10;
        int y = Math.min(MARGIN_TOP, maxY);
        y = Math.max(10, y);

        // Panel background
        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, PANEL_BG);
        graphics.renderOutline(x, y, PANEL_WIDTH, panelHeight, PANEL_BORDER);

        // Header
        String header = "Party (" + members.size() + ")";
        var safeFont = Objects.requireNonNull(font);
        graphics.drawString(safeFont, header, x + PADDING, y + PADDING - 2, TEXT_SECONDARY);

        // Member list
        int memberY = y + PADDING + HEADER_HEIGHT;
        for (int i = 0; i < visibleCount; i++) {
            PartySyncPayload.PartyMemberInfo member = members.get(i);
            renderMember(graphics, font, member, x + PADDING, memberY);
            memberY += MEMBER_HEIGHT;
        }

        if (hasOverflow) {
            String moreText = "+" + (totalMembers - visibleCount) + " more";
            graphics.drawString(safeFont, moreText, x + PADDING, memberY + 4, TEXT_SECONDARY);
        }
    }

    private static void renderMember(GuiGraphics graphics, Font font,
                                      PartySyncPayload.PartyMemberInfo member,
                                      int x, int y) {
        // Status indicator (colored dot)
        int statusColor = getStatusColor(member);
        graphics.fill(x, y + 2, x + 4, y + 6, statusColor);

        // Leader star
        String prefix = member.isLeader() ? "\u2605 " : "";
        int nameColor = member.isLeader() ? COLOR_LEADER :
                       (member.isOnline() ? TEXT_PRIMARY : COLOR_OFFLINE);

        // Player name (truncated if too long)
        String name = prefix + truncateName(member.playerName(), 10);
        graphics.drawString(Objects.requireNonNull(font), name, x + 8, y, nameColor);

        // Status label
        String statusLabel = getStatusLabel(member);
        int statusWidth = font.width(statusLabel);
        int statusX = x + (PANEL_WIDTH - PADDING * 2) - statusWidth;
        graphics.drawString(Objects.requireNonNull(font), statusLabel, statusX, y, statusColor);

        // Status bar (represents readiness/offline state)
        int barX = x;
        int barY = y + 10;
        int barWidth = PANEL_WIDTH - PADDING * 2 - 8;

        graphics.fill(barX, barY, barX + barWidth, barY + STATUS_BAR_HEIGHT, STATUS_BAR_BG);

        if (member.isOnline()) {
            int fillWidth = barWidth;
            int fillColor = STATUS_READY;
            if (!member.isReady()) {
                fillWidth = barWidth / 3;
                fillColor = STATUS_WAITING;
            }
            graphics.fill(barX, barY, barX + fillWidth, barY + STATUS_BAR_HEIGHT, fillColor);
        } else {
            // Offline members: short red bar to signal unavailable
            int fillWidth = barWidth / 4;
            graphics.fill(barX, barY, barX + fillWidth, barY + STATUS_BAR_HEIGHT, STATUS_OFFLINE);
        }
    }

    private static int getStatusColor(PartySyncPayload.PartyMemberInfo member) {
        if (!member.isOnline()) return STATUS_OFFLINE;
        if (member.isReady()) return STATUS_READY;
        return STATUS_WAITING;
    }

    private static String getStatusLabel(PartySyncPayload.PartyMemberInfo member) {
        if (!member.isOnline()) return "OFF";
        if (member.isReady()) return "RDY";
        return "WAIT";
    }

    private static String truncateName(String name, int maxLen) {
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 2) + "..";
    }

    // === Public API ===

    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }
}
