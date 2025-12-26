package com.devmod.endurance.guild;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.config.EnduranceConfigManager;
public class GuildSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildSystem.class);

    // Default values (used when no quest context or as fallback)
    public static final int DEFAULT_MAX_GUILD_SIZE = 50;
    public static final int DEFAULT_MIN_GUILD_NAME_LENGTH = 3;
    public static final int DEFAULT_MAX_GUILD_NAME_LENGTH = 24;
    public static final int DEFAULT_MAX_GUILD_LEVEL = 20;
    public static final int DEFAULT_GUILD_CREATE_COST = 5000; // tokens

    // Backwards-compatible constants
    public static final int MAX_GUILD_SIZE = DEFAULT_MAX_GUILD_SIZE;
    public static final int MIN_GUILD_NAME_LENGTH = DEFAULT_MIN_GUILD_NAME_LENGTH;
    public static final int MAX_GUILD_NAME_LENGTH = DEFAULT_MAX_GUILD_NAME_LENGTH;
    public static final int MAX_GUILD_LEVEL = DEFAULT_MAX_GUILD_LEVEL;
    public static final int GUILD_CREATE_COST = DEFAULT_GUILD_CREATE_COST;

    // Config manager reference
    private final EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

    private static GuildSystem instance;

    // Guild storage
    private final Map<String, Guild> guilds = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerGuildMap = new ConcurrentHashMap<>();

    // Weekly objectives
    private final List<GuildObjective> weeklyObjectives = new ArrayList<>();
    private Instant weeklyResetTime;

    private GuildSystem() {
        initializeWeeklyObjectives();
    }

    public static GuildSystem getInstance() {
        if (instance == null) {
            instance = new GuildSystem();
        }
        return instance;
    }

    // === Config-Aware Getters ===

    /**
     * Get max guild size for a specific quest context.
     */
    public int getMaxGuildSize(UUID questId) {
        return config.getGuildMaxSize(questId);
    }

    /**
     * Get max guild level for a specific quest context.
     */
    public int getMaxGuildLevel(UUID questId) {
        return config.getGuildMaxLevel(questId);
    }

    /**
     * Get guild create cost for a specific quest context.
     */
    public int getGuildCreateCost(UUID questId) {
        return config.getGuildCreateCost(questId);
    }

    /**
     * Get guild bonus token multiplier for a specific quest context.
     */
    public double getBonusTokensMultiplier(UUID questId) {
        return config.getGuildBonusTokensMultiplier(questId);
    }

    /**
     * Get guild bonus XP multiplier for a specific quest context.
     */
    public double getBonusXpMultiplier(UUID questId) {
        return config.getGuildBonusXpMultiplier(questId);
    }

    // === Guild Management ===

    /**
     * Create a new guild.
     */
    public GuildCreateResult createGuild(UUID founderId, String name, String tag) {
        // Validate name
        if (name == null || name.length() < MIN_GUILD_NAME_LENGTH ||
            name.length() > MAX_GUILD_NAME_LENGTH) {
            return GuildCreateResult.INVALID_NAME;
        }

        // Validate tag (3-5 chars)
        if (tag == null || tag.length() < 3 || tag.length() > 5) {
            return GuildCreateResult.INVALID_TAG;
        }

        // Check if player is already in a guild
        if (playerGuildMap.containsKey(founderId)) {
            return GuildCreateResult.ALREADY_IN_GUILD;
        }

        // Check for duplicate name
        String normalizedName = name.toLowerCase().trim();
        if (guilds.values().stream().anyMatch(g ->
            g.getName().toLowerCase().equals(normalizedName))) {
            return GuildCreateResult.NAME_TAKEN;
        }

        // Check for duplicate tag
        String normalizedTag = tag.toUpperCase().trim();
        if (guilds.values().stream().anyMatch(g ->
            g.getTag().equalsIgnoreCase(normalizedTag))) {
            return GuildCreateResult.TAG_TAKEN;
        }

        // Create guild
        String guildId = UUID.randomUUID().toString();
        Guild guild = new Guild(guildId, name, normalizedTag, founderId);
        guilds.put(guildId, guild);
        playerGuildMap.put(founderId, guildId);

        LOGGER.info("[Guild] Created guild '{}' [{}] by {}",
            name, normalizedTag, founderId);

        return GuildCreateResult.SUCCESS;
    }

    /**
     * Invite a player to a guild.
     */
    public GuildInviteResult invitePlayer(UUID inviterId, UUID targetId, String guildId) {
        Guild guild = guilds.get(guildId);
        if (guild == null) {
            return GuildInviteResult.GUILD_NOT_FOUND;
        }

        if (!guild.canInvite(inviterId)) {
            return GuildInviteResult.NO_PERMISSION;
        }

        if (guild.getMemberCount() >= MAX_GUILD_SIZE) {
            return GuildInviteResult.GUILD_FULL;
        }

        if (playerGuildMap.containsKey(targetId)) {
            return GuildInviteResult.ALREADY_IN_GUILD;
        }

        guild.addPendingInvite(targetId, inviterId);
        return GuildInviteResult.SUCCESS;
    }

    /**
     * Accept a guild invite.
     */
    public boolean acceptInvite(UUID playerId, String guildId) {
        Guild guild = guilds.get(guildId);
        if (guild == null || !guild.hasPendingInvite(playerId)) {
            return false;
        }

        if (playerGuildMap.containsKey(playerId)) {
            return false;
        }

        guild.removePendingInvite(playerId);
        guild.addMember(playerId, GuildRank.MEMBER);
        playerGuildMap.put(playerId, guildId);

        LOGGER.info("[Guild] {} joined guild '{}'", playerId, guild.getName());
        return true;
    }

    /**
     * Leave a guild.
     */
    public boolean leaveGuild(UUID playerId) {
        String guildId = playerGuildMap.get(playerId);
        if (guildId == null) {
            return false;
        }

        Guild guild = guilds.get(guildId);
        if (guild == null) {
            playerGuildMap.remove(playerId);
            return false;
        }

        // Check if leader trying to leave
        if (guild.getLeaderId().equals(playerId)) {
            if (guild.getMemberCount() > 1) {
                // Must transfer leadership first
                return false;
            } else {
                // Disband guild
                disbandGuild(guildId);
                return true;
            }
        }

        guild.removeMember(playerId);
        playerGuildMap.remove(playerId);

        LOGGER.info("[Guild] {} left guild '{}'", playerId, guild.getName());
        return true;
    }

    /**
     * Disband a guild.
     */
    public void disbandGuild(String guildId) {
        Guild guild = guilds.remove(guildId);
        if (guild != null) {
            for (UUID memberId : guild.getMemberIds()) {
                playerGuildMap.remove(memberId);
            }
            LOGGER.info("[Guild] Disbanded guild '{}'", guild.getName());
        }
    }

    // === Guild Progress ===

    /**
     * Contribute to guild objectives.
     */
    public void contributeProgress(UUID playerId, ObjectiveType type, int amount) {
        String guildId = playerGuildMap.get(playerId);
        if (guildId == null) return;

        Guild guild = guilds.get(guildId);
        if (guild == null) return;

        // Find matching objectives
        for (GuildObjective objective : weeklyObjectives) {
            if (objective.type() == type && !guild.hasCompletedObjective(objective.id())) {
                int newProgress = guild.addObjectiveProgress(objective.id(), amount);

                if (newProgress >= objective.target()) {
                    onObjectiveComplete(guild, objective);
                }
            }
        }

        // Update member contribution stats
        guild.addMemberContribution(playerId, type, amount);

        // Award guild XP
        int guildXp = calculateGuildXP(type, amount);
        guild.addExperience(guildXp);
        checkLevelUp(guild);
    }

    /**
     * Handle objective completion.
     */
    private void onObjectiveComplete(Guild guild, GuildObjective objective) {
        guild.markObjectiveComplete(objective.id());

        // Award rewards
        guild.addToBank(objective.tokenReward());
        guild.addExperience(objective.xpReward());

        LOGGER.info("[Guild] '{}' completed objective '{}'!",
            guild.getName(), objective.name());

        // TODO: Send notification to all members
    }

    /**
     * Calculate guild XP for contribution.
     */
    private int calculateGuildXP(ObjectiveType type, int amount) {
        return switch (type) {
            case KILLS -> amount / 10;
            case WAVES -> amount * 5;
            case BOSS_KILLS -> amount * 20;
            case TOKENS_EARNED -> amount / 100;
            case PRESTIGE_EARNED -> amount * 10;
            case CHALLENGES_COMPLETED -> amount * 50;
            case STYLE_S_RANKS -> amount * 15;
            case FLAWLESS_WAVES -> amount * 25;
        };
    }

    /**
     * Check and handle level up.
     */
    private void checkLevelUp(Guild guild) {
        int oldLevel = guild.getLevel();
        int xpForNext = getXPForLevel(oldLevel + 1);

        while (guild.getLevel() < MAX_GUILD_LEVEL &&
               guild.getExperience() >= xpForNext) {
            guild.levelUp();
            onGuildLevelUp(guild, guild.getLevel());
            xpForNext = getXPForLevel(guild.getLevel() + 1);
        }
    }

    /**
     * Handle guild level up.
     */
    private void onGuildLevelUp(Guild guild, int newLevel) {
        LOGGER.info("[Guild] '{}' reached level {}!", guild.getName(), newLevel);

        // Unlock perks at certain levels
        GuildPerk perk = GuildPerk.getForLevel(newLevel);
        if (perk != null) {
            guild.unlockPerk(perk);
        }

        // TODO: Notify members
    }

    /**
     * Get XP required for a level.
     */
    public int getXPForLevel(int level) {
        if (level <= 1) return 0;
        return (int) (1000 * Math.pow(1.5, level - 1));
    }

    // === Weekly Objectives ===

    /**
     * Initialize weekly objectives.
     */
    private void initializeWeeklyObjectives() {
        weeklyResetTime = getNextMondayUTC();

        // Generate objectives based on week number
        int weekNumber = (int) (System.currentTimeMillis() / (7 * 24 * 60 * 60 * 1000L));
        Random rng = new Random(weekNumber);

        weeklyObjectives.clear();
        weeklyObjectives.addAll(generateWeeklyObjectives(rng));

        LOGGER.info("[Guild] Initialized {} weekly objectives", weeklyObjectives.size());
    }

    /**
     * Generate weekly objectives.
     */
    private List<GuildObjective> generateWeeklyObjectives(Random rng) {
        List<GuildObjective> objectives = new ArrayList<>();

        // Always have a kills objective
        objectives.add(new GuildObjective(
            "weekly_kills",
            "Guild Slayers",
            "Collectively eliminate enemies as a guild",
            ObjectiveType.KILLS,
            5000 + rng.nextInt(5000),
            2000,
            500
        ));

        // Always have a waves objective
        objectives.add(new GuildObjective(
            "weekly_waves",
            "Wave Warriors",
            "Complete waves together",
            ObjectiveType.WAVES,
            200 + rng.nextInt(100),
            1500,
            400
        ));

        // Rotating third objective
        ObjectiveType[] rotatingTypes = {
            ObjectiveType.BOSS_KILLS,
            ObjectiveType.STYLE_S_RANKS,
            ObjectiveType.FLAWLESS_WAVES,
            ObjectiveType.CHALLENGES_COMPLETED
        };
        ObjectiveType rotatingType = rotatingTypes[rng.nextInt(rotatingTypes.length)];

        int target;
        String name;
        String desc;
        switch (rotatingType) {
            case BOSS_KILLS -> {
                target = 50 + rng.nextInt(30);
                name = "Boss Hunters";
                desc = "Defeat powerful bosses together";
            }
            case STYLE_S_RANKS -> {
                target = 100 + rng.nextInt(50);
                name = "Style Masters";
                desc = "Achieve S rank or higher";
            }
            case FLAWLESS_WAVES -> {
                target = 75 + rng.nextInt(25);
                name = "Perfect Execution";
                desc = "Complete waves without taking damage";
            }
            default -> {
                target = 30 + rng.nextInt(20);
                name = "Challenge Champions";
                desc = "Complete daily and weekly challenges";
            }
        }

        objectives.add(new GuildObjective(
            "weekly_rotating",
            name,
            desc,
            rotatingType,
            target,
            2500,
            600
        ));

        return objectives;
    }

    /**
     * Check and reset weekly objectives if needed.
     */
    public void checkWeeklyReset() {
        if (Instant.now().isAfter(weeklyResetTime)) {
            resetWeeklyObjectives();
        }
    }

    /**
     * Reset weekly objectives for all guilds.
     */
    private void resetWeeklyObjectives() {
        LOGGER.info("[Guild] Resetting weekly objectives");

        // Distribute weekly rewards before reset
        distributeWeeklyRewards();

        // Reset guild progress
        for (Guild guild : guilds.values()) {
            guild.resetWeeklyProgress();
        }

        // Generate new objectives
        initializeWeeklyObjectives();
    }

    /**
     * Distribute weekly rewards based on rankings.
     */
    private void distributeWeeklyRewards() {
        List<Guild> ranked = getWeeklyLeaderboard();

        for (int i = 0; i < ranked.size(); i++) {
            Guild guild = ranked.get(i);
            int rank = i + 1;

            int bonusTokens;
            int bonusXP;

            if (rank == 1) {
                bonusTokens = 10000;
                bonusXP = 2000;
            } else if (rank <= 3) {
                bonusTokens = 5000;
                bonusXP = 1000;
            } else if (rank <= 10) {
                bonusTokens = 2000;
                bonusXP = 500;
            } else {
                bonusTokens = 500;
                bonusXP = 100;
            }

            guild.addToBank(bonusTokens);
            guild.addExperience(bonusXP);

            LOGGER.info("[Guild] '{}' finished #{} - awarded {} tokens, {} XP",
                guild.getName(), rank, bonusTokens, bonusXP);
        }
    }

    /**
     * Get next Monday at midnight UTC.
     */
    private Instant getNextMondayUTC() {
        Instant now = Instant.now();
        int daysUntilMonday = (8 - now.atZone(java.time.ZoneOffset.UTC)
            .getDayOfWeek().getValue()) % 7;
        if (daysUntilMonday == 0) daysUntilMonday = 7;
        return now.truncatedTo(ChronoUnit.DAYS).plus(daysUntilMonday, ChronoUnit.DAYS);
    }

    // === Leaderboards ===

    /**
     * Get weekly guild leaderboard.
     */
    public List<Guild> getWeeklyLeaderboard() {
        return guilds.values().stream()
            .sorted((a, b) -> Integer.compare(
                b.getWeeklyScore(),
                a.getWeeklyScore()))
            .toList();
    }

    /**
     * Get all-time guild leaderboard.
     */
    public List<Guild> getAllTimeLeaderboard() {
        return guilds.values().stream()
            .sorted((a, b) -> Integer.compare(
                b.getTotalScore(),
                a.getTotalScore()))
            .toList();
    }

    // === Queries ===

    public Optional<Guild> getGuild(String guildId) {
        return Optional.ofNullable(guilds.get(guildId));
    }

    public Optional<Guild> getPlayerGuild(UUID playerId) {
        String guildId = playerGuildMap.get(playerId);
        if (guildId == null) return Optional.empty();
        return Optional.ofNullable(guilds.get(guildId));
    }

    public boolean isInGuild(UUID playerId) {
        return playerGuildMap.containsKey(playerId);
    }

    public List<GuildObjective> getWeeklyObjectives() {
        return Collections.unmodifiableList(weeklyObjectives);
    }

    public long getDaysUntilWeeklyReset() {
        return ChronoUnit.DAYS.between(Instant.now(), weeklyResetTime);
    }

    // === Enums and Records ===

    public enum GuildCreateResult {
        SUCCESS,
        INVALID_NAME,
        INVALID_TAG,
        NAME_TAKEN,
        TAG_TAKEN,
        ALREADY_IN_GUILD,
        INSUFFICIENT_FUNDS
    }

    public enum GuildInviteResult {
        SUCCESS,
        GUILD_NOT_FOUND,
        NO_PERMISSION,
        GUILD_FULL,
        ALREADY_IN_GUILD,
        ALREADY_INVITED
    }

    public enum GuildRank {
        LEADER(4, "Leader"),
        OFFICER(3, "Officer"),
        VETERAN(2, "Veteran"),
        MEMBER(1, "Member");

        public final int level;
        public final String displayName;

        GuildRank(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }
    }

    public enum ObjectiveType {
        KILLS,
        WAVES,
        BOSS_KILLS,
        TOKENS_EARNED,
        PRESTIGE_EARNED,
        CHALLENGES_COMPLETED,
        STYLE_S_RANKS,
        FLAWLESS_WAVES
    }

    public record GuildObjective(
        String id,
        String name,
        String description,
        ObjectiveType type,
        int target,
        int tokenReward,
        int xpReward
    ) {}

    public enum GuildPerk {
        BONUS_TOKENS_5(5, "Token Boost I", "+5% token gain for members"),
        BONUS_XP_5(7, "XP Boost I", "+5% season XP for members"),
        BONUS_TOKENS_10(10, "Token Boost II", "+10% token gain for members"),
        EXTRA_OBJECTIVE(12, "Extra Objective", "Unlock 4th weekly objective"),
        BONUS_XP_10(15, "XP Boost II", "+10% season XP for members"),
        GUILD_BANK_BONUS(17, "Guild Treasury", "+20% bank capacity"),
        BONUS_TOKENS_15(20, "Token Boost III", "+15% token gain for members");

        public final int level;
        public final String name;
        public final String description;

        GuildPerk(int level, String name, String description) {
            this.level = level;
            this.name = name;
            this.description = description;
        }

        public static GuildPerk getForLevel(int level) {
            for (GuildPerk perk : values()) {
                if (perk.level == level) {
                    return perk;
                }
            }
            return null;
        }
    }
}
