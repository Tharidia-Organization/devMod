package com.devmod.runtime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.config.Config;
import com.devmod.runtime.network.NexusUiPayload;

@EventBusSubscriber(modid = DevMod.MODID)
public class NexusEventHandler {
    // Thread-safe: accessed from event handlers which may run on different threads
    private static final Map<UUID, Long> BUILD_WARNINGS = new ConcurrentHashMap<>();
    private static final long BUILD_WARNING_COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> UI_INTERACTIONS = new ConcurrentHashMap<>();
    private static final long UI_INTERACT_COOLDOWN_TICKS = 10;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        NexusCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        NexusDimensionManager.INSTANCE.ensureNexusDimension(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        NexusDimensionManager.INSTANCE.shutdown(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        NexusDimensionManager.INSTANCE.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Config.NEXUS_ENABLED.get()
                && player.level().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
                NexusDimensionManager.INSTANCE.teleportPlayerToSpawn(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Config.NEXUS_ENABLED.get()
                && event.getTo().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
                // Check if player arrived via custom portal - skip spawn override
                String customPortalTag = com.devmod.portal.block.CustomPortalBlock.TAG_CUSTOM_PORTAL_TELEPORT;
                if (player.getPersistentData().getBoolean(customPortalTag)) {
                    player.getPersistentData().remove(customPortalTag);
                    com.devmod.DevMod.LOGGER.info("[Nexus] Player {} arrived via custom portal, skipping spawn override",
                        player.getName().getString());
                    playEntryCue(player);
                    return;
                }
                NexusDimensionManager.INSTANCE.teleportPlayerToSpawn(player);
                playEntryCue(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Config.NEXUS_ENABLED.get()
                && player.level().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
                NexusDimensionManager.INSTANCE.teleportPlayerToSpawn(player);
                playEntryCue(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            java.util.UUID playerId = player.getUUID();
            BUILD_WARNINGS.remove(playerId);
            UI_INTERACTIONS.remove(playerId);
            // Use new data-driven ZoneTracker
            if (playerId != null) {
                com.devmod.zone.runtime.ZoneTracker.INSTANCE.clear(playerId);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (!Config.NEXUS_ENABLED.get()) {
            return;
        }
        if (!player.level().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
            return;
        }
        if (player.hasPermissions(2)) {
            return;
        }
        if (Config.NEXUS_ALLOW_BUILD_ANYWHERE.get()) {
            return;
        }
        if (!Config.NEXUS_RESTRICT_BUILDING.get()) {
            return;
        }
        // Use data-driven zone system for build permission check
        Optional<com.devmod.zone.data.ZoneDefinition> zoneOpt =
            com.devmod.zone.runtime.ZoneResolver.INSTANCE.resolve(Objects.requireNonNull(player.serverLevel()), Objects.requireNonNull(event.getPos()));
        if (zoneOpt.map(com.devmod.zone.data.ZoneDefinition::allowBuilding).orElse(false)) {
            return;
        }
        event.setCanceled(true);
        warnBuildBlocked(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (!Config.NEXUS_ENABLED.get()) {
            return;
        }
        if (!player.level().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
            return;
        }
        if (player.hasPermissions(2)) {
            return;
        }
        if (Config.NEXUS_ALLOW_BUILD_ANYWHERE.get()) {
            return;
        }
        if (!Config.NEXUS_RESTRICT_BUILDING.get()) {
            return;
        }
        // Use data-driven zone system for build permission check
        Optional<com.devmod.zone.data.ZoneDefinition> zoneOpt =
            com.devmod.zone.runtime.ZoneResolver.INSTANCE.resolve(Objects.requireNonNull(player.serverLevel()), Objects.requireNonNull(event.getPos()));
        if (zoneOpt.map(com.devmod.zone.data.ZoneDefinition::allowBuilding).orElse(false)) {
            return;
        }
        event.setCanceled(true);
        warnBuildBlocked(player);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!Config.NEXUS_ENABLED.get()) {
            return;
        }
        if (!player.level().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
            return;
        }

        BlockPos pos = Objects.requireNonNull(event.getPos(), "pos");
        BlockState state = Objects.requireNonNull(event.getLevel().getBlockState(pos), "state");
        var lectern = Objects.requireNonNull(Blocks.LECTERN, "Blocks.LECTERN");
        var respawnAnchor = Objects.requireNonNull(Blocks.RESPAWN_ANCHOR, "Blocks.RESPAWN_ANCHOR");

        // Use data-driven zone system
        Optional<com.devmod.zone.data.ZoneDefinition> zoneOpt =
            com.devmod.zone.runtime.ZoneResolver.INSTANCE.resolve(Objects.requireNonNull(player.serverLevel()), pos);
        String zoneId = zoneOpt.map(com.devmod.zone.data.ZoneDefinition::zoneId).orElse("");

        if ("ui".equals(zoneId) && isUiScreenBlock(state)) {
            if (!consumeUiCooldown(player)) {
                return;
            }
            sendUi(player, NexusUiPayload.UiAction.TESTING_HUB);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if ("telemetry".equals(zoneId) && state.is(lectern)) {
            if (!consumeUiCooldown(player)) {
                return;
            }
            if (isLogDeskLectern(event.getLevel(), pos)) {
                sendUi(player, NexusUiPayload.UiAction.LOG_VIEWER);
            } else {
                sendUi(player, NexusUiPayload.UiAction.TELEMETRY_DASHBOARD);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }

        if (state.is(respawnAnchor)) {
            if (!consumeUiCooldown(player)) {
                return;
            }
            boolean returned = NexusDimensionManager.INSTANCE.teleportPlayerToReturn(player);
            if (!returned) {
                Component message = Objects.requireNonNull(Component.literal("No return point saved."), "noReturnMessage");
                player.displayClientMessage(message, true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void playEntryCue(net.minecraft.server.level.ServerPlayer player) {
        if (Config.NEXUS_ENTRY_SOUND.get()) {
            player.level().playSound(null, Objects.requireNonNull(player.blockPosition(), "player.blockPosition"),
                Objects.requireNonNull(SoundEvents.BEACON_ACTIVATE, "SoundEvents.BEACON_ACTIVATE"), SoundSource.AMBIENT, 0.6f, 1.15f);
        }
        if (Config.NEXUS_ENTRY_MESSAGE.get()) {
            player.displayClientMessage(Objects.requireNonNull(Component.literal(
                "NEXUS online. /devmod nexus help | tp <zone> | return | bug <msg> | pads: Red=Combat, Orange=Arena, Blue=UI, Green=Telemetry, Yellow=Showcase, Purple=Integration, Cyan=Sandbox, Iron=Mechanics | overview: /devmod nexus tp overview"
            ), "entryMessage"), true);
        }
    }

    private static void warnBuildBlocked(ServerPlayer player) {
        long now = player.level().getGameTime();
        long last = BUILD_WARNINGS.getOrDefault(player.getUUID(), 0L);
        if (now - last < BUILD_WARNING_COOLDOWN_TICKS) {
            return;
        }
        BUILD_WARNINGS.put(player.getUUID(), now);
        player.displayClientMessage(Objects.requireNonNull(Component.literal(
            "Sandbox only: use /devmod nexus tp sandbox to build."
        ), "buildWarning"), true);
    }

    private static void sendUi(ServerPlayer player, NexusUiPayload.UiAction action) {
        PacketDistributor.sendToPlayer(Objects.requireNonNull(player, "player"), new NexusUiPayload(action));
    }

    private static boolean isUiScreenBlock(BlockState state) {
        var blueGlass = Objects.requireNonNull(Blocks.BLUE_STAINED_GLASS, "Blocks.BLUE_STAINED_GLASS");
        var lightBlueGlass = Objects.requireNonNull(Blocks.LIGHT_BLUE_STAINED_GLASS, "Blocks.LIGHT_BLUE_STAINED_GLASS");
        return state.is(blueGlass) || state.is(lightBlueGlass);
    }

    private static boolean isLogDeskLectern(Level level, BlockPos pos) {
        BlockPos north = Objects.requireNonNull(pos.north(), "pos.north");
        BlockPos south = Objects.requireNonNull(pos.south(), "pos.south");
        BlockPos east = Objects.requireNonNull(pos.east(), "pos.east");
        BlockPos west = Objects.requireNonNull(pos.west(), "pos.west");
        var bookshelf = Objects.requireNonNull(Blocks.BOOKSHELF, "Blocks.BOOKSHELF");
        return level.getBlockState(north).is(bookshelf)
            || level.getBlockState(south).is(bookshelf)
            || level.getBlockState(east).is(bookshelf)
            || level.getBlockState(west).is(bookshelf);
    }

    private static boolean consumeUiCooldown(ServerPlayer player) {
        long now = player.level().getGameTime();
        long last = UI_INTERACTIONS.getOrDefault(player.getUUID(), 0L);
        if (now - last < UI_INTERACT_COOLDOWN_TICKS) {
            return false;
        }
        UI_INTERACTIONS.put(player.getUUID(), now);
        return true;
    }

}
