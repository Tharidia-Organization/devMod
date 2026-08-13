/**
 * Actions V2 Binding Layer - adapters between input sources and the action engine.
 *
 * <h2>Purpose</h2>
 * Provides a uniform interface for all the ways an action can be triggered:
 * radial menu, keybind, Brigadier command, network packet, or programmatic event.
 * Each binding adapter translates its input-source-specific context into the
 * canonical {@link com.devmod.actions.ActionContext} and delegates to the
 * {@link com.devmod.actions.engine.ActionExecutor}.
 *
 * <h2>Binding Types</h2>
 * <ul>
 *   <li>{@link com.devmod.actions.bindings.RadialBinding} - Adapts radial menu item
 *       selection into action invocation</li>
 *   <li>{@link com.devmod.actions.bindings.KeybindBinding} - Adapts key press events
 *       into action invocation</li>
 *   <li>{@link com.devmod.actions.bindings.CommandBinding} - Adapts Brigadier command
 *       execution into action invocation</li>
 *   <li>{@link com.devmod.actions.bindings.NetworkBinding} - Adapts network packet
 *       receipt into action invocation (untrusted origin)</li>
 * </ul>
 *
 * <h2>Contracts</h2>
 * <ul>
 *   <li>Each binding sets the correct {@link com.devmod.actions.ActionOrigin} on the context</li>
 *   <li>Network bindings always use {@code ActionOrigin.NETWORK} (untrusted)</li>
 *   <li>Bindings do not perform authorization - that is the policy layer's job</li>
 *   <li>Bindings are registered via {@link com.devmod.actions.bindings.BindingRegistry}</li>
 * </ul>
 *
 * <h2>Boundaries</h2>
 * <ul>
 *   <li>Bindings translate input into action invocation - they do not contain game logic</li>
 *   <li>Binding metadata (keybind key, command path) comes from
 *       {@link com.devmod.actions.catalog.ActionSpec.BindingMeta}</li>
 * </ul>
 *
 * @see com.devmod.actions.engine.ActionExecutor
 * @see com.devmod.actions.catalog.ActionSpec
 */
@ParametersAreNonnullByDefault
package com.devmod.actions.bindings;

import javax.annotation.ParametersAreNonnullByDefault;
