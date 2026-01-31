package com.devmod.hologram.data;

import java.util.EnumSet;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;

/**
 * Filters for showing/hiding specific entity types in the hologram.
 * Allows players to focus on particular entity categories.
 */
public enum EntityFilterType {
    /** Hostile mobs (zombies, skeletons, etc.) */
    HOSTILE("Hostile", 0xFF4444),

    /** Passive animals (cows, pigs, etc.) */
    PASSIVE("Passive", 0x44FF44),

    /** Players */
    PLAYER("Players", 0x4444FF),

    /** NPCs (villagers, wandering traders) */
    NPC("NPCs", 0xFFAA44),

    /** Dropped items */
    ITEM("Items", 0xFFFF44),

    /** Vehicles (boats, minecarts) */
    VEHICLE("Vehicles", 0x44FFFF),

    /** Other entities (armor stands, etc.) */
    OTHER("Other", 0xAAAAAA);

    private final String displayName;
    private final int color;

    EntityFilterType(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the display color for this entity type (RGB format).
     */
    public int getColor() {
        return color;
    }

    /**
     * Determine the filter type for an entity.
     *
     * @param entity The entity to categorize
     * @return The appropriate filter type
     */
    public static EntityFilterType fromEntity(Entity entity) {
        if (entity instanceof Monster) return HOSTILE;
        if (entity instanceof Player) return PLAYER;
        if (entity instanceof Animal) return PASSIVE;
        if (entity instanceof Villager || entity instanceof WanderingTrader) return NPC;
        if (entity instanceof ItemEntity) return ITEM;
        if (entity instanceof Boat || entity instanceof Minecart) return VEHICLE;
        return OTHER;
    }

    /**
     * Get the default set of entity filters (all enabled).
     */
    public static EnumSet<EntityFilterType> getDefaultFilters() {
        return EnumSet.allOf(EntityFilterType.class);
    }

    /**
     * Get an empty filter set (no entities shown).
     */
    public static EnumSet<EntityFilterType> none() {
        return EnumSet.noneOf(EntityFilterType.class);
    }

    /**
     * Get all filters.
     */
    public static EnumSet<EntityFilterType> all() {
        return EnumSet.allOf(EntityFilterType.class);
    }

    /**
     * Convert an EnumSet to a bitmask for network/NBT serialization.
     */
    public static int toBitmask(EnumSet<EntityFilterType> filters) {
        int result = 0;
        for (EntityFilterType filter : filters) {
            result |= (1 << filter.ordinal());
        }
        return result;
    }

    /**
     * Convert a bitmask back to an EnumSet.
     */
    public static EnumSet<EntityFilterType> fromBitmask(int bitmask) {
        EnumSet<EntityFilterType> result = EnumSet.noneOf(EntityFilterType.class);
        for (EntityFilterType filter : values()) {
            if ((bitmask & (1 << filter.ordinal())) != 0) {
                result.add(filter);
            }
        }
        return result;
    }
}
