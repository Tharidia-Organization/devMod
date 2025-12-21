# Arena API Migration Inventory (post-audit)

## Status Summary
- Legacy `ArenaManager.createArena()` is **deprecated** and returns null.
- Nessuna call-site diretta trovata in `src/main/java` per `createArena()`.
- L'adapter `ArenaManager.Arena` e' ancora usato come contenitore legacy in alcuni flussi endurance; la creazione avviene via `ArenaHandle`.

## Call-Site Inventory (legacy createArena)
**Query utilizzata**: `rg -n "createArena\(" src/main/java`

**Risultato**: nessuna call-site diretta (solo definizione metodo deprecato).

## Adapter / Legacy Residui
- `src/main/java/com/frenkvs/devmod/endurance/ArenaManager.java` (metodo deprecato).
- Adapter verso `ArenaManager.Arena` in `EnduranceQuestManager` per compatibilita' runtime.

## Next Milestones
1) Rimuovere l'adapter legacy dopo conferma KPI + release gate verdi.
2) Eliminare `ArenaManager.Arena` dai consumer che possono usare `ArenaHandle` direttamente.

## Storico
Il piano originale a 6 PR resta archiviato come riferimento storico (non piu' attivo).
