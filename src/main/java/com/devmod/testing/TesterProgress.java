package com.devmod.testing;

import com.devmod.testing.stats.AchievementTracker;
import com.devmod.testing.stats.CombatEventStatistics;
import com.devmod.testing.stats.DamageStatistics;
import com.devmod.testing.stats.EnchantmentStatistics;
import com.devmod.testing.stats.EnvironmentalDamageStats;
import com.devmod.testing.stats.ExplosionStatistics;
import com.devmod.testing.stats.KillStatistics;
import com.devmod.testing.stats.ModInteractionTracker;
import com.devmod.testing.stats.OverlayUsageTracker;
import com.devmod.testing.stats.PotionStatistics;
import com.devmod.testing.stats.SessionStatistics;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.util.ConfigPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Facade for all tester gameplay statistics.
 * Delegates to specialized statistics services for single responsibility.
 *
 * Statistics are persisted to disk and survive game restarts.
 */
public class TesterProgress {
    private static final Logger LOGGER = LoggerFactory.getLogger(TesterProgress.class);
    // Lazy initialization to avoid NPE during class loading (FMLPaths not ready yet)
    private static Path getProgressFile() {
        return ConfigPaths.getTesterProgressFile();
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final TesterProgress INSTANCE = new TesterProgress();

    // Delegate services
    private final KillStatistics kills = KillStatistics.INSTANCE;
    private final DamageStatistics damage = DamageStatistics.INSTANCE;
    private final EnvironmentalDamageStats environmental = EnvironmentalDamageStats.INSTANCE;
    private final ExplosionStatistics explosions = ExplosionStatistics.INSTANCE;
    private final PotionStatistics potions = PotionStatistics.INSTANCE;
    private final EnchantmentStatistics enchantments = EnchantmentStatistics.INSTANCE;
    private final ModInteractionTracker mods = ModInteractionTracker.INSTANCE;
    private final CombatEventStatistics combat = CombatEventStatistics.INSTANCE;
    private final OverlayUsageTracker overlays = OverlayUsageTracker.INSTANCE;
    private final SessionStatistics session = SessionStatistics.INSTANCE;
    private final AchievementTracker achievements = AchievementTracker.INSTANCE;

    private TesterProgress() {
        load();
    }

    // ==================== RECORDING METHODS ====================

    /**
     * Record a mob kill with all relevant context.
     */
    public void onMobKill(EntityType<?> mobType, Item weapon, String bodyPart,
                          boolean isCritical, boolean isHeadshot, boolean isMaceSmash,
                          boolean isArrow, double damage) {
        // Delegate to kill statistics
        boolean firstKill = kills.recordKill(mobType, weapon, bodyPart, isCritical,
                                             isHeadshot, isMaceSmash, isArrow);

        // Delegate to mod interaction tracker
        mods.recordModKill(mobType, weapon);

        // Handle achievements
        if (firstKill) {
            achievements.unlockFirstBlood();
        }
        if (isHeadshot) {
            achievements.unlockFirstHeadshot();
        }
        if (isMaceSmash) {
            achievements.unlockFirstMaceSmash();
        }
        if (kills.hasKilledWithAllBodyParts()) {
            achievements.unlockBodyPartMaster();
        }

        saveDirty();
    }

    /**
     * Record damage dealt to an entity.
     */
    public void onDamageDealt(double damageAmount, String bodyPart, Item weapon, boolean isCritical) {
        damage.recordDamageDealt(damageAmount, bodyPart, weapon);
        saveDirty();
    }

    /**
     * Record damage taken by the player.
     */
    public void onDamageTaken(double damageAmount, DamageSource source) {
        damage.recordDamageTaken(damageAmount);

        // Record environmental damage and check for explosion survival achievement
        boolean firstExplosionSurvived = environmental.recordEnvironmentalDamage(damageAmount, source);
        if (firstExplosionSurvived) {
            achievements.unlockSurvivedExplosion();
        }

        saveDirty();
    }

    /**
     * Record an explosion event.
     */
    public void onExplosion(boolean isTNT, boolean isCreeper, int mobsKilled) {
        explosions.recordExplosion(isTNT, isCreeper, mobsKilled);
        saveDirty();
    }

    /**
     * Record potion usage.
     */
    public void onPotionUsed(String effectType, boolean isSplash, boolean isLingering) {
        potions.recordPotionUsed(effectType, isSplash, isLingering);
        saveDirty();
    }

    /**
     * Record gaining a potion effect.
     */
    public void onEffectGained(String effectType) {
        potions.recordEffectGained(effectType);
        mods.recordModEffect(effectType);
        saveDirty();
    }

    /**
     * Record an enchantment-related kill.
     */
    public void onEnchantedKill(String enchantmentType) {
        enchantments.recordEnchantedKill(enchantmentType);
        saveDirty();
    }

    /**
     * Record witnessing an evasion (e.g., Enderman teleport).
     */
    public void onEvasionWitnessed() {
        combat.recordEvasionWitnessed();
        saveDirty();
    }

    /**
     * Record a blocked attack (shield).
     */
    public void onAttackBlocked() {
        combat.recordAttackBlocked();
        saveDirty();
    }

    /**
     * Record overlay toggle.
     */
    public void onOverlayToggle(String overlayName) {
        boolean allUsed = overlays.recordOverlayToggle(overlayName);
        if (allUsed) {
            achievements.unlockOverlayMaster();
        }
        saveDirty();
    }

    /**
     * Record screen opens.
     */
    public void onScreenOpen(String screenName) {
        overlays.recordScreenOpen(screenName);
        saveDirty();
    }

    /**
     * Record session start.
     */
    public void onSessionStart() {
        session.recordSessionStart();
        saveDirty();
    }

    /**
     * Record test completion.
     */
    public void onTestCompleted(boolean wasAutomatic) {
        session.recordTestCompleted(wasAutomatic);
        saveDirty();
    }

    /**
     * Update play time (call periodically).
     */
    public void updatePlayTime() {
        if (session.updatePlayTime()) {
            saveDirty();
        }
    }

    // ==================== PROGRESS QUERY METHODS ====================

    // Kill statistics
    public int getTotalKills() { return kills.getTotalKills(); }
    public int getKillsOfType(String mobType) { return kills.getKillsOfType(mobType); }
    public int getKillsWithWeapon(String weapon) { return kills.getKillsWithWeapon(weapon); }
    public int getKillsOnBodyPart(String part) { return kills.getKillsOnBodyPart(part); }
    public int getHeadshots() { return kills.getHeadshots(); }
    public int getCriticalHits() { return kills.getCriticalHits(); }
    public int getMaceSmashKills() { return kills.getMaceSmashKills(); }
    public int getArrowKills() { return kills.getArrowKills(); }
    public int getMeleeKills() { return kills.getMeleeKills(); }
    public int getCurrentKillStreak() { return kills.getCurrentKillStreak(); }
    public int getMaxKillStreak() { return kills.getMaxKillStreak(); }

    // Damage statistics
    public double getTotalDamageDealt() { return damage.getTotalDamageDealt(); }
    public double getTotalDamageTaken() { return damage.getTotalDamageTaken(); }
    public int getHitsDealt() { return damage.getHitsDealt(); }
    public double getHighestSingleHit() { return damage.getHighestSingleHit(); }
    public double getDamageOnBodyPart(String part) { return damage.getDamageOnBodyPart(part); }
    public int getHitsOnBodyPart(String part) { return damage.getHitsOnBodyPart(part); }

    // Environmental damage
    public double getFallDamageTaken() { return environmental.getFallDamageTaken(); }
    public double getFireDamageTaken() { return environmental.getFireDamageTaken(); }
    public double getLavaDamageTaken() { return environmental.getLavaDamageTaken(); }
    public double getExplosionDamageTaken() { return environmental.getExplosionDamageTaken(); }
    public double getPoisonDamageTaken() { return environmental.getPoisonDamageTaken(); }

    // Explosions
    public int getExplosionsCaused() { return explosions.getExplosionsCaused(); }
    public int getTntDetonated() { return explosions.getTntDetonated(); }
    public int getCreeperExplosions() { return explosions.getCreeperExplosions(); }
    public int getMobsKilledByExplosion() { return explosions.getMobsKilledByExplosion(); }

    // Potions
    public int getPotionsUsed() { return potions.getPotionsUsed(); }
    public int getPotionEffectsGained() { return potions.getPotionEffectsGained(); }
    public int getEffectUsageCount(String effectType) { return potions.getEffectUsageCount(effectType); }
    public int getUniqueEffectsUsed() { return potions.getUniqueEffectsUsed(); }

    // Enchantments
    public int getEnchantedWeaponKills() { return enchantments.getEnchantedWeaponKills(); }
    public int getUniqueEnchantmentsUsed() { return enchantments.getUniqueEnchantmentsUsed(); }

    // Combat
    public int getEvasionsWitnessed() { return combat.getEvasionsWitnessed(); }
    public int getBlockedAttacks() { return combat.getBlockedAttacks(); }

    // Overlays
    public int getOverlayToggles(String overlayName) { return overlays.getOverlayToggles(overlayName); }
    public int getTotalOverlayToggles() { return overlays.getTotalOverlayToggles(); }
    public int getScreenOpens(String screenName) { return overlays.getScreenOpens(screenName); }

    // Session
    public long getPlayTimeMs() { return session.getPlayTimeMs(); }
    public int getSessionsStarted() { return session.getSessionsStarted(); }
    public int getTestsCompleted() { return session.getTestsCompleted(); }
    public int getTestsAutoCompleted() { return session.getTestsAutoCompleted(); }

    // Achievements
    public boolean hasFirstBlood() { return achievements.hasFirstBlood(); }
    public boolean hasFirstHeadshot() { return achievements.hasFirstHeadshot(); }
    public boolean hasFirstMaceSmash() { return achievements.hasFirstMaceSmash(); }
    public boolean hasSurvivedExplosion() { return achievements.hasSurvivedExplosion(); }
    public boolean hasKilledWithEveryBodyPart() { return achievements.hasKilledWithEveryBodyPart(); }
    public boolean hasUsedAllOverlays() { return achievements.hasUsedAllOverlays(); }

    // Mod interaction
    public int getKillsWithModWeapon(String modId) { return mods.getKillsWithModWeapon(modId); }
    public int getWeaponUsageFromMod(String modId) { return mods.getWeaponUsageFromMod(modId); }
    public int getMobsKilledFromMod(String modId) { return mods.getMobsKilledFromMod(modId); }
    public int getEffectsUsedFromMod(String modId) { return mods.getEffectsUsedFromMod(modId); }
    public int getArmorUsageFromMod(String modId) { return mods.getArmorUsageFromMod(modId); }
    public boolean hasInteractedWithMod(String modId) { return mods.hasInteractedWithMod(modId); }
    public Set<String> getModsInteractedWith() { return mods.getModsInteractedWith(); }
    public int getUniqueModsInteractedCount() { return mods.getUniqueModsInteractedCount(); }
    public int getTotalModdedKills() { return mods.getTotalModdedKills(); }
    public int getTotalModdedMobsKilled() { return mods.getTotalModdedMobsKilled(); }

    // ==================== HELPER METHODS ====================

    /**
     * Reset all progress (for new testing session).
     */
    public void reset() {
        kills.reset();
        damage.reset();
        environmental.reset();
        explosions.reset();
        potions.reset();
        enchantments.reset();
        mods.reset();
        combat.reset();
        overlays.reset();
        session.reset();
        achievements.reset();
        save();
    }

    // ==================== PERSISTENCE ====================

    private boolean dirty = false;

    private void saveDirty() {
        dirty = true;
    }

    /**
     * Flush pending changes to disk (call periodically).
     */
    public void flush() {
        if (dirty) {
            save();
            dirty = false;
        }
    }

    public void save() {
        try {
            Files.createDirectories(getProgressFile().getParent());

            JsonObject root = new JsonObject();
            root.add("kills", kills.toJson());
            root.add("damage", damage.toJson());
            root.add("environmental", environmental.toJson());
            root.add("explosions", explosions.toJson());
            root.add("potions", potions.toJson());
            root.add("enchantments", enchantments.toJson());
            root.add("modTracking", mods.toJson());
            root.add("combat", combat.toJson());
            root.add("overlays", overlays.toJson());
            root.add("session", session.toJson());
            root.add("achievements", achievements.toJson());

            Files.writeString(getProgressFile(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save tester progress: {}", e.getMessage());
        }
    }

    public void load() {
        if (!Files.exists(getProgressFile())) {
            return;
        }

        try {
            String content = Files.readString(getProgressFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            if (root.has("kills")) kills.fromJson(root.getAsJsonObject("kills"));
            if (root.has("damage")) damage.fromJson(root.getAsJsonObject("damage"));
            if (root.has("environmental")) environmental.fromJson(root.getAsJsonObject("environmental"));
            if (root.has("explosions")) explosions.fromJson(root.getAsJsonObject("explosions"));
            if (root.has("potions")) potions.fromJson(root.getAsJsonObject("potions"));
            if (root.has("enchantments")) enchantments.fromJson(root.getAsJsonObject("enchantments"));
            if (root.has("modTracking")) mods.fromJson(root.getAsJsonObject("modTracking"));
            if (root.has("combat")) combat.fromJson(root.getAsJsonObject("combat"));
            if (root.has("overlays")) overlays.fromJson(root.getAsJsonObject("overlays"));
            if (root.has("session")) session.fromJson(root.getAsJsonObject("session"));
            if (root.has("achievements")) achievements.fromJson(root.getAsJsonObject("achievements"));

            LOGGER.info("Tester progress loaded: {} kills, {} tests completed, {} mods interacted",
                kills.getTotalKills(), session.getTestsCompleted(), mods.getUniqueModsInteractedCount());
        } catch (Exception e) {
            LOGGER.error("Failed to load tester progress: {}", e.getMessage());
        }
    }
}
