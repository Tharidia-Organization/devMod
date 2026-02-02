package com.devmod.client.ui.screens;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.client.ui.editor.components.EditorButton;

@OnlyIn(Dist.CLIENT)
public class PlayHubScreen extends ActionHubScreen {

    public PlayHubScreen(@Nullable Screen parent) {
        super(parent, "play_hub");
        buildSections();
    }

    @Override
    protected void buildSections() {
        resetSections();

        addSection("devmod.play_hub.section.endurance",
            actionItem("play-endurance-start", ActionIds.UI_ENDURANCE_SCREEN_OPEN, EditorButton.Style.PRIMARY),
            actionItem("play-endurance-shop", ActionIds.UI_ENDURANCE_SHOP_OPEN, EditorButton.Style.NORMAL),
            advancedActionItem("play-endurance-settings", ActionIds.UI_ENDURANCE_SETTINGS_OPEN, EditorButton.Style.NORMAL)
        );

        addSection("devmod.play_hub.section.quest_flow",
            advancedActionItem("play-quest-death", ActionIds.UI_QUEST_DEATH_OPEN, EditorButton.Style.NORMAL),
            advancedActionItem("play-perk-selection", ActionIds.UI_PERK_SELECTION_OPEN, EditorButton.Style.NORMAL),
            advancedActionItem("play-wave-checkpoint", ActionIds.UI_WAVE_CHECKPOINT_OPEN, EditorButton.Style.NORMAL),
            advancedActionItem("play-quest-completion", ActionIds.UI_QUEST_COMPLETION_OPEN, EditorButton.Style.NORMAL)
        );

        addSection("devmod.play_hub.section.party",
            actionItem("play-party", ActionIds.UI_PARTY_OPEN, EditorButton.Style.PRIMARY),
            actionItem("play-notification-center", ActionIds.UI_NOTIFICATION_CENTER_OPEN, EditorButton.Style.NORMAL),
            advancedActionItem("play-party-invite", ActionIds.UI_PARTY_INVITE_POPUP_OPEN, EditorButton.Style.NORMAL)
        );

        addSection("devmod.play_hub.section.season",
            advancedActionItem("play-welcome", ActionIds.UI_WELCOME_OPEN, EditorButton.Style.NORMAL),
            advancedActionItem("play-season-pass", ActionIds.UI_SEASON_PASS_OPEN, EditorButton.Style.NORMAL)
        );
    }
}
