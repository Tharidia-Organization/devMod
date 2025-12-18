#version 150

// === Inputs from Vertex Shader ===
in vec4 vertexColor;
in vec3 vertexNormal;
in float intensity;

// === Uniforms ===
uniform float GameTime;
uniform float MaxIntensity;
uniform int HeatmapType;   // 0-5 = gradient types, 6 = LIGHT_SPAWNABLE (red), 7 = LIGHT_DARK (orange)
uniform float BaseAlpha;
uniform int UseGradient;

// === Output ===
out vec4 fragColor;

// === Heatmap gradient function ===
// Intensity 0.0 -> Blue (cold)
// Intensity 0.25 -> Cyan
// Intensity 0.5 -> Green
// Intensity 0.75 -> Yellow
// Intensity 1.0 -> Red (hot)
vec3 heatmapGradient(float t) {
    t = clamp(t, 0.0, 1.0);

    vec3 color;
    if (t < 0.25) {
        // Blue -> Cyan
        float f = t / 0.25;
        color = vec3(0.0, f, 1.0);
    } else if (t < 0.5) {
        // Cyan -> Green
        float f = (t - 0.25) / 0.25;
        color = vec3(0.0, 1.0, 1.0 - f);
    } else if (t < 0.75) {
        // Green -> Yellow
        float f = (t - 0.5) / 0.25;
        color = vec3(f, 1.0, 0.0);
    } else {
        // Yellow -> Red
        float f = (t - 0.75) / 0.25;
        color = vec3(1.0, 1.0 - f, 0.0);
    }
    return color;
}

void main() {
    float time = GameTime * 1000.0;
    vec3 color;
    float alpha;

    // Normalize intensity
    float normalizedIntensity = clamp(intensity / MaxIntensity, 0.0, 1.0);

    // === Choose color based on HeatmapType ===
    if (HeatmapType == 6) {
        // LIGHT_SPAWNABLE - Fixed red
        color = vec3(1.0, 0.0, 0.0);
        alpha = BaseAlpha + normalizedIntensity * 0.4;
    } else if (HeatmapType == 7) {
        // LIGHT_DARK - Fixed orange
        color = vec3(1.0, 0.53, 0.0);
        alpha = BaseAlpha + normalizedIntensity * 0.4;
    } else if (UseGradient == 1) {
        // Gradient heatmap (DEATH, MOVEMENT, CAMPING, etc.)
        color = heatmapGradient(normalizedIntensity);
        alpha = BaseAlpha + normalizedIntensity * 0.4;
    } else {
        // Use vertex color directly
        color = vertexColor.rgb;
        alpha = vertexColor.a;
    }

    // === Add subtle shimmer effect ===
    float shimmer = sin(time * 0.003 + gl_FragCoord.x * 0.1 + gl_FragCoord.y * 0.1) * 0.05 + 1.0;
    color *= shimmer;

    // === Edge darkening for depth ===
    float edgeFactor = abs(dot(vertexNormal, vec3(0.0, 1.0, 0.0)));
    color *= 0.8 + edgeFactor * 0.2;

    // Clamp alpha
    alpha = clamp(alpha, 0.0, 0.85);

    fragColor = vec4(color, alpha);
}
