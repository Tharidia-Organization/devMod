# Docs Reality Check

> Ultimo aggiornamento: 2026-01-31

Passata di verifica tra documentazione canonica e stato reale del repository.

## Scope

- Documenti in `docs/` (escluso `docs/_archive/`).
- Inventari generati da filesystem e sorgenti Java (packages, assets, data pack, config, network).
- Allineamento comandi Gradle e endpoint operativi (dashboard/admin panel).

## Verifiche eseguite

- Rigenerato `docs/IMPLEMENTATION_STATE.md` da codice e risorse reali.
- Aggiornato `docs/CONFIGURATION.md` con reference chiavi da `Config.java`, `GameMechanicsConfig.java`, `PortalConfig.java`, `EditorClientConfig.java` e `WISClientConfig.java`.
- Aggiornato `docs/NETWORK.md` con registry completo da `ChannelId`.
- Aggiornato inventari asset e data pack in `docs/ASSETS_AND_DATA.md`.
- Allineati `docs/ARCHITECTURE.md`, `docs/SYSTEMS.md`, `docs/FEATURES.md`, `docs/OPERATIONS.md`, `docs/GLOSSARY.md`, `docs/README.md` alle strutture attuali.
- Creato `docs/TEST_COVERAGE_REPORT.md` con report coverage aggiornato.
- Ripristinati runbook in `docs/runbook/` (arena alerts, verify, release checklist).
- Aggiornati crediti asset in `docs/ASSETS_CREDITS.md` secondo fonti registrate.

## Esito

- Nessun riferimento a path inesistente nei documenti canonici aggiornati.
- Documentazione allineata allo stato corrente del repo (31/01/2026), coverage generata e verificata.
