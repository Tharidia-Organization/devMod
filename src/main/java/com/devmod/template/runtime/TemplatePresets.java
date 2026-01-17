package com.devmod.template.runtime;



import com.devmod.DevMod;
import com.devmod.template.data.RelativePosition;
import com.devmod.template.data.RoomTemplate;
import com.devmod.template.data.TemplateRegistry;

/**
 * Default template presets for all Nexus zones.
 *
 * Zones (from user spec):
 * 1. Spawn
 * 2. Tutorial (movimento/combat/stamina)
 * 3. Hub Quest (bacheca, quest principali/secondarie/ripetibili)
 * 4. Gate Progression (portali chapter + world boss)
 * 5. Building (claim)
 * 6. Town Test (A, B, distanza, gestionale, politica)
 * 7. Economia (shop, banca/asta, money sinks, carovana)
 * 8. War Hub (start/end, area fortificata, arena, assedio)
 * 9. Classes System (maestri, crafting, minigame labs)
 * 10. Eventi Periodici (tornei)
 * 11. DM Mod (comandi, HUD)
 */
public final class TemplatePresets {
    private TemplatePresets() {}

    /**
     * Register all preset templates.
     */
    public static void registerAll(TemplateRegistry registry) {
        // 1. SPAWN
        registerSpawnTemplates(registry);

        // 2. TUTORIAL
        registerTutorialTemplates(registry);

        // 3. QUEST HUB
        registerQuestHubTemplates(registry);

        // 4. GATE PROGRESSION
        registerGateProgressionTemplates(registry);

        // 5. BUILDING
        registerBuildingTemplates(registry);

        // 6. TOWN TEST
        registerTownTestTemplates(registry);

        // 7. ECONOMIA
        registerEconomyTemplates(registry);

        // 8. WAR HUB
        registerWarHubTemplates(registry);

        // 9. CLASSES SYSTEM
        registerClassesTemplates(registry);

        // 10. EVENTI
        registerEventiTemplates(registry);

        // 11. DM MOD
        registerDmModTemplates(registry);

        DevMod.LOGGER.info("[Template] Registered all preset templates");
    }

    // =========================================================================
    // 1. SPAWN TEMPLATES
    // =========================================================================

    private static void registerSpawnTemplates(TemplateRegistry registry) {
        registry.registerTemplate(RoomTemplate.builder("spawn_standard", "spawn")
            .displayName("Spawn - Standard")
            .component("functional_spawn_pad", RelativePosition.center())
            .component("display_hologram", RelativePosition.center(0, 6))
            .component("decorative_pillar", RelativePosition.northWall(3))
            .component("decorative_pillar", RelativePosition.southWall(3))
            .component("utility_landing_pad", RelativePosition.northWall(4))
            .minSize(20, 20)
            .build());

        registry.registerTemplate(RoomTemplate.builder("spawn_minimal", "spawn")
            .displayName("Spawn - Minimal")
            .component("functional_spawn_pad", RelativePosition.center())
            .minSize(12, 12)
            .build());
    }

    // =========================================================================
    // 2. TUTORIAL TEMPLATES
    // =========================================================================

    private static void registerTutorialTemplates(TemplateRegistry registry) {
        // Movement tutorial
        registry.registerTemplate(RoomTemplate.builder("tutorial_movement", "tutorial")
            .displayName("Tutorial - Movement")
            .component("utility_landing_pad", RelativePosition.northWall(4))
            .component("utility_landing_pad", RelativePosition.center())
            .component("utility_landing_pad", RelativePosition.southWall(4))
            .component("display_screen_z", RelativePosition.northWall(2))
            .component("decorative_pillar", RelativePosition.center(-4, 0))
            .component("decorative_pillar", RelativePosition.center(4, 0))
            .minSize(20, 30)
            .build());

        // Combat tutorial
        registry.registerTemplate(RoomTemplate.builder("tutorial_combat", "tutorial")
            .displayName("Tutorial - Combat")
            .component("combat_target_wall", RelativePosition.northWall(2))
            .component("combat_training_post", RelativePosition.center(-3, 0))
            .component("combat_training_post", RelativePosition.center(3, 0))
            .component("combat_dummy_pad", RelativePosition.center())
            .component("display_screen_z", RelativePosition.southWall(2))
            .minSize(24, 24)
            .build());

        // Stamina tutorial
        registry.registerTemplate(RoomTemplate.builder("tutorial_stamina", "tutorial")
            .displayName("Tutorial - Stamina")
            .component("utility_landing_pad", RelativePosition.center())
            .component("combat_arena_ring", RelativePosition.center())
            .component("display_screen_z", RelativePosition.northWall(2))
            .minSize(20, 20)
            .build());
    }

    // =========================================================================
    // 3. QUEST HUB TEMPLATES
    // =========================================================================

    private static void registerQuestHubTemplates(TemplateRegistry registry) {
        // Main quest hub
        registry.registerTemplate(RoomTemplate.builder("quest_hub_standard", "quest_hub")
            .displayName("Quest Hub - Standard")
            .component("quest_board", RelativePosition.northWall(2))
            .component("quest_npc_spot", RelativePosition.center(-4, 0))
            .component("quest_npc_spot", RelativePosition.center(4, 0))
            .component("quest_npc_spot", RelativePosition.center(0, -4))
            .component("utility_landing_pad", RelativePosition.center())
            .component("decorative_banner", RelativePosition.northWall(3))
            .minSize(24, 24)
            .build());

        // Quest board room
        registry.registerTemplate(RoomTemplate.builder("quest_board_room", "quest_board")
            .displayName("Quest Board Room")
            .component("quest_board", RelativePosition.northWall(2))
            .component("quest_board", RelativePosition.center(-6, 0))
            .component("quest_board", RelativePosition.center(6, 0))
            .component("utility_landing_pad", RelativePosition.center())
            .minSize(20, 16)
            .build());
    }

    // =========================================================================
    // 4. GATE PROGRESSION TEMPLATES
    // =========================================================================

    private static void registerGateProgressionTemplates(TemplateRegistry registry) {
        registry.registerTemplate(RoomTemplate.builder("gate_progression_standard", "gate_progression")
            .displayName("Gate Progression - Standard")
            .component("functional_portal_frame", RelativePosition.northWall(2))
            .component("functional_portal_frame", RelativePosition.center(-8, 0))
            .component("functional_portal_frame", RelativePosition.center(8, 0))
            .component("utility_landing_pad", RelativePosition.center())
            .component("display_hologram", RelativePosition.center(0, 6))
            .component("decorative_pillar", RelativePosition.center(-12, 0))
            .component("decorative_pillar", RelativePosition.center(12, 0))
            .minSize(32, 24)
            .build());

        registry.registerTemplate(RoomTemplate.builder("world_boss_arena", "world_boss")
            .displayName("World Boss Arena")
            .component("combat_arena_ring", RelativePosition.center())
            .component("decorative_pillar", RelativePosition.center(-6, -6))
            .component("decorative_pillar", RelativePosition.center(6, -6))
            .component("decorative_pillar", RelativePosition.center(-6, 6))
            .component("decorative_pillar", RelativePosition.center(6, 6))
            .component("utility_landing_pad", RelativePosition.southWall(4))
            .minSize(32, 32)
            .build());
    }

    // =========================================================================
    // 5. BUILDING TEMPLATES
    // =========================================================================

    private static void registerBuildingTemplates(TemplateRegistry registry) {
        registry.registerTemplate(RoomTemplate.builder("building_claim", "claim")
            .displayName("Building - Claim Office")
            .component("console_ns", RelativePosition.northWall(3))
            .component("display_screen_z", RelativePosition.northWall(2))
            .component("utility_landing_pad", RelativePosition.center())
            .component("quest_npc_spot", RelativePosition.center(-4, 0))
            .minSize(16, 16)
            .build());
    }

    // =========================================================================
    // 6. TOWN TEST TEMPLATES
    // =========================================================================

    private static void registerTownTestTemplates(TemplateRegistry registry) {
        // Town test area A
        registry.registerTemplate(RoomTemplate.builder("town_test_a", "town_test")
            .displayName("Town Test - Area A")
            .component("utility_landing_pad", RelativePosition.center())
            .component("display_screen_z", RelativePosition.northWall(2))
            .component("console_ns", RelativePosition.center(-4, 0))
            .minSize(20, 20)
            .build());

        // Town gestionale (resource nodes)
        registry.registerTemplate(RoomTemplate.builder("town_gestionale_resources", "town_gestionale")
            .displayName("Town - Resource Management")
            .component("storage_server_rack", RelativePosition.northWall(2))
            .component("storage_server_rack", RelativePosition.northWall(5))
            .component("console_ns", RelativePosition.center())
            .component("display_screen_z", RelativePosition.southWall(2))
            .minSize(20, 20)
            .build());

        // Town politica (factions)
        registry.registerTemplate(RoomTemplate.builder("town_politica_factions", "town_politica")
            .displayName("Town - Factions Hall")
            .component("decorative_plinth", RelativePosition.center())
            .component("decorative_banner", RelativePosition.northWall(2))
            .component("decorative_banner", RelativePosition.center(-6, 0))
            .component("decorative_banner", RelativePosition.center(6, 0))
            .component("quest_npc_spot", RelativePosition.center(-4, 4))
            .component("quest_npc_spot", RelativePosition.center(4, 4))
            .minSize(24, 20)
            .build());
    }

    // =========================================================================
    // 7. ECONOMY TEMPLATES
    // =========================================================================

    private static void registerEconomyTemplates(TemplateRegistry registry) {
        // Shop
        registry.registerTemplate(RoomTemplate.builder("economy_shop_standard", "economy_shop")
            .displayName("Economy - Shop")
            .component("shop_vendor_stall", RelativePosition.northWall(3))
            .component("shop_vendor_stall", RelativePosition.center(-6, 0))
            .component("shop_vendor_stall", RelativePosition.center(6, 0))
            .component("utility_landing_pad", RelativePosition.center())
            .minSize(24, 20)
            .build());

        // Bank
        registry.registerTemplate(RoomTemplate.builder("economy_bank_standard", "economy_bank")
            .displayName("Economy - Bank")
            .component("shop_bank_counter", RelativePosition.northWall(3))
            .component("storage_chest_row", RelativePosition.center(-6, 0))
            .component("storage_chest_row", RelativePosition.center(6, 0))
            .component("utility_landing_pad", RelativePosition.center())
            .component("decorative_pillar", RelativePosition.center(-8, -4))
            .component("decorative_pillar", RelativePosition.center(8, -4))
            .minSize(24, 20)
            .build());

        // Auction
        registry.registerTemplate(RoomTemplate.builder("economy_auction_standard", "economy_auction")
            .displayName("Economy - Auction House")
            .component("shop_auction_block", RelativePosition.center())
            .component("display_screen_z", RelativePosition.northWall(2))
            .component("utility_bench_row", RelativePosition.southWall(3))
            .minSize(20, 20)
            .build());

        // Caravan
        registry.registerTemplate(RoomTemplate.builder("economy_caravan_stop", "economy_caravan")
            .displayName("Economy - Caravan Stop")
            .component("transport_caravan_stop", RelativePosition.center())
            .component("storage_chest_row", RelativePosition.northWall(3))
            .component("display_screen_z", RelativePosition.southWall(2))
            .minSize(24, 24)
            .build());
    }

    // =========================================================================
    // 8. WAR HUB TEMPLATES
    // =========================================================================

    private static void registerWarHubTemplates(TemplateRegistry registry) {
        // War hub main
        registry.registerTemplate(RoomTemplate.builder("war_hub_standard", "war_hub")
            .displayName("War Hub - Command Center")
            .component("display_screen_z", RelativePosition.northWall(2))
            .component("console_ns", RelativePosition.center(-4, 0))
            .component("console_ns", RelativePosition.center(4, 0))
            .component("utility_landing_pad", RelativePosition.center())
            .component("decorative_banner", RelativePosition.northWall(3))
            .minSize(24, 20)
            .build());

        // Arena
        registry.registerTemplate(RoomTemplate.builder("war_arena_standard", "war_arena")
            .displayName("War - Arena")
            .component("combat_arena_ring", RelativePosition.center())
            .component("combat_target_wall", RelativePosition.northWall(2))
            .component("combat_training_post", RelativePosition.center(-5, 0))
            .component("combat_training_post", RelativePosition.center(5, 0))
            .component("utility_landing_pad", RelativePosition.southWall(4))
            .minSize(28, 28)
            .build());

        // Fortification
        registry.registerTemplate(RoomTemplate.builder("war_fortification_standard", "war_fortification")
            .displayName("War - Fortification")
            .component("decorative_pillar", RelativePosition.center(-6, -6))
            .component("decorative_pillar", RelativePosition.center(6, -6))
            .component("decorative_pillar", RelativePosition.center(-6, 6))
            .component("decorative_pillar", RelativePosition.center(6, 6))
            .component("utility_landing_pad", RelativePosition.center())
            .component("storage_chest_row", RelativePosition.northWall(3))
            .minSize(24, 24)
            .build());

        // Siege
        registry.registerTemplate(RoomTemplate.builder("war_siege_camp", "war_siege")
            .displayName("War - Siege Camp")
            .component("utility_bench_row", RelativePosition.northWall(3))
            .component("storage_chest_row", RelativePosition.center(-6, 0))
            .component("utility_landing_pad", RelativePosition.center())
            .component("display_screen_z", RelativePosition.southWall(2))
            .minSize(24, 20)
            .build());
    }

    // =========================================================================
    // 9. CLASSES SYSTEM TEMPLATES
    // =========================================================================

    private static void registerClassesTemplates(TemplateRegistry registry) {
        // Class master hall
        registry.registerTemplate(RoomTemplate.builder("class_master_hall", "class_master")
            .displayName("Classes - Master Hall")
            .component("quest_npc_spot", RelativePosition.northWall(4))
            .component("quest_npc_spot", RelativePosition.center(-6, 0))
            .component("quest_npc_spot", RelativePosition.center(6, 0))
            .component("decorative_plinth", RelativePosition.center())
            .component("display_screen_z", RelativePosition.southWall(2))
            .minSize(24, 24)
            .build());

        // Crafting area
        registry.registerTemplate(RoomTemplate.builder("class_crafting_workshop", "class_crafting")
            .displayName("Classes - Crafting Workshop")
            .component("utility_crafting_station", RelativePosition.center(-4, 0))
            .component("utility_crafting_station", RelativePosition.center(4, 0))
            .component("utility_bench_row", RelativePosition.northWall(3))
            .component("storage_chest_row", RelativePosition.southWall(3))
            .minSize(20, 20)
            .build());

        // Forging lab
        registry.registerTemplate(RoomTemplate.builder("lab_forging_standard", "lab_forging")
            .displayName("Lab - Forging")
            .component("utility_crafting_station", RelativePosition.center())
            .component("utility_bench_row", RelativePosition.northWall(3))
            .component("display_screen_z", RelativePosition.southWall(2))
            .minSize(16, 16)
            .build());

        // Alchemy lab
        registry.registerTemplate(RoomTemplate.builder("lab_alchemy_standard", "lab_alchemy")
            .displayName("Lab - Alchemy")
            .component("utility_crafting_station", RelativePosition.center())
            .component("storage_chest_row", RelativePosition.northWall(3))
            .component("display_screen_z", RelativePosition.southWall(2))
            .minSize(16, 16)
            .build());

        // Cooking lab
        registry.registerTemplate(RoomTemplate.builder("lab_cooking_standard", "lab_cooking")
            .displayName("Lab - Cooking")
            .component("utility_crafting_station", RelativePosition.center())
            .component("storage_chest_row", RelativePosition.northWall(3))
            .minSize(16, 16)
            .build());
    }

    // =========================================================================
    // 10. EVENTI TEMPLATES
    // =========================================================================

    private static void registerEventiTemplates(TemplateRegistry registry) {
        registry.registerTemplate(RoomTemplate.builder("eventi_torneo_arena", "eventi_torneo")
            .displayName("Eventi - Tournament Arena")
            .component("combat_arena_ring", RelativePosition.center())
            .component("decorative_pillar", RelativePosition.center(-8, -8))
            .component("decorative_pillar", RelativePosition.center(8, -8))
            .component("decorative_pillar", RelativePosition.center(-8, 8))
            .component("decorative_pillar", RelativePosition.center(8, 8))
            .component("decorative_plinth", RelativePosition.northWall(4))
            .component("utility_landing_pad", RelativePosition.southWall(4))
            .component("display_screen_z", RelativePosition.northWall(2))
            .minSize(32, 32)
            .build());
    }

    // =========================================================================
    // 11. DM MOD TEMPLATES
    // =========================================================================

    private static void registerDmModTemplates(TemplateRegistry registry) {
        registry.registerTemplate(RoomTemplate.builder("dm_mod_control_room", "dm_mod")
            .displayName("DM Mod - Control Room")
            .component("console_ns", RelativePosition.center(-4, 0))
            .component("console_ns", RelativePosition.center(4, 0))
            .component("display_screen_z", RelativePosition.northWall(2))
            .component("display_screen_x", RelativePosition.westWall(2))
            .component("display_screen_x", RelativePosition.eastWall(2))
            .component("storage_server_rack", RelativePosition.southWall(3))
            .component("storage_server_rack", RelativePosition.southWall(6))
            .component("utility_landing_pad", RelativePosition.center())
            .minSize(24, 24)
            .build());
    }
}
