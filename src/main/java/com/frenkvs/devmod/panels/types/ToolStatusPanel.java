package com.frenkvs.devmod.panels.types;

import com.frenkvs.devmod.panels.core.FloatingPanel;
import com.frenkvs.devmod.panels.core.PanelType;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.hub.ToolType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Pannello che mostra lo stato degli overlay/tool attivi.
 *
 * Visualizza:
 * - Lista di tool attivi con hotkey
 * - Indicatori colorati per stato on/off
 * - Conteggio tool attivi
 */
public class ToolStatusPanel extends FloatingPanel {

    // Cache degli stati tool
    private List<ToolState> toolStates = new ArrayList<>();
    private int activeCount = 0;
    private long lastUpdateTick = 0;

    private static final int UPDATE_INTERVAL_TICKS = 10; // Aggiorna ogni 10 tick

    /**
     * Stato di un singolo tool.
     */
    private record ToolState(String name, String hotkey, boolean enabled, int color) {}

    /**
     * Crea un pannello stato tool in una posizione fissa.
     */
    public ToolStatusPanel(Vec3 position) {
        super(PanelType.TOOL_STATUS, position);
        updateToolStates();
    }

    @Override
    public void tick() {
        super.tick();

        long currentTick = System.currentTimeMillis() / 50;
        if (currentTick - lastUpdateTick >= UPDATE_INTERVAL_TICKS) {
            updateToolStates();
            lastUpdateTick = currentTick;
        }
    }

    /**
     * Aggiorna gli stati di tutti i tool.
     */
    private void updateToolStates() {
        toolStates.clear();
        activeCount = 0;

        for (ToolType tool : ToolType.values()) {
            boolean enabled = tool.isEnabled();
            if (enabled) activeCount++;

            toolStates.add(new ToolState(
                tool.getLabel(),
                tool.getHotkey(),
                enabled,
                enabled ? UIConstants.Status.SUCCESS : UIConstants.Text.MUTED
            ));
        }
    }

    @Override
    public void renderContent(GuiGraphics graphics, int contentWidth, int contentHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int y = 0;
        int lineHeight = 10;

        // Header con conteggio
        String header = "Active: " + activeCount + "/" + toolStates.size();
        graphics.drawString(mc.font, header, 0, y, UIConstants.Text.SECONDARY, false);
        y += lineHeight + 4;

        // Lista tool (mostra solo i primi che entrano)
        int maxTools = (contentHeight - y) / lineHeight;
        int shown = 0;

        for (ToolState tool : toolStates) {
            if (shown >= maxTools) break;

            // Indicatore colorato
            int dotSize = 4;
            int dotY = y + (lineHeight - dotSize) / 2;
            graphics.fill(0, dotY, dotSize, dotY + dotSize, tool.color);

            // Nome e hotkey
            String text = "[" + tool.hotkey + "] " + tool.name;
            int textColor = tool.enabled ? UIConstants.Text.PRIMARY : UIConstants.Text.MUTED;
            graphics.drawString(mc.font, text, dotSize + 4, y, textColor, false);

            y += lineHeight;
            shown++;
        }
    }

    @Override
    public void renderContent3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        int y = 0;
        int lineHeight = 10;

        // Header con conteggio
        String header = "Active: " + activeCount + "/" + toolStates.size();
        renderText3D(poseStack, bufferSource, font, header, 0, y, applyAlpha(UIConstants.Text.SECONDARY, alpha));
        y += lineHeight + 4;

        // Lista tool (mostra solo i primi che entrano)
        int maxTools = (contentHeight - y) / lineHeight;
        int shown = 0;

        for (ToolState tool : toolStates) {
            if (shown >= maxTools) break;

            // Nome e hotkey
            String text = "[" + tool.hotkey + "] " + tool.name;
            int textColor = tool.enabled ? UIConstants.Text.PRIMARY : UIConstants.Text.MUTED;
            renderText3D(poseStack, bufferSource, font, text, 0, y, applyAlpha(textColor, alpha));

            y += lineHeight;
            shown++;
        }
    }

    @Override
    public String getTitle() {
        return "Tools (" + activeCount + ")";
    }

    // === Getters ===

    public int getActiveCount() {
        return activeCount;
    }

    public int getTotalCount() {
        return toolStates.size();
    }

    /**
     * Verifica se un tool specifico e' attivo.
     */
    public boolean isToolActive(ToolType tool) {
        return tool.isEnabled();
    }

    @Override
    public String toString() {
        return String.format("ToolStatusPanel[active=%d/%d]", activeCount, toolStates.size());
    }
}
