# DevMod Shader System

This document describes the custom shader system implemented for advanced visual effects in DevMod, specifically the Energy Shield rendering.

## Overview

DevMod uses NeoForge 1.21.1's shader registration system to create custom GPU-accelerated visual effects. The system properly integrates with Minecraft's rendering pipeline through `RegisterShadersEvent` and custom `RenderType` creation.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Shader System                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────────┐    ┌──────────────────────┐                   │
│  │  ShieldShaderRegistry │    │   ShaderManager      │                   │
│  │  (RegisterShadersEvent)│    │  (GPU Detection)     │                   │
│  └──────────┬───────────┘    └──────────┬───────────┘                   │
│             │                           │                                │
│             ▼                           ▼                                │
│  ┌──────────────────────┐    ┌──────────────────────┐                   │
│  │   Custom RenderType   │    │    QualityLevel      │                   │
│  │   (ShaderStateShard)  │    │  (AUTO/LOW/MED/HIGH) │                   │
│  └──────────┬───────────┘    └──────────────────────┘                   │
│             │                                                            │
│             ▼                                                            │
│  ┌──────────────────────────────────────────────────┐                   │
│  │              EnergyShieldRenderer                 │                   │
│  │  • Uses custom RenderType when shader available   │                   │
│  │  • Falls back to vanilla shader if needed         │                   │
│  │  • Sets shader uniforms (color, impact, time)     │                   │
│  └──────────────────────────────────────────────────┘                   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## File Structure

```
src/main/
├── java/com/frenkvs/devmod/rendering/
│   ├── shield/
│   │   ├── ShieldShaderRegistry.java    # Shader registration
│   │   └── EnergyShieldRenderer.java    # Shield rendering logic
│   └── shader/
│       ├── ShaderManager.java           # GPU detection & management
│       └── ShaderProgram.java           # OpenGL shader wrapper
│
└── resources/assets/devmod/shaders/
    └── core/
        ├── energy_shield.json           # Shader definition
        ├── energy_shield.vsh            # Vertex shader
        └── energy_shield.fsh            # Fragment shader
```

## Implementation Guide

### Step 1: Create Shader Files

#### Shader JSON Definition (`shaders/core/{name}.json`)

```json
{
    "blend": {
        "func": "add",
        "srcrgb": "srcalpha",
        "dstrgb": "one_minus_srcalpha"
    },
    "vertex": "devmod:core/energy_shield",
    "fragment": "devmod:core/energy_shield",
    "attributes": [
        "Position",
        "Color",
        "Normal"
    ],
    "samplers": [],
    "uniforms": [
        { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
        { "name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
        { "name": "GameTime", "type": "float", "count": 1, "values": [ 0.0 ] },
        { "name": "ShieldColor", "type": "float", "count": 3, "values": [ 0.24, 0.35, 1.0 ] },
        { "name": "ShieldStrength", "type": "float", "count": 1, "values": [ 0.6 ] },
        { "name": "ImpactTime", "type": "float", "count": 1, "values": [ 999.0 ] },
        { "name": "ImpactPoint", "type": "float", "count": 3, "values": [ 0.0, 0.0, 0.0 ] }
    ]
}
```

**Key Points:**
- `vertex` and `fragment`: Resource paths to shader files (without `.vsh`/`.fsh` extension)
- `attributes`: Must match the `VertexFormat` used in `RenderType.create()`
- `uniforms`: Default values for shader uniforms

#### Vertex Shader (`shaders/core/{name}.vsh`)

```glsl
#version 150

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec4 vertexColor;
out vec3 vertexNormal;
out vec3 localPosition;
out float fresnel;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    vertexColor = Color;
    localPosition = Position;

    // Fresnel edge glow calculation
    vec3 viewDir = normalize(-viewPos.xyz);
    vec3 transformedNormal = normalize(mat3(ModelViewMat) * Normal);
    fresnel = pow(1.0 - max(dot(viewDir, transformedNormal), 0.0), 3.0);
}
```

#### Fragment Shader (`shaders/core/{name}.fsh`)

```glsl
#version 150

in vec4 vertexColor;
in vec3 localPosition;
in float fresnel;

uniform float GameTime;
uniform vec3 ShieldColor;
uniform float ShieldStrength;
uniform float ImpactTime;
uniform vec3 ImpactPoint;

out vec4 fragColor;

void main() {
    // Energy field noise, fresnel glow, impact waves...
    vec3 finalColor = ShieldColor * (0.5 + fresnel * 0.5);
    float alpha = ShieldStrength * (0.3 + fresnel * 0.5);

    fragColor = vec4(finalColor, alpha);
}
```

### Step 2: Register Shader via Event

```java
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ShieldShaderRegistry {

    @Nullable
    private static ShaderInstance energyShieldShader;
    private static RenderType energyShieldRenderType;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            // IMPORTANT: Only specify the shader name, NOT "core/{name}"
            // ShaderInstance automatically looks in shaders/core/
            ResourceLocation shaderLocation = ResourceLocation.fromNamespaceAndPath(
                DevMod.MODID, "energy_shield"  // NOT "core/energy_shield"
            );

            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    shaderLocation,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL
                ),
                shader -> {
                    energyShieldShader = shader;
                    initializeRenderType();
                }
            );
        } catch (IOException e) {
            // Fall back to vanilla shader
            initializeFallbackRenderType();
        }
    }
}
```

### Step 3: Create Custom RenderType

```java
private static void initializeRenderType() {
    RenderStateShard.ShaderStateShard shaderState = new RenderStateShard.ShaderStateShard(
        () -> energyShieldShader
    );

    energyShieldRenderType = RenderType.create(
        "devmod_energy_shield",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.TRIANGLES,
        1536,   // Buffer size
        false,  // affects crumbling
        true,   // sort on upload (for transparency)
        RenderType.CompositeState.builder()
            .setShaderState(shaderState)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false)
    );
}
```

### Step 4: Set Shader Uniforms at Render Time

```java
public void render(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
    if (ShieldShaderRegistry.isUsingCustomShader()) {
        ShaderInstance shader = ShieldShaderRegistry.getShader();

        // Set custom uniforms
        shader.safeGetUniform("GameTime").set(RenderSystem.getShaderGameTime());
        shader.safeGetUniform("ShieldColor").set(r, g, b);
        shader.safeGetUniform("ShieldStrength").set(shieldStrength);
        shader.safeGetUniform("ImpactTime").set(impactTime);
        shader.safeGetUniform("ImpactPoint").set(impactX, impactY, impactZ);

        // Get buffer and render
        VertexConsumer consumer = bufferSource.getBuffer(ShieldShaderRegistry.getShieldRenderType());
        renderGeometry(consumer, poseStack);
    }
}
```

## Common Pitfalls

### 1. Wrong Shader Path

**Problem:** `FileNotFoundException: devmod:shaders/core/core/energy_shield.json`

**Cause:** Specifying `"core/energy_shield"` as the ResourceLocation path.

**Solution:** Only specify the shader name: `"energy_shield"`. Minecraft automatically prepends `shaders/core/`.

### 2. Vertex Format Mismatch

**Problem:** Shader compiles but geometry doesn't render.

**Cause:** `VertexFormat` in `RenderType.create()` doesn't match shader attributes.

**Solution:** Ensure exact match:
- `POSITION_COLOR_NORMAL` requires `Position`, `Color`, `Normal` attributes
- `POSITION_COLOR` requires `Position`, `Color` attributes

### 3. macOS Shader Support Detection

**Problem:** Shaders fall back to CPU on macOS Apple Silicon despite having GL 4.1.

**Cause:** macOS Core Profile doesn't expose `GL_ARB_vertex_shader` extension separately.

**Solution:** Check for core profile support:
```java
boolean hasShaderExtensions = caps.GL_ARB_vertex_shader && caps.GL_ARB_fragment_shader;
boolean isCoreProfile = glVersion >= 200; // GL 2.0+ has shaders in core
shadersSupported = glVersion >= MIN_GL_VERSION && (hasShaderExtensions || isCoreProfile);
```

### 4. Missing Uniform Values

**Problem:** `NullPointerException` when setting uniforms.

**Cause:** Uniform not defined in JSON or shader doesn't use it.

**Solution:** Use `safeGetUniform()` which returns a no-op uniform if not found.

## GPU Detection & Quality Levels

The `ShaderManager` class automatically detects GPU capabilities:

```java
public enum QualityLevel {
    LOW(100, 0.5f),    // Integrated GPUs
    MEDIUM(300, 1.0f), // Default
    HIGH(500, 1.5f);   // Dedicated GPUs (RTX, RX 6xxx, etc.)

    public final int maxParticles;
    public final float intensityMultiplier;
}
```

Detection is based on `GL_RENDERER` string:
- **HIGH**: RTX, RX 6xxx/7xxx, GTX 10xx/16xx/20xx/30xx/40xx
- **LOW**: Intel HD/UHD/Iris, LLVMpipe, Mesa, Software
- **MEDIUM**: Everything else

## Energy Shield Effects

The energy shield fragment shader implements several visual effects:

### 1. Simplex Noise Energy Field
Animated 3D noise creates a "force field" look:
```glsl
float noise = snoise(localPosition * 3.0 + vec3(time * 0.1, time * 0.05, time * 0.08));
```

### 2. Fresnel Edge Glow
Edges appear brighter using view-angle-dependent shading:
```glsl
float fresnel = pow(1.0 - max(dot(viewDir, normal), 0.0), 3.0);
```

### 3. Impact Wave Ripple
When shield blocks damage, an expanding wave radiates from the impact point:
```glsl
float angularDist = acos(clamp(dot(normLocal, normImpact), -1.0, 1.0));
float waveRadius = ImpactTime * waveSpeed;
float waveIntensity = smoothstep(waveWidth, 0.0, abs(angularDist - waveRadius));
```

### 4. Micro-Pulse Animation
Subtle brightness oscillation adds energy feel:
```glsl
float microPulse = sin(time * 2.0) * 0.1 + 0.9;
```

## Testing

### Verify Shader Loading
Check logs for:
```
[Shield] Registering energy shield shader...
[Shield] Energy shield shader registered successfully!
[Shield] Energy shield RenderType created successfully
```

### Fallback Detection
If shader fails, logs show:
```
[Shield] Failed to register energy shield shader!
[Shield] Energy shield fallback RenderType created
```

### In-Game Verification
1. Equip a shield and hold right-click to block
2. Shield sphere should appear around player
3. When taking damage while blocking, impact ripple should expand from hit direction

## Related Files

| File | Purpose |
|------|---------|
| [ShieldShaderRegistry.java](../src/main/java/com/frenkvs/devmod/rendering/shield/ShieldShaderRegistry.java) | Shader registration |
| [EnergyShieldRenderer.java](../src/main/java/com/frenkvs/devmod/rendering/shield/EnergyShieldRenderer.java) | Shield rendering |
| [ShaderManager.java](../src/main/java/com/frenkvs/devmod/rendering/shader/ShaderManager.java) | GPU detection |
| [energy_shield.json](../src/main/resources/assets/devmod/shaders/core/energy_shield.json) | Shader definition |
| [energy_shield.vsh](../src/main/resources/assets/devmod/shaders/core/energy_shield.vsh) | Vertex shader |
| [energy_shield.fsh](../src/main/resources/assets/devmod/shaders/core/energy_shield.fsh) | Fragment shader |
