package com.devmod.party.lfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.party.PartyData;
import com.devmod.party.PartyManager;

/**
 * Server-side singleton managing LFG (Looking-For-Group) listings.
 * Thread-safe via ConcurrentHashMap.
 */
public final class LFGManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LFGManager.class);

    public static final LFGManager INSTANCE = new LFGManager();

    /** All active listings by listing ID */
    private final Map<UUID, LFGListing> listings = new ConcurrentHashMap<>();

    /** Leader UUID -> Listing ID for quick lookup (one listing per leader) */
    private final Map<UUID, UUID> leaderToListing = new ConcurrentHashMap<>();

    /** Expiration: 10 minutes in ticks (20 tps * 60 sec * 10 min) */
    private static final long EXPIRY_TICKS = 20L * 60 * 10;

    /** Maximum description length */
    private static final int MAX_DESCRIPTION_LENGTH = 120;

    /** Maximum listings globally */
    private static final int MAX_LISTINGS = 100;

    private LFGManager() {}

    /**
     * Create a new LFG listing for a party leader.
     *
     * @return the created listing, or null if failed
     */
    @Nullable
    public LFGListing createListing(ServerPlayer leader, LFGActivity activity,
                                     String description, int minPlayers, int maxPlayers,
                                     boolean autoAccept, long currentTick) {
        UUID leaderId = leader.getUUID();
        String leaderName = leader.getName().getString();

        // One listing per leader
        if (leaderToListing.containsKey(leaderId)) {
            LOGGER.debug("[LFG] Leader {} already has a listing", leaderName);
            return null;
        }

        // Global cap
        if (listings.size() >= MAX_LISTINGS) {
            LOGGER.debug("[LFG] Max listings reached ({})", MAX_LISTINGS);
            return null;
        }

        // Sanitize
        if (description == null) description = "";
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        minPlayers = Math.max(1, Math.min(minPlayers, 8));
        maxPlayers = Math.max(minPlayers, Math.min(maxPlayers, 8));

        // Get current party size
        PartyData party = PartyManager.INSTANCE.getPlayerParty(leaderId);
        int currentPlayers = party != null ? party.getMemberCount() : 1;

        LFGListing listing = LFGListing.create(
            leaderId, leaderName, activity, description,
            minPlayers, maxPlayers, currentPlayers, autoAccept, currentTick
        );

        listings.put(listing.listingId(), listing);
        leaderToListing.put(leaderId, listing.listingId());

        LOGGER.info("[LFG] Listing created: {} by {} (activity: {}, {}/{})",
            listing.listingId(), leaderName, activity, currentPlayers, maxPlayers);

        return listing;
    }

    /**
     * Remove a listing by the leader's UUID.
     *
     * @return true if removed
     */
    public boolean removeListing(UUID leaderId) {
        UUID listingId = leaderToListing.remove(leaderId);
        if (listingId != null) {
            listings.remove(listingId);
            LOGGER.info("[LFG] Listing removed for leader {}", leaderId);
            return true;
        }
        return false;
    }

    /**
     * Get all active listings as an unmodifiable list.
     */
    public List<LFGListing> getListings() {
        return Collections.unmodifiableList(new ArrayList<>(listings.values()));
    }

    /**
     * Get a listing by its ID.
     */
    @Nullable
    public LFGListing getListing(UUID listingId) {
        return listings.get(listingId);
    }

    /**
     * Get the listing for a specific leader.
     */
    @Nullable
    public LFGListing getLeaderListing(UUID leaderId) {
        UUID listingId = leaderToListing.get(leaderId);
        return listingId != null ? listings.get(listingId) : null;
    }

    /**
     * Apply to a listing. If autoAccept, sends an invite directly.
     * Returns a result describing what happened.
     */
    public ApplyResult applyToListing(UUID listingId, ServerPlayer applicant) {
        LFGListing listing = listings.get(listingId);
        if (listing == null) {
            return ApplyResult.LISTING_NOT_FOUND;
        }

        UUID applicantId = applicant.getUUID();

        // Can't apply to your own listing
        if (listing.leaderId().equals(applicantId)) {
            return ApplyResult.OWN_LISTING;
        }

        // Check if already in a party
        if (PartyManager.INSTANCE.isInParty(applicantId)) {
            return ApplyResult.ALREADY_IN_PARTY;
        }

        // Check if listing is full
        PartyData party = PartyManager.INSTANCE.getPlayerParty(listing.leaderId());
        if (party != null && party.isFull()) {
            return ApplyResult.LISTING_FULL;
        }

        // Send invite from leader to applicant
        String applicantName = applicant.getName().getString();
        var result = PartyManager.INSTANCE.sendInvite(
            listing.leaderId(), listing.leaderName(),
            applicantId, applicantName,
            party != null ? party.getQuestType() : com.devmod.endurance.QuestType.PVE_COOP
        );

        if (result.success()) {
            // Update current player count in listing
            if (party != null) {
                listings.computeIfPresent(listingId, (id, l) ->
                    l.withCurrentPlayers(party.getMemberCount()));
            }
            return ApplyResult.INVITE_SENT;
        }

        return ApplyResult.INVITE_FAILED;
    }

    /**
     * Tick to expire old listings.
     *
     * @param currentTick current server tick
     */
    public void tick(long currentTick) {
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, LFGListing> entry : listings.entrySet()) {
            if (currentTick - entry.getValue().createdTick() > EXPIRY_TICKS) {
                expired.add(entry.getKey());
            }
        }
        for (UUID listingId : expired) {
            LFGListing listing = listings.remove(listingId);
            if (listing != null) {
                leaderToListing.remove(listing.leaderId());
                LOGGER.debug("[LFG] Listing {} expired", listingId);
            }
        }
    }

    /**
     * Reset all data (server shutdown/restart).
     */
    public void reset() {
        listings.clear();
        leaderToListing.clear();
        LOGGER.info("[LFG] Reset all LFG data");
    }

    /**
     * Result of applying to a listing.
     */
    public enum ApplyResult {
        INVITE_SENT("Invite sent!"),
        LISTING_NOT_FOUND("Listing no longer exists"),
        OWN_LISTING("Cannot apply to your own listing"),
        ALREADY_IN_PARTY("You are already in a party"),
        LISTING_FULL("Listing is full"),
        INVITE_FAILED("Failed to send invite");

        private final String message;

        ApplyResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
