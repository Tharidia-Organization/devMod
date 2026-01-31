package com.devmod.attributes;

import javax.annotation.Nullable;

import net.minecraft.world.phys.Vec3;

public record AttributeLogEntry(
    Type type,
    String message,
    @Nullable Vec3 position,
    long timestamp
) {
    /**
     * Types of logged events.
     */
    public enum Type {
        // Tracking
        ENTITY_DETECTED("\u00A7a[+]", AttributeLogColors.GREEN),
        ENTITY_LOST("\u00A7c[-]", AttributeLogColors.RED),
        TARGET_CHANGED("\u00A7e[T]", AttributeLogColors.YELLOW),

        // Combat
        DAMAGE_DEALT("\u00A7c[DMG]", AttributeLogColors.LIGHT_RED),
        DAMAGE_RECEIVED("\u00A74[HIT]", AttributeLogColors.DARK_RED),
        KILL("\u00A76[KILL]", AttributeLogColors.ORANGE),

        // Status
        HEALTH_LOW("\u00A7c[LOW]", AttributeLogColors.RED),
        HEALTH_CRITICAL("\u00A74[CRIT]", AttributeLogColors.CRITICAL_RED),
        HEALING("\u00A7a[HEAL]", AttributeLogColors.GREEN),

        // LoS
        LOS_GAINED("\u00A7a[LoS+]", AttributeLogColors.LIGHT_GREEN),
        LOS_LOST("\u00A77[LoS-]", AttributeLogColors.GRAY),

        // Movement
        TELEPORT("\u00A7d[TP]", AttributeLogColors.MAGENTA),
        AGGRO("\u00A7c[AGGRO]", AttributeLogColors.RED),

        // System
        INFO("\u00A77[i]", AttributeLogColors.GRAY),
        WARNING("\u00A7e[!]", AttributeLogColors.YELLOW),
        ERROR("\u00A7c[X]", AttributeLogColors.RED);

        private final String prefix;
        private final int color;

        Type(String prefix, int color) {
            this.prefix = prefix;
            this.color = color;
        }

        public String getPrefix() {
            return prefix;
        }

        public int getColor() {
            return color;
        }
    }

    /**
     * Costruttore con timestamp automatico.
     */
    public AttributeLogEntry(Type type, String message, @Nullable Vec3 position) {
        this(type, message, position, System.currentTimeMillis());
    }

    /**
     * Ottiene il messaggio formattato con prefisso colorato.
     */
    public String getFormattedMessage() {
        return type.getPrefix() + " \u00A7f" + message;
    }

    /**
     * Ottiene l'età del log in millisecondi.
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Ottiene l'età formattata (es. "5s", "1m 30s").
     */
    public String getFormattedAge() {
        long age = getAge();
        if (age < 1000) {
            return "now";
        } else if (age < 60000) {
            return (age / 1000) + "s";
        } else {
            long minutes = age / 60000;
            long seconds = (age % 60000) / 1000;
            return minutes + "m " + seconds + "s";
        }
    }

    /**
     * Calcola l'alpha per il fade out (basato sull'età).
     * @param maxAge Età massima in ms prima di scomparire completamente
     * @return Alpha da 1.0 (nuovo) a 0.0 (vecchio)
     */
    public float getAlpha(long maxAge) {
        long age = getAge();
        if (age >= maxAge) return 0f;

        // Inizia fade negli ultimi 30%
        long fadeStart = (long) (maxAge * 0.7);
        if (age < fadeStart) return 1f;

        float fadeProgress = (age - fadeStart) / (float) (maxAge - fadeStart);
        return 1f - fadeProgress;
    }
}
