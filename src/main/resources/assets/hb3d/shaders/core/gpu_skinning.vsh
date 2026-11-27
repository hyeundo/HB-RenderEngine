#version 150

in vec3 Position;
in vec3 Normal;
in vec2 UV0;
in vec4 Tangent;
in vec4 Joints;
in vec4 Weights;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 PrimitiveTransform;
uniform vec3 Recenter;
uniform int Skinned;
uniform int BoneCount;

layout(std140) uniform Hb3dBones {
    mat4 BoneMatrices[256];
};

out vec2 texCoord;
out vec3 vertexNormal;
out vec3 vertexTangent;
out float tangentSign;

void main() {
    mat4 transform = PrimitiveTransform;
    if (Skinned != 0) {
        int j0 = int(Joints.x + 0.5);
        int j1 = int(Joints.y + 0.5);
        int j2 = int(Joints.z + 0.5);
        int j3 = int(Joints.w + 0.5);
        float w0 = j0 >= 0 && j0 < BoneCount ? Weights.x : 0.0;
        float w1 = j1 >= 0 && j1 < BoneCount ? Weights.y : 0.0;
        float w2 = j2 >= 0 && j2 < BoneCount ? Weights.z : 0.0;
        float w3 = j3 >= 0 && j3 < BoneCount ? Weights.w : 0.0;
        float weightSum = w0 + w1 + w2 + w3;
        if (weightSum > 0.000001) {
            if (weightSum < 0.9999) {
                w0 /= weightSum;
                w1 /= weightSum;
                w2 /= weightSum;
                w3 /= weightSum;
            }
            mat4 skinTransform = mat4(0.0);
            if (w0 > 0.000001) skinTransform += BoneMatrices[j0] * w0;
            if (w1 > 0.000001) skinTransform += BoneMatrices[j1] * w1;
            if (w2 > 0.000001) skinTransform += BoneMatrices[j2] * w2;
            if (w3 > 0.000001) skinTransform += BoneMatrices[j3] * w3;
            transform = skinTransform;
        }
    }

    vec3 modelPosition = (transform * vec4(Position, 1.0)).xyz - Recenter;
    vec3 modelNormal = normalize(transpose(inverse(mat3(transform))) * Normal);
    vertexNormal = normalize(mat3(ModelViewMat) * modelNormal);
    vec3 modelTangent = normalize(mat3(transform) * Tangent.xyz);
    vertexTangent = normalize(mat3(ModelViewMat) * modelTangent);
    tangentSign = Tangent.w;
    texCoord = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(modelPosition, 1.0);
}
