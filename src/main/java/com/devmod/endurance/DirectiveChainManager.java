package com.devmod.endurance;

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
public final class DirectiveChainManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectiveChainManager.class);

    public static final DirectiveChainManager INSTANCE = new DirectiveChainManager();

    private final List<DirectiveChain> chainPool = new ArrayList<>();
    private final Map<UUID, DirectiveChain.ChainProgress> activeChains = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private DirectiveChainManager() {
        initializeChainPool();
    }

    /**
     * Initialize all predefined directive chains.
     */
    private void initializeChainPool() {
        // ══════════════════════════════════════════════════════════════
        // HUNT CHAINS - Track and eliminate targets
        // ══════════════════════════════════════════════════════════════

        chainPool.add(new DirectiveChain(
            "nemesis_hunt",
            "Nemesis Hunt",
            "A powerful enemy has been marked. Track it down across waves and eliminate it.",
            DirectiveChain.ChainTheme.HUNT,
            List.of(
                DirectiveChain.ChainStep.of(1, "The Scent",
                    "Scouts report enemy activity. Clear the area to draw out the target.",
                    createDirective("hunt_scout", "Scout Sweep", "Clear scouts to reveal the nemesis.", 1.1f, 0.8f, 1.0f, null, SpawnAffix.RUSH)),
                DirectiveChain.ChainStep.of(2, "Cornered",
                    "The nemesis is nearby. Lieutenants move to protect their leader.",
                    createDirective("hunt_lieutenants", "Elite Guard", "Defeat the elite guards.", 1.2f, 0.9f, 1.0f, null, SpawnAffix.ELITE)),
                DirectiveChain.ChainStep.of(3, "The Kill",
                    "Your nemesis stands alone. End this hunt.",
                    createDirective("hunt_finale", "Nemesis Showdown", "Eliminate the marked nemesis.", 1.4f, 0.7f, 1.0f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.OBJECTIVE_ELITE),
                    1.5f)
            ),
            2000, 20, 1.25f
        ));

        chainPool.add(new DirectiveChain(
            "bounty_collector",
            "Bounty Collector",
            "Multiple high-value targets have appeared. Collect all bounties for maximum reward.",
            DirectiveChain.ChainTheme.HUNT,
            List.of(
                DirectiveChain.ChainStep.of(1, "First Bounty",
                    "The first target is spotted. Quick and clean.",
                    createDirective("bounty_1", "First Mark", "Eliminate the first bounty.", 1.15f, 0.9f, 1.0f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.ELITE)),
                DirectiveChain.ChainStep.of(2, "Second Bounty",
                    "Word spreads. The second target brings reinforcements.",
                    createDirective("bounty_2", "Second Mark", "Eliminate the second bounty.", 1.2f, 1.0f, 1.0f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.BRUTE)),
                DirectiveChain.ChainStep.of(3, "Third Bounty",
                    "The final target knows you're coming. Be ready for anything.",
                    createDirective("bounty_3", "Final Mark", "Eliminate the final bounty.", 1.3f, 1.1f, 0.9f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.OBJECTIVE_ELITE)),
                DirectiveChain.ChainStep.conditional(4, "Bonus Bounty",
                    "A secret fourth target emerges. Only the best hunters find this one.",
                    createDirective("bounty_secret", "Hidden Mark", "Eliminate the secret bounty.", 1.5f, 0.8f, 1.0f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.OBJECTIVE_ELITE),
                    DirectiveChain.ChainCondition.noDeath())
            ),
            3000, 30, 1.35f
        ));

        // ══════════════════════════════════════════════════════════════
        // SIEGE CHAINS - Defend against overwhelming force
        // ══════════════════════════════════════════════════════════════

        chainPool.add(new DirectiveChain(
            "fortress_siege",
            "Fortress Siege",
            "The enemy masses for a coordinated assault. Hold your ground at all costs.",
            DirectiveChain.ChainTheme.SIEGE,
            List.of(
                DirectiveChain.ChainStep.of(1, "First Wave",
                    "The vanguard arrives. Test your defenses.",
                    createDirective("siege_vanguard", "Vanguard", "Repel the vanguard assault.", 1.1f, 1.1f, 1.0f, null, SpawnAffix.RUSH)),
                DirectiveChain.ChainStep.of(2, "Main Assault",
                    "The main force attacks. Hold the center!",
                    createDirective("siege_main", "Main Force", "Hold the center under heavy fire.", 1.25f, 1.2f, 0.9f, WaveObjectiveState.Type.HOLD_ZONE, SpawnAffix.BRUTE)),
                DirectiveChain.ChainStep.of(3, "Elite Strike",
                    "Enemy commanders lead a precision strike.",
                    createDirective("siege_elite", "Elite Strike", "Survive the elite commander assault.", 1.35f, 1.0f, 0.85f, WaveObjectiveState.Type.SURVIVE_TIME, SpawnAffix.ELITE)),
                DirectiveChain.ChainStep.of(4, "Final Push",
                    "The enemy throws everything at you. This is the last stand.",
                    createDirective("siege_final", "Final Stand", "Survive the final desperate assault.", 1.5f, 1.3f, 0.75f, WaveObjectiveState.Type.HOLD_ZONE, SpawnAffix.BRUTE),
                    1.75f)
            ),
            2500, 25, 1.3f
        ));

        chainPool.add(new DirectiveChain(
            "bunker_defense",
            "Bunker Defense",
            "Fortify and hold a strategic position while waves crash against your walls.",
            DirectiveChain.ChainTheme.SIEGE,
            List.of(
                DirectiveChain.ChainStep.of(1, "Establish Position",
                    "Clear the area and establish your defensive position.",
                    createDirective("bunker_clear", "Area Clear", "Secure the bunker zone.", 1.1f, 0.9f, 1.0f, WaveObjectiveState.Type.HOLD_ZONE, null)),
                DirectiveChain.ChainStep.of(2, "Defend",
                    "The enemy has found you. Defend your position!",
                    createDirective("bunker_defend", "Hold Position", "Defend against coordinated assault.", 1.2f, 1.15f, 0.9f, WaveObjectiveState.Type.HOLD_ZONE, SpawnAffix.RUSH)),
                DirectiveChain.ChainStep.of(3, "Reinforcements",
                    "Enemy reinforcements arrive. Stay strong!",
                    createDirective("bunker_reinforce", "Under Siege", "Hold against overwhelming numbers.", 1.35f, 1.25f, 0.85f, WaveObjectiveState.Type.HOLD_ZONE, SpawnAffix.BRUTE),
                    1.4f)
            ),
            1800, 18, 1.2f
        ));

        // ══════════════════════════════════════════════════════════════
        // GAUNTLET CHAINS - Face elite challenges
        // ══════════════════════════════════════════════════════════════

        chainPool.add(new DirectiveChain(
            "elite_gauntlet",
            "Elite Gauntlet",
            "A succession of elite warriors challenges you. Each stronger than the last.",
            DirectiveChain.ChainTheme.GAUNTLET,
            List.of(
                DirectiveChain.ChainStep.of(1, "First Trial",
                    "The first elite tests your mettle.",
                    createDirective("gauntlet_1", "First Champion", "Defeat the first elite champion.", 1.15f, 0.8f, 1.0f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.ELITE)),
                DirectiveChain.ChainStep.of(2, "Second Trial",
                    "A stronger challenger emerges.",
                    createDirective("gauntlet_2", "Second Champion", "Defeat the second elite champion.", 1.25f, 0.85f, 1.0f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.ELITE)),
                DirectiveChain.ChainStep.conditional(3, "Final Trial",
                    "The ultimate champion awaits. Prove your worth.",
                    createDirective("gauntlet_3", "Final Champion", "Defeat the final elite champion.", 1.4f, 0.9f, 1.0f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.OBJECTIVE_ELITE),
                    DirectiveChain.ChainCondition.minCombo(25))
            ),
            2200, 22, 1.3f
        ));

        chainPool.add(new DirectiveChain(
            "proving_grounds",
            "Proving Grounds",
            "Enter the arena where legends are made. Style and skill are your weapons.",
            DirectiveChain.ChainTheme.GAUNTLET,
            List.of(
                DirectiveChain.ChainStep.of(1, "Initiation",
                    "Show them what you've got. Build your momentum.",
                    createDirective("prove_init", "Opening Act", "Demonstrate your skills.", 1.1f, 1.0f, 1.0f, null, SpawnAffix.BASE)),
                DirectiveChain.ChainStep.conditional(2, "Rising Star",
                    "The crowd demands more. Elevate your performance.",
                    createDirective("prove_rise", "Crowd Pleaser", "Reach style rank A or higher.", 1.25f, 1.05f, 0.95f, null, SpawnAffix.RUSH),
                    DirectiveChain.ChainCondition.minRank(4)), // A rank
                DirectiveChain.ChainStep.conditional(3, "Legendary Performance",
                    "Become a legend. Achieve perfection.",
                    createDirective("prove_legend", "Legendary Act", "Reach style rank S and maintain it.", 1.5f, 1.1f, 0.9f, null, SpawnAffix.ELITE),
                    DirectiveChain.ChainCondition.minRank(5)), // S rank
                DirectiveChain.ChainStep.conditional(4, "Encore",
                    "The crowd won't let you leave. One more spectacular show.",
                    createDirective("prove_encore", "Encore", "Deliver a flawless finale.", 1.75f, 1.15f, 0.85f, WaveObjectiveState.Type.SURVIVE_TIME, SpawnAffix.BRUTE),
                    DirectiveChain.ChainCondition.flawless())
            ),
            3500, 35, 1.4f
        ));

        // ══════════════════════════════════════════════════════════════
        // SWARM CHAINS - Survive escalating waves
        // ══════════════════════════════════════════════════════════════

        chainPool.add(new DirectiveChain(
            "rising_tide",
            "Rising Tide",
            "The enemy numbers grow with each wave. Can you stem the tide?",
            DirectiveChain.ChainTheme.SWARM,
            List.of(
                DirectiveChain.ChainStep.of(1, "Ripple",
                    "Small numbers, but something bigger is coming.",
                    createDirective("tide_ripple", "First Ripple", "Handle the initial swarm.", 1.1f, 0.9f, 1.1f, null, SpawnAffix.BASE)),
                DirectiveChain.ChainStep.of(2, "Wave",
                    "The numbers swell. Stay focused.",
                    createDirective("tide_wave", "Rising Wave", "Survive the growing swarm.", 1.2f, 1.1f, 1.0f, null, SpawnAffix.RUSH)),
                DirectiveChain.ChainStep.of(3, "Surge",
                    "A massive surge of enemies! Fight for survival!",
                    createDirective("tide_surge", "Tidal Surge", "Survive the overwhelming surge.", 1.35f, 1.25f, 0.85f, WaveObjectiveState.Type.SURVIVE_TIME, SpawnAffix.RUSH)),
                DirectiveChain.ChainStep.of(4, "Tsunami",
                    "The final wave crashes down. Everything or nothing.",
                    createDirective("tide_tsunami", "Tsunami", "Survive the tsunami of enemies.", 1.5f, 1.4f, 0.75f, WaveObjectiveState.Type.SURVIVE_TIME, SpawnAffix.RUSH),
                    1.6f)
            ),
            2000, 20, 1.25f
        ));

        chainPool.add(new DirectiveChain(
            "horde_night",
            "Horde Night",
            "They come in endless waves under cover of darkness. Survive until dawn.",
            DirectiveChain.ChainTheme.SWARM,
            List.of(
                DirectiveChain.ChainStep.of(1, "Dusk",
                    "The first wave emerges as darkness falls.",
                    createDirective("horde_dusk", "Twilight Assault", "Fight off the dusk wave.", 1.1f, 1.05f, 1.0f, null, SpawnAffix.BASE)),
                DirectiveChain.ChainStep.of(2, "Midnight",
                    "The deepest darkness brings the worst horrors.",
                    createDirective("horde_midnight", "Midnight Horde", "Survive the midnight onslaught.", 1.25f, 1.2f, 0.9f, null, SpawnAffix.BRUTE)),
                DirectiveChain.ChainStep.of(3, "Dawn",
                    "Hold on! Dawn approaches. One final push!",
                    createDirective("horde_dawn", "Dawn's Light", "Survive until dawn breaks.", 1.4f, 1.3f, 0.85f, WaveObjectiveState.Type.SURVIVE_TIME, SpawnAffix.RUSH),
                    1.5f)
            ),
            1800, 18, 1.2f
        ));

        // ══════════════════════════════════════════════════════════════
        // TACTICAL CHAINS - Strategic objective sequences
        // ══════════════════════════════════════════════════════════════

        chainPool.add(new DirectiveChain(
            "strategic_assault",
            "Strategic Assault",
            "Execute a multi-phase tactical operation with precision.",
            DirectiveChain.ChainTheme.TACTICAL,
            List.of(
                DirectiveChain.ChainStep.of(1, "Reconnaissance",
                    "Scout the enemy positions. Gather intelligence.",
                    createDirective("tact_recon", "Recon Phase", "Clear enemy scouts.", 1.1f, 0.75f, 1.1f, null, SpawnAffix.SNIPER)),
                DirectiveChain.ChainStep.of(2, "Neutralize Sentries",
                    "Eliminate enemy sentries to open the path.",
                    createDirective("tact_sentries", "Sentry Elimination", "Take out enemy sentries.", 1.2f, 0.85f, 1.0f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.SNIPER)),
                DirectiveChain.ChainStep.of(3, "Secure Objective",
                    "The objective is exposed. Move in and secure it.",
                    createDirective("tact_secure", "Objective Secured", "Hold the objective zone.", 1.35f, 1.0f, 0.95f, WaveObjectiveState.Type.HOLD_ZONE, SpawnAffix.RUSH)),
                DirectiveChain.ChainStep.of(4, "Extraction",
                    "Mission complete. Hold for extraction.",
                    createDirective("tact_extract", "Extraction", "Survive until extraction.", 1.4f, 1.1f, 0.9f, WaveObjectiveState.Type.SURVIVE_TIME, SpawnAffix.BRUTE),
                    1.45f)
            ),
            2200, 22, 1.28f
        ));

        // ══════════════════════════════════════════════════════════════
        // NIGHTMARE CHAINS - Extreme difficulty
        // ══════════════════════════════════════════════════════════════

        chainPool.add(new DirectiveChain(
            "hell_walk",
            "Hell Walk",
            "Enter the crucible. Only the strongest emerge.",
            DirectiveChain.ChainTheme.NIGHTMARE,
            List.of(
                DirectiveChain.ChainStep.of(1, "Gate of Fire",
                    "The first trial begins. Flames await.",
                    createDirective("hell_fire", "Inferno Gate", "Survive the fire wave.", 1.3f, 1.2f, 0.85f, WaveObjectiveState.Type.SURVIVE_TIME, SpawnAffix.BRUTE)),
                DirectiveChain.ChainStep.conditional(2, "Gauntlet of Pain",
                    "No rest. The gauntlet continues.",
                    createDirective("hell_pain", "Pain Gauntlet", "Fight through the pain.", 1.4f, 1.25f, 0.8f, null, SpawnAffix.ELITE),
                    DirectiveChain.ChainCondition.noDeath()),
                DirectiveChain.ChainStep.conditional(3, "Chamber of Despair",
                    "Your will is tested. Do not falter.",
                    createDirective("hell_despair", "Despair Chamber", "Maintain your composure under pressure.", 1.5f, 1.3f, 0.75f, WaveObjectiveState.Type.HOLD_ZONE, SpawnAffix.BRUTE),
                    DirectiveChain.ChainCondition.minCombo(50)),
                DirectiveChain.ChainStep.conditional(4, "The Abyss",
                    "Face the abyss itself. Perfection or oblivion.",
                    createDirective("hell_abyss", "Abyss Walker", "Survive the final nightmare.", 1.75f, 1.4f, 0.7f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.OBJECTIVE_ELITE),
                    DirectiveChain.ChainCondition.flawless()),
                DirectiveChain.ChainStep.conditional(5, "Transcendence",
                    "You have walked through hell. Become legend.",
                    createDirective("hell_transcend", "Transcendence", "Complete the ultimate trial.", 2.0f, 1.5f, 0.65f, WaveObjectiveState.Type.ELITE_HUNT, SpawnAffix.OBJECTIVE_ELITE),
                    DirectiveChain.ChainCondition.minRank(6)) // SS rank
            ),
            5000, 50, 1.5f
        ));

        LOGGER.info("[DirectiveChainManager] Initialized {} directive chains", chainPool.size());
    }

    private WaveDirective createDirective(String id, String name, String description,
                                           float rewardMult, float spawnMult, float intervalMult,
                                           @javax.annotation.Nullable WaveObjectiveState.Type objective,
                                           @javax.annotation.Nullable SpawnAffix affix) {
        return new WaveDirective(id, name, description, rewardMult, spawnMult, intervalMult, objective, affix);
    }

    // ══════════════════════════════════════════════════════════════════
    // CHAIN SELECTION AND MANAGEMENT
    // ══════════════════════════════════════════════════════════════════

    /**
     * Get available chains for selection at a given wave.
     */
    public List<DirectiveChain> getAvailableChains(int waveNumber) {
        List<DirectiveChain> available = new ArrayList<>();

        for (DirectiveChain chain : chainPool) {
            if (isChainAvailable(chain, waveNumber)) {
                available.add(chain);
            }
        }

        return available;
    }

    /**
     * Roll random chain choices for a player to select.
     */
    public List<DirectiveChain> rollChainChoices(int waveNumber, int count) {
        List<DirectiveChain> available = getAvailableChains(waveNumber);
        if (available.isEmpty()) return List.of();

        Collections.shuffle(available, random);
        return available.subList(0, Math.min(count, available.size()));
    }

    /**
     * Check if a chain is available at the given wave number.
     */
    private boolean isChainAvailable(DirectiveChain chain, int waveNumber) {
        // Early waves can only have shorter chains
        if (waveNumber <= 3 && chain.steps().size() > 3) {
            return false;
        }

        // Nightmare chains require wave 5+
        if (chain.theme() == DirectiveChain.ChainTheme.NIGHTMARE && waveNumber < 5) {
            return false;
        }

        // Make sure there's room for the full chain
        // (This check would need quest context in a real implementation)
        return true;
    }

    // ══════════════════════════════════════════════════════════════════
    // ACTIVE CHAIN TRACKING
    // ══════════════════════════════════════════════════════════════════

    /**
     * Start a new chain for a quest.
     */
    public DirectiveChain.ChainProgress startChain(UUID questId, DirectiveChain chain) {
        DirectiveChain.ChainProgress progress = new DirectiveChain.ChainProgress(questId, chain);
        activeChains.put(questId, progress);
        LOGGER.info("[DirectiveChainManager] Started chain '{}' for quest {}", chain.name(), questId);
        return progress;
    }

    /**
     * Get active chain progress for a quest.
     */
    public Optional<DirectiveChain.ChainProgress> getActiveChain(UUID questId) {
        return Optional.ofNullable(activeChains.get(questId));
    }

    /**
     * Check if a quest has an active chain.
     */
    public boolean hasActiveChain(UUID questId) {
        DirectiveChain.ChainProgress progress = activeChains.get(questId);
        return progress != null && !progress.isCompleted() && !progress.isFailed();
    }

    /**
     * Advance chain progress after a wave completion.
     */
    public ChainAdvanceResult advanceChain(UUID questId, int waveKills, int maxCombo,
                                            boolean playerDied, boolean tookDamage, int styleRankOrdinal) {
        DirectiveChain.ChainProgress progress = activeChains.get(questId);
        if (progress == null || progress.isCompleted() || progress.isFailed()) {
            return ChainAdvanceResult.NO_CHAIN;
        }

        // Check condition for next step
        DirectiveChain.ChainStep currentStep = progress.getCurrentChainStep();
        if (currentStep != null && currentStep.condition() != null) {
            if (!progress.checkCondition(currentStep.condition(), waveKills, maxCombo,
                    playerDied, tookDamage, styleRankOrdinal)) {
                progress.markFailed();
                LOGGER.info("[DirectiveChainManager] Chain '{}' failed condition check for quest {}",
                    progress.getChain().name(), questId);
                return ChainAdvanceResult.CONDITION_FAILED;
            }
        }

        boolean wasFlawless = !tookDamage;
        progress.advanceStep(waveKills, maxCombo, wasFlawless);

        if (progress.isCompleted()) {
            LOGGER.info("[DirectiveChainManager] Chain '{}' COMPLETED for quest {}", progress.getChain().name(), questId);
            return ChainAdvanceResult.CHAIN_COMPLETED;
        }

        LOGGER.info("[DirectiveChainManager] Chain '{}' advanced to step {} for quest {}",
            progress.getChain().name(), progress.getCurrentStep() + 1, questId);
        return ChainAdvanceResult.STEP_COMPLETED;
    }

    /**
     * Abandon an active chain (player chose to exit or skip).
     */
    public void abandonChain(UUID questId) {
        DirectiveChain.ChainProgress progress = activeChains.get(questId);
        if (progress != null) {
            progress.markFailed();
            LOGGER.info("[DirectiveChainManager] Chain '{}' abandoned for quest {}",
                progress.getChain().name(), questId);
        }
    }

    /**
     * Remove chain tracking when quest ends.
     */
    public void endChain(UUID questId) {
        activeChains.remove(questId);
    }

    /**
     * Get the current directive for an active chain.
     */
    public Optional<WaveDirective> getCurrentChainDirective(UUID questId) {
        DirectiveChain.ChainProgress progress = activeChains.get(questId);
        if (progress == null || progress.isCompleted() || progress.isFailed()) {
            return Optional.empty();
        }

        DirectiveChain.ChainStep step = progress.getCurrentChainStep();
        return step != null ? Optional.of(step.directive()) : Optional.empty();
    }

    /**
     * Calculate total chain bonus rewards.
     */
    public ChainRewards calculateChainRewards(DirectiveChain.ChainProgress progress) {
        if (!progress.isCompleted()) {
            return new ChainRewards(0, 0, 1.0f, false);
        }

        DirectiveChain chain = progress.getChain();
        int tokens = chain.bonusTokens();
        int prestige = chain.bonusPrestige();
        float multiplier = chain.completionMultiplier();

        // Bonus for flawless steps
        if (progress.getStepsCompletedFlawlessly() == chain.steps().size()) {
            tokens = (int)(tokens * 1.5f);
            prestige = (int)(prestige * 1.5f);
        }

        return new ChainRewards(tokens, prestige, multiplier, true);
    }

    // ══════════════════════════════════════════════════════════════════
    // RESULT TYPES
    // ══════════════════════════════════════════════════════════════════

    public enum ChainAdvanceResult {
        NO_CHAIN,           // No active chain
        STEP_COMPLETED,     // Step completed, chain continues
        CHAIN_COMPLETED,    // Final step completed
        CONDITION_FAILED    // Failed condition check
    }

    public record ChainRewards(
        int bonusTokens,
        int bonusPrestige,
        float rewardMultiplier,
        boolean completed
    ) {}

    // ══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Get all chains in the pool.
     */
    public List<DirectiveChain> getAllChains() {
        return Collections.unmodifiableList(chainPool);
    }

    /**
     * Find a chain by ID.
     */
    public Optional<DirectiveChain> findChain(String chainId) {
        return chainPool.stream()
            .filter(c -> c.id().equals(chainId))
            .findFirst();
    }

    /**
     * Get chains by theme.
     */
    public List<DirectiveChain> getChainsByTheme(DirectiveChain.ChainTheme theme) {
        return chainPool.stream()
            .filter(c -> c.theme() == theme)
            .toList();
    }
}
