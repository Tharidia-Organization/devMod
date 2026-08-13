package com.devmod.runtime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.DevMod;

@EventBusSubscriber(modid = DevMod.MODID)
public final class RiftStampEventHandler {
    private static final Map<UUID, Long> WARNINGS = new HashMap<>();
    private static final long WARNING_COOLDOWN_TICKS = 20;

    private RiftStampEventHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RiftStampManager.INSTANCE.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RiftStampManager.INSTANCE.shutdown();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!RiftStampManager.INSTANCE.isProtected(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        warn(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!RiftStampManager.INSTANCE.isProtected(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        warn(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
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
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!RiftStampManager.INSTANCE.isProtected(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        warn(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        // FluidPlaceBlockEvent doesn't provide entity - just check protection by position
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!RiftStampManager.INSTANCE.isProtected(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        // Cannot warn specific player - FluidPlaceBlockEvent has no entity info
    }

    private static void warn(ServerPlayer player) {
        long now = player.level().getGameTime();
        long last = WARNINGS.getOrDefault(player.getUUID(), 0L);
        if (now - last < WARNING_COOLDOWN_TICKS) {
            return;
        }
        WARNINGS.put(player.getUUID(), now);
        player.displayClientMessage(Objects.requireNonNull(
            Component.literal("RiftStamp area locked until collapse."), "message"), true);
    }
}
