package com.devmod.runtime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.config.Config;
import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketManager;
import com.devmod.nexus.data.ZoneSlot;
import com.devmod.nexus.data.ZoneSlotRegistry;
import com.devmod.nexus.runtime.NexusHubManager;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;
import com.devmod.zone.runtime.ZoneResolver;

/**
 * Admin commands for Nexus hub control.
 */
public final class NexusCommand {
    private static final int ITEMS_PER_PAGE = 10;

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
                    .then(Commands.literal("slot")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                            .executes(ctx -> listSlots(ctx, 1))
                            .then(Commands.argument("page", Objects.requireNonNull(IntegerArgumentType.integer(1), "IntegerArgumentType.integer"))
                                .executes(ctx -> listSlots(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(Commands.literal("info")
                            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                                .suggests(NexusCommand::suggestSlotIds)
                                .executes(ctx -> showSlotInfo(ctx, StringArgumentType.getString(ctx, "slotId")))))
                        .then(Commands.literal("link")
                            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                                .suggests(NexusCommand::suggestSlotIds)
                                .then(Commands.argument("areaName", Objects.requireNonNull(StringArgumentType.greedyString(), "StringArgumentType.greedyString"))
                                    .suggests(NexusCommand::suggestAreaNames)
                                    .executes(ctx -> linkSlot(ctx,
                                        StringArgumentType.getString(ctx, "slotId"),
                                        StringArgumentType.getString(ctx, "areaName"))))))
                        .then(Commands.literal("unlink")
                            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                                .suggests(NexusCommand::suggestLinkedSlotIds)
                                .executes(ctx -> unlinkSlot(ctx, StringArgumentType.getString(ctx, "slotId")))))
                        .then(Commands.literal("tp")
                            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                                .suggests(NexusCommand::suggestSlotIds)
                                .executes(ctx -> teleportToSlot(ctx, StringArgumentType.getString(ctx, "slotId")))))
                        .then(Commands.literal("teleport")
                            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                                .suggests(NexusCommand::suggestSlotIds)
                                .executes(ctx -> teleportToSlot(ctx, StringArgumentType.getString(ctx, "slotId"))))))
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
                    .then(Commands.literal("teleport")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word(), "StringArgumentType.word"))
                            .suggests(NexusCommand::suggestSlotIds)
                            .executes(ctx -> teleportToSlot(ctx, StringArgumentType.getString(ctx, "slotId")))))
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
        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§7/§fdevmod nexus slot list §7- list hub slots")), false);
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§7/§fdevmod nexus slot info <id> §7- show slot details")), false);
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§7/§fdevmod nexus slot link <id> <area> §7- link area to slot")), false);
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§7/§fdevmod nexus slot unlink <id> §7- unlink slot")), false);
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§7/§fdevmod nexus teleport <slotId> §7- teleport to slot")), false);
        }
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

    private static int listSlots(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return 0;
        }

        NexusHubManager.INSTANCE.initialize(server);
        ZoneSlotRegistry registry = ZoneSlotRegistry.get(server);
        Collection<ZoneSlot> allSlots = registry.getAllSlots();

        if (allSlots.isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§eNo slots defined. Run /devmod nexus rebuild.")), false);
            return 0;
        }

        int totalSlots = allSlots.size();
        int totalPages = (totalSlots + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        int safePage = Math.max(1, Math.min(page, totalPages));
        int skip = (safePage - 1) * ITEMS_PER_PAGE;

        List<ZoneSlot> pageSlots = allSlots.stream()
            .sorted((a, b) -> a.slotId().compareTo(b.slotId()))
            .skip(skip)
            .limit(ITEMS_PER_PAGE)
            .toList();

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§e=== Nexus Slots (Page " + safePage + "/" + totalPages + ") ===")), false);

        for (ZoneSlot slot : pageSlots) {
            String status = slot.hasLinkedArea() ? "§a[LINKED]" : "§7[EMPTY]";
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§f  " + slot.slotId() + " §7(" + slot.type().name() + ") " + status)), false);
        }

        return 1;
    }

    private static int showSlotInfo(CommandContext<CommandSourceStack> ctx, String slotId) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return 0;
        }

        NexusHubManager.INSTANCE.initialize(server);
        ZoneSlotRegistry registry = ZoneSlotRegistry.get(server);
        Optional<ZoneSlot> slotOpt = registry.getSlot(Objects.requireNonNull(slotId));

        if (slotOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cSlot not found: " + slotId)));
            return 0;
        }

        ZoneSlot slot = slotOpt.get();

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§e=== Slot: " + slot.slotId() + " ===")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Display Name: §f" + slot.displayName())), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Type: §f" + slot.type().name())), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Bounds: §f" + formatBounds(slot))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Portal Color: §f" + slot.portalColor().name())), false);

        if (slot.hasLinkedArea()) {
            AreaRegistry areaRegistry = AreaRegistry.get(server);
            Optional<AreaDefinition> areaOpt = areaRegistry.getArea(Objects.requireNonNull(slot.linkedAreaId()));
            String areaName = areaOpt.map(AreaDefinition::name).orElse("Unknown");
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§7Linked Area: §a" + areaName)), false);
        } else {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§7Linked Area: §eNone")), false);
        }

        return 1;
    }

    private static int linkSlot(CommandContext<CommandSourceStack> ctx, String slotId, String areaName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return 0;
        }

        NexusHubManager.INSTANCE.initialize(server);
        AreaRegistry areaRegistry = AreaRegistry.get(server);
        Optional<AreaDefinition> areaOpt = areaRegistry.getAllAreas().stream()
            .filter(area -> area.name().equalsIgnoreCase(areaName))
            .findFirst();

        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cArea not found: " + areaName)));
            return 0;
        }

        UUID areaId = Objects.requireNonNull(areaOpt.get().id());
        boolean success = NexusHubManager.INSTANCE.linkAreaToSlot(server, Objects.requireNonNull(slotId), areaId);

        if (success) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§aLinked area '" + areaName + "' to slot '" + slotId + "'")), true);
            return 1;
        }

        source.sendFailure(Objects.requireNonNull(Component.literal("§cFailed to link area to slot.")));
        return 0;
    }

    private static int unlinkSlot(CommandContext<CommandSourceStack> ctx, String slotId) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return 0;
        }

        NexusHubManager.INSTANCE.initialize(server);
        boolean success = NexusHubManager.INSTANCE.unlinkAreaFromSlot(server, Objects.requireNonNull(slotId));

        if (success) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("§aUnlinked area from slot '" + slotId + "'")), true);
            return 1;
        }

        source.sendFailure(Objects.requireNonNull(Component.literal("§cFailed to unlink slot.")));
        return 0;
    }

    private static int teleportToSlot(CommandContext<CommandSourceStack> ctx, String slotId) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cPlayer required")));
            return 0;
        }

        NexusHubManager.INSTANCE.initialize(server);
        ZoneSlotRegistry registry = ZoneSlotRegistry.get(server);
        Optional<ZoneSlot> slotOpt = registry.getSlot(Objects.requireNonNull(slotId));
        if (slotOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cSlot not found: " + slotId)));
            return 0;
        }

        ServerLevel nexusLevel = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION));
        if (nexusLevel == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cNexus dimension not available.")));
            return 0;
        }

        BlockPos center = slotOpt.get().bounds().floorCenter();
        player.teleportTo(nexusLevel,
            center.getX() + 0.5,
            center.getY() + 1,
            center.getZ() + 0.5,
            player.getYRot(),
            player.getXRot());

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§aTeleported to slot '" + slotId + "'")), false);
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
        NexusHubManager.INSTANCE.initialize(server);
        return ZoneRegistry.get(server);
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
        NexusHubManager.INSTANCE.initialize(server);
        NexusHubManager.HubStatus hubStatus = NexusHubManager.INSTANCE.getStatus(server);

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
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Slots: §f" + hubStatus.totalSlots()
                + " §7Linked: §f" + hubStatus.linkedSlots()
                + " §7Empty: §f" + hubStatus.emptySlots())), false);

        return 1;
    }

    private static CompletableFuture<Suggestions> suggestSlotIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) {
            return builder.buildFuture();
        }

        NexusHubManager.INSTANCE.initialize(server);
        ZoneSlotRegistry registry = ZoneSlotRegistry.get(server);
        for (ZoneSlot slot : registry.getAllSlots()) {
            if (slot.slotId().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(slot.slotId());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestLinkedSlotIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) {
            return builder.buildFuture();
        }

        NexusHubManager.INSTANCE.initialize(server);
        ZoneSlotRegistry registry = ZoneSlotRegistry.get(server);
        for (ZoneSlot slot : registry.getLinkedSlots()) {
            if (slot.slotId().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(slot.slotId());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAreaNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) {
            return builder.buildFuture();
        }

        AreaRegistry registry = AreaRegistry.get(server);
        String remaining = builder.getRemainingLowerCase();
        for (AreaDefinition area : registry.getAllAreas()) {
            if (area.name().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(area.name());
            }
        }
        return builder.buildFuture();
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

    private static String formatBounds(ZoneSlot slot) {
        var b = slot.bounds();
        return String.format("(%d,%d,%d) to (%d,%d,%d) [%dx%dx%d]",
            b.minX(), b.minY(), b.minZ(),
            b.maxX(), b.maxY(), b.maxZ(),
            b.width(), b.height(), b.length());
    }
}
