package com.devmod.client.ui;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.rendering.RoomBoundsVisualizer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.telemetry.RoomDefinition;
import com.devmod.telemetry.TelemetryConfig;
import com.devmod.util.ConfigPaths;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class RoomBoundsEditorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomBoundsEditorScreen.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // === UI Colors - Standardized to DesignTokens ===
    private static final int PANEL_BG = DesignTokens.Background.PANEL_SOLID();
    private static final int PANEL_BORDER = DesignTokens.Border.DEFAULT();  // Blue instead of green
    private static final int TEXT_TITLE = DesignTokens.Text.TITLE();  // Cyan
    private static final int TEXT_NORMAL = DesignTokens.Text.PRIMARY();
    private static final int TEXT_DIM = DesignTokens.Text.SECONDARY();
    private static final int TEXT_ACCENT = DesignTokens.Accent.BLUE();  // Blue instead of green
    private static final int TEXT_WARNING = DesignTokens.Accent.ORANGE();
    private static final int TEXT_ERROR = DesignTokens.Accent.RED();

    // === Dimensions ===
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 280;

    // === State ===
    // STATIC: Persists between screen openings so the user can:
    // 1. Aprire schermata → Set Point A → Chiudere
    // 2. Camminare all'altro angolo
    // 3. Riaprire schermata → Set Point B → Salvare
    @Nullable
    private static BlockPos pointA = null;
    @Nullable
    private static BlockPos pointB = null;
    private static String pendingRoomName = "new_room";
    private String currentDimension = "minecraft:overworld";
    private List<RoomDefinition> existingRooms = new ArrayList<>();

    // === Widgets ===
    @Nullable
    private EditBox roomNameBox;
    // Note: Button fields removed - not needed as buttons are managed by addRenderableWidget()

    // Modal overlays
    @Nullable
    private ConfirmDialog deleteDialog;
    @Nullable
    private ConfirmDialog overwriteDialog;
    @Nullable
    private RoomDefinition pendingDeleteRoom;
    @Nullable
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

        // Sync state from visualizer if local static fields are null
        // This handles the case where pending points were set in a previous session
        // but the static fields were cleared (e.g., after game restart)
        if (pointA == null && RoomBoundsVisualizer.INSTANCE.getPendingPointA() != null) {
            pointA = RoomBoundsVisualizer.INSTANCE.getPendingPointA();
        }
        if (pointB == null && RoomBoundsVisualizer.INSTANCE.getPendingPointB() != null) {
            pointB = RoomBoundsVisualizer.INSTANCE.getPendingPointB();
        }

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
        // Local capture for null safety - field is @Nullable but we just assigned it
        EditBox nameBox = new EditBox(safeFont, panelX + 90, panelY + 50, 200, 18, I18n.ui("room_name"));
        roomNameBox = nameBox;
        nameBox.setMaxLength(64);
        nameBox.setValue(roomName);
        nameBox.setHint(I18n.ui("enter_room_name_hint"));
        nameBox.setResponder(s -> pendingRoomName = s); // Salva mentre digita
        nameBox.setBordered(false);
        nameBox.setTextColor(DesignTokens.Text.PRIMARY());
        nameBox.setTextColorUneditable(DesignTokens.Text.MUTED());
        addRenderableWidget(nameBox);

        // Set Point A Button
        String setPointALabel = Objects.requireNonNull(I18n.ui("set_point_a").getString(), "setPointALabel");
        EditorButton setPointAButton = EditorButton.builder("set-point-a", setPointALabel)
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> invokeAction(ActionIds.UI_ROOM_BOUNDS_POINT_A))
            .build();
        addRenderableWidget(new EditorButtonWidget(setPointAButton, panelX + 20, panelY + 80, 130, 20));

        // Set Point B Button
        String setPointBLabel = Objects.requireNonNull(I18n.ui("set_point_b").getString(), "setPointBLabel");
        EditorButton setPointBButton = EditorButton.builder("set-point-b", setPointBLabel)
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> invokeAction(ActionIds.UI_ROOM_BOUNDS_POINT_B))
            .build();
        addRenderableWidget(new EditorButtonWidget(setPointBButton, panelX + 170, panelY + 80, 130, 20));

        // Save Button
        String saveLabel = Objects.requireNonNull(I18n.ui("save_room").getString(), "saveLabel");
        EditorButton saveButton = EditorButton.builder("save-room", saveLabel)
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> invokeAction(ActionIds.UI_ROOM_BOUNDS_SAVE))
            .build();
        addRenderableWidget(new EditorButtonWidget(saveButton, panelX + 20, panelY + 200, 130, 20));

        // Cancel Button
        String cancelLabel = Objects.requireNonNull(I18n.ui("cancel").getString(), "cancelLabel");
        EditorButton cancelButton = EditorButton.builder("cancel", cancelLabel)
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(cancelButton, panelX + 170, panelY + 200, 130, 20));

        // Delete Last Room Button
        String deleteLabel = Objects.requireNonNull(I18n.ui("delete_last").getString(), "deleteLabel");
        EditorButton deleteButton = EditorButton.builder("delete-last-room", deleteLabel)
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::deleteLastRoom)
            .build();
        addRenderableWidget(new EditorButtonWidget(deleteButton, panelX + 20, panelY + 230, 130, 20));

        initDialogs();
    }

    private void initDialogs() {
        deleteDialog = ConfirmDialog.create(
            "Delete Room",
            "Delete",
            "Cancel",
            ConfirmDialog.Style.DANGER,
            this::confirmDeleteLast,
            this::clearPendingDelete,
            "This will delete the selected room."
        );

        overwriteDialog = ConfirmDialog.create(
            "Overwrite Room",
            "Overwrite",
            "Cancel",
            ConfirmDialog.Style.WARNING,
            this::confirmOverwrite,
            this::clearPendingSave,
            "A room with this name already exists."
        );
    }

    private void confirmDeleteLast() {
        invokeConfirmedAction(ActionIds.UI_ROOM_BOUNDS_DELETE_LAST);
    }

    private void confirmOverwrite() {
        invokeConfirmedAction(ActionIds.UI_ROOM_BOUNDS_SAVE);
    }

    private void invokeAction(String actionId) {
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void invokeConfirmedAction(String actionId) {
        ActionRegistry.invoke(actionId,
            ClientActionContexts.forClient(ActionOrigin.UI).withConfirmed(true));
    }

    private void loadExistingRooms() {
        TelemetryConfig config = new TelemetryConfig();
        existingRooms = new ArrayList<>(config.loadRooms());
    }

    private EditBox requireRoomNameBox() {
        return Objects.requireNonNull(roomNameBox, "roomNameBox");
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
            EditBox roomNameBox = requireRoomNameBox();
            String previewId = "_preview_" + roomNameBox.getValue();
            RoomBoundsVisualizer.INSTANCE.addRoom(previewId, pointA, pointB);

            // Enable visualizer if not already
            if (!RoomBoundsVisualizer.INSTANCE.isEnabled()) {
                RoomBoundsVisualizer.INSTANCE.setEnabled(true);
            }
        }
    }

    private void saveRoom() {
        EditBox roomNameBox = requireRoomNameBox();
        String roomName = roomNameBox.getValue().trim();

        // Validation
        if (roomName.isEmpty()) {
            setStatus("Error: Room name cannot be empty!", TEXT_ERROR);
            return;
        }

        // Local capture for null safety - static fields are @Nullable
        BlockPos ptA = pointA;
        BlockPos ptB = pointB;
        if (ptA == null || ptB == null) {
            setStatus("Error: Set both Point A and Point B!", TEXT_ERROR);
            return;
        }

        // Calculate min/max
        BlockPos min = new BlockPos(
                Math.min(ptA.getX(), ptB.getX()),
                Math.min(ptA.getY(), ptB.getY()),
                Math.min(ptA.getZ(), ptB.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(ptA.getX(), ptB.getX()),
                Math.max(ptA.getY(), ptB.getY()),
                Math.max(ptA.getZ(), ptB.getZ())
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
            ConfirmDialog overwriteDialog = Objects.requireNonNull(this.overwriteDialog, "overwriteDialog");
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

        // Local capture for null safety - field is @Nullable
        RoomDefinition roomToDelete = existingRooms.get(existingRooms.size() - 1);
        pendingDeleteRoom = roomToDelete;
        ConfirmDialog deleteDialog = Objects.requireNonNull(this.deleteDialog, "deleteDialog");
        deleteDialog.configure(
            "Delete room '" + roomToDelete.id() + "'?",
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

        // Room size preview - local capture for null safety on static @Nullable fields
        BlockPos ptA = pointA;
        BlockPos ptB = pointB;
        if (ptA != null && ptB != null) {
            int sizeX = Math.abs(ptB.getX() - ptA.getX()) + 1;
            int sizeY = Math.abs(ptB.getY() - ptA.getY()) + 1;
            int sizeZ = Math.abs(ptB.getZ() - ptA.getZ()) + 1;
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

        renderInputBackgrounds(safeGraphics);

        super.render(safeGraphics, mouseX, mouseY, partialTick);

        // Modal overlays on top
        renderDialogs(safeGraphics, mouseX, mouseY, safeFont);
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        if (roomNameBox != null) {
            AxiomRenderer.drawInputBackground(graphics, roomNameBox.getX(), roomNameBox.getY(),
                roomNameBox.getWidth(), roomNameBox.getHeight(), roomNameBox.isFocused());
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (color & DesignTokens.Mask.RGB);
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
    // ACTION BRIDGE (Radial / UI)
    // ═══════════════════════════════════════════════════════════════

    public static final class Actions {
        private Actions() {}

        public static boolean setPointA(ActionContext context) {
            RoomBoundsEditorScreen screen = getOpenScreen();
            if (screen != null) {
                screen.setPointA();
                return true;
            }
            return setPointFromContext(context, true);
        }

        public static boolean setPointB(ActionContext context) {
            RoomBoundsEditorScreen screen = getOpenScreen();
            if (screen != null) {
                screen.setPointB();
                return true;
            }
            return setPointFromContext(context, false);
        }

        public static boolean saveRoom(ActionContext context) {
            RoomBoundsEditorScreen screen = getOpenScreen();
            if (screen != null) {
                if (context.isConfirmed() && screen.pendingSaveRoom != null) {
                    screen.applyPendingSave();
                    return true;
                }
                screen.saveRoom();
                return true;
            }
            return saveRoomWithoutScreen(context);
        }

        public static boolean deleteLastRoom(ActionContext context) {
            RoomBoundsEditorScreen screen = getOpenScreen();
            if (screen != null && screen.pendingDeleteRoom != null) {
                screen.deletePendingRoom();
                return true;
            }
            return deleteLastRoomWithoutScreen(context);
        }

        @Nullable
        private static RoomBoundsEditorScreen getOpenScreen() {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof RoomBoundsEditorScreen editor) {
                return editor;
            }
            return null;
        }

        private static boolean setPointFromContext(ActionContext context, boolean isPointA) {
            var player = context.getPlayer();
            if (player == null) {
                context.sendFailure(Component.translatable("devmod.action.requires_player"));
                return false;
            }

            BlockPos pos = player.blockPosition();
            if (isPointA) {
                pointA = pos;
                RoomBoundsVisualizer.INSTANCE.setPendingPointA(pos);
            } else {
                pointB = pos;
                RoomBoundsVisualizer.INSTANCE.setPendingPointB(pos);
            }

            updatePreviewFallback();
            String label = isPointA ? "Point A set: " : "Point B set: ";
            context.sendSuccess(Component.literal(label + formatBlockPosStatic(pos)), true);
            return true;
        }

        private static boolean saveRoomWithoutScreen(ActionContext context) {
            String roomName = safePendingRoomName();
            if (roomName.isBlank()) {
                context.sendFailure(Component.literal("Error: Room name cannot be empty!"));
                return false;
            }
            if (pointA == null || pointB == null) {
                context.sendFailure(Component.literal("Error: Set both Point A and Point B!"));
                return false;
            }

            String dimension = resolveDimension();
            RoomDefinition room = buildRoomDefinition(roomName, dimension);

            List<RoomDefinition> rooms = new ArrayList<>(new TelemetryConfig().loadRooms());
            boolean replacing = rooms.removeIf(existing -> existing.id().equals(roomName));
            if (replacing && !context.isConfirmed()) {
                context.sendFailure(Component.translatable("devmod.action.requires_confirm"));
                return false;
            }

            rooms.add(room);
            if (!writeRoomsToFile(rooms)) {
                context.sendFailure(Component.literal("Error: Failed to save file!"));
                return false;
            }

            String action = replacing ? "overwritten" : "saved";
            RoomBoundsVisualizer.INSTANCE.removeRoom("_preview_" + room.id());
            RoomBoundsVisualizer.INSTANCE.reload();
            RoomBoundsVisualizer.INSTANCE.clearPendingPoints();

            pointA = null;
            pointB = null;
            pendingRoomName = "new_room_" + (rooms.size() + 1);

            context.sendSuccess(Component.literal("Room '" + room.id() + "' " + action + "!"), true);
            return true;
        }

        private static boolean deleteLastRoomWithoutScreen(ActionContext context) {
            List<RoomDefinition> rooms = new ArrayList<>(new TelemetryConfig().loadRooms());
            if (rooms.isEmpty()) {
                context.sendFailure(Component.literal("No rooms to delete!"));
                return false;
            }

            RoomDefinition removed = rooms.remove(rooms.size() - 1);
            if (!writeRoomsToFile(rooms)) {
                context.sendFailure(Component.literal("Error: Failed to save file!"));
                return false;
            }

            RoomBoundsVisualizer.INSTANCE.reload();
            RoomBoundsVisualizer.INSTANCE.clearPendingPoints();

            pointA = null;
            pointB = null;
            pendingRoomName = "new_room_" + (rooms.size() + 1);

            context.sendSuccess(Component.literal("Deleted room: " + removed.id()), true);
            return true;
        }

        private static void updatePreviewFallback() {
            if (pointA == null || pointB == null) {
                return;
            }
            String previewId = "_preview_" + safePendingRoomName();
            RoomBoundsVisualizer.INSTANCE.addRoom(previewId, pointA, pointB);
            if (!RoomBoundsVisualizer.INSTANCE.isEnabled()) {
                RoomBoundsVisualizer.INSTANCE.setEnabled(true);
            }
        }

        private static String safePendingRoomName() {
            if (pendingRoomName == null || pendingRoomName.isBlank()) {
                pendingRoomName = "new_room";
            }
            return pendingRoomName;
        }

        private static String resolveDimension() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                return mc.level.dimension().location().toString();
            }
            return "minecraft:overworld";
        }

        private static RoomDefinition buildRoomDefinition(String roomName, String dimension) {
            BlockPos safePointA = Objects.requireNonNull(pointA, "pointA");
            BlockPos safePointB = Objects.requireNonNull(pointB, "pointB");
            BlockPos min = new BlockPos(
                Math.min(safePointA.getX(), safePointB.getX()),
                Math.min(safePointA.getY(), safePointB.getY()),
                Math.min(safePointA.getZ(), safePointB.getZ())
            );
            BlockPos max = new BlockPos(
                Math.max(safePointA.getX(), safePointB.getX()),
                Math.max(safePointA.getY(), safePointB.getY()),
                Math.max(safePointA.getZ(), safePointB.getZ())
            );
            return new RoomDefinition(roomName, dimension, min, max);
        }

        private static boolean writeRoomsToFile(List<RoomDefinition> rooms) {
            Path file = ConfigPaths.getTelemetryRoomsFile();
            try {
                Files.createDirectories(file.getParent());
                try (Writer writer = Files.newBufferedWriter(file)) {
                    GSON.toJson(rooms, writer);
                }
                LOGGER.info("[RoomBoundsEditor] Saved {} rooms to {}", rooms.size(), file);
                return true;
            } catch (IOException e) {
                LOGGER.error("[RoomBoundsEditor] Failed to save rooms", e);
                return false;
            }
        }

        private static String formatBlockPosStatic(BlockPos pos) {
            return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
        }
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
        // Local capture for null safety - field is @Nullable
        RoomDefinition roomToDelete = pendingDeleteRoom;
        if (roomToDelete == null) {
            return;
        }

        List<RoomDefinition> backup = new ArrayList<>(existingRooms);
        existingRooms.removeIf(room -> room.id().equals(roomToDelete.id()));

        if (saveRoomsToFile()) {
            setStatus("Deleted room: " + roomToDelete.id(), TEXT_WARNING);
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
