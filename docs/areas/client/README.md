# Client Package

> Ultimo aggiornamento: 2025-12-30

Architettura del lato client di DevMod: UI, overlay, rendering e input.

---

## Overview

Il package `client` è il più grande di DevMod con **367 file Java**. Gestisce tutto ciò che il giocatore vede e con cui interagisce:

- **Schermate e menu** — Settings, editor, wizard
- **Overlay HUD** — Informazioni in tempo reale durante il gioco
- **Rendering debug** — Visualizzatori per sviluppo e testing
- **Input** — 40+ keybind personalizzabili
- **Notifiche** — Toast e centro notifiche
- **Effetti visivi** — Trail armi, screen shake, shader

---

## Entry Point

```java
// DevModClient.java
public class DevModClient {
    public static void init() {
        // 1. Registra il bridge UI
        ClientUiBridge.register(new ClientUiBridgeImpl());

        // 2. Registra handler network
        ClientNetworkPayloadHooks.register();

        // 3. Registra keybind
        KeyInputHandler.register();

        // 4. Carica settings
        SettingsManager.load();
    }
}
```

---

## Organizzazione Gerarchica

```
com.devmod.client/
│
├── DevModClient.java          # Entry point
├── ClientUiBridgeImpl.java    # Implementazione bridge
│
├── ui/                        # 213 file — SCHERMATE E COMPONENTI
│   ├── unified/               # Sistema settings unificato
│   ├── screens/               # Schermate specializzate
│   ├── editor/                # Item editor
│   ├── radial/                # Menu radiale
│   ├── wizard/                # Wizard guidati
│   ├── hub/                   # Testing hub
│   └── components/            # Widget riutilizzabili
│
├── overlay/                   # 28 file — HUD E OVERLAY
│   ├── ImpactHudOverlay.java  # HUD principale combattimento
│   ├── StaminaHudOverlay.java # Barra stamina
│   └── ...                    # Altri 26 overlay
│
├── rendering/                 # 27 file — DEBUG E VISUALIZZATORI
│   ├── DebugRenderer.java     # Hub centrale debug
│   ├── visualizers/           # Visualizzatori specifici
│   └── shader/                # Pipeline shader
│
├── input/                     # Gestione keybind
│   └── KeyInputHandler.java   # 40+ keybind
│
├── network/                   # 9 file — Handler payload client
├── panels/                    # 11 file — Pannelli 3D floating
├── notification/              # 9 file — Sistema notifiche
├── effects/                   # 4 file — VFX e screen shake
├── endurance/                 # 14 file — UI quest endurance
├── combat/                    # 3 file — Trail armi, tooltip
├── party/                     # 5 file — UI party
├── telemetry/                 # 6 file — Metriche client
└── compat/                    # 4 file — Compatibilità mod
```

---

## Il Menu Radiale (Tasto G)

Il punto di accesso principale a tutte le funzionalità DevMod.

```
                    ┌─────────────┐
                    │   Settings  │
                    └─────────────┘
                          │
     ┌────────────────────┼────────────────────┐
     │                    │                    │
┌─────────┐        ┌─────────────┐      ┌──────────┐
│  Debug  │        │ RADIAL MENU │      │  Editor  │
│ Toggles │◀───────│   (G key)   │─────▶│  Tools   │
└─────────┘        └─────────────┘      └──────────┘
     │                    │                    │
     └────────────────────┼────────────────────┘
                          │
                    ┌─────────────┐
                    │   Quest &   │
                    │   Party     │
                    └─────────────┘
```

Da qui puoi aprire:
- **Settings** — Tutte le configurazioni
- **Item Editor** — Modifica stats armi/armature
- **Mob Config** — Configura mob specifici
- **Testing Hub** — Tool di QA
- **Debug Overlays** — Visualizzatori vari
- **Quest/Endurance** — Gestione quest
- **Party** — Gestione gruppo

---

## Sistema UI Unificato

### SettingsScreen

Una schermata settings con tab per categoria:

| Tab | Contenuto |
|-----|-----------|
| General | Impostazioni generali mod |
| Combat | Body part detection, damage display |
| Debug Overlays | Abilita/disabilita visualizzatori |
| Visualizers | Configurazione visualizzatori |
| Telemetry | Privacy, export dati |
| Keybinds | Personalizzazione tasti |
| Mob Config | Link a mob config |
| Editor | Link a item editor |

### Persistenza

```java
// Automatica su chiusura schermata
SettingsManager.save();

// Manuale
SettingsManager.load();
SettingsManager.reset();
```

Salvato in: `config/devmod/client_settings.json`

---

## Sistema Overlay

### Impact HUD (principale)

Mostra informazioni combattimento in tempo reale:

```
┌──────────────────────────────────────┐
│  Target: Zombie                       │
│  ████████████████░░░░ 75%            │  ← Vita target
│                                       │
│  Last Hit: 12.5 dmg (HEAD)           │  ← Ultimo colpo
│  DPS: 8.2                            │  ← Danno per secondo
│                                       │
│  Combo: 5x                           │  ← Contatore combo
└──────────────────────────────────────┘
```

### Altri Overlay

| Overlay | Mostra |
|---------|--------|
| StaminaHudOverlay | Barra stamina per abilità |
| ResonanceHudOverlay | Tier risonanza combattimento |
| PartyHudOverlay | Stato membri party |
| BossPhaseOverlay | Fase boss attuale |
| QuestSequenceOverlay | Progresso quest |
| EnduranceQuestOverlay | Wave corrente, kill, tempo |
| ContractHudOverlay | Contratti attivi |
| EconomyOverlay | Economia in-game |

### Attivazione

```java
// Da codice
ImpactHudService.setEnabled(true);

// Da keybind (configurabile in settings)
// Default: vari tasti F1-F12

// Da radial menu
// Settings → Debug Overlays → toggle
```

---

## Sistema Rendering Debug

### DebugRenderer (Hub)

Centralizza tutti i visualizzatori debug:

```java
public class DebugRenderer {
    // Toggle singoli visualizzatori
    public static boolean pathfindingEnabled;
    public static boolean lineOfSightEnabled;
    public static boolean roomBoundsEnabled;
    public static boolean heatmapEnabled;
    // ... altri

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (pathfindingEnabled) PathfindingDebugger.render(event);
        if (lineOfSightEnabled) LineOfSightVisualizer.render(event);
        // ...
    }
}
```

### Visualizzatori Disponibili

| Visualizzatore | Cosa Mostra | Uso |
|----------------|-------------|-----|
| PathfindingDebugger | Path dei mob | Debug AI |
| LineOfSightVisualizer | Linee visione mob | Debug AI |
| AggroRangeVisualizer | Range aggro | Balancing |
| RoomBoundsVisualizer | Confini stanze | Level design |
| HeatmapVisualizer | Heatmap movimento/morti | Analytics |
| LightLevelOverlay | Livelli luce | Spawn check |
| SpawnabilityOverlay | Dove spawnano mob | Lighting |
| SafeSpotVisualizer | Zone sicure | Balancing |
| ChunkPerformanceVisualizer | Performance chunk | Optimization |

---

## Sistema Input

### KeyInputHandler

40+ keybind organizzati per categoria:

```java
// Accesso primario
RADIAL_MENU = KeyMapping("key.devmod.radial", G)

// Configurazione
SETTINGS = KeyMapping("key.devmod.settings", ...)
ITEM_EDITOR = KeyMapping("key.devmod.editor", ...)
MOB_CONFIG = KeyMapping("key.devmod.mob_config", ...)

// Debug (molti di default non assegnati)
TOGGLE_PATHFINDING = KeyMapping("key.devmod.pathfinding", ...)
TOGGLE_LOS = KeyMapping("key.devmod.los", ...)
TOGGLE_HEATMAP = KeyMapping("key.devmod.heatmap", ...)

// Abilità
DASH = KeyMapping("key.devmod.dash", LEFT_SHIFT)
DODGE = KeyMapping("key.devmod.dodge", LEFT_ALT)

// Comunicazione
NOTIFICATION_CENTER = KeyMapping("key.devmod.notifications", N)
MAILBOX = KeyMapping("key.devmod.mailbox", M)
```

### Handler Input

```java
@SubscribeEvent
public static void onKeyInput(InputEvent.Key event) {
    if (RADIAL_MENU.consumeClick()) {
        Minecraft.getInstance().setScreen(new RadialMenuScreen());
    }
    if (DASH.consumeClick()) {
        NetworkHandler.send(AbilityActionPayload.dash());
    }
    // ...
}
```

---

## Sistema Notifiche

### Flusso

```
Server invia UnifiedNotificationPayload
         ↓
ClientNotificationManager.onNotificationReceived()
         ↓
Check preferenze (muted? priority threshold?)
         ↓
UnifiedToastOverlay.show() — toast in alto a destra
         ↓
Se sendToMailbox → salva per NotificationCenterScreen
```

### Toast

Notifiche temporanee in alto a destra:

```
┌─────────────────────────────┐
│ 🏆 Achievement Unlocked!    │
│    First Blood              │
│    [Click to view]          │
└─────────────────────────────┘
```

### Notification Center

Schermata con storico notifiche (tasto N):

```
┌─────────────────────────────────────────┐
│         NOTIFICATION CENTER              │
├─────────────────────────────────────────┤
│ ● Quest Complete — Wave 5 finished      │
│ ● Achievement — 100 kills reached       │
│ ○ Party Invite — John invited you       │
│ ○ System — Server restarting in 5 min   │
└─────────────────────────────────────────┘
  ● = non letta   ○ = letta
```

---

## Pannelli 3D Floating

Pannelli che fluttuano nel mondo 3D, ancorati a entità o posizioni.

### Tipi

| Tipo | Descrizione |
|------|-------------|
| CombatPanel | Stats durante combattimento |
| EntityInfoPanel | Info su entità target |
| DamagePanel | Breakdown danni |

### Uso

```java
// Crea pannello su entità
FloatingPanelManager.create(
    PanelType.ENTITY_INFO,
    targetEntity,
    new EntityInfoContent(entity)
);

// Rimuovi
FloatingPanelManager.remove(panelId);

// Rimuovi tutti
FloatingPanelManager.clear();
```

---

## Effetti Visivi

### Screen Shake

```java
// Trigger shake
ShakeManager.shake(
    intensity,    // 0.0 - 1.0
    durationMs,   // millisecondi
    ShakeType.IMPACT
);
```

### Weapon Trails

Trail colorati che seguono le armi durante gli attacchi:

```java
// Configurato in settings
// Abilitato automaticamente per armi con stats custom
```

### VFX Flash

```java
// Flash rosso su headshot
HeadshotFlashVFX.trigger();
```

---

## Network Handlers

### ClientNetworkPayloadHooks

Dispatcher centrale per payload dal server:

```java
public class ClientNetworkPayloadHooks {
    public static void register() {
        // Config
        PayloadHandler.register(ConfigSyncPayload.class,
            ClientConfigHandlers::handle);

        // Combat
        PayloadHandler.register(ImpactPayload.class,
            ClientImpactHandlers::handle);

        // Shield
        PayloadHandler.register(ShieldUpdatePayload.class,
            ClientShieldHandlers::handle);

        // Party
        PayloadHandler.register(PartyUpdatePayload.class,
            ClientPartyHandlers::handle);

        // Endurance
        PayloadHandler.register(WaveUpdatePayload.class,
            ClientEnduranceHandlers::handle);
    }
}
```

---

## Pattern Architetturali

### 1. Bridge Pattern
```java
// Server code può richiedere UI senza dipendere da client classes
ClientUiBridge.get().openSettings();
// Su dedicated server → no-op
// Su client → apre schermata
```

### 2. Event-Driven
```java
// Input, rendering, tick tutti via NeoForge events
@SubscribeEvent
public static void onRenderOverlay(RenderGuiLayerEvent.Post event) {
    ImpactHudOverlay.render(event.getGuiGraphics());
}
```

### 3. Manager Singleton
```java
// Ogni sistema ha un manager centrale
FloatingPanelManager.getInstance()
SettingsManager.getInstance()
ClientNotificationManager.getInstance()
```

### 4. Component Riutilizzabili
```java
// Widget UI condivisi
CountdownTimer timer = new CountdownTimer(10, this::onComplete);
add(timer);
```

---

## Come Aggiungere un Nuovo Overlay

### 1. Crea la classe

```java
public class MioOverlay {
    private static boolean enabled = false;

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void render(GuiGraphics graphics) {
        if (!enabled) return;

        // Rendering code
        graphics.drawString(font, "Ciao!", 10, 10, 0xFFFFFF);
    }
}
```

### 2. Registra nell'event handler

```java
// In un event subscriber
@SubscribeEvent
public static void onRenderGui(RenderGuiLayerEvent.Post event) {
    MioOverlay.render(event.getGuiGraphics());
}
```

### 3. Aggiungi toggle in settings

```java
// In SettingsData
public boolean mioOverlayEnabled = false;

// In SettingsScreen
addToggle("Mio Overlay",
    () -> settings.mioOverlayEnabled,
    v -> {
        settings.mioOverlayEnabled = v;
        MioOverlay.setEnabled(v);
    }
);
```

### 4. (Opzionale) Aggiungi keybind

```java
// In KeyInputHandler
public static final KeyMapping TOGGLE_MIO = new KeyMapping(
    "key.devmod.mio_overlay",
    InputConstants.UNKNOWN.getValue(),
    "key.categories.devmod"
);

// Nel handler
if (TOGGLE_MIO.consumeClick()) {
    MioOverlay.setEnabled(!MioOverlay.isEnabled());
}
```

---

## Dipendenze

- Minecraft Client API — GUI, rendering
- NeoForge Client Events — input, render hooks
- LWJGL/OpenGL — shader, rendering avanzato
- `com.devmod.network` — payload handling
- `com.devmod.bridge` — server-client communication
