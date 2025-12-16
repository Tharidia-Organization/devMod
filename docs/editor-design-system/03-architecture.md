# Architecture

## Unified Editor Architecture

> **Architettura confermata:** Un solo `ItemEditorScreen` con moduli per tipo item.

### Filosofia

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

### Regole Architetturali NON NEGOZIABILI

| Regola | Descrizione |
|--------|-------------|
| **R1: Zero coordinate nei moduli** | I moduli NON piazzano widget. Ritornano `List<EditorSection>` |
| **R2: Layout engine unico** | `EditorLayout` renderizza tutto, calcola posizioni |
| **R3: Shell fisso** | Header/Footer/Left column identici per tutti i moduli |
| **R4: Tab auto-select** | Aprendo da armor → tab Armor attivo. Da weapon → tab Weapon |

### Struttura Classi

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

### EditorModule Interface

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

### EditorSection - Unità di contenuto

```java
/**
 * Sezione di contenuto renderizzabile.
 * NON contiene posizioni - solo dati.
 */
public sealed interface EditorSection {

    record SliderSection(
        String label,
        float minValue,
        float maxValue,
        float currentValue,
        int accentColor,
        Consumer<Float> onChange
    ) implements EditorSection {}

    record ToggleSection(
        String label,
        boolean currentValue,
        Consumer<Boolean> onChange
    ) implements EditorSection {}

    record ListSection(
        String label,
        List<ListItem> items,
        Consumer<ListItem> onSelect
    ) implements EditorSection {}

    record SeparatorSection(
        String title
    ) implements EditorSection {}

    record InputSection(
        String label,
        String currentValue,
        Consumer<String> onChange
    ) implements EditorSection {}
}
```

### EditorLayout - Calcola TUTTO

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

## Entry Point: Radial Menu Integration

> **Entry point confermato:** Radial Menu con voci separate per tipo.

### Filosofia

```
┌─────────────────────────────────────────────────────────────────┐
│  RADIAL MENU                                                    │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  Voci SEPARATE per tipo, ma STESSO screen:                      │
│                                                                 │
│  [Edit Weapon]  ──→  ItemEditorScreen(item, WEAPON)             │
│  [Edit Armor]   ──→  ItemEditorScreen(item, ARMOR)              │
│  [Edit Item]    ──→  ItemEditorScreen(item, GENERAL)            │
│                                                                 │
│  Visibilità basata su tipo item in mano.                        │
│  Auto-detect solo come FALLBACK, non logica principale.         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Enum StartTab

```java
public enum EditorStartTab {
    WEAPON,   // Apre WeaponModule
    ARMOR,    // Apre ArmorModule
    GENERAL;  // Apre GeneralModule (fallback)
}
```

### Constructor ItemEditorScreen

```java
public class ItemEditorScreen extends Screen {

    private final ItemStack item;
    private final EditorStartTab requestedTab;
    private EditorModule activeModule;

    /**
     * Costruttore con tab esplicito.
     * @param item L'item da editare
     * @param startTab Il modulo richiesto (WEAPON, ARMOR, GENERAL)
     */
    public ItemEditorScreen(ItemStack item, EditorStartTab startTab) {
        super(Component.literal("Item Editor"));
        this.item = item;
        this.requestedTab = startTab;
        this.activeModule = resolveModule(item, startTab);
    }

    /**
     * Risolve il modulo da usare.
     * Se startTab non è applicabile all'item, fallback a GENERAL + warning.
     */
    private EditorModule resolveModule(ItemStack item, EditorStartTab requested) {
        return switch (requested) {
            case WEAPON -> {
                if (isWeapon(item)) {
                    yield new WeaponModule(item);
                } else {
                    LOGGER.warn("Requested WEAPON tab but item {} is not a weapon. Falling back to GENERAL.",
                        item.getItem().getDescriptionId());
                    yield new GeneralModule(item);
                }
            }
            case ARMOR -> {
                if (isArmor(item)) {
                    yield new ArmorModule(item);
                } else {
                    LOGGER.warn("Requested ARMOR tab but item {} is not armor. Falling back to GENERAL.",
                        item.getItem().getDescriptionId());
                    yield new GeneralModule(item);
                }
            }
            case GENERAL -> new GeneralModule(item);
        };
    }
}
```

### Vantaggi dell'Architettura Unificata

| Aspetto | Prima (2 editor) | Dopo (1 editor + moduli) |
|---------|------------------|--------------------------|
| **Bug layout** | Fix in 2 posti | Fix in 1 posto |
| **Nuovi tipi item** | Nuovo Screen | Nuovo Module (~100 righe) |
| **Testing** | 2 screen da testare | 1 screen, moduli isolati |
| **Coerenza UI** | Dipende da disciplina | Garantita da architettura |
| **Coordinate** | Sparse ovunque | Solo in EditorLayout |

### Migration Path

```
FASE 1: Crea struttura base
─────────────────────────────
1. Crea EditorLayout.java
2. Crea EditorModule interface
3. Crea EditorSection sealed interface
4. Crea ItemEditorScreen shell

FASE 2: Migra WeaponEditor
─────────────────────────────
1. Estrai logica in WeaponModule
2. Converti sliders in SliderSection
3. Testa che funzioni identico

FASE 3: Migra ArmorEditor
─────────────────────────────
1. Estrai logica in ArmorModule
2. Converti sliders in SliderSection
3. Testa che funzioni identico

FASE 4: Rimuovi vecchi editor
─────────────────────────────
1. Elimina WeaponEditorScreen.java
2. Elimina ArmorEditorScreen.java
3. Aggiorna tutti i riferimenti
```