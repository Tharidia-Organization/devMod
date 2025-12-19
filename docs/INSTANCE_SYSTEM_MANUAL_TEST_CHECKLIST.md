# Instance Dimension System - Checklist Test Manuali

## Istruzioni

Questo documento contiene la checklist per i test manuali del sistema Instance Dimension.
Ogni test deve essere eseguito e documentato con:
- [ ] Passato / [X] Passato / [!] Fallito
- Data e versione
- Note eventuali

---

## Pre-requisiti

- [ ] Server Minecraft 1.21.1 con NeoForge
- [ ] DevMod installato correttamente
- [ ] Log server accessibile
- [ ] Per test multiplayer: 2+ client connessi

---

## M1. Test Singleplayer - Happy Path

### M1.1 Avvio Quest Base
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Avvia gioco singleplayer | Mondo caricato | [ ] |
| 2 | Verifica log: `[InstanceManager] Initialized` | Messaggio presente | [ ] |
| 3 | Apri UI Endurance Quest | Menu visibile | [ ] |
| 4 | Seleziona un mob (es. Zombie) | Mob selezionato | [ ] |
| 5 | Clicca "Start Quest" | Messaggio countdown o "Preparing..." | [ ] |
| 6 | Attendi teleport | Player in arena void | [ ] |
| 7 | Verifica piattaforma stone bricks | Arena visibile | [ ] |
| 8 | Verifica mob spawn | Mob appaiono | [ ] |

### M1.2 Completamento Quest
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Completa tutte le wave | Messaggio "Quest completed!" | [ ] |
| 2 | Attendi teleport ritorno | Player in posizione originale | [ ] |
| 3 | Verifica inventory | Inventory identico a prima | [ ] |
| 4 | Verifica health/food | Valori ripristinati | [ ] |
| 5 | Verifica XP | XP identico a prima | [ ] |
| 6 | Verifica dimensione distrutta (5s dopo) | No folder in dimensions/devmod/ | [ ] |

### M1.3 Fallimento Quest (Morte)
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Avvia nuova quest | Player in arena | [ ] |
| 2 | Lasciati uccidere dai mob | Messaggio "You died" | [ ] |
| 3 | Verifica teleport automatico | Player in posizione originale | [ ] |
| 4 | Verifica inventory ripristinato | Inventory pre-quest | [ ] |

---

## M2. Test Recovery - Disconnect

### M2.1 Disconnect Durante Countdown
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Avvia quest con countdown 10s | "Teleporting in 10 seconds..." | [ ] |
| 2 | Alt+F4 durante countdown | Client chiuso | [ ] |
| 3 | Riconnetti | Player in posizione originale | [ ] |
| 4 | Verifica messaggio | "Teleport was interrupted" | [ ] |
| 5 | Verifica inventory | Inventory intatto | [ ] |

### M2.2 Disconnect Durante Quest
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Avvia quest e attendi arena | Player in istanza | [ ] |
| 2 | Alt+F4 | Client chiuso | [ ] |
| 3 | Riconnetti | Player in posizione originale overworld | [ ] |
| 4 | Verifica messaggio | "Quest failed - you disconnected" | [ ] |
| 5 | Verifica inventory | Inventory pre-quest ripristinato | [ ] |
| 6 | Verifica no istanza attiva | `InstanceRegistry` vuoto per player | [ ] |

### M2.3 Disconnect Durante Return
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Completa quest | "Returning to overworld..." | [ ] |
| 2 | Alt+F4 immediatamente | Client chiuso | [ ] |
| 3 | Riconnetti | Player in posizione originale | [ ] |
| 4 | Verifica messaggio | "Return was interrupted" | [ ] |

---

## M3. Test Server Restart

### M3.1 Restart Durante Quest Attiva
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Avvia quest | Player in arena | [ ] |
| 2 | Stop server (/stop o kill) | Server chiuso | [ ] |
| 3 | Riavvia server | Server up | [ ] |
| 4 | Connetti client | Player login | [ ] |
| 5 | Verifica recovery | Player in posizione originale | [ ] |
| 6 | Verifica istanze orfane pulite | No istanze con 0 players | [ ] |

### M3.2 Verifica Startup Cleanup
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Dopo restart, verifica log | `[Recovery] Performing startup cleanup...` | [ ] |
| 2 | Verifica orphaned snapshots | Log: eventuali snapshot orfani rilevati | [ ] |
| 3 | Verifica empty instances | Istanze vuote marcate per distruzione | [ ] |

---

## M4. Test Multiplayer

### M4.1 Party Quest (2 Players)
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Player A invita Player B | Party creato | [ ] |
| 2 | Player A avvia quest | Entrambi in countdown | [ ] |
| 3 | Attendi teleport | Entrambi nella stessa arena | [ ] |
| 4 | Verifica stesso instanceId | Stesso ID per entrambi | [ ] |
| 5 | Completa quest insieme | Entrambi tornano alle pos originali | [ ] |

### M4.2 Party Member Disconnect
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | 2 players in party quest | Entrambi in arena | [ ] |
| 2 | Player B disconnette | Player A continua | [ ] |
| 3 | Player B riconnette | Player B in pos originale | [ ] |
| 4 | Player A completa | Player A torna a casa | [ ] |
| 5 | Istanza distrutta | No istanza residua | [ ] |

### M4.3 Istanze Parallele
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Player A avvia quest solo | Player A in istanza 1 | [ ] |
| 2 | Player B avvia quest solo | Player B in istanza 2 | [ ] |
| 3 | Verifica isolamento | Istanze separate | [ ] |
| 4 | Entrambi completano | Entrambi tornano OK | [ ] |
| 5 | Entrambe istanze distrutte | Cleanup completo | [ ] |

---

## M5. Test Edge Cases

### M5.1 Dimensione Originale Non-Overworld
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Vai nel Nether | Player in minecraft:the_nether | [ ] |
| 2 | Avvia quest dal Nether | Player teleportato in arena | [ ] |
| 3 | Completa quest | Player ritorna NEL NETHER | [ ] |
| 4 | Verifica posizione esatta | Stesso x,y,z di prima | [ ] |

### M5.2 Inventory Pieno
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Riempi inventory completamente | 36 slot pieni | [ ] |
| 2 | Avvia quest | Snapshot salvato | [ ] |
| 3 | In arena, droppa tutti items | Inventory vuoto in arena | [ ] |
| 4 | Completa quest | Inventory ripristinato con TUTTI gli items | [ ] |

### M5.3 Effetti Pozione Attivi
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Applica Speed + Strength | Effetti attivi | [ ] |
| 2 | Avvia quest | In arena | [ ] |
| 3 | Verifica effetti in arena | Effetti potrebbero essere rimossi | [ ] |
| 4 | Completa quest | Effetti originali ripristinati | [ ] |

### M5.4 Doppio Avvio Quest
| Step | Azione | Risultato Atteso | Status |
|------|--------|------------------|--------|
| 1 | Avvia quest | In arena | [ ] |
| 2 | Prova ad avviare seconda quest | Messaggio errore "already in instance" | [ ] |
| 3 | Verifica solo 1 istanza | Una sola istanza per player | [ ] |

---

## M6. Test Performance

### M6.1 Tempo di Creazione Istanza
| Metrica | Threshold | Valore Misurato | Status |
|---------|-----------|-----------------|--------|
| Tempo da click a teleport (immediate) | <5s | ___s | [ ] |
| Tempo da click a countdown start | <3s | ___s | [ ] |
| Tempo distruzione istanza | <10s dopo exit | ___s | [ ] |

### M6.2 Impatto TPS
| Scenario | TPS Atteso | TPS Misurato | Status |
|----------|------------|--------------|--------|
| 1 istanza attiva | >18 | ___ | [ ] |
| 3 istanze attive | >16 | ___ | [ ] |
| 5 istanze attive | >14 | ___ | [ ] |

### M6.3 Memory
| Scenario | Heap Increase | Note | Status |
|----------|---------------|------|--------|
| Creazione 1 istanza | <50MB | ___ | [ ] |
| Dopo distruzione | Back to baseline | ___ | [ ] |
| 10 cicli create/destroy | No leak | ___ | [ ] |

---

## M7. Verifica Log

### Log Pattern da Verificare
| Pattern | Quando | Presente? |
|---------|--------|-----------|
| `[InstanceManager] Initialized` | Server start | [ ] |
| `[Instance] <uuid> state changed: CREATING -> READY` | Quest start | [ ] |
| `[DynamicDim] Successfully created dimension` | Dimension created | [ ] |
| `[DynamicDim] Teleported <player> to instance` | Player enters | [ ] |
| `[Instance] <uuid> state changed: READY -> ACTIVE` | Quest active | [ ] |
| `[Recovery] Saved snapshot for player` | Before teleport | [ ] |
| `[Recovery] Successfully recovered player` | On disconnect recovery | [ ] |
| `[DynamicDim] Dimension destroyed for instance` | Cleanup complete | [ ] |

### Log Pattern di Errore (Non Dovrebbero Apparire)
| Pattern | Significato | Presente? |
|---------|-------------|-----------|
| `[Recovery] Failed to save snapshot` | IO Error | [ ] (should be NO) |
| `[DynamicDim] Failed to create dimension` | Creation error | [ ] (should be NO) |
| `[InstanceManager] Teleport failed` | Teleport error | [ ] (should be NO) |
| `NullPointerException` | Bug | [ ] (should be NO) |
| `ConcurrentModificationException` | Race condition | [ ] (should be NO) |

---

## M8. Cleanup Verification

### Dopo Ogni Sessione di Test
| Check | Come Verificare | Status |
|-------|-----------------|--------|
| No snapshot residui | Check `config/devmod/snapshots/` | [ ] |
| No istanze in registry | Check log o comando debug | [ ] |
| No dimension folders | Check `world/dimensions/devmod/` | [ ] |
| No pending teleports | Restart server clean | [ ] |

---

## Firma Test

| Tester | Data | Versione Mod | Versione MC | Note |
|--------|------|--------------|-------------|------|
| ______ | ____/____/____ | v0.___.___ | 1.21.1 | _____ |

---

## Bug Trovati

### Template Bug Report

```markdown
### BUG-M___: [Titolo]

**Test**: M_._
**Severità**: Critical / High / Medium / Low
**Riproduzione**:
1. Step 1
2. Step 2
...

**Expected**: [comportamento atteso]
**Actual**: [comportamento reale]
**Log excerpt**:
```
[paste relevant log lines]
```

**Screenshot/Video**: [link se disponibile]
```
