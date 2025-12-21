package com.frenkvs.devmod.actions;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.telemetry.TelemetryJson;
import com.frenkvs.devmod.telemetry.TelemetryService;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionRegistry {
    private static final Map<String, RadialAction> ACTIONS = new ConcurrentHashMap<>();

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

    public static boolean invoke(String id, ActionContext context) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(context, "context");

        RadialAction action = ACTIONS.get(id);
        if (action == null) {
            DevMod.LOGGER.warn("[ActionRegistry] Unknown action id {}", id);
            return false;
        }

        ActionPrecondition precondition = action.getPrecondition();
        if (!precondition.test(context)) {
            context.sendFailure(precondition.failureMessage(context));
            logInvocation(action, context, false);
            return false;
        }

        if (action.requiresConfirm() && !context.isConfirmed()) {
            context.sendFailure(Component.translatable("devmod.action.requires_confirm"));
            logInvocation(action, context, false);
            return false;
        }

        try {
            action.invoke(context);
            logInvocation(action, context, true);
            return true;
        } catch (Exception e) {
            DevMod.LOGGER.error("[ActionRegistry] Failed to invoke action {}", id, e);
            context.sendFailure(Component.translatable("devmod.action.failed", action.getLabel()));
            logInvocation(action, context, false);
            return false;
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

    private static void logInvocation(RadialAction action, ActionContext context, boolean success) {
        if (context.getSource() == null && context.getServerPlayer() == null) {
            return;
        }
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

        String line = "{\"ts\":\"" + Instant.now() + "\"," +
            "\"type\":\"action.invoked\"," +
            "\"action\":\"" + TelemetryJson.escape(action.getId()) + "\"," +
            "\"origin\":\"" + TelemetryJson.escape(context.getOrigin().name().toLowerCase(Locale.ROOT)) + "\"," +
            "\"player\":\"" + TelemetryJson.escape(playerName) + "\"," +
            "\"dimension\":\"" + TelemetryJson.escape(dimension) + "\"," +
            "\"success\":" + success + "}";

        TelemetryService.INSTANCE.appendActionLine(line);
    }
}
