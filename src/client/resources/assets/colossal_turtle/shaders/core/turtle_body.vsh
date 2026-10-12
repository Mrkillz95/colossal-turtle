#version 150

#moj_import <light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform float Near;
uniform float Squash;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 texCoord0;

// past the edge of the loaded world everything is pulled in along the line of sight: same size on screen, but
// inside the view
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    float d = length(view.xyz);
    if (d > Near) view.xyz *= (Near + (d - Near) * Squash) / d;
    gl_Position = ProjMat * view;
    vertexDistance = d;
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
