package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.network.ClientTensionCache;
import com.devmod.client.party.ClientPartyCache;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.WaveObjectiveState;
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
    private static final int COLOR_SPECTATOR = DesignTokens.Text.MUTED;

    private static final int STATUS_BAR_BG = DesignTokens.Surface.LEVEL_0;
    private static final int STATUS_READY = DesignTokens.Semantic.SUCCESS;
    private static final int STATUS_WAITING = DesignTokens.Semantic.WARNING;
    private static final int STATUS_OFFLINE = DesignTokens.Semantic.ERROR;

    // === Dimensions (using DesignTokens grid) ===
    private static final int PANEL_WIDTH = 176;
    private static final int MEMBER_HEIGHT = DesignTokens.Component.ROW_HEIGHT_COMPACT; // 24px
    private static final int STATUS_BAR_HEIGHT = 3;
    private static final int PADDING = 6;
    private static final int HEADER_LINE_GAP = 2;
    private static final int HEADER_BAR_HEIGHT = 3;
    private static final int HEADER_BAR_GAP = 2;
    private static final int HEADER_BAR_TOP_GAP = 4;
    private static final int MAX_VISIBLE_MEMBERS = 8;
    private static final int MARGIN_LEFT = 10;
    private static final int MARGIN_TOP = 300; // Below EnduranceQuestOverlay

    // === Toggle (disabled by default - enable via radial menu) ===
    private static boolean enabled = false;

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

        // FIX AUDIT #1/#9: Single snapshot for render frame consistency
        // All questData accesses use this snapshot to avoid TOCTOU races.
        //
        // FIX AUDIT2 #10: Why explicit null checks instead of @SuppressWarnings("null")?
        // - @SuppressWarnings would hide ALL null warnings in the method, including legitimate ones
        // - Explicit checks are self-documenting and precisely scoped
        // - IDE doesn't recognize that hasQuestData==true implies questData!=null
        final QuestSyncPayload questData = ClientQuestCache.getData();
        final boolean hasQuestData = questData != null && questData.hasActiveQuest();

        // Derived state (null checks satisfy IDE static analysis)
        boolean atCheckpoint = questData != null && hasQuestData
            && questData.getState() == EnduranceQuestState.WAVE_COMPLETE;
        boolean tensionActive = ClientTensionCache.isActive();
        List<String> modifiers = questData != null && hasQuestData
            ? questData.waveModifiers() : List.of();
        boolean hasModifiers = modifiers != null && !modifiers.isEmpty();
        boolean syncStale = hasQuestData && ClientQuestCache.isStale(3000);
        int syncSeconds = syncStale ? Math.max(1, (int) Math.ceil(ClientQuestCache.getTimeSinceUpdate() / 1000.0)) : 0;
        String waitingLabel = atCheckpoint ? buildWaitingLabel(members) : "";
        boolean showWaiting = !waitingLabel.isEmpty();
        boolean localSpectator = false;
        boolean localReady = false;
        var localPlayer = mc.player;
        if (localPlayer != null) {
            UUID localId = localPlayer.getUUID();
            for (PartySyncPayload.PartyMemberInfo member : members) {
                if (member.playerId().equals(localId)) {
                    localSpectator = member.isSpectator();
                    localReady = member.isReady();
                    break;
                }
            }
        }
        String nextLabel = "";
        if (hasQuestData) {
            if (localSpectator) {
                nextLabel = "NEXT: READY = REJOIN";
            } else if (atCheckpoint) {
                nextLabel = localReady ? "NEXT: WAITING PARTY" : "NEXT: READY to CONTINUE";
            }
        }
        boolean showNext = !nextLabel.isEmpty();

        int activeCount = 0;
        int readyCount = 0;
        if (hasQuestData) {
            for (PartySyncPayload.PartyMemberInfo member : members) {
                if (member.isSpectator() || !member.isOnline()) {
                    continue;
                }
                activeCount++;
                if (member.isReady()) {
                    readyCount++;
                }
            }
        }
        int gateTotal = Math.max(1, activeCount);
        float gateProgress = activeCount > 0 ? (float) readyCount / activeCount : 0f;

        int totalMembers = members.size();
        int visibleCount = Math.min(totalMembers, MAX_VISIBLE_MEMBERS);
        boolean hasOverflow = totalMembers > MAX_VISIBLE_MEMBERS;

        int headerLines = 1;
        if (hasQuestData) {
            headerLines += 2;
            if (syncStale) headerLines++;
            if (showWaiting) headerLines++;
            if (showNext) headerLines++;
            if (hasModifiers) headerLines++;
        }

        int lineHeight = font.lineHeight;
        int headerTextHeight = headerLines * lineHeight + (headerLines - 1) * HEADER_LINE_GAP;
        int barCount = hasQuestData ? 1 + (tensionActive ? 1 : 0) : 0;
        int barBlockHeight = barCount > 0
            ? (barCount * HEADER_BAR_HEIGHT + (barCount - 1) * HEADER_BAR_GAP)
            : 0;
        int headerBlockHeight = PADDING + headerTextHeight
            + (barCount > 0 ? HEADER_BAR_TOP_GAP + barBlockHeight : 0);

        // Calculate panel size
        int panelHeight = headerBlockHeight + visibleCount * MEMBER_HEIGHT + PADDING;
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
        String headerLeft = "Party " + members.size();
        String headerRight = "";
        String waveLabel = "";
        if (hasQuestData && questData != null) {
            if (questData.endlessMode()) {
                waveLabel = "Wave " + questData.currentWave() + "/INF";
            } else {
                waveLabel = "Wave " + questData.currentWave() + "/" + questData.totalWaves();
            }
            headerRight = waveLabel;
        }
        var safeFont = Objects.requireNonNull(font);
        int headerX = x + PADDING;
        int headerY = y + PADDING;
        graphics.drawString(safeFont, headerLeft, headerX, headerY, TEXT_SECONDARY);
        if (!headerRight.isEmpty()) {
            int rightWidth = safeFont.width(headerRight);
            int rightX = x + PANEL_WIDTH - PADDING - rightWidth;
            graphics.drawString(safeFont, headerRight, rightX, headerY, TEXT_SECONDARY);
        }

        int lineIndex = 1;
        if (hasQuestData && questData != null) {
            String runLabel = "Run " + formatDuration(questData.sessionDurationMs());
            runLabel = fitToWidth(safeFont, runLabel, PANEL_WIDTH - PADDING * 2 - 50);
            int runY = headerY + lineIndex * (lineHeight + HEADER_LINE_GAP);
            graphics.drawString(safeFont, runLabel, headerX, runY, TEXT_SECONDARY);

            String tensionLabel = "";
            int tensionColor = TEXT_SECONDARY;
            if (tensionActive) {
                int tensionPercent = Math.round(ClientTensionCache.getTensionPercent() * 100f);
                String tensionName = ClientTensionCache.getTensionLabel();
                if (tensionName == null) {
                    tensionName = "";
                }
                tensionLabel = (tensionName.isEmpty() ? "Tension" : tensionName) + " " + tensionPercent + "%";
                tensionColor = ClientTensionCache.getDisplayColor();
            }
            if (!tensionLabel.isEmpty()) {
                int tensionWidth = safeFont.width(tensionLabel);
                int tensionX = x + PANEL_WIDTH - PADDING - tensionWidth;
                graphics.drawString(safeFont, tensionLabel, tensionX, runY, tensionColor);
            }
            lineIndex++;

            if (syncStale) {
                int syncY = headerY + lineIndex * (lineHeight + HEADER_LINE_GAP);
                graphics.drawString(safeFont, "SYNC " + syncSeconds + "s", headerX, syncY, STATUS_OFFLINE);
                lineIndex++;
            }

            String detailLeft;
            String detailRight = "";
            int detailColor = TEXT_PRIMARY;
            if (atCheckpoint) {
                detailLeft = "Checkpoint " + readyCount + "/" + gateTotal;
                detailColor = readyCount >= gateTotal ? STATUS_READY : STATUS_WAITING;
                if (activeCount == 0) {
                    detailRight = "No actives";
                } else {
                    int waiting = Math.max(0, gateTotal - readyCount);
                    detailRight = waiting > 0 ? "Waiting " + waiting : "All Ready";
                }
            } else {
                String objective = questData.objectiveTitle();
                if (objective == null || objective.isEmpty()) {
                    objective = questData.questName();
                }
                int progress = questData.objectiveProgress();
                int target = questData.objectiveTarget();
                WaveObjectiveState.Type objectiveType = questData.getObjectiveType();
                if (objectiveType == WaveObjectiveState.Type.KILL_ALL) {
                    progress = questData.mobsKilledInWave();
                    target = questData.totalMobsInWave();
                    if (target > 0) {
                        detailLeft = "Kills " + progress + "/" + target;
                    } else {
                        detailLeft = "Kills";
                    }
                } else {
                    detailLeft = "Obj " + objective;
                }
                if (objectiveType != WaveObjectiveState.Type.KILL_ALL) {
                    if (target > 0) {
                        detailRight = progress + "/" + target;
                    } else if (questData.objectiveComplete()) {
                        detailRight = "DONE";
                    } else if (questData.objectiveFailed()) {
                        detailRight = "FAIL";
                    }
                }
            }
            int detailY = headerY + lineIndex * (lineHeight + HEADER_LINE_GAP);
            int rightWidth = detailRight.isEmpty() ? 0 : safeFont.width(detailRight);
            int leftMax = PANEL_WIDTH - PADDING * 2 - (rightWidth > 0 ? rightWidth + 6 : 0);
            detailLeft = fitToWidth(safeFont, detailLeft, leftMax);
            graphics.drawString(safeFont, detailLeft, headerX, detailY, detailColor);
            if (!detailRight.isEmpty()) {
                int rightX = x + PANEL_WIDTH - PADDING - rightWidth;
                graphics.drawString(safeFont, detailRight, rightX, detailY, TEXT_SECONDARY);
            }
            lineIndex++;

            if (showWaiting) {
                String waitingText = fitToWidth(safeFont, waitingLabel, PANEL_WIDTH - PADDING * 2);
                int waitingY = headerY + lineIndex * (lineHeight + HEADER_LINE_GAP);
                graphics.drawString(safeFont, waitingText, headerX, waitingY, TEXT_SECONDARY);
                lineIndex++;
            }

            if (showNext) {
                String nextText = fitToWidth(safeFont, nextLabel, PANEL_WIDTH - PADDING * 2);
                int actionY = headerY + lineIndex * (lineHeight + HEADER_LINE_GAP);
                graphics.drawString(safeFont, nextText, headerX, actionY, STATUS_READY);
                lineIndex++;
            }

            if (hasModifiers) {
                String modsText = buildModifiersText(modifiers);
                modsText = fitToWidth(safeFont, modsText, PANEL_WIDTH - PADDING * 2);
                int modsY = headerY + lineIndex * (lineHeight + HEADER_LINE_GAP);
                graphics.drawString(safeFont, modsText, headerX, modsY, TEXT_SECONDARY);
                lineIndex++;
            }
        }

        if (hasQuestData && barCount > 0) {
            int barX = x + PADDING;
            int barY = headerY + headerTextHeight + HEADER_BAR_TOP_GAP;
            int barWidth = PANEL_WIDTH - PADDING * 2;
            if (tensionActive) {
                graphics.fill(barX, barY, barX + barWidth, barY + HEADER_BAR_HEIGHT, STATUS_BAR_BG);
                int fill = Math.round(barWidth * ClientTensionCache.getTensionPercent());
                if (fill > 0) {
                    graphics.fill(barX, barY, barX + fill, barY + HEADER_BAR_HEIGHT, ClientTensionCache.getDisplayColor());
                }
                barY += HEADER_BAR_HEIGHT + HEADER_BAR_GAP;
            }
            graphics.fill(barX, barY, barX + barWidth, barY + HEADER_BAR_HEIGHT, STATUS_BAR_BG);
            // FIX AUDIT2 #4: Use snapshot-based getWaveProgress for frame consistency
            float progress = atCheckpoint ? gateProgress : ClientQuestCache.getWaveProgress(questData);
            int fill = Math.round(barWidth * Math.min(1f, Math.max(0f, progress)));
            int color = atCheckpoint ? STATUS_WAITING : STATUS_READY;
            if (atCheckpoint) {
                color = readyCount >= gateTotal ? STATUS_READY : STATUS_WAITING;
            }
            if (fill > 0) {
                graphics.fill(barX, barY, barX + fill, barY + HEADER_BAR_HEIGHT, color);
            }
        }

        // Member list
        int memberY = y + headerBlockHeight;
        for (int i = 0; i < visibleCount; i++) {
            PartySyncPayload.PartyMemberInfo member = members.get(i);
            renderMember(graphics, font, member, x + PADDING, memberY, atCheckpoint);
            memberY += MEMBER_HEIGHT;
        }

        if (hasOverflow) {
            String moreText = "+" + (totalMembers - visibleCount) + " more";
            graphics.drawString(safeFont, moreText, x + PADDING, memberY + 4, TEXT_SECONDARY);
        }
    }

    private static void renderMember(GuiGraphics graphics, Font font,
                                      PartySyncPayload.PartyMemberInfo member,
                                      int x, int y, boolean atCheckpoint) {
        // Status indicator (colored dot)
        int statusColor = getStatusColor(member, atCheckpoint);
        graphics.fill(x, y + 2, x + 4, y + 6, statusColor);

        // Leader star
        String prefix = member.isLeader() ? "\u2605 " : "";
        int nameColor = member.isLeader() ? COLOR_LEADER :
                       (member.isOnline() ? TEXT_PRIMARY : COLOR_OFFLINE);

        // Player name (truncated if too long)
        String name = prefix + truncateName(member.playerName(), 10);
        graphics.drawString(Objects.requireNonNull(font), name, x + 8, y, nameColor);

        // Status label
        String statusLabel = Objects.requireNonNull(getStatusLabel(member, atCheckpoint), "statusLabel");
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
            if (member.isSpectator()) {
                fillWidth = barWidth / 2;
                fillColor = COLOR_SPECTATOR;
            } else if (atCheckpoint && !member.isReady()) {
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

    private static int getStatusColor(PartySyncPayload.PartyMemberInfo member, boolean atCheckpoint) {
        if (!member.isOnline()) return STATUS_OFFLINE;
        if (member.isSpectator()) return COLOR_SPECTATOR;
        if (!atCheckpoint) return STATUS_READY;
        if (member.isReady()) return STATUS_READY;
        return STATUS_WAITING;
    }

    private static String getStatusLabel(PartySyncPayload.PartyMemberInfo member, boolean atCheckpoint) {
        if (!member.isOnline()) return "OFF";
        if (member.isSpectator()) return "SPEC";
        if (!atCheckpoint) return "ACT";
        if (member.isReady()) return "RDY";
        return "WAIT";
    }

    private static String truncateName(String name, int maxLen) {
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 2) + "..";
    }

    private static String buildModifiersText(List<String> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return "";
        }
        int cap = Math.min(3, modifiers.size());
        String text = String.join(", ", modifiers.subList(0, cap));
        if (modifiers.size() > cap) {
            text = text + " +" + (modifiers.size() - cap);
        }
        return "Mods: " + text;
    }

    private static String buildWaitingLabel(List<PartySyncPayload.PartyMemberInfo> members) {
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

    private static String fitToWidth(Font font, String text, int maxWidth) {
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

    private static String formatDuration(long durationMs) {
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
