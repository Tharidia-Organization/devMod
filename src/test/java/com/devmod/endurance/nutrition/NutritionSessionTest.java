package com.devmod.endurance.nutrition;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NutritionSessionTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID QUEST = UUID.randomUUID();

    private NutritionSession wellFedSession() {
        // All values above WELL_FED_THRESHOLD (0.6)
        float[] vals = {0.8f, 0.9f, 0.7f, 0.85f, 0.75f, 0.65f};
        return new NutritionSession(PLAYER, QUEST, vals);
    }

    private NutritionSession neutralSession() {
        // All at 0.5 (above malnourished, below well-fed)
        float[] vals = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
        return new NutritionSession(PLAYER, QUEST, vals);
    }

    private NutritionSession malnourishedSession() {
        // One category below MALNOURISHED_THRESHOLD (0.2)
        float[] vals = {0.8f, 0.15f, 0.7f, 0.8f, 0.7f, 0.6f};
        return new NutritionSession(PLAYER, QUEST, vals);
    }

    private NutritionSession criticalSession() {
        // One category below CRITICAL_THRESHOLD (0.1)
        float[] vals = {0.8f, 0.05f, 0.7f, 0.8f, 0.7f, 0.6f};
        return new NutritionSession(PLAYER, QUEST, vals);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("Null initial values default to 0.5")
        void nullValuesDefaultToHalf() {
            NutritionSession session = new NutritionSession(PLAYER, QUEST, null);
            float[] current = session.getCurrentValues();
            for (float v : current) {
                assertEquals(0.5f, v, 0.001f);
            }
        }

        @Test
        @DisplayName("Short array defaults to 0.5")
        void shortArrayDefaults() {
            float[] shortVals = {0.8f, 0.9f}; // Only 2 values
            NutritionSession session = new NutritionSession(PLAYER, QUEST, shortVals);
            float[] current = session.getCurrentValues();
            for (float v : current) {
                assertEquals(0.5f, v, 0.001f);
            }
        }

        @Test
        @DisplayName("Snapshot preserves initial values")
        void snapshotPreservesInitial() {
            float[] vals = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f};
            NutritionSession session = new NutritionSession(PLAYER, QUEST, vals);
            float[] snapshot = session.getSnapshotValues();
            assertArrayEquals(vals, snapshot, 0.001f);
        }

        @Test
        @DisplayName("getCurrentValues returns a copy")
        void currentValuesAreCopy() {
            NutritionSession session = wellFedSession();
            float[] v1 = session.getCurrentValues();
            float[] v2 = session.getCurrentValues();
            assertNotSame(v1, v2);
        }
    }

    @Nested
    @DisplayName("State detection")
    class StateDetectionTests {
        @Test
        @DisplayName("Well-fed when all categories >= 60%")
        void wellFedDetection() {
            NutritionSession session = wellFedSession();
            assertTrue(session.isWellFed());
            assertFalse(session.isMalnourished());
            assertFalse(session.isCritical());
        }

        @Test
        @DisplayName("Neutral state: not well-fed, not malnourished")
        void neutralDetection() {
            NutritionSession session = neutralSession();
            assertFalse(session.isWellFed());
            assertFalse(session.isMalnourished());
            assertFalse(session.isCritical());
        }

        @Test
        @DisplayName("Malnourished when any category < 20%")
        void malnourishedDetection() {
            NutritionSession session = malnourishedSession();
            assertFalse(session.isWellFed());
            assertTrue(session.isMalnourished());
        }

        @Test
        @DisplayName("Critical when any category < 10%")
        void criticalDetection() {
            NutritionSession session = criticalSession();
            assertTrue(session.isCritical());
            assertTrue(session.isMalnourished()); // Critical implies malnourished
        }
    }

    @Nested
    @DisplayName("Combat modifiers - Well-fed")
    class WellFedModifiers {
        @Test
        @DisplayName("Damage multiplier is 1.15 when well-fed")
        void damageBonus() {
            assertEquals(1.15f, wellFedSession().getDamageMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Damage reduction is 0.20 when well-fed")
        void damageReduction() {
            assertEquals(0.20f, wellFedSession().getDamageReduction(), 0.001f);
        }

        @Test
        @DisplayName("Speed multiplier is 1.10 when well-fed")
        void speedBonus() {
            assertEquals(1.10f, wellFedSession().getSpeedMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Health regen is positive when well-fed")
        void healthRegen() {
            assertEquals(0.5f, wellFedSession().getHealthChangePerSecond(), 0.001f);
        }

        @Test
        @DisplayName("Combo decay is normal when well-fed")
        void comboDecayNormal() {
            assertEquals(1.0f, wellFedSession().getComboDecayMultiplier(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Combat modifiers - Neutral")
    class NeutralModifiers {
        @Test
        @DisplayName("Damage multiplier is 1.0 when neutral")
        void damageNeutral() {
            assertEquals(1.0f, neutralSession().getDamageMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Damage reduction is 0 when neutral")
        void noReduction() {
            assertEquals(0f, neutralSession().getDamageReduction(), 0.001f);
        }

        @Test
        @DisplayName("Speed multiplier is 1.0 when neutral")
        void speedNeutral() {
            assertEquals(1.0f, neutralSession().getSpeedMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("No health change when neutral")
        void noHealthChange() {
            assertEquals(0f, neutralSession().getHealthChangePerSecond(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Combat modifiers - Malnourished/Critical")
    class MalnourishedModifiers {
        @Test
        @DisplayName("Damage multiplier is 0.75 when malnourished")
        void damagePenalty() {
            assertEquals(0.75f, malnourishedSession().getDamageMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Damage reduction is -0.50 (more damage taken) when malnourished")
        void moreDamageTaken() {
            assertEquals(-0.50f, malnourishedSession().getDamageReduction(), 0.001f);
        }

        @Test
        @DisplayName("Speed multiplier is 0.85 when malnourished")
        void speedPenalty() {
            assertEquals(0.85f, malnourishedSession().getSpeedMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Critical state causes health drain")
        void criticalHealthDrain() {
            float health = criticalSession().getHealthChangePerSecond();
            assertTrue(health < 0, "Critical should drain health");
            assertEquals(-NutritionSession.CRITICAL_HEALTH_DRAIN_PER_SEC, health, 0.001f);
        }

        @Test
        @DisplayName("Critical state increases combo decay")
        void criticalComboDecay() {
            assertEquals(1.5f, criticalSession().getComboDecayMultiplier(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Action restrictions")
    class ActionRestrictions {
        @Test
        @DisplayName("Healing allowed when well-fed or neutral")
        void healingAllowed() {
            assertTrue(wellFedSession().isHealingAllowed());
            assertTrue(neutralSession().isHealingAllowed());
        }

        @Test
        @DisplayName("Healing blocked when malnourished")
        void healingBlocked() {
            assertFalse(malnourishedSession().isHealingAllowed());
            assertFalse(criticalSession().isHealingAllowed());
        }

        @Test
        @DisplayName("Executions allowed unless critical")
        void executionRestrictions() {
            assertTrue(wellFedSession().canPerformExecutions());
            assertTrue(neutralSession().canPerformExecutions());
            assertTrue(malnourishedSession().canPerformExecutions());
            assertFalse(criticalSession().canPerformExecutions());
        }
    }

    @Nested
    @DisplayName("Value queries")
    class ValueQueries {
        @Test
        @DisplayName("getValue by category returns correct value")
        void getValueByCategory() {
            float[] vals = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f};
            NutritionSession session = new NutritionSession(PLAYER, QUEST, vals);
            assertEquals(0.1f, session.getValue(NutritionCategory.GRAIN), 0.001f);
            assertEquals(0.6f, session.getValue(NutritionCategory.WATER), 0.001f);
        }

        @Test
        @DisplayName("getValue by index returns 0 for invalid index")
        void getValueInvalidIndex() {
            NutritionSession session = neutralSession();
            assertEquals(0f, session.getValue(-1), 0.001f);
            assertEquals(0f, session.getValue(10), 0.001f);
        }

        @Test
        @DisplayName("getLowestValue returns minimum")
        void lowestValue() {
            float[] vals = {0.5f, 0.2f, 0.8f, 0.3f, 0.9f, 0.4f};
            NutritionSession session = new NutritionSession(PLAYER, QUEST, vals);
            assertEquals(0.2f, session.getLowestValue(), 0.001f);
        }

        @Test
        @DisplayName("getLowestCategory returns category with minimum")
        void lowestCategory() {
            float[] vals = {0.5f, 0.2f, 0.8f, 0.3f, 0.9f, 0.4f};
            NutritionSession session = new NutritionSession(PLAYER, QUEST, vals);
            assertEquals(NutritionCategory.PROTEIN, session.getLowestCategory());
        }

        @Test
        @DisplayName("getBalance is average of all values")
        void balanceIsAverage() {
            float[] vals = {0.6f, 0.6f, 0.6f, 0.6f, 0.6f, 0.6f};
            NutritionSession session = new NutritionSession(PLAYER, QUEST, vals);
            assertEquals(0.6f, session.getBalance(), 0.001f);
        }
    }

    @Test
    @DisplayName("updateValues changes state dynamically")
    void updateChangesState() {
        NutritionSession session = wellFedSession();
        assertTrue(session.isWellFed());

        // Drop one category to trigger malnourished
        float[] newVals = {0.8f, 0.1f, 0.7f, 0.85f, 0.75f, 0.65f};
        session.updateValues(newVals, 100L);

        assertFalse(session.isWellFed());
        assertTrue(session.isMalnourished());
        assertEquals(100L, session.getLastUpdateTick());
    }

    @Test
    @DisplayName("updateValues ignores null input")
    void updateIgnoresNull() {
        NutritionSession session = wellFedSession();
        session.updateValues(null, 200L);
        assertTrue(session.isWellFed()); // State unchanged
        assertEquals(0L, session.getLastUpdateTick()); // Not updated
    }
}
