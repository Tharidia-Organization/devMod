package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.network.WeaponStatsPayload;
import com.frenkvs.devmod.ui.editor.AbstractEditorModule;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.ModuleTab;
import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.core.EditorCache;
import com.frenkvs.devmod.ui.editor.core.EditorDimensions;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.debug.DebugInfoSection;
import com.frenkvs.devmod.ui.editor.debug.ItemDebugInfo;
import com.frenkvs.devmod.ui.editor.debug.ValueComparison;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Editor module for weapon statistics.
 * Allows editing damage multipliers, attack stats, and special effects.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.3
 */
public class WeaponModule extends AbstractEditorModule {

    // ═══════════════════════════════════════════════════════════════
    // WEAPON STATS
    // ═══════════════════════════════════════════════════════════════

    private static final String NBT_KEY = "WeaponModStats";
    private WeaponStats stats = new WeaponStats();
    private WeaponStats originalStats = new WeaponStats();

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Hit Location Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider headMultSlider;
    private EditorSlider bodyMultSlider;
    private EditorSlider armsMultSlider;
    private EditorSlider legsMultSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Combat Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider attackDamageSlider;
    private EditorSlider attackSpeedSlider;
    private EditorSlider attackReachSlider;
    private EditorSlider attackKnockbackSlider;
    private EditorSlider armorPenetrationSlider;
    private EditorSlider baseDamageBonusSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Special Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorToggle critEnabledToggle;
    private EditorSlider critChanceSlider;
    private EditorSlider critDamageSlider;
    private EditorToggle lifestealEnabledToggle;
    private EditorSlider lifestealSlider;
    private EditorToggle fireDamageEnabledToggle;
    private EditorSlider fireDamageSlider;
    private EditorToggle magicDamageEnabledToggle;
    private EditorSlider magicDamageSlider;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public WeaponModule() {
        super("weapon", "Weapon Editor");
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        // Load existing weapon stats from item NBT
        loadStatsFromItem();
        originalStats = stats.copy();

        // Update all sliders to reflect current stats
        updateSlidersFromStats();
    }

    private void loadStatsFromItem() {
        // Check if item has weapon stats component
        CompoundTag tag = item.getOrDefault(
            Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
            Objects.requireNonNull(net.minecraft.world.item.component.CustomData.EMPTY)
        ).copyTag();

        if (tag.contains(NBT_KEY)) {
            stats = WeaponStats.load(tag.getCompound(NBT_KEY));
        } else {
            stats = new WeaponStats();
        }
    }

    private void updateSlidersFromStats() {
        if (headMultSlider != null) headMultSlider.setValue(stats.headMult);
        if (bodyMultSlider != null) bodyMultSlider.setValue(stats.bodyMult);
        if (armsMultSlider != null) armsMultSlider.setValue(stats.armsMult);
        if (legsMultSlider != null) legsMultSlider.setValue(stats.legsMult);

        if (attackDamageSlider != null) attackDamageSlider.setValue(stats.attackDamage);
        if (attackSpeedSlider != null) attackSpeedSlider.setValue(stats.attackSpeed);
        if (attackReachSlider != null) attackReachSlider.setValue(stats.attackReach);
        if (attackKnockbackSlider != null) attackKnockbackSlider.setValue(stats.attackKnockback);
        if (armorPenetrationSlider != null) armorPenetrationSlider.setValue(stats.armorPenetration * 100);
        if (baseDamageBonusSlider != null) baseDamageBonusSlider.setValue(stats.baseDamageBonus);

        if (critChanceSlider != null) critChanceSlider.setValue(stats.critChance * 100);
        if (critDamageSlider != null) critDamageSlider.setValue(stats.critDamage);
        if (lifestealSlider != null) lifestealSlider.setValue(stats.lifesteal * 100);
        if (fireDamageSlider != null) fireDamageSlider.setValue(stats.fireDamageBonus);
        if (magicDamageSlider != null) magicDamageSlider.setValue(stats.magicDamageBonus);
    }

    /**
     * Apply external stats (preset/import) while keeping undo/history in sync.
     */
    public void applyExternalStats(WeaponStats newStats, String reason) {
        if (newStats == null) {
            return;
        }
        saveUndoState();
        stats = newStats.copy();
        updateSlidersFromStats();
        pendingChanges.clear();
        String changeReason = (reason == null || reason.isBlank()) ? "Preset applied" : reason;
        pendingChanges.add(changeReason);
        addHistoryEntry(changeReason);
        EditorCache.getInstance().invalidateItem(item.toString());
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();

        // Create UI components
        createHitLocationComponents();
        createCombatComponents();
        createSpecialComponents();

        // Add tabs
        addTab(ModuleTab.of("hitlocation", "Hit Location", this::getHitLocationSections));
        addTab(ModuleTab.of("combat", "Combat", this::getCombatSections));
        addTab(ModuleTab.of("special", "Special", this::getSpecialSections));
        addTab(ModuleTab.of("debug", "Debug", this::getDebugSections));
    }

    // ═══════════════════════════════════════════════════════════════
    // HIT LOCATION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createHitLocationComponents() {
        headMultSlider = new EditorSlider("headMult", "Head Multiplier", 0.5f, 3.0f, 1.5f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.headMult = v; markDirty("Head multiplier"); });

        bodyMultSlider = new EditorSlider("bodyMult", "Body Multiplier", 0.5f, 2.0f, 1.0f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.bodyMult = v; markDirty("Body multiplier"); });

        armsMultSlider = new EditorSlider("armsMult", "Arms Multiplier", 0.3f, 1.5f, 0.8f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.armsMult = v; markDirty("Arms multiplier"); });

        legsMultSlider = new EditorSlider("legsMult", "Legs Multiplier", 0.3f, 1.5f, 0.7f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.legsMult = v; markDirty("Legs multiplier"); });
    }

    private List<EditorSection> getHitLocationSections() {
        return List.of(
            new SliderSectionAdapter(headMultSlider),
            new SliderSectionAdapter(bodyMultSlider),
            new SliderSectionAdapter(armsMultSlider),
            new SliderSectionAdapter(legsMultSlider)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // COMBAT TAB
    // ═══════════════════════════════════════════════════════════════

    private void createCombatComponents() {
        attackDamageSlider = new EditorSlider("atkDmg", "Attack Damage", 0f, 50f, 0f)
            .step(0.5f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.attackDamage = v; markDirty("Attack damage"); });

        attackSpeedSlider = new EditorSlider("atkSpd", "Attack Speed", 0f, 4f, 0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .onChange(v -> { stats.attackSpeed = v; markDirty("Attack speed"); });

        attackReachSlider = new EditorSlider("atkRch", "Attack Reach", 0f, 6f, 0f)
            .step(0.1f)
            .format("%.1f")
            .suffix(" blocks")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .onChange(v -> { stats.attackReach = v; markDirty("Attack reach"); });

        attackKnockbackSlider = new EditorSlider("atkKB", "Knockback", 0f, 5f, 0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .onChange(v -> { stats.attackKnockback = v; markDirty("Knockback"); });

        armorPenetrationSlider = new EditorSlider("armorPen", "Armor Penetration", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .onChange(v -> { stats.armorPenetration = v / 100f; markDirty("Armor penetration"); });

        baseDamageBonusSlider = new EditorSlider("baseDmg", "Base Damage Bonus", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.baseDamageBonus = v; markDirty("Base damage bonus"); });
    }

    private List<EditorSection> getCombatSections() {
        return List.of(
            new SliderSectionAdapter(attackDamageSlider),
            new SliderSectionAdapter(attackSpeedSlider),
            new SliderSectionAdapter(attackReachSlider),
            new SliderSectionAdapter(attackKnockbackSlider),
            new SliderSectionAdapter(armorPenetrationSlider),
            new SliderSectionAdapter(baseDamageBonusSlider)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // SPECIAL TAB
    // ═══════════════════════════════════════════════════════════════

    private void createSpecialComponents() {
        // Critical Hit Toggle & Sliders
        critEnabledToggle = new EditorToggle("critEnabled", "Enable Critical Hits", stats.critChance > 0)
            .tooltip("Enable critical hit mechanics for this weapon")
            .onChange(enabled -> {
                critChanceSlider.setEnabled(enabled);
                critDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.critChance = 0f;
                    critChanceSlider.setValue(0f);
                }
                markDirty("Critical hits " + (enabled ? "enabled" : "disabled"));
            });

        critChanceSlider = new EditorSlider("critCh", "Critical Chance", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.PERCENT)
            .enabled(stats.critChance > 0)
            .onChange(v -> { stats.critChance = v / 100f; markDirty("Critical chance"); });

        critDamageSlider = new EditorSlider("critDmg", "Critical Damage", 1f, 5f, 1.5f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .enabled(stats.critChance > 0)
            .onChange(v -> { stats.critDamage = v; markDirty("Critical damage"); });

        // Lifesteal Toggle & Slider
        lifestealEnabledToggle = new EditorToggle("lifestealEnabled", "Enable Lifesteal", stats.lifesteal > 0)
            .tooltip("Heal a percentage of damage dealt")
            .onChange(enabled -> {
                lifestealSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.lifesteal = 0f;
                    lifestealSlider.setValue(0f);
                }
                markDirty("Lifesteal " + (enabled ? "enabled" : "disabled"));
            });

        lifestealSlider = new EditorSlider("lifesteal", "Lifesteal", 0f, 50f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .enabled(stats.lifesteal > 0)
            .onChange(v -> { stats.lifesteal = v / 100f; markDirty("Lifesteal"); });

        // Fire Damage Toggle & Slider
        fireDamageEnabledToggle = new EditorToggle("fireEnabled", "Enable Fire Damage", stats.fireDamageBonus > 0)
            .tooltip("Add fire damage to attacks")
            .onChange(enabled -> {
                fireDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.fireDamageBonus = 0f;
                    fireDamageSlider.setValue(0f);
                }
                markDirty("Fire damage " + (enabled ? "enabled" : "disabled"));
            });

        fireDamageSlider = new EditorSlider("fireDmg", "Fire Damage", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .enabled(stats.fireDamageBonus > 0)
            .onChange(v -> { stats.fireDamageBonus = v; markDirty("Fire damage"); });

        // Magic Damage Toggle & Slider
        magicDamageEnabledToggle = new EditorToggle("magicEnabled", "Enable Magic Damage", stats.magicDamageBonus > 0)
            .tooltip("Add magic damage to attacks")
            .onChange(enabled -> {
                magicDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.magicDamageBonus = 0f;
                    magicDamageSlider.setValue(0f);
                }
                markDirty("Magic damage " + (enabled ? "enabled" : "disabled"));
            });

        magicDamageSlider = new EditorSlider("magicDmg", "Magic Damage", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .enabled(stats.magicDamageBonus > 0)
            .onChange(v -> { stats.magicDamageBonus = v; markDirty("Magic damage"); });
    }

    private List<EditorSection> getSpecialSections() {
        return List.of(
            new SliderSectionAdapter(critChanceSlider),
            new SliderSectionAdapter(critDamageSlider),
            new SliderSectionAdapter(lifestealSlider),
            new SliderSectionAdapter(fireDamageSlider),
            new SliderSectionAdapter(magicDamageSlider)
        );
    }

    private CompoundTag getCustomDataTag() {
        Objects.requireNonNull(item, "item cannot be null");
        var customData = item.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA, "DataComponents.CUSTOM_DATA cannot be null"),
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = customData.copyTag();
        return tag == null ? new CompoundTag() : tag;
    }

    private List<EditorSection> getDebugSections() {
        ItemDebugInfo info = buildDebugInfo();
        List<ValueComparison> comparisons = buildValueComparisons();
        List<String> history = getRecentHistoryEntries(5);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(getCustomDataTag(), 8);
        return List.of(new DebugInfoSection(info, comparisons, history, nbtLines, this::copyDebugInfo));
    }

    private ItemDebugInfo buildDebugInfo() {
        var rawItem = Objects.requireNonNull(item.getItem(), "item cannot be null");
        var key = BuiltInRegistries.ITEM.getKey(rawItem);
        String registryName = key == null ? "<unknown>" : key.toString();
        CompoundTag customTag = getCustomDataTag();
        int tagCount = customTag.getAllKeys().size();
        boolean hasCustomData = customTag.contains(NBT_KEY);
        return new ItemDebugInfo(registryName, item.getCount(), item.getDamageValue(), item.getMaxDamage(),
            tagCount, hasCustomData);
    }

    private List<ValueComparison> buildValueComparisons() {
        List<ValueComparison> comparisons = new ArrayList<>();
        WeaponStats actual = loadStatsFromTag(getCustomDataTag());

        comparisons.add(makeComparison("Head multiplier", actual.headMult, stats.headMult));
        comparisons.add(makeComparison("Body multiplier", actual.bodyMult, stats.bodyMult));
        comparisons.add(makeComparison("Arms multiplier", actual.armsMult, stats.armsMult));
        comparisons.add(makeComparison("Legs multiplier", actual.legsMult, stats.legsMult));

        comparisons.add(makeComparison("Attack damage", actual.attackDamage, stats.attackDamage));
        comparisons.add(makeComparison("Attack speed", actual.attackSpeed, stats.attackSpeed));
        comparisons.add(makeComparison("Attack reach", actual.attackReach, stats.attackReach));
        comparisons.add(makeComparison("Attack knockback", actual.attackKnockback, stats.attackKnockback));
        comparisons.add(makeComparison("Armor penetration (%)", actual.armorPenetration * 100, stats.armorPenetration * 100));
        comparisons.add(makeComparison("Base damage bonus", actual.baseDamageBonus, stats.baseDamageBonus));

        comparisons.add(makeComparison("Crit chance (%)", actual.critChance * 100, stats.critChance * 100));
        comparisons.add(makeComparison("Crit damage", actual.critDamage, stats.critDamage));

        comparisons.add(makeComparison("Lifesteal (%)", actual.lifesteal * 100, stats.lifesteal * 100));
        comparisons.add(makeComparison("Fire damage", actual.fireDamageBonus, stats.fireDamageBonus));
        comparisons.add(makeComparison("Magic damage", actual.magicDamageBonus, stats.magicDamageBonus));
        return comparisons;
    }

    private WeaponStats loadStatsFromTag(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            return WeaponStats.load(tag.getCompound(NBT_KEY));
        }
        return new WeaponStats();
    }

    private ValueComparison makeComparison(String label, double actualValue, double current) {
        boolean mismatch = Math.abs(actualValue - current) > 0.1e-3;
        return new ValueComparison(label, actualValue, current, actualValue, mismatch, mismatch);
    }

    private void copyDebugInfo() {
        ItemDebugInfo info = buildDebugInfo();
        List<ValueComparison> comparisons = buildValueComparisons();
        List<String> history = getRecentHistoryEntries(5);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(getCustomDataTag(), 12);
        String payload = buildDebugClipboardText(info, comparisons, history, nbtLines);

        Minecraft mc = Minecraft.getInstance();
        if (mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(payload);
        }
        reportStatus("Debug info copied!", UIConstants.Accent.GREEN);
    }

    private String buildDebugClipboardText(ItemDebugInfo info, List<ValueComparison> comparisons,
                                           List<String> history, List<String> nbtLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DevMod Debug Info ===\n");
        sb.append("Registry: ").append(info.registryName()).append("\n");
        sb.append("Stack size: ").append(info.stackSize()).append("\n");
        sb.append("Damage: ").append(info.currentDamage()).append("/").append(info.maxDamage()).append("\n");
        sb.append("NBT tags: ").append(info.nbtTagCount()).append("\n");
        sb.append("Custom data: ").append(info.hasCustomData() ? "yes" : "no").append("\n\n");

        sb.append("--- Values ---\n");
        for (ValueComparison comp : comparisons) {
            String suffix = comp.hasMismatch() ? " [MISMATCH]" : comp.isModified() ? " [MOD]" : "";
            sb.append(String.format(Locale.US, "%s: exp %.2f srv %.2f cur %.2f%s\n",
                comp.attributeName(), comp.originalValue(), comp.serverValue(), comp.currentValue(), suffix));
        }

        sb.append("\n--- History ---\n");
        if (history.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (String entry : history) {
                sb.append(entry).append("\n");
            }
        }

        sb.append("\n--- NBT ---\n");
        if (nbtLines.isEmpty()) {
            sb.append("(empty)\n");
        } else {
            for (String line : nbtLines) {
                sb.append(line).append("\n");
            }
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int mouseX, int mouseY) {
        int y = contentBounds.y() + UIConstants.Spacing.MD;
        int sliderWidth = layout != null ? layout.getSliderWidth() : 200;
        int x = contentBounds.x() + (contentBounds.width() - sliderWidth) / 2;

        // Special tab has toggles + sliders
        if (activeTabIndex == 2) {
            y = renderSpecialTab(graphics, x, y, sliderWidth, mouseX, mouseY);
        } else {
            // Regular tabs with only sliders
            List<EditorSlider> sliders = getCurrentTabSliders();
            for (EditorSlider slider : sliders) {
                int height = slider.render(graphics, x, y, sliderWidth, mouseX, mouseY);
                y += height + UIConstants.Spacing.SM;
            }
        }

        // DPS preview at bottom
        renderDPSPreview(graphics, contentBounds, y);
    }

    private int renderSpecialTab(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        // Critical Hit section
        y += critEnabledToggle.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.XS;
        y += critChanceSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.XS;
        y += critDamageSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.MD;

        // Lifesteal section
        y += lifestealEnabledToggle.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.XS;
        y += lifestealSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.MD;

        // Fire Damage section
        y += fireDamageEnabledToggle.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.XS;
        y += fireDamageSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.MD;

        // Magic Damage section
        y += magicDamageEnabledToggle.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.XS;
        y += magicDamageSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.SM;

        return y;
    }

    private List<EditorSlider> getCurrentTabSliders() {
        return switch (activeTabIndex) {
            case 0 -> List.of(headMultSlider, bodyMultSlider, armsMultSlider, legsMultSlider);
            case 1 -> List.of(attackDamageSlider, attackSpeedSlider, attackReachSlider,
                             attackKnockbackSlider, armorPenetrationSlider, baseDamageBonusSlider);
            case 2 -> List.of(critChanceSlider, critDamageSlider, lifestealSlider,
                             fireDamageSlider, magicDamageSlider);
            default -> List.of();
        };
    }

    private List<EditorToggle> getCurrentTabToggles() {
        return switch (activeTabIndex) {
            case 2 -> List.of(critEnabledToggle, lifestealEnabledToggle, fireDamageEnabledToggle, magicDamageEnabledToggle);
            default -> List.of();
        };
    }

    private void renderDPSPreview(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int startY) {
        var font = Minecraft.getInstance().font;
        int y = startY + UIConstants.Spacing.LG;

        // Calculate DPS
        float dps = EditorCache.getInstance().getOrCompute(
            EditorCache.Types.DPS,
            Objects.requireNonNull(item.toString()),
            () -> stats.calculateDPS(4.0f) // Default base damage
        );

        String dpsText = String.format("DPS: %.1f", dps);
        int textWidth = font.width(Objects.requireNonNull(dpsText));
        int x = contentBounds.x() + (contentBounds.width() - textWidth) / 2;

        graphics.drawString(font, dpsText, x, y, UIConstants.Text.VALUE, false);
    }

    @Override
    public int calculateContentHeight() {
        int height = UIConstants.Spacing.MD;

        if (activeTabIndex == 2) {
            // Special tab: toggles + sliders
            // 4 toggle+slider pairs with spacing
            height += 4 * (EditorDimensions.TOGGLE_HEIGHT + UIConstants.Spacing.XS);
            for (EditorSlider slider : getCurrentTabSliders()) {
                height += slider.calculateHeight() + UIConstants.Spacing.MD;
            }
        } else {
            List<EditorSlider> sliders = getCurrentTabSliders();
            for (EditorSlider slider : sliders) {
                height += slider.calculateHeight() + UIConstants.Spacing.SM;
            }
        }

        // DPS preview
        height += UIConstants.Spacing.LG + 12;

        return height + UIConstants.Spacing.MD;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check toggles first (only on special tab)
        for (EditorToggle toggle : getCurrentTabToggles()) {
            if (toggle.mouseClicked(mouseX, mouseY, button)) {
                saveUndoState();
                return true;
            }
        }

        // Check sliders
        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.mouseClicked(mouseX, mouseY, button)) {
                saveUndoState();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                saveUndoState();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Check toggles first
        for (EditorToggle toggle : getCurrentTabToggles()) {
            if (toggle.keyPressed(keyCode, scanCode, modifiers)) {
                saveUndoState();
                return true;
            }
        }

        // Check sliders
        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.keyPressed(keyCode, scanCode, modifiers)) {
                saveUndoState();
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        // Build NBT from current stats
        CompoundTag statsTag = new CompoundTag();
        stats.save(statsTag);

        return new WeaponStatsPayload(Objects.requireNonNull(item), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        // Apply stats to item for preview (client-side only)
        CompoundTag tag = item.getOrDefault(
            Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
            Objects.requireNonNull(net.minecraft.world.item.component.CustomData.EMPTY)
        ).copyTag();

        CompoundTag statsTag = new CompoundTag();
        stats.save(statsTag);
        tag.put(NBT_KEY, statsTag);

        item.set(
            Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
            net.minecraft.world.item.component.CustomData.of(tag)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & STATE
    // ═══════════════════════════════════════════════════════════════

    public WeaponStats getStats() {
        return stats;
    }

    public WeaponStats getOriginalStats() {
        return originalStats;
    }

    /**
     * Reset all stats to original values loaded from the item.
     */
    public void resetToOriginal() {
        stats = originalStats.copy();
        updateSlidersFromStats();
        clearDirty();
    }

    /**
     * Check if current stats differ from original.
     */
    public boolean hasModifications() {
        return !statsEquals(stats, originalStats);
    }

    @Override
    public boolean hasPendingDiff() {
        return hasModifications();
    }

    private boolean statsEquals(WeaponStats a, WeaponStats b) {
        return a.headMult == b.headMult
            && a.bodyMult == b.bodyMult
            && a.armsMult == b.armsMult
            && a.legsMult == b.legsMult
            && a.attackDamage == b.attackDamage
            && a.attackSpeed == b.attackSpeed
            && a.attackReach == b.attackReach
            && a.attackKnockback == b.attackKnockback
            && a.armorPenetration == b.armorPenetration
            && a.baseDamageBonus == b.baseDamageBonus
            && a.critChance == b.critChance
            && a.critDamage == b.critDamage
            && a.lifesteal == b.lifesteal
            && a.fireDamageBonus == b.fireDamageBonus
            && a.magicDamageBonus == b.magicDamageBonus;
    }

    // ═══════════════════════════════════════════════════════════════
    // SLIDER SECTION ADAPTER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adapts EditorSlider to EditorSection interface.
     */
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
        public void setDragging(boolean dragging) { /* handled internally */ }
    }
}
