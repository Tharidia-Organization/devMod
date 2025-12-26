package com.devmod.combat.signature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.config.gamedesign.GameDesignConfig;
import com.devmod.config.gamedesign.GameDesignConfigManager;
public class WeaponTraitRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaponTraitRegistry.class);

    public static final WeaponTraitRegistry INSTANCE = new WeaponTraitRegistry();

    private final Map<ResourceLocation, WeaponTrait> traitById = new HashMap<>();
    private final Map<SoulImprint.ImprintStat, WeaponTrait> traitByStat = new EnumMap<>(SoulImprint.ImprintStat.class);

    // ========== Predefined Traits ==========

    public static final WeaponTrait EXECUTIONER = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "executioner"))
        .adjective("Executioner's")
        .requiredStat(SoulImprint.ImprintStat.HEADSHOTS)
        .effect(WeaponTrait.TraitEffectType.HEADSHOT_BONUS, 0.15f) // +15% headshot damage
        .color(0xFF4444)
        .build();

    public static final WeaponTrait TYRANT_SLAYER = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "tyrant_slayer"))
        .adjective("Tyrant-Slaying")
        .requiredStat(SoulImprint.ImprintStat.BOSS_KILLS)
        .effect(WeaponTrait.TraitEffectType.BOSS_DAMAGE, 0.30f) // +30% vs bosses
        .color(0x8844FF)
        .build();

    public static final WeaponTrait STYLISH = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "stylish"))
        .adjective("Stylish")
        .requiredStat(SoulImprint.ImprintStat.SSS_WAVES)
        .effect(WeaponTrait.TraitEffectType.STYLE_GAIN, 0.20f) // +20% style gain
        .color(0xFFAA00)
        .build();

    public static final WeaponTrait BLOODTHIRSTY = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "bloodthirsty"))
        .adjective("Bloodthirsty")
        .requiredStat(SoulImprint.ImprintStat.TOTAL_KILLS)
        .effect(WeaponTrait.TraitEffectType.LIFESTEAL, 0.005f) // 0.5% lifesteal
        .color(0xCC0000)
        .build();

    public static final WeaponTrait HARMONIC = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "harmonic"))
        .adjective("Harmonic")
        .requiredStat(SoulImprint.ImprintStat.PERFECT_RESONANCES)
        .effect(WeaponTrait.TraitEffectType.RESONANCE_BONUS, 0.50f) // +50% resonance damage
        .color(0x44FFFF)
        .build();

    public static final WeaponTrait PRECISION = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "precision"))
        .adjective("Precise")
        .requiredStat(SoulImprint.ImprintStat.CRITICAL_HITS)
        .effect(WeaponTrait.TraitEffectType.CRIT_CHANCE, 0.10f) // +10% crit chance
        .color(0xFFFF44)
        .build();

    public static final WeaponTrait RELENTLESS = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "relentless"))
        .adjective("Relentless")
        .requiredStat(SoulImprint.ImprintStat.HIGH_COMBOS)
        .effect(WeaponTrait.TraitEffectType.COMBO_DECAY, 0.20f) // -20% combo decay
        .color(0xFF8800)
        .build();

    public static final WeaponTrait GUARDIAN = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "guardian"))
        .adjective("Guardian's")
        .requiredStat(SoulImprint.ImprintStat.NO_HIT_WAVES)
        .effect(WeaponTrait.TraitEffectType.DAMAGE_REDUCTION, 0.05f) // +5% damage reduction
        .color(0x44FF44)
        .build();

    public static final WeaponTrait DEVASTATING = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "devastating"))
        .adjective("Devastating")
        .requiredStat(SoulImprint.ImprintStat.TOTAL_DAMAGE)
        .effect(WeaponTrait.TraitEffectType.DAMAGE_PERCENT, 0.05f) // +5% all damage
        .color(0xFF6644)
        .build();

    public static final WeaponTrait FINISHER = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "finisher"))
        .adjective("Finishing")
        .requiredStat(SoulImprint.ImprintStat.EXECUTE_KILLS)
        .effect(WeaponTrait.TraitEffectType.EXECUTE_THRESHOLD, 0.10f) // Execute below 10% HP
        .color(0xAA0000)
        .build();

    public static final WeaponTrait CLEAVING = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "cleaving"))
        .adjective("Cleaving")
        .requiredStat(SoulImprint.ImprintStat.MULTI_KILLS)
        .effect(WeaponTrait.TraitEffectType.DAMAGE_PERCENT, 0.03f) // +3% damage (area focus)
        .color(0x8888FF)
        .build();

    public static final WeaponTrait RETALIATING = WeaponTrait.builder(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "retaliating"))
        .adjective("Retaliating")
        .requiredStat(SoulImprint.ImprintStat.PARRY_KILLS)
        .effect(WeaponTrait.TraitEffectType.DAMAGE_PERCENT, 0.08f) // +8% damage (counter-attack focus)
        .color(0xFFFFFF)
        .build();

    private WeaponTraitRegistry() {
        registerAll();
    }

    private void registerAll() {
        register(EXECUTIONER);
        register(TYRANT_SLAYER);
        register(STYLISH);
        register(BLOODTHIRSTY);
        register(HARMONIC);
        register(PRECISION);
        register(RELENTLESS);
        register(GUARDIAN);
        register(DEVASTATING);
        register(FINISHER);
        register(CLEAVING);
        register(RETALIATING);

        LOGGER.info("[WeaponTraitRegistry] Registered {} weapon traits", traitById.size());
    }

    private void register(WeaponTrait trait) {
        traitById.put(trait.getId(), trait);
        traitByStat.put(trait.getRequiredStat(), trait);
    }

    // ========== Lookups ==========

    /**
     * Get trait by ID string (e.g., "devmod:executioner").
     */
    @Nullable
    public WeaponTrait getTrait(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc != null ? traitById.get(loc) : null;
    }

    /**
     * Get trait by ResourceLocation.
     */
    @Nullable
    public WeaponTrait getTrait(ResourceLocation id) {
        return traitById.get(id);
    }

    /**
     * Get the trait unlocked by a specific stat.
     */
    @Nullable
    public WeaponTrait getTraitForStat(SoulImprint.ImprintStat stat) {
        return traitByStat.get(stat);
    }

    /**
     * Get all registered traits.
     */
    public Collection<WeaponTrait> getAllTraits() {
        return Collections.unmodifiableCollection(traitById.values());
    }

    /**
     * Get trait count.
     */
    public int getTraitCount() {
        return traitById.size();
    }

    // ========== Utility ==========

    /**
     * Get config for the given quest instance.
     */
    private static GameDesignConfig.SignatureWeaponsConfig getConfig(@Nullable UUID questId) {
        return GameDesignConfigManager.INSTANCE.getSignatureWeaponsConfig(questId);
    }

    /**
     * Get the configured effect value for a trait.
     * Uses config values instead of the trait's default value.
     *
     * @param trait The trait to get effect value for
     * @param questId Quest ID for config lookup (nullable)
     * @return The configured effect value
     */
    public static float getConfiguredEffectValue(WeaponTrait trait, @Nullable UUID questId) {
        GameDesignConfig.SignatureWeaponsConfig config = getConfig(questId);

        // Map trait ID to config value
        String traitName = trait.getId().getPath();
        return switch (traitName) {
            case "executioner" -> config.executionerBonus;
            case "tyrant_slayer" -> config.tyrantSlayerBonus;
            case "stylish" -> config.stylishBonus;
            case "bloodthirsty" -> config.bloodthirstyLifesteal;
            case "harmonic" -> config.harmonicBonus;
            case "precision" -> config.precisionCritChance;
            case "relentless" -> config.relentlessComboDecay;
            case "guardian" -> config.guardianDamageReduction;
            // For traits without specific config, use the trait's default value
            default -> trait.getEffect().value();
        };
    }

    /**
     * Calculate combined effect value from multiple traits of the same type.
     * Uses configured values instead of default trait values.
     *
     * @param traits Set of traits to combine
     * @param type The effect type to sum
     * @param questId Quest ID for config lookup (nullable)
     * @return Combined effect value
     */
    public static float getCombinedEffect(Set<WeaponTrait> traits, WeaponTrait.TraitEffectType type,
                                           @Nullable UUID questId) {
        float total = 0f;
        for (WeaponTrait trait : traits) {
            if (trait.getEffect().type() == type) {
                total += getConfiguredEffectValue(trait, questId);
            }
        }
        return total;
    }

    /**
     * Calculate combined effect value from multiple traits of the same type.
     * @deprecated Use {@link #getCombinedEffect(Set, WeaponTrait.TraitEffectType, UUID)} instead.
     */
    @Deprecated
    public static float getCombinedEffect(Set<WeaponTrait> traits, WeaponTrait.TraitEffectType type) {
        return getCombinedEffect(traits, type, null);
    }

    /**
     * Get formatted trait list for tooltips.
     */
    public static List<String> getTraitDescriptions(Set<WeaponTrait> traits) {
        List<String> descriptions = new ArrayList<>();
        for (WeaponTrait trait : traits) {
            descriptions.add(trait.getAdjective() + ": " + trait.getEffect().getDescription());
        }
        return descriptions;
    }
}
