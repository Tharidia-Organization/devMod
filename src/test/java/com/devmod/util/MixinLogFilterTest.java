package com.devmod.util;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the shouldFilter logic of MixinLogFilter via reflection.
 */
@DisplayName("MixinLogFilter")
class MixinLogFilterTest {

    /**
     * Creates a fresh instance and invokes the private shouldFilter method.
     */
    private static boolean shouldFilter(String loggerName, String message) throws Exception {
        var constructor = MixinLogFilter.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();

        Method m = MixinLogFilter.class.getDeclaredMethod("shouldFilter", String.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(instance, loggerName, message);
    }

    // ========================================================================
    // Client-side indicator filtering
    // ========================================================================

    @Nested
    @DisplayName("client-side indicators")
    class ClientSideIndicators {

        @ParameterizedTest
        @ValueSource(strings = {
            "net.minecraft.client.Minecraft",
            "Target class net/minecraft/client/gui/screens/Screen not found",
            "Cannot apply mixin to ClientLevel",
            "LocalPlayer mixin target missing",
            "GuiGraphics renderer missing",
            "RenderSystem access failed",
            "EntityRenderer target not found",
            "KeyMapping not available"
        })
        @DisplayName("filters messages with client-side class indicators")
        void filtersClientIndicators(String message) throws Exception {
            assertTrue(shouldFilter("org.spongepowered.mixin", message));
        }

        @Test
        @DisplayName("does not filter server-side messages")
        void doesNotFilterServerSide() throws Exception {
            assertFalse(shouldFilter("org.spongepowered.mixin",
                "Applied mixin to net.minecraft.server.level.ServerLevel"));
        }
    }

    // ========================================================================
    // Mixin system detection
    // ========================================================================

    @Nested
    @DisplayName("mixin system detection")
    class MixinDetection {

        @Test
        @DisplayName("requires mixin in logger name or message")
        void requiresMixin() throws Exception {
            assertFalse(shouldFilter("net.minecraft.server",
                "net.minecraft.client.Minecraft not found"));
        }

        @Test
        @DisplayName("detects mixin in message when not in logger name")
        void detectsMixinInMessage() throws Exception {
            assertTrue(shouldFilter("some.other.logger",
                "mixin target net.minecraft.client.Minecraft not found"));
        }

        @Test
        @DisplayName("case insensitive mixin detection in logger")
        void caseInsensitiveLogger() throws Exception {
            assertTrue(shouldFilter("org.spongepowered.MIXIN",
                "net.minecraft.client.Minecraft"));
        }
    }

    // ========================================================================
    // Known mod detection
    // ========================================================================

    @Nested
    @DisplayName("known mod filtering")
    class KnownMods {

        @ParameterizedTest
        @ValueSource(strings = {
            "scholar",
            "shield_api",
            "cold_sweat",
            "create",
            "jei",
            "jade",
            "curios"
        })
        @DisplayName("filters known mods with client mixin issues")
        void filtersKnownMods(String mod) throws Exception {
            assertTrue(shouldFilter("org.spongepowered.mixin",
                mod + " target class not found"));
        }

        @Test
        @DisplayName("known mod requires target/cannot keyword")
        void knownModRequiresKeyword() throws Exception {
            // Message contains known mod but NOT "target" or "cannot"
            assertFalse(shouldFilter("org.spongepowered.mixin",
                "scholar applied successfully"));
        }
    }

    // ========================================================================
    // Regex pattern matching
    // ========================================================================

    @Nested
    @DisplayName("regex pattern matching")
    class RegexPatternTests {

        @Test
        @DisplayName("matches Target class with client keyword")
        void matchesTargetClassClient() throws Exception {
            assertTrue(shouldFilter("org.spongepowered.mixin",
                "Target class net.minecraft.client.render.FooBar not found"));
        }

        @Test
        @DisplayName("matches Mixin target with Screen")
        void matchesMixinTargetScreen() throws Exception {
            assertTrue(shouldFilter("org.spongepowered.mixin",
                "Mixin target Screen class missing"));
        }

        @Test
        @DisplayName("matches Cannot find target with GUI")
        void matchesCannotFindGui() throws Exception {
            assertTrue(shouldFilter("org.spongepowered.mixin",
                "Cannot find target class for Gui overlay"));
        }
    }

    // ========================================================================
    // Null safety
    // ========================================================================

    @Nested
    @DisplayName("null safety")
    class NullSafety {

        @Test
        @DisplayName("null loggerName returns false")
        void nullLoggerName() throws Exception {
            assertFalse(shouldFilter(null, "net.minecraft.client.Minecraft"));
        }

        @Test
        @DisplayName("null message returns false")
        void nullMessage() throws Exception {
            assertFalse(shouldFilter("org.spongepowered.mixin", null));
        }

        @Test
        @DisplayName("both null returns false")
        void bothNull() throws Exception {
            assertFalse(shouldFilter(null, null));
        }
    }
}
