# Shared Components

## Slot Selector System

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

## Mode Badge

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

## Dirty State System

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

## Confirmation Dialog

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

## Item Info Panel

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

## Dual-Mode System (PREVIEW / APPLY)

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

### Keyboard Shortcuts

| Shortcut | Azione |
|----------|--------|
| **F5** | Toggle PREVIEW ↔ APPLY mode |
| **Ctrl+Enter** | Quick Apply (solo in APPLY mode con dirty) |