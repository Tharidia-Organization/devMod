package com.devmod.telemetry.player;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.devmod.abilities.DashAbilitySystem;
import com.devmod.abilities.DodgeAbilitySystem;
import com.devmod.abilities.StaminaSystem;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.MutatorSystem;
import com.devmod.endurance.PerkSystem;
import com.devmod.integration.PehkuiIntegration;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.duckdb.DuckDBConfig;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.devmod.telemetry.util.BitPackedFlags;

import com.mojang.logging.LogUtils;

/**
 * Telemetry service for tracking comprehensive player attributes.
 *
 * Tracks:
 * - Health (hearts, HP, max HP, absorption)
 * - Food (hunger level, saturation, exhaustion)
 * - Movement (speed, velocity, sprint/sneak state)
 * - Combat attributes (melee, magic, ranged multipliers and cooldowns)
 * - Defensive attributes (armor, damage reduction)
 * - Physical (reach, hitbox via Pehkui)
 * - Special abilities (stamina, dash, dodge) - from perk system
 * - Resource gathering speed
 * - View distance bonuses
 *
 * Data written to player_attributes.ndjson via TelemetryService.
 */
public class PlayerAttributeTelemetryService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayerAttributeTelemetryService INSTANCE = new PlayerAttributeTelemetryService();

    // PERFORMANCE: Cached reflection field for exhaustion (avoid repeated lookups)
    private static final java.lang.reflect.Field EXHAUSTION_FIELD;
    static {
        java.lang.reflect.Field field = null;
        try {
            field = net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOGGER.warn("[PlayerAttributes] Could not cache exhaustionLevel field: {}", e.getMessage());
        }
        EXHAUSTION_FIELD = field;
    }

    // Snapshot interval configuration
    private int snapshotIntervalTicks = 100; // Every 5 seconds by default

    // Last snapshot timestamps per player
    private final Map<UUID, Long> lastSnapshotTick = new ConcurrentHashMap<>();

    // Previous attribute values for change detection
    private final Map<UUID, PlayerAttributeSnapshot> previousSnapshots = new ConcurrentHashMap<>();

    private PlayerAttributeTelemetryService() {}

    /**
     * Configure snapshot interval.
     * @param ticks Number of ticks between snapshots (20 ticks = 1 second)
     */
    public void setSnapshotInterval(int ticks) {
        this.snapshotIntervalTicks = Math.max(20, ticks); // Minimum 1 second
    }

    /**
     * Called every server tick for each player.
     * Performs periodic snapshots and change-based tracking.
     */
    public void tick(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long currentTick = player.tickCount;

        // Check if it's time for a periodic snapshot
        Long lastTick = lastSnapshotTick.get(playerId);
        if (lastTick == null || currentTick - lastTick >= snapshotIntervalTicks) {
            recordSnapshot(player, "periodic");
            lastSnapshotTick.put(playerId, currentTick);
        }
    }

    /**
     * Record a full attribute snapshot.
     * @param player The player to snapshot
     * @param trigger What triggered this snapshot (periodic, damage, heal, perk, etc.)
     */
    public void recordSnapshot(ServerPlayer player, String trigger) {
        PlayerAttributeSnapshot snapshot = captureSnapshot(player);

        // PERFORMANCE: Build JSON with StringBuilder.append() instead of String.format()
        // String.format() is ~10x slower due to format string parsing overhead
        StringBuilder json = new StringBuilder(1024); // Pre-size to avoid resizing
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"playerId\":\"").append(player.getUUID()).append("\",");
        json.append("\"playerName\":\"").append(escape(player.getGameProfile().getName())).append("\",");
        json.append("\"trigger\":\"").append(escape(trigger)).append("\",");

        // Health section
        json.append("\"health\":{");
        json.append("\"hearts\":").append(snapshot.healthHearts).append(",");
        json.append("\"hp\":"); appendFloat2(json, snapshot.healthHp); json.append(",");
        json.append("\"maxHp\":"); appendFloat2(json, snapshot.maxHealthHp); json.append(",");
        json.append("\"absorption\":"); appendFloat2(json, snapshot.absorptionHp);
        json.append("},");

        // Food section
        json.append("\"food\":{");
        json.append("\"hunger\":").append(snapshot.hungerLevel).append(",");
        json.append("\"saturation\":"); appendFloat2(json, snapshot.saturation); json.append(",");
        json.append("\"exhaustion\":"); appendFloat2(json, snapshot.exhaustion);
        json.append("},");

        // Movement section - PERFORMANCE: Use bit-packed flags instead of 4 separate booleans
        // flags bits: [0]=sprinting, [1]=sneaking, [2]=swimming, [3]=falling
        int movementFlags = BitPackedFlags.packMovementSimple(
            snapshot.isSprinting, snapshot.isSneaking, snapshot.isSwimming, snapshot.isFalling);
        json.append("\"movement\":{");
        json.append("\"speed\":"); appendDouble4(json, snapshot.movementSpeed); json.append(",");
        json.append("\"velocityX\":"); appendDouble4(json, snapshot.velocityX); json.append(",");
        json.append("\"velocityY\":"); appendDouble4(json, snapshot.velocityY); json.append(",");
        json.append("\"velocityZ\":"); appendDouble4(json, snapshot.velocityZ); json.append(",");
        json.append("\"flags\":").append(movementFlags);
        json.append("},");

        // Combat - Melee section
        json.append("\"melee\":{");
        json.append("\"attackDamage\":"); appendDouble2(json, snapshot.meleeAttackDamage); json.append(",");
        json.append("\"attackSpeed\":"); appendDouble2(json, snapshot.meleeAttackSpeed); json.append(",");
        json.append("\"damageMultiplier\":"); appendFloat2(json, snapshot.meleeDamageMultiplier); json.append(",");
        json.append("\"reductionMultiplier\":"); appendFloat2(json, snapshot.meleeReduction);
        json.append("},");

        // Combat - Magic section
        json.append("\"magic\":{");
        json.append("\"damageMultiplier\":"); appendFloat2(json, snapshot.magicDamageMultiplier); json.append(",");
        json.append("\"reductionMultiplier\":"); appendFloat2(json, snapshot.magicReduction);
        json.append("},");

        // Combat - Ranged section
        json.append("\"ranged\":{");
        json.append("\"damageMultiplier\":"); appendFloat2(json, snapshot.rangedDamageMultiplier); json.append(",");
        json.append("\"reductionMultiplier\":"); appendFloat2(json, snapshot.rangedReduction);
        json.append("},");

        // Defense section
        json.append("\"defense\":{");
        json.append("\"armor\":"); appendDouble2(json, snapshot.armorValue); json.append(",");
        json.append("\"armorToughness\":"); appendDouble2(json, snapshot.armorToughness); json.append(",");
        json.append("\"knockbackResistance\":"); appendDouble2(json, snapshot.knockbackResistance); json.append(",");
        json.append("\"damageReduction\":"); appendFloat2(json, snapshot.totalDamageReduction);
        json.append("},");

        // Physical section
        json.append("\"physical\":{");
        json.append("\"reach\":"); appendDouble2(json, snapshot.reach); json.append(",");
        json.append("\"hitboxWidth\":"); appendFloat2(json, snapshot.hitboxWidth); json.append(",");
        json.append("\"hitboxHeight\":"); appendFloat2(json, snapshot.hitboxHeight); json.append(",");
        json.append("\"pehkuiScale\":"); appendNullableFloat2(json, snapshot.pehkuiScale); json.append(",");
        json.append("\"pehkuiHitboxScale\":"); appendNullableFloat2(json, snapshot.pehkuiHitboxScale);
        json.append("},");

        // Special abilities section - PERFORMANCE: Use bit-packed flags for booleans
        // flags bits: [0]=dashAvailable, [1]=dodgeAvailable, [2]=staminaFull, [3]=staminaEmpty
        boolean staminaFull = snapshot.stamina >= snapshot.maxStamina - 0.1f;
        boolean staminaEmpty = snapshot.stamina <= 0.1f;
        int abilityFlags = BitPackedFlags.packAbility(
            snapshot.dashAvailable, snapshot.dodgeAvailable, staminaFull, staminaEmpty, false, false);
        json.append("\"abilities\":{");
        json.append("\"stamina\":"); appendFloat2(json, snapshot.stamina); json.append(",");
        json.append("\"maxStamina\":"); appendFloat2(json, snapshot.maxStamina); json.append(",");
        json.append("\"dashCooldown\":"); appendFloat2(json, snapshot.dashCooldown); json.append(",");
        json.append("\"dodgeCooldown\":"); appendFloat2(json, snapshot.dodgeCooldown); json.append(",");
        json.append("\"flags\":").append(abilityFlags);
        json.append("},");

        // Modifiers section (from perk/mutator systems)
        json.append("\"modifiers\":{");
        json.append("\"hungerMultiplier\":"); appendFloat2(json, snapshot.hungerMultiplier); json.append(",");
        json.append("\"viewChunksBonus\":").append(snapshot.viewChunksBonus).append(",");
        json.append("\"resourceGatherSpeed\":"); appendFloat2(json, snapshot.resourceGatherSpeed); json.append(",");
        json.append("\"critChance\":"); appendFloat2(json, snapshot.critChance); json.append(",");
        json.append("\"critDamageMultiplier\":"); appendFloat2(json, snapshot.critDamageMultiplier); json.append(",");
        json.append("\"lifestealPercent\":"); appendFloat2(json, snapshot.lifestealPercent); json.append(",");
        json.append("\"styleMultiplier\":"); appendFloat2(json, snapshot.styleMultiplier);
        json.append("},");

        // Combat state (from combo system)
        json.append("\"combatState\":{");
        json.append("\"currentCombo\":").append(snapshot.currentCombo).append(",");
        json.append("\"styleRank\":\"").append(snapshot.styleRank != null ? snapshot.styleRank : "NONE").append("\",");
        json.append("\"styleScore\":").append(snapshot.styleScore);
        json.append("},");

        // Position for context
        json.append("\"position\":{");
        json.append("\"x\":"); appendDouble2(json, player.getX()); json.append(",");
        json.append("\"y\":"); appendDouble2(json, player.getY()); json.append(",");
        json.append("\"z\":"); appendDouble2(json, player.getZ()); json.append(",");
        json.append("\"dimension\":\"").append(player.level().dimension().location()).append("\"");
        json.append("}");

        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPlayerSnapshot(
            player.getUUID(), player.getGameProfile().getName(), trigger,
            snapshot.healthHp, snapshot.maxHealthHp, snapshot.healthHearts, snapshot.absorptionHp,
            snapshot.hungerLevel, snapshot.saturation, snapshot.exhaustion,
            snapshot.movementSpeed, snapshot.velocityX, snapshot.velocityY, snapshot.velocityZ,
            movementFlags, snapshot.meleeDamageMultiplier, snapshot.meleeReduction,
            snapshot.magicDamageMultiplier, snapshot.magicReduction,
            snapshot.rangedDamageMultiplier, snapshot.rangedReduction,
            snapshot.armorValue, snapshot.armorToughness, snapshot.knockbackResistance,
            snapshot.totalDamageReduction, snapshot.reach,
            snapshot.hitboxWidth, snapshot.hitboxHeight,
            snapshot.pehkuiScale != null ? (double) snapshot.pehkuiScale : null,
            snapshot.pehkuiHitboxScale != null ? (double) snapshot.pehkuiHitboxScale : null,
            snapshot.stamina, snapshot.maxStamina,
            snapshot.dashCooldown, snapshot.dodgeCooldown, abilityFlags,
            snapshot.currentCombo, snapshot.styleRank, snapshot.styleScore,
            player.getX(), player.getY(), player.getZ(),
            player.level().dimension().location().toString()
        );

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendPlayerAttributesLine(json.toString());
        }

        // Store for change detection
        previousSnapshots.put(player.getUUID(), snapshot);

        LOGGER.debug("[PlayerAttributes] Snapshot recorded for {} (trigger: {})", player.getGameProfile().getName(), trigger);
    }

    /**
     * Record attribute change event (called when specific attributes change).
     */
    public void recordAttributeChange(ServerPlayer player, String attributeName, double oldValue, double newValue) {
        String json = String.format(
            "{\"ts\":\"%s\",\"playerId\":\"%s\",\"type\":\"attribute_change\"," +
            "\"attribute\":\"%s\",\"oldValue\":%.4f,\"newValue\":%.4f,\"delta\":%.4f}",
            Instant.now(), player.getUUID(), escape(attributeName),
            oldValue, newValue, newValue - oldValue
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPlayerAttributeChange(
            player.getUUID(), attributeName, oldValue, newValue);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendPlayerAttributesLine(json);
        }
    }

    /**
     * Record health change event.
     */
    public void recordHealthChange(ServerPlayer player, float oldHealth, float newHealth, String source) {
        String json = String.format(
            "{\"ts\":\"%s\",\"playerId\":\"%s\",\"type\":\"health_change\"," +
            "\"oldHp\":%.2f,\"newHp\":%.2f,\"delta\":%.2f,\"source\":\"%s\"," +
            "\"hearts\":%d,\"maxHp\":%.2f}",
            Instant.now(), player.getUUID(),
            oldHealth, newHealth, newHealth - oldHealth, escape(source),
            (int) Math.ceil(newHealth / 2.0), player.getMaxHealth()
        );

        // DuckDB: Primary storage (use "health" as attribute name for health changes)
        DuckDBTelemetryService.INSTANCE.logPlayerAttributeChange(
            player.getUUID(), "health:" + source, oldHealth, newHealth);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendPlayerAttributesLine(json);
        }
    }

    /**
     * Record food/hunger change event.
     */
    public void recordFoodChange(ServerPlayer player, int oldLevel, int newLevel, float saturationChange) {
        String json = String.format(
            "{\"ts\":\"%s\",\"playerId\":\"%s\",\"type\":\"food_change\"," +
            "\"oldHunger\":%d,\"newHunger\":%d,\"hungerDelta\":%d," +
            "\"saturation\":%.2f,\"saturationDelta\":%.2f}",
            Instant.now(), player.getUUID(),
            oldLevel, newLevel, newLevel - oldLevel,
            player.getFoodData().getSaturationLevel(), saturationChange
        );

        // DuckDB: Primary storage (use "hunger" as attribute name for food changes)
        DuckDBTelemetryService.INSTANCE.logPlayerAttributeChange(
            player.getUUID(), "hunger", oldLevel, newLevel);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendPlayerAttributesLine(json);
        }
    }

    /**
     * Capture current player attribute snapshot.
     */
    private PlayerAttributeSnapshot captureSnapshot(ServerPlayer player) {
        PlayerAttributeSnapshot snapshot = new PlayerAttributeSnapshot();

        // Health
        snapshot.healthHp = player.getHealth();
        snapshot.maxHealthHp = player.getMaxHealth();
        snapshot.healthHearts = (int) Math.ceil(snapshot.healthHp / 2.0);
        snapshot.absorptionHp = player.getAbsorptionAmount();

        // Food
        var foodData = player.getFoodData();
        snapshot.hungerLevel = foodData.getFoodLevel();
        snapshot.saturation = foodData.getSaturationLevel();
        snapshot.exhaustion = getExhaustion(foodData);

        // Movement
        var movementAttr = player.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
        snapshot.movementSpeed = movementAttr != null ? movementAttr.getValue() : 0.1;
        var delta = player.getDeltaMovement();
        snapshot.velocityX = delta.x;
        snapshot.velocityY = delta.y;
        snapshot.velocityZ = delta.z;
        snapshot.isSprinting = player.isSprinting();
        snapshot.isSneaking = player.isShiftKeyDown();
        snapshot.isSwimming = player.isSwimming();
        snapshot.isFalling = player.fallDistance > 0;

        // Combat - Melee
        var attackDamageAttr = player.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        snapshot.meleeAttackDamage = attackDamageAttr != null ? attackDamageAttr.getValue() : 1.0;
        var attackSpeedAttr = player.getAttribute(Objects.requireNonNull(Attributes.ATTACK_SPEED));
        snapshot.meleeAttackSpeed = attackSpeedAttr != null ? attackSpeedAttr.getValue() : 4.0;

        // Defense
        snapshot.armorValue = player.getArmorValue();
        var armorToughnessAttr = player.getAttribute(Objects.requireNonNull(Attributes.ARMOR_TOUGHNESS));
        snapshot.armorToughness = armorToughnessAttr != null ? armorToughnessAttr.getValue() : 0.0;
        var knockbackAttr = player.getAttribute(Objects.requireNonNull(Attributes.KNOCKBACK_RESISTANCE));
        snapshot.knockbackResistance = knockbackAttr != null ? knockbackAttr.getValue() : 0.0;

        // Physical - Reach
        var reachAttr = player.getAttribute(Objects.requireNonNull(Attributes.BLOCK_INTERACTION_RANGE));
        snapshot.reach = reachAttr != null ? reachAttr.getValue() : 4.5;

        // Physical - Hitbox
        var boundingBox = player.getBoundingBox();
        snapshot.hitboxWidth = (float) (boundingBox.maxX - boundingBox.minX);
        snapshot.hitboxHeight = (float) (boundingBox.maxY - boundingBox.minY);

        // Pehkui integration
        if (PehkuiIntegration.isAvailable()) {
            snapshot.pehkuiScale = PehkuiIntegration.getScale(player);
            snapshot.pehkuiHitboxScale = PehkuiIntegration.getHitboxScale(player);
        }

        // Get perk/mutator modifiers if in quest
        populatePerkModifiers(player, snapshot);
        populateMutatorModifiers(player, snapshot);
        populateComboState(player, snapshot);
        populateAbilityData(player, snapshot);

        return snapshot;
    }

    /**
     * Populate modifiers from PerkSystem if player is in a quest.
     */
    private void populatePerkModifiers(ServerPlayer player, PlayerAttributeSnapshot snapshot) {
        var sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());
        if (sessionOpt.isPresent()) {
            var session = sessionOpt.get();

            snapshot.meleeDamageMultiplier = session.getDamageMultiplier();
            snapshot.meleeReduction = session.getDamageReductionMultiplier();
            snapshot.critChance = session.getCritChanceBonus();
            snapshot.critDamageMultiplier = session.getCritDamageMultiplier();
            snapshot.lifestealPercent = session.getLifestealPercent();
            snapshot.styleMultiplier = session.getStyleMultiplier();

            // Elemental chances affect magic damage conceptually
            snapshot.magicDamageMultiplier = 1.0f +
                session.getFireChance() * 0.5f +
                session.getFreezeChance() * 0.5f +
                session.getLightningChance() * 0.5f;

            // Movement speed modifier
            snapshot.movementSpeed *= session.getMovementSpeedMultiplier();

            // Total damage reduction from perks
            snapshot.totalDamageReduction = 1.0f - session.getDamageReductionMultiplier();
        } else {
            // Defaults when not in quest
            snapshot.meleeDamageMultiplier = 1.0f;
            snapshot.meleeReduction = 1.0f;
            snapshot.magicDamageMultiplier = 1.0f;
            snapshot.magicReduction = 1.0f;
            snapshot.rangedDamageMultiplier = 1.0f;
            snapshot.rangedReduction = 1.0f;
            snapshot.critChance = 0.0f;
            snapshot.critDamageMultiplier = 1.5f;
            snapshot.lifestealPercent = 0.0f;
            snapshot.styleMultiplier = 1.0f;
            snapshot.totalDamageReduction = 0.0f;
        }
    }

    /**
     * Populate modifiers from MutatorSystem if active.
     */
    private void populateMutatorModifiers(ServerPlayer player, PlayerAttributeSnapshot snapshot) {
        var sessionOpt = MutatorSystem.INSTANCE.getSession(player.getUUID());
        if (sessionOpt.isPresent()) {
            var session = sessionOpt.get();

            // Mutators can affect player damage and speed
            snapshot.meleeDamageMultiplier *= session.getPlayerDamageMultiplier();
            snapshot.rangedDamageMultiplier *= session.getPlayerDamageMultiplier();
            snapshot.movementSpeed *= session.getPlayerSpeedMultiplier();

            // Special mutator effects
            if (session.hasLowGravity()) {
                snapshot.velocityY *= 0.5; // Slower falling
            }
            if (session.hasHighGravity()) {
                snapshot.velocityY *= 1.5; // Faster falling
            }
        }
    }

    /**
     * Populate combat state from ComboSystem.
     */
    private void populateComboState(ServerPlayer player, PlayerAttributeSnapshot snapshot) {
        var comboSessionOpt = ComboSystem.INSTANCE.getSession(player.getUUID());
        if (comboSessionOpt.isPresent()) {
            var comboSession = comboSessionOpt.get();
            snapshot.currentCombo = comboSession.getCurrentCombo();
            snapshot.styleRank = comboSession.getCurrentRank().name();
            snapshot.styleScore = comboSession.getStyleScore();
        } else {
            snapshot.currentCombo = 0;
            snapshot.styleRank = null;
            snapshot.styleScore = 0;
        }
    }

    /**
     * Populate ability data from StaminaSystem, DashAbilitySystem, DodgeAbilitySystem.
     */
    private void populateAbilityData(ServerPlayer player, PlayerAttributeSnapshot snapshot) {
        UUID playerId = player.getUUID();

        // Stamina
        snapshot.stamina = StaminaSystem.INSTANCE.getStamina(playerId);
        snapshot.maxStamina = StaminaSystem.INSTANCE.getMaxStamina(playerId);

        // Dash
        snapshot.dashAvailable = DashAbilitySystem.INSTANCE.isDashAvailable(playerId);
        snapshot.dashCooldown = DashAbilitySystem.INSTANCE.getCooldownPercent(playerId);

        // Dodge
        snapshot.dodgeAvailable = DodgeAbilitySystem.INSTANCE.isDodgeAvailable(playerId);
        snapshot.dodgeCooldown = DodgeAbilitySystem.INSTANCE.getCooldownPercent(playerId);
    }

    /**
     * Get exhaustion level via cached reflection field (not directly exposed).
     * PERFORMANCE: Uses static cached field instead of repeated lookups.
     */
    private float getExhaustion(net.minecraft.world.food.FoodData foodData) {
        if (EXHAUSTION_FIELD == null) return 0.0f;
        try {
            return EXHAUSTION_FIELD.getFloat(foodData);
        } catch (IllegalAccessException e) {
            return 0.0f;
        }
    }

    /**
     * Clean up tracking data for a player.
     */
    public void cleanupPlayer(UUID playerId) {
        lastSnapshotTick.remove(playerId);
        previousSnapshots.remove(playerId);
    }

    /**
     * Get previous snapshot for comparison.
     */
    public PlayerAttributeSnapshot getPreviousSnapshot(UUID playerId) {
        return previousSnapshots.get(playerId);
    }

    // ============================================
    // PERFORMANCE: Fast number formatting helpers
    // These avoid String.format() overhead (~10x faster)
    // ============================================

    /** Append float with 2 decimal places without String.format() */
    private static void appendFloat2(StringBuilder sb, float value) {
        long scaled = Math.round(value * 100.0);
        long intPart = scaled / 100;
        long decPart = Math.abs(scaled % 100);
        sb.append(intPart).append('.');
        if (decPart < 10) sb.append('0');
        sb.append(decPart);
    }

    /** Append double with 2 decimal places without String.format() */
    private static void appendDouble2(StringBuilder sb, double value) {
        long scaled = Math.round(value * 100.0);
        long intPart = scaled / 100;
        long decPart = Math.abs(scaled % 100);
        sb.append(intPart).append('.');
        if (decPart < 10) sb.append('0');
        sb.append(decPart);
    }

    /** Append double with 4 decimal places without String.format() */
    private static void appendDouble4(StringBuilder sb, double value) {
        long scaled = Math.round(value * 10000.0);
        long intPart = scaled / 10000;
        long decPart = Math.abs(scaled % 10000);
        sb.append(intPart).append('.');
        if (decPart < 10) sb.append("000");
        else if (decPart < 100) sb.append("00");
        else if (decPart < 1000) sb.append('0');
        sb.append(decPart);
    }

    /** Append nullable Float with 2 decimal places, or "null" if null */
    private static void appendNullableFloat2(StringBuilder sb, Float value) {
        if (value == null) {
            sb.append("null");
        } else {
            appendFloat2(sb, value);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Complete snapshot of player attributes at a point in time.
     */
    public static class PlayerAttributeSnapshot {
        // Health
        public float healthHp;
        public float maxHealthHp;
        public int healthHearts;
        public float absorptionHp;

        // Food
        public int hungerLevel;
        public float saturation;
        public float exhaustion;

        // Movement
        public double movementSpeed;
        public double velocityX;
        public double velocityY;
        public double velocityZ;
        public boolean isSprinting;
        public boolean isSneaking;
        public boolean isSwimming;
        public boolean isFalling;

        // Combat - Melee
        public double meleeAttackDamage;
        public double meleeAttackSpeed;
        public float meleeDamageMultiplier;
        public float meleeReduction;

        // Combat - Magic
        public float magicDamageMultiplier;
        public float magicReduction;

        // Combat - Ranged
        public float rangedDamageMultiplier;
        public float rangedReduction;

        // Defense
        public double armorValue;
        public double armorToughness;
        public double knockbackResistance;
        public float totalDamageReduction;

        // Physical
        public double reach;
        public float hitboxWidth;
        public float hitboxHeight;
        public Float pehkuiScale;
        public Float pehkuiHitboxScale;

        // Abilities (from StaminaSystem, DashAbilitySystem, DodgeAbilitySystem)
        public float stamina;
        public float maxStamina;
        public boolean dashAvailable;
        public float dashCooldown;
        public boolean dodgeAvailable;
        public float dodgeCooldown;

        // Modifiers
        public float hungerMultiplier = 1.0f;
        public int viewChunksBonus = 0;
        public float resourceGatherSpeed = 1.0f;
        public float critChance;
        public float critDamageMultiplier;
        public float lifestealPercent;
        public float styleMultiplier;

        // Combat state
        public int currentCombo;
        public String styleRank;
        public int styleScore;
    }
}
