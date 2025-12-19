# DevMod Test World Setup

## Overview

Questo documento descrive come creare e configurare un mondo di test per la validazione manuale di DevMod.

---

## Quick Start

### Opzione 1: Nuovo Mondo via Commands

1. Avvia Minecraft con DevMod
2. Crea nuovo mondo: `Singleplayer > Create New World`
3. Impostazioni:
   - Nome: `DevMod_Test`
   - Game Mode: `Creative`
   - Difficulty: `Normal`
   - Allow Cheats: `ON`
4. Dopo spawn, esegui:

```
/gamemode survival
/give @p diamond_sword{devmod:{}} 1
/give @p iron_sword{devmod:{}} 1
/give @p bow{devmod:{}} 1
```

### Opzione 2: Server Dedicato

1. Avvia server: `./gradlew runServer`
2. In console:
```
op <your_username>
gamemode creative <your_username>
```
3. Connetti client e prepara items

---

## Test Scenarios Setup

### Scenario A: Endurance Quest Testing

**Prerequisiti:**
- Player in overworld
- Almeno 1 mob type configurato
- Inventory non vuoto (per test restore)

**Setup Commands:**
```
# Posizionati in area sicura
/tp @p 0 100 0

# Prepara inventory con items distintivi
/give @p diamond 64
/give @p emerald 32
/give @p netherite_sword 1
/give @p golden_apple 10

# Applica effetti (per test restore)
/effect give @p speed 9999 1
/effect give @p strength 9999 1

# Imposta XP (per test restore)
/xp add @p 1000 points

# Salva posizione per verifica return
/execute as @p run say Starting pos: ~ ~ ~
```

### Scenario B: Recovery Testing

**Setup per test disconnect:**
```
# Stesso setup di Scenario A
# Prima di disconnect, nota:
# - Posizione esatta
# - Inventory contents
# - Effetti attivi
# - Livello XP
```

### Scenario C: Multiplayer Testing

**Server Setup:**
```
# In server.properties
max-players=4
online-mode=false  # Solo per test locale!
```

**Client Setup (per ogni player):**
```
# Player 1: Leader
/give @p diamond_helmet 1

# Player 2-4: Members
/give @p iron_helmet 1
```

---

## Test Locations

### Overworld Test Area

```
Coordinate: 0, 100, 0
Descrizione: Piattaforma elevata, sicura da mob
Setup: /fill -10 99 -10 10 99 10 stone
```

### Nether Test Area

```
Coordinate: 0, 70, 0
Descrizione: Piattaforma nel Nether per test cross-dimension
Setup: /fill -5 69 -5 5 69 5 netherrack
```

### End Test Area

```
Coordinate: 0, 60, 0
Descrizione: Piattaforma nell'End
Setup: /fill -5 59 -5 5 59 5 end_stone
```

---

## Verifiche Post-Quest

### Checklist Inventory

| Item | Pre-Quest | Post-Quest | Match? |
|------|-----------|------------|--------|
| Diamond Sword | [ ] | [ ] | [ ] |
| Diamonds (64) | [ ] | [ ] | [ ] |
| Emeralds (32) | [ ] | [ ] | [ ] |
| Golden Apples | [ ] | [ ] | [ ] |

### Checklist Stats

| Stat | Pre-Quest | Post-Quest | Match? |
|------|-----------|------------|--------|
| Health | [ ] | [ ] | [ ] |
| Food | [ ] | [ ] | [ ] |
| XP Level | [ ] | [ ] | [ ] |
| Position | [ ] | [ ] | [ ] |
| Dimension | [ ] | [ ] | [ ] |

### Checklist Effects

| Effect | Pre-Quest | Post-Quest | Match? |
|--------|-----------|------------|--------|
| Speed | [ ] | [ ] | [ ] |
| Strength | [ ] | [ ] | [ ] |
| Other | [ ] | [ ] | [ ] |

---

## Debug Commands

### DevMod Status

```
# Verifica stato instance (implementare se non esiste)
/devmod instance list
/devmod instance info <uuid>
/devmod snapshot list
/devmod recovery status
```

### Minecraft Debug

```
# Mostra info debug
F3 (debug screen)

# Chunk info
/forceload query

# Entity count
/kill @e[type=!player,distance=..100]
# (ATTENZIONE: uccide entities!)
```

---

## Cleanup Commands

### Dopo Test Session

```
# Rimuovi effetti
/effect clear @p

# Reset XP
/xp set @p 0 levels
/xp set @p 0 points

# Pulisci inventory
/clear @p

# Torna a creative
/gamemode creative
```

### Server Cleanup

```
# Stop server pulito
/stop

# Oppure in console:
stop
```

---

## Automated Test World (Future)

### Datapack per Test World

```json
// data/devmod_test/worldgen/world_preset/test_world.json
{
  "dimensions": {
    "minecraft:overworld": {
      "type": "minecraft:overworld",
      "generator": {
        "type": "minecraft:flat",
        "settings": {
          "layers": [
            {"block": "minecraft:bedrock", "height": 1},
            {"block": "minecraft:stone", "height": 63},
            {"block": "minecraft:grass_block", "height": 1}
          ],
          "biome": "minecraft:plains"
        }
      }
    }
  }
}
```

### Function per Setup

```mcfunction
# data/devmod_test/functions/setup.mcfunction
gamemode survival @a
tp @a 0 65 0
give @a diamond_sword 1
give @a diamond 64
effect give @a speed 9999 1
xp add @a 100 levels
say Test world ready!
```

---

## Troubleshooting

### Mondo non carica

1. Verifica log per errori
2. Elimina `world/` e ricrea
3. Controlla `server.properties`

### Commands non funzionano

1. Verifica cheats abilitati
2. Verifica op status (multiplayer)
3. Verifica permessi

### Mod non carica

1. Verifica `build/libs/DevMod.jar` esiste
2. Controlla log per errori mixin
3. Verifica compatibilità versione
