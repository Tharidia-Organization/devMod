package com.devmod.client.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.network.chat.Component;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.config.Config;
import com.devmod.damage.DamageBreakdown;
import com.devmod.util.I18n;
import com.devmod.util.ThreadLocalPool;

public final class ImpactHudContentBuilder {

    // P2: Thread-local pools for ArrayList reuse in render loop
    private static final ThreadLocalPool<ArrayList<HudSection>> SECTION_LIST_POOL =
        ThreadLocalPool.ofList(ArrayList::new, 4);
    private static final ThreadLocalPool<ArrayList<HudLine>> LINE_LIST_POOL =
        ThreadLocalPool.ofList(ArrayList::new, 8);

    private ImpactHudContentBuilder() {}

    public enum LineType {
        NORMAL,
        VALUE,
        FORMULA,
        MUTED,
        HIGHLIGHT
    }

    public enum Spacing {
        NONE,
        SMALL,
        SECTION,
        LARGE
    }

    public record NumberFormat(String valueFormat, String multiplierFormat) {
        public String formatValue(float value) {
            return String.format(valueFormat, value);
        }

        public String formatMultiplier(float value) {
            return String.format(multiplierFormat, value);
        }
    }

    public static final class Colors {
        public static final int TITLE = DesignTokens.Accent.PRIMARY;           // Cyan
        public static final int NORMAL = DesignTokens.Text.PRIMARY;            // White
        public static final int VALUE = DesignTokens.Semantic.SUCCESS;         // Green
        public static final int FORMULA = DesignTokens.Semantic.WARNING;       // Gold
        public static final int MUTED = DesignTokens.Text.MUTED;               // Gray
        public static final int HIGHLIGHT = DesignTokens.Semantic.ERROR;       // Red
        public static final int REDUCTION = DesignTokens.Semantic.ERROR_MUTED; // Light red
        public static final int AMPLIFIED = DesignTokens.Semantic.SUCCESS_MUTED; // Light green
        public static final int HIGHLIGHT_SHADOW = OverlayTheme.Impact.HIGHLIGHT_SHADOW;
        public static final int CALCULATED_SHADOW = OverlayTheme.Impact.CALCULATED_SHADOW;

        private Colors() {}
    }

    public record HudLine(Component text, int color, LineType type, Spacing spacingAfter, int shadowColor) {
        public static final int NO_SHADOW = -1;

        public HudLine(Component text, int color, LineType type, Spacing spacingAfter) {
            this(text, color, type, spacingAfter, NO_SHADOW);
        }

        public HudLine(Component text, int color, LineType type) {
            this(text, color, type, Spacing.NONE, NO_SHADOW);
        }

        public boolean hasShadow() {
            return shadowColor != NO_SHADOW;
        }

        public HudLine withSpacing(Spacing spacing) {
            return new HudLine(text, color, type, spacing, shadowColor);
        }
    }

    public record HudSection(Component title, List<HudLine> lines, boolean drawSeparator, Spacing titleSpacing) {
        public HudSection(Component title, List<HudLine> lines, boolean drawSeparator, Spacing titleSpacing) {
            this.title = title;
            this.lines = List.copyOf(lines);
            this.drawSeparator = drawSeparator;
            this.titleSpacing = titleSpacing;
        }
    }

    public static List<HudSection> buildContent(ImpactData data, NumberFormat format) {
        // P2: Use pooled list, caller receives copy since HudSection uses List.copyOf internally
        ArrayList<HudSection> sections = SECTION_LIST_POOL.acquire();
        try {
            sections.add(buildMainSection(data, format));
            buildModSection(data, format).ifPresent(sections::add);
            return List.copyOf(sections);
        } finally {
            SECTION_LIST_POOL.release(sections);
        }
    }

    public static Optional<HudSection> buildHistorySection(ImpactData data, NumberFormat format) {
        if (data == null || data.attackerUUID == null) {
            return Optional.empty();
        }

        boolean showHistory = isHistoryEnabled();
        boolean showDps = isDpsEnabled();
        if (!showHistory && !showDps) {
            return Optional.empty();
        }

        List<ImpactData> recent = showHistory
            ? ImpactHistory.getRecent(data.attackerUUID, ImpactHistory.getMaxEntries())
            : List.of();
        float dps = showDps ? ImpactDpsTracker.getCurrentDps(data.attackerUUID) : 0f;

        List<HudLine> historyLines = new ArrayList<>();
        if (showHistory) {
            int added = 0;
            for (ImpactData impact : recent) {
                if (impact == null) {
                    continue;
                }
                if (impact == data && recent.size() > 1) {
                    continue;
                }
                float damage = impact.hasActualDamage() ? impact.getActualDamageDealt() : impact.breakdown.finalDamage;
                String partKey = "devmod.bodypart." + impact.bodyPart.name().toLowerCase(Locale.ROOT);
                Component line = I18n.translate("devmod.hud.history_line",
                    impact.targetName,
                    I18n.translate(partKey),
                    format.formatValue(damage));
                historyLines.add(new HudLine(line, Colors.MUTED, LineType.MUTED, Spacing.NONE));
                if (++added >= ImpactHistory.getMaxEntries()) {
                    break;
                }
            }
        }

        List<HudLine> lines = new ArrayList<>();
        if (dps > 0.05f) {
            lines.add(new HudLine(
                I18n.translate("devmod.hud.dps", format.formatValue(dps)),
                Colors.VALUE,
                LineType.VALUE,
                historyLines.isEmpty() ? Spacing.NONE : Spacing.SMALL
            ));
        }
        lines.addAll(historyLines);

        if (lines.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new HudSection(
            I18n.translate("devmod.hud.history_title"),
            lines,
            false,
            Spacing.SMALL
        ));
    }

    private static boolean isHistoryEnabled() {
        try {
            return Config.IMPACT_HUD_HISTORY_ENABLED.get();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isDpsEnabled() {
        try {
            return Config.IMPACT_HUD_DPS_ENABLED.get();
        } catch (Exception ignored) {
            return true;
        }
    }

    public static HudSection buildMainSection(ImpactData data, NumberFormat format) {
        // P2: Use pooled list - HudSection constructor calls List.copyOf so this is safe
        ArrayList<HudLine> lines = LINE_LIST_POOL.acquire();
        try {
            return buildMainSectionImpl(data, format, lines);
        } finally {
            LINE_LIST_POOL.release(lines);
        }
    }

    private static HudSection buildMainSectionImpl(ImpactData data, NumberFormat format, List<HudLine> lines) {

        Component partHit = Objects.requireNonNull(I18n.translate("devmod.hud.part_hit"), "partHitLabel")
            .append(Objects.requireNonNull(I18n.literal(": "), "partHitSeparator"))
            .append(Objects.requireNonNull(
                Objects.requireNonNull(I18n.literal(data.bodyPart.name()), "partHitName")
                    .withStyle(style -> style.withColor(data.getBodyPartColor() & DesignTokens.Mask.RGB)),
                "partHitNameStyled"))
            .append(Objects.requireNonNull(I18n.literal(" ("), "partHitOpen"))
            .append(Objects.requireNonNull(I18n.translate("devmod.hud.modifier"), "partHitModifier"))
            .append(Objects.requireNonNull(I18n.literal(": "), "partHitModifierSeparator"))
            .append(Objects.requireNonNull(
                Objects.requireNonNull(I18n.literal("x" + format.formatMultiplier(data.bodyPartMultiplier)),
                    "partHitMultiplier")
                    .withStyle(style -> style.withColor(Colors.VALUE & DesignTokens.Mask.RGB)),
                "partHitMultiplierStyled"))
            .append(Objects.requireNonNull(I18n.literal(")"), "partHitClose"));
        lines.add(new HudLine(partHit, Colors.NORMAL, LineType.NORMAL, Spacing.SMALL));

        lines.add(new HudLine(
            I18n.translate("devmod.hud.source", data.getFormattedAttackSourceComponent()),
            Colors.MUTED,
            LineType.MUTED,
            Spacing.SECTION
        ));

        DamageBreakdown bd = data.breakdown;
        // P2: Use pooled list for breakdown lines
        ArrayList<HudLine> breakdownLines = LINE_LIST_POOL.acquire();
        try {
            buildBreakdownLines(breakdownLines, bd, format);
            lines.addAll(breakdownLines);
        } finally {
            LINE_LIST_POOL.release(breakdownLines);
        }

        lines.add(new HudLine(
            I18n.translate("devmod.hud.formula", bd.getFormulaString()),
            Colors.FORMULA,
            LineType.FORMULA,
            Spacing.SMALL
        ));

        if (data.hasActualDamage()) {
            lines.add(new HudLine(
                I18n.translate("devmod.hud.actual_damage", format.formatValue(data.getActualDamageDealt())),
                Colors.HIGHLIGHT,
                LineType.HIGHLIGHT,
                Spacing.SMALL,
                Colors.HIGHLIGHT_SHADOW
            ));

            lines.add(new HudLine(
                I18n.translate("devmod.hud.health_change",
                    format.formatValue(data.getHealthBefore()),
                    format.formatValue(data.getHealthAfter())),
                Colors.MUTED,
                LineType.MUTED,
                Spacing.NONE
            ));

            float reduction = data.getDamageReduction();
            if (Math.abs(reduction) > 0.1f) {
                if (reduction > 0) {
                    lines.add(new HudLine(
                        I18n.translate("devmod.hud.armor_reduced", format.formatValue(reduction)),
                        Colors.REDUCTION,
                        LineType.MUTED,
                        Spacing.NONE
                    ));
                } else {
                    lines.add(new HudLine(
                        I18n.translate("devmod.hud.damage_amplified", format.formatValue(-reduction)),
                        Colors.AMPLIFIED,
                        LineType.MUTED,
                        Spacing.NONE
                    ));
                }
            }

            // BUG-003 FIX: Detailed reduction breakdown
            if (data.hasReductionBreakdown()) {
                addReductionBreakdownLines(lines, data, format);
            }
        } else {
            lines.add(new HudLine(
                I18n.translate("devmod.hud.calculated_damage", format.formatValue(bd.finalDamage)),
                Colors.VALUE,
                LineType.HIGHLIGHT,
                Spacing.NONE,
                Colors.CALCULATED_SHADOW
            ));
        }

        return new HudSection(
            I18n.translate("devmod.hud.impact_analysis_title"),
            lines,
            true,
            Spacing.SMALL
        );
    }

    public static Optional<HudSection> buildModSection(ImpactData data, NumberFormat format) {
        if (!data.hasPehkuiModification() && !data.isEpicFightCombat()) {
            return Optional.empty();
        }

        List<HudLine> lines = new ArrayList<>();

        if (data.isEpicFightCombat()) {
            String animName = data.getEpicFightAnimationName();
            if (animName != null && !animName.isEmpty()) {
                lines.add(new HudLine(
                    I18n.translate("devmod.hud.epic_fight_animation", animName),
                    Colors.MUTED,
                    LineType.MUTED,
                    Spacing.NONE
                ));
            } else {
                lines.add(new HudLine(
                    I18n.translate("devmod.hud.epic_fight_combat"),
                    Colors.MUTED,
                    LineType.MUTED,
                    Spacing.NONE
                ));
            }
        }

        if (data.hasPehkuiModification()) {
            float scale = data.pehkuiVisualScale != null ? data.pehkuiVisualScale : 1.0f;
            lines.add(new HudLine(
                I18n.translate("devmod.hud.pehkui_scale", format.formatValue(scale)),
                Colors.MUTED,
                LineType.MUTED,
                Spacing.NONE
            ));
        }

        if (lines.size() == 2) {
            HudLine last = lines.get(1);
            lines.set(1, last.withSpacing(Spacing.SMALL));
        }

        return Optional.of(new HudSection(
            I18n.translate("devmod.hud.mod_specifics"),
            lines,
            false,
            Spacing.LARGE
        ));
    }

    /**
     * P2: Helper to build damage breakdown lines into a pooled list.
     */
    private static void buildBreakdownLines(List<HudLine> breakdownLines, DamageBreakdown bd, NumberFormat format) {
        breakdownLines.add(new HudLine(
            I18n.translate("devmod.hud.base_weapon_damage", format.formatValue(bd.baseWeaponDamage)),
            Colors.NORMAL,
            LineType.NORMAL,
            Spacing.NONE
        ));

        for (DamageBreakdown.EnchantBonus eb : bd.enchantBonuses) {
            if (eb.bonus() > 0) {
                breakdownLines.add(new HudLine(
                    I18n.translate("devmod.hud.enchant", eb.name(), format.formatValue(eb.bonus())),
                    Colors.VALUE,
                    LineType.VALUE,
                    Spacing.NONE
                ));
            }
        }

        if (bd.pehkuiSizeBonus > 0) {
            breakdownLines.add(new HudLine(
                I18n.translate("devmod.hud.pehkui_size_bonus", format.formatValue(bd.pehkuiSizeBonus)),
                Colors.VALUE,
                LineType.VALUE,
                Spacing.NONE
            ));
        }

        if (!breakdownLines.isEmpty()) {
            int lastIndex = breakdownLines.size() - 1;
            HudLine last = breakdownLines.get(lastIndex);
            breakdownLines.set(lastIndex, last.withSpacing(Spacing.SECTION));
        }
    }

    /**
     * Adds detailed reduction breakdown lines (BUG-003 FIX).
     * Shows armor, enchantment/protection, and shield block reductions separately.
     */
    private static void addReductionBreakdownLines(List<HudLine> lines, ImpactData data, NumberFormat format) {
        float armor = data.getArmorReduction();
        float enchant = data.getEnchantmentReduction();
        float blocked = data.getBlockedDamage();

        if (armor > 0.01f) {
            lines.add(new HudLine(
                I18n.translate("devmod.hud.reduction_armor", format.formatValue(armor)),
                Colors.REDUCTION,
                LineType.MUTED,
                Spacing.NONE
            ));
        }

        if (enchant > 0.01f) {
            lines.add(new HudLine(
                I18n.translate("devmod.hud.reduction_protection", format.formatValue(enchant)),
                Colors.REDUCTION,
                LineType.MUTED,
                Spacing.NONE
            ));
        }

        if (blocked > 0.01f) {
            lines.add(new HudLine(
                I18n.translate("devmod.hud.reduction_blocked", format.formatValue(blocked)),
                Colors.REDUCTION,
                LineType.MUTED,
                Spacing.NONE
            ));
        }
    }
}
