#version 150

// === Inputs from Vertex Shader ===
in vec4 vertexColor;
in vec3 vertexNormal;
in vec3 viewPosition;
in vec3 localPosition;
in vec2 texCoord;
in float fresnel;

// === Uniforms ===
uniform float GameTime;
uniform vec3 ShieldColor;
uniform float ShieldStrength;
uniform float ImpactTime;
uniform vec3 ImpactPoint;
uniform float NoiseIntensity;
uniform float PulseSpeed;

// === Output ===
out vec4 fragColor;

// === Constants ===
const float PI = 3.14159265359;

// === Simplex 3D Noise ===
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
    vec3 baseColor = vertexColor.rgb * ShieldColor;
    float time = GameTime * PulseSpeed * 2000.0;

    // === 1. Energy Field Pattern ===
    vec3 noisePos = viewPosition * 2.0;
    float noise1 = snoise(noisePos + vec3(time * 0.1, 0.0, 0.0));
    float noise2 = snoise(noisePos * 2.0 + vec3(0.0, time * 0.15, 0.0));
    float noise3 = snoise(noisePos * 4.0 + vec3(time * 0.2, time * 0.2, 0.0));
    float energyPattern = (noise1 + noise2 * 0.5 + noise3 * 0.25) * 0.5 + 0.5;

    // === 2. Fresnel Edge Glow ===
    vec3 fresnelColor = baseColor * 2.0;
    float fresnelIntensity = fresnel * 0.8;

    // === 3. Subtle Pulse ===
    float pulse = sin(time * 0.05) * 0.1 + 0.9;

    // === 4. Impact Ripple Effect ===
    // Ultra-thin artificial/technical lines expanding from impact
    float impactBrightness = 0.0;
    float impactDistortion = 0.0;

    if (ImpactTime < 1.5 && length(ImpactPoint) > 0.001) {
        vec3 normLocal = normalize(localPosition);
        vec3 normImpact = normalize(ImpactPoint);

        // Geodesic distance on sphere surface
        float dist = acos(clamp(dot(normLocal, normImpact), -1.0, 1.0));

        // === Ripple parameters ===
        float maxSpread = 1.0;
        float waveSpeed = 0.8;  // Faster - no delay feel
        float currentWavePos = ImpactTime * waveSpeed;

        // Fade over 1.5 seconds
        float globalFade = 1.0 - smoothstep(0.0, 1.5, ImpactTime);

        // === Ultra-thin sharp rings - artificial/technical look ===
        float rippleSum = 0.0;

        // Ring 1 - Leading edge (razor thin)
        float ring1Pos = currentWavePos;
        float ring1Width = 0.012;  // ULTRA THIN
        float ring1 = exp(-pow((dist - ring1Pos) / ring1Width, 2.0));
        ring1 *= smoothstep(maxSpread, 0.0, ring1Pos);
        rippleSum += ring1 * 0.7;

        // Ring 2 - Following (even thinner)
        float ring2Pos = currentWavePos * 0.6;
        float ring2Width = 0.01;
        float ring2 = exp(-pow((dist - ring2Pos) / ring2Width, 2.0));
        ring2 *= smoothstep(maxSpread * 0.6, 0.0, ring2Pos);
        rippleSum += ring2 * 0.5;

        // Ring 3 - Inner (thinnest)
        float ring3Pos = currentWavePos * 0.3;
        float ring3Width = 0.008;
        float ring3 = exp(-pow((dist - ring3Pos) / ring3Width, 2.0));
        ring3 *= smoothstep(maxSpread * 0.3, 0.0, ring3Pos);
        rippleSum += ring3 * 0.3;

        // === Central impact - tight pinpoint ===
        float centralIntensity = exp(-dist * 30.0);  // Very tight
        float centralFade = exp(-ImpactTime * 3.0);
        float centralGlow = centralIntensity * centralFade;

        // === Minimal disturbance ===
        float insideWave = smoothstep(currentWavePos + 0.02, currentWavePos - 0.08, dist);
        float disturbanceNoise = snoise(normLocal * 12.0 + vec3(ImpactTime * 3.0));
        impactDistortion = insideWave * disturbanceNoise * 0.08 * globalFade;

        // Combine
        impactBrightness = (rippleSum + centralGlow) * globalFade;
    }

    // === 5. Combine All Effects ===
    vec3 finalColor = baseColor * (0.5 + energyPattern * 0.5 * NoiseIntensity * 3.0) * pulse;
    finalColor += fresnelColor * fresnelIntensity;

    // Add energy distortion from impact
    finalColor *= (1.0 + impactDistortion * 0.5);

    // Impact ripples - bright white/cyan color
    vec3 rippleColor = mix(vec3(1.0, 1.0, 1.0), baseColor * 3.0, 0.4);
    finalColor += impactBrightness * rippleColor;

    // === 6. Alpha ===
    float baseAlpha = vertexColor.a * ShieldStrength * (0.25 + fresnel * 0.4 + energyPattern * 0.15 * NoiseIntensity);
    float impactAlpha = impactBrightness * 0.6;
    float alpha = clamp(baseAlpha + impactAlpha, 0.0, 0.9);

    fragColor = vec4(finalColor, alpha);
}
