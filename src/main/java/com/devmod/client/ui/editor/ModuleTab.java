package com.devmod.client.ui.editor;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

public record ModuleTab(
    String id,
    String label,
    @Nullable ResourceLocation icon,
    Supplier<List<EditorSection>> sectionsSupplier
) {
    /**
     * Create a tab without an icon.
     */
    public static ModuleTab of(String id, String label, Supplier<List<EditorSection>> sections) {
        return new ModuleTab(id, label, null, sections);
    }

    /**
     * Create a tab with an icon.
     */
    public static ModuleTab of(String id, String label, ResourceLocation icon, Supplier<List<EditorSection>> sections) {
        return new ModuleTab(id, label, icon, sections);
    }

    /**
     * Get sections for this tab.
     */
    public List<EditorSection> getSections() {
        return sectionsSupplier.get();
    }
}
