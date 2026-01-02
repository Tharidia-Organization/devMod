package com.devmod.notification.network;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationParamsCodec;
import com.devmod.notification.NotificationPriority;

/**
 * Unified network payload for all notification types.
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
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    // Security limits
    private static final int MAX_STRING_LENGTH = 512;
    private static final int MAX_PARAMS_LENGTH = 2048;

    public static final ResourceLocation ID = Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "unified_notification"));
    public static final Type<UnifiedNotificationPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, UnifiedNotificationPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public UnifiedNotificationPayload decode(@Nonnull FriendlyByteBuf buf) {
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
                public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull UnifiedNotificationPayload payload) {
                    final FriendlyByteBuf safeBuf = Objects.requireNonNull(buf);
                    final UnifiedNotificationPayload safePayload = Objects.requireNonNull(payload);
                    safeBuf.writeLong(safePayload.notificationId().getMostSignificantBits());
                    safeBuf.writeLong(safePayload.notificationId().getLeastSignificantBits());
                    safeBuf.writeVarInt(safePayload.categoryOrdinal());
                    safeBuf.writeVarInt(safePayload.priorityOrdinal());
                    safeBuf.writeUtf(Objects.requireNonNull(truncate(safePayload.titleKey(), MAX_STRING_LENGTH)));
                    safeBuf.writeUtf(Objects.requireNonNull(truncate(safePayload.messageKey(), MAX_STRING_LENGTH)));
                    safeBuf.writeUtf(Objects.requireNonNull(truncate(safePayload.paramsJson(), MAX_PARAMS_LENGTH)));
                    safeBuf.writeUtf(Objects.requireNonNull(truncate(safePayload.iconId(), MAX_STRING_LENGTH)));
                    safeBuf.writeUtf(Objects.requireNonNull(truncate(safePayload.soundId(), MAX_STRING_LENGTH)));
                    safeBuf.writeUtf(Objects.requireNonNull(truncate(safePayload.actionId(), MAX_STRING_LENGTH)));
                    safeBuf.writeUtf(Objects.requireNonNull(truncate(safePayload.actionDataJson(), MAX_PARAMS_LENGTH)));
                    safeBuf.writeVarInt(safePayload.displayDurationMs());
                    safeBuf.writeLong(safePayload.createdAtEpochMs());
                }

                @Nonnull
                private String truncate(String s, int maxLen) {
                    if (s == null) return "";
                    String result = s.length() > maxLen ? s.substring(0, maxLen) : s;
                    return Objects.requireNonNull(result);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += 16; // UUID
        size += varIntSize(categoryOrdinal);
        size += varIntSize(priorityOrdinal);
        size += estimatedUtfSize(titleKey);
        size += estimatedUtfSize(messageKey);
        size += estimatedUtfSize(paramsJson);
        size += estimatedUtfSize(iconId);
        size += estimatedUtfSize(soundId);
        size += estimatedUtfSize(actionId);
        size += estimatedUtfSize(actionDataJson);
        size += varIntSize(displayDurationMs);
        size += 8; // createdAtEpochMs
        return size;
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

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
