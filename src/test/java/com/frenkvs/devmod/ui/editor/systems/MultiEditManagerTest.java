package com.frenkvs.devmod.ui.editor.systems;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MultiEditManagerTest {

    @Test
    public void applyToAll_recordsSuccessAndFailure() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack("A");
        ItemStack b = new ItemStack("B");
        ItemStack c = new ItemStack("C");

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);
        manager.addToSelection(c, 2);

        AtomicInteger counter = new AtomicInteger(0);

        BatchEditResult res = manager.applyToAll(item -> {
            // Fail for the second item specifically (reference equality works on distinct instances)
            if ("B".equals(item.getHoverName().getString())) throw new RuntimeException("boom");
            counter.incrementAndGet();
        });

        assertEquals(2, counter.get(), "Modifier should have run for two items");
        assertEquals(2, res.successCount());
        assertEquals(1, res.failureCount());
        assertFalse(res.allFailed());
        assertFalse(res.allSucceeded());
    }
}
