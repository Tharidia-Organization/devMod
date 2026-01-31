#version 150

in vec4 vertexColor;
in vec2 texCoord;
in vec3 worldPos;
in vec3 viewPos;

uniform sampler2D Sampler0;
uniform float GameTime;
uniform float Alpha;
uniform float GlitchIntensity;
uniform float ScanLineSpeed;
uniform float ScanLineDensity;
uniform float GlowIntensity;
uniform float PulseSpeed;
uniform float FadeInProgress;
uniform float WavePhase;
uniform float WaveIntensity;
uniform float MaxY;
uniform vec3 HoloColor;
uniform vec3 GlowColor;
uniform float TexturedMode;
uniform float TextureBlend;

out vec4 fragColor;

// Pseudo-random function
float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

// Noise function for glitch
float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

void main() {
    float time = GameTime * 20.0;

    // === FADE-IN FROM BOTTOM ===
    // Calculate normalized Y position (0 at bottom, 1 at MaxY)
    float normalizedY = worldPos.y / max(MaxY, 1.0);

    // Fade in threshold - blocks above this are hidden
    float fadeThreshold = FadeInProgress * 1.2; // Slightly overshoot for smooth transition

    // Smooth fade at the edge of the threshold
    float fadeEdge = smoothstep(fadeThreshold - 0.1, fadeThreshold, normalizedY);

    // Discard pixels above the fade line (during fade-in animation)
    if (FadeInProgress < 1.0 && normalizedY > fadeThreshold) {
        discard;
    }

    // === BASE COLOR ===
    vec3 baseColor;

    // DEBUG: Check if vertex color is being received
    // vertexColor should contain block map colors (various values)
    // If vertexColor.rgb is all zeros or uniform, colors won't show
    vec3 blockColor = vertexColor.rgb;

    // If vertex colors are all zero, use a bright debug color (magenta)
    if (blockColor.r < 0.01 && blockColor.g < 0.01 && blockColor.b < 0.01) {
        blockColor = vec3(1.0, 0.0, 1.0); // Magenta = vertex colors are zero
    }

    if (TexturedMode > 0.5) {
        // Textured mode: sample from block atlas
        vec4 texSample = texture(Sampler0, texCoord);
        vec3 texColor = texSample.rgb;

        // Blend texture with holographic tint
        baseColor = mix(texColor, texColor * HoloColor, TextureBlend);

        // Apply filter overlay if flagged (vertexColor.a encodes filter state)
        if (vertexColor.a > 0.5) {
            baseColor = mix(baseColor, blockColor, 0.5);
        }
    } else {
        // Color mode: use vertex color (70% block color, 30% holo tint)
        baseColor = mix(blockColor, HoloColor, 0.3);
    }

    // === WAVE EFFECT ===
    // Horizontal wave that travels through the hologram
    float waveY = normalizedY * 10.0;
    float wave = sin(waveY - WavePhase * 5.0) * 0.5 + 0.5;
    wave = pow(wave, 4.0); // Sharpen wave
    float waveGlow = wave * WaveIntensity;

    // === SCAN LINES ===
    float scanLine = sin((worldPos.y * ScanLineDensity) - (time * ScanLineSpeed)) * 0.5 + 0.5;
    scanLine = pow(scanLine, 3.0);
    float scanEffect = 0.7 + scanLine * 0.3;

    // Secondary faster scan lines
    float scanLine2 = sin((worldPos.y * ScanLineDensity * 4.0) + (time * ScanLineSpeed * 0.5)) * 0.5 + 0.5;
    scanLine2 = pow(scanLine2, 8.0) * 0.15;

    // === GLOW / FRESNEL EFFECT ===
    vec3 viewDir = normalize(-viewPos);
    float edgeFactor = 1.0 - abs(dot(vec3(0.0, 1.0, 0.0), viewDir));
    float glow = pow(edgeFactor, 2.0) * GlowIntensity;

    // === PULSE ANIMATION ===
    float pulse = sin(time * PulseSpeed * 0.1) * 0.1 + 0.9;

    // === GLITCH EFFECT ===
    float glitch = 0.0;
    if (GlitchIntensity > 0.0) {
        float glitchTime = floor(time * 0.5);
        float glitchNoise = noise(vec2(glitchTime, worldPos.y * 0.5));
        float band = step(0.97, random(vec2(glitchTime, floor(worldPos.y * 2.0))));
        glitch = band * glitchNoise * GlitchIntensity;

        if (glitch > 0.3) {
            baseColor.r *= 1.0 + glitch * 0.5;
            baseColor.b *= 1.0 - glitch * 0.3;
        }
    }

    // === CHROMATIC ABERRATION ===
    float aberration = edgeFactor * 0.02;
    baseColor.r *= 1.0 + aberration;
    baseColor.b *= 1.0 - aberration;

    // === FADE-IN EDGE GLOW ===
    // Bright line at the fade-in edge
    float fadeEdgeGlow = 0.0;
    if (FadeInProgress < 1.0) {
        float edgeDist = abs(normalizedY - fadeThreshold);
        fadeEdgeGlow = exp(-edgeDist * 30.0) * 2.0;
    }

    // === COMBINE EFFECTS ===
    vec3 finalColor = baseColor;
    finalColor *= scanEffect;
    finalColor += scanLine2 * HoloColor;
    finalColor += glow * GlowColor;
    finalColor *= pulse;
    finalColor += glitch * GlowColor * 0.5;
    finalColor += waveGlow * GlowColor * 0.3;
    finalColor += fadeEdgeGlow * GlowColor; // Bright edge during fade-in

    // === HOLOGRAPHIC TINT ===
    finalColor = mix(finalColor, finalColor * HoloColor * 1.5, 0.15);

    // === FINAL ALPHA ===
    float finalAlpha = Alpha * vertexColor.a;
    finalAlpha *= (0.8 + scanLine * 0.2);

    // Fade alpha near the fade-in edge
    if (FadeInProgress < 1.0) {
        finalAlpha *= smoothstep(fadeThreshold, fadeThreshold - 0.05, normalizedY);
    }

    finalAlpha = clamp(finalAlpha, 0.0, 1.0);

    // DEBUG: Uncomment to see raw vertex color (should show block colors)
    // fragColor = vec4(vertexColor.rgb, 0.8);
    // return;

    fragColor = vec4(finalColor, finalAlpha);
}
