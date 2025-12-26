# Recipe Editor - Specifica Tecnica Completa

> **Status**: ⏳ PLANNING - Feature non ancora implementata
> **Last Updated**: 2024-12-23
> **Type**: Specifica tecnica per sviluppo futuro

## ⚠️ Nota Importante

Questa cartella contiene la **specifica tecnica** per il Recipe Editor, una feature **pianificata ma non ancora implementata**. I documenti qui presenti sono riferimenti progettuali, NON documentazione di codice esistente.

---

## Overview

Il Recipe Editor di DevMod permetterà la creazione e modifica di qualsiasi tipo di ricetta direttamente in-game, con persistenza via datapack e sincronizzazione multiplayer.

## Scope

| Tipo Ricetta | Supporto | Milestone |
|--------------|----------|-----------|
| Crafting Shaped | Full | M1 |
| Crafting Shapeless | Full | M1 |
| Smelting | Full | M2 |
| Blasting | Full | M2 |
| Smoking | Full | M2 |
| Campfire Cooking | Full | M2 |
| Smithing Transform | Full | M3 |
| Smithing Trim | Full | M3 |
| Stonecutting | Full | M3 |

## Documentazione

| Documento | Descrizione |
|-----------|-------------|
| [01-neoforge-api.md](01-neoforge-api.md) | API NeoForge 1.21 per ricette |
| [02-json-formats.md](02-json-formats.md) | Formati JSON Minecraft 1.21 |
| [03-architecture.md](03-architecture.md) | Architettura sistema (sealed interfaces, records) |
| [04-ui-components.md](04-ui-components.md) | Componenti UI (grid, slots, modules) |
| [05-network-persistence.md](05-network-persistence.md) | Network sync e persistence |
| [06-milestones.md](06-milestones.md) | Piano implementazione incrementale |
| [07-file-reference.md](07-file-reference.md) | Files da creare/modificare |
| [08-checklist.md](08-checklist.md) | Checklist implementazione |

## Effort Estimate

| Milestone | Righe | Complessita |
|-----------|-------|-------------|
| M1: Crafting | 800-1000 | Alta |
| M2: Smelting | 300-400 | Media |
| M3: Smithing | 400-500 | Media |
| M4: Advanced | 400-600 | Alta |
| **Totale** | **1900-2500** | **Alta** |

## Dipendenze (tutte esistenti)

- `AbstractEditorModule` - Base per RecipeModule
- `DatapackIO` - Pattern per export
- `NetworkHandler` - Pattern per payloads
- `CraftingInfoPanel` - Reference per grid
- `SlotSelector` - Pattern per slots
- `ConfigPaths` - Path management

## Riferimenti Esterni

- [NeoForge Recipes](https://docs.neoforged.net/docs/resources/server/recipes/)
- [NeoForge Custom Recipes](https://docs.neoforged.net/docs/resources/server/recipes/custom)
- [NeoForge Ingredients](https://docs.neoforged.net/docs/resources/server/recipes/ingredients)
- [Minecraft Wiki - Recipe](https://minecraft.wiki/w/Recipe)

---

## Cross-References

- [[MOC]] - Master index
- [[FEATURES]] - Feature list (Recipe Editor listed as PLANNED)
- [[subsystems/editor-design-system/README]] - Editor UI patterns da riutilizzare
