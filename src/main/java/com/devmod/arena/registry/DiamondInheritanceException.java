package com.devmod.arena.registry;

import java.util.Set;

public class DiamondInheritanceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DiamondInheritanceException(String templateId, String duplicateAncestor, Set<String> path) {
        super("Diamond inheritance detected for template '%s': ancestor '%s' appears twice in chain %s"
            .formatted(templateId, duplicateAncestor, path));
    }
}
