# Formati JSON Ricette Minecraft 1.21

> Specifica completa dei formati JSON per ogni tipo di ricetta

## Note Importanti 1.21

- **`result.id`**: In 1.21 usa `id` invece di `item` per il risultato
- **`result.count`**: Opzionale, default 1
- **Tags**: Prefisso `#` (es. `#c:ingots/iron`)

---

## Crafting Shaped

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "equipment",
  "group": "diamond_tools",
  "show_notification": true,
  "pattern": [
    "###",
    " | ",
    " | "
  ],
  "key": {
    "#": "minecraft:diamond",
    "|": "minecraft:stick"
  },
  "result": {
    "id": "minecraft:diamond_pickaxe",
    "count": 1
  }
}
```

### Campi

| Campo | Tipo | Required | Descrizione |
|-------|------|----------|-------------|
| `type` | string | Si | `"minecraft:crafting_shaped"` |
| `category` | string | No | `"equipment"`, `"building"`, `"misc"`, `"redstone"` |
| `group` | string | No | Raggruppamento nel recipe book |
| `show_notification` | boolean | No | Mostra notifica unlock (default: true) |
| `pattern` | array[string] | Si | 1-3 righe, max 3 caratteri ciascuna |
| `key` | object | Si | Mappa carattere → ingrediente |
| `result` | object | Si | Item risultato |

### Pattern Rules

- Minimo 1 riga, massimo 3
- Ogni riga max 3 caratteri
- Spazio = slot vuoto
- Pattern viene automaticamente "shrunk" (es. ricetta 2x2 in grid 3x3)

### Key Formats

```json
// Item singolo
"#": "minecraft:diamond"

// Tag
"#": "#c:ingots/iron"

// Alternative (OR)
"#": ["minecraft:gold_ingot", "minecraft:copper_ingot"]

// Item con components
"#": {
  "item": "minecraft:enchanted_book",
  "components": { ... }
}
```

---

## Crafting Shapeless

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "group": "dyes",
  "ingredients": [
    "minecraft:red_dye",
    "minecraft:yellow_dye"
  ],
  "result": {
    "id": "minecraft:orange_dye",
    "count": 2
  }
}
```

### Campi

| Campo | Tipo | Required | Descrizione |
|-------|------|----------|-------------|
| `type` | string | Si | `"minecraft:crafting_shapeless"` |
| `category` | string | No | Categoria recipe book |
| `group` | string | No | Raggruppamento |
| `ingredients` | array | Si | 1-9 ingredienti |
| `result` | object | Si | Item risultato |

### Ingredient Formats

```json
// Item singolo
"minecraft:diamond"

// Tag
"#c:ingots/iron"

// Alternative (OR) - qualsiasi dei seguenti
["minecraft:gold_ingot", "minecraft:copper_ingot"]

// Mix di formati
"ingredients": [
  "minecraft:diamond",
  "#minecraft:planks",
  ["minecraft:gold_ingot", "minecraft:iron_ingot"]
]
```

---

## Smelting

```json
{
  "type": "minecraft:smelting",
  "category": "misc",
  "group": "iron_ingot",
  "ingredient": "minecraft:iron_ore",
  "result": {
    "id": "minecraft:iron_ingot"
  },
  "experience": 0.7,
  "cookingtime": 200
}
```

### Varianti

| Type | cookingtime default | Descrizione |
|------|---------------------|-------------|
| `minecraft:smelting` | 200 (10s) | Furnace standard |
| `minecraft:blasting` | 100 (5s) | Blast furnace |
| `minecraft:smoking` | 100 (5s) | Smoker |
| `minecraft:campfire_cooking` | 600 (30s) | Campfire |

### Campi

| Campo | Tipo | Required | Default | Descrizione |
|-------|------|----------|---------|-------------|
| `type` | string | Si | - | Tipo smelting |
| `category` | string | No | `"misc"` | `"food"`, `"blocks"`, `"misc"` |
| `ingredient` | string/object | Si | - | Item/tag input |
| `result` | object | Si | - | Item output |
| `experience` | float | No | 0.0 | XP guadagnata |
| `cookingtime` | int | No | varies | Ticks di cottura |

---

## Smithing Transform

```json
{
  "type": "minecraft:smithing_transform",
  "template": "minecraft:netherite_upgrade_smithing_template",
  "base": "minecraft:diamond_sword",
  "addition": "minecraft:netherite_ingot",
  "result": {
    "id": "minecraft:netherite_sword"
  }
}
```

### Campi

| Campo | Tipo | Required | Descrizione |
|-------|------|----------|-------------|
| `type` | string | Si | `"minecraft:smithing_transform"` |
| `template` | string/object | Si | Smithing template item |
| `base` | string/object | Si | Item da trasformare |
| `addition` | string/object | Si | Materiale aggiuntivo |
| `result` | object | Si | Item risultato |

### Nota Importante

> Il risultato **eredita i componenti** (enchantments, damage, etc.) dall'item base.

---

## Smithing Trim

```json
{
  "type": "minecraft:smithing_trim",
  "template": "minecraft:coast_armor_trim_smithing_template",
  "base": "#minecraft:trimmable_armor",
  "addition": "#minecraft:trim_materials"
}
```

### Differenze da Transform

- **Nessun `result`**: il risultato e' sempre l'armor con trim
- `base` tipicamente e' un tag (`#minecraft:trimmable_armor`)
- `addition` tipicamente e' un tag (`#minecraft:trim_materials`)

---

## Stonecutting

```json
{
  "type": "minecraft:stonecutting",
  "ingredient": "minecraft:stone",
  "result": {
    "id": "minecraft:stone_bricks",
    "count": 1
  }
}
```

### Campi

| Campo | Tipo | Required | Descrizione |
|-------|------|----------|-------------|
| `type` | string | Si | `"minecraft:stonecutting"` |
| `ingredient` | string/object | Si | Item/tag input |
| `result` | object | Si | Item output |

### Nota

Lo stonecutter puo avere multiple ricette per lo stesso ingrediente, tutte mostrate come opzioni.

---

## Result Object

```json
// Minimo
{
  "id": "minecraft:diamond_sword"
}

// Con count
{
  "id": "minecraft:diamond_sword",
  "count": 2
}

// Con components (1.21+)
{
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:enchantments": {
      "levels": {
        "minecraft:sharpness": 5
      }
    }
  }
}
```

### Campi Result

| Campo | Tipo | Required | Default | Descrizione |
|-------|------|----------|---------|-------------|
| `id` | string | Si | - | ResourceLocation item |
| `count` | int | No | 1 | Quantita output |
| `components` | object | No | null | Data components |

---

## Ingredient Formats (Completo)

### Item Singolo
```json
"minecraft:diamond"
```

### Tag
```json
"#minecraft:planks"
"#c:ingots/iron"
"#forge:gems/diamond"
```

### Alternative (Array)
```json
["minecraft:gold_ingot", "minecraft:copper_ingot", "#c:ingots/silver"]
```

### Item con Components
```json
{
  "item": "minecraft:potion",
  "components": {
    "minecraft:potion_contents": {
      "potion": "minecraft:healing"
    }
  }
}
```

### NeoForge Compound (Explicit)
```json
{
  "type": "neoforge:compound",
  "children": [
    "minecraft:diamond",
    "#c:gems/ruby"
  ]
}
```

### NeoForge Difference
```json
{
  "type": "neoforge:difference",
  "base": "#minecraft:planks",
  "subtracted": "minecraft:crimson_planks"
}
```

---

## Validazione

### Regole Comuni

1. **ID valido**: `namespace:path` format
2. **Item esistente**: deve essere registrato
3. **Tag esistente**: deve avere almeno 1 item
4. **Pattern valido**: no righe vuote nel mezzo
5. **Almeno 1 ingrediente**: ricetta non vuota

### Limiti

| Tipo | Limite |
|------|--------|
| Pattern righe | 1-3 |
| Pattern colonne | 1-3 |
| Shapeless ingredients | 1-9 |
| Result count | 1-64 |
| Cookingtime | 1-72000 (1h) |
| Experience | 0.0-10.0 |
