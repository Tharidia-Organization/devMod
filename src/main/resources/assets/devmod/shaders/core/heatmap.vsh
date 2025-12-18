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
out float intensity;

void main() {
    // Transform position to clip space
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    // Pass color through - CPU calculates gradient, but we can enhance it
    vertexColor = Color;

    // Extract intensity from vertex color red channel (CPU passes normalized intensity)
    // The CPU code uses color.r as intensity for gradient calculation
    intensity = Color.r;

    // Transform normal to view space
    mat3 normalMat = mat3(ModelViewMat);
    vertexNormal = normalize(normalMat * Normal);
}
