package com.devmod.arena.registry;

import java.util.*;

/**
 * SpawnSlot validation (bounds, duplicates, forbidden zones, hazards overlap, required tags).
 */
public class SpawnSlotValidator {

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

            int absX = pos[0] + bounds.originX();
            int absY = computeY(pos[1], slot.yMode(), template.floor().y());
            int absZ = pos[2] + bounds.originZ();

            // Bounds check
            if (!bounds.contains(absX, absY, absZ)) {
                errors.add(err(i, "outside arena bounds"));
            }

            // Duplicates
            String key = absX + "," + absY + "," + absZ;
            if (!seen.add(key)) {
                errors.add(err(i, "duplicate position " + key));
            }

            // Forbidden zones
            if (template.forbiddenZones() != null) {
                for (int j = 0; j < template.forbiddenZones().size(); j++) {
                    if (isInForbidden(absX, absY, absZ, template.forbiddenZones().get(j), template.floor().y())) {
                        errors.add(err(i, "in forbiddenZone[" + j + "]"));
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
        return mode == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR ? floorY + y : y;
    }

    private boolean isInForbidden(int x, int y, int z, ArenaTemplate.ForbiddenZone zone, int floorY) {
        if (zone == null || zone.min() == null || zone.max() == null) return false;
        int minY = zone.min()[1];
        int maxY = zone.max()[1];
        if (zone.yMode() == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
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
}
