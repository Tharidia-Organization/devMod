# DevMod Editor Design System
## Unified UI/UX Specification for Item Editors
### Version 1.5 - Template & Preset Architecture

> **Source-of-truth (IMPORTANT):** questo documento resta allineato al codice. Dove esempi e codice divergono, **vince il comportamento reale**. Stato attuale: **MultiEdit è implementato**, lo storage per-item usa **CustomData** con tag `WeaponModStats` / `ArmorModStats`, il Recipe Editor è **FUTURE / NON ORA**.

---

> **NOTA:** Questo documento è stato scomposto in file più piccoli per facilitare la manutenzione. Vedere i file nella directory `docs/editor-design-system/` per le specifiche dettagliate.

## File Structure

### Core Documentation
- [00-overview.md](00-overview.md) - Executive Summary e Strategic Context
- [01-layout-specifications.md](01-layout-specifications.md) - Dimensioni, Layout Master, Posizioni
- [02-shared-components.md](02-shared-components.md) - Componenti condivisi tra editor
- [03-architecture.md](03-architecture.md) - Architettura unificata
- [03-crafting-analysis.md](03-crafting-analysis.md) - Analisi crafting e item value
- [04-debug-system.md](04-debug-system.md) - Sistema di debug
- [05-dual-mode-system.md](05-dual-mode-system.md) - Preview vs Apply modes
- [06-persistence.md](06-persistence.md) - Architettura di persistenza
- [07-ui-scaling.md](07-ui-scaling.md) - Scaling e risoluzione
- [08-unified-architecture.md](08-unified-architecture.md) - Architettura editor unificata
- [09-radial-menu-integration.md](09-radial-menu-integration.md) - Integrazione radial menu
- [10-weapon-types.md](10-weapon-types.md) - Tipi di armi supportati
- [11-multiedit-system.md](11-multiedit-system.md) - Sistema multi-edit
- [12-grid-spacing.md](12-grid-spacing.md) - Sistema griglia e spacing
- [13-scroll-system.md](13-scroll-system.md) - Sistema scroll
- [14-debug-overlay.md](14-debug-overlay.md) - Debug overlay
- [15-weapon-properties.md](15-weapon-properties.md) - Proprietà armi
- [16-ranged-weapons.md](16-ranged-weapons.md) - Armi a distanza
- [17-implementation-guide.md](17-implementation-guide.md) - Guida implementazione
- [18-testing-strategy.md](18-testing-strategy.md) - Strategia testing
- [19-performance-considerations.md](19-performance-considerations.md) - Considerazioni performance

### Advanced Features
- [20-crafting-info-panel.md](20-crafting-info-panel.md) - Crafting Info Panel
- [21-template-preset-architecture.md](21-template-preset-architecture.md) - Template & Preset System
- [22-recipe-editor-future.md](22-recipe-editor-future.md) - Recipe Editor (Future)

### Meta Documentation
- [COMPLETION_STRATEGY.md](COMPLETION_STRATEGY.md) - Strategia di completamento
- [README.md](README.md) - Guida alla documentazione

---

## Quick Reference

### Implementation Status
- **MultiEdit System**: ✅ Implementato
- **Dual-Mode System**: ✅ Implementato  
- **Debug System**: ✅ Implementato
- **Crafting Info Panel**: ✅ Implementato (overlay con selezione multi-ricetta, scroll ingredienti, bottone footer “Recipe”)
- **Template & Preset System**: ✅ Implementato (overlay preset + favorites, import/export/preset wiring)
- **Ranged Weapons Support**: ☐ Parziale (UI/payload bow/crossbow attivi; mancano tridente, source indicator, ammo filter)

### Remaining Work
- Integrare il supporto armi a distanza nell’editor (UI, payload, preview/apply) partendo da `RangedWeaponModule`
- Aggiornare documentazione e media PR post-implementazione (preset overlay, crafting/value panel, failure summary)
- Rafforzare la copertura test su preset/multi-edit/debug clipboard e mitigare la flakiness DuckDB segnalata nel log

### Key Dimensions
- Panel: 550px × 420px
- Header: 28px height
- Footer: 60px height  
- Left Column: 140px width
- Content Area: 390px width

### Key Shortcuts
- `F5`: Toggle PREVIEW ↔ APPLY mode
- `F9`: Toggle debug overlay
- `F10`: Grid overlay
- `F11`: Bounds overlay
- `Ctrl+Enter`: Quick apply (APPLY mode only)

---

Per dettagli completi, consultare i file specifici nella directory.
