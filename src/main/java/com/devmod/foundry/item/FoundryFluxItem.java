package com.devmod.foundry.item;

import net.minecraft.world.item.Item;

/**
 * Flux item with a configurable purity bonus.
 */
public class FoundryFluxItem extends Item {
    private final float purityBonus;
    private final float purityCap;

    public FoundryFluxItem(Properties properties, float purityBonus, float purityCap) {
        super(properties);
        this.purityBonus = purityBonus;
        this.purityCap = purityCap;
    }

    public float getPurityBonus() {
        return purityBonus;
    }

    public float getPurityCap() {
        return purityCap;
    }
}
