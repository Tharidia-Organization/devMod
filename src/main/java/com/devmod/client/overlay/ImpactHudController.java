package com.devmod.client.overlay;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;

import net.neoforged.fml.loading.FMLEnvironment;

import com.devmod.client.panels.context.ContextDetector;
import com.devmod.client.panels.context.ContextMode;
import com.devmod.config.Config;

/**
 * Central controller for the Impact HUD system.
 * Coordinates display modes based on context (COMBAT/EXPLORE/TEST) and user preferences.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Listens to ContextDetector for automatic mode switching</li>
 *   <li>Routes incoming impacts to appropriate subsystems</li>
 *   <li>Manages DisplayMode (OFF/MINIMAL/DETAILED/ANALYSIS)</li>
 *   <li>Provides Epic Fight integration (reduces overlap)</li>
 * </ul>
 */
public final class ImpactHudController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImpactHudController.class);

    public static final ImpactHudController INSTANCE = new ImpactHudController();

    // === State ===
    private ImpactDisplayMode displayMode = ImpactDisplayMode.DETAILED;
    private ImpactDisplayMode userPreferredCombatMode = ImpactDisplayMode.DETAILED;
    private boolean user3dPanelsEnabled = true;
    private boolean initialized = false;

    // === Listener for external mode change notifications ===
    @Nullable
    private Consumer<ImpactDisplayMode> modeChangeListener = null;

    private ImpactHudController() {}

    /**
     * Initializes the controller and registers with ContextDetector.
     * Should be called once during client setup.
     */
    public void initialize() {
        if (initialized) return;
        if (!FMLEnvironment.dist.isClient()) return;

        // Load user preference from config
        loadUserPreference();
        apply3dPanelState();

        // Register context change listener
        ContextDetector.INSTANCE.addModeChangeListener(this::onContextChange);

        initialized = true;
        LOGGER.info("ImpactHudController initialized, default mode: {}", displayMode);
    }

    /**
     * Loads user preference from config.
     */
    private void loadUserPreference() {
        try {
            String savedMode = Config.IMPACT_DISPLAY_MODE_DEFAULT.get();
            userPreferredCombatMode = ImpactDisplayMode.fromName(savedMode);
            displayMode = userPreferredCombatMode;
        } catch (Exception e) {
            LOGGER.warn("Failed to load impact display mode from config, using DETAILED", e);
            userPreferredCombatMode = ImpactDisplayMode.DETAILED;
            displayMode = ImpactDisplayMode.DETAILED;
        }
    }

    /**
     * Saves user preference to config.
     */
    private void saveUserPreference() {
        try {
            String modeName = Objects.requireNonNull(
                userPreferredCombatMode.name(),
                "ImpactDisplayMode name cannot be null");
            Config.IMPACT_DISPLAY_MODE_DEFAULT.set(modeName);
        } catch (Exception e) {
            LOGGER.warn("Failed to save impact display mode to config", e);
        }
    }

    /**
     * Called when ContextDetector changes mode.
     */
    private void onContextChange(ContextMode contextMode) {
        ImpactDisplayMode newMode = switch (contextMode) {
            case EXPLORE -> ImpactDisplayMode.OFF;
            case COMBAT -> userPreferredCombatMode;
            case TEST -> ImpactDisplayMode.ANALYSIS;
            case CUSTOM -> displayMode;
        };

        if (newMode != displayMode) {
            setDisplayMode(newMode);
            LOGGER.debug("Context changed to {}, display mode now: {}", contextMode, newMode);
        }
    }

    /**
     * Main entry point for new impacts.
     * Decides what to display based on current mode and impact data.
     *
     * @param impactData The impact data to process
     */
    public void onImpact(ImpactData impactData) {
        if (impactData == null) return;

        ImpactDisplayMode effectiveMode = displayMode;

        // Boss detection: always show detailed for bosses
        if (isBossTarget(impactData)) {
            if (effectiveMode == ImpactDisplayMode.MINIMAL) {
                effectiveMode = ImpactDisplayMode.DETAILED;
            }
        }

        // Epic Fight integration: reduce overlap when Epic Fight is active
        if (impactData.isEpicFightCombat() && effectiveMode == ImpactDisplayMode.DETAILED) {
            // Epic Fight provides its own feedback, use minimal to complement
            effectiveMode = ImpactDisplayMode.MINIMAL;
        }

        // Route to subsystems based on effective mode
        routeImpact(impactData, effectiveMode);
    }

    /**
     * Routes the impact to appropriate subsystems based on display mode.
     */
    private void routeImpact(ImpactData impactData, ImpactDisplayMode mode) {
        if (mode == ImpactDisplayMode.OFF) {
            return; // Nothing to show
        }

        // Flash/shake feedback (all modes except OFF)
        if (mode.shouldShowFlash()) {
            // Flash and shake are handled in ImpactHudService.triggerDamageShakeIfApplicable
            // and ClientVFXProxy - they check Config.IMPACT_VFX_ENABLED internally
        }

        // 3D panels (DETAILED and ANALYSIS only)
        if (mode.shouldShow3DPanels()) {
            Impact3DPanelManager.INSTANCE.spawnPanelFromImpact(impactData);
        }

        // DPS tracking (always, for recap on demand)
        if (impactData.hasActualDamage()) {
            ImpactDpsTracker.recordDamage(impactData.attackerUUID, impactData.getActualDamageDealt());
        } else {
            // Use calculated damage as fallback
            ImpactDpsTracker.recordDamage(impactData.attackerUUID, impactData.breakdown.finalDamage);
        }

        // Combat session tracking (for recap feature)
        CombatSessionTracker.INSTANCE.recordHit(impactData);

        LOGGER.debug("Impact routed: mode={}, target={}, damage={}",
            mode, impactData.targetName, impactData.breakdown.finalDamage);
    }

    /**
     * Checks if the target is a boss entity.
     * Uses NBT tag 'endurance_is_boss' from the Endurance quest system.
     */
    private boolean isBossTarget(ImpactData impactData) {
        LivingEntity target = impactData.getTarget();
        if (target == null) return false;

        // Check NBT tag
        var data = target.getPersistentData();
        return data.getBoolean("endurance_is_boss");
    }

    // === Public API ===

    /**
     * Gets the current display mode.
     */
    public ImpactDisplayMode getDisplayMode() {
        return displayMode;
    }

    /**
     * Sets the display mode directly.
     * Note: This bypasses context-based auto-switching temporarily.
     */
    public void setDisplayMode(ImpactDisplayMode mode) {
        if (mode == null) mode = ImpactDisplayMode.DETAILED;

        ImpactDisplayMode oldMode = this.displayMode;
        this.displayMode = mode;

        // Update subsystem states
        apply3dPanelState();

        // Notify listener
        if (modeChangeListener != null && oldMode != mode) {
            modeChangeListener.accept(mode);
        }

        LOGGER.debug("Display mode changed: {} -> {}", oldMode, mode);
    }

    /**
     * Cycles to the next user-selectable display mode.
     * OFF -> MINIMAL -> DETAILED -> OFF
     */
    public void cycleDisplayMode() {
        ImpactDisplayMode nextMode = displayMode.cycleNext();
        setDisplayMode(nextMode);

        // If we're in COMBAT context, save this as user preference
        if (ContextDetector.INSTANCE.getCurrentMode() == ContextMode.COMBAT) {
            userPreferredCombatMode = nextMode;
            saveUserPreference();
        }
    }

    /**
     * Sets the user's preferred combat mode and saves to config.
     */
    public void setUserPreferredCombatMode(ImpactDisplayMode mode) {
        if (mode == ImpactDisplayMode.ANALYSIS) {
            // ANALYSIS is only for TEST context
            mode = ImpactDisplayMode.DETAILED;
        }
        userPreferredCombatMode = mode;
        saveUserPreference();

        // Apply immediately if in COMBAT context
        if (ContextDetector.INSTANCE.getCurrentMode() == ContextMode.COMBAT) {
            setDisplayMode(mode);
        }
    }

    /**
     * Gets the user's preferred combat mode.
     */
    public ImpactDisplayMode getUserPreferredCombatMode() {
        return userPreferredCombatMode;
    }

    /**
     * Applies a preset configuration.
     *
     * @param preset One of "minimal", "detailed", "training"
     */
    public void applyPreset(String preset) {
        ImpactDisplayMode mode = switch (preset.toLowerCase(java.util.Locale.ROOT)) {
            case "minimal" -> ImpactDisplayMode.MINIMAL;
            case "detailed" -> ImpactDisplayMode.DETAILED;
            case "training", "analysis" -> ImpactDisplayMode.ANALYSIS;
            default -> ImpactDisplayMode.DETAILED;
        };

        setUserPreferredCombatMode(mode);
        setDisplayMode(mode);
        LOGGER.info("Applied preset: {} -> {}", preset, mode);
    }

    /**
     * Checks if the system is effectively enabled (not OFF).
     */
    public boolean isEnabled() {
        return displayMode != ImpactDisplayMode.OFF;
    }

    /**
     * Toggles between OFF and the user's preferred mode.
     */
    public void toggle() {
        if (displayMode == ImpactDisplayMode.OFF) {
            setDisplayMode(userPreferredCombatMode);
        } else {
            setDisplayMode(ImpactDisplayMode.OFF);
        }
    }

    /**
     * Sets a listener for mode changes (for UI updates).
     */
    public void setModeChangeListener(@Nullable Consumer<ImpactDisplayMode> listener) {
        this.modeChangeListener = listener;
    }

    /**
     * Gets debug info for the status overlay.
     */
    public String getDebugInfo() {
        return String.format("Impact: %s (pref: %s, ctx: %s)",
            displayMode.getDisplayName(),
            userPreferredCombatMode.getDisplayName(),
            ContextDetector.INSTANCE.getCurrentMode().getDisplayName());
    }

    /**
     * Resets to default state.
     */
    public void reset() {
        displayMode = ImpactDisplayMode.DETAILED;
        userPreferredCombatMode = ImpactDisplayMode.DETAILED;
        user3dPanelsEnabled = true;
        apply3dPanelState();
        Impact3DPanelManager.INSTANCE.clear();
    }

    /**
     * Returns whether 3D panels are allowed by the user preference.
     */
    public boolean is3dPanelsEnabled() {
        return user3dPanelsEnabled;
    }

    /**
     * Enables/disables 3D panels while respecting the current display mode.
     */
    public void set3dPanelsEnabled(boolean enabled) {
        user3dPanelsEnabled = enabled;
        apply3dPanelState();
    }

    /**
     * Toggle 3D panels on/off (user preference).
     */
    public void toggle3dPanels() {
        set3dPanelsEnabled(!user3dPanelsEnabled);
    }

    private void apply3dPanelState() {
        Impact3DPanelManager.INSTANCE.setEnabled(displayMode.shouldShow3DPanels() && user3dPanelsEnabled);
    }
}
