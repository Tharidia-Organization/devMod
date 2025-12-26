package com.devmod.arena.security;

import java.util.EnumSet;
import java.util.Set;
public enum TemplatePermission {

    /**
     * Can view template in listings and info commands.
     */
    VIEW("devmod.template.view", 0, "View template details"),

    /**
     * Can use template to create arena instances.
     * Includes VIEW.
     */
    USE("devmod.template.use", 1, "Use template to create arenas"),

    /**
     * Can modify template configuration and settings.
     * Includes VIEW, USE.
     */
    EDIT("devmod.template.edit", 2, "Edit template configuration"),

    /**
     * Can delete template from registry.
     * Includes VIEW, USE, EDIT.
     */
    DELETE("devmod.template.delete", 3, "Delete templates"),

    /**
     * Full admin access including hot-reload and force operations.
     * Includes all other permissions.
     */
    ADMIN("devmod.template.admin", 4, "Full template administration");

    private final String permissionNode;
    private final int level;
    private final String description;

    TemplatePermission(String permissionNode, int level, String description) {
        this.permissionNode = permissionNode;
        this.level = level;
        this.description = description;
    }

    /**
     * Returns the permission node string for integration with permission systems.
     */
    public String permissionNode() {
        return permissionNode;
    }

    /**
     * Returns the hierarchical level (higher = more permissions).
     */
    public int level() {
        return level;
    }

    /**
     * Returns a human-readable description.
     */
    public String description() {
        return description;
    }

    /**
     * Returns true if this permission implies the given permission.
     * Higher levels imply lower levels.
     */
    public boolean implies(TemplatePermission other) {
        return this.level >= other.level;
    }

    /**
     * Returns all permissions implied by this permission.
     */
    public Set<TemplatePermission> impliedPermissions() {
        EnumSet<TemplatePermission> implied = EnumSet.noneOf(TemplatePermission.class);
        for (TemplatePermission p : values()) {
            if (this.implies(p)) {
                implied.add(p);
            }
        }
        return implied;
    }

    /**
     * Returns the minimum operator level required for this permission.
     * Useful for vanilla server integration.
     */
    public int requiredOpLevel() {
        return switch (this) {
            case VIEW -> 0;      // Anyone can view
            case USE -> 0;       // Anyone can use (subject to other checks)
            case EDIT -> 2;      // Requires op level 2
            case DELETE -> 3;    // Requires op level 3
            case ADMIN -> 4;     // Requires op level 4
        };
    }

    /**
     * Finds permission by node string.
     */
    public static TemplatePermission fromNode(String node) {
        for (TemplatePermission p : values()) {
            if (p.permissionNode.equals(node)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown permission node: " + node);
    }

    /**
     * Finds minimum permission required for the given operator level.
     */
    public static TemplatePermission forOpLevel(int opLevel) {
        TemplatePermission result = VIEW;
        for (TemplatePermission p : values()) {
            if (opLevel >= p.requiredOpLevel()) {
                result = p;
            }
        }
        return result;
    }

    /**
     * Returns all permission nodes as an array (for registration).
     */
    public static String[] allNodes() {
        String[] nodes = new String[values().length];
        for (int i = 0; i < values().length; i++) {
            nodes[i] = values()[i].permissionNode;
        }
        return nodes;
    }

    /**
     * Checks if a set of permissions includes the required permission.
     */
    public static boolean hasPermission(Set<TemplatePermission> granted, TemplatePermission required) {
        for (TemplatePermission p : granted) {
            if (p.implies(required)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a permission set from op level.
     */
    public static Set<TemplatePermission> fromOpLevel(int opLevel) {
        EnumSet<TemplatePermission> perms = EnumSet.noneOf(TemplatePermission.class);
        for (TemplatePermission p : values()) {
            if (opLevel >= p.requiredOpLevel()) {
                perms.add(p);
            }
        }
        return perms;
    }
}
