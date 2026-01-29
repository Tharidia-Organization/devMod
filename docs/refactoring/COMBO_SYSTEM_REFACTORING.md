# ComboSystem Refactoring Analysis

## Executive Summary

Il **ComboSystem** è un sistema critico nel mod che gestisce il tracking delle combo DMC-style durante le quest. Dopo un'analisi approfondita, ho identificato **8 problemi architetturali significativi** che compromettono manutenibilità, testabilità e scalabilità.

**Verdict**: Il sistema necessita di un refactoring strutturale per separare le responsabilità e ridurre l'accoppiamento.

---

## 1. Problemi Identificati

### 1.1 DUAL SESSION STORAGE (Severità: CRITICA)

Le sessioni combo sono memorizzate in **due posti diversi**:

```java
// In ComboSystem.java (linea 27)
private final Map<UUID, ComboSession> activeSessions = new ConcurrentHashMap<>();

// In EnduranceEventCombat.java (linea 37)
static final Map<UUID, ComboSystem.ComboSession> comboSessions = new ConcurrentHashMap<>();
```

**Impatto**:
- Rischio di inconsistenza di stato tra le due mappe
- Memory leak se una mappa viene pulita ma l'altra no
- Difficoltà di debug (quale mappa è la source of truth?)
- Duplicazione di logica per accesso/modifica

**Evidenza** in `EnduranceEventHandler.java:61-62`:
```java
ComboSystem.ComboSession comboSession = ComboSystem.INSTANCE.startSession(playerId, questId);
EnduranceEventCombat.putComboSession(playerId, comboSession); // DUPLICAZIONE!
```

---

### 1.2 GOD CLASS - ComboSession (Severità: SEVERA)

La inner class `ComboSession` (linee 151-632) gestisce **10+ responsabilità**:

| Responsabilità | Campi/Metodi |
|----------------|--------------|
| Combo tracking | `currentCombo`, `maxCombo`, `lastHitTime` |
| Style tracking | `styleScore`, `currentRank`, `highestRank` |
| Flow state | `FlowStateTracker flowState`, `lastFlowState` |
| Announcements | `recentAnnouncements`, `addAnnouncement()` |
| Combat stats | `totalHits`, `totalKills`, `totalDamage` |
| Defensive stats | `perfectDodges`, `parries`, `counterAttacks` |
| Kill timing | `recentKillTimes`, `MULTI_KILL_WINDOW_MS` |
| Grace period | `waveStartTime`, `WAVE_GRACE_PERIOD_MS` |
| Notifications | `pendingComboLost`, `pendingRankChange` |
| Telemetry | chiamate a `EnduranceTelemetryService` |
| Challenge updates | `DailyChallengeManager`, `WeeklyChallengeManager` |
| Config access | `EnduranceConfigManager.INSTANCE` |

**Violazione**: Single Responsibility Principle (SRP)

---

### 1.3 TIGHT COUPLING (Severità: SEVERA)

`ComboSession.registerAction()` (linee 273-366) chiama direttamente:

```java
// Linea 330-331
DailyChallengeManager.INSTANCE.onStyleRankUpdate(playerId, newRank);
DailyChallengeManager.INSTANCE.onComboUpdate(playerId, currentCombo);

// Linea 334-335
WeeklyChallengeManager.INSTANCE.onStyleRankAchieved(playerId, newRank);
WeeklyChallengeManager.INSTANCE.onComboUpdate(playerId, currentCombo);

// Linee 339-348
EnduranceTelemetryService.INSTANCE.recordStyleRankChange(...);
EnduranceTelemetryService.INSTANCE.recordSpecialAction(...);
```

**Impatto**:
- Impossibile testare `ComboSession` senza mockare singleton statici
- Ogni modifica ai challenge/telemetry richiede modifiche a ComboSystem
- Violazione del Dependency Inversion Principle (DIP)

---

### 1.4 ENUMS WITH EMBEDDED LOGIC (Severità: MEDIA)

Gli enum `StyleRank` e `ActionType` contengono business logic:

```java
public enum StyleRank {
    // ... contiene threshold, color, multiplier

    public static StyleRank fromScore(int styleScore, UUID questId) {
        // Logica di lookup config inline!
        EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;
        if (styleScore >= config.getStyleRankSSSThreshold(questId)) return SSS;
        // ...
    }
}
```

**Impatto**:
- L'enum dipende da `EnduranceConfigManager` (coupling)
- Difficile da testare con threshold diversi
- Logica duplicata (metodo con e senza questId)

---

### 1.5 SINGLETON PATTERN ABUSE (Severità: MEDIA)

Quasi ogni classe usa singleton:

| Classe | Pattern |
|--------|---------|
| `ComboSystem` | `INSTANCE` |
| `RewardSystem` | `INSTANCE` |
| `TensionSystem` | `INSTANCE` |
| `MomentumTracker` | `INSTANCE` |
| `ComebackSystem` | `INSTANCE` |
| `FlowStateTracker` | Istanza per sessione (OK) |
| `ClientCombatFlowCache` | `INSTANCE` |

**Impatto**:
- Global mutable state
- Difficoltà di testing
- Ordine di inizializzazione non determinato
- Impossibile avere istanze multiple per testing parallelo

---

### 1.6 NO INTERFACE ABSTRACTION (Severità: MEDIA)

Nessuna interfaccia definita per:
- `IComboSession` - contratto per sessioni
- `IComboSystemEvents` - listener per eventi combo
- `IStyleCalculator` - calcolo punti stile
- `ITelemetryRecorder` - astrazione telemetria

**Impatto**:
- Impossibile creare mock per unit test
- Nessun contratto formale tra componenti
- Difficile sostituire implementazioni

---

### 1.7 TELEMETRY SCATTERED (Severità: BASSA)

Le chiamate telemetry sono sparse in 6+ punti di `ComboSession`:
- `registerAction()` - linee 339-348
- `onDamageTaken()` - linee 436-440
- `awardMilestoneBonus()` - linee 577-582

**Impatto**:
- Difficile tracciare tutti i punti di telemetry
- Rischio di inconsistenza nei dati registrati
- Modifica telemetry richiede scan dell'intera classe

---

### 1.8 CLIENT-SERVER TYPE COUPLING (Severità: BASSA)

`ClientCombatFlowCache` dipende direttamente dai tipi server:
```java
import com.devmod.endurance.ComboSystem;  // Server type
import com.devmod.endurance.FlowStateTracker;  // Server type
```

**Impatto**:
- Client package dipende da server package
- Cambio in ComboSystem richiede rebuild client

---

## 2. Architettura Proposta

### 2.1 Struttura Package

```
com.devmod.endurance.combat/
├── api/
│   ├── IComboSession.java              # Interfaccia sessione
│   ├── IComboEventListener.java        # Observer pattern
│   ├── IStyleCalculator.java           # Strategy per calcolo stile
│   └── ComboEvent.java                 # Eventi type-safe
├── core/
│   ├── ComboSessionImpl.java           # Implementazione base
│   ├── ComboTracker.java               # Solo tracking combo
│   ├── StyleTracker.java               # Solo tracking stile
│   └── CombatStatsTracker.java         # Solo stats combattimento
├── flow/
│   ├── FlowStateTracker.java           # (esistente, OK)
│   └── FlowStateConfig.java            # Configurazione estratta
├── scoring/
│   ├── StyleRankResolver.java          # Risoluzione rank da config
│   ├── ActionPointsCalculator.java     # Calcolo punti per azione
│   └── MultiplierChain.java            # Chain of multipliers
├── events/
│   ├── ComboEventDispatcher.java       # Event bus interno
│   ├── TelemetryComboListener.java     # Observer per telemetry
│   └── ChallengeComboListener.java     # Observer per challenges
├── config/
│   └── ComboSystemConfig.java          # Config estratta
├── network/
│   ├── CombatFlowSyncPayload.java      # (esistente, rinominato)
│   └── ComboStateDTO.java              # DTO per network
└── ComboSystemFacade.java              # Facade pubblica semplificata
```

### 2.2 Diagramma delle Dipendenze (Target)

```
┌─────────────────────────────────────────────────────────────┐
│                     ComboSystemFacade                        │
│  (Single entry point - gestisce lifecycle e routing)         │
└────────────────────────────┬────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  ComboTracker   │  │  StyleTracker   │  │CombatStatsTracker│
│  (combo count)  │  │  (style/rank)   │  │   (hits/kills)   │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
                              ▼
                   ┌─────────────────────┐
                   │ ComboEventDispatcher│
                   │   (Event Bus)       │
                   └──────────┬──────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│TelemetryListener│  │ChallengeListener│  │AnnouncementSvc  │
│ (decoupled)     │  │  (decoupled)    │  │  (decoupled)    │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### 2.3 Interfacce Chiave

```java
// api/IComboSession.java
public interface IComboSession {
    UUID getPlayerId();
    UUID getQuestId();

    // Combo
    int getCurrentCombo();
    int getMaxCombo();
    void incrementCombo();
    void breakCombo();

    // Style
    int getStyleScore();
    StyleRank getCurrentRank();
    StyleRank getHighestRank();

    // Actions
    ActionResult registerAction(ActionType action, float damage);
    void onDamageTaken(float damage);

    // Stats (read-only)
    CombatStats getStats();
}

// api/IComboEventListener.java
public interface IComboEventListener {
    void onComboIncreased(UUID playerId, int newCombo);
    void onComboBreak(UUID playerId, int lostCombo);
    void onRankChange(UUID playerId, StyleRank oldRank, StyleRank newRank);
    void onMilestoneReached(UUID playerId, ActionType milestone, int styleEarned);
}

// api/ComboEvent.java (sealed hierarchy)
public sealed interface ComboEvent {
    UUID playerId();
    long timestamp();

    record ComboIncreased(UUID playerId, long timestamp, int newCombo) implements ComboEvent {}
    record ComboBreak(UUID playerId, long timestamp, int lostCombo, float damage) implements ComboEvent {}
    record RankChanged(UUID playerId, long timestamp, StyleRank oldRank, StyleRank newRank) implements ComboEvent {}
    record MilestoneReached(UUID playerId, long timestamp, ActionType milestone, int styleEarned) implements ComboEvent {}
}
```

### 2.4 Facade Semplificata

```java
// ComboSystemFacade.java
public final class ComboSystemFacade {
    private static ComboSystemFacade instance;

    private final Map<UUID, IComboSession> sessions = new ConcurrentHashMap<>();
    private final ComboEventDispatcher eventDispatcher;
    private final ComboSystemConfig config;

    // Dependency injection via builder o DI container
    public static void initialize(ComboSystemConfig config,
                                   List<IComboEventListener> listeners) {
        instance = new ComboSystemFacade(config, listeners);
    }

    public static ComboSystemFacade get() {
        return Objects.requireNonNull(instance, "ComboSystem not initialized");
    }

    // === Session Management (SINGLE SOURCE OF TRUTH) ===

    public IComboSession startSession(UUID playerId, UUID questId) {
        var session = new ComboSessionImpl(playerId, questId, config);
        sessions.put(playerId, session);
        return session;
    }

    public Optional<IComboSession> getSession(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public IComboSession endSession(UUID playerId) {
        return sessions.remove(playerId);
    }

    // === Event Handlers (delegate to session) ===

    public ActionResult onPlayerAttack(ServerPlayer player, LivingEntity target,
                                        float damage, AttackContext ctx) {
        return getSession(player.getUUID())
            .map(s -> s.registerAction(resolveActionType(ctx), damage))
            .orElse(null);
    }

    // Tick all sessions
    public void tick() {
        sessions.values().forEach(IComboSession::tick);
    }
}
```

---

## 3. Piano di Implementazione

### Fase 1: Preparazione (Low Risk)
**Durata stimata**: 2-3 ore

1. **Creare package `combat/api`**
   - Definire `IComboSession`, `IComboEventListener`, `ComboEvent`
   - Non modifica codice esistente

2. **Creare `ComboEventDispatcher`**
   - Event bus interno con lista di listener
   - Pattern Observer

3. **Estrarre `StyleRankResolver`**
   - Spostare logica da `StyleRank.fromScore()` a classe dedicata
   - Iniettare config invece di usare singleton

### Fase 2: Session Unification (Medium Risk)
**Durata stimata**: 3-4 ore

1. **Creare `ComboSystemFacade`**
   - Single point of entry per tutte le operazioni
   - Unica mappa per le sessioni

2. **Deprecare `EnduranceEventCombat.comboSessions`**
   - Aggiungere `@Deprecated` con javadoc
   - Redirect graduale a Facade

3. **Aggiornare `EnduranceEventHandler`**
   - Usare Facade invece di accesso diretto

### Fase 3: Session Decomposition (High Risk)
**Durata stimata**: 4-6 ore

1. **Estrarre `ComboTracker`**
   - Solo responsabilità: `currentCombo`, `maxCombo`, timeout
   - Unit test dedicati

2. **Estrarre `StyleTracker`**
   - Solo responsabilità: `styleScore`, `currentRank`, decay
   - Usa `StyleRankResolver` iniettato

3. **Estrarre `CombatStatsTracker`**
   - Solo responsabilità: hits, kills, damage, defensive stats

4. **Creare `ComboSessionImpl`**
   - Compone i tracker estratti
   - Implementa `IComboSession`

### Fase 4: Decoupling Telemetry & Challenges (Medium Risk)
**Durata stimata**: 2-3 ore

1. **Creare `TelemetryComboListener`**
   - Implementa `IComboEventListener`
   - Sottoscrive a `ComboEventDispatcher`
   - Rimuove chiamate dirette da session

2. **Creare `ChallengeComboListener`**
   - Gestisce daily/weekly challenge updates
   - Sottoscrive a dispatcher

### Fase 5: Cleanup & Migration (Low Risk)
**Durata stimata**: 1-2 ore

1. **Rimuovere codice deprecato**
   - `EnduranceEventCombat.comboSessions`
   - Metodi duplicati

2. **Aggiornare test**
   - Nuovi unit test per ogni tracker
   - Integration test per Facade

3. **Aggiornare documentazione**

---

## 4. Rischi e Mitigazioni

| Rischio | Probabilità | Impatto | Mitigazione |
|---------|-------------|---------|-------------|
| Regressione combo tracking | Media | Alto | Test unitari estesi pre-refactoring |
| Inconsistenza sessioni durante migrazione | Alta | Medio | Feature flag per switch graduale |
| Performance degradation | Bassa | Medio | Profiling prima/dopo |
| Breaking network protocol | Media | Alto | Mantenere `CombatFlowSyncPayload` compatibile |

---

## 5. Metriche di Successo

### Prima del Refactoring:
- `ComboSession`: ~480 linee, 10+ responsabilità
- Test coverage: difficile da misurare (singleton)
- Coupling: 5+ dipendenze dirette a singleton

### Dopo il Refactoring (Target):
- Classi < 200 linee ciascuna
- Single responsibility per classe
- 0 dipendenze dirette a singleton (injection)
- Test coverage > 80% per ogni tracker
- Separation client/server types

---

## 6. Appendice: File Impattati

### File da Modificare:
1. `ComboSystem.java` - Refactoring completo
2. `FlowStateTracker.java` - Estrazione config
3. `EnduranceEventCombat.java` - Rimozione mappa
4. `EnduranceEventHandler.java` - Uso Facade
5. `EnduranceEventTick.java` - Uso Facade
6. `CombatFlowSyncPayload.java` - Uso interfaccia
7. `RewardSystem.java` - Uso interfaccia

### Nuovi File:
1. `combat/api/IComboSession.java`
2. `combat/api/IComboEventListener.java`
3. `combat/api/ComboEvent.java`
4. `combat/core/ComboSessionImpl.java`
5. `combat/core/ComboTracker.java`
6. `combat/core/StyleTracker.java`
7. `combat/core/CombatStatsTracker.java`
8. `combat/events/ComboEventDispatcher.java`
9. `combat/events/TelemetryComboListener.java`
10. `combat/events/ChallengeComboListener.java`
11. `combat/scoring/StyleRankResolver.java`
12. `ComboSystemFacade.java`

---

## 7. Conclusioni

Il ComboSystem attuale funziona ma presenta debiti tecnici significativi che rallentano lo sviluppo e rendono difficile il testing. Il refactoring proposto:

1. **Risolve il dual storage** con una Facade centralizzata
2. **Decompone la God Class** in tracker specializzati
3. **Decoupla le dipendenze** con event-driven architecture
4. **Abilita testing** con interfacce mockabili

Il refactoring può essere eseguito incrementalmente senza breaking changes, permettendo validazione graduale.

---

*Documento generato da Claude Code - Analisi del 29/01/2026*
