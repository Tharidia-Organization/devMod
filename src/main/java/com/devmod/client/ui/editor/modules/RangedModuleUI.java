package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.ammo.AmmoSystem;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.RangedWeaponModule;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorTextField;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.debug.DebugInfoSection;
import com.devmod.client.ui.editor.debug.ItemDebugInfo;
import com.devmod.client.ui.editor.debug.ValueComparison;
import com.devmod.client.ui.editor.sections.InputSectionAdapter;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;

/**
 * UI component management for RangedModule.
 * Handles component creation, section building, and UI updates.
 * Follows the same pattern as ArmorModuleUI.
 */
public class RangedModuleUI {

    private static final int TOGGLE_SECTION_HEIGHT = EditorDimensions.TOGGLE_HEIGHT + DesignTokens.Spacing.SM;

    private final RangedModule module;
    private final RangedModuleCore core;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Mechanics Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable private EditorSlider drawSpeedSlider;
    @Nullable private EditorSlider chargeTimeSlider;
    @Nullable private EditorSlider accuracySlider;
    @Nullable private EditorSlider rangeSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Projectile Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable private EditorSlider projectileSpeedSlider;
    @Nullable private EditorSlider projectileGravitySlider;
    @Nullable private EditorSlider projectileSpreadSlider;
    @Nullable private EditorSlider baseDamageSlider;
    @Nullable private EditorToggle multishotToggle;
    @Nullable private EditorSlider multishotCountSlider;
    @Nullable private EditorSlider piercingSlider;
    @Nullable private EditorToggle infinityToggle;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Damage Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable private EditorSlider critChanceSlider;
    @Nullable private EditorSlider critDamageSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Trident Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable private EditorSlider loyaltySpeedSlider;
    @Nullable private EditorSlider riptideDistanceSlider;
    @Nullable private EditorToggle riptideRequiresWaterToggle;
    @Nullable private EditorToggle channelingToggle;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Ammo Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable private EditorTextField ammoFilterInput;
    private List<String> ammoMatches = new ArrayList<>();
    private final List<AmmoSystem.AmmoSuggestion> ammoSuggestions = AmmoSystem.getSuggestedTags();

    public RangedModuleUI(RangedModule module, RangedModuleCore core) {
        this.module = Objects.requireNonNull(module, "module cannot be null");
        this.core = Objects.requireNonNull(core, "core cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT CREATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create all UI components based on variant and source.
     */
    public void createAllComponents(RangedModule.RangedVariant variant) {
        SourceBadge.Source source = core.determineSource();
        createMechanicsComponents(variant, source);
        createProjectileComponents(source);
        createDamageComponents(source);
        createMetadataComponents();
        if (variant == RangedModule.RangedVariant.TRIDENT) {
            createTridentComponents(source);
        }
    }

    private void createMechanicsComponents(RangedModule.RangedVariant variant, SourceBadge.Source source) {
        RangedWeaponModule.RangedStats stats = core.getStats();

        String drawLabel = variant == RangedModule.RangedVariant.CROSSBOW ? "Reload Speed" : "Draw Speed";
        String accuracyLabel = variant == RangedModule.RangedVariant.CROSSBOW ? "Stability" : "Accuracy";
        String rangeLabel = switch (variant) {
            case CROSSBOW -> "Bolt Range";
            case TRIDENT -> "Throw Range";
            default -> "Range";
        };

        drawSpeedSlider = new EditorSlider("drawSpeed", drawLabel, 0.2f, 3.0f, stats.drawSpeed)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Multiplier for bow draw/crossbow reload speed. 1.0 = normal, 2.0 = twice as fast, 0.5 = twice as slow.")
            .onChange(v -> { stats.drawSpeed = v; module.markDirty(drawLabel); });

        if (variant == RangedModule.RangedVariant.CROSSBOW) {
            String chargeLabel = "Charge Time";
            chargeTimeSlider = new EditorSlider("chargeTime", chargeLabel, 0.2f, 3.0f, stats.chargeTime)
                .step(0.05f)
                .format("%.2f")
                .suffix("x")
                .trackColor(DesignTokens.SliderColors.SPEED)
                .showInput(true)
                .source(source)
                .info("Time to fully charge the crossbow. 1.0 = normal, higher = slower charge.")
                .onChange(v -> { stats.chargeTime = v; module.markDirty(chargeLabel); });
        }

        accuracySlider = new EditorSlider("accuracy", accuracyLabel, 0.5f, 1.25f, stats.accuracy)
            .step(0.01f)
            .format("%.2f")
            .suffix("x")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Shot precision. 1.0 = perfect accuracy, <1.0 = more spread, >1.0 = tighter grouping.")
            .onChange(v -> { stats.accuracy = v; module.markDirty(accuracyLabel); });

        rangeSlider = new EditorSlider("range", rangeLabel, 0.5f, 3.5f, stats.range)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Maximum effective range multiplier. Affects how far projectiles travel before losing damage.")
            .onChange(v -> { stats.range = v; module.markDirty(rangeLabel); });
    }

    private void createProjectileComponents(SourceBadge.Source source) {
        RangedWeaponModule.RangedStats stats = core.getStats();
        RangedModule.RangedVariant variant = module.getVariant();

        String projSpeedLabel = variant == RangedModule.RangedVariant.CROSSBOW ? "Bolt Speed" : "Arrow Speed";
        projectileSpeedSlider = new EditorSlider("projectileSpeed", projSpeedLabel, 0.5f, 5.0f, stats.projectileSpeed)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("How fast the projectile travels. Higher = flatter trajectory, more damage. 1.0 = vanilla arrow speed.")
            .onChange(v -> { stats.projectileSpeed = v; module.markDirty(projSpeedLabel); });

        projectileGravitySlider = new EditorSlider("projectileGravity", "Gravity", 0f, 0.2f, stats.projectileGravity)
            .step(0.005f)
            .format("%.3f")
            .suffix(" g/t")
            .trackColor(DesignTokens.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Downward acceleration per tick. 0.05 = normal arrow, 0 = no drop (laser-like), 0.2 = very heavy.")
            .onChange(v -> { stats.projectileGravity = v; module.markDirty("Gravity"); });

        projectileSpreadSlider = new EditorSlider("projectileSpread", "Spread", 0f, 3f, stats.projectileSpread)
            .step(0.05f)
            .format("%.2f")
            .suffix("°")
            .trackColor(DesignTokens.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Random deviation added to each shot. 0 = perfectly straight, 3 = very inaccurate spread.")
            .onChange(v -> { stats.projectileSpread = v; module.markDirty("Spread"); });

        baseDamageSlider = new EditorSlider("baseDamage", "Base Damage", 0f, 20f, stats.baseDamage)
            .step(0.1f)
            .format("%.1f")
            .suffix(" HP")
            .trackColor(DesignTokens.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Base damage before Power enchant and velocity bonuses. Vanilla arrow = 2.0, Power V adds +12.5.")
            .onChange(v -> { stats.baseDamage = v; module.markDirty("Base damage"); });

        multishotToggle = new EditorToggle("multishot", "Enable Multishot", stats.multishot)
            .source(source)
            .tooltip("Fire multiple projectiles per shot (uses one ammo)")
            .onChange(val -> { stats.multishot = val; module.markDirty("Multishot"); });

        piercingSlider = new EditorSlider("piercing", "Piercing Level", 0f, 5f, stats.piercing)
            .step(1f)
            .format("%.0f")
            .suffix(" targets")
            .trackColor(DesignTokens.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Number of entities the projectile can pass through. Like Piercing enchant. 0 = stops on first hit.")
            .onChange(v -> { stats.piercing = Math.round(v); module.markDirty("Piercing"); });

        multishotCountSlider = new EditorSlider("multishotCount", "Projectile Count", 1f, 5f, stats.multishotCount)
            .step(1f)
            .format("%.0f")
            .suffix(" arrows")
            .trackColor(DesignTokens.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Number of projectiles fired when Multishot is enabled. Vanilla Multishot = 3. All consume only 1 ammo.")
            .onChange(v -> { stats.multishotCount = Math.round(v); module.markDirty("Projectile count"); });

        infinityToggle = new EditorToggle("infinity", "Infinity Override", stats.infinityOverride)
            .source(source)
            .tooltip("Force infinite ammo even without Infinity enchant. First arrow in inventory is used as template.")
            .onChange(v -> { stats.infinityOverride = v; module.markDirty("Infinity override"); });
    }

    private void createDamageComponents(SourceBadge.Source source) {
        RangedWeaponModule.RangedStats stats = core.getStats();

        critChanceSlider = new EditorSlider("critChance", "Crit Chance", 0f, 100f, stats.critChance * 100f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(DesignTokens.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Chance for projectile to deal critical damage. Vanilla arrows crit at full velocity. This adds bonus crit chance.")
            .onChange(v -> { stats.critChance = v / 100f; module.markDirty("Crit chance"); });

        critDamageSlider = new EditorSlider("critDamage", "Crit Damage", 1.0f, 3.5f, stats.critDamage)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(DesignTokens.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Damage multiplier on critical hit. 1.5x = 50% bonus damage. Stacks with Power enchant.")
            .onChange(v -> { stats.critDamage = v; module.markDirty("Crit damage"); });
    }

    private void createMetadataComponents() {
        RangedWeaponModule.RangedStats stats = core.getStats();

        EditorTextField ammoFilter = new EditorTextField("ammoFilter", "Ammo Filter")
            .placeholder("e.g. minecraft:arrow or #minecraft:arrows")
            .validator(this::isAmmoFilterValid)
            .onChange(val -> {
                stats.ammoFilter = val == null ? "" : val.trim();
                ammoMatches = computeAmmoMatches(stats.ammoFilter);
                module.markDirty("Ammo filter");
            });
        ammoFilter.setValue(stats.ammoFilter == null ? "" : stats.ammoFilter);
        ammoFilterInput = ammoFilter;
    }

    private void createTridentComponents(SourceBadge.Source source) {
        RangedWeaponModule.RangedStats stats = core.getStats();

        loyaltySpeedSlider = new EditorSlider("loyaltySpeed", "Loyalty Speed", 0f, 5f, stats.loyaltySpeed)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("How fast the trident returns to the player. Higher = faster return. 0 = no return (requires pickup).")
            .onChange(v -> { stats.loyaltySpeed = v; module.markDirty("Loyalty speed"); });

        riptideDistanceSlider = new EditorSlider("riptideDistance", "Riptide Distance", 0f, 64f, stats.riptideDistance)
            .step(1f)
            .format("%.0f")
            .suffix(" blocks")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Maximum distance the player can travel when using Riptide in water/rain. 0 = disabled.")
            .onChange(v -> { stats.riptideDistance = v; module.markDirty("Riptide distance"); });

        riptideRequiresWaterToggle = new EditorToggle("riptideWater", "Riptide Requires Water", stats.riptideRequiresWater)
            .source(source)
            .tooltip("If enabled, Riptide only works in water or rain. If disabled, works anywhere.")
            .onChange(v -> { stats.riptideRequiresWater = v; module.markDirty("Riptide requires water"); });

        channelingToggle = new EditorToggle("channeling", "Channeling Allowed", stats.channeling)
            .source(source)
            .tooltip("When enabled, trident summons lightning on hit during thunderstorms.")
            .onChange(v -> { stats.channeling = v; module.markDirty("Channeling"); });
    }

    // ═══════════════════════════════════════════════════════════════
    // UPDATE COMPONENTS FROM STATS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update all UI components from current stats.
     */
    public void updateComponentsFromStats() {
        RangedWeaponModule.RangedStats stats = core.getStats();
        RangedWeaponModule.SourcedStats sourced = core.getSourcedStats();

        // Apply source labels
        if (sourced != null) {
            applySourceLabel(drawSpeedSlider, sourced.drawSpeed());
            applySourceLabel(chargeTimeSlider, sourced.chargeTime());
            applySourceLabel(accuracySlider, sourced.accuracy());
            applySourceLabel(rangeSlider, sourced.range());
            applySourceLabel(projectileSpeedSlider, sourced.projectileSpeed());
            applySourceLabel(projectileGravitySlider, sourced.projectileGravity());
            applySourceLabel(projectileSpreadSlider, sourced.projectileSpread());
            applySourceLabel(baseDamageSlider, sourced.baseDamage());
            applySourceLabel(piercingSlider, sourced.piercing());
            applySourceLabel(multishotCountSlider, sourced.multishotCount());
            applySourceLabel(multishotToggle, sourced.multishot());
            applySourceLabel(infinityToggle, sourced.infinityOverride());
            applySourceLabel(critChanceSlider, sourced.critChance());
            applySourceLabel(critDamageSlider, sourced.critDamage());
            applySourceLabel(loyaltySpeedSlider, sourced.loyaltySpeed());
            applySourceLabel(riptideDistanceSlider, sourced.riptideDistance());
            applySourceLabel(riptideRequiresWaterToggle, sourced.riptideRequiresWater());
            applySourceLabel(channelingToggle, sourced.channeling());
        }

        // Update slider values
        if (drawSpeedSlider != null) drawSpeedSlider.setValue(stats.drawSpeed);
        if (chargeTimeSlider != null) chargeTimeSlider.setValue(stats.chargeTime);
        if (accuracySlider != null) accuracySlider.setValue(stats.accuracy);
        if (rangeSlider != null) rangeSlider.setValue(stats.range);
        if (projectileSpeedSlider != null) projectileSpeedSlider.setValue(stats.projectileSpeed);
        if (projectileGravitySlider != null) projectileGravitySlider.setValue(stats.projectileGravity);
        if (projectileSpreadSlider != null) projectileSpreadSlider.setValue(stats.projectileSpread);
        if (baseDamageSlider != null) baseDamageSlider.setValue(stats.baseDamage);
        if (critChanceSlider != null) critChanceSlider.setValue(stats.critChance * 100f);
        if (critDamageSlider != null) critDamageSlider.setValue(stats.critDamage);
        if (piercingSlider != null) piercingSlider.setValue(stats.piercing);
        if (multishotCountSlider != null) multishotCountSlider.setValue(stats.multishotCount);
        if (multishotToggle != null) multishotToggle.setValue(stats.multishot);
        if (infinityToggle != null) infinityToggle.setValue(stats.infinityOverride);
        if (ammoFilterInput != null) ammoFilterInput.setValue(stats.ammoFilter == null ? "" : stats.ammoFilter);
        if (loyaltySpeedSlider != null) loyaltySpeedSlider.setValue(stats.loyaltySpeed);
        if (riptideDistanceSlider != null) riptideDistanceSlider.setValue(stats.riptideDistance);
        if (riptideRequiresWaterToggle != null) riptideRequiresWaterToggle.setValue(stats.riptideRequiresWater);
        if (channelingToggle != null) channelingToggle.setValue(stats.channeling);

        // Update ammo matches
        ammoMatches = computeAmmoMatches(stats.ammoFilter);
    }

    private void applySourceLabel(@Nullable EditorSlider slider, @Nullable RangedWeaponModule.SourcedValue<?> sourced) {
        if (slider == null || sourced == null) return;
        slider.setLabel(slider.getLabel().replaceFirst("^\\[[^]]+\\] ", ""));
    }

    private void applySourceLabel(@Nullable EditorToggle toggle, @Nullable RangedWeaponModule.SourcedValue<?> sourced) {
        if (toggle == null || sourced == null) return;
        toggle.setLabel(toggle.getLabel().replaceFirst("^\\[[^]]+\\] ", ""));
    }

    // ═══════════════════════════════════════════════════════════════
    // SECTION BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getMechanicsSections() {
        var list = new ArrayList<EditorSection>();
        list.add(new SliderSectionAdapter(Objects.requireNonNull(drawSpeedSlider, "drawSpeedSlider")));
        if (chargeTimeSlider != null) list.add(new SliderSectionAdapter(chargeTimeSlider));
        list.add(new SliderSectionAdapter(Objects.requireNonNull(accuracySlider, "accuracySlider")));
        list.add(new SliderSectionAdapter(Objects.requireNonNull(rangeSlider, "rangeSlider")));
        return list;
    }

    public List<EditorSection> getProjectileSections() {
        return List.of(
            new SliderSectionAdapter(Objects.requireNonNull(projectileSpeedSlider, "projectileSpeedSlider")),
            new SliderSectionAdapter(Objects.requireNonNull(projectileGravitySlider, "projectileGravitySlider")),
            new SliderSectionAdapter(Objects.requireNonNull(projectileSpreadSlider, "projectileSpreadSlider")),
            new SliderSectionAdapter(Objects.requireNonNull(baseDamageSlider, "baseDamageSlider")),
            new ToggleSectionAdapter(Objects.requireNonNull(multishotToggle, "multishotToggle"), TOGGLE_SECTION_HEIGHT),
            new SliderSectionAdapter(Objects.requireNonNull(multishotCountSlider, "multishotCountSlider")),
            new SliderSectionAdapter(Objects.requireNonNull(piercingSlider, "piercingSlider")),
            new ToggleSectionAdapter(Objects.requireNonNull(infinityToggle, "infinityToggle"), TOGGLE_SECTION_HEIGHT)
        );
    }

    public List<EditorSection> getDamageSections() {
        return List.of(
            new SliderSectionAdapter(Objects.requireNonNull(critChanceSlider, "critChanceSlider")),
            new SliderSectionAdapter(Objects.requireNonNull(critDamageSlider, "critDamageSlider"))
        );
    }

    public List<EditorSection> getTridentSections() {
        return List.of(
            new SliderSectionAdapter(Objects.requireNonNull(loyaltySpeedSlider, "loyaltySpeedSlider")),
            new SliderSectionAdapter(Objects.requireNonNull(riptideDistanceSlider, "riptideDistanceSlider")),
            new ToggleSectionAdapter(Objects.requireNonNull(riptideRequiresWaterToggle, "riptideRequiresWaterToggle"), TOGGLE_SECTION_HEIGHT),
            new ToggleSectionAdapter(Objects.requireNonNull(channelingToggle, "channelingToggle"), TOGGLE_SECTION_HEIGHT)
        );
    }

    public List<EditorSection> getAmmoSections() {
        return List.of(
            new InputSectionAdapter(requireAmmoFilterInput(), true),
            new AmmoListSection()
        );
    }

    private EditorTextField requireAmmoFilterInput() {
        return Objects.requireNonNull(ammoFilterInput, "ammoFilterInput");
    }

    // ═══════════════════════════════════════════════════════════════
    // AMMO MATCHING
    // ═══════════════════════════════════════════════════════════════

    private List<String> computeAmmoMatches(String filter) {
        AmmoSystem.FilterState state = resolveAmmoFilterState(filter);
        if (state != AmmoSystem.FilterState.VALID_ITEM && state != AmmoSystem.FilterState.VALID_TAG) {
            return new ArrayList<>();
        }
        List<ItemStack> matches = AmmoSystem.getItemsFromTagString(filter);
        List<String> names = new ArrayList<>();
        int count = 0;
        for (ItemStack stack : matches) {
            if (count >= 16) break;
            try {
                names.add(stack.getHoverName().getString());
            } catch (Exception e) {
                names.add(stack.getItem().getDescriptionId());
            }
            count++;
        }
        return names;
    }

    private boolean isAmmoFilterValid(@Nullable String filter) {
        AmmoSystem.FilterState state = resolveAmmoFilterState(filter);
        return state == AmmoSystem.FilterState.BLANK
            || state == AmmoSystem.FilterState.VALID_ITEM
            || state == AmmoSystem.FilterState.VALID_TAG;
    }

    private AmmoSystem.FilterState resolveAmmoFilterState(@Nullable String filter) {
        return AmmoSystem.resolveFilterState(filter);
    }

    // ═══════════════════════════════════════════════════════════════
    // DEBUG SECTIONS
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getDebugSections(ItemStack item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item.getItem(), "stack item"));
        String registry = key == null ? "<unknown>" : key.toString();
        var customType = Objects.requireNonNull(DataComponents.CUSTOM_DATA, "custom data component");
        CustomData customData = item.get(customType);
        CompoundTag customTag = customData == null ? new CompoundTag() : customData.copyTag();
        ItemDebugInfo info = new ItemDebugInfo(
            registry,
            item.getCount(),
            item.getDamageValue(),
            item.getMaxDamage(),
            customTag == null ? 0 : customTag.size(),
            customData != null
        );

        RangedWeaponModule.RangedStats stats = core.getStats();
        RangedWeaponModule.RangedStats originalStats = core.getOriginalStats();

        List<ValueComparison> comparisons = List.of(
            comparison("drawSpeed", stats.drawSpeed, originalStats.drawSpeed),
            comparison("accuracy", stats.accuracy, originalStats.accuracy),
            comparison("range", stats.range, originalStats.range),
            comparison("projectileSpeed", stats.projectileSpeed, originalStats.projectileSpeed),
            comparison("piercing", stats.piercing, originalStats.piercing),
            comparison("critChance", stats.critChance, originalStats.critChance),
            comparison("critDamage", stats.critDamage, originalStats.critDamage),
            comparison("multishot", stats.multishot ? 1f : 0f, originalStats.multishot ? 1f : 0f)
        );

        List<String> history = module.getHistoryEntries();
        List<String> nbtLines = DebugInfoSection.formatNbtLines(core.getCustomDataTag(item), 16);
        return List.of(new DebugInfoSection(info, comparisons, history, nbtLines, () -> copyDebugInfo(item)));
    }

    private ValueComparison comparison(String name, float current, float original) {
        boolean modified = Math.abs(current - original) > 1e-3;
        return new ValueComparison(name, original, current, Double.NaN, modified, false);
    }

    private void copyDebugInfo(ItemStack item) {
        try {
            Minecraft mc = Minecraft.getInstance();
            RangedWeaponModule.RangedStats stats = core.getStats();
            StringBuilder sb = new StringBuilder();
            String name = item.getHoverName() == null ? "<unknown>" : item.getHoverName().getString();
            sb.append("Ranged Debug for ").append(name).append("\n");
            sb.append(String.format(Locale.US, "drawSpeed=%.2f accuracy=%.2f range=%.2f projSpeed=%.2f piercing=%d multishot=%s crit=%.2f/%.2f",
                stats.drawSpeed, stats.accuracy, stats.range, stats.projectileSpeed, stats.piercing,
                stats.multishot ? "on" : "off", stats.critChance, stats.critDamage));
            if (stats.ammoFilter != null && !stats.ammoFilter.isBlank()) {
                sb.append("\nammoFilter=").append(stats.ammoFilter);
            }
            mc.keyboardHandler.setClipboard(Objects.requireNonNull(sb.toString(), "debug text"));
            module.logEvent("Copied debug info");
        } catch (Exception ignored) {
            // Best-effort copy
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AMMO LIST SECTION (Inner Class)
    // ═══════════════════════════════════════════════════════════════

    private class AmmoListSection implements EditorSection.CustomSection {
        private static final int HEADER_BLOCK_HEIGHT = 14;
        private static final int ENTRY_LINE_HEIGHT = 12;
        private static final int MATCHES_BOTTOM_PADDING = 8;
        private static final int SUGGESTION_ROW_HEIGHT = 14;
        private static final int SUGGESTION_BLOCK_PADDING = 20;
        private static final int SUGGESTION_TITLE_GAP = 6;
        private static final int SUGGESTION_TITLE_LINE_HEIGHT = 14;
        private static final int BUTTON_TEXT_OFFSET_X = 6;
        private static final int BUTTON_TEXT_OFFSET_Y = 3;
        private static final int BUTTON_GAP = 4;
        private static final int MAX_MATCHES_SHOWN = 8;
        private final List<ResponsiveLayout.Rect> suggestionRects = new ArrayList<>();

        @Override
        public String getId() { return "ammoList"; }

        @Override
        public String getLabel() { return ""; }

        @Override
        public int getHeight() {
            AmmoSystem.FilterState state = resolveAmmoFilterState(ammoFilterInput == null ? "" : ammoFilterInput.getValue());
            int lines = Math.max(1, ammoMatches.size());
            int suggLines = ammoSuggestions.size();
            int hintLines = state == AmmoSystem.FilterState.BLANK ? 0 : 1;
            return HEADER_BLOCK_HEIGHT + hintLines * ENTRY_LINE_HEIGHT + lines * ENTRY_LINE_HEIGHT + MATCHES_BOTTOM_PADDING
                + suggLines * SUGGESTION_ROW_HEIGHT + SUGGESTION_BLOCK_PADDING;
        }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font);
            int x = bounds.x() + DesignTokens.Spacing.SM;
            int y = bounds.y() + DesignTokens.Spacing.SM;
            String filterValue = ammoFilterInput == null ? "" : ammoFilterInput.getValue();
            AmmoSystem.FilterState state = resolveAmmoFilterState(filterValue);

            String header;
            int headerColor;
            String hint = "";
            int hintColor = DesignTokens.Text.MUTED();

            switch (state) {
                case BLANK -> {
                    header = "No ammo filter set";
                    headerColor = DesignTokens.Text.MUTED();
                }
                case INVALID_FORMAT -> {
                    header = "Invalid filter format";
                    headerColor = DesignTokens.Semantic.ERROR;
                    hint = "Use namespace:item or #namespace:tag";
                }
                case MISSING_ITEM -> {
                    header = "Item not found";
                    headerColor = DesignTokens.Semantic.WARNING;
                    hint = "Item id: " + filterValue.trim();
                }
                case EMPTY_TAG -> {
                    header = "Tag has no entries";
                    headerColor = DesignTokens.Semantic.WARNING;
                    hint = "Tag: " + filterValue.trim();
                }
                case VALID_TAG -> {
                    header = ammoMatches.isEmpty()
                        ? "No items match this filter"
                        : "Matching ammo (" + ammoMatches.size() + "):";
                    headerColor = DesignTokens.Text.SECONDARY();
                    hint = "Tag: " + filterValue.trim();
                }
                case VALID_ITEM -> {
                    header = ammoMatches.isEmpty()
                        ? "No items match this filter"
                        : "Matching ammo (" + ammoMatches.size() + "):";
                    headerColor = DesignTokens.Text.SECONDARY();
                    hint = "Item id: " + filterValue.trim();
                }
                default -> {
                    header = "No items match this filter";
                    headerColor = DesignTokens.Text.SECONDARY();
                }
            }

            graphics.drawString(font, header, x, y, headerColor, false);
            y += ENTRY_LINE_HEIGHT;
            if (!hint.isEmpty()) {
                graphics.drawString(font, hint, x, y, hintColor, false);
                y += ENTRY_LINE_HEIGHT;
            }
            int shown = 0;
            for (String entry : ammoMatches) {
                if (shown >= MAX_MATCHES_SHOWN) break;
                graphics.drawString(font, "- " + entry, x, y, DesignTokens.Text.PRIMARY(), false);
                y += ENTRY_LINE_HEIGHT;
                shown++;
            }

            // Suggestions + clear
            y += SUGGESTION_TITLE_GAP;
            graphics.drawString(font, "Suggestions:", x, y, DesignTokens.Text.SECONDARY(), false);
            y += SUGGESTION_TITLE_LINE_HEIGHT;
            suggestionRects.clear();
            int btnHeight = SUGGESTION_ROW_HEIGHT;
            int btnWidth = bounds.width() - DesignTokens.Spacing.SM * 2;

            // Clear button
            int clearX = x;
            graphics.fill(clearX, y, clearX + btnWidth, y + btnHeight, DesignTokens.Background.INPUT());
            graphics.drawString(font, "Clear filter", clearX + BUTTON_TEXT_OFFSET_X,
                y + BUTTON_TEXT_OFFSET_Y, DesignTokens.Text.PRIMARY(), false);
            suggestionRects.add(new ResponsiveLayout.Rect(clearX, y, btnWidth, btnHeight));
            y += btnHeight + BUTTON_GAP;

            for (AmmoSystem.AmmoSuggestion sugg : ammoSuggestions) {
                graphics.fill(clearX, y, clearX + btnWidth, y + btnHeight, DesignTokens.Background.PANEL());
                String label = formatSuggestionLabel(sugg);
                graphics.drawString(font, label, clearX + BUTTON_TEXT_OFFSET_X,
                    y + BUTTON_TEXT_OFFSET_Y, DesignTokens.Text.PRIMARY(), false);
                suggestionRects.add(new ResponsiveLayout.Rect(clearX, y, btnWidth, btnHeight));
                y += btnHeight + BUTTON_GAP;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            if (suggestionRects.isEmpty()) return false;

            RangedWeaponModule.RangedStats stats = core.getStats();

            // First rect is clear
            if (suggestionRects.get(0).contains((int) mouseX, (int) mouseY)) {
                stats.ammoFilter = "";
                requireAmmoFilterInput().setValue("");
                ammoMatches = computeAmmoMatches(stats.ammoFilter);
                module.markDirty("Ammo filter");
                return true;
            }

            for (int i = 1; i < suggestionRects.size(); i++) {
                if (suggestionRects.get(i).contains((int) mouseX, (int) mouseY)) {
                    AmmoSystem.AmmoSuggestion s = ammoSuggestions.get(i - 1);
                    stats.ammoFilter = s.value();
                    requireAmmoFilterInput().setValue(s.value());
                    ammoMatches = computeAmmoMatches(stats.ammoFilter);
                    module.markDirty("Ammo filter");
                    return true;
                }
            }
            return false;
        }

        private String formatSuggestionLabel(AmmoSystem.AmmoSuggestion suggestion) {
            String label = suggestion.displayName();
            if (label == null || label.isBlank()) {
                label = suggestion.value();
            }
            return suggestion.isTag() ? "Tag: " + label : label;
        }
    }
}
