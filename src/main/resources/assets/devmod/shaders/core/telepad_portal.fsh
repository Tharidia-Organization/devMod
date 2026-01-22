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
    // DEBUG: Output solid cyan to verify geometry is correct
    // If you see a cyan oval, the geometry and rendering setup are working

    vec2 uv = texCoord * 2.0 - 1.0;
    float dist = length(uv);

    // Simple oval mask
    float ovalMask = 1.0 - smoothstep(0.8, 1.0, dist);
    if (ovalMask <= 0.01) {
        discard;
    }

    // Simple animated color
    float time = GameTime * 20.0;
    vec3 color = ColorPrimary * (0.5 + 0.5 * sin(time));

    fragColor = vec4(color * Intensity, ovalMask * vertexColor.a);
}
