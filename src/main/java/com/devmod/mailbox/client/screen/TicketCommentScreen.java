package com.devmod.mailbox.client.screen;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.mailbox.client.ClientTicketCache.TicketData;
import com.devmod.mailbox.network.payload.TicketActionPayload;

/**
 * Screen for adding a comment to a ticket.
 */
@OnlyIn(Dist.CLIENT)
public class TicketCommentScreen extends Screen {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 230;
    private static final long STATUS_TTL_MS = 5000;

    @Nullable
    private final Screen parent;
    private final UUID ticketId;
    private final String subject;

    private int panelX;
    private int panelY;

    @Nullable private EditBox commentField;
    @Nullable private EditorButton submitButton;
    @Nullable private EditorButton cancelButton;

    @Nullable private String statusMessage;
    private int statusColor = DesignTokens.Text.MUTED();
    private long statusMessageAt = 0;

    public TicketCommentScreen(@Nullable Screen parent, UUID ticketId, String subject) {
        super(Objects.requireNonNull(Component.translatable("devmod.ticket.comment.title")));
        this.parent = parent;
        this.ticketId = Objects.requireNonNull(ticketId, "ticketId");
        this.subject = subject != null ? subject : "";
    }

    public static void open(@Nullable Screen parent, TicketData ticket) {
        if (ticket == null) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new TicketCommentScreen(parent, ticket.id(), ticket.subject()));
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

        EditBox field = new EditBox(
            getFont(),
            panelX + 20,
            panelY + 90,
            PANEL_WIDTH - 40,
            20,
            Objects.requireNonNull(Component.translatable("devmod.ticket.comment.placeholder"))
        );
        field.setMaxLength(1000);
        field.setHint(Objects.requireNonNull(Component.translatable("devmod.ticket.comment.placeholder")));
        field.setFocused(true);
        addRenderableWidget(field);
        commentField = field;

        submitButton = EditorButton.builder("ticket_comment_submit",
                Component.translatable("devmod.ticket.comment.submit").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onSubmit)
            .build();

        cancelButton = EditorButton.builder("ticket_comment_cancel",
                Component.translatable("devmod.ticket.comment.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onCancel)
            .build();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE8101820);
        int borderColor = DesignTokens.Border.DEFAULT();
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, borderColor);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);

        String titleLabel = Objects.requireNonNull(Component.translatable("devmod.ticket.comment.title"), "title").getString();
        graphics.drawString(getFont(), titleLabel, panelX + 15, panelY + 15, DesignTokens.Text.PRIMARY(), false);

        if (!subject.isBlank()) {
            String subjectLabel = Component.translatable("devmod.ticket.comment.subject", subject).getString();
            graphics.drawString(getFont(), subjectLabel, panelX + 15, panelY + 35, DesignTokens.Text.MUTED(), false);
        }

        graphics.drawString(getFont(),
            Component.translatable("devmod.ticket.comment.label").getString(),
            panelX + 15, panelY + 70, DesignTokens.Text.SECONDARY(), false);

        int buttonY = panelY + PANEL_HEIGHT - 35;
        if (cancelButton != null) {
            cancelButton.render(graphics, panelX + 15, buttonY, 90, 22, mouseX, mouseY);
        }
        if (submitButton != null) {
            submitButton.render(graphics, panelX + PANEL_WIDTH - 105, buttonY, 90, 22, mouseX, mouseY);
        }

        renderStatus(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (statusMessage == null) {
            return;
        }
        long age = System.currentTimeMillis() - statusMessageAt;
        if (age > STATUS_TTL_MS) {
            statusMessage = null;
            return;
        }
        graphics.drawString(getFont(), statusMessage, panelX + 15, panelY + PANEL_HEIGHT - 55, statusColor, false);
    }

    private void setStatusMessage(String message, int color) {
        statusMessage = message;
        statusColor = color;
        statusMessageAt = System.currentTimeMillis();
    }

    private void onSubmit() {
        EditBox field = commentField;
        if (field == null) {
            return;
        }

        String comment = field.getValue().trim();
        if (comment.isEmpty()) {
            setStatusMessage(
                Component.translatable("devmod.ticket.comment.empty").getString(),
                DesignTokens.Status.WARNING()
            );
            DesignTokens.Sound.warning();
            return;
        }

        PacketDistributor.sendToServer(Objects.requireNonNull(TicketActionPayload.addComment(ticketId, comment)));
        DesignTokens.Sound.click();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void onCancel() {
        DesignTokens.Sound.click();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (submitButton != null && submitButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (cancelButton != null && cancelButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (submitButton != null) {
            submitButton.mouseReleased(mouseX, mouseY, button);
        }
        if (cancelButton != null) {
            cancelButton.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 /* ESC */) {
            onCancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
