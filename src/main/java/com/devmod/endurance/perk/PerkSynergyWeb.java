package com.devmod.endurance.perk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import com.devmod.endurance.PerkSynergySystem;
import com.devmod.endurance.PerkSystem;
public class PerkSynergyWeb {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerkSynergyWeb.class);

    public static final PerkSynergyWeb INSTANCE = new PerkSynergyWeb();

    // Hidden perk definitions
    private final Map<String, HiddenPerk> hiddenPerks = new LinkedHashMap<>();

    // Sacrifice recipes: which perks can be sacrificed for what
    private final List<SacrificeRecipe> sacrificeRecipes = new ArrayList<>();

    // Runtime discovery state (loaded from SavedData)
    private final Map<UUID, PlayerDiscoveries> playerDiscoveries = new ConcurrentHashMap<>();

    // ========== Hidden Perk Definition ==========

    /**
     * A hidden perk that must be discovered before it appears in selection.
     */
    public static class HiddenPerk {
        public final String perkId;              // ID of the actual perk
        public final String hintName;            // Cryptic hint name shown before discovery
        public final String hintDescription;     // Vague hint about how to unlock
        public final UnlockCondition condition;  // How to unlock this perk
        public final int discoveryXp;            // XP bonus for discovering

        public HiddenPerk(String perkId, String hintName, String hintDescription,
                         UnlockCondition condition, int discoveryXp) {
            this.perkId = perkId;
            this.hintName = hintName;
            this.hintDescription = hintDescription;
            this.condition = condition;
            this.discoveryXp = discoveryXp;
        }
    }

    /**
     * Condition to unlock a hidden perk.
     */
    public interface UnlockCondition {
        /**
         * Check if the condition is met.
         * @param context Current player context
         * @return true if unlocked
         */
        boolean isMet(DiscoveryContext context);

        /**
         * Get progress toward unlock (0.0 to 1.0).
         */
        default float getProgress(DiscoveryContext context) {
            return isMet(context) ? 1.0f : 0.0f;
        }

        /**
         * Get human-readable progress text.
         */
        String getProgressText(DiscoveryContext context);
    }

    /**
     * Context for checking unlock conditions.
     */
    public record DiscoveryContext(
        UUID playerId,
        Set<String> sessionPerks,          // Perks acquired this session
        Set<String> allDiscoveredPerks,    // All perks ever discovered
        int currentWave,
        int totalKills,
        int totalWavesCompleted,
        String styleRank,                   // Current style rank (D to SSS)
        Map<String, Integer> perkStacks    // Stack counts for each perk
    ) {}

    /**
     * Recipe for sacrificing perks to get a stronger one.
     */
    public record SacrificeRecipe(
        String resultPerkId,           // Perk you get
        Set<String> requiredPerks,     // Perks that must be sacrificed
        String description,            // What this sacrifice does
        int stylePointCost             // Additional style point cost
    ) {
        public boolean canSacrifice(Set<String> ownedPerks, int stylePoints) {
            return ownedPerks.containsAll(requiredPerks) && stylePoints >= stylePointCost;
        }
    }

    // ========== Player Discovery Tracking ==========

    /**
     * Persistent discovery data for a player.
     */
    public static class PlayerDiscoveries {
        private final UUID playerId;
        private final Set<String> discoveredHiddenPerks = new HashSet<>();
        private final Set<String> discoveredSynergies = new HashSet<>();
        private int totalDiscoveryXp = 0;
        private int discoveryLevel = 1;

        // Stats for unlock conditions
        private int totalWavesCompleted = 0;
        private int totalKills = 0;
        private int highestWave = 0;
        private int sssRanksAchieved = 0;
        private int executionsPerformed = 0;
        private int cursesAccepted = 0;
        private final Map<String, Integer> perkUsageCounts = new HashMap<>();

        public PlayerDiscoveries(UUID playerId) {
            this.playerId = playerId;
        }

        public boolean hasDiscovered(String perkId) {
            return discoveredHiddenPerks.contains(perkId);
        }

        public void discover(String perkId, int xp) {
            if (discoveredHiddenPerks.add(perkId)) {
                totalDiscoveryXp += xp;
                updateLevel();
            }
        }

        public void discoverSynergy(String synergyId) {
            discoveredSynergies.add(synergyId);
        }

        private void updateLevel() {
            // Every 500 XP = 1 level
            discoveryLevel = 1 + (totalDiscoveryXp / 500);
        }

        public void recordWaveComplete(int wave) {
            totalWavesCompleted++;
            if (wave > highestWave) highestWave = wave;
        }

        public void recordKills(int kills) {
            totalKills += kills;
        }

        public void recordSSSRank() {
            sssRanksAchieved++;
        }

        public void recordExecution() {
            executionsPerformed++;
        }

        public void recordCurseAccepted() {
            cursesAccepted++;
        }

        public void recordPerkUsage(String perkId) {
            perkUsageCounts.merge(perkId, 1, Integer::sum);
        }

        // Getters
        public UUID getPlayerId() { return playerId; }
        public Set<String> getDiscoveredPerks() { return Collections.unmodifiableSet(discoveredHiddenPerks); }
        public Set<String> getDiscoveredSynergies() { return Collections.unmodifiableSet(discoveredSynergies); }
        public int getTotalDiscoveryXp() { return totalDiscoveryXp; }
        public int getDiscoveryLevel() { return discoveryLevel; }
        public int getTotalWavesCompleted() { return totalWavesCompleted; }
        public int getTotalKills() { return totalKills; }
        public int getHighestWave() { return highestWave; }
        public int getSssRanksAchieved() { return sssRanksAchieved; }
        public int getExecutionsPerformed() { return executionsPerformed; }
        public int getCursesAccepted() { return cursesAccepted; }
        public int getPerkUsageCount(String perkId) { return perkUsageCounts.getOrDefault(perkId, 0); }

        // NBT Serialization
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("playerId", playerId);

            ListTag discovered = new ListTag();
            for (String perk : discoveredHiddenPerks) {
                discovered.add(StringTag.valueOf(perk));
            }
            tag.put("discoveredPerks", discovered);

            ListTag synergies = new ListTag();
            for (String syn : discoveredSynergies) {
                synergies.add(StringTag.valueOf(syn));
            }
            tag.put("discoveredSynergies", synergies);

            tag.putInt("discoveryXp", totalDiscoveryXp);
            tag.putInt("discoveryLevel", discoveryLevel);
            tag.putInt("wavesCompleted", totalWavesCompleted);
            tag.putInt("totalKills", totalKills);
            tag.putInt("highestWave", highestWave);
            tag.putInt("sssRanks", sssRanksAchieved);
            tag.putInt("executions", executionsPerformed);
            tag.putInt("curses", cursesAccepted);

            CompoundTag usages = new CompoundTag();
            for (Map.Entry<String, Integer> entry : perkUsageCounts.entrySet()) {
                usages.putInt(entry.getKey(), entry.getValue());
            }
            tag.put("perkUsage", usages);

            return tag;
        }

        public static PlayerDiscoveries load(CompoundTag tag) {
            UUID playerId = tag.getUUID("playerId");
            PlayerDiscoveries discoveries = new PlayerDiscoveries(playerId);

            ListTag discovered = tag.getList("discoveredPerks", Tag.TAG_STRING);
            for (int i = 0; i < discovered.size(); i++) {
                discoveries.discoveredHiddenPerks.add(discovered.getString(i));
            }

            ListTag synergies = tag.getList("discoveredSynergies", Tag.TAG_STRING);
            for (int i = 0; i < synergies.size(); i++) {
                discoveries.discoveredSynergies.add(synergies.getString(i));
            }

            discoveries.totalDiscoveryXp = tag.getInt("discoveryXp");
            discoveries.discoveryLevel = tag.getInt("discoveryLevel");
            discoveries.totalWavesCompleted = tag.getInt("wavesCompleted");
            discoveries.totalKills = tag.getInt("totalKills");
            discoveries.highestWave = tag.getInt("highestWave");
            discoveries.sssRanksAchieved = tag.getInt("sssRanks");
            discoveries.executionsPerformed = tag.getInt("executions");
            discoveries.cursesAccepted = tag.getInt("curses");

            CompoundTag usages = tag.getCompound("perkUsage");
            for (String key : usages.getAllKeys()) {
                discoveries.perkUsageCounts.put(key, usages.getInt(key));
            }

            return discoveries;
        }
    }

    // ========== SavedData for Persistence ==========

    /**
     * World-level saved data for all player discoveries.
     */
    public static class DiscoverySavedData extends SavedData {
        private static final String DATA_NAME = "devmod_perk_discoveries";

        private final Map<UUID, PlayerDiscoveries> discoveries = new HashMap<>();

        public DiscoverySavedData() {}

        public static DiscoverySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
            DiscoverySavedData data = new DiscoverySavedData();
            ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
            for (int i = 0; i < players.size(); i++) {
                PlayerDiscoveries pd = PlayerDiscoveries.load(players.getCompound(i));
                data.discoveries.put(pd.getPlayerId(), pd);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag players = new ListTag();
            for (PlayerDiscoveries pd : discoveries.values()) {
                players.add(pd.save());
            }
            tag.put("players", players);
            return tag;
        }

        public PlayerDiscoveries getOrCreate(UUID playerId) {
            return discoveries.computeIfAbsent(playerId, PlayerDiscoveries::new);
        }

        public static DiscoverySavedData get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(
                    new Factory<>(DiscoverySavedData::new, DiscoverySavedData::load),
                    DATA_NAME
                );
        }
    }

    // ========== Initialization ==========

    private PerkSynergyWeb() {
        initializeHiddenPerks();
        initializeSacrificeRecipes();
    }

    private void initializeHiddenPerks() {
        // === HIDDEN PERKS - Discovered through gameplay ===

        // Discovered by achieving SSS rank 3 times in one run
        registerHiddenPerk(new HiddenPerk(
            "style_is_substance",
            "???",
            "Masters of style unlock hidden power...",
            new AchievementCondition("sss_master", ctx -> ctx.styleRank().equals("SSS"), "Achieve SSS rank"),
            100
        ));

        // Discovered by stacking 3 elemental perks
        registerHiddenPerk(new HiddenPerk(
            "elemental_mastery",
            "Elemental ???",
            "Those who command all elements...",
            new PerkCombinationCondition(
                Set.of("fire_aspect", "frost_touch", "lightning_strike"),
                "Own all 3 elemental perks"
            ),
            150
        ));

        // Discovered by reaching wave 10
        registerHiddenPerk(new HiddenPerk(
            "avatar_of_war",
            "Avatar of ???",
            "Only the most aggressive warriors...",
            new WaveCondition(10, "Reach wave 10"),
            200
        ));

        // Discovered by using vampiric perks to heal 500 HP total
        registerHiddenPerk(new HiddenPerk(
            "soul_drain",
            "Soul ???",
            "Those who drink deeply from their enemies...",
            new PerkCombinationCondition(
                Set.of("lifesteal", "blood_frenzy"),
                "Master both lifesteal and blood frenzy"
            ),
            150
        ));

        // Discovered by surviving with less than 10% HP for 30 seconds
        registerHiddenPerk(new HiddenPerk(
            "adrenaline_surge",
            "Adrenaline ???",
            "Dance on the edge of death...",
            new AchievementCondition("near_death_survivor",
                ctx -> ctx.totalWavesCompleted() >= 5,
                "Survive 5 waves on low health"
            ),
            175
        ));

        // Discovered by performing 10 executions
        registerHiddenPerk(new HiddenPerk(
            "executioners_wrath",
            "Executioner's ???",
            "Those who deliver final judgment...",
            new AchievementCondition("execution_master",
                ctx -> true, // Always available after discovery via stats
                "Perform 10 executions"
            ),
            125
        ));

        // Discovered by accepting 5 curses total (across runs)
        registerHiddenPerk(new HiddenPerk(
            "ultimate_curse",
            "Pact with ???",
            "Those who bargain with darkness...",
            new AchievementCondition("curse_collector",
                ctx -> true, // Checked via persistent stats
                "Accept 5 Devil's Bargain curses"
            ),
            250
        ));

        // Discovered by completing wave without taking damage
        registerHiddenPerk(new HiddenPerk(
            "phantom_shift",
            "Phantom ???",
            "The untouchable ones...",
            new AchievementCondition("untouchable",
                ctx -> ctx.totalWavesCompleted() >= 3,
                "Complete a wave without taking damage"
            ),
            150
        ));

        // Discovered by having 5+ offense perks
        registerHiddenPerk(new HiddenPerk(
            "echo_strike",
            "Echo ???",
            "Strikes that resonate through time...",
            new CategoryCountCondition(PerkSystem.PerkCategory.OFFENSE, 5,
                "Acquire 5 offense perks"
            ),
            175
        ));

        // Discovered by having 5+ defense perks
        registerHiddenPerk(new HiddenPerk(
            "blood_pact",
            "Blood ???",
            "Those who share their fate...",
            new CategoryCountCondition(PerkSystem.PerkCategory.DEFENSE, 5,
                "Acquire 5 defense perks"
            ),
            175
        ));

        LOGGER.info("[PerkSynergyWeb] Registered {} hidden perks", hiddenPerks.size());
    }

    private void initializeSacrificeRecipes() {
        // === SACRIFICE RECIPES - Remove perks for stronger ones ===

        // Sacrifice 3 Common offense perks for Berserker
        sacrificeRecipes.add(new SacrificeRecipe(
            "berserker",
            Set.of("sharp_blades", "fury"),
            "Sacrifice Sharp Blades and Fury to become a Berserker",
            500
        ));

        // Sacrifice defensive perks for Glass Cannon
        sacrificeRecipes.add(new SacrificeRecipe(
            "glass_cannon",
            Set.of("tough_skin", "vitality"),
            "Sacrifice defense for ultimate offense",
            750
        ));

        // Sacrifice all elemental perks for Storm Master (enhanced elemental)
        sacrificeRecipes.add(new SacrificeRecipe(
            "elemental_mastery",
            Set.of("fire_aspect", "frost_touch", "lightning_strike"),
            "Unite the elements into mastery",
            1000
        ));

        // Sacrifice lifesteal and regeneration for Soul Harvest
        sacrificeRecipes.add(new SacrificeRecipe(
            "soul_harvest",
            Set.of("lifesteal", "blood_frenzy", "soul_drain"),
            "Consume life force to harvest souls",
            1500
        ));

        // Sacrifice combo perks for ultimate style mastery
        sacrificeRecipes.add(new SacrificeRecipe(
            "style_is_substance",
            Set.of("showoff", "combo_master"),
            "Style becomes power incarnate",
            1000
        ));

        LOGGER.info("[PerkSynergyWeb] Registered {} sacrifice recipes", sacrificeRecipes.size());
    }

    private void registerHiddenPerk(HiddenPerk perk) {
        hiddenPerks.put(perk.perkId, perk);
    }

    // ========== Public API ==========

    /**
     * Get player discoveries (loading from SavedData if needed).
     */
    public PlayerDiscoveries getDiscoveries(ServerPlayer player) {
        UUID playerId = player.getUUID();
        return playerDiscoveries.computeIfAbsent(playerId, id -> {
            ServerLevel level = player.serverLevel();
            DiscoverySavedData data = DiscoverySavedData.get(level);
            return data.getOrCreate(playerId);
        });
    }

    /**
     * Check if a perk is hidden for this player.
     */
    public boolean isHiddenPerk(String perkId, UUID playerId) {
        HiddenPerk hidden = hiddenPerks.get(perkId);
        if (hidden == null) return false;

        PlayerDiscoveries discoveries = playerDiscoveries.get(playerId);
        if (discoveries == null) return true;

        return !discoveries.hasDiscovered(perkId);
    }

    /**
     * Get display info for a hidden perk (shows "???" if not discovered).
     */
    public HiddenPerkDisplay getHiddenPerkDisplay(String perkId, UUID playerId) {
        HiddenPerk hidden = hiddenPerks.get(perkId);
        if (hidden == null) {
            // Not a hidden perk, get normal display from PerkSystem
            return PerkSystem.INSTANCE.getPerk(perkId)
                .map(p -> new HiddenPerkDisplay(p.name, p.description, true, 1.0f, ""))
                .orElse(null);
        }

        PlayerDiscoveries discoveries = playerDiscoveries.get(playerId);
        if (discoveries != null && discoveries.hasDiscovered(perkId)) {
            // Already discovered
            return PerkSystem.INSTANCE.getPerk(perkId)
                .map(p -> new HiddenPerkDisplay(p.name, p.description, true, 1.0f, "Discovered!"))
                .orElse(null);
        }

        // Still hidden - show hint
        return new HiddenPerkDisplay(
            hidden.hintName,
            hidden.hintDescription,
            false,
            0.0f,
            "???"
        );
    }

    public record HiddenPerkDisplay(
        String displayName,
        String description,
        boolean isDiscovered,
        float progress,
        String progressText
    ) {}

    /**
     * Check and process perk discoveries based on current context.
     */
    public List<String> checkDiscoveries(ServerPlayer player, DiscoveryContext context) {
        PlayerDiscoveries discoveries = getDiscoveries(player);
        List<String> newlyDiscovered = new ArrayList<>();

        for (HiddenPerk hidden : hiddenPerks.values()) {
            if (discoveries.hasDiscovered(hidden.perkId)) continue;

            // Check additional persistent conditions
            if (hidden.perkId.equals("executioners_wrath") && discoveries.getExecutionsPerformed() < 10) continue;
            if (hidden.perkId.equals("ultimate_curse") && discoveries.getCursesAccepted() < 5) continue;

            if (hidden.condition.isMet(context)) {
                discoveries.discover(hidden.perkId, hidden.discoveryXp);
                newlyDiscovered.add(hidden.perkId);

                // Notify player
                player.displayClientMessage(
                    Component.literal("§d§l[DISCOVERY] §r§5" + getPerkName(hidden.perkId) +
                        " §r§7unlocked! §a+" + hidden.discoveryXp + " Discovery XP"),
                    false
                );

                LOGGER.info("[PerkSynergyWeb] {} discovered hidden perk: {} (+{} XP)",
                    player.getName().getString(), hidden.perkId, hidden.discoveryXp);
            }
        }

        if (!newlyDiscovered.isEmpty()) {
            saveDiscoveries(player);
        }

        return newlyDiscovered;
    }

    /**
     * Get available sacrifice recipes for current perks.
     */
    public List<SacrificeRecipe> getAvailableSacrifices(Set<String> ownedPerks, int stylePoints) {
        return sacrificeRecipes.stream()
            .filter(r -> r.canSacrifice(ownedPerks, stylePoints))
            .filter(r -> !ownedPerks.contains(r.resultPerkId)) // Don't already have result
            .toList();
    }

    /**
     * Perform a sacrifice to get a stronger perk.
     */
    public SacrificeResult performSacrifice(ServerPlayer player, PerkSystem.PerkSession session,
                                            SacrificeRecipe recipe, int currentStylePoints) {
        Set<String> ownedPerks = session.getAcquiredPerkIds();

        if (!recipe.canSacrifice(ownedPerks, currentStylePoints)) {
            return new SacrificeResult(false, "Cannot perform sacrifice - missing requirements", null, 0);
        }

        // Remove sacrificed perks (we can't actually remove from session in current design,
        // so we track this separately and apply negative effects)
        // For now, mark them as "sacrificed" in persistent data
        PlayerDiscoveries discoveries = getDiscoveries(player);

        // Apply the result perk
        PerkSystem.INSTANCE.getPerk(recipe.resultPerkId).ifPresent(perk -> {
            session.addPerk(perk.id);
            PerkSystem.PerkContext context = new PerkSystem.PerkContext(player, session, 1);
            perk.apply(context);
        });

        // Visual feedback
        player.displayClientMessage(
            Component.literal("§4§l[SACRIFICE] §r§c" + recipe.description +
                " §r§7→ §a" + getPerkName(recipe.resultPerkId)),
            false
        );

        LOGGER.info("[PerkSynergyWeb] {} performed sacrifice: {} -> {}",
            player.getName().getString(),
            recipe.requiredPerks,
            recipe.resultPerkId);

        return new SacrificeResult(true, "Sacrifice complete!", recipe.resultPerkId, recipe.stylePointCost);
    }

    public record SacrificeResult(
        boolean success,
        String message,
        String resultPerkId,
        int styleCost
    ) {}

    /**
     * Get synergy preview including hidden synergy hints.
     */
    public SynergyWebPreview getWebPreview(String perkId, Set<String> ownedPerks, UUID playerId) {
        // Get base synergy info
        PerkSynergySystem.SynergyPreview baseSynergy =
            PerkSynergySystem.INSTANCE.analyzePerk(perkId, ownedPerks);

        // Add hidden perk discovery hints
        PlayerDiscoveries discoveries = playerDiscoveries.get(playerId);
        List<String> hiddenHints = new ArrayList<>();

        if (discoveries != null) {
            // Check if this perk helps unlock any hidden perks
            Set<String> simulated = new HashSet<>(ownedPerks);
            simulated.add(perkId);

            for (HiddenPerk hidden : hiddenPerks.values()) {
                if (discoveries.hasDiscovered(hidden.perkId)) continue;

                // Simple hint: if this perk is part of a combo condition
                if (hidden.condition instanceof PerkCombinationCondition combo) {
                    if (combo.requiredPerks.contains(perkId) && !simulated.containsAll(combo.requiredPerks)) {
                        long have = combo.requiredPerks.stream().filter(simulated::contains).count();
                        long need = combo.requiredPerks.size();
                        if (have > 0 && have < need) {
                            hiddenHints.add("§8[" + have + "/" + need + " toward a hidden perk...]");
                        }
                    }
                }
            }
        }

        return new SynergyWebPreview(baseSynergy, hiddenHints);
    }

    public record SynergyWebPreview(
        PerkSynergySystem.SynergyPreview baseSynergies,
        List<String> hiddenHints
    ) {}

    /**
     * Record stats for unlock conditions.
     */
    public void recordWaveComplete(ServerPlayer player, int wave) {
        PlayerDiscoveries d = getDiscoveries(player);
        d.recordWaveComplete(wave);
        saveDiscoveries(player);
    }

    public void recordKills(ServerPlayer player, int kills) {
        PlayerDiscoveries d = getDiscoveries(player);
        d.recordKills(kills);
    }

    public void recordExecution(ServerPlayer player) {
        PlayerDiscoveries d = getDiscoveries(player);
        d.recordExecution();
        saveDiscoveries(player);
    }

    public void recordCurseAccepted(ServerPlayer player) {
        PlayerDiscoveries d = getDiscoveries(player);
        d.recordCurseAccepted();
        saveDiscoveries(player);
    }

    public void recordPerkAcquired(ServerPlayer player, String perkId) {
        PlayerDiscoveries d = getDiscoveries(player);
        d.recordPerkUsage(perkId);
    }

    public void recordSSSRank(ServerPlayer player) {
        PlayerDiscoveries d = getDiscoveries(player);
        d.recordSSSRank();
        saveDiscoveries(player);
    }

    /**
     * Save discoveries to world data.
     */
    private void saveDiscoveries(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DiscoverySavedData data = DiscoverySavedData.get(level);
        PlayerDiscoveries discoveries = playerDiscoveries.get(player.getUUID());
        if (discoveries != null) {
            data.discoveries.put(player.getUUID(), discoveries);
            data.setDirty();
        }
    }

    /**
     * Load discoveries when player joins.
     */
    public void onPlayerJoin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DiscoverySavedData data = DiscoverySavedData.get(level);
        PlayerDiscoveries discoveries = data.getOrCreate(player.getUUID());
        playerDiscoveries.put(player.getUUID(), discoveries);
    }

    /**
     * Cleanup when player leaves.
     */
    public void onPlayerLeave(ServerPlayer player) {
        saveDiscoveries(player);
        playerDiscoveries.remove(player.getUUID());
    }

    /**
     * Get all hidden perks for display in discovery menu.
     */
    public List<HiddenPerkInfo> getAllHiddenPerks(UUID playerId) {
        PlayerDiscoveries discoveries = playerDiscoveries.get(playerId);
        List<HiddenPerkInfo> result = new ArrayList<>();

        for (HiddenPerk hidden : hiddenPerks.values()) {
            boolean discovered = discoveries != null && discoveries.hasDiscovered(hidden.perkId);
            result.add(new HiddenPerkInfo(
                hidden.perkId,
                discovered ? getPerkName(hidden.perkId) : hidden.hintName,
                discovered ? getPerkDescription(hidden.perkId) : hidden.hintDescription,
                discovered,
                hidden.discoveryXp
            ));
        }

        return result;
    }

    public record HiddenPerkInfo(
        String perkId,
        String displayName,
        String description,
        boolean discovered,
        int discoveryXp
    ) {}

    // ========== Helper Methods ==========

    private String getPerkName(String perkId) {
        return PerkSystem.INSTANCE.getPerk(perkId)
            .map(p -> p.name)
            .orElse(perkId);
    }

    private String getPerkDescription(String perkId) {
        return PerkSystem.INSTANCE.getPerk(perkId)
            .map(p -> p.description)
            .orElse("");
    }

    // ========== Condition Implementations ==========

    /**
     * Condition based on owning specific perk combinations.
     */
    public static class PerkCombinationCondition implements UnlockCondition {
        private final Set<String> requiredPerks;
        private final String progressText;

        public PerkCombinationCondition(Set<String> requiredPerks, String progressText) {
            this.requiredPerks = requiredPerks;
            this.progressText = progressText;
        }

        @Override
        public boolean isMet(DiscoveryContext context) {
            return context.sessionPerks().containsAll(requiredPerks);
        }

        @Override
        public float getProgress(DiscoveryContext context) {
            long have = requiredPerks.stream().filter(context.sessionPerks()::contains).count();
            return (float) have / requiredPerks.size();
        }

        @Override
        public String getProgressText(DiscoveryContext context) {
            long have = requiredPerks.stream().filter(context.sessionPerks()::contains).count();
            return progressText + " (" + have + "/" + requiredPerks.size() + ")";
        }
    }

    /**
     * Condition based on reaching a certain wave.
     */
    public static class WaveCondition implements UnlockCondition {
        private final int requiredWave;
        private final String progressText;

        public WaveCondition(int requiredWave, String progressText) {
            this.requiredWave = requiredWave;
            this.progressText = progressText;
        }

        @Override
        public boolean isMet(DiscoveryContext context) {
            return context.currentWave() >= requiredWave;
        }

        @Override
        public float getProgress(DiscoveryContext context) {
            return Math.min(1.0f, (float) context.currentWave() / requiredWave);
        }

        @Override
        public String getProgressText(DiscoveryContext context) {
            return progressText + " (" + context.currentWave() + "/" + requiredWave + ")";
        }
    }

    /**
     * Condition based on category perk count.
     */
    public static class CategoryCountCondition implements UnlockCondition {
        private final PerkSystem.PerkCategory category;
        private final int requiredCount;
        private final String progressText;

        public CategoryCountCondition(PerkSystem.PerkCategory category, int requiredCount, String progressText) {
            this.category = category;
            this.requiredCount = requiredCount;
            this.progressText = progressText;
        }

        @Override
        public boolean isMet(DiscoveryContext context) {
            long count = context.sessionPerks().stream()
                .map(id -> PerkSystem.INSTANCE.getPerk(id).orElse(null))
                .filter(p -> p != null && p.category == category)
                .count();
            return count >= requiredCount;
        }

        @Override
        public float getProgress(DiscoveryContext context) {
            long count = context.sessionPerks().stream()
                .map(id -> PerkSystem.INSTANCE.getPerk(id).orElse(null))
                .filter(p -> p != null && p.category == category)
                .count();
            return Math.min(1.0f, (float) count / requiredCount);
        }

        @Override
        public String getProgressText(DiscoveryContext context) {
            long count = context.sessionPerks().stream()
                .map(id -> PerkSystem.INSTANCE.getPerk(id).orElse(null))
                .filter(p -> p != null && p.category == category)
                .count();
            return progressText + " (" + count + "/" + requiredCount + ")";
        }
    }

    /**
     * Generic achievement condition with predicate.
     */
    public static class AchievementCondition implements UnlockCondition {
        private final String achievementId;
        private final Predicate<DiscoveryContext> predicate;
        private final String progressText;

        public AchievementCondition(String achievementId, Predicate<DiscoveryContext> predicate, String progressText) {
            this.achievementId = achievementId;
            this.predicate = predicate;
            this.progressText = progressText;
        }

        @Override
        public boolean isMet(DiscoveryContext context) {
            return predicate.test(context);
        }

        @Override
        public String getProgressText(DiscoveryContext context) {
            return progressText + (isMet(context) ? " [Complete]" : " [In Progress]");
        }
    }
}
