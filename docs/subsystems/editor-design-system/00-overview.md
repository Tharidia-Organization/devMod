# DevMod Editor Design System - Overview

> Last updated: 2025-12-26
> Status: HISTORICAL (design system snapshot)

## Version 1.5 - Template & Preset Architecture

> **Source-of-truth (IMPORTANT):** questo documento resta allineato al codice. Dove esempi e codice divergono, **vince il comportamento reale**. Stato attuale: **MultiEdit è implementato**, lo storage per-item usa **CustomData** con tag `WeaponModStats` / `ArmorModStats`, il Recipe Editor è **FUTURE / NON ORA**.

---

## Implementation Status
- **Implementation:** MultiEdit subsystem (manager + panel) added and wired into the editor UI (`src/main/java/com/devmod/client/ui/editor/systems/MultiEditManager.java`, `MultiEditPanel.java`).
- **Preset adapter:** `ItemEditorPresetManager` added to map `ItemEditorDataManager.PresetData` into `WeaponStats` / `ArmorStats` and persist via existing config managers.
- **Preset wrapper:** `DataPreset` provides a `Preset` facade over `ItemEditorDataManager.PresetData`.
- **UI:** `MultiEditPanel` shows selection, items, remove, preset selector (dropdown scrollabile), e `[Apply to all]` / `[Clear All]` actions. Default expanded; header toggles collapse; empty-state note quando zero match; Apply disabilitato in Preview.
- **Feedback:** Batch apply operations producono un `BatchEditResult`; il pannello mostra success/failure count, dettagli fallimenti espandibili e bottone "Copy" per copiare gli errori.
- **Data keys:** Per-item editor data is stored under `WeaponModStats` (weapon) e `ArmorModStats` (armor). Vecchi nomi come `devmod:stats` / `devmod:custom_stats` sono deprecati.
- **Dual-mode semantics (Preview vs Apply):** Preview operations modify an editor-local copy only and do not persist per-item CustomData or send network packets. Applying with persistence invokes the persistence handler which updates the inventory slot and sends the appropriate payloads (weapon/armor) to the server; persistence failures are surfaced as batch failures in the UI.
- **Scope update:** Recipe/Crafting panels (Feature A/C) sono ORA IN SCOPE per questa iterazione (prima marcati FUTURE); devono essere implementati in entrambi gli editor.

## Remaining work (da svolgere ora):
- Implementare Crafting Info Panel / Item Value Analysis (Feature A/C) per weapon/armor, UI e logica, con overlay/tab come da specifica.
- Aggiungere unit test per `ItemEditorPresetManager` e `MultiEditManager.applyPresetToAll`; aggiungere integrazione test per MultiEdit/Debug.
- Polish failure UI (virtualizzazione liste lunghe, modal dedicato), aggiornare screenshot/gif (nuovi media post-implementazione crafting/failure UI).
- Aggiornare documentazione e media PR con gli elementi sopra (preset dropdown, failure summary, crafting/value panels, debug).

## Media (PR4):
- Screenshot/gif #1: preset dropdown aperto con lista scrollabile.
- Screenshot/gif #2: failure summary con lista espansa + bottone Copy errors.

---

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

## UI SYSTEMS ARCHITECTURE

DevMod utilizza **due sistemi UI paralleli** per scopi diversi:

### Editor System (ItemEditorScreen)
Per editing complesso con undo/redo, payload building, source tracking.

```
EditorButton/Slider/Toggle → SectionAdapter → EditorSection → ModuleTab → AbstractEditorModule → ItemEditorScreen
```

**Moduli:**
| Modulo | Maturità | Stato |
|--------|----------|-------|
| WeaponModule | ⭐⭐⭐⭐⭐ | Reference implementation |
| ArmorModule | ⭐⭐⭐⭐⭐ | Reference implementation |
| RangedModule | ⭐⭐⭐⭐ | Funzionale |
| RecipeModule | ⭐⭐⭐⭐ | Funzionale |
| GeneralModule | ⭐⭐⭐⭐ | ✅ **Navigation Hub** (vedi [27-general-module-hub.md](27-general-module-hub.md)) |

### Panel System (VoxelLab)
Per settings screens, dashboard views, config toggles.

```
EditorButton → ButtonRow → SectionPanel → PanelContainer → AbstractVoxelLabPage → VoxelLabScreen
```

**Pagine:** OverviewPage, DebugOverlaysPage, HudSystemsPage, TelemetryPage, EffectsPage, CombatPage, ComponentShowcasePage

### Comparison
Vedi [23-architecture-comparison.md](23-architecture-comparison.md) per guida completa su quando usare quale sistema.

---

## RELATED DOCUMENTS

### Core Architecture
- [23-architecture-comparison.md](23-architecture-comparison.md) - Editor vs Panel system comparison
- [08-unified-architecture.md](08-unified-architecture.md) - Unified editor architecture

### Component Library
- [24-component-library.md](24-component-library.md) - EditorButton, Slider, Toggle, TextField
- [25-panel-system.md](25-panel-system.md) - UIPanel sealed interface, PanelContainer

### Module Development
- [26-module-evolution-guide.md](26-module-evolution-guide.md) - Maturity levels and upgrade checklist
- [27-general-module-hub.md](27-general-module-hub.md) - GeneralModule as Navigation Hub

### Feature Specs
- [15-weapon-properties.md](15-weapon-properties.md) - WeaponModule reference
- [16-armor-properties.md](16-armor-properties.md) - ArmorModule reference
- [16-ranged-weapons.md](16-ranged-weapons.md) - RangedModule support
