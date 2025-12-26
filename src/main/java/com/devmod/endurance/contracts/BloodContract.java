package com.devmod.endurance.contracts;

import java.util.Objects;
import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class BloodContract {

    private final ResourceLocation id;
    private final String nameKey;
    private final String descriptionKey;
    private final ContractTier tier;
    private final ContractEffect effect;
    private final float rewardMultiplier;
    private final boolean isPartyContract;
    private final BiPredicate<ServerPlayer, ContractContext> violationCheck;

    /**
     * Contract tiers determine visual style and base difficulty.
     */
    public enum ContractTier {
        MINOR(0.5f, 0xFFAA00, "Minor"),      // Small risk, small reward
        STANDARD(1.0f, 0xFF6600, "Standard"), // Balanced
        MAJOR(1.5f, 0xFF3300, "Major"),       // High risk
        BLOOD(2.0f, 0xFF0000, "Blood");       // Extreme risk

        public final float baseMultiplier;
        public final int color;
        public final String displayName;

        ContractTier(float baseMultiplier, int color, String displayName) {
            this.baseMultiplier = baseMultiplier;
            this.color = color;
            this.displayName = displayName;
        }
    }

    /**
     * Contract effects applied during the wave.
     */
    public enum ContractEffect {
        // Damage modifiers
        GLASS_CANNON("+50% damage dealt, +100% damage taken"),
        BERSERKER("+75% damage when below 50% health, no healing"),
        VAMPIRE("No natural regen, lifesteal on kill"),

        // Restriction contracts
        PACIFIST("Cannot use primary weapon slot"),
        BLINDFOLD("No mob health bars or damage numbers"),
        SILENCE("No active abilities"),

        // Time pressure
        HASTE("Complete wave in 60 seconds or fail"),
        BLITZ("30 seconds, but +200% damage"),

        // Skill checks
        HUBRIS("Drop below S rank = instant death"),
        PERFECTIONIST("Take any damage = contract violated"),
        UNTOUCHABLE("3 hits allowed, then death"),

        // Party contracts
        SACRIFICE("Random ally takes 2x damage, party gets bonus"),
        LINKED_FATE("If one dies, all die"),
        BLOOD_PACT("Share damage across party");

        public final String description;

        ContractEffect(String description) {
            this.description = description;
        }
    }

    /**
     * Context passed to violation checks.
     */
    public record ContractContext(
        float damageDealt,
        float damageTaken,
        int hitsTaken,
        long waveStartTime,
        long currentTime,
        int currentStyleRank,
        boolean playerDied
    ) {}

    private BloodContract(Builder builder) {
        this.id = Objects.requireNonNull(builder.id);
        this.nameKey = Objects.requireNonNull(builder.nameKey);
        this.descriptionKey = Objects.requireNonNull(builder.descriptionKey);
        this.tier = Objects.requireNonNull(builder.tier);
        this.effect = Objects.requireNonNull(builder.effect);
        this.rewardMultiplier = builder.rewardMultiplier;
        this.isPartyContract = builder.isPartyContract;
        this.violationCheck = builder.violationCheck != null ?
            builder.violationCheck : (p, c) -> false;
    }

    // ========== Getters ==========

    @Nonnull
    public ResourceLocation getId() { return id; }

    @Nonnull
    public String getNameKey() { return nameKey; }

    @Nonnull
    public String getDescriptionKey() { return descriptionKey; }

    @Nonnull
    public Component getName() {
        return Component.translatable(nameKey);
    }

    @Nonnull
    public Component getDescription() {
        return Component.translatable(descriptionKey);
    }

    @Nonnull
    public ContractTier getTier() { return tier; }

    @Nonnull
    public ContractEffect getEffect() { return effect; }

    public float getRewardMultiplier() { return rewardMultiplier; }

    public float getTotalMultiplier() {
        return tier.baseMultiplier * rewardMultiplier;
    }

    public boolean isPartyContract() { return isPartyContract; }

    public int getColor() { return tier.color; }

    /**
     * Check if this contract is violated given the current context.
     */
    public boolean isViolated(ServerPlayer player, ContractContext context) {
        return violationCheck.test(player, context);
    }

    /**
     * Check if this contract is compatible with another.
     * Some contracts are mutually exclusive.
     */
    public boolean isCompatibleWith(BloodContract other) {
        if (other == null) return true;

        // Party contracts can't stack
        if (this.isPartyContract && other.isPartyContract) return false;

        // Same effect can't be signed twice
        if (this.effect == other.effect) return false;

        // Specific incompatibilities
        if (this.effect == ContractEffect.VAMPIRE && other.effect == ContractEffect.BERSERKER) return false;
        if (this.effect == ContractEffect.PERFECTIONIST && other.effect == ContractEffect.UNTOUCHABLE) return false;

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BloodContract that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "BloodContract{" + id + ", tier=" + tier + ", multiplier=" + getTotalMultiplier() + "x}";
    }

    // ========== Builder ==========

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private String nameKey;
        private String descriptionKey;
        private ContractTier tier = ContractTier.STANDARD;
        private ContractEffect effect;
        private float rewardMultiplier = 1.0f;
        private boolean isPartyContract = false;
        private BiPredicate<ServerPlayer, ContractContext> violationCheck;

        private Builder(ResourceLocation id) {
            this.id = id;
            this.nameKey = "devmod.contract." + id.getPath();
            this.descriptionKey = "devmod.contract." + id.getPath() + ".desc";
        }

        public Builder nameKey(String nameKey) {
            this.nameKey = nameKey;
            return this;
        }

        public Builder descriptionKey(String descriptionKey) {
            this.descriptionKey = descriptionKey;
            return this;
        }

        public Builder tier(ContractTier tier) {
            this.tier = tier;
            return this;
        }

        public Builder effect(ContractEffect effect) {
            this.effect = effect;
            return this;
        }

        public Builder rewardMultiplier(float multiplier) {
            this.rewardMultiplier = multiplier;
            return this;
        }

        public Builder partyContract() {
            this.isPartyContract = true;
            return this;
        }

        public Builder violationCheck(BiPredicate<ServerPlayer, ContractContext> check) {
            this.violationCheck = check;
            return this;
        }

        public BloodContract build() {
            return new BloodContract(this);
        }
    }
}
