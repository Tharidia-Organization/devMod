package com.devmod.foundry.item;

import net.minecraft.world.item.Item;

/**
 * Flux item with a configurable purity bonus.
 */
public class FoundryFluxItem extends Item {
    private final float purityBonus;

    public FoundryFluxItem(Properties properties, float purityBonus) {
        super(properties);
        this.purityBonus = purityBonus;
    }

    public float getPurityBonus() {
        return purityBonus;
    }
}
