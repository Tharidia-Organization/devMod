package com.ageoffight.mobs.entity;

/**
 * Test-only stand-in for Age of Fight's Ashen Court base type.
 *
 * <p>An abstract class implementing {@link SquadCombatant}, mirroring the real
 * {@code public abstract class AshenCourtCombatant extends Skeleton implements SquadCombatant} so
 * that a test subclass reaches the guard the same way BoneboundVanguard and the six archetypes do:
 * up the superclass chain. Previously this stub was an interface, and every test therefore matched
 * through {@code getInterfaces()} only, leaving the {@code getSuperclass()} arm -- and its null
 * guard -- effectively uncovered.
 *
 * <p>It deliberately does not extend anything from Minecraft: the walk under test only reads class
 * names, so a bare hierarchy is enough and keeps this a plain unit test.
 */
public abstract class AshenCourtCombatant implements SquadCombatant {
}
