# Orphanage Final Report

**Date**: 2025-12-27
**Branch**: Banastaff

## Summary

- **Orphans found**: 133 (113 main, 16 test, 4 resources)
- **Integrated**: 2
- **Quarantined**: 0
- **Removed**: 129
- **Kept (reflection entrypoints)**: 2

## Build/Test Status

- `./gradlew build`: PASS
- `./gradlew test`: PASS
- Warnings (build/test): NullAway in `TicketNetworkHandler`, FutureReturnValueIgnored in `CommonModEvents`, UnusedVariable/StringSplitter in `NotificationCenterScreen`, ImportOrder in `NotificationService`, `MailboxApiServer`, `NetworkHandler`, `PartyNetworkHandler`, `CommonModEvents`, `InvitePopupScreen`, `NotificationBadgeOverlay`, `ClientNetworkPayloadHooks`, `ClientModEvents`, unused imports in `SeasonPassSystem`/`RewardSystem`

## Remaining Risks

- Possibili orfani “di secondo livello” (reflection/config/string refs) non rilevati dal solo scan per class-name.
- Documentazione arena/editor ora parzialmente storica: evitare di riusare pattern rimossi senza re‑integrazione esplicita.

## Next Steps (max 10)

1. Aggiungere test di regressione per `ConfigurableTestTemplate` (fixture config).
2. Potenziare lo scan statico includendo string refs in config/mixin JSON.
3. Se si reintroducono feature arena/editor rimosse, creare una nuova integrazione con entrypoint chiari.
