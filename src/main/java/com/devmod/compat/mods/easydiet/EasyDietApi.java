package com.devmod.compat.mods.easydiet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.player.Player;

/**
 * Reflection-based API wrapper for Easy-Diet mod.
 *
 * <p>This class provides access to Easy-Diet functionality using reflection,
 * allowing DevMod to compile and run without Easy-Diet being present.
 * All methods should be called only after verifying {@link EasyDietCompat#isAvailable()}.
 *
 * <p><b>IMPORTANT:</b> This class uses reflection to avoid compile-time dependencies.
 * All methods will return default/safe values if Easy-Diet is not loaded.
 * Always check {@link EasyDietCompat#isAvailable()} before calling any method.
 *
 * <p>Example usage:
 * <pre>{@code
 * if (EasyDietCompat.isAvailable()) {
 *     float[] values = EasyDietApi.getDietValues(player);
 *     boolean wellFed = EasyDietApi.isWellFed(player);
 * }
 * }</pre>
 *
 * @author DevMod Team
 * @see EasyDietCompat
 */
public final class EasyDietApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(EasyDietApi.class);

    /** Threshold for "well-fed" status (all categories above this). */
    public static final float WELL_FED_THRESHOLD = 0.6f;

    /** Threshold for "malnourished" status (any category below this). */
    public static final float MALNOURISHED_THRESHOLD = 0.2f;

    /** Threshold for "critical" status (any category below this). */
    public static final float CRITICAL_THRESHOLD = 0.1f;

    /** Number of diet categories. */
    public static final int CATEGORY_COUNT = 6;

    /**
     * Default values when Easy-Diet is not available.
     *
     * <p>Using 0.5 (neutral) ensures no buff/debuff without Easy-Diet:
     * <ul>
     *   <li>0.5 > 0.2 (MALNOURISHED_THRESHOLD) → no penalty</li>
     *   <li>0.5 < 0.6 (WELL_FED_THRESHOLD) → no bonus</li>
     * </ul>
     */
    private static final float[] DEFAULT_VALUES = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};

    // =========================================================================
    // REFLECTION CACHE
    // =========================================================================

    /**
     * Reflection state flags.
     *
     * <p><b>State Machine</b> (R=reflectionInitialized, A=reflectionAvailable, U=easyDietUnavailable):
     * <pre>
     * ┌─────────────────────────────────────────────────────────────┐
     * │ R=F, A=F, U=F │ Virgin state (initial)                      │
     * │ R=F, A=F, U=T │ Easy-Diet not installed (lazy check failed) │
     * │ R=T, A=F, U=F │ Init attempted, reflection failed           │
     * │ R=T, A=T, U=F │ Init succeeded, ready to use                │
     * └─────────────────────────────────────────────────────────────┘
     * Other combinations (R=T with U=T, or A=T with R=F) are impossible.
     * </pre>
     *
     * <p><b>Why volatile?</b> Init methods are {@code synchronized}, but reader methods
     * like {@link #isReady()} and {@link #getDietValues(Player)} are NOT synchronized
     * for performance. Volatile ensures visibility of flag changes to all threads
     * without requiring readers to acquire the lock.
     */
    private static volatile boolean reflectionInitialized = false;
    private static volatile boolean reflectionAvailable = false;
    private static volatile boolean easyDietUnavailable = false;

    // Cached reflection references
    @Nullable private static Object dietDataAttachment; // DietAttachments.DIET_DATA

    // Method references
    @Nullable private static Method playerGetDataMethod;
    @Nullable private static Method dietDataCopyValuesMethod;

    // Error tracking for rate-limited logging
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static final int LOG_WARN_THRESHOLD = 1;  // Log WARN on first error
    private static final int LOG_INTERVAL = 100;      // Then log every 100 errors

    private EasyDietApi() {
        // Utility class - no instantiation
    }

    // =========================================================================
    // REFLECTION INITIALIZATION
    // =========================================================================

    /**
     * Initialize reflection references eagerly during mod loading.
     *
     * <p>This method is called from {@link EasyDietCompat#initCommon()} BEFORE
     * {@code available} is set to {@code true}. It does NOT check
     * {@code EasyDietCompat.isAvailable()} to avoid circular dependency.
     *
     * <p>Use {@link #initReflection()} for lazy initialization during gameplay.
     *
     * <p><b>FIX Q1:</b> This method can be called even if {@link #initReflection()}
     * was called earlier (and set {@code easyDietUnavailable = true}). We reset
     * that flag and properly attempt initialization.
     */
    public static synchronized void initReflectionEager() {
        if (reflectionInitialized) {
            return;
        }
        // Reset the "unavailable" flag - we're being called from EasyDietCompat
        // which means Easy-Diet IS available
        easyDietUnavailable = false;
        doReflectionInit();
        reflectionInitialized = true;
    }

    /**
     * Initialize reflection references lazily on first API call.
     *
     * <p>This method checks {@link EasyDietCompat#isAvailable()} before
     * attempting initialization, so it's safe to call from anywhere.
     *
     * <p><b>FIX Q1:</b> This method now correctly handles the case where it's called
     * BEFORE {@link #initReflectionEager()}. If Easy-Diet is not available yet,
     * we set {@code easyDietUnavailable} but NOT {@code reflectionInitialized},
     * allowing {@link #initReflectionEager()} to properly initialize later.
     */
    public static synchronized void initReflection() {
        // Already initialized successfully or failed - nothing to do
        if (reflectionInitialized) {
            return;
        }

        // We already know Easy-Diet is not installed - skip repeated checks
        if (easyDietUnavailable) {
            return;
        }

        // Check if Easy-Diet compat module has initialized
        if (!EasyDietCompat.isAvailable()) {
            // Mark as unavailable to avoid repeated checks, but DON'T set reflectionInitialized
            // This allows initReflectionEager() to properly initialize later if called
            easyDietUnavailable = true;
            LOGGER.debug("[EasyDietApi] Easy-Diet not available, skipping reflection init");
            return;
        }

        // Easy-Diet is available - proceed with initialization
        doReflectionInit();
        reflectionInitialized = true;
    }

    /**
     * Internal method that performs the actual reflection initialization.
     */
    private static void doReflectionInit() {
        try {
            // Load Easy-Diet classes
            Class<?> attachmentsClass = Class.forName("com.THproject.tharidia_easydiet.diet.DietAttachments");
            Class<?> dataClass = Class.forName("com.THproject.tharidia_easydiet.diet.DietData");

            // Get DIET_DATA attachment field
            Field dietDataField = attachmentsClass.getField("DIET_DATA");
            dietDataAttachment = dietDataField.get(null);

            // Get Player.getData method (takes AttachmentType)
            // NeoForge: Player extends Entity which has getData(AttachmentType<T>)
            Class<?> attachmentTypeClass = Class.forName("net.neoforged.neoforge.attachment.AttachmentType");
            playerGetDataMethod = Player.class.getMethod("getData", attachmentTypeClass);

            // Get DietData.copyValues() method
            dietDataCopyValuesMethod = dataClass.getMethod("copyValues");

            reflectionAvailable = true;
            LOGGER.debug("[EasyDietApi] Reflection initialized successfully");

        } catch (ClassNotFoundException e) {
            LOGGER.warn("[EasyDietApi] Easy-Diet classes not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.warn("[EasyDietApi] Easy-Diet method not found: {}", e.getMessage());
        } catch (NoSuchFieldException e) {
            LOGGER.warn("[EasyDietApi] Easy-Diet field not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("[EasyDietApi] Reflection init failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if reflection API is ready.
     */
    private static boolean isReflectionReady() {
        if (!reflectionInitialized) {
            initReflection();
        }
        return reflectionAvailable;
    }

    // =========================================================================
    // DIET DATA ACCESS
    // =========================================================================

    /**
     * Get all diet values as a float array.
     *
     * <p>Array indices correspond to diet categories:
     * <ul>
     *   <li>0 = GRAIN</li>
     *   <li>1 = PROTEIN</li>
     *   <li>2 = VEGETABLE</li>
     *   <li>3 = FRUIT</li>
     *   <li>4 = SUGAR</li>
     *   <li>5 = WATER</li>
     * </ul>
     *
     * @param player the player
     * @return copy of diet values array (0.0 to 1.0 per category), or default values if unavailable
     */
    public static float[] getDietValues(Player player) {
        if (!isReflectionReady()) {
            return DEFAULT_VALUES.clone();
        }

        try {
            Method getDataMethod = playerGetDataMethod;
            Object attachment = dietDataAttachment;
            Method copyValuesMethod = dietDataCopyValuesMethod;

            if (getDataMethod == null || attachment == null || copyValuesMethod == null) {
                return DEFAULT_VALUES.clone();
            }

            // Call player.getData(DietAttachments.DIET_DATA)
            Object dietData = getDataMethod.invoke(player, attachment);
            if (dietData == null) {
                return DEFAULT_VALUES.clone();
            }

            // Call dietData.copyValues()
            Object result = copyValuesMethod.invoke(dietData);
            if (result instanceof float[] values) {
                return values;
            }

            return DEFAULT_VALUES.clone();

        } catch (Exception e) {
            logErrorRateLimited("getDietValues", e);
            return DEFAULT_VALUES.clone();
        }
    }

    /**
     * Log an error with rate limiting to avoid log spam.
     * Logs WARN on first error, then DEBUG, with periodic WARN reminders.
     */
    private static void logErrorRateLimited(String method, Exception e) {
        int count = errorCount.incrementAndGet();

        if (count == LOG_WARN_THRESHOLD) {
            // First error - log as WARN to alert developers
            LOGGER.warn("[EasyDietApi] {} failed (first occurrence): {}",
                method, e.getMessage());
        } else if (count % LOG_INTERVAL == 0) {
            // Periodic reminder at WARN level
            LOGGER.warn("[EasyDietApi] {} has failed {} times. Latest: {}",
                method, count, e.getMessage());
        } else {
            // Regular errors at DEBUG level
            LOGGER.debug("[EasyDietApi] {} failed: {}", method, e.getMessage());
        }
    }

    // =========================================================================
    // STATUS QUERIES
    // =========================================================================

    /**
     * Check if player is well-fed (all categories above threshold).
     *
     * @param player the player
     * @return true if all diet values >= 0.6
     */
    public static boolean isWellFed(Player player) {
        float[] values = getDietValues(player);
        for (float v : values) {
            if (v < WELL_FED_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if player is malnourished (any category below threshold).
     *
     * @param player the player
     * @return true if any diet value < 0.2
     */
    public static boolean isMalnourished(Player player) {
        float[] values = getDietValues(player);
        for (float v : values) {
            if (v < MALNOURISHED_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if player is in critical state (any category dangerously low).
     *
     * @param player the player
     * @return true if any diet value < 0.1
     */
    public static boolean isCritical(Player player) {
        float[] values = getDietValues(player);
        for (float v : values) {
            if (v < CRITICAL_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate overall nutrition balance score.
     *
     * @param player the player
     * @return average of all diet values (0.0 to 1.0)
     */
    public static float getNutritionBalance(Player player) {
        float[] values = getDietValues(player);
        float sum = 0;
        for (float v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /**
     * Get the lowest diet category value.
     *
     * @param player the player
     * @return the minimum value across all categories
     */
    public static float getLowestValue(Player player) {
        float[] values = getDietValues(player);
        float min = Float.MAX_VALUE;
        for (float v : values) {
            if (v < min) {
                min = v;
            }
        }
        return min == Float.MAX_VALUE ? 0.5f : min;
    }

    /**
     * Get the index of the category with lowest value.
     *
     * @param player the player
     * @return category index (0-5) with minimum value
     */
    public static int getLowestCategoryIndex(Player player) {
        float[] values = getDietValues(player);
        int minIdx = 0;
        float min = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] < min) {
                min = values[i];
                minIdx = i;
            }
        }
        return minIdx;
    }

    /**
     * Get a diet value by category index.
     *
     * @param player the player
     * @param categoryIndex category ordinal (0-5)
     * @return value from 0.0 to 1.0, or 0.5 (neutral) if index invalid
     */
    public static float getDietValue(Player player, int categoryIndex) {
        if (categoryIndex < 0 || categoryIndex >= CATEGORY_COUNT) {
            return 0.5f;
        }
        float[] values = getDietValues(player);
        return values[categoryIndex];
    }

    // =========================================================================
    // DEBUGGING & STATUS
    // =========================================================================

    /** Status constant: reflection initialized and ready. */
    public static final String STATUS_READY = "ready";

    /** Status constant: reflection initialization failed. */
    public static final String STATUS_FAILED = "failed";

    /** Status constant: reflection not yet initialized. */
    public static final String STATUS_NOT_INITIALIZED = "not initialized";

    /** Status constant: Easy-Diet mod not installed. */
    public static final String STATUS_UNAVAILABLE = "unavailable";

    /**
     * Check if reflection API is ready for use.
     *
     * <p><b>Package-private by design:</b> External code should use
     * {@link EasyDietCompat#isAvailable()} instead. This method is only
     * for internal use by {@link EasyDietCompat} after calling
     * {@link #initReflectionEager()}.
     *
     * <p><b>Note:</b> This method does NOT trigger initialization.
     * It only checks current flag state.
     *
     * @return true if reflection is initialized and available
     */
    static boolean isReady() {
        return reflectionInitialized && reflectionAvailable;
    }

    /**
     * Get reflection status for debugging.
     *
     * @return status string (one of STATUS_* constants)
     */
    public static String getReflectionStatus() {
        if (easyDietUnavailable && !reflectionInitialized) {
            return STATUS_UNAVAILABLE;
        }
        if (!reflectionInitialized) {
            return STATUS_NOT_INITIALIZED;
        }
        if (!reflectionAvailable) {
            return STATUS_FAILED;
        }
        return STATUS_READY;
    }
}
