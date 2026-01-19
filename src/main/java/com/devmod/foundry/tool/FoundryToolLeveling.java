package com.devmod.foundry.tool;

import net.minecraft.world.item.ItemStack;

/**
 * XP and leveling logic for foundry tools.
 */
public final class FoundryToolLeveling {
    private static final int MAX_LEVEL = 10;

    private FoundryToolLeveling() {}

    public static int getXpForNextLevel(int level) {
        int clamped = Math.max(1, level);
        return 100 + (clamped - 1) * 75;
    }

    public static FoundryToolData addXp(ItemStack stack, FoundryToolDefinition definition, FoundryToolData data, int xpGained) {
        if (xpGained <= 0) {
            return data;
        }
        int level = data.level();
        int xp = data.xp() + xpGained;
        int bonusUpgrades = data.bonusUpgrades();
        int bonusAbilities = data.bonusAbilities();

        while (level < MAX_LEVEL) {
            int required = getXpForNextLevel(level);
            if (xp < required) {
                break;
            }
            xp -= required;
            level += 1;
            bonusUpgrades += 1;
            if (level % 5 == 0) {
                bonusAbilities += 1;
            }
        }

        FoundryToolData updated = data
            .withLevel(level)
            .withXp(xp)
            .withBonusUpgrades(bonusUpgrades)
            .withBonusAbilities(bonusAbilities);
        updated.writeToStack(stack);
        return updated;
    }
}
