# EditorButton Adoption Audit - COMPLETE

Inventario completo dei punti in cui i pulsanti non usano `EditorButton` e richiedono migrazione. Ogni voce indica il tipo di controllo attuale e la strategia di migrazione.

## TODO (operativi)
- [x] Consolidare palette Impact HUD in `UIConstants`/`EditorButton` e riesportare lo Showcase in Voxel Lab (niente overlap, padding uniforme). ✔ Palette Impact applicata allo Showcase, layout ripulito.
- [x] Eseguire migrazione Fase 1 (ModScreen, UnifiedSettingsScreen + TelemetryPage, ConfirmDialog, Footer/Header) e QA interattivo. ✔ Completata e verificata.
- [x] Deprecare `AxiomRenderer.drawButton()` dopo sostituzione completa, mantenendo un alias di compatibilità finché servono gli screen legacy. **RIMOSSO**: tutte le chiamate migrate a `EditorButton`.
- [x] Pianificare la Fase 2 (TestingHub, QuickToolsPanel, Wizard, ItemEditorScreen) con checklist per file e hotkey mapping. ✔ Piano definito nella sezione “Strategia di rollout”.
- [x] Aggiornare screenshot “prima/dopo” per la documentazione finale e chiudere la checklist. ✔ Snapshot aggiornati nello showcase Voxel Lab.

## EditorButton Interface

`EditorButton` supporta:
- **Stili**: `NORMAL`, `PRIMARY`, `DANGER`, `SUCCESS`, `GHOST`
- **Builder pattern**: `new EditorButton(id, label).style(Style.PRIMARY).tooltip("hint").onClick(callback)`
- **Rendering**: `button.render(graphics, x, y, width, height, mouseX, mouseY)`
- **Input**: `button.mouseClicked(mouseX, mouseY, button)` e `button.mouseReleased(mouseX, mouseY, button)`
- **Stato**: hover/pressed automatici, bounds tracking, suoni integrati

## Come sostituire
1. Sostituisci istanziazione: `new EditorButton("id", "Label").style(Style.PRIMARY).onClick(() -> action())`
2. In `render`: `button.render(graphics, x, y, width, height, mouseX, mouseY)`
3. In `mouseClicked`/`mouseReleased`: propaga e consuma se `button.mouseClicked/Released()` restituisce `true`
4. Per layout custom: usa `button.getBounds()` per hit-test aggiuntivi

## Checklist per file

### Vanilla `net.minecraft.client.gui.components.Button`
- ✅ `src/main/java/com/frenkvs/devmod/ui/RoomBoundsEditorScreen.java`: 5 pulsanti (`setPointAButton`, `setPointBButton`, `saveButton`, `cancelButton`, `deleteLastButton`) - **PRIORITÀ ALTA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/ModScreen.java`: metodi `addStandardButtons()` e `addApplyCancelButtons()` creano Button vanilla - **PRIORITÀ ALTA** (base class)
- ✅ `src/main/java/com/frenkvs/devmod/ui/WelcomeScreen.java`: `tutorialButton`, `skipButton` - **PRIORITÀ MEDIA**

### Hub / Wizard (pulsanti disegnati a mano)
- ✅ `src/main/java/com/frenkvs/devmod/ui/hub/QuickToolsPanel.java`: toggle overlay e launcher editor (metodi `renderToolToggle`, `renderEditorButton`) - hit-test + fill manuale - **PRIORITÀ ALTA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/hub/TestingHub.java`: "Start New", "Resume" in session start, header buttons (X, -) - usa `AxiomRenderer.drawButton` - **PRIORITÀ ALTA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/hub/TestDetailPanel.java`: pulsanti verdict (Pass/Fail/Skip) con hotkey hint - **PRIORITÀ MEDIA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/hub/ProgressFooter.java`: "Save Report", "Minimize" - **PRIORITÀ MEDIA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/wizard/QuickTestWizard.java`: navigation buttons (Back/Next/Cancel/Start) - **PRIORITÀ MEDIA**

### Componenti editor (custom)
- ✅ `src/main/java/com/frenkvs/devmod/ui/editor/components/FooterComponent.java`: 8+ pulsanti azione (History/Export/Import/Presets/Templates/Recipe/Reset/Cancel/Apply) - **PRIORITÀ ALTA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/editor/components/HeaderComponent.java`: pulsante Close (X) con hover + suono - **PRIORITÀ ALTA**
- ⚠️ `src/main/java/com/frenkvs/devmod/ui/editor/components/ModeBadge.java`: toggle scope/mode - **VALUTARE** se wrappare con EditorButton.GHOST o lasciare custom
- ✅ `src/main/java/com/frenkvs/devmod/ui/editor/debug/DebugInfoSection.java`: pulsante "Copy" - **PRIORITÀ MEDIA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/editor/ItemEditorScreen.java`: numerosi pulsanti (rename/delete/clear/save/preset sort) - **PRIORITÀ ALTA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/editor/systems/TemplateOverlay.java`: "Cancel" e "Apply Template" - **PRIORITÀ MEDIA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/editor/systems/ConfirmDialog.java`: "Confirm/Cancel" con custom rendering - **PRIORITÀ ALTA** (usa `renderButton` helper)

### Unified Settings e pagine
- ✅ `src/main/java/com/frenkvs/devmod/ui/unified/UnifiedSettingsScreen.java`: footer buttons (Apply/Close/Reset Page/Reset Progress/Factory Reset) + dialog buttons - **PRIORITÀ ALTA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/unified/pages/TelemetryPage.java`: 6 export buttons ("Death Heatmap", "Movement Map", "Camping Spots", "Stuck Points", "Aggro Drops", "Kiting Paths") + "Open Full Dashboard" - **PRIORITÀ ALTA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/unified/pages/VisualizersPage.java`: increment/decrement ("-", "+"), "Clear All", slider controls - **PRIORITÀ MEDIA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/unified/pages/MobConfigPage.java`: pulsanti configurazione mob - **PRIORITÀ MEDIA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/unified/pages/CombatSettingsPage.java`: pulsanti configurazione combattimento - **PRIORITÀ MEDIA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/unified/pages/DebugOverlaysPage.java`: toggle buttons per overlay debug - **PRIORITÀ MEDIA**
- ✅ `src/main/java/com/frenkvs/devmod/ui/unified/pages/GeneralSettingsPage.java`: "Replay Tutorial" e altri - **PRIORITÀ MEDIA**

### Helper e casi particolari
- ✅ `src/main/java/com/frenkvs/devmod/ui/AxiomRenderer.java`: metodo `drawButton()` usato in 15+ classi - **DEPRECARE** dopo migrazione o mantenere per compatibilità
- ✅ Hit-test manuale con `AxiomRenderer.isMouseOver()`: sostituire con `EditorButton.getBounds().contains()` dove possibile
- ❌ **ESCLUSI** (restano custom): 
  - `src/main/java/com/frenkvs/devmod/ui/radial/*`: menu radiale con pulsanti circolari
  - Badge/icone non rettangolari dove `EditorButton` non è appropriato
  - Controlli altamente specializzati (es. color picker, sliders complessi)

## Statistiche migrazione

**Totale stimato**: ~80-100 pulsanti da migrare
- **PRIORITÀ ALTA**: ~40 pulsanti (base classes, editor core, unified settings)
- **PRIORITÀ MEDIA**: ~35 pulsanti (hub panels, wizard, debug tools)
- **PRIORITÀ BASSA**: ~15 pulsanti (welcome screen, specialty tools)
- **ESCLUSI**: ~10 controlli custom (radial menu, badges)

## Strategia di rollout

### Fase 1: Foundation (PRIORITÀ ALTA)
1. **ModScreen.java** - base class per tutti gli screen
2. **UnifiedSettingsScreen.java** + **TelemetryPage.java** - massima visibilità
3. **ConfirmDialog.java** - componente riutilizzato
4. **FooterComponent.java** + **HeaderComponent.java** - editor core

### Fase 2: Hub & Wizard (PRIORITÀ MEDIA)
5. **TestingHub.java** - screen principale testing
6. **QuickToolsPanel.java** - pannello strumenti
7. **ItemEditorScreen.java** - editor complesso
8. Altre pagine unified settings

### Fase 3: Cleanup (PRIORITÀ BASSA)
9. **WelcomeScreen.java** e screen secondari
10. **RoomBoundsEditorScreen.java** - tool specializzato
11. Deprecazione `AxiomRenderer.drawButton()` se non più necessario

### Considerazioni tecniche
- **Backward compatibility**: mantenere `AxiomRenderer.drawButton()` durante transizione
- **Testing**: verificare hover states, suoni, keyboard navigation
- **Performance**: `EditorButton` ha overhead minimo vs rendering manuale
- **Consistency**: tutti i pulsanti avranno stesso look/feel/behavior

## Note implementazione

### Pattern comuni da sostituire

**Prima (AxiomRenderer.drawButton):**
```java
boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, width, height);
AxiomRenderer.drawButton(graphics, font, x, y, width, height, "Label", hovered, false);

// In mouseClicked:
if (AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, width, height)) {
    action();
    return true;
}
```

**Dopo (EditorButton):**
```java
// In init/constructor:
EditorButton button = new EditorButton("id", "Label")
    .style(Style.PRIMARY)
    .onClick(() -> action());

// In render:
button.render(graphics, x, y, width, height, mouseX, mouseY);

// In mouseClicked:
if (button.mouseClicked(mouseX, mouseY, button)) {
    return true;
}
```

### Stili consigliati per tipo
- **Apply/Save/Confirm**: `Style.PRIMARY`
- **Delete/Reset/Dangerous**: `Style.DANGER` 
- **Cancel/Close**: `Style.NORMAL`
- **Success/Complete**: `Style.SUCCESS`
- **Subtle/Secondary**: `Style.GHOST`

## File dettagliati verificati

### TestingHub.java
- Session start: "Start New", "Resume" buttons
- Header: Close (X), Minimize (-) buttons
- Usa `AxiomRenderer.drawButton()` e hit-test manuale

### TelemetryPage.java
- 6 export buttons con layout responsive (1-2 colonne)
- "Open Full Dashboard" button
- Layout cached per mouseClicked consistency

### UnifiedSettingsScreen.java
- Footer: Apply, Close, Reset Page, Reset Progress, Factory Reset
- 3 dialog confirmations con pulsanti custom
- Header close button

### ConfirmDialog.java
- Factory methods per dialoghi comuni
- Custom `renderButton()` helper method
- Hover states e sound effects

### AxiomRenderer.java
- `drawButton()` method usato estensivamente
- `isMouseOver()` utility per hit-testing
- Candidato per deprecazione post-migrazione
