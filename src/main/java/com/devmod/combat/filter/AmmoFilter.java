package com.devmod.combat.filter;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
public final class AmmoFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmmoFilter.class);

    private AmmoFilter() {}

    /**
     * Checks if the arrow matches the weapon's ammo filter.
     *
     * @param weapon The ranged weapon
     * @param arrow The arrow projectile
     * @return true if the ammo is allowed, false otherwise
     */
    public static boolean matches(ItemStack weapon, AbstractArrow arrow) {
        try {
            String filterValue = getAmmoFilter(weapon);
            if (filterValue == null || filterValue.isEmpty()) {
                return true;
            }

            ItemStack pickup = getPickupItem(arrow);
            return matchesFilter(pickup, filterValue);
        } catch (Exception e) {
            LOGGER.debug("Ammo filter check failed: {}", e.getMessage());
            return true;
        }
    }

    private static String getAmmoFilter(ItemStack weapon) {
        var custom = weapon.getOrDefault(
            Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
            Objects.requireNonNull(net.minecraft.world.item.component.CustomData.EMPTY)
        );
        var tag = custom.copyTag();
        if (tag == null || !tag.contains("RangedStats")) {
            return null;
        }

        var ranged = tag.getCompound("RangedStats");
        if (!ranged.contains("ammoFilter")) {
            return null;
        }

        String rawFilter = ranged.getString("ammoFilter");
        if (rawFilter == null) {
            return null;
        }

        return Objects.requireNonNull(rawFilter).trim();
    }

    private static ItemStack getPickupItem(AbstractArrow arrow) {
        try {
            Method m = AbstractArrow.class.getDeclaredMethod("getPickupItem");
            m.setAccessible(true);
            Object res = m.invoke(arrow);
            if (res instanceof ItemStack stack) {
                return stack;
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static boolean matchesFilter(ItemStack pickup, String filterValue) {
        if (filterValue.startsWith("#")) {
            return matchesTag(pickup, filterValue.substring(1));
        }
        return matchesItemId(pickup, filterValue);
    }

    private static boolean matchesTag(ItemStack pickup, String tagIdStr) {
        ResourceLocation tagId = ResourceLocation.tryParse(Objects.requireNonNull(tagIdStr));
        if (tagId == null) {
            return false;
        }

        TagKey<Item> tagKey = TagKey.create(
            Objects.requireNonNull(Registries.ITEM),
            Objects.requireNonNull(tagId)
        );
        return pickup.is(Objects.requireNonNull(tagKey));
    }

    private static boolean matchesItemId(ItemStack pickup, String filterValue) {
        if (pickup.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(pickup.getItem()));
        if (id == null) {
            return false;
        }

        String idStr = id.toString().toLowerCase(Locale.ROOT);
        String normFilter = filterValue.trim().toLowerCase(Locale.ROOT);
        return idStr.equals(normFilter) || idStr.endsWith(normFilter);
    }
}
