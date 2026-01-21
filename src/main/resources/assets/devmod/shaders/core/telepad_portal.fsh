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
    vec2 aspect = max(Aspect, vec2(0.2));
    vec2 ep = p / aspect;

    float r = length(ep);
    if (r > 1.2) {
        discard;
    }

    float angle = atan(ep.y, ep.x);

    float n = fbm(ep * 2.5 + vec2(time * 0.25, -time * 0.18));
    float swirlA = sin(angle * 6.0 + time * 2.0 + r * 10.0);
    float swirlB = sin(angle * -4.0 + time * 1.4 - r * 6.0);
    float plasma = mix(swirlA, swirlB, 0.5) * 0.6 + n * 0.8;

    float coreMask = smoothstep(1.0, 0.0, r);
    float ringMask = smoothstep(1.02, 0.92, r) * (1.0 - smoothstep(0.78, 0.70, r));
    float rimMask = smoothstep(1.05, 0.95, r);

    vec3 viewDir = normalize(-viewPos);
    float fresnel = pow(1.0 - max(dot(viewDir, normalize(viewNormal)), 0.0), 2.2);

    vec3 color = vec3(0.0);
    float alpha = 0.0;

    if (layerId < 0.25) {
        float pulse = 0.75 + 0.25 * sin(time * 3.2 + r * 6.0);
        float flow = plasma * 0.5 + 0.5;
        color = mix(ColorSecondary, ColorPrimary, coreMask);
        color += ColorAccent * flow * 0.25;
        alpha = (0.18 + 0.55 * charge) * coreMask * (0.6 + 0.4 * flow) * pulse;
    } else if (layerId < 0.75) {
        float tick = step(0.2, sin(angle * 24.0 + time * 5.0));
        float ringGlow = ringMask * (0.6 + 0.4 * plasma);
        color = ColorAccent * (0.6 + 0.4 * tick) + ColorPrimary * ringGlow;
        color += ColorAccent * rimMask * fresnel * 0.6;
        alpha = (0.25 + 0.7 * charge) * (ringGlow + tick * ringMask * 0.6);
    } else {
        float sparkNoise = fbm(vec2(angle * 3.0, r * 8.0) + time * 1.5);
        float sparks = step(0.78 + 0.15 * sin(time * 4.0 + angle * 2.0), sparkNoise) * ringMask;
        color = ColorSpark * (0.7 + 0.3 * plasma);
        alpha = sparks * (0.15 + 0.85 * charge);
    }

    color *= Intensity;
    color *= vertexColor.rgb;
    alpha *= vertexColor.a;

    alpha = clamp(alpha, 0.0, 1.0);
    if (alpha < 0.01) {
        discard;
    }

    fragColor = vec4(color, alpha);
}
