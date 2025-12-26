package com.devmod.util;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class I18n {
    private I18n() {}

    /**
     * Create a translatable component.
     */
    @Nonnull
    public static MutableComponent translate(String key) {
        return Objects.requireNonNull(Component.translatable(Objects.requireNonNull(key)));
    }

    /**
     * Create a translatable component with arguments.
     */
    @Nonnull
    public static MutableComponent translate(String key, Object... args) {
        return Objects.requireNonNull(Component.translatable(Objects.requireNonNull(key), Objects.requireNonNull(args)));
    }

    /**
     * Create an overlay status message (e.g., "Debug Overlay: ON").
     * Uses translation key: devmod.overlay.[name]
     */
    @Nonnull
    public static MutableComponent overlayStatus(String overlayKey, boolean enabled) {
        MutableComponent name = Objects.requireNonNull(Component.translatable("devmod.overlay." + overlayKey));
        MutableComponent status = enabled
            ? Objects.requireNonNull(Objects.requireNonNull(Component.translatable("devmod.overlay.on")).withStyle(s -> s.withColor(0x55FF55)))
            : Objects.requireNonNull(Objects.requireNonNull(Component.translatable("devmod.overlay.off")).withStyle(s -> s.withColor(0xFF5555)));
        return Objects.requireNonNull(name.append(Objects.requireNonNull(Component.literal(": "))).append(status));
    }

    /**
     * Create an overlay status message with count (e.g., "Safe Spots: ON (5 spots)").
     */
    @Nonnull
    public static MutableComponent overlayStatusWithCount(String overlayKey, boolean enabled, int count, String countKey) {
        MutableComponent base = overlayStatus(overlayKey, enabled);
        MutableComponent countText = Objects.requireNonNull(Component.literal(" ("));
        countText = Objects.requireNonNull(countText.append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.valueOf(count))))));
        countText = Objects.requireNonNull(countText.append(Objects.requireNonNull(Component.literal(" "))));
        countText = Objects.requireNonNull(countText.append(Objects.requireNonNull(Component.translatable("devmod.overlay." + countKey))));
        countText = Objects.requireNonNull(countText.append(Objects.requireNonNull(Component.literal(")"))));
        countText = Objects.requireNonNull(countText.withStyle(s -> s.withColor(0xAAAAAA)));
        return Objects.requireNonNull(base.append(countText));
    }

    /**
     * Create a screen title component.
     */
    @Nonnull
    public static MutableComponent screenTitle(String screenKey) {
        return Objects.requireNonNull(Component.translatable("devmod.screen." + screenKey));
    }

    /**
     * Create a screen title with a name (e.g., "Configure: Zombie").
     */
    @Nonnull
    public static MutableComponent screenTitle(String screenKey, String name) {
        return Objects.requireNonNull(Component.translatable("devmod.screen." + screenKey, name));
    }

    /**
     * Create a UI element component (button, label, etc.).
     */
    @Nonnull
    public static MutableComponent ui(String key) {
        return Objects.requireNonNull(Component.translatable("devmod.ui." + key));
    }

    /**
     * Create a UI element component with arguments.
     */
    @Nonnull
    public static MutableComponent ui(String key, Object... args) {
        return Objects.requireNonNull(Component.translatable("devmod.ui." + key, Objects.requireNonNull(args)));
    }

    /**
     * Create an error message.
     */
    @Nonnull
    public static MutableComponent error(String messageKey) {
        return Objects.requireNonNull(Objects.requireNonNull(Component.translatable("devmod." + messageKey)).withStyle(s -> s.withColor(0xFF5555)));
    }

    /**
     * Create a success message.
     */
    @Nonnull
    public static MutableComponent success(String messageKey) {
        return Objects.requireNonNull(Objects.requireNonNull(Component.translatable("devmod." + messageKey)).withStyle(s -> s.withColor(0x55FF55)));
    }

    /**
     * Create a network/server message.
     */
    @Nonnull
    public static MutableComponent network(String key) {
        return Objects.requireNonNull(Component.translatable("devmod.network." + key));
    }

    /**
     * Create a network/server message with arguments.
     */
    @Nonnull
    public static MutableComponent network(String key, Object... args) {
        return Objects.requireNonNull(Component.translatable("devmod.network." + key, Objects.requireNonNull(args)));
    }

    /**
     * Create an endurance quest message.
     */
    @Nonnull
    public static MutableComponent endurance(String key) {
        return Objects.requireNonNull(Component.translatable("devmod.endurance." + key));
    }

    /**
     * Create an endurance quest message with arguments.
     */
    @Nonnull
    public static MutableComponent endurance(String key, Object... args) {
        return Objects.requireNonNull(Component.translatable("devmod.endurance." + key, Objects.requireNonNull(args)));
    }

    /**
     * Create a literal (non-translatable) component.
     * Use this for dynamic content that cannot be localized (e.g., player names, numbers).
     */
    @Nonnull
    public static MutableComponent literal(String text) {
        return Objects.requireNonNull(Component.literal(Objects.requireNonNull(text)));
    }

    /**
     * Create an error message with additional details appended.
     * Used for validation error messages like "Error: <detail>".
     */
    @Nonnull
    public static Component errorWithDetails(String key, String details) {
        MutableComponent base = translate(key);
        MutableComponent detailPart = Objects.requireNonNull(Component.literal(": " + Objects.requireNonNull(details)));
        return Objects.requireNonNull(base.append(detailPart));
    }
}
