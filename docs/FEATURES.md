# DevMod Features

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

Inventario sintetico delle feature presenti nel codice attuale. Per dettagli, vedere i dossier in `docs/areas/` e `docs/subsystems/`.

---

## Arena Templates (com.devmod.arena)
- Registry + policy resolution (`ArenaTemplateRegistry`, `ArenaPolicyRegistry`, `PolicyResolver`).
- Build transazionale sync/async (`TemplateArenaBuilder`, `AsyncArenaBuilder`).
- Autosmoke + report (`AutosmokeRunner`, `AutosmokeScheduler`).
- Telemetry/alerting arena (`ArenaTelemetry`, `AlertRouter`).

## Endurance Quests (com.devmod.endurance)
- Quest lifecycle e sessioni (`EnduranceQuestManager`, `EnduranceEventHandler`).
- Wave progression + boss waves (`WaveManager`, `BossWaveSystem`).
- Perk + combo + reward (`PerkSystem`, `ComboSystem`, `RewardSystem`).
- UI dedicata (`EnduranceQuestScreen`, `PerkSelectionScreen`, `KitSelectionScreen`).

## Instance Runtime (com.devmod.runtime)
- Instance lifecycle + registry (`InstanceManager`, `InstanceRegistry`, `InstanceData`).
- Snapshot e recovery player (`PlayerInstanceSnapshot`, `RecoverySystem`).
- Event wiring (`InstanceEventHandler`).

## Party System (com.devmod.party)
- Gestione party, inviti e sync (`PartyManager`, `PartyData`, `PartyInvite`).
- Payload e handler (`Party*Payload`, `PartyNetworkHandler`).
- UI (`PartyScreen`).

## Combat & Damage (com.devmod.combat, com.devmod.damage)
- Calcolo danno + body-part detection (`DamageHandler`, `HitHelper`, `DamageBreakdown`).
- Tracking e hooks (`DamageTracker`, `HitData`).

## Telemetry & Analytics (com.devmod.telemetry)
- Event tracking + tick pipeline (`TelemetryEvents`, `TelemetryService`).
- Persistenza DuckDB (`DuckDBTelemetryService`, `DuckDBBatchWriter`).
- Dashboard server (`TelemetryDashboardServer`).

## Mailbox / News / Tasks (com.devmod.mailbox)
- Messaggistica + news + task (`MailboxManager`, `NewsManager`, `TestTaskManager`).
- Persistenza DuckDB (`DuckDbMailboxRepository`).
- API admin (Javalin) (`MailboxApiServer`).
- UI client (`MailboxScreen`, `NewsScreen`, `TesterTaskScreen`).

## Editor & Tools (client UI)
- Item editor (`ItemEditorScreen`).
- Unified settings (`UnifiedSettingsScreen`).
- Radial actions (`RadialMenuScreen`, `ActionRegistry`).
- Testing hub + QA (`TestingHub`, `QATestingScreen`).

## Network & Config
- Packet registry + handlers (`ChannelId`, `NetworkHandler`).
- Validation + rate limits (`PacketValidator`).
- Config files e override (`Config`, `GameMechanicsConfig`, `GameplayOverridesManager`).

---

## Cross-References
- [[PROJECT_TOPOLOGY]]
- [[ENTRYPOINTS]]
- [[areas/arena/README]]
- [[areas/endurance/README]]
- [[areas/instance/README]]
- [[areas/telemetry/README]]
- [[areas/mailbox/README]]
