#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec3 Normal;
in vec4 Tangent;
in vec2 UV0;
in vec2 UV1;
in vec4 Color;

#ifdef SKINNED
in uvec4 Joints;
in vec4 Weights;
uniform samplerBuffer GltfJoints;
#endif

#ifdef MORPHED
uniform samplerBuffer GltfMorph;
layout(std140) uniform GltfInstance {
    ivec4 MorphIndex0;
    ivec4 MorphIndex1;
    vec4 MorphWeight0;
    vec4 MorphWeight1;
    int MorphCount;
    int MorphVertexCount;
    int InstancePad0;
    int InstancePad1;
};
#endif

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 texCoord0;

void main() {
    vec4 localPos = vec4(Position, 1.0);
#ifdef MORPHED
    for (int i = 0; i < MorphCount; i++) {
        int index = i < 4 ? MorphIndex0[i] : MorphIndex1[i - 4];
        float weight = i < 4 ? MorphWeight0[i] : MorphWeight1[i - 4];
        localPos.xyz += weight * texelFetch(GltfMorph, index * MorphVertexCount + gl_VertexID).xyz;
    }
#endif
#ifdef SKINNED
    int jx = int(Joints.x) * 3;
    int jy = int(Joints.y) * 3;
    int jz = int(Joints.z) * 3;
    int jw = int(Joints.w) * 3;
    vec4 row0 = Weights.x * texelFetch(GltfJoints, jx)     + Weights.y * texelFetch(GltfJoints, jy)     + Weights.z * texelFetch(GltfJoints, jz)     + Weights.w * texelFetch(GltfJoints, jw);
    vec4 row1 = Weights.x * texelFetch(GltfJoints, jx + 1) + Weights.y * texelFetch(GltfJoints, jy + 1) + Weights.z * texelFetch(GltfJoints, jz + 1) + Weights.w * texelFetch(GltfJoints, jw + 1);
    vec4 row2 = Weights.x * texelFetch(GltfJoints, jx + 2) + Weights.y * texelFetch(GltfJoints, jy + 2) + Weights.z * texelFetch(GltfJoints, jz + 2) + Weights.w * texelFetch(GltfJoints, jw + 2);
    localPos = vec4(dot(row0, localPos), dot(row1, localPos), dot(row2, localPos), 1.0);
#endif

    vec4 viewPosition = ModelViewMat * localPos;
    gl_Position = ProjMat * viewPosition;

    sphericalVertexDistance = fog_spherical_distance(viewPosition.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPosition.xyz);

    vertexColor = Color;
    lightMapColor = sample_lightmap(Sampler2, ivec2(ModelOffset.xy));
    texCoord0 = UV0;
}
