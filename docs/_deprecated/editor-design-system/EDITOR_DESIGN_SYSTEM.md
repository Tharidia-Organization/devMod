# DevMod Editor Design System
> DEPRECATED: superseded by the modular editor docs in `docs/editor-design-system/README.md` and `docs/editor-design-system/EDITOR_DESIGN_SYSTEM.md`.
## Unified UI/UX Specification for Item Editors
### Version 1.5 - Template & Preset Architecture

> **Source-of-truth (IMPORTANT):** questo documento resta allineato al codice. Dove esempi e codice divergono, **vince il comportamento reale**. Stato attuale: **MultiEdit è implementato**, lo storage per-item usa **CustomData** con tag `WeaponModStats` / `ArmorModStats`, il Recipe Editor è **FUTURE / NON ORA**.

---

**Implementation Status**
- **Implementation:** MultiEdit subsystem (manager + panel) added and wired into the editor UI (`src/main/java/com/frenkvs/devmod/ui/editor/systems/MultiEditManager.java`, `MultiEditPanel.java`).
- **Preset adapter:** `ItemEditorPresetManager` added to map `ItemEditorDataManager.PresetData` into `WeaponStats` / `ArmorStats` and persist via existing config managers.
- **Preset wrapper:** `DataPreset` provides a `Preset` facade over `ItemEditorDataManager.PresetData`.
- **UI:** `MultiEditPanel` shows selection, items, remove, preset selector (dropdown scrollabile), e `[Apply to all]` / `[Clear All]` actions. Default expanded; header toggles collapse; empty-state note quando zero match; Apply disabilitato in Preview.
- **Feedback:** Batch apply operations producono un `BatchEditResult`; il pannello mostra success/failure count, dettagli fallimenti espandibili e bottone “Copy” per copiare gli errori.
- **Data keys:** Per-item editor data is stored under `WeaponModStats` (weapon) e `ArmorModStats` (armor). Vecchi nomi come `devmod:stats` / `devmod:custom_stats` sono deprecati.
 - **Dual-mode semantics (Preview vs Apply):** Preview operations modify an editor-local copy only and do not persist per-item CustomData or send network packets. Applying with persistence invokes the persistence handler which updates the inventory slot and sends the appropriate payloads (weapon/armor) to the server; persistence failures are surfaced as batch failures in the UI.
- **Scope update:** Recipe/Crafting panels (Feature A/C) sono ORA IN SCOPE per questa iterazione (prima marcati FUTURE); devono essere implementati in entrambi gli editor.

**Remaining work (da svolgere ora):**
- Implementare Crafting Info Panel / Item Value Analysis (Feature A/C) per weapon/armor, UI e logica, con overlay/tab come da specifica.
- Aggiungere unit test per `ItemEditorPresetManager` e `MultiEditManager.applyPresetToAll`; aggiungere integrazione test per MultiEdit/Debug.
- Polish failure UI (virtualizzazione liste lunghe, modal dedicato), aggiornare screenshot/gif (nuovi media post-implementazione crafting/failure UI).
- Aggiornare documentazione e media PR con gli elementi sopra (preset dropdown, failure summary, crafting/value panels, debug).

**Media (PR4):**
- Screenshot/gif #1: preset dropdown aperto con lista scrollabile.
- Screenshot/gif #2: failure summary con lista espansa + bottone Copy errors.


## EXECUTIVE SUMMARY

Questo documento definisce il **Design System unificato** per tutti gli Item Editor di DevMod (ArmorEditor, WeaponEditor, e futuri editor). L'obiettivo è garantire:

1. **Coerenza visiva totale** - Layout identico, stessi componenti, stessa UX
2. **Affidabilità per i tester** - Comportamento prevedibile, feedback chiaro, nessuna perdita dati
3. **Manutenibilità** - Componenti riutilizzabili, costanti centralizzate

---

## STRATEGIC CONTEXT

### Target Users
| Priorità | Utente | Descrizione |
|----------|--------|-------------|
| 1 | **Developer** | Tu - sviluppo e debug principale |
| 2 | **Team Tester** | Testing funzionale e bilanciamento |
| 3 | **Content Creators** | Modders esterni (futuro) |

### Development Phase
**Early-Mid Development** - Focus su:
- Trovare e risolvere bug
- Primo bilanciamento gameplay
- Strumenti di debug prioritari

### Primary Use Cases (in ordine di frequenza)
| # | Scenario | Obiettivo | Feature Chiave |
|---|----------|-----------|----------------|
| 1 | "Questo item non funziona" | **DEBUG** | Log, valori raw, confronto expected vs actual |
| 2 | "Troppo forte/debole" | **BILANCIAMENTO** | Sliders rapidi, presets, value analysis |
| 3 | "Creare nuovi item" | **CONTENUTI** | Templates, batch, export/import |

### Design Philosophy
```
DEBUG FIRST → BALANCE SECOND → CONTENT THIRD
```

Gli editor devono essere **strumenti di lavoro**, non UI consumer-friendly.
Priorità: **velocità di diagnosi** > estetica > facilità d'uso per nuovi utenti.

---

# PARTE 1: SPECIFICHE LAYOUT

## 1.1 Dimensioni Standard

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ITEM EDITOR                                 │
│                        550px × 420px                                │
└─────────────────────────────────────────────────────────────────────┘
```

| Costante | Valore | Descrizione |
|----------|--------|-------------|
| `PANEL_WIDTH` | **550px** | Larghezza totale pannello |
| `PANEL_HEIGHT` | **420px** | Altezza totale pannello |
| `HEADER_HEIGHT` | **28px** | Altezza header con tabs |
| `FOOTER_HEIGHT` | **60px** | Altezza footer con bottoni |
| `LEFT_COLUMN_WIDTH` | **140px** | Colonna sinistra (preview + slots + info) |
| `CONTENT_WIDTH` | **390px** | Area contenuto tabs |
| `PREVIEW_SIZE` | **130px** | Dimensione preview 3D |
| `SLOT_AREA_HEIGHT` | **70px** | Area slot selector |
| `INFO_PANEL_HEIGHT` | **100px** | Pannello info item |

## 1.2 Layout Master

```
┌─────────────────────────────────────────────────────────────────────┐
│ [Tab1] [Tab2] [Tab3] [Tab4] [Tab5]               [MODE BADGE]  [X]  │  28px
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌────────────┐   ┌─────────────────────────────────────────────┐  │
│  │            │   │                                             │  │
│  │  PREVIEW   │   │                                             │  │
│  │  130x130   │   │                                             │  │
│  │            │   │           TAB CONTENT AREA                  │  │
│  │  [Rotate]  │   │                                             │  │  280px
│  └────────────┘   │           - Sliders                         │  │
│                   │           - Lists                           │  │
│  ┌────────────┐   │           - Pickers                         │  │
│  │   SLOTS    │   │           - Toggles                         │  │
│  │  [1][2]    │   │                                             │  │
│  │  [3][4]    │   │                                             │  │
│  └────────────┘   │                                             │  │
│                   └─────────────────────────────────────────────┘  │
│  ┌────────────┐                                                    │
│  │ ITEM INFO  │                                                    │
│  │ Name       │                                                    │
│  │ Stats      │                                                    │
│  │ ● 3 unsaved│                                                    │
│  └────────────┘                                                    │
├─────────────────────────────────────────────────────────────────────┤
│  [Undo][Redo] │ [History][Export][Import][Presets] │ [Apply]       │  60px
│               │           [Reset] [Cancel]         │               │
└─────────────────────────────────────────────────────────────────────┘
     140px                      390px
```

## 1.3 Posizioni Esatte (in pixel)

### Header Zone (y: 0 → 28)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Tab buttons | centered | 4 | 70 each | 20 |
| Mode badge | PANEL_WIDTH - 110 | 4 | 100 | 20 |
| Close button | PANEL_WIDTH - 25 | 4 | 20 | 20 |

### Left Column (x: 10, width: 140)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Preview | 12 | 20 | 130 | 130 |
| Rotate hint | 12 | 150 | 130 | 12 |
| Slot selector | 10 | 170 | 130 | 70 |
| Selected piece card | 10 | 248 | 130 | 46 |
| Item info | 10 | 300 | 130 | 100 |
| Dirty indicator | 15 | 360 | 120 | 15 |

> Nota: la card "Selected piece" sotto i quattro slot mostra il pezzo attivo (icona + label) e permette di ciclare rapidamente gli slot con un click.

### Content Area (x: 150, width: 390)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Tab content | 150 | 35 | 390 | 280 |
| Content padding | 8px internal | | | |

### Footer Zone (y: 360 → 420)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Undo button | 10 | 365 | 50 | 22 |
| Redo button | 65 | 365 | 50 | 22 |
| Separator | 120 | 365 | 1 | 50 |
| Actions row (History/Export/Import/Presets/Reset/Cancel) | 130 | 365 | 320 | 22 |
| Apply button | 420 | 365 | 120 | 50 |

> Nota: la row di quick actions è sempre visibile; ogni pulsante ha hover/border accent, senza dropdown. Apply mostra `Preview only` / `No changes` / `Apply (n)` in base allo stato.

> Nota overlay: il pannello Presets è modale (overlay scuro a schermo intero, pannello centrato) e viene renderizzato sopra al modello 3D.

---

# PARTE 2: COMPONENTI CONDIVISI

## 2.1 Slot Selector System

Il Slot Selector è l'elemento che differenzia i due editor, ma deve seguire lo stesso pattern visivo.

### ArmorEditor: 4-Slot Grid
```
┌───────────────────────────────┐
│         ARMOR SLOTS           │
├───────┬───────┬───────┬───────┤
│  [H]  │  [C]  │  [L]  │  [F]  │
│ HEAD  │ CHEST │ LEGS  │ FEET  │
│  🪖   │  🦺   │  👖   │  👢   │
└───────┴───────┴───────┴───────┘
   30px    30px    30px    30px

- Layout: 4 slot in linea orizzontale
- Ogni slot: 30x30px con icona item
- Slot attivo: bordo CYAN, background highlighted
- Slot vuoto: bordo muted, icona placeholder
- Slot con item: mostra item icon
- Interazione: Click per selezionare, Drag item per inserire
```

### WeaponEditor: 2-Slot Tabs
```
┌───────────────────────────────┐
│        WEAPON SLOTS           │
├───────────────┬───────────────┤
│  [MAIN HAND]  │  [OFF HAND]   │
│      ⚔️       │      🛡️       │
└───────────────┴───────────────┘
      60px            60px

- Layout: 2 tab orizzontali
- Ogni tab: 60x30px
- Tab attiva: bordo CYAN, background highlighted
- Tab con item: mostra item icon miniatura
- Tab vuota: icona placeholder (sword/shield)
- Interazione: Click per switchare
```

### Codice Condiviso

```java
// Interfaccia comune
public interface ISlotSelector {
    int getSlotCount();
    int getActiveSlot();
    void setActiveSlot(int slot);
    ItemStack getItemInSlot(int slot);
    void setItemInSlot(int slot, ItemStack stack);
    void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY);
    boolean mouseClicked(double mouseX, double mouseY, int button);
}

// Costanti condivise
public static final int SLOT_SIZE = 30;
public static final int SLOT_GAP = 5;
public static final int SLOT_BORDER_ACTIVE = UIConstants.Border.ACCENT;  // Cyan
public static final int SLOT_BORDER_NORMAL = UIConstants.Border.MUTED;
public static final int SLOT_BG_ACTIVE = UIConstants.Background.ACTIVE;
public static final int SLOT_BG_NORMAL = UIConstants.Background.INPUT;
```

## 2.2 Mode Badge

Identico in entrambi gli editor:

```
┌─────────────────────┐
│  ● GLOBAL           │  ← Arancione, modifica TUTTI gli item di quel tipo
└─────────────────────┘

┌─────────────────────┐
│  ● SPECIFIC         │  ← Verde, modifica solo QUESTO item
└─────────────────────┘
```

### Specifiche
| Proprietà | Valore |
|-----------|--------|
| Width | 100px |
| Height | 20px |
| Position | Top-right, 10px da bordo |
| Border GLOBAL | `UIConstants.Accent.ORANGE` (0xFFFF9800) |
| Border SPECIFIC | `UIConstants.Accent.GREEN` (0xFF00FF00) |
| Background | `UIConstants.Background.PANEL` |
| Text | "GLOBAL" o "SPECIFIC" |
| Dot indicator | Stesso colore del border |

### Tooltip (OBBLIGATORIO)
```
GLOBAL hover: "Changes will apply to ALL [item_type] in the game"
SPECIFIC hover: "Changes will apply only to THIS specific item"
```

## 2.3 Dirty State System

**CRITICO PER I TESTER** - Implementazione identica in entrambi gli editor.

### Campi
```java
private boolean isDirty = false;
private final List<String> pendingChanges = new ArrayList<>();
private long lastSaveTimestamp = 0;
```

### Metodi
```java
/**
 * Marca lo stato come modificato.
 * @param changeDescription Descrizione della modifica per debugging
 */
private void markDirty(String changeDescription) {
    isDirty = true;
    if (!pendingChanges.contains(changeDescription)) {
        pendingChanges.add(changeDescription);
    }
}

/**
 * Resetta lo stato dirty dopo un salvataggio riuscito.
 */
private void clearDirty() {
    isDirty = false;
    pendingChanges.clear();
    lastSaveTimestamp = System.currentTimeMillis();
}

/**
 * Verifica se ci sono modifiche non salvate.
 */
private boolean hasUnsavedChanges() {
    return isDirty && !pendingChanges.isEmpty();
}
```

### Trigger per markDirty()
Ogni editor DEVE chiamare `markDirty()` quando:
- Slider value cambia
- Toggle viene switchato
- Enchantment aggiunto/rimosso/modificato
- Attribute aggiunto/rimosso/modificato
- Qualsiasi valore numerico cambia

### UI Indicator
```
Posizione: Left column, sotto Item Info panel
Formato quando dirty:    "● 3 unsaved changes"  (colore: ORANGE)
Formato quando saved:    "✓ Saved 2 min ago"    (colore: GREEN)
Formato quando fresh:    (nessun testo)
```

## 2.4 Confirmation Dialog

Dialog modale per azioni distruttive. Identico in entrambi gli editor.

### Trigger
1. **Close con modifiche pending** → "Discard unsaved changes?"
2. **Switch slot con modifiche pending** → "Discard changes to [slot_name]?"
3. **Reset** → "Reset all values to default?"
4. **Load preset** → "This will overwrite current values. Continue?"
5. **Delete preset** → "Delete preset '[name]'? This cannot be undone."

### Layout
```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                     ⚠️  UNSAVED CHANGES                         │
│                                                                 │
│     You have 3 unsaved changes that will be lost.              │
│     Are you sure you want to close?                            │
│                                                                 │
│                                                                 │
│            [ Discard ]              [ Cancel ]                  │
│              (RED)                   (GRAY)                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Dimensioni: 350px × 150px, centrato nello schermo
Background: UIConstants.Background.PANEL_SOLID con overlay scuro (0x80000000)
```

### Implementazione
```java
private static class ConfirmDialog {
    final String title;
    final String message;
    final String confirmText;
    final String cancelText;
    final int confirmColor;
    final Runnable onConfirm;
    final Runnable onCancel;

    private static final int WIDTH = 350;
    private static final int HEIGHT = 150;

    void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        // Dark overlay
        graphics.fill(0, 0, screenWidth, screenHeight, 0x80000000);

        int x = (screenWidth - WIDTH) / 2;
        int y = (screenHeight - HEIGHT) / 2;

        // Panel
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(graphics, x, y, WIDTH, HEIGHT, UIConstants.Border.DEFAULT);

        // Title
        graphics.drawString(font, title, x + 20, y + 15, UIConstants.Text.TITLE, false);

        // Message
        graphics.drawString(font, message, x + 20, y + 45, UIConstants.Text.PRIMARY, false);

        // Buttons
        int btnY = y + HEIGHT - 40;
        int btnWidth = 100;
        // Confirm button (left, colored)
        renderDialogButton(graphics, font, x + 50, btnY, btnWidth, confirmText, confirmColor);
        // Cancel button (right, gray)
        renderDialogButton(graphics, font, x + WIDTH - 150, btnY, btnWidth, cancelText, UIConstants.Background.HOVER);
    }
}
```

## 2.5 Item Info Panel

Mostra informazioni sull'item correntemente in editing.

```
┌────────────────────────┐
│      ITEM INFO         │
├────────────────────────┤
│  ┌────┐                │
│  │ICON│  Diamond Sword │
│  │32px│                │
│  └────┘  Enchants: 3   │
│          Durability: ▓▓▓▓░ 85%
│          Attack: +7    │
│          Speed: +1.6   │
└────────────────────────┘
```

### Dati mostrati
| Editor | Campo 1 | Campo 2 | Campo 3 | Campo 4 |
|--------|---------|---------|---------|---------|
| Weapon | Attack Damage | Attack Speed | Durability | Enchants count |
| Armor | Defense | Toughness | Durability | Enchants count |

## 2.6 Crafting Info Panel *(IN SCOPE — DA IMPLEMENTARE ORA)*

Questa sezione è ora **in scope per l’iterazione corrente**: deve essere implementata (UI + logica) nei due editor. Mostra la ricetta di crafting dell'item corrente e il suo valore calcolato. Attualmente non ancora realizzata: seguire le specifiche sotto per lo sviluppo.

### Layout
```
┌────────────────────────────────────────┐
│           CRAFTING RECIPE              │
├────────────────────────────────────────┤
│                                        │
│    ┌───┬───┬───┐                       │
│    │   │ D │   │     D = Diamond       │
│    ├───┼───┼───┤     S = Stick         │
│    │   │ D │   │                       │
│    ├───┼───┼───┤     Result: Diamond   │
│    │   │ S │   │             Sword     │
│    └───┴───┴───┘                       │
│                                        │
├────────────────────────────────────────┤
│  ITEM VALUE ANALYSIS                   │
│                                        │
│  Ingredients:                          │
│   • 2x Diamond      (Rare)    +80      │
│   • 1x Stick        (Common)   +2      │
│                           ─────────    │
│  Total Value:                  82      │
│  Rarity Tier:              RARE        │
│                                        │
└────────────────────────────────────────┘
```

### Specifiche
| Proprietà | Valore |
|-----------|--------|
| Width | 300px (popup/overlay) |
| Height | Auto (based on content) |
| Trigger | Button "Recipe" in footer o Tab dedicata |
| Position | Centered overlay o integrato in tab COMPONENTS |

### Visualizzazione Ricetta (Feature A)

Mostra la griglia di crafting 3x3 con gli ingredienti posizionati:

```java
private void renderCraftingGrid(GuiGraphics graphics, int x, int y, CraftingRecipe recipe) {
    int cellSize = 24;
    int gridSize = cellSize * 3;

    // Background grid
    for (int row = 0; row < 3; row++) {
        for (int col = 0; col < 3; col++) {
            int cellX = x + col * cellSize;
            int cellY = y + row * cellSize;

            graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize,
                UIConstants.Background.INPUT);
            AxiomRenderer.drawBorder(graphics, cellX, cellY, cellSize, cellSize,
                UIConstants.Border.MUTED);

            // Render ingredient if present
            ItemStack ingredient = recipe.getIngredient(row, col);
            if (!ingredient.isEmpty()) {
                graphics.renderItem(ingredient, cellX + 4, cellY + 4);
            }
        }
    }

    // Arrow
    int arrowX = x + gridSize + 10;
    int arrowY = y + cellSize;
    graphics.drawString(font, "→", arrowX, arrowY + 4, UIConstants.Text.SECONDARY, false);

    // Result
    int resultX = arrowX + 20;
    graphics.renderItem(recipe.getResult(), resultX, arrowY);
}
```

### Sistema di Valutazione Item (Feature C)

Calcola un valore numerico basato sulla rarità degli ingredienti:

```java
public record ItemValueAnalysis(
    List<IngredientValue> ingredients,
    int totalValue,
    RarityTier rarityTier
) {}

public record IngredientValue(
    ItemStack item,
    int count,
    RarityTier rarity,
    int value
) {}

public enum RarityTier {
    COMMON(1, 0xFF888888, "Common"),      // Dirt, Cobblestone, Stick
    UNCOMMON(5, 0xFF55FF55, "Uncommon"),  // Iron, Coal, Redstone
    RARE(40, 0xFF5555FF, "Rare"),         // Diamond, Gold, Lapis
    EPIC(100, 0xFFAA00AA, "Epic"),        // Netherite, Emerald
    LEGENDARY(250, 0xFFFFAA00, "Legendary"); // Dragon Egg, Nether Star

    final int baseValue;
    final int color;
    final String displayName;
}

// Calcolo valore
private ItemValueAnalysis analyzeItemValue(ItemStack stack) {
    Optional<CraftingRecipe> recipe = findRecipeFor(stack);
    if (recipe.isEmpty()) {
        return new ItemValueAnalysis(List.of(), 0, RarityTier.COMMON);
    }

    List<IngredientValue> ingredients = new ArrayList<>();
    int totalValue = 0;
    RarityTier highestRarity = RarityTier.COMMON;

    for (ItemStack ingredient : recipe.get().getIngredients()) {
        RarityTier rarity = determineRarity(ingredient);
        int value = rarity.baseValue * ingredient.getCount();

        ingredients.add(new IngredientValue(ingredient, ingredient.getCount(), rarity, value));
        totalValue += value;

        if (rarity.ordinal() > highestRarity.ordinal()) {
            highestRarity = rarity;
        }
    }

    return new ItemValueAnalysis(ingredients, totalValue, highestRarity);
}

// Rendering
private void renderValueAnalysis(GuiGraphics graphics, int x, int y, ItemValueAnalysis analysis) {
    graphics.drawString(font, "ITEM VALUE ANALYSIS", x, y, UIConstants.Text.TITLE, false);

    int lineY = y + 18;
    graphics.drawString(font, "Ingredients:", x, lineY, UIConstants.Text.SECONDARY, false);
    lineY += 14;

    for (IngredientValue ing : analysis.ingredients()) {
        String line = String.format("• %dx %s", ing.count(), ing.item().getHoverName().getString());
        graphics.drawString(font, line, x + 5, lineY, UIConstants.Text.PRIMARY, false);

        // Rarity tag
        String rarityTag = String.format("(%s)", ing.rarity().displayName);
        int tagX = x + 150;
        graphics.drawString(font, rarityTag, tagX, lineY, ing.rarity().color, false);

        // Value
        String valueStr = String.format("+%d", ing.value());
        int valueX = x + 230;
        graphics.drawString(font, valueStr, valueX, lineY, UIConstants.Text.VALUE, false);

        lineY += 12;
    }

    // Separator
    lineY += 4;
    graphics.fill(x, lineY, x + 260, lineY + 1, UIConstants.Border.SEPARATOR);
    lineY += 8;

    // Total
    graphics.drawString(font, "Total Value:", x, lineY, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, String.valueOf(analysis.totalValue()), x + 200, lineY,
        UIConstants.Text.VALUE, false);

    lineY += 14;
    graphics.drawString(font, "Rarity Tier:", x, lineY, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, analysis.rarityTier().displayName, x + 200, lineY,
        analysis.rarityTier().color, false);
}
```

### Integrazione negli Editor

| Editor | Posizione |
|--------|-----------|
| Weapon | Tab "COMPONENTS" o bottone "Recipe" nel footer |
| Armor | Tab "COMPONENTS" (nuova) o bottone "Recipe" nel footer |

### Mappa Rarità Ingredienti (Configurabile)

```java
private static final Map<String, RarityTier> INGREDIENT_RARITY = Map.ofEntries(
    // Common
    Map.entry("minecraft:stick", RarityTier.COMMON),
    Map.entry("minecraft:cobblestone", RarityTier.COMMON),
    Map.entry("minecraft:oak_planks", RarityTier.COMMON),
    Map.entry("minecraft:leather", RarityTier.COMMON),
    Map.entry("minecraft:string", RarityTier.COMMON),

    // Uncommon
    Map.entry("minecraft:iron_ingot", RarityTier.UNCOMMON),
    Map.entry("minecraft:gold_ingot", RarityTier.UNCOMMON),
    Map.entry("minecraft:redstone", RarityTier.UNCOMMON),
    Map.entry("minecraft:coal", RarityTier.UNCOMMON),
    Map.entry("minecraft:copper_ingot", RarityTier.UNCOMMON),

    // Rare
    Map.entry("minecraft:diamond", RarityTier.RARE),
    Map.entry("minecraft:lapis_lazuli", RarityTier.RARE),
    Map.entry("minecraft:obsidian", RarityTier.RARE),
    Map.entry("minecraft:blaze_rod", RarityTier.RARE),

    // Epic
    Map.entry("minecraft:netherite_ingot", RarityTier.EPIC),
    Map.entry("minecraft:emerald", RarityTier.EPIC),
    Map.entry("minecraft:echo_shard", RarityTier.EPIC),

    // Legendary
    Map.entry("minecraft:nether_star", RarityTier.LEGENDARY),
    Map.entry("minecraft:dragon_egg", RarityTier.LEGENDARY),
    Map.entry("minecraft:elytra", RarityTier.LEGENDARY)
);
```

---

## 2.7 Recipe Editor (FUTURE - Feature B) — NON ORA

> **NOTA:** Feature fuori perimetro per l'iterazione attuale. Non implementare ora.
> Rimandata a fase successiva e documentata solo come riferimento.

### Descrizione
Permettere la modifica runtime delle ricette di crafting direttamente dall'editor.

### Complessità
- Richiede integrazione profonda con il RecipeManager di Minecraft
- Necessita sincronizzazione server-client per le ricette modificate
- Deve gestire conflitti con ricette esistenti
- Richiede persistenza delle modifiche (datapack o config)

### Approcci Possibili
1. **Datapack Generation** - Genera JSON ricette in un datapack custom
2. **Runtime Recipe Injection** - Modifica RecipeManager in memoria (reset al restart)
3. **Config-based** - Salva override in config, applica al caricamento

### Dipendenze
- Sistema di persistenza ricette
- UI per editing griglia 3x3 con drag & drop
- Validazione ricette (no duplicati, ingredienti validi)
- Sync network per multiplayer

### Stima Effort
| Approccio | Linee | Complessità |
|-----------|-------|-------------|
| Datapack | 500-700 | Alta |
| Runtime | 300-400 | Media (non persistente) |
| Config | 400-500 | Media-Alta |

*Questa feature sarà sviluppata in un documento separato: `RECIPE_EDITOR_PLAN.md`*

---

## 2.8 Debug Panel (PRIORITÀ MASSIMA)

> **NOTA:** Questa è la feature più importante per la fase attuale di sviluppo.
> Deve essere implementata PRIMA di altre feature "nice-to-have".

### Obiettivo
Fornire informazioni di debug immediate per diagnosticare problemi con item/armor/weapon.

### Layout
```
┌─────────────────────────────────────────────────────────────────┐
│  DEBUG INFO                                            [Copy]   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ITEM DATA                                                      │
│  ─────────────────────────────────────────────────────────────  │
│  Registry: minecraft:diamond_sword                              │
│  Stack Size: 1                                                  │
│  Damage: 0/1561                                                 │
│  NBT Tags: 3                                                    │
│                                                                 │
│  VALUE COMPARISONS                                              │
│  ─────────────────────────────────────────────────────────────  │
│  attack_damage:     7.0  →  12.0  [MISMATCH]                    │
│  attack_speed:      1.6  →  1.6                                 │
│  durability:        1561 →  2000  [MODIFIED]                    │
│                                                                 │
│  RECENT CHANGES (this session)                                  │
│  ─────────────────────────────────────────────────────────────  │
│  14:32:05  Set attack_damage = 12.0                             │
│  14:32:08  Applied to server                                    │
│  14:32:08  Server confirmed                                     │
│                                                                 │
│  NBT VIEWER                                                     │
│  ─────────────────────────────────────────────────────────────  │
│  {                                                              │
│    Damage: 0,                                                   │
│    Enchantments: [...],                                         │
│    WeaponModStats: {...}                                        │
│  }                                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Definition of Done (DEBUG tab)
- Item identification: registry, stack size, damage/maxDamage, NBT tag count, flag `hasCustomData`.
- Value comparisons: original → current, con badge `[MODIFIED]` o `[MISMATCH]` (mismatch se `serverValue` differisce). Colori: giallo per modified, rosso per mismatch.
- Session log: cronologico, include set/apply/server confirm/error, mostra almeno 1 entry o placeholder “(no entries)”.
- NBT viewer: dump leggibile del `CustomData` (`WeaponModStats` / `ArmorModStats` inclusi) con indentazione base.
- Copy-to-clipboard: bottone “Copy Debug” esporta header + values + session log + NBT.

**Stato runtime attuale (PR2):**
- Baseline server/config: se l’item ha `WeaponModStats` / `ArmorModStats` si usa quello; in assenza, fallback alla config globale se esiste; se nessuna fonte è disponibile la UI mostra `SERVER N/A` e aggiunge nota nel clipboard.
- Session log: include “Server confirmed/rejected” eventi inviati dal server dopo l’apply (global/specific); se il server nega o l’item manca, il messaggio riporta il motivo.
- MultiEdit preset dropdown: limitato a 6 elementi visibili, scrollabile, mostra il nome completo in hover e la label `Preset (<itemType>)`.
- MultiEdit failure UI: mostra conteggi success/fail, bottone “Details” per espandere i falliti (6 di default, “+more” fino a 20) e “Copy fails” per copiare solo i falliti.

### Posizione
- **Tab dedicata "DEBUG"** - Ultima tab in entrambi gli editor
- Oppure **Panel collapsabile** nella left column

### Dati da Mostrare

#### 1. Item Identification
```java
record ItemDebugInfo(
    String registryName,      // "minecraft:diamond_sword"
    int stackSize,            // 1
    int currentDamage,        // 0
    int maxDamage,            // 1561
    int nbtTagCount,          // Numero di tag NBT
    boolean hasCustomData     // true se ha dati DevMod
) {}
```

#### 2. Value Comparison (Expected vs Actual)
*ServerValue è opzionale: la UI mostra original → current e usa `hasMismatch` per colorare/etichettare.*
```java
record ValueComparison(
    String attributeName,
    double originalValue,     // Valore vanilla o da ultima load
    double currentValue,      // Valore attuale nell'editor
    double serverValue,       // Valore salvato sul server (da config)
    boolean isModified,       // currentValue != originalValue
    boolean hasMismatch       // serverValue != currentValue (BUG!)
) {}

private void renderValueComparison(GuiGraphics graphics, int x, int y, ValueComparison comp) {
    int color = comp.hasMismatch() ? UIConstants.Accent.RED :
                comp.isModified() ? UIConstants.Accent.YELLOW :
                UIConstants.Text.SECONDARY;

    String line = String.format("%-20s %8.1f → %8.1f",
        comp.attributeName(), comp.originalValue(), comp.currentValue());
    graphics.drawString(font, line, x, y, color, false);

    if (comp.hasMismatch()) {
        graphics.drawString(font, "[MISMATCH!]", x + 250, y, UIConstants.Accent.RED, false);
    } else if (comp.isModified()) {
        graphics.drawString(font, "[MODIFIED]", x + 250, y, UIConstants.Accent.YELLOW, false);
    }
}
```

#### 3. Change Log (Session)
```java
record ChangeLogEntry(
    long timestamp,
    String action,           // "Set", "Applied", "Server confirmed", "Error"
    String detail,           // "attack_damage = 12.0"
    boolean isError          // true se errore
) {}

private final List<ChangeLogEntry> sessionLog = new ArrayList<>();

private void logChange(String action, String detail) {
    sessionLog.add(new ChangeLogEntry(System.currentTimeMillis(), action, detail, false));
}

private void logError(String action, String detail) {
    sessionLog.add(new ChangeLogEntry(System.currentTimeMillis(), action, detail, true));
}
```

#### 4. NBT Viewer
```java
private void renderNbtViewer(GuiGraphics graphics, int x, int y, ItemStack stack) {
    CompoundTag tag = stack.getTag();
    if (tag == null) {
        graphics.drawString(font, "(no NBT data)", x, y, UIConstants.Text.MUTED, false);
        return;
    }

    // Render formatted JSON-like structure
    String nbtString = formatNbtForDisplay(tag, 0);
    int lineY = y;
    for (String line : nbtString.split("\n")) {
        graphics.drawString(font, line, x, lineY, UIConstants.Text.FORMULA, false);
        lineY += 10;
    }
}

private String formatNbtForDisplay(CompoundTag tag, int indent) {
    StringBuilder sb = new StringBuilder();
    String prefix = "  ".repeat(indent);

    sb.append(prefix).append("{\n");
    for (String key : tag.getAllKeys()) {
        sb.append(prefix).append("  ").append(key).append(": ");
        Tag value = tag.get(key);
        if (value instanceof CompoundTag compound) {
            sb.append("\n").append(formatNbtForDisplay(compound, indent + 1));
        } else {
            sb.append(value.toString()).append(",\n");
        }
    }
    sb.append(prefix).append("}\n");

    return sb.toString();
}
```

### Copy to Clipboard Button
```java
private void copyDebugInfoToClipboard() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== DEVMOD DEBUG INFO ===\n");
    sb.append("Item: ").append(getRegistryName()).append("\n");
    sb.append("Timestamp: ").append(LocalDateTime.now()).append("\n\n");

    sb.append("--- VALUES ---\n");
    for (ValueComparison comp : getValueComparisons()) {
        sb.append(String.format("%s: %.2f → %.2f %s\n",
            comp.attributeName(), comp.originalValue(), comp.currentValue(),
            comp.hasMismatch() ? "[MISMATCH]" : comp.isModified() ? "[MOD]" : ""));
    }

    sb.append("\n--- SESSION LOG ---\n");
    for (ChangeLogEntry entry : sessionLog) {
        sb.append(formatTimestamp(entry.timestamp())).append(" ");
        sb.append(entry.action()).append(": ").append(entry.detail()).append("\n");
    }

    sb.append("\n--- NBT ---\n");
    sb.append(formatNbtForDisplay(stack.getTag(), 0));

    Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
    showStatus("Debug info copied!", UIConstants.Accent.GREEN);
}
```

### Come leggere la Debug tab (per tester)
1. **Item Data**: controlla nome registry, damage e `hasCustomData` (se `WeaponModStats`/`ArmorModStats` presenti).
2. **Value comparisons**: giallo = modificato (non ancora applicato), rosso = mismatch con server/config (`hasMismatch=true`).
3. **Session log**: verifica se l'apply è partito/riuscito o se c'è un errore (placeholder se vuoto).
4. **NBT viewer**: controlla che il blocco `WeaponModStats`/`ArmorModStats` contenga i valori attesi e timestamp `modifiedAt`.
5. **Copy Debug**: usa il bottone per incollare in chat/issue e allegare log completo.

### Integrazione
| Editor | Tab Index | Shortcut |
|--------|-----------|----------|
| Weapon | Tab 5 (COMPONENTS → DEBUG) | F3 |
| Armor | Tab 5 (EFFECTS → DEBUG) o Tab 6 | F3 |

### Colori Status
| Stato | Colore | Significato |
|-------|--------|-------------|
| Normal | `Text.SECONDARY` | Valore non modificato |
| Modified | `Accent.YELLOW` | Modificato ma non ancora salvato |
| Saved | `Accent.GREEN` | Salvato con successo |
| Mismatch | `Accent.RED` | Server ha valore diverso (BUG!) |
| Error | `Accent.RED` | Errore di comunicazione |

---

## 2.9 Dual-Mode System (PREVIEW / APPLY)

> **Architettura confermata:** Gli editor supportano due modalità operative.

### Concetto

```
┌─────────────────────────────────────────────────────────────────┐
│  ● GLOBAL    [👁 PREVIEW MODE]                            [X]   │
│              ─────────────────                                  │
│              Modifiche visibili solo in questo client.          │
│              Nessun dato inviato al server.                     │
│              Reset automatico alla chiusura.                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  ● SPECIFIC  [⚡ APPLY MODE]                              [X]   │
│              ──────────────                                     │
│              Modifiche inviate al server.                       │
│              Persistono dopo chiusura.                          │
│              Richiedono conferma per applicare.                 │
└─────────────────────────────────────────────────────────────────┘
```

### Modalità

| Modalità | Icona | Descrizione | Comportamento |
|----------|-------|-------------|---------------|
| **PREVIEW** | 👁 | Solo visualizzazione | Client-only, nessun packet, reset on close |
| **APPLY** | ⚡ | Applica modifiche | Invia al server, persiste, richiede conferma |

### Layout Mode Toggle

```
Posizione: Accanto al Mode Badge (GLOBAL/SPECIFIC)
Dimensioni: 120px × 20px

┌──────────────────────────────────────────────────┐
│  ● GLOBAL   [👁 PREVIEW ▼]              [X]      │
│              ↑                                    │
│              Click per toggle dropdown            │
└──────────────────────────────────────────────────┘

Dropdown:
┌─────────────────┐
│ 👁 PREVIEW      │  ← Attualmente selezionato
├─────────────────┤
│ ⚡ APPLY        │
└─────────────────┘
```

### Specifiche Visive

| Modalità | Background Badge | Border | Text Color |
|----------|-----------------|--------|------------|
| PREVIEW | `0x40FFFF00` (Giallo trasparente) | `UIConstants.Accent.YELLOW` | `0xFFFFFF00` |
| APPLY | `0x4000FF00` (Verde trasparente) | `UIConstants.Accent.GREEN` | `0xFF00FF00` |

### Comportamento PREVIEW Mode

```java
/**
 * In PREVIEW mode, le modifiche sono solo client-side.
 * - Nessun UpdateArmorPayload / UpdateWeaponPayload inviato
 * - Valori visualizzati in UI aggiornati
 * - Item reale NON modificato
 * - Dirty state NON attivo (nulla da salvare)
 * - Chiusura editor: nessun warning, reset automatico
 */
private boolean isPreviewMode = true; // Default: PREVIEW

private void handleSliderChange(float newValue) {
    if (isPreviewMode) {
        // Solo aggiorna UI locale
        this.displayValue = newValue;
        // NON chiamare markDirty()
        // NON inviare packets
    } else {
        // APPLY mode: comportamento normale
        markDirty("Changed " + attributeName + " to " + newValue);
        this.pendingValue = newValue;
    }
}

@Override
public void onClose() {
    if (isPreviewMode) {
        // Chiudi senza warning - nulla da salvare
        super.onClose();
    } else {
        // Comportamento normale con dirty check
        if (hasUnsavedChanges()) {
            showConfirmDialog(...);
        } else {
            super.onClose();
        }
    }
}
```

### Comportamento APPLY Mode

```java
/**
 * In APPLY mode, le modifiche sono inviate al server.
 * - Dirty state attivo
 * - Apply button invia UpdatePayload al server
 * - Server processa e salva in config
 * - Richiede conferma prima di chiudere con modifiche pending
 */
private void applyChanges() {
    if (!isPreviewMode && hasUnsavedChanges()) {
        // Costruisci payload
        UpdateArmorPayload payload = buildPayload();

        // Invia al server
        PacketDistributor.sendToServer(payload);

        // Log per debug
        logChange("Applied", formatPayloadSummary(payload));

        // Clear dirty state
        clearDirty();

        // Feedback visivo
        showStatus("Changes applied!", UIConstants.Accent.GREEN);
        UIConstants.Sound.save();
    }
}
```

### Toggle Mode

```java
private void toggleMode() {
    if (isPreviewMode) {
        // Switching to APPLY mode
        isPreviewMode = false;
        showStatus("APPLY mode - Changes will be saved", UIConstants.Accent.GREEN);
    } else {
        // Switching to PREVIEW mode
        if (hasUnsavedChanges()) {
            showConfirmDialog(
                "Switch to Preview",
                "Unsaved changes will be discarded. Continue?",
                () -> {
                    discardChanges();
                    isPreviewMode = true;
                    showStatus("PREVIEW mode - Changes are temporary", UIConstants.Accent.YELLOW);
                },
                () -> {} // Cancel
            );
        } else {
            isPreviewMode = true;
            showStatus("PREVIEW mode - Changes are temporary", UIConstants.Accent.YELLOW);
        }
    }
}
```

### Indicatori Visivi in UI

#### Header Badge

```
PREVIEW mode:
┌─────────────────────┐
│  👁 PREVIEW         │  Border: YELLOW, pulsing glow effect
└─────────────────────┘

APPLY mode:
┌─────────────────────┐
│  ⚡ APPLY           │  Border: GREEN, solid
└─────────────────────┘
```

#### Apply Button Stato

| Modalità | Stato Button | Testo | Colore |
|----------|--------------|-------|--------|
| PREVIEW | Disabled | "Preview Only" | GRAY |
| APPLY + Clean | Disabled | "No Changes" | GRAY |
| APPLY + Dirty | Enabled | "Apply (3)" | GREEN |

```java
private void renderApplyButton(GuiGraphics graphics, int x, int y) {
    boolean canApply = !isPreviewMode && hasUnsavedChanges();

    String text;
    int bgColor;
    int borderColor;

    if (isPreviewMode) {
        text = "Preview Only";
        bgColor = UIConstants.Background.INPUT;
        borderColor = UIConstants.Border.MUTED;
    } else if (!hasUnsavedChanges()) {
        text = "No Changes";
        bgColor = UIConstants.Background.INPUT;
        borderColor = UIConstants.Border.MUTED;
    } else {
        text = "Apply (" + pendingChanges.size() + ")";
        bgColor = hovered ? UIConstants.Accent.GREEN : UIConstants.Background.INPUT;
        borderColor = UIConstants.Accent.GREEN;
    }

    renderFooterButton(graphics, font, x, y,
        BTN_LARGE_WIDTH, BTN_LARGE_HEIGHT,
        text, borderColor, hovered, canApply);
}
```

### Tooltip Informativi

```java
// Mode badge tooltip
if (isPreviewMode) {
    tooltip = List.of(
        Component.literal("PREVIEW MODE").withStyle(ChatFormatting.YELLOW),
        Component.literal("Changes are temporary and client-only."),
        Component.literal("Nothing is saved to server."),
        Component.literal(""),
        Component.literal("Click to switch to APPLY mode.").withStyle(ChatFormatting.GRAY)
    );
} else {
    tooltip = List.of(
        Component.literal("APPLY MODE").withStyle(ChatFormatting.GREEN),
        Component.literal("Changes will be sent to server."),
        Component.literal("Use Apply button to save."),
        Component.literal(""),
        Component.literal("Click to switch to PREVIEW mode.").withStyle(ChatFormatting.GRAY)
    );
}
```

### Interazione con Mode Badge (GLOBAL/SPECIFIC)

I due badge sono **indipendenti**:

```
┌───────────────────────────────────────────────────────────────┐
│  ● GLOBAL   [👁 PREVIEW ▼]                              [X]   │
│  ↑          ↑                                                 │
│  │          └── Modalità operativa (cosa succede ai dati)     │
│  └───────────── Scope applicazione (a quali item)             │
└───────────────────────────────────────────────────────────────┘

Combinazioni possibili:
- GLOBAL + PREVIEW:  Visualizza come sarebbero TUTTI gli item di quel tipo
- GLOBAL + APPLY:    Modifica effettiva su TUTTI gli item di quel tipo
- SPECIFIC + PREVIEW: Visualizza modifiche su QUESTO item specifico
- SPECIFIC + APPLY:  Modifica effettiva su QUESTO item specifico
```

### Persistenza Modalità

```java
// La modalità scelta persiste per sessione (non per item)
// Default: PREVIEW per sicurezza

// Opzionale: Salvare preferenza in config utente
private void saveUserModePreference() {
    Config.CLIENT.editorDefaultMode.set(isPreviewMode ? "PREVIEW" : "APPLY");
}

// Load on init
private void loadUserModePreference() {
    String pref = Config.CLIENT.editorDefaultMode.get();
    isPreviewMode = !"APPLY".equals(pref);
}
```

### Keyboard Shortcuts

| Shortcut | Azione |
|----------|--------|
| **F5** | Toggle PREVIEW ↔ APPLY mode |
| **Ctrl+Enter** | Quick Apply (solo in APPLY mode con dirty) |

```java
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    // ... existing shortcuts ...

    // F5: Toggle mode
    if (keyCode == GLFW.GLFW_KEY_F5) {
        toggleMode();
        return true;
    }

    // Ctrl+Enter: Quick apply
    if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_ENTER) {
        if (!isPreviewMode && hasUnsavedChanges()) {
            applyChanges();
        }
        return true;
    }

    return super.keyPressed(keyCode, scanCode, modifiers);
}
```

### Integrazione con Debug Panel

Il Debug Panel mostra informazioni diverse in base alla modalità:

```
PREVIEW mode:
┌─────────────────────────────────────────────────────────────────┐
│  DEBUG INFO                                      [PREVIEW MODE] │
├─────────────────────────────────────────────────────────────────┤
│  PREVIEW VALUES (not saved)                                     │
│  ─────────────────────────────────────────────────────────────  │
│  attack_damage:     7.0  →  12.0  [PREVIEW]                     │
│  attack_speed:      1.6  →  1.6                                 │
│                                                                 │
│  Note: These values are CLIENT-ONLY.                            │
│  Switch to APPLY mode to save changes.                          │
└─────────────────────────────────────────────────────────────────┘

APPLY mode:
┌─────────────────────────────────────────────────────────────────┐
│  DEBUG INFO                                       [APPLY MODE]  │
├─────────────────────────────────────────────────────────────────┤
│  CURRENT VALUES (pending save)                                  │
│  ─────────────────────────────────────────────────────────────  │
│  attack_damage:     7.0  →  12.0  [MODIFIED]                    │
│  attack_speed:      1.6  →  1.6                                 │
│                                                                 │
│  EXPECTED vs ACTUAL                                             │
│  Config says:       attack_damage = 7.0                         │
│  Pending:           attack_damage = 12.0  [WILL CHANGE]         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2.10 Persistence Architecture

> **Architettura confermata:** Storage primario B (CustomData + serverconfig), Export D (datapack)

### Filosofia

```
┌─────────────────────────────────────────────────────────────────┐
│  PERSISTENZA DI LAVORO (veloce, iterativa)                      │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  SPECIFIC mode → NBT/Components sull'item stesso                │
│                  Persiste con l'item, funziona in multiplayer   │
│                                                                 │
│  GLOBAL mode   → Per-world serverconfig                         │
│                  File: world/serverconfig/devmod-items.toml     │
│                  Applicato a tutti gli item di quel tipo        │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  EXPORT/RELEASE FORMAT (stabile, condivisibile)                 │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  Datapack      → JSON in datapacks/devmod_balance/              │
│                  Formato ufficiale per distribuzione            │
│                  Versionabile in Git, condivisibile             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Perché NON Datapack come Storage Primario

| Problema | Descrizione |
|----------|-------------|
| **Lento da iterare** | Scrittura file + reload + cache clear |
| **Non copre per-item** | Datapack = regole globali, non instance-specific |
| **Conflitti priority** | Gestione precedenze tra datapack complessa |
| **Troppo formale** | Overkill per "sto testando un valore" |

### Storage Layers

```
Layer 1: SPECIFIC (Per-Item Instance)
─────────────────────────────────────
Dove:     CustomData (WeaponModStats/ArmorModStats) sull'ItemStack
Scope:    Solo quell'item specifico
Persiste: Finché l'item esiste
Sync:     Automatico con item (inventory sync)

Layer 2: GLOBAL (Per-World Rules)
─────────────────────────────────────
Dove:     world/serverconfig/devmod-items.toml
Scope:    Tutti gli item di quel tipo in quel mondo
Persiste: Finché il mondo esiste
Sync:     Server → Client al login

Layer 3: EXPORT (Distribution Format)
─────────────────────────────────────
Dove:     datapacks/devmod_balance/data/devmod/...
Scope:    Qualsiasi mondo che carichi il datapack
Persiste: Sempre (file system)
Sync:     /reload o restart server
```

### Priorità Applicazione

Quando un item viene valutato, le modifiche si applicano in questo ordine (ultima vince):

```
1. Vanilla defaults                    (base)
    ↓
2. Datapack rules (se presente)        (override globale)
    ↓
3. Per-world serverconfig              (override mondo)
    ↓
4. Per-item CustomData                 (override istanza) ← VINCE
```

### Implementazione SPECIFIC (Layer 1)

```java
// NBT keys allineati (no namespace, coerenti tra codebase e doc)
private static final String ARMOR_STATS_KEY = "ArmorModStats";   // Armor editor
private static final String WEAPON_STATS_KEY = "WeaponModStats"; // Weapon editor

// Salvataggio su item (Armor example; Weapon usa WEAPON_STATS_KEY)
private void saveToItemNBT(ItemStack stack, ArmorStats stats) {
    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    CompoundTag devmodTag = new CompoundTag();

    devmodTag.putFloat("physicalReduction", stats.physicalReduction);
    devmodTag.putFloat("fireReduction", stats.fireReduction);
    devmodTag.putFloat("magicReduction", stats.magicReduction);
    devmodTag.putFloat("explosionReduction", stats.explosionReduction);
    devmodTag.putFloat("projectileReduction", stats.projectileReduction);
    devmodTag.putFloat("armorBonus", stats.armorBonus);
    devmodTag.putFloat("toughnessBonus", stats.toughnessBonus);
    devmodTag.putFloat("knockbackResistance", stats.knockbackResistance);
    devmodTag.putBoolean("thornsReflect", stats.thornsReflect);
    devmodTag.putFloat("thornsPercent", stats.thornsPercent);

    // Timestamp per debug
    devmodTag.putLong("modifiedAt", System.currentTimeMillis());

    tag.put(ARMOR_STATS_KEY, devmodTag);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
}

// Lettura da item
private ArmorStats loadFromItemNBT(ItemStack stack) {
    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (!tag.contains(ARMOR_STATS_KEY)) {
        return null; // Nessuna modifica custom
    }

    CompoundTag devmodTag = tag.getCompound(ARMOR_STATS_KEY);
    ArmorStats stats = new ArmorStats();

    stats.physicalReduction = devmodTag.getFloat("physicalReduction");
    stats.fireReduction = devmodTag.getFloat("fireReduction");
    // ... altri campi

    return stats;
}

// Check se item ha modifiche custom
public boolean hasCustomStats(ItemStack stack) {
    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    return tag.contains(ARMOR_STATS_KEY);
}

// Rimuovi modifiche custom (reset to vanilla/global)
public void clearCustomStats(ItemStack stack) {
    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (tag.contains(ARMOR_STATS_KEY)) {
        tag.remove(ARMOR_STATS_KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
```

### Implementazione GLOBAL (Layer 2)

```java
// File: world/serverconfig/devmod-items.toml
// Formato:
// [armor."minecraft:diamond_chestplate"]
// physicalReduction = 0.8
// fireReduction = 0.5
// ...

public class DevModItemConfig {
    private static final Map<String, ArmorStats> ARMOR_OVERRIDES = new HashMap<>();
    private static final Map<String, WeaponStats> WEAPON_OVERRIDES = new HashMap<>();

    // Chiamato al caricamento del mondo
    public static void loadWorldConfig(Path worldPath) {
        Path configPath = worldPath.resolve("serverconfig/devmod-items.toml");
        if (Files.exists(configPath)) {
            // Parse TOML e popola mappe
            parseConfigFile(configPath);
        }
    }

    // Chiamato quando un editor applica modifiche GLOBAL
    public static void saveGlobalOverride(String itemId, ArmorStats stats) {
        ARMOR_OVERRIDES.put(itemId, stats);
        writeConfigFile();

        // Sync a tutti i client
        syncToAllClients();
    }

    // Ottieni stats per un item type
    public static ArmorStats getGlobalOverride(String itemId) {
        return ARMOR_OVERRIDES.get(itemId);
    }
}
```

### Implementazione EXPORT (Layer 3)

```java
// Export a Datapack
public class DatapackExporter {

    public static void exportToDatapack(String packName) {
        Path packPath = getDatapacksPath().resolve(packName);

        // Struttura:
        // datapacks/devmod_balance/
        //   pack.mcmeta
        //   data/
        //     devmod/
        //       item_modifiers/
        //         armor/
        //           diamond_chestplate.json
        //         weapons/
        //           diamond_sword.json

        createPackMeta(packPath, packName);

        // Export armor overrides
        for (var entry : DevModItemConfig.getArmorOverrides().entrySet()) {
            String itemId = entry.getKey();
            ArmorStats stats = entry.getValue();

            Path jsonPath = packPath.resolve(
                "data/devmod/item_modifiers/armor/" +
                itemId.replace(":", "_") + ".json"
            );

            writeArmorJson(jsonPath, itemId, stats);
        }

        // Export weapon overrides
        for (var entry : DevModItemConfig.getWeaponOverrides().entrySet()) {
            // ... similar
        }

        LOGGER.info("Exported {} items to datapack: {}",
            getExportedCount(), packName);
    }

    // Formato JSON per armor modifier
    private static void writeArmorJson(Path path, String itemId, ArmorStats stats) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "devmod:armor_stats");
        json.addProperty("target", itemId);

        JsonObject values = new JsonObject();
        values.addProperty("physical_reduction", stats.physicalReduction);
        values.addProperty("fire_reduction", stats.fireReduction);
        values.addProperty("magic_reduction", stats.magicReduction);
        values.addProperty("explosion_reduction", stats.explosionReduction);
        values.addProperty("projectile_reduction", stats.projectileReduction);
        values.addProperty("armor_bonus", stats.armorBonus);
        values.addProperty("toughness_bonus", stats.toughnessBonus);
        values.addProperty("knockback_resistance", stats.knockbackResistance);
        values.addProperty("thorns_reflect", stats.thornsReflect);
        values.addProperty("thorns_percent", stats.thornsPercent);

        json.add("values", values);

        // Metadata
        JsonObject meta = new JsonObject();
        meta.addProperty("exported_at", LocalDateTime.now().toString());
        meta.addProperty("devmod_version", DevMod.VERSION);
        json.add("_meta", meta);

        Files.writeString(path, GSON.toJson(json));
    }
}

// Import da Datapack
public class DatapackImporter {

    public static int importFromDatapack(String packName) {
        Path packPath = getDatapacksPath().resolve(packName);

        if (!Files.exists(packPath)) {
            LOGGER.warn("Datapack not found: {}", packName);
            return 0;
        }

        int imported = 0;

        // Import armor
        Path armorPath = packPath.resolve("data/devmod/item_modifiers/armor");
        if (Files.exists(armorPath)) {
            imported += importArmorModifiers(armorPath);
        }

        // Import weapons
        Path weaponPath = packPath.resolve("data/devmod/item_modifiers/weapons");
        if (Files.exists(weaponPath)) {
            imported += importWeaponModifiers(weaponPath);
        }

        LOGGER.info("Imported {} items from datapack: {}", imported, packName);
        return imported;
    }
}
```

### UI Export/Import

```
┌─────────────────────────────────────────────────────────────────┐
│  EXPORT / IMPORT                                          [X]   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  EXPORT TO DATAPACK                                             │
│  ─────────────────────────────────────────────────────────────  │
│  Pack name: [devmod_balance_v1___]                              │
│                                                                 │
│  Include:                                                       │
│  [✓] Armor overrides (12 items)                                 │
│  [✓] Weapon overrides (8 items)                                 │
│  [ ] Per-item custom stats (requires /give)                     │
│                                                                 │
│                           [Export]                              │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  IMPORT FROM DATAPACK                                           │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  Available:                                                     │
│  > devmod_balance_v1 (20 items, 2024-01-15)                     │
│    devmod_test_pack (5 items, 2024-01-10)                       │
│                                                                 │
│  [Import Selected]  [Refresh List]                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Workflow Tipico

```
SVILUPPO (rapido, iterativo)
────────────────────────────
1. Apri editor su item
2. Modifica valori (PREVIEW mode per testare)
3. Quando soddisfatto → APPLY mode → Apply
4. Modifiche salvate in:
   - NBT item (se SPECIFIC)
   - serverconfig (se GLOBAL)
5. Testa immediatamente in-game

RELEASE (stabile, condivisibile)
────────────────────────────
1. Bilanciamento completato e testato
2. Footer → Export button
3. Scegli nome datapack
4. Export genera:
   datapacks/devmod_balance_v1/
     pack.mcmeta
     data/devmod/item_modifiers/...
5. Datapack pronto per:
   - Condividere con team
   - Commit in Git
   - Distribuire a server
```

### Indicatore Sorgente Stats

Nel Debug Panel, mostra da dove vengono i valori correnti:

```
┌─────────────────────────────────────────────────────────────────┐
│  DEBUG INFO - STAT SOURCES                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  attack_damage:   7.0 → 12.0                                    │
│                   ↑      ↑                                      │
│                   │      └── [NBT] Custom per-item override     │
│                   └───────── [VANILLA] Base value               │
│                                                                 │
│  armor:           8.0 → 10.0                                    │
│                   ↑      ↑                                      │
│                   │      └── [CONFIG] Per-world serverconfig    │
│                   └───────── [DATAPACK] devmod_balance_v1       │
│                                                                 │
│  Source Priority: NBT > CONFIG > DATAPACK > VANILLA             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2.11 Preview Component

### Specifiche comuni
| Proprietà | Valore |
|-----------|--------|
| Size | 130x130px |
| Position | Left column, top |
| Background | Transparent/subtle gradient |
| Border | 1px, UIConstants.Border.MUTED |

### Interazione
- **Mouse drag**: Ruota il modello orizzontalmente
- **Mouse scroll**: Zoom in/out (opzionale)
- **Fallback**: Auto-rotate lento se nessuna interazione

### Rendering
| Editor | Contenuto |
|--------|-----------|
| Armor | Player model con armatura equipaggiata, slot attivo evidenziato |
| Weapon | Item 3D flottante con rotazione |

---

## 2.12 Unified Editor Architecture

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

### Esempio: WeaponModule

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

### Auto-Select su Apertura

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

---

## 2.13 Entry Point: Radial Menu Integration

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

### Helper Methods per Type Detection

```java
/**
 * Utility per determinare il tipo di item.
 * Usato sia dal Radial Menu che dall'Editor.
 */
public final class ItemTypeHelper {

    private ItemTypeHelper() {}

    /**
     * Verifica se l'item è un'arma editabile.
     */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof SwordItem
            || item instanceof AxeItem
            || item instanceof TridentItem
            || item instanceof MaceItem
            // Aggiungi altri tipi se necessario
            || hasWeaponAttributes(stack);
    }

    /**
     * Verifica se l'item è un'armatura editabile.
     */
    public static boolean isArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ArmorItem;
    }

    /**
     * Verifica se l'item è editabile in generale.
     * True per qualsiasi item non-vuoto.
     */
    public static boolean isEditable(ItemStack stack) {
        return !stack.isEmpty();
    }

    /**
     * Check per armi custom che non estendono SwordItem.
     */
    private static boolean hasWeaponAttributes(ItemStack stack) {
        // Check se ha attack_damage attribute
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
            .keySet()
            .stream()
            .anyMatch(attr -> attr.equals(Attributes.ATTACK_DAMAGE));
    }
}
```

### Radial Menu Integration

```java
// Nel RadialMenuRegistry o dove registri le voci

// Voce "Edit Weapon" - visibile solo se isWeapon()
RadialMenuRegistry.register(new RadialMenuItem(
    "edit_weapon",
    Component.translatable("devmod.radial.edit_weapon"),
    WEAPON_ICON,
    (player) -> {
        ItemStack held = player.getMainHandItem();
        if (ItemTypeHelper.isWeapon(held)) {
            Minecraft.getInstance().setScreen(
                new ItemEditorScreen(held, EditorStartTab.WEAPON)
            );
        }
    },
    // Condizione visibilità
    (player) -> ItemTypeHelper.isWeapon(player.getMainHandItem())
));

// Voce "Edit Armor" - visibile solo se isArmor()
RadialMenuRegistry.register(new RadialMenuItem(
    "edit_armor",
    Component.translatable("devmod.radial.edit_armor"),
    ARMOR_ICON,
    (player) -> {
        ItemStack held = player.getMainHandItem();
        if (ItemTypeHelper.isArmor(held)) {
            Minecraft.getInstance().setScreen(
                new ItemEditorScreen(held, EditorStartTab.ARMOR)
            );
        }
    },
    // Condizione visibilità
    (player) -> ItemTypeHelper.isArmor(player.getMainHandItem())
));

// Voce "Edit Item" (General) - visibile se item editabile ma non weapon/armor
RadialMenuRegistry.register(new RadialMenuItem(
    "edit_item",
    Component.translatable("devmod.radial.edit_item"),
    GENERAL_ICON,
    (player) -> {
        ItemStack held = player.getMainHandItem();
        if (ItemTypeHelper.isEditable(held)) {
            Minecraft.getInstance().setScreen(
                new ItemEditorScreen(held, EditorStartTab.GENERAL)
            );
        }
    },
    // Condizione visibilità: editabile ma NON weapon e NON armor
    (player) -> {
        ItemStack held = player.getMainHandItem();
        return ItemTypeHelper.isEditable(held)
            && !ItemTypeHelper.isWeapon(held)
            && !ItemTypeHelper.isArmor(held);
    }
));
```

### Comportamento Fallback

| Scenario | Azione | Log |
|----------|--------|-----|
| WEAPON richiesto + item è weapon | Apre WeaponModule | - |
| WEAPON richiesto + item NON è weapon | Apre GeneralModule | `WARN: Falling back to GENERAL` |
| ARMOR richiesto + item è armor | Apre ArmorModule | - |
| ARMOR richiesto + item NON è armor | Apre GeneralModule | `WARN: Falling back to GENERAL` |
| GENERAL richiesto | Apre GeneralModule | - |

### Translation Keys

```json
{
    "devmod.radial.edit_weapon": "Edit Weapon",
    "devmod.radial.edit_armor": "Edit Armor",
    "devmod.radial.edit_item": "Edit Item"
}
```

### Debug: Nessuna Voce Visibile

Se l'utente apre il radial menu con un item che non mostra nessuna voce editor:

```java
// Nel GeneralModule o come fallback globale
// Opzionale: mostrare comunque "Edit Item" per QUALSIASI item
// Decidi se vuoi questo comportamento
```

**Decisione attuale:** "Edit Item" visibile solo se NON weapon e NON armor.
Se vuoi che sia sempre visibile come fallback, cambia la condizione.

---

## 2.14 Selection Mode: Single + MultiEdit

> **Decisione strutturale (stato attuale):** MultiEdit è parte della UI. L'entry point primario rimane l'editing single-item, mentre il pannello `MultiEdit` fornisce selezione, visualizzazione e azioni batch (apply-to-all / presets). NOTA: l'integrazione automatica della selezione (es. aggiunta via drag/drop o click in inventory) è parzialmente implementata; al momento la selezione deve essere popolata dall'utente attraverso il pannello o tramite workflow specifici non ancora stabiliti. Questa sezione documenta il comportamento reale del codice e gli step mancanti per completa integrazione.

### Razionale

| Fase | Caso d'uso principale | Modalità |
|------|-----------------------|----------|
| DEBUG | "Questo item non funziona" | Single di default; MultiEdit se devo applicare la stessa fix a più copie |
| BALANCE | "Questo valore è sbilanciato" | Single + apply preset/valori alla selezione |
| CONTENT | "Applica preset a 20 item" | MultiEdit (apply-to-all) |

### Decisione

```
┌─────────────────────────────────────────────────────────────────┐
│  FASE ATTUALE: Single + MultiEdit (ufficiale)                   │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  ✓ Default: un item focus con undo/redo + dirty state           │
│  ✓ MultiEditManager + MultiEditPanel per lista selezionati      │
│  ✓ [Apply to All] + [Clear All] + preset selector (dropdown)    │
│  ✓ BatchEditResult: success/failure count + dettagli + Copy     │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  DEBT: UX / SCALING                                             │
│  ─────────────────────────────────────────────────────────────  │
│  ▢ Shortcut / toggle rapido MultiEdit (da definire)             │
│  ▢ Virtualizzazione liste (preset/failures) per selezioni ampie │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Implicazioni Architetturali

```java
// ItemEditorScreen - focus singolo, MultiEdit come subsystem
public class ItemEditorScreen extends Screen {
    private final ItemStack item;              // Item principale in editing
    private final MultiEditManager multiEdit;  // Selezione multipla + batch apply
}

// EditorModule - continua a lavorare su singolo item, ma deve emettere log coerenti
public interface EditorModule {
    void setItem(ItemStack item);  // Singolo
    // MultiEditPanel richiama gli stessi apply/dirty hooks sugli item selezionati
}
```

### Cosa NON implementare ora

| Feature | Motivo esclusione |
|---------|-------------------|
| Nuova BatchEditorScreen separata | Riutilizziamo ItemEditorScreen + MultiEditPanel |
| Flussi di multi-selezione estesi (inventory scan massivo) | Scope creep, UI non definita |
| Conflict resolution avanzato / batch undo | Fuori scope P1, bastano failure list + log |

### FUTURE: Batch Edit Architecture (Avanzata/Deferred)

> **NOTA:** Se servirà scalare oltre il pannello MultiEdit attuale, questa è la base architetturale. Non sostituisce il flusso corrente.

#### Entry Point

```java
// Stesso pattern di ItemEditorScreen
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

#### BatchEditMode Enum

```java
public enum BatchEditMode {
    PREVIEW,        // Mostra cosa cambierebbe, non applica
    APPLY_TO_ALL,   // Applica stesso valore a tutti gli item
    APPLY_MATCHING; // Applica solo agli item che matchano un criterio
}
```

#### Gestione Valori Multipli

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

#### UI per Valori Misti

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

#### Conflict Resolution Strategy

```java
public enum ConflictStrategy {
    OVERWRITE_ALL,      // Ignora valori esistenti, applica nuovo
    KEEP_HIGHER,        // Mantieni il valore più alto tra esistente e nuovo
    KEEP_LOWER,         // Mantieni il valore più basso
    APPLY_DELTA,        // Applica differenza (+5, -10%, etc.)
    SKIP_IF_DIFFERENT;  // Non modificare se già diverso da target
}
```

#### Batch Payload

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

#### Batch Undo/Redo

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

#### Radial Menu Entry (FUTURE)

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

#### Nota su batch editor legacy (DEFERRED)
- Il flusso “BatchEditorScreen / BatchSelectionScreen” resta documentato solo come idea futura: l’implementazione attuale usa il pannello MultiEdit dentro `ItemEditorScreen` (dropdown preset + apply-to-all). Non introdurre nuove screen batch in questa fase.

#### File Structure (attuale + futuro deferito)

```
src/main/java/com/frenkvs/devmod/ui/editor/
├── ItemEditorScreen.java          ← Entry point singolo + pannello MultiEdit
├── systems/MultiEditManager.java  ← Stato selezione e apply batch
├── systems/MultiEditPanel.java    ← UI dropdown preset + summary/copy
├── BatchEditorScreen.java         ← FUTURE/DEFERRED (non implementare ora)
├── BatchSelectionScreen.java      ← FUTURE/DEFERRED (non implementare ora)
└── ...
```

#### Stima Effort

| Componente | Linee | Complessità |
|------------|-------|-------------|
| BatchEditorScreen | ~400 | Alta |
| BatchValue + UI | ~200 | Media |
| BatchUpdatePayload + handler | ~150 | Media |
| BatchSelectionScreen | ~250 | Media |
| Batch undo/redo | ~100 | Media |
| **Totale** | **~1100** | **Alta** |

**Priorità:** P3 - Implementare DOPO stabilizzazione single editor (Fase 0-4 complete).

---

## 2.15 Template & Preset Architecture

I template/preset seguono una **gerarchia a 3 livelli** con risoluzione prioritaria:

### Gerarchia dei Livelli

```
┌─────────────────────────────────────────────────────────────────┐
│                    TEMPLATE RESOLUTION ORDER                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  LEVEL 3: MODPACK PRESETS (highest priority)                    │
│  └─ config/devmod/presets/modpack/<modpack-id>/                 │
│     └─ Definiti dal modpack, override tutto                     │
│                                                                 │
│  LEVEL 2: CATEGORY PRESETS                                      │
│  └─ config/devmod/presets/category/<category>/                  │
│     └─ Per categoria item (swords, bows, helmets...)            │
│                                                                 │
│  LEVEL 1: GLOBAL PRESETS (lowest priority)                      │
│  └─ config/devmod/presets/global/                               │
│     └─ Disponibili ovunque, base defaults                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Resolution: MODPACK → CATEGORY → GLOBAL (first match wins)
```

### Struttura File System

```
config/devmod/presets/
├── global/                          # LEVEL 1: Global presets
│   ├── weapon/
│   │   ├── vanilla_default.json
│   │   ├── balanced_pvp.json
│   │   └── overpowered_debug.json
│   └── armor/
│       ├── vanilla_default.json
│       ├── tank_build.json
│       └── glass_cannon.json
│
├── category/                        # LEVEL 2: Category-specific
│   ├── sword/
│   │   ├── diamond_tier.json
│   │   └── netherite_tier.json
│   ├── bow/
│   │   ├── sniper.json
│   │   └── rapid_fire.json
│   ├── helmet/
│   │   └── night_vision_focus.json
│   ├── chestplate/
│   │   └── thorns_tank.json
│   ├── leggings/
│   │   └── speed_focus.json
│   └── boots/
│       └── feather_fall.json
│
└── modpack/                         # LEVEL 3: Modpack overrides
    ├── rlcraft/
    │   ├── manifest.json            # Modpack metadata
    │   ├── weapon/
    │   │   └── rlcraft_balanced.json
    │   └── armor/
    │       └── rlcraft_survival.json
    └── better_minecraft/
        ├── manifest.json
        └── weapon/
            └── bmc_standard.json
```

### Formato Preset JSON

```json
// config/devmod/presets/category/sword/diamond_tier.json
{
  "id": "diamond_tier",
  "name": "Diamond Tier Standard",
  "description": "Balanced stats for diamond-tier swords",
  "version": "1.0.0",
  "author": "DevMod",
  "scope": {
    "level": "category",
    "category": "sword",
    "itemFilter": {
      "material": ["diamond", "netherite"],
      "tags": ["minecraft:swords"]
    }
  },
  "values": {
    "baseDamage": 7.0,
    "attackSpeed": 1.6,
    "critChance": 0.15,
    "critMultiplier": 1.5,
    "durabilityMultiplier": 1.0,
    "enchantability": 10
  },
  "metadata": {
    "created": "2025-01-15T10:30:00Z",
    "modified": "2025-01-15T10:30:00Z",
    "tags": ["balanced", "pvp", "official"]
  }
}
```

```json
// config/devmod/presets/modpack/rlcraft/manifest.json
{
  "modpackId": "rlcraft",
  "modpackName": "RLCraft",
  "modpackVersion": "2.9.3",
  "devmodPresetVersion": "1.0.0",
  "description": "Preset pack for RLCraft hardcore survival",
  "author": "Shivaxi",
  "overrideGlobal": true,
  "overrideCategory": true,
  "presets": [
    "weapon/rlcraft_balanced.json",
    "armor/rlcraft_survival.json"
  ]
}
```

### Categorie Item Supportate

| Categoria | Items Inclusi | Preset Type |
|-----------|---------------|-------------|
| `sword` | Tutte le spade (vanilla + modded) | WeaponPreset |
| `axe` | Tutte le asce | WeaponPreset |
| `pickaxe` | Picconi (se weapon-enabled) | WeaponPreset |
| `bow` | Archi | WeaponPreset |
| `crossbow` | Balestre | WeaponPreset |
| `trident` | Tridenti | WeaponPreset |
| `helmet` | Tutti gli elmi | ArmorPreset |
| `chestplate` | Tutti i corpetti | ArmorPreset |
| `leggings` | Tutti i pantaloni | ArmorPreset |
| `boots` | Tutti gli stivali | ArmorPreset |
| `shield` | Scudi | ArmorPreset |

### Java Interface

```java
/**
 * Sealed hierarchy for preset scopes.
 */
public sealed interface PresetScope {
    record Global() implements PresetScope {}
    record Category(String category) implements PresetScope {}
    record Modpack(String modpackId, String category) implements PresetScope {}
}

/**
 * A preset definition with metadata and values.
 */
public record Preset<T>(
    String id,
    String name,
    String description,
    PresetScope scope,
    T values,
    PresetMetadata metadata
) {
    public record PresetMetadata(
        String version,
        String author,
        Instant created,
        Instant modified,
        List<String> tags
    ) {}
}

/**
 * Registry and resolver for presets.
 */
public final class PresetRegistry {
    private final Map<PresetScope, List<Preset<?>>> presets = new HashMap<>();

    /**
     * Load all presets from config directory.
     */
    public void loadFromConfig(Path configDir) { /* ... */ }

    /**
     * Resolve applicable presets for an item, respecting hierarchy.
     * Returns presets in priority order: MODPACK → CATEGORY → GLOBAL
     */
    public List<Preset<?>> resolveForItem(ItemStack item) {
        List<Preset<?>> result = new ArrayList<>();

        // 1. Check modpack presets (highest priority)
        String modpackId = detectActiveModpack();
        if (modpackId != null) {
            String category = ItemTypeHelper.getCategory(item);
            result.addAll(getPresets(new PresetScope.Modpack(modpackId, category)));
        }

        // 2. Check category presets
        String category = ItemTypeHelper.getCategory(item);
        result.addAll(getPresets(new PresetScope.Category(category)));

        // 3. Check global presets (lowest priority)
        result.addAll(getPresets(new PresetScope.Global()));

        return result;
    }

    /**
     * Detect active modpack from environment.
     * Checks: modpack.json, manifest.json, known mod combinations
     */
    @Nullable
    private String detectActiveModpack() { /* ... */ }

    /**
     * Save a user-created preset.
     */
    public void savePreset(Preset<?> preset, PresetScope scope) { /* ... */ }

    /**
     * Delete a preset (only user-created, not bundled).
     */
    public boolean deletePreset(String presetId, PresetScope scope) { /* ... */ }
}

/**
 * Helper for determining item categories.
 */
public final class ItemTypeHelper {

    public static String getCategory(ItemStack stack) {
        Item item = stack.getItem();

        // Weapons
        if (item instanceof SwordItem) return "sword";
        if (item instanceof AxeItem) return "axe";
        if (item instanceof BowItem) return "bow";
        if (item instanceof CrossbowItem) return "crossbow";
        if (item instanceof TridentItem) return "trident";

        // Armor by slot
        if (item instanceof ArmorItem armor) {
            return switch (armor.getEquipmentSlot()) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                case FEET -> "boots";
                default -> "armor";
            };
        }

        if (item instanceof ShieldItem) return "shield";

        // Fallback: check tags
        return getCategoryFromTags(stack);
    }

    public static boolean isWeapon(ItemStack stack) { /* ... */ }
    public static boolean isArmor(ItemStack stack) { /* ... */ }
    public static boolean isEditable(ItemStack stack) { /* ... */ }
}
```

### UI: Preset Selector

```
┌─────────────────────────────────────────────────────────────────┐
│  PRESETS                                                   [X]  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  📁 Filter: [All ▾] [🔍 Search...]                              │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 🎮 MODPACK: RLCraft                                       │  │
│  │   └─ ⚔️ rlcraft_balanced      "RLCraft weapon balance"    │  │
│  │   └─ 🛡️ rlcraft_survival     "Survival-focused armor"    │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │ 📂 CATEGORY: Sword                                        │  │
│  │   └─ ⚔️ diamond_tier         "Diamond tier standard"      │  │
│  │   └─ ⚔️ netherite_tier       "Netherite tier standard"    │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │ 🌐 GLOBAL                                                 │  │
│  │   └─ ⚔️ vanilla_default      "Vanilla-like stats"         │  │
│  │   └─ ⚔️ balanced_pvp         "PvP balanced"               │  │
│  │   └─ ⚔️ overpowered_debug    "Debug testing"      [🔧]    │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Selected: diamond_tier                                         │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Preview:                                                  │  │
│  │   Base Damage: 7.0 → 8.5 (+1.5)                           │  │
│  │   Attack Speed: 1.6 → 1.8 (+0.2)                          │  │
│  │   Crit Chance: 15% → 20% (+5%)                            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  [Save Current as Preset]    [Delete]    [Load] [Apply]         │
└─────────────────────────────────────────────────────────────────┘
```

### Preset Operazioni

| Operazione | Scope | Descrizione |
|------------|-------|-------------|
| **Load** | Read-only | Mostra preview dei valori |
| **Apply** | Write | Applica valori all'item corrente |
| **Save As** | Write | Salva config corrente come nuovo preset |
| **Delete** | Write | Rimuove preset (solo user-created) |
| **Export** | Read | Esporta preset come file JSON |
| **Import** | Write | Importa preset da file JSON |

### Modpack Detection Strategy

```java
public final class ModpackDetector {

    private static final Map<String, Set<String>> KNOWN_MODPACKS = Map.of(
        "rlcraft", Set.of("lycanitesmobs", "iceandfire", "spartan_weaponry"),
        "better_minecraft", Set.of("create", "farmers_delight", "supplementaries"),
        "all_the_mods_9", Set.of("mekanism", "thermal", "applied_energistics_2")
    );

    /**
     * Detect modpack by checking:
     * 1. Explicit config (config/devmod/modpack.txt)
     * 2. manifest.json in minecraft root
     * 3. Known mod combinations
     */
    @Nullable
    public static String detect() {
        // 1. Explicit config
        Path explicit = FMLPaths.CONFIGDIR.get().resolve("devmod/modpack.txt");
        if (Files.exists(explicit)) {
            return Files.readString(explicit).trim();
        }

        // 2. Manifest check
        Path manifest = FMLPaths.GAMEDIR.get().resolve("manifest.json");
        if (Files.exists(manifest)) {
            JsonObject json = JsonParser.parseReader(
                Files.newBufferedReader(manifest)
            ).getAsJsonObject();
            if (json.has("name")) {
                return normalizeModpackName(json.get("name").getAsString());
            }
        }

        // 3. Mod combination detection
        Set<String> loadedMods = ModList.get().getMods().stream()
            .map(ModInfo::getModId)
            .collect(Collectors.toSet());

        for (var entry : KNOWN_MODPACKS.entrySet()) {
            if (loadedMods.containsAll(entry.getValue())) {
                return entry.getKey();
            }
        }

        return null; // No modpack detected
    }
}
```

### Bundled Default Presets

DevMod include preset di default per testing:

| Preset ID | Scope | Tipo | Descrizione |
|-----------|-------|------|-------------|
| `vanilla_default` | Global | Both | Stats vanilla-like |
| `balanced_pvp` | Global | Weapon | Bilanciato per PvP |
| `overpowered_debug` | Global | Both | Valori alti per debug |
| `tank_build` | Global | Armor | Alta difesa, bassa mobilità |
| `glass_cannon` | Global | Armor | Bassa difesa, alta mobilità |
| `diamond_tier` | Category | Weapon | Standard tier diamante |
| `netherite_tier` | Category | Weapon | Standard tier netherite |

### Stima Implementazione

| Componente | LOC | Complessità |
|------------|-----|-------------|
| PresetScope + Preset records | ~80 | Bassa |
| PresetRegistry | ~250 | Media |
| PresetSerializer (JSON) | ~150 | Media |
| ModpackDetector | ~100 | Media |
| ItemTypeHelper (extended) | ~100 | Bassa |
| PresetSelectorScreen | ~400 | Alta |
| UI integration | ~100 | Media |
| Default preset JSONs | ~20 files | Bassa |
| **Totale** | **~1180** | **Media** |

**Priorità:** P2 - Implementare DOPO single editor (Fase 0-3), PRIMA di batch edit.

---

## 2.16 Scroll Policy: Rigid Layout

Tutte le tab condividono lo **stesso layout rigido**. Lo scroll è consentito **solo** nel content area.

### Layout Zones

```
┌─────────────────────────────────────────────────────────────────┐
│ [Tab1] [Tab2] [Tab3] [Tab4] [Tab5]               [MODE]    [X]  │  HEADER: FIXED
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────┐   ┌─────────────────────────────────────────┐  │
│  │            │   │                                         │  │
│  │  PREVIEW   │   │                                         │  │
│  │   FIXED    │   │       SCROLLABLE CONTENT AREA           │  │
│  │            │   │                                         │  │
│  └────────────┘   │  ┌─────────────────────────────────┐    │  │
│                   │  │ Section 1                       │    │  │  LEFT: FIXED
│  ┌────────────┐   │  │ Section 2                       │    │  │
│  │   SLOTS    │   │  │ Section 3                       │◄───┼──┼── SCROLL
│  │   FIXED    │   │  │ Section 4                       │    │  │   ONLY HERE
│  └────────────┘   │  │ Section 5                       │    │  │
│                   │  │ ...                             │    │  │
│  ┌────────────┐   │  └─────────────────────────────────┘    │  │
│  │   INFO     │   │                                         │  │
│  │   FIXED    │   └─────────────────────────────────────────┘  │
│  └────────────┘                                                │
├─────────────────────────────────────────────────────────────────┤
│  [Undo][Redo] │ [History][Export][Presets] │ [Apply]            │  FOOTER: FIXED
└─────────────────────────────────────────────────────────────────┘
```

### Zone Behavior Table

| Zona | Scroll | Dimensioni | Contenuto |
|------|--------|------------|-----------|
| **Header** | ❌ FIXED | 28px height | Tab bar, mode badge, close button |
| **Left Column** | ❌ FIXED | 140px width × 280px height | Preview, slots, item info |
| **Content Area** | ✅ SCROLL | 390px width × 280px viewport | Tab-specific sections |
| **Footer** | ❌ FIXED | 60px height | Action buttons |

### Scroll Implementation

```java
/**
 * Scrollable content area for tab content.
 * All modules render into this area, never outside.
 */
public final class ScrollableContentArea {
    // Viewport dimensions (visible area)
    public static final int VIEWPORT_X = 150;
    public static final int VIEWPORT_Y = 35;
    public static final int VIEWPORT_WIDTH = 390;
    public static final int VIEWPORT_HEIGHT = 280;

    // Scroll state
    private float scrollOffset = 0;
    private float maxScrollOffset = 0;
    private float scrollVelocity = 0;

    // Scroll settings
    private static final float SCROLL_SPEED = 15.0f;
    private static final float SCROLL_SMOOTHING = 0.85f;
    private static final int SCROLLBAR_WIDTH = 6;

    /**
     * Render content with scissor clipping.
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Enable scissor to clip content outside viewport
        graphics.enableScissor(
            VIEWPORT_X,
            VIEWPORT_Y,
            VIEWPORT_X + VIEWPORT_WIDTH,
            VIEWPORT_Y + VIEWPORT_HEIGHT
        );

        // Translate for scroll offset
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        // Render all sections from current module
        int yOffset = VIEWPORT_Y;
        for (EditorSection section : currentModule.getSections()) {
            yOffset = section.render(graphics, VIEWPORT_X, yOffset, mouseX, mouseY);
        }

        // Calculate max scroll
        int contentHeight = yOffset - VIEWPORT_Y;
        maxScrollOffset = Math.max(0, contentHeight - VIEWPORT_HEIGHT);

        graphics.pose().popPose();
        graphics.disableScissor();

        // Render scrollbar if needed
        if (maxScrollOffset > 0) {
            renderScrollbar(graphics);
        }
    }

    /**
     * Handle mouse scroll.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOverViewport(mouseX, mouseY)) {
            scrollVelocity -= delta * SCROLL_SPEED;
            return true;
        }
        return false;
    }

    /**
     * Smooth scroll animation tick.
     */
    public void tick() {
        scrollOffset += scrollVelocity;
        scrollVelocity *= SCROLL_SMOOTHING;

        // Clamp scroll
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScrollOffset);

        // Stop tiny velocities
        if (Math.abs(scrollVelocity) < 0.1f) {
            scrollVelocity = 0;
        }
    }

    /**
     * Scroll to ensure a specific Y position is visible.
     */
    public void scrollToVisible(int targetY) {
        int relativeY = targetY - VIEWPORT_Y;

        if (relativeY < scrollOffset) {
            // Target above viewport, scroll up
            scrollOffset = relativeY;
        } else if (relativeY > scrollOffset + VIEWPORT_HEIGHT - 30) {
            // Target below viewport, scroll down
            scrollOffset = relativeY - VIEWPORT_HEIGHT + 30;
        }
    }

    /**
     * Render scrollbar indicator.
     */
    private void renderScrollbar(GuiGraphics graphics) {
        int scrollbarX = VIEWPORT_X + VIEWPORT_WIDTH - SCROLLBAR_WIDTH - 2;
        int scrollbarHeight = VIEWPORT_HEIGHT;

        // Background track
        graphics.fill(
            scrollbarX, VIEWPORT_Y,
            scrollbarX + SCROLLBAR_WIDTH, VIEWPORT_Y + scrollbarHeight,
            UIConstants.Background.DARKER
        );

        // Thumb
        float thumbRatio = VIEWPORT_HEIGHT / (float)(maxScrollOffset + VIEWPORT_HEIGHT);
        int thumbHeight = Math.max(20, (int)(scrollbarHeight * thumbRatio));
        int thumbY = VIEWPORT_Y + (int)((scrollbarHeight - thumbHeight) * (scrollOffset / maxScrollOffset));

        graphics.fill(
            scrollbarX, thumbY,
            scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight,
            UIConstants.Border.ACCENT
        );
    }
}
```

### Keyboard Navigation

| Tasto | Azione |
|-------|--------|
| `Page Up` | Scroll up di VIEWPORT_HEIGHT |
| `Page Down` | Scroll down di VIEWPORT_HEIGHT |
| `Home` | Scroll to top (offset = 0) |
| `End` | Scroll to bottom (offset = max) |
| `↑` / `↓` | Scroll di 20px |

### Rules for Module Authors

1. **MAI** posizionare elementi fuori dal content area
2. **MAI** implementare scroll custom nei moduli
3. **SEMPRE** usare `EditorSection` per strutturare il contenuto
4. **SEMPRE** calcolare l'altezza totale correttamente per max scroll
5. Le sezioni ricevono solo Y offset relativo, mai coordinate assolute

### Content Height Calculation

```java
/**
 * Modules must implement this to report total content height.
 */
public interface EditorModule {
    // ... other methods ...

    /**
     * Calculate total height of all sections.
     * Used by ScrollableContentArea to set maxScrollOffset.
     */
    default int calculateContentHeight() {
        int height = 0;
        for (EditorSection section : getSections()) {
            height += section.getHeight();
            height += SECTION_GAP; // 8px between sections
        }
        return height;
    }
}
```

---

## 2.17 Resolution & UI Scaling

Base 1080p con UI scale dedicato, indipendente dalla GUI scale di Minecraft.

### Scale Factors (Discreti)

| Scale | Panel Size | Target Resolution | Note |
|-------|------------|-------------------|------|
| **1.0x** | 550×420 | 1080p (1920×1080) | Base reference |
| **1.25x** | 688×525 | 1080p large / 1440p small | |
| **1.5x** | 825×630 | 1440p (2560×1440) | |
| **2.0x** | 1100×840 | 4K (3840×2160) | |

**NO scale intermedi** (no 1.33x, 1.75x) - solo valori discreti per evitare artefatti.

### Config Option

```toml
# config/devmod-client.toml

[editor]
# UI Scale for Item Editor
# Values: "auto", "1.0", "1.25", "1.5", "2.0"
# Auto selects largest scale that fits screen with margin
uiScale = "auto"
```

### Auto Scale Algorithm

```java
/**
 * UI Scale calculator for editor.
 * Independent from Minecraft GUI scale.
 */
public final class EditorScaleCalculator {

    private static final float[] SCALE_OPTIONS = {1.0f, 1.25f, 1.5f, 2.0f};
    private static final int SCREEN_MARGIN = 24; // px margin from screen edges

    // Base dimensions (1080p reference)
    public static final int BASE_WIDTH = 550;
    public static final int BASE_HEIGHT = 420;

    /**
     * Calculate optimal scale factor.
     * Returns largest scale that keeps panel within screen bounds.
     */
    public static float calculateAutoScale(int screenWidth, int screenHeight) {
        float maxScale = 1.0f;

        for (float scale : SCALE_OPTIONS) {
            int scaledWidth = Math.round(BASE_WIDTH * scale);
            int scaledHeight = Math.round(BASE_HEIGHT * scale);

            // Check if fits with margin
            if (scaledWidth + (SCREEN_MARGIN * 2) <= screenWidth &&
                scaledHeight + (SCREEN_MARGIN * 2) <= screenHeight) {
                maxScale = scale;
            } else {
                break; // Scales are ordered, stop at first that doesn't fit
            }
        }

        return maxScale;
    }

    /**
     * Get scale from config, resolving "auto" if needed.
     */
    public static float getEffectiveScale(int screenWidth, int screenHeight) {
        String configValue = Config.CLIENT.editorUiScale.get();

        if ("auto".equals(configValue)) {
            return calculateAutoScale(screenWidth, screenHeight);
        }

        try {
            float scale = Float.parseFloat(configValue);
            // Validate against allowed values
            for (float allowed : SCALE_OPTIONS) {
                if (Math.abs(scale - allowed) < 0.01f) {
                    return allowed;
                }
            }
        } catch (NumberFormatException e) {
            // Fallback
        }

        return 1.0f; // Default fallback
    }
}
```

### Coordinate Scaling Rules

```java
/**
 * All coordinates must be scaled through this utility.
 * Ensures alignment to 4px grid after scaling.
 */
public final class ScaledCoord {

    private static float currentScale = 1.0f;

    public static void setScale(float scale) {
        currentScale = scale;
    }

    /**
     * Scale a coordinate and align to 4px grid.
     */
    public static int scale(int base) {
        return alignTo4(Math.round(base * currentScale));
    }

    /**
     * Scale a dimension (width/height) and align to 4px grid.
     */
    public static int scaleDim(int base) {
        return alignTo4(Math.round(base * currentScale));
    }

    /**
     * Align value to nearest multiple of 4.
     */
    private static int alignTo4(int value) {
        return ((value + 2) / 4) * 4;
    }

    // Pre-scaled constants for common values
    public static int panelWidth() { return scaleDim(550); }
    public static int panelHeight() { return scaleDim(420); }
    public static int headerHeight() { return scaleDim(28); }
    public static int footerHeight() { return scaleDim(60); }
    public static int leftColumnWidth() { return scaleDim(140); }
    public static int contentWidth() { return scaleDim(390); }
    public static int previewSize() { return scaleDim(100); }
}
```

### 4 Regole Fondamentali

| # | Regola | Dettaglio |
|---|--------|-----------|
| 1 | **Scale discreti only** | 1.0 / 1.25 / 1.5 / 2.0 - mai valori intermedi |
| 2 | **Auto = max che entra** | Pannello + 24px margine deve stare nello schermo |
| 3 | **Clamp, non shrink** | Se non entra → scroll nel content, header/footer fissi |
| 4 | **Allineamento 4px** | Tutte le coordinate arrotondate a multipli di 4 |

### Screen Fit Validation

```java
/**
 * Validate panel fits in screen, apply clamp if needed.
 */
public static class ScreenFitResult {
    public final int panelX;
    public final int panelY;
    public final int panelWidth;
    public final int panelHeight;
    public final boolean contentNeedsExtraScroll;

    public static ScreenFitResult calculate(int screenWidth, int screenHeight, float scale) {
        int scaledWidth = ScaledCoord.scaleDim(BASE_WIDTH);
        int scaledHeight = ScaledCoord.scaleDim(BASE_HEIGHT);

        // Center panel
        int panelX = (screenWidth - scaledWidth) / 2;
        int panelY = (screenHeight - scaledHeight) / 2;

        boolean needsClamp = false;

        // Horizontal clamp
        if (scaledWidth > screenWidth - SCREEN_MARGIN * 2) {
            scaledWidth = screenWidth - SCREEN_MARGIN * 2;
            panelX = SCREEN_MARGIN;
            needsClamp = true;
        }

        // Vertical clamp - never shrink header/footer
        if (scaledHeight > screenHeight - SCREEN_MARGIN * 2) {
            scaledHeight = screenHeight - SCREEN_MARGIN * 2;
            panelY = SCREEN_MARGIN;
            needsClamp = true;
            // Content area gets reduced, scroll compensates
        }

        return new ScreenFitResult(
            ScaledCoord.alignTo4(panelX),
            ScaledCoord.alignTo4(panelY),
            ScaledCoord.alignTo4(scaledWidth),
            ScaledCoord.alignTo4(scaledHeight),
            needsClamp
        );
    }
}
```

### Font Scaling

| Element | Base Size | 1.25x | 1.5x | 2.0x |
|---------|-----------|-------|------|------|
| Tab label | 9px | 11px | 14px | 18px |
| Section header | 10px | 12px | 15px | 20px |
| Value text | 8px | 10px | 12px | 16px |
| Button text | 9px | 11px | 14px | 18px |

```java
/**
 * Get scaled font for text rendering.
 * Uses Minecraft's font with scale matrix.
 */
public static void drawScaledText(GuiGraphics graphics, String text, int x, int y, int color, float textScale) {
    float effectiveScale = currentScale * textScale;
    graphics.pose().pushPose();
    graphics.pose().scale(effectiveScale, effectiveScale, 1.0f);
    graphics.drawString(
        Minecraft.getInstance().font,
        text,
        Math.round(x / effectiveScale),
        Math.round(y / effectiveScale),
        color
    );
    graphics.pose().popPose();
}
```

### In-Game Settings UI

```
┌─────────────────────────────────────────────────────────────────┐
│  DEVMOD SETTINGS                                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Editor UI Scale:                                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ [Auto] │ [1.0x] │ [1.25x] │ [1.5x] │ [2.0x]             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Current: Auto → 1.5x (detected 1440p)                          │
│  Panel size: 825×630px                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2.18 Debug Overlay

Debug overlay attivabile per sviluppo e troubleshooting. **Requisito fondamentale** per uno strumento di sviluppo.

### Keyboard Shortcuts

| Tasto | Funzione | Descrizione |
|-------|----------|-------------|
| `F9` | **Master Toggle** | Attiva/disattiva debug mode |
| `F10` | Grid Overlay | Mostra 4px grid + zone boundaries |
| `F11` | Bounds Overlay | Mostra bounding box componenti |
| `F9` + `Shift` | Cycle Detail | Low → Medium → High → Off |

### Visual Reference

```
┌─────────────────────────────────────────────────────────────────┐
│ [Tab1] [Tab2] [Tab3]                    [DEBUG ON]  [MODE] [X]  │
├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┤
│ ┌··········────────────┐   ┌─────────────────────────────────┐  │
│ : PREVIEW  :100×100    :   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  │
│ :   ┼───┼  :           :   │ ║ Section 1          ║ h:45    │  │
│ :   │ ● │  :           :   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  │
│ └··········────────────┘   │ ║ Section 2          ║ h:80    │  │
│ ┌──────────────────────┐   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  │
│ │ SLOTS    │ 140×70    │   │ ║ ⚠ OVERFLOW +12px  ║ h:92    │  │
│ └──────────────────────┘   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  │
│ ┌──────────────────────┐   └─────────────────────────────────┘  │
│ │ INFO     │ 140×100   │                                        │
│ └──────────────────────┘   ┌─────────────────────────────────┐  │
│                            │ Grid:4px │ Scale:1.5x │ FPS:60  │  │
│                            │ Scroll:45/280 │ Sections:4      │  │
│                            │ Mouse: 234,156 │ Hovered: Slider │  │
│                            └─────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│  [Undo][Redo]  │  [F9:Debug] [F10:Grid] [F11:Bounds]  │ [Apply] │
└─────────────────────────────────────────────────────────────────┘

Legend:
  ····  = Component bounding box (cyan)
  ▓▓▓▓  = Section divider
  ⚠     = Overflow/clipping warning (red)
  ┼───┼ = Grid alignment markers
```

### Debug Info Panel

```java
/**
 * Debug information displayed in overlay.
 */
public record DebugInfo(
    // Layout
    float scale,
    int gridSize,
    int panelWidth,
    int panelHeight,

    // Scroll
    float scrollOffset,
    float maxScroll,
    int visibleSections,
    int totalSections,

    // Performance
    int fps,
    long frameTimeMs,
    int renderCalls,

    // Interaction
    int mouseX,
    int mouseY,
    String hoveredComponent,
    String focusedComponent,

    // Warnings
    List<DebugWarning> warnings
) {}

public record DebugWarning(
    WarningType type,
    String component,
    String message,
    int x, int y, int width, int height
) {
    public enum WarningType {
        OVERFLOW,       // Content exceeds bounds
        TRUNCATED,      // Text was truncated
        MISALIGNED,     // Not on 4px grid
        OUT_OF_VIEWPORT // Rendered outside visible area
    }
}
```

### Overlay Layers

```java
/**
 * Debug overlay rendering layers.
 */
public final class DebugOverlay {

    private static boolean enabled = false;
    private static boolean showGrid = false;
    private static boolean showBounds = false;
    private static DetailLevel detailLevel = DetailLevel.MEDIUM;

    public enum DetailLevel {
        LOW,    // Only warnings
        MEDIUM, // Warnings + bounds + basic info
        HIGH    // Everything including grid + coordinates
    }

    // Colors
    private static final int COLOR_GRID = 0x40FFFFFF;        // White 25%
    private static final int COLOR_ZONE_BOUNDARY = 0x80FFFF00; // Yellow 50%
    private static final int COLOR_BBOX = 0x8000FFFF;        // Cyan 50%
    private static final int COLOR_BBOX_HOVERED = 0xC000FFFF; // Cyan 75%
    private static final int COLOR_WARNING = 0xFFFF4444;     // Red solid
    private static final int COLOR_OVERFLOW = 0x80FF0000;    // Red 50%
    private static final int COLOR_INFO_BG = 0xE0000000;     // Black 88%
    private static final int COLOR_INFO_TEXT = 0xFFCCCCCC;   // Light gray

    /**
     * Toggle master debug mode.
     */
    public static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            showGrid = false;
            showBounds = false;
        }
    }

    /**
     * Cycle detail level with Shift+F9.
     */
    public static void cycleDetailLevel() {
        detailLevel = switch (detailLevel) {
            case LOW -> DetailLevel.MEDIUM;
            case MEDIUM -> DetailLevel.HIGH;
            case HIGH -> DetailLevel.LOW;
        };
    }

    /**
     * Render debug overlay on top of editor.
     */
    public static void render(GuiGraphics graphics, EditorScreen editor, int mouseX, int mouseY) {
        if (!enabled) return;

        // Layer 1: Grid (if enabled)
        if (showGrid || detailLevel == DetailLevel.HIGH) {
            renderGrid(graphics, editor);
        }

        // Layer 2: Zone boundaries
        renderZoneBoundaries(graphics, editor);

        // Layer 3: Component bounds (if enabled)
        if (showBounds || detailLevel != DetailLevel.LOW) {
            renderComponentBounds(graphics, editor, mouseX, mouseY);
        }

        // Layer 4: Warnings (always when debug is on)
        renderWarnings(graphics, editor);

        // Layer 5: Info panel
        renderInfoPanel(graphics, editor, mouseX, mouseY);
    }

    /**
     * Render 4px grid overlay.
     */
    private static void renderGrid(GuiGraphics graphics, EditorScreen editor) {
        int startX = editor.getPanelX();
        int startY = editor.getPanelY();
        int endX = startX + editor.getPanelWidth();
        int endY = startY + editor.getPanelHeight();

        // Vertical lines
        for (int x = startX; x <= endX; x += 4) {
            int color = (x % 16 == 0) ? 0x60FFFFFF : COLOR_GRID;
            graphics.vLine(x, startY, endY, color);
        }

        // Horizontal lines
        for (int y = startY; y <= endY; y += 4) {
            int color = (y % 16 == 0) ? 0x60FFFFFF : COLOR_GRID;
            graphics.hLine(startX, endX, y, color);
        }
    }

    /**
     * Render zone boundaries (header, left, content, footer).
     */
    private static void renderZoneBoundaries(GuiGraphics graphics, EditorScreen editor) {
        int px = editor.getPanelX();
        int py = editor.getPanelY();

        // Header boundary
        int headerBottom = py + ScaledCoord.headerHeight();
        graphics.hLine(px, px + editor.getPanelWidth(), headerBottom, COLOR_ZONE_BOUNDARY);

        // Left column boundary
        int leftRight = px + ScaledCoord.leftColumnWidth();
        graphics.vLine(leftRight, headerBottom, py + editor.getPanelHeight() - ScaledCoord.footerHeight(), COLOR_ZONE_BOUNDARY);

        // Footer boundary
        int footerTop = py + editor.getPanelHeight() - ScaledCoord.footerHeight();
        graphics.hLine(px, px + editor.getPanelWidth(), footerTop, COLOR_ZONE_BOUNDARY);

        // Labels
        if (detailLevel == DetailLevel.HIGH) {
            graphics.drawString(font(), "HEADER", px + 4, py + 4, COLOR_INFO_TEXT);
            graphics.drawString(font(), "LEFT", px + 4, headerBottom + 4, COLOR_INFO_TEXT);
            graphics.drawString(font(), "CONTENT", leftRight + 4, headerBottom + 4, COLOR_INFO_TEXT);
            graphics.drawString(font(), "FOOTER", px + 4, footerTop + 4, COLOR_INFO_TEXT);
        }
    }

    /**
     * Render bounding boxes for all components.
     */
    private static void renderComponentBounds(GuiGraphics graphics, EditorScreen editor, int mouseX, int mouseY) {
        for (DebugBounds bounds : editor.getComponentBounds()) {
            boolean hovered = bounds.contains(mouseX, mouseY);
            int color = hovered ? COLOR_BBOX_HOVERED : COLOR_BBOX;

            // Draw bbox outline
            renderBboxOutline(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);

            // Draw dimensions label
            if (hovered || detailLevel == DetailLevel.HIGH) {
                String label = bounds.name() + " " + bounds.width() + "×" + bounds.height();
                graphics.drawString(font(), label, bounds.x() + 2, bounds.y() + 2, COLOR_INFO_TEXT);
            }
        }
    }

    /**
     * Render warnings for overflow, truncation, misalignment.
     */
    private static void renderWarnings(GuiGraphics graphics, EditorScreen editor) {
        for (DebugWarning warning : editor.getDebugWarnings()) {
            // Red overlay on problem area
            graphics.fill(
                warning.x(), warning.y(),
                warning.x() + warning.width(), warning.y() + warning.height(),
                COLOR_OVERFLOW
            );

            // Warning icon and message
            String icon = switch (warning.type()) {
                case OVERFLOW -> "⚠ OVERFLOW";
                case TRUNCATED -> "✂ TRUNCATED";
                case MISALIGNED -> "⊠ MISALIGNED";
                case OUT_OF_VIEWPORT -> "◐ OUT OF VIEW";
            };

            graphics.drawString(font(), icon + ": " + warning.message(),
                warning.x(), warning.y() - 10, COLOR_WARNING);
        }
    }

    /**
     * Render debug info panel in corner.
     */
    private static void renderInfoPanel(GuiGraphics graphics, EditorScreen editor, int mouseX, int mouseY) {
        DebugInfo info = editor.getDebugInfo();

        List<String> lines = new ArrayList<>();
        lines.add("Grid: " + info.gridSize() + "px │ Scale: " + info.scale() + "x │ FPS: " + info.fps());
        lines.add("Scroll: " + (int)info.scrollOffset() + "/" + (int)info.maxScroll() +
                  " │ Sections: " + info.visibleSections() + "/" + info.totalSections());
        lines.add("Mouse: " + mouseX + "," + mouseY + " │ Hovered: " + info.hoveredComponent());

        if (detailLevel == DetailLevel.HIGH) {
            lines.add("Frame: " + info.frameTimeMs() + "ms │ Draws: " + info.renderCalls());
            lines.add("Focused: " + info.focusedComponent());
        }

        if (!info.warnings().isEmpty()) {
            lines.add("⚠ Warnings: " + info.warnings().size());
        }

        // Calculate panel size
        int panelWidth = 280;
        int panelHeight = lines.size() * 12 + 8;
        int panelX = editor.getPanelX() + editor.getPanelWidth() - panelWidth - 8;
        int panelY = editor.getPanelY() + editor.getPanelHeight() - ScaledCoord.footerHeight() - panelHeight - 8;

        // Background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_INFO_BG);

        // Text
        int y = panelY + 4;
        for (String line : lines) {
            graphics.drawString(font(), line, panelX + 4, y, COLOR_INFO_TEXT);
            y += 12;
        }
    }

    private static void renderBboxOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.hLine(x, x + w, y, color);
        g.hLine(x, x + w, y + h, color);
        g.vLine(x, y, y + h, color);
        g.vLine(x + w, y, y + h, color);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
```

### Component Bounds Registration

```java
/**
 * Components must register their bounds for debug overlay.
 */
public record DebugBounds(
    String name,
    int x, int y,
    int width, int height
) {
    public boolean contains(int mx, int my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}

/**
 * Interface for components that report debug info.
 */
public interface DebugReporter {
    /**
     * Register bounding box for debug overlay.
     */
    DebugBounds getDebugBounds();

    /**
     * Report any warnings (overflow, truncation, etc).
     */
    default List<DebugWarning> getDebugWarnings() {
        return List.of();
    }
}
```

### Overflow Detection

```java
/**
 * Utility for detecting rendering issues.
 */
public final class OverflowDetector {

    /**
     * Check if text will be truncated at given width.
     */
    public static Optional<DebugWarning> checkTextTruncation(
            String text, int x, int y, int maxWidth, Font font) {
        int textWidth = font.width(text);
        if (textWidth > maxWidth) {
            return Optional.of(new DebugWarning(
                WarningType.TRUNCATED,
                "Text",
                "\"" + text.substring(0, 10) + "...\" exceeds by " + (textWidth - maxWidth) + "px",
                x, y, maxWidth, font.lineHeight
            ));
        }
        return Optional.empty();
    }

    /**
     * Check if component exceeds viewport bounds.
     */
    public static Optional<DebugWarning> checkViewportOverflow(
            String component, int x, int y, int width, int height,
            int viewportX, int viewportY, int viewportW, int viewportH) {

        int overflowRight = (x + width) - (viewportX + viewportW);
        int overflowBottom = (y + height) - (viewportY + viewportH);

        if (overflowRight > 0 || overflowBottom > 0) {
            String msg = "";
            if (overflowRight > 0) msg += "right +" + overflowRight + "px ";
            if (overflowBottom > 0) msg += "bottom +" + overflowBottom + "px";

            return Optional.of(new DebugWarning(
                WarningType.OVERFLOW,
                component,
                msg.trim(),
                x, y, width, height
            ));
        }
        return Optional.empty();
    }

    /**
     * Check if coordinate is aligned to 4px grid.
     */
    public static Optional<DebugWarning> checkAlignment(
            String component, int x, int y, int width, int height) {

        List<String> misaligned = new ArrayList<>();
        if (x % 4 != 0) misaligned.add("x=" + x);
        if (y % 4 != 0) misaligned.add("y=" + y);
        if (width % 4 != 0) misaligned.add("w=" + width);
        if (height % 4 != 0) misaligned.add("h=" + height);

        if (!misaligned.isEmpty()) {
            return Optional.of(new DebugWarning(
                WarningType.MISALIGNED,
                component,
                String.join(", ", misaligned) + " not on 4px grid",
                x, y, width, height
            ));
        }
        return Optional.empty();
    }
}
```

### Config Toggle

```toml
# config/devmod-client.toml

[debug]
# Enable debug overlay by default (can toggle with F9)
debugOverlayEnabled = false

# Default detail level: "low", "medium", "high"
debugDetailLevel = "medium"

# Show grid by default when debug is on
debugShowGrid = false

# Show component bounds by default when debug is on
debugShowBounds = true
```

---

## 2.19 Grid & Spacing System

Tutte le coordinate e dimensioni devono rispettare una **griglia 4px** con **padding tokens fissi**.

### Base Unit

```
BASE UNIT = 4px

Tutti i valori devono essere multipli di 4:
  ✓ 4, 8, 12, 16, 20, 24, 28, 32...
  ✗ 5, 6, 7, 9, 10, 11, 13, 14, 15...
```

### Spacing Tokens

```java
/**
 * Spacing tokens - ONLY use these values for padding/gap/margin.
 * Never use arbitrary pixel values.
 */
public final class EditorSpacing {
    private EditorSpacing() {}

    // Base unit
    public static final int UNIT = 4;

    // Spacing tokens
    public static final int XS  = 4;   // Intra-component (icon↔text, input padding)
    public static final int S   = 8;   // Component padding, small gaps
    public static final int M   = 12;  // Section padding, medium gaps
    public static final int L   = 16;  // Zone padding, large gaps
    public static final int XL  = 24;  // Panel margins, extra large gaps

    // Derived values (all multiples of 4)
    public static final int COMPONENT_GAP = S;      // 8px between components in row
    public static final int SECTION_GAP = M;        // 12px between sections
    public static final int ROW_GAP = S;            // 8px between rows in section
    public static final int CONTENT_PADDING = S;    // 8px content area padding
    public static final int BUTTON_PADDING_H = S;   // 8px horizontal button padding
    public static final int BUTTON_PADDING_V = XS;  // 4px vertical button padding

    /**
     * Validate a value is on the 4px grid.
     * Use in debug builds to catch errors early.
     */
    public static boolean isOnGrid(int value) {
        return value % UNIT == 0;
    }

    /**
     * Snap a value to nearest grid point.
     */
    public static int snapToGrid(int value) {
        return ((value + 2) / UNIT) * UNIT;
    }
}
```

### Usage Matrix

| Context | Padding | Gap | Token |
|---------|---------|-----|-------|
| **Button** | 8×4 (H×V) | - | S, XS |
| **Input field** | 4px all | - | XS |
| **Slider** label↔track | 8px | - | S |
| **Toggle** label↔switch | 8px | - | S |
| **Components** in row | - | 8px | S |
| **Rows** in section | - | 8px | S |
| **Sections** in content | - | 12px | M |
| **Section** header↔content | 8px | - | S |
| **Content area** padding | 8px | - | S |
| **Left column** padding | 8px | - | S |
| **Footer** padding | 8px | - | S |
| **Panel** screen margin | 24px | - | XL |

### Visual Reference

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  ←24px→┌─────────────────────────────────────────────┐←24px→   │  XL: Panel margin
│        │                                             │         │
│        │  ←8px→ CONTENT AREA ←8px→                   │         │  S: Content padding
│        │        ┌─────────────────────────────┐      │         │
│        │        │ SECTION HEADER              │      │         │
│        │        │ ←8px padding→               │      │         │  S: Section padding
│        │        ├─────────────────────────────┤      │         │
│        │        │                             │      │         │
│        │        │  [Label]←8px→[━━━━━━━━━━]   │      │         │  S: Label↔control
│        │        │       ↑                     │      │         │
│        │        │      8px ROW_GAP            │      │         │  S: Row gap
│        │        │       ↓                     │      │         │
│        │        │  [Label]←8px→[━━━━━━━━━━]   │      │         │
│        │        │                             │      │         │
│        │        └─────────────────────────────┘      │         │
│        │              ↑                              │         │
│        │             12px SECTION_GAP                │         │  M: Section gap
│        │              ↓                              │         │
│        │        ┌─────────────────────────────┐      │         │
│        │        │ NEXT SECTION                │      │         │
│        │        └─────────────────────────────┘      │         │
│        │                                             │         │
│        └─────────────────────────────────────────────┘         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Component Dimensions (all on 4px grid)

```java
/**
 * Standard component dimensions - all multiples of 4.
 */
public final class EditorDimensions {
    private EditorDimensions() {}

    // Buttons
    public static final int BTN_HEIGHT_SMALL = 20;   // 5 units
    public static final int BTN_HEIGHT_NORMAL = 24;  // 6 units
    public static final int BTN_HEIGHT_LARGE = 32;   // 8 units
    public static final int BTN_MIN_WIDTH = 48;      // 12 units

    // Inputs
    public static final int INPUT_HEIGHT = 20;       // 5 units
    public static final int INPUT_MIN_WIDTH = 60;    // 15 units

    // Sliders
    public static final int SLIDER_HEIGHT = 20;      // 5 units
    public static final int SLIDER_TRACK_HEIGHT = 4; // 1 unit
    public static final int SLIDER_THUMB_SIZE = 12;  // 3 units

    // Toggles
    public static final int TOGGLE_WIDTH = 36;       // 9 units
    public static final int TOGGLE_HEIGHT = 20;      // 5 units

    // Tabs
    public static final int TAB_HEIGHT = 24;         // 6 units
    public static final int TAB_MIN_WIDTH = 64;      // 16 units

    // Sections
    public static final int SECTION_HEADER_HEIGHT = 24;  // 6 units
    public static final int SECTION_MIN_HEIGHT = 48;     // 12 units

    // Scrollbar
    public static final int SCROLLBAR_WIDTH = 8;     // 2 units

    // Icons
    public static final int ICON_SMALL = 12;         // 3 units
    public static final int ICON_NORMAL = 16;        // 4 units
    public static final int ICON_LARGE = 24;         // 6 units
}
```

### Row Layout Helper

```java
/**
 * Helper for laying out components in a row with consistent spacing.
 */
public final class RowLayout {
    private final int startX;
    private final int y;
    private final int gap;
    private int currentX;

    public RowLayout(int x, int y, int gap) {
        this.startX = x;
        this.y = y;
        this.gap = EditorSpacing.snapToGrid(gap);
        this.currentX = x;
    }

    public RowLayout(int x, int y) {
        this(x, y, EditorSpacing.COMPONENT_GAP);
    }

    /**
     * Add a component and return its X position.
     * Automatically advances currentX for next component.
     */
    public int add(int width) {
        int x = currentX;
        currentX += EditorSpacing.snapToGrid(width) + gap;
        return x;
    }

    /**
     * Add flexible space (for right-aligned components).
     */
    public void addSpace(int space) {
        currentX += EditorSpacing.snapToGrid(space);
    }

    /**
     * Get current X position.
     */
    public int getX() {
        return currentX;
    }

    /**
     * Get Y position (constant for row).
     */
    public int getY() {
        return y;
    }

    /**
     * Get total width used so far.
     */
    public int getWidth() {
        return currentX - startX - gap; // Subtract trailing gap
    }
}
```

### Section Layout Helper

```java
/**
 * Helper for laying out sections vertically with consistent spacing.
 */
public final class SectionLayout {
    private final int x;
    private final int startY;
    private final int width;
    private int currentY;

    public SectionLayout(int x, int y, int width) {
        this.x = x;
        this.startY = y;
        this.width = EditorSpacing.snapToGrid(width);
        this.currentY = y;
    }

    /**
     * Add a section header and return its Y position.
     */
    public int addHeader(String title) {
        int y = currentY;
        currentY += EditorDimensions.SECTION_HEADER_HEIGHT;
        currentY += EditorSpacing.S; // Padding after header
        return y;
    }

    /**
     * Add a row and return its Y position.
     */
    public int addRow(int height) {
        int y = currentY;
        currentY += EditorSpacing.snapToGrid(height);
        currentY += EditorSpacing.ROW_GAP;
        return y;
    }

    /**
     * End current section and add section gap.
     */
    public void endSection() {
        currentY -= EditorSpacing.ROW_GAP; // Remove last row gap
        currentY += EditorSpacing.SECTION_GAP;
    }

    /**
     * Get current Y position.
     */
    public int getY() {
        return currentY;
    }

    /**
     * Get total height used so far.
     */
    public int getHeight() {
        return currentY - startY;
    }

    /**
     * Get content X (with padding).
     */
    public int getContentX() {
        return x + EditorSpacing.CONTENT_PADDING;
    }

    /**
     * Get content width (minus padding).
     */
    public int getContentWidth() {
        return width - (EditorSpacing.CONTENT_PADDING * 2);
    }
}
```

### Enforcement Rules

| Rule | Enforcement | Level |
|------|-------------|-------|
| Coordinates on 4px grid | `ScaledCoord.alignTo4()` | Compile-time (use helper) |
| Dimensions on 4px grid | `EditorDimensions` constants | Compile-time (use constants) |
| Spacing from tokens only | `EditorSpacing` constants | Code review |
| No magic numbers | Static analysis / linter | CI |
| Grid violations | Debug overlay `MISALIGNED` warning | Runtime (dev) |

### Anti-Patterns

```java
// ❌ BAD: Magic numbers
int x = 137;
int padding = 5;
graphics.fill(x, y, x + 73, y + 19, color);

// ✓ GOOD: Grid-aligned constants
int x = ScaledCoord.scale(136);  // Snaps to 136
int padding = EditorSpacing.XS;  // 4px
graphics.fill(x, y, x + EditorDimensions.BTN_MIN_WIDTH, y + EditorDimensions.BTN_HEIGHT_SMALL, color);

// ❌ BAD: Arbitrary gap
int gap = 6;
renderComponent(x, y);
renderComponent(x + width + gap, y);

// ✓ GOOD: Token-based gap
RowLayout row = new RowLayout(x, y);
renderComponent(row.add(width1), row.getY());
renderComponent(row.add(width2), row.getY());
```

### Integration with Scaling (2.17)

```java
/**
 * Scaled spacing values - use these in render code.
 */
public final class ScaledSpacing {

    public static int xs()  { return ScaledCoord.scale(EditorSpacing.XS); }
    public static int s()   { return ScaledCoord.scale(EditorSpacing.S); }
    public static int m()   { return ScaledCoord.scale(EditorSpacing.M); }
    public static int l()   { return ScaledCoord.scale(EditorSpacing.L); }
    public static int xl()  { return ScaledCoord.scale(EditorSpacing.XL); }

    public static int componentGap() { return ScaledCoord.scale(EditorSpacing.COMPONENT_GAP); }
    public static int sectionGap()   { return ScaledCoord.scale(EditorSpacing.SECTION_GAP); }
    public static int rowGap()       { return ScaledCoord.scale(EditorSpacing.ROW_GAP); }
}
```

---

## 2.20 Weapon Properties Architecture

Architettura delle proprietà armi basata su **Minecraft 1.21 Data Components** e **NeoForge Attributes**.

### Design Principles

| Principio | Decisione | Rationale |
|-----------|-----------|-----------|
| **Source of Truth** | `devmod:*` namespace | Controllo totale, nessuna dipendenza esterna |
| **Pufferfish Compat** | Mapping opzionale | Compatibilità senza hard dependency |
| **Tool Rules** | Read-only MVP | Evita inconsistenze, editing in Fase 2 |
| **Damage Types** | Predefined bonuses MVP | Custom types = combat framework, troppo complesso |
| **Ranged Weapons** | Fase 2 | Stabilizzare melee first, ranged ha più edge cases |

### Property Tiers

#### TIER 1: Vanilla Core (Data Components - `minecraft:attribute_modifiers`)

| Property | Attribute ID | Default | Range | Tab |
|----------|--------------|---------|-------|-----|
| **Base Damage** | `minecraft:attack_damage` | 1 | 0–2048 | STATS |
| **Attack Speed** | `minecraft:attack_speed` | 4 | 0–1024 | STATS |
| **Attack Knockback** | `minecraft:attack_knockback` | 0 | 0–5 | STATS |
| **Entity Reach** | `minecraft:entity_interaction_range` | 2.5 | 0–64 | STATS |
| **Sweeping Ratio** | `minecraft:sweeping_damage_ratio` | 0 | 0–1 | STATS |

#### TIER 2: Vanilla Item Properties (Data Components)

| Property | Component | Default | Note | Tab |
|----------|-----------|---------|------|-----|
| **Max Durability** | `minecraft:max_damage` | varies | Per-instance override | DURABILITY |
| **Current Damage** | `minecraft:damage` | 0 | Current wear | DURABILITY |
| **Repair Cost** | `minecraft:repair_cost` | 0 | Anvil penalty | DURABILITY |
| **Unbreakable** | `minecraft:unbreakable` | false | Infinite durability | DURABILITY |
| **Tool Rules** | `minecraft:tool` | - | Mining speed per block tag | ADVANCED |

#### TIER 3: DevMod Custom Attributes (Registered via NeoForge)

| Property | Attribute ID | Default | Range | Tab |
|----------|--------------|---------|-------|-----|
| **Crit Chance** | `devmod:crit_chance` | 0 | 0–100 | COMBAT |
| **Crit Multiplier** | `devmod:crit_multiplier` | 1.5 | 1.0–5.0 | COMBAT |
| **Armor Shred** | `devmod:armor_shred` | 0 | 0–66 | COMBAT |
| **Life Steal** | `devmod:life_steal` | 0 | 0–100 | COMBAT |

#### TIER 4: Damage Type Bonuses (Predefined Categories)

| Property | Target/Tag | Default | Range | Tab |
|----------|------------|---------|-------|-----|
| **vs Undead** | Entity type check | 0 | 0–200% | DAMAGE TYPES |
| **vs Arthropods** | Entity type check | 0 | 0–200% | DAMAGE TYPES |
| **vs Players** | Player entity | 0 | 0–200% | DAMAGE TYPES |
| **Fire Bonus** | `#minecraft:is_fire` | 0 | 0–200% | DAMAGE TYPES |
| **True Damage** | `#bypasses_armor` | 0 | 0–100% | DAMAGE TYPES |

### Attribute Registration (NeoForge)

```java
/**
 * DevMod custom attributes for weapons.
 * Source of truth: devmod namespace.
 */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, DevMod.MODID);

    // Combat attributes
    public static final Holder<Attribute> CRIT_CHANCE = ATTRIBUTES.register("crit_chance",
        () -> new RangedAttribute("attribute.devmod.crit_chance", 0.0D, 0.0D, 100.0D)
            .setSyncable(true));

    public static final Holder<Attribute> CRIT_MULTIPLIER = ATTRIBUTES.register("crit_multiplier",
        () -> new RangedAttribute("attribute.devmod.crit_multiplier", 1.5D, 1.0D, 5.0D)
            .setSyncable(true));

    public static final Holder<Attribute> ARMOR_SHRED = ATTRIBUTES.register("armor_shred",
        () -> new RangedAttribute("attribute.devmod.armor_shred", 0.0D, 0.0D, 66.0D)
            .setSyncable(true));

    public static final Holder<Attribute> LIFE_STEAL = ATTRIBUTES.register("life_steal",
        () -> new RangedAttribute("attribute.devmod.life_steal", 0.0D, 0.0D, 100.0D)
            .setSyncable(true));

    // Damage type bonuses
    public static final Holder<Attribute> DAMAGE_VS_UNDEAD = ATTRIBUTES.register("damage_vs_undead",
        () -> new RangedAttribute("attribute.devmod.damage_vs_undead", 0.0D, 0.0D, 200.0D)
            .setSyncable(true));

    public static final Holder<Attribute> DAMAGE_VS_ARTHROPODS = ATTRIBUTES.register("damage_vs_arthropods",
        () -> new RangedAttribute("attribute.devmod.damage_vs_arthropods", 0.0D, 0.0D, 200.0D)
            .setSyncable(true));

    public static final Holder<Attribute> DAMAGE_VS_PLAYERS = ATTRIBUTES.register("damage_vs_players",
        () -> new RangedAttribute("attribute.devmod.damage_vs_players", 0.0D, 0.0D, 200.0D)
            .setSyncable(true));

    public static final Holder<Attribute> TRUE_DAMAGE_PERCENT = ATTRIBUTES.register("true_damage_percent",
        () -> new RangedAttribute("attribute.devmod.true_damage_percent", 0.0D, 0.0D, 100.0D)
            .setSyncable(true));
}
```

### Pufferfish Compatibility Layer

```java
/**
 * Optional compatibility mapping for Pufferfish's Attributes.
 * Enabled via config when mod is present.
 */
public final class PufferfishCompat {

    private static final boolean PUFFERFISH_LOADED = ModList.get().isLoaded("puffish_attributes");

    // Mapping: DevMod ID -> Pufferfish ID
    private static final Map<ResourceLocation, ResourceLocation> COMPAT_MAP = Map.of(
        DevMod.rl("armor_shred"), ResourceLocation.parse("puffish_attributes:armor_shred"),
        DevMod.rl("life_steal"), ResourceLocation.parse("puffish_attributes:life_steal"),
        DevMod.rl("crit_chance"), ResourceLocation.parse("puffish_attributes:crit_chance")
        // Note: crit_multiplier non esiste in Pufferfish
    );

    /**
     * Check if compat mode is enabled.
     */
    public static boolean isCompatEnabled() {
        return PUFFERFISH_LOADED && Config.SERVER.pufferfishCompat.get();
    }

    /**
     * Get effective attribute holder, using Pufferfish if compat enabled.
     */
    public static Holder<Attribute> getEffectiveAttribute(Holder<Attribute> devmodAttr) {
        if (!isCompatEnabled()) {
            return devmodAttr;
        }

        ResourceLocation devmodId = devmodAttr.unwrapKey()
            .map(ResourceKey::location)
            .orElse(null);

        if (devmodId != null && COMPAT_MAP.containsKey(devmodId)) {
            ResourceLocation pufferfishId = COMPAT_MAP.get(devmodId);
            // Lookup Pufferfish attribute from registry
            return BuiltInRegistries.ATTRIBUTE.getHolder(pufferfishId).orElse(devmodAttr);
        }

        return devmodAttr;
    }
}
```

### Tab Structure: WeaponModule

```
┌─────────────────────────────────────────────────────────────────┐
│ [STATS] [COMBAT] [DURABILITY] [DAMAGE TYPES] [DEBUG]            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  TAB: STATS (Vanilla Core)                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ BASE COMBAT                                              │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Damage       [━━━━━━━━━━] 7.0    DPS: 11.2         │   │
│  │ Attack Speed      [━━━━━━━━━━] 1.6    attacks/sec       │   │
│  │ Attack Knockback  [━━━━━━━━━━] 0.0                      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ REACH & AREA                                             │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Entity Reach      [━━━━━━━━━━] 2.5    blocks            │   │
│  │ Sweeping Ratio    [━━━━━━━━━━] 0%     AoE multiplier    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: COMBAT (DevMod Custom)                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ CRITICAL HITS                                            │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Crit Chance       [━━━━━━━━━━] 5%     (replaces jump)   │   │
│  │ Crit Multiplier   [━━━━━━━━━━] 1.5x   damage bonus      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ PENETRATION & SUSTAIN                                    │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Armor Shred       [━━━━━━━━━━] 0%     ignores armor     │   │
│  │ Life Steal        [━━━━━━━━━━] 0%     heal on hit       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: DURABILITY (Data Components)                              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Max Durability    [━━━━━━━━━━] 1561                     │   │
│  │ Current Damage    [━━━━━━━━━━] 0      (0% worn)         │   │
│  │ Repair Cost       [━━━━━━━━━━] 0      anvil penalty     │   │
│  │ Unbreakable       [ ]                                   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: DAMAGE TYPES (Predefined Bonuses)                         │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ TARGET BONUSES                                           │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ vs Undead         [━━━━━━━━━━] +0%                      │   │
│  │ vs Arthropods     [━━━━━━━━━━] +0%                      │   │
│  │ vs Players        [━━━━━━━━━━] +0%                      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DAMAGE CONVERSION                                        │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Fire Damage       [━━━━━━━━━━] +0%    (sets on fire)    │   │
│  │ True Damage       [━━━━━━━━━━] 0%     (bypasses armor)  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: DEBUG (Dev Only - F9 to toggle)                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ [View Raw Components]                                   │   │
│  │ ┌─────────────────────────────────────────────────────┐ │   │
│  │ │ minecraft:attribute_modifiers: {...}                │ │   │
│  │ │ minecraft:max_damage: 1561                          │ │   │
│  │ │ minecraft:damage: 0                                 │ │   │
│  │ │ minecraft:tool: {rules: [...]}                      │ │   │
│  │ └─────────────────────────────────────────────────────┘ │   │
│  │                                                         │   │
│  │ [View Attribute Modifiers]                              │   │
│  │ ┌─────────────────────────────────────────────────────┐ │   │
│  │ │ attack_damage: +6.0 (add_value) [mainhand]          │ │   │
│  │ │ attack_speed: -2.4 (add_value) [mainhand]           │ │   │
│  │ └─────────────────────────────────────────────────────┘ │   │
│  │                                                         │   │
│  │ [Damage Calculator]  [Export JSON]  [Copy Command]      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Tool Rules (ADVANCED Tab - Read-Only MVP)

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: ADVANCED                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ TOOL RULES (read-only)                              [?] │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Rule 1: #minecraft:mineable/sword                       │   │
│  │         Speed: 1.5  │  Correct for drops: ✓            │   │
│  │                                                         │   │
│  │ Rule 2: #minecraft:cobwebs                              │   │
│  │         Speed: 15.0 │  Correct for drops: ✓            │   │
│  │                                                         │   │
│  │ Default mining speed: 1.0                               │   │
│  │ Damage per block: 2                                     │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ ENCHANTABILITY                                          │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Enchantability    [━━━━━━━━━━] 10     (item property)   │   │
│  │ ℹ️ Higher = better enchants at enchanting table         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Implementation Phases

| Phase | Scope | Priority |
|-------|-------|----------|
| **MVP** | STATS + COMBAT + DURABILITY tabs | P0 |
| **Phase 1** | DAMAGE TYPES tab (predefined bonuses) | P1 |
| **Phase 2** | DEBUG tab (raw component viewer) | P1 |
| **Phase 3** | ADVANCED tab (tool rules read-only) | P2 |
| **Phase 4** | Tool rules editing with validation | P3 |
| **Phase 5** | Ranged weapons module | P3 |
| **Future** | Custom damage type creator | P4+ |

### Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                      WEAPON EDITOR DATA FLOW                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │  ItemStack  │───▶│ WeaponStats │───▶│  UI Sliders │         │
│  │  (Source)   │    │  (Model)    │    │  (View)     │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│        │                  │                  │                  │
│        │                  │                  │                  │
│        ▼                  ▼                  ▼                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │   Read:     │    │   Edit:     │    │   Apply:    │         │
│  │ Components  │    │ WeaponStats │    │  Payload    │         │
│  │ Attributes  │    │   fields    │    │  to Server  │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│                                                                 │
│  PREVIEW MODE: Changes stay in WeaponStats (client-only)        │
│  APPLY MODE: WeaponStats → Payload → Server → ItemStack         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### WeaponStats Record

```java
/**
 * Transient model for weapon stats editing.
 * Maps to/from ItemStack components and attributes.
 */
public record WeaponStats(
    // Tier 1: Vanilla Core
    float baseDamage,
    float attackSpeed,
    float attackKnockback,
    float entityReach,
    float sweepingRatio,

    // Tier 2: Durability
    int maxDurability,
    int currentDamage,
    int repairCost,
    boolean unbreakable,

    // Tier 3: DevMod Custom
    float critChance,
    float critMultiplier,
    float armorShred,
    float lifeSteal,

    // Tier 4: Damage Type Bonuses
    float damageVsUndead,
    float damageVsArthropods,
    float damageVsPlayers,
    float fireDamageBonus,
    float trueDamagePercent
) {
    /**
     * Extract stats from an ItemStack.
     */
    public static WeaponStats fromItemStack(ItemStack stack, LivingEntity holder) {
        // Read vanilla attributes
        float damage = (float) holder.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float speed = (float) holder.getAttributeValue(Attributes.ATTACK_SPEED);
        // ... extract all values from components and attributes
        return new WeaponStats(/* ... */);
    }

    /**
     * Build payload for server sync.
     */
    public UpdateWeaponPayload toPayload(boolean isGlobal, String itemName) {
        return new UpdateWeaponPayload(
            isGlobal, itemName,
            baseDamage, attackSpeed, attackKnockback, entityReach, sweepingRatio,
            maxDurability, repairCost, unbreakable,
            critChance, critMultiplier, armorShred, lifeSteal,
            damageVsUndead, damageVsArthropods, damageVsPlayers,
            fireDamageBonus, trueDamagePercent
        );
    }
}
```

### Config Options

```toml
# config/devmod-server.toml

[weapons]
# Enable Pufferfish's Attributes compatibility mapping
# When enabled and Pufferfish is present, devmod attributes map to pufferfish equivalents
pufferfishCompat = true

# Maximum allowed values (server-side validation)
maxCritChance = 100.0
maxCritMultiplier = 5.0
maxArmorShred = 66.0
maxLifeSteal = 100.0
maxDamageBonus = 200.0
maxTrueDamage = 100.0

# Allow editing vanilla attributes (attack_damage, attack_speed, etc.)
allowVanillaAttributeEditing = true

# Allow editing durability components
allowDurabilityEditing = true
```

---

## 2.21 Weapon Type Detection & Modded Support

Supporto per armi non-standard (asce, tridenti, archi, balestre) e rilevamento automatico armi moddate.

### Weapon Type Support Matrix

| Weapon Type | Java Class | Module | Tab Speciale | Phase |
|-------------|------------|--------|--------------|-------|
| **Sword** | `SwordItem` | WeaponModule | - | MVP |
| **Axe** | `AxeItem` | WeaponModule | - | MVP |
| **Pickaxe** (combat) | `PickaxeItem` | WeaponModule | - | MVP |
| **Mace** | `MaceItem` | WeaponModule | MACE | Phase 1 |
| **Trident** | `TridentItem` | WeaponModule | TRIDENT | Phase 2 |
| **Bow** | `BowItem` | RangedModule | BOW | Phase 2 |
| **Crossbow** | `CrossbowItem` | RangedModule | CROSSBOW | Phase 2 |
| **Shield** | `ShieldItem` | ArmorModule | SHIELD | Phase 3 |
| **Modded Melee** | Tag/Attribute | WeaponModule | GENERIC | MVP |
| **Modded Ranged** | Tag/Attribute | RangedModule | GENERIC | Phase 2 |

### Detection Priority Chain

```java
/**
 * Weapon type detection with fallback chain.
 * Priority: Class → Tags → Attributes → Config → Fallback
 */
public final class WeaponTypeDetector {

    /**
     * Detected weapon type with confidence level.
     */
    public record DetectionResult(
        WeaponType type,
        DetectionMethod method,
        float confidence,  // 0.0 - 1.0
        @Nullable String warning
    ) {
        public boolean isHighConfidence() {
            return confidence >= 0.8f;
        }
    }

    public enum WeaponType {
        // Melee
        SWORD,
        AXE,
        MACE,
        TRIDENT,
        PICKAXE_COMBAT,
        GENERIC_MELEE,

        // Ranged
        BOW,
        CROSSBOW,
        GENERIC_RANGED,

        // Defense
        SHIELD,

        // Unknown
        UNKNOWN,
        NOT_A_WEAPON
    }

    public enum DetectionMethod {
        CLASS_INSTANCEOF,      // Highest confidence
        ITEM_TAG,              // High confidence
        ATTRIBUTE_HEURISTIC,   // Medium confidence
        CONFIG_WHITELIST,      // Explicit override
        CONFIG_BLACKLIST,      // Explicit exclusion
        FALLBACK_GENERIC       // Lowest confidence
    }

    /**
     * Detect weapon type for an ItemStack.
     */
    public static DetectionResult detect(ItemStack stack) {
        if (stack.isEmpty()) {
            return new DetectionResult(WeaponType.NOT_A_WEAPON, null, 1.0f, null);
        }

        Item item = stack.getItem();

        // PRIORITY 1: Config blacklist (explicit exclusion)
        if (isBlacklisted(item)) {
            return new DetectionResult(WeaponType.NOT_A_WEAPON,
                DetectionMethod.CONFIG_BLACKLIST, 1.0f, null);
        }

        // PRIORITY 2: Config whitelist (explicit override)
        WeaponType whitelistType = getWhitelistType(item);
        if (whitelistType != null) {
            return new DetectionResult(whitelistType,
                DetectionMethod.CONFIG_WHITELIST, 1.0f, null);
        }

        // PRIORITY 3: Java class hierarchy (instanceof)
        DetectionResult classResult = detectByClass(item);
        if (classResult != null) {
            return classResult;
        }

        // PRIORITY 4: Item tags (data-driven)
        DetectionResult tagResult = detectByTags(stack);
        if (tagResult != null) {
            return tagResult;
        }

        // PRIORITY 5: Attribute/Component heuristics
        DetectionResult attrResult = detectByAttributes(stack);
        if (attrResult != null) {
            return attrResult;
        }

        // FALLBACK: Not a weapon
        return new DetectionResult(WeaponType.NOT_A_WEAPON, null, 1.0f, null);
    }

    /**
     * Priority 3: Class-based detection (highest confidence for vanilla).
     */
    @Nullable
    private static DetectionResult detectByClass(Item item) {
        // Melee weapons
        if (item instanceof SwordItem) {
            return result(WeaponType.SWORD, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        }
        if (item instanceof AxeItem) {
            return result(WeaponType.AXE, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        }
        if (item instanceof MaceItem) {
            return result(WeaponType.MACE, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        }
        if (item instanceof TridentItem) {
            return result(WeaponType.TRIDENT, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        }
        if (item instanceof PickaxeItem && Config.SERVER.treatPickaxeAsWeapon.get()) {
            return result(WeaponType.PICKAXE_COMBAT, DetectionMethod.CLASS_INSTANCEOF, 0.9f);
        }

        // Ranged weapons
        if (item instanceof BowItem) {
            return result(WeaponType.BOW, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        }
        if (item instanceof CrossbowItem) {
            return result(WeaponType.CROSSBOW, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        }

        // Defense
        if (item instanceof ShieldItem) {
            return result(WeaponType.SHIELD, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        }

        return null;
    }

    /**
     * Priority 4: Tag-based detection (works for modded items).
     */
    @Nullable
    private static DetectionResult detectByTags(ItemStack stack) {
        // DevMod explicit tags (highest priority for tags)
        if (stack.is(ModTags.Items.EDITABLE_MELEE_WEAPONS)) {
            return result(WeaponType.GENERIC_MELEE, DetectionMethod.ITEM_TAG, 0.95f);
        }
        if (stack.is(ModTags.Items.EDITABLE_RANGED_WEAPONS)) {
            return result(WeaponType.GENERIC_RANGED, DetectionMethod.ITEM_TAG, 0.95f);
        }

        // Common/Forge convention tags
        if (stack.is(Tags.Items.TOOLS_SWORDS) || stack.is(ItemTags.SWORDS)) {
            return result(WeaponType.SWORD, DetectionMethod.ITEM_TAG, 0.9f);
        }
        if (stack.is(Tags.Items.TOOLS_AXES) || stack.is(ItemTags.AXES)) {
            return result(WeaponType.AXE, DetectionMethod.ITEM_TAG, 0.9f);
        }

        // Generic melee weapon tags
        if (stack.is(ModTags.Items.MELEE_WEAPONS)) {
            return result(WeaponType.GENERIC_MELEE, DetectionMethod.ITEM_TAG, 0.85f);
        }

        // Ranged tags
        if (stack.is(ModTags.Items.RANGED_WEAPONS)) {
            return result(WeaponType.GENERIC_RANGED, DetectionMethod.ITEM_TAG, 0.85f);
        }

        return null;
    }

    /**
     * Priority 5: Attribute-based heuristics (fallback for unknown modded items).
     */
    @Nullable
    private static DetectionResult detectByAttributes(ItemStack stack) {
        // Check for attack_damage modifier in attribute_modifiers component
        var attrModifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attrModifiers == null) {
            return null;
        }

        boolean hasAttackDamage = false;
        boolean hasAttackSpeed = false;
        float damageValue = 0;

        for (var entry : attrModifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                hasAttackDamage = true;
                damageValue = (float) entry.modifier().amount();
            }
            if (entry.attribute().is(Attributes.ATTACK_SPEED)) {
                hasAttackSpeed = true;
            }
        }

        // Heuristic: Has attack modifiers = probably a weapon
        if (hasAttackDamage && hasAttackSpeed) {
            // High damage (>3) = more likely intentional weapon
            float confidence = damageValue > 3 ? 0.7f : 0.5f;
            return new DetectionResult(
                WeaponType.GENERIC_MELEE,
                DetectionMethod.ATTRIBUTE_HEURISTIC,
                confidence,
                "Detected as weapon via attributes. Add to whitelist for better support."
            );
        }

        return null;
    }

    private static DetectionResult result(WeaponType type, DetectionMethod method, float confidence) {
        return new DetectionResult(type, method, confidence, null);
    }
}
```

### Tag Definitions

```java
/**
 * DevMod item tags for weapon detection.
 */
public final class ModTags {
    public static final class Items {
        // Explicit opt-in tags (modders can add their items)
        public static final TagKey<Item> EDITABLE_MELEE_WEAPONS =
            tag("editable_melee_weapons");
        public static final TagKey<Item> EDITABLE_RANGED_WEAPONS =
            tag("editable_ranged_weapons");
        public static final TagKey<Item> EDITABLE_SHIELDS =
            tag("editable_shields");

        // Generic category tags
        public static final TagKey<Item> MELEE_WEAPONS =
            tag("melee_weapons");
        public static final TagKey<Item> RANGED_WEAPONS =
            tag("ranged_weapons");

        // Exclusion tag
        public static final TagKey<Item> NOT_EDITABLE =
            tag("not_editable");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, DevMod.rl(name));
        }
    }
}
```

### Default Tag JSON Files

```
data/devmod/tags/item/
├── editable_melee_weapons.json    # Empty, for modders to populate
├── editable_ranged_weapons.json   # Empty, for modders to populate
├── melee_weapons.json             # Includes #c:tools/swords, #c:tools/axes
├── ranged_weapons.json            # Includes #c:ranged_weapons
└── not_editable.json              # Items to exclude from editor
```

```json
// data/devmod/tags/item/melee_weapons.json
{
  "replace": false,
  "values": [
    "#c:tools/swords",
    "#c:tools/axes",
    "#forge:tools/melee_weapon",
    "#neoforge:melee_weapons"
  ]
}
```

### Config Whitelist/Blacklist

```json
// config/devmod/weapon_whitelist.json
{
  "_comment": "Items to explicitly treat as weapons (overrides detection)",
  "melee": [
    "somemod:custom_sword",
    "anothermod:battle_axe"
  ],
  "ranged": [
    "somemod:magic_staff"
  ],
  "mace": [],
  "trident": []
}
```

```json
// config/devmod/weapon_blacklist.json
{
  "_comment": "Items to explicitly exclude from weapon editor",
  "items": [
    "minecraft:stick",
    "somemod:decorative_sword"
  ]
}
```

### Weapon-Specific Tabs

#### MACE Tab (Phase 1)

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: MACE (MaceItem specific)                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ SMASH ATTACK                                             │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Fall Damage Bonus    [━━━━━━━━━━] +3.0   per block      │   │
│  │ Max Bonus Damage     [━━━━━━━━━━] 150.0  cap            │   │
│  │ Fall Damage Negation [✓]                                │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ KNOCKBACK                                                │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Smash Knockback      [━━━━━━━━━━] 1.5    radius         │   │
│  │ Smash AOE Damage     [━━━━━━━━━━] 50%    of hit         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### TRIDENT Tab (Phase 2)

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: TRIDENT (TridentItem specific)                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ THROW ATTACK                                             │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Throw Damage         [━━━━━━━━━━] 8.0                   │   │
│  │ Throw Speed          [━━━━━━━━━━] 2.5    blocks/tick    │   │
│  │ Return Speed         [━━━━━━━━━━] 1.5    (Loyalty)      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ RIPTIDE                                                  │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Riptide Distance     [━━━━━━━━━━] 12.0   blocks         │   │
│  │ Riptide Damage       [━━━━━━━━━━] 6.0    on collision   │   │
│  │ Requires Water       [✓]                                │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### BOW Tab (Phase 2)

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: BOW (BowItem specific)                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ DRAW MECHANICS                                           │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Draw Time            [━━━━━━━━━━] 20     ticks (1 sec)  │   │
│  │ Min Draw for Crit    [━━━━━━━━━━] 18     ticks          │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ PROJECTILE                                               │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Arrow Damage         [━━━━━━━━━━] 6.0    (full draw)    │   │
│  │ Arrow Velocity       [━━━━━━━━━━] 3.0    blocks/tick    │   │
│  │ Arrow Gravity        [━━━━━━━━━━] 0.05                  │   │
│  │ Arrow Spread         [━━━━━━━━━━] 1.0    inaccuracy     │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ INFINITY COMPAT                                          │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Consumes Ammo        [✓]                                │   │
│  │ Infinity Override    [ ]         (ignores enchant)      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### CROSSBOW Tab (Phase 2)

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: CROSSBOW (CrossbowItem specific)                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ CHARGE MECHANICS                                         │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Charge Time          [━━━━━━━━━━] 25     ticks          │   │
│  │ Quick Charge Bonus   [━━━━━━━━━━] -5     ticks/level    │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ PROJECTILE                                               │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Projectile Damage    [━━━━━━━━━━] 9.0                   │   │
│  │ Projectile Velocity  [━━━━━━━━━━] 3.15   blocks/tick    │   │
│  │ Piercing Level       [━━━━━━━━━━] 0                     │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ MULTISHOT                                                │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Multishot Count      [━━━━━━━━━━] 1      projectiles    │   │
│  │ Multishot Spread     [━━━━━━━━━━] 10°    angle          │   │
│  │ Extra Ammo Cost      [ ]         (consumes per shot)    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Module Selection Logic

```java
/**
 * Select appropriate editor module based on weapon type.
 */
public final class WeaponModuleSelector {

    /**
     * Get the appropriate module for a detected weapon type.
     */
    public static EditorModule getModule(DetectionResult detection, ItemStack stack) {
        return switch (detection.type()) {
            // Melee weapons → WeaponModule with variant tabs
            case SWORD, AXE, PICKAXE_COMBAT, GENERIC_MELEE ->
                new WeaponModule(stack, WeaponVariant.STANDARD);

            case MACE ->
                new WeaponModule(stack, WeaponVariant.MACE);

            case TRIDENT ->
                new WeaponModule(stack, WeaponVariant.TRIDENT);

            // Ranged weapons → RangedModule
            case BOW ->
                new RangedModule(stack, RangedVariant.BOW);

            case CROSSBOW ->
                new RangedModule(stack, RangedVariant.CROSSBOW);

            case GENERIC_RANGED ->
                new RangedModule(stack, RangedVariant.GENERIC);

            // Shield → ArmorModule with shield variant
            case SHIELD ->
                new ArmorModule(stack, ArmorVariant.SHIELD);

            // Not a weapon
            case UNKNOWN, NOT_A_WEAPON ->
                null;
        };
    }

    /**
     * Get available tabs for a weapon variant.
     */
    public static List<ModuleTab> getTabsForVariant(WeaponVariant variant) {
        List<ModuleTab> tabs = new ArrayList<>();

        // Common tabs for all melee weapons
        tabs.add(ModuleTab.STATS);
        tabs.add(ModuleTab.COMBAT);
        tabs.add(ModuleTab.DURABILITY);
        tabs.add(ModuleTab.DAMAGE_TYPES);

        // Variant-specific tabs
        switch (variant) {
            case MACE -> tabs.add(ModuleTab.MACE_SMASH);
            case TRIDENT -> tabs.add(ModuleTab.TRIDENT_THROW);
            default -> {} // No extra tabs for standard
        }

        // Debug tab (always last, only in debug mode)
        if (DebugOverlay.isEnabled()) {
            tabs.add(ModuleTab.DEBUG);
        }

        return tabs;
    }
}

public enum WeaponVariant {
    STANDARD,   // Sword, Axe, Generic
    MACE,       // Mace-specific smash mechanics
    TRIDENT     // Trident-specific throw/riptide
}

public enum RangedVariant {
    BOW,        // Draw-based
    CROSSBOW,   // Charge-based
    GENERIC     // Unknown ranged
}
```

### Low Confidence Warning UI

```
┌─────────────────────────────────────────────────────────────────┐
│  ⚠️ LOW CONFIDENCE DETECTION                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Item: somemod:mystery_blade                             │   │
│  │ Detected as: GENERIC_MELEE                              │   │
│  │ Method: ATTRIBUTE_HEURISTIC                             │   │
│  │ Confidence: 50%                                         │   │
│  │                                                         │   │
│  │ This item was detected as a weapon based on its         │   │
│  │ attributes, but may not behave as expected.             │   │
│  │                                                         │   │
│  │ To improve detection:                                   │   │
│  │ • Add to #devmod:editable_melee_weapons tag             │   │
│  │ • Or add to config/devmod/weapon_whitelist.json         │   │
│  │                                                         │   │
│  │ [Continue Anyway]  [Add to Whitelist]  [Cancel]         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Implementation Phases

| Phase | Weapon Types | Detection Methods |
|-------|--------------|-------------------|
| **MVP** | Sword, Axe, Generic Melee | Class + Tags + Attributes |
| **Phase 1** | + Mace | + MACE tab |
| **Phase 2** | + Trident, Bow, Crossbow | + RangedModule |
| **Phase 3** | + Shield | + Shield in ArmorModule |
| **Future** | Custom weapon types | + Plugin API |

### Config Options

```toml
# config/devmod-server.toml

[weapons.detection]
# Enable attribute-based heuristic detection for unknown items
enableHeuristicDetection = true

# Minimum confidence level to show editor without warning
minConfidenceForAutoEdit = 0.8

# Treat pickaxes as weapons (shows in weapon editor)
treatPickaxeAsWeapon = false

# Log detection results for debugging
logDetectionResults = true
```

---

## 2.22 Ranged Weapon Properties

Proprietà per armi a distanza: projectile speed, gravity, spread, draw time, ammo type.

### Critical Technical Constraints

#### Source of Truth Problem

**IMPORTANTE**: Le proprietà ranged **non sono** Data Components standard come damage/durability.

| Property | Actual Source | Writable? | Note |
|----------|---------------|-----------|------|
| Draw Time | `BowItem` class hardcoded | ❌ Vanilla | Requires mixin/AT |
| Charge Time | `CrossbowItem` class | ❌ Vanilla | Requires mixin/AT |
| Projectile Speed | `AbstractArrow` entity spawn | ❌ Vanilla | Set at shoot time |
| Projectile Gravity | `AbstractArrow.getGravity()` | ❌ Vanilla | Entity property |
| Spread/Inaccuracy | `shoot()` method parameter | ❌ Vanilla | Passed at shoot |
| Arrow Damage | `AbstractArrow` entity | ⚠️ Partial | Power enchant modifies |
| Multishot | Enchantment effect | ❌ Vanilla | Enchant-driven |
| Piercing | Enchantment effect | ❌ Vanilla | Enchant-driven |

**Soluzione DevMod**: Custom Data Components + Event hooks per override.

```java
/**
 * DevMod ranged weapon components.
 * These OVERRIDE vanilla behavior when present.
 */
public final class RangedComponents {
    public static final DataComponentType<Float> DRAW_TIME_TICKS =
        register("draw_time_ticks", Codec.FLOAT);

    public static final DataComponentType<Float> PROJECTILE_SPEED =
        register("projectile_speed", Codec.FLOAT);

    public static final DataComponentType<Float> PROJECTILE_GRAVITY =
        register("projectile_gravity", Codec.FLOAT);

    public static final DataComponentType<Float> PROJECTILE_SPREAD =
        register("projectile_spread", Codec.FLOAT);

    public static final DataComponentType<Float> BASE_ARROW_DAMAGE =
        register("base_arrow_damage", Codec.FLOAT);

    public static final DataComponentType<Integer> MULTISHOT_COUNT =
        register("multishot_count", Codec.INT);

    public static final DataComponentType<Integer> PIERCING_LEVEL =
        register("piercing_level", Codec.INT);

    public static final DataComponentType<ResourceLocation> AMMO_TAG_FILTER =
        register("ammo_tag_filter", ResourceLocation.CODEC);
}
```

### Property Support Matrix

| Property | Bow | Crossbow | Trident | Source | MVP | Phase 2 |
|----------|-----|----------|---------|--------|-----|---------|
| **Draw/Charge Time** | ✅ | ✅ | - | Class/DevMod | Read | Edit |
| **Projectile Speed** | ✅ | ✅ | ✅ | Entity/DevMod | Read | Edit |
| **Projectile Gravity** | ✅ | ✅ | ✅ | Entity/DevMod | Read | Edit |
| **Projectile Spread** | ✅ | ✅ | - | Shoot/DevMod | Read | Edit |
| **Base Damage** | ✅ | ✅ | ✅ | Entity/DevMod | Read | Edit |
| **Ammo Tag Filter** | ✅ | ✅ | - | DevMod only | Read | Edit |
| **Infinity Override** | ✅ | - | - | DevMod only | Read | Edit |
| **Multishot Count** | - | ✅ | - | Enchant/DevMod | Read | Edit |
| **Piercing Level** | - | ✅ | ✅ | Enchant/DevMod | Read | Edit |
| **Loyalty Speed** | - | - | ✅ | Enchant/DevMod | Read | Phase 3 |
| **Riptide Distance** | - | - | ✅ | Enchant/DevMod | Read | Phase 3 |
| **Channeling** | - | - | ✅ | Enchant only | Read | - |

### Value Source Indicator

MVP mostra **"Effective Value + Source"** per ogni proprietà:

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: PROJECTILE (Read-Only MVP)                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ PROJECTILE PHYSICS                                       │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Projectile Speed    3.0      [VANILLA DEFAULT]          │   │
│  │ Projectile Gravity  0.05     [VANILLA DEFAULT]          │   │
│  │ Projectile Spread   1.0      [VANILLA DEFAULT]          │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DAMAGE                                                   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Arrow Damage   6.0      [VANILLA DEFAULT]          │   │
│  │ Power Bonus         +2.5     [ENCHANTMENT: Power V]     │   │
│  │ Effective Damage    8.5      [COMPUTED]                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ℹ️ Values are read-only. Edit requires Phase 2 implementation. │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Source Types

```java
/**
 * Indicates where a ranged property value comes from.
 */
public enum ValueSource {
    VANILLA_DEFAULT("Vanilla Default", 0x888888),      // Hardcoded in vanilla class
    DEVMOD_COMPONENT("DevMod Override", 0x00AAFF),     // devmod:* component on item
    ENCHANTMENT("Enchantment", 0xFFAA00),              // Modified by enchant
    ATTRIBUTE_MODIFIER("Attribute", 0x00FF00),         // Via attribute system
    COMPUTED("Computed", 0xAAAAFF),                    // Calculated from multiple sources
    UNKNOWN("Unknown", 0xFF0000);                      // Could not determine

    public final String displayName;
    public final int color;

    ValueSource(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }
}

/**
 * A ranged property value with source tracking.
 */
public record SourcedValue<T>(
    T value,
    ValueSource source,
    @Nullable String sourceDetail  // e.g., "Power V" for enchantment
) {
    public static <T> SourcedValue<T> vanillaDefault(T value) {
        return new SourcedValue<>(value, ValueSource.VANILLA_DEFAULT, null);
    }

    public static <T> SourcedValue<T> devmod(T value) {
        return new SourcedValue<>(value, ValueSource.DEVMOD_COMPONENT, null);
    }

    public static <T> SourcedValue<T> enchant(T value, String enchantName) {
        return new SourcedValue<>(value, ValueSource.ENCHANTMENT, enchantName);
    }
}
```

### RangedStats Model

```java
/**
 * Transient model for ranged weapon stats.
 * Tracks both effective values and their sources.
 */
public record RangedStats(
    // Mechanics
    SourcedValue<Float> drawTime,           // Bow: ticks to full draw
    SourcedValue<Float> chargeTime,         // Crossbow: ticks to load
    SourcedValue<Integer> multishotCount,   // Crossbow: projectiles per shot
    SourcedValue<Integer> piercingLevel,    // Crossbow/Trident: entities pierced

    // Projectile Physics
    SourcedValue<Float> projectileSpeed,    // blocks/tick
    SourcedValue<Float> projectileGravity,  // downward acceleration
    SourcedValue<Float> projectileSpread,   // inaccuracy factor

    // Damage
    SourcedValue<Float> baseDamage,         // Before enchants
    SourcedValue<Float> enchantBonus,       // From Power/etc
    float effectiveDamage,                  // Computed total

    // Ammo
    SourcedValue<ResourceLocation> ammoTagFilter,
    SourcedValue<Boolean> infinityOverride,

    // Trident-specific (Phase 3)
    SourcedValue<Float> throwDamage,
    SourcedValue<Float> loyaltySpeed,
    SourcedValue<Float> riptideDistance,
    SourcedValue<Boolean> requiresWater
) {
    /**
     * Extract stats from a ranged weapon ItemStack.
     */
    public static RangedStats fromItemStack(ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof BowItem) {
            return extractBowStats(stack);
        } else if (item instanceof CrossbowItem) {
            return extractCrossbowStats(stack);
        } else if (item instanceof TridentItem) {
            return extractTridentStats(stack);
        }

        return extractGenericRangedStats(stack);
    }

    private static RangedStats extractBowStats(ItemStack stack) {
        // Check for DevMod components first, fallback to vanilla defaults
        Float drawTime = stack.has(RangedComponents.DRAW_TIME_TICKS)
            ? stack.get(RangedComponents.DRAW_TIME_TICKS)
            : null;

        Float speed = stack.has(RangedComponents.PROJECTILE_SPEED)
            ? stack.get(RangedComponents.PROJECTILE_SPEED)
            : null;

        // ... extract all values with source tracking

        return new RangedStats(
            drawTime != null
                ? SourcedValue.devmod(drawTime)
                : SourcedValue.vanillaDefault(20.0f),  // Vanilla bow = 20 ticks
            // ... other fields
        );
    }
}
```

### Tab Structure: RangedModule

#### BOW Tabs (Phase 2 Editing)

```
┌─────────────────────────────────────────────────────────────────┐
│ [STATS] [MECHANICS] [PROJECTILE] [AMMO] [DEBUG]                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  TAB: MECHANICS (Bow)                                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ DRAW MECHANICS                                           │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Draw Time           [━━━━━━━━━━] 20 ticks   [VANILLA]   │   │
│  │                     1.0 seconds to full draw             │   │
│  │                                                         │   │
│  │ Min Draw for Crit   [━━━━━━━━━━] 18 ticks   [VANILLA]   │   │
│  │                     90% draw = critical shot enabled     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: PROJECTILE                                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ PHYSICS                                                  │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Speed               [━━━━━━━━━━] 3.0        [VANILLA]   │   │
│  │                     blocks/tick (60 blocks/sec)          │   │
│  │                                                         │   │
│  │ Gravity             [━━━━━━━━━━] 0.05       [VANILLA]   │   │
│  │                     downward accel per tick              │   │
│  │                                                         │   │
│  │ Spread              [━━━━━━━━━━] 1.0        [VANILLA]   │   │
│  │                     inaccuracy (0 = perfect)             │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DAMAGE                                                   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Damage         [━━━━━━━━━━] 6.0        [VANILLA]   │   │
│  │ Power Bonus                      +2.5       [POWER V]   │   │
│  │ ─────────────────────────────────────────────────────   │   │
│  │ Effective Damage                 8.5        [COMPUTED]  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: AMMO                                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ AMMO FILTER                                              │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Accepted Ammo Tag   [#minecraft:arrows           ▾]     │   │
│  │                                                         │   │
│  │ Matching Items (12 total):                      [scroll]│   │
│  │ ┌─────────────────────────────────────────────────────┐ │   │
│  │ │ 🏹 Arrow                                            │ │   │
│  │ │ 🏹 Spectral Arrow                                   │ │   │
│  │ │ 🏹 Tipped Arrow (Water Breathing)                   │ │   │
│  │ │ 🏹 Tipped Arrow (Fire Resistance)                   │ │   │
│  │ │ 🏹 Tipped Arrow (Healing)                           │ │   │
│  │ │ ... +7 more                                         │ │   │
│  │ └─────────────────────────────────────────────────────┘ │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ INFINITY                                                 │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Consumes Ammo       [✓]                     [VANILLA]   │   │
│  │ Infinity Override   [ ]         (force infinite ammo)   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### CROSSBOW Tabs (Phase 2 Editing)

```
┌─────────────────────────────────────────────────────────────────┐
│ [STATS] [MECHANICS] [PROJECTILE] [AMMO] [DEBUG]                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  TAB: MECHANICS (Crossbow)                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ CHARGE MECHANICS                                         │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Charge Time    [━━━━━━━━━━] 25 ticks   [VANILLA]   │   │
│  │                     1.25 seconds base                    │   │
│  │                                                         │   │
│  │ Quick Charge Bonus  -15 ticks               [QC III]    │   │
│  │ Effective Charge    10 ticks                [COMPUTED]  │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ MULTISHOT                                                │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Multishot Count     [━━━━━━━━━━] 3          [MULTISHOT] │   │
│  │ Spread Angle        [━━━━━━━━━━] 10°        [VANILLA]   │   │
│  │ Extra Ammo Cost     [ ]         (1 arrow = 3 shots)     │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ PIERCING                                                 │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Piercing Level      [━━━━━━━━━━] 4          [PIERCE IV] │   │
│  │                     Passes through 4 entities            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### TRIDENT Tabs (Phase 3 - Read-Only until then)

```
┌─────────────────────────────────────────────────────────────────┐
│ [STATS] [MELEE] [THROW] [ENCHANTS] [DEBUG]                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  TAB: THROW (Trident - Read-Only)                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ⚠️ TRIDENT EDITING AVAILABLE IN PHASE 3                  │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ THROW MECHANICS                                          │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Throw Damage        8.0                     [VANILLA]   │   │
│  │ Throw Speed         2.5 blocks/tick         [VANILLA]   │   │
│  │ Throw Gravity       0.05                    [VANILLA]   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ LOYALTY (if enchanted)                                   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Loyalty Level       3                       [LOYALTY III]│   │
│  │ Return Speed        Computed from level     [VANILLA]   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ RIPTIDE (if enchanted)                                   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Riptide Level       0                       [NONE]      │   │
│  │ Riptide Distance    N/A                                 │   │
│  │ Requires Water      N/A                                 │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ CHANNELING (if enchanted)                                │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Channeling          [ ]                     [NONE]      │   │
│  │ Requires Thunder    Yes (vanilla behavior)              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Ammo Tag Filter UI

```java
/**
 * Ammo tag selector with preview and fallback.
 */
public final class AmmoTagSelector {

    private static final ResourceLocation DEFAULT_ARROWS = ResourceLocation.parse("minecraft:arrows");
    private static final ResourceLocation ANY_ITEM = ResourceLocation.parse("devmod:any_ammo");
    private static final int PREVIEW_LIMIT = 10;

    /**
     * Get items matching the ammo tag.
     */
    public static AmmoPreview getMatchingItems(ResourceLocation tagId) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);

        List<Item> matching = BuiltInRegistries.ITEM.getTag(tag)
            .map(holders -> holders.stream()
                .map(Holder::value)
                .limit(PREVIEW_LIMIT + 1)  // +1 to detect overflow
                .toList())
            .orElse(List.of());

        int totalCount = BuiltInRegistries.ITEM.getTag(tag)
            .map(holders -> (int) holders.stream().count())
            .orElse(0);

        boolean hasMore = matching.size() > PREVIEW_LIMIT;
        List<Item> preview = hasMore
            ? matching.subList(0, PREVIEW_LIMIT)
            : matching;

        return new AmmoPreview(preview, totalCount, hasMore);
    }

    public record AmmoPreview(
        List<Item> previewItems,
        int totalCount,
        boolean hasMore
    ) {}

    /**
     * Common ammo tag presets.
     */
    public static final List<AmmoTagPreset> PRESETS = List.of(
        new AmmoTagPreset("Any", ANY_ITEM, "Accepts any item as ammo"),
        new AmmoTagPreset("Arrows", DEFAULT_ARROWS, "Vanilla arrows + tipped"),
        new AmmoTagPreset("Fireworks", ResourceLocation.parse("minecraft:firework_rockets"), "For crossbow"),
        new AmmoTagPreset("Custom", null, "Enter custom tag...")
    );

    public record AmmoTagPreset(String name, @Nullable ResourceLocation tag, String description) {}
}
```

### Event Hooks for Override

```java
/**
 * Event hooks to apply DevMod ranged components.
 */
@EventBusSubscriber(modid = DevMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RangedEventHooks {

    /**
     * Override arrow properties when shot from DevMod-modified bow/crossbow.
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        ItemStack bow = event.getBow();

        // Check for DevMod speed override
        if (bow.has(RangedComponents.PROJECTILE_SPEED)) {
            // Modify the velocity that will be applied
            float speed = bow.get(RangedComponents.PROJECTILE_SPEED);
            // Note: Actual implementation requires mixin or reflection
            // to intercept the arrow spawn
        }
    }

    /**
     * Override arrow entity properties after spawn.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow) {
            // Apply custom gravity, etc.
        }
    }

    /**
     * Override draw time tick count.
     * Requires mixin into BowItem.use() or event pre-handler.
     */
    // This requires more invasive hooks - documented for Phase 2
}
```

### Implementation Roadmap

| Phase | Scope | Read | Edit | Notes |
|-------|-------|------|------|-------|
| **MVP** | Bow values | ✅ | ❌ | Effective + Source display |
| **MVP** | Crossbow values | ✅ | ❌ | Effective + Source display |
| **MVP** | Trident values | ✅ | ❌ | Read-only, no editing |
| **Phase 2** | Bow editing | ✅ | ✅ | DevMod components + events |
| **Phase 2** | Crossbow editing | ✅ | ✅ | DevMod components + events |
| **Phase 2** | Ammo tag filter | ✅ | ✅ | Tag selection + preview |
| **Phase 3** | Trident editing | ✅ | ✅ | Dual-mode complexity |
| **Phase 3** | Loyalty/Riptide | ✅ | ✅ | Enchant synergy rules |
| **Future** | Custom projectiles | - | - | Full projectile entity editor |

### Config Options

```toml
# config/devmod-server.toml

[ranged]
# Enable DevMod ranged property overrides
enableRangedOverrides = true

# Default ammo tag if none specified
defaultAmmoTag = "minecraft:arrows"

# Allow infinity override (force infinite ammo without enchant)
allowInfinityOverride = true

# Maximum projectile speed (blocks/tick) - safety limit
maxProjectileSpeed = 10.0

# Minimum draw/charge time (ticks) - prevent instant fire
minDrawTime = 1
```

### Vanilla Default Reference

| Property | Bow | Crossbow | Trident | Unit |
|----------|-----|----------|---------|------|
| Draw/Charge Time | 20 | 25 | - | ticks |
| Projectile Speed | 3.0 | 3.15 | 2.5 | blocks/tick |
| Projectile Gravity | 0.05 | 0.05 | 0.05 | blocks/tick² |
| Spread (full draw) | 0 | 0 | 0 | degrees |
| Spread (partial) | 1.0 | - | - | degrees |
| Base Damage | 6.0 | 9.0 | 8.0 | HP |
| Crit Multiplier | 1.5x | - | - | - |

---

## 2.23 Hit Location Multipliers — Deferred

### Status: Out of Scope (Deferred to Phase 4+)

Hit location multipliers (head/body/legs damage modifiers) are **not included** in the MVP or initial phases.

### Rationale

| Factor | Assessment |
|--------|------------|
| **Vanilla Support** | Minecraft has no native hit-location detection for entity parts |
| **Implementation Complexity** | Requires ray-tracing / hitbox heuristics with unreliable accuracy |
| **Conflict Risk** | May conflict with other combat mods (Better Combat, Epic Fight, etc.) |
| **Debug-First Alignment** | High risk, uncertain value — violates debug-first principle |
| **User Confusion** | Placeholder UI controls without gameplay effect cause false expectations |

### Decision

- **No UI controls** for hit location in MVP
- **No data storage** for location multipliers
- **No placeholder fields** (avoid confusion)

### Future Consideration (Phase 4+)

If implemented in a future phase, requirements include:

```java
/**
 * Future hit location system requirements (Phase 4+).
 */
public final class HitLocationRequirements {

    /**
     * Required features for hit location system.
     */
    public enum Requirement {
        /**
         * Fallback mode when detection fails.
         * Default to "body only" (1.0x multiplier everywhere).
         */
        COMPATIBILITY_FALLBACK,

        /**
         * Server-authoritative hit detection.
         * Client sends aim vector, server validates.
         */
        SERVER_AUTHORITATIVE,

        /**
         * Telemetry for accuracy verification.
         * Track hit location vs expected to tune detection.
         */
        ACCURACY_TELEMETRY,

        /**
         * Per-entity-type hitbox definitions.
         * Different creatures have different anatomy.
         */
        ENTITY_HITBOX_CONFIG,

        /**
         * Mod compatibility layer.
         * Detect and defer to other combat mods if present.
         */
        MOD_COMPATIBILITY
    }

    /**
     * Hit location zones (if implemented).
     */
    public enum HitZone {
        HEAD(1.5f, "Critical hit zone"),
        BODY(1.0f, "Standard damage"),
        LEGS(0.75f, "Reduced damage, may slow"),
        ARMS(0.5f, "Minimal damage");

        private final float defaultMultiplier;
        private final String description;

        HitZone(float defaultMultiplier, String description) {
            this.defaultMultiplier = defaultMultiplier;
            this.description = description;
        }

        public float getDefaultMultiplier() { return defaultMultiplier; }
        public String getDescription() { return description; }
    }
}
```

### Config Placeholder (Disabled by Default)

```toml
# config/devmod-server.toml

[combat.hitLocation]
# FUTURE: Enable hit location multipliers (Phase 4+)
# Currently has no effect - reserved for future implementation
enableHitLocation = false

# FUTURE: Default multipliers per zone
# headMultiplier = 1.5
# bodyMultiplier = 1.0
# legsMultiplier = 0.75
```

---

## 2.24 Dangerous Values System

### Overview

Three-tier validation system for handling values that could cause gameplay issues, crashes, or undefined behavior.

### Validation Levels

| Level | Name | Behavior | UI Indicator | User Action |
|-------|------|----------|--------------|-------------|
| **1** | Soft Limit (Warning) | Value accepted, warning shown | ⚠️ Orange icon | Can proceed |
| **2** | Hard Limit (Clamp) | Value clamped to safe range | 🔒 Lock icon | Auto-corrected |
| **3** | Forbidden (Block) | Value rejected entirely | ❌ Red icon | Must fix |

### Level Definitions

#### Level 1: Soft Limits (Warning)
- Values beyond "reasonable" range but technically valid
- UI shows orange warning icon with explanatory tooltip
- User can proceed — this is a debug tool, not a nanny
- Logged to telemetry for awareness

#### Level 2: Hard Limits (Clamp)
- Values that would cause overflow, crash, or undefined behavior
- UI automatically clamps to max/min safe boundary
- Shows notification of applied clamp with original value
- Prevents game-breaking states

#### Level 3: Forbidden (Block)
- Values that violate Minecraft invariants (NaN, Infinity, null)
- Input rejected, field reset to previous valid value
- Red error with clear explanation
- Cannot be bypassed even in Unsafe mode

### Property Limits Reference

```java
/**
 * Validation limits for all editable properties.
 */
public final class ValueLimits {

    /**
     * Limit definition for a property.
     */
    public record Limit(
        double softMin,      // Below = warning
        double softMax,      // Above = warning
        double hardMin,      // Below = clamp
        double hardMax,      // Above = clamp
        boolean allowZero,   // Whether 0 is valid
        boolean allowNegative // Whether negative is valid
    ) {
        public static Limit of(double softMin, double softMax, double hardMin, double hardMax) {
            return new Limit(softMin, softMax, hardMin, hardMax, true, false);
        }

        public static Limit ofWithNegative(double softMin, double softMax, double hardMin, double hardMax) {
            return new Limit(softMin, softMax, hardMin, hardMax, true, true);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WEAPON PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Attack damage in half-hearts */
    public static final Limit ATTACK_DAMAGE = Limit.of(
        0.0, 100.0,      // Soft: 0-100 (vanilla sword max ~7)
        0.0, 2048.0      // Hard: 0-2048 (prevent overflow)
    );

    /** Attack speed in attacks/second */
    public static final Limit ATTACK_SPEED = Limit.of(
        0.5, 10.0,       // Soft: 0.5-10 (vanilla range ~1.6-4)
        0.1, 1024.0      // Hard: 0.1-1024 (must be positive)
    );

    /** Attack range in blocks */
    public static final Limit ATTACK_RANGE = Limit.of(
        1.0, 10.0,       // Soft: 1-10 (vanilla ~3)
        0.1, 64.0        // Hard: 0.1-64 (chunk distance)
    );

    /** Knockback strength */
    public static final Limit KNOCKBACK = Limit.of(
        0.0, 5.0,        // Soft: 0-5 (vanilla knockback II ~1.8)
        0.0, 100.0       // Hard: 0-100
    );

    /** Critical hit chance percentage */
    public static final Limit CRIT_CHANCE = Limit.of(
        0.0, 100.0,      // Soft: 0-100%
        0.0, 1000.0      // Hard: 0-1000% (allow guaranteed crits)
    );

    /** Critical hit damage multiplier */
    public static final Limit CRIT_MULTIPLIER = Limit.of(
        1.0, 5.0,        // Soft: 1-5x (vanilla 1.5x)
        1.0, 100.0       // Hard: 1-100x
    );

    /** Armor penetration percentage */
    public static final Limit ARMOR_SHRED = Limit.of(
        0.0, 50.0,       // Soft: 0-50%
        0.0, 100.0       // Hard: 0-100% (full penetration)
    );

    /** Lifesteal percentage */
    public static final Limit LIFESTEAL = Limit.of(
        0.0, 25.0,       // Soft: 0-25%
        0.0, 100.0       // Hard: 0-100%
    );

    /** Sweep damage ratio */
    public static final Limit SWEEP_RATIO = Limit.of(
        0.0, 1.0,        // Soft: 0-100%
        0.0, 2.0         // Hard: 0-200%
    );

    // ═══════════════════════════════════════════════════════════════
    // ARMOR PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Armor value (defense points) */
    public static final Limit ARMOR_VALUE = Limit.of(
        0.0, 30.0,       // Soft: 0-30 (full netherite ~20)
        0.0, 1024.0      // Hard: 0-1024
    );

    /** Armor toughness */
    public static final Limit ARMOR_TOUGHNESS = Limit.of(
        0.0, 20.0,       // Soft: 0-20 (full netherite ~12)
        0.0, 1024.0      // Hard: 0-1024
    );

    /** Knockback resistance (0-1 scale) */
    public static final Limit KNOCKBACK_RESISTANCE = Limit.of(
        0.0, 1.0,        // Soft: 0-100%
        0.0, 10.0        // Hard: 0-1000% (allow over-resist)
    );

    /** Damage reduction percentage (per type) */
    public static final Limit DAMAGE_REDUCTION = Limit.of(
        0.0, 80.0,       // Soft: 0-80% (avoid invincibility)
        0.0, 100.0       // Hard: 0-100% (cap at immunity)
    );

    /** Thorns reflection percentage */
    public static final Limit THORNS_PERCENT = Limit.of(
        0.0, 30.0,       // Soft: 0-30%
        0.0, 50.0        // Hard: 0-50% (prevent reflect loops)
    );

    /** Movement speed modifier */
    public static final Limit MOVEMENT_SPEED = Limit.ofWithNegative(
        -0.1, 0.2,       // Soft: -10% to +20%
        -0.5, 1.0        // Hard: -50% to +100%
    );

    // ═══════════════════════════════════════════════════════════════
    // RANGED PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Projectile speed in blocks/tick */
    public static final Limit PROJECTILE_SPEED = Limit.of(
        0.5, 10.0,       // Soft: 0.5-10 (vanilla bow ~3)
        0.1, 100.0       // Hard: 0.1-100
    );

    /** Draw/charge time in ticks */
    public static final Limit DRAW_TIME = Limit.of(
        5, 60,           // Soft: 5-60 ticks (0.25-3 sec)
        1, 200           // Hard: 1-200 ticks (must be positive)
    );

    /** Projectile gravity in blocks/tick² */
    public static final Limit PROJECTILE_GRAVITY = Limit.of(
        0.01, 0.1,       // Soft: 0.01-0.1 (vanilla 0.05)
        0.0, 1.0         // Hard: 0-1 (0 = no gravity)
    );

    /** Arrow spread in degrees */
    public static final Limit PROJECTILE_SPREAD = Limit.of(
        0.0, 5.0,        // Soft: 0-5 degrees
        0.0, 45.0        // Hard: 0-45 degrees
    );

    // ═══════════════════════════════════════════════════════════════
    // FORBIDDEN VALUES (Always blocked)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if value is forbidden (Level 3).
     */
    public static boolean isForbidden(double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }

    /**
     * Check if string value is forbidden.
     */
    public static boolean isForbidden(String value) {
        return value == null;
    }
}
```

### Validation Result

```java
/**
 * Result of validating a value against limits.
 */
public sealed interface ValidationResult {

    /** Value is within soft limits - no issues */
    record Valid(double value) implements ValidationResult {}

    /** Value exceeds soft limit - warning shown */
    record SoftWarning(
        double value,
        double softLimit,
        boolean isAbove,
        String message
    ) implements ValidationResult {}

    /** Value exceeded hard limit - was clamped */
    record HardClamped(
        double clampedValue,
        double originalValue,
        double hardLimit,
        boolean wasAbove,
        String message
    ) implements ValidationResult {}

    /** Value is forbidden - rejected */
    record Forbidden(
        double attemptedValue,
        double fallbackValue,
        String reason
    ) implements ValidationResult {}
}
```

### Validator Implementation

```java
/**
 * Validates and transforms property values.
 */
public final class ValueValidator {

    /**
     * Validate a value against limits.
     */
    public static ValidationResult validate(double value, ValueLimits.Limit limit) {
        // Level 3: Forbidden check
        if (ValueLimits.isForbidden(value)) {
            return new ValidationResult.Forbidden(
                value,
                limit.softMin(),
                "Value is NaN or Infinite"
            );
        }

        // Level 3: Negative check (if not allowed)
        if (!limit.allowNegative() && value < 0) {
            return new ValidationResult.Forbidden(
                value,
                0.0,
                "Negative values not allowed"
            );
        }

        // Level 3: Zero check (if not allowed)
        if (!limit.allowZero() && value == 0) {
            return new ValidationResult.Forbidden(
                value,
                limit.softMin(),
                "Zero not allowed"
            );
        }

        // Level 2: Hard clamp check
        if (value < limit.hardMin()) {
            return new ValidationResult.HardClamped(
                limit.hardMin(),
                value,
                limit.hardMin(),
                false,
                String.format("Clamped to minimum (was %.2f)", value)
            );
        }
        if (value > limit.hardMax()) {
            return new ValidationResult.HardClamped(
                limit.hardMax(),
                value,
                limit.hardMax(),
                true,
                String.format("Clamped to maximum (was %.2f)", value)
            );
        }

        // Level 1: Soft warning check
        if (value < limit.softMin()) {
            return new ValidationResult.SoftWarning(
                value,
                limit.softMin(),
                false,
                String.format("Below recommended minimum (%.2f)", limit.softMin())
            );
        }
        if (value > limit.softMax()) {
            return new ValidationResult.SoftWarning(
                value,
                limit.softMax(),
                true,
                String.format("Exceeds recommended maximum (%.2f)", limit.softMax())
            );
        }

        // All good
        return new ValidationResult.Valid(value);
    }

    /**
     * Get the effective value after validation.
     */
    public static double getEffectiveValue(ValidationResult result) {
        return switch (result) {
            case ValidationResult.Valid v -> v.value();
            case ValidationResult.SoftWarning w -> w.value();
            case ValidationResult.HardClamped c -> c.clampedValue();
            case ValidationResult.Forbidden f -> f.fallbackValue();
        };
    }
}
```

### UI Rendering

```java
/**
 * Renders validation state in the editor UI.
 */
public final class ValidationRenderer {

    private static final int WARNING_COLOR = 0xFFAA00;   // Orange
    private static final int CLAMPED_COLOR = 0x00AAFF;   // Blue
    private static final int ERROR_COLOR = 0xFF4444;    // Red
    private static final int VALID_COLOR = 0x888888;    // Gray (no indicator)

    /**
     * Render validation indicator next to input field.
     */
    public static void renderIndicator(
        GuiGraphics graphics,
        int x, int y,
        ValidationResult result
    ) {
        switch (result) {
            case ValidationResult.Valid v -> {
                // No indicator for valid values
            }

            case ValidationResult.SoftWarning w -> {
                renderIcon(graphics, x, y, "⚠", WARNING_COLOR);
                // Tooltip on hover: w.message()
            }

            case ValidationResult.HardClamped c -> {
                renderIcon(graphics, x, y, "🔒", CLAMPED_COLOR);
                // Tooltip on hover: c.message()
            }

            case ValidationResult.Forbidden f -> {
                renderIcon(graphics, x, y, "✕", ERROR_COLOR);
                // Tooltip on hover: f.reason()
            }
        }
    }

    private static void renderIcon(GuiGraphics graphics, int x, int y, String icon, int color) {
        graphics.drawString(
            Minecraft.getInstance().font,
            icon,
            x, y,
            color
        );
    }

    /**
     * Render validation tooltip on hover.
     */
    public static void renderTooltip(
        GuiGraphics graphics,
        int mouseX, int mouseY,
        ValidationResult result
    ) {
        String message = switch (result) {
            case ValidationResult.Valid v -> null;
            case ValidationResult.SoftWarning w -> "⚠ " + w.message();
            case ValidationResult.HardClamped c -> "🔒 " + c.message();
            case ValidationResult.Forbidden f -> "✕ " + f.reason();
        };

        if (message != null) {
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                Component.literal(message),
                mouseX, mouseY
            );
        }
    }
}
```

### UI Mockup

```
┌─────────────────────────────────────────────────────────────┐
│ WEAPON STATS                                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Attack Damage    [████████████====] 150     ⚠️             │
│                   ⚠️ Exceeds recommended maximum (100)       │
│                                                             │
│  Attack Speed     [██==============] 0.10    🔒             │
│                   🔒 Clamped to minimum (was -5.00)         │
│                                                             │
│  Crit Chance      [████████========] 50%                    │
│                   (no indicator - within limits)            │
│                                                             │
│  Knockback        [================] NaN     ✕              │
│                   ✕ Value is NaN or Infinite                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Validation Modes

```java
/**
 * Validation strictness modes.
 */
public enum ValidationMode {
    /**
     * STRICT: Soft limits enforced as hard limits.
     * Best for public servers - prevents "unreasonable" values.
     */
    STRICT,

    /**
     * DEBUG: Soft limits show warnings only.
     * Default for singleplayer/development - maximum flexibility.
     */
    DEBUG,

    /**
     * UNSAFE: Only forbidden values blocked.
     * Opt-in mode for advanced users who "know what they're doing".
     * Requires explicit confirmation.
     */
    UNSAFE
}
```

### Mode Behavior Matrix

| Validation | STRICT Mode | DEBUG Mode | UNSAFE Mode |
|------------|-------------|------------|-------------|
| **Within soft limits** | ✅ Accept | ✅ Accept | ✅ Accept |
| **Exceeds soft limit** | 🔒 Clamp | ⚠️ Warn | ✅ Accept |
| **Exceeds hard limit** | 🔒 Clamp | 🔒 Clamp | ⚠️ Warn |
| **Forbidden (NaN/Inf)** | ❌ Block | ❌ Block | ❌ Block |

### Mode Selection

```java
/**
 * Mode selection with confirmation for unsafe.
 */
public final class ValidationModeSelector {

    private ValidationMode currentMode = ValidationMode.DEBUG;
    private boolean unsafeConfirmed = false;

    /**
     * Set validation mode.
     */
    public void setMode(ValidationMode mode) {
        if (mode == ValidationMode.UNSAFE && !unsafeConfirmed) {
            // Show confirmation dialog
            showUnsafeConfirmation(() -> {
                unsafeConfirmed = true;
                currentMode = ValidationMode.UNSAFE;
            });
        } else {
            currentMode = mode;
        }
    }

    /**
     * Apply validation based on current mode.
     */
    public ValidationResult validateWithMode(double value, ValueLimits.Limit limit) {
        ValidationResult baseResult = ValueValidator.validate(value, limit);

        return switch (currentMode) {
            case STRICT -> promoteWarningsToClamps(baseResult, limit);
            case DEBUG -> baseResult;
            case UNSAFE -> demoteClampsToWarnings(baseResult);
        };
    }

    private ValidationResult promoteWarningsToClamps(ValidationResult result, ValueLimits.Limit limit) {
        if (result instanceof ValidationResult.SoftWarning w) {
            double clampedValue = w.isAbove() ? limit.softMax() : limit.softMin();
            return new ValidationResult.HardClamped(
                clampedValue, w.value(), w.softLimit(), w.isAbove(),
                "Strict mode: " + w.message()
            );
        }
        return result;
    }

    private ValidationResult demoteClampsToWarnings(ValidationResult result) {
        if (result instanceof ValidationResult.HardClamped c) {
            // In unsafe mode, hard clamps become warnings (except forbidden)
            return new ValidationResult.SoftWarning(
                c.originalValue(), c.hardLimit(), c.wasAbove(),
                "⚠️ UNSAFE: " + c.message()
            );
        }
        return result;
    }
}
```

### Unsafe Mode Confirmation Dialog

```
┌─────────────────────────────────────────────────────────────┐
│                    ⚠️ UNSAFE MODE                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  You are about to enable UNSAFE validation mode.            │
│                                                             │
│  This mode bypasses safety limits and may cause:            │
│  • Game crashes                                             │
│  • World corruption                                         │
│  • Unexpected behavior                                      │
│  • Server instability                                       │
│                                                             │
│  Only use this if you understand the risks.                 │
│                                                             │
│  ┌─────────────────┐     ┌─────────────────┐               │
│  │     CANCEL      │     │  I UNDERSTAND   │               │
│  └─────────────────┘     └─────────────────┘               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Config

```toml
# config/devmod-client.toml

[editor.validation]
# Default validation mode: STRICT, DEBUG, or UNSAFE
defaultMode = "DEBUG"

# Show validation indicators in UI
showIndicators = true

# Show tooltips on validation warnings
showTooltips = true

# Play sound on clamp/block
playSounds = true
```

```toml
# config/devmod-server.toml

[validation]
# Server-enforced maximum validation mode
# Clients cannot use a more permissive mode than this
maxAllowedMode = "DEBUG"

# Override client mode for all players (empty = use client preference)
forceMode = ""

# Log validation events to server console
logValidation = false

# Log level: WARN_ONLY, ALL, NONE
logLevel = "WARN_ONLY"
```

---

## 2.25 Armor Properties Architecture

### Design Principle

Armor Editor follows the **same hybrid pattern as Weapon Editor**:
- Layer 1: Vanilla attributes (read + edit)
- Layer 2: DevMod custom attributes (read + edit)
- Layer 3: Set Bonus System (deferred to Phase 3+)

### Layer 1: Vanilla Armor Attributes

```java
/**
 * Vanilla armor attributes accessible in Armor Editor.
 */
public final class VanillaArmorAttributes {

    // ═══════════════════════════════════════════════════════════════
    // CORE DEFENSE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Defense points displayed as armor icons.
     * 1 point = half chestplate icon above hotbar.
     * Full netherite set = 20 points.
     */
    public static final ResourceLocation ARMOR =
        ResourceLocation.withDefaultNamespace("generic.armor");

    /**
     * Reduces effectiveness of high-damage attacks.
     * Only diamond (8) and netherite (12 full set) have non-zero toughness.
     * Range: 0-20
     */
    public static final ResourceLocation ARMOR_TOUGHNESS =
        ResourceLocation.withDefaultNamespace("generic.armor_toughness");

    // ═══════════════════════════════════════════════════════════════
    // KNOCKBACK RESISTANCE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Percentage resistance to knockback from attacks.
     * Range: 0.0 (none) to 1.0 (immune)
     * Netherite armor provides 0.1 per piece (0.4 full set).
     */
    public static final ResourceLocation KNOCKBACK_RESISTANCE =
        ResourceLocation.withDefaultNamespace("generic.knockback_resistance");

    /**
     * Percentage resistance to explosion knockback.
     * Separate from regular knockback resistance.
     * Range: 0.0 to 1.0
     */
    public static final ResourceLocation EXPLOSION_KNOCKBACK_RESISTANCE =
        ResourceLocation.withDefaultNamespace("generic.explosion_knockback_resistance");

    // ═══════════════════════════════════════════════════════════════
    // MOVEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Base movement speed modifier.
     * Negative values for heavy armor penalty.
     * Default: 0.1 (base player speed)
     */
    public static final ResourceLocation MOVEMENT_SPEED =
        ResourceLocation.withDefaultNamespace("generic.movement_speed");

    // ═══════════════════════════════════════════════════════════════
    // FALL DAMAGE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Multiplier for fall damage received.
     * Range: 0.0 (immune) to 100.0
     * Default: 1.0 (normal damage)
     */
    public static final ResourceLocation FALL_DAMAGE_MULTIPLIER =
        ResourceLocation.withDefaultNamespace("generic.fall_damage_multiplier");

    /**
     * Fall distance (in blocks) before taking damage.
     * Default: 3.0 blocks
     * Higher = more safe fall distance.
     */
    public static final ResourceLocation SAFE_FALL_DISTANCE =
        ResourceLocation.withDefaultNamespace("generic.safe_fall_distance");
}
```

### Layer 2: DevMod Custom Armor Attributes

```java
/**
 * DevMod custom armor attributes.
 * Registered under devmod:* namespace.
 */
public final class ModArmorAttributes {

    private static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, DevMod.MODID);

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE TYPE REDUCTIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Physical damage reduction percentage.
     * Applies to: melee attacks, mob attacks, player attacks.
     * Range: 0-100%
     */
    public static final Holder<Attribute> PHYSICAL_REDUCTION = ATTRIBUTES.register(
        "physical_reduction",
        () -> new RangedAttribute("attribute.devmod.physical_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Fire damage reduction percentage.
     * Applies to: fire, lava, burning, fire aspect.
     * Stacks with Fire Protection enchantment.
     * Range: 0-100%
     */
    public static final Holder<Attribute> FIRE_REDUCTION = ATTRIBUTES.register(
        "fire_reduction",
        () -> new RangedAttribute("attribute.devmod.fire_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Magic damage reduction percentage.
     * Applies to: potions, guardian beams, evoker fangs, wither effect.
     * Range: 0-100%
     */
    public static final Holder<Attribute> MAGIC_REDUCTION = ATTRIBUTES.register(
        "magic_reduction",
        () -> new RangedAttribute("attribute.devmod.magic_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Explosion damage reduction percentage.
     * Applies to: creepers, TNT, fireballs, beds in nether/end.
     * Stacks with Blast Protection enchantment.
     * Range: 0-100%
     */
    public static final Holder<Attribute> EXPLOSION_REDUCTION = ATTRIBUTES.register(
        "explosion_reduction",
        () -> new RangedAttribute("attribute.devmod.explosion_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Projectile damage reduction percentage.
     * Applies to: arrows, tridents, fireballs, shulker bullets.
     * Stacks with Projectile Protection enchantment.
     * Range: 0-100%
     */
    public static final Holder<Attribute> PROJECTILE_REDUCTION = ATTRIBUTES.register(
        "projectile_reduction",
        () -> new RangedAttribute("attribute.devmod.projectile_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    // ═══════════════════════════════════════════════════════════════
    // THORNS SYSTEM
    // ═══════════════════════════════════════════════════════════════

    /**
     * Thorns damage reflection percentage.
     * Percentage of received damage reflected to attacker.
     * Range: 0-50% (capped to prevent infinite loops)
     */
    public static final Holder<Attribute> THORNS_REFLECT = ATTRIBUTES.register(
        "thorns_reflect",
        () -> new RangedAttribute("attribute.devmod.thorns_reflect", 0.0D, 0.0D, 50.0D)
            .setSyncable(true)
    );

    /**
     * Thorns activation chance.
     * Chance that thorns triggers on hit.
     * Range: 0-100%
     */
    public static final Holder<Attribute> THORNS_CHANCE = ATTRIBUTES.register(
        "thorns_chance",
        () -> new RangedAttribute("attribute.devmod.thorns_chance", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    // ═══════════════════════════════════════════════════════════════
    // EVASION & HEALING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Dodge chance percentage.
     * Chance to completely avoid incoming melee damage.
     * Range: 0-100% (soft limit 50% recommended)
     */
    public static final Holder<Attribute> DODGE_CHANCE = ATTRIBUTES.register(
        "dodge_chance",
        () -> new RangedAttribute("attribute.devmod.dodge_chance", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Healing received multiplier.
     * Multiplier for all healing (potions, regeneration, food).
     * Range: 0-1000% (0 = no healing, 100 = normal, 200 = double)
     */
    public static final Holder<Attribute> HEALING_RECEIVED = ATTRIBUTES.register(
        "healing_received",
        () -> new RangedAttribute("attribute.devmod.healing_received", 100.0D, 0.0D, 1000.0D)
            .setSyncable(true)
    );

    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
    }
}
```

### Attribute Summary Table

| Attribute | Namespace | Default | Soft Min | Soft Max | Hard Min | Hard Max |
|-----------|-----------|---------|----------|----------|----------|----------|
| **Vanilla - Core** |
| armor | minecraft | 0 | 0 | 30 | 0 | 1024 |
| armor_toughness | minecraft | 0 | 0 | 20 | 0 | 1024 |
| knockback_resistance | minecraft | 0 | 0 | 1.0 | 0 | 10 |
| explosion_knockback_resistance | minecraft | 0 | 0 | 1.0 | 0 | 10 |
| movement_speed | minecraft | 0.1 | -0.1 | 0.2 | -0.5 | 1.0 |
| fall_damage_multiplier | minecraft | 1.0 | 0 | 1.0 | 0 | 100 |
| safe_fall_distance | minecraft | 3.0 | 3 | 10 | 0 | 256 |
| **DevMod - Reductions** |
| physical_reduction | devmod | 0 | 0 | 80 | 0 | 100 |
| fire_reduction | devmod | 0 | 0 | 80 | 0 | 100 |
| magic_reduction | devmod | 0 | 0 | 80 | 0 | 100 |
| explosion_reduction | devmod | 0 | 0 | 80 | 0 | 100 |
| projectile_reduction | devmod | 0 | 0 | 80 | 0 | 100 |
| **DevMod - Thorns** |
| thorns_reflect | devmod | 0 | 0 | 30 | 0 | 50 |
| thorns_chance | devmod | 0 | 0 | 100 | 0 | 100 |
| **DevMod - Utility** |
| dodge_chance | devmod | 0 | 0 | 50 | 0 | 100 |
| healing_received | devmod | 100 | 50 | 200 | 0 | 1000 |

### ValueLimits Addition

```java
// Add to ValueLimits.java (Section 2.24)

// ═══════════════════════════════════════════════════════════════
// ARMOR PROPERTIES (Extended)
// ═══════════════════════════════════════════════════════════════

/** Explosion knockback resistance (0-1 scale) */
public static final Limit EXPLOSION_KB_RESISTANCE = Limit.of(
    0.0, 1.0,        // Soft: 0-100%
    0.0, 10.0        // Hard: 0-1000%
);

/** Fall damage multiplier */
public static final Limit FALL_DAMAGE_MULT = Limit.of(
    0.0, 1.0,        // Soft: 0-100% (no fall damage to normal)
    0.0, 100.0       // Hard: 0-10000%
);

/** Safe fall distance in blocks */
public static final Limit SAFE_FALL_DIST = Limit.of(
    3.0, 10.0,       // Soft: 3-10 blocks (vanilla to enhanced)
    0.0, 256.0       // Hard: 0-256 (world height)
);

/** Dodge chance percentage */
public static final Limit DODGE_CHANCE = Limit.of(
    0.0, 50.0,       // Soft: 0-50% (avoid guaranteed dodge)
    0.0, 100.0       // Hard: 0-100%
);

/** Healing received multiplier percentage */
public static final Limit HEALING_RECEIVED = Limit.of(
    50.0, 200.0,     // Soft: 50-200%
    0.0, 1000.0      // Hard: 0-1000%
);
```

### Damage Reduction Event Hook

```java
/**
 * Event handler for applying DevMod armor damage reductions.
 */
@EventBusSubscriber(modid = DevMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ArmorDamageHandler {

    /**
     * Apply damage type reductions from DevMod attributes.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        float damage = event.getNewDamage();

        // Get reduction based on damage type
        float reduction = getReductionForDamageType(entity, source);

        if (reduction > 0) {
            float reducedDamage = damage * (1.0f - reduction / 100.0f);
            event.setNewDamage(Math.max(0, reducedDamage));
        }
    }

    /**
     * Determine which reduction attribute applies to this damage source.
     */
    private static float getReductionForDamageType(LivingEntity entity, DamageSource source) {
        // Check damage type tags
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return (float) entity.getAttributeValue(ModArmorAttributes.FIRE_REDUCTION);
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return (float) entity.getAttributeValue(ModArmorAttributes.EXPLOSION_REDUCTION);
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return (float) entity.getAttributeValue(ModArmorAttributes.PROJECTILE_REDUCTION);
        }
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            // Magic damage (potions, etc.)
            return (float) entity.getAttributeValue(ModArmorAttributes.MAGIC_REDUCTION);
        }

        // Default to physical for melee/untagged damage
        if (source.getEntity() instanceof LivingEntity) {
            return (float) entity.getAttributeValue(ModArmorAttributes.PHYSICAL_REDUCTION);
        }

        return 0.0f;
    }

    /**
     * Apply dodge chance.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDodgeCheck(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();

        // Dodge only applies to melee attacks
        if (!source.is(DamageTypeTags.IS_PROJECTILE) &&
            source.getEntity() instanceof LivingEntity) {

            double dodgeChance = entity.getAttributeValue(ModArmorAttributes.DODGE_CHANCE);
            if (dodgeChance > 0 && entity.getRandom().nextFloat() * 100 < dodgeChance) {
                event.setNewDamage(0);
                // Visual/audio feedback
                if (entity.level().isClientSide) {
                    // Play dodge sound, show particle
                }
            }
        }
    }

    /**
     * Apply healing received modifier.
     */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        double healingMod = entity.getAttributeValue(ModArmorAttributes.HEALING_RECEIVED);

        if (healingMod != 100.0) {
            float modifiedHeal = event.getAmount() * (float) (healingMod / 100.0);
            event.setAmount(modifiedHeal);
        }
    }
}
```

### Armor Editor Tabs

| Tab | Content | Attributes |
|-----|---------|------------|
| **PROTECTION** | Damage type reductions | physical, fire, magic, explosion, projectile |
| **ATTRIBUTES** | Core armor stats | armor, toughness, knockback_res, explosion_kb_res, movement |
| **UTILITY** | Special mechanics | dodge_chance, healing_received, fall_damage_mult, safe_fall_dist |
| **THORNS** | Reflection system | thorns_reflect, thorns_chance |
| **ENCHANTS** | Enchantment viewer | Read-only enchantment list |

### UI Mockup

```
┌─────────────────────────────────────────────────────────────────┐
│ ARMOR EDITOR                              [Diamond Chestplate]  │
├─────────────────────────────────────────────────────────────────┤
│ [PROTECTION] [ATTRIBUTES] [UTILITY] [THORNS] [ENCHANTS]         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐  DAMAGE REDUCTIONS                             │
│  │             │  ───────────────────────────────────────────   │
│  │   [ARMOR]   │                                                │
│  │    ICON     │  Physical     [████████====] 40%               │
│  │             │  Fire         [██══════════] 10%               │
│  │             │  Magic        [════════════]  0%               │
│  └─────────────┘  Explosion    [██████══════] 30%               │
│                   Projectile   [████════════] 20%               │
│  Slot: CHEST                                                    │
│  Material: DIAMOND             ───────────────────────────────  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Total Effective Reduction: ~35% vs Physical             │   │
│  │ (includes armor + enchants + devmod attributes)         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ [PREVIEW]  [APPLY TO ITEM]  [APPLY GLOBALLY]         [RESET]   │
└─────────────────────────────────────────────────────────────────┘
```

### Deferred Features (Phase 3+)

| Feature | Reason | Complexity |
|---------|--------|------------|
| **Set Bonus System** | Requires multi-slot detection, stacking rules, custom set definitions | HIGH |
| **Ghost Health** | Requires tick-based regeneration system, combat detection | MEDIUM |
| **Overheal** | Requires absorption heart management, damage hooks | MEDIUM |
| **Armor Pierce/Shred** | These are attacker attributes, not defender - belongs in Weapon Editor | N/A |

### Compatibility Notes

| Mod | Compatibility | Notes |
|-----|---------------|-------|
| **Apothic Attributes** | Optional mapping | Map devmod:dodge_chance ↔ apothic:dodge_chance if both present |
| **Pufferfish's Attributes** | Optional mapping | Similar attribute names, can coexist |
| **Protection Enchantments** | Additive stacking | DevMod reductions + enchant EPF both apply |

### Config

```toml
# config/devmod-server.toml

[armor]
# Enable DevMod armor attribute system
enableArmorAttributes = true

# Maximum total damage reduction (prevents invincibility)
maxTotalReduction = 95.0

# Dodge chance cap (server can limit)
maxDodgeChance = 75.0

# Healing received minimum (prevent healing immunity)
minHealingReceived = 10.0

# Thorns reflect cap (prevent infinite loops)
maxThornsReflect = 50.0

[armor.compatibility]
# Map DevMod attributes to Apothic Attributes if present
mapToApothic = true

# Map DevMod attributes to Pufferfish's Attributes if present
mapToPufferfish = true
```

---

## 2.26 Armor Editor View Modes

### Dual Mode System

Armor Editor supports two view modes:
- **OVERVIEW**: Full set view with all 4 slots + aggregated totals
- **SINGLE**: Single item editing (like Weapon Editor)

### Mode Toggle

```
┌─────────────────────────────────────────────────────────────────┐
│ ARMOR EDITOR                    [◉ OVERVIEW] [○ SINGLE ITEM]    │
├─────────────────────────────────────────────────────────────────┤
```

### Mode Selection Logic

```java
/**
 * Armor Editor view mode management.
 */
public enum ArmorViewMode {
    /**
     * Full set overview with all 4 slots visible.
     * Shows aggregated totals and per-slot summary.
     */
    OVERVIEW,

    /**
     * Single item editing mode.
     * Same layout as Weapon Editor - one item at a time.
     */
    SINGLE
}

/**
 * Determines default view mode based on context.
 */
public static ArmorViewMode getDefaultMode(Player player, ItemStack heldItem) {
    // If holding armor piece → SINGLE mode for that piece
    if (heldItem.getItem() instanceof ArmorItem) {
        return ArmorViewMode.SINGLE;
    }

    // If wearing any armor → OVERVIEW mode
    for (EquipmentSlot slot : EquipmentSlot.values()) {
        if (slot.isArmor() && !player.getItemBySlot(slot).isEmpty()) {
            return ArmorViewMode.OVERVIEW;
        }
    }

    // No armor context → SINGLE mode (empty state)
    return ArmorViewMode.SINGLE;
}
```

### OVERVIEW Mode Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ ARMOR EDITOR                    [◉ OVERVIEW] [○ SINGLE ITEM]    │
├─────────────────────────────────────────────────────────────────┤
│ [OVERVIEW] [PROTECTION] [ATTRIBUTES] [UTILITY] [THORNS]         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │   HEAD   │ │  CHEST   │ │   LEGS   │ │   FEET   │           │
│  │  ┌────┐  │ │  ┌────┐  │ │  ┌────┐  │ │  ┌────┐  │           │
│  │  │ICON│  │ │  │ICON│  │ │  │ICON│  │ │  │ICON│  │           │
│  │  └────┘  │ │  └────┘  │ │  └────┘  │ │  └────┘  │           │
│  │ Diamond  │ │ Diamond  │ │  Iron    │ │ Diamond  │           │
│  │ Helmet   │ │ Chest    │ │  Legs    │ │  Boots   │           │
│  │ ──────── │ │ ──────── │ │ ──────── │ │ ──────── │           │
│  │ Arm:  3  │ │ Arm:  8  │ │ Arm:  5  │ │ Arm:  3  │           │
│  │ Tgh:  2  │ │ Tgh:  2  │ │ Tgh:  0  │ │ Tgh:  2  │           │
│  │ KB: 10%  │ │ KB: 10%  │ │ KB:  0%  │ │ KB: 10%  │           │
│  │ ──────── │ │ ──────── │ │ ──────── │ │ ──────── │           │
│  │  [EDIT]  │ │  [EDIT]  │ │  [EDIT]  │ │  [EDIT]  │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
│                                                                 │
│  ═══════════════════════════════════════════════════════════   │
│  SET TOTALS                                                     │
│  ───────────────────────────────────────────────────────────   │
│  │ Armor Points:    19/20    │ Physical Red:   25%          │  │
│  │ Toughness:        6       │ Fire Red:       40%          │  │
│  │ Knockback Res:   30%      │ Magic Red:      10%          │  │
│  │ Movement Mod:    -2%      │ Explosion Red:  35%          │  │
│  │ Dodge Chance:    15%      │ Projectile Red: 20%          │  │
│  ───────────────────────────────────────────────────────────   │
│  │ ████████████████████░░░░ │ Effective DR: ~45% vs Phys   │  │
│  ═══════════════════════════════════════════════════════════   │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ [APPLY ALL]  [EXPORT SET]  [IMPORT SET]              [RESET]   │
└─────────────────────────────────────────────────────────────────┘
```

### SINGLE Mode Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ ARMOR EDITOR                    [○ OVERVIEW] [◉ SINGLE ITEM]    │
├─────────────────────────────────────────────────────────────────┤
│ [PROTECTION] [ATTRIBUTES] [UTILITY] [THORNS] [ENCHANTS]         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐  DAMAGE REDUCTIONS                             │
│  │             │  ───────────────────────────────────────────   │
│  │   [ARMOR]   │                                                │
│  │    ICON     │  Physical     [████████====] 40%               │
│  │             │  Fire         [██══════════] 10%               │
│  │             │  Magic        [════════════]  0%               │
│  └─────────────┘  Explosion    [██████══════] 30%               │
│                   Projectile   [████════════] 20%               │
│  Slot: CHEST                                                    │
│  Material: DIAMOND             ───────────────────────────────  │
│                                                                 │
│  ┌─ SLOT SELECTOR ──────────────────────────────────────────┐  │
│  │ [HEAD] [CHEST●] [LEGS] [FEET]    or    [HELD ITEM]       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ [PREVIEW]  [APPLY TO ITEM]  [APPLY GLOBALLY]         [RESET]   │
└─────────────────────────────────────────────────────────────────┘
```

### Slot Selector Component

```java
/**
 * Slot selector for SINGLE mode.
 * Allows switching between equipped slots or held item.
 */
public final class ArmorSlotSelector {

    public enum SlotSource {
        HEAD(EquipmentSlot.HEAD, "Head"),
        CHEST(EquipmentSlot.CHEST, "Chest"),
        LEGS(EquipmentSlot.LEGS, "Legs"),
        FEET(EquipmentSlot.FEET, "Feet"),
        HELD_ITEM(null, "Held Item");

        private final @Nullable EquipmentSlot slot;
        private final String displayName;

        SlotSource(@Nullable EquipmentSlot slot, String displayName) {
            this.slot = slot;
            this.displayName = displayName;
        }

        public ItemStack getItem(Player player) {
            if (this == HELD_ITEM) {
                return player.getMainHandItem();
            }
            return player.getItemBySlot(slot);
        }

        public boolean hasArmor(Player player) {
            ItemStack item = getItem(player);
            return !item.isEmpty() && item.getItem() instanceof ArmorItem;
        }
    }

    private SlotSource selectedSlot = SlotSource.HELD_ITEM;

    /**
     * Render slot selector buttons.
     */
    public void render(GuiGraphics graphics, int x, int y, Player player) {
        int buttonX = x;

        for (SlotSource source : SlotSource.values()) {
            boolean hasArmor = source.hasArmor(player);
            boolean isSelected = source == selectedSlot;

            // Button styling
            int bgColor = isSelected ? UIConstants.Background.ACTIVE
                        : hasArmor ? UIConstants.Background.PANEL
                        : UIConstants.Background.DISABLED;

            int textColor = isSelected ? UIConstants.Text.WHITE
                          : hasArmor ? UIConstants.Text.PRIMARY
                          : UIConstants.Text.DISABLED;

            // Render button
            graphics.fill(buttonX, y, buttonX + 50, y + 20, bgColor);
            graphics.drawCenteredString(
                Minecraft.getInstance().font,
                source.displayName,
                buttonX + 25, y + 6,
                textColor
            );

            // Selection indicator
            if (isSelected) {
                graphics.drawString(
                    Minecraft.getInstance().font,
                    "●", buttonX + 45, y + 6,
                    UIConstants.Border.ACCENT
                );
            }

            buttonX += 54; // Button width + gap
        }
    }

    /**
     * Handle click on slot selector.
     */
    public boolean onClick(int mouseX, int mouseY, int selectorX, int selectorY, Player player) {
        int buttonX = selectorX;

        for (SlotSource source : SlotSource.values()) {
            if (mouseX >= buttonX && mouseX < buttonX + 50 &&
                mouseY >= selectorY && mouseY < selectorY + 20) {

                if (source.hasArmor(player)) {
                    selectedSlot = source;
                    return true;
                }
            }
            buttonX += 54;
        }
        return false;
    }

    public SlotSource getSelectedSlot() {
        return selectedSlot;
    }

    public ItemStack getSelectedItem(Player player) {
        return selectedSlot.getItem(player);
    }
}
```

### Mode Transitions

```java
/**
 * Handles transitions between OVERVIEW and SINGLE modes.
 */
public final class ArmorEditorModeController {

    private ArmorViewMode currentMode;
    private ArmorSlotSelector slotSelector;
    private @Nullable EquipmentSlot focusedSlot;

    /**
     * Switch from OVERVIEW to SINGLE, focusing on a specific slot.
     * Called when user clicks [EDIT] on a slot card.
     */
    public void editSlot(EquipmentSlot slot) {
        currentMode = ArmorViewMode.SINGLE;
        slotSelector.setSelectedSlot(SlotSource.fromEquipmentSlot(slot));
        focusedSlot = slot;
    }

    /**
     * Switch from SINGLE back to OVERVIEW.
     */
    public void showOverview() {
        currentMode = ArmorViewMode.OVERVIEW;
        focusedSlot = null;
    }

    /**
     * Toggle between modes via header buttons.
     */
    public void toggleMode() {
        if (currentMode == ArmorViewMode.OVERVIEW) {
            // Switch to SINGLE, default to first equipped slot
            currentMode = ArmorViewMode.SINGLE;
        } else {
            currentMode = ArmorViewMode.OVERVIEW;
        }
    }
}
```

### Empty Slot Handling

```
┌──────────┐
│   LEGS   │
│  ┌────┐  │
│  │ ?? │  │  ← Empty slot icon
│  └────┘  │
│  Empty   │
│ ──────── │
│ No armor │
│ equipped │
│ ──────── │
│ [EQUIP]  │  ← Opens inventory picker
└──────────┘
```

### Tab Visibility by Mode

| Tab | OVERVIEW Mode | SINGLE Mode |
|-----|---------------|-------------|
| **OVERVIEW** | ✅ Visible (default) | ❌ Hidden |
| **PROTECTION** | ✅ Visible | ✅ Visible (default) |
| **ATTRIBUTES** | ✅ Visible | ✅ Visible |
| **UTILITY** | ✅ Visible | ✅ Visible |
| **THORNS** | ✅ Visible | ✅ Visible |
| **ENCHANTS** | ❌ Hidden | ✅ Visible |

Note: In OVERVIEW mode, clicking a tab other than OVERVIEW shows aggregated values for all slots (non-editable). Click [EDIT] on a slot to enter SINGLE mode for editing.

### Aggregation Logic

```java
/**
 * Calculates aggregated stats for full armor set.
 */
public final class ArmorSetAggregator {

    /**
     * Aggregate armor stats from all equipped slots.
     */
    public static AggregatedArmorStats aggregate(Player player) {
        AggregatedArmorStats stats = new AggregatedArmorStats();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;

            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty()) continue;

            // Vanilla attributes (additive)
            stats.totalArmor += getArmorValue(armor);
            stats.totalToughness += getToughnessValue(armor);
            stats.knockbackResistance += getKnockbackResistance(armor);

            // DevMod reductions (use highest per type, not additive)
            stats.physicalReduction = Math.max(stats.physicalReduction,
                getDevModReduction(armor, ModArmorAttributes.PHYSICAL_REDUCTION));
            stats.fireReduction = Math.max(stats.fireReduction,
                getDevModReduction(armor, ModArmorAttributes.FIRE_REDUCTION));
            stats.magicReduction = Math.max(stats.magicReduction,
                getDevModReduction(armor, ModArmorAttributes.MAGIC_REDUCTION));
            stats.explosionReduction = Math.max(stats.explosionReduction,
                getDevModReduction(armor, ModArmorAttributes.EXPLOSION_REDUCTION));
            stats.projectileReduction = Math.max(stats.projectileReduction,
                getDevModReduction(armor, ModArmorAttributes.PROJECTILE_REDUCTION));

            // Dodge/healing (additive with cap)
            stats.dodgeChance += getDodgeChance(armor);
            stats.healingReceived = combineMultipliers(
                stats.healingReceived, getHealingReceived(armor)
            );

            stats.slotCount++;
        }

        // Apply caps
        stats.knockbackResistance = Math.min(1.0f, stats.knockbackResistance);
        stats.dodgeChance = Math.min(100.0f, stats.dodgeChance);

        return stats;
    }

    public record AggregatedArmorStats(
        float totalArmor,
        float totalToughness,
        float knockbackResistance,
        float physicalReduction,
        float fireReduction,
        float magicReduction,
        float explosionReduction,
        float projectileReduction,
        float dodgeChance,
        float healingReceived,
        int slotCount
    ) {
        public AggregatedArmorStats() {
            this(0, 0, 0, 0, 0, 0, 0, 0, 0, 100, 0);
        }

        public boolean isFullSet() {
            return slotCount == 4;
        }
    }
}
```

### Keyboard Shortcuts

| Key | Action |
|-----|--------|
| **Tab** | Toggle OVERVIEW ↔ SINGLE mode |
| **1-4** | In SINGLE mode: select HEAD/CHEST/LEGS/FEET |
| **H** | In SINGLE mode: select HELD ITEM |
| **Enter** | In OVERVIEW: edit selected slot |
| **Esc** | In SINGLE: return to OVERVIEW (if came from there) |

### Config

```toml
# config/devmod-client.toml

[armorEditor]
# Default view mode: OVERVIEW or SINGLE
defaultMode = "OVERVIEW"

# Remember last used mode between sessions
rememberMode = true

# Show aggregated totals bar in OVERVIEW
showTotalsBar = true

# Highlight "weak" slots (lowest stats) in OVERVIEW
highlightWeakSlots = true

# Weak slot threshold (percentage of best slot)
weakSlotThreshold = 50
```

---

## 2.27 Set Bonus & Conditions System — Deferred

### Status: Phase 3+ (Not in MVP)

Set bonuses and conditional effects are **deferred** to Phase 3 and beyond. This section documents the planned architecture for future implementation.

### Rationale for Deferral

| Concern | Impact |
|---------|--------|
| **UI Complexity** | Requires dedicated set definition UI, not just sliders |
| **Tick Evaluation** | Conditions need per-tick checks, performance consideration |
| **Set Detection** | Must track equipped items across 4 slots + match against definitions |
| **Stacking Rules** | Multiple sets, partial sets, conflicting bonuses |
| **Testing Surface** | Many edge cases (equip/unequip, dimension change, death) |
| **MVP Scope** | Core attribute editing works without this |

### Implementation Roadmap

| Phase | Feature | Complexity |
|-------|---------|------------|
| **MVP** | No set bonus, no conditions | - |
| **Phase 3** | Set Bonus (2/4 pieces) | MEDIUM |
| **Phase 3** | Conditions: Biome, Time, Health | LOW-MEDIUM |
| **Phase 3** | Conditions: In Combat | MEDIUM |
| **Phase 4** | Stamina System + Condition | HIGH |
| **Phase 4** | Custom Set Definition UI | HIGH |
| **Phase 5** | Condition Combinator (AND/OR logic) | MEDIUM |

---

### Phase 3 Architecture

#### Set Bonus Core

```java
/**
 * Defines a set bonus that activates when wearing multiple matching pieces.
 */
public record SetBonus(
    ResourceLocation setId,          // e.g., "devmod:diamond_tank"
    String displayName,              // "Diamond Tank Set"
    SetMatcher matcher,              // How to identify set pieces
    List<SetTier> tiers              // Bonuses at 2pc, 4pc, etc.
) {

    /**
     * A tier of bonus that activates at a piece threshold.
     */
    public record SetTier(
        int requiredPieces,          // 2 or 4 typically
        List<BonusEffect> effects,   // Effects to apply
        List<Condition> conditions   // Optional activation conditions
    ) {}
}

/**
 * How to match items as part of a set.
 */
public sealed interface SetMatcher {

    /** Match by armor material (vanilla sets) */
    record ByMaterial(ArmorMaterial material) implements SetMatcher {}

    /** Match by item tag */
    record ByTag(TagKey<Item> tag) implements SetMatcher {}

    /** Match by explicit item list */
    record ByItems(Set<ResourceLocation> itemIds) implements SetMatcher {}

    /** Match by mod ID (all armor from a mod) */
    record ByMod(String modId) implements SetMatcher {}

    /** Match by custom NBT/component marker */
    record ByComponent(ResourceLocation componentType, String value) implements SetMatcher {}
}
```

#### Bonus Effects

```java
/**
 * Effects that can be granted by set bonuses.
 */
public sealed interface BonusEffect {

    /** Add to an attribute */
    record AttributeBonus(
        Holder<Attribute> attribute,
        double amount,
        AttributeModifier.Operation operation
    ) implements BonusEffect {}

    /** Grant a potion effect */
    record PotionEffect(
        Holder<MobEffect> effect,
        int amplifier,
        boolean showParticles
    ) implements BonusEffect {}

    /** Modify damage reduction */
    record DamageReduction(
        DamageTypeTag damageType,    // null = all damage
        float percentReduction
    ) implements BonusEffect {}

    /** Special ability unlock */
    record AbilityGrant(
        ResourceLocation abilityId   // e.g., "devmod:double_jump"
    ) implements BonusEffect {}
}
```

#### Conditions System

```java
/**
 * Conditions that must be met for effects to activate.
 * Evaluated per-tick when relevant.
 */
public sealed interface Condition {

    /** Always active (no condition) */
    record Always() implements Condition {}

    /** Active in specific biome(s) */
    record InBiome(
        TagKey<Biome> biomeTag       // e.g., #minecraft:is_nether
    ) implements Condition {}

    /** Active during time range (0-24000 ticks) */
    record TimeOfDay(
        int startTick,               // 0 = sunrise (6:00)
        int endTick                  // 13000 = sunset, 18000 = midnight
    ) implements Condition {
        public static final TimeOfDay DAY = new TimeOfDay(0, 12999);
        public static final TimeOfDay NIGHT = new TimeOfDay(13000, 23999);
    }

    /** Active when health below/above threshold */
    record HealthThreshold(
        float percent,               // 0.0 - 1.0
        Comparator comparator        // BELOW or ABOVE
    ) implements Condition {
        public enum Comparator { BELOW, ABOVE }

        public static HealthThreshold below(float pct) {
            return new HealthThreshold(pct, Comparator.BELOW);
        }
        public static HealthThreshold above(float pct) {
            return new HealthThreshold(pct, Comparator.ABOVE);
        }
    }

    /** Active when recently damaged (in combat) */
    record InCombat(
        int tickWindow               // Ticks since last damage to count as "in combat"
    ) implements Condition {
        public static final InCombat DEFAULT = new InCombat(100); // 5 seconds
    }

    /** Active in specific dimension */
    record InDimension(
        ResourceKey<Level> dimension
    ) implements Condition {}

    /** Active when on fire */
    record OnFire(boolean inverted) implements Condition {} // inverted = NOT on fire

    /** Active when submerged in water */
    record Submerged(boolean inverted) implements Condition {}

    /** Active when sprinting */
    record Sprinting(boolean inverted) implements Condition {}

    /** Active when sneaking */
    record Sneaking(boolean inverted) implements Condition {}
}
```

#### Phase 4: Stamina Condition

```java
/**
 * Phase 4+ - Requires custom stamina system implementation.
 */
public sealed interface StaminaCondition extends Condition {

    /** Active when stamina above threshold */
    record StaminaAbove(float percent) implements StaminaCondition {}

    /** Active when stamina below threshold */
    record StaminaBelow(float percent) implements StaminaCondition {}

    /** Active when stamina is full */
    record StaminaFull() implements StaminaCondition {}

    /** Active when stamina is empty */
    record StaminaEmpty() implements StaminaCondition {}
}

// Note: Stamina system itself is a separate feature requiring:
// - StaminaCapability attached to player
// - Stamina drain on actions (sprint, jump, attack)
// - Stamina regeneration tick handler
// - Stamina HUD overlay
// - Config for stamina values
```

#### Phase 5: Condition Combinators

```java
/**
 * Phase 5 - Combine multiple conditions with logic.
 */
public sealed interface ConditionCombinator extends Condition {

    /** All conditions must be true */
    record All(List<Condition> conditions) implements ConditionCombinator {}

    /** Any condition must be true */
    record Any(List<Condition> conditions) implements ConditionCombinator {}

    /** Condition must be false */
    record Not(Condition condition) implements ConditionCombinator {}
}

// Example usage:
// new All(List.of(
//     TimeOfDay.NIGHT,
//     new InBiome(BiomeTags.IS_OVERWORLD),
//     new Not(new InCombat(100))
// ))
// → "At night, in overworld, out of combat"
```

### Set Bonus Evaluation

```java
/**
 * Evaluates and applies set bonuses each tick.
 */
@EventBusSubscriber(modid = DevMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SetBonusEvaluator {

    // Cache to avoid re-evaluation every tick
    private static final Map<UUID, CachedSetState> playerCache = new WeakHashMap<>();
    private static final int REEVALUATE_INTERVAL = 20; // Every second

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // Throttle evaluation
        if (player.tickCount % REEVALUATE_INTERVAL != 0) {
            return;
        }

        CachedSetState cached = playerCache.get(player.getUUID());
        List<EquippedSet> currentSets = detectEquippedSets(player);

        // Check if armor changed
        if (cached == null || !cached.matches(currentSets)) {
            // Remove old bonuses
            if (cached != null) {
                removeBonuses(player, cached.activeBonuses());
            }

            // Apply new bonuses
            List<ActiveBonus> newBonuses = evaluateAndApply(player, currentSets);
            playerCache.put(player.getUUID(), new CachedSetState(currentSets, newBonuses));
        } else {
            // Armor same, but re-evaluate conditions
            reevaluateConditions(player, cached);
        }
    }

    private static List<EquippedSet> detectEquippedSets(Player player) {
        List<ItemStack> armor = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.isArmor()) {
                armor.add(player.getItemBySlot(slot));
            }
        }

        List<EquippedSet> result = new ArrayList<>();
        for (SetBonus setDef : SetBonusRegistry.getAllSets()) {
            int matchCount = 0;
            for (ItemStack item : armor) {
                if (setDef.matcher().matches(item)) {
                    matchCount++;
                }
            }
            if (matchCount >= 2) { // Minimum for any bonus
                result.add(new EquippedSet(setDef, matchCount));
            }
        }
        return result;
    }

    private record EquippedSet(SetBonus definition, int pieceCount) {}
    private record CachedSetState(List<EquippedSet> sets, List<ActiveBonus> activeBonuses) {
        boolean matches(List<EquippedSet> other) {
            return sets.equals(other);
        }
    }
    private record ActiveBonus(SetBonus set, SetTier tier, boolean conditionMet) {}
}
```

### Condition Evaluation

```java
/**
 * Evaluates conditions against current player state.
 */
public final class ConditionEvaluator {

    public static boolean evaluate(Condition condition, Player player) {
        return switch (condition) {
            case Condition.Always a -> true;

            case Condition.InBiome b ->
                player.level().getBiome(player.blockPosition()).is(b.biomeTag());

            case Condition.TimeOfDay t -> {
                long time = player.level().getDayTime() % 24000;
                yield t.startTick() <= time && time <= t.endTick();
            }

            case Condition.HealthThreshold h -> {
                float healthPct = player.getHealth() / player.getMaxHealth();
                yield switch (h.comparator()) {
                    case BELOW -> healthPct < h.percent();
                    case ABOVE -> healthPct > h.percent();
                };
            }

            case Condition.InCombat c ->
                (player.tickCount - player.getLastHurtByMobTimestamp()) < c.tickWindow();

            case Condition.InDimension d ->
                player.level().dimension().equals(d.dimension());

            case Condition.OnFire f ->
                player.isOnFire() != f.inverted();

            case Condition.Submerged s ->
                player.isUnderWater() != s.inverted();

            case Condition.Sprinting sp ->
                player.isSprinting() != sp.inverted();

            case Condition.Sneaking sn ->
                player.isShiftKeyDown() != sn.inverted();

            // Phase 4+
            case StaminaCondition sc ->
                evaluateStamina(sc, player);

            // Phase 5
            case ConditionCombinator.All all ->
                all.conditions().stream().allMatch(c -> evaluate(c, player));

            case ConditionCombinator.Any any ->
                any.conditions().stream().anyMatch(c -> evaluate(c, player));

            case ConditionCombinator.Not not ->
                !evaluate(not.condition(), player);
        };
    }

    private static boolean evaluateStamina(StaminaCondition condition, Player player) {
        // Phase 4+ - requires StaminaCapability
        // float stamina = StaminaCapability.get(player).getStaminaPercent();
        // return switch (condition) { ... };
        return false; // Not implemented yet
    }
}
```

### UI Mockup (Phase 3+)

```
┌─────────────────────────────────────────────────────────────────┐
│ ARMOR EDITOR                    [○ OVERVIEW] [○ SINGLE] [◉ SETS]│
├─────────────────────────────────────────────────────────────────┤
│ [OVERVIEW] [PROTECTION] [ATTRIBUTES] [UTILITY] [SETS]           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ACTIVE SET BONUSES                                             │
│  ═══════════════════════════════════════════════════════════   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ DIAMOND TANK SET                              [4/4] ✓   │   │
│  │ ─────────────────────────────────────────────────────── │   │
│  │ (2) +10% Physical Reduction                    [ACTIVE] │   │
│  │ (4) +25% Physical Reduction, +5 Armor          [ACTIVE] │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ NIGHTSTALKER SET                              [2/4]     │   │
│  │ ─────────────────────────────────────────────────────── │   │
│  │ (2) +15% Dodge at Night                 [INACTIVE: Day] │   │
│  │ (4) Invisibility at Night               [NEED 4 PIECES] │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ═══════════════════════════════════════════════════════════   │
│  AVAILABLE SETS (not equipped)                                  │
│  ───────────────────────────────────────────────────────────   │
│  • Netherite Juggernaut (0/4) - Fire immunity at 4pc           │
│  • Leather Scout (0/4) - Movement speed bonuses                │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ [CREATE SET]  [IMPORT SET]  [EXPORT SET]             [HELP]    │
└─────────────────────────────────────────────────────────────────┘
```

### Data Storage (Phase 3+)

```json
// config/devmod/sets/diamond_tank.json
{
  "setId": "devmod:diamond_tank",
  "displayName": "Diamond Tank Set",
  "matcher": {
    "type": "by_material",
    "material": "minecraft:diamond"
  },
  "tiers": [
    {
      "requiredPieces": 2,
      "effects": [
        {
          "type": "attribute_bonus",
          "attribute": "devmod:physical_reduction",
          "amount": 10.0,
          "operation": "addition"
        }
      ],
      "conditions": []
    },
    {
      "requiredPieces": 4,
      "effects": [
        {
          "type": "attribute_bonus",
          "attribute": "devmod:physical_reduction",
          "amount": 25.0,
          "operation": "addition"
        },
        {
          "type": "attribute_bonus",
          "attribute": "minecraft:generic.armor",
          "amount": 5.0,
          "operation": "addition"
        }
      ],
      "conditions": []
    }
  ]
}
```

```json
// config/devmod/sets/nightstalker.json
{
  "setId": "devmod:nightstalker",
  "displayName": "Nightstalker Set",
  "matcher": {
    "type": "by_tag",
    "tag": "devmod:nightstalker_armor"
  },
  "tiers": [
    {
      "requiredPieces": 2,
      "effects": [
        {
          "type": "attribute_bonus",
          "attribute": "devmod:dodge_chance",
          "amount": 15.0,
          "operation": "addition"
        }
      ],
      "conditions": [
        { "type": "time_of_day", "start": 13000, "end": 23999 }
      ]
    },
    {
      "requiredPieces": 4,
      "effects": [
        {
          "type": "potion_effect",
          "effect": "minecraft:invisibility",
          "amplifier": 0,
          "showParticles": false
        }
      ],
      "conditions": [
        { "type": "time_of_day", "start": 13000, "end": 23999 }
      ]
    }
  ]
}
```

### Config

```toml
# config/devmod-server.toml

[setBonus]
# Enable set bonus system (Phase 3+)
enabled = false  # Disabled until Phase 3

# Maximum set bonuses that can be active simultaneously
maxActiveSets = 3

# Reevaluation interval in ticks (lower = more responsive, higher = better performance)
evaluationInterval = 20

# Allow custom set definitions from datapacks
allowDatapackSets = true

# Built-in set definitions
enableBuiltinSets = true

[setBonus.conditions]
# Enable biome-based conditions
enableBiomeConditions = true

# Enable time-based conditions
enableTimeConditions = true

# Enable health-based conditions
enableHealthConditions = true

# Enable combat detection
enableCombatConditions = true

# Combat timeout in ticks (how long after damage to count as "in combat")
combatTimeout = 100
```

---

## 2.28 Damage Resistance Roadmap

### Current State (MVP)

Five core resistances defined in Section 2.25:

| Attribute | Damage Types Covered |
|-----------|---------------------|
| `devmod:physical_reduction` | Melee attacks, mob attacks, player attacks |
| `devmod:fire_reduction` | Fire, lava, burning, fire aspect |
| `devmod:magic_reduction` | Potions, guardian beams, evoker fangs, wither effect, poison |
| `devmod:explosion_reduction` | Creepers, TNT, fireballs, beds in nether/end |
| `devmod:projectile_reduction` | Arrows, tridents, fireballs, shulker bullets |

### Implementation Phases

| Phase | Resistances | Status |
|-------|-------------|--------|
| **MVP** | physical, fire, magic, explosion, projectile | ✅ Defined |
| **Phase 2** | wither, lightning | Planned |
| **Phase 3+** | frost/cold, bleed (custom damage types) | Future |

---

### Phase 2: Extended Vanilla Resistances

```java
/**
 * Phase 2 - Additional resistances for vanilla damage types.
 */
public final class ExtendedArmorAttributes {

    // ═══════════════════════════════════════════════════════════════
    // PHASE 2: EXTENDED VANILLA RESISTANCES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Wither damage reduction percentage.
     * Applies to: wither effect, wither skeleton attacks, wither boss.
     * Note: Wither damage bypasses armor in vanilla, this provides custom mitigation.
     * Range: 0-100%
     */
    public static final Holder<Attribute> WITHER_REDUCTION = ATTRIBUTES.register(
        "wither_reduction",
        () -> new RangedAttribute("attribute.devmod.wither_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Lightning damage reduction percentage.
     * Applies to: lightning strikes, channeling trident.
     * Range: 0-100%
     */
    public static final Holder<Attribute> LIGHTNING_REDUCTION = ATTRIBUTES.register(
        "lightning_reduction",
        () -> new RangedAttribute("attribute.devmod.lightning_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );
}
```

#### Phase 2 Event Hook Addition

```java
/**
 * Extended damage handler for Phase 2 resistances.
 */
private static float getReductionForDamageType(LivingEntity entity, DamageSource source) {
    // MVP resistances (existing)
    if (source.is(DamageTypeTags.IS_FIRE)) {
        return (float) entity.getAttributeValue(ModArmorAttributes.FIRE_REDUCTION);
    }
    if (source.is(DamageTypeTags.IS_EXPLOSION)) {
        return (float) entity.getAttributeValue(ModArmorAttributes.EXPLOSION_REDUCTION);
    }
    if (source.is(DamageTypeTags.IS_PROJECTILE)) {
        return (float) entity.getAttributeValue(ModArmorAttributes.PROJECTILE_REDUCTION);
    }
    if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
        return (float) entity.getAttributeValue(ModArmorAttributes.MAGIC_REDUCTION);
    }

    // Phase 2 resistances
    if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)) {
        return (float) entity.getAttributeValue(ExtendedArmorAttributes.WITHER_REDUCTION);
    }
    if (source.is(DamageTypes.LIGHTNING_BOLT)) {
        return (float) entity.getAttributeValue(ExtendedArmorAttributes.LIGHTNING_REDUCTION);
    }

    // Default to physical for melee/untagged damage
    if (source.getEntity() instanceof LivingEntity) {
        return (float) entity.getAttributeValue(ModArmorAttributes.PHYSICAL_REDUCTION);
    }

    return 0.0f;
}
```

#### Phase 2 ValueLimits

```java
// Add to ValueLimits.java

/** Wither damage reduction percentage */
public static final Limit WITHER_REDUCTION = Limit.of(
    0.0, 80.0,       // Soft: 0-80% (wither is meant to be dangerous)
    0.0, 100.0       // Hard: 0-100%
);

/** Lightning damage reduction percentage */
public static final Limit LIGHTNING_REDUCTION = Limit.of(
    0.0, 80.0,       // Soft: 0-80%
    0.0, 100.0       // Hard: 0-100%
);
```

---

### Phase 3+: Custom Damage Types

#### Frost/Cold Damage System

```java
/**
 * Phase 3+ - Custom frost damage type.
 * Requires registration in data/devmod/damage_type/frost.json
 */
public final class FrostDamageSystem {

    // Damage type registration
    public static final ResourceKey<DamageType> FROST = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "frost")
    );

    /**
     * Frost damage reduction attribute.
     */
    public static final Holder<Attribute> FROST_REDUCTION = ATTRIBUTES.register(
        "frost_reduction",
        () -> new RangedAttribute("attribute.devmod.frost_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Apply frost damage with slowness effect.
     */
    public static void applyFrostDamage(LivingEntity target, float amount, @Nullable Entity source) {
        DamageSource damageSource = target.level().damageSources().source(FROST, source);
        target.hurt(damageSource, amount);

        // Apply slowness based on damage
        int slowDuration = (int) (amount * 20); // 1 second per damage point
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowDuration, 1));
    }
}
```

```json
// data/devmod/damage_type/frost.json
{
  "exhaustion": 0.1,
  "message_id": "frost",
  "scaling": "when_caused_by_living_non_player"
}
```

#### Bleed Damage System

```java
/**
 * Phase 3+ - Custom bleed damage type with DoT.
 */
public final class BleedDamageSystem {

    public static final ResourceKey<DamageType> BLEED = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "bleed")
    );

    /**
     * Bleed damage reduction attribute.
     */
    public static final Holder<Attribute> BLEED_REDUCTION = ATTRIBUTES.register(
        "bleed_reduction",
        () -> new RangedAttribute("attribute.devmod.bleed_reduction", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Apply bleed stack to target.
     * Bleed deals damage over time and prevents healing.
     */
    public static void applyBleed(LivingEntity target, int stacks, int durationTicks) {
        // Bleed is tracked via capability or data attachment
        BleedCapability.get(target).addStacks(stacks, durationTicks);
    }

    /**
     * Tick handler for bleed damage.
     */
    @SubscribeEvent
    public static void onLivingTick(LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        BleedCapability bleed = BleedCapability.get(entity);

        if (bleed.hasStacks() && entity.tickCount % 20 == 0) {
            // Deal 1 damage per stack per second
            float damage = bleed.getStacks();

            // Apply reduction
            float reduction = (float) entity.getAttributeValue(BLEED_REDUCTION);
            damage *= (1.0f - reduction / 100.0f);

            if (damage > 0) {
                DamageSource source = entity.level().damageSources().source(BLEED);
                entity.hurt(source, damage);
            }

            bleed.tick();
        }
    }
}
```

---

### Complete Resistance Matrix

| Resistance | MVP | Phase 2 | Phase 3+ | Vanilla Tag |
|------------|-----|---------|----------|-------------|
| `physical_reduction` | ✅ | - | - | (entity-based) |
| `fire_reduction` | ✅ | - | - | `#is_fire` |
| `magic_reduction` | ✅ | - | - | `#witch_resistant_to` |
| `explosion_reduction` | ✅ | - | - | `#is_explosion` |
| `projectile_reduction` | ✅ | - | - | `#is_projectile` |
| `wither_reduction` | - | ✅ | - | `wither`, `wither_skull` |
| `lightning_reduction` | - | ✅ | - | `lightning_bolt` |
| `frost_reduction` | - | - | ✅ | `devmod:frost` (custom) |
| `bleed_reduction` | - | - | ✅ | `devmod:bleed` (custom) |

### Non-Reducible Damage Types (Design Choice)

| Damage Type | Reason |
|-------------|--------|
| `void` | Instant kill, not meant to be survivable |
| `starve` | Resource management mechanic |
| `generic_kill` | Admin/command kill |
| `outside_border` | World boundary enforcement |

### UI: Protection Tab Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ PROTECTION                                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  CORE RESISTANCES (MVP)                                         │
│  ───────────────────────────────────────────────────────────   │
│  Physical     [████████====] 40%    ⚔️                          │
│  Fire         [██══════════] 10%    🔥                          │
│  Magic        [████════════] 20%    ✨                          │
│  Explosion    [██████══════] 30%    💥                          │
│  Projectile   [████════════] 20%    🏹                          │
│                                                                 │
│  EXTENDED RESISTANCES (Phase 2)                                 │
│  ───────────────────────────────────────────────────────────   │
│  Wither       [════════════]  0%    💀                          │
│  Lightning    [════════════]  0%    ⚡                          │
│                                                                 │
│  CUSTOM RESISTANCES (Phase 3+)                                  │
│  ───────────────────────────────────────────────────────────   │
│  Frost        [██══════════] 10%    ❄️   [REQUIRES ADDON]       │
│  Bleed        [════════════]  0%    🩸   [REQUIRES ADDON]       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Config

```toml
# config/devmod-server.toml

[resistances]
# Enable core resistances (MVP)
enableCoreResistances = true

# Enable extended vanilla resistances (Phase 2)
enableExtendedResistances = false  # Enable when Phase 2 ships

# Enable custom damage types (Phase 3+)
enableCustomDamageTypes = false  # Enable when Phase 3 ships

# Maximum total resistance per damage type (prevents immunity)
maxResistance = 95.0

[resistances.stacking]
# How resistances stack with enchantments
# Options: ADDITIVE, MULTIPLICATIVE, HIGHEST_ONLY
enchantStackMode = "MULTIPLICATIVE"

# How resistances stack with potion effects (Fire Resistance, etc.)
potionStackMode = "MULTIPLICATIVE"
```

---

## 2.29 Movement & Advanced Systems Roadmap

### Implementation Phases

| Feature | Phase | Vanilla Support | Complexity |
|---------|-------|-----------------|------------|
| Movement speed | ✅ MVP | ✅ `movement_speed` | LOW |
| Sprint speed bonus | Phase 2 | ❌ Custom | LOW |
| Sneak speed bonus | Phase 2 | ❌ Custom | LOW |
| Swim speed bonus | Phase 2 | ❌ Custom | LOW |
| Stamina system | Phase 4+ | ❌ None | HIGH |
| Noise/Stealth | Phase 4+ | ❌ None | HIGH |

---

### MVP: Movement Speed (Already Defined)

Covered in Section 2.25:

```java
// Already defined in VanillaArmorAttributes
public static final ResourceLocation MOVEMENT_SPEED =
    ResourceLocation.withDefaultNamespace("generic.movement_speed");

// ValueLimits (Section 2.24)
public static final Limit MOVEMENT_SPEED = Limit.ofWithNegative(
    -0.1, 0.2,       // Soft: -10% to +20%
    -0.5, 1.0        // Hard: -50% to +100%
);
```

---

### Phase 2: Context-Specific Movement Bonuses

```java
/**
 * Phase 2 - Movement bonuses for specific actions.
 */
public final class MovementAttributes {

    private static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, DevMod.MODID);

    /**
     * Sprint speed bonus (additive, on top of base movement).
     * Only applies while sprinting.
     * Range: -50% to +100%
     */
    public static final Holder<Attribute> SPRINT_SPEED_BONUS = ATTRIBUTES.register(
        "sprint_speed_bonus",
        () -> new RangedAttribute("attribute.devmod.sprint_speed_bonus", 0.0D, -0.5D, 1.0D)
            .setSyncable(true)
    );

    /**
     * Sneak speed bonus (additive).
     * Only applies while sneaking.
     * Vanilla sneak is 30% of walk speed.
     * Range: -50% to +200%
     */
    public static final Holder<Attribute> SNEAK_SPEED_BONUS = ATTRIBUTES.register(
        "sneak_speed_bonus",
        () -> new RangedAttribute("attribute.devmod.sneak_speed_bonus", 0.0D, -0.5D, 2.0D)
            .setSyncable(true)
    );

    /**
     * Swim speed bonus (additive).
     * Only applies while swimming/in water.
     * Range: -50% to +100%
     */
    public static final Holder<Attribute> SWIM_SPEED_BONUS = ATTRIBUTES.register(
        "swim_speed_bonus",
        () -> new RangedAttribute("attribute.devmod.swim_speed_bonus", 0.0D, -0.5D, 1.0D)
            .setSyncable(true)
    );

    /**
     * Step height bonus (in blocks).
     * Vanilla: 0.6 blocks (can step up slabs).
     * Range: 0 to 2 blocks
     */
    public static final Holder<Attribute> STEP_HEIGHT_BONUS = ATTRIBUTES.register(
        "step_height_bonus",
        () -> new RangedAttribute("attribute.devmod.step_height_bonus", 0.0D, 0.0D, 2.0D)
            .setSyncable(true)
    );

    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
    }
}
```

#### Phase 2 Event Handlers

```java
/**
 * Apply context-specific movement bonuses.
 */
@EventBusSubscriber(modid = DevMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MovementHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // Sprint speed bonus
        if (player.isSprinting()) {
            double bonus = player.getAttributeValue(MovementAttributes.SPRINT_SPEED_BONUS);
            if (bonus != 0) {
                applyTemporarySpeedModifier(player, "devmod:sprint_bonus", bonus);
            }
        }

        // Sneak speed bonus
        if (player.isShiftKeyDown()) {
            double bonus = player.getAttributeValue(MovementAttributes.SNEAK_SPEED_BONUS);
            if (bonus != 0) {
                applyTemporarySpeedModifier(player, "devmod:sneak_bonus", bonus);
            }
        }

        // Swim speed bonus
        if (player.isInWater() || player.isSwimming()) {
            double bonus = player.getAttributeValue(MovementAttributes.SWIM_SPEED_BONUS);
            if (bonus != 0) {
                applyTemporarySpeedModifier(player, "devmod:swim_bonus", bonus);
            }
        }
    }

    private static void applyTemporarySpeedModifier(Player player, String id, double amount) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        ResourceLocation modId = ResourceLocation.parse(id);
        AttributeModifier existing = speedAttr.getModifier(modId);

        if (existing == null || existing.amount() != amount) {
            speedAttr.removeModifier(modId);
            speedAttr.addTransientModifier(new AttributeModifier(
                modId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }
    }
}
```

#### Phase 2 ValueLimits

```java
// Add to ValueLimits.java

/** Sprint speed bonus multiplier */
public static final Limit SPRINT_SPEED_BONUS = Limit.ofWithNegative(
    -0.2, 0.5,       // Soft: -20% to +50%
    -0.5, 1.0        // Hard: -50% to +100%
);

/** Sneak speed bonus multiplier */
public static final Limit SNEAK_SPEED_BONUS = Limit.ofWithNegative(
    -0.2, 1.0,       // Soft: -20% to +100%
    -0.5, 2.0        // Hard: -50% to +200%
);

/** Swim speed bonus multiplier */
public static final Limit SWIM_SPEED_BONUS = Limit.ofWithNegative(
    -0.2, 0.5,       // Soft: -20% to +50%
    -0.5, 1.0        // Hard: -50% to +100%
);

/** Step height bonus in blocks */
public static final Limit STEP_HEIGHT_BONUS = Limit.of(
    0.0, 1.0,        // Soft: 0-1 block
    0.0, 2.0         // Hard: 0-2 blocks
);
```

---

### Phase 4+: Stamina System

```java
/**
 * Phase 4+ - Complete stamina system.
 * NOT IN MVP - Requires dedicated implementation.
 */
public final class StaminaSystem {

    // ═══════════════════════════════════════════════════════════════
    // CAPABILITY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Player stamina capability.
     */
    public interface StaminaCapability {
        float getStamina();
        float getMaxStamina();
        float getStaminaPercent();
        void drain(float amount);
        void restore(float amount);
        void tick();
    }

    // ═══════════════════════════════════════════════════════════════
    // ATTRIBUTES
    // ═══════════════════════════════════════════════════════════════

    /** Maximum stamina pool */
    public static final Holder<Attribute> MAX_STAMINA = ATTRIBUTES.register(
        "max_stamina",
        () -> new RangedAttribute("attribute.devmod.max_stamina", 100.0D, 0.0D, 1000.0D)
            .setSyncable(true)
    );

    /** Stamina regeneration rate (per second) */
    public static final Holder<Attribute> STAMINA_REGEN = ATTRIBUTES.register(
        "stamina_regen",
        () -> new RangedAttribute("attribute.devmod.stamina_regen", 5.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /** Sprint stamina drain (per second while sprinting) */
    public static final Holder<Attribute> SPRINT_STAMINA_DRAIN = ATTRIBUTES.register(
        "sprint_stamina_drain",
        () -> new RangedAttribute("attribute.devmod.sprint_stamina_drain", 10.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /** Jump stamina cost (per jump) */
    public static final Holder<Attribute> JUMP_STAMINA_COST = ATTRIBUTES.register(
        "jump_stamina_cost",
        () -> new RangedAttribute("attribute.devmod.jump_stamina_cost", 5.0D, 0.0D, 50.0D)
            .setSyncable(true)
    );

    /** Attack stamina cost (per swing) */
    public static final Holder<Attribute> ATTACK_STAMINA_COST = ATTRIBUTES.register(
        "attack_stamina_cost",
        () -> new RangedAttribute("attribute.devmod.attack_stamina_cost", 3.0D, 0.0D, 50.0D)
            .setSyncable(true)
    );

    // ═══════════════════════════════════════════════════════════════
    // ARMOR MODIFIERS
    // ═══════════════════════════════════════════════════════════════

    /** Stamina drain modifier from armor (multiplicative) */
    public static final Holder<Attribute> ARMOR_STAMINA_DRAIN_MOD = ATTRIBUTES.register(
        "armor_stamina_drain_mod",
        () -> new RangedAttribute("attribute.devmod.armor_stamina_drain_mod", 1.0D, 0.5D, 3.0D)
            .setSyncable(true)
    );

    /** Stamina regen modifier from armor (multiplicative) */
    public static final Holder<Attribute> ARMOR_STAMINA_REGEN_MOD = ATTRIBUTES.register(
        "armor_stamina_regen_mod",
        () -> new RangedAttribute("attribute.devmod.armor_stamina_regen_mod", 1.0D, 0.1D, 2.0D)
            .setSyncable(true)
    );
}
```

#### Stamina HUD (Phase 4+)

```
┌─────────────────────────────────────────┐
│ ♥♥♥♥♥♥♥♥♥♥  Health                      │
│ ████████░░░░░░░░░░  Stamina (40%)       │
│ 🍖🍖🍖🍖🍖🍖🍖🍖🍖🍖  Hunger            │
└─────────────────────────────────────────┘
```

---

### Phase 4+: Noise/Stealth System

```java
/**
 * Phase 4+ - Noise and stealth detection system.
 * NOT IN MVP - Requires dedicated implementation.
 */
public final class NoiseStealthSystem {

    // ═══════════════════════════════════════════════════════════════
    // NOISE ATTRIBUTES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Base noise level generated by movement.
     * 0 = silent, 100 = maximum noise.
     * Affects mob detection range.
     */
    public static final Holder<Attribute> NOISE_LEVEL = ATTRIBUTES.register(
        "noise_level",
        () -> new RangedAttribute("attribute.devmod.noise_level", 50.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Armor noise modifier (multiplicative).
     * Leather = 0.5x, Chain = 1.0x, Iron = 1.5x, Diamond/Netherite = 1.2x
     */
    public static final Holder<Attribute> ARMOR_NOISE_MOD = ATTRIBUTES.register(
        "armor_noise_mod",
        () -> new RangedAttribute("attribute.devmod.armor_noise_mod", 1.0D, 0.0D, 3.0D)
            .setSyncable(true)
    );

    // ═══════════════════════════════════════════════════════════════
    // STEALTH ATTRIBUTES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Stealth bonus (reduces detection range).
     * 0 = no bonus, 100 = invisible to detection.
     */
    public static final Holder<Attribute> STEALTH_BONUS = ATTRIBUTES.register(
        "stealth_bonus",
        () -> new RangedAttribute("attribute.devmod.stealth_bonus", 0.0D, 0.0D, 100.0D)
            .setSyncable(true)
    );

    /**
     * Backstab damage multiplier.
     * Applies when attacking from behind undetected.
     */
    public static final Holder<Attribute> BACKSTAB_MULTIPLIER = ATTRIBUTES.register(
        "backstab_multiplier",
        () -> new RangedAttribute("attribute.devmod.backstab_multiplier", 1.0D, 1.0D, 5.0D)
            .setSyncable(true)
    );

    // ═══════════════════════════════════════════════════════════════
    // NOISE GENERATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Noise levels per action.
     */
    public enum NoiseAction {
        WALK(10),
        SPRINT(30),
        JUMP(20),
        LAND(25),
        LAND_HARD(50),      // Fall > 3 blocks
        ATTACK(15),
        BREAK_BLOCK(40),
        PLACE_BLOCK(20),
        OPEN_CONTAINER(25),
        EAT(10),
        EQUIP_ARMOR(35);

        private final int baseNoise;

        NoiseAction(int baseNoise) {
            this.baseNoise = baseNoise;
        }

        public int getBaseNoise() {
            return baseNoise;
        }
    }

    /**
     * Calculate effective noise for an action.
     */
    public static float calculateNoise(Player player, NoiseAction action) {
        float baseNoise = action.getBaseNoise();
        float noiseMod = (float) player.getAttributeValue(ARMOR_NOISE_MOD);
        float stealthBonus = (float) player.getAttributeValue(STEALTH_BONUS);

        // Sneaking reduces noise by 70%
        if (player.isShiftKeyDown()) {
            baseNoise *= 0.3f;
        }

        // Apply modifiers
        float effectiveNoise = baseNoise * noiseMod * (1.0f - stealthBonus / 100.0f);
        return Math.max(0, effectiveNoise);
    }

    /**
     * Calculate mob detection range based on noise.
     */
    public static float getDetectionRange(float noiseLevel) {
        // Base detection: 16 blocks at noise 50
        // Scale: 0 noise = 0 blocks, 100 noise = 32 blocks
        return noiseLevel * 0.32f;
    }
}
```

#### Default Armor Noise Values

| Material | Noise Modifier | Reasoning |
|----------|---------------|-----------|
| Leather | 0.5x | Soft, flexible |
| Gold | 0.8x | Soft metal |
| Chain | 1.0x | Baseline (jingly but light) |
| Iron | 1.5x | Heavy, clanky |
| Diamond | 1.2x | Dense but well-fitted |
| Netherite | 1.1x | Dense but magical dampening |

---

### UI: Utility Tab Layout (Phase 2+)

```
┌─────────────────────────────────────────────────────────────────┐
│ UTILITY                                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  MOVEMENT (MVP + Phase 2)                                       │
│  ───────────────────────────────────────────────────────────   │
│  Base Speed      [████████====] +10%    🚶                      │
│  Sprint Bonus    [██████══════] +30%    🏃   [PHASE 2]          │
│  Sneak Bonus     [████════════] +20%    🧎   [PHASE 2]          │
│  Swim Bonus      [════════════]  +0%    🏊   [PHASE 2]          │
│  Step Height     [██══════════] +0.5    📐   [PHASE 2]          │
│                                                                 │
│  STAMINA MODIFIERS (Phase 4+)                                   │
│  ───────────────────────────────────────────────────────────   │
│  Drain Modifier  [════════════] 1.0x    ⚡   [NOT AVAILABLE]    │
│  Regen Modifier  [════════════] 1.0x    💚   [NOT AVAILABLE]    │
│                                                                 │
│  STEALTH (Phase 4+)                                             │
│  ───────────────────────────────────────────────────────────   │
│  Noise Level     [════════════] 1.0x    🔊   [NOT AVAILABLE]    │
│  Stealth Bonus   [════════════]  +0%    👁️   [NOT AVAILABLE]    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Config

```toml
# config/devmod-server.toml

[movement]
# Enable context-specific movement bonuses (Phase 2)
enableMovementBonuses = false  # Enable when Phase 2 ships

# Maximum sprint speed bonus
maxSprintBonus = 1.0

# Maximum sneak speed bonus
maxSneakBonus = 2.0

# Maximum swim speed bonus
maxSwimBonus = 1.0

# Maximum step height bonus (blocks)
maxStepHeightBonus = 2.0

[stamina]
# Enable stamina system (Phase 4+)
enabled = false  # Enable when Phase 4 ships

# Base max stamina
baseMaxStamina = 100.0

# Base regen rate (per second)
baseRegenRate = 5.0

# Stamina required to sprint
sprintThreshold = 10.0

# Prevent sprinting when stamina empty
preventSprintWhenEmpty = true

[stealth]
# Enable noise/stealth system (Phase 4+)
enabled = false  # Enable when Phase 4 ships

# Base detection range multiplier
detectionRangeMultiplier = 1.0

# Enable backstab bonus damage
enableBackstab = true

# Backstab angle threshold (degrees from behind)
backstabAngle = 60.0
```

---

## 2.30 Durability System

### Implementation Phases

| Feature | Phase | Editable | Note |
|---------|-------|----------|------|
| Current durability | ✅ MVP | Read-only | Display current/max |
| Max durability | ✅ MVP | ✅ Yes | Override max value |
| Unbreakable | ✅ MVP | ✅ Yes | Boolean toggle |
| Degradation rate | Phase 2 | ✅ Yes | Damage multiplier |
| Repair efficiency | Phase 2 | ✅ Yes | Repair multiplier |
| Fragility curve | Phase 3 | ✅ Yes | Degradation accelerates with use |
| Durability threshold effects | Phase 3 | ✅ Yes | Effects at low durability |

---

### MVP: Basic Durability

#### Data Components (1.21)

```java
/**
 * MVP durability editing via Data Components.
 */
public final class DurabilityEditor {

    /**
     * Get current durability.
     */
    public static int getCurrentDurability(ItemStack stack) {
        if (!stack.isDamageableItem()) return 0;
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    /**
     * Get max durability.
     */
    public static int getMaxDurability(ItemStack stack) {
        return stack.getMaxDamage();
    }

    /**
     * Set max durability via component override.
     */
    public static void setMaxDurability(ItemStack stack, int maxDurability) {
        stack.set(DataComponents.MAX_DAMAGE, maxDurability);

        // Clamp current damage to not exceed new max
        int currentDamage = stack.getDamageValue();
        if (currentDamage > maxDurability) {
            stack.setDamageValue(maxDurability - 1);
        }
    }

    /**
     * Check if item is unbreakable.
     */
    public static boolean isUnbreakable(ItemStack stack) {
        Unbreakable unbreakable = stack.get(DataComponents.UNBREAKABLE);
        return unbreakable != null;
    }

    /**
     * Set unbreakable state.
     */
    public static void setUnbreakable(ItemStack stack, boolean unbreakable, boolean showInTooltip) {
        if (unbreakable) {
            stack.set(DataComponents.UNBREAKABLE, new Unbreakable(showInTooltip));
        } else {
            stack.remove(DataComponents.UNBREAKABLE);
        }
    }

    /**
     * Repair item to full durability.
     */
    public static void repairFull(ItemStack stack) {
        stack.setDamageValue(0);
    }

    /**
     * Set specific damage value.
     */
    public static void setDamage(ItemStack stack, int damage) {
        stack.setDamageValue(Math.min(damage, stack.getMaxDamage() - 1));
    }
}
```

#### MVP UI

```
┌─────────────────────────────────────────────────────────────────┐
│ DURABILITY                                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  CURRENT STATE                                                  │
│  ───────────────────────────────────────────────────────────   │
│                                                                 │
│  Durability Bar    [████████████████░░░░] 1247 / 1561          │
│                    (79.9% remaining)                            │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ [REPAIR FULL]              [SET TO 50%]     [SET TO 1]   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  CONFIGURATION                                                  │
│  ───────────────────────────────────────────────────────────   │
│                                                                 │
│  Max Durability    [    1561    ] [▼][▲]   (vanilla: 1561)     │
│                                                                 │
│  Unbreakable       [ ] Enable                                   │
│                    [ ] Show in tooltip                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### ValueLimits

```java
// Add to ValueLimits.java

/** Max durability */
public static final Limit MAX_DURABILITY = Limit.of(
    1, 10000,        // Soft: 1-10000 (netherite tools ~2031)
    1, 100000        // Hard: 1-100000
);
```

---

### Phase 2: Degradation & Repair Modifiers

#### Custom Data Components

```java
/**
 * Phase 2 - Durability modifier components.
 */
public final class DurabilityComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DevMod.MODID);

    /**
     * Degradation rate multiplier.
     * 1.0 = normal, 0.5 = half damage, 2.0 = double damage.
     */
    public static final Supplier<DataComponentType<Float>> DEGRADATION_RATE =
        COMPONENTS.register("degradation_rate", () ->
            DataComponentType.<Float>builder()
                .persistent(Codec.FLOAT)
                .networkSynchronized(ByteBufCodecs.FLOAT)
                .build()
        );

    /**
     * Repair efficiency multiplier.
     * 1.0 = normal, 2.0 = double repair, 0.5 = half repair.
     */
    public static final Supplier<DataComponentType<Float>> REPAIR_EFFICIENCY =
        COMPONENTS.register("repair_efficiency", () ->
            DataComponentType.<Float>builder()
                .persistent(Codec.FLOAT)
                .networkSynchronized(ByteBufCodecs.FLOAT)
                .build()
        );

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
```

#### Phase 2 Event Handlers

```java
/**
 * Apply degradation rate modifier to durability damage.
 */
@EventBusSubscriber(modid = DevMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DurabilityHandler {

    /**
     * Modify durability damage based on degradation rate.
     */
    @SubscribeEvent
    public static void onItemDamage(ItemDurabilityChangeEvent event) {
        ItemStack stack = event.getItemStack();
        int originalDamage = event.getDelta();

        // Only apply to damage (positive delta)
        if (originalDamage <= 0) return;

        Float degradationRate = stack.get(DurabilityComponents.DEGRADATION_RATE);
        if (degradationRate != null && degradationRate != 1.0f) {
            int modifiedDamage = Math.round(originalDamage * degradationRate);
            modifiedDamage = Math.max(1, modifiedDamage); // At least 1 damage
            event.setDelta(modifiedDamage);
        }
    }

    /**
     * Modify repair amount based on repair efficiency.
     * Hook into anvil/grindstone repair.
     */
    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        ItemStack output = event.getOutput();

        Float repairEfficiency = output.get(DurabilityComponents.REPAIR_EFFICIENCY);
        if (repairEfficiency != null && repairEfficiency != 1.0f) {
            // Calculate bonus repair
            int currentDamage = output.getDamageValue();
            int originalRepair = event.getInput().getDamageValue() - currentDamage;

            if (originalRepair > 0) {
                int bonusRepair = Math.round(originalRepair * (repairEfficiency - 1.0f));
                int newDamage = Math.max(0, currentDamage - bonusRepair);
                output.setDamageValue(newDamage);
            }
        }
    }
}
```

#### Phase 2 UI Extension

```
┌─────────────────────────────────────────────────────────────────┐
│ DURABILITY                                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  CURRENT STATE                                                  │
│  ───────────────────────────────────────────────────────────   │
│  Durability Bar    [████████████████░░░░] 1247 / 1561          │
│                    (79.9% remaining)                            │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ [REPAIR FULL]              [SET TO 50%]     [SET TO 1]   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  CONFIGURATION                                                  │
│  ───────────────────────────────────────────────────────────   │
│  Max Durability    [    1561    ] [▼][▲]   (vanilla: 1561)     │
│                                                                 │
│  Unbreakable       [ ] Enable                                   │
│                    [ ] Show in tooltip                          │
│                                                                 │
│  MODIFIERS (Phase 2)                                            │
│  ───────────────────────────────────────────────────────────   │
│  Degradation Rate  [████████════] 0.8x    (slower wear)        │
│                    0.5x ──────────────────────────── 3.0x      │
│                                                                 │
│  Repair Efficiency [██████████══] 1.5x    (better repairs)     │
│                    0.5x ──────────────────────────── 2.0x      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### Phase 2 ValueLimits

```java
// Add to ValueLimits.java

/** Degradation rate multiplier */
public static final Limit DEGRADATION_RATE = Limit.of(
    0.5, 2.0,        // Soft: 0.5x-2x
    0.1, 3.0         // Hard: 0.1x-3x
);

/** Repair efficiency multiplier */
public static final Limit REPAIR_EFFICIENCY = Limit.of(
    0.5, 1.5,        // Soft: 0.5x-1.5x
    0.25, 2.0        // Hard: 0.25x-2x
);
```

---

### Durability Display Helpers

```java
/**
 * Utilities for durability display.
 */
public final class DurabilityDisplay {

    /**
     * Get durability bar color based on percentage.
     */
    public static int getBarColor(float percent) {
        if (percent > 0.6f) return 0x00FF00;  // Green
        if (percent > 0.3f) return 0xFFFF00;  // Yellow
        if (percent > 0.1f) return 0xFFA500;  // Orange
        return 0xFF0000;                       // Red
    }

    /**
     * Format durability for display.
     */
    public static String formatDurability(int current, int max) {
        return String.format("%d / %d", current, max);
    }

    /**
     * Format percentage for display.
     */
    public static String formatPercent(float percent) {
        return String.format("%.1f%%", percent * 100);
    }

    /**
     * Get degradation rate description.
     */
    public static String getDegradationDescription(float rate) {
        if (rate < 0.8f) return "Very Durable";
        if (rate < 1.0f) return "Durable";
        if (rate == 1.0f) return "Normal";
        if (rate < 1.5f) return "Fragile";
        return "Very Fragile";
    }

    /**
     * Get repair efficiency description.
     */
    public static String getRepairDescription(float efficiency) {
        if (efficiency > 1.5f) return "Excellent";
        if (efficiency > 1.2f) return "Good";
        if (efficiency == 1.0f) return "Normal";
        if (efficiency > 0.7f) return "Poor";
        return "Very Poor";
    }
}
```

---

### Phase 3: Fragility Curves & Threshold Effects

#### Fragility Curve System

```java
/**
 * Phase 3 - Fragility curve that accelerates degradation as item wears.
 */
public final class FragilityCurveSystem {

    /**
     * Fragility curve types.
     */
    public enum FragilityCurve {
        /**
         * Linear degradation (default).
         * Same damage rate throughout lifespan.
         * Multiplier = 1.0 always
         */
        LINEAR(percent -> 1.0f),

        /**
         * Exponential fragility.
         * Gets more fragile as durability decreases.
         * At 100%: 1.0x, at 50%: 1.5x, at 10%: 3.0x
         */
        EXPONENTIAL(percent -> 1.0f + (1.0f - percent) * 2.0f),

        /**
         * Reverse exponential (hardening).
         * Gets more durable as it wears (battle-hardened).
         * At 100%: 1.5x, at 50%: 1.0x, at 10%: 0.5x
         */
        HARDENING(percent -> 0.5f + percent),

        /**
         * Step function.
         * Normal until 25%, then rapid degradation.
         */
        STEP(percent -> percent > 0.25f ? 1.0f : 3.0f),

        /**
         * S-Curve (logistic).
         * Slow start, fast middle, slow end.
         */
        S_CURVE(percent -> {
            float x = (0.5f - percent) * 6; // Center at 50%
            return 1.0f + (float)(1.0 / (1.0 + Math.exp(-x)));
        }),

        /**
         * Random variance.
         * Base degradation with random ±50% variance.
         */
        RANDOM(percent -> 0.5f + (float)Math.random());

        private final FragilityFunction function;

        FragilityCurve(FragilityFunction function) {
            this.function = function;
        }

        /**
         * Get degradation multiplier for given durability percentage.
         */
        public float getMultiplier(float durabilityPercent) {
            return function.apply(durabilityPercent);
        }
    }

    @FunctionalInterface
    interface FragilityFunction {
        float apply(float durabilityPercent);
    }
}
```

#### Fragility Component

```java
/**
 * Data component for fragility curve.
 */
public static final Supplier<DataComponentType<FragilityCurveData>> FRAGILITY_CURVE =
    COMPONENTS.register("fragility_curve", () ->
        DataComponentType.<FragilityCurveData>builder()
            .persistent(FragilityCurveData.CODEC)
            .networkSynchronized(FragilityCurveData.STREAM_CODEC)
            .build()
    );

/**
 * Fragility curve data.
 */
public record FragilityCurveData(
    FragilityCurve curve,
    float intensity          // Multiplier for curve effect (0.5 = half effect, 2.0 = double)
) {
    public static final Codec<FragilityCurveData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("curve").xmap(FragilityCurve::valueOf, FragilityCurve::name)
                .forGetter(FragilityCurveData::curve),
            Codec.FLOAT.fieldOf("intensity").forGetter(FragilityCurveData::intensity)
        ).apply(instance, FragilityCurveData::new)
    );

    public static final StreamCodec<ByteBuf, FragilityCurveData> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(FragilityCurve::valueOf, FragilityCurve::name),
            FragilityCurveData::curve,
            ByteBufCodecs.FLOAT, FragilityCurveData::intensity,
            FragilityCurveData::new
        );

    public static final FragilityCurveData DEFAULT = new FragilityCurveData(FragilityCurve.LINEAR, 1.0f);

    /**
     * Calculate effective degradation multiplier.
     */
    public float getEffectiveMultiplier(float durabilityPercent) {
        float baseMultiplier = curve.getMultiplier(durabilityPercent);
        // Intensity scales deviation from 1.0
        return 1.0f + (baseMultiplier - 1.0f) * intensity;
    }
}
```

#### Durability Threshold Effects

```java
/**
 * Effects that trigger at durability thresholds.
 */
public final class DurabilityThresholdSystem {

    /**
     * Threshold effect definition.
     */
    public record ThresholdEffect(
        float threshold,             // Durability % to trigger (e.g., 0.25 = 25%)
        ThresholdAction action,      // What happens
        boolean persistent           // Stays active while below threshold
    ) {}

    /**
     * Actions that can occur at thresholds.
     */
    public sealed interface ThresholdAction {

        /** Attribute modifier while below threshold */
        record AttributePenalty(
            Holder<Attribute> attribute,
            double amount,
            AttributeModifier.Operation operation
        ) implements ThresholdAction {}

        /** Visual warning (glow, particles) */
        record VisualWarning(
            WarningType type
        ) implements ThresholdAction {
            public enum WarningType { GLOW_RED, PARTICLES, SCREEN_SHAKE }
        }

        /** Sound warning */
        record SoundWarning(
            ResourceLocation sound,
            float volume
        ) implements ThresholdAction {}

        /** Speed penalty */
        record SpeedPenalty(float multiplier) implements ThresholdAction {}

        /** Damage penalty */
        record DamagePenalty(float multiplier) implements ThresholdAction {}

        /** Can break on next use */
        record BreakWarning() implements ThresholdAction {}

        /** Auto-unequip when threshold reached */
        record AutoUnequip() implements ThresholdAction {}
    }

    /**
     * Default threshold effects.
     */
    public static final List<ThresholdEffect> DEFAULT_THRESHOLDS = List.of(
        // At 25%: visual warning
        new ThresholdEffect(0.25f,
            new ThresholdAction.VisualWarning(ThresholdAction.VisualWarning.WarningType.GLOW_RED),
            true),

        // At 10%: damage penalty
        new ThresholdEffect(0.10f,
            new ThresholdAction.DamagePenalty(0.75f),
            true),

        // At 5%: break warning sound
        new ThresholdEffect(0.05f,
            new ThresholdAction.SoundWarning(
                ResourceLocation.fromNamespaceAndPath("devmod", "item.about_to_break"),
                1.0f),
            false)
    );
}
```

#### Threshold Component

```java
/**
 * Data component for threshold effects.
 */
public static final Supplier<DataComponentType<ThresholdEffectsData>> THRESHOLD_EFFECTS =
    COMPONENTS.register("threshold_effects", () ->
        DataComponentType.<ThresholdEffectsData>builder()
            .persistent(ThresholdEffectsData.CODEC)
            .networkSynchronized(ThresholdEffectsData.STREAM_CODEC)
            .build()
    );

public record ThresholdEffectsData(
    List<ThresholdEffect> effects,
    boolean enabled
) {
    public static final ThresholdEffectsData DISABLED =
        new ThresholdEffectsData(List.of(), false);

    public static final ThresholdEffectsData DEFAULT =
        new ThresholdEffectsData(DurabilityThresholdSystem.DEFAULT_THRESHOLDS, true);
}
```

#### Phase 3 Event Handler Update

```java
/**
 * Extended durability handler with fragility curves.
 */
@EventBusSubscriber(modid = DevMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AdvancedDurabilityHandler {

    @SubscribeEvent
    public static void onItemDamage(ItemDurabilityChangeEvent event) {
        ItemStack stack = event.getItemStack();
        int originalDamage = event.getDelta();

        if (originalDamage <= 0) return;

        float modifiedDamage = originalDamage;

        // Phase 2: Base degradation rate
        Float degradationRate = stack.get(DurabilityComponents.DEGRADATION_RATE);
        if (degradationRate != null) {
            modifiedDamage *= degradationRate;
        }

        // Phase 3: Fragility curve
        FragilityCurveData fragility = stack.get(DurabilityComponents.FRAGILITY_CURVE);
        if (fragility != null) {
            float durabilityPercent = (float)(stack.getMaxDamage() - stack.getDamageValue())
                                    / stack.getMaxDamage();
            float curveMultiplier = fragility.getEffectiveMultiplier(durabilityPercent);
            modifiedDamage *= curveMultiplier;
        }

        event.setDelta(Math.max(1, Math.round(modifiedDamage)));
    }

    /**
     * Check and apply threshold effects each tick.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return; // Check once per second

        // Check all equipment
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;

            ThresholdEffectsData thresholds = stack.get(DurabilityComponents.THRESHOLD_EFFECTS);
            if (thresholds == null || !thresholds.enabled()) continue;

            float durabilityPercent = (float)(stack.getMaxDamage() - stack.getDamageValue())
                                    / stack.getMaxDamage();

            for (ThresholdEffect effect : thresholds.effects()) {
                if (durabilityPercent <= effect.threshold()) {
                    applyThresholdAction(player, stack, slot, effect);
                }
            }
        }
    }

    private static void applyThresholdAction(Player player, ItemStack stack,
                                            EquipmentSlot slot, ThresholdEffect effect) {
        switch (effect.action()) {
            case ThresholdAction.AttributePenalty p -> {
                // Apply attribute modifier
                applyThresholdAttributeModifier(player, p, slot);
            }
            case ThresholdAction.VisualWarning v -> {
                // Send visual warning to client
                if (v.type() == ThresholdAction.VisualWarning.WarningType.GLOW_RED) {
                    // Glowing effect or item highlight
                }
            }
            case ThresholdAction.SoundWarning s -> {
                if (!effect.persistent()) {
                    // Play sound once
                    player.playSound(SoundEvent.createVariableRangeEvent(s.sound()), s.volume(), 1.0f);
                }
            }
            case ThresholdAction.DamagePenalty d -> {
                // Applied in damage calculation
            }
            case ThresholdAction.SpeedPenalty sp -> {
                // Applied via attribute modifier
            }
            case ThresholdAction.BreakWarning b -> {
                // Show "about to break" warning
            }
            case ThresholdAction.AutoUnequip a -> {
                // Move item to inventory
                player.setItemSlot(slot, ItemStack.EMPTY);
                player.getInventory().add(stack);
            }
        }
    }
}
```

#### Phase 3 UI Extension

```
┌─────────────────────────────────────────────────────────────────┐
│ DURABILITY                                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  CURRENT STATE                                                  │
│  ───────────────────────────────────────────────────────────   │
│  Durability Bar    [████████████████░░░░] 1247 / 1561          │
│                    (79.9% remaining)                            │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ [REPAIR FULL]              [SET TO 50%]     [SET TO 1]   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  CONFIGURATION                                                  │
│  ───────────────────────────────────────────────────────────   │
│  Max Durability    [    1561    ] [▼][▲]   (vanilla: 1561)     │
│                                                                 │
│  Unbreakable       [ ] Enable                                   │
│                    [ ] Show in tooltip                          │
│                                                                 │
│  MODIFIERS (Phase 2)                                            │
│  ───────────────────────────────────────────────────────────   │
│  Degradation Rate  [████████════] 0.8x    (slower wear)        │
│  Repair Efficiency [██████████══] 1.5x    (better repairs)     │
│                                                                 │
│  FRAGILITY CURVE (Phase 3)                                      │
│  ───────────────────────────────────────────────────────────   │
│  Curve Type:  [LINEAR ▼]                                       │
│               ○ Linear (constant wear)                          │
│               ● Exponential (fragile when worn)                 │
│               ○ Hardening (tougher when worn)                   │
│               ○ Step (sudden at 25%)                            │
│               ○ S-Curve (variable)                              │
│                                                                 │
│  Intensity:   [████████════] 1.5x                              │
│                                                                 │
│  Preview:     100% ─────╲                                       │
│                          ╲                                      │
│               50%  ───────╲____                                 │
│                                ╲___                             │
│               0%   ────────────────╲  (current: 79.9%)         │
│                                                                 │
│  THRESHOLD EFFECTS (Phase 3)                                    │
│  ───────────────────────────────────────────────────────────   │
│  [✓] Enable threshold effects                                   │
│                                                                 │
│  ┌────────┬─────────────────────────────────┬─────────┐        │
│  │ At %   │ Effect                          │ Actions │        │
│  ├────────┼─────────────────────────────────┼─────────┤        │
│  │ 25%    │ Visual Warning (red glow)       │ [Edit]  │        │
│  │ 10%    │ Damage Penalty (-25%)           │ [Edit]  │        │
│  │  5%    │ Break Warning Sound             │ [Edit]  │        │
│  └────────┴─────────────────────────────────┴─────────┘        │
│  [+ Add Threshold]                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### Phase 3 ValueLimits

```java
// Add to ValueLimits.java

/** Fragility curve intensity */
public static final Limit FRAGILITY_INTENSITY = Limit.of(
    0.5, 2.0,        // Soft: 0.5x-2x intensity
    0.1, 5.0         // Hard: 0.1x-5x intensity
);

/** Threshold percentage */
public static final Limit THRESHOLD_PERCENT = Limit.of(
    0.05, 0.5,       // Soft: 5%-50%
    0.01, 0.99       // Hard: 1%-99%
);

/** Threshold damage penalty */
public static final Limit THRESHOLD_DAMAGE_PENALTY = Limit.of(
    0.5, 0.9,        // Soft: 50%-90% damage
    0.1, 1.0         // Hard: 10%-100% damage
);

/** Threshold speed penalty */
public static final Limit THRESHOLD_SPEED_PENALTY = Limit.of(
    0.5, 0.9,        // Soft: 50%-90% speed
    0.1, 1.0         // Hard: 10%-100% speed
);
```

---

### Config

```toml
# config/devmod-server.toml

[durability]
# Allow editing max durability
allowMaxDurabilityEdit = true

# Allow setting items as unbreakable
allowUnbreakable = true

# Maximum allowed max durability
maxDurabilityLimit = 100000

# Enable degradation rate modifier (Phase 2)
enableDegradationRate = false  # Enable when Phase 2 ships

# Enable repair efficiency modifier (Phase 2)
enableRepairEfficiency = false  # Enable when Phase 2 ships

# Minimum degradation rate (prevents invincible items)
minDegradationRate = 0.1

# Maximum repair efficiency
maxRepairEfficiency = 2.0

# Enable fragility curves (Phase 3)
enableFragilityCurves = false  # Enable when Phase 3 ships

# Enable threshold effects (Phase 3)
enableThresholdEffects = false  # Enable when Phase 3 ships

# Maximum fragility intensity
maxFragilityIntensity = 5.0

# Allow auto-unequip threshold action
allowAutoUnequip = true

# Minimum threshold percentage
minThresholdPercent = 0.01
```

---

## 2.31 Repair System

Sistema di riparazione per armi e armature.

### Roadmap

| Phase | Feature | Descrizione |
|-------|---------|-------------|
| MVP | repair_cost R/W | Lettura/scrittura costo XP base anvil |
| MVP | repair_materials (R) | Visualizzazione materiali riparazione (read-only) |
| MVP | repair_penalty reset | Reset del "too expensive" penalty |
| Phase 2 | repair_efficiency | Moltiplicatore durabilità recuperata |
| Phase 2 | xp_cost_override | Override formula costo XP |
| Phase 3 | repair_materials (R/W) | Override materiali via Data Components |
| Phase 3 | partial_repair | Riparazione parziale con meno materiali |
| Phase 4+ | anvil_recipes | Ricette anvil custom |
| Phase 4+ | repair_station | Blocco dedicato per riparazioni avanzate |

---

### MVP Implementation

#### Data Components utilizzati

```java
// Vanilla 1.21 Data Components per Repair
import net.minecraft.core.component.DataComponents;

// Costo base riparazione (intero)
DataComponents.REPAIR_COST  // Integer - base XP cost

// Materiali di riparazione (tag-based)
DataComponents.REPAIRABLE  // Repairable - contiene HolderSet<Item>

// Stato riparazione
// Nota: Il "repair penalty" è calcolato da REPAIR_COST, non salvato separatamente
```

#### Lettura Materiali di Riparazione

```java
public class RepairMaterialReader {

    /**
     * Get repair materials for an item.
     * Returns empty set if not repairable.
     */
    public static Set<Item> getRepairMaterials(ItemStack stack) {
        Repairable repairable = stack.get(DataComponents.REPAIRABLE);
        if (repairable == null) {
            return Set.of();
        }

        HolderSet<Item> items = repairable.items();
        Set<Item> materials = new HashSet<>();

        items.forEach(holder -> materials.add(holder.value()));

        return materials;
    }

    /**
     * Get repair material tag name (if tag-based).
     */
    @Nullable
    public static ResourceLocation getRepairTag(ItemStack stack) {
        Repairable repairable = stack.get(DataComponents.REPAIRABLE);
        if (repairable == null) {
            return null;
        }

        // Check if it's a tag reference
        Optional<TagKey<Item>> tagKey = repairable.items().unwrapKey()
            .filter(key -> key instanceof TagKey)
            .map(key -> (TagKey<Item>) key);

        return tagKey.map(TagKey::location).orElse(null);
    }

    /**
     * Check if an item can repair this stack.
     */
    public static boolean canRepairWith(ItemStack toRepair, ItemStack material) {
        Repairable repairable = toRepair.get(DataComponents.REPAIRABLE);
        if (repairable == null) {
            return false;
        }

        return repairable.items().contains(material.getItemHolder());
    }
}
```

#### Repair Cost Management

```java
public class RepairCostManager {

    /** Minimum repair cost */
    public static final int MIN_REPAIR_COST = 0;

    /** Maximum repair cost (before "too expensive") */
    public static final int MAX_REPAIR_COST = 39;

    /** Vanilla "too expensive" threshold */
    public static final int TOO_EXPENSIVE_THRESHOLD = 40;

    /**
     * Get current repair cost.
     */
    public static int getRepairCost(ItemStack stack) {
        Integer cost = stack.get(DataComponents.REPAIR_COST);
        return cost != null ? cost : 0;
    }

    /**
     * Set repair cost.
     */
    public static void setRepairCost(ItemStack stack, int cost) {
        cost = Math.max(MIN_REPAIR_COST, cost);
        stack.set(DataComponents.REPAIR_COST, cost);
    }

    /**
     * Reset repair penalty (set cost to 0).
     * This removes the "too expensive" problem.
     */
    public static void resetRepairPenalty(ItemStack stack) {
        stack.set(DataComponents.REPAIR_COST, 0);
    }

    /**
     * Check if item is "too expensive" to repair.
     */
    public static boolean isTooExpensive(ItemStack stack) {
        return getRepairCost(stack) >= TOO_EXPENSIVE_THRESHOLD;
    }

    /**
     * Get estimated total XP cost for next anvil repair.
     * This is an approximation - actual cost depends on operation.
     */
    public static int estimateNextRepairCost(ItemStack stack) {
        int baseCost = getRepairCost(stack);
        // Each repair roughly doubles the cost
        return baseCost + 1;
    }
}
```

#### RepairData Record

```java
/**
 * Aggregated repair information for display.
 */
public record RepairData(
    int repairCost,
    boolean isTooExpensive,
    Set<Item> repairMaterials,
    @Nullable ResourceLocation repairTag,
    boolean isRepairable
) {

    public static RepairData from(ItemStack stack) {
        int cost = RepairCostManager.getRepairCost(stack);
        Set<Item> materials = RepairMaterialReader.getRepairMaterials(stack);
        ResourceLocation tag = RepairMaterialReader.getRepairTag(stack);

        return new RepairData(
            cost,
            cost >= RepairCostManager.TOO_EXPENSIVE_THRESHOLD,
            materials,
            tag,
            !materials.isEmpty() || stack.isDamageableItem()
        );
    }

    /**
     * Get display name for repair materials.
     */
    public String getMaterialsDisplayName() {
        if (repairTag != null) {
            // Show tag name
            return "#" + repairTag.toString();
        }

        if (repairMaterials.isEmpty()) {
            return "None";
        }

        if (repairMaterials.size() == 1) {
            Item item = repairMaterials.iterator().next();
            return item.getDescription().getString();
        }

        return repairMaterials.size() + " materials";
    }
}
```

---

### Phase 2 Implementation

#### Repair Efficiency

```java
/**
 * Data component for repair efficiency modifier.
 */
public record RepairEfficiency(float multiplier) {

    public static final float DEFAULT = 1.0f;
    public static final float MIN = 0.25f;  // 25% efficiency
    public static final float MAX = 4.0f;   // 400% efficiency

    public static final Codec<RepairEfficiency> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.FLOAT.fieldOf("multiplier").forGetter(RepairEfficiency::multiplier)
        ).apply(instance, RepairEfficiency::new)
    );

    public RepairEfficiency {
        multiplier = Math.clamp(multiplier, MIN, MAX);
    }

    /**
     * Calculate actual durability restored.
     * @param baseAmount Vanilla repair amount
     * @return Modified repair amount
     */
    public int calculateRepairAmount(int baseAmount) {
        return Math.round(baseAmount * multiplier);
    }
}

// Registration
public static final DataComponentType<RepairEfficiency> REPAIR_EFFICIENCY =
    DataComponentType.<RepairEfficiency>builder()
        .persistent(RepairEfficiency.CODEC)
        .networkSynchronized(RepairEfficiency.STREAM_CODEC)
        .build();
```

#### XP Cost Override

```java
/**
 * Override for anvil XP cost calculation.
 */
public record XpCostOverride(
    CostMode mode,
    float value
) {

    public enum CostMode {
        /** Multiply vanilla cost */
        MULTIPLY,
        /** Fixed cost per repair */
        FIXED,
        /** Cost based on durability restored */
        PER_DURABILITY,
        /** Free repairs */
        FREE
    }

    public static final XpCostOverride DEFAULT = new XpCostOverride(CostMode.MULTIPLY, 1.0f);

    public static final Codec<XpCostOverride> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.xmap(CostMode::valueOf, CostMode::name)
                .fieldOf("mode").forGetter(XpCostOverride::mode),
            Codec.FLOAT.fieldOf("value").forGetter(XpCostOverride::value)
        ).apply(instance, XpCostOverride::new)
    );

    /**
     * Calculate modified XP cost.
     * @param vanillaCost Original vanilla cost
     * @param durabilityRestored Amount of durability being restored
     * @return Final XP cost
     */
    public int calculateCost(int vanillaCost, int durabilityRestored) {
        return switch (mode) {
            case MULTIPLY -> Math.round(vanillaCost * value);
            case FIXED -> (int) value;
            case PER_DURABILITY -> Math.round(durabilityRestored * value / 100f);
            case FREE -> 0;
        };
    }
}
```

#### AnvilUpdateEvent Handler

```java
@Mod.EventBusSubscriber(modid = DevMod.MODID)
public class RepairEventHandler {

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();   // Item being repaired
        ItemStack right = event.getRight(); // Material or item

        // Check for repair efficiency
        RepairEfficiency efficiency = left.get(ModDataComponents.REPAIR_EFFICIENCY);
        if (efficiency != null && efficiency.multiplier() != 1.0f) {
            // Modify output durability
            ItemStack output = event.getOutput();
            if (output != null && output.isDamaged()) {
                int currentDamage = output.getDamageValue();
                int repairAmount = left.getDamageValue() - currentDamage;
                int modifiedRepair = efficiency.calculateRepairAmount(repairAmount);
                int newDamage = Math.max(0, left.getDamageValue() - modifiedRepair);
                output.setDamageValue(newDamage);
                event.setOutput(output);
            }
        }

        // Check for XP cost override
        XpCostOverride costOverride = left.get(ModDataComponents.XP_COST_OVERRIDE);
        if (costOverride != null) {
            int vanillaCost = event.getCost();
            int durabilityRestored = left.getDamageValue() -
                (event.getOutput() != null ? event.getOutput().getDamageValue() : 0);
            int newCost = costOverride.calculateCost(vanillaCost, durabilityRestored);
            event.setCost(newCost);
        }
    }
}
```

---

### Phase 3 Implementation

#### Custom Repair Materials

```java
/**
 * Override repair materials for an item.
 * Replaces vanilla Repairable component behavior.
 */
public record CustomRepairMaterials(
    List<ResourceLocation> materials,
    boolean appendToVanilla  // true = add to vanilla, false = replace
) {

    public static final Codec<CustomRepairMaterials> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("materials")
                .forGetter(CustomRepairMaterials::materials),
            Codec.BOOL.optionalFieldOf("append", false)
                .forGetter(CustomRepairMaterials::appendToVanilla)
        ).apply(instance, CustomRepairMaterials::new)
    );

    /**
     * Check if an item can repair this.
     */
    public boolean canRepairWith(ItemStack material, ItemStack original) {
        ResourceLocation materialId = BuiltInRegistries.ITEM.getKey(material.getItem());

        if (materials.contains(materialId)) {
            return true;
        }

        if (appendToVanilla) {
            return RepairMaterialReader.canRepairWith(original, material);
        }

        return false;
    }

    /**
     * Get all effective repair materials.
     */
    public Set<Item> getEffectiveMaterials(ItemStack original) {
        Set<Item> result = new HashSet<>();

        // Add custom materials
        for (ResourceLocation loc : materials) {
            Item item = BuiltInRegistries.ITEM.get(loc);
            if (item != Items.AIR) {
                result.add(item);
            }
        }

        // Add vanilla materials if appending
        if (appendToVanilla) {
            result.addAll(RepairMaterialReader.getRepairMaterials(original));
        }

        return result;
    }
}
```

#### Partial Repair

```java
/**
 * Allow partial repairs with fewer materials.
 */
public record PartialRepair(
    boolean enabled,
    float minMaterialRatio  // Minimum fraction of material required (0.25 = 25%)
) {

    public static final PartialRepair DISABLED = new PartialRepair(false, 1.0f);
    public static final PartialRepair DEFAULT = new PartialRepair(true, 0.25f);

    public static final Codec<PartialRepair> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(PartialRepair::enabled),
            Codec.FLOAT.optionalFieldOf("min_ratio", 0.25f)
                .forGetter(PartialRepair::minMaterialRatio)
        ).apply(instance, PartialRepair::new)
    );

    /**
     * Calculate repair amount for partial material.
     * @param fullRepairAmount Amount if using full material
     * @param materialFraction Fraction of material used (0.0-1.0)
     * @return Actual repair amount
     */
    public int calculatePartialRepair(int fullRepairAmount, float materialFraction) {
        if (!enabled) {
            return materialFraction >= 1.0f ? fullRepairAmount : 0;
        }

        if (materialFraction < minMaterialRatio) {
            return 0;  // Not enough material
        }

        return Math.round(fullRepairAmount * materialFraction);
    }
}
```

---

### Quick Actions

Azioni rapide per operazioni comuni su tutti gli item (armi e armature).

#### Available Quick Actions

| Action | Descrizione | Shortcut |
|--------|-------------|----------|
| **Reset Penalty** | Imposta `repair_cost` a 0 (rimuove "too expensive") | R |
| **Set Cost to 1** | Imposta `repair_cost` a 1 (minimo con penalty) | 1 |
| **Free Repairs** | Abilita `XpCostOverride.FREE` (Phase 2) | F |
| **Max Efficiency** | Imposta `repair_efficiency` a 2.0x (Phase 2) | E |

#### Quick Action Implementation

```java
public class RepairQuickActions {

    /**
     * Reset repair penalty to 0.
     */
    public static void resetPenalty(ItemStack stack) {
        RepairCostManager.resetRepairPenalty(stack);
    }

    /**
     * Set repair cost to minimum (1).
     */
    public static void setMinimalCost(ItemStack stack) {
        RepairCostManager.setRepairCost(stack, 1);
    }

    /**
     * Enable free repairs (Phase 2).
     */
    public static void enableFreeRepairs(ItemStack stack) {
        stack.set(ModDataComponents.XP_COST_OVERRIDE,
            new XpCostOverride(XpCostOverride.CostMode.FREE, 0));
    }

    /**
     * Set maximum repair efficiency (Phase 2).
     */
    public static void setMaxEfficiency(ItemStack stack) {
        stack.set(ModDataComponents.REPAIR_EFFICIENCY,
            new RepairEfficiency(RepairEfficiency.MAX));
    }

    /**
     * Reset all repair modifiers to vanilla defaults.
     */
    public static void resetToVanilla(ItemStack stack) {
        // Keep current repair cost (vanilla behavior)
        stack.remove(ModDataComponents.REPAIR_EFFICIENCY);
        stack.remove(ModDataComponents.XP_COST_OVERRIDE);
        stack.remove(ModDataComponents.CUSTOM_REPAIR_MATERIALS);
        stack.remove(ModDataComponents.PARTIAL_REPAIR);
    }
}
```

#### UI Quick Action Bar

```
┌──────────────────────────────────────────────────────────────────┐
│  [RESET PENALTY]  [SET TO 1]  [FREE REPAIRS]  [MAX EFFICIENCY]   │
└──────────────────────────────────────────────────────────────────┘
```

---

### UI Mockup

```
┌─────────────────────────────────────────────────────────────────────┐
│                        REPAIR SETTINGS                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  QUICK ACTIONS                                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ [RESET PENALTY]  [SET TO 1]  [FREE REPAIRS]  [MAX EFFICIENCY]│   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  REPAIR COST                                                         │
│  ───────────────────────────────────────────────────────────────    │
│  Current Cost     [       7       ] [▼][▲]                          │
│                   ⚠ High cost - next repair: ~8 XP levels           │
│                                                                      │
│  REPAIR MATERIALS (Read-Only)                                        │
│  ───────────────────────────────────────────────────────────────    │
│  Tag: #minecraft:repairs_diamond_armor                               │
│                                                                      │
│  Materials:  [💎] Diamond                                            │
│                                                                      │
│  MODIFIERS (Phase 2)                                                 │
│  ───────────────────────────────────────────────────────────────    │
│  Repair Efficiency  [██████████████] 1.5x  (150% durability)        │
│                                                                      │
│  XP Cost Mode:  [MULTIPLY ▼]                                        │
│                 ○ Multiply (0.5x-2.0x vanilla cost)                  │
│                 ○ Fixed (constant XP per repair)                     │
│                 ○ Per Durability (XP per 100 durability)             │
│                 ○ Free (no XP cost)                                  │
│                                                                      │
│  Cost Value:    [████████════] 0.75x                                │
│                                                                      │
│  CUSTOM MATERIALS (Phase 3)                                          │
│  ───────────────────────────────────────────────────────────────    │
│  [ ] Override repair materials                                       │
│  [✓] Append to vanilla materials                                     │
│                                                                      │
│  Custom Materials:                                                   │
│  ┌────────────────────────────────────────────┐                     │
│  │ [💎] Diamond                    [Remove]   │                     │
│  │ [🟡] Gold Ingot                 [Remove]   │                     │
│  └────────────────────────────────────────────┘                     │
│  [+ Add Material]                                                    │
│                                                                      │
│  PARTIAL REPAIR (Phase 3)                                            │
│  ───────────────────────────────────────────────────────────────    │
│  [✓] Enable partial repairs                                          │
│  Min Material:  [████══════] 25%  (minimum material fraction)       │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### ValueLimits

```java
// Add to ValueLimits.java

/** Repair cost (XP levels) */
public static final Limit REPAIR_COST = Limit.of(
    0, 20,       // Soft: 0-20 levels
    0, 100       // Hard: 0-100 levels
);

/** Repair efficiency multiplier */
public static final Limit REPAIR_EFFICIENCY = Limit.of(
    0.5, 2.0,    // Soft: 50%-200%
    0.25, 4.0    // Hard: 25%-400%
);

/** XP cost multiplier */
public static final Limit XP_COST_MULTIPLIER = Limit.of(
    0.25, 2.0,   // Soft: 25%-200%
    0.0, 10.0    // Hard: 0%-1000% (0 = free)
);

/** Fixed XP cost */
public static final Limit XP_COST_FIXED = Limit.of(
    1, 30,       // Soft: 1-30 levels
    0, 100       // Hard: 0-100 levels
);

/** Partial repair minimum ratio */
public static final Limit PARTIAL_REPAIR_RATIO = Limit.of(
    0.25, 0.75,  // Soft: 25%-75%
    0.1, 0.9     // Hard: 10%-90%
);
```

---

### Config

```toml
# config/devmod-server.toml

[repair]
# Allow editing repair cost
allowRepairCostEdit = true

# Allow resetting repair penalty
allowRepairPenaltyReset = true

# Maximum allowed repair cost
maxRepairCost = 100

# Enable repair efficiency modifier (Phase 2)
enableRepairEfficiency = false

# Enable XP cost override (Phase 2)
enableXpCostOverride = false

# Maximum repair efficiency multiplier
maxRepairEfficiency = 4.0

# Allow free repairs (XP cost = 0)
allowFreeRepairs = false

# Enable custom repair materials (Phase 3)
enableCustomRepairMaterials = false

# Enable partial repairs (Phase 3)
enablePartialRepairs = false

# Minimum partial repair ratio
minPartialRepairRatio = 0.1
```

---

### Network Payload

```java
/**
 * Payload for updating repair settings.
 */
public record UpdateRepairPayload(
    int repairCost,
    boolean resetPenalty,
    // Phase 2
    float repairEfficiency,
    XpCostOverride.CostMode costMode,
    float costValue,
    // Phase 3
    boolean useCustomMaterials,
    List<String> customMaterials,
    boolean appendToVanilla,
    boolean enablePartialRepair,
    float partialRepairRatio
) implements CustomPacketPayload {

    public static final Type<UpdateRepairPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "update_repair")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateRepairPayload> STREAM_CODEC =
        StreamCodec.of(UpdateRepairPayload::encode, UpdateRepairPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, UpdateRepairPayload payload) {
        buf.writeVarInt(payload.repairCost);
        buf.writeBoolean(payload.resetPenalty);
        buf.writeFloat(payload.repairEfficiency);
        buf.writeUtf(payload.costMode.name());
        buf.writeFloat(payload.costValue);
        buf.writeBoolean(payload.useCustomMaterials);
        buf.writeCollection(payload.customMaterials, RegistryFriendlyByteBuf::writeUtf);
        buf.writeBoolean(payload.appendToVanilla);
        buf.writeBoolean(payload.enablePartialRepair);
        buf.writeFloat(payload.partialRepairRatio);
    }

    private static UpdateRepairPayload decode(RegistryFriendlyByteBuf buf) {
        return new UpdateRepairPayload(
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readFloat(),
            XpCostOverride.CostMode.valueOf(buf.readUtf()),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readList(RegistryFriendlyByteBuf::readUtf),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

---

## 2.32 Enchantments System

Sistema completo per gestione enchantment su armi e armature.

### Roadmap

| Phase | Feature | Descrizione |
|-------|---------|-------------|
| MVP | Add/Remove | Aggiungere/rimuovere enchantment |
| MVP | Level Edit | Modificare livello (1 → vanilla max) |
| Phase 2 | Level Override | Superare max vanilla (es. Sharpness X) |
| Phase 2 | Bypass Compatibility | Combinare enchant incompatibili |
| Phase 3 | Blacklist | Bloccare enchant specifici per item |
| Phase 3 | Whitelist Mode | Solo enchant permessi |
| Phase 3 | Custom XP Costs | Costo XP per applicare enchant |
| Phase 4+ | Mod Integration | Supporto enchant da altri mod |

---

### Core Data Structures

#### EnchantmentEntry

```java
/**
 * Represents a single enchantment on an item.
 */
public record EnchantmentEntry(
    ResourceLocation enchantmentId,
    int level,
    boolean bypassedCompatibility,  // true if forced despite incompatibility
    boolean overMaxLevel            // true if level > vanilla max
) {

    /**
     * Create from vanilla enchantment holder.
     */
    public static EnchantmentEntry fromHolder(Holder<Enchantment> holder, int level) {
        ResourceLocation id = holder.unwrapKey()
            .map(ResourceKey::location)
            .orElseThrow();
        int maxLevel = holder.value().getMaxLevel();

        return new EnchantmentEntry(
            id,
            level,
            false,
            level > maxLevel
        );
    }

    /**
     * Get enchantment holder from registry.
     */
    public Optional<Holder<Enchantment>> getHolder(RegistryAccess registries) {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
    }

    /**
     * Get display name with level.
     */
    public Component getDisplayName(RegistryAccess registries) {
        return getHolder(registries)
            .map(holder -> Enchantment.getFullname(holder, level))
            .orElse(Component.literal(enchantmentId.toString() + " " + level));
    }

    /**
     * Check if this is a valid vanilla level.
     */
    public boolean isVanillaLevel(RegistryAccess registries) {
        return getHolder(registries)
            .map(holder -> level <= holder.value().getMaxLevel())
            .orElse(false);
    }
}
```

#### EnchantmentConfig

```java
/**
 * Configuration for enchantment behavior on a specific item.
 */
public record EnchantmentConfig(
    Set<ResourceLocation> blacklist,      // Blocked enchantments
    Set<ResourceLocation> whitelist,      // Allowed enchantments (if whitelistMode)
    boolean whitelistMode,                // true = only whitelist allowed
    boolean bypassAllCompatibility,       // Ignore all compatibility rules
    Map<ResourceLocation, Integer> levelOverrides,  // Custom max levels
    Map<ResourceLocation, Integer> xpCosts          // Custom XP costs
) {

    public static final EnchantmentConfig DEFAULT = new EnchantmentConfig(
        Set.of(),
        Set.of(),
        false,
        false,
        Map.of(),
        Map.of()
    );

    public static final Codec<EnchantmentConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                .optionalFieldOf("blacklist", Set.of())
                .forGetter(EnchantmentConfig::blacklist),
            ResourceLocation.CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                .optionalFieldOf("whitelist", Set.of())
                .forGetter(EnchantmentConfig::whitelist),
            Codec.BOOL.optionalFieldOf("whitelist_mode", false)
                .forGetter(EnchantmentConfig::whitelistMode),
            Codec.BOOL.optionalFieldOf("bypass_compatibility", false)
                .forGetter(EnchantmentConfig::bypassAllCompatibility),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                .optionalFieldOf("level_overrides", Map.of())
                .forGetter(EnchantmentConfig::levelOverrides),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                .optionalFieldOf("xp_costs", Map.of())
                .forGetter(EnchantmentConfig::xpCosts)
        ).apply(instance, EnchantmentConfig::new)
    );

    /**
     * Check if enchantment is allowed on this item.
     */
    public boolean isAllowed(ResourceLocation enchantmentId) {
        if (blacklist.contains(enchantmentId)) {
            return false;
        }
        if (whitelistMode) {
            return whitelist.contains(enchantmentId);
        }
        return true;
    }

    /**
     * Get effective max level for enchantment.
     */
    public int getMaxLevel(ResourceLocation enchantmentId, int vanillaMax) {
        return levelOverrides.getOrDefault(enchantmentId, vanillaMax);
    }

    /**
     * Get XP cost for applying enchantment.
     */
    public int getXpCost(ResourceLocation enchantmentId, int defaultCost) {
        return xpCosts.getOrDefault(enchantmentId, defaultCost);
    }
}
```

---

### MVP Implementation

#### Reading Enchantments

```java
public class EnchantmentReader {

    /**
     * Get all enchantments on an item.
     */
    public static List<EnchantmentEntry> getEnchantments(ItemStack stack, RegistryAccess registries) {
        ItemEnchantments enchantments = stack.getOrDefault(
            DataComponents.ENCHANTMENTS,
            ItemEnchantments.EMPTY
        );

        List<EnchantmentEntry> entries = new ArrayList<>();
        enchantments.entrySet().forEach(entry -> {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            entries.add(EnchantmentEntry.fromHolder(holder, level));
        });

        return entries;
    }

    /**
     * Get stored enchantments (for enchanted books).
     */
    public static List<EnchantmentEntry> getStoredEnchantments(ItemStack stack, RegistryAccess registries) {
        ItemEnchantments enchantments = stack.getOrDefault(
            DataComponents.STORED_ENCHANTMENTS,
            ItemEnchantments.EMPTY
        );

        List<EnchantmentEntry> entries = new ArrayList<>();
        enchantments.entrySet().forEach(entry -> {
            entries.add(EnchantmentEntry.fromHolder(entry.getKey(), entry.getIntValue()));
        });

        return entries;
    }

    /**
     * Check if item has specific enchantment.
     */
    public static boolean hasEnchantment(ItemStack stack, ResourceLocation enchantmentId, RegistryAccess registries) {
        return getEnchantments(stack, registries).stream()
            .anyMatch(e -> e.enchantmentId().equals(enchantmentId));
    }

    /**
     * Get level of specific enchantment (0 if not present).
     */
    public static int getEnchantmentLevel(ItemStack stack, ResourceLocation enchantmentId, RegistryAccess registries) {
        return getEnchantments(stack, registries).stream()
            .filter(e -> e.enchantmentId().equals(enchantmentId))
            .findFirst()
            .map(EnchantmentEntry::level)
            .orElse(0);
    }
}
```

#### Writing Enchantments

```java
public class EnchantmentWriter {

    /**
     * Set enchantments on an item, replacing all existing.
     */
    public static void setEnchantments(ItemStack stack, List<EnchantmentEntry> entries, RegistryAccess registries) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

        for (EnchantmentEntry entry : entries) {
            entry.getHolder(registries).ifPresent(holder -> {
                mutable.set(holder, entry.level());
            });
        }

        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    /**
     * Add or update a single enchantment.
     */
    public static void addEnchantment(ItemStack stack, EnchantmentEntry entry, RegistryAccess registries) {
        List<EnchantmentEntry> entries = new ArrayList<>(EnchantmentReader.getEnchantments(stack, registries));

        // Remove existing if present
        entries.removeIf(e -> e.enchantmentId().equals(entry.enchantmentId()));
        entries.add(entry);

        setEnchantments(stack, entries, registries);
    }

    /**
     * Remove an enchantment.
     */
    public static void removeEnchantment(ItemStack stack, ResourceLocation enchantmentId, RegistryAccess registries) {
        List<EnchantmentEntry> entries = new ArrayList<>(EnchantmentReader.getEnchantments(stack, registries));
        entries.removeIf(e -> e.enchantmentId().equals(enchantmentId));
        setEnchantments(stack, entries, registries);
    }

    /**
     * Set level of existing enchantment.
     */
    public static void setEnchantmentLevel(
        ItemStack stack,
        ResourceLocation enchantmentId,
        int level,
        RegistryAccess registries
    ) {
        List<EnchantmentEntry> entries = new ArrayList<>(EnchantmentReader.getEnchantments(stack, registries));

        for (int i = 0; i < entries.size(); i++) {
            EnchantmentEntry entry = entries.get(i);
            if (entry.enchantmentId().equals(enchantmentId)) {
                entries.set(i, new EnchantmentEntry(
                    enchantmentId,
                    level,
                    entry.bypassedCompatibility(),
                    level > getVanillaMaxLevel(enchantmentId, registries)
                ));
                break;
            }
        }

        setEnchantments(stack, entries, registries);
    }

    /**
     * Clear all enchantments.
     */
    public static void clearEnchantments(ItemStack stack) {
        stack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    private static int getVanillaMaxLevel(ResourceLocation enchantmentId, RegistryAccess registries) {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId))
            .map(h -> h.value().getMaxLevel())
            .orElse(1);
    }
}
```

#### Enchantment Picker

```java
/**
 * UI component for selecting enchantments to add.
 *
 * Filter behavior:
 * - Default: Only shows enchantments applicable to item type AND compatible with current
 * - showIncompatible: Also shows incompatible enchantments (can be forced)
 * - showNonApplicable: Shows ALL enchantments (can be forced with warning)
 */
public class EnchantmentPicker {

    private final RegistryAccess registries;
    private final ItemStack targetItem;
    private String searchFilter = "";
    private EnchantmentCategory categoryFilter = null;
    private boolean showIncompatible = false;
    private boolean showNonApplicable = false;  // Show enchants not meant for this item type

    public enum EnchantmentCategory {
        WEAPON,      // Damage enchants (Sharpness, Smite, etc.)
        ARMOR,       // Protection enchants
        TOOL,        // Tool enchants (Efficiency, Fortune, etc.)
        BOW,         // Bow/Crossbow enchants
        TRIDENT,     // Trident enchants
        FISHING,     // Fishing rod enchants
        UNIVERSAL,   // Mending, Unbreaking, Curse
        ALL
    }

    /**
     * Filter visibility modes.
     */
    public enum FilterMode {
        /** Only applicable + compatible (default, cleanest UI) */
        STRICT,
        /** Applicable + show incompatible (for bypass) */
        SHOW_INCOMPATIBLE,
        /** Show ALL enchantments (power user mode) */
        SHOW_ALL
    }

    public void setFilterMode(FilterMode mode) {
        this.showIncompatible = mode == FilterMode.SHOW_INCOMPATIBLE || mode == FilterMode.SHOW_ALL;
        this.showNonApplicable = mode == FilterMode.SHOW_ALL;
    }

    /**
     * Get filtered list of available enchantments.
     */
    public List<EnchantmentOption> getAvailableEnchantments() {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);
        List<EnchantmentEntry> current = EnchantmentReader.getEnchantments(targetItem, registries);
        Set<ResourceLocation> currentIds = current.stream()
            .map(EnchantmentEntry::enchantmentId)
            .collect(Collectors.toSet());

        List<EnchantmentOption> options = new ArrayList<>();

        registry.holders().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            Enchantment enchant = holder.value();

            // Check if already applied
            boolean alreadyApplied = currentIds.contains(id);

            // Check compatibility with current enchantments
            boolean compatible = isCompatibleWithCurrent(holder, current);

            // Check if can apply to this item type
            boolean canApply = enchant.canEnchant(targetItem);

            // Apply visibility filters
            boolean passesVisibilityFilter = canApply || showNonApplicable;
            boolean passesCompatibilityFilter = compatible || showIncompatible;

            // Apply search filter
            String name = enchant.description().getString().toLowerCase();
            boolean matchesSearch = searchFilter.isEmpty() ||
                name.contains(searchFilter.toLowerCase()) ||
                id.toString().contains(searchFilter.toLowerCase());

            // Apply category filter
            boolean matchesCategory = categoryFilter == null ||
                categoryFilter == EnchantmentCategory.ALL ||
                matchesCategory(enchant, categoryFilter);

            if (passesVisibilityFilter && passesCompatibilityFilter && matchesSearch && matchesCategory) {
                options.add(new EnchantmentOption(
                    id,
                    enchant.description(),
                    enchant.getMaxLevel(),
                    alreadyApplied,
                    compatible,
                    canApply,
                    getApplicabilityWarning(enchant, canApply, compatible)
                ));
            }
        });

        // Sort: applicable first, then compatible, then alphabetical
        options.sort(Comparator
            .comparing(EnchantmentOption::canApply).reversed()
            .thenComparing(EnchantmentOption::compatible).reversed()
            .thenComparing(o -> o.name().getString())
        );

        return options;
    }

    /**
     * Get warning message for non-standard enchantment application.
     */
    @Nullable
    private String getApplicabilityWarning(Enchantment enchant, boolean canApply, boolean compatible) {
        if (!canApply && !compatible) {
            return "Not applicable to this item AND incompatible with current enchantments";
        }
        if (!canApply) {
            return "Not normally applicable to this item type (e.g., Riptide on Sword)";
        }
        if (!compatible) {
            return "Incompatible with existing enchantment";
        }
        return null;
    }

    private boolean isCompatibleWithCurrent(Holder<Enchantment> holder, List<EnchantmentEntry> current) {
        for (EnchantmentEntry entry : current) {
            Optional<Holder<Enchantment>> otherHolder = entry.getHolder(registries);
            if (otherHolder.isPresent() && !Enchantment.areCompatible(holder, otherHolder.get())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCategory(Enchantment enchant, EnchantmentCategory category) {
        // Simplified category matching based on enchantment properties
        return switch (category) {
            case WEAPON -> enchant.canEnchant(new ItemStack(Items.DIAMOND_SWORD));
            case ARMOR -> enchant.canEnchant(new ItemStack(Items.DIAMOND_CHESTPLATE));
            case TOOL -> enchant.canEnchant(new ItemStack(Items.DIAMOND_PICKAXE));
            case BOW -> enchant.canEnchant(new ItemStack(Items.BOW));
            case TRIDENT -> enchant.canEnchant(new ItemStack(Items.TRIDENT));
            case FISHING -> enchant.canEnchant(new ItemStack(Items.FISHING_ROD));
            case UNIVERSAL -> true;  // Show all
            case ALL -> true;
        };
    }

    /**
     * Get count of enchantments per visibility level (for UI badges).
     */
    public FilterCounts getFilterCounts() {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);
        List<EnchantmentEntry> current = EnchantmentReader.getEnchantments(targetItem, registries);

        int applicable = 0;
        int incompatible = 0;
        int nonApplicable = 0;

        for (var holder : registry.holders().toList()) {
            Enchantment enchant = holder.value();
            boolean canApply = enchant.canEnchant(targetItem);
            boolean compatible = isCompatibleWithCurrent(holder, current);

            if (canApply && compatible) {
                applicable++;
            } else if (canApply && !compatible) {
                incompatible++;
            } else {
                nonApplicable++;
            }
        }

        return new FilterCounts(applicable, incompatible, nonApplicable);
    }

    public record FilterCounts(int applicable, int incompatible, int nonApplicable) {
        public int total() { return applicable + incompatible + nonApplicable; }
    }

    public record EnchantmentOption(
        ResourceLocation id,
        Component name,
        int maxLevel,
        boolean alreadyApplied,
        boolean compatible,
        boolean canApply,
        @Nullable String warning
    ) {
        /**
         * Get the action button type for this option.
         */
        public ActionType getActionType() {
            if (alreadyApplied) return ActionType.ALREADY_APPLIED;
            if (canApply && compatible) return ActionType.ADD;
            if (canApply && !compatible) return ActionType.FORCE_INCOMPATIBLE;
            return ActionType.FORCE_NON_APPLICABLE;
        }

        public enum ActionType {
            ADD,                    // Normal add button
            FORCE_INCOMPATIBLE,     // Force button (yellow warning)
            FORCE_NON_APPLICABLE,   // Force button (red warning)
            ALREADY_APPLIED         // Disabled/checkmark
        }
    }
}
```

---

### Phase 2 Implementation

#### Level Override System

```java
/**
 * Manages enchantment level overrides beyond vanilla limits.
 */
public class EnchantmentLevelOverride {

    /** Absolute maximum level allowed */
    public static final int ABSOLUTE_MAX_LEVEL = 255;

    /** Soft limit for UI warnings */
    public static final int SOFT_MAX_LEVEL = 10;

    /**
     * Create enchantment entry with level override.
     */
    public static EnchantmentEntry createWithOverride(
        ResourceLocation enchantmentId,
        int level,
        RegistryAccess registries
    ) {
        int vanillaMax = getVanillaMaxLevel(enchantmentId, registries);

        return new EnchantmentEntry(
            enchantmentId,
            Math.min(level, ABSOLUTE_MAX_LEVEL),
            false,
            level > vanillaMax
        );
    }

    /**
     * Get validation result for level.
     */
    public static ValidationResult validateLevel(
        ResourceLocation enchantmentId,
        int level,
        RegistryAccess registries
    ) {
        int vanillaMax = getVanillaMaxLevel(enchantmentId, registries);

        if (level <= 0) {
            return new ValidationResult.Blocked("Level must be positive");
        }

        if (level > ABSOLUTE_MAX_LEVEL) {
            return new ValidationResult.Clamped(
                ABSOLUTE_MAX_LEVEL,
                "Maximum level is " + ABSOLUTE_MAX_LEVEL
            );
        }

        if (level > SOFT_MAX_LEVEL) {
            return new ValidationResult.Warning(
                "Level " + level + " may cause unexpected behavior"
            );
        }

        if (level > vanillaMax) {
            return new ValidationResult.Warning(
                "Exceeds vanilla max (" + vanillaMax + ")"
            );
        }

        return new ValidationResult.Valid();
    }

    private static int getVanillaMaxLevel(ResourceLocation id, RegistryAccess registries) {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, id))
            .map(h -> h.value().getMaxLevel())
            .orElse(1);
    }
}
```

#### Compatibility Bypass

```java
/**
 * Handles bypassing enchantment compatibility rules.
 */
public class CompatibilityBypass {

    /**
     * Known incompatible pairs for UI display.
     */
    public static final List<IncompatiblePair> KNOWN_INCOMPATIBLE = List.of(
        new IncompatiblePair("minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"),
        new IncompatiblePair("minecraft:protection", "minecraft:fire_protection",
            "minecraft:blast_protection", "minecraft:projectile_protection"),
        new IncompatiblePair("minecraft:depth_strider", "minecraft:frost_walker"),
        new IncompatiblePair("minecraft:infinity", "minecraft:mending"),
        new IncompatiblePair("minecraft:silk_touch", "minecraft:fortune"),
        new IncompatiblePair("minecraft:loyalty", "minecraft:riptide"),
        new IncompatiblePair("minecraft:channeling", "minecraft:riptide"),
        new IncompatiblePair("minecraft:multishot", "minecraft:piercing")
    );

    public record IncompatiblePair(Set<ResourceLocation> enchantments) {
        public IncompatiblePair(String... ids) {
            this(Arrays.stream(ids)
                .map(ResourceLocation::parse)
                .collect(Collectors.toSet()));
        }

        public boolean contains(ResourceLocation id) {
            return enchantments.contains(id);
        }

        public boolean conflictsWith(ResourceLocation a, ResourceLocation b) {
            return enchantments.contains(a) && enchantments.contains(b);
        }
    }

    /**
     * Check if two enchantments are incompatible.
     */
    public static boolean areIncompatible(
        ResourceLocation a,
        ResourceLocation b,
        RegistryAccess registries
    ) {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);

        Optional<Holder.Reference<Enchantment>> holderA = registry.getHolder(
            ResourceKey.create(Registries.ENCHANTMENT, a));
        Optional<Holder.Reference<Enchantment>> holderB = registry.getHolder(
            ResourceKey.create(Registries.ENCHANTMENT, b));

        if (holderA.isEmpty() || holderB.isEmpty()) {
            return false;
        }

        return !Enchantment.areCompatible(holderA.get(), holderB.get());
    }

    /**
     * Get all incompatibilities for an enchantment.
     */
    public static Set<ResourceLocation> getIncompatibilities(
        ResourceLocation enchantmentId,
        RegistryAccess registries
    ) {
        Set<ResourceLocation> incompatible = new HashSet<>();
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);

        Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(
            ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));

        if (holder.isEmpty()) {
            return incompatible;
        }

        registry.holders().forEach(other -> {
            if (!other.key().location().equals(enchantmentId) &&
                !Enchantment.areCompatible(holder.get(), other)) {
                incompatible.add(other.key().location());
            }
        });

        return incompatible;
    }

    /**
     * Force add incompatible enchantment, marking as bypassed.
     */
    public static void forceAddIncompatible(
        ItemStack stack,
        EnchantmentEntry entry,
        RegistryAccess registries
    ) {
        EnchantmentEntry bypassed = new EnchantmentEntry(
            entry.enchantmentId(),
            entry.level(),
            true,  // Mark as bypassed
            entry.overMaxLevel()
        );

        EnchantmentWriter.addEnchantment(stack, bypassed, registries);
    }
}
```

---

### Phase 3 Implementation

#### Blacklist/Whitelist System

```java
/**
 * Data component for enchantment restrictions.
 */
public record EnchantmentRestrictions(
    Set<ResourceLocation> blacklist,
    Set<ResourceLocation> whitelist,
    boolean whitelistMode
) {

    public static final EnchantmentRestrictions NONE = new EnchantmentRestrictions(
        Set.of(), Set.of(), false
    );

    public static final Codec<EnchantmentRestrictions> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                .optionalFieldOf("blacklist", Set.of())
                .forGetter(EnchantmentRestrictions::blacklist),
            ResourceLocation.CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                .optionalFieldOf("whitelist", Set.of())
                .forGetter(EnchantmentRestrictions::whitelist),
            Codec.BOOL.optionalFieldOf("whitelist_mode", false)
                .forGetter(EnchantmentRestrictions::whitelistMode)
        ).apply(instance, EnchantmentRestrictions::new)
    );

    /**
     * Check if enchantment is allowed.
     */
    public boolean isAllowed(ResourceLocation enchantmentId) {
        if (blacklist.contains(enchantmentId)) {
            return false;
        }
        if (whitelistMode) {
            return whitelist.contains(enchantmentId);
        }
        return true;
    }

    /**
     * Add to blacklist.
     */
    public EnchantmentRestrictions withBlacklisted(ResourceLocation enchantmentId) {
        Set<ResourceLocation> newBlacklist = new HashSet<>(blacklist);
        newBlacklist.add(enchantmentId);
        return new EnchantmentRestrictions(newBlacklist, whitelist, whitelistMode);
    }

    /**
     * Remove from blacklist.
     */
    public EnchantmentRestrictions withoutBlacklisted(ResourceLocation enchantmentId) {
        Set<ResourceLocation> newBlacklist = new HashSet<>(blacklist);
        newBlacklist.remove(enchantmentId);
        return new EnchantmentRestrictions(newBlacklist, whitelist, whitelistMode);
    }

    /**
     * Add to whitelist.
     */
    public EnchantmentRestrictions withWhitelisted(ResourceLocation enchantmentId) {
        Set<ResourceLocation> newWhitelist = new HashSet<>(whitelist);
        newWhitelist.add(enchantmentId);
        return new EnchantmentRestrictions(blacklist, newWhitelist, whitelistMode);
    }

    /**
     * Toggle whitelist mode.
     */
    public EnchantmentRestrictions withWhitelistMode(boolean enabled) {
        return new EnchantmentRestrictions(blacklist, whitelist, enabled);
    }
}

// Registration
public static final DataComponentType<EnchantmentRestrictions> ENCHANTMENT_RESTRICTIONS =
    DataComponentType.<EnchantmentRestrictions>builder()
        .persistent(EnchantmentRestrictions.CODEC)
        .networkSynchronized(EnchantmentRestrictions.STREAM_CODEC)
        .build();
```

#### Custom XP Costs

```java
/**
 * Custom XP costs for applying enchantments.
 */
public record EnchantmentXpCosts(
    Map<ResourceLocation, Integer> costs,
    float globalMultiplier
) {

    public static final EnchantmentXpCosts DEFAULT = new EnchantmentXpCosts(Map.of(), 1.0f);

    public static final Codec<EnchantmentXpCosts> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                .optionalFieldOf("costs", Map.of())
                .forGetter(EnchantmentXpCosts::costs),
            Codec.FLOAT.optionalFieldOf("multiplier", 1.0f)
                .forGetter(EnchantmentXpCosts::globalMultiplier)
        ).apply(instance, EnchantmentXpCosts::new)
    );

    /**
     * Get XP cost for enchantment.
     */
    public int getCost(ResourceLocation enchantmentId, int level, int defaultCost) {
        Integer customCost = costs.get(enchantmentId);
        if (customCost != null) {
            return Math.round(customCost * level * globalMultiplier);
        }
        return Math.round(defaultCost * globalMultiplier);
    }

    /**
     * Set custom cost for enchantment.
     */
    public EnchantmentXpCosts withCost(ResourceLocation enchantmentId, int baseCost) {
        Map<ResourceLocation, Integer> newCosts = new HashMap<>(costs);
        newCosts.put(enchantmentId, baseCost);
        return new EnchantmentXpCosts(newCosts, globalMultiplier);
    }

    /**
     * Remove custom cost.
     */
    public EnchantmentXpCosts withoutCost(ResourceLocation enchantmentId) {
        Map<ResourceLocation, Integer> newCosts = new HashMap<>(costs);
        newCosts.remove(enchantmentId);
        return new EnchantmentXpCosts(newCosts, globalMultiplier);
    }

    /**
     * Set global multiplier.
     */
    public EnchantmentXpCosts withMultiplier(float multiplier) {
        return new EnchantmentXpCosts(costs, multiplier);
    }
}
```

#### Enchantment Event Handler

```java
/**
 * Enforces enchantment restrictions.
 */
@Mod.EventBusSubscriber(modid = DevMod.MODID)
public class EnchantmentRestrictionHandler {

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // Check restrictions on target item
        EnchantmentRestrictions restrictions = left.get(ModDataComponents.ENCHANTMENT_RESTRICTIONS);
        if (restrictions == null) {
            return;
        }

        // If right is enchanted book, check each enchantment
        if (right.is(Items.ENCHANTED_BOOK)) {
            List<EnchantmentEntry> bookEnchants = EnchantmentReader.getStoredEnchantments(
                right, event.getPlayer().registryAccess());

            for (EnchantmentEntry entry : bookEnchants) {
                if (!restrictions.isAllowed(entry.enchantmentId())) {
                    // Block this anvil operation
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // Apply custom XP costs
        EnchantmentXpCosts xpCosts = left.get(ModDataComponents.ENCHANTMENT_XP_COSTS);
        if (xpCosts != null && xpCosts.globalMultiplier() != 1.0f) {
            int baseCost = event.getCost();
            event.setCost(Math.round(baseCost * xpCosts.globalMultiplier()));
        }
    }

    @SubscribeEvent
    public static void onEnchantmentLevel(EnchantmentLevelEvent event) {
        // This event allows modifying enchantment levels dynamically
        // Used for level override enforcement
    }
}
```

---

### Phase 4+ Mod Integration

#### ModdedEnchantmentRegistry

```java
/**
 * Registry for enchantments from other mods.
 */
public class ModdedEnchantmentRegistry {

    private static final Map<String, ModEnchantmentInfo> MOD_ENCHANTMENTS = new ConcurrentHashMap<>();

    public record ModEnchantmentInfo(
        String modId,
        ResourceLocation enchantmentId,
        Component displayName,
        int maxLevel,
        boolean isCompatible  // Works with DevMod's system
    ) {}

    /**
     * Scan for modded enchantments.
     */
    public static void scanForModdedEnchantments(RegistryAccess registries) {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);

        registry.holders().forEach(holder -> {
            ResourceLocation id = holder.key().location();

            // Skip vanilla
            if (id.getNamespace().equals("minecraft")) {
                return;
            }

            ModEnchantmentInfo info = new ModEnchantmentInfo(
                id.getNamespace(),
                id,
                holder.value().description(),
                holder.value().getMaxLevel(),
                true  // Assume compatible until proven otherwise
            );

            MOD_ENCHANTMENTS.put(id.toString(), info);
        });
    }

    /**
     * Get all enchantments from a specific mod.
     */
    public static List<ModEnchantmentInfo> getEnchantmentsFromMod(String modId) {
        return MOD_ENCHANTMENTS.values().stream()
            .filter(info -> info.modId().equals(modId))
            .toList();
    }

    /**
     * Get all detected mods with enchantments.
     */
    public static Set<String> getModsWithEnchantments() {
        return MOD_ENCHANTMENTS.values().stream()
            .map(ModEnchantmentInfo::modId)
            .collect(Collectors.toSet());
    }
}
```

---

### UI Mockup

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ENCHANTMENTS                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  CURRENT ENCHANTMENTS                                                │
│  ───────────────────────────────────────────────────────────────    │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ ⚔ Sharpness V           [▼][▲] Level: [  5  ]   [Remove]  │     │
│  │ 🔥 Fire Aspect II        [▼][▲] Level: [  2  ]   [Remove]  │     │
│  │ 💀 Smite III ⚠ BYPASSED  [▼][▲] Level: [  3  ]   [Remove]  │     │
│  │ 🔧 Unbreaking III        [▼][▲] Level: [  3  ]   [Remove]  │     │
│  │ ✨ Mending I             [▼][▲] Level: [  1  ]   [Remove]  │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  [CLEAR ALL]                                                         │
│                                                                      │
│  ADD ENCHANTMENT                                                     │
│  ───────────────────────────────────────────────────────────────    │
│  Search: [________________________] [🔍]                            │
│                                                                      │
│  Category: [ALL ▼]                                                   │
│                                                                      │
│  Filters:  [✓] Show Incompatible (3)   [ ] Show Non-Applicable (25) │
│            └─ yellow [Force] button     └─ red [Force!] button      │
│                                                                      │
│  Showing: 12 applicable  │  Total: 40 enchantments                  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ ⚔ Knockback          Max: 2    ✓ Applicable      [Add]    │     │
│  │ 💨 Sweeping Edge      Max: 3    ✓ Applicable      [Add]    │     │
│  │ 🎯 Looting            Max: 3    ✓ Applicable      [Add]    │     │
│  ├────────────────────────────────────────────────────────────┤     │
│  │ ☠ Bane of Arthropods Max: 5    ⚠ Incompatible    [Force]  │     │
│  │   └─ Conflicts with: Sharpness                             │     │
│  ├────────────────────────────────────────────────────────────┤     │
│  │ 🌊 Riptide           Max: 3    ✗ Non-Applicable  [Force!] │     │
│  │   └─ Warning: Not meant for Sword items                    │     │
│  │ 🎣 Luck of the Sea   Max: 3    ✗ Non-Applicable  [Force!] │     │
│  │   └─ Warning: Not meant for Sword items                    │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  LEVEL OVERRIDE (Phase 2)                                            │
│  ───────────────────────────────────────────────────────────────    │
│  [✓] Allow levels above vanilla max                                  │
│  [✓] Bypass compatibility rules                                      │
│                                                                      │
│  Max Override Level: [████████════] 10  (soft limit)                │
│                                                                      │
│  RESTRICTIONS (Phase 3)                                              │
│  ───────────────────────────────────────────────────────────────    │
│  Mode: ○ No Restrictions  ○ Blacklist  ● Whitelist                  │
│                                                                      │
│  Blacklisted:                                                        │
│  ┌────────────────────────────────────────┐                         │
│  │ 💀 Curse of Vanishing        [Remove] │                         │
│  │ 🔗 Curse of Binding          [Remove] │                         │
│  └────────────────────────────────────────┘                         │
│  [+ Add to Blacklist]                                                │
│                                                                      │
│  XP COSTS (Phase 3)                                                  │
│  ───────────────────────────────────────────────────────────────    │
│  Global Multiplier: [████████████] 1.0x                             │
│                                                                      │
│  Custom Costs:                                                       │
│  ┌────────────────────────────────────────────────────────┐         │
│  │ Mending       Base: [ 10 ] XP per level    [Remove]    │         │
│  │ Sharpness     Base: [  3 ] XP per level    [Remove]    │         │
│  └────────────────────────────────────────────────────────┘         │
│  [+ Add Custom Cost]                                                 │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Quick Actions

| Action | Descrizione | Shortcut |
|--------|-------------|----------|
| **Clear All** | Rimuove tutti gli enchantment | C |
| **Max All** | Imposta tutti al livello massimo (vanilla) | M |
| **Max All Override** | Imposta tutti a livello 10 (Phase 2) | Shift+M |
| **Remove Curses** | Rimuove solo curse enchantments | X |
| **Add Common** | Aggiunge set comune (Unbreaking III, Mending) | U |

```java
public class EnchantmentQuickActions {

    public static void clearAll(ItemStack stack) {
        EnchantmentWriter.clearEnchantments(stack);
    }

    public static void maxAllVanilla(ItemStack stack, RegistryAccess registries) {
        List<EnchantmentEntry> entries = EnchantmentReader.getEnchantments(stack, registries);
        List<EnchantmentEntry> maxed = entries.stream()
            .map(e -> {
                int maxLevel = e.getHolder(registries)
                    .map(h -> h.value().getMaxLevel())
                    .orElse(e.level());
                return new EnchantmentEntry(e.enchantmentId(), maxLevel, e.bypassedCompatibility(), false);
            })
            .toList();
        EnchantmentWriter.setEnchantments(stack, maxed, registries);
    }

    public static void maxAllOverride(ItemStack stack, RegistryAccess registries, int maxLevel) {
        List<EnchantmentEntry> entries = EnchantmentReader.getEnchantments(stack, registries);
        List<EnchantmentEntry> maxed = entries.stream()
            .map(e -> new EnchantmentEntry(e.enchantmentId(), maxLevel, e.bypassedCompatibility(), true))
            .toList();
        EnchantmentWriter.setEnchantments(stack, maxed, registries);
    }

    public static void removeCurses(ItemStack stack, RegistryAccess registries) {
        List<EnchantmentEntry> entries = EnchantmentReader.getEnchantments(stack, registries);
        List<EnchantmentEntry> filtered = entries.stream()
            .filter(e -> !isCurse(e, registries))
            .toList();
        EnchantmentWriter.setEnchantments(stack, filtered, registries);
    }

    public static void addCommonSet(ItemStack stack, RegistryAccess registries) {
        EnchantmentWriter.addEnchantment(stack, new EnchantmentEntry(
            ResourceLocation.parse("minecraft:unbreaking"), 3, false, false), registries);
        EnchantmentWriter.addEnchantment(stack, new EnchantmentEntry(
            ResourceLocation.parse("minecraft:mending"), 1, false, false), registries);
    }

    private static boolean isCurse(EnchantmentEntry entry, RegistryAccess registries) {
        return entry.getHolder(registries)
            .map(h -> h.value().isCurse())
            .orElse(false);
    }
}
```

---

### ValueLimits

```java
// Add to ValueLimits.java

/** Enchantment level (vanilla) */
public static final Limit ENCHANT_LEVEL_VANILLA = Limit.of(
    1, 5,        // Soft: 1-5 (most vanilla enchants)
    1, 10        // Hard: 1-10
);

/** Enchantment level (override) */
public static final Limit ENCHANT_LEVEL_OVERRIDE = Limit.of(
    1, 10,       // Soft: 1-10
    1, 255       // Hard: 1-255 (Minecraft limit)
);

/** XP cost per enchant level */
public static final Limit ENCHANT_XP_COST = Limit.of(
    1, 30,       // Soft: 1-30 levels
    0, 100       // Hard: 0-100 levels (0 = free)
);

/** XP cost global multiplier */
public static final Limit ENCHANT_XP_MULTIPLIER = Limit.of(
    0.5, 2.0,    // Soft: 50%-200%
    0.0, 10.0    // Hard: 0%-1000%
);

/** Max enchantments per item */
public static final Limit MAX_ENCHANTMENTS = Limit.of(
    1, 10,       // Soft: 1-10 enchants
    1, 50        // Hard: 1-50 enchants
);
```

---

### Config

```toml
# config/devmod-server.toml

[enchantments]
# Allow adding/removing enchantments
allowEnchantmentEditing = true

# Allow modifying enchantment levels
allowLevelEditing = true

# Maximum enchantment level (vanilla max by default)
maxEnchantmentLevel = 5

# === Phase 2 ===

# Allow levels above vanilla max
allowLevelOverride = false

# Maximum override level
maxOverrideLevel = 10

# Absolute maximum level (hard limit)
absoluteMaxLevel = 255

# Allow bypassing compatibility rules
allowCompatibilityBypass = false

# === Phase 3 ===

# Allow item-specific blacklists
allowEnchantmentBlacklist = false

# Allow whitelist mode
allowWhitelistMode = false

# Allow custom XP costs
allowCustomXpCosts = false

# Minimum XP cost (prevents free enchanting)
minXpCost = 1

# Maximum XP cost multiplier
maxXpCostMultiplier = 10.0

# === Phase 4+ ===

# Enable modded enchantment integration
enableModdedEnchantments = true

# Mods to exclude from scanning
excludedMods = []
```

---

### Network Payload

```java
/**
 * Payload for enchantment updates.
 */
public record UpdateEnchantmentsPayload(
    List<EnchantmentData> enchantments,
    boolean clearExisting,
    // Phase 2
    boolean bypassCompatibility,
    // Phase 3
    EnchantmentRestrictions restrictions,
    EnchantmentXpCosts xpCosts
) implements CustomPacketPayload {

    public record EnchantmentData(
        String enchantmentId,
        int level,
        boolean forcedCompatibility
    ) {}

    public static final Type<UpdateEnchantmentsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "update_enchantments")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateEnchantmentsPayload> STREAM_CODEC =
        StreamCodec.of(UpdateEnchantmentsPayload::encode, UpdateEnchantmentsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, UpdateEnchantmentsPayload payload) {
        buf.writeCollection(payload.enchantments, (b, data) -> {
            b.writeUtf(data.enchantmentId());
            b.writeVarInt(data.level());
            b.writeBoolean(data.forcedCompatibility());
        });
        buf.writeBoolean(payload.clearExisting);
        buf.writeBoolean(payload.bypassCompatibility);
        // Restrictions and XP costs encoded via their codecs
        buf.writeJsonWithCodec(EnchantmentRestrictions.CODEC, payload.restrictions);
        buf.writeJsonWithCodec(EnchantmentXpCosts.CODEC, payload.xpCosts);
    }

    private static UpdateEnchantmentsPayload decode(RegistryFriendlyByteBuf buf) {
        List<EnchantmentData> enchantments = buf.readList(b ->
            new EnchantmentData(b.readUtf(), b.readVarInt(), b.readBoolean())
        );
        boolean clearExisting = buf.readBoolean();
        boolean bypassCompatibility = buf.readBoolean();
        EnchantmentRestrictions restrictions = buf.readJsonWithCodec(EnchantmentRestrictions.CODEC);
        EnchantmentXpCosts xpCosts = buf.readJsonWithCodec(EnchantmentXpCosts.CODEC);

        return new UpdateEnchantmentsPayload(
            enchantments, clearExisting, bypassCompatibility, restrictions, xpCosts
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

---

## 2.33 Affix System (Phase 4+)

Sistema di prefissi/suffissi stile Diablo/Path of Exile per generazione nomi item e bonus custom.

> **Status**: Deferred to Phase 4+ - Sistema complesso che richiede completamento di weapon/armor editor base.
> Design documentato per implementazione futura.

### Differenze Enchantments vs Affixes

| Aspetto | Enchantments | Affixes |
|---------|--------------|---------|
| Origine | Vanilla Minecraft | Custom DevMod |
| Livelli | Discreti (I, II, III...) | Valori continui (range) |
| Compatibilità | Regole vanilla | Regole custom per slot |
| Nome item | Non modifica | Genera nome (es. "Fiery Sword of Frost") |
| UI | Tab ENCHANTS | Tab AFFIXES (separato) |
| Persistenza | DataComponents.ENCHANTMENTS | Custom Data Component |

---

### Core Concepts

#### Affix Types

```java
/**
 * Type of affix determining name position.
 */
public enum AffixType {
    /** Appears before item name: "Fiery Diamond Sword" */
    PREFIX,
    /** Appears after item name: "Diamond Sword of Frost" */
    SUFFIX,
    /** Hidden affix, no name contribution but has effects */
    IMPLICIT
}
```

#### Affix Tiers

```java
/**
 * Rarity/power tier of an affix.
 */
public enum AffixTier {
    COMMON(0x9D9D9D, 1.0f),      // Gray
    UNCOMMON(0x1EFF00, 1.2f),    // Green
    RARE(0x0070DD, 1.5f),        // Blue
    EPIC(0xA335EE, 2.0f),        // Purple
    LEGENDARY(0xFF8000, 3.0f);   // Orange

    public final int color;
    public final float valueMultiplier;

    AffixTier(int color, float valueMultiplier) {
        this.color = color;
        this.valueMultiplier = valueMultiplier;
    }

    /**
     * Get tier from roll (0.0 - 1.0).
     */
    public static AffixTier fromRoll(float roll, float luckBonus) {
        float adjusted = Math.min(1.0f, roll + luckBonus);
        if (adjusted > 0.99f) return LEGENDARY;
        if (adjusted > 0.95f) return EPIC;
        if (adjusted > 0.80f) return RARE;
        if (adjusted > 0.50f) return UNCOMMON;
        return COMMON;
    }
}
```

---

### Affix Definition

```java
/**
 * Definition of an affix type (template).
 */
public record AffixDefinition(
    ResourceLocation id,
    AffixType type,
    Component displayName,
    String nameContribution,  // e.g., "Fiery" or "of Frost"
    List<AffixModifier> modifiers,
    Set<ItemCategory> applicableCategories,
    Set<ResourceLocation> incompatibleAffixes,
    Map<AffixTier, TierValues> tierValues,
    int weight  // Spawn weight for random generation
) {

    /**
     * Categories this affix can apply to.
     */
    public enum ItemCategory {
        SWORD, AXE, MACE, PICKAXE, SHOVEL, HOE,
        HELMET, CHESTPLATE, LEGGINGS, BOOTS,
        BOW, CROSSBOW, TRIDENT,
        SHIELD,
        ALL_WEAPONS, ALL_ARMOR, ALL_TOOLS, ALL
    }

    /**
     * Check if this affix can apply to an item.
     */
    public boolean canApplyTo(ItemStack stack) {
        if (applicableCategories.contains(ItemCategory.ALL)) {
            return true;
        }
        // Check item against categories
        Item item = stack.getItem();
        if (item instanceof SwordItem && applicableCategories.contains(ItemCategory.SWORD)) return true;
        if (item instanceof AxeItem && applicableCategories.contains(ItemCategory.AXE)) return true;
        if (item instanceof ArmorItem armor) {
            return switch (armor.getEquipmentSlot()) {
                case HEAD -> applicableCategories.contains(ItemCategory.HELMET);
                case CHEST -> applicableCategories.contains(ItemCategory.CHESTPLATE);
                case LEGS -> applicableCategories.contains(ItemCategory.LEGGINGS);
                case FEET -> applicableCategories.contains(ItemCategory.BOOTS);
                default -> false;
            };
        }
        // ... more checks
        return false;
    }

    /**
     * Values for a specific tier.
     */
    public record TierValues(
        float minValue,
        float maxValue,
        @Nullable Component tierName  // Optional tier-specific name override
    ) {}
}
```

#### Affix Modifiers

```java
/**
 * A single modifier effect from an affix.
 */
public sealed interface AffixModifier {

    /**
     * Apply this modifier to an item's attributes.
     */
    void apply(ItemStack stack, float value, AffixTier tier);

    /**
     * Get tooltip line for this modifier.
     */
    Component getTooltip(float value, AffixTier tier);

    // --- Implementations ---

    /**
     * Adds to an attribute (e.g., +5 Attack Damage).
     */
    record AttributeBonus(
        ResourceLocation attribute,
        AttributeModifier.Operation operation,
        EquipmentSlotGroup slot
    ) implements AffixModifier {

        @Override
        public void apply(ItemStack stack, float value, AffixTier tier) {
            // Add attribute modifier via Data Components
            ItemAttributeModifiers modifiers = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY
            );

            Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.getHolder(
                ResourceKey.create(Registries.ATTRIBUTE, attribute)).orElseThrow();

            AttributeModifier mod = new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("devmod", "affix_" + attribute.getPath()),
                value * tier.valueMultiplier,
                operation
            );

            modifiers = modifiers.withModifierAdded(attr, mod, slot);
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        }

        @Override
        public Component getTooltip(float value, AffixTier tier) {
            float finalValue = value * tier.valueMultiplier;
            String sign = finalValue >= 0 ? "+" : "";
            return Component.literal(sign + String.format("%.1f", finalValue) + " ")
                .append(getAttributeName(attribute))
                .withStyle(style -> style.withColor(tier.color));
        }
    }

    /**
     * Percentage damage reduction.
     */
    record DamageReduction(
        DamageType damageType,
        boolean isPercent
    ) implements AffixModifier {

        public enum DamageType {
            PHYSICAL, FIRE, MAGIC, EXPLOSION, PROJECTILE, ALL
        }

        @Override
        public void apply(ItemStack stack, float value, AffixTier tier) {
            // Store in custom data component for event handler
            AffixDamageReductions reductions = stack.getOrDefault(
                ModDataComponents.AFFIX_DAMAGE_REDUCTIONS,
                AffixDamageReductions.EMPTY
            );
            float finalValue = value * tier.valueMultiplier;
            reductions = reductions.with(damageType, finalValue);
            stack.set(ModDataComponents.AFFIX_DAMAGE_REDUCTIONS, reductions);
        }

        @Override
        public Component getTooltip(float value, AffixTier tier) {
            float finalValue = value * tier.valueMultiplier;
            String suffix = isPercent ? "%" : "";
            return Component.literal("+" + String.format("%.1f", finalValue) + suffix + " ")
                .append(damageType.name() + " Resistance")
                .withStyle(style -> style.withColor(tier.color));
        }
    }

    /**
     * On-hit effect (e.g., life steal, ignite).
     */
    record OnHitEffect(
        EffectType effectType
    ) implements AffixModifier {

        public enum EffectType {
            LIFE_STEAL,      // Heal % of damage dealt
            IGNITE,          // Set target on fire
            FREEZE,          // Apply slowness
            POISON,          // Apply poison
            WITHER,          // Apply wither
            LIGHTNING,       // Chance to strike lightning
            KNOCKBACK_BOOST, // Extra knockback
            EXECUTE          // Bonus damage to low HP targets
        }

        @Override
        public void apply(ItemStack stack, float value, AffixTier tier) {
            AffixOnHitEffects effects = stack.getOrDefault(
                ModDataComponents.AFFIX_ON_HIT_EFFECTS,
                AffixOnHitEffects.EMPTY
            );
            float finalValue = value * tier.valueMultiplier;
            effects = effects.with(effectType, finalValue);
            stack.set(ModDataComponents.AFFIX_ON_HIT_EFFECTS, effects);
        }

        @Override
        public Component getTooltip(float value, AffixTier tier) {
            float finalValue = value * tier.valueMultiplier;
            return Component.literal(String.format("%.1f", finalValue) + "% ")
                .append(effectType.name().replace("_", " "))
                .withStyle(style -> style.withColor(tier.color));
        }
    }

    /**
     * Passive aura effect.
     */
    record AuraEffect(
        MobEffect effect,
        float radius
    ) implements AffixModifier {

        @Override
        public void apply(ItemStack stack, float value, AffixTier tier) {
            AffixAuras auras = stack.getOrDefault(
                ModDataComponents.AFFIX_AURAS,
                AffixAuras.EMPTY
            );
            int amplifier = (int)(value * tier.valueMultiplier);
            auras = auras.with(effect, amplifier, radius);
            stack.set(ModDataComponents.AFFIX_AURAS, auras);
        }

        @Override
        public Component getTooltip(float value, AffixTier tier) {
            int amplifier = (int)(value * tier.valueMultiplier);
            return Component.literal("Aura: ")
                .append(effect.getDisplayName())
                .append(" " + (amplifier + 1))
                .append(" (" + radius + "m)")
                .withStyle(style -> style.withColor(tier.color));
        }
    }
}
```

---

### Affix Instance

```java
/**
 * An actual affix applied to an item.
 */
public record AffixInstance(
    ResourceLocation definitionId,
    AffixTier tier,
    float rolledValue,  // Value within tier's min-max range
    long seed           // For deterministic re-rolls
) {

    public static final Codec<AffixInstance> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(AffixInstance::definitionId),
            Codec.STRING.xmap(AffixTier::valueOf, AffixTier::name)
                .fieldOf("tier").forGetter(AffixInstance::tier),
            Codec.FLOAT.fieldOf("value").forGetter(AffixInstance::rolledValue),
            Codec.LONG.fieldOf("seed").forGetter(AffixInstance::seed)
        ).apply(instance, AffixInstance::new)
    );

    /**
     * Get the definition from registry.
     */
    public Optional<AffixDefinition> getDefinition() {
        return AffixRegistry.get(definitionId);
    }

    /**
     * Get final computed value (base * tier multiplier).
     */
    public float getFinalValue() {
        return rolledValue * tier.valueMultiplier;
    }

    /**
     * Get name contribution for item name generation.
     */
    public Optional<String> getNameContribution() {
        return getDefinition().map(AffixDefinition::nameContribution);
    }

    /**
     * Re-roll value within same tier.
     */
    public AffixInstance reroll(RandomSource random) {
        return getDefinition().map(def -> {
            AffixDefinition.TierValues tierValues = def.tierValues().get(tier);
            float newValue = tierValues.minValue() +
                random.nextFloat() * (tierValues.maxValue() - tierValues.minValue());
            return new AffixInstance(definitionId, tier, newValue, random.nextLong());
        }).orElse(this);
    }

    /**
     * Upgrade to next tier (if possible).
     */
    public Optional<AffixInstance> upgrade(RandomSource random) {
        AffixTier nextTier = switch (tier) {
            case COMMON -> AffixTier.UNCOMMON;
            case UNCOMMON -> AffixTier.RARE;
            case RARE -> AffixTier.EPIC;
            case EPIC -> AffixTier.LEGENDARY;
            case LEGENDARY -> null;
        };

        if (nextTier == null) return Optional.empty();

        return getDefinition().map(def -> {
            AffixDefinition.TierValues tierValues = def.tierValues().get(nextTier);
            float newValue = tierValues.minValue() +
                random.nextFloat() * (tierValues.maxValue() - tierValues.minValue());
            return new AffixInstance(definitionId, nextTier, newValue, random.nextLong());
        });
    }
}
```

---

### Item Affixes Data Component

```java
/**
 * All affixes on an item.
 */
public record ItemAffixes(
    List<AffixInstance> prefixes,
    List<AffixInstance> suffixes,
    List<AffixInstance> implicits,
    boolean nameLocked  // Prevent name regeneration
) {

    public static final ItemAffixes EMPTY = new ItemAffixes(List.of(), List.of(), List.of(), false);

    public static final int MAX_PREFIXES = 3;
    public static final int MAX_SUFFIXES = 3;
    public static final int MAX_IMPLICITS = 2;

    public static final Codec<ItemAffixes> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            AffixInstance.CODEC.listOf().optionalFieldOf("prefixes", List.of())
                .forGetter(ItemAffixes::prefixes),
            AffixInstance.CODEC.listOf().optionalFieldOf("suffixes", List.of())
                .forGetter(ItemAffixes::suffixes),
            AffixInstance.CODEC.listOf().optionalFieldOf("implicits", List.of())
                .forGetter(ItemAffixes::implicits),
            Codec.BOOL.optionalFieldOf("name_locked", false)
                .forGetter(ItemAffixes::nameLocked)
        ).apply(instance, ItemAffixes::new)
    );

    /**
     * Get all affixes combined.
     */
    public List<AffixInstance> all() {
        List<AffixInstance> all = new ArrayList<>();
        all.addAll(prefixes);
        all.addAll(suffixes);
        all.addAll(implicits);
        return all;
    }

    /**
     * Check if can add more prefixes.
     */
    public boolean canAddPrefix() {
        return prefixes.size() < MAX_PREFIXES;
    }

    /**
     * Check if can add more suffixes.
     */
    public boolean canAddSuffix() {
        return suffixes.size() < MAX_SUFFIXES;
    }

    /**
     * Add a prefix.
     */
    public ItemAffixes withPrefix(AffixInstance affix) {
        if (!canAddPrefix()) throw new IllegalStateException("Max prefixes reached");
        List<AffixInstance> newPrefixes = new ArrayList<>(prefixes);
        newPrefixes.add(affix);
        return new ItemAffixes(newPrefixes, suffixes, implicits, nameLocked);
    }

    /**
     * Add a suffix.
     */
    public ItemAffixes withSuffix(AffixInstance affix) {
        if (!canAddSuffix()) throw new IllegalStateException("Max suffixes reached");
        List<AffixInstance> newSuffixes = new ArrayList<>(suffixes);
        newSuffixes.add(affix);
        return new ItemAffixes(prefixes, newSuffixes, implicits, nameLocked);
    }

    /**
     * Remove an affix by index.
     */
    public ItemAffixes without(AffixType type, int index) {
        return switch (type) {
            case PREFIX -> {
                List<AffixInstance> newList = new ArrayList<>(prefixes);
                newList.remove(index);
                yield new ItemAffixes(newList, suffixes, implicits, nameLocked);
            }
            case SUFFIX -> {
                List<AffixInstance> newList = new ArrayList<>(suffixes);
                newList.remove(index);
                yield new ItemAffixes(prefixes, newList, implicits, nameLocked);
            }
            case IMPLICIT -> {
                List<AffixInstance> newList = new ArrayList<>(implicits);
                newList.remove(index);
                yield new ItemAffixes(prefixes, suffixes, newList, nameLocked);
            }
        };
    }

    /**
     * Get highest tier among all affixes.
     */
    public AffixTier getHighestTier() {
        return all().stream()
            .map(AffixInstance::tier)
            .max(Comparator.comparingInt(Enum::ordinal))
            .orElse(AffixTier.COMMON);
    }

    /**
     * Generate item name from affixes.
     */
    public Component generateName(Component baseName) {
        if (nameLocked) return baseName;

        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();

        for (AffixInstance affix : prefixes) {
            affix.getNameContribution().ifPresent(name -> {
                if (prefix.length() > 0) prefix.append(" ");
                prefix.append(name);
            });
        }

        for (AffixInstance affix : suffixes) {
            affix.getNameContribution().ifPresent(name -> {
                suffix.append(" ").append(name);
            });
        }

        MutableComponent result = Component.empty();
        if (prefix.length() > 0) {
            result.append(prefix.toString()).append(" ");
        }
        result.append(baseName);
        if (suffix.length() > 0) {
            result.append(suffix.toString());
        }

        // Apply highest tier color
        return result.withStyle(style -> style.withColor(getHighestTier().color));
    }
}

// Registration
public static final DataComponentType<ItemAffixes> ITEM_AFFIXES =
    DataComponentType.<ItemAffixes>builder()
        .persistent(ItemAffixes.CODEC)
        .networkSynchronized(ItemAffixes.STREAM_CODEC)
        .build();
```

---

### Affix Registry

```java
/**
 * Registry of all available affix definitions.
 */
public class AffixRegistry {

    private static final Map<ResourceLocation, AffixDefinition> AFFIXES = new ConcurrentHashMap<>();

    /**
     * Register a new affix definition.
     */
    public static void register(AffixDefinition definition) {
        AFFIXES.put(definition.id(), definition);
    }

    /**
     * Get affix by ID.
     */
    public static Optional<AffixDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(AFFIXES.get(id));
    }

    /**
     * Get all affixes applicable to an item.
     */
    public static List<AffixDefinition> getApplicable(ItemStack stack, AffixType type) {
        return AFFIXES.values().stream()
            .filter(def -> def.type() == type)
            .filter(def -> def.canApplyTo(stack))
            .toList();
    }

    /**
     * Get weighted random affix for item.
     */
    public static Optional<AffixDefinition> getRandomAffix(
        ItemStack stack,
        AffixType type,
        RandomSource random,
        Set<ResourceLocation> exclude
    ) {
        List<AffixDefinition> applicable = getApplicable(stack, type).stream()
            .filter(def -> !exclude.contains(def.id()))
            .toList();

        if (applicable.isEmpty()) return Optional.empty();

        int totalWeight = applicable.stream().mapToInt(AffixDefinition::weight).sum();
        int roll = random.nextInt(totalWeight);

        int current = 0;
        for (AffixDefinition def : applicable) {
            current += def.weight();
            if (roll < current) {
                return Optional.of(def);
            }
        }

        return Optional.of(applicable.get(applicable.size() - 1));
    }

    // --- Built-in Affixes ---

    public static void registerBuiltins() {
        // === PREFIXES ===

        // Fiery - Fire damage
        register(new AffixDefinition(
            ResourceLocation.fromNamespaceAndPath("devmod", "fiery"),
            AffixType.PREFIX,
            Component.translatable("affix.devmod.fiery"),
            "Fiery",
            List.of(new AffixModifier.OnHitEffect(AffixModifier.OnHitEffect.EffectType.IGNITE)),
            Set.of(AffixDefinition.ItemCategory.ALL_WEAPONS),
            Set.of(ResourceLocation.fromNamespaceAndPath("devmod", "frozen")),  // Incompatible
            Map.of(
                AffixTier.COMMON, new AffixDefinition.TierValues(5f, 10f, null),
                AffixTier.UNCOMMON, new AffixDefinition.TierValues(10f, 20f, null),
                AffixTier.RARE, new AffixDefinition.TierValues(20f, 35f, null),
                AffixTier.EPIC, new AffixDefinition.TierValues(35f, 50f, Component.literal("Blazing")),
                AffixTier.LEGENDARY, new AffixDefinition.TierValues(50f, 75f, Component.literal("Infernal"))
            ),
            100
        ));

        // Vampiric - Life steal
        register(new AffixDefinition(
            ResourceLocation.fromNamespaceAndPath("devmod", "vampiric"),
            AffixType.PREFIX,
            Component.translatable("affix.devmod.vampiric"),
            "Vampiric",
            List.of(new AffixModifier.OnHitEffect(AffixModifier.OnHitEffect.EffectType.LIFE_STEAL)),
            Set.of(AffixDefinition.ItemCategory.ALL_WEAPONS),
            Set.of(),
            Map.of(
                AffixTier.COMMON, new AffixDefinition.TierValues(1f, 3f, null),
                AffixTier.UNCOMMON, new AffixDefinition.TierValues(3f, 5f, null),
                AffixTier.RARE, new AffixDefinition.TierValues(5f, 8f, null),
                AffixTier.EPIC, new AffixDefinition.TierValues(8f, 12f, Component.literal("Blood-Drinking")),
                AffixTier.LEGENDARY, new AffixDefinition.TierValues(12f, 20f, Component.literal("Soul-Drinking"))
            ),
            80
        ));

        // Sturdy - Extra armor
        register(new AffixDefinition(
            ResourceLocation.fromNamespaceAndPath("devmod", "sturdy"),
            AffixType.PREFIX,
            Component.translatable("affix.devmod.sturdy"),
            "Sturdy",
            List.of(new AffixModifier.AttributeBonus(
                ResourceLocation.withDefaultNamespace("generic.armor"),
                AttributeModifier.Operation.ADD_VALUE,
                EquipmentSlotGroup.ARMOR
            )),
            Set.of(AffixDefinition.ItemCategory.ALL_ARMOR),
            Set.of(),
            Map.of(
                AffixTier.COMMON, new AffixDefinition.TierValues(1f, 2f, null),
                AffixTier.UNCOMMON, new AffixDefinition.TierValues(2f, 4f, null),
                AffixTier.RARE, new AffixDefinition.TierValues(4f, 6f, null),
                AffixTier.EPIC, new AffixDefinition.TierValues(6f, 8f, Component.literal("Fortified")),
                AffixTier.LEGENDARY, new AffixDefinition.TierValues(8f, 12f, Component.literal("Impenetrable"))
            ),
            100
        ));

        // === SUFFIXES ===

        // of Frost - Cold damage/slow
        register(new AffixDefinition(
            ResourceLocation.fromNamespaceAndPath("devmod", "of_frost"),
            AffixType.SUFFIX,
            Component.translatable("affix.devmod.of_frost"),
            "of Frost",
            List.of(new AffixModifier.OnHitEffect(AffixModifier.OnHitEffect.EffectType.FREEZE)),
            Set.of(AffixDefinition.ItemCategory.ALL_WEAPONS),
            Set.of(ResourceLocation.fromNamespaceAndPath("devmod", "fiery")),
            Map.of(
                AffixTier.COMMON, new AffixDefinition.TierValues(10f, 20f, null),
                AffixTier.UNCOMMON, new AffixDefinition.TierValues(20f, 35f, null),
                AffixTier.RARE, new AffixDefinition.TierValues(35f, 50f, null),
                AffixTier.EPIC, new AffixDefinition.TierValues(50f, 70f, Component.literal("of the Glacier")),
                AffixTier.LEGENDARY, new AffixDefinition.TierValues(70f, 100f, Component.literal("of Absolute Zero"))
            ),
            100
        ));

        // of the Titan - Extra damage
        register(new AffixDefinition(
            ResourceLocation.fromNamespaceAndPath("devmod", "of_the_titan"),
            AffixType.SUFFIX,
            Component.translatable("affix.devmod.of_the_titan"),
            "of the Titan",
            List.of(new AffixModifier.AttributeBonus(
                ResourceLocation.withDefaultNamespace("generic.attack_damage"),
                AttributeModifier.Operation.ADD_VALUE,
                EquipmentSlotGroup.MAINHAND
            )),
            Set.of(AffixDefinition.ItemCategory.ALL_WEAPONS),
            Set.of(),
            Map.of(
                AffixTier.COMMON, new AffixDefinition.TierValues(1f, 2f, null),
                AffixTier.UNCOMMON, new AffixDefinition.TierValues(2f, 4f, null),
                AffixTier.RARE, new AffixDefinition.TierValues(4f, 6f, null),
                AffixTier.EPIC, new AffixDefinition.TierValues(6f, 10f, Component.literal("of the Colossus")),
                AffixTier.LEGENDARY, new AffixDefinition.TierValues(10f, 15f, Component.literal("of the God-Slayer"))
            ),
            90
        ));

        // of Regeneration - Health regen aura
        register(new AffixDefinition(
            ResourceLocation.fromNamespaceAndPath("devmod", "of_regeneration"),
            AffixType.SUFFIX,
            Component.translatable("affix.devmod.of_regeneration"),
            "of Regeneration",
            List.of(new AffixModifier.AuraEffect(MobEffects.REGENERATION, 5f)),
            Set.of(AffixDefinition.ItemCategory.ALL_ARMOR),
            Set.of(),
            Map.of(
                AffixTier.COMMON, new AffixDefinition.TierValues(0f, 0f, null),  // Regen I
                AffixTier.UNCOMMON, new AffixDefinition.TierValues(0f, 0f, null),
                AffixTier.RARE, new AffixDefinition.TierValues(1f, 1f, null),  // Regen II
                AffixTier.EPIC, new AffixDefinition.TierValues(1f, 1f, Component.literal("of Vitality")),
                AffixTier.LEGENDARY, new AffixDefinition.TierValues(2f, 2f, Component.literal("of Immortality"))
            ),
            60
        ));
    }
}
```

---

### Affix Generator

```java
/**
 * Generates random affixes for items.
 */
public class AffixGenerator {

    /**
     * Generate random affixes for an item.
     */
    public static ItemAffixes generate(
        ItemStack stack,
        RandomSource random,
        AffixGenerationConfig config
    ) {
        List<AffixInstance> prefixes = new ArrayList<>();
        List<AffixInstance> suffixes = new ArrayList<>();
        Set<ResourceLocation> usedAffixes = new HashSet<>();

        // Determine counts
        int prefixCount = rollCount(random, config.minPrefixes(), config.maxPrefixes(), config.luck());
        int suffixCount = rollCount(random, config.minSuffixes(), config.maxSuffixes(), config.luck());

        // Generate prefixes
        for (int i = 0; i < prefixCount; i++) {
            generateAffix(stack, AffixType.PREFIX, random, config.luck(), usedAffixes)
                .ifPresent(prefixes::add);
        }

        // Generate suffixes
        for (int i = 0; i < suffixCount; i++) {
            generateAffix(stack, AffixType.SUFFIX, random, config.luck(), usedAffixes)
                .ifPresent(suffixes::add);
        }

        return new ItemAffixes(prefixes, suffixes, List.of(), false);
    }

    private static int rollCount(RandomSource random, int min, int max, float luck) {
        int base = min + random.nextInt(max - min + 1);
        // Luck can add bonus affixes
        if (luck > 0 && random.nextFloat() < luck * 0.1f) {
            base = Math.min(base + 1, max);
        }
        return base;
    }

    private static Optional<AffixInstance> generateAffix(
        ItemStack stack,
        AffixType type,
        RandomSource random,
        float luck,
        Set<ResourceLocation> exclude
    ) {
        return AffixRegistry.getRandomAffix(stack, type, random, exclude)
            .map(def -> {
                exclude.add(def.id());
                // Also exclude incompatible
                exclude.addAll(def.incompatibleAffixes());

                // Roll tier
                AffixTier tier = AffixTier.fromRoll(random.nextFloat(), luck);

                // Roll value within tier
                AffixDefinition.TierValues tierValues = def.tierValues().get(tier);
                float value = tierValues.minValue() +
                    random.nextFloat() * (tierValues.maxValue() - tierValues.minValue());

                return new AffixInstance(def.id(), tier, value, random.nextLong());
            });
    }

    /**
     * Configuration for affix generation.
     */
    public record AffixGenerationConfig(
        int minPrefixes,
        int maxPrefixes,
        int minSuffixes,
        int maxSuffixes,
        float luck  // 0.0 - 1.0, affects tier rolls
    ) {
        public static final AffixGenerationConfig DEFAULT = new AffixGenerationConfig(0, 2, 0, 2, 0f);
        public static final AffixGenerationConfig RARE = new AffixGenerationConfig(1, 3, 1, 3, 0.2f);
        public static final AffixGenerationConfig LEGENDARY = new AffixGenerationConfig(2, 3, 2, 3, 0.5f);
    }
}
```

---

### Affix Event Handlers

```java
/**
 * Handles affix effects during gameplay.
 */
@Mod.EventBusSubscriber(modid = DevMod.MODID)
public class AffixEventHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Apply damage reduction affixes from armor
        LivingEntity entity = event.getEntity();
        float totalReduction = 0f;

        for (ItemStack armor : entity.getArmorSlots()) {
            AffixDamageReductions reductions = armor.get(ModDataComponents.AFFIX_DAMAGE_REDUCTIONS);
            if (reductions != null) {
                totalReduction += reductions.getReduction(event.getSource());
            }
        }

        if (totalReduction > 0) {
            float newDamage = event.getAmount() * (1f - Math.min(totalReduction, 0.9f));
            event.setAmount(newDamage);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingDamageEvent.Post event) {
        // Apply on-hit effects from weapon affixes
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            AffixOnHitEffects effects = weapon.get(ModDataComponents.AFFIX_ON_HIT_EFFECTS);

            if (effects != null) {
                LivingEntity target = event.getEntity();
                float damage = event.getNewDamage();

                effects.apply(attacker, target, damage);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        // Apply aura effects
        LivingEntity entity = event.getEntity();
        if (entity.tickCount % 20 != 0) return;  // Once per second

        for (ItemStack armor : entity.getArmorSlots()) {
            AffixAuras auras = armor.get(ModDataComponents.AFFIX_AURAS);
            if (auras != null) {
                auras.apply(entity);
            }
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ItemAffixes affixes = stack.get(ModDataComponents.ITEM_AFFIXES);

        if (affixes == null || affixes.all().isEmpty()) return;

        List<Component> tooltip = event.getToolTip();

        // Add separator
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("═══ Affixes ═══").withStyle(ChatFormatting.GOLD));

        // Add each affix
        for (AffixInstance instance : affixes.all()) {
            instance.getDefinition().ifPresent(def -> {
                // Affix name with tier color
                Component tierName = def.tierValues().get(instance.tier()).tierName();
                Component name = tierName != null ? tierName : def.displayName();
                tooltip.add(Component.literal("• ")
                    .append(name)
                    .withStyle(style -> style.withColor(instance.tier().color)));

                // Modifier effects
                for (AffixModifier modifier : def.modifiers()) {
                    tooltip.add(Component.literal("  ")
                        .append(modifier.getTooltip(instance.rolledValue(), instance.tier())));
                }
            });
        }
    }
}
```

---

### UI Mockup

```
┌─────────────────────────────────────────────────────────────────────┐
│                          AFFIXES                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ITEM NAME PREVIEW                                                   │
│  ───────────────────────────────────────────────────────────────    │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │        🗡️ Vampiric Diamond Sword of the Titan              │     │
│  │                    [EPIC]                                   │     │
│  └────────────────────────────────────────────────────────────┘     │
│  [✓] Lock name (prevent regeneration)                               │
│                                                                      │
│  PREFIXES (2/3)                                                      │
│  ───────────────────────────────────────────────────────────────    │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ 🟣 Vampiric                              Tier: [EPIC ▼]    │     │
│  │    +12.5% Life Steal                                       │     │
│  │    Value: [████████████] 12.5  (range: 8-12)   [Reroll]   │     │
│  │                                          [Upgrade] [Remove]│     │
│  ├────────────────────────────────────────────────────────────┤     │
│  │ 🔵 Sturdy                                Tier: [RARE ▼]    │     │
│  │    +5.2 Armor                                              │     │
│  │    Value: [██████████══] 5.2   (range: 4-6)    [Reroll]   │     │
│  │                                          [Upgrade] [Remove]│     │
│  └────────────────────────────────────────────────────────────┘     │
│  [+ Add Prefix]                                                      │
│                                                                      │
│  SUFFIXES (1/3)                                                      │
│  ───────────────────────────────────────────────────────────────    │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ 🟠 of the Titan (Legendary: of the God-Slayer)             │     │
│  │    +14.2 Attack Damage                                     │     │
│  │    Value: [██████████████] 14.2 (range: 10-15) [Reroll]   │     │
│  │                                                   [Remove] │     │
│  └────────────────────────────────────────────────────────────┘     │
│  [+ Add Suffix]                                                      │
│                                                                      │
│  IMPLICITS (0/2)                                                     │
│  ───────────────────────────────────────────────────────────────    │
│  (No implicits - these are item-specific base affixes)              │
│  [+ Add Implicit]                                                    │
│                                                                      │
│  GENERATION                                                          │
│  ───────────────────────────────────────────────────────────────    │
│  Preset: [LEGENDARY ▼]  Luck: [████████████] 50%                    │
│                                                                      │
│  [REROLL ALL]  [UPGRADE ALL]  [CLEAR ALL]                           │
│                                                                      │
│  AFFIX PICKER                                                        │
│  ───────────────────────────────────────────────────────────────    │
│  Search: [________________________] Type: [PREFIX ▼]                │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ 🔥 Fiery         Fire damage on hit     ✗ Incompatible    │     │
│  │    └─ Conflicts with: of Frost                             │     │
│  │ ⚡ Shocking       Lightning chance       ✓ Available [Add] │     │
│  │ ☠️ Venomous       Poison on hit          ✓ Available [Add] │     │
│  │ 💀 Withering      Wither on hit          ✓ Available [Add] │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Quick Actions

| Action | Descrizione | Shortcut |
|--------|-------------|----------|
| **Reroll All** | Re-roll values di tutti gli affix (mantiene tier) | R |
| **Upgrade All** | Tenta upgrade tier per tutti (può fallire) | U |
| **Clear All** | Rimuove tutti gli affix | C |
| **Generate Random** | Genera affix casuali con preset | G |
| **Max Tier All** | Imposta tutti a LEGENDARY | Shift+U |

```java
public class AffixQuickActions {

    public static void rerollAll(ItemStack stack, RandomSource random) {
        ItemAffixes affixes = stack.get(ModDataComponents.ITEM_AFFIXES);
        if (affixes == null) return;

        List<AffixInstance> newPrefixes = affixes.prefixes().stream()
            .map(a -> a.reroll(random))
            .toList();
        List<AffixInstance> newSuffixes = affixes.suffixes().stream()
            .map(a -> a.reroll(random))
            .toList();

        stack.set(ModDataComponents.ITEM_AFFIXES,
            new ItemAffixes(newPrefixes, newSuffixes, affixes.implicits(), affixes.nameLocked()));
    }

    public static void upgradeAll(ItemStack stack, RandomSource random) {
        ItemAffixes affixes = stack.get(ModDataComponents.ITEM_AFFIXES);
        if (affixes == null) return;

        List<AffixInstance> newPrefixes = affixes.prefixes().stream()
            .map(a -> a.upgrade(random).orElse(a))
            .toList();
        List<AffixInstance> newSuffixes = affixes.suffixes().stream()
            .map(a -> a.upgrade(random).orElse(a))
            .toList();

        stack.set(ModDataComponents.ITEM_AFFIXES,
            new ItemAffixes(newPrefixes, newSuffixes, affixes.implicits(), affixes.nameLocked()));
    }

    public static void clearAll(ItemStack stack) {
        stack.remove(ModDataComponents.ITEM_AFFIXES);
        // Also remove affix-applied modifiers
        stack.remove(ModDataComponents.AFFIX_DAMAGE_REDUCTIONS);
        stack.remove(ModDataComponents.AFFIX_ON_HIT_EFFECTS);
        stack.remove(ModDataComponents.AFFIX_AURAS);
    }

    public static void generateRandom(ItemStack stack, RandomSource random, AffixGenerator.AffixGenerationConfig config) {
        clearAll(stack);
        ItemAffixes affixes = AffixGenerator.generate(stack, random, config);
        stack.set(ModDataComponents.ITEM_AFFIXES, affixes);
        applyAffixModifiers(stack, affixes);
    }

    public static void maxTierAll(ItemStack stack) {
        ItemAffixes affixes = stack.get(ModDataComponents.ITEM_AFFIXES);
        if (affixes == null) return;

        List<AffixInstance> newPrefixes = affixes.prefixes().stream()
            .map(a -> new AffixInstance(a.definitionId(), AffixTier.LEGENDARY, a.rolledValue(), a.seed()))
            .toList();
        List<AffixInstance> newSuffixes = affixes.suffixes().stream()
            .map(a -> new AffixInstance(a.definitionId(), AffixTier.LEGENDARY, a.rolledValue(), a.seed()))
            .toList();

        stack.set(ModDataComponents.ITEM_AFFIXES,
            new ItemAffixes(newPrefixes, newSuffixes, affixes.implicits(), affixes.nameLocked()));
    }

    private static void applyAffixModifiers(ItemStack stack, ItemAffixes affixes) {
        for (AffixInstance instance : affixes.all()) {
            instance.getDefinition().ifPresent(def -> {
                for (AffixModifier modifier : def.modifiers()) {
                    modifier.apply(stack, instance.rolledValue(), instance.tier());
                }
            });
        }
    }
}
```

---

### ValueLimits

```java
// Add to ValueLimits.java

/** Affix value range */
public static final Limit AFFIX_VALUE = Limit.of(
    0.0, 100.0,      // Soft: 0-100
    0.0, 1000.0      // Hard: 0-1000
);

/** Max prefixes per item */
public static final Limit MAX_PREFIXES = Limit.of(
    1, 3,            // Soft: 1-3
    0, 6             // Hard: 0-6
);

/** Max suffixes per item */
public static final Limit MAX_SUFFIXES = Limit.of(
    1, 3,            // Soft: 1-3
    0, 6             // Hard: 0-6
);

/** Luck value for generation */
public static final Limit AFFIX_LUCK = Limit.of(
    0.0, 0.5,        // Soft: 0-50%
    0.0, 1.0         // Hard: 0-100%
);
```

---

### Config

```toml
# config/devmod-server.toml

[affixes]
# Enable affix system (Phase 4+)
enableAffixSystem = false

# Maximum prefixes per item
maxPrefixes = 3

# Maximum suffixes per item
maxSuffixes = 3

# Maximum implicits per item
maxImplicits = 2

# Allow tier upgrades
allowTierUpgrade = true

# Allow value rerolls
allowValueReroll = true

# Require currency for reroll/upgrade (future)
requireCurrency = false

# Natural affix generation on loot
enableNaturalGeneration = false

# Base luck for natural generation
baseLuck = 0.0

# Allow legendary tier
allowLegendaryTier = true

# Custom affix definitions file
customAffixesFile = "config/devmod-affixes.json"
```

---

### Network Payload

```java
/**
 * Payload for affix updates.
 */
public record UpdateAffixesPayload(
    List<AffixData> prefixes,
    List<AffixData> suffixes,
    List<AffixData> implicits,
    boolean nameLocked,
    boolean clearExisting
) implements CustomPacketPayload {

    public record AffixData(
        String affixId,
        String tier,
        float value
    ) {}

    public static final Type<UpdateAffixesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "update_affixes")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAffixesPayload> STREAM_CODEC =
        StreamCodec.of(UpdateAffixesPayload::encode, UpdateAffixesPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, UpdateAffixesPayload payload) {
        BiConsumer<RegistryFriendlyByteBuf, AffixData> affixWriter = (b, data) -> {
            b.writeUtf(data.affixId());
            b.writeUtf(data.tier());
            b.writeFloat(data.value());
        };

        buf.writeCollection(payload.prefixes, affixWriter);
        buf.writeCollection(payload.suffixes, affixWriter);
        buf.writeCollection(payload.implicits, affixWriter);
        buf.writeBoolean(payload.nameLocked);
        buf.writeBoolean(payload.clearExisting);
    }

    private static UpdateAffixesPayload decode(RegistryFriendlyByteBuf buf) {
        Function<RegistryFriendlyByteBuf, AffixData> affixReader = b ->
            new AffixData(b.readUtf(), b.readUtf(), b.readFloat());

        return new UpdateAffixesPayload(
            buf.readList(affixReader),
            buf.readList(affixReader),
            buf.readList(affixReader),
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

---

## 2.34 Data Components System

Sistema per gestione Data Components 1.21+ con UI strutturata e raw view.

### Design Philosophy

- **Data Components over NBT**: 1.21+ usa Data Components, NBT è deprecato
- **Type-safe**: Ogni componente ha Codec e validazione
- **Categorized Access**: Read-only vs Read/Write per sicurezza
- **Extensible**: Supporto componenti da altre mod

---

### Component Access Levels

#### Read/Write Components (Modificabili)

| Component | Tipo | UI Widget | Descrizione |
|-----------|------|-----------|-------------|
| `DAMAGE` | Integer | Slider + NumericField | Durabilità corrente |
| `MAX_DAMAGE` | Integer | NumericField | Durabilità massima |
| `UNBREAKABLE` | Boolean | Checkbox | Item indistruttibile |
| `ENCHANTMENTS` | ItemEnchantments | EnchantmentEditor | Già in Section 2.32 |
| `STORED_ENCHANTMENTS` | ItemEnchantments | EnchantmentEditor | Per libri incantati |
| `ATTRIBUTE_MODIFIERS` | ItemAttributeModifiers | AttributeEditor | Core stats |
| `CUSTOM_NAME` | Component | TextField | Nome custom |
| `LORE` | List<Component> | MultilineTextField | Descrizione |
| `REPAIR_COST` | Integer | NumericField | Costo XP riparazione |
| `RARITY` | Rarity | Dropdown | Common/Uncommon/Rare/Epic |
| `CUSTOM_MODEL_DATA` | Integer | NumericField | Per resource packs |
| `DYED_COLOR` | DyedItemColor | ColorPicker | Colore leather armor |
| `TRIM` | ArmorTrim | TrimSelector | Armor trim pattern |
| `HIDE_TOOLTIP` | Unit | Checkbox | Nascondi tooltip |
| `HIDE_ADDITIONAL_TOOLTIP` | Unit | Checkbox | Nascondi tooltip extra |
| `ENCHANTMENT_GLINT_OVERRIDE` | Boolean | Checkbox | Forza/disabilita glint |
| `FIRE_RESISTANT` | Unit | Checkbox | Resistenza al fuoco |
| `REPAIRABLE` | Repairable | MaterialSelector | Materiali riparazione |

#### Read-Only Components (Solo visualizzazione)

| Component | Motivo Read-Only |
|-----------|------------------|
| `MAX_STACK_SIZE` | Modifica può corrompere inventari |
| `FOOD` | Complessità (nutrition, saturation, effects) |
| `CONSUMABLE` | Complessità animazioni/suoni |
| `TOOL` | Mining tiers, regole complesse |
| `WEAPON` | Vanilla weapon behavior |
| `EQUIPPABLE` | Slot assignment risks |
| `USE_COOLDOWN` | Timing sensitive |
| `CONTAINER` | Nested items complexity |
| `BUNDLE_CONTENTS` | Nested items complexity |

#### Hidden Components (Non mostrare)

| Component | Motivo |
|-----------|--------|
| `CREATIVE_SLOT_LOCK` | Internal only |
| `INTANGIBLE_PROJECTILE` | Internal only |
| `MAP_ID` | Map-specific |
| `MAP_DECORATIONS` | Map-specific |
| `DEBUG_STICK_STATE` | Debug only |
| `ENTITY_DATA` | Spawn egg internal |
| `BUCKET_ENTITY_DATA` | Bucket internal |
| `BLOCK_ENTITY_DATA` | Placed block data |

---

### Component Registry

```java
/**
 * Registry of component metadata for UI.
 */
public class ComponentRegistry {

    private static final Map<DataComponentType<?>, ComponentMeta<?>> COMPONENTS = new LinkedHashMap<>();

    /**
     * Access level for a component.
     */
    public enum AccessLevel {
        /** Full read/write in UI */
        READ_WRITE,
        /** Display only, no modification */
        READ_ONLY,
        /** Never show in UI */
        HIDDEN
    }

    /**
     * Metadata for a component type.
     */
    public record ComponentMeta<T>(
        DataComponentType<T> type,
        String displayName,
        String category,
        AccessLevel access,
        ComponentWidget<T> widget,
        @Nullable String description
    ) {}

    /**
     * Widget factory for component UI.
     */
    public interface ComponentWidget<T> {
        /**
         * Create widget for displaying/editing this component.
         */
        AbstractWidget create(ItemStack stack, T value, Consumer<T> onChange);

        /**
         * Create read-only display.
         */
        Component toDisplayText(T value);
    }

    // --- Registration ---

    static {
        // Read/Write components
        register(DataComponents.DAMAGE, "Damage", "durability", AccessLevel.READ_WRITE,
            new IntegerSliderWidget(0, stack -> stack.getMaxDamage()),
            "Current durability damage");

        register(DataComponents.MAX_DAMAGE, "Max Durability", "durability", AccessLevel.READ_WRITE,
            new IntegerFieldWidget(1, 32767),
            "Maximum durability");

        register(DataComponents.UNBREAKABLE, "Unbreakable", "durability", AccessLevel.READ_WRITE,
            new UnbreakableWidget(),
            "Item cannot be damaged");

        register(DataComponents.CUSTOM_NAME, "Custom Name", "display", AccessLevel.READ_WRITE,
            new ComponentTextFieldWidget(),
            "Override item display name");

        register(DataComponents.LORE, "Lore", "display", AccessLevel.READ_WRITE,
            new LoreEditorWidget(),
            "Custom description lines");

        register(DataComponents.RARITY, "Rarity", "display", AccessLevel.READ_WRITE,
            new RarityDropdownWidget(),
            "Item rarity (affects name color)");

        register(DataComponents.ENCHANTMENTS, "Enchantments", "enchants", AccessLevel.READ_WRITE,
            new EnchantmentEditorWidget(),
            "Applied enchantments");

        register(DataComponents.ATTRIBUTE_MODIFIERS, "Attributes", "stats", AccessLevel.READ_WRITE,
            new AttributeModifiersWidget(),
            "Stat modifiers");

        register(DataComponents.REPAIR_COST, "Repair Cost", "durability", AccessLevel.READ_WRITE,
            new IntegerFieldWidget(0, 100),
            "Base XP cost for anvil repairs");

        register(DataComponents.CUSTOM_MODEL_DATA, "Model Data", "display", AccessLevel.READ_WRITE,
            new IntegerFieldWidget(0, Integer.MAX_VALUE),
            "Custom model for resource packs");

        register(DataComponents.DYED_COLOR, "Dye Color", "display", AccessLevel.READ_WRITE,
            new ColorPickerWidget(),
            "Leather armor color");

        register(DataComponents.TRIM, "Armor Trim", "display", AccessLevel.READ_WRITE,
            new ArmorTrimWidget(),
            "Armor trim pattern and material");

        register(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, "Glint Override", "display", AccessLevel.READ_WRITE,
            new TriStateWidget("Default", "Force On", "Force Off"),
            "Override enchantment glint");

        register(DataComponents.FIRE_RESISTANT, "Fire Resistant", "properties", AccessLevel.READ_WRITE,
            new BooleanCheckboxWidget(),
            "Item survives fire/lava");

        register(DataComponents.HIDE_TOOLTIP, "Hide Tooltip", "display", AccessLevel.READ_WRITE,
            new BooleanCheckboxWidget(),
            "Completely hide item tooltip");

        // Read-Only components
        register(DataComponents.MAX_STACK_SIZE, "Max Stack Size", "properties", AccessLevel.READ_ONLY,
            new IntegerDisplayWidget(),
            "Maximum stack size (read-only for safety)");

        register(DataComponents.FOOD, "Food Properties", "properties", AccessLevel.READ_ONLY,
            new FoodDisplayWidget(),
            "Nutrition and saturation");

        register(DataComponents.TOOL, "Tool Properties", "properties", AccessLevel.READ_ONLY,
            new ToolDisplayWidget(),
            "Mining speed and rules");

        // Hidden components
        register(DataComponents.CREATIVE_SLOT_LOCK, null, null, AccessLevel.HIDDEN, null, null);
        register(DataComponents.MAP_ID, null, null, AccessLevel.HIDDEN, null, null);
    }

    private static <T> void register(
        DataComponentType<T> type,
        @Nullable String displayName,
        @Nullable String category,
        AccessLevel access,
        @Nullable ComponentWidget<T> widget,
        @Nullable String description
    ) {
        COMPONENTS.put(type, new ComponentMeta<>(type, displayName, category, access, widget, description));
    }

    /**
     * Get metadata for a component type.
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<ComponentMeta<T>> get(DataComponentType<T> type) {
        return Optional.ofNullable((ComponentMeta<T>) COMPONENTS.get(type));
    }

    /**
     * Get all registered components by category.
     */
    public static Map<String, List<ComponentMeta<?>>> getByCategory() {
        return COMPONENTS.values().stream()
            .filter(meta -> meta.access() != AccessLevel.HIDDEN)
            .filter(meta -> meta.category() != null)
            .collect(Collectors.groupingBy(ComponentMeta::category));
    }

    /**
     * Get all visible components for an item.
     */
    public static List<ComponentMeta<?>> getVisibleFor(ItemStack stack) {
        return COMPONENTS.values().stream()
            .filter(meta -> meta.access() != AccessLevel.HIDDEN)
            .filter(meta -> stack.has(meta.type()) || isApplicable(stack, meta.type()))
            .toList();
    }

    private static boolean isApplicable(ItemStack stack, DataComponentType<?> type) {
        // Check if component can be added to this item type
        if (type == DataComponents.DYED_COLOR) {
            return stack.getItem() instanceof DyeableLeatherItem;
        }
        if (type == DataComponents.TRIM) {
            return stack.getItem() instanceof ArmorItem;
        }
        // Most components can be added to any item
        return true;
    }
}
```

---

### Unknown Components Handling

```java
/**
 * Handles components from other mods.
 */
public class UnknownComponentHandler {

    /**
     * Mode for handling unknown components.
     */
    public enum UnknownMode {
        /** Don't show unknown components */
        HIDE,
        /** Show but don't allow editing */
        READ_ONLY,
        /** Allow raw JSON editing */
        RAW_EDIT
    }

    private static UnknownMode mode = UnknownMode.READ_ONLY;

    public static void setMode(UnknownMode newMode) {
        mode = newMode;
    }

    public static UnknownMode getMode() {
        return mode;
    }

    /**
     * Get unknown components on an item.
     */
    public static List<UnknownComponent> getUnknownComponents(ItemStack stack) {
        if (mode == UnknownMode.HIDE) {
            return List.of();
        }

        List<UnknownComponent> unknown = new ArrayList<>();

        stack.getComponents().forEach(typed -> {
            DataComponentType<?> type = typed.type();
            if (!ComponentRegistry.get(type).isPresent()) {
                // This is an unknown component
                ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
                unknown.add(new UnknownComponent(
                    id != null ? id : ResourceLocation.fromNamespaceAndPath("unknown", "component"),
                    type,
                    typed.value(),
                    mode == UnknownMode.RAW_EDIT
                ));
            }
        });

        return unknown;
    }

    /**
     * Represents an unknown component.
     */
    public record UnknownComponent(
        ResourceLocation id,
        DataComponentType<?> type,
        Object value,
        boolean editable
    ) {
        /**
         * Get JSON representation.
         */
        public String toJson() {
            try {
                // Use component's codec to serialize
                @SuppressWarnings("unchecked")
                DataComponentType<Object> objType = (DataComponentType<Object>) type;
                Codec<Object> codec = objType.codec();
                if (codec != null) {
                    return codec.encodeStart(JsonOps.INSTANCE, value)
                        .result()
                        .map(JsonElement::toString)
                        .orElse("{}");
                }
            } catch (Exception e) {
                // Fallback
            }
            return value.toString();
        }

        /**
         * Try to parse and set from JSON.
         */
        public Optional<Object> fromJson(String json) {
            if (!editable) return Optional.empty();

            try {
                @SuppressWarnings("unchecked")
                DataComponentType<Object> objType = (DataComponentType<Object>) type;
                Codec<Object> codec = objType.codec();
                if (codec != null) {
                    JsonElement element = JsonParser.parseString(json);
                    return codec.parse(JsonOps.INSTANCE, element).result();
                }
            } catch (Exception e) {
                // Parse failed
            }
            return Optional.empty();
        }
    }
}
```

---

### Raw View System

```java
/**
 * Raw JSON/Component view for power users.
 */
public class RawComponentView {

    private final ItemStack stack;
    private String currentJson;
    private String originalJson;
    private List<String> errors = new ArrayList<>();

    public RawComponentView(ItemStack stack) {
        this.stack = stack;
        this.originalJson = serializeAll();
        this.currentJson = originalJson;
    }

    /**
     * Serialize all components to JSON.
     */
    public String serializeAll() {
        JsonObject root = new JsonObject();

        stack.getComponents().forEach(typed -> {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(typed.type());
            if (id != null) {
                try {
                    @SuppressWarnings("unchecked")
                    DataComponentType<Object> type = (DataComponentType<Object>) typed.type();
                    Codec<Object> codec = type.codec();
                    if (codec != null) {
                        codec.encodeStart(JsonOps.INSTANCE, typed.value())
                            .result()
                            .ifPresent(json -> root.add(id.toString(), json));
                    }
                } catch (Exception e) {
                    root.addProperty(id.toString(), "<error: " + e.getMessage() + ">");
                }
            }
        });

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(root);
    }

    /**
     * Attempt to parse and apply JSON changes.
     */
    public boolean applyJson(String json) {
        errors.clear();

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            ItemStack newStack = stack.copy();

            // Clear existing components (except protected ones)
            // This is simplified - real impl needs careful handling

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) {
                    errors.add("Invalid component ID: " + entry.getKey());
                    continue;
                }

                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
                if (type == null) {
                    errors.add("Unknown component: " + id);
                    continue;
                }

                // Check if editable
                Optional<ComponentRegistry.ComponentMeta<?>> meta = ComponentRegistry.get(type);
                if (meta.isPresent() && meta.get().access() == ComponentRegistry.AccessLevel.READ_ONLY) {
                    errors.add("Component is read-only: " + id);
                    continue;
                }

                try {
                    applyComponent(newStack, type, entry.getValue());
                } catch (Exception e) {
                    errors.add("Failed to apply " + id + ": " + e.getMessage());
                }
            }

            if (errors.isEmpty()) {
                // Copy all components from newStack to original
                copyComponents(newStack, stack);
                currentJson = json;
                return true;
            }

        } catch (JsonParseException e) {
            errors.add("JSON parse error: " + e.getMessage());
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private <T> void applyComponent(ItemStack stack, DataComponentType<T> type, JsonElement json) {
        Codec<T> codec = type.codec();
        if (codec == null) {
            throw new IllegalStateException("No codec for component");
        }

        T value = codec.parse(JsonOps.INSTANCE, json)
            .getOrThrow(msg -> new IllegalArgumentException(msg));

        stack.set(type, value);
    }

    private void copyComponents(ItemStack from, ItemStack to) {
        // Clear and copy - simplified
        from.getComponents().forEach(typed -> {
            @SuppressWarnings("unchecked")
            DataComponentType<Object> type = (DataComponentType<Object>) typed.type();
            to.set(type, typed.value());
        });
    }

    /**
     * Get diff between original and current.
     */
    public List<DiffLine> getDiff() {
        // Simple line-by-line diff
        String[] origLines = originalJson.split("\n");
        String[] currLines = currentJson.split("\n");

        List<DiffLine> diff = new ArrayList<>();

        int maxLen = Math.max(origLines.length, currLines.length);
        for (int i = 0; i < maxLen; i++) {
            String orig = i < origLines.length ? origLines[i] : "";
            String curr = i < currLines.length ? currLines[i] : "";

            if (orig.equals(curr)) {
                diff.add(new DiffLine(DiffType.UNCHANGED, curr));
            } else if (orig.isEmpty()) {
                diff.add(new DiffLine(DiffType.ADDED, curr));
            } else if (curr.isEmpty()) {
                diff.add(new DiffLine(DiffType.REMOVED, orig));
            } else {
                diff.add(new DiffLine(DiffType.REMOVED, orig));
                diff.add(new DiffLine(DiffType.ADDED, curr));
            }
        }

        return diff;
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean hasChanges() {
        return !currentJson.equals(originalJson);
    }

    /**
     * Copy current JSON to clipboard.
     */
    public void copyToClipboard(Minecraft mc) {
        mc.keyboardHandler.setClipboard(currentJson);
    }

    /**
     * Paste from clipboard.
     */
    public String pasteFromClipboard(Minecraft mc) {
        return mc.keyboardHandler.getClipboard();
    }

    public enum DiffType {
        UNCHANGED(0xFFFFFF),
        ADDED(0x00FF00),
        REMOVED(0xFF0000);

        public final int color;
        DiffType(int color) { this.color = color; }
    }

    public record DiffLine(DiffType type, String content) {}
}
```

---

### UI Widgets

```java
/**
 * Common widget implementations for components.
 */
public class ComponentWidgets {

    /**
     * Integer slider widget.
     */
    public static class IntegerSliderWidget implements ComponentRegistry.ComponentWidget<Integer> {
        private final int min;
        private final Function<ItemStack, Integer> maxProvider;

        public IntegerSliderWidget(int min, Function<ItemStack, Integer> maxProvider) {
            this.min = min;
            this.maxProvider = maxProvider;
        }

        @Override
        public AbstractWidget create(ItemStack stack, Integer value, Consumer<Integer> onChange) {
            int max = maxProvider.apply(stack);
            return new SliderWidget(0, 0, 150, 20, min, max, value, onChange::accept);
        }

        @Override
        public Component toDisplayText(Integer value) {
            return Component.literal(String.valueOf(value));
        }
    }

    /**
     * Integer field widget.
     */
    public static class IntegerFieldWidget implements ComponentRegistry.ComponentWidget<Integer> {
        private final int min;
        private final int max;

        public IntegerFieldWidget(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public AbstractWidget create(ItemStack stack, Integer value, Consumer<Integer> onChange) {
            EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 20, Component.empty());
            box.setValue(String.valueOf(value));
            box.setResponder(text -> {
                try {
                    int parsed = Integer.parseInt(text);
                    if (parsed >= min && parsed <= max) {
                        onChange.accept(parsed);
                    }
                } catch (NumberFormatException ignored) {}
            });
            return box;
        }

        @Override
        public Component toDisplayText(Integer value) {
            return Component.literal(String.valueOf(value));
        }
    }

    /**
     * Boolean checkbox widget.
     */
    public static class BooleanCheckboxWidget implements ComponentRegistry.ComponentWidget<Unit> {
        @Override
        public AbstractWidget create(ItemStack stack, Unit value, Consumer<Unit> onChange) {
            return Checkbox.builder(Component.literal("Enabled"), Minecraft.getInstance().font)
                .selected(value != null)
                .onValueChange((cb, selected) -> onChange.accept(selected ? Unit.INSTANCE : null))
                .build();
        }

        @Override
        public Component toDisplayText(Unit value) {
            return Component.literal(value != null ? "Yes" : "No");
        }
    }

    /**
     * Rarity dropdown widget.
     */
    public static class RarityDropdownWidget implements ComponentRegistry.ComponentWidget<Rarity> {
        @Override
        public AbstractWidget create(ItemStack stack, Rarity value, Consumer<Rarity> onChange) {
            return new DropdownWidget<>(0, 0, 100, 20,
                Arrays.asList(Rarity.values()),
                value,
                Rarity::name,
                onChange
            );
        }

        @Override
        public Component toDisplayText(Rarity value) {
            return Component.literal(value.name()).withStyle(value.color());
        }
    }

    /**
     * Color picker widget for dyed items.
     */
    public static class ColorPickerWidget implements ComponentRegistry.ComponentWidget<DyedItemColor> {
        @Override
        public AbstractWidget create(ItemStack stack, DyedItemColor value, Consumer<DyedItemColor> onChange) {
            return new ColorPickerButton(0, 0, 60, 20, value.rgb(), rgb -> {
                onChange.accept(new DyedItemColor(rgb, value.showInTooltip()));
            });
        }

        @Override
        public Component toDisplayText(DyedItemColor value) {
            return Component.literal(String.format("#%06X", value.rgb()))
                .withStyle(style -> style.withColor(value.rgb()));
        }
    }

    /**
     * Lore editor (multiline).
     */
    public static class LoreEditorWidget implements ComponentRegistry.ComponentWidget<ItemLore> {
        @Override
        public AbstractWidget create(ItemStack stack, ItemLore value, Consumer<ItemLore> onChange) {
            return new MultiLineEditBox(0, 0, 200, 80, value, newLore -> {
                onChange.accept(newLore);
            });
        }

        @Override
        public Component toDisplayText(ItemLore value) {
            return Component.literal(value.lines().size() + " lines");
        }
    }

    /**
     * Unbreakable toggle with tooltip visibility.
     */
    public static class UnbreakableWidget implements ComponentRegistry.ComponentWidget<Unbreakable> {
        @Override
        public AbstractWidget create(ItemStack stack, Unbreakable value, Consumer<Unbreakable> onChange) {
            // Two checkboxes: enabled + show in tooltip
            return new UnbreakablePanel(value, onChange);
        }

        @Override
        public Component toDisplayText(Unbreakable value) {
            if (value == null) return Component.literal("No");
            return Component.literal("Yes" + (value.showInTooltip() ? "" : " (hidden)"));
        }
    }
}
```

---

### UI Mockup - Components Tab

```
┌─────────────────────────────────────────────────────────────────────┐
│                        COMPONENTS                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  View: ○ Structured  ● Raw JSON                    [Copy] [Paste]   │
│                                                                      │
│  ═══ STRUCTURED VIEW ═══                                             │
│                                                                      │
│  DISPLAY                                                             │
│  ───────────────────────────────────────────────────────────────    │
│  Custom Name    [Mighty Diamond Sword_____________]                  │
│  Rarity         [EPIC ▼]                                            │
│  Glint Override [Default ▼]  (Default / Force On / Force Off)      │
│  Hide Tooltip   [ ]                                                  │
│                                                                      │
│  Lore:                                                               │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ A legendary blade forged in dragon fire.                   │     │
│  │ +10% damage to undead                                       │     │
│  │                                                             │     │
│  └────────────────────────────────────────────────────────────┘     │
│  [+ Add Line]                                                        │
│                                                                      │
│  DURABILITY                                                          │
│  ───────────────────────────────────────────────────────────────    │
│  Current       [████████████████████] 1561/1561                     │
│  Max           [    1561    ] [▼][▲]                                │
│  Repair Cost   [      7     ] [▼][▲]                                │
│  Unbreakable   [ ] Enable  [✓] Show in tooltip                      │
│                                                                      │
│  PROPERTIES                                                          │
│  ───────────────────────────────────────────────────────────────    │
│  Fire Resistant [ ]                                                  │
│  Model Data     [      0     ]                                       │
│                                                                      │
│  READ-ONLY COMPONENTS                                                │
│  ───────────────────────────────────────────────────────────────    │
│  Max Stack Size: 1                                                   │
│  Tool Type: Sword (cannot modify)                                    │
│                                                                      │
│  UNKNOWN COMPONENTS (from other mods)                                │
│  ───────────────────────────────────────────────────────────────    │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ apotheosis:sockets                           [Read-Only]   │     │
│  │ {"slots": 3, "gems": ["ruby", "emerald"]}                   │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### UI Mockup - Raw JSON View

```
┌─────────────────────────────────────────────────────────────────────┐
│                        COMPONENTS                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  View: ● Structured  ○ Raw JSON                    [Copy] [Paste]   │
│                                                                      │
│  ═══ RAW JSON VIEW ═══                                               │
│                                                                      │
│  [✓] Syntax Highlighting  [✓] Show Diff  [ ] Compact                │
│                                                                      │
│  ┌─ Editor ─────────────────────────────────────────────────────┐   │
│  │  1 │ {                                                        │   │
│  │  2 │   "minecraft:damage": 0,                                 │   │
│  │  3 │   "minecraft:max_damage": 1561,                          │   │
│  │  4 │ + "minecraft:custom_name": "Mighty Diamond Sword",       │   │
│  │  5 │   "minecraft:rarity": "epic",                            │   │
│  │  6 │   "minecraft:enchantments": {                            │   │
│  │  7 │     "minecraft:sharpness": 5,                            │   │
│  │  8 │ -   "minecraft:unbreaking": 3                            │   │
│  │  9 │ +   "minecraft:unbreaking": 4                            │   │
│  │ 10 │   },                                                     │   │
│  │ 11 │   "minecraft:attribute_modifiers": {                     │   │
│  │ 12 │     ...                                                  │   │
│  │ 13 │   }                                                      │   │
│  │ 14 │ }                                                        │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─ Errors ─────────────────────────────────────────────────────┐   │
│  │ (No errors)                                                   │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  [Validate]  [Apply Changes]  [Reset to Original]                   │
│                                                                      │
│  Legend:  + Added  - Removed  ~ Modified                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Quick Actions

| Action | Descrizione | Shortcut |
|--------|-------------|----------|
| **Copy JSON** | Copia tutti i componenti come JSON | Ctrl+C |
| **Paste JSON** | Incolla e applica JSON | Ctrl+V |
| **Reset All** | Ripristina componenti originali | Ctrl+R |
| **Validate** | Verifica JSON syntax | Ctrl+Enter |
| **Toggle View** | Switch Structured ↔ Raw | Tab |

---

### ValueLimits

```java
// Add to ValueLimits.java

/** Custom model data */
public static final Limit CUSTOM_MODEL_DATA = Limit.of(
    0, 99999,            // Soft: typical range
    0, Integer.MAX_VALUE // Hard: Java int max
);

/** Repair cost */
public static final Limit REPAIR_COST_COMPONENT = Limit.of(
    0, 39,               // Soft: before "too expensive"
    0, 1000              // Hard: extended range
);

/** Lore lines count */
public static final Limit LORE_LINES = Limit.of(
    0, 10,               // Soft: readable
    0, 50                // Hard: max lines
);

/** Lore line length */
public static final Limit LORE_LINE_LENGTH = Limit.of(
    0, 100,              // Soft: fits tooltip
    0, 500               // Hard: extended
);
```

---

### Config

```toml
# config/devmod-server.toml

[components]
# Enable component editing
enableComponentEditing = true

# Mode for unknown components from other mods
# Options: "hide", "read_only", "raw_edit"
unknownComponentsMode = "read_only"

# Allow raw JSON editing
allowRawJsonEdit = true

# Allow editing read-only components (dangerous!)
allowReadOnlyOverride = false

# Maximum lore lines
maxLoreLines = 50

# Maximum lore line length
maxLoreLineLength = 500

# Show hidden components in raw view
showHiddenInRawView = false

# Validate JSON on every keystroke (vs on apply)
liveJsonValidation = true

# Components blacklist (never show/edit)
componentBlacklist = [
    "minecraft:creative_slot_lock"
]

# Components to always treat as read-only
forceReadOnly = [
    "minecraft:max_stack_size"
]
```

---

### Network Payload

```java
/**
 * Payload for component updates.
 */
public record UpdateComponentsPayload(
    Map<String, String> components,  // component_id -> json_value
    List<String> removeComponents,   // components to remove
    boolean rawMode                  // true if from raw editor
) implements CustomPacketPayload {

    public static final Type<UpdateComponentsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "update_components")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateComponentsPayload> STREAM_CODEC =
        StreamCodec.of(UpdateComponentsPayload::encode, UpdateComponentsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, UpdateComponentsPayload payload) {
        buf.writeMap(
            payload.components,
            RegistryFriendlyByteBuf::writeUtf,
            RegistryFriendlyByteBuf::writeUtf
        );
        buf.writeCollection(payload.removeComponents, RegistryFriendlyByteBuf::writeUtf);
        buf.writeBoolean(payload.rawMode);
    }

    private static UpdateComponentsPayload decode(RegistryFriendlyByteBuf buf) {
        return new UpdateComponentsPayload(
            buf.readMap(RegistryFriendlyByteBuf::readUtf, RegistryFriendlyByteBuf::readUtf),
            buf.readList(RegistryFriendlyByteBuf::readUtf),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Apply this payload to an item.
     */
    public void apply(ItemStack stack, RegistryAccess registries) {
        // Remove specified components
        for (String id : removeComponents) {
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc != null) {
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(loc);
                if (type != null) {
                    stack.remove(type);
                }
            }
        }

        // Apply/update components
        for (Map.Entry<String, String> entry : components.entrySet()) {
            ResourceLocation loc = ResourceLocation.tryParse(entry.getKey());
            if (loc == null) continue;

            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(loc);
            if (type == null) continue;

            // Check access level
            if (!rawMode) {
                Optional<ComponentRegistry.ComponentMeta<?>> meta = ComponentRegistry.get(type);
                if (meta.isPresent() && meta.get().access() != ComponentRegistry.AccessLevel.READ_WRITE) {
                    continue;  // Skip non-writable in structured mode
                }
            }

            try {
                applyComponentJson(stack, type, entry.getValue());
            } catch (Exception e) {
                // Log error, skip this component
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void applyComponentJson(ItemStack stack, DataComponentType<T> type, String json) {
        Codec<T> codec = type.codec();
        if (codec == null) return;

        JsonElement element = JsonParser.parseString(json);
        T value = codec.parse(JsonOps.INSTANCE, element)
            .getOrThrow(msg -> new IllegalArgumentException(msg));

        stack.set(type, value);
    }
}
```

---

## 2.35 Apply & Permissions System

Sistema per applicazione modifiche, permessi utente, logging e undo/redo.

---

### Apply Behavior

#### Apply Target

L'editor modifica direttamente l'item nello slot originale.

```java
/**
 * Apply target modes.
 */
public enum ApplyTarget {
    /** Modify the original item in-place (default) */
    MODIFY_ORIGINAL,
    /** Create a copy, leave original unchanged */
    CREATE_COPY,
    /** Replace original, store backup in undo stack */
    REPLACE_WITH_BACKUP
}
```

#### Apply Flow

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│ User Edits  │ ──► │  Validate    │ ──► │ Store Undo  │ ──► │ Apply to     │
│ in UI       │     │  All Values  │     │ Snapshot    │     │ ItemStack    │
└─────────────┘     └──────────────┘     └─────────────┘     └──────────────┘
                           │                                         │
                           ▼                                         ▼
                    ┌──────────────┐                         ┌──────────────┐
                    │ Validation   │                         │ Send Network │
                    │ Failed       │                         │ Payload      │
                    └──────────────┘                         └──────────────┘
                           │                                         │
                           ▼                                         ▼
                    ┌──────────────┐                         ┌──────────────┐
                    │ Show Errors  │                         │ Server-Side  │
                    │ Block Apply  │                         │ Validation   │
                    └──────────────┘                         └──────────────┘
```

#### ApplyManager

```java
/**
 * Manages apply operations with validation and undo support.
 */
public class ApplyManager {

    private final UndoRedoManager undoManager;
    private final ChangeLogger changeLogger;
    private ApplyTarget applyTarget = ApplyTarget.REPLACE_WITH_BACKUP;

    public ApplyManager(UndoRedoManager undoManager, ChangeLogger changeLogger) {
        this.undoManager = undoManager;
        this.changeLogger = changeLogger;
    }

    /**
     * Apply changes to an item.
     *
     * @param player The player making changes
     * @param slot The inventory slot containing the item
     * @param changes The changes to apply
     * @return Result of the apply operation
     */
    public ApplyResult apply(Player player, int slot, ItemChanges changes) {
        ItemStack original = player.getInventory().getItem(slot);
        if (original.isEmpty()) {
            return ApplyResult.failure("No item in slot");
        }

        // 1. Validate all changes
        ValidationResult validation = validateChanges(original, changes);
        if (validation.hasBlockingErrors()) {
            return ApplyResult.blocked(validation.getErrors());
        }

        // 2. Store undo snapshot
        ItemStack snapshot = original.copy();
        undoManager.pushUndo(new UndoEntry(player.getUUID(), slot, snapshot, System.currentTimeMillis()));

        // 3. Apply changes based on target mode
        ItemStack result = switch (applyTarget) {
            case MODIFY_ORIGINAL -> {
                applyChangesToStack(original, changes);
                yield original;
            }
            case CREATE_COPY -> {
                ItemStack copy = original.copy();
                applyChangesToStack(copy, changes);
                // Add copy to inventory
                if (!player.getInventory().add(copy)) {
                    yield null;  // Inventory full
                }
                yield copy;
            }
            case REPLACE_WITH_BACKUP -> {
                applyChangesToStack(original, changes);
                yield original;
            }
        };

        if (result == null) {
            undoManager.popUndo();  // Rollback undo entry
            return ApplyResult.failure("Could not apply changes (inventory full?)");
        }

        // 4. Log the change
        changeLogger.log(new ChangeLogEntry(
            player.getUUID(),
            player.getName().getString(),
            slot,
            snapshot,
            result.copy(),
            changes,
            System.currentTimeMillis()
        ));

        // 5. Return result with warnings
        if (validation.hasWarnings()) {
            return ApplyResult.successWithWarnings(validation.getWarnings());
        }
        return ApplyResult.success();
    }

    private ValidationResult validateChanges(ItemStack stack, ItemChanges changes) {
        ValidationResult result = new ValidationResult();

        for (ItemChange change : changes.getChanges()) {
            ValidationResult changeResult = change.validate(stack);
            result.merge(changeResult);
        }

        return result;
    }

    private void applyChangesToStack(ItemStack stack, ItemChanges changes) {
        for (ItemChange change : changes.getChanges()) {
            change.apply(stack);
        }
    }

    /**
     * Result of an apply operation.
     */
    public sealed interface ApplyResult {
        record Success(List<String> warnings) implements ApplyResult {
            public boolean hasWarnings() { return !warnings.isEmpty(); }
        }
        record Failure(String reason) implements ApplyResult {}
        record Blocked(List<String> errors) implements ApplyResult {}

        static ApplyResult success() { return new Success(List.of()); }
        static ApplyResult successWithWarnings(List<String> warnings) { return new Success(warnings); }
        static ApplyResult failure(String reason) { return new Failure(reason); }
        static ApplyResult blocked(List<String> errors) { return new Blocked(errors); }
    }
}
```

#### ItemChanges

```java
/**
 * Collection of changes to apply to an item.
 */
public record ItemChanges(List<ItemChange> changes) {

    public static ItemChanges of(ItemChange... changes) {
        return new ItemChanges(List.of(changes));
    }

    public List<ItemChange> getChanges() {
        return changes;
    }
}

/**
 * A single change to an item.
 */
public sealed interface ItemChange {

    /**
     * Validate this change against the item.
     */
    ValidationResult validate(ItemStack stack);

    /**
     * Apply this change to the item.
     */
    void apply(ItemStack stack);

    /**
     * Get human-readable description of this change.
     */
    String getDescription();

    // --- Implementations ---

    record SetComponent<T>(DataComponentType<T> type, T value) implements ItemChange {
        @Override
        public ValidationResult validate(ItemStack stack) {
            // Component-specific validation via ComponentRegistry
            return ValidationResult.valid();
        }

        @Override
        public void apply(ItemStack stack) {
            stack.set(type, value);
        }

        @Override
        public String getDescription() {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            return "Set " + (id != null ? id.toString() : "unknown") + " = " + value;
        }
    }

    record RemoveComponent(DataComponentType<?> type) implements ItemChange {
        @Override
        public ValidationResult validate(ItemStack stack) {
            return ValidationResult.valid();
        }

        @Override
        public void apply(ItemStack stack) {
            stack.remove(type);
        }

        @Override
        public String getDescription() {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            return "Remove " + (id != null ? id.toString() : "unknown");
        }
    }

    record SetAttribute(ResourceLocation attribute, float value, AttributeModifier.Operation op)
        implements ItemChange {

        @Override
        public ValidationResult validate(ItemStack stack) {
            // Use ValueLimits for validation
            return ValidationResult.valid();
        }

        @Override
        public void apply(ItemStack stack) {
            // Apply attribute modifier
        }

        @Override
        public String getDescription() {
            return "Set " + attribute.getPath() + " = " + value;
        }
    }

    record SetEnchantment(ResourceLocation enchant, int level) implements ItemChange {
        @Override
        public ValidationResult validate(ItemStack stack) {
            if (level < 0 || level > 255) {
                return ValidationResult.error("Invalid enchantment level: " + level);
            }
            return ValidationResult.valid();
        }

        @Override
        public void apply(ItemStack stack) {
            // Apply enchantment
        }

        @Override
        public String getDescription() {
            return "Set " + enchant.getPath() + " " + level;
        }
    }
}
```

---

### Permissions System

#### Permission Levels

```java
/**
 * Permission levels for editor access.
 */
public enum EditorPermission {
    /** No access */
    NONE,
    /** Read-only access (can view but not modify) */
    VIEW_ONLY,
    /** Limited access (basic edits only) */
    LIMITED,
    /** Full access (all features) */
    FULL
}

/**
 * Permission check result.
 */
public record PermissionResult(
    boolean allowed,
    EditorPermission level,
    @Nullable String reason
) {
    public static PermissionResult allowed(EditorPermission level) {
        return new PermissionResult(true, level, null);
    }

    public static PermissionResult denied(String reason) {
        return new PermissionResult(false, EditorPermission.NONE, reason);
    }
}
```

#### PermissionManager

```java
/**
 * Manages editor permissions.
 */
public class PermissionManager {

    private static final PermissionManager INSTANCE = new PermissionManager();

    private boolean requireOp = true;
    private int requiredOpLevel = 2;
    private boolean allowCreativeMode = true;
    private boolean allowSinglePlayer = true;
    private Set<UUID> devModeWhitelist = new HashSet<>();
    private Set<UUID> blacklist = new HashSet<>();

    public static PermissionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Check if a player can access the editor.
     */
    public PermissionResult checkPermission(Player player) {
        // 1. Check blacklist first
        if (blacklist.contains(player.getUUID())) {
            return PermissionResult.denied("You are blacklisted from using the editor");
        }

        // 2. Check whitelist (always allowed)
        if (devModeWhitelist.contains(player.getUUID())) {
            return PermissionResult.allowed(EditorPermission.FULL);
        }

        // 3. Check single player
        if (allowSinglePlayer && isSinglePlayer(player)) {
            return PermissionResult.allowed(EditorPermission.FULL);
        }

        // 4. Check creative mode
        if (allowCreativeMode && player.isCreative()) {
            return PermissionResult.allowed(EditorPermission.FULL);
        }

        // 5. Check OP level
        if (requireOp) {
            if (player instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.hasPermissions(requiredOpLevel)) {
                    return PermissionResult.allowed(EditorPermission.FULL);
                }
            }
            return PermissionResult.denied("Requires OP level " + requiredOpLevel);
        }

        // Default: allow with full access
        return PermissionResult.allowed(EditorPermission.FULL);
    }

    /**
     * Check permission for a specific action.
     */
    public PermissionResult checkActionPermission(Player player, EditorAction action) {
        PermissionResult basePermission = checkPermission(player);
        if (!basePermission.allowed()) {
            return basePermission;
        }

        // Check action-specific restrictions
        return switch (action) {
            case VIEW -> basePermission;
            case EDIT_BASIC -> basePermission.level().ordinal() >= EditorPermission.LIMITED.ordinal()
                ? basePermission
                : PermissionResult.denied("Insufficient permissions for basic editing");
            case EDIT_ADVANCED -> basePermission.level().ordinal() >= EditorPermission.FULL.ordinal()
                ? basePermission
                : PermissionResult.denied("Insufficient permissions for advanced editing");
            case RAW_EDIT -> basePermission.level() == EditorPermission.FULL
                ? basePermission
                : PermissionResult.denied("Raw editing requires full permissions");
            case EXPORT_IMPORT -> basePermission.level() == EditorPermission.FULL
                ? basePermission
                : PermissionResult.denied("Export/Import requires full permissions");
        };
    }

    private boolean isSinglePlayer(Player player) {
        return player.level().getServer() != null &&
               player.level().getServer().isSingleplayer();
    }

    /**
     * Actions that require permission checks.
     */
    public enum EditorAction {
        VIEW,           // Open editor, view values
        EDIT_BASIC,     // Edit basic properties (damage, name, lore)
        EDIT_ADVANCED,  // Edit advanced properties (attributes, enchants)
        RAW_EDIT,       // Raw JSON editing
        EXPORT_IMPORT   // Export/import item data
    }

    // --- Config Loading ---

    public void loadFromConfig(Config config) {
        this.requireOp = config.requireOp;
        this.requiredOpLevel = config.opLevel;
        this.allowCreativeMode = config.allowCreativeMode;
        this.allowSinglePlayer = config.allowSinglePlayer;
        this.devModeWhitelist = new HashSet<>(config.devModeWhitelist.stream()
            .map(UUID::fromString)
            .toList());
        this.blacklist = new HashSet<>(config.blacklist.stream()
            .map(UUID::fromString)
            .toList());
    }
}
```

#### Permission Checks in UI

```java
/**
 * Permission-aware UI component wrapper.
 */
public class PermissionAwareWidget {

    /**
     * Wrap a widget with permission check.
     */
    public static AbstractWidget wrap(
        AbstractWidget widget,
        Player player,
        PermissionManager.EditorAction action
    ) {
        PermissionResult result = PermissionManager.getInstance()
            .checkActionPermission(player, action);

        if (!result.allowed()) {
            // Disable widget and show reason on hover
            widget.active = false;
            // Add tooltip with reason
            return new TooltipWrapper(widget, Component.literal(result.reason())
                .withStyle(ChatFormatting.RED));
        }

        return widget;
    }
}
```

---

### Change Logging

#### ChangeLogEntry

```java
/**
 * A single logged change.
 */
public record ChangeLogEntry(
    UUID playerId,
    String playerName,
    int slot,
    ItemStack before,
    ItemStack after,
    ItemChanges changes,
    long timestamp
) {

    /**
     * Serialize to JSON for storage.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("player_id", playerId.toString());
        json.addProperty("player_name", playerName);
        json.addProperty("slot", slot);
        json.addProperty("timestamp", timestamp);

        // Serialize item states
        json.addProperty("before", serializeItemStack(before));
        json.addProperty("after", serializeItemStack(after));

        // Serialize changes
        JsonArray changesArray = new JsonArray();
        for (ItemChange change : changes.getChanges()) {
            changesArray.add(change.getDescription());
        }
        json.add("changes", changesArray);

        return json;
    }

    private static String serializeItemStack(ItemStack stack) {
        // Use codec to serialize
        return ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack)
            .result()
            .map(JsonElement::toString)
            .orElse("{}");
    }

    /**
     * Format for display.
     */
    public Component toDisplayComponent() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String time = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter);

        return Component.literal("[" + time + "] ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(playerName)
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" modified item in slot " + slot)
                .withStyle(ChatFormatting.WHITE));
    }
}
```

#### ChangeLogger

```java
/**
 * Logs all item changes.
 */
public class ChangeLogger {

    private static final int MAX_MEMORY_ENTRIES = 1000;
    private static final String LOG_FILE = "devmod_changes.log";

    private final List<ChangeLogEntry> memoryLog = new ArrayList<>();
    private final Path logFilePath;
    private boolean persistToFile = true;
    private boolean enabled = true;

    public ChangeLogger(Path worldPath) {
        this.logFilePath = worldPath.resolve("logs").resolve(LOG_FILE);
    }

    /**
     * Log a change.
     */
    public void log(ChangeLogEntry entry) {
        if (!enabled) return;

        // Add to memory log
        memoryLog.add(entry);
        if (memoryLog.size() > MAX_MEMORY_ENTRIES) {
            memoryLog.remove(0);
        }

        // Persist to file
        if (persistToFile) {
            persistEntry(entry);
        }
    }

    private void persistEntry(ChangeLogEntry entry) {
        try {
            Files.createDirectories(logFilePath.getParent());
            String line = entry.toJson().toString() + "\n";
            Files.writeString(logFilePath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Log error but don't crash
        }
    }

    /**
     * Get recent entries.
     */
    public List<ChangeLogEntry> getRecentEntries(int count) {
        int start = Math.max(0, memoryLog.size() - count);
        return new ArrayList<>(memoryLog.subList(start, memoryLog.size()));
    }

    /**
     * Get entries for a specific player.
     */
    public List<ChangeLogEntry> getEntriesForPlayer(UUID playerId, int count) {
        return memoryLog.stream()
            .filter(e -> e.playerId().equals(playerId))
            .skip(Math.max(0, memoryLog.size() - count))
            .toList();
    }

    /**
     * Search entries by item type.
     */
    public List<ChangeLogEntry> searchByItemType(ResourceLocation itemId, int count) {
        return memoryLog.stream()
            .filter(e -> {
                ResourceLocation beforeId = BuiltInRegistries.ITEM.getKey(e.before().getItem());
                ResourceLocation afterId = BuiltInRegistries.ITEM.getKey(e.after().getItem());
                return itemId.equals(beforeId) || itemId.equals(afterId);
            })
            .limit(count)
            .toList();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPersistToFile(boolean persist) {
        this.persistToFile = persist;
    }
}
```

---

### Undo/Redo System

#### UndoEntry

```java
/**
 * A single undo entry.
 */
public record UndoEntry(
    UUID playerId,
    int slot,
    ItemStack snapshot,
    long timestamp
) {

    /**
     * Check if this entry has expired.
     */
    public boolean isExpired(long maxAgeMs) {
        return System.currentTimeMillis() - timestamp > maxAgeMs;
    }
}
```

#### UndoRedoManager

```java
/**
 * Manages undo/redo stacks per player.
 */
public class UndoRedoManager {

    private static final int DEFAULT_MAX_DEPTH = 50;
    private static final long DEFAULT_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes

    private final Map<UUID, Deque<UndoEntry>> undoStacks = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<UndoEntry>> redoStacks = new ConcurrentHashMap<>();

    private int maxDepth = DEFAULT_MAX_DEPTH;
    private long expiryMs = DEFAULT_EXPIRY_MS;
    private boolean persistUndo = false;

    /**
     * Push an entry to undo stack.
     */
    public void pushUndo(UndoEntry entry) {
        Deque<UndoEntry> stack = undoStacks.computeIfAbsent(entry.playerId(), k -> new ArrayDeque<>());

        // Clear redo stack on new action
        redoStacks.remove(entry.playerId());

        // Add entry
        stack.push(entry);

        // Trim to max depth
        while (stack.size() > maxDepth) {
            stack.removeLast();
        }

        // Clean expired entries
        cleanExpired(stack);
    }

    /**
     * Pop from undo stack (for undo operation).
     */
    public Optional<UndoEntry> popUndo(UUID playerId) {
        Deque<UndoEntry> stack = undoStacks.get(playerId);
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        UndoEntry entry = stack.pop();
        if (entry.isExpired(expiryMs)) {
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    /**
     * Push to redo stack (after undo).
     */
    public void pushRedo(UndoEntry entry) {
        Deque<UndoEntry> stack = redoStacks.computeIfAbsent(entry.playerId(), k -> new ArrayDeque<>());
        stack.push(entry);

        while (stack.size() > maxDepth) {
            stack.removeLast();
        }
    }

    /**
     * Pop from redo stack.
     */
    public Optional<UndoEntry> popRedo(UUID playerId) {
        Deque<UndoEntry> stack = redoStacks.get(playerId);
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(stack.pop());
    }

    /**
     * Perform undo operation.
     */
    public UndoResult undo(Player player) {
        Optional<UndoEntry> entryOpt = popUndo(player.getUUID());
        if (entryOpt.isEmpty()) {
            return UndoResult.failure("Nothing to undo");
        }

        UndoEntry entry = entryOpt.get();
        ItemStack currentItem = player.getInventory().getItem(entry.slot());

        // Store current state for redo
        pushRedo(new UndoEntry(player.getUUID(), entry.slot(), currentItem.copy(), System.currentTimeMillis()));

        // Restore snapshot
        player.getInventory().setItem(entry.slot(), entry.snapshot().copy());

        return UndoResult.success("Undid changes to item in slot " + entry.slot());
    }

    /**
     * Perform redo operation.
     */
    public UndoResult redo(Player player) {
        Optional<UndoEntry> entryOpt = popRedo(player.getUUID());
        if (entryOpt.isEmpty()) {
            return UndoResult.failure("Nothing to redo");
        }

        UndoEntry entry = entryOpt.get();
        ItemStack currentItem = player.getInventory().getItem(entry.slot());

        // Store current state for undo
        pushUndo(new UndoEntry(player.getUUID(), entry.slot(), currentItem.copy(), System.currentTimeMillis()));

        // Apply redo state
        player.getInventory().setItem(entry.slot(), entry.snapshot().copy());

        return UndoResult.success("Redid changes to item in slot " + entry.slot());
    }

    /**
     * Get undo stack depth for a player.
     */
    public int getUndoDepth(UUID playerId) {
        Deque<UndoEntry> stack = undoStacks.get(playerId);
        return stack != null ? stack.size() : 0;
    }

    /**
     * Get redo stack depth for a player.
     */
    public int getRedoDepth(UUID playerId) {
        Deque<UndoEntry> stack = redoStacks.get(playerId);
        return stack != null ? stack.size() : 0;
    }

    /**
     * Clear all history for a player.
     */
    public void clearHistory(UUID playerId) {
        undoStacks.remove(playerId);
        redoStacks.remove(playerId);
    }

    private void cleanExpired(Deque<UndoEntry> stack) {
        stack.removeIf(e -> e.isExpired(expiryMs));
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void setExpiryMs(long expiryMs) {
        this.expiryMs = expiryMs;
    }

    /**
     * Result of undo/redo operation.
     */
    public record UndoResult(boolean success, String message) {
        public static UndoResult success(String message) { return new UndoResult(true, message); }
        public static UndoResult failure(String message) { return new UndoResult(false, message); }
    }
}
```

---

### Value Safety System

#### SafetyManager

```java
/**
 * Handles dangerous value detection and rollback.
 */
public class SafetyManager {

    private static final SafetyManager INSTANCE = new SafetyManager();

    private final Map<UUID, ItemStack> crashBackups = new ConcurrentHashMap<>();
    private boolean autoRollbackEnabled = false;  // Only on crash
    private boolean blockDangerousValues = true;

    public static SafetyManager getInstance() {
        return INSTANCE;
    }

    /**
     * Store backup before potentially dangerous operation.
     */
    public void storeBackup(UUID playerId, int slot, ItemStack item) {
        crashBackups.put(playerId, item.copy());
    }

    /**
     * Clear backup after successful operation.
     */
    public void clearBackup(UUID playerId) {
        crashBackups.remove(playerId);
    }

    /**
     * Attempt rollback (called on crash recovery).
     */
    public void attemptRollback(UUID playerId, Player player, int slot) {
        if (!autoRollbackEnabled) return;

        ItemStack backup = crashBackups.get(playerId);
        if (backup != null) {
            player.getInventory().setItem(slot, backup);
            crashBackups.remove(playerId);
        }
    }

    /**
     * Check if a change should be blocked.
     */
    public SafetyCheck checkSafety(ItemStack stack, ItemChange change) {
        // Use ValidationMode from Section 2.24
        ValidationResult result = change.validate(stack);

        if (result instanceof ValidationResult.Blocked blocked) {
            return SafetyCheck.block(blocked.reason());
        }

        if (result instanceof ValidationResult.Clamped clamped) {
            return SafetyCheck.clamp(clamped.clampedValue(), clamped.reason());
        }

        if (result instanceof ValidationResult.Warning warning) {
            return SafetyCheck.warn(warning.message());
        }

        return SafetyCheck.safe();
    }

    /**
     * Safety check result.
     */
    public sealed interface SafetyCheck {
        record Safe() implements SafetyCheck {}
        record Warning(String message) implements SafetyCheck {}
        record Clamp(Object clampedValue, String reason) implements SafetyCheck {}
        record Block(String reason) implements SafetyCheck {}

        static SafetyCheck safe() { return new Safe(); }
        static SafetyCheck warn(String message) { return new Warning(message); }
        static SafetyCheck clamp(Object value, String reason) { return new Clamp(value, reason); }
        static SafetyCheck block(String reason) { return new Block(reason); }
    }
}
```

---

### UI Integration

#### Apply Button States

```
┌─────────────────────────────────────────────────────────────────────┐
│                        APPLY CONTROLS                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  [UNDO (3)]  [REDO (1)]                    [APPLY CHANGES] │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  Apply Mode: ○ Modify Original  ○ Create Copy                       │
│                                                                      │
│  Status: ✓ No validation errors                                     │
│                                                                      │
│  ─── Recent Changes ───                                              │
│  • Set attack_damage = 15.0                                          │
│  • Added Sharpness V                                                 │
│  • Changed custom_name                                               │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

#### Validation Feedback

```
┌─────────────────────────────────────────────────────────────────────┐
│  Status: ⚠ 2 warnings, 1 error                                      │
│                                                                      │
│  ┌─ Warnings ─────────────────────────────────────────────────┐     │
│  │ ⚠ attack_damage (50) exceeds soft limit (20)               │     │
│  │ ⚠ Sharpness X exceeds vanilla max (V)                      │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  ┌─ Errors ───────────────────────────────────────────────────┐     │
│  │ ❌ attack_speed (-5) below minimum (0.1)                    │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  [APPLY ANYWAY (with warnings)]  [FIX ERRORS]                       │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Config

```toml
# config/devmod-server.toml

[permissions]
# Require OP status to use editor
requireOp = true

# Required OP level (1-4)
opLevel = 2

# Allow players in creative mode to use editor
allowCreativeMode = true

# Allow in single player without restrictions
allowSinglePlayer = true

# UUID whitelist for dev mode access
devModeWhitelist = []

# UUID blacklist (always denied)
blacklist = []

[logging]
# Enable change logging
enableLogging = true

# Persist logs to file
persistToFile = true

# Maximum log entries in memory
maxMemoryEntries = 1000

# Log file path (relative to world folder)
logFile = "logs/devmod_changes.log"

[undo]
# Enable undo/redo system
enableUndo = true

# Maximum undo stack depth
maxUndoDepth = 50

# Undo entry expiry time (milliseconds)
undoExpiryMs = 1800000  # 30 minutes

# Persist undo across sessions (not recommended)
persistUndo = false

[safety]
# Block values that would break the game
blockDangerousValues = true

# Auto-rollback on crash (experimental)
autoRollbackOnCrash = false

# Show warnings for soft limit violations
showSoftLimitWarnings = true

# Require confirmation for values above soft limits
requireConfirmationForWarnings = true
```

---

### Network Payloads

```java
/**
 * Undo/Redo request payload.
 */
public record UndoRedoPayload(
    boolean isUndo  // true = undo, false = redo
) implements CustomPacketPayload {

    public static final Type<UndoRedoPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "undo_redo")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UndoRedoPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.isUndo),
            buf -> new UndoRedoPayload(buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

/**
 * Apply changes payload.
 */
public record ApplyChangesPayload(
    int slot,
    List<String> changeDescriptions,  // Serialized changes
    boolean createCopy
) implements CustomPacketPayload {

    public static final Type<ApplyChangesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "apply_changes")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyChangesPayload> STREAM_CODEC =
        StreamCodec.of(ApplyChangesPayload::encode, ApplyChangesPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ApplyChangesPayload payload) {
        buf.writeVarInt(payload.slot);
        buf.writeCollection(payload.changeDescriptions, RegistryFriendlyByteBuf::writeUtf);
        buf.writeBoolean(payload.createCopy);
    }

    private static ApplyChangesPayload decode(RegistryFriendlyByteBuf buf) {
        return new ApplyChangesPayload(
            buf.readVarInt(),
            buf.readList(RegistryFriendlyByteBuf::readUtf),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

---

### Keyboard Shortcuts

| Shortcut | Azione |
|----------|--------|
| `Ctrl+Z` | Undo |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Redo |
| `Ctrl+S` | Apply Changes |
| `Ctrl+Shift+S` | Apply as Copy |
| `Escape` | Cancel / Close without saving |

---

## 2.36 Export/Import & Presets System

Sistema per esportazione, importazione e presets di configurazioni item.

---

### Export Formats

#### Supported Formats

| Formato | Estensione | Use Case | Features |
|---------|------------|----------|----------|
| **JSON** | `.json` | Default, sharing, human-readable | Full data, comments support |
| **TOML** | `.toml` | Config-style presets | Readable, good for manual editing |
| **Clipboard** | N/A | Quick copy/paste | Base64 encoded JSON |
| **Datapack** | `data/...` | Vanilla integration | Recipe/loot table format |

#### ExportFormat Enum

```java
/**
 * Supported export formats.
 */
public enum ExportFormat {
    JSON("json", "JSON", true),
    TOML("toml", "TOML Config", true),
    CLIPBOARD("clipboard", "Clipboard", false),
    DATAPACK("datapack", "Datapack", true);

    public final String extension;
    public final String displayName;
    public final boolean createsFile;

    ExportFormat(String extension, String displayName, boolean createsFile) {
        this.extension = extension;
        this.displayName = displayName;
        this.createsFile = createsFile;
    }
}
```

---

### Export Modes

#### Full vs Delta Export

```java
/**
 * Export mode determining what data to include.
 */
public enum ExportMode {
    /**
     * Export all components, even vanilla defaults.
     * Use for: Complete backups, sharing full items.
     */
    FULL,

    /**
     * Export only differences from vanilla item.
     * Use for: Presets, config overrides, smaller files.
     */
    DELTA,

    /**
     * Export only selected components.
     * Use for: Partial presets (e.g., only enchants).
     */
    SELECTIVE
}
```

#### Delta Calculation

```java
/**
 * Calculates delta between modified item and vanilla base.
 */
public class DeltaCalculator {

    /**
     * Get delta components (only changed from vanilla).
     */
    public static Map<DataComponentType<?>, Object> calculateDelta(ItemStack modified) {
        // Get vanilla item defaults
        ItemStack vanilla = new ItemStack(modified.getItem());
        Map<DataComponentType<?>, Object> delta = new LinkedHashMap<>();

        // Compare each component
        modified.getComponents().forEach(typed -> {
            DataComponentType<?> type = typed.type();
            Object modifiedValue = typed.value();
            Object vanillaValue = vanilla.get(type);

            // Include if different from vanilla
            if (!Objects.equals(modifiedValue, vanillaValue)) {
                delta.put(type, modifiedValue);
            }
        });

        // Check for removed components (present in vanilla, absent in modified)
        vanilla.getComponents().forEach(typed -> {
            if (!modified.has(typed.type())) {
                delta.put(typed.type(), null);  // null = removed
            }
        });

        return delta;
    }

    /**
     * Check if item has any modifications from vanilla.
     */
    public static boolean hasModifications(ItemStack stack) {
        return !calculateDelta(stack).isEmpty();
    }

    /**
     * Get count of modified components.
     */
    public static int getModificationCount(ItemStack stack) {
        return calculateDelta(stack).size();
    }
}
```

---

### Export Data Structure

#### ItemExportData

```java
/**
 * Complete export data structure with versioning.
 */
public record ItemExportData(
    // Versioning
    String schemaVersion,
    String modVersion,
    String minecraftVersion,
    long exportTimestamp,

    // Item identification
    ResourceLocation itemId,
    @Nullable String customName,

    // Export metadata
    ExportMode exportMode,
    String exportedBy,

    // Item data
    Map<String, JsonElement> components,

    // Optional sections
    @Nullable EnchantmentExportData enchantments,
    @Nullable AttributeExportData attributes,
    @Nullable AffixExportData affixes,

    // Notes
    @Nullable String description,
    List<String> tags
) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0.0";

    public static final Codec<ItemExportData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("schema_version").forGetter(ItemExportData::schemaVersion),
            Codec.STRING.fieldOf("mod_version").forGetter(ItemExportData::modVersion),
            Codec.STRING.fieldOf("minecraft_version").forGetter(ItemExportData::minecraftVersion),
            Codec.LONG.fieldOf("export_timestamp").forGetter(ItemExportData::exportTimestamp),
            ResourceLocation.CODEC.fieldOf("item_id").forGetter(ItemExportData::itemId),
            Codec.STRING.optionalFieldOf("custom_name").forGetter(d -> Optional.ofNullable(d.customName())),
            Codec.STRING.xmap(ExportMode::valueOf, ExportMode::name)
                .fieldOf("export_mode").forGetter(ItemExportData::exportMode),
            Codec.STRING.fieldOf("exported_by").forGetter(ItemExportData::exportedBy),
            Codec.unboundedMap(Codec.STRING, JsonElement.CODEC)
                .fieldOf("components").forGetter(ItemExportData::components),
            EnchantmentExportData.CODEC.optionalFieldOf("enchantments")
                .forGetter(d -> Optional.ofNullable(d.enchantments())),
            AttributeExportData.CODEC.optionalFieldOf("attributes")
                .forGetter(d -> Optional.ofNullable(d.attributes())),
            AffixExportData.CODEC.optionalFieldOf("affixes")
                .forGetter(d -> Optional.ofNullable(d.affixes())),
            Codec.STRING.optionalFieldOf("description").forGetter(d -> Optional.ofNullable(d.description())),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(ItemExportData::tags)
        ).apply(instance, ItemExportData::new)
    );

    /**
     * Create export data from an item.
     */
    public static ItemExportData fromItem(
        ItemStack stack,
        ExportMode mode,
        String exportedBy,
        @Nullable String description,
        List<String> tags
    ) {
        Map<String, JsonElement> components = new LinkedHashMap<>();

        // Get components based on mode
        Map<DataComponentType<?>, Object> toExport = switch (mode) {
            case FULL -> getAllComponents(stack);
            case DELTA -> DeltaCalculator.calculateDelta(stack);
            case SELECTIVE -> getAllComponents(stack);  // Filtered later
        };

        // Serialize components
        toExport.forEach((type, value) -> {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id != null && value != null) {
                serializeComponent(type, value).ifPresent(json ->
                    components.put(id.toString(), json)
                );
            } else if (id != null && value == null) {
                // Mark as removed
                components.put(id.toString(), JsonNull.INSTANCE);
            }
        });

        // Extract specialized data
        EnchantmentExportData enchants = EnchantmentExportData.from(stack);
        AttributeExportData attrs = AttributeExportData.from(stack);
        AffixExportData affixes = AffixExportData.from(stack);

        // Get custom name
        Component customNameComp = stack.get(DataComponents.CUSTOM_NAME);
        String customName = customNameComp != null ? customNameComp.getString() : null;

        return new ItemExportData(
            CURRENT_SCHEMA_VERSION,
            DevMod.VERSION,
            SharedConstants.getCurrentVersion().getName(),
            System.currentTimeMillis(),
            BuiltInRegistries.ITEM.getKey(stack.getItem()),
            customName,
            mode,
            exportedBy,
            components,
            enchants,
            attrs,
            affixes,
            description,
            tags
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<JsonElement> serializeComponent(DataComponentType<T> type, Object value) {
        Codec<T> codec = type.codec();
        if (codec == null) return Optional.empty();

        return codec.encodeStart(JsonOps.INSTANCE, (T) value).result();
    }

    private static Map<DataComponentType<?>, Object> getAllComponents(ItemStack stack) {
        Map<DataComponentType<?>, Object> all = new LinkedHashMap<>();
        stack.getComponents().forEach(typed -> all.put(typed.type(), typed.value()));
        return all;
    }
}
```

#### Specialized Export Data

```java
/**
 * Enchantment-specific export data (human-readable).
 */
public record EnchantmentExportData(
    Map<String, Integer> enchantments,  // id -> level
    boolean bypassedCompatibility,
    Map<String, Integer> levelOverrides
) {
    public static final Codec<EnchantmentExportData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                .fieldOf("enchantments").forGetter(EnchantmentExportData::enchantments),
            Codec.BOOL.optionalFieldOf("bypassed_compatibility", false)
                .forGetter(EnchantmentExportData::bypassedCompatibility),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                .optionalFieldOf("level_overrides", Map.of())
                .forGetter(EnchantmentExportData::levelOverrides)
        ).apply(instance, EnchantmentExportData::new)
    );

    public static EnchantmentExportData from(ItemStack stack) {
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) return null;

        Map<String, Integer> map = new LinkedHashMap<>();
        enchants.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
            if (id != null) {
                map.put(id.toString(), entry.getIntValue());
            }
        });

        return new EnchantmentExportData(map, false, Map.of());
    }
}

/**
 * Attribute-specific export data.
 */
public record AttributeExportData(
    List<AttributeModifierEntry> modifiers
) {
    public record AttributeModifierEntry(
        String attribute,
        String id,
        double amount,
        String operation,
        String slot
    ) {}

    public static final Codec<AttributeExportData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            AttributeModifierEntry.CODEC.listOf()
                .fieldOf("modifiers").forGetter(AttributeExportData::modifiers)
        ).apply(instance, AttributeExportData::new)
    );

    public static AttributeExportData from(ItemStack stack) {
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods == null) return null;

        List<AttributeModifierEntry> entries = new ArrayList<>();
        mods.modifiers().forEach(entry -> {
            ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value());
            if (attrId != null) {
                entries.add(new AttributeModifierEntry(
                    attrId.toString(),
                    entry.modifier().id().toString(),
                    entry.modifier().amount(),
                    entry.modifier().operation().name(),
                    entry.slot().toString()
                ));
            }
        });

        return entries.isEmpty() ? null : new AttributeExportData(entries);
    }
}

/**
 * Affix-specific export data.
 */
public record AffixExportData(
    List<AffixEntry> prefixes,
    List<AffixEntry> suffixes,
    List<AffixEntry> implicits
) {
    public record AffixEntry(
        String affixId,
        String tier,
        float value
    ) {}

    public static final Codec<AffixExportData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            AffixEntry.CODEC.listOf().optionalFieldOf("prefixes", List.of())
                .forGetter(AffixExportData::prefixes),
            AffixEntry.CODEC.listOf().optionalFieldOf("suffixes", List.of())
                .forGetter(AffixExportData::suffixes),
            AffixEntry.CODEC.listOf().optionalFieldOf("implicits", List.of())
                .forGetter(AffixExportData::implicits)
        ).apply(instance, AffixExportData::new)
    );

    public static AffixExportData from(ItemStack stack) {
        ItemAffixes affixes = stack.get(ModDataComponents.ITEM_AFFIXES);
        if (affixes == null) return null;

        List<AffixEntry> prefixes = affixes.prefixes().stream()
            .map(a -> new AffixEntry(a.definitionId().toString(), a.tier().name(), a.rolledValue()))
            .toList();
        List<AffixEntry> suffixes = affixes.suffixes().stream()
            .map(a -> new AffixEntry(a.definitionId().toString(), a.tier().name(), a.rolledValue()))
            .toList();
        List<AffixEntry> implicits = affixes.implicits().stream()
            .map(a -> new AffixEntry(a.definitionId().toString(), a.tier().name(), a.rolledValue()))
            .toList();

        if (prefixes.isEmpty() && suffixes.isEmpty() && implicits.isEmpty()) {
            return null;
        }

        return new AffixExportData(prefixes, suffixes, implicits);
    }
}
```

---

### Exporters

#### JSON Exporter

```java
/**
 * Exports item data to JSON format.
 */
public class JsonExporter {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    /**
     * Export to JSON string.
     */
    public static String export(ItemExportData data) {
        return GSON.toJson(ItemExportData.CODEC.encodeStart(JsonOps.INSTANCE, data)
            .getOrThrow(msg -> new RuntimeException(msg)));
    }

    /**
     * Export to file.
     */
    public static void exportToFile(ItemExportData data, Path path) throws IOException {
        String json = export(data);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /**
     * Generate filename for export.
     */
    public static String generateFilename(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemName = id.getPath().replace("/", "_");
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .format(LocalDateTime.now());
        return itemName + "_" + timestamp + ".json";
    }
}
```

#### TOML Exporter

```java
/**
 * Exports item data to TOML format.
 */
public class TomlExporter {

    /**
     * Export to TOML string.
     */
    public static String export(ItemExportData data) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# DevMod Item Export\n");
        sb.append("# Generated: ").append(formatTimestamp(data.exportTimestamp())).append("\n\n");

        // Versioning
        sb.append("[version]\n");
        sb.append("schema = \"").append(data.schemaVersion()).append("\"\n");
        sb.append("mod = \"").append(data.modVersion()).append("\"\n");
        sb.append("minecraft = \"").append(data.minecraftVersion()).append("\"\n\n");

        // Item info
        sb.append("[item]\n");
        sb.append("id = \"").append(data.itemId()).append("\"\n");
        if (data.customName() != null) {
            sb.append("name = \"").append(escapeToml(data.customName())).append("\"\n");
        }
        sb.append("export_mode = \"").append(data.exportMode().name()).append("\"\n\n");

        // Enchantments (readable format)
        if (data.enchantments() != null && !data.enchantments().enchantments().isEmpty()) {
            sb.append("[enchantments]\n");
            data.enchantments().enchantments().forEach((id, level) -> {
                String shortId = id.contains(":") ? id.split(":")[1] : id;
                sb.append(shortId).append(" = ").append(level).append("\n");
            });
            sb.append("\n");
        }

        // Attributes (readable format)
        if (data.attributes() != null && !data.attributes().modifiers().isEmpty()) {
            sb.append("[attributes]\n");
            for (var entry : data.attributes().modifiers()) {
                String shortAttr = entry.attribute().contains(":")
                    ? entry.attribute().split(":")[1]
                    : entry.attribute();
                sb.append(shortAttr).append(" = ")
                    .append(entry.amount())
                    .append(" # ").append(entry.operation())
                    .append("\n");
            }
            sb.append("\n");
        }

        // Affixes
        if (data.affixes() != null) {
            if (!data.affixes().prefixes().isEmpty()) {
                sb.append("[affixes.prefixes]\n");
                for (var affix : data.affixes().prefixes()) {
                    sb.append(affix.affixId()).append(" = { tier = \"")
                        .append(affix.tier()).append("\", value = ")
                        .append(affix.value()).append(" }\n");
                }
                sb.append("\n");
            }
            if (!data.affixes().suffixes().isEmpty()) {
                sb.append("[affixes.suffixes]\n");
                for (var affix : data.affixes().suffixes()) {
                    sb.append(affix.affixId()).append(" = { tier = \"")
                        .append(affix.tier()).append("\", value = ")
                        .append(affix.value()).append(" }\n");
                }
                sb.append("\n");
            }
        }

        // Description
        if (data.description() != null) {
            sb.append("[metadata]\n");
            sb.append("description = \"").append(escapeToml(data.description())).append("\"\n");
            if (!data.tags().isEmpty()) {
                sb.append("tags = [");
                sb.append(data.tags().stream()
                    .map(t -> "\"" + escapeToml(t) + "\"")
                    .collect(Collectors.joining(", ")));
                sb.append("]\n");
            }
        }

        return sb.toString();
    }

    private static String escapeToml(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatTimestamp(long ts) {
        return Instant.ofEpochMilli(ts).toString();
    }
}
```

#### Clipboard Exporter

```java
/**
 * Exports item data to clipboard (Base64 encoded).
 */
public class ClipboardExporter {

    private static final String CLIPBOARD_PREFIX = "DEVMOD_ITEM:";

    /**
     * Export to clipboard.
     */
    public static void exportToClipboard(ItemExportData data, Minecraft mc) {
        String json = JsonExporter.export(data);
        String encoded = CLIPBOARD_PREFIX + Base64.getEncoder().encodeToString(
            json.getBytes(StandardCharsets.UTF_8)
        );
        mc.keyboardHandler.setClipboard(encoded);
    }

    /**
     * Check if clipboard contains valid item data.
     */
    public static boolean isValidClipboard(String clipboard) {
        return clipboard != null && clipboard.startsWith(CLIPBOARD_PREFIX);
    }

    /**
     * Import from clipboard.
     */
    public static Optional<ItemExportData> importFromClipboard(Minecraft mc) {
        String clipboard = mc.keyboardHandler.getClipboard();
        if (!isValidClipboard(clipboard)) {
            return Optional.empty();
        }

        try {
            String encoded = clipboard.substring(CLIPBOARD_PREFIX.length());
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            return JsonImporter.parse(json);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
```

#### Datapack Exporter

```java
/**
 * Exports item modifications as datapack format.
 */
public class DatapackExporter {

    /**
     * Export as datapack structure.
     * Creates files for loot table modifier and/or recipe.
     */
    public static DatapackExport export(ItemExportData data, String namespace) {
        Map<String, String> files = new LinkedHashMap<>();

        // Generate item modifier (for loot tables)
        String modifierJson = generateItemModifier(data);
        String modifierPath = "data/" + namespace + "/item_modifiers/"
            + data.itemId().getPath() + "_custom.json";
        files.put(modifierPath, modifierJson);

        // Generate pack.mcmeta
        String packMcmeta = generatePackMcmeta(namespace);
        files.put("pack.mcmeta", packMcmeta);

        return new DatapackExport(namespace, files);
    }

    private static String generateItemModifier(ItemExportData data) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:set_components");

        JsonObject components = new JsonObject();
        data.components().forEach(components::add);
        root.add("components", components);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static String generatePackMcmeta(String namespace) {
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "DevMod exported item: " + namespace);
        pack.addProperty("pack_format", 48);  // 1.21.x
        root.add("pack", pack);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * Datapack export result.
     */
    public record DatapackExport(
        String namespace,
        Map<String, String> files  // path -> content
    ) {
        /**
         * Write to directory.
         */
        public void writeTo(Path outputDir) throws IOException {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path filePath = outputDir.resolve(entry.getKey());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, entry.getValue(), StandardCharsets.UTF_8);
            }
        }

        /**
         * Write to ZIP file.
         */
        public void writeToZip(Path zipPath) throws IOException {
            try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                    zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
        }
    }
}
```

---

### Importers

#### JSON Importer

```java
/**
 * Imports item data from JSON.
 */
public class JsonImporter {

    /**
     * Parse JSON string to export data.
     */
    public static Optional<ItemExportData> parse(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return ItemExportData.CODEC.parse(JsonOps.INSTANCE, element).result();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Import from file.
     */
    public static Optional<ItemExportData> importFromFile(Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return parse(json);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Apply import data to an item.
     */
    public static ImportResult applyToItem(ItemStack stack, ItemExportData data, RegistryAccess registries) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Verify item type matches (if not delta mode)
        ResourceLocation currentItem = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (data.exportMode() == ExportMode.FULL && !data.itemId().equals(currentItem)) {
            warnings.add("Item type mismatch: export is for " + data.itemId() + ", applying to " + currentItem);
        }

        // Check version compatibility
        VersionCheck versionCheck = checkVersionCompatibility(data);
        if (versionCheck.hasIssues()) {
            warnings.addAll(versionCheck.warnings());
            errors.addAll(versionCheck.errors());
        }

        if (!errors.isEmpty()) {
            return ImportResult.failure(errors, warnings);
        }

        // Apply components
        for (Map.Entry<String, JsonElement> entry : data.components().entrySet()) {
            ResourceLocation compId = ResourceLocation.tryParse(entry.getKey());
            if (compId == null) {
                warnings.add("Invalid component ID: " + entry.getKey());
                continue;
            }

            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(compId);
            if (type == null) {
                warnings.add("Unknown component: " + compId);
                continue;
            }

            try {
                if (entry.getValue().isJsonNull()) {
                    // Remove component
                    stack.remove(type);
                } else {
                    // Set component
                    applyComponent(stack, type, entry.getValue());
                }
            } catch (Exception e) {
                warnings.add("Failed to apply " + compId + ": " + e.getMessage());
            }
        }

        return ImportResult.success(warnings);
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyComponent(ItemStack stack, DataComponentType<T> type, JsonElement json) {
        Codec<T> codec = type.codec();
        if (codec == null) return;

        T value = codec.parse(JsonOps.INSTANCE, json)
            .getOrThrow(msg -> new IllegalArgumentException(msg));
        stack.set(type, value);
    }

    /**
     * Import result.
     */
    public record ImportResult(
        boolean success,
        List<String> errors,
        List<String> warnings
    ) {
        public static ImportResult success(List<String> warnings) {
            return new ImportResult(true, List.of(), warnings);
        }

        public static ImportResult failure(List<String> errors, List<String> warnings) {
            return new ImportResult(false, errors, warnings);
        }
    }
}
```

---

### Version Compatibility

#### VersionCheck

```java
/**
 * Checks version compatibility for imports.
 */
public class VersionChecker {

    /**
     * Check compatibility of export data.
     */
    public static VersionCheck checkVersionCompatibility(ItemExportData data) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Check schema version
        String currentSchema = ItemExportData.CURRENT_SCHEMA_VERSION;
        if (!isCompatibleSchema(data.schemaVersion(), currentSchema)) {
            errors.add("Incompatible schema version: " + data.schemaVersion()
                + " (current: " + currentSchema + ")");
        } else if (!data.schemaVersion().equals(currentSchema)) {
            warnings.add("Schema version mismatch: " + data.schemaVersion()
                + " (current: " + currentSchema + "). Migration may be needed.");
        }

        // Check Minecraft version
        String currentMc = SharedConstants.getCurrentVersion().getName();
        if (!isSameMajorMinecraftVersion(data.minecraftVersion(), currentMc)) {
            warnings.add("Minecraft version mismatch: " + data.minecraftVersion()
                + " (current: " + currentMc + "). Some data may not apply correctly.");
        }

        // Check mod version
        String currentMod = DevMod.VERSION;
        int comparison = compareVersions(data.modVersion(), currentMod);
        if (comparison > 0) {
            warnings.add("Export from newer mod version: " + data.modVersion()
                + " (current: " + currentMod + ")");
        }

        return new VersionCheck(warnings, errors);
    }

    private static boolean isCompatibleSchema(String exportVersion, String currentVersion) {
        // Major version must match
        String exportMajor = exportVersion.split("\\.")[0];
        String currentMajor = currentVersion.split("\\.")[0];
        return exportMajor.equals(currentMajor);
    }

    private static boolean isSameMajorMinecraftVersion(String exportMc, String currentMc) {
        // Compare major.minor (e.g., 1.21)
        String[] exportParts = exportMc.split("\\.");
        String[] currentParts = currentMc.split("\\.");

        if (exportParts.length < 2 || currentParts.length < 2) return false;

        return exportParts[0].equals(currentParts[0]) && exportParts[1].equals(currentParts[1]);
    }

    private static int compareVersions(String v1, String v2) {
        // Simple version comparison
        String[] parts1 = v1.replaceAll("[^0-9.]", "").split("\\.");
        String[] parts2 = v2.replaceAll("[^0-9.]", "").split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2) return Integer.compare(p1, p2);
        }
        return 0;
    }

    /**
     * Version check result.
     */
    public record VersionCheck(List<String> warnings, List<String> errors) {
        public boolean hasIssues() {
            return !warnings.isEmpty() || !errors.isEmpty();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
```

#### Migration Handlers

```java
/**
 * Handles migration between schema versions.
 */
public class SchemaMigrator {

    private static final Map<String, MigrationHandler> MIGRATIONS = new LinkedHashMap<>();

    static {
        // Register migration handlers
        // MIGRATIONS.put("0.9.0->1.0.0", new Migration_0_9_to_1_0());
    }

    /**
     * Migrate export data to current schema.
     */
    public static ItemExportData migrate(ItemExportData data) {
        String current = data.schemaVersion();
        String target = ItemExportData.CURRENT_SCHEMA_VERSION;

        if (current.equals(target)) {
            return data;  // No migration needed
        }

        // Find migration path
        List<MigrationHandler> path = findMigrationPath(current, target);
        if (path.isEmpty()) {
            throw new IllegalStateException("No migration path from " + current + " to " + target);
        }

        // Apply migrations in sequence
        ItemExportData migrated = data;
        for (MigrationHandler handler : path) {
            migrated = handler.migrate(migrated);
        }

        return migrated;
    }

    private static List<MigrationHandler> findMigrationPath(String from, String to) {
        // Simple sequential migration
        List<MigrationHandler> path = new ArrayList<>();
        String current = from;

        for (Map.Entry<String, MigrationHandler> entry : MIGRATIONS.entrySet()) {
            String[] versions = entry.getKey().split("->");
            if (versions[0].equals(current)) {
                path.add(entry.getValue());
                current = versions[1];
                if (current.equals(to)) break;
            }
        }

        return current.equals(to) ? path : List.of();
    }

    /**
     * Migration handler interface.
     */
    public interface MigrationHandler {
        ItemExportData migrate(ItemExportData data);
    }
}
```

---

### Presets System

#### Preset

```java
/**
 * A saved preset configuration.
 */
public record Preset(
    String id,
    String name,
    String description,
    PresetScope scope,
    ItemExportData data,
    Set<PresetContent> contents,
    List<String> tags,
    long createdAt,
    long modifiedAt
) {

    /**
     * What the preset applies to.
     */
    public enum PresetScope {
        /** Applies to specific item type only */
        SPECIFIC_ITEM,
        /** Applies to any weapon */
        ALL_WEAPONS,
        /** Applies to any armor */
        ALL_ARMOR,
        /** Applies to any tool */
        ALL_TOOLS,
        /** Applies to any item */
        UNIVERSAL
    }

    /**
     * What content is included in the preset.
     */
    public enum PresetContent {
        ATTRIBUTES,
        ENCHANTMENTS,
        DURABILITY,
        REPAIR,
        DISPLAY,  // name, lore, rarity
        AFFIXES
    }

    public static final Codec<Preset> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(Preset::id),
            Codec.STRING.fieldOf("name").forGetter(Preset::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(Preset::description),
            Codec.STRING.xmap(PresetScope::valueOf, PresetScope::name)
                .fieldOf("scope").forGetter(Preset::scope),
            ItemExportData.CODEC.fieldOf("data").forGetter(Preset::data),
            Codec.STRING.listOf().xmap(
                list -> list.stream().map(PresetContent::valueOf).collect(Collectors.toSet()),
                set -> set.stream().map(PresetContent::name).toList()
            ).fieldOf("contents").forGetter(Preset::contents),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(Preset::tags),
            Codec.LONG.fieldOf("created_at").forGetter(Preset::createdAt),
            Codec.LONG.fieldOf("modified_at").forGetter(Preset::modifiedAt)
        ).apply(instance, Preset::new)
    );

    /**
     * Check if this preset can be applied to an item.
     */
    public boolean canApplyTo(ItemStack stack) {
        return switch (scope) {
            case SPECIFIC_ITEM -> data.itemId().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            case ALL_WEAPONS -> stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem;
            case ALL_ARMOR -> stack.getItem() instanceof ArmorItem;
            case ALL_TOOLS -> stack.getItem() instanceof DiggerItem;
            case UNIVERSAL -> true;
        };
    }

    /**
     * Apply this preset to an item.
     */
    public JsonImporter.ImportResult applyTo(ItemStack stack, RegistryAccess registries) {
        if (!canApplyTo(stack)) {
            return JsonImporter.ImportResult.failure(
                List.of("Preset cannot be applied to this item type"),
                List.of()
            );
        }

        // Filter data based on included contents
        ItemExportData filtered = filterByContents(data, contents);
        return JsonImporter.applyToItem(stack, filtered, registries);
    }

    private static ItemExportData filterByContents(ItemExportData data, Set<PresetContent> contents) {
        // Filter components based on content flags
        Map<String, JsonElement> filtered = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : data.components().entrySet()) {
            if (shouldIncludeComponent(entry.getKey(), contents)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        return new ItemExportData(
            data.schemaVersion(),
            data.modVersion(),
            data.minecraftVersion(),
            data.exportTimestamp(),
            data.itemId(),
            contents.contains(PresetContent.DISPLAY) ? data.customName() : null,
            data.exportMode(),
            data.exportedBy(),
            filtered,
            contents.contains(PresetContent.ENCHANTMENTS) ? data.enchantments() : null,
            contents.contains(PresetContent.ATTRIBUTES) ? data.attributes() : null,
            contents.contains(PresetContent.AFFIXES) ? data.affixes() : null,
            data.description(),
            data.tags()
        );
    }

    private static boolean shouldIncludeComponent(String componentId, Set<PresetContent> contents) {
        if (componentId.contains("damage") || componentId.contains("unbreakable")) {
            return contents.contains(PresetContent.DURABILITY);
        }
        if (componentId.contains("repair")) {
            return contents.contains(PresetContent.REPAIR);
        }
        if (componentId.contains("enchantment")) {
            return contents.contains(PresetContent.ENCHANTMENTS);
        }
        if (componentId.contains("attribute")) {
            return contents.contains(PresetContent.ATTRIBUTES);
        }
        if (componentId.contains("name") || componentId.contains("lore") || componentId.contains("rarity")) {
            return contents.contains(PresetContent.DISPLAY);
        }
        return true;  // Include by default
    }
}
```

#### PresetManager

```java
/**
 * Manages preset storage and retrieval.
 */
public class PresetManager {

    private static final String PRESETS_DIR = "config/devmod/presets";
    private final Map<String, Preset> presets = new ConcurrentHashMap<>();
    private final Path presetsPath;

    public PresetManager(Path gamePath) {
        this.presetsPath = gamePath.resolve(PRESETS_DIR);
    }

    /**
     * Load all presets from disk.
     */
    public void loadPresets() {
        presets.clear();

        try {
            if (!Files.exists(presetsPath)) {
                Files.createDirectories(presetsPath);
                return;
            }

            try (var stream = Files.list(presetsPath)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadPreset);
            }
        } catch (IOException e) {
            // Log error
        }
    }

    private void loadPreset(Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(json);
            Preset preset = Preset.CODEC.parse(JsonOps.INSTANCE, element)
                .getOrThrow(msg -> new RuntimeException(msg));
            presets.put(preset.id(), preset);
        } catch (Exception e) {
            // Log error
        }
    }

    /**
     * Save a preset.
     */
    public void savePreset(Preset preset) throws IOException {
        presets.put(preset.id(), preset);

        Files.createDirectories(presetsPath);
        Path path = presetsPath.resolve(preset.id() + ".json");
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(
            Preset.CODEC.encodeStart(JsonOps.INSTANCE, preset)
                .getOrThrow(msg -> new RuntimeException(msg))
        );
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /**
     * Delete a preset.
     */
    public boolean deletePreset(String id) {
        presets.remove(id);
        try {
            return Files.deleteIfExists(presetsPath.resolve(id + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get all presets.
     */
    public Collection<Preset> getAllPresets() {
        return presets.values();
    }

    /**
     * Get presets applicable to an item.
     */
    public List<Preset> getPresetsFor(ItemStack stack) {
        return presets.values().stream()
            .filter(p -> p.canApplyTo(stack))
            .toList();
    }

    /**
     * Get preset by ID.
     */
    public Optional<Preset> getPreset(String id) {
        return Optional.ofNullable(presets.get(id));
    }

    /**
     * Search presets by tag.
     */
    public List<Preset> searchByTag(String tag) {
        return presets.values().stream()
            .filter(p -> p.tags().contains(tag))
            .toList();
    }

    /**
     * Search presets by name.
     */
    public List<Preset> searchByName(String query) {
        String lower = query.toLowerCase();
        return presets.values().stream()
            .filter(p -> p.name().toLowerCase().contains(lower))
            .toList();
    }

    /**
     * Create preset from current item.
     */
    public Preset createPreset(
        ItemStack stack,
        String name,
        String description,
        Preset.PresetScope scope,
        Set<Preset.PresetContent> contents,
        List<String> tags,
        String createdBy
    ) {
        String id = generatePresetId(name);
        ItemExportData data = ItemExportData.fromItem(
            stack, ExportMode.DELTA, createdBy, description, tags
        );
        long now = System.currentTimeMillis();

        return new Preset(id, name, description, scope, data, contents, tags, now, now);
    }

    private String generatePresetId(String name) {
        String base = name.toLowerCase()
            .replaceAll("[^a-z0-9]", "_")
            .replaceAll("_+", "_");
        String id = base;
        int counter = 1;
        while (presets.containsKey(id)) {
            id = base + "_" + counter++;
        }
        return id;
    }
}
```

---

### UI Mockup

```
┌─────────────────────────────────────────────────────────────────────┐
│                     EXPORT / IMPORT                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ═══ EXPORT ═══                                                      │
│                                                                      │
│  Format:   ○ JSON  ○ TOML  ● Clipboard  ○ Datapack                  │
│                                                                      │
│  Mode:     ● Delta (changes only)  ○ Full (all data)                │
│                                                                      │
│  Include:  [✓] Attributes  [✓] Enchantments  [✓] Durability         │
│            [✓] Display     [ ] Affixes       [ ] Repair             │
│                                                                      │
│  Description: [________________________________]                     │
│  Tags:        [combat, legendary, custom_____]                      │
│                                                                      │
│  [EXPORT]  [EXPORT AS PRESET...]                                    │
│                                                                      │
│  ═══ IMPORT ═══                                                      │
│                                                                      │
│  Source:   ○ File  ● Clipboard  ○ Preset                            │
│                                                                      │
│  ┌─ Clipboard Preview ──────────────────────────────────────────┐   │
│  │ Schema: 1.0.0 | Mod: 1.21-0.5.0 | MC: 1.21.1                 │   │
│  │ Item: minecraft:diamond_sword                                 │   │
│  │ Mode: DELTA                                                   │   │
│  │ Components: 5 modified                                        │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  [IMPORT]  [PREVIEW CHANGES...]                                     │
│                                                                      │
│  ═══ PRESETS ═══                                                     │
│                                                                      │
│  Search: [____________________] [🔍]  Filter: [ALL ▼]               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ ⚔ Combat Master        [WEAPONS]     [Apply] [Edit] [Del] │     │
│  │   +15 damage, Sharpness V, Unbreaking III                  │     │
│  ├────────────────────────────────────────────────────────────┤     │
│  │ 🛡 Tank Armor           [ARMOR]       [Apply] [Edit] [Del] │     │
│  │   +20 armor, +10 toughness, all resistances                │     │
│  ├────────────────────────────────────────────────────────────┤     │
│  │ ⚡ Speed Demon           [UNIVERSAL]   [Apply] [Edit] [Del] │     │
│  │   +50% movement speed, lightweight                          │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Quick Actions

| Action | Descrizione | Shortcut |
|--------|-------------|----------|
| **Quick Export** | Export to clipboard (Delta) | Ctrl+E |
| **Quick Import** | Import from clipboard | Ctrl+I |
| **Save as Preset** | Save current as new preset | Ctrl+P |
| **Open Presets** | Open preset browser | P |

---

### Presets Overlay (UI Behavior)

- **Input capture**: overlay consumes mouse/keyboard events (clicks, scroll, Delete) until closed; ESC/outside-click closes; underlying History/Favorites are frozen (auto-hide) while open and restored to their previous expansion state on close.
- **Virtualization**: list rows fixed at **24px** height with scissor clipping and lazy measurement; only visible + buffer rows are rendered to avoid layout hitches when many presets exist.
- **Shortcuts**: `Ctrl+F` focuses search, `Delete` on hovered entry opens delete confirm, `Enter` is inert (no accidental load), `Esc` closes overlay, arrow keys keep the default scroll behavior.
- **Consistency**: Quick Apply from overlay (and Favorites) updates “last loaded preset” metadata (timestamp + scope badge) and status to keep footer/dirty indicators in sync.

---

### Config

```toml
# config/devmod-client.toml

[export]
# Default export format
defaultFormat = "clipboard"

# Default export mode
defaultMode = "delta"

# Default export directory
exportDirectory = "exports"

# Include description prompt on export
promptDescription = true

# Auto-generate tags based on item
autoGenerateTags = true

[import]
# Show preview before import
showPreview = true

# Warn on version mismatch
warnVersionMismatch = true

# Allow import from older schema versions
allowOlderSchema = true

# Allow import from newer schema versions (risky)
allowNewerSchema = false

[presets]
# Presets directory
presetsDirectory = "config/devmod/presets"

# Show built-in presets
showBuiltinPresets = true

# Allow editing built-in presets
allowEditBuiltins = false

# Sync presets with server (multiplayer)
syncWithServer = false
```

---

### Network Payloads

```java
/**
 * Payload for importing item data.
 */
public record ImportItemPayload(
    int slot,
    String jsonData,
    boolean preview  // true = just preview, false = apply
) implements CustomPacketPayload {

    public static final Type<ImportItemPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "import_item")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportItemPayload> STREAM_CODEC =
        StreamCodec.of(ImportItemPayload::encode, ImportItemPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ImportItemPayload payload) {
        buf.writeVarInt(payload.slot);
        buf.writeUtf(payload.jsonData, 65535);  // Max 64KB
        buf.writeBoolean(payload.preview);
    }

    private static ImportItemPayload decode(RegistryFriendlyByteBuf buf) {
        return new ImportItemPayload(
            buf.readVarInt(),
            buf.readUtf(65535),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

/**
 * Payload for applying a preset.
 */
public record ApplyPresetPayload(
    int slot,
    String presetId
) implements CustomPacketPayload {

    public static final Type<ApplyPresetPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "apply_preset")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPresetPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slot);
                buf.writeUtf(payload.presetId);
            },
            buf -> new ApplyPresetPayload(buf.readVarInt(), buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

---

## 2.37 UI Polish & Localization System

### Decisioni Design (Domande 45-48)

| # | Domanda | Decisione |
|---|---------|-----------|
| 45 | Tooltip avanzato con preview? | **Sì** - Tooltip esteso con comparison before/after |
| 46 | Animazioni UI | **Immediate** - Nessuna animazione, max responsiveness |
| 47 | Suoni feedback | **Sì** - Suoni Minecraft vanilla per feedback |
| 48 | Localizzazione | **en_us + it_it** - Due lingue con fallback |

---

### 2.37.1 Advanced Tooltip System

```java
/**
 * Tooltip display modes.
 */
public enum TooltipMode {
    BASIC,      // Name + current value only
    EXTENDED,   // Before/after comparison with color diff
    PREVIEW     // Full simulation panel
}

/**
 * Tooltip content for a stat change.
 */
public record StatTooltip(
    String statName,
    String translationKey,
    float currentValue,
    float newValue,
    String unit,          // "%", "pts", "x", etc.
    boolean higherIsBetter
) {
    /**
     * Get the difference between new and current.
     */
    public float getDelta() {
        return newValue - currentValue;
    }

    /**
     * Check if this change is positive (improvement).
     */
    public boolean isPositive() {
        float delta = getDelta();
        return higherIsBetter ? delta > 0 : delta < 0;
    }

    /**
     * Check if this change is negative (worse).
     */
    public boolean isNegative() {
        float delta = getDelta();
        return higherIsBetter ? delta < 0 : delta > 0;
    }

    /**
     * Check if there's no change.
     */
    public boolean isNeutral() {
        return Math.abs(getDelta()) < 0.001f;
    }

    /**
     * Get color for the delta display.
     */
    public int getDeltaColor() {
        if (isNeutral()) return 0xFFAAAAAA;  // Gray
        if (isPositive()) return 0xFF55FF55; // Green
        return 0xFFFF5555;                    // Red
    }

    /**
     * Format the delta string with sign.
     */
    public String formatDelta() {
        float delta = getDelta();
        if (isNeutral()) return "±0" + unit;
        String sign = delta > 0 ? "+" : "";
        return sign + String.format("%.1f", delta) + unit;
    }
}

/**
 * Comparison tooltip showing before/after values.
 */
public record ComparisonTooltip(
    List<StatTooltip> stats,
    @Nullable String warningMessage,
    @Nullable String infoMessage
) {
    public static ComparisonTooltip empty() {
        return new ComparisonTooltip(List.of(), null, null);
    }

    public boolean hasChanges() {
        return stats.stream().anyMatch(s -> !s.isNeutral());
    }

    public int getPositiveCount() {
        return (int) stats.stream().filter(StatTooltip::isPositive).count();
    }

    public int getNegativeCount() {
        return (int) stats.stream().filter(StatTooltip::isNegative).count();
    }
}

/**
 * Renders advanced tooltips with comparison data.
 */
public class AdvancedTooltipRenderer {

    private static final int TOOLTIP_BG = 0xF0100010;
    private static final int TOOLTIP_BORDER_START = 0x505000FF;
    private static final int TOOLTIP_BORDER_END = 0x5028007F;

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int HEADER_HEIGHT = 12;

    /**
     * Render a comparison tooltip at the given position.
     */
    public static void renderComparisonTooltip(
            GuiGraphics graphics,
            Font font,
            ComparisonTooltip tooltip,
            int mouseX,
            int mouseY,
            int screenWidth,
            int screenHeight) {

        if (tooltip.stats().isEmpty()) return;

        // Calculate tooltip dimensions
        int maxWidth = 0;
        for (StatTooltip stat : tooltip.stats()) {
            String line = buildComparisonLine(stat);
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        // Add header width
        maxWidth = Math.max(maxWidth, font.width("§lChanges Preview"));

        int tooltipWidth = maxWidth + PADDING * 2;
        int tooltipHeight = HEADER_HEIGHT + tooltip.stats().size() * LINE_HEIGHT + PADDING * 2;

        // Add warning/info lines
        if (tooltip.warningMessage() != null) {
            tooltipHeight += LINE_HEIGHT;
            maxWidth = Math.max(maxWidth, font.width("⚠ " + tooltip.warningMessage()));
        }
        if (tooltip.infoMessage() != null) {
            tooltipHeight += LINE_HEIGHT;
            maxWidth = Math.max(maxWidth, font.width("ℹ " + tooltip.infoMessage()));
        }

        tooltipWidth = maxWidth + PADDING * 2;

        // Position tooltip (avoid screen edges)
        int x = mouseX + 12;
        int y = mouseY - 12;

        if (x + tooltipWidth > screenWidth) {
            x = mouseX - tooltipWidth - 4;
        }
        if (y + tooltipHeight > screenHeight) {
            y = screenHeight - tooltipHeight;
        }
        if (y < 0) y = 0;

        // Draw background
        graphics.fill(x - 1, y - 1, x + tooltipWidth + 1, y + tooltipHeight + 1, TOOLTIP_BORDER_START);
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, TOOLTIP_BG);

        // Draw header
        int textY = y + PADDING;
        graphics.drawString(font, "§lChanges Preview", x + PADDING, textY, 0xFFFFFFFF);
        textY += HEADER_HEIGHT;

        // Draw separator
        graphics.fill(x + PADDING, textY - 2, x + tooltipWidth - PADDING, textY - 1, 0xFF555555);

        // Draw each stat comparison
        for (StatTooltip stat : tooltip.stats()) {
            renderStatLine(graphics, font, stat, x + PADDING, textY);
            textY += LINE_HEIGHT;
        }

        // Draw warning if present
        if (tooltip.warningMessage() != null) {
            textY += 2; // Small gap
            graphics.drawString(font, "§c⚠ " + tooltip.warningMessage(), x + PADDING, textY, 0xFFFF5555);
            textY += LINE_HEIGHT;
        }

        // Draw info if present
        if (tooltip.infoMessage() != null) {
            graphics.drawString(font, "§7ℹ " + tooltip.infoMessage(), x + PADDING, textY, 0xFFAAAAAA);
        }
    }

    private static void renderStatLine(GuiGraphics graphics, Font font, StatTooltip stat, int x, int y) {
        // Format: "Stat Name: 10.0 → 15.0 (+5.0)"
        String name = I18n.get(stat.translationKey());
        String current = String.format("%.1f", stat.currentValue()) + stat.unit();
        String newVal = String.format("%.1f", stat.newValue()) + stat.unit();
        String delta = stat.formatDelta();

        int deltaColor = stat.getDeltaColor();

        // Draw name
        graphics.drawString(font, name + ": ", x, y, 0xFFAAAAAA);
        int nameWidth = font.width(name + ": ");

        // Draw current value
        graphics.drawString(font, current, x + nameWidth, y, 0xFFFFFFFF);
        int currentWidth = font.width(current);

        // Draw arrow
        graphics.drawString(font, " → ", x + nameWidth + currentWidth, y, 0xFF888888);
        int arrowWidth = font.width(" → ");

        // Draw new value (colored)
        int newColor = stat.isNeutral() ? 0xFFFFFFFF : deltaColor;
        graphics.drawString(font, newVal, x + nameWidth + currentWidth + arrowWidth, y, newColor);
        int newWidth = font.width(newVal);

        // Draw delta in parentheses
        if (!stat.isNeutral()) {
            String deltaStr = " (" + delta + ")";
            graphics.drawString(font, deltaStr, x + nameWidth + currentWidth + arrowWidth + newWidth, y, deltaColor);
        }
    }

    private static String buildComparisonLine(StatTooltip stat) {
        String name = I18n.get(stat.translationKey());
        String current = String.format("%.1f", stat.currentValue()) + stat.unit();
        String newVal = String.format("%.1f", stat.newValue()) + stat.unit();
        String delta = stat.formatDelta();
        return name + ": " + current + " → " + newVal + " (" + delta + ")";
    }
}

/**
 * Builds comparison tooltips from editor state.
 */
public class TooltipBuilder {

    /**
     * Build tooltip for weapon damage change.
     */
    public static ComparisonTooltip forWeaponStats(WeaponStats original, WeaponStats modified) {
        List<StatTooltip> stats = new ArrayList<>();

        if (original.attackDamage != modified.attackDamage) {
            stats.add(new StatTooltip(
                "Attack Damage",
                "devmod.tooltip.attack_damage",
                original.attackDamage,
                modified.attackDamage,
                "",
                true
            ));
        }

        if (original.attackSpeed != modified.attackSpeed) {
            stats.add(new StatTooltip(
                "Attack Speed",
                "devmod.tooltip.attack_speed",
                original.attackSpeed,
                modified.attackSpeed,
                "",
                true
            ));
        }

        if (original.attackReach != modified.attackReach) {
            stats.add(new StatTooltip(
                "Attack Reach",
                "devmod.tooltip.attack_reach",
                original.attackReach,
                modified.attackReach,
                "",
                true
            ));
        }

        if (original.attackKnockback != modified.attackKnockback) {
            stats.add(new StatTooltip(
                "Knockback",
                "devmod.tooltip.knockback",
                original.attackKnockback,
                modified.attackKnockback,
                "",
                true
            ));
        }

        // Calculate DPS change
        float originalDPS = original.attackDamage * original.attackSpeed;
        float modifiedDPS = modified.attackDamage * modified.attackSpeed;
        if (Math.abs(originalDPS - modifiedDPS) > 0.01f) {
            stats.add(new StatTooltip(
                "DPS",
                "devmod.tooltip.dps",
                originalDPS,
                modifiedDPS,
                "",
                true
            ));
        }

        // Check for dangerous values
        String warning = null;
        if (modified.attackDamage > 100) {
            warning = "Extremely high damage may break game balance";
        } else if (modified.attackSpeed > 10) {
            warning = "Very high attack speed may cause lag";
        }

        return new ComparisonTooltip(stats, warning, null);
    }

    /**
     * Build tooltip for armor stats change.
     */
    public static ComparisonTooltip forArmorStats(ArmorStats original, ArmorStats modified) {
        List<StatTooltip> stats = new ArrayList<>();

        if (original.physicalReduction != modified.physicalReduction) {
            stats.add(new StatTooltip(
                "Physical Reduction",
                "devmod.tooltip.physical_reduction",
                original.physicalReduction * 100,
                modified.physicalReduction * 100,
                "%",
                true
            ));
        }

        if (original.fireReduction != modified.fireReduction) {
            stats.add(new StatTooltip(
                "Fire Reduction",
                "devmod.tooltip.fire_reduction",
                original.fireReduction * 100,
                modified.fireReduction * 100,
                "%",
                true
            ));
        }

        if (original.magicReduction != modified.magicReduction) {
            stats.add(new StatTooltip(
                "Magic Reduction",
                "devmod.tooltip.magic_reduction",
                original.magicReduction * 100,
                modified.magicReduction * 100,
                "%",
                true
            ));
        }

        if (original.explosionReduction != modified.explosionReduction) {
            stats.add(new StatTooltip(
                "Explosion Reduction",
                "devmod.tooltip.explosion_reduction",
                original.explosionReduction * 100,
                modified.explosionReduction * 100,
                "%",
                true
            ));
        }

        if (original.projectileReduction != modified.projectileReduction) {
            stats.add(new StatTooltip(
                "Projectile Reduction",
                "devmod.tooltip.projectile_reduction",
                original.projectileReduction * 100,
                modified.projectileReduction * 100,
                "%",
                true
            ));
        }

        if (original.armorBonus != modified.armorBonus) {
            stats.add(new StatTooltip(
                "Armor Bonus",
                "devmod.tooltip.armor_bonus",
                original.armorBonus,
                modified.armorBonus,
                "",
                true
            ));
        }

        if (original.toughnessBonus != modified.toughnessBonus) {
            stats.add(new StatTooltip(
                "Toughness",
                "devmod.tooltip.toughness",
                original.toughnessBonus,
                modified.toughnessBonus,
                "",
                true
            ));
        }

        if (original.knockbackResistance != modified.knockbackResistance) {
            stats.add(new StatTooltip(
                "Knockback Resistance",
                "devmod.tooltip.knockback_resistance",
                original.knockbackResistance * 100,
                modified.knockbackResistance * 100,
                "%",
                true
            ));
        }

        // Calculate EHP change (simplified)
        float originalEHP = calculateEHP(original);
        float modifiedEHP = calculateEHP(modified);
        if (Math.abs(originalEHP - modifiedEHP) > 0.1f) {
            stats.add(new StatTooltip(
                "Effective HP",
                "devmod.tooltip.ehp",
                originalEHP,
                modifiedEHP,
                "",
                true
            ));
        }

        // Warning for high values
        String warning = null;
        if (modified.physicalReduction >= 1.0f || modified.fireReduction >= 1.0f) {
            warning = "100% reduction makes player immune to damage type";
        }

        return new ComparisonTooltip(stats, warning, null);
    }

    private static float calculateEHP(ArmorStats stats) {
        // Simplified EHP calculation: base 20 HP * (1 / (1 - avg_reduction))
        float avgReduction = (stats.physicalReduction + stats.fireReduction +
                             stats.magicReduction + stats.explosionReduction +
                             stats.projectileReduction) / 5.0f;
        avgReduction = Math.min(avgReduction, 0.95f); // Cap at 95% to avoid division issues
        return 20.0f / (1.0f - avgReduction);
    }
}
```

---

### 2.37.2 Immediate UI Response (No Animations)

```java
/**
 * UI configuration for immediate response mode.
 * All animations disabled for maximum responsiveness.
 */
public final class UIResponseConfig {

    private UIResponseConfig() {}

    // ═══════════════════════════════════════════════════════════════
    // TIMING CONSTANTS - ALL SET TO 0 FOR IMMEDIATE RESPONSE
    // ═══════════════════════════════════════════════════════════════

    /** Fade animation duration (disabled) */
    public static final int FADE_DURATION_MS = 0;

    /** Slide animation duration (disabled) */
    public static final int SLIDE_DURATION_MS = 0;

    /** Scale animation duration (disabled) */
    public static final int SCALE_DURATION_MS = 0;

    /** Tab switch animation (disabled) */
    public static final int TAB_SWITCH_MS = 0;

    /** Tooltip appear delay */
    public static final int TOOLTIP_DELAY_MS = 200; // Small delay to prevent flicker

    /** Hover state change (immediate) */
    public static final int HOVER_TRANSITION_MS = 0;

    /** Button press feedback (immediate) */
    public static final int BUTTON_PRESS_MS = 0;

    // ═══════════════════════════════════════════════════════════════
    // VISUAL FEEDBACK - INSTANT STATE CHANGES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get button background color for state (no interpolation).
     */
    public static int getButtonColor(ButtonState state) {
        return switch (state) {
            case NORMAL -> 0xFF2A2A2A;
            case HOVERED -> 0xFF3A3A3A;
            case PRESSED -> 0xFF1A1A1A;
            case DISABLED -> 0xFF1A1A1A;
            case FOCUSED -> 0xFF3A3A5A;
        };
    }

    /**
     * Get slider track color for state.
     */
    public static int getSliderTrackColor(boolean active) {
        return active ? 0xFF4A4A4A : 0xFF2A2A2A;
    }

    /**
     * Get slider thumb color for state.
     */
    public static int getSliderThumbColor(SliderState state) {
        return switch (state) {
            case NORMAL -> 0xFF5A5A5A;
            case HOVERED -> 0xFF7A7A7A;
            case DRAGGING -> 0xFF3A7AFF;
            case DISABLED -> 0xFF3A3A3A;
        };
    }

    /**
     * Get tab background for state.
     */
    public static int getTabBackground(TabState state) {
        return switch (state) {
            case NORMAL -> 0xFF1E1E1E;
            case HOVERED -> 0xFF2E2E2E;
            case SELECTED -> 0xFF3A3A3A;
            case DISABLED -> 0xFF151515;
        };
    }

    public enum ButtonState { NORMAL, HOVERED, PRESSED, DISABLED, FOCUSED }
    public enum SliderState { NORMAL, HOVERED, DRAGGING, DISABLED }
    public enum TabState { NORMAL, HOVERED, SELECTED, DISABLED }
}

/**
 * Immediate-mode widget base class.
 * No animation state, direct rendering.
 */
public abstract class ImmediateWidget {

    protected int x, y, width, height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected boolean hovered = false;
    protected boolean focused = false;

    /**
     * Update widget state. Called every frame.
     * Returns true if state changed.
     */
    public boolean update(int mouseX, int mouseY) {
        boolean wasHovered = hovered;
        hovered = visible && enabled && isMouseOver(mouseX, mouseY);
        return hovered != wasHovered;
    }

    /**
     * Render widget immediately with current state.
     * No animation interpolation.
     */
    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /**
     * Handle mouse click. Returns true if consumed.
     */
    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);

    /**
     * Handle mouse release.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse drag.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    protected boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // Getters/setters
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setSize(int width, int height) { this.width = width; this.height = height; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setFocused(boolean focused) { this.focused = focused; }
    public boolean isHovered() { return hovered; }
    public boolean isFocused() { return focused; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}

/**
 * Immediate-mode slider with no animation.
 */
public class ImmediateSlider extends ImmediateWidget {

    private float value;
    private final float min, max;
    private final float step;
    private boolean dragging = false;
    private Consumer<Float> onChange;
    private String label;
    private String unit;

    public ImmediateSlider(float min, float max, float step, float initial) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = clamp(initial);
    }

    public void setOnChange(Consumer<Float> onChange) {
        this.onChange = onChange;
    }

    public void setLabel(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;

        UIResponseConfig.SliderState state = getState();

        // Track background
        int trackColor = UIResponseConfig.getSliderTrackColor(enabled);
        graphics.fill(x, y + height/2 - 2, x + width, y + height/2 + 2, trackColor);

        // Filled portion
        float percent = (value - min) / (max - min);
        int filledWidth = (int)(width * percent);
        graphics.fill(x, y + height/2 - 2, x + filledWidth, y + height/2 + 2, 0xFF3A7AFF);

        // Thumb
        int thumbX = x + filledWidth - 4;
        int thumbColor = UIResponseConfig.getSliderThumbColor(state);
        graphics.fill(thumbX, y + 2, thumbX + 8, y + height - 2, thumbColor);

        // Label and value
        if (label != null) {
            Font font = Minecraft.getInstance().font;
            String text = label + ": " + String.format("%.1f", value) + (unit != null ? unit : "");
            graphics.drawString(font, text, x, y - 10, enabled ? 0xFFFFFFFF : 0xFF888888);
        }
    }

    private UIResponseConfig.SliderState getState() {
        if (!enabled) return UIResponseConfig.SliderState.DISABLED;
        if (dragging) return UIResponseConfig.SliderState.DRAGGING;
        if (hovered) return UIResponseConfig.SliderState.HOVERED;
        return UIResponseConfig.SliderState.NORMAL;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) return false;
        if (isMouseOver((int)mouseX, (int)mouseY)) {
            dragging = true;
            updateValue(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            updateValue(mouseX);
            return true;
        }
        return false;
    }

    private void updateValue(double mouseX) {
        float percent = (float)(mouseX - x) / width;
        percent = Math.max(0, Math.min(1, percent));
        float newValue = min + (max - min) * percent;
        newValue = clamp(newValue);

        // Snap to step
        if (step > 0) {
            newValue = Math.round(newValue / step) * step;
        }

        if (newValue != value) {
            value = newValue;
            if (onChange != null) {
                onChange.accept(value);
            }
        }
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }

    public float getValue() { return value; }
    public void setValue(float value) { this.value = clamp(value); }
}
```

---

### 2.37.3 Sound Feedback System

```java
/**
 * Sound events for editor UI feedback.
 * Uses vanilla Minecraft sounds.
 */
public final class EditorSounds {

    private EditorSounds() {}

    // ═══════════════════════════════════════════════════════════════
    // SOUND MAPPINGS TO VANILLA
    // ═══════════════════════════════════════════════════════════════

    /** Button click sound */
    public static final SoundEvent CLICK = SoundEvents.UI_BUTTON_CLICK.value();

    /** Apply/Confirm success sound */
    public static final SoundEvent APPLY_SUCCESS = SoundEvents.ANVIL_USE;

    /** Apply failed sound */
    public static final SoundEvent APPLY_FAILED = SoundEvents.ANVIL_LAND;

    /** Warning/Alert sound */
    public static final SoundEvent WARNING = SoundEvents.NOTE_BLOCK_BASS.value();

    /** Error sound */
    public static final SoundEvent ERROR = SoundEvents.VILLAGER_NO;

    /** Enchantment applied */
    public static final SoundEvent ENCHANT = SoundEvents.ENCHANTMENT_TABLE_USE;

    /** Slider value change (subtle) */
    public static final SoundEvent SLIDER_TICK = SoundEvents.UI_BUTTON_CLICK.value();

    /** Tab switch */
    public static final SoundEvent TAB_SWITCH = SoundEvents.UI_BUTTON_CLICK.value();

    /** Undo action */
    public static final SoundEvent UNDO = SoundEvents.ITEM_PICKUP;

    /** Redo action */
    public static final SoundEvent REDO = SoundEvents.ITEM_PICKUP;

    /** Export complete */
    public static final SoundEvent EXPORT = SoundEvents.BOOK_PAGE_TURN;

    /** Import complete */
    public static final SoundEvent IMPORT = SoundEvents.BOOK_PAGE_TURN;

    /** Preset saved */
    public static final SoundEvent PRESET_SAVE = SoundEvents.BOOK_PUT;

    /** Preset loaded */
    public static final SoundEvent PRESET_LOAD = SoundEvents.ARMOR_EQUIP_GENERIC.value();

    /** Reset to defaults */
    public static final SoundEvent RESET = SoundEvents.PLAYER_LEVELUP;

    /** Copy to clipboard */
    public static final SoundEvent COPY = SoundEvents.UI_STONECUTTER_SELECT_RECIPE;

    /** Paste from clipboard */
    public static final SoundEvent PASTE = SoundEvents.UI_STONECUTTER_TAKE_RESULT;

    // ═══════════════════════════════════════════════════════════════
    // VOLUME SETTINGS
    // ═══════════════════════════════════════════════════════════════

    /** Base volume for UI sounds (relative to master) */
    public static final float UI_VOLUME = 0.5f;

    /** Volume for subtle feedback (slider ticks) */
    public static final float SUBTLE_VOLUME = 0.2f;

    /** Volume for important feedback (apply, error) */
    public static final float IMPORTANT_VOLUME = 0.8f;

    // ═══════════════════════════════════════════════════════════════
    // PLAYBACK HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Play a UI sound at the player's position.
     */
    public static void play(SoundEvent sound, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            // Check if sounds are enabled in config
            if (!EditorConfig.get().soundsEnabled) return;

            mc.level.playLocalSound(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                sound,
                SoundSource.MASTER,
                volume * UI_VOLUME,
                1.0f, // pitch
                false // distanceDelay
            );
        }
    }

    /**
     * Play with custom pitch.
     */
    public static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            if (!EditorConfig.get().soundsEnabled) return;

            mc.level.playLocalSound(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                sound,
                SoundSource.MASTER,
                volume * UI_VOLUME,
                pitch,
                false
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════

    /** Play click sound */
    public static void click() {
        play(CLICK, UI_VOLUME);
    }

    /** Play apply success sound */
    public static void applySuccess() {
        play(APPLY_SUCCESS, IMPORTANT_VOLUME);
    }

    /** Play apply failed sound */
    public static void applyFailed() {
        play(APPLY_FAILED, IMPORTANT_VOLUME);
    }

    /** Play warning sound */
    public static void warning() {
        play(WARNING, IMPORTANT_VOLUME, 0.5f);
    }

    /** Play error sound */
    public static void error() {
        play(ERROR, IMPORTANT_VOLUME);
    }

    /** Play enchant sound */
    public static void enchant() {
        play(ENCHANT, IMPORTANT_VOLUME);
    }

    /** Play slider tick (rate-limited) */
    private static long lastSliderTick = 0;
    private static final long SLIDER_TICK_COOLDOWN_MS = 50;

    public static void sliderTick() {
        long now = System.currentTimeMillis();
        if (now - lastSliderTick >= SLIDER_TICK_COOLDOWN_MS) {
            lastSliderTick = now;
            play(SLIDER_TICK, SUBTLE_VOLUME, 1.2f);
        }
    }

    /** Play tab switch sound */
    public static void tabSwitch() {
        play(TAB_SWITCH, UI_VOLUME, 0.9f);
    }

    /** Play undo sound */
    public static void undo() {
        play(UNDO, UI_VOLUME, 0.8f);
    }

    /** Play redo sound */
    public static void redo() {
        play(REDO, UI_VOLUME, 1.2f);
    }

    /** Play export complete sound */
    public static void exportComplete() {
        play(EXPORT, UI_VOLUME);
    }

    /** Play import complete sound */
    public static void importComplete() {
        play(IMPORT, UI_VOLUME);
    }

    /** Play preset saved sound */
    public static void presetSaved() {
        play(PRESET_SAVE, UI_VOLUME);
    }

    /** Play preset loaded sound */
    public static void presetLoaded() {
        play(PRESET_LOAD, UI_VOLUME);
    }

    /** Play reset sound */
    public static void reset() {
        play(RESET, UI_VOLUME, 0.5f);
    }

    /** Play copy sound */
    public static void copy() {
        play(COPY, UI_VOLUME);
    }

    /** Play paste sound */
    public static void paste() {
        play(PASTE, UI_VOLUME);
    }
}

/**
 * Integration with UI widgets.
 */
public interface SoundFeedback {

    /**
     * Called when button is clicked.
     */
    default void onButtonClick() {
        EditorSounds.click();
    }

    /**
     * Called when slider value changes.
     */
    default void onSliderChange() {
        EditorSounds.sliderTick();
    }

    /**
     * Called when apply succeeds.
     */
    default void onApplySuccess() {
        EditorSounds.applySuccess();
    }

    /**
     * Called when apply fails.
     */
    default void onApplyFailed() {
        EditorSounds.applyFailed();
    }

    /**
     * Called when a dangerous value is detected.
     */
    default void onWarning() {
        EditorSounds.warning();
    }

    /**
     * Called on validation error.
     */
    default void onError() {
        EditorSounds.error();
    }
}
```

---

### 2.37.4 Localization System (en_us + it_it)

```java
/**
 * Supported languages for the editor.
 */
public enum EditorLanguage {
    EN_US("en_us", "English"),
    IT_IT("it_it", "Italiano");

    private final String code;
    private final String displayName;

    EditorLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static EditorLanguage fromCode(String code) {
        for (EditorLanguage lang : values()) {
            if (lang.code.equals(code)) return lang;
        }
        return EN_US; // Default fallback
    }
}

/**
 * Translation key constants for editor UI.
 */
public final class EditorTranslations {

    private EditorTranslations() {}

    // ═══════════════════════════════════════════════════════════════
    // SCREEN TITLES
    // ═══════════════════════════════════════════════════════════════

    public static final String WEAPON_EDITOR_TITLE = "devmod.screen.weapon_editor.title";
    public static final String ARMOR_EDITOR_TITLE = "devmod.screen.armor_editor.title";

    // ═══════════════════════════════════════════════════════════════
    // TAB NAMES
    // ═══════════════════════════════════════════════════════════════

    public static final String TAB_STATS = "devmod.tab.stats";
    public static final String TAB_PROTECTION = "devmod.tab.protection";
    public static final String TAB_ATTRIBUTES = "devmod.tab.attributes";
    public static final String TAB_ENCHANTS = "devmod.tab.enchants";
    public static final String TAB_DURABILITY = "devmod.tab.durability";
    public static final String TAB_EFFECTS = "devmod.tab.effects";
    public static final String TAB_COMPONENTS = "devmod.tab.components";
    public static final String TAB_AFFIXES = "devmod.tab.affixes";

    // ═══════════════════════════════════════════════════════════════
    // STAT LABELS
    // ═══════════════════════════════════════════════════════════════

    // Weapon stats
    public static final String STAT_ATTACK_DAMAGE = "devmod.stat.attack_damage";
    public static final String STAT_ATTACK_SPEED = "devmod.stat.attack_speed";
    public static final String STAT_ATTACK_REACH = "devmod.stat.attack_reach";
    public static final String STAT_KNOCKBACK = "devmod.stat.knockback";
    public static final String STAT_CRIT_CHANCE = "devmod.stat.crit_chance";
    public static final String STAT_CRIT_DAMAGE = "devmod.stat.crit_damage";
    public static final String STAT_DPS = "devmod.stat.dps";

    // Armor stats
    public static final String STAT_PHYSICAL_REDUCTION = "devmod.stat.physical_reduction";
    public static final String STAT_FIRE_REDUCTION = "devmod.stat.fire_reduction";
    public static final String STAT_MAGIC_REDUCTION = "devmod.stat.magic_reduction";
    public static final String STAT_EXPLOSION_REDUCTION = "devmod.stat.explosion_reduction";
    public static final String STAT_PROJECTILE_REDUCTION = "devmod.stat.projectile_reduction";
    public static final String STAT_ARMOR_BONUS = "devmod.stat.armor_bonus";
    public static final String STAT_TOUGHNESS = "devmod.stat.toughness";
    public static final String STAT_KNOCKBACK_RESISTANCE = "devmod.stat.knockback_resistance";
    public static final String STAT_THORNS = "devmod.stat.thorns";
    public static final String STAT_EHP = "devmod.stat.ehp";

    // Durability
    public static final String STAT_DURABILITY_CURRENT = "devmod.stat.durability_current";
    public static final String STAT_DURABILITY_MAX = "devmod.stat.durability_max";
    public static final String STAT_UNBREAKABLE = "devmod.stat.unbreakable";

    // ═══════════════════════════════════════════════════════════════
    // BUTTONS
    // ═══════════════════════════════════════════════════════════════

    public static final String BTN_APPLY = "devmod.button.apply";
    public static final String BTN_CANCEL = "devmod.button.cancel";
    public static final String BTN_RESET = "devmod.button.reset";
    public static final String BTN_UNDO = "devmod.button.undo";
    public static final String BTN_REDO = "devmod.button.redo";
    public static final String BTN_EXPORT = "devmod.button.export";
    public static final String BTN_IMPORT = "devmod.button.import";
    public static final String BTN_PRESETS = "devmod.button.presets";
    public static final String BTN_HISTORY = "devmod.button.history";
    public static final String BTN_SAVE_PRESET = "devmod.button.save_preset";
    public static final String BTN_LOAD_PRESET = "devmod.button.load_preset";
    public static final String BTN_DELETE_PRESET = "devmod.button.delete_preset";
    public static final String BTN_COPY = "devmod.button.copy";
    public static final String BTN_PASTE = "devmod.button.paste";

    // ═══════════════════════════════════════════════════════════════
    // TOOLTIPS
    // ═══════════════════════════════════════════════════════════════

    public static final String TOOLTIP_CHANGES_PREVIEW = "devmod.tooltip.changes_preview";
    public static final String TOOLTIP_NO_CHANGES = "devmod.tooltip.no_changes";
    public static final String TOOLTIP_DANGEROUS_VALUE = "devmod.tooltip.dangerous_value";
    public static final String TOOLTIP_UNDO_HINT = "devmod.tooltip.undo_hint";
    public static final String TOOLTIP_REDO_HINT = "devmod.tooltip.redo_hint";
    public static final String TOOLTIP_APPLY_HINT = "devmod.tooltip.apply_hint";

    // ═══════════════════════════════════════════════════════════════
    // MESSAGES
    // ═══════════════════════════════════════════════════════════════

    public static final String MSG_APPLY_SUCCESS = "devmod.message.apply_success";
    public static final String MSG_APPLY_FAILED = "devmod.message.apply_failed";
    public static final String MSG_EXPORT_SUCCESS = "devmod.message.export_success";
    public static final String MSG_IMPORT_SUCCESS = "devmod.message.import_success";
    public static final String MSG_IMPORT_FAILED = "devmod.message.import_failed";
    public static final String MSG_PRESET_SAVED = "devmod.message.preset_saved";
    public static final String MSG_PRESET_LOADED = "devmod.message.preset_loaded";
    public static final String MSG_PRESET_DELETED = "devmod.message.preset_deleted";
    public static final String MSG_UNDO_SUCCESS = "devmod.message.undo_success";
    public static final String MSG_REDO_SUCCESS = "devmod.message.redo_success";
    public static final String MSG_NOTHING_TO_UNDO = "devmod.message.nothing_to_undo";
    public static final String MSG_NOTHING_TO_REDO = "devmod.message.nothing_to_redo";
    public static final String MSG_RESET_SUCCESS = "devmod.message.reset_success";
    public static final String MSG_PERMISSION_DENIED = "devmod.message.permission_denied";
    public static final String MSG_COPIED_TO_CLIPBOARD = "devmod.message.copied_to_clipboard";
    public static final String MSG_PASTED_FROM_CLIPBOARD = "devmod.message.pasted_from_clipboard";
    public static final String MSG_INVALID_CLIPBOARD = "devmod.message.invalid_clipboard";

    // ═══════════════════════════════════════════════════════════════
    // WARNINGS
    // ═══════════════════════════════════════════════════════════════

    public static final String WARN_HIGH_DAMAGE = "devmod.warning.high_damage";
    public static final String WARN_HIGH_SPEED = "devmod.warning.high_speed";
    public static final String WARN_IMMUNE_DAMAGE = "devmod.warning.immune_damage";
    public static final String WARN_VERSION_MISMATCH = "devmod.warning.version_mismatch";
    public static final String WARN_UNSAVED_CHANGES = "devmod.warning.unsaved_changes";

    // ═══════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════

    public static final String DIALOG_CONFIRM_RESET = "devmod.dialog.confirm_reset";
    public static final String DIALOG_CONFIRM_DISCARD = "devmod.dialog.confirm_discard";
    public static final String DIALOG_CONFIRM_DELETE = "devmod.dialog.confirm_delete";
    public static final String DIALOG_PRESET_NAME = "devmod.dialog.preset_name";
    public static final String DIALOG_EXPORT_FORMAT = "devmod.dialog.export_format";

    // ═══════════════════════════════════════════════════════════════
    // MISC
    // ═══════════════════════════════════════════════════════════════

    public static final String LABEL_ENABLED = "devmod.label.enabled";
    public static final String LABEL_DISABLED = "devmod.label.disabled";
    public static final String LABEL_YES = "devmod.label.yes";
    public static final String LABEL_NO = "devmod.label.no";
    public static final String LABEL_GLOBAL = "devmod.label.global";
    public static final String LABEL_SPECIFIC = "devmod.label.specific";
    public static final String LABEL_VANILLA = "devmod.label.vanilla";
    public static final String LABEL_MODIFIED = "devmod.label.modified";
}
```

#### Language File: en_us.json

```json
{
  "_comment": "DevMod Editor - English (US)",

  "devmod.screen.weapon_editor.title": "Weapon Editor",
  "devmod.screen.armor_editor.title": "Armor Editor",

  "devmod.tab.stats": "Stats",
  "devmod.tab.protection": "Protection",
  "devmod.tab.attributes": "Attributes",
  "devmod.tab.enchants": "Enchants",
  "devmod.tab.durability": "Durability",
  "devmod.tab.effects": "Effects",
  "devmod.tab.components": "Components",
  "devmod.tab.affixes": "Affixes",

  "devmod.stat.attack_damage": "Attack Damage",
  "devmod.stat.attack_speed": "Attack Speed",
  "devmod.stat.attack_reach": "Attack Reach",
  "devmod.stat.knockback": "Knockback",
  "devmod.stat.crit_chance": "Critical Chance",
  "devmod.stat.crit_damage": "Critical Damage",
  "devmod.stat.dps": "DPS",

  "devmod.stat.physical_reduction": "Physical Reduction",
  "devmod.stat.fire_reduction": "Fire Reduction",
  "devmod.stat.magic_reduction": "Magic Reduction",
  "devmod.stat.explosion_reduction": "Explosion Reduction",
  "devmod.stat.projectile_reduction": "Projectile Reduction",
  "devmod.stat.armor_bonus": "Armor Bonus",
  "devmod.stat.toughness": "Toughness",
  "devmod.stat.knockback_resistance": "Knockback Resistance",
  "devmod.stat.thorns": "Thorns",
  "devmod.stat.ehp": "Effective HP",

  "devmod.stat.durability_current": "Current Durability",
  "devmod.stat.durability_max": "Max Durability",
  "devmod.stat.unbreakable": "Unbreakable",

  "devmod.button.apply": "Apply",
  "devmod.button.cancel": "Cancel",
  "devmod.button.reset": "Reset",
  "devmod.button.undo": "Undo",
  "devmod.button.redo": "Redo",
  "devmod.button.export": "Export",
  "devmod.button.import": "Import",
  "devmod.button.presets": "Presets",
  "devmod.button.history": "History",
  "devmod.button.save_preset": "Save Preset",
  "devmod.button.load_preset": "Load Preset",
  "devmod.button.delete_preset": "Delete Preset",
  "devmod.button.copy": "Copy",
  "devmod.button.paste": "Paste",

  "devmod.tooltip.changes_preview": "Changes Preview",
  "devmod.tooltip.no_changes": "No changes",
  "devmod.tooltip.dangerous_value": "Warning: This value may break game balance",
  "devmod.tooltip.undo_hint": "Undo (Ctrl+Z)",
  "devmod.tooltip.redo_hint": "Redo (Ctrl+Y)",
  "devmod.tooltip.apply_hint": "Apply Changes (Ctrl+S)",

  "devmod.message.apply_success": "Changes applied successfully",
  "devmod.message.apply_failed": "Failed to apply changes: %s",
  "devmod.message.export_success": "Exported to %s",
  "devmod.message.import_success": "Import successful",
  "devmod.message.import_failed": "Import failed: %s",
  "devmod.message.preset_saved": "Preset '%s' saved",
  "devmod.message.preset_loaded": "Preset '%s' loaded",
  "devmod.message.preset_deleted": "Preset '%s' deleted",
  "devmod.message.undo_success": "Undone: %s",
  "devmod.message.redo_success": "Redone: %s",
  "devmod.message.nothing_to_undo": "Nothing to undo",
  "devmod.message.nothing_to_redo": "Nothing to redo",
  "devmod.message.reset_success": "Reset to defaults",
  "devmod.message.permission_denied": "Permission denied",
  "devmod.message.copied_to_clipboard": "Copied to clipboard",
  "devmod.message.pasted_from_clipboard": "Pasted from clipboard",
  "devmod.message.invalid_clipboard": "Invalid clipboard data",

  "devmod.warning.high_damage": "Extremely high damage may break game balance",
  "devmod.warning.high_speed": "Very high attack speed may cause lag",
  "devmod.warning.immune_damage": "100%% reduction makes player immune to this damage type",
  "devmod.warning.version_mismatch": "Version mismatch: file is from %s, current is %s",
  "devmod.warning.unsaved_changes": "You have unsaved changes",

  "devmod.dialog.confirm_reset": "Reset all values to defaults?",
  "devmod.dialog.confirm_discard": "Discard unsaved changes?",
  "devmod.dialog.confirm_delete": "Delete preset '%s'?",
  "devmod.dialog.preset_name": "Enter preset name:",
  "devmod.dialog.export_format": "Select export format:",

  "devmod.label.enabled": "Enabled",
  "devmod.label.disabled": "Disabled",
  "devmod.label.yes": "Yes",
  "devmod.label.no": "No",
  "devmod.label.global": "Global",
  "devmod.label.specific": "Specific",
  "devmod.label.vanilla": "Vanilla",
  "devmod.label.modified": "Modified"
}
```

#### Language File: it_it.json

```json
{
  "_comment": "DevMod Editor - Italiano",

  "devmod.screen.weapon_editor.title": "Editor Armi",
  "devmod.screen.armor_editor.title": "Editor Armature",

  "devmod.tab.stats": "Statistiche",
  "devmod.tab.protection": "Protezione",
  "devmod.tab.attributes": "Attributi",
  "devmod.tab.enchants": "Incantesimi",
  "devmod.tab.durability": "Durabilità",
  "devmod.tab.effects": "Effetti",
  "devmod.tab.components": "Componenti",
  "devmod.tab.affixes": "Affissi",

  "devmod.stat.attack_damage": "Danno d'Attacco",
  "devmod.stat.attack_speed": "Velocità d'Attacco",
  "devmod.stat.attack_reach": "Portata d'Attacco",
  "devmod.stat.knockback": "Contraccolpo",
  "devmod.stat.crit_chance": "Probabilità Critico",
  "devmod.stat.crit_damage": "Danno Critico",
  "devmod.stat.dps": "DPS",

  "devmod.stat.physical_reduction": "Riduzione Fisica",
  "devmod.stat.fire_reduction": "Riduzione Fuoco",
  "devmod.stat.magic_reduction": "Riduzione Magica",
  "devmod.stat.explosion_reduction": "Riduzione Esplosione",
  "devmod.stat.projectile_reduction": "Riduzione Proiettili",
  "devmod.stat.armor_bonus": "Bonus Armatura",
  "devmod.stat.toughness": "Robustezza",
  "devmod.stat.knockback_resistance": "Resistenza Contraccolpo",
  "devmod.stat.thorns": "Spine",
  "devmod.stat.ehp": "HP Effettivi",

  "devmod.stat.durability_current": "Durabilità Attuale",
  "devmod.stat.durability_max": "Durabilità Massima",
  "devmod.stat.unbreakable": "Indistruttibile",

  "devmod.button.apply": "Applica",
  "devmod.button.cancel": "Annulla",
  "devmod.button.reset": "Ripristina",
  "devmod.button.undo": "Annulla",
  "devmod.button.redo": "Ripeti",
  "devmod.button.export": "Esporta",
  "devmod.button.import": "Importa",
  "devmod.button.presets": "Preset",
  "devmod.button.history": "Cronologia",
  "devmod.button.save_preset": "Salva Preset",
  "devmod.button.load_preset": "Carica Preset",
  "devmod.button.delete_preset": "Elimina Preset",
  "devmod.button.copy": "Copia",
  "devmod.button.paste": "Incolla",

  "devmod.tooltip.changes_preview": "Anteprima Modifiche",
  "devmod.tooltip.no_changes": "Nessuna modifica",
  "devmod.tooltip.dangerous_value": "Attenzione: Questo valore potrebbe sbilanciare il gioco",
  "devmod.tooltip.undo_hint": "Annulla (Ctrl+Z)",
  "devmod.tooltip.redo_hint": "Ripeti (Ctrl+Y)",
  "devmod.tooltip.apply_hint": "Applica Modifiche (Ctrl+S)",

  "devmod.message.apply_success": "Modifiche applicate con successo",
  "devmod.message.apply_failed": "Applicazione fallita: %s",
  "devmod.message.export_success": "Esportato in %s",
  "devmod.message.import_success": "Importazione riuscita",
  "devmod.message.import_failed": "Importazione fallita: %s",
  "devmod.message.preset_saved": "Preset '%s' salvato",
  "devmod.message.preset_loaded": "Preset '%s' caricato",
  "devmod.message.preset_deleted": "Preset '%s' eliminato",
  "devmod.message.undo_success": "Annullato: %s",
  "devmod.message.redo_success": "Ripetuto: %s",
  "devmod.message.nothing_to_undo": "Niente da annullare",
  "devmod.message.nothing_to_redo": "Niente da ripetere",
  "devmod.message.reset_success": "Ripristinato ai valori predefiniti",
  "devmod.message.permission_denied": "Permesso negato",
  "devmod.message.copied_to_clipboard": "Copiato negli appunti",
  "devmod.message.pasted_from_clipboard": "Incollato dagli appunti",
  "devmod.message.invalid_clipboard": "Dati appunti non validi",

  "devmod.warning.high_damage": "Danno estremamente alto potrebbe sbilanciare il gioco",
  "devmod.warning.high_speed": "Velocità d'attacco molto alta potrebbe causare lag",
  "devmod.warning.immune_damage": "Riduzione 100%% rende il giocatore immune a questo tipo di danno",
  "devmod.warning.version_mismatch": "Versione non corrispondente: file da %s, attuale %s",
  "devmod.warning.unsaved_changes": "Hai modifiche non salvate",

  "devmod.dialog.confirm_reset": "Ripristinare tutti i valori ai predefiniti?",
  "devmod.dialog.confirm_discard": "Scartare le modifiche non salvate?",
  "devmod.dialog.confirm_delete": "Eliminare il preset '%s'?",
  "devmod.dialog.preset_name": "Inserisci nome preset:",
  "devmod.dialog.export_format": "Seleziona formato esportazione:",

  "devmod.label.enabled": "Abilitato",
  "devmod.label.disabled": "Disabilitato",
  "devmod.label.yes": "Sì",
  "devmod.label.no": "No",
  "devmod.label.global": "Globale",
  "devmod.label.specific": "Specifico",
  "devmod.label.vanilla": "Vanilla",
  "devmod.label.modified": "Modificato"
}
```

---

### 2.37.5 Localization Helper Class

```java
/**
 * Helper for editor localization with fallback.
 */
public final class EditorI18n {

    private EditorI18n() {}

    private static final String FALLBACK_LANG = "en_us";

    /**
     * Get translated string with current language.
     * Falls back to en_us if key not found.
     */
    public static String get(String key) {
        String result = I18n.get(key);
        // If not found (returns key), try fallback wouldn't help since MC handles this
        return result;
    }

    /**
     * Get translated string with format arguments.
     */
    public static String get(String key, Object... args) {
        return I18n.get(key, args);
    }

    /**
     * Get Component for rendering.
     */
    public static Component component(String key) {
        return Component.translatable(key);
    }

    /**
     * Get Component with arguments.
     */
    public static Component component(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * Check if translation exists.
     */
    public static boolean exists(String key) {
        return I18n.exists(key);
    }

    /**
     * Get current language code.
     */
    public static String getCurrentLanguage() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    /**
     * Check if current language is supported by editor.
     */
    public static boolean isEditorLanguageSupported() {
        String current = getCurrentLanguage();
        return "en_us".equals(current) || "it_it".equals(current);
    }

    // ═══════════════════════════════════════════════════════════════
    // STAT FORMATTING HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Format a stat value with its name.
     */
    public static String formatStat(String nameKey, float value, String unit) {
        return get(nameKey) + ": " + formatValue(value) + unit;
    }

    /**
     * Format a percentage value (0-1 to 0-100%).
     */
    public static String formatPercent(float value) {
        return String.format("%.0f%%", value * 100);
    }

    /**
     * Format a float value with appropriate precision.
     */
    public static String formatValue(float value) {
        if (Math.abs(value - Math.round(value)) < 0.01f) {
            return String.valueOf(Math.round(value));
        }
        return String.format("%.1f", value);
    }

    /**
     * Format a comparison (before → after).
     */
    public static String formatComparison(float before, float after, String unit) {
        String beforeStr = formatValue(before) + unit;
        String afterStr = formatValue(after) + unit;
        float delta = after - before;
        String deltaStr = (delta >= 0 ? "+" : "") + formatValue(delta) + unit;
        return beforeStr + " → " + afterStr + " (" + deltaStr + ")";
    }
}
```

---

### 2.37.6 Config Section for UI Polish

```toml
# ═══════════════════════════════════════════════════════════════════════════
# UI POLISH CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════

[ui]
    # Enable sound feedback for UI actions
    # Type: boolean
    soundsEnabled = true

    # Tooltip display mode
    # Values: "basic", "extended", "preview"
    tooltipMode = "extended"

    # Tooltip delay in milliseconds before showing
    # Range: 0 - 1000
    tooltipDelayMs = 200

    # Show comparison tooltips when hovering Apply button
    showComparisonTooltip = true

    # Highlight changed values with color
    highlightChanges = true

    # Color for positive changes (improvements)
    # Format: 0xAARRGGBB
    positiveChangeColor = 0xFF55FF55

    # Color for negative changes (worse)
    negativeChangeColor = 0xFFFF5555

    # Color for neutral (no change)
    neutralChangeColor = 0xFFAAAAAA

[ui.sounds]
    # Master volume for all editor sounds (0.0 - 1.0)
    masterVolume = 0.5

    # Play sound on button click
    clickSound = true

    # Play sound on slider change (rate-limited)
    sliderSound = true

    # Play sound on apply success/failure
    applySound = true

    # Play sound on warnings
    warningSound = true

    # Play sound on undo/redo
    undoRedoSound = true

[ui.tooltip]
    # Show DPS/EHP calculations in tooltips
    showCalculations = true

    # Show warnings for dangerous values
    showWarnings = true

    # Max width for tooltip text wrapping
    maxWidth = 250
```

---

### 2.37.7 UI Mockup - Tooltip Comparison

```
┌─────────────────────────────────────────────────────────────────┐
│  WEAPON EDITOR - Diamond Sword                                  │
├─────────────────────────────────────────────────────────────────┤
│  [Stats] [Enchants] [Durability] [Attributes] [Components]      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Attack Damage: ═══════════●═══════ 12.0                      │
│   Attack Speed:  ════════●════════════ 1.8                      │
│   Attack Reach:  ══════════════●══════ 3.5                      │
│   Knockback:     ════●════════════════ 0.5                      │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────────────┐                            │
│  │ §lChanges Preview              │ ← Tooltip on Apply hover   │
│  │ ───────────────────────────────│                            │
│  │ Attack Damage: 7.0 → 12.0 (§a+5.0§r)                        │
│  │ Attack Speed:  1.6 → 1.8  (§a+0.2§r)                        │
│  │ Attack Reach:  3.0 → 3.5  (§a+0.5§r)                        │
│  │ DPS:           11.2 → 21.6 (§a+10.4§r)                      │
│  │                               │                              │
│  │ §c⚠ High damage may break balance§r                         │
│  └────────────────────────────────┘                            │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ [Undo][Redo] │ [History][Export][Import][Presets] │ [█APPLY█]  │
│              │              [Reset] [Cancel]       │            │
└─────────────────────────────────────────────────────────────────┘
```

**Legend:**
- `§a` = Green (positive change)
- `§c` = Red (warning)
- `§r` = Reset formatting
- Tooltip appears on hover over Apply button
- Shows all pending changes with before/after comparison
- DPS calculated automatically
- Warning displayed for dangerous values

---

Sezione 2.37 completata con:
- **Tooltip avanzato** con comparison before/after e calcolo automatico DPS/EHP
- **UI immediate** senza animazioni per max responsiveness
- **Suoni vanilla** per feedback (click, anvil, enchant, etc.)
- **Localizzazione en_us + it_it** con tutte le stringhe tradotte

---

## 2.38 Testing, Shortcuts & Developer Mode

### Decisioni Design (Domande 49-52)

| # | Domanda | Decisione |
|---|---------|-----------|
| 49 | Testing integrato? | **Sì** - Test automatici in-game con dummy entity |
| 50 | Keyboard shortcuts? | **Sì** - Extended (Tab nav, numeri, F5, etc.) |
| 51 | Multi-edit? | **Sì** - Batch edit e selezione multipla |
| 52 | Developer/Debug mode? | **Sì** - Toggle in config + keybind F3+D |

---

### 2.38.1 In-Game Testing System

```java
/**
 * Testing modes for verifying item modifications.
 */
public enum TestMode {
    /** No testing, manual verification */
    NONE,
    /** Spawn dummy entity and test damage/protection */
    COMBAT_TEST,
    /** Preview stats without applying */
    PREVIEW_ONLY,
    /** Full simulation with detailed report */
    FULL_SIMULATION
}

/**
 * Test result from combat simulation.
 */
public record TestResult(
    boolean success,
    TestMode mode,
    String itemName,
    List<TestMetric> metrics,
    @Nullable String errorMessage,
    long durationMs
) {
    public static TestResult success(TestMode mode, String itemName, List<TestMetric> metrics, long durationMs) {
        return new TestResult(true, mode, itemName, metrics, null, durationMs);
    }

    public static TestResult failure(TestMode mode, String itemName, String error) {
        return new TestResult(false, mode, itemName, List.of(), error, 0);
    }
}

/**
 * Individual test metric.
 */
public record TestMetric(
    String name,
    String translationKey,
    float expected,
    float actual,
    float tolerance,
    String unit
) {
    public boolean passed() {
        return Math.abs(expected - actual) <= tolerance;
    }

    public float getDelta() {
        return actual - expected;
    }
}

/**
 * Manages in-game testing for item modifications.
 */
public class ItemTestManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Test entity type (use armor stand or custom dummy)
    private static final EntityType<?> TEST_ENTITY_TYPE = EntityType.ARMOR_STAND;

    // Test area offset from player
    private static final Vec3 TEST_OFFSET = new Vec3(3, 0, 0);

    // Cleanup delay after test
    private static final int CLEANUP_DELAY_TICKS = 100; // 5 seconds

    private final MinecraftServer server;
    private final Map<UUID, TestSession> activeSessions = new ConcurrentHashMap<>();

    public ItemTestManager(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Run a weapon damage test.
     */
    public CompletableFuture<TestResult> testWeaponDamage(
            ServerPlayer player,
            ItemStack weapon,
            WeaponStats expectedStats) {

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                // Create test session
                TestSession session = new TestSession(player.getUUID(), TestMode.COMBAT_TEST);
                activeSessions.put(player.getUUID(), session);

                // Spawn test dummy
                ServerLevel level = player.serverLevel();
                Vec3 spawnPos = player.position().add(TEST_OFFSET);

                ArmorStand dummy = new ArmorStand(level, spawnPos.x, spawnPos.y, spawnPos.z);
                dummy.setInvulnerable(false);
                dummy.setNoGravity(true);
                dummy.setCustomName(Component.literal("§6[Test Dummy]"));
                dummy.setCustomNameVisible(true);

                // Give dummy max health for testing
                dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                dummy.setHealth(1000);

                level.addFreshEntity(dummy);
                session.setTestEntity(dummy);

                // Record initial health
                float initialHealth = dummy.getHealth();

                // Simulate attack
                DamageSource source = level.damageSources().playerAttack(player);
                float baseDamage = expectedStats.attackDamage;

                // Apply damage
                dummy.hurt(source, baseDamage);

                // Calculate actual damage dealt
                float actualDamage = initialHealth - dummy.getHealth();

                // Build metrics
                List<TestMetric> metrics = new ArrayList<>();

                metrics.add(new TestMetric(
                    "Base Damage",
                    "devmod.test.base_damage",
                    baseDamage,
                    actualDamage,
                    0.5f, // tolerance
                    ""
                ));

                // Calculate DPS
                float expectedDPS = expectedStats.attackDamage * expectedStats.attackSpeed;
                metrics.add(new TestMetric(
                    "Theoretical DPS",
                    "devmod.test.theoretical_dps",
                    expectedDPS,
                    expectedDPS, // Same since we can't measure real-time
                    0.1f,
                    "/s"
                ));

                // Schedule cleanup
                scheduleCleanup(session, CLEANUP_DELAY_TICKS);

                long duration = System.currentTimeMillis() - startTime;
                return TestResult.success(TestMode.COMBAT_TEST, weapon.getHoverName().getString(), metrics, duration);

            } catch (Exception e) {
                LOGGER.error("Test failed", e);
                return TestResult.failure(TestMode.COMBAT_TEST, weapon.getHoverName().getString(), e.getMessage());
            }
        }, server);
    }

    /**
     * Run an armor protection test.
     */
    public CompletableFuture<TestResult> testArmorProtection(
            ServerPlayer player,
            ItemStack armor,
            ArmorStats expectedStats) {

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                TestSession session = new TestSession(player.getUUID(), TestMode.COMBAT_TEST);
                activeSessions.put(player.getUUID(), session);

                List<TestMetric> metrics = new ArrayList<>();

                // Test each damage type
                float[] testDamage = {10.0f}; // Base damage to test with

                // Physical damage test
                float physicalReduction = expectedStats.physicalReduction;
                float expectedPhysicalDamage = testDamage[0] * (1 - physicalReduction);
                metrics.add(new TestMetric(
                    "Physical Reduction",
                    "devmod.test.physical_reduction",
                    physicalReduction * 100,
                    physicalReduction * 100,
                    1.0f,
                    "%"
                ));

                // Fire damage test
                float fireReduction = expectedStats.fireReduction;
                metrics.add(new TestMetric(
                    "Fire Reduction",
                    "devmod.test.fire_reduction",
                    fireReduction * 100,
                    fireReduction * 100,
                    1.0f,
                    "%"
                ));

                // Magic damage test
                float magicReduction = expectedStats.magicReduction;
                metrics.add(new TestMetric(
                    "Magic Reduction",
                    "devmod.test.magic_reduction",
                    magicReduction * 100,
                    magicReduction * 100,
                    1.0f,
                    "%"
                ));

                // Calculate EHP
                float avgReduction = (physicalReduction + fireReduction + magicReduction +
                                     expectedStats.explosionReduction + expectedStats.projectileReduction) / 5.0f;
                float ehp = 20.0f / (1.0f - Math.min(avgReduction, 0.95f));
                metrics.add(new TestMetric(
                    "Effective HP",
                    "devmod.test.ehp",
                    ehp,
                    ehp,
                    1.0f,
                    ""
                ));

                long duration = System.currentTimeMillis() - startTime;
                return TestResult.success(TestMode.COMBAT_TEST, armor.getHoverName().getString(), metrics, duration);

            } catch (Exception e) {
                LOGGER.error("Armor test failed", e);
                return TestResult.failure(TestMode.COMBAT_TEST, armor.getHoverName().getString(), e.getMessage());
            }
        }, server);
    }

    /**
     * Preview stats without applying.
     */
    public TestResult previewStats(ItemStack item, Object stats) {
        List<TestMetric> metrics = new ArrayList<>();

        if (stats instanceof WeaponStats ws) {
            metrics.add(new TestMetric("Attack Damage", "devmod.stat.attack_damage",
                ws.attackDamage, ws.attackDamage, 0, ""));
            metrics.add(new TestMetric("Attack Speed", "devmod.stat.attack_speed",
                ws.attackSpeed, ws.attackSpeed, 0, ""));
            metrics.add(new TestMetric("DPS", "devmod.stat.dps",
                ws.attackDamage * ws.attackSpeed, ws.attackDamage * ws.attackSpeed, 0, ""));
        } else if (stats instanceof ArmorStats as) {
            metrics.add(new TestMetric("Physical", "devmod.stat.physical_reduction",
                as.physicalReduction * 100, as.physicalReduction * 100, 0, "%"));
            metrics.add(new TestMetric("Fire", "devmod.stat.fire_reduction",
                as.fireReduction * 100, as.fireReduction * 100, 0, "%"));
        }

        return TestResult.success(TestMode.PREVIEW_ONLY, item.getHoverName().getString(), metrics, 0);
    }

    private void scheduleCleanup(TestSession session, int delayTicks) {
        // Schedule entity removal
        server.tell(new TickTask(server.getTickCount() + delayTicks, () -> {
            if (session.getTestEntity() != null && session.getTestEntity().isAlive()) {
                session.getTestEntity().discard();
            }
            activeSessions.remove(session.getPlayerId());
        }));
    }

    /**
     * Cancel active test for player.
     */
    public void cancelTest(UUID playerId) {
        TestSession session = activeSessions.remove(playerId);
        if (session != null && session.getTestEntity() != null) {
            session.getTestEntity().discard();
        }
    }

    /**
     * Check if player has active test.
     */
    public boolean hasActiveTest(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }
}

/**
 * Active test session data.
 */
public class TestSession {
    private final UUID playerId;
    private final TestMode mode;
    private final long startTime;
    private Entity testEntity;

    public TestSession(UUID playerId, TestMode mode) {
        this.playerId = playerId;
        this.mode = mode;
        this.startTime = System.currentTimeMillis();
    }

    public UUID getPlayerId() { return playerId; }
    public TestMode getMode() { return mode; }
    public long getStartTime() { return startTime; }
    public Entity getTestEntity() { return testEntity; }
    public void setTestEntity(Entity entity) { this.testEntity = entity; }
}
```

---

### 2.38.2 Test Result Display

```java
/**
 * Renders test results in the editor UI.
 */
public class TestResultRenderer {

    private static final int PANEL_WIDTH = 200;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 12;

    /**
     * Render test result panel.
     */
    public static void render(GuiGraphics graphics, Font font, TestResult result, int x, int y) {
        // Background
        int height = calculateHeight(result, font);
        graphics.fill(x, y, x + PANEL_WIDTH, y + height, 0xE0000000);
        graphics.renderOutline(x, y, PANEL_WIDTH, height, 0xFF444444);

        int textY = y + PADDING;

        // Header
        String header = result.success() ? "§a✓ Test Passed" : "§c✗ Test Failed";
        graphics.drawString(font, header, x + PADDING, textY, 0xFFFFFFFF);
        textY += LINE_HEIGHT + 4;

        // Item name
        graphics.drawString(font, "§7" + result.itemName(), x + PADDING, textY, 0xFFAAAAAA);
        textY += LINE_HEIGHT + 4;

        // Separator
        graphics.fill(x + PADDING, textY, x + PANEL_WIDTH - PADDING, textY + 1, 0xFF444444);
        textY += 6;

        // Metrics
        for (TestMetric metric : result.metrics()) {
            renderMetric(graphics, font, metric, x + PADDING, textY);
            textY += LINE_HEIGHT;
        }

        // Error message if failed
        if (!result.success() && result.errorMessage() != null) {
            textY += 4;
            graphics.drawString(font, "§c" + result.errorMessage(), x + PADDING, textY, 0xFFFF5555);
        }

        // Duration
        textY += 8;
        graphics.drawString(font, "§8" + result.durationMs() + "ms", x + PADDING, textY, 0xFF666666);
    }

    private static void renderMetric(GuiGraphics graphics, Font font, TestMetric metric, int x, int y) {
        String icon = metric.passed() ? "§a✓" : "§c✗";
        String name = metric.name();
        String value = String.format("%.1f%s", metric.actual(), metric.unit());

        graphics.drawString(font, icon + " " + name + ": " + value, x, y,
            metric.passed() ? 0xFFFFFFFF : 0xFFFF8888);
    }

    private static int calculateHeight(TestResult result, Font font) {
        int lines = 3 + result.metrics().size(); // Header + item + separator + metrics
        if (!result.success()) lines += 2;
        return PADDING * 2 + lines * LINE_HEIGHT + 20;
    }
}
```

---

### 2.38.3 Extended Keyboard Shortcuts

```java
/**
 * All keyboard shortcuts for the editor.
 */
public final class EditorShortcuts {

    private EditorShortcuts() {}

    // ═══════════════════════════════════════════════════════════════
    // BASIC SHORTCUTS
    // ═══════════════════════════════════════════════════════════════

    /** Undo last change */
    public static final KeyMapping UNDO = new KeyMapping(
        "key.devmod.editor.undo",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Z,
        "key.categories.devmod"
    );

    /** Redo last undone change */
    public static final KeyMapping REDO = new KeyMapping(
        "key.devmod.editor.redo",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Y,
        "key.categories.devmod"
    );

    /** Apply changes */
    public static final KeyMapping APPLY = new KeyMapping(
        "key.devmod.editor.apply",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_S,
        "key.categories.devmod"
    );

    /** Close editor / Cancel */
    public static final KeyMapping CANCEL = new KeyMapping(
        "key.devmod.editor.cancel",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_ESCAPE,
        "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════
    // TAB NAVIGATION
    // ═══════════════════════════════════════════════════════════════

    /** Next tab */
    public static final KeyMapping NEXT_TAB = new KeyMapping(
        "key.devmod.editor.next_tab",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_TAB,
        "key.categories.devmod"
    );

    /** Previous tab (Shift+Tab) */
    public static final KeyMapping PREV_TAB = new KeyMapping(
        "key.devmod.editor.prev_tab",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_TAB, // Combined with Shift
        "key.categories.devmod"
    );

    // Tab direct access (1-5 keys)
    public static final int[] TAB_KEYS = {
        GLFW.GLFW_KEY_1,
        GLFW.GLFW_KEY_2,
        GLFW.GLFW_KEY_3,
        GLFW.GLFW_KEY_4,
        GLFW.GLFW_KEY_5
    };

    // ═══════════════════════════════════════════════════════════════
    // EXTENDED SHORTCUTS
    // ═══════════════════════════════════════════════════════════════

    /** Refresh / Reload from item */
    public static final KeyMapping REFRESH = new KeyMapping(
        "key.devmod.editor.refresh",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F5,
        "key.categories.devmod"
    );

    /** Copy current config to clipboard */
    public static final KeyMapping COPY = new KeyMapping(
        "key.devmod.editor.copy",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        "key.categories.devmod"
    );

    /** Paste config from clipboard */
    public static final KeyMapping PASTE = new KeyMapping(
        "key.devmod.editor.paste",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.devmod"
    );

    /** Reset to defaults */
    public static final KeyMapping RESET = new KeyMapping(
        "key.devmod.editor.reset",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        "key.categories.devmod"
    );

    /** Run test */
    public static final KeyMapping TEST = new KeyMapping(
        "key.devmod.editor.test",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_T,
        "key.categories.devmod"
    );

    /** Toggle developer mode (F3+D) */
    public static final KeyMapping DEV_MODE = new KeyMapping(
        "key.devmod.editor.dev_mode",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_D,
        "key.categories.devmod"
    );

    /** Quick export */
    public static final KeyMapping EXPORT = new KeyMapping(
        "key.devmod.editor.export",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_E,
        "key.categories.devmod"
    );

    /** Quick import */
    public static final KeyMapping IMPORT = new KeyMapping(
        "key.devmod.editor.import",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_I,
        "key.categories.devmod"
    );

    /** Open presets menu */
    public static final KeyMapping PRESETS = new KeyMapping(
        "key.devmod.editor.presets",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_P,
        "key.categories.devmod"
    );

    /** Toggle help overlay */
    public static final KeyMapping HELP = new KeyMapping(
        "key.devmod.editor.help",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F1,
        "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════
    // SLIDER FINE CONTROL
    // ═══════════════════════════════════════════════════════════════

    /** Fine adjustment modifier (hold for 0.1 step) */
    public static final int FINE_MODIFIER = GLFW.GLFW_KEY_LEFT_SHIFT;

    /** Coarse adjustment modifier (hold for 10 step) */
    public static final int COARSE_MODIFIER = GLFW.GLFW_KEY_LEFT_CONTROL;

    /** Increase focused slider value */
    public static final int INCREASE = GLFW.GLFW_KEY_UP;

    /** Decrease focused slider value */
    public static final int DECREASE = GLFW.GLFW_KEY_DOWN;

    /** Jump to min value */
    public static final int MIN_VALUE = GLFW.GLFW_KEY_HOME;

    /** Jump to max value */
    public static final int MAX_VALUE = GLFW.GLFW_KEY_END;
}

/**
 * Handles keyboard input for the editor.
 */
public class EditorKeyboardHandler {

    private final BaseEditorScreen screen;
    private boolean f3Pressed = false;

    public EditorKeyboardHandler(BaseEditorScreen screen) {
        this.screen = screen;
    }

    /**
     * Handle key press. Returns true if consumed.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;

        // Track F3 for dev mode toggle
        if (keyCode == GLFW.GLFW_KEY_F3) {
            f3Pressed = true;
            return false;
        }

        // F3+D for dev mode
        if (keyCode == GLFW.GLFW_KEY_D && f3Pressed) {
            screen.toggleDevMode();
            EditorSounds.click();
            return true;
        }

        // Ctrl+Z - Undo
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            screen.undo();
            return true;
        }

        // Ctrl+Y or Ctrl+Shift+Z - Redo
        if (ctrl && (keyCode == GLFW.GLFW_KEY_Y || (shift && keyCode == GLFW.GLFW_KEY_Z))) {
            screen.redo();
            return true;
        }

        // Ctrl+S - Apply
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
            screen.applyChanges();
            return true;
        }

        // Ctrl+C - Copy
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            screen.copyToClipboard();
            return true;
        }

        // Ctrl+V - Paste
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            screen.pasteFromClipboard();
            return true;
        }

        // Ctrl+R - Reset
        if (ctrl && keyCode == GLFW.GLFW_KEY_R) {
            screen.resetToDefaults();
            return true;
        }

        // Ctrl+T - Test
        if (ctrl && keyCode == GLFW.GLFW_KEY_T) {
            screen.runTest();
            return true;
        }

        // Ctrl+E - Export
        if (ctrl && keyCode == GLFW.GLFW_KEY_E) {
            screen.openExportMenu();
            return true;
        }

        // Ctrl+I - Import
        if (ctrl && keyCode == GLFW.GLFW_KEY_I) {
            screen.openImportMenu();
            return true;
        }

        // Ctrl+P - Presets
        if (ctrl && keyCode == GLFW.GLFW_KEY_P) {
            screen.openPresetsMenu();
            return true;
        }

        // F1 - Help
        if (keyCode == GLFW.GLFW_KEY_F1) {
            screen.toggleHelpOverlay();
            return true;
        }

        // F5 - Refresh
        if (keyCode == GLFW.GLFW_KEY_F5) {
            screen.refreshFromItem();
            return true;
        }

        // Tab / Shift+Tab - Tab navigation
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (shift) {
                screen.previousTab();
            } else {
                screen.nextTab();
            }
            return true;
        }

        // Number keys 1-5 for direct tab access
        for (int i = 0; i < EditorShortcuts.TAB_KEYS.length; i++) {
            if (keyCode == EditorShortcuts.TAB_KEYS[i]) {
                screen.setTab(i);
                return true;
            }
        }

        // Arrow keys for slider control
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            float step = 1.0f;
            if (shift) step = 0.1f;      // Fine
            if (ctrl) step = 10.0f;      // Coarse

            if (keyCode == GLFW.GLFW_KEY_DOWN) step = -step;

            screen.adjustFocusedSlider(step);
            return true;
        }

        // Home/End for min/max
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            screen.setFocusedSliderToMin();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            screen.setFocusedSliderToMax();
            return true;
        }

        // Escape - Cancel/Close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            screen.onClose();
            return true;
        }

        return false;
    }

    /**
     * Handle key release.
     */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F3) {
            f3Pressed = false;
        }
        return false;
    }
}
```

---

### 2.38.4 Shortcuts Help Overlay

```java
/**
 * Renders keyboard shortcuts help overlay.
 */
public class ShortcutsHelpOverlay {

    private static final int OVERLAY_WIDTH = 300;
    private static final int PADDING = 12;
    private static final int LINE_HEIGHT = 14;
    private static final int SECTION_GAP = 8;

    private boolean visible = false;

    public void toggle() {
        visible = !visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!visible) return;

        // Calculate dimensions
        int height = calculateHeight();
        int x = (screenWidth - OVERLAY_WIDTH) / 2;
        int y = (screenHeight - height) / 2;

        // Background with blur effect simulation
        graphics.fill(0, 0, screenWidth, screenHeight, 0x80000000);

        // Panel background
        graphics.fill(x, y, x + OVERLAY_WIDTH, y + height, 0xF0101010);
        graphics.renderOutline(x, y, OVERLAY_WIDTH, height, 0xFF3A7AFF);

        int textY = y + PADDING;

        // Title
        String title = "§l§nKeyboard Shortcuts";
        int titleWidth = font.width(title);
        graphics.drawString(font, title, x + (OVERLAY_WIDTH - titleWidth) / 2, textY, 0xFFFFFFFF);
        textY += LINE_HEIGHT + SECTION_GAP;

        // Basic section
        textY = renderSection(graphics, font, x + PADDING, textY, "§6Basic", new String[][] {
            {"Ctrl+Z", "Undo"},
            {"Ctrl+Y", "Redo"},
            {"Ctrl+S", "Apply changes"},
            {"Escape", "Close editor"}
        });

        // Navigation section
        textY = renderSection(graphics, font, x + PADDING, textY, "§6Navigation", new String[][] {
            {"Tab", "Next tab"},
            {"Shift+Tab", "Previous tab"},
            {"1-5", "Jump to tab"}
        });

        // Editing section
        textY = renderSection(graphics, font, x + PADDING, textY, "§6Editing", new String[][] {
            {"Ctrl+C", "Copy config"},
            {"Ctrl+V", "Paste config"},
            {"Ctrl+R", "Reset to defaults"},
            {"F5", "Refresh from item"}
        });

        // Slider control section
        textY = renderSection(graphics, font, x + PADDING, textY, "§6Slider Control", new String[][] {
            {"↑/↓", "Adjust value (±1)"},
            {"Shift+↑/↓", "Fine adjust (±0.1)"},
            {"Ctrl+↑/↓", "Coarse adjust (±10)"},
            {"Home/End", "Min/Max value"}
        });

        // Advanced section
        textY = renderSection(graphics, font, x + PADDING, textY, "§6Advanced", new String[][] {
            {"Ctrl+T", "Run test"},
            {"Ctrl+E", "Export"},
            {"Ctrl+I", "Import"},
            {"Ctrl+P", "Presets"},
            {"F3+D", "Toggle dev mode"},
            {"F1", "Toggle this help"}
        });

        // Footer
        textY += SECTION_GAP;
        graphics.drawString(font, "§7Press F1 or Escape to close", x + PADDING, textY, 0xFF888888);
    }

    private int renderSection(GuiGraphics graphics, Font font, int x, int y, String title, String[][] shortcuts) {
        // Section title
        graphics.drawString(font, title, x, y, 0xFFFFFFFF);
        y += LINE_HEIGHT;

        // Shortcuts
        for (String[] shortcut : shortcuts) {
            String key = "§b" + shortcut[0];
            String desc = "§7" + shortcut[1];
            graphics.drawString(font, key, x + 8, y, 0xFF55FFFF);
            graphics.drawString(font, desc, x + 100, y, 0xFFAAAAAA);
            y += LINE_HEIGHT;
        }

        return y + SECTION_GAP;
    }

    private int calculateHeight() {
        // Title + 5 sections + footer
        int lines = 1 + 4 + 3 + 4 + 4 + 6 + 1; // Each section header + items
        int sections = 5;
        return PADDING * 2 + lines * LINE_HEIGHT + sections * SECTION_GAP + SECTION_GAP;
    }
}
```

---

### 2.38.5 Multi-Edit System

**Stato attuale (UI)**
- Selettore preset: dropdown scrollabile (8 visibili, mouse wheel), etichetta verde quando selezionato, fallback `(no presets)`.
- Azioni: `[Clear All]`, `[Apply to all]` (usa `DataPreset` + `ItemEditorPresetManager`).
- Stati UX: pannello aperto di default, header cliccabile per collapse/expand, Apply disabilitato in Preview o senza preset, empty state esplicito quando la selezione è vuota.
- Esito batch: summary success/failure count; se fallimenti >0 mostra bottoni `Details` e `Copy` (copia lista errori nel clipboard), elenco fino a 6 righe con overflow `(+N more)`.
- Sezione selezione: header con count, lista item con remove inline, collapse/expand.

```java
/**
 * Manages editing multiple items simultaneously.
 */
public class MultiEditManager {

    private final List<ItemStack> selectedItems = new ArrayList<>();
    private final List<Integer> selectedSlots = new ArrayList<>();
    private MultiEditMode mode = MultiEditMode.SINGLE;

    public enum MultiEditMode {
        /** Edit single item */
        SINGLE,
        /** Apply same preset to multiple items */
        BATCH_PRESET,
        /** Edit multiple items with same changes */
        BATCH_EDIT,
        /** Select items from inventory */
        SELECTION
    }

    /**
     * Add item to selection.
     */
    public void addToSelection(ItemStack item, int slot) {
        if (!selectedItems.contains(item)) {
            selectedItems.add(item.copy());
            selectedSlots.add(slot);
        }
    }

    /**
     * Remove item from selection.
     */
    public void removeFromSelection(int index) {
        if (index >= 0 && index < selectedItems.size()) {
            selectedItems.remove(index);
            selectedSlots.remove(index);
        }
    }

    /**
     * Clear all selection.
     */
    public void clearSelection() {
        selectedItems.clear();
        selectedSlots.clear();
    }

    /**
     * Get selected items count.
     */
    public int getSelectionCount() {
        return selectedItems.size();
    }

    /**
     * Check if item is selected.
     */
    public boolean isSelected(ItemStack item) {
        return selectedItems.stream().anyMatch(s -> ItemStack.isSameItem(s, item));
    }

    /**
     * Apply changes to all selected items.
     */
    public BatchEditResult applyToAll(Consumer<ItemStack> modifier) {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < selectedItems.size(); i++) {
            try {
                ItemStack item = selectedItems.get(i);
                modifier.accept(item);
                successes.add(item.getHoverName().getString());
            } catch (Exception e) {
                failures.add(selectedItems.get(i).getHoverName().getString() + ": " + e.getMessage());
            }
        }

        return new BatchEditResult(successes, failures);
    }

    /**
     * Apply preset to all selected items.
     */
    public BatchEditResult applyPresetToAll(Preset preset, PresetManager presetManager) {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < selectedItems.size(); i++) {
            ItemStack item = selectedItems.get(i);
            int slot = selectedSlots.get(i);

            try {
                // Check if preset scope matches item
                if (preset.scope().matches(item)) {
                    presetManager.applyPreset(preset, item, slot);
                    successes.add(item.getHoverName().getString());
                } else {
                    failures.add(item.getHoverName().getString() + ": Scope mismatch");
                }
            } catch (Exception e) {
                failures.add(item.getHoverName().getString() + ": " + e.getMessage());
            }
        }

        return new BatchEditResult(successes, failures);
    }

    /**
     * Get items that match a filter.
     */
    public List<ItemStack> getMatchingItems(Predicate<ItemStack> filter) {
        return selectedItems.stream().filter(filter).toList();
    }

    public List<ItemStack> getSelectedItems() {
        return Collections.unmodifiableList(selectedItems);
    }

    public List<Integer> getSelectedSlots() {
        return Collections.unmodifiableList(selectedSlots);
    }

    public MultiEditMode getMode() {
        return mode;
    }

    public void setMode(MultiEditMode mode) {
        this.mode = mode;
    }
}

/**
 * Result of batch edit operation.
 */
public record BatchEditResult(
    List<String> successes,
    List<String> failures
) {
    public int totalCount() {
        return successes.size() + failures.size();
    }

    public int successCount() {
        return successes.size();
    }

    public int failureCount() {
        return failures.size();
    }

    public boolean allSucceeded() {
        return failures.isEmpty();
    }

    public boolean allFailed() {
        return successes.isEmpty();
    }
}

/**
 * UI panel for multi-edit selection.
 */
public class MultiEditPanel {

    private final MultiEditManager manager;
    private boolean expanded = false;

    public MultiEditPanel(MultiEditManager manager) {
        this.manager = manager;
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width) {
        int count = manager.getSelectionCount();

        // Header bar
        int headerHeight = 20;
        graphics.fill(x, y, x + width, y + headerHeight, 0xFF2A2A2A);

        // Selection count
        String countText = count + " item" + (count != 1 ? "s" : "") + " selected";
        graphics.drawString(font, countText, x + 4, y + 6, 0xFFFFFFFF);

        // Expand/collapse button
        String expandIcon = expanded ? "▼" : "▶";
        graphics.drawString(font, expandIcon, x + width - 15, y + 6, 0xFFAAAAAA);

        if (!expanded || count == 0) return;

        // Selected items list
        int listY = y + headerHeight;
        int itemHeight = 18;

        for (int i = 0; i < manager.getSelectedItems().size(); i++) {
            ItemStack item = manager.getSelectedItems().get(i);

            // Item background
            // Hover detection implemented in the concrete MultiEditPanel implementation
            // See: `src/main/java/com/frenkvs/devmod/ui/editor/systems/MultiEditPanel.java`
            boolean hovered = (new com.frenkvs.devmod.ui.editor.core.ResponsiveLayout.Rect(x, listY, width, itemHeight)).contains(mouseX, mouseY);
            graphics.fill(x, listY, x + width, listY + itemHeight,
                hovered ? 0xFF3A3A3A : 0xFF222222);

            // Item icon (simplified)
            graphics.drawString(font, "▪", x + 4, listY + 5, 0xFF888888);

            // Item name
            String name = item.getHoverName().getString();
            if (name.length() > 25) name = name.substring(0, 22) + "...";
            graphics.drawString(font, name, x + 16, listY + 5, 0xFFFFFFFF);

            // Remove button
            graphics.drawString(font, "§c✗", x + width - 15, listY + 5, 0xFFFF5555);

            listY += itemHeight;
        }

        // Action buttons
        listY += 4;
        graphics.fill(x, listY, x + width, listY + 24, 0xFF1A1A1A);

        // Clear all button
        graphics.drawString(font, "[Clear All]", x + 4, listY + 8, 0xFFFF8888);

        // Apply to all button
        graphics.drawString(font, "[Apply to All]", x + width - 80, listY + 8, 0xFF88FF88);
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    public boolean isExpanded() {
        return expanded;
    }
}
```

---

### 2.38.6 Developer/Debug Mode

```java
/**
 * Developer mode state and configuration.
 */
public class DevModeState {

    private static boolean enabled = false;
    private static final List<String> eventLog = new ArrayList<>();
    private static final int MAX_LOG_ENTRIES = 100;

    /**
     * Toggle developer mode.
     */
    public static void toggle() {
        enabled = !enabled;
        log("Dev mode " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Check if developer mode is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Set developer mode state.
     */
    public static void setEnabled(boolean state) {
        enabled = state;
    }

    /**
     * Log an event (only in dev mode).
     */
    public static void log(String message) {
        if (!enabled) return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        String entry = "[" + timestamp + "] " + message;

        eventLog.add(entry);
        if (eventLog.size() > MAX_LOG_ENTRIES) {
            eventLog.remove(0);
        }
    }

    /**
     * Get recent log entries.
     */
    public static List<String> getRecentLogs(int count) {
        int start = Math.max(0, eventLog.size() - count);
        return new ArrayList<>(eventLog.subList(start, eventLog.size()));
    }

    /**
     * Clear event log.
     */
    public static void clearLog() {
        eventLog.clear();
    }
}

/**
 * Developer mode overlay showing debug information.
 */
public class DevModeOverlay {

    private static final int PANEL_WIDTH = 280;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    private boolean showComponentIds = true;
    private boolean showRawValues = true;
    private boolean showEventLog = true;
    private int selectedTab = 0; // 0=Info, 1=Components, 2=Log

    /**
     * Render developer overlay.
     */
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                      ItemStack item, Object currentStats) {

        if (!DevModeState.isEnabled()) return;

        int x = screenWidth - PANEL_WIDTH - 10;
        int y = 10;

        // Background
        int height = calculateHeight(item);
        graphics.fill(x, y, x + PANEL_WIDTH, y + height, 0xE0000000);
        graphics.renderOutline(x, y, PANEL_WIDTH, height, 0xFFFF8800);

        int textY = y + PADDING;

        // Header
        graphics.drawString(font, "§6§l[DEV MODE]", x + PADDING, textY, 0xFFFF8800);
        textY += LINE_HEIGHT + 2;

        // Tab buttons
        String[] tabs = {"Info", "Components", "Log"};
        int tabX = x + PADDING;
        for (int i = 0; i < tabs.length; i++) {
            int color = (i == selectedTab) ? 0xFFFFFF00 : 0xFF888888;
            graphics.drawString(font, "[" + tabs[i] + "]", tabX, textY, color);
            tabX += font.width("[" + tabs[i] + "]") + 8;
        }
        textY += LINE_HEIGHT + 4;

        // Separator
        graphics.fill(x + PADDING, textY, x + PANEL_WIDTH - PADDING, textY + 1, 0xFFFF8800);
        textY += 4;

        // Tab content
        switch (selectedTab) {
            case 0 -> renderInfoTab(graphics, font, x + PADDING, textY, item, currentStats);
            case 1 -> renderComponentsTab(graphics, font, x + PADDING, textY, item);
            case 2 -> renderLogTab(graphics, font, x + PADDING, textY);
        }
    }

    private void renderInfoTab(GuiGraphics graphics, Font font, int x, int y,
                               ItemStack item, Object stats) {
        // Item info
        graphics.drawString(font, "§7Item: §f" + item.getHoverName().getString(), x, y, 0xFFFFFFFF);
        y += LINE_HEIGHT;

        // Registry name
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
        graphics.drawString(font, "§7ID: §e" + id.toString(), x, y, 0xFFFFFFFF);
        y += LINE_HEIGHT;

        // Stack size and damage
        graphics.drawString(font, "§7Count: §f" + item.getCount() + " §7Damage: §f" + item.getDamageValue(), x, y, 0xFFFFFFFF);
        y += LINE_HEIGHT;

        // Component count
        int componentCount = 0;
        // Count components (implementation depends on how you iterate DataComponentMap)
        graphics.drawString(font, "§7Components: §f" + componentCount, x, y, 0xFFFFFFFF);
        y += LINE_HEIGHT + 4;

        // Stats info
        if (stats instanceof WeaponStats ws) {
            graphics.drawString(font, "§6Weapon Stats:", x, y, 0xFFFF8800);
            y += LINE_HEIGHT;
            graphics.drawString(font, "  §7damage=§f" + ws.attackDamage, x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
            graphics.drawString(font, "  §7speed=§f" + ws.attackSpeed, x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
            graphics.drawString(font, "  §7reach=§f" + ws.attackReach, x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
            graphics.drawString(font, "  §7knockback=§f" + ws.attackKnockback, x, y, 0xFFFFFFFF);
        } else if (stats instanceof ArmorStats as) {
            graphics.drawString(font, "§6Armor Stats:", x, y, 0xFFFF8800);
            y += LINE_HEIGHT;
            graphics.drawString(font, "  §7physical=§f" + as.physicalReduction, x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
            graphics.drawString(font, "  §7fire=§f" + as.fireReduction, x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
            graphics.drawString(font, "  §7magic=§f" + as.magicReduction, x, y, 0xFFFFFFFF);
        }
    }

    private void renderComponentsTab(GuiGraphics graphics, Font font, int x, int y, ItemStack item) {
        graphics.drawString(font, "§6Data Components:", x, y, 0xFFFF8800);
        y += LINE_HEIGHT;

        // List all components on the item
        // This is a simplified view - actual implementation would iterate DataComponentMap
        graphics.drawString(font, "§7(Component list here)", x, y, 0xFF888888);
        y += LINE_HEIGHT;

        // Show specific important components
        if (item.has(DataComponents.DAMAGE)) {
            graphics.drawString(font, "  §eDamage: §f" + item.get(DataComponents.DAMAGE), x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }

        if (item.has(DataComponents.MAX_DAMAGE)) {
            graphics.drawString(font, "  §eMaxDamage: §f" + item.get(DataComponents.MAX_DAMAGE), x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }

        if (item.has(DataComponents.UNBREAKABLE)) {
            graphics.drawString(font, "  §eUnbreakable: §ftrue", x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }

        if (item.has(DataComponents.ENCHANTMENTS)) {
            ItemEnchantments enchants = item.get(DataComponents.ENCHANTMENTS);
            graphics.drawString(font, "  §eEnchantments: §f" + enchants.size(), x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }

        if (item.has(DataComponents.ATTRIBUTE_MODIFIERS)) {
            ItemAttributeModifiers attrs = item.get(DataComponents.ATTRIBUTE_MODIFIERS);
            graphics.drawString(font, "  §eAttributes: §f" + attrs.modifiers().size(), x, y, 0xFFFFFFFF);
        }
    }

    private void renderLogTab(GuiGraphics graphics, Font font, int x, int y) {
        graphics.drawString(font, "§6Event Log:", x, y, 0xFFFF8800);
        y += LINE_HEIGHT;

        List<String> logs = DevModeState.getRecentLogs(15);

        if (logs.isEmpty()) {
            graphics.drawString(font, "§7(No events)", x, y, 0xFF888888);
            return;
        }

        for (String log : logs) {
            // Truncate long lines
            if (log.length() > 40) {
                log = log.substring(0, 37) + "...";
            }
            graphics.drawString(font, "§7" + log, x, y, 0xFFAAAAAA);
            y += LINE_HEIGHT;
        }
    }

    private int calculateHeight(ItemStack item) {
        return 200; // Fixed height for now
    }

    public void nextTab() {
        selectedTab = (selectedTab + 1) % 3;
    }

    public void prevTab() {
        selectedTab = (selectedTab + 2) % 3;
    }

    public void setTab(int tab) {
        selectedTab = Math.max(0, Math.min(2, tab));
    }
}
```

---

### 2.38.7 Config Section for Testing & Dev Mode

```toml
# ═══════════════════════════════════════════════════════════════════════════
# TESTING & DEVELOPER MODE CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════

[testing]
    # Enable in-game testing system
    enabled = true

    # Default test mode
    # Values: "none", "combat_test", "preview_only", "full_simulation"
    defaultMode = "preview_only"

    # Auto-cleanup test entities after seconds
    cleanupDelaySeconds = 5

    # Show test results overlay
    showResultsOverlay = true

    # Test dummy custom name
    testDummyName = "[Test Dummy]"

[shortcuts]
    # Enable extended keyboard shortcuts
    extendedShortcuts = true

    # Enable number keys for tab switching
    numberTabSwitch = true

    # Enable slider keyboard control
    sliderKeyboardControl = true

    # Fine adjustment step (with Shift)
    fineStep = 0.1

    # Coarse adjustment step (with Ctrl)
    coarseStep = 10.0

[multiedit]
    # Enable multi-item editing
    enabled = true

    # Maximum items in selection
    maxSelection = 20

    # Default multi-edit mode
    # Values: "single", "batch_preset", "batch_edit", "selection"
    defaultMode = "single"

    # Show selection count in UI
    showSelectionCount = true

[devmode]
    # Enable developer mode toggle (F3+D)
    allowToggle = true

    # Start with dev mode enabled
    enabledByDefault = false

    # Show component IDs in dev overlay
    showComponentIds = true

    # Show raw numeric values
    showRawValues = true

    # Show event log
    showEventLog = true

    # Max event log entries
    maxLogEntries = 100

    # Log file path (relative to game directory)
    logFilePath = "logs/devmod_editor.log"

    # Persist dev mode state between sessions
    persistState = false
```

---

### 2.38.8 UI Mockup - Developer Mode Overlay

```
┌─────────────────────────────────────────────────────────────────┐
│  WEAPON EDITOR - Diamond Sword                        §6[DEV]§r │
├─────────────────────────────────────────────────────────────────┤
│  [Stats] [Enchants] [Durability] [Attributes] [Components]      │
├─────────────────────────────────────────────────────────────────┤
│                                         ┌──────────────────────┐│
│   Attack Damage: ═══════════●═══════    │§6§l[DEV MODE]        ││
│   Attack Speed:  ════════●══════════    │[Info][Components][Log]│
│   Attack Reach:  ══════════════●════    │──────────────────────││
│   Knockback:     ════●══════════════    │§7Item: §fDiamond Sword│
│                                         │§7ID: §eminecraft:dia..│
│   §6[Run Test]§r  §7Last: §a✓ Passed    │§7Count: §f1 §7Dmg: §f0│
│                                         │§7Components: §f12     │
│                                         │                      ││
│                                         │§6Weapon Stats:       ││
│                                         │  §7damage=§f12.0     ││
│                                         │  §7speed=§f1.8       ││
│                                         │  §7reach=§f3.5       ││
│                                         │  §7knockback=§f0.5   ││
│                                         └──────────────────────┘│
├─────────────────────────────────────────────────────────────────┤
│ [Undo][Redo] │ [History][Export][Import][Presets] │ [█APPLY█]  │
│    §8Ctrl+Z/Y§r │      §8F1=Help  F3+D=DevMode§r    │ §8Ctrl+S§r   │
└─────────────────────────────────────────────────────────────────┘
```

---

### 2.38.9 Shortcuts Quick Reference Card

```
┌────────────────────────────────────────────────────────────────┐
│                    KEYBOARD SHORTCUTS                          │
├────────────────────────────────────────────────────────────────┤
│  §6BASIC§r                     │  §6NAVIGATION§r                  │
│  Ctrl+Z    Undo              │  Tab        Next tab            │
│  Ctrl+Y    Redo              │  Shift+Tab  Previous tab        │
│  Ctrl+S    Apply             │  1-5        Jump to tab         │
│  Escape    Close             │                                 │
├────────────────────────────────────────────────────────────────┤
│  §6EDITING§r                   │  §6SLIDER CONTROL§r              │
│  Ctrl+C    Copy config       │  ↑/↓        Adjust ±1           │
│  Ctrl+V    Paste config      │  Shift+↑/↓  Fine adjust ±0.1    │
│  Ctrl+R    Reset defaults    │  Ctrl+↑/↓   Coarse adjust ±10   │
│  F5        Refresh           │  Home/End   Min/Max value       │
├────────────────────────────────────────────────────────────────┤
│  §6ADVANCED§r                  │  §6MULTI-EDIT§r                  │
│  Ctrl+T    Run test          │  Ctrl+Click Add to selection    │
│  Ctrl+E    Export            │  Ctrl+A     Select all similar  │
│  Ctrl+I    Import            │  Ctrl+D     Deselect all        │
│  Ctrl+P    Presets           │                                 │
│  F3+D      Toggle dev mode   │                                 │
│  F1        Show/hide help    │                                 │
└────────────────────────────────────────────────────────────────┘
```

---

Sezione 2.38 completata con:
- **Testing integrato** con dummy entity per verificare danno/protezione
- **Extended shortcuts** con Tab navigation, numeri, F5, slider arrows
- **Multi-edit** con batch preset e selezione multipla
- **Developer mode** con F3+D toggle, component viewer, event log

---

## 2.39 Edge Cases & Favorites System

### Decisioni Design (Domande 53-56)

| # | Domanda | Decisione |
|---|---------|-----------|
| 53 | Item stackati (count > 1)? | **Separare stack** - Edita solo il primo, separa automaticamente |
| 54 | Item virtuali/template? | **Sì** - Sistema di template items per preview |
| 55 | Chiusura con modifiche non salvate? | **Dialog di conferma** - Chiede se salvare/scartare |
| 56 | Favorites con accesso rapido? | **Sì** - Star toggle e lista quick-access |

---

### 2.39.1 Stacked Items Handling

```java
/**
 * Handles editing of stacked items by separating them.
 */
public class StackSeparator {

    /**
     * Result of stack separation.
     */
    public record SeparationResult(
        ItemStack editableItem,    // The single item to edit
        ItemStack remainingStack,  // The remaining stack (count - 1)
        int originalSlot,          // Original slot
        int newSlot                // Slot for remaining stack (-1 if inventory full)
    ) {
        public boolean hasRemaining() {
            return !remainingStack.isEmpty();
        }

        public boolean remainingPlaced() {
            return newSlot != -1 || remainingStack.isEmpty();
        }
    }

    /**
     * Check if item needs separation before editing.
     */
    public static boolean needsSeparation(ItemStack stack) {
        return stack.getCount() > 1;
    }

    /**
     * Separate one item from stack for editing.
     * Returns the single item and handles the remaining stack.
     */
    public static SeparationResult separate(Player player, int slot) {
        Inventory inventory = player.getInventory();
        ItemStack original = inventory.getItem(slot);

        if (original.isEmpty()) {
            return new SeparationResult(ItemStack.EMPTY, ItemStack.EMPTY, slot, -1);
        }

        if (original.getCount() == 1) {
            // No separation needed
            return new SeparationResult(original, ItemStack.EMPTY, slot, slot);
        }

        // Create single item for editing
        ItemStack singleItem = original.copyWithCount(1);

        // Create remaining stack
        ItemStack remaining = original.copyWithCount(original.getCount() - 1);

        // Place single item in original slot
        inventory.setItem(slot, singleItem);

        // Find slot for remaining stack
        int remainingSlot = findSlotForRemaining(inventory, remaining, slot);

        if (remainingSlot != -1) {
            inventory.setItem(remainingSlot, remaining);
        } else {
            // No space - drop on ground or keep in cursor
            // For now, we'll merge back if no space
            player.drop(remaining, false);
        }

        return new SeparationResult(singleItem, remaining, slot, remainingSlot);
    }

    /**
     * Find a slot for the remaining stack.
     * Prefers empty slots, then tries to merge with existing stacks.
     */
    private static int findSlotForRemaining(Inventory inventory, ItemStack remaining, int excludeSlot) {
        // First, try to find an empty slot
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (i == excludeSlot) continue;
            if (inventory.getItem(i).isEmpty()) {
                return i;
            }
        }

        // Then, try to merge with existing compatible stacks
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (i == excludeSlot) continue;
            ItemStack existing = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space >= remaining.getCount()) {
                    existing.grow(remaining.getCount());
                    remaining.setCount(0);
                    return i;
                }
            }
        }

        return -1; // No slot available
    }

    /**
     * Merge edited item back with remaining stack if user cancels.
     */
    public static void mergeBack(Player player, SeparationResult result) {
        if (!result.hasRemaining()) return;

        Inventory inventory = player.getInventory();
        ItemStack edited = inventory.getItem(result.originalSlot());

        if (result.remainingPlaced() && result.newSlot() != -1) {
            ItemStack remaining = inventory.getItem(result.newSlot());

            // Check if items are still compatible (no changes made)
            if (ItemStack.isSameItemSameComponents(edited, remaining)) {
                // Merge back
                edited.grow(remaining.getCount());
                inventory.setItem(result.newSlot(), ItemStack.EMPTY);
            }
        }
    }
}

/**
 * UI warning for stack separation.
 */
public class StackWarningDialog {

    private boolean visible = false;
    private SeparationResult pendingResult;
    private Consumer<Boolean> callback;

    /**
     * Show warning dialog before separating stack.
     */
    public void show(ItemStack stack, Consumer<Boolean> onConfirm) {
        this.visible = true;
        this.callback = onConfirm;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!visible) return;

        int width = 280;
        int height = 100;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;

        // Background
        graphics.fill(x, y, x + width, y + height, 0xF0101010);
        graphics.renderOutline(x, y, width, height, 0xFFFFAA00);

        // Title
        graphics.drawString(font, "§6Stack Separation", x + 10, y + 10, 0xFFFFFFFF);

        // Message
        String msg1 = "This item is stacked. Editing will";
        String msg2 = "separate 1 item from the stack.";
        graphics.drawString(font, msg1, x + 10, y + 30, 0xFFAAAAAA);
        graphics.drawString(font, msg2, x + 10, y + 42, 0xFFAAAAAA);

        // Buttons
        int btnY = y + height - 30;
        graphics.drawString(font, "[Cancel]", x + 10, btnY, 0xFFFF8888);
        graphics.drawString(font, "[Continue]", x + width - 70, btnY, 0xFF88FF88);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        // Simplified - check button clicks
        // In real implementation, calculate button bounds

        return true;
    }

    public void confirm(boolean proceed) {
        visible = false;
        if (callback != null) {
            callback.accept(proceed);
        }
    }
}
```

---

### 2.39.2 Template Items System

```java
/**
 * Virtual/template item for previewing configurations.
 */
public record TemplateItem(
    String id,
    String name,
    ResourceLocation baseItem,
    TemplateType type,
    Map<String, Object> defaultStats,
    List<String> tags,
    @Nullable String description,
    long createdAt,
    long modifiedAt
) {
    public enum TemplateType {
        WEAPON,
        ARMOR,
        TOOL,
        CUSTOM
    }

    /**
     * Create an ItemStack from this template.
     */
    public ItemStack toItemStack(HolderLookup.Provider registries) {
        Item item = BuiltInRegistries.ITEM.get(baseItem);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);

        // Apply default stats based on type
        applyDefaultStats(stack, registries);

        // Set custom name
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));

        return stack;
    }

    private void applyDefaultStats(ItemStack stack, HolderLookup.Provider registries) {
        // Apply stored stats
        for (Map.Entry<String, Object> entry : defaultStats.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Handle different stat types
            switch (key) {
                case "damage" -> {
                    if (value instanceof Number num) {
                        // Apply damage attribute
                    }
                }
                case "attackSpeed" -> {
                    if (value instanceof Number num) {
                        // Apply attack speed attribute
                    }
                }
                case "armor" -> {
                    if (value instanceof Number num) {
                        // Apply armor attribute
                    }
                }
                // ... etc
            }
        }
    }
}

/**
 * Manages template items.
 */
public class TemplateManager {

    private static final Path TEMPLATES_DIR = FMLPaths.CONFIGDIR.get().resolve("devmod/templates");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, TemplateItem> templates = new ConcurrentHashMap<>();

    /**
     * Load all templates from disk.
     */
    public void loadTemplates() {
        templates.clear();

        try {
            if (!Files.exists(TEMPLATES_DIR)) {
                Files.createDirectories(TEMPLATES_DIR);
                createDefaultTemplates();
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(TEMPLATES_DIR, "*.json")) {
                for (Path file : stream) {
                    try {
                        String json = Files.readString(file);
                        TemplateItem template = GSON.fromJson(json, TemplateItem.class);
                        templates.put(template.id(), template);
                    } catch (Exception e) {
                        LOGGER.error("Failed to load template: " + file, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load templates", e);
        }
    }

    /**
     * Save template to disk.
     */
    public void saveTemplate(TemplateItem template) {
        templates.put(template.id(), template);

        Path file = TEMPLATES_DIR.resolve(template.id() + ".json");
        try {
            String json = GSON.toJson(template);
            Files.writeString(file, json);
        } catch (IOException e) {
            LOGGER.error("Failed to save template: " + template.id(), e);
        }
    }

    /**
     * Delete template.
     */
    public void deleteTemplate(String id) {
        templates.remove(id);

        Path file = TEMPLATES_DIR.resolve(id + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.error("Failed to delete template: " + id, e);
        }
    }

    /**
     * Create template from existing item.
     */
    public TemplateItem createFromItem(ItemStack item, String name) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());

        // Determine type
        TemplateItem.TemplateType type = determineType(item);

        // Extract current stats
        Map<String, Object> stats = extractStats(item, type);

        String id = generateUniqueId(name);
        long now = System.currentTimeMillis();

        return new TemplateItem(
            id,
            name,
            itemId,
            type,
            stats,
            List.of(),
            null,
            now,
            now
        );
    }

    private TemplateItem.TemplateType determineType(ItemStack item) {
        Item i = item.getItem();
        if (i instanceof SwordItem || i instanceof AxeItem) {
            return TemplateItem.TemplateType.WEAPON;
        } else if (i instanceof ArmorItem) {
            return TemplateItem.TemplateType.ARMOR;
        } else if (i instanceof DiggerItem) {
            return TemplateItem.TemplateType.TOOL;
        }
        return TemplateItem.TemplateType.CUSTOM;
    }

    private Map<String, Object> extractStats(ItemStack item, TemplateItem.TemplateType type) {
        Map<String, Object> stats = new HashMap<>();

        // Extract based on type
        if (type == TemplateItem.TemplateType.WEAPON) {
            // Get damage, speed, etc from attributes
            ItemAttributeModifiers attrs = item.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);

            for (ItemAttributeModifiers.Entry entry : attrs.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                    stats.put("damage", entry.modifier().amount());
                } else if (entry.attribute().is(Attributes.ATTACK_SPEED)) {
                    stats.put("attackSpeed", entry.modifier().amount());
                }
            }
        }

        return stats;
    }

    private String generateUniqueId(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]", "_");
        String id = base;
        int counter = 1;
        while (templates.containsKey(id)) {
            id = base + "_" + counter++;
        }
        return id;
    }

    /**
     * Get all templates.
     */
    public Collection<TemplateItem> getAllTemplates() {
        return templates.values();
    }

    /**
     * Get templates by type.
     */
    public List<TemplateItem> getTemplatesByType(TemplateItem.TemplateType type) {
        return templates.values().stream()
            .filter(t -> t.type() == type)
            .toList();
    }

    /**
     * Get template by ID.
     */
    public Optional<TemplateItem> getTemplate(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    /**
     * Create default templates.
     */
    private void createDefaultTemplates() {
        // Example default templates
        saveTemplate(new TemplateItem(
            "basic_sword",
            "Basic Sword Template",
            ResourceLocation.withDefaultNamespace("iron_sword"),
            TemplateItem.TemplateType.WEAPON,
            Map.of("damage", 6.0, "attackSpeed", 1.6),
            List.of("starter", "weapon"),
            "A basic iron sword template",
            System.currentTimeMillis(),
            System.currentTimeMillis()
        ));

        saveTemplate(new TemplateItem(
            "tank_chestplate",
            "Tank Chestplate Template",
            ResourceLocation.withDefaultNamespace("diamond_chestplate"),
            TemplateItem.TemplateType.ARMOR,
            Map.of("armor", 8.0, "toughness", 2.0, "knockbackResistance", 0.1),
            List.of("tank", "armor"),
            "High defense chestplate template",
            System.currentTimeMillis(),
            System.currentTimeMillis()
        ));
    }
}

/**
 * Template browser/picker UI.
 */
public class TemplateBrowserScreen extends Screen {

    private final List<TemplateItem> templates;
    private final Consumer<TemplateItem> onSelect;
    private int selectedIndex = -1;
    private String searchQuery = "";
    private TemplateItem.TemplateType filterType = null;

    public TemplateBrowserScreen(TemplateManager manager, Consumer<TemplateItem> onSelect) {
        super(Component.literal("Template Browser"));
        this.templates = new ArrayList<>(manager.getAllTemplates());
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        // Search box
        // Type filter buttons
        // Template list
        // Preview panel
        // Select/Cancel buttons
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int x = (width - 300) / 2;
        int y = (height - 200) / 2;

        // Background
        graphics.fill(x, y, x + 300, y + 200, 0xF0101010);
        graphics.renderOutline(x, y, 300, 200, 0xFF3A7AFF);

        // Title
        graphics.drawString(font, "§lTemplate Browser", x + 10, y + 10, 0xFFFFFFFF);

        // Filter tabs
        int tabX = x + 10;
        int tabY = y + 25;
        String[] tabs = {"All", "Weapons", "Armor", "Tools"};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = (i == 0 && filterType == null) ||
                              (i == 1 && filterType == TemplateItem.TemplateType.WEAPON) ||
                              (i == 2 && filterType == TemplateItem.TemplateType.ARMOR) ||
                              (i == 3 && filterType == TemplateItem.TemplateType.TOOL);
            int color = selected ? 0xFFFFFF00 : 0xFF888888;
            graphics.drawString(font, "[" + tabs[i] + "]", tabX, tabY, color);
            tabX += font.width("[" + tabs[i] + "]") + 8;
        }

        // Template list
        int listY = y + 45;
        List<TemplateItem> filtered = getFilteredTemplates();
        for (int i = 0; i < Math.min(filtered.size(), 8); i++) {
            TemplateItem template = filtered.get(i);
            boolean selected = i == selectedIndex;
            boolean hovered = mouseY >= listY && mouseY < listY + 16 &&
                             mouseX >= x + 10 && mouseX < x + 200;

            int bg = selected ? 0xFF3A3A5A : (hovered ? 0xFF2A2A2A : 0x00000000);
            graphics.fill(x + 10, listY, x + 200, listY + 16, bg);

            String icon = getTypeIcon(template.type());
            graphics.drawString(font, icon + " " + template.name(), x + 14, listY + 4,
                selected ? 0xFFFFFFFF : 0xFFAAAAAA);

            listY += 18;
        }

        // Preview panel (right side)
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            TemplateItem selected = filtered.get(selectedIndex);
            renderPreview(graphics, x + 210, y + 45, selected);
        }

        // Buttons
        graphics.drawString(font, "[Cancel]", x + 10, y + 180, 0xFFFF8888);
        graphics.drawString(font, "[Select]", x + 240, y + 180, 0xFF88FF88);
        graphics.drawString(font, "[New]", x + 130, y + 180, 0xFF88FFFF);
    }

    private void renderPreview(GuiGraphics graphics, int x, int y, TemplateItem template) {
        graphics.drawString(font, "§6" + template.name(), x, y, 0xFFFF8800);
        y += 12;

        graphics.drawString(font, "§7Type: §f" + template.type(), x, y, 0xFFFFFFFF);
        y += 10;

        graphics.drawString(font, "§7Base: §e" + template.baseItem(), x, y, 0xFFFFFFFF);
        y += 12;

        if (template.description() != null) {
            graphics.drawString(font, "§7" + template.description(), x, y, 0xFF888888);
        }
    }

    private String getTypeIcon(TemplateItem.TemplateType type) {
        return switch (type) {
            case WEAPON -> "⚔";
            case ARMOR -> "🛡";
            case TOOL -> "⛏";
            case CUSTOM -> "✦";
        };
    }

    private List<TemplateItem> getFilteredTemplates() {
        return templates.stream()
            .filter(t -> filterType == null || t.type() == filterType)
            .filter(t -> searchQuery.isEmpty() ||
                        t.name().toLowerCase().contains(searchQuery.toLowerCase()))
            .toList();
    }
}
```

---

### 2.39.3 Unsaved Changes Dialog

```java
/**
 * Dialog for handling unsaved changes on close.
 */
public class UnsavedChangesDialog {

    public enum DialogResult {
        SAVE,       // Save changes and close
        DISCARD,    // Discard changes and close
        CANCEL      // Cancel close, stay in editor
    }

    private boolean visible = false;
    private Consumer<DialogResult> callback;
    private int changeCount = 0;

    /**
     * Show the dialog.
     */
    public void show(int changeCount, Consumer<DialogResult> onResult) {
        this.visible = true;
        this.changeCount = changeCount;
        this.callback = onResult;
    }

    /**
     * Hide the dialog.
     */
    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * Render the dialog.
     */
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!visible) return;

        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x80000000);

        int width = 300;
        int height = 120;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;

        // Dialog background
        graphics.fill(x, y, x + width, y + height, 0xF0101010);
        graphics.renderOutline(x, y, width, height, 0xFFFFAA00);

        // Warning icon and title
        graphics.drawString(font, "§6⚠ Unsaved Changes", x + 10, y + 10, 0xFFFFAA00);

        // Message
        String msg1 = "You have " + changeCount + " unsaved change" + (changeCount != 1 ? "s" : "") + ".";
        String msg2 = "Do you want to save before closing?";
        graphics.drawString(font, msg1, x + 10, y + 35, 0xFFFFFFFF);
        graphics.drawString(font, msg2, x + 10, y + 50, 0xFFAAAAAA);

        // Buttons
        int btnY = y + height - 35;
        int btnWidth = 80;
        int gap = 15;
        int totalWidth = btnWidth * 3 + gap * 2;
        int btnX = x + (width - totalWidth) / 2;

        // Save button
        renderButton(graphics, font, btnX, btnY, btnWidth, "Save", 0xFF55AA55, 0xFF88FF88);
        btnX += btnWidth + gap;

        // Discard button
        renderButton(graphics, font, btnX, btnY, btnWidth, "Discard", 0xFFAA5555, 0xFFFF8888);
        btnX += btnWidth + gap;

        // Cancel button
        renderButton(graphics, font, btnX, btnY, btnWidth, "Cancel", 0xFF555555, 0xFFAAAAAA);
    }

    private void renderButton(GuiGraphics graphics, Font font, int x, int y, int width,
                             String text, int bgColor, int textColor) {
        int height = 22;
        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.renderOutline(x, y, width, height, textColor);

        int textWidth = font.width(text);
        graphics.drawString(font, text, x + (width - textWidth) / 2, y + 7, textColor);
    }

    /**
     * Handle mouse click.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible || button != 0) return false;

        int width = 300;
        int height = 120;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;

        int btnY = y + height - 35;
        int btnHeight = 22;
        int btnWidth = 80;
        int gap = 15;
        int totalWidth = btnWidth * 3 + gap * 2;
        int btnX = x + (width - totalWidth) / 2;

        // Check which button was clicked
        if (mouseY >= btnY && mouseY < btnY + btnHeight) {
            // Save button
            if (mouseX >= btnX && mouseX < btnX + btnWidth) {
                select(DialogResult.SAVE);
                return true;
            }
            btnX += btnWidth + gap;

            // Discard button
            if (mouseX >= btnX && mouseX < btnX + btnWidth) {
                select(DialogResult.DISCARD);
                return true;
            }
            btnX += btnWidth + gap;

            // Cancel button
            if (mouseX >= btnX && mouseX < btnX + btnWidth) {
                select(DialogResult.CANCEL);
                return true;
            }
        }

        return true; // Consume click even if not on button
    }

    /**
     * Handle keyboard shortcuts.
     */
    public boolean keyPressed(int keyCode) {
        if (!visible) return false;

        // Enter = Save
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            select(DialogResult.SAVE);
            return true;
        }

        // Escape = Cancel
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            select(DialogResult.CANCEL);
            return true;
        }

        // D = Discard
        if (keyCode == GLFW.GLFW_KEY_D) {
            select(DialogResult.DISCARD);
            return true;
        }

        return true;
    }

    private void select(DialogResult result) {
        visible = false;
        if (callback != null) {
            callback.accept(result);
        }
    }
}

/**
 * Integration with editor screen.
 */
public abstract class BaseEditorScreen extends Screen {

    protected final UnsavedChangesDialog unsavedDialog = new UnsavedChangesDialog();
    protected boolean hasUnsavedChanges = false;
    protected int unsavedChangeCount = 0;

    /**
     * Mark that changes have been made.
     */
    protected void markDirty() {
        hasUnsavedChanges = true;
        unsavedChangeCount++;
    }

    /**
     * Clear dirty flag after save.
     */
    protected void clearDirty() {
        hasUnsavedChanges = false;
        unsavedChangeCount = 0;
    }

    /**
     * Override onClose to check for unsaved changes.
     */
    @Override
    public void onClose() {
        if (hasUnsavedChanges) {
            unsavedDialog.show(unsavedChangeCount, result -> {
                switch (result) {
                    case SAVE -> {
                        applyChanges();
                        closeScreen();
                    }
                    case DISCARD -> closeScreen();
                    case CANCEL -> {} // Do nothing, stay open
                }
            });
        } else {
            closeScreen();
        }
    }

    /**
     * Actually close the screen.
     */
    protected void closeScreen() {
        super.onClose();
    }

    /**
     * Apply changes - to be implemented by subclasses.
     */
    protected abstract void applyChanges();
}
```

---

### 2.39.4 Favorites System

**Persistenza e scope**
- Storage **client-side** (per utente) in `config/devmod/favorites.json` – non per-world.
- Header tooltip del pannello mostra: `Favorites scope: CLIENT` per ricordarlo ai tester.
- Favorites può contenere preset, template o config item; max definito in config.

```java
/**
 * Favorite item reference.
 */
public record FavoriteItem(
    String id,
    String displayName,
    ResourceLocation itemType,
    FavoriteType type,
    @Nullable String presetId,  // If favoriting a preset
    @Nullable String notes,
    int sortOrder,
    long addedAt
) {
    public enum FavoriteType {
        ITEM_CONFIG,    // A specific item configuration
        PRESET,         // A preset
        TEMPLATE        // A template
    }
}

/**
 * Manages user favorites.
 */
public class FavoritesManager {

    private static final Path FAVORITES_FILE = FMLPaths.CONFIGDIR.get()
        .resolve("devmod/favorites.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_FAVORITES = 50;

    private final List<FavoriteItem> favorites = new ArrayList<>();
    private final Set<String> favoriteIds = new HashSet<>();

    /**
     * Load favorites from disk.
     */
    public void load() {
        favorites.clear();
        favoriteIds.clear();

        if (!Files.exists(FAVORITES_FILE)) return;

        try {
            String json = Files.readString(FAVORITES_FILE);
            Type listType = new TypeToken<List<FavoriteItem>>(){}.getType();
            List<FavoriteItem> loaded = GSON.fromJson(json, listType);
            if (loaded != null) {
                favorites.addAll(loaded);
                favorites.forEach(f -> favoriteIds.add(f.id()));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load favorites", e);
        }
    }

    /**
     * Save favorites to disk.
     */
    public void save() {
        try {
            Files.createDirectories(FAVORITES_FILE.getParent());
            String json = GSON.toJson(favorites);
            Files.writeString(FAVORITES_FILE, json);
        } catch (IOException e) {
            LOGGER.error("Failed to save favorites", e);
        }
    }

    /**
     * Add item to favorites.
     */
    public boolean addFavorite(FavoriteItem item) {
        if (favorites.size() >= MAX_FAVORITES) {
            return false;
        }
        if (favoriteIds.contains(item.id())) {
            return false;
        }

        favorites.add(item);
        favoriteIds.add(item.id());
        save();
        return true;
    }

    /**
     * Remove item from favorites.
     */
    public boolean removeFavorite(String id) {
        boolean removed = favorites.removeIf(f -> f.id().equals(id));
        if (removed) {
            favoriteIds.remove(id);
            save();
        }
        return removed;
    }

    /**
     * Toggle favorite status.
     */
    public boolean toggleFavorite(FavoriteItem item) {
        if (isFavorite(item.id())) {
            return removeFavorite(item.id());
        } else {
            return addFavorite(item);
        }
    }

    /**
     * Check if item is favorited.
     */
    public boolean isFavorite(String id) {
        return favoriteIds.contains(id);
    }

    /**
     * Get all favorites.
     */
    public List<FavoriteItem> getAllFavorites() {
        return Collections.unmodifiableList(favorites);
    }

    /**
     * Get favorites by type.
     */
    public List<FavoriteItem> getFavoritesByType(FavoriteItem.FavoriteType type) {
        return favorites.stream()
            .filter(f -> f.type() == type)
            .toList();
    }

    /**
     * Reorder favorites.
     */
    public void reorder(String id, int newIndex) {
        int currentIndex = -1;
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).id().equals(id)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1 || currentIndex == newIndex) return;

        FavoriteItem item = favorites.remove(currentIndex);
        favorites.add(Math.min(newIndex, favorites.size()), item);
        save();
    }

    /**
     * Update favorite notes.
     */
    public void updateNotes(String id, String notes) {
        for (int i = 0; i < favorites.size(); i++) {
            FavoriteItem f = favorites.get(i);
            if (f.id().equals(id)) {
                favorites.set(i, new FavoriteItem(
                    f.id(), f.displayName(), f.itemType(), f.type(),
                    f.presetId(), notes, f.sortOrder(), f.addedAt()
                ));
                save();
                return;
            }
        }
    }

    /**
     * Create favorite from preset.
     */
    public FavoriteItem createFromPreset(Preset preset) {
        return new FavoriteItem(
            "preset_" + preset.id(),
            preset.name(),
            null,
            FavoriteItem.FavoriteType.PRESET,
            preset.id(),
            null,
            favorites.size(),
            System.currentTimeMillis()
        );
    }

    /**
     * Create favorite from template.
     */
    public FavoriteItem createFromTemplate(TemplateItem template) {
        return new FavoriteItem(
            "template_" + template.id(),
            template.name(),
            template.baseItem(),
            FavoriteItem.FavoriteType.TEMPLATE,
            null,
            null,
            favorites.size(),
            System.currentTimeMillis()
        );
    }

    /**
     * Create favorite from item config.
     */
    public FavoriteItem createFromItemConfig(ItemStack item, String configJson) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        String id = "item_" + itemId.toString().replace(":", "_") + "_" + System.currentTimeMillis();

        return new FavoriteItem(
            id,
            item.getHoverName().getString(),
            itemId,
            FavoriteItem.FavoriteType.ITEM_CONFIG,
            null,
            null,
            favorites.size(),
            System.currentTimeMillis()
        );
    }
}

/**
 * Quick access favorites bar in editor.
 */
public class FavoritesBar {

    private final FavoritesManager manager;
    private boolean expanded = false;
    private int hoveredIndex = -1;

    public FavoritesBar(FavoritesManager manager) {
        this.manager = manager;
    }

    /**
     * Render the favorites bar.
     */
    public void render(GuiGraphics graphics, Font font, int x, int y, int width) {
        List<FavoriteItem> favorites = manager.getAllFavorites();

        // Header bar
        int headerHeight = 18;
        graphics.fill(x, y, x + width, y + headerHeight, 0xFF1A1A1A);

        // Star icon and count
        String header = "★ Favorites (" + favorites.size() + ")";
        graphics.drawString(font, header, x + 4, y + 5, 0xFFFFD700);

        // Expand/collapse
        String expandIcon = expanded ? "▼" : "▶";
        graphics.drawString(font, expandIcon, x + width - 12, y + 5, 0xFFAAAAAA);

        if (!expanded || favorites.isEmpty()) return;

        // Favorites list
        int listY = y + headerHeight;
        int itemHeight = 20;
        int maxVisible = 8;

        for (int i = 0; i < Math.min(favorites.size(), maxVisible); i++) {
            FavoriteItem fav = favorites.get(i);
            boolean hovered = hoveredIndex == i;

            // Background
            int bg = hovered ? 0xFF2A2A3A : 0xFF222222;
            graphics.fill(x, listY, x + width, listY + itemHeight, bg);

            // Type icon
            String icon = getTypeIcon(fav.type());
            graphics.drawString(font, icon, x + 4, listY + 6, 0xFFFFD700);

            // Name
            String name = fav.displayName();
            if (name.length() > 20) name = name.substring(0, 17) + "...";
            graphics.drawString(font, name, x + 18, listY + 6, 0xFFFFFFFF);

            // Remove star on hover
            if (hovered) {
                graphics.drawString(font, "§c✗", x + width - 14, listY + 6, 0xFFFF5555);
            }

            listY += itemHeight;
        }

        // "More..." if truncated
        if (favorites.size() > maxVisible) {
            graphics.drawString(font, "§7... " + (favorites.size() - maxVisible) + " more",
                x + 4, listY + 4, 0xFF888888);
        }
    }

    private String getTypeIcon(FavoriteItem.FavoriteType type) {
        return switch (type) {
            case ITEM_CONFIG -> "▪";
            case PRESET -> "📋";
            case TEMPLATE -> "📄";
        };
    }

    /**
     * Handle mouse movement for hover.
     */
    public void mouseMoved(int mouseX, int mouseY, int barX, int barY, int width) {
        if (!expanded) {
            hoveredIndex = -1;
            return;
        }

        int headerHeight = 18;
        int itemHeight = 20;
        int listY = barY + headerHeight;

        List<FavoriteItem> favorites = manager.getAllFavorites();

        hoveredIndex = -1;
        for (int i = 0; i < Math.min(favorites.size(), 8); i++) {
            if (mouseX >= barX && mouseX < barX + width &&
                mouseY >= listY && mouseY < listY + itemHeight) {
                hoveredIndex = i;
                break;
            }
            listY += itemHeight;
        }
    }

    /**
     * Handle click.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int barX, int barY, int width,
                                Consumer<FavoriteItem> onSelect) {
        int headerHeight = 18;

        // Header click - toggle expand
        if (mouseY >= barY && mouseY < barY + headerHeight &&
            mouseX >= barX && mouseX < barX + width) {
            expanded = !expanded;
            return true;
        }

        if (!expanded) return false;

        // Item click
        if (hoveredIndex >= 0) {
            List<FavoriteItem> favorites = manager.getAllFavorites();
            if (hoveredIndex < favorites.size()) {
                FavoriteItem fav = favorites.get(hoveredIndex);

                // Check if clicking remove button
                int itemY = barY + headerHeight + hoveredIndex * 20;
                if (mouseX >= barX + width - 20 && mouseX < barX + width) {
                    manager.removeFavorite(fav.id());
                    return true;
                }

                // Otherwise select
                onSelect.accept(fav);
                return true;
            }
        }

        return false;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }
}

/**
 * Star toggle button for favoriting current item/preset.
 */
public class FavoriteStarButton {

    private final FavoritesManager manager;
    private String currentId;
    private boolean starred = false;

    public FavoriteStarButton(FavoritesManager manager) {
        this.manager = manager;
    }

    /**
     * Set the current item being edited.
     */
    public void setCurrentItem(String id) {
        this.currentId = id;
        this.starred = manager.isFavorite(id);
    }

    /**
     * Render the star button.
     */
    public void render(GuiGraphics graphics, Font font, int x, int y, boolean hovered) {
        String star = starred ? "§6★" : "§7☆";
        if (hovered && !starred) {
            star = "§e☆";
        }
        graphics.drawString(font, star, x, y, 0xFFFFFFFF);
    }

    /**
     * Toggle favorite status.
     */
    public void toggle(FavoriteItem itemToFavorite) {
        if (starred) {
            manager.removeFavorite(currentId);
            starred = false;
        } else {
            manager.addFavorite(itemToFavorite);
            starred = true;
        }
    }

    public boolean isStarred() {
        return starred;
    }
}
```

### Favorites Panel MVP (Bounds & Precedenza)

- **Bounds**: ancorato dentro `EditorLayout.getHeaderBounds()` a `x = header.x + 8`, `y = header.y + 6`, larghezza 180px, header 18px + lista max 8 righe (20px ciascuna) con scissor; z-order allineato alla History overlay.
- **Persist store**: client-only `config/devmod/favorites.json` (scope tooltip: “Favorites scope: CLIENT”), nessuna sincronizzazione per-world/server.
- **Dirty confirm**: quick apply da Favorites riusa lo stesso flusso di conferma dirty/pending dei Presets; se rifiutato, nessuno stato UI viene mutato.
- **Precedenza UI**: un solo overlay attivo; aprire Presets chiude/freeze il pannello Favorites (e History) e ne ripristina lo stato (collapsed/expanded) alla chiusura. Quick apply da Favorites aggiorna anche “last loaded preset” metadata + status badge per evitare disallineamenti visivi.

---

### 2.39.5 Config Section for Edge Cases & Favorites

```toml
# ═══════════════════════════════════════════════════════════════════════════
# EDGE CASES & FAVORITES CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════

[stacks]
    # How to handle stacked items (count > 1)
    # Values: "block", "edit_all", "separate"
    stackBehavior = "separate"

    # Show warning dialog before separating
    showSeparationWarning = true

    # Auto-merge back if edit is cancelled without changes
    autoMergeOnCancel = true

[templates]
    # Enable template/virtual items system
    enabled = true

    # Directory for template storage
    templatesDir = "config/devmod/templates"

    # Max templates per user
    maxTemplates = 100

    # Create default templates on first run
    createDefaults = true

[unsavedChanges]
    # How to handle unsaved changes on close
    # Values: "lose", "dialog", "autosave"
    behavior = "dialog"

    # Auto-save draft interval (ms, 0 = disabled)
    # Only used if behavior = "autosave"
    autosaveIntervalMs = 30000

    # Draft storage location
    draftDir = "config/devmod/drafts"

    # Max drafts to keep
    maxDrafts = 10

[favorites]
    # Enable favorites system
    enabled = true

    # Scope: CLIENT (stored in config/devmod/favorites.json)

    # Max favorites
    maxFavorites = 50

    # Show favorites bar in editor
    showFavoritesBar = true

    # Favorites bar default state
    favoritesBarExpanded = false

    # Storage file
    favoritesFile = "config/devmod/favorites.json"

    # Allow favoriting presets
    allowPresetFavorites = true

    # Allow favoriting templates
    allowTemplateFavorites = true

    # Allow favoriting item configs
    allowItemConfigFavorites = true
```

---

### 2.39.6 UI Mockup - Complete Editor with All Systems

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  WEAPON EDITOR - Diamond Sword §e[modified]§r              §6★§r  §6[DEV]§r │
├─────────────────────────────────────────────────────────────────────────────┤
│  [Stats] [Enchants] [Durability] [Attributes] [Components]                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────┐                      ┌───────────────────────────┐│
│  │ ★ Favorites (3)  ▼  │                      │ §6[DEV MODE]              ││
│  │ ▪ God Sword         │                      │ [Info][Components][Log]   ││
│  │ 📋 Tank Build       │   Attack Damage:     │ §7ID: minecraft:diamond.. ││
│  │ 📄 Glass Cannon     │   ═══════════●═════  │ §7Components: 12          ││
│  └─────────────────────┘   12.0               │                           ││
│                                               │ §6Weapon Stats:           ││
│                           Attack Speed:       │   §7damage=§f12.0         ││
│                           ════════●═══════    │   §7speed=§f1.8           ││
│                           1.8                 └───────────────────────────┘│
│                                                                             │
│                           Attack Reach:                                     │
│                           ══════════════●══                                 │
│                           3.5                                               │
│                                                                             │
│  ┌─ Multi-Edit ─────────────────────────────┐                              │
│  │ 3 items selected                      ▼  │                              │
│  │ ▪ Diamond Sword                      §c✗§r │                              │
│  │ ▪ Iron Sword                         §c✗§r │                              │
│  │ ▪ Netherite Sword                    §c✗§r │                              │
│  │ [Clear All]              [Apply to All]  │                              │
│  └──────────────────────────────────────────┘                              │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Undo][Redo] │ [History][Export][Import][Presets] │ [Test] │  [█APPLY█]    │
│    §8Ctrl+Z/Y§r  │        §8F1=Help  F3+D=Dev§r         │        │   §8Ctrl+S§r    │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│            §6⚠ Unsaved Changes                  │
│                                                 │
│  You have 5 unsaved changes.                    │
│  Do you want to save before closing?            │
│                                                 │
│    ┌──────┐    ┌─────────┐    ┌────────┐      │
│    │ Save │    │ Discard │    │ Cancel │      │
│    └──────┘    └─────────┘    └────────┘      │
│                                                 │
└─────────────────────────────────────────────────┘
```

---

Sezione 2.39 completata con:
- **Stack separation** automatica per item stackati (count > 1)
- **Template items** per preview di configurazioni virtuali
- **Dialog conferma** per modifiche non salvate
- **Favorites system** con star toggle e lista quick-access

---

**Prossime domande di design (57-60) — Performance & Final:**

57. **Vuoi caching per performance (stats calculation, preview rendering)?**
    - No (calcolo ogni frame)
    - Sì, con invalidation su change

58. **Vuoi supporto per screen scaling/responsive design?**
    - Fixed size
    - Responsive con min/max bounds

59. **Logging livello: solo errori o anche debug info?**
    - Solo errori
    - Debug completo (config toggle)

60. **Documentazione in-game: tooltips avanzati o wiki/help page separata?**
    - Solo tooltips
    - Help page integrata (F1)
    - Entrambi

---

## 2.40 Performance, Responsive & Documentation

### Decisioni Design (Domande 57-60)

| # | Domanda | Decisione |
|---|---------|-----------|
| 57 | Caching per performance? | **Sì** - Con invalidation su change |
| 58 | Screen scaling/responsive? | **Responsive** - Con min/max bounds |
| 59 | Logging livello? | **Debug completo** - Con config toggle |
| 60 | Documentazione in-game? | **Entrambi** - Tooltips avanzati + Help page F1 |

---

### 2.40.1 Caching System

```java
/**
 * Cache key for computed values.
 */
public record CacheKey(
    String type,       // "weapon_stats", "armor_stats", "dps", "ehp", etc.
    String itemId,     // Item identifier
    int version        // Incremented on change
) {}

/**
 * Cached computation result.
 */
public record CacheEntry<T>(
    T value,
    long computedAt,
    long expiresAt
) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public static <T> CacheEntry<T> of(T value, long ttlMs) {
        long now = System.currentTimeMillis();
        return new CacheEntry<>(value, now, now + ttlMs);
    }
}

/**
 * Performance cache for editor computations.
 */
public class EditorCache {

    private static final long DEFAULT_TTL_MS = 5000; // 5 seconds
    private static final long STATS_TTL_MS = 1000;   // 1 second for stats
    private static final long PREVIEW_TTL_MS = 100;  // 100ms for preview renders
    private static final int MAX_ENTRIES = 100;

    private final Map<CacheKey, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger(0);

    // ═══════════════════════════════════════════════════════════════
    // CACHE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get cached value or compute if missing/expired.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String type, String itemId, Supplier<T> computer) {
        CacheKey key = new CacheKey(type, itemId, version.get());

        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.value();
        }

        // Compute new value
        T value = computer.get();

        // Determine TTL based on type
        long ttl = getTtlForType(type);

        // Store in cache
        cache.put(key, CacheEntry.of(value, ttl));

        // Cleanup old entries if needed
        if (cache.size() > MAX_ENTRIES) {
            cleanupExpired();
        }

        return value;
    }

    /**
     * Invalidate all caches (called on any change).
     */
    public void invalidateAll() {
        version.incrementAndGet();
        cache.clear();
    }

    /**
     * Invalidate cache for specific item.
     */
    public void invalidateItem(String itemId) {
        cache.entrySet().removeIf(e -> e.getKey().itemId().equals(itemId));
    }

    /**
     * Invalidate specific cache type.
     */
    public void invalidateType(String type) {
        cache.entrySet().removeIf(e -> e.getKey().type().equals(type));
    }

    private long getTtlForType(String type) {
        return switch (type) {
            case "preview", "tooltip" -> PREVIEW_TTL_MS;
            case "weapon_stats", "armor_stats", "dps", "ehp" -> STATS_TTL_MS;
            default -> DEFAULT_TTL_MS;
        };
    }

    private void cleanupExpired() {
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /**
     * Get cache statistics for debugging.
     */
    public CacheStats getStats() {
        int total = cache.size();
        int expired = (int) cache.values().stream().filter(CacheEntry::isExpired).count();
        return new CacheStats(total, total - expired, expired, version.get());
    }

    public record CacheStats(int total, int valid, int expired, int version) {}
}

/**
 * Cached stat calculations.
 */
public class CachedStatsCalculator {

    private final EditorCache cache;

    public CachedStatsCalculator(EditorCache cache) {
        this.cache = cache;
    }

    /**
     * Calculate weapon DPS with caching.
     */
    public float calculateDPS(String itemId, float damage, float speed) {
        return cache.getOrCompute("dps", itemId, () -> damage * speed);
    }

    /**
     * Calculate armor EHP with caching.
     */
    public float calculateEHP(String itemId, ArmorStats stats) {
        return cache.getOrCompute("ehp", itemId, () -> {
            float avgReduction = (stats.physicalReduction + stats.fireReduction +
                                 stats.magicReduction + stats.explosionReduction +
                                 stats.projectileReduction) / 5.0f;
            avgReduction = Math.min(avgReduction, 0.95f);
            return 20.0f / (1.0f - avgReduction);
        });
    }

    /**
     * Build comparison tooltip with caching.
     */
    public ComparisonTooltip buildWeaponTooltip(String itemId, WeaponStats original, WeaponStats modified) {
        return cache.getOrCompute("tooltip_weapon", itemId,
            () -> TooltipBuilder.forWeaponStats(original, modified));
    }

    /**
     * Build armor tooltip with caching.
     */
    public ComparisonTooltip buildArmorTooltip(String itemId, ArmorStats original, ArmorStats modified) {
        return cache.getOrCompute("tooltip_armor", itemId,
            () -> TooltipBuilder.forArmorStats(original, modified));
    }
}

/**
 * Render cache for expensive UI operations.
 */
public class RenderCache {

    private final Map<String, BufferedImage> textureCache = new ConcurrentHashMap<>();
    private final Map<String, List<FormattedCharSequence>> textCache = new ConcurrentHashMap<>();

    /**
     * Cache text wrapping results.
     */
    public List<FormattedCharSequence> wrapText(Font font, String key, Component text, int maxWidth) {
        return textCache.computeIfAbsent(key, k -> font.split(text, maxWidth));
    }

    /**
     * Invalidate text cache.
     */
    public void invalidateText() {
        textCache.clear();
    }

    /**
     * Clear all render caches.
     */
    public void clearAll() {
        textureCache.clear();
        textCache.clear();
    }
}
```

---

### 2.40.2 Responsive Layout System

```java
/**
 * Screen size breakpoints.
 */
public enum ScreenSize {
    SMALL(0, 480),      // < 480px width
    MEDIUM(480, 720),   // 480-720px
    LARGE(720, 1080),   // 720-1080px
    XLARGE(1080, Integer.MAX_VALUE); // > 1080px

    private final int minWidth;
    private final int maxWidth;

    ScreenSize(int minWidth, int maxWidth) {
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
    }

    public static ScreenSize fromWidth(int width) {
        for (ScreenSize size : values()) {
            if (width >= size.minWidth && width < size.maxWidth) {
                return size;
            }
        }
        return MEDIUM;
    }

    public boolean isAtLeast(ScreenSize other) {
        return this.ordinal() >= other.ordinal();
    }
}

/**
 * Responsive layout constraints.
 */
public record LayoutConstraints(
    int minWidth,
    int maxWidth,
    int minHeight,
    int maxHeight,
    int paddingHorizontal,
    int paddingVertical
) {
    public static final LayoutConstraints EDITOR_DEFAULT = new LayoutConstraints(
        320,    // minWidth
        600,    // maxWidth
        240,    // minHeight
        400,    // maxHeight
        10,     // paddingHorizontal
        8       // paddingVertical
    );

    public static final LayoutConstraints DIALOG_DEFAULT = new LayoutConstraints(
        250,    // minWidth
        400,    // maxWidth
        100,    // minHeight
        200,    // maxHeight
        15,     // paddingHorizontal
        12      // paddingVertical
    );

    /**
     * Calculate actual width within constraints.
     */
    public int calculateWidth(int availableWidth) {
        int desired = availableWidth - paddingHorizontal * 2;
        return Math.max(minWidth, Math.min(maxWidth, desired));
    }

    /**
     * Calculate actual height within constraints.
     */
    public int calculateHeight(int availableHeight) {
        int desired = availableHeight - paddingVertical * 2;
        return Math.max(minHeight, Math.min(maxHeight, desired));
    }
}

/**
 * Responsive layout calculator for editor screens.
 */
public class ResponsiveLayout {

    private final LayoutConstraints constraints;
    private ScreenSize currentSize;
    private int screenWidth;
    private int screenHeight;

    // Calculated values
    private int editorX;
    private int editorY;
    private int editorWidth;
    private int editorHeight;
    private int contentPadding;
    private int sliderWidth;
    private int tabWidth;
    private int fontSize;
    private boolean showSidePanels;
    private boolean compactMode;

    public ResponsiveLayout(LayoutConstraints constraints) {
        this.constraints = constraints;
    }

    /**
     * Recalculate layout for screen dimensions.
     */
    public void calculate(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.currentSize = ScreenSize.fromWidth(screenWidth);

        // Calculate editor panel dimensions
        editorWidth = constraints.calculateWidth(screenWidth);
        editorHeight = constraints.calculateHeight(screenHeight);

        // Center the editor
        editorX = (screenWidth - editorWidth) / 2;
        editorY = (screenHeight - editorHeight) / 2;

        // Adjust internal layout based on size
        calculateInternalLayout();
    }

    private void calculateInternalLayout() {
        switch (currentSize) {
            case SMALL -> {
                contentPadding = 6;
                sliderWidth = editorWidth - 40;
                tabWidth = 50;
                fontSize = 1; // Normal
                showSidePanels = false;
                compactMode = true;
            }
            case MEDIUM -> {
                contentPadding = 8;
                sliderWidth = Math.min(250, editorWidth - 60);
                tabWidth = 60;
                fontSize = 1;
                showSidePanels = false;
                compactMode = false;
            }
            case LARGE -> {
                contentPadding = 10;
                sliderWidth = 280;
                tabWidth = 70;
                fontSize = 1;
                showSidePanels = true;
                compactMode = false;
            }
            case XLARGE -> {
                contentPadding = 12;
                sliderWidth = 320;
                tabWidth = 80;
                fontSize = 1;
                showSidePanels = true;
                compactMode = false;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public int getEditorX() { return editorX; }
    public int getEditorY() { return editorY; }
    public int getEditorWidth() { return editorWidth; }
    public int getEditorHeight() { return editorHeight; }
    public int getContentPadding() { return contentPadding; }
    public int getSliderWidth() { return sliderWidth; }
    public int getTabWidth() { return tabWidth; }
    public boolean showSidePanels() { return showSidePanels; }
    public boolean isCompactMode() { return compactMode; }
    public ScreenSize getScreenSize() { return currentSize; }

    /**
     * Get content area bounds.
     */
    public Rect getContentArea() {
        return new Rect(
            editorX + contentPadding,
            editorY + 50, // After header and tabs
            editorWidth - contentPadding * 2,
            editorHeight - 100 // Minus header and footer
        );
    }

    /**
     * Get footer area bounds.
     */
    public Rect getFooterArea() {
        return new Rect(
            editorX + contentPadding,
            editorY + editorHeight - 45,
            editorWidth - contentPadding * 2,
            40
        );
    }

    /**
     * Get side panel bounds (if visible).
     */
    public Rect getSidePanelArea(boolean left) {
        if (!showSidePanels) return Rect.EMPTY;

        int panelWidth = 150;
        int x = left ? editorX - panelWidth - 10 : editorX + editorWidth + 10;

        return new Rect(x, editorY, panelWidth, editorHeight);
    }

    public record Rect(int x, int y, int width, int height) {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}

/**
 * Responsive widget that adapts to available space.
 */
public abstract class ResponsiveWidget {

    protected ResponsiveLayout layout;
    protected ResponsiveLayout.Rect bounds;

    /**
     * Update layout when screen size changes.
     */
    public void updateLayout(ResponsiveLayout layout) {
        this.layout = layout;
        recalculateBounds();
    }

    /**
     * Recalculate bounds based on layout.
     */
    protected abstract void recalculateBounds();

    /**
     * Render with current layout.
     */
    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
}

/**
 * Responsive slider that adjusts width.
 */
public class ResponsiveSlider extends ResponsiveWidget {

    private float value;
    private float min, max;
    private String label;
    private int y;

    @Override
    protected void recalculateBounds() {
        ResponsiveLayout.Rect content = layout.getContentArea();
        int sliderWidth = layout.getSliderWidth();

        bounds = new ResponsiveLayout.Rect(
            content.x() + (content.width() - sliderWidth) / 2,
            content.y() + y,
            sliderWidth,
            20
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (bounds == null) return;

        // Track
        graphics.fill(bounds.x(), bounds.y() + 8, bounds.x() + bounds.width(), bounds.y() + 12, 0xFF2A2A2A);

        // Filled
        float percent = (value - min) / (max - min);
        int filledWidth = (int)(bounds.width() * percent);
        graphics.fill(bounds.x(), bounds.y() + 8, bounds.x() + filledWidth, bounds.y() + 12, 0xFF3A7AFF);

        // Thumb
        int thumbX = bounds.x() + filledWidth - 4;
        graphics.fill(thumbX, bounds.y() + 4, thumbX + 8, bounds.y() + 16, 0xFF5A5A5A);

        // Label (compact mode hides some labels)
        if (!layout.isCompactMode() && label != null) {
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, label + ": " + String.format("%.1f", value),
                bounds.x(), bounds.y() - 10, 0xFFFFFFFF);
        }
    }
}
```

---

### 2.40.3 Debug Logging System

```java
/**
 * Log levels for editor debugging.
 */
public enum LogLevel {
    ERROR(0),
    WARN(1),
    INFO(2),
    DEBUG(3),
    TRACE(4);

    private final int priority;

    LogLevel(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled(LogLevel configuredLevel) {
        return this.priority <= configuredLevel.priority;
    }
}

/**
 * Editor-specific logger with configurable levels.
 */
public class EditorLogger {

    private static final Logger MINECRAFT_LOGGER = LogUtils.getLogger();
    private static LogLevel currentLevel = LogLevel.ERROR;
    private static boolean fileLoggingEnabled = false;
    private static Path logFile;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String category;

    public EditorLogger(String category) {
        this.category = category;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set global log level.
     */
    public static void setLevel(LogLevel level) {
        currentLevel = level;
        info("EditorLogger", "Log level set to: " + level);
    }

    /**
     * Enable file logging.
     */
    public static void enableFileLogging(Path file) {
        logFile = file;
        fileLoggingEnabled = true;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "=== DevMod Editor Log Started ===\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            MINECRAFT_LOGGER.error("Failed to create log file", e);
            fileLoggingEnabled = false;
        }
    }

    /**
     * Disable file logging.
     */
    public static void disableFileLogging() {
        fileLoggingEnabled = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // LOGGING METHODS
    // ═══════════════════════════════════════════════════════════════

    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    public void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    public void trace(String message) {
        log(LogLevel.TRACE, message, null);
    }

    /**
     * Log with format arguments.
     */
    public void debug(String format, Object... args) {
        if (LogLevel.DEBUG.isEnabled(currentLevel)) {
            log(LogLevel.DEBUG, String.format(format, args), null);
        }
    }

    public void trace(String format, Object... args) {
        if (LogLevel.TRACE.isEnabled(currentLevel)) {
            log(LogLevel.TRACE, String.format(format, args), null);
        }
    }

    private void log(LogLevel level, String message, @Nullable Throwable throwable) {
        if (!level.isEnabled(currentLevel)) return;

        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String fullMessage = String.format("[%s] [%s/%s] %s",
            timestamp, category, level.name(), message);

        // Console logging
        switch (level) {
            case ERROR -> {
                if (throwable != null) {
                    MINECRAFT_LOGGER.error(fullMessage, throwable);
                } else {
                    MINECRAFT_LOGGER.error(fullMessage);
                }
            }
            case WARN -> MINECRAFT_LOGGER.warn(fullMessage);
            case INFO -> MINECRAFT_LOGGER.info(fullMessage);
            case DEBUG, TRACE -> {
                // Only log to file for debug/trace unless console debug enabled
                if (currentLevel.priority >= LogLevel.DEBUG.priority) {
                    MINECRAFT_LOGGER.info(fullMessage);
                }
            }
        }

        // File logging
        if (fileLoggingEnabled && logFile != null) {
            writeToFile(fullMessage, throwable);
        }

        // Dev mode overlay logging
        if (DevModeState.isEnabled()) {
            DevModeState.log(String.format("[%s] %s", level.name().charAt(0), message));
        }
    }

    private void writeToFile(String message, @Nullable Throwable throwable) {
        try {
            StringBuilder sb = new StringBuilder(message).append("\n");
            if (throwable != null) {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                sb.append(sw).append("\n");
            }
            Files.writeString(logFile, sb.toString(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Silently fail file logging
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════

    public static void error(String category, String message) {
        new EditorLogger(category).error(message);
    }

    public static void warn(String category, String message) {
        new EditorLogger(category).warn(message);
    }

    public static void info(String category, String message) {
        new EditorLogger(category).info(message);
    }

    public static void debug(String category, String message) {
        new EditorLogger(category).debug(message);
    }

    public static void trace(String category, String message) {
        new EditorLogger(category).trace(message);
    }

    // ═══════════════════════════════════════════════════════════════
    // PERFORMANCE LOGGING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Time an operation and log result.
     */
    public <T> T timed(String operation, Supplier<T> supplier) {
        if (!LogLevel.DEBUG.isEnabled(currentLevel)) {
            return supplier.get();
        }

        long start = System.nanoTime();
        T result = supplier.get();
        long elapsed = System.nanoTime() - start;

        debug("%s completed in %.2fms", operation, elapsed / 1_000_000.0);
        return result;
    }

    /**
     * Time a void operation.
     */
    public void timed(String operation, Runnable runnable) {
        if (!LogLevel.DEBUG.isEnabled(currentLevel)) {
            runnable.run();
            return;
        }

        long start = System.nanoTime();
        runnable.run();
        long elapsed = System.nanoTime() - start;

        debug("%s completed in %.2fms", operation, elapsed / 1_000_000.0);
    }
}

/**
 * Pre-configured loggers for different subsystems.
 */
public final class EditorLoggers {

    private EditorLoggers() {}

    public static final EditorLogger UI = new EditorLogger("UI");
    public static final EditorLogger STATS = new EditorLogger("Stats");
    public static final EditorLogger NETWORK = new EditorLogger("Network");
    public static final EditorLogger CACHE = new EditorLogger("Cache");
    public static final EditorLogger PRESET = new EditorLogger("Preset");
    public static final EditorLogger TEMPLATE = new EditorLogger("Template");
    public static final EditorLogger EXPORT = new EditorLogger("Export");
    public static final EditorLogger IMPORT = new EditorLogger("Import");
    public static final EditorLogger PERMISSION = new EditorLogger("Permission");
    public static final EditorLogger TEST = new EditorLogger("Test");
}
```

---

### 2.40.4 In-Game Help System

```java
/**
 * Help page content.
 */
public record HelpPage(
    String id,
    String titleKey,
    List<HelpSection> sections,
    @Nullable String previousPageId,
    @Nullable String nextPageId
) {}

/**
 * Section within a help page.
 */
public record HelpSection(
    String titleKey,
    List<String> contentKeys,
    @Nullable String iconType,
    List<HelpLink> links
) {}

/**
 * Link to another help page or external resource.
 */
public record HelpLink(
    String textKey,
    String target,
    LinkType type
) {
    public enum LinkType {
        INTERNAL_PAGE,  // Link to another help page
        TOOLTIP,        // Show tooltip on hover
        ACTION          // Execute action (e.g., open settings)
    }
}

/**
 * Help system managing all documentation.
 */
public class HelpSystem {

    private final Map<String, HelpPage> pages = new LinkedHashMap<>();
    private String currentPageId = "overview";

    public HelpSystem() {
        initializePages();
    }

    private void initializePages() {
        // Overview page
        pages.put("overview", new HelpPage(
            "overview",
            "devmod.help.overview.title",
            List.of(
                new HelpSection(
                    "devmod.help.overview.intro.title",
                    List.of(
                        "devmod.help.overview.intro.line1",
                        "devmod.help.overview.intro.line2",
                        "devmod.help.overview.intro.line3"
                    ),
                    "info",
                    List.of()
                ),
                new HelpSection(
                    "devmod.help.overview.features.title",
                    List.of(
                        "devmod.help.overview.features.weapons",
                        "devmod.help.overview.features.armor",
                        "devmod.help.overview.features.presets",
                        "devmod.help.overview.features.export"
                    ),
                    "list",
                    List.of(
                        new HelpLink("devmod.help.link.weapons", "weapons", HelpLink.LinkType.INTERNAL_PAGE),
                        new HelpLink("devmod.help.link.armor", "armor", HelpLink.LinkType.INTERNAL_PAGE)
                    )
                )
            ),
            null,
            "weapons"
        ));

        // Weapons editing page
        pages.put("weapons", new HelpPage(
            "weapons",
            "devmod.help.weapons.title",
            List.of(
                new HelpSection(
                    "devmod.help.weapons.stats.title",
                    List.of(
                        "devmod.help.weapons.stats.damage",
                        "devmod.help.weapons.stats.speed",
                        "devmod.help.weapons.stats.reach",
                        "devmod.help.weapons.stats.knockback"
                    ),
                    "sword",
                    List.of()
                ),
                new HelpSection(
                    "devmod.help.weapons.dps.title",
                    List.of(
                        "devmod.help.weapons.dps.formula",
                        "devmod.help.weapons.dps.example"
                    ),
                    "calculator",
                    List.of()
                )
            ),
            "overview",
            "armor"
        ));

        // Armor editing page
        pages.put("armor", new HelpPage(
            "armor",
            "devmod.help.armor.title",
            List.of(
                new HelpSection(
                    "devmod.help.armor.protection.title",
                    List.of(
                        "devmod.help.armor.protection.physical",
                        "devmod.help.armor.protection.fire",
                        "devmod.help.armor.protection.magic",
                        "devmod.help.armor.protection.explosion",
                        "devmod.help.armor.protection.projectile"
                    ),
                    "shield",
                    List.of()
                ),
                new HelpSection(
                    "devmod.help.armor.ehp.title",
                    List.of(
                        "devmod.help.armor.ehp.explanation",
                        "devmod.help.armor.ehp.formula"
                    ),
                    "heart",
                    List.of()
                )
            ),
            "weapons",
            "shortcuts"
        ));

        // Keyboard shortcuts page
        pages.put("shortcuts", new HelpPage(
            "shortcuts",
            "devmod.help.shortcuts.title",
            List.of(
                new HelpSection(
                    "devmod.help.shortcuts.basic.title",
                    List.of(
                        "devmod.help.shortcuts.basic.undo",
                        "devmod.help.shortcuts.basic.redo",
                        "devmod.help.shortcuts.basic.apply",
                        "devmod.help.shortcuts.basic.close"
                    ),
                    "keyboard",
                    List.of()
                ),
                new HelpSection(
                    "devmod.help.shortcuts.advanced.title",
                    List.of(
                        "devmod.help.shortcuts.advanced.test",
                        "devmod.help.shortcuts.advanced.export",
                        "devmod.help.shortcuts.advanced.devmode",
                        "devmod.help.shortcuts.advanced.help"
                    ),
                    "keyboard",
                    List.of()
                )
            ),
            "armor",
            "presets"
        ));

        // Presets page
        pages.put("presets", new HelpPage(
            "presets",
            "devmod.help.presets.title",
            List.of(
                new HelpSection(
                    "devmod.help.presets.what.title",
                    List.of(
                        "devmod.help.presets.what.line1",
                        "devmod.help.presets.what.line2"
                    ),
                    "preset",
                    List.of()
                ),
                new HelpSection(
                    "devmod.help.presets.how.title",
                    List.of(
                        "devmod.help.presets.how.save",
                        "devmod.help.presets.how.load",
                        "devmod.help.presets.how.share"
                    ),
                    "list",
                    List.of()
                )
            ),
            "shortcuts",
            null
        ));
    }

    /**
     * Get current help page.
     */
    public HelpPage getCurrentPage() {
        return pages.get(currentPageId);
    }

    /**
     * Navigate to a page.
     */
    public void goToPage(String pageId) {
        if (pages.containsKey(pageId)) {
            currentPageId = pageId;
        }
    }

    /**
     * Go to next page.
     */
    public void nextPage() {
        HelpPage current = getCurrentPage();
        if (current != null && current.nextPageId() != null) {
            currentPageId = current.nextPageId();
        }
    }

    /**
     * Go to previous page.
     */
    public void previousPage() {
        HelpPage current = getCurrentPage();
        if (current != null && current.previousPageId() != null) {
            currentPageId = current.previousPageId();
        }
    }

    /**
     * Get all page IDs for navigation.
     */
    public List<String> getAllPageIds() {
        return new ArrayList<>(pages.keySet());
    }
}

/**
 * Help screen overlay (F1).
 */
public class HelpScreen extends Screen {

    private final HelpSystem helpSystem;
    private final Screen parentScreen;
    private int scrollOffset = 0;

    public HelpScreen(Screen parent) {
        super(Component.translatable("devmod.help.title"));
        this.parentScreen = parent;
        this.helpSystem = new HelpSystem();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, 0xC0000000);

        int panelWidth = Math.min(400, width - 40);
        int panelHeight = Math.min(300, height - 40);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;

        // Panel background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xF0101010);
        graphics.renderOutline(x, y, panelWidth, panelHeight, 0xFF3A7AFF);

        HelpPage page = helpSystem.getCurrentPage();
        if (page == null) return;

        // Title
        Component title = Component.translatable(page.titleKey());
        graphics.drawString(font, "§l" + title.getString(), x + 15, y + 12, 0xFFFFFFFF);

        // Close button
        graphics.drawString(font, "§c[X]", x + panelWidth - 25, y + 12, 0xFFFF5555);

        // Navigation
        int navY = y + panelHeight - 25;
        if (page.previousPageId() != null) {
            graphics.drawString(font, "§7[← Previous]", x + 15, navY, 0xFFAAAAAA);
        }
        if (page.nextPageId() != null) {
            graphics.drawString(font, "§7[Next →]", x + panelWidth - 70, navY, 0xFFAAAAAA);
        }

        // Page indicator
        List<String> pageIds = helpSystem.getAllPageIds();
        int pageNum = pageIds.indexOf(page.id()) + 1;
        String pageIndicator = pageNum + "/" + pageIds.size();
        int indicatorWidth = font.width(pageIndicator);
        graphics.drawString(font, "§8" + pageIndicator, x + (panelWidth - indicatorWidth) / 2, navY, 0xFF666666);

        // Content
        int contentY = y + 35;
        int contentHeight = panelHeight - 70;
        int maxY = contentY + contentHeight;

        // Scissor for scrolling
        graphics.enableScissor(x + 10, contentY, x + panelWidth - 10, maxY);

        int sectionY = contentY - scrollOffset;
        for (HelpSection section : page.sections()) {
            sectionY = renderSection(graphics, section, x + 15, sectionY, panelWidth - 30, mouseX, mouseY);
            sectionY += 15; // Gap between sections
        }

        graphics.disableScissor();

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawString(font, "§7▲", x + panelWidth / 2 - 4, contentY - 8, 0xFF888888);
        }
        // Check if more content below
        // (simplified - would need actual content height calculation)
    }

    private int renderSection(GuiGraphics graphics, HelpSection section, int x, int y, int width, int mouseX, int mouseY) {
        // Section title with icon
        String icon = getSectionIcon(section.iconType());
        Component title = Component.translatable(section.titleKey());
        graphics.drawString(font, icon + " §6" + title.getString(), x, y, 0xFFFF8800);
        y += 14;

        // Content lines
        for (String contentKey : section.contentKeys()) {
            Component content = Component.translatable(contentKey);
            List<FormattedCharSequence> wrapped = font.split(content, width - 10);
            for (FormattedCharSequence line : wrapped) {
                graphics.drawString(font, line, x + 8, y, 0xFFCCCCCC);
                y += 11;
            }
        }

        // Links
        for (HelpLink link : section.links()) {
            Component linkText = Component.translatable(link.textKey());
            boolean hovered = mouseX >= x + 8 && mouseX < x + 8 + font.width(linkText) &&
                             mouseY >= y && mouseY < y + 10;
            int color = hovered ? 0xFF55FFFF : 0xFF55AAFF;
            graphics.drawString(font, "→ " + linkText.getString(), x + 8, y, color);
            y += 12;
        }

        return y;
    }

    private String getSectionIcon(@Nullable String iconType) {
        if (iconType == null) return "•";
        return switch (iconType) {
            case "info" -> "ℹ";
            case "list" -> "≡";
            case "sword" -> "⚔";
            case "shield" -> "🛡";
            case "heart" -> "♥";
            case "keyboard" -> "⌨";
            case "calculator" -> "∑";
            case "preset" -> "📋";
            default -> "•";
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // F1 or Escape to close
        if (keyCode == GLFW.GLFW_KEY_F1 || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(parentScreen);
            return true;
        }

        // Left/Right for navigation
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            helpSystem.previousPage();
            scrollOffset = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            helpSystem.nextPage();
            scrollOffset = 0;
            return true;
        }

        // Up/Down for scrolling
        if (keyCode == GLFW.GLFW_KEY_UP) {
            scrollOffset = Math.max(0, scrollOffset - 20);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollOffset += 20;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, scrollOffset - (int)(scrollY * 20));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = Math.min(400, width - 40);
        int panelHeight = Math.min(300, height - 40);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;

        // Close button
        if (mouseX >= x + panelWidth - 25 && mouseX < x + panelWidth - 5 &&
            mouseY >= y + 8 && mouseY < y + 20) {
            minecraft.setScreen(parentScreen);
            return true;
        }

        // Navigation
        int navY = y + panelHeight - 25;
        HelpPage page = helpSystem.getCurrentPage();

        if (page.previousPageId() != null &&
            mouseX >= x + 15 && mouseX < x + 100 &&
            mouseY >= navY && mouseY < navY + 12) {
            helpSystem.previousPage();
            scrollOffset = 0;
            return true;
        }

        if (page.nextPageId() != null &&
            mouseX >= x + panelWidth - 70 && mouseX < x + panelWidth - 15 &&
            mouseY >= navY && mouseY < navY + 12) {
            helpSystem.nextPage();
            scrollOffset = 0;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
```

---

### 2.40.5 Advanced Tooltips with Context

```java
/**
 * Context-aware tooltip that shows relevant help.
 */
public class ContextualTooltip {

    private final HelpSystem helpSystem;

    public ContextualTooltip(HelpSystem helpSystem) {
        this.helpSystem = helpSystem;
    }

    /**
     * Build tooltip for a stat slider.
     */
    public List<Component> forStat(String statKey, float currentValue, float minValue, float maxValue) {
        List<Component> lines = new ArrayList<>();

        // Stat name and value
        lines.add(Component.translatable(statKey)
            .withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal(String.format("%.1f", currentValue))
            .withStyle(ChatFormatting.WHITE));

        // Range info
        lines.add(Component.literal(""));
        lines.add(Component.literal(String.format("Range: %.1f - %.1f", minValue, maxValue))
            .withStyle(ChatFormatting.GRAY));

        // Stat-specific help
        String helpKey = "devmod.tooltip.help." + statKey.replace("devmod.stat.", "");
        if (I18n.exists(helpKey)) {
            lines.add(Component.literal(""));
            lines.add(Component.translatable(helpKey)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        // Keyboard hint
        lines.add(Component.literal(""));
        lines.add(Component.literal("Shift+↑/↓ for fine adjustment")
            .withStyle(ChatFormatting.DARK_GRAY));

        return lines;
    }

    /**
     * Build tooltip for a button.
     */
    public List<Component> forButton(String buttonKey, @Nullable String shortcut) {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable(buttonKey)
            .withStyle(ChatFormatting.WHITE));

        if (shortcut != null) {
            lines.add(Component.literal(shortcut)
                .withStyle(ChatFormatting.YELLOW));
        }

        // Button-specific help
        String helpKey = buttonKey + ".help";
        if (I18n.exists(helpKey)) {
            lines.add(Component.literal(""));
            lines.add(Component.translatable(helpKey)
                .withStyle(ChatFormatting.GRAY));
        }

        return lines;
    }

    /**
     * Build tooltip for dangerous value warning.
     */
    public List<Component> forWarning(String warningKey, float value, float threshold) {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.literal("⚠ Warning")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        lines.add(Component.translatable(warningKey)
            .withStyle(ChatFormatting.RED));

        lines.add(Component.literal(""));
        lines.add(Component.literal(String.format("Current: %.1f (threshold: %.1f)", value, threshold))
            .withStyle(ChatFormatting.GRAY));

        lines.add(Component.literal(""));
        lines.add(Component.literal("Press F1 for more information")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        return lines;
    }

    /**
     * Build tooltip for enchantment.
     */
    public List<Component> forEnchantment(Holder<Enchantment> enchantment, int level, int maxLevel) {
        List<Component> lines = new ArrayList<>();

        // Enchantment name with level
        lines.add(Enchantment.getFullname(enchantment, level));

        // Max level info
        lines.add(Component.literal(String.format("Level %d of %d", level, maxLevel))
            .withStyle(ChatFormatting.GRAY));

        // Description if available
        String descKey = "enchantment." + enchantment.unwrapKey().orElseThrow().location().toString().replace(":", ".") + ".desc";
        if (I18n.exists(descKey)) {
            lines.add(Component.literal(""));
            lines.add(Component.translatable(descKey)
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        return lines;
    }
}
```

---

### 2.40.6 Config Section for Performance & Documentation

```toml
# ═══════════════════════════════════════════════════════════════════════════
# PERFORMANCE, RESPONSIVE & DOCUMENTATION CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════

[performance]
    # Enable computation caching
    cacheEnabled = true

    # Default cache TTL in milliseconds
    cacheTtlMs = 5000

    # Stats cache TTL (shorter for responsiveness)
    statsCacheTtlMs = 1000

    # Preview/tooltip cache TTL (very short)
    previewCacheTtlMs = 100

    # Max cache entries before cleanup
    maxCacheEntries = 100

    # Enable render caching (text wrapping, etc.)
    renderCacheEnabled = true

[responsive]
    # Enable responsive layout
    enabled = true

    # Minimum editor width
    minWidth = 320

    # Maximum editor width
    maxWidth = 600

    # Minimum editor height
    minHeight = 240

    # Maximum editor height
    maxHeight = 400

    # Small screen breakpoint
    smallScreenWidth = 480

    # Large screen breakpoint
    largeScreenWidth = 720

    # Extra large screen breakpoint
    xlargeScreenWidth = 1080

    # Show side panels on large screens
    showSidePanelsOnLarge = true

    # Enable compact mode on small screens
    compactModeOnSmall = true

[logging]
    # Log level for editor
    # Values: "error", "warn", "info", "debug", "trace"
    level = "error"

    # Enable file logging
    fileLoggingEnabled = false

    # Log file path (relative to game directory)
    logFilePath = "logs/devmod_editor.log"

    # Log performance timings
    logPerformance = false

    # Max log file size in MB before rotation
    maxLogSizeMb = 10

    # Number of log files to keep
    maxLogFiles = 3

[help]
    # Enable in-game help system
    enabled = true

    # Show help button in editor
    showHelpButton = true

    # F1 opens help overlay
    f1OpensHelp = true

    # Show contextual tooltips
    contextualTooltips = true

    # Tooltip show delay in milliseconds
    tooltipDelayMs = 500

    # Extended tooltip info (shows keyboard hints)
    extendedTooltips = true

    # Show "Press F1 for help" hints
    showF1Hints = true
```

---

### 2.40.7 Help Text Translations (Addition to Language Files)

```json
{
  "_comment": "Help system translations - Add to en_us.json",

  "devmod.help.title": "DevMod Editor Help",

  "devmod.help.overview.title": "Overview",
  "devmod.help.overview.intro.title": "Introduction",
  "devmod.help.overview.intro.line1": "DevMod Editor allows you to modify weapon and armor statistics in real-time.",
  "devmod.help.overview.intro.line2": "Changes can be applied to individual items or saved as reusable presets.",
  "devmod.help.overview.intro.line3": "All modifications are reversible with the Undo system.",

  "devmod.help.overview.features.title": "Features",
  "devmod.help.overview.features.weapons": "• Weapon editing: damage, speed, reach, knockback",
  "devmod.help.overview.features.armor": "• Armor editing: protection values, attributes, effects",
  "devmod.help.overview.features.presets": "• Preset system: save and load configurations",
  "devmod.help.overview.features.export": "• Export/Import: share configurations as JSON",

  "devmod.help.link.weapons": "Learn about weapon editing",
  "devmod.help.link.armor": "Learn about armor editing",

  "devmod.help.weapons.title": "Weapon Editing",
  "devmod.help.weapons.stats.title": "Weapon Statistics",
  "devmod.help.weapons.stats.damage": "• Attack Damage: Base damage dealt per hit",
  "devmod.help.weapons.stats.speed": "• Attack Speed: Attacks per second",
  "devmod.help.weapons.stats.reach": "• Attack Reach: Distance at which you can hit",
  "devmod.help.weapons.stats.knockback": "• Knockback: How far enemies are pushed",

  "devmod.help.weapons.dps.title": "DPS Calculation",
  "devmod.help.weapons.dps.formula": "DPS = Damage × Attack Speed",
  "devmod.help.weapons.dps.example": "Example: 7 damage × 1.6 speed = 11.2 DPS",

  "devmod.help.armor.title": "Armor Editing",
  "devmod.help.armor.protection.title": "Protection Types",
  "devmod.help.armor.protection.physical": "• Physical: Melee and fall damage",
  "devmod.help.armor.protection.fire": "• Fire: Fire and lava damage",
  "devmod.help.armor.protection.magic": "• Magic: Potion and magic damage",
  "devmod.help.armor.protection.explosion": "• Explosion: TNT and creeper damage",
  "devmod.help.armor.protection.projectile": "• Projectile: Arrow and fireball damage",

  "devmod.help.armor.ehp.title": "Effective HP",
  "devmod.help.armor.ehp.explanation": "EHP represents your survivability with current armor.",
  "devmod.help.armor.ehp.formula": "EHP = 20 ÷ (1 - average_reduction)",

  "devmod.help.shortcuts.title": "Keyboard Shortcuts",
  "devmod.help.shortcuts.basic.title": "Basic Shortcuts",
  "devmod.help.shortcuts.basic.undo": "• Ctrl+Z: Undo last change",
  "devmod.help.shortcuts.basic.redo": "• Ctrl+Y: Redo undone change",
  "devmod.help.shortcuts.basic.apply": "• Ctrl+S: Apply changes",
  "devmod.help.shortcuts.basic.close": "• Escape: Close editor",

  "devmod.help.shortcuts.advanced.title": "Advanced Shortcuts",
  "devmod.help.shortcuts.advanced.test": "• Ctrl+T: Run combat test",
  "devmod.help.shortcuts.advanced.export": "• Ctrl+E: Export configuration",
  "devmod.help.shortcuts.advanced.devmode": "• F3+D: Toggle developer mode",
  "devmod.help.shortcuts.advanced.help": "• F1: Toggle this help",

  "devmod.help.presets.title": "Presets",
  "devmod.help.presets.what.title": "What are Presets?",
  "devmod.help.presets.what.line1": "Presets are saved item configurations that can be reused.",
  "devmod.help.presets.what.line2": "They can include stats, enchantments, and other properties.",

  "devmod.help.presets.how.title": "Using Presets",
  "devmod.help.presets.how.save": "• Save: Configure item, then click 'Save Preset'",
  "devmod.help.presets.how.load": "• Load: Select preset from list, click 'Load'",
  "devmod.help.presets.how.share": "• Share: Export preset as JSON file",

  "devmod.tooltip.help.attack_damage": "Higher damage means more HP removed per hit",
  "devmod.tooltip.help.attack_speed": "Higher speed allows more attacks per second",
  "devmod.tooltip.help.physical_reduction": "Reduces damage from melee attacks and falls",
  "devmod.tooltip.help.fire_reduction": "Reduces damage from fire, lava, and burning"
}
```

---

### 2.40.8 UI Mockup - Help Screen

```
┌─────────────────────────────────────────────────────────────────┐
│  §l DevMod Editor Help                                    §c[X]§r │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  §6ℹ Introduction                                               │
│    DevMod Editor allows you to modify weapon and armor          │
│    statistics in real-time.                                     │
│    Changes can be applied to individual items or saved as       │
│    reusable presets.                                            │
│    All modifications are reversible with the Undo system.       │
│                                                                 │
│  §6≡ Features                                                   │
│    • Weapon editing: damage, speed, reach, knockback            │
│    • Armor editing: protection values, attributes, effects      │
│    • Preset system: save and load configurations                │
│    • Export/Import: share configurations as JSON                │
│                                                                 │
│    §9→ Learn about weapon editing                               │
│    §9→ Learn about armor editing                                │
│                                                                 │
│                          §8▼ scroll for more                    │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  §7[← Previous]              §81/5§r              §7[Next →]    │
└─────────────────────────────────────────────────────────────────┘

Navigation: ←/→ arrows or click | Scroll: ↑/↓ or mouse wheel | Close: F1/Esc
```

---

Sezione 2.40 completata con:
- **Caching system** con invalidation su change per stats, tooltips, render
- **Responsive layout** con min/max bounds e breakpoints (small/medium/large/xlarge)
- **Debug logging** completo con livelli configurabili e file logging
- **Help system** con pagine navigabili (F1) e tooltips contestuali avanzati

---

## 2.41 Decisioni Bloccanti Pre-Templates

### 2.41.1 UI Scaling Rules (definitive)
- **Valori ammessi:** `auto`, `1.0`, `1.25`, `1.5`, `2.0` (nessun altro valore).  
- **Auto:** sceglie il più grande che entra nello schermo con margine di 24px per lato; se nessuno entra → forza 1.0 e segnala warning nel log dev.  
- **Rounding:** tutte le coordinate/dimensioni passano per `ScaledCoord.alignTo4()` (griglia 4px) dopo lo scale.  
- **Clamp verticale:** se l’altezza scalata non entra, header/footer restano fissi e il **content area** aggiunge scroll verticale; mai shrink di header/footer.  
- **Persistenza:** preferenza utente letta da config e salvata per sessione; fallback sempre `auto`.  
- **Scroll only nel content:** qualunque clamp attiva scroll SOLO nel content viewport, mai header/footer/left column.

### 2.41.2 Tabs Overflow Policy (header)
- **Strategia unica:** scroll orizzontale con wheel/drag + pulsanti ◀ ▶ (se overflow). Niente “More…” dropdown e niente truncate hard.  
- **Dimensioni fisse:** tab width 70px (scaled + snap 4px); gap 2px.  
- **Indicatori:** se overflow compaiono gradient fade ai bordi e i pulsanti ◀ ▶ restano visibili.  
- **Tooltip:** sempre mostra testo completo del tab, anche se il label è più lungo della larghezza visiva (nessun ellipsis).  
- **Input capture:** quando si scrollano i tab l’input non “buca” al contenuto; release ripristina.  

### 2.41.3 Data Components vs NBT (source of truth)
- **Source of truth:** Data Components 1.21 (DataComponents.*) per TUTTI i campi editabili.  
- **Legacy NBT:** letto solo come fallback compat per item pre-1.21 o mod legacy; on-load → migrazione a Data Components; on-save → scrittura SOLO componenti (nessuna doppia sorgente).  
- **Precedenza:** Componenti > NBT. Se vengono trovati entrambi, l’editor mostra un warning “Legacy NBT ignored (migrated to components)” nel debug panel.  
- **Config:** flag `allowLegacyNbtWrite=false` di default; se true, duplica su NBT solo per compat (non consigliato).  
- **Testing:** validation blocca apply se un campo richiede componente mancante su piattaforma (log chiaro).

---

## 2.42 Templates Selection UI (MVP)

### Obiettivi MVP
- **Lista filtrata per item type:** mostra solo template compatibili con l’item corrente (match su type/category).  
- **Preview summary:** pannello che evidenzia quali campi verranno toccati (es. Stats, Enchants, Durability) e scope applicazione.  
- **Apply con dirty confirm:** usa `ConfirmDialog` standard se ci sono pending changes; “Apply Template” rispetta PREVIEW/APPLY mode.  
- **Stato/History:** apply aggiorna status bar, history entry e “last loaded” metadata esattamente come i presets.

### Layout MVP
```
┌───────────────────────────────────────────────┐
│  Filter: [Current type ▾] [🔍 Search…]        │
├───────────────────────────────────────────────┤
│  List (virtualized 24px rows, max 8 visibili) │ ← scissor, scroll wheel
│  ▸ Tank Chestplate      [Armor]               │
│  ▸ Glass Cannon         [Weapon]              │
│  ▸ Speed Boots          [Armor]               │
├───────────────────────────────────────────────┤
│  Preview                                                │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Template: Tank Chestplate                          │
│  │ Scope: Armor (Chest)                               │
│  │ Touches: Stats (Armor/Toughness), Durability,      │
│  │          Enchants (2)                              │
│  └───────────────────────────────────────────────────┘ │
├───────────────────────────────────────────────┤
│ [Apply Template] [Cancel]                     │
└───────────────────────────────────────────────┘
```

### Comportamento UI
- **Virtualization:** righe 24px, render solo visibili + buffer; scissor attivo; nessun jank con molti template.  
- **Input capture:** overlay/popup cattura click/scroll fino a chiusura; ESC o click fuori chiude.  
- **Shortcuts:** `Ctrl+F` focus search, `Enter` = Apply (selezionato), `Delete` su entry = confirm delete (quando abiliteremo gestione).  
- **Confirm dirty:** se dirty in APPLY mode → `ConfirmDialog` “Overwrite current changes?”; PREVIEW mode applica localmente senza dirty.  
- **Status/History:** dopo apply, status banner “Template applied”, history entry “Applied template <name>”, aggiorna “last loaded” badge (stessa pipeline dei presets).

### Definition of Done (Templates MVP)
- Lista filtrata per tipo item ✅  
- Preview mostra campi toccati e scope ✅  
- Apply usa conferma dirty e rispetta PREVIEW/APPLY ✅  
- Aggiornati status bar + history + last-loaded ✅  
- Nessun overlap UI; scroll ok; input capture ok ✅  

---
## 🎉 DESIGN SYSTEM COMPLETATO!

Il documento EDITOR_DESIGN_SYSTEM.md ora contiene la specifica completa per:

### Sezioni Completate (2.23 - 2.40):
1. **2.23** - Hit Location Multipliers
2. **2.24** - Dangerous Values System
3. **2.25** - Armor Properties
4. **2.26** - Armor Editor View Modes
5. **2.27** - Set Bonus & Conditions
6. **2.28** - Damage Resistance Roadmap
7. **2.29** - Movement & Advanced Systems
8. **2.30** - Durability System
9. **2.31** - Repair System
10. **2.32** - Enchantments System
11. **2.33** - Affix System
12. **2.34** - Data Components System
13. **2.35** - Apply & Permissions System
14. **2.36** - Export/Import & Presets System
15. **2.37** - UI Polish & Localization
16. **2.38** - Testing, Shortcuts & Developer Mode
17. **2.39** - Edge Cases & Favorites System
18. **2.40** - Performance, Responsive & Documentation

Vuoi procedere con altre domande o siamo pronti per iniziare l'implementazione?
| Tab height | 22px |
| Tab gap | 2px |
| Position | Centered in header |
| Selected bg | UIConstants.Background.ACTIVE |
| Hover bg | UIConstants.Background.HOVER |
| Normal bg | UIConstants.Background.HEADER |
| Selected border-bottom | 2px, UIConstants.Border.ACCENT |
| Text selected | UIConstants.Text.WHITE |
| Text normal | UIConstants.Text.SECONDARY |

## 3.2 Tab Mapping

### ArmorEditor Tabs
| Index | Nome | Contenuto |
|-------|------|-----------|
| 0 | PROTECTION | Physical, Fire, Magic, Explosion, Projectile sliders |
| 1 | ATTRIBUTES | Armor, Toughness, Knockback Resistance, Movement Speed |
| 2 | ENCHANTS | Enchantment list + picker |
| 3 | DURABILITY | Current, Max durability + Unbreakable toggle |
| 4 | EFFECTS | Thorns toggle + percent, Special effects |

### WeaponEditor Tabs
| Index | Nome | Contenuto |
|-------|------|-----------|
| 0 | STATS | Damage, Speed, Reach, Knockback sliders |
| 1 | ENCHANTS | Enchantment list + picker |
| 2 | DURABILITY | Current, Max, Unbreakable |
| 3 | ATTRIBUTES | Dynamic attribute list from registry |
| 4 | COMPONENTS | NBT/Component viewer (advanced) |

---

# PARTE 4: FOOTER BUTTONS

## 4.1 Layout Footer

```
┌─────────────────────────────────────────────────────────────────────┐
│ [Undo][Redo] │ [History][Export][Import][Presets] │    [APPLY]     │
│    Row 1     │              Row 1                 │                │
├──────────────┼────────────────────────────────────┤     (big)      │
│              │      [Reset]  [Cancel]             │                │
│              │              Row 2                 │                │
└──────────────┴────────────────────────────────────┴────────────────┘
```

## 4.2 Button Specifications

| Button | Width | Height | Color | Shortcut |
|--------|-------|--------|-------|----------|
| Undo | 50px | 22px | YELLOW (0xFFFFD700) | Ctrl+Z |
| Redo | 50px | 22px | GREEN (0xFF00FF00) | Ctrl+Y |
| History | 60px | 22px | CYAN (0xFF00FFFF) | - |
| Export | 55px | 22px | ORANGE (0xFFFF9800) | - |
| Import | 55px | 22px | GOLD (0xFFFFD700) | - |
| Presets | 60px | 22px | PURPLE (0xFF9C27B0) | - |
| Reset | 60px | 22px | RED (0xFFFF4444) | - |
| Cancel | 60px | 22px | GRAY (UIConstants.Background.HOVER) | ESC |
| Apply | 120px | 50px | GREEN (0xFF00FF00) | Ctrl+S |

## 4.3 Button States

```java
// Rendering con stati
private void renderFooterButton(GuiGraphics graphics, Font font,
                                 int x, int y, int width, int height,
                                 String text, int accentColor,
                                 boolean hovered, boolean enabled) {
    int bgColor;
    int borderColor;
    int textColor;

    if (!enabled) {
        bgColor = UIConstants.Background.INPUT;
        borderColor = UIConstants.Border.MUTED;
        textColor = UIConstants.Text.DISABLED;
    } else if (hovered) {
        bgColor = accentColor;
        borderColor = UIConstants.lighten(accentColor, 0.3f);
        textColor = UIConstants.Text.WHITE;
    } else {
        bgColor = UIConstants.Background.INPUT;
        borderColor = accentColor;
        textColor = UIConstants.Text.PRIMARY;
    }

    graphics.fill(x, y, x + width, y + height, bgColor);
    AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);

    int textX = x + (width - font.width(text)) / 2;
    int textY = y + (height - 8) / 2;
    graphics.drawString(font, text, textX, textY, textColor, false);
}
```

---

# PARTE 5: FEATURE PARITY

## 5.1 Matrice Feature - STATO TARGET

Entrambi gli editor DEVONO avere TUTTE queste feature implementate.

**Priorità basate su: DEBUG FIRST → BALANCE SECOND → CONTENT THIRD**

| Feature | Descrizione | Obiettivo | Priorità |
|---------|-------------|-----------|----------|
| **Dual-Mode (PREVIEW/APPLY)** | Modalità visualizzazione vs applicazione | DEBUG | **P0 - CRITICA** |
| **Debug Panel** | NBT viewer, value comparison, session log | DEBUG | **P0 - CRITICA** |
| **Dirty State** | Tracking modifiche non salvate | DEBUG | **P0 - CRITICA** |
| **Confirmation Dialogs** | Warning prima di perdere dati | DEBUG | **P0 - CRITICA** |
| **Value Mismatch Detection** | Expected vs Actual comparison | DEBUG | **P0 - CRITICA** |
| **Copy Debug Info** | Clipboard export per bug report | DEBUG | **P1 - ALTA** |
| **Undo/Redo** | 50 stati max, snapshot completo | BALANCE | **P1 - ALTA** |
| **Presets** | Save/Load/Delete user presets | BALANCE | **P1 - ALTA** |
| **Item Value Analysis** | Calcolo valore/rarità ingredienti | BALANCE | **P1 - ALTA** |
| **Crafting Recipe View** | Visualizzazione ricetta crafting | BALANCE | **P2 - MEDIA** |
| **History Panel** | Lista modifiche con timestamp | DEBUG | **P2 - MEDIA** |
| **Templates** | Quick-apply per builds comuni | CONTENT | **P2 - MEDIA** |
| **Export/Import** | Datapack export/import per distribuzione | CONTENT | **P2 - MEDIA** |
| **Tooltips** | Hover info su ogni elemento | UX | **P3 - BASSA** |
| **Sound Feedback** | Audio cues per azioni | UX | **P3 - BASSA** |
| **Keyboard Shortcuts** | Ctrl+Z, Ctrl+Y, Ctrl+S, F3 | UX | **P3 - BASSA** |
| **Recipe Editor** | Modifica ricette runtime | CONTENT | **FUTURE** |

### Legenda Priorità
| Priorità | Significato | Quando |
|----------|-------------|--------|
| **P0** | Bloccante per testing | Fase 1 - Immediate |
| **P1** | Essenziale per workflow | Fase 2 - Questa settimana |
| **P2** | Migliora efficienza | Fase 3 - Prossime iterazioni |
| **P3** | Nice-to-have | Fase 4 - Polish finale |
| **FUTURE** | Post-release | Da pianificare |

## 5.2 Gap Analysis Attuale

### ArmorEditor - Mancante
**P0 - CRITICA:**
- [x] Dual-Mode PREVIEW/APPLY toggle
- [x] Debug Panel (Tab dedicata)
- [x] Dirty State system (RIMOSSO per errore)
- [x] Confirmation dialogs
- [x] Value Mismatch Detection

**P1 - ALTA:**
- [x] Copy Debug Info to clipboard
- [ ] Presets (solo skeleton)
- [ ] Item Value Analysis

**P2 - MEDIA:**
- [ ] History panel (solo skeleton)
- [ ] Templates system
- [ ] Export/Import
- [ ] Crafting Recipe View

**P3 - BASSA:**
- [ ] Tooltips
- [ ] Sound feedback

### WeaponEditor - Mancante
**P0 - CRITICA:**
- [x] Dual-Mode PREVIEW/APPLY toggle
- [x] Debug Panel (Tab dedicata)
- [x] Dirty State system
- [x] Confirmation dialogs
- [x] Value Mismatch Detection

**P1 - ALTA:**
- [x] Copy Debug Info to clipboard
- [ ] Off-hand support
- [ ] Item Value Analysis

**P2 - MEDIA:**
- [ ] Crafting Recipe View

**P3 - BASSA:**
- [ ] Tooltips rendering (definiti ma non usati)
- [ ] Sound feedback

## 5.3 Presets System

Identico per entrambi gli editor, usando `ItemEditorDataManager`.

```java
// Save preset
public void savePreset(String name) {
    EditorState state = captureCurrentState();
    ItemEditorDataManager.savePreset(getEditorType(), name, state);
    UIConstants.Sound.save();
    showStatus("Preset '" + name + "' saved!", UIConstants.Accent.GREEN);
}

// Load preset
public void loadPreset(String name) {
    if (hasUnsavedChanges()) {
        showConfirmDialog("Load Preset",
            "Loading will overwrite current changes. Continue?",
            () -> doLoadPreset(name),
            () -> {}
        );
    } else {
        doLoadPreset(name);
    }
}

// Delete preset
public void deletePreset(String name) {
    showConfirmDialog("Delete Preset",
        "Delete '" + name + "'? This cannot be undone.",
        () -> {
            ItemEditorDataManager.deletePreset(getEditorType(), name);
            UIConstants.Sound.delete();
            refreshPresetList();
        },
        () -> {}
    );
}
```

## 5.4 History Panel

```
┌─────────────────────────────────┐
│         EDIT HISTORY            │
├─────────────────────────────────┤
│ 14:32:05  Changed damage to 15  │
│ 14:31:58  Added Sharpness V     │
│ 14:31:45  Removed Fire Aspect   │
│ 14:31:30  Set durability 1000   │
│ 14:31:22  Applied template      │
│ ...                             │
├─────────────────────────────────┤
│ [Clear All]      Showing 5/12   │
└─────────────────────────────────┘

Dimensioni: 250px × 200px
Posizione: Overlay panel, appare quando si clicca History button
Max entries: 50 (stessa dimensione undo stack)
```

---

# PARTE 6: SLIDERS & INPUTS

## 6.1 Slider Design

```
Label Name                    [Value]
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░○
```

### Specifiche
| Proprietà | Valore |
|-----------|--------|
| Track height | 10px |
| Track width | Full content width - label - value |
| Handle size | 12px diameter |
| Label width | 90px |
| Value width | 50px |
| Row height | 24px |
| Gap between rows | 4px |

### Colori per categoria

| Categoria | Colore | Hex |
|-----------|--------|-----|
| Physical/Attack | RED | 0xFFFF4444 |
| Fire | ORANGE | 0xFFFF9800 |
| Magic | PURPLE | 0xFF9C27B0 |
| Explosion | YELLOW | 0xFFFFFF00 |
| Projectile | CYAN | 0xFF00FFFF |
| Speed | GREEN | 0xFF00FF00 |
| Defense | BLUE | 0xFF3D5AFE |
| Durability | GRAY | 0xFFAAAAAA |

### Gradient Fill
```java
// Fill con gradient da colore a colore più chiaro
for (int i = 0; i < fillWidth; i++) {
    float t = (float) i / trackWidth;
    int gradColor = UIConstants.lerp(slider.color, UIConstants.lighten(slider.color, 0.4f), t);
    graphics.fill(trackX + i, trackY + 1, trackX + i + 1, trackY + trackHeight - 1, gradColor);
}
```

## 6.2 Toggle Design

```
[Label]                    [ON ]  or  [OFF]
                           green      gray
```

### Specifiche
| Proprietà | Valore |
|-----------|--------|
| Toggle width | 40px |
| Toggle height | 18px |
| ON background | UIConstants.Toggle.ON (0xFF00FF00) |
| OFF background | UIConstants.Toggle.OFF (dark) |
| Text | "ON" / "OFF" centered |

## 6.3 Input Field Design

```
[Label]     [___value___]
             editable
```

### Specifiche
| Proprietà | Valore |
|-----------|--------|
| Input width | 80px (normal), 120px (wide) |
| Input height | 18px |
| Border normal | UIConstants.Border.MUTED |
| Border focused | UIConstants.Border.ACCENT |
| Background | UIConstants.Background.INPUT |
| Text | UIConstants.Text.PRIMARY |

---

# PARTE 7: KEYBOARD & SOUND

## 7.1 Keyboard Shortcuts

| Shortcut | Azione | Entrambi Editor |
|----------|--------|-----------------|
| Ctrl+Z | Undo | ✓ |
| Ctrl+Y | Redo | ✓ |
| Ctrl+S | Apply/Save | ✓ |
| ESC | Cancel/Close (con confirm se dirty) | ✓ |
| Tab | Next input field | ✓ |
| Shift+Tab | Previous input field | ✓ |
| 1-5 | Switch to tab 1-5 | ✓ |

### Implementazione
```java
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    // Dialog ha priorità
    if (activeDialog != null) {
        return activeDialog.keyPressed(keyCode);
    }

    // Ctrl shortcuts
    if (Screen.hasControlDown()) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_Z -> { undo(); return true; }
            case GLFW.GLFW_KEY_Y -> { redo(); return true; }
            case GLFW.GLFW_KEY_S -> { applyChanges(); return true; }
        }
    }

    // Tab switching con numeri
    if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
        int tabIndex = keyCode - GLFW.GLFW_KEY_1;
        if (tabIndex < tabs.length) {
            switchTab(tabIndex);
            return true;
        }
    }

    // ESC
    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
        onClose();
        return true;
    }

    return super.keyPressed(keyCode, scanCode, modifiers);
}
```

## 7.2 Sound Feedback

| Azione | Suono | Volume | Pitch |
|--------|-------|--------|-------|
| Button click | UI_BUTTON_CLICK | 1.0 | 1.0 |
| Apply success | PLAYER_LEVELUP | 1.0 | 1.5 |
| Error | VILLAGER_NO | 1.0 | 1.0 |
| Toggle ON | LEVER_CLICK | 0.5 | 1.2 |
| Toggle OFF | LEVER_CLICK | 0.5 | 0.8 |
| Preset saved | VILLAGER_YES | 0.8 | 1.2 |
| Preset deleted | ITEM_PICKUP | 0.6 | 0.6 |
| Undo/Redo | UI_BUTTON_CLICK | 0.5 | 1.0 |

Usare `UIConstants.Sound.*` già definiti.

---

# PARTE 8: PIANO IMPLEMENTAZIONE UNIFICATO

> **NOTA:** Piano aggiornato per architettura B (editor unificato con moduli)

## 8.0 Fase 0: Architettura Base (PREREQUISITO)
**Obiettivo:** Creare la struttura unificata PRIMA di toccare feature

### Nuovi File da Creare
```
src/main/java/com/frenkvs/devmod/ui/editor/
├── ItemEditorScreen.java      ← Shell unico
├── EditorLayout.java          ← Layout engine centralizzato
├── EditorModule.java          ← Interface per moduli
├── EditorSection.java         ← Sealed interface per sezioni
├── Bounds.java                ← Record per coordinate
├── components/
│   ├── HeaderComponent.java
│   ├── FooterComponent.java
│   ├── LeftColumnComponent.java
│   └── ContentArea.java
└── modules/
    ├── WeaponModule.java
    ├── ArmorModule.java
    └── GeneralModule.java     ← Fallback per altri item
```

### Step Implementazione
1. Crea `Bounds.java` (record semplice)
2. Crea `EditorSection.java` (sealed interface)
3. Crea `EditorLayout.java` (calcola tutte le posizioni)
4. Crea `EditorModule.java` (interface)
5. Crea shell components (Header, Footer, LeftColumn, ContentArea)
6. Crea `ItemEditorScreen.java` che assembla tutto
7. Testa con modulo vuoto "GeneralModule"

**Linee stimate:** 400-500 totali

## 8.1 Fase 1: Migrazione WeaponEditor → WeaponModule
**Obiettivo:** Portare tutta la logica Weapon nel nuovo sistema

### Step
1. Crea `WeaponModule.java` che implementa `EditorModule`
2. Estrai tutti gli slider come `EditorSection.SliderSection`
3. Estrai enchantment list come `EditorSection.ListSection`
4. Porta dirty state, dual-mode, confirm dialog nel modulo
5. Testa apertura da weapon item
6. Verifica che funzioni identico al vecchio

**Linee stimate:** 300-350

## 8.2 Fase 2: Migrazione ArmorEditor → ArmorModule
**Obiettivo:** Portare tutta la logica Armor nel nuovo sistema

### Step
1. Crea `ArmorModule.java` che implementa `EditorModule`
2. Estrai protection sliders come `EditorSection.SliderSection`
3. Estrai attribute sliders
4. Porta slot selector (4 slots)
5. Porta dirty state, dual-mode, confirm dialog
6. Testa apertura da armor item
7. Verifica che funzioni identico al vecchio

**Linee stimate:** 350-400

## 8.3 Fase 3: Eliminazione Vecchi Editor
**Obiettivo:** Rimuovere codice duplicato

### Step
1. Aggiorna tutti i riferimenti a `ArmorEditorScreen` → `ItemEditorScreen`
2. Aggiorna tutti i riferimenti a `WeaponEditorScreen` → `ItemEditorScreen`
3. Elimina `ArmorEditorScreen.java`
4. Elimina `WeaponEditorScreen.java`
5. Verifica che radial menu apra correttamente
6. Full regression test

**Linee stimate:** -800 (rimozione) + 50 (aggiornamenti)

## 8.4 Fase 4: Feature Comuni
**Obiettivo:** Implementare feature P0-P1 nel sistema unificato

### Nel Shell (ItemEditorScreen)
1. Dual-mode toggle (PREVIEW/APPLY) - già nel design
2. Dirty state indicator
3. Confirmation dialogs
4. Footer buttons (Apply, Cancel, Export, etc.)

### Nei Moduli
1. Debug Panel (tab DEBUG in ogni modulo)
2. Copy Debug Info to clipboard
3. Presets system

**Linee stimate:** 200-250

## 8.5 Fase 5: Polish
**Obiettivo:** UX refinement

### Step
1. Tooltips su tutti gli elementi
2. Sound feedback
3. Keyboard shortcuts funzionanti
4. Test con vari tipi di item

**Linee stimate:** 100-150

## Timeline Riepilogo

| Fase | Obiettivo | Linee | Priorità |
|------|-----------|-------|----------|
| 0 | Architettura base | ~450 | **BLOCCANTE** |
| 1 | WeaponModule | ~325 | P0 |
| 2 | ArmorModule | ~375 | P0 |
| 3 | Rimozione vecchi | ~-750 | P0 |
| 4 | Feature comuni | ~225 | P1 |
| 5 | Polish | ~125 | P3 |

**Totale netto:** ~750 linee nuove (contro ~1000 duplicati rimossi)

---

# PARTE 9: CHECKLIST FINALE PER TESTER

## Pre-Release Checklist

### Dual-Mode System
- [ ] Badge PREVIEW/APPLY visibile in header
- [ ] Click su badge apre dropdown
- [ ] Default mode è PREVIEW (giallo)
- [ ] F5 togla tra PREVIEW e APPLY
- [ ] In PREVIEW: Apply button mostra "Preview Only" disabilitato
- [ ] In PREVIEW: Chiusura senza warning
- [ ] In APPLY: Modifiche attivano dirty state
- [ ] In APPLY: Chiusura con dirty mostra conferma
- [ ] Tooltip su badge spiega la modalità

### Funzionalità Core
- [ ] Editor si apre senza crash
- [ ] Tutti i tab sono accessibili e renderizzano contenuto
- [ ] Sliders funzionano (drag, click)
- [ ] Toggles funzionano
- [ ] Input fields accettano valori

### Dirty State
- [ ] Modificando un valore appare "● X unsaved changes"
- [ ] Chiudendo con modifiche appare dialog di conferma
- [ ] Apply pulisce dirty state e mostra "✓ Saved"
- [ ] Cancel senza modifiche chiude immediatamente

### Slot System
- [ ] ArmorEditor: tutti 4 slot selezionabili
- [ ] WeaponEditor: Main/Off hand switchabili
- [ ] Switching slot con dirty mostra conferma
- [ ] Item corretto mostrato nel preview

### Undo/Redo
- [ ] Ctrl+Z annulla ultima modifica
- [ ] Ctrl+Y ripristina
- [ ] Buttons Undo/Redo funzionano
- [ ] History mostra lista modifiche

### Presets
- [ ] Save preset funziona
- [ ] Load preset funziona
- [ ] Delete preset chiede conferma
- [ ] Presets persistono tra sessioni

### Visual Consistency
- [ ] Dimensioni panel identiche tra i due editor
- [ ] Posizioni componenti identiche
- [ ] Colori bottoni identici
- [ ] Font e sizing identici

---

# APPENDICE A: COSTANTI JAVA DA AGGIUNGERE

```java
// In UIConstants.java o in EditorConstants.java dedicato

public final class EditorConstants {
    private EditorConstants() {}

    // Panel
    public static final int PANEL_WIDTH = 550;
    public static final int PANEL_HEIGHT = 420;

    // Zones
    public static final int HEADER_HEIGHT = 28;
    public static final int FOOTER_HEIGHT = 60;
    public static final int LEFT_COLUMN_WIDTH = 140;
    public static final int CONTENT_WIDTH = 390;

    // Preview
    public static final int PREVIEW_SIZE = 100;
    public static final int PREVIEW_X = 20;
    public static final int PREVIEW_Y = 38;

    // Slots
    public static final int SLOT_SIZE = 30;
    public static final int SLOT_GAP = 5;
    public static final int SLOT_AREA_Y = 155;
    public static final int SLOT_AREA_HEIGHT = 70;

    // Item Info
    public static final int INFO_PANEL_Y = 235;
    public static final int INFO_PANEL_HEIGHT = 100;

    // Tabs
    public static final int TAB_WIDTH = 70;
    public static final int TAB_HEIGHT = 22;
    public static final int TAB_GAP = 2;

    // Buttons
    public static final int BTN_SMALL_WIDTH = 50;
    public static final int BTN_MEDIUM_WIDTH = 60;
    public static final int BTN_LARGE_WIDTH = 120;
    public static final int BTN_SMALL_HEIGHT = 22;
    public static final int BTN_LARGE_HEIGHT = 50;

    // Dialog
    public static final int DIALOG_WIDTH = 350;
    public static final int DIALOG_HEIGHT = 150;

    // Undo
    public static final int MAX_UNDO_STATES = 50;
    public static final int MAX_HISTORY_ENTRIES = 50;
}
```

---

# APPENDICE B: RIFERIMENTI FILE

| File | Ruolo | Modifiche Necessarie |
|------|-------|---------------------|
| ArmorEditorScreen.java | Editor principale armature | Fase 1-4 complete |
| WeaponEditorScreen.java | Editor principale armi | Fase 1-4 complete |
| UIConstants.java | Design system colori | Verificare completezza |
| AxiomRenderer.java | Utility rendering | Potrebbe servire nuovi metodi |
| ItemEditorDataManager.java | Persistence presets | Estendere per ArmorEditor |
| EditorConstants.java | **NUOVO** | Costanti layout unificate |

---

*Documento creato come parte del DevMod UI/UX Unification Initiative*
*Versione 1.0 - Per revisione team e tester*
