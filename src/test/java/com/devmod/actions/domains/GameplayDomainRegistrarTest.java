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
 * Tests for GameplayDomainRegistrar: abilities, quest, and endurance actions.
 */
@DisplayName("GameplayDomainRegistrar")
class GameplayDomainRegistrarTest {

    private GameplayDomainRegistrar registrar;
    private List<ActionSpec> specs;
    private Map<String, ActionSpec> specMap;

    @BeforeEach
    void setUp() {
        registrar = new GameplayDomainRegistrar();
        specs = registrar.getActionSpecs();
        specMap = specs.stream().collect(Collectors.toMap(ActionSpec::id, s -> s));
    }

    @Test
    @DisplayName("Domain name is 'gameplay'")
    void domainName() {
        assertEquals("gameplay", registrar.domainName());
    }

    @Test
    @DisplayName("Has no dependencies")
    void noDependencies() {
        assertTrue(registrar.getDependencies().isEmpty());
    }

    @Test
    @DisplayName("Returns 7 action specs")
    void totalActionCount() {
        assertEquals(7, specs.size());
    }

    @Nested
    @DisplayName("Ability Actions")
    class AbilityActions {

        @Test
        @DisplayName("Dash action spec is correct")
        void dashSpec() {
            ActionSpec spec = specMap.get(ActionIds.ABILITY_DASH);
            assertNotNull(spec, "ABILITY_DASH spec should exist");
            assertEquals(ActionChannel.CLIENT, spec.channel());
            assertEquals(ActionCategory.COMBAT, spec.category());
            assertEquals("devmod.action.ability.dash", spec.uiMeta().labelKey());
            assertEquals("Root/Combat/Abilities/Dash", spec.uiMeta().menuPath());
            assertEquals("DASH_KEY", spec.bindingMeta().keybindId());
            assertEquals(0, spec.permissionLevel());
        }

        @Test
        @DisplayName("Dodge action spec is correct")
        void dodgeSpec() {
            ActionSpec spec = specMap.get(ActionIds.ABILITY_DODGE);
            assertNotNull(spec, "ABILITY_DODGE spec should exist");
            assertEquals(ActionChannel.CLIENT, spec.channel());
            assertEquals(ActionCategory.COMBAT, spec.category());
            assertEquals("devmod.action.ability.dodge", spec.uiMeta().labelKey());
            assertEquals("Root/Combat/Abilities/Dodge", spec.uiMeta().menuPath());
            assertEquals("DODGE_KEY", spec.bindingMeta().keybindId());
        }
    }

    @Nested
    @DisplayName("Quest Actions")
    class QuestActions {

        @Test
        @DisplayName("Quest task complete spec is correct")
        void questTaskCompleteSpec() {
            ActionSpec spec = specMap.get(ActionIds.QUEST_TASK_COMPLETE);
            assertNotNull(spec, "QUEST_TASK_COMPLETE spec should exist");
            assertEquals(ActionChannel.CLIENT, spec.channel());
            assertEquals(ActionCategory.ENDURANCE, spec.category());
            assertEquals("QUEST_COMPLETE_TASK_KEY", spec.bindingMeta().keybindId());
        }
    }

    @Nested
    @DisplayName("Endurance Actions")
    class EnduranceActions {

        @Test
        @DisplayName("Endurance quest start spec is correct")
        void enduranceStartSpec() {
            ActionSpec spec = specMap.get(ActionIds.ENDURANCE_QUEST_START);
            assertNotNull(spec, "ENDURANCE_QUEST_START spec should exist");
            assertEquals(ActionChannel.CLIENT, spec.channel());
            assertEquals(ActionCategory.ENDURANCE, spec.category());
            assertNull(spec.bindingMeta().keybindId());
        }

        @Test
        @DisplayName("Endurance quest continue spec has keybind")
        void enduranceContinueSpec() {
            ActionSpec spec = specMap.get(ActionIds.ENDURANCE_QUEST_CONTINUE);
            assertNotNull(spec, "ENDURANCE_QUEST_CONTINUE spec should exist");
            assertEquals(ActionChannel.CLIENT, spec.channel());
            assertEquals("QUEST_CONTINUE_KEY", spec.bindingMeta().keybindId());
        }

        @Test
        @DisplayName("Endurance quest exit requires confirm")
        void enduranceExitRequiresConfirm() {
            ActionSpec spec = specMap.get(ActionIds.ENDURANCE_QUEST_EXIT);
            assertNotNull(spec, "ENDURANCE_QUEST_EXIT spec should exist");
            assertTrue(spec.policyMeta().requiresConfirm());
            assertEquals("QUEST_EXIT_KEY", spec.bindingMeta().keybindId());
        }

        @Test
        @DisplayName("Endurance settings spec uses CONFIG category")
        void enduranceSettingsSpec() {
            ActionSpec spec = specMap.get(ActionIds.UI_ENDURANCE_SETTINGS_OPEN);
            assertNotNull(spec, "UI_ENDURANCE_SETTINGS_OPEN spec should exist");
            assertEquals(ActionCategory.CONFIG, spec.category());
            assertEquals(ActionChannel.CLIENT, spec.channel());
        }
    }

    @Nested
    @DisplayName("All specs are CLIENT channel")
    class ChannelValidation {

        @Test
        @DisplayName("All gameplay specs use CLIENT channel")
        void allClientChannel() {
            for (ActionSpec spec : specs) {
                assertEquals(ActionChannel.CLIENT, spec.channel(),
                    "Spec " + spec.id() + " should be CLIENT channel");
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
