package com.devmod.runtime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.config.Config;
import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketManager;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;
import com.devmod.zone.runtime.ZoneResolver;

/**
 * Admin commands for Nexus hub control.
 */
public final class NexusCommand {
    private NexusCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("nexus")
                    .executes(NexusCommand::help)
                    .then(Commands.literal("help")
                        .executes(NexusCommand::help))
                    .then(Commands.literal("zones")
                        .executes(NexusCommand::zones))
                    .then(Commands.literal("tp")
                        .executes(ctx -> teleport(ctx, "hub"))
                        .then(Commands.argument("zone", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Objects.requireNonNull(getZoneIds(Objects.requireNonNull(ctx.getSource().getServer())), "zoneIds"),
                                Objects.requireNonNull(builder, "builder")))
                            .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "zone")))))
                    .then(Commands.literal("go")
                        .executes(ctx -> teleport(ctx, "hub"))
                        .then(Commands.argument("zone", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Objects.requireNonNull(getZoneIds(Objects.requireNonNull(ctx.getSource().getServer())), "zoneIds"),
                                Objects.requireNonNull(builder, "builder")))
                            .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "zone")))))
                    .then(Commands.literal("enter")
                        .executes(ctx -> teleport(ctx, "hub")))
                    .then(Commands.literal("return")
                        .executes(NexusCommand::returnToOrigin))
                    .then(Commands.literal("exit")
                        .executes(NexusCommand::returnToOrigin))
                    .then(Commands.literal("hub")
                        .executes(NexusCommand::openTestingHubHint))
                    .then(Commands.literal("test")
                        .executes(NexusCommand::openTestingHubHint))
                    .then(Commands.literal("riftstamp")
                        .requires(source -> source.hasPermission(2))
                        .executes(NexusCommand::spawnRiftStamp))
                    .then(Commands.literal("bug")
                        .then(Commands.argument("message", Objects.requireNonNull(StringArgumentType.greedyString(), "StringArgumentType.greedyString"))
                            .executes(ctx -> submitTicket(ctx, TicketCategory.BUG))))
                    .then(Commands.literal("suggestion")
                        .then(Commands.argument("message", Objects.requireNonNull(StringArgumentType.greedyString(), "StringArgumentType.greedyString"))
                            .executes(ctx -> submitTicket(ctx, TicketCategory.SUGGESTION))))
                    .then(Commands.literal("question")
                        .then(Commands.argument("message", Objects.requireNonNull(StringArgumentType.greedyString(), "StringArgumentType.greedyString"))
                            .executes(ctx -> submitTicket(ctx, TicketCategory.QUESTION))))
                    .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(NexusCommand::status))
                    .then(Commands.literal("rebuild")
                        .requires(source -> source.hasPermission(4))
                        .executes(NexusCommand::rebuild))
                    .then(Commands.literal("lock")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> setLock(ctx, true)))
                    .then(Commands.literal("unlock")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> setLock(ctx, false)))
                    .then(Commands.literal("admin")
                        .then(Commands.literal("instances")
                            .requires(source -> source.hasPermission(Config.ADMIN_PANEL_PERMISSION_LEVEL.get()))
                            .executes(NexusCommand::openAdminInstancePanel)))
                )
        );
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§b=== DevMod Nexus ===")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus tp <zone> §7- teleport to a Nexus zone")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus enter §7- enter hub and save return")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus return §7- return to previous location")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus bug <msg> §7- file a bug report")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus hub §7- open Testing Hub (F7)")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus riftstamp §7- spawn a RiftStamp portal (admin)")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus zones §7- list zone ids")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus status §7- show Nexus state")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus rebuild §7- rebuild hub (admin)")), false);
        return 1;
    }

    private static int zones(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<String> zoneIds = getZoneIds(Objects.requireNonNull(source.getServer()));
        if (zoneIds.isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§eZones: §f<none>")), false);
            return 1;
        }
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§eZones: §f" + String.join(", ", zoneIds))), false);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx, String zoneId) {
        CommandSourceStack source = ctx.getSource();
        if (!Config.NEXUS_ENABLED.get()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cNexus is disabled in config"), "message"));
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cPlayer required"), "message"));
            return 0;
        }
        Optional<ZoneDefinition> zoneOpt = ZoneResolver.INSTANCE.resolveByNameOrAlias(
            Objects.requireNonNull(source.getServer()), Objects.requireNonNull(zoneId));
        if (zoneOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cUnknown zone '" + zoneId
                + "'. Use /devmod nexus zones"), "message"));
            return 0;
        }
        ZoneDefinition zone = zoneOpt.get();
        boolean success = NexusDimensionManager.INSTANCE.teleportPlayerToZone(player, zone.zoneId());
        if (!success) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cFailed to teleport to '" + zone.zoneId() + "'"), "message"));
            return 0;
        }
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§aTeleported to §f" + zone.displayName())), true);
        return 1;
    }

    @Nonnull
    private static ZoneRegistry getZoneRegistry(@Nonnull MinecraftServer server) {
        ZoneRegistry registry = ZoneRegistry.get(server);
        if (!registry.isInitialized()) {
            registry.initializeWithLegacyZones(Objects.requireNonNull(NexusDimensionManager.getHubOrigin()));
        }
        return registry;
    }

    @Nonnull
    private static List<String> getZoneIds(@Nonnull MinecraftServer server) {
        ZoneRegistry registry = getZoneRegistry(server);
        return Objects.requireNonNull(registry.getAllZones().stream()
            .map(ZoneDefinition::zoneId)
            .sorted()
            .toList());
    }

    private static int returnToOrigin(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cPlayer required"), "message"));
            return 0;
        }
        boolean success = NexusDimensionManager.INSTANCE.teleportPlayerToReturn(player);
        if (!success) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cNo return point saved. Use /devmod nexus enter."), "message"));
            return 0;
        }
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§aReturned to your previous location")), true);
        return 1;
    }

    private static int openTestingHubHint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Open Testing Hub with §fF7§7 or the radial menu")), false);
        return 1;
    }

    private static int spawnRiftStamp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cPlayer required"), "message"));
            return 0;
        }
        boolean ok = RiftStampManager.INSTANCE.spawnAtPlayer(player);
        if (!ok) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cFailed to spawn RiftStamp here."), "message"));
            return 0;
        }
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§aRiftStamp opened for 60 seconds.")), true);
        return 1;
    }

    private static int submitTicket(CommandContext<CommandSourceStack> ctx, TicketCategory category)
        throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String message = StringArgumentType.getString(ctx, "message");
        if (message.length() < 10) {
            player.sendSystemMessage(Objects.requireNonNull(Component.literal("§cAdd more detail (min 10 chars)."), "message"));
            return 0;
        }

        String subject = message.length() > 50 ? message.substring(0, 47) + "..." : message;
        player.sendSystemMessage(Objects.requireNonNull(Component.literal("§7Submitting " + category.getDisplayName() + "..."), "message"));
        TicketManager.INSTANCE.createTicket(
            player.getUUID(),
            player.getName().getString(),
            category,
            subject,
            message
        ).thenAccept(ticket -> {
            player.sendSystemMessage(Objects.requireNonNull(Component.literal("§aTicket submitted: §f"
                + ticket.id().toString().substring(0, 8)), "message"));
        }).exceptionally(e -> {
            player.sendSystemMessage(Objects.requireNonNull(Component.literal("§cFailed to submit ticket."), "message"));
            return null;
        });

        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        NexusHubSavedData data = NexusHubSavedData.get(server);
        ServerLevel level = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION, "NEXUS_DIMENSION"));

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§e=== Nexus Status ===")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Enabled: §f" + Config.NEXUS_ENABLED.get())), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Loaded: §f" + (level != null))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Built: §f" + data.isBuilt() + " §7(v" + data.getVersion() + ")")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Locked: §f" + data.isLocked())), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Build Mode: §f" + Config.NEXUS_BUILD_MODE.get())), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Rebuild Policy: §f" + Config.NEXUS_REBUILD_POLICY.get())), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Palette: §f" + Config.NEXUS_PALETTE_PROFILE.get())), false);

        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        boolean queued = NexusDimensionManager.INSTANCE.requestRebuild(server, true);
        if (queued) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§aNexus rebuild queued")), false);
        } else {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§eNexus rebuild already in progress")), false);
        }
        return 1;
    }

    private static int setLock(CommandContext<CommandSourceStack> ctx, boolean locked) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        NexusDimensionManager.INSTANCE.setHubLocked(server, locked);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(locked ? "§eNexus locked" : "§aNexus unlocked")), false);
        return 1;
    }

    private static int openAdminInstancePanel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cPlayer required"), "message"));
            return 0;
        }

        // Send sync data to player - client will open the screen
        com.devmod.runtime.network.AdminInstanceNetworkHandler.sendSyncToPlayer(player);

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§aOpening Instance Control Panel...")), false);
        return 1;
    }
}
