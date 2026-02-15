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

        addSection("devmod.nexus_hub.section.combat_zones",
            actionItem("nexus-tp-hub", ActionIds.COMMAND_NEXUS_TP_HUB, EditorButton.Style.PRIMARY),
            actionItem("nexus-tp-combat-lab", ActionIds.COMMAND_NEXUS_TP_COMBAT_LAB, EditorButton.Style.NORMAL),
            actionItem("nexus-tp-abilities-lab", ActionIds.COMMAND_NEXUS_TP_ABILITIES_LAB, EditorButton.Style.NORMAL),
            actionItem("nexus-tp-boss-arena", ActionIds.COMMAND_NEXUS_TP_BOSS_ARENA, EditorButton.Style.NORMAL),
            actionItem("nexus-tp-collision-lab", ActionIds.COMMAND_NEXUS_TP_COLLISION_LAB, EditorButton.Style.NORMAL)
        );

        addSection("devmod.nexus_hub.section.systems_zones",
            advancedActionItem("nexus-tp-portal-lab", ActionIds.COMMAND_NEXUS_TP_PORTAL_LAB, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-npc-lab", ActionIds.COMMAND_NEXUS_TP_NPC_LAB, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-quest-testing", ActionIds.COMMAND_NEXUS_TP_QUEST_TESTING, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-arena-builder", ActionIds.COMMAND_NEXUS_TP_ARENA_BUILDER, EditorButton.Style.NORMAL)
        );

        addSection("devmod.nexus_hub.section.tools_zones",
            advancedActionItem("nexus-tp-vfx-studio", ActionIds.COMMAND_NEXUS_TP_VFX_STUDIO, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-item-workshop", ActionIds.COMMAND_NEXUS_TP_ITEM_WORKSHOP, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-config-room", ActionIds.COMMAND_NEXUS_TP_CONFIG_ROOM, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-hud-testing", ActionIds.COMMAND_NEXUS_TP_HUD_TESTING, EditorButton.Style.NORMAL)
        );

        addSection("devmod.nexus_hub.section.misc_zones",
            advancedActionItem("nexus-tp-sandbox", ActionIds.COMMAND_NEXUS_TP_SANDBOX, EditorButton.Style.NORMAL),
            advancedActionItem("nexus-tp-admin-tools", ActionIds.COMMAND_NEXUS_TP_ADMIN_TOOLS, EditorButton.Style.NORMAL)
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
