package com.devmod.arena.registry;

import java.util.List;

public class TemplateLoadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String templateId;
    private final transient List<String> errors;

    public TemplateLoadException(String templateId, List<String> errors) {
        super("Failed to load template '%s': %s".formatted(templateId, String.join("; ", errors)));
        this.templateId = templateId;
        this.errors = errors;
    }

    public TemplateLoadException(String templateId, String error) {
        this(templateId, List.of(error));
    }

    public TemplateLoadException(String templateId, String error, Throwable cause) {
        super("Failed to load template '%s': %s".formatted(templateId, error), cause);
        this.templateId = templateId;
        this.errors = List.of(error);
    }

    public String getTemplateId() {
        return templateId;
    }

    public List<String> getErrors() {
        return errors;
    }
}
