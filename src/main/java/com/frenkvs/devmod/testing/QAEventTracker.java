package com.frenkvs.devmod.testing;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.HitHelper;
import com.frenkvs.devmod.hud.ImpactHudOverlay;
import com.frenkvs.devmod.rendering.DebugRenderer;
import com.frenkvs.devmod.rendering.LightLevelOverlay;
import com.frenkvs.devmod.rendering.LineOfSightVisualizer;
import com.frenkvs.devmod.rendering.PathfindingDebugger;
import com.frenkvs.devmod.rendering.RoomBoundsVisualizer;
import com.frenkvs.devmod.rendering.SafeSpotVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Event tracker that hooks into game events to automatically update TesterProgress.
 * This enables event-driven auto-completion of tests without polling.
 *
 * Tracks: kills, damage, explosions, potions, effects, and more.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class QAEventTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(QAEventTracker.class);

    // Track last hit info for correlating damage with kills
    private static String lastHitBodyPart = null;
    private static boolean lastHitWasCritical = false;
    private static boolean lastHitWasMaceSmash = false;
    private static ItemStack lastWeaponUsed = ItemStack.EMPTY;
    private static long lastDamageTime = 0;

    // Periodic save counter
    private static int tickCounter = 0;
    private static final int SAVE_INTERVAL = 600; // Every 30 seconds (600 ticks)
    private static final int ACHIEVEMENT_CHECK_INTERVAL = 100; // Every 5 seconds

    /**
     * Reset state when player leaves world. Call from ClientModEvents.onPlayerLogout.
     */
    public static void resetWorldState() {
        testingSystemInitialized = false;
        initDelayTicks = 0;
        tickCounter = 0;
    }

    /**
     * Track damage dealt by player to mobs.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        float damage = event.getNewDamage();

        // Check if player dealt this damage
        Entity attacker = source.getEntity();
        if (attacker == null || !attacker.equals(mc.player)) {
            // Player took damage instead
            if (target.equals(mc.player)) {
                TesterProgress.INSTANCE.onDamageTaken(damage, source);
            }
            return;
        }

        // Player dealt damage to a mob
        Player player = Objects.requireNonNull(mc.player);
        ItemStack weapon = player.getMainHandItem();

        // Detect body part from HitHelper if available
        String bodyPart = HitHelper.getLastHitPart();
        if (bodyPart == null) bodyPart = "BODY";

        // Check for critical hit
        boolean isCritical = player.fallDistance > 0 && !player.onGround() &&
                            !player.isInWater() && !player.isPassenger();

        // Check for mace smash (mace + falling)
        boolean isMaceSmash = weapon.is(java.util.Objects.requireNonNull(Items.MACE)) && player.fallDistance > 1.5f;

        // Check if arrow - used in tracking
        boolean isArrow = source.getDirectEntity() instanceof Arrow;

        // Store for kill correlation
        lastHitBodyPart = bodyPart;
        lastHitWasCritical = isCritical;
        lastHitWasMaceSmash = isMaceSmash;
        lastWeaponUsed = weapon.copy();
        lastDamageTime = System.currentTimeMillis();

        // Record damage (note: isArrow tracked separately in kill events)
        TesterProgress.INSTANCE.onDamageDealt(damage, bodyPart, weapon.getItem(), isCritical || isArrow);

        LOGGER.debug("Damage tracked: {} dmg to {} ({}) with {}",
            damage, target.getName().getString(), bodyPart, weapon.getHoverName().getString());
    }

    /**
     * Track mob kills by player.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LivingEntity dead = event.getEntity();
        DamageSource source = event.getSource();

        // Check if player caused the death
        Entity killer = source.getEntity();
        Player player = mc.player;
        if (killer == null || player == null || !killer.equals(player)) {
            return;
        }

        // Use cached hit info if recent (within 500ms)
        String bodyPart = "BODY";
        boolean isCritical = false;
        boolean isMaceSmash = false;
        ItemStack weapon = player.getMainHandItem();

        if (System.currentTimeMillis() - lastDamageTime < 500) {
            String cachedBodyPart = lastHitBodyPart;
            bodyPart = cachedBodyPart != null ? cachedBodyPart : "BODY";
            isCritical = lastHitWasCritical;
            isMaceSmash = lastHitWasMaceSmash;
            ItemStack cachedWeapon = lastWeaponUsed;
            if (cachedWeapon != null && !cachedWeapon.isEmpty()) {
                weapon = cachedWeapon;
            }
        }

        // Check if headshot
        boolean isHeadshot = "HEAD".equalsIgnoreCase(bodyPart);

        // Check if arrow kill
        boolean isArrow = source.getDirectEntity() instanceof Arrow;

        // Get final damage for tracking
        float damage = dead.getMaxHealth(); // Approximate

        // Record the kill
        TesterProgress.INSTANCE.onMobKill(
            dead.getType(),
            weapon.getItem(),
            bodyPart,
            isCritical,
            isHeadshot,
            isMaceSmash,
            isArrow,
            damage
        );

        // Check for enchanted weapon kill - use modern DataComponents API
        ItemEnchantments enchants = weapon.getOrDefault(
            java.util.Objects.requireNonNull(DataComponents.ENCHANTMENTS),
            java.util.Objects.requireNonNull(ItemEnchantments.EMPTY)
        );
        if (!enchants.isEmpty()) {
            // Track as enchanted kill
            TesterProgress.INSTANCE.onEnchantedKill("enchanted_weapon");
        }

        LOGGER.debug("Kill tracked: {} ({}) - Headshot: {}, Mace: {}, Arrow: {}",
            dead.getName().getString(), bodyPart, isHeadshot, isMaceSmash, isArrow);
    }

    /**
     * Track explosions.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Check explosion source
        Entity exploder = event.getExplosion().getDirectSourceEntity();
        boolean isTNT = false;
        boolean isCreeper = false;

        if (exploder != null) {
            isCreeper = exploder instanceof Creeper;
            // TNT detection - check if the explosion originated from a TNT block
            // The source entity for TNT is a PrimedTnt entity
            isTNT = exploder.getType() == EntityType.TNT;
        }

        // Count mobs killed by this explosion
        List<Entity> affected = event.getAffectedEntities();
        int mobsKilled = 0;
        for (Entity entity : affected) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                // Approximate - entity in explosion radius with low health
                if (living.getHealth() < living.getMaxHealth() * 0.3f) {
                    mobsKilled++;
                }
            }
        }

        TesterProgress.INSTANCE.onExplosion(isTNT, isCreeper, mobsKilled);

        LOGGER.debug("Explosion tracked: TNT={}, Creeper={}, MobsAffected={}",
            isTNT, isCreeper, mobsKilled);
    }

    /**
     * Track potion effects gained.
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Only track effects on the player
        if (!event.getEntity().equals(mc.player)) return;

        MobEffectInstance effect = event.getEffectInstance();
        if (effect != null) {
            net.minecraft.resources.ResourceLocation effectKey = BuiltInRegistries.MOB_EFFECT.getKey(
                java.util.Objects.requireNonNull(effect.getEffect().value())
            );
            if (effectKey != null) {
                String effectName = effectKey.toString();
                TesterProgress.INSTANCE.onEffectGained(effectName);
                LOGGER.debug("Effect gained: {}", effectName);
            }
        }
    }

    // Delay testing system initialization to prevent blocking world load
    private static boolean testingSystemInitialized = false;
    private static int initDelayTicks = 0;
    private static final int INIT_DELAY = 200; // 10 seconds to let world fully load

    /**
     * Periodic tick for maintenance tasks.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !event.getEntity().equals(mc.player)) return;

        tickCounter++;

        // Delay testing system initialization until world is stable
        // This prevents FileSystem errors during "Preparing spawn area"
        if (!testingSystemInitialized) {
            initDelayTicks++;
            if (initDelayTicks < INIT_DELAY) {
                return; // Skip all testing system calls until world is stable
            }
            testingSystemInitialized = true;
            LOGGER.debug("QA Testing system initialized after world load delay");
        }

        try {
            // Periodic save
            if (tickCounter % SAVE_INTERVAL == 0) {
                TesterProgress.INSTANCE.flush();
                TesterProgress.INSTANCE.updatePlayTime();
            }

            // Periodic achievement check
            if (tickCounter % ACHIEVEMENT_CHECK_INTERVAL == 0) {
                TesterProfile.INSTANCE.checkAchievements(TesterProgress.INSTANCE);

                // Also check test auto-completion
                checkTestCompletions();
            }

            // Track overlay toggles (check state changes)
            trackOverlayStates();
        } catch (Exception e) {
            // Don't let testing system errors affect gameplay
            LOGGER.debug("QA testing tick error: {}", e.getMessage());
        }
    }

    // Previous overlay states for detecting toggles
    private static boolean prevImpactHud = false;
    private static boolean prevDebugRenderer = false;
    private static boolean prevPathfinding = false;
    private static boolean prevLightLevel = false;
    private static boolean prevRoomBounds = false;
    private static boolean prevLineOfSight = false;
    private static boolean prevSafeSpots = false;

    /**
     * Detect overlay state changes and track toggles.
     */
    private static void trackOverlayStates() {
        // Impact HUD
        boolean currentImpactHud = ImpactHudOverlay.isEnabled();
        if (currentImpactHud != prevImpactHud) {
            if (currentImpactHud) TesterProgress.INSTANCE.onOverlayToggle("impact_hud");
            prevImpactHud = currentImpactHud;
        }

        // Debug Renderer
        boolean currentDebugRenderer = DebugRenderer.INSTANCE.isEnabled();
        if (currentDebugRenderer != prevDebugRenderer) {
            if (currentDebugRenderer) TesterProgress.INSTANCE.onOverlayToggle("debug_renderer");
            prevDebugRenderer = currentDebugRenderer;
        }

        // Pathfinding
        boolean currentPathfinding = PathfindingDebugger.INSTANCE.isEnabled();
        if (currentPathfinding != prevPathfinding) {
            if (currentPathfinding) TesterProgress.INSTANCE.onOverlayToggle("pathfinding");
            prevPathfinding = currentPathfinding;
        }

        // Light Level
        boolean currentLightLevel = LightLevelOverlay.INSTANCE.isEnabled();
        if (currentLightLevel != prevLightLevel) {
            if (currentLightLevel) TesterProgress.INSTANCE.onOverlayToggle("light_level");
            prevLightLevel = currentLightLevel;
        }

        // Room Bounds
        boolean currentRoomBounds = RoomBoundsVisualizer.INSTANCE.isEnabled();
        if (currentRoomBounds != prevRoomBounds) {
            if (currentRoomBounds) TesterProgress.INSTANCE.onOverlayToggle("room_bounds");
            prevRoomBounds = currentRoomBounds;
        }

        // Line of Sight
        boolean currentLineOfSight = LineOfSightVisualizer.INSTANCE.isEnabled();
        if (currentLineOfSight != prevLineOfSight) {
            if (currentLineOfSight) TesterProgress.INSTANCE.onOverlayToggle("line_of_sight");
            prevLineOfSight = currentLineOfSight;
        }

        // Safe Spots
        boolean currentSafeSpots = SafeSpotVisualizer.INSTANCE.isEnabled();
        if (currentSafeSpots != prevSafeSpots) {
            if (currentSafeSpots) TesterProgress.INSTANCE.onOverlayToggle("safe_spots");
            prevSafeSpots = currentSafeSpots;
        }
    }

    /**
     * Check all tests for auto-completion based on current progress.
     */
    private static void checkTestCompletions() {
        TestingSession session = TestingSession.INSTANCE;
        if (!session.isSessionActive()) return;

        TesterProgress progress = TesterProgress.INSTANCE;
        boolean anyCompleted = false;

        for (TestCase test : session.getAllTests()) {
            // Skip already completed tests
            if (test.getStatus() == TestCase.TestStatus.PASSED ||
                test.getStatus() == TestCase.TestStatus.FAILED ||
                test.getStatus() == TestCase.TestStatus.SKIPPED) {
                continue;
            }

            // Check progress-based completion
            float testProgress = getTestProgress(test.getId(), progress);
            if (testProgress >= 1.0f) {
                test.markPassed("Auto-completed via gameplay");
                progress.onTestCompleted(true);
                TesterProfile.INSTANCE.onTestCompleted();
                TesterProfile.INSTANCE.awardXP(getTestXP(test), "Test completed: " + test.getName());

                // Notify
                QANotificationSystem.INSTANCE.showTestCompleted(test);

                anyCompleted = true;
                LOGGER.info("Test auto-completed: {}", test.getName());
            }
        }

        if (anyCompleted) {
            session.saveSession();
        }
    }

    /**
     * Calculate progress for a specific test (0.0 - 1.0).
     */
    public static float getTestProgress(String testId, TesterProgress progress) {
        return switch (testId) {
            // Damage System Tests
            case "dmg_melee_basic" -> progress.getHitsDealt() >= 1 ? 1.0f : 0.0f;
            case "dmg_ranged_arrow" -> Math.min(1.0f, progress.getArrowKills() / 3.0f);
            case "dmg_mace_smash" -> progress.getMaceSmashKills() >= 1 ? 1.0f : 0.0f;
            case "dmg_body_parts" -> {
                int parts = 0;
                if (progress.getHitsOnBodyPart("HEAD") > 0) parts++;
                if (progress.getHitsOnBodyPart("BODY") > 0) parts++;
                if (progress.getHitsOnBodyPart("ARMS") > 0) parts++;
                if (progress.getHitsOnBodyPart("LEGS") > 0) parts++;
                yield parts / 4.0f;
            }
            case "dmg_enderman_evade" -> progress.getEvasionsWitnessed() >= 1 ? 1.0f : 0.0f;
            case "dmg_environmental" -> {
                int types = 0;
                if (progress.getFallDamageTaken() > 0) types++;
                if (progress.getFireDamageTaken() > 0) types++;
                if (progress.getLavaDamageTaken() > 0) types++;
                yield types / 3.0f;
            }

            // Overlay Tests
            case "ovl_impact_hud" -> progress.getOverlayToggles("impact_hud") >= 1 ? 1.0f : 0.0f;
            case "ovl_debug_renderer" -> progress.getOverlayToggles("debug_renderer") >= 1 ? 1.0f : 0.0f;
            case "ovl_pathfinding" -> progress.getOverlayToggles("pathfinding") >= 1 ? 1.0f : 0.0f;
            case "ovl_light_level" -> progress.getOverlayToggles("light_level") >= 1 ? 1.0f : 0.0f;
            case "ovl_room_bounds" -> progress.getOverlayToggles("room_bounds") >= 1 ? 1.0f : 0.0f;
            case "ovl_safe_spots" -> progress.getOverlayToggles("safe_spots") >= 1 ? 1.0f : 0.0f;
            case "ovl_los" -> progress.getOverlayToggles("line_of_sight") >= 1 ? 1.0f : 0.0f;

            // UI Tests
            case "ui_telemetry_dash" -> progress.getScreenOpens("telemetry") >= 1 ? 1.0f : 0.0f;
            case "ui_mob_config" -> progress.getScreenOpens("mob_config") >= 1 ? 1.0f : 0.0f;
            case "ui_weapon_editor" -> progress.getScreenOpens("weapon_editor") >= 1 ? 1.0f : 0.0f;

            // Performance Tests - require longer play time
            case "perf_fps_stable" -> Math.min(1.0f, progress.getPlayTimeMs() / (120_000f)); // 2 min
            case "perf_no_freeze" -> Math.min(1.0f, progress.getPlayTimeMs() / (180_000f)); // 3 min
            case "perf_memory" -> Math.min(1.0f, progress.getPlayTimeMs() / (600_000f)); // 10 min

            // Keybind Tests
            case "key_all_work" -> {
                int overlays = progress.getTotalOverlayToggles();
                yield Math.min(1.0f, overlays / 7.0f); // All 7 overlays
            }

            // Telemetry Tests
            case "tel_export" -> progress.getScreenOpens("telemetry") >= 3 ? 1.0f :
                                 Math.min(0.9f, progress.getScreenOpens("telemetry") / 3.0f);
            case "tel_visualize" -> progress.getScreenOpens("telemetry") >= 2 ? 1.0f : 0.5f;

            // Default - no auto-progress
            default -> 0.0f;
        };
    }

    /**
     * Get XP reward for completing a test.
     */
    private static int getTestXP(TestCase test) {
        return switch (test.getPriority()) {
            case CRITICAL -> 50;
            case HIGH -> 35;
            case MEDIUM -> 25;
            case LOW -> 15;
        };
    }

    /**
     * Record screen open events (call from screen constructors).
     */
    public static void onScreenOpened(String screenName) {
        TesterProgress.INSTANCE.onScreenOpen(screenName);
        LOGGER.debug("Screen opened: {}", screenName);
    }

    /**
     * Record when a potion is used (thrown or drunk).
     */
    public static void onPotionUsed(String effectType, boolean isSplash, boolean isLingering) {
        TesterProgress.INSTANCE.onPotionUsed(effectType, isSplash, isLingering);
        LOGGER.debug("Potion used: {} (splash={}, lingering={})", effectType, isSplash, isLingering);
    }

    /**
     * Record shield block events.
     */
    public static void onShieldBlock() {
        TesterProgress.INSTANCE.onAttackBlocked();
        LOGGER.debug("Shield block recorded");
    }

    /**
     * Record Enderman teleport evasion.
     */
    public static void onEndermanEvade() {
        TesterProgress.INSTANCE.onEvasionWitnessed();
        LOGGER.debug("Enderman evasion recorded");
    }
}
