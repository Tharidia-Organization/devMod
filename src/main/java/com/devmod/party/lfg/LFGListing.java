package com.devmod.party.lfg;

import java.util.UUID;

/**
 * Represents a single LFG listing posted by a party leader.
 */
public record LFGListing(
    UUID listingId,
    UUID leaderId,
    String leaderName,
    LFGActivity activity,
    String description,
    int minPlayers,
    int maxPlayers,
    int currentPlayers,
    boolean autoAccept,
    long createdTick
) {
    /**
     * Create a new listing with a random ID and the current tick.
     */
    public static LFGListing create(UUID leaderId, String leaderName, LFGActivity activity,
                                     String description, int minPlayers, int maxPlayers,
                                     int currentPlayers, boolean autoAccept, long currentTick) {
        return new LFGListing(
            UUID.randomUUID(), leaderId, leaderName, activity,
            description, minPlayers, maxPlayers, currentPlayers,
            autoAccept, currentTick
        );
    }

    /**
     * Return a copy with updated current player count.
     */
    public LFGListing withCurrentPlayers(int count) {
        return new LFGListing(listingId, leaderId, leaderName, activity,
            description, minPlayers, maxPlayers, count, autoAccept, createdTick);
    }
}
