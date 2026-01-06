package com.devmod.endurance;

import net.minecraft.world.item.Items;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;

/**
 * Event subscriber for registering leaderboard commands.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class LeaderboardCommandEvents {

    private LeaderboardCommandEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LeaderboardCommands.register(event.getDispatcher());
    }

    /**
     * Register leaderboard actions. Called during mod initialization.
     */
    public static void registerActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.LEADERBOARD_HELP)
            .labelKey("devmod.action.leaderboard.help")
            .descriptionKey("devmod.action.leaderboard.help.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Leaderboards/Help")
            .icon(Items.PAPER)
            .commandHint("leaderboard help")
            .handler(context -> {
                var cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    LeaderboardCommands.showHelp(cmd);
                    return;
                }
                context.executeCommand("leaderboard help");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.LEADERBOARD_LIST)
            .labelKey("devmod.action.leaderboard.list")
            .descriptionKey("devmod.action.leaderboard.list.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Leaderboards/Categories")
            .icon(Items.BOOK)
            .commandHint("leaderboard list")
            .handler(context -> {
                var cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    LeaderboardCommands.listCategories(cmd);
                    return;
                }
                context.executeCommand("leaderboard list");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.LEADERBOARD_TOP)
            .labelKey("devmod.action.leaderboard.top")
            .descriptionKey("devmod.action.leaderboard.top.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Leaderboards/Top")
            .icon(Items.GOLDEN_APPLE)
            .commandHint("leaderboard top <category> [limit]")
            .handler(context -> {
                var cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    LeaderboardCommands.showTop(cmd);
                    return;
                }
                context.executeCommand("leaderboard top waves_completed");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.LEADERBOARD_ME)
            .labelKey("devmod.action.leaderboard.me")
            .descriptionKey("devmod.action.leaderboard.me.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Leaderboards/My Rank")
            .icon(Items.PLAYER_HEAD)
            .commandHint("leaderboard me [category]")
            .handler(context -> {
                var cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    LeaderboardCommands.showMe(cmd);
                    return;
                }
                context.executeCommand("leaderboard me");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.LEADERBOARD_PLAYER)
            .labelKey("devmod.action.leaderboard.player")
            .descriptionKey("devmod.action.leaderboard.player.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Leaderboards/Player")
            .icon(Items.SKELETON_SKULL)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("leaderboard player <player> [category]")
            .handler(context -> {
                var cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    LeaderboardCommands.showPlayer(cmd);
                    return;
                }
                context.sendSuccess(net.minecraft.network.chat.Component.literal("Use: /leaderboard player <name>"), false);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.LEADERBOARD_WEEKLY)
            .labelKey("devmod.action.leaderboard.weekly")
            .descriptionKey("devmod.action.leaderboard.weekly.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Leaderboards/Weekly")
            .icon(Items.CLOCK)
            .commandHint("leaderboard weekly [category]")
            .handler(context -> {
                var cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    LeaderboardCommands.showWeekly(cmd);
                    return;
                }
                context.executeCommand("leaderboard weekly");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.LEADERBOARD_ARENA)
            .labelKey("devmod.action.leaderboard.arena")
            .descriptionKey("devmod.action.leaderboard.arena.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Leaderboards/Arena")
            .icon(Items.IRON_SWORD)
            .commandHint("leaderboard arena <arenaId> [category]")
            .handler(context -> {
                var cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    LeaderboardCommands.showArena(cmd);
                    return;
                }
                context.sendSuccess(net.minecraft.network.chat.Component.literal("Use: /leaderboard arena <id>"), false);
            })
            .build());
    }
}
