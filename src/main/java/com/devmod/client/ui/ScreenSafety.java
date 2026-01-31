package com.devmod.client.ui;

import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

/**
 * Centralized safe screen opener with fallback error UI.
 */
public final class ScreenSafety {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenSafety.class);

    private ScreenSafety() {}

    public static void openSafe(String context, @Nullable Screen parent, Supplier<Screen> factory) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                Screen screen = Objects.requireNonNull(factory.get(), "screen");
                mc.setScreen(screen);
            } catch (Exception e) {
                LOGGER.error("[ScreenSafety] Failed to open screen: {}", context, e);
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                    message = e.getClass().getSimpleName();
                }
                String fullMessage = context == null || context.isBlank()
                    ? message
                    : context + ": " + message;
                mc.setScreen(new ErrorFallbackScreen(parent, fullMessage));
            }
        });
    }
}
