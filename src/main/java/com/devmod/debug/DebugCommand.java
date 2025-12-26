package com.devmod.debug;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionCommandInvoker;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/**
 * Command handler for /devdebug command.
 * Usage: /devdebug <feature> - toggles debug feature
 *        /devdebug list - shows all features and status
 *        /devdebug off - disables all features
 */
public class DebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devdebug")
                .requires(source -> source.hasPermission(2)) // Requires op level 2
                .then(Commands.literal("list")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DEBUG_COMMAND_LIST, ctx)))
                .then(Commands.literal("off")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DEBUG_COMMAND_OFF, ctx)))
                .then(Commands.argument("feature", Objects.requireNonNull(StringArgumentType.word()))
                    .suggests(DebugCommand::suggestFeatures)
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DEBUG_COMMAND_TOGGLE, ctx)))
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DEBUG_COMMAND_HELP, ctx))
        );
    }

    public static void registerActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_COMMAND_HELP)
            .labelKey("devmod.action.debug.command.help")
            .descriptionKey("devmod.action.debug.command.help.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Help")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devdebug")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    showHelp(cmd);
                    return;
                }
                context.executeCommand("devdebug");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_COMMAND_LIST)
            .labelKey("devmod.action.debug.command.list")
            .descriptionKey("devmod.action.debug.command.list.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/List")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devdebug list")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    listFeatures(cmd);
                    return;
                }
                context.executeCommand("devdebug list");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_COMMAND_OFF)
            .labelKey("devmod.action.debug.command.off")
            .descriptionKey("devmod.action.debug.command.off.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Disable All")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devdebug off")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    disableAll(cmd);
                    return;
                }
                context.executeCommand("devdebug off");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DEBUG_COMMAND_TOGGLE)
            .labelKey("devmod.action.debug.command.toggle")
            .descriptionKey("devmod.action.debug.command.toggle.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Debug/Native/Toggle Feature")
            .icon(Items.COMMAND_BLOCK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devdebug <feature>")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    toggleFeature(cmd);
                    return;
                }
                if (!context.openCommandPrompt("devdebug ")) {
                    context.executeCommand("devdebug");
                }
            })
            .build());
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§e=== DevMod Debug System ==="), false);
        source.sendSuccess(() -> Component.literal("§7/devdebug <feature> §f- Toggle a debug feature"), false);
        source.sendSuccess(() -> Component.literal("§7/devdebug list §f- Show all features and status"), false);
        source.sendSuccess(() -> Component.literal("§7/devdebug off §f- Disable all features"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eAvailable features:"), false);

        for (DebugFeature feature : DebugFeature.values()) {
            source.sendSuccess(() -> Component.literal(
                "§7- §f" + feature.getId() + " §8(" + feature.getDisplayName() + ")"
            ), false);
        }

        return 1;
    }

    private static int listFeatures(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("This command must be run by a player")));
            return 0;
        }

        Set<DebugFeature> enabled = DebugManager.INSTANCE.getEnabledFeatures(player);

        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("§e=== Debug Features Status ===")), false);

        for (DebugFeature feature : DebugFeature.values()) {
            boolean isEnabled = enabled.contains(feature);
            String status = isEnabled ? "§a[ON]" : "§c[OFF]";
            String featureName = feature.getDisplayName();
            String description = feature.getDescription();

            source.sendSuccess(() -> Objects.requireNonNull(Component.literal(
                status + " §f" + featureName + " §8- " + description
            )), false);
        }

        source.sendSuccess(() -> Objects.requireNonNull(Component.literal(
            "§7Active features: §f" + enabled.size() + "/" + DebugFeature.values().length
        )), false);

        return 1;
    }

    private static int toggleFeature(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("This command must be run by a player")));
            return 0;
        }

        String featureId = StringArgumentType.getString(context, "feature");

        // Find feature by ID (case-insensitive)
        DebugFeature feature = null;
        for (DebugFeature f : DebugFeature.values()) {
            if (f.getId().equalsIgnoreCase(featureId) ||
                f.name().equalsIgnoreCase(featureId)) {
                feature = f;
                break;
            }
        }

        if (feature == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal(
                "Unknown debug feature: " + featureId + ". Use /devdebug list to see available features."
            )));
            return 0;
        }

        boolean nowEnabled = DebugManager.INSTANCE.toggle(player, feature);

        // IMPORTANT: Send sync packet to client to update DebugRenderBools
        PacketDistributor.sendToPlayer(player, new DebugSyncPayload(feature.getId(), nowEnabled));

        String status = nowEnabled ? "§aENABLED" : "§cDISABLED";
        final DebugFeature finalFeature = feature;
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal(
            "§7[Debug] §f" + finalFeature.getDisplayName() + " " + status
        )), false);

        if (nowEnabled) {
            source.sendSuccess(() -> Objects.requireNonNull(Component.literal(
                "§8" + finalFeature.getDescription()
            )), false);
        }

        return 1;
    }

    private static int disableAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("This command must be run by a player")));
            return 0;
        }

        Set<DebugFeature> enabled = DebugManager.INSTANCE.getEnabledFeatures(player);
        int count = enabled.size();

        for (DebugFeature feature : DebugFeature.values()) {
            DebugManager.INSTANCE.disable(player, feature);
            // Send sync packet to client
            PacketDistributor.sendToPlayer(player, new DebugSyncPayload(feature.getId(), false));
        }

        source.sendSuccess(() -> Objects.requireNonNull(Component.literal(
            "§7[Debug] §cDisabled all " + count + " debug features"
        )), false);

        return 1;
    }

    private static CompletableFuture<Suggestions> suggestFeatures(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        Arrays.stream(DebugFeature.values())
            .map(DebugFeature::getId)
            .filter(id -> id.startsWith(input))
            .forEach(builder::suggest);

        return builder.buildFuture();
    }
}
