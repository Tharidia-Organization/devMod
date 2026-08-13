package com.devmod.npc.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.DevMod;
import com.devmod.debug.DiagnosticLogger;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.data.NpcRegistry;
import com.devmod.npc.dialog.condition.DialogCondition;
import com.devmod.npc.event.DialogEvent;
import com.devmod.npc.event.DialogEventBus;

/**
 * Manages active dialog sessions between players and NPCs.
 * Handles session creation, action processing, and validation.
 */
public class NpcDialogManager {
    public static final NpcDialogManager INSTANCE = new NpcDialogManager();

    /** Maximum session duration before auto-cleanup (10 minutes) */
    private static final long MAX_SESSION_DURATION_MS = 10 * 60 * 1000;

    /** Rate limit: max actions per player per minute */
    private static final int MAX_ACTIONS_PER_MINUTE = 60;

    private final Map<UUID, NpcDialogSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> actionTimestamps = new ConcurrentHashMap<>();

    private NpcDialogManager() {}

    // ========================================================================
    // Session Management
    // ========================================================================

    /**
     * Opens a dialog session for a player with an NPC.
     *
     * @param player The server player
     * @param npcId  The NPC's UUID
     * @param config The NPC configuration
     * @param registry The NPC registry
     * @return The dialog payload to send to the client, or null if failed
     */
    @Nullable
    public NpcDialogResult openDialog(
        @Nonnull ServerPlayer player,
        @Nonnull UUID npcId,
        @Nonnull NpcConfiguration config,
        @Nonnull NpcRegistry registry
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(registry, "registry");

        String dialogSetId = config.dialogSetId();
        if (dialogSetId == null || dialogSetId.isEmpty()) {
            DevMod.LOGGER.debug("NPC {} has no dialog set assigned", npcId);
            return null;
        }

        // Get dialog set from registry (custom or preset)
        DialogSet dialogSet = DialogRegistry.getDialogSet(dialogSetId).orElse(null);
        if (dialogSet == null) {
            DevMod.LOGGER.warn("Dialog set {} not found for NPC {}", dialogSetId, npcId);
            return null;
        }

        // Check schedule availability
        if (!dialogSet.isScheduleAvailable(player.level())) {
            String reason = dialogSet.getUnavailabilityReason(player.level());
            DevMod.LOGGER.debug("Dialog {} unavailable for NPC {}: {}", dialogSetId, npcId, reason);
            // Send unavailability message to player
            if (reason != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A77" + config.displayName() + ": \u00A7c" + reason));
            }
            return null;
        }

        // Get entry node
        DialogNode entryNode = dialogSet.getEntryNode().orElse(null);
        if (entryNode == null) {
            DevMod.LOGGER.warn("Entry node not found in dialog set {}", dialogSetId);
            return null;
        }

        // Mark as visited (for FirstVisit conditions)
        registry.markVisited(player.getUUID(), npcId);

        // Create context for condition evaluation
        NpcContext context = new NpcContext(
            npcId,
            config.displayName(),
            dialogSetId,
            entryNode.id(),
            registry,
            player.getServer()
        );

        // Find the first visible node (checking conditions)
        DialogNode visibleNode = findVisibleNode(entryNode, player, context);
        if (visibleNode == null) {
            DevMod.LOGGER.debug("No visible dialog node for player {} with NPC {}", player.getName().getString(), npcId);
            return null;
        }

        // Create session
        NpcDialogSession session = NpcDialogSession.create(
            player.getUUID(),
            npcId,
            dialogSetId,
            visibleNode.id()
        );
        activeSessions.put(player.getUUID(), session);

        // Filter visible options
        List<DialogOption> visibleOptions = filterVisibleOptions(visibleNode.options(), player, context);

        DevMod.LOGGER.debug("Opened dialog for {} with NPC {} at node {}",
            player.getName().getString(), npcId, visibleNode.id());

        DiagnosticLogger.npc("openDialog: player=%s, npcId=%s, dialogSet=%s, node=%s, options=%d",
            player.getName().getString(), npcId, dialogSetId, visibleNode.id(), visibleOptions.size());

        // Post dialog opened event
        DialogEventBus.post(new DialogEvent.Opened(
            player.getUUID(), npcId, dialogSetId, visibleNode.id()
        ));

        // Resolve placeholders in lines and option labels
        List<String> resolvedLines = resolveLines(visibleNode.lines(), player, context);
        List<DialogOption> resolvedOptions = resolveOptionLabels(visibleOptions, player, context);

        return new NpcDialogResult(
            npcId,
            config.displayName(),
            dialogSetId,
            visibleNode.id(),
            resolvedLines,
            resolvedOptions
        );
    }

    /**
     * Opens a dialog session for a player with an NPC using a dialog set ID directly.
     * Used when the NPC might not be in the registry (e.g., entity-based lookup).
     *
     * @param player      The server player
     * @param npcId       The NPC's UUID
     * @param dialogSetId The dialog set ID to open
     * @param registry    The NPC registry
     * @return The dialog payload to send to the client, or null if failed
     */
    @Nullable
    public NpcDialogResult openDialogBySetId(
        @Nonnull ServerPlayer player,
        @Nonnull UUID npcId,
        @Nonnull String dialogSetId,
        @Nonnull NpcRegistry registry
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(dialogSetId, "dialogSetId");
        Objects.requireNonNull(registry, "registry");

        if (dialogSetId.isEmpty()) {
            DevMod.LOGGER.debug("Empty dialogSetId provided for NPC {}", npcId);
            return null;
        }

        // Get dialog set from registry
        DialogSet dialogSet = DialogRegistry.getDialogSet(dialogSetId).orElse(null);
        if (dialogSet == null) {
            // Try registry in the future
            DevMod.LOGGER.warn("Dialog set {} not found for NPC {}", dialogSetId, npcId);
            return null;
        }

        // Get NPC name (from registry or use a default)
        String npcName = registry.getNpc(npcId)
            .map(NpcConfiguration::displayName)
            .orElse("NPC");

        // Check schedule availability
        if (!dialogSet.isScheduleAvailable(player.level())) {
            String reason = dialogSet.getUnavailabilityReason(player.level());
            DevMod.LOGGER.debug("Dialog {} unavailable for NPC {}: {}", dialogSetId, npcId, reason);
            // Send unavailability message to player
            if (reason != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A77" + npcName + ": \u00A7c" + reason));
            }
            return null;
        }

        // Get entry node
        DialogNode entryNode = dialogSet.getEntryNode().orElse(null);
        if (entryNode == null) {
            DevMod.LOGGER.warn("Entry node not found in dialog set {}", dialogSetId);
            return null;
        }

        // Mark as visited (for FirstVisit conditions)
        registry.markVisited(player.getUUID(), npcId);

        // Create context for condition evaluation
        NpcContext context = new NpcContext(
            npcId,
            npcName,
            dialogSetId,
            entryNode.id(),
            registry,
            player.getServer()
        );

        // Find the first visible node (checking conditions)
        DialogNode visibleNode = findVisibleNode(entryNode, player, context);
        if (visibleNode == null) {
            DevMod.LOGGER.debug("No visible dialog node for player {} with NPC {}", player.getName().getString(), npcId);
            return null;
        }

        // Create session
        NpcDialogSession session = NpcDialogSession.create(
            player.getUUID(),
            npcId,
            dialogSetId,
            visibleNode.id()
        );
        activeSessions.put(player.getUUID(), session);

        // Filter visible options
        List<DialogOption> visibleOptions = filterVisibleOptions(visibleNode.options(), player, context);

        DevMod.LOGGER.debug("Opened dialog for {} with NPC {} at node {}",
            player.getName().getString(), npcId, visibleNode.id());

        DiagnosticLogger.npc("openDialogBySetId: player=%s, npcId=%s, dialogSet=%s, node=%s, options=%d",
            player.getName().getString(), npcId, dialogSetId, visibleNode.id(), visibleOptions.size());

        // Post dialog opened event
        DialogEventBus.post(new DialogEvent.Opened(
            player.getUUID(), npcId, dialogSetId, visibleNode.id()
        ));

        // Resolve placeholders in lines and option labels
        List<String> resolvedLines = resolveLines(visibleNode.lines(), player, context);
        List<DialogOption> resolvedOptions = resolveOptionLabels(visibleOptions, player, context);

        return new NpcDialogResult(
            npcId,
            npcName,
            dialogSetId,
            visibleNode.id(),
            resolvedLines,
            resolvedOptions
        );
    }

    /**
     * Processes a dialog action from a player.
     *
     * @param player    The server player
     * @param npcId     The NPC's UUID
     * @param nodeId    The current node ID (for validation)
     * @param optionId  The selected option ID
     * @param registry  The NPC registry
     * @return The next dialog payload, or null if dialog should close
     */
    @Nullable
    public NpcDialogResult processAction(
        @Nonnull ServerPlayer player,
        @Nonnull UUID npcId,
        @Nonnull String nodeId,
        @Nonnull String optionId,
        @Nonnull NpcRegistry registry
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(optionId, "optionId");
        Objects.requireNonNull(registry, "registry");

        // Rate limiting
        if (!checkRateLimit(player.getUUID())) {
            DevMod.LOGGER.warn("Rate limit exceeded for player {}", player.getName().getString());
            return null;
        }

        // Get session
        NpcDialogSession session = activeSessions.get(player.getUUID());
        if (session == null) {
            DevMod.LOGGER.warn("No active session for player {}", player.getName().getString());
            return null;
        }

        // Validate session matches request
        if (!session.isForNpc(npcId)) {
            DevMod.LOGGER.warn("Session NPC mismatch for player {}: expected {}, got {}",
                player.getName().getString(), session.npcId(), npcId);
            return null;
        }

        if (!session.currentNodeId().equals(nodeId)) {
            DevMod.LOGGER.warn("Session node mismatch for player {}: expected {}, got {}",
                player.getName().getString(), session.currentNodeId(), nodeId);
            return null;
        }

        // Get NPC config
        NpcConfiguration config = registry.getNpc(npcId).orElse(null);
        if (config == null) {
            DevMod.LOGGER.warn("NPC {} not found", npcId);
            closeDialog(player.getUUID());
            return null;
        }

        // Get dialog set
        DialogSet dialogSet = DialogRegistry.getDialogSet(session.dialogSetId()).orElse(null);
        if (dialogSet == null) {
            DevMod.LOGGER.warn("Dialog set {} not found", session.dialogSetId());
            closeDialog(player.getUUID());
            return null;
        }

        // Get current node
        DialogNode currentNode = dialogSet.getNode(nodeId).orElse(null);
        if (currentNode == null) {
            DevMod.LOGGER.warn("Node {} not found in dialog set {}", nodeId, session.dialogSetId());
            closeDialog(player.getUUID());
            return null;
        }

        // Get selected option
        DialogOption option = currentNode.getOption(optionId).orElse(null);
        if (option == null) {
            DevMod.LOGGER.warn("Option {} not found in node {}", optionId, nodeId);
            closeDialog(player.getUUID());
            return null;
        }

        // Create context
        NpcContext context = new NpcContext(
            npcId,
            config.displayName(),
            session.dialogSetId(),
            nodeId,
            registry,
            player.getServer()
        );

        // Re-check the option's visibility condition before acting on it.
        // openDialog only filters options for display, so a client that knows an
        // option id could otherwise claim a gated reward or trigger a gated
        // console command by selecting an option it was never shown.
        DialogCondition optionCondition = option.showCondition();
        if (optionCondition != null && !optionCondition.test(player, context)) {
            DevMod.LOGGER.warn("Player {} selected option {} in node {} whose condition does not hold",
                player.getName().getString(), optionId, nodeId);
            closeDialog(player.getUUID());
            return null;
        }

        // Execute action
        DiagnosticLogger.npc("processAction: player=%s, npcId=%s, node=%s, option=%s (%s)",
            player.getName().getString(), npcId, nodeId, optionId, option.label());
        option.action().execute(player, context);

        // Post option selected event
        DialogEventBus.post(new DialogEvent.OptionSelected(
            player.getUUID(), npcId, session.dialogSetId(), nodeId, optionId, option.label()
        ));

        // Record option selection in dialog memory for HasSelectedOption condition
        registry.recordOptionSelected(player.getUUID(), npcId, optionId);

        // Check if dialog should close
        if (context.isDialogClosed()) {
            closeDialog(player.getUUID());
            return null;
        }

        // Get next node
        String nextNodeId = context.currentNodeId();
        DialogNode nextNode = dialogSet.getNode(nextNodeId).orElse(null);
        if (nextNode == null) {
            DevMod.LOGGER.warn("Next node {} not found", nextNodeId);
            closeDialog(player.getUUID());
            return null;
        }

        // Update session
        activeSessions.put(player.getUUID(), session.withCurrentNode(nextNodeId));

        // Filter visible options for next node
        List<DialogOption> visibleOptions = filterVisibleOptions(nextNode.options(), player, context);

        // Resolve placeholders in lines and option labels
        List<String> resolvedLines = resolveLines(nextNode.lines(), player, context);
        List<DialogOption> resolvedOptions = resolveOptionLabels(visibleOptions, player, context);

        return new NpcDialogResult(
            npcId,
            config.displayName(),
            session.dialogSetId(),
            nextNodeId,
            resolvedLines,
            resolvedOptions
        );
    }

    /**
     * Closes a dialog session for a player.
     */
    public void closeDialog(@Nonnull UUID playerId) {
        closeDialog(playerId, DialogEvent.CloseReason.PLAYER_CHOICE);
    }

    /**
     * Closes a dialog session for a player with a specific reason.
     */
    public void closeDialog(@Nonnull UUID playerId, @Nonnull DialogEvent.CloseReason reason) {
        NpcDialogSession removed = activeSessions.remove(playerId);
        if (removed != null) {
            DevMod.LOGGER.debug("Closed dialog session for player {}", playerId);
            DiagnosticLogger.npc("closeDialog: player=%s, npcId=%s, reason=%s", playerId, removed.npcId(), reason);

            // Post dialog closed event
            DialogEventBus.post(new DialogEvent.Closed(
                playerId, removed.npcId(), removed.dialogSetId(), reason
            ));
        }
    }

    /**
     * Gets the active session for a player.
     */
    @Nonnull
    public Optional<NpcDialogSession> getSession(@Nonnull UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * Checks if a player has an active dialog session.
     */
    public boolean hasActiveSession(@Nonnull UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Finds the first visible node, considering show conditions.
     */
    @Nullable
    private DialogNode findVisibleNode(DialogNode startNode, ServerPlayer player, NpcContext context) {
        // Check if start node is visible
        if (startNode.showCondition() == null || startNode.showCondition().test(player, context)) {
            return startNode;
        }

        // If start node has a condition that fails, there's no automatic fallback
        // The dialog set should be designed to handle this
        return null;
    }

    /**
     * Filters options to only include those that pass their show conditions.
     */
    @Nonnull
    private List<DialogOption> filterVisibleOptions(List<DialogOption> options, ServerPlayer player, NpcContext context) {
        List<DialogOption> visible = new ArrayList<>();
        for (DialogOption option : options) {
            DialogCondition condition = option.showCondition();
            if (condition == null || condition.test(player, context)) {
                visible.add(option);
            }
        }
        return visible;
    }

    /**
     * Resolves placeholders in dialog lines.
     */
    @Nonnull
    private List<String> resolveLines(List<String> lines, ServerPlayer player, NpcContext context) {
        return lines.stream()
            .map(line -> PlaceholderResolver.resolve(line, player, context))
            .collect(Collectors.toList());
    }

    /**
     * Resolves placeholders in option labels.
     */
    @Nonnull
    private List<DialogOption> resolveOptionLabels(List<DialogOption> options, ServerPlayer player, NpcContext context) {
        return options.stream()
            .map(opt -> new DialogOption(
                opt.id(),
                PlaceholderResolver.resolve(opt.label(), player, context),
                opt.icon(),
                opt.showCondition(),
                opt.action()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Checks rate limiting for a player.
     * Thread-safe: Synchronizes on the timestamp list to prevent TOCTOU race conditions
     * where multiple threads could pass the check before any adds a timestamp.
     */
    private boolean checkRateLimit(@Nonnull UUID playerId) {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60_000;

        List<Long> timestamps = actionTimestamps.computeIfAbsent(playerId, k -> new ArrayList<>());

        // Synchronize on the list to make check-then-act atomic
        synchronized (timestamps) {
            // Remove old timestamps
            timestamps.removeIf(t -> t < oneMinuteAgo);

            // Check limit
            if (timestamps.size() >= MAX_ACTIONS_PER_MINUTE) {
                return false;
            }

            timestamps.add(now);
            return true;
        }
    }

    /**
     * Cleans up stale sessions (called periodically).
     */
    public void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        activeSessions.entrySet().removeIf(entry -> {
            boolean stale = entry.getValue().isOlderThan(MAX_SESSION_DURATION_MS);
            if (stale) {
                DevMod.LOGGER.debug("Cleaned up stale session for player {}", entry.getKey());
                // Post timeout event
                NpcDialogSession session = entry.getValue();
                DialogEventBus.post(new DialogEvent.Closed(
                    entry.getKey(), session.npcId(), session.dialogSetId(), DialogEvent.CloseReason.TIMEOUT
                ));
            }
            return stale;
        });

        // Also cleanup old action timestamps
        // Must synchronize on each list to avoid race with checkRateLimit()
        long oneMinuteAgo = now - 60_000;
        actionTimestamps.forEach((playerId, list) -> {
            synchronized (list) {
                list.removeIf(t -> t < oneMinuteAgo);
            }
        });
        actionTimestamps.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty();
            }
        });
    }

    /**
     * Clears all sessions (for server shutdown).
     */
    public void clearAll() {
        activeSessions.clear();
        actionTimestamps.clear();
    }

    /**
     * Cleans up all dialog data for a player (called on disconnect).
     * Prevents memory leaks by removing player-specific entries.
     *
     * @param playerId The player's UUID
     */
    public void cleanupPlayer(@Nonnull UUID playerId) {
        NpcDialogSession removed = activeSessions.remove(playerId);
        actionTimestamps.remove(playerId);
        if (removed != null) {
            DevMod.LOGGER.debug("Cleaned up dialog data for disconnected player {}", playerId);
        }
    }

    // ========================================================================
    // Result Record
    // ========================================================================

    /**
     * Result of opening or processing a dialog.
     */
    public record NpcDialogResult(
        UUID npcId,
        String npcName,
        String dialogSetId,
        String nodeId,
        List<String> lines,
        List<DialogOption> options
    ) {}
}
