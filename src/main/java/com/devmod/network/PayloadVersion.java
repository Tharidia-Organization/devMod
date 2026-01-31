package com.devmod.network;

import net.minecraft.network.FriendlyByteBuf;
/**
 * Utilities for reading and writing payload protocol versions.
 *
 * <p>Usage in encode/decode methods:
 * <pre>
 * private static void encode(RegistryFriendlyByteBuf buf, MyPayload payload) {
 *     PayloadVersion.writeVersion(buf, CURRENT_VERSION);
 *     // ... encode fields
 * }
 *
 * private static MyPayload decode(RegistryFriendlyByteBuf buf) {
 *     int version = PayloadVersion.readVersion(buf, MIN_VERSION, CURRENT_VERSION);
 *     // ... decode fields based on version
 *     if (version >= 2) {
 *         // decode V2+ fields
 *     }
 * }
 * </pre>
 */
public final class PayloadVersion {

    private PayloadVersion() {
    }

    /**
     * Writes a protocol version to the buffer.
     *
     * @param buf the buffer to write to
     * @param version the version to write (must be positive)
     * @throws IllegalArgumentException if version is not positive
     */
    public static void writeVersion(FriendlyByteBuf buf, int version) {
        if (version <= 0) {
            throw new IllegalArgumentException("Version must be positive: " + version);
        }
        buf.writeVarInt(version);
    }

    /**
     * Reads a protocol version from the buffer.
     *
     * @param buf the buffer to read from
     * @param minSupported minimum supported version (throws if below)
     * @param maxSupported maximum supported version (clamps to this if above)
     * @return the version, clamped to maxSupported if higher
     * @throws IllegalStateException if version is below minSupported
     */
    public static int readVersion(FriendlyByteBuf buf, int minSupported, int maxSupported) {
        int version = buf.readVarInt();

        if (version < minSupported) {
            throw new IllegalStateException(
                "Unsupported payload version: " + version + " (minimum: " + minSupported + ")");
        }

        // Clamp to max - allows newer clients to send newer versions
        // that older servers can still partially decode
        return Math.min(version, maxSupported);
    }

    /**
     * Reads a protocol version, returning the raw value without clamping.
     * Use this when you want to detect unknown versions.
     *
     * @param buf the buffer to read from
     * @param minSupported minimum supported version (throws if below)
     * @return the raw version from the buffer
     * @throws IllegalStateException if version is below minSupported
     */
    public static int readVersionStrict(FriendlyByteBuf buf, int minSupported) {
        int version = buf.readVarInt();

        if (version < minSupported) {
            throw new IllegalStateException(
                "Unsupported payload version: " + version + " (minimum: " + minSupported + ")");
        }

        return version;
    }

    /**
     * Reads a protocol version with default minimum of 1.
     *
     * @param buf the buffer to read from
     * @param maxSupported maximum supported version (clamps to this if above)
     * @return the version, clamped to maxSupported if higher
     */
    public static int readVersion(FriendlyByteBuf buf, int maxSupported) {
        return readVersion(buf, 1, maxSupported);
    }

    /**
     * Checks if a version supports a feature introduced in a specific version.
     *
     * @param currentVersion the payload's current version
     * @param featureVersion the version where the feature was introduced
     * @return true if the feature is supported
     */
    public static boolean supportsFeature(int currentVersion, int featureVersion) {
        return currentVersion >= featureVersion;
    }
}
