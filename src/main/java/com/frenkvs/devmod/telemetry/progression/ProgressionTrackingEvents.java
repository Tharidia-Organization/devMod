package com.frenkvs.devmod.telemetry.progression;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * Event handlers for player progression tracking.
 *
 * Hooks into:
 * - Block break/place events
 * - XP and level events
 * - Advancement events
 * - Dimension change events
 * - Combat events (attacks, critical hits)
 * - Trading events
 * - Fishing events
 *
 * Logs telemetry via PlayerProgressionService.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class ProgressionTrackingEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    // ===== BLOCK EVENTS =====

    /**
     * Track block breaking.
     * Captures mining patterns and resource extraction.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        BlockState state = event.getState();
        String blockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(state.getBlock()))).toString();

        var record = PlayerProgressionService.INSTANCE.recordBlockBreak(
                player,
                (net.minecraft.server.level.ServerLevel) player.level(),
                event.getPos(),
                blockId
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] Block broken: {} broke {} at {}",
                    player.getGameProfile().getName(), blockId, event.getPos());
        }

        // DuckDB: log block break
        BlockPos pos = event.getPos();
        String worldId = player.level().dimension().location().toString();
        String room = TelemetryService.INSTANCE.getCurrentRoom();
        DuckDBTelemetryService.INSTANCE.logBlock(
                player.getUUID(), player.getGameProfile().getName(),
                worldId, room, "break", blockId,
                pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Track block placing.
     * Captures building patterns and fortification behavior.
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        BlockState state = event.getPlacedBlock();
        String blockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(state.getBlock()))).toString();

        var record = PlayerProgressionService.INSTANCE.recordBlockPlace(
                player,
                (net.minecraft.server.level.ServerLevel) player.level(),
                event.getPos(),
                blockId
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] Block placed: {} placed {} at {}",
                    player.getGameProfile().getName(), blockId, event.getPos());
        }

        // DuckDB: log block place
        BlockPos pos = event.getPos();
        String worldId = player.level().dimension().location().toString();
        String room = TelemetryService.INSTANCE.getCurrentRoom();
        DuckDBTelemetryService.INSTANCE.logBlock(
                player.getUUID(), player.getGameProfile().getName(),
                worldId, room, "place", blockId,
                pos.getX(), pos.getY(), pos.getZ());
    }

    // ===== XP EVENTS =====

    /**
     * Track XP orb pickup.
     */
    @SubscribeEvent
    public static void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        int amount = event.getOrb().getValue();

        var record = PlayerProgressionService.INSTANCE.recordXpPickup(player, amount);

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] XP picked up: {} gained {} XP",
                    player.getGameProfile().getName(), amount);
        }

        // DuckDB: log XP pickup (with 1s batching via logXp)
        BlockPos pos = player.blockPosition();
        String worldId = player.level().dimension().location().toString();
        String room = TelemetryService.INSTANCE.getCurrentRoom();
        int level = player.experienceLevel;
        DuckDBTelemetryService.INSTANCE.logXp(
                player.getUUID(), player.getGameProfile().getName(),
                worldId, room, "pickup", amount, level, level,
                pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Track level changes.
     */
    @SubscribeEvent
    public static void onLevelChange(PlayerXpEvent.LevelChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        int oldLevel = player.experienceLevel;
        int newLevel = oldLevel + event.getLevels();

        var record = PlayerProgressionService.INSTANCE.recordLevelChange(player, oldLevel, newLevel);

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] Level change: {} went from level {} to {}",
                    player.getGameProfile().getName(), oldLevel, newLevel);
        }

        // DuckDB: log level change (always logged immediately, not batched)
        BlockPos pos = player.blockPosition();
        String worldId = player.level().dimension().location().toString();
        String room = TelemetryService.INSTANCE.getCurrentRoom();
        DuckDBTelemetryService.INSTANCE.logXp(
                player.getUUID(), player.getGameProfile().getName(),
                worldId, room, "level_change", 0, oldLevel, newLevel,
                pos.getX(), pos.getY(), pos.getZ());
    }

    // ===== ADVANCEMENT EVENTS =====

    /**
     * Track advancement completion.
     */
    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        var advancement = event.getAdvancement();
        String advancementId = advancement.id().toString();
        String title = advancement.value().display()
                .map(d -> d.getTitle().getString())
                .orElse(advancementId);

        var record = PlayerProgressionService.INSTANCE.recordAdvancement(
                player,
                advancement.id(),
                title
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] Advancement earned: {} earned '{}'",
                    player.getGameProfile().getName(), title);
        }

        // DuckDB: log advancement (session-dedup handled in logAdvancement)
        BlockPos pos = player.blockPosition();
        String worldId = player.level().dimension().location().toString();
        String room = TelemetryService.INSTANCE.getCurrentRoom();
        DuckDBTelemetryService.INSTANCE.logAdvancement(
                player.getUUID(), player.getGameProfile().getName(),
                worldId, room, advancementId, title,
                pos.getX(), pos.getY(), pos.getZ());
    }

    // ===== DIMENSION EVENTS =====

    /**
     * Track dimension changes.
     */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String fromDim = event.getFrom().location().toString();
        String toDim = event.getTo().location().toString();

        var record = PlayerProgressionService.INSTANCE.recordDimensionChange(player, fromDim, toDim);

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] Dimension change: {} traveled from {} to {}",
                    player.getGameProfile().getName(), fromDim, toDim);
        }

        // DuckDB: log dimension change (5s TTL dedup handled in logDimensionChange)
        BlockPos pos = player.blockPosition();
        String worldId = toDim; // After dimension change, use the destination
        DuckDBTelemetryService.INSTANCE.logDimensionChange(
                player.getUUID(), player.getGameProfile().getName(),
                worldId, fromDim, toDim,
                pos.getX(), pos.getY(), pos.getZ());
    }

    // ===== COMBAT EVENTS =====

    /**
     * Track critical hits.
     */
    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (!event.isCriticalHit()) return;

        Entity target = event.getTarget();
        String targetName = target.getName().getString();
        String targetType = Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(target.getType()))).toString();
        float damage = event.getDamageMultiplier();

        var record = PlayerProgressionService.INSTANCE.recordCriticalHit(
                player,
                targetName,
                targetType,
                damage,
                event.getDamageMultiplier()
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] Critical hit: {} crit {} for {}x damage",
                    player.getGameProfile().getName(), targetName, event.getDamageMultiplier());
        }
    }

    /**
     * Track attack events.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity)) return; // Only track living entity attacks

        String targetName = target.getName().getString();
        String targetType = Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(target.getType()))).toString();
        ItemStack weapon = player.getMainHandItem();
        String weaponId = weapon.isEmpty() ? "fist" : Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(weapon.getItem()))).toString();

        // Check if this is a sweep attack (player is on ground and not sprinting)
        boolean isSweep = player.onGround() && !player.isSprinting() && player.walkDist - player.walkDistO < player.getSpeed();

        var record = PlayerProgressionService.INSTANCE.recordAttack(
                player,
                targetName,
                targetType,
                weaponId,
                isSweep
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] Attack: {} attacked {} with {}",
                    player.getGameProfile().getName(), targetName, weaponId);
        }
    }

    // ===== TRADE EVENTS =====

    /**
     * Track villager trades.
     */
    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        AbstractVillager villager = event.getAbstractVillager();
        String villagerType = Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(villager.getType()))).toString();

        String profession = "unknown";
        if (villager instanceof Villager v) {
            profession = v.getVillagerData().getProfession().name();
        }

        MerchantOffer offer = event.getMerchantOffer();
        ItemStack costA = offer.getCostA();
        ItemStack result = offer.getResult();

        String itemSold = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(costA.getItem()))).toString();
        String itemBought = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(result.getItem()))).toString();

        var record = PlayerProgressionService.INSTANCE.recordTrade(
                player,
                villagerType,
                profession,
                itemBought,
                result.getCount(),
                itemSold,
                costA.getCount()
        );

        if (record != null) {
            TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
            LOGGER.debug("[Progression] Trade: {} traded with {} ({})",
                    player.getGameProfile().getName(), villagerType, profession);
        }

        // DuckDB: log trade
        BlockPos pos = player.blockPosition();
        String worldId = player.level().dimension().location().toString();
        String room = TelemetryService.INSTANCE.getCurrentRoom();
        DuckDBTelemetryService.INSTANCE.logTrade(
                player.getUUID(), player.getGameProfile().getName(),
                worldId, room, villagerType, profession,
                itemBought, result.getCount(), itemSold, costA.getCount(),
                pos.getX(), pos.getY(), pos.getZ());
    }

    // ===== FISHING EVENTS =====

    /**
     * Track fishing catches.
     */
    @SubscribeEvent
    public static void onFishing(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        var drops = event.getDrops();
        if (drops.isEmpty()) return;

        BlockPos pos = player.blockPosition();
        String worldId = player.level().dimension().location().toString();
        String room = TelemetryService.INSTANCE.getCurrentRoom();

        for (ItemStack drop : drops) {
            String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(drop.getItem()))).toString();

            var record = PlayerProgressionService.INSTANCE.recordFishing(
                    player,
                    itemId,
                    drop.getCount(),
                    player.blockPosition()
            );

            if (record != null) {
                TelemetryService.INSTANCE.appendProgressionLine(record.toJson());
                LOGGER.debug("[Progression] Fished: {} caught {}x {}",
                        player.getGameProfile().getName(), drop.getCount(), itemId);
            }

            // DuckDB: log fishing
            DuckDBTelemetryService.INSTANCE.logFishing(
                    player.getUUID(), player.getGameProfile().getName(),
                    worldId, room, itemId, drop.getCount(),
                    pos.getX(), pos.getY(), pos.getZ());
        }
    }

    // ===== CLEANUP =====

    /**
     * Cleanup when player logs out.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerProgressionService.INSTANCE.cleanupPlayer(player.getUUID());
        // DuckDB: clear dedup state for this player
        DuckDBTelemetryService.INSTANCE.clearPlayerDedupState(player.getUUID());
    }
}
