package com.devmod.client.ui.editor.state;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ItemEditorState}.
 */
public class ItemEditorStateTest {

    private ItemEditorState state;

    @BeforeEach
    void setUp() {
        state = new ItemEditorState(ItemStack.EMPTY);
    }

    @Test
    void initialStateHasCorrectDefaults() {
        assertTrue(state.isPreviewMode());
        assertFalse(state.isGlobalMode());
        assertNull(state.getActiveModule());
        assertNull(state.getSelectedSlot());
    }

    @Test
    void setPreviewModeUpdatesState() {
        state.setPreviewMode(false);
        assertFalse(state.isPreviewMode());

        state.setPreviewMode(true);
        assertTrue(state.isPreviewMode());
    }

    @Test
    void setGlobalModeUpdatesState() {
        state.setGlobalMode(true);
        assertTrue(state.isGlobalMode());

        state.setGlobalMode(false);
        assertFalse(state.isGlobalMode());
    }

    @Test
    void togglePreviewModeAlternates() {
        assertTrue(state.isPreviewMode());

        state.togglePreviewMode();
        assertFalse(state.isPreviewMode());

        state.togglePreviewMode();
        assertTrue(state.isPreviewMode());
    }

    @Test
    void toggleGlobalModeAlternates() {
        assertFalse(state.isGlobalMode());

        state.toggleGlobalMode();
        assertTrue(state.isGlobalMode());

        state.toggleGlobalMode();
        assertFalse(state.isGlobalMode());
    }

    @Test
    void getModeDescriptionFormatsCorrectly() {
        state.setPreviewMode(true);
        state.setGlobalMode(false);
        assertEquals("Preview / Specific", state.getModeDescription());

        state.setPreviewMode(false);
        state.setGlobalMode(true);
        assertEquals("Apply / Global", state.getModeDescription());

        state.setPreviewMode(true);
        state.setGlobalMode(true);
        assertEquals("Preview / Global", state.getModeDescription());

        state.setPreviewMode(false);
        state.setGlobalMode(false);
        assertEquals("Apply / Specific", state.getModeDescription());
    }

    @Test
    void changeListenersAreNotified() {
        AtomicInteger callCount = new AtomicInteger(0);
        state.addChangeListener(s -> callCount.incrementAndGet());

        state.setPreviewMode(false);
        assertEquals(1, callCount.get());

        state.setGlobalMode(true);
        assertEquals(2, callCount.get());

        state.togglePreviewMode();
        assertEquals(3, callCount.get());
    }

    @Test
    void removeChangeListenerStopsNotifications() {
        AtomicInteger callCount = new AtomicInteger(0);
        var listener = (java.util.function.Consumer<ItemEditorState>) s -> callCount.incrementAndGet();

        state.addChangeListener(listener);
        state.setPreviewMode(false);
        assertEquals(1, callCount.get());

        state.removeChangeListener(listener);
        state.setPreviewMode(true);
        assertEquals(1, callCount.get()); // Not incremented after removal
    }

    @Test
    void removeAllListenersClearsAllNotifications() {
        AtomicInteger callCount1 = new AtomicInteger(0);
        AtomicInteger callCount2 = new AtomicInteger(0);

        state.addChangeListener(s -> callCount1.incrementAndGet());
        state.addChangeListener(s -> callCount2.incrementAndGet());

        state.setPreviewMode(false);
        assertEquals(1, callCount1.get());
        assertEquals(1, callCount2.get());

        state.removeAllListeners();
        state.setPreviewMode(true);
        assertEquals(1, callCount1.get()); // Not incremented after removeAll
        assertEquals(1, callCount2.get()); // Not incremented after removeAll
    }

    @Test
    void getItemReturnsCurrentItem() {
        assertNotNull(state.getItem());
    }

    @Test
    void getOriginalItemReturnsCopy() {
        ItemStack original = state.getOriginalItem();
        assertNotNull(original);
    }
}
