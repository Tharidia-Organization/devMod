/**
 * Actions V2 Catalog - declarative action specifications and validation.
 *
 * <p>The catalog is the single source of truth for all action metadata. It replaces
 * the imperative {@link com.devmod.actions.RadialAction} builder approach with
 * immutable, declarative {@link com.devmod.actions.catalog.ActionSpec} records.
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@link com.devmod.actions.catalog.ActionSpec} - Canonical action specification record</li>
 * </ul>
 *
 * <p>Contracts:
 * <ul>
 *   <li>ActionSpec is immutable and validated at construction time</li>
 *   <li>Action IDs must match the pattern devmod.domain.name</li>
 *   <li>Two ActionSpec instances are equal if and only if their IDs are equal</li>
 * </ul>
 *
 * @see com.devmod.actions.domains.DomainRegistrar
 * @see com.devmod.actions.engine.ActionExecutor
 */
@ParametersAreNonnullByDefault
package com.devmod.actions.catalog;

import javax.annotation.ParametersAreNonnullByDefault;
