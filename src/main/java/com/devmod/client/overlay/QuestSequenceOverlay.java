package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.party.ArrivalConfirmPayload;
import com.devmod.party.QuestSequencePayload;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class QuestSequenceOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "quest_sequence");

    // Singleton for state management
    public static final QuestSequenceOverlay INSTANCE = new QuestSequenceOverlay();

    // Current sequence state
    @Nullable
    private UUID activePartyId = null;
    private QuestSequencePayload.Phase currentPhase = QuestSequencePayload.Phase.CANCELLED;
    private int secondsRemaining = 0;
    private int phaseStartSeconds = 0;
    private int totalMembers = 0;
    private List<UUID> arrivedMembers = List.of();
    private String title = "";
    private String subtitle = "";
    private List<String> infoLines = List.of();

    // Track if we've sent arrival confirmation
    private boolean arrivalConfirmed = false;
    // Retry timer for arrival confirmation (in case server rejects due to position not yet synced)
    private int arrivalRetryTicks = 0;
    private static final int ARRIVAL_RETRY_INTERVAL = 10; // 0.5 seconds

    // Animation
    private float animationProgress = 0f;
    private static final int FADE_IN_TICKS = 10;
    private static final int FADE_OUT_TICKS = 20;
    private boolean fadingOut = false;
    private int fadeOutTicks = 0;

    private QuestSequenceOverlay() {}

    /**
     * Register the GUI layer for rendering.
     */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.BOSS_OVERLAY),
            Objects.requireNonNull(LAYER_ID),
            QuestSequenceOverlay::renderStatic
        );
    }

    /**
     * Static render method for the GUI layer callback.
     */
    private static void renderStatic(GuiGraphics graphics, DeltaTracker deltaTracker) {
        INSTANCE.render(graphics, deltaTracker.getRealtimeDeltaTicks());
    }

    /**
     * Update the sequence state from server payload.
     */
    public void update(QuestSequencePayload payload) {
        QuestSequencePayload.Phase previousPhase = this.currentPhase;

        this.activePartyId = payload.partyId();
        this.currentPhase = payload.phase();
        this.secondsRemaining = payload.secondsRemaining();
        this.totalMembers = payload.totalMembers();
        this.arrivedMembers = payload.arrivedMembers();
        this.title = payload.title();
        this.subtitle = payload.subtitle();
        this.infoLines = payload.infoLines();
        this.fadingOut = false;
        this.fadeOutTicks = 0;

        if (currentPhase != previousPhase) {
            phaseStartSeconds = secondsRemaining;
        } else if (secondsRemaining > phaseStartSeconds) {
            phaseStartSeconds = secondsRemaining;
        }

        // Reset arrival confirmation when starting new sequence
        if (currentPhase == QuestSequencePayload.Phase.COUNTDOWN_START) {
            arrivalConfirmed = false;
            arrivalRetryTicks = 0;
        }

        // Check if we're in WAITING_FOR_ARRIVALS and need to send/retry confirmation
        if (currentPhase == QuestSequencePayload.Phase.WAITING_FOR_ARRIVALS) {
            Minecraft mc = Minecraft.getInstance();
            UUID myUUID = mc.player != null ? mc.player.getUUID() : null;

            // Check if server has already confirmed our arrival
            boolean serverConfirmedArrival = myUUID != null && arrivedMembers.contains(myUUID);

            if (serverConfirmedArrival) {
                // Server confirmed - no need to retry
                arrivalConfirmed = true;
            } else if (!arrivalConfirmed || arrivalRetryTicks >= ARRIVAL_RETRY_INTERVAL) {
                // Send initial or retry confirmation
                sendArrivalConfirmation();
                arrivalRetryTicks = 0;
            }
        }

        // Start fade out if sequence ended
        if (currentPhase == QuestSequencePayload.Phase.STARTED ||
            currentPhase == QuestSequencePayload.Phase.CANCELLED) {
            fadingOut = true;
        }
    }

    /**
     * Send arrival confirmation to server.
     * Will retry if server hasn't confirmed yet (position verification may fail initially due to lag).
     */
    private void sendArrivalConfirmation() {
        if (activePartyId != null) {
            arrivalConfirmed = true;
            PacketDistributor.sendToServer(new ArrivalConfirmPayload(activePartyId));
        }
    }

    /**
     * Clear the overlay (sequence ended or cancelled).
     */
    public void clear() {
        fadingOut = true;
    }

    /**
     * Check if overlay should be rendered.
     */
    public boolean isActive() {
        return activePartyId != null && (!fadingOut || fadeOutTicks < FADE_OUT_TICKS);
    }

    /**
     * Render the overlay.
     */
    public void render(GuiGraphics graphics, float partialTick) {
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.options.hideGui) return;
        var font = Objects.requireNonNull(mc.font, "font");

        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Update animation
        if (fadingOut) {
            fadeOutTicks++;
            animationProgress = Math.max(0, 1f - (fadeOutTicks / (float) FADE_OUT_TICKS));
            if (fadeOutTicks >= FADE_OUT_TICKS) {
                activePartyId = null;
                return;
            }
        } else {
            animationProgress = Math.min(1f, animationProgress + partialTick / FADE_IN_TICKS);
        }

        // Increment retry timer for arrival confirmation
        if (currentPhase == QuestSequencePayload.Phase.WAITING_FOR_ARRIVALS) {
            arrivalRetryTicks++;
            // Check if we need to retry (server hasn't confirmed our arrival yet)
            UUID myUUID = player.getUUID();
            boolean serverConfirmedArrival = arrivedMembers.contains(myUUID);
            if (!serverConfirmedArrival && arrivalRetryTicks >= ARRIVAL_RETRY_INTERVAL) {
                sendArrivalConfirmation();
                arrivalRetryTicks = 0;
            }
        }

        int alpha = (int) (255 * animationProgress);
        if (alpha < 10) return;

        // Calculate box size - larger for arrival phase
        int boxWidth = 240;
        int baseHeight = currentPhase == QuestSequencePayload.Phase.WAITING_FOR_ARRIVALS ? 90 : 70;
        int extraLines = 0;
        if (currentPhase == QuestSequencePayload.Phase.BRIEFING) {
            extraLines += infoLines.size();
        }
        if (subtitle != null && !subtitle.isBlank()) {
            extraLines += 1;
        }
        if (title != null && !title.isBlank() && currentPhase == QuestSequencePayload.Phase.BRIEFING) {
            extraLines += 1;
        }
        int boxHeight = baseHeight + Math.min(extraLines * 10, 60);
        int boxX = (screenWidth - boxWidth) / 2;
        int boxY = 40;

        // Background
        int bgColor = DesignTokens.withAlpha(DesignTokens.Background.PANEL_SOLID,
            (int) (DesignTokens.Alpha.A88 * animationProgress));
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, bgColor);

        // Border with glow effect based on phase
        int borderColor = getPhaseColor();
        borderColor = DesignTokens.withAlpha(borderColor, alpha);
        graphics.renderOutline(boxX, boxY, boxWidth, boxHeight, borderColor);

        // Phase title
        Component phaseText = Objects.requireNonNull(getPhaseText(), "phase text");
        int textColor = DesignTokens.withAlpha(DesignTokens.Text.PRIMARY, alpha);
        graphics.drawCenteredString(font, phaseText, boxX + boxWidth / 2, boxY + 8, textColor);

        // Render based on phase
        if (currentPhase == QuestSequencePayload.Phase.BRIEFING) {
            renderBriefing(graphics, Objects.requireNonNull(font, "font"), boxX, boxY, boxWidth, alpha);
        } else if (currentPhase == QuestSequencePayload.Phase.WAITING_FOR_ARRIVALS) {
            renderArrivalStatus(graphics, Objects.requireNonNull(font, "font"), boxX, boxY, boxWidth, alpha);
        } else if (secondsRemaining > 0 && !fadingOut) {
            renderCountdown(graphics, Objects.requireNonNull(font, "font"), boxX, boxY, boxWidth, alpha);
        } else if (currentPhase == QuestSequencePayload.Phase.STARTED) {
            renderGoMessage(graphics, Objects.requireNonNull(font, "font"), boxX, boxY, boxWidth, alpha);
        }

        // Progress bar
        renderProgressBar(graphics, boxX, boxY, boxWidth, boxHeight, alpha);
    }

    /**
     * Render countdown number.
     */
    private void renderCountdown(GuiGraphics graphics, net.minecraft.client.gui.Font font, int boxX, int boxY, int boxWidth, int alpha) {
        var safeFont = Objects.requireNonNull(font, "font");
        if (subtitle != null && !subtitle.isBlank()) {
            String safeSubtitle = Objects.requireNonNull(subtitle, "subtitle");
            int subtitleColor = DesignTokens.withAlpha(DesignTokens.Text.SECONDARY, alpha);
            graphics.drawCenteredString(safeFont, safeSubtitle, boxX + boxWidth / 2, boxY + 22, subtitleColor);
        }
        String countdownText = Objects.requireNonNull(String.valueOf(secondsRemaining), "countdown");
        int countdownColor = getCountdownColor();
        countdownColor = DesignTokens.withAlpha(countdownColor, alpha);

        graphics.pose().pushPose();
        graphics.pose().translate(boxX + boxWidth / 2f, boxY + 32, 0);
        graphics.pose().scale(2f, 2f, 1f);
        graphics.drawCenteredString(safeFont, countdownText, 0, 0, countdownColor);
        graphics.pose().popPose();
    }

    private void renderBriefing(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                int boxX, int boxY, int boxWidth, int alpha) {
        var safeFont = Objects.requireNonNull(font, "font");
        int textColor = DesignTokens.withAlpha(DesignTokens.Text.SECONDARY, alpha);
        int lineY = boxY + 24;

        if (title != null && !title.isBlank()) {
            String safeTitle = Objects.requireNonNull(title, "title");
            graphics.drawCenteredString(safeFont, safeTitle, boxX + boxWidth / 2, lineY, textColor);
            lineY += 10;
        }

        if (subtitle != null && !subtitle.isBlank()) {
            String safeSubtitle = Objects.requireNonNull(subtitle, "subtitle");
            graphics.drawCenteredString(safeFont, safeSubtitle, boxX + boxWidth / 2, lineY, textColor);
            lineY += 10;
        }

        if (infoLines != null) {
            for (String line : infoLines) {
                String safeLine = Objects.requireNonNull(line, "info line");
                if (safeLine.isBlank()) {
                    continue;
                }
                graphics.drawCenteredString(safeFont, safeLine, boxX + boxWidth / 2, lineY, textColor);
                lineY += 10;
            }
        }
    }

    /**
     * Render "GO!" message.
     */
    private void renderGoMessage(GuiGraphics graphics, net.minecraft.client.gui.Font font, int boxX, int boxY, int boxWidth, int alpha) {
        int goColor = DesignTokens.withAlpha(DesignTokens.Semantic.SUCCESS, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(boxX + boxWidth / 2f, boxY + 32, 0);
        graphics.pose().scale(2f, 2f, 1f);
        graphics.drawCenteredString(Objects.requireNonNull(font, "font"), "GO!", 0, 0, goColor);
        graphics.pose().popPose();
    }

    /**
     * Render arrival status with checkmarks.
     */
    private void renderArrivalStatus(GuiGraphics graphics, net.minecraft.client.gui.Font font, int boxX, int boxY, int boxWidth, int alpha) {
        int arrived = arrivedMembers.size();
        int total = totalMembers > 0 ? totalMembers : 1;

        // Arrival count
        String arrivalText = Objects.requireNonNull(arrived + " / " + total + " arrived", "arrival text");
        int arrivalColor = DesignTokens.withAlpha(DesignTokens.Text.PRIMARY, alpha);
        var safeFont = Objects.requireNonNull(font, "font");
        graphics.drawCenteredString(safeFont, arrivalText, boxX + boxWidth / 2, boxY + 28, arrivalColor);

        // Visual dots for each player
        int dotSize = 8;
        int dotSpacing = 12;
        int totalDotsWidth = total * dotSpacing;
        int startX = boxX + (boxWidth - totalDotsWidth) / 2;
        int dotY = boxY + 45;

        for (int i = 0; i < total; i++) {
            int dotX = startX + i * dotSpacing;
            boolean isArrived = i < arrived;

            int dotColor = isArrived
                ? DesignTokens.withAlpha(DesignTokens.Semantic.SUCCESS, alpha)
                : DesignTokens.withAlpha(DesignTokens.Text.DISABLED, alpha);

            graphics.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, dotColor);

            // Checkmark for arrived
            if (isArrived) {
                int checkColor = DesignTokens.withAlpha(DesignTokens.Text.PRIMARY, alpha);
                graphics.drawString(safeFont, "✓", dotX + 1, dotY, checkColor);
            }
        }

        // Timeout warning
        if (secondsRemaining <= 10 && secondsRemaining > 0) {
            String timeoutText = Objects.requireNonNull("Timeout in " + secondsRemaining + "s", "timeout text");
            int timeoutColor = DesignTokens.withAlpha(DesignTokens.Accent.GOLD, alpha);
            graphics.drawCenteredString(safeFont, timeoutText, boxX + boxWidth / 2, boxY + 62, timeoutColor);
        }
    }

    /**
     * Render progress bar at bottom of box.
     */
    private void renderProgressBar(GuiGraphics graphics, int boxX, int boxY, int boxWidth, int boxHeight, int alpha) {
        int barX = boxX + 10;
        int barY = boxY + boxHeight - 12;
        int barWidth = boxWidth - 20;
        int barHeight = 4;

        // Bar background
        int barBgColor = DesignTokens.withAlpha(DesignTokens.Background.INPUT, alpha);
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, barBgColor);

        // Bar progress
        float progress = getPhaseProgress();
        int progressWidth = (int) (barWidth * progress);
        int barColor = DesignTokens.withAlpha(getPhaseColor(), alpha);
        graphics.fill(barX, barY, barX + progressWidth, barY + barHeight, barColor);
    }

    private Component getPhaseText() {
        return switch (currentPhase) {
            case COUNTDOWN_START -> Component.translatable("devmod.sequence.countdown");
            case TELEPORTING -> Component.translatable("devmod.sequence.teleporting");
            case WAITING_FOR_ARRIVALS -> Component.translatable("devmod.sequence.waiting_arrivals");
            case SYNCING -> Component.translatable("devmod.sequence.syncing");
            case STARTING -> Component.translatable("devmod.sequence.starting");
            case STARTED -> Component.translatable("devmod.sequence.started");
            case CANCELLED -> Component.translatable("devmod.sequence.cancelled");
            case BRIEFING -> Component.literal("Briefing");
            case SAFE_WINDOW -> Component.literal("Safe Window");
            case WAVE_INCOMING -> Component.literal("Wave Incoming");
            case BOSS_INTRO -> Component.literal("Boss Intro");
        };
    }

    private int getPhaseColor() {
        return switch (currentPhase) {
            case COUNTDOWN_START -> DesignTokens.Accent.GOLD;
            case TELEPORTING -> DesignTokens.Accent.PRIMARY;
            case WAITING_FOR_ARRIVALS -> DesignTokens.Accent.PURPLE;
            case SYNCING -> DesignTokens.Accent.PRIMARY;
            case STARTING -> DesignTokens.Semantic.SUCCESS;
            case STARTED -> DesignTokens.Semantic.SUCCESS;
            case CANCELLED -> DesignTokens.Semantic.ERROR;
            case BRIEFING -> DesignTokens.Accent.PRIMARY;
            case SAFE_WINDOW -> DesignTokens.Semantic.SUCCESS;
            case WAVE_INCOMING -> DesignTokens.Accent.GOLD;
            case BOSS_INTRO -> DesignTokens.Semantic.ERROR;
        };
    }

    private int getCountdownColor() {
        if (secondsRemaining <= 1) return DesignTokens.Semantic.ERROR;
        if (secondsRemaining <= 3) return DesignTokens.Accent.GOLD;
        return DesignTokens.Text.PRIMARY;
    }

    private float getPhaseProgress() {
        return switch (currentPhase) {
            case COUNTDOWN_START -> {
                int totalSeconds = phaseStartSeconds > 0 ? phaseStartSeconds : 5;
                yield secondsRemaining > 0 ? 1f - (secondsRemaining / (float) totalSeconds) : 1f;
            }
            case TELEPORTING -> 0.3f;
            case WAITING_FOR_ARRIVALS -> {
                if (totalMembers > 0) {
                    yield 0.3f + 0.4f * (arrivedMembers.size() / (float) totalMembers);
                }
                yield 0.5f;
            }
            case SYNCING -> {
                int totalSeconds = phaseStartSeconds > 0 ? phaseStartSeconds : 3;
                yield secondsRemaining > 0 ? 0.7f + (0.3f * (1f - secondsRemaining / (float) totalSeconds)) : 1f;
            }
            case STARTING, STARTED -> 1f;
            case CANCELLED -> 0f;
            case BRIEFING -> {
                int totalSeconds = phaseStartSeconds > 0 ? phaseStartSeconds : 3;
                yield secondsRemaining > 0 ? 1f - (secondsRemaining / (float) totalSeconds) : 1f;
            }
            case SAFE_WINDOW -> {
                int totalSeconds = phaseStartSeconds > 0 ? phaseStartSeconds : 3;
                yield secondsRemaining > 0 ? 1f - (secondsRemaining / (float) totalSeconds) : 1f;
            }
            case WAVE_INCOMING -> {
                int totalSeconds = phaseStartSeconds > 0 ? phaseStartSeconds : 10;
                yield secondsRemaining > 0 ? 1f - (secondsRemaining / (float) totalSeconds) : 1f;
            }
            case BOSS_INTRO -> {
                int totalSeconds = phaseStartSeconds > 0 ? phaseStartSeconds : 1;
                yield secondsRemaining > 0 ? 1f - (secondsRemaining / (float) totalSeconds) : 1f;
            }
        };
    }
}
