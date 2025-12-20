package com.devmod.arena.registry;

/**
 * Exception thrown when inheritance chain exceeds maximum depth.
 */
public class InheritanceDepthExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String templateId;
    private final int depth;
    private final int maxDepth;

    public InheritanceDepthExceededException(String templateId, int depth, int maxDepth) {
        super("Inheritance depth exceeded for template '%s'. Depth: %d, Max: %d".formatted(
            templateId, depth, maxDepth));
        this.templateId = templateId;
        this.depth = depth;
        this.maxDepth = maxDepth;
    }

    public String getTemplateId() {
        return templateId;
    }

    public int getDepth() {
        return depth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }
}
