package com.devmod.foundry.progression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Tracks player progress in the foundry system.
 * Stored as a capability on the player.
 */
public class FoundryPlayerProgress implements INBTSerializable<CompoundTag> {
    public static final Codec<FoundryPlayerProgress> CODEC =
        RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("tier", FoundryTier.PRIMITIVE.getName())
                    .forGetter(progress -> progress.tier.getName()),
                ResourceLocation.CODEC.optionalFieldOf("specialization")
                    .forGetter(progress -> java.util.Optional.ofNullable(progress.specialization)),
                ResourceLocation.CODEC.listOf().optionalFieldOf("unlockedMaterials", java.util.List.of())
                    .forGetter(progress -> java.util.List.copyOf(progress.unlockedMaterials)),
                ResourceLocation.CODEC.listOf().optionalFieldOf("unlockedModifiers", java.util.List.of())
                    .forGetter(progress -> java.util.List.copyOf(progress.unlockedModifiers)),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("materialMastery", java.util.Map.of())
                    .forGetter(progress -> java.util.Map.copyOf(progress.materialMastery)),
                Codec.INT.optionalFieldOf("toolsCrafted", 0).forGetter(progress -> progress.totalToolsCrafted),
                Codec.INT.optionalFieldOf("metalMelted", 0).forGetter(progress -> progress.totalMetalMelted),
                Codec.INT.optionalFieldOf("alloysCreated", 0).forGetter(progress -> progress.totalAlloysCreated),
                Codec.INT.optionalFieldOf("incidentsSurvived", 0).forGetter(progress -> progress.totalIncidentsSurvived)
            ).apply(instance, (tierName, specOpt, unlockedMaterials, unlockedModifiers, mastery,
                toolsCrafted, metalMelted, alloysCreated, incidentsSurvived) -> {
                FoundryPlayerProgress progress = new FoundryPlayerProgress();
                progress.tier = FoundryTier.fromName(tierName);
                progress.specialization = specOpt.orElse(null);
                progress.unlockedMaterials.addAll(unlockedMaterials);
                progress.unlockedModifiers.addAll(unlockedModifiers);
                progress.materialMastery.putAll(mastery);
                progress.totalToolsCrafted = toolsCrafted;
                progress.totalMetalMelted = metalMelted;
                progress.totalAlloysCreated = alloysCreated;
                progress.totalIncidentsSurvived = incidentsSurvived;
                return progress;
            })
        );
    private static final String TAG_TIER = "Tier";
    private static final String TAG_SPECIALIZATION = "Specialization";
    private static final String TAG_UNLOCKED_MATERIALS = "UnlockedMaterials";
    private static final String TAG_UNLOCKED_MODIFIERS = "UnlockedModifiers";
    private static final String TAG_MATERIAL_MASTERY = "MaterialMastery";
    private static final String TAG_TOOLS_CRAFTED = "ToolsCrafted";
    private static final String TAG_METAL_MELTED = "MetalMelted";
    private static final String TAG_ALLOYS_CREATED = "AlloysCreated";
    private static final String TAG_INCIDENTS_SURVIVED = "IncidentsSurvived";

    private FoundryTier tier = FoundryTier.PRIMITIVE;
    @Nullable
    private ResourceLocation specialization = null;

    private final Set<ResourceLocation> unlockedMaterials = new HashSet<>();
    private final Set<ResourceLocation> unlockedModifiers = new HashSet<>();
    private final Map<ResourceLocation, Integer> materialMastery = new HashMap<>();

    // Statistics
    private int totalToolsCrafted = 0;
    private int totalMetalMelted = 0; // in mb
    private int totalAlloysCreated = 0;
    private int totalIncidentsSurvived = 0;

    public FoundryPlayerProgress() {
        // Unlock basic materials
        ensureDefaultMaterials();
    }

    // Tier management

    public FoundryTier getTier() {
        return tier;
    }

    public void setTier(FoundryTier tier) {
        this.tier = tier;
    }

    public boolean tryAdvanceTier() {
        if (canAdvanceTier()) {
            tier = tier.next();
            return true;
        }
        return false;
    }

    public boolean canAdvanceTier() {
        // Requirements for tier advancement
        return switch (tier) {
            case PRIMITIVE -> totalMetalMelted >= 1000; // Melted 1 bucket
            case BASIC -> totalToolsCrafted >= 3 && totalAlloysCreated >= 1;
            case IRON_AGE -> totalToolsCrafted >= 10 && hasMaterialMastery(3);
            case ADVANCED -> totalAlloysCreated >= 10 && totalIncidentsSurvived >= 5;
            case NETHER -> totalToolsCrafted >= 50;
            case COSMIC -> false; // Max tier
        };
    }

    private boolean hasMaterialMastery(int count) {
        return materialMastery.values().stream()
                .filter(level -> level >= 10)
                .count() >= count;
    }

    public int countMasteredMaterials(int threshold) {
        return (int) materialMastery.values().stream()
            .filter(level -> level >= threshold)
            .count();
    }

    // Specialization

    @Nullable
    public ResourceLocation getSpecialization() {
        return specialization;
    }

    public void setSpecialization(@Nullable ResourceLocation specialization) {
        this.specialization = specialization;
    }

    public boolean hasSpecialization() {
        return specialization != null;
    }

    // Material unlocks

    public boolean isMaterialUnlocked(ResourceLocation materialId) {
        return unlockedMaterials.contains(materialId);
    }

    public void unlockMaterial(ResourceLocation materialId) {
        unlockedMaterials.add(materialId);
    }

    public Set<ResourceLocation> getUnlockedMaterials() {
        return Set.copyOf(unlockedMaterials);
    }

    // Modifier unlocks

    public boolean isModifierUnlocked(ResourceLocation modifierId) {
        return unlockedModifiers.contains(modifierId);
    }

    public void unlockModifier(ResourceLocation modifierId) {
        unlockedModifiers.add(modifierId);
    }

    public Set<ResourceLocation> getUnlockedModifiers() {
        return Set.copyOf(unlockedModifiers);
    }

    // Material mastery

    public int getMaterialMastery(ResourceLocation materialId) {
        return materialMastery.getOrDefault(materialId, 0);
    }

    public void addMaterialMastery(ResourceLocation materialId, int amount) {
        int current = materialMastery.getOrDefault(materialId, 0);
        materialMastery.put(materialId, Math.min(100, current + amount));
    }

    public float getMasteryBonus(ResourceLocation materialId) {
        int mastery = getMaterialMastery(materialId);
        // 0-100 mastery = 0-10% bonus
        return mastery / 1000f;
    }

    // Statistics

    public void recordToolCrafted() {
        totalToolsCrafted++;
    }

    public void recordMetalMelted(int mb) {
        totalMetalMelted += mb;
    }

    public void recordAlloyCreated() {
        totalAlloysCreated++;
    }

    public void recordIncidentSurvived() {
        totalIncidentsSurvived++;
    }

    public int getTotalToolsCrafted() {
        return totalToolsCrafted;
    }

    public int getTotalMetalMelted() {
        return totalMetalMelted;
    }

    public int getTotalAlloysCreated() {
        return totalAlloysCreated;
    }

    public int getTotalIncidentsSurvived() {
        return totalIncidentsSurvived;
    }

    // Serialization

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();

        tag.putString(TAG_TIER, tier.getName());
        if (specialization != null) {
            tag.putString(TAG_SPECIALIZATION, specialization.toString());
        }

        // Unlocked materials
        ListTag materialsTag = new ListTag();
        for (ResourceLocation id : unlockedMaterials) {
            materialsTag.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_UNLOCKED_MATERIALS, materialsTag);

        // Unlocked modifiers
        ListTag modifiersTag = new ListTag();
        for (ResourceLocation id : unlockedModifiers) {
            modifiersTag.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_UNLOCKED_MODIFIERS, modifiersTag);

        // Material mastery
        CompoundTag masteryTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : materialMastery.entrySet()) {
            masteryTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put(TAG_MATERIAL_MASTERY, masteryTag);

        // Stats
        tag.putInt(TAG_TOOLS_CRAFTED, totalToolsCrafted);
        tag.putInt(TAG_METAL_MELTED, totalMetalMelted);
        tag.putInt(TAG_ALLOYS_CREATED, totalAlloysCreated);
        tag.putInt(TAG_INCIDENTS_SURVIVED, totalIncidentsSurvived);

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        tier = FoundryTier.fromName(tag.getString(TAG_TIER));

        if (tag.contains(TAG_SPECIALIZATION)) {
            specialization = ResourceLocation.tryParse(tag.getString(TAG_SPECIALIZATION));
        }

        // Unlocked materials
        unlockedMaterials.clear();
        ListTag materialsTag = tag.getList(TAG_UNLOCKED_MATERIALS, Tag.TAG_STRING);
        for (Tag t : materialsTag) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                unlockedMaterials.add(id);
            }
        }

        // Unlocked modifiers
        unlockedModifiers.clear();
        ListTag modifiersTag = tag.getList(TAG_UNLOCKED_MODIFIERS, Tag.TAG_STRING);
        for (Tag t : modifiersTag) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                unlockedModifiers.add(id);
            }
        }

        // Material mastery
        materialMastery.clear();
        CompoundTag masteryTag = tag.getCompound(TAG_MATERIAL_MASTERY);
        for (String key : masteryTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                materialMastery.put(id, masteryTag.getInt(key));
            }
        }

        // Stats
        totalToolsCrafted = tag.getInt(TAG_TOOLS_CRAFTED);
        totalMetalMelted = tag.getInt(TAG_METAL_MELTED);
        totalAlloysCreated = tag.getInt(TAG_ALLOYS_CREATED);
        totalIncidentsSurvived = tag.getInt(TAG_INCIDENTS_SURVIVED);
        ensureDefaultMaterials();
    }

    private void ensureDefaultMaterials() {
        unlockedMaterials.add(ResourceLocation.fromNamespaceAndPath("devmod", "wood"));
        unlockedMaterials.add(ResourceLocation.fromNamespaceAndPath("devmod", "stone"));
    }
}
