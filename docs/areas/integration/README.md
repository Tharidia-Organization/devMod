# Integration System

> Ultimo aggiornamento: 2025-12-30

Come DevMod si integra con altri mod senza creare dipendenze rigide.

---

## Il Problema

Minecraft ha centinaia di mod popolari. DevMod vuole funzionare bene con molti di essi:
- **Pehkui** cambia la dimensione delle entità → serve sapere la scala per calcolare i danni
- **Better Combat** modifica il sistema di combattimento → serve leggere i dati degli attacchi
- **Distant Horizons** ottimizza il rendering → serve notificarlo quando creiamo dimensioni temporanee
- **LittleTiles** aggiunge blocchi custom → serve inizializzare handler nelle nostre dimensioni

**Ma** non vogliamo che DevMod **richieda** questi mod. Se un giocatore non ha Pehkui, DevMod deve funzionare lo stesso.

---

## La Soluzione: Reflection-based Integration

Invece di importare direttamente le classi di altri mod (che causerebbe crash se il mod non c'è), usiamo **reflection** per:

1. Controllare se il mod è presente
2. Caricare le sue classi a runtime
3. Chiamare i suoi metodi dinamicamente

Se il mod non c'è, le funzioni semplicemente ritornano valori di default.

---

## Struttura Package

```
com.devmod.integration/
├── ModIntegrationManager.java     # Hub centrale - coordina tutto
├── BetterCombatIntegration.java   # Legge dati combattimento
├── PehkuiIntegration.java         # Legge scala entità
├── DistantHorizonsIntegration.java # Notifica dimensioni dinamiche
├── LittleTilesIntegration.java    # Inizializza handler animazioni
└── PufferfishIntegration.java     # Mappa attributi tra mod
```

---

## ModIntegrationManager

Il "cervello" del sistema. Gestisce tutto da un punto centrale.

### Cosa Fa

1. **Al boot del server**: Rileva quali mod sono installati
2. **Fornisce API sicure**: Altri sistemi DevMod chiamano qui, mai direttamente le integrazioni
3. **Gestisce errori**: Se qualcosa va storto, logga e continua

### Come Usarlo

```java
// Invece di chiamare PehkuiIntegration direttamente:
float scale = ModIntegrationManager.getPehkuiScale(entity);
// Ritorna 1.0f se Pehkui non è installato

// Verificare presenza mod:
if (ModIntegrationManager.isBetterCombatLoaded()) {
    String attackName = ModIntegrationManager.getBetterCombatAttackName(player);
}
```

### Logging

All'avvio vedrai nel log:
```
[DevMod] Mod integration status:
  - Pehkui: ENABLED
  - Better Combat: ENABLED
  - Distant Horizons: ENABLED (API available)
  - LittleTiles: NOT FOUND
```

---

## Le Singole Integrazioni

### PehkuiIntegration — Scala Entità

**Perché serve**: Un mob gigante dovrebbe fare più danni. Un mob minuscolo dovrebbe avere hitbox più piccola.

**Cosa legge**:
- `getScale(entity)` → scala visiva (quanto appare grande)
- `getHitboxScale(entity)` → scala hitbox (può essere diversa dalla visiva!)

**Esempio pratico**:
```java
// Nel calcolo danni
float scale = ModIntegrationManager.getPehkuiScale(mob);
float damage = baseDamage * scale;  // Mob 2x più grande = 2x danni
```

---

### BetterCombatIntegration — Dati Combattimento

**Perché serve**: Better Combat aggiunge attacchi speciali (fendenti, affondi, attacchi rotanti). DevMod può reagire diversamente a ciascuno.

**Cosa legge**:
- `getAttackName(player)` → tipo attacco corrente ("Slash", "Thrust", "Spin")
- `getReach(player)` → portata arma modificata
- `isInCombo(player)` → se il giocatore è in una combo
- `getComboCount(player)` → numero di hit nella combo

**Esempio pratico**:
```java
// Bonus danno per combo lunghe
if (ModIntegrationManager.isBetterCombatLoaded()) {
    int combo = ModIntegrationManager.getBetterCombatComboCount(player);
    if (combo >= 5) {
        damage *= 1.2f;  // +20% per combo di 5+
    }
}
```

---

### DistantHorizonsIntegration — Dimensioni Dinamiche

**Perché serve**: Distant Horizons genera LOD (Level of Detail) per il terreno lontano. Ma le nostre arene sono **temporanee** — non ha senso generare LOD per una dimensione che esisterà 5 minuti.

**Cosa fa**:
- `registerDynamicDimension(key)` → dice a DH "questa dimensione è temporanea"
- `shouldSkipLodGeneration(key)` → DH può chiedere se saltare la generazione
- `notifyDimensionChange(player, from, to)` → transizioni più fluide

**Quando viene chiamato** (automaticamente da DynamicDimensionManager):
```java
// Dopo creazione dimensione arena
DistantHorizonsIntegration.registerDynamicDimension(dimensionKey);

// Prima di distruggere
DistantHorizonsIntegration.unregisterDynamicDimension(dimensionKey);
```

---

### LittleTilesIntegration — Handler Animazioni

**Perché serve**: LittleTiles ha un sistema di animazioni per i suoi blocchi custom. Ogni `Level` ha bisogno di un handler. Le nostre dimensioni dinamiche sono create a runtime, quindi dobbiamo inizializzare l'handler manualmente.

**Il bug che risolve**: Senza questa integrazione, i giocatori che entrano in un'arena con blocchi LittleTiles vedrebbero un NullPointerException.

**Quando viene chiamato**:
```java
// DOPO che ServerLevel è stato aggiunto al server
LittleTilesIntegration.registerDynamicDimension(serverLevel);

// Durante cleanup
LittleTilesIntegration.unregisterDynamicDimension(dimensionKey);
```

---

### PufferfishIntegration — Mappatura Attributi

**Perché serve**: Alcuni mod definiscono attributi simili (es. "armor_shred", "life_steal"). Se sia DevMod che Pufferfish definiscono `life_steal`, vogliamo usare quello di Pufferfish per evitare duplicati.

**Come funziona**:
```java
// DevMod registra il suo attributo
Attribute devmodLifeSteal = ModAttributes.LIFE_STEAL;

// Ma quando lo usiamo, passiamo per il mapper
Attribute actual = PufferfishIntegration.map(devmodLifeSteal);
// Se Pufferfish è presente, ritorna il suo attributo
// Altrimenti ritorna quello di DevMod
```

**Attributi mappati attualmente**:
- `armor_shred` → Pufferfish armor shred
- `life_steal` → Pufferfish life steal

---

## Aggiungere una Nuova Integrazione

### 1. Crea la classe di integrazione

```java
public class NuovoModIntegration {
    private static boolean loaded = false;
    private static boolean checked = false;

    public static boolean isLoaded() {
        if (!checked) {
            checked = true;
            try {
                Class.forName("com.nuovomod.api.MainClass");
                loaded = true;
            } catch (ClassNotFoundException e) {
                loaded = false;
            }
        }
        return loaded;
    }

    public static String getData(Entity entity) {
        if (!isLoaded()) return "default";
        try {
            // Reflection per chiamare metodi del mod
            // ...
        } catch (Exception e) {
            return "default";
        }
    }
}
```

### 2. Registra in ModIntegrationManager

```java
// In initCompatModules()
if (NuovoModIntegration.isLoaded()) {
    LOGGER.info("NuovoMod integration enabled");
}

// Aggiungi metodo helper
public static String getNuovoModData(Entity entity) {
    return NuovoModIntegration.getData(entity);
}
```

### 3. Usa dove serve

```java
// Nel tuo codice
String data = ModIntegrationManager.getNuovoModData(entity);
```

---

## Differenza tra Integration e Compat

| Aspetto | integration/ | compat/ |
|---------|--------------|---------|
| **Scopo** | Leggere dati da altri mod | Adattare comportamento DevMod |
| **Direzione** | DevMod ← Altri Mod | DevMod → Altri Mod |
| **Esempio** | Leggi scala da Pehkui | Registra attributi per Curios |
| **Complessità** | Reflection pura | Spesso usa API del mod |

---

## Troubleshooting

### "Integration non funziona"

1. Controlla il log per "Mod integration status"
2. Il mod target è effettivamente installato?
3. La versione del mod è compatibile? (API potrebbero cambiare)

### "NullPointerException in dimensione custom"

Probabilmente manca l'inizializzazione LittleTiles. Assicurati che `registerDynamicDimension` sia chiamato **dopo** che il ServerLevel è stato aggiunto.

### "Valori strani da Pehkui"

Pehkui ha scale separate per visual e hitbox. Stai usando quella giusta per il tuo caso d'uso?

---

## Dipendenze

- NeoForge ModList — per rilevare mod
- Java Reflection — per chiamate dinamiche
- SLF4J — per logging
