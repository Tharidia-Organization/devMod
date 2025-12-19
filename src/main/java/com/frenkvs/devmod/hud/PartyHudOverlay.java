package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.party.ClientPartyCache;
import com.frenkvs.devmod.party.PartyData;
import com.frenkvs.devmod.party.PartySyncPayload;
import com.frenkvs.devmod.ui.UIConstants;
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

import java.util.List;

/**
 * HUD Overlay showing party members during Endurance Quest.
 *
 * Displays in the left side of the screen:
 * - Compact list of party members
 * - Mini health bar for each member
 * - Status icon (ready/in combat/dead)
 * - Leader indicator
 *
 * Only visible when in a party during an active quest.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class PartyHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "party_hud");

    // === Colors ===
    private static final int PANEL_BG = 0xCC1A1A2E;           // Dark blue 80% opacity
    private static final int PANEL_BORDER = UIConstants.Border.DEFAULT();
    private static final int TEXT_PRIMARY = UIConstants.Text.PRIMARY();
    private static final int TEXT_SECONDARY = UIConstants.Text.SECONDARY();
    private static final int COLOR_LEADER = UIConstants.Accent.GOLD();
    private static final int COLOR_READY = UIConstants.Accent.GREEN();
    private static final int COLOR_NOT_READY = UIConstants.Accent.RED();
    private static final int COLOR_OFFLINE = UIConstants.Text.DISABLED();

    private static final int HEALTH_BG = 0xFF333333;
    private static final int HEALTH_HIGH = UIConstants.Accent.GREEN();
    private static final int HEALTH_MED = UIConstants.Accent.GOLD();
    private static final int HEALTH_LOW = UIConstants.Accent.RED();

    // === Dimensions ===
    private static final int PANEL_WIDTH = 120;
    private static final int MEMBER_HEIGHT = 20;
    private static final int HEALTH_BAR_HEIGHT = 3;
    private static final int PADDING = 6;
    private static final int MARGIN_LEFT = 10;
    private static final int MARGIN_TOP = 300; // Below EnduranceQuestOverlay

    // === Toggle ===
    private static boolean enabled = true;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.BOSS_OVERLAY,
            LAYER_ID,
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

        // Calculate panel size
        int panelHeight = PADDING * 2 + members.size() * MEMBER_HEIGHT;

        int x = MARGIN_LEFT;
        int y = MARGIN_TOP;

        // Panel background
        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, PANEL_BG);
        graphics.renderOutline(x, y, PANEL_WIDTH, panelHeight, PANEL_BORDER);

        // Header
        String header = "Party (" + members.size() + ")";
        graphics.drawString(font, header, x + PADDING, y + PADDING - 2, TEXT_SECONDARY);

        // Member list
        int memberY = y + PADDING + 12;
        for (PartySyncPayload.PartyMemberInfo member : members) {
            renderMember(graphics, font, member, x + PADDING, memberY);
            memberY += MEMBER_HEIGHT;
        }
    }

    private static void renderMember(GuiGraphics graphics, Font font,
                                      PartySyncPayload.PartyMemberInfo member,
                                      int x, int y) {
        // Status indicator (colored dot)
        int statusColor = getStatusColor(member);
        graphics.fill(x, y + 2, x + 4, y + 6, statusColor);

        // Leader star or ready indicator
        String prefix = member.isLeader() ? "\u2605 " : "";
        int nameColor = member.isLeader() ? COLOR_LEADER :
                       (member.isOnline() ? TEXT_PRIMARY : COLOR_OFFLINE);

        // Player name (truncated if too long)
        String name = prefix + truncateName(member.playerName(), 10);
        graphics.drawString(font, name, x + 8, y, nameColor);

        // Health bar (placeholder - actual health would come from server sync)
        // For now, show ready status as "health"
        int barX = x;
        int barY = y + 10;
        int barWidth = PANEL_WIDTH - PADDING * 2 - 8;

        graphics.fill(barX, barY, barX + barWidth, barY + HEALTH_BAR_HEIGHT, HEALTH_BG);

        if (member.isOnline()) {
            int fillWidth = barWidth;
            int fillColor = HEALTH_HIGH;
            if (!member.isReady()) {
                fillWidth = barWidth / 3;
                fillColor = HEALTH_MED;
            }
            graphics.fill(barX, barY, barX + fillWidth, barY + HEALTH_BAR_HEIGHT, fillColor);
        } else {
            // Offline members: short red bar to signal unavailable
            int fillWidth = barWidth / 4;
            graphics.fill(barX, barY, barX + fillWidth, barY + HEALTH_BAR_HEIGHT, HEALTH_LOW);
        }
    }

    private static int getStatusColor(PartySyncPayload.PartyMemberInfo member) {
        if (!member.isOnline()) return COLOR_OFFLINE;
        if (member.isReady()) return COLOR_READY;
        return COLOR_NOT_READY;
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
