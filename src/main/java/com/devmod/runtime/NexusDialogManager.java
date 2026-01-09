package com.devmod.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;


/**
 * Manages dialog content for the Nexus avatar.
 * Generates personalized responses based on player context.
 */
public final class NexusDialogManager {
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
        FAREWELL,       // Goodbye message

        // === Manual System ===
        MANUAL_INDEX,           // Manual main index
        MANUAL_CLONE,           // Clone module overview
        MANUAL_CLONE_BIOSCANNER,// Bioscanner item guide
        MANUAL_CLONE_TELEPAD,   // Telepad block guide
        MANUAL_CLONE_NEUROCELL, // Neurocell block guide
        MANUAL_CLONE_REFORMER,  // Reformer block guide
        MANUAL_CLONE_ENTITY     // Player Clone entity guide
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

        return new DialogResponse(speakerName, lines, getMainOptions());
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

    // ==========================================================================
    // MANUAL SYSTEM
    // ==========================================================================

    /**
     * Get manual index - main manual menu.
     */
    @Nonnull
    public DialogResponse getManualIndex(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("DevMod Manual");
        lines.add("");
        lines.add("Select a topic to learn more:");
        lines.add("");
        lines.add("The manual contains guides for all DevMod");
        lines.add("systems, blocks, items, and entities.");

        List<DialogOption> options = new ArrayList<>();
        options.add(new DialogOption("manual_clone", "Clone System", "\uD83E\uDDEC", DialogType.MANUAL_CLONE));
        options.add(new DialogOption("back", "Back to menu", "\u2B05", DialogType.GREETING));

        return new DialogResponse("NEXA", lines, options);
    }

    /**
     * Get Clone module overview.
     */
    @Nonnull
    public DialogResponse getManualClone(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Clone System Overview");
        lines.add("");
        lines.add("The Clone system allows you to create");
        lines.add("entity clones that follow and fight for you.");
        lines.add("");
        lines.add("Workflow:");
        lines.add("1. Use BIOSCANNER to scan an entity");
        lines.add("2. Place data in NEUROCELL to process");
        lines.add("3. Connect NEUROCELL to REFORMER via NEUROLINK");
        lines.add("4. REFORMER spawns the clone!");
        lines.add("");
        lines.add("Select a component to learn more:");

        List<DialogOption> options = new ArrayList<>();
        options.add(new DialogOption("bioscanner", "Bioscanner", "\uD83D\uDD2C", DialogType.MANUAL_CLONE_BIOSCANNER));
        options.add(new DialogOption("telepad", "Telepad", "\u2728", DialogType.MANUAL_CLONE_TELEPAD));
        options.add(new DialogOption("neurocell", "Neurocell", "\uD83E\uDDE0", DialogType.MANUAL_CLONE_NEUROCELL));
        options.add(new DialogOption("reformer", "Reformer", "\u2699", DialogType.MANUAL_CLONE_REFORMER));
        options.add(new DialogOption("clone_entity", "Player Clone", "\uD83D\uDC64", DialogType.MANUAL_CLONE_ENTITY));
        options.add(new DialogOption("back", "Back to manual", "\u2B05", DialogType.MANUAL_INDEX));

        return new DialogResponse("NEXA", lines, options);
    }

    /**
     * Get Bioscanner item guide.
     */
    @Nonnull
    public DialogResponse getManualCloneBioscanner(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Bioscanner");
        lines.add("");
        lines.add("A handheld device that scans and stores");
        lines.add("entity data for cloning.");
        lines.add("");
        lines.add("Usage:");
        lines.add("- Right-click any entity to scan");
        lines.add("- Stores: entity type, name, UUID, NBT data");
        lines.add("- Item glows when containing data");
        lines.add("- Shift+right-click to clear data");
        lines.add("");
        lines.add("Tip: Works on players too!");

        return new DialogResponse("NEXA", lines, getManualCloneBackOption());
    }

    /**
     * Get Telepad block guide.
     */
    @Nonnull
    public DialogResponse getManualCloneTelepad(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Telepad");
        lines.add("");
        lines.add("A teleportation pad that connects to a");
        lines.add("network of other telepads.");
        lines.add("");
        lines.add("Setup:");
        lines.add("1. Place the telepad");
        lines.add("2. Right-click to open config screen");
        lines.add("3. Set a network name (e.g. 'base')");
        lines.add("4. Stand on pad for 2 seconds to teleport");
        lines.add("");
        lines.add("Features:");
        lines.add("- Cross-dimensional travel");
        lines.add("- Random destination in same network");
        lines.add("- Configurable charge time");

        return new DialogResponse("NEXA", lines, getManualCloneBackOption());
    }

    /**
     * Get Neurocell block guide.
     */
    @Nonnull
    public DialogResponse getManualCloneNeurocell(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Neurocell");
        lines.add("");
        lines.add("A cloning chamber that processes entity");
        lines.add("data from a Bioscanner.");
        lines.add("");
        lines.add("Usage:");
        lines.add("1. Right-click with filled Bioscanner");
        lines.add("2. Wait for processing (15 seconds)");
        lines.add("3. Connect to Reformer via Neurolink");
        lines.add("");
        lines.add("Visual:");
        lines.add("- Shows entity preview during processing");
        lines.add("- Glows when ready for Reformer");

        return new DialogResponse("NEXA", lines, getManualCloneBackOption());
    }

    /**
     * Get Reformer block guide.
     */
    @Nonnull
    public DialogResponse getManualCloneReformer(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Reformer");
        lines.add("");
        lines.add("The final stage - spawns cloned entities");
        lines.add("from processed Neurocell data.");
        lines.add("");
        lines.add("Setup:");
        lines.add("1. Connect to Neurocell via Neurolink");
        lines.add("2. Neurocell must have processed data");
        lines.add("3. Reformer auto-starts reconstruction");
        lines.add("");
        lines.add("Spawn Time:");
        lines.add("- Based on entity max health");
        lines.add("- Players: ~60 seconds");
        lines.add("- Zombies: ~24 seconds");
        lines.add("");
        lines.add("Note: Neurolink max distance is 16 blocks");

        return new DialogResponse("NEXA", lines, getManualCloneBackOption());
    }

    /**
     * Get Player Clone entity guide.
     */
    @Nonnull
    public DialogResponse getManualCloneEntity(@Nonnull NexusDialogContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        List<String> lines = new ArrayList<>();

        lines.add("Player Clone");
        lines.add("");
        lines.add("A tamed companion that follows and fights");
        lines.add("for its owner.");
        lines.add("");
        lines.add("Behavior Modes:");
        lines.add("- FOLLOW: Follows owner (default)");
        lines.add("- GUARD: Stays in position");
        lines.add("- ATTACK: Actively hunts hostiles");
        lines.add("");
        lines.add("Controls:");
        lines.add("- Right-click: Cycle behavior mode");
        lines.add("- Shift+right-click: Toggle sit/stand");
        lines.add("");
        lines.add("Combat:");
        lines.add("- Attacks owner's targets");
        lines.add("- Defends owner when hurt");
        lines.add("- Won't attack owner's other pets");

        return new DialogResponse("NEXA", lines, getManualCloneBackOption());
    }

    /**
     * Get back option for Clone manual pages.
     */
    @Nonnull
    private List<DialogOption> getManualCloneBackOption() {
        return List.of(
            new DialogOption("back", "Back to Clone", "\u2B05", DialogType.MANUAL_CLONE),
            new DialogOption("manual", "Manual Index", "\uD83D\uDCD6", DialogType.MANUAL_INDEX),
            new DialogOption("farewell", "Close", "\u2716", DialogType.FAREWELL)
        );
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
            // Manual System
            case MANUAL_INDEX -> getManualIndex(ctx);
            case MANUAL_CLONE -> getManualClone(ctx);
            case MANUAL_CLONE_BIOSCANNER -> getManualCloneBioscanner(ctx);
            case MANUAL_CLONE_TELEPAD -> getManualCloneTelepad(ctx);
            case MANUAL_CLONE_NEUROCELL -> getManualCloneNeurocell(ctx);
            case MANUAL_CLONE_REFORMER -> getManualCloneReformer(ctx);
            case MANUAL_CLONE_ENTITY -> getManualCloneEntity(ctx);
        };
    }

    /**
     * Get the main menu options.
     */
    @Nonnull
    private List<DialogOption> getMainOptions() {
        List<DialogOption> options = new ArrayList<>();

        options.add(new DialogOption("quest_info", "Tell me about quests", "\u2694", DialogType.QUEST_INFO));
        options.add(new DialogOption("stats", "Show my statistics", "\uD83D\uDCCA", DialogType.STATS));
        options.add(new DialogOption("tips", "Give me tips", "\uD83D\uDCA1", DialogType.TIPS));
        options.add(new DialogOption("zones", "Zone guide", "\uD83D\uDDFA", DialogType.ZONE_GUIDE));
        options.add(new DialogOption("manual", "Open Manual", "\uD83D\uDCD6", DialogType.MANUAL_INDEX));
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
