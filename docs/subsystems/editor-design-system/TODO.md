# DevMod Editor - TODO & Tracking

## Stato: Completato - Tutte le Fasi (1-6)

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

### Fase 5: Miglioramenti Architetturali - COMPLETATO

- [x] Creare EditorComponent interface base
  - [x] Interface con getId(), render(), isEnabled(), isHovered(), mouseClicked(), etc.
  - [x] Supporto bounds, focus, visibility
  - [x] Lifecycle methods (tick, onClose)
- [x] Creare InputHandler interface
  - [x] Interface separata per input handling
  - [x] Static utility methods per modifier keys
- [x] Creare ScrollState utility class
  - [x] Gestione offset, maxOffset, viewport
  - [x] Metodi scroll(), scrollToItem(), reset()
  - [x] Supporto virtualized rendering (getFirstVisibleRow, getRowOffset)
  - [x] Calcolo scrollbar metrics
- [x] Creare VirtualizedList component
  - [x] Generic list con tipo parametrico
  - [x] Fluent configuration API
  - [x] RowRenderContext per custom rendering
  - [x] Selection management (single select)
  - [x] Double-click support
  - [x] Keyboard navigation (arrows, home/end, page up/down)
  - [x] Scroll indicators e scrollbar

---

## Fase 6: Feature Avanzate P3 - COMPLETATO

- [x] **Animazioni transizione overlay**
  - [x] AnimationState.java: utility class per gestione animazioni
  - [x] Supporto fade, slide-up, slide-down, scale
  - [x] Easing functions (ease-out-cubic, ease-in-out, ease-out-back)
  - [x] Integrazione in BaseOverlay con `withAnimation()`

- [x] **Sistema Tema configurabile**
  - [x] Theme.java: interface per definizione colori
  - [x] DarkTheme.java: tema scuro (default, colori UIConstants)
  - [x] LightTheme.java: tema chiaro
  - [x] ThemeManager.java: singleton per gestione/switch tema
  - [x] Listener pattern per notifiche cambio tema

- [x] **FocusManager per keyboard navigation**
  - [x] FocusManager.java: gestione focus tra componenti
  - [x] Focusable interface per componenti focusabili
  - [x] Tab/Shift+Tab navigation
  - [x] Focus callbacks (onFocusGained, onFocusLost)

- [x] **GeneralModule completato**
  - [x] Implementato buildPayload() con ModifyItemPayload
  - [x] Supporto durability, unbreakable, repairCost

---

## Bug Noti

- [x] **ArmorModule.java** - Type mismatch (RISOLTO)
  - Confronto `Holder<Attribute>` vs `Attribute`
  - Causa: chiamata equals() con tipo sbagliato
  - Fix: usato Holder correttamente

---

## TODO: UI Scaling & Config

### Config TOML Persistente
- [x] Integrare con NeoForge config system ✓ (`EditorClientConfig.java`)
- [x] Creare `config/devmod-client.toml` con sezione `[editor]` ✓ (auto-generato da NeoForge)
- [x] Migrare da System.property/env a config file persistente ✓ (config-first, fallback in `EditorConfig.java`)
- [x] Aggiungere listener per reload config a runtime ✓ (`EditorConfig.ConfigChangeListener`, `DevMod::onConfigReload`)

### In-Game Settings UI
- [x] Creare `EditorSettingsPage.java` ✓ (integrato in UnifiedSettingsScreen)
- [x] UI con ButtonRow per selezione scale (Auto, 1.0x, 1.25x, 1.5x, 2.0x) ✓
- [x] Mostrare info scale corrente e dimensioni pannello ✓
- [x] Registrare pagina nel menu impostazioni (SettingsCategory.EDITOR) ✓
- [x] Mostrare preview live dell'effetto scale ✓ (EditorSettingsPage)

### Riferimenti
- Documentazione: `docs/subsystems/editor-design-system/07-ui-scaling.md`
- Implementazione: `EditorClientConfig.java`, `EditorConfig.java`, `EditorScaleCalculator.java`, `ScaledCoord.java`
- Settings UI: `EditorSettingsPage.java`, `UnifiedSettingsScreen.java`

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

### File Creati in Fase 5

| File | Descrizione |
|------|-------------|
| EditorComponent.java | Interface base per componenti UI |
| InputHandler.java | Interface per gestione input |
| ScrollState.java | Utility class per scroll management |
| VirtualizedList.java | Componente lista virtualizzata |

### File Creati in Fase 6

| File | Descrizione |
|------|-------------|
| AnimationState.java | Gestione animazioni con easing |
| Theme.java | Interface per definizione temi |
| DarkTheme.java | Implementazione tema scuro |
| LightTheme.java | Implementazione tema chiaro |
| ThemeManager.java | Singleton per gestione temi |
| FocusManager.java | Gestione focus keyboard navigation |

---

## Changelog

### 2025-12-17 (Sessione 4)

- **Completato: Config Reload Listener a Runtime**
  - EditorConfig.java: aggiunto ConfigChangeListener interface
  - EditorConfig.java: aggiunto listener management (add/remove/notify)
  - EditorConfig.java: aggiunto onConfigReload() con change detection
  - EditorConfig.java: aggiunto initCache() per inizializzazione valori cached
  - DevMod.java: registrato listener per ModConfigEvent.Reloading
  - DevMod.java: chiamata EditorConfig.initCache() all'avvio
  - UI può ora reagire a cambi config runtime (uiScale, soundsEnabled, defaultMode)

### 2025-12-17 (Sessione 3)

- **Integrazione Feature Fase 6 nell'Editor**
  - Animazioni: aggiunto `.withAnimation()` a craftingPanel, helpOverlay, templateOverlay
  - ConfirmDialog: animazioni abilitate di default nel costruttore
  - ThemeManager: integrato in UIConstants con metodi themed (PANEL(), BORDER(), TEXT(), etc.)
  - Theme interface: aggiunti metodi `darkerBackground()`, `textValue()`, `textFormula()`
  - FocusManager: EditorButton implementa `FocusManager.Focusable`
  - EditorButton: aggiunto supporto focus ring (cyan), keyPressed per Enter/Space
  - ConfirmDialog: aggiunto `registerFocusables(FocusManager)` per Tab navigation
  - ItemEditorScreen: aggiunto `showDialog()` helper per focus registration automatica
  - Aggiornati 5 dialogs chiave per usare `showDialog()` invece di `activeDialog.show()`

### 2025-12-17 (Sessione 2)

- **Completata Fase 6: Feature Avanzate P3**
  - AnimationState.java: sistema animazioni con fade, slide, scale e easing functions
  - BaseOverlay: integrato supporto animazioni con `withAnimation()`
  - Theme.java + DarkTheme.java + LightTheme.java: sistema temi configurabili
  - ThemeManager.java: singleton per switch temi con listener pattern
  - FocusManager.java: gestione keyboard focus con Tab navigation
  - GeneralModule: completato buildPayload() con ModifyItemPayload
- **Aggiornata documentazione TODO.md**

### 2025-12-17 (Sessione 1)

- **Completata Fase 5: Miglioramenti Architetturali**
  - EditorComponent.java: interface base per tutti i componenti UI
  - InputHandler.java: interface separata per input handling con utility methods
  - ScrollState.java: utility class riutilizzabile per scroll management
  - VirtualizedList.java: componente generico per liste lunghe con virtualization
- **Aggiornata documentazione 03-architecture.md**
  - Sincronizzata con implementazione reale
  - Documentato AbstractEditorModule e sistema Undo/Redo
  - Documentato BaseOverlay e overlay system
  - Aggiunto RangedModule, feature avanzate, file structure
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
