package com.devmod.runtime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
    private static final Map<UUID, Long> BUILD_WARNINGS = new HashMap<>();
    private static final long BUILD_WARNING_COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> UI_INTERACTIONS = new HashMap<>();
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
            BUILD_WARNINGS.remove(player.getUUID());
            UI_INTERACTIONS.remove(player.getUUID());
            NexusZoneTracker.clear(player.getUUID());
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
        if (NexusZoneLayout.isBuildAllowed(event.getPos(), NexusDimensionManager.getHubOrigin())) {
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
        if (NexusZoneLayout.isBuildAllowed(event.getPos(), NexusDimensionManager.getHubOrigin())) {
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

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        NexusZoneLayout.Zone zone = NexusZoneLayout.resolveZone(pos, NexusDimensionManager.getHubOrigin());

        if (zone == NexusZoneLayout.Zone.UI && isUiScreenBlock(state)) {
            if (!consumeUiCooldown(player)) {
                return;
            }
            sendUi(player, NexusUiPayload.UiAction.TESTING_HUB);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (zone == NexusZoneLayout.Zone.TELEMETRY && state.is(Blocks.LECTERN)) {
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

        if (state.is(Blocks.RESPAWN_ANCHOR)) {
            if (!consumeUiCooldown(player)) {
                return;
            }
            boolean returned = NexusDimensionManager.INSTANCE.teleportPlayerToReturn(player);
            if (!returned) {
                player.displayClientMessage(Component.literal("No return point saved."), true);
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
        PacketDistributor.sendToPlayer(player, new NexusUiPayload(action));
    }

    private static boolean isUiScreenBlock(BlockState state) {
        return state.is(Blocks.BLUE_STAINED_GLASS) || state.is(Blocks.LIGHT_BLUE_STAINED_GLASS);
    }

    private static boolean isLogDeskLectern(Level level, BlockPos pos) {
        return level.getBlockState(pos.north()).is(Blocks.BOOKSHELF)
            || level.getBlockState(pos.south()).is(Blocks.BOOKSHELF)
            || level.getBlockState(pos.east()).is(Blocks.BOOKSHELF)
            || level.getBlockState(pos.west()).is(Blocks.BOOKSHELF);
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

    /**
     * Handle player interaction with entities in the Nexus dimension.
     * Handles right-click on the Nexus avatar and portal pedestals.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // Only handle on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Only handle main hand to avoid double-firing
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        // Only handle in Nexus dimension
        if (!event.getLevel().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if target is the Nexus avatar
        var target = Objects.requireNonNull(event.getTarget(), "event.getTarget");
        if (target.getTags().contains(NexusAvatarManager.AVATAR_TAG)) {
            NexusAvatarManager.handleInteraction(player, target);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // Check if target is a portal pedestal
        if (target.getTags().contains(NexusPortalManager.PORTAL_TAG)) {
            if (NexusPortalManager.INSTANCE.handleInteraction(player, target)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
    }
}
