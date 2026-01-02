package com.devmod.notification.network;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.notification.NotificationRouter;
/**
 * Client-to-server update payload for notification preferences.
 */
public record NotificationPreferencesUpdatePayload(
        boolean globalMute,
        int minOverlayPriorityOrdinal,
        boolean preferChatOverOverlay,
        float masterVolume,
        String categoryPrefsJson
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final Gson GSON = new Gson();
    private static final int MAX_JSON_LENGTH = 8192;

    public static final ResourceLocation ID = Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "notification_prefs_update"));
    public static final Type<NotificationPreferencesUpdatePayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, NotificationPreferencesUpdatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NotificationPreferencesUpdatePayload decode(@Nonnull FriendlyByteBuf buf) {
                    boolean globalMute = buf.readBoolean();
                    int minOverlayPriorityOrdinal = buf.readVarInt();
                    boolean preferChatOverOverlay = buf.readBoolean();
                    float masterVolume = buf.readFloat();
                    String categoryPrefsJson = buf.readUtf(MAX_JSON_LENGTH);
                    return new NotificationPreferencesUpdatePayload(
                            globalMute,
                            minOverlayPriorityOrdinal,
                            preferChatOverOverlay,
                            masterVolume,
                            categoryPrefsJson
                    );
                }

                @Override
                public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull NotificationPreferencesUpdatePayload payload) {
                    buf.writeBoolean(payload.globalMute());
                    buf.writeVarInt(payload.minOverlayPriorityOrdinal());
                    buf.writeBoolean(payload.preferChatOverOverlay());
                    buf.writeFloat(payload.masterVolume());
                    buf.writeUtf(Objects.requireNonNull(payload.categoryPrefsJson() != null ? payload.categoryPrefsJson() : ""), MAX_JSON_LENGTH);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public NotificationRouter.NotificationPreferences toPreferences(UUID playerUuid) {
        NotificationRouter.NotificationPreferences prefs = new NotificationRouter.NotificationPreferences(playerUuid);
        prefs.setGlobalMute(globalMute);
        prefs.setMinOverlayPriority(NotificationPriority.fromOrdinal(minOverlayPriorityOrdinal));
        prefs.setPreferChatOverOverlay(preferChatOverOverlay);
        prefs.setMasterVolume(masterVolume);

        Map<String, CategoryPrefDto> dtoMap = getCategoryPrefs();
        for (Map.Entry<String, CategoryPrefDto> entry : dtoMap.entrySet()) {
            NotificationCategory category = NotificationCategory.fromId(entry.getKey());
            CategoryPrefDto dto = entry.getValue();
            prefs.setCategoryOverlayEnabled(category, dto.overlayEnabled);
            prefs.setCategorySoundEnabled(category, dto.soundEnabled);
            prefs.setCategorySoundVolume(category, dto.soundVolume);
        }

        return prefs;
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

    public record CategoryPrefDto(boolean overlayEnabled, boolean soundEnabled, float soundVolume) {}

    @Override
    public int estimatedSize() {
        int size = 1; // globalMute boolean
        size += 1; // minOverlayPriorityOrdinal varint
        size += 1; // preferChatOverOverlay boolean
        size += 4; // masterVolume float
        size += varIntSize(categoryPrefsJson != null ? categoryPrefsJson.length() : 0);
        size += (categoryPrefsJson != null ? categoryPrefsJson.length() : 0);
        return size;
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }
}
