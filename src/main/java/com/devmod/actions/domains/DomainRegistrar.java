package com.devmod.actions.domains;

import java.util.List;
import java.util.Set;

import com.devmod.actions.catalog.ActionSpec;

/**
 * Interface that each functional domain implements to declare its actions.
 * Replaces the monolithic DevModClientActions and DevModActions with focused,
 * per-domain registrars.
 *
 * <p>Each registrar:
 * <ul>
 *   <li>Declares a unique domain name (used as action ID namespace prefix)</li>
 *   <li>Provides a list of ActionSpec instances for all actions in the domain</li>
 *   <li>Registers handler functions with the HandlerRegistry</li>
 *   <li>Declares dependencies on other domains for initialization ordering</li>
 * </ul>
 */
public interface DomainRegistrar {

    /**
     * Returns the unique name of this domain (e.g., "ui", "debug", "arena").
     */
    String domainName();

    /**
     * Returns all action specifications for this domain.
     * Called during initialization to populate the action catalog.
     */
    List<ActionSpec> getActionSpecs();

    /**
     * Registers handler functions for this domain's actions.
     *
     * @param registry the handler registry to register with
     */
    void registerHandlers(HandlerRegistry registry);

    /**
     * Returns the set of domain names this domain depends on.
     * The DomainRegistry ensures dependencies are initialized first.
     *
     * @return set of domain names, or empty set if no dependencies
     */
    default Set<String> getDependencies() {
        return Set.of();
    }
}
