package com.devmod.nexus.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Defines permissions for a zone slot in the Nexus hub.
 * Controls what actions are allowed within the slot.
 *
 * <p>This uses a simple permission model based on permission levels (0-4).
 * For more complex team/party permissions, integrate with the party module.
 */
public record SlotPermissions(
    boolean canEdit,           // Can modify content via Area Builder
    boolean canPlace,          // Can place blocks
    boolean canBreak,          // Can break blocks
    boolean canSpawnMobs,      // Can spawn mobs (Clone module)
    boolean canUsePortals,     // Can use portals
    int minPermissionLevel     // Minimum OP level required (0-4)
) {

    /**
     * Codec for NBT/JSON serialization.
     */
    public static final Codec<SlotPermissions> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("canEdit").forGetter(SlotPermissions::canEdit),
            Codec.BOOL.fieldOf("canPlace").forGetter(SlotPermissions::canPlace),
            Codec.BOOL.fieldOf("canBreak").forGetter(SlotPermissions::canBreak),
            Codec.BOOL.fieldOf("canSpawnMobs").forGetter(SlotPermissions::canSpawnMobs),
            Codec.BOOL.fieldOf("canUsePortals").forGetter(SlotPermissions::canUsePortals),
            Codec.INT.fieldOf("minPermissionLevel").forGetter(SlotPermissions::minPermissionLevel)
        ).apply(instance, (canEdit, canPlace, canBreak, canSpawnMobs, canUsePortals, minPermissionLevel) ->
            new SlotPermissions(canEdit, canPlace, canBreak, canSpawnMobs, canUsePortals, minPermissionLevel))
    );

    /**
     * StreamCodec for network synchronization.
     */
    public static final StreamCodec<FriendlyByteBuf, SlotPermissions> STREAM_CODEC = StreamCodec.of(
        (buf, perms) -> {
            buf.writeBoolean(perms.canEdit);
            buf.writeBoolean(perms.canPlace);
            buf.writeBoolean(perms.canBreak);
            buf.writeBoolean(perms.canSpawnMobs);
            buf.writeBoolean(perms.canUsePortals);
            buf.writeVarInt(perms.minPermissionLevel);
        },
        buf -> new SlotPermissions(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readVarInt()
        )
    );

    // === Preset Configurations ===

    /**
     * Default permissions - read-only, portals only.
     * Used for visitors/players without special access.
     */
    public static final SlotPermissions DEFAULT = new SlotPermissions(
        false, false, false, false, true, 0
    );

    /**
     * Admin-only permissions - full access, requires OP level 2.
     * Used for restricted zones.
     */
    public static final SlotPermissions ADMIN_ONLY = new SlotPermissions(
        true, true, true, true, true, 2
    );

    /**
     * Full access - all permissions, no OP required.
     * Used for editable zones open to all players.
     */
    public static final SlotPermissions FULL_ACCESS = new SlotPermissions(
        true, true, true, true, true, 0
    );

    /**
     * Builder permissions - can edit and place, but not spawn mobs.
     * Useful for building zones.
     */
    public static final SlotPermissions BUILDER = new SlotPermissions(
        true, true, true, false, true, 0
    );

    /**
     * Test area permissions - full access for testing purposes.
     * Same as FULL_ACCESS but semantically distinct.
     */
    public static final SlotPermissions TEST_AREA = FULL_ACCESS;

    // === Permission Checks ===

    /**
     * Check if a player has sufficient permission level.
     *
     * @param player the player to check
     * @return true if player meets the minimum permission level
     */
    public boolean hasPermissionLevel(ServerPlayer player) {
        return player.hasPermissions(minPermissionLevel);
    }

    /**
     * Check if a player can edit this slot.
     *
     * @param player the player to check
     * @return true if player can edit
     */
    public boolean canPlayerEdit(ServerPlayer player) {
        return canEdit && hasPermissionLevel(player);
    }

    /**
     * Check if a player can place blocks in this slot.
     *
     * @param player the player to check
     * @return true if player can place blocks
     */
    public boolean canPlayerPlace(ServerPlayer player) {
        return canPlace && hasPermissionLevel(player);
    }

    /**
     * Check if a player can break blocks in this slot.
     *
     * @param player the player to check
     * @return true if player can break blocks
     */
    public boolean canPlayerBreak(ServerPlayer player) {
        return canBreak && hasPermissionLevel(player);
    }

    /**
     * Check if a player can spawn mobs in this slot.
     *
     * @param player the player to check
     * @return true if player can spawn mobs
     */
    public boolean canPlayerSpawnMobs(ServerPlayer player) {
        return canSpawnMobs && hasPermissionLevel(player);
    }

    /**
     * Check if a player can use portals in this slot.
     *
     * @param player the player to check
     * @return true if player can use portals
     */
    public boolean canPlayerUsePortals(ServerPlayer player) {
        return canUsePortals && hasPermissionLevel(player);
    }

    // === Factory Methods ===

    /**
     * Create permissions for a specific slot type.
     *
     * @param type the slot type
     * @return appropriate permissions for the type
     */
    public static SlotPermissions forSlotType(SlotType type) {
        return switch (type) {
            case FIXED -> DEFAULT;
            case EDITABLE -> FULL_ACCESS;
            case TEST_AREA -> TEST_AREA;
            case RESTRICTED -> ADMIN_ONLY;
        };
    }

    /**
     * Create a copy with a different permission level.
     *
     * @param level the new minimum permission level (0-4)
     * @return new SlotPermissions with updated level
     */
    public SlotPermissions withPermissionLevel(int level) {
        return new SlotPermissions(
            canEdit, canPlace, canBreak, canSpawnMobs, canUsePortals,
            Math.max(0, Math.min(4, level))
        );
    }

    /**
     * Create a copy with editing enabled/disabled.
     *
     * @param canEdit whether editing is allowed
     * @return new SlotPermissions with updated edit permission
     */
    public SlotPermissions withCanEdit(boolean canEdit) {
        return new SlotPermissions(
            canEdit, canPlace, canBreak, canSpawnMobs, canUsePortals, minPermissionLevel
        );
    }
}
