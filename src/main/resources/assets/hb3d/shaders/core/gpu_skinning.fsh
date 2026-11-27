#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D NormalSampler;
uniform int NormalMapped;

in vec2 texCoord;
in vec3 vertexNormal;
in vec3 vertexTangent;
in float tangentSign;
out vec4 fragColor;

void main() {
    vec4 baseColor = texture(DiffuseSampler, texCoord);
    if (baseColor.a < 0.01) {
        discard;
    }
    vec3 normal = normalize(vertexNormal);
    float normalMapped = 0.0;
    float tangentDetail = 1.0;
    if (NormalMapped != 0) {
        vec3 tangent = normalize(vertexTangent - normal * dot(normal, vertexTangent));
        vec3 bitangent = normalize(cross(normal, tangent)) * tangentSign;
        vec3 sampledNormal = texture(NormalSampler, texCoord).xyz * 2.0 - 1.0;
        sampledNormal.xy *= 3.5;
        sampledNormal = normalize(sampledNormal);
        vec3 tangentLight = normalize(vec3(-0.48, 0.58, 0.66));
        float tangentDiffuse = max(dot(sampledNormal, tangentLight), 0.0);
        tangentDetail = 0.08 + pow(tangentDiffuse, 1.35) * 1.65;
        normal = normalize(mat3(tangent, bitangent, normal) * sampledNormal);
        normalMapped = 1.0;
    }
    vec3 lightDirection = normalize(vec3(-0.42, 0.88, 0.20));
    float diffuse = max(dot(normal, lightDirection), 0.0);
    float upward = max(normal.y, 0.0);
    float shade = 0.58 + diffuse * 0.28 + upward * 0.12;
    float specular = 0.0;
    if (normalMapped > 0.5) {
        vec3 viewDirection = vec3(0.0, 0.0, 1.0);
        vec3 halfDirection = normalize(lightDirection + viewDirection);
        specular = pow(max(dot(normal, halfDirection), 0.0), 18.0) * 0.55;
        shade = 0.14 + diffuse * 0.80 + upward * 0.06;
    }
    fragColor = vec4(baseColor.rgb * shade * tangentDetail + vec3(specular), baseColor.a);
}
