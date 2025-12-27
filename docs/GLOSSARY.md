# Glossary

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

---

## Arena System

| Term | Definition | File Reference |
|------|------------|----------------|
| **ArenaTemplate** | Definizione di template arena | `src/main/java/com/devmod/arena/registry/ArenaTemplate.java` |
| **ArenaPolicy** | Regole di selezione template | `src/main/java/com/devmod/arena/policy/ArenaPolicy.java` |
| **ResolveContext** | Contesto di risoluzione policy | `src/main/java/com/devmod/arena/policy/ResolveContext.java` |
| **ResolvedArena** | Template + policy risolta | `src/main/java/com/devmod/arena/policy/ResolvedArena.java` |
| **TemplateRegistryBootstrap** | Bootstrap registry + config | `src/main/java/com/devmod/arena/registry/TemplateRegistryBootstrap.java` |
| **Autosmoke** | Smoke test automatico template | `src/main/java/com/devmod/arena/autosmoke/` |

---

## Endurance System

| Term | Definition | File Reference |
|------|------------|----------------|
| **EnduranceQuestManager** | Orchestratore sessioni endurance | `src/main/java/com/devmod/endurance/EnduranceQuestManager.java` |
| **WaveManager** | Spawn e progressione wave | `src/main/java/com/devmod/endurance/WaveManager.java` |
| **PerkSystem** | Perk roguelike per wave | `src/main/java/com/devmod/endurance/PerkSystem.java` |
| **ComboSystem** | Scoring combo/stile | `src/main/java/com/devmod/endurance/ComboSystem.java` |
| **RewardSystem** | Reward e shop endurance | `src/main/java/com/devmod/endurance/RewardSystem.java` |

---

## Instance Runtime

| Term | Definition | File Reference |
|------|------------|----------------|
| **InstanceManager** | Lifecycle delle istanze | `src/main/java/com/devmod/runtime/InstanceManager.java` |
| **InstanceRegistry** | Registry istanze attive | `src/main/java/com/devmod/runtime/InstanceRegistry.java` |
| **InstanceData** | Modello istanza | `src/main/java/com/devmod/runtime/InstanceData.java` |
| **PlayerInstanceSnapshot** | Snapshot player (NBT) | `src/main/java/com/devmod/runtime/PlayerInstanceSnapshot.java` |
| **RecoverySystem** | Recovery post-crash | `src/main/java/com/devmod/runtime/RecoverySystem.java` |

---

## Telemetry

| Term | Definition | File Reference |
|------|------------|----------------|
| **TelemetryService** | Orchestratore telemetry | `src/main/java/com/devmod/telemetry/TelemetryService.java` |
| **TelemetryEvents** | Event hooks + tick | `src/main/java/com/devmod/telemetry/TelemetryEvents.java` |
| **DuckDBTelemetryService** | Persistenza DuckDB | `src/main/java/com/devmod/telemetry/duckdb/DuckDBTelemetryService.java` |
| **DuckDBBatchWriter** | Writer batch async | `src/main/java/com/devmod/telemetry/duckdb/DuckDBBatchWriter.java` |
| **TelemetryDashboardServer** | Dashboard server | `src/main/java/com/devmod/telemetry/dashboard/TelemetryDashboardServer.java` |

---

## Radial / UI

| Term | Definition | File Reference |
|------|------------|----------------|
| **RadialMenuScreen** | Menu radiale principale | `src/main/java/com/devmod/client/ui/radial/RadialMenuScreen.java` |
| **ActionRegistry** | Registry globale azioni | `src/main/java/com/devmod/actions/ActionRegistry.java` |
| **RadialAction** | Definizione azione | `src/main/java/com/devmod/actions/RadialAction.java` |
| **ItemEditorScreen** | Editor item | `src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java` |
| **UnifiedSettingsScreen** | Settings UI | `src/main/java/com/devmod/client/ui/unified/UnifiedSettingsScreen.java` |

---

## Network / Communication

| Term | Definition | File Reference |
|------|------------|----------------|
| **ChannelId** | Registry ID canali payload | `src/main/java/com/devmod/network/ChannelId.java` |
| **NetworkHandler** | Registrazione payload | `src/main/java/com/devmod/network/NetworkHandler.java` |
| **PacketValidator** | Validazione/rate limit | `src/main/java/com/devmod/network/PacketValidator.java` |
| **StreamCodec** | Serializzazione payload | Payload classes in `src/main/java/com/devmod/` |

---

## Mailbox System

| Term | Definition | File Reference |
|------|------------|----------------|
| **MailboxManager** | Orchestratore mailbox | `src/main/java/com/devmod/mailbox/MailboxManager.java` |
| **NewsManager** | Gestione news | `src/main/java/com/devmod/mailbox/news/NewsManager.java` |
| **TestTaskManager** | Task QA | `src/main/java/com/devmod/mailbox/task/TestTaskManager.java` |
| **DuckDbMailboxRepository** | Persistenza mailbox | `src/main/java/com/devmod/mailbox/persistence/DuckDbMailboxRepository.java` |
| **MailboxApiServer** | API admin (Javalin) | `src/main/java/com/devmod/mailbox/api/MailboxApiServer.java` |

---

## Config

| Term | Definition | File Reference |
|------|------------|----------------|
| **Config** | Config comune mod | `src/main/java/com/devmod/config/Config.java` |
| **GameMechanicsConfig** | Config meccaniche | `src/main/java/com/devmod/config/GameMechanicsConfig.java` |
| **EditorClientConfig** | Config client editor | `src/main/java/com/devmod/config/EditorClientConfig.java` |
| **ConfigPaths** | Path config runtime | `src/main/java/com/devmod/util/ConfigPaths.java` |

---

## Testing

| Term | Definition | File Reference |
|------|------------|----------------|
| **GameTest** | Framework test in-game | `src/main/java/com/devmod/gametest/` |
| **TestingHub** | UI QA | `src/main/java/com/devmod/client/ui/hub/TestingHub.java` |
| **QATestingScreen** | UI QA session | `src/main/java/com/devmod/client/testing/QATestingScreen.java` |

---

## Abbreviations

| Abbrev | Full Form |
|--------|-----------|
| **DD** | Design Decision |
| **P0/P1/P2** | Priorita task |
| **TTK** | Time-to-Kill |
| **DPS** | Damage Per Second |
| **NBT** | Named Binary Tag |
| **NDJSON** | Newline-Delimited JSON |
| **HUD** | Heads-Up Display |
| **VFX** | Visual Effects |
