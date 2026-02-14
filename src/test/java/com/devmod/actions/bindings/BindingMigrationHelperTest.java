package com.devmod.actions.bindings;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.ArenaDomainRegistrar;
import com.devmod.actions.domains.GameplayDomainRegistrar;
import com.devmod.actions.domains.TestingDomainRegistrar;
import com.devmod.actions.domains.TelemetryDomainRegistrar;
import com.devmod.actions.domains.AdminDomainRegistrar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BindingMigrationHelper: creates bindings from ActionSpec metadata.
 */
@DisplayName("BindingMigrationHelper")
class BindingMigrationHelperTest {

    private BindingRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BindingRegistry();
    }

    @Nested
    @DisplayName("Single Spec Binding Creation")
    class SingleSpecTests {

        @Test
        @DisplayName("Creates RadialBinding from menuPath")
        void createsRadialBinding() {
            ActionSpec spec = ActionSpec.builder("devmod.test.radial.action")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc", "minecraft:compass", "Root/Test/Action", false)
                .build();

            int count = BindingMigrationHelper.createBindingsForSpec(spec, registry);

            assertEquals(1, count);
            List<ActionBinding> bindings = registry.getBindings("devmod.test.radial.action");
            assertEquals(1, bindings.size());
            assertInstanceOf(RadialBinding.class, bindings.get(0));

            RadialBinding radial = (RadialBinding) bindings.get(0);
            assertEquals("Root/Test/Action", radial.menuPath());
            assertEquals("minecraft:compass", radial.iconItemId());
            assertEquals(ActionOrigin.RADIAL, radial.origin());
        }

        @Test
        @DisplayName("Creates KeybindBinding from keybindId")
        void createsKeybindBinding() {
            ActionSpec spec = ActionSpec.builder("devmod.test.keybind.action")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.COMBAT)
                .ui("label", "desc")
                .binding("DASH_KEY", null, null)
                .build();

            int count = BindingMigrationHelper.createBindingsForSpec(spec, registry);

            assertEquals(1, count);
            List<ActionBinding> bindings = registry.getBindings("devmod.test.keybind.action");
            assertEquals(1, bindings.size());
            assertInstanceOf(KeybindBinding.class, bindings.get(0));

            KeybindBinding keybind = (KeybindBinding) bindings.get(0);
            assertEquals("DASH_KEY", keybind.keybindId());
            assertEquals(ActionOrigin.KEYBIND, keybind.origin());
        }

        @Test
        @DisplayName("Creates CommandBinding from commandHint")
        void createsCommandBinding() {
            ActionSpec spec = ActionSpec.builder("devmod.test.command.action")
                .channel(ActionChannel.SERVER)
                .category(ActionCategory.TOOLS)
                .ui("label", "desc")
                .binding(null, "arena status", null)
                .build();

            int count = BindingMigrationHelper.createBindingsForSpec(spec, registry);

            assertEquals(1, count);
            List<ActionBinding> bindings = registry.getBindings("devmod.test.command.action");
            assertEquals(1, bindings.size());
            assertInstanceOf(CommandBinding.class, bindings.get(0));

            CommandBinding command = (CommandBinding) bindings.get(0);
            assertEquals("arena status", command.commandPath());
            assertEquals(ActionOrigin.COMMAND, command.origin());
        }

        @Test
        @DisplayName("Creates NetworkBinding from networkChannel")
        void createsNetworkBinding() {
            ActionSpec spec = ActionSpec.builder("devmod.test.network.action")
                .channel(ActionChannel.BOTH)
                .category(ActionCategory.ENDURANCE)
                .allowAllOrigins()
                .ui("label", "desc")
                .binding(null, null, "devmod:quest_start")
                .build();

            int count = BindingMigrationHelper.createBindingsForSpec(spec, registry);

            assertEquals(1, count);
            List<ActionBinding> bindings = registry.getBindings("devmod.test.network.action");
            assertEquals(1, bindings.size());
            assertInstanceOf(NetworkBinding.class, bindings.get(0));

            NetworkBinding network = (NetworkBinding) bindings.get(0);
            assertEquals("devmod:quest_start", network.networkChannel());
            assertEquals(ActionOrigin.NETWORK, network.origin());
        }

        @Test
        @DisplayName("Creates multiple bindings from a fully-configured spec")
        void createsMultipleBindings() {
            ActionSpec spec = ActionSpec.builder("devmod.test.multi.action")
                .channel(ActionChannel.BOTH)
                .category(ActionCategory.COMBAT)
                .allowAllOrigins()
                .ui("label", "desc", "minecraft:sword", "Root/Combat/Action", false)
                .binding("KEY_X", "combat action", "devmod:combat")
                .build();

            int count = BindingMigrationHelper.createBindingsForSpec(spec, registry);

            assertEquals(4, count); // radial + keybind + command + network
            List<ActionBinding> bindings = registry.getBindings("devmod.test.multi.action");
            assertEquals(4, bindings.size());
        }

        @Test
        @DisplayName("Creates no bindings for spec with no metadata")
        void noBindingsForEmptySpec() {
            ActionSpec spec = ActionSpec.builder("devmod.test.empty.action")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.MISC)
                .ui("label", "desc")
                .build();

            int count = BindingMigrationHelper.createBindingsForSpec(spec, registry);

            assertEquals(0, count);
            assertTrue(registry.getBindings("devmod.test.empty.action").isEmpty());
        }
    }

    @Nested
    @DisplayName("Bulk Population")
    class BulkPopulationTests {

        @Test
        @DisplayName("Populates from gameplay domain specs")
        void populatesFromGameplay() {
            GameplayDomainRegistrar registrar = new GameplayDomainRegistrar();
            List<ActionSpec> specs = registrar.getActionSpecs();

            int count = BindingMigrationHelper.populateBindings(specs, registry);

            // All 7 gameplay specs have menuPath -> 7 radial bindings
            // Some have keybind bindings (5 have keybindId)
            assertTrue(count >= 7, "Should create at least 7 bindings. Created: " + count);
            assertTrue(registry.size() >= 7);
        }

        @Test
        @DisplayName("Populates from arena domain specs")
        void populatesFromArena() {
            ArenaDomainRegistrar registrar = new ArenaDomainRegistrar();
            List<ActionSpec> specs = registrar.getActionSpecs();

            int count = BindingMigrationHelper.populateBindings(specs, registry);

            // All 19 arena specs have menuPath + commandHint = at least 38 bindings
            assertTrue(count >= 38, "Should create at least 38 bindings. Created: " + count);
        }

        @Test
        @DisplayName("Populates from all batch-2 domains")
        void populatesFromAllDomains() {
            List<ActionSpec> allSpecs = new ArrayList<>();
            allSpecs.addAll(new GameplayDomainRegistrar().getActionSpecs());
            allSpecs.addAll(new ArenaDomainRegistrar().getActionSpecs());
            allSpecs.addAll(new TestingDomainRegistrar().getActionSpecs());
            allSpecs.addAll(new TelemetryDomainRegistrar().getActionSpecs());
            allSpecs.addAll(new AdminDomainRegistrar().getActionSpecs());

            int count = BindingMigrationHelper.populateBindings(allSpecs, registry);

            assertTrue(count >= 100, "Should create at least 100 bindings. Created: " + count);
        }
    }

    @Nested
    @DisplayName("Orphan Detection")
    class OrphanDetectionTests {

        @Test
        @DisplayName("No orphan radial specs after population")
        void noOrphanRadialSpecs() {
            GameplayDomainRegistrar registrar = new GameplayDomainRegistrar();
            List<ActionSpec> specs = registrar.getActionSpecs();

            BindingMigrationHelper.populateBindings(specs, registry);

            int orphans = BindingMigrationHelper.countOrphanedRadialSpecs(specs, registry);
            assertEquals(0, orphans, "Should have no orphaned radial specs");
        }

        @Test
        @DisplayName("Detects orphans when bindings not populated")
        void detectsOrphans() {
            GameplayDomainRegistrar registrar = new GameplayDomainRegistrar();
            List<ActionSpec> specs = registrar.getActionSpecs();

            // Do NOT populate bindings
            int orphans = BindingMigrationHelper.countOrphanedRadialSpecs(specs, registry);
            assertTrue(orphans > 0, "Should detect orphans when bindings not populated");
        }
    }

    @Nested
    @DisplayName("Binding Type Queries")
    class BindingTypeQueryTests {

        @Test
        @DisplayName("Can query radial bindings by type")
        void queryRadialBindings() {
            GameplayDomainRegistrar registrar = new GameplayDomainRegistrar();
            BindingMigrationHelper.populateBindings(registrar.getActionSpecs(), registry);

            List<RadialBinding> radials = registry.getBindingsOfType(RadialBinding.class);
            assertFalse(radials.isEmpty());
            assertEquals(7, radials.size()); // All 7 gameplay specs have menuPath
        }

        @Test
        @DisplayName("Can query keybind bindings by type")
        void queryKeybindBindings() {
            GameplayDomainRegistrar registrar = new GameplayDomainRegistrar();
            BindingMigrationHelper.populateBindings(registrar.getActionSpecs(), registry);

            List<KeybindBinding> keybinds = registry.getBindingsOfType(KeybindBinding.class);
            // dash, dodge, quest_complete, quest_continue, quest_exit = 5 keybinds
            assertEquals(5, keybinds.size());
        }
    }
}
