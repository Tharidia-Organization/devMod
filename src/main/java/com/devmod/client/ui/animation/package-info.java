/**
 * Lightweight UI animation utilities for screen transitions and effects.
 *
 * <p>This package provides simple, reusable animation helpers for common UI transitions:</p>
 * <ul>
 *   <li>{@link com.devmod.client.ui.animation.UiAnimation} - Main animation controller</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create animation
 * private final UiAnimation openAnim = UiAnimation.fadeSlideUp(200);
 *
 * // Start on screen open
 * protected void init() {
 *     openAnim.start();
 * }
 *
 * // Update and apply in render
 * public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
 *     openAnim.update();
 *
 *     // Apply alpha to content
 *     int alpha = openAnim.getAlphaInt();
 *
 *     // Apply slide offset
 *     int offsetY = openAnim.getSlideOffset(20);
 *
 *     // Render with transforms
 *     renderContent(graphics, offsetY, alpha);
 * }
 *
 * // Animate close
 * public void onClose() {
 *     openAnim.reverse(() -> super.onClose());
 * }
 * }</pre>
 *
 * <h2>Available Animation Types</h2>
 * <ul>
 *   <li><b>fade()</b> - Simple alpha fade (150ms)</li>
 *   <li><b>slideUp()</b> - Slide from below (200ms)</li>
 *   <li><b>slideDown()</b> - Slide from above (200ms)</li>
 *   <li><b>scale()</b> - Pop effect with overshoot (150ms)</li>
 *   <li><b>fadeSlideUp()</b> - Combined fade + slide (200ms, most common)</li>
 *   <li><b>fadeSlideDown()</b> - Combined fade + slide down (200ms)</li>
 * </ul>
 *
 * @see com.devmod.client.ui.animation.UiAnimation
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.devmod.client.ui.animation;
