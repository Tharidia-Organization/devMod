package com.devmod.testing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
public class ModDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModDiscoveryService.class);

    public static final ModDiscoveryService INSTANCE = new ModDiscoveryService();

    // Discovered mod information
    private final Map<String, ModInfo> discoveredMods = new LinkedHashMap<>();
    private boolean hasScanned = false;

    // Mod categories for test prioritization
    public enum ModCategory {
        COMBAT("Combat", "Mods that add weapons, armor, or combat mechanics"),
        MAGIC("Magic", "Mods that add spells, enchantments, or magical items"),
        CREATURES("Creatures", "Mods that add new mobs or modify existing ones"),
        TOOLS("Tools", "Mods that add tools, utilities, or quality-of-life features"),
        WORLD("World", "Mods that add dimensions, biomes, or world generation"),
        FOOD("Food", "Mods that add food, cooking, or farming"),
        TECH("Tech", "Mods that add machines, automation, or technology"),
        DECORATION("Decoration", "Mods that add decorative blocks or furniture"),
        LIBRARY("Library", "API/Library mods used by other mods"),
        UNKNOWN("Unknown", "Uncategorized mods");

        private final String name;
        private final String description;

        ModCategory(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    /**
     * Information about a discovered mod.
     */
    public static class ModInfo {
        private final String modId;
        private final String displayName;
        private final String version;
        private final String description;
        private ModCategory category = ModCategory.UNKNOWN;

        // Discovered content
        private final List<ResourceLocation> items = new ArrayList<>();
        private final List<ResourceLocation> blocks = new ArrayList<>();
        private final List<ResourceLocation> entities = new ArrayList<>();
        private final List<ResourceLocation> effects = new ArrayList<>();
        private final List<ResourceLocation> enchantments = new ArrayList<>();

        // Combat-specific content
        private final List<ResourceLocation> weapons = new ArrayList<>();
        private final List<ResourceLocation> armor = new ArrayList<>();
        private final List<ResourceLocation> hostileMobs = new ArrayList<>();

        public ModInfo(String modId, String displayName, String version, String description) {
            this.modId = modId;
            this.displayName = displayName;
            this.version = version;
            this.description = description;
        }

        public String getModId() { return modId; }
        public String getDisplayName() { return displayName; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public ModCategory getCategory() { return category; }
        public void setCategory(ModCategory category) { this.category = category; }

        public List<ResourceLocation> getItems() { return Collections.unmodifiableList(items); }
        public List<ResourceLocation> getBlocks() { return Collections.unmodifiableList(blocks); }
        public List<ResourceLocation> getEntities() { return Collections.unmodifiableList(entities); }
        public List<ResourceLocation> getEffects() { return Collections.unmodifiableList(effects); }
        public List<ResourceLocation> getEnchantments() { return Collections.unmodifiableList(enchantments); }
        public List<ResourceLocation> getWeapons() { return Collections.unmodifiableList(weapons); }
        public List<ResourceLocation> getArmor() { return Collections.unmodifiableList(armor); }
        public List<ResourceLocation> getHostileMobs() { return Collections.unmodifiableList(hostileMobs); }

        public int getTotalContent() {
            return items.size() + blocks.size() + entities.size() + effects.size() + enchantments.size();
        }

        public boolean hasCombatContent() {
            return !weapons.isEmpty() || !armor.isEmpty() || !hostileMobs.isEmpty();
        }
    }

    private ModDiscoveryService() {}

    /**
     * Scan all loaded mods and discover their content.
     * Call this after registries are frozen (during world load or later).
     */
    public void scanMods() {
        if (hasScanned) return;

        LOGGER.info("Starting mod discovery scan...");
        long startTime = System.currentTimeMillis();

        // Get all loaded mods from NeoForge
        ModList modList = ModList.get();
        List<IModInfo> mods = modList.getMods();

        for (IModInfo modInfo : mods) {
            String modId = modInfo.getModId();

            // Skip minecraft and neoforge core
            if (modId.equals("minecraft") || modId.equals("neoforge") || modId.equals("forge")) {
                continue;
            }

            ModInfo info = new ModInfo(
                modId,
                modInfo.getDisplayName(),
                modInfo.getVersion().toString(),
                modInfo.getDescription()
            );

            // Scan registries for this mod's content
            scanItems(modId, info);
            scanBlocks(modId, info);
            scanEntities(modId, info);
            scanEffects(modId, info);
            scanEnchantments(modId, info);

            // Categorize the mod based on content
            categorizeMod(info);

            discoveredMods.put(modId, info);

            LOGGER.debug("Discovered mod: {} ({}) - {} items, {} entities",
                info.getDisplayName(), modId, info.items.size(), info.entities.size());
        }

        hasScanned = true;
        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Mod discovery complete: {} mods found in {}ms", discoveredMods.size(), elapsed);
    }

    private void scanItems(String modId, ModInfo info) {
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation key = entry.getKey().location();
            if (key.getNamespace().equals(modId)) {
                info.items.add(key);

                // Classify item type
                Item item = entry.getValue();
                classifyItem(item, key, info);
            }
        }
    }

    private void classifyItem(Item item, ResourceLocation key, ModInfo info) {
        // Check if weapon (has attack damage attribute or is a sword/axe type)
        String path = key.getPath().toLowerCase();

        // Weapons detection
        if (path.contains("sword") || path.contains("blade") || path.contains("katana") ||
            path.contains("dagger") || path.contains("axe") || path.contains("mace") ||
            path.contains("hammer") || path.contains("spear") || path.contains("halberd") ||
            path.contains("scythe") || path.contains("staff") || path.contains("wand") ||
            path.contains("bow") || path.contains("crossbow") || path.contains("gun")) {
            info.weapons.add(key);
        }

        // Armor detection
        if (path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") ||
            path.contains("boots") || path.contains("armor") || path.contains("cuirass") ||
            path.contains("greaves") || path.contains("gauntlet")) {
            info.armor.add(key);
        }
    }

    private void scanBlocks(String modId, ModInfo info) {
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            ResourceLocation key = entry.getKey().location();
            if (key.getNamespace().equals(modId)) {
                info.blocks.add(key);
            }
        }
    }

    private void scanEntities(String modId, ModInfo info) {
        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            ResourceLocation key = entry.getKey().location();
            if (key.getNamespace().equals(modId)) {
                info.entities.add(key);

                // Check if hostile mob
                EntityType<?> type = entry.getValue();
                String category = type.getCategory().getName();
                if (category.equals("monster") || category.equals("creature")) {
                    // Heuristic: if name contains hostile keywords
                    String path = key.getPath().toLowerCase();
                    if (path.contains("zombie") || path.contains("skeleton") || path.contains("demon") ||
                        path.contains("boss") || path.contains("dragon") || path.contains("golem") ||
                        path.contains("spider") || path.contains("creeper") || path.contains("witch") ||
                        path.contains("phantom") || path.contains("warden") || path.contains("monster")) {
                        info.hostileMobs.add(key);
                    }
                }
            }
        }
    }

    private void scanEffects(String modId, ModInfo info) {
        for (var entry : BuiltInRegistries.MOB_EFFECT.entrySet()) {
            ResourceLocation key = entry.getKey().location();
            if (key.getNamespace().equals(modId)) {
                info.effects.add(key);
            }
        }
    }

    private void scanEnchantments(String modId, ModInfo info) {
        // Note: In NeoForge 1.21+, enchantments are data-driven and not in BuiltInRegistries
        // They are registered via datapack. This method serves as a placeholder for future
        // implementation when datapack scanning is added, or for mods that register
        // enchantments through other means.
        // For now, enchantment discovery requires datapack scanning which is more complex.
    }

    /**
     * Categorize mod based on its content.
     */
    private void categorizeMod(ModInfo info) {
        String modId = info.modId.toLowerCase();
        String desc = info.description.toLowerCase();
        String name = info.displayName.toLowerCase();

        // Check name/description keywords
        if (containsAny(modId + desc + name, "combat", "weapon", "sword", "armor", "fight", "battle", "knight", "warrior")) {
            info.setCategory(ModCategory.COMBAT);
        } else if (containsAny(modId + desc + name, "magic", "spell", "wizard", "mage", "arcane", "enchant", "mystic")) {
            info.setCategory(ModCategory.MAGIC);
        } else if (containsAny(modId + desc + name, "mob", "creature", "monster", "animal", "pet", "spawn")) {
            info.setCategory(ModCategory.CREATURES);
        } else if (containsAny(modId + desc + name, "food", "cook", "farm", "crop", "delight", "cuisine")) {
            info.setCategory(ModCategory.FOOD);
        } else if (containsAny(modId + desc + name, "tech", "machine", "energy", "power", "auto", "factory")) {
            info.setCategory(ModCategory.TECH);
        } else if (containsAny(modId + desc + name, "world", "biome", "dimension", "terrain", "structure")) {
            info.setCategory(ModCategory.WORLD);
        } else if (containsAny(modId + desc + name, "decor", "furniture", "build", "block", "aesthetic")) {
            info.setCategory(ModCategory.DECORATION);
        } else if (containsAny(modId + desc + name, "lib", "api", "core", "util")) {
            info.setCategory(ModCategory.LIBRARY);
        } else if (info.hasCombatContent()) {
            // If has weapons/armor/mobs, categorize as combat
            info.setCategory(ModCategory.COMBAT);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // ==================== QUERY METHODS ====================

    /**
     * Get all discovered mods.
     */
    public Collection<ModInfo> getAllMods() {
        ensureScanned();
        return Collections.unmodifiableCollection(discoveredMods.values());
    }

    /**
     * Get mod by ID.
     */
    public ModInfo getMod(String modId) {
        ensureScanned();
        return discoveredMods.get(modId);
    }

    /**
     * Get mods by category.
     */
    public List<ModInfo> getModsByCategory(ModCategory category) {
        ensureScanned();
        return discoveredMods.values().stream()
            .filter(m -> m.getCategory() == category)
            .collect(Collectors.toList());
    }

    /**
     * Get all mods with combat content (weapons, armor, hostile mobs).
     */
    public List<ModInfo> getCombatMods() {
        ensureScanned();
        return discoveredMods.values().stream()
            .filter(ModInfo::hasCombatContent)
            .collect(Collectors.toList());
    }

    /**
     * Get all weapons from all mods.
     */
    public List<ResourceLocation> getAllWeapons() {
        ensureScanned();
        List<ResourceLocation> weapons = new ArrayList<>();
        for (ModInfo mod : discoveredMods.values()) {
            weapons.addAll(mod.weapons);
        }
        return weapons;
    }

    /**
     * Get all hostile mobs from all mods.
     */
    public List<ResourceLocation> getAllHostileMobs() {
        ensureScanned();
        List<ResourceLocation> mobs = new ArrayList<>();
        for (ModInfo mod : discoveredMods.values()) {
            mobs.addAll(mod.hostileMobs);
        }
        return mobs;
    }

    /**
     * Get count of discovered mods.
     */
    public int getModCount() {
        ensureScanned();
        return discoveredMods.size();
    }

    /**
     * Check if a mod is loaded.
     */
    public boolean isModLoaded(String modId) {
        ensureScanned();
        return discoveredMods.containsKey(modId);
    }

    private void ensureScanned() {
        if (!hasScanned) {
            scanMods();
        }
    }

    /**
     * Force rescan (useful after world load).
     */
    public void rescan() {
        hasScanned = false;
        discoveredMods.clear();
        scanMods();
    }

    /**
     * Get a summary of discovered mods.
     */
    public String getSummary() {
        ensureScanned();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Mod Discovery Summary ===\n");
        sb.append("Total Mods: ").append(discoveredMods.size()).append("\n\n");

        // Group by category
        Map<ModCategory, List<ModInfo>> byCategory = discoveredMods.values().stream()
            .collect(Collectors.groupingBy(ModInfo::getCategory));

        for (ModCategory cat : ModCategory.values()) {
            List<ModInfo> mods = byCategory.getOrDefault(cat, Collections.emptyList());
            if (!mods.isEmpty()) {
                sb.append(cat.getName()).append(" (").append(mods.size()).append("):\n");
                for (ModInfo mod : mods) {
                    sb.append("  - ").append(mod.getDisplayName());
                    if (mod.hasCombatContent()) {
                        sb.append(" [").append(mod.weapons.size()).append(" weapons, ");
                        sb.append(mod.hostileMobs.size()).append(" mobs]");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
