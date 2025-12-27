package com.devmod.mailbox.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionCommandInvoker;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsCategory;
import com.devmod.mailbox.news.NewsManager;

/**
 * Admin commands for the mailbox system.
 *
 * Provides commands for:
 * - Sending messages to players
 * - Broadcasting messages to all players
 * - Managing news articles
 * - Viewing system statistics
 */
public final class MailboxCommands {

    private MailboxCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("mailbox")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("help")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.MAILBOX_COMMAND_HELP, ctx)))
                .then(Commands.literal("stats")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.MAILBOX_COMMAND_STATS, ctx)))
                .then(Commands.literal("send")
                    .then(Commands.argument("player", Objects.requireNonNull(UuidArgument.uuid()))
                        .then(Commands.argument("subject", Objects.requireNonNull(StringArgumentType.string()))
                            .then(Commands.argument("body", Objects.requireNonNull(StringArgumentType.greedyString()))
                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.MAILBOX_COMMAND_SEND, ctx))))))
                .then(Commands.literal("broadcast")
                    .then(Commands.argument("subject", Objects.requireNonNull(StringArgumentType.string()))
                        .then(Commands.argument("body", Objects.requireNonNull(StringArgumentType.greedyString()))
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.MAILBOX_COMMAND_BROADCAST, ctx)))))
                .then(Commands.literal("inbox")
                    .then(Commands.argument("player", Objects.requireNonNull(UuidArgument.uuid()))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.MAILBOX_COMMAND_INBOX, ctx))))
                .then(Commands.literal("purge")
                    .then(Commands.argument("player", Objects.requireNonNull(UuidArgument.uuid()))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.MAILBOX_COMMAND_PURGE, ctx))))
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.MAILBOX_COMMAND_HELP, ctx))
        );

        dispatcher.register(
            Commands.literal("news")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("help")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.NEWS_COMMAND_HELP, ctx)))
                .then(Commands.literal("list")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.NEWS_COMMAND_LIST, ctx)))
                .then(Commands.literal("create")
                    .then(Commands.argument("category", Objects.requireNonNull(StringArgumentType.word()))
                        .suggests(MailboxCommands::suggestCategories)
                        .then(Commands.argument("title", Objects.requireNonNull(StringArgumentType.string()))
                            .then(Commands.argument("content", Objects.requireNonNull(StringArgumentType.greedyString()))
                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.NEWS_COMMAND_CREATE, ctx))))))
                .then(Commands.literal("delete")
                    .then(Commands.argument("id", Objects.requireNonNull(UuidArgument.uuid()))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.NEWS_COMMAND_DELETE, ctx))))
                .then(Commands.literal("publish")
                    .then(Commands.argument("id", Objects.requireNonNull(UuidArgument.uuid()))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.NEWS_COMMAND_PUBLISH, ctx))))
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.NEWS_COMMAND_HELP, ctx))
        );
    }

    public static void registerActions() {
        // =====================================================================
        // MAILBOX COMMANDS
        // =====================================================================

        ActionRegistry.register(RadialAction.builder(ActionIds.MAILBOX_COMMAND_HELP)
            .labelKey("devmod.action.mailbox.command.help")
            .descriptionKey("devmod.action.mailbox.command.help.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/Mailbox/Help")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("mailbox help")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    showMailboxHelp(cmd);
                    return;
                }
                context.executeCommand("mailbox help");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.MAILBOX_COMMAND_STATS)
            .labelKey("devmod.action.mailbox.command.stats")
            .descriptionKey("devmod.action.mailbox.command.stats.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/Mailbox/Stats")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("mailbox stats")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    showMailboxStats(cmd);
                    return;
                }
                context.executeCommand("mailbox stats");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.MAILBOX_COMMAND_SEND)
            .labelKey("devmod.action.mailbox.command.send")
            .descriptionKey("devmod.action.mailbox.command.send.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/Mailbox/Send")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("mailbox send <player> <subject> <body>")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    sendMessage(cmd);
                    return;
                }
                if (!context.openCommandPrompt("mailbox send ")) {
                    context.executeCommand("mailbox help");
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.MAILBOX_COMMAND_BROADCAST)
            .labelKey("devmod.action.mailbox.command.broadcast")
            .descriptionKey("devmod.action.mailbox.command.broadcast.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/Mailbox/Broadcast")
            .icon(Items.BELL)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("mailbox broadcast <subject> <body>")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    broadcastMessage(cmd);
                    return;
                }
                if (!context.openCommandPrompt("mailbox broadcast ")) {
                    context.executeCommand("mailbox help");
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.MAILBOX_COMMAND_INBOX)
            .labelKey("devmod.action.mailbox.command.inbox")
            .descriptionKey("devmod.action.mailbox.command.inbox.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/Mailbox/View Inbox")
            .icon(Items.CHEST)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("mailbox inbox <player>")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    viewInbox(cmd);
                    return;
                }
                if (!context.openCommandPrompt("mailbox inbox ")) {
                    context.executeCommand("mailbox help");
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.MAILBOX_COMMAND_PURGE)
            .labelKey("devmod.action.mailbox.command.purge")
            .descriptionKey("devmod.action.mailbox.command.purge.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/Mailbox/Purge")
            .icon(Items.LAVA_BUCKET)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("mailbox purge <player>")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    purgeInbox(cmd);
                    return;
                }
                if (!context.openCommandPrompt("mailbox purge ")) {
                    context.executeCommand("mailbox help");
                }
            })
            .build());

        // =====================================================================
        // NEWS COMMANDS
        // =====================================================================

        ActionRegistry.register(RadialAction.builder(ActionIds.NEWS_COMMAND_HELP)
            .labelKey("devmod.action.news.command.help")
            .descriptionKey("devmod.action.news.command.help.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/News/Help")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("news help")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    showNewsHelp(cmd);
                    return;
                }
                context.executeCommand("news help");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.NEWS_COMMAND_LIST)
            .labelKey("devmod.action.news.command.list")
            .descriptionKey("devmod.action.news.command.list.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/News/List")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("news list")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    listNews(cmd);
                    return;
                }
                context.executeCommand("news list");
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.NEWS_COMMAND_CREATE)
            .labelKey("devmod.action.news.command.create")
            .descriptionKey("devmod.action.news.command.create.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/News/Create")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("news create <category> <title> <content>")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    createNews(cmd);
                    return;
                }
                if (!context.openCommandPrompt("news create ")) {
                    context.executeCommand("news help");
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.NEWS_COMMAND_DELETE)
            .labelKey("devmod.action.news.command.delete")
            .descriptionKey("devmod.action.news.command.delete.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/News/Delete")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("news delete <id>")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    deleteNews(cmd);
                    return;
                }
                if (!context.openCommandPrompt("news delete ")) {
                    context.executeCommand("news help");
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.NEWS_COMMAND_PUBLISH)
            .labelKey("devmod.action.news.command.publish")
            .descriptionKey("devmod.action.news.command.publish.desc")
            .category(ActionCategory.DEBUG)
            .menuPath("Root/Admin/News/Publish")
            .icon(Items.KNOWLEDGE_BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("news publish <id>")
            .handler(context -> {
                CommandContext<CommandSourceStack> cmd = context.getCommandContext();
                if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
                    publishNews(cmd);
                    return;
                }
                if (!context.openCommandPrompt("news publish ")) {
                    context.executeCommand("news help");
                }
            })
            .build());
    }

    // =========================================================================
    // MAILBOX COMMAND HANDLERS
    // =========================================================================

    private static int showMailboxHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§e=== Mailbox Admin Commands ==="), false);
        source.sendSuccess(() -> Component.literal("§7/mailbox stats §f- Show mailbox statistics"), false);
        source.sendSuccess(() -> Component.literal("§7/mailbox send <uuid> <subject> <body> §f- Send message"), false);
        source.sendSuccess(() -> Component.literal("§7/mailbox broadcast <subject> <body> §f- Broadcast to all"), false);
        source.sendSuccess(() -> Component.literal("§7/mailbox inbox <uuid> §f- View player inbox"), false);
        source.sendSuccess(() -> Component.literal("§7/mailbox purge <uuid> §f- Delete all player messages"), false);
        return 1;
    }

    private static int showMailboxStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        CompletableFuture<?> statsFuture = MailboxManager.INSTANCE.getTotalMessageCount().thenCombine(
            MailboxManager.INSTANCE.getTotalUnreadCount(),
            (total, unread) -> {
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§e=== Mailbox Statistics ===")
                ), false);
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§7Total messages: §f" + total)
                ), false);
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§7Unread messages: §f" + unread)
                ), false);

                MailboxConfig config = MailboxConfig.INSTANCE;
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§7Max per player: §f" + config.getMaxMessagesPerPlayer())
                ), false);
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§7Retention days: §f" + config.getMessageRetentionDays())
                ), false);
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§7API enabled: §f" + config.isApiEnabled())
                ), false);

                return null;
            }
        );
        observeFuture(statsFuture, source, "Failed to load mailbox stats");

        return 1;
    }

    private static int sendMessage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        UUID targetUuid = UuidArgument.getUuid(context, "player");
        String subject = StringArgumentType.getString(context, "subject");
        String body = StringArgumentType.getString(context, "body");
        String senderName = source.getTextName();

        MailboxManager.INSTANCE.sendAdminMessage(senderName, targetUuid, subject, body, null)
            .thenAccept(messageId -> {
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§aMessage sent to " + targetUuid + " (ID: " + messageId + ")")
                ), false);
            })
            .exceptionally(e -> {
                source.sendFailure(Objects.requireNonNull(
                    Component.literal("§cFailed to send message: " + e.getMessage())
                ));
                return null;
            });

        return 1;
    }

    private static int broadcastMessage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        String subject = StringArgumentType.getString(context, "subject");
        String body = StringArgumentType.getString(context, "body");
        String senderName = source.getTextName();

        MailboxManager.INSTANCE.broadcast(senderName, subject, body, MessageType.ADMIN)
            .thenAccept(count -> {
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§aBroadcast sent to " + count + " players")
                ), false);
            })
            .exceptionally(e -> {
                source.sendFailure(Objects.requireNonNull(
                    Component.literal("§cFailed to broadcast: " + e.getMessage())
                ));
                return null;
            });

        return 1;
    }

    private static int viewInbox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        UUID targetUuid = UuidArgument.getUuid(context, "player");

        CompletableFuture<?> inboxFuture = MailboxManager.INSTANCE.getMessages(targetUuid)
            .thenAccept(messages -> {
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§e=== Inbox for " + targetUuid + " ===")
                ), false);
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§7Total: §f" + messages.size() + " messages")
                ), false);

                int unread = (int) messages.stream().filter(m -> m.readAt() == null).count();
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§7Unread: §f" + unread)
                ), false);

                source.sendSuccess(() -> Objects.requireNonNull(Component.literal("")), false);

                messages.stream().limit(10).forEach(msg -> {
                    String readStatus = msg.readAt() == null ? "§c[UNREAD]" : "§a[READ]";
                    String attachStatus = msg.hasAttachment() && !msg.attachmentClaimed()
                        ? " §6[ATTACHMENT]" : "";

                    source.sendSuccess(() -> Objects.requireNonNull(
                        Component.literal(readStatus + " §f" + msg.subject() + attachStatus)
                    ), false);
                    source.sendSuccess(() -> Objects.requireNonNull(
                        Component.literal("  §8ID: " + msg.id().toString().substring(0, 8) +
                            " | From: " + msg.senderName() + " | Type: " + msg.messageType())
                    ), false);
                });

                if (messages.size() > 10) {
                    source.sendSuccess(() -> Objects.requireNonNull(
                        Component.literal("§7... and " + (messages.size() - 10) + " more")
                    ), false);
                }
            });
        observeFuture(inboxFuture, source, "Failed to load inbox");

        return 1;
    }

    private static int purgeInbox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        UUID targetUuid = UuidArgument.getUuid(context, "player");

        MailboxManager.INSTANCE.getMessages(targetUuid)
            .thenCompose(messages -> {
                List<CompletableFuture<Boolean>> deleteFutures = messages.stream()
                    .map(msg -> MailboxManager.INSTANCE.deleteMessage(msg.id()))
                    .toList();

                return CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture<?>[0]))
                    .thenApply(v -> messages.size());
            })
            .thenAccept(count -> {
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§aPurged " + count + " messages from " + targetUuid)
                ), false);
            })
            .exceptionally(e -> {
                source.sendFailure(Objects.requireNonNull(
                    Component.literal("§cFailed to purge: " + e.getMessage())
                ));
                return null;
            });

        return 1;
    }

    // =========================================================================
    // NEWS COMMAND HANDLERS
    // =========================================================================

    private static int showNewsHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§e=== News Admin Commands ==="), false);
        source.sendSuccess(() -> Component.literal("§7/news list §f- List all news articles"), false);
        source.sendSuccess(() -> Component.literal("§7/news create <category> <title> <content> §f- Create article"), false);
        source.sendSuccess(() -> Component.literal("§7/news delete <id> §f- Delete article"), false);
        source.sendSuccess(() -> Component.literal("§7/news publish <id> §f- Publish unpublished article"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eCategories: §f" +
            String.join(", ", Arrays.stream(NewsCategory.values()).map(Enum::name).toList())), false);
        return 1;
    }

    private static int listNews(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        CompletableFuture<?> newsFuture = NewsManager.INSTANCE.getAllNews()
            .thenAccept(articles -> {
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§e=== News Articles (" + articles.size() + ") ===")
                ), false);

                articles.forEach(article -> {
                    String status = article.publishedAt() != null ? "§a[PUBLISHED]" : "§c[DRAFT]";
                    String activeStatus = article.active() ? "" : " §8[INACTIVE]";

                    source.sendSuccess(() -> Objects.requireNonNull(
                        Component.literal(status + activeStatus + " §e[" + article.category() + "] §f" + article.title())
                    ), false);
                    source.sendSuccess(() -> Objects.requireNonNull(
                        Component.literal("  §8ID: " + article.id().toString().substring(0, 8) +
                            " | By: " + article.authorName() + " | Priority: " + article.priority())
                    ), false);
                });
            });
        observeFuture(newsFuture, source, "Failed to load news");

        return 1;
    }

    private static int createNews(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        String categoryStr = StringArgumentType.getString(context, "category");
        String title = StringArgumentType.getString(context, "title");
        String content = StringArgumentType.getString(context, "content");
        String authorName = source.getTextName();

        NewsCategory category;
        try {
            category = NewsCategory.valueOf(categoryStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Objects.requireNonNull(
                Component.literal("§cInvalid category: " + categoryStr + ". Valid: " +
                    String.join(", ", Arrays.stream(NewsCategory.values()).map(Enum::name).toList()))
            ));
            return 0;
        }

        NewsArticle article = NewsArticle.builder()
            .title(title)
            .content(content)
            .category(category)
            .authorName(authorName)
            .publishNow()
            .active(true)
            .build();

        NewsManager.INSTANCE.createArticle(article)
            .thenRun(() -> {
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal("§aNews article created: " + article.id())
                ), false);
            })
            .exceptionally(e -> {
                source.sendFailure(Objects.requireNonNull(
                    Component.literal("§cFailed to create article: " + e.getMessage())
                ));
                return null;
            });

        return 1;
    }

    private static int deleteNews(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        UUID articleId = UuidArgument.getUuid(context, "id");

        NewsManager.INSTANCE.deleteArticle(articleId)
            .thenAccept(success -> {
                if (success) {
                    source.sendSuccess(() -> Objects.requireNonNull(
                        Component.literal("§aNews article deleted: " + articleId)
                    ), false);
                } else {
                    source.sendFailure(Objects.requireNonNull(
                        Component.literal("§cArticle not found: " + articleId)
                    ));
                }
            })
            .exceptionally(e -> {
                source.sendFailure(Objects.requireNonNull(
                    Component.literal("§cFailed to delete article: " + e.getMessage())
                ));
                return null;
            });

        return 1;
    }

    private static int publishNews(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        UUID articleId = UuidArgument.getUuid(context, "id");

        NewsManager.INSTANCE.getArticle(articleId)
            .thenCompose(opt -> {
                if (opt.isEmpty()) {
                    source.sendFailure(Objects.requireNonNull(
                        Component.literal("§cArticle not found: " + articleId)
                    ));
                    return CompletableFuture.completedFuture(false);
                }

                NewsArticle article = opt.get();
                if (article.publishedAt() != null) {
                    source.sendFailure(Objects.requireNonNull(
                        Component.literal("§cArticle already published: " + articleId)
                    ));
                    return CompletableFuture.completedFuture(false);
                }

                return NewsManager.INSTANCE.updateArticle(article.withPublishedNow());
            })
            .thenAccept(success -> {
                if (success) {
                    source.sendSuccess(() -> Objects.requireNonNull(
                        Component.literal("§aNews article published: " + articleId)
                    ), false);
                }
            })
            .exceptionally(e -> {
                source.sendFailure(Objects.requireNonNull(
                    Component.literal("§cFailed to publish article: " + e.getMessage())
                ));
                return null;
            });

        return 1;
    }

    // =========================================================================
    // SUGGESTIONS
    // =========================================================================

    private static CompletableFuture<Suggestions> suggestCategories(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        Arrays.stream(NewsCategory.values())
            .map(c -> c.name().toLowerCase(Locale.ROOT))
            .filter(name -> name.startsWith(input))
            .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private static void observeFuture(
            CompletableFuture<?> future, CommandSourceStack source, String failureMessage) {
        CompletableFuture<?> observed = future.exceptionally(e -> {
            source.sendFailure(Objects.requireNonNull(
                Component.literal("§c" + failureMessage + ": " + e.getMessage())
            ));
            return null;
        });
        observed.isCancelled();
    }
}
