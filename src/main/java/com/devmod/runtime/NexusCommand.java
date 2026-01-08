package com.devmod.runtime;
import java.util.Objects;

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
                                Objects.requireNonNull(NexusSpawnManager.zoneIds(), "zoneIds"), Objects.requireNonNull(builder, "builder")))
                            .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "zone")))))
                    .then(Commands.literal("go")
                        .executes(ctx -> teleport(ctx, "hub"))
                        .then(Commands.argument("zone", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Objects.requireNonNull(NexusSpawnManager.zoneIds(), "zoneIds"), Objects.requireNonNull(builder, "builder")))
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
                    .then(Commands.literal("avatar")
                        .executes(NexusCommand::avatarStatus)
                        .then(Commands.literal("spawn")
                            .requires(source -> source.hasPermission(2))
                            .executes(NexusCommand::spawnAvatar))
                        .then(Commands.literal("remove")
                            .requires(source -> source.hasPermission(2))
                            .executes(NexusCommand::removeAvatar))
                        .then(Commands.literal("status")
                            .executes(NexusCommand::avatarStatus)))
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
            Component.literal("§7/§fdevmod nexus avatar spawn §7- respawn Nexus AI (admin)")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus zones §7- list zone ids")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus status §7- show Nexus state")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod nexus rebuild §7- rebuild hub (admin)")), false);
        return 1;
    }

    private static int spawnAvatar(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = requireNexusLevel(source);
        if (level == null) {
            return 0;
        }
        NexusAvatarManager.spawn(level, NexusDimensionManager.getHubOrigin());
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§aNexus avatar spawned")), true);
        return 1;
    }

    private static int removeAvatar(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = requireNexusLevel(source);
        if (level == null) {
            return 0;
        }
        NexusAvatarManager.remove(level, NexusDimensionManager.getHubOrigin());
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§eNexus avatar removed")), true);
        return 1;
    }

    private static int avatarStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = requireNexusLevel(source);
        if (level == null) {
            return 0;
        }
        boolean present = NexusAvatarManager.hasAvatar(level, NexusDimensionManager.getHubOrigin());
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§eNexus Avatar: §f" + (present ? "online" : "missing"))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Name: §f" + Config.NEXUS_AVATAR_NAME.get())), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Skin: §f" + Config.NEXUS_AVATAR_SKIN.get())), false);
        return 1;
    }

    private static ServerLevel requireNexusLevel(CommandSourceStack source) {
        if (!Config.NEXUS_ENABLED.get()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cNexus is disabled in config"), "message"));
            return null;
        }
        MinecraftServer server = source.getServer();
        NexusDimensionManager.INSTANCE.ensureNexusDimension(server);
        ServerLevel level = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (level == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cNexus dimension not ready"), "message"));
        }
        return level;
    }

    private static int zones(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§eZones: §f" + NexusSpawnManager.zoneListLabel())), false);
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
        NexusSpawnManager.Zone zone = NexusSpawnManager.resolveZone(zoneId);
        if (zone == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cUnknown zone '" + zoneId
                + "'. Use /devmod nexus zones"), "message"));
            return 0;
        }
        NexusDimensionManager.INSTANCE.teleportPlayerToZone(player, zone);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§aTeleported to §f" + zone.label())), true);
        return 1;
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
}
