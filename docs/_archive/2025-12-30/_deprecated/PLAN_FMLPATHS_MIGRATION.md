# Piano Migrazione: Path.of() → FMLPaths

## Analisi della Situazione Attuale

### File che usano FMLPaths (CORRETTO)
| File | Pattern |
|------|---------|
| `TelemetrySettings.java` | `FMLPaths.CONFIGDIR.get().resolve("devmod/telemetry_settings.json")` |
| `TelemetryConfig.java` | `FMLPaths.CONFIGDIR.get().resolve("devmod")` |
| `MobConfigManager.java` | `FMLPaths.CONFIGDIR.get().resolve("devmod")` |

### File che usano Path.of() o Paths.get() (DA MIGRARE)
| File | Attuale | Problema |
|------|---------|----------|
| `SettingsManager.java` | `Path.of("config/devmod")` | Path relativo, dipende da working directory |
| `TutorialManager.java` | `Paths.get("run/config/devmod/...")` | Hardcoded "run/", non funziona in produzione |
| `TesterProfile.java` | `Paths.get("run/config/devmod/...")` | Hardcoded "run/", non funziona in produzione |
| `TesterProgress.java` | `Paths.get("run/config/devmod/...")` | Hardcoded "run/", non funziona in produzione |
| `TestingSession.java` | `Paths.get("run/config/devmod/...")` | Hardcoded "run/", non funziona in produzione |
| `ModTestConfig.java` | `gameDir.resolve("config/devmod/...")` | OK se gameDir è corretto |

### Problema Critico
I file testing usano `Paths.get("run/config/devmod/...")` che:
1. Funziona SOLO in ambiente dev (dove la working directory è il progetto)
2. In produzione, "run/" non esiste - il launcher usa una directory diversa
3. I file di progresso QA andrebbero **persi** in produzione

---

## Piano di Migrazione

### Fase 1: Creare Utility Class Centralizzata

Creare `ConfigPaths.java` che centralizza tutti i path:

```java
package com.devmod.util;

import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;

public final class ConfigPaths {
    private ConfigPaths() {}

    // === CONFIG DIRECTORIES ===

    /** Main config directory: config/devmod/ */
    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve("devmod");
    }

    /** Telemetry config: config/devmod/telemetry_settings.json */
    public static Path getTelemetrySettings() {
        return getConfigDir().resolve("telemetry_settings.json");
    }

    /** Room definitions: config/devmod/telemetry_rooms.json */
    public static Path getTelemetryRooms() {
        return getConfigDir().resolve("telemetry_rooms.json");
    }

    /** Mob configs: config/devmod/mob_configs/ */
    public static Path getMobConfigsDir() {
        return getConfigDir().resolve("mob_configs");
    }

    /** Settings: config/devmod/settings.json */
    public static Path getSettingsFile() {
        return getConfigDir().resolve("settings.json");
    }

    // === QA TESTING FILES ===

    /** QA session: config/devmod/qa_session.json */
    public static Path getQASessionFile() {
        return getConfigDir().resolve("qa_session.json");
    }

    /** Tutorial progress: config/devmod/tutorial_progress.json */
    public static Path getTutorialFile() {
        return getConfigDir().resolve("tutorial_progress.json");
    }

    /** Tester profile: config/devmod/tester_profile.json */
    public static Path getTesterProfileFile() {
        return getConfigDir().resolve("tester_profile.json");
    }

    /** Tester progress: config/devmod/tester_progress.json */
    public static Path getTesterProgressFile() {
        return getConfigDir().resolve("tester_progress.json");
    }

    /** Test templates: config/devmod/test_templates/ */
    public static Path getTestTemplatesDir() {
        return getConfigDir().resolve("test_templates");
    }

    // === GAME DIRECTORIES ===

    /** Game directory (where saves, logs, etc. are) */
    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /** QA Reports: qa_reports/ */
    public static Path getQAReportsDir() {
        return getGameDir().resolve("qa_reports");
    }

    /** Telemetry exports: telemetry/ */
    public static Path getTelemetryExportDir() {
        return getGameDir().resolve("telemetry");
    }
}
```

### Fase 2: Migrare i File

#### 2.1 SettingsManager.java
```java
// PRIMA
private static final String CONFIG_DIR = "config/devmod";
Path configDir = Path.of(CONFIG_DIR);

// DOPO
import com.devmod.util.ConfigPaths;
Path configPath = ConfigPaths.getSettingsFile();
```

#### 2.2 TutorialManager.java
```java
// PRIMA
private static final Path TUTORIAL_FILE = Paths.get("run/config/devmod/tutorial_progress.json");

// DOPO
// Lazy init per evitare NPE durante class loading
private static Path getTutorialFile() {
    return ConfigPaths.getTutorialFile();
}
```

#### 2.3 TesterProfile.java
```java
// PRIMA
private static final Path PROFILE_FILE = Paths.get("run/config/devmod/tester_profile.json");

// DOPO
private static Path getProfileFile() {
    return ConfigPaths.getTesterProfileFile();
}
```

#### 2.4 TesterProgress.java
```java
// PRIMA
private static final Path PROGRESS_FILE = Paths.get("run/config/devmod/tester_progress.json");

// DOPO
private static Path getProgressFile() {
    return ConfigPaths.getTesterProgressFile();
}
```

#### 2.5 TestingSession.java
```java
// PRIMA
private static final Path SESSION_FILE = Paths.get("run/config/devmod/qa_session.json");

// DOPO
private static Path getSessionFile() {
    return ConfigPaths.getQASessionFile();
}
```

#### 2.6 ModTestConfig.java
```java
// PRIMA
configDir = gameDir.resolve("config").resolve("devmod").resolve("test_templates");

// DOPO
public static void init() {
    configDir = ConfigPaths.getTestTemplatesDir();
    // ...
}
```

### Fase 3: Aggiornare File Esistenti che Usano già FMLPaths

Questi file possono essere semplificati per usare la utility class:
- `TelemetrySettings.java`
- `TelemetryConfig.java`
- `MobConfigManager.java`

---

## Ordine di Esecuzione

1. **ConfigPaths.java** - Creare la utility class
2. **SettingsManager.java** - Migrare (file richiesto dall'utente)
3. **TutorialManager.java** - Migrare (bug critico: "run/" hardcoded)
4. **TesterProfile.java** - Migrare (bug critico: "run/" hardcoded)
5. **TesterProgress.java** - Migrare (bug critico: "run/" hardcoded)
6. **TestingSession.java** - Migrare (bug critico: "run/" hardcoded)
7. **ModTestConfig.java** - Migrare
8. **File esistenti con FMLPaths** - Refactor opzionale per usare ConfigPaths

---

## Test Plan

1. Compilare il progetto (`./gradlew compileJava`)
2. Avviare il client (`./gradlew runClient`)
3. Verificare che tutti i file di config vengano creati in `config/devmod/`
4. Verificare che il QA testing salvi correttamente
5. Verificare che i settings persistano tra riavvii

---

## Rischi e Mitigazioni

| Rischio | Mitigazione |
|---------|-------------|
| FMLPaths non disponibile durante static init | Usare lazy initialization con getter methods |
| File esistenti in "run/config" | Il mod continuerà a funzionare in dev; i file in produzione saranno nuovi |
| Breaking change per utenti esistenti | Non applicabile (mod in sviluppo) |

---

## Stima Impatto

- **File da modificare**: 7 file Java
- **Nuovo file**: 1 (ConfigPaths.java)
- **Complessità**: Bassa (sostituzioni meccaniche)
- **Rischio regressione**: Basso (test compilazione + runtime)
