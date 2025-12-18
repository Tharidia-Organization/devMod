#version 150

// === Inputs from Vertex Shader ===
in vec4 vertexColor;
in vec3 vertexNormal;
in float pathProgress;
in float beaconParam;

// === Uniforms ===
uniform float GameTime;
uniform float MarchOffset;    // For marching ants animation
uniform int BeaconType;       // 0=path, 1=start, 2=dest, 3=node
uniform vec3 StartColor;      // Cyan (0, 1, 1)
uniform vec3 EndColor;        // Gold (1, 0.84, 0)
uniform int CanReach;         // 1=reachable (gold), 0=not (red)
uniform float Alpha;

// === Output ===
out vec4 fragColor;

void main() {
    float time = GameTime * 1000.0;
    vec3 color;
    float alpha = Alpha;

    // === BEACON TYPES ===
    if (BeaconType == 1) {
        // START beacon - Cyan with pulse
        color = StartColor;
        float pulse = sin(time * 0.005) * 0.2 + 0.8;
        color *= pulse;
        alpha *= 0.8;
    }
    else if (BeaconType == 2) {
        // DESTINATION beacon - Gold or Red based on reachability
        if (CanReach == 1) {
            color = EndColor;
        } else {
            color = vec3(1.0, 0.2, 0.2); // Red for unreachable
        }
        float pulse = sin(time * 0.006 + 1.0) * 0.2 + 0.8;
        color *= pulse;
        alpha *= 0.8;
    }
    else if (BeaconType == 3) {
        // NODE marker - White
        color = vec3(1.0, 1.0, 1.0);
        alpha *= 0.5;
    }
    // === PATH TYPE (BeaconType == 0) ===
    else {
        // Gradient from start to end color based on path progress
        color = mix(StartColor, EndColor, pathProgress);

        // === Marching ants effect ===
        // Create dashed pattern that moves along the path
        float marchPattern = fract(pathProgress * 10.0 - MarchOffset);

        // Dash pattern: bright for 0.0-0.5, dim for 0.5-1.0
        if (marchPattern > 0.5) {
            // Dim section (gap)
            color *= 0.4;
            alpha *= 0.6;
        } else {
            // Bright section (dash)
            color *= 1.2;
        }

        // Add subtle glow at edges
        float edgeGlow = 1.0 - abs(beaconParam - 0.5) * 2.0;
        color += vec3(0.1) * edgeGlow;
    }

    // === Add shimmer effect ===
    float shimmer = sin(time * 0.008 + pathProgress * 20.0) * 0.1 + 1.0;
    color *= shimmer;

    // Clamp values
    color = clamp(color, 0.0, 1.5);
    alpha = clamp(alpha * vertexColor.a, 0.0, 1.0);

    fragColor = vec4(color, alpha);
}
