# Arena Template – Gap List (post-audit v2.23)

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

Stato aggiornato dopo audit documentazione e verifica codice.

## Gaps aperti (focus evolutivo)
1. **Prebuild Pool (DD63)**: decisione enablement basata su telemetria; default disabilitata
   (flag `devmod.arena.prebuildPoolEnabled` / `DEVMOD_ARENA_PREBUILD_POOL_ENABLED`).
2. **Legacy cleanup**: rimuovere eventuali riferimenti legacy segnalati dal `WrapperAnalyzer`
   e dal job `LegacyCallCheck`.

## Allineamenti completati
- **[DONE] Doc canonicali e deprecazioni** (audit + README + runbook).
- **[DONE] Spec allineata a schema** (`extendsTemplate`, `schemaVersion` int, `origin.mode` enum).
- **[DONE] COMPLETE docs path fix** (Agent 08).
- **[DONE] Prebuild Pool implementation** (flag `devmod.arena.prebuildPoolEnabled`; decisione enablement pending).
- **[DONE] Legacy adapter removal** (ArenaContext sostituisce ArenaManager.Arena nei flussi endurance).
