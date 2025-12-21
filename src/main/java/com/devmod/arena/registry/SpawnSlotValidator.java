package com.devmod.arena.registry;

import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Objects;

/**
 * SpawnSlot validation (bounds, duplicates, forbidden zones, hazards overlap, required tags).
 */
public class SpawnSlotValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpawnSlotValidator.class);
    private static final ArenaTemplate.SpawnSlot.Validation DEFAULT_VALIDATION =
        new ArenaTemplate.SpawnSlot.Validation(true, 2, 1);

    private ArenaTelemetry telemetry;

    @FunctionalInterface
    public interface BlockQuery {
        boolean isAir(net.minecraft.core.BlockPos pos);

        default boolean isSolid(net.minecraft.core.BlockPos pos) {
            return !isAir(pos);
        }
    }

    public SpawnSlotValidator() {
    }

    public SpawnSlotValidator(ArenaTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    public void setTelemetry(ArenaTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    public ValidationResult validate(ArenaTemplate template, Bounds bounds) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<ArenaTemplate.SpawnSlot> slots = template.spawnSlots() == null ? List.of() : template.spawnSlots();
        Set<String> seen = new HashSet<>();

        int playerTagCount = 0;
        int mobTagCount = 0;

        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            int[] pos = slot.pos();
            if (pos == null || pos.length != 3) {
                errors.add(err(i, "pos must have 3 elements"));
                continue;
            }

            // Apply playerSpawnOffset only to player-tagged slots
            int offsetX = 0;
            int offsetY = 0;
            int offsetZ = 0;
            if (slot.tags() != null && slot.tags().contains("player") && template.playerSpawnOffset() != null) {
                offsetX = template.playerSpawnOffset().x();
                offsetY = template.playerSpawnOffset().y();
                offsetZ = template.playerSpawnOffset().z();
            }

            int absX = pos[0] + bounds.originX() + offsetX;
            int absY = computeY(pos[1], slot.yMode(), template.floor().y()) + offsetY;
            int absZ = pos[2] + bounds.originZ() + offsetZ;
            String key = absX + "," + absY + "," + absZ;

            // Bounds check
            if (!bounds.contains(absX, absY, absZ)) {
                errors.add(err(i, "outside arena bounds"));
                emitTelemetry("arena.spawnslot.out_of_bounds", template.id(), key);
            }

            // Duplicates
            if (!seen.add(key)) {
                errors.add(err(i, "duplicate position " + key));
            }

            // Forbidden zones
            if (template.forbiddenZones() != null) {
                for (int j = 0; j < template.forbiddenZones().size(); j++) {
                    if (isInForbidden(absX, absY, absZ, template.forbiddenZones().get(j), template.floor().y())) {
                        errors.add(err(i, "in forbiddenZone[" + j + "]"));
                        emitTelemetry("arena.spawnslot.in_forbidden_zone", template.id(), key);
                    }
                }
            }

            // Hazard intersection (best-effort)
            if (template.hazards() != null) {
                for (int j = 0; j < template.hazards().size(); j++) {
                    if (isInHazard(absX, absY, absZ, template.hazards().get(j), template.floor().y())) {
                        errors.add(err(i, "in hazard[" + j + "] " + template.hazards().get(j).type()));
                        emitTelemetry("arena.spawnslot.in_hazard", template.id(), key);
                    }
                }
            }

            // Tags check
            if (slot.tags() != null) {
                if (slot.tags().contains("player")) playerTagCount++;
                if (slot.tags().contains("mob") || slot.tags().contains("boss")) mobTagCount++;
            }

            // Min distance warning (simple O(n^2))
            for (int j = i + 1; j < slots.size(); j++) {
                var other = slots.get(j);
                int[] op = other.pos();
                if (op == null || op.length != 3) continue;
                int ox = op[0] + bounds.originX();
                int oy = computeY(op[1], other.yMode(), template.floor().y());
                int oz = op[2] + bounds.originZ();
                double dx = absX - ox;
                double dy = absY - oy;
                double dz = absZ - oz;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < 2.0) {
                    warnings.add("SpawnSlot[%d] and [%d] too close (%.2f)".formatted(i, j, dist));
                }
            }
        }

        if (playerTagCount == 0) {
            errors.add("At least one spawnSlot must have tag 'player'");
        }
        if (mobTagCount == 0) {
            errors.add("At least one spawnSlot must have tag 'mob' or 'boss'");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private int computeY(int y, ArenaTemplate.SpawnSlot.YMode mode, int floorY) {
        if (mode == null || mode == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            return floorY + y;
        }
        return y;
    }

    private boolean isInForbidden(int x, int y, int z, ArenaTemplate.ForbiddenZone zone, int floorY) {
        if (zone == null || zone.min() == null || zone.max() == null) return false;
        int minY = zone.min()[1];
        int maxY = zone.max()[1];
        ArenaTemplate.SpawnSlot.YMode mode = zone.yMode() != null
            ? zone.yMode()
            : ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR;
        if (mode == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            minY += floorY;
            maxY += floorY;
        }
        return x >= zone.min()[0] && x <= zone.max()[0]
            && y >= minY && y <= maxY
            && z >= zone.min()[2] && z <= zone.max()[2];
    }

    private String err(int idx, String msg) {
        return "SpawnSlot[%d]: %s".formatted(idx, msg);
    }

    private void emitTelemetry(String event, String templateId, String posKey) {
        if (telemetry != null) {
            telemetry.emit(event, Map.of(
                "templateId", templateId,
                "position", posKey
            ));
        } else {
            LOGGER.debug("{}: templateId={}, position={}", event, templateId, posKey);
        }
    }

    private boolean isInHazard(int x, int y, int z, ArenaTemplate.Hazard hazard, int floorY) {
        if (hazard == null || hazard.params() == null) return false;
        Map<String, Object> params = hazard.params();
        switch (hazard.type()) {
            case "lava_ring", "lava_pool", "void_pit" -> {
                int[] center = listToPos(params.get("center"));
                Number rNum = (Number) params.getOrDefault("radius", 0);
                if (center == null || rNum == null) return false;
                int radius = rNum.intValue();
                int dx = x - center[0];
                int dz = z - center[2];
                return (dx * dx + dz * dz) <= radius * radius;
            }
            case "fire_zone" -> {
                int[] min = listToPos(params.get("min"));
                int[] max = listToPos(params.get("max"));
                if (min == null || max == null) return false;
                return x >= min[0] && x <= max[0]
                    && y >= min[1] && y <= max[1]
                    && z >= min[2] && z <= max[2];
            }
            case "magma_floor" -> {
                // Assume full floor coverage when enabled
                return y == floorY;
            }
            default -> {
                return false;
            }
        }
    }

    private int[] listToPos(Object v) {
        if (v instanceof List<?> list && list.size() == 3) {
            int[] arr = new int[3];
            for (int i = 0; i < 3; i++) {
                Object elem = list.get(i);
                if (elem instanceof Number n) {
                    arr[i] = n.intValue();
                } else {
                    return null;
                }
            }
            return arr;
        }
        return null;
    }

    /**
    * Runtime validation of a single spawn slot according to validation rules.
    */
    public boolean validateAtRuntime(String templateId, ArenaTemplate.SpawnSlot slot, BlockQuery query, net.minecraft.core.BlockPos absPos) {
        if (slot == null) return true;
        var rules = slot.validation() != null ? slot.validation() : DEFAULT_VALIDATION;

        // Solid below
        if (rules.requireSolidBelow()) {
            net.minecraft.core.BlockPos below = Objects.requireNonNull(absPos.below());
            if (!query.isSolid(below)) {
                emitTelemetry("arena.spawnslot.validation_failed", templateId, absPos.toShortString());
                return false;
            }
        }

        // Air above
        for (int y = 0; y < rules.requireAirAbove(); y++) {
            net.minecraft.core.BlockPos above = Objects.requireNonNull(absPos.above(y));
            if (!query.isAir(above)) {
                emitTelemetry("arena.spawnslot.validation_failed", templateId, absPos.toShortString());
                return false;
            }
        }

        // Clear radius (horizontal)
        int r = rules.requireClearRadius();
        if (r > 0) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    net.minecraft.core.BlockPos check = Objects.requireNonNull(absPos.offset(dx, 0, dz));
                    if (query.isSolid(check)) {
                        emitTelemetry("arena.spawnslot.validation_failed", templateId, absPos.toShortString());
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean validateAtRuntime(String templateId, ArenaTemplate.SpawnSlot slot, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos absPos) {
        return validateAtRuntime(templateId, slot, new BlockQuery() {
            @Override
            public boolean isAir(net.minecraft.core.BlockPos pos) {
                return level.getBlockState(Objects.requireNonNull(pos, "pos")).isAir();
            }

            @Override
            public boolean isSolid(net.minecraft.core.BlockPos pos) {
                net.minecraft.core.BlockPos safePos = Objects.requireNonNull(pos, "pos");
                return level.getBlockState(safePos).isSolidRender(level, safePos);
            }
        }, absPos);
    }
}
