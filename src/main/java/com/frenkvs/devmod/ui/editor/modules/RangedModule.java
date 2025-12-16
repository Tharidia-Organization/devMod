package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.network.RangedWeaponStatsPayload;
import com.frenkvs.devmod.ui.editor.AbstractEditorModule;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.ModuleTab;
import com.frenkvs.devmod.ui.editor.RangedWeaponModule;
import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorTextField;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.core.EditorDimensions;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.debug.DebugInfoSection;
import com.frenkvs.devmod.ui.editor.debug.ItemDebugInfo;
import com.frenkvs.devmod.ui.editor.debug.ValueComparison;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ArrayList;

/**
 * Editor module for ranged weapons (bow/crossbow).
 * Uses CustomData "RangedStats" via RangedWeaponModule helper.
 */
public class RangedModule extends AbstractEditorModule {

    public enum RangedVariant { BOW, CROSSBOW, TRIDENT, GENERIC }
    private RangedVariant variant = RangedVariant.GENERIC;

    private RangedWeaponModule.RangedStats stats = new RangedWeaponModule.RangedStats();
    private RangedWeaponModule.RangedStats originalStats = new RangedWeaponModule.RangedStats();
    private RangedWeaponModule.SourcedStats sourcedStats;

    // UI components
    private EditorSlider drawSpeedSlider;
    private EditorSlider accuracySlider;
    private EditorSlider rangeSlider;
    private EditorSlider projectileSpeedSlider;
    private EditorSlider chargeTimeSlider;
    private EditorSlider projectileGravitySlider;
    private EditorSlider projectileSpreadSlider;
    private EditorSlider baseDamageSlider;
    private EditorSlider loyaltySpeedSlider;
    private EditorSlider riptideDistanceSlider;
    private EditorToggle riptideRequiresWaterToggle;
    private EditorToggle channelingToggle;
    private EditorSlider critChanceSlider;
    private EditorSlider critDamageSlider;
    private EditorSlider piercingSlider;
    private EditorSlider multishotCountSlider;
    private EditorToggle multishotToggle;
    private EditorToggle infinityToggle;
    private EditorTextField ammoFilterInput;
    private List<String> ammoMatches = new ArrayList<>();
    private final List<Suggestion> ammoSuggestions = List.of(
        new Suggestion("#minecraft:arrows", "Tag: arrows"),
        new Suggestion("minecraft:arrow", "Arrow"),
        new Suggestion("minecraft:spectral_arrow", "Spectral Arrow"),
        new Suggestion("minecraft:tipped_arrow", "Tipped Arrow"),
        new Suggestion("minecraft:trident", "Trident")
    );

    public RangedModule() {
        super("ranged", "Ranged Weapon Editor");
    }

    public RangedModule(RangedVariant variant) {
        super("ranged", "Ranged Weapon Editor");
        this.variant = variant == null ? RangedVariant.GENERIC : variant;
    }

    @Override
    protected void onItemSet() {
        detectVariantFromItem();
        stats = RangedWeaponModule.getStats(item);
        sourcedStats = RangedWeaponModule.getSourcedStats(item);
        originalStats = stats.copy();
        updateComponentsFromStats();
    }

    private void detectVariantFromItem() {
        var itm = item.getItem();
        if (itm instanceof net.minecraft.world.item.BowItem) {
            variant = RangedVariant.BOW;
        } else if (itm instanceof net.minecraft.world.item.CrossbowItem) {
            variant = RangedVariant.CROSSBOW;
        } else if (itm instanceof net.minecraft.world.item.TridentItem) {
            variant = RangedVariant.TRIDENT;
        } else {
            variant = RangedVariant.GENERIC;
        }
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();
        createMechanicsComponents();
        createProjectileComponents();
        createDamageComponents();
        createMetadataComponents();
        if (variant == RangedVariant.TRIDENT) {
            createTridentComponents();
        }

        addTab(ModuleTab.of(getVariantTabId(), getVariantTabLabel(), this::getMechanicsSections));
        addTab(ModuleTab.of("projectile", "Projectile", this::getProjectileSections));
        addTab(ModuleTab.of("damage", "Damage", this::getDamageSections));
        addTab(ModuleTab.of("ammo", "Ammo", this::getAmmoSections));
        if (variant == RangedVariant.TRIDENT) {
            addTab(ModuleTab.of("trident", "Trident", this::getTridentSections));
        }
        addTab(ModuleTab.of("debug", "Debug", this::getDebugSections));
    }

    private void createMechanicsComponents() {
        String drawLabel = variant == RangedVariant.CROSSBOW ? "Reload Speed" : "Draw Speed";
        String accuracyLabel = variant == RangedVariant.CROSSBOW ? "Stability" : "Accuracy";
        String rangeLabel = switch (variant) {
            case CROSSBOW -> "Bolt Range";
            case TRIDENT -> "Throw Range";
            default -> "Range";
        };
        String chargeLabel = "Charge Time";
        drawSpeedSlider = new EditorSlider("drawSpeed", drawLabel, 0.2f, 3.0f, stats.drawSpeed)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .onChange(v -> { stats.drawSpeed = v; markDirty(drawLabel); });
        if (variant == RangedVariant.CROSSBOW) {
            chargeTimeSlider = new EditorSlider("chargeTime", chargeLabel, 0.2f, 3.0f, stats.chargeTime)
                .step(0.05f)
                .format("%.2f")
                .suffix("x")
                .trackColor(UIConstants.SliderColors.SPEED)
                .onChange(v -> { stats.chargeTime = v; markDirty(chargeLabel); });
        }

        accuracySlider = new EditorSlider("accuracy", accuracyLabel, 0.5f, 1.25f, stats.accuracy)
            .step(0.01f)
            .format("%.2f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .onChange(v -> { stats.accuracy = v; markDirty(accuracyLabel); });

        rangeSlider = new EditorSlider("range", rangeLabel, 0.5f, 3.5f, stats.range)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .onChange(v -> { stats.range = v; markDirty(rangeLabel); });
    }

    private void createProjectileComponents() {
        String projSpeedLabel = variant == RangedVariant.CROSSBOW ? "Bolt Speed" : "Arrow Speed";
        projectileSpeedSlider = new EditorSlider("projectileSpeed", projSpeedLabel, 0.5f, 5.0f, stats.projectileSpeed)
            .step(0.05f)
            .format("%.2f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .onChange(v -> { stats.projectileSpeed = v; markDirty(projSpeedLabel); });

        projectileGravitySlider = new EditorSlider("projectileGravity", "Gravity", 0f, 0.2f, stats.projectileGravity)
            .step(0.005f)
            .format("%.3f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .onChange(v -> { stats.projectileGravity = v; markDirty("Gravity"); });

        projectileSpreadSlider = new EditorSlider("projectileSpread", "Spread", 0f, 3f, stats.projectileSpread)
            .step(0.05f)
            .format("%.2f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .onChange(v -> { stats.projectileSpread = v; markDirty("Spread"); });

        baseDamageSlider = new EditorSlider("baseDamage", "Base Damage", 0f, 20f, stats.baseDamage)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.baseDamage = v; markDirty("Base damage"); });

        multishotToggle = new EditorToggle("multishot", "Enable Multishot", stats.multishot)
            .onChange(val -> { stats.multishot = val; markDirty("Multishot"); });

        piercingSlider = new EditorSlider("piercing", "Piercing Level", 0f, 5f, stats.piercing)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.piercing = Math.round(v); markDirty("Piercing"); });

        multishotCountSlider = new EditorSlider("multishotCount", "Projectile Count", 1f, 5f, stats.multishotCount)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.multishotCount = Math.round(v); markDirty("Projectile count"); });
    }

    private void createMetadataComponents() {
        ammoFilterInput = new EditorTextField("ammoFilter", "Ammo Filter")
            .placeholder("e.g. minecraft:arrow")
            .onChange(val -> {
                stats.ammoFilter = val == null ? "" : val.trim();
                markDirty("Ammo filter");
            });
        ammoFilterInput.setValue(stats.ammoFilter == null ? "" : stats.ammoFilter);
        infinityToggle = new EditorToggle("infinity", "Infinity Override", stats.infinityOverride)
            .tooltip("Force infinite ammo even without enchant")
            .onChange(v -> { stats.infinityOverride = v; markDirty("Infinity override"); });
    }

    private void createDamageComponents() {
        critChanceSlider = new EditorSlider("critChance", "Crit Chance", 0f, 1f, stats.critChance)
            .step(0.01f)
            .format("%.2f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.critChance = v; markDirty("Crit chance"); });

        critDamageSlider = new EditorSlider("critDamage", "Crit Damage", 1.0f, 3.5f, stats.critDamage)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.critDamage = v; markDirty("Crit damage"); });
    }

    private void createTridentComponents() {
        loyaltySpeedSlider = new EditorSlider("loyaltySpeed", "Loyalty Speed", 0f, 5f, stats.loyaltySpeed)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .onChange(v -> { stats.loyaltySpeed = v; markDirty("Loyalty speed"); });
        riptideDistanceSlider = new EditorSlider("riptideDistance", "Riptide Distance", 0f, 64f, stats.riptideDistance)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .onChange(v -> { stats.riptideDistance = v; markDirty("Riptide distance"); });
        riptideRequiresWaterToggle = new EditorToggle("riptideWater", "Riptide Requires Water", stats.riptideRequiresWater)
            .onChange(v -> { stats.riptideRequiresWater = v; markDirty("Riptide requires water"); });
        channelingToggle = new EditorToggle("channeling", "Channeling Allowed", stats.channeling)
            .onChange(v -> { stats.channeling = v; markDirty("Channeling"); });
    }

    private List<EditorSection> getMechanicsSections() {
        var list = new java.util.ArrayList<EditorSection>();
        list.add(new SliderSectionAdapter(drawSpeedSlider));
        if (chargeTimeSlider != null) list.add(new SliderSectionAdapter(chargeTimeSlider));
        list.add(new SliderSectionAdapter(accuracySlider));
        list.add(new SliderSectionAdapter(rangeSlider));
        return list;
    }

    private List<EditorSection> getProjectileSections() {
        return List.of(
            new SliderSectionAdapter(projectileSpeedSlider),
            new SliderSectionAdapter(projectileGravitySlider),
            new SliderSectionAdapter(projectileSpreadSlider),
            new SliderSectionAdapter(baseDamageSlider),
            new ToggleSectionAdapter(multishotToggle),
            new SliderSectionAdapter(multishotCountSlider),
            new SliderSectionAdapter(piercingSlider),
            new ToggleSectionAdapter(infinityToggle)
        );
    }

    private List<EditorSection> getDamageSections() {
        return List.of(
            new SliderSectionAdapter(critChanceSlider),
            new SliderSectionAdapter(critDamageSlider)
        );
    }

    private List<EditorSection> getTridentSections() {
        return List.of(
            new SliderSectionAdapter(loyaltySpeedSlider),
            new SliderSectionAdapter(riptideDistanceSlider),
            new ToggleSectionAdapter(riptideRequiresWaterToggle),
            new ToggleSectionAdapter(channelingToggle)
        );
    }

    private List<EditorSection> getAmmoSections() {
        return List.of(
            new InputSectionAdapter(ammoFilterInput),
            new AmmoListSection()
        );
    }

    private List<String> computeAmmoMatches(String filter) {
        List<String> result = new ArrayList<>();
        if (filter == null || filter.isBlank()) return result;
        ResourceLocation id = ResourceLocation.tryParse(java.util.Objects.requireNonNull(filter));
        if (id == null) return result;
        try {
            TagKey<net.minecraft.world.item.Item> tag = TagKey.create(
                java.util.Objects.requireNonNull(Registries.ITEM),
                java.util.Objects.requireNonNull(id)
            );
            BuiltInRegistries.ITEM.getTag(java.util.Objects.requireNonNull(tag)).ifPresent(set -> {
                set.stream().limit(16).forEach(h -> {
                    var item = h.value();
                    String name = item.getDescriptionId();
                    try {
                        name = item.getDescription().getString();
                    } catch (Exception ignored) { }
                    result.add(name);
                });
            });
        } catch (Exception ignored) {
            // best effort
        }
        return result;
    }

    private class AmmoListSection implements EditorSection.CustomSection {
        private final List<ResponsiveLayout.Rect> suggestionRects = new ArrayList<>();

        @Override
        public String getId() { return "ammoList"; }
        @Override
        public String getLabel() { return ""; }
        @Override
        public int getHeight() {
            int lines = Math.max(1, ammoMatches.size());
            int suggLines = ammoSuggestions.size();
            return 14 + lines * 12 + 8 + suggLines * 14 + 20;
        }
        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font);
            int x = bounds.x() + UIConstants.Spacing.SM;
            int y = bounds.y() + UIConstants.Spacing.SM;
            String header = ammoMatches.isEmpty() ? "No items match this tag" : "Matching ammo (" + ammoMatches.size() + "):";
            graphics.drawString(font, header, x, y, UIConstants.Text.SECONDARY, false);
            y += 12;
            int shown = 0;
            for (String entry : ammoMatches) {
                if (shown >= 8) break;
                graphics.drawString(font, "- " + entry, x, y, UIConstants.Text.PRIMARY, false);
                y += 12;
                shown++;
            }

            // Suggestions + clear
            y += 6;
            graphics.drawString(font, "Suggestions:", x, y, UIConstants.Text.SECONDARY, false);
            y += 14;
            suggestionRects.clear();
            int btnHeight = 14;
            int btnWidth = bounds.width() - UIConstants.Spacing.SM * 2;
            // Clear button
            int clearX = x;
            graphics.fill(clearX, y, clearX + btnWidth, y + btnHeight, UIConstants.Background.INPUT);
            graphics.drawString(font, "Clear filter", clearX + 6, y + 3, UIConstants.Text.PRIMARY, false);
            suggestionRects.add(new ResponsiveLayout.Rect(clearX, y, btnWidth, btnHeight));
            y += btnHeight + 4;
            for (Suggestion sugg : ammoSuggestions) {
                graphics.fill(clearX, y, clearX + btnWidth, y + btnHeight, UIConstants.Background.PANEL);
                graphics.drawString(font, sugg.label(), clearX + 6, y + 3, UIConstants.Text.PRIMARY, false);
                suggestionRects.add(new ResponsiveLayout.Rect(clearX, y, btnWidth, btnHeight));
                y += btnHeight + 4;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            if (suggestionRects.isEmpty()) return false;
            // First rect is clear
            if (suggestionRects.get(0).contains((int) mouseX, (int) mouseY)) {
                stats.ammoFilter = "";
                ammoFilterInput.setValue("");
                ammoMatches = computeAmmoMatches(stats.ammoFilter);
                markDirty("Ammo filter");
                return true;
            }
            for (int i = 1; i < suggestionRects.size(); i++) {
                if (suggestionRects.get(i).contains((int) mouseX, (int) mouseY)) {
                    Suggestion s = ammoSuggestions.get(i - 1);
                    stats.ammoFilter = s.value();
                    ammoFilterInput.setValue(s.value());
                    ammoMatches = computeAmmoMatches(stats.ammoFilter);
                    markDirty("Ammo filter");
                    return true;
                }
            }
            return false;
        }
    }

    private record Suggestion(String value, String label) {}

    private List<EditorSection> getDebugSections() {
        ItemStack stack = getItem();
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem(), "stack item"));
        String registry = key == null ? "<unknown>" : key.toString();
        var customType = Objects.requireNonNull(DataComponents.CUSTOM_DATA, "custom data component");
        CustomData customData = stack.get(customType);
        CompoundTag customTag = customData == null ? new CompoundTag() : customData.copyTag();
        ItemDebugInfo info = new ItemDebugInfo(
            registry,
            stack.getCount(),
            stack.getDamageValue(),
            stack.getMaxDamage(),
            customTag == null ? 0 : customTag.size(),
            customData != null
        );

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

        List<String> history = getHistoryEntries();
        List<String> nbtLines = DebugInfoSection.formatNbtLines(getCustomDataTag(), 16);
        return List.of(new DebugInfoSection(info, comparisons, history, nbtLines, this::copyDebugInfo));
    }

    private ValueComparison comparison(String name, float current, float original) {
        boolean modified = Math.abs(current - original) > 1e-3;
        return new ValueComparison(name, original, current, Double.NaN, modified, false);
    }

    private CompoundTag getCustomDataTag() {
        var customType = Objects.requireNonNull(DataComponents.CUSTOM_DATA, "custom data component");
        CustomData data = item.get(customType);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        return tag == null ? new CompoundTag() : tag;
    }

    private void updateComponentsFromStats() {
        // update sourced info
        sourcedStats = RangedWeaponModule.getSourcedStats(item);
        applySourceLabel(drawSpeedSlider, sourcedStats.drawSpeed());
        applySourceLabel(chargeTimeSlider, sourcedStats.chargeTime());
        applySourceLabel(accuracySlider, sourcedStats.accuracy());
        applySourceLabel(rangeSlider, sourcedStats.range());
        applySourceLabel(projectileSpeedSlider, sourcedStats.projectileSpeed());
        applySourceLabel(projectileGravitySlider, sourcedStats.projectileGravity());
        applySourceLabel(projectileSpreadSlider, sourcedStats.projectileSpread());
        applySourceLabel(baseDamageSlider, sourcedStats.baseDamage());
        applySourceLabel(piercingSlider, sourcedStats.piercing());
        applySourceLabel(multishotCountSlider, sourcedStats.multishotCount());
        applySourceLabel(multishotToggle, sourcedStats.multishot());
        applySourceLabel(infinityToggle, sourcedStats.infinityOverride());
        applySourceLabel(critChanceSlider, sourcedStats.critChance());
        applySourceLabel(critDamageSlider, sourcedStats.critDamage());
        applySourceLabel(loyaltySpeedSlider, sourcedStats.loyaltySpeed());
        applySourceLabel(riptideDistanceSlider, sourcedStats.riptideDistance());
        applySourceLabel(riptideRequiresWaterToggle, sourcedStats.riptideRequiresWater());
        applySourceLabel(channelingToggle, sourcedStats.channeling());

        if (drawSpeedSlider != null) drawSpeedSlider.setValue(stats.drawSpeed);
        if (chargeTimeSlider != null) chargeTimeSlider.setValue(stats.chargeTime);
        if (accuracySlider != null) accuracySlider.setValue(stats.accuracy);
        if (rangeSlider != null) rangeSlider.setValue(stats.range);
        if (projectileSpeedSlider != null) projectileSpeedSlider.setValue(stats.projectileSpeed);
        if (projectileGravitySlider != null) projectileGravitySlider.setValue(stats.projectileGravity);
        if (projectileSpreadSlider != null) projectileSpreadSlider.setValue(stats.projectileSpread);
        if (baseDamageSlider != null) baseDamageSlider.setValue(stats.baseDamage);
        if (critChanceSlider != null) critChanceSlider.setValue(stats.critChance);
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
        ammoMatches = computeAmmoMatches(stats.ammoFilter);
    }

    private void applySourceLabel(EditorSlider slider, RangedWeaponModule.SourcedValue<?> sourced) {
        if (slider == null || sourced == null) return;
        String prefix = switch (sourced.source()) {
            case DEVMOD_COMPONENT -> "[DEV] ";
            case CUSTOM_DATA -> "[NBT] ";
            case VANILLA_DEFAULT -> "[VANILLA] ";
            default -> "";
        };
        slider.setLabel(prefix + slider.getLabel().replaceFirst("^\\[[^]]+\\] ", ""));
    }

    private void applySourceLabel(EditorToggle toggle, RangedWeaponModule.SourcedValue<?> sourced) {
        if (toggle == null || sourced == null) return;
        String prefix = switch (sourced.source()) {
            case DEVMOD_COMPONENT -> "[DEV] ";
            case CUSTOM_DATA -> "[NBT] ";
            case VANILLA_DEFAULT -> "[VANILLA] ";
            default -> "";
        };
        toggle.setLabel(prefix + toggle.getLabel().replaceFirst("^\\[[^]]+\\] ", ""));
    }

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        CompoundTag root = new CompoundTag();
        CompoundTag rangedTag = new CompoundTag();
        rangedTag.putFloat("drawSpeed", stats.drawSpeed);
        rangedTag.putFloat("chargeTime", stats.chargeTime);
        rangedTag.putFloat("accuracy", stats.accuracy);
        rangedTag.putFloat("range", stats.range);
        rangedTag.putFloat("projectileSpeed", stats.projectileSpeed);
        rangedTag.putFloat("projectileGravity", stats.projectileGravity);
        rangedTag.putFloat("projectileSpread", stats.projectileSpread);
        rangedTag.putFloat("baseDamage", stats.baseDamage);
        rangedTag.putInt("piercing", stats.piercing);
        rangedTag.putInt("multishotCount", stats.multishotCount);
        rangedTag.putBoolean("multishot", stats.multishot);
        rangedTag.putBoolean("infinityOverride", stats.infinityOverride);
        rangedTag.putFloat("critChance", stats.critChance);
        rangedTag.putFloat("critDamage", stats.critDamage);
        rangedTag.putFloat("riptideDistance", stats.riptideDistance);
        rangedTag.putFloat("loyaltySpeed", stats.loyaltySpeed);
        rangedTag.putBoolean("riptideRequiresWater", stats.riptideRequiresWater);
        rangedTag.putBoolean("channeling", stats.channeling);
        if (stats.ammoFilter != null && !stats.ammoFilter.isBlank()) {
            rangedTag.putString("ammoFilter", java.util.Objects.requireNonNull(stats.ammoFilter));
        }
        root.put("RangedStats", rangedTag);

        return new RangedWeaponStatsPayload(java.util.Objects.requireNonNull(item, "item cannot be null"), root, isGlobal);
    }

    @Override
    public void applyPreview() {
        try {
            ItemStack copy = item.copy();
            RangedWeaponModule.applyStats(copy, stats);
            setPreviewItem(copy);
        } catch (Exception ignored) {
            clearPreview();
        }
    }

    @Override
    public void resetToOriginal() {
        stats = originalStats.copy();
        updateComponentsFromStats();
        clearDirty();
    }

    @Override
    public boolean hasPendingDiff() {
        return !statsEquals(stats, originalStats);
    }

    public RangedWeaponModule.RangedStats getStats() {
        return stats;
    }

    public RangedVariant getVariant() {
        return variant;
    }

    public void setVariant(RangedVariant variant) {
        this.variant = variant == null ? RangedVariant.GENERIC : variant;
    }

    private String getVariantTabLabel() {
        return switch (variant) {
            case BOW -> "Bow";
            case CROSSBOW -> "Crossbow";
            case TRIDENT -> "Trident";
            default -> "Mechanics";
        };
    }

    private String getVariantTabId() {
        return switch (variant) {
            case BOW -> "bow";
            case CROSSBOW -> "crossbow";
            case TRIDENT -> "trident";
            default -> "mechanics";
        };
    }

    private boolean statsEquals(RangedWeaponModule.RangedStats a, RangedWeaponModule.RangedStats b) {
        return Math.abs(a.drawSpeed - b.drawSpeed) < 1e-3
            && Math.abs(a.chargeTime - b.chargeTime) < 1e-3
            && Math.abs(a.accuracy - b.accuracy) < 1e-3
            && Math.abs(a.range - b.range) < 1e-3
            && Math.abs(a.projectileSpeed - b.projectileSpeed) < 1e-3
            && Math.abs(a.projectileGravity - b.projectileGravity) < 1e-3
            && Math.abs(a.projectileSpread - b.projectileSpread) < 1e-3
            && Math.abs(a.baseDamage - b.baseDamage) < 1e-3
            && a.piercing == b.piercing
            && a.multishotCount == b.multishotCount
            && a.multishot == b.multishot
            && a.infinityOverride == b.infinityOverride
            && Math.abs(a.critChance - b.critChance) < 1e-3
            && Math.abs(a.critDamage - b.critDamage) < 1e-3
            && Math.abs(a.loyaltySpeed - b.loyaltySpeed) < 1e-3
            && Math.abs(a.riptideDistance - b.riptideDistance) < 1e-3
            && a.riptideRequiresWater == b.riptideRequiresWater
            && a.channeling == b.channeling
            && Objects.equals(a.ammoFilter, b.ammoFilter);
    }

    /**
     * Copy current debug info to clipboard.
     */
    private void copyDebugInfo() {
        try {
            Minecraft mc = Minecraft.getInstance();
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
            logEvent("Copied debug info");
        } catch (Exception ignored) {
            // Best-effort copy
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SECTION ADAPTERS
    // ═══════════════════════════════════════════════════════════════

    private static class SliderSectionAdapter implements EditorSection.SliderSection {
        private final EditorSlider slider;

        SliderSectionAdapter(EditorSlider slider) {
            this.slider = slider;
        }

        @Override
        public String getId() { return slider.getId(); }

        @Override
        public String getLabel() { return slider.getLabel(); }

        @Override
        public int getHeight() { return slider.calculateHeight(); }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            slider.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return slider.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return slider.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return slider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return slider.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public float getValue() { return slider.getValue(); }

        @Override
        public void setValue(float value) { slider.setValue(value); }

        @Override
        public float getMin() { return slider.getMin(); }

        @Override
        public float getMax() { return slider.getMax(); }

        @Override
        public float getStep() { return slider.getStep(); }

        @Override
        public String getFormat() { return "%.2f"; }

        @Override
        public int getColor() { return UIConstants.SliderColors.NEUTRAL; }

        @Override
        public boolean isDragging() { return slider.isDragging(); }

        @Override
        public void setDragging(boolean dragging) { }
    }

    private static class ToggleSectionAdapter implements EditorSection.ToggleSection {
        private final EditorToggle toggle;

        ToggleSectionAdapter(EditorToggle toggle) {
            this.toggle = toggle;
        }

        @Override
        public String getId() { return toggle.getId(); }

        @Override
        public String getLabel() { return toggle.getLabel(); }

        @Override
        public int getHeight() { return EditorDimensions.TOGGLE_HEIGHT + UIConstants.Spacing.SM; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            toggle.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return toggle.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return toggle.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean getValue() { return toggle.getValue(); }

        @Override
        public void setValue(boolean value) { toggle.setValue(value); }
    }

    private static class InputSectionAdapter implements EditorSection.InputSection {
        private final EditorTextField input;

        InputSectionAdapter(EditorTextField input) {
            this.input = input;
        }

        @Override public String getId() { return input.getId(); }
        @Override public String getLabel() { return input.getLabel(); }
        @Override public int getHeight() { return input.calculateHeight(); }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            input.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Right-click to clear quickly
            if (button == 1 && input.getBounds().contains((int) mouseX, (int) mouseY)) {
                input.setValue("");
                return true;
            }
            return input.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return input.keyPressed(keyCode, scanCode, modifiers); }
        @Override public boolean charTyped(char chr, int modifiers) { return input.charTyped(chr, modifiers); }
        @Override public String getText() { return input.getValue(); }
        @Override public void setText(String text) { input.setValue(text); }
        @Override public String getPlaceholder() { return ""; }
        @Override public boolean isNumeric() { return false; }
    }
}
