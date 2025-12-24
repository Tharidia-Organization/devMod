# DevMod Rendering System - Complete Audit

> **Last Updated**: 2024-12-23
> **Status**: ✅ CURRENT - Audit architettura rendering

This document provides a comprehensive audit of all rendering systems in DevMod, analyzing current implementation approaches and identifying candidates for GPU shader conversion.

## Executive Summary

| Category | Count | Current Approach |
|----------|-------|------------------|
| **GPU Shader** | 1 | Custom GLSL (EnergyShieldRenderer) |
| **Cached Geometry** | 2 | Pre-computed meshes (SphereRenderer, HexagonalShieldMesh) |
| **Vanilla RenderType** | 12 | Immediate-mode CPU rendering |
| **2D GUI** | 15+ | GuiGraphics screen-space rendering |

---

## Part 1: 3D World Rendering Systems

### 1.1 GPU Shader-Based (Current Best Practice)

#### EnergyShieldRenderer
- **Location:** `rendering/shield/EnergyShieldRenderer.java`
- **Approach:** Custom GLSL shader via `RegisterShadersEvent`
- **Effects:**
  - Simplex noise energy field animation
  - Fresnel edge glow
  - Impact wave ripple (angular distance on sphere)
  - Micro-pulse animation
- **Performance:** Excellent - all calculations on GPU
- **Status:** PRODUCTION READY

**Associated Files:**
| File | Purpose |
|------|---------|
| `ShieldShaderRegistry.java` | Registers shader, creates RenderType |
| `energy_shield.json` | Shader definition (uniforms, attributes) |
| `energy_shield.vsh` | Vertex shader (Fresnel calc, local position pass) |
| `energy_shield.fsh` | Fragment shader (noise, impact wave, alpha) |

---

### 1.2 Pre-Computed Cached Geometry (Optimized CPU)

#### SphereRenderer
- **Location:** `rendering/SphereRenderer.java`
- **Approach:** Cached lat/lon tessellation mesh (`CachedSphereMesh`)
- **Performance:** ~94% improvement vs per-frame trigonometry
- **Used By:** `DebugRenderer.DebugSphere`, `AggroRangeVisualizer`
- **Shader Candidate:** LOW - already highly optimized

#### HexagonalShieldMesh
- **Location:** `rendering/shield/HexagonalShieldMesh.java`
- **Approach:** Icosahedron subdivision for geodesic sphere
- **Performance:** Pre-computed float arrays for fast vertex submission
- **Shader Candidate:** ALREADY USING SHADER (via EnergyShieldRenderer)

#### TrigCache
- **Location:** `rendering/TrigCache.java`
- **Approach:** 4096-entry sin/cos lookup tables + cached spirals/rings
- **Performance:** ~70% speedup for CPU-based animated effects
- **Used By:** `ImpactVFX`, `SphereRenderer`, `PathfindingDebugger`
- **Shader Candidate:** N/A (utility class)

---

### 1.3 Vanilla RenderType - Immediate Mode (Candidates for Shader Conversion)

#### HIGH PRIORITY CANDIDATES

##### 1. ImpactVFX
- **Location:** `hud/ImpactVFX.java`
- **Lines of Code:** 651
- **Current Approach:** CPU-calculated spirals, slashes, rings using TrigCache
- **Effects:**
  - Energy Vortex (double rotating spirals + concentric rings + rays)
  - Slash Trail (animated blade arc with sparks)
  - Connection Lines (camera-oriented with pulse)
- **Problem:** ~200 vertices/frame, heavy sin/cos despite TrigCache
- **Shader Benefits:**
  - All animation on GPU (time-based)
  - Glow/bloom effects possible
  - Particle system for sparks
- **Complexity:** HIGH
- **Priority:** HIGH - Highest visual impact

##### 2. HeatmapVisualizer
- **Location:** `rendering/HeatmapVisualizer.java`
- **Lines of Code:** 410
- **Current Approach:** Per-block quad rendering with gradient color
- **Effects:**
  - Blue-cyan-green-yellow-red gradient based on intensity
  - Height variation based on count
  - Distance culling
- **Problem:** Up to 10,000 quads (MAX_POINTS_PER_HEATMAP)
- **Shader Benefits:**
  - Instanced rendering for massive performance gain
  - GPU gradient calculation
  - Animated pulsing per cell
- **Complexity:** MEDIUM
- **Priority:** HIGH - Performance critical with large datasets

##### 3. PathfindingDebugger
- **Location:** `rendering/PathfindingDebugger.java`
- **Lines of Code:** 646
- **Current Approach:** CPU-animated beacons, rotating rings, gradient paths
- **Effects:**
  - START beacon (rotating cyan rings, vertical beam)
  - DESTINATION beacon (gold diamond frame, crosshair)
  - Animated path (marching ants, gradient, direction arrows)
- **Problem:** Heavy per-frame Math.sin/cos, multiple render passes
- **Shader Benefits:**
  - GPU-based rotation/pulse animation
  - Marching ants as fragment shader pattern
  - Gradient in shader
- **Complexity:** HIGH
- **Priority:** MEDIUM - Spectacular but debug-only feature

##### 4. LineOfSightVisualizer
- **Location:** `rendering/LineOfSightVisualizer.java`
- **Lines of Code:** 338
- **Current Approach:** CPU raycast results + cone visualization
- **Effects:**
  - View cone (8-segment frustum)
  - LoS rays (green=visible, red=blocked, yellow=out of FOV)
  - Blocking obstacle wireframe highlight
- **Problem:** Redundant geometry generation
- **Shader Benefits:**
  - Instanced lines with gradient
  - Cone as single mesh with shader-based opacity
- **Complexity:** LOW
- **Priority:** MEDIUM

#### MEDIUM PRIORITY CANDIDATES

##### 5. AggroRangeVisualizer
- **Location:** `rendering/AggroRangeVisualizer.java`
- **Lines of Code:** 238
- **Current Approach:** SphereRenderer + debugLineStrip for meridians/circles
- **Effects:**
  - Follow range sphere (translucent)
  - Attack range sphere
  - Targeting line to current target
- **Problem:** Already using cached spheres, but no animation
- **Shader Benefits:**
  - Animated pulsing sphere
  - Energy field effect like EnergyShield
- **Complexity:** LOW (reuse energy_shield shader pattern)
- **Priority:** MEDIUM

##### 6. LightLevelOverlay
- **Location:** `rendering/LightLevelOverlay.java`
- **Lines of Code:** 252
- **Current Approach:** Cached light data + per-block quads
- **Effects:**
  - Green/yellow/red blocks based on light level
  - Billboard text numbers
- **Problem:** Many draw calls for large areas
- **Shader Benefits:**
  - Instanced quad rendering
  - Animated glow on dangerous spots
- **Complexity:** MEDIUM
- **Priority:** LOW - Already has caching optimization

##### 7. BodyPartRenderer
- **Location:** `rendering/BodyPartRenderer.java`
- **Lines of Code:** 228
- **Current Approach:** Wireframe + solid quads, color-coded
- **Effects:**
  - HEAD (cyan), ARMS (yellow), BODY (green), LEGS (red)
  - Semi-transparent faces + opaque edges
  - Hit highlight pulsing
- **Shader Benefits:**
  - X-ray/outline shader effect
  - Animated pulse on hit
- **Complexity:** MEDIUM
- **Priority:** LOW

#### LOW PRIORITY CANDIDATES

##### 8. DebugRenderer
- **Location:** `rendering/DebugRenderer.java`
- **Lines of Code:** 606
- **Purpose:** Generic debug shape system (boxes, lines, spheres, circles, arrows)
- **Shader Benefits:** Limited - general-purpose utility
- **Priority:** VERY LOW

##### 9. RoomBoundsVisualizer
- **Location:** `rendering/RoomBoundsVisualizer.java`
- **Purpose:** Static room boundary boxes
- **Priority:** VERY LOW

##### 10. SafeSpotVisualizer
- **Location:** `rendering/SafeSpotVisualizer.java`
- **Purpose:** Pulsing red boxes for camping spots
- **Priority:** VERY LOW

##### 11. VerticalLevelsVisualizer
- **Location:** `rendering/VerticalLevelsVisualizer.java`
- **Purpose:** Zone level boxes
- **Priority:** VERY LOW

##### 12. ChunkPerformanceVisualizer
- **Location:** `rendering/ChunkPerformanceVisualizer.java`
- **Purpose:** Chunk border performance coloring
- **Priority:** VERY LOW

---

## Part 2: 2D HUD Rendering Systems

These use `GuiGraphics` for 2D screen-space rendering. Shader conversion is **NOT RECOMMENDED** as they don't benefit from 3D shader pipelines.

### 2.1 Impact HUD System
| File | Purpose | Current Approach |
|------|---------|------------------|
| `ImpactHudOverlay.java` | 2D damage panel | GuiGraphics |
| `Impact3DRenderer.java` | 3D world panel | PoseStack + text |
| `ImpactHudContentBuilder.java` | Data formatting | N/A |
| `ImpactDpsTracker.java` | DPS calculation | N/A |

### 2.2 Screen Effects
| File | Purpose | Shader Candidate? |
|------|---------|-------------------|
| `HeadshotFlashEffect.java` | Red screen flash | NO - simple fill() |
| `BadgePopupOverlay.java` | Badge unlock popup | NO - complex 2D animation, already optimized |

### 2.3 Quest/Economy HUD
All use GuiGraphics - NOT shader candidates:
- `EnduranceQuestOverlay.java`
- `ComboDecayOverlay.java`
- `TokenGainOverlay.java`
- `EconomyOverlay.java`
- `BossPhaseOverlay.java`
- `PartyHudOverlay.java`
- `StaminaHudOverlay.java`
- `SkillEfficacyOverlay.java`

### 2.4 Status/Info HUD
- `RecordBannerOverlay.java`
- `TelemetryStatusOverlay.java`
- `OnboardingOverlay.java`
- `QuickHelpOverlay.java`
- `InstanceLoadingOverlay.java`
- `EntityDensityOverlay.java`
- `MobDebugOverlay.java`
- `EntityInfoOverlay.java`
- `SpawnabilityOverlay.java`

---

## Part 3: Shader Conversion Roadmap

### Phase 1: ImpactVFX Shader (HIGH VALUE)
**Estimated New Files:**
```
shaders/core/impact_vfx.json
shaders/core/impact_vfx.vsh
shaders/core/impact_vfx.fsh
rendering/vfx/ImpactVFXShaderRegistry.java
```

**Key Uniforms:**
- `GameTime` - Animation driver
- `VortexColor` - Primary/secondary colors
- `VortexRotation` - Current rotation angle
- `SlashProgress` - 0.0-1.0 animation progress
- `ImpactPosition` - World-space hit point

**Shader Effects:**
1. Spiral geometry with GPU rotation
2. Additive glow blending
3. Noise-based energy distortion
4. Particle system (geometry shader or instancing)

### Phase 2: Heatmap Shader (HIGH PERFORMANCE)
**Key Features:**
- Instanced quad rendering (single draw call for thousands of cells)
- GPU gradient calculation
- Height offset based on uniform buffer data

### Phase 3: Pathfinding Shader (VISUAL POLISH)
**Key Features:**
- Animated beacon rotation
- Marching ants pattern in fragment shader
- Path gradient interpolation

---

## Part 4: Technical Requirements

### Minimum GPU Support
- OpenGL 3.3+ (GLSL 330)
- macOS Core Profile support (handled by Minecraft shader pipeline; no custom detection layer)

### Shader Registration Pattern (Established)
```java
@SubscribeEvent
public static void onRegisterShaders(RegisterShadersEvent event) {
    ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(MODID, "shader_name");
    event.registerShader(
        new ShaderInstance(event.getResourceProvider(), loc, vertexFormat),
        shader -> shaderInstance = shader
    );
}
```

### Custom RenderType Pattern (Established)
```java
RenderType.create(
    "name",
    vertexFormat,
    VertexFormat.Mode.TRIANGLES,
    bufferSize,
    false, // affects crumbling
    true,  // sort on upload
    RenderType.CompositeState.builder()
        .setShaderState(new ShaderStateShard(() -> shaderInstance))
        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
        .setDepthTestState(LEQUAL_DEPTH_TEST)
        .setCullState(NO_CULL)
        .createCompositeState(false)
);
```

---

## Part 5: Conversion Priority Matrix

| Renderer | Visual Impact | Performance Gain | Complexity | Priority Score |
|----------|---------------|------------------|------------|----------------|
| **ImpactVFX** | HIGH | MEDIUM | HIGH | 9/10 |
| **HeatmapVisualizer** | MEDIUM | HIGH | MEDIUM | 8/10 |
| **PathfindingDebugger** | HIGH | LOW | HIGH | 6/10 |
| **LineOfSightVisualizer** | MEDIUM | MEDIUM | LOW | 5/10 |
| **AggroRangeVisualizer** | MEDIUM | LOW | LOW | 4/10 |
| **LightLevelOverlay** | LOW | MEDIUM | MEDIUM | 4/10 |
| **BodyPartRenderer** | LOW | LOW | MEDIUM | 3/10 |

---

## Conclusion

The EnergyShieldRenderer demonstrates the proper methodology for custom shaders in NeoForge 1.21.1. The highest-value conversion targets are:

1. **ImpactVFX** - Transforms combat feedback into a GPU-powered spectacle
2. **HeatmapVisualizer** - Enables massive datasets with instanced rendering
3. **PathfindingDebugger** - Polishes the debug experience

The 2D HUD systems should remain using GuiGraphics as they don't benefit from 3D shader pipelines.
