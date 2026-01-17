package com.devmod.npc.dialog;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import net.minecraft.server.level.ServerPlayer;

/**
 * Resolves placeholders in dialog text.
 * Supports built-in placeholders and custom variable registration.
 *
 * <p>Built-in placeholders:
 * <ul>
 *   <li>{player} - Player display name</li>
 *   <li>{player_uuid} - Player UUID</li>
 *   <li>{npc_name} - NPC display name</li>
 *   <li>{npc_id} - NPC UUID</li>
 *   <li>{quest_count} - Number of completed quests</li>
 *   <li>{time} - In-game time (formatted)</li>
 *   <li>{date} - Real-world date</li>
 *   <li>{dimension} - Current dimension name</li>
 *   <li>{visit_count} - Number of visits to this NPC</li>
 * </ul>
 */
public final class PlaceholderResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    /** Built-in resolvers */
    private static final Map<String, BiFunction<ServerPlayer, NpcContext, String>> RESOLVERS = new HashMap<>();

    /** Custom resolvers registered by other systems */
    private static final Map<String, BiFunction<ServerPlayer, NpcContext, String>> CUSTOM_RESOLVERS = new HashMap<>();

    static {
        // Player placeholders
        RESOLVERS.put("player", (player, ctx) -> player.getName().getString());
        RESOLVERS.put("player_uuid", (player, ctx) -> player.getStringUUID());

        // NPC placeholders
        RESOLVERS.put("npc_name", (player, ctx) -> ctx.npcName());
        RESOLVERS.put("npc_id", (player, ctx) -> ctx.npcId().toString());

        // Quest placeholders
        RESOLVERS.put("quest_count", (player, ctx) -> String.valueOf(ctx.getCompletedQuestCount(player.getUUID())));

        // Time placeholders
        RESOLVERS.put("time", (player, ctx) -> formatGameTime(player));
        RESOLVERS.put("date", (player, ctx) -> LocalDate.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE));

        // World placeholders
        RESOLVERS.put("dimension", (player, ctx) -> player.level().dimension().location().toString());

        // Visit placeholders
        RESOLVERS.put("visit_count", (player, ctx) -> {
            int count = ctx.getRegistry().getVisitCount(player.getUUID(), ctx.npcId());
            return String.valueOf(count);
        });
    }

    private PlaceholderResolver() {}

    /**
     * Resolves all placeholders in the given text.
     *
     * @param text    The text containing placeholders
     * @param player  The server player
     * @param context The NPC context
     * @return The text with all placeholders replaced
     */
    @Nonnull
    public static String resolve(@Nonnull String text, @Nonnull ServerPlayer player, @Nonnull NpcContext context) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");

        if (!text.contains("{")) {
            return text;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = resolvePlaceholder(key, player, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolves a single placeholder key.
     *
     * @param key     The placeholder key (without braces)
     * @param player  The server player
     * @param context The NPC context
     * @return The resolved value, or the original placeholder if not found
     */
    @Nonnull
    private static String resolvePlaceholder(String key, ServerPlayer player, NpcContext context) {
        // Check custom variables first (from NpcContext)
        String customValue = context.getVariable(key);
        if (customValue != null) {
            return customValue;
        }

        // Check custom resolvers
        BiFunction<ServerPlayer, NpcContext, String> customResolver = CUSTOM_RESOLVERS.get(key);
        if (customResolver != null) {
            try {
                String value = customResolver.apply(player, context);
                return value != null ? value : "{" + key + "}";
            } catch (Exception e) {
                return "{" + key + "}";
            }
        }

        // Check built-in resolvers
        BiFunction<ServerPlayer, NpcContext, String> resolver = RESOLVERS.get(key);
        if (resolver != null) {
            try {
                String value = resolver.apply(player, context);
                return value != null ? value : "{" + key + "}";
            } catch (Exception e) {
                return "{" + key + "}";
            }
        }

        // Not found - return original placeholder
        return "{" + key + "}";
    }

    /**
     * Registers a custom placeholder resolver.
     *
     * @param key      The placeholder key (without braces)
     * @param resolver The resolver function
     */
    public static void register(@Nonnull String key, @Nonnull BiFunction<ServerPlayer, NpcContext, String> resolver) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolver, "resolver");
        CUSTOM_RESOLVERS.put(key, resolver);
    }

    /**
     * Unregisters a custom placeholder resolver.
     *
     * @param key The placeholder key to remove
     */
    public static void unregister(@Nonnull String key) {
        CUSTOM_RESOLVERS.remove(key);
    }

    /**
     * Clears all custom resolvers.
     */
    public static void clearCustomResolvers() {
        CUSTOM_RESOLVERS.clear();
    }

    /**
     * Formats the in-game time as a human-readable string.
     */
    private static String formatGameTime(ServerPlayer player) {
        long dayTime = player.level().getDayTime() % 24000;
        int hours = (int) ((dayTime / 1000 + 6) % 24);
        int minutes = (int) ((dayTime % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }
}
