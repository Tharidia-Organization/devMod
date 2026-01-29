package com.devmod.npc.network;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.devmod.network.PayloadSizeUtil;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.DialogSchedule;
import com.devmod.npc.dialog.DialogSet;
import com.devmod.npc.dialog.action.DialogAction;
import com.devmod.npc.dialog.condition.DialogCondition;

final class DialogPayloadSizing {

    private DialogPayloadSizing() {
    }

    static long estimateDialogSetSize(DialogSet dialogSet) {
        long size = PayloadSizeUtil.estimatedUtfSize(dialogSet.id());
        size += PayloadSizeUtil.estimatedUtfSize(dialogSet.name());
        size += PayloadSizeUtil.varIntSize(dialogSet.nodes().size());
        for (var entry : dialogSet.nodes().entrySet()) {
            size += PayloadSizeUtil.estimatedUtfSize(entry.getKey());
            size += estimateNodeSize(entry.getValue());
        }
        size += PayloadSizeUtil.estimatedUtfSize(dialogSet.entryNodeId());
        size += 8; // updatedAt
        size += PayloadSizeUtil.varIntSize(dialogSet.revision());
        size += 1; // isPreset
        size += 1; // owner UUID present
        if (dialogSet.ownerUUID() != null) {
            size += 16;
        }
        size += 1; // schedule present
        if (dialogSet.schedule() != null) {
            size += estimateScheduleSize(dialogSet.schedule());
        }
        return size;
    }

    private static long estimateNodeSize(DialogNode node) {
        long size = PayloadSizeUtil.varIntSize(node.lines().size());
        for (String line : node.lines()) {
            size += PayloadSizeUtil.estimatedUtfSize(line);
        }
        size += PayloadSizeUtil.varIntSize(node.options().size());
        for (DialogOption option : node.options()) {
            size += estimateOptionSize(option);
        }
        size += 1; // showCondition present
        if (node.showCondition() != null) {
            size += estimateConditionSize(node.showCondition());
        }
        return size;
    }

    private static long estimateOptionSize(DialogOption option) {
        long size = PayloadSizeUtil.estimatedUtfSize(option.id());
        size += PayloadSizeUtil.estimatedUtfSize(option.label());
        size += PayloadSizeUtil.estimatedUtfSize(option.icon());
        size += 1; // showCondition present
        if (option.showCondition() != null) {
            size += estimateConditionSize(option.showCondition());
        }
        size += estimateActionSize(option.action());
        return size;
    }

    private static long estimateActionSize(DialogAction action) {
        long size = PayloadSizeUtil.estimatedUtfSize(action.getType());
        switch (action) {
            case DialogAction.GoToNode goTo -> size += PayloadSizeUtil.estimatedUtfSize(goTo.nodeId());
            case DialogAction.CloseDialog ignored -> {
                // no extra data
            }
            case DialogAction.ExecuteCommand cmd -> {
                size += PayloadSizeUtil.estimatedUtfSize(cmd.command());
                size += PayloadSizeUtil.varIntSize(cmd.source().ordinal());
            }
            case DialogAction.Teleport tp -> {
                size += 1; // position present
                if (tp.position() != null) {
                    size += 8; // BlockPos
                }
                size += 1; // dimension present
                if (tp.dimension() != null) {
                    size += estimateResourceKeySize(tp.dimension());
                }
                size += 1; // zoneId present
                if (tp.zoneId() != null) {
                    size += PayloadSizeUtil.estimatedUtfSize(tp.zoneId());
                }
            }
            case DialogAction.GiveItem give -> {
                size += estimateResourceLocationSize(give.itemId());
                size += PayloadSizeUtil.varIntSize(give.count());
                size += 1; // nbt present
                size += estimateNbtSize(give.nbt());
            }
            case DialogAction.PlaySound sound -> {
                size += estimateResourceLocationSize(sound.soundId());
                size += 4; // volume
                size += 4; // pitch
            }
            case DialogAction.OpenGui gui -> size += PayloadSizeUtil.estimatedUtfSize(gui.guiId());
            case DialogAction.SetVariable set -> {
                size += PayloadSizeUtil.estimatedUtfSize(set.key());
                size += PayloadSizeUtil.estimatedUtfSize(set.value());
            }
            case DialogAction.RememberChoice remember -> {
                size += PayloadSizeUtil.estimatedUtfSize(remember.key());
                size += PayloadSizeUtil.estimatedUtfSize(remember.value());
            }
            case DialogAction.SetEmotion emo -> size += PayloadSizeUtil.varIntSize(emo.emotion().ordinal());
            case DialogAction.Custom custom -> {
                size += PayloadSizeUtil.estimatedUtfSize(custom.handlerId());
                size += 1; // data present
                size += estimateNbtSize(custom.data());
            }
        }
        return size;
    }

    private static long estimateConditionSize(DialogCondition condition) {
        long size = PayloadSizeUtil.estimatedUtfSize(condition.getType());
        switch (condition) {
            case DialogCondition.Always ignored -> {
                // no extra data
            }
            case DialogCondition.FirstVisit first -> size += 16;
            case DialogCondition.HasPermission perm -> size += PayloadSizeUtil.varIntSize(perm.opLevel());
            case DialogCondition.HasSelectedOption opt -> size += PayloadSizeUtil.estimatedUtfSize(opt.optionId());
            case DialogCondition.NpcHasEmotion emo -> size += PayloadSizeUtil.varIntSize(emo.emotion().ordinal());
            case DialogCondition.QuestCompleted quest -> size += PayloadSizeUtil.estimatedUtfSize(quest.questId());
            case DialogCondition.QuestCount count -> {
                size += PayloadSizeUtil.varIntSize(count.minQuests());
                size += PayloadSizeUtil.varIntSize(count.comparison().ordinal());
            }
            case DialogCondition.TimeOfDay time -> size += 16;
            case DialogCondition.And and -> size += estimateConditionListSize(and.conditions());
            case DialogCondition.Or or -> size += estimateConditionListSize(or.conditions());
            case DialogCondition.Not not -> size += estimateConditionSize(not.condition());
            case DialogCondition.Custom custom -> {
                size += PayloadSizeUtil.estimatedUtfSize(custom.handlerId());
                size += 1; // data present
                size += estimateNbtSize(custom.data());
            }
        }
        return size;
    }

    private static long estimateConditionListSize(@Nullable List<DialogCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return PayloadSizeUtil.varIntSize(0);
        }
        long size = PayloadSizeUtil.varIntSize(conditions.size());
        for (DialogCondition condition : conditions) {
            size += estimateConditionSize(condition);
        }
        return size;
    }

    private static long estimateScheduleSize(DialogSchedule schedule) {
        long size = 1; // minGameTime present
        if (schedule.minGameTime() != null) {
            size += 8;
        }
        size += 1; // maxGameTime present
        if (schedule.maxGameTime() != null) {
            size += 8;
        }
        size += 1; // realWorldDays present
        if (schedule.realWorldDays() != null && !schedule.realWorldDays().isEmpty()) {
            size += PayloadSizeUtil.varIntSize(schedule.realWorldDays().size());
            size += schedule.realWorldDays().size(); // day ordinals (1..7)
        }
        size += 1; // startDate present
        if (schedule.startDate() != null) {
            size += PayloadSizeUtil.estimatedUtfSize(schedule.startDate().toString());
        }
        size += 1; // endDate present
        if (schedule.endDate() != null) {
            size += PayloadSizeUtil.estimatedUtfSize(schedule.endDate().toString());
        }
        return size;
    }

    private static long estimateResourceLocationSize(ResourceLocation location) {
        return PayloadSizeUtil.estimatedUtfSize(location.toString());
    }

    private static long estimateResourceKeySize(ResourceKey<Level> key) {
        return PayloadSizeUtil.estimatedUtfSize(key.location().toString());
    }

    private static long estimateNbtSize(@Nullable net.minecraft.nbt.CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return 0;
        }
        return PayloadSizeUtil.estimatedUtfSize(tag.toString());
    }
}
