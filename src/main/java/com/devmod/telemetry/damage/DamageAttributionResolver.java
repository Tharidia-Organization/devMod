package com.devmod.telemetry.damage;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.compat.mods.ironsspellbooks.IronsSpellbooksCompat;
import com.devmod.compat.mods.spellengine.SpellEngineCompat;

/**
 * Resolves attacker/weapon attribution from damage sources across modded combat systems.
 */
public final class DamageAttributionResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] PROJECTILE_ITEM_METHOD_CANDIDATES = {
        "getPickupItem",
        "getPickupItemStack",
        "getItem",
        "getItemStack",
        "getWeaponItem",
        "getProjectileItem",
        "getStack"
    };

    private static final Map<Class<?>, Method> PROJECTILE_ITEM_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> PROJECTILE_ITEM_MISS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> SOURCE_ITEM_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> SOURCE_ITEM_MISS = new ConcurrentHashMap<>();

    private DamageAttributionResolver() {}

    @Nullable
    public static Entity resolveAttacker(DamageSource source) {
        if (source == null) return null;
        Entity attacker = source.getEntity();
        if (attacker != null) return attacker;

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner != null) return owner;
        }

        if (direct instanceof LivingEntity living) {
            return living;
        }

        return null;
    }

    @Nullable
    public static Item resolveWeaponOverride(DamageSource source, @Nullable Entity attacker) {
        if (source == null) return null;

        Entity direct = source.getDirectEntity();
        boolean magicDamage = isMagicDamage(source);

        Item spellItem = resolveSpellcastingItem(attacker, magicDamage);
        if (spellItem != null) {
            return spellItem;
        }

        Item sourceItem = resolveDamageSourceItem(source);
        if (sourceItem != null) {
            return sourceItem;
        }

        Item projectileItem = resolveProjectileItem(direct);
        if (projectileItem != null) {
            return projectileItem;
        }

        if (attacker instanceof LivingEntity living) {
            ItemStack main = living.getMainHandItem();
            if (!main.isEmpty()) {
                return main.getItem();
            }
            ItemStack off = living.getOffhandItem();
            if (!off.isEmpty()) {
                return off.getItem();
            }
        }

        return null;
    }

    private static boolean isMagicDamage(DamageSource source) {
        try {
            if (DamageTypeTags.WITCH_RESISTANT_TO != null && source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
                return true;
            }
        } catch (Exception ignored) {
            // Defensive: tag lookup can be null in some registries.
        }

        String msgId = source.getMsgId();
        if (msgId == null) return false;
        String lower = msgId.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("magic") || lower.contains("spell") || lower.contains("irons_spellbooks")
            || lower.contains("spell_engine");
    }

    @Nullable
    private static Item resolveSpellcastingItem(@Nullable Entity attacker, boolean magicDamage) {
        if (!magicDamage || !(attacker instanceof LivingEntity living)) {
            return null;
        }

        if (attacker instanceof Player player) {
            if (IronsSpellbooksCompat.isAvailable() && IronsSpellbooksCompat.isCasting(player)) {
                ItemStack castingItem = IronsSpellbooksCompat.findCastingItem(player);
                if (castingItem != null && !castingItem.isEmpty()) {
                    return castingItem.getItem();
                }
            }
        }

        if (SpellEngineCompat.isAvailable() && SpellEngineCompat.isCastingSpell(living)) {
            ItemStack main = living.getMainHandItem();
            if (!main.isEmpty() && SpellEngineCompat.hasSpells(main)) {
                return main.getItem();
            }
            ItemStack off = living.getOffhandItem();
            if (!off.isEmpty() && SpellEngineCompat.hasSpells(off)) {
                return off.getItem();
            }
        }

        return null;
    }

    @Nullable
    private static Item resolveProjectileItem(@Nullable Entity direct) {
        ItemStack stack = resolveProjectileItemStack(direct);
        if (stack != null && !stack.isEmpty()) {
            return stack.getItem();
        }
        return null;
    }

    @Nullable
    private static Item resolveDamageSourceItem(DamageSource source) {
        if (source == null) return null;
        Method method = findSourceItemMethod(source.getClass());
        if (method == null) return null;
        try {
            Object result = method.invoke(source);
            if (result instanceof ItemStack stack && !stack.isEmpty()) {
                return stack.getItem();
            }
            if (result instanceof Item item) {
                return item;
            }
        } catch (Exception e) {
            LOGGER.debug("[Telemetry] Failed to resolve item from DamageSource {}: {}",
                source.getClass().getName(), e.getMessage());
        }
        return null;
    }

    @Nullable
    private static ItemStack resolveProjectileItemStack(@Nullable Entity direct) {
        if (direct == null) return null;

        Method method = findProjectileItemMethod(direct.getClass());
        if (method != null) {
            try {
                Object result = method.invoke(direct);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (Exception e) {
                LOGGER.debug("[Telemetry] Failed to resolve projectile item from {}: {}",
                    direct.getClass().getName(), e.getMessage());
            }
        }

        return null;
    }

    @Nullable
    private static Method findProjectileItemMethod(Class<?> clazz) {
        if (PROJECTILE_ITEM_METHODS.containsKey(clazz)) {
            return PROJECTILE_ITEM_METHODS.get(clazz);
        }
        if (PROJECTILE_ITEM_MISS.containsKey(clazz)) {
            return null;
        }

        for (String candidate : PROJECTILE_ITEM_METHOD_CANDIDATES) {
            Method method = findMethod(clazz, candidate);
            if (method != null && ItemStack.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                PROJECTILE_ITEM_METHODS.put(clazz, method);
                return method;
            }
        }

        PROJECTILE_ITEM_MISS.put(clazz, true);
        return null;
    }

    @Nullable
    private static Method findSourceItemMethod(Class<?> clazz) {
        if (SOURCE_ITEM_METHODS.containsKey(clazz)) {
            return SOURCE_ITEM_METHODS.get(clazz);
        }
        if (SOURCE_ITEM_MISS.containsKey(clazz)) {
            return null;
        }

        String[] candidates = {
            "getWeapon",
            "getWeaponItem",
            "getItem",
            "getItemStack",
            "getSourceItem",
            "getSourceStack",
            "getWeaponStack"
        };

        for (String candidate : candidates) {
            Method method = findMethod(clazz, candidate);
            if (method != null) {
                Class<?> ret = method.getReturnType();
                if (ItemStack.class.isAssignableFrom(ret) || Item.class.isAssignableFrom(ret)) {
                    method.setAccessible(true);
                    SOURCE_ITEM_METHODS.put(clazz, method);
                    return method;
                }
            }
        }

        SOURCE_ITEM_MISS.put(clazz, true);
        return null;
    }

    @Nullable
    private static Method findMethod(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try superclass
            }
            current = current.getSuperclass();
        }
        return null;
    }

}
