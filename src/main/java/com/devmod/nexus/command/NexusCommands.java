package com.devmod.nexus.command;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.nexus.data.SlotType;
import com.devmod.nexus.data.ZoneSlot;
import com.devmod.nexus.data.ZoneSlotRegistry;
import com.devmod.nexus.runtime.NexusHubManager;
import com.devmod.runtime.NexusDimensionManager;

/**
 * CLI commands for Nexus hub management.
 * Provides commands for slot management, status, and teleportation.
 */
public final class NexusCommands {

    private static final int ITEMS_PER_PAGE = 10;

    private NexusCommands() {}

    /**
     * Registers all nexus commands under /devmod nexus.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("nexus")
                    .requires(src -> src.hasPermission(2))
                    .then(statusCommand())
                    .then(slotCommands())
                    .then(teleportCommand())
                    .then(rebuildCommand())
                    .executes(NexusCommands::showHelp)
                )
        );
    }

    // ========================================================================
    // Help
    // ========================================================================

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("=== DevMod Nexus Commands ===").withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("/devmod nexus status").withStyle(ChatFormatting.YELLOW)
                .append(Objects.requireNonNull(Component.literal(" - Show hub status").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("/devmod nexus slot list [page]").withStyle(ChatFormatting.YELLOW)
                .append(Objects.requireNonNull(Component.literal(" - List all slots").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("/devmod nexus slot info <slotId>").withStyle(ChatFormatting.YELLOW)
                .append(Objects.requireNonNull(Component.literal(" - Show slot details").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("/devmod nexus slot link <slotId> <areaName>").withStyle(ChatFormatting.YELLOW)
                .append(Objects.requireNonNull(Component.literal(" - Link area to slot").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("/devmod nexus slot unlink <slotId>").withStyle(ChatFormatting.YELLOW)
                .append(Objects.requireNonNull(Component.literal(" - Unlink area from slot").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("/devmod nexus teleport <slotId>").withStyle(ChatFormatting.YELLOW)
                .append(Objects.requireNonNull(Component.literal(" - Teleport to slot").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("/devmod nexus rebuild").withStyle(ChatFormatting.YELLOW)
                .append(Objects.requireNonNull(Component.literal(" - Rebuild hub foundation").withStyle(ChatFormatting.GRAY)))), false);
        return 1;
    }

    // ========================================================================
    // Status Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> statusCommand() {
        return Commands.literal("status")
            .executes(NexusCommands::showStatus);
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();

        NexusHubManager.HubStatus status = NexusHubManager.INSTANCE.getStatus(Objects.requireNonNull(server));

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("=== Nexus Hub Status ===").withStyle(ChatFormatting.GOLD)), false);

        ChatFormatting sessionColor = status.sessionInitialized() ? ChatFormatting.GREEN : ChatFormatting.RED;
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Session: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal(status.sessionInitialized() ? "Ready" : "Not Ready")
                    .withStyle(sessionColor)))), false);

        ChatFormatting slotsColor = status.slotsInitialized() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Slots Initialized: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.valueOf(status.slotsInitialized())))
                    .withStyle(slotsColor)))), false);

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Total Slots: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.valueOf(status.totalSlots())))
                    .withStyle(ChatFormatting.WHITE)))), false);

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Linked: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.valueOf(status.linkedSlots())))
                    .withStyle(ChatFormatting.GREEN)))
                .append(Objects.requireNonNull(Component.literal(" / Empty: ").withStyle(ChatFormatting.GRAY)))
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.valueOf(status.emptySlots())))
                    .withStyle(ChatFormatting.YELLOW)))), false);

        return 1;
    }

    // ========================================================================
    // Slot Commands
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> slotCommands() {
        return Commands.literal("slot")
            .then(slotListCommand())
            .then(slotInfoCommand())
            .then(slotLinkCommand())
            .then(slotUnlinkCommand());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> slotListCommand() {
        return Commands.literal("list")
            .executes(ctx -> listSlots(ctx, 1));
    }

    private static int listSlots(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();

        ZoneSlotRegistry registry = ZoneSlotRegistry.get(Objects.requireNonNull(server));
        Collection<ZoneSlot> allSlots = registry.getAllSlots();
        int totalSlots = allSlots.size();

        if (totalSlots == 0) {
            source.sendSuccess(() -> Component.literal("No slots defined. Run /devmod nexus rebuild to initialize.")
                .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        int totalPages = (totalSlots + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        int safePage = Math.max(1, Math.min(page, totalPages));
        int skip = (safePage - 1) * ITEMS_PER_PAGE;

        List<ZoneSlot> pageSlots = allSlots.stream()
            .sorted((a, b) -> a.slotId().compareTo(b.slotId()))
            .skip(skip)
            .limit(ITEMS_PER_PAGE)
            .toList();

        source.sendSuccess(() -> Component.literal("=== Nexus Slots (Page " + safePage + "/" + totalPages + ") ===")
            .withStyle(ChatFormatting.GOLD), false);

        for (ZoneSlot slot : pageSlots) {
            ChatFormatting typeColor = getTypeColor(slot.type());
            String status = slot.hasLinkedArea() ? "[LINKED]" : "[EMPTY]";
            ChatFormatting statusColor = slot.hasLinkedArea() ? ChatFormatting.GREEN : ChatFormatting.GRAY;

            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("  " + slot.slotId()).withStyle(ChatFormatting.WHITE)
                    .append(Objects.requireNonNull(Component.literal(" (" + slot.type().name() + ") ")
                        .withStyle(Objects.requireNonNull(typeColor))))
                    .append(Objects.requireNonNull(Component.literal(status)
                        .withStyle(statusColor)))), false);
        }

        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> slotInfoCommand() {
        return Commands.literal("info")
            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word()))
                .suggests(NexusCommands::suggestSlotIds)
                .executes(ctx -> showSlotInfo(ctx, StringArgumentType.getString(ctx, "slotId"))));
    }

    private static int showSlotInfo(CommandContext<CommandSourceStack> ctx, String slotId) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();

        ZoneSlotRegistry registry = ZoneSlotRegistry.get(Objects.requireNonNull(server));
        Optional<ZoneSlot> slotOpt = registry.getSlot(Objects.requireNonNull(slotId));

        if (slotOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Slot not found: " + slotId)));
            return 0;
        }

        ZoneSlot slot = slotOpt.get();

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("=== Slot: " + slot.slotId() + " ===").withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Display Name: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(slot.displayName())).withStyle(ChatFormatting.WHITE)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Type: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(slot.type().name())).withStyle(Objects.requireNonNull(getTypeColor(slot.type())))))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Bounds: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(formatBounds(slot))).withStyle(ChatFormatting.WHITE)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Portal Color: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(slot.portalColor().name())).withStyle(ChatFormatting.WHITE)))), false);

        if (slot.hasLinkedArea()) {
            AreaRegistry areaRegistry = AreaRegistry.get(server);
            Optional<AreaDefinition> areaOpt = areaRegistry.getArea(Objects.requireNonNull(slot.linkedAreaId()));
            String areaName = areaOpt.map(AreaDefinition::name).orElse("Unknown");
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Linked Area: ").withStyle(ChatFormatting.GRAY)
                    .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(areaName)).withStyle(ChatFormatting.GREEN)))), false);
        } else {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Linked Area: ").withStyle(ChatFormatting.GRAY)
                    .append(Objects.requireNonNull(Component.literal("None").withStyle(ChatFormatting.YELLOW)))), false);
        }

        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> slotLinkCommand() {
        return Commands.literal("link")
            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word()))
                .suggests(NexusCommands::suggestSlotIds)
                .then(Commands.argument("areaName", Objects.requireNonNull(StringArgumentType.greedyString()))
                    .suggests(NexusCommands::suggestAreaNames)
                    .executes(ctx -> linkSlot(ctx,
                        StringArgumentType.getString(ctx, "slotId"),
                        StringArgumentType.getString(ctx, "areaName")))));
    }

    private static int linkSlot(CommandContext<CommandSourceStack> ctx, String slotId, String areaName) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();

        // Find area by name
        AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(server));
        Optional<AreaDefinition> areaOpt = areaRegistry.getAllAreas().stream()
            .filter(a -> a.name().equalsIgnoreCase(areaName))
            .findFirst();

        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + areaName)));
            return 0;
        }

        UUID areaId = Objects.requireNonNull(areaOpt.get().id());

        boolean success = NexusHubManager.INSTANCE.linkAreaToSlot(server, Objects.requireNonNull(slotId), areaId);

        if (success) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Linked area '" + areaName + "' to slot '" + slotId + "'")
                    .withStyle(ChatFormatting.GREEN)), true);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to link area to slot. Check logs for details.")));
            return 0;
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> slotUnlinkCommand() {
        return Commands.literal("unlink")
            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word()))
                .suggests(NexusCommands::suggestLinkedSlotIds)
                .executes(ctx -> unlinkSlot(ctx, StringArgumentType.getString(ctx, "slotId"))));
    }

    private static int unlinkSlot(CommandContext<CommandSourceStack> ctx, String slotId) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();

        boolean success = NexusHubManager.INSTANCE.unlinkAreaFromSlot(Objects.requireNonNull(server), Objects.requireNonNull(slotId));

        if (success) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Unlinked area from slot '" + slotId + "'")
                    .withStyle(ChatFormatting.GREEN)), true);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to unlink slot. Check logs for details.")));
            return 0;
        }
    }

    // ========================================================================
    // Teleport Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> teleportCommand() {
        return Commands.literal("teleport")
            .then(Commands.argument("slotId", Objects.requireNonNull(StringArgumentType.word()))
                .suggests(NexusCommands::suggestSlotIds)
                .executes(ctx -> teleportToSlot(ctx, StringArgumentType.getString(ctx, "slotId"))));
    }

    private static int teleportToSlot(CommandContext<CommandSourceStack> ctx, String slotId) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Objects.requireNonNull(Component.literal("This command requires a player.")));
            return 0;
        }

        ZoneSlotRegistry registry = ZoneSlotRegistry.get(Objects.requireNonNull(server));
        Optional<ZoneSlot> slotOpt = registry.getSlot(Objects.requireNonNull(slotId));

        if (slotOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Slot not found: " + slotId)));
            return 0;
        }

        ZoneSlot slot = slotOpt.get();
        ServerLevel nexusLevel = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION));

        if (nexusLevel == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Nexus dimension not available.")));
            return 0;
        }

        // Teleport to slot center
        BlockPos center = slot.bounds().floorCenter();
        player.teleportTo(nexusLevel,
            center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
            player.getYRot(), player.getXRot());

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Teleported to slot '" + slotId + "'").withStyle(ChatFormatting.GREEN)), false);
        return 1;
    }

    // ========================================================================
    // Rebuild Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> rebuildCommand() {
        return Commands.literal("rebuild")
            .executes(ctx -> rebuildFoundation(ctx, false))
            .then(Commands.literal("--force")
                .executes(ctx -> rebuildFoundation(ctx, true)));
    }

    private static int rebuildFoundation(CommandContext<CommandSourceStack> ctx, boolean force) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();

        ServerLevel nexusLevel = Objects.requireNonNull(server).getLevel(
            Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION));

        if (nexusLevel == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Nexus dimension not available.")));
            return 0;
        }

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Building Nexus foundation...").withStyle(ChatFormatting.YELLOW)), true);

        BlockPos origin = Objects.requireNonNull(NexusDimensionManager.getHubOrigin());
        boolean built = NexusHubManager.INSTANCE.buildFoundation(nexusLevel, origin, force);

        if (built) {
            // Initialize slots if not already done
            NexusHubManager.INSTANCE.initialize(server);

            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Foundation build complete!").withStyle(ChatFormatting.GREEN)), true);
        } else {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Foundation already exists. Use --force to rebuild.")
                    .withStyle(ChatFormatting.YELLOW)), false);
        }

        return 1;
    }

    // ========================================================================
    // Suggestions
    // ========================================================================

    private static CompletableFuture<Suggestions> suggestSlotIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        var server = ctx.getSource().getServer();
        if (server == null) {
            return builder.buildFuture();
        }

        ZoneSlotRegistry registry = ZoneSlotRegistry.get(server);
        for (ZoneSlot slot : registry.getAllSlots()) {
            if (slot.slotId().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(slot.slotId());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestLinkedSlotIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        var server = ctx.getSource().getServer();
        if (server == null) {
            return builder.buildFuture();
        }

        ZoneSlotRegistry registry = ZoneSlotRegistry.get(server);
        for (ZoneSlot slot : registry.getLinkedSlots()) {
            if (slot.slotId().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(slot.slotId());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAreaNames(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        var server = ctx.getSource().getServer();
        if (server == null) {
            return builder.buildFuture();
        }

        AreaRegistry registry = AreaRegistry.get(server);
        for (AreaDefinition area : registry.getAllAreas()) {
            if (area.name().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(area.name());
            }
        }
        return builder.buildFuture();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static ChatFormatting getTypeColor(SlotType type) {
        return switch (type) {
            case FIXED -> ChatFormatting.RED;
            case EDITABLE -> ChatFormatting.GREEN;
            case TEST_AREA -> ChatFormatting.AQUA;
            case RESTRICTED -> ChatFormatting.GOLD;
        };
    }

    private static String formatBounds(ZoneSlot slot) {
        var b = slot.bounds();
        return String.format("(%d,%d,%d) to (%d,%d,%d) [%dx%dx%d]",
            b.minX(), b.minY(), b.minZ(),
            b.maxX(), b.maxY(), b.maxZ(),
            b.width(), b.height(), b.length());
    }
}
