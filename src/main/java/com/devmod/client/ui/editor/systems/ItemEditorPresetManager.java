package com.devmod.client.ui.editor.systems;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.ItemEditorDataManager;
import com.devmod.config.ArmorConfigManager;
import com.devmod.config.WeaponConfigManager;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.WeaponStats;

public class ItemEditorPresetManager implements PresetManager {

    public static final ItemEditorPresetManager INSTANCE = new ItemEditorPresetManager();
    private @Nullable BiConsumer<ItemStack, WeaponStats> weaponApplier;
    private @Nullable BiConsumer<ItemStack, ArmorStats> armorApplier;

    @Override
    public boolean applyPreset(Preset preset, ItemStack item, int slotIndex) {
        if (preset == null || item == null || item.isEmpty()) return false;

        // Support our DataPreset wrapper
        if (preset instanceof DataPreset dp) {
            ItemEditorDataManager.PresetData data = dp.getData();
            return applyDataPreset(data, item);
        }

        // If a caller passed the raw PresetData by mistake, try to handle it
        if (preset instanceof PresetDataProxy proxy) {
            return applyDataPreset(proxy.data, item);
        }

        // Unknown preset type
        return false;
    }

    private boolean applyDataPreset(ItemEditorDataManager.PresetData data, ItemStack item) {
        if (data == null || item == null) return false;

        List<Float> stats = data.statValues;
        if (stats == null) return false;

        // Weapon presets export 15 floats, armor presets export 10 floats (per editor logic)
        if (stats.size() >= 15) {
            WeaponStats w = new WeaponStats();
            w.setHeadMult(getStat(stats, 0, w.getHeadMult()));
            w.setBodyMult(getStat(stats, 1, w.getBodyMult()));
            w.setArmsMult(getStat(stats, 2, w.getArmsMult()));
            w.setLegsMult(getStat(stats, 3, w.getLegsMult()));
            w.setAttackDamage(getStat(stats, 4, w.getAttackDamage()));
            w.setAttackSpeed(getStat(stats, 5, w.getAttackSpeed()));
            w.setAttackReach(getStat(stats, 6, w.getAttackReach()));
            w.setAttackKnockback(getStat(stats, 7, w.getAttackKnockback()));
            w.setArmorPenetration(getStat(stats, 8, w.getArmorPenetration()));
            w.setBaseDamageBonus(getStat(stats, 9, w.getBaseDamageBonus()));
            w.setCritChance(getStat(stats, 10, w.getCritChance()));
            w.setCritDamage(getStat(stats, 11, w.getCritDamage()));
            w.setLifesteal(getStat(stats, 12, w.getLifesteal()));
            w.setFireDamageBonus(getStat(stats, 13, w.getFireDamageBonus()));
            w.setMagicDamageBonus(getStat(stats, 14, w.getMagicDamageBonus()));

            // Persist to the item (specific NBT)
            weaponApplier().accept(item, w);
            return true;
        }

        if (stats.size() >= 10) {
            ArmorStats a = new ArmorStats();
            a.setPhysicalReduction(getStat(stats, 0, a.getPhysicalReduction()));
            a.setFireReduction(getStat(stats, 1, a.getFireReduction()));
            a.setMagicReduction(getStat(stats, 2, a.getMagicReduction()));
            a.setExplosionReduction(getStat(stats, 3, a.getExplosionReduction()));
            a.setProjectileReduction(getStat(stats, 4, a.getProjectileReduction()));
            a.setArmorBonus(getStat(stats, 5, a.getArmorBonus()));
            a.setToughnessBonus(getStat(stats, 6, a.getToughnessBonus()));
            a.setKnockbackResistance(getStat(stats, 7, a.getKnockbackResistance()));
            a.setThornsPercent(getStat(stats, 8, a.getThornsPercent()));
            a.setThornsReflect(getStat(stats, 9, a.isThornsReflect() ? 1f : 0f) > 0.5f);

            armorApplier().accept(item, a);
            return true;
        }

        return false;
    }

    private float getStat(List<Float> stats, int idx, float fallback) {
        if (stats == null || idx < 0 || idx >= stats.size()) return fallback;
        Float v = stats.get(idx);
        return v == null ? fallback : v;
    }

    /**
     * A tiny helper to support accidental passing of raw data objects as Preset.
     * This class is package-private and only used as a defensive measure.
     */
    static final class PresetDataProxy implements Preset {
        final ItemEditorDataManager.PresetData data;
        PresetDataProxy(ItemEditorDataManager.PresetData data) { this.data = data; }
        @Override public java.util.function.Predicate<ItemStack> scope() { return s -> true; }
    }

    // Package-private hooks for tests
    void setWeaponApplier(BiConsumer<ItemStack, WeaponStats> applier) {
        weaponApplier = Objects.requireNonNull(applier);
    }

    void setArmorApplier(BiConsumer<ItemStack, ArmorStats> applier) {
        armorApplier = Objects.requireNonNull(applier);
    }

    void resetAppliers() {
        weaponApplier = null;
        armorApplier = null;
    }

    private BiConsumer<ItemStack, WeaponStats> weaponApplier() {
        if (weaponApplier == null) {
            weaponApplier = WeaponConfigManager::setSpecificStats;
        }
        return weaponApplier;
    }

    private BiConsumer<ItemStack, ArmorStats> armorApplier() {
        if (armorApplier == null) {
            armorApplier = ArmorConfigManager::setSpecificStats;
        }
        return armorApplier;
    }
}
