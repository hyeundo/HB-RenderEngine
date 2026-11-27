package com.yourname.hb3d.render;

import com.yourname.hb3d.Hb3dMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

final class GpuSkinningShader implements AutoCloseable {
    static final int MAX_BONES = 256;
    private static final int BONE_BINDING = 3;

    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(MAX_BONES * 16);
    private int program;
    private int boneUbo;
    private int modelViewLocation;
    private int projectionLocation;
    private int primitiveTransformLocation;
    private int recenterLocation;
    private int skinnedLocation;
    private int boneCountLocation;
    private int normalMappedLocation;

    boolean ensureInitialized() {
        if (program != 0) {
            return true;
        }
        try {
            int vertexShader = compile(GL20.GL_VERTEX_SHADER, readResource("/assets/hb3d/shaders/core/gpu_skinning.vsh"));
            int fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, readResource("/assets/hb3d/shaders/core/gpu_skinning.fsh"));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glBindAttribLocation(program, 0, "Position");
            GL20.glBindAttribLocation(program, 1, "Normal");
            GL20.glBindAttribLocation(program, 2, "UV0");
            GL20.glBindAttribLocation(program, 3, "Joints");
            GL20.glBindAttribLocation(program, 4, "Weights");
            GL20.glBindAttribLocation(program, 5, "Tangent");
            GL20.glLinkProgram(program);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("GPU skinning program link failed: " + GL20.glGetProgramInfoLog(program));
            }

            modelViewLocation = uniform("ModelViewMat");
            projectionLocation = uniform("ProjMat");
            primitiveTransformLocation = uniform("PrimitiveTransform");
            recenterLocation = uniform("Recenter");
            skinnedLocation = uniform("Skinned");
            boneCountLocation = uniform("BoneCount");
            normalMappedLocation = uniform("NormalMapped");
            GL20.glUseProgram(program);
            GL20.glUniform1i(uniform("DiffuseSampler"), 0);
            GL20.glUniform1i(uniform("NormalSampler"), 1);
            int blockIndex = GL31.glGetUniformBlockIndex(program, "Hb3dBones");
            if (blockIndex == GL31.GL_INVALID_INDEX) {
                throw new IllegalStateException("GPU skinning shader is missing Hb3dBones");
            }
            GL31.glUniformBlockBinding(program, blockIndex, BONE_BINDING);
            GL20.glUseProgram(0);

            boneUbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, boneUbo);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, (long) MAX_BONES * 16L * Float.BYTES, GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BONE_BINDING, boneUbo);
            return true;
        } catch (RuntimeException | IOException exception) {
            Hb3dMod.LOGGER.error("Failed to initialize HB_Renderengine GPU skinning", exception);
            close();
            return false;
        }
    }

    void bind(Matrix4f modelView, Matrix4f projection, float centerX, float minY, float centerZ) {
        GL20.glUseProgram(program);
        uploadMatrix(modelViewLocation, modelView);
        uploadMatrix(projectionLocation, projection);
        GL20.glUniform3f(recenterLocation, centerX, minY, centerZ);
    }

    void bindPrimitive(Matrix4f primitiveTransform, List<Matrix4f> boneMatrices) {
        boolean skinned = !boneMatrices.isEmpty();
        GL20.glUniform1i(skinnedLocation, skinned ? 1 : 0);
        GL20.glUniform1i(boneCountLocation, boneMatrices.size());
        uploadMatrix(primitiveTransformLocation, primitiveTransform);
        if (!skinned) {
            return;
        }

        matrixBuffer.clear();
        for (Matrix4f matrix : boneMatrices) {
            matrix.get(matrixBuffer);
            matrixBuffer.position(matrixBuffer.position() + 16);
        }
        matrixBuffer.flip();
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, boneUbo);
        GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, matrixBuffer);
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BONE_BINDING, boneUbo);
    }

    void bindNormalMapped(boolean normalMapped) {
        GL20.glUniform1i(normalMappedLocation, normalMapped ? 1 : 0);
    }

    void unbind() {
        GL20.glUseProgram(0);
    }

    private int uniform(String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) {
            throw new IllegalStateException("GPU skinning shader is missing uniform " + name);
        }
        return location;
    }

    private void uploadMatrix(int location, Matrix4f matrix) {
        matrixBuffer.clear();
        matrix.get(matrixBuffer);
        matrixBuffer.limit(16);
        GL20.glUniformMatrix4fv(location, false, matrixBuffer);
    }

    private int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("GPU skinning shader compile failed: " + log);
        }
        return shader;
    }

    private String readResource(String path) throws IOException {
        try (InputStream stream = GpuSkinningShader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing shader resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void close() {
        if (boneUbo != 0) {
            GL15.glDeleteBuffers(boneUbo);
            boneUbo = 0;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }
}
