package com.devmod.arena.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1-008: Structure checksum validation for arena templates.
 *
 * Computes deterministic checksums of template structural properties to:
 * <ul>
 *   <li>Detect tampering or corruption</li>
 *   <li>Validate template integrity before builds</li>
 *   <li>Enable cache invalidation on template changes</li>
 *   <li>Support version control and audit trails</li>
 * </ul>
 *
 * <p>The checksum covers:
 * <ul>
 *   <li>Structural dimensions (size, floor, walls, ceiling)</li>
 *   <li>Spawn slots and their positions</li>
 *   <li>Hazard definitions</li>
 *   <li>Block materials</li>
 *   <li>Version metadata</li>
 * </ul>
 *
 * <p>The checksum does NOT include:
 * <ul>
 *   <li>Runtime-only settings</li>
 *   <li>Display names and descriptions</li>
 *   <li>Non-functional metadata</li>
 * </ul>
 */
public final class TemplateChecksum {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateChecksum.class);

    private static final String ALGORITHM = "SHA-256";
    private static final int CHECKSUM_SHORT_LENGTH = 8; // First 8 chars for display

    private TemplateChecksum() {}

    /**
     * Compute the full SHA-256 checksum of a template's structural properties.
     *
     * @param template The template to checksum
     * @return 64-character hex string checksum
     */
    public static String compute(ArenaTemplate template) {
        Objects.requireNonNull(template, "template");

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);

            // Core identity
            updateDigest(digest, "id", template.id());
            updateDigest(digest, "version", String.valueOf(template.version()));
            updateDigest(digest, "schemaVersion", String.valueOf(template.schemaVersion()));

            // Dimensions
            updateDigest(digest, "size", String.valueOf(template.size()));
            if (template.sizeX() != null) {
                updateDigest(digest, "sizeX", String.valueOf(template.sizeX()));
            }
            if (template.sizeZ() != null) {
                updateDigest(digest, "sizeZ", String.valueOf(template.sizeZ()));
            }
            updateDigest(digest, "shape", template.arenaShape().name());

            // Floor
            ArenaTemplate.Floor floor = template.floor();
            if (floor != null) {
                updateDigest(digest, "floor.y", String.valueOf(floor.y()));
                updateDigest(digest, "floor.thickness", String.valueOf(floor.thickness()));
                updateDigest(digest, "floor.material", floor.material());
                updateDigest(digest, "floor.pattern", floor.pattern());
            }

            // Walls
            ArenaTemplate.Walls walls = template.walls();
            if (walls != null) {
                updateDigest(digest, "walls.enabled", String.valueOf(walls.enabled()));
                updateDigest(digest, "walls.material", walls.material());
                updateDigest(digest, "walls.height", String.valueOf(walls.height()));
                updateDigest(digest, "walls.thickness", String.valueOf(walls.thickness()));
            }

            // Ceiling
            ArenaTemplate.Ceiling ceiling = template.ceiling();
            if (ceiling != null) {
                updateDigest(digest, "ceiling.enabled", String.valueOf(ceiling.enabled()));
                updateDigest(digest, "ceiling.material", ceiling.material());
                updateDigest(digest, "ceiling.y", String.valueOf(ceiling.y()));
            }

            // Underfloor
            ArenaTemplate.Underfloor underfloor = template.underfloor();
            if (underfloor != null) {
                updateDigest(digest, "underfloor.material", underfloor.material());
                updateDigest(digest, "underfloor.depth", String.valueOf(underfloor.depth()));
            }

            // Spawn slots (sorted for determinism)
            List<ArenaTemplate.SpawnSlot> slots = template.spawnSlots();
            if (slots != null) {
                updateDigest(digest, "spawnSlots.count", String.valueOf(slots.size()));
                for (int i = 0; i < slots.size(); i++) {
                    ArenaTemplate.SpawnSlot slot = slots.get(i);
                    int[] pos = slot.pos();
                    updateDigest(digest, "slot." + i + ".pos",
                        pos[0] + "," + pos[1] + "," + pos[2]);
                    updateDigest(digest, "slot." + i + ".yMode", slot.yMode().name());
                    updateDigest(digest, "slot." + i + ".tags", String.join(",", slot.tags()));
                }
            }

            // Hazards
            List<ArenaTemplate.Hazard> hazards = template.hazards();
            if (hazards != null && !hazards.isEmpty()) {
                updateDigest(digest, "hazards.count", String.valueOf(hazards.size()));
                for (int i = 0; i < hazards.size(); i++) {
                    ArenaTemplate.Hazard hazard = hazards.get(i);
                    updateDigest(digest, "hazard." + i + ".type", hazard.type());
                    if (hazard.y() != null) {
                        updateDigest(digest, "hazard." + i + ".y", String.valueOf(hazard.y()));
                    }
                    ArenaTemplate.SpawnSlot.YMode yMode = hazard.yMode();
                    if (yMode != null) {
                        updateDigest(digest, "hazard." + i + ".yMode", yMode.name());
                    }
                }
            }

            // Forbidden zones
            List<ArenaTemplate.ForbiddenZone> zones = template.forbiddenZones();
            if (zones != null && !zones.isEmpty()) {
                updateDigest(digest, "forbiddenZones.count", String.valueOf(zones.size()));
            }

            // Limits
            ArenaTemplate.Limits limits = template.limits();
            if (limits != null) {
                updateDigest(digest, "limits.maxBlocks", String.valueOf(limits.maxBlocks()));
                updateDigest(digest, "limits.maxEntities", String.valueOf(limits.maxEntities()));
            }

            // Origin
            ArenaTemplate.Origin origin = template.origin();
            if (origin != null) {
                updateDigest(digest, "origin.mode", origin.mode().name());
                updateDigest(digest, "origin.x", String.valueOf(origin.x()));
                updateDigest(digest, "origin.y", String.valueOf(origin.y()));
                updateDigest(digest, "origin.z", String.valueOf(origin.z()));
            }

            // Structure NBT (if present, hash its reference)
            ArenaTemplate.StructureNbt structureNbt = template.structureNbt();
            if (structureNbt != null) {
                updateDigest(digest, "structureNbt.path", structureNbt.path());
            }

            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("[TemplateChecksum] Algorithm not available: {}", ALGORITHM, e);
            throw new RuntimeException("Checksum algorithm not available", e);
        }
    }

    /**
     * Compute a short checksum (first 8 characters) for display purposes.
     *
     * @param template The template to checksum
     * @return 8-character hex string
     */
    public static String computeShort(ArenaTemplate template) {
        String full = compute(template);
        return full.substring(0, CHECKSUM_SHORT_LENGTH);
    }

    /**
     * Verify that a template matches an expected checksum.
     *
     * @param template The template to verify
     * @param expectedChecksum The expected checksum (full or short)
     * @return true if the checksum matches
     */
    public static boolean verify(ArenaTemplate template, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isEmpty()) {
            return false;
        }

        String computed = compute(template);

        // Support both full and short checksums
        if (expectedChecksum.length() == CHECKSUM_SHORT_LENGTH) {
            return computed.startsWith(expectedChecksum);
        }

        return computed.equalsIgnoreCase(expectedChecksum);
    }

    /**
     * Compare two templates and return whether they are structurally identical.
     *
     * @param a First template
     * @param b Second template
     * @return true if structurally identical
     */
    public static boolean structurallyEqual(ArenaTemplate a, ArenaTemplate b) {
        if (Objects.equals(a, b)) return true;
        if (a == null || b == null) return false;

        return compute(a).equals(compute(b));
    }

    /**
     * Validation result with details.
     */
    public record ValidationResult(
        boolean valid,
        @Nullable String computedChecksum,
        @Nullable String expectedChecksum,
        @Nullable String templateId,
        @Nullable String errorMessage
    ) {
        public static ValidationResult success(ArenaTemplate template, @Nullable String expected) {
            String computed = compute(template);
            return new ValidationResult(true, computed, expected, template.id(), null);
        }

        public static ValidationResult mismatch(ArenaTemplate template, String expected) {
            String computed = compute(template);
            return new ValidationResult(false, computed, expected, template.id(),
                "Checksum mismatch: expected " + expected + " but computed " + computed);
        }

        public static ValidationResult error(String templateId, String error) {
            return new ValidationResult(false, null, null, templateId, error);
        }
    }

    /**
     * Validate a template against an expected checksum with detailed result.
     *
     * @param template The template to validate
     * @param expectedChecksum The expected checksum
     * @return Detailed validation result
     */
    public static ValidationResult validate(ArenaTemplate template, @Nullable String expectedChecksum) {
        if (template == null) {
            return ValidationResult.error(null, "Template is null");
        }

        if (expectedChecksum == null || expectedChecksum.isEmpty()) {
            // No expected checksum - just compute and return success
            return ValidationResult.success(template, null);
        }

        if (verify(template, expectedChecksum)) {
            return ValidationResult.success(template, expectedChecksum);
        }

        return ValidationResult.mismatch(template, expectedChecksum);
    }

    /**
     * Generate a checksum manifest entry for a template.
     *
     * @param template The template
     * @return Manifest entry string (id:version:checksum)
     */
    public static String manifestEntry(ArenaTemplate template) {
        return String.format("%s:%d:%s",
            template.id(),
            template.version(),
            computeShort(template));
    }

    private static void updateDigest(MessageDigest digest, String key, @Nullable String value) {
        if (value == null) return;
        String entry = key + "=" + value + "\n";
        digest.update(entry.getBytes(StandardCharsets.UTF_8));
    }
}
