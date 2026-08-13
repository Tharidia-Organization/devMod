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
 * Domain registrar for admin actions: mailbox commands, news commands,
 * leaderboard commands, and server-side debug commands.
 *
 * <p>All admin actions require elevated permission levels (2+).
 * Mailbox and news actions use ActionCategory.DEBUG (matching existing
 * registration). Leaderboard actions use ActionCategory.ENDURANCE.
 * Debug commands use ActionCategory.DEBUG.
 */
public final class AdminDomainRegistrar implements DomainRegistrar {

    @Override
    public String domainName() {
        return "admin";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        List<ActionSpec> specs = new ArrayList<>();

        // =====================================================================
        // MAILBOX COMMANDS (perm level 2, category DEBUG)
        // =====================================================================
        specs.add(adminSpec(ActionIds.MAILBOX_COMMAND_HELP,
            "devmod.action.mailbox.command.help", "devmod.action.mailbox.command.help.desc",
            "minecraft:paper", "Root/Admin/Mailbox/Help", "mailbox help",
            ActionCategory.DEBUG, 2));
        specs.add(adminSpec(ActionIds.MAILBOX_COMMAND_STATS,
            "devmod.action.mailbox.command.stats", "devmod.action.mailbox.command.stats.desc",
            "minecraft:book", "Root/Admin/Mailbox/Stats", "mailbox stats",
            ActionCategory.DEBUG, 2));
        specs.add(adminPromptSpec(ActionIds.MAILBOX_COMMAND_SEND,
            "devmod.action.mailbox.command.send", "devmod.action.mailbox.command.send.desc",
            "minecraft:writable_book", "Root/Admin/Mailbox/Send", "mailbox send <player> <subject> <body>",
            ActionCategory.DEBUG, 2));
        specs.add(adminPromptSpec(ActionIds.MAILBOX_COMMAND_BROADCAST,
            "devmod.action.mailbox.command.broadcast", "devmod.action.mailbox.command.broadcast.desc",
            "minecraft:bell", "Root/Admin/Mailbox/Broadcast", "mailbox broadcast <subject> <body>",
            ActionCategory.DEBUG, 2));
        specs.add(adminPromptSpec(ActionIds.MAILBOX_COMMAND_INBOX,
            "devmod.action.mailbox.command.inbox", "devmod.action.mailbox.command.inbox.desc",
            "minecraft:chest", "Root/Admin/Mailbox/View Inbox", "mailbox inbox <player>",
            ActionCategory.DEBUG, 2));
        specs.add(adminPromptSpec(ActionIds.MAILBOX_COMMAND_PURGE,
            "devmod.action.mailbox.command.purge", "devmod.action.mailbox.command.purge.desc",
            "minecraft:lava_bucket", "Root/Admin/Mailbox/Purge", "mailbox purge <player>",
            ActionCategory.DEBUG, 2));

        // =====================================================================
        // NEWS COMMANDS (perm level 2, category DEBUG)
        // =====================================================================
        specs.add(adminSpec(ActionIds.NEWS_COMMAND_HELP,
            "devmod.action.news.command.help", "devmod.action.news.command.help.desc",
            "minecraft:paper", "Root/Admin/News/Help", "news help",
            ActionCategory.DEBUG, 2));
        specs.add(adminSpec(ActionIds.NEWS_COMMAND_LIST,
            "devmod.action.news.command.list", "devmod.action.news.command.list.desc",
            "minecraft:book", "Root/Admin/News/List", "news list",
            ActionCategory.DEBUG, 2));
        specs.add(adminPromptSpec(ActionIds.NEWS_COMMAND_CREATE,
            "devmod.action.news.command.create", "devmod.action.news.command.create.desc",
            "minecraft:writable_book", "Root/Admin/News/Create", "news create <category> <title> <content>",
            ActionCategory.DEBUG, 2));
        specs.add(adminPromptSpec(ActionIds.NEWS_COMMAND_DELETE,
            "devmod.action.news.command.delete", "devmod.action.news.command.delete.desc",
            "minecraft:barrier", "Root/Admin/News/Delete", "news delete <id>",
            ActionCategory.DEBUG, 2));
        specs.add(adminPromptSpec(ActionIds.NEWS_COMMAND_PUBLISH,
            "devmod.action.news.command.publish", "devmod.action.news.command.publish.desc",
            "minecraft:knowledge_book", "Root/Admin/News/Publish", "news publish <id>",
            ActionCategory.DEBUG, 2));

        // =====================================================================
        // LEADERBOARD COMMANDS (category ENDURANCE)
        // =====================================================================
        specs.add(adminSpec(ActionIds.LEADERBOARD_HELP,
            "devmod.action.leaderboard.help", "devmod.action.leaderboard.help.desc",
            "minecraft:paper", "Root/Play/Leaderboards/Help", "leaderboard help",
            ActionCategory.ENDURANCE, 0));
        specs.add(adminSpec(ActionIds.LEADERBOARD_LIST,
            "devmod.action.leaderboard.list", "devmod.action.leaderboard.list.desc",
            "minecraft:book", "Root/Play/Leaderboards/Categories", "leaderboard list",
            ActionCategory.ENDURANCE, 0));
        specs.add(adminSpec(ActionIds.LEADERBOARD_TOP,
            "devmod.action.leaderboard.top", "devmod.action.leaderboard.top.desc",
            "minecraft:golden_apple", "Root/Play/Leaderboards/Top", "leaderboard top <category> [limit]",
            ActionCategory.ENDURANCE, 0));
        specs.add(adminSpec(ActionIds.LEADERBOARD_ME,
            "devmod.action.leaderboard.me", "devmod.action.leaderboard.me.desc",
            "minecraft:player_head", "Root/Play/Leaderboards/My Rank", "leaderboard me [category]",
            ActionCategory.ENDURANCE, 0));
        specs.add(adminSpec(ActionIds.LEADERBOARD_PLAYER,
            "devmod.action.leaderboard.player", "devmod.action.leaderboard.player.desc",
            "minecraft:skeleton_skull", "Root/Play/Leaderboards/Player", "leaderboard player <player> [category]",
            ActionCategory.ENDURANCE, 2));
        specs.add(adminSpec(ActionIds.LEADERBOARD_WEEKLY,
            "devmod.action.leaderboard.weekly", "devmod.action.leaderboard.weekly.desc",
            "minecraft:clock", "Root/Play/Leaderboards/Weekly", "leaderboard weekly [category]",
            ActionCategory.ENDURANCE, 0));
        specs.add(adminSpec(ActionIds.LEADERBOARD_ARENA,
            "devmod.action.leaderboard.arena", "devmod.action.leaderboard.arena.desc",
            "minecraft:iron_sword", "Root/Play/Leaderboards/Arena", "leaderboard arena <arenaId> [category]",
            ActionCategory.ENDURANCE, 0));

        // =====================================================================
        // DEBUG COMMANDS (server-side, category DEBUG, perm level 2)
        // =====================================================================
        specs.add(adminSpec(ActionIds.DEBUG_COMMAND_HELP,
            "devmod.action.debug.command.help", "devmod.action.debug.command.help.desc",
            "minecraft:paper", "Root/Debug/Commands/Help", "devdebug",
            ActionCategory.DEBUG, 2));
        specs.add(adminSpec(ActionIds.DEBUG_COMMAND_LIST,
            "devmod.action.debug.command.list", "devmod.action.debug.command.list.desc",
            "minecraft:book", "Root/Debug/Commands/List", "devdebug list",
            ActionCategory.DEBUG, 2));
        specs.add(adminSpec(ActionIds.DEBUG_COMMAND_OFF,
            "devmod.action.debug.command.off", "devmod.action.debug.command.off.desc",
            "minecraft:barrier", "Root/Debug/Commands/Disable All", "devdebug off",
            ActionCategory.DEBUG, 2));
        specs.add(adminPromptSpec(ActionIds.DEBUG_COMMAND_TOGGLE,
            "devmod.action.debug.command.toggle", "devmod.action.debug.command.toggle.desc",
            "minecraft:command_block", "Root/Debug/Commands/Toggle Feature", "devdebug <feature>",
            ActionCategory.DEBUG, 2));

        return List.copyOf(specs);
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        for (String id : List.of(
                // Mailbox
                ActionIds.MAILBOX_COMMAND_HELP, ActionIds.MAILBOX_COMMAND_STATS,
                ActionIds.MAILBOX_COMMAND_SEND, ActionIds.MAILBOX_COMMAND_BROADCAST,
                ActionIds.MAILBOX_COMMAND_INBOX, ActionIds.MAILBOX_COMMAND_PURGE,
                // News
                ActionIds.NEWS_COMMAND_HELP, ActionIds.NEWS_COMMAND_LIST,
                ActionIds.NEWS_COMMAND_CREATE, ActionIds.NEWS_COMMAND_DELETE,
                ActionIds.NEWS_COMMAND_PUBLISH,
                // Leaderboard
                ActionIds.LEADERBOARD_HELP, ActionIds.LEADERBOARD_LIST,
                ActionIds.LEADERBOARD_TOP, ActionIds.LEADERBOARD_ME,
                ActionIds.LEADERBOARD_PLAYER, ActionIds.LEADERBOARD_WEEKLY,
                ActionIds.LEADERBOARD_ARENA,
                // Debug commands
                ActionIds.DEBUG_COMMAND_HELP, ActionIds.DEBUG_COMMAND_LIST,
                ActionIds.DEBUG_COMMAND_OFF, ActionIds.DEBUG_COMMAND_TOGGLE)) {
            registry.registerResult(id, ActionHandler.delegatingToV1(id));
        }
    }

    @Override
    public Set<String> getDependencies() {
        return Set.of();
    }

    // ── Factory helpers ──

    private static ActionSpec adminSpec(String id, String labelKey, String descKey,
                                        String iconItemId, String menuPath, String commandHint,
                                        ActionCategory category, int permLevel) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.SERVER)
            .category(category)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(permLevel)
            .ui(labelKey, descKey, iconItemId, menuPath, false)
            .binding(null, commandHint, null)
            .policy(false, 0, permLevel > 0 ? "requiresPermissionOrClient_" + permLevel : null)
            .telemetryTags("admin")
            .build();
    }

    private static ActionSpec adminPromptSpec(String id, String labelKey, String descKey,
                                              String iconItemId, String menuPath, String commandHint,
                                              ActionCategory category, int permLevel) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.BOTH)
            .category(category)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(permLevel)
            .ui(labelKey, descKey, iconItemId, menuPath, false)
            .binding(null, commandHint, null)
            .policy(false, 0, permLevel > 0 ? "requiresPermissionOrClient_" + permLevel : null)
            .telemetryTags("admin")
            .build();
    }
}
