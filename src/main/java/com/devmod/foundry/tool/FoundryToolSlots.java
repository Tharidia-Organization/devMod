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
        int totalDefense = Math.max(0, definition.baseDefense() + data.bonusDefense());
        int usedUpgrades = 0;
        int usedAbilities = 0;
        int usedDefense = 0;

        for (Map.Entry<ResourceLocation, Integer> entry : data.modifiers().entrySet()) {
            FoundryModifierDefinition modifier = findModifier(entry.getKey());
            if (modifier == null) {
                continue;
            }
            int level = entry.getValue();
            int slots = Math.max(0, modifier.slots() * level);
            FoundryModifierSlot slotType = modifier.slotType();

            // Slotless and trait modifiers don't consume slots
            if (!slotType.consumesSlots()) {
                continue;
            }

            switch (slotType) {
                case ABILITY -> usedAbilities += slots;
                case DEFENSE -> usedDefense += slots;
                default -> usedUpgrades += slots;
            }
        }

        return new SlotUsage(totalUpgrades, usedUpgrades, totalAbilities, usedAbilities, totalDefense, usedDefense);
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
        int usedAbilities,
        int totalDefense,
        int usedDefense
    ) {
        public int freeUpgrades() {
            return Math.max(0, totalUpgrades - usedUpgrades);
        }

        public int freeAbilities() {
            return Math.max(0, totalAbilities - usedAbilities);
        }

        public int freeDefense() {
            return Math.max(0, totalDefense - usedDefense);
        }
    }
}
