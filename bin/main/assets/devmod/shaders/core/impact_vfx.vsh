#version 150

// === Vertex Attributes (POSITION_COLOR_NORMAL format) ===
in vec3 Position;
in vec4 Color;
in vec3 Normal;

// === Uniforms ===
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform int EffectType;       // 0=vortex, 1=slash, 2=lines
uniform float Rotation;       // Current rotation angle (radians)
uniform float PulseScale;     // Scale factor for pulsing (0.85-1.15)
uniform float SlashProgress;  // Slash animation progress (0.0-1.0)

// === Outputs to Fragment Shader ===
out vec4 vertexColor;
out vec3 vertexNormal;
out vec3 localPosition;
out float effectParam;        // Additional parameter passed to fragment

void main() {
    vec3 pos = Position;

    // Apply rotation for vortex effect (EffectType == 0)
    if (EffectType == 0) {
        // Rotate around Y axis
        float cosR = cos(Rotation);
        float sinR = sin(Rotation);
        float newX = pos.x * cosR - pos.z * sinR;
        float newZ = pos.x * sinR + pos.z * cosR;
        pos.x = newX;
        pos.z = newZ;

        // Apply pulsing scale
        pos *= PulseScale;
    }
    // Slash trail animation (EffectType == 1)
    else if (EffectType == 1) {
        // Use Normal.x as the vertex's position along the slash (0-1)
        float vertexT = Normal.x;

        // Vertices beyond SlashProgress get pulled back
        if (vertexT > SlashProgress) {
            // Fade effect: shrink vertices not yet reached
            float fadeFactor = max(0.0, 1.0 - (vertexT - SlashProgress) * 3.0);
            pos *= fadeFactor;
        }
    }
    // Connection lines (EffectType == 2) - pulse scaling
    else if (EffectType == 2) {
        pos *= PulseScale;
    }

    // Transform position to clip space
    vec4 viewPos = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * viewPos;

    // Pass color through (may be modified by CPU for gradient)
    vertexColor = Color;

    // Pass local position for fragment calculations
    localPosition = Position;

    // Transform normal to view space
    mat3 normalMat = mat3(ModelViewMat);
    vertexNormal = normalize(normalMat * Normal);

    // Pass effect-specific parameter
    // For vortex: spiral progress (from Normal.y)
    // For slash: position along slash (from Normal.x)
    // For lines: line index (from Normal.z)
    effectParam = Normal.y;
}
