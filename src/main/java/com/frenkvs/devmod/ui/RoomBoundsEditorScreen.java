package com.frenkvs.devmod.ui;

import com.frenkvs.devmod.rendering.RoomBoundsVisualizer;
import com.frenkvs.devmod.telemetry.RoomDefinition;
import com.frenkvs.devmod.telemetry.TelemetryConfig;
import com.frenkvs.devmod.util.ConfigPaths;
import com.frenkvs.devmod.util.I18n;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game UI per definire Room Bounds.
 *
 * Permette di:
 * - Impostare Punto A (angolo min) dalla posizione del player
 * - Impostare Punto B (angolo max) dalla posizione del player
 * - Dare un nome alla room
 * - Salvare direttamente nel file telemetry_rooms.json
 * - Visualizzare la lista delle room esistenti
 *
 * Keybind: Shift+R (quando Room Bounds visualizer è attivo)
 */
@SuppressWarnings("null")
public class RoomBoundsEditorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomBoundsEditorScreen.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // === Colori UI ===
    private static final int PANEL_BG = 0xDD1A1A2E;
    private static final int PANEL_BORDER = 0xFF4CAF50;
    private static final int TEXT_TITLE = 0xFF81C784;
    private static final int TEXT_NORMAL = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_ACCENT = 0xFF4CAF50;
    private static final int TEXT_WARNING = 0xFFFF9800;
    private static final int TEXT_ERROR = 0xFFFF5252;

    // === Dimensioni ===
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 280;

    // === State ===
    // STATIC: Persiste tra aperture della schermata così l'utente può:
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
    private Button setPointAButton;
    private Button setPointBButton;
    private Button saveButton;
    private Button cancelButton;
    private Button deleteLastButton;

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
        if (minecraft != null && minecraft.level != null) {
            currentDimension = minecraft.level.dimension().location().toString();
        }

        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Room Name Input - usa il nome salvato dalla sessione precedente
        roomNameBox = new EditBox(font, panelX + 90, panelY + 50, 200, 18, I18n.ui("room_name"));
        roomNameBox.setMaxLength(64);
        roomNameBox.setValue(pendingRoomName);
        roomNameBox.setHint(I18n.ui("enter_room_name_hint"));
        roomNameBox.setResponder(s -> pendingRoomName = s); // Salva mentre digita
        addRenderableWidget(roomNameBox);

        // Set Point A Button
        setPointAButton = Button.builder(I18n.ui("set_point_a"), btn -> setPointA())
                .pos(panelX + 20, panelY + 80)
                .size(130, 20)
                .build();
        addRenderableWidget(setPointAButton);

        // Set Point B Button
        setPointBButton = Button.builder(I18n.ui("set_point_b"), btn -> setPointB())
                .pos(panelX + 170, panelY + 80)
                .size(130, 20)
                .build();
        addRenderableWidget(setPointBButton);

        // Save Button
        saveButton = Button.builder(I18n.ui("save_room"), btn -> saveRoom())
                .pos(panelX + 20, panelY + 200)
                .size(130, 20)
                .build();
        addRenderableWidget(saveButton);

        // Cancel Button
        cancelButton = Button.builder(I18n.ui("cancel"), btn -> onClose())
                .pos(panelX + 170, panelY + 200)
                .size(130, 20)
                .build();
        addRenderableWidget(cancelButton);

        // Delete Last Room Button
        deleteLastButton = Button.builder(I18n.ui("delete_last"), btn -> deleteLastRoom())
                .pos(panelX + 20, panelY + 230)
                .size(130, 20)
                .build();
        addRenderableWidget(deleteLastButton);
    }

    private void loadExistingRooms() {
        TelemetryConfig config = new TelemetryConfig();
        existingRooms = new ArrayList<>(config.loadRooms());
    }

    private void setPointA() {
        if (minecraft != null && minecraft.player != null) {
            pointA = minecraft.player.blockPosition();
            setStatus("Point A set: " + formatBlockPos(pointA), TEXT_ACCENT);

            // Update visualizer pending marker (visible even when screen is closed)
            RoomBoundsVisualizer.INSTANCE.setPendingPointA(pointA);

            // Preview in visualizer
            updateVisualizerPreview();
        }
    }

    private void setPointB() {
        if (minecraft != null && minecraft.player != null) {
            pointB = minecraft.player.blockPosition();
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

        // Check for duplicate name
        for (RoomDefinition room : existingRooms) {
            if (room.id().equals(roomName)) {
                setStatus("Error: Room '" + roomName + "' already exists!", TEXT_ERROR);
                return;
            }
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
        existingRooms.add(newRoom);

        // Save to file
        if (saveRoomsToFile()) {
            setStatus("Room '" + roomName + "' saved!", TEXT_ACCENT);

            // Remove preview and add actual room
            RoomBoundsVisualizer.INSTANCE.removeRoom("_preview_" + roomName);
            RoomBoundsVisualizer.INSTANCE.reload();

            // Clear pending markers from visualizer
            RoomBoundsVisualizer.INSTANCE.clearPendingPoints();

            // Reset static state for next room
            pointA = null;
            pointB = null;
            pendingRoomName = "new_room_" + (existingRooms.size() + 1);
            roomNameBox.setValue(pendingRoomName);
        } else {
            setStatus("Error: Failed to save file!", TEXT_ERROR);
        }
    }

    private void deleteLastRoom() {
        if (existingRooms.isEmpty()) {
            setStatus("No rooms to delete!", TEXT_WARNING);
            return;
        }

        RoomDefinition removed = existingRooms.remove(existingRooms.size() - 1);

        if (saveRoomsToFile()) {
            setStatus("Deleted room: " + removed.id(), TEXT_WARNING);
            RoomBoundsVisualizer.INSTANCE.reload();
        } else {
            // Restore if save failed
            existingRooms.add(removed);
            setStatus("Error: Failed to save file!", TEXT_ERROR);
        }
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Panel background
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_BG);

        // Title
        graphics.drawCenteredString(font, "Room Bounds Editor", centerX, panelY + 10, TEXT_TITLE);

        // Dimension info
        graphics.drawString(font, "Dimension: " + currentDimension, panelX + 20, panelY + 30, TEXT_DIM);

        // Room Name Label
        graphics.drawString(font, "Room Name:", panelX + 20, panelY + 54, TEXT_NORMAL);

        // Point A info
        String pointAText = pointA != null ? formatBlockPos(pointA) : "Not set";
        int pointAColor = pointA != null ? TEXT_ACCENT : TEXT_DIM;
        graphics.drawString(font, "Point A: " + pointAText, panelX + 20, panelY + 110, pointAColor);

        // Point B info
        String pointBText = pointB != null ? formatBlockPos(pointB) : "Not set";
        int pointBColor = pointB != null ? TEXT_ACCENT : TEXT_DIM;
        graphics.drawString(font, "Point B: " + pointBText, panelX + 20, panelY + 125, pointBColor);

        // Room size preview
        if (pointA != null && pointB != null) {
            int sizeX = Math.abs(pointB.getX() - pointA.getX()) + 1;
            int sizeY = Math.abs(pointB.getY() - pointA.getY()) + 1;
            int sizeZ = Math.abs(pointB.getZ() - pointA.getZ()) + 1;
            String sizeText = String.format("Size: %dx%dx%d (%d blocks)", sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
            graphics.drawString(font, sizeText, panelX + 20, panelY + 145, TEXT_ACCENT);
        }

        // Existing rooms list
        graphics.drawString(font, "Existing Rooms: " + existingRooms.size(), panelX + 20, panelY + 165, TEXT_NORMAL);

        int listY = panelY + 178;
        int maxShow = 2;
        int startIdx = Math.max(0, existingRooms.size() - maxShow);
        for (int i = startIdx; i < existingRooms.size() && i < startIdx + maxShow; i++) {
            RoomDefinition room = existingRooms.get(i);
            String roomInfo = "- " + room.id() + " " + formatBlockPos(room.min()) + " to " + formatBlockPos(room.max());
            if (font.width(roomInfo) > PANEL_WIDTH - 40) {
                roomInfo = "- " + room.id() + " (...)";
            }
            graphics.drawString(font, roomInfo, panelX + 25, listY, TEXT_DIM);
            listY += 10;
        }

        // Status message (fades after 3 seconds)
        if (!statusMessage.isEmpty()) {
            long elapsed = System.currentTimeMillis() - statusTime;
            if (elapsed < 3000) {
                float alpha = elapsed < 2500 ? 1.0f : 1.0f - (elapsed - 2500) / 500.0f;
                int color = applyAlpha(statusColor, alpha);
                graphics.drawCenteredString(font, statusMessage, centerX, panelY + PANEL_HEIGHT - 15, color);
            } else {
                statusMessage = "";
            }
        }

        // Hint
        graphics.drawCenteredString(font, "Stand at corner, click 'Set Point'. Repeat for opposite corner.",
                centerX, panelY + PANEL_HEIGHT + 5, TEXT_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);
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
}
