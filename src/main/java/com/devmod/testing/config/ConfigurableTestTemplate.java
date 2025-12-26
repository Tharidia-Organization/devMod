package com.devmod.testing.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.testing.DynamicTestGenerator.TestTemplate;
import com.devmod.testing.ModDiscoveryService.ModInfo;
import com.devmod.testing.TestCase;
import com.devmod.testing.TestCase.TestPriority;
import com.devmod.testing.TesterProgress;
public class ConfigurableTestTemplate implements TestTemplate {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableTestTemplate.class);

    private final ModTestConfig.TestTemplateConfig config;

    public ConfigurableTestTemplate(ModTestConfig.TestTemplateConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return config.templateName != null ? config.templateName : config.displayName + " Tests";
    }

    @Override
    public boolean appliesTo(ModInfo mod) {
        return config.modId.equals(mod.getModId());
    }

    @Override
    public List<TestCase> generateTests(ModInfo mod) {
        List<TestCase> tests = new ArrayList<>();
        String modId = config.modId;

        // Generate core tests from config
        if (config.coreTests != null) {
            for (ModTestConfig.TestDefinition testDef : config.coreTests) {
                tests.add(createTest(modId, testDef));
            }
        }

        // Generate custom tests from config
        if (config.customTests != null) {
            for (ModTestConfig.TestDefinition testDef : config.customTests) {
                tests.add(createTest(modId, testDef));
            }
        }

        // Generate magic school tests
        if (config.generateSchoolTests && config.magicSchools != null) {
            for (ModTestConfig.SchoolDefinition school : config.magicSchools) {
                tests.add(createSchoolTest(modId, school));
            }
        }

        // Generate armor tests
        if (config.generateArmorTests && config.armorSets != null && !config.armorSets.isEmpty()) {
            tests.add(createArmorOverviewTest(modId));
        }

        // Generate mob tests
        if (config.generateMobTests && config.mobs != null) {
            for (ModTestConfig.MobDefinition mob : config.mobs) {
                if ("Boss".equals(mob.type)) {
                    tests.add(createBossTest(modId, mob));
                }
            }
        }

        // Generate structure tests
        if (config.generateStructureTests && config.structures != null && !config.structures.isEmpty()) {
            tests.add(createStructureOverviewTest(modId));
        }

        // Generate crafting station tests
        if (config.craftingStations != null) {
            for (ModTestConfig.CraftingStationDefinition station : config.craftingStations) {
                tests.add(createCraftingStationTest(modId, station));
            }
        }

        // Generate spellbook tier tests
        if (config.spellbookTiers != null) {
            for (ModTestConfig.SpellbookTierDefinition tier : config.spellbookTiers) {
                tests.add(createSpellbookTierTest(modId, tier));
            }
        }

        LOGGER.info("[{}] Generated {} tests from config", getName(), tests.size());
        return tests;
    }

    private TestCase createTest(String modId, ModTestConfig.TestDefinition def) {
        TestPriority priority = parsePriority(def.priority);
        String category = config.displayName + " - " + def.category;
        String fullId = modId + "_" + def.id;

        return TestCase.withProgress(
            fullId,
            category,
            def.name,
            def.description,
            def.steps,
            priority,
            progress -> evaluateProgress(modId, def, progress)
        );
    }

    private TestCase createSchoolTest(String modId, ModTestConfig.SchoolDefinition school) {
        String category = config.displayName + " - " + school.name + " School";
        String fullId = modId + "_school_" + school.id;

        return TestCase.withProgress(
            fullId,
            category,
            school.name + " Magic",
            "Test " + school.name + " school spells: " + school.description,
            "1. Obtain " + school.name + " school spells\n" +
            "2. Cast at least 3 different " + school.name + " spells\n" +
            "3. Verify damage type is correct\n" +
            "4. Test school-specific effects\n" +
            "5. Note: " + school.description,
            TestPriority.HIGH,
            progress -> {
                int kills = progress.getMobsKilledFromMod(modId);
                if (kills >= 5) return 1.0f;
                if (kills >= 1) return 0.5f;
                return 0f;
            }
        );
    }

    private TestCase createArmorOverviewTest(String modId) {
        String category = config.displayName + " - Armor";

        return TestCase.withProgress(
            modId + "_armor_overview",
            category,
            "Armor Sets Overview",
            "Test armor protection and set bonuses (" + config.armorSets.size() + " sets available)",
            "1. Equip any armor set\n2. Take damage and verify protection\n3. Check special bonuses\n4. Test set bonus when full set equipped",
            TestPriority.MEDIUM,
            progress -> progress.getArmorUsageFromMod(modId) >= 1 ? 1.0f : 0f
        );
    }

    private TestCase createBossTest(String modId, ModTestConfig.MobDefinition boss) {
        String category = config.displayName + " - Bosses";

        return TestCase.withProgress(
            modId + "_boss_" + boss.id,
            category,
            boss.name + " Boss Fight",
            "Defeat the " + boss.name + " boss" +
                (boss.spawnLocation != null ? " in " + boss.spawnLocation : ""),
            "1. Find boss spawn location" +
                (boss.spawnLocation != null ? " (" + boss.spawnLocation + ")" : "") + "\n" +
            "2. Prepare for battle\n" +
            "3. Fight the " + boss.name + "\n" +
            "4. Learn attack patterns\n" +
            "5. Loot unique drops",
            TestPriority.HIGH,
            progress -> {
                int kills = progress.getMobsKilledFromMod(modId);
                return kills >= 1 ? 1.0f : 0f;
            }
        );
    }

    private TestCase createStructureOverviewTest(String modId) {
        String category = config.displayName + " - Exploration";

        return TestCase.withProgress(
            modId + "_structures_overview",
            category,
            "Explore Mod Structures",
            "Find and explore structures (" + config.structures.size() + " types)",
            "1. Explore the world to find mod structures\n" +
            "2. Loot chests for unique items\n" +
            "3. Encounter structure-specific mobs\n" +
            "4. Complete structure objectives",
            TestPriority.MEDIUM,
            progress -> progress.hasInteractedWithMod(modId) ? 1.0f : 0f
        );
    }

    private TestCase createCraftingStationTest(String modId, ModTestConfig.CraftingStationDefinition station) {
        String category = config.displayName + " - Crafting";
        TestPriority priority = parsePriority(station.priority);

        return TestCase.withProgress(
            modId + "_crafting_" + station.id,
            category,
            station.name,
            station.description,
            "1. Craft a " + station.name + "\n" +
            "2. Place it and open GUI\n" +
            "3. Create an item using the station\n" +
            "4. Verify output is correct",
            priority,
            progress -> progress.hasInteractedWithMod(modId) ? 1.0f : 0f
        );
    }

    private TestCase createSpellbookTierTest(String modId, ModTestConfig.SpellbookTierDefinition tier) {
        String category = config.displayName + " - Progression";
        TestPriority priority = parsePriority(tier.priority);

        return TestCase.withProgress(
            modId + "_spellbook_" + tier.id,
            category,
            tier.name,
            "Craft and test " + tier.name + " (" + tier.spellSlots + " spell slots)",
            "1. Craft " + tier.name + " using " + tier.material + "\n" +
            "2. Test spell slot count (" + tier.spellSlots + " slots)\n" +
            "3. Compare power to other tiers\n" +
            "4. Test tier-specific features",
            priority,
            progress -> progress.hasInteractedWithMod(modId) ? 1.0f : 0f
        );
    }

    private float evaluateProgress(String modId, ModTestConfig.TestDefinition def,
                                    TesterProgress progress) {
        if (def.progressCondition == null) {
            return progress.hasInteractedWithMod(modId) ? 1.0f : 0f;
        }

        int threshold = def.progressThreshold > 0 ? def.progressThreshold : 1;

        switch (def.progressCondition) {
            case "killed_mobs":
                int kills = progress.getMobsKilledFromMod(modId);
                if (kills >= threshold) return 1.0f;
                if (kills >= 1) return 0.5f;
                return 0f;

            case "armor_used":
                return progress.getArmorUsageFromMod(modId) >= threshold ? 1.0f : 0f;

            case "weapon_used":
                return progress.getWeaponUsageFromMod(modId) >= threshold ? 1.0f : 0f;

            case "interacted":
            default:
                return progress.hasInteractedWithMod(modId) ? 1.0f : 0f;
        }
    }

    private TestPriority parsePriority(String priority) {
        if (priority == null) return TestPriority.MEDIUM;
        switch (priority.toUpperCase()) {
            case "HIGH": return TestPriority.HIGH;
            case "LOW": return TestPriority.LOW;
            default: return TestPriority.MEDIUM;
        }
    }
}
