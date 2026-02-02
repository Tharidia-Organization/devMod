package com.devmod.client.template;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.template.data.RoomTemplate;
import com.devmod.template.data.ZoneTemplate;
import com.devmod.template.network.ApplyTemplatePayload;

/**
 * GUI screen for selecting and applying room templates.
 */
@OnlyIn(Dist.CLIENT)
public class TemplateEditorScreen extends Screen {
    private static final int EDITOR_WIDTH = 440;
    private static final int EDITOR_HEIGHT = 320;
    private static final int PADDING = DesignTokens.Spacing.LG;

    private final String zoneId;
    private final int minX, minZ, maxX, maxZ, floorY;
    private final List<RoomTemplate> availableTemplates;
    private String selectedTemplateId;

    private int leftPos;
    private int topPos;
    private int scrollOffset = 0;

    private final List<EditorButtonWidget> templateButtons = new java.util.ArrayList<>();
    @Nullable
    private EditorButton applyButton;

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }

    public TemplateEditorScreen(String zoneId,
                                int minX, int minZ, int maxX, int maxZ, int floorY,
                                List<RoomTemplate> availableTemplates,
                                String currentTemplateId) {
        super(Component.translatable("screen.devmod.template_editor"));
        this.zoneId = zoneId;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.floorY = floorY;
        this.availableTemplates = availableTemplates;
        this.selectedTemplateId = currentTemplateId;
    }

    @Override
    protected void init() {
        super.init();
        UIScaleManager.update();

        int sEditorWidth = s(EDITOR_WIDTH);
        int sEditorHeight = s(EDITOR_HEIGHT);
        int sPadding = s(PADDING);
        this.leftPos = (this.width - sEditorWidth) / 2;
        this.topPos = (this.height - sEditorHeight) / 2;
        templateButtons.clear();

        // Template buttons
        int buttonY = topPos + s(60);
        int buttonIndex = 0;
        int listButtonHeight = s(EditorButton.Size.MEDIUM.height());
        int listRowStride = listButtonHeight + s(DesignTokens.Spacing.SM);
        int listButtonWidth = sEditorWidth - sPadding * 2 - s(100);

        for (RoomTemplate template : availableTemplates) {
            if (buttonIndex >= 8) break; // Max visible

            int y = buttonY + (buttonIndex - scrollOffset) * listRowStride;
            if (y >= topPos + s(60) && y < topPos + sEditorHeight - s(80)) {
                final String templateId = template.templateId();
                EditorButton.Style style = templateId.equals(selectedTemplateId)
                    ? EditorButton.Style.PRIMARY
                    : EditorButton.Style.GHOST;
                EditorButton templateButton = EditorButton.builder("template_" + templateId, template.displayName())
                    .style(style)
                    .size(EditorButton.Size.MEDIUM)
                    .onClick(() -> selectTemplate(templateId))
                    .build();
                EditorButtonWidget widget = new EditorButtonWidget(templateButton, leftPos + sPadding, y, listButtonWidth, listButtonHeight);
                templateButtons.add(widget);
                addRenderableWidget(widget);
            }
            buttonIndex++;
        }

        // Apply button
        int buttonRowY = topPos + sEditorHeight - sPadding - listButtonHeight;
        int actionWidth = s(DesignTokens.Size.BUTTON_WIDTH_MEDIUM);
        int cancelWidth = s(DesignTokens.Size.BUTTON_WIDTH);
        int actionGap = s(DesignTokens.Spacing.SM);

        applyButton = EditorButton.builder("apply_template", Component.translatable("gui.devmod.template.apply").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::applyTemplate)
            .build();
        addRenderableWidget(new EditorButtonWidget(applyButton,
            leftPos + sEditorWidth - sPadding - actionWidth * 2 - actionGap,
            buttonRowY,
            actionWidth,
            listButtonHeight));

        EditorButton clearApplyButton = EditorButton.builder("clear_apply", Component.translatable("gui.devmod.template.clear_apply").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::clearAndApplyTemplate)
            .build();
        addRenderableWidget(new EditorButtonWidget(clearApplyButton,
            leftPos + sEditorWidth - sPadding - actionWidth,
            buttonRowY,
            actionWidth,
            listButtonHeight));

        EditorButton cancelButton = EditorButton.builder("cancel", Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(cancelButton,
            leftPos + sPadding,
            buttonRowY,
            cancelWidth,
            listButtonHeight));

        updateButtonStates();
    }

    private void selectTemplate(String templateId) {
        this.selectedTemplateId = templateId;
        recreateWidgets();
    }

    private void recreateWidgets() {
        clearWidgets();
        init();
    }

    private void applyTemplate() {
        if (selectedTemplateId == null || selectedTemplateId.isEmpty()) {
            return;
        }

        ApplyTemplatePayload payload = new ApplyTemplatePayload(
            selectedTemplateId, minX, minZ, maxX, maxZ, floorY, false
        );
        PacketDistributor.sendToServer(payload);
        onClose();
    }

    private void clearAndApplyTemplate() {
        if (selectedTemplateId == null || selectedTemplateId.isEmpty()) {
            return;
        }

        ApplyTemplatePayload payload = new ApplyTemplatePayload(
            selectedTemplateId, minX, minZ, maxX, maxZ, floorY, true
        );
        PacketDistributor.sendToServer(payload);
        onClose();
    }

    private void updateButtonStates() {
        if (applyButton != null) {
            applyButton.setEnabled(selectedTemplateId != null && !selectedTemplateId.isEmpty());
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Get zone color
        ZoneTemplate zone = ZoneTemplate.fromString(zoneId);
        int borderColor = zone.getColorWithAlpha();
        int sEditorWidth = s(EDITOR_WIDTH);
        int sEditorHeight = s(EDITOR_HEIGHT);
        int sPadding = s(PADDING);
        AxiomRenderer.drawPanelWithHeader(
            graphics,
            font,
            leftPos,
            topPos,
            sEditorWidth,
            sEditorHeight,
            this.title.getString(),
            DesignTokens.Background.PANEL,
            DesignTokens.Background.HEADER,
            borderColor,
            borderColor
        );

        // Zone info
        int infoY = topPos + s(DesignTokens.Spacing.HEADER_HEIGHT) + s(DesignTokens.Spacing.SM);
        String info = String.format("Zone: %s | Size: %dx%d | Templates: %d",
            zoneId, maxX - minX, maxZ - minZ, availableTemplates.size());
        UIScaleManager.drawScaledString(graphics, this.font, info, leftPos + sPadding, infoY, DesignTokens.Text.SECONDARY, false);

        // Selected template info
        if (selectedTemplateId != null && !selectedTemplateId.isEmpty()) {
            UIScaleManager.drawScaledString(graphics, this.font, "Selected: " + selectedTemplateId,
                leftPos + sPadding, infoY + s(DesignTokens.Spacing.SM), DesignTokens.Text.PRIMARY, false);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (availableTemplates.size() > 8) {
            int maxScroll = availableTemplates.size() - 8;
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            recreateWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
