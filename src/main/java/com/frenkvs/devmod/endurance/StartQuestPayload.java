package com.frenkvs.devmod.endurance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Payload sent from client to server to start an Endurance Quest.
 * Supports both solo play and multiplayer party quests.
 */
public record StartQuestPayload(
    String mobId,
    int totalWaves,
    boolean endlessMode,
    int arenaSize,
    QuestType questType,
    @Nullable UUID partyId,
    List<UUID> partyMemberIds,
    String kitId
) implements CustomPacketPayload {

    public static final Type<StartQuestPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "start_endurance_quest"))
    );

    // Security limits to prevent DoS attacks
    private static final int MAX_STRING_LENGTH = 256;
    private static final int MAX_PARTY_SIZE = 20; // EVENT max players

    public static final StreamCodec<RegistryFriendlyByteBuf, StartQuestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public StartQuestPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            // SECURITY FIX: Limit string length
            String mobId = Objects.requireNonNull(buf.readUtf(MAX_STRING_LENGTH));
            int totalWaves = buf.readVarInt();
            boolean endlessMode = buf.readBoolean();
            int arenaSize = buf.readVarInt();
            // SECURITY FIX: Clamp arena size to reasonable bounds
            arenaSize = Math.max(16, Math.min(arenaSize, 256));

            // Read quest type (ordinal)
            int questTypeOrdinal = buf.readVarInt();
            QuestType questType = QuestType.values()[Math.min(questTypeOrdinal, QuestType.values().length - 1)];

            // Read party ID (nullable)
            UUID partyId = null;
            if (buf.readBoolean()) {
                partyId = buf.readUUID();
            }

            // Read party member IDs
            int memberCount = buf.readVarInt();
            memberCount = Math.min(memberCount, MAX_PARTY_SIZE); // Security limit
            List<UUID> partyMemberIds = new ArrayList<>(memberCount);
            for (int i = 0; i < memberCount; i++) {
                partyMemberIds.add(Objects.requireNonNull(buf.readUUID()));
            }

            // Read kit ID
            String kitId = buf.readUtf(MAX_STRING_LENGTH);

            return new StartQuestPayload(mobId, totalWaves, endlessMode, arenaSize, questType, partyId, partyMemberIds, kitId);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull StartQuestPayload payload) {
            buf.writeUtf(Objects.requireNonNull(payload.mobId));
            buf.writeVarInt(payload.totalWaves);
            buf.writeBoolean(payload.endlessMode);
            buf.writeVarInt(payload.arenaSize);

            // Write quest type
            buf.writeVarInt(payload.questType.ordinal());

            // Write party ID (nullable)
            buf.writeBoolean(payload.partyId != null);
            if (payload.partyId != null) {
                buf.writeUUID(payload.partyId);
            }

            // Write party member IDs
            buf.writeVarInt(payload.partyMemberIds.size());
            for (UUID memberId : payload.partyMemberIds) {
                buf.writeUUID(Objects.requireNonNull(memberId));
            }

            // Write kit ID
            buf.writeUtf(payload.kitId != null ? payload.kitId : "STARTER");
        }
    };

    /**
     * Create for solo play with default settings.
     */
    public StartQuestPayload(String mobId, int totalWaves, boolean endlessMode) {
        this(mobId, totalWaves, endlessMode, 64, QuestType.PVE_COOP, null, List.of(), "STARTER");
    }

    /**
     * Create for solo play with custom arena size.
     */
    public StartQuestPayload(String mobId, int totalWaves, boolean endlessMode, int arenaSize) {
        this(mobId, totalWaves, endlessMode, arenaSize, QuestType.PVE_COOP, null, List.of(), "STARTER");
    }

    /**
     * Create for solo play with kit selection.
     */
    public StartQuestPayload(String mobId, int totalWaves, boolean endlessMode, String kitId) {
        this(mobId, totalWaves, endlessMode, 64, QuestType.PVE_COOP, null, List.of(), kitId);
    }

    /**
     * Check if this is a multiplayer party quest.
     */
    public boolean isMultiplayer() {
        return partyId != null && !partyMemberIds.isEmpty();
    }

    /**
     * Get player count for scaling calculations.
     */
    public int getPlayerCount() {
        return Math.max(1, partyMemberIds.size());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get mob ID as ResourceLocation.
     */
    public ResourceLocation getMobResourceLocation() {
        return Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(mobId)));
    }
}
