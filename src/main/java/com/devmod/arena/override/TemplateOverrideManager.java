package com.devmod.arena.override;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class TemplateOverrideManager {

    private static final TemplateOverrideManager INSTANCE = new TemplateOverrideManager();

    /** Session-based overrides (cleared on server restart) */
    private final Map<UUID, TemplateOverride> sessionOverrides = new ConcurrentHashMap<>();

    /** Default TTL for overrides: 1 hour */
    private static final long DEFAULT_TTL_SECONDS = 3600;

    private TemplateOverrideManager() {}

    public static TemplateOverrideManager getInstance() {
        return INSTANCE;
    }

    /**
     * Sets a template override for a player (session-only)
     *
     * @param playerId The player's UUID
     * @param templateId The template to force
     * @param ttlSeconds Time-to-live in seconds (0 = use default)
     * @return The created override
     */
    public TemplateOverride setOverride(UUID playerId, ResourceLocation templateId, long ttlSeconds) {
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        Instant expiry = Instant.now().plusSeconds(effectiveTtl);

        TemplateOverride override = new TemplateOverride(templateId, expiry, false);
        sessionOverrides.put(playerId, override);

        return override;
    }

    /**
     * Sets a persistent template override (survives relog via data attachment)
     *
     * @param player The player
     * @param templateId The template to force
     * @param ttlSeconds Time-to-live in seconds
     * @return The created override
     */
    public TemplateOverride setPersistentOverride(ServerPlayer player, ResourceLocation templateId, long ttlSeconds) {
        TemplateOverride override = setOverride(player.getUUID(), templateId, ttlSeconds);
        TemplateOverride persistentOverride = override.withPersistent(true);

        // Store in data attachment for relog persistence
        TemplateOverrideCapability.TemplateOverrideData data =
                player.getData(Objects.requireNonNull(TemplateOverrideCapability.TEMPLATE_OVERRIDE.get()));
        data.setOverride(persistentOverride);

        return persistentOverride;
    }

    /**
     * Gets the active override for a player
     *
     * @param playerId The player's UUID
     * @return The active override, or empty if none or expired
     */
    public Optional<TemplateOverride> getOverride(UUID playerId) {
        TemplateOverride override = sessionOverrides.get(playerId);

        if (override == null) {
            return Optional.empty();
        }

        // Check expiry
        if (override.isExpired()) {
            sessionOverrides.remove(playerId);
            return Optional.empty();
        }

        return Optional.of(override);
    }

    /**
     * Gets the forced template ID for a player, if any
     *
     * @param playerId The player's UUID
     * @return The forced template ID, or empty
     */
    public Optional<ResourceLocation> getForcedTemplateId(UUID playerId) {
        return getOverride(playerId).map(TemplateOverride::templateId);
    }

    /**
     * Clears any override for a player
     *
     * @param playerId The player's UUID
     */
    public void clearOverride(UUID playerId) {
        sessionOverrides.remove(playerId);
    }

    /**
     * Clears a persistent override (both session and data attachment)
     *
     * @param player The player
     */
    public void clearPersistentOverride(ServerPlayer player) {
        clearOverride(player.getUUID());

        TemplateOverrideCapability.TemplateOverrideData data =
                player.getData(Objects.requireNonNull(TemplateOverrideCapability.TEMPLATE_OVERRIDE.get()));
        data.clearOverride();
    }

    /**
     * Restores override from data attachment (called on player login)
     *
     * @param player The player logging in
     */
    public void restoreFromAttachment(ServerPlayer player) {
        TemplateOverrideCapability.TemplateOverrideData data =
                player.getData(Objects.requireNonNull(TemplateOverrideCapability.TEMPLATE_OVERRIDE.get()));

        data.getOverride().ifPresent(override -> {
            if (!override.isExpired()) {
                sessionOverrides.put(player.getUUID(), override);
            } else {
                data.clearOverride();
            }
        });
    }

    /**
     * Cleans up expired overrides (call periodically)
     */
    public void cleanupExpired() {
        sessionOverrides.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Gets the count of active overrides
     */
    public int getActiveOverrideCount() {
        return sessionOverrides.size();
    }

    /**
     * Represents a template override with expiry
     */
    public record TemplateOverride(
        ResourceLocation templateId,
        Instant expiry,
        boolean persistent
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }

        public long getRemainingSeconds() {
            return Math.max(0, expiry.getEpochSecond() - Instant.now().getEpochSecond());
        }

        public TemplateOverride withPersistent(boolean persistent) {
            return new TemplateOverride(templateId, expiry, persistent);
        }

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("templateId", Objects.requireNonNull(templateId.toString()));
            tag.putLong("expiry", expiry.getEpochSecond());
            tag.putBoolean("persistent", persistent);
            return tag;
        }

        @Nullable
        public static TemplateOverride fromNbt(CompoundTag tag) {
            String templateIdStr = Objects.requireNonNull(tag.getString("templateId"));
            ResourceLocation templateId = ResourceLocation.tryParse(templateIdStr);
            if (templateId == null) {
                return null;
            }
            Instant expiry = Instant.ofEpochSecond(tag.getLong("expiry"));
            boolean persistent = tag.getBoolean("persistent");
            return new TemplateOverride(templateId, expiry, persistent);
        }
    }
}
