#version 330

uniform sampler2D AccumSampler;
uniform sampler2D RevealSampler;

out vec4 fragColor;

void main() {
    ivec2 coord = ivec2(gl_FragCoord.xy);
    vec4 accum = texelFetch(AccumSampler, coord, 0);
    float transmittance = exp(texelFetch(RevealSampler, coord, 0).r);
    vec3 average = accum.rgb / max(accum.a, 1e-5);
    fragColor = vec4(average, transmittance);
}
