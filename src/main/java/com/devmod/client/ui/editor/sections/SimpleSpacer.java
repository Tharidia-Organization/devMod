package com.devmod.client.ui.editor.sections;

import com.devmod.client.ui.editor.EditorSection;
public final class SimpleSpacer implements EditorSection.SpacerSection {
    private final String id;
    private final int height;

    public SimpleSpacer(String id, int height) {
        this.id = id;
        this.height = height;
    }

    @Override
    public String getId() { return id; }

    @Override
    public int getHeight() { return height; }
}
