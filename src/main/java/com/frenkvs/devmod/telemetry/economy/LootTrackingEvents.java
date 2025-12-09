package com.frenkvs.devmod.telemetry.economy;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.telemetry.TelemetryService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Event handlers for loot and economy tracking.
 *
 * Hooks into:
 * - Mob drop events (LivingDropsEvent)
 * - Item crafting events (PlayerEvent.ItemCraftedEvent)
 * - Item smelting events (PlayerEvent.ItemSmeltedEvent)
 *
 * Note: Item pickup tracking is done via tick-based monitoring in TelemetryEvents
 * because NeoForge doesn't have a direct ItemPickupEvent.
 *
 * Logs telemetry via EconomyMetricsService.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class LootTrackingEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Track mob kills and drops when mobs die.
     * This captures kill counts AND all items dropped by mobs on death.
     * The LivingDropsEvent fires AFTER LivingDeathEvent, so we handle both here.
     */
    @SubscribeEvent
    public static void onMobDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player) return; // Don't track player drops

        String mobType = entity.getType().getDescriptionId();
        BlockPos pos = entity.blockPosition();
        ServerLevel level = (ServerLevel) entity.level();

        Collection<ItemEntity> drops = event.getDrops();
        boolean hasLoot = !drops.isEmpty();

        // Record the kill first (for accurate drop percentage calculation)
        var killRecord = EconomyMetricsService.INSTANCE.recordMobKill(mobType, hasLoot);
        if (killRecord != null) {
            TelemetryService.INSTANCE.appendEconomyLine(killRecord.toJson());
            LOGGER.debug("[Economy] Mob killed: {} (kill #{}, hasLoot={})",
                    mobType, killRecord.totalKills(), hasLoot);
        }

        // Then record each individual drop AND collect items for per-item stats
        if (hasLoot) {
            List<ItemStack> droppedItems = new ArrayList<>();

            for (ItemEntity itemEntity : drops) {
                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) continue;

                droppedItems.add(stack.copy());

                var dropRecord = EconomyMetricsService.INSTANCE.recordMobDrop(level, mobType, stack, pos);
                if (dropRecord != null) {
                    TelemetryService.INSTANCE.appendEconomyLine(dropRecord.toJson());
                    LOGGER.debug("[Economy] Mob drop: {} dropped {}x {} at {}",
                            mobType, stack.getCount(), stack.getItem(), pos);
                }
            }

            // Record aggregated item drop stats for this kill
            EconomyMetricsService.INSTANCE.recordMobDropItems(mobType, droppedItems);
        }
    }

    /**
     * Track mob deaths that don't drop items (for accurate kill counting).
     * LivingDeathEvent fires before LivingDropsEvent, so we use this as a fallback
     * for mobs that die without dropping anything (e.g., from void, kill command, etc.)
     *
     * Note: Most kills are tracked via onMobDrops above. This catches edge cases.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player) return;

        // Check if this death will trigger LivingDropsEvent
        // If source is void damage or similar, LivingDropsEvent might not fire
        // For now, we track all kills in onMobDrops since it always fires after death
        // This event is here as documentation and for future edge-case handling
    }

    /**
     * Track when player crafts items (item creation).
     * Crafted items count as item acquisition.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;

        // Track as item acquisition (crafted items are also "acquired")
        var record = EconomyMetricsService.INSTANCE.recordItemPickup(
                player,
                crafted,
                player.blockPosition()
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendEconomyLine(record.toJson());
            LOGGER.debug("[Economy] Item crafted: {} crafted {}x {}",
                    player.getGameProfile().getName(), crafted.getCount(), crafted.getItem());
        }
    }

    /**
     * Track when player smelts items.
     * Smelted items count as item acquisition.
     */
    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        ItemStack smelted = event.getSmelting();
        if (smelted.isEmpty()) return;

        // Track as item acquisition
        var record = EconomyMetricsService.INSTANCE.recordItemPickup(
                player,
                smelted,
                player.blockPosition()
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendEconomyLine(record.toJson());
            LOGGER.debug("[Economy] Item smelted: {} smelted {}x {}",
                    player.getGameProfile().getName(), smelted.getCount(), smelted.getItem());
        }
    }

    /**
     * Cleanup when player logs out.
     * Removes player-specific tracking data to prevent memory leaks.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EconomyMetricsService.INSTANCE.cleanupPlayer(player.getUUID());
    }
}
