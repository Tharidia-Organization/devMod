# Piano di Aggiornamento Libreria Effekseer

## Problema Identificato

Gli effetti impact falliscono perché sono stati creati con **Effekseer 1.7+** (versione formato 1800),
mentre la libreria nativa attuale supporta solo fino alla versione **1610** (Effekseer ~1.6).

### Tabella Compatibilità Attuale

| Effetto | Versione Formato | Stato |
|---------|------------------|-------|
| block.efkefc | 1610 | ✅ Funziona |
| finisher.efkefc | 1610 | ✅ Funziona |
| portal.efkefc | 1610 | ✅ Funziona |
| vortex.efkefc | 1500 | ✅ Funziona |
| spiral.efkefc | 84 | ✅ Funziona |
| backstab.efkefc | **1800** | ❌ Fallisce |
| combo.efkefc | **1800** | ❌ Fallisce |
| critical.efkefc | **1800** | ❌ Fallisce |
| headshot.efkefc | **1800** | ❌ Fallisce |
| hit.efkefc | **1800** | ❌ Fallisce |
| kill.efkefc | **1800** | ❌ Fallisce |
| milestone.efkefc | **1800** | ❌ Fallisce |

## Soluzione Proposta

Aggiornare la libreria nativa Effekseer dalla versione attuale alla versione **1.70e**
(da AAAParticles 1.4.7 per Minecraft 1.21).

### Fonte: AAAParticles 1.4.7
- Repository: https://github.com/ChloePrime/AAAParticles
- Versione: 1.21-1.4.7 (22 Ottobre 2024)
- Effekseer: 1.70e
- Compatibilità: macOS (arm64 + x86_64), Windows, Linux

## Analisi delle Modifiche

### 1. Librerie Native

| File | Vecchia Dimensione | Nuova Dimensione | Note |
|------|-------------------|------------------|------|
| libEffekseerNativeForJava.dylib | 12.7 MB | 6.4 MB | Diverso MD5 |
| EffekseerNativeForJava.dll | 132 bytes (LFS) | 132 bytes (LFS) | Richiede Git LFS |
| libEffekseerNativeForJava.so | 132 bytes (LFS) | 132 bytes (LFS) | Richiede Git LFS |

### 2. Binding SWIG Java

Differenze minime (solo annotazioni @SuppressWarnings):

| File | Differenze |
|------|------------|
| EffekseerEffectCore.java | 3 righe |
| EffekseerManagerCore.java | 3 righe |
| EffekseerBackendCore.java | 3 righe |
| EffekseerTextureType.java | 3 righe |
| EffekseerCoreDeviceType.java | 3 righe |
| EffekseerCore.java | 0 righe |
| EffekseerCoreJNI.java | 0 righe |

**Conclusione**: API compatibile, nessun breaking change.

## Piano di Implementazione

### Step 1: Backup
```
src/main/resources/assets/devmod/libEffekseerNativeForJava.dylib
src/main/resources/assets/devmod/EffekseerNativeForJava.dll
src/main/resources/assets/devmod/libEffekseerNativeForJava.so
src/main/java/Effekseer/swig/*.java
```

### Step 2: Aggiornare Libreria Native macOS
- Sostituire `libEffekseerNativeForJava.dylib` con la versione da AAAParticles 1.4.7

### Step 3: Aggiornare Binding SWIG
- Copiare i file Java aggiornati da AAAParticles (modifiche minime)

### Step 4: Gestione Windows/Linux
- Le librerie .dll e .so richiedono Git LFS
- Opzione A: Installare Git LFS e fare pull
- Opzione B: Scaricare manualmente da release AAAParticles

### Step 5: Test
- Rebuild del progetto
- Avviare il client
- Verificare che tutti gli effetti impact si carichino

## Rischi e Mitigazioni

| Rischio | Probabilità | Mitigazione |
|---------|-------------|-------------|
| API breaking changes | Bassa | Diff mostra solo annotazioni |
| Problemi rendering | Media | Testare tutti gli effetti |
| Incompatibilità Windows/Linux | Media | .dll/.so sono LFS pointers |

## Rollback

In caso di problemi, ripristinare i file dal backup.

## Prossimi Passi

1. ✅ Approvazione del piano
2. ⏳ Esecuzione Step 1-5
3. ⏳ Verifica finale
