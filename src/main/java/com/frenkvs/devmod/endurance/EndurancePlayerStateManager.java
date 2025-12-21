package com.frenkvs.devmod.endurance;

import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * Manages player state during endurance quests.
 * Handles saving/restoring inventory, game mode, health, and providing starter kits.
 */
public class EndurancePlayerStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EndurancePlayerStateManager.class);

    public static final EndurancePlayerStateManager INSTANCE = new EndurancePlayerStateManager();

    private EndurancePlayerStateManager() {}

    // ═══════════════════════════════════════════════════════════════
    // PREPARE PLAYER FOR QUEST
    // ═══════════════════════════════════════════════════════════════

    /**
     * Prepare a player for the quest: save current state, set survival mode,
     * clear inventory, and give a starter kit.
     *
     * NOTE: When using Instance Dimension mode, inventory/state is saved by RecoverySystem
     * BEFORE this method is called. We only save to session for legacy (overworld arena) mode.
     */
    public void preparePlayerForQuest(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        // Save original game mode (always needed for both modes)
        session.setOriginalGameMode(player.gameMode.getGameModeForPlayer());

        // Only save inventory locally for LEGACY mode (non-instance)
        // In Instance mode, RecoverySystem already saved a full snapshot
        if (!session.isInInstanceDimension()) {
            ListTag inventoryTag = new ListTag();
            player.getInventory().save(inventoryTag);
            session.setSavedInventory(inventoryTag);
            LOGGER.info("[EnduranceQuest] Prepared player {} for quest (saved {} inventory slots, was in {} mode)",
                player.getName().getString(), inventoryTag.size(), session.getOriginalGameMode());
        } else {
            LOGGER.info("[EnduranceQuest] Prepared player {} for INSTANCE quest (state saved by RecoverySystem)",
                player.getName().getString());
        }

        // Clear the player's inventory completely
        player.getInventory().clearContent();

        // Set to survival mode
        player.setGameMode(GameType.SURVIVAL);

        // Give starter kit
        giveStarterKit(player);

        // Heal player to full
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
    }

    // ═══════════════════════════════════════════════════════════════
    // STARTER KIT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Give the player a starter kit for the endurance quest.
     */
    public void giveStarterKit(ServerPlayer player) {
        var inventory = player.getInventory();

        // Iron Sword (main weapon)
        ItemStack sword = new ItemStack(Objects.requireNonNull(Items.IRON_SWORD));
        inventory.add(sword);

        // Bow + Arrows (ranged option)
        ItemStack bow = new ItemStack(Objects.requireNonNull(Items.BOW));
        inventory.add(bow);
        inventory.add(new ItemStack(Objects.requireNonNull(Items.ARROW), 32));

        // Shield (defense)
        inventory.add(new ItemStack(Objects.requireNonNull(Items.SHIELD)));

        // Basic armor set (iron)
        player.getInventory().armor.set(3, new ItemStack(Objects.requireNonNull(Items.IRON_HELMET)));      // Head slot
        player.getInventory().armor.set(2, new ItemStack(Objects.requireNonNull(Items.IRON_CHESTPLATE)));  // Chest slot
        player.getInventory().armor.set(1, new ItemStack(Objects.requireNonNull(Items.IRON_LEGGINGS)));    // Legs slot
        player.getInventory().armor.set(0, new ItemStack(Objects.requireNonNull(Items.IRON_BOOTS)));       // Feet slot

        // Food (golden apples for emergency healing)
        inventory.add(new ItemStack(Objects.requireNonNull(Items.GOLDEN_APPLE), 3));
        inventory.add(new ItemStack(Objects.requireNonNull(Items.COOKED_BEEF), 16));

        // Utility items
        inventory.add(new ItemStack(Objects.requireNonNull(Items.TORCH), 16));

        LOGGER.debug("[EnduranceQuest] Gave starter kit to {}", player.getName().getString());
    }

    /**
     * Reset quest loadout after death to ensure the player has the expected kit.
     */
    public void resetQuestLoadout(ServerPlayer player) {
        player.getInventory().clearContent();
        giveStarterKit(player);

        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);

        LOGGER.debug("[EnduranceQuest] Reset quest loadout for {}", player.getName().getString());
    }

    /**
     * Apply a short invulnerability window after teleport/respawn.
     */
    public void applySafeWindowEffects(ServerPlayer player, int ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        int duration = Math.max(1, ticks);
        player.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE), duration, 4, false, false));
        player.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.REGENERATION), duration, 1, false, false));
    }

    // ═══════════════════════════════════════════════════════════════
    // RESTORE PLAYER AFTER QUEST
    // ═══════════════════════════════════════════════════════════════

    /**
     * Restore a player's original state after the quest ends.
     *
     * NOTE: When using Instance Dimension mode, full state restoration (including inventory,
     * position, effects) is handled by RecoverySystem via InstanceManager.endInstanceQuest().
     * This method only performs local restoration for LEGACY mode.
     */
    public boolean restorePlayerAfterQuest(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        // In Instance mode, RecoverySystem handles FULL restoration (inventory, position, etc.)
        // DO NOT touch player state here - let RecoverySystem do it atomically
        if (session.isInInstanceDimension()) {
            LOGGER.debug("[EnduranceQuest] Instance mode: skipping local restore (RecoverySystem handles it)");
            return false;
        }

        // === LEGACY MODE: Full local restoration ===

        // Clear quest inventory
        player.getInventory().clearContent();

        // Restore original inventory
        ListTag savedInventory = session.getSavedInventory();
        if (savedInventory != null && !savedInventory.isEmpty()) {
            player.getInventory().load(savedInventory);
        }

        // Restore original game mode
        GameType originalMode = session.getOriginalGameMode();
        if (originalMode != null) {
            player.setGameMode(originalMode);
        }

        // Heal player
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);

        LOGGER.info("[EnduranceQuest] Restored player {} state (game mode: {})",
            player.getName().getString(), originalMode);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // CLEANUP QUEST SYSTEMS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Cleanup quest-related systems (WaveManager, BossWaveSystem) when quest ends.
     * This ensures all state is properly reset for the next quest.
     */
    public void cleanupQuestSystems(EnduranceQuestManager.ActiveQuestSession session) {
        ArenaManager.Arena arena = session.getArena();
        if (arena == null) {
            LOGGER.warn("[EnduranceQuest] Cannot cleanup quest systems - arena is null");
            return;
        }
        UUID arenaId = arena.getId();

        // Cleanup WaveManager state (removes tracked mobs, resets wave state)
        WaveManager.INSTANCE.cleanupWave(arenaId, arena.getLevel());

        // Cleanup BossWaveSystem if there's an active boss fight
        BossWaveSystem.INSTANCE.endBossFight(arenaId, false);

        LOGGER.debug("[EnduranceQuest] Cleaned up quest systems for arena {}", arenaId);
    }

    // ═══════════════════════════════════════════════════════════════
    // CLEANUP ARENA OR INSTANCE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Cleanup the arena or instance dimension when a quest ends.
     * If the session used an instance dimension, destroys the instance.
     * Otherwise, destroys the legacy overworld arena.
     *
     * @param session The quest session to cleanup
     * @param arenaManager The arena manager for legacy mode cleanup
     * @param success Whether the quest was completed successfully
     */
    public void cleanupArenaOrInstance(EnduranceQuestManager.ActiveQuestSession session, boolean success) {
        if (session.isInInstanceDimension()) {
            // Instance dimension mode - use InstanceArenaManager for cleanup
            java.util.UUID instanceId = session.getInstanceId();
            if (instanceId != null) {
                com.frenkvs.devmod.instance.InstanceManager.INSTANCE.endInstanceQuest(
                    instanceId,
                    success,
                    success ? "Quest completed" : "Quest ended"
                );
                LOGGER.debug("[EnduranceQuest] Scheduled instance {} for destruction (success: {})",
                    instanceId, success);
            }
            return;
        }

        // Legacy overworld arenas are no longer supported for Endurance.
        LOGGER.error("[EnduranceQuest] Legacy arena cleanup blocked - instance-only flow required");
    }
}
