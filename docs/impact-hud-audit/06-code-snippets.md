# Impact HUD - Code Snippets di Riferimento

Questo documento contiene i frammenti di codice più rilevanti per comprendere il funzionamento del sistema.

---

## 1. Entry Point - DamageHandler

### Intercettazione Danno
```java
// DamageHandler.java:57-58
@SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
public static void onDamage(LivingIncomingDamageEvent event) {
```

### Identificazione Attacco Melee vs Ranged
```java
// DamageHandler.java:76-116
if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
    // RANGED: Use arrow Y coordinate (100% PRECISION)
    weapon = attacker.getMainHandItem();
    part = HitHelper.getBodyPart(victim, arrow.getY());
    hitPoint = arrow.position();
    Vec3 delta = arrow.getDeltaMovement();
    slashDirection = (delta.lengthSqr() > 0.0001) ? delta.normalize() : arrow.getViewVector(1.0f);
    isRanged = true;
} else {
    // MELEE: Use AABB subdivision raycast (95% PRECISION)
    weapon = attacker.getMainHandItem();
    HitHelper.HitResult hitResult = HitHelper.rayTraceBodyPartWithHitPoint(attacker, victim);
    part = hitResult.part();
    hitPoint = hitResult.hitPoint();
    slashDirection = attacker.getViewVector(1.0F);
    isRanged = false;
}
```

### Calcolo Danno Finale
```java
// DamageHandler.java:136-160
float originalDamage = event.getAmount();
float newDamage = (originalDamage + stats.baseDamageBonus) * multiplier;

// Damage Bonus
if (stats.damageBonus > 0) {
    newDamage += originalDamage * stats.damageBonus;
}

// Armor Penetration
float targetArmor = Math.max(0f, victim.getArmorValue() - stats.armorShred);
if (stats.armorPenetration > 0) {
    armorPenBonus = calculateArmorPenBonus(stats.armorPenetration, targetArmor, newDamage);
    newDamage += armorPenBonus;
}

// Custom Armor Reduction (DevMod)
if (victim instanceof Player playerVictim) {
    armorReduction = calculateCustomArmorReduction(playerVictim, event.getSource());
    newDamage = newDamage * (1.0f - armorReduction);
}

event.setAmount(newDamage);
```

### Creazione ImpactData
```java
// DamageHandler.java:237-261
DamageBreakdown breakdown = new DamageBreakdown(
    weapon,
    victim,
    originalDamage,
    multiplier,
    armorPenBonus
);

ImpactData impactData = new ImpactData(
    attacker.getUUID(),
    victim,
    part,
    multiplier,
    breakdown,
    attackSource,
    isRanged,
    hitPoint,
    slashDirection
);
ImpactData.store(impactData);
```

---

## 2. Body Part Detection - HitHelper

### Raycast AABB Algorithm
```java
// HitHelper.java:202-330
private static HitResult calculateBodyPartWithHitPoint(LivingEntity attacker, LivingEntity target) {
    AABB mainBox = target.getBoundingBox();
    Vec3 center = mainBox.getCenter();
    double width = mainBox.getXsize();
    double height = mainBox.getYsize();
    double depth = mainBox.getZsize();

    // Adaptive mode for non-humanoid hitboxes
    double aspectRatio = Math.max(width, depth) / height;
    boolean isHorizontalBody = aspectRatio > 2.0;  // Dragon, fish
    boolean isTallBody = height > 3.0 && aspectRatio < 0.5;  // Enderman

    // Attacker raycast
    Vec3 eye = attacker.getEyePosition();
    Vec3 look = attacker.getViewVector(1.0F);
    double reach = getDynamicReach(attacker);  // From attributes
    Vec3 end = eye.add(look.scale(reach));

    // HEAD (top 25%)
    double headHeight = height * 0.25;
    AABB headBox = new AABB(
        center.x - width/2, mainBox.maxY - headHeight, center.z - depth/2,
        center.x + width/2, mainBox.maxY, center.z + depth/2
    );
    Optional<Vec3> headHit = headBox.clip(eye, end);
    if (headHit.isPresent()) {
        return HitResult.of(BodyPart.HEAD, headHit.get());
    }

    // ARMS (lateral 30% of torso zone)
    // ... similar logic

    // BODY (center 40% of torso zone)
    // ... similar logic

    // LEGS (bottom 35%)
    // ... similar logic

    // Fallback: pitch-based
    double pitch = attacker.getXRot();
    if (pitch < -15) return HitResult.of(BodyPart.HEAD, center);
    if (pitch > 25) return HitResult.of(BodyPart.LEGS, center);
    return HitResult.of(BodyPart.BODY, center);
}
```

### Cache Implementation
```java
// HitHelper.java:44-82
private static final Map<CacheKey, CacheEntry> BODY_PART_CACHE = new ConcurrentHashMap<>();

private record CacheKey(UUID attackerId, UUID targetId, int positionHash) {
    static CacheKey of(LivingEntity attacker, LivingEntity target) {
        Vec3 pos = target.position();
        int hash = (int)(pos.x * 10) ^ (int)(pos.y * 10) ^ (int)(pos.z * 10);
        return new CacheKey(attacker.getUUID(), target.getUUID(), hash);
    }
}

private record CacheEntry(BodyPart bodyPart, Vec3 hitPoint, long timestamp) {
    boolean isExpired() {
        return System.currentTimeMillis() - timestamp > getCacheTtlMs();  // 100ms default
    }
}
```

---

## 3. Damage Breakdown - DamageBreakdown

### Enchant Bonus Calculation
```java
// damage/DamageBreakdown.java:57-98
private void calculateEnchantBonuses(ItemStack weapon, LivingEntity target) {
    ItemEnchantments enchantments = weapon.get(DataComponents.ENCHANTMENTS);
    if (enchantments == null || enchantments.isEmpty()) return;

    for (Holder<Enchantment> holder : enchantments.keySet()) {
        int level = enchantments.getLevel(holder);
        String enchName = holder.getRegisteredName();

        // Sharpness: +1.0 + 0.5 per additional level
        if (enchName.contains("sharpness")) {
            float bonus = 1.0f + (level - 1) * 0.5f;
            enchantBonuses.add(new EnchantBonus("Sharpness " + toRoman(level), level, bonus));
        }
        // Smite: +2.5 per level vs undead
        else if (enchName.contains("smite")) {
            if (target.isInvertedHealAndHarm()) {
                float bonus = level * 2.5f;
                enchantBonuses.add(new EnchantBonus("Smite " + toRoman(level), level, bonus));
            }
        }
        // Bane of Arthropods: +2.5 per level vs arthropods
        else if (enchName.contains("bane_of_arthropods")) {
            if (isArthropod(target)) {
                float bonus = level * 2.5f;
                enchantBonuses.add(new EnchantBonus("Bane " + toRoman(level), level, bonus));
            }
        }
    }
}
```

### Formula String Generation (Cached)
```java
// damage/DamageBreakdown.java:132-154 (now cached in constructor - BUG-010 FIXED)
public String getFormulaString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("(%.1f", baseWeaponDamage));

    for (EnchantBonus eb : enchantBonuses) {
        if (eb.bonus() > 0) {
            sb.append(String.format("+%.1f", eb.bonus()));
        }
    }

    if (pehkuiSizeBonus > 0) {
        sb.append(String.format("+%.1f", pehkuiSizeBonus));
    }

    sb.append(String.format(") * %.2f", bodyPartMultiplier));

    if (armorPenetrationBonus > 0) {
        sb.append(String.format(" + %.1f", armorPenetrationBonus));
    }

    sb.append(String.format(" = %.1f", finalDamage));
    return sb.toString();
}
```

---

## 4. ImpactData Storage

### Per-Player Isolation
```java
// ImpactData.java:24-26
private static final Map<UUID, ImpactData> IMPACTS_BY_PLAYER = new ConcurrentHashMap<>();

public static void store(ImpactData data) {
    if (data == null || data.attackerUUID == null) return;
    IMPACTS_BY_PLAYER.put(data.attackerUUID, data);
    maybeCleanup();
}
```

### Observation State Machine
```java
// ImpactData.java:229-264
public void setObserved(boolean observed) {
    if (this.isBeingObserved && !observed) {
        // Player just looked away - start timer
        this.stoppedLookingTimestamp = System.currentTimeMillis();
    } else if (observed) {
        // Player is looking - reset timer
        this.stoppedLookingTimestamp = -1;
    }
    this.isBeingObserved = observed;
}

public boolean isExpired() {
    if (isBeingObserved) return false;

    if (stoppedLookingTimestamp < 0) {
        return System.currentTimeMillis() - timestamp > DISPLAY_DURATION_MS;
    }

    return System.currentTimeMillis() - stoppedLookingTimestamp > DISPLAY_DURATION_MS;
}

public float getRemainingAlpha() {
    if (isBeingObserved) return 1.0f;

    long referenceTime = stoppedLookingTimestamp > 0 ? stoppedLookingTimestamp : timestamp;
    long elapsed = System.currentTimeMillis() - referenceTime;

    if (elapsed > DISPLAY_DURATION_MS) return 0f;

    long fadeStart = DISPLAY_DURATION_MS - FADE_DURATION_MS;
    if (elapsed > fadeStart) {
        float fadeProgress = (elapsed - fadeStart) / (float) FADE_DURATION_MS;
        return 1.0f - fadeProgress;
    }

    return 1.0f;
}
```

---

## 5. Actual Damage Tracking

### Post-Event Capture
```java
// ActualDamageTracker.java:29-57
@SubscribeEvent(priority = EventPriority.LOWEST)
public static void onDamagePost(LivingDamageEvent.Post event) {
    LivingEntity entity = event.getEntity();
    int entityId = entity.getId();

    // Get REAL damage from NeoForge API
    float actualDamage = event.getNewDamage();

    // Calculate health before/after
    float healthAfter = entity.getHealth();
    float healthBefore = healthAfter + actualDamage;

    if (entity.isDeadOrDying()) {
        healthBefore = actualDamage;
    }

    // Update ImpactData
    ImpactData impact = ImpactData.get();
    if (impact != null) {
        LivingEntity target = impact.getTarget();
        if (target != null && target.getId() == entityId) {
            impact.setActualDamage(healthBefore, healthAfter, actualDamage);
        }
    }
}
```

---

## 6. HUD 2D Rendering

### Main Render Loop
```java
// ImpactHudOverlay.java:77-130
private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
    if (!enabled) return;

    ImpactData data = ImpactData.get();
    if (data == null) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.options.hideGui) return;

    int screenWidth = graphics.guiWidth();
    int screenHeight = graphics.guiHeight();
    Font font = mc.font;

    int panelWidth = 260;
    int panelHeight = calculatePanelHeight(data, font);

    // Position: right corner with margin
    int panelX = screenWidth - panelWidth - 10;
    int panelY = 10;

    // Observation check
    int crosshairX = screenWidth / 2;
    int crosshairY = screenHeight / 2;
    boolean isObserving = isCrosshairOverPanel(crosshairX, crosshairY);
    data.setObserved(isObserving);

    float alpha = data.getRemainingAlpha();
    if (alpha <= 0.01f) return;

    renderImpactPanel(graphics, font, data, panelX, panelY, panelWidth, panelHeight, alpha);

    if (data.hasPehkuiModification() || data.isBetterCombatAttack()) {
        int modPanelY = panelY + panelHeight + 8;
        renderModSpecificsPanel(graphics, font, data, panelX, modPanelY, panelWidth, alpha);
    }
}
```

### Panel Background
```java
// ImpactHudOverlay.java:286-299
private static void renderPanelBackground(GuiGraphics g, int x, int y, int width, int height, float alpha) {
    // Outer glow
    g.fill(x - 1, y - 1, x + width + 1, y + height + 1, applyAlpha(PANEL_BORDER_GLOW, alpha * 0.3f));

    // Main background
    g.fill(x, y, x + width, y + height, applyAlpha(PANEL_BG, alpha));

    // Border
    int borderColor = applyAlpha(PANEL_BORDER, alpha * 0.8f);
    g.fill(x, y, x + width, y + 1, borderColor);                    // Top
    g.fill(x, y + height - 1, x + width, y + height, borderColor);  // Bottom
    g.fill(x, y, x + 1, y + height, borderColor);                   // Left
    g.fill(x + width - 1, y, x + width, y + height, borderColor);   // Right
}
```

---

## 7. 3D Panel Rendering

### Billboard Rotation
```java
// Impact3DRenderer.java:64-95
public void renderPanel(PoseStack poseStack, MultiBufferSource bufferSource,
                        Vec3 cameraPos, Vec3 panelWorldPos, Vec3 hitPoint,
                        ImpactData data, float alpha) {
    if (alpha <= 0.01f) return;

    // Render connection line
    renderConnectionLine(poseStack, bufferSource, cameraPos, hitPoint, panelWorldPos, alpha);

    poseStack.pushPose();

    // Translate to panel position
    Vec3 relativePos = panelWorldPos.subtract(cameraPos);
    poseStack.translate(relativePos.x, relativePos.y, relativePos.z);

    // Billboard rotation (face camera)
    Vec3 toCamera = cameraPos.subtract(panelWorldPos).normalize();
    float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
    poseStack.mulPose(new Quaternionf().rotationY(yaw));

    // Scale (negative Y for vertical flip)
    poseStack.scale(PANEL_SCALE, -PANEL_SCALE, PANEL_SCALE);

    // Center panel
    poseStack.translate(-PANEL_WIDTH_PX / 2, -PANEL_HEIGHT_PX / 2, 0);

    renderPanelContent(poseStack, bufferSource, data, alpha, mc.font);

    poseStack.popPose();
}
```

### 3D Text Rendering
```java
// Impact3DRenderer.java:336-362
private void renderText3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                           String text, float x, float y, int color, float globalAlpha) {
    poseStack.pushPose();
    poseStack.translate(x, y, -0.5f);

    Matrix4f matrix = poseStack.last().pose();

    // Apply alpha
    int alpha = (int) (((color >> 24) & 0xFF) * globalAlpha);
    if (alpha == 0) alpha = (int) (255 * globalAlpha);
    int finalColor = (alpha << 24) | (color & 0x00FFFFFF);

    // SEE_THROUGH for correct 3D rendering
    font.drawInBatch(
        text,
        0, 0,
        finalColor,
        false,  // no shadow
        matrix,
        bufferSource,
        Font.DisplayMode.SEE_THROUGH,
        0,        // backgroundColor
        15728880  // full bright
    );

    poseStack.popPose();
}
```

---

## 8. VFX Effects

### Energy Vortex Spiral
```java
// ImpactVFX.java:136-158
// Outer spiral (clockwise rotation)
float baseRadius = 0.25f * pulseScale;
int spiralSegments = 32;
float spiralRotations = 2.0f;

for (int i = 0; i <= spiralSegments; i++) {
    float t = (float) i / spiralSegments;
    float angle = rotation + t * spiralRotations * (float) Math.PI * 2;
    float radius = baseRadius * (1.0f - t * 0.6f);  // Narrows towards center

    float x = (float) Math.cos(angle) * radius;
    float y = (float) Math.sin(angle) * radius * 0.5f;  // Vertically flattened
    float z = (float) Math.sin(angle) * radius;

    // Color fading from primary to secondary
    float colorMix = t;
    float r = r1 * (1 - colorMix) + r2 * colorMix;
    float g = g1 * (1 - colorMix) + g2 * colorMix;
    float b = b1 * (1 - colorMix) + b2 * colorMix;

    consumer.addVertex(matrix, x, y, z)
        .setColor(r, g, b, alpha * (1.0f - t * 0.5f))
        .setNormal(0, 1, 0);
}
```

### Slash Animation
```java
// ImpactVFX.java:269-307
// Animation: Blade moves from left to right
// progress 0.0 -> blade on left
// progress 0.5 -> blade at center (impact point)
// progress 1.0 -> blade on right

float bladePos = (progress * 2.0f - 1.0f) * slashLength;

// Cut trail
float trailStart = -slashLength;
float trailEnd = Math.min(bladePos, slashLength);

for (int i = 0; i <= trailSegments; i++) {
    float t = (float) i / trailSegments;
    float pos = trailStart + (trailEnd - trailStart) * t;

    float x = perpX * pos;
    float z = perpZ * pos;

    // Vertical arc (curved cut)
    float arcT = (pos + slashLength) / (2 * slashLength);
    float y = (float) Math.sin(arcT * Math.PI) * slashHeight;

    // Alpha: stronger near blade, fades towards tail
    float distFromBlade = Math.abs(pos - bladePos);
    float trailAlpha = baseAlpha * Math.max(0, 1.0f - distFromBlade / slashLength);

    consumer.addVertex(matrix, x, y, z)
        .setColor(cr, cg, cb, trailAlpha)
        .setNormal(0, 1, 0);
}
```

---

## 9. Armor Penetration Formulas

### All Four Formulas
```java
// DamageHandler.java:431-459
return switch (formula) {
    case SIMPLE -> {
        // Original: armorPen * armorValue * multiplier
        float ignoredArmor = armorValue * armorPen;
        yield ignoredArmor * (float) multiplier;
    }
    case VANILLA_ACCURATE -> {
        // Uses Minecraft's armor formula
        float effectiveArmor = Math.min(20f, Math.max(armorValue / 5f, armorValue - baseDamage / 2f));
        float armorReduction = effectiveArmor / 25f;
        float blockedDamage = baseDamage * armorReduction;
        yield blockedDamage * armorPen * (float) multiplier;
    }
    case PERCENTAGE -> {
        // Reduces armor effectiveness by percentage
        float effectiveArmor = Math.min(20f, armorValue);
        float normalReduction = effectiveArmor / 25f;
        float reducedReduction = normalReduction * (1f - armorPen);
        float bonusDamage = baseDamage * (normalReduction - reducedReduction);
        yield bonusDamage * (float) multiplier;
    }
    case FLAT_BONUS -> {
        // Adds flat true damage
        yield armorPen * (float) flatBonus;
    }
};
```

---

## 10. Enderman Evasion Detection

### Attack Tracking
```java
// DamageHandler.java:540-564
@SubscribeEvent
public static void onAttackEntity(AttackEntityEvent event) {
    if (!(event.getTarget() instanceof LivingEntity target)) return;

    Player player = event.getEntity();
    long now = System.currentTimeMillis();

    // Save Enderman's position NOW, before it teleports!
    Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
    Vec3 playerEye = player.getEyePosition();
    Vec3 lookDir = player.getLookAngle();

    pendingAttacks.put(target.getId(), new PendingAttack(now, targetPos, playerEye, lookDir));

    if (target instanceof EnderMan) {
        scheduleEvasionCheck(player, target, now);
    }
}
```

### Deferred Evasion Check
```java
// DamageHandler.java:571-595
private static void scheduleEvasionCheck(Player player, LivingEntity target, long attackTime) {
    final int targetId = target.getId();

    EVASION_SCHEDULER.schedule(() -> {
        synchronized (EVASION_LOCK) {
            PendingAttack attackData = pendingAttacks.remove(targetId);
            if (attackData == null) return;

            Long hitTime = confirmedHits.remove(targetId);
            if (hitTime == null || hitTime < attackTime) {
                // Damage NOT confirmed -> EVASION!
                LOGGER.debug("EVASION DETECTED! Enderman evaded at {}", attackData.targetPosition);
                ClientVFXProxy.spawnMeleeEvasionPanel(player, target, attackData.targetPosition, attackData.lookDir);
            }
        }
    }, 150, TimeUnit.MILLISECONDS);
}
```
