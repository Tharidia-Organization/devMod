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
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DebugDomainRegistrar: overlays, heatmaps, visualizers,
 * HUD toggles, economy, and context debug.
 */
@DisplayName("DebugDomainRegistrar")
class DebugDomainRegistrarTest {

    private DebugDomainRegistrar registrar;
    private List<ActionSpec> specs;
    private Map<String, ActionSpec> specMap;

    @BeforeEach
    void setUp() {
        registrar = new DebugDomainRegistrar();
        specs = registrar.getActionSpecs();
        specMap = specs.stream().collect(Collectors.toMap(ActionSpec::id, s -> s));
    }

    @Test
    @DisplayName("Domain name is 'debug'")
    void domainName() {
        assertEquals("debug", registrar.domainName());
    }

    @Test
    @DisplayName("Has no dependencies")
    void noDependencies() {
        assertTrue(registrar.getDependencies().isEmpty());
    }

    @Test
    @DisplayName("Returns 62 action specs")
    void totalActionCount() {
        assertEquals(62, specs.size());
    }

    @Nested
    @DisplayName("Channel Validation")
    class ChannelValidation {

        @Test
        @DisplayName("All debug specs use CLIENT channel")
        void allClientChannel() {
            for (ActionSpec spec : specs) {
                assertEquals(ActionChannel.CLIENT, spec.channel(),
                    "Spec " + spec.id() + " should be CLIENT channel");
            }
        }
    }

    @Nested
    @DisplayName("Category Validation")
    class CategoryValidation {

        @Test
        @DisplayName("Debug overlay actions use DEBUG category")
        void debugOverlayActionsUseDebugCategory() {
            for (String id : List.of(
                    ActionIds.DEBUG_OVERLAY_TOGGLE, ActionIds.DEBUG_BODY_PARTS_TOGGLE,
                    ActionIds.DEBUG_OVERLAYS_ENABLE_ALL, ActionIds.DEBUG_OVERLAYS_DISABLE_ALL,
                    ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE, ActionIds.DEBUG_PATHFINDING_TOGGLE)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionCategory.DEBUG, spec.category(),
                    id + " should use DEBUG category");
            }
        }

        @Test
        @DisplayName("Heatmap actions use DEBUG category")
        void heatmapActionsUseDebugCategory() {
            for (String id : List.of(
                    ActionIds.DEBUG_HEATMAP_CYCLE, ActionIds.DEBUG_HEATMAP_TOGGLE,
                    ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE, ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE,
                    ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT, ActionIds.DEBUG_HEATMAP_CLEAR_ALL)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionCategory.DEBUG, spec.category(),
                    id + " should use DEBUG category");
            }
        }

        @Test
        @DisplayName("HUD actions use appropriate categories")
        void hudActionsUseCorrectCategories() {
            // Impact HUD uses COMBAT category
            ActionSpec impactToggle = specMap.get(ActionIds.HUD_IMPACT_TOGGLE);
            assertNotNull(impactToggle);
            assertEquals(ActionCategory.COMBAT, impactToggle.category());

            // Quest HUD uses ENDURANCE category
            ActionSpec questToggle = specMap.get(ActionIds.HUD_QUEST_TOGGLE);
            assertNotNull(questToggle);
            assertEquals(ActionCategory.ENDURANCE, questToggle.category());

            // Party HUD uses PARTY category
            ActionSpec partyToggle = specMap.get(ActionIds.HUD_PARTY_TOGGLE);
            assertNotNull(partyToggle);
            assertEquals(ActionCategory.PARTY, partyToggle.category());

            // Quick help uses TOOLS category
            ActionSpec quickHelp = specMap.get(ActionIds.HUD_QUICK_HELP_TOGGLE);
            assertNotNull(quickHelp);
            assertEquals(ActionCategory.TOOLS, quickHelp.category());
        }
    }

    @Nested
    @DisplayName("Action Types")
    class ActionTypes {

        @Test
        @DisplayName("Toggle actions use TOGGLE_SETTING type")
        void toggleActionType() {
            for (String id : List.of(
                    ActionIds.DEBUG_OVERLAY_TOGGLE, ActionIds.DEBUG_BODY_PARTS_TOGGLE,
                    ActionIds.HUD_IMPACT_TOGGLE, ActionIds.HUD_QUEST_TOGGLE)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionType.TOGGLE_SETTING, spec.actionType(),
                    id + " should use TOGGLE_SETTING type");
            }
        }
    }

    @Nested
    @DisplayName("Toggle Flags")
    class ToggleFlags {

        @Test
        @DisplayName("Toggle actions have isToggle=true in UI meta")
        void toggleActionsHaveIsToggleTrue() {
            for (String id : List.of(
                    ActionIds.DEBUG_OVERLAY_TOGGLE, ActionIds.DEBUG_BODY_PARTS_TOGGLE,
                    ActionIds.DEBUG_HEATMAP_TOGGLE, ActionIds.HUD_IMPACT_TOGGLE,
                    ActionIds.HUD_QUEST_TOGGLE, ActionIds.HUD_PARTY_TOGGLE)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertTrue(spec.uiMeta().isToggle(),
                    id + " should have isToggle=true");
            }
        }

        @Test
        @DisplayName("Non-toggle actions have isToggle=false in UI meta")
        void nonToggleActionsHaveIsToggleFalse() {
            for (String id : List.of(
                    ActionIds.DEBUG_HEATMAP_CYCLE, ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT,
                    ActionIds.DEBUG_HEATMAP_CLEAR_ALL, ActionIds.DEBUG_COMBAT_RESET)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertFalse(spec.uiMeta().isToggle(),
                    id + " should have isToggle=false");
            }
        }
    }

    @Nested
    @DisplayName("Permission Levels")
    class PermissionLevels {

        @Test
        @DisplayName("All debug actions use permission level 0")
        void allPermLevel0() {
            for (ActionSpec spec : specs) {
                assertEquals(0, spec.permissionLevel(),
                    "Spec " + spec.id() + " should require permission level 0");
            }
        }
    }

    @Nested
    @DisplayName("UI Metadata")
    class UiMetadata {

        @Test
        @DisplayName("All specs have non-empty label keys")
        void allHaveLabelKeys() {
            for (ActionSpec spec : specs) {
                assertNotNull(spec.uiMeta().labelKey());
                assertFalse(spec.uiMeta().labelKey().isEmpty(),
                    "Label key should not be empty for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-empty desc keys")
        void allHaveDescKeys() {
            for (ActionSpec spec : specs) {
                assertNotNull(spec.uiMeta().descKey());
                assertFalse(spec.uiMeta().descKey().isEmpty(),
                    "Desc key should not be empty for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have menu paths starting with Root/")
        void allHaveMenuPaths() {
            for (ActionSpec spec : specs) {
                assertNotNull(spec.uiMeta().menuPath(),
                    "Menu path should not be null for: " + spec.id());
                assertTrue(spec.uiMeta().menuPath().startsWith("Root/"),
                    "Menu path should start with 'Root/' for: " + spec.id());
            }
        }
    }

    @Nested
    @DisplayName("Specific Action Spot-Checks")
    class SpecificActions {

        @Test
        @DisplayName("Heatmap cycle action exists")
        void heatmapCycle() {
            ActionSpec spec = specMap.get(ActionIds.DEBUG_HEATMAP_CYCLE);
            assertNotNull(spec);
            assertEquals(ActionCategory.DEBUG, spec.category());
            assertEquals(ActionChannel.CLIENT, spec.channel());
        }

        @Test
        @DisplayName("Impact HUD dismiss exists")
        void impactDismiss() {
            ActionSpec spec = specMap.get(ActionIds.DEBUG_IMPACT_DISMISS);
            assertNotNull(spec);
        }

        @Test
        @DisplayName("Economy overlay actions exist")
        void economyActions() {
            assertNotNull(specMap.get(ActionIds.DEBUG_ECONOMY_TOGGLE));
            assertNotNull(specMap.get(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE));
            assertNotNull(specMap.get(ActionIds.DEBUG_ECONOMY_SORT_CYCLE));
        }

        @Test
        @DisplayName("Native debug toggles exist")
        void nativeDebugToggles() {
            for (String id : List.of(
                    ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE,
                    ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE,
                    ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE,
                    ActionIds.DEBUG_NATIVE_POI_TOGGLE,
                    ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE,
                    ActionIds.DEBUG_NATIVE_BEES_TOGGLE,
                    ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE,
                    ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE)) {
                assertNotNull(specMap.get(id), "Spec for " + id + " should exist");
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
