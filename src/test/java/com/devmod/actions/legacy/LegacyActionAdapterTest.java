package com.devmod.actions.legacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LegacyActionAdapter: V1 RadialAction to V2 ActionSpec conversion.
 *
 * <p>Since RadialAction requires Minecraft runtime (ItemStack, etc.), these tests
 * focus on:
 * <ul>
 *   <li>inferChannel() - channel inference from ActionType</li>
 *   <li>inferOrigins() - origin inference with CLIENT_SAFE_ACTIONS awareness</li>
 *   <li>Source-level analysis of the adapter class structure</li>
 * </ul>
 */
@DisplayName("LegacyActionAdapter")
class LegacyActionAdapterTest {

    private static final Set<ActionOrigin> TRUSTED_ORIGINS = EnumSet.of(
        ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
        ActionOrigin.UI, ActionOrigin.EVENT);

    private static final Set<ActionOrigin> TRUSTED_PLUS_NETWORK = EnumSet.of(
        ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
        ActionOrigin.UI, ActionOrigin.EVENT, ActionOrigin.NETWORK);

    // =========================================================================
    // inferChannel tests
    // =========================================================================

    @Nested
    @DisplayName("Channel Inference")
    class ChannelInference {

        @Test
        @DisplayName("Null ActionType maps to CLIENT")
        void nullType() {
            assertEquals(ActionChannel.CLIENT, LegacyActionAdapter.inferChannel(null));
        }

        @Test
        @DisplayName("NAVIGATE_SCREEN maps to CLIENT")
        void navigateScreen() {
            assertEquals(ActionChannel.CLIENT,
                LegacyActionAdapter.inferChannel(ActionType.NAVIGATE_SCREEN));
        }

        @Test
        @DisplayName("TOGGLE_SETTING maps to CLIENT")
        void toggleSetting() {
            assertEquals(ActionChannel.CLIENT,
                LegacyActionAdapter.inferChannel(ActionType.TOGGLE_SETTING));
        }

        @Test
        @DisplayName("OPEN_EXTERNAL maps to CLIENT")
        void openExternal() {
            assertEquals(ActionChannel.CLIENT,
                LegacyActionAdapter.inferChannel(ActionType.OPEN_EXTERNAL));
        }

        @Test
        @DisplayName("SUBCATEGORY maps to CLIENT")
        void subcategory() {
            assertEquals(ActionChannel.CLIENT,
                LegacyActionAdapter.inferChannel(ActionType.SUBCATEGORY));
        }

        @Test
        @DisplayName("TRIGGER_EVENT maps to CLIENT")
        void triggerEvent() {
            assertEquals(ActionChannel.CLIENT,
                LegacyActionAdapter.inferChannel(ActionType.TRIGGER_EVENT));
        }

        @Test
        @DisplayName("RUN_SERVER_COMMAND maps to SERVER")
        void runServerCommand() {
            assertEquals(ActionChannel.SERVER,
                LegacyActionAdapter.inferChannel(ActionType.RUN_SERVER_COMMAND));
        }

        @Test
        @DisplayName("SEND_SERVER_RPC maps to BOTH")
        void sendServerRpc() {
            assertEquals(ActionChannel.BOTH,
                LegacyActionAdapter.inferChannel(ActionType.SEND_SERVER_RPC));
        }

        @Test
        @DisplayName("All ActionType values are handled (exhaustive)")
        void allTypesHandled() {
            for (ActionType type : ActionType.values()) {
                assertDoesNotThrow(() -> LegacyActionAdapter.inferChannel(type),
                    "Unhandled ActionType: " + type);
            }
        }
    }

    // =========================================================================
    // inferOrigins tests
    // =========================================================================

    @Nested
    @DisplayName("Origin Inference")
    class OriginInference {

        @Test
        @DisplayName("Non-client-safe CLIENT action gets trusted-only origins")
        void nonClientSafeClientAction() {
            Set<ActionOrigin> origins = LegacyActionAdapter.inferOrigins(
                null, ActionChannel.CLIENT, false);

            assertEquals(TRUSTED_ORIGINS, origins);
            assertFalse(origins.contains(ActionOrigin.NETWORK));
        }

        @Test
        @DisplayName("Client-safe CLIENT action still gets trusted-only (ActionSpec forbids CLIENT+NETWORK)")
        void clientSafeClientAction() {
            // ActionSpec throws if channel=CLIENT and origins contain NETWORK
            Set<ActionOrigin> origins = LegacyActionAdapter.inferOrigins(
                null, ActionChannel.CLIENT, true);

            assertEquals(TRUSTED_ORIGINS, origins);
            assertFalse(origins.contains(ActionOrigin.NETWORK));
        }

        @Test
        @DisplayName("Client-safe SERVER action gets trusted + NETWORK")
        void clientSafeServerAction() {
            Set<ActionOrigin> origins = LegacyActionAdapter.inferOrigins(
                null, ActionChannel.SERVER, true);

            assertEquals(TRUSTED_PLUS_NETWORK, origins);
            assertTrue(origins.contains(ActionOrigin.NETWORK));
        }

        @Test
        @DisplayName("Client-safe BOTH action gets trusted + NETWORK")
        void clientSafeBothAction() {
            Set<ActionOrigin> origins = LegacyActionAdapter.inferOrigins(
                null, ActionChannel.BOTH, true);

            assertEquals(TRUSTED_PLUS_NETWORK, origins);
            assertTrue(origins.contains(ActionOrigin.NETWORK));
        }

        @Test
        @DisplayName("Non-client-safe SERVER action gets trusted-only")
        void nonClientSafeServerAction() {
            Set<ActionOrigin> origins = LegacyActionAdapter.inferOrigins(
                null, ActionChannel.SERVER, false);

            assertEquals(TRUSTED_ORIGINS, origins);
            assertFalse(origins.contains(ActionOrigin.NETWORK));
        }

        @Test
        @DisplayName("Non-client-safe BOTH action gets trusted-only")
        void nonClientSafeBothAction() {
            Set<ActionOrigin> origins = LegacyActionAdapter.inferOrigins(
                null, ActionChannel.BOTH, false);

            assertEquals(TRUSTED_ORIGINS, origins);
            assertFalse(origins.contains(ActionOrigin.NETWORK));
        }
    }

    // =========================================================================
    // Source-level analysis
    // =========================================================================

    @Nested
    @DisplayName("Source-Level Structure")
    class SourceLevelStructure {

        private String readAdapterSource() throws IOException {
            // Navigate from test to src
            Path srcRoot = Path.of("src/main/java/com/devmod/actions/legacy/LegacyActionAdapter.java");
            if (!Files.exists(srcRoot)) {
                srcRoot = Path.of("").toAbsolutePath()
                    .resolve("src/main/java/com/devmod/actions/legacy/LegacyActionAdapter.java");
            }
            assertTrue(Files.exists(srcRoot), "LegacyActionAdapter.java not found at: " + srcRoot);
            return Files.readString(srcRoot);
        }

        @Test
        @DisplayName("Class is final utility with private constructor")
        void classStructure() throws IOException {
            String src = readAdapterSource();
            assertTrue(src.contains("public final class LegacyActionAdapter"));
            assertTrue(src.contains("private LegacyActionAdapter()"));
        }

        @Test
        @DisplayName("Has toSpec method that takes RadialAction")
        void hasToSpec() throws IOException {
            String src = readAdapterSource();
            assertTrue(src.contains("public static ActionSpec toSpec(RadialAction action)"));
        }

        @Test
        @DisplayName("Has convertAll method")
        void hasConvertAll() throws IOException {
            String src = readAdapterSource();
            assertTrue(src.contains("public static List<ActionSpec> convertAll()"));
        }

        @Test
        @DisplayName("Has convertAll overload for Collection")
        void hasConvertAllCollection() throws IOException {
            String src = readAdapterSource();
            assertTrue(src.contains("public static List<ActionSpec> convertAll(Collection<RadialAction> actions)"));
        }

        @Test
        @DisplayName("Uses ActionRegistry.isClientSafeAction for origin inference")
        void usesClientSafeCheck() throws IOException {
            String src = readAdapterSource();
            assertTrue(src.contains("ActionRegistry.isClientSafeAction"));
        }

        @Test
        @DisplayName("Has TAG_CLIENT_SAFE and TAG_LEGACY_ADAPTED constants")
        void hasTelemetryTags() throws IOException {
            String src = readAdapterSource();
            assertTrue(src.contains("TAG_CLIENT_SAFE"));
            assertTrue(src.contains("TAG_LEGACY_ADAPTED"));
            assertEquals("client-safe", LegacyActionAdapter.TAG_CLIENT_SAFE);
            assertEquals("legacy-adapted", LegacyActionAdapter.TAG_LEGACY_ADAPTED);
        }

        @Test
        @DisplayName("convertAll sorts results by action ID")
        void convertAllSorts() throws IOException {
            String src = readAdapterSource();
            assertTrue(src.contains("specs.sort(Comparator.comparing(ActionSpec::id))"));
        }

        @Test
        @DisplayName("convertAll handles and logs conversion failures")
        void convertAllHandlesFailures() throws IOException {
            String src = readAdapterSource();
            // Should have try-catch around toSpec calls
            Pattern tryPattern = Pattern.compile("try\\s*\\{[^}]*toSpec\\(action\\)");
            Matcher m = tryPattern.matcher(src);
            assertTrue(m.find(), "convertAll should wrap toSpec in try-catch");
            assertTrue(src.contains("catch (Exception e)"));
        }

        @Test
        @DisplayName("inferChannel handles all ActionType enum values")
        void inferChannelExhaustive() throws IOException {
            String src = readAdapterSource();
            for (ActionType type : ActionType.values()) {
                assertTrue(src.contains(type.name()),
                    "inferChannel should handle: " + type.name());
            }
        }
    }
}
