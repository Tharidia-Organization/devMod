package com.frenkvs.devmod.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
                    .executes(DebugCommand::listFeatures))
                .then(Commands.literal("off")
                    .executes(DebugCommand::disableAll))
                .then(Commands.argument("feature", StringArgumentType.word())
                    .suggests(DebugCommand::suggestFeatures)
                    .executes(DebugCommand::toggleFeature))
                .executes(DebugCommand::showHelp)
        );
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
            source.sendFailure(Component.literal("This command must be run by a player"));
            return 0;
        }

        Set<DebugFeature> enabled = DebugManager.INSTANCE.getEnabledFeatures(player);

        source.sendSuccess(() -> Component.literal("§e=== Debug Features Status ==="), false);

        for (DebugFeature feature : DebugFeature.values()) {
            boolean isEnabled = enabled.contains(feature);
            String status = isEnabled ? "§a[ON]" : "§c[OFF]";
            String featureName = feature.getDisplayName();
            String description = feature.getDescription();

            source.sendSuccess(() -> Component.literal(
                status + " §f" + featureName + " §8- " + description
            ), false);
        }

        source.sendSuccess(() -> Component.literal(
            "§7Active features: §f" + enabled.size() + "/" + DebugFeature.values().length
        ), false);

        return 1;
    }

    private static int toggleFeature(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player"));
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
            source.sendFailure(Component.literal(
                "Unknown debug feature: " + featureId + ". Use /devdebug list to see available features."
            ));
            return 0;
        }

        boolean nowEnabled = DebugManager.INSTANCE.toggle(player, feature);

        // IMPORTANT: Send sync packet to client to update DebugRenderBools
        PacketDistributor.sendToPlayer(player, new DebugSyncPayload(feature.getId(), nowEnabled));

        String status = nowEnabled ? "§aENABLED" : "§cDISABLED";
        final DebugFeature finalFeature = feature;
        source.sendSuccess(() -> Component.literal(
            "§7[Debug] §f" + finalFeature.getDisplayName() + " " + status
        ), false);

        if (nowEnabled) {
            source.sendSuccess(() -> Component.literal(
                "§8" + finalFeature.getDescription()
            ), false);
        }

        return 1;
    }

    private static int disableAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return 0;
        }

        Set<DebugFeature> enabled = DebugManager.INSTANCE.getEnabledFeatures(player);
        int count = enabled.size();

        for (DebugFeature feature : DebugFeature.values()) {
            DebugManager.INSTANCE.disable(player, feature);
            // Send sync packet to client
            PacketDistributor.sendToPlayer(player, new DebugSyncPayload(feature.getId(), false));
        }

        source.sendSuccess(() -> Component.literal(
            "§7[Debug] §cDisabled all " + count + " debug features"
        ), false);

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
