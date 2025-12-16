# 2.9 Dual-Mode System (PREVIEW / APPLY)

> **Architettura confermata:** Gli editor supportano due modalità operative.

## Concetto

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

## Modalità

| Modalità | Icona | Descrizione | Comportamento |
|----------|-------|-------------|---------------|
| **PREVIEW** | 👁 | Solo visualizzazione | Client-only, nessun packet, reset on close |
| **APPLY** | ⚡ | Applica modifiche | Invia al server, persiste, richiede conferma |

## Layout Mode Toggle

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

## Specifiche Visive

| Modalità | Background Badge | Border | Text Color |
|----------|-----------------|--------|------------|
| PREVIEW | `0x40FFFF00` (Giallo trasparente) | `UIConstants.Accent.YELLOW` | `0xFFFFFF00` |
| APPLY | `0x4000FF00` (Verde trasparente) | `UIConstants.Accent.GREEN` | `0xFF00FF00` |

## Comportamento PREVIEW Mode

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

## Comportamento APPLY Mode

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

## Toggle Mode

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

## Indicatori Visivi in UI

### Header Badge

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

### Apply Button Stato

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

## Tooltip Informativi

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

## Interazione con Mode Badge (GLOBAL/SPECIFIC)

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

## Persistenza Modalità

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

### Configurazione (implementata)
- Config entry: `editor.defaultMode` (Enum) in `Config` (client/common)  
  - Valori: `PREVIEW` (default, safe) / `APPLY` (persistente)  
- Runtime: l'editor legge la preferenza all'inizializzazione (`loadUserModePreference`) e la salva ogni volta che il badge PREVIEW/APPLY viene cambiato (`saveUserModePreference`).  
- Effetto: l'ultima scelta dell'utente persiste tra sessioni, rispettando il default PREVIEW alla prima esecuzione.

### UX: Impostazione rapida da UI
- Nel dropdown del badge PREVIEW/APPLY è presente una voce “Set current as default” che salva la modalità corrente come preferenza utente (usa la config `editor.defaultMode`).  
- Click su questa voce persiste immediatamente la scelta; resta “one click away”, non richiede schermate extra.  
- Il dropdown resta invariato per scope (GLOBAL/SPECIFIC) e non aggiunge rumore se il callback non è disponibile.

## Keyboard Shortcuts

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

## Integrazione con Debug Panel

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
