# DEVMOD - AUDIT DI RIFERIMENTO COMPLETO
## Documento di Tracciamento Stato Mod - Versione 0.1.0

**Data Generazione:** 2026-01-11
**Branch Corrente:** Banastaff
**Main Branch:** main
**Totale Classi Java:** 1382

---

# INDICE

1. [Informazioni Generali](#1-informazioni-generali)
2. [Versioni e Specifiche Tecniche](#2-versioni-e-specifiche-tecniche)
3. [Struttura Directory](#3-struttura-directory)
4. [Architettura Moduli](#4-architettura-moduli)
5. [Registry Blocchi](#5-registry-blocchi)
6. [Registry Items](#6-registry-items)
7. [Registry Entities](#7-registry-entities)
8. [Registry Block Entities](#8-registry-block-entities)
9. [Registry Menu e Screen](#9-registry-menu-e-screen)
10. [Network Packets](#10-network-packets)
11. [Comandi Registrati](#11-comandi-registrati)
12. [Attributi Custom](#12-attributi-custom)
13. [Data Components](#13-data-components)
14. [Creative Tabs](#14-creative-tabs)
15. [Risorse e Assets](#15-risorse-e-assets)
16. [Configurazione Build](#16-configurazione-build)
17. [Dipendenze](#17-dipendenze)
18. [Note Architetturali](#18-note-architetturali)
19. [Changelog Strutturale](#19-changelog-strutturale)

---

# 1. INFORMAZIONI GENERALI

| Proprietà | Valore |
|-----------|--------|
| **Mod ID** | `devmod` |
| **Nome Mod** | `mob config viewer` |
| **Versione** | `0.1.0` |
| **Package Base** | `com.devmod` |
| **Autori** | Frenk012, Vassago |
| **Licenza** | All Rights Reserved |
| **Descrizione** | Mod to render mob info and implement quick config views and editor for weapons |

---

# 2. VERSIONI E SPECIFICHE TECNICHE

## 2.1 Versioni Core

| Componente | Versione |
|------------|----------|
| **Minecraft** | 1.21.1 |
| **NeoForge** | 21.1.216 |
| **Java** | 21 |
| **Parchment Mappings** | 2024.11.17 |
| **Loader Version Range** | [1,) |
| **Minecraft Version Range** | [1.21.1] |

## 2.2 Plugin Build

| Plugin | Versione |
|--------|----------|
| NeoForge ModDev | 2.0.120 |
| Error Prone | 4.1.0 |
| SpotBugs | 6.0.7 |
| JaCoCo | 0.8.11 |
| Checkstyle | 10.21.1 |

---

# 3. STRUTTURA DIRECTORY

```
/Users/erik/Desktop/DevMod/devMod/
├── src/
│   ├── main/
│   │   ├── java/com/devmod/           [1382 classi Java]
│   │   │   ├── abilities/             [6 classi]
│   │   │   ├── actions/               [17 classi]
│   │   │   ├── ammo/                  [1 classe]
│   │   │   ├── arena/                 [154 classi]
│   │   │   ├── attributes/            [3 classi]
│   │   │   ├── blocks/                [3 classi]
│   │   │   ├── bridge/                [1 classe]
│   │   │   ├── client/                [452 classi]
│   │   │   ├── clone/                 [~50 classi]
│   │   │   ├── combat/                [15 classi]
│   │   │   ├── compat/                [44 classi]
│   │   │   ├── components/            [6 classi]
│   │   │   ├── config/                [32 classi]
│   │   │   ├── core/                  [2 classi]
│   │   │   ├── damage/                [2 classi]
│   │   │   ├── debug/                 [22 classi]
│   │   │   ├── effects/               [1 classe]
│   │   │   ├── endurance/             [120 classi]
│   │   │   ├── entity/                [3 classi]
│   │   │   ├── events/                [7 classi]
│   │   │   ├── exception/             [1 classe]
│   │   │   ├── gametest/              [5 classi]
│   │   │   ├── hologram/              [16 classi]
│   │   │   ├── integration/           [6 classi]
│   │   │   ├── mailbox/               [93 classi]
│   │   │   ├── mixin/                 [18 classi]
│   │   │   ├── mob/                   [10 classi]
│   │   │   ├── network/               [40 classi]
│   │   │   ├── nexus/                 [1 classe]
│   │   │   ├── notification/          [15 classi]
│   │   │   ├── party/                 [12 classi]
│   │   │   ├── portal/                [23 classi]
│   │   │   ├── quest/                 [3 classi]
│   │   │   ├── recipe/                [18 classi]
│   │   │   ├── rendering/             [2 classi]
│   │   │   ├── runtime/               [49 classi]
│   │   │   ├── security/              [1 classe]
│   │   │   ├── shared/                [1 classe]
│   │   │   ├── stats/                 [7 classi]
│   │   │   ├── tags/                  [1 classe]
│   │   │   ├── telemetry/             [71 classi]
│   │   │   ├── testing/               [21 classi]
│   │   │   ├── util/                  [16 classi]
│   │   │   ├── DevMod.java            [Main mod class]
│   │   │   └── ModConfig.java         [Configuration]
│   │   ├── resources/
│   │   │   ├── assets/devmod/
│   │   │   │   ├── blockstates/       [163 file JSON]
│   │   │   │   ├── models/block/      [333 file JSON]
│   │   │   │   ├── models/item/       [179 file JSON]
│   │   │   │   ├── textures/          [264 file PNG]
│   │   │   │   ├── lang/              [2 file JSON]
│   │   │   │   └── shaders/           [22 file GLSL]
│   │   │   ├── data/devmod/
│   │   │   │   ├── recipe/            [29 file JSON]
│   │   │   │   ├── loot_table/        [6 file JSON]
│   │   │   │   ├── tags/item/         [6 file JSON]
│   │   │   │   ├── structures/        [4 file NBT]
│   │   │   │   └── presets/           [6 file JSON]
│   │   │   ├── config/devmod/         [Configurazioni runtime]
│   │   │   ├── schemas/               [2 file JSON Schema]
│   │   │   ├── db/                    [Schema DuckDB]
│   │   │   ├── dashboard/             [Web UI resources]
│   │   │   └── devmod.mixins.json
│   │   └── templates/
│   │       └── META-INF/neoforge.mods.toml
│   └── test/
│       ├── java/                      [Test JUnit 5]
│       └── resources/
├── admin-panel/                       [Vue.js/Vite frontend]
├── config/                            [Dev configurations]
├── docs/                              [Documentazione]
├── run/                               [Runtime environment]
├── build.gradle                       [873 righe]
└── gradle.properties
```

---

# 4. ARCHITETTURA MODULI

## 4.1 Moduli Principali (per numero classi)

| Modulo | Classi | Descrizione |
|--------|--------|-------------|
| **client** | 452 | Sistema UI, rendering, input, state management |
| **arena** | 154 | Creazione e gestione arena sandbox |
| **endurance** | 120 | Sistema sfide, quest, party, nutrizione |
| **mailbox** | 93 | Messaggistica, broadcast, REST API |
| **telemetry** | 71 | Analytics con DuckDB, heatmap, export |
| **clone** | ~50 | Sistema clonazione entità |
| **runtime** | 49 | Dimensioni custom, hologram, portali |
| **compat** | 44 | Integrazione mod esterni |
| **network** | 40 | Packets e sincronizzazione |
| **config** | 32 | Gestione configurazione |
| **portal** | 23 | Sistema portali interdimensionali |
| **debug** | 22 | Strumenti debugging |
| **testing** | 21 | Test harness e validation |
| **mixin** | 18 | Bytecode injection |
| **actions** | 17 | Sistema azioni e keybind |
| **hologram** | 16 | Proiettore ologrammi 3D |
| **util** | 16 | Utility varie |
| **combat** | 15 | Sistema combattimento |
| **notification** | 15 | Sistema notifiche |
| **party** | 12 | Gestione party |
| **mob** | 10 | Configurazione mob |
| **recipe** | 18 | Ricette custom |

## 4.2 Dettaglio Sottosistema Arena

```
arena/
├── alert/              [11 classi] - Sistema alert (Discord, DuckDB, Console)
├── autosmoke/          [8 classi]  - Testing automatico arena
├── builder/            [11 classi] - Costruzione asincrona arena
├── budget/             [3 classi]  - Budget performance (MSPT)
├── cleanup/            [6 classi]  - Pulizia post-costruzione
├── command/            [3 classi]  - Comandi admin
├── concurrency/        [3 classi]  - Rate limiting e lock
├── config/             [4 classi]  - Configurazione template
├── fallback/           [2 classi]  - Circuit breaker
├── logging/            [3 classi]  - Aggregazione log
├── metrics/            [2 classi]  - Metriche performance
├── monitor/            [2 classi]  - Monitor MSPT
├── override/           [7 classi]  - Override template capability
├── policy/             [11 classi] - Sistema policy e mutatori
├── registry/           [26 classi] - Registry e validazione template
├── security/           [3 classi]  - Permissioni e audit
├── serialization/      [1 classe]  - Serializzazione template
├── spawn/              [5 classi]  - Gestione spawn slot
├── zone/               [8 classi]  - Layout e transizioni zone
└── [altri sottosistemi]
```

## 4.3 Dettaglio Sottosistema Client

```
client/
├── abilities/          - UI stamina e abilità
├── arena/              - HUD progress build arena
├── attributes/         - Overlay attributi
├── collision/          - Debug collision rendering
├── combat/             - Weapon trail, tooltip
├── compat/             - Compatibilità client-side
├── config/             - Cache configurazione
├── debug/              - Debug rendering
├── effects/            - Shake, trail effects
├── endurance/          - Quest UI, cache
├── entity/             - Entity rendering custom
├── environment/        - Environment setup
├── events/             - Event listener client
├── gametest/           - GameTest client
├── input/              - Input handling
├── network/            - Network handler client
├── nexus/              - Nexus integration
├── notification/       - Sistema notifiche HUD
├── overlay/            - Overlay rendering
├── panels/             - Sistema pannelli UI
├── party/              - Party UI
├── quest/              - Quest UI
├── rendering/          - Sistema rendering (shader, shield)
├── season/             - Season system
├── state/              - State management
├── telemetry/          - Telemetry client
├── testing/            - Testing UI
└── ui/                 - Core UI system
    ├── animation/      - UI animation
    ├── components/     - UI components
    ├── editor/         - Editor avanzato
    ├── hub/            - Hub UI
    ├── overlay/        - Overlay UI
    ├── radial/         - Radial menu system
    ├── screens/        - Custom screens
    ├── scroll/         - Scroll handling
    ├── search/         - Search UI
    └── testing/        - Testing UI
```

---

# 5. REGISTRY BLOCCHI

## 5.1 Blocchi Totali: 160

### 5.1.1 Portal Module (6 blocchi)

| Nome Registrazione | Classe | Proprietà |
|--------------------|--------|-----------|
| `custom_portal` | CustomPortalBlock | No collision, unbreakable, light 11, 16 colori |
| `rune_haste` | RuneBlock | Strength 1.5F, light 7, teletrasporto istantaneo |
| `rune_gate` | RuneBlock | Strength 1.5F, light 7, cross-dimension link |
| `rune_enhancer` | RuneBlock | Strength 1.5F, light 7, +100 blocchi range |
| `rune_strong_enhancer` | RuneBlock | Strength 1.5F, light 7, +500 blocchi range |
| `rune_infinity` | RuneBlock | Strength 1.5F, light 7, range illimitato |

**File:** `com.devmod.portal.PortalBlocks`

### 5.1.2 Nexus Decorative Module (144 blocchi)

**File:** `com.devmod.nexus.NexusDecorBlocks`

**Proprietà Base:** Strength 1.5F, Sound METAL, Require correct tool

#### Categoria Base (12 blocchi + 12 slab = 24)

| Blocco | Slab | Light | Colore Hex |
|--------|------|-------|------------|
| nexus_panel | nexus_panel_slab | No | #8899AA |
| nexus_tile | nexus_tile_slab | No | #6B8E8E |
| nexus_grid | nexus_grid_slab | No | #A0A8B0 |
| nexus_plating | nexus_plating_slab | No | #505860 |
| nexus_core | nexus_core_slab | No | #8B7B8B |
| nexus_frame | nexus_frame_slab | No | #9A9590 |
| nexus_conduit | nexus_conduit_slab | No | #7BA3A3 |
| nexus_terminal | nexus_terminal_slab | No | #9090A0 |
| nexus_vent | nexus_vent_slab | No | #606060 |
| nexus_circuit | nexus_circuit_slab | No | #5A8080 |
| nexus_smooth | nexus_smooth_slab | No | #808080 |
| nexus_light | nexus_light_slab | **12** | #D0D0D8 |

#### Categoria Bold (24 blocchi + 24 slab = 48)

| Blocco | Light | Blocco | Light |
|--------|-------|--------|-------|
| nexus_azure | Yes | nexus_plasma | Yes |
| nexus_signal | Yes | nexus_matrix | Yes |
| nexus_energy | Yes | nexus_data | No |
| nexus_crystal | Yes | nexus_hologram | Yes |
| nexus_reactor | Yes | nexus_pulse | Yes |
| nexus_grid2 | No | nexus_tech | No |
| nexus_stripes | No | nexus_display | Yes |
| nexus_neon | Yes | nexus_carbon | No |
| nexus_quantum | Yes | nexus_binary | Yes |
| nexus_steel | No | nexus_void | No |
| nexus_glow | Yes | nexus_hazard | No |
| nexus_iris | Yes | nexus_ember | Yes |

#### Categoria Nuova (36 blocchi + 36 slab = 72)

| Blocco | Light | Blocco | Light |
|--------|-------|--------|-------|
| nexus_cobalt | No | nexus_mint | No |
| nexus_rose | No | nexus_onyx | No |
| nexus_copper | No | nexus_gold | No |
| nexus_silver | No | nexus_bronze | No |
| nexus_hex | No | nexus_dots | Yes |
| nexus_wave | No | nexus_checker | No |
| nexus_diamond | No | nexus_cross | Yes |
| nexus_spiral | No | nexus_brick | No |
| nexus_scales | No | nexus_mesh | No |
| nexus_azure_light | Yes | nexus_azure_dark | No |
| nexus_plasma_light | Yes | nexus_plasma_dark | No |
| nexus_matrix_light | Yes | nexus_energy_light | Yes |
| nexus_reactor_light | Yes | nexus_crystal_light | Yes |
| nexus_neon_blue | Yes | nexus_circuit_gold | Yes |
| nexus_panel_dark | No | nexus_grid_gold | Yes |
| nexus_vent_red | No | nexus_terminal_green | Yes |
| nexus_display_red | Yes | nexus_conduit_yellow | No |
| nexus_frame_white | Yes | nexus_tile_purple | No |

### 5.1.3 Clone Module (7 blocchi)

| Nome Registrazione | Classe | Proprietà |
|--------------------|--------|-----------|
| `telepad` | TelepadBlock | Strength 2.0F, light 5, teleportazione |
| `imprinter` | ImprinterBlock | Scanner automatico entità |
| `neurocell` | NeurocellBlock | Camera clonazione 1×2 |
| `neurocell_l` | NeurocellLBlock | Camera clonazione 2×2×2 |
| `neurolink` | NeurolinkBlock | Cavi connessione |
| `reformer` | ReformerBlock | Spawner entità clonate |
| `centrifuge` | CentrifugeBlock | Crafting automatico |

**File:** `com.devmod.clone.CloneBlocks`

### 5.1.4 Hologram Module (1 blocco)

| Nome Registrazione | Classe | Proprietà |
|--------------------|--------|-----------|
| `hologram_projector` | HologramProjectorBlock | Strength 2.0F, light 7, proiezione 3D |

**File:** `com.devmod.hologram.HologramBlocks`

### 5.1.5 Core Module (1 blocco)

| Nome Registrazione | Classe | Proprietà |
|--------------------|--------|-----------|
| `nexus_portal` | NexusPortalBlock | Unbreakable, light 11, 16 varianti colore |

**File:** `com.devmod.blocks.ModBlocks`

### 5.1.6 Debug Module (1 blocco)

| Nome Registrazione | Classe | Proprietà |
|--------------------|--------|-----------|
| `entity_scanner` | EntityScannerBlock | Strength 2.0F, light 5, scanner debug |

**File:** `com.devmod.debug.block.DebugBlocks`

---

# 6. REGISTRY ITEMS

## 6.1 Items Totali: 176

### 6.1.1 Portal Items (22)

| Nome Registrazione | Classe | Proprietà |
|--------------------|--------|-----------|
| `portal_igniter_white` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_orange` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_magenta` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_light_blue` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_yellow` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_lime` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_pink` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_gray` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_light_gray` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_cyan` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_purple` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_blue` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_brown` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_green` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_red` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_igniter_black` | PortalIgniterItem | Durability 64, stack 1 |
| `portal_linker` | PortalLinkerItem | Stack 1 |
| `rune_haste` | BlockItem | Stack 64 |
| `rune_gate` | BlockItem | Stack 64 |
| `rune_enhancer` | BlockItem | Stack 64 |
| `rune_strong_enhancer` | BlockItem | Stack 64 |
| `rune_infinity` | BlockItem | Stack 64 |

**File:** `com.devmod.portal.PortalItems`

### 6.1.2 Nexus Block Items (144)

Ogni blocco Nexus (72) + ogni slab Nexus (72) = 144 BlockItem

**File:** `com.devmod.nexus.NexusDecorBlocks`

### 6.1.3 Clone Items (8)

| Nome Registrazione | Classe |
|--------------------|--------|
| `telepad` | BlockItem |
| `bioscanner` | BioscannerItem |
| `imprinter` | BlockItem |
| `neurocell` | BlockItem |
| `neurolink` | BlockItem |
| `reformer` | BlockItem |
| `neurocell_l` | BlockItem |
| `centrifuge` | BlockItem |

**File:** `com.devmod.clone.CloneItems`

### 6.1.4 Altri Items (2)

| Nome Registrazione | Classe | File |
|--------------------|--------|------|
| `hologram_projector` | BlockItem | HologramItems |
| `viewer_item` | Item | DevMod |

---

# 7. REGISTRY ENTITIES

## 7.1 Entities Totali: 2

### 7.1.1 NexaEntity

| Proprietà | Valore |
|-----------|--------|
| **Nome Registrazione** | `nexa` |
| **Classe** | `com.devmod.entity.NexaEntity` |
| **Tipo** | PathfinderMob |
| **Categoria** | MobCategory.MISC |
| **Dimensioni** | 0.6W × 1.8H |
| **Tracking Range** | 10 |
| **Update Interval** | 2 tick |
| **Proprietà** | Fire Immune, No Gravity, Silent, Invulnerable |

**Renderer:** `NexaEntityRenderer` (HumanoidModel)

### 7.1.2 PlayerCloneEntity

| Proprietà | Valore |
|-----------|--------|
| **Nome Registrazione** | `player_clone` |
| **Classe** | `com.devmod.clone.entity.PlayerCloneEntity` |
| **Tipo** | TamableAnimal |
| **Categoria** | MobCategory.CREATURE |
| **Dimensioni** | 0.6W × 1.8H |
| **Tracking Range** | 10 |
| **Update Interval** | 2 tick |

**Behavior Modes:**
- MODE_FOLLOW (0): Segue proprietario
- MODE_GUARD (1): Difende posizione
- MODE_ATTACK (2): Attacca nemici

**Renderer:** `PlayerCloneEntityRenderer` (HumanoidModel)

**File:** `com.devmod.entity.ModEntities`

---

# 8. REGISTRY BLOCK ENTITIES

## 8.1 Block Entities Totali: 9

### 8.1.1 Clone Module (6)

| Nome | Classe | Block | Renderer |
|------|--------|-------|----------|
| `neurocell` | NeurocellBlockEntity | NeurocellBlock | NeurocellRenderer |
| `neurocell_l` | NeurocellLBlockEntity | NeurocellLBlock | NeurocellLRenderer |
| `imprinter` | ImprinterBlockEntity | ImprinterBlock | - |
| `reformer` | ReformerBlockEntity | ReformerBlock | - |
| `centrifuge` | CentrifugeBlockEntity | CentrifugeBlock | - |
| `telepad` | TelepadBlockEntity | TelepadBlock | - |

**File:** `com.devmod.clone.CloneBlockEntities`

### 8.1.2 Hologram Module (1)

| Nome | Classe | Block | Renderer |
|------|--------|-------|----------|
| `hologram_projector` | HologramProjectorBlockEntity | HologramProjectorBlock | HologramRenderer |

**File:** `com.devmod.hologram.HologramBlockEntities`

### 8.1.3 Debug Module (1)

| Nome | Classe | Block | Renderer |
|------|--------|-------|----------|
| `entity_scanner` | EntityScannerBlockEntity | EntityScannerBlock | - |

**File:** `com.devmod.debug.block.DebugBlockEntities`

---

# 9. REGISTRY MENU E SCREEN

## 9.1 Menu Totali: 3

| Menu | Classe | Screen | Slots |
|------|--------|--------|-------|
| `neurocell` | NeurocellMenu | NeurocellScreen | 1 input + player inv |
| `neurocell_l` | NeurocellLMenu | NeurocellLScreen | 1 input + player inv |
| `centrifuge` | CentrifugeMenu | CentrifugeScreen | 3 input + 1 output + player inv |

**File:** `com.devmod.clone.CloneMenus`

## 9.2 Screen Non-Menu (Config Screens)

| Screen | Block Entity | Componenti |
|--------|--------------|------------|
| TelepadConfigScreen | TelepadBlockEntity | EditBox nome + Apply/Close |
| HologramConfigScreen | HologramProjectorBlockEntity | Slider size, rotation toggle |
| EntityScannerScreen | EntityScannerBlockEntity | List + Detail view |

---

# 10. NETWORK PACKETS

## 10.1 Canali Totali: 171

**File:** `com.devmod.network.NetworkHandler`

### 10.1.1 MOB/ITEM (IDs 1-4)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| MOB_STATS | 1 | C→S | UpdateMobStatsPayload |
| WEAPON_LEGACY | 2 | C→S | UpdateWeaponPayload |
| EQUIP_MOB | 3 | C→S | EquipMobPayload |
| MODIFY_ITEM | 4 | C→S | ModifyItemPayload |

### 10.1.2 ENDURANCE QUEST (IDs 5-25)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| START_QUEST | 5 | C→S | StartQuestPayload |
| QUEST_ACTION | 6 | C→S | QuestActionPayload |
| QUEST_SYNC | 7 | S→C | QuestSyncPayload |
| SHOP_PURCHASE | 8 | C→S | ShopPurchasePayload |
| SHOP_SYNC | 9 | S→C | ShopSyncPayload |
| REQUEST_SHOP_SYNC | 10 | C→S | RequestShopSyncPayload |
| MOB_CONFIG_CONFIRM | 11 | S→C | MobConfigConfirmPayload |
| QUEST_DEATH | 12 | S→C | QuestDeathPayload |
| PERK_CHOICES | 13 | S→C | PerkChoicesPayload |
| PERK_SELECTION | 14 | C→S | PerkSelectionPayload |
| QUEST_COMPLETION | 15 | S→C | QuestCompletionPayload |
| PERSONAL_RECORDS_SYNC | 16 | S→C | PersonalRecordsSyncPayload |
| REQUEST_PERSONAL_RECORDS | 17 | C→S | RequestPersonalRecordsPayload |
| BOSS_ALERT | 18 | S→C | BossAlertPayload |
| REQUEST_ARENA_SUGGESTIONS | 19 | C→S | RequestArenaSuggestionsPayload |
| ARENA_SUGGESTIONS | 20 | S→C | ArenaSuggestionsPayload |
| KIT_SYNC | 21 | C→S | KitSyncPayload |
| KIT_SYNC_CONFIRM | 22 | S→C | KitSyncConfirmPayload |
| INSTANCE_LOADING | 23 | S→C | InstanceLoadingPayload |
| WAVE_DIRECTIVE_CHOICES | 24 | S→C | WaveDirectiveChoicesPayload |
| WAVE_DIRECTIVE_SELECTION | 25 | C→S | WaveDirectiveSelectionPayload |

### 10.1.3 PARTY (IDs 26-34)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| PARTY_ACTION | 26 | C→S | PartyActionPayload |
| PARTY_SYNC | 28 | S→C | PartySyncPayload |
| QUEST_SEQUENCE | 29 | S→C | QuestSequencePayload |
| NAMED_INVITE | 30 | C→S | NamedInvitePayload |
| ARRIVAL_CONFIRM | 31 | C→S | ArrivalConfirmPayload |
| CANCEL_SEQUENCE | 32 | C→S | CancelSequencePayload |
| INVITE_RESPONSE | 33 | C→S | InviteResponsePayload |
| PARTY_STATS_SYNC | 34 | S→C | PartyStatsSyncPayload |

### 10.1.4 CONFIG/TELEMETRY (IDs 36-45)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| UPDATE_ARMOR | 36 | C→S | UpdateArmorPayload |
| RANGED_WEAPON_STATS | 37 | C→S | RangedWeaponStatsPayload |
| ARMOR_STATS | 38 | C→S | ArmorStatsPayload |
| GLOBAL_CONFIG_SYNC | 39 | S→C | GlobalConfigSyncPayload |
| RECIPE_SYNC | 40 | C→S | RecipeSyncPayload |
| RECIPE_CLIENT_SYNC | 41 | S→C | RecipeClientSyncPayload |
| TELEMETRY_BATCH | 42 | C→S | TelemetryBatchPayload |
| EDITOR_APPLY_CONFIRM | 43 | S→C | EditorApplyConfirmPayload |
| ENDURANCE_CONFIG_SYNC | 44 | C→S | EnduranceConfigSyncPayload |
| CONTRACT_SYNC | 45 | S→C | ContractSyncPayload |

### 10.1.5 ITEM STATS (IDs 46-54)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| USABLE_STATS | 46 | C→S | UsableStatsPayload |
| FOOD_STATS | 47 | C→S | FoodStatsPayload |
| FUEL_STATS | 48 | C→S | FuelStatsPayload |
| WEAPON_STATS_V2 | 49 | C→S | WeaponStatsPayload |
| TENSION_UPDATE | 51 | S→C | TensionUpdatePayload |
| GAME_MECHANICS_SYNC | 52 | S→C | GameMechanicsSyncPayload |
| ENDURANCE_MOB_CONFIG_SYNC | 53 | C→S | EnduranceMobConfigSyncPayload |
| COMBAT_FLOW_SYNC | 54 | S→C | CombatFlowSyncPayload |

### 10.1.6 SHIELD VISUAL (IDs 56-59)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| SHIELD_STATE | 56 | S→C | ShieldStatePayload |
| SHIELD_IMPACT | 57 | S→C | ShieldImpactPayload |
| SHIELD_SHATTER | 58 | S→C | ShieldShatterPayload |
| IMPACT_SYNC | 59 | S→C | ImpactSyncPayload |

### 10.1.7 ABILITY (IDs 66-68)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| STAMINA_SYNC | 66 | S→C | StaminaSyncPayload |
| ABILITY_ACTION | 67 | C→S | AbilityActionPayload |
| LVC_SYNC | 68 | S→C | LVCSyncPayload |

### 10.1.8 ARENA (IDs 76-78)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| BUILD_PROGRESS | 76 | S→C | BuildProgressPayload |
| ENVIRONMENT_SYNC | 77 | S→C | EnvironmentSyncPayload |
| ZONE_DEBUG | 78 | S→C | ZoneDebugPayload |

### 10.1.9 CHALLENGES (IDs 86-89)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| CHALLENGE_SYNC | 86 | S→C | ChallengeSyncPayload |

### 10.1.10 DEBUG (IDs 90-94)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| DEBUG_TOGGLE | 90 | C→S | DebugTogglePayload |
| DEBUG_SYNC | 91 | S→C | DebugSyncPayload |
| ENTITY_PATHING | 92 | S→C | EntityPathingPayload |
| ENTITY_SCAN_DATA | 93 | S→C | EntityScanDataPayload |
| ENTITY_SCANNER_OPEN | 94 | S→C | EntityScannerOpenPayload |

### 10.1.11 MAILBOX (IDs 100-115)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| MAILBOX_SYNC | 100 | S→C | MailboxSyncPayload |
| MAILBOX_SEND | 101 | C→S | MailboxSendPayload |
| MAILBOX_READ | 102 | C→S | MailboxReadPayload |
| MAILBOX_NOTIFY | 105 | S→C | MailboxNotifyPayload |
| NEWS_SYNC | 106 | S→C | NewsSyncPayload |
| NEWS_READ | 107 | C→S | NewsReadPayload |
| TASK_SYNC | 108 | S→C | TaskSyncPayload |
| TASK_ACTION | 109 | C→S | TaskActionPayload |
| MAILBOX_STATUS | 110 | S→C | MailboxStatusPayload |
| MAILBOX_ACCESS | 111 | S→C | MailboxAccessPayload |
| TICKET_SYNC | 112 | S→C | TicketSyncPayload |
| TICKET_CREATE | 113 | C→S | TicketCreatePayload |
| TICKET_SYNC_REQUEST | 114 | C→S | TicketSyncRequestPayload |
| TICKET_ACTION | 115 | C→S | TicketActionPayload |

### 10.1.12 NOTIFICATION (IDs 120-124)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| UNIFIED_NOTIFICATION | 120 | S→C | UnifiedNotificationPayload |
| NOTIFICATION_PREFS_SYNC | 121 | S→C | NotificationPreferencesSyncPayload |
| NOTIFICATION_PREFS_UPDATE | 122 | C→S | NotificationPreferencesUpdatePayload |
| SEASON_PASS_SYNC | 123 | S→C | SeasonPassPayload |
| REQUEST_SEASON_PASS | 124 | C→S | RequestSeasonPassPayload |

### 10.1.13 COMPAT (IDs 130-132)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| NUTRITION_SYNC | 130 | S→C | NutritionSyncPayload |
| REQUEST_MOB_POOL_CONFIG | 131 | C→S | RequestMobPoolConfigPayload |
| MOB_POOL_CONFIG_SYNC | 132 | S→C | MobPoolConfigSyncPayload |

### 10.1.14 NEXUS (IDs 140-144)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| NEXUS_DIALOG | 140 | S→C | NexusDialogPayload |
| NEXUS_DIALOG_ACTION | 141 | C→S | NexusDialogActionPayload |
| NEXUS_UI | 142 | S→C | NexusUiPayload |
| NEXUS_LOG_REQUEST | 143 | C→S | NexusLogRequestPayload |
| NEXUS_LOG_SNAPSHOT | 144 | S→C | NexusLogSnapshotPayload |

### 10.1.15 PORTAL (IDs 150-152)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| PORTAL_STATE | 150 | S→C | PortalStatePayload |
| PORTAL_PREVIEW_REQUEST | 151 | C→S | PortalPreviewRequestPayload |
| PORTAL_PREVIEW | 152 | S→C | PortalPreviewPayload |

### 10.1.16 HOLOGRAM (IDs 160-161)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| HOLOGRAM_CONFIG | 160 | C→S | HologramConfigPayload |
| HOLOGRAM_OPEN_SCREEN | 161 | S→C | HologramOpenScreenPayload |

### 10.1.17 CLONE (IDs 170-171)

| Channel | ID | Direction | Payload |
|---------|----|-----------|---------|
| TELEPAD_CONFIG | 170 | C→S | TelepadConfigPayload |
| TELEPAD_OPEN_SCREEN | 171 | S→C | TelepadOpenScreenPayload |

---

# 11. COMANDI REGISTRATI

## 11.1 Arena Commands

```
/arena
  ├─ build           - Build arena da template
  ├─ validate        - Validare template
  ├─ list            - Elencare arene
  └─ test            - Test arena configuration
```

**File:** `com.devmod.arena.command.ArenaCommands`

## 11.2 Nexus Commands

```
/devmod nexus
  ├─ zones           - Lista zone disponibili
  ├─ tp [zone]       - Teleportarsi a zona
  ├─ go [zone]       - Alias per tp
  ├─ enter           - Enter hub
  ├─ return/exit     - Tornare a origine
  ├─ bug [message]   - Report bug ticket
  ├─ suggestion      - Submit suggestion
  ├─ question        - Submit question
  ├─ avatar          - Avatar management
  │   ├─ spawn
  │   ├─ remove
  │   └─ status
  └─ status          - Nexus status
```

**File:** `com.devmod.runtime.NexusCommand`

## 11.3 Portal Commands

```
/portal
  ├─ create          - Create custom portal
  ├─ link            - Link portals
  └─ list            - List portals
```

**File:** `com.devmod.portal.command.PortalCommand`

## 11.4 Debug Commands

```
/devdebug
  ├─ list            - List debug features
  ├─ off             - Disable all debug
  ├─ biome           - Biome diagnostics
  └─ [feature]       - Toggle specific feature
```

**File:** `com.devmod.debug.DebugCommand`

## 11.5 Mailbox Commands

```
/mailbox
  ├─ send            - Send mail to player
  ├─ clear           - Clear mailbox
  └─ broadcast       - Send to all players

/report [message]    - Submit report ticket
```

**File:** `com.devmod.mailbox.admin.MailboxCommands`

## 11.6 Export Commands

```
/config export       - Export current config
/recipe export       - Export all recipes
```

---

# 12. ATTRIBUTI CUSTOM

## 12.1 Attributi Totali: 9

**File:** `com.devmod.attributes.ModAttributes`

| Attributo | ID | Default | Range | Descrizione |
|-----------|----|---------|-----------|----|
| CRIT_CHANCE | crit_chance | 0.0 | [-1M, 1M] | Probabilità critico |
| CRIT_MULTIPLIER | crit_multiplier | 1.5 | [-1M, 1M] | Moltiplicatore critico |
| ARMOR_SHRED | armor_shred | 0.0 | [-1M, 1M] | Penetrazione armatura |
| LIFE_STEAL | life_steal | 0.0 | [-1M, 1M] | Furto vita |
| DAMAGE_BONUS | damage_bonus | 0.0 | [-1M, 1M] | Bonus danno generale |
| DAMAGE_VS_UNDEAD | damage_vs_undead | 0.0 | [-1M, 1M] | Danno vs non morti |
| DAMAGE_VS_ARTHROPODS | damage_vs_arthropods | 0.0 | [-1M, 1M] | Danno vs artropodi |
| DAMAGE_VS_PLAYERS | damage_vs_players | 0.0 | [-1M, 1M] | Danno PvP |
| TRUE_DAMAGE_PERCENT | true_damage_percent | 0.0 | [-1M, 1M] | % danno puro |

---

# 13. DATA COMPONENTS

## 13.1 Components Totali: 16

### 13.1.1 Item Stats Components (6)

| Component | File | Type |
|-----------|------|------|
| ARMOR_STATS | ArmorComponents | CompoundTag |
| WEAPON_STATS | WeaponComponents | CompoundTag |
| FOOD_STATS | FoodComponents | CompoundTag |
| FUEL_STATS | FuelComponents | CompoundTag |
| USABLE_STATS | UsableComponents | CompoundTag |
| BIOSCAN_DATA | CloneComponents | BioscanData |

### 13.1.2 Ranged Components (8)

| Component | File | Type |
|-----------|------|------|
| DRAW_TIME_TICKS | RangedComponents | Float |
| PROJECTILE_SPEED | RangedComponents | Float |
| PROJECTILE_GRAVITY | RangedComponents | Float |
| PROJECTILE_SPREAD | RangedComponents | Float |
| BASE_ARROW_DAMAGE | RangedComponents | Float |
| MULTISHOT_COUNT | RangedComponents | Integer |
| PIERCING_LEVEL | RangedComponents | Integer |
| AMMO_TAG_FILTER | RangedComponents | ResourceLocation |

---

# 14. CREATIVE TABS

## 14.1 Tabs Totali: 4

| Tab | ID | Icon | Contenuti |
|-----|----|------|-----------|
| DevMod | `itemGroup.devmod` | viewer_item | viewer_item |
| Portals | `itemGroup.devmod.portals` | portal_igniter_blue | 16 igniters + linker + 5 rune |
| Clone | `itemGroup.devmod.clone` | bioscanner | 9 items clone module |
| Nexus Blocks | `itemGroup.devmod.nexus_blocks` | nexus_panel | 144 blocchi Nexus |

---

# 15. RISORSE E ASSETS

## 15.1 Statistiche Assets

| Categoria | Quantità |
|-----------|----------|
| Blockstates | 163 |
| Block Models | 333 |
| Item Models | 179 |
| PNG Textures | ~264 |
| Ricette | 29 |
| Loot Tables | 6 |
| Item Tags | 6 |
| Shaders | 22 |
| Presets | 6 |
| Mob Config | 6 |
| Schema JSON | 2 |
| Lingue | 2 |
| Strutture NBT | 4 |

## 15.2 File di Lingua

| File | Linee | Copertura |
|------|-------|-----------|
| en_us.json | 2990 | Completa |
| it_it.json | 2107 | Parziale |

## 15.3 Shaders

| Shader | Tipo | Uso |
|--------|------|-----|
| energy_shield | Vertex/Fragment | Shield visuals |
| heatmap | Vertex/Fragment | Telemetry heatmap |
| impact_vfx | Vertex/Fragment | Combat impact |
| pathfinding | Vertex/Fragment | Entity AI debug |
| weapon_trail | Vertex/Fragment | Weapon swing trail |
| simplex_noise | Library | Shared noise |
| fresnel | Library | Shared fresnel |

---

# 16. CONFIGURAZIONE BUILD

## 16.1 Plugin Attivi

- java-library
- maven-publish
- net.neoforged.moddev (2.0.120)
- idea
- jacoco
- checkstyle
- net.ltgt.errorprone (4.1.0)
- com.github.spotbugs (6.0.7)

## 16.2 Run Configurations

| Config | Descrizione |
|--------|-------------|
| client | Client standard |
| server | Server con --nogui |
| gameTestServer | GameTest runner |
| clientStrictMode | DuckDB strict mode test |
| data | Data generator |

## 16.3 Code Quality Rules

### JaCoCo Coverage Policy (DD41)

| Tipo Codice | Target |
|-------------|--------|
| Core logic (cleanup, template, validation) | 80% |
| MC-dependent (monitor, world, entity) | 60% |
| Network/UI (ui, hud, dashboard) | 50% |

### Checkstyle

- Import ordering: java → javax → external → minecraft → neoforged → devmod

### Error Prone + NullAway

- @Nonnull/@Nullable enforcement
- Excluded: compat, testing, gametest, arena, combat, util

### SpotBugs

- Effort: MAX
- Report Level: MEDIUM

## 16.4 Design Token Enforcement

Hex colors centralizzati in:
- `DesignTokens.java`
- `SharedColorTokens.java`

Allowlist per eccezioni:
- NutritionCategory.java
- PortalColor.java
- RuneType.java

---

# 17. DIPENDENZE

## 17.1 Dipendenze Runtime Principali

| Libreria | Versione | Uso |
|----------|----------|-----|
| NeoForge | 21.1.216 | Mod framework |
| DuckDB JDBC | 1.4.3.0 | Analytics database |
| Javalin | 5.6.5 | REST API server |
| Jetty | 11.0.20 | Web server |
| Jackson | 2.17.2 | JSON serialization |
| JJWT | 0.12.3 | JWT auth tokens |

## 17.2 Dipendenze Test

| Libreria | Versione | Uso |
|----------|----------|-----|
| JUnit 5 | 5.10.0 | Unit testing |
| Mockito | 4.11.0 | Mocking |
| SLF4J | 2.0.9 | Logging |

## 17.3 Soft Dependencies

| Mod | Integrazione |
|-----|--------------|
| Distant Horizons | API 2.3.0-b-1.21.1 |
| Player Animator | 2.0.4+1.21.1 |
| GUI Scaler | 1.21.1-1.0.1 |
| Easy-Diet | Reflection API |

---

# 18. NOTE ARCHITETTURALI

## 18.1 Pattern Chiave

1. **Soft Dependencies**: Easy-Diet usa reflection API per evitare hard dependency
2. **Classpath Isolation**: Javalin/Jackson scaricati at runtime (JavalinBootstrap)
3. **Null Safety**: Error Prone + NullAway per @Nonnull/@Nullable
4. **Color Token System**: Hex colors centralizzati, checkstyle enforcement
5. **Deprecation Policy (DD67)**: 3-milestone deprecation con -Werror toggle
6. **Performance Budgeting**: BuildBudget per MSPT durante arena construction

## 18.2 Sistemi Critici

### Arena System
- Template con ereditarietà
- Policy con mutatori
- Build asincrono con budget MSPT
- Autosmoke testing

### Endurance System
- Sfide multi-room
- Nutrizione categorizzata
- Party collaborativo
- Leaderboard

### Telemetry System
- DuckDB embedded
- Event tracking granulare
- Heatmap generation
- Export CSV/JSON

### Portal System
- 16 colori
- 5 tipi rune
- Range configurabile
- Cross-dimension

---

# 19. CHANGELOG STRUTTURALE

## Commit Recenti

| Hash | Descrizione |
|------|-------------|
| e80c637c | feat: Aggiungi nuove texture per il blocco nexus_backup_confirmed |
| fc810e1a | feat: Add Centrifuge block and GUI implementation |
| 5c1377a3 | Refactor CloneClientSetup and Renderer Classes; Improve Null Safety |
| 78992bb5 | feat: Update NeurocellL block and entity to support directional offsets |
| 573050f2 | feat: Add rotation values for neurocell and neurocell_l block states |

## File Modificati (Staging)

- build.gradle (M)
- 22 blockstates Nexus (M)
- 90+ models/block Nexus (M)
- 110+ textures/block/nexus (M)
- Nuove texture: nexus_iron.png, backup folder

---

# APPENDICE A: ENUMERAZIONI

## A.1 PortalColor (16 valori)

| Colore | Hex | Nome |
|--------|-----|------|
| WHITE | #F9FFFE | Bianco |
| ORANGE | #F9801D | Arancione |
| MAGENTA | #C74EBD | Magenta |
| LIGHT_BLUE | #3AB3DA | Blu chiaro |
| YELLOW | #FED83D | Giallo |
| LIME | #80C71F | Verde lime |
| PINK | #F38BAA | Rosa |
| GRAY | #474F52 | Grigio scuro |
| LIGHT_GRAY | #9D9D97 | Grigio chiaro |
| CYAN | #169C9C | Ciano |
| PURPLE | #8932B8 | Viola |
| BLUE | #3C44AA | Blu (default) |
| BROWN | #835432 | Marrone |
| GREEN | #5E7C16 | Verde |
| RED | #B02E26 | Rosso |
| BLACK | #1D1D21 | Nero |

## A.2 RuneType (5 valori)

| Tipo | ID | Colore Glow | Range Bonus | Instant TP | Cross-Dim |
|------|----|----|-------|-------|-------|
| HASTE | haste | 0x55FFFF | 0 | Yes | No |
| GATE | gate | 0xAA00AA | 0 | No | Yes |
| ENHANCER | enhancer | 0xFFAA00 | +100 | No | No |
| STRONG_ENHANCER | strong_enhancer | 0xFF5500 | +500 | No | No |
| INFINITY | infinity | 0x00FF00 | Unlimited | No | No |

---

# APPENDICE B: FLUSSI DATI

## B.1 Clone System Flow

```
1. SCAN:
   Entity → Imprinter → Neurocell (BioscanData) → Bioscanner

2. SPAWN:
   Bioscanner → Reformer → reads Neurocell → PlayerCloneEntity

3. TELEPORT:
   Player → Telepad (charge 40 ticks) → PortalRegistry → Dest Telepad
```

## B.2 Arena Build Flow

```
1. Template Loading:
   JSON → TemplateLoader → TemplateValidator → ArenaTemplateRegistry

2. Build:
   ArenaCommands → AsyncArenaBuilder → ChunkLoadingManager → BatchBlockPlacer

3. Cleanup:
   BuildTransaction → ArenaCleanupExecutor → BlockIntegrityVerifier
```

---

# APPENDICE C: PAYLOAD LIMITS

| Categoria | Limite |
|-----------|--------|
| SMALL | 256 bytes |
| MEDIUM | 2 KB |
| LARGE | 8 KB |
| SYNC_LARGE | 16 KB |
| EDITOR | 32 KB |
| XLARGE | 64 KB |
| TELEMETRY | 128 KB |
| MAILBOX | 4 KB |
| TICKET | 2 KB |

---

**Fine Documento**

*Generato automaticamente. Verificare con il codice sorgente per discrepanze.*
