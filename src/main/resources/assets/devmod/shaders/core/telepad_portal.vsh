#version 150

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 uv;
out vec3 viewPos;
out vec3 viewNormal;
out float layerId;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;

    vertexColor = Color;
    uv = Normal.yz * 0.5 + 0.5;
    viewPos = view.xyz;

    layerId = Normal.x;

    vec3 baseNormal = layerId < -0.35 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0);
    vec3 normal = normalize(mat3(ModelViewMat) * baseNormal);
    viewNormal = normal;
}
