package com.devmod.quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Central registry for quest definitions and player progress.
 * Quest definitions are loaded at startup; player progress is tracked per-UUID.
 */
public class QuestRegistry {
    public static final QuestRegistry INSTANCE = new QuestRegistry();
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestRegistry.class);

    private final Map<String, Quest> questDefinitions = new LinkedHashMap<>();
    private final Map<UUID, Map<String, QuestProgress>> playerProgress = new ConcurrentHashMap<>();

    private final List<Runnable> changeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private QuestRegistry() {}

    // ========================================================================
    // QUEST DEFINITIONS
    // ========================================================================

    public void registerQuest(Quest quest) {
        questDefinitions.put(quest.getId(), quest);
        LOGGER.debug("[QuestRegistry] Registered quest: {}", quest.getId());
    }

    @Nullable
    public Quest getQuest(String questId) {
        return questDefinitions.get(questId);
    }

    public Collection<Quest> getAllQuests() {
        return Collections.unmodifiableCollection(questDefinitions.values());
    }

    public Set<String> getQuestIds() {
        return Collections.unmodifiableSet(questDefinitions.keySet());
    }

    // ========================================================================
    // PLAYER PROGRESS
    // ========================================================================

    /**
     * Starts a quest for a player. Creates progress from the quest definition.
     */
    public boolean startQuest(UUID playerId, String questId) {
        Quest quest = questDefinitions.get(questId);
        if (quest == null) {
            LOGGER.warn("[QuestRegistry] Cannot start unknown quest: {}", questId);
            return false;
        }

        Map<String, QuestProgress> progress = playerProgress.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        // Check if already active
        QuestProgress existing = progress.get(questId);
        if (existing != null && existing.getStatus().isActive()) {
            LOGGER.debug("[QuestRegistry] Quest {} already active for player {}", questId, playerId);
            return false;
        }

        // Check prerequisites
        if (!arePrerequisitesMet(playerId, quest)) {
            LOGGER.debug("[QuestRegistry] Prerequisites not met for quest {} (player {})", questId, playerId);
            return false;
        }

        QuestProgress newProgress = new QuestProgress(questId, quest.getObjectives());
        newProgress.setStatus(QuestStatus.ACTIVE);
        progress.put(questId, newProgress);
        notifyListeners();

        LOGGER.info("[QuestRegistry] Player {} started quest: {}", playerId, questId);
        return true;
    }

    /**
     * Completes a quest for a player and grants rewards.
     */
    public boolean completeQuest(UUID playerId, String questId) {
        QuestProgress progress = getProgress(playerId, questId);
        if (progress == null || !progress.getStatus().isActive()) {
            return false;
        }

        progress.setStatus(QuestStatus.COMPLETED);
        notifyListeners();

        // Grant rewards if player is online
        Quest quest = questDefinitions.get(questId);
        if (quest != null) {
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    QuestTracker.grantRewards(player, quest);

                    // Notify about newly available quests (prerequisites now met)
                    List<Quest> nowAvailable = getAvailableQuests(playerId);
                    for (Quest available : nowAvailable) {
                        player.sendSystemMessage(
                            net.minecraft.network.chat.Component.translatable(
                                "devmod.quest.new_available", available.getName()));
                    }
                }
            }
        }

        LOGGER.info("[QuestRegistry] Player {} completed quest: {}", playerId, questId);
        return true;
    }

    /**
     * Fails a quest for a player.
     */
    public boolean failQuest(UUID playerId, String questId) {
        QuestProgress progress = getProgress(playerId, questId);
        if (progress == null || !progress.getStatus().isActive()) {
            return false;
        }

        progress.setStatus(QuestStatus.FAILED);
        notifyListeners();

        LOGGER.info("[QuestRegistry] Player {} failed quest: {}", playerId, questId);
        return true;
    }

    /**
     * Abandons an active quest, resetting it to NOT_STARTED.
     */
    public boolean abandonQuest(UUID playerId, String questId) {
        Map<String, QuestProgress> progress = playerProgress.get(playerId);
        if (progress == null) return false;

        QuestProgress questProgress = progress.get(questId);
        if (questProgress == null || !questProgress.getStatus().isActive()) return false;

        progress.remove(questId);
        notifyListeners();

        LOGGER.info("[QuestRegistry] Player {} abandoned quest: {}", playerId, questId);
        return true;
    }

    /**
     * Updates a specific objective within a player's quest progress.
     */
    public void updateObjective(UUID playerId, String questId, String objectiveId, QuestObjective updated) {
        QuestProgress progress = getProgress(playerId, questId);
        if (progress == null || !progress.getStatus().isActive()) return;

        progress.updateObjective(objectiveId, updated);

        // Auto-complete quest if all objectives are done
        if (progress.areAllObjectivesComplete()) {
            completeQuest(playerId, questId);
        } else {
            notifyListeners();
        }
    }

    @Nullable
    public QuestProgress getProgress(UUID playerId, String questId) {
        Map<String, QuestProgress> progress = playerProgress.get(playerId);
        return progress != null ? progress.get(questId) : null;
    }

    /**
     * Returns all active quests for a player.
     */
    public List<QuestProgress> getActiveQuests(UUID playerId) {
        Map<String, QuestProgress> progress = playerProgress.get(playerId);
        if (progress == null) return List.of();

        List<QuestProgress> active = new ArrayList<>();
        for (QuestProgress p : progress.values()) {
            if (p.getStatus().isActive()) {
                active.add(p);
            }
        }
        return active;
    }

    /**
     * Returns all completed quests for a player.
     */
    public List<QuestProgress> getCompletedQuests(UUID playerId) {
        Map<String, QuestProgress> progress = playerProgress.get(playerId);
        if (progress == null) return List.of();

        List<QuestProgress> completed = new ArrayList<>();
        for (QuestProgress p : progress.values()) {
            if (p.getStatus() == QuestStatus.COMPLETED) {
                completed.add(p);
            }
        }
        return completed;
    }

    /**
     * Returns all quests available to start for a player (prerequisites met, not active/completed).
     */
    public List<Quest> getAvailableQuests(UUID playerId) {
        List<Quest> available = new ArrayList<>();
        Map<String, QuestProgress> progress = playerProgress.getOrDefault(playerId, Map.of());

        for (Quest quest : questDefinitions.values()) {
            QuestProgress p = progress.get(quest.getId());
            // Skip if already active or completed (unless repeatable)
            if (p != null) {
                if (p.getStatus().isActive()) continue;
                if (p.getStatus() == QuestStatus.COMPLETED && !quest.isRepeatable()) continue;
            }
            // Check prerequisites
            if (arePrerequisitesMet(playerId, quest)) {
                available.add(quest);
            }
        }
        return available;
    }

    /**
     * Returns the status of a quest for a player.
     */
    public QuestStatus getQuestStatus(UUID playerId, String questId) {
        QuestProgress progress = getProgress(playerId, questId);
        return progress != null ? progress.getStatus() : QuestStatus.NOT_STARTED;
    }

    /**
     * Toggles tracking on/off for a quest. Max 3 tracked quests.
     */
    public void toggleTracking(UUID playerId, String questId) {
        QuestProgress progress = getProgress(playerId, questId);
        if (progress == null || !progress.getStatus().isActive()) return;

        if (progress.isTracked()) {
            progress.setTracked(false);
        } else {
            // Count currently tracked quests
            int trackedCount = 0;
            Map<String, QuestProgress> allProgress = playerProgress.get(playerId);
            if (allProgress != null) {
                for (QuestProgress p : allProgress.values()) {
                    if (p.isTracked()) trackedCount++;
                }
            }
            if (trackedCount < 3) {
                progress.setTracked(true);
            }
        }
        notifyListeners();
    }

    /**
     * Returns tracked quests for a player (max 3).
     */
    public List<QuestProgress> getTrackedQuests(UUID playerId) {
        Map<String, QuestProgress> progress = playerProgress.get(playerId);
        if (progress == null) return List.of();

        List<QuestProgress> tracked = new ArrayList<>();
        for (QuestProgress p : progress.values()) {
            if (p.isTracked() && p.getStatus().isActive()) {
                tracked.add(p);
            }
        }
        return tracked;
    }

    private boolean arePrerequisitesMet(UUID playerId, Quest quest) {
        if (!quest.hasPrerequisites()) return true;
        Map<String, QuestProgress> progress = playerProgress.get(playerId);
        if (progress == null) return quest.getRequiredQuestIds().isEmpty();

        for (String reqId : quest.getRequiredQuestIds()) {
            QuestProgress reqProgress = progress.get(reqId);
            if (reqProgress == null || reqProgress.getStatus() != QuestStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clears all progress for a player.
     */
    public void clearPlayerProgress(UUID playerId) {
        playerProgress.remove(playerId);
        notifyListeners();
    }

    /**
     * Clears all data (quest definitions and player progress).
     */
    public void clearAll() {
        questDefinitions.clear();
        playerProgress.clear();
        notifyListeners();
        LOGGER.info("[QuestRegistry] Cleared all quest data");
    }

    /**
     * Loads persisted progress for a player into the in-memory map.
     * Called from QuestProgressSavedData on server start.
     */
    public void loadPlayerProgress(UUID playerId, Map<String, QuestProgress> progress) {
        playerProgress.put(playerId, new ConcurrentHashMap<>(progress));
    }

    /**
     * Returns the full player progress map for persistence.
     */
    public Map<UUID, Map<String, QuestProgress>> getAllPlayerProgress() {
        return new ConcurrentHashMap<>(playerProgress);
    }

    // ========================================================================
    // EXAMPLE QUESTS (for testing)
    // ========================================================================

    /**
     * Registers starter quests for the quest system.
     */
    public void registerExampleQuests() {
        // Quest 1: "First Blood" - Kill 10 zombies
        registerQuest(Quest.builder("first_blood")
            .name("First Blood")
            .description("Prove your combat skills by slaying 10 zombies.")
            .category("Combat")
            .addObjective(new QuestObjective.Kill("kill_10_zombies", EntityType.ZOMBIE, 10, 0))
            .reward(QuestReward.builder()
                .xp(100)
                .items(List.of(new ItemStack(Items.IRON_SWORD)))
                .build())
            .build());

        // Quest 2: "Collector" - Collect 32 diamonds
        registerQuest(Quest.builder("collector")
            .name("Collector")
            .description("Amass a fortune by collecting 32 diamonds.")
            .category("Gathering")
            .addObjective(new QuestObjective.Collect("collect_32_diamonds", Items.DIAMOND, 32, 0))
            .reward(QuestReward.builder()
                .xp(200)
                .items(List.of(new ItemStack(Items.DIAMOND_PICKAXE)))
                .build())
            .build());

        // Quest 3: "Explorer" - Visit the Nether
        registerQuest(Quest.builder("explorer")
            .name("Explorer")
            .description("Venture into the Nether dimension.")
            .category("Exploration")
            .addObjective(new QuestObjective.VisitDimension("visit_nether", Level.NETHER, "The Nether", false))
            .reward(QuestReward.builder()
                .xp(150)
                .items(List.of(new ItemStack(Items.GOLDEN_APPLE)))
                .build())
            .build());

        // Quest 4: "Arena Rookie" - Kill 50 mobs in a challenging fight
        // (Modeled as a kill quest since arena waves are managed by EnduranceQuestManager)
        registerQuest(Quest.builder("arena_rookie")
            .name("Arena Rookie")
            .description("Defeat 50 hostile mobs to prove your arena readiness.")
            .category("Combat")
            .requiresQuest("first_blood")
            .addObjective(new QuestObjective.Kill("kill_25_zombies", EntityType.ZOMBIE, 25, 0))
            .addObjective(new QuestObjective.Kill("kill_15_skeletons", EntityType.SKELETON, 15, 0))
            .addObjective(new QuestObjective.Kill("kill_10_spiders", EntityType.SPIDER, 10, 0))
            .reward(QuestReward.builder()
                .xp(300)
                .items(List.of(
                    new ItemStack(Items.IRON_CHESTPLATE),
                    new ItemStack(Items.IRON_LEGGINGS)))
                .build())
            .build());

        // Quest 5: "Combo Master" - Reach experience level 30 and craft a diamond sword
        // (Modeled as progression quest since combo ranks are internal to EnduranceQuest)
        registerQuest(Quest.builder("combo_master")
            .name("Combo Master")
            .description("Reach level 30 and forge a diamond sword to master combat.")
            .category("Progression")
            .addObjective(new QuestObjective.ReachLevel("reach_level_30", 30, 0))
            .addObjective(new QuestObjective.CraftItem("craft_diamond_sword", Items.DIAMOND_SWORD, 1, 0))
            .reward(QuestReward.builder()
                .xp(250)
                .items(List.of(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)))
                .build())
            .build());

        // Quest 6: Repeatable daily hunt
        registerQuest(Quest.builder("daily_hunt")
            .name("Daily Hunt")
            .description("Slay 5 hostile mobs. Repeatable daily.")
            .category("Combat")
            .repeatable(true)
            .cooldownTicks(24000) // 1 MC day
            .addObjective(new QuestObjective.Kill("kill_5_mobs", EntityType.ZOMBIE, 5, 0))
            .reward(QuestReward.builder().xp(25).currency(50).build())
            .build());

        LOGGER.info("[QuestRegistry] Registered {} starter quests", questDefinitions.size());
    }

    // ========================================================================
    // LISTENERS
    // ========================================================================

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOGGER.error("[QuestRegistry] Error notifying listener", e);
            }
        }
    }
}
