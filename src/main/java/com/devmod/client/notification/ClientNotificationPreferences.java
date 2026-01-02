package com.devmod.client.notification;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.notification.network.NotificationPreferencesSyncPayload;
import com.devmod.notification.network.NotificationPreferencesUpdatePayload;

/**
 * Client-side notification preferences.
 * Synced from server or loaded from local config.
 */
@OnlyIn(Dist.CLIENT)
public class ClientNotificationPreferences {

    private static final Gson GSON = new Gson();

    public static final ClientNotificationPreferences INSTANCE = new ClientNotificationPreferences();

    private boolean globalMute = false;
    private NotificationPriority minOverlayPriority = NotificationPriority.LOW;
    private float masterVolume = 1.0f;
    private boolean preferChatOverOverlay = false;

    private final Map<NotificationCategory, CategoryPreference> categoryPreferences;

    public ClientNotificationPreferences() {
        this.categoryPreferences = new EnumMap<>(NotificationCategory.class);

        // Initialize with defaults from each category
        for (NotificationCategory category : NotificationCategory.values()) {
            categoryPreferences.put(category, new CategoryPreference(
                    category.isDefaultOverlayEnabled(),
                    true, // sound enabled by default
                    1.0f  // full volume
            ));
        }
    }

    public boolean isGlobalMute() {
        return globalMute;
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    /**
     * Check if a category is muted for overlay display.
     */
    public boolean isCategoryMuted(NotificationCategory category) {
        if (globalMute) return true;

        CategoryPreference pref = categoryPreferences.get(category);
        return pref != null && !pref.overlayEnabled;
    }

    /**
     * Check if sound is enabled for a category.
     */
    public boolean isSoundEnabled(NotificationCategory category) {
        if (globalMute) return false;

        CategoryPreference pref = categoryPreferences.get(category);
        return pref == null || pref.soundEnabled;
    }

    /**
     * Get sound volume for a category.
     */
    public float getSoundVolume(NotificationCategory category) {
        if (globalMute) return 0f;

        CategoryPreference pref = categoryPreferences.get(category);
        float catVolume = pref != null ? pref.soundVolume : 1.0f;
        return masterVolume * catVolume;
    }

    /**
     * Get raw category volume (without master volume).
     */
    public float getCategoryVolume(NotificationCategory category) {
        CategoryPreference pref = categoryPreferences.get(category);
        return pref != null ? pref.soundVolume : 1.0f;
    }

    /**
     * Get the minimum priority level for overlay display.
     */
    public NotificationPriority getMinOverlayPriority() {
        return minOverlayPriority;
    }

    /**
     * Check if user prefers chat notifications over overlays.
     */
    public boolean prefersChatOverOverlay() {
        return preferChatOverOverlay;
    }

    /**
     * Get raw category preference data.
     */
    @Nullable
    public CategoryPreference getCategoryPreference(NotificationCategory category) {
        return categoryPreferences.get(category);
    }

    // ===== Setters =====

    public void setGlobalMute(boolean globalMute) {
        this.globalMute = globalMute;
    }

    public void setMinOverlayPriority(NotificationPriority priority) {
        this.minOverlayPriority = priority;
    }

    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0f, Math.min(1f, volume));
    }

    public void setPreferChatOverOverlay(boolean prefer) {
        this.preferChatOverOverlay = prefer;
    }

    public void setCategoryOverlayEnabled(NotificationCategory category, boolean enabled) {
        CategoryPreference pref = categoryPreferences.computeIfAbsent(category,
                c -> new CategoryPreference(true, true, 1.0f));
        categoryPreferences.put(category, new CategoryPreference(enabled, pref.soundEnabled, pref.soundVolume));
    }

    public void setCategorySoundEnabled(NotificationCategory category, boolean enabled) {
        CategoryPreference pref = categoryPreferences.computeIfAbsent(category,
                c -> new CategoryPreference(true, true, 1.0f));
        categoryPreferences.put(category, new CategoryPreference(pref.overlayEnabled, enabled, pref.soundVolume));
    }

    public void setCategorySoundVolume(NotificationCategory category, float volume) {
        CategoryPreference pref = categoryPreferences.computeIfAbsent(category,
                c -> new CategoryPreference(true, true, 1.0f));
        categoryPreferences.put(category, new CategoryPreference(
                pref.overlayEnabled, pref.soundEnabled, Math.max(0f, Math.min(1f, volume))));
    }

    /**
     * Apply preferences received from the server.
     */
    public void applySyncPayload(NotificationPreferencesSyncPayload payload) {
        if (payload == null) {
            return;
        }

        globalMute = payload.globalMute();
        minOverlayPriority = payload.getMinOverlayPriority();
        preferChatOverOverlay = payload.preferChatOverOverlay();
        masterVolume = Math.max(0f, Math.min(1f, payload.masterVolume()));

        categoryPreferences.clear();
        for (NotificationCategory category : NotificationCategory.values()) {
            categoryPreferences.put(category, new CategoryPreference(
                    category.isDefaultOverlayEnabled(),
                    true,
                    1.0f
            ));
        }

        Map<String, NotificationPreferencesSyncPayload.CategoryPrefDto> dtoMap = payload.getCategoryPrefs();
        for (Map.Entry<String, NotificationPreferencesSyncPayload.CategoryPrefDto> entry : dtoMap.entrySet()) {
            NotificationCategory category = NotificationCategory.fromId(entry.getKey());
            NotificationPreferencesSyncPayload.CategoryPrefDto dto = entry.getValue();
            categoryPreferences.put(category, new CategoryPreference(
                    dto.overlayEnabled(),
                    dto.soundEnabled(),
                    Math.max(0f, Math.min(1f, dto.soundVolume()))
            ));
        }
    }

    /**
     * Build a payload for sending updates to the server.
     */
    public NotificationPreferencesUpdatePayload toUpdatePayload() {
        Map<String, NotificationPreferencesUpdatePayload.CategoryPrefDto> dtoMap = new HashMap<>();
        for (NotificationCategory category : NotificationCategory.values()) {
            CategoryPreference pref = categoryPreferences.get(category);
            if (pref != null) {
                dtoMap.put(category.getId(), new NotificationPreferencesUpdatePayload.CategoryPrefDto(
                        pref.overlayEnabled(),
                        pref.soundEnabled(),
                        pref.soundVolume()
                ));
            }
        }

        String json = dtoMap.isEmpty() ? "" : GSON.toJson(dtoMap);
        return new NotificationPreferencesUpdatePayload(
                globalMute,
                minOverlayPriority.ordinal(),
                preferChatOverOverlay,
                masterVolume,
                json
        );
    }

    /**
     * Reset to defaults.
     */
    public void reset() {
        globalMute = false;
        minOverlayPriority = NotificationPriority.LOW;
        masterVolume = 1.0f;
        preferChatOverOverlay = false;

        categoryPreferences.clear();
        for (NotificationCategory category : NotificationCategory.values()) {
            categoryPreferences.put(category, new CategoryPreference(
                    category.isDefaultOverlayEnabled(),
                    true,
                    1.0f
            ));
        }
    }

    /**
     * Preference settings for a single category.
     */
    public record CategoryPreference(
            boolean overlayEnabled,
            boolean soundEnabled,
            float soundVolume
    ) {}
}
