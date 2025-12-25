package com.devmod.combat.signature;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soul Imprint - Weapon history and accumulated achievements.
 *
 * Every weapon that deals significant damage in Endurance Quests accumulates
 * a "Soul Imprint" that tracks the player's accomplishments with that weapon.
 * As stats accumulate, the weapon unlocks permanent traits and evolves its name.
 *
 * This creates a sense of attachment and progression with individual weapons,
 * encouraging players to develop mastery rather than constantly switching gear.
 */
public class SoulImprint {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoulImprint.class);

    private static final String NBT_KEY = "SoulImprint";
    private static final String NBT_OWNER_ID = "OwnerId";
    private static final String NBT_OWNER_NAME = "OwnerName";
    private static final String NBT_STATS = "Stats";
    private static final String NBT_TRAITS = "Traits";
    private static final String NBT_STAGE = "Stage";
    private static final String NBT_CUSTOM_NAME = "CustomName";
    private static final String NBT_CREATED_TIME = "CreatedTime";

    // Owner information
    private UUID ownerId;
    private String ownerName;

    // Accumulated statistics
    private final EnumMap<ImprintStat, Integer> stats = new EnumMap<>(ImprintStat.class);

    // Unlocked traits
    private final Set<WeaponTrait> unlockedTraits = new LinkedHashSet<>();

    // Evolution stage (0-4)
    private int evolutionStage = 0;

    // Custom name (for stage 4)
    @Nullable
    private String customName;

    // Timestamp when imprint was created
    private long createdTime;

    /**
     * Statistics tracked for soul imprinting.
     */
    public enum ImprintStat {
        TOTAL_KILLS("Total Kills", 500),
        HEADSHOTS("Headshots", 100),
        BOSS_KILLS("Boss Kills", 50),
        SSS_WAVES("SSS Waves Completed", 10),
        CRITICAL_HITS("Critical Hits", 200),
        PERFECT_RESONANCES("Perfect Resonances", 10),
        HIGH_COMBOS("50+ Combos", 25),
        NO_HIT_WAVES("No-Hit Waves", 10),
        TOTAL_DAMAGE("Total Damage Dealt", 100000),
        EXECUTE_KILLS("Execute Kills", 30),
        MULTI_KILLS("Multi-Kills", 50),
        PARRY_KILLS("Parry Counter Kills", 20);

        public final String displayName;
        public final int traitThreshold;

        ImprintStat(String displayName, int traitThreshold) {
            this.displayName = displayName;
            this.traitThreshold = traitThreshold;
        }
    }

    public SoulImprint() {
        this.createdTime = System.currentTimeMillis();
        for (ImprintStat stat : ImprintStat.values()) {
            stats.put(stat, 0);
        }
    }

    public SoulImprint(UUID ownerId, String ownerName) {
        this();
        this.ownerId = ownerId;
        this.ownerName = ownerName;
    }

    // ========== Stat Recording ==========

    /**
     * Record a stat increment.
     * Automatically checks for trait unlocks.
     */
    public boolean recordStat(ImprintStat stat, int amount) {
        if (amount <= 0) return false;

        int oldValue = stats.getOrDefault(stat, 0);
        int newValue = oldValue + amount;
        stats.put(stat, newValue);

        // Check for trait unlock
        boolean unlockedNew = false;
        if (oldValue < stat.traitThreshold && newValue >= stat.traitThreshold) {
            WeaponTrait trait = WeaponTraitRegistry.INSTANCE.getTraitForStat(stat);
            if (trait != null && !unlockedTraits.contains(trait)) {
                unlockedTraits.add(trait);
                unlockedNew = true;
                checkEvolutionStage();
                LOGGER.info("[SoulImprint] Weapon unlocked trait: {} ({}={}/{})",
                    trait.getId(), stat.name(), newValue, stat.traitThreshold);
            }
        }

        return unlockedNew;
    }

    /**
     * Get current value of a stat.
     */
    public int getStat(ImprintStat stat) {
        return stats.getOrDefault(stat, 0);
    }

    /**
     * Get progress toward a trait (0.0 to 1.0+).
     */
    public float getTraitProgress(ImprintStat stat) {
        return (float) getStat(stat) / stat.traitThreshold;
    }

    // ========== Evolution ==========

    /**
     * Check and update evolution stage based on unlocked traits.
     */
    private void checkEvolutionStage() {
        int traitCount = unlockedTraits.size();

        int newStage;
        if (traitCount >= 5) {
            newStage = 4;
        } else if (traitCount >= 3) {
            newStage = 3;
        } else if (traitCount >= 1) {
            newStage = 2;
        } else if (ownerId != null) {
            newStage = 1;
        } else {
            newStage = 0;
        }

        if (newStage > evolutionStage) {
            evolutionStage = newStage;
            LOGGER.info("[SoulImprint] Weapon evolved to stage {}", evolutionStage);
        }
    }

    /**
     * Generate the evolved display name for this weapon.
     */
    @Nonnull
    public Component getEvolvedName(ItemStack stack) {
        String baseName = stack.getHoverName().getString();

        return switch (evolutionStage) {
            case 0 -> Component.literal(baseName);
            case 1 -> Component.literal(ownerName + "'s " + baseName)
                .withStyle(Style.EMPTY.withColor(0xAAAAAA));
            case 2 -> {
                // Find primary trait for naming
                WeaponTrait primaryTrait = getPrimaryTrait();
                String traitName = primaryTrait != null ? primaryTrait.getAdjective() : "Enhanced";
                yield Component.literal(ownerName + "'s " + traitName + " " + getWeaponType(baseName))
                    .withStyle(Style.EMPTY.withColor(0x55FF55));
            }
            case 3 -> {
                WeaponTrait primaryTrait = getPrimaryTrait();
                String traitName = primaryTrait != null ? primaryTrait.getAdjective() : "Legendary";
                yield Component.literal(ownerName + "'s " + traitName + " " + getWeaponType(baseName))
                    .withStyle(Style.EMPTY.withColor(0x5555FF).withBold(true));
            }
            case 4 -> {
                // Unique legendary name
                String uniqueName = customName != null ? customName : generateUniqueName();
                String title = generateTitle();
                yield Component.literal(uniqueName + ", the " + title)
                    .withStyle(Style.EMPTY.withColor(0xFF55FF).withBold(true).withItalic(true));
            }
            default -> Component.literal(baseName);
        };
    }

    /**
     * Get the primary (first unlocked) trait.
     */
    @Nullable
    public WeaponTrait getPrimaryTrait() {
        return unlockedTraits.isEmpty() ? null : unlockedTraits.iterator().next();
    }

    /**
     * Extract weapon type from name (e.g., "Diamond Sword" -> "Blade").
     */
    private String getWeaponType(String baseName) {
        String lower = baseName.toLowerCase();
        if (lower.contains("sword")) return "Blade";
        if (lower.contains("axe")) return "Cleaver";
        if (lower.contains("bow")) return "Bow";
        if (lower.contains("crossbow")) return "Arbalest";
        if (lower.contains("mace")) return "Maul";
        if (lower.contains("trident")) return "Trident";
        if (lower.contains("spear")) return "Lance";
        if (lower.contains("dagger")) return "Fang";
        if (lower.contains("hammer")) return "Crusher";
        return "Weapon";
    }

    /**
     * Generate a unique legendary name for stage 4 weapons.
     */
    private String generateUniqueName() {
        // Generate based on primary stats
        List<String> prefixes = List.of(
            "Voidrender", "Soulreaver", "Dawnbreaker", "Nightfall",
            "Stormcaller", "Frostbite", "Hellfire", "Eclipse",
            "Oblivion", "Genesis", "Ragnarok", "Eternity"
        );

        // Use owner name hash for consistent naming
        int hash = ownerName != null ? ownerName.hashCode() : (int) createdTime;
        return prefixes.get(Math.abs(hash) % prefixes.size());
    }

    /**
     * Generate a title for stage 4 weapons based on dominant traits.
     */
    private String generateTitle() {
        if (unlockedTraits.isEmpty()) return "Legend";

        // Find most impressive trait
        WeaponTrait best = null;
        int bestProgress = 0;

        for (WeaponTrait trait : unlockedTraits) {
            int progress = getStat(trait.getRequiredStat());
            if (progress > bestProgress) {
                bestProgress = progress;
                best = trait;
            }
        }

        if (best == null) return "Legend";

        return switch (best.getRequiredStat()) {
            case HEADSHOTS -> "Executioner";
            case BOSS_KILLS -> "Tyrant Slayer";
            case SSS_WAVES -> "SSS Blade";
            case TOTAL_KILLS -> "Bloodthirsty";
            case CRITICAL_HITS -> "Precision Edge";
            case PERFECT_RESONANCES -> "Harmonic";
            case HIGH_COMBOS -> "Relentless";
            case NO_HIT_WAVES -> "Guardian";
            default -> "Legend";
        };
    }

    // ========== NBT Persistence ==========

    /**
     * Save imprint to item's custom data component.
     */
    public void saveToItem(ItemStack stack) {
        CompoundTag tag = new CompoundTag();

        if (ownerId != null) {
            tag.putUUID(NBT_OWNER_ID, ownerId);
        }
        if (ownerName != null) {
            tag.putString(NBT_OWNER_NAME, ownerName);
        }

        // Save stats
        CompoundTag statsTag = new CompoundTag();
        for (var entry : stats.entrySet()) {
            if (entry.getValue() > 0) {
                statsTag.putInt(entry.getKey().name(), entry.getValue());
            }
        }
        tag.put(NBT_STATS, statsTag);

        // Save traits
        ListTag traitsList = new ListTag();
        for (WeaponTrait trait : unlockedTraits) {
            traitsList.add(StringTag.valueOf(trait.getId().toString()));
        }
        tag.put(NBT_TRAITS, traitsList);

        tag.putInt(NBT_STAGE, evolutionStage);
        tag.putLong(NBT_CREATED_TIME, createdTime);

        if (customName != null) {
            tag.putString(NBT_CUSTOM_NAME, customName);
        }

        // Store in item's custom data component (1.21.1 API)
        final CompoundTag finalTag = tag;
        CustomData.update(requireNonNull(DataComponents.CUSTOM_DATA), requireNonNull(stack),
            existingTag -> existingTag.put(NBT_KEY, finalTag));
    }

    /**
     * Load imprint from item's custom data component.
     */
    @Nullable
    public static SoulImprint loadFromItem(ItemStack stack) {
        CustomData customData = stack.get(requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData == null || !customData.contains(NBT_KEY)) {
            return null;
        }

        CompoundTag tag = customData.copyTag().getCompound(NBT_KEY);
        SoulImprint imprint = new SoulImprint();

        if (tag.hasUUID(NBT_OWNER_ID)) {
            imprint.ownerId = tag.getUUID(NBT_OWNER_ID);
        }
        if (tag.contains(NBT_OWNER_NAME)) {
            imprint.ownerName = tag.getString(NBT_OWNER_NAME);
        }

        // Load stats
        CompoundTag statsTag = tag.getCompound(NBT_STATS);
        for (ImprintStat stat : ImprintStat.values()) {
            if (statsTag.contains(stat.name())) {
                imprint.stats.put(stat, statsTag.getInt(stat.name()));
            }
        }

        // Load traits
        ListTag traitsList = tag.getList(NBT_TRAITS, Tag.TAG_STRING);
        for (int i = 0; i < traitsList.size(); i++) {
            String traitId = traitsList.getString(i);
            WeaponTrait trait = WeaponTraitRegistry.INSTANCE.getTrait(traitId);
            if (trait != null) {
                imprint.unlockedTraits.add(trait);
            }
        }

        imprint.evolutionStage = tag.getInt(NBT_STAGE);
        imprint.createdTime = tag.getLong(NBT_CREATED_TIME);

        if (tag.contains(NBT_CUSTOM_NAME)) {
            imprint.customName = tag.getString(NBT_CUSTOM_NAME);
        }

        return imprint;
    }

    /**
     * Check if an item has a soul imprint.
     */
    public static boolean hasImprint(ItemStack stack) {
        CustomData customData = stack.get(requireNonNull(DataComponents.CUSTOM_DATA));
        return customData != null && customData.contains(NBT_KEY);
    }

    // ========== Getters ==========

    public UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public int getEvolutionStage() { return evolutionStage; }
    public Set<WeaponTrait> getUnlockedTraits() { return Collections.unmodifiableSet(unlockedTraits); }
    public int getTraitCount() { return unlockedTraits.size(); }
    public long getCreatedTime() { return createdTime; }

    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public void setCustomName(String customName) { this.customName = customName; }

    /**
     * Check if this imprint belongs to a specific player.
     */
    public boolean isOwnedBy(UUID playerId) {
        return ownerId != null && ownerId.equals(playerId);
    }

    /**
     * Get total stats summary for display.
     */
    public int getTotalKills() { return getStat(ImprintStat.TOTAL_KILLS); }
    public int getTotalDamage() { return getStat(ImprintStat.TOTAL_DAMAGE); }
}
