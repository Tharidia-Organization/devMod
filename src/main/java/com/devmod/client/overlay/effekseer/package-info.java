/**
 * Effekseer-based impact VFX system.
 *
 * <p>This package provides GPU-accelerated particle effects for combat feedback
 * using the Effekseer library. The system is designed for rich, context-aware
 * visual feedback that scales with damage, combo count, and hit type.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.devmod.client.overlay.effekseer.EffectContext} - Immutable context
 *       carrying all impact information for effect decisions</li>
 *   <li>{@link com.devmod.client.overlay.effekseer.EffectOrchestrator} - Handles effect
 *       selection, layering, and lifecycle management</li>
 *   <li>{@link com.devmod.client.overlay.effekseer.ComboTracker} - Tracks consecutive
 *       hits for combo-based effect scaling</li>
 *   <li>{@link com.devmod.client.overlay.effekseer.EffectPreset} - Configurable
 *       intensity presets from Minimal to Epic</li>
 * </ul>
 *
 * <h2>Effect Files</h2>
 * Effects are loaded from: {@code assets/devmod/effeks/impact/}
 * <ul>
 *   <li>{@code hit.efkefc} - Generic hit feedback</li>
 *   <li>{@code critical.efkefc} - Critical zone hits</li>
 *   <li>{@code headshot.efkefc} - Headshot particles</li>
 *   <li>{@code kill.efkefc} - Kill effects</li>
 *   <li>{@code combo.efkefc} - Chain hit effects</li>
 *   <li>{@code backstab.efkefc} - Sneak attack effects</li>
 *   <li>{@code block.efkefc} - Blocked damage effects</li>
 *   <li>{@code finisher.efkefc} - Epic combo finisher kills</li>
 *   <li>{@code milestone.efkefc} - Combo milestone celebrations</li>
 * </ul>
 *
 * @see com.devmod.client.overlay.ImpactEffekseerVFX
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.devmod.client.overlay.effekseer;
