package com.devmod.client.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.ModScreen;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UIConstants;
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
    private QuestData selectedQuest;
    private QuestTask selectedTask;
    private int questListScroll = 0;
    private int taskListScroll = 0;

    // Input fields
    private EditBox questNoteField;
    private EditBox taskNoteField;
    private EditBox newQuestNameField;
    private EditBox newTaskDescField;

    // Buttons
    private EditorButton addQuestBtn;
    private Button addQuestBtnWidget;
    private EditorButton deleteQuestBtn;
    private Button deleteQuestBtnWidget;
    private EditorButton addTaskBtn;
    private Button addTaskBtnWidget;
    private EditorButton deleteTaskBtn;
    private Button deleteTaskBtnWidget;
    private EditorButton completeTaskBtn;
    private Button completeTaskBtnWidget;
    private EditorButton setActiveBtn;
    private Button setActiveBtnWidget;
    private EditorButton newEnduranceBtn;
    private Button newEnduranceBtnWidget;

    // Change listener reference for cleanup
    private Runnable questChangeListener;

    // === Endurance Quest Modal State ===
    private boolean showEnduranceModal = false;
    private List<EnduranceQuestRegistry.MobQuestConfig> availableMobs = new ArrayList<>();
    private List<EnduranceQuestRegistry.MobQuestConfig> filteredMobs = new ArrayList<>();
    private EnduranceQuestRegistry.MobQuestConfig selectedMob = null;
    private int mobListScroll = 0;
    private int enduranceWaves = 10;
    private boolean enduranceEndless = false;
    private EditBox mobSearchField;
    private EditorButton startEnduranceBtn;
    private Button startEnduranceBtnWidget;
    private EditorButton cancelEnduranceBtn;
    private Button cancelEnduranceBtnWidget;
    private EditorButton wavesMinusBtn;
    private Button wavesMinusBtnWidget;
    private EditorButton wavesPlusBtn;
    private Button wavesPlusBtnWidget;
    private EditorButton endlessModeBtn;
    private Button endlessModeBtnWidget;

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

        int contentTop = HEADER_HEIGHT + PADDING;

        // === Quest Note Field ===
        int noteFieldX = QUEST_LIST_WIDTH + TASK_LIST_WIDTH + PADDING * 3;
        int noteFieldWidth = width - noteFieldX - PADDING;

        questNoteField = Objects.requireNonNull(new EditBox(Objects.requireNonNull(font, "font"), noteFieldX, contentTop + 40, Math.max(100, noteFieldWidth), 20, I18n.translate("devmod.quest.quest_note")), "questNoteField");
        questNoteField.setMaxLength(200);
        questNoteField.setHint(I18n.translate("devmod.quest.quest_note_hint"));
        if (selectedQuest != null && selectedQuest.hasQuestNote()) {
            questNoteField.setValue(Objects.requireNonNull(selectedQuest.getQuestNote(), "questNote"));
        }
        questNoteField.setResponder(this::onQuestNoteChanged);
        this.addRenderableWidget(questNoteField);

        // === Task Note Field ===
        taskNoteField = Objects.requireNonNull(new EditBox(Objects.requireNonNull(font, "font"), noteFieldX, contentTop + 120, Math.max(100, noteFieldWidth), 20, I18n.translate("devmod.quest.task_note")), "taskNoteField");
        taskNoteField.setMaxLength(200);
        taskNoteField.setHint(I18n.translate("devmod.quest.task_note_hint"));
        if (selectedTask != null && selectedTask.hasNote()) {
            taskNoteField.setValue(Objects.requireNonNull(selectedTask.getNote(), "taskNote"));
        }
        taskNoteField.setResponder(this::onTaskNoteChanged);
        this.addRenderableWidget(taskNoteField);

        // === New Quest Name Field ===
        newQuestNameField = Objects.requireNonNull(new EditBox(Objects.requireNonNull(font), PADDING, height - 55, QUEST_LIST_WIDTH - 25, 18, I18n.translate("devmod.quest.new_quest")));
        newQuestNameField.setMaxLength(50);
        newQuestNameField.setHint(I18n.translate("devmod.quest.new_quest_hint"));
        this.addRenderableWidget(Objects.requireNonNull(newQuestNameField));

        // === New Task Description Field ===
        int taskFieldX = QUEST_LIST_WIDTH + PADDING * 2;
        newTaskDescField = Objects.requireNonNull(new EditBox(Objects.requireNonNull(font), taskFieldX, height - 55, TASK_LIST_WIDTH - 25, 18, I18n.translate("devmod.quest.new_task")));
        newTaskDescField.setMaxLength(100);
        newTaskDescField.setHint(I18n.translate("devmod.quest.new_task_hint"));
        this.addRenderableWidget(Objects.requireNonNull(newTaskDescField));

        // === Buttons ===
        // Add Quest Button (+)
        addQuestBtn = EditorButton.builder("quest-add", I18n.ui("add_symbol").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::addNewQuest)
            .build();
        addQuestBtnWidget = Objects.requireNonNull(addQuestBtn.asVanilla(PADDING + QUEST_LIST_WIDTH - 22, height - 55, 20, 18));
        this.addRenderableWidget(addQuestBtnWidget);

        // Delete Quest Button
        deleteQuestBtn = EditorButton.builder("quest-delete", I18n.ui("delete_symbol").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::deleteSelectedQuest)
            .build();
        deleteQuestBtnWidget = Objects.requireNonNull(deleteQuestBtn.asVanilla(PADDING + QUEST_LIST_WIDTH - 22, contentTop, 20, 18));
        deleteQuestBtnWidget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(I18n.translate("devmod.quest.delete_quest")));
        this.addRenderableWidget(Objects.requireNonNull(deleteQuestBtnWidget));

        // Add Task Button (+)
        addTaskBtn = EditorButton.builder("task-add", I18n.ui("add_symbol").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::addNewTask)
            .build();
        addTaskBtnWidget = Objects.requireNonNull(addTaskBtn.asVanilla(taskFieldX + TASK_LIST_WIDTH - 22, height - 55, 20, 18));
        this.addRenderableWidget(addTaskBtnWidget);

        // Delete Task Button
        deleteTaskBtn = EditorButton.builder("task-delete", I18n.ui("delete_symbol").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::deleteSelectedTask)
            .build();
        deleteTaskBtnWidget = Objects.requireNonNull(deleteTaskBtn.asVanilla(taskFieldX + TASK_LIST_WIDTH - 22, contentTop, 20, 18));
        deleteTaskBtnWidget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(I18n.translate("devmod.quest.delete_task")));
        this.addRenderableWidget(Objects.requireNonNull(deleteTaskBtnWidget));

        // Complete Task Button
        completeTaskBtn = EditorButton.builder("task-complete", I18n.ui("complete_with_icon").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::completeSelectedTask)
            .build();
        completeTaskBtnWidget = Objects.requireNonNull(completeTaskBtn.asVanilla(noteFieldX, contentTop + 150, 100, 20));
        this.addRenderableWidget(completeTaskBtnWidget);

        // Set Active Quest Button
        setActiveBtn = EditorButton.builder("quest-set-active", I18n.ui("activate_with_icon").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::setActiveQuest)
            .build();
        setActiveBtnWidget = Objects.requireNonNull(setActiveBtn.asVanilla(noteFieldX + 110, contentTop + 150, 80, 20));
        this.addRenderableWidget(setActiveBtnWidget);

        // Close Button
        this.addRenderableWidget(Objects.requireNonNull(EditorButton.builder("quest-close", I18n.ui("close").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build()
            .asVanilla(width / 2 - 50, height - 28, 100, 20)));

        // === NEW: Endurance Quest Button ===
        newEnduranceBtn = EditorButton.builder("quest-endurance", I18n.ui("endurance_quest").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::openEnduranceModal)
            .build();
        newEnduranceBtnWidget = Objects.requireNonNull(newEnduranceBtn.asVanilla(PADDING, height - 28, 90, 20));
        newEnduranceBtnWidget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(I18n.translate("devmod.quest.create_endurance")));
        this.addRenderableWidget(Objects.requireNonNull(newEnduranceBtnWidget));

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

    /**
     * Initializes Endurance Quest modal components.
     */
    private void initEnduranceModalComponents() {
        // Load available mobs
        availableMobs = new ArrayList<>(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());
        availableMobs.sort(Comparator.comparing(m -> m.displayName));
        filteredMobs = new ArrayList<>(availableMobs);

        int modalWidth = 400;
        int modalHeight = 300;
        int modalX = (width - modalWidth) / 2;
        int modalY = (height - modalHeight) / 2;

        // Search field
        mobSearchField = Objects.requireNonNull(new EditBox(Objects.requireNonNull(font), modalX + 10, modalY + 35, modalWidth - 20, 18, I18n.ui("search")));
        mobSearchField.setHint(I18n.translate("devmod.quest.search_mobs"));
        mobSearchField.setResponder(this::filterMobs);
        mobSearchField.setVisible(false);
        this.addRenderableWidget(Objects.requireNonNull(mobSearchField));

        // Waves control buttons
        wavesMinusBtn = EditorButton.builder("endurance-waves-minus", I18n.ui("minus_symbol").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> adjustWaves(-1))
            .build();
        wavesMinusBtnWidget = Objects.requireNonNull(wavesMinusBtn.asVanilla(modalX + modalWidth - 150, modalY + modalHeight - 70, 20, 20));
        wavesMinusBtnWidget.visible = false;
        this.addRenderableWidget(Objects.requireNonNull(wavesMinusBtnWidget));

        wavesPlusBtn = EditorButton.builder("endurance-waves-plus", I18n.ui("plus_symbol").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> adjustWaves(1))
            .build();
        wavesPlusBtnWidget = Objects.requireNonNull(wavesPlusBtn.asVanilla(modalX + modalWidth - 80, modalY + modalHeight - 70, 20, 20));
        wavesPlusBtnWidget.visible = false;
        this.addRenderableWidget(Objects.requireNonNull(wavesPlusBtnWidget));

        // Endless mode toggle
        endlessModeBtn = EditorButton.builder("endurance-endless-toggle", I18n.translate("devmod.endurance.endless_off").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::toggleEndlessMode)
            .build();
        endlessModeBtnWidget = Objects.requireNonNull(endlessModeBtn.asVanilla(modalX + 10, modalY + modalHeight - 70, 100, 20));
        endlessModeBtnWidget.visible = false;
        this.addRenderableWidget(Objects.requireNonNull(endlessModeBtnWidget));

        // Start button
        startEnduranceBtn = EditorButton.builder("endurance-start", I18n.ui("start_quest_with_icon").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.LARGE)
            .onClick(this::startEnduranceQuest)
            .build();
        startEnduranceBtnWidget = Objects.requireNonNull(startEnduranceBtn.asVanilla(modalX + modalWidth - 110, modalY + modalHeight - 35, 100, 25));
        startEnduranceBtnWidget.visible = false;
        this.addRenderableWidget(Objects.requireNonNull(startEnduranceBtnWidget));

        // Cancel button
        cancelEnduranceBtn = EditorButton.builder("endurance-cancel", I18n.ui("cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::closeEnduranceModal)
            .build();
        cancelEnduranceBtnWidget = Objects.requireNonNull(cancelEnduranceBtn.asVanilla(modalX + 10, modalY + modalHeight - 35, 80, 25));
        cancelEnduranceBtnWidget.visible = false;
        this.addRenderableWidget(Objects.requireNonNull(cancelEnduranceBtnWidget));
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
        // Background
        renderBackground(Objects.requireNonNull(graphics, "graphics"), mouseX, mouseY, partialTick);

        int contentTop = HEADER_HEIGHT + PADDING;

        // === Header ===
        graphics.fill(0, 0, width, HEADER_HEIGHT, UIConstants.Background.HEADER());
        graphics.drawCenteredString(Objects.requireNonNull(font, "font"), "Quest Editor", width / 2, 10, UIConstants.Text.TITLE());

        // === Quest List Panel ===
        renderQuestListPanel(graphics, PADDING, contentTop, mouseX, mouseY);

        // === Task List Panel ===
        int taskListX = QUEST_LIST_WIDTH + PADDING * 2;
        renderTaskListPanel(graphics, taskListX, contentTop, mouseX, mouseY);

        // === Details Panel ===
        int detailsX = QUEST_LIST_WIDTH + TASK_LIST_WIDTH + PADDING * 3;
        int detailsWidth = width - detailsX - PADDING;
        renderDetailsPanel(graphics, detailsX, contentTop, detailsWidth);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Render endurance modal on top if open
        renderEnduranceModal(graphics, mouseX, mouseY);
    }

    private void renderQuestListPanel(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int panelHeight = height - y - 65;

        // Panel background
        g.fill(x, y, x + QUEST_LIST_WIDTH, y + panelHeight, UIConstants.Background.PANEL());
        g.fill(x, y, x + QUEST_LIST_WIDTH, y + 1, UIConstants.Border.DEFAULT());
        g.fill(x, y + panelHeight - 1, x + QUEST_LIST_WIDTH, y + panelHeight, UIConstants.Border.DEFAULT());
        g.fill(x, y, x + 1, y + panelHeight, UIConstants.Border.DEFAULT());
        g.fill(x + QUEST_LIST_WIDTH - 1, y, x + QUEST_LIST_WIDTH, y + panelHeight, UIConstants.Border.DEFAULT());

        // Header
        g.drawString(Objects.requireNonNull(font, "font"), "Quest", x + 5, y + 5, UIConstants.Text.TITLE(), false);

        // Quest list
        List<QuestData> quests = QuestManager.INSTANCE.getAllQuests();
        int listY = y + 22;
        int maxVisible = (panelHeight - 25) / LINE_HEIGHT;

        for (int i = questListScroll; i < Math.min(quests.size(), questListScroll + maxVisible); i++) {
            QuestData quest = quests.get(i);
            int itemY = listY + (i - questListScroll) * LINE_HEIGHT;

            // Highlight selected
            boolean isSelected = quest == selectedQuest;
            boolean isActive = quest == QuestManager.INSTANCE.getActiveQuest();
            boolean isHovered = mouseX >= x + 2 && mouseX < x + QUEST_LIST_WIDTH - 2 &&
                               mouseY >= itemY && mouseY < itemY + LINE_HEIGHT;

            if (isSelected) {
                g.fill(x + 2, itemY, x + QUEST_LIST_WIDTH - 2, itemY + LINE_HEIGHT, 0x44FFFFFF);
            } else if (isHovered) {
                g.fill(x + 2, itemY, x + QUEST_LIST_WIDTH - 2, itemY + LINE_HEIGHT, 0x22FFFFFF);
            }

            // Quest name with status icon
            String prefix = isActive ? "\u2605 " : "  ";
            String name = prefix + truncate(quest.getName(), QUEST_LIST_WIDTH - 30);
            int color = isActive ? UIConstants.Accent.GOLD() : (quest.isComplete() ? UIConstants.Accent.GREEN() : UIConstants.Text.PRIMARY());
            g.drawString(Objects.requireNonNull(font, "font"), name, x + 5, itemY + 2, color, false);

            // Progress indicator
            String progress = Objects.requireNonNull(quest.getProgressSummary(), "progress");
            g.drawString(Objects.requireNonNull(font, "font"), progress, x + QUEST_LIST_WIDTH - font.width(progress) - 25, itemY + 2, UIConstants.Text.MUTED(), false);
        }
    }

    private void renderTaskListPanel(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int panelHeight = height - y - 65;

        // Panel background
        g.fill(x, y, x + TASK_LIST_WIDTH, y + panelHeight, UIConstants.Background.PANEL());
        g.fill(x, y, x + TASK_LIST_WIDTH, y + 1, UIConstants.Border.DEFAULT());
        g.fill(x, y + panelHeight - 1, x + TASK_LIST_WIDTH, y + panelHeight, UIConstants.Border.DEFAULT());
        g.fill(x, y, x + 1, y + panelHeight, UIConstants.Border.DEFAULT());
        g.fill(x + TASK_LIST_WIDTH - 1, y, x + TASK_LIST_WIDTH, y + panelHeight, UIConstants.Border.DEFAULT());

        // Header
        g.drawString(Objects.requireNonNull(font, "font"), "Task", x + 5, y + 5, UIConstants.Text.TITLE(), false);

        if (selectedQuest == null) {
            g.drawString(Objects.requireNonNull(font, "font"), "Select a quest", x + 10, y + 30, UIConstants.Text.MUTED(), false);
            return;
        }

        // Task list
        List<QuestTask> tasks = selectedQuest.getTasks();
        int listY = y + 22;
        int maxVisible = (panelHeight - 25) / LINE_HEIGHT;

        for (int i = taskListScroll; i < Math.min(tasks.size(), taskListScroll + maxVisible); i++) {
            QuestTask task = tasks.get(i);
            int itemY = listY + (i - taskListScroll) * LINE_HEIGHT;

            // Highlight selected/current
            boolean isSelected = task == selectedTask;
            boolean isCurrent = task == selectedQuest.getCurrentTask();
            boolean isHovered = mouseX >= x + 2 && mouseX < x + TASK_LIST_WIDTH - 2 &&
                               mouseY >= itemY && mouseY < itemY + LINE_HEIGHT;

            if (isSelected) {
                g.fill(x + 2, itemY, x + TASK_LIST_WIDTH - 2, itemY + LINE_HEIGHT, 0x44FFFFFF);
            } else if (isHovered) {
                g.fill(x + 2, itemY, x + TASK_LIST_WIDTH - 2, itemY + LINE_HEIGHT, 0x22FFFFFF);
            }

            // Task with status
            String prefix = task.isCompleted() ? "\u2713 " : (isCurrent ? "\u25B6 " : "  ");
            String desc = prefix + truncate(task.getDescription(), TASK_LIST_WIDTH - 20);
            int color = task.isCompleted() ? UIConstants.Accent.GREEN() : (isCurrent ? UIConstants.Accent.GOLD() : UIConstants.Text.PRIMARY());
            g.drawString(Objects.requireNonNull(font, "font"), desc, x + 5, itemY + 2, color, false);

            // Note indicator
            if (task.hasNote()) {
                g.drawString(Objects.requireNonNull(font, "font"), "\u270E", x + TASK_LIST_WIDTH - 15, itemY + 2, UIConstants.Accent.BLUE(), false);
            }
        }
    }

    private void renderDetailsPanel(GuiGraphics g, int x, int y, int panelWidth) {
        if (panelWidth < 50) return;

        // Section: Quest Note
        g.drawString(Objects.requireNonNull(font, "font"), "Quest Note:", x, y + 25, UIConstants.Text.MUTED(), false);

        // Section: Task Note
        g.drawString(Objects.requireNonNull(font, "font"), "Task Note:", x, y + 105, UIConstants.Text.MUTED(), false);

        // Current selection info
        if (selectedQuest != null) {
            g.drawString(Objects.requireNonNull(font, "font"), "Quest: " + selectedQuest.getName(), x, y + 5, UIConstants.Text.TITLE(), false);
        }

        if (selectedTask != null) {
            g.drawString(Objects.requireNonNull(font, "font"), "Task: " + truncate(selectedTask.getDescription(), panelWidth / 6), x, y + 85, UIConstants.Text.TITLE(), false);
        }

        // Help text
        int helpY = y + 190;
        g.drawString(Objects.requireNonNull(font, "font"), "Keybind:", x, helpY, UIConstants.Text.MUTED(), false);
        g.drawString(Objects.requireNonNull(font, "font"), "] = Complete task", x, helpY + 12, UIConstants.Text.DISABLED(), false);
        g.drawString(Objects.requireNonNull(font, "font"), "\\ = Toggle HUD", x, helpY + 24, UIConstants.Text.DISABLED(), false);
        g.drawString(Objects.requireNonNull(font, "font"), "[ = Open editor", x, helpY + 36, UIConstants.Text.DISABLED(), false);
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
            int contentTop = HEADER_HEIGHT + PADDING;
            int panelHeight = height - contentTop - 65;

            // Click on quest list
            if (mouseX >= PADDING && mouseX < PADDING + QUEST_LIST_WIDTH &&
                mouseY >= contentTop + 22 && mouseY < contentTop + panelHeight) {
                int clickedIndex = (int) ((mouseY - contentTop - 22) / LINE_HEIGHT) + questListScroll;
                List<QuestData> quests = QuestManager.INSTANCE.getAllQuests();
                if (clickedIndex >= 0 && clickedIndex < quests.size()) {
                    selectedQuest = quests.get(clickedIndex);
                    selectedTask = selectedQuest.getCurrentTask();
                    updateFieldsFromSelection();
                    updateButtonStates();
                    return true;
                }
            }

            // Click on task list
            int taskListX = QUEST_LIST_WIDTH + PADDING * 2;
            if (mouseX >= taskListX && mouseX < taskListX + TASK_LIST_WIDTH &&
                mouseY >= contentTop + 22 && mouseY < contentTop + panelHeight && selectedQuest != null) {
                int clickedIndex = (int) ((mouseY - contentTop - 22) / LINE_HEIGHT) + taskListScroll;
                List<QuestTask> tasks = selectedQuest.getTasks();
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

        // Scroll quest list
        if (mouseX >= PADDING && mouseX < PADDING + QUEST_LIST_WIDTH) {
            int maxScroll = Math.max(0, QuestManager.INSTANCE.getAllQuests().size() - 10);
            questListScroll = Math.max(0, Math.min(maxScroll, questListScroll - (int) scrollY));
            return true;
        }

        // Scroll task list
        int taskListX = QUEST_LIST_WIDTH + PADDING * 2;
        if (mouseX >= taskListX && mouseX < taskListX + TASK_LIST_WIDTH && selectedQuest != null) {
            int maxScroll = Math.max(0, selectedQuest.getTasks().size() - 10);
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
        String name = newQuestNameField.getValue().trim();
        if (name.isEmpty()) {
            name = "New Quest " + (QuestManager.INSTANCE.getAllQuests().size() + 1);
        }
        QuestData newQuest = new QuestData("quest_" + System.currentTimeMillis(), name);
        newQuest.addTask(new QuestTask("task_1", "First task"));
        QuestManager.INSTANCE.addQuest(newQuest);
        selectedQuest = newQuest;
        selectedTask = newQuest.getCurrentTask();
        newQuestNameField.setValue("");
        updateFieldsFromSelection();
        updateButtonStates();
    }

    private void deleteSelectedQuest() {
        if (selectedQuest != null) {
            QuestManager.INSTANCE.removeQuest(selectedQuest.getId());
            selectedQuest = QuestManager.INSTANCE.getActiveQuest();
            selectedTask = selectedQuest != null ? selectedQuest.getCurrentTask() : null;
            updateFieldsFromSelection();
            updateButtonStates();
        }
    }

    private void addNewTask() {
        if (selectedQuest != null) {
            String desc = newTaskDescField.getValue().trim();
            if (desc.isEmpty()) {
                desc = "Task " + (selectedQuest.getTasks().size() + 1);
            }
            QuestTask newTask = new QuestTask("task_" + System.currentTimeMillis(), desc);
            selectedQuest.addTask(newTask);
            selectedTask = newTask;
            newTaskDescField.setValue("");
            QuestManager.INSTANCE.markDirty();
            updateFieldsFromSelection();
            updateButtonStates();
        }
    }

    private void deleteSelectedTask() {
        if (selectedQuest != null && selectedTask != null) {
            selectedQuest.getTasks().remove(selectedTask);
            selectedTask = selectedQuest.getCurrentTask();
            QuestManager.INSTANCE.markDirty();
            updateFieldsFromSelection();
            updateButtonStates();
        }
    }

    private void completeSelectedTask() {
        if (selectedTask != null && !selectedTask.isCompleted()) {
            selectedTask.setCompleted(true);
            if (selectedQuest != null) {
                // Move to next task if this was current
                if (selectedTask == selectedQuest.getCurrentTask()) {
                    selectedQuest.advanceToNextTask();
                }
            }
            QuestManager.INSTANCE.markDirty();
            updateButtonStates();
        }
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
        if (selectedQuest != null) {
            // Check if our selected quest still exists
            boolean found = false;
            for (QuestData q : QuestManager.INSTANCE.getAllQuests()) {
                if (q.getId().equals(selectedQuest.getId())) {
                    selectedQuest = q;
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedQuest = QuestManager.INSTANCE.getActiveQuest();
            }
        }
        if (selectedQuest != null) {
            selectedTask = selectedQuest.getCurrentTask();
        } else {
            selectedTask = null;
        }
        updateFieldsFromSelection();
        updateButtonStates();
    }

    private void updateFieldsFromSelection() {
        if (questNoteField != null) {
            questNoteField.setValue(Objects.requireNonNull(selectedQuest != null && selectedQuest.hasQuestNote() ? selectedQuest.getQuestNote() : "", "questNoteValue"));
        }
        if (taskNoteField != null) {
            taskNoteField.setValue(Objects.requireNonNull(selectedTask != null && selectedTask.hasNote() ? selectedTask.getNote() : "", "taskNoteValue"));
        }
    }

    private void updateButtonStates() {
        if (deleteQuestBtnWidget != null) {
            deleteQuestBtnWidget.active = selectedQuest != null;
        }
        if (addTaskBtnWidget != null) {
            addTaskBtnWidget.active = selectedQuest != null;
        }
        if (deleteTaskBtnWidget != null) {
            deleteTaskBtnWidget.active = selectedTask != null;
        }
        if (completeTaskBtnWidget != null) {
            completeTaskBtnWidget.active = selectedTask != null && !selectedTask.isCompleted();
        }
        if (setActiveBtnWidget != null) {
            setActiveBtnWidget.active = selectedQuest != null && selectedQuest != QuestManager.INSTANCE.getActiveQuest();
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
        availableMobs.sort(Comparator.comparing(m -> m.displayName));
        filteredMobs = new ArrayList<>(availableMobs);

        if (mobSearchField != null) {
            mobSearchField.setValue("");
        }
        updateEnduranceButtonStates();
    }

    private void closeEnduranceModal() {
        showEnduranceModal = false;
        setEnduranceModalVisible(false);
    }

    private void setEnduranceModalVisible(boolean visible) {
        if (mobSearchField != null) mobSearchField.setVisible(visible);
        if (wavesMinusBtnWidget != null) wavesMinusBtnWidget.visible = visible;
        if (wavesPlusBtnWidget != null) wavesPlusBtnWidget.visible = visible;
        if (endlessModeBtnWidget != null) endlessModeBtnWidget.visible = visible;
        if (startEnduranceBtnWidget != null) startEnduranceBtnWidget.visible = visible;
        if (cancelEnduranceBtnWidget != null) cancelEnduranceBtnWidget.visible = visible;
    }

    private void filterMobs(String query) {
        if (query == null || query.isEmpty()) {
            filteredMobs = new ArrayList<>(availableMobs);
        } else {
            String lowerQuery = query.toLowerCase();
            filteredMobs = availableMobs.stream()
                .filter(m -> m.displayName.toLowerCase().contains(lowerQuery) ||
                             m.mobId.toString().toLowerCase().contains(lowerQuery))
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
        if (endlessModeBtnWidget != null) {
            endlessModeBtnWidget.setMessage(I18n.translate(enduranceEndless ? "devmod.endurance.endless_on" : "devmod.endurance.endless_off"));
        }
    }

    private void updateEnduranceButtonStates() {
        if (startEnduranceBtnWidget != null) {
            startEnduranceBtnWidget.active = selectedMob != null;
        }
    }

    private void startEnduranceQuest() {
        if (selectedMob == null) return;

        // Create QuestData for the endurance quest
        QuestData enduranceQuest = QuestData.createEnduranceQuest(
            selectedMob.mobId.toString(),
            selectedMob.displayName,
            enduranceWaves,
            enduranceEndless
        );

        // Add to quest manager and set as active
        QuestManager.INSTANCE.addQuest(enduranceQuest);
        QuestManager.INSTANCE.setActiveQuest(enduranceQuest);

        // Send network packet to server to actually start the quest
        PacketDistributor.sendToServer(new StartQuestPayload(
            selectedMob.mobId.toString(),
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

        int modalWidth = 400;
        int modalHeight = 300;
        int modalX = (width - modalWidth) / 2;
        int modalY = (height - modalHeight) / 2;

        // Darken background
        graphics.fill(0, 0, width, height, 0xAA000000);

        // Modal background
        graphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, UIConstants.Background.PANEL_SOLID());
        graphics.fill(modalX, modalY, modalX + modalWidth, modalY + 1, UIConstants.Border.DEFAULT());
        graphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, UIConstants.Border.DEFAULT());
        graphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, UIConstants.Border.DEFAULT());
        graphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, UIConstants.Border.DEFAULT());

        // Title
        graphics.drawCenteredString(Objects.requireNonNull(font, "font"), "\u2694 New Endurance Quest", modalX + modalWidth / 2, modalY + 10, UIConstants.Accent.GOLD());

        // Mob list area
        int listX = modalX + 10;
        int listY = modalY + 58;
        int listWidth = modalWidth - 20;
        int listHeight = modalHeight - 145;

        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, UIConstants.Background.PANEL());

        // Render mob list
        int itemHeight = 20;
        int maxVisible = listHeight / itemHeight;

        for (int i = mobListScroll; i < Math.min(filteredMobs.size(), mobListScroll + maxVisible); i++) {
            EnduranceQuestRegistry.MobQuestConfig mob = filteredMobs.get(i);
            int itemY = listY + (i - mobListScroll) * itemHeight;

            boolean isSelected = mob == selectedMob;
            boolean isHovered = mouseX >= listX && mouseX < listX + listWidth &&
                               mouseY >= itemY && mouseY < itemY + itemHeight;

            if (isSelected) {
                graphics.fill(listX + 1, itemY, listX + listWidth - 1, itemY + itemHeight, UIConstants.Background.ACTIVE());
            } else if (isHovered) {
                graphics.fill(listX + 1, itemY, listX + listWidth - 1, itemY + itemHeight, UIConstants.Background.HOVER());
            }

            // Tier color indicator
            int tierColor = getTierColor(mob.tier);
            graphics.fill(listX + 2, itemY + 2, listX + 5, itemY + itemHeight - 2, tierColor);

            // Mob name
            graphics.drawString(Objects.requireNonNull(font, "font"), mob.displayName, listX + 10, itemY + 5, UIConstants.Text.PRIMARY(), false);

            // Tier badge
            String tierText = Objects.requireNonNull(mob.tier.name(), "tierText");
            int tierTextWidth = font.width(tierText);
            graphics.drawString(Objects.requireNonNull(font, "font"), tierText, listX + listWidth - tierTextWidth - 10, itemY + 5, tierColor, false);
        }

        // Scrollbar
        if (filteredMobs.size() > maxVisible) {
            int scrollbarHeight = (int) ((float) listHeight / filteredMobs.size() * maxVisible * itemHeight);
            int scrollbarY = listY + (int) ((float) mobListScroll / (filteredMobs.size() - maxVisible) * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 3, listY, listX + listWidth, listY + listHeight, UIConstants.Border.SEPARATOR());
            graphics.fill(listX + listWidth - 3, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, UIConstants.Border.DEFAULT());
        }

        // Stats info
        graphics.drawString(Objects.requireNonNull(font, "font"), filteredMobs.size() + " mobs available", listX, listY + listHeight + 5, UIConstants.Text.SECONDARY(), false);

        // Wave count display
        String waveText = "Waves: " + enduranceWaves;
        graphics.drawString(Objects.requireNonNull(font, "font"), waveText, modalX + modalWidth - 125, modalY + modalHeight - 65, UIConstants.Text.PRIMARY(), false);

        // Selected mob info
        if (selectedMob != null) {
            int infoY = modalY + modalHeight - 95;
            graphics.drawString(Objects.requireNonNull(font, "font"), "Selected: " + selectedMob.displayName, listX, infoY, UIConstants.Accent.GREEN(), false);
        }
    }

    private int getTierColor(EnduranceQuestRegistry.MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> UIConstants.Text.MUTED();
            case EASY -> UIConstants.Accent.GREEN();
            case MEDIUM -> UIConstants.Accent.GOLD();
            case HARD -> UIConstants.Accent.ORANGE();
            case ELITE -> UIConstants.Accent.PURPLE();
            case BOSS -> UIConstants.Accent.RED();
        };
    }

    // Handle clicks on mob list in modal
    private boolean handleEnduranceModalClick(double mouseX, double mouseY) {
        if (!showEnduranceModal) return false;

        int modalWidth = 400;
        int modalHeight = 300;
        int modalX = (width - modalWidth) / 2;
        int modalY = (height - modalHeight) / 2;

        int listX = modalX + 10;
        int listY = modalY + 58;
        int listWidth = modalWidth - 20;
        int listHeight = modalHeight - 145;
        int itemHeight = 20;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int clickedIndex = (int) ((mouseY - listY) / itemHeight) + mobListScroll;
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

        int modalWidth = 400;
        int modalHeight = 300;
        int modalX = (width - modalWidth) / 2;
        int modalY = (height - modalHeight) / 2;

        int listX = modalX + 10;
        int listY = modalY + 58;
        int listWidth = modalWidth - 20;
        int listHeight = modalHeight - 145;
        int itemHeight = 20;
        int maxVisible = listHeight / itemHeight;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int maxScroll = Math.max(0, filteredMobs.size() - maxVisible);
            mobListScroll = Math.max(0, Math.min(maxScroll, mobListScroll - (int) scrollY));
            return true;
        }

        return false;
    }
}
