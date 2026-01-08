package com.devmod.runtime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.devmod.DevMod;
import com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat;
import com.devmod.config.Config;

@EventBusSubscriber(modid = DevMod.MODID)
public class NexusDummyFeedback {
    private static final Map<UUID, Long> LAST_FEEDBACK = new HashMap<>();

    @SubscribeEvent
    public static void onDummyDamage(LivingDamageEvent.Post event) {
        if (!Config.NEXUS_DUMMY_FEEDBACK.get()) {
            return;
        }
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!DummmmmmyCompat.isDummy(event.getEntity())) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.level().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
            return;
        }

        long now = player.level().getGameTime();
        long last = LAST_FEEDBACK.getOrDefault(player.getUUID(), 0L);
        int cooldown = Math.max(0, Config.NEXUS_DUMMY_FEEDBACK_COOLDOWN.get());
        if (now - last < cooldown) {
            return;
        }
        LAST_FEEDBACK.put(player.getUUID(), now);

        float hit = event.getNewDamage();
        Map<String, Object> stats = DummmmmmyCompat.getDamageStats(event.getEntity());
        float total = readFloat(stats, "totalDamage", -1f);
        float dps = readFloat(stats, "dps", -1f);

        StringBuilder msg = new StringBuilder("Dummy hit ").append(format(hit));
        if (dps >= 0) {
            msg.append(" | DPS ").append(format(dps));
        }
        if (total >= 0) {
            msg.append(" | Total ").append(format(total));
        }
        player.displayClientMessage(Component.literal(msg.toString()), true);
    }

    private static float readFloat(Map<String, Object> stats, String key, float fallback) {
        Object value = stats.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return fallback;
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
