package com.devmod.arena.registry;

import java.util.Set;

/**
 * Thrown when a diamond inheritance pattern is detected (duplicate ancestor).
 */
public class DiamondInheritanceException extends RuntimeException {
    public DiamondInheritanceException(String templateId, String duplicateAncestor, Set<String> path) {
        super("Diamond inheritance detected for template '%s': ancestor '%s' appears twice in chain %s"
            .formatted(templateId, duplicateAncestor, path));
    }
}
