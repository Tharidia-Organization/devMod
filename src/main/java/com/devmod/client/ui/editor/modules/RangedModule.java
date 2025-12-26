package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.ammo.AmmoSystem;
import com.devmod.client.ui.editor.AbstractEditorModule;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.ModuleTab;
import com.devmod.client.ui.editor.RangedWeaponModule;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorTextField;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.debug.DebugInfoSection;
import com.devmod.client.ui.editor.debug.ItemDebugInfo;
import com.devmod.client.ui.editor.debug.ValueComparison;
import com.devmod.client.ui.editor.sections.InputSectionAdapter;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import com.devmod.network.RangedWeaponStatsPayload;

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
    private static final int TOGGLE_SECTION_HEIGHT = EditorDimensions.TOGGLE_HEIGHT + UIConstants.Spacing.SM;

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

    /**
     * Determines the source badge based on where the stats come from.
     */
    private SourceBadge.Source determineSource() {
        if (sourcedStats != null && sourcedStats.drawSpeed() != null) {
            return switch (sourcedStats.drawSpeed().source()) {
                case DEVMOD_COMPONENT -> SourceBadge.Source.DEV;
                case CUSTOM_DATA -> SourceBadge.Source.NBT;
                default -> SourceBadge.Source.VANILLA;
            };
        }
        return SourceBadge.Source.VANILLA;
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
        SourceBadge.Source source = determineSource();
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
            .showInput(true)
            .source(source)
            .info("Multiplier for bow draw/crossbow reload speed. 1.0 = normal, 2.0 = twice as fast, 0.5 = twice as slow.")
            .onChange(v -> { stats.drawSpeed = v; markDirty(drawLabel); });
        if (variant == RangedVariant.CROSSBOW) {
            chargeTimeSlider = new EditorSlider("chargeTime", chargeLabel, 0.2f, 3.0f, stats.chargeTime)
                .step(0.05f)
                .format("%.2f")
                .suffix("x")
                .trackColor(UIConstants.SliderColors.SPEED)
                .showInput(true)
                .source(source)
                .info("Time to fully charge the crossbow. 1.0 = normal, higher = slower charge.")
                .onChange(v -> { stats.chargeTime = v; markDirty(chargeLabel); });
        }

        accuracySlider = new EditorSlider("accuracy", accuracyLabel, 0.5f, 1.25f, stats.accuracy)
            .step(0.01f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Shot precision. 1.0 = perfect accuracy, <1.0 = more spread, >1.0 = tighter grouping.")
            .onChange(v -> { stats.accuracy = v; markDirty(accuracyLabel); });

        rangeSlider = new EditorSlider("range", rangeLabel, 0.5f, 3.5f, stats.range)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Maximum effective range multiplier. Affects how far projectiles travel before losing damage.")
            .onChange(v -> { stats.range = v; markDirty(rangeLabel); });
    }

    private void createProjectileComponents() {
        SourceBadge.Source source = determineSource();
        String projSpeedLabel = variant == RangedVariant.CROSSBOW ? "Bolt Speed" : "Arrow Speed";
        projectileSpeedSlider = new EditorSlider("projectileSpeed", projSpeedLabel, 0.5f, 5.0f, stats.projectileSpeed)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("How fast the projectile travels. Higher = flatter trajectory, more damage. 1.0 = vanilla arrow speed.")
            .onChange(v -> { stats.projectileSpeed = v; markDirty(projSpeedLabel); });

        projectileGravitySlider = new EditorSlider("projectileGravity", "Gravity", 0f, 0.2f, stats.projectileGravity)
            .step(0.005f)
            .format("%.3f")
            .suffix(" g/t")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Downward acceleration per tick. 0.05 = normal arrow, 0 = no drop (laser-like), 0.2 = very heavy.")
            .onChange(v -> { stats.projectileGravity = v; markDirty("Gravity"); });

        projectileSpreadSlider = new EditorSlider("projectileSpread", "Spread", 0f, 3f, stats.projectileSpread)
            .step(0.05f)
            .format("%.2f")
            .suffix("°")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Random deviation added to each shot. 0 = perfectly straight, 3 = very inaccurate spread.")
            .onChange(v -> { stats.projectileSpread = v; markDirty("Spread"); });

        baseDamageSlider = new EditorSlider("baseDamage", "Base Damage", 0f, 20f, stats.baseDamage)
            .step(0.1f)
            .format("%.1f")
            .suffix(" HP")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Base damage before Power enchant and velocity bonuses. Vanilla arrow = 2.0, Power V adds +12.5.")
            .onChange(v -> { stats.baseDamage = v; markDirty("Base damage"); });

        multishotToggle = new EditorToggle("multishot", "Enable Multishot", stats.multishot)
            .source(source)
            .tooltip("Fire multiple projectiles per shot (uses one ammo)")
            .onChange(val -> { stats.multishot = val; markDirty("Multishot"); });

        piercingSlider = new EditorSlider("piercing", "Piercing Level", 0f, 5f, stats.piercing)
            .step(1f)
            .format("%.0f")
            .suffix(" targets")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Number of entities the projectile can pass through. Like Piercing enchant. 0 = stops on first hit.")
            .onChange(v -> { stats.piercing = Math.round(v); markDirty("Piercing"); });

        multishotCountSlider = new EditorSlider("multishotCount", "Projectile Count", 1f, 5f, stats.multishotCount)
            .step(1f)
            .format("%.0f")
            .suffix(" arrows")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Number of projectiles fired when Multishot is enabled. Vanilla Multishot = 3. All consume only 1 ammo.")
            .onChange(v -> { stats.multishotCount = Math.round(v); markDirty("Projectile count"); });
    }

    private void createMetadataComponents() {
        SourceBadge.Source source = determineSource();
        ammoFilterInput = new EditorTextField("ammoFilter", "Ammo Filter")
            .placeholder("e.g. minecraft:arrow or #minecraft:arrows")
            .onChange(val -> {
                stats.ammoFilter = val == null ? "" : val.trim();
                markDirty("Ammo filter");
            });
        ammoFilterInput.setValue(stats.ammoFilter == null ? "" : stats.ammoFilter);
        infinityToggle = new EditorToggle("infinity", "Infinity Override", stats.infinityOverride)
            .source(source)
            .tooltip("Force infinite ammo even without Infinity enchant. First arrow in inventory is used as template.")
            .onChange(v -> { stats.infinityOverride = v; markDirty("Infinity override"); });
    }

    private void createDamageComponents() {
        SourceBadge.Source source = determineSource();
        critChanceSlider = new EditorSlider("critChance", "Crit Chance", 0f, 1f, stats.critChance)
            .step(0.01f)
            .format("%.2f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Chance for projectile to deal critical damage (0-1). Vanilla arrows crit at full velocity. This adds bonus crit chance.")
            .onChange(v -> { stats.critChance = v; markDirty("Crit chance"); });

        critDamageSlider = new EditorSlider("critDamage", "Crit Damage", 1.0f, 3.5f, stats.critDamage)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Damage multiplier on critical hit. 1.5x = 50% bonus damage. Stacks with Power enchant.")
            .onChange(v -> { stats.critDamage = v; markDirty("Crit damage"); });
    }

    private void createTridentComponents() {
        SourceBadge.Source source = determineSource();
        loyaltySpeedSlider = new EditorSlider("loyaltySpeed", "Loyalty Speed", 0f, 5f, stats.loyaltySpeed)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("How fast the trident returns to the player. Higher = faster return. 0 = no return (requires pickup).")
            .onChange(v -> { stats.loyaltySpeed = v; markDirty("Loyalty speed"); });
        riptideDistanceSlider = new EditorSlider("riptideDistance", "Riptide Distance", 0f, 64f, stats.riptideDistance)
            .step(1f)
            .format("%.0f")
            .suffix(" blocks")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Maximum distance the player can travel when using Riptide in water/rain. 0 = disabled.")
            .onChange(v -> { stats.riptideDistance = v; markDirty("Riptide distance"); });
        riptideRequiresWaterToggle = new EditorToggle("riptideWater", "Riptide Requires Water", stats.riptideRequiresWater)
            .source(source)
            .tooltip("If enabled, Riptide only works in water or rain. If disabled, works anywhere.")
            .onChange(v -> { stats.riptideRequiresWater = v; markDirty("Riptide requires water"); });
        channelingToggle = new EditorToggle("channeling", "Channeling Allowed", stats.channeling)
            .source(source)
            .tooltip("When enabled, trident summons lightning on hit during thunderstorms.")
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
            new ToggleSectionAdapter(multishotToggle, TOGGLE_SECTION_HEIGHT),
            new SliderSectionAdapter(multishotCountSlider),
            new SliderSectionAdapter(piercingSlider),
            new ToggleSectionAdapter(infinityToggle, TOGGLE_SECTION_HEIGHT)
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
            new ToggleSectionAdapter(riptideRequiresWaterToggle, TOGGLE_SECTION_HEIGHT),
            new ToggleSectionAdapter(channelingToggle, TOGGLE_SECTION_HEIGHT)
        );
    }

    private List<EditorSection> getAmmoSections() {
        return List.of(
            new InputSectionAdapter(ammoFilterInput, true),
            new AmmoListSection()
        );
    }

    private List<String> computeAmmoMatches(String filter) {
        if (filter == null || filter.isBlank()) {
            return new ArrayList<>();
        }
        // Delegate to AmmoSystem utility for consistent behavior
        // Use tag string directly for lookup
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
            int lines = Math.max(1, ammoMatches.size());
            int suggLines = ammoSuggestions.size();
            return HEADER_BLOCK_HEIGHT + lines * ENTRY_LINE_HEIGHT + MATCHES_BOTTOM_PADDING
                + suggLines * SUGGESTION_ROW_HEIGHT + SUGGESTION_BLOCK_PADDING;
        }
        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font);
            int x = bounds.x() + UIConstants.Spacing.SM;
            int y = bounds.y() + UIConstants.Spacing.SM;
            String header = ammoMatches.isEmpty() ? "No items match this tag" : "Matching ammo (" + ammoMatches.size() + "):";
            graphics.drawString(font, header, x, y, UIConstants.Text.SECONDARY(), false);
            y += ENTRY_LINE_HEIGHT;
            int shown = 0;
            for (String entry : ammoMatches) {
                if (shown >= MAX_MATCHES_SHOWN) break;
                graphics.drawString(font, "- " + entry, x, y, UIConstants.Text.PRIMARY(), false);
                y += ENTRY_LINE_HEIGHT;
                shown++;
            }

            // Suggestions + clear
            y += SUGGESTION_TITLE_GAP;
            graphics.drawString(font, "Suggestions:", x, y, UIConstants.Text.SECONDARY(), false);
            y += SUGGESTION_TITLE_LINE_HEIGHT;
            suggestionRects.clear();
            int btnHeight = SUGGESTION_ROW_HEIGHT;
            int btnWidth = bounds.width() - UIConstants.Spacing.SM * 2;
            // Clear button
            int clearX = x;
            graphics.fill(clearX, y, clearX + btnWidth, y + btnHeight, UIConstants.Background.INPUT());
            graphics.drawString(font, "Clear filter", clearX + BUTTON_TEXT_OFFSET_X,
                y + BUTTON_TEXT_OFFSET_Y, UIConstants.Text.PRIMARY(), false);
            suggestionRects.add(new ResponsiveLayout.Rect(clearX, y, btnWidth, btnHeight));
            y += btnHeight + BUTTON_GAP;
            for (Suggestion sugg : ammoSuggestions) {
                graphics.fill(clearX, y, clearX + btnWidth, y + btnHeight, UIConstants.Background.PANEL());
                graphics.drawString(font, sugg.label(), clearX + BUTTON_TEXT_OFFSET_X,
                    y + BUTTON_TEXT_OFFSET_Y, UIConstants.Text.PRIMARY(), false);
                suggestionRects.add(new ResponsiveLayout.Rect(clearX, y, btnWidth, btnHeight));
                y += btnHeight + BUTTON_GAP;
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

}
