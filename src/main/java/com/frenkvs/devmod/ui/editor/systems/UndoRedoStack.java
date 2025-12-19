package com.frenkvs.devmod.ui.editor.systems;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Undo/Redo stack for editor actions.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#36-undoredo-stack
 */
public final class UndoRedoStack<T> {

    private final Deque<UndoState<T>> undoStack = new ArrayDeque<>();
    private final Deque<UndoState<T>> redoStack = new ArrayDeque<>();
    private final int maxSize;

    public UndoRedoStack(int maxSize) {
        this.maxSize = maxSize;
    }

    public UndoRedoStack() {
        this(50);  // Default max
    }

    /**
     * Push a new state onto the undo stack.
     * Clears the redo stack.
     */
    public void push(T state, String description) {
        undoStack.push(new UndoState<>(state, description, System.currentTimeMillis()));
        redoStack.clear();  // New action invalidates redo history

        // Trim to max size
        while (undoStack.size() > maxSize) {
            undoStack.removeLast();
        }
    }

    /**
     * Undo last action.
     * @param currentState Current state to push to redo stack
     * @return Previous state to restore, or null if nothing to undo
     */
    @Nullable
    public UndoState<T> undo(T currentState, String currentDescription) {
        if (undoStack.isEmpty()) {
            return null;
        }

        // Push current to redo
        redoStack.push(new UndoState<>(currentState, currentDescription, System.currentTimeMillis()));

        // Pop from undo
        return undoStack.pop();
    }

    /**
     * Redo last undone action.
     * @param currentState Current state to push to undo stack
     * @return Next state to restore, or null if nothing to redo
     */
    @Nullable
    public UndoState<T> redo(T currentState, String currentDescription) {
        if (redoStack.isEmpty()) {
            return null;
        }

        // Push current to undo
        undoStack.push(new UndoState<>(currentState, currentDescription, System.currentTimeMillis()));

        // Pop from redo
        return redoStack.pop();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public int getUndoCount() {
        return undoStack.size();
    }

    public int getRedoCount() {
        return redoStack.size();
    }

    /**
     * Get undo history for display.
     */
    public List<String> getUndoHistory(int maxItems) {
        return undoStack.stream()
            .limit(maxItems)
            .map(UndoState::description)
            .toList();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public record UndoState<T>(T state, String description, long timestamp) {}
}
