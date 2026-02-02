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

    private record Layout(int contentWidth, int contentX, int contentY, int contentHeight,
                          int categoryWidth, int panelGap, int rowHeight, int headerHeight,
                          int bottomBarHeight, int buttonWidth, int buttonHeight, int buttonGap, int buttonY) {}

    private Layout layout() {
        int contentWidth = Math.min(UIScaleManager.scale(CONTENT_WIDTH), this.width - UIScaleManager.scale(32));
        int contentX = (this.width - contentWidth) / 2;
        int contentY = UIScaleManager.scale(28);
        int bottomBarHeight = UIScaleManager.scale(BOTTOM_BAR_HEIGHT);
        int contentHeight = this.height - contentY - bottomBarHeight;
        int categoryWidth = UIScaleManager.scale(CATEGORY_WIDTH);
        int panelGap = UIScaleManager.scale(PANEL_GAP);
        int rowHeight = UIScaleManager.scale(ROW_HEIGHT);
        int headerHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int buttonWidth = UIScaleManager.scale(110);
        int buttonHeight = UIScaleManager.scale(DesignTokens.Size.BUTTON_HEIGHT);
        int buttonGap = UIScaleManager.scale(10);
        int buttonY = this.height - UIScaleManager.scale(32);
        return new Layout(contentWidth, contentX, contentY, contentHeight, categoryWidth, panelGap, rowHeight,
            headerHeight, bottomBarHeight, buttonWidth, buttonHeight, buttonGap, buttonY);
    }

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
        Layout layout = layout();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        Font safeFont = getSafeFont();

        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);
        AxiomRenderer.drawCenteredTitle(graphics, safeFont, this.width, UIScaleManager.scale(8), "Telemetry Logs");

        int leftX = layout.contentX();
        int leftY = layout.contentY();
        int leftHeight = layout.contentHeight();
        int rightX = leftX + layout.categoryWidth() + layout.panelGap();
        int rightY = layout.contentY();
        int rightWidth = layout.contentWidth() - layout.categoryWidth() - layout.panelGap();
        int rightHeight = layout.contentHeight();

        AxiomRenderer.drawSimplePanel(graphics, leftX, leftY, layout.categoryWidth(), leftHeight);
        AxiomRenderer.drawSimplePanel(graphics, rightX, rightY, rightWidth, rightHeight);

        renderCategoryList(graphics, safeFont, leftX, leftY, leftHeight, layout);
        renderLogPanel(graphics, safeFont, rightX, rightY, rightWidth, rightHeight, layout);

        int totalWidth = (layout.buttonWidth() * 2) + layout.buttonGap();
        int buttonX = (this.width - totalWidth) / 2;
        backButton.render(graphics, buttonX, layout.buttonY(), layout.buttonWidth(), layout.buttonHeight(), mouseX, mouseY);
        refreshButton.render(graphics, buttonX + layout.buttonWidth() + layout.buttonGap(), layout.buttonY(),
            layout.buttonWidth(), layout.buttonHeight(), mouseX, mouseY);
    }

    private void renderCategoryList(GuiGraphics graphics, @Nonnull Font font, int x, int y, int height, Layout layout) {
        Font safeFont = Objects.requireNonNull(font, "font");
        NexusLogType[] types = NexusLogType.values();
        int maxVisible = Math.max(1, (height - UIScaleManager.scale(12)) / layout.rowHeight());
        int maxScroll = Math.max(0, types.length - maxVisible);
        categoryScroll = Math.max(0, Math.min(categoryScroll, maxScroll));
        int selectedIndex = currentType.ordinal();
        if (selectedIndex < categoryScroll) {
            categoryScroll = selectedIndex;
        } else if (selectedIndex >= categoryScroll + maxVisible) {
            categoryScroll = Math.min(maxScroll, selectedIndex - maxVisible + 1);
        }

        int rowY = y + UIScaleManager.scale(6);
        for (int i = 0; i < maxVisible; i++) {
            int index = categoryScroll + i;
            if (index >= types.length) {
                break;
            }
            NexusLogType type = types[index];
            boolean selected = type == currentType;
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x + UIScaleManager.scale(4), rowY,
                layout.categoryWidth() - UIScaleManager.scale(8), layout.rowHeight() - UIScaleManager.scale(2));
            int bgColor = selected
                ? DesignTokens.Background.ACTIVE()
                : (hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL());
            graphics.fill(x + UIScaleManager.scale(4), rowY, x + layout.categoryWidth() - UIScaleManager.scale(4),
                rowY + layout.rowHeight() - UIScaleManager.scale(2), bgColor);
            int textColor = selected ? DesignTokens.Text.ACCENT() : DesignTokens.Text.PRIMARY();
            String label = Objects.requireNonNull(type.label(), "label");
            int maxLabelWidth = layout.categoryWidth() - UIScaleManager.scale(20);
            if (UIScaleManager.getScaledStringWidth(safeFont, label) > maxLabelWidth) {
                String trimmed = Objects.requireNonNull(
                    safeFont.plainSubstrByWidth(label, Math.max(0, maxLabelWidth - UIScaleManager.getScaledStringWidth(safeFont, "..."))),
                    "trimmed");
                label = trimmed + "...";
            }
            UIScaleManager.drawScaledString(graphics, safeFont, label, x + UIScaleManager.scale(10),
                rowY + UIScaleManager.scale(6), textColor, false);
            rowY += layout.rowHeight();
        }
    }

    private void renderLogPanel(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, Layout layout) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + layout.headerHeight(), DesignTokens.Background.HEADER());
        String header = currentType.label() + " (" + (sourceLabel.isBlank() ? currentType.fileName() : sourceLabel) + ")";
        UIScaleManager.drawScaledString(graphics, font, header, x + UIScaleManager.scale(8),
            y + UIScaleManager.scale(7), DesignTokens.Text.PRIMARY(), false);

        String countLabel = truncated
            ? "Last " + lines.size() + " lines"
            : lines.size() + " lines";
        int countWidth = UIScaleManager.getScaledStringWidth(font, countLabel);
        UIScaleManager.drawScaledString(graphics, font, countLabel, x + width - countWidth - UIScaleManager.scale(8),
            y + UIScaleManager.scale(7), DesignTokens.Text.MUTED(), false);

        int listY = y + layout.headerHeight() + UIScaleManager.scale(6);
        int listHeight = height - layout.headerHeight() - UIScaleManager.scale(10);
        int lineHeight = Math.max(font.lineHeight + UIScaleManager.scale(2), UIScaleManager.getScaledLineHeight());
        int maxVisible = Math.max(1, listHeight / lineHeight);
        int maxScroll = Math.max(0, lines.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (lines.isEmpty()) {
            UIScaleManager.drawScaledString(graphics, font, "No log data available.", x + UIScaleManager.scale(10),
                listY + UIScaleManager.scale(4), DesignTokens.Text.MUTED(), false);
            return;
        }

        int end = Math.min(lines.size(), scrollOffset + maxVisible);
        int rowY = listY;
        for (int i = scrollOffset; i < end; i++) {
            String line = Objects.requireNonNull(lines.get(i), "line");
            UIScaleManager.drawScaledString(graphics, font, line, x + UIScaleManager.scale(10), rowY, DesignTokens.Text.SECONDARY(), false);
            rowY += lineHeight;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        Layout layout = layout();
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (refreshButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int contentX = layout.contentX();
        int contentY = layout.contentY();
        int leftX = contentX;
        int leftY = contentY;
        int leftHeight = layout.contentHeight();
        NexusLogType[] types = NexusLogType.values();
        int maxVisible = Math.max(1, (leftHeight - UIScaleManager.scale(12)) / layout.rowHeight());
        int index = (int) ((mouseY - (leftY + UIScaleManager.scale(6))) / layout.rowHeight());
        if (index >= 0 && index < maxVisible
            && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, leftX + UIScaleManager.scale(4),
                leftY + UIScaleManager.scale(6) + (index * layout.rowHeight()),
                layout.categoryWidth() - UIScaleManager.scale(8), layout.rowHeight() - UIScaleManager.scale(2))) {
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
        UIScaleManager.update();
        Layout layout = layout();
        int contentX = layout.contentX();
        int contentY = layout.contentY();
        int leftX = contentX;
        int leftHeight = layout.contentHeight();
        int rightX = contentX + layout.categoryWidth() + layout.panelGap();
        int rightWidth = layout.contentWidth() - layout.categoryWidth() - layout.panelGap();
        int rightHeight = layout.contentHeight();

        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, leftX, contentY, layout.categoryWidth(), leftHeight)) {
            NexusLogType[] types = NexusLogType.values();
            int maxVisible = Math.max(1, (leftHeight - UIScaleManager.scale(12)) / layout.rowHeight());
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
