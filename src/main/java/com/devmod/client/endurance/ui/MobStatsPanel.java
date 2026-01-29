package com.devmod.client.endurance.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.EnduranceQuestRegistry.MobDifficultyPreset;
import com.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.devmod.endurance.config.EnduranceMobConfig;
import com.devmod.util.I18n;

/**
 * UI panel for editing stats of a selected mob for Endurance mode.
 * Features:
 * - Sliders for all mob stats (health, damage, speed, armor)
 * - Sliders for wave config (count per wave, scaling, max)
 * - Elite chance slider
 * - Difficulty preset buttons
 * - Real-time preview of changes
 */
@OnlyIn(Dist.CLIENT)
public class MobStatsPanel {

    // Slider ranges
    private static final double MAX_HEALTH = 500.0;
    private static final double MAX_DAMAGE = 100.0;
    private static final double MAX_SPEED = 2.0;
    private static final double MAX_ARMOR = 30.0;
    private static final int MAX_COUNT = 30;
    private static final double MAX_SCALING = 3.0;
    private static final int MAX_PER_WAVE = 50;
    private static final double MAX_ELITE_CHANCE = 1.0;

    private static final int SLIDER_SPACING = 24;
    private static final int SECTION_SPACING = 12;
    private static final int PADDING = 8;

    // Position and size
    private int x;
    private int y;
    private int width;
    private int height;

    // Current mob config
    @Nullable
    private ResourceLocation currentMobId;
    @Nullable
    private EnduranceMobConfig originalConfig;

    // Editable values
    private float health = 20f;
    private float damage = 3f;
    private float speed = 0.23f;
    private float armor = 0f;
    private int countPerWave = 4;
    private float countScaling = 1.2f;
    private int maxPerWave = 15;
    private float eliteChance = 0.15f;
    private MobTier tier = MobTier.MEDIUM;
    private MobDifficultyPreset preset = MobDifficultyPreset.STANDARD;

    // UI state
    private int activeSlider = -1;
    private int hoveredPreset = -1;
    private int scrollOffset = 0;

    // Callbacks
    @Nullable
    private Consumer<EnduranceMobConfig> onConfigChanged;

    // Presets with colors
    private static final List<PresetInfo> PRESETS = List.of(
        new PresetInfo(MobDifficultyPreset.SWARM, "Swarm", DesignTokens.Semantic.SUCCESS, "Many weak mobs"),
        new PresetInfo(MobDifficultyPreset.STANDARD, "Standard", DesignTokens.Text.SECONDARY, "Balanced stats"),
        new PresetInfo(MobDifficultyPreset.TANK, "Tank", DesignTokens.Accent.CYAN(), "High HP, low count"),
        new PresetInfo(MobDifficultyPreset.GLASS_CANNON, "Glass", DesignTokens.Semantic.ERROR, "High DMG, low HP"),
        new PresetInfo(MobDifficultyPreset.BOSS_STYLE, "Boss", DesignTokens.Accent.PURPLE(), "Very strong, single")
    );

    public MobStatsPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // ========== Setters ==========

    public void setPosition(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Set the mob config to edit.
     */
    public void setMobConfig(@Nullable EnduranceMobConfig config) {
        if (config == null) {
            this.currentMobId = null;
            this.originalConfig = null;
            return;
        }

        this.currentMobId = config.mobId();
        this.originalConfig = config;

        // Copy values
        this.health = config.baseHealth();
        this.damage = config.baseDamage();
        this.speed = config.baseSpeed();
        this.armor = config.baseArmor();
        this.countPerWave = config.baseCountPerWave();
        this.countScaling = config.countScalingPerWave();
        this.maxPerWave = config.maxPerWave();
        this.eliteChance = config.eliteChance();
        this.tier = config.tier();
        this.preset = config.difficultyPreset();
    }

    public void setOnConfigChanged(Consumer<EnduranceMobConfig> callback) {
        this.onConfigChanged = callback;
    }

    // ========== Getters ==========

    @Nullable
    public ResourceLocation getCurrentMobId() {
        return currentMobId;
    }

    /**
     * Get the modified config for the current mob.
     */
    @Nullable
    public EnduranceMobConfig getModifiedConfig() {
        if (currentMobId == null) {
            return null;
        }
        return new EnduranceMobConfig(
            currentMobId,
            true, // enabled (managed by MobListPanel)
            health, damage, speed, armor,
            countPerWave, countScaling, maxPerWave, eliteChance,
            tier, preset
        );
    }

    /**
     * Check if the current config has been modified from original.
     */
    public boolean hasChanges() {
        EnduranceMobConfig config = originalConfig;
        if (config == null) return false;
        return Math.abs(health - config.baseHealth()) > 0.01f
            || Math.abs(damage - config.baseDamage()) > 0.01f
            || Math.abs(speed - config.baseSpeed()) > 0.001f
            || Math.abs(armor - config.baseArmor()) > 0.01f
            || countPerWave != config.baseCountPerWave()
            || Math.abs(countScaling - config.countScalingPerWave()) > 0.01f
            || maxPerWave != config.maxPerWave()
            || Math.abs(eliteChance - config.eliteChance()) > 0.001f;
    }

    public boolean isSliderActive() {
        return activeSlider >= 0;
    }

    // ========== Actions ==========

    /**
     * Apply a difficulty preset to the current mob.
     */
    public void applyPreset(MobDifficultyPreset newPreset) {
        this.preset = newPreset;

        // Apply preset multipliers to original values
        EnduranceMobConfig config = originalConfig;
        if (config != null) {
            health = config.baseHealth() * newPreset.getHpMultiplier();
            damage = config.baseDamage() * newPreset.getDamageMultiplier();
            countPerWave = (int) Math.ceil(config.baseCountPerWave() * newPreset.getCountMultiplier());
        }

        notifyChange();
    }

    /**
     * Reset to original values.
     */
    public void resetToOriginal() {
        if (originalConfig != null) {
            setMobConfig(originalConfig);
        }
    }

    private void notifyChange() {
        if (onConfigChanged != null && currentMobId != null) {
            onConfigChanged.accept(getModifiedConfig());
        }
    }

    // ========== Rendering ==========

    public void render(GuiGraphics graphics, @Nonnull Font font, int mouseX, int mouseY) {
        // Background
        graphics.fill(x, y, x + width, y + height, DesignTokens.Surface.LEVEL_0);
        graphics.renderOutline(x, y, width, height, DesignTokens.Border.DEFAULT);

        if (currentMobId == null) {
            // Empty state
            String emptyText = I18n.translate("devmod.endurance.mob_editor.select_mob").getString();
            if (emptyText != null) {
                int textWidth = font.width(emptyText);
                graphics.drawString(font, emptyText, x + (width - textWidth) / 2, y + height / 2 - 4,
                    DesignTokens.Text.MUTED, false);
            }
            return;
        }

        int contentX = x + PADDING;
        int contentY = y + PADDING - scrollOffset;
        int contentWidth = width - PADDING * 2;

        // Mob name header - use local variables to satisfy null analysis
        ResourceLocation mobId = currentMobId;
        if (mobId == null) return; // Should never happen due to early return above
        EnduranceMobConfig config = originalConfig;
        String mobName = config != null ? config.getDisplayName() : mobId.getPath();
        graphics.drawString(font, mobName, contentX, contentY, DesignTokens.Text.PRIMARY, false);
        contentY += 14;

        // Tier indicator
        String tierText = "[" + tier.name() + "]";
        int tierColor = getTierColor(tier);
        graphics.drawString(font, tierText, contentX, contentY, tierColor, false);
        contentY += 18;

        // Preset buttons
        contentY = renderPresets(graphics, font, contentX, contentY, contentWidth, mouseX, mouseY);
        contentY += SECTION_SPACING;

        // Stats section header
        graphics.drawString(font, I18n.translate("devmod.endurance.mob_editor.section.stats").getString(),
            contentX, contentY, DesignTokens.Text.SECONDARY, false);
        contentY += 14;

        // Stats sliders
        contentY = renderSlider(graphics, font, contentX, contentY, contentWidth, 0,
            "Health", health, 1, MAX_HEALTH, "%.0f HP", mouseX, mouseY);
        contentY = renderSlider(graphics, font, contentX, contentY, contentWidth, 1,
            "Damage", damage, 0, MAX_DAMAGE, "%.1f", mouseX, mouseY);
        contentY = renderSlider(graphics, font, contentX, contentY, contentWidth, 2,
            "Speed", speed, 0.01f, MAX_SPEED, "%.2f", mouseX, mouseY);
        contentY = renderSlider(graphics, font, contentX, contentY, contentWidth, 3,
            "Armor", armor, 0, MAX_ARMOR, "%.0f", mouseX, mouseY);

        contentY += SECTION_SPACING;

        // Wave config section header
        graphics.drawString(font, I18n.translate("devmod.endurance.mob_editor.section.wave").getString(),
            contentX, contentY, DesignTokens.Text.SECONDARY, false);
        contentY += 14;

        // Wave config sliders
        contentY = renderSlider(graphics, font, contentX, contentY, contentWidth, 4,
            "Count/Wave", countPerWave, 1, MAX_COUNT, "%d", mouseX, mouseY);
        contentY = renderSlider(graphics, font, contentX, contentY, contentWidth, 5,
            "Scaling", countScaling, 1.0f, MAX_SCALING, "%.1fx", mouseX, mouseY);
        contentY = renderSlider(graphics, font, contentX, contentY, contentWidth, 6,
            "Max/Wave", maxPerWave, 1, MAX_PER_WAVE, "%d", mouseX, mouseY);
        renderSlider(graphics, font, contentX, contentY, contentWidth, 7,
            "Elite %", eliteChance * 100, 0, 100, "%.0f%%", mouseX, mouseY);

        // Modified indicator
        if (hasChanges()) {
            graphics.drawString(font, "*", x + width - 12, y + 4, DesignTokens.Semantic.WARNING, false);
        }
    }

    private int renderPresets(GuiGraphics graphics, @Nonnull Font font, int contentX, int contentY,
            int contentWidth, int mouseX, int mouseY) {

        int btnWidth = (contentWidth - (PRESETS.size() - 1) * 4) / PRESETS.size();
        int btnHeight = 24;

        hoveredPreset = -1;

        for (int i = 0; i < PRESETS.size(); i++) {
            PresetInfo info = PRESETS.get(i);
            int btnX = contentX + i * (btnWidth + 4);

            boolean isSelected = info.preset == this.preset;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + btnWidth
                && mouseY >= contentY && mouseY < contentY + btnHeight;

            if (isHovered) {
                hoveredPreset = i;
            }

            // Button background
            int bgColor = isSelected ? info.color : (isHovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1);
            graphics.fill(btnX, contentY, btnX + btnWidth, contentY + btnHeight, bgColor);

            // Border
            int borderColor = isSelected ? info.color : DesignTokens.Border.DEFAULT;
            graphics.renderOutline(btnX, contentY, btnWidth, btnHeight, borderColor);

            // Label (centered)
            @Nonnull String label = info.label;
            if (font.width(label) > btnWidth - 4) {
                label = Objects.requireNonNull(label.substring(0, 3));
            }
            int textColor = isSelected ? DesignTokens.Text.WHITE : DesignTokens.Text.PRIMARY;
            int textX = btnX + (btnWidth - font.width(label)) / 2;
            graphics.drawString(font, label, textX, contentY + 8, textColor, false);
        }

        return contentY + btnHeight + 4;
    }

    @SuppressWarnings("UnusedVariable") // mouseX/mouseY reserved for hover effects
    private int renderSlider(GuiGraphics graphics, @Nonnull Font font, int contentX, int contentY,
            int contentWidth, int sliderId, String label, double value, double min, double max,
            String format, int mouseX, int mouseY) {

        int labelWidth = 70;
        int valueWidth = 50;
        int sliderX = contentX + labelWidth;
        int sliderW = contentWidth - labelWidth - valueWidth - 8;
        int sliderY = contentY + 4;
        int sliderH = 8;

        // Label
        graphics.drawString(font, label, contentX, contentY + 2, DesignTokens.Text.PRIMARY, false);

        // Slider track
        graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, DesignTokens.Surface.LEVEL_1);

        // Slider fill
        double ratio = (value - min) / (max - min);
        int fillW = (int) (ratio * sliderW);
        graphics.fill(sliderX, sliderY, sliderX + fillW, sliderY + sliderH, DesignTokens.Accent.PRIMARY);

        // Slider handle
        int handleX = sliderX + fillW - 3;
        boolean isActive = activeSlider == sliderId;
        int handleColor = isActive ? DesignTokens.Text.WHITE : DesignTokens.Border.LIGHT;
        graphics.fill(handleX, sliderY - 2, handleX + 6, sliderY + sliderH + 2, handleColor);

        // Value display
        String valueStr;
        if (format.contains("d")) {
            valueStr = String.format(format, (int) value);
        } else {
            valueStr = String.format(format, value);
        }
        graphics.drawString(font, valueStr, sliderX + sliderW + 8, contentY + 2, DesignTokens.Text.SECONDARY, false);

        return contentY + SLIDER_SPACING;
    }

    private int getTierColor(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> DesignTokens.Text.MUTED;
            case EASY -> DesignTokens.Semantic.SUCCESS;
            case MEDIUM -> DesignTokens.Semantic.WARNING;
            case HARD -> DesignTokens.Semantic.ERROR;
            case ELITE -> DesignTokens.Accent.GOLD();
            case BOSS -> DesignTokens.Accent.PURPLE();
        };
    }

    // ========== Input Handling ==========

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || currentMobId == null) return false;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        // Check preset buttons
        if (hoveredPreset >= 0 && hoveredPreset < PRESETS.size()) {
            applyPreset(PRESETS.get(hoveredPreset).preset);
            return true;
        }

        // Check sliders
        int contentX = x + PADDING;
        int contentY = y + PADDING - scrollOffset + 14 + 18; // Skip header and tier
        contentY += 28 + SECTION_SPACING; // Skip presets
        contentY += 14; // Skip stats header

        int labelWidth = 70;
        int valueWidth = 50;
        int contentWidth = width - PADDING * 2;
        int sliderX = contentX + labelWidth;
        int sliderW = contentWidth - labelWidth - valueWidth - 8;

        // Check each slider (0-7)
        for (int i = 0; i < 8; i++) {
            int sliderY = contentY + i * SLIDER_SPACING + 4;
            if (i >= 4) {
                sliderY += SECTION_SPACING + 14; // Account for wave section header
            }

            if (mouseX >= sliderX && mouseX <= sliderX + sliderW
                && mouseY >= sliderY - 4 && mouseY <= sliderY + 12) {
                activeSlider = i;
                updateSliderValue(mouseX, sliderX, sliderW);
                return true;
            }
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeSlider >= 0) {
            activeSlider = -1;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeSlider >= 0 && currentMobId != null) {
            int contentX = x + PADDING;
            int labelWidth = 70;
            int valueWidth = 50;
            int contentWidth = width - PADDING * 2;
            int sliderX = contentX + labelWidth;
            int sliderW = contentWidth - labelWidth - valueWidth - 8;

            updateSliderValue(mouseX, sliderX, sliderW);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        // Calculate content height and max scroll
        int contentHeight = calculateContentHeight();
        int maxScroll = Math.max(0, contentHeight - height + PADDING * 2);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (deltaY * 10)));
        return true;
    }

    private int calculateContentHeight() {
        // Header + tier + presets + stats header + 4 sliders + wave header + 4 sliders
        return 14 + 18 + 32 + SECTION_SPACING + 14 + (4 * SLIDER_SPACING) + SECTION_SPACING + 14 + (4 * SLIDER_SPACING);
    }

    private void updateSliderValue(double mouseX, int sliderX, int sliderW) {
        double ratio = Mth.clamp((mouseX - sliderX) / sliderW, 0, 1);

        switch (activeSlider) {
            case 0 -> health = (float) (1 + ratio * (MAX_HEALTH - 1));
            case 1 -> damage = (float) (ratio * MAX_DAMAGE);
            case 2 -> speed = (float) (0.01 + ratio * (MAX_SPEED - 0.01));
            case 3 -> armor = (float) (ratio * MAX_ARMOR);
            case 4 -> countPerWave = (int) (1 + ratio * (MAX_COUNT - 1));
            case 5 -> countScaling = (float) (1.0 + ratio * (MAX_SCALING - 1.0));
            case 6 -> maxPerWave = (int) (1 + ratio * (MAX_PER_WAVE - 1));
            case 7 -> eliteChance = (float) (ratio * MAX_ELITE_CHANCE);
        }

        notifyChange();
    }

    // ========== Helper Classes ==========

    private record PresetInfo(@Nonnull MobDifficultyPreset preset, @Nonnull String label, int color, @Nonnull String description) {}
}
