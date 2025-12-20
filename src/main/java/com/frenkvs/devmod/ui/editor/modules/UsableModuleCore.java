package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.UsableStats;
import com.frenkvs.devmod.ui.editor.components.SourceBadge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;

/**
 * Core stats management for UsableModule.
 * Handles loading, saving, and state management.
 */
public class UsableModuleCore {

    static final String NBT_KEY = "UsableModStats";
    static final double EPSILON = 1e-4;

    // Stats state
    UsableStats stats = new UsableStats();
    UsableStats originalStats = new UsableStats();
    String sourcePrefix = "";
    SourceBadge.Source dataSource = SourceBadge.Source.VANILLA;

    // Reference to parent module
    @SuppressWarnings("unused")
    private final UsableModule module;

    public UsableModuleCore(UsableModule module) {
        this.module = module;
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load stats from item NBT/components.
     */
    public void loadStatsFromItem(ItemStack item) {
        CompoundTag statsTag = null;
        try {
            var usableComponent = com.frenkvs.devmod.UsableComponents.usableStatsComponent();
            if (usableComponent != null) {
                statsTag = item.get(usableComponent);
            }
        } catch (Exception ignored) { }

        CompoundTag customTag;
        try {
            customTag = item.getOrDefault(
                Objects.requireNonNull(DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(CustomData.EMPTY)
            ).copyTag();
        } catch (Exception ignored) {
            customTag = new CompoundTag();
        }
        if (customTag == null) {
            customTag = new CompoundTag();
        }

        DevMod.LOGGER.info("[Editor][Usable] Item={} | hasComponent={} | hasCustomData={}",
            item.getItem().toString(),
            statsTag != null && !statsTag.isEmpty(),
            customTag != null && !customTag.isEmpty());

        if (statsTag == null || statsTag.isEmpty()) {
            try {
                if (customTag.contains(NBT_KEY)) {
                    statsTag = customTag.getCompound(NBT_KEY);
                }
            } catch (Exception ignored) {
                statsTag = new CompoundTag();
            }
        }

        if (statsTag != null && !statsTag.isEmpty()) {
            sourcePrefix = "[DEV] ";
            dataSource = SourceBadge.Source.DEV;
            DevMod.LOGGER.info("[Editor][Usable] Loaded stats from component tag (size={})", statsTag.size());
            stats = UsableStats.load(statsTag);
        } else {
            boolean hasCustomData = customTag != null && !customTag.isEmpty();
            sourcePrefix = hasCustomData ? "[NBT] " : "[VANILLA] ";
            dataSource = hasCustomData ? SourceBadge.Source.NBT : SourceBadge.Source.VANILLA;
            DevMod.LOGGER.info("[Editor][Usable] No custom stats found; applying vanilla defaults.");
            stats = new UsableStats();
            applyVanillaDefaults(item, stats);
        }

        originalStats = stats.copy();
    }

    /**
     * Populate stats from vanilla item properties.
     */
    void applyVanillaDefaults(ItemStack item, UsableStats target) {
        if (target == null || item == null) return;

        try {
            // Get vanilla use duration
            var player = Minecraft.getInstance().player;
            int vanillaUseDuration = player != null ? item.getItem().getUseDuration(item, player) : 0;
            if (vanillaUseDuration > 0) {
                target.useDuration = vanillaUseDuration;
            }

            // Check if item is throwable by type
            var itemType = item.getItem();
            if (itemType instanceof net.minecraft.world.item.SnowballItem ||
                itemType instanceof net.minecraft.world.item.EggItem ||
                itemType instanceof net.minecraft.world.item.EnderpearlItem ||
                itemType instanceof net.minecraft.world.item.ThrowablePotionItem) {
                target.isThrowable = true;
                target.projectileSpeed = 1.5f;
                target.projectileGravity = 0.03f;
            }

            // Note: CONSUMABLE and USE_COOLDOWN components may not be available in all versions
            // For now, use duration is obtained via getUseDuration() above

            DevMod.LOGGER.info("[Editor][Usable] Vanilla defaults -> useDur={} cooldown={} throwable={}",
                target.useDuration, target.cooldownDuration, target.isThrowable);
        } catch (Exception e) {
            DevMod.LOGGER.warn("[Editor][Usable] Failed to apply vanilla defaults", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public UsableStats getStats() {
        return stats;
    }

    public void setStats(UsableStats newStats) {
        this.stats = newStats != null ? newStats : new UsableStats();
    }

    public UsableStats getOriginalStats() {
        return originalStats;
    }

    public void setOriginalStats(UsableStats newOriginal) {
        this.originalStats = newOriginal != null ? newOriginal : new UsableStats();
    }

    public SourceBadge.Source getDataSource() {
        return dataSource;
    }

    public String getSourcePrefix() {
        return sourcePrefix;
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPARISON
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if current stats differ from original.
     */
    public boolean hasModifications() {
        if (stats.useDuration != originalStats.useDuration) return true;
        if (stats.cooldownDuration != originalStats.cooldownDuration) return true;
        if (!Objects.equals(stats.useAnimation, originalStats.useAnimation)) return true;
        if (stats.isThrowable != originalStats.isThrowable) return true;
        if (Math.abs(stats.projectileSpeed - originalStats.projectileSpeed) > EPSILON) return true;
        if (Math.abs(stats.projectileGravity - originalStats.projectileGravity) > EPSILON) return true;
        if (Math.abs(stats.projectileInaccuracy - originalStats.projectileInaccuracy) > EPSILON) return true;
        if (stats.projectileDamage != originalStats.projectileDamage) return true;
        if (stats.consumeOnUse != originalStats.consumeOnUse) return true;
        if (!Objects.equals(stats.remainderItem, originalStats.remainderItem)) return true;
        return false;
    }

    /**
     * Get CustomData tag from item (utility method for module).
     */
    public CompoundTag getCustomDataTag(ItemStack item) {
        try {
            return item.getOrDefault(
                Objects.requireNonNull(DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(CustomData.EMPTY)
            ).copyTag();
        } catch (Exception e) {
            return new CompoundTag();
        }
    }
}
