package com.frenkvs.devmod.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Internationalization helper for DevMod.
 * Provides convenient methods to create translated components.
 */
public final class I18n {
    private I18n() {}

    /**
     * Create a translatable component.
     */
    public static MutableComponent translate(String key) {
        return Component.translatable(key);
    }

    /**
     * Create a translatable component with arguments.
     */
    public static MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * Create an overlay status message (e.g., "Debug Overlay: ON").
     * Uses translation key: devmod.overlay.[name]
     */
    public static MutableComponent overlayStatus(String overlayKey, boolean enabled) {
        MutableComponent name = Component.translatable("devmod.overlay." + overlayKey);
        MutableComponent status = enabled
            ? Component.translatable("devmod.overlay.on").withStyle(s -> s.withColor(0x55FF55))
            : Component.translatable("devmod.overlay.off").withStyle(s -> s.withColor(0xFF5555));
        return name.append(Component.literal(": ")).append(status);
    }

    /**
     * Create an overlay status message with count (e.g., "Safe Spots: ON (5 spots)").
     */
    public static MutableComponent overlayStatusWithCount(String overlayKey, boolean enabled, int count, String countKey) {
        MutableComponent base = overlayStatus(overlayKey, enabled);
        MutableComponent countText = Component.literal(" (")
            .append(Component.literal(String.valueOf(count)))
            .append(Component.literal(" "))
            .append(Component.translatable("devmod.overlay." + countKey))
            .append(Component.literal(")"))
            .withStyle(s -> s.withColor(0xAAAAAA));
        return base.append(countText);
    }

    /**
     * Create a screen title component.
     */
    public static MutableComponent screenTitle(String screenKey) {
        return Component.translatable("devmod.screen." + screenKey);
    }

    /**
     * Create a screen title with a name (e.g., "Configure: Zombie").
     */
    public static MutableComponent screenTitle(String screenKey, String name) {
        return Component.translatable("devmod.screen." + screenKey, name);
    }

    /**
     * Create a UI element component (button, label, etc.).
     */
    public static MutableComponent ui(String key) {
        return Component.translatable("devmod.ui." + key);
    }

    /**
     * Create an error message.
     */
    public static MutableComponent error(String messageKey) {
        return Component.translatable("devmod." + messageKey).withStyle(s -> s.withColor(0xFF5555));
    }

    /**
     * Create a success message.
     */
    public static MutableComponent success(String messageKey) {
        return Component.translatable("devmod." + messageKey).withStyle(s -> s.withColor(0x55FF55));
    }

    /**
     * Create a network/server message.
     */
    public static MutableComponent network(String key) {
        return Component.translatable("devmod.network." + key);
    }

    /**
     * Create a network/server message with arguments.
     */
    public static MutableComponent network(String key, Object... args) {
        return Component.translatable("devmod.network." + key, args);
    }

    /**
     * Create an endurance quest message.
     */
    public static MutableComponent endurance(String key) {
        return Component.translatable("devmod.endurance." + key);
    }

    /**
     * Create an endurance quest message with arguments.
     */
    public static MutableComponent endurance(String key, Object... args) {
        return Component.translatable("devmod.endurance." + key, args);
    }

    /**
     * Create a literal (non-translatable) component.
     * Use this for dynamic content that cannot be localized (e.g., player names, numbers).
     */
    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }
}
