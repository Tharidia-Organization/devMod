package com.devmod.client.ui.editor.modules;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.stats.UsableStats;

public class UsableModuleCore {

    static final String NBT_KEY = "UsableModStats";
    static final double EPSILON = 1e-4;

    // Stats state
    UsableStats stats = new UsableStats();
    UsableStats originalStats = new UsableStats();
    String sourcePrefix = "";
    SourceBadge.Source dataSource = SourceBadge.Source.VANILLA;

    public UsableModuleCore() {}

    // ═══════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load stats from item NBT/components.
     */
    public void loadStatsFromItem(ItemStack item) {
        CompoundTag statsTag = null;
        try {
            var usableComponent = com.devmod.components.UsableComponents.usableStatsComponent();
            if (usableComponent != null) {
                statsTag = item.get(usableComponent);
            }
        } catch (Exception e) {
            DevMod.LOGGER.debug("[Editor][Usable] Failed to read usable stats component", e);
        }

        CompoundTag customTag;
        try {
            customTag = item.getOrDefault(
                Objects.requireNonNull(DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(CustomData.EMPTY)
            ).copyTag();
        } catch (Exception e) {
            DevMod.LOGGER.debug("[Editor][Usable] Failed to read custom data component", e);
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
            } catch (Exception e) {
                DevMod.LOGGER.debug("[Editor][Usable] Failed to read usable stats from custom data", e);
                statsTag = new CompoundTag();
            }
        }

        if (statsTag != null && !statsTag.isEmpty()) {
            sourcePrefix = "[DEV] ";
            dataSource = SourceBadge.Source.DEV;
            DevMod.LOGGER.info("[Editor][Usable] Loaded stats from component tag (size={})", statsTag.size());
            stats = UsableStats.fromTag(statsTag);
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
                target.setUseDuration(vanillaUseDuration);
            }

            // Check if item is throwable by type
            var itemType = item.getItem();
            if (itemType instanceof net.minecraft.world.item.SnowballItem ||
                itemType instanceof net.minecraft.world.item.EggItem ||
                itemType instanceof net.minecraft.world.item.EnderpearlItem ||
                itemType instanceof net.minecraft.world.item.ThrowablePotionItem) {
                target.setThrowable(true);
                target.setProjectileSpeed(1.5f);
                target.setProjectileGravity(0.03f);
            }

            // Note: CONSUMABLE and USE_COOLDOWN components may not be available in all versions
            // For now, use duration is obtained via getUseDuration() above

            DevMod.LOGGER.info("[Editor][Usable] Vanilla defaults -> useDur={} cooldown={} throwable={}",
                target.getUseDuration(), target.getCooldownDuration(), target.isThrowable());
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
        if (stats.getUseDuration() != originalStats.getUseDuration()) return true;
        if (stats.getCooldownDuration() != originalStats.getCooldownDuration()) return true;
        if (!Objects.equals(stats.getUseAnimation(), originalStats.getUseAnimation())) return true;
        if (stats.isThrowable() != originalStats.isThrowable()) return true;
        if (Math.abs(stats.getProjectileSpeed() - originalStats.getProjectileSpeed()) > EPSILON) return true;
        if (Math.abs(stats.getProjectileGravity() - originalStats.getProjectileGravity()) > EPSILON) return true;
        if (Math.abs(stats.getProjectileInaccuracy() - originalStats.getProjectileInaccuracy()) > EPSILON) return true;
        if (stats.getProjectileDamage() != originalStats.getProjectileDamage()) return true;
        if (stats.isConsumeOnUse() != originalStats.isConsumeOnUse()) return true;
        if (!Objects.equals(stats.getRemainderItem(), originalStats.getRemainderItem())) return true;
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
