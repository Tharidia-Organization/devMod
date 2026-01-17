# Glossario

> Ultimo aggiornamento: 2026-01-15

## Arena

| Termine | Definizione | File |
|---|---|---|
| ArenaTemplate | Definizione template arena | `src/main/java/com/devmod/arena/registry/ArenaTemplate.java` |
| ArenaPolicy | Regole di selezione template | `src/main/java/com/devmod/arena/policy/ArenaPolicy.java` |
| TemplateRegistryBootstrap | Bootstrap registry + config | `src/main/java/com/devmod/arena/registry/TemplateRegistryBootstrap.java` |
| Autosmoke | Smoke test automatico | `src/main/java/com/devmod/arena/autosmoke/` |

## Endurance

| Termine | Definizione | File |
|---|---|---|
| EnduranceQuestManager | Orchestratore sessioni | `src/main/java/com/devmod/endurance/EnduranceQuestManager.java` |
| WaveManager | Spawn e progressione wave | `src/main/java/com/devmod/endurance/WaveManager.java` |
| PerkSystem | Perk roguelike | `src/main/java/com/devmod/endurance/PerkSystem.java` |
| RewardSystem | Reward e shop | `src/main/java/com/devmod/endurance/RewardSystem.java` |

## Combat & Collision

| Termine | Definizione | File |
|---|---|---|
| BodyPartDefinition | Definizione hitbox per parte | `src/main/java/com/devmod/collision/bodypart/BodyPartDefinition.java` |
| OBBRaycast | Raycast su OBB | `src/main/java/com/devmod/collision/obb/OBBRaycast.java` |
| DamageHandler | Pipeline danni | `src/main/java/com/devmod/combat/DamageHandler.java` |

## Clone System

| Termine | Definizione | File |
|---|---|---|
| Neurocell | Camera clonazione | `src/main/java/com/devmod/clone/block/NeurocellBlock.java` |
| Reformer | Spawner clone | `src/main/java/com/devmod/clone/block/ReformerBlock.java` |
| Telepad | Teleport pad | `src/main/java/com/devmod/clone/block/TelepadBlock.java` |
| PlayerCloneEntity | Entita clone | `src/main/java/com/devmod/clone/entity/PlayerCloneEntity.java` |

## Portal & Transport

| Termine | Definizione | File |
|---|---|---|
| CustomPortalBlock | Blocco portale custom | `src/main/java/com/devmod/portal/block/CustomPortalBlock.java` |
| Warp Core | Nodo trasporto | `src/main/java/com/devmod/transport/block/TransportCoreBlock.java` |

## Area & Zone

| Termine | Definizione | File |
|---|---|---|
| Area Builder | Sistema build aree | `src/main/java/com/devmod/area/` |
| Zone Marker | Marker data-driven | `src/main/java/com/devmod/zone/` |

## NPC

| Termine | Definizione | File |
|---|---|---|
| Dialog | Sistema dialoghi | `src/main/java/com/devmod/npc/dialog/` |
| Neurocell NPC | Item configurazione NPC | `src/main/java/com/devmod/npc/item/` |

## Hologram

| Termine | Definizione | File |
|---|---|---|
| Hologram Projector | Blocco proiettore 3D | `src/main/java/com/devmod/hologram/` |

## Telemetry

| Termine | Definizione | File |
|---|---|---|
| TelemetryService | Orchestratore telemetry | `src/main/java/com/devmod/telemetry/TelemetryService.java` |
| DuckDBSchemaManager | Schema runtime DuckDB | `src/main/java/com/devmod/telemetry/duckdb/DuckDBSchemaManager.java` |

## Network

| Termine | Definizione | File |
|---|---|---|
| ChannelId | ID canali payload | `src/main/java/com/devmod/network/ChannelId.java` |
| NetworkHandler | Registrazione payload | `src/main/java/com/devmod/network/NetworkHandler.java` |

## Abbreviazioni

| Abbrev | Significato |
|---|---|
| DD | Design Decision |
| TTK | Time-to-Kill |
| DPS | Damage Per Second |
