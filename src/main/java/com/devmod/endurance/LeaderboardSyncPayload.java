package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;
import com.devmod.stats.LeaderboardEntry;

/**
 * Server -> Client payload carrying leaderboard data for all three tabs
 * (arena, style, quests). The client caches these for responsive tab switching.
 */
public record LeaderboardSyncPayload(
    List<LeaderboardEntry> arenaEntries,
    List<LeaderboardEntry> styleEntries,
    List<LeaderboardEntry> questEntries
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final int MAX_STRING_LENGTH = 64;
    private static final int MAX_ENTRIES_PER_TAB = 100;

    public static final Type<LeaderboardSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "leaderboard_sync"))
    );

    public static final StreamCodec<ByteBuf, LeaderboardSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LeaderboardSyncPayload decode(@Nonnull ByteBuf buf) {
            List<LeaderboardEntry> arena = readEntryList(buf);
            List<LeaderboardEntry> style = readEntryList(buf);
            List<LeaderboardEntry> quests = readEntryList(buf);
            return new LeaderboardSyncPayload(arena, style, quests);
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull LeaderboardSyncPayload payload) {
            writeEntryList(buf, payload.arenaEntries);
            writeEntryList(buf, payload.styleEntries);
            writeEntryList(buf, payload.questEntries);
        }

        private List<LeaderboardEntry> readEntryList(ByteBuf buf) {
            int count = Math.min(buf.readInt(), MAX_ENTRIES_PER_TAB);
            List<LeaderboardEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int rank = buf.readInt();
                long uuidHigh = buf.readLong();
                long uuidLow = buf.readLong();
                UUID playerId = new UUID(uuidHigh, uuidLow);
                String playerName = readString(buf);
                long score = buf.readLong();
                long secondary = buf.readLong();
                long tertiary = buf.readLong();
                entries.add(new LeaderboardEntry(rank, playerId, playerName, score, secondary, tertiary));
            }
            return entries;
        }

        private void writeEntryList(ByteBuf buf, List<LeaderboardEntry> entries) {
            int count = Math.min(entries.size(), MAX_ENTRIES_PER_TAB);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                LeaderboardEntry e = entries.get(i);
                buf.writeInt(e.rank());
                buf.writeLong(e.playerId().getMostSignificantBits());
                buf.writeLong(e.playerId().getLeastSignificantBits());
                writeString(buf, e.playerName());
                buf.writeLong(e.score());
                buf.writeLong(e.secondary());
                buf.writeLong(e.tertiary());
            }
        }

        private String readString(ByteBuf buf) {
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) length = 0;
            if (length == 0) return "";
            return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        }

        private void writeString(ByteBuf buf, String str) {
            if (str == null) str = "";
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(bytes.length, MAX_STRING_LENGTH);
            buf.writeInt(length);
            buf.writeBytes(bytes, 0, length);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        // Per entry: 4 (rank) + 16 (uuid) + ~20 (name) + 24 (3 longs) = ~64 bytes
        int entrySize = 64;
        int total = 4; // arena count
        total += arenaEntries.size() * entrySize;
        total += 4; // style count
        total += styleEntries.size() * entrySize;
        total += 4; // quest count
        total += questEntries.size() * entrySize;
        return total;
    }
}
