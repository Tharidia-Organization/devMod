# Performance Considerations

## Overview

Linee guida per ottimizzazioni performance nel sistema editor, con focus su rendering fluido e responsività.

## Target Performance

### Rendering Targets
| Metric | Target | Critical Threshold |
|--------|--------|--------------------|
| **Frame Time** | < 16ms (60 FPS) | < 33ms (30 FPS) |
| **Input Latency** | < 50ms | < 100ms |
| **Memory Usage** | < 100MB | < 200MB |
| **Startup Time** | < 500ms | < 1000ms |

### Scalability Targets
| Scenario | Target | Max Acceptable |
|----------|--------|----------------|
| **Sections per Tab** | 50+ | 100+ |
| **Items in MultiEdit** | 100+ | 500+ |
| **Session Duration** | 1 hour+ | 4 hours+ |
| **Concurrent Editors** | 10+ | 50+ |

## Rendering Optimizations

### 1. Render Culling
```java
/**
 * Only render sections visible in viewport.
 */
public class ScrollableContentArea {
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Enable scissor clipping
        graphics.enableScissor(VIEWPORT_X, VIEWPORT_Y, 
            VIEWPORT_X + VIEWPORT_WIDTH, VIEWPORT_Y + VIEWPORT_HEIGHT);
        
        // Calculate visible range
        float visibleTop = scrollOffset;
        float visibleBottom = scrollOffset + VIEWPORT_HEIGHT;
        
        // Only render visible sections
        for (SectionBounds bounds : sectionBounds) {
            if (bounds.y() + bounds.height() >= visibleTop && 
                bounds.y() <= visibleBottom) {
                bounds.section().render(graphics, bounds.x(), bounds.y(), bounds.width());
            }
        }
        
        graphics.disableScissor();
    }
}
```

### 2. Batch Rendering
```java
/**
 * Batch similar render operations to reduce state changes.
 */
public class BatchRenderer {
    private final List<RenderCommand> textCommands = new ArrayList<>();
    private final List<RenderCommand> rectCommands = new ArrayList<>();
    
    public void queueText(String text, int x, int y, int color) {
        textCommands.add(new TextCommand(text, x, y, color));
    }
    
    public void queueRect(int x, int y, int width, int height, int color) {
        rectCommands.add(new RectCommand(x, y, width, height, color));
    }
    
    public void flush(GuiGraphics graphics) {
        // Batch all rectangles
        for (RenderCommand cmd : rectCommands) {
            cmd.execute(graphics);
        }
        rectCommands.clear();
        
        // Batch all text
        for (RenderCommand cmd : textCommands) {
            cmd.execute(graphics);
        }
        textCommands.clear();
    }
}
```

### 3. Dirty Region Tracking
```java
/**
 * Only re-render changed regions.
 */
public class DirtyRegionTracker {
    private final Set<Rectangle> dirtyRegions = new HashSet<>();
    private boolean fullRedraw = true;
    
    public void markDirty(int x, int y, int width, int height) {
        dirtyRegions.add(new Rectangle(x, y, width, height));
        fullRedraw = false;
    }
    
    public void markFullRedraw() {
        fullRedraw = true;
        dirtyRegions.clear();
    }
    
    public boolean needsRedraw(int x, int y, int width, int height) {
        if (fullRedraw) return true;
        
        Rectangle region = new Rectangle(x, y, width, height);
        return dirtyRegions.stream().anyMatch(dirty -> dirty.intersects(region));
    }
    
    public void clearDirty() {
        dirtyRegions.clear();
        fullRedraw = false;
    }
}
```

## Memory Management

### 1. Object Pooling
```java
/**
 * Pool frequently created objects to reduce GC pressure.
 */
public class RenderObjectPool {
    private final Queue<StringBuilder> stringBuilders = new ArrayDeque<>();
    private final Queue<ArrayList<String>> stringLists = new ArrayDeque<>();
    
    public StringBuilder borrowStringBuilder() {
        StringBuilder sb = stringBuilders.poll();
        if (sb == null) {
            sb = new StringBuilder(256);
        } else {
            sb.setLength(0);
        }
        return sb;
    }
    
    public void returnStringBuilder(StringBuilder sb) {
        if (sb.capacity() < 1024) { // Don't pool huge builders
            stringBuilders.offer(sb);
        }
    }
    
    public List<String> borrowStringList() {
        ArrayList<String> list = stringLists.poll();
        if (list == null) {
            list = new ArrayList<>(16);
        } else {
            list.clear();
        }
        return list;
    }
    
    public void returnStringList(ArrayList<String> list) {
        if (list.size() < 100) { // Don't pool huge lists
            stringLists.offer(list);
        }
    }
}
```

### 2. Lazy Loading
```java
/**
 * Load expensive data only when needed.
 */
public class LazyDebugInfo {
    private ItemStack item;
    private ItemDebugInfo debugInfo;
    private List<ValueComparison> comparisons;
    private String nbtString;
    
    public ItemDebugInfo getDebugInfo() {
        if (debugInfo == null) {
            debugInfo = ItemDebugInfo.fromItemStack(item);
        }
        return debugInfo;
    }
    
    public List<ValueComparison> getValueComparisons() {
        if (comparisons == null) {
            comparisons = computeValueComparisons();
        }
        return comparisons;
    }
    
    public String getNbtString() {
        if (nbtString == null) {
            nbtString = formatNbtForDisplay(item.getTag(), 0);
        }
        return nbtString;
    }
    
    public void invalidate() {
        debugInfo = null;
        comparisons = null;
        nbtString = null;
    }
}
```

### 3. Memory Leak Prevention
```java
/**
 * Automatic cleanup of long-lived objects.
 */
public class EditorSessionManager {
    private final Map<UUID, EditorSession> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
    
    public EditorSessionManager() {
        // Cleanup old sessions every 5 minutes
        cleanup.scheduleAtFixedRate(this::cleanupOldSessions, 5, 5, TimeUnit.MINUTES);
    }
    
    public void registerSession(UUID sessionId, EditorSession session) {
        activeSessions.put(sessionId, session);
        session.setLastAccess(System.currentTimeMillis());
    }
    
    private void cleanupOldSessions() {
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        
        activeSessions.entrySet().removeIf(entry -> {
            EditorSession session = entry.getValue();
            if (session.getLastAccess() < cutoff) {
                session.cleanup(); // Release resources
                return true;
            }
            return false;
        });
    }
    
    public void shutdown() {
        cleanup.shutdown();
        activeSessions.values().forEach(EditorSession::cleanup);
        activeSessions.clear();
    }
}
```

## String Operations

### 1. String Formatting Optimization
```java
/**
 * Pre-format strings and cache results.
 */
public class FormattedText {
    private final String template;
    private final Object[] args;
    private String cached;
    private long cacheTime;
    
    public FormattedText(String template, Object... args) {
        this.template = template;
        this.args = args.clone();
    }
    
    public String get() {
        long now = System.currentTimeMillis();
        if (cached == null || now - cacheTime > 1000) { // Cache for 1 second
            cached = String.format(template, args);
            cacheTime = now;
        }
        return cached;
    }
    
    public void update(Object... newArgs) {
        System.arraycopy(newArgs, 0, args, 0, Math.min(args.length, newArgs.length));
        cached = null; // Invalidate cache
    }
}

// Usage
private final FormattedText damageText = new FormattedText("Damage: %.1f/%.1f", 0.0f, 0.0f);

public void render(GuiGraphics graphics, int x, int y) {
    damageText.update(currentDamage, maxDamage);
    graphics.drawString(font, damageText.get(), x, y, color, false);
}
```

### 2. StringBuilder Reuse
```java
/**
 * Reuse StringBuilder instances to reduce allocations.
 */
public class StringBuilderCache {
    private static final ThreadLocal<StringBuilder> CACHE = 
        ThreadLocal.withInitial(() -> new StringBuilder(256));
    
    public static StringBuilder acquire() {
        StringBuilder sb = CACHE.get();
        sb.setLength(0);
        return sb;
    }
    
    public static String getStringAndRelease(StringBuilder sb) {
        String result = sb.toString();
        if (sb.capacity() < 1024) {
            sb.setLength(0);
        } else {
            CACHE.set(new StringBuilder(256));
        }
        return result;
    }
}

// Usage
public String formatDebugInfo() {
    StringBuilder sb = StringBuilderCache.acquire();
    sb.append("=== DEBUG INFO ===\n");
    sb.append("Item: ").append(registryName).append("\n");
    sb.append("Damage: ").append(damage).append("/").append(maxDamage).append("\n");
    return StringBuilderCache.getStringAndRelease(sb);
}
```

## Collection Optimizations

### 1. Pre-sized Collections
```java
/**
 * Size collections appropriately to avoid resizing.
 */
public class OptimizedCollections {
    // Pre-size based on expected usage
    private final List<EditorSection> sections = new ArrayList<>(16);
    private final Map<String, ValueComparison> comparisons = new HashMap<>(32);
    private final Set<String> dirtyFields = new HashSet<>(8);
    
    // Use primitive collections where possible
    private final TIntObjectHashMap<String> indexToName = new TIntObjectHashMap<>();
    private final TFloatList values = new TFloatArrayList(16);
}
```

### 2. Efficient Iteration
```java
/**
 * Use efficient iteration patterns.
 */
public class EfficientIteration {
    
    // ✅ GOOD: Index-based iteration for ArrayList
    public void processListEfficiently(List<EditorSection> sections) {
        for (int i = 0, size = sections.size(); i < size; i++) {
            EditorSection section = sections.get(i);
            processSection(section);
        }
    }
    
    // ✅ GOOD: Enhanced for-loop for unknown collection types
    public void processCollectionEfficiently(Collection<EditorSection> sections) {
        for (EditorSection section : sections) {
            processSection(section);
        }
    }
    
    // ❌ BAD: Iterator creation in loop
    public void processInefficiently(List<EditorSection> sections) {
        for (Iterator<EditorSection> it = sections.iterator(); it.hasNext();) {
            EditorSection section = it.next();
            processSection(section);
        }
    }
}
```

## Caching Strategies

### 1. Layout Caching
```java
/**
 * Cache expensive layout calculations.
 */
public class LayoutCache {
    private final Map<LayoutKey, List<SectionBounds>> cache = new ConcurrentHashMap<>();
    
    public List<SectionBounds> getLayout(List<EditorSection> sections, int width, int height) {
        LayoutKey key = new LayoutKey(sections, width, height);
        return cache.computeIfAbsent(key, this::computeLayout);
    }
    
    private List<SectionBounds> computeLayout(LayoutKey key) {
        // Expensive layout calculation
        return EditorLayout.layoutSections(key.sections, key.width, key.height);
    }
    
    public void invalidate() {
        cache.clear();
    }
    
    private record LayoutKey(List<EditorSection> sections, int width, int height) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LayoutKey other)) return false;
            return width == other.width && height == other.height && 
                   Objects.equals(sections, other.sections);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(sections, width, height);
        }
    }
}
```

### 2. Texture Caching
```java
/**
 * Cache rendered textures to avoid re-rendering.
 */
public class TextureCache {
    private final Map<String, CachedTexture> cache = new ConcurrentHashMap<>();
    private final int maxCacheSize = 100;
    
    public ResourceLocation getCachedTexture(String key, Supplier<BufferedImage> generator) {
        CachedTexture cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            cached.updateLastUsed();
            return cached.texture;
        }
        
        // Generate new texture
        BufferedImage image = generator.get();
        ResourceLocation texture = uploadTexture(key, image);
        
        cache.put(key, new CachedTexture(texture, System.currentTimeMillis()));
        
        // Cleanup old entries
        if (cache.size() > maxCacheSize) {
            cleanupOldTextures();
        }
        
        return texture;
    }
    
    private void cleanupOldTextures() {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        cache.entrySet().removeIf(entry -> entry.getValue().lastUsed < cutoff);
    }
    
    private static class CachedTexture {
        final ResourceLocation texture;
        long lastUsed;
        
        CachedTexture(ResourceLocation texture, long lastUsed) {
            this.texture = texture;
            this.lastUsed = lastUsed;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - lastUsed > TimeUnit.MINUTES.toMillis(10);
        }
        
        void updateLastUsed() {
            lastUsed = System.currentTimeMillis();
        }
    }
}
```

## Network Optimizations

### 1. Payload Batching
```java
/**
 * Batch multiple changes into single network packet.
 */
public class PayloadBatcher {
    private final List<AttributeChange> pendingChanges = new ArrayList<>();
    private final Timer batchTimer = new Timer();
    private TimerTask currentTask;
    
    public void queueChange(String attribute, Object value) {
        pendingChanges.add(new AttributeChange(attribute, value));
        
        // Cancel existing timer
        if (currentTask != null) {
            currentTask.cancel();
        }
        
        // Start new timer - batch changes for 100ms
        currentTask = new TimerTask() {
            @Override
            public void run() {
                flushChanges();
            }
        };
        batchTimer.schedule(currentTask, 100);
    }
    
    private void flushChanges() {
        if (!pendingChanges.isEmpty()) {
            BatchUpdatePayload payload = new BatchUpdatePayload(
                List.copyOf(pendingChanges)
            );
            PacketDistributor.sendToServer(payload);
            pendingChanges.clear();
        }
    }
    
    public void forceFlush() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        flushChanges();
    }
}
```

### 2. Delta Compression
```java
/**
 * Only send changed values, not full state.
 */
public class DeltaPayload implements CustomPacketPayload {
    private final Map<String, Object> changes;
    private final long timestamp;
    
    public DeltaPayload(Map<String, Object> oldValues, Map<String, Object> newValues) {
        this.changes = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
        
        // Only include changed values
        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = oldValues.get(key);
            
            if (!Objects.equals(oldValue, newValue)) {
                changes.put(key, newValue);
            }
        }
    }
    
    public boolean isEmpty() {
        return changes.isEmpty();
    }
    
    public Map<String, Object> getChanges() {
        return changes;
    }
}
```

## Profiling Integration

### 1. Performance Monitoring
```java
/**
 * Built-in performance monitoring.
 */
public class PerformanceMonitor {
    private final Map<String, PerformanceMetric> metrics = new ConcurrentHashMap<>();
    
    public void startTiming(String operation) {
        metrics.put(operation, new PerformanceMetric(System.nanoTime()));
    }
    
    public void endTiming(String operation) {
        PerformanceMetric metric = metrics.get(operation);
        if (metric != null) {
            metric.addSample(System.nanoTime() - metric.startTime);
        }
    }
    
    public void logSlowOperations() {
        metrics.entrySet().stream()
            .filter(entry -> entry.getValue().getAverageMs() > 1.0)
            .forEach(entry -> {
                LOGGER.warn("Slow operation {}: avg={}ms, max={}ms, count={}",
                    entry.getKey(),
                    entry.getValue().getAverageMs(),
                    entry.getValue().getMaxMs(),
                    entry.getValue().getSampleCount());
            });
    }
    
    private static class PerformanceMetric {
        long startTime;
        long totalTime;
        long maxTime;
        int sampleCount;
        
        PerformanceMetric(long startTime) {
            this.startTime = startTime;
        }
        
        void addSample(long duration) {
            totalTime += duration;
            maxTime = Math.max(maxTime, duration);
            sampleCount++;
        }
        
        double getAverageMs() {
            return sampleCount > 0 ? (totalTime / sampleCount) / 1_000_000.0 : 0;
        }
        
        double getMaxMs() {
            return maxTime / 1_000_000.0;
        }
        
        int getSampleCount() {
            return sampleCount;
        }
    }
}

// Usage
public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    PerformanceMonitor.INSTANCE.startTiming("editor_render");
    try {
        // Render logic
        renderContent(graphics, mouseX, mouseY);
    } finally {
        PerformanceMonitor.INSTANCE.endTiming("editor_render");
    }
}
```

### 2. Memory Profiling
```java
/**
 * Track memory usage patterns.
 */
public class MemoryProfiler {
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final List<MemorySnapshot> snapshots = new ArrayList<>();
    
    public void takeSnapshot(String label) {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        
        snapshots.add(new MemorySnapshot(
            label,
            System.currentTimeMillis(),
            heap.getUsed(),
            heap.getMax(),
            nonHeap.getUsed()
        ));
        
        // Keep only last 100 snapshots
        if (snapshots.size() > 100) {
            snapshots.remove(0);
        }
    }
    
    public void detectMemoryLeaks() {
        if (snapshots.size() < 10) return;
        
        // Check if memory usage is consistently increasing
        long trend = 0;
        for (int i = 1; i < snapshots.size(); i++) {
            long diff = snapshots.get(i).heapUsed - snapshots.get(i-1).heapUsed;
            trend += diff;
        }
        
        if (trend > 50 * 1024 * 1024) { // 50MB increase
            LOGGER.warn("Potential memory leak detected: {}MB increase over {} samples",
                trend / 1024 / 1024, snapshots.size());
        }
    }
    
    private record MemorySnapshot(
        String label,
        long timestamp,
        long heapUsed,
        long heapMax,
        long nonHeapUsed
    ) {}
}
```

## Configuration

### Performance Config
```toml
# config/devmod-client.toml

[performance]
# Enable performance monitoring
enableProfiling = false

# Render optimizations
enableRenderCulling = true
enableBatchRendering = true
enableDirtyRegions = true

# Memory management
enableObjectPooling = true
maxCacheSize = 100
sessionTimeoutMinutes = 60

# Network optimizations
batchDelayMs = 100
enableDeltaCompression = true

# Debug settings
logSlowOperations = true
slowOperationThresholdMs = 1.0
```

## Benchmarking

### JMH Benchmarks
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class EditorBenchmarks {
    
    private ItemEditorScreen screen;
    private GuiGraphics graphics;
    
    @Setup
    public void setup() {
        screen = TestUtils.createTestScreen();
        graphics = TestUtils.mockGraphics();
    }
    
    @Benchmark
    public void renderScreen() {
        screen.render(graphics, 0, 0, 0.0f);
    }
    
    @Benchmark
    public void layoutSections() {
        List<EditorSection> sections = screen.getActiveModule().getSections();
        EditorLayout.layoutSections(sections);
    }
    
    @Benchmark
    public void formatDebugInfo() {
        screen.getDebugInfo().formatForClipboard();
    }
}
```

## Performance Regression Detection

### Automated Checks
```python
# scripts/check-performance-regression.py
import json
import sys

def check_regression(baseline_file, current_file):
    with open(baseline_file) as f:
        baseline = json.load(f)
    
    with open(current_file) as f:
        current = json.load(f)
    
    regressions = []
    
    for benchmark in baseline:
        name = benchmark['benchmark']
        baseline_score = benchmark['primaryMetric']['score']
        
        current_benchmark = next((b for b in current if b['benchmark'] == name), None)
        if not current_benchmark:
            continue
            
        current_score = current_benchmark['primaryMetric']['score']
        regression = (current_score - baseline_score) / baseline_score
        
        if regression > 0.1:  # 10% regression threshold
            regressions.append({
                'benchmark': name,
                'baseline': baseline_score,
                'current': current_score,
                'regression': f"{regression:.1%}"
            })
    
    if regressions:
        print("Performance regressions detected:")
        for reg in regressions:
            print(f"  {reg['benchmark']}: {reg['baseline']:.2f} -> {reg['current']:.2f} ({reg['regression']})")
        sys.exit(1)
    else:
        print("No performance regressions detected")

if __name__ == "__main__":
    check_regression("baseline-benchmarks.json", "current-benchmarks.json")
```

**Priorità**: Implementare performance monitoring nel debug panel per identificare bottleneck durante sviluppo.

---

## Implementation Status (2025-01)

| Component | File | Status |
|-----------|------|--------|
| `PerformanceMonitor` | `ui/editor/PerformanceMonitor.java` | ✅ Implemented (frame + operation timing) |
| `EditorCache` | `ui/editor/core/EditorCache.java` | ✅ Implemented (TTL + stats) |
| `StringBuilderCache` | `ui/editor/core/StringBuilderCache.java` | ✅ Implemented |
| `RenderObjectPool` | `ui/editor/core/RenderObjectPool.java` | ✅ Implemented |
| `DirtyRegionTracker` | `ui/editor/core/DirtyRegionTracker.java` | ✅ Implemented |
| `ScrollableContentArea` | `ui/editor/components/ScrollableContentArea.java` | ✅ Scissoring (culling pending) |
| `BatchRenderer` | - | ⏳ Not implemented |
| `LayoutCache` | - | ⏳ Not implemented |
| `PayloadBatcher` | - | ⏳ Not implemented (network) |
| `MemoryProfiler` | - | ⏳ Not implemented |
| Config options | - | ⏳ Not implemented |

**Features implemented:**
- Frame timing with 60-sample rolling average
- Operation timing with `startTiming()/endTiming()` and try-with-resources `time()`
- Slow operation logging (threshold: 1ms)
- Thread-local StringBuilder caching
- Object pooling for StringBuilder, ArrayList<String>, int[]
- Dirty region tracking for partial redraws
- TTL-based cache with hit/miss stats

**Integration points:**
- `ItemEditorScreen.render()` - uses `PerformanceMonitor` singleton with operation timing for content rendering
- `DebugOverlay.renderInfoPanel()` - uses `StringBuilderCache` and `RenderObjectPool` for string formatting
- `ScrollableContentArea.render()` - uses `DirtyRegionTracker` to track scroll-triggered redraws

**Usage examples:**

```java
// StringBuilderCache
String result = StringBuilderCache.build(sb -> {
    sb.append("Damage: ").append(damage).append("/").append(maxDamage);
});

// PerformanceMonitor operation timing
try (var timer = PerformanceMonitor.getInstance().time("render_sections")) {
    renderSections(graphics);
}

// DirtyRegionTracker
DirtyRegionTracker tracker = DirtyRegionTracker.getInstance();
tracker.markDirty(component.getBounds());
if (tracker.needsRedraw(sectionBounds)) {
    section.render(graphics);
}
tracker.clearDirty();

// RenderObjectPool
RenderObjectPool pool = RenderObjectPool.getInstance();
List<String> lines = pool.borrowStringList();
// ... use lines ...
pool.returnStringList(lines);
```