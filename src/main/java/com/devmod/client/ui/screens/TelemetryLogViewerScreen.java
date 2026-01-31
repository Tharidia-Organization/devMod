package com.devmod.client.ui.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.runtime.network.NexusLogRequestPayload;
import com.devmod.runtime.network.NexusLogSnapshotPayload;
import com.devmod.runtime.network.NexusLogType;

@OnlyIn(Dist.CLIENT)
public class TelemetryLogViewerScreen extends Screen {
    private static final int CONTENT_WIDTH = 680;
    private static final int PANEL_GAP = 8;
    private static final int CATEGORY_WIDTH = 170;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 24;
    private static final int BOTTOM_BAR_HEIGHT = 40;

    private static NexusLogType lastType = NexusLogType.HITS;

    @Nullable
    private final Screen parent;
    private NexusLogType currentType;
    private List<String> lines = new ArrayList<>();
    private boolean truncated;
    private String sourceLabel = "";
    private int scrollOffset;
    private int categoryScroll;
    private int mouseX;
    private int mouseY;

    private final EditorButton backButton = new EditorButton("log-back", "Back");
    private final EditorButton refreshButton = new EditorButton("log-refresh", "Refresh").style(EditorButton.Style.PRIMARY);

    public TelemetryLogViewerScreen(@Nullable Screen parent) {
        super(Component.literal("Telemetry Logs"));
        this.parent = parent;
        this.currentType = lastType;
    }

    @Override
    protected void init() {
        // Initialize button callbacks here to avoid this-escape in constructor
        backButton.onClick(this::onClose);
        refreshButton.onClick(this::requestCurrent);
        requestCurrent();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        Font safeFont = getSafeFont();

        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);
        AxiomRenderer.drawCenteredTitle(graphics, safeFont, this.width, 8, "Telemetry Logs");

        int contentWidth = Math.min(CONTENT_WIDTH, this.width - 32);
        int contentX = (this.width - contentWidth) / 2;
        int contentY = 28;
        int contentHeight = this.height - contentY - BOTTOM_BAR_HEIGHT;

        int leftX = contentX;
        int leftY = contentY;
        int leftHeight = contentHeight;
        int rightX = leftX + CATEGORY_WIDTH + PANEL_GAP;
        int rightY = contentY;
        int rightWidth = contentWidth - CATEGORY_WIDTH - PANEL_GAP;
        int rightHeight = contentHeight;

        AxiomRenderer.drawSimplePanel(graphics, leftX, leftY, CATEGORY_WIDTH, leftHeight);
        AxiomRenderer.drawSimplePanel(graphics, rightX, rightY, rightWidth, rightHeight);

        renderCategoryList(graphics, safeFont, leftX, leftY, leftHeight);
        renderLogPanel(graphics, safeFont, rightX, rightY, rightWidth, rightHeight);

        int buttonY = this.height - 32;
        int buttonWidth = 110;
        int gap = 10;
        int totalWidth = (buttonWidth * 2) + gap;
        int buttonX = (this.width - totalWidth) / 2;
        backButton.render(graphics, buttonX, buttonY, buttonWidth, DesignTokens.Size.BUTTON_HEIGHT, mouseX, mouseY);
        refreshButton.render(graphics, buttonX + buttonWidth + gap, buttonY, buttonWidth,
            DesignTokens.Size.BUTTON_HEIGHT, mouseX, mouseY);
    }

    private void renderCategoryList(GuiGraphics graphics, @Nonnull Font font, int x, int y, int height) {
        Font safeFont = Objects.requireNonNull(font, "font");
        NexusLogType[] types = NexusLogType.values();
        int maxVisible = Math.max(1, (height - 12) / ROW_HEIGHT);
        int maxScroll = Math.max(0, types.length - maxVisible);
        categoryScroll = Math.max(0, Math.min(categoryScroll, maxScroll));
        int selectedIndex = currentType.ordinal();
        if (selectedIndex < categoryScroll) {
            categoryScroll = selectedIndex;
        } else if (selectedIndex >= categoryScroll + maxVisible) {
            categoryScroll = Math.min(maxScroll, selectedIndex - maxVisible + 1);
        }

        int rowY = y + 6;
        for (int i = 0; i < maxVisible; i++) {
            int index = categoryScroll + i;
            if (index >= types.length) {
                break;
            }
            NexusLogType type = types[index];
            boolean selected = type == currentType;
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x + 4, rowY, CATEGORY_WIDTH - 8, ROW_HEIGHT - 2);
            int bgColor = selected
                ? DesignTokens.Background.ACTIVE()
                : (hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL());
            graphics.fill(x + 4, rowY, x + CATEGORY_WIDTH - 4, rowY + ROW_HEIGHT - 2, bgColor);
            int textColor = selected ? DesignTokens.Text.ACCENT() : DesignTokens.Text.PRIMARY();
            String label = Objects.requireNonNull(type.label(), "label");
            int maxLabelWidth = CATEGORY_WIDTH - 20;
            if (UIScaleManager.getScaledStringWidth(safeFont, label) > maxLabelWidth) {
                String trimmed = Objects.requireNonNull(
                    safeFont.plainSubstrByWidth(label, Math.max(0, maxLabelWidth - UIScaleManager.getScaledStringWidth(safeFont, "..."))),
                    "trimmed");
                label = trimmed + "...";
            }
            UIScaleManager.drawScaledString(graphics, safeFont, label, x + 10, rowY + 6, textColor, false);
            rowY += ROW_HEIGHT;
        }
    }

    private void renderLogPanel(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + HEADER_HEIGHT, DesignTokens.Background.HEADER());
        String header = currentType.label() + " (" + (sourceLabel.isBlank() ? currentType.fileName() : sourceLabel) + ")";
        UIScaleManager.drawScaledString(graphics, font, header, x + 8, y + 7, DesignTokens.Text.PRIMARY(), false);

        String countLabel = truncated
            ? "Last " + lines.size() + " lines"
            : lines.size() + " lines";
        int countWidth = UIScaleManager.getScaledStringWidth(font, countLabel);
        UIScaleManager.drawScaledString(graphics, font, countLabel, x + width - countWidth - 8, y + 7, DesignTokens.Text.MUTED(), false);

        int listY = y + HEADER_HEIGHT + 6;
        int listHeight = height - HEADER_HEIGHT - 10;
        int lineHeight = font.lineHeight + 2;
        int maxVisible = Math.max(1, listHeight / lineHeight);
        int maxScroll = Math.max(0, lines.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (lines.isEmpty()) {
            UIScaleManager.drawScaledString(graphics, font, "No log data available.", x + 10, listY + 4, DesignTokens.Text.MUTED(), false);
            return;
        }

        int end = Math.min(lines.size(), scrollOffset + maxVisible);
        int rowY = listY;
        for (int i = scrollOffset; i < end; i++) {
            String line = Objects.requireNonNull(lines.get(i), "line");
            UIScaleManager.drawScaledString(graphics, font, line, x + 10, rowY, DesignTokens.Text.SECONDARY(), false);
            rowY += lineHeight;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (refreshButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int contentWidth = Math.min(CONTENT_WIDTH, this.width - 32);
        int contentX = (this.width - contentWidth) / 2;
        int contentY = 28;
        int leftX = contentX;
        int leftY = contentY;

        int leftHeight = this.height - contentY - BOTTOM_BAR_HEIGHT;
        NexusLogType[] types = NexusLogType.values();
        int maxVisible = Math.max(1, (leftHeight - 12) / ROW_HEIGHT);
        int index = (int) ((mouseY - (leftY + 6)) / ROW_HEIGHT);
        if (index >= 0 && index < maxVisible
            && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, leftX + 4,
                leftY + 6 + (index * ROW_HEIGHT), CATEGORY_WIDTH - 8, ROW_HEIGHT - 2)) {
            int typeIndex = categoryScroll + index;
            if (typeIndex >= 0 && typeIndex < types.length) {
                requestLog(types[typeIndex]);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        handled |= backButton.mouseReleased(mouseX, mouseY, button);
        handled |= refreshButton.mouseReleased(mouseX, mouseY, button);
        if (handled) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentWidth = Math.min(CONTENT_WIDTH, this.width - 32);
        int contentX = (this.width - contentWidth) / 2;
        int contentY = 28;
        int contentHeight = this.height - contentY - BOTTOM_BAR_HEIGHT;
        int leftX = contentX;
        int leftHeight = contentHeight;
        int rightX = contentX + CATEGORY_WIDTH + PANEL_GAP;
        int rightWidth = contentWidth - CATEGORY_WIDTH - PANEL_GAP;
        int rightHeight = contentHeight;

        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, leftX, contentY, CATEGORY_WIDTH, leftHeight)) {
            NexusLogType[] types = NexusLogType.values();
            int maxVisible = Math.max(1, (leftHeight - 12) / ROW_HEIGHT);
            int maxScroll = Math.max(0, types.length - maxVisible);
            if (maxScroll > 0) {
                int step = scrollY > 0 ? -1 : 1;
                categoryScroll = Math.max(0, Math.min(categoryScroll + step, maxScroll));
                return true;
            }
        }

        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, rightX, contentY, rightWidth, rightHeight)) {
            int lineStep = scrollY > 0 ? -1 : 1;
            scrollOffset += lineStep;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void requestCurrent() {
        requestLog(currentType);
    }

    private void requestLog(NexusLogType type) {
        currentType = type;
        lastType = type;
        scrollOffset = 0;
        lines = List.of("Loading...");
        PacketDistributor.sendToServer(new NexusLogRequestPayload(type));
    }

    @Nonnull
    private Font getSafeFont() {
        return Objects.requireNonNull(font, "font");
    }

    private void applySnapshotData(NexusLogSnapshotPayload payload) {
        currentType = payload.logType();
        lastType = payload.logType();
        lines = new ArrayList<>(payload.lines());
        truncated = payload.truncated();
        sourceLabel = payload.sourceLabel();
        scrollOffset = 0;
    }

    public static void applySnapshot(NexusLogSnapshotPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TelemetryLogViewerScreen screen) {
            screen.applySnapshotData(payload);
        }
    }
}
