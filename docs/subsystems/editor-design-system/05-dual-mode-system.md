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
| PREVIEW | `0x40FFEB3B` (Giallo Material) | `UIConstants.Mode.PREVIEW_BORDER` (`0xFFFFEB3B`) | `0xFFFFEB3B` |
| APPLY | `0x404CAF50` (Verde Material) | `UIConstants.Mode.APPLY_BORDER` (`0xFF4CAF50`) | `0xFF4CAF50` |

> **Nota**: I colori usano Material Design palette (FFEB3B = Yellow 500, 4CAF50 = Green 500).

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
// ItemEditorScreen.java
private void switchToPreviewMode(boolean discardChanges) {
    isPreviewMode = true;
    if (activeModule != null) {
        if (discardChanges) {
            activeModule.resetToOriginal();
            activeModule.clearDirty();
        }
        activeModule.applyPreview();
        activeModule.logEvent(discardChanges
            ? "Switched to PREVIEW (discarded changes)"
            : "Switched to PREVIEW");
    }
    showStatus("Preview Mode", UIConstants.Accent.CYAN());
}

private void switchToApplyMode() {
    isPreviewMode = false;
    if (activeModule != null) {
        activeModule.clearPreview();
        if (!activeModule.hasUnsavedChanges() && activeModule.hasPendingDiff()) {
            activeModule.markDirty("Pending changes from preview");
        }
        activeModule.logEvent("Switched to APPLY (dirty on)");
    }
    showStatus("Apply Mode", UIConstants.Accent.GREEN());
}

// Called when ModeBadge mode changes
private void handleModeChange(boolean preview) {
    // If switching to PREVIEW with dirty state, show confirm dialog
    if (preview && !isPreviewMode && activeModule != null && activeModule.hasUnsavedChanges()) {
        activeDialog = ConfirmDialog.discardChanges(
            () -> {
                switchToPreviewMode(true);
                header.getModeBadge().setMode(ModeBadge.Mode.PREVIEW);
                saveUserModePreference();
            },
            () -> {
                header.getModeBadge().setMode(ModeBadge.Mode.APPLY);
            }
        );
        activeDialog.show();
        return;
    }

    if (preview) {
        switchToPreviewMode(false);
    } else {
        switchToApplyMode();
    }
    saveUserModePreference();
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
| APPLY + Dirty | Enabled | "✓ Apply (N)" | GREEN |

> **Nota**: Il testo include icona checkmark ✓ per enfatizzare l'azione di conferma.

```java
// FooterComponent.java - renderApplyButton()
private void renderApplyButton(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
    boolean enabled = canApply && isDirty;

    // Background - green tint for primary action
    int bgColor = !enabled ? UIConstants.Button.DISABLED() :
                 (applyHovered ? UIConstants.Button.PRIMARY_HOVER : UIConstants.Button.PRIMARY);
    graphics.fill(x, y, x + width, y + height, bgColor);

    // Border
    int borderColor = enabled ? UIConstants.Accent.GREEN() : UIConstants.Border.DEFAULT();
    if (applyHovered && enabled) {
        borderColor = UIConstants.lighten(borderColor, 0.3f);
    }
    AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);

    // Text with checkmark icon
    String label;
    if (!canApply) {
        label = "Preview Only";
    } else if (!isDirty) {
        label = "No Changes";
    } else if (pendingCount > 0) {
        label = "✓ Apply (" + pendingCount + ")";
    } else {
        label = "✓ Apply";
    }

    int textColor = enabled ? UIConstants.Text.PRIMARY() : UIConstants.Text.DISABLED();
    // ... draw centered text with Typography.buttonScale()

    // Dirty indicator dot (orange)
    if (isDirty) {
        int dotSize = ScaledCoord.scaleDim(6);
        graphics.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, UIConstants.Accent.ORANGE());
    }
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
// Config.java - Enum definition
public enum EditorDefaultMode {
    PREVIEW,
    APPLY
}

public static final ModConfigSpec.EnumValue<EditorDefaultMode> EDITOR_DEFAULT_MODE;

// In builder
EDITOR_DEFAULT_MODE = BUILDER
    .comment("Default editor mode (PREVIEW = safe, client-only; APPLY = persistent)")
    .defineEnum("defaultMode", EditorDefaultMode.PREVIEW);

// ItemEditorScreen.java - Save/Load preference
private void saveUserModePreference() {
    try {
        Config.EDITOR_DEFAULT_MODE.set(isPreviewMode
            ? Config.EditorDefaultMode.PREVIEW
            : Config.EditorDefaultMode.APPLY);
        showStatus("Default mode saved", UIConstants.Accent.BLUE());
    } catch (Exception ignored) {
        // Best-effort: config may be read-only in some contexts
    }
}

private void loadUserModePreference() {
    try {
        Config.EditorDefaultMode pref = Config.EDITOR_DEFAULT_MODE.get();
        isPreviewMode = pref != Config.EditorDefaultMode.APPLY;
    } catch (Exception ignored) {
        isPreviewMode = true; // Default to safe PREVIEW
    }
}
```

### Configurazione (implementata)
- Config entry: `EDITOR_DEFAULT_MODE` (Enum) in `Config.java`
  - Valori: `EditorDefaultMode.PREVIEW` (default, safe) / `EditorDefaultMode.APPLY` (persistente)
- Runtime: l'editor legge la preferenza all'inizializzazione (`loadUserModePreference`) e la salva ogni volta che il badge PREVIEW/APPLY viene cambiato (`saveUserModePreference`).
- Effetto: l'ultima scelta dell'utente persiste tra sessioni, rispettando il default PREVIEW alla prima esecuzione.

### UX: Impostazione rapida da UI
- Nel dropdown del badge PREVIEW/APPLY è presente una voce "Set current as default" che salva la modalità corrente come preferenza utente (usa la config `EDITOR_DEFAULT_MODE`).
- Click su questa voce persiste immediatamente la scelta; resta "one click away", non richiede schermate extra.
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

---

## Changelog

| Data | Modifica |
|------|----------|
| 2025-12-17 | Allineato colori badge a Material Design (FFEB3B/4CAF50) |
| 2025-12-17 | Aggiornato Apply button con checkmark ✓ e dirty indicator |
| 2025-12-17 | Aggiornato Toggle Mode con implementazione reale (switchToPreviewMode/switchToApplyMode) |
| 2025-12-17 | Aggiornato Config persistence con `EditorDefaultMode` enum |
