package com.devmod.arena.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Helper to wire structure manifest + provider into the registry.
 *
 * Usage:
 * <pre>
 *   StructureValidationInitializer.configure(
 *       registry,
 *       Path.of("config/devmod/structures_manifest.json"),
 *       getClass().getClassLoader()
 *   );
 * </pre>
 */
public final class StructureValidationInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructureValidationInitializer.class);

    private StructureValidationInitializer() {}

    public static void configure(ArenaTemplateRegistry registry,
                                 Path manifestPath,
                                 ClassLoader resourceLoader,
                                 com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        try {
            if (!Files.exists(manifestPath)) {
                LOGGER.info("Structure manifest not found at {}, skipping structure validation", manifestPath);
                return;
            }
            StructureManifestParser parser = new StructureManifestParser();
            StructureManifest manifest = parser.parse(manifestPath);
            if (configSnapshot != null) {
                // Override limits/namespaces from config snapshot if provided
                var namespaces = configSnapshot.structureAllowedNamespaces() != null && !configSnapshot.structureAllowedNamespaces().isEmpty()
                    ? Set.copyOf(configSnapshot.structureAllowedNamespaces())
                    : manifest.allowedNamespaces();
                manifest = new StructureManifest(
                    manifest.structures(),
                    namespaces,
                    configSnapshot.structureMaxFileSizeBytes() > 0 ? configSnapshot.structureMaxFileSizeBytes() : manifest.maxFileSizeBytes(),
                    configSnapshot.structureMaxBlockCount() > 0 ? configSnapshot.structureMaxBlockCount() : manifest.maxBlockCount(),
                    configSnapshot.structureMaxEntityCount() > 0 ? configSnapshot.structureMaxEntityCount() : manifest.maxEntityCount()
                );
            }

            ClasspathStructureDataProvider provider = new ClasspathStructureDataProvider(resourceLoader);
            registry.enableStructureValidation(manifest, provider);

            LOGGER.info("Structure validation enabled with manifest {} (namespaces={}, maxSize={}, maxBlocks={}, maxEntities={})",
                manifestPath,
                manifest.allowedNamespaces(),
                manifest.maxFileSizeBytes(),
                manifest.maxBlockCount(),
                manifest.maxEntityCount());
        } catch (Exception e) {
            LOGGER.error("Failed to enable structure validation from manifest {}", manifestPath, e);
        }
    }
}
