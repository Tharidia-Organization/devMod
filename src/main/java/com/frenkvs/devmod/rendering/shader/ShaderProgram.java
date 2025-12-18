package com.frenkvs.devmod.rendering.shader;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper for an OpenGL shader program.
 *
 * <p>Provides convenient methods for binding the program and setting uniforms.
 * Uniform locations are cached for performance.</p>
 */
@OnlyIn(Dist.CLIENT)
public class ShaderProgram {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProgram.class);

    private final String name;
    private final int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private boolean deleted = false;

    /**
     * Creates a new shader program wrapper.
     *
     * @param name Human-readable name for logging
     * @param programId OpenGL program ID
     */
    public ShaderProgram(String name, int programId) {
        this.name = name;
        this.programId = programId;
    }

    /**
     * Gets the program name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the OpenGL program ID.
     */
    public int getProgramId() {
        return programId;
    }

    /**
     * Binds this shader program for rendering.
     */
    public void bind() {
        if (deleted) {
            LOGGER.warn("Attempting to bind deleted shader: {}", name);
            return;
        }
        GL20.glUseProgram(programId);
    }

    /**
     * Unbinds any shader program (reverts to fixed-function).
     */
    public void unbind() {
        GL20.glUseProgram(0);
    }

    /**
     * Gets the location of a uniform variable, caching the result.
     *
     * @param uniformName Name of the uniform in the shader
     * @return Location ID, or -1 if not found
     */
    public int getUniformLocation(String uniformName) {
        return uniformLocations.computeIfAbsent(uniformName, name -> {
            int location = GL20.glGetUniformLocation(programId, name);
            if (location == -1) {
                LOGGER.debug("Uniform '{}' not found in shader '{}'", name, this.name);
            }
            return location;
        });
    }

    // === Uniform Setters ===

    /**
     * Sets an integer uniform.
     */
    public void setUniform(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    /**
     * Sets a float uniform.
     */
    public void setUniform(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    /**
     * Sets a vec2 uniform.
     */
    public void setUniform(String name, float x, float y) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    /**
     * Sets a vec3 uniform.
     */
    public void setUniform(String name, float x, float y, float z) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z);
        }
    }

    /**
     * Sets a vec3 uniform from a Vector3f.
     */
    public void setUniform(String name, Vector3f vec) {
        setUniform(name, vec.x, vec.y, vec.z);
    }

    /**
     * Sets a vec4 uniform.
     */
    public void setUniform(String name, float x, float y, float z, float w) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }

    /**
     * Sets a vec4 uniform from a Vector4f.
     */
    public void setUniform(String name, Vector4f vec) {
        setUniform(name, vec.x, vec.y, vec.z, vec.w);
    }

    /**
     * Sets a mat4 uniform from a Matrix4f.
     */
    public void setUniform(String name, Matrix4f matrix) {
        int location = getUniformLocation(name);
        if (location != -1) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(16);
                matrix.get(buffer);
                GL20.glUniformMatrix4fv(location, false, buffer);
            }
        }
    }

    /**
     * Sets a mat3 uniform from a Matrix3f.
     */
    public void setUniformMatrix3(String name, org.joml.Matrix3f matrix) {
        int location = getUniformLocation(name);
        if (location != -1) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(9);
                matrix.get(buffer);
                GL20.glUniformMatrix3fv(location, false, buffer);
            }
        }
    }

    /**
     * Sets a boolean uniform (as int 0/1).
     */
    public void setUniform(String name, boolean value) {
        setUniform(name, value ? 1 : 0);
    }

    /**
     * Sets a sampler uniform (texture unit index).
     */
    public void setSampler(String name, int textureUnit) {
        setUniform(name, textureUnit);
    }

    /**
     * Sets a color uniform from ARGB integer.
     */
    public void setColorUniform(String name, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255.0f;
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        setUniform(name, r, g, b, a);
    }

    /**
     * Sets a color uniform from RGB integer (alpha = 1.0).
     */
    public void setColorUniformRGB(String name, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        setUniform(name, r, g, b);
    }

    /**
     * Deletes the shader program and frees GPU resources.
     */
    public void delete() {
        if (!deleted) {
            GL20.glDeleteProgram(programId);
            deleted = true;
            uniformLocations.clear();
            LOGGER.debug("Shader program deleted: {}", name);
        }
    }

    /**
     * Checks if this program has been deleted.
     */
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public String toString() {
        return "ShaderProgram[name=" + name + ", id=" + programId + ", deleted=" + deleted + "]";
    }
}
