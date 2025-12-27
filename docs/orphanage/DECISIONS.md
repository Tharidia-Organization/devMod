# Orphanage Decisions Log

**Date**: 2025-12-27
**Format**: ADR-style

---

## ADR-001: Integrare ConfigurableTestTemplate

### Status
ACCEPTED - Implementato

### Context
`ConfigurableTestTemplate` esisteva ma non era mai registrato in `DynamicTestGenerator`. Nessun template configurabile veniva caricato dai JSON di `ModTestConfig`.

### Decision
Registrare template configurabili dopo `ModDiscoveryService.scanMods()` tramite `registerConfigTemplates()`.

### Alternatives Considered
1. **Rimuovere la classe** - Perdita di funzionalita configurabili
2. **Caricare solo DevMod** - Limiterebbe l'estendibilita

### Impact
- Template configurabili attivi per mod con JSON
- Nessun breaking change per mod senza config

---

## ADR-002: Integrare PathSanitizer per export telemetry

### Status
ACCEPTED - Implementato

### Context
`PathSanitizer` era unused, mentre gli exporter scrivevano direttamente su filesystem. Necessario validare path e consentire `.png` per heatmap.

### Decision
Usare `PathSanitizer.sanitizeForWrite()` in `CsvExporter`, `JsonReportExporter`, `HeatmapExporter` e aggiungere `.png` alle estensioni write.

### Alternatives Considered
1. **Lasciare invariato** - Nessun controllo path
2. **Validare solo directory** - `PathSanitizer` richiede estensione file

### Impact
- Export bloccati su path non validi
- Heatmap PNG supportate

---

## ADR-003: Rimuovere renderer notifiche legacy

### Status
ACCEPTED - Implementato

### Context
`ToastRenderer` e `BannerRenderer` non hanno call-site; sostituiti da `UnifiedToastOverlay`.

### Decision
Rimuovere i due file.

### Alternatives Considered
1. **Quarantena** - Codice duplicato e non usato
2. **Integrare** - Sistema unified gia` copre i casi

### Impact
- Riduzione codebase
- Nessun impatto funzionale

---

## ADR-004: Rimuovere payload party non registrati

### Status
ACCEPTED - Implementato

### Context
`PartyInvitePayload`, `PartyNotificationPayload` e `RequestOnlinePlayersPayload` non sono registrati in `NetworkHandler` e non hanno call-site.

### Decision
Rimuovere i tre payload.

### Alternatives Considered
1. **Registrare i payload** - Nessun usage reale
2. **Quarantena** - Più rumore che valore

### Impact
- Riduzione dead code
- Nessun impatto funzionale

---

## ADR-005: Rimuovere BuildProgressOverlay + test orfani

### Status
ACCEPTED - Implementato

### Context
`BuildProgressOverlay` non e` usato dal runtime; solo test e docs lo referenziavano.

### Decision
Rimuovere la classe, eliminare `BuildProgressOverlayTest`, e ridurre `ArenaSystemIntegrationTest` alla sola parte payload/monitor/eventi.

### Alternatives Considered
1. **Integrare overlay nel runtime** - L'HUD attuale usa `BuildProgressHud`/`BuildProgressPayload`
2. **Quarantena** - Test restavano orfani

### Impact
- Test suite pulita da test orfani
- Docs aggiornate

---

## ADR-006: Quarantena di classi non referenziate

### Status
ACCEPTED - Implementato (poi superato da ADR-011/012/013)

### Context
86 classi risultavano senza call-site in `src/main/java` (vedi `_candidate_unreferenced.txt`). Coprono debug, endurance legacy, telemetry, editor UI e vari sottosistemi arena.

### Decision
Marcare con `@Deprecated(forRemoval=true)` + commento orphanage per mantenere il codice come riferimento senza lasciarlo in mainline “attivo”.

### Alternatives Considered
1. **Rimozione totale** - Rischio di perdita knowledge futura
2. **Integrazione completa** - Troppo ampia per questa fase

### Impact
- Chiarezza su codice legacy/inattivo
- Nessun cambiamento runtime

---

## ADR-007: KEEP per GameTest holder (reflection)

### Status
ACCEPTED - Nessuna azione

### Context
`L0BootVerificationTests` e `InstanceSystemGameTests` non hanno call-site ma sono scoperti via `@GameTestHolder`/`@GameTest`.

### Decision
KEEP come entrypoint riflessivi validi.

### Alternatives Considered
1. **Quarantena** - Rischio di disabilitare test runtime

### Impact
- Nessuna modifica

---

## ADR-008: Rimuovere notifiche legacy (overlay + payload)

### Status
ACCEPTED - Implementato

### Context
Overlay client e payload endurance legacy risultano senza call-site. La pipeline attuale usa `NotificationService` e `UnifiedNotificationPayload` (es. `EnduranceEventHandler`, `SeasonPassSystem`, `CommonModEvents`).

### Decision
Rimuovere overlay e payload legacy per badge/token/record/combo/season/resonance/welcome e mail/news.

### Alternatives Considered
1. **Quarantena** - Riduce rumore ma lascia codice morto nel runtime
2. **Reintegrazione legacy** - Duplica la pipeline unificata

### Impact
- Notifiche consolidate nel sistema unificato
- Riduzione dead code lato client e network

---

## ADR-009: Rimuovere pannelli client non referenziati

### Status
ACCEPTED - Implementato

### Context
`PositionSmoother`, `TestProgressPanel` e `ToolStatusPanel` non hanno call-site o registrazioni UI.

### Decision
Rimuovere i pannelli legacy non usati.

### Alternatives Considered
1. **Quarantena** - Nessun valore immediato, mantiene superficie morta
2. **Integrare nel HUD** - Richiede design/UI non pianificati

### Impact
- Meno superficie client inutilizzata
- Nessun impatto runtime

---

## ADR-010: Rimuovere texture macro non usate

### Status
ACCEPTED - Implementato

### Context
`macro_*.png` non e` referenziato da JSON o codice (radial/menu).

### Decision
Rimuovere le texture non referenziate.

### Alternatives Considered
1. **Tenere come placeholder** - Nessun usage reale
2. **Spostare in docs/_deprecated** - Inutile per asset grafici

### Impact
- Asset pack piu` snello
- Nessun impatto funzionale

---

## ADR-011: Rimuovere legacy debug/telemetry/endurance/migration

### Status
ACCEPTED - Implementato

### Context
Classi legacy senza call-site runtime, non registrate via event bus o reflection. `ArmorMigrationHelper` aveva solo test dedicato senza usage in main.

### Decision
Rimuovere `DebugClientRenderer`, `DebugDataCollector`, `PrestigeResetSystem`, `BloodContractRegistry`, `ClientSeasonCache`, `LandmarkService`, `JumpAnalysisService`, `MemoryCleanupService`, `ArmorMigrationHelper`, `LegacyCallCheck` e relativi test orfani.

### Alternatives Considered
1. **Integrare nel runtime** - Nessun hook/registry attuale
2. **Quarantena permanente** - Lascia codice morto senza valore operativo

### Impact
- Riduzione superficie legacy
- Test orfani eliminati

---

## ADR-012: Rimuovere UI editor/radial legacy + test orfani

### Status
ACCEPTED - Implementato

### Context
Sistema UI/editor precedente senza call-site. Nessun caricamento riflessivo o registrazioni attive; test unitari coprivano solo classi non usate.

### Decision
Rimuovere stack editor/radial legacy (`EscapeBehavior`, `ItemEditor*`, `OverlayInputGuard`, `RadialMenuState`, `TransitionAnimator`, `ScrollableSettingsPage`, `ArenaDebugHud/ArenaHudKeyBinding`) e test collegati.

### Alternatives Considered
1. **Integrare nel flow UI** - Richiede redesign completo non pianificato
2. **Quarantena** - Non giustifica mantenimento nel runtime

### Impact
- Codebase UI piu` coerente
- Test orfani rimossi

---

## ADR-013: Rimuovere subsistemi arena non integrati + suite test orfane

### Status
ACCEPTED - Implementato

### Context
Ampio set di servizi arena (analytics/validation/fallback/registry/etc.) senza call-site o registrazioni. Coperti solo da test dedicati; nessun usage runtime.

### Decision
Rimuovere i subsistemi arena non integrati e i test correlati (HeatmapCollector, PolicyMutatorResolver, AdvancedArenaTemplateValidator, FallbackBuildStrategy, DuckDbRepository, ArenaFailureHandler, ArenaQuestIntegration, ecc.).

### Alternatives Considered
1. **Integrare nel runtime** - Scope troppo ampio per questa fase
2. **Quarantena** - Mantiene codice non usato in mainline

### Impact
- Riduzione significativa di dead code
- Test suite allineata all’uso reale

---

## ADR-014: Pulizia commenti con riferimenti a classi rimosse

### Status
ACCEPTED - Implementato

### Context
Due commenti facevano riferimento a classi rimosse (`MemoryCleanupService` e payload notifiche legacy), generando rumore nelle scansioni orphan.

### Decision
Rimuovere i riferimenti dalle docstring/commenti e mantenerli neutrali.

### Alternatives Considered
1. **Lasciare i riferimenti** - Continui falsi positivi durante la discovery
2. **Spostare note in docs legacy** - Overhead per due righe di commento

### Impact
- Scansioni orphan piu` pulite
- Nessun impatto funzionale

---

## Decision Summary

| ADR | Azione | Priorita | Rischio |
|-----|--------|----------|---------|
| 001 | INTEGRATE ConfigurableTestTemplate | ALTA | MEDIO |
| 002 | INTEGRATE PathSanitizer | MEDIA | BASSO |
| 003 | REMOVE ToastRenderer + BannerRenderer | MEDIA | BASSO |
| 004 | REMOVE PartyInvitePayload + PartyNotificationPayload + RequestOnlinePlayersPayload | MEDIA | BASSO |
| 005 | REMOVE BuildProgressOverlay + tests | ALTA | BASSO |
| 006 | QUARANTINE 86 classi (superseded) | MEDIA | MEDIO |
| 007 | KEEP GameTest holders | ALTA | ALTO |
| 008 | REMOVE notifiche legacy (overlay + payload) | ALTA | BASSO |
| 009 | REMOVE pannelli client non referenziati | MEDIA | BASSO |
| 010 | REMOVE texture macro non usate | BASSA | BASSO |
| 011 | REMOVE legacy debug/telemetry/endurance/migration | MEDIA | BASSO |
| 012 | REMOVE UI editor/radial legacy + test orfani | MEDIA | MEDIO |
| 013 | REMOVE subsistemi arena non integrati + test orfani | ALTA | MEDIO |
| 014 | CLEANUP commenti con riferimenti a classi rimosse | BASSA | BASSO |
