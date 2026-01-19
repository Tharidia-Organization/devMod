package com.devmod.foundry.tool;

import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialStats;

/**
 * Repair calculations for foundry tools.
 */
public final class FoundryToolRepair {
    private FoundryToolRepair() {}

    public static int getRepairAmount(FoundryMaterialDefinition material, int repairCount) {
        FoundryMaterialStats head = material.getStats("head");
        FoundryMaterialStats handle = material.getStats("handle");
        int base = Math.max(1, head.durability() / 2 + handle.durability() / 4);
        double penalty = Math.pow(0.9, Math.max(0, repairCount));
        return Math.max(1, (int) Math.round(base * penalty));
    }
}
