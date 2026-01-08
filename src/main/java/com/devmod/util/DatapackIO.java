package com.devmod.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.devmod.config.handler.impl.ArmorConfigHandler;
import com.devmod.config.handler.impl.WeaponConfigHandler;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.WeaponStats;

public final class DatapackIO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatapackIO.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Include _meta block in exported JSON for traceability */
    private static final boolean INCLUDE_EXPORT_METADATA = true;

    private DatapackIO() {}

    /**
     * Export current global armor/weapon overrides into a datapack.
     *
     * @param packName directory under datapacks/
     * @return number of files exported
     */
    public static int exportOverrides(String packName) {
        // Null safety on game directory
        Path gameDir = ConfigPaths.getGameDir();
        if (gameDir == null) {
            LOGGER.error("[DatapackIO] Game directory is null, cannot export datapack");
            return 0;
        }
        Path base = gameDir.resolve("datapacks").resolve(packName);
        int count = 0;
        try {
            // pack.mcmeta
            writePackMeta(base);

            // Armor overrides
            Path armorDir = base.resolve("data/devmod/item_modifiers/armor");
            for (Map.Entry<Item, ArmorStats> entry : ArmorConfigHandler.getEveryGlobalStats().entrySet()) {
                Item item = entry.getKey();
                if (item == null) {
                    LOGGER.warn("[DatapackIO] Skipping null armor item in export");
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                Path out = armorDir.resolve(id.toString().replace(":", "_") + ".json");
                writeArmor(out, id, entry.getValue());
                count++;
            }

            // Weapon overrides
            Path weaponDir = base.resolve("data/devmod/item_modifiers/weapons");
            for (Map.Entry<Item, WeaponStats> entry : WeaponConfigHandler.getEveryGlobalStats().entrySet()) {
                Item item = entry.getKey();
                if (item == null) {
                    LOGGER.warn("[DatapackIO] Skipping null weapon item in export");
                    continue;
                }
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
        // Null safety on game directory
        Path gameDir = ConfigPaths.getGameDir();
        if (gameDir == null) {
            LOGGER.error("[DatapackIO] Game directory is null, cannot import datapack");
            return 0;
        }
        Path base = gameDir.resolve("datapacks").resolve(packName);
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
                        ArmorConfigHandler.INSTANCE.setGlobalStats(armorItem, stats);
                        imported++;
                    } catch (Exception e) {
                        LOGGER.warn("[DatapackIO] Failed to import armor file {}", file.getFileName(), e);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("[DatapackIO] Failed to list armor directory {}: {}", armorDir, e.getMessage());
            }
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
                        WeaponConfigHandler.INSTANCE.setGlobalStats(weaponItem, stats);
                        imported++;
                    } catch (Exception e) {
                        LOGGER.warn("[DatapackIO] Failed to import weapon file {}", file.getFileName(), e);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("[DatapackIO] Failed to list weapon directory {}: {}", weaponDir, e.getMessage());
            }
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
        pack.addProperty("description", "DevMod overrides exported at " + LocalDateTime.now(ZoneId.systemDefault()));

        JsonObject root = new JsonObject();
        root.add("pack", pack);

        Files.writeString(base.resolve("pack.mcmeta"), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static void writeArmor(Path path, ResourceLocation id, ArmorStats stats) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "devmod:armor_stats");
        json.addProperty("target", id.toString());

        JsonObject values = new JsonObject();
        values.addProperty("physical_reduction", stats.getPhysicalReduction());
        values.addProperty("fire_reduction", stats.getFireReduction());
        values.addProperty("magic_reduction", stats.getMagicReduction());
        values.addProperty("explosion_reduction", stats.getExplosionReduction());
        values.addProperty("projectile_reduction", stats.getProjectileReduction());
        values.addProperty("armor_bonus", stats.getArmorBonus());
        values.addProperty("toughness_bonus", stats.getToughnessBonus());
        values.addProperty("knockback_resistance", stats.getKnockbackResistance());
        values.addProperty("thorns_reflect", stats.isThornsReflect());
        values.addProperty("thorns_percent", stats.getThornsPercent());
        values.addProperty("shield_reflect_projectiles", stats.isShieldReflectProjectiles());
        values.addProperty("shield_block_strength", stats.getShieldBlockStrength());
        values.addProperty("shield_recovery_speed", stats.getShieldRecoverySpeed());

        json.add("values", values);

        // Add export metadata for traceability
        if (INCLUDE_EXPORT_METADATA) {
            json.add("_meta", createExportMetadata());
        }

        Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    private static void writeWeapon(Path path, ResourceLocation id, WeaponStats stats) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "devmod:weapon_stats");
        json.addProperty("target", id.toString());

        JsonObject values = new JsonObject();
        values.addProperty("attack_damage", stats.getAttackDamage());
        values.addProperty("attack_speed", stats.getAttackSpeed());
        values.addProperty("attack_reach", stats.getAttackReach());
        values.addProperty("attack_knockback", stats.getAttackKnockback());
        values.addProperty("armor_penetration", stats.getArmorPenetration());
        values.addProperty("base_damage_bonus", stats.getBaseDamageBonus());
        values.addProperty("crit_chance", stats.getCritChance());
        values.addProperty("crit_damage", stats.getCritDamage());
        values.addProperty("damage_bonus", stats.getDamageBonus());
        values.addProperty("sweeping_ratio", stats.getSweepingRatio());
        values.addProperty("armor_shred", stats.getArmorShred());
        values.addProperty("damage_vs_undead", stats.getDamageVsUndead());
        values.addProperty("damage_vs_arthropods", stats.getDamageVsArthropods());
        values.addProperty("damage_vs_players", stats.getDamageVsPlayers());
        values.addProperty("true_damage_percent", stats.getTrueDamagePercent());
        values.addProperty("fire_damage_bonus", stats.getFireDamageBonus());
        values.addProperty("magic_damage_bonus", stats.getMagicDamageBonus());
        values.addProperty("lifesteal", stats.getLifesteal());
        values.addProperty("clear_tool_rules", stats.isClearToolRules());

        json.add("values", values);

        // Add export metadata for traceability
        if (INCLUDE_EXPORT_METADATA) {
            json.add("_meta", createExportMetadata());
        }

        Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    /**
     * Creates the _meta block for export traceability.
     * Contains export timestamp and mod version.
     */
    private static JsonObject createExportMetadata() {
        JsonObject meta = new JsonObject();
        meta.addProperty("exported_at", LocalDateTime.now(ZoneId.systemDefault()).toString());
        meta.addProperty("devmod_version", getModVersion());
        return meta;
    }

    /**
     * Gets the mod version from NeoForge ModContainer or falls back to "unknown".
     */
    private static String getModVersion() {
        try {
            return net.neoforged.fml.ModList.get()
                    .getModContainerById("devmod")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
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
        if (values.has("physical_reduction")) stats.setPhysicalReduction(values.get("physical_reduction").getAsFloat());
        if (values.has("fire_reduction")) stats.setFireReduction(values.get("fire_reduction").getAsFloat());
        if (values.has("magic_reduction")) stats.setMagicReduction(values.get("magic_reduction").getAsFloat());
        if (values.has("explosion_reduction")) stats.setExplosionReduction(values.get("explosion_reduction").getAsFloat());
        if (values.has("projectile_reduction")) stats.setProjectileReduction(values.get("projectile_reduction").getAsFloat());
        if (values.has("armor_bonus")) stats.setArmorBonus(values.get("armor_bonus").getAsFloat());
        if (values.has("toughness_bonus")) stats.setToughnessBonus(values.get("toughness_bonus").getAsFloat());
        if (values.has("knockback_resistance")) stats.setKnockbackResistance(values.get("knockback_resistance").getAsFloat());
        if (values.has("thorns_reflect")) stats.setThornsReflect(values.get("thorns_reflect").getAsBoolean());
        if (values.has("thorns_percent")) stats.setThornsPercent(values.get("thorns_percent").getAsFloat());
        if (values.has("shield_reflect_projectiles")) stats.setShieldReflectProjectiles(values.get("shield_reflect_projectiles").getAsBoolean());
        if (values.has("shield_block_strength")) stats.setShieldBlockStrength(values.get("shield_block_strength").getAsFloat());
        if (values.has("shield_recovery_speed")) stats.setShieldRecoverySpeed(values.get("shield_recovery_speed").getAsFloat());
        return stats;
    }

    private static WeaponStats parseWeapon(JsonObject values) {
        WeaponStats stats = new WeaponStats();
        if (values == null) return stats;
        if (values.has("attack_damage")) stats.setAttackDamage(values.get("attack_damage").getAsFloat());
        if (values.has("attack_speed")) stats.setAttackSpeed(values.get("attack_speed").getAsFloat());
        if (values.has("attack_reach")) stats.setAttackReach(values.get("attack_reach").getAsFloat());
        if (values.has("attack_knockback")) stats.setAttackKnockback(values.get("attack_knockback").getAsFloat());
        if (values.has("armor_penetration")) stats.setArmorPenetration(values.get("armor_penetration").getAsFloat());
        if (values.has("base_damage_bonus")) stats.setBaseDamageBonus(values.get("base_damage_bonus").getAsFloat());
        if (values.has("crit_chance")) stats.setCritChance(values.get("crit_chance").getAsFloat());
        if (values.has("crit_damage")) stats.setCritDamage(values.get("crit_damage").getAsFloat());
        if (values.has("damage_bonus")) stats.setDamageBonus(values.get("damage_bonus").getAsFloat());
        if (values.has("sweeping_ratio")) stats.setSweepingRatio(values.get("sweeping_ratio").getAsFloat());
        if (values.has("armor_shred")) stats.setArmorShred(values.get("armor_shred").getAsFloat());
        if (values.has("damage_vs_undead")) stats.setDamageVsUndead(values.get("damage_vs_undead").getAsFloat());
        if (values.has("damage_vs_arthropods")) stats.setDamageVsArthropods(values.get("damage_vs_arthropods").getAsFloat());
        if (values.has("damage_vs_players")) stats.setDamageVsPlayers(values.get("damage_vs_players").getAsFloat());
        if (values.has("true_damage_percent")) stats.setTrueDamagePercent(values.get("true_damage_percent").getAsFloat());
        if (values.has("fire_damage_bonus")) stats.setFireDamageBonus(values.get("fire_damage_bonus").getAsFloat());
        if (values.has("magic_damage_bonus")) stats.setMagicDamageBonus(values.get("magic_damage_bonus").getAsFloat());
        if (values.has("lifesteal")) stats.setLifesteal(values.get("lifesteal").getAsFloat());
        if (values.has("clear_tool_rules")) stats.setClearToolRules(values.get("clear_tool_rules").getAsBoolean());
        return stats;
    }
}
