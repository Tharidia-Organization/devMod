package com.devmod.quest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persists quest progress for all players using NeoForge SavedData.
 * Stored in the overworld data folder as "devmod_quest_progress".
 */
public class QuestProgressSavedData extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestProgressSavedData.class);
    private static final String DATA_NAME = "devmod_quest_progress";

    private final Map<UUID, Map<String, QuestProgress>> playerProgress = new ConcurrentHashMap<>();

    public QuestProgressSavedData() {}

    // ========================================================================
    // LOAD / SAVE
    // ========================================================================

    public static QuestProgressSavedData load(
        @Nonnull CompoundTag tag,
        @Nonnull HolderLookup.Provider registries
    ) {
        QuestProgressSavedData data = new QuestProgressSavedData();

        CompoundTag playersTag = tag.getCompound("players");
        for (String uuidStr : playersTag.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(uuidStr);
                CompoundTag playerTag = playersTag.getCompound(uuidStr);
                Map<String, QuestProgress> questMap = new ConcurrentHashMap<>();

                for (String questId : playerTag.getAllKeys()) {
                    CompoundTag progressTag = playerTag.getCompound(questId);
                    QuestProgress progress = loadProgress(questId, progressTag);
                    if (progress != null) {
                        questMap.put(questId, progress);
                    }
                }

                if (!questMap.isEmpty()) {
                    data.playerProgress.put(playerId, questMap);
                }
            } catch (Exception e) {
                LOGGER.warn("[QuestProgressSavedData] Failed to load progress for player {}: {}",
                    uuidStr, e.getMessage());
            }
        }

        LOGGER.info("[QuestProgressSavedData] Loaded progress for {} players", data.playerProgress.size());
        return data;
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();

        for (Map.Entry<UUID, Map<String, QuestProgress>> entry : playerProgress.entrySet()) {
            CompoundTag playerTag = new CompoundTag();

            for (Map.Entry<String, QuestProgress> questEntry : entry.getValue().entrySet()) {
                CompoundTag progressTag = saveProgress(questEntry.getValue());
                playerTag.put(questEntry.getKey(), progressTag);
            }

            playersTag.put(entry.getKey().toString(), playerTag);
        }

        tag.put("players", playersTag);
        return tag;
    }

    // ========================================================================
    // PROGRESS SERIALIZATION
    // ========================================================================

    private static QuestProgress loadProgress(String questId, CompoundTag tag) {
        ListTag objectivesTag = tag.getList("objectives", Tag.TAG_COMPOUND);
        java.util.List<QuestObjective> objectives = new java.util.ArrayList<>();

        for (int i = 0; i < objectivesTag.size(); i++) {
            CompoundTag objTag = objectivesTag.getCompound(i);
            QuestObjective obj = loadObjective(objTag);
            if (obj != null) {
                objectives.add(obj);
            }
        }

        if (objectives.isEmpty()) {
            return null;
        }

        QuestProgress progress = new QuestProgress(questId, objectives);
        progress.setStatus(QuestStatus.valueOf(tag.getString("status")));
        progress.setStartedAtTick(tag.getLong("startedAt"));
        progress.setCompletedAtTick(tag.getLong("completedAt"));
        progress.setTracked(tag.getBoolean("tracked"));
        return progress;
    }

    private static CompoundTag saveProgress(QuestProgress progress) {
        CompoundTag tag = new CompoundTag();
        tag.putString("status", progress.getStatus().name());
        tag.putLong("startedAt", progress.getStartedAtTick());
        tag.putLong("completedAt", progress.getCompletedAtTick());
        tag.putBoolean("tracked", progress.isTracked());

        ListTag objectivesTag = new ListTag();
        for (QuestObjective obj : progress.getObjectives()) {
            CompoundTag objTag = saveObjective(obj);
            if (objTag != null) {
                objectivesTag.add(objTag);
            }
        }
        tag.put("objectives", objectivesTag);
        return tag;
    }

    @SuppressWarnings("unchecked")
    private static QuestObjective loadObjective(CompoundTag tag) {
        String type = tag.getString("type");
        String id = tag.getString("id");

        return switch (type) {
            case "kill" -> {
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(
                    ResourceLocation.parse(tag.getString("target")));
                yield new QuestObjective.Kill(id, entityType,
                    tag.getInt("required"), tag.getInt("current"));
            }
            case "collect" -> {
                Item item = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(tag.getString("item")));
                yield new QuestObjective.Collect(id, item,
                    tag.getInt("required"), tag.getInt("current"));
            }
            case "visit" -> new QuestObjective.Visit(id,
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getInt("radius"), tag.getBoolean("visited"));
            case "visit_dimension" -> {
                ResourceKey<Level> dimension = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString("dimension")));
                yield new QuestObjective.VisitDimension(id, dimension,
                    tag.getString("dimensionName"), tag.getBoolean("visited"));
            }
            case "interact" -> new QuestObjective.Interact(id,
                tag.getString("npcId"), tag.getString("npcName"),
                tag.getBoolean("interacted"));
            case "craft" -> {
                Item item = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(tag.getString("item")));
                yield new QuestObjective.CraftItem(id, item,
                    tag.getInt("required"), tag.getInt("crafted"));
            }
            case "reach_level" -> new QuestObjective.ReachLevel(id,
                tag.getInt("targetLevel"), tag.getInt("currentLevel"));
            default -> {
                LOGGER.warn("[QuestProgressSavedData] Unknown objective type: {}", type);
                yield null;
            }
        };
    }

    private static CompoundTag saveObjective(QuestObjective obj) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", obj.id());

        switch (obj) {
            case QuestObjective.Kill kill -> {
                tag.putString("type", "kill");
                ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(kill.target());
                tag.putString("target", key.toString());
                tag.putInt("required", kill.required());
                tag.putInt("current", kill.current());
            }
            case QuestObjective.Collect collect -> {
                tag.putString("type", "collect");
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(collect.item());
                tag.putString("item", key.toString());
                tag.putInt("required", collect.required());
                tag.putInt("current", collect.current());
            }
            case QuestObjective.Visit visit -> {
                tag.putString("type", "visit");
                tag.putInt("x", visit.target().getX());
                tag.putInt("y", visit.target().getY());
                tag.putInt("z", visit.target().getZ());
                tag.putInt("radius", visit.radius());
                tag.putBoolean("visited", visit.visited());
            }
            case QuestObjective.VisitDimension visitDim -> {
                tag.putString("type", "visit_dimension");
                tag.putString("dimension", visitDim.dimension().location().toString());
                tag.putString("dimensionName", visitDim.dimensionName());
                tag.putBoolean("visited", visitDim.visited());
            }
            case QuestObjective.Interact interact -> {
                tag.putString("type", "interact");
                tag.putString("npcId", interact.npcId());
                tag.putString("npcName", interact.npcName());
                tag.putBoolean("interacted", interact.interacted());
            }
            case QuestObjective.CraftItem craft -> {
                tag.putString("type", "craft");
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(craft.item());
                tag.putString("item", key.toString());
                tag.putInt("required", craft.required());
                tag.putInt("crafted", craft.crafted());
            }
            case QuestObjective.ReachLevel level -> {
                tag.putString("type", "reach_level");
                tag.putInt("targetLevel", level.targetLevel());
                tag.putInt("currentLevel", level.currentLevel());
            }
        }

        return tag;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Returns the mutable progress map for a player.
     */
    public Map<String, QuestProgress> getPlayerProgress(UUID playerId) {
        return playerProgress.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
    }

    /**
     * Sets progress for a player (used when loading into QuestRegistry).
     */
    public void setPlayerProgress(UUID playerId, Map<String, QuestProgress> progress) {
        playerProgress.put(playerId, new ConcurrentHashMap<>(progress));
        setDirty();
    }

    /**
     * Clears progress for a player.
     */
    public void clearPlayerProgress(UUID playerId) {
        playerProgress.remove(playerId);
        setDirty();
    }

    /**
     * Loads all progress into the QuestRegistry's in-memory maps.
     */
    public void loadIntoRegistry() {
        for (Map.Entry<UUID, Map<String, QuestProgress>> entry : playerProgress.entrySet()) {
            QuestRegistry.INSTANCE.loadPlayerProgress(entry.getKey(), entry.getValue());
        }
        LOGGER.info("[QuestProgressSavedData] Loaded progress for {} players into QuestRegistry",
            playerProgress.size());
    }

    /**
     * Saves all progress from QuestRegistry into this SavedData.
     */
    public void saveFromRegistry() {
        Map<UUID, Map<String, QuestProgress>> registryProgress = QuestRegistry.INSTANCE.getAllPlayerProgress();
        playerProgress.clear();
        playerProgress.putAll(registryProgress);
        setDirty();
    }

    // ========================================================================
    // STATIC ACCESS
    // ========================================================================

    public static QuestProgressSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(
                new Factory<>(QuestProgressSavedData::new, QuestProgressSavedData::load),
                DATA_NAME
            );
    }
}
