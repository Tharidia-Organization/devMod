package com.devmod.telemetry.damage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for damage tracking and weapon statistics.
 *
 * Manages:
 * - Weapon aggregates (total damage, hits, kills, misses)
 * - Room aggregates (damage to player vs mob, deaths)
 * - Minion statistics (damage dealt during lifetime)
 * - TTK tracking (time to kill)
 */
public class DamageTrackingService {
    public static final DamageTrackingService INSTANCE = new DamageTrackingService();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<Item, WeaponAggregate> weaponAggregates = new ConcurrentHashMap<>();
    private final Map<String, RoomAggregate> roomAggregates = new ConcurrentHashMap<>();
    private final Map<UUID, MinionDamageStats> minionDamage = new ConcurrentHashMap<>();

    private Path dataDirectory = null;

    private DamageTrackingService() {}

    // ============================================
    // WEAPON TRACKING
    // ============================================

    /**
     * Registers a hit with a weapon.
     */
    public void registerWeaponHit(ServerPlayer player, double damage, boolean isKill) {
        Item weapon = player.getMainHandItem().getItem();
        weaponAggregates.computeIfAbsent(weapon, k -> new WeaponAggregate())
                .registerHit(damage, isKill);
    }

    /**
     * Registers a miss with a weapon.
     */
    public void registerWeaponMiss(ServerPlayer player) {
        Item weapon = player.getMainHandItem().getItem();
        weaponAggregates.computeIfAbsent(weapon, k -> new WeaponAggregate())
                .registerMiss();
    }

    /**
     * Gets weapon statistics as formatted strings.
     */
    public List<String> getWeaponSummaries() {
        List<String> lines = new ArrayList<>();
        weaponAggregates.forEach((item, agg) -> {
            String id = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item))).toString();
            lines.add(String.format(Locale.ROOT,
                    "%s dmg=%.2f hits=%d kills=%d miss=%d acc=%.1f%%",
                    id, agg.totalDamage, agg.hits, agg.kills, agg.misses,
                    agg.getAccuracy() * 100));
        });
        return lines;
    }

    /**
     * Ottiene l'aggregato per un'arma specifica.
     */
    public Optional<WeaponAggregate> getWeaponStats(Item weapon) {
        return Optional.ofNullable(weaponAggregates.get(weapon));
    }

    // ============================================
    // ROOM TRACKING
    // ============================================

    /**
     * Registra danno inflitto a un target in una stanza.
     */
    public void registerRoomDamage(String room, LivingEntity target, double damage, boolean isKill) {
        roomAggregates.computeIfAbsent(room, k -> new RoomAggregate())
                .registerDamage(target, damage, isKill);
    }

    /**
     * Registra heal in una stanza.
     */
    public void registerRoomHeal(String room, LivingEntity target, double heal) {
        roomAggregates.computeIfAbsent(room, k -> new RoomAggregate())
                .registerHeal(target, heal);
    }

    /**
     * Ottiene le statistiche per stanza come stringhe formattate.
     */
    public List<String> getRoomSummaries() {
        List<String> lines = new ArrayList<>();
        roomAggregates.forEach((room, agg) -> {
            lines.add(String.format(Locale.ROOT,
                    "%s dmgToPlayers=%.2f dmgToMobs=%.2f playerDeaths=%d mobDeaths=%d healToPlayers=%.2f",
                    room, agg.damageToPlayers, agg.damageToMobs,
                    agg.playerDeaths, agg.mobDeaths, agg.healToPlayers));
        });
        return lines;
    }

    /**
     * Ottiene l'aggregato per una stanza specifica.
     */
    public Optional<RoomAggregate> getRoomStats(String room) {
        return Optional.ofNullable(roomAggregates.get(room));
    }

    // ============================================
    // MINION DAMAGE TRACKING
    // ============================================

    /**
     * Registra danno fatto da un minion.
     */
    public void registerMinionDamage(Mob mob, double damage) {
        minionDamage.computeIfAbsent(mob.getUUID(), k -> new MinionDamageStats())
                .register(damage);
    }

    /**
     * Ottiene e rimuove le statistiche di un minion alla sua morte.
     */
    public Optional<MinionDamageStats> removeMinionStats(UUID minionId) {
        return Optional.ofNullable(minionDamage.remove(minionId));
    }

    // ============================================
    // CLEANUP
    // ============================================

    /**
     * Cleans up all data for a removed entity.
     */
    public void cleanupEntity(UUID entityId) {
        minionDamage.remove(entityId);
    }

    /**
     * Resets all statistics.
     */
    public void clearAll() {
        weaponAggregates.clear();
        roomAggregates.clear();
        minionDamage.clear();
    }

    // ============================================
    // DATA CLASSES
    // ============================================

    /**
     * Aggregato statistiche per arma.
     */
    public static class WeaponAggregate {
        private double totalDamage;
        private int hits;
        private int kills;
        private int misses;

        public void registerHit(double damage, boolean isKill) {
            totalDamage += damage;
            hits++;
            if (isKill) kills++;
        }

        public void registerMiss() {
            misses++;
        }

        public double getTotalDamage() { return totalDamage; }
        public int getHits() { return hits; }
        public int getKills() { return kills; }
        public int getMisses() { return misses; }

        public double getAverageHitDamage() {
            return hits > 0 ? totalDamage / hits : 0;
        }

        public double getAccuracy() {
            int total = hits + misses;
            return total > 0 ? (double) hits / total : 1.0;
        }

        @Override
        public String toString() {
            return String.format("WeaponAggregate{dmg=%.2f, hits=%d, kills=%d, miss=%d, acc=%.1f%%}",
                    totalDamage, hits, kills, misses, getAccuracy() * 100);
        }
    }

    /**
     * Aggregato statistiche per stanza.
     */
    public static class RoomAggregate {
        private double damageToPlayers;
        private double damageToMobs;
        private int playerDeaths;
        private int mobDeaths;
        private double healToPlayers;
        private double healToMobs;

        public void registerDamage(LivingEntity target, double damage, boolean isKill) {
            boolean isPlayer = target instanceof ServerPlayer;
            if (isPlayer) {
                damageToPlayers += damage;
                if (isKill) playerDeaths++;
            } else {
                damageToMobs += damage;
                if (isKill) mobDeaths++;
            }
        }

        public void registerHeal(LivingEntity target, double heal) {
            if (target instanceof ServerPlayer) {
                healToPlayers += heal;
            } else {
                healToMobs += heal;
            }
        }

        public double getDamageToPlayers() { return damageToPlayers; }
        public double getDamageToMobs() { return damageToMobs; }
        public int getPlayerDeaths() { return playerDeaths; }
        public int getMobDeaths() { return mobDeaths; }
        public double getHealToPlayers() { return healToPlayers; }
        public double getHealToMobs() { return healToMobs; }

        @Override
        public String toString() {
            return String.format("RoomAggregate{dmgPlayers=%.2f, dmgMobs=%.2f, deaths=%d/%d}",
                    damageToPlayers, damageToMobs, playerDeaths, mobDeaths);
        }
    }

    /**
     * Statistiche danno per un singolo minion.
     */
    public static class MinionDamageStats {
        private double totalDamage;
        private int hits;

        public void register(double damage) {
            totalDamage += damage;
            hits++;
        }

        public double getTotalDamage() { return totalDamage; }
        public int getHits() { return hits; }

        @Override
        public String toString() {
            return String.format("MinionDamageStats{dmg=%.2f, hits=%d}", totalDamage, hits);
        }
    }

    // ============================================
    // PERSISTENCE
    // ============================================

    /**
     * Inizializza il servizio con una directory per i dati.
     */
    public void initialize(Path telemetryDir) {
        this.dataDirectory = telemetryDir;
        loadAggregates();
    }

    /**
     * Carica gli aggregati da disco.
     */
    public void loadAggregates() {
        if (dataDirectory == null) return;

        Path file = dataDirectory.resolve("damage_aggregates.json");
        if (!Files.exists(file)) {
            LOGGER.info("[DamageTracking] No saved aggregates found, starting fresh");
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            Type type = new TypeToken<SavedAggregates>() {}.getType();
            SavedAggregates saved = GSON.fromJson(reader, type);

            if (saved != null) {
                // Restore weapon aggregates
                if (saved.weapons != null) {
                    saved.weapons.forEach((itemId, agg) -> {
                        ResourceLocation resLoc = ResourceLocation.tryParse(Objects.requireNonNull(itemId));
                        if (resLoc != null && BuiltInRegistries.ITEM.containsKey(resLoc)) {
                            Item item = Objects.requireNonNull(BuiltInRegistries.ITEM.get(resLoc));
                            weaponAggregates.put(item, agg);
                        }
                    });
                }

                // Restore room aggregates
                if (saved.rooms != null) {
                    roomAggregates.putAll(saved.rooms);
                }

                LOGGER.info("[DamageTracking] Loaded {} weapon and {} room aggregates",
                        weaponAggregates.size(), roomAggregates.size());
            }
        } catch (IOException e) {
            LOGGER.error("[DamageTracking] Failed to load aggregates", e);
        }
    }

    /**
     * Salva gli aggregati su disco.
     */
    public void saveAggregates() {
        if (dataDirectory == null) return;

        Path file = dataDirectory.resolve("damage_aggregates.json");

        try {
            Files.createDirectories(dataDirectory);

            SavedAggregates toSave = new SavedAggregates();

            // Convert weapon aggregates to serializable format
            toSave.weapons = new ConcurrentHashMap<>();
            weaponAggregates.forEach((item, agg) -> {
                ResourceLocation key = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item)));
                toSave.weapons.put(Objects.requireNonNull(key.toString()), agg);
            });

            toSave.rooms = new ConcurrentHashMap<>(roomAggregates);

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(toSave, writer);
                LOGGER.debug("[DamageTracking] Saved {} weapon and {} room aggregates",
                        toSave.weapons.size(), toSave.rooms.size());
            }
        } catch (IOException e) {
            LOGGER.error("[DamageTracking] Failed to save aggregates", e);
        }
    }

    /**
     * Classe container per la serializzazione degli aggregati.
     */
    private static class SavedAggregates {
        Map<String, WeaponAggregate> weapons;
        Map<String, RoomAggregate> rooms;
    }
}
