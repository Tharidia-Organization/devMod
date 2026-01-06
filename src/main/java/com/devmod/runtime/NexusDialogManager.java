package com.devmod.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages dialog content for the Nexus avatar.
 * Generates personalized responses based on player context.
 */
public final class NexusDialogManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusDialogManager.class);

    public static final NexusDialogManager INSTANCE = new NexusDialogManager();

    private NexusDialogManager() {}

    /**
     * Types of dialogs the avatar can present.
     */
    public enum DialogType {
        GREETING,       // Initial greeting
        QUEST_INFO,     // Information about quests
        STATS,          // Player statistics
        TIPS,           // Gameplay tips
        ZONE_GUIDE,     // Guide to Nexus zones
        FAREWELL        // Goodbye message
    }

    /**
     * A selectable option in a dialog.
     */
    public record DialogOption(
        @Nonnull String id,
        @Nonnull String label,
        @Nonnull String icon,
        @Nonnull DialogType nextDialog
    ) {
        public DialogOption {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(nextDialog, "nextDialog");
        }
    }

    /**
     * A complete dialog response from the avatar.
     */
    public record DialogResponse(
        @Nonnull String speakerName,
        @Nonnull List<String> lines,
        @Nonnull List<DialogOption> options
    ) {
        public DialogResponse {
            Objects.requireNonNull(speakerName, "speakerName");
            Objects.requireNonNull(lines, "lines");
            Objects.requireNonNull(options, "options");
        }
    }

    /**
     * Get the greeting dialog for a player.
     */
    @Nonnull
    public DialogResponse getGreeting(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();
        String speakerName = "NEXA";

        // Personalized greeting based on player progress
        if (ctx.isFirstVisit()) {
            lines.add("Welcome to the Nexus, " + ctx.playerName() + "!");
            lines.add("I am NEXA, your guide to this realm.");
            lines.add("This hub connects to various testing zones.");
            lines.add("Feel free to explore or ask me anything!");
        } else if (ctx.isNewcomer()) {
            lines.add("Hello again, " + ctx.playerName() + "!");
            lines.add("Ready to start your first quest?");
            lines.add("The Combat zone awaits challengers.");
        } else if (ctx.isVeteran()) {
            lines.add("Welcome back, " + ctx.getPlayerTitle() + " " + ctx.playerName() + "!");
            lines.add("You've completed " + ctx.totalQuestsCompleted() + " quests.");
            if (ctx.highestWaveReached() > 0) {
                lines.add("Your record: Wave " + ctx.highestWaveReached() + "!");
            }
            lines.add("What brings you here today?");
        } else {
            lines.add("Hello, " + ctx.playerName() + "!");
            lines.add("Good to see you in the Nexus.");
            lines.add("How can I help you today?");
        }

        // Add context-specific notes
        if (ctx.hasActiveQuest()) {
            lines.add("");
            lines.add("[You have an active quest in progress]");
        }
        if (ctx.isInParty()) {
            lines.add("");
            lines.add("[Party: " + ctx.partySize() + " members]");
        }

        return new DialogResponse(speakerName, lines, getMainOptions(ctx));
    }

    /**
     * Get quest information dialog.
     */
    @Nonnull
    public DialogResponse getQuestInfo(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Endurance Quests are wave-based challenges.");
        lines.add("");
        lines.add("How to start:");
        lines.add("1. Create or join a party");
        lines.add("2. Select your kit and mob type");
        lines.add("3. The leader starts the quest");
        lines.add("");
        lines.add("Survive as many waves as you can!");
        lines.add("Defeating bosses grants bonus rewards.");

        if (ctx.totalQuestsCompleted() > 0) {
            lines.add("");
            lines.add("Your stats: " + ctx.totalQuestsCompleted() + " quests, " +
                     ctx.totalWavesCleared() + " waves cleared");
        }

        return new DialogResponse("NEXA", lines, getBackOption());
    }

    /**
     * Get player statistics dialog.
     */
    @Nonnull
    public DialogResponse getStats(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Your Statistics, " + ctx.playerName() + ":");
        lines.add("");
        lines.add("Title: " + ctx.getPlayerTitle());
        lines.add("Quests Completed: " + ctx.totalQuestsCompleted());
        lines.add("Waves Cleared: " + ctx.totalWavesCleared());
        lines.add("Highest Wave: " + ctx.highestWaveReached());
        lines.add("Total Kills: " + ctx.totalKills());
        lines.add("");
        lines.add("Season Tier: " + ctx.seasonTier());
        lines.add("Total XP: " + ctx.totalXp());

        if (ctx.achievementsUnlocked() > 0) {
            lines.add("Achievements: " + ctx.achievementsUnlocked());
        }

        return new DialogResponse("NEXA", lines, getBackOption());
    }

    /**
     * Get gameplay tips dialog.
     */
    @Nonnull
    public DialogResponse getTips(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Tips for Success:");
        lines.add("");

        // Tailor tips to player experience
        if (ctx.isNewcomer()) {
            lines.add("- Start with easier mob types (Zombies)");
            lines.add("- Learn enemy attack patterns");
            lines.add("- Don't rush - pace yourself");
            lines.add("- Use the environment for cover");
        } else if (ctx.totalQuestsCompleted() < 20) {
            lines.add("- Try different kits to find your style");
            lines.add("- Headshots deal bonus damage");
            lines.add("- Build combos for style points");
            lines.add("- Party play shares aggro");
        } else {
            lines.add("- Master dodge timing for bosses");
            lines.add("- Optimize your gear loadout");
            lines.add("- Coordinate with party members");
            lines.add("- Aim for flawless wave bonuses");
        }

        lines.add("");
        lines.add("Good luck out there!");

        return new DialogResponse("NEXA", lines, getBackOption());
    }

    /**
     * Get zone guide dialog.
     */
    @Nonnull
    public DialogResponse getZoneGuide(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Nexus Zones:");
        lines.add("");
        lines.add("[Red] COMBAT - Test fighting mechanics");
        lines.add("[Orange] ARENA - Endurance quest arenas");
        lines.add("[Blue] UI - UI testing laboratory");
        lines.add("[Green] TELEMETRY - Data & metrics");
        lines.add("[Yellow] SHOWCASE - Item displays");
        lines.add("[Purple] INTEGRATION - Mod compat tests");
        lines.add("[Cyan] SANDBOX - Free building area");
        lines.add("[Gray] MECHANICS - Redstone & tech");
        lines.add("");
        lines.add("Use: /devmod nexus tp <zone>");

        return new DialogResponse("NEXA", lines, getBackOption());
    }

    /**
     * Get farewell dialog.
     */
    @Nonnull
    public DialogResponse getFarewell(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        if (ctx.isVeteran()) {
            lines.add("Until next time, " + ctx.getPlayerTitle() + "!");
            lines.add("May your combos be endless.");
        } else if (ctx.isNewcomer()) {
            lines.add("Good luck on your journey!");
            lines.add("Return anytime for guidance.");
        } else {
            lines.add("Farewell, " + ctx.playerName() + "!");
            lines.add("See you in the arena!");
        }

        return new DialogResponse("NEXA", lines, List.of());
    }

    /**
     * Get a dialog response by type.
     */
    @Nonnull
    public DialogResponse getDialog(@Nonnull DialogType type, @Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ctx, "ctx");

        return switch (type) {
            case GREETING -> getGreeting(ctx);
            case QUEST_INFO -> getQuestInfo(ctx);
            case STATS -> getStats(ctx);
            case TIPS -> getTips(ctx);
            case ZONE_GUIDE -> getZoneGuide(ctx);
            case FAREWELL -> getFarewell(ctx);
        };
    }

    /**
     * Get the main menu options.
     */
    @Nonnull
    private List<DialogOption> getMainOptions(@Nonnull NexusDialogContext ctx) {
        List<DialogOption> options = new ArrayList<>();

        options.add(new DialogOption("quest_info", "Tell me about quests", "\u2694", DialogType.QUEST_INFO));
        options.add(new DialogOption("stats", "Show my statistics", "\uD83D\uDCCA", DialogType.STATS));
        options.add(new DialogOption("tips", "Give me tips", "\uD83D\uDCA1", DialogType.TIPS));
        options.add(new DialogOption("zones", "Zone guide", "\uD83D\uDDFA", DialogType.ZONE_GUIDE));
        options.add(new DialogOption("farewell", "Goodbye", "\uD83D\uDC4B", DialogType.FAREWELL));

        return options;
    }

    /**
     * Get back-to-main option.
     */
    @Nonnull
    private List<DialogOption> getBackOption() {
        return List.of(
            new DialogOption("back", "Back", "\u2B05", DialogType.GREETING),
            new DialogOption("farewell", "Goodbye", "\uD83D\uDC4B", DialogType.FAREWELL)
        );
    }
}
