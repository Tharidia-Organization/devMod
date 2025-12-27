# Arena API Migration Inventory (post-audit)

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Status Summary
- Legacy `ArenaManager` class is not present in current source tree.
- Legacy usage patterns are tracked by `com.devmod.arena.migration.WrapperAnalyzer`.
- `LegacyCallCheck` can scan `src/main/java` for legacy patterns.
- Nessuna call-site diretta trovata in `src/main/java` per pattern legacy.
- I flussi endurance usano `ArenaContext` derivato da `ArenaHandle`.

## Call-Site Inventory (legacy patterns)
**Query utilizzata**: `rg -n "ArenaManager|LegacyArenaConfig|new\\s+ArenaInstance" src/main/java`

**Risultato**: nessuna call-site diretta fuori da `ArenaInstance.java` (match interni/factory) e
solo pattern nel `WrapperAnalyzer`.

## Adapter / Legacy Residui
- Nessun adapter legacy presente nel codice attuale.

## Next Milestones
1) Rimuovere i pattern legacy dal WrapperAnalyzer quando non servono piu'.

## Storico
Il piano originale a 6 PR resta archiviato come riferimento storico (non piu' attivo).
