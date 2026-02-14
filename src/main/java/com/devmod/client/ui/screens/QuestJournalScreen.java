package com.devmod.client.ui.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.quest.Quest;
import com.devmod.quest.QuestObjective;
import com.devmod.quest.QuestProgress;
import com.devmod.quest.QuestRegistry;
import com.devmod.quest.QuestReward;
import com.devmod.quest.QuestStatus;
import com.devmod.util.I18n;

/**
 * Quest Journal screen with left panel (quest list with categories)
 * and right panel (selected quest details with objectives and rewards).
 */
@OnlyIn(Dist.CLIENT)
public class QuestJournalScreen extends Screen {

    // Colors
    private static final int BG_COLOR = DesignTokens.Bg.LEVEL_1;
    private static final int PANEL_BG = DesignTokens.Bg.LEVEL_2;
    private static final int PANEL_SELECTED = DesignTokens.Surface.LEVEL_2;
    private static final int PANEL_HOVER = DesignTokens.Surface.LEVEL_1;
    private static final int BORDER_COLOR = DesignTokens.Stroke.DEFAULT;
    private static final int TEXT_PRIMARY = DesignTokens.Text.PRIMARY;
    private static final int TEXT_SECONDARY = DesignTokens.Text.SECONDARY;
    private static final int TEXT_MUTED = DesignTokens.Text.MUTED;

    private static final int COLOR_ACTIVE = DesignTokens.Semantic.WARNING;
    private static final int COLOR_COMPLETED = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_AVAILABLE = DesignTokens.Text.PRIMARY;
    private static final int COLOR_LOCKED = DesignTokens.Text.DISABLED;
    private static final int COLOR_FAILED = DesignTokens.Semantic.ERROR;

    private static final int PROGRESS_BG = OverlayTheme.Progress.BG;
    private static final int PROGRESS_FILL = OverlayTheme.Progress.FILL;
    private static final int PROGRESS_FILL_COMPLETE = OverlayTheme.Progress.FILL_GREEN;

    // Layout (base values, scaled via UIScaleManager)
    private static final int BASE_MARGIN = 20;
    private static final int BASE_LEFT_PANEL_WIDTH = 180;
    private static final int BASE_PANEL_GAP = 6;
    private static final int BASE_ENTRY_HEIGHT = 22;
    private static final int BASE_HEADER_HEIGHT = 20;

    // Tab indices
    private static final int TAB_ACTIVE = 0;
    private static final int TAB_AVAILABLE = 1;
    private static final int TAB_COMPLETED = 2;

    @Nullable
    private final Screen parent;
    private int currentTab = TAB_ACTIVE;
    private int leftScrollOffset = 0;
    private int rightScrollOffset = 0;

    // Cached data
    private final List<QuestListEntry> displayedQuests = new ArrayList<>();
    @Nullable
    private QuestListEntry selectedEntry;

    // Layout bounds (computed in init via UIScaleManager)
    private int contentLeft, contentTop, contentWidth, contentHeight;
    private int leftPanelRight, leftPanelWidth;
    private int rightPanelLeft, rightPanelWidth;
    private int margin, panelGap, entryHeight, headerHeight;

    public QuestJournalScreen(@Nullable Screen parent) {
        super(Component.translatable("devmod.quest.journal.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        UIScaleManager.update();

        margin = UIScaleManager.scale(BASE_MARGIN);
        leftPanelWidth = UIScaleManager.scale(BASE_LEFT_PANEL_WIDTH);
        panelGap = UIScaleManager.scaleMin(BASE_PANEL_GAP, UIScaleManager.MIN_GAP);
        entryHeight = UIScaleManager.scale(BASE_ENTRY_HEIGHT);
        headerHeight = UIScaleManager.scale(BASE_HEADER_HEIGHT);

        contentLeft = margin;
        contentTop = margin + UIScaleManager.scale(20);
        contentWidth = width - margin * 2;
        contentHeight = height - margin * 2 - UIScaleManager.scale(20);

        leftPanelRight = contentLeft + leftPanelWidth;
        rightPanelLeft = leftPanelRight + panelGap;
        rightPanelWidth = contentLeft + contentWidth - rightPanelLeft;

        // Tab buttons
        int tabY = contentTop - UIScaleManager.scale(18);
        int tabWidth = UIScaleManager.scale(60);
        int tabGap = UIScaleManager.scale(4);
        int tabH = UIScaleManager.scale(16);
        addRenderableWidget(Button.builder(I18n.translate("devmod.quest.journal.tab.active"), b -> setTab(TAB_ACTIVE))
            .bounds(contentLeft, tabY, tabWidth, tabH).build());
        addRenderableWidget(Button.builder(I18n.translate("devmod.quest.journal.tab.available"), b -> setTab(TAB_AVAILABLE))
            .bounds(contentLeft + tabWidth + tabGap, tabY, tabWidth, tabH).build());
        addRenderableWidget(Button.builder(I18n.translate("devmod.quest.journal.tab.done"), b -> setTab(TAB_COMPLETED))
            .bounds(contentLeft + (tabWidth + tabGap) * 2, tabY, tabWidth, tabH).build());

        refreshQuestList();
    }

    private void setTab(int tab) {
        currentTab = tab;
        leftScrollOffset = 0;
        selectedEntry = null;
        refreshQuestList();
    }

    private void refreshQuestList() {
        displayedQuests.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID playerId = mc.player.getUUID();

        switch (currentTab) {
            case TAB_ACTIVE -> {
                List<QuestProgress> active = QuestRegistry.INSTANCE.getActiveQuests(playerId);
                for (QuestProgress p : active) {
                    Quest quest = QuestRegistry.INSTANCE.getQuest(p.getQuestId());
                    if (quest != null) {
                        displayedQuests.add(new QuestListEntry(quest, p, QuestStatus.ACTIVE));
                    }
                }
            }
            case TAB_AVAILABLE -> {
                List<Quest> available = QuestRegistry.INSTANCE.getAvailableQuests(playerId);
                for (Quest quest : available) {
                    displayedQuests.add(new QuestListEntry(quest, null, QuestStatus.NOT_STARTED));
                }
            }
            case TAB_COMPLETED -> {
                List<QuestProgress> completed = QuestRegistry.INSTANCE.getCompletedQuests(playerId);
                for (QuestProgress p : completed) {
                    Quest quest = QuestRegistry.INSTANCE.getQuest(p.getQuestId());
                    if (quest != null) {
                        displayedQuests.add(new QuestListEntry(quest, p, QuestStatus.COMPLETED));
                    }
                }
            }
        }

        // Auto-select first entry
        if (!displayedQuests.isEmpty() && selectedEntry == null) {
            selectedEntry = displayedQuests.get(0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();

        // Background
        graphics.fill(0, 0, width, height, DesignTokens.withAlpha(DesignTokens.Palette.SHADOW, 0xC0));

        // Title
        graphics.drawCenteredString(font, title, width / 2, margin / 2 + 2, TEXT_PRIMARY);

        // Left panel background
        graphics.fill(contentLeft, contentTop, leftPanelRight, contentTop + contentHeight, PANEL_BG);
        graphics.renderOutline(contentLeft, contentTop, leftPanelWidth, contentHeight, BORDER_COLOR);

        // Right panel background
        graphics.fill(rightPanelLeft, contentTop, rightPanelLeft + rightPanelWidth, contentTop + contentHeight, PANEL_BG);
        graphics.renderOutline(rightPanelLeft, contentTop, rightPanelWidth, contentHeight, BORDER_COLOR);

        // Render left panel (quest list)
        renderQuestList(graphics, mouseX, mouseY);

        // Render right panel (quest details)
        renderQuestDetails(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderQuestList(GuiGraphics graphics, int mouseX, int mouseY) {
        int pad = UIScaleManager.scaleMin(4, UIScaleManager.MIN_PADDING);
        int x = contentLeft + pad;
        int y = contentTop + pad;
        int listWidth = leftPanelWidth - pad * 2;

        // Tab indicator
        String tabLabel = switch (currentTab) {
            case TAB_ACTIVE -> I18n.translate("devmod.quest.journal.header.active").getString();
            case TAB_AVAILABLE -> I18n.translate("devmod.quest.journal.header.available").getString();
            case TAB_COMPLETED -> I18n.translate("devmod.quest.journal.header.completed").getString();
            default -> I18n.translate("devmod.quest.journal.header.default").getString();
        };
        int tabColor = switch (currentTab) {
            case TAB_ACTIVE -> COLOR_ACTIVE;
            case TAB_COMPLETED -> COLOR_COMPLETED;
            default -> COLOR_AVAILABLE;
        };
        graphics.drawString(font, tabLabel, x, y, tabColor);
        y += headerHeight;

        if (displayedQuests.isEmpty()) {
            graphics.drawString(font, I18n.translate("devmod.quest.journal.no_quests").getString(), x + pad, y + pad * 2, TEXT_MUTED);
            return;
        }

        // Quest entries
        int maxVisible = (contentHeight - headerHeight - pad * 2) / entryHeight;
        int start = Math.min(leftScrollOffset, Math.max(0, displayedQuests.size() - maxVisible));

        for (int i = start; i < displayedQuests.size() && i < start + maxVisible; i++) {
            QuestListEntry entry = displayedQuests.get(i);
            int entryY = y + (i - start) * entryHeight;

            boolean isSelected = entry == selectedEntry;
            boolean isHovered = mouseX >= x && mouseX < x + listWidth
                && mouseY >= entryY && mouseY < entryY + entryHeight;

            // Entry background
            if (isSelected) {
                graphics.fill(x, entryY, x + listWidth, entryY + entryHeight, PANEL_SELECTED);
            } else if (isHovered) {
                graphics.fill(x, entryY, x + listWidth, entryY + entryHeight, PANEL_HOVER);
            }

            // Status indicator dot
            int statusColor = getStatusColor(entry.status);
            graphics.fill(x + 2, entryY + 7, x + 6, entryY + 11, statusColor);

            // Quest name
            String name = entry.quest.getName().getString();
            if (font.width(name) > listWidth - 16) {
                name = font.plainSubstrByWidth(name, listWidth - 20) + "..";
            }
            graphics.drawString(font, name, x + 10, entryY + 4, statusColor);

            // Progress indicator for active quests
            if (entry.progress != null && entry.status == QuestStatus.ACTIVE) {
                String progressText = entry.progress.getProgressSummary();
                int progressWidth = font.width(progressText);
                graphics.drawString(font, progressText, x + listWidth - progressWidth - 2, entryY + 4, TEXT_MUTED);
            }

            // Tracked indicator
            if (entry.progress != null && entry.progress.isTracked()) {
                graphics.drawString(font, "\u2022", x + listWidth - 6, entryY + 2, COLOR_ACTIVE);
            }
        }

        // Scroll indicators
        if (start > 0) {
            graphics.drawCenteredString(font, "\u25B2", contentLeft + leftPanelWidth / 2, contentTop + headerHeight, TEXT_MUTED);
        }
        if (start + maxVisible < displayedQuests.size()) {
            graphics.drawCenteredString(font, "\u25BC", contentLeft + leftPanelWidth / 2, contentTop + contentHeight - 10, TEXT_MUTED);
        }
    }

    private void renderQuestDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = rightPanelLeft + 8;
        int y = contentTop + 8 - rightScrollOffset * 10;
        int detailWidth = rightPanelWidth - 16;

        if (selectedEntry == null) {
            graphics.drawCenteredString(font, I18n.translate("devmod.quest.journal.select_quest").getString(), rightPanelLeft + rightPanelWidth / 2, contentTop + 48, TEXT_MUTED);
            return;
        }

        // Scissor clip to prevent rendering outside the detail panel
        graphics.enableScissor(rightPanelLeft, contentTop + headerHeight, rightPanelLeft + rightPanelWidth, contentTop + contentHeight);

        Quest quest = selectedEntry.quest;
        QuestProgress progress = selectedEntry.progress;

        // Quest name
        graphics.drawString(font, quest.getName(), x, y, TEXT_PRIMARY);
        y += 14;

        // Category
        if (quest.getCategory() != null) {
            graphics.drawString(font, "[" + quest.getCategory() + "]", x, y, TEXT_MUTED);
            y += 12;
        }

        // Status badge
        String statusText = selectedEntry.status.name().replace("_", " ");
        int statusColor = getStatusColor(selectedEntry.status);
        graphics.drawString(font, statusText, x, y, statusColor);
        y += 14;

        // Separator
        graphics.fill(x, y, x + detailWidth, y + 1, BORDER_COLOR);
        y += 6;

        // Description
        List<String> descLines = wrapText(quest.getDescription().getString(), detailWidth);
        for (String line : descLines) {
            graphics.drawString(font, line, x, y, TEXT_SECONDARY);
            y += 10;
        }
        y += 6;

        // Objectives header
        graphics.drawString(font, I18n.translate("devmod.quest.journal.objectives").getString(), x, y, TEXT_PRIMARY);
        y += 12;

        List<QuestObjective> objectives = progress != null ? progress.getObjectives() : quest.getObjectives();
        for (QuestObjective obj : objectives) {
            // Checkbox indicator
            String check = obj.isComplete() ? "\u2713 " : "\u25CB ";
            int objColor = obj.isComplete() ? COLOR_COMPLETED : TEXT_SECONDARY;
            graphics.drawString(font, check + obj.description().getString(), x + 4, y, objColor);
            y += 11;

            // Progress bar for incomplete objectives
            if (!obj.isComplete() && progress != null) {
                int barX = x + 14;
                int barWidth = Math.min(detailWidth - 60, 120);
                int barHeight = 4;

                graphics.fill(barX, y, barX + barWidth, y + barHeight, PROGRESS_BG);
                int fillWidth = (int) (barWidth * obj.progress());
                if (fillWidth > 0) {
                    graphics.fill(barX, y, barX + fillWidth, y + barHeight, PROGRESS_FILL);
                }

                // Percentage
                String pct = String.format("%.0f%%", obj.progress() * 100);
                graphics.drawString(font, pct, barX + barWidth + 4, y - 2, TEXT_MUTED);
                y += 8;
            }
        }

        y += 8;

        // Rewards section
        QuestReward reward = quest.getReward();
        if (!reward.isEmpty()) {
            graphics.fill(x, y, x + detailWidth, y + 1, BORDER_COLOR);
            y += 6;

            graphics.drawString(font, I18n.translate("devmod.quest.journal.rewards").getString(), x, y, TEXT_PRIMARY);
            y += 12;

            if (reward.hasExperience()) {
                graphics.drawString(font, "  " + reward.experiencePoints() + " XP", x, y, DesignTokens.Semantic.SUCCESS);
                y += 10;
            }
            if (reward.hasCurrency()) {
                graphics.drawString(font, "  " + reward.currency() + " coins", x, y, COLOR_ACTIVE);
                y += 10;
            }
            if (reward.hasItems()) {
                graphics.drawString(font, "  " + reward.items().size() + " item(s)", x, y, DesignTokens.Accent.CYAN);
                y += 10;
            }
            if (reward.hasUnlocks()) {
                graphics.drawString(font, "  Unlocks " + reward.unlockedQuestIds().size() + " quest(s)", x, y, TEXT_SECONDARY);
                y += 10;
            }
        }

        y += 10;

        // Prerequisites
        if (quest.hasPrerequisites()) {
            graphics.drawString(font, I18n.translate("devmod.quest.journal.requires").getString(), x, y, TEXT_MUTED);
            y += 10;
            for (String reqId : quest.getRequiredQuestIds()) {
                Quest req = QuestRegistry.INSTANCE.getQuest(reqId);
                String reqName = req != null ? req.getName().getString() : reqId;
                graphics.drawString(font, "  - " + reqName, x, y, TEXT_MUTED);
                y += 10;
            }
            y += 4;
        }

        graphics.disableScissor();

        // Action buttons (rendered at bottom of detail panel)
        renderActionButtons(graphics, mouseX, mouseY, selectedEntry);
    }

    private void renderActionButtons(GuiGraphics graphics, int mouseX, int mouseY, QuestListEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int buttonY = contentTop + contentHeight - 28;
        int buttonX = rightPanelLeft + 8;
        int buttonWidth = 70;
        int buttonHeight = 18;
        int gap = 6;

        switch (entry.status) {
            case NOT_STARTED -> {
                // "Accept" button
                boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                    && mouseY >= buttonY && mouseY < buttonY + buttonHeight;
                int bg = hovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
                graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, bg);
                graphics.renderOutline(buttonX, buttonY, buttonWidth, buttonHeight, COLOR_COMPLETED);
                graphics.drawCenteredString(font, I18n.translate("devmod.quest.journal.button.accept").getString(), buttonX + buttonWidth / 2, buttonY + 5, COLOR_COMPLETED);
            }
            case ACTIVE -> {
                // "Track" button
                boolean tracked = entry.progress != null && entry.progress.isTracked();
                String trackLabel = tracked
                    ? I18n.translate("devmod.quest.journal.button.untrack").getString()
                    : I18n.translate("devmod.quest.journal.button.track").getString();
                boolean hovered1 = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                    && mouseY >= buttonY && mouseY < buttonY + buttonHeight;
                int bg1 = hovered1 ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
                graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, bg1);
                graphics.renderOutline(buttonX, buttonY, buttonWidth, buttonHeight, COLOR_ACTIVE);
                graphics.drawCenteredString(font, trackLabel, buttonX + buttonWidth / 2, buttonY + 5, COLOR_ACTIVE);

                // "Abandon" button
                int abandonX = buttonX + buttonWidth + gap;
                boolean hovered2 = mouseX >= abandonX && mouseX < abandonX + buttonWidth
                    && mouseY >= buttonY && mouseY < buttonY + buttonHeight;
                int bg2 = hovered2 ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
                graphics.fill(abandonX, buttonY, abandonX + buttonWidth, buttonY + buttonHeight, bg2);
                graphics.renderOutline(abandonX, buttonY, buttonWidth, buttonHeight, COLOR_FAILED);
                graphics.drawCenteredString(font, I18n.translate("devmod.quest.journal.button.abandon").getString(), abandonX + buttonWidth / 2, buttonY + 5, COLOR_FAILED);
            }
            case COMPLETED -> {
                if (entry.quest.isRepeatable()) {
                    boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                        && mouseY >= buttonY && mouseY < buttonY + buttonHeight;
                    int bg = hovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
                    graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, bg);
                    graphics.renderOutline(buttonX, buttonY, buttonWidth, buttonHeight, DesignTokens.Accent.CYAN);
                    graphics.drawCenteredString(font, I18n.translate("devmod.quest.journal.button.restart").getString(), buttonX + buttonWidth / 2, buttonY + 5, DesignTokens.Accent.CYAN);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check left panel clicks (quest list)
            int pad = UIScaleManager.scaleMin(4, UIScaleManager.MIN_PADDING);
            int x = contentLeft + pad;
            int listWidth = leftPanelWidth - pad * 2;
            int y = contentTop + pad + headerHeight;
            int maxVisible = (contentHeight - headerHeight - pad * 2) / entryHeight;
            int start = Math.min(leftScrollOffset, Math.max(0, displayedQuests.size() - maxVisible));

            for (int i = start; i < displayedQuests.size() && i < start + maxVisible; i++) {
                int entryY = y + (i - start) * entryHeight;
                if (mouseX >= x && mouseX < x + listWidth && mouseY >= entryY && mouseY < entryY + entryHeight) {
                    selectedEntry = displayedQuests.get(i);
                    rightScrollOffset = 0;
                    return true;
                }
            }

            // Check action button clicks
            if (selectedEntry != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    UUID playerId = mc.player.getUUID();
                    int buttonY = contentTop + contentHeight - 28;
                    int buttonX = rightPanelLeft + 8;
                    int buttonWidth = 70;
                    int buttonHeight = 18;

                    if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                        && mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
                        handlePrimaryAction(playerId, selectedEntry);
                        return true;
                    }

                    // Second button (Abandon for active quests)
                    if (selectedEntry.status == QuestStatus.ACTIVE) {
                        int abandonX = buttonX + buttonWidth + 6;
                        if (mouseX >= abandonX && mouseX < abandonX + buttonWidth
                            && mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
                            QuestRegistry.INSTANCE.abandonQuest(playerId, selectedEntry.quest.getId());
                            selectedEntry = null;
                            refreshQuestList();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handlePrimaryAction(UUID playerId, QuestListEntry entry) {
        switch (entry.status) {
            case NOT_STARTED -> {
                QuestRegistry.INSTANCE.startQuest(playerId, entry.quest.getId());
                setTab(TAB_ACTIVE);
            }
            case ACTIVE -> {
                QuestRegistry.INSTANCE.toggleTracking(playerId, entry.quest.getId());
                refreshQuestList();
            }
            case COMPLETED -> {
                if (entry.quest.isRepeatable()) {
                    QuestRegistry.INSTANCE.startQuest(playerId, entry.quest.getId());
                    setTab(TAB_ACTIVE);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < leftPanelRight) {
            leftScrollOffset = Math.max(0, leftScrollOffset - (int) scrollY);
        } else {
            rightScrollOffset = Math.max(0, rightScrollOffset - (int) scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_J || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private int getStatusColor(QuestStatus status) {
        return switch (status) {
            case ACTIVE -> COLOR_ACTIVE;
            case COMPLETED -> COLOR_COMPLETED;
            case NOT_STARTED -> COLOR_AVAILABLE;
            case FAILED -> COLOR_FAILED;
        };
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (font.width(candidate) > maxWidth) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            } else {
                currentLine = new StringBuilder(candidate);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    /**
     * Internal data holder for quest list entries.
     */
    private record QuestListEntry(Quest quest, @Nullable QuestProgress progress, QuestStatus status) {}
}
