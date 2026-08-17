package com.ageoffight.mobs.entity;

/**
 * Test-only stand-in for Age of Fight's squad-membership marker.
 *
 * <p>An interface, as the real one is. Paired with {@link AshenCourtCombatant}, which is a class, so
 * the two stubs between them exercise both arms of the hierarchy walk in MobDrivePolicy: the
 * superclass chain and the interface set. Both fully qualified names must match Age of Fight
 * exactly, because the guard is a string comparison and a typo in it fails silently -- the endurance
 * system would resume writing those mobs' attributes and they would go back to standing still with
 * no error anywhere.
 */
public interface SquadCombatant {
}
