package com.devmod.testing.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

public class AchievementTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementTracker.class);
    public static final AchievementTracker INSTANCE = new AchievementTracker();

    private boolean firstBlood = false;
    private boolean firstHeadshot = false;
    private boolean firstMaceSmash = false;
    private boolean survivedExplosion = false;
    private boolean killedWithEveryBodyPart = false;
    private boolean usedAllOverlays = false;

    private AchievementTracker() {}

    public boolean unlockFirstBlood() {
        if (!firstBlood) {
            firstBlood = true;
            LOGGER.info("Achievement unlocked: First Blood!");
            return true;
        }
        return false;
    }

    public boolean unlockFirstHeadshot() {
        if (!firstHeadshot) {
            firstHeadshot = true;
            LOGGER.info("Achievement unlocked: First Headshot!");
            return true;
        }
        return false;
    }

    public boolean unlockFirstMaceSmash() {
        if (!firstMaceSmash) {
            firstMaceSmash = true;
            LOGGER.info("Achievement unlocked: First Mace Smash!");
            return true;
        }
        return false;
    }

    public boolean unlockSurvivedExplosion() {
        if (!survivedExplosion) {
            survivedExplosion = true;
            LOGGER.info("Achievement unlocked: Survived an Explosion!");
            return true;
        }
        return false;
    }

    public boolean unlockBodyPartMaster() {
        if (!killedWithEveryBodyPart) {
            killedWithEveryBodyPart = true;
            LOGGER.info("Achievement unlocked: Body Part Master!");
            return true;
        }
        return false;
    }

    public boolean unlockOverlayMaster() {
        if (!usedAllOverlays) {
            usedAllOverlays = true;
            LOGGER.info("Achievement unlocked: Overlay Master!");
            return true;
        }
        return false;
    }

    // === Getters ===
    public boolean hasFirstBlood() { return firstBlood; }
    public boolean hasFirstHeadshot() { return firstHeadshot; }
    public boolean hasFirstMaceSmash() { return firstMaceSmash; }
    public boolean hasSurvivedExplosion() { return survivedExplosion; }
    public boolean hasKilledWithEveryBodyPart() { return killedWithEveryBodyPart; }
    public boolean hasUsedAllOverlays() { return usedAllOverlays; }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("firstBlood", firstBlood);
        json.addProperty("firstHeadshot", firstHeadshot);
        json.addProperty("firstMaceSmash", firstMaceSmash);
        json.addProperty("survivedExplosion", survivedExplosion);
        json.addProperty("bodyPartMaster", killedWithEveryBodyPart);
        json.addProperty("overlayMaster", usedAllOverlays);
        return json;
    }

    public void fromJson(JsonObject json) {
        firstBlood = getBool(json, "firstBlood", false);
        firstHeadshot = getBool(json, "firstHeadshot", false);
        firstMaceSmash = getBool(json, "firstMaceSmash", false);
        survivedExplosion = getBool(json, "survivedExplosion", false);
        killedWithEveryBodyPart = getBool(json, "bodyPartMaster", false);
        usedAllOverlays = getBool(json, "overlayMaster", false);
    }

    public void reset() {
        firstBlood = false;
        firstHeadshot = false;
        firstMaceSmash = false;
        survivedExplosion = false;
        killedWithEveryBodyPart = false;
        usedAllOverlays = false;
    }

    private boolean getBool(JsonObject obj, String key, boolean defaultValue) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : defaultValue;
    }
}
