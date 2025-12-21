# Arena Template – Gap List (post-audit v2.23)

Stato aggiornato dopo audit documentazione e verifica codice.

## Gaps aperti (focus evolutivo)
1) **[OPEN] Prebuild Pool enablement (DD63)**
   - Pool implementata ma DEFERRED; decidere go/no-go dopo 2 settimane di telemetria.
   - Output atteso: decisione + config/flag definitivo + runbook update.

2) **[OPEN] Legacy adapter removal**
   - `ArenaManager.createArena()` e adapter legacy ancora presenti (deprecati).
   - Output atteso: milestone di rimozione quando KPI+release gate sono verdi.

## Allineamenti completati
- **[DONE] Doc canonicali e deprecazioni** (audit + README + runbook).
- **[DONE] Spec allineata a schema** (`extendsTemplate`, `schemaVersion` int, `origin.mode` enum).
- **[DONE] COMPLETE docs path fix** (Agent 08).
