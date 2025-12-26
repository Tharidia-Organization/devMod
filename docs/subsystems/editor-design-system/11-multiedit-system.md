# 2.14 Selection Mode: Single + MultiEdit

> **Decisione strutturale (stato attuale):** MultiEdit è parte della UI. L'entry point primario rimane l'editing single-item, mentre il pannello `MultiEdit` fornisce selezione, visualizzazione e azioni batch (apply-to-all / presets). La selezione viene popolata automaticamente premendo **M** che scansiona l'inventario per item dello stesso tipo. Questa sezione documenta il comportamento reale del codice.

## Razionale

| Fase | Caso d'uso principale | Modalità |
|------|-----------------------|----------|
| DEBUG | "Questo item non funziona" | Single di default; MultiEdit se devo applicare la stessa fix a più copie |
| BALANCE | "Questo valore è sbilanciato" | Single + apply preset/valori alla selezione |
| CONTENT | "Applica preset a 20 item" | MultiEdit (apply-to-all) |

## Decisione

```
┌─────────────────────────────────────────────────────────────────┐
│  FASE ATTUALE: Single + MultiEdit (ufficiale)                   │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  ✓ Default: un item focus con undo/redo + dirty state           │
│  ✓ MultiEditManager + MultiEditPanel per lista selezionati      │
│  ✓ [Apply to All] + [Clear All] usando EditorButton component   │
│  ✓ Preset selector (dropdown) con scoping per itemType          │
│  ✓ BatchEditResult: success/failure count + dettagli + Copy     │
│  ✓ Shortcut M: refresh selezione + espande pannello             │
│  ✓ Virtualizzazione lista selezione (max 10 visibili + scroll)  │
│  ✓ Dropdown preset con scrolling (max 8 visibili)               │
│  ✓ Failure list scrollabile separata (windowed, max 6/20 righe) │
│  ✓ Export failures to file + Copy to clipboard                  │
│  ✓ DataPreset wrapper + ItemEditorPresetManager                 │
│  ✓ Preview mode toggle (applica senza persistere)               │
│  ✓ Batch undo via Ctrl+Z (snapshot pre-apply)                   │
│  ✓ Slot validation prima di apply/persist                       │
│  ✓ Timeout (5s) e cancellation per batch lunghi                 │
│  ✓ Conferma dialog per batch >10 item                           │
│  ✓ Timing info per batch ≥20 item                               │
│  ✓ Progress indicator real-time durante batch apply             │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  COMPLETATO - Nessun debt UX rimanente                          │
└─────────────────────────────────────────────────────────────────┘
```

## Implicazioni Architetturali

```java
// ItemEditorScreen - focus singolo, MultiEdit come subsystem
public class ItemEditorScreen extends Screen {
    private final ItemStack item;              // Item principale in editing
    private MultiEditManager multiEditManager; // Selezione multipla + batch apply
    private MultiEditPanel multiEditPanel;     // UI panel per selezione
    private boolean showMultiEditPanel = false;

    // Shortcut M attiva/refresh il pannello
    if (keyCode == GLFW.GLFW_KEY_M) {
        showMultiEditPanel = !showMultiEditPanel;
        if (showMultiEditPanel) {
            refreshMultiEditSelection(); // Scansiona inventario
            multiEditPanel.setExpanded(true);
        }
    }
}

// EditorModule - continua a lavorare su singolo item
public interface EditorModule {
    void setItem(ItemStack item);  // Singolo
    // MultiEditPanel applica preset via PresetManager, non tramite EditorModule
}

// Preset abstraction per MultiEdit
public interface Preset {
    Predicate<ItemStack> scope();  // Filtra item applicabili
}

// PresetManager facade per applicazione preset
public interface PresetManager {
    boolean applyPreset(Preset preset, ItemStack item, int slotIndex);
}
```

## Implementazione Attuale

### MultiEditPanel UI

Il pannello viene mostrato/nascosto con **M** e si posiziona sotto la colonna sinistra dell'editor.

```
┌─────────────────────────────────────────────────────────────────┐
│  3 items selected                                         ▼     │ ← Header (click to collapse)
├─────────────────────────────────────────────────────────────────┤
│  Preset (sword)   [Diamond Sword Preset]                  ▼     │ ← Dropdown con scroll
├─────────────────────────────────────────────────────────────────┤
│  ▪ Diamond Sword                                          ✗     │ ← Lista virtualizzata
│  ▪ Iron Sword                                             ✗     │   (max 10 visibili)
│  ▪ Golden Sword                                           ✗     │
├─────────────────────────────────────────────────────────────────┤
│  [Preview only] (will not persist)                              │ ← Toggle per dry-run
│  [Clear All]                              [Apply to all]        │ ← EditorButton components
├─────────────────────────────────────────────────────────────────┤
│  ████████████░░░░░░░  Applying... 15/25                         │ ← Progress bar (during apply)
├─────────────────────────────────────────────────────────────────┤
│  Last apply: 2 successes, 1 failed (23 items in 45ms)           │ ← Risultato + timing
│  [Export][Copy][Details]                                        │ ← Failure actions
│  • slot#5 minecraft:wooden_sword - Scope mismatch               │ ← Failure details (scroll)
└─────────────────────────────────────────────────────────────────┘
```

### Flusso Operativo

1. **Apertura**: Premere **M** in ItemEditorScreen
2. **Selezione automatica**: `refreshMultiEditSelection()` scansiona inventario per item dello stesso tipo
3. **Scegliere preset**: Dropdown filtra preset per `itemType` attivo
4. **[Opzionale]** Attivare "Preview only" per dry-run senza persistenza
5. **Applicare**: Click "Apply to all" → `MultiEditManager.applyPresetToAll()`
   - Se >10 item: mostra conferma dialog
   - Progress bar real-time durante l'operazione (Applying... X/Y)
   - Timeout automatico dopo 5 secondi
6. **Risultato**: `BatchEditResult` con success/failure count + dettagli strutturati
   - Per batch ≥20 item: mostra timing info
7. **[Opzionale]** Se errore: **Ctrl+Z** per batch undo (ripristina snapshot)

### Keyboard Shortcuts

| Shortcut | Azione |
|----------|--------|
| `M` | Toggle MultiEdit panel + refresh selezione |
| `Ctrl+Z` | Undo ultimo batch (se snapshot disponibile) |
| `Esc` | Chiudi panel / annulla operazione |
| `↑↓` | Naviga preset dropdown |
| `Enter` | Applica preset selezionato |

### BatchEditResult

```java
public class BatchEditResult {
    public static class FailureDetail {
        public final String itemName;
        public final String itemId;     // Registry id (minecraft:diamond_sword)
        public final int slot;          // Inventory slot
        public final String message;    // Error message
        public final String stackTrace; // Optional stack trace
    }

    public int successCount();
    public int failureCount();
    public List<FailureDetail> failureDetails();
    public String generateErrorReport();  // Clipboard-friendly format
}
```

## Cosa NON implementare ora

| Feature | Motivo esclusione |
|---------|-------------------|
| Nuova BatchEditorScreen separata | Riutilizziamo ItemEditorScreen + MultiEditPanel |
| Flussi di multi-selezione estesi (inventory scan massivo) | Scope creep, UI non definita |
| Conflict resolution avanzato | Fuori scope P1, bastano failure list + log |
| Multi-level undo stack | Singolo snapshot sufficiente per uso attuale |

## FUTURE: Batch Edit Architecture (Avanzata/Deferred)

> **NOTA:** Se servirà scalare oltre il pannello MultiEdit attuale, questa è la base architetturale. Non sostituisce il flusso corrente.

### Entry Point

```java
// Stesso pattern di ItemEditorScreen (FUTURO - oggi MultiEditPanel con scroll + shortcut M)
public class BatchEditorScreen extends Screen {
    private final List<ItemStack> items;
    private final EditorStartTab startTab;
    private final BatchEditMode mode;

    public BatchEditorScreen(List<ItemStack> items, EditorStartTab startTab) {
        this.items = List.copyOf(items); // Immutabile
        this.startTab = startTab;
        this.mode = BatchEditMode.PREVIEW; // Default sicuro
    }
}
```

### BatchEditMode Enum

```java
public enum BatchEditMode {
    PREVIEW,        // Mostra cosa cambierebbe, non applica
    APPLY_TO_ALL,   // Applica stesso valore a tutti gli item
    APPLY_MATCHING; // Applica solo agli item che matchano un criterio
}
```

### Gestione Valori Multipli

```java
/**
 * Rappresenta un valore che può essere uniforme o misto tra item.
 */
public sealed interface BatchValue<T> {

    /** Tutti gli item hanno lo stesso valore */
    record Uniform<T>(T value) implements BatchValue<T> {}

    /** Item hanno valori diversi */
    record Mixed<T>(T min, T max, T average, Map<T, Integer> distribution) implements BatchValue<T> {}

    /** Alcuni item non hanno questo attributo */
    record Partial<T>(T value, int presentCount, int totalCount) implements BatchValue<T> {}
}

// Esempio uso
BatchValue<Float> damage = analyzeAttribute(items, "attack_damage");
// → Uniform(7.0) se tutti hanno 7.0
// → Mixed(5.0, 12.0, 8.5, {5.0→3, 7.0→5, 12.0→2}) se diversi
```

### UI per Valori Misti

```
┌─────────────────────────────────────────────────────────────────┐
│  BATCH EDIT - 10 items selected                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Attack Damage                                                  │
│  [✓] ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░○  [MIXED: 5-12]            │
│      ↑                                                          │
│      Checkbox: applica questo campo                             │
│                                                                 │
│  Attack Speed                                                   │
│  [ ] ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓○  [UNIFORM: 1.6]           │
│      ↑                                                          │
│      Non selezionato: non modifica                              │
│                                                                 │
│  Durability                                                     │
│  [✓] ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░○  [SET: 1000]              │
│      ↑                                                          │
│      Selezionato con nuovo valore                               │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  Changes: 2 fields × 10 items = 20 modifications                │
│                                                                 │
│  [Preview Changes]  [Apply to All]  [Cancel]                    │
└─────────────────────────────────────────────────────────────────┘
```

### Conflict Resolution Strategy

```java
public enum ConflictStrategy {
    OVERWRITE_ALL,      // Ignora valori esistenti, applica nuovo
    KEEP_HIGHER,        // Mantieni il valore più alto tra esistente e nuovo
    KEEP_LOWER,         // Mantieni il valore più basso
    APPLY_DELTA,        // Applica differenza (+5, -10%, etc.)
    SKIP_IF_DIFFERENT;  // Non modificare se già diverso da target
}
```

### Batch Payload

```java
public record BatchUpdatePayload(
    List<ItemReference> targets,      // Quali item modificare
    EditorStartTab moduleType,        // WEAPON, ARMOR, GENERAL
    Map<String, Object> newValues,    // Attributo → nuovo valore
    ConflictStrategy strategy,        // Come gestire conflitti
    boolean isPreview                 // true = dry run, false = applica
) implements CustomPacketPayload {

    public static final Type<BatchUpdatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "batch_update")
    );
}

// Riferimento a un item (non ItemStack diretto per network)
public record ItemReference(
    int entityId,       // Player entity ID
    int slotIndex,      // Slot nell'inventario
    String itemId       // Per validazione "minecraft:diamond_sword"
) {}
```

### Batch Undo/Redo

```java
public record BatchUndoState(
    List<ItemReference> targets,
    Map<ItemReference, Map<String, Object>> previousValues, // Stato pre-modifica per item
    Map<String, Object> appliedValues,                       // Cosa è stato applicato
    long timestamp
) {}

// Stack separato per batch operations
private final Deque<BatchUndoState> batchUndoStack = new ArrayDeque<>(20);
```

### Radial Menu Entry (FUTURE)

```java
// Voce "Batch Edit" - visibile solo se multiple item selezionabili
// Richiede UI di selezione inventory (non implementare ora)
RadialMenuRegistry.register(new RadialMenuItem(
    "batch_edit",
    Component.translatable("devmod.radial.batch_edit"),
    BATCH_ICON,
    (player) -> {
        // Apre inventory selector, poi BatchEditorScreen
        Minecraft.getInstance().setScreen(new BatchSelectionScreen());
    },
    // Sempre visibile quando batch edit è abilitato
    (player) -> Config.CLIENT.enableBatchEdit.get()
));
```

### Nota su batch editor legacy (DEFERRED)
- Il flusso "BatchEditorScreen / BatchSelectionScreen" resta documentato solo come idea futura: l'implementazione attuale usa il pannello MultiEdit dentro `ItemEditorScreen` (dropdown preset + apply-to-all). Non introdurre nuove screen batch in questa fase.

### File Structure (attuale + futuro deferito)

```
src/main/java/com/frenkvs/devmod/ui/editor/
├── ItemEditorScreen.java          ← Entry point singolo + pannello MultiEdit
├── systems/
│   ├── MultiEditManager.java      ← Stato selezione, apply batch, timeout, cancel, ProgressCallback
│   ├── MultiEditPanel.java        ← UI dropdown preset + progress bar + summary/copy (usa EditorButton)
│   ├── BatchEditResult.java       ← Risultato operazione batch con FailureDetail
│   ├── BatchUndoSnapshot.java     ← Snapshot per batch undo (deep copy ItemStacks)
│   ├── ConfirmDialog.java         ← Dialog conferma (include batchApply factory)
│   ├── Preset.java                ← Interface con scope() predicate
│   ├── PresetManager.java         ← Interface per applicazione preset
│   ├── DataPreset.java            ← Wrapper per ItemEditorDataManager.PresetData
│   └── ItemEditorPresetManager.java ← Implementazione PresetManager
├── BatchEditorScreen.java         ← FUTURE/DEFERRED (non implementare ora)
├── BatchSelectionScreen.java      ← FUTURE/DEFERRED (non implementare ora)
└── ...
```

### Stima Effort

| Componente | Linee | Complessità |
|------------|-------|-------------|
| BatchEditorScreen | ~400 | Alta |
| BatchValue + UI | ~200 | Media |
| BatchUpdatePayload + handler | ~150 | Media |
| BatchSelectionScreen | ~250 | Media |
| Batch undo/redo | ~100 | Media |
| **Totale** | **~1100** | **Alta** |

**Priorità:** P3 - Implementare DOPO stabilizzazione single editor (Fase 0-4 complete).
