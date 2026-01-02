package com.devmod.combat.filter;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.components.RangedComponents;

public final class AmmoFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmmoFilter.class);
    private static final String NBT_RANGED_STATS = "RangedStats";
    private static final String NBT_AMMO_FILTER = "ammoFilter";

    // P2: Cached reflection method to avoid repeated lookups
    private static volatile Method cachedGetPickupItemMethod = null;
    private static volatile boolean methodLookupAttempted = false;

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

            ItemStack pickup = getPickupItemViaReflection(arrow);
            return matchesFilter(pickup, filterValue);
        } catch (Exception e) {
            LOGGER.debug("Ammo filter check failed: {}", e.getMessage());
            return true;
        }
    }

    @Nullable
    private static String getAmmoFilter(ItemStack weapon) {
        // 1. Check DataComponent (modern storage from RangedWeaponModule)
        try {
            if (RangedComponents.isAmmoFilterBound()) {
                ResourceLocation tagFilter = weapon.get(
                    requireNonNull(RangedComponents.AMMO_TAG_FILTER.get()));
                if (tagFilter != null) {
                    return "#" + tagFilter.toString();
                }
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to read ammo filter from DataComponent: {}", e.getMessage());
        }

        // 2. Fallback to NBT CustomData (legacy storage from RangedModule)
        var custom = weapon.getOrDefault(
            requireNonNull(DataComponents.CUSTOM_DATA),
            requireNonNull(CustomData.EMPTY));
        var tag = custom.copyTag();
        if (tag == null || !tag.contains(NBT_RANGED_STATS)) {
            return null;
        }

        var ranged = tag.getCompound(NBT_RANGED_STATS);
        if (!ranged.contains(NBT_AMMO_FILTER)) {
            return null;
        }

        String rawFilter = ranged.getString(NBT_AMMO_FILTER);
        if (rawFilter == null) {
            return null;
        }

        return rawFilter.trim();
    }

    private static ItemStack getPickupItemViaReflection(AbstractArrow arrow) {
        // P2: Use cached method to avoid repeated reflection lookups
        Method method = getCachedGetPickupItemMethod();
        if (method == null) {
            return ItemStack.EMPTY;
        }

        try {
            Object res = method.invoke(arrow);
            if (res instanceof ItemStack stack) {
                return stack;
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to invoke getPickupItem via reflection", e);
        }
        return ItemStack.EMPTY;
    }

    /**
     * P2: Lazily initializes and caches the getPickupItem method.
     * Thread-safe via double-checked locking with volatile.
     */
    @Nullable
    private static Method getCachedGetPickupItemMethod() {
        if (!methodLookupAttempted) {
            synchronized (AmmoFilter.class) {
                if (!methodLookupAttempted) {
                    try {
                        Method m = AbstractArrow.class.getDeclaredMethod("getPickupItem");
                        m.setAccessible(true);
                        cachedGetPickupItemMethod = m;
                        LOGGER.debug("Cached getPickupItem method for AmmoFilter");
                    } catch (NoSuchMethodException e) {
                        LOGGER.warn("Failed to cache getPickupItem method: {}", e.getMessage());
                    }
                    methodLookupAttempted = true;
                }
            }
        }
        return cachedGetPickupItemMethod;
    }

    private static boolean matchesFilter(ItemStack pickup, String filterValue) {
        if (filterValue.startsWith("#")) {
            return matchesTag(pickup, filterValue.substring(1));
        }
        return matchesItemId(pickup, filterValue);
    }

    private static boolean matchesTag(ItemStack pickup, String tagIdStr) {
        ResourceLocation tagId = ResourceLocation.tryParse(requireNonNull(tagIdStr));
        if (tagId == null) {
            return false;
        }

        TagKey<Item> tagKey = TagKey.create(requireNonNull(Registries.ITEM), tagId);
        return pickup.is(requireNonNull(tagKey));
    }

    private static boolean matchesItemId(ItemStack pickup, String filterValue) {
        if (pickup.isEmpty()) {
            return false;
        }

        // getKey() never returns null for registered items.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(requireNonNull(pickup.getItem()));
        String idStr = id.toString().toLowerCase(Locale.ROOT);
        String normFilter = filterValue.trim().toLowerCase(Locale.ROOT);
        return idStr.equals(normFilter) || idStr.endsWith(normFilter);
    }

    @Nonnull
    private static <T> T requireNonNull(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
