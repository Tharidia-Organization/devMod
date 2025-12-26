# Arena Template – Gap List (post-audit v2.23)

> **Status**: CURRENT (gap tracking)

Stato aggiornato dopo audit documentazione e verifica codice.

## Gaps aperti (focus evolutivo)
1. **Prebuild Pool (DD63)**: decisione enablement basata su telemetria; default disabilitata.
2. **Legacy cleanup**: rimuovere eventuali riferimenti legacy segnalati dal WrapperAnalyzer.

## Allineamenti completati
- **[DONE] Doc canonicali e deprecazioni** (audit + README + runbook).
- **[DONE] Spec allineata a schema** (`extendsTemplate`, `schemaVersion` int, `origin.mode` enum).
- **[DONE] COMPLETE docs path fix** (Agent 08).
- **[DONE] Prebuild Pool implementation** (flag `devmod.arena.prebuildPoolEnabled` con toggle runtime; decisione enablement pending).
- **[DONE] Legacy adapter removal** (ArenaContext sostituisce ArenaManager.Arena nei flussi endurance).
