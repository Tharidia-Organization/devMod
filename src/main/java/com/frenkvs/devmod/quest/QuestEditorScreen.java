package com.frenkvs.devmod.quest;

import com.frenkvs.devmod.endurance.EnduranceQuestRegistry;
import com.frenkvs.devmod.endurance.StartQuestPayload;
import com.frenkvs.devmod.ui.ModScreen;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UI estesa per gestire le quest.
 *
 * Features:
 * - Lista delle quest disponibili
 * - Dettaglio quest selezionata con tutte le task
 * - Editor per note su quest e task
 * - Creazione/eliminazione quest
 * - Sincronizzazione con QuestHudOverlay
 */
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
    private Button addQuestBtn;
    private Button deleteQuestBtn;
    private Button addTaskBtn;
    private Button deleteTaskBtn;
    private Button completeTaskBtn;
    private Button setActiveBtn;
    private Button newEnduranceBtn;

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
    private Button startEnduranceBtn;
    private Button cancelEnduranceBtn;
    private Button wavesMinusBtn;
    private Button wavesPlusBtn;
    private Button endlessModeBtn;

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
        int contentHeight = height - contentTop - 60; // Space for bottom buttons

        // === Quest Note Field ===
        int noteFieldX = QUEST_LIST_WIDTH + TASK_LIST_WIDTH + PADDING * 3;
        int noteFieldWidth = width - noteFieldX - PADDING;

        questNoteField = new EditBox(font, noteFieldX, contentTop + 40, Math.max(100, noteFieldWidth), 20, I18n.translate("devmod.quest.quest_note"));
        questNoteField.setMaxLength(200);
        questNoteField.setHint(I18n.translate("devmod.quest.quest_note_hint"));
        if (selectedQuest != null && selectedQuest.hasQuestNote()) {
            questNoteField.setValue(selectedQuest.getQuestNote());
        }
        questNoteField.setResponder(this::onQuestNoteChanged);
        this.addRenderableWidget(questNoteField);

        // === Task Note Field ===
        taskNoteField = new EditBox(font, noteFieldX, contentTop + 120, Math.max(100, noteFieldWidth), 20, I18n.translate("devmod.quest.task_note"));
        taskNoteField.setMaxLength(200);
        taskNoteField.setHint(I18n.translate("devmod.quest.task_note_hint"));
        if (selectedTask != null && selectedTask.hasNote()) {
            taskNoteField.setValue(selectedTask.getNote());
        }
        taskNoteField.setResponder(this::onTaskNoteChanged);
        this.addRenderableWidget(taskNoteField);

        // === New Quest Name Field ===
        newQuestNameField = new EditBox(font, PADDING, height - 55, QUEST_LIST_WIDTH - 25, 18, I18n.translate("devmod.quest.new_quest"));
        newQuestNameField.setMaxLength(50);
        newQuestNameField.setHint(I18n.translate("devmod.quest.new_quest_hint"));
        this.addRenderableWidget(newQuestNameField);

        // === New Task Description Field ===
        int taskFieldX = QUEST_LIST_WIDTH + PADDING * 2;
        newTaskDescField = new EditBox(font, taskFieldX, height - 55, TASK_LIST_WIDTH - 25, 18, I18n.translate("devmod.quest.new_task"));
        newTaskDescField.setMaxLength(100);
        newTaskDescField.setHint(I18n.translate("devmod.quest.new_task_hint"));
        this.addRenderableWidget(newTaskDescField);

        // === Buttons ===
        // Add Quest Button (+)
        addQuestBtn = Button.builder(I18n.ui("add_symbol"), b -> addNewQuest())
            .bounds(PADDING + QUEST_LIST_WIDTH - 22, height - 55, 20, 18)
            .build();
        this.addRenderableWidget(addQuestBtn);

        // Delete Quest Button
        deleteQuestBtn = Button.builder(I18n.ui("delete_symbol"), b -> deleteSelectedQuest())
            .bounds(PADDING + QUEST_LIST_WIDTH - 22, contentTop, 20, 18)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(I18n.translate("devmod.quest.delete_quest")))
            .build();
        this.addRenderableWidget(deleteQuestBtn);

        // Add Task Button (+)
        addTaskBtn = Button.builder(I18n.ui("add_symbol"), b -> addNewTask())
            .bounds(taskFieldX + TASK_LIST_WIDTH - 22, height - 55, 20, 18)
            .build();
        this.addRenderableWidget(addTaskBtn);

        // Delete Task Button
        deleteTaskBtn = Button.builder(I18n.ui("delete_symbol"), b -> deleteSelectedTask())
            .bounds(taskFieldX + TASK_LIST_WIDTH - 22, contentTop, 20, 18)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(I18n.translate("devmod.quest.delete_task")))
            .build();
        this.addRenderableWidget(deleteTaskBtn);

        // Complete Task Button
        completeTaskBtn = Button.builder(I18n.ui("complete_with_icon"), b -> completeSelectedTask())
            .bounds(noteFieldX, contentTop + 150, 100, 20)
            .build();
        this.addRenderableWidget(completeTaskBtn);

        // Set Active Quest Button
        setActiveBtn = Button.builder(I18n.ui("activate_with_icon"), b -> setActiveQuest())
            .bounds(noteFieldX + 110, contentTop + 150, 80, 20)
            .build();
        this.addRenderableWidget(setActiveBtn);

        // Close Button
        this.addRenderableWidget(Button.builder(I18n.ui("close"), b -> onClose())
            .bounds(width / 2 - 50, height - 28, 100, 20)
            .build());

        // === NEW: Endurance Quest Button ===
        newEnduranceBtn = Button.builder(I18n.ui("endurance_quest"), b -> openEnduranceModal())
            .bounds(PADDING, height - 28, 90, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(I18n.translate("devmod.quest.create_endurance")))
            .build();
        this.addRenderableWidget(newEnduranceBtn);

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
        mobSearchField = new EditBox(font, modalX + 10, modalY + 35, modalWidth - 20, 18, I18n.ui("search"));
        mobSearchField.setHint(I18n.translate("devmod.quest.search_mobs"));
        mobSearchField.setResponder(this::filterMobs);
        mobSearchField.setVisible(false);
        this.addRenderableWidget(mobSearchField);

        // Waves control buttons
        wavesMinusBtn = Button.builder(I18n.ui("minus_symbol"), b -> adjustWaves(-1))
            .bounds(modalX + modalWidth - 150, modalY + modalHeight - 70, 20, 20)
            .build();
        wavesMinusBtn.visible = false;
        this.addRenderableWidget(wavesMinusBtn);

        wavesPlusBtn = Button.builder(I18n.ui("plus_symbol"), b -> adjustWaves(1))
            .bounds(modalX + modalWidth - 80, modalY + modalHeight - 70, 20, 20)
            .build();
        wavesPlusBtn.visible = false;
        this.addRenderableWidget(wavesPlusBtn);

        // Endless mode toggle
        endlessModeBtn = Button.builder(I18n.translate("devmod.endurance.endless_off"), b -> toggleEndlessMode())
            .bounds(modalX + 10, modalY + modalHeight - 70, 100, 20)
            .build();
        endlessModeBtn.visible = false;
        this.addRenderableWidget(endlessModeBtn);

        // Start button
        startEnduranceBtn = Button.builder(I18n.ui("start_quest_with_icon"), b -> startEnduranceQuest())
            .bounds(modalX + modalWidth - 110, modalY + modalHeight - 35, 100, 25)
            .build();
        startEnduranceBtn.visible = false;
        this.addRenderableWidget(startEnduranceBtn);

        // Cancel button
        cancelEnduranceBtn = Button.builder(I18n.ui("cancel"), b -> closeEnduranceModal())
            .bounds(modalX + 10, modalY + modalHeight - 35, 80, 25)
            .build();
        cancelEnduranceBtn.visible = false;
        this.addRenderableWidget(cancelEnduranceBtn);
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int contentTop = HEADER_HEIGHT + PADDING;

        // === Header ===
        graphics.fill(0, 0, width, HEADER_HEIGHT, UIConstants.Background.HEADER);
        graphics.drawCenteredString(font, "Quest Editor", width / 2, 10, UIConstants.Text.TITLE);

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
        g.fill(x, y, x + QUEST_LIST_WIDTH, y + panelHeight, UIConstants.Background.PANEL);
        g.fill(x, y, x + QUEST_LIST_WIDTH, y + 1, UIConstants.Border.DEFAULT);
        g.fill(x, y + panelHeight - 1, x + QUEST_LIST_WIDTH, y + panelHeight, UIConstants.Border.DEFAULT);
        g.fill(x, y, x + 1, y + panelHeight, UIConstants.Border.DEFAULT);
        g.fill(x + QUEST_LIST_WIDTH - 1, y, x + QUEST_LIST_WIDTH, y + panelHeight, UIConstants.Border.DEFAULT);

        // Header
        g.drawString(font, "Quest", x + 5, y + 5, UIConstants.Text.TITLE, false);

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
            int color = isActive ? UIConstants.Accent.GOLD : (quest.isComplete() ? UIConstants.Accent.GREEN : UIConstants.Text.PRIMARY);
            g.drawString(font, name, x + 5, itemY + 2, color, false);

            // Progress indicator
            String progress = quest.getProgressSummary();
            g.drawString(font, progress, x + QUEST_LIST_WIDTH - font.width(progress) - 25, itemY + 2, UIConstants.Text.MUTED, false);
        }
    }

    private void renderTaskListPanel(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int panelHeight = height - y - 65;

        // Panel background
        g.fill(x, y, x + TASK_LIST_WIDTH, y + panelHeight, UIConstants.Background.PANEL);
        g.fill(x, y, x + TASK_LIST_WIDTH, y + 1, UIConstants.Border.DEFAULT);
        g.fill(x, y + panelHeight - 1, x + TASK_LIST_WIDTH, y + panelHeight, UIConstants.Border.DEFAULT);
        g.fill(x, y, x + 1, y + panelHeight, UIConstants.Border.DEFAULT);
        g.fill(x + TASK_LIST_WIDTH - 1, y, x + TASK_LIST_WIDTH, y + panelHeight, UIConstants.Border.DEFAULT);

        // Header
        g.drawString(font, "Task", x + 5, y + 5, UIConstants.Text.TITLE, false);

        if (selectedQuest == null) {
            g.drawString(font, "Seleziona una quest", x + 10, y + 30, UIConstants.Text.MUTED, false);
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
            int color = task.isCompleted() ? UIConstants.Accent.GREEN : (isCurrent ? UIConstants.Accent.GOLD : UIConstants.Text.PRIMARY);
            g.drawString(font, desc, x + 5, itemY + 2, color, false);

            // Note indicator
            if (task.hasNote()) {
                g.drawString(font, "\u270E", x + TASK_LIST_WIDTH - 15, itemY + 2, UIConstants.Accent.BLUE, false);
            }
        }
    }

    private void renderDetailsPanel(GuiGraphics g, int x, int y, int panelWidth) {
        if (panelWidth < 50) return;

        // Section: Quest Note
        g.drawString(font, "Nota Quest:", x, y + 25, UIConstants.Text.MUTED, false);

        // Section: Task Note
        g.drawString(font, "Nota Task:", x, y + 105, UIConstants.Text.MUTED, false);

        // Current selection info
        if (selectedQuest != null) {
            g.drawString(font, "Quest: " + selectedQuest.getName(), x, y + 5, UIConstants.Text.TITLE, false);
        }

        if (selectedTask != null) {
            g.drawString(font, "Task: " + truncate(selectedTask.getDescription(), panelWidth / 6), x, y + 85, UIConstants.Text.TITLE, false);
        }

        // Help text
        int helpY = y + 190;
        g.drawString(font, "Keybind:", x, helpY, UIConstants.Text.MUTED, false);
        g.drawString(font, "] = Complete task", x, helpY + 12, UIConstants.Text.DISABLED, false);
        g.drawString(font, "\\ = Toggle HUD", x, helpY + 24, UIConstants.Text.DISABLED, false);
        g.drawString(font, "[ = Open editor", x, helpY + 36, UIConstants.Text.DISABLED, false);
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

        int contentTop = HEADER_HEIGHT + PADDING;

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

    // === Actions ===

    private void addNewQuest() {
        String name = newQuestNameField.getValue().trim();
        if (name.isEmpty()) {
            name = "Nuova Quest " + (QuestManager.INSTANCE.getAllQuests().size() + 1);
        }
        QuestData newQuest = new QuestData("quest_" + System.currentTimeMillis(), name);
        newQuest.addTask(new QuestTask("task_1", "Prima task"));
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
            questNoteField.setValue(selectedQuest != null && selectedQuest.hasQuestNote() ? selectedQuest.getQuestNote() : "");
        }
        if (taskNoteField != null) {
            taskNoteField.setValue(selectedTask != null && selectedTask.hasNote() ? selectedTask.getNote() : "");
        }
    }

    private void updateButtonStates() {
        if (deleteQuestBtn != null) {
            deleteQuestBtn.active = selectedQuest != null;
        }
        if (addTaskBtn != null) {
            addTaskBtn.active = selectedQuest != null;
        }
        if (deleteTaskBtn != null) {
            deleteTaskBtn.active = selectedTask != null;
        }
        if (completeTaskBtn != null) {
            completeTaskBtn.active = selectedTask != null && !selectedTask.isCompleted();
        }
        if (setActiveBtn != null) {
            setActiveBtn.active = selectedQuest != null && selectedQuest != QuestManager.INSTANCE.getActiveQuest();
        }
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(sb.toString() + c + ellipsis) > maxWidth) break;
            sb.append(c);
        }
        return sb + ellipsis;
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
        if (wavesMinusBtn != null) wavesMinusBtn.visible = visible;
        if (wavesPlusBtn != null) wavesPlusBtn.visible = visible;
        if (endlessModeBtn != null) endlessModeBtn.visible = visible;
        if (startEnduranceBtn != null) startEnduranceBtn.visible = visible;
        if (cancelEnduranceBtn != null) cancelEnduranceBtn.visible = visible;
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
        if (endlessModeBtn != null) {
            endlessModeBtn.setMessage(I18n.translate(enduranceEndless ? "devmod.endurance.endless_on" : "devmod.endurance.endless_off"));
        }
    }

    private void updateEnduranceButtonStates() {
        if (startEnduranceBtn != null) {
            startEnduranceBtn.active = selectedMob != null;
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
        graphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, UIConstants.Background.PANEL_SOLID);
        graphics.fill(modalX, modalY, modalX + modalWidth, modalY + 1, UIConstants.Border.DEFAULT);
        graphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, UIConstants.Border.DEFAULT);
        graphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, UIConstants.Border.DEFAULT);
        graphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, UIConstants.Border.DEFAULT);

        // Title
        graphics.drawCenteredString(font, "\u2694 New Endurance Quest", modalX + modalWidth / 2, modalY + 10, UIConstants.Accent.GOLD);

        // Mob list area
        int listX = modalX + 10;
        int listY = modalY + 58;
        int listWidth = modalWidth - 20;
        int listHeight = modalHeight - 145;

        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, UIConstants.Background.PANEL);

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
                graphics.fill(listX + 1, itemY, listX + listWidth - 1, itemY + itemHeight, UIConstants.Background.ACTIVE);
            } else if (isHovered) {
                graphics.fill(listX + 1, itemY, listX + listWidth - 1, itemY + itemHeight, UIConstants.Background.HOVER);
            }

            // Tier color indicator
            int tierColor = getTierColor(mob.tier);
            graphics.fill(listX + 2, itemY + 2, listX + 5, itemY + itemHeight - 2, tierColor);

            // Mob name
            graphics.drawString(font, mob.displayName, listX + 10, itemY + 5, UIConstants.Text.PRIMARY, false);

            // Tier badge
            String tierText = mob.tier.name();
            int tierTextWidth = font.width(tierText);
            graphics.drawString(font, tierText, listX + listWidth - tierTextWidth - 10, itemY + 5, tierColor, false);
        }

        // Scrollbar
        if (filteredMobs.size() > maxVisible) {
            int scrollbarHeight = (int) ((float) listHeight / filteredMobs.size() * maxVisible * itemHeight);
            int scrollbarY = listY + (int) ((float) mobListScroll / (filteredMobs.size() - maxVisible) * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 3, listY, listX + listWidth, listY + listHeight, UIConstants.Border.SEPARATOR);
            graphics.fill(listX + listWidth - 3, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, UIConstants.Border.DEFAULT);
        }

        // Stats info
        graphics.drawString(font, filteredMobs.size() + " mobs available", listX, listY + listHeight + 5, UIConstants.Text.SECONDARY, false);

        // Wave count display
        String waveText = "Waves: " + enduranceWaves;
        graphics.drawString(font, waveText, modalX + modalWidth - 125, modalY + modalHeight - 65, UIConstants.Text.PRIMARY, false);

        // Selected mob info
        if (selectedMob != null) {
            int infoY = modalY + modalHeight - 95;
            graphics.drawString(font, "Selected: " + selectedMob.displayName, listX, infoY, UIConstants.Accent.GREEN, false);
        }
    }

    private int getTierColor(EnduranceQuestRegistry.MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> UIConstants.Text.MUTED;
            case EASY -> UIConstants.Accent.GREEN;
            case MEDIUM -> UIConstants.Accent.GOLD;
            case HARD -> UIConstants.Accent.ORANGE;
            case ELITE -> UIConstants.Accent.PURPLE;
            case BOSS -> UIConstants.Accent.RED;
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
