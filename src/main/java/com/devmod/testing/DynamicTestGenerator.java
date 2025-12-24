package com.devmod.testing;

import com.devmod.testing.ModDiscoveryService.ModInfo;
import com.devmod.testing.TestCase.TestPriority;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatically generates test cases based on discovered mod content.
 * Creates tests for weapons, mobs, effects, and mod-specific features.
 *
 * Test generation is modular and extensible through TestTemplates.
 */
public class DynamicTestGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicTestGenerator.class);

    public static final DynamicTestGenerator INSTANCE = new DynamicTestGenerator();

    // Generated tests organized by mod (thread-safe for 100-1000 concurrent users)
    private final Map<String, List<TestCase>> generatedTests = new ConcurrentHashMap<>();
    private final AtomicBoolean hasGenerated = new AtomicBoolean(false);

    // Test templates for different content types (thread-safe)
    private final List<TestTemplate> templates = new CopyOnWriteArrayList<>();

    /**
     * Template for generating tests based on mod content.
     */
    public interface TestTemplate {
        /**
         * Generate tests for a mod.
         * @param mod The mod info
         * @return List of generated test cases
         */
        List<TestCase> generateTests(ModInfo mod);

        /**
         * Check if this template applies to the given mod.
         */
        boolean appliesTo(ModInfo mod);

        /**
         * Get template name for logging.
         */
        String getName();
    }

    private DynamicTestGenerator() {
        registerDefaultTemplates();
    }

    /**
     * Register default test templates.
     */
    private void registerDefaultTemplates() {
        // DevMod core tests (always run first)
        templates.add(new DevModCoreTestTemplate());

        // Iron's Spellbooks specialized template (must be before generic templates)
        templates.add(new IronSpellbooksTestTemplate());

        // Weapon testing template
        templates.add(new WeaponTestTemplate());

        // Mob testing template
        templates.add(new MobTestTemplate());

        // Effect testing template
        templates.add(new EffectTestTemplate());

        // Armor testing template
        templates.add(new ArmorTestTemplate());

        // Generic mod functionality template
        templates.add(new GenericModTestTemplate());
    }

    /**
     * Template for DevMod core functionality tests.
     * Always generates tests regardless of mod content.
     */
    private static class DevModCoreTestTemplate implements TestTemplate {
        @Override
        public String getName() { return "DevMod Core Tests"; }

        @Override
        public boolean appliesTo(ModInfo mod) {
            // Only apply to DevMod itself
            return "devmod".equals(mod.getModId());
        }

        @Override
        public List<TestCase> generateTests(ModInfo mod) {
            List<TestCase> tests = new ArrayList<>();

            // Add all DevMod armor test cases
            tests.addAll(DevModArmorTestCases.generateTestCases());

            // Add all DevMod preset system test cases
            tests.addAll(DevModPresetTestCases.generateTestCases());

            LOGGER.info("[DevModCoreTestTemplate] Generated {} DevMod core tests", tests.size());
            return tests;
        }
    }

    /**
     * Register a custom test template.
     */
    public void registerTemplate(TestTemplate template) {
        templates.add(template);
        LOGGER.debug("Registered test template: {}", template.getName());
    }

    /**
     * Generate all tests for all discovered mods.
     */
    public void generateAllTests() {
        if (hasGenerated.get()) return;

        LOGGER.info("Starting dynamic test generation...");
        long startTime = System.currentTimeMillis();

        // Ensure mods are scanned
        ModDiscoveryService.INSTANCE.scanMods();

        int totalTests = 0;

        for (ModInfo mod : ModDiscoveryService.INSTANCE.getAllMods()) {
            List<TestCase> modTests = generateTestsForMod(mod);
            if (!modTests.isEmpty()) {
                generatedTests.put(mod.getModId(), modTests);
                totalTests += modTests.size();
            }
        }

        hasGenerated.set(true);
        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Dynamic test generation complete: {} tests for {} mods in {}ms",
            totalTests, generatedTests.size(), elapsed);
    }

    /**
     * Generate tests for a specific mod.
     */
    private List<TestCase> generateTestsForMod(ModInfo mod) {
        List<TestCase> tests = new ArrayList<>();

        for (TestTemplate template : templates) {
            if (template.appliesTo(mod)) {
                try {
                    List<TestCase> templateTests = template.generateTests(mod);
                    tests.addAll(templateTests);
                    LOGGER.debug("Template {} generated {} tests for {}",
                        template.getName(), templateTests.size(), mod.getModId());
                } catch (Exception e) {
                    LOGGER.error("Error generating tests with template {} for mod {}: {}",
                        template.getName(), mod.getModId(), e.getMessage());
                }
            }
        }

        return tests;
    }

    // ==================== DEFAULT TEMPLATES ====================

    /**
     * Template for weapon-related tests.
     */
    private static class WeaponTestTemplate implements TestTemplate {
        @Override
        public String getName() { return "Weapon Tests"; }

        @Override
        public boolean appliesTo(ModInfo mod) {
            return !mod.getWeapons().isEmpty();
        }

        @Override
        public List<TestCase> generateTests(ModInfo mod) {
            List<TestCase> tests = new ArrayList<>();
            String modId = mod.getModId();
            String modName = mod.getDisplayName();

            // Test: Use any weapon from this mod
            final String capturedModId = modId;
            tests.add(TestCase.withProgress(
                modId + "_weapon_basic",
                modName + " - Weapons",
                "Use " + modName + " Weapon",
                "Attack a mob using any weapon from " + modName,
                "1. Equip a weapon from " + modName + "\n2. Attack any mob\n3. Verify damage is displayed",
                TestPriority.HIGH,
                progress -> {
                    // Check if player has killed mobs with weapons from this mod
                    int kills = progress.getKillsWithModWeapon(capturedModId);
                    if (kills >= 5) return 1.0f;
                    if (kills >= 1) return 0.5f;
                    return progress.hasInteractedWithMod(capturedModId) ? 0.2f : 0f;
                }
            ));

            // If mod has multiple weapons, add variety test
            if (mod.getWeapons().size() >= 3) {
                tests.add(TestCase.withProgress(
                    modId + "_weapon_variety",
                    modName + " - Weapons",
                    "Test Multiple " + modName + " Weapons",
                    "Use at least 3 different weapons from " + modName,
                    "1. Collect 3+ weapons from " + modName + "\n2. Attack mobs with each weapon\n3. Compare damage values",
                    TestPriority.MEDIUM,
                    progress -> {
                        // Progress based on weapon usage count
                        int usage = progress.getWeaponUsageFromMod(capturedModId);
                        if (usage >= 10) return 1.0f;
                        if (usage >= 5) return 0.7f;
                        if (usage >= 3) return 0.5f;
                        return usage / 10.0f;
                    }
                ));
            }

            // Headshot test for precision weapons
            List<ResourceLocation> precisionWeapons = mod.getWeapons().stream()
                .filter(w -> {
                    String path = w.getPath().toLowerCase();
                    return path.contains("bow") || path.contains("crossbow") ||
                           path.contains("gun") || path.contains("rifle");
                })
                .toList();

            if (!precisionWeapons.isEmpty()) {
                tests.add(TestCase.withProgress(
                    modId + "_weapon_headshot",
                    modName + " - Weapons",
                    modName + " Ranged Headshot",
                    "Land a headshot with a ranged weapon from " + modName,
                    "1. Equip a ranged weapon from " + modName + "\n2. Aim for a mob's head\n3. Verify headshot is detected",
                    TestPriority.MEDIUM,
                    progress -> progress.getHeadshots() >= 1 ? 1.0f : 0f
                ));
            }

            return tests;
        }
    }

    /**
     * Template for mob-related tests.
     */
    private static class MobTestTemplate implements TestTemplate {
        @Override
        public String getName() { return "Mob Tests"; }

        @Override
        public boolean appliesTo(ModInfo mod) {
            return !mod.getEntities().isEmpty();
        }

        @Override
        public List<TestCase> generateTests(ModInfo mod) {
            List<TestCase> tests = new ArrayList<>();
            String modId = mod.getModId();
            String modName = mod.getDisplayName();
            final String capturedModId = modId;

            // Test: Encounter mod's mobs
            if (!mod.getHostileMobs().isEmpty()) {
                tests.add(TestCase.withProgress(
                    modId + "_mob_encounter",
                    modName + " - Creatures",
                    "Fight " + modName + " Mobs",
                    "Engage in combat with hostile mobs from " + modName,
                    "1. Find or spawn a hostile mob from " + modName + "\n2. Attack and defeat it\n3. Verify body part detection works",
                    TestPriority.HIGH,
                    progress -> {
                        int kills = progress.getMobsKilledFromMod(capturedModId);
                        if (kills >= 5) return 1.0f;
                        if (kills >= 3) return 0.7f;
                        if (kills >= 1) return 0.4f;
                        return progress.hasInteractedWithMod(capturedModId) ? 0.1f : 0f;
                    }
                ));

                // Boss test if mod likely has bosses
                List<ResourceLocation> potentialBosses = mod.getHostileMobs().stream()
                    .filter(m -> m.getPath().toLowerCase().contains("boss"))
                    .toList();

                if (!potentialBosses.isEmpty()) {
                    tests.add(TestCase.withProgress(
                        modId + "_mob_boss",
                        modName + " - Creatures",
                        "Defeat " + modName + " Boss",
                        "Defeat a boss mob from " + modName,
                        "1. Summon or find a boss from " + modName + "\n2. Fight and defeat it\n3. Verify damage tracking throughout fight",
                        TestPriority.HIGH,
                        progress -> {
                            // Boss kills tracked separately - for now check high damage dealt
                            int mobKills = progress.getMobsKilledFromMod(capturedModId);
                            return mobKills >= 10 ? 1.0f : mobKills / 10.0f;
                        }
                    ));
                }
            }

            // Test: Mob attributes display
            tests.add(TestCase.withProgress(
                modId + "_mob_stats",
                modName + " - Creatures",
                modName + " Mob Stats Overlay",
                "Verify mob stats are displayed for " + modName + " creatures",
                "1. Look at any mob from " + modName + "\n2. Verify stats overlay appears\n3. Check HP, armor, and damage values are shown",
                TestPriority.MEDIUM,
                progress -> progress.hasInteractedWithMod(capturedModId) ? 1.0f : 0f
            ));

            return tests;
        }
    }

    /**
     * Template for potion/effect tests.
     */
    private static class EffectTestTemplate implements TestTemplate {
        @Override
        public String getName() { return "Effect Tests"; }

        @Override
        public boolean appliesTo(ModInfo mod) {
            return !mod.getEffects().isEmpty();
        }

        @Override
        public List<TestCase> generateTests(ModInfo mod) {
            List<TestCase> tests = new ArrayList<>();
            String modId = mod.getModId();
            String modName = mod.getDisplayName();
            final String capturedModId = modId;

            tests.add(TestCase.withProgress(
                modId + "_effect_basic",
                modName + " - Effects",
                "Experience " + modName + " Effect",
                "Gain a potion effect from " + modName,
                "1. Use a potion or item that grants an effect from " + modName + "\n2. Verify effect is active\n3. Check effect tracking works",
                TestPriority.MEDIUM,
                progress -> {
                    int effects = progress.getEffectsUsedFromMod(capturedModId);
                    if (effects >= 1) return 1.0f;
                    return progress.hasInteractedWithMod(capturedModId) ? 0.3f : 0f;
                }
            ));

            // Multiple effects test
            if (mod.getEffects().size() >= 3) {
                tests.add(TestCase.withProgress(
                    modId + "_effect_variety",
                    modName + " - Effects",
                    "Experience Multiple " + modName + " Effects",
                    "Gain at least 3 different effects from " + modName,
                    "1. Collect potions/items with different effects\n2. Use each one\n3. Verify all are tracked",
                    TestPriority.LOW,
                    progress -> {
                        int effects = progress.getEffectsUsedFromMod(capturedModId);
                        if (effects >= 3) return 1.0f;
                        return effects / 3.0f;
                    }
                ));
            }

            return tests;
        }
    }

    /**
     * Template for armor tests.
     */
    private static class ArmorTestTemplate implements TestTemplate {
        @Override
        public String getName() { return "Armor Tests"; }

        @Override
        public boolean appliesTo(ModInfo mod) {
            return !mod.getArmor().isEmpty();
        }

        @Override
        public List<TestCase> generateTests(ModInfo mod) {
            List<TestCase> tests = new ArrayList<>();
            String modId = mod.getModId();
            String modName = mod.getDisplayName();
            final String capturedModId = modId;

            tests.add(TestCase.withProgress(
                modId + "_armor_basic",
                modName + " - Armor",
                "Wear " + modName + " Armor",
                "Equip armor from " + modName + " and take damage",
                "1. Equip armor from " + modName + "\n2. Take damage from a mob\n3. Verify armor reduction is displayed correctly",
                TestPriority.MEDIUM,
                progress -> {
                    int usage = progress.getArmorUsageFromMod(capturedModId);
                    if (usage >= 1) return 1.0f;
                    return progress.hasInteractedWithMod(capturedModId) ? 0.3f : 0f;
                }
            ));

            // Full set test
            if (mod.getArmor().size() >= 4) {
                tests.add(TestCase.withProgress(
                    modId + "_armor_fullset",
                    modName + " - Armor",
                    "Full " + modName + " Armor Set",
                    "Equip a complete armor set from " + modName,
                    "1. Collect helmet, chestplate, leggings, boots from " + modName + "\n2. Equip all pieces\n3. Take damage and verify protection values",
                    TestPriority.LOW,
                    progress -> {
                        int usage = progress.getArmorUsageFromMod(capturedModId);
                        if (usage >= 4) return 1.0f;
                        return usage / 4.0f;
                    }
                ));
            }

            return tests;
        }
    }

    /**
     * Generic template for any mod.
     */
    private static class GenericModTestTemplate implements TestTemplate {
        @Override
        public String getName() { return "Generic Mod Tests"; }

        @Override
        public boolean appliesTo(ModInfo mod) {
            // Apply to mods with significant content
            return mod.getTotalContent() >= 5;
        }

        @Override
        public List<TestCase> generateTests(ModInfo mod) {
            List<TestCase> tests = new ArrayList<>();
            String modId = mod.getModId();
            String modName = mod.getDisplayName();
            final String capturedModId = modId;

            // Integration test
            tests.add(TestCase.withProgress(
                modId + "_integration",
                modName + " - Integration",
                modName + " DevMod Compatibility",
                "Verify DevMod features work with " + modName + " content",
                "1. Use items/mobs from " + modName + "\n2. Check damage numbers display correctly\n3. Verify body part detection works\n4. Test overlay compatibility",
                TestPriority.HIGH,
                progress -> progress.hasInteractedWithMod(capturedModId) ? 1.0f : 0f
            ));

            // Performance test for large mods
            if (mod.getTotalContent() > 50) {
                tests.add(TestCase.withProgress(
                    modId + "_performance",
                    modName + " - Performance",
                    modName + " Performance Check",
                    "Verify no performance issues with " + modName,
                    "1. Play with " + modName + " content active\n2. Monitor FPS for 2 minutes\n3. Ensure no significant drops with overlays enabled",
                    TestPriority.MEDIUM,
                    progress -> {
                        // Consider performance test passed after significant interaction
                        int totalInteractions = progress.getMobsKilledFromMod(capturedModId) +
                                               progress.getKillsWithModWeapon(capturedModId);
                        if (totalInteractions >= 20) return 1.0f;
                        return totalInteractions / 20.0f;
                    }
                ));
            }

            return tests;
        }
    }

    // ==================== QUERY METHODS ====================

    /**
     * Get all generated tests.
     */
    public Map<String, List<TestCase>> getAllGeneratedTests() {
        ensureGenerated();
        return Collections.unmodifiableMap(generatedTests);
    }

    /**
     * Get tests for a specific mod.
     */
    public List<TestCase> getTestsForMod(String modId) {
        ensureGenerated();
        return generatedTests.getOrDefault(modId, Collections.emptyList());
    }

    /**
     * Get all tests as a flat list.
     */
    public List<TestCase> getAllTestsFlat() {
        ensureGenerated();
        List<TestCase> all = new ArrayList<>();
        for (List<TestCase> modTests : generatedTests.values()) {
            all.addAll(modTests);
        }
        return all;
    }

    /**
     * Get total number of generated tests.
     */
    public int getTotalTestCount() {
        ensureGenerated();
        return generatedTests.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    /**
     * Get number of mods with generated tests.
     */
    public int getModsWithTestsCount() {
        ensureGenerated();
        return generatedTests.size();
    }

    private void ensureGenerated() {
        if (!hasGenerated.get()) {
            generateAllTests();
        }
    }

    /**
     * Force regeneration of tests.
     */
    public void regenerate() {
        hasGenerated.set(false);
        generatedTests.clear();
        generateAllTests();
    }

    /**
     * Get summary of generated tests.
     */
    public String getSummary() {
        ensureGenerated();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Dynamic Test Generation Summary ===\n");
        sb.append("Total Tests: ").append(getTotalTestCount()).append("\n");
        sb.append("Mods with Tests: ").append(getModsWithTestsCount()).append("\n\n");

        for (Map.Entry<String, List<TestCase>> entry : generatedTests.entrySet()) {
            ModInfo mod = ModDiscoveryService.INSTANCE.getMod(entry.getKey());
            String modName = mod != null ? mod.getDisplayName() : entry.getKey();
            sb.append(modName).append(": ").append(entry.getValue().size()).append(" tests\n");

            for (TestCase test : entry.getValue()) {
                sb.append("  - ").append(test.getName()).append(" [").append(test.getPriority()).append("]\n");
            }
        }

        return sb.toString();
    }

    // ==================== IRON'S SPELLBOOKS SPECIALIZED TEMPLATE ====================

    /**
     * Specialized template for Iron's Spellbooks mod.
     * Generates comprehensive tests for all 9 magic schools, spells, mana system,
     * armor sets, mobs, structures, crafting stations, and imbue system.
     */
    private static class IronSpellbooksTestTemplate implements TestTemplate {
        private static final String IRONS_SPELLBOOKS_ID = "irons_spellbooks";

        // All 9 magic schools with their characteristics
        private static final String[][] MAGIC_SCHOOLS = {
            {"fire", "Fire", "Burning damage, explosions, area denial"},
            {"ice", "Ice", "Freezing, slowness, cold damage"},
            {"lightning", "Lightning", "Chain damage, stuns, electric shocks"},
            {"holy", "Holy", "Healing, smiting undead, protection"},
            {"ender", "Ender", "Teleportation, void damage, dimensional magic"},
            {"blood", "Blood", "Life steal, sacrificial magic, debuffs"},
            {"evocation", "Evocation", "Summoning, conjuration, minions"},
            {"nature", "Nature", "Poison, thorns, animal companions"},
            {"eldritch", "Eldritch", "Cosmic horror, madness, ancient power"}
        };

        // Armor sets (11 wizard armor sets) - used in test descriptions to show available armor count
        private static final String[] ARMOR_SETS = {
            "wandering_magician", "pumpkin", "pyromancer", "electromancer",
            "archevoker", "cultist", "cryomancer", "shadowwalker",
            "priest", "plagued", "netherite_battlemage"
        };

        // Mob types
        private static final String[][] MOBS = {
            {"dead_king", "Dead King", "Boss"},
            {"archevoker", "Archevoker", "Hostile"},
            {"cryomancer", "Cryomancer", "Hostile"},
            {"pyromancer", "Pyromancer", "Hostile"},
            {"necromancer", "Necromancer", "Hostile"},
            {"priest", "Priest", "Hostile"},
            {"keeper", "Keeper", "Neutral"}
        };

        // Structure types
        private static final String[] STRUCTURES = {
            "evoker_fort", "mountain_tower", "priest_house",
            "ancient_battleground", "catacombs", "wizard_tower", "mangrove_hut"
        };

        @Override
        public String getName() { return "Iron's Spellbooks Tests"; }

        @Override
        public boolean appliesTo(ModInfo mod) {
            return IRONS_SPELLBOOKS_ID.equals(mod.getModId());
        }

        @Override
        public List<TestCase> generateTests(ModInfo mod) {
            List<TestCase> tests = new ArrayList<>();

            // ===== CORE MECHANICS =====
            addCoreMechanicsTests(tests);

            // ===== MAGIC SCHOOLS (9 schools) =====
            addMagicSchoolTests(tests);

            // ===== SPELLBOOK PROGRESSION =====
            addSpellbookProgressionTests(tests);

            // ===== CRAFTING STATIONS =====
            addCraftingStationTests(tests);

            // ===== ARMOR SETS =====
            addArmorSetTests(tests);

            // ===== MOBS & BOSS =====
            addMobTests(tests);

            // ===== STRUCTURES =====
            addStructureTests(tests);

            // ===== IMBUE SYSTEM =====
            addImbueSystemTests(tests);

            // ===== INTEGRATION =====
            addIntegrationTests(tests);

            LOGGER.info("[IronSpellbooksTestTemplate] Generated {} tests for Iron's Spellbooks", tests.size());
            return tests;
        }

        private void addCoreMechanicsTests(List<TestCase> tests) {
            // Mana System Test
            tests.add(TestCase.withProgress(
                "iss_mana_system",
                "Iron's Spellbooks - Core",
                "Mana System",
                "Test the mana regeneration and consumption system",
                "1. Cast any spell to consume mana\n2. Wait for mana to regenerate\n3. Verify mana bar displays correctly\n4. Test mana regeneration rate\n5. Verify spell fails when out of mana",
                TestPriority.HIGH,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));

            // Spell Casting Test
            tests.add(TestCase.withProgress(
                "iss_spell_casting",
                "Iron's Spellbooks - Core",
                "Basic Spell Casting",
                "Cast spells using spellbooks and scrolls",
                "1. Equip a spellbook with spells\n2. Cast a spell using right-click\n3. Verify spell animation plays\n4. Verify damage numbers display\n5. Test spell cooldowns",
                TestPriority.HIGH,
                progress -> {
                    int interactions = progress.getMobsKilledFromMod(IRONS_SPELLBOOKS_ID);
                    return interactions >= 1 ? 1.0f : 0f;
                }
            ));

            // Spell Slots Test
            tests.add(TestCase.withProgress(
                "iss_spell_slots",
                "Iron's Spellbooks - Core",
                "Spell Slot Management",
                "Test equipping and switching between spell slots",
                "1. Open spellbook inventory\n2. Assign spells to different slots\n3. Switch between slots in combat\n4. Verify hotbar displays spell icons\n5. Test spell slot limits",
                TestPriority.MEDIUM,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));

            // Spell Levels Test
            tests.add(TestCase.withProgress(
                "iss_spell_levels",
                "Iron's Spellbooks - Core",
                "Spell Level Scaling",
                "Test damage scaling with spell levels",
                "1. Obtain same spell at different levels\n2. Compare damage output\n3. Verify higher levels deal more damage\n4. Test mana cost scaling\n5. Verify level displays in tooltip",
                TestPriority.MEDIUM,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));
        }

        private void addMagicSchoolTests(List<TestCase> tests) {
            for (String[] school : MAGIC_SCHOOLS) {
                String schoolId = school[0];
                String schoolName = school[1];
                String schoolDesc = school[2];

                tests.add(TestCase.withProgress(
                    "iss_school_" + schoolId,
                    "Iron's Spellbooks - " + schoolName + " School",
                    schoolName + " Magic",
                    "Test " + schoolName + " school spells: " + schoolDesc,
                    "1. Obtain " + schoolName + " school spells\n" +
                    "2. Cast at least 3 different " + schoolName + " spells\n" +
                    "3. Verify damage type is correct\n" +
                    "4. Test school-specific effects\n" +
                    "5. Note: " + schoolDesc,
                    TestPriority.HIGH,
                    progress -> {
                        int kills = progress.getMobsKilledFromMod(IRONS_SPELLBOOKS_ID);
                        if (kills >= 5) return 1.0f;
                        if (kills >= 1) return 0.5f;
                        return 0f;
                    }
                ));
            }

            // Cross-school test
            tests.add(TestCase.withProgress(
                "iss_all_schools",
                "Iron's Spellbooks - Schools",
                "Master of All Schools",
                "Use spells from all 9 magic schools",
                "1. Collect spells from each school:\n" +
                "   - Fire, Ice, Lightning\n" +
                "   - Holy, Ender, Blood\n" +
                "   - Evocation, Nature, Eldritch\n" +
                "2. Cast at least one spell from each\n" +
                "3. Compare effectiveness against different enemies",
                TestPriority.LOW,
                progress -> {
                    int kills = progress.getMobsKilledFromMod(IRONS_SPELLBOOKS_ID);
                    return Math.min(1.0f, kills / 20.0f);
                }
            ));
        }

        private void addSpellbookProgressionTests(List<TestCase> tests) {
            // Iron Spellbook
            tests.add(TestCase.withProgress(
                "iss_spellbook_iron",
                "Iron's Spellbooks - Progression",
                "Iron Spellbook",
                "Craft and test Iron Spellbook (starter tier)",
                "1. Craft Iron Spellbook at Scroll Forge\n2. Assign spells (limited slots)\n3. Test spell power at iron tier\n4. Note spell slot limitations",
                TestPriority.HIGH,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));

            // Diamond Spellbook
            tests.add(TestCase.withProgress(
                "iss_spellbook_diamond",
                "Iron's Spellbooks - Progression",
                "Diamond Spellbook",
                "Craft and test Diamond Spellbook (mid tier)",
                "1. Craft Diamond Spellbook\n2. Compare spell slots to Iron\n3. Test spell power increase\n4. Verify more spell slots available",
                TestPriority.MEDIUM,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));

            // Netherite Spellbook
            tests.add(TestCase.withProgress(
                "iss_spellbook_netherite",
                "Iron's Spellbooks - Progression",
                "Netherite Spellbook",
                "Craft and test Netherite Spellbook (top tier)",
                "1. Upgrade to Netherite Spellbook\n2. Test maximum spell slots\n3. Verify highest spell power\n4. Test fire resistance in Nether",
                TestPriority.MEDIUM,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));
        }

        private void addCraftingStationTests(List<TestCase> tests) {
            // Scroll Forge
            tests.add(TestCase.withProgress(
                "iss_scroll_forge",
                "Iron's Spellbooks - Crafting",
                "Scroll Forge",
                "Use the Scroll Forge to create spell scrolls",
                "1. Craft a Scroll Forge\n2. Place it and open GUI\n3. Create a spell scroll\n4. Verify scroll contains correct spell\n5. Test using the scroll",
                TestPriority.HIGH,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));

            // Arcane Anvil
            tests.add(TestCase.withProgress(
                "iss_arcane_anvil",
                "Iron's Spellbooks - Crafting",
                "Arcane Anvil",
                "Use the Arcane Anvil for upgrades and inscriptions",
                "1. Craft an Arcane Anvil\n2. Inscribe a spell into a spellbook\n3. Upgrade spellbook tier\n4. Test inscription mechanics\n5. Verify spell is permanently added",
                TestPriority.HIGH,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));

            // Alchemist Cauldron
            tests.add(TestCase.withProgress(
                "iss_alchemist_cauldron",
                "Iron's Spellbooks - Crafting",
                "Alchemist Cauldron",
                "Use Alchemist Cauldron for magical brewing",
                "1. Craft an Alchemist Cauldron\n2. Add water and ingredients\n3. Create a magical potion\n4. Test potion effects\n5. Compare to vanilla brewing",
                TestPriority.MEDIUM,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));
        }

        private void addArmorSetTests(List<TestCase> tests) {
            // General armor test - references all armor sets
            tests.add(TestCase.withProgress(
                "iss_armor_general",
                "Iron's Spellbooks - Armor",
                "Wizard Armor Basics",
                "Test wizard armor protection and bonuses from " + ARMOR_SETS.length + " available sets",
                "1. Equip any wizard armor set\n2. Take damage and verify protection\n3. Check mana bonuses\n4. Verify school-specific bonuses\n5. Test set bonuses when full set equipped",
                TestPriority.HIGH,
                progress -> {
                    int usage = progress.getArmorUsageFromMod(IRONS_SPELLBOOKS_ID);
                    return usage >= 1 ? 1.0f : 0f;
                }
            ));

            // School-specific armors
            String[][] schoolArmors = {
                {"pyromancer", "Pyromancer", "Fire"},
                {"cryomancer", "Cryomancer", "Ice"},
                {"electromancer", "Electromancer", "Lightning"},
                {"priest", "Priest", "Holy"},
                {"shadowwalker", "Shadowwalker", "Ender"},
                {"cultist", "Cultist", "Blood"},
                {"archevoker", "Archevoker", "Evocation"},
                {"plagued", "Plagued", "Nature"}
            };

            for (String[] armor : schoolArmors) {
                tests.add(TestCase.withProgress(
                    "iss_armor_" + armor[0],
                    "Iron's Spellbooks - Armor",
                    armor[1] + " Armor Set",
                    "Test " + armor[1] + " armor set (" + armor[2] + " school bonuses)",
                    "1. Obtain full " + armor[1] + " armor set\n" +
                    "2. Equip all pieces\n" +
                    "3. Cast " + armor[2] + " spells and verify bonus\n" +
                    "4. Test set bonus effect\n" +
                    "5. Compare damage with/without armor",
                    TestPriority.LOW,
                    progress -> progress.getArmorUsageFromMod(IRONS_SPELLBOOKS_ID) >= 1 ? 1.0f : 0f
                ));
            }

            // Netherite Battlemage
            tests.add(TestCase.withProgress(
                "iss_armor_battlemage",
                "Iron's Spellbooks - Armor",
                "Netherite Battlemage Armor",
                "Test top-tier Netherite Battlemage armor",
                "1. Craft full Netherite Battlemage set\n2. Test high protection values\n3. Verify mana bonuses\n4. Test with heavy combat spells\n5. Compare to other armor sets",
                TestPriority.MEDIUM,
                progress -> progress.getArmorUsageFromMod(IRONS_SPELLBOOKS_ID) >= 1 ? 1.0f : 0f
            ));
        }

        private void addMobTests(List<TestCase> tests) {
            // Dead King Boss
            tests.add(TestCase.withProgress(
                "iss_boss_dead_king",
                "Iron's Spellbooks - Boss",
                "Dead King Boss Fight",
                "Defeat the Dead King boss",
                "1. Find or summon the Dead King\n2. Engage in boss fight\n3. Dodge boss mechanics\n4. Verify damage tracking during fight\n5. Collect boss loot",
                TestPriority.HIGH,
                progress -> {
                    int kills = progress.getMobsKilledFromMod(IRONS_SPELLBOOKS_ID);
                    return kills >= 10 ? 1.0f : kills / 10.0f;
                }
            ));

            // Hostile wizard mobs
            for (String[] mob : MOBS) {
                if (!"Boss".equals(mob[2])) {
                    tests.add(TestCase.withProgress(
                        "iss_mob_" + mob[0],
                        "Iron's Spellbooks - Creatures",
                        "Fight " + mob[1],
                        "Encounter and defeat " + mob[1] + " (" + mob[2] + ")",
                        "1. Find " + mob[1] + " in the world\n" +
                        "2. Engage in combat\n" +
                        "3. Test body part detection\n" +
                        "4. Verify damage numbers display\n" +
                        "5. Collect any drops",
                        TestPriority.MEDIUM,
                        progress -> {
                            int kills = progress.getMobsKilledFromMod(IRONS_SPELLBOOKS_ID);
                            return kills >= 1 ? 1.0f : 0f;
                        }
                    ));
                }
            }
        }

        private void addStructureTests(List<TestCase> tests) {
            tests.add(TestCase.withProgress(
                "iss_structures_general",
                "Iron's Spellbooks - Exploration",
                "Discover Magic Structures",
                "Find and explore Iron's Spellbooks structures",
                "1. Explore the world for magic structures:\n" +
                "   - Wizard Towers\n" +
                "   - Evoker Forts\n" +
                "   - Mountain Towers\n" +
                "   - Catacombs\n" +
                "   - Ancient Battlegrounds\n" +
                "2. Loot chests for spells and equipment\n" +
                "3. Fight structure guardians",
                TestPriority.MEDIUM,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));

            // Individual structure tests
            for (String structure : STRUCTURES) {
                String displayName = structure.replace("_", " ");
                displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);

                tests.add(TestCase.withProgress(
                    "iss_structure_" + structure,
                    "Iron's Spellbooks - Exploration",
                    "Explore " + displayName,
                    "Find and clear " + displayName,
                    "1. Locate " + displayName + " structure\n" +
                    "2. Clear all hostile mobs\n" +
                    "3. Loot all chests\n" +
                    "4. Find unique items/spells",
                    TestPriority.LOW,
                    progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
                ));
            }
        }

        private void addImbueSystemTests(List<TestCase> tests) {
            tests.add(TestCase.withProgress(
                "iss_imbue_weapon",
                "Iron's Spellbooks - Imbue",
                "Weapon Imbuing",
                "Test imbuing weapons with spell effects",
                "1. Obtain an imbue spell (e.g., Fire Imbue)\n2. Apply imbue to a weapon\n3. Attack mobs and verify effect\n4. Check duration and damage bonus\n5. Test different imbue types",
                TestPriority.MEDIUM,
                progress -> {
                    int kills = progress.getKillsWithModWeapon(IRONS_SPELLBOOKS_ID);
                    return kills >= 1 ? 1.0f : 0f;
                }
            ));

            // Specific imbue types
            String[] imbueTypes = {"fire", "ice", "lightning", "poison"};
            for (String imbue : imbueTypes) {
                String displayName = imbue.substring(0, 1).toUpperCase() + imbue.substring(1);
                tests.add(TestCase.withProgress(
                    "iss_imbue_" + imbue,
                    "Iron's Spellbooks - Imbue",
                    displayName + " Imbue",
                    "Test " + displayName + " Imbue on weapons",
                    "1. Learn " + displayName + " Imbue spell\n" +
                    "2. Cast on equipped weapon\n" +
                    "3. Attack mobs and verify " + imbue + " damage\n" +
                    "4. Check visual effects on weapon\n" +
                    "5. Note duration and cooldown",
                    TestPriority.LOW,
                    progress -> progress.getKillsWithModWeapon(IRONS_SPELLBOOKS_ID) >= 1 ? 1.0f : 0f
                ));
            }
        }

        private void addIntegrationTests(List<TestCase> tests) {
            // DevMod Integration
            tests.add(TestCase.withProgress(
                "iss_devmod_damage",
                "Iron's Spellbooks - Integration",
                "Spell Damage Tracking",
                "Verify DevMod tracks spell damage correctly",
                "1. Cast damage spells at mobs\n2. Verify damage numbers appear\n3. Check body part detection with spells\n4. Test AoE spell damage tracking\n5. Verify critical hits display",
                TestPriority.HIGH,
                progress -> {
                    int kills = progress.getMobsKilledFromMod(IRONS_SPELLBOOKS_ID);
                    return kills >= 3 ? 1.0f : kills / 3.0f;
                }
            ));

            // Curios Integration
            tests.add(TestCase.withProgress(
                "iss_curios_rings",
                "Iron's Spellbooks - Integration",
                "Curios Ring Slots",
                "Test magic rings in Curios slots",
                "1. Find or craft magic rings\n2. Equip in Curios ring slots\n3. Verify mana/spell bonuses\n4. Test multiple rings stacking\n5. Check tooltip displays",
                TestPriority.MEDIUM,
                progress -> progress.hasInteractedWithMod(IRONS_SPELLBOOKS_ID) ? 1.0f : 0f
            ));

            // Performance Test
            tests.add(TestCase.withProgress(
                "iss_performance",
                "Iron's Spellbooks - Integration",
                "Spell Performance",
                "Test performance with many spell effects",
                "1. Cast multiple AoE spells\n2. Summon multiple minions\n3. Monitor FPS during heavy combat\n4. Check for lag with particle effects\n5. Verify smooth gameplay",
                TestPriority.MEDIUM,
                progress -> {
                    int interactions = progress.getMobsKilledFromMod(IRONS_SPELLBOOKS_ID) +
                                      progress.getKillsWithModWeapon(IRONS_SPELLBOOKS_ID);
                    return interactions >= 10 ? 1.0f : interactions / 10.0f;
                }
            ));
        }
    }
}
