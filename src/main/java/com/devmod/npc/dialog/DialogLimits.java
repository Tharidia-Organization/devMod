package com.devmod.npc.dialog;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Shared limits for NPC dialog data across UI, network, and persistence.
 */
public final class DialogLimits {
    private DialogLimits() {
    }

    public static final int MAX_DIALOG_ID_LENGTH = 64;
    public static final int MAX_DIALOG_NAME_LENGTH = 64;
    public static final int MAX_NODE_ID_LENGTH = 64;
    public static final int MAX_OPTION_ID_LENGTH = 64;
    public static final int MAX_OPTION_LABEL_LENGTH = 128;
    public static final int MAX_OPTION_ICON_LENGTH = 32;
    public static final int MAX_LINES_PER_NODE = 20;
    public static final int MAX_LINE_LENGTH = 512;
    public static final int MAX_OPTIONS_PER_NODE = 10;
    public static final int MAX_NODES = 200;

    public static final int MAX_ACTION_TYPE_LENGTH = 32;
    public static final int MAX_ACTION_PARAM_LENGTH = 256;
    public static final int MAX_COMMAND_LENGTH = 256;
    public static final int MAX_GUI_ID_LENGTH = 64;
    public static final int MAX_ZONE_ID_LENGTH = 64;
    public static final int MAX_VARIABLE_KEY_LENGTH = 64;
    public static final int MAX_VARIABLE_VALUE_LENGTH = 256;
    public static final int MAX_CUSTOM_HANDLER_LENGTH = 64;

    public static final int MAX_CONDITION_TYPE_LENGTH = 32;
    public static final int MAX_CONDITION_CHILDREN = 16;
    public static final int MAX_QUEST_ID_LENGTH = 64;
    public static final int MAX_DATE_LENGTH = 16;
    public static final int MAX_SCHEDULE_DAYS = 7;

    public static String truncate(@Nullable String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public static List<String> clampLines(@Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(Math.min(lines.size(), MAX_LINES_PER_NODE));
        for (String line : lines) {
            if (result.size() >= MAX_LINES_PER_NODE) {
                break;
            }
            result.add(truncate(line, MAX_LINE_LENGTH));
        }
        return List.copyOf(result);
    }
}
