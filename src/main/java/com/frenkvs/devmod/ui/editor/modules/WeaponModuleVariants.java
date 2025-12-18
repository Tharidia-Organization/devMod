package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.components.SourceBadge;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.nbt.CompoundTag;

/**
 * Manages Mace and Trident variant-specific state for WeaponModule.
 */
public class WeaponModuleVariants {

    // ═══════════════════════════════════════════════════════════════
    // MACE VARIANT STATE
    // ═══════════════════════════════════════════════════════════════

    float smashBonus = 3.0f;
    float smashCap = 150.0f;
    float smashKnockback = 1.0f;
    float smashAoeDamage = 50.0f;
    boolean smashNegateFall = false;

    // Mace originals
    private float originalSmashBonus = smashBonus;
    private float originalSmashCap = smashCap;
    private float originalSmashKnockback = smashKnockback;
    private float originalSmashAoeDamage = smashAoeDamage;
    private boolean originalSmashNegateFall = smashNegateFall;

    // ═══════════════════════════════════════════════════════════════
    // TRIDENT VARIANT STATE
    // ═══════════════════════════════════════════════════════════════

    float throwDamage = 8.0f;
    float throwSpeed = 2.5f;
    float loyaltySpeed = 1.5f;
    float riptideDistance = 12.0f;
    float riptideDamage = 6.0f;
    boolean riptideRequiresWater = true;

    // Trident originals
    private float originalThrowDamage = throwDamage;
    private float originalThrowSpeed = throwSpeed;
    private float originalLoyaltySpeed = loyaltySpeed;
    private float originalRiptideDistance = riptideDistance;
    private float originalRiptideDamage = riptideDamage;
    private boolean originalRiptideRequiresWater = riptideRequiresWater;

    // ═══════════════════════════════════════════════════════════════
    // MACE UI COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    EditorSlider smashBonusSlider;
    EditorSlider smashCapSlider;
    EditorSlider smashKnockbackSlider;
    EditorSlider smashAoeDamageSlider;
    EditorToggle smashFallNegationToggle;

    // ═══════════════════════════════════════════════════════════════
    // TRIDENT UI COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    EditorSlider throwDamageSlider;
    EditorSlider throwSpeedSlider;
    EditorSlider loyaltySpeedSlider;
    EditorSlider riptideDistanceSlider;
    EditorSlider riptideDamageSlider;
    EditorToggle riptideRequiresWaterToggle;

    private final WeaponModule module;

    public WeaponModuleVariants(WeaponModule module) {
        this.module = module;
    }

    // ═══════════════════════════════════════════════════════════════
    // CREATE VARIANT COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    public void createVariantComponents(WeaponModule.WeaponVariant variant, SourceBadge.Source dataSource) {
        if (variant == WeaponModule.WeaponVariant.MACE) {
            createMaceComponents(dataSource);
        } else if (variant == WeaponModule.WeaponVariant.TRIDENT) {
            createTridentComponents(dataSource);
        }
    }

    private void createMaceComponents(SourceBadge.Source dataSource) {
        smashBonusSlider = new EditorSlider("smashBonus", "Fall Damage Bonus", 0f, 20f, 3.0f)
            .step(0.5f)
            .format("%.1f")
            .suffix(" per block")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Extra damage per block fallen. Mace default = 3.0. Total = Base + (Fall Height * Bonus).")
            .onChange(v -> { smashBonus = v; module.markDirty("Smash fall bonus"); });

        smashCapSlider = new EditorSlider("smashCap", "Max Bonus Damage", 0f, 300f, 150f)
            .step(5f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Maximum bonus damage from falling. Prevents infinite scaling.")
            .onChange(v -> { smashCap = v; module.markDirty("Smash cap"); });

        smashKnockbackSlider = new EditorSlider("smashKb", "Smash Knockback", 0f, 5f, 1.0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(dataSource)
            .info("Knockback strength on smash attack. Higher = enemies fly further.")
            .onChange(v -> { smashKnockback = v; module.markDirty("Smash knockback"); });

        smashAoeDamageSlider = new EditorSlider("smashAoe", "Smash AOE Damage", 0f, 200f, 50f)
            .step(5f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Percentage of damage dealt to nearby enemies in AoE. 50% = half damage to secondary targets.")
            .onChange(v -> { smashAoeDamage = v; module.markDirty("Smash AOE damage"); });

        smashFallNegationToggle = new EditorToggle("smashNegate", "Fall Damage Negation", false)
            .source(dataSource)
            .tooltip("Negate fall damage when landing a smash attack")
            .onChange(v -> { smashNegateFall = v; module.markDirty("Fall damage negation"); });
    }

    private void createTridentComponents(SourceBadge.Source dataSource) {
        throwDamageSlider = new EditorSlider("throwDmg", "Throw Damage", 0f, 30f, 8f)
            .step(0.5f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Damage dealt when trident hits as thrown projectile. Vanilla = 8.0.")
            .onChange(v -> { throwDamage = v; module.markDirty("Throw damage"); });

        throwSpeedSlider = new EditorSlider("throwSpeed", "Throw Speed", 0f, 5f, 2.5f)
            .step(0.1f)
            .format("%.2f")
            .suffix(" blocks/tick")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("How fast trident travels when thrown. Higher = longer range, faster flight.")
            .onChange(v -> { throwSpeed = v; module.markDirty("Throw speed"); });

        loyaltySpeedSlider = new EditorSlider("loyaltySpeed", "Return Speed (Loyalty)", 0f, 5f, 1.5f)
            .step(0.1f)
            .format("%.2f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("How fast trident returns with Loyalty enchant. 0 = no return.")
            .onChange(v -> { loyaltySpeed = v; module.markDirty("Return speed"); });

        riptideDistanceSlider = new EditorSlider("riptideDist", "Riptide Distance", 0f, 30f, 12f)
            .step(0.5f)
            .format("%.1f")
            .suffix(" blocks")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Maximum distance player travels when using Riptide in water/rain.")
            .onChange(v -> { riptideDistance = v; module.markDirty("Riptide distance"); });

        riptideDamageSlider = new EditorSlider("riptideDmg", "Riptide Damage", 0f, 20f, 6f)
            .step(0.5f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Damage dealt to entities hit during Riptide dash.")
            .onChange(v -> { riptideDamage = v; module.markDirty("Riptide damage"); });

        riptideRequiresWaterToggle = new EditorToggle("riptideWater", "Requires Water", true)
            .source(dataSource)
            .tooltip("If enabled, Riptide only works in water or rain")
            .onChange(v -> { riptideRequiresWater = v; module.markDirty("Riptide water requirement"); });
    }

    // ═══════════════════════════════════════════════════════════════
    // LOAD / SAVE VARIANT DATA
    // ═══════════════════════════════════════════════════════════════

    public void loadVariantData(CompoundTag customRoot, CompoundTag componentRoot) {
        CompoundTag source = customRoot == null ? new CompoundTag() : customRoot;
        if (source.isEmpty() && componentRoot != null) {
            source = componentRoot;
        }
        if (source.contains("Mace")) {
            CompoundTag mace = source.getCompound("Mace");
            smashBonus = mace.getFloat("smashBonus");
            smashCap = mace.getFloat("smashCap");
            smashKnockback = mace.getFloat("smashKnockback");
            smashAoeDamage = mace.getFloat("smashAoeDamage");
            smashNegateFall = mace.getBoolean("smashNegateFall");
        }
        if (source.contains("Trident")) {
            CompoundTag tri = source.getCompound("Trident");
            throwDamage = tri.getFloat("throwDamage");
            throwSpeed = tri.getFloat("throwSpeed");
            loyaltySpeed = tri.getFloat("loyaltySpeed");
            riptideDistance = tri.getFloat("riptideDistance");
            riptideDamage = tri.getFloat("riptideDamage");
            riptideRequiresWater = tri.getBoolean("riptideRequiresWater");
        }
        updateVariantComponents(module.getVariant());
        snapshotVariantOriginals();
    }

    public void saveVariantData(CompoundTag root, WeaponModule.WeaponVariant variant) {
        if (variant == WeaponModule.WeaponVariant.MACE) {
            CompoundTag mace = new CompoundTag();
            mace.putFloat("smashBonus", smashBonus);
            mace.putFloat("smashCap", smashCap);
            mace.putFloat("smashKnockback", smashKnockback);
            mace.putFloat("smashAoeDamage", smashAoeDamage);
            mace.putBoolean("smashNegateFall", smashNegateFall);
            root.put("Mace", mace);
        } else if (variant == WeaponModule.WeaponVariant.TRIDENT) {
            CompoundTag tri = new CompoundTag();
            tri.putFloat("throwDamage", throwDamage);
            tri.putFloat("throwSpeed", throwSpeed);
            tri.putFloat("loyaltySpeed", loyaltySpeed);
            tri.putFloat("riptideDistance", riptideDistance);
            tri.putFloat("riptideDamage", riptideDamage);
            tri.putBoolean("riptideRequiresWater", riptideRequiresWater);
            root.put("Trident", tri);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UPDATE VARIANT COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    public void updateVariantComponents(WeaponModule.WeaponVariant variant) {
        if (variant == WeaponModule.WeaponVariant.MACE) {
            if (smashBonusSlider != null) smashBonusSlider.setValue(smashBonus);
            if (smashCapSlider != null) smashCapSlider.setValue(smashCap);
            if (smashKnockbackSlider != null) smashKnockbackSlider.setValue(smashKnockback);
            if (smashAoeDamageSlider != null) smashAoeDamageSlider.setValue(smashAoeDamage);
            if (smashFallNegationToggle != null) smashFallNegationToggle.setValue(smashNegateFall);
        }
        if (variant == WeaponModule.WeaponVariant.TRIDENT) {
            if (throwDamageSlider != null) throwDamageSlider.setValue(throwDamage);
            if (throwSpeedSlider != null) throwSpeedSlider.setValue(throwSpeed);
            if (loyaltySpeedSlider != null) loyaltySpeedSlider.setValue(loyaltySpeed);
            if (riptideDistanceSlider != null) riptideDistanceSlider.setValue(riptideDistance);
            if (riptideDamageSlider != null) riptideDamageSlider.setValue(riptideDamage);
            if (riptideRequiresWaterToggle != null) riptideRequiresWaterToggle.setValue(riptideRequiresWater);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SNAPSHOT & RESET
    // ═══════════════════════════════════════════════════════════════

    public void snapshotVariantOriginals() {
        originalSmashBonus = smashBonus;
        originalSmashCap = smashCap;
        originalSmashKnockback = smashKnockback;
        originalSmashAoeDamage = smashAoeDamage;
        originalSmashNegateFall = smashNegateFall;
        originalThrowDamage = throwDamage;
        originalThrowSpeed = throwSpeed;
        originalLoyaltySpeed = loyaltySpeed;
        originalRiptideDistance = riptideDistance;
        originalRiptideDamage = riptideDamage;
        originalRiptideRequiresWater = riptideRequiresWater;
    }

    public void resetToOriginal(WeaponModule.WeaponVariant variant) {
        smashBonus = originalSmashBonus;
        smashCap = originalSmashCap;
        smashKnockback = originalSmashKnockback;
        smashAoeDamage = originalSmashAoeDamage;
        smashNegateFall = originalSmashNegateFall;
        throwDamage = originalThrowDamage;
        throwSpeed = originalThrowSpeed;
        loyaltySpeed = originalLoyaltySpeed;
        riptideDistance = originalRiptideDistance;
        riptideDamage = originalRiptideDamage;
        riptideRequiresWater = originalRiptideRequiresWater;
        updateVariantComponents(variant);
    }

    // ═══════════════════════════════════════════════════════════════
    // VARIANT CHANGED CHECK
    // ═══════════════════════════════════════════════════════════════

    public boolean variantChanged() {
        return variantChanged(module.getVariant());
    }

    public boolean variantChanged(WeaponModule.WeaponVariant variant) {
        return switch (variant) {
            case MACE -> Float.compare(smashBonus, originalSmashBonus) != 0
                || Float.compare(smashCap, originalSmashCap) != 0
                || Float.compare(smashKnockback, originalSmashKnockback) != 0
                || Float.compare(smashAoeDamage, originalSmashAoeDamage) != 0
                || smashNegateFall != originalSmashNegateFall;
            case TRIDENT -> Float.compare(throwDamage, originalThrowDamage) != 0
                || Float.compare(throwSpeed, originalThrowSpeed) != 0
                || Float.compare(loyaltySpeed, originalLoyaltySpeed) != 0
                || Float.compare(riptideDistance, originalRiptideDistance) != 0
                || Float.compare(riptideDamage, originalRiptideDamage) != 0
                || riptideRequiresWater != originalRiptideRequiresWater;
            default -> false;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS FOR UI COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    public EditorSlider getSmashBonusSlider() { return smashBonusSlider; }
    public EditorSlider getSmashCapSlider() { return smashCapSlider; }
    public EditorSlider getSmashKnockbackSlider() { return smashKnockbackSlider; }
    public EditorSlider getSmashAoeDamageSlider() { return smashAoeDamageSlider; }
    public EditorToggle getSmashFallNegationToggle() { return smashFallNegationToggle; }

    public EditorSlider getThrowDamageSlider() { return throwDamageSlider; }
    public EditorSlider getThrowSpeedSlider() { return throwSpeedSlider; }
    public EditorSlider getLoyaltySpeedSlider() { return loyaltySpeedSlider; }
    public EditorSlider getRiptideDistanceSlider() { return riptideDistanceSlider; }
    public EditorSlider getRiptideDamageSlider() { return riptideDamageSlider; }
    public EditorToggle getRiptideRequiresWaterToggle() { return riptideRequiresWaterToggle; }
}
