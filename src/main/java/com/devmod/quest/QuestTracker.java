package com.devmod.quest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.devmod.DevMod;

/**
 * Listens to NeoForge GAME bus events and updates quest progress automatically.
 * Also handles persistence (save on logout, load on login) and reward granting.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class QuestTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestTracker.class);

    private static final int POSITION_CHECK_INTERVAL = 20; // Check every second
    // Per-player tick counters to avoid shared-state bug where one player's tick
    // resets the counter for all players
    private static final Map<UUID, Integer> playerTickCounters = new ConcurrentHashMap<>();
    private static boolean npcListenerRegistered = false;

    private QuestTracker() {}

    // ========================================================================
    // PERSISTENCE: Login / Logout
    // ========================================================================

    /**
     * On player login: load quest progress from SavedData and register NPC listener.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID playerId = player.getUUID();

        // Ensure quest definitions are registered
        if (QuestRegistry.INSTANCE.getQuestIds().isEmpty()) {
            QuestRegistry.INSTANCE.registerExampleQuests();
        }

        // Register NPC dialog listener once to wire Interact objectives
        if (!npcListenerRegistered) {
            try {
                com.devmod.npc.event.DialogEventBus.onOpened(opened ->
                    onNpcInteract(opened.playerId(), opened.npcId().toString()));
                npcListenerRegistered = true;
                LOGGER.debug("[QuestTracker] Registered NPC dialog listener for Interact objectives");
            } catch (Exception e) {
                LOGGER.warn("[QuestTracker] Failed to register NPC dialog listener: {}", e.getMessage());
            }
        }

        // Load persisted progress into registry
        QuestProgressSavedData savedData = QuestProgressSavedData.get(server);
        var progress = savedData.getPlayerProgress(playerId);
        if (!progress.isEmpty()) {
            QuestRegistry.INSTANCE.loadPlayerProgress(playerId, progress);
            LOGGER.info("[QuestTracker] Loaded {} quest progress entries for player {}",
                progress.size(), player.getName().getString());
        }

        // Auto-start "first_blood" quest for new players who have no quest progress
        if (QuestRegistry.INSTANCE.getActiveQuests(playerId).isEmpty()
                && QuestRegistry.INSTANCE.getCompletedQuests(playerId).isEmpty()) {
            if (QuestRegistry.INSTANCE.startQuest(playerId, "first_blood")) {
                QuestRegistry.INSTANCE.toggleTracking(playerId, "first_blood");
                player.sendSystemMessage(
                    Component.translatable("devmod.quest.auto_start", "First Blood"));
                LOGGER.info("[QuestTracker] Auto-started 'first_blood' quest for new player {}",
                    player.getName().getString());
            }
        }
    }

    /**
     * On player logout: persist progress to SavedData and clean up tick counter.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        savePlayerProgress(player);
        playerTickCounters.remove(player.getUUID());
    }

    /**
     * Saves a player's quest progress to SavedData.
     */
    public static void savePlayerProgress(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID playerId = player.getUUID();
        QuestProgressSavedData savedData = QuestProgressSavedData.get(server);
        var allProgress = QuestRegistry.INSTANCE.getAllPlayerProgress();
        var playerProgress = allProgress.get(playerId);
        if (playerProgress != null && !playerProgress.isEmpty()) {
            savedData.setPlayerProgress(playerId, playerProgress);
            LOGGER.debug("[QuestTracker] Saved quest progress for player {}",
                player.getName().getString());
        }
    }

    // ========================================================================
    // EVENT HANDLERS
    // ========================================================================

    /**
     * Handles mob kills - updates Kill objectives.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // Check if a player caused the death
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        UUID playerId = player.getUUID();
        EntityType<?> killedType = entity.getType();

        List<QuestProgress> activeQuests = QuestRegistry.INSTANCE.getActiveQuests(playerId);
        for (QuestProgress progress : activeQuests) {
            for (QuestObjective obj : progress.getObjectives()) {
                if (obj instanceof QuestObjective.Kill kill && !kill.isComplete()) {
                    if (kill.target().equals(killedType)) {
                        QuestObjective.Kill updated = kill.increment();
                        QuestRegistry.INSTANCE.updateObjective(playerId, progress.getQuestId(), kill.id(), updated);

                        if (updated.isComplete()) {
                            LOGGER.debug("[QuestTracker] Player {} completed kill objective: {} ({}/{})",
                                player.getName().getString(), kill.id(), updated.current(), updated.required());
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles item pickup - updates Collect objectives.
     */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.level().isClientSide()) return;

        UUID playerId = serverPlayer.getUUID();
        ItemStack pickedUp = event.getItemEntity().getItem();
        if (pickedUp.isEmpty()) return;
        Item pickedItem = pickedUp.getItem();
        int amount = pickedUp.getCount();

        List<QuestProgress> activeQuests = QuestRegistry.INSTANCE.getActiveQuests(playerId);
        for (QuestProgress progress : activeQuests) {
            for (QuestObjective obj : progress.getObjectives()) {
                if (obj instanceof QuestObjective.Collect collect && !collect.isComplete()) {
                    if (collect.item().equals(pickedItem)) {
                        QuestObjective.Collect updated = collect.increment(amount);
                        QuestRegistry.INSTANCE.updateObjective(playerId, progress.getQuestId(), collect.id(), updated);

                        if (updated.isComplete()) {
                            LOGGER.debug("[QuestTracker] Player {} completed collect objective: {}",
                                serverPlayer.getName().getString(), collect.id());
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles item crafting - updates CraftItem objectives.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID playerId = serverPlayer.getUUID();
        ItemStack crafted = event.getCrafting();
        Item craftedItem = crafted.getItem();
        int amount = crafted.getCount();

        List<QuestProgress> activeQuests = QuestRegistry.INSTANCE.getActiveQuests(playerId);
        for (QuestProgress progress : activeQuests) {
            for (QuestObjective obj : progress.getObjectives()) {
                if (obj instanceof QuestObjective.CraftItem craft && !craft.isComplete()) {
                    if (craft.item().equals(craftedItem)) {
                        QuestObjective.CraftItem updated = craft.increment(amount);
                        QuestRegistry.INSTANCE.updateObjective(playerId, progress.getQuestId(), craft.id(), updated);

                        if (updated.isComplete()) {
                            LOGGER.debug("[QuestTracker] Player {} completed craft objective: {}",
                                player.getName().getString(), craft.id());
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles player dimension changes - checks VisitDimension and level objectives.
     */
    @SubscribeEvent
    public static void onPlayerDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID playerId = serverPlayer.getUUID();
        ResourceKey<Level> newDimension = event.getTo();

        // Check VisitDimension objectives
        List<QuestProgress> activeQuests = QuestRegistry.INSTANCE.getActiveQuests(playerId);
        for (QuestProgress progress : activeQuests) {
            for (QuestObjective obj : progress.getObjectives()) {
                if (obj instanceof QuestObjective.VisitDimension visitDim && !visitDim.isComplete()) {
                    if (visitDim.dimension().equals(newDimension)) {
                        QuestObjective.VisitDimension updated = visitDim.markVisited();
                        QuestRegistry.INSTANCE.updateObjective(playerId, progress.getQuestId(), visitDim.id(), updated);
                        LOGGER.debug("[QuestTracker] Player {} completed visit dimension objective: {}",
                            player.getName().getString(), visitDim.id());
                    }
                }
            }
        }

        // Also check level objectives
        checkLevelObjectives(player);
    }

    /**
     * Periodic position and level checks on player tick (every 20 ticks per player).
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID playerId = serverPlayer.getUUID();

        // Per-player tick counter avoids one player resetting the interval for all
        int ticks = playerTickCounters.merge(playerId, 1, Integer::sum);
        if (ticks < POSITION_CHECK_INTERVAL) return;
        playerTickCounters.put(playerId, 0);
        BlockPos playerPos = serverPlayer.blockPosition();
        int playerLevel = serverPlayer.experienceLevel;

        List<QuestProgress> activeQuests = QuestRegistry.INSTANCE.getActiveQuests(playerId);
        for (QuestProgress progress : activeQuests) {
            for (QuestObjective obj : progress.getObjectives()) {
                // Visit objectives - check distance
                if (obj instanceof QuestObjective.Visit visit && !visit.isComplete()) {
                    double distance = Math.sqrt(playerPos.distSqr(visit.target()));
                    if (distance <= visit.radius()) {
                        QuestObjective.Visit updated = visit.markVisited();
                        QuestRegistry.INSTANCE.updateObjective(playerId, progress.getQuestId(), visit.id(), updated);
                        LOGGER.debug("[QuestTracker] Player {} completed visit objective: {}",
                            player.getName().getString(), visit.id());
                    }
                }

                // ReachLevel objectives
                if (obj instanceof QuestObjective.ReachLevel level && !level.isComplete()) {
                    if (playerLevel >= level.targetLevel()) {
                        QuestObjective.ReachLevel updated = level.withLevel(playerLevel);
                        QuestRegistry.INSTANCE.updateObjective(playerId, progress.getQuestId(), level.id(), updated);
                        LOGGER.debug("[QuestTracker] Player {} completed level objective: {}",
                            player.getName().getString(), level.id());
                    }
                }
            }
        }
    }

    /**
     * Manually trigger an NPC interaction objective update.
     * Called from NPC interaction handlers.
     */
    public static void onNpcInteract(UUID playerId, String npcId) {
        List<QuestProgress> activeQuests = QuestRegistry.INSTANCE.getActiveQuests(playerId);
        for (QuestProgress progress : activeQuests) {
            for (QuestObjective obj : progress.getObjectives()) {
                if (obj instanceof QuestObjective.Interact interact && !interact.isComplete()) {
                    if (interact.npcId().equals(npcId)) {
                        QuestObjective.Interact updated = interact.markInteracted();
                        QuestRegistry.INSTANCE.updateObjective(playerId, progress.getQuestId(), interact.id(), updated);
                        LOGGER.debug("[QuestTracker] Player {} completed interact objective with NPC: {}",
                            playerId, npcId);
                    }
                }
            }
        }
    }

    // ========================================================================
    // QUEST COMPLETION & REWARDS
    // ========================================================================

    /**
     * Grants rewards for a completed quest. Called by QuestRegistry on completion.
     */
    public static void grantRewards(ServerPlayer player, Quest quest) {
        QuestReward reward = quest.getReward();
        if (reward.isEmpty()) return;

        // Grant XP
        if (reward.hasExperience()) {
            player.giveExperiencePoints(reward.experiencePoints());
        }

        // Grant items
        if (reward.hasItems()) {
            for (ItemStack item : reward.items()) {
                ItemStack copy = item.copy();
                if (!player.getInventory().add(copy)) {
                    // Drop on ground if inventory is full
                    player.drop(copy, false);
                }
            }
        }

        // Send completion notification
        player.sendSystemMessage(
            Component.translatable("devmod.quest.tracker.complete_prefix")
                .append(quest.getName())
                .append(Component.literal(" - "))
                .append(reward.getSummary()));

        // Check if this quest unlocks other quests
        if (reward.hasUnlocks()) {
            for (String unlockedId : reward.unlockedQuestIds()) {
                Quest unlocked = QuestRegistry.INSTANCE.getQuest(unlockedId);
                if (unlocked != null) {
                    player.sendSystemMessage(
                        Component.translatable("devmod.quest.tracker.new_quest_prefix")
                            .append(unlocked.getName()));
                }
            }
        }

        // Persist progress
        savePlayerProgress(player);

        LOGGER.info("[QuestTracker] Granted rewards for quest '{}' to player {}",
            quest.getId(), player.getName().getString());
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    private static void checkLevelObjectives(Player player) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID playerId = serverPlayer.getUUID();
        int playerLevel = serverPlayer.experienceLevel;

        List<QuestProgress> activeQuests = QuestRegistry.INSTANCE.getActiveQuests(playerId);
        for (QuestProgress progress : activeQuests) {
            for (QuestObjective obj : progress.getObjectives()) {
                if (obj instanceof QuestObjective.ReachLevel level && !level.isComplete()) {
                    if (playerLevel >= level.targetLevel()) {
                        QuestObjective.ReachLevel updated = level.withLevel(playerLevel);
                        QuestRegistry.INSTANCE.updateObjective(playerId, progress.getQuestId(), level.id(), updated);
                    }
                }
            }
        }
    }
}
