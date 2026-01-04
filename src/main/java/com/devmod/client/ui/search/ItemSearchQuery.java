package com.devmod.client.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Advanced item search query parser with EMI-like prefix support.
 * <p>
 * Supported prefixes:
 * <ul>
 *   <li>{@code @modid} - Search by mod namespace (e.g., @minecraft, @devmod)</li>
 *   <li>{@code #tag} - Search by item tag (e.g., #logs, #planks)</li>
 *   <li>{@code $text} - Search in tooltip text</li>
 * </ul>
 * <p>
 * Multiple terms can be combined with spaces. All terms must match (AND logic).
 * Example: {@code @minecraft #logs oak} - finds minecraft items with logs tag containing "oak"
 */
public final class ItemSearchQuery {

    /** Pattern to extract prefixed terms: @mod, #tag, $tooltip, or plain text */
    private static final Pattern TERM_PATTERN = Pattern.compile(
        "(@[\\w:.-]+|#[\\w:.-]+|\\$[\\w]+|[^@#$\\s]+)"
    );

    private final String rawQuery;
    private final List<SearchTerm> terms;

    private ItemSearchQuery(String rawQuery, List<SearchTerm> terms) {
        this.rawQuery = Objects.requireNonNull(rawQuery);
        this.terms = Objects.requireNonNull(terms);
    }

    /**
     * Parse a search query string into a structured query object.
     *
     * @param query The raw query string
     * @return A parsed ItemSearchQuery
     */
    @Nonnull
    public static ItemSearchQuery parse(@Nonnull String query) {
        Objects.requireNonNull(query, "query");
        String normalized = query.toLowerCase(Locale.ROOT).trim();

        if (normalized.isEmpty()) {
            return new ItemSearchQuery(query, List.of());
        }

        List<SearchTerm> terms = new ArrayList<>();
        Matcher matcher = TERM_PATTERN.matcher(normalized);

        while (matcher.find()) {
            String term = matcher.group(1);
            if (term.startsWith("@")) {
                // Mod namespace search
                terms.add(new ModTerm(term.substring(1)));
            } else if (term.startsWith("#")) {
                // Tag search
                terms.add(new TagTerm(term.substring(1)));
            } else if (term.startsWith("$")) {
                // Tooltip search
                terms.add(new TooltipTerm(term.substring(1)));
            } else {
                // Plain text (name or ID)
                terms.add(new NameTerm(term));
            }
        }

        return new ItemSearchQuery(query, terms);
    }

    /**
     * Check if an item matches all search terms.
     *
     * @param stack The ItemStack to test
     * @return true if all terms match
     */
    public boolean matches(@Nonnull ItemStack stack) {
        Objects.requireNonNull(stack, "stack");

        if (terms.isEmpty()) {
            return true; // Empty query matches everything
        }

        for (SearchTerm term : terms) {
            if (!term.matches(stack)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if this query is empty (matches everything).
     */
    public boolean isEmpty() {
        return terms.isEmpty();
    }

    /**
     * Get the raw query string.
     */
    public String getRawQuery() {
        return rawQuery;
    }

    /**
     * Get a hint text explaining the search syntax.
     */
    @Nonnull
    public static String getHintText() {
        return "@mod #tag $tooltip";
    }

    // ═══════════════════════════════════════════════════════════════
    // SEARCH TERM TYPES
    // ═══════════════════════════════════════════════════════════════

    private interface SearchTerm {
        boolean matches(ItemStack stack);
    }

    /**
     * Matches item name or registry ID.
     */
    private record NameTerm(String text) implements SearchTerm {
        @Override
        public boolean matches(ItemStack stack) {
            // Match against display name
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(text)) {
                return true;
            }

            // Match against registry ID
            Item item = Objects.requireNonNull(stack.getItem());
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            String id = key.toString().toLowerCase(Locale.ROOT);
            return id.contains(text);
        }
    }

    /**
     * Matches mod namespace (e.g., @minecraft, @devmod).
     */
    private record ModTerm(String modId) implements SearchTerm {
        @Override
        public boolean matches(ItemStack stack) {
            Item item = Objects.requireNonNull(stack.getItem());
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            String namespace = key.getNamespace().toLowerCase(Locale.ROOT);
            return namespace.contains(modId);
        }
    }

    /**
     * Matches item tags (e.g., #logs, #planks).
     */
    private record TagTerm(String tagPattern) implements SearchTerm {
        @Override
        public boolean matches(ItemStack stack) {
            // Use getItemHolder() which is the non-deprecated way to get tags
            Holder<Item> holder = stack.getItemHolder();

            // Check all tags the item belongs to
            for (TagKey<Item> tag : holder.tags().toList()) {
                String tagId = tag.location().toString().toLowerCase(Locale.ROOT);
                String tagPath = tag.location().getPath().toLowerCase(Locale.ROOT);

                // Match against full tag ID or just path
                if (tagId.contains(tagPattern) || tagPath.contains(tagPattern)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Matches tooltip text (e.g., $fire, $damage).
     */
    private record TooltipTerm(String text) implements SearchTerm {
        @Override
        public boolean matches(ItemStack stack) {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                return false;
            }

            try {
                // Get tooltip lines
                List<Component> tooltipLines = stack.getTooltipLines(
                    Objects.requireNonNull(Item.TooltipContext.of(mc.level)),
                    mc.player,
                    Objects.requireNonNull(net.minecraft.world.item.TooltipFlag.NORMAL)
                );

                // Search in all tooltip lines (skip first which is the name)
                for (int i = 1; i < tooltipLines.size(); i++) {
                    String line = tooltipLines.get(i).getString().toLowerCase(Locale.ROOT);
                    if (line.contains(text)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Tooltip generation failed, skip
            }

            return false;
        }
    }
}
