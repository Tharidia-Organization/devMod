package com.devmod.client.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.ModList;

import com.devmod.DevMod;
import com.devmod.client.rendering.TrigCache;
import com.devmod.client.rendering.shader.VFXShaderRegistry;

public class WeaponTrailVFX {

    // Singleton instance
    public static final WeaponTrailVFX INSTANCE = new WeaponTrailVFX();

    // Trail configuration
    private static final int MAX_TRAIL_POINTS = 16;
    private static final long TRAIL_DURATION_MS = 200;
    private static final float MIN_DISTANCE_THRESHOLD = 0.02f;

    // Trail state
    private final Deque<TrailPoint> trailPoints = new ArrayDeque<>();
    private boolean isSwinging = false;
    private int lastSwingCount = 0;

    // BetterCombat compatibility
    private static Boolean betterCombatPresent = null;
    private static boolean betterCombatEventsRegistered = false;
    private boolean lastAttackKeyState = false;
    private long swingStartTime = 0;
    private static final long SWING_DURATION_MS = 300; // Duration to track after attack starts
    private float lastAttackAnim = 0; // Track attack animation progress

    // BetterCombat event-driven swing detection
    private volatile boolean betterCombatSwingActive = false;
    private volatile long betterCombatSwingStartTime = 0;

    // Trail colors - dynamically extracted from weapon
    private float trailR = 0.8f, trailG = 0.9f, trailB = 1.0f;
    private float glowR = 0.5f, glowG = 0.7f, glowB = 1.0f;
    private boolean useWeaponColor = true;  // Auto-extract color from weapon
    private ItemStack lastWeaponItem = ItemStack.EMPTY;  // Track weapon for color update
    private float colorEnhance = 2.0f;      // COLOR_EN: 1-2, default 2 (like EpicFight)
    private float emissiveStrength = 1.2f;  // EXTRA_LIGHT equivalent
    private float opacity = 1.0f;           // OPACITY: 0-1, default 1
    private int textureLight = 255;         // TEXTURE_LIGHT: 0-255 for forced emissive
    private boolean darkRendering = true;   // DARK_RENDERING: different blend for dark scenes

    // Enable/disable
    private boolean enabled = true;

    // Iris shader detection (like ShaderHelper.java)
    private static Boolean irisShaderActive = null;
    private static long lastIrisCheck = 0;
    private static final long IRIS_CHECK_INTERVAL_MS = 1000; // Re-check every second

    private WeaponTrailVFX() {}

    /**
     * Called every client tick to track weapon position.
     */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !enabled) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();

        // Only track for weapons
        if (!isWeapon(mainHand)) {
            clearTrail();
            return;
        }

        // Update trail color based on weapon
        updateWeaponColor(mainHand);

        long now = System.currentTimeMillis();

        // Detect swing using multiple methods for BetterCombat compatibility
        boolean swingDetected = detectSwing(player, mc, now);

        // Track position during swing or while trail is fading
        if (swingDetected || !trailPoints.isEmpty()) {
            trackWeaponPosition(player);
        }

        // Clean up old points
        while (!trailPoints.isEmpty() && now - trailPoints.peekFirst().timestamp > TRAIL_DURATION_MS) {
            trailPoints.pollFirst();
        }
    }

    // Debug logging (disable after testing)
    private static final boolean DEBUG_SWING_DETECTION = true;

    /**
     * Detect weapon swing using multiple methods for mod compatibility.
     * Works with vanilla Minecraft and BetterCombat.
     *
     * IMPORTANT: Only detect actual attacks, NOT camera movement or walking.
     */
    private boolean detectSwing(Player player, Minecraft mc, long now) {
        // Check BetterCombat presence once and register events
        if (betterCombatPresent == null) {
            betterCombatPresent = isBetterCombatLoaded();
            if (betterCombatPresent) {
                // Register event listeners for BetterCombat's attack events
                registerBetterCombatEvents();
            }
        }

        // === BetterCombat Event-Driven Detection (Primary when available) ===
        // This is the most reliable method as it uses BC's own event system
        if (betterCombatPresent && betterCombatSwingActive) {
            // BC event triggered a swing - use event timing
            isSwinging = true;
            swingStartTime = betterCombatSwingStartTime;

            // Check if BC swing should end (use longer duration for BC animations)
            long bcSwingDuration = SWING_DURATION_MS + 150; // BC animations are often longer
            if ((now - betterCombatSwingStartTime) > bcSwingDuration) {
                betterCombatSwingActive = false;
                isSwinging = false;
                if (DEBUG_SWING_DETECTION) {
                    DevMod.LOGGER.info("[WeaponTrailVFX] BetterCombat swing END (duration expired)");
                }
            }
            return isSwinging;
        }

        // === Fallback Methods for Vanilla or when BC events don't fire ===

        // Method 1: Vanilla swingTime - most reliable for vanilla
        // swingTime counts down from swing duration to 0 during an attack
        int currentSwingTime = player.swingTime;
        if (currentSwingTime > 0 && lastSwingCount == 0) {
            // Swing just started
            isSwinging = true;
            swingStartTime = now;
            if (DEBUG_SWING_DETECTION) {
                DevMod.LOGGER.info("[WeaponTrailVFX] Swing START: swingTime={}", currentSwingTime);
            }
        }
        lastSwingCount = currentSwingTime;

        // Method 2: Attack animation progress (getAttackAnim)
        // This returns 0-1 during attack animation, useful for BetterCombat
        float currentAttackAnim = player.getAttackAnim(1.0f);
        if (currentAttackAnim > 0.01f && lastAttackAnim < 0.01f) {
            // Attack animation just started
            if (!isSwinging) {
                isSwinging = true;
                swingStartTime = now;
                if (DEBUG_SWING_DETECTION) {
                    DevMod.LOGGER.info("[WeaponTrailVFX] Swing START: attackAnim={}", currentAttackAnim);
                }
            }
        }
        lastAttackAnim = currentAttackAnim;

        // Method 3: Attack key + cooldown check (for BetterCombat which may not trigger swingTime)
        boolean attackKeyPressed = mc.options.keyAttack.isDown();
        if (attackKeyPressed && !lastAttackKeyState && isWeapon(player.getMainHandItem())) {
            // Attack key just pressed - but only count if not already swinging
            // and player can actually attack (not on cooldown)
            float cooldown = player.getAttackStrengthScale(0);
            if (cooldown >= 0.9f && !isSwinging) {
                isSwinging = true;
                swingStartTime = now;
                if (DEBUG_SWING_DETECTION) {
                    DevMod.LOGGER.info("[WeaponTrailVFX] Swing START: attackKey (cooldown={})", cooldown);
                }
            }
        }
        lastAttackKeyState = attackKeyPressed;

        // Check if swing should end
        // End when: swingTime is 0 AND attackAnim is near 0 AND duration passed
        boolean swingAnimActive = currentSwingTime > 0 || currentAttackAnim > 0.05f;
        boolean durationPassed = (now - swingStartTime) > SWING_DURATION_MS;

        if (isSwinging && !swingAnimActive && durationPassed) {
            isSwinging = false;
            if (DEBUG_SWING_DETECTION) {
                DevMod.LOGGER.info("[WeaponTrailVFX] Swing END");
            }
        }

        return isSwinging;
    }

    /**
     * Check if BetterCombat mod is loaded.
     * Uses NeoForge ModList API (preferred) with reflection fallback.
     */
    private static boolean isBetterCombatLoaded() {
        // Method 1: Use NeoForge ModList API (most reliable)
        try {
            if (ModList.get().isLoaded("bettercombat")) {
                DevMod.LOGGER.info("[WeaponTrailVFX] BetterCombat detected via ModList API");
                return true;
            }
        } catch (Exception e) {
            DevMod.LOGGER.debug("[WeaponTrailVFX] ModList check failed: {}", e.getMessage());
        }

        // Method 2: Reflection fallback - check multiple possible class names
        String[] possibleClasses = {
            "net.bettercombat.BetterCombat",
            "net.bettercombat.BetterCombatMod",
            "net.bettercombat.client.BetterCombatClient",
            "net.bettercombat.api.WeaponAttributes"
        };

        for (String className : possibleClasses) {
            try {
                Class.forName(className, false, WeaponTrailVFX.class.getClassLoader());
                DevMod.LOGGER.info("[WeaponTrailVFX] BetterCombat detected via reflection: {}", className);
                return true;
            } catch (ClassNotFoundException ignored) {
                // Try next class
            }
        }

        DevMod.LOGGER.info("[WeaponTrailVFX] BetterCombat not detected - using vanilla swing detection");
        return false;
    }

    /**
     * Register BetterCombat event listeners using reflection.
     * This hooks into ATTACK_START and ATTACK_HIT events to detect swings
     * more reliably than vanilla methods.
     */
    private void registerBetterCombatEvents() {
        if (betterCombatEventsRegistered) return;
        betterCombatEventsRegistered = true;

        try {
            // Get BetterCombatClientEvents class
            Class<?> eventsClass = Class.forName("net.bettercombat.api.client.BetterCombatClientEvents");

            // Get ATTACK_START publisher
            Object attackStartPublisher = eventsClass.getField("ATTACK_START").get(null);

            // Get Publisher.register method
            Class<?> publisherClass = attackStartPublisher.getClass();
            java.lang.reflect.Method registerMethod = null;
            for (java.lang.reflect.Method m : publisherClass.getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 1) {
                    registerMethod = m;
                    break;
                }
            }

            if (registerMethod != null) {
                // Get the PlayerAttackStart interface
                Class<?> listenerInterface = Class.forName("net.bettercombat.api.client.BetterCombatClientEvents$PlayerAttackStart");

                // Create a dynamic proxy to handle the callback
                Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    WeaponTrailVFX.class.getClassLoader(),
                    new Class<?>[] { listenerInterface },
                    (proxyObj, method, args) -> {
                        if (method.getName().equals("onPlayerAttackStart")) {
                            // Attack started - trigger our swing detection
                            onBetterCombatAttackStart();
                        }
                        return null;
                    }
                );

                // Register the listener
                registerMethod.invoke(attackStartPublisher, proxy);
                DevMod.LOGGER.info("[WeaponTrailVFX] Successfully registered BetterCombat ATTACK_START event listener");
            }

            // Also register ATTACK_HIT for extended trail during hit
            Object attackHitPublisher = eventsClass.getField("ATTACK_HIT").get(null);
            Class<?> hitListenerInterface = Class.forName("net.bettercombat.api.client.BetterCombatClientEvents$PlayerAttackHit");

            Object hitProxy = java.lang.reflect.Proxy.newProxyInstance(
                WeaponTrailVFX.class.getClassLoader(),
                new Class<?>[] { hitListenerInterface },
                (proxyObj, method, args) -> {
                    if (method.getName().equals("onPlayerAttackHit")) {
                        // Attack hit - extend swing duration slightly
                        onBetterCombatAttackHit();
                    }
                    return null;
                }
            );

            for (java.lang.reflect.Method m : attackHitPublisher.getClass().getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 1) {
                    m.invoke(attackHitPublisher, hitProxy);
                    DevMod.LOGGER.info("[WeaponTrailVFX] Successfully registered BetterCombat ATTACK_HIT event listener");
                    break;
                }
            }

        } catch (ClassNotFoundException e) {
            DevMod.LOGGER.debug("[WeaponTrailVFX] BetterCombat events API not found: {}", e.getMessage());
        } catch (Exception e) {
            DevMod.LOGGER.warn("[WeaponTrailVFX] Failed to register BetterCombat events: {}", e.getMessage());
        }
    }

    /**
     * Called when BetterCombat fires an ATTACK_START event.
     */
    private void onBetterCombatAttackStart() {
        betterCombatSwingActive = true;
        betterCombatSwingStartTime = System.currentTimeMillis();
        if (DEBUG_SWING_DETECTION) {
            DevMod.LOGGER.info("[WeaponTrailVFX] BetterCombat ATTACK_START event received");
        }
    }

    /**
     * Called when BetterCombat fires an ATTACK_HIT event.
     */
    private void onBetterCombatAttackHit() {
        // Extend swing time on hit for better visual feedback
        if (betterCombatSwingActive) {
            betterCombatSwingStartTime = System.currentTimeMillis() - 100; // Extend by 100ms
        }
        if (DEBUG_SWING_DETECTION) {
            DevMod.LOGGER.info("[WeaponTrailVFX] BetterCombat ATTACK_HIT event received");
        }
    }

    /**
     * Track the weapon tip position for trail generation.
     */
    private void trackWeaponPosition(Player player) {
        // Calculate weapon tip position in world space
        Vec3 tipPos = Objects.requireNonNull(calculateWeaponTipPosition(player));
        Vec3 basePos = Objects.requireNonNull(calculateWeaponBasePosition(player));

        long now = System.currentTimeMillis();

        // Only add if moved enough
        if (!trailPoints.isEmpty()) {
            TrailPoint last = trailPoints.peekLast();
            if (last != null && tipPos.distanceTo(Objects.requireNonNull(last.tipPos)) < MIN_DISTANCE_THRESHOLD) {
                return;
            }
        }

        // Add new point
        trailPoints.addLast(new TrailPoint(tipPos, basePos, now));

        // Limit trail length
        while (trailPoints.size() > MAX_TRAIL_POINTS) {
            trailPoints.pollFirst();
        }
    }

    /**
     * Calculate weapon tip position based on player arm and rotation.
     */
    private Vec3 calculateWeaponTipPosition(Player player) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        // Weapon extends forward and down from hand
        float weaponLength = 1.2f;

        // Calculate direction vector
        float yawRad = (float) Math.toRadians(-yaw);
        float pitchRad = (float) Math.toRadians(-pitch);

        // Swing animation offset
        float swingProgress = player.getAttackAnim(1.0f);
        float swingAngle = (float) Math.sin(swingProgress * Math.PI) * 1.5f;

        // Right hand offset
        float rightOffset = 0.4f;
        float rightX = TrigCache.cos(yawRad + (float) Math.PI / 2) * rightOffset;
        float rightZ = TrigCache.sin(yawRad + (float) Math.PI / 2) * rightOffset;

        // Forward direction with pitch
        float forwardX = -TrigCache.sin(yawRad) * TrigCache.cos(pitchRad);
        float forwardY = -TrigCache.sin(pitchRad);
        float forwardZ = TrigCache.cos(yawRad) * TrigCache.cos(pitchRad);

        // Add swing arc
        float swingX = TrigCache.cos(yawRad + swingAngle) * TrigCache.cos(pitchRad);
        float swingZ = TrigCache.sin(yawRad + swingAngle) * TrigCache.cos(pitchRad);

        float tipX = (float) eyePos.x + rightX + (forwardX + swingX) * weaponLength * 0.5f;
        float tipY = (float) eyePos.y - 0.5f + forwardY * weaponLength;
        float tipZ = (float) eyePos.z + rightZ + (forwardZ + swingZ) * weaponLength * 0.5f;

        return new Vec3(tipX, tipY, tipZ);
    }

    /**
     * Calculate weapon base (handle) position.
     */
    private Vec3 calculateWeaponBasePosition(Player player) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        float yaw = player.getYRot();
        float yawRad = (float) Math.toRadians(-yaw);

        // Right hand offset
        float rightOffset = 0.35f;
        float rightX = TrigCache.cos(yawRad + (float) Math.PI / 2) * rightOffset;
        float rightZ = TrigCache.sin(yawRad + (float) Math.PI / 2) * rightOffset;

        return new Vec3(
            eyePos.x + rightX,
            eyePos.y - 0.6f,
            eyePos.z + rightZ
        );
    }

    /**
     * Render the weapon trail.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        if (!enabled || trailPoints.size() < 2) {
            return;
        }

        // Skip rendering if Iris shaders are active (like EpicFightSwordLight does)
        if (isIrisShaderPackActive()) {
            return;
        }

        boolean useGPU = VFXShaderRegistry.isWeaponTrailShaderReady();

        if (useGPU) {
            renderTrailGPU(poseStack, bufferSource, cameraPos);
        } else {
            renderTrailCPU(poseStack, bufferSource, cameraPos);
        }
    }

    /**
     * Check if Iris shader pack is active (like ShaderHelper.java from EpicFightSwordLight).
     * Uses reflection to avoid hard dependency on Iris.
     */
    private boolean isIrisShaderPackActive() {
        long now = System.currentTimeMillis();

        // Cache result to avoid reflection overhead every frame
        if (irisShaderActive != null && (now - lastIrisCheck) < IRIS_CHECK_INTERVAL_MS) {
            return irisShaderActive;
        }
        lastIrisCheck = now;

        try {
            // Try to find Iris API class
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false,
                WeaponTrailVFX.class.getClassLoader());

            // Get getInstance method
            var getInstanceMethod = irisApiClass.getMethod("getInstance");
            Object irisInstance = getInstanceMethod.invoke(null);

            // Get isShaderPackInUse method
            var isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            irisShaderActive = (Boolean) isShaderPackInUseMethod.invoke(irisInstance);

            if (irisShaderActive && DEBUG_SWING_DETECTION) {
                DevMod.LOGGER.debug("[WeaponTrailVFX] Iris shader pack detected, skipping trail rendering");
            }

            return irisShaderActive;
        } catch (ClassNotFoundException e) {
            // Iris not installed, no shader pack active
            irisShaderActive = false;
            return false;
        } catch (Exception e) {
            // Error checking Iris, assume no shader
            irisShaderActive = false;
            return false;
        }
    }

    /**
     * GPU-accelerated trail rendering with custom shader.
     * Matches EpicFightSwordLight's TrailParticleMixin rendering behavior.
     */
    private void renderTrailGPU(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        ShaderInstance shader = VFXShaderRegistry.getWeaponTrailShader();
        RenderType renderType = darkRendering
            ? VFXShaderRegistry.getWeaponTrailDarkRenderType()
            : VFXShaderRegistry.getWeaponTrailRenderType();

        if (shader == null || renderType == null) {
            renderTrailCPU(poseStack, bufferSource, cameraPos);
            return;
        }

        // Set shader uniforms (matching EpicFightSwordLight config options)
        setShaderUniform(shader, "GameTime", (float)(System.currentTimeMillis() % 100000) / 1000.0f);
        setShaderUniformVec3(shader, "TrailColor", trailR, trailG, trailB);
        setShaderUniformVec3(shader, "GlowColor", glowR, glowG, glowB);
        setShaderUniform(shader, "Alpha", opacity);  // OPACITY config
        setShaderUniform(shader, "ColorEnhance", colorEnhance);  // COLOR_EN config
        setShaderUniform(shader, "EmissiveStrength", emissiveStrength);
        setShaderUniform(shader, "TrailLength", (float) trailPoints.size() / MAX_TRAIL_POINTS);
        setShaderUniform(shader, "TextureLight", textureLight / 255.0f);  // TEXTURE_LIGHT config (normalized)
        setShaderUniform(shader, "DarkRendering", darkRendering ? 1.0f : 0.0f);  // DARK_RENDERING config

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();
        var pose = poseStack.last();

        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        renderRibbonGeometry(consumer, matrix, pose);

        poseStack.popPose();
    }

    /**
     * CPU fallback trail rendering.
     */
    private void renderTrailCPU(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(Objects.requireNonNull(poseStack.last()).pose());

        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lightning()));

        // Simple line strip for CPU fallback
        TrailPoint[] points = trailPoints.toArray(new TrailPoint[0]);
        long now = System.currentTimeMillis();

        for (int i = 0; i < points.length - 1; i++) {
            TrailPoint p1 = points[i];
            TrailPoint p2 = points[i + 1];

            float progress = (float) i / points.length;
            float alpha = (1.0f - progress) * 0.8f;
            float age = (now - p1.timestamp) / (float) TRAIL_DURATION_MS;
            alpha *= (1.0f - age);

            // Draw line from tip to tip
            consumer.addVertex(matrix, (float) p1.tipPos.x, (float) p1.tipPos.y, (float) p1.tipPos.z)
                .setColor(trailR, trailG, trailB, alpha);
            consumer.addVertex(matrix, (float) p2.tipPos.x, (float) p2.tipPos.y, (float) p2.tipPos.z)
                .setColor(trailR, trailG, trailB, alpha * 0.8f);
        }

        poseStack.popPose();
    }

    /**
     * Generate afterimage geometry from trail points.
     * Creates quads between tip and base positions to simulate weapon blade silhouette.
     * This creates the "ghosting" effect of the weapon swing.
     */
    private void renderRibbonGeometry(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose) {
        Objects.requireNonNull(matrix);
        Objects.requireNonNull(pose);
        TrailPoint[] points = trailPoints.toArray(new TrailPoint[0]);
        if (points.length < 2) return;

        long now = System.currentTimeMillis();

        for (int i = 0; i < points.length - 1; i++) {
            TrailPoint p1 = points[i];
            TrailPoint p2 = points[i + 1];

            // Progress along trail (0 = oldest/tail, 1 = newest/tip)
            // Reversed so newest points are brightest
            float progress1 = 1.0f - (float) i / (points.length - 1);
            float progress2 = 1.0f - (float) (i + 1) / (points.length - 1);

            // Age factor (0 = new, 1 = old/fading)
            float age1 = Math.min(1.0f, (now - p1.timestamp) / (float) TRAIL_DURATION_MS);
            float age2 = Math.min(1.0f, (now - p2.timestamp) / (float) TRAIL_DURATION_MS);

            // === AFTERIMAGE EFFECT ===
            // Use actual tip and base positions to create blade silhouette
            // This creates quads that follow the weapon's actual blade shape

            // Quad corners: tip1 -> base1 -> base2 -> tip2
            // This traces the blade's swept area during the swing
            Vec3 tip1 = p1.tipPos;
            Vec3 base1 = p1.basePos;
            Vec3 tip2 = p2.tipPos;
            Vec3 base2 = p2.basePos;

            // Build two triangles for the quad (blade silhouette)
            // Normal encodes: x=progress (for fade), y=width position (0=base, 1=tip), z=age

            // Triangle 1: base1 -> tip1 -> base2
            consumer.addVertex(matrix, (float) base1.x, (float) base1.y, (float) base1.z)
                .setColor(1f, 1f, 1f, 1f).setNormal(pose, progress1, 0f, age1);
            consumer.addVertex(matrix, (float) tip1.x, (float) tip1.y, (float) tip1.z)
                .setColor(1f, 1f, 1f, 1f).setNormal(pose, progress1, 1f, age1);
            consumer.addVertex(matrix, (float) base2.x, (float) base2.y, (float) base2.z)
                .setColor(1f, 1f, 1f, 1f).setNormal(pose, progress2, 0f, age2);

            // Triangle 2: tip1 -> tip2 -> base2
            consumer.addVertex(matrix, (float) tip1.x, (float) tip1.y, (float) tip1.z)
                .setColor(1f, 1f, 1f, 1f).setNormal(pose, progress1, 1f, age1);
            consumer.addVertex(matrix, (float) tip2.x, (float) tip2.y, (float) tip2.z)
                .setColor(1f, 1f, 1f, 1f).setNormal(pose, progress2, 1f, age2);
            consumer.addVertex(matrix, (float) base2.x, (float) base2.y, (float) base2.z)
                .setColor(1f, 1f, 1f, 1f).setNormal(pose, progress2, 0f, age2);
        }
    }

    /**
     * Check if item is a melee weapon.
     */
    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof SwordItem ||
               stack.getItem() instanceof AxeItem;
    }

    /**
     * Extract trail color from weapon based on enchantments and material.
     * Priority: 1) Enchantment glint (if enchanted), 2) Material-based color
     */
    private void updateWeaponColor(ItemStack weapon) {
        Objects.requireNonNull(weapon);
        if (!useWeaponColor || weapon.isEmpty()) {
            return;
        }

        // Only update if weapon changed
        if (ItemStack.isSameItemSameComponents(weapon, Objects.requireNonNull(lastWeaponItem))) {
            return;
        }
        lastWeaponItem = weapon.copy();

        // Check if enchanted - use enchantment glint color (purple/blue shimmer)
        boolean isEnchanted = weapon.isEnchanted();

        if (isEnchanted) {
            // Enchanted weapons get a magical purple/blue glow
            // Similar to vanilla enchantment glint color
            trailR = 0.6f;
            trailG = 0.4f;
            trailB = 1.0f;
            glowR = 0.5f;
            glowG = 0.3f;
            glowB = 0.9f;
            return;
        }

        // Material-based coloring for non-enchanted weapons
        if (weapon.getItem() instanceof TieredItem tieredItem) {
            Tier tier = tieredItem.getTier();

            // Match colors to vanilla material appearance
            if (tier == Tiers.WOOD) {
                // Warm brown
                trailR = 0.6f; trailG = 0.4f; trailB = 0.2f;
                glowR = 0.7f; glowG = 0.5f; glowB = 0.3f;
            } else if (tier == Tiers.STONE) {
                // Gray stone
                trailR = 0.5f; trailG = 0.5f; trailB = 0.5f;
                glowR = 0.6f; glowG = 0.6f; glowB = 0.6f;
            } else if (tier == Tiers.IRON) {
                // Silver/white metallic
                trailR = 0.85f; trailG = 0.85f; trailB = 0.9f;
                glowR = 0.9f; glowG = 0.9f; glowB = 1.0f;
            } else if (tier == Tiers.GOLD) {
                // Golden yellow
                trailR = 1.0f; trailG = 0.85f; trailB = 0.3f;
                glowR = 1.0f; glowG = 0.9f; glowB = 0.5f;
            } else if (tier == Tiers.DIAMOND) {
                // Cyan/aqua diamond
                trailR = 0.4f; trailG = 0.9f; trailB = 0.9f;
                glowR = 0.5f; glowG = 1.0f; glowB = 1.0f;
            } else if (tier == Tiers.NETHERITE) {
                // Dark red/purple netherite
                trailR = 0.4f; trailG = 0.2f; trailB = 0.3f;
                glowR = 0.6f; glowG = 0.3f; glowB = 0.4f;
            } else {
                // Unknown tier - default to white/silver
                trailR = 0.9f; trailG = 0.9f; trailB = 1.0f;
                glowR = 0.8f; glowG = 0.8f; glowB = 1.0f;
            }
        } else {
            // Default for non-tiered weapons
            trailR = 0.8f; trailG = 0.9f; trailB = 1.0f;
            glowR = 0.7f; glowG = 0.8f; glowB = 1.0f;
        }
    }

    /**
     * Clear the trail.
     */
    public void clearTrail() {
        trailPoints.clear();
        isSwinging = false;
    }

    // === Configuration ===

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) clearTrail();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setTrailColor(float r, float g, float b) {
        this.trailR = r;
        this.trailG = g;
        this.trailB = b;
    }

    public void setGlowColor(float r, float g, float b) {
        this.glowR = r;
        this.glowG = g;
        this.glowB = b;
    }

    public void setColorEnhance(float enhance) {
        this.colorEnhance = Math.max(1.0f, Math.min(2.0f, enhance));
    }

    public void setEmissiveStrength(float strength) {
        this.emissiveStrength = Math.max(0.0f, Math.min(2.0f, strength));
    }

    /**
     * Set opacity (0-1). Matches EpicFightSwordLight's OPACITY config.
     */
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }

    public float getOpacity() {
        return opacity;
    }

    /**
     * Set texture light level (0-255). Matches EpicFightSwordLight's TEXTURE_LIGHT config.
     * Controls forced emissive lighting - higher values make trail glow through darkness.
     */
    public void setTextureLight(int light) {
        this.textureLight = Math.max(0, Math.min(255, light));
    }

    public int getTextureLight() {
        return textureLight;
    }

    /**
     * Enable/disable dark rendering mode. Matches EpicFightSwordLight's DARK_RENDERING config.
     * When enabled, uses translucent blend instead of additive for better visibility in dark scenes.
     */
    public void setDarkRendering(boolean enabled) {
        this.darkRendering = enabled;
    }

    public boolean isDarkRendering() {
        return darkRendering;
    }

    // === Shader Helpers ===

    private void setShaderUniform(ShaderInstance shader, String name, float value) {
        Objects.requireNonNull(name);
        try {
            var uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(value);
            }
        } catch (Exception ignored) {}
    }

    private void setShaderUniformVec3(ShaderInstance shader, String name, float x, float y, float z) {
        Objects.requireNonNull(name);
        try {
            var uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(x, y, z);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Trail point data.
     */
    private record TrailPoint(Vec3 tipPos, Vec3 basePos, long timestamp) {}
}
