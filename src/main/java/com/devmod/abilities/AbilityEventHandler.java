package com.devmod.abilities;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.DevMod;
import com.devmod.telemetry.player.AbilityTelemetryService;

@EventBusSubscriber(modid = DevMod.MODID)
public class AbilityEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Server tick - update all ability systems for each player.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Skip creative/spectator players
            if (player.isCreative() || player.isSpectator()) continue;

            // Tick all ability systems
            StaminaSystem.INSTANCE.tick(player);
            DashAbilitySystem.INSTANCE.tick(player);
            DodgeAbilitySystem.INSTANCE.tick(player);
        }
    }

    /**
     * Damage event - check for dodge i-frames to negate damage.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        // Get damage source for telemetry
        String damageSource = event.getSource() != null ? event.getSource().getMsgId() : "unknown";

        // Check if player has dodge i-frames active
        if (DodgeAbilitySystem.INSTANCE.onDamageDuringDodge(player, event.getAmount(), damageSource)) {
            // Perfect dodge - cancel the damage
            event.setCanceled(true);
            LOGGER.debug("[Ability] Damage negated by dodge i-frames for {}", player.getName().getString());
        }
    }

    /**
     * Player disconnect - cleanup ability data and export telemetry.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var playerId = player.getUUID();

        // Export telemetry session summary before cleanup
        AbilityTelemetryService.INSTANCE.cleanupPlayer(playerId);

        // Cleanup ability systems
        StaminaSystem.INSTANCE.cleanupPlayer(playerId);
        DashAbilitySystem.INSTANCE.cleanupPlayer(playerId);
        DodgeAbilitySystem.INSTANCE.cleanupPlayer(playerId);

        LOGGER.debug("[Ability] Cleaned up ability data for {}", player.getName().getString());
    }

    /**
     * Player login - initialize ability data.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Initialize with full stamina
        StaminaSystem.INSTANCE.fillStamina(player.getUUID());

        LOGGER.debug("[Ability] Initialized ability data for {}", player.getName().getString());
    }
}
