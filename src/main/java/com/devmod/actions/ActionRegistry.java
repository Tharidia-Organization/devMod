package com.devmod.actions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

import com.devmod.DevMod;
import com.devmod.telemetry.TelemetryJson;
import com.devmod.telemetry.TelemetryService;

public final class ActionRegistry {
    private static final Map<String, RadialAction> ACTIONS = new ConcurrentHashMap<>();

    /**
     * P1: Whitelist of action IDs that can be safely invoked from untrusted (network) contexts.
     * Actions not in this list will be rejected when origin is NETWORK or UNKNOWN.
     * This provides defense-in-depth against arbitrary action invocation attacks.
     */
    private static final Set<String> CLIENT_SAFE_ACTIONS = Set.of(
        // Endurance quest actions - safe, server validates quest state
        ActionIds.ENDURANCE_QUEST_START,
        ActionIds.ENDURANCE_QUEST_CONTINUE,
        ActionIds.ENDURANCE_QUEST_EXIT,
        ActionIds.QUEST_TASK_COMPLETE,

        // Ability actions - safe, server validates cooldowns
        ActionIds.ABILITY_DASH,
        ActionIds.ABILITY_DODGE,

        // UI open actions - safe, client-side only
        ActionIds.UI_RADIAL_OPEN,
        ActionIds.UI_SETTINGS_OPEN,
        ActionIds.UI_ENDURANCE_SCREEN_OPEN,
        ActionIds.UI_ENDURANCE_SHOP_OPEN,
        ActionIds.UI_PARTY_OPEN,
        ActionIds.UI_NOTIFICATION_CENTER_OPEN,
        ActionIds.UI_MAILBOX_OPEN,
        ActionIds.UI_QUEST_DEATH_OPEN,
        ActionIds.UI_PERK_SELECTION_OPEN,
        ActionIds.UI_QUEST_COMPLETION_OPEN,
        ActionIds.UI_WAVE_CHECKPOINT_OPEN,
        ActionIds.UI_SEASON_PASS_OPEN,

        // HUD toggles - safe, client-side state
        ActionIds.HUD_IMPACT_TOGGLE,
        ActionIds.HUD_QUEST_TOGGLE,
        ActionIds.HUD_ENDURANCE_TOGGLE,
        ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE,

        // Onboarding - safe, client preference
        ActionIds.UI_ONBOARDING_START,
        ActionIds.UI_ONBOARDING_SKIP
    );

    /** Metrics: count of rejected untrusted action attempts */
    private static final AtomicLong untrustedActionRejections = new AtomicLong(0);

    private ActionRegistry() {}

    public static void register(RadialAction action) {
        Objects.requireNonNull(action, "action");
        RadialAction existing = ACTIONS.putIfAbsent(action.getId(), action);
        if (existing != null) {
            DevMod.LOGGER.warn("[ActionRegistry] Duplicate action id {}, keeping original", action.getId());
        }
    }

    public static Collection<RadialAction> listActions() {
        return List.copyOf(ACTIONS.values());
    }

    @Nullable
    public static RadialAction getAction(String id) {
        return ACTIONS.get(id);
    }

    /**
     * Invokes an action and returns a boolean for backward compatibility.
     * @see #invokeWithResult(String, ActionContext) for structured results
     */
    public static boolean invoke(String id, ActionContext context) {
        return invokeWithResult(id, context).isSuccess();
    }

    /**
     * Invokes an action and returns a structured ActionResult with status, error code, and duration.
     */
    public static ActionResult invokeWithResult(String id, ActionContext context) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(context, "context");

        long startTime = System.currentTimeMillis();

        // P1: Validate untrusted origins against whitelist
        ActionOrigin origin = context.getOrigin();
        if (!origin.isTrusted() && !CLIENT_SAFE_ACTIONS.contains(id)) {
            untrustedActionRejections.incrementAndGet();
            DevMod.LOGGER.warn("[SECURITY] Untrusted action blocked: id={}, origin={}", id, origin);
            ActionResult result = ActionResult.blocked(ActionResult.ERROR_UNTRUSTED_ACTION,
                "Action not allowed from untrusted origin");
            logInvocationExtended(id, null, context, result);
            return result;
        }

        RadialAction action = ACTIONS.get(id);
        if (action == null) {
            DevMod.LOGGER.warn("[ActionRegistry] Unknown action id {}", id);
            ActionResult result = ActionResult.blocked(ActionResult.ERROR_UNKNOWN_ACTION,
                "Unknown action: " + id);
            logInvocationExtended(id, null, context, result);
            return result;
        }

        ActionPrecondition precondition = action.getPrecondition();
        if (!precondition.test(context)) {
            context.sendFailure(precondition.failureMessage(context));
            ActionResult result = ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED,
                precondition.failureMessage(context).getString());
            logInvocationExtended(id, action, context, result);
            return result;
        }

        if (action.requiresConfirm() && !context.isConfirmed()) {
            context.sendFailure(Component.translatable("devmod.action.requires_confirm"));
            ActionResult result = ActionResult.blocked(ActionResult.ERROR_REQUIRES_CONFIRM,
                "Action requires confirmation");
            logInvocationExtended(id, action, context, result);
            return result;
        }

        try {
            action.invoke(context);
            long durationMs = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.ok(durationMs);
            logInvocationExtended(id, action, context, result);
            return result;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            DevMod.LOGGER.error("[ActionRegistry] Failed to invoke action {}", id, e);
            context.sendFailure(Component.translatable("devmod.action.failed", action.getLabel()));
            ActionResult result = ActionResult.failed(ActionResult.ERROR_EXCEPTION,
                Objects.requireNonNullElse(e.getMessage(), "Unknown error"), durationMs);
            logInvocationExtended(id, action, context, result);
            return result;
        }
    }

    public static List<RadialAction> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<RadialAction> matches = new ArrayList<>();
        for (RadialAction action : ACTIONS.values()) {
            if (action.getId().toLowerCase(Locale.ROOT).contains(needle)
                || action.getLabelKey().toLowerCase(Locale.ROOT).contains(needle)
                || action.getDescriptionKey().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(action);
            }
        }
        matches.sort(Comparator.comparing(RadialAction::getId));
        return matches;
    }

    public static List<RadialAction> actionsForContext(ActionContext context) {
        List<RadialAction> available = new ArrayList<>();
        for (RadialAction action : ACTIONS.values()) {
            if (action.getPrecondition().test(context)) {
                available.add(action);
            }
        }
        available.sort(Comparator.comparing(RadialAction::getId));
        return available;
    }

    /**
     * Extended telemetry logging with ActionResult details.
     * Logs radial_action_invoked, radial_action_blocked, or radial_action_failed events.
     */
    private static void logInvocationExtended(String actionId, @Nullable RadialAction action,
                                               ActionContext context, ActionResult result) {
        String playerName = "server";
        var player = context.getPlayer();
        if (player != null) {
            playerName = player.getGameProfile().getName();
        }
        String dimension = "";
        var level = context.getLevel();
        if (level != null) {
            dimension = level.dimension().location().toString();
        }

        String eventType = switch (result.status()) {
            case OK -> "radial_action_invoked";
            case BLOCKED -> "radial_action_blocked";
            case FAILED -> "radial_action_failed";
        };

        String actionTypeStr = "";
        if (action != null) {
            ActionType actionType = action.getActionType();
            if (actionType != null) {
                actionTypeStr = actionType.name().toLowerCase(Locale.ROOT);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":\"").append(Instant.now()).append("\",");
        sb.append("\"type\":\"").append(eventType).append("\",");
        sb.append("\"actionId\":\"").append(TelemetryJson.escape(actionId)).append("\",");
        sb.append("\"origin\":\"").append(TelemetryJson.escape(context.getOrigin().name().toLowerCase(Locale.ROOT))).append("\",");
        sb.append("\"player\":\"").append(TelemetryJson.escape(playerName)).append("\",");
        sb.append("\"dimension\":\"").append(TelemetryJson.escape(dimension)).append("\",");
        if (!actionTypeStr.isEmpty()) {
            sb.append("\"actionType\":\"").append(actionTypeStr).append("\",");
        }
        sb.append("\"result\":\"").append(result.status().name()).append("\",");
        if (result.errorCode() != null) {
            sb.append("\"errorCode\":\"").append(TelemetryJson.escape(result.errorCode())).append("\",");
        }
        sb.append("\"durationMs\":").append(result.durationMs()).append("}");

        TelemetryService.INSTANCE.appendActionLine(sb.toString());
    }

    // =========================================================================
    // P1: Client Action ID Whitelist - Metrics and Utilities
    // =========================================================================

    /**
     * Returns the total count of untrusted action rejections.
     * Useful for admin dashboards and security monitoring.
     */
    public static long getUntrustedActionRejections() {
        return untrustedActionRejections.get();
    }

    /**
     * Checks if an action ID is in the client-safe whitelist.
     * Actions in this whitelist can be invoked from untrusted (NETWORK) origins.
     */
    public static boolean isClientSafeAction(String actionId) {
        return CLIENT_SAFE_ACTIONS.contains(actionId);
    }

    /**
     * Returns the number of registered actions.
     */
    public static int getRegisteredActionCount() {
        return ACTIONS.size();
    }

    /**
     * Returns the number of client-safe whitelisted actions.
     */
    public static int getClientSafeActionCount() {
        return CLIENT_SAFE_ACTIONS.size();
    }
}
