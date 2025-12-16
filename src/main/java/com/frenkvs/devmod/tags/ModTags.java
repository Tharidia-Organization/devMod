package com.frenkvs.devmod.tags;

import com.frenkvs.devmod.DevMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * DevMod tag definitions for weapon detection and editor filters.
 */
public final class ModTags {
    private ModTags() {}

    public static final class Items {
        public static final TagKey<Item> EDITABLE_MELEE_WEAPONS = tag("editable_melee_weapons");
        public static final TagKey<Item> EDITABLE_RANGED_WEAPONS = tag("editable_ranged_weapons");
        public static final TagKey<Item> EDITABLE_SHIELDS = tag("editable_shields");
        public static final TagKey<Item> MELEE_WEAPONS = tag("melee_weapons");
        public static final TagKey<Item> RANGED_WEAPONS = tag("ranged_weapons");
        public static final TagKey<Item> NOT_EDITABLE = tag("not_editable");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(
                java.util.Objects.requireNonNull(Registries.ITEM),
                java.util.Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(
                    java.util.Objects.requireNonNull(DevMod.MODID),
                    java.util.Objects.requireNonNull(name)
                ))
            );
        }
    }
}
