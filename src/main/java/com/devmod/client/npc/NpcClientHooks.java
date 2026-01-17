package com.devmod.client.npc;

import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.network.NpcDialogPayload;
import com.devmod.npc.network.OpenDialogEditorPayload;
import com.devmod.npc.network.OpenNpcConfigPayload;

/**
 * Client-side hooks for NPC system network payloads.
 * Called via reflection from NpcNetworkHandler to avoid class loading on server.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcClientHooks {
    private NpcClientHooks() {}

    /**
     * Opens the NPC configuration screen.
     * Called when OpenNpcConfigPayload is received.
     */
    public static void openConfigScreen(OpenNpcConfigPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            NpcConfiguration config = payload.config();
            if (config == null) {
                if (mc.player == null) {
                    return;
                }
                config = NpcConfiguration.createDefault(
                    mc.player.getName().getString(),
                    mc.player.getUUID()
                );
            }
            mc.setScreen(new NpcConfigScreen(
                config,
                payload.hand(),
                payload.existingNpcId()
            ));
        });
    }

    /**
     * Opens the dialog editor screen.
     * Called when OpenDialogEditorPayload is received.
     */
    public static void openDialogEditor(OpenDialogEditorPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new DialogEditorScreen(
            payload.dialogSetId(),
            payload.dialogSet()
        )));
    }

    /**
     * Opens the NPC dialog screen.
     * Called when NpcDialogPayload is received.
     */
    public static void openNpcDialog(NpcDialogPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new NpcDialogScreen(
            payload.npcId(),
            payload.npcName(),
            payload.dialogSetId(),
            payload.currentNodeId(),
            payload.lines(),
            payload.options()
        )));
    }
}
