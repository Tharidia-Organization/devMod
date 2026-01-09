package com.devmod.config.handler.export;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import net.minecraft.world.item.Item;

import com.devmod.config.handler.IConfigHandler;
import com.devmod.config.stats.IItemStats;
/**
 * Extension of IConfigHandler that supports export to command strings.
 * Inspired by CraftTweaker's dumpToCommandString pattern.
 *
 * @param <S> The stats type this handler manages
 */
public interface IExportableConfigHandler<S extends IItemStats> extends IConfigHandler<S> {

    /**
     * Get the namespace prefix for commands (e.g., "weapons", "armor").
     * Used in command format: {@code DevMod.<namespace>.setStats(...)}
     */
    String getCommandNamespace();

    /**
     * Export a single item's stats to command string format.
     *
     * @param item the item
     * @param stats the stats to export
     * @param prettyPrint whether to format with newlines/indentation
     * @return the command string
     */
    String exportToCommandString(Item item, S stats, boolean prettyPrint);

    /**
     * Export all global stats to command strings.
     *
     * @param prettyPrint whether to format with newlines/indentation
     * @return list of command strings, one per item
     */
    List<String> exportAllToCommandStrings(boolean prettyPrint);

    /**
     * Export all global stats to a single script block with header.
     *
     * @param prettyPrint whether to format with newlines/indentation
     * @return complete script content
     */
    default String exportAllToScript(boolean prettyPrint) {
        StringBuilder sb = new StringBuilder();
        sb.append("// DevMod Config Export - ").append(getCommandNamespace()).append("\n");
        sb.append("// Generated: ").append(ZonedDateTime.now(ZoneId.systemDefault())).append("\n\n");

        List<String> commands = exportAllToCommandStrings(prettyPrint);
        for (String cmd : commands) {
            sb.append(cmd);
            if (prettyPrint) {
                sb.append("\n\n");
            } else {
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
