# 2.12 Unified Editor Architecture

> **Architettura confermata:** Un solo `ItemEditorScreen` con moduli per tipo item.

## Filosofia

```
┌─────────────────────────────────────────────────────────────────┐
│  PROBLEMA: Due editor separati = duplicazione bug UI/layout    │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  ✗ ArmorEditorScreen.java  ─┬─→ Bug layout qui                  │
│  ✗ WeaponEditorScreen.java ─┘   Bug layout anche qui            │
│                                                                 │
│  "Shared components" non basta: i bug nascono da LAYOUT e FLOW │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  SOLUZIONE: Un solo ItemEditorScreen con Content Modules       │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  ✓ ItemEditorScreen.java    ← Shell identico sempre            │
│      ├── EditorLayout       ← Unico layout engine               │
│      ├── WeaponModule       ← Fornisce sezioni, non coordinate  │
│      ├── ArmorModule        ← Fornisce sezioni, non coordinate  │
│      └── GeneralModule      ← Future: pozioni, enchant books... │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Regole Architetturali NON NEGOZIABILI

| Regola | Descrizione |
|--------|-------------|
| **R1: Zero coordinate nei moduli** | I moduli NON piazzano widget. Ritornano `List<EditorSection>` |
| **R2: Layout engine unico** | `EditorLayout` renderizza tutto, calcola posizioni |
| **R3: Shell fisso** | Header/Footer/Left column identici per tutti i moduli |
| **R4: Tab auto-select** | Aprendo da armor → tab Armor attivo. Da weapon → tab Weapon |

## Struttura Classi

```java
// Screen principale - UNICO entry point
public class ItemEditorScreen extends Screen {
    private final EditorLayout layout;           // Calcola tutte le posizioni
    private final List<EditorModule> modules;    // Weapon, Armor, General...
    private EditorModule activeModule;           // Modulo corrente

    // Shell components (sempre identici)
    private final HeaderComponent header;
    private final FooterComponent footer;
    private final LeftColumnComponent leftColumn;
    private final ContentArea contentArea;

    public ItemEditorScreen(ItemStack item) {
        // Auto-detect tipo e seleziona modulo
        this.activeModule = detectModule(item);
    }

    @Override
    protected void init() {
        // Layout engine calcola TUTTO
        layout.computePositions(width, height);

        // Shell usa posizioni dal layout
        header.init(layout.getHeaderBounds());
        footer.init(layout.getFooterBounds());
        leftColumn.init(layout.getLeftColumnBounds());

        // Content module usa area dal layout
        contentArea.init(layout.getContentBounds());
        activeModule.init(contentArea);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Shell rendering (identico per tutti)
        header.render(graphics, mouseX, mouseY);
        footer.render(graphics, mouseX, mouseY);
        leftColumn.render(graphics, mouseX, mouseY);

        // Module rendering (delegato al modulo attivo)
        activeModule.render(graphics, contentArea.getBounds(), mouseX, mouseY);
    }
}
```

## EditorModule Interface

```java
/**
 * Interface per i moduli di contenuto.
 * I moduli NON gestiscono coordinate - ritornano solo dati strutturati.
 */
public interface EditorModule {

    /** ID del modulo (weapon, armor, general) */
    String getId();

    /** Titolo mostrato nel tab */
    String getTitle();

    /** Icona del tab */
    ResourceLocation getIcon();

    /** Tabs interni del modulo */
    List<ModuleTab> getTabs();

    /** Inizializza con l'item da editare */
    void setItem(ItemStack item);

    /** Renderizza il contenuto nell'area assegnata */
    void render(GuiGraphics graphics, Bounds contentBounds, int mouseX, int mouseY);

    /** Handle input */
    boolean mouseClicked(double mouseX, double mouseY, int button);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);

    /** Dirty state */
    boolean hasUnsavedChanges();
    void markDirty(String change);
    void clearDirty();

    /** Build payload per server */
    CustomPacketPayload buildPayload();
}
```

## EditorSection - Unità di contenuto

> **Nota sull'implementazione:** Il design iniziale usava `record` per le sezioni, trattandole come semplici contenitori di dati. L'implementazione si è evoluta verso un pattern più robusto e orientato agli oggetti, dove ogni tipo di sezione è un'`interfaccia non-sealed` che estende `EditorSection`. Questo permette a ogni sezione di incapsulare il proprio stato e la propria logica di interazione, portando a un design più manutenibile.

```java
/**
 * Sealed interface per le sezioni di contenuto dell'editor.
 * Ogni sezione definisce un tipo specifico di contenuto editabile e interattivo.
 */
public sealed interface EditorSection permits
    EditorSection.SliderSection,
    EditorSection.ToggleSection,
    EditorSection.InputSection,
    EditorSection.ListSection,
    EditorSection.HeaderSection,
    EditorSection.SpacerSection,
    EditorSection.CustomSection {

    String getId();
    String getLabel();
    int getHeight();

    void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY);

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    // --- TIPI DI SEZIONE ---

    /**
     * Sezione Slider per valori numerici.
     */
    non-sealed interface SliderSection extends EditorSection {
        float getValue();
        void setValue(float value);
    }

    /**
     * Sezione Toggle per valori booleani.
     */
    non-sealed interface ToggleSection extends EditorSection {
        boolean getValue();
        void setValue(boolean value);
    }

    /**
     * Sezione per l'input di testo.
     */
    non-sealed interface InputSection extends EditorSection {
        String getText();
        void setText(String text);
    }
    
    /**
     * Sezione custom per contenuti complessi con logica di rendering propria.
     */
    non-sealed interface CustomSection extends EditorSection {
        // Implementazione custom
    }
}
```

## EditorLayout - Calcola TUTTO

```java
/**
 * Layout engine centralizzato.
 * UNICO posto dove esistono coordinate.
 */
public class EditorLayout {

    // Dimensioni fisse
    public static final int PANEL_WIDTH = 550;
    public static final int PANEL_HEIGHT = 420;
    public static final int HEADER_HEIGHT = 28;
    public static final int FOOTER_HEIGHT = 60;
    public static final int LEFT_COLUMN_WIDTH = 140;

    // Bounds calcolati
    private Bounds panelBounds;
    private Bounds headerBounds;
    private Bounds footerBounds;
    private Bounds leftColumnBounds;
    private Bounds contentBounds;

    public void computePositions(int screenWidth, int screenHeight) {
        // Centro il pannello
        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = (screenHeight - PANEL_HEIGHT) / 2;

        panelBounds = new Bounds(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        headerBounds = new Bounds(
            panelX, panelY,
            PANEL_WIDTH, HEADER_HEIGHT
        );

        footerBounds = new Bounds(
            panelX, panelY + PANEL_HEIGHT - FOOTER_HEIGHT,
            PANEL_WIDTH, FOOTER_HEIGHT
        );

        leftColumnBounds = new Bounds(
            panelX, panelY + HEADER_HEIGHT,
            LEFT_COLUMN_WIDTH, PANEL_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT
        );

        contentBounds = new Bounds(
            panelX + LEFT_COLUMN_WIDTH, panelY + HEADER_HEIGHT,
            PANEL_WIDTH - LEFT_COLUMN_WIDTH, PANEL_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT
        );
    }

    // Getters
    public Bounds getPanelBounds() { return panelBounds; }
    public Bounds getHeaderBounds() { return headerBounds; }
    public Bounds getFooterBounds() { return footerBounds; }
    public Bounds getLeftColumnBounds() { return leftColumnBounds; }
    public Bounds getContentBounds() { return contentBounds; }

    /**
     * Calcola posizioni per una lista di sezioni nel content area.
     */
    public List<SectionBounds> layoutSections(List<EditorSection> sections) {
        List<SectionBounds> result = new ArrayList<>();
        int y = contentBounds.y() + 8; // padding top

        for (EditorSection section : sections) {
            int height = getSectionHeight(section);
            result.add(new SectionBounds(
                section,
                contentBounds.x() + 8,
                y,
                contentBounds.width() - 16,
                height
            ));
            y += height + 4; // gap between sections
        }

        return result;
    }

    private int getSectionHeight(EditorSection section) {
        return switch (section) {
            case EditorSection.SliderSection s -> 24;
            case EditorSection.ToggleSection s -> 20;
            case EditorSection.ListSection s -> Math.min(100, 20 + s.items().size() * 16);
            case EditorSection.SeparatorSection s -> 18;
            case EditorSection.InputSection s -> 22;
        };
    }
}

public record Bounds(int x, int y, int width, int height) {
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}

public record SectionBounds(EditorSection section, int x, int y, int width, int height) {}
```

## Esempio: WeaponModule

```java
public class WeaponModule implements EditorModule {

    private ItemStack weapon;
    private float damage;
    private float speed;
    private boolean isDirty = false;
    private final List<String> pendingChanges = new ArrayList<>();

    @Override
    public String getId() { return "weapon"; }

    @Override
    public String getTitle() { return "Weapon"; }

    @Override
    public List<ModuleTab> getTabs() {
        return List.of(
            new ModuleTab("STATS", this::getStatsSections),
            new ModuleTab("ENCHANTS", this::getEnchantSections),
            new ModuleTab("DURABILITY", this::getDurabilitySections),
            new ModuleTab("DEBUG", this::getDebugSections)
        );
    }

    private List<EditorSection> getStatsSections() {
        return List.of(
            new EditorSection.SeparatorSection("Combat Stats"),
            new EditorSection.SliderSection(
                "Attack Damage",
                0, 50, damage,
                UIConstants.Accent.RED,
                v -> { damage = v; markDirty("damage=" + v); }
            ),
            new EditorSection.SliderSection(
                "Attack Speed",
                0, 4, speed,
                UIConstants.Accent.GREEN,
                v -> { speed = v; markDirty("speed=" + v); }
            )
        );
    }

    // ... altri metodi
}
```

## Auto-Select su Apertura

```java
// In ItemEditorScreen constructor
private EditorModule detectModule(ItemStack item) {
    if (item.getItem() instanceof ArmorItem) {
        return new ArmorModule();
    } else if (item.getItem() instanceof SwordItem ||
               item.getItem() instanceof AxeItem ||
               item.getItem() instanceof TridentItem) {
        return new WeaponModule();
    } else {
        return new GeneralModule(); // fallback
    }
}

// Switch manuale tra moduli (tab click)
private void switchModule(EditorModule newModule) {
    if (activeModule.hasUnsavedChanges()) {
        showConfirmDialog(
            "Switch Module",
            "Unsaved changes will be lost. Continue?",
            () -> doSwitch(newModule),
            () -> {}
        );
    } else {
        doSwitch(newModule);
    }
}
```

## Vantaggi dell'Architettura Unificata

| Aspetto | Prima (2 editor) | Dopo (1 editor + moduli) |
|---------|------------------|--------------------------|
| **Bug layout** | Fix in 2 posti | Fix in 1 posto |
| **Nuovi tipi item** | Nuovo Screen | Nuovo Module (~100 righe) |
| **Testing** | 2 screen da testare | 1 screen, moduli isolati |
| **Coerenza UI** | Dipende da disciplina | Garantita da architettura |
| **Coordinate** | Sparse ovunque | Solo in EditorLayout |

## Migration Path Dettagliato

La migrazione dai vecchi editor (`WeaponEditorScreen`, `ArmorEditorScreen`) all'architettura unificata segue un processo strutturato per minimizzare i rischi e garantire la coerenza.

### FASE 1: Creazione della Struttura Base (Shell)

1.  **Creare `EditorLayout.java`**: Implementare la classe che calcola le coordinate di tutti i pannelli principali (header, footer, left column, content).
2.  **Creare `EditorModule.java`**: Definire l'interfaccia base per tutti i moduli di contenuto.
3.  **Creare `EditorSection.java`**: Definire l'interfaccia `sealed` e le varie sotto-interfacce per i tipi di contenuto (Slider, Toggle, etc.).
4.  **Creare `ItemEditorScreen.java`**: Implementare lo "shell" dell'editor, che contiene i componenti comuni e un'area per il modulo attivo, ma senza logica di business specifica.

### FASE 2: Migrazione del Primo Editor (es. WeaponEditor)

Questa fase è la più critica. L'obiettivo è estrarre tutta la logica di business e di UI dal vecchio `WeaponEditorScreen` e incapsularla in un nuovo `WeaponModule`.

#### Esempio: Da `OldWeaponEditor` a `WeaponModule`

**PRIMA: Logica sparsa nel vecchio editor**

```java
// In OldWeaponEditorScreen.java
public class OldWeaponEditorScreen extends Screen {
    private Slider attackDamageSlider;
    private Slider attackSpeedSlider;
    private Checkbox fireDamageToggle;

    @Override
    protected void init() {
        // Logica di layout manuale e accoppiata
        this.attackDamageSlider = new Slider(this.width / 2, 40, ...);
        this.attackSpeedSlider = new Slider(this.width / 2, 65, ...);
        this.fireDamageToggle = new Checkbox(this.width / 2, 90, ...);
        
        this.addRenderableWidget(attackDamageSlider);
        this.addRenderableWidget(attackSpeedSlider);
        this.addRenderableWidget(fireDamageToggle);
    }
}
```

**DOPO: Logica incapsulata nel `WeaponModule`**

Il `WeaponModule` non si occupa più del layout. La sua unica responsabilità è dichiarare *quali* sezioni di UI devono esistere.

```java
// In WeaponModule.java
public class WeaponModule implements EditorModule {
    private WeaponStats stats;

    @Override
    public List<EditorSection> getSections() {
        // Il modulo ritorna una lista di "dati" di sezione.
        // NON calcola coordinate.
        return List.of(
            new SeparatorSection("Combat Stats"),
            new SliderSection(
                "Attack Damage",
                stats.attackDamage,
                newValue -> stats.attackDamage = newValue
            ),
            new SliderSection(
                "Attack Speed",
                stats.attackSpeed,
                newValue -> stats.attackSpeed = newValue
            ),
            new SeparatorSection("Damage Types"),
            new ToggleSection(
                "Fire Damage",
                stats.hasFireDamage,
                newValue -> stats.hasFireDamage = newValue
            )
        );
    }
}
```

Sarà poi `ItemEditorScreen`, tramite `EditorLayout` e l'area di contenuto scrollabile, a renderizzare queste sezioni nel posto giusto.

### FASE 3: Migrazione degli Altri Editor (es. ArmorEditor)

Il processo viene ripetuto per `ArmorEditorScreen`, creando un `ArmorModule`. Grazie all'esperienza della Fase 2, questa fase è molto più rapida. La logica viene estratta in modo simile, mappando i vecchi componenti UI alle `EditorSection` appropriate.

### FASE 4: Pulizia e Rimozione del Vecchio Codice

1.  **Eliminare `WeaponEditorScreen.java` e `ArmorEditorScreen.java`**: Una volta che tutti i moduli sono stati migrati e testati.
2.  **Aggiornare i Punti di Ingresso**: Assicurarsi che tutte le parti del gioco che aprivano i vecchi editor ora aprano `ItemEditorScreen` con il modulo corretto.
3.  **Rimuovere Vecchi Componenti UI**: Se c'erano componenti UI custom usati solo dai vecchi editor, possono essere rimossi.

## Error Handling e Recovery Strategies

L'architettura adotta diverse strategie per gestire gli errori in modo robusto, prevenendo crash e fornendo feedback all'utente e allo sviluppatore.

### 1. Gestione Controllata delle Eccezioni

Per operazioni non critiche (es. aggiornamento di parti della UI, lettura di metadati), le eccezioni vengono catturate per evitare il crash dell'intera schermata. Questo è spesso usato per funzionalità "best-effort" dove un fallimento non compromette l'operazione principale.

**Pattern Comune:**
```java
// In ItemEditorScreen.java (o componenti simili)
try {
    // Operazione non critica, es. aggiornare una statistica nella UI
    updateLeftColumnStats();
} catch (Exception ignored) {
    // Il fallimento non è fatale. La UI potrebbe non essere aggiornata,
    // ma l'editor rimane utilizzabile. L'errore viene ignorato intenzionalmente.
}
```

### 2. Feedback Utente Tramite Messaggi di Stato

Quando un'operazione avviata dall'utente fallisce (es. import/export, applicazione di preset), l'errore viene comunicato tramite un messaggio di stato non invasivo nella UI.

**Esempio:**
```java
// In ItemEditorScreen.java
try {
    ItemEditorDataManager.ItemConfigExport imported = data.importFromFile(fileName);
    applyImportedStats(imported, "Imported " + fileName);
    showStatus("Imported " + fileName, UIConstants.Accent.BLUE);
} catch (Exception e) {
    // Comunica l'errore all'utente senza crashare
    showStatus("Import failed: " + e.getMessage(), UIConstants.Accent.RED);
}
```

### 3. Dialoghi di Conferma per Prevenire Errori Utente

Per azioni potenzialmente distruttive o che comportano perdita di dati (es. chiudere con modifiche non salvate), vengono usati dialoghi di conferma modali. Questo previene errori dell'utente.

**Esempio:**
```java
// In ItemEditorScreen.java
private void handleCloseRequest() {
    if (!isPreviewMode && activeModule != null && activeModule.hasUnsavedChanges()) {
        activeDialog = ConfirmDialog.unsavedChanges(
            activeModule.getPendingChanges().size(),
            this::onClose, // Azione se l'utente conferma
            () -> {}       // Azione se l'utente annulla
        );
        activeDialog.show();
        return;
    }
    onClose();
}
```

### 4. Logging per Sviluppatori

Le eccezioni catturate, anche se ignorate a livello di UI, vengono spesso loggate per consentire il debug post-mortem. Questo avviene sia tramite il `DebugPanel` interno che tramite il logger principale del mod.

**Esempio:**
```java
// In MultiEditManager.java
try {
    // ... logica di multi-edit ...
} catch (Exception e) {
    // Logga l'errore per il debug
    DevMod.LOGGER.warn("Multi-edit item application failed: {}", e.getMessage());
    failures.add("Error: " + e.getMessage());
}
```

## Memory Management per Moduli

La gestione della memoria si basa sul ciclo di vita dei componenti e sull'affidamento al garbage collector (GC) di Java, piuttosto che sulla deallocazione manuale. La strategia chiave è assicurarsi che gli oggetti non più necessari non abbiano riferimenti attivi, permettendo al GC di liberare la memoria.

### Ciclo di Vita e `onClose()`

Il metodo `onClose()` è il punto di ingresso principale per il cleanup. Quando `ItemEditorScreen` viene chiuso, invoca a sua volta il metodo `onClose()` del modulo attualmente attivo.

```java
// In ItemEditorScreen.java
@Override
public void onClose() {
    if (activeModule != null) {
        // Delega il cleanup al modulo attivo
        activeModule.onClose();
    }
    super.onClose();
}
```

Questo pattern a cascata assicura che ogni modulo possa eseguire la propria logica di pulizia.

### Pulizia dello Stato Interno

I moduli e i loro componenti interni sono responsabili di pulire il proprio stato, in particolare le collezioni che potrebbero mantenere riferimenti a oggetti.

**Strategie Comuni:**

1.  **Svuotare le Collezioni**: Metodi come `clear()` vengono usati su `List`, `Map`, etc., per rimuovere tutti gli elementi. Questo è fondamentale per le cronologie (undo/redo), le liste di modifiche (`DirtyState`) e le selezioni temporanee.
2.  **Null-out dei Riferimenti**: Anche se non sempre necessario grazie al GC, annullare i riferimenti a oggetti pesanti o a listener può aiutare a prevenire memory leak, specialmente se questi oggetti hanno un ciclo di vita complesso.

**Esempio in `AbstractEditorModule`:**

La classe base per i moduli implementa metodi per pulire lo stato.

```java
// In AbstractEditorModule.java
public void clearDirty() {
    pendingChanges.clear(); // Svuota la lista delle modifiche
}

public void clearHistory() {
    historyEntries.clear(); // Svuota la cronologia
    undoStack.clear();      // Svuota lo stack di undo
    redoStack.clear();
}

@Override
public void onClose() {
    // Hook per le sottoclassi per pulire risorse specifiche.
    // Es. listener, sottoscrizioni a eventi, etc.
}
```

In sintesi, l'architettura non reinventa la gestione della memoria, ma si integra correttamente con il ciclo di vita del garbage collector di Java, fornendo gli hook (`onClose`) e le pratiche (`clear()`) necessarie per una gestione pulita dello stato dei moduli.