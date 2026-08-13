package com.devmod.actions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.abilities.AbilityActionPayload;
import com.devmod.abilities.DodgeAbilitySystem;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionPrecondition;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.endurance.QuestActionPayload;
import com.devmod.endurance.StartQuestPayload;
import com.devmod.quest.QuestManager;
import com.devmod.quest.QuestTask;
import com.devmod.util.I18n;

/**
 * Gameplay-related client actions: abilities (dash/dodge), quest task completion,
 * endurance quest start/continue/exit.
 */
public final class ClientGameplayActions {

    private ClientGameplayActions() {}

    // ── Precondition helpers (package-visible; shared with the V2 catalog so both
    //    engines gate these actions identically) ──

    static ActionPrecondition activeTaskPrecondition() {
        return ActionPreconditions.clientOnly().and(
            ActionPreconditions.withMessage(
                context -> QuestManager.INSTANCE.getCurrentTask() != null,
                "devmod.action.requires_active_task"
            ));
    }

    static ActionPrecondition respawnOrCheckpointPrecondition() {
        return ActionPreconditions.clientOnly().and(
            ActionPreconditions.withMessage(
                context -> ClientQuestCache.isAwaitingRespawn()
                    || ClientQuestCache.isAtCheckpoint()
                    || context.getPayload(QuestActionPayload.Action.class) != null,
                "devmod.action.requires_respawn_or_checkpoint"
            ));
    }

    static ActionPrecondition activeQuestPrecondition() {
        return ActionPreconditions.clientOnly().and(
            ActionPreconditions.withMessage(
                context -> ClientQuestCache.hasActiveQuest(),
                "devmod.action.requires_active_quest"
            ));
    }

    // ── Registration ──

    static void registerAbilityActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.ABILITY_DASH)
            .labelKey("devmod.action.ability.dash")
            .descriptionKey("devmod.action.ability.dash.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Abilities/Dash")
            .icon(Items.FEATHER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> PacketDistributor.sendToServer(
                java.util.Objects.requireNonNull(AbilityActionPayload.dash(), "dashPayload")))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ABILITY_DODGE)
            .labelKey("devmod.action.ability.dodge")
            .descriptionKey("devmod.action.ability.dodge.desc")
            .category(ActionCategory.COMBAT)
            .menuPath("Root/Combat/Abilities/Dodge")
            .icon(Items.RABBIT_FOOT)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                Minecraft mc = Minecraft.getInstance();
                DodgeAbilitySystem.DodgeDirection direction = determineDodgeDirection(mc);
                PacketDistributor.sendToServer(
                    java.util.Objects.requireNonNull(AbilityActionPayload.dodge(direction), "dodgePayload"));
            })
            .build());
    }

    static void registerQuestActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.QUEST_TASK_COMPLETE)
            .labelKey("devmod.action.quest.task.complete")
            .descriptionKey("devmod.action.quest.task.complete.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Quest Flow/Task Complete")
            .icon(Items.WRITABLE_BOOK)
            .precondition(activeTaskPrecondition())
            .handler(context -> {
                QuestTask task = QuestManager.INSTANCE.getCurrentTask();
                if (task != null) {
                    String taskName = task.getDescription();
                    QuestManager.INSTANCE.completeCurrentTask();
                    context.sendSuccess(
                        I18n.translate("devmod.message.task_completed")
                            .append(I18n.translate("devmod.ui.colon_value", taskName)),
                        true
                    );
                } else {
                    context.sendSuccess(Component.translatable("devmod.message.no_active_task"), true);
                }
            })
            .build());
    }

    static void registerEnduranceActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.ENDURANCE_QUEST_START)
            .labelKey("devmod.action.endurance.start")
            .descriptionKey("devmod.action.endurance.start.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance/Quest Start")
            .icon(Items.COMPASS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                StartQuestPayload payload = context.getPayload(StartQuestPayload.class);
                if (payload == null) {
                    com.devmod.client.ui.ScreenSafety.openSafe(
                        "endurance_quest",
                        com.devmod.client.endurance.EnduranceQuestScreen::new);
                    return;
                }
                PacketDistributor.sendToServer(payload);
                Minecraft.getInstance().setScreen(null);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ENDURANCE_QUEST_CONTINUE)
            .labelKey("devmod.action.endurance.continue")
            .descriptionKey("devmod.action.endurance.continue.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance/Quest Continue")
            .icon(Items.TOTEM_OF_UNDYING)
            .precondition(respawnOrCheckpointPrecondition())
            .handler(context -> {
                QuestActionPayload.Action action = resolveContinueAction(context);
                PacketDistributor.sendToServer(new QuestActionPayload(action));
                context.sendSuccess(I18n.translate("devmod.network.continuing_quest"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.ENDURANCE_QUEST_EXIT)
            .labelKey("devmod.action.endurance.exit")
            .descriptionKey("devmod.action.endurance.exit.desc")
            .category(ActionCategory.ENDURANCE)
            .menuPath("Root/Play/Endurance/Quest Exit")
            .icon(Items.BARRIER)
            .precondition(activeQuestPrecondition())
            .handler(context -> {
                if (!context.isConfirmed()) {
                    com.devmod.client.ui.ScreenSafety.openSafe(
                        "quest_exit_confirm",
                        Minecraft.getInstance().screen,
                        () -> new com.devmod.client.endurance.QuestExitConfirmScreen(Minecraft.getInstance().screen));
                    return;
                }
                QuestActionPayload.Action action = resolveExitAction(context);
                PacketDistributor.sendToServer(new QuestActionPayload(action));
            })
            .build());

        // Endurance Settings screen
        ActionRegistry.register(RadialAction.builder(ActionIds.UI_ENDURANCE_SETTINGS_OPEN)
            .labelKey("devmod.action.endurance_settings")
            .descriptionKey("devmod.action.endurance_settings.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Endurance")
            .icon(Items.DIAMOND_SWORD)
            .precondition(ClientUIActions.screenPrecondition())
            .handler(context -> com.devmod.client.ui.ScreenSafety.openSafe(
                "endurance_settings",
                () -> new com.devmod.client.endurance.EnduranceSettingsScreen(null)))
            .build());
    }

    // ── Keybind hints ──

    static void registerKeybindHints() {
        ActionKeybindRegistry.register(ActionIds.ABILITY_DASH, KeyInputHandler.DASH_KEY);
        ActionKeybindRegistry.register(ActionIds.ABILITY_DODGE, KeyInputHandler.DODGE_KEY);
        ActionKeybindRegistry.register(ActionIds.QUEST_TASK_COMPLETE, KeyInputHandler.QUEST_COMPLETE_TASK_KEY);
        ActionKeybindRegistry.register(ActionIds.ENDURANCE_QUEST_CONTINUE, KeyInputHandler.QUEST_CONTINUE_KEY);
        ActionKeybindRegistry.register(ActionIds.ENDURANCE_QUEST_EXIT, KeyInputHandler.QUEST_EXIT_KEY);
    }

    static void registerCoreKeybindHints() {
        ActionKeybindRegistry.register(ActionIds.ABILITY_DASH, KeyInputHandler.DASH_KEY);
        ActionKeybindRegistry.register(ActionIds.ABILITY_DODGE, KeyInputHandler.DODGE_KEY);
        ActionKeybindRegistry.register(ActionIds.ENDURANCE_QUEST_CONTINUE, KeyInputHandler.QUEST_CONTINUE_KEY);
        ActionKeybindRegistry.register(ActionIds.ENDURANCE_QUEST_EXIT, KeyInputHandler.QUEST_EXIT_KEY);
    }

    // ── Private helpers ──

    private static QuestActionPayload.Action resolveContinueAction(ActionContext context) {
        QuestActionPayload.Action override = context.getPayload(QuestActionPayload.Action.class);
        if (override == QuestActionPayload.Action.CONTINUE_AFTER_DEATH
            || override == QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE) {
            return override;
        }
        if (ClientQuestCache.isAtCheckpoint()) {
            return QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE;
        }
        return QuestActionPayload.Action.CONTINUE_AFTER_DEATH;
    }

    private static QuestActionPayload.Action resolveExitAction(ActionContext context) {
        QuestActionPayload.Action override = context.getPayload(QuestActionPayload.Action.class);
        if (override == QuestActionPayload.Action.GIVE_UP_AFTER_DEATH
            || override == QuestActionPayload.Action.EXIT_AT_CHECKPOINT
            || override == QuestActionPayload.Action.ABANDON_QUEST) {
            return override;
        }
        if (ClientQuestCache.isAtCheckpoint()) {
            return QuestActionPayload.Action.EXIT_AT_CHECKPOINT;
        }
        if (ClientQuestCache.isAwaitingRespawn()) {
            return QuestActionPayload.Action.GIVE_UP_AFTER_DEATH;
        }
        return QuestActionPayload.Action.ABANDON_QUEST;
    }

    private static DodgeAbilitySystem.DodgeDirection determineDodgeDirection(Minecraft mc) {
        var options = mc.options;
        boolean left = options.keyLeft.isDown();
        boolean right = options.keyRight.isDown();
        boolean back = options.keyDown.isDown();
        boolean forward = options.keyUp.isDown();
        if (left && !right) {
            return DodgeAbilitySystem.DodgeDirection.LEFT;
        } else if (right && !left) {
            return DodgeAbilitySystem.DodgeDirection.RIGHT;
        } else if (back) {
            return DodgeAbilitySystem.DodgeDirection.BACK;
        } else if (forward) {
            return DodgeAbilitySystem.DodgeDirection.FORWARD;
        }
        return DodgeAbilitySystem.DodgeDirection.BACK;
    }
}
