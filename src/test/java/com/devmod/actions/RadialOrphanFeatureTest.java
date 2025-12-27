package com.devmod.actions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.client.KeyMapping;

import com.devmod.TestBootstrap;
import com.devmod.actions.client.ActionKeybindRegistry;
import com.devmod.actions.client.DevModClientActions;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.ui.radial.RadialCategory;
import com.devmod.client.ui.radial.RadialMenuItem;
import com.devmod.client.ui.radial.RadialMenuRegistry;
import com.devmod.client.ui.radial.model.MacroCategory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialOrphanFeatureTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        TestBootstrap.init();
    }

    @Test
    void allActionIdsHaveRegistrations() throws IllegalAccessException {
        DevModActions.registerCommon();
        DevModClientActions.register();

        for (Field field : ActionIds.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String id = (String) field.get(null);
            assertNotNull(ActionRegistry.getAction(id), "Missing action registration for " + field.getName());
        }
    }

    @Test
    void everyKeybindMapsToAnAction() throws IllegalAccessException {
        DevModClientActions.register();

        Set<KeyMapping> mappedKeybinds = ActionKeybindRegistry.INSTANCE.getAllEntries().stream()
            .map(ActionKeybindRegistry.KeybindEntry::keyMapping)
            .collect(Collectors.toSet());

        for (Field field : KeyInputHandler.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != KeyMapping.class) {
                continue;
            }
            KeyMapping keyMapping = (KeyMapping) field.get(null);
            assertTrue(mappedKeybinds.contains(keyMapping),
                "Keybind is missing ActionKeybindRegistry mapping: " + field.getName());
        }
    }

    @Test
    void invokeSafeActionsDoesNotCrash() {
        DevModActions.registerCommon();

        ActionContext context = ActionContext.builder(ActionOrigin.RADIAL)
            .clientSide(true)
            .build();

        ActionRegistry.invoke(ActionIds.COMMAND_TIME_DAY, context);
        ActionRegistry.invoke(ActionIds.TELEMETRY_RELOAD, context);
    }

    @Test
    void allActionsAppearInRadialMenu() throws IllegalAccessException {
        DevModActions.registerCommon();
        DevModClientActions.register();

        Map<MacroCategory, java.util.List<RadialCategory>> categories =
            RadialMenuRegistry.createDefaultCategories(() -> RadialMenuItem.registry(ActionIds.UI_MOB_CONFIG_OPEN));

        Set<String> radialIds = new HashSet<>();
        Set<RadialCategory> visited = new HashSet<>();
        for (MacroCategory macro : MacroCategory.values()) {
            for (RadialCategory category : categories.get(macro)) {
                collectRadialIds(category, radialIds, visited);
            }
        }

        Set<String> excluded = Set.of(
            ActionIds.UI_RADIAL_OPEN, ActionIds.UI_MAILBOX_OPEN, ActionIds.UI_TESTER_TASKS_OPEN,
            // Admin commands (available via /mailbox and /news commands, not radial menu)
            ActionIds.MAILBOX_COMMAND_HELP, ActionIds.MAILBOX_COMMAND_STATS,
            ActionIds.MAILBOX_COMMAND_SEND, ActionIds.MAILBOX_COMMAND_BROADCAST,
            ActionIds.MAILBOX_COMMAND_INBOX, ActionIds.MAILBOX_COMMAND_PURGE,
            ActionIds.NEWS_COMMAND_HELP, ActionIds.NEWS_COMMAND_LIST,
            ActionIds.NEWS_COMMAND_CREATE, ActionIds.NEWS_COMMAND_DELETE,
            ActionIds.NEWS_COMMAND_PUBLISH
        );
        for (Field field : ActionIds.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String id = (String) field.get(null);
            if (excluded.contains(id)) {
                continue;
            }
            assertTrue(radialIds.contains(id), "Action missing from radial menu: " + field.getName());
        }
    }

    private static void collectRadialIds(RadialCategory category, Set<String> ids, Set<RadialCategory> visited) {
        if (!visited.add(category)) {
            return;
        }
        for (RadialMenuItem item : category.getItems()) {
            String registryId = item.getAction().getRegistryId();
            if (registryId != null) {
                ids.add(registryId);
            }
            if (item.isSubcategoryLink()) {
                RadialCategory subcategory = item.getLinkedSubcategory();
                if (subcategory != null) {
                    collectRadialIds(subcategory, ids, visited);
                }
            }
        }
    }
}
