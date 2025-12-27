package com.devmod.notification.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.notification.NotificationRouter;
/**
 * Server-to-client sync payload for notification preferences.
 */
public record NotificationPreferencesSyncPayload(
        boolean globalMute,
        int minOverlayPriorityOrdinal,
        boolean preferChatOverOverlay,
        float masterVolume,
        String categoryPrefsJson
) implements CustomPacketPayload {

    private static final Gson GSON = new Gson();
    private static final int MAX_JSON_LENGTH = 8192;

    public static final ResourceLocation ID = Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "notification_prefs_sync"));
    public static final Type<NotificationPreferencesSyncPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, NotificationPreferencesSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NotificationPreferencesSyncPayload decode(FriendlyByteBuf buf) {
                    boolean globalMute = buf.readBoolean();
                    int minOverlayPriorityOrdinal = buf.readVarInt();
                    boolean preferChatOverOverlay = buf.readBoolean();
                    float masterVolume = buf.readFloat();
                    String categoryPrefsJson = buf.readUtf(MAX_JSON_LENGTH);
                    return new NotificationPreferencesSyncPayload(
                            globalMute,
                            minOverlayPriorityOrdinal,
                            preferChatOverOverlay,
                            masterVolume,
                            categoryPrefsJson
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, NotificationPreferencesSyncPayload payload) {
                    buf.writeBoolean(payload.globalMute());
                    buf.writeVarInt(payload.minOverlayPriorityOrdinal());
                    buf.writeBoolean(payload.preferChatOverOverlay());
                    buf.writeFloat(payload.masterVolume());
                    buf.writeUtf(payload.categoryPrefsJson() != null ? payload.categoryPrefsJson() : "", MAX_JSON_LENGTH);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static NotificationPreferencesSyncPayload from(NotificationRouter.NotificationPreferences prefs) {
        String prefsJson = serializeCategoryPrefs(prefs);
        return new NotificationPreferencesSyncPayload(
                prefs.isGlobalMute(),
                prefs.getMinOverlayPriority().ordinal(),
                prefs.prefersChatOverOverlay(),
                prefs.getMasterVolume(),
                prefsJson
        );
    }

    public NotificationPriority getMinOverlayPriority() {
        return NotificationPriority.fromOrdinal(minOverlayPriorityOrdinal);
    }

    public Map<String, CategoryPrefDto> getCategoryPrefs() {
        if (categoryPrefsJson == null || categoryPrefsJson.isBlank()) {
            return Map.of();
        }
        try {
            return GSON.fromJson(categoryPrefsJson,
                    new TypeToken<Map<String, CategoryPrefDto>>() {}.getType());
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String serializeCategoryPrefs(NotificationRouter.NotificationPreferences prefs) {
        Map<String, CategoryPrefDto> dtoMap = new HashMap<>();
        for (NotificationCategory category : NotificationCategory.values()) {
            NotificationRouter.NotificationPreferences.CategoryPreference pref = prefs.getCategoryPreference(category);
            if (pref != null) {
                dtoMap.put(category.getId(), new CategoryPrefDto(
                        pref.overlayEnabled(),
                        pref.soundEnabled(),
                        pref.soundVolume()
                ));
            }
        }
        return GSON.toJson(dtoMap);
    }

    public record CategoryPrefDto(boolean overlayEnabled, boolean soundEnabled, float soundVolume) {}
}
