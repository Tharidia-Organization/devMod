package com.devmod.combat.signature;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class WeaponTrait {

    private final ResourceLocation id;
    private final String nameKey;
    private final String descriptionKey;
    private final String adjective;
    private final SoulImprint.ImprintStat requiredStat;
    private final TraitEffect effect;
    private final int color;

    /**
     * Trait effect types.
     */
    public enum TraitEffectType {
        DAMAGE_PERCENT,      // +X% all damage
        HEADSHOT_BONUS,      // +X% headshot damage
        BOSS_DAMAGE,         // +X% damage vs bosses
        STYLE_GAIN,          // +X% style points
        LIFESTEAL,           // X% damage as healing
        CRIT_CHANCE,         // +X% crit chance
        COMBO_DECAY,         // -X% combo decay rate
        DAMAGE_REDUCTION,    // +X% damage reduction
        RESONANCE_BONUS,     // +X% resonance damage
        EXECUTE_THRESHOLD    // Execute below X% health
    }

    /**
     * Effect definition.
     */
    public record TraitEffect(
        TraitEffectType type,
        float value
    ) {
        public String getDescription() {
            return switch (type) {
                case DAMAGE_PERCENT -> String.format("+%.0f%% damage", value * 100);
                case HEADSHOT_BONUS -> String.format("+%.0f%% headshot damage", value * 100);
                case BOSS_DAMAGE -> String.format("+%.0f%% damage vs bosses", value * 100);
                case STYLE_GAIN -> String.format("+%.0f%% style points", value * 100);
                case LIFESTEAL -> String.format("%.1f%% lifesteal", value * 100);
                case CRIT_CHANCE -> String.format("+%.0f%% critical chance", value * 100);
                case COMBO_DECAY -> String.format("-%.0f%% combo decay", value * 100);
                case DAMAGE_REDUCTION -> String.format("+%.0f%% damage reduction", value * 100);
                case RESONANCE_BONUS -> String.format("+%.0f%% resonance damage", value * 100);
                case EXECUTE_THRESHOLD -> String.format("Execute below %.0f%% HP", value * 100);
            };
        }
    }

    private WeaponTrait(Builder builder) {
        this.id = Objects.requireNonNull(builder.id);
        this.nameKey = "devmod.trait." + id.getPath();
        this.descriptionKey = "devmod.trait." + id.getPath() + ".desc";
        this.adjective = Objects.requireNonNull(builder.adjective);
        this.requiredStat = Objects.requireNonNull(builder.requiredStat);
        this.effect = Objects.requireNonNull(builder.effect);
        this.color = builder.color;
    }

    // ========== Getters ==========

    @Nonnull
    public ResourceLocation getId() { return id; }

    @Nonnull
    public String getNameKey() { return nameKey; }

    @Nonnull
    public Component getName() {
        return Component.translatable(nameKey);
    }

    @Nonnull
    public Component getDescription() {
        return Component.translatable(descriptionKey);
    }

    @Nonnull
    public String getAdjective() { return adjective; }

    @Nonnull
    public SoulImprint.ImprintStat getRequiredStat() { return requiredStat; }

    @Nonnull
    public TraitEffect getEffect() { return effect; }

    public int getColor() { return color; }

    // ========== Effect Application ==========

    /**
     * Calculate damage modification from this trait.
     *
     * @param baseDamage Base damage being dealt
     * @param stack The weapon with this trait
     * @param attacker The entity dealing damage
     * @param target The entity being damaged
     * @param isHeadshot Whether this is a headshot
     * @param isBoss Whether target is a boss
     * @return Modified damage
     */
    public float modifyDamage(float baseDamage, ItemStack stack, LivingEntity attacker,
                              LivingEntity target, boolean isHeadshot, boolean isBoss) {
        float bonus = 0;

        switch (effect.type()) {
            case DAMAGE_PERCENT:
                bonus = baseDamage * effect.value();
                break;
            case HEADSHOT_BONUS:
                if (isHeadshot) {
                    bonus = baseDamage * effect.value();
                }
                break;
            case BOSS_DAMAGE:
                if (isBoss) {
                    bonus = baseDamage * effect.value();
                }
                break;
            case EXECUTE_THRESHOLD:
                float targetHealthPercent = target.getHealth() / target.getMaxHealth();
                if (targetHealthPercent <= effect.value()) {
                    // Execute - massive damage
                    bonus = target.getHealth() * 10; // Instant kill
                }
                break;
            default:
                // Other effects don't modify damage directly
                break;
        }

        return baseDamage + bonus;
    }

    /**
     * Get style point multiplier from this trait.
     */
    public float getStyleMultiplier() {
        if (effect.type() == TraitEffectType.STYLE_GAIN) {
            return 1.0f + effect.value();
        }
        return 1.0f;
    }

    /**
     * Get lifesteal percentage from this trait.
     */
    public float getLifestealPercent() {
        if (effect.type() == TraitEffectType.LIFESTEAL) {
            return effect.value();
        }
        return 0f;
    }

    /**
     * Get crit chance bonus from this trait.
     */
    public float getCritChanceBonus() {
        if (effect.type() == TraitEffectType.CRIT_CHANCE) {
            return effect.value();
        }
        return 0f;
    }

    /**
     * Get combo decay reduction from this trait.
     */
    public float getComboDecayReduction() {
        if (effect.type() == TraitEffectType.COMBO_DECAY) {
            return effect.value();
        }
        return 0f;
    }

    /**
     * Get damage reduction from this trait.
     */
    public float getDamageReduction() {
        if (effect.type() == TraitEffectType.DAMAGE_REDUCTION) {
            return effect.value();
        }
        return 0f;
    }

    /**
     * Get resonance damage bonus from this trait.
     */
    public float getResonanceBonus() {
        if (effect.type() == TraitEffectType.RESONANCE_BONUS) {
            return effect.value();
        }
        return 0f;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeaponTrait that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "WeaponTrait{" + id + ": " + effect.getDescription() + "}";
    }

    // ========== Builder ==========

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private String adjective = "Enhanced";
        private SoulImprint.ImprintStat requiredStat;
        private TraitEffect effect;
        private int color = 0xFFFFFF;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder adjective(String adjective) {
            this.adjective = adjective;
            return this;
        }

        public Builder requiredStat(SoulImprint.ImprintStat stat) {
            this.requiredStat = stat;
            return this;
        }

        public Builder effect(TraitEffectType type, float value) {
            this.effect = new TraitEffect(type, value);
            return this;
        }

        public Builder color(int color) {
            this.color = color;
            return this;
        }

        public WeaponTrait build() {
            return new WeaponTrait(this);
        }
    }
}
