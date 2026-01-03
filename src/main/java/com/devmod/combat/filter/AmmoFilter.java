package com.devmod.combat.filter;

import java.lang.reflect.Method;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.ammo.AmmoSystem;
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
            return AmmoSystem.matchesFilter(pickup, filterValue);
        } catch (Exception e) {
            LOGGER.debug("Ammo filter check failed: {}", e.getMessage());
            return true;
        }
    }

    @Nullable
    private static String getAmmoFilter(ItemStack weapon) {
        String nbtFilter = readAmmoFilterFromNbt(weapon);

        // 1. Check DataComponent (modern storage from RangedWeaponModule)
        try {
            if (RangedComponents.isAmmoFilterBound()) {
                ResourceLocation tagFilter = weapon.get(
                    requireNonNull(RangedComponents.AMMO_TAG_FILTER.get()));
                if (tagFilter != null) {
                    if (nbtFilter != null && !nbtFilter.isBlank()) {
                        return nbtFilter.trim();
                    }
                    return AmmoSystem.formatAmmoFilter(tagFilter);
                }
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to read ammo filter from DataComponent: {}", e.getMessage());
        }

        // 2. Fallback to NBT CustomData (legacy storage from RangedModule)
        return nbtFilter == null ? null : nbtFilter.trim();
    }

    @Nullable
    private static String readAmmoFilterFromNbt(ItemStack weapon) {
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

        return ranged.getString(NBT_AMMO_FILTER);
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

    @Nonnull
    private static <T> T requireNonNull(T value) {
        return Objects.requireNonNull(value);
    }
}
