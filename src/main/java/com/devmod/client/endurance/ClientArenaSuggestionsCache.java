package com.devmod.client.endurance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.devmod.arena.policy.TemplateSuggestion;
import com.devmod.endurance.ArenaSuggestionsPayload;

/**
 * Client-side cache for arena template suggestions.
 * Stores the latest suggestions from the server for UI display.
 */
public class ClientArenaSuggestionsCache {

    public static final ClientArenaSuggestionsCache INSTANCE = new ClientArenaSuggestionsCache();

    private volatile @Nullable String currentMobId;
    private volatile @Nullable List<TemplateSuggestion> suggestions;
    private volatile @Nullable String selectedTemplateId;

    // Listeners for UI updates
    private final CopyOnWriteArrayList<Consumer<ArenaSuggestionsPayload>> listeners = new CopyOnWriteArrayList<>();

    private ClientArenaSuggestionsCache() {}

    /**
     * Called when receiving suggestions from the server.
     */
    public void receiveSuggestions(ArenaSuggestionsPayload payload) {
        this.currentMobId = payload.mobId();
        this.suggestions = payload.suggestions();
        this.selectedTemplateId = payload.selectedTemplateId();

        // Notify listeners
        for (Consumer<ArenaSuggestionsPayload> listener : listeners) {
            try {
                listener.accept(payload);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    /**
     * Gets the current suggestions for a mob.
     */
    @Nullable
    public List<TemplateSuggestion> getSuggestions(String mobId) {
        if (mobId.equals(currentMobId)) {
            return suggestions;
        }
        return null;
    }

    /**
     * Gets the selected/recommended template ID.
     */
    @Nullable
    public String getSelectedTemplateId() {
        return selectedTemplateId;
    }

    /**
     * Checks if we have cached suggestions for a mob.
     */
    public boolean hasSuggestions(String mobId) {
        return mobId.equals(currentMobId) && suggestions != null && !suggestions.isEmpty();
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        this.currentMobId = null;
        this.suggestions = null;
        this.selectedTemplateId = null;
    }

    /**
     * Adds a listener for suggestion updates.
     */
    public void addListener(Consumer<ArenaSuggestionsPayload> listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     */
    public void removeListener(Consumer<ArenaSuggestionsPayload> listener) {
        listeners.remove(listener);
    }
}
