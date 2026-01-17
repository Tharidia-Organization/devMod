package com.devmod.template.data;

import javax.annotation.Nonnull;

import net.minecraft.util.StringRepresentable;

/**
 * Categories for room decoration components.
 */
public enum ComponentCategory implements StringRepresentable {
    DISPLAY("display"),       // Screens, panels, holograms
    CONSOLE("console"),       // Tables, workstations
    COMBAT("combat"),         // Target walls, rings, training posts
    STORAGE("storage"),       // Racks, chests, shelves
    UTILITY("utility"),       // Benches, crafting stations
    DECORATIVE("decorative"), // Plinths, pillars, statues
    FUNCTIONAL("functional"), // Pods, bays, interactive elements
    QUEST("quest"),           // Bulletin boards, quest givers
    SHOP("shop"),             // Vendor stalls, auction blocks
    TRANSPORT("transport");   // Waypoints, portals

    private final String name;

    ComponentCategory(String name) {
        this.name = name;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return name;
    }

    public static ComponentCategory fromString(String name) {
        for (ComponentCategory cat : values()) {
            if (cat.name.equals(name)) {
                return cat;
            }
        }
        return DECORATIVE;
    }
}
