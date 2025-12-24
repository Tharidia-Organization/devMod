package com.devmod.testing.stats;

import com.google.gson.JsonObject;

/**
 * Tracks combat event statistics (evasions, blocks, parries, etc.).
 * Extracted from TesterProgress for single responsibility.
 */
public class CombatEventStatistics {
    public static final CombatEventStatistics INSTANCE = new CombatEventStatistics();

    private int evasionsWitnessed = 0;  // Enderman teleports, etc.
    private int blockedAttacks = 0;      // Shield blocks
    private int parriedAttacks = 0;
    private int perfectDodges = 0;
    private int combosPerformed = 0;
    private int maxComboLength = 0;

    private CombatEventStatistics() {}

    /**
     * Record witnessing an evasion (e.g., Enderman teleport).
     */
    public void recordEvasionWitnessed() {
        evasionsWitnessed++;
    }

    /**
     * Record a blocked attack (shield).
     */
    public void recordAttackBlocked() {
        blockedAttacks++;
    }

    /**
     * Record a parried attack.
     */
    public void recordAttackParried() {
        parriedAttacks++;
    }

    /**
     * Record a perfect dodge.
     */
    public void recordPerfectDodge() {
        perfectDodges++;
    }

    /**
     * Record a combo performed with given length.
     */
    public void recordCombo(int length) {
        combosPerformed++;
        if (length > maxComboLength) {
            maxComboLength = length;
        }
    }

    // === Getters ===
    public int getEvasionsWitnessed() { return evasionsWitnessed; }
    public int getBlockedAttacks() { return blockedAttacks; }
    public int getParriedAttacks() { return parriedAttacks; }
    public int getPerfectDodges() { return perfectDodges; }
    public int getCombosPerformed() { return combosPerformed; }
    public int getMaxComboLength() { return maxComboLength; }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("evasions", evasionsWitnessed);
        json.addProperty("blocked", blockedAttacks);
        json.addProperty("parried", parriedAttacks);
        json.addProperty("perfectDodges", perfectDodges);
        json.addProperty("combos", combosPerformed);
        json.addProperty("maxCombo", maxComboLength);
        return json;
    }

    public void fromJson(JsonObject json) {
        evasionsWitnessed = getInt(json, "evasions", 0);
        blockedAttacks = getInt(json, "blocked", 0);
        parriedAttacks = getInt(json, "parried", 0);
        perfectDodges = getInt(json, "perfectDodges", 0);
        combosPerformed = getInt(json, "combos", 0);
        maxComboLength = getInt(json, "maxCombo", 0);
    }

    public void reset() {
        evasionsWitnessed = 0;
        blockedAttacks = 0;
        parriedAttacks = 0;
        perfectDodges = 0;
        combosPerformed = 0;
        maxComboLength = 0;
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }
}
