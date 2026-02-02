# Alberatura Completa DevMod (estesa)

## A) Alberatura menuPath (azioni/comandi)
Totale voci: 315

```text
- CentrifugeMenu -> CentrifugeScreen :: com.devmod.clone.menu (com/devmod/clone/menu/CentrifugeMenu.java)
- NeurocellItemMenu -> NeurocellItemScreen :: com.devmod.clone.menu (com/devmod/clone/menu/NeurocellItemMenu.java)
- NeurocellLMenu -> NeurocellLScreen :: com.devmod.clone.menu (com/devmod/clone/menu/NeurocellLMenu.java)
- NeurocellMannequinMenu -> NeurocellMannequinScreen :: com.devmod.clone.menu (com/devmod/clone/menu/NeurocellMannequinMenu.java)
- NeurocellMenu -> NeurocellScreen :: com.devmod.clone.menu (com/devmod/clone/menu/NeurocellMenu.java)
```

## B) Inventario schermate (classi Screen) per package
Totale classi Screen: 78

```text
- com
  - devmod
    - client
      - area
        - AreaBuilderScreen
        - NexusEditorCentralScreen
      - arena
        - ui
          - ArenaTestWizard
      - endurance
        - EnduranceQuestScreen
        - EnduranceSettingsScreen
        - EnduranceShopScreen
        - KitSelectionScreen
        - MobPoolEditorScreen
        - PerkSelectionScreen
        - QuestCompletionScreen
        - QuestDeathScreen
        - QuestExitConfirmScreen
        - WaveCheckpointScreen
        - WaveDirectiveScreen
        - wis
          - ui
            - DebriefScreen
      - hologram
        - HologramEditorScreen
      - nexus
        - NexusDialogScreen
      - notification
        - ui
          - NotificationCenterScreen
          - NotificationSettingsScreen
      - npc
        - DialogEditorScreen
        - DialogOptionEditorScreen
        - DialogPreviewScreen
        - NpcConfigScreen
        - NpcDialogScreen
        - graph
          - DialogGraphScreen
        - group
          - GroupDialogScreen
      - overlay
        - CombatRecapScreen
      - party
        - InvitePopupScreen
        - PartyScreen
      - quest
        - QuestEditorScreen
      - template
        - TemplateEditorScreen
      - testing
        - BadgeTestScreen
        - QATestingScreen
        - UIResponsivenessTestScreen
      - transport
        - PartyTeleportScreen
        - TransportConfigScreen
        - TransportNetworkSelectScreen
        - WaypointSelectScreen
      - ui
        - BaseDevModScreen
        - ErrorBoundaryScreen
        - ErrorFallbackScreen
        - ModScreen
        - MyScreen
        - OpenExternalConfirmScreen
        - RoomBoundsEditorScreen
        - WelcomeScreen
        - admin
          - AdminInstanceScreen
        - editor
          - ItemEditorScreen
          - StaminaSystemEditor
        - hub
          - TestingHub
        - radial
          - RadialActionDetailScreen
          - RadialMenuScreen
        - screens
          - EditorHubScreen
          - MobConfigScreen
          - MobEquipmentScreen
          - TelemetryDashboardScreen
          - TelemetryLogViewerScreen
        - season
          - SeasonPassScreen
        - testing
          - VoxelLabScreen
          - VoxelLabUiTestScreen
        - unified
          - UnifiedSettingsScreen
        - wizard
          - QuickTestWizard
      - zone
        - ZoneEditorScreen
    - clone
      - client
        - screen
          - CentrifugeScreen
          - NeurocellItemScreen
          - NeurocellLScreen
          - NeurocellMannequinScreen
          - NeurocellScreen
          - TelepadConfigScreen
    - debug
      - client
        - EntityScannerScreen
    - hologram
      - client
        - screen
          - HologramConfigScreen
    - mailbox
      - client
        - screen
          - MailboxComposeScreen
          - MailboxScreen
          - NewsScreen
          - TesterTaskScreen
          - TicketCommentScreen
          - TicketCreateScreen
```

## C) Keybinds (categorie + default key)

```text

```

## D) Openers contestuali (openSafe fuori dal Radial)

```text
- mailbox -> MailboxScreen (com/devmod/mailbox/client/screen/MailboxScreen.java)
- news -> NewsScreen (com/devmod/mailbox/client/screen/NewsScreen.java)
```

## E) Container menus (AbstractContainerMenu)

```text
- CentrifugeMenu -> CentrifugeScreen :: com.devmod.clone.menu (com/devmod/clone/menu/CentrifugeMenu.java)
- NeurocellItemMenu -> NeurocellItemScreen :: com.devmod.clone.menu (com/devmod/clone/menu/NeurocellItemMenu.java)
- NeurocellLMenu -> NeurocellLScreen :: com.devmod.clone.menu (com/devmod/clone/menu/NeurocellLMenu.java)
- NeurocellMannequinMenu -> NeurocellMannequinScreen :: com.devmod.clone.menu (com/devmod/clone/menu/NeurocellMannequinMenu.java)
- NeurocellMenu -> NeurocellScreen :: com.devmod.clone.menu (com/devmod/clone/menu/NeurocellMenu.java)
```

## F) Overlay / HUD / Visualizers (non-screen)

### F1) client/overlay
```text
- com
  - devmod
    - client
      - overlay
        - BossPhaseOverlay
        - CombatFlowHudOverlay
        - CombatRecapScreen
        - CombatSessionTracker
        - ContractHudOverlay
        - DynamicRadiusHudOverlay
        - EconomyOverlay
        - EnduranceQuestOverlay
        - EntityDensityOverlay
        - HeadshotFlashVFX
        - Impact3DPanel
        - Impact3DPanelManager
        - Impact3DRenderer
        - ImpactData
        - ImpactDisplayMode
        - ImpactDpsTracker
        - ImpactEffekseerVFX
        - ImpactHistory
        - ImpactHudContentBuilder
        - ImpactHudController
        - ImpactHudOverlay
        - ImpactHudPresets
        - ImpactHudService
        - ImpactVFX
        - InstanceLoadingOverlay
        - IntegratedTestOverlay
        - NutritionHudOverlay
        - OnboardingOverlay
        - PartyHudOverlay
        - QuestSequenceOverlay
        - QuickHelpOverlay
        - ResonanceHudOverlay
        - SkillEfficacyOverlay
        - StaminaHudOverlay
        - TelemetryStatusOverlay
        - effekseer
          - ComboTracker
          - EffectContext
          - EffectOrchestrator
          - EffectPreset
          - HitWeight
          - package-info
        - vfx
          - GlyphRenderer
          - ImpactVFXConstants
          - LinesRenderer
          - SlashRenderer
          - VortexRenderer
```

### F2) client/rendering
```text
- com
  - devmod
    - client
      - rendering
        - AggroRangeVisualizer
        - BodyPartCalculator
        - BodyPartRenderer
        - ChunkPerformanceVisualizer
        - CustomRenderTypes
        - DebugGeometryBatcher
        - DebugRenderer
        - EntityInfoOverlay
        - HeatmapVisualizer
        - LightLevelOverlay
        - LineOfSightVisualizer
        - MobDebugOverlay
        - PathfindingDebugger
        - RenderEvents
        - RoomBoundsVisualizer
        - SafeSpotVisualizer
        - SpawnabilityOverlay
        - SphereRenderer
        - TrigCache
        - VerticalLevelsVisualizer
        - WorldRenderEvents
        - shader
          - ShaderPipeline
          - ShaderPipelineDiagnostics
          - ShaderRenderTypeConfig
          - VFXShaderRegistry
        - shield
          - EnergyShieldRenderer
          - HexagonalShieldMesh
          - ShieldShaderRegistry
```

### F3) client/ui/overlay
```text
- com
  - devmod
    - client
      - ui
        - overlay
          - OverlayTheme
```

## G) Admin panel (web) - navigazione

```text
- Admin Panel
  - Dashboard (/)
  - Messages (/messages)
  - News (/news)
  - Users (/users)
  - Tasks (/tasks)
  - Tickets (/tickets)
  - Audit (/audit)
  - Config (/config)
```

## H) Verifica per sottosezione (copertura pagine esistenti)

Legenda copertura: **Sì** = esiste una pagina che può sostituire quasi tutta la sottosezione; **Parziale** = esiste una pagina ma copre solo una parte; **No** = non esiste una pagina aggregatrice (solo comandi/toggle).

### H1) Sintesi per sezione Root

| Sezione Root | Pagina dedicata (esistente) | Copertura | Note / Limiti |
|---|---|---|---|
| Home | RadialMenuScreen, WelcomeScreen | Sì | Radial è già l’hub principale; Welcome è onboarding. |
| Config | UnifiedSettingsScreen | Sì | Copre molte categorie config; alcune azioni restano toggle. |
| Testing | TestingHub | Sì | Hub con pannelli e quick tools per sessioni. |
| Telemetry | TelemetryDashboardScreen | Parziale | Dashboard copre Export/Scans/Visualizers; restano Analysis/Dungeon/Reload. |
| Tools | EditorHubScreen | Parziale | Aggrega editor; non copre comandi rapidi. |
| Developer | VoxelLabScreen (e VoxelLabUiTestScreen) | Sì | Lab dedicato (dev‑only). |
| Admin | AdminInstanceScreen + Admin Panel (web) | Parziale | AdminInstance copre solo istanze; admin mail/news/task sono separati. |
| Arena | ArenaTestWizard + RoomBoundsEditorScreen | Parziale | Quick‑test e bounds ok; manca “Arena Hub”. |
| Combat | StaminaSystemEditor | Parziale | Abilita stamina editor; diagnostics/heatmap/impact restano toggle. |
| Debug | (nessun hub) | No | Solo toggle/visualizer/command. |
| Nexus | NexusEditorCentralScreen | Parziale | Hub per aree; non copre access/portals/zones/admin. |
| Play | (nessun hub) | No | Schermate specifiche, manca un “Play Hub”. |

### H2) Dettaglio per sezione

#### Root/Config
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Config/Settings | UnifiedSettingsScreen | Sì | Pagina principale impostazioni. |
| Config/Radial Menu | UnifiedSettingsScreen (categoria Radial) | Sì | |
| Config/Keybinds | UnifiedSettingsScreen (KeybindsPage) / KeyBindsScreen | Sì | Dipende dalla route di apertura. |
| Config/Notifications | NotificationSettingsScreen | Sì | Schermata dedicata. |
| Config/Welcome | WelcomeScreen | Sì | |
| Config/Onboarding/Start, Skip | OnboardingOverlay | No | Overlay, non pagina. |
| Config/Quick Help | QuickHelpOverlay | No | Overlay, non pagina. |
| Config/Endurance | EnduranceSettingsScreen | Sì | |
| Config/Combat/Body Part Detection | UnifiedSettingsScreen | Parziale | Toggle config; non pagina dedicata. |
| Config/Effects/* | UnifiedSettingsScreen | Parziale | Toggle granulari. |
| Config/HUD/* | UnifiedSettingsScreen | Parziale | Toggle granulari. |
| Config/Telemetry/* | UnifiedSettingsScreen | Parziale | Toggle granulari. |
| Config/Game Design/* | (nessuna pagina) | No | Azioni/command. |

#### Root/Testing
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Testing/Hub | TestingHub | Sì | |
| Testing/QA Suite | QATestingScreen | Sì | |
| Testing/QA/Session, Actions, Report | QATestingScreen | Parziale | Funzioni dentro la UI, non pagine separate. |
| Testing/Quick Test Wizard | QuickTestWizard | Sì | |
| Testing/UI Responsiveness | UIResponsivenessTestScreen | Sì | |
| Testing/Badge Tests | BadgeTestScreen | Sì | |
| Testing/HUD/* | (nessuna pagina) | No | Toggle/command. |
| Testing/Panels/* | (nessuna pagina) | No | Toggle/command. |
| Testing/Debug/* | (nessuna pagina) | No | Toggle/command. |
| Testing/Endurance/* | (nessuna pagina) | No | Toggle/command. |

#### Root/Telemetry
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Telemetry/Dashboard/* | TelemetryDashboardScreen | Sì | Ops/Start/Stop/Status in tab Ops. |
| Telemetry/Export/* | TelemetryDashboardScreen (Export tab) | Sì | |
| Telemetry/Scan/* | TelemetryDashboardScreen (Scans tab) | Parziale | Copre scan UI, non tutti i comandi. |
| Telemetry/Analysis/* | (nessuna pagina) | No | Solo command. |
| Telemetry/Dungeon/* | (nessuna pagina) | No | Solo command. |
| Telemetry/Dump/* | (nessuna pagina) | No | Solo command. |
| Telemetry/Admin/Reload | (nessuna pagina) | No | Solo command. |

#### Root/Tools
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Tools/Editors/Editor Hub | EditorHubScreen | Sì | |
| Tools/Item Editor/* | EditorHubScreen | Sì | |
| Tools/Mob Editor/* | EditorHubScreen | Sì | |
| Tools/Commands/* | (nessuna pagina) | No | Gamemode/Time/Weather/Heal via command. |

#### Root/Developer
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Developer/VoxelLab | VoxelLabScreen | Sì | |
| Developer/VoxelLab UI | VoxelLabUiTestScreen | Sì | |

#### Root/Admin
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Admin/Tester Tasks | TesterTaskScreen | Sì | |
| Admin/Mailbox/* | Admin Panel (web) | Parziale | In‑game sono comandi; web ha Messages/Tickets. |
| Admin/News/* | Admin Panel (web) | Parziale | In‑game sono comandi. |
| (non menuPath) Admin Instances | AdminInstanceScreen | Parziale | Esiste ma non è nel radial. |

#### Root/Arena
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Arena/Bounds/Editor | RoomBoundsEditorScreen | Sì | |
| Arena/Quick Test Wizard | ArenaTestWizard | Sì | |
| Arena/Templates/* | (nessuna pagina) | No | Command/utility. |
| Arena/Autosmoke/* | (nessuna pagina) | No | Command/utility. |
| Arena/Force/* | (nessuna pagina) | No | Command/utility. |
| Arena/HUD/* | (nessuna pagina) | No | Toggle/command. |
| Arena/Create, Help, Status | (nessuna pagina) | No | Command/utility. |

#### Root/Combat
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Combat/Abilities/Stamina Editor | StaminaSystemEditor | Sì | |
| Combat/Abilities/Dash, Dodge | (nessuna pagina) | No | Keybind/ability. |
| Combat/Diagnostics/* | (nessuna pagina) | No | Overlay/command. |
| Combat/Heatmaps/* | (nessuna pagina) | No | Visualizer/command. |
| Combat/Impact HUD/* | UnifiedSettingsScreen (HUD) | Parziale | Configurazione, non hub operativo. |

#### Root/Debug
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Debug/AI/* | (nessuna pagina) | No | Toggle/visualizer. |
| Debug/Native/* | (nessuna pagina) | No | Toggle/visualizer. |
| Debug/Overlays/* | (nessuna pagina) | No | Toggle/visualizer. |
| Debug/Perf/* | (nessuna pagina) | No | Toggle/visualizer. |
| Debug/Spatial/* | (nessuna pagina) | No | Toggle/visualizer. |
| Debug/VFX/Screen Shake | (nessuna pagina) | No | Toggle/command. |
| Debug/Commands/* | (nessuna pagina) | No | Command. |
| Debug/Light | (nessuna pagina) | No | Toggle/visualizer. |

#### Root/Nexus
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| (non menuPath) Nexus Editor Central | NexusEditorCentralScreen | Parziale | Hub per aree. |
| Nexus/Access/* | (nessuna pagina) | No | Command/utility. |
| Nexus/Info/* | (nessuna pagina) | No | Command/utility. |
| Nexus/Portals/* | (nessuna pagina) | No | Command/utility. |
| Nexus/Zones/* | (nessuna pagina) | No | Command/utility. |
| Nexus/Admin/* | (nessuna pagina) | No | Command/utility. |

#### Root/Play
| Sottosezione menuPath | Pagina dedicata | Copertura | Note / Limiti |
|---|---|---|---|
| Play/Party | PartyScreen | Sì | |
| Play/Party/Invites | InvitePopupScreen | Parziale | Pop‑up, non hub. |
| Play/Party/Mailbox | MailboxScreen | Sì | |
| Play/Party/Notifications | NotificationCenterScreen | Sì | |
| Play/Party/HUD | (nessuna pagina) | No | Overlay toggle. |
| Play/Endurance/Start, Quest Start, Continue, Exit | EnduranceQuestScreen | Parziale | Schermata runtime; flusso è contestuale. |
| Play/Endurance/Shop | EnduranceShopScreen | Sì | |
| Play/Endurance/Season Pass | SeasonPassScreen | Sì | |
| Play/Quest Flow/* | QuestDeathScreen, QuestCompletionScreen, QuestExitConfirmScreen, PerkSelectionScreen, WaveCheckpointScreen | Sì | Pagine dedicate. |
| Play/Quest Tools/* | QuestEditorScreen, Endurance editor (EditorHub) | Parziale | EditorHub copre Endurance editor. |
| Play/Leaderboards/* | (nessuna pagina) | No | Command/utility. |

Riferimenti principali:
- `devMod/src/main/java/com/devmod/client/ui/screens/TelemetryDashboardScreen.java`
- `devMod/src/main/java/com/devmod/client/ui/hub/TestingHub.java`
- `devMod/src/main/java/com/devmod/client/ui/screens/EditorHubScreen.java`
- `devMod/src/main/java/com/devmod/client/ui/unified/UnifiedSettingsScreen.java`
- `devMod/src/main/java/com/devmod/client/ui/testing/VoxelLabScreen.java`
- `devMod/src/main/java/com/devmod/client/ui/admin/AdminInstanceScreen.java`
- `devMod/src/main/java/com/devmod/client/area/NexusEditorCentralScreen.java`

## I) Proposte pagine hub mancanti (per ridurre il Radial)

Conteggi basati sulle voci `menuPath` attuali (315 totali).

| Hub proposto | Sezioni da assorbire | Voci assorbite (menuPath) | Funzioni chiave | Beneficio |
|---|---|---:|---|---|
| ArenaHubScreen | Root/Arena/* | 25 | Templates list/validate, Autosmoke schedule, Force controls, Bounds editor, HUD toggle | Riduce molte voci Arena in 1 entry. |
| CombatHubScreen | Root/Combat/* | 29 | Abilities summary, diagnostics toggles, heatmap controls, Impact HUD presets | Centralizza toggle combat. |
| DebugHubScreen | Root/Debug/* | 33 | Visualizers on/off, native debug, perf HUD, spatial tools | Sostituisce decine di toggle. |
| PlayHubScreen | Root/Play/* | 28 | Endurance start/continue/exit, Party, Quest Flow, Leaderboards | Unifica flusso “gioco” in un entry. |
| NexusHubScreen | Root/Nexus/* | 36 | Access/Return, Zones map, Portals, Admin actions | Riduce alberatura Nexus. |
| AdminHubScreen | Root/Admin/* + AdminInstanceScreen | 12 | News/Mailbox/Tester tasks + Instances | Unifica admin in‑game; link al web panel. |
| ToolsQuickCommandsScreen | Root/Tools/Commands/* | 6 | Gamemode/Time/Weather/Heal in una pagina | Rimuove comandi rapidi dal radial. |
| DevicesHubScreen | (fuori menuPath) device screens | 0 | Telepad/Hologram/Neurocell/Centrifuge/Scanner + Transport | Accesso unico ai device context‑aware. |

Nota: in alternativa si possono **estendere** pagine esistenti (TelemetryDashboard, EditorHub, TestingHub) per assorbire sottosezioni oggi solo‑command.

## J) Tab esistenti: cosa spostare per ordinare il Radial

### J1) TelemetryDashboardScreen (tabs gia presenti)
Tabs: `OVERLAYS`, `OPS`, `DATA`, `SCANS`, `EXPORT`, `STATS`, `VISUALIZERS`  
Proposta di spostamento:
- **Root/Telemetry/** (37 voci) -> tutte dentro Telemetry Dashboard.
  - `Dashboard/*` -> tab OPS
  - `Export/*` -> tab EXPORT
  - `Scan/*` -> tab SCANS
  - `Analysis/*`, `Dump/*`, `Dungeon/*` -> tab DATA / STATS
  - `Admin/Reload` -> tab OPS
- **Root/Debug/Overlays + alcune Debug/Spatial/AI** -> tab OVERLAYS (gia ci sono i toggle principali).
- **Root/Combat/Heatmaps/Types** -> tab VISUALIZERS (gia presenti i toggle heatmap).
Riduzione potenziale: Telemetry 37 -> 1 entry (solo "Telemetry Dashboard").

### J2) UnifiedSettingsScreen (categorie gia presenti)
Categorie: `GENERAL`, `RADIAL`, `EDITOR`, `DEBUG`, `VISUALIZERS`, `COMBAT`, `MOBS`, `TELEMETRY`, `KEYBINDS`  
Proposta di spostamento:
- **Root/Config/** (58 voci) -> tutte dentro Unified Settings.
  - `Config/Radial Menu` -> RADIAL
  - `Config/Keybinds` -> KEYBINDS
  - `Config/Combat/*` -> COMBAT
  - `Config/Telemetry/*` -> TELEMETRY
  - `Config/Effects/*` + `Config/HUD/*` -> VISUALIZERS/EDITOR (o nuova categoria HUD)
  - `Config/Notifications`, `Config/Welcome`, `Config/Onboarding/*` -> GENERAL
  - `Config/Game Design/*` -> **nuova categoria** "Game Design" (oggi non c'e)
Riduzione potenziale: Config 58 -> 1 entry (solo "Settings").

### J3) ItemEditorScreen (tabs/moduli interni)
Moduli: Weapon, Armor (include Shield), General, Recipe, Usable, Food, Fuel  
Proposta di spostamento:
- **Root/Tools/Item Editor/** (9 voci) -> un solo entry "Item Editor" + scelta tab interna.
Riduzione potenziale: 9 -> 1.

### J4) NotificationCenterScreen (tabs gia presenti)
Tabs: Notifications, Mailbox, News, Tickets, Tasks  
Proposta di spostamento:
- `Root/Play/Party/Mailbox`, `Root/Play/Party/Notifications` -> Notification Center tab corrispondente
- `Root/Admin/Mailbox/*`, `Root/Admin/News/*`, `Root/Admin/Tester Tasks` -> Notification Center tab corrispondente (o link al web admin)
Riduzione potenziale: ~14 voci distribuite -> 1 entry (Notification Center) + opzionale openTab.

### J5) VoxelLabScreen (tabs gia presenti, dev-only)
Tabs: Overview, Debug, HUD, Telemetry, Effects, Combat, Showcase  
Proposta di spostamento:
- **Root/Debug/** (33 voci) -> VoxelLab/Debug
- **Root/Combat/** (29 voci) -> VoxelLab/Combat
- **Root/Config/Effects** (16) + **Root/Config/HUD** (15) -> VoxelLab/Effects + VoxelLab/HUD
- **Root/Config/Telemetry** (4) -> VoxelLab/Telemetry
Riduzione potenziale molto alta, ma solo se accetti che queste funzioni siano in un tool dev.

### J6) DebriefScreen (tabs gia presenti)
Tabs: Overview, Combat, Spatial, Timeline, Party, Report  
Proposta di spostamento:
- Ticket/report post-quest -> tab REPORT (gia contiene campi ticket).
  Questo puo sostituire entry dedicate "Ticket Create" per flussi post-run.

### J7) AreaBuilderScreen (tabs gia presenti)
Tabs: Shape, Dimensions, Materials, Biome, Options  
Proposta di spostamento:
- Tenere **solo** entry "Area Builder" nel Radial; template/snapshot/zone UI sono gia dentro.

### J8) EditorHubScreen (sezioni, non tabs)
Sezioni: Items, Mobs, Quests, World  
Proposta di spostamento:
- Aggiungere sezioni/tab per **Area/Zone/Template/Hologram** cosi puoi togliere voci sparse da Tools/World.

## K) Mapping menuPath -> pagine/tabs (strategia consigliata)

### K1) Strategia A (solo UI esistenti, zero nuove pagine)

| Prefix menuPath | Destinazione | Tab/Categoria | Nota |
|---|---|---|---|
| Root/Config/* | UnifiedSettingsScreen | GENERAL/RADIAL/KEYBINDS/COMBAT/TELEMETRY/VISUALIZERS | Sposta tutta la configurazione in un solo entry. |
| Root/Telemetry/* | TelemetryDashboardScreen | OPS/DATA/SCANS/EXPORT/STATS/VISUALIZERS | Dashboard gia usa ActionIds per comandi telemetry. |
| Root/Testing/* | TestingHub | (pannelli interni) | Hub gia esistente per sessioni e test. |
| Root/Tools/Item Editor/* | ItemEditorScreen | EditorStartTab (Weapon/Armor/General/Recipe/Usable/Food/Fuel) | 9 voci -> 1 entry. |
| Root/Tools/Mob Editor/* | EditorHubScreen | Sezione Mobs | 2 voci -> 1 entry. |
| Root/Admin/Mailbox/* | NotificationCenterScreen | MAILBOX | Admin mailbox dentro Notification Center. |
| Root/Admin/News/* | NotificationCenterScreen | NEWS | News tab gia esistente. |
| Root/Admin/Tester Tasks | NotificationCenterScreen | TASKS | Tab tasks gia esistente. |
| Root/Play/Party/Mailbox | NotificationCenterScreen | MAILBOX | |
| Root/Play/Party/Notifications | NotificationCenterScreen | NOTIFICATIONS | |

**Effetto numerico (Strategia A):**
- Totale menuPath: **315**
- Voci assorbite: **155**
- Voci rimanenti: **160**
  - Restano: Arena (25), Combat (29), Debug (33), Nexus (36), Play (26), Tools (8), Developer (2), Home (1)

### K2) Strategia B (A + VoxelLab come hub tecnico per Debug/Combat)

| Prefix menuPath | Destinazione | Tab |
|---|---|---|
| Root/Debug/* | VoxelLabScreen | DEBUG_OVERLAYS |
| Root/Combat/* | VoxelLabScreen | COMBAT |

**Effetto numerico (Strategia B):**
- Voci assorbite totali: **217**
- Voci rimanenti: **98**
  - Restano: Arena (25), Nexus (36), Play (26), Tools (8), Developer (2), Home (1)

### K3) Strategia C (B + 3 hub nuovi per ripulire il restante)

| Hub nuovo | Assorbe | Voci assorbite |
|---|---|---:|
| ArenaHubScreen | Root/Arena/* | 25 |
| NexusHubScreen | Root/Nexus/* | 36 |
| PlayHubScreen | Root/Play/* | 26 |
| ToolsQuickCommandsScreen | Root/Tools/Commands/* | 6 |

**Effetto numerico (Strategia C):**
- Voci assorbite totali: **310**
- Voci rimanenti: **5**  
  (Home, Developer/VoxelLab, Developer/VoxelLab UI, Tools/Editor Hub, Tools/Mob Editor)

Nota: il residuo `Tools/Mob Editor` puo essere eliminato spostandolo in EditorHub, portando a **4 voci** residue.

## L) Lista modifiche (azioni/menuPath) per applicare Strategia A + hub minimi

### L1) Regole di collasso (batch edit per prefix)
Da applicare nei file che registrano menuPath:
`devMod/src/main/java/com/devmod/actions/client/DevModClientActions.java`  
`devMod/src/main/java/com/devmod/actions/DevModActions.java`  
`devMod/src/main/java/com/devmod/debug/DebugCommand.java`  
`devMod/src/main/java/com/devmod/arena/command/ArenaActionRegistry.java`  
`devMod/src/main/java/com/devmod/telemetry/dashboard/DashboardCommand.java`  
`devMod/src/main/java/com/devmod/telemetry/TelemetryReloadCommand.java`  
`devMod/src/main/java/com/devmod/telemetry/dungeon/DungeonCommand.java`  
`devMod/src/main/java/com/devmod/gametest/TestHarnessCommands.java`  
`devMod/src/main/java/com/devmod/endurance/LeaderboardCommandEvents.java`  
`devMod/src/main/java/com/devmod/mailbox/admin/MailboxCommands.java`

Regole:
- `Root/Config/*` -> **nascondere dal Radial** (menuPath null o visibilityPredicate=false)  
  Rimangono accessibili da `UnifiedSettingsScreen`.
- `Root/Telemetry/*` -> **nascondere dal Radial**  
  Rimangono accessibili da `TelemetryDashboardScreen` (tab OPS/DATA/SCANS/EXPORT/STATS/VISUALIZERS).
- `Root/Testing/*` -> **nascondere dal Radial**  
  Rimangono accessibili da `TestingHub`.
- `Root/Tools/Item Editor/*` -> **nascondere dal Radial**  
  Rimangono accessibili da `ItemEditorScreen` (tab interno).
- `Root/Tools/Mob Editor/*` -> **nascondere dal Radial**  
  Rimangono accessibili da `EditorHubScreen` (sezione Mobs).
- `Root/Admin/Mailbox/*`, `Root/Admin/News/*`, `Root/Admin/Tester Tasks` -> **nascondere dal Radial**  
  Rimangono accessibili da `NotificationCenterScreen` (tab MAILBOX/NEWS/TASKS) o dal pannello web.
- `Root/Play/Party/Mailbox`, `Root/Play/Party/Notifications` -> **nascondere dal Radial**  
  Rimangono accessibili da `NotificationCenterScreen` (tab MAILBOX/NOTIFICATIONS).

### L2) Azioni che restano VISIBILI nel Radial (entry singole)
In `DevModClientActions.java` lasciare visibili (menuPath corto, 1 livello):
- `UI_SETTINGS_OPEN` -> `Root/Settings`
- `UI_TELEMETRY_DASHBOARD_OPEN` -> `Root/Telemetry`
- `UI_TESTING_HUB_OPEN` -> `Root/Testing`
- `UI_EDITOR_HUB_OPEN` -> `Root/Editors`
- `UI_NOTIFICATION_CENTER_OPEN` -> `Root/Comms`
- `UI_PARTY_OPEN` -> `Root/Play` (se non usi PlayHub)
- `UI_WELCOME_OPEN` -> `Root/Help` (opzionale)
- `UI_VOXELLAB_OPEN` -> `Root/Developer` (dev-only)

### L3) Hub nuovi minimi (se accetti 3 nuove schermate)
Azioni da aggiungere (nuove):
- `UI_PLAY_HUB_OPEN` -> `Root/Play`
- `UI_ARENA_HUB_OPEN` -> `Root/Arena`
- `UI_NEXUS_HUB_OPEN` -> `Root/Nexus`
Queste tre entry assorbono **Play (26)**, **Arena (25)**, **Nexus (36)** senza mostrare i loro comandi nel Radial.

## M) Layout Radial finale (2 click)

### M1) Versione consigliata (Strategia A + 3 hub minimi)
Macro -> item (tutti item sono schermate, zero comandi):

```text
Root/Play
  - Play Hub (nuovo)

Root/Arena
  - Arena Hub (nuovo)

Root/Nexus
  - Nexus Hub (nuovo)

Root/Telemetry
  - Telemetry Dashboard

Root/Testing
  - Testing Hub
  - QA Suite (opzionale)

Root/Editors
  - Editor Hub

Root/Settings
  - Settings (Unified)

Root/Comms
  - Notification Center

Root/Developer (dev-only)
  - VoxelLab
```

### M2) Variante ultra‑compatta (se accetti VoxelLab come hub tecnico)
Aggiungi:
- `Debug/Combat/Effects/HUD/Telemetry toggles` -> VoxelLab tabs  
Risultato: quasi tutto il Radial diventa **solo 6–8 voci**.

### M3) Variante zero‑nuove‑UI (solo esistenti)
Togli tutti i comandi/toggle dai menuPath e lascia:
- Settings, Telemetry Dashboard, Testing Hub, Editor Hub, Notification Center, Party/Play  
Radial meno pulito (restano Arena/Nexus/Debug/Combat), ma gia molto ridotto.
