/**
 * Unified scroll system for DevMod UI components.
 *
 * <p>This package provides a centralized scroll management system that solves
 * inconsistencies across the codebase's scrollable components.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link com.devmod.ui.scroll.ScrollManager} - Main entry point</li>
 *   <li>{@link com.devmod.ui.scroll.ScrollMode} - INSTANT or SMOOTH modes</li>
 *   <li>{@link com.devmod.ui.scroll.ScrollMetrics} - Scrollbar rendering data</li>
 *   <li>{@link com.devmod.ui.scroll.ScrollBehavior} - Interface for custom behaviors</li>
 * </ul>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Bounds checking - events only handled when mouse is over scroll area</li>
 *   <li>Delta-based drag - consistent scrollbar behavior</li>
 *   <li>Keyboard navigation - PAGE UP/DOWN, HOME/END, arrows</li>
 *   <li>Safe scissoring - try/finally pattern prevents leaks</li>
 * </ul>
 *
 * @see com.devmod.ui.scroll.ScrollManager
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.devmod.ui.scroll;
