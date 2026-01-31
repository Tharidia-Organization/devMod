package com.devmod.endurance.bargain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import com.devmod.endurance.lifecycle.QuestContext;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestStarted;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;
public class DevilsBargainManager implements QuestLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevilsBargainManager.class);

    public static final DevilsBargainManager INSTANCE = new DevilsBargainManager();

    // Configuration
    private static final int ALTAR_WAVE_INTERVAL = 3; // Every 3 waves
    private static final int CURSES_OFFERED = 3;       // 3 choices per altar
    private static final int MAX_CURSES = 10;          // Cap on total curses

    // Active curse sessions per quest
    private final Map<UUID, CurseSession> sessions = new ConcurrentHashMap<>();

    /**
     * Tracks active curses for a quest session.
     */
    public static class CurseSession {
        private final UUID questId;
        private final List<Curse> activeCurses = new ArrayList<>();
        private final List<Curse> offeredCurses = new ArrayList<>();
        private boolean altarActive = false;
        private int lastAltarWave = 0;

        // Cached multipliers (recalculated when curses change)
        private float totalRewardMultiplier = 1.0f;
        private float damageMultiplier = 1.0f;
        private float damageTakenMultiplier = 1.0f;
        private float speedMultiplier = 1.0f;
        private float attackSpeedMultiplier = 1.0f;
        private float maxHealthMultiplier = 1.0f;
        private float spawnRateMultiplier = 1.0f;
        private boolean hasLifesteal = false;
        private float lifestealPercent = 0f;
        private int selfDamagePerKill = 0;

        public CurseSession(UUID questId) {
            this.questId = requireQuestId(questId);
        }

        /**
         * Add a curse and recalculate modifiers.
         */
        public boolean addCurse(Curse curse) {
            if (activeCurses.size() >= MAX_CURSES) return false;
            if (activeCurses.contains(curse)) return false;

            // Check for conflicts
            for (Curse active : activeCurses) {
                if (active.conflictsWith(curse)) {
                    LOGGER.debug("[Bargain] Curse {} conflicts with {}", curse, active);
                    return false;
                }
            }

            activeCurses.add(curse);
            recalculateModifiers();

            LOGGER.info("[Bargain] Quest {} added curse: {} (total: {}, reward: {}x)",
                questId, curse.getDisplayName(), activeCurses.size(), totalRewardMultiplier);

            return true;
        }

        /**
         * Recalculate all modifier values based on active curses.
         */
        private void recalculateModifiers() {
            // Reset to defaults
            totalRewardMultiplier = 1.0f;
            damageMultiplier = 1.0f;
            damageTakenMultiplier = 1.0f;
            speedMultiplier = 1.0f;
            attackSpeedMultiplier = 1.0f;
            maxHealthMultiplier = 1.0f;
            spawnRateMultiplier = 1.0f;
            hasLifesteal = false;
            lifestealPercent = 0f;
            selfDamagePerKill = 0;

            for (Curse curse : activeCurses) {
                // Multiply reward
                totalRewardMultiplier *= curse.getRewardMultiplier();

                // Apply curse-specific effects
                switch (curse) {
                    case GLASS_CANNON -> {
                        damageTakenMultiplier *= 1.5f;
                        damageMultiplier *= 1.25f;
                    }
                    case SLUGGISH -> speedMultiplier *= 0.8f;
                    case FUMBLING -> attackSpeedMultiplier *= 0.85f;
                    case BLOOD_TITHE -> {
                        hasLifesteal = true;
                        lifestealPercent += 0.02f;
                        selfDamagePerKill += 1;
                    }
                    case CROWD_PRESSURE -> spawnRateMultiplier *= 1.3f;
                    case FRAILTY -> maxHealthMultiplier *= 0.7f;
                    case ELITE_HUNTER -> {
                        // Handled in spawn logic
                    }
                    default -> {
                        // Other curses are handled elsewhere (tick effects, etc.)
                    }
                }
            }
        }

        // Getters
        public List<Curse> getActiveCurses() { return Collections.unmodifiableList(activeCurses); }
        public List<Curse> getOfferedCurses() { return Collections.unmodifiableList(offeredCurses); }
        public boolean isAltarActive() { return altarActive; }
        public int getCurseCount() { return activeCurses.size(); }
        public float getTotalRewardMultiplier() { return totalRewardMultiplier; }
        public float getDamageMultiplier() { return damageMultiplier; }
        public float getDamageTakenMultiplier() { return damageTakenMultiplier; }
        public float getSpeedMultiplier() { return speedMultiplier; }
        public float getAttackSpeedMultiplier() { return attackSpeedMultiplier; }
        public float getMaxHealthMultiplier() { return maxHealthMultiplier; }
        public float getSpawnRateMultiplier() { return spawnRateMultiplier; }
        public boolean hasLifesteal() { return hasLifesteal; }
        public float getLifestealPercent() { return lifestealPercent; }
        public int getSelfDamagePerKill() { return selfDamagePerKill; }

        public boolean hasCurse(Curse curse) { return activeCurses.contains(curse); }

        void setAltarActive(boolean active) { this.altarActive = active; }
        void setLastAltarWave(int wave) { this.lastAltarWave = wave; }
        int getLastAltarWave() { return lastAltarWave; }
        void setOfferedCurses(List<Curse> curses) {
            offeredCurses.clear();
            offeredCurses.addAll(curses);
        }
    }

    /**
     * Start a curse session for a quest.
     */
    public void startSession(UUID questId) {
        UUID safeQuestId = requireQuestId(questId);
        sessions.put(safeQuestId, new CurseSession(safeQuestId));
        LOGGER.debug("[Bargain] Started session for quest {}", safeQuestId);
    }

    /**
     * End a curse session.
     */
    public CurseSession endSession(UUID questId) {
        UUID safeQuestId = requireQuestId(questId);
        CurseSession session = sessions.remove(safeQuestId);
        if (session != null) {
            LOGGER.info("[Bargain] Ended session for quest {} - {} curses, {}x reward",
                session.questId, session.getCurseCount(), session.getTotalRewardMultiplier());
        }
        return session;
    }

    /**
     * Get session for a quest.
     */
    public Optional<CurseSession> getSession(UUID questId) {
        return Optional.ofNullable(sessions.get(requireQuestId(questId)));
    }

    /**
     * Check if an altar should spawn at this wave.
     */
    public boolean shouldSpawnAltar(int waveNumber) {
        return waveNumber > 0 && waveNumber % ALTAR_WAVE_INTERVAL == 0;
    }

    /**
     * Spawn altar and offer curses to player.
     */
    public void spawnAltar(ServerPlayer player, UUID questId, int waveNumber) {
        CurseSession session = sessions.get(requireQuestId(questId));
        if (session == null) return;

        // Don't spawn if max curses reached
        if (session.getCurseCount() >= MAX_CURSES) {
            LOGGER.debug("[Bargain] Max curses reached, skipping altar");
            return;
        }

        // Generate curse offerings
        List<Curse> offerings = generateOfferings(session);
        session.setOfferedCurses(offerings);
        session.setAltarActive(true);
        session.setLastAltarWave(waveNumber);

        // Visual/audio feedback
        if (player.level() instanceof ServerLevel level) {
            BlockPos pos = resolveBlockPos(player);

            playSound(level, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 0.5f);

            // Ominous particles
            for (int i = 0; i < 30; i++) {
                double x = pos.getX() + (level.random.nextDouble() - 0.5) * 4;
                double y = pos.getY() + level.random.nextDouble() * 2;
                double z = pos.getZ() + (level.random.nextDouble() - 0.5) * 4;
                sendParticles(level, ParticleTypes.SOUL, x, y, z, 1, 0, 0.1, 0, 0.02);
            }
        }

        // Send offerings to player
        StringBuilder message = new StringBuilder("\u00A75\u00A7l⛧ DEVIL'S BARGAIN ⛧\u00A7r\n");
        message.append("\u00A77Choose a curse for greater rewards:\n");
        for (int i = 0; i < offerings.size(); i++) {
            Curse curse = offerings.get(i);
            message.append(String.format("\u00A77[%d] %s \u00A77- %s \u00A7e(%.2fx)%n",
                i + 1, curse.getFormattedName(), curse.getDescription(), curse.getRewardMultiplier()));
        }
        message.append("\u00A78Skip with /bargain skip");

        sendMessage(player, message.toString());

        LOGGER.info("[Bargain] Altar spawned for {} at wave {} with {} offerings",
            player.getName().getString(), waveNumber, offerings.size());
    }

    /**
     * Generate curse offerings based on session state.
     */
    private List<Curse> generateOfferings(CurseSession session) {
        List<Curse> available = new ArrayList<>();
        Random random = new Random();

        // Get all curses not already active and not conflicting
        for (Curse curse : Curse.values()) {
            if (session.hasCurse(curse)) continue;

            boolean conflicts = false;
            for (Curse active : session.getActiveCurses()) {
                if (curse.conflictsWith(active)) {
                    conflicts = true;
                    break;
                }
            }
            if (!conflicts) {
                available.add(curse);
            }
        }

        // Weight by tier (more minor curses early, more cursed curses late)
        int curseCount = session.getCurseCount();
        List<Curse> weighted = new ArrayList<>();

        for (Curse curse : available) {
            int weight = switch (curse.getTier()) {
                case MINOR -> Math.max(1, 3 - curseCount / 2);
                case MAJOR -> 2;
                case CURSED -> Math.min(3, 1 + curseCount / 3);
            };
            for (int i = 0; i < weight; i++) {
                weighted.add(curse);
            }
        }

        // Shuffle and pick
        Collections.shuffle(weighted, random);
        List<Curse> offerings = new ArrayList<>();
        Set<Curse> seen = new HashSet<>();

        for (Curse curse : weighted) {
            if (offerings.size() >= CURSES_OFFERED) break;
            if (seen.add(curse)) {
                offerings.add(curse);
            }
        }

        return offerings;
    }

    /**
     * Player accepts a curse from the altar.
     */
    public boolean acceptCurse(ServerPlayer player, UUID questId, int curseIndex) {
        CurseSession session = sessions.get(requireQuestId(questId));
        if (session == null || !session.isAltarActive()) {
            sendMessage(player, "\u00A7cNo altar active!");
            return false;
        }

        List<Curse> offered = session.getOfferedCurses();
        if (curseIndex < 0 || curseIndex >= offered.size()) {
            sendMessage(player, "\u00A7cInvalid curse selection!");
            return false;
        }

        Curse curse = offered.get(curseIndex);
        if (!session.addCurse(curse)) {
            sendMessage(player, "\u00A7cCannot accept this curse!");
            return false;
        }

        // Apply immediate effects
        applyCurseEffects(player, curse);

        // Track curse acceptance for hidden perk discoveries
        com.devmod.endurance.perk.PerkSynergyWeb.INSTANCE.recordCurseAccepted(player);

        // Close altar
        session.setAltarActive(false);

        // Feedback
        if (player.level() instanceof ServerLevel level) {
            playSound(level, resolveBlockPos(player), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5f, 1.5f);

            for (int i = 0; i < 50; i++) {
                double x = player.getX() + (level.random.nextDouble() - 0.5) * 2;
                double y = player.getY() + level.random.nextDouble() * 2;
                double z = player.getZ() + (level.random.nextDouble() - 0.5) * 2;
                sendParticles(level, ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0.1, 0, 0.05);
            }
        }

        sendMessage(player, String.format("\u00A75⛧ %s \u00A77accepted! Total reward: \u00A7e%.2fx",
            curse.getFormattedName(), session.getTotalRewardMultiplier()));

        return true;
    }

    /**
     * Player skips the altar.
     */
    public void skipAltar(ServerPlayer player, UUID questId) {
        CurseSession session = sessions.get(requireQuestId(questId));
        if (session == null || !session.isAltarActive()) {
            return;
        }

        session.setAltarActive(false);

        sendMessage(player, "\u00A77You resist the bargain... for now.");

        if (player.level() instanceof ServerLevel level) {
            playSound(level, resolveBlockPos(player), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    /**
     * Apply immediate effects when a curse is accepted.
     */
    private void applyCurseEffects(ServerPlayer player, Curse curse) {
        switch (curse) {
            case FRAILTY -> {
                // Reduce max health immediately
                float reduction = player.getMaxHealth() * 0.3f;
                var healthAttr = player.getAttribute(Objects.requireNonNull(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH));
                if (healthAttr != null) {
                    healthAttr.setBaseValue(Math.max(4, healthAttr.getBaseValue() - reduction));
                    if (player.getHealth() > player.getMaxHealth()) {
                        player.setHealth(player.getMaxHealth());
                    }
                }
            }
            case SLUGGISH -> {
                // Apply permanent slowness effect
                if (MobEffects.MOVEMENT_SLOWDOWN != null) {
                    player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 0, false, false));
                }
            }
            case FUMBLING -> {
                // Apply mining fatigue for attack speed reduction
                if (MobEffects.DIG_SLOWDOWN != null) {
                    player.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SLOWDOWN, Integer.MAX_VALUE, 0, false, false));
                }
            }
            default -> {
                // Other curses are applied via modifiers or tick effects
            }
        }

        // Store in player data for persistence
        CompoundTag data = player.getPersistentData();
        String curseKey = "bargain_curse_" + curse.name();
        data.putBoolean(curseKey, true);
    }

    /**
     * Tick curse effects for a player.
     * Call this from the server tick handler.
     */
    public void tickPlayer(ServerPlayer player, UUID questId) {
        CurseSession session = sessions.get(requireQuestId(questId));
        if (session == null) return;

        long gameTime = player.level().getGameTime();

        // Hunger curse - drain hunger every 10 seconds (200 ticks)
        if (session.hasCurse(Curse.HUNGER) && gameTime % 200 == 0) {
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 1));
        }

        // Burning Soul - ignite every 30 seconds (600 ticks)
        if (session.hasCurse(Curse.BURNING_SOUL) && gameTime % 600 == 0) {
            player.igniteForSeconds(5);
            sendMessage(player, "\u00A7c\u00A7oYour soul burns...");
        }

        // Visual indicator for cursed players (occasional particles)
        if (!session.getActiveCurses().isEmpty() && gameTime % 40 == 0) {
            if (player.level() instanceof ServerLevel level) {
                sendParticles(level, ParticleTypes.SOUL,
                    player.getX(), player.getY() + 1, player.getZ(),
                    1, 0.3, 0.5, 0.3, 0.01);
            }
        }
    }

    /**
     * Apply on-kill effects for curses.
     */
    public void onKill(ServerPlayer player, UUID questId) {
        CurseSession session = sessions.get(requireQuestId(questId));
        if (session == null) return;

        // Blood Tithe - lose HP on kill (use generic() to respect armor instead of magic())
        int selfDamage = session.getSelfDamagePerKill();
        if (selfDamage > 0) {
            var genericSource = player.damageSources().generic();
            if (genericSource != null) {
                player.hurt(genericSource, selfDamage);
            }
        }
    }

    /**
     * Apply on-damage effects for curses.
     */
    public float modifyDamageDealt(UUID questId, float damage) {
        CurseSession session = sessions.get(requireQuestId(questId));
        if (session == null) return damage;

        float modified = damage * session.getDamageMultiplier();

        // Lifesteal
        if (session.hasLifesteal() && session.getLifestealPercent() > 0) {
            // Lifesteal is handled separately when damage is confirmed
        }

        return modified;
    }

    /**
     * Apply on-damage-taken effects for curses.
     */
    public float modifyDamageTaken(UUID questId, float damage) {
        CurseSession session = sessions.get(requireQuestId(questId));
        if (session == null) return damage;

        float modified = damage * session.getDamageTakenMultiplier();

        // Echo damage
        if (session.hasCurse(Curse.ECHO_DAMAGE)) {
            // Self-damage is applied after the main damage
        }

        return modified;
    }

    /**
     * Get spawn rate multiplier for curses.
     */
    public float getSpawnRateMultiplier(UUID questId) {
        CurseSession session = sessions.get(requireQuestId(questId));
        return session != null ? session.getSpawnRateMultiplier() : 1.0f;
    }

    /**
     * Check if mobs should be elite (ELITE_HUNTER curse).
     */
    public boolean shouldSpawnElite(UUID questId) {
        CurseSession session = sessions.get(requireQuestId(questId));
        return session != null && session.hasCurse(Curse.ELITE_HUNTER);
    }

    /**
     * Get total reward multiplier for a quest.
     */
    public float getRewardMultiplier(UUID questId) {
        CurseSession session = sessions.get(requireQuestId(questId));
        return session != null ? session.getTotalRewardMultiplier() : 1.0f;
    }

    /**
     * Clean up on shutdown.
     */
    public void shutdown() {
        sessions.clear();
        LOGGER.info("[Bargain] Shutdown complete");
    }

    private static UUID requireQuestId(UUID questId) {
        if (questId == null) {
            throw new IllegalArgumentException("questId");
        }
        return questId;
    }

    private static BlockPos resolveBlockPos(ServerPlayer player) {
        return player.blockPosition();
    }

    private static Component literalMessage(String message) {
        return Component.literal(message == null ? "" : message);
    }

    private static void sendMessage(ServerPlayer player, String message) {
        player.sendSystemMessage(Objects.requireNonNull(literalMessage(message)));
    }

    private static void playSound(ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source,
            float volume, float pitch) {
        if (level == null || pos == null || sound == null || source == null) {
            return;
        }
        level.playSound(null,
            pos,
            sound,
            source,
            volume,
            pitch
        );
    }

    private static void sendParticles(ServerLevel level, SimpleParticleType particle,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (level == null || particle == null) {
            return;
        }
        level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
    }

    private DevilsBargainManager() {}

    // ========== QuestLifecycleListener Implementation ==========

    @Override
    public void onQuestStarted(QuestStarted event) {
        QuestContext ctx = event.context();
        UUID questId = ctx.questId();

        // Only create session if not already exists (quest-scoped)
        if (getSession(questId).isEmpty()) {
            startSession(questId);
            LOGGER.debug("[DevilsBargainManager] Created session for quest {} via event bus", questId);
        }
    }

    @Override
    public void onQuestEnded(QuestEnded event) {
        // Only cleanup shared resources (quest-scoped sessions) when cleanupShared is true
        if (!event.cleanupShared()) return;

        UUID questId = event.questId();
        CurseSession session = endSession(questId);
        if (session != null && session.getCurseCount() > 0) {
            LOGGER.debug("[DevilsBargainManager] Ended session for quest {} via event bus ({} curses, {}x reward)",
                questId, session.getCurseCount(), session.getTotalRewardMultiplier());
        }
    }

    @Override
    public int getPriority() {
        return 300; // After PerkSystem, bargains may interact with perks
    }

    @Override
    public String getListenerName() {
        return "DevilsBargainManager";
    }
}
