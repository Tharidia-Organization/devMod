package com.frenkvs.devmod.ui;

import com.frenkvs.devmod.rendering.RoomBoundsVisualizer;
import com.frenkvs.devmod.telemetry.RoomDefinition;
import com.frenkvs.devmod.telemetry.TelemetryConfig;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.util.ConfigPaths;
import com.frenkvs.devmod.util.I18n;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * In-game UI for defining Room Bounds.
 * Uses standard UIConstants for consistent theming.
 *
 * Allows:
 * - Set Point A (min corner) from player position
 * - Set Point B (max corner) from player position
 * - Give a name to the room
 * - Save directly to telemetry_rooms.json file
 * - View list of existing rooms
 *
 * Keybind: Shift+R (when Room Bounds visualizer is active)
 */

public class RoomBoundsEditorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomBoundsEditorScreen.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // === UI Colors - Standardized to UIConstants ===
    private static final int PANEL_BG = UIConstants.Background.PANEL_SOLID();
    private static final int PANEL_BORDER = UIConstants.Border.DEFAULT();  // Blue instead of green
    private static final int TEXT_TITLE = UIConstants.Text.TITLE();  // Cyan
    private static final int TEXT_NORMAL = UIConstants.Text.PRIMARY();
    private static final int TEXT_DIM = UIConstants.Text.SECONDARY();
    private static final int TEXT_ACCENT = UIConstants.Accent.BLUE();  // Blue instead of green
    private static final int TEXT_WARNING = UIConstants.Accent.ORANGE();
    private static final int TEXT_ERROR = UIConstants.Accent.RED();

    // === Dimensions ===
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 280;

    // === State ===
    // STATIC: Persists between screen openings so the user can:
    // 1. Aprire schermata → Set Point A → Chiudere
    // 2. Camminare all'altro angolo
    // 3. Riaprire schermata → Set Point B → Salvare
    private static BlockPos pointA = null;
    private static BlockPos pointB = null;
    private static String pendingRoomName = "new_room";
    private String currentDimension = "minecraft:overworld";
    private List<RoomDefinition> existingRooms = new ArrayList<>();

    // === Widgets ===
    private EditBox roomNameBox;
    private EditorButton setPointAButton;
    private EditorButton setPointBButton;
    private EditorButton saveButton;
    private EditorButton cancelButton;
    private EditorButton deleteLastButton;

    // Modal overlays
    private ConfirmDialog deleteDialog;
    private ConfirmDialog overwriteDialog;
    private RoomDefinition pendingDeleteRoom;
    private RoomDefinition pendingSaveRoom;

    // === Messages ===
    private String statusMessage = "";
    private int statusColor = TEXT_NORMAL;
    private long statusTime = 0;

    public RoomBoundsEditorScreen() {
        super(I18n.screenTitle("room_bounds_editor"));
    }

    @Override
    protected void init() {
        super.init();

        // Load existing rooms
        loadExistingRooms();

        // Get current dimension
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            currentDimension = mc.level.dimension().location().toString();
        }

        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Room Name Input - usa il nome salvato dalla sessione precedente
        String roomName = Objects.requireNonNull(
            Objects.requireNonNullElse(pendingRoomName, "new_room"),
            "roomName"
        );
        pendingRoomName = roomName;
        roomNameBox = new EditBox(safeFont, panelX + 90, panelY + 50, 200, 18, I18n.ui("room_name"));
        roomNameBox.setMaxLength(64);
        roomNameBox.setValue(roomName);
        roomNameBox.setHint(I18n.ui("enter_room_name_hint"));
        roomNameBox.setResponder(s -> pendingRoomName = s); // Salva mentre digita
        addRenderableWidget(roomNameBox);

        // Set Point A Button
        String setPointALabel = Objects.requireNonNull(I18n.ui("set_point_a").getString(), "setPointALabel");
        setPointAButton = EditorButton.builder("set-point-a", setPointALabel)
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::setPointA)
            .build();
        addRenderableWidget(Objects.requireNonNull(
            setPointAButton.asVanilla(panelX + 20, panelY + 80, 130, 20),
            "setPointA button"));

        // Set Point B Button
        String setPointBLabel = Objects.requireNonNull(I18n.ui("set_point_b").getString(), "setPointBLabel");
        setPointBButton = EditorButton.builder("set-point-b", setPointBLabel)
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::setPointB)
            .build();
        addRenderableWidget(Objects.requireNonNull(
            setPointBButton.asVanilla(panelX + 170, panelY + 80, 130, 20),
            "setPointB button"));

        // Save Button
        String saveLabel = Objects.requireNonNull(I18n.ui("save_room").getString(), "saveLabel");
        saveButton = EditorButton.builder("save-room", saveLabel)
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::saveRoom)
            .build();
        addRenderableWidget(Objects.requireNonNull(
            saveButton.asVanilla(panelX + 20, panelY + 200, 130, 20),
            "save button"));

        // Cancel Button
        String cancelLabel = Objects.requireNonNull(I18n.ui("cancel").getString(), "cancelLabel");
        cancelButton = EditorButton.builder("cancel", cancelLabel)
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        addRenderableWidget(Objects.requireNonNull(
            cancelButton.asVanilla(panelX + 170, panelY + 200, 130, 20),
            "cancel button"));

        // Delete Last Room Button
        String deleteLabel = Objects.requireNonNull(I18n.ui("delete_last").getString(), "deleteLabel");
        deleteLastButton = EditorButton.builder("delete-last-room", deleteLabel)
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::deleteLastRoom)
            .build();
        addRenderableWidget(Objects.requireNonNull(
            deleteLastButton.asVanilla(panelX + 20, panelY + 230, 130, 20),
            "delete button"));

        initDialogs();
    }

    private void initDialogs() {
        deleteDialog = ConfirmDialog.create(
            "Delete Room",
            "Delete",
            "Cancel",
            ConfirmDialog.Style.DANGER,
            this::deletePendingRoom,
            this::clearPendingDelete,
            "This will delete the selected room."
        );

        overwriteDialog = ConfirmDialog.create(
            "Overwrite Room",
            "Overwrite",
            "Cancel",
            ConfirmDialog.Style.WARNING,
            this::applyPendingSave,
            this::clearPendingSave,
            "A room with this name already exists."
        );
    }

    private void loadExistingRooms() {
        TelemetryConfig config = new TelemetryConfig();
        existingRooms = new ArrayList<>(config.loadRooms());
    }

    private void setPointA() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            pointA = mc.player.blockPosition();
            setStatus("Point A set: " + formatBlockPos(pointA), TEXT_ACCENT);

            // Update visualizer pending marker (visible even when screen is closed)
            RoomBoundsVisualizer.INSTANCE.setPendingPointA(pointA);

            // Preview in visualizer
            updateVisualizerPreview();
        }
    }

    private void setPointB() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            pointB = mc.player.blockPosition();
            setStatus("Point B set: " + formatBlockPos(pointB), TEXT_ACCENT);

            // Update visualizer pending marker (visible even when screen is closed)
            RoomBoundsVisualizer.INSTANCE.setPendingPointB(pointB);

            // Preview in visualizer
            updateVisualizerPreview();
        }
    }

    private void updateVisualizerPreview() {
        if (pointA != null && pointB != null) {
            // Add preview room to visualizer
            String previewId = "_preview_" + roomNameBox.getValue();
            RoomBoundsVisualizer.INSTANCE.addRoom(previewId, pointA, pointB);

            // Enable visualizer if not already
            if (!RoomBoundsVisualizer.INSTANCE.isEnabled()) {
                RoomBoundsVisualizer.INSTANCE.setEnabled(true);
            }
        }
    }

    private void saveRoom() {
        String roomName = roomNameBox.getValue().trim();

        // Validation
        if (roomName.isEmpty()) {
            setStatus("Error: Room name cannot be empty!", TEXT_ERROR);
            return;
        }

        if (pointA == null || pointB == null) {
            setStatus("Error: Set both Point A and Point B!", TEXT_ERROR);
            return;
        }

        // Calculate min/max
        BlockPos min = new BlockPos(
                Math.min(pointA.getX(), pointB.getX()),
                Math.min(pointA.getY(), pointB.getY()),
                Math.min(pointA.getZ(), pointB.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(pointA.getX(), pointB.getX()),
                Math.max(pointA.getY(), pointB.getY()),
                Math.max(pointA.getZ(), pointB.getZ())
        );

        // Create new room definition
        RoomDefinition newRoom = new RoomDefinition(roomName, currentDimension, min, max);

        // Duplicate check with overwrite confirmation
        RoomDefinition existing = null;
        for (RoomDefinition room : existingRooms) {
            if (room.id().equals(roomName)) {
                existing = room;
                break;
            }
        }

        if (existing != null) {
            pendingSaveRoom = newRoom;
            overwriteDialog.configure(
                "Overwrite room '" + roomName + "'?",
                List.of(
                    "A room with this name already exists.",
                    "Overwrite its bounds with the current selection?"
                )
            ).show();
            return;
        }

        // No duplicate, proceed
        persistRoom(newRoom, false);
    }

    private void deleteLastRoom() {
        if (existingRooms.isEmpty()) {
            setStatus("No rooms to delete!", TEXT_WARNING);
            return;
        }

        pendingDeleteRoom = existingRooms.get(existingRooms.size() - 1);
        deleteDialog.configure(
            "Delete room '" + pendingDeleteRoom.id() + "'?",
            List.of(
                "This will remove the most recently saved room.",
                "This action cannot be undone."
            )
        ).show();
    }

    private boolean saveRoomsToFile() {
        Path file = ConfigPaths.getTelemetryRoomsFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(existingRooms, writer);
            }
            LOGGER.info("[RoomBoundsEditor] Saved {} rooms to {}", existingRooms.size(), file);
            return true;
        } catch (IOException e) {
            LOGGER.error("[RoomBoundsEditor] Failed to save rooms", e);
            return false;
        }
    }

    private void setStatus(String message, int color) {
        statusMessage = message;
        statusColor = color;
        statusTime = System.currentTimeMillis();
    }

    private String formatBlockPos(BlockPos pos) {
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        @Nonnull GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        renderBackground(safeGraphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Panel background
        safeGraphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, PANEL_BORDER);
        safeGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_BG);

        // Title
        safeGraphics.drawCenteredString(safeFont, "Room Bounds Editor", centerX, panelY + 10, TEXT_TITLE);

        // Dimension info
        String dimension = Objects.requireNonNull(currentDimension, "dimension");
        safeGraphics.drawString(safeFont, "Dimension: " + dimension, panelX + 20, panelY + 30, TEXT_DIM);

        // Room Name Label
        safeGraphics.drawString(safeFont, "Room Name:", panelX + 20, panelY + 54, TEXT_NORMAL);

        // Point A info
        String pointAText = pointA != null ? formatBlockPos(pointA) : "Not set";
        int pointAColor = pointA != null ? TEXT_ACCENT : TEXT_DIM;
        safeGraphics.drawString(safeFont, "Point A: " + pointAText, panelX + 20, panelY + 110, pointAColor);

        // Point B info
        String pointBText = pointB != null ? formatBlockPos(pointB) : "Not set";
        int pointBColor = pointB != null ? TEXT_ACCENT : TEXT_DIM;
        safeGraphics.drawString(safeFont, "Point B: " + pointBText, panelX + 20, panelY + 125, pointBColor);

        // Room size preview
        if (pointA != null && pointB != null) {
            int sizeX = Math.abs(pointB.getX() - pointA.getX()) + 1;
            int sizeY = Math.abs(pointB.getY() - pointA.getY()) + 1;
            int sizeZ = Math.abs(pointB.getZ() - pointA.getZ()) + 1;
            String sizeText = String.format("Size: %dx%dx%d (%d blocks)", sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
            safeGraphics.drawString(safeFont, sizeText, panelX + 20, panelY + 145, TEXT_ACCENT);
        }

        // Existing rooms list
        safeGraphics.drawString(safeFont, "Existing Rooms: " + existingRooms.size(), panelX + 20, panelY + 165, TEXT_NORMAL);

        int listY = panelY + 178;
        int maxShow = 2;
        int startIdx = Math.max(0, existingRooms.size() - maxShow);
        for (int i = startIdx; i < existingRooms.size() && i < startIdx + maxShow; i++) {
            RoomDefinition room = existingRooms.get(i);
            String roomInfo = "- " + room.id() + " " + formatBlockPos(room.min()) + " to " + formatBlockPos(room.max());
            if (safeFont.width(roomInfo) > PANEL_WIDTH - 40) {
                roomInfo = "- " + room.id() + " (...)";
            }
            safeGraphics.drawString(safeFont, roomInfo, panelX + 25, listY, TEXT_DIM);
            listY += 10;
        }

        // Status message (fades after 3 seconds)
        if (!statusMessage.isEmpty()) {
            long elapsed = System.currentTimeMillis() - statusTime;
            if (elapsed < 3000) {
                float alpha = elapsed < 2500 ? 1.0f : 1.0f - (elapsed - 2500) / 500.0f;
                int color = applyAlpha(statusColor, alpha);
                String status = Objects.requireNonNull(statusMessage, "statusMessage");
                safeGraphics.drawCenteredString(safeFont, status, centerX, panelY + PANEL_HEIGHT - 15, color);
            } else {
                statusMessage = "";
            }
        }

        // Hint
        safeGraphics.drawCenteredString(safeFont, "Stand at corner, click 'Set Point'. Repeat for opposite corner.",
                centerX, panelY + PANEL_HEIGHT + 5, TEXT_DIM);

        super.render(safeGraphics, mouseX, mouseY, partialTick);

        // Modal overlays on top
        renderDialogs(safeGraphics, mouseX, mouseY, safeFont);
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public void onClose() {
        // Remove preview room
        if (roomNameBox != null) {
            RoomBoundsVisualizer.INSTANCE.removeRoom("_preview_" + roomNameBox.getValue());
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // MODAL HANDLERS
    // ═══════════════════════════════════════════════════════════════

    private void renderDialogs(GuiGraphics graphics, int mouseX, int mouseY, Font font) {
        if (deleteDialog != null) {
            deleteDialog.render(graphics, font, width, height, mouseX, mouseY);
        }
        if (overwriteDialog != null) {
            overwriteDialog.render(graphics, font, width, height, mouseX, mouseY);
        }
    }

    private boolean handleDialogClick(double mouseX, double mouseY) {
        boolean handled = false;
        if (deleteDialog != null) {
            handled |= deleteDialog.mouseClicked(mouseX, mouseY, width, height);
        }
        if (overwriteDialog != null) {
            handled |= overwriteDialog.mouseClicked(mouseX, mouseY, width, height);
        }
        return handled;
    }

    private boolean handleDialogScroll(double mouseX, double mouseY, double scrollDelta) {
        boolean handled = false;
        if (deleteDialog != null) {
            handled |= deleteDialog.mouseScrolled(mouseX, mouseY, scrollDelta, width, height);
        }
        if (overwriteDialog != null) {
            handled |= overwriteDialog.mouseScrolled(mouseX, mouseY, scrollDelta, width, height);
        }
        return handled;
    }

    private boolean handleDialogKey(int keyCode) {
        boolean handled = false;
        if (deleteDialog != null) {
            handled |= deleteDialog.keyPressed(keyCode);
        }
        if (overwriteDialog != null) {
            handled |= overwriteDialog.keyPressed(keyCode);
        }
        return handled;
    }

    private boolean handleDialogChar(char codePoint, int modifiers) {
        boolean handled = false;
        if (deleteDialog != null) {
            handled |= deleteDialog.charTyped(codePoint, modifiers);
        }
        if (overwriteDialog != null) {
            handled |= overwriteDialog.charTyped(codePoint, modifiers);
        }
        return handled;
    }

    private boolean isDialogOpen() {
        return (deleteDialog != null && deleteDialog.isVisible()) ||
               (overwriteDialog != null && overwriteDialog.isVisible());
    }

    private void deletePendingRoom() {
        if (pendingDeleteRoom == null) {
            return;
        }

        List<RoomDefinition> backup = new ArrayList<>(existingRooms);
        existingRooms.removeIf(room -> room.id().equals(pendingDeleteRoom.id()));

        if (saveRoomsToFile()) {
            setStatus("Deleted room: " + pendingDeleteRoom.id(), TEXT_WARNING);
            RoomBoundsVisualizer.INSTANCE.reload();
        } else {
            existingRooms = backup;
            setStatus("Error: Failed to save file!", TEXT_ERROR);
        }

        clearPendingDelete();
    }

    private void clearPendingDelete() {
        pendingDeleteRoom = null;
    }

    private void applyPendingSave() {
        if (pendingSaveRoom == null) {
            return;
        }

        persistRoom(pendingSaveRoom, true);
        clearPendingSave();
    }

    private void clearPendingSave() {
        pendingSaveRoom = null;
    }

    private void persistRoom(RoomDefinition room, boolean replacing) {
        List<RoomDefinition> backup = new ArrayList<>(existingRooms);
        existingRooms.removeIf(r -> r.id().equals(room.id()));
        existingRooms.add(room);

        if (saveRoomsToFile()) {
            String action = replacing ? "overwritten" : "saved";
            setStatus("Room '" + room.id() + "' " + action + "!", replacing ? TEXT_WARNING : TEXT_ACCENT);

            // Remove preview and add actual room
            RoomBoundsVisualizer.INSTANCE.removeRoom("_preview_" + room.id());
            RoomBoundsVisualizer.INSTANCE.reload();

            // Clear pending markers from visualizer
            RoomBoundsVisualizer.INSTANCE.clearPendingPoints();

            // Reset static state for next room
            pointA = null;
            pointB = null;
            pendingRoomName = "new_room_" + (existingRooms.size() + 1);
            if (roomNameBox != null) {
                roomNameBox.setValue(pendingRoomName);
            }
        } else {
            existingRooms = backup;
            setStatus("Error: Failed to save file!", TEXT_ERROR);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT BLOCKING FOR MODALS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleDialogClick(mouseX, mouseY)) {
            return true;
        }
        if (isDialogOpen()) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDialogOpen()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (handleDialogScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleDialogKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (handleDialogChar(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}
