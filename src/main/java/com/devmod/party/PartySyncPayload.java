package com.devmod.party;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.QuestType;
import com.devmod.network.PayloadValidation;

public record PartySyncPayload(
    boolean hasParty,
    UUID partyId,
    UUID leaderId,
    List<PartyMemberInfo> members,
    int questTypeOrdinal,
    int stateOrdinal,
    @Nullable UUID instanceId,         @Nullable // null (zero UUID) if not in quest
    String selectedMobId     // ResourceLocation as string (e.g., "minecraft:zombie"), empty if not set
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    // Security limits
    private static final int MAX_MEMBERS = 20;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_KIT_ID_LENGTH = 32;
    private static final int MAX_KIT_LABEL_LENGTH = 64;

    public static final Type<PartySyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "party_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PartySyncPayload> STREAM_CODEC = StreamCodec.of(
        PartySyncPayload::encode,
        PartySyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, PartySyncPayload payload) {
        buf.writeBoolean(payload.hasParty);
        buf.writeUUID(Objects.requireNonNull(payload.partyId));
        buf.writeUUID(Objects.requireNonNull(payload.leaderId));

        // Write members list
        buf.writeVarInt(payload.members.size());
        for (PartyMemberInfo member : payload.members) {
            buf.writeUUID(Objects.requireNonNull(member.playerId));
            buf.writeUtf(Objects.requireNonNull(member.playerName));
            buf.writeBoolean(member.isReady);
            buf.writeBoolean(member.isLeader);
            buf.writeBoolean(member.isOnline);
            buf.writeBoolean(member.isSpectator);
            buf.writeUtf(Objects.requireNonNull(Objects.requireNonNullElse(member.kitId(), "")));
            buf.writeUtf(Objects.requireNonNull(Objects.requireNonNullElse(member.kitLabel(), "")));
        }

        buf.writeVarInt(payload.questTypeOrdinal);
        buf.writeVarInt(payload.stateOrdinal);
        buf.writeUUID(payload.instanceId != null ? payload.instanceId : new UUID(0, 0));
        buf.writeUtf(payload.selectedMobId != null ? payload.selectedMobId : "");
    }

    private static PartySyncPayload decode(RegistryFriendlyByteBuf buf) {
        boolean hasParty = buf.readBoolean();
        UUID partyId = buf.readUUID();
        UUID leaderId = buf.readUUID();

        // Read members list with security bounds
        int memberCount = buf.readVarInt();
        if (memberCount < 0 || memberCount > MAX_MEMBERS) {
            memberCount = 0;
        }
        List<PartyMemberInfo> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            UUID playerId = Objects.requireNonNull(buf.readUUID());
            String playerName = Objects.requireNonNull(buf.readUtf(MAX_NAME_LENGTH));
            boolean isReady = buf.readBoolean();
            boolean isLeader = buf.readBoolean();
            boolean isOnline = buf.readBoolean();
            boolean isSpectator = buf.readBoolean();
            String kitId = buf.readUtf(MAX_KIT_ID_LENGTH);
            String kitLabel = buf.readUtf(MAX_KIT_LABEL_LENGTH);
            members.add(new PartyMemberInfo(playerId, playerName, isReady, isLeader, isOnline, isSpectator,
                kitId.isBlank() ? null : kitId, kitLabel.isBlank() ? null : kitLabel));
        }

        int questTypeOrdinal = buf.readVarInt();
        int stateOrdinal = buf.readVarInt();
        UUID instanceId = buf.readUUID();
        String selectedMobId = buf.readUtf(MAX_NAME_LENGTH);

        // Convert zero UUID back to null
        if (instanceId.getMostSignificantBits() == 0 && instanceId.getLeastSignificantBits() == 0) {
            instanceId = null;
        }

        // Convert empty string to null
        if (selectedMobId.isEmpty()) {
            selectedMobId = null;
        }

        return new PartySyncPayload(hasParty, partyId, leaderId, members, questTypeOrdinal, stateOrdinal, instanceId, selectedMobId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 1; // hasParty
        size += 16; // partyId
        size += 16; // leaderId
        size += varIntSize(members.size());
        for (PartyMemberInfo member : members) {
            size += estimateMemberSize(member);
        }
        size += varIntSize(questTypeOrdinal);
        size += varIntSize(stateOrdinal);
        size += 16; // instanceId
        size += estimatedUtfSize(selectedMobId);
        return size;
    }

    private static int estimateMemberSize(PartyMemberInfo member) {
        int size = 16; // UUID
        size += estimatedUtfSize(member.playerName);
        size += 4; // isReady, isLeader, isOnline, isSpectator
        size += estimatedUtfSize(member.kitId());
        size += estimatedUtfSize(member.kitLabel());
        return size;
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

    /**
     * Get the quest type from ordinal.
     */
    public QuestType getQuestType() {
        QuestType[] values = QuestType.values();
        if (questTypeOrdinal >= 0 && questTypeOrdinal < values.length) {
            return values[questTypeOrdinal];
        }
        return QuestType.PVE_COOP;
    }

    /**
     * Get the party state from ordinal.
     */
    public PartyData.PartyState getState() {
        PartyData.PartyState[] values = PartyData.PartyState.values();
        if (stateOrdinal >= 0 && stateOrdinal < values.length) {
            return values[stateOrdinal];
        }
        return PartyData.PartyState.FORMING;
    }

    /**
     * Get the selected mob ID as ResourceLocation.
     * Returns null if no mob is selected.
     */
    @Nullable
    public ResourceLocation getSelectedMobResourceLocation() {
        String localSelectedMobId = selectedMobId;
        if (localSelectedMobId == null || localSelectedMobId.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(localSelectedMobId);
    }

    /**
     * Get the display name of the selected mob.
     * Returns "Zombie" (default) if none selected.
     */
    public String getSelectedMobDisplayName() {
        String localSelectedMobId = selectedMobId;
        if (localSelectedMobId == null || localSelectedMobId.isEmpty()) {
            return "Zombie";
        }
        // Extract the path part and capitalize
        ResourceLocation loc = ResourceLocation.tryParse(localSelectedMobId);
        if (loc == null) return "Zombie";
        String path = loc.getPath();
        StringBuilder sb = new StringBuilder();
        int start = 0;
        for (int i = 0; i <= path.length(); i++) {
            if (i == path.length() || path.charAt(i) == '_') {
                if (i > start) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Character.toUpperCase(path.charAt(start)));
                    if (i - start > 1) {
                        sb.append(path, start + 1, i);
                    }
                }
                start = i + 1;
            }
        }
        return sb.toString();
    }

    /**
     * Check if player is the leader.
     */
    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    /**
     * Get member info by player ID.
     * Returns null if the member is not found.
     */
    @Nullable
    public PartyMemberInfo getMember(UUID playerId) {
        for (PartyMemberInfo member : members) {
            if (member.playerId.equals(playerId)) {
                return member;
            }
        }
        return null;
    }

    /**
     * Create an empty payload (no party).
     */
    public static PartySyncPayload empty() {
        return new PartySyncPayload(
            false,
            new UUID(0, 0),
            new UUID(0, 0),
            List.of(),
            0,
            0,
            null,
            null
        );
    }

    /**
     * Create a sync payload from a PartyData object.
     *
     * @param party The party data to sync
     * @param onlineChecker Function to check if a player is online
     * @return The sync payload
     */
    public static PartySyncPayload fromParty(PartyData party, java.util.function.Predicate<UUID> onlineChecker,
                                             @Nullable java.util.function.Predicate<UUID> spectatorChecker,
                                             @Nullable java.util.function.Predicate<UUID> readyChecker) {
        List<PartyMemberInfo> memberInfos = new ArrayList<>();

        for (UUID memberId : party.getMembers()) {
            String name = party.getMemberName(memberId);
            boolean isReady = readyChecker != null ? readyChecker.test(memberId) : party.isReady(memberId);
            boolean isLeader = party.isLeader(memberId);
            boolean isOnline = onlineChecker != null ? onlineChecker.test(memberId) : true;
            boolean isSpectator = spectatorChecker != null && spectatorChecker.test(memberId);
            String kitId = party.getMemberKitId(memberId);
            String kitLabel = resolveKitLabel(memberId, kitId);

            memberInfos.add(new PartyMemberInfo(memberId, name, isReady, isLeader, isOnline, isSpectator, kitId, kitLabel));
        }

        // Get selected mob ID as string (or null)
        ResourceLocation mobId = party.getSelectedMobId();
        String mobIdString = mobId != null ? mobId.toString() : null;

        return new PartySyncPayload(
            true,
            party.getPartyId(),
            party.getLeaderId(),
            memberInfos,
            party.getQuestType().ordinal(),
            party.getState().ordinal(),
            party.getInstanceId(),
            mobIdString
        );
    }

    public static PartySyncPayload fromParty(PartyData party, java.util.function.Predicate<UUID> onlineChecker) {
        return fromParty(party, onlineChecker, null, null);
    }

    public static PartySyncPayload fromParty(PartyData party, java.util.function.Predicate<UUID> onlineChecker,
                                             java.util.function.Predicate<UUID> spectatorChecker) {
        return fromParty(party, onlineChecker, spectatorChecker, null);
    }

    /**
     * Information about a single party member.
     */
    public record PartyMemberInfo(
        UUID playerId,
        String playerName,
        boolean isReady,
        boolean isLeader,
        boolean isOnline,
        boolean isSpectator,
        @javax.annotation.Nullable String kitId,
        @javax.annotation.Nullable String kitLabel
    ) {
        /**
         * Get display status string.
         */
        public String getStatusDisplay() {
            if (!isOnline) return "\u00A77[OFFLINE]";
            if (isSpectator) return "\u00A77[SPECTATOR]";
            if (isLeader) return "\u00A76[LEADER]";
            if (isReady) return "\u00A7a[READY]";
            return "\u00A7c[NOT READY]";
        }
    }

    private static String resolveKitLabel(UUID memberId, String kitId) {
        if (kitId == null || kitId.isBlank()) {
            return null;
        }
        if ("TEMPORARY".equals(kitId)) {
            String name = com.devmod.endurance.KitManager.INSTANCE.getTemporaryKitName(memberId);
            return name != null && !name.isBlank() ? name : "Temporary Kit";
        }
        if (kitId.length() == 8) {
            var synced = com.devmod.endurance.KitManager.INSTANCE.getSyncedCustomKit(memberId, kitId);
            if (synced.isPresent()) {
                return synced.get().getName();
            }
            var saved = com.devmod.endurance.KitManager.INSTANCE.getCustomKit(kitId);
            if (saved.isPresent()) {
                return saved.get().getName();
            }
            return "Custom Kit";
        }
        var preset = com.devmod.endurance.KitManager.INSTANCE.getKitById(kitId);
        if (preset != null) {
            return preset.getDisplayName();
        }
        return kitId;
    }
}
