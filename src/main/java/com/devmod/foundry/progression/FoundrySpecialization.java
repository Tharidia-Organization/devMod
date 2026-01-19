package com.devmod.foundry.progression;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.foundry.tool.FoundryToolKind;
import com.devmod.foundry.tool.FoundryToolStats;

/**
 * Player specialization that influences foundry outcomes.
 */
public enum FoundrySpecialization {
    WEAPONSMITH("weaponsmith", 1.0f, 0.9f, 1.2f, 1.2f, 1.0f),
    TOOLSMITH("toolsmith", 1.2f, 1.2f, 0.9f, 0.9f, 1.0f),
    ALLOYIST("alloyist", 1.0f, 1.0f, 1.0f, 1.0f, 1.15f);

    private final ResourceLocation id;
    private final float durabilityMult;
    private final float miningSpeedMult;
    private final float attackDamageMult;
    private final float attackSpeedMult;
    private final float alloyYieldMult;

    FoundrySpecialization(
        String path,
        float durabilityMult,
        float miningSpeedMult,
        float attackDamageMult,
        float attackSpeedMult,
        float alloyYieldMult
    ) {
        this.id = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, path);
        this.durabilityMult = durabilityMult;
        this.miningSpeedMult = miningSpeedMult;
        this.attackDamageMult = attackDamageMult;
        this.attackSpeedMult = attackSpeedMult;
        this.alloyYieldMult = alloyYieldMult;
    }

    public ResourceLocation getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable("gui.devmod.foundry_progress.spec." + id.getPath());
    }

    public FoundryToolStats applyToStats(FoundryToolStats stats, FoundryToolKind kind) {
        if (kind.isArmor()) {
            return stats;
        }
        int durability = Math.max(1, Math.round(stats.durability() * durabilityMult));
        float miningSpeed = stats.miningSpeed() * miningSpeedMult;
        float attackDamage = stats.attackDamage() * attackDamageMult;
        float attackSpeed = stats.attackSpeed() * attackSpeedMult;
        return new FoundryToolStats(
            durability,
            miningSpeed,
            attackDamage,
            attackSpeed,
            stats.miningLevel(),
            stats.armor(),
            stats.toughness(),
            stats.knockbackResistance(),
            stats.reach(),
            stats.critChance(),
            stats.critDamage(),
            stats.drawSpeed(),
            stats.projectileSpeed()
        );
    }

    public float getAlloyYieldMultiplier() {
        return alloyYieldMult;
    }

    @Nullable
    public static FoundrySpecialization fromId(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        for (FoundrySpecialization specialization : values()) {
            if (specialization.id.equals(id)) {
                return specialization;
            }
        }
        return null;
    }
}
