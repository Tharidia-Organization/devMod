package com.devmod.network;

/**
 * Interface for payloads that support protocol versioning.
 *
 * <p>Versioning allows payloads to evolve over time while maintaining
 * backwards compatibility with older clients/servers.
 *
 * <p>Version history should be documented in each implementing payload:
 * <pre>
 * // V1: Base fields (introduced)
 * // V2: Added field X
 * // V3: Added field Y, deprecated field Z
 * </pre>
 */
public interface VersionedPayload {

    /**
     * Returns the protocol version of this payload.
     * Higher versions indicate newer protocol formats.
     *
     * @return the protocol version (1+)
     */
    int protocolVersion();

    /**
     * Returns the minimum supported version for decoding.
     * Payloads with versions below this will be rejected.
     *
     * @return the minimum supported version (default: 1)
     */
    default int minSupportedVersion() {
        return 1;
    }

    /**
     * Returns true if this payload version is supported.
     *
     * @param version the version to check
     * @return true if the version is within supported range
     */
    default boolean isVersionSupported(int version) {
        return version >= minSupportedVersion() && version <= protocolVersion();
    }
}
