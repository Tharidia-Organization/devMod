/**
 * Public API surface for the Endurance module.
 *
 * <p>External modules should import from this package instead of reaching
 * into endurance internals. This decouples consumers from implementation
 * details and makes refactoring safer.</p>
 *
 * <h2>Provided types</h2>
 * <ul>
 *   <li>{@link com.devmod.endurance.api.StyleRank} &ndash; DMC-style ranking (D through SSS)</li>
 *   <li>{@link com.devmod.endurance.api.ActionType} &ndash; combat action classification</li>
 *   <li>{@link com.devmod.endurance.api.EnduranceConstants} &ndash; shared tag/constant strings</li>
 *   <li>{@link com.devmod.endurance.api.QuestTypes} &ndash; quest type re-export</li>
 * </ul>
 *
 * <h2>Migration guide</h2>
 * <pre>{@code
 * // Before:
 * import com.devmod.endurance.ComboSystem;
 * ComboSystem.ActionType.DASH
 *
 * // After:
 * import com.devmod.endurance.api.ActionType;
 * ActionType.DASH
 * }</pre>
 *
 * <p>Original locations remain functional for backward compatibility.</p>
 */
package com.devmod.endurance.api;
