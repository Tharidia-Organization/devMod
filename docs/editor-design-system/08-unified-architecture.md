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

L'interfaccia completa implementata in `EditorModule.java`:

```java
/**
 * Interface per i moduli di contenuto.
 * I moduli NON gestiscono coordinate - ritornano solo dati strutturati.
 */
public interface EditorModule {

    // ═══════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /** ID del modulo (weapon, armor, general) */
    String getId();

    /** Titolo mostrato nel tab */
    String getTitle();

    /** Icona del tab (opzionale) */
    default ResourceLocation getIcon() { return null; }

    // ═══════════════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════════════

    /** Tabs interni del modulo */
    List<ModuleTab> getTabs();

    /** Indice del tab attivo */
    int getActiveTabIndex();

    /** Imposta il tab attivo */
    void setActiveTab(int index);

    // ═══════════════════════════════════════════════════════════════
    // CONTENT
    // ═══════════════════════════════════════════════════════════════

    /** Inizializza con l'item da editare */
    void setItem(ItemStack item);

    /** Ottieni l'item corrente */
    ItemStack getItem();

    /** Inizializza il modulo con le info di layout */
    void init(ResponsiveLayout layout);

    /** Ottieni le sezioni per il tab corrente */
    List<EditorSection> getSections();

    /** Renderizza il contenuto nell'area assegnata */
    void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int mouseX, int mouseY);

    /** Calcola l'altezza totale del contenuto per lo scroll */
    int calculateContentHeight();

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    boolean mouseClicked(double mouseX, double mouseY, int button);
    boolean mouseReleased(double mouseX, double mouseY, int button);
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    boolean charTyped(char chr, int modifiers);

    // ═══════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    /** Verifica se ci sono modifiche non salvate */
    boolean hasUnsavedChanges();

    /** Lista delle modifiche pendenti (per dirty indicator) */
    List<String> getPendingChanges();

    /** Marca una modifica come pendente */
    void markDirty(String changeDescription);

    /** Pulisci lo stato dirty (dopo il salvataggio) */
    void clearDirty();

    /** Consumer per messaggi di stato */
    default void setStatusConsumer(BiConsumer<String, Integer> statusConsumer) {}

    /** Ottieni le entry della cronologia */
    List<String> getHistoryEntries();

    /** Pulisci la cronologia */
    void clearHistory();

    /** Logga un evento nella timeline (per debug/session log) */
    default void logEvent(String description) {}

    /** Abilita/disabilita il tracking dirty (es. preview mode) */
    default void setDirtyTrackingEnabled(boolean enabled) {}

    /** Rileva se lo stato attuale differisce dall'originale */
    default boolean hasPendingDiff() { return hasUnsavedChanges(); }

    /** Fornisce una copia preview dell'item (opzionale) */
    default ItemStack getPreviewItem() { return null; }

    /** Pulisci lo stato di preview (opzionale) */
    default void clearPreview() {}

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    /** Build payload per sync con il server */
    CustomPacketPayload buildPayload(boolean isGlobal);

    /** Applica le modifiche localmente (preview mode) */
    void applyPreview();

    /** Resetta ai valori originali */
    void resetToOriginal();

    // ═══════════════════════════════════════════════════════════════
    // UNDO/REDO
    // ═══════════════════════════════════════════════════════════════

    boolean canUndo();
    boolean canRedo();
    void undo();
    void redo();
    void saveUndoState();

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /** Chiamato quando il modulo viene chiuso */
    default void onClose() {}

    /** Chiamato ogni tick */
    default void tick() {}
}
```

## AbstractEditorModule - Classe Base

La classe `AbstractEditorModule` implementa la logica comune per tutti i moduli:

```java
/**
 * Classe base astratta per i moduli dell'editor.
 * Fornisce funzionalità comuni per tab management, undo/redo e dirty tracking.
 */
public abstract class AbstractEditorModule implements EditorModule {

    protected final String id;
    protected final String title;
    protected ItemStack item = ItemStack.EMPTY;
    protected ItemStack originalItem = ItemStack.EMPTY;
    protected ResponsiveLayout layout;

    // Tabs
    protected final List<ModuleTab> tabs = new ArrayList<>();
    protected int activeTabIndex = 0;

    // Dirty tracking
    protected final List<String> pendingChanges = new ArrayList<>();
    protected final List<String> historyEntries = new ArrayList<>();

    // Undo/Redo stacks
    protected final Stack<UndoState> undoStack = new Stack<>();
    protected final Stack<UndoState> redoStack = new Stack<>();
    private static final int MAX_UNDO_STATES = 50;

    // UndoState record
    protected record UndoState(String description, byte[] itemData, int tabIndex) {}

    // Metodi astratti che le sottoclassi devono implementare:
    protected abstract void onItemSet();
    protected abstract void initializeTabs();
}
```

**Moduli che estendono AbstractEditorModule:**
- `WeaponModule` - Statistiche armi (damage, speed, reach, etc.)
- `ArmorModule` - Statistiche armature (resistenze, bonus, etc.)
- `RangedModule` - Armi a distanza (archi, balestre)
- `GeneralModule` - Proprietà generali (durabilità, enchantments)

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

    // Input handling con defaults
    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    default boolean charTyped(char chr, int modifiers) { return false; }

    // --- TIPI DI SEZIONE ---

    /**
     * Sezione Slider per valori numerici.
     */
    non-sealed interface SliderSection extends EditorSection {
        float getValue();
        void setValue(float value);
        float getMin();
        float getMax();
        float getStep();
        String getFormat();
        int getColor();
        boolean isDragging();
        void setDragging(boolean dragging);
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
        String getPlaceholder();
        boolean isNumeric();
    }

    /**
     * Sezione per selezione da lista.
     */
    non-sealed interface ListSection extends EditorSection {
        List<String> getOptions();
        int getSelectedIndex();
        void setSelectedIndex(int index);
    }

    /**
     * Sezione header/titolo per raggruppamento.
     */
    non-sealed interface HeaderSection extends EditorSection {
        boolean isCollapsible();
        boolean isCollapsed();
        void setCollapsed(boolean collapsed);
    }

    /**
     * Spacer per layout purposes.
     */
    non-sealed interface SpacerSection extends EditorSection {
        @Override
        default String getLabel() { return ""; }

        @Override
        default void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            // Spacers don't render anything
        }
    }

    /**
     * Sezione custom per contenuti complessi con logica di rendering propria.
     */
    non-sealed interface CustomSection extends EditorSection {
        // Custom sections implement their own rendering logic
    }
}
```

## EditorLayout - Calcola TUTTO

```java
/**
 * Layout engine centralizzato.
 * UNICO posto dove esistono coordinate.
 * Usa UIConstants per dimensioni base e ScaledCoord per scaling dinamico.
 */
public class EditorLayout {

    // Bounds calcolati (inizializzati a EMPTY)
    private Bounds panelBounds = Bounds.EMPTY;
    private Bounds headerBounds = Bounds.EMPTY;
    private Bounds footerBounds = Bounds.EMPTY;
    private Bounds leftColumnBounds = Bounds.EMPTY;
    private Bounds contentBounds = Bounds.EMPTY;

    // Cache per bounds delle sezioni per colonna
    private final Map<String, List<SectionBounds>> columnSections = new HashMap<>();

    /**
     * Calcola tutte le posizioni dato lo screen size e la scala UI.
     * Usa EditorScaleCalculator per determinare dimensioni pannello clamped.
     */
    public void computePositions(int screenWidth, int screenHeight) {
        computePositions(screenWidth, screenHeight, ScaledCoord.getScale());
    }

    public void computePositions(int screenWidth, int screenHeight, float scale) {
        EditorScaleCalculator.ScreenFitResult fit = EditorScaleCalculator.calculateFit(screenWidth, screenHeight, scale);
        computePositions(fit, scale);
    }

    public void computePositions(EditorScaleCalculator.ScreenFitResult fit, float scale) {
        columnSections.clear();

        int panelWidth = fit.panelWidth();
        int panelHeight = fit.panelHeight();
        int hotbarReserve = ScaledCoord.scaleDim(24, scale); // spazio per hotbar vanilla
        int headerHeight = ScaledCoord.scaleDim(UIConstants.Size.HEADER_HEIGHT, scale);
        int footerHeight = ScaledCoord.scaleDim(UIConstants.Size.FOOTER_HEIGHT, scale);
        int leftWidth = ScaledCoord.scaleDim(UIConstants.PanelDimensions.LEFT_COLUMN_WIDTH, scale);

        int panelX = fit.panelX();
        int panelY = fit.panelY();

        // Garantisci altezza minima per header/footer/content
        int minHeight = headerHeight + footerHeight + ScaledCoord.scaleDim(32, scale);
        panelHeight = Math.max(panelHeight - hotbarReserve, minHeight);

        panelBounds = new Bounds(panelX, panelY, panelWidth, panelHeight);
        headerBounds = new Bounds(panelX, panelY, panelWidth, headerHeight);
        footerBounds = new Bounds(panelX, panelY + panelHeight - footerHeight, panelWidth, footerHeight);
        leftColumnBounds = new Bounds(panelX, panelY + headerHeight, leftWidth,
            panelHeight - headerHeight - footerHeight);
        contentBounds = new Bounds(panelX + leftWidth, panelY + headerHeight,
            panelWidth - leftWidth, panelHeight - headerHeight - footerHeight);

        // Validazione allineamento griglia 4px
        validateBounds(panelBounds, "panelBounds");
        validateBounds(headerBounds, "headerBounds");
        validateBounds(footerBounds, "footerBounds");
        validateBounds(leftColumnBounds, "leftColumnBounds");
        validateBounds(contentBounds, "contentBounds");
    }

    /**
     * Layout delle sezioni in una colonna specifica.
     */
    public void layoutSectionsInColumn(String columnId, List<EditorSection> sections,
                                        Bounds area, int padding, int gap) {
        List<SectionBounds> boundsList = new ArrayList<>();
        int currentY = area.y() + padding;
        int sectionWidth = area.width() - (2 * padding);

        for (EditorSection section : sections) {
            int sectionHeight = section.getHeight();
            Bounds sectionArea = new Bounds(area.x() + padding, currentY, sectionWidth, sectionHeight);
            boundsList.add(new SectionBounds(section.getId(), sectionArea));
            currentY += sectionHeight + gap;
        }

        columnSections.put(columnId, boundsList);
    }

    // Getters
    public Bounds getPanelBounds() { return panelBounds; }
    public Bounds getHeaderBounds() { return headerBounds; }
    public Bounds getFooterBounds() { return footerBounds; }
    public Bounds getLeftColumnBounds() { return leftColumnBounds; }
    public Bounds getContentBounds() { return contentBounds; }

    public Bounds getSectionBounds(String columnId, String sectionId) { ... }
    public List<SectionBounds> getSectionsForColumn(String columnId) { ... }

    private void validateBounds(Bounds bounds, String context) {
        // Valida allineamento alla griglia 4px
        if (bounds.x() % 4 != 0 || bounds.y() % 4 != 0 ||
            bounds.width() % 4 != 0 || bounds.height() % 4 != 0) {
            System.err.println("Layout Validation Warning: " + context + " not aligned to 4px grid.");
        }
    }
}
```

## Bounds e SectionBounds Records

```java
/**
 * Rectangle bounds con metodi helper.
 */
public record Bounds(int x, int y, int width, int height) {
    public static final Bounds EMPTY = new Bounds(0, 0, 0, 0);

    public int right() { return x + width; }
    public int bottom() { return y + height; }

    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}

/**
 * Bounds associati a una sezione specifica.
 */
public record SectionBounds(String sectionId, Bounds bounds) {}
```

## Esempio: WeaponModule

L'implementazione reale estende `AbstractEditorModule`:

```java
public class WeaponModule extends AbstractEditorModule {

    private WeaponStats stats = new WeaponStats();
    private WeaponStats originalStats = new WeaponStats();

    // UI Components - creati come campi per gestire stato
    private EditorSlider attackDamageSlider;
    private EditorSlider attackSpeedSlider;
    private EditorSlider attackReachSlider;
    // ... altri slider

    public WeaponModule() {
        super("weapon", "Weapon");
    }

    @Override
    protected void onItemSet() {
        // Carica stats dall'item (NBT o global config)
        stats = loadWeaponStats(item);
        originalStats = stats.copy();
        initializeSliders();
    }

    @Override
    protected void initializeTabs() {
        addTab(new ModuleTab("STATS", "Hit Location", this::getHitLocationSections));
        addTab(new ModuleTab("COMBAT", "Combat", this::getCombatSections));
        addTab(new ModuleTab("EFFECTS", "Effects", this::getEffectsSections));
        addTab(new ModuleTab("DEBUG", "Debug", this::getDebugSections));
    }

    private List<EditorSection> getHitLocationSections() {
        return List.of(
            createHeaderSection("Hit Location Multipliers"),
            wrapSlider(headMultSlider),
            wrapSlider(bodyMultSlider),
            wrapSlider(armsMultSlider),
            wrapSlider(legsMultSlider)
        );
    }

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        return new WeaponStatsPayload(item, stats, isGlobal);
    }

    // ... altri metodi
}
```

## Auto-Select su Apertura

L'`ItemEditorScreen` usa `resolveModule()` per selezionare il modulo in base al tipo di item e al tab richiesto:

```java
// In ItemEditorScreen
private EditorModule resolveModule(ItemStack item, EditorStartTab requestedTab) {
    // Se il tab è esplicito, usa quello
    if (requestedTab == EditorStartTab.WEAPON) {
        return new WeaponModule();
    } else if (requestedTab == EditorStartTab.ARMOR) {
        return new ArmorModule();
    } else if (requestedTab == EditorStartTab.RANGED) {
        return new RangedModule();
    }

    // Auto-detect basato sul tipo di item
    if (item.getItem() instanceof ArmorItem) {
        return new ArmorModule();
    } else if (isRangedWeapon(item)) {
        return new RangedModule();
    } else if (item.getItem() instanceof SwordItem ||
               item.getItem() instanceof AxeItem ||
               item.getItem() instanceof TridentItem ||
               item.getItem() instanceof MaceItem) {
        return new WeaponModule();
    } else {
        return new GeneralModule(); // fallback per tutti gli altri item
    }
}

// Switch manuale tra moduli (via tab click o menu)
private void switchModule(EditorModule newModule) {
    if (activeModule.hasUnsavedChanges()) {
        showDialog(ConfirmDialog.unsavedChanges(
            activeModule.getPendingChanges().size(),
            () -> doSwitch(newModule),
            () -> {}
        ));
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
| **Undo/Redo** | Da implementare per ogni editor | Ereditato da AbstractEditorModule |
| **Dirty tracking** | Codice duplicato | Centralizzato in AbstractEditorModule |
| **Preview mode** | Complesso da sincronizzare | Integrato nel ciclo di vita modulo |

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

---

## Feature Avanzate Implementate

Questa sezione documenta le feature avanzate che sono state implementate oltre al design iniziale.

### UI Scaling Dinamico

L'editor supporta scaling dinamico dell'UI con più modalità:

```java
// In EditorClientConfig.java
public enum EditorUiScale {
    AUTO("auto"),       // Scala automatica basata su risoluzione
    SCALE_1_0("1.0"),   // 100% - nessuno scaling
    SCALE_1_25("1.25"), // 125%
    SCALE_1_5("1.5"),   // 150%
    SCALE_2_0("2.0");   // 200% - HiDPI
}
```

Le coordinate sono gestite centralmente da `ScaledCoord`:

```java
public class ScaledCoord {
    private static float currentScale = 1.0f;

    public static int scaleDim(int base, float scale) {
        return Math.round(base * scale);
    }

    public static int scaleDim(int base) {
        return scaleDim(base, currentScale);
    }
}
```

### Sistema Overlay con BaseOverlay

Gli overlay modali (dialoghi, pannelli, help) estendono `BaseOverlay`:

```java
public abstract class BaseOverlay {
    protected boolean visible = false;
    protected AnimationState animationState;

    // Metodi base
    public void show() { visible = true; animationState.reset(); }
    public void hide() { visible = false; }
    public void toggle() { if (visible) hide(); else show(); }
    public boolean isVisible() { return visible; }

    // Template method per il rendering
    public final void render(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible) return;
        float progress = animationState != null ? animationState.getProgress() : 1.0f;
        renderBackdrop(g, screenW, screenH, progress);
        // ... render panel e contenuto
    }

    // Hooks per le sottoclassi
    protected abstract void renderContent(...);
    protected abstract int getPanelWidth();
    protected abstract int getPanelHeight();

    // Supporto animazioni
    public BaseOverlay withAnimation() {
        this.animationState = new AnimationState(AnimationState.Type.FADE, 200);
        return this;
    }
}
```

**Overlay che estendono BaseOverlay:**
- `ConfirmDialog` - Dialoghi di conferma
- `HelpOverlay` - Pannello aiuto (F1)
- `TemplateOverlay` - Selezione template
- `CraftingInfoPanel` - Info ricette crafting

### Sistema Animazioni

```java
public class AnimationState {
    public enum Type { FADE, SLIDE_UP, SLIDE_DOWN, SCALE }
    public enum Easing { LINEAR, EASE_OUT_CUBIC, EASE_IN_OUT, EASE_OUT_BACK }

    private final Type type;
    private final int durationMs;
    private long startTime;

    public float getProgress() {
        float raw = (System.currentTimeMillis() - startTime) / (float) durationMs;
        return applyEasing(Math.min(1.0f, raw));
    }
}
```

### Sistema Temi

L'editor supporta temi configurabili:

```java
public interface Theme {
    int panelBackground();
    int panelBorder();
    int headerBackground();
    int textPrimary();
    int textSecondary();
    int accentPrimary();
    // ... altri colori
}

public class ThemeManager {
    private static Theme currentTheme = new DarkTheme();

    public static Theme current() { return currentTheme; }
    public static void setTheme(Theme theme) { currentTheme = theme; notifyListeners(); }
}
```

### Focus Management

Supporto navigazione da tastiera tra componenti:

```java
public class FocusManager {
    public interface Focusable {
        boolean isFocused();
        void setFocused(boolean focused);
        void onFocusGained();
        void onFocusLost();
    }

    private final List<Focusable> focusables = new ArrayList<>();
    private int focusIndex = -1;

    public void focusNext() { ... }      // Tab
    public void focusPrevious() { ... }  // Shift+Tab
}
```

### EditorCache

Sistema di cache per ottimizzare il rendering:

```java
public class EditorCache {
    public enum Types { PREVIEW, LAYOUT, STATS, ALL }

    private final Map<String, CachedValue<?>> cache = new ConcurrentHashMap<>();

    public void invalidateType(Types type) { ... }
    public void invalidateItem(String itemId) { ... }
    public void invalidateAll() { cache.clear(); }
}
```

---

## File Structure Implementata

```
ui/editor/
├── ItemEditorScreen.java          # Screen principale (shell)
├── EditorModule.java              # Interface per moduli
├── EditorSection.java             # Interface sealed per sezioni
├── AbstractEditorModule.java      # Classe base per moduli
├── ModuleTab.java                 # Definizione tab
├── EditorStartTab.java            # Enum per tab iniziale
│
├── modules/
│   ├── WeaponModule.java          # Modulo armi melee
│   ├── ArmorModule.java           # Modulo armature
│   ├── RangedModule.java          # Modulo armi a distanza
│   └── GeneralModule.java         # Modulo proprietà generali
│
├── components/
│   ├── EditorButton.java          # Bottone multi-stile
│   ├── EditorSlider.java          # Slider numerico
│   ├── EditorToggle.java          # Toggle boolean
│   ├── EditorTextField.java       # Campo testo
│   ├── HeaderComponent.java       # Header con tabs
│   ├── FooterComponent.java       # Footer con azioni
│   ├── LeftColumnComponent.java   # Colonna sinistra
│   ├── ScrollableContentArea.java # Area contenuto scrollabile
│   └── ButtonRow.java             # Layout orizzontale bottoni
│
├── core/
│   ├── EditorLayout.java          # Layout engine
│   ├── Bounds.java                # Record bounds
│   ├── SectionBounds.java         # Bounds per sezione
│   ├── UIConstants.java           # Colori e costanti
│   ├── EditorSpacing.java         # Spacing constants
│   ├── EditorDimensions.java      # Dimensioni componenti
│   ├── ScaledCoord.java           # Coordinate scalate
│   ├── EditorScaleCalculator.java # Calcolo scala UI
│   ├── BaseOverlay.java           # Classe base overlay
│   ├── AnimationState.java        # Gestione animazioni
│   ├── Theme.java                 # Interface tema
│   ├── ThemeManager.java          # Gestione temi
│   ├── FocusManager.java          # Focus keyboard
│   └── EditorCache.java           # Sistema cache
│
├── systems/
│   ├── ConfirmDialog.java         # Dialoghi conferma
│   ├── HelpOverlay.java           # Pannello aiuto
│   ├── TemplateOverlay.java       # Selezione template
│   ├── CraftingInfoPanel.java     # Info crafting
│   ├── DebugPanel.java            # Debug info
│   ├── MultiEditPanel.java        # Multi-edit UI
│   ├── MultiEditManager.java      # Logica multi-edit
│   ├── DirtyState.java            # Tracking modifiche
│   └── UndoRedoStack.java         # Undo/Redo stack
│
└── debug/
    ├── DebugOverlay.java          # Overlay debug
    ├── DebugInfoSection.java      # Sezione info debug
    └── ValueComparison.java       # Comparazione valori
```
