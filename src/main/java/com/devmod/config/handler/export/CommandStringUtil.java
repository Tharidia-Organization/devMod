package com.devmod.config.handler.export;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for serializing values to DevMod command string format.
 * Inspired by CraftTweaker's CommandStringDisplayable pattern.
 */
public final class CommandStringUtil {

    /** Standard header line for export formatting. */
    public static final String HEADER_LINE = "// ═══════════════════════════════════════════════════════════════";

    /** Empty item bracket notation. */
    public static final String EMPTY_ITEM = "<item:empty>";

    private CommandStringUtil() {}

    // ═══════════════════════════════════════════════════════════════
    // BRACKET SYNTAX
    // ═══════════════════════════════════════════════════════════════

    /**
     * Convert item to bracket notation: {@code <item:minecraft:diamond_sword>}
     */
    public static String itemBracket(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return resourceLocationBracket(id);
    }

    /**
     * Convert ResourceLocation to item bracket notation: {@code <item:minecraft:diamond>}
     */
    public static String resourceLocationBracket(ResourceLocation id) {
        if (id == null) {
            return EMPTY_ITEM;
        }
        return "<item:" + id.getNamespace() + ":" + id.getPath() + ">";
    }

    /**
     * Convert ResourceLocation to tag bracket notation: {@code <tag:minecraft:logs>}
     */
    public static String tagBracket(ResourceLocation id) {
        if (id == null) {
            return EMPTY_ITEM;
        }
        return "<tag:" + id.getNamespace() + ":" + id.getPath() + ">";
    }

    /**
     * Convert ItemStack to bracket notation.
     */
    public static String itemStackBracket(ItemStack stack) {
        return itemBracket(stack.getItem());
    }

    // ═══════════════════════════════════════════════════════════════
    // VALUE FORMATTING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Format float value with 'f' suffix.
     */
    public static String formatFloat(float value) {
        if (value == (int) value) {
            return String.format(Locale.US, "%.1ff", value);
        }
        return String.format(Locale.US, "%sf", trimTrailingZeros(value));
    }

    /**
     * Format double value.
     */
    public static String formatDouble(double value) {
        if (value == (long) value) {
            return String.format(Locale.US, "%.1f", value);
        }
        return trimTrailingZeros(value);
    }

    /**
     * Format integer value.
     */
    public static String formatInt(int value) {
        return String.valueOf(value);
    }

    /**
     * Format long value.
     */
    public static String formatLong(long value) {
        return String.valueOf(value);
    }

    /**
     * Format boolean value (lowercase).
     */
    public static String formatBoolean(boolean value) {
        return value ? "true" : "false";
    }

    /**
     * Quote and escape a string value.
     */
    public static String quoteAndEscape(String value) {
        if (value == null) return "null";
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            + "\"";
    }

    /**
     * Format a list of values.
     */
    public static <T> String formatList(List<T> list, Function<T, String> formatter) {
        if (list == null || list.isEmpty()) return "[]";
        return "[" + list.stream()
            .map(formatter)
            .collect(Collectors.joining(", ")) + "]";
    }

    // ═══════════════════════════════════════════════════════════════
    // GENERIC VALUE FORMATTING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Format any value based on its type.
     * Automatically dispatches to the correct formatter.
     */
    public static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Float f) return formatFloat(f);
        if (value instanceof Double d) return formatDouble(d);
        if (value instanceof Integer i) return formatInt(i);
        if (value instanceof Long l) return formatLong(l);
        if (value instanceof Boolean b) return formatBoolean(b);
        if (value instanceof String s) return quoteAndEscape(s);
        if (value instanceof Item item) return itemBracket(item);
        if (value instanceof ItemStack stack) return itemStackBracket(stack);
        if (value instanceof List<?> list) {
            return formatList(list, CommandStringUtil::formatValue);
        }
        // Fallback: toString
        return value.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static String trimTrailingZeros(double value) {
        String str = String.format(Locale.US, "%.6f", value);
        // Remove trailing zeros but keep at least one decimal
        str = str.replaceAll("0+$", "");
        if (str.endsWith(".")) str += "0";
        return str;
    }

    private static String trimTrailingZeros(float value) {
        return trimTrailingZeros((double) value);
    }
}
