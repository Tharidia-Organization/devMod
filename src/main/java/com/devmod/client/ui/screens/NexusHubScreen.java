package com.devmod.client.ui.screens;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.client.ui.editor.components.EditorButton;

@OnlyIn(Dist.CLIENT)
public class NexusHubScreen extends ActionHubScreen {

    public NexusHubScreen(@Nullable Screen parent) {
        super(parent, "nexus_hub");
        buildSections();
    }

    @Override
    protected void buildSections() {
        resetSections();

        addSection("devmod.nexus_hub.section.access",
            actionItem("nexus-help", ActionIds.COMMAND_NEXUS_HELP, EditorButton.Style.NORMAL),
            actionItem("nexus-status", ActionIds.COMMAND_NEXUS_STATUS, EditorButton.Style.NORMAL),
            actionItem("nexus-zones", ActionIds.COMMAND_NEXUS_ZONES, EditorButton.Style.NORMAL),
            actionItem("nexus-enter", ActionIds.COMMAND_NEXUS_ENTER, EditorButton.Style.PRIMARY),
            actionItem("nexus-return", ActionIds.COMMAND_NEXUS_RETURN, EditorButton.Style.NORMAL)
        );

        addSection("devmod.nexus_hub.section.core_zones",
            actionItem("nexus-tp-hub", ActionIds.COMMAND_NEXUS_TP_HUB, EditorButton.Style.PRIMARY),
            actionItem("nexus-tp-overview", ActionIds.COMMAND_NEXUS_TP_OVERVIEW, EditorButton.Style.NORMAL),
            actionItem("nexus-tp-tutorial", ActionIds.COMMAND_NEXUS_TP_TUTORIAL, EditorButton.Style.NORMAL),
            actionItem("nexus-tp-gate", ActionIds.COMMAND_NEXUS_TP_GATE_PROGRESSION, EditorButton.Style.NORMAL),
            actionItem("nexus-tp-classes", ActionIds.COMMAND_NEXUS_TP_CLASSES, EditorButton.Style.NORMAL)
        );

        addSection("devmod.nexus_hub.section.systems_zones",
            advancedActionItem("nexus-tp-combat", ActionIds.COMMAND_NEXUS_TP_COMBAT, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-arena", ActionIds.COMMAND_NEXUS_TP_ARENA, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-ui", ActionIds.COMMAND_NEXUS_TP_UI, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-telemetry", ActionIds.COMMAND_NEXUS_TP_TELEMETRY, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-showcase", ActionIds.COMMAND_NEXUS_TP_SHOWCASE, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-integration", ActionIds.COMMAND_NEXUS_TP_INTEGRATION, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-sandbox", ActionIds.COMMAND_NEXUS_TP_SANDBOX, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-mechanics", ActionIds.COMMAND_NEXUS_TP_MECHANICS, EditorButton.Style.NORMAL)
        );

        addSection("devmod.nexus_hub.section.town_zones",
            advancedActionItem("nexus-tp-building-west", ActionIds.COMMAND_NEXUS_TP_BUILDING_WEST, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-building-east", ActionIds.COMMAND_NEXUS_TP_BUILDING_EAST, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-quest-ne", ActionIds.COMMAND_NEXUS_TP_QUEST_NORTHEAST, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-quest-nw", ActionIds.COMMAND_NEXUS_TP_QUEST_NORTHWEST, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-war-east", ActionIds.COMMAND_NEXUS_TP_WAR_HUB_EAST, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-war-west", ActionIds.COMMAND_NEXUS_TP_WAR_HUB_WEST, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-econ-east", ActionIds.COMMAND_NEXUS_TP_ECONOMIA_EAST, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-econ-west", ActionIds.COMMAND_NEXUS_TP_ECONOMIA_WEST, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-town", ActionIds.COMMAND_NEXUS_TP_TOWN_MANAGEMENT, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-eventi", ActionIds.COMMAND_NEXUS_TP_EVENTI, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-dm", ActionIds.COMMAND_NEXUS_TP_DM_MOD, EditorButton.Style.NORMAL)
        );

        addSection("devmod.nexus_hub.section.ops",
            advancedActionItem("nexus-lock", ActionIds.COMMAND_NEXUS_LOCK, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-unlock", ActionIds.COMMAND_NEXUS_UNLOCK, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-rebuild", ActionIds.COMMAND_NEXUS_REBUILD, EditorButton.Style.DANGER)
        );

        addSection("devmod.nexus_hub.section.avatar",
            advancedActionItem("nexus-avatar-status", ActionIds.COMMAND_NEXUS_AVATAR_STATUS, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-avatar-spawn", ActionIds.COMMAND_NEXUS_AVATAR_SPAWN, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-avatar-remove", ActionIds.COMMAND_NEXUS_AVATAR_REMOVE, EditorButton.Style.DANGER),
            advancedActionItem("nexus-riftstamp", ActionIds.COMMAND_NEXUS_RIFTSTAMP, EditorButton.Style.NORMAL)
        );
    }
}
