#version 150

// === Vertex Attributes (POSITION_COLOR_NORMAL format) ===
in vec3 Position;
in vec4 Color;
in vec3 Normal;

// === Uniforms ===
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform float Rotation;       // For beacon rotation
uniform float PulseScale;     // For beacon pulsing
uniform int BeaconType;       // 0=path, 1=start, 2=dest, 3=node

// === Outputs to Fragment Shader ===
out vec4 vertexColor;
out vec3 vertexNormal;
out float pathProgress;       // Position along path (0-1) for gradient/marching
out float beaconParam;        // Additional beacon parameter

void main() {
    vec3 pos = Position;

    // Apply rotation for beacon vertices (BeaconType > 0)
    if (BeaconType > 0) {
        // Rotate around Y axis for beacon rings
        float cosR = cos(Rotation);
        float sinR = sin(Rotation);
        float newX = pos.x * cosR - pos.z * sinR;
        float newZ = pos.x * sinR + pos.z * cosR;
        pos.x = newX;
        pos.z = newZ;

        // Apply pulse scale
        pos *= PulseScale;
    }

    // Transform position to clip space
    vec4 viewPos = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * viewPos;

    // Pass color through
    vertexColor = Color;

    // Extract path progress from Normal.x (CPU passes 0-1 along path)
    pathProgress = Normal.x;

    // Extract beacon parameter from Normal.y
    beaconParam = Normal.y;

    // Transform normal to view space
    mat3 normalMat = mat3(ModelViewMat);
    vertexNormal = normalize(normalMat * Normal);
}
