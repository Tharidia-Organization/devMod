package com.devmod.arena.template;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

public class TemplatePlaceholder {

    /**
     * DD9: Placeholder pattern ${key} with alphanumeric keys.
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    /**
     * DD9: Maximum replacement iterations to prevent infinite loops.
     */
    private static final int MAX_ITERATIONS = 10;

    private final Map<String, PlaceholderResolver> resolvers = new HashMap<>();
    private boolean builtinsRegistered = false;

    public TemplatePlaceholder() {
        // Built-ins will be registered lazily on first resolve/register call
    }

    /**
     * Resolves all placeholders in the given text.
     * DD9: Replaces ${key} with resolved values.
     *
     * @param text the text containing placeholders
     * @param context the resolution context
     * @return text with placeholders resolved
     */
    public String resolve(String text, PlaceholderContext context) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        int iterations = 0;

        // Iterate until no more placeholders or max iterations reached
        while (iterations < MAX_ITERATIONS) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
            StringBuffer sb = new StringBuffer();
            boolean found = false;

            while (matcher.find()) {
                found = true;
                String key = matcher.group(1);
                String replacement = resolvePlaceholder(key, context);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);

            if (!found) {
                break;
            }
            result = sb.toString();
            iterations++;
        }

        return result;
    }

    /**
     * Registers a custom placeholder resolver.
     * DD9: Extensible placeholder system.
     *
     * @param key the placeholder key (without ${})
     * @param resolver the resolver function
     */
    public void register(String key, PlaceholderResolver resolver) {
        resolvers.put(key.toLowerCase(Locale.ROOT), resolver);
    }

    /**
     * Unregisters a placeholder resolver.
     *
     * @param key the placeholder key
     */
    public void unregister(String key) {
        resolvers.remove(key.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if a placeholder key is registered.
     *
     * @param key the placeholder key
     * @return true if registered
     */
    public boolean isRegistered(String key) {
        return resolvers.containsKey(key.toLowerCase(Locale.ROOT));
    }

    private String resolvePlaceholder(String key, PlaceholderContext context) {
        ensureBuiltins();
        PlaceholderResolver resolver = resolvers.get(key.toLowerCase(Locale.ROOT));
        if (resolver != null) {
            Optional<String> result = resolver.resolve(context);
            if (result.isPresent()) {
                return result.get();
            }
        }

        // Check context custom values
        String custom = context.getCustomValue(key);
        if (custom != null) {
            return custom;
        }

        // Return original placeholder if not resolved
        return "${" + key + "}";
    }

    private void registerBuiltins() {
        register("player_name", ctx -> Optional.ofNullable(ctx.playerName()));
        register("player_uuid", ctx -> Optional.ofNullable(ctx.playerId()).map(UUID::toString));
        register("party_size", ctx -> Optional.of(String.valueOf(ctx.partySize())));
        register("arena_id", ctx -> Optional.ofNullable(ctx.arenaId()).map(UUID::toString));
        register("template_id", ctx -> Optional.ofNullable(ctx.templateId()));
        register("timestamp", ctx -> Optional.of(java.time.Instant.now().toString()));
        register("random_int", ctx -> Optional.of(String.valueOf((int) (Math.random() * 1000))));
    }

    private void ensureBuiltins() {
        if (builtinsRegistered) {
            return;
        }
        registerBuiltins();
        builtinsRegistered = true;
    }

    /**
     * Functional interface for placeholder resolution.
     */
    @FunctionalInterface
    public interface PlaceholderResolver {
        Optional<String> resolve(PlaceholderContext context);
    }

    /**
     * Context for placeholder resolution.
     * DD9: Contains all values needed for built-in placeholders.
     */
    public record PlaceholderContext(
        @Nullable String playerName,
        @Nullable UUID playerId,
        int partySize,
        @Nullable UUID arenaId,
        @Nullable String templateId,
        Map<String, String> customValues
    ) {
        public PlaceholderContext {
            customValues = customValues != null ? Map.copyOf(customValues) : Map.of();
        }

        @Nullable
        public String getCustomValue(String key) {
            return customValues.get(key.toLowerCase(Locale.ROOT));
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String playerName;
            private UUID playerId;
            private int partySize = 1;
            private UUID arenaId;
            private String templateId;
            private final Map<String, String> customValues = new HashMap<>();

            public Builder playerName(String name) {
                this.playerName = name;
                return this;
            }

            public Builder playerId(UUID id) {
                this.playerId = id;
                return this;
            }

            public Builder partySize(int size) {
                this.partySize = size;
                return this;
            }

            public Builder arenaId(UUID id) {
                this.arenaId = id;
                return this;
            }

            public Builder templateId(String id) {
                this.templateId = id;
                return this;
            }

            public Builder custom(String key, String value) {
                this.customValues.put(key.toLowerCase(Locale.ROOT), value);
                return this;
            }

            public PlaceholderContext build() {
                return new PlaceholderContext(playerName, playerId, partySize, arenaId, templateId, customValues);
            }
        }
    }
}
