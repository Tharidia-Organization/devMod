package com.devmod.network.handlers;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * P2: Interface for domain-specific payload registration.
 *
 * <p>Implementations register their payloads with the event
 * and handle their own payload processing logic.
 */
public interface PayloadRegistrar {

    /**
     * Register all payloads for this domain.
     *
     * @param event The registration event
     */
    void registerPayloads(RegisterPayloadHandlersEvent event);

    /**
     * Get the domain name for logging purposes.
     */
    String getDomainName();
}
