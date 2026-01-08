package com.devmod.events;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;

import com.devmod.DevMod;
import com.devmod.runtime.RecoverySystem;

@EventBusSubscriber(modid = DevMod.MODID)
public final class LoginProtectionEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginProtectionEvents.class);
    private static final long DEFAULT_PROTECTION_MS = 10_000;
    private static final long RECOVERY_PROTECTION_MS = 20_000;
    private static final Map<UUID, Long> protectionUntilMs = new ConcurrentHashMap<>();

    private LoginProtectionEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        long protectionMs = DEFAULT_PROTECTION_MS;
        if (RecoverySystem.INSTANCE.hasSnapshot(player.getUUID()) || isInInstanceDimension(player)) {
            protectionMs = RECOVERY_PROTECTION_MS;
        }
        long until = System.currentTimeMillis() + protectionMs;
        protectionUntilMs.put(player.getUUID(), until);

        LOGGER.debug("[LoginProtection] Enabled for {} ({} ms)",
            player.getName().getString(), protectionMs);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            protectionUntilMs.remove(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Long until = protectionUntilMs.get(player.getUUID());
        if (until == null) {
            return;
        }

        if (System.currentTimeMillis() > until) {
            protectionUntilMs.remove(player.getUUID());
            return;
        }

        if (event.getNewDamage() > 0f) {
            event.setNewDamage(0f);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Long until = protectionUntilMs.get(player.getUUID());
        if (until == null || System.currentTimeMillis() > until) {
            return;
        }

        event.setCanceled(true);
        player.setHealth(Math.max(1.0f, player.getMaxHealth()));
    }

    private static boolean isInInstanceDimension(ServerPlayer player) {
        if (player == null || player.level() == null) {
            return false;
        }
        ResourceLocation id = player.level().dimension().location();
        if (id == null) {
            return false;
        }
        return "devmod".equals(id.getNamespace()) && id.getPath().startsWith("instance_");
    }
}
