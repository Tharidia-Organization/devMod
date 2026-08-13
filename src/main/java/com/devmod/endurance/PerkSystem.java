package com.devmod.endurance;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.endurance.lifecycle.QuestContext;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestStarted;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
public class PerkSystem implements QuestLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerkSystem.class);

    public static final PerkSystem INSTANCE = new PerkSystem();

    @Nonnull
    private static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    // Config manager reference
    private final EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

    /**
     * Get tier weight from config for a specific quest.
     */
    private int getTierWeight(PerkTier tier, UUID questId) {
        return switch (tier) {
            case COMMON -> config.getPerkCommonWeight(questId);
            case UNCOMMON -> config.getPerkUncommonWeight(questId);
            case RARE -> config.getPerkRareWeight(questId);
            case EPIC -> config.getPerkEpicWeight(questId);
            case LEGENDARY -> config.getPerkLegendaryWeight(questId);
        };
    }

    // All available perks
    private final Map<String, Perk> allPerks = new LinkedHashMap<>();

    // Player perk sessions
    private final Map<UUID, PerkSession> activeSessions = new ConcurrentHashMap<>();

    // Meta-progression: unlocked perks per player (persistent)
    private final Map<UUID, Set<String>> unlockedPerks = new ConcurrentHashMap<>();

    private final Random random = new Random();

    /**
     * Perk rarity tiers.
     */
    public enum PerkTier {
        COMMON(EnduranceColors.PerkRarity.COMMON, "Common", 60),      // 60% chance
        UNCOMMON(EnduranceColors.PerkRarity.UNCOMMON, "Uncommon", 25),// 25% chance
        RARE(EnduranceColors.PerkRarity.RARE, "Rare", 10),            // 10% chance
        EPIC(EnduranceColors.PerkRarity.EPIC, "Epic", 4),             // 4% chance
        LEGENDARY(EnduranceColors.PerkRarity.LEGENDARY, "Legendary", 1); // 1% chance

        private final int color;
        private final String displayName;
        private final int weight;

        PerkTier(int color, String displayName, int weight) {
            this.color = color;
            this.displayName = displayName;
            this.weight = weight;
        }

        public int getColor() { return color; }
        public String getDisplayName() { return displayName; }
        public int getWeight() { return weight; }
    }

    /**
     * Perk categories for organization and synergies.
     */
    public enum PerkCategory {
        OFFENSE("Offense", EnduranceColors.PerkCategory.OFFENSE),   // Damage, crits, attack speed
        DEFENSE("Defense", EnduranceColors.PerkCategory.DEFENSE),   // Health, armor, resistance
        UTILITY("Utility", EnduranceColors.PerkCategory.UTILITY),   // Movement, cooldowns, resource
        VAMPIRIC("Vampiric", EnduranceColors.PerkCategory.VAMPIRIC),// Life steal, drain effects
        ELEMENTAL("Elemental", EnduranceColors.PerkCategory.ELEMENTAL), // Fire, ice, lightning
        COMBO("Combo", EnduranceColors.PerkCategory.COMBO),         // Style and combo bonuses
        CURSE("Curse", EnduranceColors.PerkCategory.CURSE);         // Negative effects for rewards

        private final String displayName;
        private final int color;

        PerkCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }
    }

    /**
     * A single perk definition.
     */
    public static class Perk {
        private final String id;
        private final String name;
        private final String description;
        private final PerkTier tier;
        private final PerkCategory category;
        private final String iconPath;

        // Can this perk stack?
        private final boolean stackable;
        private final int maxStacks;

        // Required perks to appear (synergy)
        private final Set<String> requiredPerks;

        // Perks that make this one not appear
        private final Set<String> incompatiblePerks;

        // Effect when acquired
        private final Consumer<PerkContext> onAcquire;

        // Effect every tick (if applicable)
        private final Consumer<PerkContext> onTick;

        // Effect on specific events
        private final Consumer<PerkContext> onHit;
        private final Consumer<PerkContext> onKill;
        private final Consumer<PerkContext> onDamageTaken;

        public Perk(String id, String name, String description, PerkTier tier, PerkCategory category,
                    boolean stackable, int maxStacks, @Nullable Consumer<PerkContext> onAcquire) {
            this(id, name, description, tier, category, null, stackable, maxStacks,
                Set.of(), Set.of(), onAcquire, null, null, null, null);
        }

        public Perk(String id, String name, String description, PerkTier tier, PerkCategory category,
                    @Nullable String iconPath, boolean stackable, int maxStacks,
                    Set<String> requiredPerks, Set<String> incompatiblePerks,
                    Consumer<PerkContext> onAcquire, @Nullable Consumer<PerkContext> onTick,
                    @Nullable Consumer<PerkContext> onHit, @Nullable Consumer<PerkContext> onKill,
                    @Nullable Consumer<PerkContext> onDamageTaken) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tier = tier;
            this.category = category;
            this.iconPath = iconPath;
            this.stackable = stackable;
            this.maxStacks = maxStacks;
            this.requiredPerks = requiredPerks;
            this.incompatiblePerks = incompatiblePerks;
            this.onAcquire = onAcquire;
            this.onTick = onTick;
            this.onHit = onHit;
            this.onKill = onKill;
            this.onDamageTaken = onDamageTaken;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public PerkTier getTier() { return tier; }
        public PerkCategory getCategory() { return category; }
        public String getIconPath() { return iconPath; }
        public boolean isStackable() { return stackable; }
        public int getMaxStacks() { return maxStacks; }
        public Set<String> getRequiredPerks() { return requiredPerks; }
        public Set<String> getIncompatiblePerks() { return incompatiblePerks; }

        public void apply(PerkContext context) {
            if (onAcquire != null) onAcquire.accept(context);
        }

        public void tick(PerkContext context) {
            if (onTick != null) onTick.accept(context);
        }

        public void onHitEnemy(PerkContext context) {
            if (onHit != null) onHit.accept(context);
        }

        public void onKillEnemy(PerkContext context) {
            if (onKill != null) onKill.accept(context);
        }

        public void onTakeDamage(PerkContext context) {
            if (onDamageTaken != null) onDamageTaken.accept(context);
        }
    }

    /**
     * Context passed to perk effects.
     */
    public static class PerkContext {
        private final ServerPlayer player;
        private final PerkSession session;
        private final int stackCount;

        // Event-specific data
        private float damage;
        private boolean isCritical;

        public PerkContext(ServerPlayer player, PerkSession session, int stackCount) {
            this.player = player;
            this.session = session;
            this.stackCount = stackCount;
        }

        public ServerPlayer getPlayer() { return player; }
        public PerkSession getSession() { return session; }
        public int getStackCount() { return stackCount; }

        public float getDamage() {
            return damage;
        }

        public void setDamage(float damage) {
            this.damage = damage;
        }

        public boolean isCritical() {
            return isCritical;
        }

        public void setCritical(boolean critical) {
            this.isCritical = critical;
        }
    }

    /**
     * Player's perk state during a quest.
     */
    public static class PerkSession {
        private final UUID playerId;
        private final UUID questId;

        // Acquired perks and their stack counts
        private final Map<String, Integer> acquiredPerks = new LinkedHashMap<>();

        // Stat modifiers from perks
        private float damageMultiplier = 1.0f;
        private float attackSpeedMultiplier = 1.0f;
        private float movementSpeedMultiplier = 1.0f;
        private float damageReductionMultiplier = 1.0f;
        private float lifestealPercent = 0f;
        private float critChanceBonus = 0f;
        private float critDamageMultiplier = 1.5f;

        // Elemental effects
        private float fireChance = 0f;
        private float freezeChance = 0f;
        private float lightningChance = 0f;

        // Combo/style bonuses
        private float styleMultiplier = 1.0f;
        private float comboDecayReduction = 0f;

        // Curse effects (for bonus rewards)
        private int curseLevel = 0;
        private float curseRewardMultiplier = 1.0f;

        // Pending perk choices
        private List<Perk> pendingChoices = new ArrayList<>();
        private int lastPerkChoicesWave = 0;

        // Applied attribute modifiers (for cleanup), paired with their target attribute
        private final List<AppliedAttributeModifier> appliedModifiers = new ArrayList<>();

        private Set<String> suggestedPerks = Set.of();
        private Set<String> excludedPerks = Set.of();
        private Set<String> requiredPerks = Set.of();

        public PerkSession(UUID playerId, UUID questId) {
            this.playerId = playerId;
            this.questId = questId;
        }

        public void applyBindings(@javax.annotation.Nullable ArenaPolicy.PerkBindings bindings) {
            if (bindings == null) {
                this.suggestedPerks = Set.of();
                this.excludedPerks = Set.of();
                this.requiredPerks = Set.of();
                return;
            }
            this.suggestedPerks = bindings.suggested() != null ? Set.copyOf(bindings.suggested()) : Set.of();
            this.excludedPerks = bindings.excluded() != null ? Set.copyOf(bindings.excluded()) : Set.of();
            this.requiredPerks = bindings.required() != null ? Set.copyOf(bindings.required()) : Set.of();
        }

        public boolean hasPerk(String perkId) {
            return acquiredPerks.containsKey(perkId);
        }

        public int getPerkStacks(String perkId) {
            return acquiredPerks.getOrDefault(perkId, 0);
        }

        public void addPerk(String perkId) {
            acquiredPerks.merge(perkId, 1, (a, b) -> a + b);
        }

        public Set<String> getAcquiredPerkIds() {
            return acquiredPerks.keySet();
        }

        public int getTotalPerksAcquired() {
            return acquiredPerks.values().stream().mapToInt(Integer::intValue).sum();
        }

        public boolean isSuggested(String perkId) {
            return perkId != null && suggestedPerks.contains(perkId);
        }

        public boolean isExcluded(String perkId) {
            return perkId != null && excludedPerks.contains(perkId);
        }

        public boolean isRequired(String perkId) {
            return perkId != null && requiredPerks.contains(perkId);
        }

        public boolean hasRequiredPending() {
            if (requiredPerks.isEmpty()) {
                return false;
            }
            for (String required : requiredPerks) {
                if (!hasPerk(required)) {
                    return true;
                }
            }
            return false;
        }

        public Set<String> getRequiredPerks() { return requiredPerks; }

        // Stat getters and modifiers
        public float getDamageMultiplier() { return damageMultiplier; }
        public void addDamageMultiplier(float amount) { damageMultiplier += amount; }

        public float getAttackSpeedMultiplier() { return attackSpeedMultiplier; }
        public void addAttackSpeedMultiplier(float amount) { attackSpeedMultiplier += amount; }

        public float getMovementSpeedMultiplier() { return movementSpeedMultiplier; }
        public void addMovementSpeedMultiplier(float amount) { movementSpeedMultiplier += amount; }

        public float getDamageReductionMultiplier() { return damageReductionMultiplier; }
        public void addDamageReduction(float amount) { damageReductionMultiplier -= amount; }

        public float getLifestealPercent() { return lifestealPercent; }
        public void addLifesteal(float amount) { lifestealPercent += amount; }

        public float getCritChanceBonus() { return critChanceBonus; }
        public void addCritChance(float amount) { critChanceBonus += amount; }

        public float getCritDamageMultiplier() { return critDamageMultiplier; }
        public void addCritDamage(float amount) { critDamageMultiplier += amount; }

        public float getFireChance() { return fireChance; }
        public void addFireChance(float amount) { fireChance += amount; }

        public float getFreezeChance() { return freezeChance; }
        public void addFreezeChance(float amount) { freezeChance += amount; }

        public float getLightningChance() { return lightningChance; }
        public void addLightningChance(float amount) { lightningChance += amount; }

        public float getStyleMultiplier() { return styleMultiplier; }
        public void addStyleMultiplier(float amount) { styleMultiplier += amount; }

        public float getComboDecayReduction() { return comboDecayReduction; }
        public void addComboDecayReduction(float amount) { comboDecayReduction = Math.min(0.9f, comboDecayReduction + amount); }

        public int getCurseLevel() { return curseLevel; }
        public void addCurse(int level) {
            curseLevel += level;
            curseRewardMultiplier += level * 0.25f; // +25% rewards per curse level
        }

        public float getCurseRewardMultiplier() { return curseRewardMultiplier; }

        public List<Perk> getPendingChoices() { return pendingChoices; }
        public void setPendingChoices(List<Perk> choices, int waveNumber) {
            this.pendingChoices = choices;
            this.lastPerkChoicesWave = waveNumber;
        }
        public void clearPendingChoices() { pendingChoices.clear(); }
        public int getLastPerkChoicesWave() { return lastPerkChoicesWave; }

        public UUID getPlayerId() { return playerId; }
        public UUID getQuestId() { return questId; }

        public void addAppliedModifier(Holder<Attribute> attribute, AttributeModifier modifier) {
            appliedModifiers.add(new AppliedAttributeModifier(attribute, modifier));
        }

        public List<AppliedAttributeModifier> getAppliedModifiers() {
            return appliedModifiers;
        }
    }

    /**
     * An attribute modifier applied by a perk, paired with the attribute it was added to
     * so it can be removed from that same attribute when the session ends.
     */
    public record AppliedAttributeModifier(Holder<Attribute> attribute, AttributeModifier modifier) {}

    // ========== Initialization ==========

    private PerkSystem() {
        initializePerks();
    }

    private void initializePerks() {
        // === OFFENSE PERKS ===

        // NOTE: Stackable perks use fixed per-stack bonuses (not multiplied by stackCount)
        // because the callback is called each time the perk is acquired, not once for all stacks

        registerPerk(new Perk("sharp_blades", "Sharp Blades", "+15% damage per stack", PerkTier.COMMON, PerkCategory.OFFENSE,
            true, 5, ctx -> ctx.getSession().addDamageMultiplier(0.15f)));

        registerPerk(new Perk("fury", "Fury", "+10% attack speed per stack", PerkTier.COMMON, PerkCategory.OFFENSE,
            true, 5, ctx -> ctx.getSession().addAttackSpeedMultiplier(0.10f)));

        registerPerk(new Perk("critical_eye", "Critical Eye", "+10% crit chance per stack", PerkTier.UNCOMMON, PerkCategory.OFFENSE,
            true, 3, ctx -> ctx.getSession().addCritChance(0.10f)));

        registerPerk(new Perk("executioner", "Executioner", "+50% crit damage per stack", PerkTier.RARE, PerkCategory.OFFENSE,
            true, 2, ctx -> ctx.getSession().addCritDamage(0.50f)));

        registerPerk(new Perk("berserker", "Berserker", "+5% damage per 10% missing HP", PerkTier.EPIC, PerkCategory.OFFENSE,
            false, 1, ctx -> {
                // Calculated dynamically during combat
            }));

        registerPerk(new Perk("glass_cannon", "Glass Cannon", "+100% damage, +50% damage taken", PerkTier.LEGENDARY, PerkCategory.OFFENSE,
            false, 1, ctx -> {
                ctx.getSession().addDamageMultiplier(1.0f);
                ctx.getSession().addDamageReduction(-0.5f); // More damage taken
            }));

        // === DEFENSE PERKS ===

        registerPerk(new Perk("tough_skin", "Tough Skin", "-10% damage taken per stack", PerkTier.COMMON, PerkCategory.DEFENSE,
            true, 5, ctx -> ctx.getSession().addDamageReduction(0.10f)));

        registerPerk(new Perk("vitality", "Vitality", "+2 max hearts", PerkTier.UNCOMMON, PerkCategory.DEFENSE,
            true, 5, ctx -> {
                var attr = ctx.getPlayer().getAttribute(requireNonNull(Attributes.MAX_HEALTH, "MAX_HEALTH"));
                if (attr != null) {
                    // Each stack uses a unique modifier ID so they don't conflict
                    // Only add the INCREMENTAL bonus (4.0 HP per new stack, not cumulative)
                    ResourceLocation modId = requireNonNull(
                        ResourceLocation.fromNamespaceAndPath("devmod", "vitality_stack_" + ctx.getStackCount()),
                        "vitalityModifierId"
                    );
                    AttributeModifier mod = new AttributeModifier(
                        modId,
                        4.0, // Fixed per-stack bonus, not multiplied by stackCount
                        AttributeModifier.Operation.ADD_VALUE
                    );
                    attr.addTransientModifier(mod);
                    ctx.getSession().addAppliedModifier(
                        requireNonNull(Attributes.MAX_HEALTH, "MAX_HEALTH"), mod);
                    ctx.getPlayer().setHealth(ctx.getPlayer().getHealth() + 4.0f);
                }
            }));

        registerPerk(new Perk("regeneration", "Regeneration", "Slowly regenerate health", PerkTier.RARE, PerkCategory.DEFENSE,
            false, 1, null)); // Applied via tick

        registerPerk(new Perk("second_wind", "Second Wind", "Survive fatal blow once per wave", PerkTier.EPIC, PerkCategory.DEFENSE,
            false, 1, ctx -> {
                // Tracked separately
            }));

        registerPerk(new Perk("immortal", "Immortal", "Cannot die for 3 seconds after taking fatal damage", PerkTier.LEGENDARY, PerkCategory.DEFENSE,
            false, 1, ctx -> {
                // Tracked separately
            }));

        // === UTILITY PERKS ===

        registerPerk(new Perk("swift_feet", "Swift Feet", "+15% movement speed per stack", PerkTier.COMMON, PerkCategory.UTILITY,
            true, 3, ctx -> ctx.getSession().addMovementSpeedMultiplier(0.15f)));

        registerPerk(new Perk("treasure_hunter", "Treasure Hunter", "+25% reward points", PerkTier.UNCOMMON, PerkCategory.UTILITY,
            true, 3, ctx -> {
                // Applied to rewards
            }));

        registerPerk(new Perk("combo_master", "Combo Master", "-25% combo decay rate per stack", PerkTier.RARE, PerkCategory.COMBO,
            true, 3, ctx -> ctx.getSession().addComboDecayReduction(0.25f)));

        // === VAMPIRIC PERKS ===

        registerPerk(new Perk("lifesteal", "Lifesteal", "Heal for 5% of damage dealt per stack", PerkTier.UNCOMMON, PerkCategory.VAMPIRIC,
            true, 4, ctx -> ctx.getSession().addLifesteal(0.05f)));

        registerPerk(new Perk("blood_frenzy", "Blood Frenzy", "Killing enemies heals 2 HP", PerkTier.RARE, PerkCategory.VAMPIRIC,
            true, 3, null)); // Applied on kill

        registerPerk(new Perk("soul_drain", "Soul Drain", "Kills restore hunger and give brief regen", PerkTier.EPIC, PerkCategory.VAMPIRIC,
            false, 1, null)); // Applied on kill

        // === ELEMENTAL PERKS ===

        registerPerk(new Perk("fire_aspect", "Blazing Strikes", "20% chance to ignite enemies per stack", PerkTier.UNCOMMON, PerkCategory.ELEMENTAL,
            true, 3, ctx -> ctx.getSession().addFireChance(0.20f)));

        registerPerk(new Perk("frost_touch", "Frost Touch", "20% chance to slow enemies per stack", PerkTier.UNCOMMON, PerkCategory.ELEMENTAL,
            true, 3, ctx -> ctx.getSession().addFreezeChance(0.20f)));

        registerPerk(new Perk("lightning_strike", "Lightning Strike", "10% chance to chain lightning per stack", PerkTier.RARE, PerkCategory.ELEMENTAL,
            true, 2, ctx -> ctx.getSession().addLightningChance(0.10f)));

        registerPerk(new Perk("elemental_mastery", "Elemental Mastery", "All elemental effects +50%", PerkTier.EPIC, PerkCategory.ELEMENTAL,
            false, 1, ctx -> {
                ctx.getSession().addFireChance(ctx.getSession().getFireChance() * 0.5f);
                ctx.getSession().addFreezeChance(ctx.getSession().getFreezeChance() * 0.5f);
                ctx.getSession().addLightningChance(ctx.getSession().getLightningChance() * 0.5f);
            }));

        // === COMBO/STYLE PERKS ===

        registerPerk(new Perk("showoff", "Showoff", "+25% style points per stack", PerkTier.COMMON, PerkCategory.COMBO,
            true, 4, ctx -> ctx.getSession().addStyleMultiplier(0.25f)));

        registerPerk(new Perk("momentum", "Momentum", "Combo hits increase damage (max +50%)", PerkTier.RARE, PerkCategory.COMBO,
            false, 1, ctx -> {
                // Calculated dynamically
            }));

        registerPerk(new Perk("style_is_substance", "Style is Substance", "Style rank affects damage", PerkTier.EPIC, PerkCategory.COMBO,
            false, 1, ctx -> {
                // S rank = +50% damage, SSS = +100%
            }));

        // === CURSE PERKS (risk/reward) ===

        registerPerk(new Perk("curse_fragility", "Curse: Fragility", "+50% damage taken per stack, +50% rewards", PerkTier.UNCOMMON, PerkCategory.CURSE,
            true, 2, ctx -> {
                ctx.getSession().addDamageReduction(-0.5f);
                ctx.getSession().addCurse(1);
            }));

        registerPerk(new Perk("curse_weakness", "Curse: Weakness", "-25% damage per stack, +50% rewards", PerkTier.UNCOMMON, PerkCategory.CURSE,
            true, 2, ctx -> {
                ctx.getSession().addDamageMultiplier(-0.25f);
                ctx.getSession().addCurse(1);
            }));

        registerPerk(new Perk("curse_doom", "Curse: Doom", "Take 1 damage/second, +100% rewards", PerkTier.RARE, PerkCategory.CURSE,
            false, 1, ctx -> {
                ctx.getSession().addCurse(2);
            })); // Damage applied via tick

        registerPerk(new Perk("ultimate_curse", "Pact with Death", "Die in one hit, +300% rewards", PerkTier.LEGENDARY, PerkCategory.CURSE,
            false, 1, ctx -> {
                var attr = ctx.getPlayer().getAttribute(requireNonNull(Attributes.MAX_HEALTH, "MAX_HEALTH"));
                if (attr != null) {
                    ctx.getPlayer().setHealth(1.0f);
                }
                ctx.getSession().addCurse(6);
            }));

        // === LEGENDARY SYNERGY PERKS ===

        registerPerk(new Perk("avatar_of_war", "Avatar of War",
            "Requires 5+ offense perks. +100% damage, attacks cause explosions",
            PerkTier.LEGENDARY, PerkCategory.OFFENSE, null, false, 1,
            Set.of(), Set.of(), // Requires offense perks (checked separately)
            ctx -> ctx.getSession().addDamageMultiplier(1.0f),
            null, null, null, null));

        registerPerk(new Perk("unkillable", "Unkillable",
            "Requires 5+ defense perks. Immune to one-shots, +50% health",
            PerkTier.LEGENDARY, PerkCategory.DEFENSE, null, false, 1,
            Set.of(), Set.of(),
            ctx -> {
                var attr = ctx.getPlayer().getAttribute(requireNonNull(Attributes.MAX_HEALTH, "MAX_HEALTH"));
                if (attr != null) {
                    float bonus = ctx.getPlayer().getMaxHealth() * 0.5f;
                    ctx.getPlayer().setHealth(ctx.getPlayer().getHealth() + bonus);
                }
            },
            null, null, null, null));

        // ═══════════════════════════════════════════════════════════════
        // TRANSFORMATIVE PERKS - Fundamentally change playstyle
        // ═══════════════════════════════════════════════════════════════

        // 1. ECHO STRIKE - Attacks create a delayed echo that hits again
        registerPerk(new Perk("echo_strike", "Echo Strike",
            "Your attacks create an echo that repeats 50% damage after 0.5s",
            PerkTier.EPIC, PerkCategory.OFFENSE, null, false, 1,
            Set.of(), Set.of("glass_cannon"),
            null, // Applied via combat system
            null, null, null, null));

        // 2. BULLET TIME - Time slows when you're in danger
        registerPerk(new Perk("bullet_time", "Bullet Time",
            "Below 30% HP: nearby enemies move 50% slower",
            PerkTier.RARE, PerkCategory.DEFENSE, null, false, 1,
            Set.of(), Set.of(),
            null, // Applied via tick processing
            null, null, null, null));

        // 3. UNSTOPPABLE FORCE - Become an immovable object
        registerPerk(new Perk("unstoppable_force", "Unstoppable Force",
            "Immune to knockback. Charging through enemies damages them",
            PerkTier.RARE, PerkCategory.DEFENSE, null, false, 1,
            Set.of(), Set.of(),
            ctx -> {
                // Remove knockback vulnerability
                var attr = ctx.getPlayer().getAttribute(requireNonNull(Attributes.KNOCKBACK_RESISTANCE, "KNOCKBACK_RESISTANCE"));
                if (attr != null) {
                    ResourceLocation modId = ResourceLocation.fromNamespaceAndPath("devmod", "unstoppable_force");
                    if (modId != null) {
                        AttributeModifier modifier = new AttributeModifier(modId, 1.0, AttributeModifier.Operation.ADD_VALUE);
                        attr.addTransientModifier(modifier);
                        ctx.getSession().addAppliedModifier(
                            requireNonNull(Attributes.KNOCKBACK_RESISTANCE, "KNOCKBACK_RESISTANCE"), modifier);
                    }
                }
            },
            null, null, null, null));

        // 4. CHAIN REACTION - Kills create explosions after a delay
        registerPerk(new Perk("chain_reaction", "Chain Reaction",
            "Killed enemies explode after 1s, damaging nearby foes",
            PerkTier.EPIC, PerkCategory.OFFENSE, null, false, 1,
            Set.of(), Set.of(),
            null, // Applied via kill processing
            null, null, null, null));

        // 5. BLOOD PACT - Link your fate to allies
        registerPerk(new Perk("blood_pact", "Blood Pact",
            "Damage is split with nearby party members. Healing is shared",
            PerkTier.LEGENDARY, PerkCategory.DEFENSE, null, false, 1,
            Set.of(), Set.of("glass_cannon"),
            null, // Applied via damage/heal events
            null, null, null, null));

        // 6. PHANTOM SHIFT - Periodically become intangible
        registerPerk(new Perk("phantom_shift", "Phantom Shift",
            "Every 10s, become invulnerable for 2s. Can't attack during",
            PerkTier.EPIC, PerkCategory.DEFENSE, null, false, 1,
            Set.of(), Set.of(),
            null, // Applied via tick processing
            null, null, null, null));

        // 7. SOUL HARVEST - Kills empower your next attack
        registerPerk(new Perk("soul_harvest", "Soul Harvest",
            "Each kill stores a soul (max 10). Use all for massive attack",
            PerkTier.LEGENDARY, PerkCategory.OFFENSE, null, false, 1,
            Set.of(), Set.of(),
            null, // Tracked via kill counter
            null, null, null, null));

        // 8. REVENGE - Taking damage makes you stronger
        registerPerk(new Perk("revenge", "Revenge",
            "Each hit taken grants +20% damage for 5s (stacks 5x)",
            PerkTier.RARE, PerkCategory.OFFENSE, null, false, 1,
            Set.of(), Set.of(),
            null, // Applied via damage taken event
            null, null, null,
            ctx -> {
                // Add revenge buff (handled by combat system)
            }));

        // 9. EXECUTIONER'S WRATH - Low HP enemies take massive damage
        registerPerk(new Perk("executioners_wrath", "Executioner's Wrath",
            "Enemies below 25% HP take 3x damage from you",
            PerkTier.EPIC, PerkCategory.OFFENSE, null, false, 1,
            Set.of(), Set.of(),
            null, // Applied via damage calculation
            null, null, null, null));

        // 10. ADRENALINE SURGE - Near-death grants temporary god mode
        registerPerk(new Perk("adrenaline_surge", "Adrenaline Surge",
            "Dropping below 10% HP: 3s invincibility + full heal over time",
            PerkTier.LEGENDARY, PerkCategory.DEFENSE, null, false, 1,
            Set.of(), Set.of("ultimate_curse"),
            null, // Synergizes with ComebackSystem
            null, null, null, null));

        LOGGER.info("[PerkSystem] Registered {} perks", allPerks.size());
    }

    private void registerPerk(Perk perk) {
        allPerks.put(perk.getId(), perk);
    }

    // ========== Session Management ==========

    /**
     * Start a new perk session for a player.
     */
    public PerkSession startSession(UUID playerId, UUID questId) {
        return startSession(playerId, questId, null);
    }

    public PerkSession startSession(UUID playerId, UUID questId, @javax.annotation.Nullable ArenaPolicy policy) {
        PerkSession session = new PerkSession(playerId, questId);
        if (policy != null && policy.perkBindings() != null) {
            session.applyBindings(policy.perkBindings());
        }
        activeSessions.put(playerId, session);
        LOGGER.debug("[PerkSystem] Started session for player {}", playerId);
        return session;
    }

    /**
     * Get active session for a player.
     */
    public Optional<PerkSession> getSession(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * End session and clean up all perk effects.
     */
    public PerkSession endSession(ServerPlayer player) {
        PerkSession session = activeSessions.remove(player.getUUID());
        if (session != null) {
            // Remove each applied modifier from the attribute it was actually added to
            for (AppliedAttributeModifier applied : session.getAppliedModifiers()) {
                var attr = player.getAttribute(requireNonNull(applied.attribute(), "attribute"));
                if (attr != null) {
                    attr.removeModifier(requireNonNull(applied.modifier(), "modifier"));
                }
            }

            // Remove any perk-related potion effects that might still be active
            player.removeEffect(requireNonNull(MobEffects.REGENERATION, "REGENERATION"));
            player.removeEffect(requireNonNull(MobEffects.MOVEMENT_SPEED, "MOVEMENT_SPEED"));
            player.removeEffect(requireNonNull(MobEffects.DAMAGE_BOOST, "DAMAGE_BOOST"));
            player.removeEffect(requireNonNull(MobEffects.DAMAGE_RESISTANCE, "DAMAGE_RESISTANCE"));

            // Reset player health to max (after attribute cleanup) to avoid issues
            // with health being above new max health
            float maxHealth = player.getMaxHealth();
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }

            LOGGER.info("[PerkSystem] Session ended for player {} (quest {}) - {} perks acquired, all effects removed",
                session.getPlayerId(), session.getQuestId(), session.getTotalPerksAcquired());
        }
        return session;
    }

    /**
     * End session without a player reference (used during forced shutdown).
     */
    public PerkSession endSession(UUID playerId) {
        return activeSessions.remove(playerId);
    }

    /**
     * Check if a player has unlocked a specific perk.
     */
    public boolean hasUnlockedPerk(UUID playerId, String perkId) {
        Set<String> perks = unlockedPerks.get(playerId);
        return perks != null && perks.contains(perkId);
    }

    /**
     * Get all unlocked perks for a player.
     */
    public Set<String> getUnlockedPerks(UUID playerId) {
        return unlockedPerks.getOrDefault(playerId, Set.of());
    }

    // ========== Perk Selection ==========

    /**
     * Generate perk choices for a player after completing a wave.
     */
    public List<Perk> generatePerkChoices(ServerPlayer player, int waveNumber) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return List.of();

        List<Perk> choices = new ArrayList<>();
        List<Perk> availablePerks = getAvailablePerks(session, waveNumber);
        List<Perk> requiredPerks = getRequiredPerksToOffer(session);
        if (!requiredPerks.isEmpty()) {
            availablePerks = requiredPerks;
        }

        // Select 3 random perks weighted by tier
        int attempts = 0;
        while (choices.size() < 3 && attempts < 100 && !availablePerks.isEmpty()) {
            Perk selected = selectWeightedPerk(availablePerks, waveNumber, session);
            if (selected != null && !choices.contains(selected)) {
                choices.add(selected);
                availablePerks.remove(selected);
            }
            attempts++;
        }

        session.setPendingChoices(choices, waveNumber);

        // Telemetry: record perk choices offered
        if (!choices.isEmpty()) {
            EnduranceTelemetryService.INSTANCE.recordPerkChoicesOffered(
                player.getUUID(),
                session.getQuestId(),
                waveNumber,
                choices
            );
        }

        return choices;
    }

    /**
     * Get perks available for selection.
     */
    private List<Perk> getAvailablePerks(PerkSession session, int waveNumber) {
        List<Perk> available = new ArrayList<>();

        for (Perk perk : allPerks.values()) {
            if (session.isExcluded(perk.getId())) continue;

            // Check if already at max stacks
            if (!perk.isStackable() && session.hasPerk(perk.getId())) continue;
            if (perk.isStackable() && session.getPerkStacks(perk.getId()) >= perk.getMaxStacks()) continue;

            // Check required perks
            if (!perk.getRequiredPerks().isEmpty()) {
                boolean hasAll = perk.getRequiredPerks().stream().allMatch(session::hasPerk);
                if (!hasAll) continue;
            }

            // Check incompatible perks
            if (perk.getIncompatiblePerks().stream().anyMatch(session::hasPerk)) continue;

            // Check synergy requirements for legendary perks
            if (perk.getTier() == PerkTier.LEGENDARY) {
                if (perk.getId().equals("avatar_of_war")) {
                    long offenseCount = session.getAcquiredPerkIds().stream()
                        .map(allPerks::get)
                        .filter(p -> p != null && p.category == PerkCategory.OFFENSE)
                        .count();
                    if (offenseCount < 5) continue;
                } else if (perk.getId().equals("unkillable")) {
                    long defenseCount = session.getAcquiredPerkIds().stream()
                        .map(allPerks::get)
                        .filter(p -> p != null && p.category == PerkCategory.DEFENSE)
                        .count();
                    if (defenseCount < 5) continue;
                }
            }

            // Higher tier perks appear more in later waves
            if (perk.getTier().ordinal() > waveNumber / 2) {
                // Skip very rare perks in early waves
                if (waveNumber < 3 && perk.getTier().ordinal() >= PerkTier.EPIC.ordinal()) continue;
            }

            available.add(perk);
        }

        return available;
    }

    private List<Perk> getRequiredPerksToOffer(PerkSession session) {
        if (!session.hasRequiredPending()) {
            return List.of();
        }
        List<Perk> required = new ArrayList<>();
        for (String perkId : session.getRequiredPerks()) {
            if (session.hasPerk(perkId)) {
                continue;
            }
            Perk perk = allPerks.get(perkId);
            if (perk == null) {
                LOGGER.warn("[PerkSystem] Required perk '{}' not found", perkId);
                continue;
            }
            if (perk.getIncompatiblePerks().stream().anyMatch(session::hasPerk)) {
                LOGGER.warn("[PerkSystem] Required perk '{}' incompatible with existing perks", perkId);
                continue;
            }
            if (!perk.getRequiredPerks().isEmpty()) {
                boolean hasAll = perk.getRequiredPerks().stream().allMatch(session::hasPerk);
                if (!hasAll) {
                    LOGGER.warn("[PerkSystem] Required perk '{}' missing prerequisites", perkId);
                    continue;
                }
            }
            required.add(perk);
        }
        return required;
    }

    /**
     * Select a perk weighted by tier rarity.
     */
    private Perk selectWeightedPerk(List<Perk> perks, int waveNumber, @javax.annotation.Nullable PerkSession session) {
        if (perks.isEmpty()) return null;

        // Get quest ID for config lookup
        UUID questId = session != null ? session.getQuestId() : null;

        // Calculate total weight
        int totalWeight = 0;
        for (Perk perk : perks) {
            // Higher waves increase rare perk chances
            // Use config-aware weight if quest context available, otherwise fall back to enum default
            int weight = questId != null ? getTierWeight(perk.getTier(), questId) : perk.getTier().getWeight();
            if (waveNumber >= 5) weight *= (perk.getTier().ordinal() + 1);
            if (session != null && session.isSuggested(perk.getId())) {
                weight = (int) Math.ceil(weight * 1.5);
            }
            totalWeight += weight;
        }

        // Select random - config-supplied weights can all be zero, which has no valid
        // weighted roll, so fall back to the uniform pick used when no bucket matches
        if (totalWeight <= 0) {
            return perks.get(random.nextInt(perks.size()));
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Perk perk : perks) {
            int weight = questId != null ? getTierWeight(perk.getTier(), questId) : perk.getTier().getWeight();
            if (waveNumber >= 5) weight *= (perk.getTier().ordinal() + 1);
            if (session != null && session.isSuggested(perk.getId())) {
                weight = (int) Math.ceil(weight * 1.5);
            }
            cumulative += weight;
            if (roll < cumulative) {
                return perk;
            }
        }

        return perks.get(random.nextInt(perks.size()));
    }

    /**
     * Player selects a perk from choices.
     */
    public boolean selectPerk(ServerPlayer player, int choiceIndex) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return false;

        List<Perk> choices = session.getPendingChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) return false;

        Perk selected = choices.get(choiceIndex);
        applyPerk(player, session, selected);
        session.clearPendingChoices();

        LOGGER.info("[PerkSystem] Player {} selected perk: {} ({})",
            player.getName().getString(), selected.name, selected.tier.getDisplayName());

        return true;
    }

    /**
     * Apply a perk to player.
     */
    private void applyPerk(ServerPlayer player, PerkSession session, Perk perk) {
        session.addPerk(perk.getId());

        // Telemetry: record perk selection
        EnduranceTelemetryService.INSTANCE.recordPerkSelected(
            player.getUUID(),
            session.getQuestId(),
            perk.getId(),
            perk.getName(),
            perk.getTier(),
            perk.getCategory(),
            session.getPerkStacks(perk.getId()),
            session.getTotalPerksAcquired(),
            session.getLastPerkChoicesWave()
        );

        // Trigger player attribute snapshot on perk acquisition
        com.devmod.telemetry.player.PlayerAttributeTelemetryService.INSTANCE
            .recordSnapshot(player, "perk_acquired_" + perk.getId());

        PerkContext context = new PerkContext(player, session, session.getPerkStacks(perk.getId()));
        perk.apply(context);
    }

    // ========== Perk Effects ==========

    /**
     * Process tick effects for all active sessions.
     */
    public void tick(ServerPlayer player) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return;

        // Regeneration perk
        if (session.hasPerk("regeneration")) {
            if (player.tickCount % 40 == 0) { // Every 2 seconds
                player.heal(1.0f);
            }
        }

        // Doom curse - use generic() to respect armor instead of magic()
        if (session.hasPerk("curse_doom")) {
            if (player.tickCount % 20 == 0) { // Every second
                player.hurt(requireNonNull(player.damageSources().generic(), "genericDamage"), 1.0f);
            }
        }

        // Process perk tick callbacks
        for (String perkId : session.getAcquiredPerkIds()) {
            Perk perk = allPerks.get(perkId);
            if (perk != null) {
                PerkContext context = new PerkContext(player, session, session.getPerkStacks(perkId));
                perk.tick(context);
            }
        }
    }

    /**
     * Process on-hit effects.
     */
    public float processHit(ServerPlayer player, float damage, boolean isCritical) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return damage;

        float finalDamage = damage * session.getDamageMultiplier();

        // Berserker perk
        if (session.hasPerk("berserker")) {
            float missingHpPercent = 1.0f - (player.getHealth() / player.getMaxHealth());
            finalDamage *= 1.0f + (missingHpPercent * 0.5f);
        }

        // Momentum perk - bonus damage based on combo count
        if (session.hasPerk("momentum") && ComboSystemFacade.isInitialized()) {
            var comboOpt = ComboSystemFacade.get().getSession(player.getUUID());
            if (comboOpt.isPresent()) {
                float comboBonus = Math.min(0.5f, comboOpt.get().getCurrentCombo() * 0.01f);
                finalDamage *= 1.0f + comboBonus;
            }
        }

        // Critical damage
        if (isCritical) {
            finalDamage *= session.getCritDamageMultiplier();
        }

        // Lifesteal
        if (session.getLifestealPercent() > 0) {
            float heal = finalDamage * session.getLifestealPercent();
            player.heal(heal);
        }

        // Elemental effects are processed separately

        return finalDamage;
    }

    /**
     * Process on-kill effects.
     */
    public void processKill(ServerPlayer player) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return;

        // Blood Frenzy
        if (session.hasPerk("blood_frenzy")) {
            player.heal(2.0f * session.getPerkStacks("blood_frenzy"));
        }

        // Soul Drain
        if (session.hasPerk("soul_drain")) {
            player.getFoodData().eat(2, 0.5f);
            player.addEffect(new MobEffectInstance(
                requireNonNull(MobEffects.REGENERATION, "REGENERATION"), 40, 0));
        }
    }

    /**
     * Process damage taken.
     */
    public float processDamageTaken(ServerPlayer player, float damage) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return damage;

        float finalDamage = damage * session.getDamageReductionMultiplier();

        // Second Wind (one-time save)
        // Immortal (temporary invulnerability)
        // These need special handling in damage events

        return Math.max(0, finalDamage);
    }

    // ========== Getters ==========

    public Collection<Perk> getAllPerks() {
        return Collections.unmodifiableCollection(allPerks.values());
    }

    public Optional<Perk> getPerk(String id) {
        return Optional.ofNullable(allPerks.get(id));
    }

    // ========== QuestLifecycleListener Implementation ==========

    @Override
    public void onQuestStarted(QuestStarted event) {
        QuestContext ctx = event.context();
        UUID playerId = ctx.playerId();
        UUID questId = ctx.questId();

        // Only create session if not already exists
        if (getSession(playerId).isEmpty()) {
            startSession(playerId, questId, ctx.policy());
            LOGGER.debug("[PerkSystem] Created session for player {} via event bus", playerId);
        }
    }

    @Override
    public void onQuestEnded(QuestEnded event) {
        QuestContext ctx = event.context();
        ServerPlayer player = ctx.player();

        // End session and remove attribute modifiers
        PerkSession session = endSession(player);
        if (session != null) {
            LOGGER.debug("[PerkSystem] Ended session for player {} via event bus ({} perks)",
                player.getUUID(), session.getTotalPerksAcquired());
        }
    }

    @Override
    public int getPriority() {
        return 400; // After MutatorSystem, perks may depend on mutators
    }

    @Override
    public String getListenerName() {
        return "PerkSystem";
    }
}
