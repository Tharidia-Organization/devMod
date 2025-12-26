package com.devmod.client.ui.editor.systems;

import java.util.ArrayList;
import java.util.List;

import com.devmod.client.ui.editor.core.UIConstants;

public final class DirtyState {

    private boolean isDirty = false;
    private final List<String> pendingChanges = new ArrayList<>();
    private long lastSaveTimestamp = 0;

    /**
     * Mark state as modified.
     * @param changeDescription Human-readable change description
     */
    public void markDirty(String changeDescription) {
        isDirty = true;
        if (!pendingChanges.contains(changeDescription)) {
            pendingChanges.add(changeDescription);
            // Keep only last 10 changes for display
            while (pendingChanges.size() > 10) {
                pendingChanges.remove(0);
            }
        }
    }

    /**
     * Clear dirty state after successful save.
     */
    public void clearDirty() {
        isDirty = false;
        pendingChanges.clear();
        lastSaveTimestamp = System.currentTimeMillis();
    }

    public boolean isDirty() {
        return isDirty;
    }

    public boolean hasUnsavedChanges() {
        return isDirty && !pendingChanges.isEmpty();
    }

    public int getPendingCount() {
        return pendingChanges.size();
    }

    public List<String> getPendingChanges() {
        return List.copyOf(pendingChanges);
    }

    public long getLastSaveTimestamp() {
        return lastSaveTimestamp;
    }

    /**
     * Get display text for dirty indicator.
     */
    public DirtyIndicator getIndicator() {
        if (hasUnsavedChanges()) {
            return new DirtyIndicator(
                "● " + pendingChanges.size() + " unsaved",
                UIConstants.Accent.ORANGE(),
                true
            );
        } else if (lastSaveTimestamp > 0) {
            long ago = System.currentTimeMillis() - lastSaveTimestamp;
            String timeText = formatTimeAgo(ago);
            return new DirtyIndicator(
                "✓ Saved " + timeText,
                UIConstants.Accent.GREEN(),
                false
            );
        } else {
            return new DirtyIndicator("", 0, false);
        }
    }

    private String formatTimeAgo(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return "just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " min ago";
        return "over 1h ago";
    }

    public record DirtyIndicator(String text, int color, boolean showIcon) {}
}
