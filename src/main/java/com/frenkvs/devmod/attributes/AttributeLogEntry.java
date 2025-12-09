package com.frenkvs.devmod.attributes;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Voce del log per il sistema di monitoraggio attributi.
 * Record immutabile con timestamp, tipo, messaggio e posizione opzionale.
 */
public record AttributeLogEntry(
    Type type,
    String message,
    @Nullable Vec3 position,
    long timestamp
) {
    /**
     * Tipi di eventi loggati.
     */
    public enum Type {
        // Tracking
        ENTITY_DETECTED("§a[+]", 0xFF00FF00),      // Verde
        ENTITY_LOST("§c[-]", 0xFFFF0000),          // Rosso
        TARGET_CHANGED("§e[T]", 0xFFFFFF00),       // Giallo

        // Combat
        DAMAGE_DEALT("§c[DMG]", 0xFFFF5555),       // Rosso chiaro
        DAMAGE_RECEIVED("§4[HIT]", 0xFFAA0000),    // Rosso scuro
        KILL("§6[KILL]", 0xFFFFAA00),              // Arancione

        // Status
        HEALTH_LOW("§c[LOW]", 0xFFFF0000),         // Rosso
        HEALTH_CRITICAL("§4[CRIT]", 0xFF550000),   // Rosso scuro
        HEALING("§a[HEAL]", 0xFF00FF00),           // Verde

        // LoS
        LOS_GAINED("§a[LoS+]", 0xFF55FF55),        // Verde chiaro
        LOS_LOST("§7[LoS-]", 0xFFAAAAAA),          // Grigio

        // Movement
        TELEPORT("§d[TP]", 0xFFFF55FF),            // Magenta
        AGGRO("§c[AGGRO]", 0xFFFF0000),            // Rosso

        // System
        INFO("§7[i]", 0xFFAAAAAA),                 // Grigio
        WARNING("§e[!]", 0xFFFFFF00),              // Giallo
        ERROR("§c[X]", 0xFFFF0000);                // Rosso

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
        return type.getPrefix() + " §f" + message;
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
