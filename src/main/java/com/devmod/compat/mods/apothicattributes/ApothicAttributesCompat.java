package com.devmod.compat.mods.apothicattributes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class ApothicAttributesCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApothicAttributesCompat.class);
    public static final String MOD_ID = "apothic_attributes";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached attribute holders
    private static final Map<String, Optional<Holder<Attribute>>> attributeCache = new HashMap<>();

    // Known Apothic Attributes (ResourceLocation paths)
    // Namespace changed from "attributeslib" to "apothic_attributes" in 1.21
    public static final String CRIT_CHANCE = "apothic_attributes:crit_chance";
    public static final String CRIT_DAMAGE = "apothic_attributes:crit_damage";
    public static final String LIFE_STEAL = "apothic_attributes:life_steal";
    public static final String ARMOR_SHRED = "apothic_attributes:armor_shred";
    public static final String ARMOR_PIERCE = "apothic_attributes:armor_pierce";
    public static final String ARROW_DAMAGE = "apothic_attributes:arrow_damage";
    public static final String ARROW_VELOCITY = "apothic_attributes:arrow_velocity";
    public static final String DRAW_SPEED = "apothic_attributes:draw_speed";
    public static final String DODGE_CHANCE = "apothic_attributes:dodge_chance";
    public static final String MINING_SPEED = "apothic_attributes:mining_speed";
    public static final String EXPERIENCE_GAINED = "apothic_attributes:experience_gained";
    public static final String HEALING_RECEIVED = "apothic_attributes:healing_received";
    public static final String GHOST_HEALTH = "apothic_attributes:ghost_health";
    public static final String COLD_DAMAGE = "apothic_attributes:cold_damage";
    public static final String FIRE_DAMAGE = "apothic_attributes:fire_damage";
    public static final String CURRENT_HP_DAMAGE = "apothic_attributes:current_hp_damage";
    public static final String OVERHEAL = "apothic_attributes:overheal";
    public static final String PROT_PIERCE = "apothic_attributes:prot_pierce";
    public static final String PROT_SHRED = "apothic_attributes:prot_shred";

    // All tracked attributes for enumeration
    private static final List<String> ALL_ATTRIBUTES = List.of(
        CRIT_CHANCE, CRIT_DAMAGE, LIFE_STEAL, ARMOR_SHRED, ARMOR_PIERCE,
        ARROW_DAMAGE, ARROW_VELOCITY, DRAW_SPEED, DODGE_CHANCE, MINING_SPEED,
        EXPERIENCE_GAINED, HEALING_RECEIVED, GHOST_HEALTH, COLD_DAMAGE,
        FIRE_DAMAGE, CURRENT_HP_DAMAGE, OVERHEAL, PROT_PIERCE, PROT_SHRED
    );

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Apothic Attributes";
    }

    @Override
    public int priority() {
        // High priority - attribute system affects damage calculations
        return 22;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:apothicattributes] Apothic Attributes not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:apothicattributes] Apothic Attributes detected");
        LOGGER.debug("[Compat:apothicattributes] Version: {}", Compat.getVersion(MOD_ID));

        // Pre-cache common attributes
        cacheCommonAttributes();
    }

    /**
     * Pre-cache commonly used attributes from the registry.
     */
    private void cacheCommonAttributes() {
        // Cache will be populated lazily when attributes are first accessed
        // This avoids issues with registry timing
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:apothicattributes] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Extended attribute access (crit, lifesteal, armor shred), HUD display, telemetry";
    }

    /**
     * Check if Apothic Attributes is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Get an attribute holder from the registry.
     *
     * @param attributeId Full attribute ID (e.g., "apothicattributes:crit_chance")
     * @return The attribute holder, or null if not found
     */
    @Nullable
    public static Holder<Attribute> getAttribute(@Nullable String attributeId) {
        if (!available || attributeId == null) {
            return null;
        }
        String id = attributeId.trim();
        if (id.isEmpty()) {
            return null;
        }

        Optional<Holder<Attribute>> cached = attributeCache.computeIfAbsent(
            id,
            ApothicAttributesCompat::resolveAttribute
        );
        return cached.orElse(null);
    }

    private static Optional<Holder<Attribute>> resolveAttribute(@Nonnull String attributeId) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(attributeId);
            if (rl == null) {
                return Optional.empty();
            }
            Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.getHolder(rl);
            if (holder.isPresent()) {
                return Optional.of(holder.get());
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:apothicattributes] Could not find attribute {}: {}",
                attributeId, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Get the value of an attribute for an entity.
     *
     * @param entity The entity
     * @param attributeId The attribute ID
     * @return The attribute value, or -1 if not available
     */
    public static double getAttributeValue(LivingEntity entity, String attributeId) {
        if (!available || entity == null) return -1;

        Holder<Attribute> holder = getAttribute(attributeId);
        if (holder == null) return -1;

        try {
            AttributeInstance instance = entity.getAttribute(holder);
            if (instance != null) {
                return instance.getValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:apothicattributes] Error getting attribute value: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get the base value of an attribute for an entity.
     */
    public static double getAttributeBaseValue(LivingEntity entity, String attributeId) {
        if (!available || entity == null) return -1;

        Holder<Attribute> holder = getAttribute(attributeId);
        if (holder == null) return -1;

        try {
            AttributeInstance instance = entity.getAttribute(holder);
            if (instance != null) {
                return instance.getBaseValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:apothicattributes] Error getting base value: {}", e.getMessage());
        }

        return -1;
    }

    // === Convenience Methods for Common Attributes ===

    /**
     * Get critical hit chance (0.0 to 1.0 typically).
     */
    public static double getCritChance(LivingEntity entity) {
        return getAttributeValue(entity, CRIT_CHANCE);
    }

    /**
     * Get critical hit damage multiplier.
     */
    public static double getCritDamage(LivingEntity entity) {
        return getAttributeValue(entity, CRIT_DAMAGE);
    }

    /**
     * Get life steal percentage.
     */
    public static double getLifeSteal(LivingEntity entity) {
        return getAttributeValue(entity, LIFE_STEAL);
    }

    /**
     * Get armor shred value.
     */
    public static double getArmorShred(LivingEntity entity) {
        return getAttributeValue(entity, ARMOR_SHRED);
    }

    /**
     * Get armor pierce value.
     */
    public static double getArmorPierce(LivingEntity entity) {
        return getAttributeValue(entity, ARMOR_PIERCE);
    }

    /**
     * Get arrow damage bonus.
     */
    public static double getArrowDamage(LivingEntity entity) {
        return getAttributeValue(entity, ARROW_DAMAGE);
    }

    /**
     * Get arrow velocity bonus.
     */
    public static double getArrowVelocity(LivingEntity entity) {
        return getAttributeValue(entity, ARROW_VELOCITY);
    }

    /**
     * Get draw speed multiplier.
     */
    public static double getDrawSpeed(LivingEntity entity) {
        return getAttributeValue(entity, DRAW_SPEED);
    }

    /**
     * Get dodge chance (0.0 to 1.0 typically).
     */
    public static double getDodgeChance(LivingEntity entity) {
        return getAttributeValue(entity, DODGE_CHANCE);
    }

    /**
     * Get mining speed bonus.
     */
    public static double getMiningSpeed(LivingEntity entity) {
        return getAttributeValue(entity, MINING_SPEED);
    }

    /**
     * Get experience gained multiplier.
     */
    public static double getExperienceGained(LivingEntity entity) {
        return getAttributeValue(entity, EXPERIENCE_GAINED);
    }

    /**
     * Get healing received multiplier.
     */
    public static double getHealingReceived(LivingEntity entity) {
        return getAttributeValue(entity, HEALING_RECEIVED);
    }

    /**
     * Get all extended attribute values for an entity.
     *
     * @param entity The entity
     * @return Map of attribute ID to value (only includes present attributes)
     */
    public static Map<String, Double> getAllAttributeValues(LivingEntity entity) {
        Map<String, Double> result = new LinkedHashMap<>();

        if (!available || entity == null) {
            return result;
        }

        for (String attrId : ALL_ATTRIBUTES) {
            double value = getAttributeValue(entity, attrId);
            if (value >= 0) { // Only include if attribute exists
                result.put(attrId, value);
            }
        }

        return result;
    }

    /**
     * Get combat-relevant attributes for an entity.
     */
    public static Map<String, Double> getCombatAttributes(LivingEntity entity) {
        Map<String, Double> result = new LinkedHashMap<>();

        if (!available || entity == null) {
            return result;
        }

        double critChance = getCritChance(entity);
        if (critChance >= 0) result.put("Crit Chance", critChance * 100);

        double critDamage = getCritDamage(entity);
        if (critDamage >= 0) result.put("Crit Damage", critDamage * 100);

        double lifeSteal = getLifeSteal(entity);
        if (lifeSteal >= 0) result.put("Life Steal", lifeSteal * 100);

        double armorPierce = getArmorPierce(entity);
        if (armorPierce >= 0) result.put("Armor Pierce", armorPierce);

        double armorShred = getArmorShred(entity);
        if (armorShred >= 0) result.put("Armor Shred", armorShred * 100);

        double dodge = getDodgeChance(entity);
        if (dodge >= 0) result.put("Dodge Chance", dodge * 100);

        return result;
    }

    /**
     * Check if an entity has any Apothic Attributes.
     */
    public static boolean hasApothicAttributes(LivingEntity entity) {
        if (!available || entity == null) return false;

        for (String attrId : ALL_ATTRIBUTES) {
            if (getAttributeValue(entity, attrId) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get a formatted string of combat stats for display.
     */
    public static String getCombatStatsString(LivingEntity entity) {
        if (!available || entity == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        Map<String, Double> combat = getCombatAttributes(entity);

        for (Map.Entry<String, Double> entry : combat.entrySet()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(String.format("%s: %.1f%%", entry.getKey(), entry.getValue()));
        }

        return sb.toString();
    }

    /**
     * Get list of all known Apothic Attribute IDs.
     */
    public static List<String> getAllAttributeIds() {
        return ALL_ATTRIBUTES;
    }

    /**
     * Get a display name for an attribute ID.
     */
    public static String getDisplayName(@Nullable String attributeId) {
        // Extract the name part and format it
        if (attributeId == null || attributeId.isBlank()) {
            return "";
        }
        String name = attributeId;
        int separator = name.indexOf(':');
        if (separator >= 0 && separator + 1 < name.length()) {
            name = name.substring(separator + 1);
        }

        // Convert snake_case to Title Case
        StringBuilder sb = new StringBuilder();
        StringTokenizer tokenizer = new StringTokenizer(name, "_");
        while (tokenizer.hasMoreTokens()) {
            String part = tokenizer.nextToken();
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.length() > 0 ? sb.toString() : name;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Apothic Attributes: not available";
        }
        int cached = (int) attributeCache.values().stream()
            .filter(Optional::isPresent)
            .count();
        return String.format("Apothic Attributes: %d attributes cached", cached);
    }
}
