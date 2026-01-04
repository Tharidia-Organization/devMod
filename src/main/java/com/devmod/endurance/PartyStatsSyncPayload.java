package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Network payload for syncing party wave statistics from server to all party members.
 * Sent after each wave completes in a party run.
 *
 * <p>Contains aggregated stats for all party members, enabling the client
 * to display a "Party Stats" tab in the debrief screen.
 */
public record PartyStatsSyncPayload(
    int waveNumber,
    int totalWaves,
    long waveDurationMs,
    List<PlayerEntry> players,
    int partyTotalKills,
    int partyEliteKills,
    int partyTotalDamageDealt,
    int partyTotalDamageTaken,
    int partyDeaths,
    int partyMaxCombo,
    float partyDPS,
    boolean partyNoDamageWave,
    String mvpPlayerId,
    String mvpReason
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<PartyStatsSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "party_stats_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyStatsSyncPayload> STREAM_CODEC =
        StreamCodec.of(PartyStatsSyncPayload::encode, PartyStatsSyncPayload::decode);

    // Security limits
    private static final int MAX_PLAYERS = 8;
    private static final int MAX_STRING_LENGTH = 64;

    /**
     * Per-player stats entry.
     */
    public record PlayerEntry(
        String playerId,
        String playerName,
        int kills,
        int eliteKills,
        int damageDealt,
        int damageTaken,
        int deaths,
        int maxCombo,
        float dps,
        float killPercent,
        boolean wasSpectator
    ) {}

    private static void encode(RegistryFriendlyByteBuf buf, PartyStatsSyncPayload payload) {
        buf.writeVarInt(payload.waveNumber);
        buf.writeVarInt(payload.totalWaves);
        buf.writeLong(payload.waveDurationMs);

        // Players list
        buf.writeVarInt(payload.players.size());
        for (PlayerEntry player : payload.players) {
            buf.writeUtf(Objects.requireNonNull(player.playerId));
            buf.writeUtf(Objects.requireNonNull(player.playerName));
            buf.writeVarInt(player.kills);
            buf.writeVarInt(player.eliteKills);
            buf.writeVarInt(player.damageDealt);
            buf.writeVarInt(player.damageTaken);
            buf.writeVarInt(player.deaths);
            buf.writeVarInt(player.maxCombo);
            buf.writeFloat(player.dps);
            buf.writeFloat(player.killPercent);
            buf.writeBoolean(player.wasSpectator);
        }

        // Party totals
        buf.writeVarInt(payload.partyTotalKills);
        buf.writeVarInt(payload.partyEliteKills);
        buf.writeVarInt(payload.partyTotalDamageDealt);
        buf.writeVarInt(payload.partyTotalDamageTaken);
        buf.writeVarInt(payload.partyDeaths);
        buf.writeVarInt(payload.partyMaxCombo);
        buf.writeFloat(payload.partyDPS);
        buf.writeBoolean(payload.partyNoDamageWave);
        buf.writeUtf(Objects.requireNonNull(payload.mvpPlayerId));
        buf.writeUtf(Objects.requireNonNull(payload.mvpReason));
    }

    private static PartyStatsSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int waveNumber = buf.readVarInt();
        int totalWaves = buf.readVarInt();
        long waveDurationMs = buf.readLong();

        // Players list with security bounds
        int playerCount = buf.readVarInt();
        if (playerCount < 0 || playerCount > MAX_PLAYERS) {
            playerCount = 0;
        }

        List<PlayerEntry> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            String playerId = buf.readUtf(MAX_STRING_LENGTH);
            String playerName = buf.readUtf(MAX_STRING_LENGTH);
            int kills = buf.readVarInt();
            int eliteKills = buf.readVarInt();
            int damageDealt = buf.readVarInt();
            int damageTaken = buf.readVarInt();
            int deaths = buf.readVarInt();
            int maxCombo = buf.readVarInt();
            float dps = buf.readFloat();
            float killPercent = buf.readFloat();
            boolean wasSpectator = buf.readBoolean();

            players.add(new PlayerEntry(
                playerId, playerName, kills, eliteKills, damageDealt, damageTaken,
                deaths, maxCombo, dps, killPercent, wasSpectator
            ));
        }

        int partyTotalKills = buf.readVarInt();
        int partyEliteKills = buf.readVarInt();
        int partyTotalDamageDealt = buf.readVarInt();
        int partyTotalDamageTaken = buf.readVarInt();
        int partyDeaths = buf.readVarInt();
        int partyMaxCombo = buf.readVarInt();
        float partyDPS = buf.readFloat();
        boolean partyNoDamageWave = buf.readBoolean();
        String mvpPlayerId = buf.readUtf(MAX_STRING_LENGTH);
        String mvpReason = buf.readUtf(MAX_STRING_LENGTH);

        return new PartyStatsSyncPayload(
            waveNumber, totalWaves, waveDurationMs,
            players,
            partyTotalKills, partyEliteKills, partyTotalDamageDealt, partyTotalDamageTaken,
            partyDeaths, partyMaxCombo, partyDPS, partyNoDamageWave,
            mvpPlayerId, mvpReason
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += 4 + 4 + 8; // waveNumber, totalWaves, waveDurationMs
        size += 4; // player count
        for (PlayerEntry player : players) {
            size += estimatedUtfSize(player.playerId);
            size += estimatedUtfSize(player.playerName);
            size += 4 * 7; // kills, eliteKills, damageDealt, damageTaken, deaths, maxCombo (varints)
            size += 4 + 4 + 1; // dps, killPercent, wasSpectator
        }
        size += 4 * 6; // party totals (varints)
        size += 4 + 1; // partyDPS, partyNoDamageWave
        size += estimatedUtfSize(mvpPlayerId);
        size += estimatedUtfSize(mvpReason);
        return size;
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return 1;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return 1 + bytes.length;
    }

    /**
     * Convert to PartyWaveStats for client-side use.
     */
    public PartyWaveStats toPartyWaveStats() {
        List<PartyWaveStats.PlayerWaveData> playerData = new ArrayList<>();
        for (PlayerEntry entry : players) {
            playerData.add(new PartyWaveStats.PlayerWaveData(
                entry.playerId,
                entry.playerName,
                entry.kills,
                entry.eliteKills,
                entry.damageDealt,
                entry.damageTaken,
                entry.deaths,
                entry.maxCombo,
                entry.dps,
                entry.killPercent,
                entry.wasSpectator
            ));
        }

        return new PartyWaveStats(
            waveNumber, totalWaves, waveDurationMs,
            playerData,
            partyTotalKills, partyEliteKills, partyTotalDamageDealt, partyTotalDamageTaken,
            partyDeaths, partyMaxCombo, partyDPS, partyNoDamageWave,
            mvpPlayerId, mvpReason
        );
    }

    /**
     * Create from PartyWaveStats (server-side).
     */
    public static PartyStatsSyncPayload fromPartyWaveStats(PartyWaveStats stats) {
        List<PlayerEntry> entries = new ArrayList<>();
        for (PartyWaveStats.PlayerWaveData player : stats.playerStats()) {
            entries.add(new PlayerEntry(
                player.playerId(),
                player.playerName(),
                player.kills(),
                player.eliteKills(),
                player.damageDealt(),
                player.damageTaken(),
                player.deaths(),
                player.maxCombo(),
                player.dps(),
                player.killPercent(),
                player.wasSpectator()
            ));
        }

        return new PartyStatsSyncPayload(
            stats.waveNumber(), stats.totalWaves(), stats.waveDurationMs(),
            entries,
            stats.partyTotalKills(), stats.partyEliteKills(),
            stats.partyTotalDamageDealt(), stats.partyTotalDamageTaken(),
            stats.partyDeaths(), stats.partyMaxCombo(),
            stats.partyDPS(), stats.partyNoDamageWave(),
            stats.mvpPlayerId(), stats.mvpReason()
        );
    }
}
