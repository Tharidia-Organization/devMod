package com.devmod.attributes;

import com.devmod.DevMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DevMod custom attributes (weapon-related).
 * Source of truth for attribute IDs and ranges.
 */
public final class ModAttributes {
    private ModAttributes() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(java.util.Objects.requireNonNull(Registries.ATTRIBUTE), DevMod.MODID);

    // Combat attributes
    public static final DeferredHolder<Attribute, Attribute> CRIT_CHANCE = ATTRIBUTES.register("crit_chance",
        () -> new RangedAttribute("attribute.devmod.crit_chance", 0.0D, 0.0D, 100.0D).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> CRIT_MULTIPLIER = ATTRIBUTES.register("crit_multiplier",
        () -> new RangedAttribute("attribute.devmod.crit_multiplier", 1.5D, 1.0D, 5.0D).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> ARMOR_SHRED = ATTRIBUTES.register("armor_shred",
        () -> new RangedAttribute("attribute.devmod.armor_shred", 0.0D, 0.0D, 66.0D).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> LIFE_STEAL = ATTRIBUTES.register("life_steal",
        () -> new RangedAttribute("attribute.devmod.life_steal", 0.0D, 0.0D, 100.0D).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> DAMAGE_BONUS = ATTRIBUTES.register("damage_bonus",
        () -> new RangedAttribute("attribute.devmod.damage_bonus", 0.0D, 0.0D, 100.0D).setSyncable(true));

    // Damage type bonuses
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_VS_UNDEAD = ATTRIBUTES.register("damage_vs_undead",
        () -> new RangedAttribute("attribute.devmod.damage_vs_undead", 0.0D, 0.0D, 200.0D).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> DAMAGE_VS_ARTHROPODS = ATTRIBUTES.register("damage_vs_arthropods",
        () -> new RangedAttribute("attribute.devmod.damage_vs_arthropods", 0.0D, 0.0D, 200.0D).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> DAMAGE_VS_PLAYERS = ATTRIBUTES.register("damage_vs_players",
        () -> new RangedAttribute("attribute.devmod.damage_vs_players", 0.0D, 0.0D, 200.0D).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> TRUE_DAMAGE_PERCENT = ATTRIBUTES.register("true_damage_percent",
        () -> new RangedAttribute("attribute.devmod.true_damage_percent", 0.0D, 0.0D, 100.0D).setSyncable(true));
}
