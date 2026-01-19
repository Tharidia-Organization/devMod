package com.devmod.nexus.data;

/**
 * Defines the type of a zone slot in the Nexus hub.
 * Determines editing permissions and behavior.
 *
 * <p>Design Note: Specific test configurations (mob testing, wave testing, etc.)
 * are handled by the Area Editor module, not by SlotType variants.
 */
public enum SlotType {

    /**
     * Fixed slot - cannot be modified.
     * Used for core hub infrastructure: spawn point, central platform, corridors.
     */
    FIXED,

    /**
     * Editable slot - can be modified via Area Builder.
     * Standard zone type for player-editable content.
     */
    EDITABLE,

    /**
     * Test area slot - temporary and removable.
     * Used for creating disposable testing zones that can be deleted.
     */
    TEST_AREA,

    /**
     * Restricted slot - only admin can modify.
     * Used for important zones that require elevated permissions to edit.
     */
    RESTRICTED;

    /**
     * Check if this slot type allows player editing.
     *
     * @return true if players can edit this slot via Area Builder
     */
    public boolean isPlayerEditable() {
        return this == EDITABLE;
    }

    /**
     * Check if this slot type allows admin editing.
     *
     * @return true if admins can edit this slot
     */
    public boolean isAdminEditable() {
        return this != FIXED;
    }

    /**
     * Check if this slot type is temporary and can be removed.
     *
     * @return true if the slot can be deleted
     */
    public boolean isRemovable() {
        return this == TEST_AREA;
    }

    /**
     * Check if this slot type represents core infrastructure.
     *
     * @return true if this is a fixed infrastructure slot
     */
    public boolean isInfrastructure() {
        return this == FIXED;
    }
}
