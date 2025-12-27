package com.devmod.endurance.guild;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Guild {

    private final String id;
    private String name;
    private String tag;
    private String description = "";
    private String motd = ""; // Message of the day
    private final Instant createdAt;

    // Leadership
    private UUID leaderId;

    // Members
    private final Map<UUID, GuildMember> members = new ConcurrentHashMap<>();
    private final Set<UUID> pendingInvites = ConcurrentHashMap.newKeySet();

    // Progression
    private int level = 1;
    private int experience = 0;
    private int totalScore = 0; // All-time score
    private int weeklyScore = 0;

    // Bank
    private int bankTokens = 0;
    private int bankPrestige = 0;

    // Weekly objectives
    private final Map<String, Integer> objectiveProgress = new ConcurrentHashMap<>();
    private final Set<String> completedObjectives = ConcurrentHashMap.newKeySet();

    // Unlocked perks
    private final Set<GuildSystem.GuildPerk> unlockedPerks = EnumSet.noneOf(GuildSystem.GuildPerk.class);

    // Stats
    private int totalKills = 0;
    private int totalWaves = 0;
    private int totalBossKills = 0;

    @SuppressWarnings("this-escape") // Founder membership setup in constructor is intentional
    public Guild(String id, String name, String tag, UUID founderId) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderId = founderId;
        this.createdAt = Instant.now();

        // Add founder as leader
        addMember(founderId, GuildSystem.GuildRank.LEADER);
    }

    // === Member Management ===

    public void addMember(UUID playerId, GuildSystem.GuildRank rank) {
        GuildMember member = new GuildMember(playerId, rank, Instant.now());
        members.put(playerId, member);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public boolean hasMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public Optional<GuildMember> getMember(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public int getMemberCount() {
        return members.size();
    }

    public Set<UUID> getMemberIds() {
        return Set.copyOf(members.keySet());
    }

    public Collection<GuildMember> getMembers() {
        return Collections.unmodifiableCollection(members.values());
    }

    // === Ranks and Permissions ===

    public boolean canInvite(UUID playerId) {
        GuildMember member = members.get(playerId);
        if (member == null) return false;
        return member.rank().level >= GuildSystem.GuildRank.OFFICER.level;
    }

    public boolean canKick(UUID playerId) {
        return canInvite(playerId);
    }

    public boolean canPromote(UUID playerId) {
        GuildMember member = members.get(playerId);
        if (member == null) return false;
        return member.rank() == GuildSystem.GuildRank.LEADER;
    }

    public void setMemberRank(UUID playerId, GuildSystem.GuildRank newRank) {
        GuildMember member = members.get(playerId);
        if (member != null) {
            members.put(playerId, new GuildMember(
                playerId, newRank, member.joinedAt(),
                member.weeklyContribution(), member.totalContribution()
            ));
        }
    }

    public void transferLeadership(UUID newLeaderId) {
        if (!hasMember(newLeaderId)) return;

        // Demote old leader to officer
        setMemberRank(leaderId, GuildSystem.GuildRank.OFFICER);

        // Promote new leader
        setMemberRank(newLeaderId, GuildSystem.GuildRank.LEADER);
        leaderId = newLeaderId;
    }

    // === Invites ===

    public void addPendingInvite(UUID playerId, UUID inviterId) {
        pendingInvites.add(playerId);
    }

    public void removePendingInvite(UUID playerId) {
        pendingInvites.remove(playerId);
    }

    public boolean hasPendingInvite(UUID playerId) {
        return pendingInvites.contains(playerId);
    }

    // === Progression ===

    public void addExperience(int amount) {
        experience += amount;
    }

    public void levelUp() {
        level++;
    }

    public void addScore(int amount) {
        weeklyScore += amount;
        totalScore += amount;
    }

    // === Bank ===

    public void addToBank(int tokens) {
        bankTokens += tokens;
    }

    public boolean withdrawFromBank(UUID playerId, int amount) {
        if (!canInvite(playerId)) return false; // Only officers+
        if (bankTokens < amount) return false;

        bankTokens -= amount;
        return true;
    }

    // === Objectives ===

    public int addObjectiveProgress(String objectiveId, int amount) {
        int current = objectiveProgress.getOrDefault(objectiveId, 0);
        int newProgress = current + amount;
        objectiveProgress.put(objectiveId, newProgress);

        // Add to weekly score
        addScore(amount);

        return newProgress;
    }

    public int getObjectiveProgress(String objectiveId) {
        return objectiveProgress.getOrDefault(objectiveId, 0);
    }

    public void markObjectiveComplete(String objectiveId) {
        completedObjectives.add(objectiveId);
    }

    public boolean hasCompletedObjective(String objectiveId) {
        return completedObjectives.contains(objectiveId);
    }

    public void resetWeeklyProgress() {
        weeklyScore = 0;
        objectiveProgress.clear();
        completedObjectives.clear();

        // Reset member weekly contributions
        members.replaceAll((memberId, member) -> new GuildMember(
            memberId, member.rank(), member.joinedAt(),
            0, member.totalContribution()
        ));
    }

    // === Member Contributions ===

    public void addMemberContribution(UUID playerId, GuildSystem.ObjectiveType type, int amount) {
        GuildMember member = members.get(playerId);
        if (member == null) return;

        // Weight contribution by type
        int weighted = switch (type) {
            case KILLS -> amount;
            case WAVES -> amount * 10;
            case BOSS_KILLS -> amount * 50;
            case TOKENS_EARNED -> amount / 10;
            case PRESTIGE_EARNED -> amount * 20;
            case CHALLENGES_COMPLETED -> amount * 100;
            case STYLE_S_RANKS -> amount * 30;
            case FLAWLESS_WAVES -> amount * 40;
        };

        members.put(playerId, new GuildMember(
            playerId, member.rank(), member.joinedAt(),
            member.weeklyContribution() + weighted,
            member.totalContribution() + weighted
        ));

        // Update guild stats
        switch (type) {
            case KILLS -> totalKills += amount;
            case WAVES -> totalWaves += amount;
            case BOSS_KILLS -> totalBossKills += amount;
            default -> {}
        }
    }

    /**
     * Get top contributors this week.
     */
    public List<GuildMember> getTopContributors(int limit) {
        return members.values().stream()
            .sorted((a, b) -> Integer.compare(b.weeklyContribution(), a.weeklyContribution()))
            .limit(limit)
            .toList();
    }

    // === Perks ===

    public void unlockPerk(GuildSystem.GuildPerk perk) {
        unlockedPerks.add(perk);
    }

    public boolean hasPerk(GuildSystem.GuildPerk perk) {
        return unlockedPerks.contains(perk);
    }

    public Set<GuildSystem.GuildPerk> getUnlockedPerks() {
        return EnumSet.copyOf(unlockedPerks);
    }

    /**
     * Get total token bonus from perks.
     */
    public float getTokenBonusMultiplier() {
        float bonus = 1.0f;
        if (hasPerk(GuildSystem.GuildPerk.BONUS_TOKENS_5)) bonus += 0.05f;
        if (hasPerk(GuildSystem.GuildPerk.BONUS_TOKENS_10)) bonus += 0.10f;
        if (hasPerk(GuildSystem.GuildPerk.BONUS_TOKENS_15)) bonus += 0.15f;
        return bonus;
    }

    /**
     * Get total XP bonus from perks.
     */
    public float getXPBonusMultiplier() {
        float bonus = 1.0f;
        if (hasPerk(GuildSystem.GuildPerk.BONUS_XP_5)) bonus += 0.05f;
        if (hasPerk(GuildSystem.GuildPerk.BONUS_XP_10)) bonus += 0.10f;
        return bonus;
    }

    // === Getters/Setters ===

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMotd() { return motd; }
    public void setMotd(String motd) { this.motd = motd; }
    public UUID getLeaderId() { return leaderId; }
    public Instant getCreatedAt() { return createdAt; }
    public int getLevel() { return level; }
    public int getExperience() { return experience; }
    public int getTotalScore() { return totalScore; }
    public int getWeeklyScore() { return weeklyScore; }
    public int getBankTokens() { return bankTokens; }
    public int getBankPrestige() { return bankPrestige; }
    public int getTotalKills() { return totalKills; }
    public int getTotalWaves() { return totalWaves; }
    public int getTotalBossKills() { return totalBossKills; }

    /**
     * Get a summary for display.
     */
    public GuildSummary getSummary() {
        return new GuildSummary(
            id, name, tag, description,
            getMemberCount(), GuildSystem.MAX_GUILD_SIZE,
            level, experience,
            weeklyScore, totalScore,
            bankTokens,
            getTokenBonusMultiplier(),
            getXPBonusMultiplier()
        );
    }

    // === Records ===

    public record GuildMember(
        UUID playerId,
        GuildSystem.GuildRank rank,
        Instant joinedAt,
        int weeklyContribution,
        int totalContribution
    ) {
        public GuildMember(UUID playerId, GuildSystem.GuildRank rank, Instant joinedAt) {
            this(playerId, rank, joinedAt, 0, 0);
        }
    }

    public record GuildSummary(
        String id,
        String name,
        String tag,
        String description,
        int memberCount,
        int maxMembers,
        int level,
        int experience,
        int weeklyScore,
        int totalScore,
        int bankTokens,
        float tokenBonus,
        float xpBonus
    ) {}
}
