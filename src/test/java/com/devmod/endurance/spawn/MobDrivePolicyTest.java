package com.devmod.endurance.spawn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ageoffight.mobs.entity.AshenCourtCombatant;
import com.ageoffight.mobs.entity.SquadCombatant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Locks the guard that keeps DevMod from writing attribute base values on mobs whose own mod reads
 * them back as a design budget.
 *
 * <p>These assertions look trivial and are not: the whole guard is a set of fully qualified class
 * names matched against a hierarchy, and if a name stops matching nothing fails, nothing logs, and
 * the endurance system quietly resumes freezing those mobs. This suite has already earned its keep
 * twice -- it caught an {@code ArrayDeque} rejecting the null superclass of an interface, and an
 * {@code FMLPaths} call that threw outside a Minecraft runtime on the wave spawn path.
 *
 * <p>The stubs under {@code com.ageoffight.mobs.entity} mirror the real shapes: a class extending a
 * class, and an interface, so both arms of the walk are covered.
 */
@DisplayName("MobDrivePolicy guarded-type detection")
class MobDrivePolicyTest {

    private static final String GUARDED_CLASS = "com.ageoffight.mobs.entity.AshenCourtCombatant";
    private static final String GUARDED_INTERFACE = "com.ageoffight.mobs.entity.SquadCombatant";

    /** Mirrors BoneboundVanguard: one step up the superclass chain. */
    private static final class Vanguard extends AshenCourtCombatant {
    }

    /** Mirrors an archetype subclassed further, so the walk has to climb twice. */
    private static class Archetype extends AshenCourtCombatant {
    }

    private static final class GraveCantor extends Archetype {
    }

    /** Implements only the squad marker, reaching the guard through the interface arm. */
    private static final class MarkerOnly implements SquadCombatant {
    }

    /** A plain vanilla-shaped mob class: nothing here should be guarded. */
    private static final class OrdinaryMob {
    }

    @AfterEach
    void resetCaches() {
        MobDrivePolicy.invalidate();
    }

    @Test
    @DisplayName("a direct subclass of the Ashen Court base class is guarded")
    void directSubclassIsGuarded() {
        assertEquals(GUARDED_CLASS, MobDrivePolicy.firstGuardedSupertype(Vanguard.class));
    }

    @Test
    @DisplayName("a deeper subclass inherits the guard, so a new archetype is covered on the day it is added")
    void deeperSubclassInheritsTheGuard() {
        assertEquals(GUARDED_CLASS, MobDrivePolicy.firstGuardedSupertype(GraveCantor.class));
    }

    @Test
    @DisplayName("the squad marker interface alone is enough to be guarded")
    void interfaceArmIsGuarded() {
        assertEquals(GUARDED_INTERFACE, MobDrivePolicy.firstGuardedSupertype(MarkerOnly.class));
    }

    @Test
    @DisplayName("an unrelated mob class is not guarded and keeps its scaling")
    void unrelatedClassIsNotGuarded() {
        assertNull(MobDrivePolicy.firstGuardedSupertype(OrdinaryMob.class));
    }

    @Test
    @DisplayName("the walk terminates on Object rather than looping or throwing on a null superclass")
    void walkTerminates() {
        assertNull(MobDrivePolicy.firstGuardedSupertype(Object.class));
    }

    @Test
    @DisplayName("resolving the guard list does not need a Minecraft runtime")
    void guardListResolvesWithoutFml() {
        // The config path goes through ConfigPaths, which calls FMLPaths.CONFIGDIR.get() and throws
        // when FML is absent. That call sits on the wave spawn path with no try around it, so it
        // must degrade to the built-in guards instead of propagating -- this asserts it does.
        assertEquals(GUARDED_CLASS, MobDrivePolicy.firstGuardedSupertype(Vanguard.class));
        assertNull(MobDrivePolicy.firstGuardedSupertype(String.class));
    }
}
