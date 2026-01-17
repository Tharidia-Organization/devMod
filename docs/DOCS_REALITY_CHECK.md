# Docs Reality Check

> Ultimo aggiornamento: 2026-01-15

Seconda passata di verifica tra documentazione canonica e stato reale del repository.

## Scope

- Documenti in `docs/` (escluso `docs/_archive/`).
- Riferimenti a path e asset validati contro filesystem.
- Allineamento comandi Gradle e export telemetry.

## Verifiche eseguite

- Path in backtick verificati contro il filesystem (nessun path mancante).
- Task Gradle GameTest corretto: `runGameTestServer`.
- Config runtime aggiornate ai file effettivi in `run/config/`.
- Path principali in `docs/SYSTEMS.md` resi espliciti.
- Glossario aggiornato alle classi effettive (`DamageHandler`, `TransportCoreBlock`).
- Export telemetry allineati a `run/telemetry/exports`, `run/telemetry/csv`, `run/telemetry/reports`.
- Ranges canali network completati (challenge e compat).

## File aggiornati

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/QUICKSTART.md`
- `docs/CONFIGURATION.md`
- `docs/SYSTEMS.md`
- `docs/GLOSSARY.md`
- `docs/NETWORK.md`
- `docs/OPERATIONS.md`
- `docs/DATABASE.md`
- `docs/CLONE_MODEL_WORKFLOW.md`

## Esito

- Nessun riferimento a path inesistente nella documentazione canonica.
- Documentazione allineata allo stato attuale del codice e delle risorse.
