# DevMod Editor - TODO & Tracking

## Stato: Completato - Fase 1 Refactoring

### Fase 1: Refactoring Bottoni Hardcoded - COMPLETATO

- [x] **DebugPanel.java** - Sostituire 3 bottoni con EditorButton
  - [x] Aggiungere campi EditorButton (copyButton, exportButton, copyItemButton)
  - [x] Rimuovere logica rendering manuale
  - [x] Aggiornare handleClick() per usare button.mouseClicked()

- [x] **ConfirmDialog.java** - Sostituire 2 bottoni con EditorButton
  - [x] Eliminare metodo renderButton()
  - [x] Creare confirmButton e cancelButton come campi
  - [x] Aggiornare render() e mouseClicked()

- [x] **TemplateOverlay.java** - Sostituire 2 bottoni con EditorButton
  - [x] Eliminare metodo renderButton()
  - [x] Creare cancelButton e applyButton come campi
  - [x] Aggiornare render e input handling

- [x] **CraftingInfoPanel.java** - Sostituire 2 bottoni navigazione
  - [x] Creare prevButton e nextButton
  - [x] Sostituire drawRecipeSelector() hardcoded

- [x] **MultiEditPanel.java** - Sostituire 5 bottoni
  - [x] clearButton (style DANGER)
  - [x] applyButton (style SUCCESS)
  - [x] exportButton (style GHOST)
  - [x] copyFailsButton (style GHOST)
  - [x] detailsButton (toggleable GHOST)

### Fase 2: Nuovi Componenti Base - COMPLETATO

- [x] **BaseOverlay.java** - Classe astratta per overlay modali
  - [x] Implementare show/hide/isVisible
  - [x] Template method per render (backdrop + panel + content)
  - [x] Default keyPressed per ESC
  - [x] Click outside to close

- [x] **ButtonRow.java** - Layout orizzontale bottoni
  - [x] API fluent per aggiungere bottoni
  - [x] Metodi render() e mouseClicked()
  - [x] Supporto gap configurabile
  - [x] Supporto allineamento (LEFT, CENTER, RIGHT)

### Fase 3: Documentazione - COMPLETATO

- [x] Aggiornare 01-layout-specifications.md con sezione Component System
- [x] Documentare pattern EditorButton (styles, sizes, callbacks)
- [x] Documentare BaseOverlay pattern
- [x] Documentare ButtonRow pattern
- [x] Aggiungere diagramma gerarchia componenti

---

## Prossimi Passi (P2)

### Fase 4A: Migrare Overlay Semplici a BaseOverlay - COMPLETATO

- [x] ConfirmDialog extends BaseOverlay
  - [x] Rimosso codice duplicato (visible, show/hide, backdrop, panel centering)
  - [x] Implementato getPanelWidth(), getPanelHeight(), renderContent()
  - [x] Override onEscapePressed() per chiamare onCancel
  - [x] Override handleKeyPressed() per Enter
  - [x] Override shouldCloseOnClickOutside() = false
- [x] HelpOverlay extends BaseOverlay
  - [x] Mantenuto toggle() come metodo aggiuntivo
  - [x] Override renderPanel() per usare Border.ACCENT
  - [x] Override handleKeyPressed() per F1
  - [x] Override handleMouseClicked() per chiudere anche su click interno

### Fase 4B: Estendere BaseOverlay (per overlay complessi) - COMPLETATO

- [x] Aggiungere mouseScrolled() a BaseOverlay
- [x] Aggiungere charTyped() a BaseOverlay
- [x] Aggiungere toggle() a BaseOverlay

### Fase 4C: Migrare Overlay Complessi - COMPLETATO

- [x] TemplateOverlay extends BaseOverlay
  - [x] Implementato handleMouseScrolled() per lista virtualizzata
  - [x] Implementato handleCharTyped() per search box
  - [x] Mantenuto keyPressed(int, int) per Ctrl+F con modifiers
  - [x] Aggiornate chiamate in ItemEditorScreen

### Fase 4D: CraftingInfoPanel con Altezza Dinamica - COMPLETATO

- [x] Esteso BaseOverlay con supporto altezza dinamica
  - [x] Aggiunto `getPanelHeight(int screenHeight)` per calcolo altezza basato su screen
  - [x] Aggiunto `usesDynamicHeight()` per abilitare altezza dinamica
  - [x] Aggiornato `render()`, `mouseClicked()`, `mouseScrolled()` per usare altezza dinamica
- [x] CraftingInfoPanel extends BaseOverlay
  - [x] Override `usesDynamicHeight()` = true
  - [x] Override `getPanelHeight(int screenHeight)` con calcolo dinamico
  - [x] Implementato `handleMouseClicked()` per bottoni navigazione
  - [x] Implementato `handleKeyPressed()` per frecce sinistra/destra
  - [x] Implementato `handleMouseScrolled()` per scroll ingredienti
  - [x] Aggiornate chiamate in ItemEditorScreen

### Fase 5: Miglioramenti Architetturali

- [ ] Creare EditorComponent interface base
- [ ] Creare InputHandler interface
- [ ] Estrarre InteractiveComponent abstract class

---

## Miglioramenti Futuri (P3)

- [ ] VirtualizedList component per liste scrollabili lunghe
- [ ] ScrollState utility class
- [ ] Animazioni transizione per overlay
- [ ] Tema scuro/chiaro configurabile
- [ ] Accessibilità keyboard navigation

---

## Bug Noti

- [ ] **ArmorModule.java** - Type mismatch (se presente)
  - Confronto `Holder<Attribute>` vs `Attribute`
  - Causa: chiamata equals() con tipo sbagliato

---

## Note di Sviluppo

### Pattern Utilizzati

1. **Builder Pattern** - EditorButton, EditorSlider, ButtonRow
2. **Template Method** - BaseOverlay.render()
3. **Observer Pattern** - onClick callbacks
4. **Fluent Interface** - Tutti i componenti

### Convenzioni Codice

- Tutti i componenti devono usare `UIConstants` per colori
- Tutti gli spacing devono usare `EditorSpacing` (multipli di 4px)
- Tutte le dimensioni devono usare `EditorDimensions`
- Scaling via `ScaledCoord.scaleDim()`

### File Modificati in Fase 1

| File | Bottoni Refactored | LOC Rimossi |
|------|-------------------|-------------|
| DebugPanel.java | 3 | ~25 |
| ConfirmDialog.java | 2 | ~25 |
| TemplateOverlay.java | 2 | ~20 |
| CraftingInfoPanel.java | 2 | ~15 |
| MultiEditPanel.java | 5 | ~30 |
| **TOTALE** | **14 bottoni** | **~115 LOC** |

### File Creati in Fase 2

| File | Descrizione |
|------|-------------|
| BaseOverlay.java | Classe astratta per overlay modali |
| ButtonRow.java | Layout orizzontale per bottoni |

---

## Changelog

### 2025-12-17

- **Completata Fase 4D: CraftingInfoPanel con altezza dinamica**
  - BaseOverlay: aggiunto supporto altezza dinamica con `getPanelHeight(int)` e `usesDynamicHeight()`
  - CraftingInfoPanel: estende BaseOverlay, rimosso ~70 LOC duplicato
  - Calcolo altezza dinamico basato su numero ingredienti e dimensioni schermo
  - Aggiornate chiamate in ItemEditorScreen (render, mouseClicked, mouseScrolled)
- **Completata Fase 4B: estensione BaseOverlay**
  - Aggiunto toggle() per toggle visibilità
  - Aggiunto mouseScrolled() con handleMouseScrolled() hook
  - Aggiunto charTyped() con handleCharTyped() hook
- **Completata Fase 4C: migrazione TemplateOverlay a BaseOverlay**
  - TemplateOverlay: estende BaseOverlay, rimosso ~60 LOC duplicato
  - Implementato scroll virtualizzato tramite handleMouseScrolled()
  - Implementato input search box tramite handleCharTyped()
  - Aggiornate tutte le chiamate in ItemEditorScreen
- **Completata Fase 4A: migrazione overlay semplici a BaseOverlay**
  - ConfirmDialog: estende BaseOverlay, rimosso ~50 LOC duplicato
  - HelpOverlay: estende BaseOverlay, rimosso ~40 LOC duplicato
  - Aggiornate chiamate in ItemEditorScreen per nuova signature mouseClicked()

### 2025-12-16

- Completata Fase 1: refactoring di 14 bottoni hardcoded
- Completata Fase 2: creati BaseOverlay.java e ButtonRow.java
- Completata Fase 3: aggiornata documentazione con Component System
- Creato questo file TODO.md
- **Sincronizzata 02-shared-components.md** con implementazione reale:
  - SlotSelector: rimossa ISlotSelector interface, documentato SlotType enum, keyboard navigation
  - ModeBadge: documentato BadgeType (SCOPE/MODE), dropdown, colori corretti
  - Dirty State: aggiornato API con pendingChanges(int), formattazione tempo
  - ItemInfoPanel: documentato StatLine record, colorazione automatica valori
  - ConfirmDialog: corrette dimensioni 320x140, documentati factory methods
  - Keyboard shortcuts: aggiunti Ctrl+S, Ctrl+Z, Ctrl+Y, Ctrl+Shift+Z
  - Nuova sezione: Componenti Non Documentati (Footer, Header, Typography, EditorSounds)
