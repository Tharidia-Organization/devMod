package com.devmod.arena.registry;

import java.util.Set;

public class InheritanceCycleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String templateId;
    private final transient Set<String> visitedChain;

    public InheritanceCycleException(String templateId, Set<String> visitedChain) {
        super("Circular inheritance detected for template '%s'. Chain: %s".formatted(
            templateId, String.join(" -> ", visitedChain) + " -> " + templateId));
        this.templateId = templateId;
        this.visitedChain = visitedChain;
    }

    public String getTemplateId() {
        return templateId;
    }

    public Set<String> getVisitedChain() {
        return visitedChain;
    }
}
