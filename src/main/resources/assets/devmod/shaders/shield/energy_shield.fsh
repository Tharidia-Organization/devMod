#version 150

// === Inputs from Vertex Shader ===
in vec4 vertexColor;
in vec3 vertexNormal;
in vec3 viewPosition;
in vec3 localPosition;  // Local/model space position for impact
in vec2 texCoord;
in float fresnel;

// === Uniforms ===
uniform float GameTime;
uniform vec3 ShieldColor;      // Base shield color (RGB, 0-1)
uniform float ShieldStrength;  // Shield opacity/intensity (0-1)
uniform float ImpactTime;      // Time since last impact (seconds)
uniform vec3 ImpactPoint;      // Local space impact point (normalized to sphere surface)
uniform float NoiseIntensity;  // Energy field noise strength (0-0.5)
uniform float PulseSpeed;      // Animation speed multiplier

// === Output ===
out vec4 fragColor;

// === Simplex 3D Noise (inlined for compatibility) ===
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

float snoise(vec3 v) {
    const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
    const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

    vec3 i = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);

    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);

    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy;
    vec3 x3 = x0 - D.yyy;

    i = mod289(i);
    vec4 p = permute(permute(permute(
        i.z + vec4(0.0, i1.z, i2.z, 1.0))
        + i.y + vec4(0.0, i1.y, i2.y, 1.0))
        + i.x + vec4(0.0, i1.x, i2.x, 1.0));

    float n_ = 0.142857142857;
    vec3 ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);
    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);

    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);

    vec4 s0 = floor(b0) * 2.0 + 1.0;
    vec4 s1 = floor(b1) * 2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);

    vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x; p1 *= norm.y; p2 *= norm.z; p3 *= norm.w;

    vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m * m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
}

void main() {
    // === 1. Animated Energy Field Noise ===
    float time = GameTime * PulseSpeed * 1000.0; // Convert to useful range
    vec3 noiseCoord = localPosition * 3.0 + vec3(time * 0.1, time * 0.05, time * 0.08);
    float noise = snoise(noiseCoord) * 0.5 + 0.5; // Map to 0-1

    // Second layer of noise for more detail
    vec3 noiseCoord2 = localPosition * 6.0 + vec3(time * 0.15, -time * 0.1, time * 0.12);
    float noise2 = snoise(noiseCoord2) * 0.5 + 0.5;

    // Combine noise layers
    float combinedNoise = noise * 0.7 + noise2 * 0.3;

    // === 2. Fresnel Edge Glow ===
    // Fresnel is pre-calculated in vertex shader
    float edgeGlow = fresnel;

    // === 3. Pulsing Animation ===
    float pulse = sin(time * 0.5) * 0.5 + 0.5; // Slow pulse 0-1
    float microPulse = sin(time * 2.0) * 0.1 + 0.9; // Fast subtle pulse

    // === 4. Impact Wave Effect ===
    // Calculate spherical distance (arc length on unit sphere)
    float impactGlow = 0.0;
    float rippleEffect = 0.0;

    if (ImpactTime < 1.0 && length(ImpactPoint) > 0.001) {
        // Use angular distance on sphere surface for proper ripple
        vec3 normLocal = normalize(localPosition);
        vec3 normImpact = normalize(ImpactPoint);
        float angularDist = acos(clamp(dot(normLocal, normImpact), -1.0, 1.0));

        // Expanding wave front
        float waveSpeed = 4.0;  // How fast the wave expands
        float waveRadius = ImpactTime * waveSpeed;  // Current wave position
        float waveWidth = 0.3;  // Width of the wave band

        // Distance from the wave front
        float distFromWave = abs(angularDist - waveRadius);

        // Sharp wave band that fades over time
        float waveFade = max(0.0, 1.0 - ImpactTime * 1.5);  // Fade out over ~0.67s
        float waveIntensity = smoothstep(waveWidth, 0.0, distFromWave) * waveFade;

        // Inner ripples following the main wave
        float innerRipples = sin(angularDist * 20.0 - time * 8.0) * 0.5 + 0.5;
        float rippleMask = smoothstep(waveRadius + 0.1, waveRadius - 0.5, angularDist);
        rippleEffect = innerRipples * rippleMask * waveFade * 0.4;

        // Central flash at impact point (fades quickly)
        float centralFlash = exp(-angularDist * 8.0) * max(0.0, 1.0 - ImpactTime * 4.0);

        // Combine wave effects
        impactGlow = waveIntensity * 1.5 + centralFlash + rippleEffect;
    }

    // === 5. Combine All Effects ===
    // Base color with noise variation
    vec3 baseColor = ShieldColor;
    vec3 brightColor = ShieldColor * 1.5 + vec3(0.2); // Brighter for edges
    vec3 impactColor = vec3(1.0, 1.0, 1.0);  // White flash for impacts

    // Mix based on fresnel and noise
    vec3 finalColor = mix(baseColor, brightColor, edgeGlow);

    // Add noise-based color variation
    finalColor += combinedNoise * NoiseIntensity * ShieldColor;

    // Add pulsing
    finalColor *= microPulse;

    // Add impact wave (bright white/cyan flash)
    vec3 waveColor = mix(vec3(1.0), ShieldColor * 2.0, 0.3);  // Slightly tinted wave
    finalColor += impactGlow * waveColor;

    // === 6. Alpha Calculation ===
    // Base alpha from shield strength
    float alpha = ShieldStrength * 0.3;

    // Add fresnel contribution (edges more visible)
    alpha += edgeGlow * ShieldStrength * 0.5;

    // Add noise contribution
    alpha += combinedNoise * NoiseIntensity * 0.2;

    // Add impact wave to alpha (makes wave more visible)
    alpha += impactGlow * 0.6;

    // Clamp to reasonable range
    alpha = clamp(alpha, 0.0, 0.95);

    // Apply vertex color alpha if present
    alpha *= vertexColor.a;

    fragColor = vec4(finalColor, alpha);
}
