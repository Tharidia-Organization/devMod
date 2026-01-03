package com.devmod.integration;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatRegistry;
import com.devmod.compat.mods.epicfight.EpicFightCompat;
import com.devmod.util.MixinLogFilter;

public class ModIntegrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModIntegrationManager.class);

    private static boolean pehkuiLoaded = false;
    private static boolean distantHorizonsLoaded = false;
    private static boolean littleTilesLoaded = false;
    private static boolean initialized = false;

    /**
     * Initializes the manager by checking which mods are present.
     * Call during mod construction.
     */
    public static void init() {
        if (initialized) return;

        // Install log filter early to suppress client-side mixin warnings on dedicated servers
        if (!FMLEnvironment.dist.isClient()) {
            MixinLogFilter.install();
        }

        // Use new Compat utility for detection
        pehkuiLoaded = Compat.isLoaded(Compat.Mods.PEHKUI);
        distantHorizonsLoaded = Compat.isLoaded(Compat.Mods.DISTANT_HORIZONS);
        littleTilesLoaded = Compat.isLoaded(Compat.Mods.LITTLETILES);

        // Initialize legacy integrations
        if (distantHorizonsLoaded) {
            DistantHorizonsIntegration.init();
        }

        if (littleTilesLoaded) {
            LittleTilesIntegration.init();
        }

        // Initialize new CompatRegistry system
        initCompatModules();

        LOGGER.info("[DevMod] Mod Integration Status:");
        LOGGER.info("  - Pehkui: {}", pehkuiLoaded ? "ENABLED" : "not found");
        LOGGER.info("  - Distant Horizons: {}", distantHorizonsLoaded ? "ENABLED" : "not found");
        LOGGER.info("  - LittleTiles: {}", littleTilesLoaded ? "ENABLED" : "not found");
        LOGGER.info("  - CompatRegistry modules: {}", CompatRegistry.getActiveCount());

        initialized = true;
    }

    /**
     * Register and initialize CompatModule-based integrations.
     */
    private static void initCompatModules() {
        // Register all compat modules here

        // P1 - UI/Input/HUD
        CompatRegistry.register(new com.devmod.compat.mods.clothconfig.ClothConfigCompat());
        CompatRegistry.register(new com.devmod.compat.mods.journeymap.JourneyMapCompat());
        CompatRegistry.register(new com.devmod.compat.mods.emi.EmiCompat());

        // P3 - Combat/Attributes (Animation Libraries)
        CompatRegistry.register(new com.devmod.compat.mods.geckolib.GeckoLibModuleCompat());
        CompatRegistry.register(new com.devmod.compat.mods.azurelib.AzureLibCompat());

        // P3 - Combat/Attributes (Combat Overhauls)
        CompatRegistry.register(new com.devmod.compat.mods.epicfight.EpicFightCompat());

        // P3 - Combat/Attributes (Equipment)
        CompatRegistry.register(new com.devmod.compat.mods.curios.CuriosCompat());
        CompatRegistry.register(new com.devmod.compat.mods.accessories.AccessoriesCompat());

        // P3 - Combat/Attributes (Magic Systems)
        CompatRegistry.register(new com.devmod.compat.mods.ironsspellbooks.IronsSpellbooksCompat());
        CompatRegistry.register(new com.devmod.compat.mods.spellengine.SpellEngineCompat());
        CompatRegistry.register(new com.devmod.compat.mods.spellpower.SpellPowerCompat());
        CompatRegistry.register(new com.devmod.compat.mods.rangedweaponapi.RangedWeaponApiCompat());
        CompatRegistry.register(new com.devmod.compat.mods.apothicattributes.ApothicAttributesCompat());
        CompatRegistry.register(new com.devmod.compat.mods.elixirum.ElixirumCompat());

        // P3 - Combat/Attributes (Mobs)
        CompatRegistry.register(new com.devmod.compat.mods.mowziesmobs.MowziesMobsCompat());

        // P3 - Combat/Attributes (Mob AI)
        CompatRegistry.register(new com.devmod.compat.mods.smartbrainlib.SmartBrainLibCompat());

        // P3 - Combat/Attributes (Artifacts)
        CompatRegistry.register(new com.devmod.compat.mods.relics.RelicsCompat());

        // P4 - Telemetry/Performance
        CompatRegistry.register(new com.devmod.compat.mods.spark.SparkCompat());
        CompatRegistry.register(new com.devmod.compat.mods.iris.IrisCompat());

        // P3 - Combat/Attributes (Progression)
        CompatRegistry.register(new com.devmod.compat.mods.puffishskills.PuffishSkillsCompat());

        // P3 - Combat/Attributes (Class Systems)
        CompatRegistry.register(new com.devmod.compat.mods.rpgseries.RpgSeriesCompat());
        CompatRegistry.register(new com.devmod.compat.mods.shieldapi.ShieldApiCompat());

        // P5 - QoL/Testing
        CompatRegistry.register(new com.devmod.compat.mods.easynpc.EasyNpcCompat());
        CompatRegistry.register(new com.devmod.compat.mods.playeranimator.PlayerAnimatorCompat());
        CompatRegistry.register(new com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat());
        CompatRegistry.register(new com.devmod.compat.mods.emotecraft.EmotecraftCompat());

        // Client-only compat modules (safe to register only on client)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.devmod.client.compat.ClientCompatRegistrar.registerClientModules();
        }

        // P2 - Dimension/World
        CompatRegistry.register(new com.devmod.compat.mods.terrablender.TerraBlenderCompat());
        CompatRegistry.register(new com.devmod.compat.mods.c2me.C2MECompat());

        // P4 - Performance
        CompatRegistry.register(new com.devmod.compat.mods.modernfix.ModernFixCompat());
        CompatRegistry.register(new com.devmod.compat.mods.ferritecore.FerriteCoreCompat());
        CompatRegistry.register(new com.devmod.compat.mods.lithium.LithiumCompat());
        CompatRegistry.register(new com.devmod.compat.mods.sodium.SodiumCompat());
        CompatRegistry.register(new com.devmod.compat.mods.entityculling.EntityCullingCompat());

        // Initialize common (server+client) functionality
        CompatRegistry.initCommon();
    }

    /**
     * Initialize client-side compat modules.
     * Call from DevModClient during FMLClientSetupEvent.
     */
    public static void initClient() {
        CompatRegistry.initClient();
    }

    /**
     * Gets the visual scale of an entity from Pehkui.
     * @return scale (1.0 = normal), or null if Pehkui is not present
     */
    @Nullable
    public static Float getPehkuiScale(@Nullable LivingEntity entity) {
        if (!pehkuiLoaded || entity == null) return null;
        try {
            return PehkuiIntegration.getScale(entity);
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Pehkui scale for entity {}: {}", entity.getName().getString(), e.getMessage());
            return null;
        }
    }

    /**
     * Gets the hitbox scale of an entity from Pehkui.
     * Can be different from the visual scale.
     * @return hitbox scale, or null if Pehkui is not present
     */
    @Nullable
    public static Float getPehkuiHitboxScale(@Nullable LivingEntity entity) {
        if (!pehkuiLoaded || entity == null) return null;
        try {
            return PehkuiIntegration.getHitboxScale(entity);
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Pehkui hitbox scale for entity {}: {}", entity.getName().getString(), e.getMessage());
            return null;
        }
    }

    /**
     * Checks if an entity has been scaled by Pehkui.
     */
    public static boolean isScaledByPehkui(LivingEntity entity) {
        Float scale = getPehkuiScale(entity);
        return scale != null && Math.abs(scale - 1.0f) > 0.01f;
    }

    // === Getters ===

    public static boolean isPehkuiLoaded() {
        return pehkuiLoaded;
    }

    public static boolean isDistantHorizonsLoaded() {
        return distantHorizonsLoaded;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // === Epic Fight Integration Methods ===
    // Epic Fight is the core combat mod - delegates to EpicFightCompat

    /**
     * Checks if Epic Fight is loaded and available.
     */
    public static boolean isEpicFightLoaded() {
        return EpicFightCompat.isAvailable();
    }

    /**
     * Checks if Epic Fight combat is active for an entity.
     * Returns true if entity has a patch and is either in battle mode or performing an action.
     */
    public static boolean isEpicFightCombatActive(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        return EpicFightCompat.isCombatActive(entity);
    }

    /**
     * Checks if a player is in Epic Fight battle mode.
     */
    public static boolean isInEpicFightBattleMode(@Nullable Player player) {
        if (player == null) return false;
        return EpicFightCompat.isInBattleMode(player);
    }

    /**
     * Gets the current Epic Fight animation name for an entity.
     * @return animation name, or null if not available
     */
    @Nullable
    public static String getEpicFightAnimationName(@Nullable LivingEntity entity) {
        if (entity == null) return null;
        return EpicFightCompat.getCurrentAnimationName(entity);
    }

    /**
     * Gets the Epic Fight stamina for a player.
     * @return current stamina, or -1 if not available
     */
    public static float getEpicFightStamina(@Nullable Player player) {
        if (player == null) return -1f;
        return EpicFightCompat.getStamina(player);
    }

    /**
     * Gets the Epic Fight max stamina for a player.
     * @return max stamina, or -1 if not available
     */
    public static float getEpicFightMaxStamina(@Nullable Player player) {
        if (player == null) return -1f;
        return EpicFightCompat.getMaxStamina(player);
    }

    /**
     * Checks if an entity is currently performing an Epic Fight action.
     */
    public static boolean isInEpicFightAction(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        return EpicFightCompat.isInAction(entity);
    }

    /**
     * Checks if a player is currently guarding in Epic Fight.
     */
    public static boolean isEpicFightGuarding(@Nullable Player player) {
        if (player == null) return false;
        return EpicFightCompat.isGuarding(player);
    }

    /**
     * Checks if a player is currently parrying in Epic Fight.
     */
    public static boolean isEpicFightParrying(@Nullable Player player) {
        if (player == null) return false;
        return EpicFightCompat.isParrying(player);
    }

    /**
     * Checks if a player is within the Epic Fight parry window.
     */
    public static boolean isEpicFightInParryWindow(@Nullable Player player) {
        if (player == null) return false;
        return EpicFightCompat.isInParryWindow(player);
    }

    /**
     * Checks if a player executed a perfect parry in Epic Fight.
     */
    public static boolean isEpicFightPerfectParry(@Nullable Player player) {
        if (player == null) return false;
        return EpicFightCompat.isPerfectParry(player);
    }

    /**
     * Gets the Epic Fight parry sound event.
     */
    @Nullable
    public static SoundEvent getEpicFightParrySoundEvent() {
        return EpicFightCompat.getParrySoundEvent();
    }

    /**
     * Gets the Epic Fight guard impact sound event.
     */
    @Nullable
    public static SoundEvent getEpicFightGuardSoundEvent() {
        return EpicFightCompat.getGuardSoundEvent();
    }

    /**
     * Gets the Epic Fight holding skill name for a player.
     */
    @Nullable
    public static String getEpicFightHoldingSkillName(@Nullable Player player) {
        if (player == null) return null;
        return EpicFightCompat.getHoldingSkillName(player);
    }
}
