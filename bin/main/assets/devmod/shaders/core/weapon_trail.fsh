#version 150

// === Inputs from Vertex Shader ===
in vec4 vertexColor;
in float trailProgress;
in float trailWidth;
in float trailAge;

// === Uniforms ===
uniform float GameTime;
uniform vec3 TrailColor;
uniform vec3 GlowColor;
uniform float Alpha;
uniform float ColorEnhance;
uniform float EmissiveStrength;
uniform float TrailLength;
uniform float TextureLight;    // Forced emissive light (0-1, normalized from 0-255)
uniform float DarkRendering;   // Dark rendering mode toggle (0 or 1)

// === Output ===
out vec4 fragColor;

// === HSV Color Enhancement (ported from ColorEnhancement.java) ===

vec3 rgbToHsv(vec3 rgb) {
    float maxC = max(max(rgb.r, rgb.g), rgb.b);
    float minC = min(min(rgb.r, rgb.g), rgb.b);
    float delta = maxC - minC;

    float h = 0.0;
    float s = (maxC > 0.0) ? (delta / maxC) : 0.0;
    float v = maxC;

    if (delta > 0.0) {
        if (maxC == rgb.r) {
            h = mod((rgb.g - rgb.b) / delta, 6.0);
        } else if (maxC == rgb.g) {
            h = (rgb.b - rgb.r) / delta + 2.0;
        } else {
            h = (rgb.r - rgb.g) / delta + 4.0;
        }
        h *= 60.0;
        if (h < 0.0) h += 360.0;
    }

    return vec3(h, s, v);
}

vec3 hsvToRgb(vec3 hsv) {
    float h = hsv.x;
    float s = hsv.y;
    float v = hsv.z;

    float c = v * s;
    float x = c * (1.0 - abs(mod(h / 60.0, 2.0) - 1.0));
    float m = v - c;

    vec3 rgb;
    if (h < 60.0) {
        rgb = vec3(c, x, 0.0);
    } else if (h < 120.0) {
        rgb = vec3(x, c, 0.0);
    } else if (h < 180.0) {
        rgb = vec3(0.0, c, x);
    } else if (h < 240.0) {
        rgb = vec3(0.0, x, c);
    } else if (h < 300.0) {
        rgb = vec3(x, 0.0, c);
    } else {
        rgb = vec3(c, 0.0, x);
    }

    return rgb + vec3(m);
}

// Adaptive color enhancement based on EpicFightSwordLight logic
vec3 enhanceColor(vec3 rgb, float enhanceFactor) {
    // Calculate standard deviation of RGB (detect gray vs saturated)
    float mean = (rgb.r + rgb.g + rgb.b) / 3.0;
    float variance = ((rgb.r - mean) * (rgb.r - mean) +
                      (rgb.g - mean) * (rgb.g - mean) +
                      (rgb.b - mean) * (rgb.b - mean)) / 3.0;
    float stdDev = sqrt(variance);

    vec3 hsv = rgbToHsv(rgb);

    // Gray colors (low stdDev < 0.06): only boost brightness
    if (stdDev < 0.06) {
        // Brightness boost only
        float valueBoost;
        if (hsv.z < 0.3) {
            valueBoost = 1.0 + enhanceFactor * 1.8; // Dark: strong boost
        } else if (hsv.z > 0.7) {
            valueBoost = 1.0 + enhanceFactor * 0.8; // Light: gentle boost
        } else {
            valueBoost = 1.0 + enhanceFactor;       // Medium
        }
        hsv.z = min(1.0, hsv.z * valueBoost);
    } else {
        // Saturated colors: boost both saturation and brightness

        // Saturation boost
        float satBoost;
        if (hsv.y < 0.3) {
            satBoost = 1.0 + enhanceFactor * 1.5;   // Low sat: strong boost
        } else if (hsv.y > 0.7) {
            satBoost = 1.0 + enhanceFactor * 0.3;   // High sat: gentle
        } else {
            satBoost = 1.0 + enhanceFactor;
        }
        hsv.y = min(1.0, hsv.y * satBoost);

        // Value boost
        float valueBoost;
        if (hsv.z < 0.3) {
            valueBoost = 1.0 + enhanceFactor * 1.8;
        } else if (hsv.z > 0.7) {
            valueBoost = 1.0 + enhanceFactor * 0.8;
        } else {
            valueBoost = 1.0 + enhanceFactor;
        }
        hsv.z = min(1.0, hsv.z * valueBoost);
    }

    return hsvToRgb(hsv);
}

void main() {
    float time = GameTime * 1000.0;

    // === Base Trail Color ===
    vec3 baseColor = TrailColor;

    // === AFTERIMAGE EFFECT ===
    // trailProgress: 0 = oldest (tail), 1 = newest (current position)
    // Newer parts of the trail are brighter
    float progressFade = pow(trailProgress, 0.5); // Smooth falloff from new to old

    // === Blade Position Gradient ===
    // trailWidth: 0 = base/handle, 1 = tip of blade
    // Tip should be brighter and more saturated (like a glowing edge)
    float bladePosition = trailWidth;
    float tipGlow = pow(bladePosition, 0.7); // Tip is brighter
    float baseGlow = 1.0 - bladePosition * 0.3; // Base slightly dimmer

    // === Age-based fade ===
    // trailAge: 0 = new, 1 = old (about to disappear)
    float ageFade = 1.0 - trailAge;
    ageFade = pow(max(0.0, ageFade), 0.8); // Smooth fade out

    // === Apply Color Enhancement ===
    float enhanceFactor = (ColorEnhance - 1.0);
    vec3 enhancedColor = enhanceColor(baseColor, enhanceFactor);

    // === Tip Highlight Effect ===
    // Make the blade tip more vibrant/white-hot
    vec3 tipHighlight = mix(enhancedColor, vec3(1.0), tipGlow * 0.3);

    // === Add Glow ===
    // Glow stronger at blade tip and for newer trail sections
    float glowIntensity = tipGlow * progressFade * EmissiveStrength;
    vec3 glowContrib = GlowColor * glowIntensity * 0.6;

    // === Shimmer Effect (subtle) ===
    float shimmer = sin(time * 0.008 + bladePosition * 10.0) * 0.05 + 1.0;

    // === Combine ===
    vec3 finalColor = tipHighlight * shimmer * baseGlow + glowContrib;

    // === Forced Emissive Light ===
    float emissiveBoost = 1.0 + TextureLight * EmissiveStrength * 0.5;
    finalColor *= emissiveBoost;

    // === Dark Rendering Mode ===
    if (DarkRendering > 0.5) {
        // Boost visibility in dark environments
        float darkBoost = 1.2 + (1.0 - progressFade) * 0.2;
        finalColor *= darkBoost;
        // Make trail more solid in dark mode
        progressFade = mix(progressFade, 1.0, 0.15);
    }

    // === Final Alpha ===
    // Combine all fade factors for smooth afterimage dissolution
    float finalAlpha = Alpha * progressFade * ageFade * vertexColor.a;

    // Edge softening: slight fade at base of blade
    finalAlpha *= mix(0.7, 1.0, bladePosition);

    // Clamp
    float maxBright = DarkRendering > 0.5 ? 1.5 : 2.0;
    finalColor = clamp(finalColor, 0.0, maxBright);
    finalAlpha = clamp(finalAlpha, 0.0, 1.0);

    fragColor = vec4(finalColor, finalAlpha);
}
