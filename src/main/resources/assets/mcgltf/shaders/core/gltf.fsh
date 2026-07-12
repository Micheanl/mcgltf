#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

layout(std140) uniform GltfMaterial {
    vec4 BaseColorFactor;
    float AlphaCutoff;
    int MaterialFlags;
    int MaterialPad0;
};

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;

#ifdef OIT
layout(location = 0) out vec4 accum;
layout(location = 1) out vec4 reveal;
#else
out vec4 fragColor;
#endif

void main() {
#ifdef LOD_SIMPLE
    vec4 color = BaseColorFactor * vertexColor;
#else
    vec4 color = texture(Sampler0, texCoord0) * BaseColorFactor * vertexColor;
#endif
#ifdef ALPHA_MASK
    if (color.a < AlphaCutoff) {
        discard;
    }
#endif
    color *= ColorModulator;
    if ((MaterialFlags & 1) == 0) {
        color *= lightMapColor;
    }
    vec4 shaded = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#ifdef OIT
    float alpha = clamp(shaded.a, 0.0, 1.0);
    float weight = clamp(alpha * (3.0 / (1e-4 + pow(sphericalVertexDistance * 0.1, 3.0))), 1e-2, 3e3);
    accum = vec4(shaded.rgb * alpha, alpha) * weight;
    reveal = vec4(log(1.0 - clamp(alpha, 0.0, 0.9999)), 0.0, 0.0, 0.0);
#else
    fragColor = shaded;
#endif
}
