package com.devmod.compat.mods.puffishskills;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class PuffishSkillsCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PuffishSkillsCompat.class);
    public static final String MOD_ID = "puffish_skills";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> skillsApiClass;
    private static Class<?> skillCategoryClass;
    private static Method getExperienceMethod;
    private static Method getPointsSpentMethod;
    private static Method getUnlockedSkillsMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Puffish Skills";
    }

    @Override
    public int priority() {
        // Medium priority - progression system
        return 33;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:puffishskills] Puffish Skills not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:puffishskills] Puffish Skills detected");
        LOGGER.debug("[Compat:puffishskills] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load Puffish Skills API classes via reflection.
     */
    private void loadApi() {
        try {
            // Puffish Skills package structure
            String[] packages = {
                "net.puffish.skillsmod.api",
                "net.puffish.skillsmod"
            };

            for (String pkg : packages) {
                try {
                    skillsApiClass = Class.forName(pkg + ".SkillsAPI");
                    LOGGER.debug("[Compat:puffishskills] Found SkillsAPI at {}", pkg);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            // Try to find category class
            try {
                skillCategoryClass = Class.forName(
                    "net.puffish.skillsmod.api.Category");
            } catch (ClassNotFoundException ignored) {}

            if (skillsApiClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:puffishskills] Puffish Skills API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:puffishskills] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:puffishskills] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Skill tracking, experience monitoring, progression display";
    }

    /**
     * Check if Puffish Skills is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Get skill progress info for a player.
     *
     * @param player The player (must be ServerPlayer for full data)
     * @return Map of skill progress data
     */
    public static Map<String, Object> getSkillProgress(Player player) {
        Map<String, Object> progress = new LinkedHashMap<>();

        if (!apiAvailable || player == null) {
            return progress;
        }

        try {
            // Get categories for player
            if (skillsApiClass != null) {
                Method getCategoriesMethod = skillsApiClass.getMethod("getCategories");
                Object categories = getCategoriesMethod.invoke(null);

                if (categories instanceof Collection<?> categoryList) {
                    progress.put("categoryCount", categoryList.size());

                    List<Map<String, Object>> categoryData = new ArrayList<>();
                    for (Object category : categoryList) {
                        Map<String, Object> catInfo = getCategoryInfo(player, category);
                        if (!catInfo.isEmpty()) {
                            categoryData.add(catInfo);
                        }
                    }

                    if (!categoryData.isEmpty()) {
                        progress.put("categories", categoryData);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:puffishskills] Error getting skill progress: {}", e.getMessage());
        }

        return progress;
    }

    private static Map<String, Object> getCategoryInfo(Player player, Object category) {
        Map<String, Object> info = new LinkedHashMap<>();

        try {
            // Get category ID
            Method getIdMethod = category.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(category);
            if (id != null) {
                info.put("id", id.toString());
            }

            // Try to get player-specific data
            if (player instanceof ServerPlayer serverPlayer) {
                // Get experience in this category
                try {
                    Method getExperienceMethod = category.getClass()
                        .getMethod("getExperience", ServerPlayer.class);
                    Object xp = getExperienceMethod.invoke(category, serverPlayer);
                    if (xp instanceof Number) {
                        info.put("experience", ((Number) xp).intValue());
                    }
                } catch (NoSuchMethodException ignored) {}

                // Get points spent
                try {
                    Method getPointsSpentMethod = category.getClass()
                        .getMethod("getPointsSpent", ServerPlayer.class);
                    Object points = getPointsSpentMethod.invoke(category, serverPlayer);
                    if (points instanceof Number) {
                        info.put("pointsSpent", ((Number) points).intValue());
                    }
                } catch (NoSuchMethodException ignored) {}

                // Get unlocked skills count
                try {
                    Method getUnlockedMethod = category.getClass()
                        .getMethod("getUnlockedSkills", ServerPlayer.class);
                    Object unlocked = getUnlockedMethod.invoke(category, serverPlayer);
                    if (unlocked instanceof Collection<?> unlockedSkills) {
                        info.put("unlockedCount", unlockedSkills.size());
                    }
                } catch (NoSuchMethodException ignored) {}
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:puffishskills] Error getting category info: {}", e.getMessage());
        }

        return info;
    }

    /**
     * Get total skill points spent by a player.
     *
     * @param player The player
     * @return Total points spent, or 0
     */
    public static int getTotalPointsSpent(Player player) {
        if (!apiAvailable || !(player instanceof ServerPlayer)) {
            return 0;
        }

        int total = 0;
        Map<String, Object> progress = getSkillProgress(player);

        Object categories = progress.get("categories");
        if (categories instanceof List<?> categoryList) {
            for (Object catObj : categoryList) {
                if (catObj instanceof Map<?, ?> catInfo) {
                    Object points = catInfo.get("pointsSpent");
                    if (points instanceof Number) {
                        total += ((Number) points).intValue();
                    }
                }
            }
        }

        return total;
    }

    /**
     * Get skills summary for telemetry.
     *
     * @param player The player
     * @return Summary map
     */
    public static Map<String, Object> getSkillsSummary(Player player) {
        Map<String, Object> summary = new LinkedHashMap<>();

        if (!apiAvailable || player == null) {
            return summary;
        }

        summary.put("totalPointsSpent", getTotalPointsSpent(player));

        Map<String, Object> progress = getSkillProgress(player);
        Object categoryCount = progress.get("categoryCount");
        if (categoryCount != null) {
            summary.put("categoryCount", categoryCount);
        }

        return summary;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Puffish Skills: not available";
        }
        if (!apiAvailable) {
            return "Puffish Skills: detected (API not loaded)";
        }
        return "Puffish Skills: API available";
    }
}
