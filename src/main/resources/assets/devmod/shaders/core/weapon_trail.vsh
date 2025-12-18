#version 150

// === Vertex Attributes (POSITION_COLOR_NORMAL format) ===
in vec3 Position;
in vec4 Color;
in vec3 Normal;

// === Uniforms ===
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform float TrailLength;

// === Outputs to Fragment Shader ===
out vec4 vertexColor;
out float trailProgress;    // Position along trail (0=tip, 1=tail) from Normal.x
out float trailWidth;       // Width factor from Normal.y
out float trailAge;         // Age factor from Normal.z

void main() {
    // Transform position to clip space
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    // Pass color through
    vertexColor = Color;

    // Extract trail parameters from Normal
    // Normal.x = progress along trail (0 = weapon tip, 1 = trail end)
    // Normal.y = width factor (for edge softening)
    // Normal.z = age/fade factor
    trailProgress = Normal.x;
    trailWidth = Normal.y;
    trailAge = Normal.z;
}
