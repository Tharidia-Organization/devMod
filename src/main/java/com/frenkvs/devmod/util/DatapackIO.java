package com.frenkvs.devmod.util;

import com.frenkvs.devmod.ArmorConfigManager;
import com.frenkvs.devmod.ArmorStats;
import com.frenkvs.devmod.WeaponConfigManager;
import com.frenkvs.devmod.WeaponStats;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal datapack export/import utilities for DevMod overrides.
 * Structure follows docs/editor-design-system/06-persistence.md:
 * datapacks/<pack>/data/devmod/item_modifiers/{armor|weapons}/<item>.json
 */
public final class DatapackIO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatapackIO.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DatapackIO() {}

    /**
     * Export current global armor/weapon overrides into a datapack.
     *
     * @param packName directory under datapacks/
     * @return number of files exported
     */
    public static int exportOverrides(String packName) {
        Path base = ConfigPaths.getGameDir().resolve("datapacks").resolve(packName);
        int count = 0;
        try {
            // pack.mcmeta
            writePackMeta(base);

            // Armor overrides
            Path armorDir = base.resolve("data/devmod/item_modifiers/armor");
            for (Map.Entry<Item, ArmorStats> entry : ArmorConfigManager.getAllGlobalStats().entrySet()) {
                Item item = Objects.requireNonNull(entry.getKey());
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                Path out = armorDir.resolve(id.toString().replace(":", "_") + ".json");
                writeArmor(out, id, entry.getValue());
                count++;
            }

            // Weapon overrides
            Path weaponDir = base.resolve("data/devmod/item_modifiers/weapons");
            for (Map.Entry<Item, WeaponStats> entry : WeaponConfigManager.getAllGlobalStats().entrySet()) {
                Item item = Objects.requireNonNull(entry.getKey());
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                Path out = weaponDir.resolve(id.toString().replace(":", "_") + ".json");
                writeWeapon(out, id, entry.getValue());
                count++;
            }

            LOGGER.info("[DatapackIO] Exported {} overrides to datapack '{}'", count, packName);
        } catch (Exception e) {
            LOGGER.error("[DatapackIO] Failed to export datapack {}", packName, e);
        }
        return count;
    }

    /**
     * Import overrides from an existing datapack folder.
     *
     * @param packName datapack directory under datapacks/
     * @return number of overrides imported
     */
    public static int importOverrides(String packName) {
        Path base = ConfigPaths.getGameDir().resolve("datapacks").resolve(packName);
        int imported = 0;

        // Armor
        Path armorDir = base.resolve("data/devmod/item_modifiers/armor");
        if (Files.exists(armorDir)) {
            try (var stream = Files.list(armorDir)) {
                for (Path file : stream.toList()) {
                    try {
                        JsonObject json = readJson(file);
                        if (json == null) continue;
                        var targetNode = json.getAsJsonPrimitive("target");
                        if (targetNode == null) continue;
                        String target = Objects.requireNonNull(targetNode.getAsString(), "target");
                        ResourceLocation id = ResourceLocation.tryParse(target);
                        if (id == null) continue;
                        ArmorStats stats = parseArmor(json.getAsJsonObject("values"));
                        Item armorItem = ItemLookup.getItem(id);
                        if (armorItem != null) {
                            ArmorConfigManager.setGlobalStats(armorItem, stats);
                            imported++;
                        } else {
                            LOGGER.warn("[DatapackIO] Unknown armor item id {}", id);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[DatapackIO] Failed to import armor file {}", file.getFileName(), e);
                    }
                }
            } catch (IOException ignored) {}
        }

        // Weapons
        Path weaponDir = base.resolve("data/devmod/item_modifiers/weapons");
        if (Files.exists(weaponDir)) {
            try (var stream = Files.list(weaponDir)) {
                for (Path file : stream.toList()) {
                    try {
                        JsonObject json = readJson(file);
                        if (json == null) continue;
                        var targetNode = json.getAsJsonPrimitive("target");
                        if (targetNode == null) continue;
                        String target = Objects.requireNonNull(targetNode.getAsString(), "target");
                        ResourceLocation id = ResourceLocation.tryParse(target);
                        if (id == null) continue;
                        WeaponStats stats = parseWeapon(json.getAsJsonObject("values"));
                        Item weaponItem = ItemLookup.getItem(id);
                        if (weaponItem != null) {
                            WeaponConfigManager.setGlobalStats(weaponItem, stats);
                            imported++;
                        } else {
                            LOGGER.warn("[DatapackIO] Unknown weapon item id {}", id);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[DatapackIO] Failed to import weapon file {}", file.getFileName(), e);
                    }
                }
            } catch (IOException ignored) {}
        }

        if (imported > 0) {
            LOGGER.info("[DatapackIO] Imported {} overrides from datapack '{}'", imported, packName);
        }
        return imported;
    }

    private static void writePackMeta(Path base) throws IOException {
        Files.createDirectories(base);
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 48);
        pack.addProperty("description", "DevMod overrides exported at " + LocalDateTime.now());

        JsonObject root = new JsonObject();
        root.add("pack", pack);

        Files.writeString(base.resolve("pack.mcmeta"), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static void writeArmor(Path path, ResourceLocation id, ArmorStats stats) throws IOException {
        Files.createDirectories(path.getParent());
        JsonObject json = new JsonObject();
        json.addProperty("type", "devmod:armor_stats");
        json.addProperty("target", id.toString());

        JsonObject values = new JsonObject();
        values.addProperty("physical_reduction", stats.physicalReduction);
        values.addProperty("fire_reduction", stats.fireReduction);
        values.addProperty("magic_reduction", stats.magicReduction);
        values.addProperty("explosion_reduction", stats.explosionReduction);
        values.addProperty("projectile_reduction", stats.projectileReduction);
        values.addProperty("armor_bonus", stats.armorBonus);
        values.addProperty("toughness_bonus", stats.toughnessBonus);
        values.addProperty("knockback_resistance", stats.knockbackResistance);
        values.addProperty("thorns_reflect", stats.thornsReflect);
        values.addProperty("thorns_percent", stats.thornsPercent);
        values.addProperty("shield_reflect_projectiles", stats.shieldReflectProjectiles);
        values.addProperty("shield_block_strength", stats.shieldBlockStrength);
        values.addProperty("shield_recovery_speed", stats.shieldRecoverySpeed);

        json.add("values", values);
        Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    private static void writeWeapon(Path path, ResourceLocation id, WeaponStats stats) throws IOException {
        Files.createDirectories(path.getParent());
        JsonObject json = new JsonObject();
        json.addProperty("type", "devmod:weapon_stats");
        json.addProperty("target", id.toString());

        JsonObject values = new JsonObject();
        values.addProperty("attack_damage", stats.attackDamage);
        values.addProperty("attack_speed", stats.attackSpeed);
        values.addProperty("attack_reach", stats.attackReach);
        values.addProperty("attack_knockback", stats.attackKnockback);
        values.addProperty("armor_penetration", stats.armorPenetration);
        values.addProperty("base_damage_bonus", stats.baseDamageBonus);
        values.addProperty("crit_chance", stats.critChance);
        values.addProperty("crit_damage", stats.critDamage);
        values.addProperty("damage_bonus", stats.damageBonus);
        values.addProperty("armor_shred", stats.armorShred);
        values.addProperty("damage_vs_undead", stats.damageVsUndead);
        values.addProperty("damage_vs_arthropods", stats.damageVsArthropods);
        values.addProperty("damage_vs_players", stats.damageVsPlayers);
        values.addProperty("true_damage_percent", stats.trueDamagePercent);
        values.addProperty("fire_damage_bonus", stats.fireDamageBonus);
        values.addProperty("magic_damage_bonus", stats.magicDamageBonus);
        values.addProperty("lifesteal", stats.lifesteal);
        values.addProperty("clear_tool_rules", stats.clearToolRules);

        json.add("values", values);
        Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    private static JsonObject readJson(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return GSON.fromJson(content, JsonObject.class);
        } catch (Exception e) {
            LOGGER.warn("[DatapackIO] Failed to read {}", file, e);
            return null;
        }
    }

    private static ArmorStats parseArmor(JsonObject values) {
        ArmorStats stats = new ArmorStats();
        if (values == null) return stats;
        if (values.has("physical_reduction")) stats.physicalReduction = values.get("physical_reduction").getAsFloat();
        if (values.has("fire_reduction")) stats.fireReduction = values.get("fire_reduction").getAsFloat();
        if (values.has("magic_reduction")) stats.magicReduction = values.get("magic_reduction").getAsFloat();
        if (values.has("explosion_reduction")) stats.explosionReduction = values.get("explosion_reduction").getAsFloat();
        if (values.has("projectile_reduction")) stats.projectileReduction = values.get("projectile_reduction").getAsFloat();
        if (values.has("armor_bonus")) stats.armorBonus = values.get("armor_bonus").getAsFloat();
        if (values.has("toughness_bonus")) stats.toughnessBonus = values.get("toughness_bonus").getAsFloat();
        if (values.has("knockback_resistance")) stats.knockbackResistance = values.get("knockback_resistance").getAsFloat();
        if (values.has("thorns_reflect")) stats.thornsReflect = values.get("thorns_reflect").getAsBoolean();
        if (values.has("thorns_percent")) stats.thornsPercent = values.get("thorns_percent").getAsFloat();
        if (values.has("shield_reflect_projectiles")) stats.shieldReflectProjectiles = values.get("shield_reflect_projectiles").getAsBoolean();
        if (values.has("shield_block_strength")) stats.shieldBlockStrength = values.get("shield_block_strength").getAsFloat();
        if (values.has("shield_recovery_speed")) stats.shieldRecoverySpeed = values.get("shield_recovery_speed").getAsFloat();
        return stats;
    }

    private static WeaponStats parseWeapon(JsonObject values) {
        WeaponStats stats = new WeaponStats();
        if (values == null) return stats;
        if (values.has("attack_damage")) stats.attackDamage = values.get("attack_damage").getAsFloat();
        if (values.has("attack_speed")) stats.attackSpeed = values.get("attack_speed").getAsFloat();
        if (values.has("attack_reach")) stats.attackReach = values.get("attack_reach").getAsFloat();
        if (values.has("attack_knockback")) stats.attackKnockback = values.get("attack_knockback").getAsFloat();
        if (values.has("armor_penetration")) stats.armorPenetration = values.get("armor_penetration").getAsFloat();
        if (values.has("base_damage_bonus")) stats.baseDamageBonus = values.get("base_damage_bonus").getAsFloat();
        if (values.has("crit_chance")) stats.critChance = values.get("crit_chance").getAsFloat();
        if (values.has("crit_damage")) stats.critDamage = values.get("crit_damage").getAsFloat();
        if (values.has("damage_bonus")) stats.damageBonus = values.get("damage_bonus").getAsFloat();
        if (values.has("armor_shred")) stats.armorShred = values.get("armor_shred").getAsFloat();
        if (values.has("damage_vs_undead")) stats.damageVsUndead = values.get("damage_vs_undead").getAsFloat();
        if (values.has("damage_vs_arthropods")) stats.damageVsArthropods = values.get("damage_vs_arthropods").getAsFloat();
        if (values.has("damage_vs_players")) stats.damageVsPlayers = values.get("damage_vs_players").getAsFloat();
        if (values.has("true_damage_percent")) stats.trueDamagePercent = values.get("true_damage_percent").getAsFloat();
        if (values.has("fire_damage_bonus")) stats.fireDamageBonus = values.get("fire_damage_bonus").getAsFloat();
        if (values.has("magic_damage_bonus")) stats.magicDamageBonus = values.get("magic_damage_bonus").getAsFloat();
        if (values.has("lifesteal")) stats.lifesteal = values.get("lifesteal").getAsFloat();
        if (values.has("clear_tool_rules")) stats.clearToolRules = values.get("clear_tool_rules").getAsBoolean();
        return stats;
    }
}
