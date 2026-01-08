package com.devmod.config.handler.impl;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.devmod.components.UsableComponents;
import com.devmod.config.component.BooleanComponent;
import com.devmod.config.component.FloatComponent;
import com.devmod.config.component.IntComponent;
import com.devmod.config.handler.AbstractConfigHandler;
import com.devmod.config.handler.DecomposedConfig;
import com.devmod.config.handler.IConfigComponent;
import com.devmod.config.handler.IDecomposedConfig;
import com.devmod.stats.UsableStats;

/**
 * Config handler for usable item stats.
 * Provides decompose/recompose functionality with typed components.
 */
public class UsableConfigHandler extends AbstractConfigHandler<UsableStats> {

    /** Singleton instance */
    public static final UsableConfigHandler INSTANCE = new UsableConfigHandler();

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT DEFINITIONS
    // ═══════════════════════════════════════════════════════════════

    // Use properties
    public static final IntComponent USE_DURATION = new IntComponent(
        "useDuration", "Use Duration", "Use", 0, 0, 1000);
    public static final IntComponent COOLDOWN_DURATION = new IntComponent(
        "cooldownDuration", "Cooldown Duration", "Use", 0, 0, 6000);

    // Throwable properties
    public static final BooleanComponent IS_THROWABLE = new BooleanComponent(
        "isThrowable", "Is Throwable", "Throwable", false);
    public static final FloatComponent PROJECTILE_SPEED = new FloatComponent(
        "projectileSpeed", "Projectile Speed", "Throwable", 1.5f, 0.1f, 10.0f);
    public static final FloatComponent PROJECTILE_GRAVITY = new FloatComponent(
        "projectileGravity", "Projectile Gravity", "Throwable", 0.03f, 0.0f, 0.5f);
    public static final FloatComponent PROJECTILE_INACCURACY = new FloatComponent(
        "projectileInaccuracy", "Projectile Inaccuracy", "Throwable", 1.0f, 0.0f, 10.0f);
    public static final IntComponent PROJECTILE_DAMAGE = new IntComponent(
        "projectileDamage", "Projectile Damage", "Throwable", 0, 0, 100);

    // Consumption properties
    public static final BooleanComponent CONSUME_ON_USE = new BooleanComponent(
        "consumeOnUse", "Consume On Use", "Consumption", true);

    private static final Set<IConfigComponent<?>> ALL_COMPONENTS;

    static {
        ALL_COMPONENTS = new LinkedHashSet<>();
        // Use
        ALL_COMPONENTS.add(USE_DURATION);
        ALL_COMPONENTS.add(COOLDOWN_DURATION);
        // Throwable
        ALL_COMPONENTS.add(IS_THROWABLE);
        ALL_COMPONENTS.add(PROJECTILE_SPEED);
        ALL_COMPONENTS.add(PROJECTILE_GRAVITY);
        ALL_COMPONENTS.add(PROJECTILE_INACCURACY);
        ALL_COMPONENTS.add(PROJECTILE_DAMAGE);
        // Consumption
        ALL_COMPONENTS.add(CONSUME_ON_USE);
    }

    public UsableConfigHandler() {
        super("usable_configs.json", "UsableModStats");
    }

    // ═══════════════════════════════════════════════════════════════
    // ABSTRACT METHOD IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Class<UsableStats> getStatsClass() {
        return UsableStats.class;
    }

    @Override
    @Nullable
    protected DataComponentType<CompoundTag> getComponentType() {
        return UsableComponents.usableStatsComponent();
    }

    @Override
    protected UsableStats fromTag(CompoundTag tag) {
        return UsableStats.fromTag(tag);
    }

    @Override
    protected Type getMapType() {
        return new TypeToken<Map<String, UsableStats>>(){}.getType();
    }

    @Override
    public UsableStats createDefault() {
        return new UsableStats();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        // Usable items are generic - any item can potentially be made usable
        return stack != null && !stack.isEmpty();
    }

    @Override
    public Set<IConfigComponent<?>> getComponents() {
        return ALL_COMPONENTS;
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public UsableStats validateAndClamp(UsableStats stats) {
        if (stats == null) return createDefault();

        // Use component clamp for all values
        stats.setUseDuration(USE_DURATION.clamp(stats.getUseDuration()));
        stats.setCooldownDuration(COOLDOWN_DURATION.clamp(stats.getCooldownDuration()));
        stats.setProjectileSpeed(PROJECTILE_SPEED.clamp(stats.getProjectileSpeed()));
        stats.setProjectileGravity(PROJECTILE_GRAVITY.clamp(stats.getProjectileGravity()));
        stats.setProjectileInaccuracy(PROJECTILE_INACCURACY.clamp(stats.getProjectileInaccuracy()));
        stats.setProjectileDamage(PROJECTILE_DAMAGE.clamp(stats.getProjectileDamage()));

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // DECOMPOSE/RECOMPOSE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void populateDecomposed(DecomposedConfig config, UsableStats stats) {
        // Use
        config.set(USE_DURATION, stats.getUseDuration());
        config.set(COOLDOWN_DURATION, stats.getCooldownDuration());
        // Throwable
        config.set(IS_THROWABLE, stats.isThrowable());
        config.set(PROJECTILE_SPEED, stats.getProjectileSpeed());
        config.set(PROJECTILE_GRAVITY, stats.getProjectileGravity());
        config.set(PROJECTILE_INACCURACY, stats.getProjectileInaccuracy());
        config.set(PROJECTILE_DAMAGE, stats.getProjectileDamage());
        // Consumption
        config.set(CONSUME_ON_USE, stats.isConsumeOnUse());
    }

    @Override
    protected void applyFromDecomposed(IDecomposedConfig config, UsableStats stats) {
        // Use
        config.get(USE_DURATION).ifPresent(stats::setUseDuration);
        config.get(COOLDOWN_DURATION).ifPresent(stats::setCooldownDuration);
        // Throwable
        config.get(IS_THROWABLE).ifPresent(stats::setThrowable);
        config.get(PROJECTILE_SPEED).ifPresent(stats::setProjectileSpeed);
        config.get(PROJECTILE_GRAVITY).ifPresent(stats::setProjectileGravity);
        config.get(PROJECTILE_INACCURACY).ifPresent(stats::setProjectileInaccuracy);
        config.get(PROJECTILE_DAMAGE).ifPresent(stats::setProjectileDamage);
        // Consumption
        config.get(CONSUME_ON_USE).ifPresent(stats::setConsumeOnUse);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC COMPATIBILITY API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Clear specific stats from an item stack (static version).
     */
    public static void clearItemSpecificStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        INSTANCE.clearSpecificStats(stack);
    }
}
