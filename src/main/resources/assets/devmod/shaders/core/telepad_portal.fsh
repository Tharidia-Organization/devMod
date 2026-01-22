#version 150

in vec4 vertexColor;
in vec2 texCoord;
in vec3 worldPos;

uniform float GameTime;
uniform float Charge;
uniform float Intensity;
uniform vec3 ColorPrimary;
uniform vec3 ColorSecondary;
uniform vec3 ColorGlow;

uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord * 2.0 - 1.0;
    float dist = length(uv);

    // Discard outside oval
    if (dist > 0.95) {
        discard;
    }

    float time = GameTime * 15.0;

    // Soft radial gradient - brighter at center
    float centerGlow = 1.0 - smoothstep(0.0, 0.8, dist);

    // Gentle pulsing energy that flows inward
    float wave = sin(dist * 12.0 - time * 1.5) * 0.5 + 0.5;
    wave *= (1.0 - dist); // Fade wave toward edges

    // Subtle rotation effect
    float angle = atan(uv.y, uv.x);
    float spiral = sin(angle * 3.0 - time * 0.5 + dist * 4.0) * 0.15 + 0.85;

    // Base energy color - blend primary and secondary based on distance
    vec3 energyColor = mix(ColorPrimary, ColorSecondary, dist * 0.7);

    // Add wave brightness
    energyColor += ColorGlow * wave * 0.4;

    // Apply spiral modulation
    energyColor *= spiral;

    // Center hotspot
    energyColor += ColorGlow * centerGlow * 0.3 * (0.8 + Charge * 0.2);

    // Soft edge fade
    float edgeFade = 1.0 - smoothstep(0.7, 0.95, dist);

    // Use texture for subtle noise variation (prevents banding)
    vec4 tex = texture(Sampler0, texCoord + time * 0.002);
    float noise = tex.r * 0.05;
    energyColor += noise * ColorPrimary;

    float alpha = edgeFade * vertexColor.a * (0.6 + centerGlow * 0.4);

    fragColor = vec4(energyColor * Intensity, alpha);
}
