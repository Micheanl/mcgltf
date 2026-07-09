#version 450

in Vary {
	vec2 uv;
	vec3 nrm;
	vec4 color;
	vec2 light;
} vary;

uniform sampler2D Sampler0;
uniform sampler2D Lightmap;
uniform vec4 BaseColorFactor;
uniform vec3 Emissive;
uniform float Metallic;
uniform float Roughness;
uniform float AlphaCutoff;
uniform int MaskMode;

out vec4 fragColor;

const float PI = 3.14159265;

void main() {
	vec4 albedo = texture(Sampler0, vary.uv) * BaseColorFactor * vary.color;
	if (MaskMode == 1 && albedo.a < AlphaCutoff) {
		discard;
	}
	vec3 n = normalize(vary.nrm);
	vec3 ambient = texture(Lightmap, vary.light).rgb;
	vec3 v = vec3(0.0, 0.0, 1.0);
	vec3 l = normalize(vec3(0.35, 0.6, 1.0));
	vec3 h = normalize(v + l);
	float nl = max(dot(n, l), 0.0);
	float nh = max(dot(n, h), 0.0);
	float a = Roughness * Roughness;
	float d = nh * nh * (a * a - 1.0) + 1.0;
	float ggx = (a * a) / (PI * d * d);
	vec3 f0 = mix(vec3(0.04), albedo.rgb, Metallic);
	vec3 diffuse = albedo.rgb * (1.0 - Metallic) * ambient;
	vec3 specular = f0 * ggx * nl * ambient;
	vec3 color = diffuse + specular + Emissive;
	fragColor = vec4(color, albedo.a);
}
