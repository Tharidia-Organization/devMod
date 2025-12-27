# Orphanage Inventory

**Data analisi**: 2025-12-27
**Branch**: Banastaff

## Legenda Tipi Orphan

| Tipo | Descrizione |
|------|-------------|
| 1 | Never referenced - nessuna referenza dal codebase |
| 2 | Dead entrypoint - handler/screen/service non registrato o invocato |
| 3 | Legacy leftover - rimpiazzato da nuovo sistema |
| 4 | Duplicate responsibility - fa la stessa cosa di altro componente |
| 5 | Resource orphan - JSON/lang/texture non usata |
| 6 | Test orphan - test che non testa nulla di reale |
| 7 | Config orphan - config keys mai lette/applicate |

---

## AZIONI NON INTEGRATE NEL RADIAL MENU

| File/Class | Tipo | Evidence | Risk if removed | Decision | Action taken |
|------------|------|----------|-----------------|----------|--------------|
| `UI_NOTIFICATION_CENTER_OPEN` | 2 | Registrato in ActionRegistry (DevModClientActions:565) ma NON in RadialMenuRegistry. Test RadialOrphanFeatureTest fallisce. | MEDIO - Feature funzionante ma non accessibile da radial | **KEEP+INTEGRATE** | Aggiungere a RadialMenuRegistry.buildPlayCategories() |

---

## CLASSI JAVA POTENZIALMENTE ORFANE

### Alta Priorità (Rimozione/Integrazione)

| File/Class | Tipo | Evidence | Risk if removed | Decision | Action taken |
|------------|------|----------|-----------------|----------|--------------|
| `client/panels/tracking/PositionSmoother.java` | 1 | Solo 2 riferimenti (file stesso + docs deprecato). Utility per smoothing posizioni mai usata. | BASSO - nessun uso | **REMOVE** | Rimuovere |
| `client/network/ClientConfigFeedbackPayload.java` | 2 | Solo 5 riferimenti. Non registrato in NetworkHandler. Payload draft non completato. | BASSO | **REMOVE** | Rimuovere |
| `client/panels/context/ContextDetector.java` | 2 | SINGLETON.tick() mai chiamato nel game loop. Sistema context detection non integrato. | MEDIO - feature completa non usata | **KEEP+QUARANTINE** | Deprecare con note |

### Media Priorità (Verificare uso)

| File/Class | Tipo | Evidence | Risk if removed | Decision | Action taken |
|------------|------|----------|-----------------|----------|--------------|
| `client/overlay/WelcomeToastOverlay.java` | 3 | Metodo show() mai invocato. Possibile duplicato con WelcomeScreen/notifiche. | MEDIO | **KEEP+QUARANTINE** | Verificare con sistema notifiche |
| `client/overlay/DynamicRadiusHudOverlay.java` | 2 | Solo 2 riferimenti. Dipende da LightLevelOverlay/SpawnabilityOverlay. | MEDIO | **KEEP** | Utility per altri overlay |
| `client/panels/types/TestProgressPanel.java` | 2 | Solo 4 riferimenti in ContextMode. Sistema FloatingPanel in disuso. | MEDIO | **KEEP+QUARANTINE** | Deprecare con FloatingPanel |
| `client/panels/types/ToolStatusPanel.java` | 2 | Solo 4 riferimenti. Stesso pattern di TestProgressPanel. | MEDIO | **KEEP+QUARANTINE** | Deprecare con FloatingPanel |

### Bassa Priorità (Feature Toggle)

| File/Class | Tipo | Evidence | Risk if removed | Decision | Action taken |
|------------|------|----------|-----------------|----------|--------------|
| `client/overlay/StaminaHudOverlay.java` | 2 | Auto-registrata @EventBusSubscriber. Toggle pubblico non invocato. | ALTO - potrebbe servire | **KEEP** | Dipende da sistema stamina |
| `client/overlay/PartyHudOverlay.java` | 2 | Auto-registrata. Toggle pubblico non invocato. | ALTO | **KEEP** | Dipende da party system |

---

## SCREENS NON RAGGIUNGIBILI

| File/Class | Tipo | Evidence | Risk if removed | Decision | Action taken |
|------------|------|----------|-----------------|----------|--------------|
| `client/testing/BadgeTestScreen.java` | - | FALSO POSITIVO - Aperta da DevModClientActions:532 | - | **KEEP** | - |
| `client/testing/ArenaTestWizard.java` | 2 | Nessun setScreen trovato | BASSO - screen di test | **KEEP+INTEGRATE** | Aggiungere action per aprirla |
| `quest/QuestEditorScreen.java` | 2 | Keybind registrato ma handler mancante | ALTO - feature editor | **KEEP+INTEGRATE** | Aggiungere handler keybind |

---

## KEYBINDS SENZA HANDLER

| Keybind | Tipo | Evidence | Risk if removed | Decision | Action taken |
|---------|------|----------|-----------------|----------|--------------|
| `OPEN_QUEST_EDITOR_KEY` | 2 | Registrato riga 423, handler mancante | ALTO | **KEEP+INTEGRATE** | Aggiungere handler |
| `QUEST_CONTINUE_KEY` | 2 | Registrato riga 427, handler mancante | ALTO | **KEEP+INTEGRATE** | Aggiungere handler |
| `QUEST_EXIT_KEY` | 2 | Registrato riga 428, handler mancante | ALTO | **KEEP+INTEGRATE** | Aggiungere handler |
| `INSPECT_MOB_KEY` | 2 | Registrato riga 435, handler mancante | MEDIO | **KEEP+INTEGRATE** | Aggiungere handler |

---

## RISORSE ORFANE

### Lang Keys (50+ chiavi stimate)

| Categoria | Chiavi | Tipo | Evidence | Decision | Action taken |
|-----------|--------|------|----------|----------|--------------|
| `devmod.achievement.*` | 14 chiavi | 5 | Nessun riferimento Java | **KEEP** | Sistema achievement futuro |
| `devmod.combo.rank.*` | 7 chiavi | 5 | Nessun riferimento Java | **KEEP** | Sistema combo futuro |
| `devmod.currency.*` | 5 chiavi | 5 | Nessun riferimento Java | **KEEP** | Sistema economy futuro |
| `devmod.slider.*` | 20+ chiavi | 5 | Nessun riferimento Java | **REMOVE** | Categoria interamente inutilizzata |
| `devmod.death.*` | 3 chiavi | 5 | Alcune usate, alcune no | **VERIFY** | Controllare uso effettivo |
| `devmod.wave.*` | 3 chiavi | 5 | Nessun riferimento Java | **KEEP** | Sistema wave futuro |

### Textures

| File | Tipo | Evidence | Decision | Action taken |
|------|------|----------|----------|--------------|
| `textures/gui/icons/radial/macro_analyze.png` | 5 | Nessun riferimento | **REMOVE** | Non usata |
| `textures/gui/icons/radial/macro_combat.png` | 5 | Nessun riferimento | **REMOVE** | Non usata |
| `textures/gui/icons/radial/macro_tools.png` | 5 | Nessun riferimento | **REMOVE** | Non usata |
| `textures/gui/icons/radial/macro_play.png` | 5 | Nessun riferimento | **REMOVE** | Non usata |

### Mixin Config

| Entry | Tipo | Evidence | Decision |
|-------|------|----------|----------|
| Tutti i mixin | - | VERIFICATO - Tutti validi | **KEEP** |

---

## FALSI POSITIVI VERIFICATI

Queste classi sembravano orfane ma sono legittime:

| File/Class | Motivo |
|------------|--------|
| `SignatureWeaponEvents.java` | @EventBusSubscriber auto-registrato |
| `RecordBannerOverlay.java` | Usata da ClientOverlayHandlers |
| `SeasonTierUpToastOverlay.java` | Usata da ClientNetworkPayloadHooks |
| `TokenGainOverlay.java` | Usata da ClientOverlayHandlers |
| `HeadshotFlashVFX.java` | Usata da ClientVFXHelper |
| `ClientConfigHandlers.java` | Usato da ClientNetworkPayloadHooks |
| `BadgeTestScreen.java` | Aperta da DevModClientActions:532 |
| `NotificationCenterActionData.java` | Usata da NotificationService |
| `NotificationParamsCodec.java` | Usato per persistence |

---

## STATISTICHE RIEPILOGO

| Categoria | Trovati | KEEP | INTEGRATE | QUARANTINE | REMOVE |
|-----------|---------|------|-----------|------------|--------|
| Azioni non in radial | 1 | 0 | 1 | 0 | 0 |
| Classi Java | 10 | 3 | 0 | 4 | 2 |
| Screens | 2 | 0 | 2 | 0 | 0 |
| Keybinds senza handler | 4 | 0 | 4 | 0 | 0 |
| Lang keys | 50+ | 30+ | 0 | 0 | 20+ |
| Textures | 4 | 0 | 0 | 0 | 4 |
| **TOTALE** | ~70+ | ~33 | 7 | 4 | ~26 |

---

## PROSSIMI STEP

1. **INTEGRAZIONE** (7 items)
   - UI_NOTIFICATION_CENTER_OPEN → RadialMenuRegistry
   - ArenaTestWizard → Action per aprirla
   - 4 keybinds → Aggiungere handlers

2. **QUARANTENA** (4 items)
   - ContextDetector → @Deprecated
   - WelcomeToastOverlay → @Deprecated
   - TestProgressPanel → @Deprecated
   - ToolStatusPanel → @Deprecated

3. **RIMOZIONE** (26+ items)
   - PositionSmoother.java
   - ClientConfigFeedbackPayload.java
   - 4 textures macro_*.png
   - 20+ lang keys slider.*
