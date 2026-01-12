package com.devmod.clone.item;

import javax.annotation.Nonnull;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.config.Config;

/**
 * Grinder item for the Clone Pulverizer.
 * Used as replaceable rollers with durability.
 * Durability is configurable via devmod-common.toml.
 */
public class GrinderItem extends Item {

    /** Default durability used at construction (config may override at runtime). */
    public static final int DEFAULT_DURABILITY = 256;

    public GrinderItem() {
        super(new Item.Properties()
            .stacksTo(1)
            .durability(DEFAULT_DURABILITY));
    }

    /**
     * Get max damage from config for dynamic durability changes.
     * This allows runtime config changes without restart.
     */
    @Override
    public int getMaxDamage(@Nonnull ItemStack stack) {
        return Config.GRINDER_DURABILITY.get();
    }
}
