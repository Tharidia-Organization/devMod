package com.devmod.client.area;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.area.network.CloneAreaPayload;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;

/**
 * Dialog for cloning an area to a new location with optional modifications.
 */
@OnlyIn(Dist.CLIENT)
public final class CloneAreaDialog extends BaseOverlay {

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 280;
    // UX-07 fix: Increased to 10 seconds, or until user retries
    private static final long ERROR_DISPLAY_DURATION = 10_000L;

    private final UUID sourceAreaId;
    private final String sourceName;
    private final Runnable onComplete;

    @Nullable private EditBox nameField;
    @Nullable private EditBox xField;
    @Nullable private EditBox yField;
    @Nullable private EditBox zField;
    @Nullable private Checkbox keepZoneCheckbox;
    private final EditorButton cloneButton;
    private final EditorButton cancelButton;

    private String newName;
    private String xValue = "0";
    private String yValue = "0";
    private String zValue = "0";
    private boolean keepLinkedZone = false;

    @Nullable private String errorMessage;
    private long errorDisplayTime;

    private int padding;
    private int btnY;
    private int btnWidth;
    private int btnHeight;
    private int cloneX;
    private int cancelX;

    public CloneAreaDialog(UUID sourceAreaId, String sourceName, Runnable onComplete) {
        this.sourceAreaId = Objects.requireNonNull(sourceAreaId);
        this.sourceName = Objects.requireNonNull(sourceName);
        this.newName = sourceName + " (Clone)";
        this.onComplete = Objects.requireNonNull(onComplete);

        this.cloneButton = new EditorButton("clone",
            Component.translatable("area.clone.confirm").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::doClone);

        this.cancelButton = new EditorButton("cancel",
            Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::hide);
    }

    @Override
    protected int getPanelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return PANEL_HEIGHT;
    }

    @Override
    public void show() {
        super.show();
        // Initialize position from player
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            BlockPos pos = mc.player.blockPosition();
            xValue = String.valueOf(pos.getX());
            yValue = String.valueOf(pos.getY());
            zValue = String.valueOf(pos.getZ());
        }
        newName = sourceName + " (Clone)";
        keepLinkedZone = false;
        // Reset fields - extract to local to satisfy null checker
        EditBox name = nameField;
        if (name != null) name.setValue(Objects.requireNonNull(newName));
        EditBox x = xField;
        if (x != null) x.setValue(Objects.requireNonNull(xValue));
        EditBox y = yField;
        if (y != null) y.setValue(Objects.requireNonNull(yValue));
        EditBox z = zField;
        if (z != null) z.setValue(Objects.requireNonNull(zValue));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                 int x, int y, int width, int height,
                                 int mouseX, int mouseY) {
        padding = ScaledCoord.scaleDim(DesignTokens.Spacing.XL);
        int labelHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int fieldHeight = ScaledCoord.scaleDim(20);
        int coordFieldWidth = ScaledCoord.scaleDim(60);

        // Title
        Typography.drawText(graphics, font,
            Component.translatable("area.clone.title", sourceName).getString(),
            x + padding, y + padding,
            DesignTokens.Text.TITLE(),
            Typography.withUiScale(Typography.BODY));

        // New name label
        int nameLabelY = y + padding + UIScaleManager.getScaledLineHeight() + ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.clone.new_name")),
            x + padding, nameLabelY,
            DesignTokens.Text.SECONDARY());

        // Name field
        int nameFieldY = nameLabelY + labelHeight + 4;
        int nameFieldWidth = width - padding * 2;

        EditBox nameBox = nameField;
        if (nameBox == null) {
            nameBox = new EditBox(font, x + padding, nameFieldY, nameFieldWidth, fieldHeight,
                Objects.requireNonNull(Component.translatable("area.clone.new_name")));
            nameBox.setMaxLength(64);
            nameBox.setValue(Objects.requireNonNull(newName));
            nameBox.setBordered(false);
            nameBox.setResponder(val -> newName = val != null ? val : "");
            nameField = nameBox;
        }
        nameBox.setX(x + padding);
        nameBox.setY(nameFieldY);
        nameBox.setWidth(nameFieldWidth);

        AxiomRenderer.drawInputBackground(graphics, nameBox.getX(), nameBox.getY(),
            nameBox.getWidth(), nameBox.getHeight(), nameBox.isFocused());
        nameBox.render(graphics, mouseX, mouseY, 0);

        // Position label
        int posLabelY = nameFieldY + fieldHeight + ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.clone.position")),
            x + padding, posLabelY,
            DesignTokens.Text.SECONDARY());

        // Position fields
        int posFieldY = posLabelY + UIScaleManager.getScaledLineHeight() + 4;
        int coordGap = ScaledCoord.scaleDim(DesignTokens.Spacing.SM);
        int labelWidth = UIScaleManager.getScaledStringWidth(font, "X: ");

        // X field
        UIScaleManager.drawScaledString(graphics, font, "X:", x + padding, posFieldY + 5, DesignTokens.Text.MUTED());
        EditBox xBox = xField;
        if (xBox == null) {
            xBox = new EditBox(font, x + padding + labelWidth, posFieldY, coordFieldWidth, fieldHeight,
                Objects.requireNonNull(Component.literal("X")));
            xBox.setValue(Objects.requireNonNull(xValue));
            xBox.setBordered(false);
            xBox.setResponder(val -> xValue = val != null ? val : "0");
            xField = xBox;
        }
        xBox.setX(x + padding + labelWidth);
        xBox.setY(posFieldY);
        AxiomRenderer.drawInputBackground(graphics, xBox.getX(), xBox.getY(),
            xBox.getWidth(), xBox.getHeight(), xBox.isFocused());
        xBox.render(graphics, mouseX, mouseY, 0);

        // Y field
        int yFieldX = x + padding + labelWidth + coordFieldWidth + coordGap + labelWidth;
        UIScaleManager.drawScaledString(graphics, font, "Y:", yFieldX - labelWidth, posFieldY + 5, DesignTokens.Text.MUTED());
        EditBox yBox = yField;
        if (yBox == null) {
            yBox = new EditBox(font, yFieldX, posFieldY, coordFieldWidth, fieldHeight,
                Objects.requireNonNull(Component.literal("Y")));
            yBox.setValue(Objects.requireNonNull(yValue));
            yBox.setBordered(false);
            yBox.setResponder(val -> yValue = val != null ? val : "0");
            yField = yBox;
        }
        yBox.setX(yFieldX);
        yBox.setY(posFieldY);
        AxiomRenderer.drawInputBackground(graphics, yBox.getX(), yBox.getY(),
            yBox.getWidth(), yBox.getHeight(), yBox.isFocused());
        yBox.render(graphics, mouseX, mouseY, 0);

        // Z field
        int zFieldX = yFieldX + coordFieldWidth + coordGap + labelWidth;
        UIScaleManager.drawScaledString(graphics, font, "Z:", zFieldX - labelWidth, posFieldY + 5, DesignTokens.Text.MUTED());
        EditBox zBox = zField;
        if (zBox == null) {
            zBox = new EditBox(font, zFieldX, posFieldY, coordFieldWidth, fieldHeight,
                Objects.requireNonNull(Component.literal("Z")));
            zBox.setValue(Objects.requireNonNull(zValue));
            zBox.setBordered(false);
            zBox.setResponder(val -> zValue = val != null ? val : "0");
            zField = zBox;
        }
        zBox.setX(zFieldX);
        zBox.setY(posFieldY);
        AxiomRenderer.drawInputBackground(graphics, zBox.getX(), zBox.getY(),
            zBox.getWidth(), zBox.getHeight(), zBox.isFocused());
        zBox.render(graphics, mouseX, mouseY, 0);

        // Keep zone checkbox
        int checkboxY = posFieldY + fieldHeight + ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        Checkbox checkbox = keepZoneCheckbox;
        if (checkbox == null) {
            checkbox = Checkbox.builder(
                Objects.requireNonNull(Component.translatable("area.clone.keep_zone")), font)
                .pos(x + padding, checkboxY)
                .build();
            keepZoneCheckbox = checkbox;
        }
        checkbox.setX(x + padding);
        checkbox.setY(checkboxY);
        checkbox.render(graphics, mouseX, mouseY, 0);
        keepLinkedZone = checkbox.selected();

        // Buttons
        btnWidth = ScaledCoord.scaleDim(100);
        btnHeight = ScaledCoord.scaleDim(28);
        int gap = ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        btnY = y + height - btnHeight - padding;
        cloneX = x + width / 2 - btnWidth - gap;
        cancelX = x + width / 2 + gap;

        // Render error message if present
        String error = errorMessage;
        if (error != null) {
            long elapsed = System.currentTimeMillis() - errorDisplayTime;
            if (elapsed < ERROR_DISPLAY_DURATION) {
                int errorY = btnY - UIScaleManager.getScaledLineHeight() - 4;
                UIScaleManager.drawScaledCenteredString(graphics, font, error,
                    x + width / 2, errorY, DesignTokens.AreaBuilder.ERROR_TEXT);
            } else {
                errorMessage = null;
            }
        }

        cloneButton.render(graphics, cloneX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        cancelButton.render(graphics, cancelX, btnY, btnWidth, btnHeight, mouseX, mouseY);
    }

    private void doClone() {
        // UX-07 fix: Clear previous error when user retries
        errorMessage = null;

        String name = newName.trim();
        if (name.isEmpty()) {
            showError(Component.translatable("area.clone.error.empty_name").getString());
            return;
        }

        try {
            int posX = Integer.parseInt(xValue.trim());
            int posY = Integer.parseInt(yValue.trim());
            int posZ = Integer.parseInt(zValue.trim());

            // Validate Y bounds (Minecraft world limits)
            if (posY < -64 || posY > 320) {
                showError(Component.translatable("area.clone.error.y_bounds").getString());
                return;
            }

            // Validate X/Z bounds (world border default: ±29,999,984)
            if (Math.abs(posX) > 29_999_984 || Math.abs(posZ) > 29_999_984) {
                showError(Component.translatable("area.clone.error.xz_bounds").getString());
                return;
            }

            BlockPos newCenter = new BlockPos(posX, posY, posZ);

            PacketDistributor.sendToServer(new CloneAreaPayload(
                sourceAreaId, newCenter, name, keepLinkedZone));

            hide();
            onComplete.run();
        } catch (NumberFormatException e) {
            showError(Component.translatable("area.clone.error.invalid_coords").getString());
        }
    }

    private void showError(String message) {
        this.errorMessage = message;
        this.errorDisplayTime = System.currentTimeMillis();
    }

    @Override
    protected void onEscapePressed() {
        hide();
    }

    @Override
    protected boolean shouldCloseOnClickOutside() {
        return false;
    }

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
                                         int panelX, int panelY, int panelW, int panelH) {
        // Unfocus all first
        unfocusAllFields();

        // Check name field
        if (nameField != null && nameField.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }
        // Check coordinate fields
        if (xField != null && xField.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }
        if (yField != null && yField.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }
        if (zField != null && zField.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }
        // Check checkbox
        if (keepZoneCheckbox != null && keepZoneCheckbox.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }
        // Check buttons
        if (cloneButton.mouseClicked(mouseX, mouseY, 0)) {
            cloneButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        if (cancelButton.mouseClicked(mouseX, mouseY, 0)) {
            cancelButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        return true;
    }

    private void unfocusAllFields() {
        EditBox name = nameField;
        if (name != null) name.setFocused(false);
        EditBox x = xField;
        if (x != null) x.setFocused(false);
        EditBox y = yField;
        if (y != null) y.setFocused(false);
        EditBox z = zField;
        if (z != null) z.setFocused(false);
    }

    @Override
    protected boolean handleCharTyped(char chr, int modifiers) {
        EditBox name = nameField;
        if (name != null && name.isFocused()) {
            return name.charTyped(chr, modifiers);
        }
        EditBox x = xField;
        if (x != null && x.isFocused()) {
            // Only allow digits and minus
            if (Character.isDigit(chr) || chr == '-') {
                return x.charTyped(chr, modifiers);
            }
            return true;
        }
        EditBox y = yField;
        if (y != null && y.isFocused()) {
            if (Character.isDigit(chr) || chr == '-') {
                return y.charTyped(chr, modifiers);
            }
            return true;
        }
        EditBox z = zField;
        if (z != null && z.isFocused()) {
            if (Character.isDigit(chr) || chr == '-') {
                return z.charTyped(chr, modifiers);
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        EditBox name = nameField;
        if (name != null && name.isFocused()) {
            return name.keyPressed(keyCode, 0, 0);
        }
        EditBox x = xField;
        if (x != null && x.isFocused()) {
            return x.keyPressed(keyCode, 0, 0);
        }
        EditBox y = yField;
        if (y != null && y.isFocused()) {
            return y.keyPressed(keyCode, 0, 0);
        }
        EditBox z = zField;
        if (z != null && z.isFocused()) {
            return z.keyPressed(keyCode, 0, 0);
        }
        return true;
    }
}
