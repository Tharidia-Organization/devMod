package com.devmod.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.policy.ArenaPolicy.AscensionOverrides;
import com.devmod.arena.policy.ArenaPolicy.BargainOverrides;
import com.devmod.arena.policy.ArenaPolicy.ChallengeOverrides;
import com.devmod.arena.policy.ArenaPolicy.ComboOverrides;
import com.devmod.arena.policy.ArenaPolicy.ExecutionOverrides;
import com.devmod.arena.policy.ArenaPolicy.GuildOverrides;
import com.devmod.arena.policy.ArenaPolicy.HazardOverrides;
import com.devmod.arena.policy.ArenaPolicy.MomentumOverrides;
import com.devmod.arena.policy.ArenaPolicy.PerkOverrides;
import com.devmod.arena.policy.ArenaPolicy.PerkRarityOverrides;
import com.devmod.arena.policy.ArenaPolicy.RewardOverrides;
import com.devmod.arena.policy.ArenaPolicy.SeasonPassOverrides;
import com.devmod.arena.policy.ArenaPolicy.StyleRankOverrides;
import com.devmod.arena.policy.ArenaPolicy.TensionOverrides;
import com.devmod.arena.policy.ArenaPolicy.WaveOverrides;
/**
 * Runtime manager for GameplayOverrides loaded from JSON files.
 *
 * Allows modpack creators and testers to define per-arena or per-difficulty
 * gameplay parameter overrides in JSON files that can be hot-reloaded.
 *
 * Directory structure:
 *   config/devmod/overrides/
 *     ├── default.json           (fallback overrides)
 *     ├── arena_boss.json        (arena-specific)
 *     ├── difficulty_hard.json   (difficulty-specific)
 *     └── test_mode.json         (custom profile)
 *
 * Usage:
 *   GameplayOverridesManager.INSTANCE.loadAll();
 *   GameplayOverrides overrides = GameplayOverridesManager.INSTANCE.get("arena_boss");
 *
 * @see GameMechanicsConfig for global defaults
 * @see GameplayOverrides for per-instance structure
 */
public class GameplayOverridesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameplayOverridesManager.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public static final GameplayOverridesManager INSTANCE = new GameplayOverridesManager();

    private final Path overridesDir;
    private final Map<String, GameplayOverrides> loadedOverrides = new ConcurrentHashMap<>();
    private long lastReloadTime = 0;

    private GameplayOverridesManager() {
        this.overridesDir = Paths.get("config", "devmod", "overrides");
    }

    /**
     * Initializes the override directory and creates example files if empty.
     */
    public void initialize() {
        try {
            Files.createDirectories(overridesDir);

            // Create example file if directory is empty
            if (Files.list(overridesDir).findAny().isEmpty()) {
                createExampleFiles();
            }

            loadAll();
            LOGGER.info("[GameplayOverrides] Initialized with {} override profiles", loadedOverrides.size());
        } catch (IOException e) {
            LOGGER.error("[GameplayOverrides] Failed to initialize override directory", e);
        }
    }

    /**
     * Load all JSON override files from the directory.
     */
    public void loadAll() {
        loadedOverrides.clear();
        lastReloadTime = System.currentTimeMillis();

        try {
            if (!Files.exists(overridesDir)) {
                return;
            }

            Files.list(overridesDir)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadFile);

            LOGGER.info("[GameplayOverrides] Loaded {} override profiles", loadedOverrides.size());
        } catch (IOException e) {
            LOGGER.error("[GameplayOverrides] Failed to load override files", e);
        }
    }

    /**
     * Load a single override file.
     */
    private void loadFile(Path file) {
        String profileName = file.getFileName().toString().replace(".json", "");

        try {
            String json = Files.readString(file);
            GameplayOverrides overrides = parseGameplayOverrides(json);

            if (overrides != null) {
                loadedOverrides.put(profileName, overrides);
                LOGGER.debug("[GameplayOverrides] Loaded profile: {}", profileName);
            }
        } catch (Exception e) {
            LOGGER.error("[GameplayOverrides] Failed to load {}: {}", profileName, e.getMessage());
        }
    }

    /**
     * Get an override profile by name.
     */
    @Nullable
    public GameplayOverrides get(String profileName) {
        return loadedOverrides.get(profileName);
    }

    /**
     * Get override with fallback to default profile.
     */
    public GameplayOverrides getOrDefault(String profileName) {
        GameplayOverrides override = loadedOverrides.get(profileName);
        if (override != null) return override;

        override = loadedOverrides.get("default");
        if (override != null) return override;

        return GameplayOverrides.EMPTY;
    }

    /**
     * List all available profile names.
     */
    public Set<String> getProfileNames() {
        return new HashSet<>(loadedOverrides.keySet());
    }

    /**
     * Reload all override files (hot-reload support).
     */
    public void reload() {
        LOGGER.info("[GameplayOverrides] Reloading all override profiles...");
        loadAll();
    }

    /**
     * Save an override profile to disk.
     */
    public void save(String profileName, GameplayOverrides overrides) {
        Path file = overridesDir.resolve(profileName + ".json");

        try {
            // Create backup if exists
            if (Files.exists(file)) {
                Path backup = overridesDir.resolve(profileName + ".json.bak");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            String json = serializeGameplayOverrides(overrides);

            // Atomic write
            Path temp = overridesDir.resolve(profileName + ".json.tmp");
            Files.writeString(temp, json);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            loadedOverrides.put(profileName, overrides);
            LOGGER.info("[GameplayOverrides] Saved profile: {}", profileName);
        } catch (IOException e) {
            LOGGER.error("[GameplayOverrides] Failed to save {}: {}", profileName, e.getMessage());
        }
    }

    /**
     * Delete an override profile.
     */
    public boolean delete(String profileName) {
        Path file = overridesDir.resolve(profileName + ".json");

        try {
            if (Files.deleteIfExists(file)) {
                loadedOverrides.remove(profileName);
                LOGGER.info("[GameplayOverrides] Deleted profile: {}", profileName);
                return true;
            }
        } catch (IOException e) {
            LOGGER.error("[GameplayOverrides] Failed to delete {}: {}", profileName, e.getMessage());
        }
        return false;
    }

    // ============================================
    // JSON PARSING
    // ============================================

    @Nullable
    private GameplayOverrides parseGameplayOverrides(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            return new GameplayOverrides(
                parseExecutionOverrides(root.getAsJsonObject("execution")),
                parseComboOverrides(root.getAsJsonObject("combo")),
                parseStyleRankOverrides(root.getAsJsonObject("styleRank")),
                parseMomentumOverrides(root.getAsJsonObject("momentum")),
                parseBargainOverrides(root.getAsJsonObject("bargain")),
                parseHazardOverrides(root.getAsJsonObject("hazards")),
                parsePerkOverrides(root.getAsJsonObject("perks")),
                parseWaveOverrides(root.getAsJsonObject("waves")),
                parseRewardOverrides(root.getAsJsonObject("rewards")),
                parseSeasonPassOverrides(root.getAsJsonObject("seasonPass")),
                parseGuildOverrides(root.getAsJsonObject("guild")),
                parseAscensionOverrides(root.getAsJsonObject("ascension")),
                parseTensionOverrides(root.getAsJsonObject("tension")),
                parseChallengeOverrides(root.getAsJsonObject("challenges")),
                parsePerkRarityOverrides(root.getAsJsonObject("perkRarity"))
            );
        } catch (Exception e) {
            LOGGER.error("[GameplayOverrides] Parse error: {}", e.getMessage());
            return null;
        }
    }

    @Nullable
    private ExecutionOverrides parseExecutionOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new ExecutionOverrides(
            getDouble(obj, "hpThreshold"),
            getInt(obj, "durationTicks"),
            getInt(obj, "cooldownTicks"),
            getInt(obj, "styleReward"),
            getDouble(obj, "hpRegenPercent"),
            getDouble(obj, "dropBoost"),
            getDouble(obj, "interruptVulnerability"),
            getDouble(obj, "range")
        );
    }

    @Nullable
    private ComboOverrides parseComboOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new ComboOverrides(
            getInt(obj, "timeoutTicks"),
            getInt(obj, "basePoints"),
            getDouble(obj, "multiplierIncrement"),
            getDouble(obj, "maxMultiplier"),
            getInt(obj, "finisherThreshold"),
            getInt(obj, "juggleBonus"),
            getInt(obj, "headshotBonus"),
            getInt(obj, "executionBonus")
        );
    }

    @Nullable
    private StyleRankOverrides parseStyleRankOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new StyleRankOverrides(
            getInt(obj, "dThreshold"),
            getInt(obj, "cThreshold"),
            getInt(obj, "bThreshold"),
            getInt(obj, "aThreshold"),
            getInt(obj, "sThreshold"),
            getInt(obj, "ssThreshold"),
            getInt(obj, "sssThreshold"),
            getDouble(obj, "decayRate"),
            getInt(obj, "decayDelayTicks")
        );
    }

    @Nullable
    private MomentumOverrides parseMomentumOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new MomentumOverrides(
            getDouble(obj, "decayRate"),
            getDouble(obj, "killBoost"),
            getDouble(obj, "hitBoost"),
            getDouble(obj, "overdriveThreshold"),
            getInt(obj, "overdriveDurationTicks"),
            getInt(obj, "staleThresholdTicks"),
            getDouble(obj, "freshBonusMultiplier"),
            getDouble(obj, "virtuosoThreshold"),
            getInt(obj, "virtuosoDurationTicks")
        );
    }

    @Nullable
    private BargainOverrides parseBargainOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new BargainOverrides(
            getBool(obj, "enabled"),
            getInt(obj, "altarSpawnWave"),
            getInt(obj, "altarIntervalWaves"),
            getInt(obj, "maxCursesPerRun"),
            getInt(obj, "choiceTimeoutTicks"),
            getDouble(obj, "cursePowerMultiplier"),
            getDouble(obj, "boonPowerMultiplier")
        );
    }

    @Nullable
    private HazardOverrides parseHazardOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new HazardOverrides(
            getBool(obj, "enabled"),
            getInt(obj, "floorCrumbleWave"),
            getInt(obj, "bloodMoonWave"),
            getInt(obj, "arenaShrinkWave"),
            getInt(obj, "lightningStormWave"),
            getInt(obj, "voidRiftsWave"),
            getInt(obj, "floorCrumbleDuration"),
            getInt(obj, "bloodMoonDuration"),
            getInt(obj, "arenaShrinkDuration"),
            getInt(obj, "lightningStormDuration"),
            getInt(obj, "voidRiftsDuration"),
            getDouble(obj, "lightningDamage"),
            getDouble(obj, "voidRiftDamage"),
            getDouble(obj, "bloodMoonMobBuff"),
            getDouble(obj, "arenaShrinkRate")
        );
    }

    @Nullable
    private PerkOverrides parsePerkOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new PerkOverrides(
            getBool(obj, "synergyEnabled"),
            getBool(obj, "hiddenPerksEnabled"),
            getBool(obj, "sacrificeEnabled"),
            getInt(obj, "sacrificeStyleCost"),
            getInt(obj, "discoveryXpBase"),
            getDouble(obj, "synergyBonusMultiplier")
        );
    }

    @Nullable
    private WaveOverrides parseWaveOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new WaveOverrides(
            getInt(obj, "baseMobCount"),
            getDouble(obj, "mobScaling"),
            getInt(obj, "intermissionTicks"),
            getDouble(obj, "eliteChanceBase"),
            getDouble(obj, "eliteChanceScaling"),
            getInt(obj, "bossInterval")
        );
    }

    @Nullable
    private RewardOverrides parseRewardOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new RewardOverrides(
            getDouble(obj, "xpMultiplier"),
            getDouble(obj, "styleMultiplier"),
            getDouble(obj, "dropRateBonus"),
            getInt(obj, "bonusChestWaveInterval")
        );
    }

    @Nullable
    private SeasonPassOverrides parseSeasonPassOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new SeasonPassOverrides(
            getInt(obj, "maxTier"),
            getInt(obj, "xpPerTier"),
            getInt(obj, "xpPerKill"),
            getInt(obj, "xpPerWave"),
            getInt(obj, "xpPerBoss"),
            getInt(obj, "xpDailyChallenge"),
            getInt(obj, "xpWeeklyChallenge"),
            getInt(obj, "xpStyleS"),
            getInt(obj, "xpStyleSS"),
            getInt(obj, "xpStyleSSS"),
            getInt(obj, "xpFlawlessWave"),
            getInt(obj, "xpHighCombo50"),
            getInt(obj, "xpHighCombo100"),
            getDouble(obj, "xpBoostMultiplier"),
            getDouble(obj, "xpGlobalMultiplier")
        );
    }

    @Nullable
    private GuildOverrides parseGuildOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new GuildOverrides(
            getInt(obj, "maxSize"),
            getInt(obj, "maxLevel"),
            getInt(obj, "createCost"),
            getInt(obj, "xpPerLevelBase"),
            getDouble(obj, "xpScaling"),
            getInt(obj, "bankTaxPercent"),
            getInt(obj, "objectiveKillsTarget"),
            getInt(obj, "objectiveWavesTarget"),
            getInt(obj, "objectiveBossesTarget"),
            getInt(obj, "objectiveTokensTarget"),
            getDouble(obj, "bonusTokensMultiplier"),
            getDouble(obj, "bonusXpMultiplier")
        );
    }

    @Nullable
    private AscensionOverrides parseAscensionOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new AscensionOverrides(
            getInt(obj, "maxLevel"),
            getInt(obj, "minPrestige"),
            getInt(obj, "costScaling"),
            getDouble(obj, "damageBonusPerLevel"),
            getDouble(obj, "defenseBonusPerLevel"),
            getDouble(obj, "tokenMultiplierPerLevel"),
            getDouble(obj, "lifestealBonusPerLevel"),
            getDouble(obj, "critBonusPerLevel"),
            getDouble(obj, "comboDecayReductionPerLevel"),
            getInt(obj, "extraPerkSlotsPerLevel"),
            getDouble(obj, "startingHpBonusPerLevel")
        );
    }

    @Nullable
    private TensionOverrides parseTensionOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new TensionOverrides(
            getDouble(obj, "baseWaveGain"),
            getDouble(obj, "noHitBonus"),
            getDouble(obj, "minThreshold"),
            getDouble(obj, "maxThreshold"),
            getDouble(obj, "eliteKillBonus"),
            getDouble(obj, "comboBonusThreshold"),
            getDouble(obj, "styleSBonus"),
            getInt(obj, "minWavesBeforeBoss"),
            getInt(obj, "maxWavesWithoutBoss"),
            getDouble(obj, "decayAfterBoss")
        );
    }

    @Nullable
    private ChallengeOverrides parseChallengeOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new ChallengeOverrides(
            getInt(obj, "dailyCount"),
            getInt(obj, "weeklyCount"),
            getInt(obj, "dailyResetHour"),
            getInt(obj, "weeklyResetDay"),
            getInt(obj, "killsTargetMin"),
            getInt(obj, "killsTargetMax"),
            getInt(obj, "wavesTargetMin"),
            getInt(obj, "wavesTargetMax"),
            getInt(obj, "comboTargetMin"),
            getInt(obj, "comboTargetMax"),
            getInt(obj, "dailyTokenReward"),
            getInt(obj, "weeklyTokenReward"),
            getInt(obj, "dailyXpReward"),
            getInt(obj, "weeklyXpReward")
        );
    }

    @Nullable
    private PerkRarityOverrides parsePerkRarityOverrides(@Nullable JsonObject obj) {
        if (obj == null) return null;
        return new PerkRarityOverrides(
            getInt(obj, "commonWeight"),
            getInt(obj, "uncommonWeight"),
            getInt(obj, "rareWeight"),
            getInt(obj, "epicWeight"),
            getInt(obj, "legendaryWeight"),
            getInt(obj, "choicesPerSelection"),
            getInt(obj, "rerollCostBase"),
            getInt(obj, "rerollCostIncrement"),
            getDouble(obj, "damageBonusCommon"),
            getDouble(obj, "damageBonusUncommon"),
            getDouble(obj, "damageBonusRare"),
            getDouble(obj, "damageBonusEpic"),
            getDouble(obj, "damageBonusLegendary")
        );
    }

    // ============================================
    // JSON SERIALIZATION
    // ============================================

    private String serializeGameplayOverrides(GameplayOverrides overrides) {
        JsonObject root = new JsonObject();

        if (overrides.execution() != null) root.add("execution", serializeExecution(overrides.execution()));
        if (overrides.combo() != null) root.add("combo", serializeCombo(overrides.combo()));
        if (overrides.styleRank() != null) root.add("styleRank", serializeStyleRank(overrides.styleRank()));
        if (overrides.momentum() != null) root.add("momentum", serializeMomentum(overrides.momentum()));
        if (overrides.bargain() != null) root.add("bargain", serializeBargain(overrides.bargain()));
        if (overrides.hazards() != null) root.add("hazards", serializeHazards(overrides.hazards()));
        if (overrides.perks() != null) root.add("perks", serializePerks(overrides.perks()));
        if (overrides.waves() != null) root.add("waves", serializeWaves(overrides.waves()));
        if (overrides.rewards() != null) root.add("rewards", serializeRewards(overrides.rewards()));
        if (overrides.seasonPass() != null) root.add("seasonPass", serializeSeasonPass(overrides.seasonPass()));
        if (overrides.guild() != null) root.add("guild", serializeGuild(overrides.guild()));
        if (overrides.ascension() != null) root.add("ascension", serializeAscension(overrides.ascension()));
        if (overrides.tension() != null) root.add("tension", serializeTension(overrides.tension()));
        if (overrides.challenges() != null) root.add("challenges", serializeChallenges(overrides.challenges()));
        if (overrides.perkRarity() != null) root.add("perkRarity", serializePerkRarity(overrides.perkRarity()));

        return GSON.toJson(root);
    }

    private JsonObject serializeExecution(ExecutionOverrides ex) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "hpThreshold", ex.hpThreshold());
        addIfNotNull(obj, "durationTicks", ex.durationTicks());
        addIfNotNull(obj, "cooldownTicks", ex.cooldownTicks());
        addIfNotNull(obj, "styleReward", ex.styleReward());
        addIfNotNull(obj, "hpRegenPercent", ex.hpRegenPercent());
        addIfNotNull(obj, "dropBoost", ex.dropBoost());
        addIfNotNull(obj, "interruptVulnerability", ex.interruptVulnerability());
        addIfNotNull(obj, "range", ex.range());
        return obj;
    }

    private JsonObject serializeCombo(ComboOverrides c) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "timeoutTicks", c.timeoutTicks());
        addIfNotNull(obj, "basePoints", c.basePoints());
        addIfNotNull(obj, "multiplierIncrement", c.multiplierIncrement());
        addIfNotNull(obj, "maxMultiplier", c.maxMultiplier());
        addIfNotNull(obj, "finisherThreshold", c.finisherThreshold());
        addIfNotNull(obj, "juggleBonus", c.juggleBonus());
        addIfNotNull(obj, "headshotBonus", c.headshotBonus());
        addIfNotNull(obj, "executionBonus", c.executionBonus());
        return obj;
    }

    private JsonObject serializeStyleRank(StyleRankOverrides sr) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "dThreshold", sr.dThreshold());
        addIfNotNull(obj, "cThreshold", sr.cThreshold());
        addIfNotNull(obj, "bThreshold", sr.bThreshold());
        addIfNotNull(obj, "aThreshold", sr.aThreshold());
        addIfNotNull(obj, "sThreshold", sr.sThreshold());
        addIfNotNull(obj, "ssThreshold", sr.ssThreshold());
        addIfNotNull(obj, "sssThreshold", sr.sssThreshold());
        addIfNotNull(obj, "decayRate", sr.decayRate());
        addIfNotNull(obj, "decayDelayTicks", sr.decayDelayTicks());
        return obj;
    }

    private JsonObject serializeMomentum(MomentumOverrides m) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "decayRate", m.decayRate());
        addIfNotNull(obj, "killBoost", m.killBoost());
        addIfNotNull(obj, "hitBoost", m.hitBoost());
        addIfNotNull(obj, "overdriveThreshold", m.overdriveThreshold());
        addIfNotNull(obj, "overdriveDurationTicks", m.overdriveDurationTicks());
        addIfNotNull(obj, "staleThresholdTicks", m.staleThresholdTicks());
        addIfNotNull(obj, "freshBonusMultiplier", m.freshBonusMultiplier());
        addIfNotNull(obj, "virtuosoThreshold", m.virtuosoThreshold());
        addIfNotNull(obj, "virtuosoDurationTicks", m.virtuosoDurationTicks());
        return obj;
    }

    private JsonObject serializeBargain(BargainOverrides b) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "enabled", b.enabled());
        addIfNotNull(obj, "altarSpawnWave", b.altarSpawnWave());
        addIfNotNull(obj, "altarIntervalWaves", b.altarIntervalWaves());
        addIfNotNull(obj, "maxCursesPerRun", b.maxCursesPerRun());
        addIfNotNull(obj, "choiceTimeoutTicks", b.choiceTimeoutTicks());
        addIfNotNull(obj, "cursePowerMultiplier", b.cursePowerMultiplier());
        addIfNotNull(obj, "boonPowerMultiplier", b.boonPowerMultiplier());
        return obj;
    }

    private JsonObject serializeHazards(HazardOverrides h) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "enabled", h.enabled());
        addIfNotNull(obj, "floorCrumbleWave", h.floorCrumbleWave());
        addIfNotNull(obj, "bloodMoonWave", h.bloodMoonWave());
        addIfNotNull(obj, "arenaShrinkWave", h.arenaShrinkWave());
        addIfNotNull(obj, "lightningStormWave", h.lightningStormWave());
        addIfNotNull(obj, "voidRiftsWave", h.voidRiftsWave());
        addIfNotNull(obj, "floorCrumbleDuration", h.floorCrumbleDuration());
        addIfNotNull(obj, "bloodMoonDuration", h.bloodMoonDuration());
        addIfNotNull(obj, "arenaShrinkDuration", h.arenaShrinkDuration());
        addIfNotNull(obj, "lightningStormDuration", h.lightningStormDuration());
        addIfNotNull(obj, "voidRiftsDuration", h.voidRiftsDuration());
        addIfNotNull(obj, "lightningDamage", h.lightningDamage());
        addIfNotNull(obj, "voidRiftDamage", h.voidRiftDamage());
        addIfNotNull(obj, "bloodMoonMobBuff", h.bloodMoonMobBuff());
        addIfNotNull(obj, "arenaShrinkRate", h.arenaShrinkRate());
        return obj;
    }

    private JsonObject serializePerks(PerkOverrides p) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "synergyEnabled", p.synergyEnabled());
        addIfNotNull(obj, "hiddenPerksEnabled", p.hiddenPerksEnabled());
        addIfNotNull(obj, "sacrificeEnabled", p.sacrificeEnabled());
        addIfNotNull(obj, "sacrificeStyleCost", p.sacrificeStyleCost());
        addIfNotNull(obj, "discoveryXpBase", p.discoveryXpBase());
        addIfNotNull(obj, "synergyBonusMultiplier", p.synergyBonusMultiplier());
        return obj;
    }

    private JsonObject serializeWaves(WaveOverrides w) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "baseMobCount", w.baseMobCount());
        addIfNotNull(obj, "mobScaling", w.mobScaling());
        addIfNotNull(obj, "intermissionTicks", w.intermissionTicks());
        addIfNotNull(obj, "eliteChanceBase", w.eliteChanceBase());
        addIfNotNull(obj, "eliteChanceScaling", w.eliteChanceScaling());
        addIfNotNull(obj, "bossInterval", w.bossInterval());
        return obj;
    }

    private JsonObject serializeRewards(RewardOverrides r) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "xpMultiplier", r.xpMultiplier());
        addIfNotNull(obj, "styleMultiplier", r.styleMultiplier());
        addIfNotNull(obj, "dropRateBonus", r.dropRateBonus());
        addIfNotNull(obj, "bonusChestWaveInterval", r.bonusChestWaveInterval());
        return obj;
    }

    private JsonObject serializeSeasonPass(SeasonPassOverrides sp) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "maxTier", sp.maxTier());
        addIfNotNull(obj, "xpPerTier", sp.xpPerTier());
        addIfNotNull(obj, "xpPerKill", sp.xpPerKill());
        addIfNotNull(obj, "xpPerWave", sp.xpPerWave());
        addIfNotNull(obj, "xpPerBoss", sp.xpPerBoss());
        addIfNotNull(obj, "xpDailyChallenge", sp.xpDailyChallenge());
        addIfNotNull(obj, "xpWeeklyChallenge", sp.xpWeeklyChallenge());
        addIfNotNull(obj, "xpStyleS", sp.xpStyleS());
        addIfNotNull(obj, "xpStyleSS", sp.xpStyleSS());
        addIfNotNull(obj, "xpStyleSSS", sp.xpStyleSSS());
        addIfNotNull(obj, "xpFlawlessWave", sp.xpFlawlessWave());
        addIfNotNull(obj, "xpHighCombo50", sp.xpHighCombo50());
        addIfNotNull(obj, "xpHighCombo100", sp.xpHighCombo100());
        addIfNotNull(obj, "xpBoostMultiplier", sp.xpBoostMultiplier());
        addIfNotNull(obj, "xpGlobalMultiplier", sp.xpGlobalMultiplier());
        return obj;
    }

    private JsonObject serializeGuild(GuildOverrides g) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "maxSize", g.maxSize());
        addIfNotNull(obj, "maxLevel", g.maxLevel());
        addIfNotNull(obj, "createCost", g.createCost());
        addIfNotNull(obj, "xpPerLevelBase", g.xpPerLevelBase());
        addIfNotNull(obj, "xpScaling", g.xpScaling());
        addIfNotNull(obj, "bankTaxPercent", g.bankTaxPercent());
        addIfNotNull(obj, "objectiveKillsTarget", g.objectiveKillsTarget());
        addIfNotNull(obj, "objectiveWavesTarget", g.objectiveWavesTarget());
        addIfNotNull(obj, "objectiveBossesTarget", g.objectiveBossesTarget());
        addIfNotNull(obj, "objectiveTokensTarget", g.objectiveTokensTarget());
        addIfNotNull(obj, "bonusTokensMultiplier", g.bonusTokensMultiplier());
        addIfNotNull(obj, "bonusXpMultiplier", g.bonusXpMultiplier());
        return obj;
    }

    private JsonObject serializeAscension(AscensionOverrides a) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "maxLevel", a.maxLevel());
        addIfNotNull(obj, "minPrestige", a.minPrestige());
        addIfNotNull(obj, "costScaling", a.costScaling());
        addIfNotNull(obj, "damageBonusPerLevel", a.damageBonusPerLevel());
        addIfNotNull(obj, "defenseBonusPerLevel", a.defenseBonusPerLevel());
        addIfNotNull(obj, "tokenMultiplierPerLevel", a.tokenMultiplierPerLevel());
        addIfNotNull(obj, "lifestealBonusPerLevel", a.lifestealBonusPerLevel());
        addIfNotNull(obj, "critBonusPerLevel", a.critBonusPerLevel());
        addIfNotNull(obj, "comboDecayReductionPerLevel", a.comboDecayReductionPerLevel());
        addIfNotNull(obj, "extraPerkSlotsPerLevel", a.extraPerkSlotsPerLevel());
        addIfNotNull(obj, "startingHpBonusPerLevel", a.startingHpBonusPerLevel());
        return obj;
    }

    private JsonObject serializeTension(TensionOverrides t) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "baseWaveGain", t.baseWaveGain());
        addIfNotNull(obj, "noHitBonus", t.noHitBonus());
        addIfNotNull(obj, "minThreshold", t.minThreshold());
        addIfNotNull(obj, "maxThreshold", t.maxThreshold());
        addIfNotNull(obj, "eliteKillBonus", t.eliteKillBonus());
        addIfNotNull(obj, "comboBonusThreshold", t.comboBonusThreshold());
        addIfNotNull(obj, "styleSBonus", t.styleSBonus());
        addIfNotNull(obj, "minWavesBeforeBoss", t.minWavesBeforeBoss());
        addIfNotNull(obj, "maxWavesWithoutBoss", t.maxWavesWithoutBoss());
        addIfNotNull(obj, "decayAfterBoss", t.decayAfterBoss());
        return obj;
    }

    private JsonObject serializeChallenges(ChallengeOverrides c) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "dailyCount", c.dailyCount());
        addIfNotNull(obj, "weeklyCount", c.weeklyCount());
        addIfNotNull(obj, "dailyResetHour", c.dailyResetHour());
        addIfNotNull(obj, "weeklyResetDay", c.weeklyResetDay());
        addIfNotNull(obj, "killsTargetMin", c.killsTargetMin());
        addIfNotNull(obj, "killsTargetMax", c.killsTargetMax());
        addIfNotNull(obj, "wavesTargetMin", c.wavesTargetMin());
        addIfNotNull(obj, "wavesTargetMax", c.wavesTargetMax());
        addIfNotNull(obj, "comboTargetMin", c.comboTargetMin());
        addIfNotNull(obj, "comboTargetMax", c.comboTargetMax());
        addIfNotNull(obj, "dailyTokenReward", c.dailyTokenReward());
        addIfNotNull(obj, "weeklyTokenReward", c.weeklyTokenReward());
        addIfNotNull(obj, "dailyXpReward", c.dailyXpReward());
        addIfNotNull(obj, "weeklyXpReward", c.weeklyXpReward());
        return obj;
    }

    private JsonObject serializePerkRarity(PerkRarityOverrides pr) {
        JsonObject obj = new JsonObject();
        addIfNotNull(obj, "commonWeight", pr.commonWeight());
        addIfNotNull(obj, "uncommonWeight", pr.uncommonWeight());
        addIfNotNull(obj, "rareWeight", pr.rareWeight());
        addIfNotNull(obj, "epicWeight", pr.epicWeight());
        addIfNotNull(obj, "legendaryWeight", pr.legendaryWeight());
        addIfNotNull(obj, "choicesPerSelection", pr.choicesPerSelection());
        addIfNotNull(obj, "rerollCostBase", pr.rerollCostBase());
        addIfNotNull(obj, "rerollCostIncrement", pr.rerollCostIncrement());
        addIfNotNull(obj, "damageBonusCommon", pr.damageBonusCommon());
        addIfNotNull(obj, "damageBonusUncommon", pr.damageBonusUncommon());
        addIfNotNull(obj, "damageBonusRare", pr.damageBonusRare());
        addIfNotNull(obj, "damageBonusEpic", pr.damageBonusEpic());
        addIfNotNull(obj, "damageBonusLegendary", pr.damageBonusLegendary());
        return obj;
    }

    // ============================================
    // HELPERS
    // ============================================

    @Nullable
    private static Double getDouble(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : null;
    }

    @Nullable
    private static Integer getInt(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : null;
    }

    @Nullable
    private static Boolean getBool(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : null;
    }

    private static void addIfNotNull(JsonObject obj, String key, @Nullable Object value) {
        if (value != null) {
            if (value instanceof Double d) obj.addProperty(key, d);
            else if (value instanceof Integer i) obj.addProperty(key, i);
            else if (value instanceof Boolean b) obj.addProperty(key, b);
        }
    }

    // ============================================
    // EXAMPLE FILES
    // ============================================

    private void createExampleFiles() {
        // Create a comprehensive example
        GameplayOverrides example = GameplayOverrides.builder()
            .execution(new ExecutionOverrides(0.20, 50, 40, 250, 0.08, 0.40, 1.5, 5.0))
            .hazards(HazardOverrides.earlyHazards())
            .waves(WaveOverrides.intense())
            .rewards(RewardOverrides.generous())
            .build();

        save("example_intense", example);

        // Create a relaxed example
        GameplayOverrides relaxed = GameplayOverrides.builder()
            .execution(new ExecutionOverrides(0.25, 60, 40, 150, 0.10, 0.20, 1.0, 6.0))
            .bargain(BargainOverrides.disabled())
            .hazards(HazardOverrides.disabled())
            .waves(WaveOverrides.relaxed())
            .styleRank(StyleRankOverrides.easier())
            .build();

        save("example_relaxed", relaxed);

        LOGGER.info("[GameplayOverrides] Created example override files");
    }

    /**
     * Get last reload timestamp for cache invalidation.
     */
    public long getLastReloadTime() {
        return lastReloadTime;
    }
}
