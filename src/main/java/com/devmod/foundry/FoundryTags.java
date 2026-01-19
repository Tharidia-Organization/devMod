package com.devmod.foundry;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import com.devmod.DevMod;

/**
 * Foundry-specific tag keys.
 */
public final class FoundryTags {
    private FoundryTags() {}

    public static final class Items {
        public static final TagKey<Item> FOUNDRY_FLUX = tag("foundry_flux");
        public static final TagKey<Item> ORE_RICH = tag("foundry_ore_rich");
        public static final TagKey<Item> ORE_POOR = tag("foundry_ore_poor");
        public static final TagKey<Item> RAW_RICH = tag("foundry_raw_rich");
        public static final TagKey<Item> RAW_POOR = tag("foundry_raw_poor");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(
                Objects.requireNonNull(Registries.ITEM),
                Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, name))
            );
        }

        private Items() {}
    }
}
