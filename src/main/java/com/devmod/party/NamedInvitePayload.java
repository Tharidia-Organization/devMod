package com.devmod.party;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.QuestType;
import com.devmod.network.PayloadValidation;

public record NamedInvitePayload(
    String targetPlayerName,
    int questTypeOrdinal
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<NamedInvitePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "named_invite"))
    );

    // Security: limit player name to 16 characters (Minecraft max)
    private static final int MAX_NAME_LENGTH = 16;

    public static final StreamCodec<RegistryFriendlyByteBuf, NamedInvitePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        @Nonnull
        public NamedInvitePayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            String name = Objects.requireNonNull(buf.readUtf(MAX_NAME_LENGTH));
            int questType = buf.readVarInt();
            return new NamedInvitePayload(name, questType);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull NamedInvitePayload payload) {
            buf.writeUtf(Objects.requireNonNull(payload.targetPlayerName), MAX_NAME_LENGTH);
            buf.writeVarInt(payload.questTypeOrdinal);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get the quest type from the ordinal.
     */
    public QuestType getQuestType() {
        QuestType[] values = QuestType.values();
        if (questTypeOrdinal >= 0 && questTypeOrdinal < values.length) {
            return values[questTypeOrdinal];
        }
        return QuestType.PVE_COOP;
    }

    /**
     * Create a payload with the quest type enum.
     */
    public static NamedInvitePayload create(String targetPlayerName, QuestType questType) {
        return new NamedInvitePayload(targetPlayerName, questType.ordinal());
    }

    @Override
    public int estimatedSize() {
        // VarInt length prefix + UTF-8 bytes for name, plus VarInt for quest type
        int nameBytes = targetPlayerName != null ? targetPlayerName.length() : 0;
        return 1 + nameBytes + 1; // varint(len) + name bytes + varint(questType)
    }
}
