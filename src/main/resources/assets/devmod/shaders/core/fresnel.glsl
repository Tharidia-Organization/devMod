// Fresnel Effect Functions - GLSL implementation
// Used for edge glow effects on shields and VFX

/**
 * Basic Fresnel effect
 * Calculates edge brightness based on viewing angle
 *
 * @param viewDir View direction (camera to surface, normalized)
 * @param normal Surface normal (normalized)
 * @param power Fresnel power (higher = sharper falloff, typical: 2-5)
 * @return Fresnel factor (0 at center, 1 at edges)
 */
float fresnel(vec3 viewDir, vec3 normal, float power) {
    float dotProduct = max(dot(viewDir, normal), 0.0);
    return pow(1.0 - dotProduct, power);
}

/**
 * Schlick approximation of Fresnel
 * More physically accurate, commonly used in PBR
 *
 * @param viewDir View direction (normalized)
 * @param normal Surface normal (normalized)
 * @param f0 Fresnel reflectance at normal incidence (typically 0.04 for dielectrics)
 * @return Fresnel reflectance
 */
float fresnelSchlick(vec3 viewDir, vec3 normal, float f0) {
    float cosTheta = max(dot(viewDir, normal), 0.0);
    return f0 + (1.0 - f0) * pow(1.0 - cosTheta, 5.0);
}

/**
 * Fresnel with roughness for PBR
 *
 * @param viewDir View direction (normalized)
 * @param normal Surface normal (normalized)
 * @param f0 Base reflectance
 * @param roughness Surface roughness (0 = smooth, 1 = rough)
 * @return Fresnel reflectance adjusted for roughness
 */
float fresnelSchlickRoughness(vec3 viewDir, vec3 normal, float f0, float roughness) {
    float cosTheta = max(dot(viewDir, normal), 0.0);
    return f0 + (max(1.0 - roughness, f0) - f0) * pow(1.0 - cosTheta, 5.0);
}

/**
 * Energy shield Fresnel (from Prismatic)
 * Optimized for energy field visual effect
 *
 * @param viewDir View direction (normalized)
 * @param normal Surface normal (normalized)
 * @return Edge glow intensity (0-1)
 */
float shieldFresnel(vec3 viewDir, vec3 normal) {
    // Power of 3 gives nice edge glow for energy shields
    return pow(1.0 - max(dot(viewDir, normal), 0.0), 3.0);
}

/**
 * Animated Fresnel with pulse
 * Adds time-based pulsing to the fresnel effect
 *
 * @param viewDir View direction (normalized)
 * @param normal Surface normal (normalized)
 * @param time Current time (for animation)
 * @param pulseSpeed Speed of the pulse
 * @param pulseAmount Amount of pulse variation (0-1)
 * @return Pulsing fresnel factor
 */
float fresnelPulse(vec3 viewDir, vec3 normal, float time, float pulseSpeed, float pulseAmount) {
    float baseFresnel = shieldFresnel(viewDir, normal);
    float pulse = sin(time * pulseSpeed) * 0.5 + 0.5; // 0-1
    return baseFresnel * (1.0 + pulse * pulseAmount);
}

/**
 * Two-tone Fresnel for shields
 * Creates inner and outer glow zones
 *
 * @param viewDir View direction (normalized)
 * @param normal Surface normal (normalized)
 * @param innerColor Color for center regions
 * @param outerColor Color for edge regions
 * @return Blended color based on fresnel
 */
vec3 fresnelColor(vec3 viewDir, vec3 normal, vec3 innerColor, vec3 outerColor) {
    float f = shieldFresnel(viewDir, normal);
    return mix(innerColor, outerColor, f);
}

/**
 * Rim lighting effect
 * Similar to fresnel but with adjustable threshold
 *
 * @param viewDir View direction (normalized)
 * @param normal Surface normal (normalized)
 * @param threshold Edge threshold (0-1)
 * @param smoothness Transition smoothness
 * @return Rim light intensity
 */
float rimLight(vec3 viewDir, vec3 normal, float threshold, float smoothness) {
    float rim = 1.0 - max(dot(viewDir, normal), 0.0);
    return smoothstep(threshold - smoothness, threshold + smoothness, rim);
}
