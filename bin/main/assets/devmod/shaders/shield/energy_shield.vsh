#version 150

// === Vertex Attributes ===
in vec3 Position;
in vec4 Color;
in vec3 Normal;
in vec2 UV0;

// === Uniforms ===
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 NormalMat;
uniform float GameTime;

// === Outputs to Fragment Shader ===
out vec4 vertexColor;
out vec3 vertexNormal;
out vec3 viewPosition;
out vec3 localPosition;  // Local/model space position for impact calculations
out vec2 texCoord;
out float fresnel;

void main() {
    // Transform position to view space
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    // Pass color through
    vertexColor = Color;

    // Transform normal to view space
    vertexNormal = normalize(NormalMat * Normal);

    // View position for lighting calculations
    viewPosition = viewPos.xyz;

    // Local position (unit sphere, used for impact ripple)
    localPosition = Position;

    // Texture coordinates
    texCoord = UV0;

    // Calculate Fresnel (edge glow)
    // View direction is from surface to camera (negative of view position normalized)
    vec3 viewDir = normalize(-viewPos.xyz);
    fresnel = pow(1.0 - max(dot(viewDir, vertexNormal), 0.0), 3.0);
}
