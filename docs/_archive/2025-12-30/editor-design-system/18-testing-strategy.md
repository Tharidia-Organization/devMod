# Testing Strategy

> Last updated: 2025-12-26
> Status: HISTORICAL (design system snapshot)

## Overview

Strategia di testing completa per il sistema editor, con focus su affidabilità e prevenzione regressioni.

## Test Pyramid

```
                    ┌─────────────────┐
                    │   E2E Tests     │  ← 10% - Full user workflows
                    │   (Slow)        │
                ┌───┴─────────────────┴───┐
                │  Integration Tests      │  ← 30% - Module interactions
                │  (Medium)               │
            ┌───┴─────────────────────────┴───┐
            │     Unit Tests                  │  ← 60% - Individual components
            │     (Fast)                      │
            └─────────────────────────────────┘
```

## Unit Tests (60% - Fast)

### 1. Data Models
```java
// Test per record e data structures
@Test
void testItemDebugInfo() {
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    ItemDebugInfo info = ItemDebugInfo.fromItemStack(sword);
    
    assertEquals("minecraft:diamond_sword", info.registryName());
    assertEquals(1, info.stackSize());
    assertEquals(1561, info.maxDamage());
    assertEquals(0, info.currentDamage());
    assertFalse(info.hasCustomData());
}

@Test
void testValueComparison() {
    ValueComparison comp = new ValueComparison(
        "attack_damage", 7.0, 12.0, 7.0, true, true
    );
    
    assertTrue(comp.isModified());
    assertTrue(comp.hasMismatch());
    assertEquals("attack_damage", comp.attributeName());
}

@Test
void testWeaponStats() {
    WeaponStats stats = new WeaponStats(7.0f, 1.6f, 0.0f, 2.5f, 0.0f);
    
    assertEquals(7.0f, stats.baseDamage());
    assertEquals(1.6f, stats.attackSpeed());
    
    WeaponStats modified = stats.withBaseDamage(12.0f);
    assertEquals(12.0f, modified.baseDamage());
    assertEquals(1.6f, modified.attackSpeed()); // Unchanged
}
```

### 2. Layout Engine
```java
@Test
void testEditorLayout() {
    EditorLayout layout = new EditorLayout();
    layout.computePositions(1920, 1080);
    
    Bounds panelBounds = layout.getPanelBounds();
    assertEquals(550, panelBounds.width());
    assertEquals(420, panelBounds.height());
    
    // Panel should be centered
    assertEquals((1920 - 550) / 2, panelBounds.x());
    assertEquals((1080 - 420) / 2, panelBounds.y());
}

@Test
void testSectionLayout() {
    List<EditorSection> sections = List.of(
        new EditorSection.SliderSection("Test", 0, 10, 5, 0xFF0000, v -> {}),
        new EditorSection.ToggleSection("Toggle", true, v -> {})
    );
    
    EditorLayout layout = new EditorLayout();
    layout.computePositions(1920, 1080);
    List<SectionBounds> bounds = layout.layoutSections(sections);
    
    assertEquals(2, bounds.size());
    assertTrue(bounds.get(1).y() > bounds.get(0).y()); // Second section below first
}
```

### 3. Scaling System
```java
@Test
void testScaledCoord() {
    ScaledCoord.setScale(1.0f);
    assertEquals(100, ScaledCoord.scale(100));
    assertEquals(104, ScaledCoord.scale(103)); // Aligned to 4px grid
    
    ScaledCoord.setScale(1.5f);
    assertEquals(152, ScaledCoord.scale(100)); // 150 aligned to 152
}

@Test
void testAutoScaleCalculation() {
    // 1080p screen should use 1.0x scale
    assertEquals(1.0f, EditorScaleCalculator.calculateAutoScale(1920, 1080));
    
    // 1440p screen should use 1.5x scale
    assertEquals(1.5f, EditorScaleCalculator.calculateAutoScale(2560, 1440));
    
    // 4K screen should use 2.0x scale
    assertEquals(2.0f, EditorScaleCalculator.calculateAutoScale(3840, 2160));
    
    // Small screen should clamp to 1.0x
    assertEquals(1.0f, EditorScaleCalculator.calculateAutoScale(800, 600));
}
```

### 4. Weapon Type Detection
```java
@Test
void testWeaponTypeDetection() {
    // Vanilla weapons
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    DetectionResult result = WeaponTypeDetector.detect(sword);
    assertEquals(WeaponType.SWORD, result.type());
    assertEquals(DetectionMethod.CLASS_INSTANCEOF, result.method());
    assertTrue(result.isHighConfidence());
    
    // Non-weapon
    ItemStack dirt = new ItemStack(Items.DIRT);
    result = WeaponTypeDetector.detect(dirt);
    assertEquals(WeaponType.NOT_A_WEAPON, result.type());
}

@Test
void testWeaponTypeDetectionWithTags() {
    // Mock item with weapon tag
    ItemStack customWeapon = createMockItemWithTag(ModTags.Items.EDITABLE_MELEE_WEAPONS);
    DetectionResult result = WeaponTypeDetector.detect(customWeapon);
    
    assertEquals(WeaponType.GENERIC_MELEE, result.type());
    assertEquals(DetectionMethod.ITEM_TAG, result.method());
    assertTrue(result.confidence() >= 0.8f);
}
```

### 5. Persistence Layer
```java
@Test
void testNBTPersistence() {
    ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
    WeaponStats stats = new WeaponStats(12.0f, 2.0f, 15.0f, 2.0f, 3.0f);
    
    // Save to NBT
    WeaponStatsNBT.saveToItemNBT(stack, stats);
    assertTrue(WeaponStatsNBT.hasCustomStats(stack));
    
    // Load from NBT
    WeaponStats loaded = WeaponStatsNBT.loadFromItemNBT(stack);
    assertEquals(stats.baseDamage(), loaded.baseDamage());
    assertEquals(stats.attackSpeed(), loaded.attackSpeed());
    assertEquals(stats.critChance(), loaded.critChance());
}

@Test
void testGlobalConfigPersistence() {
    ArmorStats stats = new ArmorStats(0.8f, 0.5f, 0.3f);
    
    DevModItemConfig.saveGlobalOverride("minecraft:diamond_chestplate", stats);
    ArmorStats loaded = DevModItemConfig.getGlobalOverride("minecraft:diamond_chestplate");
    
    assertNotNull(loaded);
    assertEquals(stats.physicalReduction(), loaded.physicalReduction());
}
```

## Integration Tests (30% - Medium)

### 1. Module Integration
```java
@Test
void testWeaponModuleIntegration() {
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    WeaponModule module = new WeaponModule(sword);
    
    // Test section generation
    List<EditorSection> sections = module.getSections();
    assertFalse(sections.isEmpty());
    
    // Test dirty state
    assertFalse(module.hasUnsavedChanges());
    module.markDirty("test change");
    assertTrue(module.hasUnsavedChanges());
    
    // Test payload building
    CustomPacketPayload payload = module.buildPayload();
    assertNotNull(payload);
}

@Test
void testEditorScreenModuleSwitch() {
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    ItemEditorScreen screen = new ItemEditorScreen(sword, EditorStartTab.WEAPON);
    
    assertEquals("weapon", screen.getActiveModule().getId());
    
    // Switch to armor module (should show warning if dirty)
    screen.switchModule(new ArmorModule(sword));
    // Verify switch behavior
}
```

### 2. PREVIEW/APPLY Mode Integration
```java
@Test
void testPreviewModeIntegration() {
    ItemEditorScreen screen = createTestScreen();
    screen.setPreviewMode(true);
    
    // In preview mode, changes should not mark dirty
    screen.handleSliderChange("attack_damage", 12.0f);
    assertFalse(screen.hasUnsavedChanges());
    
    // Switch to apply mode
    screen.setPreviewMode(false);
    screen.handleSliderChange("attack_damage", 15.0f);
    assertTrue(screen.hasUnsavedChanges());
}

@Test
void testApplyModeIntegration() {
    ItemEditorScreen screen = createTestScreen();
    screen.setPreviewMode(false);
    
    // Make changes
    screen.handleSliderChange("attack_damage", 12.0f);
    assertTrue(screen.hasUnsavedChanges());
    
    // Apply changes
    screen.applyChanges();
    assertFalse(screen.hasUnsavedChanges());
    
    // Verify payload was sent (mock network)
    verify(mockNetworkHandler).sendToServer(any(UpdateWeaponPayload.class));
}
```

### 3. Debug Panel Integration
```java
@Test
void testDebugPanelIntegration() {
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    ItemEditorScreen screen = new ItemEditorScreen(sword, EditorStartTab.WEAPON);
    
    // Switch to debug tab
    screen.switchToTab("DEBUG");
    
    // Verify debug info is populated
    ItemDebugInfo debugInfo = screen.getDebugInfo();
    assertNotNull(debugInfo);
    assertEquals("minecraft:diamond_sword", debugInfo.registryName());
    
    // Test copy to clipboard
    screen.copyDebugInfoToClipboard();
    String clipboardContent = getClipboardContent();
    assertTrue(clipboardContent.contains("DEVMOD DEBUG INFO"));
    assertTrue(clipboardContent.contains("minecraft:diamond_sword"));
}
```

### 4. Scroll System Integration
```java
@Test
void testScrollIntegration() {
    ItemEditorScreen screen = createTestScreenWithManyElements();
    ScrollableContentArea scrollArea = screen.getScrollArea();
    
    // Test initial state
    assertEquals(0, scrollArea.getScrollOffset());
    
    // Simulate scroll
    scrollArea.mouseScrolled(400, 200, -3.0); // Scroll down
    assertTrue(scrollArea.getScrollOffset() > 0);
    
    // Test scroll bounds
    scrollArea.scrollToBottom();
    assertEquals(scrollArea.getMaxScrollOffset(), scrollArea.getScrollOffset());
}
```

## End-to-End Tests (10% - Slow)

### 1. Complete User Workflows
```java
@Test
void testCompleteWeaponEditingWorkflow() {
    // 1. Open weapon editor
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    ItemEditorScreen screen = openWeaponEditor(sword);
    
    // 2. Switch to APPLY mode
    screen.setPreviewMode(false);
    
    // 3. Modify weapon stats
    screen.switchToTab("STATS");
    screen.setSliderValue("attack_damage", 12.0f);
    screen.setSliderValue("attack_speed", 2.0f);
    
    // 4. Apply changes
    assertTrue(screen.hasUnsavedChanges());
    screen.applyChanges();
    assertFalse(screen.hasUnsavedChanges());
    
    // 5. Verify changes persisted
    screen.close();
    screen = openWeaponEditor(sword);
    assertEquals(12.0f, screen.getSliderValue("attack_damage"));
    assertEquals(2.0f, screen.getSliderValue("attack_speed"));
}

@Test
void testDebugWorkflow() {
    ItemStack problematicSword = createProblematicItem();
    ItemEditorScreen screen = openWeaponEditor(problematicSword);
    
    // 1. Switch to debug tab
    screen.switchToTab("DEBUG");
    
    // 2. Verify debug info shows problems
    List<ValueComparison> comparisons = screen.getValueComparisons();
    assertTrue(comparisons.stream().anyMatch(ValueComparison::hasMismatch));
    
    // 3. Copy debug info
    screen.copyDebugInfoToClipboard();
    String debugInfo = getClipboardContent();
    assertTrue(debugInfo.contains("[MISMATCH]"));
    
    // 4. Use debug info to identify problem
    assertTrue(debugInfo.contains("attack_damage"));
}
```

### 2. Multi-Resolution Testing
```java
@Test
void testMultiResolutionSupport() {
    int[][] resolutions = {
        {1920, 1080}, {2560, 1440}, {3840, 2160}, {1366, 768}
    };
    
    for (int[] res : resolutions) {
        ItemEditorScreen screen = createTestScreen();
        screen.init(mockMinecraft, res[0], res[1]);
        
        // Verify panel fits on screen
        Bounds panelBounds = screen.getPanelBounds();
        assertTrue(panelBounds.x() >= 0);
        assertTrue(panelBounds.y() >= 0);
        assertTrue(panelBounds.x() + panelBounds.width() <= res[0]);
        assertTrue(panelBounds.y() + panelBounds.height() <= res[1]);
        
        // Verify UI scale is appropriate
        float scale = screen.getUIScale();
        assertTrue(scale >= 1.0f && scale <= 2.0f);
    }
}
```

## Visual Regression Testing

### 1. Screenshot Comparison
```java
@Test
void testVisualRegression() {
    ItemEditorScreen screen = createTestScreen();
    
    // Render screen to image
    BufferedImage actual = renderScreenToImage(screen);
    
    // Compare with reference image
    BufferedImage expected = loadReferenceImage("weapon_editor_baseline.png");
    
    double similarity = compareImages(actual, expected);
    assertTrue(similarity > 0.95, "Visual regression detected: " + similarity);
}

@Test
void testDebugOverlayVisual() {
    ItemEditorScreen screen = createTestScreen();
    DebugOverlay.toggle(); // Enable debug overlay
    
    BufferedImage actual = renderScreenToImage(screen);
    BufferedImage expected = loadReferenceImage("debug_overlay_baseline.png");
    
    double similarity = compareImages(actual, expected);
    assertTrue(similarity > 0.90, "Debug overlay visual regression: " + similarity);
}
```

## Performance Testing

### 1. Render Performance
```java
@Test
void testRenderPerformance() {
    ItemEditorScreen screen = createTestScreenWithManyElements();
    
    // Warm up
    for (int i = 0; i < 100; i++) {
        screen.render(mockGraphics, 0, 0, 0.0f);
    }
    
    // Measure render time
    long startTime = System.nanoTime();
    for (int i = 0; i < 1000; i++) {
        screen.render(mockGraphics, 0, 0, 0.0f);
    }
    long endTime = System.nanoTime();
    
    double avgRenderTime = (endTime - startTime) / 1000.0 / 1_000_000.0; // ms
    assertTrue(avgRenderTime < 1.0, "Render too slow: " + avgRenderTime + "ms");
}

@Test
void testScrollPerformance() {
    ScrollableContentArea scrollArea = createScrollAreaWithManyElements();
    
    // Test scroll performance
    long startTime = System.nanoTime();
    for (int i = 0; i < 1000; i++) {
        scrollArea.mouseScrolled(400, 200, -1.0);
        scrollArea.tick();
    }
    long endTime = System.nanoTime();
    
    double avgScrollTime = (endTime - startTime) / 1000.0 / 1_000_000.0; // ms
    assertTrue(avgScrollTime < 0.1, "Scroll too slow: " + avgScrollTime + "ms");
}
```

## Memory Testing

### 1. Memory Leaks
```java
@Test
void testMemoryLeaks() {
    Runtime runtime = Runtime.getRuntime();
    long initialMemory = runtime.totalMemory() - runtime.freeMemory();
    
    // Create and destroy many editor screens
    for (int i = 0; i < 100; i++) {
        ItemEditorScreen screen = createTestScreen();
        screen.init(mockMinecraft, 1920, 1080);
        screen.render(mockGraphics, 0, 0, 0.0f);
        screen.onClose();
        
        if (i % 10 == 0) {
            System.gc(); // Suggest garbage collection
            Thread.sleep(10);
        }
    }
    
    System.gc();
    Thread.sleep(100);
    
    long finalMemory = runtime.totalMemory() - runtime.freeMemory();
    long memoryIncrease = finalMemory - initialMemory;
    
    // Allow some memory increase but not excessive
    assertTrue(memoryIncrease < 50 * 1024 * 1024, // 50MB
        "Memory leak detected: " + (memoryIncrease / 1024 / 1024) + "MB");
}
```

## Test Utilities

### 1. Mock Factories
```java
public class TestUtils {
    public static ItemEditorScreen createTestScreen() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        return new ItemEditorScreen(sword, EditorStartTab.WEAPON);
    }
    
    public static ItemStack createProblematicItem() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        // Add conflicting NBT data
        CompoundTag tag = new CompoundTag();
        tag.putFloat("attack_damage", 999.0f); // Mismatch with expected
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
    
    public static GuiGraphics mockGraphics() {
        return mock(GuiGraphics.class);
    }
}
```

### 2. Assertion Helpers
```java
public class EditorAssertions {
    public static void assertValidLayout(EditorLayout layout) {
        Bounds panel = layout.getPanelBounds();
        Bounds header = layout.getHeaderBounds();
        Bounds footer = layout.getFooterBounds();
        Bounds leftColumn = layout.getLeftColumnBounds();
        Bounds content = layout.getContentBounds();
        
        // Verify no overlaps
        assertFalse(header.intersects(footer));
        assertFalse(leftColumn.intersects(content));
        
        // Verify alignment
        assertTrue(EditorSpacing.isOnGrid(panel.x()));
        assertTrue(EditorSpacing.isOnGrid(panel.y()));
        assertTrue(EditorSpacing.isOnGrid(panel.width()));
        assertTrue(EditorSpacing.isOnGrid(panel.height()));
    }
    
    public static void assertValidDebugInfo(ItemDebugInfo info) {
        assertNotNull(info.registryName());
        assertTrue(info.stackSize() > 0);
        assertTrue(info.maxDamage() >= 0);
        assertTrue(info.currentDamage() >= 0);
        assertTrue(info.currentDamage() <= info.maxDamage());
        assertTrue(info.nbtTagCount() >= 0);
    }
}
```

## CI/CD Integration

### 1. Test Execution
```yaml
# .github/workflows/test.yml
name: Test Suite
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      
      - name: Run Unit Tests
        run: ./gradlew test --tests "*Unit*"
      
      - name: Run Integration Tests
        run: ./gradlew test --tests "*Integration*"
      
      - name: Run E2E Tests
        run: ./gradlew test --tests "*E2E*"
        
      - name: Generate Test Report
        run: ./gradlew jacocoTestReport
        
      - name: Upload Coverage
        uses: codecov/codecov-action@v3
```

### 2. Performance Benchmarks
```yaml
      - name: Performance Benchmarks
        run: ./gradlew jmh
        
      - name: Check Performance Regression
        run: |
          if [ -f benchmark-results.json ]; then
            python scripts/check-performance-regression.py
          fi
```

## Test Coverage Goals

| Component | Target Coverage | Current |
|-----------|----------------|---------|
| Data Models | 95% | - |
| Layout Engine | 90% | - |
| UI Components | 85% | - |
| Integration | 80% | - |
| E2E Workflows | 70% | - |

## Continuous Testing

### 1. Test on Every Commit
- Unit tests (< 30 seconds)
- Integration tests (< 2 minutes)
- Visual regression (< 5 minutes)

### 2. Nightly Testing
- Full E2E suite
- Performance benchmarks
- Memory leak detection
- Multi-resolution testing

### 3. Release Testing
- Manual QA checklist
- Compatibility testing
- Stress testing
- User acceptance testing

**Priorità**: Implementare unit tests per Debug Panel PRIMA di sviluppare la feature.
