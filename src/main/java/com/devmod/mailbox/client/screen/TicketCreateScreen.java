package com.devmod.mailbox.client.screen;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.mailbox.client.ClientTicketCache;
import com.devmod.mailbox.client.MailboxUiTheme;
import com.devmod.mailbox.network.payload.TicketCreatePayload;
import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketPriority;

/**
 * Screen for creating a new support ticket.
 */
@OnlyIn(Dist.CLIENT)
public class TicketCreateScreen extends Screen {

    private static final int PANEL_WIDTH = 450;
    private static final int PANEL_HEIGHT = 350;

    @Nullable
    private final Screen parentScreen;

    private int panelX, panelY;

    // Form fields
    @Nullable private EditBox subjectField;
    @Nullable private EditBox descriptionField;

    // Selected category and priority
    private TicketCategory selectedCategory = TicketCategory.BUG;
    private TicketPriority selectedPriority = TicketPriority.NORMAL;

    // Buttons
    @Nullable private EditorButton submitButton;
    @Nullable private EditorButton cancelButton;

    // Category buttons
    @Nullable private EditorButton[] categoryButtons;

    // Status
    @Nullable private String statusMessage;
    private int statusColor = DesignTokens.Text.MUTED();
    private long statusMessageAt = 0;
    private static final long STATUS_TTL_MS = 5000;

    public TicketCreateScreen(@Nullable Screen parent) {
        super(Objects.requireNonNull(Component.translatable("devmod.ticket.create.title")));
        this.parentScreen = parent;
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

        // Subject field
        final EditBox subjectBox = new EditBox(getFont(), panelX + 100, panelY + 60, PANEL_WIDTH - 120, 20,
            Objects.requireNonNull(Component.literal("Subject")));
        subjectBox.setMaxLength(100);
        subjectBox.setHint(Objects.requireNonNull(Component.literal("Brief summary of the issue...")));
        addRenderableWidget(subjectBox);
        subjectField = subjectBox;

        // Description field (multi-line simulation with larger edit box)
        final EditBox descBox = new EditBox(getFont(), panelX + 100, panelY + 160, PANEL_WIDTH - 120, 20,
            Objects.requireNonNull(Component.literal("Description")));
        descBox.setMaxLength(1000);
        descBox.setHint(Objects.requireNonNull(Component.literal("Describe your issue in detail...")));
        addRenderableWidget(descBox);
        descriptionField = descBox;

        // Category buttons (positioned during render, not init)
        final EditorButton[] catButtons = new EditorButton[TicketCategory.values().length];
        for (int i = 0; i < TicketCategory.values().length; i++) {
            TicketCategory cat = TicketCategory.values()[i];
            final int index = i;
            catButtons[i] = EditorButton.builder("cat_" + cat.name(), cat.getDisplayName())
                .style(cat == selectedCategory ? EditorButton.Style.PRIMARY : EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .onClick(() -> selectCategory(index))
                .build();
        }
        categoryButtons = catButtons;

        // Submit button
        submitButton = EditorButton.builder("submit", "Submit Ticket")
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onSubmitClicked)
            .build();

        // Cancel button
        cancelButton = EditorButton.builder("cancel", "Cancel")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onCancelClicked)
            .build();
    }

    private void selectCategory(int index) {
        selectedCategory = TicketCategory.values()[index];
        selectedPriority = selectedCategory.getDefaultPriority();

        // Update button styles
        final EditorButton[] catBtns = categoryButtons;
        if (catBtns != null) {
            for (int i = 0; i < catBtns.length; i++) {
                if (catBtns[i] != null) {
                    catBtns[i].style(i == index ? EditorButton.Style.PRIMARY : EditorButton.Style.GHOST);
                }
            }
        }
        UiSounds.click();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, MailboxUiTheme.Panel.BG);

        // Border
        int borderColor = DesignTokens.Border.DEFAULT();
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, borderColor);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);

        // Title
        String title = "Create New Ticket";
        graphics.drawString(getFont(), title, panelX + 15, panelY + 15, DesignTokens.Text.PRIMARY(), false);

        // Separator
        graphics.fill(panelX + 10, panelY + 35, panelX + PANEL_WIDTH - 10, panelY + 36, DesignTokens.Border.DEFAULT());

        // Subject label
        graphics.drawString(getFont(), "Subject:", panelX + 15, panelY + 65, DesignTokens.Text.SECONDARY(), false);

        // Category label
        graphics.drawString(getFont(), "Category:", panelX + 15, panelY + 100, DesignTokens.Text.SECONDARY(), false);

        // Render category buttons
        int catX = panelX + 100;
        int catY = panelY + 95;
        int catWidth = 80;

        if (categoryButtons != null) {
            for (EditorButton btn : categoryButtons) {
                if (btn != null) {
                    btn.render(graphics, catX, catY, catWidth, 22, mouseX, mouseY);
                    catX += catWidth + 5;
                    if (catX + catWidth > panelX + PANEL_WIDTH - 20) {
                        catX = panelX + 100;
                        catY += 26;
                    }
                }
            }
        }

        // Description label
        graphics.drawString(getFont(), "Description:", panelX + 15, panelY + 165, DesignTokens.Text.SECONDARY(), false);

        // Priority indicator
        String priorityText = "Priority: " + selectedPriority.getDisplayName();
        int priorityColor = getPriorityColor(selectedPriority);
        graphics.drawString(getFont(), priorityText, panelX + 15, panelY + 200, priorityColor, false);

        // Hint text
        String hint = "Tip: Select a category to set the default priority. Abuse reports are high priority.";
        graphics.drawString(getFont(), hint, panelX + 15, panelY + 220, DesignTokens.Text.MUTED(), false);

        // Buttons
        int buttonY = panelY + PANEL_HEIGHT - 40;
        if (cancelButton != null) {
            cancelButton.render(graphics, panelX + 15, buttonY, 80, 24, mouseX, mouseY);
        }
        if (submitButton != null) {
            submitButton.render(graphics, panelX + PANEL_WIDTH - 130, buttonY, 110, 24, mouseX, mouseY);
        }

        // Status message
        renderStatus(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (statusMessage == null) return;

        long age = System.currentTimeMillis() - statusMessageAt;
        if (age > STATUS_TTL_MS) {
            statusMessage = null;
            return;
        }

        int y = panelY + PANEL_HEIGHT - 60;
        graphics.drawString(getFont(), statusMessage, panelX + 15, y, statusColor, false);
    }

    private int getPriorityColor(TicketPriority priority) {
        return switch (priority) {
            case LOW -> DesignTokens.Text.MUTED();
            case NORMAL -> DesignTokens.Text.PRIMARY();
            case HIGH -> DesignTokens.Accent.GOLD();
            case URGENT -> DesignTokens.Accent.RED();
        };
    }

    private void onSubmitClicked() {
        final EditBox subjectBox = subjectField;
        final EditBox descBox = descriptionField;
        if (subjectBox == null || descBox == null) return;

        String subject = subjectBox.getValue().trim();
        String description = descBox.getValue().trim();

        // Validation
        if (subject.isEmpty()) {
            setStatusMessage("Please enter a subject", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }

        if (subject.length() < 5) {
            setStatusMessage("Subject must be at least 5 characters", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }

        if (description.isEmpty()) {
            setStatusMessage("Please enter a description", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }

        if (description.length() < 20) {
            setStatusMessage("Description must be at least 20 characters", DesignTokens.Status.WARNING());
            UiSounds.warning();
            return;
        }

        // Send to server
        PacketDistributor.sendToServer(new TicketCreatePayload(
            selectedCategory,
            selectedPriority,
            subject,
            description
        ));

        // Optimistic update - add to cache
        UUID tempId = UUID.randomUUID();
        ClientTicketCache.addTicket(new ClientTicketCache.TicketData(
            tempId,
            selectedCategory,
            selectedPriority,
            com.devmod.mailbox.ticket.TicketStatus.OPEN,
            subject,
            description,
            java.time.Instant.now(),
            null,
            0
        ));

        setStatusMessage("Ticket submitted!", DesignTokens.Status.SUCCESS());
        UiSounds.success();

        // Close after short delay
        Minecraft.getInstance().tell(() -> onCancelClicked());
    }

    private void onCancelClicked() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(parentScreen);
        UiSounds.click();
    }

    private void setStatusMessage(String message, int color) {
        statusMessage = message;
        statusColor = color;
        statusMessageAt = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Category buttons (use internal bounds from last render)
        final EditorButton[] catBtns = categoryButtons;
        if (catBtns != null) {
            for (EditorButton btn : catBtns) {
                if (btn != null && btn.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        // Submit/Cancel buttons
        if (submitButton != null && submitButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (cancelButton != null && cancelButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (categoryButtons != null) {
            for (EditorButton btn : categoryButtons) {
                if (btn != null) {
                    btn.mouseReleased(mouseX, mouseY, button);
                }
            }
        }
        if (submitButton != null) submitButton.mouseReleased(mouseX, mouseY, button);
        if (cancelButton != null) cancelButton.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape to close
        if (keyCode == 256 /* ESCAPE */) {
            onCancelClicked();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
