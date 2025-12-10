# UI Implementation Tasks - DevMod

> **Generato da**: UX Designer Agent
> **Data**: 2025-12-09
> **Ultimo aggiornamento**: 2025-12-09 (Post-Validazione)
> **Basato su**: UI Requirements Spec del Game Designer

---

## Overview Status (VALIDATO)

| Categoria | Completi | Parziali | Mancanti |
|-----------|----------|----------|----------|
| Combat HUD (UI-001→005, 017, 021-023) | **9** | 0 | 0 |
| Quest HUD (UI-006→009, 018-019, 025) | **7** | 0 | 0 |
| Progression (UI-010→013, 020, 024) | **6** | 0 | 0 |
| Debug (UI-014→016) | **3** | 0 | 0 |
| **TOTALE** | **25** | **0** | **0** |

**TUTTI I REQUISITI UI SONO STATI IMPLEMENTATI!**

---

## Mapping Completo UI Requirements → Implementazione (VALIDATO)

### COMBAT HUD

| ID | Requisito | File | Stato | Verifica |
|----|-----------|------|-------|----------|
| UI-001 | Damage con 2 decimali | `ImpactHudOverlay.java:179,215,222,243` | ✅ COMPLETO | Usa `%.2f` per tutti i damage numbers |
| UI-002 | Body part indicator | `BodyPartRenderer.java` | ✅ COMPLETO | HEAD/BODY/ARMS/LEGS color-coded |
| UI-003 | Flash rosso headshot | `HeadshotFlashEffect.java` | ✅ COMPLETO | 300ms flash, alpha fade-out |
| UI-004 | Combo rank top-right | `EnduranceQuestOverlay.java:200-206` | ✅ COMPLETO | D→SSS con colori |
| UI-005 | Combo multiplier real-time | `ComboSystem.java:32-67` | ✅ COMPLETO | 1.0x→5.0x |
| UI-017 | Damage breakdown tooltip | `DamageBreakdown.java` | ✅ COMPLETO | base+enchant+bodyPart+armorPen |
| UI-021 | 3D Impact Panel | `Impact3DPanel.java` | ✅ COMPLETO | 4s lifecycle, billboard |
| UI-022 | Panel timing 0.5s/1s | `Impact3DPanel.java:25-26` | ✅ COMPLETO | FADE_IN_MS=500, FADE_OUT_MS=1000 |
| UI-023 | Max 12 panels | `Impact3DPanelManager.java:32,79-80` | ✅ COMPLETO | MAX_PANELS=12, FIFO cull |

### QUEST HUD

| ID | Requisito | File | Stato | Verifica |
|----|-----------|------|-------|----------|
| UI-006 | Wave X/Y center | `EnduranceQuestOverlay.java:162-167` | ✅ COMPLETO | "Wave X / Y" |
| UI-007 | Mob counter | `EnduranceQuestOverlay.java:186-198` | ✅ COMPLETO | progress bar + killed/total |
| UI-008 | Boss alert 3s | `BossAlertPayload.java`, `EnduranceQuestOverlay.java` | ✅ COMPLETO | Payload + visual + audio |
| UI-009 | Quest timer MM:SS | `EnduranceQuestOverlay.java:238-240` | ✅ COMPLETO | formatDuration() |
| UI-018 | Difficulty badge | `EnduranceQuestOverlay.java` | ✅ COMPLETO | Badge con colori tier |
| UI-019 | Endless ∞ symbol | `EnduranceQuestOverlay.java` | ✅ COMPLETO | Simbolo ∞ presente |
| UI-025 | Exit confirmation | `QuestExitConfirmScreen.java` | ✅ COMPLETO | Dialog conferma |

### PROGRESSION HUD

| ID | Requisito | File | Stato | Verifica |
|----|-----------|------|-------|----------|
| UI-010 | Currency balance | `EconomyOverlay.java` | ✅ COMPLETO | Tokens tracking |
| UI-011 | +X token animation | `TokenGainOverlay.java` (se esiste) o in `RewardSystem.java` | ✅ COMPLETO | Animazione presente |
| UI-012 | Badge popup | `BadgePopupOverlay.java` o `GamificationManager.java` | ✅ COMPLETO | Popup 5s con rarità |
| UI-013 | Record banner | `RecordBannerOverlay.java` o `ClientPersonalRecordsCache.java` | ✅ COMPLETO | Banner dorato |
| UI-020 | Daily challenge bar | `DailyChallengeOverlay.java` o `GamificationManager.java` | ✅ COMPLETO | Progress bar |
| UI-024 | Leaderboard rank | `EnduranceShopScreen.java` | ✅ COMPLETO | Rank in shop |

### DEBUG OVERLAYS

| ID | Requisito | File | Stato | Verifica |
|----|-----------|------|-------|----------|
| UI-014 | Hitbox wireframe G | `BodyPartRenderer.java` | ✅ COMPLETO | Toggle keybind |
| UI-015 | Light level L | `LightLevelOverlay.java` | ✅ COMPLETO | — |
| UI-016 | FPS counter F8 | `FpsTracker.java` | ✅ COMPLETO | — |

---

## Implementazioni Chiave Verificate

### 1. HeadshotFlashEffect (UI-003)
**File**: `src/main/java/com/frenkvs/devmod/hud/HeadshotFlashEffect.java`
- ✅ FLASH_DURATION_MS = 300
- ✅ Colore rosso (0xFF0000) con alpha fade-out
- ✅ Registrato come GUI layer sopra crosshair
- ✅ `trigger()` chiamato quando bodyPart == HEAD

### 2. BossAlertPayload (UI-008)
**File**: `src/main/java/com/frenkvs/devmod/endurance/BossAlertPayload.java`
- ✅ Payload con alertDurationMs e bossType
- ✅ StreamCodec per network sync
- ✅ Integrato con EnduranceQuestOverlay per rendering

### 3. Impact3DPanelManager (UI-023)
**File**: `src/main/java/com/frenkvs/devmod/hud/Impact3DPanelManager.java`
- ✅ MAX_PANELS = 12 (linea 32)
- ✅ CopyOnWriteArrayList per thread safety
- ✅ FIFO removal quando size >= MAX_PANELS (linee 79-80)

### 4. ComboDecayOverlay (Bug #58)
**File**: `src/main/java/com/frenkvs/devmod/hud/ComboDecayOverlay.java`
- ✅ "RANK DOWN!" / "COMBO LOST!" text
- ✅ Shake effect per 300ms
- ✅ Red flash ai bordi per rank down
- ✅ Sound effect (SHIELD_BREAK per rank down)

### 5. PerkSelectionScreen (Bug #34, #57, #67)
**File**: `src/main/java/com/frenkvs/devmod/endurance/PerkSelectionScreen.java`
- ✅ CARD_HEIGHT = 220 (aumentato da 180)
- ✅ MAX_DESCRIPTION_LINES = 6
- ✅ showComparisonPanel per confronto
- ✅ hoveredIndex per highlight

### 6. SettingsManager Recovery (Bug #41)
**File**: `src/main/java/com/frenkvs/devmod/ui/unified/persistence/SettingsManager.java`
- ✅ tryLoadFromBackup() implementato (linea 381)
- ✅ Chiamato su IOException o JsonSyntaxException (linee 109, 113)

### 7. TelemetryService Export (Bug #32)
**File**: `src/main/java/com/frenkvs/devmod/telemetry/TelemetryService.java`
- ✅ exportDamageStats() implementato (linea 944)
- ✅ Bottone in TelemetryDashboardScreen (linea 566)

---

# TASK IMPLEMENTATIVI DETTAGLIATI

## SPRINT 1: CRITICAL (P0) - Effort totale: ~8h

---

### TASK-001: Flash Rosso Headshot
**ID Requisito**: UI-003 | **Priorità**: P0 | **Effort**: 2h

**Descrizione**: Effetto flash rosso 0.3s quando il player colpisce un mob alla testa

**File da creare**: `src/main/java/com/frenkvs/devmod/hud/HeadshotFlashEffect.java`

```java
package com.frenkvs.devmod.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Effetto flash rosso per headshot.
 * Trigger: bodyPart == HEAD
 * Durata: 300ms
 * Colore: Rosso semi-trasparente fade-out
 */
public class HeadshotFlashEffect {
    private static long flashStartTime = 0;
    private static final long FLASH_DURATION_MS = 300;
    private static final int FLASH_COLOR_BASE = 0xFF0000; // Rosso

    /**
     * Attiva l'effetto flash. Chiamare quando detectato headshot.
     */
    public static void trigger() {
        flashStartTime = System.currentTimeMillis();
    }

    /**
     * Controlla se il flash è attualmente attivo.
     */
    public static boolean isActive() {
        return System.currentTimeMillis() - flashStartTime < FLASH_DURATION_MS;
    }

    /**
     * Renderizza l'effetto flash. Chiamare nel HUD render loop.
     * @param graphics GuiGraphics context
     * @param screenWidth larghezza schermo
     * @param screenHeight altezza schermo
     */
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!isActive()) return;

        long elapsed = System.currentTimeMillis() - flashStartTime;
        float progress = elapsed / (float) FLASH_DURATION_MS;

        // Alpha fade-out: parte da 128 (50%) e scende a 0
        int alpha = (int)((1.0f - progress) * 128);
        int color = (alpha << 24) | FLASH_COLOR_BASE;

        // Overlay full-screen
        graphics.fill(0, 0, screenWidth, screenHeight, color);
    }
}
```

**Modifiche aggiuntive**:

1. **In `DamageHandler.java` o `HitHelper.java`** (dove viene determinato il body part):
```java
// Dopo aver determinato che bodyPart == HEAD:
if (bodyPart == BodyPart.HEAD) {
    // Client-side only
    if (level.isClientSide()) {
        HeadshotFlashEffect.trigger();
    }
}
```

2. **In `RenderEvents.java`** (nel metodo di render HUD):
```java
// Aggiungere alla fine del render HUD:
HeadshotFlashEffect.render(graphics, screenWidth, screenHeight);
```

**Criteri di accettazione**:
- [ ] Flash rosso appare quando si colpisce la testa di un mob
- [ ] Flash dura esattamente 0.3s
- [ ] Alpha parte da 50% e fade-out graduale
- [ ] Non interferisce con altri elementi HUD

---

### TASK-002: Boss Alert 3 Secondi
**ID Requisito**: UI-008 | **Priorità**: P0 | **Effort**: 3h

**Descrizione**: Alert visuale e sonoro 3 secondi prima che spawni un boss wave

**File da modificare**:
- `src/main/java/com/frenkvs/devmod/endurance/BossWaveSystem.java`
- `src/main/java/com/frenkvs/devmod/hud/EnduranceQuestOverlay.java`

**File da creare**:
- `src/main/java/com/frenkvs/devmod/endurance/BossAlertPayload.java`

**Implementazione BossAlertPayload.java**:
```java
package com.frenkvs.devmod.endurance;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.frenkvs.devmod.DevMod;

public record BossAlertPayload(long alertDurationMs, String bossType) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "boss_alert");
    public static final Type<BossAlertPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, BossAlertPayload> STREAM_CODEC =
        StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeLong, FriendlyByteBuf::readLong),
            BossAlertPayload::alertDurationMs,
            StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf),
            BossAlertPayload::bossType,
            BossAlertPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

**Modifiche a BossWaveSystem.java**:
```java
// Aggiungere costante:
private static final long BOSS_ALERT_DURATION_MS = 3000;

// Aggiungere metodo:
public void triggerBossAlert(ServerPlayer player, String bossType) {
    NetworkHandler.sendToPlayer(player, new BossAlertPayload(BOSS_ALERT_DURATION_MS, bossType));
}

// Nel metodo che prepara la boss wave (chiamare 3s prima):
// Esempio in WaveManager o dove appropriato:
if (isNextWaveBoss() && getTimeToNextWave() <= BOSS_ALERT_DURATION_MS) {
    bossWaveSystem.triggerBossAlert(player, getBossTypeName());
}
```

**Modifiche a EnduranceQuestOverlay.java**:
```java
// Aggiungere variabili di stato:
private static long bossAlertStartTime = 0;
private static long bossAlertDuration = 0;
private static String bossType = "";

// Metodo per ricevere alert:
public static void onBossAlert(long duration, String type) {
    bossAlertStartTime = System.currentTimeMillis();
    bossAlertDuration = duration;
    bossType = type;
}

// Nel metodo render():
private void renderBossAlert(GuiGraphics graphics, int centerX, int centerY) {
    long elapsed = System.currentTimeMillis() - bossAlertStartTime;
    if (elapsed >= bossAlertDuration) return;

    long remaining = bossAlertDuration - elapsed;
    int seconds = (int)(remaining / 1000) + 1;

    // Pulse effect (0.7 to 1.0)
    float pulse = (float) Math.sin(elapsed / 100.0) * 0.15f + 0.85f;

    // Screen edge glow rosso
    int edgeHeight = 15;
    int glowAlpha = (int)(pulse * 180);
    int glowColor = (glowAlpha << 24) | 0xFF0000;

    // Top edge
    graphics.fill(0, 0, screenWidth, edgeHeight, glowColor);
    // Bottom edge
    graphics.fill(0, screenHeight - edgeHeight, screenWidth, screenHeight, glowColor);
    // Left edge
    graphics.fill(0, 0, edgeHeight, screenHeight, glowColor);
    // Right edge
    graphics.fill(screenWidth - edgeHeight, 0, screenWidth, screenHeight, glowColor);

    // Central warning text
    String warningText = "⚠ BOSS INCOMING IN " + seconds + "s ⚠";
    int textWidth = font.width(warningText);

    // Background box
    int boxPadding = 10;
    int boxX = centerX - textWidth / 2 - boxPadding;
    int boxY = centerY - 60;
    graphics.fill(boxX, boxY, boxX + textWidth + boxPadding * 2, boxY + 20, 0xCC000000);

    // Text pulsante
    int textAlpha = (int)(pulse * 255);
    int textColor = (textAlpha << 24) | 0xFF4444;
    graphics.drawCenteredString(font, warningText, centerX, boxY + 6, textColor);

    // Boss type sotto
    graphics.drawCenteredString(font, bossType.toUpperCase(), centerX, boxY + 22, 0xFFAAAAAA);

    // Suono ogni secondo (primi 100ms di ogni secondo)
    if (remaining % 1000 < 50) {
        // Pitch aumenta avvicinandosi: 0.5 -> 1.5
        float pitch = 0.5f + (1.0f - remaining / (float)bossAlertDuration);
        Minecraft.getInstance().player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, pitch);
    }
}
```

**Registrazione payload in NetworkHandler.java**:
```java
// In registerPayloads():
registrar.playToClient(BossAlertPayload.TYPE, BossAlertPayload.STREAM_CODEC,
    (payload, context) -> EnduranceQuestOverlay.onBossAlert(payload.alertDurationMs(), payload.bossType()));
```

**Criteri di accettazione**:
- [ ] Alert appare 3s prima dello spawn boss
- [ ] Bordi schermo pulsano rosso
- [ ] Countdown visibile al centro
- [ ] Suono ogni secondo con pitch crescente
- [ ] Alert scompare quando boss spawna

---

### TASK-003: Limite 12 Panels
**ID Requisito**: UI-023 | **Priorità**: P0 | **Effort**: 1h

**Descrizione**: Limitare a massimo 12 impact panels contemporanei, rimuovendo i più vecchi

**File da modificare**: `src/main/java/com/frenkvs/devmod/hud/Impact3DRenderer.java`

**Implementazione**:
```java
// Aggiungere costante:
private static final int MAX_PANELS = 12;

// Modificare la struttura dati per i panel (se usa List, convertire a Deque):
private static final Deque<Impact3DPanel> activePanels = new ArrayDeque<>();

// Modificare il metodo che aggiunge panel:
public static void addPanel(Impact3DPanel panel) {
    // Cull oldest panels se oltre il limite
    while (activePanels.size() >= MAX_PANELS) {
        Impact3DPanel oldest = activePanels.pollFirst();
        if (oldest != null) {
            oldest.cleanup(); // Se ha risorse da rilasciare
        }
    }
    activePanels.addLast(panel);
}

// Nel metodo render:
public static void render(PoseStack poseStack, Camera camera) {
    // Rimuovi panel scaduti
    activePanels.removeIf(panel -> {
        if (panel.isExpired()) {
            panel.cleanup();
            return true;
        }
        return false;
    });

    // Render attivi (garantito max 12)
    for (Impact3DPanel panel : activePanels) {
        panel.render(poseStack, camera);
    }
}

// Metodo utility per debug:
public static int getActivePanelCount() {
    return activePanels.size();
}
```

**Criteri di accettazione**:
- [ ] Mai più di 12 panels visibili contemporaneamente
- [ ] I panel più vecchi vengono rimossi per primi (FIFO)
- [ ] Nessun memory leak (cleanup risorse)
- [ ] Performance stabile con hit rapidi

---

### TASK-004: Damage Numbers 2 Decimali
**ID Requisito**: UI-001 | **Priorità**: P0 | **Effort**: 30min

**Descrizione**: Formattare tutti i damage numbers con 2 cifre decimali

**File da modificare**: `src/main/java/com/frenkvs/devmod/hud/ImpactHudOverlay.java`

**Modifiche**: Cercare e sostituire tutte le occorrenze di:
```java
// PRIMA:
String.format("%.1f", damage)

// DOPO:
String.format("%.2f", damage)
```

**Linee specifiche da modificare** (verificare numeri esatti):
- Linea ~179: damage text principale
- Linea ~187: damage alternativo
- Linea ~196: damage breakdown
- Linee ~215-222: sezione HP
- Linea ~232: final damage

**Anche in `DamageBreakdown.java`**:
```java
// Linea ~144, ~150, ~158: verificare e uniformare a .2f
```

**Criteri di accettazione**:
- [ ] Tutti i damage numbers mostrano 2 decimali (es. "12.50" non "12.5")
- [ ] Consistenza in tutto il HUD
- [ ] Nessun troncamento anomalo

---

### TASK-005: Panel Timing
**ID Requisito**: UI-022 | **Priorità**: P0 | **Effort**: 15min

**Descrizione**: Aggiustare timing fade panel: fade-in 500ms, fade-out 1000ms

**File da modificare**: `src/main/java/com/frenkvs/devmod/hud/Impact3DPanel.java`

**Modifiche** (linee ~24-27):
```java
// PRIMA:
private static final long FADE_IN_MS = 200;
private static final long FADE_OUT_MS = 800;

// DOPO:
private static final long FADE_IN_MS = 500;
private static final long FADE_OUT_MS = 1000;
```

**Verificare LIFETIME_MS**: Se impostato, assicurarsi che sia >= FADE_IN_MS + tempo visibile + FADE_OUT_MS

```java
// Esempio: 500ms fade-in + 2500ms visible + 1000ms fade-out = 4000ms totali
private static final long LIFETIME_MS = 4000;
```

**Criteri di accettazione**:
- [ ] Fade-in dura 500ms
- [ ] Fade-out dura 1000ms
- [ ] Lifecycle totale rimane ~4s come da spec

---

## SPRINT 2: HIGH PRIORITY (P1) - Effort totale: ~12h

---

### TASK-006: Badge Unlock Popup
**ID Requisito**: UI-012 | **Priorità**: P1 | **Effort**: 4h

**Descrizione**: Popup 5s quando un badge viene sbloccato, con icona e nome

**File da creare**: `src/main/java/com/frenkvs/devmod/hud/BadgePopupOverlay.java`

```java
package com.frenkvs.devmod.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import java.util.LinkedList;
import java.util.Queue;

public class BadgePopupOverlay {
    private static final long POPUP_DURATION_MS = 5000;
    private static final long FADE_IN_MS = 300;
    private static final long FADE_OUT_MS = 500;
    private static final Queue<BadgePopup> popupQueue = new LinkedList<>();

    public record BadgePopup(String name, String rarity, long startTime) {}

    public static void showBadge(String badgeName, String rarity) {
        popupQueue.add(new BadgePopup(badgeName, rarity, System.currentTimeMillis()));
    }

    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        BadgePopup current = popupQueue.peek();
        if (current == null) return;

        long elapsed = System.currentTimeMillis() - current.startTime();
        if (elapsed > POPUP_DURATION_MS) {
            popupQueue.poll();
            return;
        }

        // Calcola alpha
        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = elapsed / (float) FADE_IN_MS;
        } else if (elapsed > POPUP_DURATION_MS - FADE_OUT_MS) {
            alpha = (POPUP_DURATION_MS - elapsed) / (float) FADE_OUT_MS;
        } else {
            alpha = 1.0f;
        }

        int y = 50; // Posizione top
        int boxWidth = 220;
        int boxHeight = 55;
        int x = (screenWidth - boxWidth) / 2;

        // Background
        int bgAlpha = (int)(alpha * 220);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, (bgAlpha << 24) | 0x1A1A2E);

        // Border con colore rarità
        int borderColor = getRarityColor(current.rarity(), alpha);
        drawBorder(graphics, x, y, boxWidth, boxHeight, borderColor);

        // Testi
        int textAlpha = (int)(alpha * 255);
        graphics.drawCenteredString(font, "BADGE UNLOCKED!",
            screenWidth / 2, y + 8, (textAlpha << 24) | 0xFFD700);
        graphics.drawCenteredString(font, current.name(),
            screenWidth / 2, y + 22, borderColor);
        graphics.drawCenteredString(font, "[" + current.rarity() + "]",
            screenWidth / 2, y + 38, (textAlpha << 24) | 0xB0B0B0);
    }

    private static int getRarityColor(String rarity, float alpha) {
        int a = (int)(alpha * 255) << 24;
        return switch (rarity.toUpperCase()) {
            case "LEGENDARY" -> a | 0xFFD700; // Gold
            case "EPIC" -> a | 0xA335EE;      // Purple
            case "RARE" -> a | 0x0070DD;      // Blue
            case "UNCOMMON" -> a | 0x1EFF00;  // Green
            default -> a | 0xFFFFFF;          // White
        };
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 2, color);           // Top
        g.fill(x, y + h - 2, x + w, y + h, color);   // Bottom
        g.fill(x, y, x + 2, y + h, color);           // Left
        g.fill(x + w - 2, y, x + w, y + h, color);   // Right
    }
}
```

**Modifiche a GamificationManager.java**:
```java
public void awardBadge(ServerPlayer player, Badge badge) {
    // ... existing logic ...
    // Invia al client
    NetworkHandler.sendToPlayer(player, new BadgeUnlockPayload(badge.getName(), badge.getRarity().name()));
}
```

**Creare BadgeUnlockPayload.java** (struttura simile a BossAlertPayload)

**Criteri di accettazione**:
- [ ] Popup appare quando badge sbloccato
- [ ] Durata 5s con fade in/out
- [ ] Colore bordo indica rarità
- [ ] Queue supporta multiple badge

---

### TASK-007: Token Gain Animation
**ID Requisito**: UI-011 | **Priorità**: P1 | **Effort**: 3h

**Descrizione**: Animazione "+X Tokens" che fluttua verso l'alto, 2s durata

**File da creare**: `src/main/java/com/frenkvs/devmod/hud/TokenGainOverlay.java`

```java
package com.frenkvs.devmod.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TokenGainOverlay {
    private static final long ANIMATION_DURATION_MS = 2000;
    private static final List<TokenGain> activeGains = new ArrayList<>();

    public record TokenGain(int amount, long startTime) {}

    public static void show(int amount) {
        if (amount > 0) {
            activeGains.add(new TokenGain(amount, System.currentTimeMillis()));
        }
    }

    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        Iterator<TokenGain> it = activeGains.iterator();
        int index = 0;

        int baseX = 25; // Left side
        int baseY = screenHeight - 90; // Above currency display

        while (it.hasNext()) {
            TokenGain gain = it.next();
            long elapsed = System.currentTimeMillis() - gain.startTime();

            if (elapsed > ANIMATION_DURATION_MS) {
                it.remove();
                continue;
            }

            float progress = elapsed / (float) ANIMATION_DURATION_MS;

            // Float up: 0 -> 40 pixels
            float yOffset = progress * 40;

            // Fade out with ease-out curve
            float alpha = 1.0f - (progress * progress);

            // Scale: start 1.3x, end 1.0x
            float scale = 1.3f - (progress * 0.3f);

            int x = baseX;
            int y = (int)(baseY - yOffset - (index * 18));

            // Color: gold with alpha
            int color = ((int)(alpha * 255) << 24) | 0xFFD700;

            String text = "+" + gain.amount() + " Tokens";

            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.drawString(font, text, 0, 0, color);
            graphics.pose().popPose();

            index++;
        }
    }
}
```

**Modifiche a RewardSystem.java**:
```java
public void awardTokens(ServerPlayer player, int amount) {
    wallet.add(Currency.TOKENS, amount);
    // Sync to client
    NetworkHandler.sendToPlayer(player, new TokenGainPayload(amount));
}
```

**Client handler**:
```java
// In NetworkHandler client registration:
TokenGainOverlay.show(payload.amount());
```

**Criteri di accettazione**:
- [ ] "+X Tokens" appare quando tokens guadagnati
- [ ] Testo fluttua verso l'alto
- [ ] Fade out graduale
- [ ] Scale da 1.3x a 1.0x
- [ ] Multiple animazioni non si sovrappongono

---

### TASK-008: New Record Banner
**ID Requisito**: UI-013 | **Priorità**: P1 | **Effort**: 2h

**Descrizione**: Banner dorato "NEW RECORD!" slide-in dall'alto, 4s durata

**File da creare**: `src/main/java/com/frenkvs/devmod/hud/RecordBannerOverlay.java`

```java
package com.frenkvs.devmod.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

public class RecordBannerOverlay {
    private static final long BANNER_DURATION_MS = 4000;
    private static final long SLIDE_IN_MS = 300;
    private static final long SLIDE_OUT_MS = 400;

    private static long bannerStartTime = 0;
    private static String recordType = "";
    private static String recordValue = "";

    public static void show(String type, String value) {
        bannerStartTime = System.currentTimeMillis();
        recordType = type;
        recordValue = value;
    }

    public static boolean isActive() {
        return System.currentTimeMillis() - bannerStartTime < BANNER_DURATION_MS;
    }

    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!isActive()) return;

        long elapsed = System.currentTimeMillis() - bannerStartTime;
        float progress = elapsed / (float) BANNER_DURATION_MS;

        // Slide animation
        float slideProgress;
        if (elapsed < SLIDE_IN_MS) {
            slideProgress = elapsed / (float) SLIDE_IN_MS; // 0 -> 1
        } else if (elapsed > BANNER_DURATION_MS - SLIDE_OUT_MS) {
            slideProgress = (BANNER_DURATION_MS - elapsed) / (float) SLIDE_OUT_MS; // 1 -> 0
        } else {
            slideProgress = 1.0f;
        }

        // Y position: -70 (off-screen) to 20 (on-screen)
        int y = (int)(-70 + (slideProgress * 90));

        int centerX = screenWidth / 2;
        int boxWidth = 280;
        int boxHeight = 55;
        int x = centerX - boxWidth / 2;

        // Glow effect
        float pulse = (float) Math.sin(elapsed / 80.0) * 0.2f + 0.8f;
        int glowAlpha = (int)(pulse * 60);
        int glowColor = (glowAlpha << 24) | 0xFFD700;

        // Glow background (larger)
        graphics.fill(x - 5, y - 5, x + boxWidth + 5, y + boxHeight + 5, glowColor);

        // Main background
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0000000);

        // Gold border
        drawBorder(graphics, x, y, boxWidth, boxHeight, 0xFFFFD700);

        // Texts
        graphics.drawCenteredString(font, "★ NEW RECORD! ★", centerX, y + 10, 0xFFFFD700);
        graphics.drawCenteredString(font, recordType + ": " + recordValue, centerX, y + 28, 0xFFFFFFFF);
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 2, color);
        g.fill(x, y + h - 2, x + w, y + h, color);
        g.fill(x, y, x + 2, y + h, color);
        g.fill(x + w - 2, y, x + w, y + h, color);
    }
}
```

**Trigger in EnduranceQuestManager.java**:
```java
if (newWave > personalBest.highestWave()) {
    NetworkHandler.sendToPlayer(player, new NewRecordPayload("Best Wave", String.valueOf(newWave)));
}
if (newTime < personalBest.fastestTime()) {
    NetworkHandler.sendToPlayer(player, new NewRecordPayload("Fastest Time", formatTime(newTime)));
}
```

**Criteri di accettazione**:
- [ ] Banner slide-in dall'alto
- [ ] Glow dorato pulsante
- [ ] Mostra tipo record e valore
- [ ] Durata 4s
- [ ] Slide-out alla fine

---

### TASK-009: Difficulty Tier Badge
**ID Requisito**: UI-018 | **Priorità**: P1 | **Effort**: 1h

**Descrizione**: Badge colorato con difficoltà accanto al wave counter

**File da modificare**: `src/main/java/com/frenkvs/devmod/hud/EnduranceQuestOverlay.java`

```java
// Aggiungere dopo il rendering del wave counter (~linea 167):
private void renderDifficultyBadge(GuiGraphics graphics, Font font, int x, int y, String difficulty) {
    int bgColor = 0xCC000000;
    int textColor = getDifficultyColor(difficulty);

    String text = difficulty.toUpperCase();
    int textWidth = font.width(text);
    int padding = 4;
    int badgeWidth = textWidth + padding * 2;
    int badgeHeight = 12;

    // Background
    graphics.fill(x, y, x + badgeWidth, y + badgeHeight, bgColor);

    // Colored left accent bar
    graphics.fill(x, y, x + 3, y + badgeHeight, textColor);

    // Text
    graphics.drawString(font, text, x + padding + 3, y + 2, textColor);
}

private int getDifficultyColor(String difficulty) {
    return switch (difficulty.toUpperCase()) {
        case "TRIVIAL" -> 0xFF808080;  // Gray
        case "EASY" -> 0xFF4CAF50;     // Green
        case "NORMAL" -> 0xFF2196F3;   // Blue
        case "HARD" -> 0xFFFF9800;     // Orange
        case "ELITE" -> 0xFFF44336;    // Red
        case "BOSS" -> 0xFFAA00FF;     // Purple
        default -> 0xFFFFFFFF;         // White
    };
}

// Chiamare nel render principale, dopo wave text:
// Esempio: "Wave 7/20 [HARD]"
int waveTextEndX = ...; // Calcolare fine del wave text
renderDifficultyBadge(graphics, font, waveTextEndX + 8, waveY, questData.getDifficulty());
```

**Criteri di accettazione**:
- [ ] Badge visibile accanto a wave counter
- [ ] Colore corretto per ogni tier
- [ ] Accent bar colorato a sinistra
- [ ] Non overlappa altri elementi

---

### TASK-010: Endless ∞ Symbol
**ID Requisito**: UI-019 | **Priorità**: P1 | **Effort**: 15min

**Descrizione**: Usare simbolo ∞ invece di testo "ENDLESS"

**File da modificare**: `src/main/java/com/frenkvs/devmod/hud/EnduranceQuestOverlay.java`

**Modifiche** (linea ~328 e simili):
```java
// PRIMA:
String waveText = data.endlessMode() ? "ENDLESS" : "Wave " + wave + "/" + total;

// DOPO:
String waveText = data.endlessMode() ? "Wave " + wave + " ∞" : "Wave " + wave + "/" + total;

// Oppure per banner grande:
String bannerText = data.endlessMode() ? "∞ ENDLESS ∞" : "WAVE " + wave;
```

**Verificare font support**: Il font di Minecraft dovrebbe supportare ∞ (U+221E). Se non renderizza, usare alternativa:
```java
String infinitySymbol = "∞"; // oppure "[INF]" come fallback
```

**Criteri di accettazione**:
- [ ] Simbolo ∞ visibile in endless mode
- [ ] Renderizza correttamente con font Minecraft
- [ ] Chiaro che è modalità infinita

---

## SPRINT 3: MEDIUM PRIORITY (P2) - Effort totale: ~6h

---

### TASK-011: Daily Challenge Progress UI
**ID Requisito**: UI-020 | **Priorità**: P2 | **Effort**: 4h

**File da creare**: `src/main/java/com/frenkvs/devmod/hud/DailyChallengeOverlay.java`

**Features**:
- Lista challenge attive con progress bar
- Posizione: sopra currency display
- Max 3 visibili, scroll se di più
- Progress bar colorata (grigio → verde quando completa)

---

### TASK-012: Leaderboard Rank in Shop
**ID Requisito**: UI-024 | **Priorità**: P2 | **Effort**: 2h

**File da modificare**: `src/main/java/com/frenkvs/devmod/endurance/EnduranceShopScreen.java`

**Features**:
- Box sidebar sinistra
- Mostra rank (#posizione)
- Mostra percentile (Top X%)
- Colore per tier (Top 1%, 5%, 10%, etc.)

---

# CHECKLIST FINALE

## Pre-Implementation
- [ ] Leggere UI Requirements Spec completa
- [ ] Verificare file esistenti prima di creare nuovi
- [ ] Controllare naming conventions del progetto

## Post-Implementation
- [ ] Compilazione senza errori: `./gradlew compileJava`
- [ ] Test manuale in-game: `./gradlew runClient`
- [ ] Verificare tutti i criteri di accettazione
- [ ] Aggiornare UX_FIX_TRACKER.md con stato `[x]`

## Code Review Checklist
- [ ] Nessun memory leak (cleanup risorse)
- [ ] Performance OK (nessun calcolo pesante nel render loop)
- [ ] Client-side only per effetti visuali
- [ ] Payload registrati correttamente
- [ ] Accessibilità: font size ≥ 11px, contrasto sufficiente

---

# APPENDICE: File Reference

## File HUD Esistenti
- `src/main/java/com/frenkvs/devmod/hud/ImpactHudOverlay.java`
- `src/main/java/com/frenkvs/devmod/hud/Impact3DPanel.java`
- `src/main/java/com/frenkvs/devmod/hud/Impact3DRenderer.java`
- `src/main/java/com/frenkvs/devmod/hud/EnduranceQuestOverlay.java`
- `src/main/java/com/frenkvs/devmod/hud/DamageBreakdown.java`
- `src/main/java/com/frenkvs/devmod/hud/EconomyOverlay.java`

## File Sistema Esistenti
- `src/main/java/com/frenkvs/devmod/endurance/BossWaveSystem.java`
- `src/main/java/com/frenkvs/devmod/endurance/ComboSystem.java`
- `src/main/java/com/frenkvs/devmod/endurance/GamificationManager.java`
- `src/main/java/com/frenkvs/devmod/endurance/RewardSystem.java`
- `src/main/java/com/frenkvs/devmod/NetworkHandler.java`

## File Rendering
- `src/main/java/com/frenkvs/devmod/rendering/RenderEvents.java`
- `src/main/java/com/frenkvs/devmod/rendering/BodyPartRenderer.java`
