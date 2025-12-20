package com.frenkvs.devmod;

import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * State management for MobConfigScreen.
 * Handles values, presets, animations, and save/load operations.
 */
public class MobConfigScreenState {

    // Slider max values
    public static final double MAX_HEALTH = 500.0;
    public static final double MAX_ARMOR = 30.0;
    public static final double MAX_SPEED = 1.0;
    public static final double MAX_DAMAGE = 100.0;
    public static final double MAX_ATTACK_RANGE = 16.0;
    public static final double MAX_FOLLOW_RANGE = 128.0;
    public static final double MAX_KNOCKBACK_RES = 1.0;

    // Persist mode preference
    private static boolean lastGlobalMode = false;

    // Presets
    public static final String[] PRESET_NAMES = {"Tank", "Glass Cannon", "Speedy", "Balanced", "Boss", "Custom"};
    public static final String[] PRESET_DESCRIPTIONS = {
        "High HP & armor, low speed/damage",
        "2.5x damage, 0.5x health, no armor",
        "2x speed, extended follow range",
        "Reset to original mob stats",
        "3x HP, 2x damage, full knockback res",
        "Your custom configuration"
    };
    public static final int[][] PRESET_COLORS = {
        {UIConstants.Accent.CYAN(), UIConstants.Accent.BLUE()},
        {UIConstants.Accent.RED(), UIConstants.Accent.ORANGE()},
        {UIConstants.Accent.GREEN(), UIConstants.Accent.YELLOW()},
        {0xFFAAAAAA, 0xFF888888},
        {UIConstants.Accent.PURPLE(), UIConstants.Accent.GOLD()},
        {UIConstants.Border.DEFAULT(), UIConstants.Border.LIGHT()}
    };

    private final Mob mob;

    // Current values (editable)
    public double health, armor, damage, followRange, attackRange;
    public double speed, knockbackResist;

    // Original values (for comparison)
    public final double origHealth, origArmor, origDamage, origFollowRange, origAttackRange;
    public final double origSpeed, origKnockbackResist;

    // Mode
    public boolean isGlobalMode = lastGlobalMode;

    // UI State
    public int selectedTab = 0;
    public int activeSlider = -1;
    public int selectedPreset = 5;
    public int hoveredPreset = -1;
    public int hoveredUserPreset = -1;

    // Mob preview
    public float rotationX = 0;
    public float rotationY = 0;
    public float targetRotationY = -30;
    public boolean isDraggingPreview = false;
    public int dragStartX, dragStartY;
    public float dragStartRotX, dragStartRotY;
    public float previewZoom = 1.0f;

    // Animation
    public float animationProgress = 0;
    public long lastFrameTime = 0;
    public float[] sliderAnimations = new float[7];

    // Direct numeric input state
    public int editingSlider = -1;
    public StringBuilder inputBuffer = new StringBuilder();
    public long inputCursorBlink = 0;

    // Dialogs
    public boolean showSavePresetDialog = false;
    public StringBuilder presetNameInput = new StringBuilder();
    public String deleteConfirmPreset = null;
    public boolean showingConfirmDialog = false;
    public boolean confirmedDiscard = false;

    // Blur control
    public int originalBlurValue = 0;

    public MobConfigScreenState(Mob mob) {
        this.mob = mob;

        // Store original values
        this.origHealth = getVal(Objects.requireNonNull(Attributes.MAX_HEALTH));
        this.origArmor = getVal(Objects.requireNonNull(Attributes.ARMOR));
        this.origDamage = getVal(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        this.origFollowRange = getVal(Objects.requireNonNull(Attributes.FOLLOW_RANGE));
        this.origSpeed = getVal(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
        this.origKnockbackResist = getVal(Objects.requireNonNull(Attributes.KNOCKBACK_RESISTANCE));

        double reach = getVal(Objects.requireNonNull(Attributes.ENTITY_INTERACTION_RANGE));
        if (reach <= 0.1) {
            reach = mob.getBbWidth() * 2.0 + 1.0;
        }
        this.origAttackRange = reach;

        // Initialize current values
        this.health = origHealth;
        this.armor = origArmor;
        this.damage = origDamage;
        this.followRange = origFollowRange;
        this.attackRange = origAttackRange;
        this.speed = origSpeed;
        this.knockbackResist = origKnockbackResist;

        lastFrameTime = System.currentTimeMillis();
    }

    private double getVal(@Nonnull Holder<Attribute> attr) {
        var attribute = mob.getAttribute(attr);
        if (attribute != null) {
            return mob.getAttributeValue(attr);
        }
        return 0;
    }

    public Mob getMob() {
        return mob;
    }

    // ═══════════════════════════════════════════════════════════════
    // MODE TOGGLE
    // ═══════════════════════════════════════════════════════════════

    public void toggleMode() {
        isGlobalMode = !isGlobalMode;
        lastGlobalMode = isGlobalMode;
    }

    // ═══════════════════════════════════════════════════════════════
    // ANIMATIONS
    // ═══════════════════════════════════════════════════════════════

    public void updateAnimations() {
        long now = System.currentTimeMillis();
        float delta = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;

        // Opening animation
        if (animationProgress < 1) {
            animationProgress = Math.min(1, animationProgress + delta * 3);
        }

        // Decay slider pulse animations
        for (int i = 0; i < sliderAnimations.length; i++) {
            if (sliderAnimations[i] > 0) {
                sliderAnimations[i] = Math.max(0, sliderAnimations[i] - delta * 2);
            }
        }

        // Auto-rotate preview when not dragging
        if (!isDraggingPreview) {
            targetRotationY += delta * 20;
            if (targetRotationY > 180) targetRotationY -= 360;
        }

        // Smooth rotation
        rotationY = Mth.lerp(0.1f, rotationY, targetRotationY);
    }

    public void triggerSliderAnimation(int sliderIndex) {
        if (sliderIndex >= 0 && sliderIndex < sliderAnimations.length) {
            sliderAnimations[sliderIndex] = 1.0f;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SLIDER VALUES
    // ═══════════════════════════════════════════════════════════════

    public void updateSliderValue(int mouseX, int slidersX, int sliderWidth) {
        float percent = (mouseX - slidersX) / (float) sliderWidth;
        percent = Mth.clamp(percent, 0, 1);

        int localSlider = activeSlider % 10;

        switch (selectedTab) {
            case 0 -> { // Stats
                switch (localSlider) {
                    case 0 -> health = percent * MAX_HEALTH;
                    case 1 -> armor = percent * MAX_ARMOR;
                    case 2 -> speed = percent * MAX_SPEED;
                }
            }
            case 1 -> { // Combat
                switch (localSlider) {
                    case 0 -> damage = percent * MAX_DAMAGE;
                    case 1 -> attackRange = percent * MAX_ATTACK_RANGE;
                    case 2 -> knockbackResist = percent * MAX_KNOCKBACK_RES;
                }
            }
            case 2 -> { // AI
                if (localSlider == 0 || localSlider == 1) {
                    followRange = percent * MAX_FOLLOW_RANGE;
                }
            }
        }

        triggerSliderAnimation(localSlider);
        selectedPreset = 5;
    }

    public double getSliderValue(int sliderId) {
        int tab = sliderId / 10;
        int local = sliderId % 10;
        return switch (tab) {
            case 0 -> switch (local) {
                case 0 -> health;
                case 1 -> armor;
                case 2 -> speed * 100;
                default -> 0;
            };
            case 1 -> switch (local) {
                case 0 -> damage;
                case 1 -> attackRange;
                case 2 -> knockbackResist * 100;
                default -> 0;
            };
            case 2 -> switch (local) {
                case 0 -> followRange;
                default -> 0;
            };
            default -> 0;
        };
    }

    public void setSliderValue(int sliderId, double value) {
        int tab = sliderId / 10;
        int local = sliderId % 10;
        switch (tab) {
            case 0 -> {
                switch (local) {
                    case 0 -> health = Mth.clamp(value, 0, MAX_HEALTH);
                    case 1 -> armor = Mth.clamp(value, 0, MAX_ARMOR);
                    case 2 -> speed = Mth.clamp(value / 100, 0, MAX_SPEED);
                }
            }
            case 1 -> {
                switch (local) {
                    case 0 -> damage = Mth.clamp(value, 0, MAX_DAMAGE);
                    case 1 -> attackRange = Mth.clamp(value, 0, MAX_ATTACK_RANGE);
                    case 2 -> knockbackResist = Mth.clamp(value / 100, 0, MAX_KNOCKBACK_RES);
                }
            }
            case 2 -> {
                if (local == 0) followRange = Mth.clamp(value, 0, MAX_FOLLOW_RANGE);
            }
        }
        selectedPreset = 5;
    }

    public void commitNumericInput() {
        if (editingSlider < 0) return;

        String input = inputBuffer.toString().trim();
        if (!input.isEmpty()) {
            try {
                double value = Double.parseDouble(input);
                setSliderValue(editingSlider, value);
            } catch (NumberFormatException ignored) {
                // Invalid input - keep old value
            }
        }
        editingSlider = -1;
        inputBuffer.setLength(0);
    }

    // ═══════════════════════════════════════════════════════════════
    // PRESETS
    // ═══════════════════════════════════════════════════════════════

    public void applyPreset(int preset) {
        selectedPreset = preset;

        switch (preset) {
            case 0 -> { // Tank
                health = Math.max(origHealth, 80);
                armor = 20;
                damage = origDamage * 0.7;
                speed = origSpeed * 0.6;
                knockbackResist = 0.8;
            }
            case 1 -> { // Glass Cannon
                health = origHealth * 0.5;
                armor = 0;
                damage = origDamage * 2.5;
                speed = origSpeed * 1.3;
                knockbackResist = 0;
            }
            case 2 -> { // Speedy
                health = origHealth;
                armor = origArmor;
                damage = origDamage;
                speed = origSpeed * 2;
                followRange = 48;
                knockbackResist = 0;
            }
            case 3 -> { // Balanced
                health = origHealth;
                armor = origArmor;
                damage = origDamage;
                speed = origSpeed;
                followRange = origFollowRange;
                knockbackResist = origKnockbackResist;
            }
            case 4 -> { // Boss
                health = Math.max(origHealth * 3, 200);
                armor = 15;
                damage = origDamage * 2;
                speed = origSpeed * 0.8;
                knockbackResist = 1.0;
                followRange = 48;
                attackRange = origAttackRange * 1.5;
            }
            case 5 -> { /* Custom - do nothing */ }
        }

        playPresetSound(preset);
    }

    public void playPresetSound(int preset) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        switch (preset) {
            case 0 -> mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.ANVIL_PLACE), 0.6f, 0.5f)));
            case 1 -> mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.PLAYER_ATTACK_CRIT), 1.0f, 1.2f)));
            case 2 -> mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.ENDER_DRAGON_FLAP), 0.5f, 1.5f)));
            case 3 -> mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 1.0f, 1.0f)));
            case 4 -> mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.TOTEM_USE), 0.4f, 0.7f)));
            case 5 -> mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 0.8f, 1.2f)));
        }
    }

    public void applyUserPreset(String name) {
        MobPresetManager.MobPreset preset = MobPresetManager.getPreset(name);
        if (preset == null) return;

        health = preset.health();
        armor = preset.armor();
        damage = preset.damage();
        speed = preset.speed();
        followRange = preset.followRange();
        attackRange = preset.attackRange();
        knockbackResist = preset.knockbackResist();
        selectedPreset = 5;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 1.0f, 1.2f)));
        }
    }

    public void saveCurrentAsPreset(String name) {
        if (name == null || name.trim().isEmpty()) return;

        MobPresetManager.MobPreset preset = MobPresetManager.MobPreset.create(
            name.trim(),
            health, armor, damage, speed, followRange, attackRange, knockbackResist
        );

        if (MobPresetManager.savePreset(name.trim(), preset)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                    Objects.requireNonNull(SoundEvents.EXPERIENCE_ORB_PICKUP), 0.5f, 1.0f)));
            }
        }
    }

    public void deleteUserPreset(String name) {
        MobPresetManager.deletePreset(name);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.ITEM_PICKUP), 0.5f, 0.8f)));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESET & SAVE
    // ═══════════════════════════════════════════════════════════════

    public void resetToOriginal() {
        health = origHealth;
        armor = origArmor;
        damage = origDamage;
        followRange = origFollowRange;
        attackRange = origAttackRange;
        speed = origSpeed;
        knockbackResist = origKnockbackResist;
        selectedPreset = 5;
    }

    public void save() {
        PacketDistributor.sendToServer(new UpdateMobStatsPayload(
            isGlobalMode, mob.getId(), followRange, damage, health, armor, attackRange, speed, knockbackResist));

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(
                com.frenkvs.devmod.util.I18n.translate("devmod.mobconfig.applying"),
                true
            );
        }
    }

    public boolean hasUnsavedChanges() {
        final double EPSILON = 0.01;
        return Math.abs(health - origHealth) > EPSILON ||
               Math.abs(armor - origArmor) > EPSILON ||
               Math.abs(damage - origDamage) > EPSILON ||
               Math.abs(followRange - origFollowRange) > EPSILON ||
               Math.abs(attackRange - origAttackRange) > EPSILON ||
               Math.abs(speed - origSpeed) > EPSILON ||
               Math.abs(knockbackResist - origKnockbackResist) > EPSILON;
    }
}
