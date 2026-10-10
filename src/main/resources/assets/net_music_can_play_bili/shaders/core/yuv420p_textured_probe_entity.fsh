#version 330

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float y = texture(Sampler0, texCoord0).r;
    vec4 color = vec4(y, 0.0, 0.0, 1.0) * vertexColor * ColorModulator;
    fragColor = linear_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogStart, FogEnd, FogColor);
}