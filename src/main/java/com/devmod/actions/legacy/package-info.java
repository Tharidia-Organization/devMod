/**
 * Actions V2 Legacy Compatibility Layer - bridge between V1 and V2 action systems.
 *
 * <h2>Purpose</h2>
 * Provides a compatibility bridge so that the V1 action system ({@link com.devmod.actions.ActionRegistry},
 * {@link com.devmod.actions.RadialAction}) continues to work during the migration to V2
 * ({@link com.devmod.actions.catalog.ActionSpec}, {@link com.devmod.actions.engine.ActionExecutor}).
 * This enables incremental migration: domains can be moved one at a time without breaking
 * existing functionality.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link com.devmod.actions.legacy.LegacyActionAdapter} - Converts a V1
 *       {@link com.devmod.actions.RadialAction} into a V2 {@link com.devmod.actions.catalog.ActionSpec},
 *       preserving all metadata and handler references</li>
 *   <li>{@link com.devmod.actions.legacy.LegacyCompatBridge} - Routes action invocations:
 *       if the action has been migrated to V2, uses the new engine; otherwise falls back
 *       to the old {@code ActionRegistry}</li>
 *   <li>{@link com.devmod.actions.legacy.ShadowModeComparator} - During dual-run mode,
 *       executes both V1 and V2 pipelines and compares results, logging discrepancies
 *       for validation before cutover</li>
 * </ul>
 *
 * <h2>Migration Lifecycle</h2>
 * <ol>
 *   <li><b>Phase 1</b> - All actions go through V1 (current state)</li>
 *   <li><b>Phase 2</b> - LegacyCompatBridge installed; migrated domains use V2, rest use V1</li>
 *   <li><b>Phase 3</b> - Shadow mode: both pipelines run, comparator logs mismatches</li>
 *   <li><b>Phase 4</b> - V2 becomes primary; V1 kept as fallback</li>
 *   <li><b>Phase 5</b> - V1 code removed; this package deleted</li>
 * </ol>
 *
 * <h2>Contracts</h2>
 * <ul>
 *   <li>The bridge must produce identical observable behavior to V1 for unmigrated actions</li>
 *   <li>Shadow mode comparator must not affect action outcomes (read-only comparison)</li>
 *   <li>Adapter must handle all RadialAction fields including nullable ones</li>
 * </ul>
 *
 * <h2>Boundaries</h2>
 * <ul>
 *   <li>This package is temporary - it will be removed after full migration</li>
 *   <li>New features should be built against V2 APIs, not the legacy bridge</li>
 * </ul>
 *
 * @see com.devmod.actions.ActionRegistry
 * @see com.devmod.actions.engine.ActionExecutor
 */
package com.devmod.actions.legacy;
