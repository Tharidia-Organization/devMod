package com.devmod.actions.domains;

import java.util.List;
import java.util.Set;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.catalog.ActionSpec.BindingMeta;
import com.devmod.actions.catalog.ActionSpec.PolicyMeta;
import com.devmod.actions.catalog.ActionSpec.TelemetryMeta;
import com.devmod.actions.catalog.ActionSpec.UiMeta;

/**
 * Domain registrar for gameplay actions: abilities (dash/dodge),
 * quest task completion, and endurance quest flow (start/continue/exit).
 *
 * <p>All actions in this domain are CLIENT channel and originate from
 * keybinds or the radial menu. Handlers delegate to the existing
 * ActionRegistry for backwards compatibility during migration.
 */
public final class GameplayDomainRegistrar implements DomainRegistrar {

    @Override
    public String domainName() {
        return "gameplay";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        return List.of(
            // ── Abilities ──
            ActionSpec.builder(ActionIds.ABILITY_DASH)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.COMBAT)
                .actionType(ActionType.TRIGGER_EVENT)
                .ui("devmod.action.ability.dash",
                    "devmod.action.ability.dash.desc",
                    "minecraft:feather",
                    "Root/Combat/Abilities/Dash",
                    false)
                .binding("DASH_KEY", null, null)
                .policy(false, 0, "clientOnly")
                .telemetryTags("ability", "dash")
                .build(),

            ActionSpec.builder(ActionIds.ABILITY_DODGE)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.COMBAT)
                .actionType(ActionType.TRIGGER_EVENT)
                .ui("devmod.action.ability.dodge",
                    "devmod.action.ability.dodge.desc",
                    "minecraft:rabbit_foot",
                    "Root/Combat/Abilities/Dodge",
                    false)
                .binding("DODGE_KEY", null, null)
                .policy(false, 0, "clientOnly")
                .telemetryTags("ability", "dodge")
                .build(),

            // ── Quest ──
            ActionSpec.builder(ActionIds.QUEST_TASK_COMPLETE)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.ENDURANCE)
                .actionType(ActionType.TRIGGER_EVENT)
                .ui("devmod.action.quest.task.complete",
                    "devmod.action.quest.task.complete.desc",
                    "minecraft:writable_book",
                    "Root/Play/Quest Flow/Task Complete",
                    false)
                .binding("QUEST_COMPLETE_TASK_KEY", null, null)
                .policy(false, 0, "clientOnly_activeTask")
                .telemetryTags("quest", "task_complete")
                .build(),

            // ── Endurance Quest Flow ──
            ActionSpec.builder(ActionIds.ENDURANCE_QUEST_START)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.ENDURANCE)
                .actionType(ActionType.SEND_SERVER_RPC)
                .ui("devmod.action.endurance.start",
                    "devmod.action.endurance.start.desc",
                    "minecraft:compass",
                    "Root/Play/Endurance/Quest Start",
                    false)
                .policy(false, 0, "clientOnly")
                .telemetryTags("endurance", "quest_start")
                .build(),

            ActionSpec.builder(ActionIds.ENDURANCE_QUEST_CONTINUE)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.ENDURANCE)
                .actionType(ActionType.SEND_SERVER_RPC)
                .ui("devmod.action.endurance.continue",
                    "devmod.action.endurance.continue.desc",
                    "minecraft:totem_of_undying",
                    "Root/Play/Endurance/Quest Continue",
                    false)
                .binding("QUEST_CONTINUE_KEY", null, null)
                .policy(false, 0, "clientOnly_respawnOrCheckpoint")
                .telemetryTags("endurance", "quest_continue")
                .build(),

            ActionSpec.builder(ActionIds.ENDURANCE_QUEST_EXIT)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.ENDURANCE)
                .actionType(ActionType.SEND_SERVER_RPC)
                .ui("devmod.action.endurance.exit",
                    "devmod.action.endurance.exit.desc",
                    "minecraft:barrier",
                    "Root/Play/Endurance/Quest Exit",
                    false)
                .binding("QUEST_EXIT_KEY", null, null)
                .policy(true, 0, "clientOnly_activeQuest")
                .telemetryTags("endurance", "quest_exit")
                .build(),

            // ── Endurance Settings Screen ──
            ActionSpec.builder(ActionIds.UI_ENDURANCE_SETTINGS_OPEN)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.CONFIG)
                .actionType(ActionType.NAVIGATE_SCREEN)
                .ui("devmod.action.endurance_settings",
                    "devmod.action.endurance_settings.desc",
                    "minecraft:diamond_sword",
                    "Root/Config/Endurance",
                    false)
                .policy(false, 0, "screenPrecondition")
                .telemetryTags("ui", "endurance_settings")
                .build()
        );
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        registry.register(ActionIds.ABILITY_DASH,
            context -> ActionRegistry.invoke(ActionIds.ABILITY_DASH, context));
        registry.register(ActionIds.ABILITY_DODGE,
            context -> ActionRegistry.invoke(ActionIds.ABILITY_DODGE, context));
        registry.register(ActionIds.QUEST_TASK_COMPLETE,
            context -> ActionRegistry.invoke(ActionIds.QUEST_TASK_COMPLETE, context));
        registry.register(ActionIds.ENDURANCE_QUEST_START,
            context -> ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_START, context));
        registry.register(ActionIds.ENDURANCE_QUEST_CONTINUE,
            context -> ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_CONTINUE, context));
        registry.register(ActionIds.ENDURANCE_QUEST_EXIT,
            context -> ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_EXIT, context));
        registry.register(ActionIds.UI_ENDURANCE_SETTINGS_OPEN,
            context -> ActionRegistry.invoke(ActionIds.UI_ENDURANCE_SETTINGS_OPEN, context));
    }

    @Override
    public Set<String> getDependencies() {
        return Set.of();
    }
}
