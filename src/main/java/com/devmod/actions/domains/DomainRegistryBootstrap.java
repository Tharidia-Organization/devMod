package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.fml.loading.FMLEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionCatalogValidator;
import com.devmod.actions.catalog.ActionCatalogValidator.CatalogValidationReport;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Bootstrap orchestrator that initializes all V2 domain registrars.
 *
 * <p>Creates all {@link DomainRegistrar} implementations, registers them
 * with a {@link DomainRegistry}, populates the {@link ActionCatalog} and
 * {@link HandlerRegistry}, and validates the result via
 * {@link ActionCatalogValidator}.
 *
 * <p>This class runs PARALLEL to the existing V1 registration. It does
 * NOT replace the legacy registration yet.
 */
public final class DomainRegistryBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainRegistryBootstrap.class);

    private DomainRegistryBootstrap() {}

    /**
     * Creates, populates, validates, and returns a fully-initialized
     * {@link DomainRegistry} containing all known domain registrars.
     *
     * @return the initialized domain registry
     * @throws IllegalStateException if initialization or validation fails
     */
    public static DomainRegistry initialize() {
        return initialize(createAllRegistrars());
    }

    /**
     * Initializes a {@link DomainRegistry} from the supplied registrars.
     * Useful for testing with a subset of domains.
     *
     * @param registrars the registrars to load
     * @return the initialized domain registry
     * @throws IllegalStateException if initialization or validation fails
     */
    public static DomainRegistry initialize(List<DomainRegistrar> registrars) {
        LOGGER.info("[DomainRegistryBootstrap] Starting V2 domain initialization with {} registrars",
            registrars.size());

        DomainRegistry domainRegistry = new DomainRegistry();

        for (DomainRegistrar registrar : registrars) {
            domainRegistry.addRegistrar(registrar);
        }

        domainRegistry.initialize();

        // Populate the central ActionCatalog from the DomainRegistry's catalog
        ActionCatalog actionCatalog = new ActionCatalog();
        for (ActionSpec spec : domainRegistry.getCatalog().values()) {
            actionCatalog.register(spec);
        }

        // Validate the catalog
        CatalogValidationReport report = ActionCatalogValidator.validate(
            actionCatalog, domainRegistry.getHandlerRegistry());

        if (!report.isValid()) {
            LOGGER.error("[DomainRegistryBootstrap] Catalog validation FAILED with {} errors, {} warnings",
                report.errorCount(), report.warningCount());
            for (ActionCatalogValidator.Violation violation : report.getBySeverity(
                    ActionCatalogValidator.Severity.ERROR)) {
                LOGGER.error("  [{}] {}", violation.rule(), violation.message());
            }
            throw new IllegalStateException(
                "V2 action catalog validation failed with " + report.errorCount() + " errors");
        }

        if (report.warningCount() > 0) {
            LOGGER.warn("[DomainRegistryBootstrap] Catalog validation passed with {} warnings",
                report.warningCount());
            for (ActionCatalogValidator.Violation violation : report.getBySeverity(
                    ActionCatalogValidator.Severity.WARNING)) {
                LOGGER.warn("  [{}] {}", violation.rule(), violation.message());
            }
        }

        LOGGER.info("[DomainRegistryBootstrap] V2 domain initialization complete: {} domains, {} actions",
            domainRegistry.domainCount(), domainRegistry.actionCount());

        return domainRegistry;
    }

    /**
     * Creates all known domain registrar instances.
     *
     * <p>Batch 1 (Phases 4-5): Gameplay, Arena, Testing, Telemetry, Admin
     * <p>Batch 2 (Phase 6): UI, Debug, Config, Commands
     *
     * @return ordered list of all domain registrars
     */
    public static List<DomainRegistrar> createAllRegistrars() {
        List<DomainRegistrar> registrars = new ArrayList<>(List.of(
            // Batch 1 (server-safe)
            new GameplayDomainRegistrar(),
            new ArenaDomainRegistrar(),
            new TestingDomainRegistrar(),
            new TelemetryDomainRegistrar(),
            new AdminDomainRegistrar(),
            new CommandDomainRegistrar()
        ));

        // Batch 2: client-only registrars (import client classes, must not be loaded on dedicated server)
        // FMLEnvironment.dist is null in test JVM — include client registrars when null (safe for tests)
        if (FMLEnvironment.dist == null || FMLEnvironment.dist.isClient()) {
            registrars.add(new UiDomainRegistrar());
            registrars.add(new DebugDomainRegistrar());
            registrars.add(new ConfigDomainRegistrar());
        }

        return registrars;
    }
}
