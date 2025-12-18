# Editor Design System

Sistema di design unificato per gli editor di item/armor/weapon di DevMod.

## Struttura Documentazione

### Core Architecture
- [00-overview.md](00-overview.md) - Panoramica generale del design system
- [01-layout-specifications.md](01-layout-specifications.md) - Specifiche layout e dimensioni
- [02-shared-components.md](02-shared-components.md) - Componenti UI condivisi
- [03-architecture.md](03-architecture.md) - Architettura generale
- [03-crafting-analysis.md](03-crafting-analysis.md) - Analisi sistema crafting

### Core Systems
- [04-debug-system.md](04-debug-system.md) - **PRIORITÀ MASSIMA** - Debug panel
- [05-dual-mode-system.md](05-dual-mode-system.md) - Sistema PREVIEW/APPLY mode
- [06-persistence.md](06-persistence.md) - Architettura persistenza dati
- [07-ui-scaling.md](07-ui-scaling.md) - Sistema scaling UI e risoluzione
- [08-unified-architecture.md](08-unified-architecture.md) - Architettura editor unificato

### Advanced Features
- [09-radial-menu-integration.md](09-radial-menu-integration.md) - Integrazione radial menu
- [10-weapon-types.md](10-weapon-types.md) - Rilevamento tipi arma e supporto modded
- [11-multiedit-system.md](11-multiedit-system.md) - Sistema multi-editing
- [12-grid-spacing.md](12-grid-spacing.md) - Sistema griglia 4px e spacing
- [13-scroll-system.md](13-scroll-system.md) - Sistema scrolling content

### Development Tools
- [14-debug-overlay.md](14-debug-overlay.md) - Debug overlay F9 per sviluppo
- [15-weapon-properties.md](15-weapon-properties.md) - Architettura proprietà armi
- [16-ranged-weapons.md](16-ranged-weapons.md) - Supporto armi a distanza

## Stato Implementazione

### P0 - Priorità Massima
- [x] **04-debug-system.md** - Debug panel (DEVE essere implementato PRIMA di tutto)
- [x] 01-layout-specifications.md - Layout base
- [x] 02-shared-components.md - Componenti UI base
- [x] 05-dual-mode-system.md - PREVIEW/APPLY mode

### P1 - Priorità Alta
- [x] 08-unified-architecture.md - Architettura unificata
- [x] 06-persistence.md - Sistema persistenza
- [x] 07-ui-scaling.md - UI scaling
- [x] 12-grid-spacing.md - Sistema griglia

### P2 - Priorità Media
- [x] 15-weapon-properties.md - Proprietà armi
- [x] 10-weapon-types.md - Rilevamento tipi arma
- [x] 14-debug-overlay.md - Debug overlay
- [x] 13-scroll-system.md - Sistema scrolling

### P3 - Priorità Bassa
- [x] 09-radial-menu-integration.md - Radial menu
- [x] 11-multiedit-system.md - Multi-editing
- [ ] 16-ranged-weapons.md - Armi a distanza (modello pronto, integrazione UI/payload da completare)

### Implementation Support
- [17-implementation-guide.md](17-implementation-guide.md) - Guida implementazione con ordine prioritario
- [18-testing-strategy.md](18-testing-strategy.md) - Strategia testing completa
- [19-performance-considerations.md](19-performance-considerations.md) - Ottimizzazioni performance

## Note Importanti

1. **Il Debug Panel (04-debug-system.md) è PRIORITÀ ASSOLUTA** - deve essere implementato prima di qualsiasi altra feature "nice-to-have"

2. **Duplicazioni Rimosse**: Durante la riorganizzazione sono stati rimossi i seguenti duplicati:
   - `06-persistence-storage.md` (identico a `05-persistence.md`)
   - `13-grid-spacing.md` (identico a `10-grid-spacing.md`)
   - `12-debug-overlay.md` (identico a `14-debug-overlay.md`)
   - `07-weapon-properties.md` (meno completo di `15-weapon-properties.md`)
   - `11-ui-scaling.md` (duplicato di scaling)

3. **Numerazione Sequenziale**: I file sono ora numerati in sequenza logica senza gap o sovrapposizioni.

4. **Aggiornamenti recenti**: Il Crafting Info Panel (03-crafting-analysis.md) è stato implementato con overlay modale, selezione multi-ricetta e lista ingredienti scrollabile (trigger “Recipe” nel footer).

## Strategia di Completamento

### Fase 1: Foundation (P0)
Implementare debug panel e componenti base per avere una base solida di sviluppo.

### Fase 2: Core Features (P1)
Implementare architettura unificata e sistemi di persistenza/scaling.

### Fase 3: Advanced Features (P2-P3)
Aggiungere features avanzate e supporto per tipi di arma specializzati.

## Stato Scomposizione

✅ **Scomposizione Completata**
- 19 file documentazione creati
- Duplicazioni rimosse e riorganizzate
- Guida implementazione con priorità definite
- Strategia testing completa
- Considerazioni performance documentate

**Prossimo Step**: Seguire [17-implementation-guide.md](17-implementation-guide.md) per implementazione

## Riferimenti Architetturali

- **Design Pattern**: Module-based architecture con layout engine centralizzato
- **UI Framework**: Minecraft GuiGraphics con componenti custom
- **Data Flow**: PREVIEW mode (client-only) vs APPLY mode (server sync)
- **Persistence**: NBT + ServerConfig + Datapack export
- **Scaling**: Discrete scale factors (1.0x, 1.25x, 1.5x, 2.0x)
- **Grid System**: 4px base unit con spacing tokens

---

## UI Systems Comparison (NEW)

DevMod usa due sistemi UI paralleli. Vedi [23-architecture-comparison.md](23-architecture-comparison.md) per guida completa.

- [23-architecture-comparison.md](23-architecture-comparison.md) - Editor vs Panel system
- [24-component-library.md](24-component-library.md) - EditorButton, Slider, Toggle, TextField
- [25-panel-system.md](25-panel-system.md) - UIPanel, PanelContainer, AbstractVoxelLabPage

## Module Development (NEW)

Guida per sviluppo e upgrade moduli editor.

- [26-module-evolution-guide.md](26-module-evolution-guide.md) - Maturity levels (⭐-⭐⭐⭐⭐⭐) e checklist upgrade
- [27-general-module-hub.md](27-general-module-hub.md) - GeneralModule redesign come Navigation Hub

## Module Maturity Status

| Module | Level | Status |
|--------|-------|--------|
| WeaponModule | ⭐⭐⭐⭐⭐ | Reference |
| ArmorModule | ⭐⭐⭐⭐⭐ | Reference |
| RangedModule | ⭐⭐⭐⭐ | Functional |
| RecipeModule | ⭐⭐⭐⭐ | Functional |
| GeneralModule | ⭐⭐ | **→ Navigation Hub (PLANNED)** |
