package com.devmod.endurance.nemesis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public class NemesisProfile {

    private static final Logger LOGGER = LoggerFactory.getLogger(NemesisProfile.class);

    // NBT keys
    private static final String NBT_BOSS_ID = "BossId";
    private static final String NBT_DEFEAT_COUNT = "DefeatCount";
    private static final String NBT_TOTAL_FIGHTS = "TotalFights";
    private static final String NBT_RANGED_HITS = "RangedHits";
    private static final String NBT_MELEE_HITS = "MeleeHits";
    private static final String NBT_HEADSHOTS = "Headshots";
    private static final String NBT_TOTAL_HITS = "TotalHits";
    private static final String NBT_DODGE_LEFT = "DodgeLeft";
    private static final String NBT_DODGE_RIGHT = "DodgeRight";
    private static final String NBT_DODGE_BACK = "DodgeBack";
    private static final String NBT_TOTAL_DODGES = "TotalDodges";
    private static final String NBT_PHASE_TIMES = "PhaseTimes";
    private static final String NBT_WEAPON_TYPES = "WeaponTypes";
    private static final String NBT_ADAPTATIONS = "Adaptations";
    private static final String NBT_LAST_DEFEAT_TIME = "LastDefeatTime";
    private static final String DEFAULT_WEAPON_TYPE = "unknown";

    // Boss identity
    private ResourceLocation bossId;

    // Encounter statistics
    private int defeatCount = 0;
    private int totalFights = 0;

    // Combat behavior tracking
    private int rangedHits = 0;
    private int meleeHits = 0;
    private int headshots = 0;
    private int totalHits = 0;

    // Dodge pattern tracking
    private int dodgeLeft = 0;
    private int dodgeRight = 0;
    private int dodgeBack = 0;
    private int totalDodges = 0;

    // Phase kill times (average per phase)
    private final Map<Integer, List<Long>> phaseKillTimes = new HashMap<>();

    // Weapon type usage (type -> hit count)
    private final Map<String, Integer> weaponTypeUsage = new HashMap<>();

    // Unlocked adaptations
    private final Set<NemesisAdaptation> adaptations = new LinkedHashSet<>();

    // Timing
    private long lastDefeatTime = 0;

    public NemesisProfile() {}

    public NemesisProfile(ResourceLocation bossId) {
        this.bossId = bossId;
    }

    // ========== Combat Recording ==========

    /**
     * Record a hit from the player.
     */
    public void recordHit(boolean isRanged, boolean isHeadshot, String weaponType) {
        totalHits++;
        if (isRanged) {
            rangedHits++;
        } else {
            meleeHits++;
        }
        if (isHeadshot) {
            headshots++;
        }

        // Track weapon type
        String safeWeaponType = normalizeWeaponType(weaponType);
        Integer currentCount = weaponTypeUsage.get(safeWeaponType);
        weaponTypeUsage.put(safeWeaponType, currentCount == null ? 1 : currentCount + 1);
    }

    /**
     * Record a dodge by the player.
     */
    public void recordDodge(DodgeDirection direction) {
        if (direction == null) {
            return;
        }
        totalDodges++;
        switch (direction) {
            case LEFT -> dodgeLeft++;
            case RIGHT -> dodgeRight++;
            case BACK -> dodgeBack++;
        }
    }

    /**
     * Record phase completion time.
     */
    public void recordPhaseTime(int phase, long timeMs) {
        phaseKillTimes.computeIfAbsent(phase, k -> new ArrayList<>()).add(timeMs);
    }

    /**
     * Record a defeat.
     */
    public void recordDefeat() {
        defeatCount++;
        totalFights++;
        lastDefeatTime = System.currentTimeMillis();

        // Calculate new adaptations after defeat
        recalculateAdaptations();

        LOGGER.info("[Nemesis] Boss {} defeated {} times, now has {} adaptations",
            bossId, defeatCount, adaptations.size());
    }

    /**
     * Record a fight where the boss won (player died).
     */
    public void recordVictory() {
        totalFights++;
    }

    // ========== Adaptation Calculation ==========

    /**
     * Recalculate adaptations based on accumulated data.
     */
    public void recalculateAdaptations() {
        // Clear and rebuild based on current stats
        adaptations.clear();

        // Check ranged ratio - if player uses 70%+ ranged, gain deflection
        if (totalHits >= 50 && getRangedRatio() >= 0.70f) {
            adaptations.add(NemesisAdaptation.PROJECTILE_DEFLECTION);
        }

        // Check dominant dodge direction - if player always dodges one way
        if (totalDodges >= 20) {
            float maxDodgeRatio = Math.max(getLeftDodgeRatio(),
                Math.max(getRightDodgeRatio(), getBackDodgeRatio()));
            if (maxDodgeRatio >= 0.60f) {
                adaptations.add(NemesisAdaptation.SWEEPING_BLADE);
            }
        }

        // Check fast phase kills - average under 30 seconds per phase
        if (!phaseKillTimes.isEmpty()) {
            long avgPhaseTime = getAveragePhaseTime();
            if (avgPhaseTime > 0 && avgPhaseTime < 30_000) {
                adaptations.add(NemesisAdaptation.EARLY_PHASE_ACTIVATION);
            }
        }

        // Check weapon type dominance - if one type is 60%+ of hits
        String dominantWeapon = getDominantWeaponType();
        if (dominantWeapon != null && getWeaponTypeRatio(dominantWeapon) >= 0.60f) {
            adaptations.add(NemesisAdaptation.DAMAGE_RESISTANCE);
        }

        // Check headshot ratio - if 30%+ of hits are headshots
        if (totalHits >= 30 && getHeadshotRatio() >= 0.30f) {
            adaptations.add(NemesisAdaptation.PROTECTIVE_HELMET);
        }

        // Additional adaptations based on defeat count
        if (defeatCount >= 3) {
            adaptations.add(NemesisAdaptation.IMPROVED_REFLEXES);
        }
        if (defeatCount >= 5) {
            adaptations.add(NemesisAdaptation.ENRAGED);
        }
        if (defeatCount >= 10) {
            adaptations.add(NemesisAdaptation.VETERAN);
        }
    }

    // ========== Ratio Calculations ==========

    public float getRangedRatio() {
        return totalHits > 0 ? (float) rangedHits / totalHits : 0f;
    }

    public float getMeleeRatio() {
        return totalHits > 0 ? (float) meleeHits / totalHits : 0f;
    }

    public float getHeadshotRatio() {
        return totalHits > 0 ? (float) headshots / totalHits : 0f;
    }

    public float getLeftDodgeRatio() {
        return totalDodges > 0 ? (float) dodgeLeft / totalDodges : 0f;
    }

    public float getRightDodgeRatio() {
        return totalDodges > 0 ? (float) dodgeRight / totalDodges : 0f;
    }

    public float getBackDodgeRatio() {
        return totalDodges > 0 ? (float) dodgeBack / totalDodges : 0f;
    }

    public long getAveragePhaseTime() {
        long totalTime = 0;
        int count = 0;
        for (var times : phaseKillTimes.values()) {
            for (long time : times) {
                totalTime += time;
                count++;
            }
        }
        return count > 0 ? totalTime / count : 0;
    }

    public String getDominantWeaponType() {
        String dominant = null;
        int maxCount = 0;
        for (var entry : weaponTypeUsage.entrySet()) {
            String key = entry.getKey();
            Integer count = entry.getValue();
            if (isBlank(key) || count == null) {
                continue;
            }
            if (count > maxCount) {
                maxCount = count;
                dominant = key;
            }
        }
        return dominant;
    }

    public float getWeaponTypeRatio(String type) {
        String normalized = normalizeWeaponType(type);
        Integer typeCountValue = weaponTypeUsage.get(normalized);
        int typeCount = typeCountValue == null ? 0 : typeCountValue;
        int total = 0;
        for (Integer value : weaponTypeUsage.values()) {
            if (value != null) {
                total += value;
            }
        }
        return total > 0 ? (float) typeCount / total : 0f;
    }

    // ========== Visual Scarring ==========

    /**
     * Get the visual scar level based on defeat count.
     */
    public ScarLevel getScarLevel() {
        if (defeatCount >= 5) {
            return ScarLevel.BATTLE_WORN;
        } else if (defeatCount >= 3) {
            return ScarLevel.MULTIPLE_SCARS;
        } else if (defeatCount >= 1) {
            return ScarLevel.SMALL_SCAR;
        }
        return ScarLevel.NONE;
    }

    public enum ScarLevel {
        NONE(0),
        SMALL_SCAR(1),
        MULTIPLE_SCARS(2),
        BATTLE_WORN(3);

        public final int level;

        ScarLevel(int level) {
            this.level = level;
        }
    }

    public enum DodgeDirection {
        LEFT, RIGHT, BACK
    }

    // ========== NBT Persistence ==========

    /**
     * Save profile to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        if (bossId != null) {
            tag.putString(NBT_BOSS_ID, bossId.toString());
        }
        tag.putInt(NBT_DEFEAT_COUNT, defeatCount);
        tag.putInt(NBT_TOTAL_FIGHTS, totalFights);
        tag.putInt(NBT_RANGED_HITS, rangedHits);
        tag.putInt(NBT_MELEE_HITS, meleeHits);
        tag.putInt(NBT_HEADSHOTS, headshots);
        tag.putInt(NBT_TOTAL_HITS, totalHits);
        tag.putInt(NBT_DODGE_LEFT, dodgeLeft);
        tag.putInt(NBT_DODGE_RIGHT, dodgeRight);
        tag.putInt(NBT_DODGE_BACK, dodgeBack);
        tag.putInt(NBT_TOTAL_DODGES, totalDodges);
        tag.putLong(NBT_LAST_DEFEAT_TIME, lastDefeatTime);

        // Save weapon types
        CompoundTag weaponTag = new CompoundTag();
        for (var entry : weaponTypeUsage.entrySet()) {
            String key = entry.getKey();
            if (isBlank(key)) {
                continue;
            }
            Integer value = entry.getValue();
            weaponTag.putInt(key, value == null ? 0 : value);
        }
        tag.put(NBT_WEAPON_TYPES, weaponTag);

        // Save phase times
        CompoundTag phaseTag = new CompoundTag();
        for (var entry : phaseKillTimes.entrySet()) {
            Integer phase = entry.getKey();
            List<Long> times = entry.getValue();
            if (phase == null || times == null || times.isEmpty()) {
                continue;
            }
            ListTag timesList = new ListTag();
            for (Long time : times) {
                if (time != null) {
                    timesList.add(LongTag.valueOf(time));
                }
            }
            if (!timesList.isEmpty()) {
                String phaseKey = Integer.toString(phase);
                phaseTag.put(phaseKey, timesList);
            }
        }
        tag.put(NBT_PHASE_TIMES, phaseTag);

        // Save adaptations
        ListTag adaptationsList = new ListTag();
        for (NemesisAdaptation adaptation : adaptations) {
            if (adaptation != null) {
                adaptationsList.add(StringTag.valueOf(adaptation.name()));
            }
        }
        tag.put(NBT_ADAPTATIONS, adaptationsList);

        return tag;
    }

    /**
     * Load profile from NBT.
     */
    public static NemesisProfile load(CompoundTag tag) {
        NemesisProfile profile = new NemesisProfile();

        if (tag.contains(NBT_BOSS_ID)) {
            String bossIdStr = tag.getString(NBT_BOSS_ID);
            if (!bossIdStr.isEmpty()) {
                profile.bossId = ResourceLocation.tryParse(bossIdStr);
            }
        }
        profile.defeatCount = tag.getInt(NBT_DEFEAT_COUNT);
        profile.totalFights = tag.getInt(NBT_TOTAL_FIGHTS);
        profile.rangedHits = tag.getInt(NBT_RANGED_HITS);
        profile.meleeHits = tag.getInt(NBT_MELEE_HITS);
        profile.headshots = tag.getInt(NBT_HEADSHOTS);
        profile.totalHits = tag.getInt(NBT_TOTAL_HITS);
        profile.dodgeLeft = tag.getInt(NBT_DODGE_LEFT);
        profile.dodgeRight = tag.getInt(NBT_DODGE_RIGHT);
        profile.dodgeBack = tag.getInt(NBT_DODGE_BACK);
        profile.totalDodges = tag.getInt(NBT_TOTAL_DODGES);
        profile.lastDefeatTime = tag.getLong(NBT_LAST_DEFEAT_TIME);

        // Load weapon types
        if (tag.contains(NBT_WEAPON_TYPES)) {
            CompoundTag weaponTag = tag.getCompound(NBT_WEAPON_TYPES);
            for (String key : weaponTag.getAllKeys()) {
                if (isBlank(key)) {
                    continue;
                }
                profile.weaponTypeUsage.put(key, weaponTag.getInt(key));
            }
        }

        // Load phase times
        if (tag.contains(NBT_PHASE_TIMES, Tag.TAG_COMPOUND)) {
            CompoundTag phaseTag = tag.getCompound(NBT_PHASE_TIMES);
            for (String key : phaseTag.getAllKeys()) {
                if (isBlank(key)) {
                    continue;
                }
                try {
                    int phase = Integer.parseInt(key);
                    ListTag timesList = phaseTag.getList(key, Tag.TAG_LONG);
                    List<Long> times = new ArrayList<>(timesList.size());
                    for (int i = 0; i < timesList.size(); i++) {
                        Tag timeTag = timesList.get(i);
                        if (timeTag instanceof LongTag longTag) {
                            times.add(longTag.getAsLong());
                        }
                    }
                    if (!times.isEmpty()) {
                        profile.phaseKillTimes.put(phase, times);
                    }
                } catch (NumberFormatException ignored) {
                    // Skip invalid phase keys
                }
            }
        }

        // Load adaptations
        if (tag.contains(NBT_ADAPTATIONS)) {
            ListTag adaptationsList = tag.getList(NBT_ADAPTATIONS, Tag.TAG_STRING);
            for (int i = 0; i < adaptationsList.size(); i++) {
                String name = adaptationsList.getString(i);
                if (isBlank(name)) {
                    continue;
                }
                try {
                    profile.adaptations.add(NemesisAdaptation.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    // Unknown adaptation, skip
                }
            }
        }

        return profile;
    }

    // ========== Getters ==========

    public ResourceLocation getBossId() { return bossId; }
    public int getDefeatCount() { return defeatCount; }
    public int getTotalFights() { return totalFights; }
    public Set<NemesisAdaptation> getAdaptations() { return Collections.unmodifiableSet(adaptations); }
    public boolean hasAdaptation(NemesisAdaptation adaptation) { return adaptations.contains(adaptation); }
    public long getLastDefeatTime() { return lastDefeatTime; }

    private static String normalizeWeaponType(String weaponType) {
        if (isBlank(weaponType)) {
            return DEFAULT_WEAPON_TYPE;
        }
        return weaponType.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
