package com.devmod.client.ui.editor.snapshot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ItemEditorHistoryEntry {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    public long timestamp;
    public String action = "change";
    public String itemId = "";
    public String itemName = "";
    @Nullable
    public String slotId = null;
    public ItemEditorSnapshot before;
    public ItemEditorSnapshot after;

    public ItemEditorHistoryEntry(ItemEditorSnapshot before, ItemEditorSnapshot after, String action) {
        this.before = Objects.requireNonNull(before, "before snapshot");
        this.after = Objects.requireNonNull(after, "after snapshot");
        this.action = action == null ? "change" : action;
        this.timestamp = System.currentTimeMillis();
        this.itemId = after.meta.itemId == null ? "" : after.meta.itemId;
        this.itemName = after.meta.itemName == null ? "" : after.meta.itemName;
        this.slotId = after.meta.slotId;
    }

    public String formatTimestamp() {
        return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    public String formatLabel() {
        String itemLabel = itemName == null || itemName.isBlank() ? itemId : itemName;
        if (itemLabel.isBlank()) {
            itemLabel = "Item";
        }
        return formatTimestamp() + "  " + action + "  " + itemLabel;
    }

    public String formatDiffSummary() {
        JsonObject beforeObj = asObject(before.item);
        JsonObject afterObj = asObject(after.item);
        String beforeId = getString(beforeObj, "id");
        String afterId = getString(afterObj, "id");
        boolean idChanged = !Objects.equals(beforeId, afterId);

        JsonObject beforeComponents = getObject(beforeObj, "components");
        JsonObject afterComponents = getObject(afterObj, "components");
        DiffCounts diff = diffKeys(beforeComponents, afterComponents);
        StringBuilder summary = new StringBuilder();
        if (idChanged) {
            summary.append("id changed");
        }
        if (diff.added > 0 || diff.removed > 0 || diff.changed > 0) {
            if (summary.length() > 0) summary.append(" · ");
            summary.append("components +").append(diff.added)
                .append(" ~").append(diff.changed)
                .append(" -").append(diff.removed);
        }
        if (summary.length() == 0) {
            summary.append("no component changes");
        }
        return summary.toString();
    }

    private static DiffCounts diffKeys(JsonObject beforeObj, JsonObject afterObj) {
        Set<String> keys = new HashSet<>();
        if (beforeObj != null) keys.addAll(beforeObj.keySet());
        if (afterObj != null) keys.addAll(afterObj.keySet());
        int added = 0;
        int removed = 0;
        int changed = 0;
        for (String key : keys) {
            JsonElement before = beforeObj == null ? null : beforeObj.get(key);
            JsonElement after = afterObj == null ? null : afterObj.get(key);
            if (before == null && after != null) {
                added++;
            } else if (before != null && after == null) {
                removed++;
            } else if (before != null && after != null && !before.equals(after)) {
                changed++;
            }
        }
        return new DiffCounts(added, removed, changed);
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement value = obj.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return new JsonObject();
        JsonElement value = obj.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonObject asObject(@Nullable JsonElement element) {
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return new JsonObject();
    }

    private record DiffCounts(int added, int removed, int changed) {}
}
