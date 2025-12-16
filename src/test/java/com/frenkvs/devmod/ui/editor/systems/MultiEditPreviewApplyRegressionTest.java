package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.Objects.requireNonNull;

/**
 * Regression tests to ensure preview (no persist) vs apply (persist) behavior
 * for the MultiEdit subsystem.
 */
public class MultiEditPreviewApplyRegressionTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void previewMode_doesNotInvokePersistenceHandler() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack(requireNonNull(Items.STONE));
        ItemStack b = new ItemStack(requireNonNull(Items.DIRT)); // use different id to avoid deduplication

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);

        AtomicInteger persistCalled = new AtomicInteger(0);

        // persistence handler should NOT be called in preview mode (persist=false)
        manager.setPersistenceHandler((item, slot) -> {
            persistCalled.incrementAndGet();
            return true;
        });

        Preset preset = new Preset() {
            @Override public java.util.function.Predicate<ItemStack> scope() { return s -> true; }
        };

        PresetManager pm = new PresetManager() {
            @Override
            public boolean applyPreset(Preset preset, ItemStack item, int slotIndex) {
                // simulate modifying the passed item (preview copy)
                try {
                    item.setCount(item.getCount() + 1);
                } catch (Exception ignored) {}
                return true;
            }
        };

        BatchEditResult res = manager.applyPresetToAll(preset, pm, false);

        assertEquals(2, res.successCount(), "Both selected items should be processed (preview)");
        assertEquals(0, res.failureCount(), "No failures expected in preview mode");
        assertEquals(0, persistCalled.get(), "Persistence handler must NOT be called in preview mode");
    }

    @Test
    public void applyWithPersist_invokesPersistenceHandler_and_reportsFailures() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack(requireNonNull(Items.STONE));
        ItemStack b = new ItemStack(requireNonNull(Items.DIRT)); // use different id to avoid deduplication

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);

        AtomicInteger persistCalled = new AtomicInteger(0);

        // persistence handler simulates persisting only the first slot
        manager.setPersistenceHandler((item, slot) -> {
            persistCalled.incrementAndGet();
            return slot == 0;
        });

        Preset preset = new Preset() {
            @Override public java.util.function.Predicate<ItemStack> scope() { return s -> true; }
        };

        PresetManager pm = new PresetManager() {
            @Override
            public boolean applyPreset(Preset preset, ItemStack item, int slotIndex) {
                // pretend we update the item's NBT/state
                try { item.setCount(5); } catch (Exception ignored) {}
                return true;
            }
        };

        BatchEditResult res = manager.applyPresetToAll(preset, pm, true);

        // persistence handler should be called for each selected item
        assertEquals(2, persistCalled.get(), "Persistence handler must be called for each selected item when persist=true");

        // one should succeed (slot 0 returned true), one should be reported as failure due to persist=false
        assertEquals(1, res.successCount(), "One item should be persisted successfully");
        assertEquals(1, res.failureCount(), "One item should fail persistence and be reported");
    }
}
