# DevMod Architecture

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

Panoramica aggiornata dell'architettura DevMod basata sul codice attuale.

## Vista d'insieme

```
┌────────────────────────────────────────────────────────────────────────────┐
│                                  DevMod                                   │
│                               com.devmod.*                                │
├────────────────────────────────────────────────────────────────────────────┤
│ Server Runtime                                                             │
│  - Arena Templates  (arena/)                                                │
│  - Endurance Quests (endurance/)                                            │
│  - Combat & Damage (combat/, damage/, attributes/)                          │
│  - Party System   (party/)                                                  │
│  - Instance Runtime (runtime/)                                              │
│  - Mailbox/News/Tasks (mailbox/)                                             │
│  - Telemetry & Analytics (telemetry/)                                       │
│  - Network + Validation (network/)                                          │
│  - Config/Overrides (config/, util/ConfigPaths)                             │
├────────────────────────────────────────────────────────────────────────────┤
│ Client Layer                                                                │
│  - Radial Actions + UI (client/ui/radial/, actions/)                         │
│  - Screens/Editors (client/ui/, client/endurance/, client/quest/)            │
│  - HUD/Overlays (client/overlay/, client/rendering/)                         │
│  - Mailbox UI (mailbox/client/)                                              │
│  - Testing Hub (client/ui/hub/, client/testing/)                             │
├────────────────────────────────────────────────────────────────────────────┤
│ Persistence & Assets                                                        │
│  - config/devmod/* (runtime config + state)                                 │
│  - DuckDB (telemetry + mailbox)                                             │
│  - NDJSON logs (run/*)                                                      │
│  - assets/, data/, schemas/, dashboard/                                     │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Sistemi Server Core (package + classi chiave)

### Arena Templates (`com.devmod.arena`)
- Registry + policy: `ArenaTemplateRegistry`, `ArenaPolicyRegistry`, `PolicyResolver`.
- Bootstrap/config: `TemplateRegistryBootstrap`, `ArenaTemplateConfig`.
- Build: `TemplateArenaBuilder`, `AsyncArenaBuilder`, `AsyncArenaBuildCoordinator`.
- QA/ops: `AutosmokeRunner`, `AutosmokeScheduler`, `AutosmokeReportWriter`.

### Endurance Quests (`com.devmod.endurance`)
- Orchestrazione: `EnduranceQuestManager`, `EnduranceEventHandler`.
- Progressione: `WaveManager`, `BossWaveSystem`, `WaveDirective`.
- Progressione/abilità: `PerkSystem`, `ComboSystem`, `RewardSystem`.

### Combat & Damage (`com.devmod.combat`, `com.devmod.damage`)
- Core: `DamageHandler`, `HitHelper`, `DamageBreakdown`.
- Tracking: `DamageTracker`, `HitData`.

### Party System (`com.devmod.party`)
- Stato party: `PartyManager`, `PartyData`, `PartyInvite`.
- Network: `PartyNetworkHandler` + payloads `Party*Payload`.

### Instance Runtime (`com.devmod.runtime`)
- Lifecycle: `InstanceManager`, `InstanceRegistry`, `InstanceData`.
- Recovery: `RecoverySystem`, `PlayerInstanceSnapshot`.
- Event wiring: `InstanceEventHandler`.

### Mailbox System (`com.devmod.mailbox`)
- Core: `MailboxManager`, `MailboxMessage`, `MailboxConfig`.
- News & task: `NewsManager`, `TestTaskManager`.
- Persistence: `DuckDbMailboxRepository`.
- API: `MailboxApiServer` (Javalin).

### Telemetry (`com.devmod.telemetry`)
- Orchestrazione: `TelemetryService`, `TelemetryEvents`.
- DuckDB: `DuckDBTelemetryService`, `DuckDBBatchWriter`, `DuckDBQueryAPI`.
- Dashboard: `TelemetryDashboardServer`.

### Network & Validation (`com.devmod.network`)
- Registry: `ChannelId`, `NetworkHandler`, `DebugNetworkHandler`.
- Validation: `PacketValidator`.

---

## Client Layer (package + classi chiave)

### Radial Actions + UI
- `client/ui/radial/RadialMenuScreen`
- `actions/ActionRegistry`, `actions/RadialAction`

### Screen principali
- `client/ui/editor/ItemEditorScreen`
- `client/ui/unified/UnifiedSettingsScreen`
- `client/ui/screens/TelemetryDashboardScreen`
- `client/ui/hub/TestingHub`
- `client/testing/QATestingScreen`
- `client/party/PartyScreen`

### Endurance UI
- `client/endurance/EnduranceQuestScreen`, `PerkSelectionScreen`, `KitSelectionScreen`, `WaveDirectiveScreen`, `EnduranceShopScreen`

### Mailbox UI
- `mailbox/client/screen/MailboxScreen`, `MailboxComposeScreen`, `NewsScreen`, `TesterTaskScreen`
- `mailbox/client/overlay/MailNotificationOverlay`, `NewsAlertOverlay`

### Overlays/Rendering
- `client/overlay/*`, `client/rendering/*`

---

## Risorse e Config
- Config runtime: `config/devmod/*` (vedi `com.devmod.util.ConfigPaths`).
- Assets: `src/main/resources/assets/devmod/`.
- Data packs: `src/main/resources/data/devmod/`.
- Schemi: `src/main/resources/schemas/`.
- Dashboard: `src/main/resources/dashboard/`.

---

## Cross-References
- [[PROJECT_TOPOLOGY]]
- [[ENTRYPOINTS]]
- [[MOC]]
