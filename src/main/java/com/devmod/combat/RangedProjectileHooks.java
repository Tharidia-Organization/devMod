package com.devmod.combat;

import java.lang.reflect.Method;
import java.util.Objects;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import com.devmod.DevMod;
import com.devmod.network.PacketValidator;
import com.devmod.stats.RangedWeaponStats;

@EventBusSubscriber(modid = DevMod.MODID)
public final class RangedProjectileHooks {

    private RangedProjectileHooks() {}
    private static volatile Method SET_PIERCE;
    private static volatile boolean pierceLookupAttempted = false;
    private static volatile boolean pierceReflectionErrorLogged = false;

    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow) {
            handleArrow(arrow);
        } else if (event.getEntity() instanceof ThrownTrident trident) {
            handleTrident(trident);
        }
    }

    private static void handleTrident(ThrownTrident trident) {
        if (!(trident.getOwner() instanceof LivingEntity shooter)) return;

        ItemStack weapon = shooter.getMainHandItem();
        if (!(weapon.getItem() instanceof net.minecraft.world.item.TridentItem)) {
            return;
        }

        var stats = RangedWeaponStats.getStats(weapon);
        PacketValidator security = PacketValidator.INSTANCE;

        float speed = (float) security.validateRangedSpeed(stats.getProjectileSpeed());
        float gravity = (float) security.validateRangedGravity(stats.getProjectileGravity());
        float baseDamage = (float) security.validateRangedBaseDamage(stats.getBaseDamage());
        float loyaltySpeed = (float) security.validateRangedSpeed(stats.getLoyaltySpeed());
        float riptideDistance = (float) security.validateRangedBaseDamage(stats.getRiptideDistance());
        boolean riptideRequiresWater = stats.isRiptideRequiresWater();

        if (baseDamage > 0) {
            trident.setBaseDamage(baseDamage);
        }
        // projectileSpeed and loyaltySpeed are multipliers, so 1.0 keeps the vanilla throw speed
        Vec3 delta = Objects.requireNonNull(trident.getDeltaMovement());
        if (delta.lengthSqr() > 0.0001 && (speed > 0 || loyaltySpeed > 0)) {
            float scalar = speed > 0 ? speed : 1.0f;
            if (loyaltySpeed > 0) scalar = Math.max(scalar, loyaltySpeed);
            Vec3 normalized = Objects.requireNonNullElseGet(delta.normalize(), () -> delta);
            trident.setDeltaMovement(Objects.requireNonNull(normalized.scale(delta.length() * scalar)));
        }
        if (gravity <= 0.0001f) {
            trident.setNoGravity(true);
        }
        if (riptideRequiresWater && !shooter.isInWaterOrRain()) {
            Vec3 current = Objects.requireNonNull(trident.getDeltaMovement());
            trident.setDeltaMovement(Objects.requireNonNull(current.scale(0.5)));
        }
        if (riptideDistance > 0 && trident.getDeltaMovement().lengthSqr() > 0) {
            Vec3 current = Objects.requireNonNull(trident.getDeltaMovement());
            Vec3 normalized = Objects.requireNonNullElseGet(current.normalize(), () -> current);
            trident.setDeltaMovement(Objects.requireNonNull(normalized.scale(Math.max(current.length(), riptideDistance / 20.0))));
        }
        // Crit-like boost for riptide-inspired damage
        if (stats.getCritChance() > 0 && shooter.getRandom().nextFloat() < stats.getCritChance()) {
            trident.setBaseDamage(trident.getBaseDamage() * Math.max(1.0f, stats.getCritDamage()));
        }
    }

    private static void handleArrow(AbstractArrow arrow) {
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;

        ItemStack weapon = shooter.getMainHandItem();
        if (!(weapon.getItem() instanceof net.minecraft.world.item.BowItem
            || weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem)) {
            return;
        }

        RangedWeaponStats stats = RangedWeaponStats.getStats(weapon);
        PacketValidator security = PacketValidator.INSTANCE;

        float speed = (float) security.validateRangedSpeed(stats.getProjectileSpeed());
        float gravity = (float) security.validateRangedGravity(stats.getProjectileGravity());
        float spread = (float) security.validateRangedSpread(stats.getProjectileSpread());
        float baseDamage = (float) security.validateRangedBaseDamage(stats.getBaseDamage());
        float critChance = stats.getCritChance();
        float critDamage = stats.getCritDamage() <= 0 ? 1.0f : stats.getCritDamage();
        byte pierceLevel = (byte) Math.max(0, Math.min(10, stats.getPiercing()));
        boolean infinity = stats.isInfinityOverride();

        // Base damage override
        if (baseDamage > 0) {
            arrow.setBaseDamage(baseDamage);
        }

        // Speed override: projectileSpeed is a multiplier, so 1.0 keeps the vanilla launch speed
        Vec3 delta = Objects.requireNonNull(arrow.getDeltaMovement());
        if (delta.lengthSqr() > 0.0001 && speed > 0) {
            Vec3 normalized = Objects.requireNonNullElseGet(delta.normalize(), () -> delta);
            arrow.setDeltaMovement(Objects.requireNonNull(normalized.scale(delta.length() * speed)));
        }

        // Gravity override
        arrow.setNoGravity(gravity <= 0.0001f);
        Vec3 gravityAdjusted = Objects.requireNonNull(arrow.getDeltaMovement());
        arrow.setDeltaMovement(gravityAdjusted.x, gravityAdjusted.y - gravity, gravityAdjusted.z);

        // Spread override: apply a small random offset
        if (spread > 0) {
            var rand = shooter.getRandom();
            double ox = (rand.nextDouble() - 0.5) * spread * 0.02;
            double oy = (rand.nextDouble() - 0.5) * spread * 0.02;
            double oz = (rand.nextDouble() - 0.5) * spread * 0.02;
            Vec3 current = Objects.requireNonNull(arrow.getDeltaMovement());
            arrow.setDeltaMovement(Objects.requireNonNull(current.add(ox, oy, oz)));
        }

        // Crit chance
        if (critChance > 0 && shooter.getRandom().nextFloat() < critChance) {
            arrow.setBaseDamage(arrow.getBaseDamage() * critDamage);
            arrow.setCritArrow(true);
        }

        // Piercing (uses reflection; method is private)
        applyPierce(arrow, pierceLevel);

        // Infinity override: do not drop arrows for pickup
        if (infinity) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }

        // Multishot (best-effort fan spread)
        if (stats.getMultishotCount() > 1) {
            spawnExtraProjectiles(arrow, shooter, stats.getMultishotCount() - 1, stats.getProjectileSpread(), pierceLevel, critChance, critDamage, infinity);
        }
    }

    private static void applyPierce(AbstractArrow arrow, byte pierceLevel) {
        if (pierceLevel <= 0) return;
        Method method = getCachedSetPierceMethod();
        if (method == null) return;
        try {
            method.invoke(arrow, pierceLevel);
        } catch (Exception e) {
            if (!pierceReflectionErrorLogged) {
                pierceReflectionErrorLogged = true;
                DevMod.LOGGER.debug("[RangedProjectileHooks] Failed to apply pierce via reflection", e);
            }
        }
    }

    /**
     * Lazily initializes and caches the setPierceLevel method.
     * Thread-safe via double-checked locking with volatile.
     */
    @javax.annotation.Nullable
    private static Method getCachedSetPierceMethod() {
        if (!pierceLookupAttempted) {
            synchronized (RangedProjectileHooks.class) {
                if (!pierceLookupAttempted) {
                    try {
                        Method m = AbstractArrow.class.getDeclaredMethod("setPierceLevel", byte.class);
                        m.setAccessible(true);
                        SET_PIERCE = m;
                    } catch (NoSuchMethodException e) {
                        DevMod.LOGGER.warn("[RangedProjectileHooks] Failed to cache setPierceLevel method: {}", e.getMessage());
                    } finally {
                        pierceLookupAttempted = true;
                    }
                }
            }
        }
        return SET_PIERCE;
    }

    private static void spawnExtraProjectiles(AbstractArrow baseArrow, LivingEntity shooter, int extraCount, float spread, byte pierceLevel, float critChance, float critDamage, boolean infinity) {
        Level level = Objects.requireNonNull(baseArrow.level());
        Vec3 baseVel = Objects.requireNonNull(baseArrow.getDeltaMovement());
        if (baseVel.lengthSqr() < 1e-6) return;
        double angleStep = Math.toRadians(Math.max(5.0, spread * 5.0)); // widen fan with spread

        for (int i = 0; i < extraCount; i++) {
            AbstractArrow extra = (AbstractArrow) baseArrow.getType().create(level);
            if (extra == null) break;
            extra.moveTo(baseArrow.getX(), baseArrow.getY(), baseArrow.getZ(), baseArrow.getYRot(), baseArrow.getXRot());
            extra.setOwner(shooter);
            extra.setBaseDamage(baseArrow.getBaseDamage());
            extra.pickup = infinity ? AbstractArrow.Pickup.CREATIVE_ONLY : baseArrow.pickup;
            extra.setNoGravity(baseArrow.isNoGravity());
            extra.setCritArrow(baseArrow.isCritArrow());
            applyPierce(extra, pierceLevel);

            double angle = ((i % 2 == 0 ? 1 : -1) * (i / 2 + 1)) * angleStep;
            Vec3 rotated = rotateYaw(baseVel, angle);
            extra.setDeltaMovement(Objects.requireNonNull(rotated));

            if (critChance > 0 && shooter.getRandom().nextFloat() < critChance) {
                extra.setBaseDamage(extra.getBaseDamage() * critDamage);
                extra.setCritArrow(true);
            }

            level.addFreshEntity(extra);
        }
    }

    private static Vec3 rotateYaw(Vec3 vec, double angleRad) {
        Vec3 safeVec = Objects.requireNonNull(vec);
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double x = safeVec.x * cos - safeVec.z * sin;
        double z = safeVec.x * sin + safeVec.z * cos;
        return new Vec3(x, safeVec.y, z);
    }
}
