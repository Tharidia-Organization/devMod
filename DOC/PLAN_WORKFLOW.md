# Analisi Feature VOXEL-LAB per NeoForge 1.21.1

Basandomi sull'analisi del progetto esistente e considerando i vincoli di performance per server con 100-200 giocatori, ecco la classificazione delle feature richieste:

## ✅ IMPLEMENTABILI (Alta Priorità)

### Gruppo A1: Tool di Visualizzazione Base (REQ-A)
- [x] **M1 - Punti di Stuck [HEATMAP]** - Già implementato in `StuckEvents.java`
- [x] **REQ-A1 - Anteprima Hitbox "Ghost"** - Parzialmente implementato (rendering sfere aggro/attack)
- [x] **REQ-A2 - Visualizzatore Raggio Aggro e Leash** - Già implementato in `WorldRenderEvents.java`
- [ ] **REQ-A3 - Overlay Skill AoE (Telegraphing)** - Richiede integrazione con sistema skill
- [x] **REQ-A4 - Debugger NavMesh/Pathfinding** - Già implementato (path rendering in `StuckEvents.java`)
- [ ] **REQ-A5 - Anchor Point Visualizer** - Rendering semplice di BlockPos
- [ ] **REQ-A6 - Visualizzatore Linee di Vista (LoS)** - Raycasting client-side, basso impatto
- [ ] **REQ-A7 - Visualizzatore Livelli Verticali** - Overlay client-side per fasce Y

### Gruppo A2: Metriche Base Comportamento Mob
- [x] **M2 - Perdita Aggro Anomala [HEATMAP] [TH FEATURES]** - Tracciabile via eventi aggro esistenti
- [ ] **M3 - Reset del Boss (Leash Fail) [TH FEATURES]** - Logging eventi leash, basso overhead
- [ ] **M4 - Kiting Path Boss [HEATMAP] [TH FEATURES]** - Estensione path tracking esistente
- [ ] **M5 - Tempo Transizione Fasi Boss [TH FEATURES]** - Timestamp su cambio fase, overhead minimo
- [ ] **M6 - Efficacia dei Minion [TH FEATURES]** - Tracking vita/danni via eventi damage
- [ ] **M8 - Spawn Falliti o Anomali [TH FEATURES]** - Validazione spawn point, check una tantum

### Gruppo A3: Metriche Geometria Base
- [ ] **M9 - Heatmap Morti [HEATMAP] [TH FEATURES]** - Logging coordinate morte, aggregazione offline
- [ ] **M10 - Heatmap Movimento [HEATMAP] [TH FEATURES]** - Sampling posizione player ogni N tick (configurabile)
- [ ] **M14 - Punti di Caduta Parkour [HEATMAP] [TH FEATURES]** - Evento caduta + coordinate
- [ ] **M16 - Direzione Sguardo all'Ingresso [TH FEATURES]** - Capture yaw/pitch su trigger region
- [ ] **M17 - Tempo Idle (Disorientamento) [TH FEATURES]** - Tracking velocità = 0 + movimento camera

### Gruppo A4: Metriche Combattimento Essenziali
- [ ] **M30 - Danno Out per Colpo [TH FEATURES]** - Hook eventi damage, già presente sistema tracking
- [ ] **M31 - Storico Danno per Arma/Tipo [TH FEATURES]** - Aggregazione dati M30
- [ ] **M33 - Danno In per Stanza [TH FEATURES]** - Tracking damage + region check
- [ ] **M34 - Time To Kill (TTK) [TH FEATURES]** - Timestamp spawn → morte mob
- [ ] **M35 - Durata Fight per Stanza [TH FEATURES]** - Timer primo colpo → ultimo mob morto
- [ ] **M37 - Stato Vitali a Fine Fight [TH FEATURES]** - Snapshot HP post-combat

### Gruppo A5: Flusso e Meta-Game
- [ ] **M41 - Esito Run Dungeon [TH FEATURES]** - Flag completamento/abbandono/wipe
- [ ] **M42 - Choke Point (Stanza del Quit) [TH FEATURES]** - Logging ultima stanza + motivo uscita
- [ ] **M43 - Sequenza Stanze Percorse [TH FEATURES]** - Array ordinato ID stanze visitate
- [ ] **M44 - Interazione con Loot [TH FEATURES]** - Eventi apertura chest + pickup item
- [ ] **M46 - Puzzle Solving [TH FEATURES]** - Timer + contatore tentativi su trigger puzzle

---

## ⚠️ DIFFICILI DA IMPLEMENTARE (Richiedono Ottimizzazione)

### Gruppo B1: Metriche Performance-Intensive
- [ ] **M10 - Heatmap Movimento [HEATMAP]** - **Sampling aggressivo**: max 1 sample/secondo per player, storage compresso
- [ ] **M11 - Safe Spot/Camping [HEATMAP]** - Richiede tracking posizione + danno output, filtro movimento < threshold
- [ ] **M18 - Lunghezza Linee di Vista (LoS)** - Raycasting ripetuto costoso, limitare a sample periodici
- [ ] **M23 - Superfici Calpestate vs Mai Usate [HEATMAP]** - Tracking blocchi calpestati, richiede chunked storage
- [ ] **M27 - Light Level & Spawnability Map [HEATMAP]** - Scan light level costoso, pre-calcolo offline consigliato
- [ ] **M28 - Superfici Spawn-valid/Spawn-blocking** - Classificazione blocchi, pre-calcolo + cache

### Gruppo B2: Tool Visualizzazione Complessi
- [ ] **REQ-A8 - Traffic/Heatmap Overlay in Editor** - Richiede sistema persistence + rendering overlay denso
- [ ] **REQ-A9 - Trigger & Region Visualizer** - Integrazione con plugin region (WorldGuard/GriefPrevention)
- [ ] **REQ-A10 - Coverage & Safe-Spot Visualizer** - Dipende da M11, rendering dati aggregati

### Gruppo B3: Metriche Combattimento Avanzate
- [ ] **M7 - Skill Whiff del Boss** - Tracking AoE + hit detection, richiede integrazione skill system
- [ ] **M32 - Hit/Miss Ratio** - Conteggio colpi sparati vs landed, overhead su armi rapide
- [ ] **M36 - Distanza di Ingaggio** - Calcolo distanza per ogni hit, aggregazione necessaria
- [ ] **M38 - Utilizzo Skill/Spell** - Tracking cast skill, dipende da architettura skill system
- [ ] **M39 - Efficacia Skill/Spell** - Correlazione skill → damage → targets, complesso
- [ ] **M40 - Cure per Stanza** - Tracking healing events, multiple sorgenti

### Gruppo B4: Metriche Navigazione Avanzate
- [ ] **M19 - Uso Percorsi Verticali** - Identificazione tipo blocco (scala/rampa) + tracking utilizzo
- [ ] **M20 - Distribuzione Altezze Giocate** - Bucketing coordinate Y + tempo trascorso
- [ ] **M21 - Densità Traffico in Collo di Bottiglia** - Identificazione chokepoint + conteggio player simultanei
- [ ] **M22 - Frequenza Salti e Uso Blocchi Arrampicabili** - Evento salto + block interaction tracking
- [ ] **M24 - Tipologia Blocchi Pavimento sul Path** - Sampling blocchi sotto player su path principale
- [ ] **M25 - Contrasto Illuminazione Path vs Laterali** - Analisi light level differenziale
- [ ] **M26 - Landmark Osservati** - Raycast direzione sguardo + tempo fisso su target

### Gruppo B5: Meta-Game Complesso
- [ ] **M45 - Uso del Loot nelle Stanze Successive** - Tracking equipaggiamento + correlazione temporale
- [ ] **M47 - Backtracking** - Rilevamento ritorno in stanze già visitate, richiede grafo stanze
- [ ] **M48 - Uso Checkpoint/Respawn** - Tracking checkpoint + correlazione con morti
- [ ] **M49 - Scoperta e Uso Shortcut/Secret** - Flagging aree segrete + tracking discovery
- [ ] **M50 - Zone Mai Attraversate** - Inversione M23, richiede mappa completa dungeon

---

## ❌ DA SCARTARE (Infattibili o Troppo Costose)

### Gruppo C1: Performance Killer per Server Multiplayer

**M12 - Altezza Player Anomala**
- **Motivo**: Richiede tracking continuo Y coordinate durante combat + correlazione con geometria stanza
- **Impatto**: Overhead costante per ogni player in combat, difficile definire "anomala" senza pre-mapping stanza
- **Alternativa**: Usare M20 (Distribuzione Altezze) con soglie configurabili

**M13 - Collisioni Invisibili**
- **Motivo**: Richiede hook a livello motion engine per rilevare velocity = 0 su barrier block
- **Impatto**: Evento ad altissima frequenza (ogni tick movimento), impossibile distinguere da stop volontario
- **Alternativa**: Manual reporting via comando debug quando player si blocca

**M15 - Trigger Falliti**
- **Motivo**: Richiede tracking "quasi-trigger" (player vicino ma non dentro region)
- **Impatto**: Calcolo distanza da tutte le region per ogni player ogni tick
- **Alternativa**: Logging trigger effettivamente attivati + manual review posizionamento

**M29 - Eventi su Confini di Chunk/Region [HEATMAP]**
- **Motivo**: Correlazione eventi (stuck/reset/trigger) con coordinate chunk boundaries
- **Impatto**: Overhead di calcolo chunk boundary per ogni evento + storage spaziale complesso
- **Alternativa**: Post-processing offline dei dati M1/M2/M15 con overlay chunk grid

### Gruppo C2: Metriche Client-Side Non Tracciabili Server

**M51 - Performance per Stanza (TPS/MSPT)**
- **Motivo**: TPS è globale server, non per-stanza; MSPT breakdown richiede profiler invasivo
- **Impatto**: Impossibile isolare performance di singola stanza senza profiler dedicato
- **Alternativa**: Spark/Timings profiler esterno + correlazione manuale con stanze attive
- **Note**: Tracciabile solo a livello globale server, non granulare per stanza

**M52 - Entity Count per Stanza**
- **Motivo**: Conteggio entità per region fattibile MA...
- **Impatto**: Su server 100-200 player con multiple istanze dungeon, overhead significativo
- **Alternativa**: Snapshot periodici (ogni 30s) invece di tracking real-time
- **Classificazione**: Spostabile in "Difficili" se implementato con sampling

**M53 - Light Updates**
- **Motivo**: Richiede hook al lighting engine di Minecraft, non esposto via API
- **Impatto**: Impossibile senza core mod o mixin invasivi
- **Alternativa**: Pre-analisi statica illuminazione in editor, evitare light updates dinamici

**M54 - Densità Entità Decorative**
- **Motivo**: Classificazione "decorativa" vs "funzionale" ambigua
- **Impatto**: Conteggio tile entities fattibile, ma "peso" soggettivo
- **Alternativa**: Tool esterno (MCEdit/WorldEdit) per analisi statica pre-deploy

**M55 - Latenza Media Giocatori in Fight Critici**
- **Motivo**: Ping player disponibile, ma correlazione con "fight critici" richiede definizione eventi
- **Impatto**: Basso se limitato a snapshot su boss fight trigger
- **Classificazione**: Spostabile in "Implementabili" se limitato a eventi specifici

**M56 - Block Update Rate**
- **Motivo**: Richiede hook a block update events, altissima frequenza
- **Impatto**: Overhead insostenibile su server con redstone/fluidi attivi
- **Alternativa**: Profiler esterno (Timings) + design guideline "no redstone in combat areas"

**M57 - FPS Budget Stimato (Costo Rendering)**
- **Motivo**: Calcolo client-side, server non ha accesso a rendering pipeline
- **Impatto**: Impossibile da server, richiederebbe client mod
- **Alternativa**: Tool esterno (Optifine debug, F3) + manual review

**M58 - FPS Client Medi per Zona**
- **Motivo**: Richiede client mod per reporting FPS al server
- **Impatto**: Invasivo, privacy concerns, non fattibile vanilla
- **Alternativa**: Survey volontario player + correlazione manuale con zone

---

## 🔧 REQUISITI TECNICI PER IMPLEMENTAZIONE

### Infrastruttura Necessaria

#### 1. Sistema di Persistence
```java
// Necessario per storage metriche
- Database embedded (SQLite) o file-based (JSON compresso)
- Async write per evitare lag server
- Rotation automatica dati (retention policy)
- Export formato CSV/JSON per analisi esterna
```

#### 2. Sistema di Sampling Configurabile
```java
// Config per bilanciare dettaglio vs performance
- Sampling rate per metrica (es: M10 ogni 20 tick invece di ogni tick)
- Whitelist/blacklist stanze per tracking
- Enable/disable runtime via comando
- Buffer size e flush interval configurabili
```

#### 3. Sistema di Aggregazione
```java
// Processing dati per heatmap
- Bucketing spaziale (grid 1x1x1 blocchi)
- Aggregazione temporale (sessioni, giorni, settimane)
- Calcolo statistiche (media, mediana, percentili)
- Generazione heatmap overlay per visualizzazione
```

#### 4. Integrazione Region/Stanza
```java
// Prerequisito per metriche "per stanza"
- Sistema identificazione stanze (WorldGuard regions, custom)
- Mapping player → stanza corrente
- Eventi enter/exit stanza
- Metadata stanza (tipo, difficoltà, boss)
```

### Dipendenze Esterne Consigliate

- **WorldGuard/GriefPrevention**: Gestione region per identificazione stanze
- **MythicMobs**: Sistema boss/skill per metriche M5-M7
- **PlaceholderAPI**: Esposizione metriche real-time
- **Spark/Timings**: Profiling performance (alternativa a M51-M56)

---

## 🎯 ROADMAP IMPLEMENTAZIONE CONSIGLIATA

### Fase 1: Foundation (Settimane 1-2)
- [x] Sistema persistence (SQLite async) [TH FEATURES]
- [ ] Config sampling rates
- [ ] Eventi base (morte, damage, movimento) [TH FEATURES]
- [ ] Integrazione region system [TH FEATURES]

### Fase 2: Metriche Core (Settimane 3-4)
- [ ] M9 - Heatmap Morti [TH FEATURES]
- [ ] M30-M31 - Damage tracking [TH FEATURES]
- [ ] M34-M35 - TTK e durata fight [TH FEATURES]
- [ ] M41-M43 - Flusso run [TH FEATURES]

### Fase 3: Visualizzazione (Settimane 5-6)
- [ ] REQ-A5 - Anchor points
- [ ] REQ-A6 - LoS visualizer
- [ ] REQ-A7 - Livelli verticali
- [ ] REQ-A8 - Heatmap overlay (dati Fase 2)

### Fase 4: Metriche Avanzate (Settimane 7-8)
- [ ] M10 - Heatmap movimento (sampling ottimizzato) [TH FEATURES]
- [ ] M11 - Safe spots [TH FEATURES]
- [ ] M23 - Superfici calpestate [TH FEATURES]
- [ ] Sistema aggregazione e export [TH FEATURES]

### Fase 5: Ottimizzazione e Tuning (Settimane 9-10)
- [ ] Load testing 100-200 player
- [ ] Tuning sampling rates
- [ ] Ottimizzazione query database
- [ ] Documentazione e training

---

## 🗺️ ROADMAP DETTAGLIATA IMPLEMENTAZIONE - FEATURE IMPLEMENTABILI

### 📦 GRUPPO A1: Tool Visualizzazione Base (DevMod Standalone)

#### REQ-A3 - Overlay Skill AoE (Telegraphing)
- [ ] Sistema registrazione skill AoE
  - [ ] Interface `IAoESkill` con metodi `getShape()`, `getRadius()`, `getAngle()`
  - [ ] Registry skill AoE per boss/mob custom
- [ ] Rendering overlay client-side
  - [ ] Render cerchi AoE a terra (CircleRenderer)
  - [ ] Render coni AoE (ConeRenderer)
  - [ ] Render linee AoE (LineRenderer)
  - [ ] Sistema colori configurabile (warning/danger)
- [ ] Integrazione con sistema skill esistente
  - [ ] Hook eventi pre-cast skill
  - [ ] Packet server→client per sync preview AoE

#### REQ-A5 - Anchor Point Visualizer
- [ ] Sistema tracking anchor points
  - [ ] Data structure `AnchorPoint` (pos, type, phase, mobId)
  - [ ] Command `/devmod anchor add <type> [phase]`
  - [ ] Command `/devmod anchor remove <radius>`
  - [ ] Command `/devmod anchor list`
- [ ] Rendering anchor points
  - [ ] Render marker 3D per spawn point (verde)
  - [ ] Render marker 3D per teleport point (ciano)
  - [ ] Render marker 3D per phase anchor (giallo)
  - [ ] Label con info fase/tipo
- [ ] Persistence anchor points
  - [ ] Save/load da file JSON per world
  - [ ] Import/export anchor configuration

#### REQ-A6 - Visualizzatore Linee di Vista (LoS)
- [ ] Sistema raycasting LoS
  - [ ] Raycast da mob a player (max 64 blocchi)
  - [ ] Raycast da player a target (on-demand)
  - [ ] Cache risultati raycast (TTL 10 tick)
- [ ] Visualizzazione LoS
  - [ ] Render linea verde se LoS libera
  - [ ] Render linea rossa se LoS bloccata
  - [ ] Render punto impatto su blocco ostruente
  - [ ] Toggle visualizzazione con keybind
- [ ] Debug info LoS
  - [ ] Overlay distanza LoS
  - [ ] Lista blocchi ostruenti
  - [ ] Angolo visuale mob→player

#### REQ-A7 - Visualizzatore Livelli Verticali
- [ ] Sistema definizione fasce Y
  - [ ] Config fasce Y per stanza (pavimento, mid, alto)
  - [ ] Auto-detect fasce da geometria stanza
  - [ ] Command `/devmod levels set <y1> <y2> <y3>`
- [ ] Rendering fasce verticali
  - [ ] Overlay bande colorate per fascia Y
  - [ ] Trasparenza configurabile
  - [ ] Toggle per fascia (show/hide pavimento, mid, alto)
- [ ] Analisi utilizzo fasce
  - [ ] Tracking tempo player per fascia
  - [ ] Heatmap verticale (quale fascia più usata)
  - [ ] Export dati utilizzo fasce

---

### 📊 GRUPPO A2: Metriche Comportamento Mob (TH Features Integration)

#### M3 - Reset del Boss (Leash Fail) [TH FEATURES]
- [ ] Event listener leash
  - [ ] Hook `EntityEvent` per leash reset
  - [ ] Capture posizione reset
  - [ ] Capture distanza da spawn point
- [ ] Logging reset eventi
  - [ ] Timestamp reset
  - [ ] Boss ID e tipo
  - [ ] Coordinate reset
  - [ ] Player più vicino al momento reset
- [ ] Packet a TH Features per persistence

#### M4 - Kiting Path Boss [HEATMAP] [TH FEATURES]
- [ ] Estensione path tracking esistente
  - [ ] Flag "isBoss" in path tracking
  - [ ] Sampling posizione boss ogni 20 tick durante combat
  - [ ] Detect movimento circolare (kiting pattern)
- [ ] Analisi pattern movimento
  - [ ] Calcolo "spin rate" (rotazioni/minuto)
  - [ ] Detect stuck in corner
  - [ ] Heatmap posizioni boss durante fight
- [ ] Packet a TH Features per persistence

#### M5 - Tempo Transizione Fasi Boss [TH FEATURES]
- [ ] Sistema tracking fasi boss
  - [ ] Interface `IBossPhases` con metodi `getCurrentPhase()`, `getPhaseThresholds()`
  - [ ] Event `BossPhaseChangeEvent` (pre/post)
  - [ ] Registry boss con fasi
- [ ] Logging transizioni
  - [ ] Timestamp inizio/fine fase
  - [ ] HP threshold fase
  - [ ] Durata fase in tick
  - [ ] Player attivi durante fase
- [ ] Packet a TH Features per persistence

#### M6 - Efficacia dei Minion [TH FEATURES]
- [ ] Tracking minion lifecycle
  - [ ] Event spawn minion (timestamp, boss parent)
  - [ ] Event morte minion (timestamp, killer, lifetime)
  - [ ] Tracking damage dealt da minion
- [ ] Metriche minion
  - [ ] Vita media minion per tipo
  - [ ] Damage totale inflitto
  - [ ] Numero minion vivi contemporaneamente (peak)
  - [ ] Kill/Death ratio minion
- [ ] Packet a TH Features per persistence

#### M8 - Spawn Falliti o Anomali [TH FEATURES]
- [ ] Validazione spawn point
  - [ ] Check blocco solido a coordinate spawn
  - [ ] Check void (Y < min world height)
  - [ ] Check area raggiungibile (pathfinding test)
- [ ] Logging spawn falliti
  - [ ] Coordinate spawn tentato
  - [ ] Motivo fallimento (solid/void/unreachable)
  - [ ] Mob type
  - [ ] Spawner source (se disponibile)
- [ ] Packet a TH Features per persistence
- [ ] Alert in-game per admin
  - [ ] Chat message con coordinate cliccabili
  - [ ] Particle effect su spawn point fallito

---

### 🗺️ GRUPPO A3: Metriche Geometria Base (TH Features Integration)

#### M9 - Heatmap Morti [HEATMAP] [TH FEATURES]
- [ ] Event listener morte player
  - [ ] Hook `LivingDeathEvent` per player
  - [ ] Capture coordinate morte (X, Y, Z)
  - [ ] Capture causa morte (mob, caduta, lava, etc.)
  - [ ] Capture stanza/region se disponibile
- [ ] Aggregazione dati morti
  - [ ] Bucketing coordinate (grid 1x1x1)
  - [ ] Conteggio morti per blocco
  - [ ] Conteggio morti per causa
- [ ] Packet a TH Features per persistence
- [ ] Visualizzazione heatmap
  - [ ] Command `/devmod heatmap deaths [radius]`
  - [ ] Render particelle rosse intensità proporzionale
  - [ ] Overlay numerico morti per blocco

#### M10 - Heatmap Movimento [HEATMAP] [TH FEATURES]
- [ ] Sampling posizione player
  - [ ] Tick event ogni 20 tick (1/secondo)
  - [ ] Capture coordinate player (X, Y, Z)
  - [ ] Filter player in creative/spectator
  - [ ] Filter player AFK (no movimento > 30s)
- [ ] Aggregazione movimento
  - [ ] Bucketing coordinate (grid 1x1x1)
  - [ ] Conteggio sample per blocco
  - [ ] Calcolo "desire lines" (path più frequenti)
- [ ] Packet a TH Features per persistence (batched ogni 5s)
- [ ] Visualizzazione heatmap
  - [ ] Command `/devmod heatmap movement [radius]`
  - [ ] Render particelle blu intensità proporzionale
  - [ ] Overlay "autostrade" movimento

#### M14 - Punti di Caduta Parkour [HEATMAP] [TH FEATURES]
- [ ] Event listener caduta
  - [ ] Hook `LivingFallEvent`
  - [ ] Filter cadute > 3 blocchi
  - [ ] Capture coordinate atterraggio
  - [ ] Capture altezza caduta
- [ ] Analisi cadute parkour
  - [ ] Detect cadute ripetute stesso punto (>3 in 5min)
  - [ ] Correlazione con morte da caduta
  - [ ] Identificazione "salto difficile"
- [ ] Packet a TH Features per persistence
- [ ] Visualizzazione punti caduta
  - [ ] Command `/devmod heatmap falls [radius]`
  - [ ] Render marker arancione per cadute frequenti

#### M16 - Direzione Sguardo all'Ingresso [TH FEATURES]
- [ ] Event listener ingresso stanza
  - [ ] Hook region enter event
  - [ ] Capture yaw/pitch player primi 3 secondi
  - [ ] Sampling ogni 10 tick per 60 tick
- [ ] Analisi direzione sguardo
  - [ ] Calcolo direzione media guardata
  - [ ] Raycast direzione → identificazione target guardato
  - [ ] Confronto con landmark previsti
- [ ] Packet a TH Features per persistence
- [ ] Visualizzazione analisi
  - [ ] Command `/devmod gaze analyze <region>`
  - [ ] Render frecce direzione media sguardo
  - [ ] Heatmap "cosa guardano i player"

#### M17 - Tempo Idle (Disorientamento) [TH FEATURES]
- [ ] Tracking stato idle
  - [ ] Detect velocità player = 0 per > 5 secondi
  - [ ] Detect movimento camera (yaw/pitch change)
  - [ ] Exclude AFK (no input > 2 minuti)
- [ ] Logging idle zones
  - [ ] Coordinate zona idle
  - [ ] Durata idle
  - [ ] Movimento camera durante idle (disorientamento)
- [ ] Packet a TH Features per persistence
- [ ] Visualizzazione idle zones
  - [ ] Command `/devmod heatmap idle [radius]`
  - [ ] Render marker giallo per zone confuse

---

### ⚔️ GRUPPO A4: Metriche Combattimento (TH Features Integration)

#### M30 - Danno Out per Colpo [TH FEATURES]
- [ ] Event listener damage output
  - [ ] Hook `LivingDamageEvent` (attacker = player)
  - [ ] Capture danno pre-mitigation
  - [ ] Capture danno post-mitigation
  - [ ] Capture tipo danno (fisico, fuoco, magia, etc.)
  - [ ] Capture arma/item usato
  - [ ] Capture target (mob type, HP prima/dopo)
- [ ] Packet a TH Features per persistence

#### M31 - Storico Danno per Arma/Tipo [TH FEATURES]
- [ ] Aggregazione dati M30
  - [ ] Group by arma/item
  - [ ] Group by tipo danno
  - [ ] Calcolo statistiche (totale, media, min, max)
  - [ ] Conteggio colpi per arma
  - [ ] Conteggio kill per arma
- [ ] Query system per analisi
  - [ ] Command `/devmod stats weapon <item>`
  - [ ] Command `/devmod stats damage <type>`
  - [ ] Export CSV statistiche armi
- [ ] Dipende da TH Features per storage

#### M33 - Danno In per Stanza [TH FEATURES]
- [ ] Event listener damage input
  - [ ] Hook `LivingDamageEvent` (target = player)
  - [ ] Capture danno ricevuto
  - [ ] Capture attacker (mob type)
  - [ ] Capture stanza/region corrente
- [ ] Aggregazione danno per stanza
  - [ ] Danno totale per stanza
  - [ ] Danno medio per player
  - [ ] Picchi burst damage
  - [ ] Fonte danno principale per stanza
- [ ] Packet a TH Features per persistence

#### M34 - Time To Kill (TTK) [TH FEATURES]
- [ ] Tracking lifecycle mob
  - [ ] Event spawn mob (timestamp)
  - [ ] Event morte mob (timestamp)
  - [ ] Calcolo TTK = morte - spawn
  - [ ] Capture killer (player/team)
- [ ] Aggregazione TTK
  - [ ] TTK medio per mob type
  - [ ] TTK per fase boss
  - [ ] TTK min/max/mediana
- [ ] Packet a TH Features per persistence

#### M35 - Durata Fight per Stanza [TH FEATURES]
- [ ] Tracking fight lifecycle
  - [ ] Detect inizio fight (primo damage dealt in stanza)
  - [ ] Detect fine fight (ultimo mob morto)
  - [ ] Calcolo durata fight
- [ ] Metriche fight
  - [ ] Durata media per stanza
  - [ ] Durata per difficoltà
  - [ ] Correlazione durata ↔ esito (win/wipe)
- [ ] Packet a TH Features per persistence

#### M37 - Stato Vitali a Fine Fight [TH FEATURES]
- [ ] Snapshot HP post-combat
  - [ ] Event fine fight (ultimo mob morto)
  - [ ] Capture HP residui tutti player in stanza
  - [ ] Capture HP percentuale
  - [ ] Capture pozioni/cure usate durante fight
- [ ] Analisi stress fight
  - [ ] HP medio residuo (indica difficoltà)
  - [ ] Numero player sotto 30% HP
  - [ ] Numero morti durante fight
- [ ] Packet a TH Features per persistence

---

### 🎮 GRUPPO A5: Flusso e Meta-Game (TH Features Integration)

#### M41 - Esito Run Dungeon [TH FEATURES]
- [ ] Tracking run lifecycle
  - [ ] Event inizio run (ingresso dungeon)
  - [ ] Event fine run (completamento/abbandono/wipe)
  - [ ] Timestamp inizio/fine
  - [ ] Durata totale run
- [ ] Classificazione esito
  - [ ] Completato (boss finale morto)
  - [ ] Abbandonato (player usciti volontariamente)
  - [ ] Wipe (tutti player morti)
- [ ] Packet a TH Features per persistence

#### M42 - Choke Point (Stanza del Quit) [TH FEATURES]
- [ ] Tracking abbandoni
  - [ ] Event player leave dungeon
  - [ ] Capture ultima stanza visitata
  - [ ] Capture motivo uscita (quit/death/timeout)
  - [ ] Capture tempo trascorso in stanza
- [ ] Aggregazione choke points
  - [ ] Conteggio abbandoni per stanza
  - [ ] Percentuale completamento per stanza
  - [ ] Identificazione "stanza killer"
- [ ] Packet a TH Features per persistence

#### M43 - Sequenza Stanze Percorse [TH FEATURES]
- [ ] Tracking percorso player
  - [ ] Event enter/exit stanza
  - [ ] Array ordinato ID stanze visitate
  - [ ] Timestamp ingresso/uscita per stanza
  - [ ] Durata permanenza per stanza
- [ ] Analisi percorsi
  - [ ] Percorso più comune
  - [ ] Stanze skippate frequentemente
  - [ ] Stanze opzionali percepite come obbligatorie
- [ ] Packet a TH Features per persistence

#### M44 - Interazione con Loot [TH FEATURES]
- [ ] Event listener loot
  - [ ] Hook chest open event
  - [ ] Hook item pickup event
  - [ ] Capture item raccolto/ignorato
  - [ ] Capture stanza loot
- [ ] Tracking utilizzo loot
  - [ ] Detect item equipaggiato
  - [ ] Detect item venduto/droppato
  - [ ] Tempo tra pickup e utilizzo
- [ ] Analisi reward value
  - [ ] Percentuale loot raccolto vs ignorato
  - [ ] Item più desiderati
  - [ ] Correlazione loot ↔ progressione
- [ ] Packet a TH Features per persistence

#### M46 - Puzzle Solving [TH FEATURES]
- [ ] Sistema tracking puzzle
  - [ ] Interface `IPuzzle` con metodi `onAttempt()`, `onSolve()`, `onFail()`
  - [ ] Registry puzzle per stanza
  - [ ] Event puzzle attempt/solve/fail
- [ ] Metriche puzzle
  - [ ] Tempo medio risoluzione
  - [ ] Numero tentativi errati
  - [ ] Percentuale risoluzione
  - [ ] Puzzle mai risolti
- [ ] Packet a TH Features per persistence

---

### 🔧 INFRASTRUTTURA DEVMOD (Standalone)

#### Sistema Config Avanzato
- [ ] Config per sampling rates
  - [ ] Config sampling movimento (default 20 tick)
  - [ ] Config sampling LoS (default 40 tick)
  - [ ] Config buffer size eventi (default 100)
  - [ ] Config flush interval (default 5s)
- [ ] Config per visualizzazione
  - [ ] Toggle per ogni tipo overlay
  - [ ] Colori configurabili per ogni metrica
  - [ ] Trasparenza overlay
  - [ ] Render distance per overlay
- [ ] Config per performance
  - [ ] Whitelist/blacklist stanze per tracking
  - [ ] Max player tracked simultaneamente
  - [ ] Enable/disable per metrica

#### Sistema Comandi Debug
- [ ] Command `/devmod toggle <feature>`
  - [ ] Enable/disable feature runtime
  - [ ] Feedback stato feature
- [ ] Command `/devmod visualize <type> [params]`
  - [ ] Visualizza overlay specifico
  - [ ] Parametri: radius, duration, filter
- [ ] Command `/devmod export <metrica> <format>`
  - [ ] Export dati in CSV/JSON
  - [ ] Richiede integrazione TH Features per metriche storage
- [ ] Command `/devmod clear <metrica>`
  - [ ] Clear cache/buffer metrica
  - [ ] Reset visualizzazione

#### Sistema Packet Custom
- [ ] Packet `PathRenderPayload` (già esistente)
- [ ] Packet `AoEPreviewPayload`
  - [ ] Sync preview AoE skill server→client
- [ ] Packet `HeatmapDataPayload`
  - [ ] Sync dati heatmap per visualizzazione
- [ ] Packet `AnchorPointPayload`
  - [ ] Sync anchor points server→client
- [ ] Packet `LoSDebugPayload`
  - [ ] Sync risultati raycast LoS

#### Sistema Integrazione TH Features
- [ ] Interface `ITharidiaFeaturesAPI`
  - [ ] Metodo `sendMetricData(MetricType, JsonObject)`
  - [ ] Metodo `queryMetricData(MetricType, QueryParams)`
  - [ ] Check disponibilità TH Features mod
- [ ] Fallback locale se TH Features assente
  - [ ] Warning log "TH Features not found, metrics disabled"
  - [ ] Visualizzazione funziona comunque (no persistence)
- [ ] Packet bridge DevMod → TH Features
  - [ ] Batching eventi per ridurre overhead
  - [ ] Async send per non bloccare main thread

---

## ⚡ CONSIDERAZIONI PERFORMANCE CRITICHE

### Limiti Server 100-200 Player

**Budget Tick Disponibile**
- Target: 20 TPS (50ms/tick)
- Budget telemetria: MAX 5ms/tick (10% overhead)
- Con 200 player: 0.025ms per player per tick

**Strategie Mitigazione**
1. **Sampling Spaziale**: Traccia solo player in dungeon attivi
2. **Sampling Temporale**: 1 sample ogni 20 tick (1/secondo) invece di ogni tick
3. **Async Processing**: Write database in thread separato
4. **Batching**: Accumula eventi e flush ogni 5 secondi
5. **Chunked Storage**: Comprimi coordinate in grid 1x1 invece di float precisi

**Red Flags da Evitare**
- ❌ Raycasting ogni tick per ogni player
- ❌ Pathfinding simulation client-side
- ❌ Block iteration in raggio > 16 blocchi
- ❌ Sync database write nel main thread
- ❌ Tracking eventi ad altissima frequenza (block updates, particle spawn)

---