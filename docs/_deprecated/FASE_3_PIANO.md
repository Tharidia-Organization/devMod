# Fase 3: Unified Settings Panel System

## Obiettivo
Consolidare tutte le schermate di configurazione in un unico sistema unificato, mantenendo lo stile Axiom esistente e migliorando l'esperienza utente con navigazione fluida e persistenza delle impostazioni.

---

## Analisi Stato Attuale

### Schermate Esistenti da Unificare

| Screen | File | Funzionalità | LOC |
|--------|------|--------------|-----|
| **VoxelLabDashboard** | `ui/VoxelLabDashboard.java` | Dashboard principale con pannelli draggabili | ~550 |
| **UnifiedModScreen** | `UnifiedModScreen.java` | 4 tab (Main, Debug, Combat, Analytics) | ~414 |
| **SettingsScreen** | `SettingsScreen.java` | Settings Mob Viewer (toggle, colori) | ~150 |
| **MobConfigScreen** | `MobConfigScreen.java` | Config stats mob (health, armor, etc.) | ~300 |
| **WeaponEditorScreen** | `WeaponEditorScreen.java` | Editor moltiplicatori armi | ~400 |
| **TelemetryDashboardScreen** | `TelemetryDashboardScreen.java` | Dashboard telemetria | ~200 |

### Problemi Attuali
1. **Frammentazione**: 6+ schermate separate per configurazione
2. **Navigazione confusa**: Utente deve ricordare hotkey diverse
3. **Stile inconsistente**: Alcune screen usano tab, altre pannelli draggabili
4. **Nessuna persistenza**: Impostazioni non salvate tra sessioni
5. **Duplicazione**: VoxelLabDashboard e UnifiedModScreen hanno funzioni sovrapposte

---

## Architettura Proposta

### Struttura Finale
```
com.devmod.ui.unified/
├── UnifiedSettingsScreen.java      # Screen principale con sidebar
├── SettingsCategory.java           # Enum categorie
├── SettingsPage.java               # Interfaccia per pagine
├── pages/
│   ├── GeneralSettingsPage.java    # Impostazioni generali mod
│   ├── DebugOverlaysPage.java      # Toggle overlay debug
│   ├── VisualizersPage.java        # Heatmap e visualizers
│   ├── CombatSettingsPage.java     # Weapon editor, multipliers
│   ├── MobConfigPage.java          # Config stats mob
│   ├── TelemetryPage.java          # Export, analytics
│   └── KeybindsPage.java           # Gestione hotkey
├── components/
│   ├── SettingsSidebar.java        # Sidebar navigazione
│   ├── SettingsHeader.java         # Header con breadcrumb
│   ├── SettingsFooter.java         # Footer con azioni
│   └── SearchBar.java              # Ricerca impostazioni
└── persistence/
    ├── SettingsManager.java        # Gestione salvataggio
    └── SettingsData.java           # POJO per serializzazione
```

---

## Piano Implementazione

### Step 1: Foundation (Struttura Base)
**File da creare:**
- `SettingsCategory.java` - Enum con categorie
- `SettingsPage.java` - Interfaccia base per pagine
- `UnifiedSettingsScreen.java` - Screen principale con layout sidebar+content

**Layout:**
```
┌────────────────────────────────────────────────────────────────┐
│  VOXEL-LAB Settings                              [X] Close     │
├────────────────────────────────────────────────────────────────┤
│ ┌──────────────┐ ┌───────────────────────────────────────────┐ │
│ │ [Search...]  │ │  Category > Subcategory                   │ │
│ ├──────────────┤ ├───────────────────────────────────────────┤ │
│ │ ▸ General    │ │                                           │ │
│ │ ▸ Debug      │ │  [Page Content Area]                      │ │
│ │ ▸ Visualizers│ │                                           │ │
│ │ ▸ Combat     │ │  - Toggle rows                            │ │
│ │ ▸ Mobs       │ │  - Sliders                                │ │
│ │ ▸ Telemetry  │ │  - Color pickers                          │ │
│ │ ▸ Keybinds   │ │  - Buttons                                │ │
│ │              │ │                                           │ │
│ └──────────────┘ └───────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────────┤
│  [Reset All]  [Import]  [Export]           [Apply] [Cancel]    │
└────────────────────────────────────────────────────────────────┘
```

### Step 2: Componenti UI
**File da creare:**
- `SettingsSidebar.java` - Lista categorie con icone e hover
- `SettingsHeader.java` - Breadcrumb + titolo pagina
- `SettingsFooter.java` - Azioni globali (reset, import/export, apply)

**Nuovi componenti AxiomRenderer:**
- `drawSidebarItem()` - Item sidebar con icona
- `drawBreadcrumb()` - Navigazione gerarchica
- `drawSearchField()` - Campo ricerca
- `drawSlider()` - Slider per valori numerici
- `drawColorPicker()` - Picker colore inline

### Step 3: Pagine Configurazione

#### 3.1 GeneralSettingsPage
Migra da: `SettingsScreen.java`
- Overlay HUD toggle
- World Render toggle
- Render Style selector
- View Color picker
- Language selector (futuro)

#### 3.2 DebugOverlaysPage
Migra da: `VoxelLabDashboard.java` (Debug Panel)
- Debug Overlay toggle
- Light Levels toggle
- Line of Sight toggle
- Pathfinding Debug toggle
- Room Bounds toggle

#### 3.3 VisualizersPage
Migra da: `VoxelLabDashboard.java` (Visualizers Panel)
- Heatmap toggles (Movement, Deaths, Stuck, Camping, Aggro Drop, Kiting)
- Vertical Levels toggle
- Safe Spots toggle
- Heatmap opacity slider
- Heatmap resolution selector

#### 3.4 CombatSettingsPage
Migra da: `WeaponEditorScreen.java`
- Weapon multipliers editor
- Body part damage config
- Penetration settings
- Critical hit settings
- Weapon presets

#### 3.5 MobConfigPage
Migra da: `MobConfigScreen.java`
- Mob stats editor (health, armor, damage)
- Attack range config
- Follow range config
- Global vs Instance mode
- Mob presets

#### 3.6 TelemetryPage
Migra da: `TelemetryDashboardScreen.java`
- Export format selector
- Auto-export toggle
- Session stats display
- Data retention settings
- Clear data button

#### 3.7 KeybindsPage
Nuovo:
- Lista tutti i keybind del mod
- Conflitto detection
- Reset to defaults

### Step 4: Persistenza
**File da creare:**
- `SettingsManager.java` - Singleton per load/save
- `SettingsData.java` - POJO con tutti i settings

**File config:** `config/devmod/settings.json`
```json
{
  "version": 1,
  "general": {
    "showOverlay": true,
    "showRender": true,
    "renderAsBlocks": false,
    "followRangeColor": "#00FF00"
  },
  "debug": {
    "debugOverlay": false,
    "lightLevels": false,
    "lineOfSight": false,
    "pathfinding": false,
    "roomBounds": false
  },
  "visualizers": {
    "heatmaps": {
      "movement": false,
      "deaths": false
    },
    "heatmapOpacity": 0.5,
    "verticalLevels": false,
    "safeSpots": false
  },
  "combat": {
    "globalMultipliers": {
      "head": 2.0,
      "body": 1.0,
      "legs": 0.8
    }
  },
  "telemetry": {
    "autoExport": false,
    "exportFormat": "json"
  }
}
```

### Step 5: Integrazione
- Modificare `KeyInputHandler` per aprire `UnifiedSettingsScreen` con K
- Aggiornare `RenderEvents` per usare SettingsManager
- Deprecare vecchie screen (mantenerle come fallback)
- Aggiornare riferimenti in `VoxelLabDashboard`

### Step 6: Polish
- Animazioni transizione pagine (fade)
- Ricerca settings con highlight
- Tooltips informativi
- Undo/Redo per modifiche
- Preview live delle modifiche

---

## File da Modificare

| File | Modifica |
|------|----------|
| `KeyInputHandler.java` | Cambiare target da VoxelLabDashboard a UnifiedSettingsScreen |
| `VoxelLabDashboard.java` | Aggiungere link "Open Full Settings" |
| `UIConstants.java` | Aggiungere costanti per sidebar |
| `AxiomRenderer.java` | Nuovi metodi render (slider, colorpicker, sidebar) |
| `ModConfig.java` | Integrare con SettingsManager |

---

## Stima Effort

| Step | Complessità | File Nuovi | File Modificati |
|------|-------------|------------|-----------------|
| 1. Foundation | Media | 3 | 0 |
| 2. Componenti UI | Media | 4 | 2 |
| 3. Pagine (7) | Alta | 7 | 0 |
| 4. Persistenza | Media | 2 | 1 |
| 5. Integrazione | Bassa | 0 | 4 |
| 6. Polish | Bassa | 0 | 2 |

**Totale:** 16 nuovi file, 9 modifiche

---

## Priorità Implementazione

1. **Must Have (Core)**
   - UnifiedSettingsScreen con sidebar
   - DebugOverlaysPage (già funzionante in VoxelLab)
   - VisualizersPage (già funzionante in VoxelLab)
   - Persistenza base

2. **Should Have (Importante)**
   - CombatSettingsPage
   - MobConfigPage
   - TelemetryPage
   - Search functionality

3. **Nice to Have (Polish)**
   - KeybindsPage
   - Animazioni
   - Undo/Redo
   - Import/Export presets

---

## Note Tecniche

### Compatibilità
- Mantenere `VoxelLabDashboard` come "Quick Access" per toggle rapidi
- `UnifiedSettingsScreen` per configurazione completa
- Hotkey K apre UnifiedSettings, Quick Dashboard accessibile da lì

### Pattern da Usare
- **Singleton** per SettingsManager
- **Strategy** per SettingsPage implementations
- **Observer** per live preview
- **Builder** per SettingsData construction

### Testing
- Verificare persistenza dopo restart
- Verificare tutti i toggle funzionano
- Verificare navigazione sidebar
- Verificare ricerca

---

## Conclusione

La Fase 3 unifica 6+ schermate frammentate in un unico sistema coerente con:
- Navigazione sidebar intuitiva
- Persistenza automatica
- Stile Axiom consistente
- Ricerca rapida
- Import/Export configurazioni

Questo migliora significativamente l'esperienza utente riducendo la complessità di navigazione e fornendo un punto di accesso unico per tutte le configurazioni del mod.
