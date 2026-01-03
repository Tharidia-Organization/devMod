package com.devmod.client.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.combat.CombatColors;
import com.devmod.combat.signature.SoulImprint;
import com.devmod.combat.signature.WeaponTrait;
import com.devmod.shared.SharedColorTokens;

@OnlyIn(Dist.CLIENT)
public class SignatureWeaponTooltip {

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
                .withStyle(SharedColorTokens.Chat.GRAY)
                .append(Component.literal(imprint.getOwnerName())
                    .withStyle(SharedColorTokens.Chat.GOLD)));
        }

        // Evolution stage
        String stageName = getStageName(imprint.getEvolutionStage());
        lines.add(Component.literal("  Stage: ")
            .withStyle(SharedColorTokens.Chat.GRAY)
            .append(Component.literal(stageName)
                .withStyle(Style.EMPTY.withColor(getStageColor(imprint.getEvolutionStage())))));

        // Stats summary
        lines.add(Component.literal("  Total Kills: ")
            .withStyle(SharedColorTokens.Chat.GRAY)
            .append(Component.literal(String.format("%,d", imprint.getTotalKills()))
                .withStyle(SharedColorTokens.Chat.RED)));

        if (imprint.getTotalDamage() > 0) {
            lines.add(Component.literal("  Total Damage: ")
                .withStyle(SharedColorTokens.Chat.GRAY)
                .append(Component.literal(String.format("%,d", imprint.getTotalDamage()))
                    .withStyle(SharedColorTokens.Chat.GOLD)));
        }

        // Unlocked traits
        Set<WeaponTrait> traits = imprint.getUnlockedTraits();
        if (!traits.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal("  Traits:")
                .withStyle(SharedColorTokens.Chat.LIGHT_PURPLE));

            for (WeaponTrait trait : traits) {
                MutableComponent traitLine = Component.literal("    ");
                traitLine.append(Component.literal("\u2726 ") // Star symbol
                    .withStyle(Style.EMPTY.withColor(trait.getColor())));
                traitLine.append(Component.literal(trait.getAdjective())
                    .withStyle(Style.EMPTY.withColor(trait.getColor()).withBold(true)));
                traitLine.append(Component.literal(" - ")
                    .withStyle(SharedColorTokens.Chat.GRAY));
                traitLine.append(Component.literal(trait.getEffect().getDescription())
                    .withStyle(SharedColorTokens.Chat.WHITE));
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
    @Nullable
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
            .withStyle(SharedColorTokens.Chat.GRAY)
            .append(Component.literal(bestStat.displayName)
                .withStyle(SharedColorTokens.Chat.YELLOW))
            .append(Component.literal(String.format(" [%d/%d] %d%%", current, required, percent))
                .withStyle(SharedColorTokens.Chat.DARK_GRAY));
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
            case 0 -> CombatColors.ImprintStage.OWNER;
            case 1 -> CombatColors.Text.PRIMARY;
            case 2 -> CombatColors.ImprintStage.ENHANCED;
            case 3 -> CombatColors.ImprintStage.LEGENDARY;
            case 4 -> CombatColors.ImprintStage.ASCENDED;
            default -> CombatColors.ImprintStage.OWNER;
        };
    }

    /**
     * Check if an item has a Soul Imprint.
     */
    public static boolean hasSoulImprint(ItemStack stack) {
        return SoulImprint.hasImprint(stack);
    }
}
