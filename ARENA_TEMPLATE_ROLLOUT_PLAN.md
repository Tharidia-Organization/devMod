# Arena Template Rollout Plan (Instance-First) v2.2

Documento operativo per trasformare il sistema arena Endurance in un catalogo di template istanziabili (single e multiplayer) completamente isolati, osservabili e facili da testare.

**Revisione**: v2.2 - Separazione concettuale **Template (L1 Layout) + Policy (L2 Gameplay)**. Integrazione completa con tutte le capacità esistenti del progetto.

---

## Architettura a Livelli

| Livello | Nome | Responsabilità | File | Owner |
|---------|------|----------------|------|-------|
| **L1** | `ArenaTemplate` | Layout fisico: size, palette, spawnSlots, hazards, limits | `arena_templates/*.json` | Level Designer |
| **L2** | `ArenaPolicy` | Routing, perk/mutator bindings, reward modifiers | `arena_policies/*.json` | Game Designer |
| **L3** | `ArenaGamification` | Badge, challenge, leaderboard rules | Codice GamificationManager | Tools Dev |

### Perché separare Template e Policy?

1. **Riuso**: stesso layout `boss_ring_80` con policy diverse (`ranked` vs `casual`)
2. **Ownership chiara**: Level Designer non deve toccare reward/routing
3. **Testing isolato**: puoi testare il build senza caricare gamification
4. **Varianti facili**: nuova policy = nuovo JSON, zero codice

---

## Obiettivi
- **Instance dimension obbligatoria** per single/multi: zero residui mondo, parallelismo, cleanup automatico.
- **Catalogo di template arena** (JSON/datapack/structure) con selezione dinamica per mob/bioma/difficoltà.
- **Pipeline di generazione deterministica**: build idempotente, validazione bounds, barriere e spawn slots certificati.
- **Build transazionale**: rollback automatico su failure, chunk loading garantito prima del build.
- **Osservabilità e QA integrati**: metriche per template, smoke auto, wizard e comandi tester per iterare velocemente.
- **Migrazione sicura**: compatibilità con l'arena default 64×64, feature flag progressivi, rollback rapido.
- **Hot-reload development**: ricaricare template senza restart per iterazione veloce.

Non obiettivi: rework completo del wave system o del reward loop (solo integrazione con template/istanze).

---

## Stato attuale (riuso)
- **Instance-first già attivo**: `EnduranceQuestManager#setUseInstanceDimensions(true)` di default; start quest async con overlay loading; fallback legacy rimosso.
- **Party flow**: `QuestStartSequence` prepara arena in istanza (`InstanceArenaManager.startInstanceQuestForParty`), teleporta party, startPreparedQuest con `instanceId`.
- **Arena runtime**: `ArenaManager` costruisce piattaforma pietra + barriere invisibili, 64×64, bounds e spawn distribuiti.
- **Isolamento istanze**: `InstanceManager` + `InstanceArenaManager` mappano `arenaId↔instanceId`, cleanup e forzano end su crash/disconnect.
- **Strumenti tester** (già in mod ma poco sfruttati): QuickTestWizard/TestingHub con preselezione mob, QuickToolsPanel (continue/exit/abandon), HUD metriche (KPS/DPS/DTPS), dev commands endurance (stats/perks/smoke/export), autosmoke run semplice, Dashboard button, telemetria `EnduranceTelemetryService`.

---

## Gap principali
- Nessun **catalogo template**: arena sempre identica; nessuna variazione materiale/bioma/layout o hazard modulari.
- Mancano **metadati** (spawn slots, sicurezza drop, regioni vietate) e **validator** per dimensioni, barriere e vuoti.
- **Selezione template** hardcoded: non esiste routing per mob/difficoltà/party-size o per test mirati.
- **Osservabilità arena** minima: non tracciamo errori di build, tempo di generazione, "dead zone", pathing fallito, leak di entità/blocks.
- **Tooling QA** non collegato ai template: autosmoke non cicla varianti, wizard non mostra template, nessun comando per creare/distruggere template offline.
- **Compatibilità overworld** ancora accennata in alcuni percorsi: serve hardening "instance-only" e fallback chiaro solo per debug.
- **Nessun chunk loading esplicito**: il builder assume chunk caricati ma non lo garantisce.
- **Nessun rollback su failure**: se il build fallisce a metà, blocchi orfani rimangono.

---

## Architettura proposta

### 1) ArenaTemplate Registry
- Sorgente dati: `data/devmod/arena_templates/*.json` (o datapack) + opzionale `structures/` NBT per layout complessi.
- **Schema completo** (vedi sezione dedicata): `id`, `version`, `extends`, `size`, `height`, `palette`, `spawnSlots`, `playerSpawnOffset`, `mobSpawnStrategy`, `forbiddenZones`, `hazards`, `biomeTag`, `lighting`, `instanceSettings`, `compat`, `tags`, `structureNbt`, `buildPriority`, `limits`.
- Loader con validazione e log warning per campi mancanti; fallback su `default_flat_64`.
- **Hot-reload**: comando `/devmod arena reload` per ricaricare template senza restart.

### 2) Template Inheritance
- Supporto `"extends": "parent_template_id"` per evitare duplicazione.
- Il loader risolve ricorsivamente l'ereditarietà e merge i campi.
- Override espliciti sovrascrivono i valori del parent.

### 3) Template Resolution Layer
- API `ArenaTemplateResolver.resolve(mobId, questType, playerCount, difficultyTag)` → `ArenaTemplate`.
- **Sistema di scoring weighted** invece di cascata lineare:
  ```
  TemplateScore = matchMob(5) + matchDifficulty(3) + matchPlayerCount(2) + matchTags(1)
  ```
- Override manuale via command/wizard ha priorità assoluta.
- Telemetria su ogni fallback con motivo.

### 4) Builder istanziabile (Transazionale)
- Nuovo `TemplateArenaBuilder` con architettura modulare:
  ```
  TemplateArenaBuilder (orchestrator)
  ├── ChunkLoader        # forza caricamento chunk prima del build
  ├── FloorBuilder       # costruisce pavimento
  ├── WallBuilder        # costruisce pareti/barriere
  ├── SpawnSlotValidator # valida slot spawn (aria sopra, solido sotto)
  ├── HazardPlacer       # posiziona hazard (lava, void, traps)
  └── BuildTelemetry     # metriche build
  ```

- **Build transazionale**:
  ```java
  try {
      builder.beginTransaction();
      builder.ensureChunksLoaded();  // CRITICO: prima di qualsiasi setBlock
      builder.buildFloor();
      builder.buildWalls();
      builder.buildSpawnSlots();
      builder.placeHazards();
      builder.validateFinal();
      builder.commit();
  } catch (BuildException e) {
      builder.rollback(); // rimuove TUTTI i blocchi piazzati
      telemetry.emit("arena.build.fail", templateId, e.getMessage());
      throw e;
  }
  ```

- **Dry-run con preview**:
  ```java
  public record BuildValidation(
      boolean valid,
      int blocksRequired,
      int chunksRequired,
      long estimatedMs,
      List<String> warnings,
      List<String> errors
  ) {}

  // TemplateArenaBuilder.validateBuild(template, level) → BuildValidation
  ```

- Restituisce `ArenaHandle { arenaId, instanceId, templateId, templateVersion, bounds }`.

### 5) Chunk Loading System
- Prima di costruire, `ChunkLoader.forceChunks(level, bounds, chunkRadius)`.
- Attende che tutti i chunk siano `ChunkStatus.FULL`.
- Timeout configurabile (default 10s) con failure se non completato.
- Rilascia chunk forzati dopo cleanup arena.

### 6) Lifecycle e cleanup
- Allinea `EndurancePlayerStateManager.cleanupArenaOrInstance` per usare template metadata (forbiddenZones, hazard cleanup).
- Distruzione: rimuovi blocchi modificati + segnala `EnduranceTelemetryService` (`arena_destroy_ms`, `blocks_modified`, `entities_remaining`).
- **Cleanup enumerato**: il builder traccia ogni blocco modificato per cleanup deterministico.

### 7) Osservabilità
- Eventi per telemetria: `arena.build.start|end|fail|rollback`, `arena.template.chosen|fallback|unsupported`, `arena.cleanup.start|end`.
- Metriche per template: tempo build, chunk load time, spawn validation failures, entity leak count, deaths per zone (heatmap futura).
- **Versioning telemetria**: ogni evento include `templateVersion` per coerenza storica.

### 8) Tooling e UX
- QuickTestWizard/TestingHub: scelta template (dropdown + tag filter), preview size/materiale, force template per sessione.
- Dev commands:
  - `/devmod arena create <template>` (solo test)
  - `/devmod arena validate <template>` (dry-run con report)
  - `/devmod arena list` (tutti i template disponibili)
  - `/devmod arena reload` (hot-reload da disco)
  - `/devmod arena force <template>` (forza per sessione corrente)
  - `/devmod arena metrics <template>` (ultimi N build)
- Autosmoke: cicla set di template "smoke" e produce report sintetico (esiti spawn, tempo build, crash/log).
- HUD overlay: mostra `Template: id v{version} (size) | Instance: short-id`.

---

## Capacità della mod (da sfruttare al 100%)
- **Instance Dimension System**: isolamento, cleanup auto, recovery da crash, party teleport, mapping arena↔instance.
- **Telemetry/Analytics**: `TelemetryService`, `EnduranceTelemetryService`, export heatmap, `EnduranceAnalytics` per wave/perk/boss; hook di eventi già presenti.
- **Reward/Gamification**: `RewardSystem`, `GamificationManager` (badge/challenge/leaderboard) per incentivare coverage template.
- **Combat systems**: `WaveManager`, `MutatorSystem`, `PerkSystem`, `ComboSystem`, `BossWaveSystem` già integrati nel loop Endurance.
- **Player State**: `EndurancePlayerStateManager` per snapshot/restore, `SessionHandler` per cleanup; già compatibili con instance mode.
- **UI/UX Tester**: QuickTestWizard, TestingHub, QuickToolsPanel shortcuts, HUD metriche, Dashboard button.
- **Dev/QA tooling**: autosmoke, dev commands endurance (stats/perks/smoke/export), telemetry dashboard backend (upgrade plan).
- **Data layer**: Quest registry, persistence, NDJSON telemetry (`run/telemetry`), analytics dashboard (DASHBOARD_UPGRADE_PLAN).

---

## Sfruttamento profondo delle capacità
- **Template-aware telemetry**: aggiungere `templateId` + `templateVersion` in tutti gli eventi (arena.build|cleanup, wave, death, spawn, perks/mutators picks) per heatmap e DPS/TTK per template.
- **Analytics/Dashboard**: nuovi endpoint/chart "performance per template", "death heatmap per template", "spawn fail per template" usando le API in DASHBOARD_UPGRADE_PLAN.
- **Gamification**: badge "Template explorer" (X template testati), "Smoke ranger" (tutti i template smoke passati), leaderboard per coverage template.
- **Reward**: token bonus su nuovi template o tag `smoke:true`; penalità minime su failure early per favorire iterazione rapida.
- **Mutator/Perk coupling**: consentire binding template→mutator set (es. ranged-friendly, melee stress) e loggare scelte per correlare difficoltà.
- **Autosmoke**: loop su `tags.contains("smoke")` + `forceTemplateId` con esiti metrici (build_ms, leak_ent, leak_blocks, wave_start_ok); esport CSV/JSON.
- **QuickTestWizard/TestingHub**: preimpostare template consigliati per mob/bioma, mostrare requisiti (min/max players, size), anteprima materiali.
- **Dev commands**: oltre a create/list/validate, aggiungere `force-template <id>` (sessione corrente), `arena metrics <id>` (ultimi N build).
- **PlayerState/Recovery**: integra metadata forbiddenZones/hazard per cleanup; verifica ritorno a overworld con `instanceId` associato a template.

---

## Cosa riusare / cosa trasformare
- **Riuso diretto:** `InstanceArenaManager` (allocazione livello + mapping), `EnduranceQuestManager` flow instance-first, `QuestStartSequence` party teleport, `ArenaManager.Arena` per bounds/spawn distribuiti, telemetria esistente, QuickToolsPanel/commands.
- **Trasformare:** `ArenaManager` in provider plug-in (interfaccia `ArenaFactory`), build floor/walls parametrico invece di hardcoded; `EnduranceQuestScreen`/wizard per includere selezione template; autosmoke per iterare varianti.
- **Sostituire:** creazione overworld legacy → feature flag solo debug; hardcode 64×64 → lettura da template; routing manuale per party (usa `TemplateResolver` + `instanceSettings`).

---

## Fasi di rollout

### Fase 0 – Inventory & spec (2 giorni)
- Mappare arene attuali (solo default) e requisiti mob/boss.
- Definire `arena_template.schema.json` completo con tutti i campi.
- Creare 2 template validati e testati (`default_flat_64`, `boss_ring_80`).
- Implementare loader base con validazione schema.
- Abilitare log/telemetria grezzi su build/destroy esistenti per baseline (tempo medio build, leak entità).

**Criteria di done**: Schema JSON pubblicato e validato, 2 template funzionanti caricati dal loader, metriche baseline build_ms.

### Fase 1 – Registry + builder (3 giorni)
- Implementare `ArenaTemplateRegistry` + loader/validator con fallback e inheritance.
- Implementare `TemplateArenaBuilder` con:
  - Chunk loading garantito
  - Build transazionale con rollback
  - Dry-run validation
  - Moduli separati (Floor/Wall/SpawnSlot/Hazard)
- Aggiungere `TemplateResolver` con scoring weighted.
- Integrare in `EnduranceQuestManager.prepareArenaForParty` e `startQuestInInstanceDimension`.
- Aggiornare telemetria con eventi build/cleanup/rollback + errori.

**Criteria di done**: Loader+validator funzionanti, builder transazionale con rollback testato, fallback sicuro, dry-run command, telemetria build/cleanup.

### Fase 2 – UX/Test tooling (1.5 giorni)
- QuickTestWizard/TestingHub: aggiungere selezione template + note su requisiti (size, player cap).
- Dev commands `/arena` per create/validate/list/reload/force/metrics (solo op/tester).
- HUD overlay breve (template + version + instance id) e messaggi party "Template X – size Y".
- Estendere autosmoke per ciclare i template taggati `smoke:true` con report CSV/JSON.

**Criteria di done**: Wizard con select template, tutti i comandi funzionanti, overlay HUD con templateId, hot-reload funzionante.

### Fase 3 – Qualità e hardening (2 giorni)
- Validator avanzato: check chunk load completato, spawnSlots aria+floor, bounding box senza gap.
- Gate "instance-only": errori chiari se flag disattivato, fallback solo in debug.
- Cleanup robusto: conteggio blocchi modificati, rimozione hazard custom, verify entità residue.
- Performance budget per template (tempo build massimo, blocchi totali) con alert/log.
- Limiti di sicurezza: max template size (256), max hazards (50), max spawn slots (100).
- Test casi edge: build parziale, chunk timeout, template malformato, concorrenza.

**Criteria di done**: Validator avanzato completo, gate instance-only, cleanup con conteggio residui, budget performance con alert, tutti i limiti applicati.

### Fase 4 – Migrazione e rollout controllato (2 giorni)
- Migrare chiamate dirette ad `ArenaManager.createArena` (se rimaste) verso `TemplateArenaBuilder`.
- Rilasciare 3 template iniziali e metterli dietro feature flag (config).
- Eseguire autosmoke + manual checklist istanze (`INSTANCE_SYSTEM_MANUAL_TEST_CHECKLIST`) su single e party.
- Abilitare routing per mob top-priority; monitorare telemetria 48h; espandere catalogo.
- Buffer per hotfix e issue emergenti.

**Criteria di done**: Migrazione call-site completata, 3 template in produzione dietro flag, monitor 48h senza regressioni, dashboard per-template funzionante.

---

## Piano QA e osservabilità
- **Unit/integration:** test per `ArenaTemplateRegistry` (schema, inheritance), `TemplateResolver` (scoring), `TemplateArenaBuilder` (transactional build, rollback), validazione spawn slots.
- **Manuale istanze:** checklist attuale (teleport, inventory wipe, ritorno) estesa con `templateId`, `templateVersion`, tempo build.
- **Autosmoke**: 1 run per template (single) + 1 party (2p) con log error scanner.
- **Metriche/alert:** percent build fail, tempo medio build, rollback count, entità residue >0, mismatch arena↔instance map.
- **Crash forensics:** log path `logs/arena-template-*.log`, includere `templateId`, `templateVersion`, `instanceId`, stack trace.

### Test automatici per template
Per ogni template, generare automaticamente:
- Unit test di loading/validation schema
- Unit test di inheritance resolution
- Smoke test di build in istanza vuota
- Performance test (tempo build, memoria, blocchi)
- Rollback test (failure simulata a metà build)

---

## Rischi e mitigazioni

| Rischio | Mitigazione |
|---------|-------------|
| **Template invalidi** | Validator + fallback default + alert in console + hot-reload per fix rapido |
| **Leak blocchi/entità** | Cleanup enumerato + telemetria di residui + autosmoke giornaliero |
| **Performance build** su template grandi | Budget tempo/blocchi + build async con progress overlay + limiti size |
| **Compatibilità party** | Usare sempre `startInstanceQuestForParty` + spawnSlots con `minPlayers/maxPlayers` |
| **Build failure a metà** | Build transazionale con rollback automatico |
| **Chunk non caricati** | ChunkLoader con timeout e failure esplicita |
| **Concorrenza template** | Lock per template durante build (o pool di istanze pre-buildate) |
| **Template obsoleti** | Versioning + migration path + warning per campi deprecati |
| **Telemetria inconsistente** | `templateVersion` in ogni evento |

---

## Next steps immediati
1. Scrivere `arena_template.schema.json` completo in `data/devmod/arena_templates/`.
2. Creare 2 template validati (`default_flat_64`, `boss_ring_80`) con tutti i campi.
3. Implementare loader base con validazione e inheritance.
4. Implementare `ChunkLoader` per garantire chunk caricati.
5. Implementare `TemplateArenaBuilder` transazionale con rollback.

---

## Schema L1 - ArenaTemplate (Layout) - 100% Autocontenuto

Lo schema contiene **tutti** i parametri per costruire l'arena. Zero valori hardcoded nel builder.

```json
// arena_templates/default_flat_64.template.json
{
  "$schema": "arena_template.schema.json",

  // === METADATI ===
  "id": "default_flat_64",
  "version": 1,
  "extends": null,
  "schemaVersion": "1.0.0",
  "breakingChange": false,

  // === GEOMETRIA E ORIGINE ===
  "origin": {
    "mode": "center",
    "x": 0, "y": 64, "z": 0
  },
  "size": 64,

  // === FLOOR ===
  "floor": {
    "y": 64,
    "thickness": 1,
    "material": "minecraft:stone_bricks",
    "pattern": "border",
    "borderMaterial": "minecraft:polished_andesite",
    "borderWidth": 2
  },

  // === WALLS ===
  "walls": {
    "enabled": true,
    "material": "minecraft:barrier",
    "height": 10,
    "thickness": 1,
    "startY": 64,
    "style": "solid"
  },

  // === CEILING ===
  "ceiling": {
    "enabled": true,
    "material": "minecraft:barrier",
    "y": 74,
    "thickness": 1
  },

  // === UNDERFLOOR ===
  "underfloor": {
    "material": "minecraft:bedrock",
    "depth": 3
  },

  // === PALETTE ===
  "palette": {
    "accent": "minecraft:polished_andesite",
    "highlight": "minecraft:glowstone"
  },

  // === BIOME ===
  "biome": {
    "id": "minecraft:plains",
    "applyTo": "bounds"
  },

  // === LIGHTING ===
  "lighting": {
    "skyLight": 15,
    "blockLight": 10,
    "ambientLight": true,
    "lightSources": []
  },

  // === SPAWN SLOTS ===
  "spawnSlots": [
    {
      "pos": [0, 1, 0],
      "yMode": "relativeToFloor",
      "tags": ["center", "player"],
      "validation": { "requireSolidBelow": true, "requireAirAbove": 2 }
    },
    {
      "pos": [10, 1, 0],
      "yMode": "relativeToFloor",
      "tags": ["melee", "mob"],
      "validation": { "requireSolidBelow": true, "requireAirAbove": 2 }
    },
    {
      "pos": [-10, 1, 0],
      "yMode": "relativeToFloor",
      "tags": ["ranged", "mob"],
      "validation": { "requireSolidBelow": true, "requireAirAbove": 2 }
    },
    {
      "pos": [20, 1, 20],
      "yMode": "relativeToFloor",
      "tags": ["corner", "mob"],
      "validation": { "requireSolidBelow": true, "requireAirAbove": 2 }
    }
  ],
  "playerSpawnOffset": { "x": 0, "y": 0, "z": 0 },
  "mobSpawnStrategy": "distributed",

  // === FORBIDDEN ZONES ===
  "forbiddenZones": [
    {
      "min": [-5, 0, -5],
      "max": [5, 6, 5],
      "yMode": "relativeToFloor",
      "reason": "player_safe_zone"
    }
  ],

  // === HAZARDS ===
  "hazards": [],

  // === ENVIRONMENT ===
  "environment": {
    "particles": [],
    "ambientSound": null,
    "fog": { "enabled": false }
  },

  // === COMPAT ===
  "compat": { "minPlayers": 1, "maxPlayers": 4 },

  // === INSTANCE SETTINGS ===
  "instanceSettings": {
    "chunkRadius": 2,
    "tickDistance": 4,
    "keepLoaded": true
  },

  // === STRUCTURE NBT (opzionale) ===
  "structureNbt": null,

  // === BUILD SETTINGS ===
  "buildPriority": "sync",
  "buildOrder": "floor_first",
  "limits": {
    "maxBuildTimeMs": 5000,
    "maxBlocks": 50000,
    "maxEntities": 100
  },

  // === TAGS ===
  "tags": ["flat", "melee-friendly", "smoke"]
}
```

### Template con inheritance

```json
// arena_templates/boss_ring_80.template.json
{
  "id": "boss_ring_80",
  "version": 1,
  "extends": "default_flat_64",
  "schemaVersion": "1.0.0",

  // Override solo i campi diversi dal parent
  "size": 80,

  "floor": {
    "y": 64,
    "thickness": 1,
    "material": "minecraft:deepslate_bricks",
    "pattern": "solid"
  },

  "walls": {
    "enabled": true,
    "material": "minecraft:barrier",
    "height": 15,
    "thickness": 1,
    "startY": 64
  },

  "ceiling": {
    "enabled": true,
    "material": "minecraft:barrier",
    "y": 79
  },

  "palette": {
    "accent": "minecraft:gilded_blackstone",
    "hazardBorder": "minecraft:magma_block"
  },

  "lighting": {
    "skyLight": 10,
    "blockLight": 5,
    "ambientLight": true,
    "lightSources": [
      { "pos": [0, 75, 0], "block": "minecraft:shroomlight" }
    ]
  },

  "spawnSlots": [
    {
      "pos": [0, 1, 0],
      "yMode": "relativeToFloor",
      "tags": ["center", "boss"],
      "validation": { "requireSolidBelow": true, "requireAirAbove": 3 }
    },
    {
      "pos": [30, 1, 0],
      "yMode": "relativeToFloor",
      "tags": ["player", "safe"],
      "validation": { "requireSolidBelow": true, "requireAirAbove": 2 }
    },
    {
      "pos": [-30, 1, 0],
      "yMode": "relativeToFloor",
      "tags": ["player", "safe"],
      "validation": { "requireSolidBelow": true, "requireAirAbove": 2 }
    }
  ],

  "hazards": [
    {
      "type": "lava_ring",
      "params": { "innerRadius": 35, "outerRadius": 38 },
      "y": 64,
      "yMode": "absolute"
    }
  ],

  "environment": {
    "particles": [
      { "type": "minecraft:smoke", "rate": 0.05, "area": "bounds" }
    ],
    "ambientSound": "minecraft:ambient.nether_wastes.mood"
  },

  "compat": { "minPlayers": 1, "maxPlayers": 2 },

  "limits": {
    "maxBuildTimeMs": 10000,
    "maxBlocks": 100000
  },

  "tags": ["ring", "hazard", "boss-layout", "smoke"]
}
```

### Campi calcolati dal builder (non in schema)

Il builder calcola internamente:
- `bounds`: AABB calcolato da `origin + size + walls.height`
- `absoluteSpawnPositions`: conversione da `relativeToFloor` a coordinate mondo
- `chunkList`: lista chunk da pre-caricare
- `blockCount`: conteggio blocchi stimato per dry-run

---

## Schema L2 - ArenaPolicy (Gameplay)

```json
// arena_policies/default.policy.json (fallback globale)
{
  "$schema": "arena_policy.schema.json",
  "id": "default",
  "version": 1,
  "templateId": null,  // applica a tutti i template senza policy specifica

  "routing": {
    "mobIds": [],
    "questTypes": [],
    "difficultyTags": [],
    "weight": 0
  },

  "perkBindings": {
    "suggested": [],
    "excluded": [],
    "required": []
  },

  "mutatorBindings": {
    "suggested": [],
    "excluded": [],
    "required": []
  },

  "rewardModifiers": {
    "baseMultiplier": 1.0,
    "firstCompletionBonus": 0.1,
    "hazardBonus": 0.0,
    "streakMultiplier": 0.05
  },

  "balanceOverrides": {
    "spawnRateMultiplier": 1.0,
    "damageMultiplier": 1.0,
    "waveScaling": 1.0
  },

  "tags": ["default", "smoke"]
}
```

### Policy ranked per boss layout

```json
// arena_policies/boss_ring_80_ranked.policy.json
{
  "id": "boss_ring_80_ranked",
  "version": 1,
  "templateId": "boss_ring_80",

  "routing": {
    "mobIds": ["minecraft:warden", "minecraft:ender_dragon"],
    "questTypes": ["boss"],
    "difficultyTags": ["extreme", "ranked"],
    "weight": 10
  },

  "perkBindings": {
    "suggested": ["shield_start", "lifesteal"],
    "excluded": ["glass_cannon"],
    "required": []
  },

  "mutatorBindings": {
    "suggested": ["ranged_boost"],
    "excluded": ["melee_only"],
    "required": ["boss_enrage"]
  },

  "rewardModifiers": {
    "baseMultiplier": 1.5,
    "firstCompletionBonus": 0.25,
    "hazardBonus": 0.1,
    "streakMultiplier": 0.1
  },

  "balanceOverrides": {
    "spawnRateMultiplier": 1.2,
    "damageMultiplier": 1.3,
    "waveScaling": 1.5
  },

  "tags": ["ranked", "boss", "hardcore", "smoke"]
}
```

### Policy casual per stesso layout

```json
// arena_policies/boss_ring_80_casual.policy.json
{
  "id": "boss_ring_80_casual",
  "version": 1,
  "templateId": "boss_ring_80",

  "routing": {
    "mobIds": ["minecraft:warden"],
    "questTypes": ["boss"],
    "difficultyTags": ["normal", "casual"],
    "weight": 5
  },

  "perkBindings": {
    "suggested": ["health_boost", "damage_resist"],
    "excluded": [],
    "required": []
  },

  "mutatorBindings": {
    "suggested": [],
    "excluded": ["boss_enrage"],
    "required": []
  },

  "rewardModifiers": {
    "baseMultiplier": 1.0,
    "firstCompletionBonus": 0.1,
    "hazardBonus": 0.05,
    "streakMultiplier": 0.05
  },

  "balanceOverrides": {
    "spawnRateMultiplier": 0.8,
    "damageMultiplier": 0.9,
    "waveScaling": 1.0
  },

  "tags": ["casual", "boss"]
}
```

---

## Priorità di routing - PolicyResolver (Weighted Scoring)

Il routing ora avviene su **Policy** (non Template). Il resolver restituisce una coppia `{template, policy}`.

```java
public class PolicyResolver {
    private static final int WEIGHT_MOB = 5;
    private static final int WEIGHT_QUEST_TYPE = 4;
    private static final int WEIGHT_DIFFICULTY = 3;
    private static final int WEIGHT_PLAYER_COUNT = 2;
    private static final int WEIGHT_TAGS = 1;

    public ResolvedArena resolve(
        @Nullable String forcePolicyId,
        ResourceLocation mobId,
        String questType,
        int playerCount,
        Set<String> tags
    ) {
        // 1. Override manuale ha priorità assoluta
        if (forcePolicyId != null) {
            ArenaPolicy policy = policyRegistry.get(forcePolicyId)
                .orElseGet(() -> {
                    telemetry.emit("arena.policy.force_not_found", forcePolicyId);
                    return getDefaultPolicy();
                });
            ArenaTemplate template = templateRegistry.get(policy.templateId())
                .orElse(getDefaultTemplate());
            return new ResolvedArena(template, policy);
        }

        // 2. Scoring weighted su Policy
        return policyRegistry.all().stream()
            .map(p -> new ScoredPolicy(p, score(p, mobId, questType, playerCount, tags)))
            .filter(sp -> sp.score > 0)
            .max(Comparator.comparingInt(sp -> sp.score))
            .map(sp -> {
                ArenaTemplate template = templateRegistry.get(sp.policy.templateId())
                    .orElse(getDefaultTemplate());
                return new ResolvedArena(template, sp.policy);
            })
            .orElseGet(() -> {
                telemetry.emit("arena.policy.fallback", "no_match");
                return new ResolvedArena(getDefaultTemplate(), getDefaultPolicy());
            });
    }

    private int score(ArenaPolicy p, ResourceLocation mobId, String questType, int playerCount, Set<String> tags) {
        int score = 0;

        // Score da policy.routing
        if (p.routing().mobIds().contains(mobId.toString())) score += WEIGHT_MOB;
        if (p.routing().questTypes().contains(questType)) score += WEIGHT_QUEST_TYPE;
        if (tags.stream().anyMatch(p.routing().difficultyTags()::contains)) score += WEIGHT_DIFFICULTY;
        if (tags.stream().anyMatch(p.tags()::contains)) score += WEIGHT_TAGS;

        // Player count check dal template associato
        ArenaTemplate template = templateRegistry.get(p.templateId()).orElse(null);
        if (template != null && playerCount >= template.compat().minPlayers()
            && playerCount <= template.compat().maxPlayers()) {
            score += WEIGHT_PLAYER_COUNT;
        }

        score += p.routing().weight(); // bonus esplicito

        return score;
    }
}

// Return type
public record ResolvedArena(
    ArenaTemplate template,  // L1 - layout fisico
    ArenaPolicy policy       // L2 - gameplay rules
) {}
```

---

## Architettura (vista sintetica)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DOMAIN LAYER                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  ArenaTemplateRegistry (L1)     ArenaPolicyRegistry (L2)                     │
│  ├── load(path)                 ├── load(path)                              │
│  ├── get(id)                    ├── get(id)                                 │
│  ├── all()                      ├── getForTemplate(templateId)              │
│  ├── reload()                   ├── all()                                   │
│  └── resolveInheritance()       └── reload()                                │
│                                                                              │
│  PolicyResolver                                                              │
│  ├── resolve(mob, quest, players, tags) → ResolvedArena{template, policy}   │
│  ├── score(policy, criteria)                                                │
│  └── getDefaultPolicy(), getDefaultTemplate()                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BUILD LAYER                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  TemplateArenaBuilder (orchestrator)                                         │
│  ├── validateBuild(template) → BuildValidation                              │
│  ├── build(template, level) → ArenaHandle                                   │
│  │   ├── beginTransaction()                                                 │
│  │   ├── ChunkLoader.forceChunks()                                          │
│  │   ├── FloorBuilder.build()                                               │
│  │   ├── WallBuilder.build()                                                │
│  │   ├── SpawnSlotValidator.validate()                                      │
│  │   ├── HazardPlacer.place()                                               │
│  │   ├── commit() / rollback()                                              │
│  │   └── BuildTelemetry.emit()                                              │
│  └── Uses: InstanceArenaManager (dimension), ArenaManager (setBlock)        │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            RUNTIME LAYER                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  EnduranceQuestManager                                                       │
│  ├── prepareArenaForParty() → PolicyResolver → TemplateArenaBuilder         │
│  ├── startQuestInInstanceDimension()                                        │
│  └── session stores: arenaId, instanceId, templateId, policyId + versions   │
│                                                                              │
│  WaveManager / BossWaveSystem / MutatorSystem / PerkSystem                   │
│  └── operano sull'arena, usano spawnSlots (L1) + bindings/balanceOverrides (L2)
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          OBSERVABILITY LAYER                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  EnduranceTelemetryService                                                   │
│  ├── arena.build.start|end|fail|rollback                                    │
│  ├── arena.policy.chosen|fallback|force_not_found                           │
│  ├── arena.cleanup.start|end                                                │
│  └── Tutti gli eventi: templateId, templateVersion, policyId, policyVersion │
│                                                                              │
│  ArenaTemplateMetrics                                                        │
│  ├── build_time_ms per template                                             │
│  ├── spawn_validation_failures                                              │
│  ├── entity_leak_count                                                      │
│  └── rollback_count                                                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            TOOLING LAYER                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  QuickTestWizard / TestingHub                                                │
│  ├── Template dropdown + tag filter                                         │
│  ├── Preview size/materiale                                                 │
│  └── Force template per sessione                                            │
│                                                                              │
│  Dev Commands (/devmod arena ...)                                            │
│  ├── create <template>    # build in test instance                          │
│  ├── validate <template>  # dry-run con report                              │
│  ├── list                 # tutti i template                                │
│  ├── reload               # hot-reload da disco                             │
│  ├── force <template>     # forza per sessione                              │
│  └── metrics <template>   # ultimi N build                                  │
│                                                                              │
│  Autosmoke                                                                   │
│  ├── Cicla template con tag "smoke"                                         │
│  └── Report CSV/JSON con metriche                                           │
│                                                                              │
│  HUD Overlay                                                                 │
│  └── Template: id v{version} (size) | Instance: short-id                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## QA – collegamento checklist
- Estendere `INSTANCE_SYSTEM_MANUAL_TEST_CHECKLIST` con colonne `templateId`, `templateVersion`, `build_ms`, `entities_residual`, `blocks_residual`.
- Autosmoke: assert `arena.build.fail == 0`, `rollback_count == 0`, `entities_residual == 0`, `blocks_residual == 0`; report CSV/JSON.
- Test routing: casi mock per override, mob rule, questType rule, scoring, fallback.
- Test inheritance: verifica merge corretto dei campi, override espliciti.
- Test concurrency: 2 party richiedono stesso template contemporaneamente.

---

## Data flow (architetturale)
- **Ingresso**: Player/party → `EnduranceQuestScreen/QuickTestWizard` (opzionale `forceTemplateId`) → `QuestStartSequence` / `EnduranceQuestManager`.
- **Selezione**: `TemplateResolver` (override → scoring weighted → fallback) restituisce `ArenaTemplate`.
- **Provisioning**: `InstanceArenaManager` crea dimensione; `TemplateArenaBuilder` costruisce arena secondo template (transazionale); produce `ArenaHandle {arenaId, instanceId, templateId, templateVersion}`.
- **Runtime**: `WaveManager`/`BossWaveSystem`/`MutatorSystem`/`PerkSystem` operano sull'arena; HUD mostra template/version/instance; QuickToolsPanel/commands interagiscono con session.
- **Telemetry**: `EnduranceTelemetryService` etichetta eventi con `templateId/templateVersion/instanceId`; `TelemetryService` logga combat/spawn/death con room = `templateId` o `arenaId`.
- **Analytics/Dashboard**: API aggregano per `templateId` + `templateVersion` (build_ms, failure_rate, rollback_rate, TTK/KPS/DTPS per template, death heatmap, spawn fail).
- **Egress/Cleanup**: `EndurancePlayerStateManager.cleanupArenaOrInstance` usa metadata template (forbidden/hazards) per ripulire; `InstanceArenaManager` chiude dimensione; telemetria `arena.cleanup.*` con residui.

---

## Integrare con i sistemi esistenti (punti di contatto)
- **Wave/Spawn**: usare `ArenaTemplate.spawnSlots` come seed per spawn distribution; se vuoti, fallback all'algoritmo esistente.
- **BossWaveSystem**: consentire tag `boss` per template; route boss arenas; loggare phase con `templateId`.
- **Mutator/Perk**: opzionale binding template→mutator set; telemetria per correlare outcome per template+mutator/perk.
- **Reward/Gamification**: consumano `templateId` per badge/leaderboard; wallet bonus per nuovi template o smoke passati.
- **Telemetry Dashboard**: agganciare endpoints (DASHBOARD_UPGRADE_PLAN) a `templateId` + `templateVersion` filter e grafici (heatmap, TTK, deaths, build time).
- **Recovery**: `InstanceManager` + `EndurancePlayerStateManager` già gestiscono disconnect; assicurare che `templateId` + `templateVersion` siano serializzati nel session state per restore.

---

## Misure di completamento (per fase)
- **F0**: Schema JSON completo + 2 template campione validati dal loader; loader con inheritance; metriche baseline build_ms su default.
- **F1**: Registry/Resolver/Builder transazionale funzionanti in instance mode; chunk loading; rollback testato; telemetria build/cleanup/rollback; fallback sicuro; dry-run command.
- **F2**: UI/commands/autosmoke con `forceTemplateId`; HUD mostra template/version/instance; hot-reload funzionante; report autosmoke con residui.
- **F3**: Validator avanzato (spawn, chunk loaded, forbidden/hazard), gate instance-only, budget blocchi/tempo con alert, limiti di sicurezza, test edge cases.
- **F4**: Migrazione call-site completata; 3 template in produzione dietro flag; monitor 48h con dashboard per-template; zero regressioni.

---

## Owner / durata / done

| Fase | Owner | Durata | Criteria di done |
| --- | --- | --- | --- |
| 0 Spec + Config | Tech Lead + Core Dev | **2.5g** | Schema JSON completo, 2 template validati, ArenaTemplateConfig, Feature Flags chain |
| 1 Registry/Builder + Persistence | Core Dev | **4g** | Loader+validator+inheritance, builder transazionale, DuckDB/NDJSON, Instance integration |
| 2 UX/Tooling + Dashboard | UI/Tools Dev | **3g** | Wizard, HUD, Dashboard analytics endpoints, Chart.js frontend |
| 3 Hardening | Core Dev | **2g** | Validator avanzato, gate instance-only, limiti sicurezza, cleanup con residui, test edge cases |
| 4 Rollout + Migration | QA + Core Dev | **2.5g** | Migrazione call-site, 3 template in prod, autosmoke OK, monitor 48h |
| 5 Integrazione sistemi | Core Dev + Tools Dev | **2g** | Wave/Perk/Reward/Gamification/Telemetria estesa |
| 6 Concurrency + Pool | Core Dev | **0.75g** | Lock strategy, prebuild pool opzionale |
| 7 Alert/Monitoring | Core Dev + QA | **0.5g** | Canali alert, soglie, autosmoke scheduling |

**Totale: 17.25 giorni** (breakdown per ruolo: Tech Lead 1g, Core Dev 10g, UI/Tools Dev 3.5g, QA 2.75g)

---

## ArenaTemplateConfig (NUOVO v2.1)

Configurazione centralizzata con hot-reload:

```java
public class ArenaTemplateConfig {
    // Paths
    String templateDirectory = "data/devmod/arena_templates/";

    // Feature Flags (chain progressiva)
    boolean instanceOnly = true;           // gate legacy overworld
    boolean arenaTemplateEnabled = false;  // abilita sistema template
    boolean routingEnabled = false;        // abilita weighted scoring
    boolean gamificationEnabled = false;   // abilita badge/challenge template

    // Budget defaults
    int defaultMaxBlocks = 8000;
    int defaultMaxBuildTimeMs = 5000;

    // Budget boss/large templates
    int bossMaxBlocks = 100000;
    int bossMaxBuildTimeMs = 15000;

    // Alert thresholds
    AlertThresholds alertThresholds = new AlertThresholds();

    // Prebuild Pool (opzionale - skip per produzioni piccole)
    boolean prebuildPoolEnabled = false;
    Map<String, Integer> prebuildPoolConfig = Map.of("default_flat_64", 2);
    int prebuildPoolMaxTotal = 5;
    long prebuildPoolRefreshInterval = 300000; // 5 min

    // Alert channels
    List<String> alertChannels = List.of("console", "log", "dashboard");

    // Autosmoke
    String autosmokeSchedule = "0 3 * * *"; // 03:00 daily
    String autosmokeWebhookUrl = null; // optional Slack/webhook
}
```

### Alert Thresholds (soglie default vs boss/large)

| Metrica | WARN (default) | ERROR (default) | WARN (boss) | ERROR (boss) |
|---------|----------------|-----------------|-------------|--------------|
| build_ms | 3000ms | 8000ms | 8000ms | 15000ms |
| failure_rate (24h) | 5% | 15% | 5% | 15% |
| rollback_rate (24h) | 2% | 10% | 2% | 10% |
| mspt_during_build | 40ms | 50ms | 45ms | 55ms |
| tps_during_build | <18 | <15 | <18 | <15 |
| entities_residual | >0 | >5 | >0 | >5 |
| blocks_residual | >0 | >10 | >0 | >10 |

### Alert Channels

| Canale | Descrizione |
|--------|-------------|
| `console` | Output immediato (WARN giallo, ERROR rosso) |
| `log` | File `logs/arena-alerts.json` (JSON line format) |
| `dashboard` | Widget alert su tab Arena Template con history 7 giorni |
| `telemetry` | Evento `arena.alert` in EnduranceTelemetryService |
| `webhook` | (opzionale) Slack/HTTP per ERROR critici |

---

## Autosmoke Scheduling (NUOVO v2.1)

| Parametro | Valore |
|-----------|--------|
| Ambiente | **staging/not-prod** (mai in produzione) |
| Cron schedule | `0 3 * * *` (03:00 daily, configurabile) |
| Single template timeout | 60s |
| Full suite timeout | 5 minuti max |
| Report location | `run/autosmoke-reports/YYYY-MM-DD.json` |
| Report retention | 30 giorni |

**Destinazioni alert**:
- Console: WARN/ERROR immediato
- Log file: `logs/autosmoke-YYYY-MM-DD.json`
- Dashboard: badge rosso/verde su tab Arena Template
- Slack/Webhook: solo ERROR, se configurato

---

## Prebuild Pool (NUOVO v2.1 - Opzionale)

**Quando abilitare**: solo se server con >50 player concorrenti o >10 quest/minuto

**Impatto risorse**:
- Memoria: ~5MB per istanza prebuildata
- CPU: background thread a bassa priorità, ~1% CPU idle
- Chunk: mantiene chunk loaded per pool

**Nota**: Skip per produzioni piccole (<20 player) - overhead non giustificato.

```json
{
  "prebuildPoolEnabled": false,
  "prebuildPoolConfig": {
    "default_flat_64": 2,
    "boss_ring_80": 1
  },
  "prebuildPoolMaxTotal": 5,
  "prebuildPoolRefreshInterval": 300000
}
```

---

## Persistence Layer (NUOVO v2.1)

### NDJSON Events
File: `endurance_templates.ndjson`
```json
{"ts": "...", "type": "template_build", "templateId": "...", "templateVersion": 1, "build_ms": 1234, "result": "success"}
{"ts": "...", "type": "template_used", "templateId": "...", "questId": "...", "waveNumber": 5, "outcome": "complete"}
```

### DuckDB Tables
```sql
CREATE TABLE arena_template_builds (
  id UUID PRIMARY KEY,
  template_id VARCHAR NOT NULL,
  template_version INT NOT NULL,
  instance_id UUID,
  build_ms INT,
  result VARCHAR,
  blocks_placed INT,
  entities_residual INT,
  blocks_residual INT,
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE arena_template_usage (
  id UUID PRIMARY KEY,
  template_id VARCHAR NOT NULL,
  template_version INT NOT NULL,
  quest_id UUID,
  player_id UUID,
  wave_reached INT,
  outcome VARCHAR,
  duration_ms INT,
  created_at TIMESTAMP DEFAULT NOW()
);
```

Retention: 30 giorni build data, 90 giorni usage data.

---

## Gamification Integration (NUOVO v2.1)

### Badge Template-Aware
| Badge | Requisito | Tier |
|-------|-----------|------|
| Template Explorer | X template unici completati | COMMON(3), UNCOMMON(5), RARE(10), EPIC(all) |
| Smoke Ranger | Tutti i template `smoke:true` passati | RARE |
| Arena Master | Completa ogni template almeno 3 volte | EPIC |
| Template Speedrunner | Completa qualsiasi template sotto il par time | RARE |

### Challenge System
**Daily**: "Complete 2 quests in different templates", "Reach wave 10 in template X"
**Weekly**: "Try 5 different templates", "Complete all smoke templates"

### Reward Bonuses
- Nuovo template completato: +10% base reward
- Template smoke completion: +25% base reward
- Template boss completion: +50% base reward
- Template difficulty multiplier: `weight * 0.1 + 1.0`

---

## Checklist di sicurezza e qualità

- [ ] Documentazione API pubblica per `ArenaTemplate`, `TemplateResolver`, `TemplateArenaBuilder`
- [ ] Logging strutturato (JSON) per debug build failures
- [ ] Metriche esportabili per monitoring (build_time, failure_rate, rollback_rate)
- [ ] Limiti di sicurezza applicati: max size 256, max hazards 50, max spawn slots 100
- [ ] Gestione concorrenza: lock per template durante build o pool pre-buildate
- [ ] Versioning in ogni evento telemetria
- [ ] Migration path documentato per template obsoleti
- [ ] Hot-reload testato senza memory leak
- [ ] Rollback testato con failure simulata a ogni step del build
- [ ] Chunk loading timeout testato
- [ ] Cleanup residui verificato con autosmoke
