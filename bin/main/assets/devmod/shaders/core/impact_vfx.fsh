#version 150

// === Inputs from Vertex Shader ===
in vec4 vertexColor;
in vec3 vertexNormal;
in vec3 localPosition;
in float effectParam;

// === Uniforms ===
uniform float GameTime;
uniform int EffectType;         // 0=vortex, 1=slash, 2=lines
uniform float SlashProgress;    // Slash animation 0.0-1.0
uniform vec3 ColorPrimary;      // Electric blue
uniform vec3 ColorSecondary;    // Cyan
uniform vec3 ColorGlow;         // Light blue glow
uniform float Alpha;            // Global opacity
uniform float Intensity;        // Config intensity multiplier

// === Output ===
out vec4 fragColor;

// === Simple noise function ===
float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    return mix(
        mix(mix(hash(i + vec3(0, 0, 0)), hash(i + vec3(1, 0, 0)), f.x),
            mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
            mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    float time = GameTime * 1000.0;
    vec3 color = ColorPrimary;
    float alpha = Alpha;

    // === EFFECT TYPE 0: Energy Vortex ===
    if (EffectType == 0) {
        // Gradient from primary (center) to secondary (outer)
        float distFromCenter = length(localPosition.xz);
        float gradientT = clamp(distFromCenter / 1.5, 0.0, 1.0);
        color = mix(ColorPrimary, ColorSecondary, gradientT);

        // Add energy shimmer
        float shimmer = noise(localPosition * 5.0 + vec3(time * 0.2)) * 0.3;
        color += ColorGlow * shimmer;

        // Spiral intensity based on height (effectParam = spiralT)
        float spiralGlow = sin(effectParam * 6.28318 + time * 3.0) * 0.2 + 0.8;
        color *= spiralGlow;

        // Edge glow
        float edgeFade = 1.0 - smoothstep(0.0, 0.1, abs(localPosition.y - 0.5));
        alpha *= 0.6 + edgeFade * 0.4;
    }
    // === EFFECT TYPE 1: Slash Trail ===
    else if (EffectType == 1) {
        // Position along the slash (from vertex normal.x)
        float slashT = effectParam;

        // Trailing gradient: bright at blade tip, fade behind
        float trailFade = smoothstep(SlashProgress - 0.3, SlashProgress, slashT);

        // Color gradient: cyan at tip -> primary behind
        color = mix(ColorPrimary, ColorSecondary, trailFade);

        // Add spark effect near the blade
        float sparkDist = abs(slashT - SlashProgress);
        if (sparkDist < 0.1) {
            float spark = pow(1.0 - sparkDist / 0.1, 3.0);
            color += ColorGlow * spark * 2.0;
        }

        // Alpha fade behind the blade
        alpha *= trailFade * 0.8 + 0.2;

        // Energy crackling
        float crackle = noise(localPosition * 10.0 + vec3(time * 0.5)) * 0.2;
        color += vec3(crackle);
    }
    // === EFFECT TYPE 2: Connection Lines ===
    else if (EffectType == 2) {
        // Pulsing glow along the line
        float pulseT = sin(time * 4.0 + localPosition.y * 10.0) * 0.5 + 0.5;
        color = mix(ColorPrimary, ColorGlow, pulseT);

        // Distance-based fade (fade toward ends)
        float lineFade = 1.0 - abs(effectParam - 0.5) * 2.0;
        alpha *= lineFade * 0.7 + 0.3;

        // Electric flicker
        float flicker = step(0.95, noise(vec3(time * 20.0, effectParam * 5.0, 0.0)));
        color += ColorSecondary * flicker * 0.5;
    }

    // === Apply global intensity ===
    color *= Intensity;

    // === Apply vertex color (allows CPU-side color override) ===
    color *= vertexColor.rgb;
    alpha *= vertexColor.a;

    // Clamp alpha
    alpha = clamp(alpha, 0.0, 1.0);

    fragColor = vec4(color, alpha);
}
