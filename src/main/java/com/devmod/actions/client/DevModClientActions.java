package com.devmod.actions.client;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facade entry point for all client-side action registrations.
 * Delegates to domain-specific registrar classes:
 * <ul>
 *   <li>{@link ClientUIActions} - screens, editors, hubs, testing, QA, party, notifications</li>
 *   <li>{@link ClientDebugActions} - overlays, heatmaps, visualizers, HUD toggles, combat diagnostics</li>
 *   <li>{@link ClientConfigActions} - config toggles, HUD position/offset, VFX, game design config, telemetry exports</li>
 *   <li>{@link ClientGameplayActions} - abilities (dash/dodge), quest tasks, endurance quest lifecycle</li>
 * </ul>
 */
public final class DevModClientActions {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevModClientActions.class);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private DevModClientActions() {}

    /**
     * Registers all client actions. Safe to call multiple times (idempotent).
     */
    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        LOGGER.debug("[DevMod] Registering client actions via domain registrars");

        // UI: screens, editors, hubs, testing, QA, party, notifications, onboarding
        ClientUIActions.registerActions();

        // Debug: overlays, heatmaps, visualizers, HUD toggles, combat diagnostics
        ClientDebugActions.registerActions();

        // Config: config toggles, HUD position/offset, VFX, game design config
        ClientConfigActions.registerConfigActions();

        // Telemetry: heatmap exports, damage stats
        ClientConfigActions.registerTelemetryActions();

        // Quest: task completion
        ClientGameplayActions.registerQuestActions();

        // Endurance: quest start/continue/exit, settings
        ClientGameplayActions.registerEnduranceActions();

        // Abilities: dash, dodge
        ClientGameplayActions.registerAbilityActions();

        // Keybind hints (all domains)
        ClientUIActions.registerKeybindHints();
        ClientDebugActions.registerKeybindHints();
        ClientGameplayActions.registerKeybindHints();
    }

    /**
     * Registers only the core-only subset of actions (used for minimal/core mode).
     */
    public static void registerCoreOnly() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        LOGGER.debug("[DevMod] Registering core-only client actions");

        ClientUIActions.registerCoreOnlyActions();
        ClientGameplayActions.registerAbilityActions();
        ClientGameplayActions.registerEnduranceActions();

        ClientUIActions.registerCoreKeybindHints();
        ClientGameplayActions.registerCoreKeybindHints();
    }
}
