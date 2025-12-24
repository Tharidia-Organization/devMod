package com.devmod.actions;

import net.minecraft.network.chat.Component;

import java.util.Objects;

public enum ActionCategory {
    TOOLS("devmod.action.category.tools"),
    COMBAT("devmod.action.category.combat"),
    ENDURANCE("devmod.action.category.endurance"),
    ARENA("devmod.action.category.arena"),
    TELEMETRY("devmod.action.category.telemetry"),
    DEBUG("devmod.action.category.debug"),
    ADMIN("devmod.action.category.admin"),
    CONFIG("devmod.action.category.config"),
    TESTING("devmod.action.category.testing"),
    PARTY("devmod.action.category.party"),
    UI("devmod.action.category.ui"),
    MISC("devmod.action.category.misc");

    private final String labelKey;

    ActionCategory(String labelKey) {
        this.labelKey = Objects.requireNonNull(labelKey, "labelKey");
    }

    public String getLabelKey() {
        return labelKey;
    }

    public Component getLabel() {
        return Component.translatable(Objects.requireNonNull(labelKey, "labelKey"));
    }
}
