package com.devmod.notification.network;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationParamsCodec;
import com.devmod.notification.NotificationPriority;

/**
 * Unified network payload for all notification types.
 * Replaces BadgeUnlockPayload, TokenGainPayload, RecordBannerPayload, etc.
 */
public record UnifiedNotificationPayload(
        UUID notificationId,
        int categoryOrdinal,
        int priorityOrdinal,
        String titleKey,
        String messageKey,
        String paramsJson,
        String iconId,
        String soundId,
        String actionId,
        String actionDataJson,
        int displayDurationMs,
        long createdAtEpochMs
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_STRING_LENGTH = 512;
    private static final int MAX_PARAMS_LENGTH = 2048;

    public static final ResourceLocation ID = Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "unified_notification"));
    public static final Type<UnifiedNotificationPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, UnifiedNotificationPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public UnifiedNotificationPayload decode(FriendlyByteBuf buf) {
                    UUID id = new UUID(buf.readLong(), buf.readLong());
                    int categoryOrdinal = buf.readVarInt();
                    int priorityOrdinal = buf.readVarInt();
                    String titleKey = buf.readUtf(MAX_STRING_LENGTH);
                    String messageKey = buf.readUtf(MAX_STRING_LENGTH);
                    String paramsJson = buf.readUtf(MAX_PARAMS_LENGTH);
                    String iconId = buf.readUtf(MAX_STRING_LENGTH);
                    String soundId = buf.readUtf(MAX_STRING_LENGTH);
                    String actionId = buf.readUtf(MAX_STRING_LENGTH);
                    String actionDataJson = buf.readUtf(MAX_PARAMS_LENGTH);
                    int displayDurationMs = buf.readVarInt();
                    long createdAtEpochMs = buf.readLong();

                    return new UnifiedNotificationPayload(
                            id, categoryOrdinal, priorityOrdinal, titleKey, messageKey,
                            paramsJson, iconId, soundId, actionId, actionDataJson,
                            displayDurationMs, createdAtEpochMs
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, UnifiedNotificationPayload payload) {
                    buf.writeLong(payload.notificationId().getMostSignificantBits());
                    buf.writeLong(payload.notificationId().getLeastSignificantBits());
                    buf.writeVarInt(payload.categoryOrdinal());
                    buf.writeVarInt(payload.priorityOrdinal());
                    buf.writeUtf(truncate(payload.titleKey(), MAX_STRING_LENGTH));
                    buf.writeUtf(truncate(payload.messageKey(), MAX_STRING_LENGTH));
                    buf.writeUtf(truncate(payload.paramsJson(), MAX_PARAMS_LENGTH));
                    buf.writeUtf(truncate(payload.iconId(), MAX_STRING_LENGTH));
                    buf.writeUtf(truncate(payload.soundId(), MAX_STRING_LENGTH));
                    buf.writeUtf(truncate(payload.actionId(), MAX_STRING_LENGTH));
                    buf.writeUtf(truncate(payload.actionDataJson(), MAX_PARAMS_LENGTH));
                    buf.writeVarInt(payload.displayDurationMs());
                    buf.writeLong(payload.createdAtEpochMs());
                }

                private String truncate(String s, int maxLen) {
                    if (s == null) return "";
                    return s.length() > maxLen ? s.substring(0, maxLen) : s;
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create payload from a Notification.
     */
    public static UnifiedNotificationPayload from(Notification notification) {
        String paramsJson = NotificationParamsCodec.toJson(notification.params());

        return new UnifiedNotificationPayload(
                notification.id(),
                notification.category().ordinal(),
                notification.priority().ordinal(),
                notification.titleKey(),
                notification.messageKey() != null ? notification.messageKey() : "",
                paramsJson,
                notification.iconId() != null ? notification.iconId() : "",
                notification.soundId() != null ? notification.soundId() : "",
                notification.actionId() != null ? notification.actionId() : "",
                notification.actionDataJson() != null ? notification.actionDataJson() : "",
                notification.displayDurationMs(),
                notification.createdAt().toEpochMilli()
        );
    }

    /**
     * Convert payload back to Notification for client processing.
     */
    public Notification toNotification() {
        Map<String, String> params = NotificationParamsCodec.fromJson(paramsJson);

        return Notification.builder(NotificationCategory.fromOrdinal(categoryOrdinal))
                .id(notificationId)
                .priority(NotificationPriority.fromOrdinal(priorityOrdinal))
                .titleKey(titleKey)
                .messageKey(messageKey.isEmpty() ? null : messageKey)
                .params(params)
                .iconId(iconId.isEmpty() ? null : iconId)
                .soundId(soundId.isEmpty() ? null : soundId)
                .actionId(actionId.isEmpty() ? null : actionId)
                .actionDataJson(actionDataJson.isEmpty() ? null : actionDataJson)
                .createdAt(Instant.ofEpochMilli(createdAtEpochMs))
                .displayDurationMs(displayDurationMs)
                .build();
    }

    /**
     * Get the category enum value.
     */
    public NotificationCategory getCategory() {
        return NotificationCategory.fromOrdinal(categoryOrdinal);
    }

    /**
     * Get the priority enum value.
     */
    public NotificationPriority getPriority() {
        return NotificationPriority.fromOrdinal(priorityOrdinal);
    }

    /**
     * Get params as Map.
     */
    public Map<String, String> getParams() {
        return NotificationParamsCodec.fromJson(paramsJson);
    }
}
