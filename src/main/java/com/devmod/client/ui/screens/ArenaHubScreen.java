package com.devmod.client.ui.screens;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.client.ui.editor.components.EditorButton;

@OnlyIn(Dist.CLIENT)
public class ArenaHubScreen extends ActionHubScreen {

    public ArenaHubScreen(@Nullable Screen parent) {
        super(parent, "arena_hub");
        buildSections();
    }

    @Override
    protected void buildSections() {
        resetSections();

        addSection("devmod.arena_hub.section.ops",
            actionItem("arena-quick-test", ActionIds.ARENA_QUICK_TEST_WIZARD_OPEN, EditorButton.Style.NORMAL),
            actionItem("arena-create", ActionIds.ARENA_CREATE, EditorButton.Style.PRIMARY),
            actionItem("arena-status", ActionIds.ARENA_STATUS, EditorButton.Style.NORMAL),
            advancedActionItem("arena-help", ActionIds.ARENA_HELP, EditorButton.Style.NORMAL)
        );

        addSection("devmod.arena_hub.section.templates",
            actionItem("arena-template-list", ActionIds.ARENA_TEMPLATE_LIST, EditorButton.Style.NORMAL),
            actionItem("arena-template-info", ActionIds.ARENA_TEMPLATE_INFO, EditorButton.Style.NORMAL),
            advancedActionItem("arena-template-status", ActionIds.ARENA_TEMPLATE_STATUS, EditorButton.Style.NORMAL),
            advancedActionItem("arena-template-reload", ActionIds.ARENA_TEMPLATE_RELOAD, EditorButton.Style.NORMAL),
            advancedActionItem("arena-template-validate", ActionIds.ARENA_VALIDATE, EditorButton.Style.NORMAL),
            advancedActionItem("arena-template-metrics", ActionIds.ARENA_METRICS, EditorButton.Style.NORMAL)
        );

        addSection("devmod.arena_hub.section.force",
            advancedActionItem("arena-force", ActionIds.ARENA_FORCE, EditorButton.Style.PRIMARY),
            advancedActionItem("arena-force-status", ActionIds.ARENA_FORCE_STATUS, EditorButton.Style.NORMAL),
            advancedActionItem("arena-force-clear", ActionIds.ARENA_FORCE_CLEAR, EditorButton.Style.NORMAL)
        );

        addSection("devmod.arena_hub.section.autosmoke",
            advancedActionItem("arena-autosmoke-run", ActionIds.ARENA_AUTOSMOKE_RUN, EditorButton.Style.PRIMARY),
            advancedActionItem("arena-autosmoke-status", ActionIds.ARENA_AUTOSMOKE_STATUS, EditorButton.Style.NORMAL),
            advancedActionItem("arena-autosmoke-schedule", ActionIds.ARENA_AUTOSMOKE_SCHEDULE_STATUS, EditorButton.Style.NORMAL)
        );

        addSection("devmod.arena_hub.section.hud",
            advancedActionItem("arena-hud-toggle", ActionIds.ARENA_HUD_TOGGLE, EditorButton.Style.PRIMARY),
            advancedActionItem("arena-hud-on", ActionIds.ARENA_HUD_ON, EditorButton.Style.NORMAL),
            advancedActionItem("arena-hud-off", ActionIds.ARENA_HUD_OFF, EditorButton.Style.NORMAL),
            advancedActionItem("arena-hud-status", ActionIds.ARENA_HUD_STATUS, EditorButton.Style.NORMAL)
        );

        addSection("devmod.arena_hub.section.bounds",
            advancedActionItem("arena-bounds-editor", ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN, EditorButton.Style.PRIMARY),
            advancedActionItem("arena-bounds-point-a", ActionIds.UI_ROOM_BOUNDS_POINT_A, EditorButton.Style.NORMAL),
            advancedActionItem("arena-bounds-point-b", ActionIds.UI_ROOM_BOUNDS_POINT_B, EditorButton.Style.NORMAL),
            advancedActionItem("arena-bounds-save", ActionIds.UI_ROOM_BOUNDS_SAVE, EditorButton.Style.NORMAL),
            advancedActionItem("arena-bounds-delete", ActionIds.UI_ROOM_BOUNDS_DELETE_LAST, EditorButton.Style.NORMAL)
        );
    }
}
