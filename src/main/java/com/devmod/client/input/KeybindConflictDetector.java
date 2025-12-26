package com.devmod.client.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class KeybindConflictDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeybindConflictDetector.class);

    /**
     * Represents a keybind conflict.
     *
     * @param devModKey The DevMod keybind
     * @param otherKey  The conflicting keybind (vanilla or other mod)
     * @param keyName   The key name (e.g., "G")
     */
    public record KeybindConflict(KeyMapping devModKey, KeyMapping otherKey, String keyName) {}

    // All DevMod keybinds for quick lookup
    private static final List<KeyMapping> DEVMOD_KEYS = List.of(
        KeyInputHandler.OPEN_RADIAL_MENU_KEY,
        KeyInputHandler.OPEN_SETTINGS_KEY,
        KeyInputHandler.OPEN_WEAPON_EDITOR_KEY,
        KeyInputHandler.OPEN_DASHBOARD_KEY,
        KeyInputHandler.INSPECT_MOB_KEY,
        KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY,
        KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY,
        KeyInputHandler.TOGGLE_HEATMAP_KEY,
        KeyInputHandler.DISMISS_IMPACT_HUD_KEY,
        KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY,
        KeyInputHandler.TOGGLE_PATHFINDING_KEY,
        KeyInputHandler.TOGGLE_LOS_KEY,
        KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY,
        KeyInputHandler.TOGGLE_SAFE_SPOT_KEY,
        KeyInputHandler.TOGGLE_ATTRIBUTE_MONITOR_KEY,
        KeyInputHandler.TOGGLE_FPS_TRACKER_KEY,
        KeyInputHandler.TOGGLE_PROFILER_KEY,
        KeyInputHandler.TOGGLE_ENTITY_DENSITY_KEY,
        KeyInputHandler.TOGGLE_BOSS_PHASE_KEY,
        KeyInputHandler.TOGGLE_SKILL_EFFICACY_KEY,
        KeyInputHandler.TOGGLE_SPAWNABILITY_KEY,
        KeyInputHandler.OPEN_QA_TESTING_KEY,
        KeyInputHandler.OPEN_TESTING_HUB_KEY,
        KeyInputHandler.TOGGLE_QUEST_HUD_KEY,
        KeyInputHandler.QUEST_COMPLETE_TASK_KEY,
        KeyInputHandler.OPEN_QUEST_EDITOR_KEY,
        KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY,
        KeyInputHandler.QUEST_CONTINUE_KEY,
        KeyInputHandler.QUEST_EXIT_KEY,
        KeyInputHandler.OPEN_PARTY_KEY,
        KeyInputHandler.TOGGLE_ECONOMY_KEY,
        KeyInputHandler.TOGGLE_CHUNK_PERF_KEY,
        KeyInputHandler.TOGGLE_HELP_KEY,
        KeyInputHandler.TEST_SCREEN_SHAKE_KEY,
        KeyInputHandler.DASH_KEY,
        KeyInputHandler.DODGE_KEY
    );

    private KeybindConflictDetector() {
        // Utility class
    }

    /**
     * Detect all keybind conflicts between DevMod and other mods/vanilla.
     *
     * @return List of conflicts (empty if none)
     */
    public static List<KeybindConflict> detectConflicts() {
        List<KeybindConflict> conflicts = new ArrayList<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return conflicts;
        }

        Options options = mc.options;
        if (options == null) {
            return conflicts;
        }

        // Build a map of key -> other keybinds (excluding DevMod)
        Map<String, List<KeyMapping>> keyToMappings = new HashMap<>();
        KeyMapping[] allKeys = mc.options.keyMappings;

        for (KeyMapping key : allKeys) {
            if (key == null || key.isUnbound()) {
                continue;
            }

            // Skip DevMod keys in this map
            if (isDevModKey(key)) {
                continue;
            }

            String keyCode = getKeyCode(key);
            keyToMappings.computeIfAbsent(keyCode, k -> new ArrayList<>()).add(key);
        }

        // Check each DevMod key for conflicts
        for (KeyMapping devModKey : DEVMOD_KEYS) {
            if (devModKey == null || devModKey.isUnbound()) {
                continue;
            }

            String keyCode = getKeyCode(devModKey);
            List<KeyMapping> otherKeys = keyToMappings.get(keyCode);

            if (otherKeys != null && !otherKeys.isEmpty()) {
                for (KeyMapping otherKey : otherKeys) {
                    // Check if contexts overlap
                    if (contextsConflict(devModKey, otherKey)) {
                        String keyName = devModKey.getTranslatedKeyMessage().getString();
                        conflicts.add(new KeybindConflict(devModKey, otherKey, keyName));
                        LOGGER.debug("Keybind conflict detected: {} ({}) vs {} ({})",
                            devModKey.getName(), keyName,
                            otherKey.getName(), otherKey.getTranslatedKeyMessage().getString());
                    }
                }
            }
        }

        return conflicts;
    }

    /**
     * Check if a specific DevMod key has any conflicts.
     *
     * @param devModKey The DevMod keybind to check
     * @return List of conflicting keybinds (empty if none)
     */
    public static List<KeyMapping> getConflictsFor(KeyMapping devModKey) {
        List<KeyMapping> conflicts = new ArrayList<>();

        if (devModKey == null || devModKey.isUnbound()) {
            return conflicts;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return conflicts;
        }

        String devModKeyCode = getKeyCode(devModKey);

        for (KeyMapping key : mc.options.keyMappings) {
            if (key == null || key.isUnbound() || key == devModKey) {
                continue;
            }

            if (Objects.equals(getKeyCode(key), devModKeyCode) && contextsConflict(devModKey, key)) {
                conflicts.add(key);
            }
        }

        return conflicts;
    }

    /**
     * Check if there are any keybind conflicts.
     *
     * @return true if at least one conflict exists
     */
    public static boolean hasConflicts() {
        return !detectConflicts().isEmpty();
    }

    /**
     * Get a summary of all conflicts for display.
     *
     * @return Human-readable conflict summary
     */
    public static String getConflictSummary() {
        List<KeybindConflict> conflicts = detectConflicts();
        if (conflicts.isEmpty()) {
            return "No keybind conflicts detected.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(conflicts.size()).append(" conflict(s) found:\n");
        for (KeybindConflict conflict : conflicts) {
            sb.append("• [").append(conflict.keyName()).append("] ")
                .append(getShortName(conflict.devModKey()))
                .append(" ↔ ")
                .append(getShortName(conflict.otherKey()))
                .append("\n");
        }
        return sb.toString();
    }

    /**
     * Get the number of conflicts.
     */
    public static int getConflictCount() {
        return detectConflicts().size();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static boolean isDevModKey(KeyMapping key) {
        String name = key.getName();
        return name != null && name.startsWith("key.devmod.");
    }

    private static String getKeyCode(KeyMapping key) {
        // Use the key's bound input to get a consistent identifier
        return key.saveString();
    }

    private static boolean contextsConflict(KeyMapping key1, KeyMapping key2) {
        // If either key has no context, they can conflict
        var ctx1 = key1.getKeyConflictContext();
        var ctx2 = key2.getKeyConflictContext();

        // If both are in-game only, they conflict
        // If either is universal, they conflict
        // NeoForge handles this via conflicts() method
        return ctx1.conflicts(ctx2);
    }

    private static String getShortName(KeyMapping key) {
        String name = key.getName();
        if (name == null) {
            return "Unknown";
        }
        // Remove common prefixes for readability
        if (name.startsWith("key.devmod.")) {
            return name.substring("key.devmod.".length());
        }
        if (name.startsWith("key.")) {
            return name.substring("key.".length());
        }
        return name;
    }
}
