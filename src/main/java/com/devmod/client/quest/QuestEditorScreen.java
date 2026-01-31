package com.devmod.client.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.ModScreen;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.StartQuestPayload;
import com.devmod.quest.QuestData;
import com.devmod.quest.QuestManager;
import com.devmod.quest.QuestTask;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class QuestEditorScreen extends ModScreen {

    // Layout constants
    private static final int QUEST_LIST_WIDTH = 150;
    private static final int TASK_LIST_WIDTH = 200;
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 30;

    // UI State
    @Nullable
    private QuestData selectedQuest;
    @Nullable
    private QuestTask selectedTask;
    private int questListScroll = 0;
    private int taskListScroll = 0;

    // Input fields
    @Nullable
    private EditBox questNoteField;
    @Nullable
    private EditBox taskNoteField;
    @Nullable
    private EditBox newQuestNameField;
    @Nullable
    private EditBox newTaskDescField;

    // Button widgets (only stored for state updates - active/enabled toggling)
    @Nullable
    private EditorButtonWidget deleteQuestBtnWidget;
    @Nullable
    private EditorButtonWidget addTaskBtnWidget;
    @Nullable
    private EditorButtonWidget deleteTaskBtnWidget;
    @Nullable
    private EditorButtonWidget completeTaskBtnWidget;
    @Nullable
    private EditorButtonWidget setActiveBtnWidget;

    // Change listener reference for cleanup
    @Nullable
    private Runnable questChangeListener;

    // === Endurance Quest Modal State ===
    private boolean showEnduranceModal = false;
    private List<EnduranceQuestRegistry.MobQuestConfig> availableMobs = new ArrayList<>();
    private List<EnduranceQuestRegistry.MobQuestConfig> filteredMobs = new ArrayList<>();
    @Nullable
    private EnduranceQuestRegistry.MobQuestConfig selectedMob = null;
    private int mobListScroll = 0;
    private int enduranceWaves = 10;
    private boolean enduranceEndless = false;
    @Nullable
    private EditBox mobSearchField;
    // Modal button widgets (EditorButton instances are transient)
    @Nullable
    private EditorButtonWidget startEnduranceBtnWidget;
    @Nullable
    private EditorButtonWidget cancelEnduranceBtnWidget;
    @Nullable
    private EditorButtonWidget wavesMinusBtnWidget;
    @Nullable
    private EditorButtonWidget wavesPlusBtnWidget;
    @Nullable
    private EditorButtonWidget endlessModeBtnWidget;
    @Nullable
    private EditorButtonWidget enduranceQuestBtnWidget;

    // If true, opens directly to endurance modal
    private final boolean openEnduranceModalOnInit;

    public QuestEditorScreen() {
        this(false);
    }

    /**
     * Creates the Quest Editor Screen.
     * @param openEnduranceModal If true, opens the endurance quest modal immediately
     */
    public QuestEditorScreen(boolean openEnduranceModal) {
        super(I18n.screenTitle("quest_editor"), null);
        this.openEnduranceModalOnInit = openEnduranceModal;
    }

    @Override
    protected void init() {
        super.init();

        // Initialize with current active quest
        selectedQuest = QuestManager.INSTANCE.getActiveQuest();
        if (selectedQuest != null) {
            selectedTask = selectedQuest.getCurrentTask();
        }

        // Calculate scaled dimensions for layout
        UIScaleManager.update();
        int sHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int sPadding = UIScaleManager.scale(PADDING);
        int sQuestListWidth = UIScaleManager.scale(QUEST_LIST_WIDTH);
        int sTaskListWidth = UIScaleManager.scale(TASK_LIST_WIDTH);
        int sFieldHeight = UIScaleManager.scale(18);
        int sButtonSize = UIScaleManager.scale(20);

        int contentTop = sHeaderHeight + sPadding;

        // === Quest Note Field ===
        int noteFieldX = sQuestListWidth + sTaskListWidth + sPadding * 3;
        int noteFieldWidth = width - noteFieldX - sPadding;

        EditBox questNoteBox = new EditBox(Objects.requireNonNull(font, "font"), noteFieldX, contentTop + UIScaleManager.scale(40), Math.max(UIScaleManager.scale(100), noteFieldWidth), sFieldHeight + 2, I18n.translate("devmod.quest.quest_note"));
        questNoteBox.setMaxLength(200);
        questNoteBox.setHint(I18n.translate("devmod.quest.quest_note_hint"));
        QuestData currentQuest = selectedQuest;
        if (currentQuest != null && currentQuest.hasQuestNote()) {
            String questNote = currentQuest.getQuestNote();
            if (questNote != null) {
                questNoteBox.setValue(questNote);
            }
        }
        questNoteBox.setResponder(this::onQuestNoteChanged);
        questNoteBox.setBordered(false);
        questNoteBox.setTextColor(DesignTokens.Text.PRIMARY);
        questNoteBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        this.questNoteField = questNoteBox;
        this.addRenderableWidget(questNoteBox);

        // === Task Note Field ===
        EditBox taskNoteBox = new EditBox(Objects.requireNonNull(font, "font"), noteFieldX, contentTop + UIScaleManager.scale(120), Math.max(UIScaleManager.scale(100), noteFieldWidth), sFieldHeight + 2, I18n.translate("devmod.quest.task_note"));
        taskNoteBox.setMaxLength(200);
        taskNoteBox.setHint(I18n.translate("devmod.quest.task_note_hint"));
        QuestTask currentTask = selectedTask;
        if (currentTask != null && currentTask.hasNote()) {
            String taskNote = currentTask.getNote();
            if (taskNote != null) {
                taskNoteBox.setValue(taskNote);
            }
        }
        taskNoteBox.setResponder(this::onTaskNoteChanged);
        taskNoteBox.setBordered(false);
        taskNoteBox.setTextColor(DesignTokens.Text.PRIMARY);
        taskNoteBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        this.taskNoteField = taskNoteBox;
        this.addRenderableWidget(taskNoteBox);

        // === New Quest Name Field ===
        EditBox newQuestBox = new EditBox(Objects.requireNonNull(font), sPadding, height - UIScaleManager.scale(55), sQuestListWidth - UIScaleManager.scale(25), sFieldHeight, I18n.translate("devmod.quest.new_quest"));
        newQuestBox.setMaxLength(50);
        newQuestBox.setHint(I18n.translate("devmod.quest.new_quest_hint"));
        newQuestBox.setBordered(false);
        newQuestBox.setTextColor(DesignTokens.Text.PRIMARY);
        newQuestBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        this.newQuestNameField = newQuestBox;
        this.addRenderableWidget(newQuestBox);

        // === New Task Description Field ===
        int taskFieldX = sQuestListWidth + sPadding * 2;
        EditBox newTaskBox = new EditBox(Objects.requireNonNull(font), taskFieldX, height - UIScaleManager.scale(55), sTaskListWidth - UIScaleManager.scale(25), sFieldHeight, I18n.translate("devmod.quest.new_task"));
        newTaskBox.setMaxLength(100);
        newTaskBox.setHint(I18n.translate("devmod.quest.new_task_hint"));
        newTaskBox.setBordered(false);
        newTaskBox.setTextColor(DesignTokens.Text.PRIMARY);
        newTaskBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        this.newTaskDescField = newTaskBox;
        this.addRenderableWidget(newTaskBox);

        // === Buttons ===
        // Add Quest Button (+) - no state updates needed, just register
        EditorButton addQuestButton = EditorButton.builder("quest-add", I18n.ui("add_symbol").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::addNewQuest)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(addQuestButton, sPadding + sQuestListWidth - UIScaleManager.scale(22), height - UIScaleManager.scale(55), sButtonSize, sFieldHeight));

        // Delete Quest Button
        EditorButton deleteQuestButton = EditorButton.builder("quest-delete", I18n.ui("delete_symbol").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .tooltip(I18n.translate("devmod.quest.delete_quest").getString())
            .onClick(this::deleteSelectedQuest)
            .build();
        EditorButtonWidget deleteQuestWidget = new EditorButtonWidget(deleteQuestButton, sPadding + sQuestListWidth - UIScaleManager.scale(22), contentTop, sButtonSize, sFieldHeight);
        this.deleteQuestBtnWidget = deleteQuestWidget;
        this.addRenderableWidget(deleteQuestWidget);

        // Add Task Button (+)
        EditorButton addTaskButton = EditorButton.builder("task-add", I18n.ui("add_symbol").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::addNewTask)
            .build();
        EditorButtonWidget addTaskWidget = new EditorButtonWidget(addTaskButton, taskFieldX + sTaskListWidth - UIScaleManager.scale(22), height - UIScaleManager.scale(55), sButtonSize, sFieldHeight);
        this.addTaskBtnWidget = addTaskWidget;
        this.addRenderableWidget(addTaskWidget);

        // Delete Task Button
        EditorButton deleteTaskButton = EditorButton.builder("task-delete", I18n.ui("delete_symbol").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .tooltip(I18n.translate("devmod.quest.delete_task").getString())
            .onClick(this::deleteSelectedTask)
            .build();
        EditorButtonWidget deleteTaskWidget = new EditorButtonWidget(deleteTaskButton, taskFieldX + sTaskListWidth - UIScaleManager.scale(22), contentTop, sButtonSize, sFieldHeight);
        this.deleteTaskBtnWidget = deleteTaskWidget;
        this.addRenderableWidget(deleteTaskWidget);

        // Complete Task Button
        EditorButton completeTaskButton = EditorButton.builder("task-complete", I18n.ui("complete_with_icon").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::completeSelectedTask)
            .build();
        EditorButtonWidget completeTaskWidget = new EditorButtonWidget(completeTaskButton, noteFieldX, contentTop + UIScaleManager.scale(150), UIScaleManager.scale(100), sButtonSize);
        this.completeTaskBtnWidget = completeTaskWidget;
        this.addRenderableWidget(completeTaskWidget);

        // Set Active Quest Button
        EditorButton setActiveButton = EditorButton.builder("quest-set-active", I18n.ui("activate_with_icon").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::setActiveQuest)
            .build();
        EditorButtonWidget setActiveWidget = new EditorButtonWidget(setActiveButton, noteFieldX + UIScaleManager.scale(110), contentTop + UIScaleManager.scale(150), UIScaleManager.scale(80), sButtonSize);
        this.setActiveBtnWidget = setActiveWidget;
        this.addRenderableWidget(setActiveWidget);

        // Close Button
        EditorButton closeButton = EditorButton.builder("quest-close", I18n.ui("close").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(closeButton, width / 2 - UIScaleManager.scale(50), height - UIScaleManager.scale(28), UIScaleManager.scale(100), sButtonSize));

        // === NEW: Endurance Quest Button === (no state updates needed)
        EditorButton newEnduranceButton = EditorButton.builder("quest-endurance", I18n.ui("endurance_quest").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .tooltip(I18n.translate("devmod.quest.create_endurance").getString())
            .onClick(this::openEnduranceModal)
            .build();
        EditorButtonWidget newEnduranceWidget = new EditorButtonWidget(newEnduranceButton, sPadding, height - UIScaleManager.scale(28), UIScaleManager.scale(90), sButtonSize);
        this.enduranceQuestBtnWidget = newEnduranceWidget;
        this.addRenderableWidget(newEnduranceWidget);

        // Initialize endurance modal components (hidden by default)
        initEnduranceModalComponents();

        // Register change listener for real-time sync
        questChangeListener = this::onQuestDataChanged;
        QuestManager.INSTANCE.addChangeListener(questChangeListener);

        updateButtonStates();

        // If requested, open endurance modal immediately
        if (openEnduranceModalOnInit) {
            openEnduranceModal();
        }
    }

    /*
     * Initializes Endurance Quest modal components.
     */
    private void initEnduranceModalComponents() {
        // Load available mobs
        availableMobs = new ArrayList<>(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());
        availableMobs.sort(Comparator.comparing(m -> m.getDisplayName()));
        filteredMobs = new ArrayList<>(availableMobs);

        int sModalWidth = UIScaleManager.scale(400);
        int sModalHeight = UIScaleManager.scale(300);
        int modalX = (width - sModalWidth) / 2;
        int modalY = (height - sModalHeight) / 2;
        int sFieldHeight = UIScaleManager.scale(18);
        int sButtonSize = UIScaleManager.scale(20);
        int sPadding = UIScaleManager.scale(10);

        // Search field
        EditBox searchBox = new EditBox(Objects.requireNonNull(font), modalX + sPadding, modalY + UIScaleManager.scale(35), sModalWidth - sPadding * 2, sFieldHeight, I18n.ui("search"));
        searchBox.setHint(I18n.translate("devmod.quest.search_mobs"));
        searchBox.setResponder(this::filterMobs);
        searchBox.setVisible(false);
        searchBox.setBordered(false);
        searchBox.setTextColor(DesignTokens.Text.PRIMARY);
        searchBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        this.mobSearchField = searchBox;
        this.addRenderableWidget(searchBox);

        // Waves control buttons
        EditorButton wavesMinusButton = EditorButton.builder("endurance-waves-minus", I18n.ui("minus_symbol").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> adjustWaves(-1))
            .build();
        EditorButtonWidget wavesMinusWidget = new EditorButtonWidget(wavesMinusButton,
            modalX + sModalWidth - UIScaleManager.scale(150), modalY + sModalHeight - UIScaleManager.scale(70), sButtonSize, sButtonSize);
        wavesMinusWidget.visible = false;
        this.wavesMinusBtnWidget = wavesMinusWidget;
        this.addRenderableWidget(wavesMinusWidget);

        EditorButton wavesPlusButton = EditorButton.builder("endurance-waves-plus", I18n.ui("plus_symbol").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> adjustWaves(1))
            .build();
        EditorButtonWidget wavesPlusWidget = new EditorButtonWidget(wavesPlusButton,
            modalX + sModalWidth - UIScaleManager.scale(80), modalY + sModalHeight - UIScaleManager.scale(70), sButtonSize, sButtonSize);
        wavesPlusWidget.visible = false;
        this.wavesPlusBtnWidget = wavesPlusWidget;
        this.addRenderableWidget(wavesPlusWidget);

        // Endless mode toggle
        this.endlessModeBtnWidget = buildEndlessModeButton(modalX, modalY, sModalHeight);

        // Start button
        EditorButton startEnduranceButton = EditorButton.builder("endurance-start", I18n.ui("start_quest_with_icon").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.LARGE)
            .onClick(this::startEnduranceQuest)
            .build();
        EditorButtonWidget startEnduranceWidget = new EditorButtonWidget(startEnduranceButton,
            modalX + sModalWidth - UIScaleManager.scale(110), modalY + sModalHeight - UIScaleManager.scale(35),
            UIScaleManager.scale(100), UIScaleManager.scale(25));
        startEnduranceWidget.visible = false;
        this.startEnduranceBtnWidget = startEnduranceWidget;
        this.addRenderableWidget(startEnduranceWidget);

        // Cancel button
        EditorButton cancelEnduranceButton = EditorButton.builder("endurance-cancel", I18n.ui("cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::closeEnduranceModal)
            .build();
        EditorButtonWidget cancelEnduranceWidget = new EditorButtonWidget(cancelEnduranceButton,
            modalX + sPadding, modalY + sModalHeight - UIScaleManager.scale(35), UIScaleManager.scale(80), UIScaleManager.scale(25));
        cancelEnduranceWidget.visible = false;
        this.cancelEnduranceBtnWidget = cancelEnduranceWidget;
        this.addRenderableWidget(cancelEnduranceWidget);
    }

    @Override
    public void removed() {
        super.removed();
        // Unregister listener
        if (questChangeListener != null) {
            QuestManager.INSTANCE.removeChangeListener(questChangeListener);
        }
        // Save on close
        QuestManager.INSTANCE.save();
    }

    @Override
    protected void onApply() {
        QuestManager.INSTANCE.save();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();

        // Calculate scaled dimensions
        int sHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int sPadding = UIScaleManager.scale(PADDING);
        int sQuestListWidth = UIScaleManager.scale(QUEST_LIST_WIDTH);
        int sTaskListWidth = UIScaleManager.scale(TASK_LIST_WIDTH);

        // Background
        renderBackground(Objects.requireNonNull(graphics, "graphics"), mouseX, mouseY, partialTick);

        int contentTop = sHeaderHeight + sPadding;

        // === Header ===
        graphics.fill(0, 0, width, sHeaderHeight, DesignTokens.Background.HEADER());
        UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font, "font"), "Quest Editor", width / 2, UIScaleManager.scale(10), DesignTokens.Text.TITLE());

        // === Quest List Panel ===
        renderQuestListPanel(graphics, sPadding, contentTop, mouseX, mouseY);

        // === Task List Panel ===
        int taskListX = sQuestListWidth + sPadding * 2;
        renderTaskListPanel(graphics, taskListX, contentTop, mouseX, mouseY);

        // === Details Panel ===
        int detailsX = sQuestListWidth + sTaskListWidth + sPadding * 3;
        int detailsWidth = width - detailsX - sPadding;
        renderDetailsPanel(graphics, detailsX, contentTop, detailsWidth);

        renderInputBackgrounds(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        renderButtonTooltips(graphics, mouseX, mouseY);

        // Render endurance modal on top if open
        renderEnduranceModal(graphics, mouseX, mouseY);
        renderEnduranceModalInputs(graphics);
        renderEnduranceModalWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        EditBox questNoteBox = questNoteField;
        if (questNoteBox != null) {
            AxiomRenderer.drawInputBackground(
                graphics,
                questNoteBox.getX(),
                questNoteBox.getY(),
                questNoteBox.getWidth(),
                questNoteBox.getHeight(),
                questNoteBox.isFocused()
            );
        }
        EditBox taskNoteBox = taskNoteField;
        if (taskNoteBox != null) {
            AxiomRenderer.drawInputBackground(
                graphics,
                taskNoteBox.getX(),
                taskNoteBox.getY(),
                taskNoteBox.getWidth(),
                taskNoteBox.getHeight(),
                taskNoteBox.isFocused()
            );
        }
        EditBox newQuestBox = newQuestNameField;
        if (newQuestBox != null) {
            AxiomRenderer.drawInputBackground(
                graphics,
                newQuestBox.getX(),
                newQuestBox.getY(),
                newQuestBox.getWidth(),
                newQuestBox.getHeight(),
                newQuestBox.isFocused()
            );
        }
        EditBox newTaskBox = newTaskDescField;
        if (newTaskBox != null) {
            AxiomRenderer.drawInputBackground(
                graphics,
                newTaskBox.getX(),
                newTaskBox.getY(),
                newTaskBox.getWidth(),
                newTaskBox.getHeight(),
                newTaskBox.isFocused()
            );
        }
    }

    private void renderButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (showEnduranceModal) {
            return;
        }
        renderTooltipIfActive(graphics, deleteQuestBtnWidget, mouseX, mouseY);
        renderTooltipIfActive(graphics, deleteTaskBtnWidget, mouseX, mouseY);
        renderTooltipIfActive(graphics, enduranceQuestBtnWidget, mouseX, mouseY);
    }

    private void renderTooltipIfActive(GuiGraphics graphics, @Nullable EditorButtonWidget widget, int mouseX, int mouseY) {
        if (widget == null) {
            return;
        }
        String tooltip = widget.getButton().activeTooltip();
        if (tooltip != null) {
            graphics.renderTooltip(Objects.requireNonNull(font, "font"), Component.literal(tooltip), mouseX, mouseY);
        }
    }

    private void renderEnduranceModalInputs(GuiGraphics graphics) {
        if (!showEnduranceModal) {
            return;
        }
        EditBox searchField = mobSearchField;
        if (searchField != null) {
            AxiomRenderer.drawInputBackground(
                graphics,
                searchField.getX(),
                searchField.getY(),
                searchField.getWidth(),
                searchField.getHeight(),
                searchField.isFocused()
            );
        }
    }

    private void renderEnduranceModalWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!showEnduranceModal) {
            return;
        }
        EditBox searchField = mobSearchField;
        if (searchField != null) {
            searchField.render(graphics, mouseX, mouseY, partialTick);
        }
        EditorButtonWidget wavesMinusBtn = wavesMinusBtnWidget;
        if (wavesMinusBtn != null) {
            wavesMinusBtn.render(graphics, mouseX, mouseY, partialTick);
        }
        EditorButtonWidget wavesPlusBtn = wavesPlusBtnWidget;
        if (wavesPlusBtn != null) {
            wavesPlusBtn.render(graphics, mouseX, mouseY, partialTick);
        }
        EditorButtonWidget endlessModeBtn = endlessModeBtnWidget;
        if (endlessModeBtn != null) {
            endlessModeBtn.render(graphics, mouseX, mouseY, partialTick);
        }
        EditorButtonWidget startEnduranceBtn = startEnduranceBtnWidget;
        if (startEnduranceBtn != null) {
            startEnduranceBtn.render(graphics, mouseX, mouseY, partialTick);
        }
        EditorButtonWidget cancelEnduranceBtn = cancelEnduranceBtnWidget;
        if (cancelEnduranceBtn != null) {
            cancelEnduranceBtn.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderQuestListPanel(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int sQuestListWidth = UIScaleManager.scale(QUEST_LIST_WIDTH);
        int sLineHeight = UIScaleManager.scale(LINE_HEIGHT);
        int sPanelBottomMargin = UIScaleManager.scale(65);
        int panelHeight = height - y - sPanelBottomMargin;

        // Panel background
        g.fill(x, y, x + sQuestListWidth, y + panelHeight, DesignTokens.Background.PANEL());
        g.fill(x, y, x + sQuestListWidth, y + 1, DesignTokens.Border.DEFAULT());
        g.fill(x, y + panelHeight - 1, x + sQuestListWidth, y + panelHeight, DesignTokens.Border.DEFAULT());
        g.fill(x, y, x + 1, y + panelHeight, DesignTokens.Border.DEFAULT());
        g.fill(x + sQuestListWidth - 1, y, x + sQuestListWidth, y + panelHeight, DesignTokens.Border.DEFAULT());

        // Header
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "Quest", x + UIScaleManager.scale(5), y + UIScaleManager.scale(5), DesignTokens.Text.TITLE(), false);

        // Quest list
        List<QuestData> quests = QuestManager.INSTANCE.getAllQuests();
        int listY = y + UIScaleManager.scale(22);
        int maxVisible = (panelHeight - UIScaleManager.scale(25)) / sLineHeight;

        for (int i = questListScroll; i < Math.min(quests.size(), questListScroll + maxVisible); i++) {
            QuestData quest = quests.get(i);
            int itemY = listY + (i - questListScroll) * sLineHeight;

            // Highlight selected
            boolean isSelected = quest == selectedQuest;
            boolean isActive = quest == QuestManager.INSTANCE.getActiveQuest();
            boolean isHovered = mouseX >= x + 2 && mouseX < x + sQuestListWidth - 2 &&
                               mouseY >= itemY && mouseY < itemY + sLineHeight;

            if (isSelected) {
                g.fill(x + 2, itemY, x + sQuestListWidth - 2, itemY + sLineHeight,
                    DesignTokens.withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A27));
            } else if (isHovered) {
                g.fill(x + 2, itemY, x + sQuestListWidth - 2, itemY + sLineHeight,
                    DesignTokens.withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A13));
            }

            // Quest name with status icon
            String prefix = isActive ? "\u2605 " : "  ";
            String name = prefix + truncate(quest.getName(), sQuestListWidth - UIScaleManager.scale(30));
            int color = isActive ? DesignTokens.Accent.GOLD() : (quest.isComplete() ? DesignTokens.Accent.GREEN() : DesignTokens.Text.PRIMARY());
            UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), name, x + UIScaleManager.scale(5), itemY + 2, color, false);

            // Progress indicator
            String progress = Objects.requireNonNull(quest.getProgressSummary(), "progress");
            UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), progress, x + sQuestListWidth - UIScaleManager.getScaledStringWidth(font, progress) - UIScaleManager.scale(25), itemY + 2, DesignTokens.Text.MUTED(), false);
        }
    }

    private void renderTaskListPanel(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int sTaskListWidth = UIScaleManager.scale(TASK_LIST_WIDTH);
        int sLineHeight = UIScaleManager.scale(LINE_HEIGHT);
        int sPanelBottomMargin = UIScaleManager.scale(65);
        int panelHeight = height - y - sPanelBottomMargin;

        // Panel background
        g.fill(x, y, x + sTaskListWidth, y + panelHeight, DesignTokens.Background.PANEL());
        g.fill(x, y, x + sTaskListWidth, y + 1, DesignTokens.Border.DEFAULT());
        g.fill(x, y + panelHeight - 1, x + sTaskListWidth, y + panelHeight, DesignTokens.Border.DEFAULT());
        g.fill(x, y, x + 1, y + panelHeight, DesignTokens.Border.DEFAULT());
        g.fill(x + sTaskListWidth - 1, y, x + sTaskListWidth, y + panelHeight, DesignTokens.Border.DEFAULT());

        // Header
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "Task", x + UIScaleManager.scale(5), y + UIScaleManager.scale(5), DesignTokens.Text.TITLE(), false);

        // Local capture for null safety
        QuestData quest = selectedQuest;
        if (quest == null) {
            UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "Select a quest", x + UIScaleManager.scale(10), y + UIScaleManager.scale(30), DesignTokens.Text.MUTED(), false);
            return;
        }

        // Task list
        List<QuestTask> tasks = quest.getTasks();
        int listY = y + UIScaleManager.scale(22);
        int maxVisible = (panelHeight - UIScaleManager.scale(25)) / sLineHeight;

        for (int i = taskListScroll; i < Math.min(tasks.size(), taskListScroll + maxVisible); i++) {
            QuestTask task = tasks.get(i);
            int itemY = listY + (i - taskListScroll) * sLineHeight;

            // Highlight selected/current
            boolean isSelected = task == selectedTask;
            boolean isCurrent = task == quest.getCurrentTask();
            boolean isHovered = mouseX >= x + 2 && mouseX < x + sTaskListWidth - 2 &&
                               mouseY >= itemY && mouseY < itemY + sLineHeight;

            if (isSelected) {
                g.fill(x + 2, itemY, x + sTaskListWidth - 2, itemY + sLineHeight,
                    DesignTokens.withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A27));
            } else if (isHovered) {
                g.fill(x + 2, itemY, x + sTaskListWidth - 2, itemY + sLineHeight,
                    DesignTokens.withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A13));
            }

            // Task with status
            String prefix = task.isCompleted() ? "\u2713 " : (isCurrent ? "\u25B6 " : "  ");
            String desc = prefix + truncate(task.getDescription(), sTaskListWidth - UIScaleManager.scale(20));
            int color = task.isCompleted() ? DesignTokens.Accent.GREEN() : (isCurrent ? DesignTokens.Accent.GOLD() : DesignTokens.Text.PRIMARY());
            UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), desc, x + UIScaleManager.scale(5), itemY + 2, color, false);

            // Note indicator
            if (task.hasNote()) {
                UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "\u270E", x + sTaskListWidth - UIScaleManager.scale(15), itemY + 2, DesignTokens.Accent.BLUE(), false);
            }
        }
    }

    private void renderDetailsPanel(GuiGraphics g, int x, int y, int panelWidth) {
        if (panelWidth < UIScaleManager.scale(50)) return;

        // Section: Quest Note
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "Quest Note:", x, y + UIScaleManager.scale(25), DesignTokens.Text.MUTED(), false);

        // Section: Task Note
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "Task Note:", x, y + UIScaleManager.scale(105), DesignTokens.Text.MUTED(), false);

        // Current selection info (local captures for null safety)
        QuestData quest = selectedQuest;
        if (quest != null) {
            UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "Quest: " + quest.getName(), x, y + UIScaleManager.scale(5), DesignTokens.Text.TITLE(), false);
        }

        QuestTask task = selectedTask;
        if (task != null) {
            UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "Task: " + truncate(task.getDescription(), panelWidth / 6), x, y + UIScaleManager.scale(85), DesignTokens.Text.TITLE(), false);
        }

        // Help text
        int sLineSpacing = UIScaleManager.scale(12);
        int helpY = y + UIScaleManager.scale(190);
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "Keybind:", x, helpY, DesignTokens.Text.MUTED(), false);
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "] = Complete task", x, helpY + sLineSpacing, DesignTokens.Text.DISABLED(), false);
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "\\ = Toggle HUD", x, helpY + sLineSpacing * 2, DesignTokens.Text.DISABLED(), false);
        UIScaleManager.drawScaledString(g, Objects.requireNonNull(font, "font"), "[ = Open editor", x, helpY + sLineSpacing * 3, DesignTokens.Text.DISABLED(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle modal clicks first (priority)
        if (handleEnduranceModalClick(mouseX, mouseY)) {
            return true;
        }

        // If modal is open, block clicks on main UI (except modal buttons handled by super)
        if (showEnduranceModal) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            int sHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
            int sPadding = UIScaleManager.scale(PADDING);
            int sQuestListWidth = UIScaleManager.scale(QUEST_LIST_WIDTH);
            int sTaskListWidth = UIScaleManager.scale(TASK_LIST_WIDTH);
            int sLineHeight = UIScaleManager.scale(LINE_HEIGHT);
            int sListHeaderOffset = UIScaleManager.scale(22);
            int sPanelBottomMargin = UIScaleManager.scale(65);

            int contentTop = sHeaderHeight + sPadding;
            int panelHeight = height - contentTop - sPanelBottomMargin;

            // Click on quest list
            if (mouseX >= sPadding && mouseX < sPadding + sQuestListWidth &&
                mouseY >= contentTop + sListHeaderOffset && mouseY < contentTop + panelHeight) {
                int clickedIndex = (int) ((mouseY - contentTop - sListHeaderOffset) / sLineHeight) + questListScroll;
                List<QuestData> quests = QuestManager.INSTANCE.getAllQuests();
                if (clickedIndex >= 0 && clickedIndex < quests.size()) {
                    QuestData clickedQuest = quests.get(clickedIndex);
                    selectedQuest = clickedQuest;
                    selectedTask = clickedQuest.getCurrentTask();
                    updateFieldsFromSelection();
                    updateButtonStates();
                    return true;
                }
            }

            // Click on task list
            int taskListX = sQuestListWidth + sPadding * 2;
            QuestData quest = selectedQuest;
            if (mouseX >= taskListX && mouseX < taskListX + sTaskListWidth &&
                mouseY >= contentTop + sListHeaderOffset && mouseY < contentTop + panelHeight && quest != null) {
                int clickedIndex = (int) ((mouseY - contentTop - sListHeaderOffset) / sLineHeight) + taskListScroll;
                List<QuestTask> tasks = quest.getTasks();
                if (clickedIndex >= 0 && clickedIndex < tasks.size()) {
                    selectedTask = tasks.get(clickedIndex);
                    updateFieldsFromSelection();
                    updateButtonStates();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Handle modal scroll first
        if (handleEnduranceModalScroll(mouseX, mouseY, scrollY)) {
            return true;
        }

        int sPadding = UIScaleManager.scale(PADDING);
        int sQuestListWidth = UIScaleManager.scale(QUEST_LIST_WIDTH);
        int sTaskListWidth = UIScaleManager.scale(TASK_LIST_WIDTH);

        // Scroll quest list
        if (mouseX >= sPadding && mouseX < sPadding + sQuestListWidth) {
            int maxScroll = Math.max(0, QuestManager.INSTANCE.getAllQuests().size() - 10);
            questListScroll = Math.max(0, Math.min(maxScroll, questListScroll - (int) scrollY));
            return true;
        }

        // Scroll task list (local capture for null safety)
        int taskListX = sQuestListWidth + sPadding * 2;
        QuestData quest = selectedQuest;
        if (mouseX >= taskListX && mouseX < taskListX + sTaskListWidth && quest != null) {
            int maxScroll = Math.max(0, quest.getTasks().size() - 10);
            taskListScroll = Math.max(0, Math.min(maxScroll, taskListScroll - (int) scrollY));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showEnduranceModal && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeEnduranceModal();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // === Actions ===

    private void addNewQuest() {
        EditBox questNameBox = newQuestNameField;
        if (questNameBox == null) {
            return;
        }
        String name = questNameBox.getValue().trim();
        if (name.isEmpty()) {
            name = "New Quest " + (QuestManager.INSTANCE.getAllQuests().size() + 1);
        }
        QuestData newQuest = new QuestData("quest_" + System.currentTimeMillis(), name);
        newQuest.addTask(new QuestTask("task_1", "First task"));
        QuestManager.INSTANCE.addQuest(newQuest);
        selectedQuest = newQuest;
        selectedTask = newQuest.getCurrentTask();
        questNameBox.setValue("");
        updateFieldsFromSelection();
        updateButtonStates();
    }

    private void deleteSelectedQuest() {
        QuestData quest = selectedQuest;
        if (quest == null) {
            return;
        }
        QuestManager.INSTANCE.removeQuest(quest.getId());
        QuestData newActiveQuest = QuestManager.INSTANCE.getActiveQuest();
        selectedQuest = newActiveQuest;
        selectedTask = newActiveQuest != null ? newActiveQuest.getCurrentTask() : null;
        updateFieldsFromSelection();
        updateButtonStates();
    }

    private void addNewTask() {
        QuestData quest = selectedQuest;
        EditBox taskDescBox = newTaskDescField;
        if (quest == null || taskDescBox == null) {
            return;
        }
        String desc = taskDescBox.getValue().trim();
        if (desc.isEmpty()) {
            desc = "Task " + (quest.getTasks().size() + 1);
        }
        QuestTask newTask = new QuestTask("task_" + System.currentTimeMillis(), desc);
        quest.addTask(newTask);
        selectedTask = newTask;
        taskDescBox.setValue("");
        QuestManager.INSTANCE.markDirty();
        updateFieldsFromSelection();
        updateButtonStates();
    }

    private void deleteSelectedTask() {
        QuestData quest = selectedQuest;
        QuestTask task = selectedTask;
        if (quest == null || task == null) {
            return;
        }
        quest.getTasks().remove(task);
        selectedTask = quest.getCurrentTask();
        QuestManager.INSTANCE.markDirty();
        updateFieldsFromSelection();
        updateButtonStates();
    }

    private void completeSelectedTask() {
        QuestTask task = selectedTask;
        if (task == null || task.isCompleted()) {
            return;
        }
        task.setCompleted(true);
        QuestData quest = selectedQuest;
        if (quest != null) {
            // Move to next task if this was current
            if (task == quest.getCurrentTask()) {
                quest.advanceToNextTask();
            }
        }
        QuestManager.INSTANCE.markDirty();
        updateButtonStates();
    }

    private void setActiveQuest() {
        if (selectedQuest != null) {
            QuestManager.INSTANCE.setActiveQuest(selectedQuest);
            updateButtonStates();
        }
    }

    private void onQuestNoteChanged(String note) {
        if (selectedQuest != null) {
            selectedQuest.setQuestNote(note);
            QuestManager.INSTANCE.markDirty();
        }
    }

    private void onTaskNoteChanged(String note) {
        if (selectedTask != null) {
            selectedTask.setNote(note);
            QuestManager.INSTANCE.markDirty();
        }
    }

    private void onQuestDataChanged() {
        // Called when QuestManager notifies changes (from HUD actions)
        // Refresh our view
        QuestData quest = selectedQuest;
        if (quest != null) {
            // Check if our selected quest still exists
            boolean found = false;
            String questId = quest.getId();
            for (QuestData q : QuestManager.INSTANCE.getAllQuests()) {
                if (q.getId().equals(questId)) {
                    selectedQuest = q;
                    quest = q;
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedQuest = QuestManager.INSTANCE.getActiveQuest();
                quest = selectedQuest;
            }
        }
        if (quest != null) {
            selectedTask = quest.getCurrentTask();
        } else {
            selectedTask = null;
        }
        updateFieldsFromSelection();
        updateButtonStates();
    }

    private void updateFieldsFromSelection() {
        EditBox questNoteBox = questNoteField;
        if (questNoteBox != null) {
            QuestData quest = selectedQuest;
            String questNoteValue = "";
            if (quest != null && quest.hasQuestNote()) {
                String note = quest.getQuestNote();
                if (note != null) {
                    questNoteValue = note;
                }
            }
            questNoteBox.setValue(questNoteValue);
        }
        EditBox taskNoteBox = taskNoteField;
        if (taskNoteBox != null) {
            QuestTask task = selectedTask;
            String taskNoteValue = "";
            if (task != null && task.hasNote()) {
                String note = task.getNote();
                if (note != null) {
                    taskNoteValue = note;
                }
            }
            taskNoteBox.setValue(taskNoteValue);
        }
    }

    private void updateButtonStates() {
        // Local captures for null safety
        QuestData quest = selectedQuest;
        QuestTask task = selectedTask;

        EditorButtonWidget deleteQuestBtn = deleteQuestBtnWidget;
        if (deleteQuestBtn != null) {
            deleteQuestBtn.getButton().setEnabled(quest != null);
        }
        EditorButtonWidget addTaskBtn = addTaskBtnWidget;
        if (addTaskBtn != null) {
            addTaskBtn.getButton().setEnabled(quest != null);
        }
        EditorButtonWidget deleteTaskBtn = deleteTaskBtnWidget;
        if (deleteTaskBtn != null) {
            deleteTaskBtn.getButton().setEnabled(task != null);
        }
        EditorButtonWidget completeTaskBtn = completeTaskBtnWidget;
        if (completeTaskBtn != null) {
            completeTaskBtn.getButton().setEnabled(task != null && !task.isCompleted());
        }
        EditorButtonWidget setActiveBtn = setActiveBtnWidget;
        if (setActiveBtn != null) {
            setActiveBtn.getButton().setEnabled(quest != null && quest != QuestManager.INSTANCE.getActiveQuest());
        }
    }

    private String truncate(String text, int maxWidth) {
        if (Objects.requireNonNull(font, "font").width(Objects.requireNonNull(text, "text")) <= maxWidth) return text;
        String ellipsis = "...";
        int minChars = Math.min(6, text.length()); // Keep at least 6 chars for readability
        String truncated = text;
        while (font.width(truncated + ellipsis) > maxWidth && truncated.length() > minChars) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + ellipsis;
    }

    // === Endurance Quest Modal Methods ===

    private void openEnduranceModal() {
        showEnduranceModal = true;
        selectedMob = null;
        mobListScroll = 0;
        enduranceWaves = 10;
        enduranceEndless = false;

        // Show modal components
        setEnduranceModalVisible(true);

        // Refresh mob list
        availableMobs = new ArrayList<>(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());
        availableMobs.sort(Comparator.comparing(m -> m.getDisplayName()));
        filteredMobs = new ArrayList<>(availableMobs);

        EditBox searchField = mobSearchField;
        if (searchField != null) {
            searchField.setValue("");
        }
        refreshEndlessModeButton();
        updateEnduranceButtonStates();
    }

    private void closeEnduranceModal() {
        showEnduranceModal = false;
        setEnduranceModalVisible(false);
    }

    private void setEnduranceModalVisible(boolean visible) {
        // Local captures for null safety
        EditBox searchField = mobSearchField;
        if (searchField != null) searchField.setVisible(visible);
        EditorButtonWidget wavesMinusBtn = wavesMinusBtnWidget;
        if (wavesMinusBtn != null) wavesMinusBtn.visible = visible;
        EditorButtonWidget wavesPlusBtn = wavesPlusBtnWidget;
        if (wavesPlusBtn != null) wavesPlusBtn.visible = visible;
        EditorButtonWidget endlessModeBtn = endlessModeBtnWidget;
        if (endlessModeBtn != null) endlessModeBtn.visible = visible;
        EditorButtonWidget startEnduranceBtn = startEnduranceBtnWidget;
        if (startEnduranceBtn != null) startEnduranceBtn.visible = visible;
        EditorButtonWidget cancelEnduranceBtn = cancelEnduranceBtnWidget;
        if (cancelEnduranceBtn != null) cancelEnduranceBtn.visible = visible;
    }

    private void refreshEndlessModeButton() {
        int sModalWidth = UIScaleManager.scale(400);
        int sModalHeight = UIScaleManager.scale(300);
        int modalX = (width - sModalWidth) / 2;
        int modalY = (height - sModalHeight) / 2;
        buildEndlessModeButton(modalX, modalY, sModalHeight);
    }

    private EditorButtonWidget buildEndlessModeButton(int modalX, int modalY, int sModalHeight) {
        EditorButtonWidget existing = endlessModeBtnWidget;
        if (existing != null) {
            removeWidget(existing);
        }
        String label = I18n.translate(enduranceEndless ? "devmod.endurance.endless_on" : "devmod.endurance.endless_off")
            .getString();
        EditorButton.Style style = enduranceEndless ? EditorButton.Style.SUCCESS : EditorButton.Style.PRIMARY;
        EditorButton endlessButton = EditorButton.builder("endurance-endless-toggle", label)
            .style(style)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::toggleEndlessMode)
            .build();
        EditorButtonWidget widget = new EditorButtonWidget(endlessButton, modalX + UIScaleManager.scale(10), modalY + sModalHeight - UIScaleManager.scale(70), UIScaleManager.scale(100), UIScaleManager.scale(20));
        widget.visible = showEnduranceModal;
        endlessModeBtnWidget = widget;
        addRenderableWidget(widget);
        return widget;
    }

    private void filterMobs(String query) {
        if (query == null || query.isEmpty()) {
            filteredMobs = new ArrayList<>(availableMobs);
        } else {
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            filteredMobs = availableMobs.stream()
                .filter(m -> m.getDisplayName().toLowerCase(Locale.ROOT).contains(lowerQuery) ||
                             m.getMobId().toString().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .collect(Collectors.toList());
        }
        mobListScroll = 0;
        selectedMob = null;
        updateEnduranceButtonStates();
    }

    private void adjustWaves(int delta) {
        enduranceWaves = Math.max(1, Math.min(50, enduranceWaves + delta));
    }

    private void toggleEndlessMode() {
        enduranceEndless = !enduranceEndless;
        refreshEndlessModeButton();
    }

    private void updateEnduranceButtonStates() {
        EditorButtonWidget startEnduranceBtn = startEnduranceBtnWidget;
        if (startEnduranceBtn != null) {
            startEnduranceBtn.getButton().setEnabled(selectedMob != null);
        }
    }

    private void startEnduranceQuest() {
        EnduranceQuestRegistry.MobQuestConfig mob = selectedMob;
        if (mob == null) return;

        // Create QuestData for the endurance quest
        QuestData enduranceQuest = QuestData.createEnduranceQuest(
            mob.getMobId().toString(),
            mob.getDisplayName(),
            enduranceWaves,
            enduranceEndless
        );

        // Add to quest manager and set as active
        QuestManager.INSTANCE.addQuest(enduranceQuest);
        QuestManager.INSTANCE.setActiveQuest(enduranceQuest);

        // Send network packet to server to actually start the quest
        PacketDistributor.sendToServer(new StartQuestPayload(
            mob.getMobId().toString(),
            enduranceWaves,
            enduranceEndless
        ));

        // Close modal and update UI
        closeEnduranceModal();
        selectedQuest = enduranceQuest;
        selectedTask = enduranceQuest.getCurrentTask();
        updateFieldsFromSelection();
        updateButtonStates();
    }

    // === Endurance Modal Rendering ===

    private void renderEnduranceModal(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!showEnduranceModal) return;

        int sModalWidth = UIScaleManager.scale(400);
        int sModalHeight = UIScaleManager.scale(300);
        int modalX = (width - sModalWidth) / 2;
        int modalY = (height - sModalHeight) / 2;
        int sPadding = UIScaleManager.scale(10);
        int sItemHeight = UIScaleManager.scale(20);

        // Darken background
        graphics.fill(0, 0, width, height,
            DesignTokens.withAlpha(DesignTokens.Mask.NONE, DesignTokens.Alpha.A67));

        // Modal background
        graphics.fill(modalX, modalY, modalX + sModalWidth, modalY + sModalHeight, DesignTokens.Background.PANEL_SOLID());
        graphics.fill(modalX, modalY, modalX + sModalWidth, modalY + 1, DesignTokens.Border.DEFAULT());
        graphics.fill(modalX, modalY + sModalHeight - 1, modalX + sModalWidth, modalY + sModalHeight, DesignTokens.Border.DEFAULT());
        graphics.fill(modalX, modalY, modalX + 1, modalY + sModalHeight, DesignTokens.Border.DEFAULT());
        graphics.fill(modalX + sModalWidth - 1, modalY, modalX + sModalWidth, modalY + sModalHeight, DesignTokens.Border.DEFAULT());

        // Title
        UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font, "font"), "\u2694 New Endurance Quest", modalX + sModalWidth / 2, modalY + sPadding, DesignTokens.Accent.GOLD());

        // Mob list area
        int listX = modalX + sPadding;
        int listY = modalY + UIScaleManager.scale(58);
        int listWidth = sModalWidth - sPadding * 2;
        int listHeight = sModalHeight - UIScaleManager.scale(145);

        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, DesignTokens.Background.PANEL());

        // Render mob list
        int maxVisible = listHeight / sItemHeight;

        for (int i = mobListScroll; i < Math.min(filteredMobs.size(), mobListScroll + maxVisible); i++) {
            EnduranceQuestRegistry.MobQuestConfig mob = filteredMobs.get(i);
            int itemY = listY + (i - mobListScroll) * sItemHeight;

            boolean isSelected = mob == selectedMob;
            boolean isHovered = mouseX >= listX && mouseX < listX + listWidth &&
                               mouseY >= itemY && mouseY < itemY + sItemHeight;

            if (isSelected) {
                graphics.fill(listX + 1, itemY, listX + listWidth - 1, itemY + sItemHeight, DesignTokens.Background.ACTIVE());
            } else if (isHovered) {
                graphics.fill(listX + 1, itemY, listX + listWidth - 1, itemY + sItemHeight, DesignTokens.Background.HOVER());
            }

            // Tier color indicator
            int tierColor = getTierColor(mob.getTier());
            graphics.fill(listX + 2, itemY + 2, listX + UIScaleManager.scale(5), itemY + sItemHeight - 2, tierColor);

            // Mob name
            UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font, "font"), mob.getDisplayName(), listX + sPadding, itemY + UIScaleManager.scale(5), DesignTokens.Text.PRIMARY(), false);

            // Tier badge
            String tierText = Objects.requireNonNull(mob.getTier().name(), "tierText");
            int tierTextWidth = UIScaleManager.getScaledStringWidth(font, tierText);
            UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font, "font"), tierText, listX + listWidth - tierTextWidth - sPadding, itemY + UIScaleManager.scale(5), tierColor, false);
        }

        // Scrollbar
        if (filteredMobs.size() > maxVisible) {
            int scrollbarHeight = (int) ((float) listHeight / filteredMobs.size() * maxVisible * sItemHeight);
            int scrollbarY = listY + (int) ((float) mobListScroll / (filteredMobs.size() - maxVisible) * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 3, listY, listX + listWidth, listY + listHeight, DesignTokens.Border.SEPARATOR());
            graphics.fill(listX + listWidth - 3, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, DesignTokens.Border.DEFAULT());
        }

        // Stats info
        UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font, "font"), filteredMobs.size() + " mobs available", listX, listY + listHeight + UIScaleManager.scale(5), DesignTokens.Text.SECONDARY(), false);

        // Wave count display
        String waveText = "Waves: " + enduranceWaves;
        UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font, "font"), waveText, modalX + sModalWidth - UIScaleManager.scale(125), modalY + sModalHeight - UIScaleManager.scale(65), DesignTokens.Text.PRIMARY(), false);

        // Selected mob info
        EnduranceQuestRegistry.MobQuestConfig currentMob = selectedMob;
        if (currentMob != null) {
            int infoY = modalY + sModalHeight - UIScaleManager.scale(95);
            UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font, "font"), "Selected: " + currentMob.getDisplayName(), listX, infoY, DesignTokens.Accent.GREEN(), false);
        }
    }

    private int getTierColor(EnduranceQuestRegistry.MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> DesignTokens.Text.MUTED();
            case EASY -> DesignTokens.Accent.GREEN();
            case MEDIUM -> DesignTokens.Accent.GOLD();
            case HARD -> DesignTokens.Accent.ORANGE();
            case ELITE -> DesignTokens.Accent.PURPLE();
            case BOSS -> DesignTokens.Accent.RED();
        };
    }

    // Handle clicks on mob list in modal
    private boolean handleEnduranceModalClick(double mouseX, double mouseY) {
        if (!showEnduranceModal) return false;

        int sModalWidth = UIScaleManager.scale(400);
        int sModalHeight = UIScaleManager.scale(300);
        int modalX = (width - sModalWidth) / 2;
        int modalY = (height - sModalHeight) / 2;
        int sPadding = UIScaleManager.scale(10);

        int listX = modalX + sPadding;
        int listY = modalY + UIScaleManager.scale(58);
        int listWidth = sModalWidth - sPadding * 2;
        int listHeight = sModalHeight - UIScaleManager.scale(145);
        int sItemHeight = UIScaleManager.scale(20);

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int clickedIndex = (int) ((mouseY - listY) / sItemHeight) + mobListScroll;
            if (clickedIndex >= 0 && clickedIndex < filteredMobs.size()) {
                selectedMob = filteredMobs.get(clickedIndex);
                updateEnduranceButtonStates();
                return true;
            }
        }

        return false;
    }

    // Handle scroll in modal
    private boolean handleEnduranceModalScroll(double mouseX, double mouseY, double scrollY) {
        if (!showEnduranceModal) return false;

        int sModalWidth = UIScaleManager.scale(400);
        int sModalHeight = UIScaleManager.scale(300);
        int modalX = (width - sModalWidth) / 2;
        int modalY = (height - sModalHeight) / 2;
        int sPadding = UIScaleManager.scale(10);

        int listX = modalX + sPadding;
        int listY = modalY + UIScaleManager.scale(58);
        int listWidth = sModalWidth - sPadding * 2;
        int listHeight = sModalHeight - UIScaleManager.scale(145);
        int sItemHeight = UIScaleManager.scale(20);
        int maxVisible = listHeight / sItemHeight;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int maxScroll = Math.max(0, filteredMobs.size() - maxVisible);
            mobListScroll = Math.max(0, Math.min(maxScroll, mobListScroll - (int) scrollY));
            return true;
        }

        return false;
    }
}
