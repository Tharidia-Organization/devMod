# 01 - Shader Integration

## Obiettivo

Implementare gli shader GLSL per l'effetto scudo energetico con:
- Simplex 3D noise per animazione energia
- Fresnel edge glow
- Layer multipli

## File da Creare

### 1. Fragment Shader: `energy_shield.fsh`

```glsl
#version 150

// === Uniforms ===
uniform float time;           // Tempo per animazione
uniform vec3 shieldColor;     // Colore base scudo (da ArmorStats o editor)
uniform float shieldStrength; // Opacità (da ArmorStats.shieldBlockStrength)
uniform float impactTime;     // Tempo dall'ultimo impatto (per flash)
uniform vec3 impactPoint;     // Punto impatto in local space

// === Inputs da Vertex Shader ===
in vec3 fragNormal;
in vec3 fragPosition;
in vec3 viewDirection;

// === Output ===
out vec4 fragColor;

// === Simplex 3D Noise ===
// (Implementazione completa nel file)
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x*34.0)+1.0)*x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

float snoise(vec3 v) {
    // ... implementazione completa simplex noise 3D
    // ~80 linee di codice
}

void main() {
    // === 1. Fresnel Edge Glow ===
    float fresnel = pow(1.0 - max(dot(viewDirection, fragNormal), 0.0), 3.0);

    // === 2. Animated Energy Field ===
    vec3 noiseCoord = fragPosition * 2.0 + vec3(time * 0.5);
    float noise = snoise(noiseCoord) * 0.5 + 0.5;

    // === 3. Hexagonal Pattern (opzionale) ===
    // Aggiungi pattern esagonale se mesh non è già hex

    // === 4. Impact Flash ===
    float impactFade = max(0.0, 1.0 - impactTime * 2.5); // 0.4s flash
    float impactGlow = 0.0;
    if (impactFade > 0.0) {
        float dist = distance(fragPosition, impactPoint);
        impactGlow = impactFade * exp(-dist * 3.0);
    }

    // === 5. Combine Layers ===
    vec3 baseColor = shieldColor;
    vec3 edgeColor = shieldColor * 1.5 + vec3(0.3); // Brighter edges

    vec3 finalColor = mix(baseColor, edgeColor, fresnel);
    finalColor += noise * 0.15 * shieldColor;
    finalColor += impactGlow * vec3(1.0, 1.0, 1.0); // White flash

    float alpha = shieldStrength * (0.3 + fresnel * 0.5 + impactGlow * 0.5);
    alpha = clamp(alpha, 0.0, 0.9);

    fragColor = vec4(finalColor, alpha);
}
```

### 2. Vertex Shader: `energy_shield.vsh`

```glsl
#version 150

// === Uniforms ===
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
uniform mat3 normalMatrix;

// === Inputs ===
in vec3 position;
in vec3 normal;

// === Outputs to Fragment Shader ===
out vec3 fragNormal;
out vec3 fragPosition;
out vec3 viewDirection;

void main() {
    // Transform position
    vec4 viewPos = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * viewPos;

    // Pass to fragment shader
    fragPosition = position; // Local space for noise
    fragNormal = normalize(normalMatrix * normal);
    viewDirection = normalize(-viewPos.xyz);
}
```

## Integrazione in DevMod

### Posizione File

```
src/main/resources/assets/devmod/shaders/core/
├── energy_shield.fsh
├── energy_shield.vsh
└── energy_shield.json  // Shader program definition
```

### ShaderProgram Definition (`energy_shield.json`)

```json
{
    "blend": {
        "func": "add",
        "srcrgb": "srcalpha",
        "dstrgb": "one"
    },
    "vertex": "devmod:energy_shield",
    "fragment": "devmod:energy_shield",
    "attributes": [
        "Position",
        "Normal"
    ],
    "uniforms": [
        { "name": "time", "type": "float", "count": 1 },
        { "name": "shieldColor", "type": "float", "count": 3 },
        { "name": "shieldStrength", "type": "float", "count": 1 },
        { "name": "impactTime", "type": "float", "count": 1 },
        { "name": "impactPoint", "type": "float", "count": 3 },
        { "name": "modelViewMatrix", "type": "matrix4x4", "count": 16 },
        { "name": "projectionMatrix", "type": "matrix4x4", "count": 16 },
        { "name": "normalMatrix", "type": "matrix3x3", "count": 9 }
    ]
}
```

### Java Shader Loader

```java
// Nuovo file: src/main/java/com/frenkvs/devmod/client/render/EnergyShieldShader.java

package com.frenkvs.devmod.client.render;

import com.mojang.blaze3d.shaders.ShaderInstance;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public class EnergyShieldShader {
    private static ShaderInstance shieldShader;

    // Uniform locations (cached)
    private static int timeUniform;
    private static int shieldColorUniform;
    private static int shieldStrengthUniform;
    private static int impactTimeUniform;
    private static int impactPointUniform;

    public static void init(ResourceProvider provider) throws IOException {
        shieldShader = new ShaderInstance(
            provider,
            ResourceLocation.fromNamespaceAndPath("devmod", "energy_shield"),
            DefaultVertexFormat.POSITION_NORMAL
        );

        // Cache uniform locations
        timeUniform = shieldShader.getUniform("time").getLocation();
        // ... altri uniforms
    }

    public static void bind(float time, Vec3 color, float strength,
                           float impactTime, Vec3 impactPoint) {
        shieldShader.apply();
        shieldShader.getUniform("time").set(time);
        shieldShader.getUniform("shieldColor").set((float)color.x, (float)color.y, (float)color.z);
        shieldShader.getUniform("shieldStrength").set(strength);
        shieldShader.getUniform("impactTime").set(impactTime);
        shieldShader.getUniform("impactPoint").set((float)impactPoint.x, (float)impactPoint.y, (float)impactPoint.z);
    }

    public static void unbind() {
        shieldShader.clear();
    }
}
```

## Collegamento con ArmorStats

```java
// In ArmorStats.java - aggiungere nuovi campi

// Shield Visual Settings (Prismatic integration)
public int shieldColor = 0x3D5AFE;        // Electric Blue default
public float shieldOpacity = 0.6f;         // Base opacity
public boolean shieldGlowEnabled = true;   // Fresnel edge glow
public float shieldNoiseIntensity = 0.15f; // Energy field noise

// Metodo helper per passare a shader
public Vec3 getShieldColorVec3() {
    int r = (shieldColor >> 16) & 0xFF;
    int g = (shieldColor >> 8) & 0xFF;
    int b = shieldColor & 0xFF;
    return new Vec3(r / 255.0, g / 255.0, b / 255.0);
}
```

## Testing

1. Creare item test con `shieldBlockStrength > 0`
2. Equipaggiare e verificare rendering scudo
3. Colpire scudo e verificare flash impact
4. Testare con diversi valori `shieldColor` dall'editor
