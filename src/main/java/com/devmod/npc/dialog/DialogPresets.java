package com.devmod.npc.dialog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.npc.dialog.action.DialogAction;
import com.devmod.npc.dialog.condition.DialogCondition;

/**
 * Built-in dialog presets that are available to all NPCs.
 * Presets are non-modifiable and always available.
 */
public final class DialogPresets {
    private static final Map<String, DialogSet> PRESETS = new HashMap<>();

    // Preset IDs
    public static final String GUIDE_BASIC_ID = "preset_guide_basic";
    public static final String FOUNDRY_GUIDE_ID = "preset_foundry_guide";
    public static final String VENDOR_ID = "preset_vendor";
    public static final String QUEST_GIVER_ID = "preset_quest_giver";
    public static final String INFO_POINT_ID = "preset_info_point";

    static {
        registerPreset(createGuideBasic());
        registerPreset(createFoundryGuide());
        registerPreset(createVendor());
        registerPreset(createQuestGiver());
        registerPreset(createInfoPoint());
    }

    private DialogPresets() {}

    /**
     * Gets a preset by its ID.
     */
    @Nonnull
    public static Optional<DialogSet> getPreset(@Nonnull String id) {
        return Optional.ofNullable(PRESETS.get(id));
    }

    /**
     * Gets all available presets.
     */
    @Nonnull
    public static Map<String, DialogSet> getAllPresets() {
        return Map.copyOf(PRESETS);
    }

    /**
     * Gets all available preset IDs.
     */
    @Nonnull
    public static Set<String> getAllPresetIds() {
        return Set.copyOf(PRESETS.keySet());
    }

    /**
     * Checks if a preset exists.
     */
    public static boolean hasPreset(@Nonnull String id) {
        return PRESETS.containsKey(id);
    }

    private static void registerPreset(DialogSet preset) {
        PRESETS.put(preset.id(), preset);
        DevMod.LOGGER.debug("Registered dialog preset: {}", preset.id());
    }

    // ========================================================================
    // Preset Definitions
    // ========================================================================

    /**
     * Basic guide preset with zone navigation.
     */
    private static DialogSet createGuideBasic() {
        Map<String, DialogNode> nodes = new HashMap<>();

        // Greeting node
        nodes.put("greeting", new DialogNode(
            "greeting",
            List.of("Benvenuto, {player}!", "Come posso aiutarti oggi?"),
            List.of(
                DialogOption.goTo("show_zones", "Mostra zone", "\uD83D\uDDFA\uFE0F", "zones"),
                DialogOption.close("goodbye", "Arrivederci", "\uD83D\uDC4B")
            ),
            null
        ));

        // Zones node
        nodes.put("zones", new DialogNode(
            "zones",
            List.of("Ecco le zone disponibili:"),
            List.of(
                new DialogOption("tp_hub", "Hub", "\uD83C\uDFE0", null,
                    new DialogAction.Teleport(null, null, "hub")),
                new DialogOption("tp_arena", "Arena", "\u2694\uFE0F", null,
                    new DialogAction.Teleport(null, null, "arena")),
                DialogOption.goTo("back", "Indietro", "\u25C0\uFE0F", "greeting")
            ),
            null
        ));

        return DialogSet.createPreset(GUIDE_BASIC_ID, "Guida Base", nodes, "greeting");
    }

    /**
     * Foundry guide preset with core system explanations.
     */
    private static DialogSet createFoundryGuide() {
        Map<String, DialogNode> nodes = new HashMap<>();

        nodes.put("start", new DialogNode(
            "start",
            List.of(
                "Benvenuto nella Foundry.",
                "Posso spiegarti multiblocco, fusione, colata e strumenti."
            ),
            List.of(
                DialogOption.goTo("multiblock", "Multiblocco", "\uD83E\uDDF1", "multiblock"),
                DialogOption.goTo("melting", "Fusione e carburante", "\uD83D\uDD25", "melting"),
                DialogOption.goTo("casting", "Colata e stampi", "\uD83E\uDEA8", "casting"),
                DialogOption.goTo("tools", "Strumenti modulari", "\uD83D\uDD28", "tools"),
                new DialogOption("give_guide", "Dammi la guida", "\uD83D\uDCD6", null,
                    new DialogAction.GiveItem(
                        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "foundry_guide"),
                        1,
                        null
                    )),
                DialogOption.close("exit", "Chiudi", "\u274C")
            ),
            null
        ));

        nodes.put("multiblock", new DialogNode(
            "multiblock",
            List.of(
                "Costruisci una struttura cava in mattoni Foundry.",
                "Il top resta aperto e il controller guarda l'interno.",
                "Tanks, drain e faucet completano la struttura."
            ),
            List.of(DialogOption.goTo("back", "Indietro", "\u25C0\uFE0F", "start")),
            null
        ));

        nodes.put("melting", new DialogNode(
            "melting",
            List.of(
                "Inserisci carburante nel Fuel Tank.",
                "Metti materiali nel controller o nei ducts.",
                "Impurita' e temperatura influenzano la qualita'."
            ),
            List.of(DialogOption.goTo("back", "Indietro", "\u25C0\uFE0F", "start")),
            null
        ));

        nodes.put("casting", new DialogNode(
            "casting",
            List.of(
                "Usa faucet per colare in casting table o basin.",
                "I cast definiscono la forma delle parti.",
                "I canali instradano i fluidi verso piu' stampi."
            ),
            List.of(DialogOption.goTo("back", "Indietro", "\u25C0\uFE0F", "start")),
            null
        ));

        nodes.put("tools", new DialogNode(
            "tools",
            List.of(
                "Il Part Builder crea parti usando i pattern.",
                "Tool Station e Anvil assemblano strumenti e armature.",
                "Traits e modifiers cambiano le stat finali."
            ),
            List.of(DialogOption.goTo("back", "Indietro", "\u25C0\uFE0F", "start")),
            null
        ));

        return DialogSet.createPreset(FOUNDRY_GUIDE_ID, "Guida Foundry", nodes, "start");
    }

    /**
     * Vendor preset with shop integration.
     */
    private static DialogSet createVendor() {
        Map<String, DialogNode> nodes = new HashMap<>();

        // Shop intro node
        nodes.put("shop_intro", new DialogNode(
            "shop_intro",
            List.of("Benvenuto nel mio negozio!", "Cosa desideri acquistare?"),
            List.of(
                new DialogOption("open_shop", "Apri Shop", "\uD83D\uDED2", null,
                    new DialogAction.OpenGui("shop")),
                DialogOption.close("close", "Niente, grazie", "\u274C")
            ),
            null
        ));

        return DialogSet.createPreset(VENDOR_ID, "Venditore", nodes, "shop_intro");
    }

    /**
     * Quest giver preset with quest state checking.
     */
    private static DialogSet createQuestGiver() {
        Map<String, DialogNode> nodes = new HashMap<>();

        // Check quest node - shows when quest not completed
        nodes.put("check_quest", new DialogNode(
            "check_quest",
            List.of("Ho una missione per te!"),
            List.of(
                new DialogOption("accept", "Accetta", "\u2705", null,
                    new DialogAction.ExecuteCommand("quest start {player}", DialogAction.CommandSource.CONSOLE)),
                DialogOption.close("decline", "Rifiuta", "\u274C")
            ),
            new DialogCondition.Not(new DialogCondition.QuestCompleted("current"))
        ));

        // Quest done node - shows when quest completed
        nodes.put("quest_done", new DialogNode(
            "quest_done",
            List.of("Hai completato la missione!", "Ecco la tua ricompensa."),
            List.of(
                new DialogOption("claim_reward", "Ritira ricompensa", "\uD83C\uDF81", null,
                    new DialogAction.GiveItem(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("diamond"),
                        5,
                        null
                    ))
            ),
            new DialogCondition.QuestCompleted("current")
        ));

        return DialogSet.createPreset(QUEST_GIVER_ID, "Quest Giver", nodes, "check_quest");
    }

    /**
     * Info point preset with simple information display.
     */
    private static DialogSet createInfoPoint() {
        Map<String, DialogNode> nodes = new HashMap<>();

        // Main info node
        nodes.put("main", new DialogNode(
            "main",
            List.of("Benvenuto!", "Questo è un punto informativo.", "Seleziona un argomento per saperne di più."),
            List.of(
                DialogOption.goTo("about_server", "Info Server", "\uD83D\uDCBB", "about_server"),
                DialogOption.goTo("about_rules", "Regole", "\uD83D\uDCDC", "about_rules"),
                DialogOption.close("exit", "Esci", "\u274C")
            ),
            null
        ));

        // About server node
        nodes.put("about_server", new DialogNode(
            "about_server",
            List.of("Questo server offre:", "- Zone PvE", "- Arena PvP", "- Quest giornaliere", "- Eventi speciali"),
            List.of(
                DialogOption.goTo("back", "Indietro", "\u25C0\uFE0F", "main")
            ),
            null
        ));

        // About rules node
        nodes.put("about_rules", new DialogNode(
            "about_rules",
            List.of("Regole del server:", "1. Rispetta gli altri giocatori", "2. Niente hack o cheat", "3. Divertiti!"),
            List.of(
                DialogOption.goTo("back", "Indietro", "\u25C0\uFE0F", "main")
            ),
            null
        ));

        return DialogSet.createPreset(INFO_POINT_ID, "Punto Informativo", nodes, "main");
    }
}
