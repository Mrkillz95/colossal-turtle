#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float HazeStart;
uniform float HazeEnd;
uniform float HazeMax;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= vertexColor * ColorModulator * lightMapColor;
    // the haze of the distance, like far mountains
    float h = clamp((vertexDistance - HazeStart) / (HazeEnd - HazeStart), 0.0, HazeMax);
    fragColor = vec4(mix(color.rgb, FogColor.rgb, h), color.a);
}
