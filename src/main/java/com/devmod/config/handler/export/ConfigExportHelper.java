package com.devmod.config.handler.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.devmod.config.handler.IConfigComponent;
import com.devmod.config.handler.IDecomposedConfig;
/**
 * Helper class for building command strings from decomposed configs.
 */
public final class ConfigExportHelper {

    private static final String CATEGORY_PREFIX = "// ─── ";
    private static final String CATEGORY_SUFFIX = " ───";

    private ConfigExportHelper() {}

    /**
     * Build a setStats command string from item and decomposed config.
     *
     * <p>Output format (pretty):
     * <pre>
     * // ═══════════════════════════════════════════════════════════════
     * // Diamond Sword (minecraft:diamond_sword)
     * // Type: Weapon | Source: minecraft
     * // ═══════════════════════════════════════════════════════════════
     * DevMod.weapons.setStats(&lt;item:minecraft:diamond_sword&gt;, {
     *     // ─── Combat ───
     *     attackDamage: 12.5f,
     *     attackSpeed: 1.6f,
     *
     *     // ─── Critical ───
     *     critChance: 0.25f
     * });
     * </pre>
     *
     * @param namespace the command namespace (e.g., "weapons")
     * @param item the item
     * @param decomposed the decomposed stats
     * @param prettyPrint whether to format nicely with newlines and categories
     * @param skipDefaults whether to skip values equal to their defaults
     * @return the formatted command string
     */
    public static String buildSetStatsCommand(
            String namespace,
            Item item,
            IDecomposedConfig decomposed,
            boolean prettyPrint,
            boolean skipDefaults) {

        StringBuilder sb = new StringBuilder();

        // Header for pretty print
        if (prettyPrint) {
            appendHeader(sb, namespace, item);
        }

        // Command prefix
        sb.append("DevMod.").append(namespace).append(".setStats(");
        sb.append(CommandStringUtil.itemBracket(item));
        sb.append(", ");

        // Build categorized entries
        Map<String, List<EntryData>> categorizedEntries = new LinkedHashMap<>();

        for (IConfigComponent<?> component : decomposed.components()) {
            Object value = decomposed.get(component).orElse(null);
            if (value == null) continue;

            // Skip defaults if requested
            if (skipDefaults && value.equals(component.defaultValue())) {
                continue;
            }

            String category = component.category();
            String serialized = serializeComponentValue(component, value);

            categorizedEntries
                .computeIfAbsent(category, k -> new ArrayList<>())
                .add(new EntryData(component.id(), serialized));
        }

        if (categorizedEntries.isEmpty()) {
            sb.append("{}");
        } else if (prettyPrint) {
            appendPrettyBody(sb, categorizedEntries);
        } else {
            appendCompactBody(sb, categorizedEntries);
        }

        sb.append(");");
        return sb.toString();
    }

    /**
     * Append header block with item info.
     */
    private static void appendHeader(StringBuilder sb, String namespace, Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String displayName = getItemDisplayName(item);
        String typeName = namespace.substring(0, 1).toUpperCase(Locale.ROOT)
                        + namespace.substring(1, namespace.length() - 1); // "weapons" -> "Weapon"

        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
        sb.append("// ").append(displayName).append(" (").append(itemId).append(")\n");
        sb.append("// Type: ").append(typeName).append(" | Source: ").append(itemId.getNamespace()).append("\n");
        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
    }

    /**
     * Append pretty-printed body with category grouping.
     */
    private static void appendPrettyBody(StringBuilder sb, Map<String, List<EntryData>> categorizedEntries) {
        sb.append("{\n");

        List<String> categories = new ArrayList<>(categorizedEntries.keySet());
        for (int catIdx = 0; catIdx < categories.size(); catIdx++) {
            String category = categories.get(catIdx);
            List<EntryData> entries = categorizedEntries.get(category);

            // Category header
            sb.append("    ").append(CATEGORY_PREFIX).append(category).append(CATEGORY_SUFFIX).append("\n");

            // Entries
            for (int i = 0; i < entries.size(); i++) {
                EntryData entry = entries.get(i);
                sb.append("    ").append(entry.key).append(": ").append(entry.value);

                // Comma logic: add comma unless it's the last entry of the last category
                boolean isLastEntry = (i == entries.size() - 1);
                boolean isLastCategory = (catIdx == categories.size() - 1);
                if (!(isLastEntry && isLastCategory)) {
                    sb.append(",");
                }
                sb.append("\n");
            }

            // Blank line between categories (except after last)
            if (catIdx < categories.size() - 1) {
                sb.append("\n");
            }
        }

        sb.append("}");
    }

    /**
     * Append compact single-line body.
     */
    private static void appendCompactBody(StringBuilder sb, Map<String, List<EntryData>> categorizedEntries) {
        sb.append("{");

        List<EntryData> allEntries = new ArrayList<>();
        for (List<EntryData> entries : categorizedEntries.values()) {
            allEntries.addAll(entries);
        }

        for (int i = 0; i < allEntries.size(); i++) {
            EntryData entry = allEntries.get(i);
            sb.append(entry.key).append(": ").append(entry.value);
            if (i < allEntries.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append("}");
    }

    /**
     * Get display name for an item.
     */
    private static String getItemDisplayName(Item item) {
        try {
            Component name = item.getDescription();
            return name.getString();
        } catch (Exception e) {
            // Fallback to registry name
            return BuiltInRegistries.ITEM.getKey(item).getPath()
                .replace("_", " ")
                .replace("/", " ");
        }
    }

    /**
     * Serialize a component value using the component's toCommandString if available.
     */
    @SuppressWarnings("unchecked")
    private static <T> String serializeComponentValue(IConfigComponent<T> component, Object value) {
        try {
            return component.toCommandString((T) value);
        } catch (Exception e) {
            // Fallback to generic formatter
            return CommandStringUtil.formatValue(value);
        }
    }

    /**
     * Build a compact single-line setStats command.
     */
    public static String buildCompactCommand(
            String namespace,
            Item item,
            IDecomposedConfig decomposed,
            boolean skipDefaults) {
        return buildSetStatsCommand(namespace, item, decomposed, false, skipDefaults);
    }

    /**
     * Build a pretty-printed setStats command with indentation.
     */
    public static String buildPrettyCommand(
            String namespace,
            Item item,
            IDecomposedConfig decomposed,
            boolean skipDefaults) {
        return buildSetStatsCommand(namespace, item, decomposed, true, skipDefaults);
    }

    /**
     * Simple data holder for key-value entries.
     */
    private record EntryData(String key, String value) {}
}
