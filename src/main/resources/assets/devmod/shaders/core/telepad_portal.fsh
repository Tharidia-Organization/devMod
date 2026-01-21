#version 150

in vec4 vertexColor;
in vec2 uv;
in vec3 viewPos;
in vec3 viewNormal;
in float layerId;

uniform float GameTime;
uniform float Charge;
uniform float Intensity;
uniform vec2 Aspect;
uniform float PulseSpeed;
uniform vec3 ColorPrimary;
uniform vec3 ColorSecondary;
uniform vec3 ColorAccent;
uniform vec3 ColorSpark;
uniform sampler2D Sampler0;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    float a = hash(i + vec2(0.0, 0.0));
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amp * noise(p);
        p *= 2.0;
        amp *= 0.5;
    }
    return value;
}

void main() {
    float charge = clamp(Charge, 0.0, 1.0);
    float time = GameTime * max(PulseSpeed, 0.01);

    vec2 p = uv * 2.0 - 1.0;
    vec2 portalP = p;
    if (layerId >= -0.35) {
        portalP.y = portalP.y * 0.94 - 0.04;
    }
    float aspectX = max(Aspect.x, 0.2);
    vec2 ep = vec2(portalP.x / aspectX, portalP.y);
    if (layerId < -0.35) {
        ep = p;
    }

    float r = length(ep);
    float outerFade = 1.0 - smoothstep(1.0, 1.7, r);
    if (outerFade <= 0.001) {
        discard;
    }

    float rot = time * 0.55;
    float cosr = cos(rot);
    float sinr = sin(rot);
    vec2 epRot = vec2(ep.x * cosr - ep.y * sinr, ep.x * sinr + ep.y * cosr);
    float angle = atan(epRot.y, epRot.x);

    float n = fbm(ep * 2.5 + vec2(time * 0.25, -time * 0.18));
    float swirlA = sin(angle * 6.0 + time * 2.0 + r * 10.0);
    float swirlB = sin(angle * -4.0 + time * 1.4 - r * 6.0);
    float plasma = mix(swirlA, swirlB, 0.5) * 0.6 + n * 0.8;
    float plasmaFlow = plasma * 0.5 + 0.5;

    float coreMask = smoothstep(1.0, 0.0, r);
    float ringMask = smoothstep(1.04, 0.90, r) * (1.0 - smoothstep(0.80, 0.66, r));
    float rimMask = smoothstep(1.08, 0.93, r);

    vec2 runeUV = clamp(epRot * 0.5 + 0.5, 0.0, 1.0);
    float runeMask = texture(Sampler0, runeUV).r;
    runeMask = smoothstep(0.2, 0.75, runeMask);

    vec3 viewDir = normalize(-viewPos);
    float fresnel = pow(1.0 - max(dot(viewDir, normalize(viewNormal)), 0.0), 2.2);

    vec3 color = vec3(0.0);
    float alpha = 0.0;

    if (layerId < -0.35) {
        float ringGlow = ringMask * (0.55 + 0.45 * plasmaFlow);
        float runePulse = 0.55 + 0.45 * sin(time * 2.4 - angle * 4.0 + r * 2.0);
        float runeGlow = runeMask * ringMask * runePulse;
        float sweep = smoothstep(0.5, 1.0, sin(angle * 8.0 + time * 2.0));
        float basePulse = 0.65 + 0.35 * sin(time * 1.8 - r * 4.0);
        color = ColorPrimary * ringGlow + ColorAccent * (0.65 * runeGlow + 0.25 * sweep);
        color += ColorSecondary * coreMask * 0.18;
        alpha = (0.22 + 0.6 * charge) * (ringGlow * 0.8 + runeGlow) * basePulse;
    } else if (layerId < 0.2) {
        float pulse = 0.7 + 0.3 * sin(time * 3.0 + r * 7.0);
        float flow = plasmaFlow;
        color = mix(ColorSecondary, ColorPrimary, coreMask);
        color += ColorAccent * flow * 0.3;
        alpha = (0.3 + 0.7 * charge) * coreMask * (0.6 + 0.4 * flow) * pulse;
    } else if (layerId < 0.6) {
        float tick = step(0.2, sin(angle * 24.0 + time * 5.0));
        float ringGlow = ringMask * (0.55 + 0.45 * plasmaFlow);
        float runePulse = 0.6 + 0.4 * sin(time * 2.6 + angle * 3.4);
        float runeGlow = runeMask * ringMask * runePulse;
        float wave = smoothstep(0.0, 1.0, sin(time * 4.0 - r * 12.0));
        color = ColorAccent * (0.6 + 0.4 * tick) + ColorPrimary * ringGlow;
        color += ColorAccent * rimMask * fresnel * 0.7;
        color += ColorAccent * runeGlow * (0.5 + 0.5 * charge);
        color += ColorAccent * ringMask * wave * 0.25;
        alpha = (0.32 + 0.85 * charge) * (ringGlow + tick * ringMask * 0.7 + runeGlow + wave * 0.25);
    } else if (layerId < 0.85) {
        float sparkNoise = fbm(vec2(angle * 3.0, r * 8.0) + time * 1.6);
        float sparks = step(0.75 + 0.18 * sin(time * 4.0 + angle * 2.0), sparkNoise) * ringMask;
        float streak = smoothstep(0.2, 1.0, sin(angle * 12.0 + time * 6.0)) * ringMask;
        color = ColorSpark * (0.7 + 0.3 * plasmaFlow) + ColorAccent * streak * 0.35;
        alpha = (sparks * 0.25 + streak * 0.15) * (0.2 + 0.8 * charge);
    } else if (layerId < 0.92) {
        float bandPhase = fract(layerId * 12.0);
        float bandMask = smoothstep(0.90, 1.08, r) * (1.0 - smoothstep(1.28, 1.48, r));
        float arc = smoothstep(0.2, 0.95, sin(angle * 10.0 + time * 3.4 + bandPhase * 6.283));
        float pulse = smoothstep(0.0, 1.0, sin(time * 4.1 - r * 11.0 + bandPhase * 6.283));
        float orbitNoise = fbm(ep * 4.0 + vec2(time * 0.7, -time * 0.5));
        float streak = bandMask * arc * (0.55 + 0.45 * orbitNoise);
        float surge = bandMask * pulse;
        color = ColorPrimary * streak + ColorAccent * (streak * 0.8 + surge * 0.6);
        color += ColorSpark * surge * 0.25;
        color += ColorAccent * fresnel * streak * 0.35;
        alpha = (0.22 + 0.7 * charge) * (streak * 0.9 + surge * 0.5);
    } else {
        float auraInner = smoothstep(0.86, 1.00, r);
        float auraOuter = 1.0 - smoothstep(1.18, 1.38, r);
        float auraBand = auraInner * auraOuter;
        float pulseWave = 0.5 + 0.5 * sin(time * 2.8 - r * 10.0);
        float pulseRing = smoothstep(0.2, 1.0, sin(time * 3.4 - r * 13.0));
        float arc = smoothstep(0.35, 0.95, sin(angle * 8.0 + time * 2.6 + r * 2.0));
        float spokes = pow(max(0.0, sin(angle * 6.0 - time * 1.8)), 2.0);
        float auraNoise = fbm(ep * 3.2 + vec2(time * 0.6, -time * 0.4));
        float auraGlow = auraBand * (0.5 + 0.5 * pulseWave) * (0.45 + 0.55 * auraNoise);
        auraGlow *= 0.7 + 0.3 * pulseRing;
        float arcGlow = auraBand * arc * (0.35 + 0.65 * spokes) * (0.4 + 0.6 * pulseWave);
        arcGlow *= 0.6 + 0.4 * pulseRing;
        color = ColorPrimary * auraGlow + ColorAccent * (auraGlow * 0.6 + arcGlow);
        color += ColorSpark * arcGlow * 0.35;
        alpha = (0.18 + 0.6 * charge) * (auraGlow * 0.7 + arcGlow);
    }

    color *= Intensity;
    color *= vertexColor.rgb;
    alpha *= vertexColor.a;
    alpha *= outerFade;

    alpha = clamp(alpha, 0.0, 1.0);
    if (alpha < 0.003) {
        discard;
    }

    fragColor = vec4(color, alpha);
}
