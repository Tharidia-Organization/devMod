package com.devmod.ui.editor.systems;

import com.devmod.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.Objects.requireNonNull;

public class MultiEditManagerTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void applyToAll_recordsSuccessAndFailure() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack(requireNonNull(Items.STICK));
        ItemStack b = new ItemStack(requireNonNull(Items.DIRT));
        ItemStack c = new ItemStack(requireNonNull(Items.STONE));

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);
        manager.addToSelection(c, 2);

        AtomicInteger counter = new AtomicInteger(0);

        BatchEditResult res = manager.applyToAll(item -> {
            // Fail for the second item specifically
            if (item.is(requireNonNull(Items.DIRT))) throw new RuntimeException("boom");
            counter.incrementAndGet();
        });

        assertEquals(2, counter.get(), "Modifier should have run for two items");
        assertEquals(2, res.successCount());
        assertEquals(1, res.failureCount());
        assertFalse(res.allFailed());
        assertFalse(res.allSucceeded());
    }
}
