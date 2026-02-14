package com.devmod.actions.bindings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.devmod.actions.ActionOrigin;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BindingRegistry}: registration, retrieval by action ID,
 * retrieval by type, multiple bindings per action, and thread safety.
 */
@DisplayName("BindingRegistry Tests")
class BindingRegistryTest {

    private BindingRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BindingRegistry();
    }

    // =========================================================================
    // RadialBinding
    // =========================================================================

    @Nested
    @DisplayName("RadialBinding")
    class RadialBindingTests {

        @Test
        @DisplayName("Register and retrieve RadialBinding")
        void registerAndGetRadial() {
            RadialBinding binding = new RadialBinding(
                "devmod.ui.settings.open", "Root/Tools/Settings", "minecraft:compass");
            registry.register(binding);

            List<ActionBinding> bindings = registry.getBindings("devmod.ui.settings.open");
            assertEquals(1, bindings.size());
            assertInstanceOf(RadialBinding.class, bindings.get(0));
            assertEquals(ActionOrigin.RADIAL, bindings.get(0).origin());
        }

        @Test
        @DisplayName("RadialBinding has correct menuPath and iconItemId")
        void radialBindingFields() {
            RadialBinding binding = new RadialBinding(
                "devmod.ui.editor.open", "Root/Editors/Item", "minecraft:diamond_sword");

            assertEquals("devmod.ui.editor.open", binding.actionId());
            assertEquals("Root/Editors/Item", binding.menuPath());
            assertEquals("minecraft:diamond_sword", binding.iconItemId());
            assertEquals(ActionOrigin.RADIAL, binding.origin());
            assertTrue(binding.describe().contains("Root/Editors/Item"));
        }

        @Test
        @DisplayName("RadialBinding with null iconItemId is allowed")
        void radialNullIcon() {
            RadialBinding binding = new RadialBinding(
                "devmod.ui.test.noicon", "Root/Test", null);
            assertNull(binding.iconItemId());
        }

        @Test
        @DisplayName("RadialBinding rejects null actionId")
        void radialNullActionId() {
            assertThrows(NullPointerException.class, () ->
                new RadialBinding(null, "Root/Test", null));
        }

        @Test
        @DisplayName("RadialBinding rejects null menuPath")
        void radialNullMenuPath() {
            assertThrows(NullPointerException.class, () ->
                new RadialBinding("devmod.test.null_path", null, null));
        }
    }

    // =========================================================================
    // KeybindBinding
    // =========================================================================

    @Nested
    @DisplayName("KeybindBinding")
    class KeybindBindingTests {

        @Test
        @DisplayName("Register and retrieve KeybindBinding")
        void registerAndGetKeybind() {
            KeybindBinding binding = new KeybindBinding("devmod.ability.dash", "keyDash");
            registry.register(binding);

            List<ActionBinding> bindings = registry.getBindings("devmod.ability.dash");
            assertEquals(1, bindings.size());
            assertInstanceOf(KeybindBinding.class, bindings.get(0));
            assertEquals(ActionOrigin.KEYBIND, bindings.get(0).origin());
        }

        @Test
        @DisplayName("KeybindBinding has correct keybindId")
        void keybindFields() {
            KeybindBinding binding = new KeybindBinding("devmod.ability.dodge", "keyDodge");

            assertEquals("devmod.ability.dodge", binding.actionId());
            assertEquals("keyDodge", binding.keybindId());
            assertEquals(ActionOrigin.KEYBIND, binding.origin());
            assertTrue(binding.describe().contains("keyDodge"));
        }

        @Test
        @DisplayName("KeybindBinding rejects null actionId")
        void keybindNullActionId() {
            assertThrows(NullPointerException.class, () ->
                new KeybindBinding(null, "keyTest"));
        }

        @Test
        @DisplayName("KeybindBinding rejects null keybindId")
        void keybindNullKeybindId() {
            assertThrows(NullPointerException.class, () ->
                new KeybindBinding("devmod.test.null_key", null));
        }
    }

    // =========================================================================
    // CommandBinding
    // =========================================================================

    @Nested
    @DisplayName("CommandBinding")
    class CommandBindingTests {

        @Test
        @DisplayName("Register and retrieve CommandBinding")
        void registerAndGetCommand() {
            CommandBinding binding = new CommandBinding(
                "devmod.command.gamemode_creative", "gamemode creative");
            registry.register(binding);

            List<ActionBinding> bindings = registry.getBindings("devmod.command.gamemode_creative");
            assertEquals(1, bindings.size());
            assertInstanceOf(CommandBinding.class, bindings.get(0));
            assertEquals(ActionOrigin.COMMAND, bindings.get(0).origin());
        }

        @Test
        @DisplayName("CommandBinding has correct commandPath")
        void commandFields() {
            CommandBinding binding = new CommandBinding(
                "devmod.command.time_day", "time set day");

            assertEquals("devmod.command.time_day", binding.actionId());
            assertEquals("time set day", binding.commandPath());
            assertEquals(ActionOrigin.COMMAND, binding.origin());
            assertTrue(binding.describe().contains("time set day"));
        }

        @Test
        @DisplayName("CommandBinding rejects null actionId")
        void commandNullActionId() {
            assertThrows(NullPointerException.class, () ->
                new CommandBinding(null, "say hello"));
        }

        @Test
        @DisplayName("CommandBinding rejects null commandPath")
        void commandNullCommandPath() {
            assertThrows(NullPointerException.class, () ->
                new CommandBinding("devmod.test.null_cmd", null));
        }
    }

    // =========================================================================
    // NetworkBinding
    // =========================================================================

    @Nested
    @DisplayName("NetworkBinding")
    class NetworkBindingTests {

        @Test
        @DisplayName("Register and retrieve NetworkBinding")
        void registerAndGetNetwork() {
            NetworkBinding binding = new NetworkBinding(
                "devmod.endurance.quest.start", "devmod:quest_start");
            registry.register(binding);

            List<ActionBinding> bindings = registry.getBindings("devmod.endurance.quest.start");
            assertEquals(1, bindings.size());
            assertInstanceOf(NetworkBinding.class, bindings.get(0));
            assertEquals(ActionOrigin.NETWORK, bindings.get(0).origin());
        }

        @Test
        @DisplayName("NetworkBinding has correct networkChannel")
        void networkFields() {
            NetworkBinding binding = new NetworkBinding(
                "devmod.ability.dash", "devmod:ability_dash");

            assertEquals("devmod.ability.dash", binding.actionId());
            assertEquals("devmod:ability_dash", binding.networkChannel());
            assertEquals(ActionOrigin.NETWORK, binding.origin());
            assertTrue(binding.describe().contains("devmod:ability_dash"));
        }

        @Test
        @DisplayName("NetworkBinding origin is always NETWORK (untrusted)")
        void networkOriginUntrusted() {
            NetworkBinding binding = new NetworkBinding("devmod.test.net", "channel");
            assertFalse(binding.origin().isTrusted());
        }

        @Test
        @DisplayName("NetworkBinding rejects null actionId")
        void networkNullActionId() {
            assertThrows(NullPointerException.class, () ->
                new NetworkBinding(null, "channel"));
        }

        @Test
        @DisplayName("NetworkBinding rejects null networkChannel")
        void networkNullChannel() {
            assertThrows(NullPointerException.class, () ->
                new NetworkBinding("devmod.test.null_channel", null));
        }
    }

    // =========================================================================
    // getBindingsOfType
    // =========================================================================

    @Nested
    @DisplayName("getBindingsOfType")
    class GetByTypeTests {

        @Test
        @DisplayName("Filter bindings by RadialBinding type")
        void filterByRadial() {
            registry.register(new RadialBinding("devmod.ui.a", "Root/A", null));
            registry.register(new KeybindBinding("devmod.ability.b", "keyB"));
            registry.register(new RadialBinding("devmod.ui.c", "Root/C", null));
            registry.register(new CommandBinding("devmod.cmd.d", "say hello"));

            List<RadialBinding> radials = registry.getBindingsOfType(RadialBinding.class);
            assertEquals(2, radials.size());
        }

        @Test
        @DisplayName("Filter bindings by KeybindBinding type")
        void filterByKeybind() {
            registry.register(new RadialBinding("devmod.ui.a", "Root/A", null));
            registry.register(new KeybindBinding("devmod.ability.b", "keyB"));
            registry.register(new KeybindBinding("devmod.ability.c", "keyC"));

            List<KeybindBinding> keybinds = registry.getBindingsOfType(KeybindBinding.class);
            assertEquals(2, keybinds.size());
        }

        @Test
        @DisplayName("Filter returns empty list when no bindings of type")
        void filterReturnsEmpty() {
            registry.register(new RadialBinding("devmod.ui.a", "Root/A", null));

            List<NetworkBinding> networks = registry.getBindingsOfType(NetworkBinding.class);
            assertTrue(networks.isEmpty());
        }
    }

    // =========================================================================
    // getBindings by Action ID
    // =========================================================================

    @Nested
    @DisplayName("getBindings by Action ID")
    class GetByActionIdTests {

        @Test
        @DisplayName("Action with no bindings returns empty list")
        void noneReturnsEmpty() {
            List<ActionBinding> bindings = registry.getBindings("devmod.nonexistent.action");
            assertNotNull(bindings);
            assertTrue(bindings.isEmpty());
        }

        @Test
        @DisplayName("Action with multiple bindings returns all")
        void multipleBindings() {
            registry.register(new RadialBinding("devmod.ability.dash", "Root/Combat/Dash", null));
            registry.register(new KeybindBinding("devmod.ability.dash", "keyDash"));
            registry.register(new NetworkBinding("devmod.ability.dash", "devmod:dash"));

            List<ActionBinding> bindings = registry.getBindings("devmod.ability.dash");
            assertEquals(3, bindings.size());
        }

        @Test
        @DisplayName("Different actions have independent binding lists")
        void independentBindings() {
            registry.register(new RadialBinding("devmod.ui.a", "Root/A", null));
            registry.register(new RadialBinding("devmod.ui.b", "Root/B", null));

            assertEquals(1, registry.getBindings("devmod.ui.a").size());
            assertEquals(1, registry.getBindings("devmod.ui.b").size());
        }
    }

    // =========================================================================
    // Size
    // =========================================================================

    @Nested
    @DisplayName("Size Tracking")
    class SizeTests {

        @Test
        @DisplayName("Size reflects total bindings across all actions")
        void sizeReflectsTotal() {
            assertEquals(0, registry.size());

            registry.register(new RadialBinding("devmod.ui.a", "Root/A", null));
            assertEquals(1, registry.size());

            registry.register(new KeybindBinding("devmod.ui.a", "keyA"));
            assertEquals(2, registry.size());

            registry.register(new RadialBinding("devmod.ui.b", "Root/B", null));
            assertEquals(3, registry.size());
        }
    }

    // =========================================================================
    // Thread Safety
    // =========================================================================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @Timeout(5)
        @DisplayName("Concurrent register and getBindings do not throw")
        void concurrentRegisterAndGet() throws Exception {
            int threadCount = 50;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                        if (idx % 3 == 0) {
                            registry.register(new RadialBinding(
                                "devmod.thread.action_" + idx, "Root/Thread/" + idx, null));
                        } else if (idx % 3 == 1) {
                            registry.register(new KeybindBinding(
                                "devmod.thread.action_" + idx, "key" + idx));
                        } else {
                            registry.getBindings("devmod.thread.action_" + (idx - 1));
                            registry.getBindingsOfType(RadialBinding.class);
                        }
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    return null;
                }));
            }

            latch.countDown();
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertTrue(errors.isEmpty(),
                "No exceptions during concurrent binding registry operations: " + errors);
            assertTrue(registry.size() > 0);
        }

        @Test
        @Timeout(5)
        @DisplayName("Concurrent register from many threads, size is consistent")
        void concurrentRegisterConsistentSize() throws Exception {
            int threadCount = 100;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    registry.register(new RadialBinding(
                        "devmod.concurrent.binding_" + idx, "Root/" + idx, null));
                    return null;
                }));
            }

            latch.countDown();
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertEquals(threadCount, registry.size(),
                "All bindings should be registered");
        }
    }

    // =========================================================================
    // Null Rejection
    // =========================================================================

    @Nested
    @DisplayName("Null Rejection")
    class NullRejectionTests {

        @Test
        @DisplayName("register(null) throws NPE")
        void registerNullThrows() {
            assertThrows(NullPointerException.class, () -> registry.register(null));
        }
    }
}
