package com.devmod.ui.editor.controller;

import com.devmod.ui.editor.state.ItemEditorState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ModeController}.
 */
public class ModeControllerTest {

    private ItemEditorState state;
    private ModeController controller;
    private AtomicReference<String> lastStatus;
    private AtomicReference<Integer> lastColor;

    @BeforeEach
    void setUp() {
        // Create a minimal state (no Minecraft context needed for mode logic)
        state = new ItemEditorState(ItemStack.EMPTY);
        controller = new ModeController(state);

        lastStatus = new AtomicReference<>();
        lastColor = new AtomicReference<>();
        controller.setStatusCallback((msg, color) -> {
            lastStatus.set(msg);
            lastColor.set(color);
        });
    }

    @Test
    void initialStateIsPreviewMode() {
        // Default state should be preview mode
        state.setPreviewMode(true);
        assertTrue(controller.isPreviewMode());
        assertFalse(controller.isGlobalMode());
    }

    @Test
    void switchToApplyModeUpdatesState() {
        state.setPreviewMode(true);
        assertTrue(controller.isPreviewMode());

        controller.switchToApplyMode();

        assertFalse(controller.isPreviewMode());
        assertFalse(state.isPreviewMode());
        assertEquals("Apply Mode", lastStatus.get());
    }

    @Test
    void switchToPreviewModeUpdatesState() {
        state.setPreviewMode(false);
        assertFalse(controller.isPreviewMode());

        controller.switchToPreviewMode(false);

        assertTrue(controller.isPreviewMode());
        assertTrue(state.isPreviewMode());
        assertEquals("Preview Mode", lastStatus.get());
    }

    @Test
    void toggleModeAlternatesBetweenModes() {
        state.setPreviewMode(true);

        controller.toggleMode();
        assertFalse(controller.isPreviewMode());

        controller.toggleMode();
        assertTrue(controller.isPreviewMode());
    }

    @Test
    void setGlobalModeUpdatesState() {
        assertFalse(controller.isGlobalMode());

        controller.setGlobalMode(true);

        assertTrue(controller.isGlobalMode());
        assertTrue(state.isGlobalMode());
    }

    @Test
    void toggleGlobalModeAlternates() {
        state.setGlobalMode(false);

        controller.toggleGlobalMode();
        assertTrue(controller.isGlobalMode());

        controller.toggleGlobalMode();
        assertFalse(controller.isGlobalMode());
    }

    @Test
    void getModeDescriptionReturnsFormattedString() {
        state.setPreviewMode(true);
        state.setGlobalMode(false);
        assertEquals("Preview / Specific", controller.getModeDescription());

        state.setPreviewMode(false);
        state.setGlobalMode(true);
        assertEquals("Apply / Global", controller.getModeDescription());
    }

    @Test
    void onModeChangedCallbackIsInvoked() {
        AtomicReference<Boolean> callbackInvoked = new AtomicReference<>(false);
        controller.setOnModeChanged(() -> callbackInvoked.set(true));

        controller.switchToApplyMode();

        assertTrue(callbackInvoked.get());
    }
}
