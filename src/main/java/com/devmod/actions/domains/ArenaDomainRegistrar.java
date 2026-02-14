package com.devmod.actions.domains;

import java.util.List;
import java.util.Set;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

/**
 * Domain registrar for arena actions: template management, autosmoke,
 * arena status, force controls, and HUD toggles.
 *
 * <p>Common actions are SERVER channel (executed via server commands).
 * Client actions (create, template info, validate, force, metrics) use
 * BOTH channel since they may open a command prompt on the client.
 */
public final class ArenaDomainRegistrar implements DomainRegistrar {

    @Override
    public String domainName() {
        return "arena";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        return List.of(
            // ── Server-side common actions ──
            serverArenaSpec(ActionIds.ARENA_HELP,
                "devmod.action.arena.help", "devmod.action.arena.help.desc",
                "minecraft:book", "Root/Arena/Help", "arena", 2),

            serverArenaSpec(ActionIds.ARENA_TEMPLATE_LIST,
                "devmod.action.arena.template.list", "devmod.action.arena.template.list.desc",
                "minecraft:map", "Root/Arena/Templates/List", "arena template list", 2),

            serverArenaSpec(ActionIds.ARENA_TEMPLATE_RELOAD,
                "devmod.action.arena.template.reload", "devmod.action.arena.template.reload.desc",
                "minecraft:repeater", "Root/Arena/Templates/Reload", "arena template reload", 2),

            serverArenaSpec(ActionIds.ARENA_TEMPLATE_STATUS,
                "devmod.action.arena.template.status", "devmod.action.arena.template.status.desc",
                "minecraft:clock", "Root/Arena/Templates/Status", "arena template status", 2),

            serverArenaSpec(ActionIds.ARENA_AUTOSMOKE_RUN,
                "devmod.action.arena.autosmoke.run", "devmod.action.arena.autosmoke.run.desc",
                "minecraft:campfire", "Root/Arena/Autosmoke/Run", "arena autosmoke run", 2),

            serverArenaSpec(ActionIds.ARENA_AUTOSMOKE_STATUS,
                "devmod.action.arena.autosmoke.status", "devmod.action.arena.autosmoke.status.desc",
                "minecraft:clock", "Root/Arena/Autosmoke/Status", "arena autosmoke status", 2),

            serverArenaSpec(ActionIds.ARENA_AUTOSMOKE_SCHEDULE_STATUS,
                "devmod.action.arena.autosmoke.schedule", "devmod.action.arena.autosmoke.schedule.desc",
                "minecraft:clock", "Root/Arena/Autosmoke/Schedule", "arena autosmoke schedule", 2),

            serverArenaSpec(ActionIds.ARENA_STATUS,
                "devmod.action.arena.status", "devmod.action.arena.status.desc",
                "minecraft:beacon", "Root/Arena/Status", "arena status", 2),

            serverArenaSpec(ActionIds.ARENA_FORCE_CLEAR,
                "devmod.action.arena.force.clear", "devmod.action.arena.force.clear.desc",
                "minecraft:barrier", "Root/Arena/Force/Clear", "arena force clear", 2),

            serverArenaSpec(ActionIds.ARENA_FORCE_STATUS,
                "devmod.action.arena.force.status", "devmod.action.arena.force.status.desc",
                "minecraft:comparator", "Root/Arena/Force/Status", "arena force status", 2),

            serverArenaSpec(ActionIds.ARENA_HUD_TOGGLE,
                "devmod.action.arena.hud.toggle", "devmod.action.arena.hud.toggle.desc",
                "minecraft:redstone_torch", "Root/Arena/HUD/Toggle", "arena hud toggle", 2),

            serverArenaSpec(ActionIds.ARENA_HUD_ON,
                "devmod.action.arena.hud.on", "devmod.action.arena.hud.on.desc",
                "minecraft:lime_dye", "Root/Arena/HUD/On", "arena hud on", 2),

            serverArenaSpec(ActionIds.ARENA_HUD_OFF,
                "devmod.action.arena.hud.off", "devmod.action.arena.hud.off.desc",
                "minecraft:gray_dye", "Root/Arena/HUD/Off", "arena hud off", 2),

            serverArenaSpec(ActionIds.ARENA_HUD_STATUS,
                "devmod.action.arena.hud.status", "devmod.action.arena.hud.status.desc",
                "minecraft:comparator", "Root/Arena/HUD/Status", "arena hud status", 2),

            // ── Client-side actions (prompt-based) ──
            clientArenaSpec(ActionIds.ARENA_CREATE,
                "devmod.action.arena.create", "devmod.action.arena.create.desc",
                "minecraft:structure_block", "Root/Arena/Create", "arena create <template>", 2),

            clientArenaSpec(ActionIds.ARENA_TEMPLATE_INFO,
                "devmod.action.arena.template.info", "devmod.action.arena.template.info.desc",
                "minecraft:paper", "Root/Arena/Templates/Info", "arena template info <id>", 2),

            clientArenaSpec(ActionIds.ARENA_VALIDATE,
                "devmod.action.arena.validate", "devmod.action.arena.validate.desc",
                "minecraft:anvil", "Root/Arena/Templates/Validate", "arena validate <id>", 2),

            clientArenaSpec(ActionIds.ARENA_FORCE,
                "devmod.action.arena.force", "devmod.action.arena.force.desc",
                "minecraft:name_tag", "Root/Arena/Force/Set", "arena force <template> [minutes]", 2),

            clientArenaSpec(ActionIds.ARENA_METRICS,
                "devmod.action.arena.metrics", "devmod.action.arena.metrics.desc",
                "minecraft:clock", "Root/Arena/Templates/Metrics", "arena metrics <id>", 2)
        );
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        // Server-side common actions
        for (String id : List.of(
                ActionIds.ARENA_HELP, ActionIds.ARENA_TEMPLATE_LIST,
                ActionIds.ARENA_TEMPLATE_RELOAD, ActionIds.ARENA_TEMPLATE_STATUS,
                ActionIds.ARENA_AUTOSMOKE_RUN, ActionIds.ARENA_AUTOSMOKE_STATUS,
                ActionIds.ARENA_AUTOSMOKE_SCHEDULE_STATUS, ActionIds.ARENA_STATUS,
                ActionIds.ARENA_FORCE_CLEAR, ActionIds.ARENA_FORCE_STATUS,
                ActionIds.ARENA_HUD_TOGGLE, ActionIds.ARENA_HUD_ON,
                ActionIds.ARENA_HUD_OFF, ActionIds.ARENA_HUD_STATUS,
                ActionIds.ARENA_CREATE, ActionIds.ARENA_TEMPLATE_INFO,
                ActionIds.ARENA_VALIDATE, ActionIds.ARENA_FORCE,
                ActionIds.ARENA_METRICS)) {
            registry.register(id, context -> ActionRegistry.invoke(id, context));
        }
    }

    @Override
    public Set<String> getDependencies() {
        return Set.of();
    }

    // ── Factory helpers ──

    private static ActionSpec serverArenaSpec(String id, String labelKey, String descKey,
                                              String iconItemId, String menuPath,
                                              String commandHint, int permLevel) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.SERVER)
            .category(ActionCategory.ARENA)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(permLevel)
            .ui(labelKey, descKey, iconItemId, menuPath, false)
            .binding(null, commandHint, null)
            .policy(false, 0, "requiresPermissionOrClient_2")
            .telemetryTags("arena")
            .build();
    }

    private static ActionSpec clientArenaSpec(String id, String labelKey, String descKey,
                                              String iconItemId, String menuPath,
                                              String commandHint, int permLevel) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.BOTH)
            .category(ActionCategory.ARENA)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(permLevel)
            .ui(labelKey, descKey, iconItemId, menuPath, false)
            .binding(null, commandHint, null)
            .policy(false, 0, "requiresPermissionOrClient_2")
            .telemetryTags("arena")
            .build();
    }
}
