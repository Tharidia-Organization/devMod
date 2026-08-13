/**
 * Actions V2 Domain Registrars - per-domain action registration.
 *
 * <h2>Purpose</h2>
 * Replaces the monolithic {@code DevModClientActions} (2750+ lines) and
 * {@code DevModActions} with small, focused registrar classes. Each functional
 * domain (UI, Debug, Combat, Telemetry, Arena, Endurance, etc.) implements
 * {@link com.devmod.actions.domains.DomainRegistrar} and declares its actions
 * as {@link com.devmod.actions.catalog.ActionSpec} instances.
 *
 * <h2>Domain Examples</h2>
 * <ul>
 *   <li>{@code UiDomainRegistrar} - Screen-opening actions (UI_RADIAL_OPEN, UI_SETTINGS_OPEN, ...)</li>
 *   <li>{@code DebugDomainRegistrar} - Debug overlay toggles and heatmap actions</li>
 *   <li>{@code CombatDomainRegistrar} - Impact HUD, VFX, body part detection</li>
 *   <li>{@code TelemetryDomainRegistrar} - Telemetry export, dashboard, dungeon tracking</li>
 *   <li>{@code ArenaDomainRegistrar} - Arena creation, templates, autosmoke</li>
 *   <li>{@code EnduranceDomainRegistrar} - Quest start/continue/exit, wave, perks</li>
 *   <li>{@code NexusDomainRegistrar} - Nexus teleport, admin, avatar commands</li>
 *   <li>{@code PartyDomainRegistrar} - Party, mailbox, leaderboard actions</li>
 * </ul>
 *
 * <h2>Contracts</h2>
 * <ul>
 *   <li>Each registrar is self-contained: its action specs and handlers are co-located</li>
 *   <li>Registrars declare dependencies via {@code getDependencies()} for ordered initialization</li>
 *   <li>No registrar may register actions that conflict with another domain's ID namespace</li>
 *   <li>{@link com.devmod.actions.domains.DomainRegistry} validates uniqueness and dependency order</li>
 * </ul>
 *
 * <h2>Boundaries</h2>
 * <ul>
 *   <li>Registrars define WHAT actions exist, not HOW they are executed (engine) or
 *       WHO can invoke them (policy)</li>
 *   <li>Registrars may reference domain-specific services (e.g., TelemetryService) for handlers</li>
 * </ul>
 *
 * @see com.devmod.actions.catalog.ActionSpec
 * @see com.devmod.actions.engine.ActionExecutor
 */
@ParametersAreNonnullByDefault
package com.devmod.actions.domains;

import javax.annotation.ParametersAreNonnullByDefault;
