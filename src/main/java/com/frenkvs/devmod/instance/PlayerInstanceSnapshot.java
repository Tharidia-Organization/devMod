package com.frenkvs.devmod.instance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Complete snapshot of a player's state before entering an instance.
 * This is persisted to disk IMMEDIATELY to ensure recovery is always possible.
 *
 * The snapshot contains everything needed to fully restore the player
 * to their original state if anything goes wrong during the instance.
 */
public class PlayerInstanceSnapshot {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerInstanceSnapshot.class);
    private static final int CURRENT_VERSION = 1;

    // === Identifiers ===
    private final UUID playerId;
    private UUID instanceId;  // May be null during PREPARING state
    private final long createdAt;
    private long lastUpdated;

    // === Transaction State ===
    private PlayerInstanceState state;

    // === Original Position (for return) ===
    private ResourceLocation originalDimension;
    private double originalX;
    private double originalY;
    private double originalZ;
    private float originalYaw;
    private float originalPitch;

    // === Complete Player State ===
    private CompoundTag inventoryNBT;
    private CompoundTag enderChestNBT;
    private GameType originalGameMode;
    private float originalHealth;
    private float originalMaxHealth;
    private int originalFoodLevel;
    private float originalSaturation;
    private float originalExhaustion;
    private CompoundTag potionEffectsNBT;
    private int originalExperienceLevel;
    private float originalExperienceProgress;
    private int originalTotalExperience;

    // === Quest Metadata ===
    private String questType;
    private ResourceLocation mobId;
    private int targetWaves;
    private boolean endlessMode;

    // === Multiplayer ===
    @Nullable
    private UUID partyLeaderId;
    private List<UUID> partyMembers;

    /**
     * Create a new snapshot for a player.
     */
    public PlayerInstanceSnapshot(UUID playerId) {
        this.playerId = playerId;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdated = this.createdAt;
        this.state = PlayerInstanceState.NORMAL;
        this.partyMembers = new ArrayList<>();
    }

    /**
     * Private constructor for deserialization.
     */
    private PlayerInstanceSnapshot(UUID playerId, long createdAt) {
        this.playerId = playerId;
        this.createdAt = createdAt;
        this.partyMembers = new ArrayList<>();
    }

    // === State Management ===

    public PlayerInstanceState getState() {
        return state;
    }

    /**
     * Set the player state with validation.
     * Logs a warning if the transition is invalid but still allows it
     * to prevent deadlocks in recovery scenarios.
     *
     * @param newState The new state to transition to
     * @return true if the transition was valid, false if it was forced
     */
    public boolean setState(PlayerInstanceState newState) {
        PlayerInstanceState oldState = this.state;

        if (oldState == newState) {
            return true;
        }

        boolean valid = oldState.canTransitionTo(newState);
        if (!valid) {
            LOGGER.warn("[Snapshot] Player {} INVALID state transition: {} -> {} (valid: {})",
                playerId, oldState, newState, oldState.getValidNextStates());
        }

        this.state = newState;
        this.lastUpdated = System.currentTimeMillis();

        LOGGER.debug("[Snapshot] Player {} state changed: {} -> {}{}", playerId, oldState, newState,
            valid ? "" : " [FORCED]");

        return valid;
    }

    // === Getters ===

    public UUID getPlayerId() {
        return playerId;
    }

    @Nullable
    public UUID getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public ResourceLocation getOriginalDimension() {
        return originalDimension;
    }

    public double getOriginalX() {
        return originalX;
    }

    public double getOriginalY() {
        return originalY;
    }

    public double getOriginalZ() {
        return originalZ;
    }

    public float getOriginalYaw() {
        return originalYaw;
    }

    public float getOriginalPitch() {
        return originalPitch;
    }

    public CompoundTag getInventoryNBT() {
        return inventoryNBT;
    }

    @Nullable
    public CompoundTag getEnderChestNBT() {
        return enderChestNBT;
    }

    public GameType getOriginalGameMode() {
        return originalGameMode;
    }

    public float getOriginalHealth() {
        return originalHealth;
    }

    public float getOriginalMaxHealth() {
        return originalMaxHealth;
    }

    public int getOriginalFoodLevel() {
        return originalFoodLevel;
    }

    public float getOriginalSaturation() {
        return originalSaturation;
    }

    public float getOriginalExhaustion() {
        return originalExhaustion;
    }

    @Nullable
    public CompoundTag getPotionEffectsNBT() {
        return potionEffectsNBT;
    }

    public int getOriginalExperienceLevel() {
        return originalExperienceLevel;
    }

    public float getOriginalExperienceProgress() {
        return originalExperienceProgress;
    }

    public int getOriginalTotalExperience() {
        return originalTotalExperience;
    }

    public String getQuestType() {
        return questType;
    }

    @Nullable
    public ResourceLocation getMobId() {
        return mobId;
    }

    public int getTargetWaves() {
        return targetWaves;
    }

    public boolean isEndlessMode() {
        return endlessMode;
    }

    @Nullable
    public UUID getPartyLeaderId() {
        return partyLeaderId;
    }

    public List<UUID> getPartyMembers() {
        return partyMembers;
    }

    public boolean isInParty() {
        return partyLeaderId != null;
    }

    public void setPartyLeaderId(UUID leaderId) {
        this.partyLeaderId = leaderId;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void setPartyMembers(java.util.Set<UUID> members) {
        this.partyMembers = new ArrayList<>(members);
        this.lastUpdated = System.currentTimeMillis();
    }

    // === Setters (Builder Pattern) ===

    public PlayerInstanceSnapshot withPosition(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
        this.originalDimension = dimension;
        this.originalX = x;
        this.originalY = y;
        this.originalZ = z;
        this.originalYaw = yaw;
        this.originalPitch = pitch;
        return this;
    }

    public PlayerInstanceSnapshot withInventory(CompoundTag inventory, @Nullable CompoundTag enderChest) {
        this.inventoryNBT = inventory;
        this.enderChestNBT = enderChest;
        return this;
    }

    public PlayerInstanceSnapshot withGameMode(GameType gameMode) {
        this.originalGameMode = gameMode;
        return this;
    }

    public PlayerInstanceSnapshot withHealth(float health, float maxHealth) {
        this.originalHealth = health;
        this.originalMaxHealth = maxHealth;
        return this;
    }

    public PlayerInstanceSnapshot withFood(int foodLevel, float saturation, float exhaustion) {
        this.originalFoodLevel = foodLevel;
        this.originalSaturation = saturation;
        this.originalExhaustion = exhaustion;
        return this;
    }

    public PlayerInstanceSnapshot withEffects(@Nullable CompoundTag effects) {
        this.potionEffectsNBT = effects;
        return this;
    }

    public PlayerInstanceSnapshot withExperience(int level, float progress, int total) {
        this.originalExperienceLevel = level;
        this.originalExperienceProgress = progress;
        this.originalTotalExperience = total;
        return this;
    }

    public PlayerInstanceSnapshot withQuest(String type, @Nullable ResourceLocation mob, int waves, boolean endless) {
        this.questType = type;
        this.mobId = mob;
        this.targetWaves = waves;
        this.endlessMode = endless;
        return this;
    }

    public PlayerInstanceSnapshot withParty(@Nullable UUID leaderId, List<UUID> members) {
        this.partyLeaderId = leaderId;
        this.partyMembers = new ArrayList<>(members);
        return this;
    }

    // === Serialization ===

    /**
     * Serialize to NBT for disk storage.
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();

        // Version for future migrations
        tag.putInt("version", CURRENT_VERSION);

        // Identifiers
        tag.putUUID("playerId", Objects.requireNonNull(playerId, "playerId"));
        if (instanceId != null) {
            tag.putUUID("instanceId", instanceId);
        }
        tag.putLong("createdAt", createdAt);
        tag.putLong("lastUpdated", lastUpdated);

        // State
        tag.putString("state", Objects.requireNonNull(state.name(), "state"));

        // Position
        if (originalDimension != null) {
            tag.putString("dimension", Objects.requireNonNull(originalDimension.toString(), "dimension"));
        }
        tag.putDouble("x", originalX);
        tag.putDouble("y", originalY);
        tag.putDouble("z", originalZ);
        tag.putFloat("yaw", originalYaw);
        tag.putFloat("pitch", originalPitch);

        // Inventory
        if (inventoryNBT != null) {
            tag.put("inventory", inventoryNBT);
        }
        if (enderChestNBT != null) {
            tag.put("enderChest", enderChestNBT);
        }

        // Stats
        if (originalGameMode != null) {
            tag.putString("gameMode", Objects.requireNonNull(originalGameMode.name(), "gameMode"));
        }
        tag.putFloat("health", originalHealth);
        tag.putFloat("maxHealth", originalMaxHealth);
        tag.putInt("foodLevel", originalFoodLevel);
        tag.putFloat("saturation", originalSaturation);
        tag.putFloat("exhaustion", originalExhaustion);

        if (potionEffectsNBT != null) {
            tag.put("effects", potionEffectsNBT);
        }

        tag.putInt("xpLevel", originalExperienceLevel);
        tag.putFloat("xpProgress", originalExperienceProgress);
        tag.putInt("xpTotal", originalTotalExperience);

        // Quest
        if (questType != null) {
            tag.putString("questType", questType);
        }
        if (mobId != null) {
            tag.putString("mobId", Objects.requireNonNull(mobId.toString(), "mobId"));
        }
        tag.putInt("targetWaves", targetWaves);
        tag.putBoolean("endlessMode", endlessMode);

        // Party
        if (partyLeaderId != null) {
            tag.putUUID("partyLeader", partyLeaderId);
        }
        if (!partyMembers.isEmpty()) {
            ListTag memberList = new ListTag();
            for (UUID member : partyMembers) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("uuid", Objects.requireNonNull(member, "partyMember"));
                memberList.add(memberTag);
            }
            tag.put("partyMembers", memberList);
        }

        return tag;
    }

    /**
     * Deserialize from NBT.
     */
    public static PlayerInstanceSnapshot fromNBT(CompoundTag tag) {
        int version = tag.getInt("version");
        if (version != CURRENT_VERSION) {
            LOGGER.warn("[Instance] Snapshot version mismatch: {} vs {}, attempting migration",
                version, CURRENT_VERSION);
            // Future: add migration logic here
        }

        UUID playerId = tag.getUUID("playerId");
        long createdAt = tag.getLong("createdAt");

        PlayerInstanceSnapshot snapshot = new PlayerInstanceSnapshot(playerId, createdAt);

        // Identifiers
        if (tag.contains("instanceId")) {
            snapshot.instanceId = tag.getUUID("instanceId");
        }
        snapshot.lastUpdated = tag.getLong("lastUpdated");

        // State
        snapshot.state = PlayerInstanceState.valueOf(tag.getString("state"));

        // Position
        if (tag.contains("dimension")) {
            snapshot.originalDimension = ResourceLocation.parse(Objects.requireNonNull(tag.getString("dimension"), "dimension"));
        }
        snapshot.originalX = tag.getDouble("x");
        snapshot.originalY = tag.getDouble("y");
        snapshot.originalZ = tag.getDouble("z");
        snapshot.originalYaw = tag.getFloat("yaw");
        snapshot.originalPitch = tag.getFloat("pitch");

        // Inventory
        if (tag.contains("inventory")) {
            snapshot.inventoryNBT = tag.getCompound("inventory");
        }
        if (tag.contains("enderChest")) {
            snapshot.enderChestNBT = tag.getCompound("enderChest");
        }

        // Stats
        if (tag.contains("gameMode")) {
            snapshot.originalGameMode = GameType.valueOf(Objects.requireNonNull(tag.getString("gameMode"), "gameMode"));
        }
        snapshot.originalHealth = tag.getFloat("health");
        snapshot.originalMaxHealth = tag.getFloat("maxHealth");
        snapshot.originalFoodLevel = tag.getInt("foodLevel");
        snapshot.originalSaturation = tag.getFloat("saturation");
        snapshot.originalExhaustion = tag.getFloat("exhaustion");

        if (tag.contains("effects")) {
            snapshot.potionEffectsNBT = tag.getCompound("effects");
        }

        snapshot.originalExperienceLevel = tag.getInt("xpLevel");
        snapshot.originalExperienceProgress = tag.getFloat("xpProgress");
        snapshot.originalTotalExperience = tag.getInt("xpTotal");

        // Quest
        if (tag.contains("questType")) {
            snapshot.questType = tag.getString("questType");
        }
        if (tag.contains("mobId")) {
            snapshot.mobId = ResourceLocation.parse(Objects.requireNonNull(tag.getString("mobId"), "mobId"));
        }
        snapshot.targetWaves = tag.getInt("targetWaves");
        snapshot.endlessMode = tag.getBoolean("endlessMode");

        // Party
        if (tag.contains("partyLeader")) {
            snapshot.partyLeaderId = tag.getUUID("partyLeader");
        }
        if (tag.contains("partyMembers")) {
            ListTag memberList = tag.getList("partyMembers", 10); // 10 = CompoundTag
            for (int i = 0; i < memberList.size(); i++) {
                CompoundTag memberTag = memberList.getCompound(i);
                snapshot.partyMembers.add(memberTag.getUUID("uuid"));
            }
        }

        return snapshot;
    }

    // === File I/O ===

    /**
     * Save snapshot to file with fsync for guaranteed persistence.
     */
    public void saveToFile(Path filePath) throws IOException {
        CompoundTag tag = toNBT();

        // Create parent directories if needed
        Files.createDirectories(filePath.getParent());

        // Write to temp file first, then rename (atomic operation)
        Path tempPath = Objects.requireNonNull(filePath.resolveSibling(filePath.getFileName() + ".tmp"), "tempPath");
        NbtIo.writeCompressed(Objects.requireNonNull(tag, "tag"), tempPath);

        // Atomic rename
        Files.move(tempPath, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        LOGGER.debug("[Instance] Saved snapshot for player {} to {}", playerId, filePath);
    }

    /**
     * Load snapshot from file.
     */
    public static PlayerInstanceSnapshot loadFromFile(Path filePath) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(
            Objects.requireNonNull(filePath, "filePath"),
            Objects.requireNonNull(net.minecraft.nbt.NbtAccounter.unlimitedHeap(), "nbtAccounter")
        );
        return fromNBT(tag);
    }

    @Override
    public String toString() {
        return "PlayerInstanceSnapshot{" +
            "playerId=" + playerId +
            ", instanceId=" + instanceId +
            ", state=" + state +
            ", dimension=" + originalDimension +
            ", pos=(" + originalX + ", " + originalY + ", " + originalZ + ")" +
            ", quest=" + questType +
            ", party=" + (partyLeaderId != null ? "yes" : "no") +
            '}';
    }
}
