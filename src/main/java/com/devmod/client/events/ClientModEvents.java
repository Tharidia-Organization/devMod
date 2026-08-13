package com.devmod.client.events;

import java.text.DecimalFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import com.devmod.ModConfig;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.notification.ClientNotificationManager;
import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.testing.QAEventTracker;
import com.devmod.client.testing.QANotificationSystem;
import com.devmod.client.testing.TestingSession;
import com.devmod.client.testing.TutorialManager;
import com.devmod.client.transport.TransportClientPayloadHooks;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.combat.HitHelper;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.quest.QuestManager;
import com.devmod.testing.TesterProfile;
import com.devmod.util.I18n;

import static com.devmod.DevMod.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class ClientModEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientModEvents.class);

    private static final DecimalFormat df = new DecimalFormat("#.##");
    private static final ResourceLocation MOB_STATS_ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mob_stats"));
    private static final ResourceLocation QA_NOTIFICATIONS_ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "qa_notifications"));
    private static final ResourceLocation RESONANCE_HUD_ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "resonance_hud"));
    private static final ResourceLocation CONTRACT_HUD_ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "contract_hud"));
    private static final ResourceLocation TRANSPORT_HUD_ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "transport_hud"));

    // Track if profile listener is registered
    private static boolean profileListenerRegistered = false;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        Objects.requireNonNull(event, "RegisterGuiLayersEvent");
        event.registerAboveAll(Objects.requireNonNull(MOB_STATS_ID), new MobStatsLayer());
        event.registerAboveAll(Objects.requireNonNull(QA_NOTIFICATIONS_ID), new QANotificationsLayer());
        event.registerAboveAll(Objects.requireNonNull(RESONANCE_HUD_ID), Objects.requireNonNull(com.devmod.client.overlay.ResonanceHudOverlay.INSTANCE));
        event.registerAboveAll(Objects.requireNonNull(CONTRACT_HUD_ID), Objects.requireNonNull(com.devmod.client.overlay.ContractHudOverlay.INSTANCE));
        event.registerAboveAll(Objects.requireNonNull(TRANSPORT_HUD_ID), Objects.requireNonNull(TransportClientPayloadHooks.getOverlay()));
        com.devmod.client.overlay.CombatFlowHudOverlay.registerGuiLayers(event);
        com.devmod.client.overlay.EconomyOverlay.registerGuiLayers(event);
        com.devmod.client.overlay.EntityDensityOverlay.registerGuiLayers(event);
        com.devmod.client.nexus.NexusBuildProgressOverlay.register(event);

        // Register profile listener for notifications (once)
        if (!profileListenerRegistered) {
            TesterProfile.INSTANCE.addListener(new TesterProfile.ProfileEventListener() {
                @Override
                public void onLevelUp(int newLevel, String title) {
                    QANotificationSystem.INSTANCE.showLevelUp(newLevel, title);
                }

                @Override
                public void onAchievementUnlocked(TesterProfile.Achievement achievement) {
                    QANotificationSystem.INSTANCE.showAchievement(achievement);
                }

                @Override
                public void onBadgeEarned(TesterProfile.Badge badge) {
                    QANotificationSystem.INSTANCE.showBadge(badge);
                }

                @Override
                public void onStreakUpdated(int currentStreak) {
                    if (currentStreak == 3 || currentStreak == 7 || currentStreak == 30) {
                        float bonus = TesterProfile.INSTANCE.getStreakBonus();
                        QANotificationSystem.INSTANCE.showStreak(currentStreak, bonus);
                    }
                }
            });
            profileListenerRegistered = true;
            LOGGER.debug("TesterProfile notification listener registered");
        }
    }

    /**
     * Layer for rendering QA testing notifications (achievements, level ups, etc.)
     */
    public static class QANotificationsLayer implements LayeredDraw.Layer {
        @Override
        public void render(@Nonnull GuiGraphics guiGraphics, @Nonnull DeltaTracker deltaTracker) {
            QANotificationSystem.INSTANCE.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }

    /**
     * Load dynamic tests when player joins a world.
     * This is when registries are fully available.
     * Note: We run this async to avoid blocking the main thread during world load.
     */
    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Objects.requireNonNull(event, "LoggingIn event");
        LOGGER.debug("Player logging in, scheduling dynamic tests load...");

        // Load quest data
        QuestManager.INSTANCE.load();

        // Load settings (for onboarding check)
        SettingsManager.INSTANCE.load();

        // Load damage statistics from disk
        com.devmod.testing.stats.DamageStatistics.INSTANCE.load();

        // Reload weapon detection lists (whitelist/blacklist) - client-only
        try {
            com.devmod.client.ui.editor.WeaponTypeDetector.reloadWeaponLists();
            LOGGER.debug("WeaponTypeDetector lists reloaded successfully");
        } catch (Exception e) {
            LOGGER.warn("Failed to reload weapon lists: {}", e.getMessage());
        }

        // Run async to avoid blocking main thread during world load
        CompletableFuture<Void> dynamicTestsLoad = CompletableFuture.runAsync(() -> {
            try {
                // Small delay to let world finish loading
                Thread.sleep(2000);

                LOGGER.debug("Loading dynamic tests asynchronously...");
                TestingSession.INSTANCE.loadDynamicTests();

                LOGGER.info("Dynamic tests loaded: {} tests from modpack",
                    TestingSession.INSTANCE.getDynamicTestCount());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Dynamic test loading interrupted");
            } catch (Exception e) {
                LOGGER.error("Failed to load dynamic tests: {}", e.getMessage());
            }
        });
        dynamicTestsLoad.exceptionally(ex -> {
            LOGGER.error("Dynamic test load task failed", ex);
            return null;
        });

        // Check for Welcome Screen (first-time users)
        if (!SettingsManager.INSTANCE.getSettings().onboarding.hasSeenWelcome) {
            // Schedule welcome screen with retry logic
            scheduleWelcomeScreen(0);
        }
    }

    // === Welcome Screen Retry System ===
    private static final int WELCOME_INITIAL_DELAY_MS = 3000;  // Initial delay before first attempt
    private static final int WELCOME_RETRY_DELAY_MS = 2000;    // Delay between retry attempts
    private static final int WELCOME_MAX_RETRIES = 4;          // Max retry attempts (~11 seconds total)
    private static volatile boolean welcomeScreenShown = false;

    /**
     * Schedules the welcome screen with retry logic.
     * If another screen is open (e.g., inventory), it will retry until successful
     * or max retries is reached.
     *
     * @param attempt Current attempt number (0 for initial)
     */
    private static void scheduleWelcomeScreen(int attempt) {
        if (welcomeScreenShown) return;  // Already shown this session

        int delayMs = attempt == 0 ? WELCOME_INITIAL_DELAY_MS : WELCOME_RETRY_DELAY_MS;

        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> {
            Minecraft.getInstance().execute(() -> {
                // Check if already shown (race condition guard)
                if (welcomeScreenShown) return;

                // Check if user already dismissed/saw the welcome screen via settings
                if (SettingsManager.INSTANCE.getSettings().onboarding.hasSeenWelcome) {
                    LOGGER.debug("Welcome screen already seen (settings), skipping");
                    welcomeScreenShown = true;
                    return;
                }

                Minecraft mc = Minecraft.getInstance();

                // Check if player is still in world
                if (mc.level == null || mc.player == null) {
                    LOGGER.debug("Player left world, canceling welcome screen");
                    return;
                }

                // Try to show screen if no other screen is open
                var currentScreen = mc.screen;
                if (currentScreen == null) {
                    ActionRegistry.invoke(ActionIds.UI_WELCOME_OPEN,
                        ClientActionContexts.forClient(ActionOrigin.EVENT));
                    welcomeScreenShown = true;
                    LOGGER.info("Showing welcome screen for first-time user (attempt {})", attempt + 1);
                } else {
                    // Another screen is open - retry if under max attempts
                    if (attempt < WELCOME_MAX_RETRIES) {
                        LOGGER.debug("Welcome screen blocked by {} - retrying (attempt {}/{})",
                            currentScreen.getClass().getSimpleName(), attempt + 1, WELCOME_MAX_RETRIES);
                        scheduleWelcomeScreen(attempt + 1);
                    } else {
                        // Max retries reached - show notification instead
                        LOGGER.warn("Welcome screen could not be shown after {} attempts, showing chat notification",
                            WELCOME_MAX_RETRIES);
                        welcomeScreenShown = true;  // Prevent further attempts
                        showWelcomeFallbackNotification();
                    }
                }
            });
        });
    }

    /**
     * Shows a toast notification as fallback when welcome screen couldn't be displayed.
     * This ensures first-time users still learn about the mod with a proper UI overlay.
     */
    private static void showWelcomeFallbackNotification() {
        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.notification.welcome.title")
            .messageKey("devmod.notification.welcome.message")
            .priority(NotificationPriority.NORMAL)
            .soundId("system.info")
            .displayDurationMs(5000)
            .persistToMailbox(false)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    /**
     * Cleanup caches when player disconnects from server/world.
     * Prevents memory leaks from stale entity references.
     */
    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LOGGER.debug("Player logging out, clearing caches...");

        // Reset welcome screen flag for next session
        welcomeScreenShown = false;

        // Save quest data before logout
        QuestManager.INSTANCE.save();

        // Save damage statistics before logout
        com.devmod.testing.stats.DamageStatistics.INSTANCE.save();

        // Shutdown async save executors (ensures pending saves complete)
        TestingSession.INSTANCE.shutdown();
        TutorialManager.INSTANCE.shutdown();

        // Clear body part calculation cache
        HitHelper.clearCache();

        // Clear 3D impact panels (may hold entity references)
        Impact3DPanelManager.INSTANCE.clear();

        // Clear telepad occlusion tracking
        com.devmod.clone.client.renderer.TelepadDepthRenderer.clearAll();

        // Clear notifications
        QANotificationSystem.INSTANCE.clear();
        ClientNotificationManager.INSTANCE.clear();
        com.devmod.client.overlay.ResonanceHudOverlay.INSTANCE.clear();

        // Reset QA event tracker state for next world load
        QAEventTracker.resetWorldState();

        // Clear Endurance Quest client caches
        com.devmod.client.endurance.ClientQuestCache.clear();
        com.devmod.client.endurance.ClientChallengeCache.INSTANCE.clear();
        com.devmod.client.endurance.ClientCombatFlowCache.INSTANCE.clear();
        com.devmod.endurance.ClientShopCache.clear();
        com.devmod.client.overlay.EnduranceQuestOverlay.resetStateWatcher();
        com.devmod.client.overlay.ContractHudOverlay.INSTANCE.clear();

        // Clear LVC telemetry cache
        com.devmod.client.telemetry.ClientLVCCache.INSTANCE.clear();

        // Clear Season Pass cache
        com.devmod.client.season.ClientSeasonPassCache.INSTANCE.clear();

        // Clear mailbox, news, and task caches
        com.devmod.mailbox.client.ClientMailboxCache.clear();
        com.devmod.mailbox.client.ClientNewsCache.clear();
        com.devmod.mailbox.client.ClientTaskCache.clear();
        com.devmod.mailbox.client.ClientTicketCache.clear();

        // Clear ImpactData cache (MULTIPLAYER-SAFE: clears all player entries)
        com.devmod.client.overlay.ImpactData.clearAll();
        com.devmod.client.overlay.ImpactHudController.INSTANCE.reset();

        // Reset UI telemetry open timestamps
        com.devmod.client.telemetry.UiTelemetry.reset();

        // Clear transform capture caches (OBB collision system)
        com.devmod.client.collision.transform.ModelPartTransformCapture.clearAll();
        com.devmod.client.collision.transform.ModelPartTransformExtractor.clearAllCaches();
        com.devmod.collision.transform.TransformProviderRegistry.clearAllCaches();

        // Clear environment overrides (frozen time, biome) to prevent stale state on reconnect
        com.devmod.client.environment.ClientEnvironmentCache.clearAll();

        // Clear Epic Fight client cache
        com.devmod.client.compat.mods.epicfight.ClientEpicFightCache.INSTANCE.clear();

        // Clear Nexus client cache (hub status, build progress)
        com.devmod.nexus.client.NexusClientCache.INSTANCE.clear();

        // Clear area build tracking (an IN_PROGRESS build would show phantom progress on reconnect)
        com.devmod.client.area.BuildProgressTracker.INSTANCE.clear();

        // Hide the instance loading overlay (its hide payload never arrives after a disconnect)
        com.devmod.client.overlay.InstanceLoadingOverlay.hide();

        // Drop the tracked target (strong LivingEntity reference keeps the ClientLevel alive)
        com.devmod.client.panels.context.ContextDetector.INSTANCE.clearTarget();

        // Clear debug render flags (they would keep drawing in the next world while /devdebug reports OFF)
        com.devmod.debug.client.DebugRenderBools.clearAll();

        // Drop per-entity shield impacts (entity ids are not stable across worlds)
        com.devmod.client.rendering.shield.EnergyShieldRenderer.clearEffects();

        LOGGER.debug("Caches cleared successfully");
    }

    /**
     * Cleanup per-entity caches when entities leave the client level.
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        int entityId = entity.getId();
        com.devmod.client.collision.transform.ModelPartTransformCapture.removeEntity(entityId);
        com.devmod.client.collision.transform.ModelPartTransformExtractor.clearCache(entityId);
        com.devmod.collision.transform.TransformProviderRegistry.clearCache(entityId);
    }

    public static class MobStatsLayer implements LayeredDraw.Layer {

        @Override
        public void render(@Nonnull GuiGraphics guiGraphics, @Nonnull DeltaTracker deltaTracker) {
            if (!ModConfig.isShowOverlay()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.font == null) return;
            @Nonnull net.minecraft.client.gui.Font font = Objects.requireNonNull(mc.font);

            HitResult hitResult = mc.hitResult;

            if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof LivingEntity mob) {
                    renderMobStats(guiGraphics, mob, mc, font);
                }
            }
        }

        private void renderMobStats(@Nonnull GuiGraphics gui, @Nonnull LivingEntity entity, @Nonnull Minecraft mc, @Nonnull net.minecraft.client.gui.Font font) {
            int x = mc.getWindow().getGuiScaledWidth() / 2 + 10;
            int y = mc.getWindow().getGuiScaledHeight() / 2 - 10;
            int lineHeight = 10;

            // --- DATI ---
            float hp = entity.getHealth();
            float maxHp = entity.getMaxHealth();
            int armor = entity.getArmorValue();

            // --- Reach ---
            double rawReach = 0;
            // Ora .getAttribute NON dovrebbe mai ritornare null grazie a CommonModEvents,
            // ma lasciamo il controllo per sicurezza assoluta.
            var reachAttr = entity.getAttribute(Objects.requireNonNull(Attributes.ENTITY_INTERACTION_RANGE));
            if (reachAttr != null) {
                rawReach = reachAttr.getValue();
            }

            // Text to display
            String reachText;
            int reachColor;

            if (rawReach > 0) {
                // If > 0, we modified it
                reachText = I18n.translate("devmod.debug.entity.reach.modified", df.format(rawReach)).getString();
                reachColor = OverlayTheme.Debug.ENTITY_REACH_MODIFIED;
            } else {
                // If 0, it's vanilla value. Calculate it to show user.
                double estimated = entity.getBbWidth() * 2.0 + 1.0;
                reachText = I18n.translate("devmod.debug.entity.reach.vanilla", df.format(estimated)).getString();
                reachColor = OverlayTheme.Debug.ENTITY_REACH_VANILLA;
            }

            // --- DRAWING ---
            UIScaleManager.drawScaledString(gui, font,
                I18n.translate("devmod.debug.entity.name", entity.getName().getString()).getString(), x, y,
                OverlayTheme.Debug.ENTITY_NAME);
            y += lineHeight;

            UIScaleManager.drawScaledString(gui, font,
                I18n.translate("devmod.debug.entity.hp", df.format(hp), df.format(maxHp)).getString(), x, y,
                OverlayTheme.Debug.ENTITY_HP);
            y += lineHeight;

            // Armor
            float damageReduction = armor * 4.0f;
            if (damageReduction > 80.0f) damageReduction = 80.0f;
            UIScaleManager.drawScaledString(gui, font,
                I18n.translate("devmod.debug.entity.armor", armor, (int)damageReduction).getString(), x, y,
                OverlayTheme.Debug.ENTITY_ARMOR);
            y += lineHeight;

            // Damage
            double dmg = 0;
            var dmgAttr = entity.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
            if (dmgAttr != null) dmg = dmgAttr.getValue();
            if (dmg > 0) {
                UIScaleManager.drawScaledString(gui, font,
                    I18n.translate("devmod.debug.entity.damage", df.format(dmg), df.format(dmg / 2.0)).getString(), x, y,
                    OverlayTheme.Debug.ENTITY_DAMAGE);
                y += lineHeight;
            }

            // Follow Range
            double follow = 0;
            var followAttr = entity.getAttribute(Objects.requireNonNull(Attributes.FOLLOW_RANGE));
            if (followAttr != null) follow = followAttr.getValue();
            UIScaleManager.drawScaledString(gui, font,
                I18n.translate("devmod.debug.entity.follow_range", df.format(follow)).getString(), x, y,
                OverlayTheme.Debug.ENTITY_FOLLOW_RANGE);
            y += lineHeight;

            // REACH
            UIScaleManager.drawScaledString(gui, font, reachText, x, y, reachColor);
            y += lineHeight;

            // Target
            String target = I18n.translate("devmod.debug.entity.target.none").getString();
            if (entity instanceof Mob mob) {
                var tgt = mob.getTarget();
                if (tgt != null) target = tgt.getName().getString();
            }
            UIScaleManager.drawScaledString(gui, font,
                I18n.translate("devmod.debug.entity.target", target).getString(), x, y,
                OverlayTheme.Debug.ENTITY_TARGET);
        }
    }
}
