# Arena API Migration Inventory (post-audit)

## Status Summary
- Legacy `ArenaManager.createArena()` is **deprecated** and returns null.
- Nessuna call-site diretta trovata in `src/main/java` per `createArena()`.
- L'adapter `ArenaManager.Arena` e' stato rimosso dai flussi endurance; ora si usa `ArenaContext` derivato da `ArenaHandle`.

## Call-Site Inventory (legacy createArena)
**Query utilizzata**: `rg -n "createArena\(" src/main/java`

**Risultato**: nessuna call-site diretta (solo definizione metodo deprecato).

## Adapter / Legacy Residui
- `src/main/java/com/frenkvs/devmod/endurance/ArenaManager.java` resta deprecato (no call-site attive).

## Next Milestones
1) Rimuovere definitivamente `ArenaManager` quando non serve piu' per compatibilita' storica.

## Storico
Il piano originale a 6 PR resta archiviato come riferimento storico (non piu' attivo).
