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
 * Tests for UiDomainRegistrar: screens, editors, hubs, testing,
 * QA, party, notifications, endurance UI, and onboarding.
 */
@DisplayName("UiDomainRegistrar")
class UiDomainRegistrarTest {

    private UiDomainRegistrar registrar;
    private List<ActionSpec> specs;
    private Map<String, ActionSpec> specMap;

    @BeforeEach
    void setUp() {
        registrar = new UiDomainRegistrar();
        specs = registrar.getActionSpecs();
        specMap = specs.stream().collect(Collectors.toMap(ActionSpec::id, s -> s));
    }

    @Test
    @DisplayName("Domain name is 'ui'")
    void domainName() {
        assertEquals("ui", registrar.domainName());
    }

    @Test
    @DisplayName("Has no dependencies")
    void noDependencies() {
        assertTrue(registrar.getDependencies().isEmpty());
    }

    @Test
    @DisplayName("Returns 65 action specs")
    void totalActionCount() {
        assertEquals(65, specs.size());
    }

    @Nested
    @DisplayName("Channel Validation")
    class ChannelValidation {

        @Test
        @DisplayName("All UI specs use CLIENT channel")
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
        @DisplayName("Core UI actions use UI category")
        void coreUiActionsUseUiCategory() {
            for (String id : List.of(
                    ActionIds.UI_RADIAL_OPEN, ActionIds.UI_WELCOME_OPEN,
                    ActionIds.UI_ONBOARDING_START, ActionIds.UI_ONBOARDING_SKIP)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionCategory.UI, spec.category(),
                    id + " should use UI category");
            }
        }

        @Test
        @DisplayName("Tool/editor actions use TOOLS category")
        void toolActionsUseToolsCategory() {
            for (String id : List.of(
                    ActionIds.UI_ITEM_EDITOR_OPEN_AUTO, ActionIds.UI_EDITOR_HUB_OPEN,
                    ActionIds.UI_NEXUS_HUB_OPEN)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionCategory.TOOLS, spec.category(),
                    id + " should use TOOLS category");
            }
        }

        @Test
        @DisplayName("Arena hub uses ARENA category")
        void arenaHubUsesArenaCategory() {
            ActionSpec spec = specMap.get(ActionIds.UI_ARENA_HUB_OPEN);
            assertNotNull(spec);
            assertEquals(ActionCategory.ARENA, spec.category());
        }

        @Test
        @DisplayName("Testing-related actions use TESTING category")
        void testingActionsUseTestingCategory() {
            for (String id : List.of(
                    ActionIds.UI_TESTING_HUB_OPEN, ActionIds.UI_QUICK_TEST_WIZARD_OPEN,
                    ActionIds.UI_QA_TESTING_OPEN, ActionIds.UI_RESPONSIVENESS_TEST_OPEN,
                    ActionIds.UI_BADGE_TESTS_OPEN, ActionIds.UI_VOXELLAB_UI_TESTS_OPEN)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionCategory.TESTING, spec.category(),
                    id + " should use TESTING category");
            }
        }

        @Test
        @DisplayName("Party actions use PARTY category")
        void partyActionsUsePartyCategory() {
            for (String id : List.of(
                    ActionIds.UI_PARTY_OPEN, ActionIds.UI_PARTY_INVITE_POPUP_OPEN)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionCategory.PARTY, spec.category(),
                    id + " should use PARTY category");
            }
        }

        @Test
        @DisplayName("Endurance UI actions use ENDURANCE category")
        void enduranceActionsUseEnduranceCategory() {
            for (String id : List.of(
                    ActionIds.UI_ENDURANCE_SCREEN_OPEN, ActionIds.UI_ENDURANCE_SHOP_OPEN,
                    ActionIds.UI_PERK_SELECTION_OPEN, ActionIds.UI_QUEST_COMPLETION_OPEN,
                    ActionIds.UI_QUEST_DEATH_OPEN, ActionIds.UI_WAVE_CHECKPOINT_OPEN)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionCategory.ENDURANCE, spec.category(),
                    id + " should use ENDURANCE category");
            }
        }
    }

    @Nested
    @DisplayName("Action Types")
    class ActionTypes {

        @Test
        @DisplayName("Screen-opening actions use NAVIGATE_SCREEN type")
        void navigateScreenType() {
            for (String id : List.of(
                    ActionIds.UI_SETTINGS_OPEN, ActionIds.UI_ITEM_EDITOR_OPEN_AUTO,
                    ActionIds.UI_EDITOR_HUB_OPEN, ActionIds.UI_PLAY_HUB_OPEN,
                    ActionIds.UI_PARTY_OPEN)) {
                ActionSpec spec = specMap.get(id);
                assertNotNull(spec, "Spec for " + id + " should exist");
                assertEquals(ActionType.NAVIGATE_SCREEN, spec.actionType(),
                    id + " should use NAVIGATE_SCREEN type");
            }
        }
    }

    @Nested
    @DisplayName("Permission Levels")
    class PermissionLevels {

        @Test
        @DisplayName("All UI actions use permission level 0")
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
                assertNotNull(spec.uiMeta().labelKey(),
                    "Label key should not be null for: " + spec.id());
                assertFalse(spec.uiMeta().labelKey().isEmpty(),
                    "Label key should not be empty for: " + spec.id());
            }
        }

        @Test
        @DisplayName("All specs have non-empty desc keys")
        void allHaveDescKeys() {
            for (ActionSpec spec : specs) {
                assertNotNull(spec.uiMeta().descKey(),
                    "Desc key should not be null for: " + spec.id());
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
        @DisplayName("Radial open exists with correct properties")
        void radialOpen() {
            ActionSpec spec = specMap.get(ActionIds.UI_RADIAL_OPEN);
            assertNotNull(spec);
            assertEquals(ActionCategory.UI, spec.category());
            assertEquals(ActionChannel.CLIENT, spec.channel());
            assertEquals(ActionType.NAVIGATE_SCREEN, spec.actionType());
        }

        @Test
        @DisplayName("Item editor auto exists with correct icon")
        void itemEditorAuto() {
            ActionSpec spec = specMap.get(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO);
            assertNotNull(spec);
            assertEquals("minecraft:book", spec.uiMeta().iconItemId());
        }

        @Test
        @DisplayName("Onboarding actions exist")
        void onboardingActions() {
            assertNotNull(specMap.get(ActionIds.UI_ONBOARDING_START));
            assertNotNull(specMap.get(ActionIds.UI_ONBOARDING_SKIP));
        }

        @Test
        @DisplayName("Season pass action exists")
        void seasonPassAction() {
            assertNotNull(specMap.get(ActionIds.UI_SEASON_PASS_OPEN));
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
