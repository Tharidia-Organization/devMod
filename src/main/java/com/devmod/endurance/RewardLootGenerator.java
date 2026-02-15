package com.devmod.endurance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.endurance.RewardSystem.LootEntry;
import com.devmod.endurance.RewardSystem.LootTier;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

/**
 * Handles loot table initialization and loot drop generation for the reward system.
 */
class RewardLootGenerator {

    private final Map<LootTier, List<LootEntry>> lootTables = new EnumMap<>(LootTier.class);
    private final Random random;

    RewardLootGenerator(Random random) {
        this.random = random;
        initializeLootTables();
    }

    Map<LootTier, List<LootEntry>> getLootTables() {
        return lootTables;
    }

    /**
     * Generate loot drops based on performance.
     */
    List<ItemStack> generateLootDrops(ServerPlayer player, EnduranceQuest quest,
                                       IComboSession comboSession) {
        List<ItemStack> drops = new ArrayList<>();
        RegistryAccess registryAccess = player.level().registryAccess();

        // Number of drops based on waves cleared
        int dropCount = Math.min(quest.getCurrentWave(), 10);

        // Quality boost from combo performance
        float qualityBoost = 0;
        if (comboSession != null) {
            qualityBoost = comboSession.getHighestRank().ordinal() * 0.05f;
        }

        for (int i = 0; i < dropCount; i++) {
            LootTier tier = rollLootTier(qualityBoost);
            LootEntry entry = rollLootEntry(tier);
            if (entry != null) {
                ItemStack stack = entry.createStack(random, registryAccess);
                drops.add(stack);

                // Telemetry: record loot drop
                String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()))).toString();
                EnduranceTelemetryService.INSTANCE.recordLootDrop(
                    player.getUUID(), quest.getQuestId(), itemId, stack.getCount(), tier
                );
            }
        }

        // Bonus drop for completing all waves
        if (quest.getState() == EnduranceQuestState.COMPLETED) {
            // Guaranteed rare+ drop
            LootTier bonusTier = random.nextFloat() < 0.1f ? LootTier.LEGENDARY :
                                 random.nextFloat() < 0.3f ? LootTier.EPIC : LootTier.RARE;
            LootEntry bonusEntry = rollLootEntry(bonusTier);
            if (bonusEntry != null) {
                ItemStack bonusStack = bonusEntry.createStack(random, registryAccess);
                drops.add(bonusStack);

                // Telemetry: record bonus loot drop
                String bonusItemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(bonusStack.getItem()))).toString();
                EnduranceTelemetryService.INSTANCE.recordLootDrop(
                    player.getUUID(), quest.getQuestId(), bonusItemId, bonusStack.getCount(), bonusTier
                );
            }
        }

        return drops;
    }

    private LootTier rollLootTier(float qualityBoost) {
        float roll = random.nextFloat() * 100 - qualityBoost * 20;

        float cumulative = 0;
        for (LootTier tier : LootTier.values()) {
            if (tier.getDropWeight() <= 0) continue;
            cumulative += tier.getDropWeight();
            if (roll < cumulative) {
                return tier;
            }
        }
        return LootTier.COMMON;
    }

    private LootEntry rollLootEntry(LootTier tier) {
        List<LootEntry> entries = lootTables.get(tier);
        if (entries == null || entries.isEmpty()) return null;
        return entries.get(random.nextInt(entries.size()));
    }

    private void initializeLootTables() {
        // Common loot
        lootTables.put(LootTier.COMMON, Arrays.asList(
            new LootEntry(Items.IRON_INGOT, 3, 8),
            new LootEntry(Items.GOLD_INGOT, 2, 5),
            new LootEntry(Items.COAL, 8, 16),
            new LootEntry(Items.LEATHER, 4, 8),
            new LootEntry(Items.EXPERIENCE_BOTTLE, 2, 4),
            new LootEntry(Items.ARROW, 16, 32),
            new LootEntry(Items.BREAD, 4, 8)
        ));

        // Uncommon loot
        lootTables.put(LootTier.UNCOMMON, Arrays.asList(
            new LootEntry(Items.DIAMOND, 1, 3),
            new LootEntry(Items.EMERALD, 2, 5),
            new LootEntry(Items.GOLDEN_APPLE, 1, 2),
            new LootEntry(Items.ENDER_PEARL, 2, 4),
            new LootEntry(Items.BLAZE_ROD, 2, 4),
            new LootEntry(Items.IRON_SWORD, 1, 1),
            new LootEntry(Items.IRON_CHESTPLATE, 1, 1)
        ));

        // Rare loot
        lootTables.put(LootTier.RARE, Arrays.asList(
            new LootEntry(Items.DIAMOND, 3, 6),
            new LootEntry(Items.NETHERITE_SCRAP, 1, 2),
            new LootEntry(Items.ENCHANTED_GOLDEN_APPLE, 1, 1),
            new LootEntry(Items.DIAMOND_SWORD, 1, 1),
            new LootEntry(Items.DIAMOND_CHESTPLATE, 1, 1),
            new LootEntry(Items.ELYTRA, 1, 1),
            new LootEntry(Items.TOTEM_OF_UNDYING, 1, 1)
        ));

        // Epic loot
        lootTables.put(LootTier.EPIC, Arrays.asList(
            new LootEntry(Items.NETHERITE_INGOT, 1, 2),
            new LootEntry(Items.NETHERITE_SWORD, 1, 1),
            new LootEntry(Items.NETHERITE_CHESTPLATE, 1, 1),
            new LootEntry(Items.NETHER_STAR, 1, 1),
            new LootEntry(Items.DRAGON_EGG, 1, 1),
            new LootEntry(Items.BEACON, 1, 1)
        ));

        // Legendary loot
        lootTables.put(LootTier.LEGENDARY, Arrays.asList(
            new LootEntry(Items.NETHERITE_INGOT, 3, 5),
            new LootEntry(Items.NETHER_STAR, 2, 3),
            new LootEntry(Items.ENCHANTED_GOLDEN_APPLE, 3, 5),
            // These will have special enchantments applied
            new LootEntry(Items.NETHERITE_SWORD, 1, 1, true),
            new LootEntry(Items.NETHERITE_CHESTPLATE, 1, 1, true)
        ));

        // Mythic loot (achievement-only)
        lootTables.put(LootTier.MYTHIC, Arrays.asList(
            new LootEntry(Items.TOTEM_OF_UNDYING, 1, 2),
            new LootEntry(Items.ELYTRA, 1, 1),
            new LootEntry(Items.DRAGON_BREATH, 8, 16),
            new LootEntry(Items.HEART_OF_THE_SEA, 2, 4),
            new LootEntry(Items.NETHER_STAR, 5, 8)
        ));
    }
}
