package com.devmod.client.combat;

import com.devmod.combat.signature.SoulImprint;
import com.devmod.combat.signature.WeaponTrait;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client-side tooltip renderer for Signature Weapons.
 *
 * Displays the weapon's Soul Imprint data including:
 * - Evolved name based on stage
 * - Unlocked traits with effects
 * - Stat progress toward next traits
 * - Total kills and damage dealt
 */
@OnlyIn(Dist.CLIENT)
public class SignatureWeaponTooltip {

    // Tier colors for evolution stages
    private static final int STAGE_0_COLOR = 0xAAAAAA; // Common (gray)
    private static final int STAGE_1_COLOR = 0xFFFFFF; // Personal (white)
    private static final int STAGE_2_COLOR = 0x55FF55; // Enhanced (green)
    private static final int STAGE_3_COLOR = 0x5555FF; // Legendary (blue)
    private static final int STAGE_4_COLOR = 0xFF55FF; // Mythic (magenta)

    /**
     * Build tooltip lines for a weapon with a Soul Imprint.
     */
    public static List<Component> buildTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        SoulImprint imprint = SoulImprint.loadFromItem(stack);
        if (imprint == null) {
            return lines;
        }

        // Add separator
        lines.add(Component.empty());
        lines.add(Component.literal("--- Soul Imprint ---")
            .withStyle(Style.EMPTY.withColor(getStageColor(imprint.getEvolutionStage())).withItalic(true)));

        // Owner info
        if (imprint.getOwnerName() != null) {
            lines.add(Component.literal("  Bound to: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(imprint.getOwnerName())
                    .withStyle(ChatFormatting.GOLD)));
        }

        // Evolution stage
        String stageName = getStageName(imprint.getEvolutionStage());
        lines.add(Component.literal("  Stage: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(stageName)
                .withStyle(Style.EMPTY.withColor(getStageColor(imprint.getEvolutionStage())))));

        // Stats summary
        lines.add(Component.literal("  Total Kills: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.format("%,d", imprint.getTotalKills()))
                .withStyle(ChatFormatting.RED)));

        if (imprint.getTotalDamage() > 0) {
            lines.add(Component.literal("  Total Damage: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%,d", imprint.getTotalDamage()))
                    .withStyle(ChatFormatting.GOLD)));
        }

        // Unlocked traits
        Set<WeaponTrait> traits = imprint.getUnlockedTraits();
        if (!traits.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal("  Traits:")
                .withStyle(ChatFormatting.LIGHT_PURPLE));

            for (WeaponTrait trait : traits) {
                MutableComponent traitLine = Component.literal("    ");
                traitLine.append(Component.literal("\u2726 ") // Star symbol
                    .withStyle(Style.EMPTY.withColor(trait.getColor())));
                traitLine.append(Component.literal(trait.getAdjective())
                    .withStyle(Style.EMPTY.withColor(trait.getColor()).withBold(true)));
                traitLine.append(Component.literal(" - ")
                    .withStyle(ChatFormatting.GRAY));
                traitLine.append(Component.literal(trait.getEffect().getDescription())
                    .withStyle(ChatFormatting.WHITE));
                lines.add(traitLine);
            }
        }

        // Progress toward next trait (if any stat is close)
        Component progressLine = buildProgressLine(imprint);
        if (progressLine != null) {
            lines.add(Component.empty());
            lines.add(progressLine);
        }

        return lines;
    }

    /**
     * Build progress line for the closest trait to unlocking.
     */
    private static Component buildProgressLine(SoulImprint imprint) {
        float bestProgress = 0f;
        SoulImprint.ImprintStat bestStat = null;

        for (SoulImprint.ImprintStat stat : SoulImprint.ImprintStat.values()) {
            float progress = imprint.getTraitProgress(stat);
            // Only show progress for incomplete traits (progress < 1.0)
            if (progress < 1.0f && progress > bestProgress) {
                bestProgress = progress;
                bestStat = stat;
            }
        }

        if (bestStat == null || bestProgress < 0.1f) {
            return null; // No significant progress
        }

        int current = imprint.getStat(bestStat);
        int required = bestStat.traitThreshold;
        int percent = (int) (bestProgress * 100);

        return Component.literal("  Next: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(bestStat.displayName)
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(String.format(" [%d/%d] %d%%", current, required, percent))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Get evolution stage display name.
     */
    private static String getStageName(int stage) {
        return switch (stage) {
            case 0 -> "Unbound";
            case 1 -> "Personal";
            case 2 -> "Enhanced";
            case 3 -> "Legendary";
            case 4 -> "Mythic";
            default -> "Unknown";
        };
    }

    /**
     * Get color for evolution stage.
     */
    private static int getStageColor(int stage) {
        return switch (stage) {
            case 0 -> STAGE_0_COLOR;
            case 1 -> STAGE_1_COLOR;
            case 2 -> STAGE_2_COLOR;
            case 3 -> STAGE_3_COLOR;
            case 4 -> STAGE_4_COLOR;
            default -> STAGE_0_COLOR;
        };
    }

    /**
     * Check if an item has a Soul Imprint.
     */
    public static boolean hasSoulImprint(ItemStack stack) {
        return SoulImprint.hasImprint(stack);
    }
}
