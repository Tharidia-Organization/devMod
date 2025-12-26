package com.devmod.arena.command;

import java.util.function.Function;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.item.Items;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
public final class ArenaActionRegistry {
    private ArenaActionRegistry() {}

    public static void registerCommonActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_HELP)
                .labelKey("devmod.action.arena.help")
                .descriptionKey("devmod.action.arena.help.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Help")
                .icon(Items.BOOK)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena")
                .handler(context -> handleCommand(context, "arena", ActionIds.ARENA_HELP))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_TEMPLATE_LIST)
                .labelKey("devmod.action.arena.template.list")
                .descriptionKey("devmod.action.arena.template.list.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Templates/List")
                .icon(Items.MAP)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena template list")
                .handler(context -> handleCommand(context, "arena template list", ActionIds.ARENA_TEMPLATE_LIST))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_TEMPLATE_RELOAD)
                .labelKey("devmod.action.arena.template.reload")
                .descriptionKey("devmod.action.arena.template.reload.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Templates/Reload")
                .icon(Items.REPEATER)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena template reload")
                .handler(context -> handleCommand(context, "arena template reload", ActionIds.ARENA_TEMPLATE_RELOAD))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_AUTOSMOKE_RUN)
                .labelKey("devmod.action.arena.autosmoke.run")
                .descriptionKey("devmod.action.arena.autosmoke.run.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Autosmoke/Run")
                .icon(Items.CAMPFIRE)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena autosmoke run")
                .handler(context -> handleCommand(context, "arena autosmoke run", ActionIds.ARENA_AUTOSMOKE_RUN))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_AUTOSMOKE_STATUS)
                .labelKey("devmod.action.arena.autosmoke.status")
                .descriptionKey("devmod.action.arena.autosmoke.status.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Autosmoke/Status")
                .icon(Items.CLOCK)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena autosmoke status")
                .handler(context -> handleCommand(context, "arena autosmoke status", ActionIds.ARENA_AUTOSMOKE_STATUS))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_AUTOSMOKE_SCHEDULE_STATUS)
                .labelKey("devmod.action.arena.autosmoke.schedule")
                .descriptionKey("devmod.action.arena.autosmoke.schedule.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Autosmoke/Schedule")
                .icon(Items.CLOCK)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena autosmoke schedule")
                .handler(context -> handleCommand(context, "arena autosmoke schedule", ActionIds.ARENA_AUTOSMOKE_SCHEDULE_STATUS))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_STATUS)
                .labelKey("devmod.action.arena.status")
                .descriptionKey("devmod.action.arena.status.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Status")
                .icon(Items.BEACON)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena status")
                .handler(context -> handleCommand(context, "arena status", ActionIds.ARENA_STATUS))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_FORCE_CLEAR)
                .labelKey("devmod.action.arena.force.clear")
                .descriptionKey("devmod.action.arena.force.clear.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Force/Clear")
                .icon(Items.BARRIER)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena force clear")
                .handler(context -> handleCommand(context, "arena force clear", ActionIds.ARENA_FORCE_CLEAR))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_FORCE_STATUS)
                .labelKey("devmod.action.arena.force.status")
                .descriptionKey("devmod.action.arena.force.status.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Force/Status")
                .icon(Items.COMPARATOR)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena force status")
                .handler(context -> handleCommand(context, "arena force status", ActionIds.ARENA_FORCE_STATUS))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_HUD_TOGGLE)
                .labelKey("devmod.action.arena.hud.toggle")
                .descriptionKey("devmod.action.arena.hud.toggle.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/HUD/Toggle")
                .icon(Items.REDSTONE_TORCH)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena hud toggle")
                .handler(context -> handleCommand(context, "arena hud toggle", ActionIds.ARENA_HUD_TOGGLE))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_HUD_ON)
                .labelKey("devmod.action.arena.hud.on")
                .descriptionKey("devmod.action.arena.hud.on.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/HUD/On")
                .icon(Items.LIME_DYE)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena hud on")
                .handler(context -> handleCommand(context, "arena hud on", ActionIds.ARENA_HUD_ON))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_HUD_OFF)
                .labelKey("devmod.action.arena.hud.off")
                .descriptionKey("devmod.action.arena.hud.off.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/HUD/Off")
                .icon(Items.GRAY_DYE)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena hud off")
                .handler(context -> handleCommand(context, "arena hud off", ActionIds.ARENA_HUD_OFF))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_HUD_STATUS)
                .labelKey("devmod.action.arena.hud.status")
                .descriptionKey("devmod.action.arena.hud.status.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/HUD/Status")
                .icon(Items.COMPARATOR)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena hud status")
                .handler(context -> handleCommand(context, "arena hud status", ActionIds.ARENA_HUD_STATUS))
                .build());
    }

    public static void registerClientActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_CREATE)
                .labelKey("devmod.action.arena.create")
                .descriptionKey("devmod.action.arena.create.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Create")
                .icon(Items.STRUCTURE_BLOCK)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena create <template>")
                .handler(context -> handleCommandPrompt(context, "arena create ", ActionIds.ARENA_CREATE))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_TEMPLATE_INFO)
                .labelKey("devmod.action.arena.template.info")
                .descriptionKey("devmod.action.arena.template.info.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Templates/Info")
                .icon(Items.PAPER)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena template info <id>")
                .handler(context -> handleCommandPrompt(context, "arena template info ", ActionIds.ARENA_TEMPLATE_INFO))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_VALIDATE)
                .labelKey("devmod.action.arena.validate")
                .descriptionKey("devmod.action.arena.validate.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Templates/Validate")
                .icon(Items.ANVIL)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena validate <id>")
                .handler(context -> handleCommandPrompt(context, "arena validate ", ActionIds.ARENA_VALIDATE))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_FORCE)
                .labelKey("devmod.action.arena.force")
                .descriptionKey("devmod.action.arena.force.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Force/Set")
                .icon(Items.NAME_TAG)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena force <template> [minutes]")
                .handler(context -> handleCommandPrompt(context, "arena force ", ActionIds.ARENA_FORCE))
                .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ARENA_METRICS)
                .labelKey("devmod.action.arena.metrics")
                .descriptionKey("devmod.action.arena.metrics.desc")
                .category(ActionCategory.ARENA)
                .menuPath("Root/Arena/Templates/Metrics")
                .icon(Items.CLOCK)
                .precondition(ActionPreconditions.requiresPermissionOrClient(2))
                .commandHint("arena metrics <id>")
                .handler(context -> handleCommandPrompt(context, "arena metrics ", ActionIds.ARENA_METRICS))
                .build());
    }

    private static void handleCommand(ActionContext context, String command, String actionId) {
        CommandContext<CommandSourceStack> cmd = context.getCommandContext();
        if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
            Function<CommandContext<CommandSourceStack>, Integer> handler = ArenaActionBridge.getHandler(actionId);
            if (handler != null) {
                handler.apply(cmd);
                return;
            }
        }
        context.executeCommand(command);
    }

    private static void handleCommandPrompt(ActionContext context, String command, String actionId) {
        CommandContext<CommandSourceStack> cmd = context.getCommandContext();
        if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
            Function<CommandContext<CommandSourceStack>, Integer> handler = ArenaActionBridge.getHandler(actionId);
            if (handler != null) {
                handler.apply(cmd);
                return;
            }
        }
        if (!context.openCommandPrompt(command)) {
            context.executeCommand(command);
        }
    }
}
