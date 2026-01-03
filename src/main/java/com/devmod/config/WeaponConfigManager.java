package com.devmod.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import com.devmod.DevMod;
import com.devmod.attributes.ModAttributes;
import com.devmod.components.WeaponComponents;
import com.devmod.integration.PufferfishIntegration;
import com.devmod.network.PacketValidator;
import com.devmod.stats.WeaponStats;

public class WeaponConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaponConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Backup settings
    private static final int MAX_BACKUPS = 5;
    private static final String BACKUP_SUFFIX = ".backup";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String TOML_FILE = "devmod-items.toml";

    private static final net.minecraft.core.component.DataComponentType<CustomData> CUSTOM_DATA =
            Objects.requireNonNull(DataComponents.CUSTOM_DATA);

    // Map for GLOBAL settings (Per item type) - ConcurrentHashMap for thread safety
    private static final Map<Item, WeaponStats> globalStats = new ConcurrentHashMap<>();

    private static Path dataDirectory = null;

    /**
     * Initialize with config directory path.
     */
    public static void initialize(Path configDir) {
        dataDirectory = configDir.resolve("devmod");
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            LOGGER.error("[WeaponConfig] Failed to create data directory", e);
        }
        load();
        loadToml();
    }

    // Gets the final statistics for a specific weapon
    public static WeaponStats getStats(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        // 1a. Check typed data component first (new path) - safe component access
        var weaponComponent = WeaponComponents.weaponStatsComponent();
        if (weaponComponent != null) {
            CompoundTag componentTag = stack.get(weaponComponent);
            if (componentTag != null && !componentTag.isEmpty()) {
                WeaponStats loaded = WeaponStats.load(componentTag.copy());
                ensureAttributeModifiers(stack, clampStats(loaded));
                applyToolClearIfNeeded(stack, loaded);
                return loaded;
            }
        }

        // 1b. Legacy CustomData path
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData != null && customData.contains("WeaponModStats")) {
            CompoundTag legacy = customData.copyTag().getCompound("WeaponModStats");
            WeaponStats stats = WeaponStats.load(legacy);
            // Migrate forward to typed component when missing - safe component access
            var migrateComponent = WeaponComponents.weaponStatsComponent();
            if (migrateComponent != null) {
                try {
                    stack.set(migrateComponent, legacy.copy());
                } catch (Exception e) {
                    LOGGER.debug("[WeaponConfig] Failed to migrate legacy WeaponModStats to component: {}", e.getMessage());
                }
            }
            ensureAttributeModifiers(stack, clampStats(stats));
            applyToolClearIfNeeded(stack, stats);
            return stats;
        }

        // 1c. Reconstruct from attribute modifiers if present (best-effort)
        WeaponStats fromAttributes = loadFromAttributeModifiers(stack);
        if (fromAttributes != null) {
            // Persist reconstruction into component to avoid repeated rebuilds - safe component access
            var persistComponent = WeaponComponents.weaponStatsComponent();
            if (persistComponent != null) {
                try {
                    CompoundTag migrated = new CompoundTag();
                    fromAttributes.save(migrated);
                    stack.set(persistComponent, migrated);
                } catch (Exception e) {
                    LOGGER.debug("[WeaponConfig] Failed to persist reconstructed stats: {}", e.getMessage());
                }
            }
            WeaponStats clamped = clampStats(fromAttributes);
            ensureAttributeModifiers(stack, clamped);
            applyToolClearIfNeeded(stack, clamped);
            return clamped;
        }

        // 2. If it has no specific modifications, use GLOBAL ones and materialize onto the stack for consistency
        if (globalStats.containsKey(stack.getItem())) {
            WeaponStats global = clampStats(globalStats.get(stack.getItem()).copy());
            try {
                setSpecificStats(stack, global);
            } catch (Exception e) {
                LOGGER.debug("[WeaponConfig] Failed to materialize global stats onto stack: {}", e.getMessage());
            }
            return global;
        }

        // 3. Otherwise return base statistics
        return new WeaponStats();
    }

    public static void setGlobalStats(Item item, WeaponStats stats) {
        WeaponStats clamped = clampStats(stats.copy());
        globalStats.put(item, clamped);
        save(); // AUTO-SAVE on modification
    }

    /**
     * Set global stats without saving to disk.
     * Used for client-side sync from server.
     */
    public static void setGlobalStatsClientOnly(Item item, WeaponStats stats) {
        WeaponStats clamped = clampStats(stats.copy());
        globalStats.put(item, clamped);
        // No save - client-side only
    }

    public static void setSpecificStats(ItemStack stack, WeaponStats stats) {
        setSpecificStats(stack, stats, null);
    }

    public static void setSpecificStats(ItemStack stack, WeaponStats stats, @Nullable CompoundTag variantExtras) {
        WeaponStats clamped = clampStats(stats.copy());
        CustomData.update(Objects.requireNonNull(CUSTOM_DATA), Objects.requireNonNull(stack), tag -> {
            net.minecraft.nbt.CompoundTag statsTag = new net.minecraft.nbt.CompoundTag();
            clamped.save(statsTag);
            // Merge variant-specific data (Mace/Trident) without overwriting stats keys
            if (variantExtras != null) {
                try {
                    if (variantExtras.contains("Mace")) {
                        statsTag.put("Mace", Objects.requireNonNull(variantExtras.getCompound("Mace").copy()));
                    }
                    if (variantExtras.contains("Trident")) {
                        statsTag.put("Trident", Objects.requireNonNull(variantExtras.getCompound("Trident").copy()));
                    }
                } catch (Exception e) {
                    LOGGER.debug("[WeaponConfig] Failed to merge variant data: {}", e.getMessage());
                }
            }
            tag.put("WeaponModStats", Objects.requireNonNull(statsTag.copy()));
            // Also mirror to typed data component for forward compatibility - safe component access
            var setComponent = WeaponComponents.weaponStatsComponent();
            if (setComponent != null) {
                try {
                    stack.set(setComponent, statsTag.copy());
                } catch (Exception e) {
                    LOGGER.warn("[WeaponConfig] Failed to apply weapon_stats component", e);
                }
            }
        });

        // Apply durability components directly on the stack when provided
        try {
            if (clamped.getMaxDurability() > 0) {
                stack.set(Objects.requireNonNull(net.minecraft.core.component.DataComponents.MAX_DAMAGE), clamped.getMaxDurability());
            }
            if (clamped.getCurrentDamage() > 0) {
                stack.setDamageValue(clamped.getCurrentDamage());
            }
            if (clamped.getRepairCost() > 0) {
                stack.set(Objects.requireNonNull(net.minecraft.core.component.DataComponents.REPAIR_COST), clamped.getRepairCost());
            }
            if (clamped.isUnbreakable()) {
                stack.set(Objects.requireNonNull(net.minecraft.core.component.DataComponents.UNBREAKABLE), new net.minecraft.world.item.component.Unbreakable(true));
            } else {
                // Clear if previously set
                stack.remove(Objects.requireNonNull(net.minecraft.core.component.DataComponents.UNBREAKABLE));
            }
            if (clamped.isClearToolRules()) {
                stack.remove(Objects.requireNonNull(net.minecraft.core.component.DataComponents.TOOL));
            } else if (!clamped.toolRules.isEmpty() || clamped.getToolDefaultMiningSpeed() != 1.0f || clamped.getToolDamagePerBlock() != 1) {
                java.util.List<Tool.Rule> rules = new ArrayList<>();
                for (WeaponStats.ToolRuleData ruleData : clamped.toolRules) {
                    if (ruleData == null || ruleData.isEmpty()) continue;
                    ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(ruleData.blockTag));
                    if (id == null) continue;
                    TagKey<Block> tagKey = TagKey.create(Objects.requireNonNull(Registries.BLOCK), id);
                    float speed = ruleData.speed <= 0 ? 0.0f : ruleData.speed;
                    Boolean drops = ruleData.correctForDrops;
                    Tool.Rule builtRule;
                    if (drops == null) {
                        builtRule = Tool.Rule.overrideSpeed(Objects.requireNonNull(tagKey), speed);
                    } else if (drops) {
                        builtRule = Tool.Rule.minesAndDrops(Objects.requireNonNull(tagKey), speed);
                    } else {
                        builtRule = Tool.Rule.deniesDrops(Objects.requireNonNull(tagKey));
                    }
                    rules.add(builtRule);
                }
                Tool tool = new Tool(rules, clamped.getToolDefaultMiningSpeed(), Math.max(0, clamped.getToolDamagePerBlock()));
                stack.set(Objects.requireNonNull(net.minecraft.core.component.DataComponents.TOOL), tool);
            }
        } catch (Exception e) {
            LOGGER.warn("[WeaponConfig] Failed to apply durability/tool components", e);
        }

        // Apply attribute modifiers to reflect edited stats in-game (vanilla + custom)
        try {
            applyAttributeModifiers(stack, clamped);
        } catch (Exception e) {
            LOGGER.warn("[WeaponConfig] Failed to apply attribute modifiers", e);
        }
    }

    /**
    * Apply attribute modifiers to the stack based on current stats.
    * Keeps vanilla modifiers intact, replacing only DevMod-added entries.
    */
    private static void applyAttributeModifiers(ItemStack stack, WeaponStats stats) {
        ItemAttributeModifiers existing = stack.getOrDefault(
            Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS),
            Objects.requireNonNull(ItemAttributeModifiers.EMPTY)
        );
        java.util.List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(existing.modifiers());
        PacketValidator security = PacketValidator.INSTANCE;

        // Vanilla attributes
        float attackDamage = stats.getAttackDamage() > 0 ? stats.getAttackDamage() : 0;
        if (stats.getBaseDamageBonus() > 0) {
            attackDamage += stats.getBaseDamageBonus();
        }
        attackDamage = (float) security.validateDamage(attackDamage);
        // Preserve negative vanilla attack speed values (e.g., -2.4) instead of zeroing them out
        float attackSpeed = (float) security.validateAttackSpeed(stats.getAttackSpeed());
        float attackReach = (float) security.validateMultiplier(stats.getAttackReach() > 0 ? stats.getAttackReach() : 0);
        float attackKnockback = (float) security.validateMultiplier(stats.getAttackKnockback() > 0 ? stats.getAttackKnockback() : 0);
        float damageBonus = (float) security.validateSweeping(stats.getDamageBonus() > 0 ? stats.getDamageBonus() : 0);
        float sweepingRatio = (float) security.validateSweeping(stats.getSweepingRatio() > 0 ? stats.getSweepingRatio() : 0);

        addModifier(entries, holder(Attributes.ATTACK_DAMAGE), attackDamage, AttributeModifier.Operation.ADD_VALUE, "attack_damage");
        addModifier(entries, holder(Attributes.ATTACK_SPEED), attackSpeed, AttributeModifier.Operation.ADD_VALUE, "attack_speed");
        addModifier(entries, holder(Attributes.ENTITY_INTERACTION_RANGE), attackReach, AttributeModifier.Operation.ADD_VALUE, "attack_reach");
        addModifier(entries, holder(Attributes.ATTACK_KNOCKBACK), attackKnockback, AttributeModifier.Operation.ADD_VALUE, "attack_knockback");
        addModifier(entries, holder(ModAttributes.DAMAGE_BONUS), damageBonus, AttributeModifier.Operation.ADD_VALUE, "damage_bonus");
        addModifier(entries, holder(Attributes.SWEEPING_DAMAGE_RATIO), sweepingRatio, AttributeModifier.Operation.ADD_VALUE, "sweeping_ratio");

        // Custom DevMod attributes (mapped to Pufferfish when available)
        float critChance = (float) security.validateMultiplier(stats.getCritChance() > 0 ? stats.getCritChance() : 0);
        float critMult = (float) security.validateMultiplier(Float.compare(stats.getCritDamage(), 1.5f) != 0 ? stats.getCritDamage() : 0);
        float armorShred = (float) security.validateArmorShred(stats.getArmorShred() > 0 ? stats.getArmorShred() : 0);
        float lifesteal = (float) security.validateMultiplier(stats.getLifesteal() > 0 ? stats.getLifesteal() : 0);
        float vsUndead = (float) security.validateDamageVs(stats.getDamageVsUndead() > 0 ? stats.getDamageVsUndead() : 0);
        float vsArthro = (float) security.validateDamageVs(stats.getDamageVsArthropods() > 0 ? stats.getDamageVsArthropods() : 0);
        float vsPlayers = (float) security.validateDamageVs(stats.getDamageVsPlayers() > 0 ? stats.getDamageVsPlayers() : 0);
        float trueDmg = (float) security.validateTrueDamage(stats.getTrueDamagePercent() > 0 ? stats.getTrueDamagePercent() : 0);

        addModifier(entries, holder(ModAttributes.CRIT_CHANCE), critChance, AttributeModifier.Operation.ADD_VALUE, "crit_chance");
        addModifier(entries, holder(ModAttributes.CRIT_MULTIPLIER), critMult, AttributeModifier.Operation.ADD_VALUE, "crit_multiplier");
        addModifier(entries, holder(ModAttributes.ARMOR_SHRED), armorShred, AttributeModifier.Operation.ADD_VALUE, "armor_shred");
        addModifier(entries, holder(ModAttributes.LIFE_STEAL), lifesteal, AttributeModifier.Operation.ADD_VALUE, "life_steal");
        addModifier(entries, holder(ModAttributes.DAMAGE_VS_UNDEAD), vsUndead, AttributeModifier.Operation.ADD_VALUE, "vs_undead");
        addModifier(entries, holder(ModAttributes.DAMAGE_VS_ARTHROPODS), vsArthro, AttributeModifier.Operation.ADD_VALUE, "vs_arthropods");
        addModifier(entries, holder(ModAttributes.DAMAGE_VS_PLAYERS), vsPlayers, AttributeModifier.Operation.ADD_VALUE, "vs_players");
        addModifier(entries, holder(ModAttributes.TRUE_DAMAGE_PERCENT), trueDmg, AttributeModifier.Operation.ADD_VALUE, "true_damage");

        stack.set(Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS), new ItemAttributeModifiers(entries, existing.showInTooltip()));
    }

    /**
     * Best-effort ensure attribute modifiers on the stack reflect the given stats.
     * Does not mutate other components.
     */
    private static void ensureAttributeModifiers(ItemStack stack, WeaponStats stats) {
        try {
            applyAttributeModifiers(stack, stats);
        } catch (Exception e) {
            LOGGER.debug("[WeaponConfig] Failed to ensure attribute modifiers: {}", e.getMessage());
        }
    }

    private static void applyToolClearIfNeeded(ItemStack stack, WeaponStats stats) {
        if (stats.isClearToolRules()) {
            try {
                stack.remove(Objects.requireNonNull(DataComponents.TOOL));
            } catch (Exception e) {
                LOGGER.debug("[WeaponConfig] Failed to clear tool component: {}", e.getMessage());
            }
        }
    }

    /**
     * Clamp stats using server-side security limits.
     */
    public static WeaponStats clampStats(WeaponStats stats) {
        try {
            PacketValidator security = PacketValidator.INSTANCE;
            stats.setArmorShred((float) security.validateArmorShred(stats.getArmorShred()));
            stats.setDamageVsUndead((float) security.validateDamageVs(stats.getDamageVsUndead()));
            stats.setDamageVsArthropods((float) security.validateDamageVs(stats.getDamageVsArthropods()));
            stats.setDamageVsPlayers((float) security.validateDamageVs(stats.getDamageVsPlayers()));
            stats.setTrueDamagePercent((float) security.validateTrueDamage(stats.getTrueDamagePercent()));
            stats.setDamageBonus((float) security.validateSweeping(stats.getDamageBonus()));
            stats.setSweepingRatio((float) security.validateSweeping(stats.getSweepingRatio()));
            stats.setLifesteal((float) security.validateMultiplier(stats.getLifesteal()));
            stats.setFireDamageBonus((float) security.validateMultiplier(stats.getFireDamageBonus()));
            stats.setMagicDamageBonus((float) security.validateMultiplier(stats.getMagicDamageBonus()));
            stats.setAttackDamage((float) security.validateDamage(stats.getAttackDamage()));
            stats.setAttackSpeed((float) security.validateAttackSpeed(stats.getAttackSpeed()));
            stats.setAttackReach((float) security.validateMultiplier(stats.getAttackReach()));
            stats.setAttackKnockback((float) security.validateMultiplier(stats.getAttackKnockback()));
            stats.setArmorPenetration((float) security.validatePenetration(stats.getArmorPenetration()));
            stats.setBaseDamageBonus((float) security.validateDamage(stats.getBaseDamageBonus()));
            stats.setMaxDurability(security.validateDurability(stats.getMaxDurability()));
            stats.setCurrentDamage(security.validateDurability(stats.getCurrentDamage()));
            stats.setRepairCost(security.validateRepairCost(stats.getRepairCost()));
            stats.setToolDefaultMiningSpeed((float) security.validateToolSpeed(stats.getToolDefaultMiningSpeed()));
            stats.setToolDamagePerBlock(security.validateToolDamagePerBlock(stats.getToolDamagePerBlock()));
        } catch (Exception e) {
            LOGGER.debug("[WeaponConfig] Failed to clamp stats: {}", e.getMessage());
        }
        return stats;
    }
    private static void addModifier(java.util.List<ItemAttributeModifiers.Entry> entries, Holder<Attribute> attribute, double value, AttributeModifier.Operation op, String keySuffix) {
        if (attribute == null) return;
        ResourceLocation modId = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "weapon_editor_" + keySuffix);
        // Remove any existing modifier for the same attribute (regardless of namespace) to avoid stacking
        entries.removeIf(e -> {
            ResourceKey<Attribute> existing = e.attribute().unwrapKey().orElse(null);
            ResourceKey<Attribute> target = attribute.unwrapKey().orElse(null);
            boolean sameAttr = Objects.equals(existing, target);
            boolean sameId = java.util.Objects.equals(e.modifier().id(), modId);
            if (sameAttr && e.modifier().id() != null && !DevMod.MODID.equals(e.modifier().id().getNamespace())) {
                DevMod.LOGGER.debug("[WeaponConfig] Removing external modifier {} on {}", e.modifier().id(), target);
            }
            return sameAttr || sameId;
        });
        if (value == 0) {
            return; // removal only
        }
        AttributeModifier modifier = new AttributeModifier(Objects.requireNonNull(modId), value, Objects.requireNonNull(op));
        entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, EquipmentSlotGroup.MAINHAND));
    }

    private static Holder<Attribute> holder(Attribute attribute) {
        if (attribute == null) return null;
        Optional<ResourceKey<Attribute>> key = BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute);
        return key.flatMap(BuiltInRegistries.ATTRIBUTE::getHolder).orElse(null);
    }

    private static Holder<Attribute> holder(Holder<Attribute> attribute) {
        return attribute;
    }

    private static Holder<Attribute> holder(net.neoforged.neoforge.registries.DeferredHolder<Attribute, Attribute> def) {
        Attribute mapped = PufferfishIntegration.map(def);
        return holder(mapped);
    }

    /**
     * Rebuild stats from devmod-applied attribute modifiers when no component/custom data is present.
     */
    @Nullable
    public static WeaponStats loadFromAttributeModifiers(ItemStack stack) {
        ItemAttributeModifiers mods = stack.getOrDefault(
            Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS),
            Objects.requireNonNull(ItemAttributeModifiers.EMPTY)
        );
        if (mods.modifiers().isEmpty()) return null;
        WeaponStats stats = new WeaponStats();
        boolean found = false;
        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            ResourceLocation id = entry.modifier().id();
            if (id == null || !DevMod.MODID.equals(id.getNamespace())) continue;
            String path = id.getPath();
            double val = entry.modifier().amount();
            switch (path) {
                case "weapon_editor_attack_damage" -> { stats.setAttackDamage((float) val); found = true; }
                case "weapon_editor_attack_speed" -> { stats.setAttackSpeed((float) val); found = true; }
                case "weapon_editor_attack_reach" -> { stats.setAttackReach((float) val); found = true; }
                case "weapon_editor_attack_knockback" -> { stats.setAttackKnockback((float) val); found = true; }
                case "weapon_editor_damage_bonus" -> { stats.setDamageBonus((float) val); found = true; }
                case "weapon_editor_sweeping_ratio" -> { stats.setSweepingRatio((float) val); found = true; }
                case "weapon_editor_crit_chance" -> { stats.setCritChance((float) val); found = true; }
                case "weapon_editor_crit_multiplier" -> { stats.setCritDamage((float) val); found = true; }
                case "weapon_editor_armor_shred" -> { stats.setArmorShred((float) val); found = true; }
                case "weapon_editor_life_steal" -> { stats.setLifesteal((float) val); found = true; }
                case "weapon_editor_vs_undead" -> { stats.setDamageVsUndead((float) val); found = true; }
                case "weapon_editor_vs_arthropods" -> { stats.setDamageVsArthropods((float) val); found = true; }
                case "weapon_editor_vs_players" -> { stats.setDamageVsPlayers((float) val); found = true; }
                case "weapon_editor_true_damage" -> { stats.setTrueDamagePercent((float) val); found = true; }
                default -> { /* ignore */ }
            }
        }
        return found ? stats : null;
    }

    /**
     * Gets the default global statistics (used for labels).
     * Returns a WeaponStats with default values.
     */
    public static WeaponStats getGlobalStats() {
        return new WeaponStats();
    }

    /**
     * Get global stats for a specific item type.
     */
    public static WeaponStats getGlobalStats(Item item) {
        return globalStats.getOrDefault(item, new WeaponStats());
    }

    /**
     * Check if item has global config.
     */
    public static boolean hasGlobalConfig(Item item) {
        return globalStats.containsKey(item);
    }

    /**
     * Clears all global statistics (used for GameTests).
     */
    public static void clearAllGlobalStats() {
        globalStats.clear();
    }

    // ========== Persistence ==========

    /**
     * Load weapon configurations from file.
     */
    public static void load() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("weapon_configs.json");
        Path backupFile = dataDirectory.resolve("weapon_configs.json.bak");

        Path fileToLoad = Files.exists(configFile) ? configFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, WeaponStats>>(){}.getType();
                Map<String, WeaponStats> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    globalStats.clear();
                    loaded.forEach((key, stats) -> {
                        ResourceLocation resLoc = ResourceLocation.tryParse(Objects.requireNonNull(key));
                        if (resLoc != null && BuiltInRegistries.ITEM.containsKey(resLoc)) {
                            Item item = BuiltInRegistries.ITEM.get(resLoc);
                            globalStats.put(Objects.requireNonNull(item), clampStats(stats));
                        } else {
                            LOGGER.warn("[WeaponConfig] Unknown item in config: {}", key);
                        }
                    });
                    LOGGER.info("[WeaponConfig] Loaded {} weapon configurations from {}",
                            globalStats.size(), fileToLoad.getFileName());
                }
            } catch (Exception e) {
                LOGGER.error("[WeaponConfig] Failed to load weapon configs from {}", fileToLoad, e);
            }
        }
    }

    /**
     * Save weapon configurations to file.
     */
    public static void save() {
        if (dataDirectory == null) return;

        // Create timestamped backup before saving
        createTimestampedBackup();

        Path configFile = dataDirectory.resolve("weapon_configs.json");
        Path tempFile = dataDirectory.resolve("weapon_configs.json.tmp");
        Path backupFile = dataDirectory.resolve("weapon_configs.json.bak");

        try {
            Files.createDirectories(dataDirectory);

            // Write to temp file first (atomic write pattern)
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                Map<String, WeaponStats> toSave = new HashMap<>();
                globalStats.forEach((item, stats) -> {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item));
                    toSave.put(key.toString(), stats);
                });
                GSON.toJson(toSave, writer);
                writer.flush();
            }

            // Create backup of existing file
            if (Files.exists(configFile)) {
                Files.copy(configFile, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Atomic move temp to final
            Files.move(tempFile, configFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            LOGGER.info("[WeaponConfig] Saved {} weapon configurations", globalStats.size());
            // Also persist TOML view for interoperability (per doc 06-persistence)
            saveToml();

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[WeaponConfig] Failed to save weapon configs (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[WeaponConfig] Failed to save weapon configs", e);
        }
    }

    // ========== BACKUP & RECOVERY SYSTEM ==========

    /**
     * Creates a timestamped backup of the current config file.
     */
    private static void createTimestampedBackup() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("weapon_configs.json");
        if (!Files.exists(configFile)) {
            return;
        }

        try {
            Path backupDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now(ZoneId.systemDefault()).format(BACKUP_DATE_FORMAT);
            String backupName = "weapon_configs_" + timestamp + BACKUP_SUFFIX;
            Path backupFile = backupDir.resolve(backupName);

            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("[WeaponConfig] Created backup: {}", backupFile);

            cleanupOldBackups(backupDir);
        } catch (IOException e) {
            LOGGER.warn("[WeaponConfig] Failed to create backup", e);
        }
    }

    /**
     * Removes old backups keeping only MAX_BACKUPS most recent.
     */
    private static void cleanupOldBackups(Path backupDir) {
        try {
            List<Path> backups;
            try (var backupStream = Files.list(backupDir)) {
                backups = backupStream
                    .filter(p -> p.getFileName().toString().startsWith("weapon_configs_")
                              && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
            }

            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
                LOGGER.debug("[WeaponConfig] Deleted old backup: {}", backups.get(i));
            }
        } catch (IOException e) {
            LOGGER.warn("[WeaponConfig] Failed to cleanup old backups", e);
        }
    }

    /**
     * Restores config from the most recent backup.
     */
    public static boolean restoreFromBackup() {
        if (dataDirectory == null) return false;

        Path configFile = dataDirectory.resolve("weapon_configs.json");
        Path backupDir = dataDirectory.resolve("backups");

        if (!Files.exists(backupDir)) {
            LOGGER.warn("[WeaponConfig] No backup directory found");
            return false;
        }

        try {
            Optional<Path> latestBackup;
            try (var backupStream = Files.list(backupDir)) {
                latestBackup = backupStream
                    .filter(p -> p.getFileName().toString().startsWith("weapon_configs_")
                              && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException e) {
                            return 0;
                        }
                    });
            }

            if (latestBackup.isEmpty()) {
                LOGGER.warn("[WeaponConfig] No backup files found");
                return false;
            }

            Path backup = latestBackup.get();
            Files.copy(backup, configFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[WeaponConfig] Restored from backup: {}", backup);

            load();
            return true;
        } catch (IOException e) {
            LOGGER.error("[WeaponConfig] Failed to restore from backup", e);
            return false;
        }
    }

    /**
     * Lists available backup files.
     */
    public static Map<String, String> listBackups() {
        Map<String, String> backups = new HashMap<>();
        if (dataDirectory == null) return backups;

        Path backupDir = dataDirectory.resolve("backups");
        if (!Files.exists(backupDir)) return backups;

        try {
            try (var backupStream = Files.list(backupDir)) {
                backupStream
                    .filter(p -> p.getFileName().toString().startsWith("weapon_configs_")
                              && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                    .forEach(p -> {
                        try {
                            backups.put(p.getFileName().toString(), Files.getLastModifiedTime(p).toString());
                        } catch (IOException e) {
                            // Skip
                        }
                    });
            }
        } catch (IOException e) {
            LOGGER.warn("[WeaponConfig] Failed to list backups", e);
        }

        return backups;
    }

    /**
     * Get an immutable view of all global weapon overrides.
     */
    public static Map<Item, WeaponStats> getAllGlobalStats() {
        return Map.copyOf(globalStats);
    }

    /**
     * Sync global configs to all connected clients.
     * Should be called after setGlobalStats() when running on server.
     * Uses GlobalConfigSyncPayload to broadcast changes.
     */
    public static void syncToAllClients() {
        try {
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.debug("[WeaponConfig] No server available for sync");
                return;
            }

            var payload = com.devmod.network.GlobalConfigSyncPayload.fromCurrentConfigs();

            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(Objects.requireNonNull(player), Objects.requireNonNull(payload));
            }

            LOGGER.info("[WeaponConfig] Synced global configs to {} clients", server.getPlayerList().getPlayerCount());
        } catch (Exception e) {
            LOGGER.warn("[WeaponConfig] Failed to sync to clients: {}", e.getMessage());
        }
    }

    // ========== TOML STORAGE (per doc 06-persistence) ==========

    private static void loadToml() {
        if (dataDirectory == null) return;
        Path tomlPath = dataDirectory.resolve(TOML_FILE);
        if (!Files.exists(tomlPath)) {
            return;
        }

        try {
            Map<Item, WeaponStats> loaded = new HashMap<>();
            Item currentItem = null;

            for (String rawLine : Files.readAllLines(tomlPath, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[")) {
                    currentItem = null;
                    int typeEnd = line.indexOf(".");
                    int quoteStart = line.indexOf('"');
                    int quoteEnd = line.lastIndexOf('"');
                    if (typeEnd > 1 && quoteStart > typeEnd && quoteEnd > quoteStart) {
                        String sectionType = line.substring(1, typeEnd).trim(); // armor / weapon
                        String itemId = line.substring(quoteStart + 1, quoteEnd);
                        ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(itemId));
                        if (id != null && BuiltInRegistries.ITEM.containsKey(id) && "weapon".equals(sectionType)) {
                            currentItem = BuiltInRegistries.ITEM.get(id);
                            loaded.putIfAbsent(currentItem, new WeaponStats());
                        }
                    }
                    continue;
                }

                if (currentItem == null) continue;
                WeaponStats stats = loaded.getOrDefault(currentItem, new WeaponStats());

                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                try {
                    switch (key) {
                        case "attackDamage" -> stats.setAttackDamage(parseFloat(val));
                        case "attackSpeed" -> stats.setAttackSpeed(parseFloat(val));
                        case "attackReach" -> stats.setAttackReach(parseFloat(val));
                        case "attackKnockback" -> stats.setAttackKnockback(parseFloat(val));
                        case "armorPenetration" -> stats.setArmorPenetration(parseClampedFloat(val));
                        case "baseDamageBonus" -> stats.setBaseDamageBonus(parseFloat(val));
                        case "critChance" -> stats.setCritChance(parseClampedFloat(val));
                        case "critDamage" -> stats.setCritDamage(parseFloat(val));
                        case "fireDamageBonus" -> stats.setFireDamageBonus(parseFloat(val));
                        case "magicDamageBonus" -> stats.setMagicDamageBonus(parseFloat(val));
                        case "lifesteal" -> stats.setLifesteal(parseClampedFloat(val));
                        default -> {}
                    }
                    loaded.put(currentItem, stats);
                } catch (NumberFormatException ex) {
                    LOGGER.warn("[WeaponConfig] Invalid value '{}' for {} in {}", val, key, tomlPath.getFileName());
                }
            }

            if (!loaded.isEmpty()) {
                globalStats.clear();
                globalStats.putAll(loaded);
                LOGGER.info("[WeaponConfig] Loaded {} weapon entries from {}", loaded.size(), tomlPath.getFileName());
            }
        } catch (Exception e) {
            LOGGER.error("[WeaponConfig] Failed to load {}", tomlPath, e);
        }
    }

    private static void saveToml() {
        if (dataDirectory == null) return;
        Path tomlPath = dataDirectory.resolve(TOML_FILE);
        StringBuilder sb = new StringBuilder();
        sb.append("# DevMod per-world weapon overrides\n");
        sb.append("# Generated automatically from WeaponConfigManager\n\n");

        globalStats.forEach((item, stats) -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item));
            sb.append("[weapon.\"").append(id).append("\"]\n");
            sb.append("attackDamage = ").append(stats.getAttackDamage()).append("\n");
            sb.append("attackSpeed = ").append(stats.getAttackSpeed()).append("\n");
            sb.append("attackReach = ").append(stats.getAttackReach()).append("\n");
            sb.append("attackKnockback = ").append(stats.getAttackKnockback()).append("\n");
            sb.append("armorPenetration = ").append(stats.getArmorPenetration()).append("\n");
            sb.append("baseDamageBonus = ").append(stats.getBaseDamageBonus()).append("\n");
            sb.append("critChance = ").append(stats.getCritChance()).append("\n");
            sb.append("critDamage = ").append(stats.getCritDamage()).append("\n");
            sb.append("fireDamageBonus = ").append(stats.getFireDamageBonus()).append("\n");
            sb.append("magicDamageBonus = ").append(stats.getMagicDamageBonus()).append("\n");
            sb.append("lifesteal = ").append(stats.getLifesteal()).append("\n\n");
        });

        try {
            Files.createDirectories(dataDirectory);
            Files.writeString(tomlPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[WeaponConfig] Failed to save {}", tomlPath, e);
        }
    }

    private static float parseFloat(String val) {
        return Float.parseFloat(val);
    }

    private static float parseClampedFloat(String val) {
        return Mth.clamp(Float.parseFloat(val), 0.0f, 1.0f);
    }
}
