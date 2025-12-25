package com.devmod.client.ui.editor.systems;

import com.devmod.client.ui.editor.ItemEditorDataManager;
import com.devmod.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.Objects.requireNonNull;

/**
 * Integration tests for MultiEdit system with preset application.
 */
public class MultiEditIntegrationTest {
    
    private MultiEditManager manager;
    private ItemEditorPresetManager presetManager;
    private AtomicInteger persistCallCount;

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }
    
    @BeforeEach
    void setUp() {
        manager = new MultiEditManager();
        presetManager = new ItemEditorPresetManager();
        persistCallCount = new AtomicInteger(0);
        
        // Mock persistence handler
        manager.setPersistenceHandler((item, slot) -> {
            persistCallCount.incrementAndGet();
            return true; // Success
        });
    }
    
    @Test
    void batchPresetApplication_withPersistence_callsPersistenceHandler() {
        // Setup items
        ItemStack sword1 = new ItemStack(requireNonNull(Items.DIAMOND_SWORD));
        ItemStack sword2 = new ItemStack(requireNonNull(Items.IRON_SWORD));
        manager.addToSelection(sword1, 0);
        manager.addToSelection(sword2, 1);
        
        // Create preset
        ItemEditorDataManager.PresetData data = new ItemEditorDataManager.PresetData("test_batch");
        data.scope = "GLOBAL";
        data.statValues = new ArrayList<>();
        float[] vals = {1,1,1,1,12f,1.8f,0,0,0,0,0,1.5f,0,0,0};
        for (float v : vals) data.statValues.add(v);
        DataPreset preset = new DataPreset(data);
        
        // Apply with persistence
        BatchEditResult result = manager.applyPresetToAll(preset, presetManager, true);
        
        assertEquals(2, result.successCount(), "Both items should succeed");
        assertEquals(0, result.failureCount(), "No failures expected");
        assertEquals(2, persistCallCount.get(), "Persistence should be called for each item");
    }
    
    @Test
    void batchPresetApplication_withoutPersistence_doesNotCallHandler() {
        ItemStack sword = new ItemStack(requireNonNull(Items.DIAMOND_SWORD));
        manager.addToSelection(sword, 0);
        
        ItemEditorDataManager.PresetData data = new ItemEditorDataManager.PresetData("test_no_persist");
        data.statValues = new ArrayList<>();
        float[] vals = {1,1,1,1,8f,0,0,0,0,0,0,1.5f,0,0,0};
        for (float v : vals) data.statValues.add(v);
        DataPreset preset = new DataPreset(data);
        
        // Apply without persistence
        BatchEditResult result = manager.applyPresetToAll(preset, presetManager, false);
        
        assertEquals(1, result.successCount(), "Item should succeed");
        assertEquals(0, persistCallCount.get(), "Persistence should not be called");
    }
    
    @Test
    void errorReportGeneration_includesAllFailureDetails() {
        // Force a failure by using invalid preset
        ItemStack item = new ItemStack(requireNonNull(Items.STICK)); // Non-weapon item
        manager.addToSelection(item, 0);
        
        ItemEditorDataManager.PresetData data = new ItemEditorDataManager.PresetData("invalid_preset");
        data.statValues = new ArrayList<>(); // Empty -> applyPreset will fail
        DataPreset preset = new DataPreset(data);
        
        BatchEditResult result = manager.applyPresetToAll(preset, presetManager, false);
        
        assertTrue(result.failureCount() > 0, "Should have failures");
        
        String errorReport = result.generateErrorReport();
        assertNotNull(errorReport);
        assertTrue(errorReport.contains("DevMod Batch Edit Errors"));
        assertTrue(errorReport.contains("Failures: " + result.failureCount()));
        assertTrue(errorReport.contains("Error Details"));
    }
}
