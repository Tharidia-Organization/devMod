package com.frenkvs.devmod.actions;

import com.devmod.arena.command.ArenaActionRegistry;
import com.frenkvs.devmod.debug.DebugCommand;
import com.frenkvs.devmod.gametest.TestHarnessCommands;
import com.frenkvs.devmod.telemetry.TelemetryReloadCommand;
import com.frenkvs.devmod.telemetry.dashboard.DashboardCommand;
import com.frenkvs.devmod.telemetry.dungeon.DungeonCommand;
import net.minecraft.world.item.Items;

public final class DevModActions {
    private DevModActions() {}

    public static void registerCommon() {
        registerCommandActions();
        registerServerActions();
    }

    private static void registerCommandActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_GAMEMODE_CREATIVE)
            .labelKey("devmod.action.command.gamemode_creative")
            .descriptionKey("devmod.action.command.gamemode_creative.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Commands/Gamemode Creative")
            .icon(Items.GRASS_BLOCK)
            .precondition(ActionPreconditions.always())
            .commandHint("gamemode creative")
            .handler(context -> context.executeCommand("gamemode creative"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_GAMEMODE_SURVIVAL)
            .labelKey("devmod.action.command.gamemode_survival")
            .descriptionKey("devmod.action.command.gamemode_survival.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Commands/Gamemode Survival")
            .icon(Items.IRON_SWORD)
            .precondition(ActionPreconditions.always())
            .commandHint("gamemode survival")
            .handler(context -> context.executeCommand("gamemode survival"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_HEAL)
            .labelKey("devmod.action.command.heal")
            .descriptionKey("devmod.action.command.heal.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Commands/Heal")
            .icon(Items.GOLDEN_APPLE)
            .precondition(ActionPreconditions.always())
            .commandHint("heal")
            .handler(context -> context.executeCommand("heal"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_TIME_DAY)
            .labelKey("devmod.action.command.time_day")
            .descriptionKey("devmod.action.command.time_day.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Commands/Time Day")
            .icon(Items.SUNFLOWER)
            .precondition(ActionPreconditions.always())
            .commandHint("time set day")
            .handler(context -> context.executeCommand("time set day"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_TIME_NIGHT)
            .labelKey("devmod.action.command.time_night")
            .descriptionKey("devmod.action.command.time_night.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Commands/Time Night")
            .icon(Items.CLOCK)
            .precondition(ActionPreconditions.always())
            .commandHint("time set night")
            .handler(context -> context.executeCommand("time set night"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_WEATHER_CLEAR)
            .labelKey("devmod.action.command.weather_clear")
            .descriptionKey("devmod.action.command.weather_clear.desc")
            .category(ActionCategory.TOOLS)
            .menuPath("Root/Tools/Commands/Weather Clear")
            .icon(Items.FEATHER)
            .precondition(ActionPreconditions.always())
            .commandHint("weather clear")
            .handler(context -> context.executeCommand("weather clear"))
            .build());
    }

    private static void registerServerActions() {
        ArenaActionRegistry.registerActions();
        DebugCommand.registerActions();
        TelemetryReloadCommand.registerActions();
        DashboardCommand.registerActions();
        DungeonCommand.registerActions();
        TestHarnessCommands.registerActions();
    }
}
