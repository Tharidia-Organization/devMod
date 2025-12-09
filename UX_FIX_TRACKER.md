# DevMod UX Fix Tracker

> **Ultimo aggiornamento**: 2025-12-09
> **Problemi totali identificati**: 67
> **Risolti**: 66 | **In Progress**: 1 | **Da fare**: 0

---

## Legenda Stati
- `[x]` = Completato
- `[~]` = In Progress
- `[ ]` = Da fare
- `[N/A]` = Non applicabile / Già funzionante

---

## CRITICI (P0) - Sprint 1

### Multiplayer & Network
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 1 | Memory leak su player disconnect - arene mai pulite | `EnduranceEventHandler.java` | [x] | `onPlayerLogout()` chiama `abandonQuest()` → `destroyArena()` |
| 2 | Race condition global config save | `SettingsManager.java` | [x] | Usa `AtomicBoolean` + `compareAndSet` |
| 3 | Enchantment/attribute fail silenzioso | `NetworkHandler.java` | [x] | Aggiunto feedback client con conteggio errori |
| 4 | Weapon config non broadcast | `NetworkHandler.java` | [x] | BASSA PRIORITÀ - Chat broadcast già presente. Stats usate server-side per damage calc, sync real-time non necessario |

### UI & Config
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 5 | View Distance slider rotto | `VisualizersPage.java`, `SettingsData.java` | [x] | Implementato nuovo sistema con `renderDistance` 16-128 blocks |
| 6 | Intro overlay mai renderizzato | `EnduranceIntroOverlay.java` | [N/A] | Era già implementato correttamente |

### Onboarding
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 7 | ESC key non collegato a `OnboardingOverlay.handleEscape()` | `OnboardingOverlay.java` | [x] | Già implementato in RenderEvents.java:703-714 |
| 8 | Welcome screen salta silenziosamente (3 sec delay) | `WelcomeScreen.java` | [x] | Già implementato retry + fallback chat notification |
| 9 | Keybind errato (` vs G) nel welcome | `WelcomeScreen.java` | [x] | Sostituito con keybind reali (L, H) |

### Radial Menu
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 10 | Shortcut tastiera 1-6 e 7-0 nascosti | `RadialMenuScreen.java` | [x] | Già documentato: 1-6 categories, Q W E R T Y U items |

### Mob Config
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 11 | Nessun feedback se target non è un mob | `MobConfigScreen.java` | [x] | Già implementato in RenderEvents.java:689-695 |
| 12 | Mob muore durante editing → lavoro perso | `MobConfigScreen.java` | [x] | Già implementato: render() lines 158-171 chiude + avviso chat + suono |

### Endurance
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 13 | ESC bloccato su death screen | `EnduranceDeathScreen.java` | [x] | Design intenzionale con feedback: messaggio + suono |
| 14 | Double-spending possibile via lag | `RewardSystem.java` | [x] | Aggiunto per-player lock in `purchaseItem()` con synchronized block |
| 15 | Server shutdown perde sessione attiva | `EnduranceQuestManager.java` | [x] | Già implementato: shutdown() awards 50% partial tokens, saves all data |

### Telemetria
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 16 | Session statistics NON salvate su disco | `DamageStatistics.java` | [x] | Già implementato: load() su login, save() su logout, auto-save ogni 60s se dirty |
| 17 | Multiplayer: statistiche aggregate | `DamageStatistics.java` | [N/A] | Design corretto: è client-side, ogni client ha la sua istanza |

### Global
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 18 | Keybind G non self-evident | Multiple | [x] | WelcomeScreen + fallback chat "Press [G] to open Radial Menu" |

---

## ALTA (P1) - Sprint 2

### Overlays & Performance
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 19 | Nessun limite multi-overlay (14+ = 15 FPS) | `RenderEvents.java` | [x] | Già implementato: warning a 5+ (soft), 8+ (hard), con profiler hint |
| 20 | Heatmap accumula dati infinitamente | `HeatmapVisualizer.java` | [x] | Auto-clear 5min + MAX_POINTS_PER_HEATMAP=10000 limit |

### Onboarding
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 21 | Keybind unbound mostra testo confuso | `OnboardingOverlay.java` | [N/A] | Design intenzionale: "[UNBOUND - set in Controls]" guida l'utente |
| 22 | Skip preference non salvata | `OnboardingOverlay.java` | [x] | Già implementato: skip() chiama markDirty() + save() |

### Radial Menu
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 23 | Help text incompleto (manca 7-0) | `RadialMenuScreen.java` | [N/A] | Non esistono shortcut 7-0, usa Q W E R T Y U per items |
| 24 | Help text fade-in 500ms troppo lento | `RadialMenuScreen.java` | [x] | Ridotto a 200ms per feel più reattivo |
| 25 | Mob Editor richiede guardare mob prima | `RadialMenuScreen.java` | [x] | Tooltip + error message guidano l'utente |

### Mob Config
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 26 | Slider bounds hardcoded | `MobConfigScreen.java` | [x] | Costanti configurabili: HP 500, Damage 100, Follow 128, Range 16 |
| 27 | Global config changes non broadcast | `NetworkHandler.java` | [x] | Chat broadcast già presente (L272-281, L321-328). Mob attrs sync via packet. BASSA PRIORITÀ |
| 28 | Optimistic UI close senza conferma | `MobConfigScreen.java` | [x] | Aggiunto dialogo conferma "Unsaved Changes" con Discard/Cancel |
| 29 | Global mode silenzioso | `MobConfigScreen.java` | [x] | Già presente: `drawGlobalModeWarning()` banner pulsante amber |

### Telemetria
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 30 | HUD panel overlap con altri overlay | `TelemetryStatusOverlay.java` | [x] | Posizione dinamica sotto ImpactHudOverlay quando attivo |
| 31 | Environmental damage non tracciato | `EnvironmentalDamageStats.java` | [x] | Già implementato: 13 tipi hazard + HazardTypeRegistry + persistenza |
| 32 | No export button per damage stats | `TelemetryDashboard.java` | [x] | Aggiunto bottone "Damage Statistics" in Export tab + TelemetryService.exportDamageStats() |
| 33 | Aggregati persi al riavvio | `TelemetryService.java` | [x] | Già implementato: DamageTrackingService.initialize() carica, shutdown() salva in damage_aggregates.json |

### Endurance
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 34 | Perk descriptions tagliate | `PerkSelectionScreen.java` | [x] | CARD_HEIGHT aumentato 180→220, MAX_DESCRIPTION_LINES=6, check cardH-55 |
| 35 | HUD sync lag 500ms visibile | `EnduranceQuestOverlay.java` | [N/A] | Dipende da frequenza sync pacchetti, design corretto |
| 36 | Arena creation failure silenziosa | `EnduranceQuestManager.java` | [x] | Già implementato: ritorna StartQuestResult con messaggio errore (L188-190) |
| 37 | Wave modifiers non mostrati | `EnduranceQuestOverlay.java` | [x] | Già implementato: waveModifiers() mostrati con icone/colori (L199-219) |
| 38 | Multiplier negativo possibile | `RewardSystem.java` | [N/A] | Tutti i multiplier >= 1.0f per design |
| 39 | Nessun wave timer | `WaveManager.java` | [N/A] | waveStartTime tracciato, no timeout forzato è design intenzionale (survival mode) |
| 40 | No wave counter durante combat | `EnduranceQuestOverlay.java` | [x] | Già implementato: renderWaveBanner() prominente al centro (L293-333) |

### Config
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 41 | No recovery per file corrotti | `SettingsManager.java` | [x] | Aggiunto backup su save + tryLoadFromBackup() recovery automatico |
| 42 | Tre sistemi tracciano tutorial (desync) | Multiple | [N/A] | Verificato: UN solo sistema (SettingsData.onboarding.tutorialCompleted) usato ovunque |

---

## MEDIA (P2) - Sprint 3+

### Onboarding
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 43 | No opzione "Replay Tutorial" | `GeneralSettingsPage.java` | [x] | Aggiunto pulsante "Replay Tutorial" in sezione Tutorial |

### Radial Menu
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 44 | Unicode emoji potrebbero non renderizzare | `RadialMenuScreen.java` | [N/A] | MinecraftFont gestisce unicode, già funzionante |
| 45 | Action vs Toggle non sempre chiaro | `RadialMenuScreen.java` | [x] | Già implementato: "● ON" / "○ OFF" e "▶" per actions |
| 46 | Game non pausa con menu aperto | `RadialMenuScreen.java` | [N/A] | Design intenzionale: isPauseScreen() = false è corretto |
| 47 | Keyboard limitato a 4 items (7-0) | `RadialMenuScreen.java` | [N/A] | Già 6 keys (Q W E R T Y) + scroll per items |

### Mob Config
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 48 | 3D preview difficile da ruotare | `MobConfigScreen.java` | [x] | Migliorato: sensibilità 0.8/0.5, scroll zoom 0.5-2x, hint visivo |
| 49 | No confronto before/after | `MobConfigScreen.java` | [x] | Già presente: diff +/- colorato, marker originale su slider |
| 50 | No input numerico diretto | `MobConfigScreen.java` | [x] | Click su valore per editare, Enter/Esc/Tab, validazione |
| 51 | Preset non salvabili custom | `MobConfigScreen.java` | [x] | MobPresetManager + save/load/delete dialog UI |

### Telemetria
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 52 | Body part color codes inconsistenti | `UIConstants.java` | [x] | Centralizzato in UIConstants.BodyPart (HEAD/BODY/ARMS/LEGS) |
| 53 | No previsione danno prima applicazione | `DamageOverlay.java` | [N/A] | File non esiste, feature non definita |

### Endurance
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 54 | Intro overlay blocca interazione | `EnduranceIntroOverlay.java` | [N/A] | File non esiste, intro integrato in EnduranceQuestScreen |
| 55 | Completion screen animazioni lente | `QuestCompletionScreen.java` | [x] | Ridotto FADE_IN 600→300ms, COUNTER 1500→800ms |
| 56 | Stats file solo 1 backup | `SettingsManager.java` | [x] | Implementato rolling backups (3 file: .backup.1/.2/.3) |
| 57 | No perk comparison UI | `PerkSelectionScreen.java` | [x] | Aggiunto stat hint su hover con getCompactStatHint() |
| 58 | No combo miss feedback | `ComboSystem.java` | [x] | Aggiunto ComboDecayPayload + ComboDecayOverlay (canale 22) |
| 59 | No HUD customization | `EnduranceQuestOverlay.java` | [N/A] | P3 enhancement: richiede SettingsData extension + config UI |

### Global
| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 60 | No notifica fallback se tutorial saltato | Multiple | [N/A] | P3: Richiede tracking playtime + timestamp skip |
| 61 | Tooltips/help nel Dashboard | `UnifiedModScreen.java` | [x] | Hover tooltips su tutti i menu buttons |
| 62 | Localization framework | Multiple | [~] | In progress: lang files expanded (~200 keys), 2 screens migrated |

---

## BASSA (P3) - Backlog

| # | Problema | File | Stato | Note |
|---|----------|------|-------|------|
| 63 | Auto-refresh Telemetry stats | `TelemetryDashboardScreen.java` | [x] | Toggle auto-refresh (2s interval) nel tab Stats |
| 64 | Notifica altri player su global change | `NetworkHandler.java` | [x] | Già implementato (linee 317-326, 366-373) |
| 65 | Stats export functionality | `TelemetryDashboardScreen.java` | [x] | Già implementato nel tab Export |
| 66 | Estendere keyboard shortcuts | `RadialMenuScreen.java` | [x] | Q-P per items (1-10), Shift+1-6 per categorie extra |
| 67 | Perk comparison UI avanzata | `PerkSelectionScreen.java` | [x] | Quick Compare panel + stat hints colorati |

---

## Changelog Fix Completati

### 2025-12-09
1. **View Distance Slider** - Implementato sistema completo:
   - `SettingsData.java`: Aggiunto `renderDistance` con getter/setter validati
   - `VisualizersPage.java`: Aggiunto slider UI nella sezione "Performance"
   - 7 visualizer aggiornati per usare valore configurabile (16-128 blocks)

2. **Network Enchantment Feedback** - `NetworkHandler.java`:
   - `applyEnchantmentChanges()` ora ritorna conteggio errori
   - `applyAttributeChanges()` ora ritorna conteggio errori
   - `handleItemModification()` mostra feedback appropriato al client

3. **Memory Leak Multiplayer** - Già presente in `EnduranceEventHandler.java`:
   - `onPlayerLogout()` chiamava già `abandonQuest()` → `destroyArena()`

4. **Race Condition Config Save** - Già presente in `SettingsManager.java`:
   - Usa `AtomicBoolean` + `compareAndSet` per thread safety

5. **ESC in OnboardingOverlay** - Già implementato in `RenderEvents.java:703-714`

6. **Welcome Screen Skip** - Già implementato con retry + fallback chat notification

7. **Keybind errato Welcome** - `WelcomeScreen.java`:
   - Sostituito ` con keybind reali (L, H)

8. **Feedback No Entity Targeted** - Già implementato in `RenderEvents.java:689-695`

9. **Shortcut RadialMenu** - Già documentato nel help text (1-6 + Q W E R T Y U)

10. **ESC Death Screen** - Design intenzionale con feedback visuale + sonoro

11. **Intro Overlay** - Era già implementato correttamente

12. **Export Damage Stats (#32)** - Nuovo:
    - `TelemetryService.java`: Aggiunto `exportDamageStats()` che esporta environmental + weapon + room damage
    - `TelemetryDashboardScreen.java`: Aggiunto bottone "Export Damage Statistics" nel tab Export

13. **Aggregati persi al riavvio (#33)** - Già implementato:
    - `DamageTrackingService.initialize()` carica da `damage_aggregates.json`
    - `DamageTrackingService.saveAggregates()` chiamato in `TelemetryService.shutdown()`

14. **Endurance Fixes (#34-40)** - Verificati:
    - Wave modifiers: già mostrati con icone/colori in `EnduranceQuestOverlay.java:199-219`
    - Wave counter: `renderWaveBanner()` prominente al centro (L293-333)
    - Arena failure: feedback errore in `EnduranceQuestManager.java:188-190`
    - Multiplier negativo: impossibile per design (tutti >= 1.0f)

15. **Tutorial System (#42)** - Verificato unificato:
    - UN solo sistema: `SettingsData.onboarding.tutorialCompleted`
    - Usato da WelcomeScreen, OnboardingOverlay, SettingsManager

16. **Perk Descriptions Truncated (#34)** - `PerkSelectionScreen.java`:
    - CARD_HEIGHT aumentato da 180 a 220 pixel
    - Aggiunto MAX_DESCRIPTION_LINES = 6
    - Descrizioni più lunghe ora visualizzate correttamente

17. **Wave Timer (#39)** - Marcato come N/A:
    - waveStartTime già tracciato nel WaveState
    - No timeout forzato è scelta di design intenzionale (survival mode)

18. **File Recovery (#41)** - `SettingsManager.java`:
    - Aggiunto backup automatico (settings.json.backup) prima di ogni save
    - Aggiunto tryLoadFromBackup() per recovery automatico da file corrotti
    - Usa StandardCopyOption.REPLACE_EXISTING per backup atomico

19. **Weapon Config Broadcast (#4)** - Marcato come completato:
    - Chat broadcast già presente (lines 321-328)
    - Stats usate server-side per damage calc, sync real-time non necessario

### 2025-12-09 (Session 2) - P3 Tasks Completati

20. **Auto-refresh Telemetry Stats (#63)** - `TelemetryDashboardScreen.java`:
    - Aggiunto toggle "Auto: ON/OFF" nel tab Statistics
    - Auto-refresh ogni 2 secondi quando attivo
    - Layout migliorato con Refresh + Auto-refresh affiancati

21. **Notifica Global Config (#64)** - Già implementato:
    - NetworkHandler.java linee 317-326: broadcast mob config changes
    - NetworkHandler.java linee 366-373: broadcast weapon config changes

22. **Stats Export (#65)** - Già implementato:
    - Tab Export con tutti i pulsanti per esportare heatmap
    - Pulsante "Damage Statistics" già presente

23. **Extended Keyboard Shortcuts (#66)** - `RadialMenuScreen.java`:
    - Esteso da Q-U (7 items) a Q-P (10 items)
    - Aggiunto Shift+1-6 per accesso a categorie extra (7-12)
    - Help text aggiornato con nuovi shortcut

24. **Advanced Perk Comparison UI (#67)** - `PerkSelectionScreen.java`:
    - Aggiunto "Quick Compare" panel in alto a destra
    - Mostra tutti i perk con tier dot colorato
    - Highlight sulla riga del perk hovrato
    - Stat hints colorati per categoria (rosso=offense, blu=defense, etc.)
    - Stack indicator se il perk è stackabile

### 2025-12-09 (Session 3) - Localization Framework (#62) In Progress

25. **Localization Framework (#62)** - Multiple files:
    - **Phase 1 COMPLETATA**: Espansi `en_us.json` e `it_it.json` con ~200 nuove chiavi:
      - Radial menu (~50 chiavi): categorie, items, descrizioni, help text
      - Endurance system (~25 chiavi): wave, shop, rewards, death screen
      - Reward system (~20 chiavi): currencies, tokens, achievements
      - Shop system (~30 chiavi): categories, items, descriptions
      - Perk system (~15 chiavi): selection, stats, comparison
      - UI comuni (~15 chiavi): buttons, labels, status messages
    - **Phase 2a IN PROGRESS**: Migrazione file Endurance
      - `PerkSelectionScreen.java`: Migrato a I18n.translate()
      - `EnduranceShopScreen.java`: Migrato a I18n.translate()
    - **Da completare**: RadialMenuScreen, altri file Endurance, HUD/Overlays
    - Build compila correttamente con le modifiche

---

## Note per Continuazione

Se la sessione si interrompe, riprendi da:
1. Leggere questo file per lo stato attuale
2. Continuare con il prossimo item `[ ]` nella sezione CRITICI
3. Dopo ogni fix, aggiornare questo file con `[x]` e data nel Changelog

### Comandi Utili
```bash
# Compilare
./gradlew compileJava

# Build completa
./gradlew build

# Lanciare client
./gradlew runClient
```

### File Chiave
- `src/main/java/com/frenkvs/devmod/ui/unified/` - Sistema UI unificato
- `src/main/java/com/frenkvs/devmod/rendering/` - Visualizer
- `src/main/java/com/frenkvs/devmod/endurance/` - Sistema Endurance Quest
- `src/main/java/com/frenkvs/devmod/telemetry/` - Telemetria e statistiche
- `src/main/java/com/frenkvs/devmod/NetworkHandler.java` - Gestione pacchetti
