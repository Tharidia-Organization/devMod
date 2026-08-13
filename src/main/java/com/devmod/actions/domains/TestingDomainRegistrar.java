package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

/**
 * Domain registrar for testing/QA actions from TestHarnessCommands.
 *
 * <p>Includes HUD controls, panel controls, debug rendering controls,
 * endurance test helpers, and QA tooling. All require permission level 2.
 */
public final class TestingDomainRegistrar implements DomainRegistrar {

    @Override
    public String domainName() {
        return "testing";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        List<ActionSpec> specs = new ArrayList<>();

        // ── HUD controls ──
        specs.add(testSpec(ActionIds.TEST_HUD_ON,
            "devmod.action.test.hud.on", "devmod.action.test.hud.on.desc",
            "minecraft:green_dye", "Root/Testing/HUD/On", "devtest hud on"));
        specs.add(testSpec(ActionIds.TEST_HUD_OFF,
            "devmod.action.test.hud.off", "devmod.action.test.hud.off.desc",
            "minecraft:red_dye", "Root/Testing/HUD/Off", "devtest hud off"));
        specs.add(testSpec(ActionIds.TEST_HUD_TOGGLE,
            "devmod.action.test.hud.toggle", "devmod.action.test.hud.toggle.desc",
            "minecraft:lever", "Root/Testing/HUD/Toggle", "devtest hud toggle"));
        specs.add(testSpec(ActionIds.TEST_HUD_EXPORT,
            "devmod.action.test.hud.export", "devmod.action.test.hud.export.desc",
            "minecraft:writable_book", "Root/Testing/HUD/Export", "devtest hud export"));
        specs.add(testSpec(ActionIds.TEST_HUD_IMPORT,
            "devmod.action.test.hud.import", "devmod.action.test.hud.import.desc",
            "minecraft:book", "Root/Testing/HUD/Import", "devtest hud import"));

        // ── Panel controls ──
        specs.add(testSpec(ActionIds.TEST_PANEL_ON,
            "devmod.action.test.panel.on", "devmod.action.test.panel.on.desc",
            "minecraft:light_blue_dye", "Root/Testing/Panels/On", "devtest panel on"));
        specs.add(testSpec(ActionIds.TEST_PANEL_OFF,
            "devmod.action.test.panel.off", "devmod.action.test.panel.off.desc",
            "minecraft:gray_dye", "Root/Testing/Panels/Off", "devtest panel off"));
        specs.add(testSpec(ActionIds.TEST_PANEL_TOGGLE,
            "devmod.action.test.panel.toggle", "devmod.action.test.panel.toggle.desc",
            "minecraft:repeater", "Root/Testing/Panels/Toggle", "devtest panel toggle"));

        // ── Debug rendering controls ──
        specs.add(testSpec(ActionIds.TEST_DEBUG_ON,
            "devmod.action.test.debug.on", "devmod.action.test.debug.on.desc",
            "minecraft:lime_dye", "Root/Testing/Debug/On", "devtest debug on"));
        specs.add(testSpec(ActionIds.TEST_DEBUG_OFF,
            "devmod.action.test.debug.off", "devmod.action.test.debug.off.desc",
            "minecraft:redstone", "Root/Testing/Debug/Off", "devtest debug off"));
        specs.add(testSpec(ActionIds.TEST_DEBUG_TOGGLE,
            "devmod.action.test.debug.toggle", "devmod.action.test.debug.toggle.desc",
            "minecraft:command_block", "Root/Testing/Debug/Toggle", "devtest debug toggle"));

        // ── Endurance test helpers ──
        specs.add(testSpec(ActionIds.TEST_ENDURANCE_STATS,
            "devmod.action.test.endurance.stats", "devmod.action.test.endurance.stats.desc",
            "minecraft:iron_sword", "Root/Testing/Endurance/Stats", "devtest endurance stats"));
        specs.add(testSpec(ActionIds.TEST_ENDURANCE_PERKS,
            "devmod.action.test.endurance.perks", "devmod.action.test.endurance.perks.desc",
            "minecraft:emerald", "Root/Testing/Endurance/Perks", "devtest endurance perks"));
        specs.add(testSpec(ActionIds.TEST_ENDURANCE_SMOKE,
            "devmod.action.test.endurance.smoke", "devmod.action.test.endurance.smoke.desc",
            "minecraft:fire_charge", "Root/Testing/Endurance/Smoke", "devtest endurance smoke"));
        specs.add(testPromptSpec(ActionIds.TEST_ENDURANCE_EXPORT_TABLE,
            "devmod.action.test.endurance.export.table", "devmod.action.test.endurance.export.table.desc",
            "minecraft:paper", "Root/Testing/Endurance/Export Table", "devtest endurance export <table>"));
        specs.add(testSpec(ActionIds.TEST_ENDURANCE_EXPORT_ALL,
            "devmod.action.test.endurance.export.all", "devmod.action.test.endurance.export.all.desc",
            "minecraft:chest", "Root/Testing/Endurance/Export All", "devtest endurance export all"));
        specs.add(testSpec(ActionIds.TEST_ENDURANCE_AUTOSMOKE,
            "devmod.action.test.endurance.autosmoke", "devmod.action.test.endurance.autosmoke.desc",
            "minecraft:campfire", "Root/Testing/Endurance/AutoSmoke", "devtest endurance autosmoke"));
        specs.add(testSpec(ActionIds.TEST_ENDURANCE_KILLWAVE,
            "devmod.action.test.endurance.killwave", "devmod.action.test.endurance.killwave.desc",
            "minecraft:diamond_sword", "Root/Testing/Endurance/Kill Wave", "devtest endurance killwave"));
        specs.add(testSpec(ActionIds.TEST_ENDURANCE_DIE,
            "devmod.action.test.endurance.die", "devmod.action.test.endurance.die.desc",
            "minecraft:skeleton_skull", "Root/Testing/Endurance/Simulate Death", "devtest endurance die"));
        specs.add(testSpec(ActionIds.TEST_ENDURANCE_SKIP_WAVE,
            "devmod.action.test.endurance.skipwave", "devmod.action.test.endurance.skipwave.desc",
            "minecraft:ender_pearl", "Root/Testing/Endurance/Skip Wave", "devtest endurance skipwave"));

        // ── Misc test tools ──
        specs.add(testPromptSpec(ActionIds.TEST_DEBUGBOX,
            "devmod.action.test.debugbox", "devmod.action.test.debugbox.desc",
            "minecraft:glass", "Root/Testing/Debug/Box", "devtest debugbox <size>"));
        specs.add(testSpec(ActionIds.TEST_DEBUGCLEAR,
            "devmod.action.test.debugclear", "devmod.action.test.debugclear.desc",
            "minecraft:barrier", "Root/Testing/Debug/Clear", "devtest debugclear"));
        specs.add(testSpec(ActionIds.TEST_PANELCLEAR,
            "devmod.action.test.panelclear", "devmod.action.test.panelclear.desc",
            "minecraft:barrier", "Root/Testing/Panels/Clear", "devtest panelclear"));
        specs.add(testSpec(ActionIds.TEST_INFO,
            "devmod.action.test.info", "devmod.action.test.info.desc",
            "minecraft:clock", "Root/Testing/Status", "devtest info"));
        specs.add(testSpec(ActionIds.TEST_QA_OPEN,
            "devmod.action.test.qa.open", "devmod.action.test.qa.open.desc",
            "minecraft:brewing_stand", "Root/Testing/QA/Open", "devtest qa"));
        specs.add(testPromptSpec(ActionIds.TEST_BODYPART_INFO,
            "devmod.action.test.bodypart", "devmod.action.test.bodypart.desc",
            "minecraft:bone", "Root/Testing/Debug/Body Part", "devtest bodypart <part>"));

        return List.copyOf(specs);
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        for (String id : List.of(
                ActionIds.TEST_HUD_ON, ActionIds.TEST_HUD_OFF, ActionIds.TEST_HUD_TOGGLE,
                ActionIds.TEST_HUD_EXPORT, ActionIds.TEST_HUD_IMPORT,
                ActionIds.TEST_PANEL_ON, ActionIds.TEST_PANEL_OFF, ActionIds.TEST_PANEL_TOGGLE,
                ActionIds.TEST_DEBUG_ON, ActionIds.TEST_DEBUG_OFF, ActionIds.TEST_DEBUG_TOGGLE,
                ActionIds.TEST_ENDURANCE_STATS, ActionIds.TEST_ENDURANCE_PERKS,
                ActionIds.TEST_ENDURANCE_SMOKE, ActionIds.TEST_ENDURANCE_EXPORT_TABLE,
                ActionIds.TEST_ENDURANCE_EXPORT_ALL, ActionIds.TEST_ENDURANCE_AUTOSMOKE,
                ActionIds.TEST_ENDURANCE_KILLWAVE, ActionIds.TEST_ENDURANCE_DIE,
                ActionIds.TEST_ENDURANCE_SKIP_WAVE,
                ActionIds.TEST_DEBUGBOX, ActionIds.TEST_DEBUGCLEAR,
                ActionIds.TEST_PANELCLEAR, ActionIds.TEST_INFO,
                ActionIds.TEST_QA_OPEN, ActionIds.TEST_BODYPART_INFO)) {
            registry.registerResult(id, ActionHandler.delegatingToV1(id));
        }
    }

    @Override
    public Set<String> getDependencies() {
        return Set.of();
    }

    // ── Factory helpers ──

    private static ActionSpec testSpec(String id, String labelKey, String descKey,
                                       String iconItemId, String menuPath, String commandHint) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.SERVER)
            .category(ActionCategory.TESTING)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(2)
            .ui(labelKey, descKey, iconItemId, menuPath, false)
            .binding(null, commandHint, null)
            .policy(false, 0, "requiresPermissionOrClient_2")
            .telemetryTags("testing")
            .build();
    }

    private static ActionSpec testPromptSpec(String id, String labelKey, String descKey,
                                              String iconItemId, String menuPath, String commandHint) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.BOTH)
            .category(ActionCategory.TESTING)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(2)
            .ui(labelKey, descKey, iconItemId, menuPath, false)
            .binding(null, commandHint, null)
            .policy(false, 0, "requiresPermissionOrClient_2")
            .telemetryTags("testing")
            .build();
    }
}
