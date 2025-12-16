# Recipe Editor (FUTURE - Feature B) — NON ORA

> **NOTA:** Feature fuori perimetro per l'iterazione attuale. Non implementare ora.
> Rimandata a fase successiva e documentata solo come riferimento.

## Descrizione
Permettere la modifica runtime delle ricette di crafting direttamente dall'editor.

## Complessità
- Richiede integrazione profonda con il RecipeManager di Minecraft
- Necessita sincronizzazione server-client per le ricette modificate
- Deve gestire conflitti con ricette esistenti
- Richiede persistenza delle modifiche (datapack o config)

## Approcci Possibili
1. **Datapack Generation** - Genera JSON ricette in un datapack custom
2. **Runtime Recipe Injection** - Modifica RecipeManager in memoria (reset al restart)
3. **Config-based** - Salva override in config, applica al caricamento

## Dipendenze
- Sistema di persistenza ricette
- UI per editing griglia 3x3 con drag & drop
- Validazione ricette (no duplicati, ingredienti validi)
- Sync network per multiplayer

## Stima Effort
| Approccio | Linee | Complessità |
|-----------|-------|-------------|
| Datapack | 500-700 | Alta |
| Runtime | 300-400 | Media (non persistente) |
| Config | 400-500 | Media-Alta |

*Questa feature sarà sviluppata in un documento separato: `RECIPE_EDITOR_PLAN.md`*