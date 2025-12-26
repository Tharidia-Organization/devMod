package com.devmod.actions.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.KeyMapping;

public final class ActionKeybindRegistry {
    private static final Map<String, KeybindHint> KEYBINDS = new HashMap<>();

    private ActionKeybindRegistry() {}

    public static void register(String actionId, KeyMapping keyMapping) {
        register(actionId, keyMapping, null);
    }

    public static void register(String actionId, KeyMapping keyMapping, @Nullable String modifierHint) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(keyMapping, "keyMapping");
        KEYBINDS.put(actionId, new KeybindHint(keyMapping, modifierHint));
    }

    @Nullable
    public static KeybindHint getKeybindHint(String actionId) {
        return KEYBINDS.get(actionId);
    }

    public static Collection<Map.Entry<String, KeybindHint>> entries() {
        return Collections.unmodifiableSet(KEYBINDS.entrySet());
    }

    public record KeybindHint(KeyMapping keyMapping, @Nullable String modifierHint) {}
}
