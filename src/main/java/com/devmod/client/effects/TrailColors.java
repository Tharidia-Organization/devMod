package com.devmod.client.effects;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Shared palette for projectile trail rendering.
 */
public final class TrailColors {
    private TrailColors() {}

    public static final class Entity {
        public static final int ARROW = DesignTokens.Trail.Entity.ARROW;
        public static final int POTION = DesignTokens.Trail.Entity.POTION;
        public static final int TRIDENT = DesignTokens.Trail.Entity.TRIDENT;
        public static final int FIREWORK = DesignTokens.Trail.Entity.FIREWORK;
        public static final int WITHER_SKULL = DesignTokens.Trail.Entity.WITHER_SKULL;
        public static final int FIREBALL = DesignTokens.Trail.Entity.FIREBALL;
        public static final int SMALL_FIREBALL = DesignTokens.Trail.Entity.SMALL_FIREBALL;
        public static final int SHULKER_BULLET = DesignTokens.Trail.Entity.SHULKER_BULLET;
        public static final int XP_ORB = DesignTokens.Trail.Entity.XP_ORB;
        public static final int ENDER_EYE = DesignTokens.Trail.Entity.ENDER_EYE;
        public static final int ELYTRA = DesignTokens.Trail.Entity.ELYTRA;
        public static final int DEFAULT = DesignTokens.Trail.Entity.DEFAULT;

        private Entity() {}
    }
}
