package com.devmod.actions.domains;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ArenaDomainRegistrar: arena template management,
 * autosmoke, HUD controls, and force controls.
 */
@DisplayName("ArenaDomainRegistrar")
class ArenaDomainRegistrarTest {

    private ArenaDomainRegistrar registrar;
    private List<ActionSpec> specs;
    private Map<String, ActionSpec> specMap;

    @BeforeEach
    void setUp() {
        registrar = new ArenaDomainRegistrar();
        specs = registrar.getActionSpecs();
        specMap = specs.stream().collect(Collectors.toMap(ActionSpec::id, s -> s));
    }

    @Test
    @DisplayName("Domain name is 'arena'")
    void domainName() {
        assertEquals("arena", registrar.domainName());
    }

    @Test
    @DisplayName("Has no dependencies")
    void noDependencies() {
        assertTrue(registrar.getDependencies().isEmpty());
    }

    @Test
    @DisplayName("Returns 19 action specs (14 server + 5 client)")
    void totalActionCount() {
        assertEquals(19, specs.size());
    }

    @Nested
    @DisplayName("Category Validation")
    class CategoryValidation {

        @Test
        @DisplayName("All arena specs use ARENA category")
        void allArenaCategory() {
            for (ActionSpec spec : specs) {
                assertEquals(ActionCategory.ARENA, spec.category(),
                    "Spec " + spec.id() + " should be ARENA category");
            }
        }
    }

    @Nested
    @DisplayName("Channel Validation")
    class ChannelValidation {

        @Test
        @DisplayName("Server-side actions use SERVER channel")
        void serverActionsUseServerChannel() {
            for (String id : List.of(
                    ActionIds.ARENA_HELP, ActionIds.ARENA_TEMPLATE_LIST,
                    ActionIds.ARENA_TEMPLATE_RELOAD, ActionIds.ARENA_TEMPLATE_STATUS,
                    ActionIds.ARENA_STATUS, ActionIds.ARENA_HUD_TOGGLE)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionChannel.SERVER, spec.channel(),
                    "Server action " + id + " should use SERVER channel");
            }
        }

        @Test
        @DisplayName("Client-side prompt actions use BOTH channel")
        void clientActionsUseBothChannel() {
            for (String id : List.of(
                    ActionIds.ARENA_CREATE, ActionIds.ARENA_TEMPLATE_INFO,
                    ActionIds.ARENA_VALIDATE, ActionIds.ARENA_FORCE,
                    ActionIds.ARENA_METRICS)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionChannel.BOTH, spec.channel(),
                    "Client action " + id + " should use BOTH channel");
            }
        }
    }

    @Nested
    @DisplayName("Permission Levels")
    class PermissionLevels {

        @Test
        @DisplayName("All arena actions require permission level 2")
        void allPermLevel2() {
            for (ActionSpec spec : specs) {
                assertEquals(2, spec.permissionLevel(),
                    "Spec " + spec.id() + " should require permission level 2");
            }
        }
    }

    @Nested
    @DisplayName("Command Hints")
    class CommandHints {

        @Test
        @DisplayName("All arena specs have command hints")
        void allHaveCommandHints() {
            for (ActionSpec spec : specs) {
                assertNotNull(spec.bindingMeta().commandHint(),
                    "Spec " + spec.id() + " should have a command hint");
                assertFalse(spec.bindingMeta().commandHint().isEmpty(),
                    "Spec " + spec.id() + " command hint should not be empty");
            }
        }
    }

    @Nested
    @DisplayName("Handler Registration")
    class HandlerRegistrationTests {

        @Test
        @DisplayName("Registers handlers for all specs")
        void registersAllHandlers() {
            HandlerRegistry registry = new HandlerRegistry();
            registrar.registerHandlers(registry);

            for (ActionSpec spec : specs) {
                assertTrue(registry.hasHandler(spec.id()),
                    "Handler should be registered for " + spec.id());
            }
            assertEquals(specs.size(), registry.size());
        }
    }
}
