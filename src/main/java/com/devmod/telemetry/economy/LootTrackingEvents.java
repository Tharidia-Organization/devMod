package com.devmod.telemetry.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.devmod.DevMod;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.devmod.telemetry.room.RoomService;

import com.mojang.logging.LogUtils;

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
        String room = RoomService.INSTANCE.resolveRoom(level, pos);

        Collection<ItemEntity> drops = event.getDrops();
        boolean hasLoot = !drops.isEmpty();

        // Record the kill first (for accurate drop percentage calculation)
        var killRecord = EconomyMetricsService.INSTANCE.recordMobKill(mobType, hasLoot);
        if (killRecord != null) {
            TelemetryService.INSTANCE.appendEconomyLine(killRecord.toJson());
            // DuckDB: economy_mob_kills
            DuckDBTelemetryService.INSTANCE.logMobKill(mobType, killRecord.totalKills(), hasLoot);
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
                String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()))).toString();

                var dropRecord = EconomyMetricsService.INSTANCE.recordMobDrop(level, mobType, stack, pos);
                if (dropRecord != null) {
                    TelemetryService.INSTANCE.appendEconomyLine(dropRecord.toJson());
                    // DuckDB: economy_mob_drops
                    DuckDBTelemetryService.INSTANCE.logMobDrop(
                        mobType, room, itemId, stack.getCount(),
                        pos.getX(), pos.getY(), pos.getZ()
                    );
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

    // ===== NEW ECONOMY HOOKS =====

    /**
     * Track item pickup from ground.
     * This captures items picked up by walking over them (not crafting/smelting).
     */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        ItemEntity itemEntity = event.getItemEntity();
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        BlockPos pos = itemEntity.blockPosition();
        String room = RoomService.INSTANCE.resolveRoom(player.serverLevel(), pos);
        String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()))).toString();

        var record = EconomyMetricsService.INSTANCE.recordItemPickup(
                player,
                stack,
                pos
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendEconomyLine(record.toJson());
            // DuckDB: economy_item_pickups
            DuckDBTelemetryService.INSTANCE.logItemPickup(
                player.getUUID(), player.getGameProfile().getName(),
                room, itemId, stack.getCount(),
                pos.getX(), pos.getY(), pos.getZ()
            );
            LOGGER.debug("[Economy] Item picked up: {} picked {}x {}",
                    player.getGameProfile().getName(), stack.getCount(), stack.getItem());
        }
    }

    /**
     * Track item usage/consumption (food, potions, etc.).
     * Fires when player finishes using an item.
     */
    @SubscribeEvent
    public static void onItemUsed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        ItemStack usedItem = event.getItem();
        if (usedItem.isEmpty()) return;

        // Track consumable items (food, potions, etc.)
        // Check if item will be consumed (stack shrinks or transforms)
        var foodProps = usedItem.getFoodProperties(player);
        boolean isFood = foodProps != null;
        boolean isPotion = usedItem.getItem().getClass().getName().contains("Potion");
        if (isFood || isPotion) {
            String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(usedItem.getItem()))).toString();
            String useType = isFood ? "food" : "potion";

            var record = EconomyMetricsService.INSTANCE.recordItemUsed(player, usedItem);
            if (record != null) {
                TelemetryService.INSTANCE.appendEconomyLine(record.toJson());
                // DuckDB: economy_item_usage
                DuckDBTelemetryService.INSTANCE.logItemUsage(
                    player.getUUID(), player.getGameProfile().getName(),
                    "consumed", itemId, usedItem.getCount(), useType
                );
                LOGGER.debug("[Economy] Item used: {} used {}",
                        player.getGameProfile().getName(), usedItem.getItem());
            }
        }
    }

    /**
     * Track item discard (Q key or drag out of inventory).
     * Useful for tracking reward relevance - items players throw away.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        ItemStack tossed = event.getEntity().getItem();
        if (tossed.isEmpty()) return;

        String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(tossed.getItem()))).toString();

        var record = EconomyMetricsService.INSTANCE.recordItemDiscarded(player, tossed);
        if (record != null) {
            TelemetryService.INSTANCE.appendEconomyLine(record.toJson());
            // DuckDB: economy_item_usage (event_type=discarded)
            DuckDBTelemetryService.INSTANCE.logItemUsage(
                player.getUUID(), player.getGameProfile().getName(),
                "discarded", itemId, tossed.getCount(), "toss"
            );
            LOGGER.debug("[Economy] Item discarded: {} discarded {}x {}",
                    player.getGameProfile().getName(), tossed.getCount(), tossed.getItem());
        }
    }

    /**
     * Track chest/container opening.
     * Captures what items are in containers when players open them.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        var container = event.getContainer();

        // Skip player inventory (we only want chests, barrels, etc.)
        if (container.getClass().getSimpleName().contains("InventoryMenu")) return;

        // Collect container contents
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < container.slots.size(); i++) {
            var slot = container.slots.get(i);
            // Only include container slots, not player inventory slots
            if (slot.container != player.getInventory()) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    contents.add(stack.copy());
                }
            }
        }

        // Only track if container has items
        if (contents.isEmpty()) return;

        // Use player position as chest position approximation
        // (actual chest position would require more complex tracking)
        BlockPos pos = player.blockPosition();

        var record = EconomyMetricsService.INSTANCE.recordChestOpen(
                player,
                (ServerLevel) player.level(),
                pos,
                contents
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendEconomyLine(record.toJson());
            LOGGER.debug("[Economy] Container opened: {} opened container with {} items at {}",
                    player.getGameProfile().getName(), contents.size(), pos);
        }
    }
}
