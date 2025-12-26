# Milestones - Recipe Editor Implementation

> Piano di implementazione incrementale

## Overview

| Milestone | Scope | Effort | Complessita |
|-----------|-------|--------|-------------|
| M1: Crafting | Core + Crafting MVP | 800-1000 LOC | Alta |
| M2: Smelting | Furnace/Blast/Smoker/Campfire | 300-400 LOC | Media |
| M3: Smithing | Transform + Trim + Stonecutting | 400-500 LOC | Media |
| M4: Advanced | Tags, Priorities, Bulk ops | 400-600 LOC | Alta |
| **Totale** | | **1900-2500 LOC** | |

---

## Milestone 1: Crafting (MVP)

**Obiettivo:** Editor funzionale per ricette crafting shaped/shapeless

### Componenti

| Componente | Priorita | Note |
|------------|----------|------|
| `RecipeData.java` | P0 | Sealed interface + CraftingRecipeData |
| `IngredientData.java` | P0 | Record ingrediente |
| `ResultData.java` | P0 | Record risultato |
| `RecipeConfigManager.java` | P0 | CRUD + persistence |
| `RecipeSerializer.java` | P0 | JSON crafting only |
| `RecipeValidator.java` | P0 | Validazione base |
| `RecipeGridComponent.java` | P0 | Grid 3x3 interattiva |
| `IngredientSlotComponent.java` | P0 | Slot singolo |
| `RecipeResultSlot.java` | P0 | Slot risultato |
| `CraftingEditorModule.java` | P0 | Module principale |
| `RecipeSyncPayload.java` | P0 | Network payload |
| `RecipeDataCodec.java` | P0 | Network codec |
| NetworkHandler registration | P0 | Handler setup |
| DatapackIO extension | P0 | Export crafting |

### Deliverables

1. Creare nuova ricetta crafting shaped/shapeless
2. Grid 3x3 con drag-drop
3. Validazione in tempo reale
4. Salvataggio locale (serverconfig)
5. Sync multiplayer
6. Export datapack

### Acceptance Criteria

- [ ] Utente puo aprire editor su qualsiasi item
- [ ] Grid mostra 9 slot interattivi
- [ ] Click sinistro seleziona slot
- [ ] Click destro rimuove ingrediente
- [ ] Drag-drop per spostare ingredienti
- [ ] Toggle shaped/shapeless funzionante
- [ ] Validazione mostra errori
- [ ] Salvataggio persiste al riavvio
- [ ] Altro giocatore riceve ricetta
- [ ] Export genera JSON valido

---

## Milestone 2: Smelting

**Obiettivo:** Supporto ricette furnace, blast furnace, smoker, campfire

### Componenti

| Componente | Priorita | Note |
|------------|----------|------|
| `SmeltingRecipeData.java` | P1 | Nuovo record |
| `SmeltingEditorModule.java` | P1 | Module dedicato |
| RecipeSerializer extension | P1 | Smelting JSON |
| RecipeDataCodec extension | P1 | Network smelting |
| Time/XP sliders | P1 | UI controls |

### Features

- Selezione tipo (Furnace/Blast/Smoker/Campfire)
- Input slot singolo
- Output slot
- Slider cooking time con preview secondi
- Slider experience (0.0-10.0)
- Default cooking time per tipo

### Acceptance Criteria

- [ ] Dropdown selezione tipo smelting
- [ ] Cooking time slider funzionante
- [ ] Experience slider funzionante
- [ ] Default values corretti per ogni tipo
- [ ] Export genera JSON valido per ogni tipo

---

## Milestone 3: Smithing & Stonecutting

**Obiettivo:** Supporto smithing table e stonecutter

### Componenti

| Componente | Priorita | Note |
|------------|----------|------|
| `SmithingRecipeData.java` | P2 | Transform + Trim |
| `StonecuttingRecipeData.java` | P2 | Record semplice |
| `SmithingEditorModule.java` | P2 | 3 slot layout |
| `StonecuttingEditorModule.java` | P2 | 2 slot layout |
| RecipeSerializer extension | P2 | Smithing/Stonecutting JSON |
| RecipeDataCodec extension | P2 | Network codec |

### Smithing Features

- Toggle Transform/Trim
- Template slot (smithing template)
- Base slot (item da modificare)
- Addition slot (materiale)
- Result slot (solo per Transform)
- Info: "Result inherits components from base"

### Stonecutting Features

- Input slot
- Output slot con quantity
- Nota: multiple outputs dallo stesso input

### Acceptance Criteria

- [ ] Smithing Transform funzionante
- [ ] Smithing Trim funzionante (no result)
- [ ] Stonecutting funzionante
- [ ] Export JSON valido

---

## Milestone 4: Advanced Features

**Obiettivo:** Features avanzate per power users

### Componenti

| Feature | Priorita | Note |
|---------|----------|------|
| Tag selector | P3 | Popup con tag browser |
| NeoForge ingredients | P3 | Compound, Difference, etc |
| Recipe priorities | P3 | Export recipe_priorities.json |
| Bulk import/export | P3 | Multiple recipes |
| Recipe search | P3 | Filter/search UI |
| DataComponent support | P3 | Item components matching |

### Tag Selector Features

- Browser tag disponibili
- Filtro per namespace
- Preview items nel tag
- Common tags shortcut (#c:ingots, #minecraft:planks)

### NeoForge Ingredients

- CompoundIngredient: match any of
- DifferenceIngredient: A but not B
- IntersectionIngredient: both A and B
- DataComponentIngredient: item + components

### Recipe Priorities

- Slider priorita per ricetta
- Export automatico `recipe_priorities.json`
- Preview conflitti

### Bulk Operations

- Import da JSON file
- Export selettivo
- Backup/restore

### Acceptance Criteria

- [ ] Tag selector mostra tags disponibili
- [ ] Compound ingredient creabile
- [ ] Priorita esportate correttamente
- [ ] Bulk export funzionante
- [ ] Search filtra ricette

---

## Dipendenze

### Interne (Esistenti)

| Dipendenza | Status | Usata In |
|------------|--------|----------|
| AbstractEditorModule | Exists | Base modules |
| DatapackIO | Exists | Export |
| NetworkHandler | Exists | Payload registration |
| CraftingInfoPanel | Exists | Grid reference |
| SlotSelector | Exists | Slot pattern |
| ConfigPaths | Exists | Path management |
| UIConstants | Exists | Styling |
| ScaledCoord | Exists | Responsive UI |

### Esterne

| Dipendenza | Versione | Note |
|------------|----------|------|
| NeoForge | 21.x | Recipe API |
| Minecraft | 1.21.x | Recipe JSON format |

---

## Rischi

| Rischio | Probabilita | Impatto | Mitigazione |
|---------|-------------|---------|-------------|
| Recipe conflicts | Alta | Medio | Validation + warnings |
| Sync race conditions | Media | Alto | ConcurrentHashMap + atomic ops |
| Performance (many recipes) | Bassa | Medio | Lazy loading, pagination |
| Minecraft updates | Media | Alto | Abstract format layer |
| Invalid JSON export | Media | Medio | Strict validation |

---

## Timeline Suggerita

```
Milestone 1: Crafting
├── Week 1: Core data structures
│   ├── RecipeData sealed interface
│   ├── IngredientData, ResultData
│   └── RecipeConfigManager
├── Week 2: UI components
│   ├── RecipeGridComponent
│   ├── Slot components
│   └── CraftingEditorModule
└── Week 3: Network + Polish
    ├── RecipeSyncPayload
    ├── DatapackIO extension
    └── Testing + fixes

Milestone 2: Smelting (1 week)
├── SmeltingRecipeData
├── SmeltingEditorModule
└── Serializer/Codec extensions

Milestone 3: Smithing (1 week)
├── SmithingRecipeData
├── StonecuttingRecipeData
├── Editor modules
└── Testing

Milestone 4: Advanced (2 weeks)
├── Tag selector
├── Recipe priorities
├── Bulk operations
└── Polish + documentation
```

---

## Testing Strategy

### Unit Tests

- RecipeData serialization/deserialization
- RecipeValidator edge cases
- IngredientData parsing

### Integration Tests

- Full crafting flow
- Network sync roundtrip
- Datapack export validation

### Manual Testing

- UI responsiveness
- Drag-drop behavior
- Multiplayer sync
- Server restart persistence

### Validation

- Export JSON contro schema Minecraft
- Import in vanilla Minecraft
- Conflict detection accuracy
