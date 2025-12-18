package com.frenkvs.devmod.rendering.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Centralized shader management system for DevMod.
 *
 * <p>Provides GPU detection, shader loading/compilation, and automatic
 * CPU fallback for hardware without OpenGL 3.3+ support.</p>
 *
 * <p>Thread-safety: All methods must be called from render thread.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ShaderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderManager.class);

    // Singleton instance
    private static ShaderManager instance;

    // Minimum OpenGL version (3.3 = 330)
    private static final int MIN_GL_VERSION = 330;

    // Cached GPU capability state
    private boolean initialized = false;
    private boolean shadersSupported = false;
    private int glVersion = 0;
    private String glRenderer = "Unknown";

    // Loaded shader programs
    private final Map<String, ShaderProgram> shaderPrograms = new HashMap<>();

    // Quality level (affects particle counts, etc.)
    private QualityLevel qualityLevel = QualityLevel.MEDIUM;

    private ShaderManager() {}

    /**
     * Gets the singleton instance.
     */
    public static ShaderManager getInstance() {
        if (instance == null) {
            instance = new ShaderManager();
        }
        return instance;
    }

    /**
     * Initializes the shader system, detecting GPU capabilities.
     * Must be called from render thread after OpenGL context is available.
     */
    public void initialize() {
        if (initialized) return;

        try {
            GLCapabilities caps = GL.getCapabilities();

            // Get OpenGL version
            int major = GL30.glGetInteger(GL30.GL_MAJOR_VERSION);
            int minor = GL30.glGetInteger(GL30.GL_MINOR_VERSION);
            glVersion = major * 100 + minor * 10;

            // Get renderer name for quality auto-detection
            glRenderer = GL30.glGetString(GL30.GL_RENDERER);
            if (glRenderer == null) glRenderer = "Unknown";

            // Check shader support
            // Note: On macOS Core Profile (especially Apple Silicon), ARB extensions
            // are part of the core and may not be exposed separately.
            // GL 2.0+ has shaders built-in, GL 3.3+ has all we need.
            boolean hasShaderExtensions = caps.GL_ARB_vertex_shader && caps.GL_ARB_fragment_shader;
            boolean isCoreProfile = glVersion >= 200; // GL 2.0+ has shaders in core
            shadersSupported = glVersion >= MIN_GL_VERSION && (hasShaderExtensions || isCoreProfile);

            // Auto-detect quality level based on GPU
            qualityLevel = detectQualityLevel(glRenderer);

            LOGGER.info("ShaderManager initialized - GL Version: {}.{}, Renderer: {}, Shaders: {}, Quality: {}",
                major, minor, glRenderer, shadersSupported ? "SUPPORTED" : "FALLBACK", qualityLevel);

            initialized = true;

        } catch (Exception e) {
            LOGGER.error("Failed to initialize ShaderManager, using CPU fallback", e);
            shadersSupported = false;
            initialized = true;
        }
    }

    /**
     * Auto-detects quality level based on GPU renderer string.
     */
    private QualityLevel detectQualityLevel(String renderer) {
        String lower = renderer.toLowerCase();

        // High-end GPUs
        if (lower.contains("rtx") || lower.contains("radeon rx 6") || lower.contains("radeon rx 7")
            || lower.contains("geforce gtx 10") || lower.contains("geforce gtx 16")
            || lower.contains("geforce gtx 20") || lower.contains("geforce gtx 30")
            || lower.contains("geforce gtx 40")) {
            return QualityLevel.HIGH;
        }

        // Low-end / integrated GPUs
        if (lower.contains("intel hd") || lower.contains("intel uhd")
            || lower.contains("intel iris") || lower.contains("llvmpipe")
            || lower.contains("software") || lower.contains("mesa")) {
            return QualityLevel.LOW;
        }

        // Default to medium
        return QualityLevel.MEDIUM;
    }

    /**
     * Checks if GPU shaders are supported.
     *
     * @return true if shaders can be used, false if CPU fallback is required
     */
    public boolean areShadersSupported() {
        if (!initialized) {
            LOGGER.warn("ShaderManager not initialized, assuming CPU fallback");
            return false;
        }
        return shadersSupported;
    }

    /**
     * Gets the detected OpenGL version.
     *
     * @return Version as integer (e.g., 330 for 3.3, 450 for 4.5)
     */
    public int getGLVersion() {
        return glVersion;
    }

    /**
     * Gets the GPU renderer name.
     */
    public String getGLRenderer() {
        return glRenderer;
    }

    /**
     * Gets the current quality level.
     */
    public QualityLevel getQualityLevel() {
        return qualityLevel;
    }

    /**
     * Sets the quality level manually.
     */
    public void setQualityLevel(QualityLevel level) {
        this.qualityLevel = level;
        LOGGER.info("Quality level set to: {}", level);
    }

    /**
     * Loads and compiles a shader program from resource files.
     *
     * @param name Unique name for this shader program
     * @param vertexPath Resource path to vertex shader (e.g., "devmod:shaders/core/vfx.vsh")
     * @param fragmentPath Resource path to fragment shader
     * @return The compiled shader program, or null if compilation failed
     */
    @Nullable
    public ShaderProgram loadShader(String name, ResourceLocation vertexPath, ResourceLocation fragmentPath) {
        if (!shadersSupported) {
            LOGGER.debug("Shaders not supported, skipping load of: {}", name);
            return null;
        }

        // Check if already loaded
        if (shaderPrograms.containsKey(name)) {
            return shaderPrograms.get(name);
        }

        try {
            ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

            // Load shader source code
            String vertexSource = loadShaderSource(resourceManager, vertexPath);
            String fragmentSource = loadShaderSource(resourceManager, fragmentPath);

            if (vertexSource == null || fragmentSource == null) {
                LOGGER.error("Failed to load shader source for: {}", name);
                return null;
            }

            // Compile shaders
            int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource, vertexPath.toString());
            if (vertexShader == 0) return null;

            int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource, fragmentPath.toString());
            if (fragmentShader == 0) {
                GL20.glDeleteShader(vertexShader);
                return null;
            }

            // Link program
            int programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, vertexShader);
            GL20.glAttachShader(programId, fragmentShader);
            GL20.glLinkProgram(programId);

            // Check link status
            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(programId);
                LOGGER.error("Shader program link failed for {}: {}", name, log);
                GL20.glDeleteProgram(programId);
                GL20.glDeleteShader(vertexShader);
                GL20.glDeleteShader(fragmentShader);
                return null;
            }

            // Clean up individual shaders (they're now part of the program)
            GL20.glDetachShader(programId, vertexShader);
            GL20.glDetachShader(programId, fragmentShader);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);

            ShaderProgram program = new ShaderProgram(name, programId);
            shaderPrograms.put(name, program);

            LOGGER.info("Shader program loaded successfully: {}", name);
            return program;

        } catch (Exception e) {
            LOGGER.error("Exception loading shader: {}", name, e);
            return null;
        }
    }

    /**
     * Loads shader source code from a resource file.
     */
    @Nullable
    private String loadShaderSource(ResourceManager resourceManager, ResourceLocation location) {
        ResourceLocation safeLocation = Objects.requireNonNull(location, "shader location");
        try {
            var resource = resourceManager.getResource(safeLocation);
            if (resource.isEmpty()) {
                LOGGER.error("Shader resource not found: {}", safeLocation);
                return null;
            }

            try (InputStream stream = resource.get().open();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read shader source: {}", safeLocation, e);
            return null;
        }
    }

    /**
     * Compiles a single shader (vertex or fragment).
     */
    private int compileShader(int type, String source, String name) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            LOGGER.error("Shader compilation failed for {}: {}", name, log);
            GL20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    /**
     * Gets a previously loaded shader program by name.
     */
    @Nullable
    public ShaderProgram getShader(String name) {
        return shaderPrograms.get(name);
    }

    /**
     * Unloads all shader programs and frees GPU resources.
     */
    public void cleanup() {
        for (ShaderProgram program : shaderPrograms.values()) {
            program.delete();
        }
        shaderPrograms.clear();
        LOGGER.info("ShaderManager cleanup complete");
    }

    /**
     * Reloads all shaders (useful for development/resource pack changes).
     */
    public void reloadShaders() {
        // Store names and paths before clearing
        Map<String, ShaderProgram> old = new HashMap<>(shaderPrograms);

        // Clear existing
        cleanup();

        // Note: To implement full reload, we'd need to store the paths
        // For now, this just cleans up and logs
        LOGGER.info("Shaders cleared - {} programs removed", old.size());
    }

    /**
     * Quality levels for shader effects.
     */
    public enum QualityLevel {
        /** Minimal effects, max performance */
        LOW(100, 0.5f),
        /** Balanced effects and performance */
        MEDIUM(300, 1.0f),
        /** Full effects, max quality */
        HIGH(500, 1.5f);

        /** Maximum particle count for this quality level */
        public final int maxParticles;
        /** Effect intensity multiplier */
        public final float intensityMultiplier;

        QualityLevel(int maxParticles, float intensityMultiplier) {
            this.maxParticles = maxParticles;
            this.intensityMultiplier = intensityMultiplier;
        }
    }
}
