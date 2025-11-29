package com.frenkvs.devmod;

import net.minecraft.world.entity.EntityType;
import java.util.HashMap;
import java.util.Map;

public class MobConfigManager {

    // Una mappa che collega il TIPO di entità (es. Zombie) alle sue STATISTICHE salvate
    private static final Map<EntityType<?>, SavedStats> globalConfigs = new HashMap<>();

    // Classe semplice per tenere i dati in memoria
    public record SavedStats(double range, double damage, double maxHealth, double armor, double attackReach) {}

    // Salva una configurazione globale
    public static void setGlobalStats(EntityType<?> type, double range, double damage, double maxHealth, double armor, double attackReach) {
        globalConfigs.put(type, new SavedStats(range, damage, maxHealth, armor, attackReach));
    }

    // Recupera la configurazione (o null se non esiste)
    public static SavedStats getGlobalStats(EntityType<?> type) {
        return globalConfigs.get(type);
    }

    // Controlla se abbiamo modifiche per questo tipo
    public static boolean hasConfig(EntityType<?> type) {
        return globalConfigs.containsKey(type);
    }
}