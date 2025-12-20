package com.devmod.arena.registry;

/**
 * Exception thrown when a parent template cannot be found during inheritance resolution.
 */
public class ParentTemplateNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String templateId;
    private final String parentId;

    public ParentTemplateNotFoundException(String templateId, String parentId) {
        super("Parent template '%s' not found for template '%s'".formatted(parentId, templateId));
        this.templateId = templateId;
        this.parentId = parentId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getParentId() {
        return parentId;
    }
}
