#version 150

in vec3 Position;
in vec3 Normal;
in vec4 Tangent;
in vec4 Joints;
in vec4 Weights;

uniform mat4 PrimitiveTransform;
uniform vec3 Recenter;
uniform int Skinned;
uniform int BoneCount;

layout(std140) uniform Hb3dBones {
    mat4 BoneMatrices[256];
};

out vec3 SkinnedPosition;
out vec3 SkinnedNormal;
out vec4 SkinnedTangent;

void main() {
    mat4 transform = PrimitiveTransform;
    if (Skinned != 0) {
        int joints[4] = int[4](int(Joints.x + 0.5), int(Joints.y + 0.5), int(Joints.z + 0.5), int(Joints.w + 0.5));
        float weights[4] = float[4](Weights.x, Weights.y, Weights.z, Weights.w);
        float weightSum = 0.0;
        for (int i = 0; i < 4; ++i) {
            if (joints[i] < 0 || joints[i] >= BoneCount) weights[i] = 0.0;
            weightSum += weights[i];
        }
        if (weightSum > 0.000001) {
            mat4 skinTransform = mat4(0.0);
            for (int i = 0; i < 4; ++i) {
                if (weights[i] > 0.000001) skinTransform += BoneMatrices[joints[i]] * (weights[i] / weightSum);
            }
            transform = skinTransform;
        }
    }
    SkinnedPosition = (transform * vec4(Position, 1.0)).xyz - Recenter;
    SkinnedNormal = normalize(transpose(inverse(mat3(transform))) * Normal);
    SkinnedTangent = vec4(normalize(mat3(transform) * Tangent.xyz), Tangent.w);
}
