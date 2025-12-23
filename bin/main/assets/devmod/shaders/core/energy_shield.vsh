#version 150

// === Vertex Attributes (POSITION_COLOR_NORMAL format) ===
in vec3 Position;
in vec4 Color;
in vec3 Normal;

// === Uniforms ===
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

// === Outputs to Fragment Shader ===
out vec4 vertexColor;
out vec3 vertexNormal;
out vec3 viewPosition;
out vec3 localPosition;  // Local/model space position for impact calculations
out vec2 texCoord;
out float fresnel;

// Pi constant for UV generation
const float PI = 3.14159265359;

void main() {
    // Transform position to view space
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    // Pass color through
    vertexColor = Color;

    // Transform normal to view space using the upper 3x3 of ModelViewMat
    mat3 normalMat = mat3(ModelViewMat);
    vertexNormal = normalize(normalMat * Normal);

    // View position for lighting calculations
    viewPosition = viewPos.xyz;

    // Local position (unit sphere, used for impact ripple)
    localPosition = Position;

    // Generate spherical UV coordinates from position (for procedural textures)
    vec3 normalizedPos = normalize(Position);
    float u = atan(normalizedPos.z, normalizedPos.x) / (2.0 * PI) + 0.5;
    float v = asin(normalizedPos.y) / PI + 0.5;
    texCoord = vec2(u, v);

    // Calculate Fresnel (edge glow)
    // View direction is from surface to camera (negative of view position normalized)
    vec3 viewDir = normalize(-viewPos.xyz);
    fresnel = pow(1.0 - max(dot(viewDir, vertexNormal), 0.0), 3.0);
}
