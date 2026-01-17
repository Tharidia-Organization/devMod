# Arena Template Schema

This document describes the JSON schema for arena templates used in DevMod's arena system.

## Overview

Arena templates define the structure, spawn points, and configuration for combat arenas. Templates are validated at load time and again at build time.

## Basic Structure

```json
{
  "id": "my_arena",
  "version": 1,
  "schemaVersion": 1,
  "size": 64,
  "floor": { ... },
  "walls": { ... },
  "ceiling": { ... },
  "spawnSlots": [ ... ],
  "tags": ["boss", "outdoor"]
}
```

## Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique template ID (lowercase alphanumeric + underscore, max 32 chars) |
| `version` | int | Template version (>=1) |
| `schemaVersion` | int | Schema version for compatibility (currently: 1) |
| `floor` | Floor | Floor configuration (required) |

## Size Constraints

| Field | Min | Max | Description |
|-------|-----|-----|-------------|
| `size` | 8 | 256 | Default arena size (used if sizeX/sizeZ not specified) |
| `sizeX` | 8 | 256 | Arena width (optional, overrides size) |
| `sizeZ` | 8 | 256 | Arena depth (optional, overrides size) |

## Arena Shapes

| Shape | Description |
|-------|-------------|
| `RECTANGULAR` | Standard square/rectangular arena (default) |
| `CIRCULAR` | Circular arena (radius = max(sizeX, sizeZ) / 2) |
| `RING` | Donut shape with inner and outer radius |

For `RING` shape, set `ringInnerRadius` to define the hollow center.

## Floor Configuration

```json
{
  "floor": {
    "y": 64,
    "thickness": 1,
    "material": "minecraft:stone_bricks",
    "pattern": "solid",
    "borderMaterial": "minecraft:polished_andesite",
    "borderWidth": 2
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `y` | int | Y-level of the floor surface |
| `thickness` | int | Floor thickness in blocks |
| `material` | string | Main floor block ID |
| `pattern` | string | Floor pattern (solid, checkered, etc.) |
| `borderMaterial` | string | Optional border block ID |
| `borderWidth` | int | Border width in blocks |

## Walls Configuration

```json
{
  "walls": {
    "enabled": true,
    "material": "minecraft:barrier",
    "height": 10,
    "thickness": 1,
    "startY": 64,
    "style": "solid"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | boolean | Whether walls are generated |
| `material` | string | Wall block ID |
| `height` | int | Wall height in blocks |
| `thickness` | int | Wall thickness |
| `startY` | int | Y-level where walls start |
| `style` | string | Wall style: solid, fence, glass, barrier |

## Ceiling Configuration

```json
{
  "ceiling": {
    "enabled": true,
    "material": "minecraft:barrier",
    "y": 74,
    "thickness": 1
  }
}
```

## Underfloor Configuration

```json
{
  "underfloor": {
    "material": "minecraft:bedrock",
    "depth": 3,
    "sameAsFloor": false
  }
}
```

## Spawn Slots

Spawn slots define where entities can spawn:

```json
{
  "spawnSlots": [
    {
      "offset": [0, 1, 0],
      "yMode": "RELATIVE_TO_FLOOR",
      "tags": ["center", "player"],
      "validation": {
        "required": true,
        "width": 2,
        "height": 2
      }
    },
    {
      "offset": [10, 1, 0],
      "yMode": "RELATIVE_TO_FLOOR",
      "tags": ["melee", "mob"],
      "validation": {
        "required": true,
        "width": 2,
        "height": 1
      }
    }
  ]
}
```

### Y-Mode Options

| Mode | Description |
|------|-------------|
| `RELATIVE_TO_FLOOR` | Y offset from floor surface |
| `ABSOLUTE` | Absolute Y coordinate |
| `RELATIVE_TO_ORIGIN` | Y offset from arena origin |

### Spawn Tags

Common tags for spawn slot categorization:

| Tag | Use Case |
|-----|----------|
| `player` | Player spawn point |
| `mob` | General mob spawn |
| `melee` | Close-range mob spawn |
| `ranged` | Ranged mob spawn |
| `boss` | Boss spawn point |
| `center` | Arena center |
| `corner` | Arena corners |
| `flank` | Side positions |

## Lighting

```json
{
  "lighting": {
    "ambientLight": 15,
    "skyLight": 10,
    "placeTorches": true,
    "lightSources": [
      { "x": 0, "y": 5, "z": 0, "block": "minecraft:glowstone" }
    ]
  }
}
```

## Hazards

```json
{
  "hazards": [
    {
      "type": "lava_pool",
      "position": [5, 0, 5],
      "radius": 3
    },
    {
      "type": "custom",
      "builderId": "my_custom_hazard",
      "params": { ... }
    }
  ]
}
```

## Forbidden Zones

Areas where mobs cannot spawn:

```json
{
  "forbiddenZones": [
    {
      "minX": -5, "minZ": -5,
      "maxX": 5, "maxZ": 5,
      "reason": "Player spawn protection"
    }
  ]
}
```

Maximum: 20 forbidden zones per template.

## Build Settings

```json
{
  "buildSettings": {
    "priority": "SYNC",
    "order": "FLOOR_FIRST"
  }
}
```

| Priority | Description |
|----------|-------------|
| `SYNC` | Build synchronously (blocks until complete) |
| `ASYNC` | Build asynchronously (non-blocking) |

| Order | Description |
|-------|-------------|
| `FLOOR_FIRST` | Build floor, then walls, then ceiling |
| `WALLS_FIRST` | Build walls first, then floor |
| `STRUCTURE_FIRST` | Place NBT structure first |

## Instance Settings

```json
{
  "instanceSettings": {
    "chunkRadius": 5,
    "tickDistance": 4,
    "isolateWeather": true
  }
}
```

## Tags

Tags for categorization and filtering:

```json
{
  "tags": ["boss", "outdoor", "pvp", "hard"]
}
```

Common tags:
- `boss` - Boss encounter arena
- `outdoor` / `indoor` - Environment type
- `pvp` - PvP arena
- `pve` - PvE arena
- `easy` / `medium` / `hard` - Difficulty

## Block Budgets

| Category | Max Blocks | Max Build Time |
|----------|-----------|----------------|
| Default | 50,000 | 5 seconds |
| Boss | 100,000 | 15 seconds |
| Hard Cap | 150,000 | - |

## Validation Modes

| Mode | Description |
|------|-------------|
| `STRICT` | All errors fail validation (production default) |
| `PERMISSIVE` | Bounds errors become warnings |
| `LENIENT` | All errors become warnings (debug only) |

Set via environment variable: `DEVMOD_TEMPLATE_VALIDATION_MODE`

## Complete Example

```json
{
  "id": "boss_ring_80",
  "version": 1,
  "schemaVersion": 1,
  "size": 80,
  "arenaShape": "RING",
  "ringInnerRadius": 20,

  "floor": {
    "y": 64,
    "thickness": 1,
    "material": "minecraft:deepslate_bricks",
    "pattern": "solid",
    "borderMaterial": "minecraft:deepslate_tiles",
    "borderWidth": 3
  },

  "walls": {
    "enabled": true,
    "material": "minecraft:deepslate_bricks",
    "height": 12,
    "thickness": 2,
    "startY": 64,
    "style": "solid"
  },

  "ceiling": {
    "enabled": true,
    "material": "minecraft:barrier",
    "y": 76,
    "thickness": 1
  },

  "underfloor": {
    "material": "minecraft:bedrock",
    "depth": 3,
    "sameAsFloor": false
  },

  "lighting": {
    "ambientLight": 12,
    "skyLight": 0,
    "placeTorches": false,
    "lightSources": []
  },

  "spawnSlots": [
    {
      "offset": [0, 1, 30],
      "yMode": "RELATIVE_TO_FLOOR",
      "tags": ["center", "player"],
      "validation": { "required": true, "width": 2, "height": 2 }
    },
    {
      "offset": [0, 1, -30],
      "yMode": "RELATIVE_TO_FLOOR",
      "tags": ["boss"],
      "validation": { "required": true, "width": 4, "height": 4 }
    },
    {
      "offset": [25, 1, 0],
      "yMode": "RELATIVE_TO_FLOOR",
      "tags": ["mob", "add"],
      "validation": { "required": false, "width": 2, "height": 2 }
    },
    {
      "offset": [-25, 1, 0],
      "yMode": "RELATIVE_TO_FLOOR",
      "tags": ["mob", "add"],
      "validation": { "required": false, "width": 2, "height": 2 }
    }
  ],

  "hazards": [
    {
      "type": "lava_pool",
      "position": [0, 0, 0],
      "radius": 15
    }
  ],

  "forbiddenZones": [
    {
      "minX": -5, "minZ": 25,
      "maxX": 5, "maxZ": 35,
      "reason": "Player spawn protection"
    }
  ],

  "buildSettings": {
    "priority": "SYNC",
    "order": "FLOOR_FIRST"
  },

  "instanceSettings": {
    "chunkRadius": 6,
    "tickDistance": 4,
    "isolateWeather": true
  },

  "tags": ["boss", "ring", "lava", "hard"]
}
```

## Template Inheritance

Templates can extend other templates:

```json
{
  "id": "boss_ring_100",
  "extendsTemplate": "boss_ring_80",
  "version": 1,
  "size": 100,
  "ringInnerRadius": 25
}
```

Inherited fields can be overridden selectively.

## File Location

Templates are loaded from:
- `config/devmod/arena_templates/` - Custom templates
- Mod resources - Built-in templates

## Troubleshooting

### Validation Errors

| Error | Solution |
|-------|----------|
| "Template ID must be lowercase alphanumeric" | Use only a-z, 0-9, underscore |
| "size must be >= 8 and <= 256" | Adjust arena size |
| "floor is required" | Add floor configuration |
| "Spawn slot outside bounds" | Check spawn slot positions |

### Build Errors

| Error | Solution |
|-------|----------|
| "Block budget exceeded" | Reduce arena size or simplify design |
| "Chunk loading failed" | Check server chunk loading limits |
| "Build timeout" | Use ASYNC priority for large arenas |
