package com.devmod.client.npc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogSet;

/**
 * Manages undo/redo history for the dialog editor.
 * Stores snapshots of editor state for restoring previous versions.
 *
 * <p>Usage:
 * <pre>
 * DialogEditorHistory history = new DialogEditorHistory();
 * history.push(currentState);  // Before making changes
 * // ... make changes ...
 * EditorState previous = history.undo(currentState);  // Ctrl+Z
 * EditorState restored = history.redo(currentState);  // Ctrl+Y
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class DialogEditorHistory {

    /** Maximum number of states to keep in history */
    private static final int MAX_HISTORY_SIZE = 50;

    private final Deque<EditorState> undoStack = new ArrayDeque<>(MAX_HISTORY_SIZE);
    private final Deque<EditorState> redoStack = new ArrayDeque<>(MAX_HISTORY_SIZE);

    /**
     * Pushes the current state onto the undo stack.
     * Call this BEFORE making any modifications.
     * Clears the redo stack since a new branch is being created.
     *
     * @param state The current editor state to save
     */
    public void push(@Nonnull EditorState state) {
        // Limit stack size
        if (undoStack.size() >= MAX_HISTORY_SIZE) {
            undoStack.removeLast();
        }

        undoStack.push(state);
        redoStack.clear();  // New action invalidates redo history
    }

    /**
     * Undoes the last change, returning the previous state.
     *
     * @param currentState The current state (will be pushed to redo stack)
     * @return The previous state, or empty if nothing to undo
     */
    @Nonnull
    public Optional<EditorState> undo(@Nonnull EditorState currentState) {
        if (undoStack.isEmpty()) {
            return Objects.requireNonNull(Optional.empty());
        }

        // Push current to redo stack
        redoStack.push(currentState);

        // Pop and return previous state
        return Objects.requireNonNull(Optional.of(undoStack.pop()));
    }

    /**
     * Redoes a previously undone change.
     *
     * @param currentState The current state (will be pushed to undo stack)
     * @return The redone state, or empty if nothing to redo
     */
    @Nonnull
    public Optional<EditorState> redo(@Nonnull EditorState currentState) {
        if (redoStack.isEmpty()) {
            return Objects.requireNonNull(Optional.empty());
        }

        // Push current to undo stack
        undoStack.push(currentState);

        // Pop and return redo state
        return Objects.requireNonNull(Optional.of(redoStack.pop()));
    }

    /**
     * Checks if undo is available.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Checks if redo is available.
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Gets the number of states in the undo stack.
     */
    public int undoSize() {
        return undoStack.size();
    }

    /**
     * Gets the number of states in the redo stack.
     */
    public int redoSize() {
        return redoStack.size();
    }

    /**
     * Clears all history.
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * Checks if there have been any changes since the last save.
     * Compares with the initial state (bottom of undo stack).
     *
     * @param currentState The current state to compare
     * @param savedState   The last saved state
     * @return true if there are unsaved changes
     */
    public boolean isDirty(@Nonnull EditorState currentState, @Nonnull EditorState savedState) {
        return !currentState.equals(savedState);
    }

    /**
     * Snapshot of the editor state at a point in time.
     * Immutable record for safe storage in history.
     */
    public record EditorState(
        @Nonnull String dialogName,
        @Nonnull String entryNodeId,
        @Nonnull Map<String, DialogNode> nodes,
        @Nonnull String selectedNodeId,
        @Nonnull String selectedOptionId
    ) {
        /**
         * Creates an EditorState from editor fields.
         */
        @Nonnull
        public static EditorState of(
            @Nonnull String dialogName,
            @Nonnull String entryNodeId,
            @Nonnull Map<String, DialogNode> nodes,
            @Nonnull String selectedNodeId,
            @Nonnull String selectedOptionId
        ) {
            return new EditorState(
                dialogName,
                entryNodeId,
                Objects.requireNonNull(Map.copyOf(nodes)),  // Defensive copy
                selectedNodeId != null ? selectedNodeId : "",
                selectedOptionId != null ? selectedOptionId : ""
            );
        }

        /**
         * Creates a DialogSet from this state.
         */
        @Nonnull
        public DialogSet toDialogSet(@Nonnull String dialogSetId, int revision) {
            return new DialogSet(
                dialogSetId,
                dialogName,
                Objects.requireNonNull(Map.copyOf(nodes)),
                entryNodeId,
                System.currentTimeMillis(),
                revision,
                false,
                null,
                null  // schedule
            );
        }
    }
}
