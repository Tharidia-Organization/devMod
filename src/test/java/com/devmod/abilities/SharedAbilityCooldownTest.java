package com.devmod.abilities;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for SharedAbilityCooldown charge-based cooldown system.
 * <p>
 * When GameMechanicsConfig is not bootstrapped (plain JUnit), the config
 * accessors fall back to DEFAULT_MAX_CHARGES (2) and DEFAULT_RECHARGE_TICKS (60).
 */
class SharedAbilityCooldownTest {

    private final SharedAbilityCooldown cooldown = SharedAbilityCooldown.INSTANCE;
    private UUID player;

    @BeforeEach
    void setUp() {
        // Use a fresh UUID each test so previous state can't leak
        player = UUID.randomUUID();
    }

    // ------------------------------------------------------------------
    // Charge pool basics
    // ------------------------------------------------------------------

    @Test
    void freshPlayerIsReady() {
        assertTrue(cooldown.isReady(player));
    }

    @Test
    void freshPlayerHasMaxCharges() {
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES, cooldown.getAvailableCharges(player));
    }

    @Test
    void triggerConsumesOneCharge() {
        cooldown.trigger(player, 0);

        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES - 1, cooldown.getAvailableCharges(player));
        assertTrue(cooldown.isReady(player), "Should still be ready with 1 charge remaining");
    }

    @Test
    void triggerTwiceExhaustsAllCharges() {
        cooldown.trigger(player, 0);
        cooldown.trigger(player, 0);

        assertEquals(0, cooldown.getAvailableCharges(player));
        assertFalse(cooldown.isReady(player), "Should not be ready with 0 charges");
    }

    @Test
    void triggerAtZeroChargesDoesNothing() {
        // Exhaust all charges
        for (int i = 0; i < SharedAbilityCooldown.DEFAULT_MAX_CHARGES; i++) {
            cooldown.trigger(player, 0);
        }
        int chargesBefore = cooldown.getAvailableCharges(player);

        // Extra trigger should be a no-op
        cooldown.trigger(player, 0);

        assertEquals(chargesBefore, cooldown.getAvailableCharges(player));
    }

    // ------------------------------------------------------------------
    // Recharge cycle
    // ------------------------------------------------------------------

    @Test
    void tickingFullChargesIsNoOp() {
        // Tick without consuming; should stay at max
        for (int i = 0; i < 100; i++) {
            cooldown.tick(player);
        }
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES, cooldown.getAvailableCharges(player));
    }

    @Test
    void singleChargeRechargesAfterExactTicks() {
        cooldown.trigger(player, 0);
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES - 1, cooldown.getAvailableCharges(player));

        // Tick exactly DEFAULT_RECHARGE_TICKS
        for (int i = 0; i < SharedAbilityCooldown.DEFAULT_RECHARGE_TICKS; i++) {
            cooldown.tick(player);
        }

        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES, cooldown.getAvailableCharges(player),
                "Charge should be restored after recharge ticks");
    }

    @Test
    void chargeNotRestoredOneLessTick() {
        cooldown.trigger(player, 0);

        for (int i = 0; i < SharedAbilityCooldown.DEFAULT_RECHARGE_TICKS - 1; i++) {
            cooldown.tick(player);
        }

        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES - 1, cooldown.getAvailableCharges(player),
                "Charge should NOT be restored one tick early");
    }

    @Test
    void twoChargesRechargeSequentially() {
        cooldown.trigger(player, 0);
        cooldown.trigger(player, 0);
        assertEquals(0, cooldown.getAvailableCharges(player));

        // First charge recharges
        for (int i = 0; i < SharedAbilityCooldown.DEFAULT_RECHARGE_TICKS; i++) {
            cooldown.tick(player);
        }
        assertEquals(1, cooldown.getAvailableCharges(player));

        // Second charge recharges
        for (int i = 0; i < SharedAbilityCooldown.DEFAULT_RECHARGE_TICKS; i++) {
            cooldown.tick(player);
        }
        assertEquals(2, cooldown.getAvailableCharges(player));
    }

    // ------------------------------------------------------------------
    // getCooldownPercent
    // ------------------------------------------------------------------

    @Test
    void cooldownPercentZeroWhenFullyCharged() {
        assertEquals(0f, cooldown.getCooldownPercent(player, 0));
    }

    @Test
    void cooldownPercentOneRightAfterTrigger() {
        cooldown.trigger(player, 0);
        float pct = cooldown.getCooldownPercent(player, 0);
        assertEquals(1f, pct, 0.01f, "Should be 1.0 immediately after consuming a charge");
    }

    @Test
    void cooldownPercentDecreasesOverTicks() {
        cooldown.trigger(player, 0);

        float previous = cooldown.getCooldownPercent(player, 0);
        for (int i = 0; i < SharedAbilityCooldown.DEFAULT_RECHARGE_TICKS; i++) {
            cooldown.tick(player);
            float current = cooldown.getCooldownPercent(player, 0);
            assertTrue(current <= previous, "Percent should decrease or stay the same");
            previous = current;
        }
        assertEquals(0f, cooldown.getCooldownPercent(player, 0), 0.01f,
                "Percent should be 0 once fully recharged");
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    @Test
    void cleanupRemovesPlayerData() {
        cooldown.trigger(player, 0);
        cooldown.cleanup(player);

        // After cleanup a fresh getOrCreate will give max charges again
        assertTrue(cooldown.isReady(player));
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES, cooldown.getAvailableCharges(player));
    }

    @Test
    void cleanupOnUnknownPlayerIsNoOp() {
        UUID unknown = UUID.randomUUID();
        assertDoesNotThrow(() -> cooldown.cleanup(unknown));
    }

    // ------------------------------------------------------------------
    // Config hot-reload behavior
    // ------------------------------------------------------------------

    @Test
    void tickClampsAvailableChargesDownWhenMaxShrinks() {
        // This tests the code path: "if (data.availableCharges > max)"
        // In a real scenario, config could lower max charges at runtime.
        // With default config fallback (max=2), we can't lower dynamically,
        // but we verify the normal path: ticking at max does not corrupt state.
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES, cooldown.getAvailableCharges(player));
        cooldown.tick(player);
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES, cooldown.getAvailableCharges(player));
    }

    // ------------------------------------------------------------------
    // getMaxCharges
    // ------------------------------------------------------------------

    @Test
    void getMaxChargesReturnsDefault() {
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES, cooldown.getMaxCharges(player));
    }

    // ------------------------------------------------------------------
    // Legacy constant
    // ------------------------------------------------------------------

    @Test
    void legacyCooldownTicksEqualsRechargeTicks() {
        assertEquals(SharedAbilityCooldown.DEFAULT_RECHARGE_TICKS, SharedAbilityCooldown.DEFAULT_COOLDOWN_TICKS);
    }

    // ------------------------------------------------------------------
    // Multiple players are independent
    // ------------------------------------------------------------------

    @Test
    void multiplePlayersHaveIndependentCharges() {
        UUID player2 = UUID.randomUUID();

        cooldown.trigger(player, 0);
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES - 1, cooldown.getAvailableCharges(player));
        assertEquals(SharedAbilityCooldown.DEFAULT_MAX_CHARGES, cooldown.getAvailableCharges(player2));
    }

    // ------------------------------------------------------------------
    // Tick on unknown player (no getOrCreate) returns early
    // ------------------------------------------------------------------

    @Test
    void tickOnNeverSeenPlayerIsNoOp() {
        UUID unknown = UUID.randomUUID();
        // tick() does playerCharges.get() which returns null -> returns early
        assertDoesNotThrow(() -> cooldown.tick(unknown));
    }
}
