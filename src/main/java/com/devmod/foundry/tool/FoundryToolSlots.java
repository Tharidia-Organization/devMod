package com.devmod.foundry.tool;

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import com.devmod.foundry.tool.modifier.FoundryModifierDefinition;
import com.devmod.foundry.tool.modifier.FoundryModifierRegistry;
import com.devmod.foundry.tool.modifier.FoundryModifierSlot;

/**
 * Calculates modifier slot usage for foundry tools.
 */
public final class FoundryToolSlots {
    private FoundryToolSlots() {}

    public static SlotUsage calculate(FoundryToolDefinition definition, FoundryToolData data) {
        int totalUpgrades = Math.max(0, definition.baseUpgrades() + data.bonusUpgrades());
        int totalAbilities = Math.max(0, definition.baseAbilities() + data.bonusAbilities());
        int usedUpgrades = 0;
        int usedAbilities = 0;

        for (Map.Entry<ResourceLocation, Integer> entry : data.modifiers().entrySet()) {
            FoundryModifierDefinition modifier = findModifier(entry.getKey());
            if (modifier == null) {
                continue;
            }
            int level = entry.getValue();
            int slots = Math.max(0, modifier.slots() * level);
            if (modifier.slotType() == FoundryModifierSlot.ABILITY) {
                usedAbilities += slots;
            } else {
                usedUpgrades += slots;
            }
        }

        return new SlotUsage(totalUpgrades, usedUpgrades, totalAbilities, usedAbilities);
    }

    @Nullable
    private static FoundryModifierDefinition findModifier(ResourceLocation id) {
        for (FoundryModifierDefinition def : FoundryModifierRegistry.all()) {
            if (Objects.equals(def.id(), id)) {
                return def;
            }
        }
        return null;
    }

    public record SlotUsage(
        int totalUpgrades,
        int usedUpgrades,
        int totalAbilities,
        int usedAbilities
    ) {
        public int freeUpgrades() {
            return Math.max(0, totalUpgrades - usedUpgrades);
        }

        public int freeAbilities() {
            return Math.max(0, totalAbilities - usedAbilities);
        }
    }
}
