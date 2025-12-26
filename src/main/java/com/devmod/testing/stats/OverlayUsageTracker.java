package com.devmod.testing.stats;

import com.google.gson.JsonObject;
public class OverlayUsageTracker {
    public static final OverlayUsageTracker INSTANCE = new OverlayUsageTracker();

    // Overlay toggles
    private int impactHudToggles = 0;
    private int debugRendererToggles = 0;
    private int pathfindingToggles = 0;
    private int lightLevelToggles = 0;
    private int roomBoundsToggles = 0;
    private int lineOfSightToggles = 0;
    private int safeSpotToggles = 0;

    // Screen opens
    private int telemetryDashboardOpens = 0;
    private int mobConfigScreenOpens = 0;
    private int weaponEditorOpens = 0;
    private int qaScreenOpens = 0;

    private OverlayUsageTracker() {}

    /**
     * Record overlay toggle.
     * @return true if all overlays have been used at least once
     */
    public boolean recordOverlayToggle(String overlayName) {
        switch (overlayName.toLowerCase()) {
            case "impact_hud" -> impactHudToggles++;
            case "debug_renderer" -> debugRendererToggles++;
            case "pathfinding" -> pathfindingToggles++;
            case "light_level" -> lightLevelToggles++;
            case "room_bounds" -> roomBoundsToggles++;
            case "line_of_sight" -> lineOfSightToggles++;
            case "safe_spots" -> safeSpotToggles++;
        }
        return hasUsedAllOverlays();
    }

    /**
     * Record screen open.
     */
    public void recordScreenOpen(String screenName) {
        switch (screenName.toLowerCase()) {
            case "telemetry" -> telemetryDashboardOpens++;
            case "mob_config" -> mobConfigScreenOpens++;
            case "weapon_editor" -> weaponEditorOpens++;
            case "qa_testing" -> qaScreenOpens++;
        }
    }

    /**
     * Check if all overlays have been used at least once.
     */
    public boolean hasUsedAllOverlays() {
        return impactHudToggles > 0 && debugRendererToggles > 0 && pathfindingToggles > 0 &&
               lightLevelToggles > 0 && roomBoundsToggles > 0 && lineOfSightToggles > 0 && safeSpotToggles > 0;
    }

    // === Getters ===
    public int getOverlayToggles(String overlayName) {
        return switch (overlayName.toLowerCase()) {
            case "impact_hud" -> impactHudToggles;
            case "debug_renderer" -> debugRendererToggles;
            case "pathfinding" -> pathfindingToggles;
            case "light_level" -> lightLevelToggles;
            case "room_bounds" -> roomBoundsToggles;
            case "line_of_sight" -> lineOfSightToggles;
            case "safe_spots" -> safeSpotToggles;
            default -> 0;
        };
    }

    public int getTotalOverlayToggles() {
        return impactHudToggles + debugRendererToggles + pathfindingToggles +
               lightLevelToggles + roomBoundsToggles + lineOfSightToggles + safeSpotToggles;
    }

    public int getScreenOpens(String screenName) {
        return switch (screenName.toLowerCase()) {
            case "telemetry" -> telemetryDashboardOpens;
            case "mob_config" -> mobConfigScreenOpens;
            case "weapon_editor" -> weaponEditorOpens;
            case "qa_testing" -> qaScreenOpens;
            default -> 0;
        };
    }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        JsonObject overlays = new JsonObject();
        overlays.addProperty("impactHud", impactHudToggles);
        overlays.addProperty("debugRenderer", debugRendererToggles);
        overlays.addProperty("pathfinding", pathfindingToggles);
        overlays.addProperty("lightLevel", lightLevelToggles);
        overlays.addProperty("roomBounds", roomBoundsToggles);
        overlays.addProperty("lineOfSight", lineOfSightToggles);
        overlays.addProperty("safeSpots", safeSpotToggles);
        json.add("overlays", overlays);

        JsonObject screens = new JsonObject();
        screens.addProperty("telemetry", telemetryDashboardOpens);
        screens.addProperty("mobConfig", mobConfigScreenOpens);
        screens.addProperty("weaponEditor", weaponEditorOpens);
        screens.addProperty("qa", qaScreenOpens);
        json.add("screens", screens);

        return json;
    }

    public void fromJson(JsonObject json) {
        if (json.has("overlays")) {
            JsonObject overlays = json.getAsJsonObject("overlays");
            impactHudToggles = getInt(overlays, "impactHud", 0);
            debugRendererToggles = getInt(overlays, "debugRenderer", 0);
            pathfindingToggles = getInt(overlays, "pathfinding", 0);
            lightLevelToggles = getInt(overlays, "lightLevel", 0);
            roomBoundsToggles = getInt(overlays, "roomBounds", 0);
            lineOfSightToggles = getInt(overlays, "lineOfSight", 0);
            safeSpotToggles = getInt(overlays, "safeSpots", 0);
        }

        if (json.has("screens")) {
            JsonObject screens = json.getAsJsonObject("screens");
            telemetryDashboardOpens = getInt(screens, "telemetry", 0);
            mobConfigScreenOpens = getInt(screens, "mobConfig", 0);
            weaponEditorOpens = getInt(screens, "weaponEditor", 0);
            qaScreenOpens = getInt(screens, "qa", 0);
        }
    }

    public void reset() {
        impactHudToggles = 0;
        debugRendererToggles = 0;
        pathfindingToggles = 0;
        lightLevelToggles = 0;
        roomBoundsToggles = 0;
        lineOfSightToggles = 0;
        safeSpotToggles = 0;
        telemetryDashboardOpens = 0;
        mobConfigScreenOpens = 0;
        weaponEditorOpens = 0;
        qaScreenOpens = 0;
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }
}
