package com.devmod.compat.mods.epicfight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

/**
 * Compatibility module for Epic Fight mod.
 *
 * Epic Fight is a soulslike combat mod that completely overhauls Minecraft's
 * combat system with animations, combos, skills, stamina, and more.
 *
 * This integration provides:
 * - Combat mode detection (battle mode vs normal)
 * - Entity patch access for animation/combat state
 * - Combat attribute extraction (armor negation, impact, etc.)
 * - Animation state tracking
 * - Stamina system integration
 *
 * Package structure (from decompilation/docs):
 * - yesman.epicfight.world.capabilities.EpicFightCapabilities
 * - yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch
 * - yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch
 * - yesman.epicfight.api.animation.types.AttackAnimation
 * - yesman.epicfight.gameasset.Animations
 *
 * @see <a href="https://github.com/Epic-Fight/epicfight">Epic Fight GitHub</a>
 * @see <a href="https://epicfight-docs.readthedocs.io/">Epic Fight Wiki</a>
 */
public class EpicFightCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(EpicFightCompat.class);
    public static final String MOD_ID = "epicfight";

    private static volatile boolean available = false;
    private static volatile boolean initialized = false;
    private static volatile boolean apiAvailable = false;

    // Cached reflection references - Classes
    @Nullable private static Class<?> epicFightCapabilitiesClass;
    @Nullable private static Class<?> livingEntityPatchClass;
    @Nullable private static Class<?> playerPatchClass;
    @Nullable private static Class<?> animatorClass;

    // Methods from EpicFightCapabilities
    @Nullable private static Method getEntityPatchMethod;
    @Nullable private static Method getPlayerPatchMethod;

    // LivingEntityPatch methods
    @Nullable private static Method isInactionMethod;
    @Nullable private static Method getAnimatorMethod;

    // PlayerPatch methods
    @Nullable private static Method isBattleModeMethod;
    @Nullable private static Method getStaminaMethod;
    @Nullable private static Method getMaxStaminaMethod;

    // Animator methods
    @Nullable private static Method getCurrentAnimationMethod;

    // Guard/Parry detection methods (PlayerPatch)
    @Nullable private static Method isHoldingSkillMethod;
    @Nullable private static Method getHoldingSkillMethod;
    @Nullable private static Method getTickSinceLastActionMethod;

    // Skill class references
    @Nullable private static Class<?> skillClass;
    @Nullable private static Class<?> guardSkillClass;
    @Nullable private static Class<?> parryingSkillClass;

    // Sound events (loaded via reflection)
    @Nullable private static SoundEvent parrySuccessSound;
    @Nullable private static SoundEvent guardImpactSound;

    // Default parry window (from Epic Fight source)
    private static final int DEFAULT_PARRY_WINDOW = 8;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Epic Fight";
    }

    @Override
    public int priority() {
        // Very high priority - completely changes combat system
        return 15;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:epicfight] Epic Fight not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:epicfight] Epic Fight detected");
        LOGGER.debug("[Compat:epicfight] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load Epic Fight API classes via reflection.
     */
    private void loadApi() {
        try {
            // Core capability class
            epicFightCapabilitiesClass = Class.forName(
                "yesman.epicfight.world.capabilities.EpicFightCapabilities");
            LOGGER.debug("[Compat:epicfight] Found EpicFightCapabilities");

            // Entity patch classes
            livingEntityPatchClass = Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch");
            LOGGER.debug("[Compat:epicfight] Found LivingEntityPatch");

            playerPatchClass = Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
            LOGGER.debug("[Compat:epicfight] Found PlayerPatch");

            // Animator class
            animatorClass = Class.forName(
                "yesman.epicfight.api.animation.Animator");
            LOGGER.debug("[Compat:epicfight] Found Animator");

            // Load methods from EpicFightCapabilities
            loadCapabilityMethods();

            // Load methods from entity patches
            loadEntityPatchMethods();

            // Load animator methods
            loadAnimatorMethods();

            // Load skill classes for guard/parry detection
            loadSkillClasses();

            // Load sound events
            loadSoundEvents();

            apiAvailable = true;
            LOGGER.info("[Compat:epicfight] Epic Fight API loaded successfully");

        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Compat:epicfight] Epic Fight classes not found: {}", e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("invalid dist") || msg.contains("DEDICATED_SERVER")) {
                LOGGER.debug("[Compat:epicfight] Skipped client-only API on dedicated server");
            } else {
                LOGGER.warn("[Compat:epicfight] Error loading API: {}", msg);
            }
        }
    }

    private void loadCapabilityMethods() throws NoSuchMethodException {
        final Class<?> capClass = epicFightCapabilitiesClass;
        if (capClass == null) return;

        // getEntityPatch(Entity, Class) -> EntityPatch
        getEntityPatchMethod = capClass.getMethod(
            "getEntityPatch", net.minecraft.world.entity.Entity.class, Class.class);
        LOGGER.debug("[Compat:epicfight] Found getEntityPatch method");

        // getPlayerPatch(Player) -> PlayerPatch
        try {
            getPlayerPatchMethod = capClass.getMethod("getPlayerPatch", Player.class);
            LOGGER.debug("[Compat:epicfight] Found getPlayerPatch method");
        } catch (NoSuchMethodException e) {
            LOGGER.trace("[Compat:epicfight] getPlayerPatch not found, using generic method");
        }
    }

    private void loadEntityPatchMethods() {
        final Class<?> lepClass = livingEntityPatchClass;
        if (lepClass == null) return;

        try {
            // isInaction() -> boolean (entity is in special action)
            isInactionMethod = lepClass.getMethod("isInaction");
            LOGGER.debug("[Compat:epicfight] Found isInaction method");
        } catch (NoSuchMethodException e) {
            LOGGER.trace("[Compat:epicfight] isInaction not found");
        }

        try {
            // getAnimator() -> Animator
            getAnimatorMethod = lepClass.getMethod("getAnimator");
            LOGGER.debug("[Compat:epicfight] Found getAnimator method");
        } catch (NoSuchMethodException e) {
            LOGGER.trace("[Compat:epicfight] getAnimator not found");
        }

        // PlayerPatch specific methods
        final Class<?> ppClass = playerPatchClass;
        if (ppClass != null) {
            try {
                isBattleModeMethod = ppClass.getMethod("isBattleMode");
                LOGGER.debug("[Compat:epicfight] Found isBattleMode method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] isBattleMode not found");
            }

            try {
                getStaminaMethod = ppClass.getMethod("getStamina");
                LOGGER.debug("[Compat:epicfight] Found getStamina method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] getStamina not found");
            }

            try {
                getMaxStaminaMethod = ppClass.getMethod("getMaxStamina");
                LOGGER.debug("[Compat:epicfight] Found getMaxStamina method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] getMaxStamina not found");
            }
        }
    }

    private void loadAnimatorMethods() {
        final Class<?> animClass = animatorClass;
        if (animClass == null) return;

        try {
            getCurrentAnimationMethod = animClass.getMethod("getPlayerCurrentAnimation");
            LOGGER.debug("[Compat:epicfight] Found getCurrentAnimation method");
        } catch (NoSuchMethodException e) {
            // Try alternative name
            try {
                getCurrentAnimationMethod = animClass.getMethod("getCurrentAnimation");
                LOGGER.debug("[Compat:epicfight] Found getCurrentAnimation (alt) method");
            } catch (NoSuchMethodException e2) {
                LOGGER.trace("[Compat:epicfight] getCurrentAnimation not found");
            }
        }
    }

    private void loadSkillClasses() {
        // Load base Skill class
        try {
            skillClass = Class.forName("yesman.epicfight.skill.Skill");
            LOGGER.debug("[Compat:epicfight] Found Skill class");
        } catch (ClassNotFoundException e) {
            LOGGER.trace("[Compat:epicfight] Skill class not found");
        }

        // Load GuardSkill class
        try {
            guardSkillClass = Class.forName("yesman.epicfight.skill.guard.GuardSkill");
            LOGGER.debug("[Compat:epicfight] Found GuardSkill class");
        } catch (ClassNotFoundException e) {
            LOGGER.trace("[Compat:epicfight] GuardSkill class not found");
        }

        // Load ParryingSkill class
        try {
            parryingSkillClass = Class.forName("yesman.epicfight.skill.guard.ParryingSkill");
            LOGGER.debug("[Compat:epicfight] Found ParryingSkill class");
        } catch (ClassNotFoundException e) {
            LOGGER.trace("[Compat:epicfight] ParryingSkill class not found");
        }

        // Load guard/parry methods from PlayerPatch
        final Class<?> ppClass = playerPatchClass;
        if (ppClass != null) {
            try {
                isHoldingSkillMethod = ppClass.getMethod("isHoldingSkill");
                LOGGER.debug("[Compat:epicfight] Found isHoldingSkill method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] isHoldingSkill not found");
            }

            try {
                getHoldingSkillMethod = ppClass.getMethod("getHoldingSkill");
                LOGGER.debug("[Compat:epicfight] Found getHoldingSkill method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] getHoldingSkill not found");
            }

            try {
                getTickSinceLastActionMethod = ppClass.getMethod("getTickSinceLastAction");
                LOGGER.debug("[Compat:epicfight] Found getTickSinceLastAction method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] getTickSinceLastAction not found");
            }
        }
    }

    private void loadSoundEvents() {
        try {
            Class<?> soundsClass = Class.forName("yesman.epicfight.gameasset.EpicFightSounds");
            LOGGER.debug("[Compat:epicfight] Found EpicFightSounds class");

            // Try to get parry sound (CLASH)
            try {
                Field parryField = soundsClass.getDeclaredField("CLASH");
                parryField.setAccessible(true);
                Object holder = parryField.get(null);
                if (holder instanceof net.minecraft.core.Holder<?> soundHolder) {
                    parrySuccessSound = (SoundEvent) soundHolder.value();
                    LOGGER.debug("[Compat:epicfight] Found CLASH sound");
                } else if (holder instanceof SoundEvent se) {
                    parrySuccessSound = se;
                    LOGGER.debug("[Compat:epicfight] Found CLASH sound (direct)");
                }
            } catch (NoSuchFieldException e) {
                LOGGER.trace("[Compat:epicfight] CLASH sound not found");
            }

            // Try to get guard impact sound
            try {
                Field guardField = soundsClass.getDeclaredField("WEAPON_HIT_BLOCK");
                guardField.setAccessible(true);
                Object holder = guardField.get(null);
                if (holder instanceof net.minecraft.core.Holder<?> soundHolder) {
                    guardImpactSound = (SoundEvent) soundHolder.value();
                    LOGGER.debug("[Compat:epicfight] Found WEAPON_HIT_BLOCK sound");
                } else if (holder instanceof SoundEvent se) {
                    guardImpactSound = se;
                    LOGGER.debug("[Compat:epicfight] Found WEAPON_HIT_BLOCK sound (direct)");
                }
            } catch (NoSuchFieldException e) {
                LOGGER.trace("[Compat:epicfight] WEAPON_HIT_BLOCK sound not found");
            }

        } catch (ClassNotFoundException e) {
            LOGGER.trace("[Compat:epicfight] EpicFightSounds class not found");
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Error loading sounds: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:epicfight] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Combat mode detection, entity patches, animation tracking, stamina integration, guard/parry detection, sound events";
    }

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Check if Epic Fight is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Check if an entity has an Epic Fight patch.
     *
     * @param entity The entity to check
     * @return true if entity is patched by Epic Fight
     */
    public static boolean hasEntityPatch(@Nullable LivingEntity entity) {
        if (entity == null || !apiAvailable) return false;

        try {
            Object patch = getEntityPatch(entity);
            return patch != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the Epic Fight entity patch for an entity.
     *
     * @param entity The entity
     * @return The entity patch object, or null
     */
    @Nullable
    public static Object getEntityPatch(@Nullable LivingEntity entity) {
        final Method method = getEntityPatchMethod;
        final Class<?> lepClass = livingEntityPatchClass;
        if (entity == null || !apiAvailable || method == null || lepClass == null) {
            return null;
        }

        try {
            return method.invoke(null, entity, lepClass);
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get entity patch: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the PlayerPatch for a player.
     *
     * @param player The player
     * @return The player patch object, or null
     */
    @Nullable
    public static Object getPlayerPatch(@Nullable Player player) {
        if (player == null || !apiAvailable) return null;

        try {
            final Method ppMethod = getPlayerPatchMethod;
            if (ppMethod != null) {
                return ppMethod.invoke(null, player);
            }
            final Method epMethod = getEntityPatchMethod;
            final Class<?> ppClass = playerPatchClass;
            if (epMethod != null && ppClass != null) {
                return epMethod.invoke(null, player, ppClass);
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get player patch: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if a player is in Epic Fight battle mode.
     *
     * @param player The player
     * @return true if in battle mode
     */
    public static boolean isInBattleMode(@Nullable Player player) {
        final Method method = isBattleModeMethod;
        if (player == null || !apiAvailable || method == null) return false;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object result = method.invoke(playerPatch);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to check battle mode: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Check if an entity is currently performing a special action (attack, skill, etc.).
     *
     * @param entity The entity
     * @return true if in action
     */
    public static boolean isInAction(@Nullable LivingEntity entity) {
        final Method method = isInactionMethod;
        if (entity == null || !apiAvailable || method == null) return false;

        try {
            Object patch = getEntityPatch(entity);
            if (patch != null) {
                Object result = method.invoke(patch);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to check inaction: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get the current stamina of a player.
     *
     * @param player The player
     * @return Current stamina, or -1 if unavailable
     */
    public static float getStamina(@Nullable Player player) {
        final Method method = getStaminaMethod;
        if (player == null || !apiAvailable || method == null) return -1f;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object result = method.invoke(playerPatch);
                if (result instanceof Number) {
                    return ((Number) result).floatValue();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get stamina: {}", e.getMessage());
        }
        return -1f;
    }

    /**
     * Get the max stamina of a player.
     *
     * @param player The player
     * @return Max stamina, or -1 if unavailable
     */
    public static float getMaxStamina(@Nullable Player player) {
        final Method method = getMaxStaminaMethod;
        if (player == null || !apiAvailable || method == null) return -1f;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object result = method.invoke(playerPatch);
                if (result instanceof Number) {
                    return ((Number) result).floatValue();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get max stamina: {}", e.getMessage());
        }
        return -1f;
    }

    /**
     * Get the current animation name for an entity.
     *
     * @param entity The entity
     * @return Animation name, or null
     */
    @Nullable
    public static String getCurrentAnimationName(@Nullable LivingEntity entity) {
        final Method animatorMethod = getAnimatorMethod;
        final Method animMethod = getCurrentAnimationMethod;
        if (entity == null || !apiAvailable || animatorMethod == null) return null;

        try {
            Object patch = getEntityPatch(entity);
            if (patch != null) {
                Object animator = animatorMethod.invoke(patch);
                if (animator != null && animMethod != null) {
                    Object animation = animMethod.invoke(animator);
                    if (animation != null) {
                        // Try to get animation name
                        try {
                            Method getNameMethod = animation.getClass().getMethod("getRegistryName");
                            Object name = getNameMethod.invoke(animation);
                            return name != null ? name.toString() : null;
                        } catch (NoSuchMethodException e) {
                            return animation.getClass().getSimpleName();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get animation name: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if Epic Fight combat is active for an entity.
     * Returns true if entity has a patch and is either in battle mode or performing an action.
     *
     * @param entity The entity
     * @return true if Epic Fight combat is active
     */
    public static boolean isCombatActive(@Nullable LivingEntity entity) {
        if (entity == null || !hasEntityPatch(entity)) return false;

        if (entity instanceof Player player) {
            return isInBattleMode(player) || isInAction(entity);
        }
        return isInAction(entity);
    }

    // ============================================================
    // Guard/Parry API
    // ============================================================

    /**
     * Check if a player is currently holding a skill (guard, parry, etc.).
     */
    public static boolean isHoldingSkill(@Nullable Player player) {
        final Method method = isHoldingSkillMethod;
        if (player == null || !apiAvailable || method == null) return false;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object result = method.invoke(playerPatch);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to check isHoldingSkill: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get the skill object the player is currently holding.
     */
    @Nullable
    public static Object getHoldingSkill(@Nullable Player player) {
        final Method method = getHoldingSkillMethod;
        if (player == null || !apiAvailable || method == null) return null;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object skill = method.invoke(playerPatch);
                if (skill == null) {
                    return null;
                }
                Class<?> baseSkillClass = skillClass;
                if (baseSkillClass != null && !baseSkillClass.isInstance(skill)) {
                    return null;
                }
                return skill;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get holding skill: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get the name of the skill the player is currently holding.
     */
    @Nullable
    public static String getHoldingSkillName(@Nullable Player player) {
        Object skill = getHoldingSkill(player);
        if (skill == null) return null;

        try {
            Method getNameMethod = skill.getClass().getMethod("getRegistryName");
            Object name = getNameMethod.invoke(skill);
            return name != null ? name.toString() : skill.getClass().getSimpleName();
        } catch (NoSuchMethodException e) {
            try {
                Method getNameMethod = skill.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(skill);
                return name != null ? name.toString() : skill.getClass().getSimpleName();
            } catch (Exception e2) {
                return skill.getClass().getSimpleName();
            }
        } catch (Exception e) {
            return skill.getClass().getSimpleName();
        }
    }

    /**
     * Check if a player is currently guarding.
     */
    public static boolean isGuarding(@Nullable Player player) {
        if (player == null || !isHoldingSkill(player)) return false;

        Object skill = getHoldingSkill(player);
        if (skill == null) return false;

        final Class<?> guardClass = guardSkillClass;
        if (guardClass != null && guardClass.isInstance(skill)) {
            return true;
        }

        String skillName = getHoldingSkillName(player);
        return skillName != null && (
            skillName.toLowerCase(java.util.Locale.ROOT).contains("guard") ||
            skillName.toLowerCase(java.util.Locale.ROOT).contains("block") ||
            skillName.toLowerCase(java.util.Locale.ROOT).contains("shield")
        );
    }

    /**
     * Check if a player is currently using a parry skill.
     */
    public static boolean isParrying(@Nullable Player player) {
        if (player == null || !isHoldingSkill(player)) return false;

        Object skill = getHoldingSkill(player);
        if (skill == null) return false;

        final Class<?> parryClass = parryingSkillClass;
        if (parryClass != null && parryClass.isInstance(skill)) {
            return true;
        }

        String skillName = getHoldingSkillName(player);
        return skillName != null && skillName.toLowerCase(java.util.Locale.ROOT).contains("parry");
    }

    /**
     * Get the number of ticks since the player's last action.
     */
    public static int getTicksSinceLastAction(@Nullable Player player) {
        final Method method = getTickSinceLastActionMethod;
        if (player == null || !apiAvailable || method == null) return -1;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object result = method.invoke(playerPatch);
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get ticks since last action: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Check if a player is within the parry window (8 ticks by default).
     */
    public static boolean isInParryWindow(@Nullable Player player) {
        int ticksSince = getTicksSinceLastAction(player);
        int window = getParryWindow();
        return ticksSince >= 0 && ticksSince <= window;
    }

    /**
     * Check if a player executed a perfect parry (within first 3 ticks).
     */
    public static boolean isPerfectParry(@Nullable Player player) {
        if (player == null || (!isGuarding(player) && !isParrying(player))) return false;

        int ticksSince = getTicksSinceLastAction(player);
        return ticksSince >= 0 && ticksSince <= 3;
    }

    /**
     * Get the parry window duration in ticks.
     */
    public static int getParryWindow() {
        return DEFAULT_PARRY_WINDOW;
    }

    // ============================================================
    // Sound API
    // ============================================================

    /**
     * Get the Epic Fight parry/clash sound event.
     */
    @Nullable
    public static SoundEvent getParrySoundEvent() {
        return parrySuccessSound;
    }

    /**
     * Get the Epic Fight guard impact sound event.
     */
    @Nullable
    public static SoundEvent getGuardSoundEvent() {
        return guardImpactSound;
    }

}
