package com.devmod.client.ui.editor.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.components.SlotSelector;

import static org.junit.jupiter.api.Assertions.*;

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
        assertFalse(state.isGlobalMode());

        state.setGlobalMode(false);
        assertFalse(state.isGlobalMode());
    }

    @Test
    void toggleGlobalModeAlternates() {
        assertFalse(state.isGlobalMode());

        state.toggleGlobalMode();
        assertFalse(state.isGlobalMode());

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
        assertEquals("Apply / Specific", state.getModeDescription());

        state.setPreviewMode(false);
        state.setGlobalMode(false);
        assertEquals("Apply / Specific", state.getModeDescription());
    }

    @Test
    void getItemReturnsCurrentItem() {
        assertNotNull(state.getItem());
    }

    @Test
    void setSelectedSlotStoresValue() {
        SlotSelector.SlotInfo slot = new SlotSelector.SlotInfo(
            EquipmentSlot.MAINHAND,
            "Main Hand",
            "M",
            ItemStack.EMPTY
        );

        state.setSelectedSlot(slot);
        assertEquals(slot, state.getSelectedSlot());

        state.setSelectedSlot(null);
        assertNull(state.getSelectedSlot());
    }
}
