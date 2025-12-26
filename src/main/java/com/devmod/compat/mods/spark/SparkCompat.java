package com.devmod.compat.mods.spark;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

/**
 * Compatibility module for Spark Profiler.
 *
 * Spark provides:
 * - Performance profiling (CPU, memory)
 * - TPS and MSPT monitoring
 * - Health reports (CPU, memory, disk usage)
 * - GC statistics
 *
 * This integration allows DevMod to:
 * - Access TPS data for telemetry
 * - Monitor MSPT for performance tracking
 * - Include server health in telemetry
 * - Detect when profiler is running
 *
 * @see <a href="https://spark.lucko.me/docs/Developer-API">Spark Developer API</a>
 * @see <a href="https://github.com/lucko/spark">GitHub</a>
 */
public class SparkCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SparkCompat.class);
    public static final String MOD_ID = "spark";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> sparkClass;
    private static Class<?> statisticWindowClass;
    private static Class<?> tpsWindowClass;
    private static Class<?> msptWindowClass;

    private static Object sparkInstance;
    private static Method tpsMethod;
    private static Method msptMethod;
    private static Method pollMethod;

    // TPS window constants
    private static Object TPS_SECONDS_10;
    private static Object TPS_MINUTES_1;
    private static Object TPS_MINUTES_5;
    private static Object TPS_MINUTES_15;

    // MSPT window constants
    private static Object MSPT_SECONDS_10;
    private static Object MSPT_MINUTES_1;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Spark Profiler";
    }

    @Override
    public int priority() {
        // Lower priority - telemetry/monitoring
        return 50;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:spark] Spark not found");
            return;
        }

        available = true;

        try {
            // Try to load Spark API classes via reflection
            sparkClass = Class.forName("me.lucko.spark.api.Spark");

            // Get Spark provider
            Class<?> sparkProviderClass = Class.forName("me.lucko.spark.api.SparkProvider");
            Method getMethod = sparkProviderClass.getMethod("get");
            sparkInstance = getMethod.invoke(null);

            if (sparkInstance == null) {
                LOGGER.debug("[Compat:spark] Spark API not yet available");
                return;
            }

            // Load statistic window classes
            statisticWindowClass = Class.forName("me.lucko.spark.api.statistic.StatisticWindow");
            tpsWindowClass = Class.forName("me.lucko.spark.api.statistic.StatisticWindow$TicksPerSecond");
            msptWindowClass = Class.forName("me.lucko.spark.api.statistic.StatisticWindow$MillisPerTick");

            // Get TPS window constants
            TPS_SECONDS_10 = tpsWindowClass.getField("SECONDS_10").get(null);
            TPS_MINUTES_1 = tpsWindowClass.getField("MINUTES_1").get(null);
            TPS_MINUTES_5 = tpsWindowClass.getField("MINUTES_5").get(null);
            TPS_MINUTES_15 = tpsWindowClass.getField("MINUTES_15").get(null);

            // Get MSPT window constants
            MSPT_SECONDS_10 = msptWindowClass.getField("SECONDS_10").get(null);
            MSPT_MINUTES_1 = msptWindowClass.getField("MINUTES_1").get(null);

            // Get API methods
            tpsMethod = sparkClass.getMethod("tps");
            msptMethod = sparkClass.getMethod("mspt");

            // Get poll method from DoubleStatistic
            Class<?> doubleStatisticClass = Class.forName("me.lucko.spark.api.statistic.types.DoubleStatistic");
            pollMethod = doubleStatisticClass.getMethod("poll", statisticWindowClass);

            apiAvailable = true;
            LOGGER.info("[Compat:spark] Spark Profiler API available");
            LOGGER.debug("[Compat:spark] Version: {}", Compat.getVersion(MOD_ID));

        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Compat:spark] Spark API classes not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("[Compat:spark] Error initializing API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:spark] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "TPS/MSPT monitoring, server health data for telemetry";
    }

    /**
     * Check if Spark is loaded.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the Spark API is accessible for data queries.
     */
    public static boolean isApiAvailable() {
        return apiAvailable && sparkInstance != null;
    }

    /**
     * Get TPS for the last 10 seconds.
     *
     * @return TPS value, or -1 if not available
     */
    public static double getTps10Seconds() {
        return getTpsValue(TPS_SECONDS_10);
    }

    /**
     * Get TPS for the last 1 minute.
     *
     * @return TPS value, or -1 if not available
     */
    public static double getTps1Minute() {
        return getTpsValue(TPS_MINUTES_1);
    }

    /**
     * Get TPS for the last 5 minutes.
     *
     * @return TPS value, or -1 if not available
     */
    public static double getTps5Minutes() {
        return getTpsValue(TPS_MINUTES_5);
    }

    /**
     * Get TPS for the last 15 minutes.
     *
     * @return TPS value, or -1 if not available
     */
    public static double getTps15Minutes() {
        return getTpsValue(TPS_MINUTES_15);
    }

    /**
     * Get TPS value for a specific window.
     */
    private static double getTpsValue(Object window) {
        if (!apiAvailable || sparkInstance == null || tpsMethod == null || window == null) {
            return -1;
        }

        try {
            Object tpsStatistic = tpsMethod.invoke(sparkInstance);
            if (tpsStatistic == null) {
                return -1; // Platform doesn't have ticks (unlikely for Minecraft)
            }

            Object result = pollMethod.invoke(tpsStatistic, window);
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spark] Error getting TPS: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get MSPT (milliseconds per tick) for the last 10 seconds.
     *
     * @return MSPT value, or -1 if not available
     */
    public static double getMspt10Seconds() {
        return getMsptValue(MSPT_SECONDS_10);
    }

    /**
     * Get MSPT for the last 1 minute.
     *
     * @return MSPT value, or -1 if not available
     */
    public static double getMspt1Minute() {
        return getMsptValue(MSPT_MINUTES_1);
    }

    /**
     * Get MSPT value for a specific window.
     */
    private static double getMsptValue(Object window) {
        if (!apiAvailable || sparkInstance == null || msptMethod == null || window == null) {
            return -1;
        }

        try {
            Object msptStatistic = msptMethod.invoke(sparkInstance);
            if (msptStatistic == null) {
                return -1;
            }

            // MSPT returns a GenericStatistic with min/max/mean/median
            // We'll get the mean for now
            Class<?> genericStatClass = Class.forName("me.lucko.spark.api.statistic.types.GenericStatistic");
            Method pollMeanMethod = genericStatClass.getMethod("poll", statisticWindowClass);
            Object rollingStat = pollMeanMethod.invoke(msptStatistic, window);

            if (rollingStat != null) {
                Method meanMethod = rollingStat.getClass().getMethod("mean");
                Object mean = meanMethod.invoke(rollingStat);
                if (mean instanceof Number) {
                    return ((Number) mean).doubleValue();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spark] Error getting MSPT: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Check if TPS is healthy (>= 19).
     *
     * @return true if TPS is healthy, false otherwise
     */
    public static boolean isTpsHealthy() {
        double tps = getTps10Seconds();
        return tps >= 19.0;
    }

    /**
     * Check if TPS is critical (< 15).
     *
     * @return true if TPS is critically low
     */
    public static boolean isTpsCritical() {
        double tps = getTps10Seconds();
        return tps > 0 && tps < 15.0;
    }

    /**
     * Get a TPS status string for display.
     *
     * @return Status string like "TPS: 20.0 (Healthy)" or "TPS: N/A"
     */
    public static String getTpsStatusString() {
        if (!apiAvailable) {
            return "TPS: N/A (Spark not available)";
        }

        double tps = getTps10Seconds();
        if (tps < 0) {
            return "TPS: N/A";
        }

        String status;
        if (tps >= 19.5) {
            status = "Excellent";
        } else if (tps >= 19.0) {
            status = "Good";
        } else if (tps >= 17.0) {
            status = "Fair";
        } else if (tps >= 15.0) {
            status = "Poor";
        } else {
            status = "Critical";
        }

        return String.format("TPS: %.1f (%s)", tps, status);
    }

    /**
     * Get a performance summary string.
     *
     * @return Summary like "TPS: 19.8 | MSPT: 48.2ms"
     */
    public static String getPerformanceSummary() {
        if (!apiAvailable) {
            return "Performance data not available";
        }

        double tps = getTps10Seconds();
        double mspt = getMspt10Seconds();

        StringBuilder sb = new StringBuilder();
        if (tps >= 0) {
            sb.append(String.format("TPS: %.1f", tps));
        } else {
            sb.append("TPS: N/A");
        }

        sb.append(" | ");

        if (mspt >= 0) {
            sb.append(String.format("MSPT: %.1fms", mspt));
        } else {
            sb.append("MSPT: N/A");
        }

        return sb.toString();
    }

    /**
     * Get all TPS values as an array.
     *
     * @return Array of [10s, 1m, 5m, 15m] TPS values
     */
    public static double[] getAllTpsValues() {
        return new double[] {
            getTps10Seconds(),
            getTps1Minute(),
            getTps5Minutes(),
            getTps15Minutes()
        };
    }
}
