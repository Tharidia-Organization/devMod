package com.devmod.mailbox.client.screen;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.mailbox.client.ClientMailboxAccess;
import com.devmod.mailbox.client.ClientTaskCache;
import com.devmod.mailbox.client.MailboxUiTheme;
import com.devmod.mailbox.network.payload.TaskActionPayload;
import com.devmod.mailbox.task.TestTask;

/**
 * Screen for testers to view and manage their assigned tasks.
 */
@OnlyIn(Dist.CLIENT)
public class TesterTaskScreen extends Screen {

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 300;
    private static final int TASK_HEIGHT = 60;
    private static final int PADDING = 10;

    private int panelX;
    private int panelY;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    @Nullable
    private UUID selectedTaskId = null;
    @Nullable
    private final UUID initialTaskId;
    private final boolean openNotesOnInit;

    // Filter
    private boolean showCompleted = false;

    // Buttons (created once in init)
    @Nullable private Button startButton;
    @Nullable private Button completeButton;
    @Nullable private Button notesButton;
    @Nullable private Button submitNotesButton;
    @Nullable private EditBox notesEditBox;

    // Notes editing state
    private boolean showNotesEditor = false;

    public TesterTaskScreen() {
        this(null, false);
    }

    private TesterTaskScreen(@Nullable UUID initialTaskId, boolean openNotesOnInit) {
        super(Objects.requireNonNull(Component.translatable("devmod.tester.title"), "title"));
        this.initialTaskId = initialTaskId;
        this.openNotesOnInit = openNotesOnInit;
    }

    @Nonnull
    private net.minecraft.client.gui.Font getFont() {
        return Objects.requireNonNull(this.font, "Font not initialized");
    }

    @Override
    protected void init() {
        super.init();

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        // Filter toggle button
        addRenderableWidget(Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.translatable(showCompleted ? "devmod.tester.hide_completed" : "devmod.tester.show_completed"), "filter label"),
                btn -> {
                    showCompleted = !showCompleted;
                    btn.setMessage(Objects.requireNonNull(Component.translatable(showCompleted ? "devmod.tester.hide_completed" : "devmod.tester.show_completed"), "filter label"));
                    scrollOffset = 0;
                    updateMaxScroll();
                    updateActionButtons();
                })
            .bounds(panelX + PANEL_WIDTH - 120, panelY + 5, 110, 20)
            .build(), "filter button"));

        // Close button
        addRenderableWidget(Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.translatable("devmod.ui.close"), "close label"),
                btn -> onClose())
            .bounds(panelX + PANEL_WIDTH - 60, panelY + PANEL_HEIGHT - 30, 50, 20)
            .build(), "close button"));

        // Action buttons for task details (created but initially hidden)
        int detailX = panelX + PANEL_WIDTH / 2 + PADDING;
        int detailWidth = PANEL_WIDTH / 2 - PADDING * 2;
        int btnY = panelY + 35 + (PANEL_HEIGHT - 70) - 45;
        int btnWidth = (detailWidth - 15) / 2;

        startButton = addRenderableWidget(Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.translatable("devmod.tester.start"), "start label"),
                btn -> {
                    if (selectedTaskId != null) {
                        updateTaskStatus(selectedTaskId, TestTask.TaskStatus.IN_PROGRESS);
                    }
                })
            .bounds(detailX + 5, btnY, btnWidth, 20)
            .build(), "start button"));

        completeButton = addRenderableWidget(Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.translatable("devmod.tester.complete"), "complete label"),
                btn -> {
                    if (selectedTaskId != null) {
                        updateTaskStatus(selectedTaskId, TestTask.TaskStatus.COMPLETED);
                    }
                })
            .bounds(detailX + 5, btnY, btnWidth, 20)
            .build(), "complete button"));

        // Notes button (second column, same row as start/complete)
        notesButton = addRenderableWidget(Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.translatable("devmod.tester.add_notes"), "notes label"),
                btn -> toggleNotesEditor())
            .bounds(detailX + btnWidth + 10, btnY, btnWidth, 20)
            .build(), "notes button"));

        // Notes input area (initially hidden)
        int notesY = btnY - 60;
        EditBox notesBox = addRenderableWidget(new EditBox(
            getFont(),
            detailX + 5, notesY,
            detailWidth - 10, 40,
            Objects.requireNonNull(Component.translatable("devmod.tester.notes_placeholder"), "notes placeholder")
        ));
        notesBox.setMaxLength(500);
        notesBox.visible = false;
        notesEditBox = notesBox;

        Button submitBtn = addRenderableWidget(Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.translatable("devmod.tester.submit_notes"), "submit notes label"),
                btn -> submitNotes())
            .bounds(detailX + detailWidth - 75, notesY + 45, 70, 20)
            .build(), "submit notes button"));
        submitBtn.visible = false;
        submitNotesButton = submitBtn;

        applyInitialSelection();
        updateMaxScroll();
        updateActionButtons();
        if (openNotesOnInit && selectedTaskId != null) {
            toggleNotesEditor();
        }
    }

    /**
     * Toggle the notes editor visibility.
     */
    private void toggleNotesEditor() {
        showNotesEditor = !showNotesEditor;
        EditBox editBox = notesEditBox;
        if (editBox != null) {
            editBox.visible = showNotesEditor;
            if (showNotesEditor) {
                // Pre-fill with existing notes if available
                TestTask task = selectedTaskId != null ? ClientTaskCache.getTask(selectedTaskId) : null;
                String notes = task != null ? task.notes() : null;
                if (notes != null) {
                    editBox.setValue(notes);
                } else {
                    editBox.setValue("");
                }
                editBox.setFocused(true);
            }
        }
        Button submitBtn = submitNotesButton;
        if (submitBtn != null) {
            submitBtn.visible = showNotesEditor;
        }
    }

    /**
     * Submit the notes to the server.
     */
    private void submitNotes() {
        UUID taskId = selectedTaskId;
        EditBox editBox = notesEditBox;
        if (taskId == null || editBox == null) return;

        String notes = editBox.getValue().trim();
        if (notes.isEmpty()) return;

        // Optimistic update
        ClientTaskCache.updateTaskNotes(taskId, notes);

        // Send to server
        PacketDistributor.sendToServer(Objects.requireNonNull(
            TaskActionPayload.addNotes(taskId, notes), "addNotes payload"));

        // Hide the editor
        showNotesEditor = false;
        editBox.visible = false;
        Button submitBtn = submitNotesButton;
        if (submitBtn != null) submitBtn.visible = false;
    }

    /**
     * Update visibility of action buttons based on selected task status.
     */
    private void updateActionButtons() {
        Button startBtn = startButton;
        Button completeBtn = completeButton;
        Button notesBtn = notesButton;
        EditBox editBox = notesEditBox;
        Button submitBtn = submitNotesButton;

        if (startBtn == null || completeBtn == null) return;

        // Hide notes editor when task changes
        showNotesEditor = false;
        if (editBox != null) editBox.visible = false;
        if (submitBtn != null) submitBtn.visible = false;

        if (selectedTaskId == null) {
            startBtn.visible = false;
            completeBtn.visible = false;
            if (notesBtn != null) notesBtn.visible = false;
            return;
        }

        TestTask task = ClientTaskCache.getTask(selectedTaskId);
        if (task == null) {
            startBtn.visible = false;
            completeBtn.visible = false;
            if (notesBtn != null) notesBtn.visible = false;
            return;
        }

        startBtn.visible = task.status() == TestTask.TaskStatus.PENDING;
        completeBtn.visible = task.status() == TestTask.TaskStatus.IN_PROGRESS;
        if (notesBtn != null) notesBtn.visible = true;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Main panel
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, MailboxUiTheme.TesterTasks.PANEL_BG);
        graphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, MailboxUiTheme.TesterTasks.PANEL_OUTLINE);

        // Title
        graphics.drawCenteredString(getFont(), Objects.requireNonNull(title, "title"), width / 2, panelY + 10,
            MailboxUiTheme.TesterTasks.TEXT_PRIMARY);

        // Task count
        int pending = ClientTaskCache.getPendingCount();
        int total = ClientTaskCache.getTaskCount();
        String countText = pending + "/" + total + " pending";
        graphics.drawString(getFont(), countText, panelX + PADDING, panelY + 10, MailboxUiTheme.TesterTasks.TEXT_MUTED);

        // Render task list
        renderTaskList(graphics, mouseX, mouseY);

        // Render selected task details
        if (selectedTaskId != null) {
            renderTaskDetails(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTaskList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<TestTask> tasks = getFilteredTasks();
        int listX = panelX + PADDING;
        int listY = panelY + 35;
        int listWidth = selectedTaskId != null ? (PANEL_WIDTH / 2) - PADDING * 2 : PANEL_WIDTH - PADDING * 2;
        int listHeight = PANEL_HEIGHT - 70;

        // List background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, MailboxUiTheme.TesterTasks.LIST_BG);

        // No tasks message
        if (tasks.isEmpty()) {
            graphics.drawCenteredString(getFont(),
                Objects.requireNonNull(Component.translatable("devmod.tester.no_tasks"), "no tasks"),
                listX + listWidth / 2, listY + listHeight / 2, MailboxUiTheme.TesterTasks.TEXT_DIM);
            return;
        }

        // Scissor for scrolling
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int y = listY - scrollOffset;
        for (TestTask task : tasks) {
            if (y + TASK_HEIGHT > listY && y < listY + listHeight) {
                boolean isSelected = task.id().equals(selectedTaskId);
                boolean isHovered = mouseX >= listX && mouseX < listX + listWidth &&
                                   mouseY >= y && mouseY < y + TASK_HEIGHT;

                renderTaskEntry(graphics, task, listX, y, listWidth, isSelected, isHovered);
            }
            y += TASK_HEIGHT + 2;
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarHeight = Math.max(20, listHeight * listHeight / ((tasks.size() * (TASK_HEIGHT + 2)) + listHeight));
            int scrollbarY = listY + (int) ((float) scrollOffset / maxScroll * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 4, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight,
                MailboxUiTheme.TesterTasks.SCROLLBAR);
        }
    }

    private void renderTaskEntry(GuiGraphics graphics, TestTask task, int x, int y, int entryWidth, boolean selected, boolean hovered) {
        // Background
        int bgColor = selected
            ? MailboxUiTheme.TesterTasks.ENTRY_SELECTED
            : (hovered ? MailboxUiTheme.TesterTasks.ENTRY_HOVER : MailboxUiTheme.TesterTasks.ENTRY_DEFAULT);
        graphics.fill(x, y, x + entryWidth, y + TASK_HEIGHT, bgColor);

        // Priority indicator
        int priorityColor = getPriorityColor(task.priority());
        graphics.fill(x, y, x + 3, y + TASK_HEIGHT, priorityColor);

        // Status icon
        int statusColor = getStatusColor(task.status());
        graphics.fill(x + 8, y + 5, x + 14, y + 11, statusColor);

        // Title
        String titleText = task.title();
        if (titleText.length() > 40) {
            titleText = titleText.substring(0, 37) + "...";
        }
        graphics.drawString(getFont(), titleText, x + 20, y + 5, MailboxUiTheme.TesterTasks.TEXT_PRIMARY);

        // Status text
        String statusText = getStatusText(task.status());
        graphics.drawString(getFont(), statusText, x + 20, y + 18, statusColor);

        // Assigned by
        if (task.assignedByName() != null) {
            graphics.drawString(getFont(), "From: " + task.assignedByName(), x + 20, y + 31,
                MailboxUiTheme.TesterTasks.TEXT_DIM);
        }

        // Due date warning
        Long dueAt = task.dueAt();
        if (task.isOverdue()) {
            graphics.drawString(getFont(), "OVERDUE", x + entryWidth - 60, y + 5,
                MailboxUiTheme.TesterTasks.DUE_OVERDUE);
        } else if (dueAt != null) {
            long remaining = dueAt - System.currentTimeMillis();
            if (remaining < 86400000) { // Less than 24 hours
                graphics.drawString(getFont(), "Due soon", x + entryWidth - 60, y + 5,
                    MailboxUiTheme.TesterTasks.DUE_SOON);
            }
        }
    }

    private void renderTaskDetails(GuiGraphics graphics) {
        UUID taskId = selectedTaskId;
        if (taskId == null) {
            return;
        }
        TestTask task = ClientTaskCache.getTask(taskId);
        if (task == null) {
            selectedTaskId = null;
            updateActionButtons();
            return;
        }

        int detailX = panelX + PANEL_WIDTH / 2 + PADDING;
        int detailY = panelY + 35;
        int detailWidth = PANEL_WIDTH / 2 - PADDING * 2;
        int detailHeight = PANEL_HEIGHT - 70;

        // Background
        graphics.fill(detailX, detailY, detailX + detailWidth, detailY + detailHeight, MailboxUiTheme.TesterTasks.LIST_BG);

        // Title
        graphics.drawString(getFont(), task.title(), detailX + 5, detailY + 5, MailboxUiTheme.TesterTasks.TEXT_PRIMARY);

        // Description
        if (task.description() != null) {
            int descY = detailY + 25;
            for (String line : wrapText(task.description(), detailWidth - 10)) {
                graphics.drawString(getFont(), line, detailX + 5, descY, MailboxUiTheme.TesterTasks.TEXT_MUTED);
                descY += 12;
                if (descY > detailY + detailHeight - 80) break;
            }
        }

        // Notes
        if (task.notes() != null) {
            graphics.drawString(getFont(), "Notes: " + task.notes(), detailX + 5, detailY + detailHeight - 70,
                MailboxUiTheme.TesterTasks.TEXT_DIM);
        }
    }

    private List<TestTask> getFilteredTasks() {
        List<TestTask> tasks = ClientTaskCache.getTasks();
        if (!showCompleted) {
            tasks = tasks.stream()
                .filter(t -> t.status() != TestTask.TaskStatus.COMPLETED)
                .toList();
        }
        return tasks;
    }

    private void updateMaxScroll() {
        List<TestTask> tasks = getFilteredTasks();
        int contentHeight = tasks.size() * (TASK_HEIGHT + 2);
        int viewHeight = PANEL_HEIGHT - 70;
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    private void updateTaskStatus(UUID taskId, TestTask.TaskStatus status) {
        // Optimistic update
        ClientTaskCache.updateTaskStatus(taskId, status);

        // Send to server
        PacketDistributor.sendToServer(Objects.requireNonNull(
            TaskActionPayload.updateStatus(taskId, status), "updateStatus payload"));

        // Update button visibility
        updateActionButtons();
    }

    private void applyInitialSelection() {
        if (initialTaskId == null) {
            return;
        }
        TestTask task = ClientTaskCache.getTask(initialTaskId);
        if (task != null) {
            selectedTaskId = task.id();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Check if clicked on a task
        List<TestTask> tasks = getFilteredTasks();
        int listX = panelX + PADDING;
        int listY = panelY + 35;
        int listWidth = selectedTaskId != null ? (PANEL_WIDTH / 2) - PADDING * 2 : PANEL_WIDTH - PADDING * 2;

        int y = listY - scrollOffset;
        for (TestTask task : tasks) {
            if (mouseX >= listX && mouseX < listX + listWidth &&
                mouseY >= y && mouseY < y + TASK_HEIGHT) {
                selectedTaskId = task.id();
                updateActionButtons();
                return true;
            }
            y += TASK_HEIGHT + 2;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 20));
        return true;
    }

    private int getPriorityColor(int priority) {
        return switch (priority) {
            case 3 -> MailboxUiTheme.TesterTasks.PRIORITY_HIGH;
            case 2 -> MailboxUiTheme.TesterTasks.PRIORITY_MEDIUM;
            default -> MailboxUiTheme.TesterTasks.PRIORITY_LOW;
        };
    }

    private int getStatusColor(TestTask.TaskStatus status) {
        return switch (status) {
            case PENDING -> MailboxUiTheme.TesterTasks.STATUS_PENDING;
            case IN_PROGRESS -> MailboxUiTheme.TesterTasks.STATUS_IN_PROGRESS;
            case COMPLETED -> MailboxUiTheme.TesterTasks.STATUS_COMPLETED;
        };
    }

    private String getStatusText(TestTask.TaskStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case IN_PROGRESS -> "In Progress";
            case COMPLETED -> "Completed";
        };
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        net.minecraft.client.gui.Font fontRef = getFont();

        List<String> words = new java.util.ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == ' ') {
                if (i > start) {
                    words.add(text.substring(start, i));
                }
                start = i + 1;
            }
        }

        for (String word : words) {
            if (fontRef.width(current + " " + word) > maxWidth) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
            }
            if (!current.isEmpty()) {
                current.append(" ");
            }
            current.append(word);
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Open the tester task screen.
     */
    public static void open() {
        open(null, false);
    }

    public static void open(@Nullable UUID taskId) {
        open(taskId, false);
    }

    public static void openForNotes(@Nullable UUID taskId) {
        open(taskId, true);
    }

    private static void open(@Nullable UUID taskId, boolean openNotes) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (!ClientMailboxAccess.isTester()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.translatable("devmod.action.requires_tester"), true);
            }
            return;
        }
        mc.setScreen(new TesterTaskScreen(taskId, openNotes));
    }
}
