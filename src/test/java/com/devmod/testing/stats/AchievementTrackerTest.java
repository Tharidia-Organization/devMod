package com.devmod.testing.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class AchievementTrackerTest {

    private AchievementTracker tracker;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<AchievementTracker> ctor = AchievementTracker.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        tracker = ctor.newInstance();
    }

    @Test
    @DisplayName("unlockFirstBlood returns true on first call only")
    void unlockFirstBlood() {
        assertTrue(tracker.unlockFirstBlood());
        assertFalse(tracker.unlockFirstBlood());
        assertTrue(tracker.hasFirstBlood());
    }

    @Test
    @DisplayName("unlockFirstHeadshot idempotent")
    void unlockFirstHeadshot() {
        assertFalse(tracker.hasFirstHeadshot());
        assertTrue(tracker.unlockFirstHeadshot());
        assertFalse(tracker.unlockFirstHeadshot());
        assertTrue(tracker.hasFirstHeadshot());
    }

    @Test
    @DisplayName("unlockFirstMaceSmash idempotent")
    void unlockFirstMaceSmash() {
        assertTrue(tracker.unlockFirstMaceSmash());
        assertFalse(tracker.unlockFirstMaceSmash());
    }

    @Test
    @DisplayName("unlockSurvivedExplosion idempotent")
    void unlockSurvivedExplosion() {
        assertTrue(tracker.unlockSurvivedExplosion());
        assertFalse(tracker.unlockSurvivedExplosion());
    }

    @Test
    @DisplayName("unlockBodyPartMaster idempotent")
    void unlockBodyPartMaster() {
        assertTrue(tracker.unlockBodyPartMaster());
        assertFalse(tracker.unlockBodyPartMaster());
        assertTrue(tracker.hasKilledWithEveryBodyPart());
    }

    @Test
    @DisplayName("unlockOverlayMaster idempotent")
    void unlockOverlayMaster() {
        assertTrue(tracker.unlockOverlayMaster());
        assertFalse(tracker.unlockOverlayMaster());
        assertTrue(tracker.hasUsedAllOverlays());
    }

    @Test
    @DisplayName("toJson/fromJson round-trip preserves all flags")
    void jsonRoundTrip() {
        tracker.unlockFirstBlood();
        tracker.unlockSurvivedExplosion();

        JsonObject json = tracker.toJson();

        AchievementTracker restored;
        try {
            Constructor<AchievementTracker> ctor = AchievementTracker.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            restored = ctor.newInstance();
        } catch (Exception e) {
            fail("Cannot create instance");
            return;
        }
        restored.fromJson(json);

        assertTrue(restored.hasFirstBlood());
        assertFalse(restored.hasFirstHeadshot());
        assertTrue(restored.hasSurvivedExplosion());
        assertFalse(restored.hasKilledWithEveryBodyPart());
    }

    @Test
    @DisplayName("reset clears all achievements")
    void reset() {
        tracker.unlockFirstBlood();
        tracker.unlockFirstHeadshot();
        tracker.unlockFirstMaceSmash();
        tracker.reset();

        assertFalse(tracker.hasFirstBlood());
        assertFalse(tracker.hasFirstHeadshot());
        assertFalse(tracker.hasFirstMaceSmash());
    }
}
